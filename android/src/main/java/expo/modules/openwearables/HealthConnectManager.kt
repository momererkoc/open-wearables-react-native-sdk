package expo.modules.openwearables

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.reflect.KClass

class HealthConnectManager(
    private val context: Context,
    private val onLog: (String) -> Unit
) {

    private var healthConnectClient: HealthConnectClient? = null

    // Callback to resolve a pending permission promise
    var onPermissionResult: ((Boolean) -> Unit)? = null

    // ---------------------------------------------------------------------------
    // Availability
    // ---------------------------------------------------------------------------

    fun checkAvailability(): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)
        val available = status == HealthConnectClient.SDK_AVAILABLE
        if (available && healthConnectClient == null) {
            healthConnectClient = HealthConnectClient.getOrCreate(context)
        }
        return available
    }

    private fun requireClient(): HealthConnectClient {
        if (healthConnectClient == null) {
            if (!checkAvailability()) {
                throw IllegalStateException("Health Connect is not available on this device")
            }
        }
        return healthConnectClient!!
    }

    // ---------------------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------------------

    suspend fun getGrantedPermissions(): Set<String> = withContext(Dispatchers.IO) {
        try {
            val client = requireClient()
            val granted = client.permissionController.getGrantedPermissions()
            granted.map { it }.toSet()
        } catch (e: Exception) {
            onLog("[HealthConnectManager] getGrantedPermissions error: ${e.message}")
            emptySet()
        }
    }

    /**
     * Builds the full set of HealthConnect permission strings for the requested type identifiers.
     */
    fun buildPermissions(types: List<String>): Set<String> {
        val perms = mutableSetOf<String>()
        types.forEach { type ->
            val recordClass = typeToRecordClass(type)
            if (recordClass != null) {
                perms.add(HealthPermission.getReadPermission(recordClass))
            }
        }
        return perms
    }

    /**
     * Creates the ActivityResultContract for requesting Health Connect permissions.
     * The caller is responsible for registering and launching it.
     */
    fun createPermissionContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    /**
     * Returns true if all requested permissions are already granted.
     */
    suspend fun hasAllPermissions(types: List<String>): Boolean {
        val required = buildPermissions(types)
        val granted = getGrantedPermissions()
        return granted.containsAll(required)
    }

    /**
     * Launches the Health Connect permission dialog using the provided launcher.
     * onPermissionResult will be called with the result.
     */
    fun requestPermissions(
        types: List<String>,
        launcher: ActivityResultLauncher<Set<String>>
    ) {
        val permissions = buildPermissions(types)
        onLog("[HealthConnectManager] Requesting ${permissions.size} permissions")
        launcher.launch(permissions)
    }

    // ---------------------------------------------------------------------------
    // Reading records
    // ---------------------------------------------------------------------------

    suspend fun readRecords(
        types: List<String>,
        startTime: Instant,
        endTime: Instant
    ): SyncRequestData = withContext(Dispatchers.IO) {
        val client = requireClient()
        val filter = TimeRangeFilter.between(startTime, endTime)

        val steps = mutableListOf<StepsRecord>()
        val heartRates = mutableListOf<HeartRateRecord>()
        val distances = mutableListOf<DistanceRecord>()
        val activeCalories = mutableListOf<ActiveCaloriesBurnedRecord>()
        val basalMetabolicRates = mutableListOf<BasalMetabolicRateRecord>()
        val weights = mutableListOf<WeightRecord>()
        val heights = mutableListOf<HeightRecord>()
        val bloodPressures = mutableListOf<BloodPressureRecord>()
        val hrvRmssd = mutableListOf<HeartRateVariabilityRmssdRecord>()
        val oxygenSaturations = mutableListOf<OxygenSaturationRecord>()
        val bloodGlucoses = mutableListOf<BloodGlucoseRecord>()
        val respiratoryRates = mutableListOf<RespiratoryRateRecord>()
        val bodyTemperatures = mutableListOf<BodyTemperatureRecord>()
        val bodyFats = mutableListOf<BodyFatRecord>()
        val restingHeartRates = mutableListOf<RestingHeartRateRecord>()
        val floorsClimbed = mutableListOf<FloorsClimbedRecord>()
        val vo2Max = mutableListOf<Vo2MaxRecord>()
        val hydration = mutableListOf<HydrationRecord>()
        val sleepSessions = mutableListOf<SleepSessionRecord>()
        val exercises = mutableListOf<ExerciseSessionRecord>()

        val effectiveTypes = if (types.isEmpty()) allSupportedTypes() else types

        for (type in effectiveTypes) {
            try {
                when (type) {
                    "steps", "STEP_COUNT" -> {
                        val result = client.readRecords(ReadRecordsRequest(StepsRecord::class, filter))
                        steps.addAll(result.records)
                    }
                    "heartRate", "HEART_RATE" -> {
                        val result = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, filter))
                        heartRates.addAll(result.records)
                    }
                    "distanceWalkingRunning", "distanceCycling", "DISTANCE" -> {
                        val result = client.readRecords(ReadRecordsRequest(DistanceRecord::class, filter))
                        distances.addAll(result.records)
                    }
                    "activeEnergy", "ACTIVE_CALORIES_BURNED" -> {
                        val result = client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, filter))
                        activeCalories.addAll(result.records)
                    }
                    "basalEnergy", "BASAL_METABOLIC_RATE" -> {
                        val result = client.readRecords(ReadRecordsRequest(BasalMetabolicRateRecord::class, filter))
                        basalMetabolicRates.addAll(result.records)
                    }
                    "bodyMass", "WEIGHT" -> {
                        val result = client.readRecords(ReadRecordsRequest(WeightRecord::class, filter))
                        weights.addAll(result.records)
                    }
                    "height", "HEIGHT" -> {
                        val result = client.readRecords(ReadRecordsRequest(HeightRecord::class, filter))
                        heights.addAll(result.records)
                    }
                    "bloodPressure", "bloodPressureSystolic", "bloodPressureDiastolic", "BLOOD_PRESSURE" -> {
                        val result = client.readRecords(ReadRecordsRequest(BloodPressureRecord::class, filter))
                        bloodPressures.addAll(result.records)
                    }
                    "heartRateVariabilitySDNN", "HEART_RATE_VARIABILITY" -> {
                        val result = client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, filter))
                        hrvRmssd.addAll(result.records)
                    }
                    "oxygenSaturation", "bloodOxygen", "OXYGEN_SATURATION" -> {
                        val result = client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, filter))
                        oxygenSaturations.addAll(result.records)
                    }
                    "bloodGlucose", "BLOOD_GLUCOSE" -> {
                        val result = client.readRecords(ReadRecordsRequest(BloodGlucoseRecord::class, filter))
                        bloodGlucoses.addAll(result.records)
                    }
                    "respiratoryRate", "RESPIRATORY_RATE" -> {
                        val result = client.readRecords(ReadRecordsRequest(RespiratoryRateRecord::class, filter))
                        respiratoryRates.addAll(result.records)
                    }
                    "bodyTemperature", "BODY_TEMPERATURE" -> {
                        val result = client.readRecords(ReadRecordsRequest(BodyTemperatureRecord::class, filter))
                        bodyTemperatures.addAll(result.records)
                    }
                    "bodyFatPercentage", "BODY_FAT" -> {
                        val result = client.readRecords(ReadRecordsRequest(BodyFatRecord::class, filter))
                        bodyFats.addAll(result.records)
                    }
                    "restingHeartRate", "RESTING_HEART_RATE" -> {
                        val result = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, filter))
                        restingHeartRates.addAll(result.records)
                    }
                    "flightsClimbed", "FLOORS_CLIMBED" -> {
                        val result = client.readRecords(ReadRecordsRequest(FloorsClimbedRecord::class, filter))
                        floorsClimbed.addAll(result.records)
                    }
                    "vo2Max", "VO2_MAX" -> {
                        val result = client.readRecords(ReadRecordsRequest(Vo2MaxRecord::class, filter))
                        vo2Max.addAll(result.records)
                    }
                    "dietaryWater", "HYDRATION" -> {
                        val result = client.readRecords(ReadRecordsRequest(HydrationRecord::class, filter))
                        hydration.addAll(result.records)
                    }
                    "sleep", "SLEEP" -> {
                        val result = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter))
                        sleepSessions.addAll(result.records)
                    }
                    "workout", "EXERCISE" -> {
                        val result = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, filter))
                        exercises.addAll(result.records)
                    }
                    else -> {
                        onLog("[HealthConnectManager] Unknown data type: $type, skipping")
                    }
                }
            } catch (e: Exception) {
                onLog("[HealthConnectManager] Failed to read $type: ${e.message}")
            }
        }

        onLog("[HealthConnectManager] Read complete: steps=${steps.size}, hr=${heartRates.size}, sleep=${sleepSessions.size}, workouts=${exercises.size}")

        DataMapper.mapToSyncPayload(
            steps = steps,
            heartRates = heartRates,
            distances = distances,
            activeCalories = activeCalories,
            basalMetabolicRates = basalMetabolicRates,
            weights = weights,
            heights = heights,
            bloodPressures = bloodPressures,
            hrvRmssd = hrvRmssd,
            oxygenSaturations = oxygenSaturations,
            bloodGlucoses = bloodGlucoses,
            respiratoryRates = respiratoryRates,
            bodyTemperatures = bodyTemperatures,
            bodyFats = bodyFats,
            restingHeartRates = restingHeartRates,
            floorsClimbed = floorsClimbed,
            vo2Max = vo2Max,
            hydration = hydration,
            sleepSessions = sleepSessions,
            exercises = exercises
        )
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun typeToRecordClass(type: String): KClass<out Record>? = when (type) {
        "steps", "STEP_COUNT" -> StepsRecord::class
        "heartRate", "HEART_RATE" -> HeartRateRecord::class
        "distanceWalkingRunning", "distanceCycling", "DISTANCE" -> DistanceRecord::class
        "activeEnergy", "ACTIVE_CALORIES_BURNED" -> ActiveCaloriesBurnedRecord::class
        "basalEnergy", "BASAL_METABOLIC_RATE" -> BasalMetabolicRateRecord::class
        "bodyMass", "WEIGHT" -> WeightRecord::class
        "height", "HEIGHT" -> HeightRecord::class
        "bloodPressure", "bloodPressureSystolic", "bloodPressureDiastolic", "BLOOD_PRESSURE" -> BloodPressureRecord::class
        "heartRateVariabilitySDNN", "HEART_RATE_VARIABILITY" -> HeartRateVariabilityRmssdRecord::class
        "oxygenSaturation", "bloodOxygen", "OXYGEN_SATURATION" -> OxygenSaturationRecord::class
        "bloodGlucose", "BLOOD_GLUCOSE" -> BloodGlucoseRecord::class
        "respiratoryRate", "RESPIRATORY_RATE" -> RespiratoryRateRecord::class
        "bodyTemperature", "BODY_TEMPERATURE" -> BodyTemperatureRecord::class
        "bodyFatPercentage", "BODY_FAT" -> BodyFatRecord::class
        "restingHeartRate", "RESTING_HEART_RATE" -> RestingHeartRateRecord::class
        "flightsClimbed", "FLOORS_CLIMBED" -> FloorsClimbedRecord::class
        "vo2Max", "VO2_MAX" -> Vo2MaxRecord::class
        "dietaryWater", "HYDRATION" -> HydrationRecord::class
        "sleep", "SLEEP" -> SleepSessionRecord::class
        "workout", "EXERCISE" -> ExerciseSessionRecord::class
        else -> null
    }

    private fun allSupportedTypes(): List<String> = listOf(
        "steps", "heartRate", "distanceWalkingRunning", "activeEnergy", "basalEnergy",
        "bodyMass", "height", "bloodPressure", "heartRateVariabilitySDNN", "oxygenSaturation",
        "bloodGlucose", "respiratoryRate", "bodyTemperature", "bodyFatPercentage",
        "restingHeartRate", "flightsClimbed", "vo2Max", "dietaryWater", "sleep", "workout"
    )
}
