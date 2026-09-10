import SwiftUI
import WidgetKit

/// These same views are used by the debug capture previews.
struct WidgetHeader: View {
    let snapshot: Snapshot
    var now: Date = Date()
    var showsTitle: Bool = true
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(QuotaTheme.color(for: snapshot.severity(now: now))).frame(width: 6, height: 6)
            if showsTitle { Text("Quota").font(.system(size: 13, weight: .semibold)) }
            Text(snapshot.severity(now: now).label)
                .font(.system(size: 11)).foregroundStyle(palette.textSecondary)
                .lineLimit(1).minimumScaleFactor(0.7)
            Spacer(minLength: 0)
        }
    }
}

/// One overall hero regardless of the number of providers.
struct QuotaSmallView: View {
    let snapshot: Snapshot
    var now: Date = Date()
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 4) {
            if let hero = snapshot.mostCritical() {
                RingGauge(value: hero.window.value(snapshot.display),
                          severity: snapshot.isStale(now: now) ? .stale : hero.window.severity,
                          mode: snapshot.display, diameter: 72, stroke: 6, valueScale: 0.30,
                          animates: false)
                HStack(spacing: 4) {
                    BrandGlyph(providerName: hero.provider, size: 10)
                    Text("\(hero.provider) · \(hero.window.displayLabel)")
                        .font(.system(size: 12, weight: .semibold))
                        .lineLimit(1).minimumScaleFactor(0.7)
                }
                Text(snapshot.isStale(now: now) ? "Out of date" :
                     (QuotaFormat.resetLine(hero.window, now: now) ?? "Reset time unknown"))
                    .font(.system(size: 10)).monospacedDigit()
                    .foregroundStyle(palette.textSecondary)
                    .lineLimit(1).minimumScaleFactor(0.7)
            } else {
                Text("Quota").font(.system(size: 16, weight: .semibold))
                Text("No data").font(.system(size: 12)).foregroundStyle(palette.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(8)
    }
}

/// One overall hero and quiet provider headlines, always in canonical provider order.
struct QuotaMediumView: View {
    let snapshot: Snapshot
    var now: Date = Date()
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            WidgetHeader(snapshot: snapshot, now: now)
            if let hero = snapshot.mostCritical() {
                HStack(spacing: 16) {
                    VStack(spacing: 4) {
                        RingGauge(value: hero.window.value(snapshot.display),
                                  severity: snapshot.isStale(now: now) ? .stale : hero.window.severity,
                                  mode: snapshot.display, diameter: 80, stroke: 6, valueScale: 0.30,
                                  animates: false)
                        HStack(spacing: 4) {
                            BrandGlyph(providerName: hero.provider, size: 10)
                            Text("\(hero.provider) · \(hero.window.displayLabel)")
                                .font(.system(size: 11, weight: .semibold))
                                .lineLimit(1).minimumScaleFactor(0.7)
                        }
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text(providerPlanSubtitle(hero.provider, plan: snapshot.orderedProviders().first { $0.name == hero.provider }?.provider.plan))
                            .font(.system(size: 10)).foregroundStyle(palette.textSecondary)
                            .lineLimit(1).minimumScaleFactor(0.7)
                        Text(QuotaFormat.resetLine(hero.window, now: now) ?? "Reset time unknown")
                            .font(.system(size: 12)).monospacedDigit()
                            .foregroundStyle(palette.textSecondary)
                            .lineLimit(1).minimumScaleFactor(0.7)
                        ForEach(snapshot.orderedProviders().filter { $0.name != hero.provider }, id: \.name) { entry in
                            if let window = entry.provider.primaryWindows(limit: 1).first {
                                HStack(spacing: 4) {
                                    BrandGlyph(providerName: entry.name, size: 12)
                                    Text("\(entry.name) · \(window.displayLabel) \(window.value(snapshot.display))% \(snapshot.display.caption)")
                                        .font(.system(size: 12, weight: .medium)).monospacedDigit()
                                        .lineLimit(1).minimumScaleFactor(0.7)
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                Text("No data").foregroundStyle(palette.textSecondary)
            }
        }
        .padding(8)
    }
}

/// System-tinted accessories cannot promise severity hue; the stale state is literal text.
struct QuotaCircularView: View {
    let snapshot: Snapshot
    var now: Date = Date()

    var body: some View {
        let hero = snapshot.mostCritical()
        let stale = snapshot.isStale(now: now)
        ZStack {
            AccessoryWidgetBackground()
            if let hero {
                Circle().trim(from: 0, to: Double(hero.window.value(snapshot.display)) / 100)
                    .stroke(QuotaTheme.color(for: stale ? .stale : hero.window.severity),
                            style: StrokeStyle(lineWidth: 4, lineCap: .round))
                    .rotationEffect(.degrees(-90)).padding(3)
                VStack(spacing: 0) {
                    Text("\(hero.window.value(snapshot.display))%")
                        .font(.system(size: 17, weight: .semibold)).monospacedDigit()
                    Text(snapshot.display.caption)
                        .font(.system(size: 8)).lineLimit(1).minimumScaleFactor(0.5)
                    if stale {
                        Text("Out of date").font(.system(size: 8)).lineLimit(1).minimumScaleFactor(0.5)
                    } else {
                        BrandGlyph(providerName: hero.provider, size: 8)
                    }
                }
            } else { Text("—").font(.system(size: 20, weight: .semibold)) }
        }
        .widgetAccentable()
        .accessibilityLabel(hero.map {
            "\($0.provider) \($0.window.displayLabel), \($0.window.value(snapshot.display)) percent \(snapshot.display.caption), \(stale ? "Out of date" : (QuotaFormat.resetLine($0.window, now: now) ?? "Reset time unknown"))"
        } ?? "No data")
    }
}

struct QuotaRectangularView: View {
    let snapshot: Snapshot
    var now: Date = Date()

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let hero = snapshot.mostCritical() {
                Text("\(hero.window.value(snapshot.display))% \(snapshot.display.caption)")
                    .font(.system(size: 22, weight: .semibold)).monospacedDigit()
                Text("\(hero.provider) · \(hero.window.displayLabel)")
                    .font(.system(size: 12, weight: .semibold))
                Text(snapshot.isStale(now: now) ? "Out of date" :
                     (QuotaFormat.resetLine(hero.window, now: now) ?? "Reset time unknown"))
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            } else { Text("Quota · No data") }
        }
        .lineLimit(1).minimumScaleFactor(0.7)
        .widgetAccentable()
    }
}

struct QuotaCornerView: View {
    let snapshot: Snapshot
    var now: Date = Date()

    var body: some View {
        let hero = snapshot.mostCritical()
        Text(hero.map { "\($0.window.value(snapshot.display))%" } ?? "—")
            .font(.system(size: 18, weight: .semibold)).monospacedDigit()
            .widgetAccentable()
            .widgetLabel {
                if let hero {
                    Gauge(value: Double(hero.window.value(snapshot.display)), in: 0...100) {
                        Text(snapshot.isStale(now: now) ? "Out of date" :
                             "\(hero.provider) · \(hero.window.displayLabel) · \(snapshot.display.caption)")
                    }
                    .tint(QuotaTheme.color(for: snapshot.isStale(now: now) ? .stale : hero.window.severity))
                } else { Text("No data") }
            }
    }
}
