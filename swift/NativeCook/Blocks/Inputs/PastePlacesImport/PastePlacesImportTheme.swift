import SwiftUI

enum PastePlacesImportTheme {
    struct Word: Hashable {
        let text: String
        let x: Double
        let tracking: Double
    }

    struct TextLine: Hashable {
        let baseline: Double
        let words: [Word]
    }

    struct NoteLine: Hashable {
        let prefix: String
        let place: String
        let suffix: String
        var isCity = false
    }

    struct RectSpec {
        let x: Double
        let y: Double
        let width: Double
        let height: Double
    }

    static let referenceWidth = 1206.0
    static let referencePoints = 402.0
    static let referenceInset = 186.0
    static let anybodyAscent = 0.795
    static let anybodyDescent = 0.24
    static let systemAscent = 0.953
    static let systemDescent = 0.241

    static let textFontName = "AnybodyNC-Text"
    static let extraFontName = "AnybodyNC-ExtraBold"

    static let ink = color(0x000000)
    static let white = color(0xffffff)
    static let hint = color(0x505050)
    static let counter = color(0x5e5e5e)
    static let placeholder = color(0xc2c2c6)
    static let glyph = color(0x444444)
    static let cardBorder = color(0xb8b8b8)
    static let buttonFill = Color(red: 49 / 255, green: 49 / 255, blue: 50 / 255, opacity: 0.38)

    static let pageStops: [(Double, Color)] = [
        (0, color(0xf7f7f7)),
        (100, color(0xf5f5f5)),
        (200, color(0xf1f1f1)),
        (300, color(0xececec)),
        (500, color(0xececeb)),
        (800, color(0xebebeb)),
        (1100, color(0xededed)),
        (1400, color(0xededed)),
        (1600, color(0xebebeb)),
        (2000, color(0xebebeb)),
        (2300, color(0xe9eae9)),
        (2622, color(0xe8e9e8)),
    ]

    static let glowBand = (centerX: 603.0, centerY: 2270.0, radiusX: 960.0, radiusY: 340.0,
                           color: color(0xd9e5ee), opacity: 0.8)
    static let glowCorner = (centerX: 0.0, centerY: 2650.0, radiusX: 600.0, radiusY: 440.0,
                             color: color(0xd3e0e9), opacity: 0.95)

    enum Space {
        static let cardEdge = 98.0
        static let buttonEdge = 90.0
        static let cardPad = 89.0
        static let counterLeft = 102.0
        static let pasteGap = 33.0
    }

    enum Radius {
        static let card = 101.0
        static let button = 94.5
    }

    static let hairline = 2.0
    static let card = RectSpec(x: 98, y: 1271, width: 1010, height: 868)
    static let button = RectSpec(x: 90, y: 2337, width: 1026, height: 189)
    static let close = (x1: 1080.0, y1: 206.0, x2: 1116.0, y2: 243.0, stroke: 6.0)
    static let apps = RectSpec(x: 401, y: 371, width: 403, height: 239)
    static let clipboard = RectSpec(x: 508, y: 2010.5, width: 33.5, height: 41.5)

    static let headingSize = 25.1
    static let headingLines = [
        TextLine(baseline: 811.1, words: [
            Word(text: "import", x: 175.1, tracking: 0.77),
            Word(text: "places", x: 498.0, tracking: 0.5),
            Word(text: "from", x: 802.1, tracking: 0.86),
        ]),
        TextLine(baseline: 915.4, words: [
            Word(text: "notes", x: 293.0, tracking: 0.42),
            Word(text: "or", x: 561.1, tracking: 0.42),
            Word(text: "texts", x: 676.2, tracking: 0),
        ]),
    ]

