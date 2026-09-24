import SwiftUI

/// One account as the last snapshot knew it: its name, its bank, and its
/// balance in the base currency — with the account's own currency under
/// it where the two differ.
struct AccountRow: View {
    let account: Account
    let currency: String

    var body: some View {
        let a = account
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
                Text(Fmt.money(a.balanceBase ?? a.balance, a.balanceBase != nil ? currency : a.currency))
                    .fontWeight(.semibold)
                    .foregroundStyle((a.balanceBase ?? a.balance ?? 0) < 0 ? Color.loss : Color.primary)
                if a.balanceBase != nil, let ccy = a.currency, ccy.uppercased() != currency.uppercased() {
                    Text(Fmt.money(a.balance, ccy)).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }
}
