import CoreGraphics
import UIKit

enum CityEventHeroTheme {
    static let referenceWidth = 1206.0
    static let referenceHeight = 2622.0
    static let referenceInset = 186.0
    static let referencePoints = 402.0
    static let topGroupMaximum = 1000.0
    static let ascentSF = 0.953
    static let ascentHeadline = 0.93

    struct Box: Sendable {
        let x: Double
        let y: Double
        let width: Double
        let height: Double
    }

    struct VerticalPosition: Sendable {
        let y: Double
    }

    struct Guest: Sendable, Equatable {
        let centerX: Double
        let centerY: Double
        let diameter: Double
        let image: Int
    }

    struct TextStyle: Sendable {
        enum Face: Sendable { case system, headline }
        let size: Double
        let weight: UIFont.Weight
        let color: UIColor
        let tracking: Double
        let face: Face
    }

    struct BackdropKnot: Sendable {
        let y: Double
        let red: Double
        let green: Double
        let blue: Double

        var color: UIColor { UIColor(red: red / 255, green: green / 255, blue: blue / 255, alpha: 1) }
    }

    struct Glow: Sendable {
        let centerX: Double
        let centerY: Double
        let sigmaX: Double
        let sigmaY: Double
        let red: Double
        let green: Double
        let blue: Double
    }

    enum Color {
        static let page = UIColor.black
        static let white = UIColor.white
        static let glassTop = UIColor(red: 138 / 255, green: 138 / 255, blue: 138 / 255, alpha: 0.485)
        static let glassBottom = UIColor(red: 99 / 255, green: 99 / 255, blue: 99 / 255, alpha: 0.75)
        static let wordOutline = UIColor(red: 138 / 255, green: 159 / 255, blue: 167 / 255, alpha: 1)
        static let wordFill = UIColor(red: 39 / 255, green: 68 / 255, blue: 80 / 255, alpha: 1)
        static let ink = UIColor.white
        static let inkSoft = UIColor.white.withAlphaComponent(0.60)
        static let inkDate = UIColor.white.withAlphaComponent(0.62)
        static let inkLabel = UIColor.white.withAlphaComponent(0.67)
        static let chevronCard = UIColor.white.withAlphaComponent(0.38)
        static let chevronView = UIColor.white.withAlphaComponent(0.64)
        static let chevCard = chevronCard
        static let chevView = chevronView
        static let scrimTop = UIColor(red: 4 / 255, green: 9 / 255, blue: 11 / 255, alpha: 0.62)
        static let scrimTopClear = UIColor(red: 4 / 255, green: 9 / 255, blue: 11 / 255, alpha: 0)
        static let ring = UIColor.white
        static let heartFill = UIColor.white
        static let burst = UIColor.white
        static let sweep = UIColor.white.withAlphaComponent(0.30)
        static let sweepSoft = UIColor.white.withAlphaComponent(0.08)
        static let sweepClear = UIColor(white: 1, alpha: 0)
        static let mist = UIColor(red: 0.8, green: 0.9, blue: 0.93, alpha: 1)
        static let nightRim = UIColor(red: 1, green: 201 / 255, blue: 119 / 255, alpha: 1)
        static let headlineGlow = UIColor(red: 1, green: 190 / 255, blue: 110 / 255, alpha: 0.95)
    }

    enum TypeStyle {
        static let mute = TextStyle(size: 16, weight: .regular, color: Color.inkSoft, tracking: 0.2, face: .system)
        static let headline = TextStyle(size: 34.33, weight: .regular, color: .white, tracking: 0, face: .headline)
        static let date = TextStyle(size: 15.85, weight: .regular, color: Color.inkDate, tracking: 0.24, face: .system)
        static let label = TextStyle(size: 13.85, weight: .regular, color: Color.inkLabel, tracking: 0.2, face: .system)
        static let title = TextStyle(size: 15.85, weight: .medium, color: .white, tracking: 0.2, face: .system)
        static let pill = TextStyle(size: 14, weight: .medium, color: .white, tracking: 0.15, face: .system)
        static let pillSmall = TextStyle(size: 13.85, weight: .medium, color: .white, tracking: 0.2, face: .system)
    }

