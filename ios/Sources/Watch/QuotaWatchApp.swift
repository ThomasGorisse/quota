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

/// The watch reads the same shared snapshot as the phone and refreshes it when it is shown —
/// the same five rings as the medium widget, stacked so they fit a 41 mm screen.
struct WatchHomeView: View {
    @State private var snapshot: Snapshot? = WatchHomeView.initialSnapshot()
    @State private var now = Date()
    @State private var isRefreshing = false

    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                if let snapshot {
                    header(snapshot)
                    ForEach(snapshot.orderedProviders(), id: \.name) { entry in
                        WatchProviderRow(
                            name: entry.name,
                            provider: entry.provider,
                            mode: snapshot.display,
                            stale: snapshot.isStale(now: now),
                            now: now
                        )
                    }
                } else {
                    VStack(spacing: 8) {
                        Text("Not paired")
                            .font(.system(size: 16, weight: .semibold))
                        Text("Pair on the iPhone first.")
                            .font(.system(size: 13))
                            .foregroundStyle(Palette.dark.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
                }
            }
            .padding(.horizontal, 4)
        }
        .environment(\.palette, Palette.dark)
        .containerBackground(Palette.dark.background.gradient, for: .navigation)
        .navigationTitle("Quota")
        .onReceive(tick) { now = $0 }
        .refreshable { await refresh() }
        .task { await refresh() }
    }

    private func header(_ snapshot: Snapshot) -> some View {
        HStack(spacing: 8) {
            SeverityPill(severity: snapshot.severity(now: now), compact: true)
            Text(QuotaFormat.agoShort(snapshot.age(now: now)))
                .font(.system(size: 12))
                .monospacedDigit()
                .foregroundStyle(Palette.dark.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 0)
        }
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
        defer { isRefreshing = false }
        if let fresh = await QuotaClient().refreshSharedOrCached() {
            snapshot = fresh
            now = Date()
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}

/// One provider on the watch: name in the accent colour, then its rings in a row.
struct WatchProviderRow: View {
    let name: String
    let provider: ProviderStatus
    let mode: DisplayMode
    let stale: Bool
    let now: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 5) {
                BrandMark(providerName: name, size: 16)
                Text(name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(QuotaTheme.accent(for: name))
                if let plan = provider.plan, !plan.isEmpty {
                    Text(plan.uppercased())
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(QuotaTheme.accent(for: name).opacity(0.8))
                }
                Spacer(minLength: 0)
            }
            HStack(alignment: .top, spacing: 6) {
                ForEach(provider.windows) { window in
                    WindowGauge(
                        window: window,
                        mode: mode,
                        stale: stale,
                        // Both providers have to fit a 41 mm screen without scrolling.
                        diameter: provider.windows.count >= 3 ? 38 : 46,
                        stroke: QuotaTheme.ringStrokeWidget,
                        compact: true,
                        now: now
                    )
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.dark.surface,
                    in: RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))
    }
}
