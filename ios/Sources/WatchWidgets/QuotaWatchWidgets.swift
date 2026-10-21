import SwiftUI
import WidgetKit

@main
struct QuotaWatchWidgetBundle: WidgetBundle {
    var body: some Widget {
        QuotaWatchComplication()
    }
}

/// The complication: a circular ring on the least-left window, a corner gauge, and the two-line
/// rectangular reading — the same three views the iPhone lock screen uses.
struct QuotaWatchComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "QuotaWatchComplication", provider: QuotaTimelineProvider()) { entry in
            QuotaWatchComplicationView(entry: entry)
        }
        .configurationDisplayName("Quota")
        .description("Claude, Codex and Gemini limits, at a glance.")
        .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryRectangular, .accessoryInline])
    }
}

struct QuotaWatchComplicationView: View {
    let entry: QuotaEntry

    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch family {
        case .accessoryCorner:
            QuotaCornerView(snapshot: entry.snapshot, now: entry.date)
                .containerBackground(.clear, for: .widget)
        case .accessoryRectangular:
            QuotaRectangularView(snapshot: entry.snapshot, now: entry.date)
                .containerBackground(.clear, for: .widget)
        case .accessoryInline:
            let worst = entry.snapshot.mostCritical()
            Text(worst.map {
                "\($0.provider) · \($0.window.displayLabel) · \($0.window.value(entry.snapshot.display))% \(entry.snapshot.display.caption)\(entry.snapshot.isStale(provider: $0.provider, now: entry.date) ? " · Out of date" : "")"
            } ?? "Quota · No data")
        default:
            QuotaCircularView(snapshot: entry.snapshot, now: entry.date)
                .containerBackground(.clear, for: .widget)
        }
    }
}
