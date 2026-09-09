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
            VStack(spacing: 16) {
                header
                if let snapshot = model.snapshot {
                    ForEach(snapshot.orderedProviders(), id: \.name) { entry in
                        ProviderCard(
                            name: entry.name,
                            provider: entry.provider,
                            mode: model.displayMode,
                            stale: snapshot.isStale(now: model.now),
                            now: model.now,
                            availableWidth: width
                        )
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
                Text("DEMO")
                    .font(.system(size: 11, weight: .bold))
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
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(palette.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.opacity(0.12),
                    in: RoundedRectangle(cornerRadius: QuotaTheme.cardRadius, style: .continuous))
    }
}
