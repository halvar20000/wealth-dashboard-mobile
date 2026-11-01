import SwiftUI

/// The rows, newest first — of one account, of one category, or of
/// everything — with the dashboard's own search behind the field. Every
/// row opens to the whole of it.
struct TransactionsView: View {
    @Environment(AppModel.self) private var model
    let account: Account?
    /// Set when a Cash flow line was tapped: the slug, so a renamed
    /// category still finds its rows, and the label for the title.
    var category: String? = nil
    var categoryTitle: String? = nil

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
                        NavigationLink {
                            TxnDetailView(txn: t) { changed in replace(changed) }
                        } label: {
                            TxnRow(txn: t, categoryLabel: model.label(of: t.category))
                        }
                    }
                } header: {
                    Text("\(page.returned ?? 0) of \(page.matched ?? 0)")
                }
            }
        }
        .overlay {
            if page == nil && error == nil { ProgressView() }
        }
        .searchable(text: $query, prompt: Text(searchPrompt))
        .task(id: query) {
            // Wait for the thumb to pause before asking the dashboard.
            if !query.isEmpty {
                try? await Task.sleep(for: .milliseconds(350))
                if Task.isCancelled { return }
            }
            await load()
        }
        .task { await model.ensureCategories() }
        .refreshable { await load() }
        .onChange(of: model.person) { Task { await load() } }
        .navigationTitle(account?.title ?? categoryTitle ?? String(localized: "Rows"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private var searchPrompt: String {
        if let account { return String(localized: "Search \(account.title)") }
        if let categoryTitle { return String(localized: "Search \(categoryTitle)") }
        return String(localized: "Search everything")
    }

    /// The row as it is after an edit on its page.
    private func replace(_ txn: Txn) {
        guard var p = page, var rows = p.transactions,
              let i = rows.firstIndex(where: { $0.id == txn.id }) else { return }
        rows[i] = txn
        p.transactions = rows
        page = p
    }

    private func load() async {
        do {
            page = try await model.transactions(accountId: account?.id, query: query, category: category)
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
    let categoryLabel: String?

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(String((txn.description ?? txn.counterparty ?? txn.kind ?? "").prefix(70)))
                    .lineLimit(2)
                Text([Fmt.day(txn.txnDate), txn.account, categoryLabel]
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
