import SwiftUI

@main
struct QuotaApp: App {
    @StateObject private var model = StatusViewModel()
    @Environment(\.scenePhase) private var scenePhase

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

    var body: some View {
        ZStack {
            Palette.of(scheme).background.ignoresSafeArea()
            if LaunchOptions.screen == "settings" {
                SettingsView()
            } else if LaunchOptions.screen == "widgets" {
                WidgetPreviewView(snapshot: model.snapshot ?? DemoData.snapshot(),
                                  only: LaunchOptions.widgetOnly)
            } else if model.isPaired || model.demoData {
                HomeView()
            } else {
                PairingView()
            }
        }
        .quotaPalette(scheme)
        // System controls take a product colour rather than the iOS blue.
        .tint(QuotaTheme.codex)
        .animation(nil, value: model.isPaired)
    }
}
