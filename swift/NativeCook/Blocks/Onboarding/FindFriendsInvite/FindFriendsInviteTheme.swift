import SwiftUI

enum FindFriendsInviteTheme {
    static let referenceWidth = 402.0
    static let referenceTop = 62.0
    static let ascent = 0.795
    static let descent = 0.24

    enum FontName {
        static let medium = "AnybodyNC-Medium"
        static let semi = "AnybodyNC-SemiBold"
        static let bold = "AnybodyNC-Bold"
        static let extra = "AnybodyNC-ExtraBold"
    }

    enum ColorToken {
        static let ink = Color(hex: 0x000000)
        static let white = Color(hex: 0xFFFFFF)
        static let card1 = Color(hex: 0xFFFFFF)
        static let card2 = Color(hex: 0xF6F6F7)
        static let placeholder = Color(hex: 0xC4C4C4)
        static let buttonBlack = Color(hex: 0x000000)
        static let planeFold = Color(hex: 0x1E5BEA)
        static let planeInk = Color(hex: 0x0262FD)
        static let avatarPink = Color(hex: 0xF298B9)
        static let avatarGreen = Color(hex: 0xADEEA9)
        static let avatarPurple = Color(hex: 0xCCC0FB)
        static let avatarGrey = Color(hex: 0xD5D5D8)
        static let pageStops: [(Double, Color)] = [
            (0, Color(hex: 0xF6F6F6)), (67, Color(hex: 0xF6F6F6)),
            (100, Color(hex: 0xF4F4F4)), (133, Color(hex: 0xEFEFEF)),
            (200, Color(hex: 0xEBEBEB)), (267, Color(hex: 0xEAEAEA)),
            (350, Color(hex: 0xECECEC)), (400, Color(hex: 0xEFEFEF)),
            (600, Color(hex: 0xEFEFEF)), (700, Color(hex: 0xEDEDED)),
            (767, Color(hex: 0xECECEC)), (833, Color(hex: 0xEAEAEA)),
            (874, Color(hex: 0xE7E7E7))
        ]
        static let inviteStops: [(Double, Color)] = [
            (0, Color(hex: 0x0262FD)), (0.6, Color(hex: 0x0160FE)),
            (0.8, Color(hex: 0x2F52FE)), (0.9, Color(hex: 0x6F3BFF)),
            (0.98, Color(hex: 0x9239F1)), (1, Color(hex: 0x9638EE))
        ]
    }

    enum Space {
        static let cardMargin = 9.0
        static let cardGap = 21.0
        static let cardPadInvite = 32.0
        static let cardPadCheck = 14.7
        static let buttonGap = 8.0
    }

    enum Radius {
        static let card = 38.0
        static let pill = 24.0
        static let invite = 26.5
        static let check = 21.0
    }

    struct TypeStyle {
        let size: Double
        let font: String
        let letterSpacing: Double
        let color: Color
        let lineHeight: Double?

        init(_ size: Double, _ font: String, _ color: Color, letterSpacing: Double = 0, lineHeight: Double? = nil) {
            self.size = size
            self.font = font
            self.color = color
            self.letterSpacing = letterSpacing
            self.lineHeight = lineHeight
        }
    }

    enum TypeToken {
        static let placeholder = TypeStyle(13.6, FontName.medium, ColorToken.placeholder)
        static let h1 = TypeStyle(18, FontName.extra, ColorToken.ink)
        static let h2 = TypeStyle(20.5, FontName.extra, ColorToken.ink)
        static let body = TypeStyle(12.35, FontName.medium, ColorToken.ink, lineHeight: 17)
        static let bodyStrong = TypeStyle(12.6, FontName.semi, ColorToken.ink)
        static let invite = TypeStyle(15.5, FontName.bold, ColorToken.white)
        static let check = TypeStyle(12.85, FontName.semi, ColorToken.white)
    }

    struct Avatar {
        let id: String
        let diameter: Double
        let centerX: Double
        let centerY: Double
        let background: Color
        let head: Double
        let dy: Double
    }

    enum Box {
        static let pill = CGRect(x: 23.3, y: 61.3, width: 310.3, height: 48)
        static let pillShadow = (dy: 4.0, blur: 6.0, opacity: 0.4)
        static let close = (cx: 368.2, cy: 85.3, size: 12.4, stroke: 2.0)
        static let placeholder = (x: 38.7, baseline: 90.7)
        static let card1 = (y: 125.3, height: 274.3)
        static let cup = (cx: 157 + 260.0 / 6, cy: 152 + 342.0 / 6, width: 260.0 / 3, height: 342.0 / 3)
        static let h1Baseline = 286.7
        static let invite = (y: 314.7, height: 53.0)
        static let plane = (x: 138.3, y: 333.0, size: 17.7)
        static let inviteLabel = (x: 167.7, baseline: 347.3)
        static let card2 = (y: 420.7, height: 293.3)
        static let avatars = [
            Avatar(id: "head-3", diameter: 36.3, centerX: 136, centerY: 485.2, background: ColorToken.avatarPink, head: 0.6, dy: -0.01),
            Avatar(id: "head-1", diameter: 67, centerX: 182.7, centerY: 470, background: ColorToken.avatarGreen, head: 0.62, dy: 0),
            Avatar(id: "head-2", diameter: 49, centerX: 236.5, centerY: 478.7, background: ColorToken.avatarPurple, head: 0.85, dy: 0),
            Avatar(id: "head-5", diameter: 25.5, centerX: 271.3, centerY: 485.2, background: ColorToken.avatarGrey, head: 0.77, dy: -0.04)
        ]
        static let h2Baseline = 548.7
        static let bodyBaselines = [594.7, 611.7, 628.7]
        static let lock = (x: 228.3, size: 14.0)
        static let check = (y: 657.0, height: 41.7)
        static let checkLabelBaseline = 683.0
    }

    enum Copy {
        static let placeholder = "search for friends"
        static let h1 = "have friends with good taste?"
        static let invite = "invite them"
        static let h2 = "find your friends"
        static let body1 = "see who's already on mellow by checking your"
        static let body2 = "contacts. "
        static let body2Strong = "we never upload or store your"
        static let body3 = "contacts. "
        static let lock = "🔒"
        static let check = "check contacts"
    }
}

private extension Color {
    init(hex: Int) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255)
    }
}
