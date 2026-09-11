import SwiftUI

/// Pairing is a paste field first, a QR scanner second: the collector prints the URL in a
/// terminal on the same desk, so pasting it is the path that always works.
struct PairingView: View {
    @EnvironmentObject private var model: StatusViewModel
    @Environment(\.palette) private var palette

    @State private var input = ""
    @State private var error: String?
    @State private var showsScanner = false
    @FocusState private var focused: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 12) {
                        Image(systemName: "circle.dotted.circle.fill")
                            .font(.system(size: 44, weight: .light))
                            .foregroundStyle(QuotaTheme.ok)
                        Text("Quota")
                        .font(.system(size: 36, weight: .bold, design: .rounded))
                        .foregroundStyle(palette.textPrimary)
                    }
                    Text("Know what’s left.\nKeep your flow.")
                        .font(.system(size: 32, weight: .semibold))
                        .foregroundStyle(palette.textPrimary)
                        .padding(.top, 20)
                    Text("Claude, Codex and Gemini capacity. One calm place to check before your next idea.")
                        .font(.system(size: 16))
                        .foregroundStyle(palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                VStack(alignment: .leading, spacing: 12) {
                    Text("Connect your collector")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(palette.textPrimary)
                    Text("Run the Quota collector on your computer, then paste its private pairing link or scan the QR code.")
                        .font(.system(size: 13))
                        .foregroundStyle(palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                    // The placeholder is drawn rather than passed to TextField so it takes the
                    // secondary text token instead of the system tint.
                    TextField("", text: $input, axis: .vertical)
                        .accessibilityLabel("Private pairing link")
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .font(.system(size: 15, design: .monospaced))
                        .foregroundStyle(palette.textPrimary)
                        .focused($focused)
                        .lineLimit(1...3)
                        .padding(14)
                        .overlay(alignment: .leading) {
                            if input.isEmpty {
                                Text(verbatim: "https://quota.sceneview.dev/#t=…")
                                    .font(.system(size: 15, design: .monospaced))
                                    .foregroundStyle(palette.textSecondary)
                                    .lineLimit(1)
                                    .padding(.horizontal, 14)
                                    .allowsHitTesting(false)
                            }
                        }
                        .background(palette.background,
                                    in: RoundedRectangle(cornerRadius: QuotaTheme.chipRadius, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: QuotaTheme.chipRadius, style: .continuous)
                                .stroke(palette.textSecondary.opacity(0.25), lineWidth: 1)
                        )

                    if let error {
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundStyle(QuotaTheme.critical)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    HStack(spacing: 12) {
                        Button { pair(input) } label: {
                            Text("Connect")
                                .font(.system(size: 16, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Color(hex: 0x102016))
                        .background(QuotaTheme.ok,
                                    in: RoundedRectangle(cornerRadius: QuotaTheme.chipRadius, style: .continuous))
                        .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .opacity(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.5 : 1)

                        Button { showsScanner = true } label: {
                            Label("Scan", systemImage: "qrcode.viewfinder")
                                .font(.system(size: 16, weight: .semibold))
                                .padding(.vertical, 12)
                                .padding(.horizontal, 16)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(palette.textPrimary)
                        .background(palette.background,
                                    in: RoundedRectangle(cornerRadius: QuotaTheme.chipRadius, style: .continuous))
                    }

                    Button {
                        if let clip = UIPasteboard.general.string { input = clip }
                    } label: {
                        Label("Paste from clipboard", systemImage: "doc.on.clipboard")
                            .font(.system(size: 14))
                            .foregroundStyle(palette.textSecondary)
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
                .background(palette.surface,
                            in: RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))

                Button {
                    model.demoData = true
                } label: {
                    Label("Try sample data", systemImage: "sparkles")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(palette.textPrimary)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(palette.surface, in: RoundedRectangle(cornerRadius: 16))
                }
                .buttonStyle(.plain)

                Link("Set up the free collector ↗", destination: URL(string: "https://quota.sceneview.dev/setup.html")!)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(palette.textPrimary)
                    .frame(minHeight: 44)

                Text("Your pairing link is private. Your data stays with your collector. No analytics or tracking.")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(24)
        }
        .scrollIndicators(.hidden)
        .sheet(isPresented: $showsScanner) {
            QRScannerSheet { scanned in
                showsScanner = false
                pair(scanned)
            }
        }
    }

    private func pair(_ raw: String) {
        error = model.pair(with: raw)
        if error == nil { focused = false }
    }
}
