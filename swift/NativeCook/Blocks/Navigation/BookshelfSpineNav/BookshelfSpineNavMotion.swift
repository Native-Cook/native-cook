import SwiftUI

/// Timeline, poses and the shelf's books for the Bookshelf Spine Nav. Every frame of the loop is a
/// pure function of the loop time.
enum BookshelfSpineNavMotion {
    /// Loop period in seconds.
    static let loop = 9.0
    /// Length of the pull-and-open sequence, in seconds of "open time" τ.
    static let open = 2.6

    static let designW = 393.0
    static let designH = 759.0
    /// Top of the shelf board: every book stands on it.
    static let boardY = 700.0
    private static let gap = 2.0

    struct Row {
        let title: String
        let author: String
        let progress: Double
        let tone: (UInt32, UInt32)
    }

    enum Cloth { case cloth, leather, linen }

    struct Book {
        let section: String
        let w: Double
        let h: Double
        let color: UInt32
        let foil: UInt32
        let binding: Cloth
        let count: Int
        let blurb: String
        let rows: [Row]
    }

    static let books: [Book] = [
        Book(section: "Novels", w: 40, h: 196, color: 0x2F5243, foil: 0xD9B663, binding: .cloth, count: 41, blurb: "41 books · 2 in progress", rows: [
            Row(title: "The Salt Orchard", author: "Ines Calloway", progress: 0.64, tone: (0xC96F4A, 0x5B2A2E)),
            Row(title: "Harbour Lights", author: "Teo Marchetti", progress: 0.12, tone: (0x3F6E8C, 0x1C2B3E)),
            Row(title: "A Year of Small Rain", author: "Juno Vale", progress: 1, tone: (0x8DA36A, 0x2F4030)),
        ]),
        Book(section: "Essays", w: 40, h: 210, color: 0x6E2430, foil: 0xE2C072, binding: .leather, count: 12, blurb: "12 books · 3 in progress", rows: [
            Row(title: "On Walking Slowly", author: "Mira Okonjo", progress: 0.38, tone: (0xD9A24E, 0x6B3A1F)),
            Row(title: "The Kitchen Window", author: "Paul Arden-Hale", progress: 0.81, tone: (0x6F8FA6, 0x26364A)),
            Row(title: "Notes from a Ferry", author: "Sana Iqbal", progress: 0.07, tone: (0xB85C6C, 0x45202C)),
        ]),
        Book(section: "Poetry", w: 32, h: 178, color: 0x26365A, foil: 0xC9D1DE, binding: .cloth, count: 19, blurb: "19 books · 1 in progress", rows: [
            Row(title: "Lantern Hours", author: "Elsa Brandt", progress: 0.5, tone: (0xE0B64F, 0x5A4414)),
            Row(title: "Field Recordings", author: "Kofi Mensah-Lowe", progress: 1, tone: (0x7CA08B, 0x24392F)),
            Row(title: "Blue Hour Psalms", author: "Noor Haddad", progress: 0.22, tone: (0x5966A8, 0x1E2448)),
        ]),
        Book(section: "Notes", w: 42, h: 168, color: 0xB8862F, foil: 0x3A2510, binding: .linen, count: 86, blurb: "86 highlights · 9 books", rows: [
            Row(title: "“Rivers keep their own time.”", author: "The Salt Orchard, p. 112", progress: 1, tone: (0xC96F4A, 0x5B2A2E)),
            Row(title: "“Begin where your feet are.”", author: "On Walking Slowly, p. 9", progress: 1, tone: (0xD9A24E, 0x6B3A1F)),
            Row(title: "“Light is a kind of weather.”", author: "Lantern Hours, p. 31", progress: 1, tone: (0xE0B64F, 0x5A4414)),
        ]),
        Book(section: "Saved", w: 36, h: 202, color: 0x4E2F52, foil: 0xE0BE6E, binding: .cloth, count: 27, blurb: "27 books saved for later", rows: [
            Row(title: "The Copper Bell", author: "Owen Fisk", progress: 0, tone: (0xB9784A, 0x4A2A18)),
            Row(title: "North of the River", author: "Lise Gordon", progress: 0, tone: (0x6F9AA0, 0x203A3E)),
            Row(title: "Wild Plum", author: "Hana Grove", progress: 0, tone: (0x9C5A7E, 0x3A1D30)),
        ]),
        Book(section: "History", w: 40, h: 186, color: 0x8A5A35, foil: 0xF0CF83, binding: .leather, count: 15, blurb: "15 books · 1 in progress", rows: [
            Row(title: "The Canal Builders", author: "R. T. Ashdown", progress: 0.44, tone: (0x8E7A5A, 0x2F271A)),
            Row(title: "Tea, Silk and Salt", author: "Wen Huang", progress: 0, tone: (0xB44A3C, 0x3E1914)),
            Row(title: "Map of a Vanished Sea", author: "Ilse Varga", progress: 1, tone: (0x4E7E9E, 0x1A2E3E)),
        ]),
        Book(section: "Lent", w: 30, h: 160, color: 0x2E6466, foil: 0xD3DCD8, binding: .cloth, count: 4, blurb: "4 books with friends", rows: [
            Row(title: "The Long Quiet", author: "with Dara · due 3 Nov", progress: 0, tone: (0x5E7F6E, 0x1E2E26)),
            Row(title: "Small Hours", author: "with Felix · due 9 Nov", progress: 0, tone: (0xC78B5A, 0x4A2C18)),
            Row(title: "Paper Lanterns", author: "with Ama · due 21 Nov", progress: 0, tone: (0xD0A452, 0x4E3A12)),
        ]),
        Book(section: "Audio", w: 38, h: 192, color: 0x34353B, foil: 0xD69A6A, binding: .leather, count: 8, blurb: "8 audiobooks · 6 h 20 min left", rows: [
            Row(title: "The Weather Clock", author: "read by June Ellery · 4 h", progress: 0.71, tone: (0x6E7FA8, 0x232A44)),
            Row(title: "Lamplight", author: "read by Theo Rask · 7 h", progress: 0.18, tone: (0xD49A4C, 0x4A3014)),
            Row(title: "Saltmarsh Hours", author: "read by Odile Fenn · 5 h", progress: 1, tone: (0x7FA38F, 0x263A30)),
        ]),
        Book(section: "Wishlist", w: 38, h: 174, color: 0x9A4E5A, foil: 0xF2E2C4, binding: .linen, count: 33, blurb: "33 books · 4 on sale", rows: [
            Row(title: "The Ninth Lighthouse", author: "Colm Frost", progress: 0, tone: (0x4F7A9A, 0x1A2A3A)),
            Row(title: "A Room of Blue Tiles", author: "Ottilie Glenn", progress: 0, tone: (0x3F6FB0, 0x16284A)),
            Row(title: "Rooms Above the Bakery", author: "Margo Flint", progress: 0, tone: (0xC9925A, 0x4C301A)),
        ]),
    ]

