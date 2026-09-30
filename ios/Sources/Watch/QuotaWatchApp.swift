import SwiftUI
import WidgetKit

@main
struct QuotaWatchApp: App {
    var body: some Scene {
        WindowGroup {
            WatchHomeView()
        }
    }
}

/// One overall hero. Refresh on activation and once a minute while visible.
struct WatchHomeView: View {
    @State private var snapshot: Snapshot? = WatchHomeView.initialSnapshot()
    @State private var now = Date()
    @State private var isRefreshing = false
    @State private var lastAttempt = Date.distantPast
    @Environment(\.scenePhase) private var scenePhase

    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: 4) {
            if let snapshot, let hero = snapshot.mostCritical() {
                RingGauge(value: hero.window.value(snapshot.display),
                          severity: snapshot.isStale(now: now) ? .stale : hero.window.severity,
                          mode: snapshot.display, diameter: 104, stroke: 8, valueScale: 0.30)
                HStack(spacing: 4) {
                    BrandGlyph(providerName: hero.provider, size: 12)
                    Text("\(hero.provider) · \(hero.window.displayLabel)")
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1).minimumScaleFactor(0.7)
                }
                Text(QuotaFormat.resetLine(hero.window, now: now) ?? "Reset time unknown")
                    .font(.system(size: 11)).monospacedDigit()
                    .foregroundStyle(Palette.dark.textSecondary)
                    .lineLimit(1).minimumScaleFactor(0.7)
                Text(snapshot.isStale(now: now) ? "Out of date · \(QuotaFormat.agoShort(snapshot.age(now: now)))" : hero.window.severity.label)
                    .font(.system(size: 11)).foregroundStyle(Palette.dark.textSecondary)
                    .lineLimit(1).minimumScaleFactor(0.7)
            } else {
                Text(snapshot == nil ? "Not paired" : "No data")
                    .font(.system(size: 16, weight: .semibold))
                Text(snapshot == nil ? "Pair on the iPhone first." : "Waiting for an update")
                    .font(.system(size: 12)).foregroundStyle(Palette.dark.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .environment(\.palette, Palette.dark)
        .containerBackground(Palette.dark.background.gradient, for: .navigation)
        .navigationTitle("Quota")
        .onReceive(tick) {
            now = $0
            if scenePhase == .active && now.timeIntervalSince(lastAttempt) >= 60 {
                Task { await refresh() }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await refresh() } }
        }
        .refreshable { await refresh() }
        .task { await refresh() }
    }

    /// The debug launch arguments work here too, so the watch screen can be captured from the
    /// same script as the phone's.
    static func initialSnapshot() -> Snapshot? {
        if LaunchOptions.demo {
            SharedStore.shared.demoData = true
            if let display = LaunchOptions.display { SharedStore.shared.displayMode = display }
            return DemoData.snapshot()
        }
        return SharedStore.shared.snapshot
    }

    private func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        lastAttempt = Date()
        defer { isRefreshing = false }
        if let fresh = await QuotaClient().refreshSharedOrCached() {
            snapshot = fresh
            now = Date()
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}
