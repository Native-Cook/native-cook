import Foundation
import CoreGraphics

/// Layout, timeline and card poses for the Card Catalog List. Every frame of the loop is a pure
/// function of the loop time.
enum CardCatalogListMotion {
    /// Loop period in seconds.
    static let loop = 9.0

    static let designW = 393.0
    static let designH = 759.0

    static let cardW = 329.0
    static let cardH = 232.0
    static let cardX = (designW - cardW) / 2
    /// The reading line: the bottom edge every card turns about.
    static let pivotY = 424.0
    static let drawer = (x: 14.0, y: 104.0, w: designW - 28, bottom: pivotY + 66)
    static let front = CGRect(x: 6, y: 476, width: designW - 12, height: 98)

    /// The card the demo thumbs to (Gaskell).
    static let target = 14.0
    static let backLean = 15.0
    static let forwardLean = 104.0
    static let stepUp = 13.0
    static let visibleBack = 7.0
    static let visibleFront = 4.0
    /// Drag distance that moves the drawer by one card, in points.
    static let ptPerCard = 58.0

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

    /// Where the drawer stands at loop time t, in cards: rest 0–1.4 s; thumb forward 1.4–4.6 s onto
    /// Gaskell with a small overshoot; hold 4.6–5.8 s; thumb back 5.8–7.6 s; rest.
    static func autoPosition(_ t: Double) -> Double {
        let forward = easeInOutCubic(seg(t, 1.4, 4.35))
        let settle = sin(Double.pi * seg(t, 4.1, 4.8)) * 0.24 * (1 - seg(t, 4.1, 4.8))
        let back = easeInOutCubic(seg(t, 5.8, 7.6))
        return target * forward * (1 - back) + settle
    }

    /// The lean the demo's speed gives the standing cards (the same lagging follower the finger
    /// drives), sampled at 120 Hz over its steady state, so the loop's end meets its start.
    static let leanTable: [Double] = {
        let n = Int(loop * 120)
        let dt = 1.0 / 120
        var lean = 0.0
        var out = [Double](repeating: 0, count: n)
        var before = autoPosition(0)
        for pass in 0..<2 {
            for i in 0..<n {
                let now = autoPosition(Double(i) * dt)
                let speed = abs(now - before) / dt
                before = now
                lean += (min(speed * 1.4, 12) - lean) * min(1, dt * 9)
                if pass == 1 { out[i] = lean }
            }
        }
        return out
    }()

    static func autoLean(_ t: Double) -> Double {
        let x = t * 120
        let i = Int(floor(x)) % leanTable.count
        let f = x - floor(x)
        return leanTable[i] + (leanTable[(i + 1) % leanTable.count] - leanTable[i]) * f
    }

    struct CardPose {
        var rot: Double
        var ty: Double
        var scale: Double
        var shade: Double
        var opacity: Double
        var back: Double
    }

    /// The pose of a card d cards behind the reading line (d < 0: already thumbed, lying forward).
    static func cardPose(_ d: Double, _ lean: Double) -> CardPose {
        if d >= 0 {
            let first = clamp01(d)
            let rot = (backLean + lean) * first + 1.2 * min(max(d - 1, 0), 6)
            let ty = -stepUp * d
            let scale = 1 - 0.012 * min(d, visibleBack + 1)
            let shade = min(0.07 * d, 0.45)
            let opacity = 1 - clamp01(d - (visibleBack - 1))
            return CardPose(rot: rot, ty: ty, scale: scale, shade: shade, opacity: opacity, back: 0)
        }
        let e = clamp01(-d)
        let rot = -forwardLean * easeInOutCubic(e) - 4 * min(-d - 1 < 0 ? 0 : -d - 1, 3)
        let opacity = 1 - clamp01(-d - (visibleFront - 1))
        return CardPose(rot: rot, ty: 0, scale: 1, shade: 0.12 * e, opacity: opacity, back: rot < -90 ? 1 : 0)
    }

    /// Soft limits: past either end the drawer gives way slowly.
    static func rubberBand(_ s: Double, _ maxS: Double) -> Double {
        if s < 0 { return -(-s / (1 + -s / 0.9)) * 0.55 }
        if s > maxS {
            let over = s - maxS
            return maxS + (over / (1 + over / 0.9)) * 0.55
        }
        return s
    }

    struct Card {
        let surname: String
        let given: String
        let title: String
        let call: String
        let publisher: String
        let year: Int
        let pages: Int
        let tab: Int
        let status: Int
    }

    static let statusLabel = ["On shelf", "On loan", "Reading room"]

    private static let publishers = ["Harrow & Finch", "Mill Lane Press", "Copperleaf Books", "Weir & Daughters", "North Quay"]
    private static let raw: [(String, String, String)] = [
        ("Fairley", "Ines", "The Orchard Ledger"), ("Falk", "Tomas", "Winter Ferry"), ("Farrow", "June", "A House of Small Weather"),
        ("Faulk", "Daniel", "The Glass Cartographer"), ("Fenn", "Odile", "Saltmarsh Hours"), ("Fern", "Ruth", "The Night Telegraph"),
        ("Finch", "Ada", "Paper Lanterns"), ("Fisk", "Owen", "The Copper Bell"), ("Flint", "Margo", "Rooms Above the Bakery"),
        ("Ford", "Elias", "The Long Quiet"), ("Frey", "Nadia", "A Field Guide to Leaving"), ("Frost", "Colm", "The Ninth Lighthouse"),
        ("Gale", "Simone", "The Weather Clock"), ("Garnet", "Theo", "Small Hours"), ("Gaskell", "Mara", "The Lantern Weir"),
        ("Gilroy", "Pen", "Under the Plane Trees"), ("Glass", "Henry", "The Cartwright Letters"), ("Glenn", "Ottilie", "A Room of Blue Tiles"),
        ("Goode", "Ravi", "The Tidewater Year"), ("Gordon", "Lise", "North of the River"), ("Grace", "Wim", "The Clockmaker’s Niece"),
        ("Gray", "Edda", "Lamplight"), ("Greer", "Basil", "The Quiet Engine"), ("Grove", "Hana", "Wild Plum"),
    ]

    /// The drawer's cards: fiction from F to G, every name and title invented.
    static let cards: [Card] = raw.enumerated().map { i, r in
        Card(surname: r.0, given: r.1, title: r.2, call: "F\n\(r.0.prefix(3).uppercased())", publisher: publishers[(i * 3) % publishers.count],
             year: 1948 + (i * 17) % 61, pages: 168 + (i * 53) % 260, tab: i % 3, status: i % 5 == 3 ? 1 : i % 7 == 5 ? 2 : 0)
    }
}
