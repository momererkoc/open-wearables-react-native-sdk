import Foundation

// MARK: - Sync Request

struct SyncRequest: Codable {
    let provider: String
    let sdkVersion: String
    let syncTimestamp: String
    let data: SyncRequestData
}

struct SyncRequestData: Codable {
    let records: [MetricRecord]
    let sleep: [SleepRecord]
    let workouts: [WorkoutRecord]
}

// MARK: - Metric Record

struct MetricRecord: Codable {
    let id: String
    let parentId: String?
    let type: String
    let startDate: String
    let endDate: String
    let value: Double
    let unit: String
    let zoneOffset: String
    let source: SourceInfo
}

// MARK: - Sleep Record

struct SleepRecord: Codable {
    let id: String
    let parentId: String?
    let stage: String
    let startDate: String
    let endDate: String
    let zoneOffset: String
    let source: SourceInfo
    let values: [WorkoutStatistic]?
}

// MARK: - Workout Record

struct WorkoutRecord: Codable {
    let id: String
    let type: String
    let startDate: String
    let endDate: String
    let zoneOffset: String
    let source: SourceInfo
    let values: [WorkoutStatistic]
    let route: [RoutePoint]?
    let samples: [MetricRecord]?
}

// MARK: - Source Info

struct SourceInfo: Codable {
    let name: String
    let bundleIdentifier: String
    let deviceModel: String?
    let deviceManufacturer: String?
    let deviceType: String?
}

// MARK: - Workout Statistic

struct WorkoutStatistic: Codable {
    let type: String
    let unit: String
    let value: Double
}

// MARK: - Route Point

struct RoutePoint: Codable {
    let latitude: Double
    let longitude: Double
    let altitude: Double?
    let timestamp: String
}

// MARK: - Token Response

struct TokenResponse: Codable {
    let access_token: String
    let refresh_token: String?
}

// MARK: - Sync Data (intermediate, used internally)

struct SyncData {
    var records: [MetricRecord] = []
    var sleep: [SleepRecord] = []
    var workouts: [WorkoutRecord] = []

    mutating func merge(_ other: SyncData) {
        records.append(contentsOf: other.records)
        sleep.append(contentsOf: other.sleep)
        workouts.append(contentsOf: other.workouts)
    }
}

// MARK: - Stored Credentials

struct StoredCredentials {
    let userId: String?
    let accessToken: String?
    let refreshToken: String?
    let apiKey: String?
    let host: String?

    func toDictionary() -> [String: Any] {
        var dict: [String: Any] = [:]
        if let v = userId { dict["userId"] = v }
        if let v = accessToken { dict["accessToken"] = v }
        if let v = refreshToken { dict["refreshToken"] = v }
        if let v = apiKey { dict["apiKey"] = v }
        if let v = host { dict["host"] = v }
        return dict
    }
}

// MARK: - Log Level

enum OWLogLevel: Int {
    case none = 0
    case always = 1
    case debug = 2
}
