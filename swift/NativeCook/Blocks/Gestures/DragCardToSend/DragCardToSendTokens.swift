import SwiftUI

enum DragCardToSendTokens {
    static let loop: Double = 10
    static let maximumFrameDelta = 0.1
    static let resumeAfter = 2.0
    static let referenceSize = CGSize(width: 402, height: 874)

    enum ColorToken {
        static let page = Color(hex: 0xDCE5EC)
        static let ink = Color(hex: 0x0B0C10)
        static let mute = Color(hex: 0x7A808C)
        static let rowFill = Color(hex: 0xCCD0DA)
        static let rowIcon = Color(hex: 0xABAFBB)
        static let headerButton = Color(hex: 0xDDE1E7)
        static let card = Color(hex: 0x0A1FE8)
        static let cardHigh = Color(hex: 0x0C28F2)
        static let cardDeep = Color(hex: 0x0006A8)
        static let cardInk = Color.white
        static let cardMute = Color.white.opacity(0.62)
        static let cardFaint = Color.white.opacity(0.28)
        static let cardButton = Color(red: 0, green: 10 / 255, blue: 120 / 255).opacity(0.34)
        static let chip = Color(hex: 0xD9DEFF)
        static let island = Color.black
        static let sheetInk = Color.white
        static let sheetMute = Color(hex: 0x7D828C)
        static let sheetLine = Color.white.opacity(0.08)
        static let sheetHover = Color.white.opacity(0.09)
        static let chipOff = Color(hex: 0x1C1D21)
        static let chipOn = Color.white
        static let ring = Color(hex: 0x3D5BFF)
        static let next = Color(hex: 0x0010FF)
        static let success = Color(hex: 0x3DDC84)
        static let positive = Color(hex: 0x119C55)
        static let shadow = Color(hex: 0x0A1A8C)
    }

    static let type = (balance: 32.0, cardSmall: 12.0, cardLabel: 11.0, brand: 20.0, title: 22.0, rowName: 15.0, rowMeta: 12.0, sheetTitle: 32.0, sheetSection: 22.0, favName: 12.0, contact: 15.0, chip: 14.0, pill: 15.0, hint: 13.0)
    static let space = (xs: 4.0, sm: 8.0, md: 12.0, lg: 16.0, xl: 20.0, xxl: 24.0)
    static let layout = (side: 50.0, pillTop: 5.0, pillWidth: 300.0, pillHeight: 48.0, islandWidth: 126.0, islandHeight: 37.0, islandTop: 11.0, headerCenter: 96.5, headerButton: 44.0, headerSpread: 26.5, cardTop: 160.0, dragLabelTop: 305.0, cardHeight: 272.0, cardRadius: 26.0, cardPad: 20.0, actionSize: 44.0, listTitleTop: 480.0, rowTop: 557.0, rowHeight: 63.0, rowGap: 12.0, rowIcon: 40.0, avatarSmall: 28.0, avatarOverlap: 10.0)
    static let sheet = (inset: 19.0, top: 89.0, bottomGap: 40.0, radius: 40.0, pad: 37.0, close: 40.0, closeRight: 18.0, titleTop: 36.5, titleSize: 47.0, titleLine: 50.0, favTop: 160.0, favSize: 42.0, favPad: 12.0, favNudge: [1.7, -2.0, -6.3, -2.3], sectionTop: 271.0, rowsTop: 347.0, rowHeight: 59.0, contactSize: 39.0, rowAvatarX: 54.0, nextTop: 681.5, nextHeight: 52.0, nextWidth: 281.0)
    static let drop = (width: 230.0, height: 342.0, top: 40.0, fillet: 22.0, neckBreak: 22.0)
    static let tokenScale = 0.42
    static let pull = (full: 400.0, commit: 380.0, sideFollow: 0.35, upResist: 0.25, shrink: 0.01, fade: 0.0, dropOpacity: 0.97, ghostLead: 95.0, listSink: 0.05, deep: 0.14, arrow: 0.9)
    static let ambient = (sheen: 6.5, sparks: 12, sparkReach: 46.0)
    static let press = (scale: 0.97, duration: 0.09, inDuration: 0.1, outDuration: 0.16)
    static let springOpen = (stiffness: 170.0, damping: 19.0, mass: 1.0)
    static let springRetract = (stiffness: 300.0, damping: 15.0, mass: 1.0)
    static let springCard = (stiffness: 380.0, damping: 30.0, mass: 1.0)
    static let springToken = (stiffness: 420.0, damping: 28.0, mass: 0.8)
    static let springHover = (stiffness: 520.0, damping: 26.0, mass: 0.7)
    static let springEnter = (stiffness: 260.0, damping: 22.0, mass: 1.0)
    static let springCheck = (stiffness: 520.0, damping: 18.0, mass: 0.7)
    static let magnet = (radius: 64.0, over: 48.0, pull: 0.6, targetScale: 1.2)
    static let send = (absorb: 0.34, absorbScale: 0.08, ring: 0.62, hold: 0.42, close: 0.38, enterFrom: 0.92, fade: 0.22, roll: 0.7, undo: 4.0, pillSwap: 0.24)
    static let curve = (absorb: (0.77, 0.0, 0.175, 1.0), close: (0.23, 1.0, 0.32, 1.0))
    static let fade = (contentFrom: 0.75, contentTo: 1.15, homeDim: 0.3, reduced: 0.2, ghost: 0.4)
    static let script = (down: 0.6, pullEnd: 1.9, pullBy: 390.0, hoverStart: 2.3, hoverArrive: 3.3, release: 3.7, undoTap: 7.9)
    static let qaSlowmo = 1.0
    static let qaHoldAt: Double? = nil
    static let qaSheet = false

