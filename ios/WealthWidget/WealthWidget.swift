import SwiftUI
import WidgetKit

/// The figure on the Home screen.
///
/// It draws the **cached** snapshot and nothing else: a widget that waits
/// for a network is a widget that shows a spinner all morning. The cache
/// is the one the app keeps (read through the shared Keychain), so the
/// number here and the number in the app are the same number, with the
/// time it was read under it. The app asks for a redraw whenever it has
/// fresh figures; tapping the widget opens the app, which fetches.
@main
struct WealthWidgets: WidgetBundle {
    var body: some Widget {
        NetWorthWidget()
    }
}

struct NetWorthWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NetWorth", provider: CacheProvider()) { entry in
            NetWorthView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Net worth")
        .description("The figure from the app's last refresh.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

struct CacheEntry: TimelineEntry {
    let date: Date
    let readAt: Date?
    let snapshot: Snapshot?
    let excluded: Set<String>
}

struct CacheProvider: TimelineProvider {
    func placeholder(in context: Context) -> CacheEntry {
        var s = Snapshot()
        s.baseCurrency = "EUR"
        s.netWorth = NetWorth(netWorth: 123_456)
        return CacheEntry(date: Date(), readAt: Date(), snapshot: s, excluded: [])
    }

    func getSnapshot(in context: Context, completion: @escaping (CacheEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : read())
    }

    /// One entry, no schedule: the app reloads the timeline when it has
    /// something new, and the widget never fetches for itself.
    func getTimeline(in context: Context, completion: @escaping (Timeline<CacheEntry>) -> Void) {
        completion(Timeline(entries: [read()], policy: .never))
    }

    private func read() -> CacheEntry {
        let store = Store()
        let cached = store.cached()
        return CacheEntry(date: Date(), readAt: cached?.at, snapshot: cached?.snapshot,
                          excluded: store.excludedClasses)
    }
}

struct NetWorthView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CacheEntry

    var body: some View {
        let s = entry.snapshot
        let ccy = s?.currency ?? "EUR"
        let figure = Fmt.money(s?.figure(excluding: entry.excluded), ccy)
        switch family {
        case .accessoryInline:
            Text(figure)
        case .accessoryRectangular:
            VStack(alignment: .leading) {
                Text("Net worth").font(.caption2)
                Text(figure).font(.headline).minimumScaleFactor(0.6)
                if let at = entry.readAt { Text(at, style: .relative).font(.caption2) }
            }
        default:
            VStack(alignment: .leading, spacing: 4) {
                Text("Net worth").font(.caption).foregroundStyle(.secondary)
                Text(figure)
                    .font(.system(size: 26, weight: .bold))
                    .minimumScaleFactor(0.5).lineLimit(1)
                let day = s?.performance?["1d"]?.twr
                let month = s?.performance?["1m"]?.twr
                HStack(spacing: 6) {
                    if let day { Text("today \(Fmt.percent(day))").foregroundStyle(Color.sign(day)) }
                    if let month, family != .systemSmall || day == nil {
                        Text("30 d \(Fmt.percent(month))").foregroundStyle(Color.sign(month))
                    }
                }
                .font(.caption)
                let queue = (s?.waiting?.uncategorised ?? 0) + (s?.waiting?.unassignedSpending ?? 0)
                if queue > 0 {
                    Text("\(queue) waiting to be filed").font(.caption).foregroundStyle(Color.gain)
                }
                Spacer(minLength: 0)
                Group {
                    if let at = entry.readAt {
                        Text("as of \(Fmt.since(at))")
                    } else {
                        Text("Open the app once to see the figure.")
                    }
                }
                .font(.caption2).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }
}
