import SwiftUI

/// The main screen: how much is left, how long until it comes back, and how old the number is.
///
/// The screen sits in a navigation stack so the title is the system large title, folding into the
/// bar as the cards scroll under it. The bar never takes a flat fill: the page runs edge to edge
/// and the system's scroll-edge treatment is the only thing behind the title. A card opens its
/// provider's detail by zooming out of itself (iOS 18; a plain push before).
struct HomeView: View {
    @EnvironmentObject private var model: StatusViewModel
    @Environment(\.palette) private var palette
    @State private var showsSettings = false
    @State private var path: [String] = []
    @Namespace private var cards

    var body: some View {
        NavigationStack(path: $path) {
            GeometryReader { geometry in
                content(width: geometry.size.width - 32)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Quota")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showsSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .tint(palette.textSecondary)
                    .accessibilityLabel("Settings")
                }
            }
            .navigationDestination(for: String.self) { name in
                ProviderDetailView(name: name)
                    .zoomTransition(from: name, in: cards)
            }
        }
        #if DEBUG
        .task { await debugRoute() }
        #endif
    }

    #if DEBUG
    /// Debug launch arguments for captures, next to the shared ones in `LaunchOptions`:
    /// `-QuotaProvider Claude` opens that provider's detail; `-QuotaTour` zooms into the first card and back, twice.
    private func debugRoute() async {
        let arguments = ProcessInfo.processInfo.arguments
        if let index = arguments.firstIndex(of: "-QuotaProvider"), index + 1 < arguments.count {
            path = [arguments[index + 1]]
        } else if arguments.contains("-QuotaTour") {
            // The snapshot loads after launch, so wait for the first card before touring.
            var first: String?
            for await snapshot in model.$snapshot.values {
                first = snapshot?.orderedProviders().first?.name
                if first != nil { break }
            }
            guard let first else { return }
            for _ in 0..<2 {
                try? await Task.sleep(for: .seconds(2))
                path = [first]
                try? await Task.sleep(for: .seconds(3))
                path = []
            }
        }
    }
    #endif

    private func content(width: CGFloat) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                title
                header
                if let snapshot = model.snapshot {
                    if snapshot.orderedProviders().isEmpty {
                        notice("Your collector is connected. Provider limits will appear after its next update.", tone: QuotaTheme.stale)
                    }
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 340), spacing: 20)], alignment: .leading, spacing: 20) {
                    ForEach(snapshot.orderedProviders(), id: \.name) { entry in
                        NavigationLink(value: entry.name) {
                            ProviderCard(
                                name: entry.name,
                                provider: entry.provider,
                                mode: model.displayMode,
                                stale: snapshot.isStale(provider: entry.name, now: model.now),
                                now: model.now,
                                availableWidth: width
                            )
                            .zoomSource(entry.name, in: cards)
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint("Shows every limit")
                    }
                    }
                    if let message = model.errorMessage {
                        notice(message, tone: QuotaTheme.warn)
                    }
                } else if let message = model.errorMessage {
                    notice(message, tone: QuotaTheme.critical)
                } else {
                    waiting
                }
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .refreshable { await model.refresh() }
        .sheet(isPresented: $showsSettings) {
            SettingsView().environmentObject(model)
        }
    }

    private var title: some View {
        Text("Your AI, at a glance.")
            .font(.body)
            .foregroundStyle(palette.textSecondary)
    }

    private var header: some View {
        HStack(spacing: 12) {
            SeverityPill(severity: model.snapshot?.severity(now: model.now) ?? .stale)
            Text(model.snapshot.map { QuotaFormat.updatedAgo($0.age(now: model.now)) } ?? "never updated")
                .font(.system(size: 15))
                .monospacedDigit()
                .foregroundStyle(palette.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 0)
            if model.snapshot?.isDemo == true {
                // "Demo", like the web and Android chips: one word, one case, everywhere.
                Text("Demo")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(palette.textSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(palette.textSecondary.opacity(0.15),
                                in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
        }
        .frame(minHeight: 32)
    }

    private var waiting: some View {
        VStack(spacing: 12) {
            ProgressView().tint(palette.textSecondary)
            Text("Waiting for the first snapshot…")
                .font(.system(size: 15))
                .foregroundStyle(palette.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 60)
    }

    private func notice(_ message: String, tone: Color) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(tone)
            VStack(alignment: .leading, spacing: 12) {
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(palette.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Button("Try again") { Task { await model.refresh() } }
                .disabled(model.isRefreshing)
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.opacity(0.12),
                    in: RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))
    }
}

/// One provider, every window. The hero is the card's own ring — the same window, the same number —
/// and the other windows are text rows, never more rings (DESIGN.md). Nothing here computes a
/// figure: values, captions and reset lines come from the helpers the card already uses.
struct ProviderDetailView: View {
    let name: String
    @EnvironmentObject private var model: StatusViewModel
    @Environment(\.palette) private var palette

    var body: some View {
        ScrollView {
            if let snapshot = model.snapshot,
               let entry = snapshot.orderedProviders().first(where: { $0.name == name }) {
                let stale = snapshot.isStale(provider: name, now: model.now)
                VStack(spacing: 20) {
                    hero(entry.provider, stale: stale)
                    if !entry.provider.windows.isEmpty {
                        windows(entry.provider)
                    }
                }
                .padding(16)
            } else {
                Text("No data")
                    .foregroundStyle(palette.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 60)
            }
        }
        .scrollIndicators(.hidden)
        .refreshable { await model.refresh() }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func hero(_ provider: ProviderStatus, stale: Bool) -> some View {
        VStack(spacing: 14) {
            if let hero = provider.primaryWindows(limit: 1).first {
                RingGauge(value: hero.value(model.displayMode), severity: stale ? .stale : hero.severity,
                          mode: model.displayMode, diameter: 168)
                Text(stale ? "Out of date" : hero.severity.label)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(QuotaTheme.ink(for: stale ? .stale : hero.severity))
            } else {
                Text("No data").foregroundStyle(palette.textSecondary)
            }
            HStack(spacing: 8) {
                BrandMark(providerName: name, size: 24, stale: stale)
                Text(providerPlanSubtitle(name, plan: provider.plan))
                    .font(.system(size: 14))
                    .foregroundStyle(palette.textSecondary)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))
    }

    private func windows(_ provider: ProviderStatus) -> some View {
        let rows = provider.primaryWindows(limit: provider.windows.count)
        return VStack(spacing: 0) {
            ForEach(rows) { window in
                if window.id != rows.first?.id {
                    Rectangle().fill(palette.ringTrack).frame(height: 1)
                }
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(window.displayLabel)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(palette.textPrimary)
                        Text(QuotaFormat.resetLine(window, now: model.now) ?? "Reset time unknown")
                            .font(.system(size: 12)).monospacedDigit()
                            .foregroundStyle(palette.textSecondary)
                    }
                    Spacer(minLength: 8)
                    Text("\(window.value(model.displayMode))% \(model.displayMode.caption)")
                        .font(.system(size: 16, weight: .semibold)).monospacedDigit()
                        .foregroundStyle(palette.textPrimary)
                }
                .padding(.vertical, 14)
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.horizontal, 20)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))
    }
}

private extension View {
    /// The card a detail screen zooms out of, clipped to the card's own corners.
    @ViewBuilder
    func zoomSource(_ id: String, in namespace: Namespace.ID) -> some View {
        if #available(iOS 18, *) {
            matchedTransitionSource(id: id, in: namespace) {
                $0.clipShape(RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))
            }
        } else {
            self
        }
    }

    /// With Reduce Motion on, the system plays this zoom as a cross-fade.
    @ViewBuilder
    func zoomTransition(from id: String, in namespace: Namespace.ID) -> some View {
        if #available(iOS 18, *) {
            navigationTransition(.zoom(sourceID: id, in: namespace))
        } else {
            self
        }
    }
}
