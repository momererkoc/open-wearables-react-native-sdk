package expo.modules.openwearables

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.PermissionController
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.functions.Coroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenWearablesModule : Module() {

    companion object {
        private const val HEALTH_CONNECT_PERM_REQUEST_CODE = 9421
        private const val MODULE_LOG_TAG = "[OpenWearablesModule]"
    }

    private val moduleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Lazy-initialized managers — they need appContext which is available after OnCreate
    private lateinit var authManager: AuthManager
    private lateinit var apiClient: ApiClient
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var syncManager: SyncManager

    // Pending promise for the requestAuthorization flow
    private var pendingPermissionPromise: Promise? = null
    private var pendingPermissionTypes: List<String> = emptyList()

    // Permission launcher registered via the activity; re-registered on each foreground
    private var permissionLauncher: ActivityResultLauncher<Set<String>>? = null

    // Log level (0=None, 1=Always, 2=Debug)
    private var logLevel: Int = 1

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun log(message: String) {
        if (logLevel > 0) {
            sendEvent("onLog", mapOf("message" to message))
        }
    }

    private fun authError(statusCode: Int, message: String) {
        sendEvent("onAuthError", mapOf("statusCode" to statusCode, "message" to message))
    }

    private fun initManagers() {
        val ctx = appContext.reactContext
            ?: throw CodedException("NO_CONTEXT", "React context is not available", null)

        authManager = AuthManager(ctx)

        apiClient = ApiClient(
            authManager = authManager,
            onLog = { msg -> log(msg) },
            onAuthError = { code, msg -> authError(code, msg) }
        )

        healthConnectManager = HealthConnectManager(
            context = ctx,
            onLog = { msg -> log(msg) }
        )

        syncManager = SyncManager(
            context = ctx,
            authManager = authManager,
            healthConnectManager = healthConnectManager,
            apiClient = apiClient,
            onLog = { msg -> log(msg) },
            onAuthError = { code, msg -> authError(code, msg) }
        )
    }

    private fun registerPermissionLauncher(activity: Activity) {
        // Only register if the activity is a ComponentActivity (required for ActivityResultLauncher)
        if (activity is androidx.activity.ComponentActivity) {
            val contract = PermissionController.createRequestPermissionResultContract()
            permissionLauncher = activity.registerForActivityResult(contract) { granted ->
                handlePermissionResult(granted)
            }
            log("$MODULE_LOG_TAG Permission launcher registered")
        } else {
            log("$MODULE_LOG_TAG Activity is not ComponentActivity, using fallback permission flow")
        }
    }

    private fun handlePermissionResult(granted: Set<String>) {
        val promise = pendingPermissionPromise ?: return
        pendingPermissionPromise = null

        val required = healthConnectManager.buildPermissions(pendingPermissionTypes)
        val allGranted = granted.containsAll(required)

        log("$MODULE_LOG_TAG Permission result: granted=${granted.size}/${required.size}, allGranted=$allGranted")
        promise.resolve(allGranted)
    }

    // ---------------------------------------------------------------------------
    // Module Definition
    // ---------------------------------------------------------------------------

    override fun definition() = ModuleDefinition {

        Name("OpenWearablesHealthSDK")

        Events("onLog", "onAuthError")

        // -----------------------------------------------------------------------
        // Lifecycle
        // -----------------------------------------------------------------------

        OnCreate {
            initManagers()
            log("$MODULE_LOG_TAG Module created")

            appContext.activityProvider?.currentActivity?.let { activity ->
                registerPermissionLauncher(activity)
            }
        }

        OnActivityEntersForeground {
            appContext.activityProvider?.currentActivity?.let { activity ->
                // Re-register the launcher in case the activity was recreated
                if (permissionLauncher == null) {
                    registerPermissionLauncher(activity)
                }
            }
        }

        OnActivityEntersBackground {
            // Nothing specific to do on background
        }

        OnActivityDestroys {
            permissionLauncher = null
            pendingPermissionPromise?.reject(
                CodedException("ACTIVITY_DESTROYED", "Activity was destroyed while awaiting permissions", null)
            )
            pendingPermissionPromise = null
        }

        // -----------------------------------------------------------------------
        // Configure
        // -----------------------------------------------------------------------

        Function("configure") { host: String, customSyncURL: String? ->
            // Persist the host and optional custom sync URL by updating existing credentials
            val existing = authManager.getCredentials()
            if (existing != null) {
                authManager.saveCredentials(
                    userId = existing.userId,
                    accessToken = existing.accessToken,
                    refreshToken = existing.refreshToken,
                    apiKey = existing.apiKey,
                    host = host,
                    customSyncURL = customSyncURL
                )
            } else {
                // Store only configuration fields until signIn is called
                authManager.saveCredentials(
                    userId = "",
                    accessToken = null,
                    refreshToken = null,
                    apiKey = null,
                    host = host,
                    customSyncURL = customSyncURL
                )
            }
            log("$MODULE_LOG_TAG Configured with host=$host, customSyncURL=$customSyncURL")
        }

        // -----------------------------------------------------------------------
        // Auth
        // -----------------------------------------------------------------------

        AsyncFunction("signIn") Coroutine { userId: String, accessToken: String?, refreshToken: String?, apiKey: String? ->
            val creds = authManager.getCredentials()
            val host = creds?.host?.takeIf { it.isNotBlank() } ?: ""
            val customSyncURL = creds?.customSyncURL

            authManager.saveCredentials(
                userId = userId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                apiKey = apiKey,
                host = host,
                customSyncURL = customSyncURL
            )
            log("$MODULE_LOG_TAG Signed in userId=$userId")
        }

        AsyncFunction("signOut") Coroutine { _: Unit? ->
            syncManager.stopBackgroundSync()
            authManager.clearCredentials()
            log("$MODULE_LOG_TAG Signed out")
        }

        Function("updateTokens") { accessToken: String, refreshToken: String ->
            authManager.updateTokens(accessToken, refreshToken)
            log("$MODULE_LOG_TAG Tokens updated")
        }

        Function("restoreSession") {
            authManager.restoreSession()
        }

        Function("isSessionValid") {
            authManager.isSessionValid()
        }

        // -----------------------------------------------------------------------
        // Authorization
        // -----------------------------------------------------------------------

        AsyncFunction("requestAuthorization") { types: List<String>, promise: Promise ->
            moduleScope.launch {
                if (!healthConnectManager.checkAvailability()) {
                    log("$MODULE_LOG_TAG Health Connect not available")
                    promise.resolve(false)
                    return@launch
                }

                // Check if all permissions are already granted
                val alreadyGranted = healthConnectManager.hasAllPermissions(types)
                if (alreadyGranted) {
                    log("$MODULE_LOG_TAG All permissions already granted")
                    promise.resolve(true)
                    return@launch
                }

                val launcher = permissionLauncher
                if (launcher == null) {
                    log("$MODULE_LOG_TAG No permission launcher available, attempting to register")
                    val activity = appContext.activityProvider?.currentActivity
                    if (activity != null) {
                        registerPermissionLauncher(activity)
                    }
                    if (permissionLauncher == null) {
                        promise.reject(
                            CodedException(
                                "NO_LAUNCHER",
                                "Activity not available for permission request. Call requestAuthorization from a foreground context.",
                                null
                            )
                        )
                        return@launch
                    }
                }

                pendingPermissionPromise = promise
                pendingPermissionTypes = types
                healthConnectManager.requestPermissions(types, permissionLauncher!!)
                log("$MODULE_LOG_TAG Permission request launched for ${types.size} types")
            }
        }

        // -----------------------------------------------------------------------
        // Sync
        // -----------------------------------------------------------------------

        Function("setSyncInterval") { minutes: Long ->
            syncManager.setSyncInterval(minutes)
        }

        AsyncFunction("startBackgroundSync") Coroutine { syncDaysBack: Int? ->
            syncManager.startBackgroundSync(syncDaysBack)
        }

        AsyncFunction("stopBackgroundSync") Coroutine { _: Unit? ->
            syncManager.stopBackgroundSync()
        }

        AsyncFunction("syncNow") Coroutine { _: Unit? ->
            syncManager.syncNow()
        }

        AsyncFunction("resumeSync") Coroutine { _: Unit? ->
            syncManager.resumeSync()
        }

        Function("isSyncActive") {
            syncManager.isSyncActive()
        }

        Function("getSyncStatus") {
            syncManager.getSyncStatus()
        }

        Function("resetAnchors") {
            syncManager.resetAnchors()
        }

        Function("getStoredCredentials") {
            authManager.getStoredCredentials()
        }

        // -----------------------------------------------------------------------
        // Providers
        // -----------------------------------------------------------------------

        Function("getAvailableProviders") {
            val isAvailable = healthConnectManager.checkAvailability()
            listOf(
                mapOf(
                    "id" to "google",
                    "displayName" to "Google Health Connect",
                    "isAvailable" to isAvailable
                )
            )
        }

        Function("setProvider") { providerId: String ->
            // Android only supports Health Connect ("google"); always returns true for it
            if (providerId == "google") {
                log("$MODULE_LOG_TAG Provider set to google (Health Connect)")
                true
            } else {
                log("$MODULE_LOG_TAG Unknown provider: $providerId")
                false
            }
        }

        // -----------------------------------------------------------------------
        // Logs
        // -----------------------------------------------------------------------

        Function("setLogLevel") { level: Int ->
            logLevel = level
        }

        Function("getLogLevel") {
            logLevel
        }
    }
}
