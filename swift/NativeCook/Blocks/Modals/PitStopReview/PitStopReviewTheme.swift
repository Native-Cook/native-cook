import SwiftUI
import CoreText
import UIKit

/// Pit Stop Review tokens (theme.ts): points of the pin's 402 pt screen, measured on the pin at 3×; content y is
/// relative to the end of the status-bar safe area. The screen scales them by width / 402.
enum PitStopReviewTheme {
    static let referenceWidth = 402.0

    private static func rgb(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }
    private static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        rgb(Double((v >> 16) & 255), Double((v >> 8) & 255), Double(v & 255), a)
    }

    enum Colors {
        static let ink = hex(0xF8F5FA)
        static let muted = hex(0xB2ADB8)
        static let bg = hex(0x000000)
        static let panel = hex(0x1A181C)
        static let violet = hex(0x361CFF)
        static let orange = hex(0xFF4A08)
        static let yellow = hex(0xFFE62E)
        static let light = hex(0xFF3B30)
        static let lightCore = hex(0xFFD2C2)
        static let lightOff = hex(0x2B0C0B)
        static let discHot = hex(0xFF6A1A)
        static let discBloom = hex(0xE2340C)
        static let ctaInk = hex(0x080708)
    }

    /// Top bar: the Club Pass pill and the avatar share one centre line.
    static let barCenter = 25.1

    enum ClubPass {
        static let left = 28.7, height = 28.0, badge = 17.7, padLeft = 5.15, padRight = 9.5, gap = 5.3
        static let sparkleW = 8.0, sparkleH = 8.7, fontSize = 12.0, letterSpacing = -0.2, lineHeight = 15.0
        static let fontWeight = Font.Weight.semibold
    }
    enum Avatar { static let hit = 44.0, right = 19.0, ring = 40.6, photo = 33.3 }

    /// Hero: a 402 × 220 design box at content y 32; the car canvas inside it.
    enum Hero {
        static let top = 32.0, width = 402.0, height = 220.0
        static let car = CGRect(x: 70, y: 26, width: 273.33, height: 106.67)
        struct Bloom { var x, y, w, h, blur: Double; var color: UInt32; var alpha: Double }
        static let blooms = [
            Bloom(x: -80, y: 36, w: 220, h: 140, blur: 20, color: 0xFF2A0A, alpha: 0.95),
            Bloom(x: -60, y: 54, w: 180, h: 104, blur: 12, color: 0xFF5A1A, alpha: 0.7),
        ]
        static let core = (x: -50.0, y: 70.0, w: 150.0, h: 36.0, blur: 6.0)
        static let streaks = 22, streakTop = 62.0, streakSpan = 50.0
    }

    /// Start-light gantry under the parked car (hero coordinates).
    enum Gantry { static let cx = 201.0, cy = 178.0, pitch = 20.0, radius = 6.5, housingH = 20.0, housingPad = 7.0 }

    enum Pager { static let top = 422.3, height = 8.0, active = 24.7, dot = 3.3, gap = 8.8 }

    enum TabBar {
        static let width = 358.0, height = 62.0, bottom = 25.5, padding = 7.0, icon = 22.0
        static let lensWidth = 74.0, lensHeight = 52.0
        static let lensFill = Color.white.opacity(0.16)
        static let iconColor = Color.white.opacity(0.94)
    }

    enum Stars { static let row = 250.0, cell = 50.0, height = 48.0, box = 28.0, radius = 11.0 }

    /// Montserrat's ascender per point of size: a line's top is `baseline − ascent · size`.
    static let ascent = 0.968

    enum HeroText {
        static let centerX = 191.0
        static let title = (size: 30.4, baseline: 263.0, tracking: -0.3)
        static let subtitle = (size: 14.3, baseline: 293.0, lineHeight: 20.3, width: 330.0)
        static let cta = (top: 332.7, width: 177.4, height: 44.3, size: 15.3, tracking: -0.1)
    }

    enum Rail {
        static let top = 449.3, width = 278.0, height = 60.7
        static let fill = rgb(19, 19, 19), stroke = rgb(45, 45, 45)
        static let iconY = 30.4
        static let iconX: [Double] = [101.3, 167.7, 234, 300.5]
        static let calendar = 25.7, controller = 25.7, shapes = 21.4, sparkle = 24.0
        static let dividerHeight = 27.0
        static let divider = Color.white.opacity(0.2)
    }

    /// Bento under the rail: the sibling Paddock block's tiles at this pin's footprint.
    enum Bento {
        static let top = 529.0, left = 10.7
        static let tile = (width: 184.0, height: 60.3, pitch: 73.8, radius: 30.15)
        static let pitcast = (left: 207.7, width: 183.3, height: 210.0, radius: 28.0, inset: 14.5, arrowInset: 12.5, mic: 50.0, micRadius: 13.5,
                              arrow: 18.5, glyphRow: 179.8, glyph: 20.0)
        static let glyphX: [Double] = [25.7, 62, 99]
        static let violet = rgb(50, 33, 252), orange = rgb(254, 63, 3), yellow = rgb(255, 218, 38)
        static let cream = rgb(242, 238, 223), micInk = rgb(255, 196, 178)
        static let eyebrow = 12.5, eyebrowTracking = -0.3, title = 16.5, titleTracking = -0.2
        static let violetSize = 19.5, violetTracking = -0.9, badge = 13.0, chipIcon = 13.0, cardIcon = 24.0
        static let pitcastTitle = 32.0, pitcastSubtitle = 12.2, pitcastNames = 10.2, pitcastIcon = 26.0, pitcastArrow = 8.0
        static let baselineEyebrow = 25.8, baselineTitle = 44.8
        static let baselineViolet: [Double] = [32, 47.5]
        static let violetX = 71.7
        static let baselinePitcastTitle = 106.8, baselinePitcastSubtitle = 131.4, baselinePitcastNames = 152.5
        static let violetBadge = (width: 47.5, height: 28.5, centerX: 38.5, stroke: 2.5)
        static let orangeChip = (width: 30.0, height: 19.0, trailing: 14.7, radius: 5.5)
        static let tileInset = 16.0, tileTextAfterMark = 60.5, tileMarkCenter = 31.0
        // Paddock's stats card (this pin's columns make it 380.3 wide).
        static let statsTop = top + 220
        static let stats = (width: 380.3, height: 144.0, radius: 28.0, x: 85.3)
        static let statsGlow = rgb(42, 40, 40), statsShade = rgb(20, 18, 21)
        static let statsIcon = (x: 25.0, y: 27.5, width: 41.0, height: 51.0), statsIconSize = 50.0
        static let statsEyebrow = 16.0, statsEyebrowBaseline = 18.3, statsWord = 32.5, statsWordBaseline = 54.8
        static let captionX = 21.3, captionSize = 12.0
        static let captionBaselines: [Double] = [123.4, 138.2]
        static let caption = ["Stats, records & stories", "from Turn Nine"]
    }

    /// Page tone behind the hero, sampled on the pin (neutral grey), positions over the gradient's 450 pt.
    static let pageToneHeight = 450.0
    static let pageTone: [Gradient.Stop] = [
        .init(color: rgb(19, 19, 19), location: 0), .init(color: rgb(18, 18, 18), location: 0.53),
        .init(color: rgb(5, 5, 5), location: 0.71), .init(color: rgb(1, 1, 1), location: 0.86), .init(color: rgb(0, 0, 0), location: 1),
    ]
}

