import SwiftUI

enum GiftSlotDropTokens {
    enum Colors {
        static let stage = "#605133", stageEdge = "#302816", floor = "#332A13", floorLow = "#2D2511"
        static let floorLine = "rgba(255,240,190,0.08)", pocket = "#251902", pocketFade = "rgba(37,25,2,0)", pocketLow = "#4F3803"
        static let ink = "#FFFFFF", mute = "#B9B3A0", hint = "#CFCABB", stageWarmInk = "#E07A2A"
        static let pocketLight = "rgba(255,224,60,1)", glowCore = "#F7B812", offerWarm = "rgba(214,200,140,0.28)"
        static let cardShadeInk = "#8A4C00", yellow = "#FFD23F", yellowDeep = "#F2A900", outline = "rgba(255,255,255,0.42)"
        static let button = "#FCD43C", buttonInk = "#2A2112", step = "#D8D3C4"
        static let hueTint = 0.05, stageWarm = 0.3, cardLift = 0.2
        static let cardShade = (base: 0.2, multiply: 0.25)
    }

    enum Space {
        static let xs: CGFloat = 4, sm: CGFloat = 8, md: CGFloat = 12, lg: CGFloat = 16
        static let xl: CGFloat = 20, xxl: CGFloat = 24, gutter: CGFloat = 28
    }

    enum Layout {
        static let headerTop: CGFloat = 63, offerHeaderTop: CGFloat = 25, headerLeft: CGFloat = 14, headerRight: CGFloat = 50
        static let giftY: CGFloat = 144, giftDx: CGFloat = -2.5, giftSize: CGFloat = 47
        static let titleY: CGFloat = 214, titleSize: CGFloat = 25.5, titleLine: CGFloat = 34
        static let cardY: CGFloat = 427, cardW: CGFloat = 158, cardH: CGFloat = 206, cardR: CGFloat = 12
        static let spacing: CGFloat = 172, turnDeg: CGFloat = 42, sideScale: CGFloat = 1, chevronY: CGFloat = 605
        static let pocketW: CGFloat = 180, pocketTop: CGFloat = 634, floorY: CGFloat = 669
        static let slotY: CGFloat = 678, slotW: CGFloat = 170, slotH: CGFloat = 24
        static let glowDrop: CGFloat = 21, glowTop: CGFloat = -14, glowH: CGFloat = 92, pocketDepth: CGFloat = 28
        static let hintY: CGFloat = 764.5, offerY: CGFloat = 366, offerW: CGFloat = 292, offerH: CGFloat = 505
        static let howTop: CGFloat = 669, stepTop: CGFloat = 716, buttonTop: CGFloat = 780, buttonH: CGFloat = 60, buttonW: CGFloat = 357
    }

    enum TypeStyle {
        static let header: CGFloat = 14, hint: CGFloat = 17.8, howTitle: CGFloat = 17.3
        static let stepNumber: CGFloat = 40, stepText: CGFloat = 18.4, stepLine: CGFloat = 24, button: CGFloat = 16
        static let mark: CGFloat = 12, cardBig: CGFloat = 30, cardLine: CGFloat = 15
        static let offerBig: CGFloat = 52, offerLine: CGFloat = 28, offerSmall: CGFloat = 15, offerMark: CGFloat = 20
    }

    struct Gift: Identifiable, Equatable {
        let id: String
        let name: String
        let wordmark: String
        let headline: String
        let line: String
        let cap: String
        let use: String
        let expires: String
        let ink: String
        let glow: String
    }

