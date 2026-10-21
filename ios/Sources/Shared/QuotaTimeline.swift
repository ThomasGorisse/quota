import SwiftUI
import WidgetKit

/// One rendering of the widget. The date is what every countdown in the entry is measured
/// against, so a timeline of entries keeps "36m" honest between two network calls.
struct QuotaEntry: TimelineEntry {
    let date: Date
    let snapshot: Snapshot
}

/// The timeline provider fetches `/v1/status` itself — a widget must be able to update when
/// the app has not been opened for days.
struct QuotaTimelineProvider: TimelineProvider {

    /// How often the provider asks the system to run it again.
    static let refreshInterval: TimeInterval = 15 * 60
    /// Entries inside one window, so the countdowns move without a network call.
    static let entryStride: TimeInterval = 3 * 60

    func placeholder(in context: Context) -> QuotaEntry {
        QuotaEntry(date: Date(), snapshot: DemoData.snapshot())
    }

    func getSnapshot(in context: Context, completion: @escaping (QuotaEntry) -> Void) {
        if context.isPreview {
            completion(QuotaEntry(date: Date(), snapshot: DemoData.snapshot()))
            return
        }
        Task {
            let now = Date()
            let snapshot = await QuotaClient().refreshSharedOrCached()
                ?? Snapshot(status: StatusResponse(collectedAt: ISO8601.string(now), stale: true),
                            receivedAt: now)
            completion(QuotaEntry(date: now, snapshot: snapshot))
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QuotaEntry>) -> Void) {
        Task {
            let now = Date()
            // A failed fetch falls back to the cached snapshot; once it is more than ten
            // minutes old the stale rule greys the rings on its own, which is the honest
            // reading of "I could not reach the host".
            let snapshot = await QuotaClient().refreshSharedOrCached()
                ?? Snapshot(status: StatusResponse(collectedAt: ISO8601.string(now), stale: true),
                            receivedAt: now)

            var entries: [QuotaEntry] = []
            var offset: TimeInterval = 0
            while offset < Self.refreshInterval {
                entries.append(QuotaEntry(date: now.addingTimeInterval(offset), snapshot: snapshot))
                offset += Self.entryStride
            }
            completion(Timeline(entries: entries,
                                policy: .after(now.addingTimeInterval(Self.refreshInterval))))
        }
    }
}
