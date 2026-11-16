import Foundation

/// Literal port of makeLayout. Horizontal scale and compressed vertical scale are deliberately distinct.
struct SparkSphereScoreLayout: Equatable {
    let width: Double, height: Double, insetTop: Double, insetBottom: Double, fontScale: Double
    let s: Double, sc: Double, k: Double, ts: Double, cd: Double, lines: Int
    let oy: Double, discCy: Double, discR: Double, paceTop: Double
    let cardTop: Double, cardH: Double
    var discCx: Double { width / 2 }
    var discScale: Double { discR / 163.6 }
    func x(_ v: Double) -> Double { (v - 201) * s + width / 2 }
    func yA(_ y: Double) -> Double { oy + y * sc }
    func yP(_ y: Double) -> Double { paceTop + (y - (571.6 - 8.4)) * sc }
    func centre(_ off: Double) -> Double { discCy + off * ts * sc }
    var rulerX: Double { x(33.9) }
    var pitch: Double { 4.174 * s }
    var rulerCy: Double { yP(617.8) }
    var cardX: Double { x(16.9) }
    var cardW: Double { 368.5 * s }
    var cardR: Double { 12 * s }
    var notchH: Double { 11 * sc }
    var notchHalf: Double { 12 * s }
    var cardPad: Double { 16.9 * s }
    var cardTitleBase: Double { cardTop + (721.3 - 685.4) * sc }
    var cardBodyBase: Double { cardTop + (748.6 - 685.4) * sc }
    var rulerTop: Double { yP(590.4) - 14 * sc }
    var rulerZoneH: Double { yP(646.9) + 8 * sc - rulerTop }

    init(width: Double, height: Double, top: Double, bottom: Double, fontScale: Double = 1) {
        self.width = width; self.height = height; insetTop = top; insetBottom = bottom
        s = width / 402
        let fs = min(max(fontScale, 1), 1.2)
        self.fontScale = fs
        let topPad = 4 * s, botPad = 16 * s
        let avail = height - top - bottom - topPad - botPad
        let natGapB = 345.8 - 163.6 - (132.3 + 2)
        let natGapD = 571.6 - 8.4 - (345.8 + 163.6)
        let natGapF = 685.4 - 11 - (646.9 + 4)
        let bodyLinesNat = fs > 1.08 ? 4 : 3
        let natTotal = 810 - 66 + Double(bodyLinesNat - 3) * 20 * fs
        var gapB = natGapB, gapD = natGapD, gapF = natGapF, cd = 1.0
        var lines = bodyLinesNat, cardH = 810 - 685.4 + Double(bodyLinesNat - 3) * 20 * fs, sc = s
        let need = natTotal * s
        if need <= avail {
            let extra = min(max((avail - need) / s - 14, 0), 40)
            gapB += extra * 0.3; gapD += extra * 0.45; gapF += extra * 0.1
        } else {
            var deficit = (need - avail) / s
            func take(_ room: Double) -> Double {
                let t = min(room, max(deficit, 0)); deficit -= t; return t
            }
            gapB -= take(natGapB - 24)
            gapD -= take(natGapD - 26)
            let dr = take(163.6 * 2 * 0.22)
            cd = 1 - dr / (163.6 * 2)
            gapF -= take(natGapF - 12)
            if deficit > 0 && lines > 2 {
                let drop = lines - 2
                deficit -= 20 * Double(drop); lines = 2; cardH -= 20 * Double(drop)
            }
            if deficit > 0 {
                let k = (natTotal - deficit - (lines == 2 ? 20 : 0) - (natGapB - gapB) - (natGapD - gapD)) / natTotal
                sc = s * max(0.78, min(1, k))
            }
        }
        self.sc = sc; k = sc / s; ts = max(0.86, cd); self.cd = cd; self.lines = lines
        oy = top + topPad - 66 * sc
        let discTop = oy + (132.3 + 2) * sc + gapB * sc
        discR = 163.6 * cd * sc
        discCy = discTop + discR
        paceTop = discCy + discR + gapD * sc
        let apex = paceTop + ((646.9 + 4) - (571.6 - 8.4)) * sc + gapF * sc
        cardTop = apex + 11 * sc; self.cardH = cardH * sc
    }
}
