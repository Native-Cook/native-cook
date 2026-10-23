import SwiftUI

enum PriceTagWelcomeTheme {
    static let referenceWidth = 1206.0
    static let referenceHeight = 2622.0
    static let referencePoints = 402.0
    static let ascent = 0.953

    enum Colors {
        static let page = Color(red: 0xFC / 255, green: 0xFB / 255, blue: 0xFC / 255)
        static let ink = Color(red: 0x1C / 255, green: 0x1C / 255, blue: 0x1E / 255)
        static let sub = Color(red: 0x5E / 255, green: 0x62 / 255, blue: 0x60 / 255)
        static let price = Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x13 / 255)
        static let tag = Color.white
        static let button = Color(red: 0x03 / 255, green: 0x87 / 255, blue: 0xF5 / 255)
        static let buttonRim = Color(red: 215 / 255, green: 250 / 255, blue: 1, opacity: 0.85)
        static let buttonText = Color.white
        /// Etched into the frost: the groove (icy navy) and the highlight under its lower edge.
        static let etch = Color(red: 38 / 255, green: 78 / 255, blue: 112 / 255, opacity: 0.72)
        static let etchLight = Color(red: 1, green: 1, blue: 1, opacity: 0.95)
        /// The countdown on the product: scraped-white figures with a navy edge below.
        static let scrape = Color(red: 248 / 255, green: 252 / 255, blue: 1, opacity: 0.96)
        static let scrapeEdge = Color(red: 28 / 255, green: 58 / 255, blue: 92 / 255, opacity: 0.6)
        static let glowBase = Color(red: 251.8 / 255, green: 251.2 / 255, blue: 251.5 / 255)
    }

    struct TextStyle {
        let size: Double
        let weight: Font.Weight
        let color: Color
        let letterSpacing: Double
    }

    enum Typography {
        static let headline = TextStyle(size: 38.5, weight: .semibold, color: Colors.ink, letterSpacing: 0)
        static let sub = TextStyle(size: 18.5, weight: .regular, color: Colors.sub, letterSpacing: 0)
        static let price = TextStyle(size: 21, weight: .medium, color: Colors.price, letterSpacing: 0)
        static let wait = TextStyle(size: 15, weight: .semibold, color: Colors.etch, letterSpacing: 0)
        static let timer = TextStyle(size: 14, weight: .semibold, color: Colors.scrape, letterSpacing: 0.2)
        static let button = TextStyle(size: 21.8, weight: .semibold, color: Colors.buttonText, letterSpacing: 0)
    }

    enum Space {
        static let outline = 12.0
        static let canvasPad = 24.0
        static let stickerGap = 96.0
        static let screenInsetButton = 98.0
        static let headlineLine = 143.0
        static let subLine = 75.0
        static let tagPad = 18.0
        static let frostMargin = 42.0
        static let etchDrop = 9.0
        static let hit = 132.0
    }

    static let iconRadius = 43.0
    /// Width of "Wait 24h" in SF Pro Semibold, in em: the etched label shrinks to fit narrow tags.
    static let waitEm = 4.15

    /// The frost (sRGB 0-255, as the shader's constants). Cell sizes in pt at 402 pt.
    enum Frost {
        static let cell = 6.5
        static let tagCell = 3.5
        static let tagAlpha = 0.86
        static let halo = 0.55
    }

    struct Tag: Sendable {
        let centerX: Double
        let centerY: Double
        let width: Double
        let height: Double
        let tilt: Double
    }

    struct Item: Identifiable, Sendable {
        let id: String
        let x: Double
        let y: Double
        let width: Double
        let height: Double
        let tag: Tag
        let price: String
        let name: String
    }

    static let items = [
        Item(id: "headphones", x: 24, y: 1616, width: 288, height: 324,
             tag: Tag(centerX: 225, centerY: 1870, width: 210, height: 88, tilt: -4.5),
             price: "$579", name: "Headphones"),
        Item(id: "laptop", x: 330, y: 1606, width: 324, height: 312,
             tag: Tag(centerX: 553, centerY: 1882, width: 240, height: 92, tilt: 5.5),
             price: "$1599", name: "Laptop"),
        Item(id: "shirt", x: 702, y: 1620, width: 300, height: 312,
             tag: Tag(centerX: 947, centerY: 1870, width: 160, height: 90, tilt: -4),
             price: "$54", name: "Polo shirt"),
        Item(id: "bag", x: 1050, y: 1620, width: 324, height: 312,
             tag: Tag(centerX: 1318, centerY: 1870, width: 186, height: 90, tilt: 3),
             price: "$238", name: "Tote bag"),
        Item(id: "camera", x: 1410, y: 1626, width: 324, height: 300,
             tag: Tag(centerX: 1672, centerY: 1862, width: 200, height: 90, tilt: -3.5),
             price: "$849", name: "Camera")
    ]

    static let period = 1764.0
    static let wrapLeft = -360.0
    static let rowTop = 1590.0
    static let rowBottom = 1990.0
    static let pivotX = -0.2
    static let pivotY = -0.5

    enum Layout {
        static let icon = CGRect(x: 486, y: 656, width: 237, height: 236)
        static let iconBox = CGRect(x: 510, y: 680, width: 189, height: 188)
        static let headlineBaselines = [1044.0, 1187.0]
        static let subBaselines = [1339.0, 1414.0]
        static let buttonX = 98.0
        static let buttonWidth = 1010.0
        static let buttonHeight = 211.0
        static let buttonFromBottom = 379.0
        static let buttonBaseline = 131.0
        /// The countdown's centre as a fraction of the sticker canvas.
        static let timerLaptop = (fx: 0.56, fy: 0.33)
        static let timerOther = (fx: 0.5, fy: 0.44)
    }

    struct GlowBlob: Sendable {
        let centerX: Double
        let centerY: Double
        let sigma: Double
        let red: Double
        let green: Double
        let blue: Double
    }

    static let glow = [
        GlowBlob(centerX: 306.8, centerY: 576.4, sigma: 267, red: 233.2, green: 242.4, blue: 253.7),
        GlowBlob(centerX: 964, centerY: 737.3, sigma: 244, red: 252.7, green: 238.5, blue: 228.3),
        GlowBlob(centerX: 430.9, centerY: 1329.8, sigma: 302, red: 234.3, green: 246.4, blue: 238.8)
    ]

    enum Copy {
        static let appName = "Interpause"
        static let headline = ["Welcome to", "Interpause"]
        static let sub = ["Stop impulse purchases, understand", "the urge, and decide with clarity."]
        static let wait = "Wait 24h"
        static let button = "Get started"
        static let countdown = 24 * 3600
        static let itemHint = "Double-tap to freeze the price for 24 hours, again to thaw it. Drag sideways to browse."
        static let frozen = "frozen, wait 24 hours before buying"
    }
}