/// Montserrat (OFL), bundled flat in the app, loaded once and used through CoreText.
@MainActor
enum PitStopReviewFont {
    enum Face: String, CaseIterable { case medium = "Medium", semibold = "SemiBold", bold = "Bold", extraBold = "ExtraBold", black = "Black" }

    private static var cgFonts: [Face: CGFont] = [:]
    private static var cache: [String: CTFont] = [:]

    private static func cgFont(_ face: Face) -> CGFont {
        if let cached = cgFonts[face] { return cached }
        let name = "PitStopReview-Montserrat-\(face.rawValue)"
        guard let url = Bundle.main.url(forResource: name, withExtension: "ttf"),
              let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider) else {
            preconditionFailure("Missing PitStopReview font: \(name)")
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

/// One line of text placed by its baseline (`x`, `baseline` in points of the surrounding box).
struct PitStopReviewLine: View {
    let text: String
    let x: Double
    let baseline: Double
    let size: Double
    let face: PitStopReviewFont.Face
    let color: Color
    var tracking = 0.0
    var opacity = 1.0
    let scale: CGFloat

    var body: some View {
        Text(text)
            .font(PitStopReviewFont.font(face, size: size * scale))
            .tracking(tracking * scale)
            .foregroundStyle(color)
            .opacity(opacity)
            .lineLimit(1)
            .fixedSize()
            .offset(x: x * scale, y: baseline * scale - CTFontGetAscent(PitStopReviewFont.ct(face, size: size * scale)))
    }
}

/// A symbol centred at (cx, cy) in a `size` square, drawn like SwiftUI's `.font(.system(size:))` image.
struct PitStopReviewMark: View {
    let symbol: String
    let cx: Double
    let cy: Double
    let size: Double
    let color: Color
    var weight: Font.Weight = .semibold
    let scale: CGFloat

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * scale, weight: weight))
            .foregroundStyle(color)
            .frame(width: size * scale, height: size * scale)
            .offset(x: (cx - size / 2) * scale, y: (cy - size / 2) * scale)
            .accessibilityHidden(true)
    }
}

/// The window's safe-area insets. The block lays out edge to edge (`.ignoresSafeArea()`), so a GeometryReader
/// inside it reports zero insets; read them from the key window instead.
enum PitStopReviewSafeArea {
    @MainActor static var insets: EdgeInsets {
        let window = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first { $0.isKeyWindow }
        let i = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: i.top, leading: i.left, bottom: i.bottom, trailing: i.right)
    }
}
