import SwiftUI
import WidgetKit

@main
struct QuotaWidgetBundle: WidgetBundle {
    var body: some Widget {
        QuotaWidget()
    }
}

struct QuotaWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "QuotaWidget", provider: QuotaTimelineProvider()) { entry in
            QuotaWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Quota")
        .description("Claude, Codex and Gemini limits, at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular])
    }
}

struct QuotaWidgetEntryView: View {
    let entry: QuotaEntry

    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        switch family {
        case .accessoryCircular:
            QuotaCircularView(snapshot: entry.snapshot, now: entry.date)
                .containerBackground(.clear, for: .widget)
        case .accessoryRectangular:
            QuotaRectangularView(snapshot: entry.snapshot, now: entry.date)
                .containerBackground(.clear, for: .widget)
        case .systemSmall:
            QuotaSmallView(snapshot: entry.snapshot, now: entry.date)
                .quotaPalette(scheme)
                .containerBackground(Palette.of(scheme).background, for: .widget)
        default:
            QuotaMediumView(snapshot: entry.snapshot, now: entry.date)
                .quotaPalette(scheme)
                .containerBackground(Palette.of(scheme).background, for: .widget)
        }
    }
}
