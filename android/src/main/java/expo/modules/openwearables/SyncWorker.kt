package expo.modules.openwearables

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "OW_SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker starting")

        val syncDaysBack = inputData.getInt(SyncManager.DATA_KEY_SYNC_DAYS_BACK, 7)

        return try {
            val authManager = AuthManager(applicationContext)
            if (!authManager.isSessionValid()) {
                Log.w(TAG, "No valid session, skipping sync")
                return Result.success()
            }

            val logFn: (String) -> Unit = { msg -> Log.d(TAG, msg) }
            val authErrorFn: (Int, String) -> Unit = { code, msg ->
                Log.w(TAG, "Auth error $code: $msg")
            }

            val apiClient = ApiClient(authManager, logFn, authErrorFn)
            val healthConnectManager = HealthConnectManager(applicationContext, logFn)

            if (!healthConnectManager.checkAvailability()) {
                Log.w(TAG, "Health Connect not available")
                return Result.success()
            }

            val syncManager = SyncManager(
                context = applicationContext,
                authManager = authManager,
                healthConnectManager = healthConnectManager,
                apiClient = apiClient,
                onLog = logFn,
                onAuthError = authErrorFn
            )

            // Override the days back from worker input data
            val prefs = applicationContext.getSharedPreferences("ow_sync_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt(SyncManager.DATA_KEY_SYNC_DAYS_BACK, syncDaysBack).apply()

            syncManager.syncNow()

            Log.d(TAG, "SyncWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
