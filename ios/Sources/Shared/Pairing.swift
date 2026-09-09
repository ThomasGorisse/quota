import Foundation

/// The pairing URL the collector prints: `https://<host>/#t=<token>`.
///
/// The token lives in the fragment, which is why it never reaches a server log. Parsing is
/// forgiving about what a human pastes — a bare token, a URL with the fragment, extra
/// whitespace — and strict about the result: a token is 16 characters or more of URL-safe
/// text, and the host must be https.
struct Pairing: Equatable {
    var host: String
    var token: String

    static let defaultHost = "https://quota.sceneview.dev"

    enum ParseError: LocalizedError {
        case empty
        case noToken
        case insecureHost

        var errorDescription: String? {
            switch self {
            case .empty: return "Paste the pairing URL the collector printed."
            case .noToken: return "That link carries no token. It should look like https://host/#t=…"
            case .insecureHost: return "The pairing URL has to be https."
            }
        }
    }

    static func parse(_ raw: String, defaultHost: String = Pairing.defaultHost) throws -> Pairing {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw ParseError.empty }

        guard let components = URLComponents(string: trimmed), components.scheme != nil else {
            // Not a URL: accept a bare token pasted on its own against the default host.
            guard isPlausibleToken(trimmed) else { throw ParseError.noToken }
            return Pairing(host: defaultHost, token: trimmed)
        }

        guard let scheme = components.scheme?.lowercased(), scheme == "https" else {
            throw ParseError.insecureHost
        }
        guard let hostName = components.host, !hostName.isEmpty else { throw ParseError.insecureHost }

        // `t` may be in the fragment (what the collector prints) or the query (hand-made links).
        let token = value(named: "t", in: components.fragment)
            ?? components.queryItems?.first(where: { $0.name == "t" })?.value
        guard let token, isPlausibleToken(token) else { throw ParseError.noToken }

        var host = "https://" + hostName
        if let port = components.port { host += ":\(port)" }
        return Pairing(host: host, token: token)
    }

    private static func value(named name: String, in fragment: String?) -> String? {
        guard let fragment, !fragment.isEmpty else { return nil }
        for pair in fragment.split(separator: "&") {
            let parts = pair.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard parts.count == 2, parts[0] == name else { continue }
            return String(parts[1]).removingPercentEncoding ?? String(parts[1])
        }
        return nil
    }

    private static func isPlausibleToken(_ value: String) -> Bool {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_"))
        return value.count >= 16 && value.unicodeScalars.allSatisfy { allowed.contains($0) }
    }
}
