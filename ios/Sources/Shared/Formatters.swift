import Foundation

/// Every duration and age string in the product, in one place, so the phone, the widget and
/// the watch use the same labelled durations.
enum QuotaFormat {

    /// Shared precision on every surface: ceil to minutes, then at most two units.
    static func countdown(_ seconds: TimeInterval) -> String {
        if seconds <= 0 { return "due" }
        let minutes = Int(ceil(seconds / 60))
        if minutes < 60 { return "\(minutes) min" }
        if minutes < 1440 { return "\(minutes / 60) h \(minutes % 60) min" }
        return "\(minutes / 1440) d \((minutes % 1440) / 60) h"
    }

    static func countdownShort(_ seconds: TimeInterval) -> String { countdown(seconds) }

    static func resetLine(_ window: Window, now: Date = Date()) -> String? {
        guard let left = window.resetsIn(now: now) else { return "Reset time unknown" }
        return left <= 0 ? "Reset due" : "Resets in \(countdown(left))"
    }

    /// "updated 47 s ago" / "updated 4 min ago" / "updated 2 h ago".
    static func updatedAgo(_ seconds: TimeInterval) -> String {
        let total = Int(max(0, seconds).rounded())
        if total < 60 { return "updated \(total) s ago" }
        if total < 3600 { return "updated \(total / 60) min ago" }
        if total < 86_400 { return "updated \(total / 3600) h ago" }
        return "updated \(total / 86_400) d ago"
    }

    /// The same age, tightened for a widget header: "42 s ago".
    static func agoShort(_ seconds: TimeInterval) -> String {
        let total = Int(max(0, seconds).rounded())
        if total < 60 { return "\(total) s ago" }
        if total < 3600 { return "\(total / 60) min ago" }
        if total < 86_400 { return "\(total / 3600) h ago" }
        return "\(total / 86_400) d ago"
    }
}
