import SwiftUI

struct GlassPourMenuFrame: View {
    let frame: GlassPourMenuMotion.Frame
    let geometry: GlassPourMenuMotion.Geometry
    let items: [GlassPourMenuItem]
    let origin: CGPoint
    let direction: Double
    let size: CGSize
    var reduced = false
    var fade = 0.0
    var automatic = false
    var optics: [Double] = [1, 1, 1, 1]
    var tint: [Double] = [242.0 / 255, 118.0 / 255, 205.0 / 255]
    typealias M = GlassPourMenuMotion
    typealias R = GlassPourMenuResources

    var body: some View {
        ZStack(alignment: .topLeading) {
            if reduced {
                material(M.Frame(p: 0), endpoint: true)
                Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                    glyph(context, g: M.glass(M.Frame(p: 0), geometry, reduced: true), opacity: 1 - fade)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                ZStack(alignment: .topLeading) {
                    material(M.Frame(p: 1, lensY: frame.lensY, lensA: frame.lensA, flash: frame.flash), endpoint: true)
                    Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                        glyph(context, g: M.glass(M.Frame(p: 1), geometry, reduced: true), opacity: 1)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
                .compositingGroup()
                .opacity(fade)
                Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                    rows(context, alpha: Array(repeating: fade, count: items.count))
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            } else {
                material(frame, endpoint: false)
                Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                    let g = M.glass(frame, geometry, auto: automatic)
                    glyph(context, g: g, opacity: 1)
                    rows(context, alpha: g.rows)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
    private func material(_ f: M.Frame, endpoint: Bool) -> some View {
        let g = M.glass(f, geometry, reduced: endpoint, auto: automatic)
        let sat: [Double]
        if !automatic && !endpoint && f.satelliteAge >= 0 && f.satelliteAge < 0.22 {
            sat = [0, M.mix(g.neckB, g.boxY, M.clamp(f.satelliteProgress)), 5 * (1 - M.smooth(0.12, 0.22, f.satelliteAge))]
        } else { sat = [0, 0, 0] }
        var u: [Double] = [Double(origin.x), Double(origin.y), endpoint ? 0 : f.twist]
        u += [0, g.beadY, g.beadR, 0, g.boxY, g.boxW, g.boxH]
        u += [g.boxR, g.k, g.thick, g.hot, g.frost, g.open]
        u += [f.lensY, endpoint ? 18 : 18 + 2 * f.dwell, f.lensA, f.flash]
        u += [endpoint ? 0 : f.time * 1.5, g.rim, g.neckA, g.neckB, g.neckR]
        u += sat
        u += [endpoint ? 0 : f.dwell, direction]
        u += optics; u += tint
        return Rectangle().fill(.white)
            .colorEffect(ShaderLibrary.glassPourMenu(.floatArray(u.map(Float.init))))
            .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
    private func glyph(_ context: GraphicsContext, g: M.Glass, opacity: Double) {
        let k = g.glyphK, close = M.smooth(0, 0.55, k), turn = M.smooth(0.3, 1, k) + max(0, k - 1)
        for line in [-1, 0, 1] {
            var c = context
            c.opacity = opacity * (line == 0 ? 1 - M.smooth(0, 0.4, k) : 1)
            c.translateBy(x: origin.x, y: origin.y + direction * g.beadY + Double(line) * 6.9 * (1 - close))
            c.rotate(by: .radians(Double(line) * turn * .pi / 4))
            c.scaleBy(x: line == 0 ? max(0.001, 1 - close) : M.mix(1, 0.74, turn), y: 1)
            var p = Path(); p.move(to: CGPoint(x: -11.9, y: 0)); p.addLine(to: CGPoint(x: 11.9, y: 0))
            c.stroke(p, with: .color(R.color(0x3A0A30)), style: StrokeStyle(lineWidth: 2.25, lineCap: .round))
        }
    }
    private func rows(_ context: GraphicsContext, alpha: [Double]) {
        for (i, item) in items.enumerated() {
            let a = alpha[i]
            guard a > 0 else { continue }
            var c = context
            c.opacity = a * (item.disabled ? 0.35 : 1)
            c.translateBy(x: 0, y: reduced ? 0 : -4 * direction * (1 - a))
            let y = origin.y + direction * geometry.rows[i]
            let color = R.color(item.destructive ? 0x921E40 : 0x3A0F2C)
            let maxWidth = geometry.half * 2 - 20 - 27.5 - 17 - 12
            let label = R.label(item.label, size: geometry.fontSize, maxWidth: maxWidth)
            var text = c
            text.translateBy(x: origin.x - geometry.half + 20, y: y + geometry.fontSize * 0.727 / 2)
            text.fill(label.path, with: .color(color))
            if let icon = item.icon {
                var ink = c
                ink.translateBy(x: origin.x + geometry.half - 27.5 - 17 / 2, y: y - 17 / 2)
                ink.scaleBy(x: 17 / 24, y: 17 / 24)
                ink.stroke(R.icon(icon), with: .color(color),
                           style: StrokeStyle(lineWidth: 1.4 * 24 / 17, lineCap: .round, lineJoin: .round))
            }
            if item.destructive && i > 0 {
                var p = Path()
                p.move(to: CGPoint(x: origin.x - geometry.half + 15, y: y - direction * geometry.pitch / 2))
                p.addLine(to: CGPoint(x: origin.x + geometry.half - 15, y: y - direction * geometry.pitch / 2))
                var rule = c; rule.opacity *= 0.35
                rule.stroke(p, with: .color(.white), lineWidth: 0.5)
            }
        }
    }
}
