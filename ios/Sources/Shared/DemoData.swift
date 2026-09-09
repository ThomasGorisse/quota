import Foundation

/// A canned `/v1/status` used by the "demo data" toggle in Settings, so the screens, the
/// widgets and the watch can be validated — and screenshotted — before the backend is paired.
///
/// The numbers are the same ones the Android build uses, deliberately covering the three
/// severities: an ok Claude 5 h, a warn Claude week, a critical Codex 5 h. Reset times are
/// relative to now, which is what makes the "resets in …" line worth looking at.
enum DemoData {

    /// Which shape of payload to fake. A provider reports one, two or three windows and the
    /// layouts must hold for all of them, so the demo can produce a Codex that reports only
    /// Week and Reserve — which is what a Codex Pro account sends today.
    enum Variant: String {
        case plus, pro
    }

    static func snapshot(now: Date = Date(),
                         display: DisplayMode = SharedStore.shared.displayMode,
                         variant: Variant = LaunchOptions.demoVariant ?? .plus) -> Snapshot {
        Snapshot(status: status(now: now, display: display, variant: variant),
                 receivedAt: now.addingTimeInterval(-5), isDemo: true)
    }

    static func status(now: Date = Date(),
                       display: DisplayMode = SharedStore.shared.displayMode,
                       variant: Variant = LaunchOptions.demoVariant ?? .plus) -> StatusResponse {
        StatusResponse(
            collectedAt: ISO8601.string(now.addingTimeInterval(-42)),
            ageSeconds: 42,
            stale: false,
            providers: [
                "claude": ProviderStatus(plan: "max", windows: [
                    window("session", "5 h", used: 11, in: 130 * 60, from: now),
                    window("weekly", "Week", used: 68, in: 62 * 3600, from: now),
                ]),
                "codex": variant == .pro
                    ? ProviderStatus(plan: "pro", windows: [
                        window("weekly", "Week", used: 74, in: 140 * 3600, from: now),
                        window("reserve", "Reserve", used: 10, in: 96 * 3600, from: now),
                    ])
                    : ProviderStatus(plan: "plus", windows: [
                        window("session", "5 h", used: 92, in: 37 * 60, from: now),
                        window("weekly", "Week", used: 34, in: 140 * 3600, from: now),
                        window("reserve", "Reserve", used: 10, in: 96 * 3600, from: now),
                    ]),
                // The Code Assist buckets are per model, most consumed first, so the headline
                // window is a model name rather than a time window.
                "gemini": ProviderStatus(plan: "Standard", windows: [
                    window("gemini-3-pro", "3 Pro", used: 62, in: 315 * 60, from: now),
                    window("gemini-2-5-pro", "2.5 Pro", used: 29, in: 14 * 3600, from: now),
                ]),
            ],
            severity: Severity.critical.wire,
            display: display.rawValue
        )
    }

    /// The same payload gone cold, for looking at the stale state on purpose.
    static func staleSnapshot(now: Date = Date()) -> Snapshot {
        var snapshot = self.snapshot(now: now)
        snapshot.status.ageSeconds = 22 * 60
        snapshot.status.stale = true
        return snapshot
    }

    private static func window(_ id: String, _ label: String, used: Int,
                               in seconds: TimeInterval, from now: Date) -> Window {
        Window(id: id, label: label, percent: used,
               resetsAt: ISO8601.string(now.addingTimeInterval(seconds)))
    }
}
