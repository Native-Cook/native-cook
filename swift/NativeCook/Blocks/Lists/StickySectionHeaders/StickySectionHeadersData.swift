import SwiftUI

/// Ledger's activity history (invented): day sections with their payments, newest first.
struct StickySectionHeadersTxn: Identifiable {
    let id: String
    let name: String
    let meta: String
    /// Cents; negative = money out, positive = money in.
    let cents: Int
    let tint: (Color, Color)
    var initial: String { String(name.prefix(1)) }
}

struct StickySectionHeadersSection: Identifiable {
    let id: String
    let label: String
    let month: Int
    let txns: [StickySectionHeadersTxn]

    /// Money out only (the day's "spent"), in cents.
    var spent: Int { txns.reduce(0) { $0 + ($1.cents < 0 ? -$1.cents : 0) } }
    var countText: String { txns.count == 1 ? "1 payment" : "\(txns.count) payments" }
}

enum StickySectionHeadersData {
    static let months = ["Nov", "Oct", "Sep", "Aug", "Jul", "Jun"]
    static let monthNames = ["November", "October", "September", "August", "July", "June"]

    private static func hex(_ v: UInt32) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255)
    }

    private static let food = (hex(0xf3a65a), hex(0xd9733a))
    private static let coffee = (hex(0xb98a64), hex(0x8a5d3f))
    private static let transit = (hex(0x6aa3e8), hex(0x3b6fc4))
    private static let home = (hex(0x8fc5a2), hex(0x4f936b))
    private static let fun = (hex(0xe58fb0), hex(0xbf5683))
    private static let health = (hex(0x7fcbd0), hex(0x3e979f))
    private static let bills = (hex(0xa7a1e6), hex(0x6c63c4))
    private static let income = (hex(0x9fd88a), hex(0x4ea33c))

    private static func t(_ id: String, _ name: String, _ meta: String, _ dollars: Double, _ tint: (Color, Color)) -> StickySectionHeadersTxn {
        StickySectionHeadersTxn(id: id, name: name, meta: meta, cents: Int((dollars * 100).rounded()), tint: tint)
    }

    static let sections: [StickySectionHeadersSection] = [
        .init(id: "d1124", label: "Today", month: 0, txns: [
            t("a1", "Lumen Coffee", "Coffee · 8:12 am", -5.4, coffee),
            t("a2", "Northway Transit", "Transport · 8:40 am", -2.9, transit),
            t("a3", "Fernhill Market", "Groceries · 12:05 pm", -46.35, food),
            t("a4", "Saltgrass Kitchen", "Dining · 7:48 pm", -38.6, food),
        ]),
        .init(id: "d1123", label: "Yesterday", month: 0, txns: [
            t("b1", "Cinder Gym", "Health · 7:02 am", -24.0, health),
            t("b2", "Quill Books", "Shopping · 3:20 pm", -18.99, fun),
            t("b3", "Lumen Coffee", "Coffee · 4:10 pm", -4.8, coffee),
        ]),
        .init(id: "d1121", label: "Sat, Nov 21", month: 0, txns: [
            t("c1", "Harbor Pharmacy", "Health · 10:30 am", -12.75, health),
            t("c2", "Bolt & Thread", "Clothing · 1:15 pm", -64.0, fun),
            t("c3", "Fernhill Market", "Groceries · 5:40 pm", -31.2, food),
            t("c4", "Paper Lantern", "Cinema · 8:00 pm", -26.5, fun),
            t("c5", "Northway Transit", "Transport · 11:20 pm", -14.3, transit),
        ]),
        .init(id: "d1118", label: "Wed, Nov 18", month: 0, txns: [
            t("e1", "Brightline Studio", "Salary · 9:00 am", 3240.0, income),
            t("e2", "Pebble Mobile", "Bills · 9:30 am", -29.0, bills),
            t("e3", "Saltgrass Kitchen", "Dining · 1:05 pm", -17.45, food),
        ]),
        .init(id: "d1030", label: "Fri, Oct 30", month: 1, txns: [
            t("f1", "Orchard Lane Homes", "Rent · 8:00 am", -860.0, home),
            t("f2", "Lumen Coffee", "Coffee · 8:25 am", -5.4, coffee),
            t("f3", "Fernhill Market", "Groceries · 6:10 pm", -52.8, food),
            t("f4", "Northway Transit", "Transport · 6:45 pm", -2.9, transit),
        ]),
        .init(id: "d1025", label: "Sun, Oct 25", month: 1, txns: [
            t("g1", "Sundial Bakery", "Dining · 10:40 am", -14.2, food),
            t("g2", "Paper Lantern", "Cinema · 6:30 pm", -26.5, fun),
            t("g3", "Quill Books", "Shopping · 7:55 pm", -22.0, fun),
        ]),
        .init(id: "d1020", label: "Tue, Oct 20", month: 1, txns: [
            t("h1", "Cinder Gym", "Health · 7:00 am", -24.0, health),
            t("h2", "Gridwell Energy", "Bills · 9:00 am", -71.36, bills),
            t("h3", "Fernhill Market", "Groceries · 5:20 pm", -39.65, food),
            t("h4", "Lumen Coffee", "Coffee · 5:35 pm", -4.8, coffee),
        ]),
        .init(id: "d0928", label: "Mon, Sep 28", month: 2, txns: [
            t("i1", "Harbor Pharmacy", "Health · 11:10 am", -9.6, health),
            t("i2", "Saltgrass Kitchen", "Dining · 8:15 pm", -44.9, food),
            t("i3", "Northway Transit", "Transport · 10:40 pm", -12.1, transit),
        ]),
        .init(id: "d0917", label: "Thu, Sep 17", month: 2, txns: [
            t("j1", "Pebble Mobile", "Bills · 9:30 am", -29.0, bills),
            t("j2", "Bolt & Thread", "Clothing · 2:00 pm", -118.0, fun),
            t("j3", "Sundial Bakery", "Dining · 4:45 pm", -8.75, food),
            t("j4", "Fernhill Market", "Groceries · 6:30 pm", -27.4, food),
        ]),
        .init(id: "d0829", label: "Sat, Aug 29", month: 3, txns: [
            t("k1", "Kestrel Air", "Travel · 6:05 am", -212.0, transit),
            t("k2", "Lumen Coffee", "Coffee · 6:40 am", -6.2, coffee),
            t("k3", "Seabright Inn", "Travel · 3:00 pm", -168.0, home),
            t("k4", "Saltgrass Kitchen", "Dining · 8:30 pm", -52.3, food),
        ]),
        .init(id: "d0812", label: "Wed, Aug 12", month: 3, txns: [
            t("l1", "Gridwell Energy", "Bills · 9:00 am", -64.12, bills),
            t("l2", "Cinder Gym", "Health · 6:15 pm", -24.0, health),
            t("l3", "Fernhill Market", "Groceries · 7:05 pm", -33.9, food),
        ]),
        .init(id: "d0726", label: "Sun, Jul 26", month: 4, txns: [
            t("m1", "Tidewater Market", "Groceries · 11:20 am", -19.5, food),
            t("m2", "Paper Lantern", "Cinema · 7:10 pm", -26.5, fun),
            t("m3", "Northway Transit", "Transport · 10:05 pm", -3.4, transit),
        ]),
        .init(id: "d0709", label: "Thu, Jul 9", month: 4, txns: [
            t("n1", "Harbor Pharmacy", "Health · 9:40 am", -15.25, health),
            t("n2", "Quill Books", "Shopping · 1:30 pm", -31.0, fun),
            t("n3", "Saltgrass Kitchen", "Dining · 7:45 pm", -41.8, food),
        ]),
        .init(id: "d0630", label: "Tue, Jun 30", month: 5, txns: [
            t("o1", "Orchard Lane Homes", "Rent · 8:00 am", -860.0, home),
            t("o2", "Pebble Mobile", "Bills · 9:30 am", -29.0, bills),
            t("o3", "Sundial Bakery", "Dining · 10:15 am", -11.4, food),
            t("o4", "Lumen Coffee", "Coffee · 4:20 pm", -4.8, coffee),
        ]),
        .init(id: "d0612", label: "Fri, Jun 12", month: 5, txns: [
            t("p1", "Cinder Gym", "Health · 7:00 am", -24.0, health),
            t("p2", "Fernhill Market", "Groceries · 6:00 pm", -48.15, food),
            t("p3", "Paper Lantern", "Cinema · 9:20 pm", -26.5, fun),
        ]),
    ]

    /// "$1,234.56", with "−"/"+" when signed.
    static func formatMoney(_ cents: Int, signed: Bool) -> String {
        let a = abs(cents)
        var dollars = String(a / 100)
        var i = dollars.count - 3
        while i > 0 {
            dollars.insert(",", at: dollars.index(dollars.startIndex, offsetBy: i))
            i -= 3
        }
        let body = "$\(dollars).\(String(format: "%02d", a % 100))"
        if !signed { return body }
        return cents < 0 ? "\u{2212}\(body)" : "+\(body)"
    }
}
