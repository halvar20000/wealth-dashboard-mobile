import SwiftUI

/// The chart's windows, in the order contract rule 7 fixes.
private struct ChartWindow {
    let key: String
    let label: LocalizedStringKey
}

private let windows = [
    ChartWindow(key: "1m", label: "1 M"), ChartWindow(key: "3m", label: "3 M"),
    ChartWindow(key: "6m", label: "6 M"), ChartWindow(key: "ytd", label: "YTD"),
    ChartWindow(key: "1y", label: "1 Y"), ChartWindow(key: "all", label: "All"),
]

/// Everything that is owned, and how it got here.
///
/// Three views of one portfolio: the securities with what each has made,
/// where the money sits by asset class, and the accounts themselves. The
/// chart at the top belongs to all three — it is the net worth, the one
/// line that answers "how is it going".
struct PortfolioView: View {
    @Environment(AppModel.self) private var model

    private enum Part: Hashable { case holdings, allocation, accounts }

    @State private var part = Part.holdings
    @State private var period = "1y"
    @State private var history: History?
    @State private var held: Holdings?
    @State private var allocation: Allocation?
    @State private var returns: Returns?
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    header
                    chart
                    Picker("Window", selection: $period) {
                        ForEach(windows, id: \.key) { w in Text(w.label).tag(w.key) }
                    }
                    .pickerStyle(.segmented)
                }
                .listRowSeparator(.hidden)

                Section {
                    Picker("Show", selection: $part) {
                        Text("Securities").tag(Part.holdings)
                        Text("Allocation").tag(Part.allocation)
                        Text("Accounts").tag(Part.accounts)
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }

                if let error, held == nil, part != .accounts {
                    Section {
                        Text(error).font(.footnote).foregroundStyle(Color.loss)
                        Button("Try again") { Task { await load() } }
                    }
                }

                switch part {
                case .holdings: holdingsSection
                case .allocation: allocationSection
                case .accounts: accountsSection
                }
            }
            .navigationTitle("Portfolio")
            .navigationBarTitleDisplayMode(.inline)
            .personMenu()
            .navigationDestination(for: Account.self) { a in TransactionsView(account: a) }
            .refreshable {
                async let line: Void = loadHistory()
                await load()
                await line
            }
            // Four calls to a small server are a thing to ask for, not to
            // assume: once when the tab first opens, then on a pull.
            .task { if held == nil { await load() } }
            .task(id: period) { await loadHistory() }
            // Another person's accounts: the page is theirs now, so the
            // old one goes and all of it is asked again.
            .onChange(of: model.person) {
                held = nil
                allocation = nil
                returns = nil
                history = nil
                Task {
                    async let line: Void = loadHistory()
                    await load()
                    await line
                }
            }
        }
    }

    // MARK: The line

    private var currency: String { held?.baseCurrency ?? model.currency }

    @ViewBuilder private var header: some View {
        let line = history?.line ?? []
        VStack(alignment: .leading, spacing: 2) {
            Text("NET WORTH").font(.caption2).tracking(2).foregroundStyle(.secondary)
            Text(Fmt.money(line.last ?? model.snapshot?.netWorth?.total, currency))
                .font(.title.weight(.bold))
                .minimumScaleFactor(0.5).lineLimit(1)
            if line.count > 1, let first = line.first, let last = line.last {
                let change = last - first
                let parts = [
                    Fmt.signedMoney(change, currency),
                    first != 0 ? Fmt.percent(change / first) : nil,
                    history?.firstDate.map { String(localized: "since \(Fmt.day($0))") },
                ].compactMap { $0 }
                Text(parts.joined(separator: " · "))
                    .font(.footnote).foregroundStyle(Color.sign(change))
            }
        }
    }

    @ViewBuilder private var chart: some View {
        let line = history?.line ?? []
        Group {
            if line.count > 1 {
                LineChart(values: line)
            } else if history == nil {
                ProgressView()
            } else {
                Text("No points for this window yet.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 170)
    }

    // MARK: The securities

    @ViewBuilder private var holdingsSection: some View {
        let rows = (held?.holdings ?? []).sorted { ($0.valueBase ?? 0) > ($1.valueBase ?? 0) }
        if let held {
            Section {
                let value = rows.reduce(0) { $0 + ($1.valueBase ?? 0) }
                let invested = rows.reduce(0) { $0 + ($1.netInvested ?? 0) }
                HStack(alignment: .top) {
                    Figure(label: "Securities", value: Fmt.money(value, currency))
                    Spacer()
                    Figure(label: "Invested", value: Fmt.money(invested, currency))
                    Spacer()
                    Figure(label: "Gain", value: Fmt.signedMoney(value - invested, currency),
                           colour: .sign(value - invested),
                           note: returns?.all?.twr.map { Fmt.percent($0) })
                }
            } footer: {
                if let asOf = held.pricesAsOf { Text("Prices \(Fmt.day(asOf))") }
            }
            Section {
                ForEach(rows) { h in
                    HoldingRow(holding: h, ret: returns?.holdings?[h.isin ?? ""], currency: currency)
                }
            }
        } else if loading {
            Section { ProgressView().frame(maxWidth: .infinity) }
        }
        if held != nil, rows.isEmpty {
            Section { Text("No securities held.").foregroundStyle(.secondary) }
        }
    }

    // MARK: Where the money sits

    @ViewBuilder private var allocationSection: some View {
        let rows = (allocation?.byClass ?? []).filter { ($0.value ?? 0) > 0 }
            .sorted { ($0.value ?? 0) > ($1.value ?? 0) }
        Section {
            if rows.isEmpty {
                Text(loading ? "…" : String(localized: "No allocation yet — the positions are not classified."))
                    .font(.footnote).foregroundStyle(.secondary)
            } else {
                HStack(spacing: 16) {
                    DonutChart(shares: rows.map { $0.value ?? 0 }) {
                        VStack(spacing: 0) {
                            Text(Fmt.money(allocation?.total, currency))
                                .font(.caption.weight(.bold)).minimumScaleFactor(0.6).lineLimit(1)
                            Text("in all").font(.caption2).foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 20)
                    }
                    .frame(width: 150, height: 150)
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(Array(rows.enumerated()), id: \.offset) { i, r in
                            HStack(spacing: 8) {
                                Circle().fill(slices[i % slices.count]).frame(width: 10, height: 10)
                                Text(assetClass(r.key)).font(.caption)
                                Spacer(minLength: 4)
                                Text(share(r.share)).font(.caption.weight(.semibold))
                            }
                        }
                    }
                }
                .padding(.vertical, 6)
                LabeledContent("of which cash", value: Fmt.money(allocation?.cash, currency))
                    .font(.footnote)
            }
        }
    }

    private func share(_ percent: Double?) -> String {
        guard let percent else { return "—" }
        return percent.formatted(.number.precision(.fractionLength(0))) + " %"
    }

    /// The dashboard's slugs in words; an unknown one as it is.
    private func assetClass(_ key: String?) -> String {
        switch key ?? "" {
        case "equity": String(localized: "Equities")
        case "bond": String(localized: "Bonds")
        case "real_estate": String(localized: "Real estate")
        case "commodity": String(localized: "Commodities")
        case "cash": String(localized: "Cash")
        case "crypto": String(localized: "Crypto")
        case "other": String(localized: "Other")
        default: (key ?? "").replacingOccurrences(of: "_", with: " ").capitalized
        }
    }

    // MARK: The accounts

    @ViewBuilder private var accountsSection: some View {
        Section {
            if model.accounts.isEmpty {
                Text("No accounts yet.").foregroundStyle(.secondary)
            }
            ForEach(model.accounts) { a in
                NavigationLink(value: a) { AccountRow(account: a, currency: model.currency) }
            }
        }
    }

    // MARK: Loading

    private func load() async {
        loading = true
        defer { loading = false }
        async let ring = model.allocation()
        async let ret = model.returns()
        do {
            held = try await model.holdings()
            error = nil
        } catch is CancellationError {
        } catch let url as URLError where url.code == .cancelled {
        } catch {
            self.error = AppModel.sentence(for: error)
        }
        allocation = await ring ?? allocation
        returns = await ret ?? returns
    }

    /// Only the chart's window changed — one call, not four.
    private func loadHistory() async {
        do {
            history = try await model.history(period: period)
        } catch {
            if history == nil { history = History(points: []) }
        }
    }
}

private struct Figure: View {
    let label: LocalizedStringKey
    let value: String
    var colour: Color = .primary
    var note: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(colour)
                .minimumScaleFactor(0.6).lineLimit(1)
            if let note { Text(note).font(.caption2).foregroundStyle(.secondary) }
        }
    }
}