    /// The book the self-playing demo pulls.
    static let demoBook = 1

    /// Left edge of each spine on the shelf.
    static let bookX: [Double] = {
        let row = books.reduce(0) { $0 + $1.w } + gap * Double(books.count - 1)
        var xs: [Double] = []
        for (i, b) in books.enumerated() { xs.append(i == 0 ? (designW - row) / 2 : xs[i - 1] + books[i - 1].w + gap) }
        return xs
    }()

    /// Front cover width of each book (a share of its height).
    static let coverW: [Double] = books.map { ($0.h * 0.64).rounded() }

    /// Where the pulled book is held, cover out, before it opens, and how much bigger it is there.
    static let hold = (x: designW / 2, y: 282.0, scale: 1.48)
    /// The section page the open book becomes.
    static let page = CGRect(x: 16, y: 102, width: designW - 32, height: 364)

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutBack(_ x: Double) -> Double {
        let c = 1.4
        return 1 + (c + 1) * pow(x - 1, 3) + c * pow(x - 1, 2)
    }

    /// Open time τ at loop time t: rest 0–1.2 s; pull and open 1.2–3.8 s; hold 3.8–5.4 s; close and
    /// slide back 5.4–7.2 s; the books settle 7.2–8.0 s; rest.
    static func autoTau(_ t: Double) -> Double {
        if t < 1.2 { return 0 }
        if t < 3.8 { return t - 1.2 }
        if t < 5.4 { return open }
        return open * (1 - seg(t, 5.4, 7.2))
    }

