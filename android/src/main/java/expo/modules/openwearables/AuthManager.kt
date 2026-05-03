package expo.modules.openwearables

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import java.io.IOException
import java.security.GeneralSecurityException

class AuthManager(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "ow_auth_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_HOST = "host"
        private const val KEY_CUSTOM_SYNC_URL = "custom_sync_url"
    }

    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            // Fallback to plain prefs if encryption is unavailable
            context.getSharedPreferences(PREFS_FILE + "_plain", Context.MODE_PRIVATE)
        } catch (e: IOException) {
            context.getSharedPreferences(PREFS_FILE + "_plain", Context.MODE_PRIVATE)
        }
    }

    fun saveCredentials(
        userId: String,
        accessToken: String?,
        refreshToken: String?,
        apiKey: String?,
        host: String,
        customSyncURL: String?
    ) {
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_API_KEY, apiKey)
            putString(KEY_HOST, host)
            putString(KEY_CUSTOM_SYNC_URL, customSyncURL)
            apply()
        }
    }

    fun updateTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    fun updateAccessToken(accessToken: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply()
    }

    fun getCredentials(): Credentials? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val host = prefs.getString(KEY_HOST, null) ?: return null
        return Credentials(
            userId = userId,
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, null),
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null),
            apiKey = prefs.getString(KEY_API_KEY, null),
            host = host,
            customSyncURL = prefs.getString(KEY_CUSTOM_SYNC_URL, null)
        )
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    fun isSessionValid(): Boolean {
        val creds = getCredentials() ?: return false
        val hasAuth = !creds.accessToken.isNullOrBlank() || !creds.apiKey.isNullOrBlank()
        return hasAuth && creds.userId.isNotBlank() && creds.host.isNotBlank()
    }

    /**
     * Returns a JSON string representation of the current session credentials,
     * or an empty JSON object if no session is stored.
     */
    fun restoreSession(): String {
        val creds = getCredentials() ?: return "{}"
        val map = mapOf(
            "userId" to creds.userId,
            "accessToken" to creds.accessToken,
            "refreshToken" to creds.refreshToken,
            "apiKey" to creds.apiKey,
            "host" to creds.host,
            "customSyncURL" to creds.customSyncURL
        )
        return gson.toJson(map)
    }

    fun getStoredCredentials(): Map<String, Any?> {
        val creds = getCredentials() ?: return emptyMap()
        return mapOf(
            "userId" to creds.userId,
            "accessToken" to creds.accessToken,
            "refreshToken" to creds.refreshToken,
            "apiKey" to creds.apiKey,
            "host" to creds.host,
            "customSyncURL" to creds.customSyncURL
        )
    }
}
