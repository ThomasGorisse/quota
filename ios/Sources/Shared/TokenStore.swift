import Foundation
import Security

/// The device token, which is a credential: it is never logged, never put in a URL this app
/// builds, and never sent anywhere but the paired host.
///
/// It is stored in the keychain under the App Group access group, so the widget and the watch
/// read the same item as the app. That access group only resolves once the bundle is signed
/// with a real team id; when it is not — a simulator build signed ad-hoc — the keychain call
/// fails with `errSecMissingEntitlement` and the store falls back to the App Group
/// `UserDefaults`. ``TokenStore/storageDescription`` says which of the two is in use, and the
/// Settings sheet shows it, because "it is in the keychain" must not be a guess.
enum TokenStore {

    static let service = "com.gorisse.thomas.quota.token"
    static let accessGroup = SharedStore.appGroup
    static let account = "device-token"

    enum Backing: String {
        case keychain = "Keychain (App Group access group)"
        case appGroupDefaults = "App Group UserDefaults (no signed keychain access group)"
    }

    private(set) static var backing: Backing = .keychain

    static var storageDescription: String { backing.rawValue }

    // MARK: - Read

    static func token() -> String? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data, let value = String(data: data, encoding: .utf8) {
            backing = .keychain
            return value
        }
        if status == errSecItemNotFound {
            // Nothing stored under the access group; the fallback may still hold one.
            return SharedStore.shared.fallbackToken
        }
        // Any other status (typically -34018, missing entitlement) means the keychain is not
        // usable for this build.
        backing = .appGroupDefaults
        return SharedStore.shared.fallbackToken
    }

    // MARK: - Write

    @discardableResult
    static func save(_ token: String) -> Bool {
        let data = Data(token.utf8)
        var query = baseQuery()
        let attributes: [String: Any] = [kSecValueData as String: data]

        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess {
            backing = .keychain
            return true
        }

        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        let addStatus = SecItemAdd(query as CFDictionary, nil)
        if addStatus == errSecSuccess {
            backing = .keychain
            return true
        }

        backing = .appGroupDefaults
        SharedStore.shared.fallbackToken = token
        return false
    }

    static func clear() {
        SecItemDelete(baseQuery() as CFDictionary)
        SharedStore.shared.fallbackToken = nil
    }

    // MARK: - Query

    private static func baseQuery() -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        #if !targetEnvironment(simulator)
        // On device the access group is what lets the extensions read the same item. In the
        // simulator every process already shares one keychain, and asking for a group that no
        // provisioning profile granted only makes the call fail.
        query[kSecAttrAccessGroup as String] = accessGroup
        #endif
        return query
    }
}
