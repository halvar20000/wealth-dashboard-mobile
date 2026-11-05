import Foundation

/// The eight windows the dashboard measures a return over, and how a
/// chart draws each of them — as on Android.
///
/// The line comes from `net_worth_history`, which knows fewer windows
/// (1m, 3m, 6m, ytd, 1y, all): a day and a week are cut from the month,
/// three years from everything. Its points are daily for a month and
/// thin out beyond, so a day is the last two of them.
enum TimeWindow {
    /// Shown first, as before the windows could be picked.
    static let standard = "1y"

    /// The history the dashboard is asked for to draw a window.
    static func serverPeriod(_ key: String) -> String {
        switch key {
        case "1d", "1w", "1m": "1m"
        case "3m": "3m"
        case "ytd": "ytd"
        case "1y": "1y"
        default: "all"
        }
    }

    /// The first day a window covers, where the history asked for is
    /// wider than the window; nil where it is the window itself.
    static func cutoff(_ key: String, today: Date = Date()) -> String? {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        let start: Date?
        switch key {
        case "1d": start = calendar.date(byAdding: .day, value: -1, to: today)
        case "1w": start = calendar.date(byAdding: .day, value: -7, to: today)
        case "3y": start = calendar.date(byAdding: .year, value: -3, to: today)
        default: start = nil
        }
        guard let start else { return nil }
        let c = calendar.dateComponents([.year, .month, .day], from: start)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// The history cut to a window, never to fewer than two points while
    /// there are two: a line needs both ends.
    static func trim(_ history: History, to key: String, today: Date = Date()) -> History {
        guard let from = cutoff(key, today: today) else { return history }
        let points = (history.points ?? []).filter { $0.netWorth != nil || $0.securities != nil }
        var kept = points.filter { ($0.date ?? "") >= from }
        if kept.count < 2, points.count >= 2 { kept = Array(points.suffix(2)) }
        var out = history
        out.points = kept
        out.period = key
        out.firstDate = kept.first?.date ?? history.firstDate
        return out
    }
}
