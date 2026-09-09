import Foundation

/// The `GET /v1/status` payload, exactly as specified in the repository README.
///
/// `percent` is the percentage **used**; every gauge in this app shows ``Window/remaining``.
struct StatusResponse: Codable, Equatable, Sendable {
    var collectedAt: String
    var ageSeconds: Int
    var stale: Bool
    var providers: [String: ProviderStatus]
    var severity: String
    /// How every client prints a percentage: capacity left, or capacity spent. Absent on an
    /// older backend, which means remaining.
    var display: String

    enum CodingKeys: String, CodingKey {
        case collectedAt = "collected_at"
        case ageSeconds = "age_seconds"
        case stale
        case providers
        case severity
        case display
    }

    init(collectedAt: String, ageSeconds: Int = 0, stale: Bool = false,
         providers: [String: ProviderStatus] = [:], severity: String = Severity.ok.wire,
         display: String = DisplayMode.remaining.rawValue) {
        self.collectedAt = collectedAt
        self.ageSeconds = ageSeconds
        self.stale = stale
        self.providers = providers
        self.severity = severity
        self.display = display
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        collectedAt = try c.decodeIfPresent(String.self, forKey: .collectedAt) ?? ""
        ageSeconds = try c.decodeIfPresent(Int.self, forKey: .ageSeconds) ?? 0
        stale = try c.decodeIfPresent(Bool.self, forKey: .stale) ?? false
        providers = try c.decodeIfPresent([String: ProviderStatus].self, forKey: .providers) ?? [:]
        severity = try c.decodeIfPresent(String.self, forKey: .severity) ?? Severity.ok.wire
        display = try c.decodeIfPresent(String.self, forKey: .display) ?? DisplayMode.remaining.rawValue
        // `history` is deliberately not decoded: no client screen here plots it, and skipping
        // it keeps the payload the widget caches small.
    }
}

struct ProviderStatus: Codable, Equatable, Sendable {
    var plan: String?
    var windows: [Window]

    init(plan: String?, windows: [Window]) {
        self.plan = plan
        self.windows = windows
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        plan = try c.decodeIfPresent(String.self, forKey: .plan)
        windows = try c.decodeIfPresent([Window].self, forKey: .windows) ?? []
    }

    enum CodingKeys: String, CodingKey { case plan, windows }
}

struct Window: Codable, Equatable, Identifiable, Sendable {
    var id: String
    var label: String
    /// Percentage **used**, as sent by the collector.
    var percent: Int
    var resetsAt: String?

    enum CodingKeys: String, CodingKey {
        case id, label, percent
        case resetsAt = "resets_at"
    }

    /// What is left, which is the number worth reading.
    var remaining: Int { min(max(100 - percent, 0), 100) }

    var severity: Severity { Severity.ofUsedPercent(percent) }

    /// The number to print, and the arc to draw, in the mode the backend is set to.
    /// Thresholds still run on the used percent, so the colour never moves with the mode.
    func value(_ mode: DisplayMode) -> Int {
        mode == .used ? min(max(percent, 0), 100) : remaining
    }

    /// Time until this window resets, or nil when the payload carries no reset date.
    func resetsIn(now: Date = Date()) -> TimeInterval? {
        guard let resetsAt, let date = ISO8601.parse(resetsAt) else { return nil }
        return max(0, date.timeIntervalSince(now))
    }
}

enum Severity: String, Sendable, CaseIterable {
    case ok, warn, critical, stale

    var wire: String { rawValue }

    /// README rule: `<60` ok, `60–85` warn, `>85` critical — on the percentage used.
    static func ofUsedPercent(_ percent: Int) -> Severity {
        if percent > 85 { return .critical }
        if percent >= 60 { return .warn }
        return .ok
    }

    static func ofWire(_ value: String?) -> Severity {
        Severity(rawValue: value ?? "") ?? .ok
    }

    var label: String {
        switch self {
        case .ok: return "OK"
        case .warn: return "Warn"
        case .critical: return "Critical"
        case .stale: return "Stale"
        }
    }
}

/// `display` from `GET /v1/status`, synced across every client by `PUT /v1/settings`.
enum DisplayMode: String, Sendable, CaseIterable, Identifiable {
    case remaining
    case used

    var id: String { rawValue }

    /// The word under the big number.
    var caption: String { self == .used ? "% used" : "% left" }

    /// The label of the segmented control in Settings.
    var title: String { self == .used ? "Used" : "Left" }

    static func ofWire(_ value: String?) -> DisplayMode {
        DisplayMode(rawValue: value ?? "") ?? .remaining
    }
}

/// Display order and labels for the two providers this version knows about.
enum ProviderId: String, CaseIterable, Sendable {
    case claude, codex

    var displayName: String {
        switch self {
        case .claude: return "Claude"
        case .codex: return "Codex"
        }
    }
}

/// A ``StatusResponse`` plus the moment this device received it, so that "updated 42 s ago"
/// keeps counting while the screen is open and the widget can tell a cached payload is old.
struct Snapshot: Codable, Equatable, Sendable {
    var status: StatusResponse
    var receivedAt: Date
    /// True when the payload is the canned demo one, so the UI can say so.
    var isDemo: Bool = false

    /// Anything older than ten minutes is shown grey, whatever the server said.
    static let staleAfter: TimeInterval = 10 * 60

    func age(now: Date = Date()) -> TimeInterval {
        Double(status.ageSeconds) + max(0, now.timeIntervalSince(receivedAt))
    }

    func isStale(now: Date = Date()) -> Bool {
        status.stale || age(now: now) > Snapshot.staleAfter
    }

    /// The mode the whole product is in, as the backend last reported it.
    var display: DisplayMode { DisplayMode.ofWire(status.display) }

    func severity(now: Date = Date()) -> Severity {
        isStale(now: now) ? .stale : Severity.ofWire(status.severity)
    }

    /// Providers in a stable order: the ones we know about first, then anything new.
    func orderedProviders() -> [(name: String, provider: ProviderStatus)] {
        let known = ProviderId.allCases.compactMap { id -> (String, ProviderStatus)? in
            guard let p = status.providers[id.rawValue] else { return nil }
            return (id.displayName, p)
        }
        let extra = status.providers
            .filter { ProviderId(rawValue: $0.key) == nil }
            .sorted { $0.key < $1.key }
            .map { ($0.key.capitalized, $0.value) }
        return (known + extra).map { (name: $0.0, provider: $0.1) }
    }

    /// The ring a glanceable surface should show when it only has room for one:
    /// the window with the least left.
    func mostCritical() -> (provider: String, window: Window)? {
        orderedProviders()
            .flatMap { entry in entry.provider.windows.map { (entry.name, $0) } }
            .min { $0.1.remaining < $1.1.remaining }
            .map { (provider: $0.0, window: $0.1) }
    }

    func window(_ provider: ProviderId, _ id: String) -> Window? {
        status.providers[provider.rawValue]?.windows.first { $0.id == id }
    }
}

enum ISO8601 {
    private static let withFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let plain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static func parse(_ value: String) -> Date? {
        withFraction.date(from: value) ?? plain.date(from: value)
    }

    static func string(_ date: Date) -> String { plain.string(from: date) }
}
