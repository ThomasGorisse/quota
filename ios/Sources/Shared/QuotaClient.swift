import Foundation

/// `GET /v1/status` against the paired host, and nothing else. There is no analytics call, no
/// crash reporter and no second host: the only request this product makes is this one.
struct QuotaClient: Sendable {

    enum Failure: LocalizedError, Equatable {
        case notPaired
        case unauthorized
        case noDataYet
        case http(Int)
        case offline
        case malformed

        var errorDescription: String? {
            switch self {
            case .notPaired: return "Not paired yet."
            case .unauthorized: return "The host rejected this token. Pair again."
            case .noDataYet: return "Paired, but the collector has not sent anything yet."
            case .http(let code): return "The host answered \(code)."
            case .offline: return "Can't reach the host."
            case .malformed: return "The host answered something this version can't read."
            }
        }
    }

    var session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 15
        config.waitsForConnectivity = false
        config.urlCache = nil
        return URLSession(configuration: config)
    }()

    /// Fetches the current status. Demo mode short-circuits the network entirely, which is
    /// what makes every screen here reviewable without the backend.
    func status(host: String?, token: String?, demo: Bool = false) async throws -> Snapshot {
        if demo { return DemoData.snapshot() }
        guard let host, let token, !host.isEmpty, !token.isEmpty else { throw Failure.notPaired }
        guard let url = URL(string: host.hasSuffix("/") ? host + "v1/status" : host + "/v1/status") else {
            throw Failure.notPaired
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.cachePolicy = .reloadIgnoringLocalCacheData

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw Failure.offline
        }

        guard let http = response as? HTTPURLResponse else { throw Failure.malformed }
        switch http.statusCode {
        case 200: break
        case 401, 403: throw Failure.unauthorized
        case 404: throw Failure.noDataYet
        default: throw Failure.http(http.statusCode)
        }

        guard let status = try? JSONDecoder().decode(StatusResponse.self, from: data) else {
            throw Failure.malformed
        }
        return Snapshot(status: status, receivedAt: Date(), isDemo: false)
    }

    /// `PUT /v1/settings {"display": …}` — the mode is a product-wide setting, so changing it
    /// on the phone changes it on the web page and the e-ink screen too. The worker echoes the
    /// whole settings object back; only the display field is read here.
    @discardableResult
    func setDisplay(_ mode: DisplayMode, host: String?, token: String?) async throws -> DisplayMode {
        guard let host, let token, !host.isEmpty, !token.isEmpty else { throw Failure.notPaired }
        guard let url = URL(string: host.hasSuffix("/") ? host + "v1/settings" : host + "/v1/settings") else {
            throw Failure.notPaired
        }
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["display": mode.rawValue])

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw Failure.offline
        }
        guard let http = response as? HTTPURLResponse else { throw Failure.malformed }
        switch http.statusCode {
        case 200: break
        case 401, 403: throw Failure.unauthorized
        default: throw Failure.http(http.statusCode)
        }
        let echoed = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        return DisplayMode.ofWire(echoed?["display"] as? String)
    }

    /// The one call every surface makes: read the shared pairing, fetch, and write the result
    /// back to the App Group so the next surface to wake up already has something to draw.
    func refreshShared() async throws -> Snapshot {
        let store = SharedStore.shared
        let snapshot = try await status(host: store.host, token: TokenStore.token(), demo: store.demoData)
        store.snapshot = snapshot
        return snapshot
    }

    /// What a widget or the watch shows when the fetch fails: the last snapshot, which the
    /// stale rule will grey out on its own once it is more than ten minutes old.
    func refreshSharedOrCached() async -> Snapshot? {
        (try? await refreshShared()) ?? SharedStore.shared.snapshot
    }
}
