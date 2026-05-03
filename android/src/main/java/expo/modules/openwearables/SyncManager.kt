package expo.modules.openwearables

import android.content.Context
import android.content.SharedPreferences
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SyncManager(
    private val context: Context,
    private val authManager: AuthManager,
    private val healthConnectManager: HealthConnectManager,
    private val apiClient: ApiClient,
    private val onLog: (String) -> Unit,
    private val onAuthError: (Int, String) -> Unit
) {

    companion object {
        private const val PREFS_FILE = "ow_sync_prefs"
        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private const val KEY_SYNC_DAYS_BACK = "sync_days_back"
        private const val KEY_LAST_SYNC_SUCCESS = "last_sync_success"
        private const val KEY_LAST_SYNC_ATTEMPT = "last_sync_attempt"
        private const val KEY_SYNC_ERROR = "last_sync_error"
        private const val ANCHOR_PREFIX = "anchor_"
        private const val WORK_NAME = "ow_background_sync"
        private const val SDK_VERSION = "1.0.0"
        private const val MIN_SYNC_INTERVAL_MINUTES = 15L
        private const val DEFAULT_SYNC_DAYS_BACK = 7
        internal const val DATA_KEY_SYNC_DAYS_BACK = "sync_days_back"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val isSyncing = AtomicBoolean(false)

    // ---------------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------------

    fun setSyncInterval(minutes: Long) {
        val clamped = maxOf(minutes, MIN_SYNC_INTERVAL_MINUTES)
        prefs.edit().putLong(KEY_SYNC_INTERVAL, clamped).apply()
        onLog("[SyncManager] Sync interval set to $clamped minutes")
    }

    private fun getSyncInterval(): Long =
        prefs.getLong(KEY_SYNC_INTERVAL, MIN_SYNC_INTERVAL_MINUTES)

    // ---------------------------------------------------------------------------
    // Background sync (WorkManager)
    // ---------------------------------------------------------------------------

    suspend fun startBackgroundSync(syncDaysBack: Int?): Boolean = withContext(Dispatchers.IO) {
        if (!authManager.isSessionValid()) {
            onLog("[SyncManager] startBackgroundSync: No valid session")
            return@withContext false
        }

        val days = syncDaysBack ?: DEFAULT_SYNC_DAYS_BACK
        prefs.edit().putInt(KEY_SYNC_DAYS_BACK, days).apply()

        val interval = getSyncInterval()
        val inputData = workDataOf(DATA_KEY_SYNC_DAYS_BACK to days)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        prefs.edit().putBoolean("bg_sync_scheduled", true).apply()
        onLog("[SyncManager] Background sync started with interval=${interval}m, daysBack=$days")
        true
    }

    fun stopBackgroundSync() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        prefs.edit().putBoolean("bg_sync_scheduled", false).apply()
        onLog("[SyncManager] Background sync stopped")
    }

    fun isSyncActive(): Boolean = isSyncing.get()

    fun getSyncStatus(): Map<String, Any?> {
        val bgScheduled = prefs.getBoolean("bg_sync_scheduled", false)
        return mapOf(
            "isActive" to isSyncing.get(),
            "workerState" to if (bgScheduled) "ENQUEUED" else "NOT_SCHEDULED",
            "lastSyncSuccess" to prefs.getString(KEY_LAST_SYNC_SUCCESS, null),
            "lastSyncAttempt" to prefs.getString(KEY_LAST_SYNC_ATTEMPT, null),
            "lastSyncError" to prefs.getString(KEY_SYNC_ERROR, null),
            "syncIntervalMinutes" to getSyncInterval(),
            "syncDaysBack" to prefs.getInt(KEY_SYNC_DAYS_BACK, DEFAULT_SYNC_DAYS_BACK)
        )
    }

    suspend fun resumeSync(): Boolean = withContext(Dispatchers.IO) {
        val days = prefs.getInt(KEY_SYNC_DAYS_BACK, DEFAULT_SYNC_DAYS_BACK)
        startBackgroundSync(days)
    }

    // ---------------------------------------------------------------------------
    // Manual sync
    // ---------------------------------------------------------------------------

    suspend fun syncNow() = withContext(Dispatchers.IO) {
        if (!isSyncing.compareAndSet(false, true)) {
            onLog("[SyncManager] Sync already in progress, skipping")
            return@withContext
        }

        val nowIso = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        prefs.edit().putString(KEY_LAST_SYNC_ATTEMPT, nowIso).remove(KEY_SYNC_ERROR).apply()

        try {
            val creds = authManager.getCredentials()
            if (creds == null) {
                onLog("[SyncManager] syncNow: No credentials stored")
                prefs.edit().putString(KEY_SYNC_ERROR, "No credentials stored").apply()
                return@withContext
            }

            if (!authManager.isSessionValid()) {
                onLog("[SyncManager] syncNow: Session is not valid")
                prefs.edit().putString(KEY_SYNC_ERROR, "Session invalid").apply()
                return@withContext
            }

            if (!healthConnectManager.checkAvailability()) {
                onLog("[SyncManager] syncNow: Health Connect not available")
                prefs.edit().putString(KEY_SYNC_ERROR, "Health Connect not available").apply()
                return@withContext
            }

            val days = prefs.getInt(KEY_SYNC_DAYS_BACK, DEFAULT_SYNC_DAYS_BACK)
            val endTime = Instant.now()
            val startTime = endTime.minusSeconds(days.toLong() * 24 * 60 * 60)

            onLog("[SyncManager] Reading Health Connect data from $startTime to $endTime")

            val syncData = healthConnectManager.readRecords(
                types = emptyList(), // empty = all supported
                startTime = startTime,
                endTime = endTime
            )

            val totalRecords = syncData.records.size + syncData.sleep.size + syncData.workouts.size
            onLog("[SyncManager] Read $totalRecords records total")

            if (totalRecords == 0) {
                onLog("[SyncManager] No data to sync")
                prefs.edit().putString(KEY_LAST_SYNC_SUCCESS, nowIso).apply()
                return@withContext
            }

            val payload = SyncRequest(
                provider = "google",
                sdkVersion = SDK_VERSION,
                syncTimestamp = nowIso,
                data = syncData
            )

            val success = apiClient.syncData(creds.userId, payload)
            if (success) {
                onLog("[SyncManager] Sync succeeded")
                prefs.edit()
                    .putString(KEY_LAST_SYNC_SUCCESS, nowIso)
                    .remove(KEY_SYNC_ERROR)
                    .apply()
                saveAnchors(endTime)
            } else {
                val err = "Sync request failed"
                onLog("[SyncManager] $err")
                prefs.edit().putString(KEY_SYNC_ERROR, err).apply()
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            onLog("[SyncManager] syncNow error: $msg")
            prefs.edit().putString(KEY_SYNC_ERROR, msg).apply()
        } finally {
            isSyncing.set(false)
        }
    }

    // ---------------------------------------------------------------------------
    // Anchors
    // ---------------------------------------------------------------------------

    /**
     * Saves the last-synced timestamp for each data type as the provided Instant.
     * Using a single timestamp for all types for simplicity (can be extended per-type).
     */
    private fun saveAnchors(syncedUpTo: Instant) {
        val isoStr = syncedUpTo.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val editor = prefs.edit()
        listOf(
            "steps", "heartRate", "distance", "activeCalories", "basalMetabolicRate",
            "weight", "height", "bloodPressure", "hrv", "oxygenSaturation",
            "bloodGlucose", "respiratoryRate", "bodyTemperature", "bodyFat",
            "restingHeartRate", "floorsClimbed", "vo2Max", "hydration", "sleep", "workout"
        ).forEach { type ->
            editor.putString("$ANCHOR_PREFIX$type", isoStr)
        }
        editor.apply()
    }

    fun resetAnchors() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(ANCHOR_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
        onLog("[SyncManager] Anchors reset")
    }

    fun getAnchor(type: String): Instant? {
        val isoStr = prefs.getString("$ANCHOR_PREFIX$type", null) ?: return null
        return try {
            OffsetDateTime.parse(isoStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (e: Exception) {
            null
        }
    }
}
