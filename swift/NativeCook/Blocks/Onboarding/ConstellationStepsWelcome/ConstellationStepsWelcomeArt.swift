import SwiftUI

/// Screen geometry: the pole the sky turns round (above the screen), the hero centre, the scale.
struct ConstellationStepsWelcomeGeo {
    let w: Double
    let h: Double
    let px: Double
    let py: Double
    let cx: Double
    let cy: Double
    let scale: Double

    init(w: Double, h: Double, top: Double, bottom: Double) {
        self.w = w
        self.h = h
        px = w / 2
        py = -h * 0.12
        cx = w / 2
        cy = (top + bottom) / 2
        scale = max(0.6, min(1, (bottom - top - 40) / 300, (w - 40) / 280))
    }
}

private func constellationStepsWelcomeRGBA(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
    Color(red: r / 255, green: g / 255, blue: b / 255).opacity(a)
}

enum ConstellationStepsWelcomeArt {
    typealias D = ConstellationStepsWelcomeData
    typealias G = ConstellationStepsWelcomeGeo

    /// Faint line art behind each constellation: the Swan, the Queen's throne and crown, the Hunter.
    static let outlines: [Path] = {
        func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }
        func circle(_ x: Double, _ y: Double, _ r: Double) -> Path { Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)) }

        var swan = Path()
        // Tail fan, body, neck, head and beak.
        swan.move(to: pt(0, -112)); swan.addLine(to: pt(-14, -134))
        swan.move(to: pt(0, -112)); swan.addLine(to: pt(0, -140))
        swan.move(to: pt(0, -112)); swan.addLine(to: pt(14, -134))
        swan.move(to: pt(-12, -100))
        swan.addCurve(to: pt(-5, 40), control1: pt(-20, -60), control2: pt(-18, -10))
        swan.addCurve(to: pt(6, 102), control1: pt(-2, 62), control2: pt(4, 88))
        swan.move(to: pt(12, -100))
        swan.addCurve(to: pt(11, 40), control1: pt(22, -62), control2: pt(20, -12))
        swan.addCurve(to: pt(18, 102), control1: pt(10, 62), control2: pt(14, 88))
        swan.addPath(circle(12, 110, 7))
        swan.move(to: pt(15, 117)); swan.addLine(to: pt(19, 132))
        // Wings: leading edge to the tip, trailing edge back, with a few feathers.
        swan.move(to: pt(-15, -32))
        swan.addCurve(to: pt(-132, -6), control1: pt(-52, -64), control2: pt(-102, -44))
        swan.addCurve(to: pt(-16, 2), control1: pt(-98, 12), control2: pt(-52, 10))
        swan.move(to: pt(16, -40))
        swan.addCurve(to: pt(126, -28), control1: pt(54, -74), control2: pt(98, -62))
        swan.addCurve(to: pt(18, -6), control1: pt(96, -8), control2: pt(54, -6))
        for i in 0..<4 {
            let fi = Double(i)
            let x = -34 - fi * 22
            swan.move(to: pt(x, 8 - fi * 1.5)); swan.addLine(to: pt(x - 8, 20 - fi * 3))
            let xr = 36 + fi * 22
            swan.move(to: pt(xr, -6 - fi * 3)); swan.addLine(to: pt(xr + 8, 6 - fi * 5))
        }

        var throne = Path()
        throne.move(to: pt(-62, 44)); throne.addLine(to: pt(-62, -52))
        throne.addCurve(to: pt(62, -52), control1: pt(-62, -86), control2: pt(62, -86))
        throne.addLine(to: pt(62, 44))
        throne.move(to: pt(-86, 44)); throne.addLine(to: pt(86, 44))
        throne.move(to: pt(-86, 54)); throne.addLine(to: pt(86, 54))
        throne.move(to: pt(-78, 54)); throne.addLine(to: pt(-82, 104))
        throne.move(to: pt(78, 54)); throne.addLine(to: pt(82, 104))
        throne.move(to: pt(-86, 44)); throne.addLine(to: pt(-86, 10)); throne.addLine(to: pt(-62, 10))
        throne.move(to: pt(86, 44)); throne.addLine(to: pt(86, 10)); throne.addLine(to: pt(62, 10))
        // The crown above.
        throne.move(to: pt(-30, -96))
        throne.addLine(to: pt(-30, -112))
        throne.addLine(to: pt(-16, -102))
        throne.addLine(to: pt(0, -122))
        throne.addLine(to: pt(16, -102))
        throne.addLine(to: pt(30, -112))
        throne.addLine(to: pt(30, -96))
        throne.closeSubpath()
        throne.addPath(circle(0, -128, 3))

        var hunter = Path()
        hunter.addPath(circle(0, -124, 11))
        hunter.move(to: pt(-62, -86))
        hunter.addCurve(to: pt(56, -78), control1: pt(-20, -96), control2: pt(20, -92))
        // Raised arm with a club.
        hunter.move(to: pt(-62, -86))
        hunter.addCurve(to: pt(-84, -150), control1: pt(-82, -104), control2: pt(-92, -126))
        hunter.addEllipse(in: CGRect(x: -96, y: -186, width: 22, height: 40))
        // Shield held out on the right.
        hunter.move(to: pt(72, -118))
        hunter.addCurve(to: pt(72, -38), control1: pt(104, -104), control2: pt(104, -52))
        hunter.move(to: pt(56, -78)); hunter.addLine(to: pt(72, -80))
        // Torso, tunic and legs.
        hunter.move(to: pt(-58, -80))
        hunter.addCurve(to: pt(-24, 2), control1: pt(-40, -40), control2: pt(-30, -10))
        hunter.move(to: pt(52, -72))
        hunter.addCurve(to: pt(24, -14), control1: pt(36, -40), control2: pt(28, -20))
        hunter.move(to: pt(-24, 2))
        hunter.addCurve(to: pt(-58, 54), control1: pt(-44, 30), control2: pt(-54, 44))
        hunter.addLine(to: pt(64, 50))
        hunter.addCurve(to: pt(24, -14), control1: pt(52, 30), control2: pt(36, 6))
        hunter.move(to: pt(-30, 54)); hunter.addLine(to: pt(-46, 98)); hunter.addLine(to: pt(-58, 112))
        hunter.move(to: pt(34, 52)); hunter.addLine(to: pt(62, 104)); hunter.addLine(to: pt(76, 114))
        hunter.move(to: pt(0, -4)); hunter.addLine(to: pt(5, 44))

        return [swan, throne, hunter]
    }()

    private static func drawConstellation(_ ctx: GraphicsContext, _ g: G, _ k: Int, _ p: ConstellationStepsWelcomePose) {
        let c = D.constellations[k]
        let d = p.drawn[k]
        let fade = p.fade
        let n = c.edges.count
        var lit = [Double](repeating: 0, count: c.stars.count)
        var cx = ctx
        // Rotate round the pole, then place at the hero centre and scale.
        cx.translateBy(x: g.px, y: g.py)
        cx.rotate(by: .radians(p.theta - Double(k) * D.stepAngle))
        cx.translateBy(x: -g.px, y: -g.py)
        cx.translateBy(x: g.cx, y: g.cy)
        cx.scaleBy(x: g.scale, y: g.scale)

        // Line art, fading in once the lines are mostly drawn.
        let art = 0.26 * constellationStepsWelcomeClamp01((d - 0.55) / 0.45) * fade
        if art > 0.005 {
            cx.stroke(outlines[k], with: .color(constellationStepsWelcomeRGBA(196, 214, 255, art)),
                      style: StrokeStyle(lineWidth: 1 / g.scale, lineCap: .round, lineJoin: .round))
        }

        // The lines, star to star: a soft glow under a bright core, each drawn on in turn.
        if d > 0.001 && fade > 0.005 {
            var path = Path()
            for i in 0..<n {
                let e = constellationStepsWelcomeClamp01(d * Double(n) - Double(i))
                if e <= 0 { continue }
                let a = c.stars[c.edges[i].0]
                let b = c.stars[c.edges[i].1]
                path.move(to: CGPoint(x: a.0, y: a.1))
                path.addLine(to: CGPoint(x: a.0 + (b.0 - a.0) * e, y: a.1 + (b.1 - a.1) * e))
                lit[c.edges[i].0] = max(lit[c.edges[i].0], constellationStepsWelcomeClamp01(e * 5))
                lit[c.edges[i].1] = max(lit[c.edges[i].1], constellationStepsWelcomeClamp01((e - 0.85) / 0.15))
            }
            var glow = cx
            glow.addFilter(.blur(radius: 3 / g.scale))
            glow.stroke(path, with: .color(constellationStepsWelcomeRGBA(255, 214, 140, 0.32 * fade)),
                        style: StrokeStyle(lineWidth: 4.5 / g.scale, lineCap: .round))
            cx.stroke(path, with: .color(constellationStepsWelcomeRGBA(255, 238, 200, 0.92 * fade)),
                      style: StrokeStyle(lineWidth: 1.2 / g.scale, lineCap: .round))
        }

        // Stars: always a little brighter than the field; lit ones bloom.
        let w = 2 * Double.pi / D.loop
        var add = cx
        add.blendMode = .plusLighter
        for s in c.stars.indices {
            let st = c.stars[s]
            let l = lit[s] * fade
            let tw = 0.85 + 0.15 * sin(w * Double(3 + s % 3) * p.time + Double(s) * 1.7 + Double(k))
            let r = st.2 * (1 + 0.35 * l)
            let hr = r * (3.5 + 3 * l)
            let center = CGPoint(x: st.0, y: st.1)
            add.fill(Path(ellipseIn: CGRect(x: st.0 - hr, y: st.1 - hr, width: 2 * hr, height: 2 * hr)), with: .radialGradient(
                Gradient(colors: [constellationStepsWelcomeRGBA(255, 236, 200, (0.34 + 0.34 * l) * tw), constellationStepsWelcomeRGBA(255, 220, 170, 0)]),
                center: center, startRadius: 0, endRadius: hr
            ))
            let cr = r * (0.72 - 0.1 * l)
            cx.fill(Path(ellipseIn: CGRect(x: st.0 - cr, y: st.1 - cr, width: 2 * cr, height: 2 * cr)),
                    with: .color(constellationStepsWelcomeRGBA(255, 250, 238, (0.82 + 0.18 * l) * tw)))
            if l > 0.02 && st.2 > 2.3 {
                // A short four-point sparkle on the bright ones.
                let len = r * 3.2
                var sp = Path()
                sp.move(to: CGPoint(x: st.0 - len, y: st.1)); sp.addLine(to: CGPoint(x: st.0 + len, y: st.1))
                sp.move(to: CGPoint(x: st.0, y: st.1 - len)); sp.addLine(to: CGPoint(x: st.0, y: st.1 + len))
                cx.stroke(sp, with: .color(constellationStepsWelcomeRGBA(255, 244, 220, 0.55 * l)),
                          style: StrokeStyle(lineWidth: 0.8 / g.scale, lineCap: .round))
            }
        }
        let a = lit[c.alpha] * fade
        if a > 0.01 {
            let st = c.stars[c.alpha]
            let label = Text(c.label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(constellationStepsWelcomeRGBA(242, 222, 180, 0.85 * a))
            // Skia's drawText places the baseline at y; the anchor puts the text's bottom there.
            cx.draw(label, at: CGPoint(x: st.0 + 10, y: st.1 + 4 + 2.5), anchor: .bottomLeading)
        }
    }

    /// Every constellation that can be on screen at this turn of the sky.
    static func draw(_ ctx: GraphicsContext, _ g: G, _ p: ConstellationStepsWelcomePose) {
        for k in D.constellations.indices where abs(p.theta - Double(k) * D.stepAngle) <= D.stepAngle * 1.2 {
            drawConstellation(ctx, g, k, p)
        }
    }
}
