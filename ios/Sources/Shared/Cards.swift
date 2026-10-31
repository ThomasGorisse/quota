import SwiftUI

/// The header pill: a dot in the severity colour, the word in its ink, and a tint of the same
/// colour behind it. "STALE" is a severity like the others here — grey, and it wins over the value.
struct SeverityPill: View {
    let severity: Severity
    var compact: Bool = false

    private var color: Color { QuotaTheme.color(for: severity) }
    private var text: String { severity.label }

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(color)
                .frame(width: compact ? 6 : 8, height: compact ? 6 : 8)
            Text(text)
                .font(compact ? .system(size: 12, weight: .semibold) : .system(size: 16, weight: .semibold))
                .foregroundStyle(QuotaTheme.ink(for: severity))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .padding(.horizontal, compact ? 8 : 14)
        .padding(.vertical, compact ? 4 : 8)
        .background(color.opacity(0.16), in: RoundedRectangle(cornerRadius: QuotaTheme.chipRadius, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Status \(text)")
    }
}

/// "MAX" / "PLUS" next to the provider name, tinted with the provider accent.
struct PlanChip: View {
    let plan: String
    let accent: Color

    var body: some View {
        Text(plan.uppercased())
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(accent)
            .lineLimit(1)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}

/// The provider's own mark in a rounded square, tinted with its accent.
///
/// The marks are the monochrome ones from simple-icons, imported as template SVGs, so they
/// take the accent colour and never carry a brand's own palette into this UI. A provider this
/// version does not know falls back to a plain dot.
struct BrandMark: View {
    let providerName: String
    var size: CGFloat = 32
    var stale: Bool = false

    private var accent: Color { stale ? QuotaTheme.stale : QuotaTheme.accent(for: providerName) }

    private var assetName: String? {
        switch providerName.lowercased() {
        case "claude": return "BrandClaude"
        case "codex": return "BrandCodex"
        case "gemini": return "BrandGemini"
        default: return nil
        }
    }

    /// The Gemini mark is the one that carries its own palette: a flat tint of that logo is
    /// a different logo. Xcode's SVG subset does not render lobehub's four overlapping
    /// gradients, so the vendored single-path star is filled with the brand sweep instead —
    /// same colours, one shape, and it survives every rendering scale.
    private var isGemini: Bool { !stale && providerName.lowercased() == "gemini" }

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
            .fill(accent.opacity(0.16))
            .frame(width: size, height: size)
            .overlay {
                if isGemini {
                    RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
                        .fill(QuotaTheme.geminiSweep)
                        .opacity(0.18)
                }
            }
            .overlay {
                if let assetName, isGemini {
                    Image(assetName)
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: size * 0.56, height: size * 0.56)
                        .foregroundStyle(QuotaTheme.geminiSweep)
                } else if let assetName {
                    Image(assetName)
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: size * 0.56, height: size * 0.56)
                        .foregroundStyle(accent)
                } else {
                    Circle()
                        .fill(accent)
                        .frame(width: size * 0.3, height: size * 0.3)
                }
            }
            .accessibilityHidden(true)
    }
}

/// The provider mark on its own, with no rounded square behind it — for the places too small
/// to hold a name, like the lock-screen circular widget.
struct BrandGlyph: View {
    let providerName: String
    var size: CGFloat = 10
    var stale: Bool = false

    private var assetName: String? {
        switch providerName.lowercased() {
        case "claude": return "BrandClaude"
        case "codex": return "BrandCodex"
        case "gemini": return "BrandGemini"
        default: return nil
        }
    }

    var body: some View {
        if let assetName {
            Image(assetName)
                // Template even for Gemini here: a lock-screen glyph is tinted by the system
                // anyway, and a 9 pt gradient would read as mud.
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
                .foregroundStyle(stale ? QuotaTheme.stale : .primary)
        }
    }
}

/// Who a provider is, under its name — matching the web dashboard.
func providerSubtitle(_ name: String) -> String? {
    switch name.lowercased() {
    case "claude": return "Anthropic"
    case "codex": return "ChatGPT"
    case "gemini": return "Google"
    default: return nil
    }
}

/// The plan is a subtitle, never a competing badge.
func providerPlanSubtitle(_ name: String, plan: String?) -> String {
    let raw = plan ?? ""
    let title = raw.prefix(1).uppercased() + String(raw.dropFirst())
    if name.lowercased() == "codex" {
        return raw.lowercased().hasPrefix("chatgpt") ? raw : (raw.isEmpty ? "ChatGPT" : "ChatGPT \(title)")
    }
    return [providerSubtitle(name) ?? "", title].filter { !$0.isEmpty }.joined(separator: " · ")
}

/// One hero per provider; every other window is quiet text with identical treatment.
struct ProviderCard: View {
    let name: String
    let provider: ProviderStatus
    var mode: DisplayMode = .remaining
    var stale: Bool = false
    var now: Date = Date()
    var availableWidth: CGFloat = 358
    @Environment(\.palette) private var palette
    @Environment(\.dynamicTypeSize) private var dynamicType

    var body: some View {
        let layout = dynamicType.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 20)) : AnyLayout(HStackLayout(alignment: .center, spacing: 20))
        layout {
            if let hero = provider.primaryWindows(limit: 1).first {
                RingGauge(value: hero.value(mode), severity: stale ? .stale : hero.severity,
                          mode: mode, diameter: 112, stroke: 8, valueScale: 0.29)
                VStack(alignment: .leading, spacing: 8) {
                    identity
                    Text(stale ? "Out of date" : hero.severity.label)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(QuotaTheme.ink(for: stale ? .stale : hero.severity))
                    Text(hero.displayLabel)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(palette.textPrimary)
                    Text(QuotaFormat.resetLine(hero, now: now) ?? "Reset time unknown")
                        .font(.system(size: 12)).monospacedDigit()
                        .foregroundStyle(palette.textSecondary)
                    ForEach(provider.primaryWindows(limit: provider.windows.count).dropFirst()) { window in
                        Text("\(window.displayLabel) \(window.value(mode))% \(mode.caption)")
                            .font(.system(size: 12)).monospacedDigit()
                            .foregroundStyle(palette.textSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    identity
                    Text("No data").foregroundStyle(palette.textSecondary)
                }
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))
    }

    private var identity: some View {
        HStack(spacing: 8) {
            BrandMark(providerName: name, size: 24, stale: stale)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.title3.weight(.semibold)).foregroundStyle(palette.textPrimary)
                Text(providerPlanSubtitle(name, plan: provider.plan))
                    .font(.system(size: 12)).foregroundStyle(palette.textSecondary)
            }
        }
    }
}
