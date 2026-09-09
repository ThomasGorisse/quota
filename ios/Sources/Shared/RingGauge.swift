import SwiftUI

/// The gauge, as DESIGN.md specifies it: a ring, a neutral track, a big number in the middle,
/// and motion only when the value changes.
///
/// In the default `remaining` mode the arc is what is **left**. In `used` mode it grows with
/// usage instead and the number is the used percent — one field on `/v1/status`, so the phone,
/// the widgets, the watch and the web page always read the same way. The colour is unaffected:
/// severity is always computed on the used percent.
struct RingGauge: View {
    /// The number printed in the middle, already resolved for the current mode.
    let value: Int
    let severity: Severity
    var mode: DisplayMode = .remaining
    var diameter: CGFloat = 128
    var stroke: CGFloat = QuotaTheme.ringStrokePhone
    /// The "% left" / "% used" caption. Off on the widgets, where there is no room.
    var showsCaption: Bool = true
    /// The number, as a fraction of the diameter. Raised on the widgets, where the ring is the
    /// only thing worth reading and the number has to survive a 5 cm tile.
    var valueScale: CGFloat = 0.33
    var valueWeight: Font.Weight = .semibold
    var animates: Bool = true

    @Environment(\.palette) private var palette

    private var color: Color { QuotaTheme.color(for: severity) }
    private var fraction: Double { Double(min(max(value, 0), 100)) / 100 }

    var body: some View {
        ZStack {
            Circle()
                .stroke(palette.ringTrack, lineWidth: stroke)

            Circle()
                .trim(from: 0, to: fraction)
                .stroke(color, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                // Start at twelve o'clock and fill clockwise.
                .rotationEffect(.degrees(-90))
                .animation(animates ? QuotaTheme.ringAnimation : nil, value: fraction)
                .animation(animates ? QuotaTheme.ringAnimation : nil, value: severity)

            centre
                // Keeps the number inside the ring at every Dynamic Type size.
                .frame(width: diameter - stroke * 2 - 8)
        }
        .frame(width: diameter, height: diameter)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(value) percent \(mode == .used ? "used" : "left")")
    }

    private var centre: some View {
        VStack(spacing: 0) {
            Text("\(value)")
                .font(.system(size: diameter * valueScale, weight: valueWeight))
                .monospacedDigit()
                .foregroundStyle(palette.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.4)
            if showsCaption {
                Text(mode.caption)
                    // Big enough to read at a glance: which of the two modes the whole
                    // product is in has to be legible, not deduced.
                    .font(.system(size: max(11, diameter * 0.13), weight: .semibold))
                    .foregroundStyle(palette.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
            }
        }
        // Dynamic Type must not push the number out of the ring: the ring is a graphic whose
        // size is the layout, so the text inside it scales rather than the ring.
        .dynamicTypeSize(...DynamicTypeSize.xxLarge)
    }
}

/// A ring with its window label and reset countdown under it — one column of a provider card.
struct WindowGauge: View {
    let window: Window
    var mode: DisplayMode = .remaining
    /// Grey and captionless when the snapshot is stale, whatever the numbers say.
    var stale: Bool = false
    var diameter: CGFloat = 128
    var stroke: CGFloat = QuotaTheme.ringStrokePhone
    var compact: Bool = false
    var valueScale: CGFloat = 0.33
    var valueWeight: Font.Weight = .semibold
    /// The window label under the ring. Larger on the widgets, where it is the only caption.
    var labelSize: CGFloat? = nil
    var now: Date = Date()

    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: compact ? 4 : 8) {
            RingGauge(
                value: window.value(mode),
                severity: stale ? .stale : window.severity,
                mode: mode,
                diameter: diameter,
                stroke: stroke,
                showsCaption: !compact,
                valueScale: valueScale,
                valueWeight: valueWeight
            )
            Text(window.label)
                .font(.system(size: labelSize ?? (compact ? 12 : 15), weight: .semibold))
                .foregroundStyle(palette.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            if let left = window.resetsIn(now: now) {
                Text(compact ? QuotaFormat.countdownShort(left) : "resets in \(QuotaFormat.countdown(left))")
                    .font(.system(size: labelSize.map { $0 - 2 } ?? (compact ? 11 : 13)))
                    .monospacedDigit()
                    .foregroundStyle(palette.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
    }

    private var accessibilityLabel: String {
        var parts = ["\(window.label): \(window.value(mode)) percent \(mode == .used ? "used" : "left")"]
        if stale { parts.append("stale") }
        if let left = window.resetsIn(now: now) { parts.append("resets in \(QuotaFormat.countdown(left))") }
        return parts.joined(separator: ", ")
    }
}
