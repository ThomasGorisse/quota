import SwiftUI
import WidgetKit

/// Every widget layout, in the shared target so the debug preview inside the app renders the
/// exact same views the extension does — a screenshot of the preview is a screenshot of the
/// widget, not of a mock-up.

// MARK: - Header

/// "● Quota · 42 s ago" — the line at the top of the home-screen widgets.
struct WidgetHeader: View {
    let snapshot: Snapshot
    var now: Date = Date()
    var showsTitle: Bool = true

    @Environment(\.palette) private var palette

    var body: some View {
        let severity = snapshot.severity(now: now)
        HStack(spacing: 6) {
            Circle()
                .fill(QuotaTheme.color(for: severity))
                .frame(width: 8, height: 8)
            if showsTitle {
                Text("Quota")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(palette.textPrimary)
            }
            Text(severity == .stale ? "STALE" : QuotaFormat.agoShort(snapshot.age(now: now)))
                .font(.system(size: 12, weight: severity == .stale ? .semibold : .regular))
                .monospacedDigit()
                .foregroundStyle(severity == .stale ? QuotaTheme.stale : palette.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 0)
        }
    }
}

/// One provider's rings under its name, in the accent colour — the grouping the Android 4×2
/// widget uses, so the two products read the same.
struct WidgetProviderGroup: View {
    let name: String
    let provider: ProviderStatus
    var mode: DisplayMode = .remaining
    var stale: Bool
    var diameter: CGFloat
    var now: Date = Date()

    var body: some View {
        VStack(spacing: 4) {
            Text(name)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(QuotaTheme.accent(for: name))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            HStack(alignment: .top, spacing: 12) {
                ForEach(provider.windows) { window in
                    WindowGauge(
                        window: window,
                        mode: mode,
                        stale: stale,
                        diameter: diameter,
                        stroke: QuotaTheme.ringStrokeWidget,
                        compact: true,
                        now: now
                    )
                }
            }
        }
    }
}

// MARK: - Home screen

/// Small: the two 5 h windows, which are the two numbers that change during a working day.
struct QuotaSmallView: View {
    let snapshot: Snapshot
    var now: Date = Date()

    @Environment(\.palette) private var palette

    var body: some View {
        let stale = snapshot.isStale(now: now)
        let mode = snapshot.display
        VStack(alignment: .leading, spacing: 8) {
            WidgetHeader(snapshot: snapshot, now: now)
            Spacer(minLength: 0)
            HStack(spacing: 10) {
                ForEach(ProviderId.allCases, id: \.self) { id in
                    if let window = snapshot.window(id, "session") {
                        VStack(spacing: 3) {
                            RingGauge(
                                value: window.value(mode),
                                severity: stale ? .stale : window.severity,
                                mode: mode,
                                diameter: 54,
                                stroke: QuotaTheme.ringStrokeWidget,
                                showsCaption: false
                            )
                            Text(id.displayName)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(QuotaTheme.accent(for: id.displayName))
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                            if let left = window.resetsIn(now: now) {
                                Text(QuotaFormat.countdownShort(left))
                                    .font(.system(size: 10))
                                    .monospacedDigit()
                                    .foregroundStyle(palette.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
    }
}

/// Medium: all five rings, the same reading as the Android 4×2.
struct QuotaMediumView: View {
    let snapshot: Snapshot
    var now: Date = Date()

    var body: some View {
        let stale = snapshot.isStale(now: now)
        let mode = snapshot.display
        VStack(alignment: .leading, spacing: 8) {
            WidgetHeader(snapshot: snapshot, now: now)
            Spacer(minLength: 0)
            HStack(alignment: .top, spacing: 24) {
                ForEach(snapshot.orderedProviders(), id: \.name) { entry in
                    WidgetProviderGroup(
                        name: entry.name,
                        provider: entry.provider,
                        mode: mode,
                        stale: stale,
                        diameter: 50,
                        now: now
                    )
                }
            }
            .frame(maxWidth: .infinity)
            Spacer(minLength: 0)
        }
        .padding(14)
    }
}

// MARK: - Lock screen

/// Circular: there is room for exactly one ring, so it shows the window with the least left.
struct QuotaCircularView: View {
    let snapshot: Snapshot
    var now: Date = Date()

    var body: some View {
        let stale = snapshot.isStale(now: now)
        let mode = snapshot.display
        let worst = snapshot.mostCritical()
        ZStack {
            AccessoryWidgetBackground()
            if let worst {
                Circle()
                    .trim(from: 0, to: Double(worst.window.value(mode)) / 100)
                    .stroke(
                        QuotaTheme.color(for: stale ? .stale : worst.window.severity),
                        style: StrokeStyle(lineWidth: 5, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
                    .padding(3)
                VStack(spacing: 0) {
                    Text("\(worst.window.value(mode))")
                        .font(.system(size: 21, weight: .semibold))
                        .monospacedDigit()
                        .minimumScaleFactor(0.5)
                        .lineLimit(1)
                    BrandGlyph(providerName: worst.provider, size: 9)
                }
            } else {
                Text("—").font(.system(size: 20, weight: .semibold))
            }
        }
        .widgetAccentable()
        .accessibilityLabel(worst.map {
            "\($0.provider) \($0.window.label), \($0.window.value(mode)) percent \(mode == .used ? "used" : "left")"
        } ?? "No data")
    }
}

/// Rectangular: two lines, "Claude 89% · 2h09" and "Codex 8% · 36m".
struct QuotaRectangularView: View {
    let snapshot: Snapshot
    var now: Date = Date()

    var body: some View {
        let stale = snapshot.isStale(now: now)
        let mode = snapshot.display
        VStack(alignment: .leading, spacing: 2) {
            ForEach(ProviderId.allCases, id: \.self) { id in
                if let window = snapshot.window(id, "session") {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(QuotaTheme.color(for: stale ? .stale : window.severity))
                            .frame(width: 7, height: 7)
                        Text(id.displayName)
                            .font(.system(size: 14, weight: .semibold))
                        Text("\(window.value(mode))%")
                            .font(.system(size: 14, weight: .semibold))
                            .monospacedDigit()
                        if let left = window.resetsIn(now: now) {
                            Text("· \(QuotaFormat.countdownShort(left))")
                                .font(.system(size: 13))
                                .monospacedDigit()
                                .foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                    }
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                }
            }
            if stale {
                Text("STALE")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
            }
        }
        .widgetAccentable()
    }
}

/// Corner (watch only): the remaining number with the ring as its curved gauge label.
struct QuotaCornerView: View {
    let snapshot: Snapshot
    var now: Date = Date()

    var body: some View {
        let stale = snapshot.isStale(now: now)
        let mode = snapshot.display
        let worst = snapshot.mostCritical()
        Text("\(worst?.window.value(mode) ?? 0)")
            .font(.system(size: 18, weight: .semibold))
            .monospacedDigit()
            .widgetAccentable()
            .widgetLabel {
                Gauge(value: Double(worst?.window.value(mode) ?? 0), in: 0...100) {
                    Text(worst?.provider ?? "Quota")
                }
                .tint(QuotaTheme.color(for: stale ? .stale : (worst?.window.severity ?? .ok)))
            }
    }
}
