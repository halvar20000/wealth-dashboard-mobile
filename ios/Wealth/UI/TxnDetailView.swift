import SwiftUI

/// One row, all of it: every field the dashboard has, and the two things
/// a person wants to do with a row they have just found — file it, or put
/// right what the bank wrote.
///
/// Filing here is not the triage: it goes now, and "remember" starts off,
/// because a row someone looked up on purpose is usually the exception,
/// not the rule.
struct TxnDetailView: View {
    @Environment(AppModel.self) private var model
    @State private var txn: Txn
    /// Told about every change, so the list behind shows it too.
    let onChange: (Txn) -> Void

    @State private var choosing = false
    @State private var editing = false
    @State private var saving = false
    @State private var error: String?

    init(txn: Txn, onChange: @escaping (Txn) -> Void = { _ in }) {
        _txn = State(initialValue: txn)
        self.onChange = onChange
    }

    var body: some View {
        let t = txn
        let cat = model.categories.first { $0.slug == t.category }
        List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(t.headline).font(.headline)
                    Text(Fmt.signedMoney(t.amount, t.currency))
                        .font(.title2.weight(.bold))
                        .foregroundStyle(Color.sign(t.amount))
                }
                .padding(.vertical, 4)
            }

            Section {
                Button { choosing = true } label: {
                    HStack {
                        Circle().fill(Color(hex: cat?.colour) ?? .secondary).frame(width: 12, height: 12)
                        VStack(alignment: .leading) {
                            Text("Category").font(.caption).foregroundStyle(.secondary)
                            Text(cat?.title ?? t.category.flatMap { $0.isEmpty ? nil : $0 } ?? String(localized: "No category"))
                                .foregroundStyle(Color.primary)
                        }
                        Spacer()
                        if saving { ProgressView() } else {
                            Text("Change").font(.subheadline)
                        }
                    }
                }
                .disabled(saving)
            } footer: {
                if let error { Text(error).foregroundStyle(Color.loss) }
            }

            Section {
                LabeledContent("Date", value: Fmt.day(t.txnDate))
                if let v = t.account, !v.isEmpty { LabeledContent("Account", value: v) }
                if let v = t.counterparty, !v.isEmpty { LabeledContent("Counterparty", value: v) }
                if let v = t.description, !v.isEmpty {
                    LabeledContent("Description") { Text(v).multilineTextAlignment(.trailing) }
                }
                LabeledContent("Kind", value: kindLabel(t.kind))
                LabeledContent("Amount", value: Fmt.price(t.amount, t.currency))
                if let name = t.securityName ?? t.isin {
                    LabeledContent("Security", value: [name, t.securityName != nil ? t.isin : nil]
                        .compactMap { $0 }.joined(separator: " · "))
                }
                if let v = t.quantity { LabeledContent("Quantity", value: Fmt.quantity(v)) }
                if let v = t.price { LabeledContent("Price", value: Fmt.price(v, t.currency)) }
                if let v = t.fee, v != 0 { LabeledContent("Fee", value: Fmt.price(v, t.currency)) }
                if let v = t.tax, v != 0 { LabeledContent("Tax", value: Fmt.price(v, t.currency)) }
                if let v = t.tags, !v.isEmpty {
                    LabeledContent("Tags", value: v.split(separator: ",")
                        .map { $0.trimmingCharacters(in: .whitespaces) }.joined(separator: ", "))
                }
                if let v = t.source, !v.isEmpty { LabeledContent("Source", value: v) }
                LabeledContent("Number", value: "#\(t.id)")
            }
            .font(.subheadline)
        }
        .navigationTitle("Transaction")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Edit") { editing = true }.disabled(saving)
            }
        }
        .task { await model.ensureCategories() }
        .sheet(isPresented: $choosing) {
            CategoryChooser(txn: t, categories: model.categories) { slug, remember, pattern in
                Task { await categorise(slug, remember: remember, pattern: pattern) }
            }
        }
        .sheet(isPresented: $editing) {
            TxnEditSheet(txn: t) { fields in await save(fields) }
        }
    }

    private func categorise(_ slug: String, remember: Bool, pattern: String?) async {
        let before = txn
        txn.category = slug
        onChange(txn)
        saving = true
        defer { saving = false }
        do {
            try await model.setCategory(before, to: slug, remember: remember, pattern: pattern)
            error = nil
        } catch {
            // Refused: the row is as it was.
            txn = before
            onChange(before)
            self.error = AppModel.sentence(for: error)
        }
    }

    /// Returns nil when saved, else the sentence to show in the sheet.
    private func save(_ fields: [String: Any]) async -> String? {
        if fields.isEmpty { return nil }
        do {
            let now = try await model.update(txn, fields: fields)
            txn = now
            onChange(now)
            return nil
        } catch {
            return AppModel.sentence(for: error)
        }
    }
}

/// The dashboard's kinds in words; an unknown one as it is.
func kindLabel(_ kind: String?) -> String {
    switch kind ?? "" {
    case "deposit": String(localized: "Deposit")
    case "withdrawal": String(localized: "Withdrawal")
    case "buy": String(localized: "Buy")
    case "sell": String(localized: "Sell")
    case "dividend": String(localized: "Dividend")
    case "interest": String(localized: "Interest")
    case "fee": String(localized: "Fee")
    case "tax": String(localized: "Tax")
    case "transfer": String(localized: "Transfer")
    case "other": String(localized: "Other")
    case "": "—"
    default: (kind ?? "").capitalized
    }
}

/// The three groups a category can count as, in words.
func groupLabel(_ group: String?) -> String {
    switch group ?? "" {
    case "spending": String(localized: "Spending")
    case "income": String(localized: "Income")
    case "non_spending": String(localized: "Neither (moving own money)")
    default: group ?? ""
    }
}

