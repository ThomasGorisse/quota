import SwiftUI

@main
struct QuotaApp: App {
    @StateObject private var model = StatusViewModel()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // The large title keeps the rounded face the hand-drawn "Quota" title had. It is built at
        // the default size and scaled by `UIFontMetrics`, so it follows Dynamic Type changes.
        let base = UIFont.preferredFont(forTextStyle: .largeTitle, compatibleWith: UITraitCollection(preferredContentSizeCategory: .large))
        if let rounded = base.fontDescriptor.withDesign(.rounded)?.withSymbolicTraits(.traitBold) {
            let font = UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: UIFont(descriptor: rounded, size: 0))
            UINavigationBar.appearance().largeTitleTextAttributes = [.font: font]
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
        }
        .backgroundTask(.appRefresh(BackgroundRefresh.identifier)) {
            await BackgroundRefresh.run()
            BackgroundRefresh.schedule()
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                model.start()
            case .background:
                model.stop()
                BackgroundRefresh.schedule()
            default:
                break
            }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: StatusViewModel
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Palette.of(scheme).background.ignoresSafeArea()
            if LaunchOptions.screen == "settings" {
                SettingsView()
            } else if LaunchOptions.screen == "widgets" {
                WidgetPreviewView(snapshot: model.snapshot ?? DemoData.snapshot(),
                                  only: LaunchOptions.widgetOnly)
            } else if model.isPaired || model.demoData {
                HomeView().transition(.opacity)
            } else {
                PairingView().transition(.opacity)
            }
        }
        .quotaPalette(scheme)
        // System controls take a product colour rather than the iOS blue.
        .tint(QuotaTheme.codex)
        // Pairing hands over to Home with a short cross-fade, the way Android fades through.
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: model.isPaired || model.demoData)
    }
}
