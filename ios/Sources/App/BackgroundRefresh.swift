import BackgroundTasks
import Foundation
import WidgetKit

/// The `BGAppRefreshTask` that keeps the cached snapshot warm, so opening the app or glancing
/// at a widget does not start with a ten-minute-old number.
enum BackgroundRefresh {
    static let identifier = "com.gorisse.thomas.quota.refresh"
    /// The collector runs every three minutes and the widget timeline every fifteen; asking
    /// the system for fifteen too keeps the two in step without begging for budget.
    static let interval: TimeInterval = 15 * 60

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Simulators and a device with Background App Refresh switched off both land here.
            // Nothing to recover: the foreground refresh and the widget timeline still run.
        }
    }

    /// Runs the fetch and hands the result to the App Group, then reloads the widgets.
    static func run() async {
        _ = await QuotaClient().refreshSharedOrCached()
        WidgetCenter.shared.reloadAllTimelines()
    }
}
