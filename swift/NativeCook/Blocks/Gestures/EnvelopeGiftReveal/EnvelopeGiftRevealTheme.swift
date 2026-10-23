import SwiftUI

/// SwiftUI-facing aliases for the approved React Native theme tokens.
enum EnvelopeGiftRevealTheme {
    private typealias T = EnvelopeGiftRevealTokens

    static let displayFont = T.displayFont

    enum Space {
        static let xs = T.Space.xs
        static let sm = T.Space.sm
        static let md = T.Space.md
        static let lg = T.Space.lg
        static let side = T.Space.side
        static let xl = T.Space.xl
        static let xxl = T.Space.xxl
    }
}

enum EnvelopeGiftRevealColors {
    private typealias T = EnvelopeGiftRevealTokens.Colors

    static let lime = Color(hex: T.lime)
    static let limeHigh = Color(hex: T.limeHigh)
    static let limeLow = Color(hex: T.limeLow)
    static let limeEdge = Color(hex: T.limeEdge)
    static let limeInside = Color(hex: T.limeInside)
    static let flapHigh = Color(hex: T.flapHigh)
    static let flapLow = Color(hex: T.flapLow)
    static let sideHigh = Color(hex: T.sideHigh)
    static let sideLow = Color(hex: T.sideLow)
    static let sideRight = Color(hex: T.sideRight)
    static let bottomHigh = Color(hex: T.bottomHigh)
    static let bottomLow = Color(hex: T.bottomLow)
    static let crease = Color.rgba(T.crease.red, T.crease.green, T.crease.blue, T.crease.alpha)
    static let ink = Color(hex: T.ink)
    static let white = Color(hex: T.white)
    static let sealMark = Color(hex: T.sealMark)
    static let message = Color(hex: T.message)
    static let messageInk = Color(hex: T.messageInk)
    static let shadow = Color.rgba(T.shadow.red, T.shadow.green, T.shadow.blue, T.shadow.alpha)
    static let cardPink = Color(hex: T.cardPink)
    static let confetti = T.confetti.map { Color(hex: $0) }

    static let limeRGB = rgb(T.lime)
    static let whiteRGB = rgb(T.white)

    private static func rgb(_ value: UInt32) -> [Double] {
        [Double((value >> 16) & 0xFF), Double((value >> 8) & 0xFF), Double(value & 0xFF)]
    }
}

enum EnvelopeGiftRevealMetrics {
    private typealias T = EnvelopeGiftRevealTokens.Layout

    static let side = T.side
    static let headline = T.headline
    static let headlineLine = T.headlineLine
    static let headlineTop = T.headlineTop
    static let title = T.title
    static let titleLine = T.titleLine
    static let envW = T.envW
    static let envH = T.envH
    static let envTop = T.envTop
    static let sealD = T.sealD
    static let sealY = T.sealY
    static let flapTip = T.flapTip
    static let buttonD = T.buttonD
    static let buttonCentre = T.buttonCentre
    static let cardW = T.cardW
    static let cardH = T.cardH
    static let cardR = T.cardR
    static let cardInScale = T.cardInScale
    static let titleGap = T.titleGap
    static let finalTop = T.finalTop
    static let finalSide = T.finalSide
    static let cardGap = T.cardGap
    static let ctaGap = T.ctaGap
    static let messageH = T.messageH
    static let buttonH = T.buttonH
}

struct EnvelopeGiftRevealScreenLayout {
    let headlineTop: Double
    let envTop: Double
    let buttonY: Double
    let titleTop: Double
    let cardTop: Double
    let messageTop: Double
    let ctaTop: Double

    init(height: Double, top: Double, bottom _: Double) {
        typealias M = EnvelopeGiftRevealMetrics
        typealias S = EnvelopeGiftRevealTokens.Scene
        let shift = (height - S.referenceHeight) / 2
        headlineTop = max(top + S.safeTopGap, M.headlineTop + shift)
        envTop = M.envTop + shift
        buttonY = M.buttonCentre + shift
        titleTop = max(top + S.safeTopGap, M.finalTop + shift)
        cardTop = titleTop + M.titleLine * 2 + M.titleGap
        messageTop = cardTop + M.cardH + M.cardGap
        ctaTop = messageTop + M.messageH + M.ctaGap
    }
}

enum EnvelopeGiftRevealCopy {
    private typealias T = EnvelopeGiftRevealTokens.Gift

    static let amount = T.amount
    static let value = T.value
    static let message = T.message
    static let cta = T.cta
    static let titleLines = T.title.components(separatedBy: "\n")
    static let headlineLines = T.headline.components(separatedBy: "\n")
    static let sealText = T.sealText
}
