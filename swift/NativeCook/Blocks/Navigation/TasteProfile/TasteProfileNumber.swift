import SwiftUI

/// A number of the board (a stat or the photo count): whole digits at rest, split flaps while it changes.
struct TasteProfileNumber: View {
    typealias M = TasteProfileMotion

    let from: [TasteProfileTheme.Glyph]
    let to: [TasteProfileTheme.Glyph]
    /// Seconds since this number's flaps were due (negative: not yet, or no change).
    let local: Double
    let slotStagger: Double
    let step: Double
    let card: M.Card
    let reduced: Bool
    let tau: Double
    let alignRight: Bool
    let run: TasteProfileTheme.GlyphRun
    let geo: TasteProfileGeo

    var body: some View {
        if reduced || local < 0 {
            plain
        } else {
            flaps
        }
    }

    private var plain: some View {
        let show = reduced ? (tau >= M.Reduced.fade / 2 ? to : from) : from
        let dip = reduced && tau > 0 && tau < M.Reduced.fade ? M.reducedDip(tau) : 1
        return ZStack(alignment: .topLeading) {
            ForEach(show.indices, id: \.self) { index in
                TasteProfileGlyph(character: show[index].character, inkLeft: show[index].inkLeft,
                                  inkWidth: show[index].inkWidth, run: run, color: run.color, geo: geo)
            }
        }
        .opacity(dip)
    }

    private var flaps: some View {
        let n = max(from.count, to.count)
        return ZStack(alignment: .topLeading) {
            ForEach(0..<n, id: \.self) { slot in
                slotView(slot, n: n)
            }
        }
    }

    @ViewBuilder
    private func slotView(_ j: Int, n: Int) -> some View {
        let fi = alignRight ? from.count - n + j : j
        let ti = alignRight ? to.count - n + j : j
        let a: TasteProfileTheme.Glyph? = fi >= 0 && fi < from.count ? from[fi] : nil
        let b: TasteProfileTheme.Glyph? = ti >= 0 && ti < to.count ? to[ti] : nil
        let ca: Character = a?.character ?? " "
        let cb: Character = b?.character ?? " "
        let steps = M.drumSteps(ca, cb)
        let centre = centreOf(a, b)
        let slotLocal = local - Double(j) * slotStagger
        let f = M.flapAt(local: slotLocal, n: steps, step: step)
        let cardA = cardAlpha(steps: steps, slotLocal: slotLocal)
        let spread = card.spread * (alignRight ? -Double(n - 1 - j) : Double(j))
        let shift = M.easeOut(M.clamp01(cardA)) * spread
        let settled = f.angle < 0 && f.bounce < 0.01 && cardA <= 0
        if steps == 0 || slotLocal < -M.Board.digitCardIn || settled {
            let glyph = (steps == 0 || settled) ? b : a
            if let glyph {
                TasteProfileGlyph(character: glyph.character, inkLeft: glyph.inkLeft, inkWidth: glyph.inkWidth,
                                  run: run, color: run.color, geo: geo)
            }
        } else {
            let after = min(f.k + 1, steps)
            let faceA = face(M.drumChar(ca, f.k), k: f.k, steps: steps, a: a, b: b, centre: centre, shift: shift)
            let faceB = face(M.drumChar(ca, after), k: after, steps: steps, a: a, b: b, centre: centre, shift: shift)
            TasteProfileCell(a: faceA, b: faceB,
                             x: geo.p(centre + shift - card.w / 2),
                             y: geo.y(run.baseline - run.digitH - card.padY),
                             w: geo.p(card.w), h: geo.p(run.digitH + 2 * card.padY),
                             deg: f.angle, speed: f.angle >= 0 ? f.speed : f.bounce, persp: M.Board.perspDigit,
                             cardAlpha: cardA, seam: f.angle >= 0 ? cardA : 0,
                             run: run, cardRadius: geo.p(card.r), geo: geo)
        }
    }

    private func centreOf(_ a: TasteProfileTheme.Glyph?, _ b: TasteProfileTheme.Glyph?) -> Double {
        if let a, let b { return (a.inkLeft + a.inkWidth / 2 + b.inkLeft + b.inkWidth / 2) / 2 }
        if let a { return a.inkLeft + a.inkWidth / 2 }
        if let b { return b.inkLeft + b.inkWidth / 2 }
        return 0
    }

    /// The card fades in before the first flap and out after the landing.
    private func cardAlpha(steps: Int, slotLocal: Double) -> Double {
        guard steps > 0 else { return 0 }
        let dIn = M.Board.digitCardIn
        let end = Double(steps) * step
        if slotLocal < end { return M.clamp01((slotLocal + dIn) / dIn) }
        return 1 - M.clamp01((slotLocal - end - M.Board.bounce.dur * 0.5) / M.Board.digitCardOut)
    }

    private func face(_ c: Character, k: Int, steps: Int, a: TasteProfileTheme.Glyph?, b: TasteProfileTheme.Glyph?,
                      centre: Double, shift: Double) -> TasteProfileFace {
        if k == 0, let a { return .digit(a.character, inkLeft: a.inkLeft + shift, inkWidth: a.inkWidth) }
        if k == steps, let b { return .digit(b.character, inkLeft: b.inkLeft + shift, inkWidth: b.inkWidth) }
        let width = c == " " ? 0 : (TasteProfileTheme.GlyphToken.digitWidth[c, default: 33] * (run.digitH / 36)).rounded()
        return .digit(c, inkLeft: centre + shift - width / 2, inkWidth: width)
    }
}
