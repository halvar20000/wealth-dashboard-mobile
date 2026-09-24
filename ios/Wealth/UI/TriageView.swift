import SwiftUI

/// The queue as a stack of cards, one thumb.
///
/// A browser does this badly: a table of two hundred rows with a menu on
/// each is a thing nobody finishes. A card at a time, with the
/// dashboard's own guess on the right and "leave it" on the left, is a
/// thing somebody finishes on a train — which is why a verdict is written
/// down before it is sent (contract rule 2, `AppModel.decide`).
///
/// Right: take the guess (or, with no guess, open the choices).
/// Left:  skip — the row stays in the queue, it is simply not this one.
/// Tap:   the choices, to pick something else.
struct TriageView: View {
    @Environment(AppModel.self) private var model

    @State private var owning = false
    @State private var queue: AppModel.Triage?
    @State private var index = 0
    /// Where each decision was taken, so an undo can bring its card back.
    @State private var decided: [Int] = []
    @State private var loading = false
    @State private var error: String?
    @State private var note: String?
    @State private var choosing: QueueRow?

    var body: some View {
        NavigationStack {
            VStack(spacing: 14) {
                // Two queues, one screen: what has no category, and what
                // nobody has claimed — the second only where the dashboard
                // knows about it (contract rule 6).
                if model.supports("0.72.4") {
                    Picker("Queue", selection: $owning) {
                        Text("Category").tag(false)
                        Text(whoseLabel).tag(true)
                    }
                    .pickerStyle(.segmented)
                }

                HStack {
                    Text(owning ? "Whose spending?" : "What is this?").font(.headline)
                    Spacer()
                    Text(countLine).font(.caption).foregroundStyle(.secondary)
                }

                content
                    .frame(maxHeight: .infinity, alignment: .top)
            }
            .padding()
            .navigationTitle("Triage")
            .navigationBarTitleDisplayMode(.inline)
            .task(id: owning) { await load() }
            .sheet(item: $choosing) { row in
                ChoiceSheet(row: row, owning: owning,
                            categories: queue?.categories ?? [],
                            people: queue?.people ?? []) { category, owner, pattern, remember in
                    decide(row, category: category, owner: owner, pattern: pattern, remember: remember)
                }
            }
        }
    }

    private var whoseLabel: String {
        let n = model.snapshot?.waiting?.unassignedSpending ?? 0
        return n > 0 ? String(localized: "Whose (\(n))") : String(localized: "Whose")
    }

    private var countLine: String {
        let left = max((queue?.remaining ?? 0) - index, 0)
        var parts = [String(localized: "\(left) left")]
        if model.waiting > 0 { parts.append(String(localized: "\(model.waiting) to send")) }
        return parts.joined(separator: " · ")
    }