/// The choices, with the rule decided above them — as in the triage, but
/// off to start with.
private struct CategoryChooser: View {
    let txn: Txn
    let categories: [Category]
    let onPick: (String, Bool, String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var remember = false
    @State private var pattern = ""
    @State private var search = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Toggle("Remember as a rule", isOn: $remember)
                    if remember {
                        TextField("The rule remembers", text: $pattern)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                    }
                } footer: {
                    if remember {
                        Text("The text the next row like this one is recognised by — shorter is usually better: “Tenmanya” rather than the whole line.")
                    } else {
                        Text("This row only — nothing is remembered.")
                    }
                }
                Section {
                    ForEach(sorted) { c in
                        Button {
                            onPick(c.slug, remember, remember ? pattern : nil)
                            dismiss()
                        } label: {
                            HStack {
                                Circle().fill(Color(hex: c.colour) ?? .secondary).frame(width: 10, height: 10)
                                VStack(alignment: .leading) {
                                    Text(c.title).foregroundStyle(Color.primary)
                                    Text(groupLabel(c.group)).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if c.slug == txn.category { Image(systemName: "checkmark") }
                            }
                        }
                    }
                }
            }
            .searchable(text: $search, prompt: Text("Search categories"))
            .navigationTitle(txn.headline)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
        .onAppear {
            let who = (txn.counterparty ?? "").trimmingCharacters(in: .whitespaces)
            pattern = who.isEmpty ? String((txn.description ?? "").prefix(30)) : who
        }
        .presentationDetents([.medium, .large])
    }

    private var sorted: [Category] {
        let q = search.trimmingCharacters(in: .whitespaces)
        return categories
            .filter { q.isEmpty || $0.title.localizedCaseInsensitiveContains(q) || $0.slug.localizedCaseInsensitiveContains(q) }
            .sorted { ($0.transactions ?? 0) > ($1.transactions ?? 0) }
    }
}

/// Description, counterparty, date and amount: what a bank gets wrong.
private struct TxnEditSheet: View {
    let txn: Txn
    /// Answers nil when saved, else why not.
    let onSave: ([String: Any]) async -> String?

    @Environment(\.dismiss) private var dismiss
    @State private var description = ""
    @State private var counterparty = ""
    @State private var date = Date()
    @State private var amount = ""
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Description", text: $description, axis: .vertical)
                    TextField("Counterparty", text: $counterparty)
                    DatePicker("Date", selection: $date, displayedComponents: .date)
                    HStack {
                        TextField("Amount", text: $amount)
                            .keyboardType(.numbersAndPunctuation)
                        if let ccy = txn.currency { Text(ccy).foregroundStyle(.secondary) }
                    }
                } footer: {
                    if let error { Text(error).foregroundStyle(Color.loss) }
                }
            }
            .navigationTitle("Edit")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() } else { Button("Save") { Task { await save() } } }
                }
            }
        }
        .onAppear {
            description = txn.description ?? ""
            counterparty = txn.counterparty ?? ""
            date = TxnEditSheet.parse(txn.txnDate) ?? Date()
            amount = TxnEditSheet.show(txn.amount ?? 0)
        }
    }

    private func save() async {
        guard let value = TxnEditSheet.amount(amount) else {
            error = String(localized: "That is not an amount.")
            return
        }
        // Only what changed goes: a field sent unchanged is still a
        // correction on the dashboard's side.
        var fields: [String: Any] = [:]
        let d = description.trimmingCharacters(in: .whitespacesAndNewlines)
        if d != (txn.description ?? "").trimmingCharacters(in: .whitespacesAndNewlines) { fields["description"] = d }
        let c = counterparty.trimmingCharacters(in: .whitespacesAndNewlines)
        if c != (txn.counterparty ?? "").trimmingCharacters(in: .whitespacesAndNewlines) { fields["counterparty"] = c }
        let day = TxnEditSheet.iso(date)
        if day != String((txn.txnDate ?? "").prefix(10)) { fields["txn_date"] = day }
        if abs(value - (txn.amount ?? 0)) > 0.0001 { fields["amount"] = value }
        saving = true
        defer { saving = false }
        if let why = await onSave(fields) {
            error = why
        } else {
            dismiss()
        }
    }

    private static let isoFormat: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        // The phone's own zone, both ways: the day the picker shows is
        // the day meant.
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func parse(_ iso: String?) -> Date? {
        guard let iso, iso.count >= 10 else { return nil }
        return isoFormat.date(from: String(iso.prefix(10)))
    }

    static func iso(_ date: Date) -> String { isoFormat.string(from: date) }

    static func show(_ value: Double) -> String {
        let f = NumberFormatter()
        f.locale = .current
        f.numberStyle = .decimal
        f.usesGroupingSeparator = false
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        return f.string(from: NSNumber(value: value)) ?? String(value)
    }

    static func amount(_ raw: String) -> Double? { parseAmount(raw) }
}

/// "-12,50", "−12.50", "1.234,56", "1234" — the phone's own way or the
/// other one; the last separator is the decimal one.
func parseAmount(_ raw: String) -> Double? {
    var s = raw.trimmingCharacters(in: .whitespaces)
        .replacingOccurrences(of: "−", with: "-")
        .replacingOccurrences(of: " ", with: "")
        .replacingOccurrences(of: "\u{00a0}", with: "")
    if s.isEmpty { return nil }
    let comma = s.lastIndex(of: ","), dot = s.lastIndex(of: ".")
    if let comma, dot.map({ comma > $0 }) ?? true {
        s = s.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: ".")
    } else {
        s = s.replacingOccurrences(of: ",", with: "")
    }
    return Double(s)
}
