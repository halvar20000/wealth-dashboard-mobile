import SwiftUI

/// Gain and loss, the same two colours as the Android app (contract rule 7).
extension Color {
    static let gain = Color(red: 52.0 / 255, green: 211.0 / 255, blue: 153.0 / 255)   // #34D399
    static let loss = Color(red: 248.0 / 255, green: 113.0 / 255, blue: 113.0 / 255)  // #F87171

    static func sign(_ value: Double?) -> Color {
        guard let value, value != 0 else { return .secondary }
        return value > 0 ? .gain : .loss
    }
}

/// Numbers as the phone's language writes them, in the money's own unit
/// (contract rule 3): two decimals under 1 000, none above.
enum Fmt {
    static func money(_ value: Double?, _ currency: String?, locale: Locale = .current) -> String {
        guard let value else { return "—" }
        let f = NumberFormatter()
        f.locale = locale
        f.numberStyle = .currency
        f.currencyCode = (currency ?? "EUR").uppercased()
        let decimals = abs(value) < 1000 ? 2 : 0
        f.minimumFractionDigits = decimals
        f.maximumFractionDigits = decimals
        return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    /// A unit price: always with its decimals, whatever its size — a
    /// share at 1 234,56 € is not "1 235 €".
    static func price(_ value: Double?, _ currency: String?, locale: Locale = .current) -> String {
        guard let value else { return "—" }
        let f = NumberFormatter()
        f.locale = locale
        f.numberStyle = .currency
        f.currencyCode = (currency ?? "EUR").uppercased()
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = abs(value) < 10 ? 4 : 2
        return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    /// A number of shares: whole where it is whole, up to four places
    /// where a savings plan has made it fractional.
    static func quantity(_ value: Double?, locale: Locale = .current) -> String {
        guard let value else { return "—" }
        let f = NumberFormatter()
        f.locale = locale
        f.numberStyle = .decimal
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = 4
        return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    static func signedMoney(_ value: Double?, _ currency: String?, locale: Locale = .current) -> String {
        guard let value else { return "—" }
        return (value >= 0 ? "+" : "−") + money(abs(value), currency, locale: locale)
    }

    static func percent(_ fraction: Double?, locale: Locale = .current) -> String {
        guard let fraction else { return "—" }
        let f = NumberFormatter()
        f.locale = locale
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.positivePrefix = "+"
        return (f.string(from: NSNumber(value: fraction * 100)) ?? "\(fraction * 100)") + " %"
    }

    /// "2026-09-24" or a full timestamp, as a medium date.
    static func day(_ iso: String?, locale: Locale = .current) -> String {
        guard let iso, iso.count >= 10 else { return iso ?? "" }
        let parse = DateFormatter()
        parse.locale = Locale(identifier: "en_US_POSIX")
        parse.timeZone = TimeZone(identifier: "UTC")
        parse.dateFormat = "yyyy-MM-dd"
        guard let date = parse.date(from: String(iso.prefix(10))) else { return iso }
        let show = DateFormatter()
        show.locale = locale
        show.timeZone = TimeZone(identifier: "UTC")
        show.dateStyle = .medium
        show.timeStyle = .none
        return show.string(from: date)
    }

    /// "just now", "12 min ago", "3 h ago", or the day.
    static func since(_ date: Date?, now: Date = Date()) -> String {
        guard let date else { return "" }
        let seconds = now.timeIntervalSince(date)
        if seconds < 60 { return String(localized: "just now") }
        if seconds < 86_400 {
            let f = RelativeDateTimeFormatter()
            f.unitsStyle = .abbreviated
            return f.localizedString(for: date, relativeTo: now)
        }
        return date.formatted(date: .abbreviated, time: .omitted)
    }
}

extension Color {
    /// "#7c3aed" as the dashboard writes it; anything else is nil.
    init?(hex: String?) {
        guard var s = hex?.trimmingCharacters(in: .whitespaces) else { return nil }
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        self.init(red: Double((v >> 16) & 0xFF) / 255,
                  green: Double((v >> 8) & 0xFF) / 255,
                  blue: Double(v & 0xFF) / 255)
    }
}
