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
        StubProtocol.reply = (200, #"{"ok": true, "result": {"prices": {"priced": 12, "failed": 0}, "net_worth": {"net_worth": 5}}}"#)
        let api = Api(baseURL: "https://example.com", token: "t", session: StubProtocol.session)
        let market = try await api.refreshMarket()
        XCTAssertEqual(market.prices?.priced, 12)
        XCTAssertEqual(market.netWorth?.total, 5)
        let request = try XCTUnwrap(StubProtocol.lastRequest)
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.url?.path, "/api/v1/tools/refresh_market")
    }
}

/// Answers every request with one canned reply, and remembers the request.
final class StubProtocol: URLProtocol {
    nonisolated(unsafe) static var reply: (Int, String) = (200, "{}")
    nonisolated(unsafe) static var lastRequest: URLRequest?

    static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubProtocol.self]
        return URLSession(configuration: config)
    }()

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        StubProtocol.lastRequest = request
        let (status, body) = StubProtocol.reply
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1",
                                       headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
