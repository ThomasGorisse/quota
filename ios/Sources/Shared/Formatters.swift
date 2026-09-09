import Foundation

/// Every duration and age string in the product, in one place, so the phone, the widget and
/// the watch never disagree about what "2 h 09" means.
enum QuotaFormat {

    /// "2 h 09", "36 min", "2 d 13 h" — the long form used on the phone card.
    static func countdown(_ seconds: TimeInterval) -> String {
        let total = Int(seconds.rounded())
        if total <= 0 { return "now" }
        let days = total / 86_400
        let hours = (total % 86_400) / 3600
        let minutes = (total % 3600) / 60
        if days > 0 { return "\(days) d \(hours) h" }
        if hours > 0 { return String(format: "%d h %02d", hours, minutes) }
        if minutes > 0 { return "\(minutes) min" }
        return "< 1 min"
    }

    /// "2h09", "36m", "2d" — the tight form the widgets and the watch use under a ring.
    static func countdownShort(_ seconds: TimeInterval) -> String {
        let total = Int(seconds.rounded())
        if total <= 0 { return "now" }
        let days = total / 86_400
        let hours = (total % 86_400) / 3600
        let minutes = (total % 3600) / 60
        if days > 0 { return "\(days)d" }
        if hours > 0 { return String(format: "%dh%02d", hours, minutes) }
        return "\(max(minutes, 1))m"
    }

    /// "resets in 2 h 09", or nothing at all when the payload carries no reset date.
    static func resetLine(_ window: Window, now: Date = Date()) -> String? {
        guard let left = window.resetsIn(now: now) else { return nil }
        return "resets in \(countdown(left))"
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