    static let gifts = [
        Gift(id: "white", name: "Loomwell", wordmark: "loomwell", headline: "50%", line: "cashback", cap: "up to $20", use: "Use AERO Pay at Loomwell", expires: "Expires on 27 Nov", ink: "#22201C", glow: "#E9E4DA"),
        Gift(id: "parcels", name: "Nimbus", wordmark: "nimbus", headline: "50%", line: "cashback", cap: "up to $25", use: "Use AERO Pay to shop on Nimbus", expires: "Expires on 27 Nov", ink: "#1A2433", glow: "#9CCADC"),
        Gift(id: "burger", name: "Bunbox", wordmark: "BUNBOX", headline: "50%", line: "cashback", cap: "up to $10", use: "Order on Bunbox and pay with AERO Pay", expires: "Expires on 27 Nov", ink: "#FFFFFF", glow: "#E8612A"),
        Gift(id: "red", name: "Volta", wordmark: "volta", headline: "50%", line: "cashback on", cap: "mobile recharge", use: "Recharge your Volta number with AERO Pay", expires: "Expires on 27 Nov", ink: "#FFFFFF", glow: "#D9141C")
    ]
    static let startIndex = 2

    enum Drag {
        static let decide: CGFloat = 8, commit: CGFloat = 170, full: CGFloat = 250
        static let minScale = 0.92, leanDeg: CGFloat = 64, lift = 1.03, upResist = 0.2, swipeVelocity: CGFloat = 600
    }

    struct Spring { let stiffness: Double, damping: Double, mass: Double }
    enum Springs {
        static let snap = Spring(stiffness: 260, damping: 28, mass: 1)
        static let back = Spring(stiffness: 320, damping: 26, mass: 1)
        static let centre = Spring(stiffness: 420, damping: 30, mass: 0.8)
        static let rise = Spring(stiffness: 170, damping: 20, mass: 1)
        static let lift = Spring(stiffness: 500, damping: 30, mass: 0.6)
    }

    enum Insert { static let centreMs = 160.0, sinkMs = 300.0, flashMs = 420.0; static let sinkEase = (0.77, 0.0, 0.175, 1.0) }
    enum Activate { static let ms = 1_150.0, dotMs = 280.0 }
    enum Print {
        static let feeds = 3, confetti = 72
        static let feedShare = 0.22, feedMs = 260.0, feedGap = 90.0, feedEaseBack = 1.6, startScale = 0.44
        static let rollMs = 900.0, detailsDelay = 280.0, stagger = 60.0, confettiLife = 1.6, gravity = 820.0
    }
    enum Ambient {
        static let floatPt: CGFloat = 3, floatMs = 3_000.0, rollPerPt = 0.08, rollMax: CGFloat = 7
        static let band = 0.4, bandMs = 6_000.0, slotBreathMs = 2_400.0
    }
    enum Chevron { static let bobPt: CGFloat = 4, bobMs = 1_400.0, gap: CGFloat = 18 }
    enum Close { static let ms = 260.0, returnMs = 420.0; static let ease = (0.23, 1.0, 0.32, 1.0) }
    enum ButtonPress { static let scale = 0.965, inMs = 80.0, outMs = 120.0 }

    static let reducedMs = 200.0
    static let loop = 15.0
    static let resumeAfterMs = 3_500.0
    enum Script {
        static let swipes = [(at: 1_000.0, ms: 450.0, dx: 200.0), (at: 12_700.0, ms: 450.0, dx: -200.0)]
        static let pullDown = 2_400.0
        static let pullUp = 3_450.0
        static let pullBy: CGFloat = 252
        static let done = 11_000.0
    }
    static let replicaOnly = false
    static let qaHideFinger = false
    static let qaHoldAt: Double? = nil
    static let qaTimelineStartMs = 0.0
    enum Random {
        static let multiplier = 127.1, saltMultiplier = 311.7, scale = 43_758.5453, confettiSaltRange = 1.9
        static let speedMin = 380.0, speedRange = 520.0, spinRange = 18.0
    }

    static func color(_ source: String) -> Color {
        if source.hasPrefix("#") {
            let value = UInt64(source.dropFirst(), radix: 16) ?? 0
            return Color(red: Double((value >> 16) & 0xFF) / 255, green: Double((value >> 8) & 0xFF) / 255, blue: Double(value & 0xFF) / 255)
        }
        let values = source.replacingOccurrences(of: "rgba(", with: "").replacingOccurrences(of: ")", with: "")
            .split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard values.count == 4 else { return .clear }
        return Color(red: values[0] / 255, green: values[1] / 255, blue: values[2] / 255, opacity: values[3])
    }
}
