import Foundation

/// Debug-only launch arguments, so the screens that need a state to be *in* can be captured
/// without a human tapping through Settings first. Nothing here is compiled into a Release
/// build, and none of it can set a token: pairing still only happens through the URL.
///
/// ```
/// xcrun simctl launch <device> com.gorisse.thomas.quota -QuotaDemo -QuotaDisplay used
/// ```
enum LaunchOptions {
    #if DEBUG
    private static let arguments = ProcessInfo.processInfo.arguments

    /// `-QuotaDemo` — turn the canned snapshot on before the first render.
    static var demo: Bool { arguments.contains("-QuotaDemo") }

    /// `-QuotaDisplay remaining|used` — the display mode, without a round trip to the backend.
    static var display: DisplayMode? {
        value(after: "-QuotaDisplay").flatMap { DisplayMode(rawValue: $0) }
    }

    /// `-QuotaScreen widgets` — open straight into the widget preview.
    static var screen: String? { value(after: "-QuotaScreen") }

    /// `-QuotaWidgetOnly medium|small|lock` — one rendition instead of all of them.
    static var widgetOnly: String? { value(after: "-QuotaWidgetOnly") }

    /// `-QuotaDemoVariant plus|pro` — the shape of the canned payload. `pro` gives a Codex
    /// with two windows instead of three, which is what the layouts have to survive.
    static var demoVariant: DemoData.Variant? {
        value(after: "-QuotaDemoVariant").flatMap { DemoData.Variant(rawValue: $0) }
    }

    private static func value(after flag: String) -> String? {
        guard let index = arguments.firstIndex(of: flag), index + 1 < arguments.count else { return nil }
        let next = arguments[index + 1]
        return next.hasPrefix("-") ? nil : next
    }
    #else
    static var demo: Bool { false }
    static var display: DisplayMode? { nil }
    static var screen: String? { nil }
    static var widgetOnly: String? { nil }
    static var demoVariant: DemoData.Variant? { nil }
    #endif
}
