import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// The tokens from `docs/DESIGN.md`, and nothing else. Severity has to mean the same green,
/// amber and red on every surface, so none of these are system or accent colours.
enum QuotaTheme {

    // Severity — identical in light and dark.
    static let ok = Color(hex: 0x22C55E)
    static let warn = Color(hex: 0xF59E0B)
    static let critical = Color(hex: 0xEF4444)
    static let stale = Color(hex: 0x6B7280)

    // Provider accents.
    static let claude = Color(hex: 0xD97757)

    /// OpenAI retired the ChatGPT green in its 2025 rebrand and is monochrome now. Their
    /// black would vanish on ``Palette/dark``, so the accent follows the scheme.
    #if os(watchOS)
    // The watch is always dark: only the near-white half of the pair ever applies.
    static let codex = Color(hex: 0xECECEC)
    #else
    static let codex = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(Color(hex: 0xECECEC)) : UIColor(Color(hex: 0x0D0D0D))
    })
    #endif

    /// The Gemini mark is a blue → violet → pink sweep. ``geminiSweep`` draws it where a
    /// gradient is cheap; this flat tint is what everything else uses.
    static let gemini = Color(hex: 0x8E75B2)

    static let geminiSweep = AngularGradient(
        colors: [Color(hex: 0x4285F4), Color(hex: 0x9B72CB), Color(hex: 0xD96570), Color(hex: 0x4285F4)],
        center: .center
    )

    static func color(for severity: Severity) -> Color {
        switch severity {
        case .ok: return ok
        case .warn: return warn
        case .critical: return critical
        case .stale: return stale
        }
    }

    static func accent(for providerName: String) -> Color {
        switch providerName.lowercased() {
        case "claude": return claude
        case "codex": return codex
        case "gemini": return gemini
        default: return stale
        }
    }

    // Radius and spacing: 8-grid, 20 on cards, 12 on chips.
    static let cardRadius: CGFloat = 20
    static let chipRadius: CGFloat = 12
    /// Stroke widths from DESIGN.md.
    static let ringStrokePhone: CGFloat = 12
    static let ringStrokeWidget: CGFloat = 8
    /// The empty part of a ring is a neutral track, not a tint of the value colour: a dark
    /// red halo behind a red arc reads as "nearly full" instead of "nearly empty". Its colour
    /// comes from ``Palette/ringTrack`` so it keeps the same contrast in both schemes.
    /// "Ring animates on value change only, 400 ms ease-out. Nothing else moves."
    static let ringAnimation = Animation.easeOut(duration: 0.4)
}

/// Background and text, which are the only tokens that differ between the two schemes.
struct Palette {
    let background: Color
    let surface: Color
    let textPrimary: Color
    let textSecondary: Color
    /// The empty part of a ring. Neutral and clearly visible, so a nearly-empty gauge still
    /// shows the full circle it is emptying out of.
    let ringTrack: Color

    static let dark = Palette(
        background: Color(hex: 0x0B0F14),
        surface: Color(hex: 0x141A22),
        textPrimary: Color(hex: 0xF3F4F6),
        textSecondary: Color(hex: 0x9AA4B2),
        ringTrack: Color(hex: 0x2A3240)
    )

    static let light = Palette(
        background: Color(hex: 0xF6F7F9),
        surface: Color(hex: 0xFFFFFF),
        textPrimary: Color(hex: 0x111827),
        textSecondary: Color(hex: 0x6B7280),
        ringTrack: Color(hex: 0xD8DDE5)
    )

    static func of(_ scheme: ColorScheme) -> Palette {
        scheme == .dark ? .dark : .light
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette.dark
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

extension View {
    /// Resolves the palette from the current colour scheme once, at the root of a screen.
    func quotaPalette(_ scheme: ColorScheme) -> some View {
        environment(\.palette, Palette.of(scheme))
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}
