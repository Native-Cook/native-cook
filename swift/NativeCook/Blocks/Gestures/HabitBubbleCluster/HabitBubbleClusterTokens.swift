import SwiftUI

enum HabitBubbleClusterTokens {
    enum Color {
        static let bg = "#141313"; static let dust = "rgba(255,255,255,0.05)"; static let ink = "#FFFFFF"
        static let inkSoft = "rgba(255,255,255,0.55)"; static let inkFaint = "rgba(255,255,255,0.35)"
        static let control = "#1E1E21"; static let controlSelected = "#FFFFFF"; static let controlInkSelected = "#0A0A0B"
        static let disc = "#FFFFFF"; static let discDim = "rgba(255,255,255,0.1)"; static let personalTop = "#363637"
        static let personalBottom = "#252526"; static let card = "#141415"; static let hairline = "rgba(255,255,255,0.14)"
        static let good = "#5FBF5A"; static let discInk = "#0A0A0B"; static let quiet = "#E0565B"; static let quietCore = "#2A1012"
        static let badge = "#3B6CF6"; static let spark = "#FFFFFF"; static let finger = "rgba(255,255,255,0.32)"; static let fingerRing = "rgba(255,255,255,0.7)"
    }
    struct Hue { let light, inner, rim, core, halo, icon: String }
    static let hue: [String: Hue] = [
        "purple": .init(light: "#C9A2EE", inner: "#7B2BD6", rim: "#8A52C4", core: "#1A0F2B", halo: "rgba(138,82,196,0.45)", icon: "#A94FE0"),
        "green": .init(light: "#A6EDBF", inner: "#3FCB72", rim: "#5BE38E", core: "#0C1F14", halo: "rgba(80,220,140,0.36)", icon: "#5CD487"),
        "yellow": .init(light: "#F6DE93", inner: "#E8B530", rim: "#F7C948", core: "#211A08", halo: "rgba(245,195,70,0.36)", icon: "#EFC04A"),
        "orange": .init(light: "#F6BC8E", inner: "#EE7A2A", rim: "#F58A3C", core: "#22130A", halo: "rgba(245,130,60,0.36)", icon: "#EE9049"),
        "red": .init(light: "#E0888E", inner: "#A92A33", rim: "#C83A44", core: "#220C0E", halo: "rgba(232,82,90,0.34)", icon: "#E25860"),
        "glass": .init(light: "#A8A8B0", inner: "#6C6C74", rim: "#7C7C86", core: "#141416", halo: "rgba(160,160,175,0.14)", icon: "rgba(180,180,190,0.3)")
    ]
    struct Habit { let id, name, icon, hue: String; let r, x, y: Double }
    static let social: [Habit] = [
        .init(id: "wave", name: "Say hi to a neighbour", icon: "hand-left-outline", hue: "purple", r: 78, x: -40, y: -155), .init(id: "star", name: "Thank someone", icon: "star-outline", hue: "purple", r: 78, x: 119, y: -51), .init(id: "gift", name: "Send a small gift", icon: "basket-outline", hue: "red", r: 28, x: 51, y: -223), .init(id: "ball", name: "Team sport", icon: "basketball-outline", hue: "orange", r: 33, x: 78, y: -162), .init(id: "friend", name: "Call a friend", icon: "person-outline", hue: "orange", r: 34, x: -140, y: -100), .init(id: "art", name: "Draw together", icon: "color-palette-outline", hue: "green", r: 53, x: -107, y: -15), .init(id: "host", name: "Host friends", icon: "bed-outline", hue: "yellow", r: 43, x: -7, y: -33), .init(id: "magic", name: "Plan a surprise", icon: "sparkles-outline", hue: "yellow", r: 43, x: -104, y: 87), .init(id: "home", name: "Family dinner", icon: "home-outline", hue: "green", r: 50, x: 110, y: 85), .init(id: "tea", name: "Tea with mum", icon: "cafe-outline", hue: "red", r: 28, x: -130, y: 156), .init(id: "heart", name: "Date night", icon: "heart-outline", hue: "green", r: 53, x: -47, y: 172), .init(id: "wallet", name: "Split the bill", icon: "wallet-outline", hue: "orange", r: 33, x: 44, y: 155), .init(id: "ride", name: "Ride to work", icon: "bicycle-outline", hue: "yellow", r: 43, x: 121, y: 188), .init(id: "note", name: "Write a note", icon: "mail-outline", hue: "glass", r: 25, x: 65, y: 4)
    ]
    static let personal: [Habit] = [.init(id: "journal", name: "Journal", icon: "create-outline", hue: "glass", r: 22, x: 57, y: 96)]
    static let type = (segmentFontSize: 15.0, dateFontSize: 24.0, dateLineHeight: 30.0, nowFontSize: 14.0, nowLineHeight: 18.0, countFontSize: 17.0, categoryFontSize: 12.0, categoryTitleFontSize: 20.0, balanceFontSize: 17.0, goodFontSize: 14.0, sectionTitleFontSize: 24.0, sectionTitleLineHeight: 30.0, bodyFontSize: 17.0, bodyLineHeight: 25.0, personalNowFontSize: 20.0, personalNowLineHeight: 26.0, personalDateFontSize: 36.0, personalDateLineHeight: 42.0, quietFontSize: 9.0)
    static let weight = (segment: Font.Weight.bold, date: Font.Weight.medium, now: Font.Weight.medium, category: Font.Weight.medium, categoryTitle: Font.Weight.bold, balance: Font.Weight.medium, good: Font.Weight.bold, sectionTitle: Font.Weight.semibold, body: Font.Weight.regular, personalNow: Font.Weight.medium, personalDate: Font.Weight.medium, quiet: Font.Weight.bold)
    static let quietGlassBody = 0.22; static let quietGlassWedge = 0.08
    static let glass = (interiorSmall: 0.46, interiorLarge: 0.15, interiorFrom: 45.0, interiorSlope: 0.009, bigRadius: 60.0, bandLarge: 0.95, bandSmall: 0.72, rimLight: 0.8, lightX: 0.06, lightY: -0.08, lightR: 1.02, edge: 0.9, halo: 0.45, chargeLift: 0.3, highlight: 0.06, spriteEdge: 0.45, spriteEdgeGlass: 0.12)
    static let cluster = (x: 190.0, y: 380.0)
    static let referenceWidth = 402.0
    static let atlas = (size: 360.0, columns: 5, reach: 1.45, order: ["wave", "star", "gift", "ball", "friend", "art", "host", "magic", "home", "tea", "heart", "wallet", "ride", "note", "journal"])
    static let render = (liquidSteps: 24, waveStep1: 0.55, waveStep2: 0.9, liquidInset: 1.0, flashTravel: 4.0, flashWidth: 3.0, flashMinWidth: 0.5, rimWidth: 1.5, detailTravel: 24.0, arcTracking: 1.2, quietHaloScale: 1.25, quietHaloAlpha: 0.12, quietBodyAlpha: 0.22, quietStart: 150.0, quietEnd: 290.0, quietTextRadius: 0.68, quietTitleOffset: 0.25, quietHoursOffset: 0.35, quietHoursExtra: 7.0, quietCore: 3.5, quietCoreRing: 11.0, quietCoreRingAlpha: 0.25, ringGlowAlpha: 0.8, ringGlowExtra: 4.0, ringAlpha: 0.95, sparkLarge: 2.6, sparkSmall: 1.8, sparkFade: 0.6, fingerLine: 1.5)
    static let quiet = (r: 55.0, x: 0.0, y: 67.0, title: "QUIET HOURS", hours: "22:00–07:00")
    static let category: [String: String] = ["home":"Personal Life", "heart":"Personal Life", "art":"Creativity", "wave":"Neighbours", "star":"Gratitude", "host":"Friends", "magic":"Friends", "ride":"Health", "friend":"Friends", "ball":"Health", "gift":"Friends", "tea":"Family Life", "wallet":"Money", "note":"Friends", "journal":"Personal Life"]
    static let space = (hair: 2.0, xs: 3.0, s: 4.0, m: 6.0, l: 12.0, xl: 16.0, x2: 18.0, x3: 20.0, cardTop: 27.0)
    static let layout = (gutter: 17.0, headerTop: 1.0, control: 40.0, segmentWidth: 185.0, iconButton: 40.0, clusterTop: 0.0, dateY: 640.0, discY: 792.0, disc: 74.0, dot: 22.0, dotOffset: 60.0, corner: 52.0, cornerInset: 16.0, cornerY: 830.0, gap: 3.0, iconScale: 0.3, ring: 3.0, ringGap: 7.0, fingerRadius: 22.0, sparks: 12.0, personalDateY: 0.654, personalDotX: 0.53, personalDotY: 0.84, detailBalanceY: 0.513, detailCardY: 0.6, rowHeight: 60.0, iconGlow: 10.0)
    static let view = (badge: 18.0, balanceCorner: 14.0, goodHeight: 26.0, cardCorner: 18.0, hairline: 0.5, categorySpacing: 1.0)
    static let copy = (tabs: ["Social", "Personal"], date: "14 October", now: "Now - 08:12", personalDate: "26 October", category: "Category", balance: "Balance", good: "Good", description: "Description", body: ["Small rituals with the people you love keep a week from slipping by. This habit counts the evenings you sit down together, phones away, and asks nothing else of you.", "We look at when you usually manage it and nudge you on the days that fit, never on the busy ones."], avatar: "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=160&h=160&q=80")

