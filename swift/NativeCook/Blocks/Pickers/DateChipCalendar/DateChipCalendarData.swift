import SwiftUI

/// One month of the grid. Days are serials: days since 1970-01-01 UTC.
struct DateChipCalendarMonth: Identifiable, Equatable {
    var id: String
    var title: String
    var first: Int // serial of the 1st
    var offset: Int // weekday of the 1st, Sunday = 0
    var days: Int

    var last: Int { first + days - 1 }

    static let names = ["January", "February", "March", "April", "May", "June", "July", "August",
                        "September", "October", "November", "December"]
    static let weekdays = ["S", "M", "T", "W", "T", "F", "S"]

    private static let utc: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    /// `month` is 0-based, as in JavaScript.
    static func serial(_ year: Int, _ month: Int, _ day: Int) -> Int {
        let date = utc.date(from: DateComponents(year: year, month: month + 1, day: day))!
        return Int((date.timeIntervalSince1970 / 86400).rounded())
    }

    static func months(from year: Int, _ month: Int, count: Int) -> [DateChipCalendarMonth] {
        (0..<count).map { i in
            let date = utc.date(from: DateComponents(year: year, month: month + 1 + i, day: 1))!
            let parts = utc.dateComponents([.year, .month, .weekday], from: date)
            let y = parts.year!, m = parts.month! - 1
            let days = utc.range(of: .day, in: .month, for: date)!.count
            return DateChipCalendarMonth(id: "\(y)-\(m)", title: "\(names[m]) \(y)", first: serial(y, m, 1),
                                         offset: parts.weekday! - 1, days: days)
        }
    }

    static func formatDay(_ value: Int) -> String {
        let date = Date(timeIntervalSince1970: Double(value) * 86400)
        let parts = utc.dateComponents([.month, .day], from: date)
        return "\(names[parts.month! - 1].prefix(3)) \(parts.day!)"
    }

    static func formatRange(_ start: Int, _ end: Int) -> String {
        "\(formatDay(start)) – \(formatDay(end))"
    }
}

/// The stay the demo books: Saltline Cottage, from November 2026.
enum DateChipCalendarStay {
    typealias Month = DateChipCalendarMonth

    static let months = Month.months(from: 2026, 10, count: 7)
    static let december = 1
    static let today = Month.serial(2026, 10, 24)
    static let blocked = [Month.serial(2026, 11, 3), Month.serial(2026, 11, 4), Month.serial(2026, 11, 5),
                          Month.serial(2026, 11, 26), Month.serial(2026, 11, 27)]
    static let scriptStart = Month.serial(2026, 11, 9)
    static let scriptEnd = Month.serial(2026, 11, 21)
    static let scriptLabel = Month.formatRange(scriptStart, scriptEnd)

    static let facts: [(value: String, label: String)] = [
        ("2", "Bedrooms"),
        ("4.9", "Guest rating"),
        ("40 m", "To the water"),
    ]

    static func totalLabel(nights: Int, breakfast: Bool) -> String {
        let total = nights * (DateChipCalendarMotion.rate + (breakfast ? DateChipCalendarMotion.breakfast : 0))
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: "en_US")
        let amount = formatter.string(from: NSNumber(value: total)) ?? "\(total)"
        return "$\(amount) · \(nights) \(nights == 1 ? "night" : "nights")"
    }
}

/// One continuous rounded shape through the weeks of a month.
enum DateChipCalendarRange {
    private static func rounded(_ points: [CGPoint], _ radius: Double, into path: inout Path) {
        let n = points.count
        guard n >= 3 else { return }
        for i in 0..<n {
            let p = points[i]
            let prev = points[(i + n - 1) % n]
            let next = points[(i + 1) % n]
            let inX = p.x - prev.x, inY = p.y - prev.y
            let outX = next.x - p.x, outY = next.y - p.y
            let inLen = max(hypot(inX, inY), 0.0001)
            let outLen = max(hypot(outX, outY), 0.0001)
            let r = min(radius, inLen / 2, outLen / 2)
            let a = CGPoint(x: p.x - inX / inLen * r, y: p.y - inY / inLen * r)
            let c = CGPoint(x: p.x + outX / outLen * r, y: p.y + outY / outLen * r)
            if i == 0 { path.move(to: a) } else { path.addLine(to: a) }
            path.addQuadCurve(to: c, control: p)
        }
        path.closeSubpath()
    }

    /// Drops repeated points and points on a straight line, which would round into nothing.
    private static func clean(_ points: [CGPoint]) -> [CGPoint] {
        var out: [CGPoint] = []
        for i in points.indices {
            let p = points[i]
            let prev = points[(i + points.count - 1) % points.count]
            let next = points[(i + 1) % points.count]
            let same = abs(p.x - prev.x) < 0.01 && abs(p.y - prev.y) < 0.01
            let straight = (abs(prev.x - p.x) < 0.01 && abs(p.x - next.x) < 0.01)
                || (abs(prev.y - p.y) < 0.01 && abs(p.y - next.y) < 0.01)
            if !same, !straight { out.append(p) }
        }
        return out
    }

    private static func pill(_ x0: Double, _ x1: Double, _ top: Double, _ bottom: Double) -> [CGPoint] {
        [CGPoint(x: x0, y: top), CGPoint(x: x1, y: top), CGPoint(x: x1, y: bottom), CGPoint(x: x0, y: bottom)]
    }

    /// The range from day `start` to the (fractional) day `head`, clipped to one month.
    static func path(start: Double, head: Double, month: DateChipCalendarMonth, cell: Double,
                     row: Double, inset: Double) -> Path {
        var path = Path()
        guard start >= 0 else { return path }
        let first = Double(month.first), offset = Double(month.offset)
        let lo = min(start, head), hi = max(start, head)
        let last = first + Double(month.days) - 1
        if hi < first || lo > last { return path }

        let from = max(lo, first) - first + offset
        let to = min(hi, last) - first + offset + 1 // exclusive end, fractional while the head travels
        let rowA = floor(from / 7)
        let rowB = floor((to - 0.0001) / 7)
        let xA = (from - rowA * 7) * cell
        let xB = (to - rowB * 7) * cell
        let width = cell * 7
        let radius = (row - inset * 2) / 2
        let topA = rowA * row + inset
        let bottomA = (rowA + 1) * row - inset
        let topB = rowB * row + inset
        let bottomB = (rowB + 1) * row - inset

        if rowA == rowB {
            rounded(pill(xA, xB, topA, bottomA), radius, into: &path)
            return path
        }
        // Two weeks that do not overlap cannot be joined: two pills.
        if rowB == rowA + 1, xB <= xA + radius {
            rounded(pill(xA, width, topA, bottomA), radius, into: &path)
            rounded(pill(0, xB, topB, bottomB), radius, into: &path)
            return path
        }
        rounded(clean([
            CGPoint(x: xA, y: topA),
            CGPoint(x: width, y: topA),
            CGPoint(x: width, y: topB - inset * 2),
            CGPoint(x: xB, y: topB - inset * 2),
            CGPoint(x: xB, y: bottomB),
            CGPoint(x: 0, y: bottomB),
            CGPoint(x: 0, y: bottomA + inset * 2),
            CGPoint(x: xA, y: bottomA + inset * 2),
        ]), radius, into: &path)
        return path
    }
}
