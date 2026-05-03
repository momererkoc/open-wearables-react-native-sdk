package expo.modules.openwearables

data class SourceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val deviceType: String
)

data class MetricRecord(
    val id: String,
    val parentId: String?,
    val type: String,
    val startDate: String,
    val endDate: String,
    val value: Double,
    val unit: String,
    val zoneOffset: String?,
    val source: SourceInfo
)

data class SleepRecord(
    val id: String,
    val parentId: String?,
    val stage: String,
    val startDate: String,
    val endDate: String,
    val zoneOffset: String?,
    val source: SourceInfo,
    val values: List<Any> = emptyList()
)

data class WorkoutStatistic(
    val type: String,
    val unit: String,
    val value: Double
)

data class WorkoutRecord(
    val id: String,
    val type: String,
    val startDate: String,
    val endDate: String,
    val zoneOffset: String?,
    val source: SourceInfo,
    val values: List<WorkoutStatistic>
)

data class SyncRequestData(
    val records: List<MetricRecord>,
    val sleep: List<SleepRecord>,
    val workouts: List<WorkoutRecord>
)

data class SyncRequest(
    val provider: String,
    val sdkVersion: String,
    val syncTimestamp: String,
    val data: SyncRequestData
)

data class TokenResponse(
    val access_token: String?,
    val refresh_token: String?
)

data class Credentials(
    val userId: String,
    val accessToken: String?,
    val refreshToken: String?,
    val apiKey: String?,
    val host: String,
    val customSyncURL: String?
)
