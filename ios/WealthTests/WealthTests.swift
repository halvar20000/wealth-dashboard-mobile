import XCTest
@testable import Wealth

/// The contract, checked where it can be without a dashboard: the JSON
/// shapes, the address rules, the money rule and the error sentences.
final class WealthTests: XCTestCase {

    // Contract rule 5: unknown fields ignored, missing ones survivable.
    func testSnapshotDecodesWithUnknownAndMissingFields() throws {
        let json = """
        {"ok": true, "result": {
          "base_currency": "CHF",
          "net_worth": {"net_worth": 123456.78, "cash": 2000, "prices_as_of": "2026-09-23",
                        "by_class": [{"name": "Equity", "value": 100000}], "brand_new": 1},
          "performance": {"ytd": {"twr": 0.051, "pnl": 4321.5}, "1y": {"twr": -0.02}},
          "accounts": [{"id": 7, "name": "Giro", "balance_base": 1500.5, "as_of": "2026-09-22"}],
          "waiting": {"uncategorised": 12, "unassigned_spending": 3},
          "something_the_client_never_heard_of": {"x": [1, 2, 3]}
        }}
        """
        let reply = try Api.decoder.decode(ToolReply<Snapshot>.self, from: Data(json.utf8))
        let s = try XCTUnwrap(reply.result)
        XCTAssertEqual(s.currency, "CHF")
        XCTAssertEqual(s.netWorth?.total, 123456.78)
        XCTAssertEqual(s.netWorth?.pricesAsOf, "2026-09-23")
        XCTAssertEqual(s.netWorth?.byClass?.first?.name, "Equity")
        XCTAssertEqual(s.performance?["ytd"]?.twr, 0.051)
        XCTAssertEqual(s.performance?["1y"]?.twr, -0.02)
        XCTAssertEqual(s.accounts?.first?.balanceBase, 1500.5)
        XCTAssertEqual(s.accounts?.first?.asOf, "2026-09-22")
        XCTAssertEqual(s.waiting?.unassignedSpending, 3)
        XCTAssertNil(s.upcoming)
        XCTAssertNil(s.sync)
    }

    func testEmptySnapshotStillDecodes() throws {
        let s = try Api.decoder.decode(Snapshot.self, from: Data("{}".utf8))
        XCTAssertEqual(s.currency, "EUR")
        XCTAssertNil(s.netWorth?.total)
    }

    func testTransactionsDecode() throws {
        let json = """
        {"matched": 2, "returned": 1, "transactions": [
          {"id": 1, "account_id": 7, "txn_date": "2026-09-01", "description": "Rent", "amount": -950.0,
           "currency": "EUR", "security_name": null}]}
        """
        let page = try Api.decoder.decode(TransactionPage.self, from: Data(json.utf8))
        XCTAssertEqual(page.matched, 2)
        XCTAssertEqual(page.transactions?.first?.accountId, 7)
        XCTAssertEqual(page.transactions?.first?.txnDate, "2026-09-01")
    }

    // The cache is written in camelCase and read back by the same decoder
    // that reads the dashboard's snake_case.
    func testCacheRoundTrip() throws {
        var s = Snapshot()
        s.baseCurrency = "EUR"
        s.netWorth = NetWorth(netWorth: 42, pricesAsOf: "2026-09-24")
        s.performance = ["ytd": Period(twr: 0.1, pnl: 5)]
        let at = Date(timeIntervalSince1970: 1_790_000_000)
        let data = try JSONEncoder().encode(Store.Cached(at: at, snapshot: s))
        let back = try Api.decoder.decode(Store.Cached.self, from: data)
        XCTAssertEqual(back.at, at)
        XCTAssertEqual(back.snapshot.netWorth?.total, 42)
        XCTAssertEqual(back.snapshot.netWorth?.pricesAsOf, "2026-09-24")
        XCTAssertEqual(back.snapshot.performance?["ytd"]?.pnl, 5)
    }