    static let hintSize = 12.6
    static let hintLines = [
        TextLine(baseline: 1039.6, words: [
            Word(text: "works", x: 194.8, tracking: -0.04),
            Word(text: "best", x: 331.1, tracking: -0.79),
            Word(text: "when", x: 426.8, tracking: 0.21),
            Word(text: "the", x: 546.5, tracking: -0.95),
            Word(text: "places", x: 620.1, tracking: -0.05),
            Word(text: "in", x: 760.5, tracking: -0.12),
            Word(text: "the", x: 807.5, tracking: -1.12),
            Word(text: "list", x: 880.5, tracking: -0.45),
            Word(text: "are", x: 950.6, tracking: -0.45),
            Word(text: "all", x: 1029.6, tracking: 0.55),
        ]),
        TextLine(baseline: 1089.1, words: [
            Word(text: "from", x: 383.4, tracking: -1.06),
            Word(text: "the", x: 489.5, tracking: -0.84),
            Word(text: "same", x: 562.5, tracking: 0.05),
            Word(text: "region.", x: 681.1, tracking: 0.22),
        ]),
    ]

    static let tipBaseline = 1189.3
    static let tipWords = [
        Word(text: "tip", x: 338.5, tracking: -0.73),
        Word(text: "include", x: 446.5, tracking: 0.27),
        Word(text: "the", x: 602.5, tracking: -1.07),
        Word(text: "city", x: 676.0, tracking: -0.34),
        Word(text: "name", x: 758.1, tracking: 0.1),
    ]
    static let tipDash = RectSpec(x: 398.5, y: 1176.7, width: 38, height: 3.9)

    static let placeholderSize = 14.5
    static let placeholderBaseline = 1412.0
    static let placeholderWords = [
        Word(text: "paste", x: 184.8, tracking: -0.63),
        Word(text: "your", x: 322.3, tracking: -0.08),
        Word(text: "note", x: 440.8, tracking: -0.74),
        Word(text: "or", x: 553.6, tracking: -1.3),
        Word(text: "text", x: 616.4, tracking: -1.52),
        Word(text: "here...", x: 715.8, tracking: 0.14),
    ]

    static let counterSize = 15.3
    static let counterBaseline = 2220.5
    static let counterWords = [
        Word(text: "0/10,000", x: 100.4, tracking: 1.38),
        Word(text: "characters", x: 329.8, tracking: -1.07),
    ]

    static let paste = (size: 16.6, weight: Font.Weight.heavy, tracking: -0.75,
                        text: "paste", baseline: 2047.5, left: 571.0)
    static let start = (size: 16.6, tracking: -0.08, text: "start import", baseline: 2448.9)
    static let pin = (size: 14.6, text: "📍", left: 128.0, top: 998.8)

    static let noteInk = color(0x262626)
    static let marker = Color(red: 1, green: 213 / 255, blue: 46 / 255, opacity: 0.5)
    static let detect = color(0x3b78e7)
    static let detectTrail = Color(red: 59 / 255, green: 120 / 255, blue: 231 / 255, opacity: 0.10)
    static let buttonSolid = color(0x1c1c1e)
    static let chipFill = white
    static let chipBorder = cardBorder
    static let chipShadow = ink

    static let noteLines = [
        NoteLine(prefix: "weekend in ", place: "velmora", suffix: "", isCity: true),
        NoteLine(prefix: "brunch at ", place: "oriel café", suffix: ""),
        NoteLine(prefix: "sunset at ", place: "alto point", suffix: ""),
        NoteLine(prefix: "", place: "tide market", suffix: ", sat only"),
        NoteLine(prefix: "dinner at ", place: "fennel & fig", suffix: ""),
        NoteLine(prefix: "drinks at ", place: "lantern bar", suffix: ""),
    ]
    static let noteText = noteLines.map { $0.prefix + $0.place + $0.suffix }.joined(separator: "\n")

    enum Note {
        static let size = 14.5
        static let left = 184.8
        static let firstBaseline = 1412.0
        static let pitch = 66.0
        static let scanTop = 1355.0
        static let scanBottom = 1790.0
        static let scanTrail = 90.0
        static let scanStroke = 4.0
        static let markerTop = 0.42
        static let markerBottom = 0.9
        static let markerOver = 6.0
        static let markerRadius = 6.0
        static let underlineGap = 7.0
        static let underlineStroke = 4.0
    }

