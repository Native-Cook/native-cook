import SwiftUI
import CoreText
import UIKit

/// Every colour, size and baseline of Paddock Widget Bento, in points on a 402-wide screen (theme.ts).
/// The screen scales them by width / 402. Text is placed by its baseline.
enum PaddockWidgetBentoTheme {
    static let referenceWidth = 402.0

    private static func rgb(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }

    enum Colors {
        static let violet = rgb(50, 33, 252)
        static let orange = rgb(254, 63, 3)
        static let yellow = rgb(255, 218, 38)
        static let cream = rgb(242, 238, 223)
        static let statsGlow = rgb(42, 40, 40)
        static let statsShade = rgb(20, 18, 21)
        static let green = rgb(5, 208, 130)
        static let blue = rgb(62, 157, 255)
        static let red = rgb(247, 26, 33)
        static let rose = rgb(253, 121, 120)
        static let guideYellow = rgb(252, 254, 31)
        static let micInk = rgb(255, 196, 178)
        static let creamInk = rgb(242, 238, 223)
        static let bannerShade = rgb(110, 0, 6, 0.92)
        static let bannerGlow = rgb(196, 8, 18, 0.62)
        static let bannerClear = rgb(220, 20, 28, 0.38)
        static let bannerTrail = rgb(167, 0, 9)
        static let statsTrail = rgb(70, 66, 68)
        static let ink75White = Color.white.opacity(0.75)
        static let ink55Black = Color.black.opacity(0.55)
        static let closeFill = Color.black.opacity(0.5)
        static let sealStroke = rgb(247, 26, 33, 0.18)
        static let stickerBack = rgb(246, 236, 232)
        static let stickerFold = rgb(196, 172, 168)
        static let stickerShadow = rgb(60, 0, 4)
        static let slapLine = Color.white.opacity(0.9)
        static let dockGlassTint = rgb(30, 8, 10, 0.18)
        static let dockFallback = rgb(28, 20, 22, 0.62)
        static let dockStroke = Color.white.opacity(0.22)
        static let dockLens = Color.white.opacity(0.20)
        static let dockLensStroke = Color.white.opacity(0.38)
        static let dockIcon = Color.white.opacity(0.94)
        static let dockIconActive = rgb(255, 226, 224)
        static let panel10 = Color.white.opacity(0.10)
        static let pinnedLabel = rgb(235, 235, 245, 0.6)
    }

    /// Tile colours used by the pit-stop smear, by tile index (`PaddockWidgetBentoMotion.Tile`).
    static let tileColors: [Color] = [
        Colors.violet, Colors.orange, Colors.yellow, Colors.cream, Colors.statsTrail,
        Colors.green, Colors.blue, Colors.red, Colors.guideYellow, Colors.bannerTrail,
    ]

    enum Space {
        static let screenInset = 10.0
        static let columnGap = 10.0
        static let rowGap = 12.0
        static let tileInset = 16.0
        static let tileTextAfterMark = 60.5
        static let tileMarkCenter = 31.0
        static let pitcastInset = 14.5
        static let pitcastArrowInset = 12.5
        static let statsTextX = 85.3
        static let statsCaptionX = 21.3
        static let heroFirstX = 15.7
        static let heroSecondX = 58.4
        static let guideInset = 14.3
        static let guideCloseInset = 12.7
        static let bannerPad = 20.0
        static let bannerTop = 28.0
        static let bannerGap = 8.0
        static let dockPad = 7.0
        static let dockLensInset = 5.0
        static let handleTrailing = 14.0
        static let pinnedGap = 8.0
        static let pinnedInset = 18.0
    }

    private static let col = 186.0
    private static let full = 382.0
    private static let top = 54.5
    private static let rightCol = Space.screenInset + col + Space.columnGap

