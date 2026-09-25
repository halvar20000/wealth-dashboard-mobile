import SwiftUI

/// What came in, what went out, and what is left.
///
/// The dashboard does the arithmetic — every currency at the rate of its
/// month, transfers between one's own accounts left out because moving
/// money is not spending it. The page draws it and leads with the one
/// figure a household actually argues about: what an average month
/// leaves over. The rows behind it are one button away.
struct CashflowView: View {
    @Environment(AppModel.self) private var model
    @State private var months = 13
    @State private var flow: Cashflow?
    @State private var error: String?
    @State private var tooOld = false

    var body: some View {
        List {
            Section {
                Picker("Months", selection: $months) {
                    Text("6 M").tag(6)
                    Text("13 M").tag(13)
                    Text("25 M").tag(25)
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }

            if let error {
                Text(error).font(.footnote).foregroundStyle(Color.loss)
            }

            if let flow {
                let ccy = flow.baseCurrency ?? model.currency
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Left over in an average month")
                            .font(.caption).foregroundStyle(.secondary)
                        Text(Fmt.signedMoney(flow.averageNet, ccy))
                            .font(.system(size: 32, weight: .bold))
                            .foregroundStyle(Color.sign(flow.averageNet))
                            .minimumScaleFactor(0.5)
                            .lineLimit(1)
                        HStack {
                            Figure(label: "In", value: Fmt.money(flow.averageIncome, ccy), colour: .gain)
                            Spacer()
                            Figure(label: "Out", value: Fmt.money(flow.averageSpending, ccy), colour: .loss)
                            Spacer()
                            Figure(label: "Invested", value: Fmt.money(flow.averageInvestment, ccy), colour: .accentColor)
                        }
                        if let loose = flow.unconverted, !loose.isEmpty {
                            Text("Not converted, no rate: \(loose.map { Fmt.money($0.amount, $0.currency) }.joined(separator: ", "))")
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }

                if let rows = flow.months, !rows.isEmpty {
                    Section {
                        MonthBars(months: rows, currency: ccy)
                    }
                }

                if let spending = flow.byCategory, !spending.isEmpty {
                    Section("Where it goes") {
                        ForEach(spending.prefix(14), id: \.self) { c in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(c.title)
                                    Spacer()
                                    Text("\(Fmt.money(c.perMonth, ccy)) / month").fontWeight(.semibold)
                                }
                                ProgressView(value: share(c.perMonth, of: flow.averageSpending))
                                    .tint(Color(hex: c.colour) ?? .accentColor)
                            }
                        }
                    }
                }

                if let income = flow.incomeByCategory, !income.isEmpty {
                    Section("Where it comes from") {
                        ForEach(income.prefix(8), id: \.self) { c in
                            HStack {
                                Text(c.title)
                                Spacer()
                                Text("\(Fmt.money(c.perMonth, ccy)) / month")
                                    .fontWeight(.semibold)
                                    .foregroundStyle(Color.gain)
                            }
                        }
                    }
                }
            }
        }
        .overlay {
            if tooOld {
                ContentUnavailableView("Needs a newer dashboard", systemImage: "arrow.up.circle",
                                       description: Text("Cash flow arrives with dashboard 0.73.0. The rows are still here."))
            } else if flow == nil && error == nil {
                ProgressView()
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink {
                    TransactionsView(account: nil)
                } label: {
                    Label("Rows", systemImage: "list.bullet")
                }
            }
        }
        .task(id: [months, model.person ?? -1]) { await load() }
        .refreshable { await load() }
        .navigationTitle("Cash flow")
        .navigationBarTitleDisplayMode(.inline)
        .personMenu()
    }

    private func load() async {
        do {
            flow = try await model.cashflow(months: months)
            error = nil
            tooOld = false
        } catch let failure as Api.Failure where failure.status == 404 {
            // Contract rule 6: an older dashboard is not an error.
            tooOld = true
            error = nil
        } catch is CancellationError {
        } catch let url as URLError where url.code == .cancelled {
        } catch {
            self.error = AppModel.sentence(for: error)
        }
    }

    private func share(_ part: Double?, of whole: Double?) -> Double {
        guard let part, let whole, whole > 0 else { return 0 }
        return min(max(part / whole, 0), 1)
    }
}

private struct Figure: View {
    let label: LocalizedStringKey
    let value: String
    let colour: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(colour)
        }
    }
}

/// A month at a time: what came in above the line, what went out below
/// it. A year of bars says more about a household than any average.
private struct MonthBars: View {
    let months: [MonthFlow]
    let currency: String

    var body: some View {
        let biggest = max(months.map { max($0.income ?? 0, $0.spending ?? 0) }.max() ?? 0, 1)
        VStack(spacing: 6) {
            GeometryReader { geo in
                let slot = geo.size.width / CGFloat(months.count)
                let bar = min(slot * 0.34, 14)
                let mid = geo.size.height / 2
                ZStack(alignment: .topLeading) {
                    ForEach(Array(months.enumerated()), id: \.offset) { i, m in
                        let centre = slot * CGFloat(i) + slot / 2
                        let up = CGFloat((m.income ?? 0) / biggest) * (mid - 4)
                        let down = CGFloat((m.spending ?? 0) / biggest) * (mid - 4)
                        let alpha = i == months.count - 1 ? 1.0 : 0.75
                        Rectangle().fill(Color.gain.opacity(alpha))
                            .frame(width: bar, height: up)
                            .offset(x: centre - bar - 1, y: mid - up)
                        Rectangle().fill(Color.loss.opacity(alpha))
                            .frame(width: bar, height: down)
                            .offset(x: centre + 1, y: mid)
                    }
                    Rectangle().fill(Color.secondary.opacity(0.35))
                        .frame(width: geo.size.width, height: 1)
                        .offset(y: mid)
                }
            }
            .frame(height: 140)
            HStack {
                Text(months.first?.month ?? "").font(.caption2).foregroundStyle(.secondary)
                Spacer()
                if let last = months.last {
                    Text(verbatim: "\(last.month ?? ""): \(Fmt.signedMoney(last.net, currency))")
                        .font(.caption2)
                        .foregroundStyle(Color.sign(last.net))
                }
            }
        }
        .padding(.vertical, 6)
    }
}
