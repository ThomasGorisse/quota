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
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Quota")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(palette.textPrimary)
                    Text("Claude, Codex and Gemini limits, on every screen you look at.")
                        .font(.system(size: 16))
                        .foregroundStyle(palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                VStack(alignment: .leading, spacing: 12) {
                    Text("Pairing URL")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(palette.textPrimary)
                    Text("The collector prints it on first run and writes it to ~/.quota/pairing.txt. It carries the device token, so treat it as a credential.")
                        .font(.system(size: 13))
                        .foregroundStyle(palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                    // The placeholder is drawn rather than passed to TextField so it takes the
                    // secondary text token instead of the system tint.
                    TextField("", text: $input, axis: .vertical)
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
                            Text("Pair")
                                .font(.system(size: 16, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Color.white)
                        .background(QuotaTheme.codex,
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
                    Label("Look at it with demo data", systemImage: "eye")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(palette.textSecondary)
                }
                .buttonStyle(.plain)

                Text("Nothing is sent anywhere but the host in that URL. No analytics, no third party.")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(20)
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
