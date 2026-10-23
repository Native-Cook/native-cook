import SwiftUI

enum SavedPlacesMapTheme {
    static let REF_W = 402.0
    static let REF_PX_W = 1206.0
    
    enum FontName {
        static let archivo = "Archivo-Regular"
        static let archivoMedium = "Archivo-Medium"
        static let archivoSemi = "Archivo-SemiBold"
        static let archivoBold = "Archivo-ExtraBold"
        static let barlow = "Barlow-Regular"
        static let barlowSemi = "Barlow-SemiBold"
        static let barlowItalic = "Barlow-Italic"
    }

    struct TextSpec {
        let size: Double
        let font: String
        let color: Color
    }

    enum Colors {
        static let ink = color(0x000000)
        static let meta = color(0x6E7479)
        static let tileLabel = color(0x474C4F)
        static let slash = color(0x707479)
        static let tile0 = color(0xEAF0F7)
        static let tile0Border = color(0xCDD4DF)
        static let tile = color(0xFDFFFF)
        static let tileShade = color(0xEDEDF5)
        static let card = color(0xFFFFFF)
        static let cardSub = color(0x8E8E93)
        static let chip = color(0xFDFCFE)
        static let chipShade = color(0xEBE8EC)
        static let grabber = color(0xC5C7C9)
        static let highlight = color(0xF7FFF7)
        static let city = color(0x101010)
        static let borough = color(0x474858)
        static let water = color(0x6EA1C1)
        static let faded = color(0xA5A7A8)
        static let markerInk = color(0x000000)
        static let markerSub = color(0x2B2B2B)
        static let bookmark = color(0x0E77EA)
        static let thumb = color(0x8FCAFC)
        static let star = color(0xFFBD20)
        static let locate = color(0x0263FF)
        static let shieldBlue = color(0x3C62AC)
        static let shieldRed = color(0xC8514F)
        static let badge = color(0x707274)
        static let white = color(0xFFFFFF)
        static let checkbox = color(0x8A8A8D)
        static let shadow = color(0x4B7391)
        static let dim = color(0x0E2233)
        static let grabberTaken = color(0x9FA3A7)
        static let check = color(0x000000)
        static let checkInk = color(0xFFFFFF)
    }

    enum Space {
        static let margin = 16.0
        static let cardMargin = 26.0 / 3.0
        static let tileGap = 8.0
        static let cardPad = 38.0 / 3.0
    }

    enum Radius {
        static let sheet = 28.0
        static let card = 22.0
        static let tile = 50.0 / 3.0
        static let photoTop = 8.0
        static let photoList = 12.0
        static let badge = 14.0 / 3.0
    }

    enum Copy {
        static let title = "NYC cafes for chatting"
        static let places = "59 places"
        static let saves = "11 saves"
        static let views = "252 views"
        static let handle = "@ines.vale"
        static let visitedNum = "0"
        static let visitedNext = "1"
        static let visitedDen = "/59"
        static let visited = "you visited"
        static let save = "save list"
        static let share = "share list"
        static let similar = "similar lists"
        static let listTitle = "Mabel's Corner Café"
        static let listSub = "piano singalong"
        static let allPlaces = "all places"
        static let eat = "eat"
        static let cafe = "café"
        static let bar = "bar"
        // Screen reader.
        static let a11yBy = "by"
        static let a11ySheetHint = "Swipe up or down to expand or collapse the list."
        static let a11yExpanded = "expanded"
        static let a11yCollapsed = "collapsed"
        static let a11yOf = "of"
        static let a11yVisitHint = "Double-tap to mark Mabel's Corner Café visited."
        static let a11yVisitedBox = "Visited"
        static let a11yVisited = "visited"
        static let a11yMarkerHint = "Double-tap to highlight it on the map."
    }

    enum TypeSpec {
        static let title = TextSpec(size: 66.7, font: FontName.archivoBold, color: Colors.ink)
        static let meta = TextSpec(size: 42, font: FontName.archivo, color: Colors.meta)
        static let handle = TextSpec(size: 36.8, font: FontName.archivoMedium, color: Colors.ink)
        static let visitedNum = TextSpec(size: 57, font: FontName.archivoBold, color: Colors.ink)
        static let visitedDen = TextSpec(size: 57, font: FontName.archivoSemi, color: Colors.slash)
        static let visited = TextSpec(size: 39.5, font: FontName.archivo, color: Colors.tileLabel)
        static let tile = TextSpec(size: 40.5, font: FontName.archivo, color: Colors.tileLabel)
        static let listTitle = TextSpec(size: 54.1, font: FontName.archivoBold, color: Colors.ink)
        static let listSub = TextSpec(size: 45.3, font: FontName.archivo, color: Colors.cardSub)
        static let chip = TextSpec(size: 48.5, font: FontName.archivoSemi, color: Colors.ink)
    }

    struct Chip {
        let x: Double
        let w: Double
        let text: String
        let textX: Double
        let emoji: String?
        let emojiX: Double
    }

    struct MapLabel {
        let text: String
        let x: Double?
        let cx: Double?
        let base: Double
        let size: Double
        let color: Color
        let spacing: Double
        let opacity: Double
        let font: String
    }