    enum Fonts {
        static let headline = "BricolageHeadline"
        static let wordmark = "ArchivoWordmark"
    }

    enum Space {
        static let edge = 49.0
        static let pill = 99.0
        static let ring = 6.0
    }

    enum Radius {
        static let card = 75.0
        static let glassCard = 48.0
        static let thumb = 36.0
    }

    enum Layout {
        static let card = Box(x: 0, y: 181, width: 1206, height: 2195)
        static let wordmark = (x: 63.0, baseline: 306.0, outline: 11.5, size: 75.8, cap: 52.0)
        static let gift = Box(x: 598, y: 231, width: 98, height: 98)
        static let byDatePill = Box(x: 720, y: 230, width: 314, height: 100)
        static let search = Box(x: 1059, y: 231, width: 98, height: 98)
        static let topPills = (gift: gift, byDate: byDatePill, search: search)
        static let byDate = (x: 835.0, baseline: 295.0)
        static let guests = [
            Guest(centerX: 291, centerY: 1539, diameter: 106, image: 1),
            Guest(centerX: 915, centerY: 1539, diameter: 106, image: 6),
            Guest(centerX: 381, centerY: 1533, diameter: 131, image: 0),
            Guest(centerX: 826, centerY: 1533, diameter: 131, image: 5),
            Guest(centerX: 488, centerY: 1526, diameter: 155, image: 2),
            Guest(centerX: 718, centerY: 1526, diameter: 155, image: 4),
            Guest(centerX: 603, centerY: 1520, diameter: 183, image: 3),
        ]
        static let mute = (centerX: 609.0, baseline: 1692.0)
        static let headline = (centerX: 605.0, baseline: 1803.0)
        static let date = (centerX: 609.0, baseline: 1895.0)
        static let presented = Box(x: 49, y: 1957, width: 1108, height: 222)
        static let thumb = Box(x: 86, y: 1994, width: 148, height: 148)
        static let presentedBy = (x: 274.0, baseline: 2053.0)
        static let presentedTitle = (x: 275.0, baseline: 2114.0)
        static let rowY = 2228.0
        static let rowHeight = 99.0
        static let like = Box(x: 49, y: rowY, width: 180, height: rowHeight)
        static let share = Box(x: 254, y: rowY, width: 98, height: rowHeight)
        static let more = Box(x: 376, y: rowY, width: 99, height: rowHeight)
        static let view = Box(x: 787, y: rowY, width: 370, height: rowHeight)
        static let row = (y: rowY, h: rowHeight)
        static let likeCount = (x: 163.0, baseline: 2292.0)
        static let viewText = (x: 838.0, baseline: 2292.0)
        static let chevCard = (x: 1070.0, y: 2050.0)
        static let tabY = 2461.0
        static let tab = VerticalPosition(y: tabY)
        static let bearing = (headline: 5.0, mute: 2.0, date: 3.0, label: 4.0, title: 4.0, pill: 3.0)
        static let guestBand = (start: 1400.0, end: 1720.0)
        static let skyBandStart = 345.0
        static let burstHalf = 70.0
        static let countInset = 18.0
        static let photoShift = 0.57
        static let thumbCrop = (x: 180.0, y: 300.0, size: 500.0)
    }

    enum Backdrop {
        static let fadeFrom = 1450.0
        static let solidFrom = 1760.0
        static let topFadeTo = 880.0
        static let knots = [
            BackdropKnot(y: 1760, red: 26, green: 40, blue: 42),
            BackdropKnot(y: 1860, red: 21, green: 31, blue: 32),
            BackdropKnot(y: 1960, red: 17, green: 23, blue: 24),
            BackdropKnot(y: 2060, red: 3, green: 15, blue: 15),
            BackdropKnot(y: 2160, red: 0, green: 9, blue: 12),
            BackdropKnot(y: 2260, red: 0, green: 6, blue: 7),
            BackdropKnot(y: 2330, red: 3, green: 7, blue: 5),
            BackdropKnot(y: 2372, red: 6, green: 8, blue: 8),
        ]
        static let glows = [
            Glow(centerX: 407, centerY: 2031, sigmaX: 239, sigmaY: 205, red: 56, green: 17, blue: 1),
            Glow(centerX: 1104, centerY: 2084, sigmaX: 269, sigmaY: 600, red: 32, green: 9, blue: 0),
        ]
        static let gaussian = [1.0, 0.882, 0.607, 0.325, 0.135, 0.044, 0]
    }

