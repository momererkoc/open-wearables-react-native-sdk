import Foundation
import HealthKit
import BackgroundTasks

// MARK: - Sync Status Keys

private enum UserDefaultsKey {
    static let lastSyncDate = "com.openwearables.rnsdk.lastSyncDate"
    static let syncAnchors = "com.openwearables.rnsdk.anchors"
    static let activeSyncTypes = "com.openwearables.rnsdk.activeTypes"
    static let syncDaysBack = "com.openwearables.rnsdk.syncDaysBack"
}

private enum BGTaskIdentifier {
    static let refresh = "com.openwearables.healthsdk.task.refresh"
    static let process = "com.openwearables.healthsdk.task.process"
}

// MARK: - SyncManager

final class SyncManager {

    static let shared = SyncManager()
    private init() {}

    // MARK: - State

    private(set) var isSyncActive: Bool = false
    private var syncTask: Task<Void, Never>?
    private var isCurrentlySyncing: Bool = false
    private var lastSyncDate: Date? {
        get {
            let ts = UserDefaults.standard.double(forKey: UserDefaultsKey.lastSyncDate)
            return ts > 0 ? Date(timeIntervalSince1970: ts) : nil
        }
        set {
            if let d = newValue {
                UserDefaults.standard.set(d.timeIntervalSince1970, forKey: UserDefaultsKey.lastSyncDate)
            } else {
                UserDefaults.standard.removeObject(forKey: UserDefaultsKey.lastSyncDate)
            }
        }
    }
    private var activeTypes: [String] {
        get { UserDefaults.standard.stringArray(forKey: UserDefaultsKey.activeSyncTypes) ?? [] }
        set { UserDefaults.standard.set(newValue, forKey: UserDefaultsKey.activeSyncTypes) }
    }
    private var syncDaysBack: Int {
        get {
            let v = UserDefaults.standard.integer(forKey: UserDefaultsKey.syncDaysBack)
            return v > 0 ? v : 30
        }
        set { UserDefaults.standard.set(newValue, forKey: UserDefaultsKey.syncDaysBack) }
    }

    // MARK: - Log / Event hooks

    var onLog: ((String) -> Void)?
    var onAuthError: ((Int, String) -> Void)?

    // MARK: - Start Background Sync

    func startBackgroundSync(syncDaysBack daysBack: Int?) async -> Bool {
        let daysBack = daysBack ?? 30
        self.syncDaysBack = daysBack

        guard HealthKitManager.shared.isAvailable() else {
            log("HealthKit not available on this device")
            return false
        }

        let types = resolveActiveTypes()
        self.activeTypes = types

        // Enable background delivery via HKObserverQuery
        HealthKitManager.shared.enableBackgroundDelivery(for: types) { [weak self] in
            guard let self = self else { return }
            Task { await self.syncNow() }
        }

        // Register BGTask handlers (idempotent)
        registerBGTasks()

        // Schedule initial refresh task
        scheduleBGAppRefreshTask()

        isSyncActive = true
        log("Background sync started. daysBack=\(daysBack), types=\(types.count)")
        return true
    }

    // MARK: - Stop Background Sync

