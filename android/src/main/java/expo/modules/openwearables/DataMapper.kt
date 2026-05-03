package expo.modules.openwearables

import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Device
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

object DataMapper {

    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    // ---------------------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------------------

    fun mapToSyncPayload(
        steps: List<StepsRecord> = emptyList(),
        heartRates: List<HeartRateRecord> = emptyList(),
        distances: List<DistanceRecord> = emptyList(),
        activeCalories: List<ActiveCaloriesBurnedRecord> = emptyList(),
        basalMetabolicRates: List<BasalMetabolicRateRecord> = emptyList(),
        weights: List<WeightRecord> = emptyList(),
        heights: List<HeightRecord> = emptyList(),
        bloodPressures: List<BloodPressureRecord> = emptyList(),
        hrvRmssd: List<HeartRateVariabilityRmssdRecord> = emptyList(),
        oxygenSaturations: List<OxygenSaturationRecord> = emptyList(),
        bloodGlucoses: List<BloodGlucoseRecord> = emptyList(),
        respiratoryRates: List<RespiratoryRateRecord> = emptyList(),
        bodyTemperatures: List<BodyTemperatureRecord> = emptyList(),
        bodyFats: List<BodyFatRecord> = emptyList(),
        restingHeartRates: List<RestingHeartRateRecord> = emptyList(),
        floorsClimbed: List<FloorsClimbedRecord> = emptyList(),
        vo2Max: List<Vo2MaxRecord> = emptyList(),
        hydration: List<HydrationRecord> = emptyList(),
        sleepSessions: List<SleepSessionRecord> = emptyList(),
        exercises: List<ExerciseSessionRecord> = emptyList()
    ): SyncRequestData {

        val records = mutableListOf<MetricRecord>()

        steps.forEach { records.addAll(mapSteps(it)) }
        heartRates.forEach { records.addAll(mapHeartRate(it)) }
        distances.forEach { records.addAll(mapDistance(it)) }
        activeCalories.forEach { records.addAll(mapActiveCalories(it)) }
        basalMetabolicRates.forEach { records.addAll(mapBasalMetabolicRate(it)) }
        weights.forEach { records.addAll(mapWeight(it)) }
        heights.forEach { records.addAll(mapHeight(it)) }
        bloodPressures.forEach { records.addAll(mapBloodPressure(it)) }
        hrvRmssd.forEach { records.addAll(mapHrv(it)) }
        oxygenSaturations.forEach { records.addAll(mapOxygenSaturation(it)) }
        bloodGlucoses.forEach { records.addAll(mapBloodGlucose(it)) }
        respiratoryRates.forEach { records.addAll(mapRespiratoryRate(it)) }
        bodyTemperatures.forEach { records.addAll(mapBodyTemperature(it)) }
        bodyFats.forEach { records.addAll(mapBodyFat(it)) }
        restingHeartRates.forEach { records.addAll(mapRestingHeartRate(it)) }
        floorsClimbed.forEach { records.addAll(mapFloorsClimbed(it)) }
        vo2Max.forEach { records.addAll(mapVo2Max(it)) }
        hydration.forEach { records.addAll(mapHydration(it)) }

        val sleepRecords = sleepSessions.flatMap { mapSleepSession(it) }
        val workoutRecords = exercises.map { mapExerciseSession(it) }

        return SyncRequestData(
            records = records,
            sleep = sleepRecords,
            workouts = workoutRecords
        )
    }

    // ---------------------------------------------------------------------------
    // Metric mappers
    // ---------------------------------------------------------------------------

