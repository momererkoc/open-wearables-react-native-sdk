package expo.modules.openwearables

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(
    private val authManager: AuthManager,
    private val onLog: (String) -> Unit,
    private val onAuthError: (Int, String) -> Unit
) {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Syncs data to the API. Returns true on success.
     * Handles 401 by attempting a token refresh once, then retrying.
     */
    suspend fun syncData(userId: String, payload: SyncRequest): Boolean = withContext(Dispatchers.IO) {
        val creds = authManager.getCredentials()
            ?: throw IOException("No credentials stored")

        val syncUrl = buildSyncUrl(creds.host, creds.customSyncURL, userId)
        val body = gson.toJson(payload)

        onLog("[ApiClient] POST $syncUrl")

        val result = executeSync(syncUrl, body, creds)
        if (result == 401) {
            onLog("[ApiClient] 401 received, attempting token refresh")
            val refreshed = tryRefreshToken(creds)
            if (refreshed) {
                val freshCreds = authManager.getCredentials()!!
                val retryResult = executeSync(syncUrl, body, freshCreds)
                if (retryResult == 401) {
                    onAuthError(401, "Authentication failed after token refresh")
                    false
                } else {
                    retryResult in 200..299
                }
            } else {
                onAuthError(401, "Token refresh failed")
                false
            }
        } else if (result !in 200..299) {
            onLog("[ApiClient] Sync failed with status $result")
            false
        } else {
            onLog("[ApiClient] Sync succeeded with status $result")
            true
        }
    }

    /**
     * Returns the HTTP status code of the sync request.
     */
    private fun executeSync(url: String, jsonBody: String, creds: Credentials): Int {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))

        applyAuthHeaders(requestBuilder, creds)

        return try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                response.code
            }
        } catch (e: IOException) {
            onLog("[ApiClient] Network error: ${e.message}")
            -1
        }
    }

    /**
     * Attempts to refresh the access token. Returns true if successful.
     */
    private suspend fun tryRefreshToken(creds: Credentials): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = creds.refreshToken
        if (refreshToken.isNullOrBlank()) {
            onLog("[ApiClient] No refresh token available")
            return@withContext false
        }

        val url = "${normalizeHost(creds.host)}/api/v1/token/refresh"
        val bodyJson = gson.toJson(mapOf("refresh_token" to refreshToken))
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@use false
                    val tokenResponse = gson.fromJson(responseBody, TokenResponse::class.java)
                    val newAccess = tokenResponse.access_token
                    val newRefresh = tokenResponse.refresh_token
                    if (!newAccess.isNullOrBlank()) {
                        authManager.updateTokens(
                            newAccess,
                            newRefresh ?: refreshToken
                        )
                        onLog("[ApiClient] Token refreshed successfully")
                        true
                    } else {
                        onLog("[ApiClient] Token refresh response missing access_token")
                        false
                    }
                } else {
                    onLog("[ApiClient] Token refresh failed with status ${response.code}")
                    false
                }
            }
        } catch (e: IOException) {
            onLog("[ApiClient] Token refresh network error: ${e.message}")
            false
        }
    }

    /**
     * Fetches fresh tokens for a user from the user token endpoint.
     */
    suspend fun fetchUserTokens(userId: String, host: String): TokenResponse? = withContext(Dispatchers.IO) {
        val url = "${normalizeHost(host)}/api/v1/users/$userId/token"
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    gson.fromJson(body, TokenResponse::class.java)
                } else {
                    onLog("[ApiClient] fetchUserTokens failed with status ${response.code}")
                    null
                }
            }
        } catch (e: IOException) {
            onLog("[ApiClient] fetchUserTokens network error: ${e.message}")
            null
        }
    }

    private fun applyAuthHeaders(builder: Request.Builder, creds: Credentials) {
        when {
            !creds.accessToken.isNullOrBlank() ->
                builder.addHeader("Authorization", "Bearer ${creds.accessToken}")
            !creds.apiKey.isNullOrBlank() ->
                builder.addHeader("X-Open-Wearables-API-Key", creds.apiKey)
        }
    }

    private fun buildSyncUrl(host: String, customSyncURL: String?, userId: String): String {
        if (!customSyncURL.isNullOrBlank()) {
            return customSyncURL
        }
        return "${normalizeHost(host)}/api/v1/sdk/users/$userId/sync"
    }

    private fun normalizeHost(host: String): String {
        return host.trimEnd('/')
    }
}