    func testStoreCachesToDisk() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = Store(service: "test." + UUID().uuidString, directory: dir)
        XCTAssertNil(store.cached())
        var s = Snapshot()
        s.baseCurrency = "USD"
        store.cache(s)
        XCTAssertEqual(store.cached()?.snapshot.currency, "USD")
        store.forget()
        XCTAssertNil(store.cached())
    }

    // CONTRACT.md → Pairing: both schemes, a path prefix kept.
    func testNormalise() {
        XCTAssertEqual(Api.normalise("  dashboard.example.com/ "), "https://dashboard.example.com")
        XCTAssertEqual(Api.normalise("192.168.1.20:8000"), "http://192.168.1.20:8000")
        XCTAssertEqual(Api.normalise("localhost:8000"), "http://localhost:8000")
        XCTAssertEqual(Api.normalise("http://nas.local:8000/"), "http://nas.local:8000")
        XCTAssertEqual(Api.normalise("https://example.com/wealth/"), "https://example.com/wealth")
        XCTAssertEqual(Api.normalise(""), "")
    }

    // Contract rule 3: two decimals under 1 000, none above.
    func testMoneyDecimals() {
        let us = Locale(identifier: "en_US")
        XCTAssertEqual(Fmt.money(999.5, "USD", locale: us), "$999.50")
        XCTAssertEqual(Fmt.money(1234.56, "USD", locale: us), "$1,235")
        XCTAssertEqual(Fmt.money(nil, "USD", locale: us), "—")
        XCTAssertEqual(Fmt.signedMoney(-12.5, "USD", locale: us), "−$12.50")
        XCTAssertEqual(Fmt.percent(0.0512, locale: us), "+5.12 %")
        XCTAssertEqual(Fmt.percent(-0.02, locale: us), "-2.00 %")
    }

    func testDay() {
        let us = Locale(identifier: "en_US")
        XCTAssertEqual(Fmt.day("2026-09-24", locale: us), "Sep 24, 2026")
        XCTAssertEqual(Fmt.day("2026-09-24T10:00:00Z", locale: us), "Sep 24, 2026")
        XCTAssertEqual(Fmt.day(nil, locale: us), "")
    }

    // The error sentence is the dashboard's own when it gives one.
    func testToolErrorCarriesTheDashboardsSentence() async {
        StubProtocol.reply = (401, #"{"ok": false, "error": "Token revoked."}"#)
        let api = Api(baseURL: "https://example.com", token: "t", session: StubProtocol.session)
        do {
            _ = try await api.snapshot()
            XCTFail("expected a failure")
        } catch let failure as Api.Failure {
            XCTAssertEqual(failure, Api.Failure(status: 401, message: "Token revoked."))
        } catch {
            XCTFail("unexpected \(error)")
        }
    }

    func testToolWithoutWordsGetsOurs() async {
        StubProtocol.reply = (404, "not json")
        let api = Api(baseURL: "https://example.com", token: "t", session: StubProtocol.session)
        do {
            _ = try await api.snapshot()
            XCTFail("expected a failure")
        } catch let failure as Api.Failure {
            XCTAssertEqual(failure.status, 404)
            XCTAssertEqual(failure.message, Api.sentence(for: 404))
        } catch {
            XCTFail("unexpected \(error)")
        }
    }

    func testToolSendsTokenAndArguments() async throws {
        StubProtocol.reply = (200, #"{"ok": true, "result": {"matched": 0, "returned": 0, "transactions": []}}"#)
        let api = Api(baseURL: "https://example.com/wealth", token: "secret", session: StubProtocol.session)
        _ = try await api.transactions(accountId: 7, query: " rent & co ")
        let request = try XCTUnwrap(StubProtocol.lastRequest)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer secret")
        let parts = try XCTUnwrap(URLComponents(url: try XCTUnwrap(request.url), resolvingAgainstBaseURL: false))
        XCTAssertEqual(parts.path, "/wealth/api/v1/tools/transactions")
        let items = Dictionary(uniqueKeysWithValues: (parts.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        XCTAssertEqual(items["account_id"], "7")
        XCTAssertEqual(items["q"], "rent & co")
    }

    func testPairRefusal() async {
        StubProtocol.reply = (400, #"{"ok": false, "error": "That code has expired."}"#)
        do {
            _ = try await Api.pair(baseURL: "example.com", code: "123456", session: StubProtocol.session)
            XCTFail("expected a failure")
        } catch let failure as Api.Failure {
            XCTAssertEqual(failure.message, "That code has expired.")
            XCTAssertEqual(StubProtocol.lastRequest?.url?.absoluteString, "https://example.com/api/v1/pair")
        } catch {
            XCTFail("unexpected \(error)")
        }
    }

    // Contract rule 8: an unticked class leaves the figure; with nothing
    // unticked the dashboard's own total stands, rounding and all.
    func testFigureWithoutChosenClasses() {
        var s = Snapshot()
        s.netWorth = NetWorth(netWorth: 1_000.4, byClass: [
            ClassValue(name: "Cash", value: 200), ClassValue(name: "Equity", value: 300),
            ClassValue(name: "Real estate", value: 500),
        ])
        XCTAssertEqual(s.figure(excluding: []), 1_000.4)
        XCTAssertEqual(s.figure(excluding: ["Something gone"]), 1_000.4)
        XCTAssertEqual(s.figure(excluding: ["Real estate"]), 500)
        XCTAssertEqual(s.figure(excluding: ["Real estate", "Equity"]), 200)
    }

    func testExcludedClassesSurviveAndAreForgotten() throws {
        let suite = "test." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = Store(service: suite, directory: dir, defaults: defaults)
        store.excludedClasses = ["Real estate"]
        XCTAssertEqual(Store(service: suite, directory: dir, defaults: defaults).excludedClasses, ["Real estate"])
        store.forget()
        XCTAssertEqual(store.excludedClasses, [])
        defaults.removePersistentDomain(forName: suite)
    }

    // Contract rule 6: features hide below their floor.
    func testDashboardVersion() {
        XCTAssertTrue(DashboardVersion.isAtLeast("0.73.0", "0.73.0"))
        XCTAssertTrue(DashboardVersion.isAtLeast("0.73.2", "0.73.0"))
        XCTAssertTrue(DashboardVersion.isAtLeast("1.0", "0.73.0"))
        XCTAssertFalse(DashboardVersion.isAtLeast("0.72.7", "0.73.0"))
        XCTAssertFalse(DashboardVersion.isAtLeast("0.9.9", "0.73.0"))
        XCTAssertTrue(DashboardVersion.isAtLeast("0.73.0-dev", "0.73.0"))
    }

    func testCashflowDecodes() throws {
        let json = """
        {"months": [{"month": "2026-08", "income": 5000, "spending": 3200.5, "investment": 500,
                     "net": 1799.5, "categories": {"rent": 1200}}],
         "by_category": [{"category": "rent", "label": "Rent", "colour": "#7c3aed", "total": 14400, "per_month": 1200}],
         "income_by_category": [],
         "total_investment": 6000, "average_income": 5000, "average_spending": 3200.5,
         "months_covered": 12, "base_currency": "EUR",
         "unconverted": [{"currency": "CHF", "amount": 12.5}]}
        """
        let flow = try Api.decoder.decode(Cashflow.self, from: Data(json.utf8))
        XCTAssertEqual(flow.months?.first?.month, "2026-08")
        XCTAssertEqual(flow.byCategory?.first?.perMonth, 1200)
        XCTAssertEqual(flow.byCategory?.first?.title, "Rent")
        XCTAssertEqual(flow.averageNet, 1799.5)
        XCTAssertEqual(flow.averageInvestment, 500)
        XCTAssertEqual(flow.unconverted?.first?.currency, "CHF")
    }

    // Contract rule 9: the cheap refresh is a POST to refresh_market.
    func testRefreshMarketPosts() async throws {
        StubProtocol.reply = (200, #"{"ok": true, "result": {"prices": {"priced": 12, "failed": [{"isin": "IE00B4L5Y983", "error": "no quote"}], "held": 13}, "rates": {"days": 1, "currencies": 30, "latest": "2026-09-23"}, "net_worth": {"base_currency": "EUR", "net_worth": 5}}}"#)
        let api = Api(baseURL: "https://example.com", token: "t", session: StubProtocol.session)
        let market = try await api.refreshMarket()
        XCTAssertEqual(market.prices?.priced, 12)
        XCTAssertEqual(market.netWorth?.total, 5)
        XCTAssertEqual(market.prices?.failed?.first?.isin, "IE00B4L5Y983")
        let request = try XCTUnwrap(StubProtocol.lastRequest)
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.url?.path, "/api/v1/tools/refresh_market")
    }

    // A refresh that went through is not an error because a field in its
    // report came back in a shape the phone did not expect.
    func testRefreshMarketToleratesOddShapes() async throws {
        StubProtocol.reply = (200, #"{"ok": true, "result": {"prices": {"priced": "12", "failed": 3}, "rates": {"latest": 20260923, "currencies": ["USD", "CHF"]}, "net_worth": 5}}"#)
        let api = Api(baseURL: "https://example.com", token: "t", session: StubProtocol.session)
        let market = try await api.refreshMarket()
        XCTAssertNil(market.prices?.priced)
        XCTAssertNil(market.rates?.currencies)
        XCTAssertNil(market.netWorth)
    }

    // What cannot be read says which call and which field.
    func testUnreadableAnswerNamesTheField() async throws {
        StubProtocol.reply = (200, #"{"ok": true, "result": {"months": "many"}}"#)
        let api = Api(baseURL: "https://example.com", token: "t", session: StubProtocol.session)
        do {
            _ = try await api.cashflow()
            XCTFail("expected a failure")
        } catch let failure as Api.Failure {
            XCTAssertTrue(failure.message.contains("cashflow"), failure.message)
            XCTAssertTrue(failure.message.contains("months"), failure.message)
        }
    }

    // MARK: The portfolio

    func testHoldingsDecode() throws {
        let json = #"{"base_currency": "EUR", "prices_as_of": "2026-09-23", "holdings": [{"isin": "IE00B4L5Y983", "name": "World", "quantity": 12.5, "net_invested": 1000, "price": 100.5, "price_kind": "market", "value": 1256.25, "value_base": 1256.25, "accounts": ["Depot"], "incomplete_history": false, "last_price": 99}]}"#
        let held = try Api.decoder.decode(Holdings.self, from: Data(json.utf8))
        let h = try XCTUnwrap(held.holdings?.first)
        XCTAssertEqual(h.netInvested, 1000)
        XCTAssertEqual(h.gain, 256.25, accuracy: 0.001)
        XCTAssertEqual(h.id, "IE00B4L5Y983")
    }

    // The decoder turns dictionary keys to camelCase too: `asset_class`
    // must still be found, and ISINs and "1y" must survive untouched.
    func testAllocationAndReturnsKeys() throws {
        let ring = #"{"total": 100, "cash": 10, "dimensions": {"asset_class": {"has_targets": false, "rows": [{"key": "real_estate", "value": 60, "share": 60.0, "target": null, "drift": null, "gap": null}]}, "region": {"rows": []}}}"#
        let allocation = try Api.decoder.decode(Allocation.self, from: Data(ring.utf8))
        XCTAssertEqual(allocation.byClass.first?.key, "real_estate")
        XCTAssertEqual(allocation.byClass.first?.share, 60)

        let perf = #"{"all": {"twr": 0.12, "twr_annual": 0.04, "mwr": 0.05, "days": 900, "since": "2024-01-02"}, "ytd": {"twr": 0.03}, "1y": {"twr": 0.07}, "holdings": {"IE00B4L5Y983": {"name": "World", "twr": 0.2}}}"#
        let returns = try Api.decoder.decode(Returns.self, from: Data(perf.utf8))
        XCTAssertEqual(returns.year?.twr, 0.07)
        XCTAssertEqual(returns.all?.twrAnnual, 0.04)
        XCTAssertEqual(returns.holdings?["IE00B4L5Y983"]?.twr, 0.2)
    }

    func testHistoryLineSkipsEmptyDays() throws {
        let json = #"{"period": "1y", "points": [{"date": "2026-01-01", "net_worth": 1}, {"date": "2026-01-02", "net_worth": null}, {"date": "2026-01-03", "net_worth": 3}], "first_date": "2024-05-01", "start": {"date": "2026-01-01", "net_worth": 1}}"#
        let history = try Api.decoder.decode(History.self, from: Data(json.utf8))
        XCTAssertEqual(history.line, [1, 3])
    }

    // MARK: The triage

    func testQueueAndCategoriesDecode() throws {
        let queue = #"{"remaining": 140, "transactions": [{"id": 7, "account_id": 2, "account_name": "Giro", "txn_date": "2026-09-20", "description": "TENMANYA BERLIN", "counterparty": null, "amount": -23.5, "currency": "EUR", "kind": "card", "suggestion": "restaurants", "pattern": "Tenmanya"}]}"#
        let q = try Api.decoder.decode(Queue.self, from: Data(queue.utf8))
        XCTAssertEqual(q.transactions?.first?.pattern, "Tenmanya")
        XCTAssertEqual(q.remaining, 140)

        let list = ##"[{"slug": "restaurants", "label": "Restaurants", "group": "spending", "colour": "#f59e0b", "transactions": 31, "rules": 4, "deletable": true, "group_locked": false}]"##
        let categories = try Api.decoder.decode([Category].self, from: Data(list.utf8))
        XCTAssertEqual(categories.first?.title, "Restaurants")
    }

    // `remember` must go as a JSON boolean: the dashboard is Python, and
    // the string "false" is true there.
    func testVerdictGoesAsJSONTypes() async throws {
        StubProtocol.reply = (200, #"{"ok": true, "result": {"txn_id": 7, "category": "other", "rule": null, "applied": 0}}"#)
        let api = Api(baseURL: "https://example.com", token: "t", session: StubProtocol.session)
        try await api.deliver(Verdict(txnId: 7, category: "other", pattern: " Tenmanya ", remember: false))
        XCTAssertEqual(StubProtocol.lastRequest?.url?.path, "/api/v1/tools/set_category")
        let body = try XCTUnwrap(StubProtocol.lastBody)
        let sent = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
        XCTAssertEqual(sent["txn_id"] as? Int, 7)
        XCTAssertEqual(sent["pattern"] as? String, "Tenmanya")
        XCTAssertTrue((sent["remember"] as? NSNumber).map { CFGetTypeID($0) == CFBooleanGetTypeID() } ?? false)
        XCTAssertEqual(sent["remember"] as? Bool, false)
    }

    func testPendingVerdictsSurviveInOrder() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = Store(service: "test." + UUID().uuidString, directory: dir)
        store.queue(Verdict(txnId: 1, category: "food"))
        store.queue(Verdict(txnId: 2, owner: "shared"))
        let again = Store(service: "test.other", directory: dir).pending()
        XCTAssertEqual(again.map(\.txnId), [1, 2])
        XCTAssertEqual(again.last?.owner, "shared")
        store.savePending([])
        XCTAssertTrue(store.pending().isEmpty)
    }

    // MARK: The share sheet and the background round

    func testImportSendsMultipartAndReadsTheReport() async throws {
        StubProtocol.reply = (200, #"{"ok": true, "account": {"id": 3, "name": "Giro"}, "result": {"label": "ING CSV", "inserted": 12, "duplicates": 40, "skipped": 0, "parsed": 52, "problems": [], "notes": ["Two rows kept out."], "closing_balance": 10.5, "unrecognised": [], "imports": [1]}}"#)
        let api = Api(baseURL: "https://example.com", token: "t", session: StubProtocol.session)
        let reply = try await api.importFiles(accountId: 3, files: [Api.Upload(name: "a.csv", mime: "text/csv", data: Data("x;y".utf8))])
        XCTAssertEqual(reply.result?.inserted, 12)
        XCTAssertEqual(reply.result?.notes, ["Two rows kept out."])
        XCTAssertEqual(reply.account?.name, "Giro")
        XCTAssertEqual(StubProtocol.lastRequest?.url?.path, "/api/v1/accounts/3/import")
        let body = String(decoding: try XCTUnwrap(StubProtocol.lastBody), as: UTF8.self)
        XCTAssertTrue(body.contains(#"name="file"; filename="a.csv""#), body)
    }

    func testImportRefusalCarriesTheSentence() async {
        StubProtocol.reply = (422, #"{"ok": false, "error": "No reader recognised any of those files.", "files": ["a.pdf"]}"#)
        let api = Api(baseURL: "https://example.com", token: "t", session: StubProtocol.session)
        do {
            _ = try await api.importFiles(accountId: 3, files: [Api.Upload(name: "a.pdf", data: Data([1]))])
            XCTFail("expected a failure")
        } catch {
            XCTAssertEqual(error as? Api.Failure, Api.Failure(status: 422, message: "No reader recognised any of those files."))
        }
    }

    // Money about to run out outranks a stopped link, which outranks a
    // long queue; a short queue is no news at all.
    func testNoticeOrder() {
        var s = Snapshot()
        s.waiting = Waiting(uncategorised: 4, unassignedSpending: 3)
        XCTAssertNil(Round.pick(s))
        s.waiting = Waiting(uncategorised: 25, unassignedSpending: 0)
        XCTAssertEqual(Round.pick(s)?.key, "queue:2")
        s.sync = SyncHealth(links: 3, red: 1)
        XCTAssertEqual(Round.pick(s)?.key, "red:1")
        s.upcoming = Upcoming(belowZero: Event(date: "2026-10-01", name: "Rent", amount: -900, account: "Giro", running: -120))
        XCTAssertEqual(Round.pick(s)?.key, "below:2026-10-01")
    }
}

/// Answers every request with one canned reply, and remembers the request.
final class StubProtocol: URLProtocol {
    nonisolated(unsafe) static var reply: (Int, String) = (200, "{}")
    nonisolated(unsafe) static var lastRequest: URLRequest?
    /// A request's body arrives here as a stream, not as `httpBody`.
    nonisolated(unsafe) static var lastBody: Data?

    static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubProtocol.self]
        return URLSession(configuration: config)
    }()

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        StubProtocol.lastRequest = request
        StubProtocol.lastBody = request.httpBody ?? request.httpBodyStream.map { stream in
            stream.open()
            defer { stream.close() }
            var data = Data()
            var buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let n = stream.read(&buffer, maxLength: buffer.count)
                if n <= 0 { break }
                data.append(buffer, count: n)
            }
            return data
        }
        let (status, body) = StubProtocol.reply
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1",
                                       headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