    @ViewBuilder private var content: some View {
        let rows = queue?.rows ?? []
        if loading && rows.isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error, rows.isEmpty {
            empty(error)
        } else if index >= rows.count {
            empty((queue?.remaining ?? 0) > rows.count
                  ? String(localized: "That is all this app fetched — fetch the rest.")
                  : String(localized: "Nothing waiting. The queue is empty."))
        } else {
            let row = rows[index]
            let guess = owning ? nil : row.suggestion
            let guessLabel = guess.flatMap { g in queue?.categories.first { $0.slug == g }?.title } ?? guess
            VStack(spacing: 16) {
                SwipeCard(row: row, guessLabel: guessLabel, owning: owning,
                          onRight: { takeGuess(row) },
                          onLeft: { skip() },
                          onTap: { choosing = row })
                    .id(row.id)
                if let note {
                    Text(note).font(.footnote).foregroundStyle(.secondary)
                }
                HStack {
                    Button("Skip") { skip() }
                    Spacer()
                    Button {
                        undo()
                    } label: {
                        Label("Undo last", systemImage: "arrow.uturn.backward")
                    }
                    .disabled(model.waiting == 0 && decided.isEmpty)
                    Spacer()
                    Button {
                        choosing = row
                    } label: {
                        Label("Choose", systemImage: "checkmark")
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
    }

    private func empty(_ text: String) -> some View {
        VStack(spacing: 12) {
            Text(text).multilineTextAlignment(.center).foregroundStyle(.secondary)
            Button("Fetch again") { Task { await load() } }
                .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: Decisions

    private func takeGuess(_ row: QueueRow) {
        if !owning, let guess = row.suggestion {
            decide(row, category: guess, owner: nil, pattern: row.pattern, remember: true)
        } else {
            choosing = row
        }
    }

    private func decide(_ row: QueueRow, category: String?, owner: String?, pattern: String?, remember: Bool) {
        model.decide(Verdict(txnId: row.id, category: category, pattern: pattern,
                             remember: remember, owner: owner))
        decided.append(index)
        note = nil
        advance()
    }

    private func skip() {
        note = nil
        advance()
    }

    private func advance() {
        withAnimation(.snappy) { index += 1 }
    }

    /// Take the last verdict back — while it is still on the phone. Once
    /// the dashboard has it, saying so beats pretending.
    private func undo() {
        if model.undoLast(), let back = decided.popLast() {
            withAnimation(.snappy) { index = back }
            note = nil
        } else {
            decided.removeAll()
            note = String(localized: "That one is already with the dashboard — change it there.")
        }
    }

    private func load() async {
        loading = true
        error = nil
        defer { loading = false }
        do {
            let fresh = try await model.triage(owning: owning)
            queue = fresh
            index = 0
            decided = []
            note = nil
        } catch is CancellationError {
        } catch let url as URLError where url.code == .cancelled {
        } catch {
            self.error = AppModel.sentence(for: error)
        }
    }
}

/// One row, and a thumb. The card follows the finger and tints towards
/// what letting go would do.
private struct SwipeCard: View {
    let row: QueueRow
    let guessLabel: String?
    let owning: Bool
    let onRight: () -> Void
    let onLeft: () -> Void
    let onTap: () -> Void

    @State private var offset: CGFloat = 0
    private let decided: CGFloat = 120

    var body: some View {
        let tint: Color = offset > 40 ? .gain : offset < -40 ? .loss : .clear
        VStack(alignment: .leading, spacing: 8) {
            Text(Fmt.signedMoney(row.amount, row.currency))
                .font(.system(size: 30, weight: .bold))
                .foregroundStyle((row.amount ?? 0) < 0 ? Color.primary : Color.gain)
            Text(row.headline).font(.body)
            if let other = row.counterparty, !other.isEmpty, other != row.description {
                Text(other).font(.subheadline).foregroundStyle(.secondary)
            }
            Text([Fmt.day(row.txnDate), row.accountName].compactMap { $0 }.filter { !$0.isEmpty }
                .joined(separator: " · "))
                .font(.caption).foregroundStyle(.secondary)
            if owning, let label = row.label ?? row.category {
                Text(label).font(.caption).foregroundStyle(.secondary)
            }
            Divider().padding(.vertical, 4)
            if let guessLabel {
                Label("Swipe right: \(guessLabel)", systemImage: "arrow.right")
                    .font(.callout.weight(.semibold))
                if let pattern = row.pattern, !pattern.isEmpty {
                    Text("and remember “\(pattern)”").font(.caption).foregroundStyle(.secondary)
                }
            } else {
                Label("Tap to choose", systemImage: "hand.tap").font(.callout.weight(.semibold))
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tint.opacity(0.18), in: RoundedRectangle(cornerRadius: 20))
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.secondary.opacity(0.15)))
        .offset(x: offset)
        .rotationEffect(.degrees(Double(max(-6, min(6, offset / 25)))))
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .gesture(
            DragGesture(minimumDistance: 12)
                .onChanged { offset = $0.translation.width }
                .onEnded { value in
                    let x = value.predictedEndTranslation.width
                    if x > decided || value.translation.width > decided {
                        offset = 0; onRight()
                    } else if x < -decided || value.translation.width < -decided {
                        offset = 0; onLeft()
                    } else {
                        withAnimation(.spring) { offset = 0 }
                    }
                }
        )
        .accessibilityAction(named: "Take the guess", onRight)
        .accessibilityAction(named: "Skip", onLeft)
    }
}

/// The choices for one row, with the rule decided above them: the words
/// a rule remembers are what makes it right or wrong for the next
/// hundred rows. Off means this one row only — the correction that must
/// not become a habit.
private struct ChoiceSheet: View {
    let row: QueueRow
    let owning: Bool
    let categories: [Category]
    let people: [Person]
    /// category, owner, pattern, remember
    let onPick: (String?, String?, String?, Bool) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var remember = true
    @State private var pattern = ""
    @State private var search = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Toggle("Remember as a rule", isOn: $remember)
                    if remember && !owning {
                        TextField("The rule remembers", text: $pattern)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                    }
                } footer: {
                    if remember && !owning {
                        Text("The text the next row like this one is recognised by — shorter is usually better: “Tenmanya” rather than the whole line.")
                    } else if !remember {
                        Text("This row only.")
                    }
                }

                if owning {
                    Section {
                        ForEach(people) { p in
                            Button(p.name ?? "—") { pick(owner: String(p.id)) }
                        }
                        Button {
                            pick(owner: "shared")
                        } label: {
                            VStack(alignment: .leading) {
                                Text("Shared")
                                Text("split evenly between the household")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                } else {
                    Section {
                        ForEach(sorted) { c in
                            Button {
                                pick(category: c.slug)
                            } label: {
                                HStack {
                                    Circle().fill(Color(hex: c.colour) ?? .secondary).frame(width: 10, height: 10)
                                    VStack(alignment: .leading) {
                                        Text(c.title).foregroundStyle(Color.primary)
                                        let detail = [c.group, (c.transactions ?? 0) > 0 ? String(localized: "\(c.transactions ?? 0) rows") : nil]
                                            .compactMap { $0 }.joined(separator: " · ")
                                        if !detail.isEmpty {
                                            Text(detail).font(.caption).foregroundStyle(.secondary)
                                        }
                                    }
                                    Spacer()
                                    if c.slug == row.suggestion {
                                        Text("guess").font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .searchable(text: $search, prompt: Text("Search"))
            .navigationTitle(row.headline)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
        .onAppear { pattern = row.pattern ?? "" }
        .presentationDetents([.medium, .large])
    }

    /// The guess first, then the categories actually in use.
    private var sorted: [Category] {
        let q = search.trimmingCharacters(in: .whitespaces)
        return categories
            .filter { q.isEmpty || $0.title.localizedCaseInsensitiveContains(q) || $0.slug.localizedCaseInsensitiveContains(q) }
            .sorted {
                let a = $0.slug == row.suggestion, b = $1.slug == row.suggestion
                if a != b { return a }
                return ($0.transactions ?? 0) > ($1.transactions ?? 0)
            }
    }

    private func pick(category: String? = nil, owner: String? = nil) {
        let words = pattern.trimmingCharacters(in: .whitespaces)
        onPick(category, owner, words.isEmpty ? nil : words, remember)
        dismiss()
    }
}
