import SwiftUI

struct SparkSphereScoreFrame: View {
    let scene: SparkSphereScoreScene
    let g: SparkSphereScoreLayout
    let art: SparkSphereScoreArt
    let pixelScale: Double
    let reduce: Bool
    private typealias M = SparkSphereScoreMotion
    private typealias R = SparkSphereScoreResources

    var body: some View {
        let r = g.discR, origin = CGPoint(x: g.discCx - 1.5 * r, y: g.discCy - 1.5 * r)
        ZStack(alignment: .topLeading) {
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.sparkSphereScoreGround(.float2(0, 0), .floatArray(groundUniforms)))
                .frame(width: g.width, height: g.height, alignment: .topLeading)
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.sparkSphereScoreDisc(.float2(origin.x, origin.y), .floatArray(scene.discUniforms(g, pixelScale, reduce))))
                .frame(width: 3 * r, height: 3 * r, alignment: .topLeading)
                .offset(x: origin.x, y: origin.y)
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in drawCard(c) }
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.sparkSphereScoreRuler(.float2(0, g.rulerCy - 26 * g.sc), .floatArray(scene.rulerUniforms(g))))
                .frame(width: g.width, height: 52 * g.sc, alignment: .topLeading)
                .offset(y: g.rulerCy - 26 * g.sc)
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                let label = cursorLabel
                let slow = M.smooth(g.x(70), g.x(82), label.cx - label.half)
                let fast = 1 - M.smooth(g.x(320), g.x(332), label.cx + label.half)
                drawReadout(c)
                drawCursor(c, label)
                art.draw(c, scene, slow: slow, fast: fast)
            }
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
    private var groundUniforms: [Float] {
        let values: [Double] = [g.discCy + 54 * g.sc, g.discCy + 192 * g.sc, 0, 1,
                                8.0 / 255, 8.0 / 255, 10.0 / 255, 17.0 / 255, 21.0 / 255, 24.0 / 255]
        return values.map(Float.init)
    }
    private func drawCard(_ c: GraphicsContext) {
        let nx = M.clamp(g.rulerX + (scene.card + 1) / 0.05 * g.pitch + 16 * g.s, g.cardX + 30 * g.s, g.cardX + g.cardW - 30 * g.s)
        let top = g.cardTop
        c.fill(Path(roundedRect: CGRect(x: g.cardX, y: top, width: g.cardW, height: g.cardH), cornerRadius: g.cardR),
               with: .color(R.color(0x272B2E)))
        // The source notch overlaps the card by one point, covering the antialias boundary.
        var path = Path()
        path.move(to: CGPoint(x: nx - g.notchHalf, y: top + 1))
        path.addLine(to: CGPoint(x: nx - 1.8, y: top - g.notchH + 2.2))
        path.addQuadCurve(to: CGPoint(x: nx + 1.8, y: top - g.notchH + 2.2), control: CGPoint(x: nx, y: top - g.notchH - 0.4))
        path.addLine(to: CGPoint(x: nx + g.notchHalf, y: top + 1)); path.closeSubpath()
        c.fill(path, with: .color(R.color(0x272B2E)))
    }
    private var cursorLabel: (num: String, nw: Double, uw: Double, cx: Double, half: Double) {
        let num = M.fixed1(M.clamp(scene.v, -1, 3))
        let nw = art.cursorFont.width(num), uw = art.unitFont.width("x")
        let half = (nw + uw) / 2 * (1 + 0.08 * scene.grab)
        let cx = M.clamp(scene.cursorX(g), g.x(16) + half, g.x(386) - half)
        return (num, nw, uw, cx, half)
    }
    private func drawCursor(_ c: GraphicsContext, _ label: (num: String, nw: Double, uw: Double, cx: Double, half: Double)) {
        var context = c
        let base = g.yP(594.6), scale = 1 + 0.08 * scene.grab, left = label.cx - (label.nw + label.uw) / 2
        context.translateBy(x: label.cx, y: base); context.scaleBy(x: scale, y: scale)
        context.translateBy(x: -label.cx, y: -base)
        R.draw(context, art.cursorFont.line(label.num), left, base, R.color(0xF7FBFC))
        R.draw(context, art.unitFont.line("x"), left + label.nw, base, R.color(0xF7FBFC))
    }
    private func drawReadout(_ c: GraphicsContext) {
        let s = M.score(scene.score), font = art.scoreFont, slot = font.width("0"), dot = font.width(".")
        let vis = M.smooth(9, 9.95, s), left = g.discCx - (vis * slot + slot + dot + slot) / 2
        let base = g.centre(-8.1), size = 44.3 * g.ts * g.sc, cell = size * 0.62
        var context = c
        context.clip(to: Path(CGRect(x: g.discCx - 4 * slot, y: base - size * 0.711 - size * 0.24,
                                   width: 8 * slot, height: size * 0.711 + size * 0.42)))
        digit(context, s, 2, left, slot, base, cell, vis)
        digit(context, s, 1, left + vis * slot, slot, base, cell, 1)
        R.draw(context, font.line("."), left + vis * slot + slot, base, R.color(0xFDFEFE))
        digit(context, s, 0, left + vis * slot + slot + dot, slot, base, cell, 1)
        let delta = M.score(scene.card) - 32.2, number = M.fixed1(abs(delta))
        let word = delta > 0.049 ? "years older" : "years younger"
        let nw = art.deltaFont.width(number), ww = art.wordFont.width(word), gap = 4 * g.ts * g.sc
        let x = g.discCx - (nw + gap + ww) / 2, dy = g.centre(40.8)
        let rgb = M.colour(scene.card).map { M.jsRound($0 * 255) / 255 }, color = R.color(rgb)
        R.draw(c, art.deltaFont.line(number), x, dy, color)
        R.draw(c, art.wordFont.line(word), x + nw + gap, dy, color)
    }
    private func digit(_ c: GraphicsContext, _ s: Double, _ k: Int, _ x: Double, _ slot: Double, _ baseline: Double, _ cell: Double, _ vis: Double) {
        let q = k == 0 ? s * 10 : k == 1 ? s : s / 10
        let pos = reduce ? M.jsRound(q) : k == 0 ? q : floor(q) + M.smooth(1 - pow(0.1, Double(k)), 1, q - floor(q))
        let frac = k == 0 ? M.smooth(0.7, 1, pos - floor(pos)) : pos - floor(pos)
        let current = Int(floor(pos + 1e-6)), d = ((current % 10) + 10) % 10
        let aCur = (1 - M.smooth(0, 0.6, frac)) * vis, aNext = M.smooth(0.4, 1, frac) * vis
        func draw(_ digit: Int, _ y: Double, _ alpha: Double, _ blur: Double) {
            guard alpha > 0 else { return }
            let line = art.scoreFont.line(String(digit)), dx = x + (slot - line.width) / 2
            if reduce { R.draw(c, line, dx, y, R.color(0xFDFEFE), alpha) }
            else {
                c.drawLayer { layer in
                    layer.addFilter(.blur(radius: max(0.001, blur)))
                    R.draw(layer, line, dx, y, R.color(0xFDFEFE), alpha)
                }
            }
        }
        if reduce { draw(d, baseline, aCur, 0) }
        else {
            draw(d, baseline - frac * cell * 0.45, aCur, frac * cell * 0.07)
            draw((d + 1) % 10, baseline + (1 - frac) * cell * 0.45, aNext, (1 - frac) * cell * 0.07)
        }
    }
}
