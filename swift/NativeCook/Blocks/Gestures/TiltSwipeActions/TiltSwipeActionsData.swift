import Foundation

struct TiltSwipeActionsTransaction: Identifiable, Equatable {
    let key: String
    let title: String
    let category: String
    let day: String
    let amount: Double
    let icon: TiltSwipeActionsGlyphName
    var id: String { key }
}

struct TiltSwipeActionsPerson: Identifiable {
    let name: String
    let initials: String
    var id: String { initials }
}

enum TiltSwipeActionsData {
    static let balance = 1_148.90
    static let cardNumber = "4021"
    static let spending = [
        TiltSwipeActionsTransaction(key: "greenleaf", title: "Greenleaf Market", category: "Groceries", day: "Today", amount: -34, icon: .bag),
        TiltSwipeActionsTransaction(key: "harbour", title: "Harbour Electric", category: "Utilities", day: "Today", amount: -114, icon: .bolt),
        TiltSwipeActionsTransaction(key: "saigon", title: "Little Saigon Kitchen", category: "Dining", day: "Yesterday", amount: -96.60, icon: .dining),
        TiltSwipeActionsTransaction(key: "metro", title: "Metro card", category: "Transport", day: "Yesterday", amount: -25, icon: .tram),
        TiltSwipeActionsTransaction(key: "cinema", title: "Lumen Cinema", category: "Entertainment", day: "Sat", amount: -18, icon: .ticket),
        TiltSwipeActionsTransaction(key: "boulder", title: "Boulder Hall", category: "Fitness", day: "Fri", amount: -45, icon: .dumbbell),
    ]
    static let people = [
        TiltSwipeActionsPerson(name: "You", initials: "LT"),
        TiltSwipeActionsPerson(name: "Mai", initials: "MP"),
        TiltSwipeActionsPerson(name: "Leo", initials: "LN"),
        TiltSwipeActionsPerson(name: "Kai", initials: "KO"),
        TiltSwipeActionsPerson(name: "Ana", initials: "AR"),
    ]

    static func shares(_ amount: Double, count: Int) -> [Double] {
        let total = Int((abs(amount) * 100).rounded())
        let each = total / count
        return (0..<count).map { ($0 == 0 ? total - each * (count - 1) : each).asMoney }
    }

    static func money(_ value: Double, signed: Bool = true) -> (whole: String, cents: String) {
        let cents = Int((abs(value) * 100).rounded())
        let dollars = cents / 100
        let number = NumberFormatter.localizedString(from: NSNumber(value: dollars), number: .decimal)
        let minus = signed && value < 0 && cents > 0 ? "–" : ""
        return ("\(minus)$\(number)", String(format: ".%02d", cents % 100))
    }

    static func weekTotal(splitCount: Int?) -> Double {
        spending.reduce(0) { sum, item in
            if item.key == "saigon", let splitCount { return sum - shares(item.amount, count: splitCount)[0] }
            return sum + item.amount
        }
    }
}

private extension Int {
    var asMoney: Double { Double(self) / 100 }
}