    private fun mapSteps(record: StepsRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "STEP_COUNT",
                startDate = record.startTime.atOffset(record.startZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                endDate = record.endTime.atOffset(record.endZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                value = record.count.toDouble(),
                unit = "count",
                zoneOffset = (record.startZoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapHeartRate(record: HeartRateRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val parentId = record.metadata.id.ifBlank { UUID.randomUUID().toString() }
        return record.samples.map { sample ->
            MetricRecord(
                id = UUID.randomUUID().toString(),
                parentId = parentId,
                type = "HEART_RATE",
                startDate = sample.time.atOffset(record.startZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                endDate = sample.time.atOffset(record.endZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                value = sample.beatsPerMinute.toDouble(),
                unit = "bpm",
                zoneOffset = (record.startZoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        }
    }

    private fun mapDistance(record: DistanceRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "DISTANCE",
                startDate = record.startTime.atOffset(record.startZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                endDate = record.endTime.atOffset(record.endZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                value = record.distance.inMeters,
                unit = "m",
                zoneOffset = (record.startZoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapActiveCalories(record: ActiveCaloriesBurnedRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "ACTIVE_CALORIES_BURNED",
                startDate = record.startTime.atOffset(record.startZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                endDate = record.endTime.atOffset(record.endZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                value = record.energy.inKilocalories,
                unit = "kcal",
                zoneOffset = (record.startZoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapBasalMetabolicRate(record: BasalMetabolicRateRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "BASAL_METABOLIC_RATE",
                startDate = timeStr,
                endDate = timeStr,
                value = record.basalMetabolicRate.inKilocaloriesPerDay,
                unit = "kcal/day",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapWeight(record: WeightRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "WEIGHT",
                startDate = timeStr,
                endDate = timeStr,
                value = record.weight.inKilograms,
                unit = "kg",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapHeight(record: HeightRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "HEIGHT",
                startDate = timeStr,
                endDate = timeStr,
                value = record.height.inMeters,
                unit = "m",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapBloodPressure(record: BloodPressureRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        val parentId = record.metadata.id.ifBlank { UUID.randomUUID().toString() }
        return listOf(
            MetricRecord(
                id = UUID.randomUUID().toString(),
                parentId = parentId,
                type = "BLOOD_PRESSURE_SYSTOLIC",
                startDate = timeStr,
                endDate = timeStr,
                value = record.systolic.inMillimetersOfMercury,
                unit = "mmHg",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            ),
            MetricRecord(
                id = UUID.randomUUID().toString(),
                parentId = parentId,
                type = "BLOOD_PRESSURE_DIASTOLIC",
                startDate = timeStr,
                endDate = timeStr,
                value = record.diastolic.inMillimetersOfMercury,
                unit = "mmHg",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapHrv(record: HeartRateVariabilityRmssdRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "HEART_RATE_VARIABILITY",
                startDate = timeStr,
                endDate = timeStr,
                value = record.heartRateVariabilityMillis,
                unit = "ms",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapOxygenSaturation(record: OxygenSaturationRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "OXYGEN_SATURATION",
                startDate = timeStr,
                endDate = timeStr,
                value = record.percentage.value,
                unit = "%",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapBloodGlucose(record: BloodGlucoseRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "BLOOD_GLUCOSE",
                startDate = timeStr,
                endDate = timeStr,
                value = record.level.inMillimolesPerLiter,
                unit = "mmol/L",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapRespiratoryRate(record: RespiratoryRateRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "RESPIRATORY_RATE",
                startDate = timeStr,
                endDate = timeStr,
                value = record.rate,
                unit = "breaths/min",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapBodyTemperature(record: BodyTemperatureRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "BODY_TEMPERATURE",
                startDate = timeStr,
                endDate = timeStr,
                value = record.temperature.inCelsius,
                unit = "°C",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapBodyFat(record: BodyFatRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "BODY_FAT",
                startDate = timeStr,
                endDate = timeStr,
                value = record.percentage.value,
                unit = "%",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapRestingHeartRate(record: RestingHeartRateRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "RESTING_HEART_RATE",
                startDate = timeStr,
                endDate = timeStr,
                value = record.beatsPerMinute.toDouble(),
                unit = "bpm",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapFloorsClimbed(record: FloorsClimbedRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "FLOORS_CLIMBED",
                startDate = record.startTime.atOffset(record.startZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                endDate = record.endTime.atOffset(record.endZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                value = record.floors,
                unit = "count",
                zoneOffset = (record.startZoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapVo2Max(record: Vo2MaxRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        val time = record.time.atOffset(record.zoneOffset ?: ZoneOffset.UTC)
        val timeStr = time.format(isoFormatter)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "VO2_MAX",
                startDate = timeStr,
                endDate = timeStr,
                value = record.vo2MillilitersPerMinuteKilogram,
                unit = "mL/kg/min",
                zoneOffset = (record.zoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    private fun mapHydration(record: HydrationRecord): List<MetricRecord> {
        val source = deviceSource(record.metadata.device)
        return listOf(
            MetricRecord(
                id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
                parentId = null,
                type = "HYDRATION",
                startDate = record.startTime.atOffset(record.startZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                endDate = record.endTime.atOffset(record.endZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                value = record.volume.inLiters,
                unit = "L",
                zoneOffset = (record.startZoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        )
    }

    // ---------------------------------------------------------------------------
    // Sleep mapper
    // ---------------------------------------------------------------------------

    fun mapSleepSession(record: SleepSessionRecord): List<SleepRecord> {
        val source = deviceSource(record.metadata.device)
        val sessionId = record.metadata.id.ifBlank { UUID.randomUUID().toString() }

        if (record.stages.isEmpty()) {
            // No stages — emit a single "light" record for the whole session
            return listOf(
                SleepRecord(
                    id = UUID.randomUUID().toString(),
                    parentId = sessionId,
                    stage = "light",
                    startDate = record.startTime.atOffset(record.startZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                    endDate = record.endTime.atOffset(record.endZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                    zoneOffset = (record.startZoneOffset ?: ZoneOffset.UTC).id,
                    source = source
                )
            )
        }

        return record.stages.map { stage ->
            SleepRecord(
                id = UUID.randomUUID().toString(),
                parentId = sessionId,
                stage = mapSleepStage(stage.stage),
                startDate = stage.startTime.atOffset(record.startZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                endDate = stage.endTime.atOffset(record.endZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
                zoneOffset = (record.startZoneOffset ?: ZoneOffset.UTC).id,
                source = source
            )
        }
    }

    private fun mapSleepStage(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "awake"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
        SleepSessionRecord.STAGE_TYPE_REM -> "rem"
        SleepSessionRecord.STAGE_TYPE_LIGHT,
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "light"
        else -> "light"
    }

    // ---------------------------------------------------------------------------
    // Exercise / Workout mapper
    // ---------------------------------------------------------------------------

    fun mapExerciseSession(record: ExerciseSessionRecord): WorkoutRecord {
        val source = deviceSource(record.metadata.device)

        val values = mutableListOf<WorkoutStatistic>()

        record.segments.forEach { segment ->
            // segments carry repetitions for some exercise types; skip if 0
        }

        // lap data can be aggregated at read time; include static values only
        val laps = record.laps
        if (laps.isNotEmpty()) {
            val totalDistance = laps.sumOf { it.length?.inMeters ?: 0.0 }
            if (totalDistance > 0) {
                values.add(WorkoutStatistic("distance", "m", totalDistance))
            }
        }

        val durationMs = record.endTime.toEpochMilli() - record.startTime.toEpochMilli()
        values.add(WorkoutStatistic("duration", "ms", durationMs.toDouble()))

        return WorkoutRecord(
            id = record.metadata.id.ifBlank { UUID.randomUUID().toString() },
            type = mapExerciseType(record.exerciseType),
            startDate = record.startTime.atOffset(record.startZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
            endDate = record.endTime.atOffset(record.endZoneOffset ?: ZoneOffset.UTC).format(isoFormatter),
            zoneOffset = (record.startZoneOffset ?: ZoneOffset.UTC).id,
            source = source,
            values = values
        )
    }

    private fun mapExerciseType(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "running"

        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "cycling"

        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walking"

        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "swimming"

        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "strength_training"

        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"

        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "hiking"

        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "rowing"

        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "elliptical"

        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "hiit"

        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "pilates"

        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "dancing"

        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "golf"

        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "tennis"

        ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "badminton"

        ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> "volleyball"

        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "basketball"

        ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "football"

        ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> "australian_football"

        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "soccer"

        ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL -> "softball"

        ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL -> "baseball"

        ExerciseSessionRecord.EXERCISE_TYPE_CRICKET -> "cricket"

        ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> "rugby"

        ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "skiing"

        ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "snowboarding"

        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "stair_climbing"

        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "stretching"

        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "calisthenics"

        ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "martial_arts"

        ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "boxing"

        ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC -> "frisbee"

        ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING -> "guided_breathing"

        ExerciseSessionRecord.EXERCISE_TYPE_CROSS_COUNTRY_SKIING -> "cross_country_skiing"

        ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING -> "ice_skating"

        ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY -> "ice_hockey"

        ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "rock_climbing"

        ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO -> "water_polo"

        ExerciseSessionRecord.EXERCISE_TYPE_PADDLING -> "paddling"

        ExerciseSessionRecord.EXERCISE_TYPE_PARA_GLIDING -> "paragliding"

        ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS -> "gymnastics"

        else -> "other"
    }

    // ---------------------------------------------------------------------------
    // Device/source helpers
    // ---------------------------------------------------------------------------

    private fun deviceSource(device: Device?): SourceInfo {
        return SourceInfo(
            deviceId = device?.identifier ?: "unknown",
            deviceName = device?.model ?: "unknown",
            deviceManufacturer = device?.manufacturer ?: "unknown",
            deviceModel = device?.model ?: "unknown",
            deviceType = mapDeviceType(device?.type)
        )
    }

    private fun mapDeviceType(type: Int?): String = when (type) {
        Device.TYPE_WATCH -> "watch"
        Device.TYPE_PHONE -> "phone"
        Device.TYPE_SCALE -> "scale"
        Device.TYPE_RING -> "ring"
        Device.TYPE_HEAD_MOUNTED -> "head_mounted"
        Device.TYPE_FITNESS_BAND -> "fitness_band"
        Device.TYPE_CHEST_STRAP -> "chest_strap"
        Device.TYPE_SMART_DISPLAY -> "smart_display"
        else -> "unknown"
    }
}
