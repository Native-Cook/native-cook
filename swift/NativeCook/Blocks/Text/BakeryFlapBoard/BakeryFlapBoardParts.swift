import Foundation

/// A menu item: what the board spells, and what its card shows.
struct BakeryFlapBoardItem: Equatable {
    /// The name as the board spells it: capitals, up to eleven characters.
    var board: String
    /// The price as the board shows it, four characters.
    var price: String
    /// The name on the item card.
    var name: String
    /// One line about it on the card.
    var note: String
    /// When it came out of the oven, shown on the card.
    var baked: String
    /// Unsplash photo id.
    var photo: String
    /// The photo's own width ÷ height, so the card can frame it without fetching it first.
    var aspect: Double = 1.5
    /// Where the subject sits in the photo, as shares of its width and height: the card centres the
    /// frame on it as far as the photo allows.
    var focusX: Double = 0.5
    var focusY: Double = 0.5
    /// How much closer than a plain cover the card frames the photo (1 = cover).
    var zoom: Double = 1
}

enum BakeryFlapBoardText {
    /// The characters on each flap drum, in the order the flaps fall. A cell only moves forward
    /// through this list, so a change from Y to B passes through Z, the digits and the marks first.
    static let drum: [Character] = Array(" ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.'&-")
    static let columns = 16

    private static let hours = ["TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN"]
    private static let minutes: [Int: String] = [
        5: "FIVE PAST", 10: "TEN PAST", 15: "QUARTER PAST", 20: "TWENTY PAST", 25: "TWENTY FIVE PAST",
        30: "HALF PAST", 35: "TWENTY FIVE TO", 40: "TWENTY TO", 45: "QUARTER TO", 50: "TEN TO", 55: "FIVE TO",
    ]

    /// The time in words to the nearest five minutes, on two lines of the board.
    static func timeInWords(_ hours: Int, _ minutes: Int) -> [String] {
        // JavaScript's Math.round: halves round up.
        var m = Int((Double(minutes) / 5 + 0.5).rounded(.down)) * 5
        var h = hours
        if m == 60 {
            m = 0
            h += 1
        }
        if m == 0 { return ["IT IS", "\(Self.hours[h % 12]) O'CLOCK"] }
        return [Self.minutes[m]!, Self.hours[(m > 30 ? h + 1 : h) % 12]]
    }

    /// `text` centred on a row of flaps.
    static func centred(_ text: String) -> String {
        let lead = max(0, (columns - text.count) / 2)
        return pad(String(repeating: " ", count: lead) + text)
    }

    /// A menu line: the name on the left, the price on the right.
    static func menuLine(_ name: String, _ price: String) -> String {
        var line = name
        while line.count < columns - price.count { line += " " }
        return String((line + price).prefix(columns))
    }

    private static func pad(_ text: String) -> String {
        let padded = text.count < columns ? text + String(repeating: " ", count: columns - text.count) : text
        return String(padded.prefix(columns))
    }

    static func drumIndex(_ ch: Character) -> Int {
        drum.firstIndex(of: ch) ?? 0
    }
}

extension BakeryFlapBoardItem {
    static let menu: [[BakeryFlapBoardItem]] = [
        [
            .init(board: "SOURDOUGH", price: "6.40", name: "Sourdough", note: "Three-day ferment, a dark blistered crust and an open, glossy crumb.", baked: "06:40", photo: "photo-1549413468-cd78edb7e75c", aspect: 0.667, focusX: 0.5, focusY: 0.42),
            .init(board: "SEEDED RYE", price: "5.80", name: "Seeded Rye", note: "Dense dark rye with sunflower, flax and a spoon of treacle.", baked: "07:05", photo: "photo-1509440159596-0249088772ff", aspect: 1.5, focusX: 0.55, focusY: 0.5),
            .init(board: "SPELT BOULE", price: "7.20", name: "Spelt Boule", note: "Wholegrain spelt, shaped by hand and baked on the stone.", baked: "06:55", photo: "photo-1567042661848-7161ce446f85", aspect: 0.8, focusX: 0.5, focusY: 0.48),
        ],
        [
            .init(board: "BAGUETTE", price: "3.20", name: "Baguette", note: "Long-proved, with a crackling crust. Best eaten before noon.", baked: "07:10", photo: "photo-1559811814-e2c57b5e69df", aspect: 0.667, focusX: 0.5, focusY: 0.4),
            .init(board: "MILK BREAD", price: "4.60", name: "Milk Bread", note: "A soft, pillowy loaf in a paper bag, sliced to order.", baked: "06:30", photo: "photo-1622808516114-02a5749cd965", aspect: 0.667, focusX: 0.5, focusY: 0.38),
        ],
        [
            .init(board: "CROISSANT", price: "3.40", name: "Croissant", note: "Twenty-seven layers of cultured butter, laminated overnight.", baked: "06:15", photo: "photo-1546237769-6f84ec1a512a", aspect: 1, focusX: 0.5, focusY: 0.42),
            .init(board: "OAT COOKIE", price: "2.10", name: "Oat Cookie", note: "Brown butter, flaked salt and a chewy middle.", baked: "08:00", photo: "photo-1523294587484-bae6cc870010", aspect: 1.72, focusX: 0.8, focusY: 0.42, zoom: 1.7),
        ],
        [
            .init(board: "HOUSE TEA", price: "3.00", name: "House Tea", note: "A pot of black tea with bergamot, poured at the counter.", baked: "ALL DAY", photo: "photo-1638303322580-5e6aacd59c0d", aspect: 0.75, focusX: 0.6, focusY: 0.58),
            .init(board: "PEAR TART", price: "4.90", name: "Pear Tart", note: "Poached pear on almond cream, glazed with honey.", baked: "09:20", photo: "photo-1543773407-ebc74e2bc7ff", aspect: 1.5, focusX: 0.68, focusY: 0.6, zoom: 1.35),
        ],
    ]

    static let strip = [
        "photo-1608198093002-ad4e005484ec",
        "photo-1587241321921-91a834d6d191",
        "photo-1579697096985-41fe1430e5df",
        "photo-1711672284661-bd70e38f31b2",
        "photo-1568254183919-78a4f43a2877",
    ]
}

func bakeryFlapBoardPhoto(_ id: String, width: Int) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(width)&q=80"
}

/// The address of an item's card photo: wide enough for the card on a large phone, at its zoom.
func bakeryFlapBoardCardPhoto(_ item: BakeryFlapBoardItem) -> String {
    bakeryFlapBoardPhoto(item.photo, width: Int((min(1800, 1000 * max(1, item.zoom))).rounded()))
}

/// Where an item's photo sits behind the card's photo frame (w × h): scaled to cover the frame
/// (times its zoom), and moved so its subject is as near the frame's middle as the photo's edges allow.
func bakeryFlapBoardFrame(_ item: BakeryFlapBoardItem, width w: Double, height h: Double) -> CGRect {
    let aspect = item.aspect > 0 ? item.aspect : w / max(1, h)
    let zoom = max(1, item.zoom)
    let coverW = aspect > w / max(1, h) ? h * aspect : w
    let coverH = aspect > w / max(1, h) ? h : w / aspect
    let width = coverW * zoom
    let height = coverH * zoom
    let left = min(0, max(w - width, w / 2 - item.focusX * width))
    let top = min(0, max(h - height, h / 2 - item.focusY * height))
    return CGRect(x: left, y: top, width: width, height: height)
}