    func stopBackgroundSync() async {
        isSyncActive = false
        syncTask?.cancel()
        syncTask = nil
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: BGTaskIdentifier.refresh)
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: BGTaskIdentifier.process)
        log("Background sync stopped")
    }

    // MARK: - Sync Now

    func syncNow() async {
        guard !isCurrentlySyncing else {
            log("Sync already in progress, skipping")
            return
        }
        isCurrentlySyncing = true
        defer { isCurrentlySyncing = false }

        let creds = AuthManager.shared.getCredentials()
        guard let userId = creds.userId, !userId.isEmpty,
              let host = creds.host, !host.isEmpty else {
            log("Cannot sync: missing credentials")
            return
        }

        let types = activeTypes.isEmpty ? resolveActiveTypes() : activeTypes
        let daysBack = syncDaysBack
        let endDate = Date()
        let startDate = lastSyncDate ?? Calendar.current.date(byAdding: .day, value: -daysBack, to: endDate) ?? endDate

        log("Syncing \(types.count) type(s) from \(startDate) to \(endDate)")

        let data = await HealthKitManager.shared.readSamples(
            types: types,
            startDate: startDate,
            endDate: endDate
        )

        log("Read \(data.records.count) records, \(data.sleep.count) sleep, \(data.workouts.count) workouts")

        guard !data.records.isEmpty || !data.sleep.isEmpty || !data.workouts.isEmpty else {
            log("No new data to sync")
            lastSyncDate = endDate
            return
        }

        do {
            try await ApiClient.shared.syncData(
                host: host,
                userId: userId,
                syncData: data,
                token: creds.accessToken,
                apiKey: creds.apiKey
            )
            lastSyncDate = endDate
            log("Sync completed successfully")
        } catch ApiError.httpError(let code, let body) {
            log("Sync failed: HTTP \(code) \(body)")
            if code == 401 {
                onAuthError?(code, body)
            }
        } catch {
            log("Sync failed: \(error.localizedDescription)")
        }

        // Reschedule background task after successful sync
        scheduleBGAppRefreshTask()
    }

    // MARK: - Resume Sync

    func resumeSync() async -> Bool {
        guard AuthManager.shared.isSessionValid() else {
            log("Cannot resume: no valid session")
            return false
        }
        if !isSyncActive {
            isSyncActive = true
        }
        await syncNow()
        return true
    }

    // MARK: - Reset Anchors

    func resetAnchors() {
        UserDefaults.standard.removeObject(forKey: UserDefaultsKey.lastSyncDate)
        UserDefaults.standard.removeObject(forKey: UserDefaultsKey.syncAnchors)
        log("Anchors reset")
    }

    // MARK: - Sync Status

    func getSyncStatus() -> [String: Any] {
        var status: [String: Any] = [:]
        status["isSyncActive"] = isSyncActive
        status["isCurrentlySyncing"] = isCurrentlySyncing
        status["syncDaysBack"] = syncDaysBack
        if let date = lastSyncDate {
            let iso = ISO8601DateFormatter()
            iso.formatOptions = [.withInternetDateTime]
            status["lastSyncDate"] = iso.string(from: date)
        }
        status["activeTypes"] = activeTypes
        return status
    }

    // MARK: - BGTask Registration

    func registerBGTasks() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: BGTaskIdentifier.refresh,
            using: nil
        ) { [weak self] task in
            self?.handleBGAppRefreshTask(task as! BGAppRefreshTask)
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: BGTaskIdentifier.process,
            using: nil
        ) { [weak self] task in
            self?.handleBGProcessingTask(task as! BGProcessingTask)
        }
    }

    private func scheduleBGAppRefreshTask() {
        let request = BGAppRefreshTaskRequest(identifier: BGTaskIdentifier.refresh)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60) // 15 min
        try? BGTaskScheduler.shared.submit(request)
    }

    private func handleBGAppRefreshTask(_ task: BGAppRefreshTask) {
        scheduleBGAppRefreshTask() // reschedule
        let syncTask = Task {
            await syncNow()
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = {
            syncTask.cancel()
        }
    }

    private func handleBGProcessingTask(_ task: BGProcessingTask) {
        let syncTask = Task {
            await syncNow()
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = {
            syncTask.cancel()
        }
    }

    // MARK: - Helpers

    private func resolveActiveTypes() -> [String] {
        // Default comprehensive type list
        return [
            "steps", "heartRate", "restingHeartRate", "heartRateVariabilitySDNN",
            "oxygenSaturation", "activeEnergy", "basalEnergy",
            "distanceWalkingRunning", "distanceCycling", "flightsClimbed",
            "bodyMass", "height", "bodyFatPercentage", "bmi", "leanBodyMass",
            "vo2Max", "bloodGlucose", "respiratoryRate", "bodyTemperature",
            "bloodPressure", "sleep", "workout"
        ]
    }

    private func log(_ message: String) {
        onLog?("[OW-Sync] \(message)")
    }
}
