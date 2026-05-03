import ExpoModulesCore
import HealthKit

public class OpenWearablesModule: Module {

    // MARK: - Log level

    private var logLevel: OWLogLevel = .always

    // MARK: - Module Definition

    public func definition() -> ModuleDefinition {
        Name("OpenWearablesHealthSDK")

        // MARK: Events
        Events("onLog", "onAuthError")

        // MARK: Lifecycle
        OnCreate {
            SyncManager.shared.onLog = { [weak self] message in
                guard let self = self else { return }
                self.emitLog(message)
            }
            SyncManager.shared.onAuthError = { [weak self] code, message in
                guard let self = self else { return }
                self.sendEvent("onAuthError", [
                    "statusCode": code,
                    "message": message
                ])
            }
        }

        // MARK: - Configure
        Function("configure") { (host: String, customSyncURL: String?) in
            // Persist the host. If credentials already exist, update them in-place.
            let creds = AuthManager.shared.getCredentials()
            if let userId = creds.userId, !userId.isEmpty {
                AuthManager.shared.saveCredentials(
                    userId: userId,
                    accessToken: creds.accessToken,
                    refreshToken: creds.refreshToken,
                    apiKey: creds.apiKey,
                    host: host
                )
            } else {
                // No signed-in user yet — just store the host for later use by signIn.
                AuthManager.shared.saveHost(host)
            }
            self.emitLog("Configured with host: \(host)")
        }

        // MARK: - Auth
        AsyncFunction("signIn") { (
            userId: String,
            accessToken: String?,
            refreshToken: String?,
            apiKey: String?,
            promise: Promise
        ) in
            let creds = AuthManager.shared.getCredentials()
            let host = creds.host ?? ""
            AuthManager.shared.saveCredentials(
                userId: userId,
                accessToken: accessToken,
                refreshToken: refreshToken,
                apiKey: apiKey,
                host: host
            )
            self.emitLog("Signed in userId=\(userId)")
            promise.resolve()
        }

        AsyncFunction("signOut") { (promise: Promise) in
            await SyncManager.shared.stopBackgroundSync()
            AuthManager.shared.clearCredentials()
            self.emitLog("Signed out")
            promise.resolve()
        }

        Function("updateTokens") { (accessToken: String, refreshToken: String) in
            AuthManager.shared.updateTokens(accessToken: accessToken, refreshToken: refreshToken)
            self.emitLog("Tokens updated")
        }

        Function("restoreSession") { () -> String in
            return AuthManager.shared.restoreSession()
        }

        Function("isSessionValid") { () -> Bool in
            return AuthManager.shared.isSessionValid()
        }

        // MARK: - HealthKit Authorization
        AsyncFunction("requestAuthorization") { (types: [String], promise: Promise) in
            guard HealthKitManager.shared.isAvailable() else {
                self.emitLog("HealthKit not available on this device")
                promise.resolve(false)
                return
            }
            let granted: Bool = await withCheckedContinuation { continuation in
                HealthKitManager.shared.requestAuthorization(types: types) { success, error in
                    if let error = error {
                        self.emitLog("Authorization error: \(error.localizedDescription)")
                    }
                    continuation.resume(returning: success)
                }
            }
            self.emitLog("Authorization result: \(granted)")
            promise.resolve(granted)
        }

        // MARK: - Sync
        Function("setSyncInterval") { (_: Double) in
            // No-op on iOS — kept for API compatibility
        }

        AsyncFunction("startBackgroundSync") { (syncDaysBack: Int?, promise: Promise) in
            let started = await SyncManager.shared.startBackgroundSync(syncDaysBack: syncDaysBack)
            promise.resolve(started)
        }

        AsyncFunction("stopBackgroundSync") { (promise: Promise) in
            await SyncManager.shared.stopBackgroundSync()
            promise.resolve()
        }

        AsyncFunction("syncNow") { (promise: Promise) in
            await SyncManager.shared.syncNow()
            promise.resolve()
        }

        AsyncFunction("resumeSync") { (promise: Promise) in
            let resumed = await SyncManager.shared.resumeSync()
            promise.resolve(resumed)
        }

        Function("isSyncActive") { () -> Bool in
            return SyncManager.shared.isSyncActive
        }

        Function("getSyncStatus") { () -> [String: Any] in
            return SyncManager.shared.getSyncStatus()
        }

        Function("resetAnchors") {
            SyncManager.shared.resetAnchors()
        }

        Function("getStoredCredentials") { () -> [String: Any] in
            return AuthManager.shared.getCredentials().toDictionary()
        }

        // MARK: - Providers (not applicable on iOS)
        Function("getAvailableProviders") { () -> [Any] in
            return []
        }

        Function("setProvider") { (_: String) -> Bool in
            return false
        }

        // MARK: - Logs
        Function("setLogLevel") { (levelId: Int) in
            self.logLevel = OWLogLevel(rawValue: levelId) ?? .always
        }

        Function("getLogLevel") { () -> Int in
            return self.logLevel.rawValue
        }
    }

    // MARK: - Private helpers

    private func emitLog(_ message: String) {
        guard logLevel != .none else { return }
        sendEvent("onLog", ["message": message])
    }
}
