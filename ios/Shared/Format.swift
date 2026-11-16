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

/// The number format picked in Settings: "" for the phone's own, else a
/// key of `Fmt.shapes`. Observable, so a view that wrote an amount draws
/// it again when the choice changes; the widget reads the Keychain copy.
@Observable
final class NumberShape {
    static let shared = NumberShape()
    var code: String

    init(code: String? = nil) {
        self.code = code ?? Store().numberFormat
    }

    func set(_ code: String) {
        let valid = Fmt.shapes[code] == nil ? "" : code
        Store().numberFormat = valid
        self.code = valid
    }
}

/// Numbers as the phone's language writes them, in the money's own unit
/// (contract rule 3): always with its cents, so 3 300 € and 200,30 €
/// read alike — "3.300,00 €". Settings can pick other separators.
enum Fmt {
    /// The separators a user can pick instead of the phone's: group and
    /// decimal. The same four as the dashboard's setting.
    static let shapes: [String: (group: Character, decimal: Character)] = [
        "de": (".", ","),
        "en": (",", "."),
        "fr": ("\u{202F}", ","),
        "ch": ("\u{2019}", "."),
    ]
    /// The order Settings offers them in.
    static let shapeOrder = ["de", "en", "fr", "ch"]

    /// "1.234,56": how a shape is offered in Settings.
    static func example(_ code: String) -> String {
        guard let s = shapes[code] else { return "" }
        return "1\(s.group)234\(s.decimal)56"
    }

    /// The phone's text with the picked separators. Only a mark between
    /// two digits is a separator, so "kr." or "Fr." keep their dot.
    static func reshape(_ text: String, _ f: NumberFormatter, _ shape: String) -> String {
        guard let s = shapes[shape] else { return text }
        let decimals = Set([f.decimalSeparator, f.currencyDecimalSeparator].compactMap { $0 }.flatMap { Array($0) })
        let chars = Array(text)
        var out = ""
        for (i, c) in chars.enumerated() {
            let between = i > 0 && i < chars.count - 1 && chars[i - 1].isNumber && chars[i + 1].isNumber
            if !between || c.isNumber { out.append(c) }
            else { out.append(decimals.contains(c) ? s.decimal : s.group) }
        }
        return out
    }

    static func money(_ value: Double?, _ currency: String?, locale: Locale = .current, shape: String = NumberShape.shared.code) -> String {
        guard let value else { return "—" }
        let f = NumberFormatter()
        f.locale = locale
        f.numberStyle = .currency
        f.currencyCode = (currency ?? "EUR").uppercased()
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        return reshape(f.string(from: NSNumber(value: value)) ?? "\(value)", f, shape)
    }

    /// A unit price: always with its decimals, whatever its size — a
    /// share at 1 234,56 € is not "1 235 €".
    static func price(_ value: Double?, _ currency: String?, locale: Locale = .current, shape: String = NumberShape.shared.code) -> String {
        guard let value else { return "—" }
        let f = NumberFormatter()
        f.locale = locale
        f.numberStyle = .currency
        f.currencyCode = (currency ?? "EUR").uppercased()
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = abs(value) < 10 ? 4 : 2
        return reshape(f.string(from: NSNumber(value: value)) ?? "\(value)", f, shape)
    }

    /// A number of shares: whole where it is whole, up to four places
    /// where a savings plan has made it fractional.
    static func quantity(_ value: Double?, locale: Locale = .current, shape: String = NumberShape.shared.code) -> String {
        guard let value else { return "—" }
        let f = NumberFormatter()
        f.locale = locale
        f.numberStyle = .decimal
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = 4
        return reshape(f.string(from: NSNumber(value: value)) ?? "\(value)", f, shape)
    }

    static func signedMoney(_ value: Double?, _ currency: String?, locale: Locale = .current,
                            shape: String = NumberShape.shared.code) -> String {
        guard let value else { return "—" }
        return (value >= 0 ? "+" : "−") + money(abs(value), currency, locale: locale, shape: shape)
    }

    static func percent(_ fraction: Double?, locale: Locale = .current, shape: String = NumberShape.shared.code) -> String {
        guard let fraction else { return "—" }
        let f = NumberFormatter()
        f.locale = locale
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.positivePrefix = "+"
        return reshape(f.string(from: NSNumber(value: fraction * 100)) ?? "\(fraction * 100)", f, shape) + " %"
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