    enum Night {
        static let zenith = SIMD3<Float>(0.025, 0.045, 0.11)
        static let horizon = SIMD3<Float>(0.1, 0.11, 0.22)
        static let cityGlow = SIMD3<Float>(0.34, 0.17, 0.09)
        static let afterglow = SIMD3<Float>(0.22, 0.08, 0.12)
        static let buildings = SIMD3<Float>(0.13, 0.16, 0.25)
        static let warm = SIMD3<Float>(1, 0.74, 0.42)
        static let cool = SIMD3<Float>(0.8, 0.88, 1)
        static let warmShare = 0.8
        static let bloom = SIMD3<Float>(1, 0.62, 0.32)
        static let flash = SIMD3<Float>(1, 0.78, 0.5)
        static let flare = SIMD3<Float>(1, 0.9, 0.72)
        static let horizonY = 930.0
        static let zenithY = 250.0
        static let skyFrom = 940.0
        static let lightGain = 1.5
        static let edgeGain = 1.4
        static let bloomGain = 0.55
        static let flareCore = 9.0
        static let flareHalo = 60.0
        static let cell = SIMD2<Float>(5, 6)
        static let rim = UIColor(red: 1, green: 201 / 255, blue: 119 / 255, alpha: 1)
        static let headlineGlow = UIColor(red: 1, green: 190 / 255, blue: 110 / 255, alpha: 0.95)
        static let headlineGlowRadius = 18.0
    }

    enum Scene {
        static let sunHigh = SIMD3<Float>(1.0, 0.93, 0.8)
        static let sunLow = SIMD3<Float>(1.0, 0.52, 0.24)
        static let warm = 0.55
        static let lift = 0.22
        static let cloudLit = 0.8
        static let horizonGlow = 0.55
        static let glint = 1.5
        static let glintPower = 60.0
        static let rim = 0.4
        static let shadow = 0.62
        static let shadowTint = SIMD3<Float>(0.86, 0.92, 1.0)
        static let ledOff = 0.28
        static let ledGain = 1.35
        static let ledField = 0.3
        static let fieldA = SIMD3<Float>(0.02, 0.62, 0.72)
        static let fieldB = SIMD3<Float>(0.22, 0.14, 0.62)
        static let ledText = SIMD3<Float>(1.0, 0.84, 0.52)
        static let ledHot = SIMD3<Float>(1.0, 0.98, 0.94)
        static let ledScan = SIMD3<Float>(0.82, 0.96, 1.0)
        static let ledRing = SIMD3<Float>(1.0, 0.78, 0.36)
        static let spill = SIMD3<Float>(0.5, 0.72, 0.85)
        static let spillText = SIMD3<Float>(1.0, 0.78, 0.45)
        static let halo = 0.55
        static let smoke = SIMD3<Float>(0.3, 0.31, 0.37)
        static let smokeAlpha = 0.42
        static let title = UIColor.white.withAlphaComponent(0.9)
        static let titleSoft = UIColor.white.withAlphaComponent(0.62)
    }

    enum Copy {
        static let wordmark = "TOWNIE"
        static let byDate = "By date"
        static let mute = "Amira and 17 more"
        static let headline = "Dubai Jan 2026"
        static let date = "Thu, 9:00 AM – 17 Jan, 11:45 PM  ·  Dubai"
        static let presentedBy = "Presented by"
        static let presentedTitle = "Dubai in Jan 2026"
        static let likes = "1"
        static let likesNext = "2"
        static let names = ["Amira", "Kwame", "Mei", "Karim", "Priya", "Tomás", "Ingrid"]
        static let moreSuffix = " and 17 more"
        static let view = "View event"
    }
}
