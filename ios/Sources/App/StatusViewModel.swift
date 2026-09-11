import Foundation
import SwiftUI
import WidgetKit

/// The one source of truth on the phone. It owns the shared store, so anything it writes is
/// what the widget and the watch will read the next time they wake up.
@MainActor
final class StatusViewModel: ObservableObject {

    @Published private(set) var snapshot: Snapshot?
    @Published private(set) var errorMessage: String?
    @Published private(set) var isRefreshing = false
    @Published private(set) var isPaired: Bool
    @Published var demoData: Bool {
        didSet {
            guard demoData != oldValue else { return }
            store.demoData = demoData
            snapshot = nil
            store.snapshot = nil
            errorMessage = nil
            reloadWidgets()
            Task { await refresh() }
        }
    }
    /// The mode the whole product renders in. The status payload is authoritative; changing it
    /// here is optimistic, then `PUT /v1/settings` makes it stick for every other client.
    @Published private(set) var displayMode: DisplayMode
    @Published private(set) var displayError: String?
    /// Ticks once a second so "updated 47 s ago" and the countdowns keep counting while the
    /// screen is open. The rings do not animate on this: their value has not changed.
    @Published private(set) var now = Date()

    private let store = SharedStore.shared
    private let client = QuotaClient()
    private var ticker: Task<Void, Never>?

    init() {
        // Debug launch arguments are applied before anything is read, so the first frame is
        // already in the state a screenshot wants.
        if LaunchOptions.demo { store.demoData = true }
        if let display = LaunchOptions.display { store.displayMode = display }

        demoData = store.demoData
        isPaired = TokenStore.token() != nil && store.host != nil
        snapshot = store.snapshot
        displayMode = store.snapshot?.display ?? store.displayMode
    }

    var host: String? { store.host }
    var tokenStorage: String { TokenStore.storageDescription }
    var isSharedContainer: Bool { store.isShared }

    // MARK: - Lifecycle

    func start() {
        WatchSync.shared.start()
        guard ticker == nil else { return }
        ticker = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                await MainActor.run { self?.now = Date() }
            }
        }
        Task { await refresh() }
    }

    func stop() {
        ticker?.cancel()
        ticker = nil
    }

    // MARK: - Pairing

    func pair(with raw: String) -> String? {
        do {
            let pairing = try Pairing.parse(raw)
            guard TokenStore.save(pairing.token) else {
                return "Your pairing link could not be saved securely. Unlock your device and try again."
            }
            snapshot = nil
            store.snapshot = nil
            store.host = pairing.host
            isPaired = true
            errorMessage = nil
            Task { await refresh() }
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    func unpair() {
        TokenStore.clear()
        store.clearPairing()
        isPaired = false
        snapshot = nil
        errorMessage = nil
        reloadWidgets()
    }

    // MARK: - Display mode

    func setDisplayMode(_ mode: DisplayMode) async {
        guard mode != displayMode else { return }
        displayMode = mode
        store.displayMode = mode
        displayError = nil

        // Show it immediately, whether or not the host answers.
        if var current = snapshot {
            current.status.display = mode.rawValue
            snapshot = current
            store.snapshot = current
        }
        reloadWidgets()

        guard !demoData, isPaired else { return }
        do {
            let confirmed = try await client.setDisplay(mode, host: store.host, token: TokenStore.token())
            if confirmed != mode {
                displayMode = confirmed
                store.displayMode = confirmed
            }
            await refresh()
        } catch {
            displayError = error.localizedDescription
        }
    }

    // MARK: - Refresh

    func refresh() async {
        guard !isRefreshing else { return }
        guard demoData || isPaired else { return }
        let requestedHost = store.host
        let requestedToken = TokenStore.token()
        let requestedDemo = demoData
        isRefreshing = true
        defer {
            isRefreshing = false
            if requestedHost != store.host || requestedToken != TokenStore.token() || requestedDemo != demoData {
                Task { await refresh() }
            }
        }
        do {
            let fresh = try await client.status(host: requestedHost, token: requestedToken, demo: requestedDemo)
            guard requestedHost == store.host, requestedToken == TokenStore.token(), requestedDemo == demoData else { return }
            store.snapshot = fresh
            snapshot = fresh
            displayMode = fresh.display
            errorMessage = nil
            now = Date()
            reloadWidgets()
        } catch {
            guard requestedHost == store.host, requestedToken == TokenStore.token(), requestedDemo == demoData else { return }
            // The last snapshot stays on screen; the stale rule greys it out on its own.
            errorMessage = error.localizedDescription
        }
    }

    private func reloadWidgets() {
        WatchSync.shared.send()
        WidgetCenter.shared.reloadAllTimelines()
    }
}