    static let chips = [
        Chip(x: 41, w: 365, text: Copy.allPlaces, textX: 120, emoji: nil, emojiX: 62),
        Chip(x: 446, w: 184, text: Copy.eat, textX: 528, emoji: "🍽️", emojiX: 491),
        Chip(x: 666, w: 211, text: Copy.cafe, textX: 749, emoji: "☕", emojiX: 712),
        Chip(x: 917, w: 188, text: Copy.bar, textX: 999, emoji: "🍸", emojiX: 964),
        Chip(x: 1143, w: 120, text: "", textX: 0, emoji: "👜", emojiX: 1180),
    ]

    static let mapLabels = [
        MapLabel(text: "North Bergen", x: 195, cx: nil, base: 262, size: 41, color: Colors.faded, spacing: 0, opacity: 0.8, font: FontName.barlow),
        MapLabel(text: "Union City", x: 148, cx: nil, base: 428, size: 47.3, color: Colors.city, spacing: 0, opacity: 1, font: FontName.barlow),
        MapLabel(text: "Jersey City", x: 18, cx: nil, base: 828, size: 45.7, color: Colors.city, spacing: 0, opacity: 1, font: FontName.barlow),
        MapLabel(text: "MANHATTAN", x: 336, cx: nil, base: 573, size: 31, color: Colors.borough, spacing: 5.2, opacity: 1, font: FontName.barlow),
        MapLabel(text: "BROOKLYN", x: 537, cx: nil, base: 1296, size: 31, color: Colors.borough, spacing: 5.2, opacity: 1, font: FontName.barlow),
        MapLabel(text: "EAST NEW", x: nil, cx: 984.5, base: 1180, size: 31, color: Colors.borough, spacing: 5.2, opacity: 1, font: FontName.barlow),
        MapLabel(text: "YORK", x: nil, cx: 984.5, base: 1218, size: 31, color: Colors.borough, spacing: 5.2, opacity: 1, font: FontName.barlow),
        MapLabel(text: "Upper New", x: 0, cx: nil, base: 1148, size: 44, color: Colors.water, spacing: 6.2, opacity: 1, font: FontName.barlowItalic),
        MapLabel(text: "York Bay", x: 26, cx: nil, base: 1208, size: 44, color: Colors.water, spacing: 6.2, opacity: 1, font: FontName.barlowItalic),
    ]

    // Hhea ascent / descent per em (RN METRICS).
    static func ascent(_ font: String) -> Double { font.hasPrefix("Barlow") ? 1 : 0.878 }
    static func descent(_ font: String) -> Double { font.hasPrefix("Barlow") ? 0.2 : 0.21 }

    static let mapH = 1650.0
    static let shield = CGRect(x: 918, y: 738, width: 74, height: 57)
    static let shieldText = "495"
    static let shieldSize = 25.0

    // The pin's progressive blur (theme.ts topBlur). `sigma` is the measured Gaussian (ref px) and `tint` the
    // white the BlurView's light tint adds, derived from the measured veil: 1 - (1 - tint)(1 - 0.42) = 0.55.
    enum TopBlur {
        static let h = 330.0
        static let solid = 120.0
        static let sigma = 11.0
        static let tint = 0.22
        static let fadeH = 420.0
        static let fadeStops: [(Double, Double)] = [(0, 0.42), (280.0 / 420.0, 0.24), (370.0 / 420.0, 0.1), (1, 0)]
    }

    // depth (theme.ts)
    enum Depth {
        static let sky = (color(0xE7EEF3), color(0xEEF2F5))
        static let fogH = 560.0
        static let fogRGB = (238.0 / 255, 243.0 / 255, 246.0 / 255)
        static let fogStops: [(Double, Double)] = [(0, 1), (0.55, 0.85), (1, 0)]
        static let stalkW = 9.0
        static let stalkTop = color(0xFFFFFF)
        static let stalkBottom = color(0xD3DBE2)
        static let footD = 16.0
        static let foot = color(0x3E5568)
        static let beamSaved = (14.0 / 255, 119.0 / 255, 234.0 / 255)
        static let beamLiked = (96.0 / 255, 170.0 / 255, 240.0 / 255)
        static let shadow = color(0x2E4153)
        static let rippleRing = 3.0
    }
    static let depthRank: [String: Int] = ["lumen": 0, "mabel": 1, "fern": 2, "linden": 3, "aurelle": 4]

    static let tileY = 2068.0
    static let tileH = 200.0
    static let tileW = 259.5
    static let tileXs = [48.0, 331.5, 615.0, 898.5]
    static let tileBaselines = [2222.0, 2219.0, 2216.0, 2217.0]
    static let tileCentres = [177.5, 461.5, 744.0, 1027.5]

    static let chipY = 1440.0
    static let chipH = 82.0
    static let chipBaseline = 1498.0
    static let sheetTop = 1548.0
    static let grabber = CGRect(x: 526, y: 1572, width: 153, height: 15)

    static let bubbleDx = -36.0
    static let bubbleDy = -36.0
    static let bubbleH = 45.0
    static let bubbleDigit = 17.0
    static let bubblePadX = 12.0
    static let bubbleRing = 2.0
    static let bubbleSize = 30.0

    static let badgeDx = 32.0
    static let badgeDy = 32.0
    static let badgeD = 34.0
    static let badgeRing = 2.0

    static let listCardH = 328.0
    static let listGap = 26.0
    static let rollBox = CGRect(x: 95, y: 2104, width: 52, height: 60)
    static let rollRow = 60.0
    static let rollCentre = 125.0

    static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255,
              opacity: opacity)
    }
}