    static let physics = (step: 1.0 / 120.0, stiffness: 120.0, damping: 14.0, radiusStiffness: 260.0, radiusDamping: 18.0, separation: 0.5, relaxIterations: 240.0, relaxGravity: 0.04, maxFling: 2400.0, maxFrameMs: 250.0)
    static let simulation = (epsilon: 0.001, activeRadius: 0.5, designTolerance: 0.5, velocityShare: 0.25, reducedRate: 18.0, doneReducedRate: 14.0, doneRate: 9.0, minimumTouch: 22.0)
    static let hold = (delayMs: 120.0, chargeMs: 900.0, drainMs: 220.0, moveTolerance: 10.0, squeeze: 0.94, ticks: [1.0 / 3.0, 2.0 / 3.0], doneScale: 0.62, tapMs: 220.0)
    static let burst = (flashMs: 240.0, sparkMs: 620.0, sparkFrom: 6.0, sparkNear: 28.0, sparkFar: 44.0)
    static let idle = (breathe: 0.015, cyclesMin: 2.0)
    static let hint = (inMs: 180.0, holdMs: 1400.0, outMs: 260.0)
    static let liquid = (period1: 140.0, period2: 230.0, amp1: 3.0, amp2: 1.6, top: 0.75, bottom: 0.5)
    static let wave = (speed: 520.0, reach: 120.0)
    static let stagger = (stepMs: 30.0, per: 60.0)
    struct Scatter { let distance: Double }
    static let scatter = Scatter(distance: 220.0)
    static let detail = (y: 0.333, radius: 70.0, fadeMs: 240.0, fadeOutMs: 160.0, ease: [0.23, 1.0, 0.32, 1.0])
    static let `switch` = (fadeMs: 260.0, stagger: 22.0, reducedMs: 200.0)
    static let loop: Double = 12.0
    static let loopMs = 12000.0
    static let qaHold: String? = nil
    static let qaHoldAt = (rest: 0.0, a: 8300.0, b: 4600.0)
    static let autoplay = (resumeDelayMs: 3500.0, holdId: "star", holdStart: 600.0, holdEnd: 1900.0, openId: "home", openAt: 3200.0, backAt: 5400.0, personalAt: 6800.0, socialAt: 8800.0, tapAt: 10400.0, tapMs: 120.0)
    static let interactionReturnDelayMs = 2000.0
}