    static let card = (digitHeight: 38.0, chipWidth: 30.0, chipHeight: 22.0, chipRadius: 5.0, chipTop: 20.0, numberTop: 8.0, holderTop: 8.0, actionsBottom: 24.5, actionWidth: 64.0, sheenAngle: 18.0, sheenWidth: 0.45, sheenStart: 0.2)
    static let home = (gridWidth: 14.0, dot: 3.3, dotGap: 2.0, listTitleInset: 2.0, titleLine: 26.0, titleButton: 40.0, rowInset: 10.0, rowTextGap: 18.0, rowDateGap: 2.0, rowAmount: 13.0, rowGlyph: 22.0)
    static let island = (sideLeft: 16.0, sideRight: 10.0, receiptGap: 6.0, tick: 18.0, undoHeight: 30.0, undoPad: 14.0, undoFont: 14.0, barInset: 24.0, barBottom: 4.0, barHeight: 2.0)
    static let sheetStyle = (favNameTop: 10.0, addBorder: 1.5, sectionLine: 26.0, rowHorizontal: 18.5, contactGap: 14.0, nextFont: 15.0, ringLine: 3.0, spark: 6.0, badge: 22.0, badgeBorder: 2.0)
    static let replica = (pullFrame: 263.0, spreadProgress: 0.66, sheetMorphSpan: 0.6, icon: 18.0, maxRows: 5, rowMinGap: 4.0, listSpringResponse: 0.32, listSpringDamping: 0.86)
    static let autoplay = (pressBeat: 0.1, pullX: 6.0, startingDrop: 0.975, target: 2, arcX: 70.0, arcY: 30.0, hoverStart: 0.78, hoverSpan: 0.22, hoverPeak: 1.7, peakStart: 0.7, peakSpan: 0.3, fadeStart: 0.6, fadeSpan: 0.4, popDuration: 0.45, pillSwapStart: 0.55)
    static let gesture = (swayVelocity: 0.006, swayLimit: 7.0, lightPull: 20.0, rowDistanceWeight: 1.6, allowableMovement: 10_000.0, minimumVelocityDelta: 1.0 / 240.0)
    static let shape = (bodyStartWidth: 120.0, bodyStartHeight: 40.0, bodyStartTop: 8.0, overshoot: 1.08, clipInset: 6.0, pillTransition: 1.6, neckTop: 44.0, neckBottom: 64.0, neckTopRatio: 0.22, neckBottomRatio: 0.28, neckTopInset: 4.0, neckBottomInset: 6.0, neckWaist: 0.7, arrowY: 0.42)
    static let effects = (glassStart: 0.05, glassSpan: 0.3, glassEnd: 1.1, glassEndSpan: 0.5, arrowIn: 0.4, arrowOut: 0.9, arrowOutSpan: 0.2, nextStart: 1.3, nextSpan: 0.4, sparkShrink: 0.6, checkOpacity: 3.0, badgeOffset: 0.36, initialsScale: 0.32)
    static let cardStyle = (brandTracking: 1.0, balanceTracking: -0.5, balanceYOffset: 3.0, validFont: 9.0, validTracking: 0.8, holderFont: 10.0, holderTracking: 1.0, sheenAmplitude: 0.35, sheenPull: 0.9, sheenSway: 20.0, sheenAlpha: 0.12, sheenHeight: 1.5, shadowBase: 0.18, shadowLift: 0.22, radiusBase: 18.0, radiusLift: 14.0, shadowY: 14.0)

    struct Person: Identifiable {
        let id: String
        let name: String
        let initials: String
        let asset: String?
    }

    struct Transaction: Identifiable {
        let id: String
        let name: String
        let date: String
        let amount: Int
        let incoming: Bool
    }

    static let favourites = [
        Person(id: "rafi", name: "Rafi", initials: "R", asset: "DragCardToSend-fav-1.jpg"),
        Person(id: "nico", name: "Nico", initials: "N", asset: "DragCardToSend-fav-2.jpg"),
        Person(id: "samir", name: "Samir", initials: "S", asset: "DragCardToSend-fav-3.jpg")
    ]
    static let contacts = [
        Person(id: "ada", name: "Ada Fenwick", initials: "AF", asset: "DragCardToSend-face-1.png"),
        Person(id: "ravi", name: "Ravi Okafor", initials: "RO", asset: "DragCardToSend-face-2.png"),
        Person(id: "lena", name: "Lena Marsh", initials: "LM", asset: "DragCardToSend-face-3.png"),
        Person(id: "joon", name: "Joon Park", initials: "JP", asset: "DragCardToSend-face-4.png"),
        Person(id: "sofia", name: "Sofia Reyes", initials: "SR", asset: "DragCardToSend-face-5.png")
    ]
    static let amounts = [20, 50, 120, 250]
    static let defaultAmountIndex = 2
    static let startBalance = 1_500_075
    static let transactions = [
        Transaction(id: "t1", name: "Northwind Market", date: "Oct 12, 2026", amount: -10_356, incoming: false),
        Transaction(id: "t2", name: "Salary · Harbor Co", date: "Oct 10, 2026", amount: 100_097, incoming: true),
        Transaction(id: "t3", name: "Fern Coffee", date: "Oct 9, 2026", amount: -650, incoming: false),
        Transaction(id: "t4", name: "Juniper Books", date: "Oct 8, 2026", amount: -2_480, incoming: false)
    ]
}
