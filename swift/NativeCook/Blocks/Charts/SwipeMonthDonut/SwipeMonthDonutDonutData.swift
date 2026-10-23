import SwiftUI

struct SwipeMonthDonutCategory: Equatable {
    var name: String
    /// An emoji drawn by the system font.
    var icon: String
    var color: Color
}

struct SwipeMonthDonutMonth: Equatable {
    struct Change: Equatable {
        var amount: Double
        var percent: Int
        var up: Bool
    }

    var label: String
    /// Spent per category, in the order of `categories`; 0 leaves the category out of that month.
    var amounts: [Double]
    /// How many payments make up each amount.
    var counts: [Int]
    /// The change from the month before, shown under the total.
    var change: Change

    var total: Double { amounts.reduce(0, +) }
}

enum SwipeMonthDonutData {
    // The ring keeps this order clockwise from the top, so a category always grows out of the same place.
    static let categories = [
        SwipeMonthDonutCategory(name: "Eating Out", icon: "🍽️", color: Color(hex: 0x7165f0)),
        SwipeMonthDonutCategory(name: "Subscriptions", icon: "📅", color: Color(hex: 0xf0506a)),
        SwipeMonthDonutCategory(name: "Groceries", icon: "🥦", color: Color(hex: 0xfba05a)),
        SwipeMonthDonutCategory(name: "Clothing", icon: "👕", color: Color(hex: 0xc4c4cc)),
        SwipeMonthDonutCategory(name: "Coffee", icon: "☕", color: Color(hex: 0xdadae0)),
        SwipeMonthDonutCategory(name: "Drinks", icon: "🍹", color: Color(hex: 0xe6e6ea)),
    ]

    static let months = [
        SwipeMonthDonutMonth(
            label: "November 2024", amounts: [100, 104, 0, 0, 0, 0], counts: [4, 4, 0, 0, 0, 0],
            change: .init(amount: 51, percent: 20, up: false)
        ),
        SwipeMonthDonutMonth(
            label: "December 2024", amounts: [100, 104, 133, 63, 9, 0], counts: [4, 4, 6, 2, 3, 0],
            change: .init(amount: 205, percent: 100, up: true)
        ),
        SwipeMonthDonutMonth(
            label: "January 2025", amounts: [100, 104, 35, 63, 0, 42], counts: [5, 4, 2, 1, 0, 3],
            change: .init(amount: 65, percent: 15, up: false)
        ),
    ]

    // autoPlay, in seconds from the start of a loop: November rests, the pager swipes to December and
    // January, one segment is tapped there, then it swipes back twice.
    static let loop = 8.6
    /// Each swipe: when it starts, the page it leaves for, and how long it takes.
    static let swipes: [(at: Double, to: Double, duration: Double)] = [
        (0.9, 1, 0.7),
        (3.4, 2, 0.7),
        (6.0, 1, 0.8),
        (6.8, 0, 0.8),
    ]
    /// In January the second category is tapped and let go.
    static let focusOn = 4.6
    static let focusOff = 5.6
    static let focusCategory = 1
    /// After a touch autoPlay waits this long, settles back to the first page, and plays again.
    static let userHold = 3.0
    static let returnFor = 0.6

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func easeInOut(_ t: Double) -> Double {
        let u = clamp01(t)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    /// 0 to 1 with one soft overshoot, the shape of the pop spring, for autoPlay's tap.
    static func springOut(_ t: Double) -> Double {
        let u = clamp01(t)
        return 1 - pow(1 - u, 3) * cos(u * 4)
    }

    /// Where the pager is `t` seconds into a loop: a page number, fractional during a swipe.
    static func pageAt(_ t: Double) -> Double {
        var page = 0.0
        for (i, swipe) in swipes.enumerated() {
            if t < swipe.at { break }
            let from = i == 0 ? 0 : swipes[i - 1].to
            page = from + (swipe.to - from) * easeInOut((t - swipe.at) / swipe.duration)
        }
        return page
    }

    /// Each month's share of every category, from 0 to 1.
    static func shares(_ months: [SwipeMonthDonutMonth]) -> [[Double]] {
        months.map { month in
            let total = month.total
            return month.amounts.map { total > 0 ? $0 / total : 0 }
        }
    }
}
