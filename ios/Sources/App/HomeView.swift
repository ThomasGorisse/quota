import SwiftUI

/// The main screen: how much is left, how long until it comes back, and how old the number is.
struct HomeView: View {
    @EnvironmentObject private var model: StatusViewModel
    @Environment(\.palette) private var palette
    @State private var showsSettings = false

    var body: some View {
        GeometryReader { geometry in
            content(width: geometry.size.width - 32)
        }
    }

    private func content(width: CGFloat) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                title
                header
                if let snapshot = model.snapshot {
                    if snapshot.orderedProviders().isEmpty {
                        notice("Your collector is connected. Provider limits will appear after its next update.", tone: QuotaTheme.stale)
                    }
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 340), spacing: 20)], alignment: .leading, spacing: 20) {
                    ForEach(snapshot.orderedProviders(), id: \.name) { entry in
                        ProviderCard(
                            name: entry.name,
                            provider: entry.provider,
                            mode: model.displayMode,
                            stale: snapshot.isStale(provider: entry.name, now: model.now),
                            now: model.now,
                            availableWidth: width
                        )
                    }
                    }
                    if let message = model.errorMessage {
                        notice(message, tone: QuotaTheme.warn)
                    }
                } else if let message = model.errorMessage {
                    notice(message, tone: QuotaTheme.critical)
                } else {
                    waiting
                }
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .refreshable { await model.refresh() }
        .sheet(isPresented: $showsSettings) {
            SettingsView().environmentObject(model)
        }
    }

    private var title: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Quota")
                .font(.system(size: 36, weight: .bold, design: .rounded))
                .foregroundStyle(palette.textPrimary)
            Text("Your AI, at a glance.")
                .font(.body)
                .foregroundStyle(palette.textSecondary)
        }
        .padding(.top, 12)
    }

    private var header: some View {
        HStack(spacing: 12) {
            SeverityPill(severity: model.snapshot?.severity(now: model.now) ?? .stale)
            Text(model.snapshot.map { QuotaFormat.updatedAgo($0.age(now: model.now)) } ?? "never updated")
                .font(.system(size: 15))
                .monospacedDigit()
                .foregroundStyle(palette.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 0)
            if model.snapshot?.isDemo == true {
                // "Demo", like the web and Android chips: one word, one case, everywhere.
                Text("Demo")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(palette.textSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(palette.textSecondary.opacity(0.15),
                                in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            Button {
                showsSettings = true
            } label: {
                Image(systemName: "gearshape")
                    .font(.system(size: 20, weight: .medium))
                    .frame(width: 44, height: 44)
                    .foregroundStyle(palette.textSecondary)
            }
            .accessibilityLabel("Settings")
        }
        .padding(.top, 4)
    }

    private var waiting: some View {
        VStack(spacing: 12) {
            ProgressView().tint(palette.textSecondary)
            Text("Waiting for the first snapshot…")
                .font(.system(size: 15))
                .foregroundStyle(palette.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 60)
    }

    private func notice(_ message: String, tone: Color) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(tone)
            VStack(alignment: .leading, spacing: 12) {
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(palette.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Button("Try again") { Task { await model.refresh() } }
                .disabled(model.isRefreshing)
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.opacity(0.12),
                    in: RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))
    }
}
