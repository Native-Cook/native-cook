import CoreText
import SwiftUI
import UIKit

enum GlassLockKeyConstants {
    static let stageWidth = 402.0
    static let step = 1.0 / 120.0
    static let maxSteps = 30
    static let loopMS = 10_000.0
    static let resumeMS = 2_000.0

    enum Lock {
        static let topRatio = 0.36
        static let face = 150.0
        static let height = 180.0
        static let side = 22.0
        static let radius = 18.0
        static let shackle = 30.0
        static let opening = 68.0
        static let shackleRise = 100.0
        static let shortLeg = 8.0
        static let longLeg = 60.0
        static let openLift = 58.0
        static let openTilt = 0.12
        static let plugWidth = 28.0
        static let plugHeight = 9.0
        static let slot = 3.0
        static let glowRadius = 34.0
    }

    enum Key {
        static let restRatio = 0.70
        static let ring = 64.0
        static let rim = 11.0
        static let shaft = 11.0
        static let blade = 100.0
        static let bitWidth = 18.0
        static let bitHeight = 30.0
        static let bitFromTip = 8.0
        static let collar = 18.0
        static let hit = 88.0
    }

    enum Typography {
        static let side = 32.0
        static let top = 24.0
        static let eyebrowTracking = 1.8
        static let titleSize = 44.0
        static let titleLine = 50.0
        static let hintSize = 15.0
        static let hintLine = 20.0
        static let gap = 8.0
        static let bottom = 32.0
    }

    enum Sheet {
        static let heightRatio = 0.5
        static let radius = 28.0
        static let pad = 24.0
        static let row = 64.0
        static let icon = 40.0
        static let iconRadius = 12.0
        static let button = 52.0
        static let rise = 0.075
        static let shrink = 0.62
    }

    enum Copy {
        struct Row: Identifiable {
            let title: String
            let subtitle: String
            let icon: String
            var id: String { title }
        }

        static let eyebrow = "PERSONAL VAULT · 3 ITEMS"
        static let locked = "Locked"
        static let unlocked = "Unlocked"
        static let hint = "Slide the key in, then turn it right"
        static let wrongWay = "The other way"
        static let meta = "3 items · end-to-end encrypted"
        static let head = "Opened just now"
        static let rows = [
            Row(title: "Recovery codes", subtitle: "12 codes · added 3 days ago", icon: "codes"),
            Row(title: "Passport scan", subtitle: "PDF · 2.4 MB · expires 2031", icon: "doc"),
            Row(title: "Home Wi-Fi", subtitle: "Password · shared with 2", icon: "wifi"),
        ]
        static let lock = "Lock vault"
        static let unlock = "Unlock vault"
        static let keyHint = "Swipe up to insert and turn the key"
    }

    enum Carry {
        static let lift = 1.04
        static let magnet = 72.0
        static let magnetPull = 0.55
        static let seat = 12.0
        static let homeStiffness = 220.0
        static let homeDamping = 22.0
        static let bob = 3.0
        static let bobPeriod = 2.5
        static let pullOut = 34.0
    }

    enum Insert {
        static let duration = 0.22
        static let outDuration = 0.26
    }

    enum Turn {
        static let radius = 60.0
        static let open = Double.pi / 2
        static let detents = [Double.pi / 6, Double.pi / 3, Double.pi / 2]
        static let wrong = 0.21
        static let visual = 0.83
        static let backStiffness = 260.0
        static let backDamping = 24.0
        static let closeStiffness = 180.0
        static let closeDamping = 22.0
    }

    enum Shackle {
        static let stiffness = 320.0
        static let damping = 14.0
        static let closeStiffness = 260.0
        static let closeDamping = 26.0
    }

    enum Bounce {
        static let stiffness = 420.0
        static let damping = 16.0
        static let detentKick = -40.0
        static let openKick = -120.0
    }

    enum Shake {
        static let duration = 0.36
        static let amplitude = 6.0
        static let cycles = 3.0
    }

    enum SheetMotion {
        static let delay = 0.3
        static let open = 0.5
        static let close = 0.38
        static let stagger = 0.04
        static let reducedFade = 0.2
        static let shackleAfter = 0.3
    }

    enum Glint { static let duration = 0.7 }

    enum Script {
        static let carry = [0.8, 1.6]
        static let carryTo = [0.0, -100.0]
        static let turn = [1.95, 3.0]
        static let turnBy = 100.0
        static let lockAt = 6.6
        static let resumeOpen = 6.2
    }
}

