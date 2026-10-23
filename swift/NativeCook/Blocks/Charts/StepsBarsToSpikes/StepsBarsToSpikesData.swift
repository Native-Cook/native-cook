import Foundation

/// One switch position of the chart: a bar per day or week, the last one being now.
struct StepsBarsToSpikesView {
    /// The switch's letter.
    var key: String
    /// Steps per bar, oldest first.
    var values: [Double]
    /// A label under each bar; an empty one leaves the bar unlabelled.
    var labels: [String]
    /// Each bar's name, shown under the number while a finger is on it.
    var names: [String]
    /// The bar that is now, drawn solid.
    var current: Int
    /// The top of the value axis.
    var max: Double
    /// Steps in the same stretch of the period before, for the change chip.
    var previous: Double
    /// The total of this period, which the big number shows.
    var total: Double
    var changeLabel: String
    /// The small figure at the top left and what it means.
    var note: Double
    var noteLabel: String
    /// Shown at the top right of the chart, e.g. the month.
    var badge: String? = nil
}

enum StepsBarsToSpikesData {
    // October 2026 up to today, the 13th.
    private static let october: [Double] = [4400, 5900, 7600, 6200, 9100, 7800, 7020, 8730, 4050, 3600, 0, 4950, 2369]
    private static let monthDays = 31
    private static let dailyGoal = 5132.0

    private static func sum(_ values: ArraySlice<Double>) -> Double { values.reduce(0, +) }

    private static let lastWeek = Array(october.suffix(7))
    private static let thisWeek = sum(october[11...])

    static let views: [StepsBarsToSpikesView] = [
        StepsBarsToSpikesView(
            key: "D",
            values: lastWeek,
            labels: ["Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue"],
            names: ["Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "Monday", "Today"],
            current: 6,
            max: 9000,
            previous: lastWeek[5],
            total: lastWeek[6],
            changeLabel: "than previous day",
            note: dailyGoal - lastWeek[6],
            noteLabel: "Steps behind"
        ),
        StepsBarsToSpikesView(
            key: "W",
            values: [46200, 51800, 39400, 55600, 48900, sum(october[4..<11]), thisWeek],
            labels: ["8/31", "9/7", "9/14", "9/21", "9/28", "10/5", "10/12"],
            names: ["Week of Aug 31", "Week of Sep 7", "Week of Sep 14", "Week of Sep 21", "Week of Sep 28", "Week of Oct 5", "This week"],
            current: 6,
            max: 63000,
            previous: sum(october[4..<6]),
            total: thisWeek,
            changeLabel: "than previous week",
            note: round(thisWeek / 2),
            noteLabel: "Avg. steps per day"
        ),
        StepsBarsToSpikesView(
            key: "M",
            values: october + Array(repeating: 0, count: monthDays - october.count),
            labels: (0..<monthDays).map { $0 % 4 == 0 ? String(format: "%02d", $0 + 1) : "" },
            names: (0..<monthDays).map { $0 == october.count - 1 ? "Today" : "Oct \($0 + 1)" },
            current: october.count - 1,
            max: 10000,
            previous: 84600,
            total: sum(october[...]),
            changeLabel: "than previous month",
            note: round(sum(october[...]) / Double(october.count)),
            noteLabel: "Avg. steps per day",
            badge: "OCT '26"
        ),
    ]

    // What the steps come to, at an average stride and weight.
    private static let kmPerStep = 0.00071
    private static let kcalPerStep = 0.0904
    private static let stepsPerFloor = 590.0

    /// JavaScript's Math.round: halves go up.
    static func round(_ value: Double) -> Double { (value + 0.5).rounded(.down) }

    static func stats(_ steps: Double) -> (distance: String, calories: String, floors: String) {
        (
            String(format: "%.2f", steps * kmPerStep),
            grouped(String(format: "%.1f", steps * kcalPerStep)),
            String(Int(round(steps / stepsPerFloor)))
        )
    }

    /// "87291" → "87,291", keeping any decimals.
    static func grouped(_ value: String) -> String {
        let parts = value.split(separator: ".", omittingEmptySubsequences: false)
        var whole = ""
        for (i, character) in parts[0].enumerated() {
            if i > 0 && (parts[0].count - i) % 3 == 0 { whole.append(",") }
            whole.append(character)
        }
        return parts.count > 1 ? whole + "." + parts[1] : whole
    }

    static func grouped(_ value: Double) -> String { grouped(String(Int(value))) }

    /// An axis value: 8100 → "8.1K", 900 → "900".
    static func axisLabel(_ value: Double) -> String {
        if value < 1000 { return String(Int(round(value))) }
        var text = String(format: "%.1f", value / 1000)
        if text.hasSuffix(".0") { text.removeLast(2) }
        return text + "K"
    }
}
