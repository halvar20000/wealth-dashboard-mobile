import SwiftUI

/// The windows the snapshot may carry, in the order they are shown.
private struct PeriodWindow {
    let key: String
    let label: LocalizedStringKey
}

// Short on purpose: eight of these sit four to a row on a phone, so the
// day and "since start" are on screen together.
private let periods = [
    PeriodWindow(key: "1d", label: "1D"), PeriodWindow(key: "1w", label: "1W"),
    PeriodWindow(key: "1m", label: "1M"), PeriodWindow(key: "3m", label: "3M"),
    PeriodWindow(key: "ytd", label: "YTD"), PeriodWindow(key: "1y", label: "1Y"),
    PeriodWindow(key: "3y", label: "3Y"), PeriodWindow(key: "all", label: "Start"),
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
                        Text(model.error ?? String(localized: "Pull to load them from the dashboard."))
                    } actions: {
                        Button("Try again") { Task { await model.refresh() } }
                    }
                }
            }
            .navigationTitle(model.serverName ?? String(localized: "Overview"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                // Two refreshes, never confused (contract rule 9): pulling
                // the list re-reads what the dashboard knows; this button
                // makes it quote the holdings and fetch the rates again.
                // Neither talks to a bank.
                if model.supports("0.73.0") && model.snapshot != nil {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            Task { await model.refreshMarket() }
                        } label: {
                            if model.pricing {
                                ProgressView()
                            } else {
                                Label("Refresh quotes", systemImage: "chart.line.uptrend.xyaxis.circle")
                            }
                        }
                        .disabled(model.pricing)
                    }
                }
            }
        }
    }

    private func content(_ s: Snapshot) -> some View {
        let ccy = s.currency
        return List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text("NET WORTH")
                        .font(.caption2).tracking(2).foregroundStyle(.secondary)
                    Text(Fmt.money(s.figure(excluding: model.excluded), ccy))
                        .font(.system(size: 40, weight: .heavy))
                        .minimumScaleFactor(0.5)
                        .lineLimit(1)
                    let dropped = s.classes.compactMap(\.name).filter { model.excluded.contains($0) }.map { Snapshot.className($0) }
                    if !dropped.isEmpty {
                        Text("without \(dropped.joined(separator: ", ")) · with everything \(Fmt.money(s.netWorth?.total, ccy))")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
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
                    // Two rows of four rather than a strip that scrolls off
                    // the screen: the point of these eight is comparing them.
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                        ForEach(shown, id: \.key) { p in
                            PerfTile(label: p.label, period: s.performance![p.key]!, currency: ccy)
                        }
                    }
                    .padding(.vertical, 4)
                    .listRowInsets(EdgeInsets(top: 0, leading: 12, bottom: 0, trailing: 12))
                    .listRowBackground(Color.clear)
                }
            }

            // The debt is among these, as a line of its own: without it a
            // single untick would drop every loan from the figure.
            let classes = s.classes
            if !classes.isEmpty {
                Section {
                    ForEach(classes, id: \.self) { c in
                        ClassRow(item: c, currency: ccy, counted: !model.excluded.contains(c.name ?? "")) {
                            model.toggleClass(c.name ?? "")
                        }
                    }
                } header: {
                    HStack {
                        Text("What counts")
                        Spacer()
                        if !model.excluded.isEmpty {
                            Button("Everything") { model.includeEverything() }
                                .font(.caption)
                                .textCase(nil)
                        }
                    }
                }
            }

            if let up = s.upcoming {
                Section("Next \(up.days ?? 30) days") {
                    // One line of context: what is due is the list below,
                    // and the only figure worth carrying over it is what
                    // is left once it has all gone out.
                    Text(upcomingLine(up, ccy))
                        .font(.footnote)
                        .foregroundStyle(up.belowZero != nil ? Color.loss : Color.secondary)
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

    private func upcomingLine(_ up: Upcoming, _ ccy: String) -> String {
        var line = String(localized: "Cash now \(Fmt.money(up.starting, ccy)) → after \(Fmt.money(up.ending, ccy))")
        if let below = up.belowZero {
            line += " · " + String(localized: "below zero on \(Fmt.day(below.date))")
        }
        return line
    }

    private func syncLine(_ sync: SyncHealth?) -> String {
        let links = sync?.links ?? 0
        let red = sync?.red ?? 0
        return red > 0 ? String(localized: "\(links) · \(red) red") : "\(links)"
    }
}

private struct PerfTile: View {
    let label: LocalizedStringKey
    let period: Period
    let currency: String

    var body: some View {
        let colour = Color.sign(period.twr)
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            Text(Fmt.percent(period.twr)).font(.subheadline.weight(.heavy)).foregroundStyle(colour)
            Text(Fmt.signedMoney(period.pnl, currency)).font(.caption2).foregroundStyle(colour.opacity(0.85))
        }
        .lineLimit(1)
        .minimumScaleFactor(0.6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 8)
        .padding(.vertical, 10)
        .background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
    }
}

/// One asset class, ticked when it counts towards the figure at the top.
private struct ClassRow: View {
    let item: ClassValue
    let currency: String
    let counted: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            HStack {
                Image(systemName: counted ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(counted ? Color.gain : Color.secondary)
                Text(Snapshot.className(item.name))
                Spacer()
                Text(Fmt.money(item.value, currency))
                    .fontWeight(.semibold)
                    // What is owed reads in red: it is not an asset.
                    .foregroundStyle(counted && (item.value ?? 0) < 0 ? Color.loss : (counted ? Color.primary : Color.secondary))
            }
            .foregroundStyle(counted ? Color.primary : Color.secondary)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