enum GlassLockKeyPalette {
    static let bgTop = UIColor(red: 0x10 / 255, green: 0x16 / 255, blue: 0x14 / 255, alpha: 1)
    static let bgBottom = UIColor(red: 0x0A / 255, green: 0x0D / 255, blue: 0x0C / 255, alpha: 1)
    static let pool = UIColor(red: 206 / 255, green: 226 / 255, blue: 214 / 255, alpha: 0.10)
    static let shadow = UIColor.black.withAlphaComponent(0.70)
    static let brassHigh = UIColor(red: 0xF3 / 255, green: 0xD5 / 255, blue: 0x8C / 255, alpha: 1)
    static let brass = UIColor(red: 0xC9 / 255, green: 0x97 / 255, blue: 0x3E / 255, alpha: 1)
    static let brassDeep = UIColor(red: 0x8A / 255, green: 0x5E / 255, blue: 0x1F / 255, alpha: 1)
    static let brassSide = UIColor(red: 0x9A / 255, green: 0x6A / 255, blue: 0x26 / 255, alpha: 1)
    static let brassSideDeep = UIColor(red: 0x5E / 255, green: 0x3E / 255, blue: 0x12 / 255, alpha: 1)
    static let brassEdge = UIColor(red: 1, green: 236 / 255, blue: 190 / 255, alpha: 0.75)
    static let engrave = UIColor(red: 70 / 255, green: 44 / 255, blue: 10 / 255, alpha: 0.55)
    static let engraveLight = UIColor(red: 1, green: 238 / 255, blue: 196 / 255, alpha: 0.45)
    static let steelHigh = UIColor(red: 0xF4 / 255, green: 0xF6 / 255, blue: 0xF8 / 255, alpha: 1)
    static let steel = UIColor(red: 0xA9 / 255, green: 0xB0 / 255, blue: 0xB8 / 255, alpha: 1)
    static let steelDeep = UIColor(red: 0x59 / 255, green: 0x60 / 255, blue: 0x69 / 255, alpha: 1)
    static let steelEdge = UIColor.white.withAlphaComponent(0.85)
    static let plug = UIColor(red: 0xB9 / 255, green: 0x8A / 255, blue: 0x36 / 255, alpha: 1)
    static let slot = UIColor(red: 0x1A / 255, green: 0x12 / 255, blue: 0x08 / 255, alpha: 1)
    static let glow = UIColor(red: 1, green: 214 / 255, blue: 140 / 255, alpha: 0.55)
    static let glint = UIColor(red: 1, green: 250 / 255, blue: 230 / 255, alpha: 0.8)
    static let ink = UIColor(red: 0xEC / 255, green: 0xF1 / 255, blue: 0xEE / 255, alpha: 1)
    static let mute = UIColor(red: 0xEC / 255, green: 0xF1 / 255, blue: 0xEE / 255, alpha: 0.56)
    static let faint = UIColor(red: 0xEC / 255, green: 0xF1 / 255, blue: 0xEE / 255, alpha: 0.34)
    static let error = UIColor(red: 0xF0 / 255, green: 0x8A / 255, blue: 0x7A / 255, alpha: 1)
    static let sheet = UIColor(red: 0x14 / 255, green: 0x1A / 255, blue: 0x18 / 255, alpha: 1)
    static let sheetEdge = UIColor.white.withAlphaComponent(0.08)
    static let sheetLine = UIColor.white.withAlphaComponent(0.07)
    static let rowIcon = UIColor(red: 233 / 255, green: 205 / 255, blue: 140 / 255, alpha: 0.12)
    static let rowIconInk = UIColor(red: 0xE9 / 255, green: 0xCD / 255, blue: 0x8C / 255, alpha: 1)
    static let button = ink
    static let buttonInk = bgTop
}

enum GlassLockKeyAssets {
    private static let registered: Void = {
        for resource in [
            "GlassLockKey-InstrumentSerif-Regular",
            "GlassLockKey-Inter-Regular",
            "GlassLockKey-Inter-Medium",
            "GlassLockKey-Inter-SemiBold",
        ] {
            guard let url = Bundle.main.url(forResource: resource, withExtension: "ttf") else {
                assertionFailure("Missing Glass Lock Key font: \(resource).ttf")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func registerFonts() { _ = registered }
    static func serif(_ size: CGFloat) -> Font { registerFonts(); return .custom("InstrumentSerif-Regular", fixedSize: size) }
    static func regular(_ size: CGFloat) -> Font { registerFonts(); return .custom("Inter24pt-Regular", fixedSize: size) }
    static func medium(_ size: CGFloat) -> Font { registerFonts(); return .custom("Inter-Medium", fixedSize: size) }
    static func semibold(_ size: CGFloat) -> Font { registerFonts(); return .custom("Inter-SemiBold", fixedSize: size) }

    static func uiFont(_ name: String, size: CGFloat) -> UIFont {
        registerFonts()
        return UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
    }
}
