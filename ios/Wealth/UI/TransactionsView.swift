import SwiftUI

/// The rows, newest first — of one account, or of everything — with the
/// dashboard's own search behind the field.
struct TransactionsView: View {
    @Environment(AppModel.self) private var model
    let account: Account?

    @State private var query = ""
    @State private var page: TransactionPage?
    @State private var error: String?

    var body: some View {
        List {
            if let error {
                Text(error).font(.footnote).foregroundStyle(Color.loss)
            }
            if let page {
                Section {
                    ForEach(page.transactions ?? []) { t in
                        TxnRow(txn: t)
                    }
                } header: {
                    Text("\(page.returned ?? 0) of \(page.matched ?? 0)")
                }
            }
        }
        .overlay {
            if page == nil && error == nil { ProgressView() }
        }
        .searchable(text: $query, prompt: Text(account.map { "Search \($0.title)" } ?? "Search everything"))
        .task(id: query) {
            // Wait for the thumb to pause before asking the dashboard.
            if !query.isEmpty {
                try? await Task.sleep(for: .milliseconds(350))
                if Task.isCancelled { return }
            }
            await load()
        }
        .refreshable { await load() }
        .navigationTitle(account?.title ?? String(localized: "Rows"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func load() async {
        do {
            page = try await model.transactions(accountId: account?.id, query: query)
            error = nil
        } catch is CancellationError {
        } catch let url as URLError where url.code == .cancelled {
        } catch {
            self.error = AppModel.sentence(for: error)
        }
    }
}

private struct TxnRow: View {
    let txn: Txn

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(String((txn.description ?? txn.counterparty ?? txn.kind ?? "").prefix(70)))
                    .lineLimit(2)
                Text([Fmt.day(txn.txnDate), txn.account, txn.category]
                        .compactMap { $0 }
                        .filter { !$0.isEmpty }
                        .joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(Fmt.signedMoney(txn.amount, txn.currency))
                .fontWeight(.semibold)
                .foregroundStyle(Color.sign(txn.amount))
        }
    }
}
