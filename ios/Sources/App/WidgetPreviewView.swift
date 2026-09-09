import SwiftUI

/// Renders the widget views at their real point sizes inside the app.
///
/// Dragging a widget onto the home screen is not scriptable, and these are the same view types
/// the extension builds its entries from — so a screenshot taken here is a screenshot of the
/// widget, not of a look-alike.
struct WidgetPreviewView: View {
    let snapshot: Snapshot
    /// nil shows every rendition; "medium", "small" or "lock" shows just that one.
    var only: String?
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme

    /// Point sizes on a 6.9-inch iPhone.
    private let small = CGSize(width: 170, height: 170)
    private let medium = CGSize(width: 364, height: 170)
    private let circular = CGSize(width: 72, height: 72)
    private let rectangular = CGSize(width: 172, height: 76)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    if shows("medium") {
                        section("Home screen — medium") {
                            chrome(medium) { QuotaMediumView(snapshot: snapshot) }
                        }
                    }
                    if shows("small") {
                        section("Home screen — small") {
                            chrome(small) { QuotaSmallView(snapshot: snapshot) }
                        }
                    }
                    if shows("lock") {
                        section("Lock screen — circular and rectangular") {
                            HStack(spacing: 16) {
                                lockChrome(circular) { QuotaCircularView(snapshot: snapshot) }
                                lockChrome(rectangular) { QuotaRectangularView(snapshot: snapshot) }
                            }
                        }
                    }
                }
                .padding(20)
                .frame(maxHeight: .infinity, alignment: only == nil ? .top : .center)
            }
            .background(Palette.of(scheme).background.ignoresSafeArea())
            .quotaPalette(scheme)
            .navigationTitle("Widget preview")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
    }

    private func shows(_ name: String) -> Bool { only == nil || only == name }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: 10) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Palette.of(scheme).textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            content()
        }
    }

    /// The home-screen container: the same rounded, background-filled box WidgetKit draws.
    private func chrome<Content: View>(_ size: CGSize, @ViewBuilder content: () -> Content) -> some View {
        content()
            .environment(\.palette, Palette.of(scheme))
            .frame(width: size.width, height: size.height)
            .background(Palette.of(scheme).background)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Palette.of(scheme).textSecondary.opacity(0.15), lineWidth: 1)
            )
            .frame(maxWidth: .infinity)
    }

    /// The lock-screen container. On the real lock screen the system renders these vibrant,
    /// so this preview shows the layout rather than the exact tint.
    private func lockChrome<Content: View>(_ size: CGSize, @ViewBuilder content: () -> Content) -> some View {
        content()
            .foregroundStyle(.white)
            .frame(width: size.width, height: size.height)
            .padding(6)
            .background(Color.black.opacity(0.55), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}
