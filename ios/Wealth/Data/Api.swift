import Foundation

/// The dashboard's own HTTP surface, nothing else.
///
///     POST /api/v1/pair                     a six-digit code for the token
///     GET  /api/v1/tools/<name>?args        every tool the assistant has
///
/// One client, one place that knows about the bearer token, and errors
/// that carry the server's own sentence — the dashboard says why in
/// words meant for a person, and repeating them beats inventing worse
/// ones here.
struct Api {
    let baseURL: String
    let token: String?
    var session: URLSession = Api.session

    struct Failure: LocalizedError, Equatable {
        let status: Int
        let message: String
        var errorDescription: String? { message }
    }

    static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 30
        config.urlCache = nil
        return URLSession(configuration: config)
    }()

    static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    /// What a person types: "tower:8000", "https://…/", with or without
    /// a scheme. A bare IP or localhost is assumed to be plain http on the
    /// home network; a name is assumed to be https. A path prefix stays.
    static func normalise(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        if s.isEmpty { return s }
        if !s.hasPrefix("http://") && !s.hasPrefix("https://") {
            let local = s.hasPrefix("localhost")
                || s.range(of: #"^\d+\.\d+\.\d+\.\d+"#, options: .regularExpression) != nil
            s = (local ? "http://" : "https://") + s
        }
        return s
    }

    /// Trade a pairing code for the token. No token yet, by definition.
    static func pair(baseURL: String, code: String, session: URLSession = Api.session) async throws -> Paired {
        guard let url = URL(string: normalise(baseURL) + "/api/v1/pair") else {
            throw Failure(status: 0, message: "That address is not a URL.")
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.httpBody = try JSONEncoder().encode(["code": code.trimmingCharacters(in: .whitespaces)])
        let (data, response) = try await session.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let reply = try? decoder.decode(Paired.self, from: data)
        guard (200..<300).contains(status), let reply, reply.token?.isEmpty == false else {
            throw Failure(status: status, message: reply?.error ?? "The dashboard refused that code.")
        }
        return reply
    }

    /// One tool, called with GET, its `result` unwrapped from the envelope.
    func tool<T: Decodable>(_ name: String, _ args: [String: String] = [:], as type: T.Type) async throws -> T {
        guard var parts = URLComponents(string: baseURL + "/api/v1/tools/" + name) else {
            throw Failure(status: 0, message: "That address is not a URL.")
        }
        if !args.isEmpty {
            parts.queryItems = args.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = parts.url else { throw Failure(status: 0, message: "That address is not a URL.") }
        var req = URLRequest(url: url)
        req.setValue("Bearer \(token ?? "")", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Accept")

        return try await send(req, as: type)
    }

    /// The envelope unwrapped, and the dashboard's own sentence when it
    /// refuses.
    private func send<T: Decodable>(_ req: URLRequest, as type: T.Type) async throws -> T {
        let (data, response) = try await session.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let why = (try? Api.decoder.decode(ToolReply<Empty>.self, from: data))?.error
            throw Failure(status: status, message: why ?? Api.sentence(for: status))
        }
        let reply = try Api.decoder.decode(ToolReply<T>.self, from: data)
        guard let result = reply.result else {
            throw Failure(status: status, message: reply.error ?? "The dashboard sent nothing.")
        }
        return result
    }

    /// Everything a home screen shows, in one round trip.
    func snapshot(days: Int = 30) async throws -> Snapshot {
        try await tool("snapshot", ["days": String(days)], as: Snapshot.self)
    }

    func transactions(accountId: Int? = nil, query: String? = nil, limit: Int = 100) async throws -> TransactionPage {
        var args = ["limit": String(limit)]
        if let accountId { args["account_id"] = String(accountId) }
        if let q = query?.trimmingCharacters(in: .whitespaces), !q.isEmpty { args["q"] = q }
        return try await tool("transactions", args, as: TransactionPage.self)
    }

    /// Income and spending per month, the dashboard's own arithmetic.
    func cashflow(months: Int = 13) async throws -> Cashflow {
        try await tool("cashflow", ["months": String(months)], as: Cashflow.self)
    }

    /// Quote every holding again and refetch the rates — no bank is
    /// touched, which is why this is the one a phone may press often.
    func refreshMarket() async throws -> Market {
        try await post("refresh_market", as: Market.self)
    }

    /// One tool, called with POST and a JSON object of arguments — what a
    /// write wants.
    func post<T: Decodable>(_ name: String, _ args: [String: String] = [:], as type: T.Type) async throws -> T {
        guard let url = URL(string: baseURL + "/api/v1/tools/" + name) else {
            throw Failure(status: 0, message: "That address is not a URL.")
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("Bearer \(token ?? "")", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(args)
        return try await send(req, as: type)
    }

    /// The sentence for a status the dashboard gave no words for.
    static func sentence(for status: Int) -> String {
        switch status {
        case 401: return "This device is no longer paired — pair it again."
        case 404: return "This dashboard does not know that address."
        case 503: return "The dashboard has no user yet."
        default: return "The dashboard answered \(status)."
        }
    }

    private struct Empty: Decodable {}
}
