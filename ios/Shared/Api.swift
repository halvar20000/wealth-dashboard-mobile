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
        let reply: ToolReply<T>
        do {
            reply = try Api.decoder.decode(ToolReply<T>.self, from: data)
        } catch let bad as DecodingError {
            // The system's own sentence names no tool and no field, which
            // leaves nobody able to say what to fix.
            throw Failure(status: status, message: Api.sentence(for: bad, tool: req.url?.lastPathComponent))
        }
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

    // MARK: The portfolio

    /// Every security held, worked out from the trades.
    func holdings() async throws -> Holdings {
        try await tool("holdings", as: Holdings.self)
    }

    /// The net worth on a set of days — the line on the chart.
    func history(period: String = "1y") async throws -> History {
        try await tool("net_worth_history", ["period": period], as: History.self)
    }

    /// Where the money sits: by asset class, by region, by bucket.
    func allocation() async throws -> Allocation {
        try await tool("allocation", as: Allocation.self)
    }

    /// The return of the whole portfolio and of each holding.
    func returns() async throws -> Returns {
        try await tool("performance", as: Returns.self)
    }

    // MARK: The triage

    /// The queue of rows with no category, biggest first.
    func uncategorised(limit: Int = 60) async throws -> Queue {
        try await tool("uncategorised", ["limit": String(limit)], as: Queue.self)
    }

    /// Every category, for the sheet of choices. The tool answers with a
    /// bare list.
    func categories() async throws -> [Category] {
        try await tool("categories", as: [Category].self)
    }

    /// The spending nobody has claimed yet (dashboard ≥ 0.72.4).
    func unowned(limit: Int = 60) async throws -> Queue {
        try await tool("unowned_spending", ["limit": String(limit)], as: Queue.self)
    }

    /// The household, for "whose spending is this".
    func people() async throws -> [Person] {
        try await tool("people", as: PeopleList.self).people ?? []
    }

    /// One decision. The dashboard files the row and, where the verdict
    /// says so, remembers it as a rule for the next one like it.
    func deliver(_ verdict: Verdict) async throws {
        if let category = verdict.category {
            var args: [String: Any] = ["txn_id": verdict.txnId, "category": category,
                                       "remember": verdict.remember]
            if let pattern = verdict.pattern?.trimmingCharacters(in: .whitespaces), !pattern.isEmpty {
                args["pattern"] = pattern
            }
            _ = try await post("set_category", args, as: Ignored.self)
        }
        if let owner = verdict.owner {
            // The same switch as the category: "this row only" must mean
            // this row only, whichever queue the thumb was in.
            _ = try await post("set_owner", ["txn_id": verdict.txnId, "owner": owner,
                                             "remember": verdict.remember], as: Ignored.self)
        }
    }

    // MARK: A statement into an account

    /// A file on its way to the dashboard: its name, its type, its bytes.
    struct Upload {
        var name: String
        var mime: String?
        var data: Data
    }

    /// A statement into an account, as the import page takes it: the
    /// field name is `file`, several at once are allowed, and the
    /// dashboard answers with the report it would have shown on screen.
    func importFiles(accountId: Int, files: [Upload]) async throws -> ImportReply {
        guard !files.isEmpty else { throw Failure(status: 0, message: "Nothing to send.") }
        guard let url = URL(string: baseURL + "/api/v1/accounts/\(accountId)/import") else {
            throw Failure(status: 0, message: "That address is not a URL.")
        }
        let boundary = "wealth-" + UUID().uuidString
        var body = Data()
        for file in files {
            let name = file.name.replacingOccurrences(of: "\"", with: "'")
            body.append(Data("--\(boundary)\r\n".utf8))
            body.append(Data("Content-Disposition: form-data; name=\"file\"; filename=\"\(name)\"\r\n".utf8))
            body.append(Data("Content-Type: \(file.mime ?? "application/octet-stream")\r\n\r\n".utf8))
            body.append(file.data)
            body.append(Data("\r\n".utf8))
        }
        body.append(Data("--\(boundary)--\r\n".utf8))

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 120
        req.setValue("Bearer \(token ?? "")", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.httpBody = body

        let (data, response) = try await session.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let reply = try? Api.decoder.decode(ImportReply.self, from: data)
        guard (200..<300).contains(status), let reply, reply.ok != false else {
            throw Failure(status: status, message: reply?.error ?? Api.sentence(for: status))
        }
        return reply
    }

    /// An answer whose content does not matter, only that it came.
    struct Ignored: Decodable {
        init(from decoder: Decoder) throws {}
    }

    /// One tool, called with POST and a JSON object of arguments — what a
    /// write wants.
    ///
    /// Numbers and booleans go as JSON numbers and booleans, never as
    /// text: the dashboard reads `remember: "false"` as a non-empty
    /// string, which Python counts as true.
    func post<T: Decodable>(_ name: String, _ args: [String: Any] = [:], as type: T.Type) async throws -> T {
        guard let url = URL(string: baseURL + "/api/v1/tools/" + name) else {
            throw Failure(status: 0, message: "That address is not a URL.")
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("Bearer \(token ?? "")", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: args, options: [.sortedKeys])
        return try await send(req, as: type)
    }

    /// Which answer could not be read, and where in it.
    static func sentence(for bad: DecodingError, tool: String?) -> String {
        let path: [CodingKey]
        switch bad {
        case .typeMismatch(_, let c), .valueNotFound(_, let c), .keyNotFound(_, let c), .dataCorrupted(let c):
            path = c.codingPath
        @unknown default:
            path = []
        }
        let field = path.map { $0.intValue.map(String.init) ?? $0.stringValue }.joined(separator: ".")
        let what = tool ?? "that call"
        return field.isEmpty
            ? "The dashboard's answer to \(what) is not one this app can read."
            : "The dashboard's answer to \(what) is not one this app can read (\(field))."
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