/// One security. Tapping it opens what would otherwise be clutter.
private struct HoldingRow: View {
    let holding: Holding
    let ret: Period?
    let currency: String
    @State private var open = false

    var body: some View {
        let h = holding
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(h.title).fontWeight(.semibold).lineLimit(2)
                    Text("\(h.symbol ?? h.isin ?? "") · \(Fmt.quantity(h.quantity)) × \(Fmt.price(h.price, h.currency))")
                        .font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(Fmt.money(h.valueBase, currency)).fontWeight(.bold)
                    Text([ret?.twr.map { Fmt.percent($0) }, Fmt.signedMoney(h.gain, currency)]
                        .compactMap { $0 }.joined(separator: " · "))
                        .font(.caption).foregroundStyle(Color.sign(h.gain))
                }
            }
            if open {
                VStack(spacing: 4) {
                    Detail(label: "Invested", value: Fmt.money(h.netInvested, currency))
                    if let v = ret?.twrAnnual { Detail(label: "Return p.a.", value: Fmt.percent(v)) }
                    if let v = ret?.mwr { Detail(label: "Money-weighted p.a.", value: Fmt.percent(v)) }
                    if let v = ret?.since { Detail(label: "Since", value: Fmt.day(v)) }
                    if let v = h.priceAsOf { Detail(label: "Price of", value: Fmt.day(v)) }
                    if let v = h.lastTrade { Detail(label: "Last trade", value: Fmt.day(v)) }
                    if let a = h.accounts, !a.isEmpty { Detail(label: "Held in", value: a.joined(separator: ", ")) }
                    if h.incompleteHistory == true {
                        Text("The trade history has gaps — the return is an approximation.")
                            .font(.caption2).foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { withAnimation(.snappy) { open.toggle() } }
    }
}

private struct Detail: View {
    let label: LocalizedStringKey
    let value: String

    var body: some View {
        HStack {
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value).multilineTextAlignment(.trailing)
        }
        .font(.caption)
    }
}
