import SwiftUI

struct GlassPayMorphFrame: View {
    let scene: GlassPayMorphMotion.Scene
    let geometry: Geometry
    private typealias M = GlassPayMorphMotion
    private typealias R = GlassPayMorphResources

    struct Geometry {
        var width: Double, height: Double, scale: Double, dy: Double, top: Double, px: Double
    }

    var body: some View {
        let s = scene, g = geometry, u = uniforms
        ZStack(alignment: .topLeading) {
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.glassPayMorph(.float2(g.scale, g.dy), .floatArray(u)))
                .frame(width: g.width, height: g.height, alignment: .topLeading)
            if s.face > 0.003 && abs(s.turn) < 90 {
                GlassPayMorphFace(scene: s)
                    .scaleEffect(g.scale, anchor: .topLeading)
                    .offset(y: g.dy)
            }
            if let bounds = frontBounds {
                Rectangle().fill(.white)
                    .colorEffect(ShaderLibrary.glassPayMorphFront(.float2(g.scale, g.dy), .floatArray(u)))
                    .frame(width: g.width, height: g.height, alignment: .topLeading)
                    .mask(alignment: .topLeading) {
                        Rectangle().frame(width: bounds.width, height: bounds.height)
                            .offset(x: bounds.minX, y: bounds.minY)
                    }
            }
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                var context = c
                context.translateBy(x: 0, y: g.dy)
                context.scaleBy(x: g.scale, y: g.scale)
                drawPulse(context)
                drawChrome(context)
            }
            .frame(width: g.width, height: g.height, alignment: .topLeading)
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
    }

    /// Match the RN overlay drawRect, including its 3 pt antialias margin.
    private var frontBounds: CGRect? {
        let s = scene, g = geometry
        var x0 = Double.infinity, y0 = Double.infinity, x1 = -Double.infinity, y1 = -Double.infinity
        for (trail, enabled) in [(s.a, s.frostA < 0.999), (s.rd, s.frostR < 0.999)] where enabled {
            for i in 0..<M.trail {
                let r = trail[i * 3 + 2] + 3
                x0 = min(x0, trail[i * 3] - r); y0 = min(y0, trail[i * 3 + 1] - r)
                x1 = max(x1, trail[i * 3] + r); y1 = max(y1, trail[i * 3 + 1] + r)
            }
        }
        guard x1 > x0 else { return nil }
        return CGRect(x: x0 * g.scale, y: y0 * g.scale + g.dy,
                      width: (x1 - x0) * g.scale, height: (y1 - y0) * g.scale)
    }

    private var uniforms: [Float] {
        let s = scene, bothFront = (1 - s.frostA) * (1 - s.frostR), tn = abs(s.turn)
        let edge = tn > 35 && tn < 145 ? sin((tn - 35) / 110 * .pi) : 0
        var u: [Double] = [geometry.px, 1]
        u += s.a; u += s.rd
        u += [s.frostA, s.frostR, s.dock, 18 * bothFront]
        u += M.cardInverse(s)
        u += [s.w / 2, s.h / 2, s.r, s.tint, s.flash, s.bright, s.sheen, s.glint]
        u += [s.crawl, edge, 0.35, 1, 0, 0]
        return u.map(Float.init)
    }

    private func drawPulse(_ context: GraphicsContext) {
        let s = scene
        guard s.pulse >= 0 && s.pulse <= 0.75 else { return }
        for i in 0..<3 {
            let u = (s.pulse - Double(i) * 0.09) / 0.5
            if u <= 0 || u >= 1 { continue }
            let e = M.easeOutCubic(u), radius = 100 + 52 * e
            var path = R.arc(s.cx, s.cy, radius, -22, 44)
            path.addPath(R.arc(s.cx, s.cy, radius, 158, 44))
            context.stroke(path, with: .color(.white.opacity(0.5 * (1 - e) * M.clamp01(u * 8))), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
        }
    }

    private func drawChrome(_ context: GraphicsContext) {
        let base = geometry.top + 22, mx = 30.0, my = base - 6
        context.fill(Path(ellipseIn: CGRect(x: mx + 1.4 - 6, y: my + 0.6 - 6, width: 12, height: 12)), with: .color(R.color(0xEEA635)))
        context.fill(Path(ellipseIn: CGRect(x: mx - 2.6 - 4.08, y: my - 1.9 - 4.08, width: 8.16, height: 8.16)), with: .color(R.color(0xE5284F, 0.92)))
        // RN's shared paint retains alpha 0.92 after the small red mark, then drawColor resets last4 to 0.5.
        R.draw(context, R.brand, 44, base, 0.92)
        R.draw(context, R.last4, 402 - 24 - R.last4.width, base, 0.5)
        let k = scene.caption, out = 6 * k, inn = 6 * (1 - k)
        for i in 0..<4 {
            let incoming = i >= 2, first = i % 2 == 0, alpha = incoming ? k : 1 - k
            let line = R.captions[i], y = (first ? 642.0 : 666.0) + (incoming ? inn : -out)
            R.draw(context, line, 201 - line.width / 2, y, alpha * (first ? 0.92 : 0.5))
        }
    }
}
