import SwiftUI

/// The accounts as the last snapshot knew them, and for one of them the
/// rows behind it.
struct AccountsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            List {
                ForEach(model.accounts) { a in
                    NavigationLink(value: a) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(a.title).lineLimit(1)
                                let detail = [a.bank, a.asOf.map { Fmt.day($0) }]
                                    .compactMap { $0 }
                                    .filter { !$0.isEmpty }
                                    .joined(separator: " · ")
                                if !detail.isEmpty {
                                    Text(detail).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 2) {
                                Text(Fmt.money(a.balanceBase ?? a.balance, a.balanceBase != nil ? model.currency : a.currency))
                                    .fontWeight(.semibold)
                                    .foregroundStyle((a.balanceBase ?? a.balance ?? 0) < 0 ? Color.loss : Color.primary)
                                if a.balanceBase != nil, let ccy = a.currency, ccy.uppercased() != model.currency.uppercased() {
                                    Text(Fmt.money(a.balance, ccy)).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .overlay {
                if model.accounts.isEmpty {
                    ContentUnavailableView("No accounts yet", systemImage: "building.columns",
                                           description: Text("They appear once the dashboard has answered."))
                }
            }
            .refreshable { await model.refresh() }
            .navigationTitle("Accounts")
            .navigationDestination(for: Account.self) { a in
                TransactionsView(account: a)
            }
        }
    }
}
