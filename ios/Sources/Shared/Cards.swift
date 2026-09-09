import SwiftUI

/// The header pill: a dot in the severity colour, the word, and a tint of the same colour
/// behind it. "STALE" is a severity like the others here — grey, and it wins over the value.
struct SeverityPill: View {
    let severity: Severity
    var compact: Bool = false

    private var color: Color { QuotaTheme.color(for: severity) }
    private var text: String { severity == .stale ? "STALE" : severity.label }

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(color)
                .frame(width: compact ? 6 : 8, height: compact ? 6 : 8)
            Text(text)
                .font(compact ? .system(size: 12, weight: .semibold) : .system(size: 16, weight: .semibold))
                .foregroundStyle(color)
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

    private var accent: Color { QuotaTheme.accent(for: providerName) }

    private var assetName: String? {
        switch providerName.lowercased() {
        case "claude": return "BrandClaude"
        case "codex": return "BrandCodex"
        default: return nil
        }
    }

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
            .fill(accent.opacity(0.16))
            .frame(width: size, height: size)
            .overlay {
                if let assetName {
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

    private var assetName: String? {
        switch providerName.lowercased() {
        case "claude": return "BrandClaude"
        case "codex": return "BrandCodex"
        default: return nil
        }
    }

    var body: some View {
        if let assetName {
            Image(assetName)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        }
    }
}

/// Who a provider is, under its name — matching the web dashboard.
func providerSubtitle(_ name: String) -> String? {
    switch name.lowercased() {
    case "claude": return "Anthropic"
    case "codex": return "ChatGPT"
    default: return nil
    }
}

/// One provider: its mark, its name and plan, then its windows as rings side by side.
struct ProviderCard: View {
    let name: String
    let provider: ProviderStatus
    var mode: DisplayMode = .remaining
    var stale: Bool = false
    var now: Date = Date()
    /// The width the card itself will occupy. The rings are sized from it rather than from a
    /// GeometryReader inside the card, so the card keeps its intrinsic height and no dead
    /// space is left under a two-ring provider.
    var availableWidth: CGFloat = 358

    @Environment(\.palette) private var palette

    private var accent: Color { QuotaTheme.accent(for: name) }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                BrandMark(providerName: name)
                VStack(alignment: .leading, spacing: 1) {
                    Text(name)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(palette.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    if let subtitle = providerSubtitle(name) {
                        Text(subtitle)
                            .font(.system(size: 13))
                            .foregroundStyle(palette.textSecondary)
                            .lineLimit(1)
                    }
                }
                if let plan = provider.plan, !plan.isEmpty {
                    PlanChip(plan: plan, accent: accent)
                }
                Spacer(minLength: 0)
            }

            HStack(alignment: .top, spacing: ringSpacing) {
                ForEach(provider.windows) { window in
                    WindowGauge(window: window, mode: mode, stale: stale, diameter: ringDiameter, now: now)
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))
    }

    private var ringSpacing: CGFloat { provider.windows.count >= 3 ? 20 : 12 }

    private var ringDiameter: CGFloat {
        let count = max(provider.windows.count, 1)
        let inner = availableWidth - 40 - ringSpacing * CGFloat(count - 1)
        // Capped so a provider reporting two windows and one reporting three still read as
        // the same product on the same screen.
        return min(max(inner / CGFloat(count), 64), 124)
    }
}