    enum Layout {
        static let quick = CGRect(x: 10, y: 54.5, width: 186, height: 62)
        static let pitcast = CGRect(x: rightCol, y: top, width: col, height: 210)
        static let mic = 50.0, arrow = 18.5, glyphRow = 179.8, glyph = 20.0
        static let glyphX: [Double] = [25.7, 62, 99]
        static let stats = CGRect(x: 10, y: 276.5, width: full, height: 144)
        static let statsArt = CGRect(x: 254, y: 15, width: 106, height: 110)
        static let statsIcon = CGRect(x: 25, y: 27.5, width: 41, height: 51)
        static let glossary = CGRect(x: 10, y: 432.5, width: col, height: 62)
        static let education = CGRect(x: rightCol, y: 432.5, width: col, height: 62)
        static let hero = CGRect(x: 10, y: 506.5, width: full, height: 128)
        static let sealSize = 40.5, sealCenterX = 33.0, sealCenterY = 91.2
        static let guide = CGRect(x: 10, y: 646.5, width: full, height: 110)
        static let closeSize = 18.5, closeCenterY = 21.5
        static let banner = CGRect(x: 10, y: 768.5, width: full, height: 170)
        static let gridHeight = 938.5
        // Baselines (card-local).
        static let eyebrow = 25.8, title = 44.8
        static let violet: [Double] = [32, 47.5]
        static let violetX = 71.7
        static let pitcastTitle = 106.8, pitcastSubtitle = 131.4, pitcastNames = 152.5
        static let statsEyebrow = 18.3, statsWord = 54.8
        static let caption: [Double] = [123.4, 138.2]
        static let heroFirst = 54.0, heroSecond = 108.8
        static let guideTitle = 46.8
        static let guideHelper: [Double] = [70.7, 87.9]
        static let violetBadge = (width: 47.5, height: 28.5, centerX: 38.5, stroke: 2.5)
        static let orangeChip = (width: 30.0, height: 19.0, trailing: 14.7)
        static let done = (width: 72.0, centerX: 317.0, centerY: 85.5)
        static let pinnedTop = 950.5, pinnedHeight = 64.0, pinnedPitch = 72.0
        static let contentHeight = 950.5, contentMinHeight = 1010.0, bottomClearance = 118.0
        static let dock = (width: 358.0, height: 62.0, bottom: 22.0, lensWidth: 74.0, lensHeight: 52.0, icon: 22.0)
        static let touchSurface = CGRect(x: 10, y: 54.5, width: col, height: 210)
        static let pickRadius = 38.0
        static let peelSpot = CGRect(x: 250, y: 46, width: 132, height: 82)
        static let doneHeight = 44.0
    }

    enum Radius {
        static let quickCard = 31.0, card = 28.0, mic = 13.5, chip = 5.5, pinned = 22.0, dock = 31.0, lens = 26.0
    }

    enum TypeSize {
        static let eyebrow = 12.5, eyebrowTracking = -0.3
        static let tileTitle = 16.5, tileTitleTracking = -0.2
        static let violet = 19.5, violetTracking = -0.9
        static let badge = 13.0, cardIcon = 24.0, chipIcon = 13.0, handle = 16.0
        static let pitcastIcon = 26.0, pitcastArrow = 8.0, pitcastTitle = 32.0
        static let pitcastSubtitle = 12.2, pitcastNames = 10.2
        static let statsIcon = 50.0, statsEyebrow = 16.0, statsWord = 32.5, caption = 12.0
        static let tileIcon = 26.0, heroGlyph = 61.0, heroSeal = 13.0
        static let guideTitle = 28.2, guideHelper = 12.0, guideClose = 7.5
        static let bannerTitle = 32.0, bannerSubtitle = 16.0, done = 15.0, dockIcon = 22.0, body = 17.0
    }

    static let pitcastNamesAlpha = 0.9
    static let glueGhost = 0.1
    static let stickerShadowAlpha = 0.42

    enum Copy {
        static let cardLabels = ["every lap counts", "Race day Reflex test", "custom home cards"]
        static let pitcastLabel = "Read the weekend brief, Alex Vale and Sam Ives"
        static let statsLabel = "Race Stats. Switch between latest and season records"
        static let heroLabel = "Exclusive widgets. Open Widget Studio"
        static let bannerLabel = "Your race-day space. Open Widget Studio"
        static let guideTitle = "how to move cards?"
        static let guideHelper = ["Hold a quick card and drag it to a new slot,", "then tap Done to keep the new order."]
    }
}

/// Montserrat (OFL), bundled flat in the app. Loaded once from the Assets files and used through CoreText.
@MainActor
enum PaddockWidgetBentoFont {
    enum Face: String, CaseIterable {
        case medium = "Medium", semibold = "SemiBold", bold = "Bold", extraBold = "ExtraBold", black = "Black"
    }

    private static var cgFonts: [Face: CGFont] = [:]
    private static var cache: [String: CTFont] = [:]

    private static func cgFont(_ face: Face) -> CGFont {
        if let cached = cgFonts[face] { return cached }
        let name = "PaddockWidgetBento-Montserrat-\(face.rawValue)"
        guard let url = Bundle.main.url(forResource: name, withExtension: "ttf"),
              let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider) else {
            preconditionFailure("Missing PaddockWidgetBento font: \(name)")
        }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        cgFonts[face] = font
        return font
    }

    static func ct(_ face: Face, size: CGFloat) -> CTFont {
        let key = "\(face.rawValue)-\(size)"
        if let cached = cache[key] { return cached }
        let font = CTFontCreateWithGraphicsFont(cgFont(face), size, nil, nil)
        cache[key] = font
        return font
    }

    static func font(_ face: Face, size: CGFloat) -> Font { Font(ct(face, size: size)) }
}
