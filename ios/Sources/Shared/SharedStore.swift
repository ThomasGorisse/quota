import Foundation

/// Everything the app and its extensions have to agree on lives here: the paired host, the
/// last snapshot, and whether demo data is on. The token is *not* here — see ``TokenStore``.
///
/// The App Group is what makes a widget able to render before its first network call.
struct SharedStore {
    static let appGroup = "group.com.gorisse.thomas.quota"
    static let shared = SharedStore()

    /// Falls back to `.standard` so a build without the App Group entitlement (a simulator
    /// build signed ad-hoc, for instance) still runs instead of crashing.
    let defaults: UserDefaults

    /// True when the App Group container was actually available.
    let isShared: Bool

    init(suiteName: String = SharedStore.appGroup) {
        if let suite = UserDefaults(suiteName: suiteName) {
            defaults = suite
            isShared = true
        } else {
            defaults = .standard
            isShared = false
        }
    }

    private enum Key {
        static let host = "quota.host"
        static let snapshot = "quota.snapshot"
        static let demo = "quota.demoData"
        static let token = "quota.token"          // fallback only, see TokenStore
        static let display = "quota.display"
    }

    // MARK: - Host

    var host: String? {
        get { defaults.string(forKey: Key.host) }
        nonmutating set { defaults.set(newValue, forKey: Key.host) }
    }

    // MARK: - Demo data

    var demoData: Bool {
        get { defaults.bool(forKey: Key.demo) }
        nonmutating set { defaults.set(newValue, forKey: Key.demo) }
    }

    /// The last known `display` mode. The status payload is authoritative; this cache is what
    /// demo mode reads and what a surface shows before its first successful fetch.
    var displayMode: DisplayMode {
        get { DisplayMode.ofWire(defaults.string(forKey: Key.display)) }
        nonmutating set { defaults.set(newValue.rawValue, forKey: Key.display) }
    }

    // MARK: - Last snapshot

    var snapshot: Snapshot? {
        get {
            guard let data = defaults.data(forKey: Key.snapshot) else { return nil }
            return try? JSONDecoder().decode(Snapshot.self, from: data)
        }
        nonmutating set {
            if let newValue, let data = try? JSONEncoder().encode(newValue) {
                displayMode = newValue.display
                defaults.set(data, forKey: Key.snapshot)
            } else {
                defaults.removeObject(forKey: Key.snapshot)
            }
        }
    }

    // MARK: - Token fallback

    var fallbackToken: String? {
        get { defaults.string(forKey: Key.token) }
        nonmutating set { defaults.set(newValue, forKey: Key.token) }
    }

    func clearPairing() {
        defaults.removeObject(forKey: Key.host)
        defaults.removeObject(forKey: Key.snapshot)
        defaults.removeObject(forKey: Key.token)
    }
}
