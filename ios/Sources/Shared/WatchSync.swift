import Foundation
import WatchConnectivity
import WidgetKit

/// App Groups share data with extensions on one device. WatchConnectivity carries the
/// pairing and last reading to the user's paired watch, including while it is unreachable.
final class WatchSync: NSObject, WCSessionDelegate {
    static let shared = WatchSync()
    static let didUpdate = Notification.Name("QuotaWatchDidUpdate")

    func start() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func send() {
        #if os(iOS)
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled else { return }
        let store = SharedStore.shared
        var context: [String: Any] = [
            "host": store.host ?? "", "token": TokenStore.token() ?? "",
            "demo": store.demoData, "display": store.displayMode.rawValue
        ]
        if let snapshot = store.snapshot, let data = try? JSONEncoder().encode(snapshot) {
            context["snapshot"] = data
        }
        // The latest context replaces older state, including revocation on unpair.
        try? session.updateApplicationContext(context)
        #endif
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if activationState == .activated {
            #if os(watchOS)
            receive(session.receivedApplicationContext)
            #else
            send()
            #endif
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        receive(applicationContext)
    }

    private func receive(_ context: [String: Any]) {
        #if os(watchOS)
        guard let host = context["host"] as? String, let token = context["token"] as? String,
              let demo = context["demo"] as? Bool else { return }
        DispatchQueue.main.async {
            let store = SharedStore.shared
            if host.isEmpty || token.isEmpty {
                TokenStore.clear()
                store.clearPairing()
            } else {
                // Only a URL accepted by the same HTTPS parser as phone pairing is trusted.
                guard let url = URL(string: host), url.scheme == "https", url.host != nil else { return }
                guard TokenStore.save(token) else { return }
                store.host = host
            }
            store.demoData = demo
            store.displayMode = DisplayMode.ofWire(context["display"] as? String)
            store.snapshot = (context["snapshot"] as? Data).flatMap { try? JSONDecoder().decode(Snapshot.self, from: $0) }
            WidgetCenter.shared.reloadAllTimelines()
            NotificationCenter.default.post(name: Self.didUpdate, object: nil)
        }
        #endif
    }

    #if os(iOS)
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { session.activate() }
    func sessionWatchStateDidChange(_ session: WCSession) { send() }
    #endif
}
