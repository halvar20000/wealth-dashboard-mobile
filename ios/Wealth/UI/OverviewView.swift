import SwiftUI

/// The windows the snapshot may carry, in the order they are shown.
private struct PeriodWindow {
    let key: String
    let label: LocalizedStringKey
}

private let periods = [
    PeriodWindow(key: "1d", label: "1 day"), PeriodWindow(key: "1w", label: "1 week"),
    PeriodWindow(key: "1m", label: "1 month"), PeriodWindow(key: "3m", label: "3 months"),
    PeriodWindow(key: "ytd", label: "YTD"), PeriodWindow(key: "1y", label: "1 year"),
    PeriodWindow(key: "3y", label: "3 years"), PeriodWindow(key: "all", label: "Since start"),
]

/// Net worth, its parts, the return per period and what is due — all
/// from one `snapshot`. The cached one is drawn first; a refresh that
/// fails leaves it and says when it was read (contract rule 1).
struct OverviewView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            Group {
                if let snapshot = model.snapshot {
                    content(snapshot)
                } else if model.refreshing {
                    ProgressView()
                } else {
                    ContentUnavailableView {
                        Label("No figures yet", systemImage: "icloud.slash")
                    } description: {
                        Text(model.error ?? "Pull to load them from the dashboard.")
                    } actions: {
                        Button("Try again") { Task { await model.refresh() } }
                    }
                }
            }
            .navigationTitle(model.serverName ?? "Overview")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func content(_ s: Snapshot) -> some View {
        let ccy = s.currency
        return List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text("NET WORTH")
                        .font(.caption2).tracking(2).foregroundStyle(.secondary)
                    Text(Fmt.money(s.netWorth?.total, ccy))
                        .font(.system(size: 40, weight: .heavy))
                        .minimumScaleFactor(0.5)
                        .lineLimit(1)
                    let parts = [
                        s.netWorth?.cash.map { "\(String(localized: "Cash")) \(Fmt.money($0, ccy))" },
                        s.netWorth?.securities.map { "\(String(localized: "Securities")) \(Fmt.money($0, ccy))" },
                    ].compactMap { $0 }.joined(separator: " · ")
                    if !parts.isEmpty {
                        Text(parts).font(.footnote).foregroundStyle(.secondary)
                    }
                    Text(readLine(s)).font(.footnote).foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
                .listRowBackground(Color.clear)
            }

            if let error = model.error {
                Section {
                    Text(error).font(.footnote).foregroundStyle(Color.loss)
                }
            }

            let shown = periods.filter { s.performance?[$0.key] != nil }
            if !shown.isEmpty {
                Section("Performance") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(shown, id: \.key) { p in
                                PerfTile(label: p.label, period: s.performance![p.key]!, currency: ccy)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                }
            }

            if let classes = s.netWorth?.byClass, !classes.isEmpty {
                Section("By class") {
                    ForEach(classes, id: \.self) { c in
                        LabeledContent(c.name ?? "—", value: Fmt.money(c.value, ccy))
                    }
                }
            }

            if let up = s.upcoming {
                Section("Next \(up.days ?? 30) days") {
                    LabeledContent("Cash now", value: Fmt.money(up.starting, ccy))
                    LabeledContent("After what is due", value: Fmt.money(up.ending, ccy))
                    if let low = up.lowest {
                        Text("Lowest \(Fmt.money(low.running, ccy)) on \(Fmt.day(low.date)) — \(low.name ?? "")")
                            .font(.footnote)
                            .foregroundStyle((low.running ?? 0) < 0 ? Color.loss : Color.secondary)
                    }
                    ForEach(s.events ?? [], id: \.self) { e in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(e.name ?? "").lineLimit(1)
                                Text(Fmt.day(e.date) + ((e.estimate ?? false) ? " · " + String(localized: "expected") : ""))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(Fmt.signedMoney(e.amount, ccy))
                                .fontWeight(.semibold)
                                .foregroundStyle(Color.sign(e.amount))
                        }
                    }
                }
            }

            Section("Waiting for you") {
                LabeledContent("Uncategorised", value: "\(s.waiting?.uncategorised ?? 0)")
                LabeledContent("Unassigned spending", value: "\(s.waiting?.unassignedSpending ?? 0)")
                LabeledContent("Bank connections", value: syncLine(s.sync))
            }
        }
        .refreshable { await model.refresh() }
    }

    private func readLine(_ s: Snapshot) -> String {
        var line = model.stale
            ? String(localized: "As of \(Fmt.since(model.readAt))")
            : String(localized: "Updated \(Fmt.since(model.readAt))")
        if let prices = s.netWorth?.pricesAsOf {
            line += " · " + String(localized: "prices \(Fmt.day(prices))")
        }
        return line
    }

    private func syncLine(_ sync: SyncHealth?) -> String {
        let links = sync?.links ?? 0
        let red = sync?.red ?? 0
        return red > 0 ? "\(links) · \(red) " + String(localized: "red") : "\(links)"
    }
}

private struct PerfTile: View {
    let label: LocalizedStringKey
    let period: Period
    let currency: String

    var body: some View {
        let colour = Color.sign(period.twr)
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).textCase(.uppercase).foregroundStyle(.secondary)
            Text(Fmt.percent(period.twr)).font(.headline.weight(.heavy)).foregroundStyle(colour)
            Text(Fmt.signedMoney(period.pnl, currency)).font(.caption).foregroundStyle(colour.opacity(0.85))
        }
        .frame(width: 118, alignment: .leading)
        .padding(12)
        .background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
    }
}