    /// Chips: lift off their words, then sit on the map as pins (label pills). ref px unless marked pt.
    enum Chip {
        static let height = 72.0
        static let radius = 36.0
        static let padLeft = 24.0
        static let padRight = 30.0
        static let pinGap = 9.0
        static let size = 12.6
        static let pinSize = 11.0 // pt
        /// Scale of a chip once it sits on the map.
        static let mapScale = 0.8
    }

    /// The editable text area over the card, ref px.
    enum Input {
        static let left = 184.8
        static let right = 98.0 + 1010.0 - 89.0
        static let bottom = 1930.0
        static let max = 10_000
        static let label = "Note to import"
    }

    static let shade = ink

    enum MapColors {
        static let land = color(0xf3f0e9)
        static let park = color(0xdee9d4)
        static let water = color(0xd3e2ee)
        static let shore = color(0xc3d5e4)
        static let street = color(0xffffff)
        static let casing = color(0xe3ded4)
        static let minor = color(0xfbfaf6)
        static let creaseDark = Color.black.opacity(0.07)
        static let creaseLight = Color.white.opacity(0.85)
        static let route = color(0x3b78e7)
        static let routeCasing = color(0xffffff)
        static let dot = color(0x3b78e7)
        static let dotRing = color(0xffffff)
        static let shadow = color(0x000000)
    }

    enum MapShape {
        static let sea = "M 430 0 C 470 80 560 120 640 130 C 700 138 760 118 792 150 C 818 176 782 214 802 246 C 832 292 920 322 1010 382 L 1010 0 Z"
        static let river = "M 990 370 C 860 420 760 400 660 470 C 560 540 430 500 300 560 C 200 605 90 580 -10 600"
        static let parks: [(cx: Double, cy: Double, rx: Double, ry: Double)] = [
            (330, 340, 120, 62),
            (860, 720, 96, 58),
        ]
        static let major = [
            "M -10 250 C 220 230 420 310 620 300 C 780 292 900 470 1020 520",
            "M 230 -10 C 250 300 330 560 420 880",
            "M -10 770 C 300 700 620 800 1020 690",
            "M 600 120 C 610 400 680 640 760 880",
        ]
        static let minorAngle = -12.0
        static let minorGap = 70.0
        static let minorW = 6.0
        static let majorW = 13.0
        static let casingW = 18.0
        static let riverW = 28.0
        static let shoreW = 34.0
        static let seaShoreW = 4.0
        static let creaseW = 2.0
    }

    /// The five places on the map (dot centres, card-local ref px), in the note's order.
    static let spots: [(x: Double, y: Double)] = [
        (165, 290), // oriel cafe
        (770, 192), // alto point
        (640, 452), // tide market
        (150, 640), // fennel & fig
        (560, 760), // lantern bar
    ]

    /// Pins on the map, pt at 402 pt wide.
    enum Pin {
        static let dotR = 4.4
        static let coreR = 2.7
        static let gap = 3.0
        static let shadowRx = 9.0
        static let shadowRy = 2.6
        static let shadowBlur = 2.2
        static let route = 2.4
        static let routeCasing = 4.6
    }

    static let tipUnderline = RectSpec(x: 446.5, y: 1199, width: 420, height: 4)
    static let foundPrefix = "found "
    static let foundSuffix = " place"
    static let foundPlural = "s"
    static let textDigitAdvance = 1186.0 / 2000.0
    static let extraDigitAdvance = 1309.0 / 2000.0

    static let icons: [String: RectSpec] = [
        "notes": RectSpec(x: 383, y: 358, width: 213, height: 212),
        "chat": RectSpec(x: 499, y: 418, width: 214, height: 213),
        "doc": RectSpec(x: 665, y: 373, width: 142, height: 190),
    ]

    static func color(_ hex: UInt32) -> Color {
        Color(
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255
        )
    }
}
