import SwiftUI

/// One invented purchase. Icons are Feather names.
struct TiltSwipeActionsTransaction: Identifiable {
    let key: String
    let title: String
    let when: String
    let amount: String
    let icon: TiltSwipeActionsFeather
    let tint: Color

    var id: String { key }

    /// "-34$" -> 34: deleting a spend gives it back to the balance.
    var value: Double {
        Double(amount.filter { $0.isNumber || $0 == "." }) ?? 0
    }
}

enum TiltSwipeActionsData {
    static let balance = 1148.9

    static let spending: [TiltSwipeActionsTransaction] = [
        .init(key: "groceries", title: "Groceries", when: "Today", amount: "-34$", icon: .shoppingCart, tint: TiltSwipeActionsTint.yellow),
        .init(key: "rent", title: "Rent", when: "Today", amount: "-114$", icon: .home, tint: TiltSwipeActionsTint.lilac),
        .init(key: "coffee", title: "Corner Caf\u{00E9}", when: "Yesterday", amount: "-4.80$", icon: .coffee, tint: TiltSwipeActionsTint.peach),
        .init(key: "metro", title: "Metro card", when: "Yesterday", amount: "-25$", icon: .navigation, tint: TiltSwipeActionsTint.sky),
        .init(key: "cinema", title: "Cinema", when: "Sat", amount: "-18$", icon: .film, tint: TiltSwipeActionsTint.rose),
        .init(key: "gym", title: "Climbing gym", when: "Fri", amount: "-45$", icon: .activity, tint: TiltSwipeActionsTint.mint),
        .init(key: "books", title: "Bookshop", when: "Thu", amount: "-22$", icon: .bookOpen, tint: TiltSwipeActionsTint.yellow),
        .init(key: "pharmacy", title: "Pharmacy", when: "Wed", amount: "-12.60$", icon: .plusSquare, tint: TiltSwipeActionsTint.mint),
    ]

    /// The balance as the card prints it: one decimal, none when it is a whole number.
    static func format(_ value: Double) -> String {
        let rounded = (value * 10).rounded() / 10
        let text = String(format: "%.1f", rounded)
        return text.hasSuffix(".0") ? String(text.dropLast(2)) : text
    }
}
