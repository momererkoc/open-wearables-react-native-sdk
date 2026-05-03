import Foundation
import Security

// MARK: - Keychain keys

private enum KeychainKey {
    static let service = "com.openwearables.rnsdk"
    static let userId = "ow_userId"
    static let accessToken = "ow_accessToken"
    static let refreshToken = "ow_refreshToken"
    static let apiKey = "ow_apiKey"
    static let host = "ow_host"
}


// MARK: - AuthManager

final class AuthManager {

    static let shared = AuthManager()
    private init() {}

    // MARK: - Save

    func saveCredentials(
        userId: String,
        accessToken: String?,
        refreshToken: String?,
        apiKey: String?,
        host: String
    ) {
        set(value: userId, forKey: KeychainKey.userId)
        set(value: host, forKey: KeychainKey.host)
        if let token = accessToken { set(value: token, forKey: KeychainKey.accessToken) }
        if let token = refreshToken { set(value: token, forKey: KeychainKey.refreshToken) }
        if let key = apiKey { set(value: key, forKey: KeychainKey.apiKey) }
    }

    /// Store only the host. Used by `configure()` before `signIn()` is called.
    func saveHost(_ host: String) {
        set(value: host, forKey: KeychainKey.host)
    }

    func updateTokens(accessToken: String, refreshToken: String) {
        set(value: accessToken, forKey: KeychainKey.accessToken)
        set(value: refreshToken, forKey: KeychainKey.refreshToken)
    }

    // MARK: - Read

    func getCredentials() -> StoredCredentials {
        return StoredCredentials(
            userId: get(forKey: KeychainKey.userId),
            accessToken: get(forKey: KeychainKey.accessToken),
            refreshToken: get(forKey: KeychainKey.refreshToken),
            apiKey: get(forKey: KeychainKey.apiKey),
            host: get(forKey: KeychainKey.host)
        )
    }

    // MARK: - Clear

    func clearCredentials() {
        delete(forKey: KeychainKey.userId)
        delete(forKey: KeychainKey.accessToken)
        delete(forKey: KeychainKey.refreshToken)
        delete(forKey: KeychainKey.apiKey)
        delete(forKey: KeychainKey.host)
    }

    // MARK: - Session

    func isSessionValid() -> Bool {
        let creds = getCredentials()
        guard let userId = creds.userId, !userId.isEmpty else { return false }
        guard let host = creds.host, !host.isEmpty else { return false }
        // Must have either a token or an API key
        let hasToken = (creds.accessToken?.isEmpty == false)
        let hasApiKey = (creds.apiKey?.isEmpty == false)
        return hasToken || hasApiKey
    }

    /// Returns a JSON string of the current session or an empty JSON object string.
    func restoreSession() -> String {
        let creds = getCredentials()
        guard let userId = creds.userId, !userId.isEmpty else { return "{}" }
        var dict: [String: Any] = ["userId": userId]
        if let host = creds.host { dict["host"] = host }
        if let at = creds.accessToken { dict["accessToken"] = at }
        if let rt = creds.refreshToken { dict["refreshToken"] = rt }
        if let ak = creds.apiKey { dict["apiKey"] = ak }
        if let data = try? JSONSerialization.data(withJSONObject: dict),
           let str = String(data: data, encoding: .utf8) {
            return str
        }
        return "{}"
    }

    // MARK: - Keychain primitives

    private func set(value: String, forKey key: String) {
        guard let data = value.data(using: .utf8) else { return }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: KeychainKey.service,
            kSecAttrAccount as String: key
        ]

        let attributes: [String: Any] = [kSecValueData as String: data]

        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var newItem = query
            newItem[kSecValueData as String] = data
            SecItemAdd(newItem as CFDictionary, nil)
        }
    }

    private func get(forKey key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: KeychainKey.service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess,
              let data = result as? Data,
              let string = String(data: data, encoding: .utf8) else {
            return nil
        }
        return string
    }

    private func delete(forKey key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: KeychainKey.service,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
    }
}