    /// The settling wobble of the neighbours once the book is back, 7.2–8.6 s; zero elsewhere.
    static func autoWobble(_ t: Double) -> Double {
        if t < 7.2 || t > 8.6 { return 0 }
        let u = t - 7.2
        return exp(-u * 4.2) * sin(u * 15) * (1 - seg(t, 8.0, 8.6))
    }

    struct BookPose {
        var tilt: Double
        var turn: Double
        var open: Double
        var dx: Double
        var dy: Double
        var scale: Double
        var alpha: Double
        var grow: Double
        var text: Double
        var gap: Double
    }

    /// The pulled book at open time τ (0 … open).
    static func bookPose(_ tau: Double, _ index: Int) -> BookPose {
        let b = books[index]
        let tip = easeOutBack(seg(tau, 0, 0.5))
        let lift = easeInOutCubic(seg(tau, 0.4, 1.6))
        let turn = 90 * easeInOutCubic(seg(tau, 1.0, 1.6))
        let opened = easeInOutCubic(seg(tau, 1.6, 2.25))
        let tilt = -16 * tip * (1 - easeInOutCubic(seg(tau, 0.6, 1.2)))
        let scale = 1 + (hold.scale - 1) * lift
        let cw = (b.h * 0.64).rounded()
        let ox = bookX[index] + b.w / 2
        let oy = boardY
        let dx = (hold.x - (ox + hold.scale * (b.w / 2 + cw / 2))) * lift
        let dy = (hold.y - (oy - hold.scale * (b.h / 2))) * lift - 10 * tip * (1 - lift)
        return BookPose(tilt: tilt, turn: turn, open: opened, dx: dx, dy: dy, scale: scale,
                        alpha: 1 - seg(tau, 2.3, 2.55), grow: easeInOutCubic(seg(tau, 1.9, 2.6)),
                        text: seg(tau, 2.25, 2.6), gap: easeOutCubic(seg(tau, 0.35, 1.1)))
    }

    /// How a book on the shelf leans when the book `active` is out: toward the gap.
    static func neighbourLean(_ index: Int, _ active: Int, _ gap: Double, _ wobble: Double) -> Double {
        let d = index - active
        if d == 0 { return 0 }
        let reach = abs(d) == 1 ? 1.0 : abs(d) == 2 ? 0.28 : 0
        let dir = d < 0 ? 1.0 : -1.0
        return dir * reach * (6.5 * gap + 2.6 * wobble)
    }

    /// Where the page starts, on the open book's cover, in the design box.
    static func coverRect(_ index: Int) -> CGRect {
        let b = books[index]
        let cw = (b.h * 0.64).rounded() * hold.scale
        let ch = b.h * hold.scale
        return CGRect(x: hold.x - cw / 2, y: hold.y - ch / 2, width: cw, height: ch)
    }

    /// Lightens (amount > 0, toward a warm white) or darkens (amount < 0, toward a warm black) a colour.
    static func shade(_ hex: UInt32, _ amount: Double) -> Color {
        let c = [Double((hex >> 16) & 255), Double((hex >> 8) & 255), Double(hex & 255)]
        let to: [Double] = amount > 0 ? [255, 244, 226] : [14, 9, 6]
        let a = min(1, abs(amount))
        let out = (0..<3).map { (c[$0] + (to[$0] - c[$0]) * a).rounded() }
        return Color(.sRGB, red: out[0] / 255, green: out[1] / 255, blue: out[2] / 255)
    }
}
