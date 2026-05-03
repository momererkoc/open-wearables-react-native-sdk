import Foundation

// MARK: - API Errors

enum ApiError: Error, LocalizedError {
    case invalidURL
    case noCredentials
    case httpError(statusCode: Int, body: String)
    case decodingError(Error)
    case networkError(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid URL"
        case .noCredentials: return "No credentials available"
        case .httpError(let code, let body): return "HTTP \(code): \(body)"
        case .decodingError(let e): return "Decoding error: \(e.localizedDescription)"
        case .networkError(let e): return "Network error: \(e.localizedDescription)"
        }
    }
}

// MARK: - ApiClient

final class ApiClient {

    static let shared = ApiClient()
    private init() {}

    private let sdkVersion = "1.0.0"

    // MARK: - Sync

    /// Syncs data to the server. Auto-refreshes token on 401.
    func syncData(
        host: String,
        userId: String,
        syncData: SyncData,
        token: String?,
        apiKey: String?
    ) async throws {
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]

        let payload = SyncRequest(
            provider: "apple",
            sdkVersion: sdkVersion,
            syncTimestamp: iso.string(from: Date()),
            data: SyncRequestData(
                records: syncData.records,
                sleep: syncData.sleep,
                workouts: syncData.workouts
            )
        )

        let urlString = "\(host)/api/v1/sdk/users/\(userId)/sync"
        guard let url = URL(string: urlString) else { throw ApiError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        applyAuth(to: &request, token: token, apiKey: apiKey)

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        request.httpBody = try encoder.encode(payload)

        let (data, response) = try await performRequest(request)

        if let httpResponse = response as? HTTPURLResponse {
            if httpResponse.statusCode == 401 {
                // Try to refresh and retry once
                if let newToken = try? await attemptTokenRefresh(host: host) {
                    var retryRequest = request
                    applyAuth(to: &retryRequest, token: newToken, apiKey: apiKey)
                    let (_, retryResponse) = try await performRequest(retryRequest)
                    if let retryHTTP = retryResponse as? HTTPURLResponse,
                       !(200...299).contains(retryHTTP.statusCode) {
                        let body = String(data: data, encoding: .utf8) ?? ""
                        throw ApiError.httpError(statusCode: retryHTTP.statusCode, body: body)
                    }
                    return
                }
                let body = String(data: data, encoding: .utf8) ?? ""
                throw ApiError.httpError(statusCode: 401, body: body)
            }
            if !(200...299).contains(httpResponse.statusCode) {
                let body = String(data: data, encoding: .utf8) ?? ""
                throw ApiError.httpError(statusCode: httpResponse.statusCode, body: body)
            }
        }
    }

    // MARK: - Token Refresh

    func refreshToken(host: String, refreshToken: String) async throws -> TokenResponse {
        let urlString = "\(host)/api/v1/token/refresh"
        guard let url = URL(string: urlString) else { throw ApiError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body = ["refresh_token": refreshToken]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await performRequest(request)

        if let httpResponse = response as? HTTPURLResponse,
           !(200...299).contains(httpResponse.statusCode) {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw ApiError.httpError(statusCode: httpResponse.statusCode, body: body)
        }

        do {
            let tokenResponse = try JSONDecoder().decode(TokenResponse.self, from: data)
            return tokenResponse
        } catch {
            throw ApiError.decodingError(error)
        }
    }

    // MARK: - Helpers

    private func applyAuth(to request: inout URLRequest, token: String?, apiKey: String?) {
        if let token = token, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else if let key = apiKey, !key.isEmpty {
            request.setValue(key, forHTTPHeaderField: "X-Open-Wearables-API-Key")
        }
    }

    private func performRequest(_ request: URLRequest) async throws -> (Data, URLResponse) {
        do {
            return try await URLSession.shared.data(for: request)
        } catch {
            throw ApiError.networkError(error)
        }
    }

    private func attemptTokenRefresh(host: String) async throws -> String? {
        let creds = AuthManager.shared.getCredentials()
        guard let rt = creds.refreshToken, !rt.isEmpty else { return nil }
        let tokenResponse = try await refreshToken(host: host, refreshToken: rt)
        AuthManager.shared.updateTokens(
            accessToken: tokenResponse.access_token,
            refreshToken: tokenResponse.refresh_token ?? rt
        )
        return tokenResponse.access_token
    }
}
