import SwiftUI

/// Literal makeLayout(W,H,insetTop,insetBottom,fontScale) from pjg-theme.ts.
struct PourJarGoalLayout: Equatable {
    let width: Double, height: Double, s: Double, x0: Double, fontScale: Double
    let headerY: Double, buttonY: Double, buttonH: Double, cardsY: Double, cardH: Double
    let subtitleY: Double, amountBaseline: Double, jar: CGRect
    let largeType: Bool, largestType: Bool
    func x(_ n: Double) -> Double { x0 + n * s }
    init(width: Double, height: Double, top: Double, bottom: Double, fontScale: Double) {
        self.width = width; self.fontScale = fontScale
        let s = min(width / 402, 1)
        self.s = s; x0 = (width - 402 * s) / 2
        let h = max(height, fontScale > 2 ? (874 + (fontScale - 2) * 360) * s : height)
        self.height = h
        headerY = max(top < 40 ? top + 8 : 0, (54 - 62) * s + top)
        largeType = fontScale > 1.35; largestType = fontScale > 2
        buttonH = (57 + max(0, fontScale - 2) * 24) * s
        buttonY = h - max(bottom, 12) - 9 - buttonH
        cardH = (largeType ? 2 * max(94, 24 + fontScale * (12 * 0.9 + 23 * 0.95)) + 8 : 81) * s
        cardsY = buttonY - 21 * s - cardH
        subtitleY = cardsY - (largestType ? 15 * fontScale * 2.6 + 16 : 38) * s
        amountBaseline = subtitleY - (largeType ? 28 : 20) * s
        let jarBottom = amountBaseline - 78 * s
        let headerH = (largestType ? 17 * fontScale * 3.3 + 16 : 60) * s
        let jarTop = max(headerY + headerH, jarBottom - 419 * s)
        let jarW = 222 * s, jarH = max(140, jarBottom - jarTop)
        jar = CGRect(x: width / 2 - jarW / 2, y: jarTop, width: jarW, height: jarH)
    }
}
