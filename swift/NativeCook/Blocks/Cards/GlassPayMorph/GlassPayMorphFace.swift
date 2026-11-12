import SwiftUI

/// The printed face remains in the RN card-local coordinate system before the common homography.
struct GlassPayMorphFace: View {
    let scene: GlassPayMorphMotion.Scene
    private typealias M = GlassPayMorphMotion
    private typealias R = GlassPayMorphResources

    var body: some View {
        Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
            let s = scene
            let stroke = StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round)
            if s.contactless * s.face > 0.003 {
                for radius in [4.2, 7.7, 11.2] {
                    let sweep = 2 * 0.86 * 180 / Double.pi
                    context.stroke(R.arc(29, 38, radius, 180 - sweep / 2, sweep), with: .color(.white.opacity(0.92 * s.contactless * s.face)), style: stroke)
                }
            }
            if s.textChip * s.face > 0.003 {
                let ink = GraphicsContext.Shading.color(.white.opacity(0.92 * s.textChip * s.face))
                context.stroke(R.chip, with: ink, style: stroke)
                if s.chipLines > 0.001 {
                    for line in R.chipLines { context.stroke(R.trimmed(line, s.chipLines), with: ink, style: stroke) }
                }
                if s.rings > 0.001 {
                    context.stroke(R.arc(229, 38.5, 15.5, -60, 359.9 * s.rings), with: ink, style: stroke)
                    context.stroke(R.arc(212, 32.9, 10.6, 120, 359.9 * s.rings), with: ink, style: stroke)
                }
            }
            drawNumber(context)
            R.draw(context, R.name, 37.5, 135, 0.92 * s.textName * s.face)
        }
        .frame(width: 282, height: 168, alignment: .topLeading)
        .projectionEffect(projection)
    }

    private var projection: ProjectionTransform {
        let s = scene, m = M.cardMatrix(s), sx = s.w / 282, sy = s.h / 168
        var p = ProjectionTransform()
        p.m11 = m[0] * sx; p.m21 = m[1] * sy; p.m31 = m[2]
        p.m12 = m[3] * sx; p.m22 = m[4] * sy; p.m32 = m[5]
        p.m13 = m[6] * sx; p.m23 = m[7] * sy; p.m33 = m[8]
        return p
    }

    private func drawNumber(_ context: GraphicsContext) {
        let s = scene, t = s.t
        let condensing = t >= 1.04 && t < M.expand0
        let rolling = t >= 3.31 && t < M.expand1
        var lens = CGPoint.zero
        if condensing {
            let m = M.cardInverse(s), z = m[6] * 184 + m[7] * 408 + m[8]
            lens.x = ((m[0] * 184 + m[1] * 408 + m[2]) / z + s.w / 2) * 282 / s.w
            lens.y = ((m[3] * 184 + m[4] * 408 + m[5]) / z + s.h / 2) * 168 / s.h
        }
        for i in R.number.indices {
            let line = R.number[i], gx = R.numberX[i], cx = gx + line.width / 2, cy = 110 - 18.4 * 0.36
            if condensing {
                let d0 = 1.04 + Double(i) * 0.025 + (i == 3 ? 0.05 : 0)
                let k = M.easeInOutCubic(M.seg(t, d0, d0 + 0.13))
                let run = M.easeInOutCubic(M.seg(t, d0 + 0.13 * 0.75, d0 + 0.13 * 0.75 + 0.12))
                if k < 1 {
                    var c = context
                    c.translateBy(x: cx, y: cy); c.scaleBy(x: 1 - 0.92 * k, y: 1 - 0.55 * k); c.translateBy(x: -cx, y: -cy)
                    c.drawLayer { layer in
                        if k > 0.01 { layer.addFilter(.blur(radius: 2 * k)) }
                        R.draw(layer, line, gx, 110, 0.92 * s.face * (1 - 0.5 * k))
                    }
                }
                if k > 0.45 && run < 1 {
                    let f = M.clamp01((k - 0.45) / 0.3), a = f * f * (3 - 2 * f) * (1 - run * run * run)
                    let x = cx + (lens.x - cx) * run, y = cy + (lens.y - cy) * run, radius = 1.8 * (1 - 0.4 * run)
                    context.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: 2 * radius, height: 2 * radius)), with: .color(.white.opacity(0.92 * s.face * a)))
                }
            } else if rolling {
                var c = context
                c.clip(to: Path(CGRect(x: gx - 2, y: 110 - 18.4 * 0.95, width: line.width + 4, height: 18.4 * 1.2)))
                for j in line.glyphs.indices {
                    let d0 = 3.31 + Double(i * 4 + j) * 0.008, e = M.easeOutCubic(M.seg(t, d0, d0 + 0.13))
                    let p = line.glyphs[j].applying(CGAffineTransform(translationX: gx + line.positions[j], y: 110 + (1 - e) * 18.4 * 1.05))
                    c.fill(p, with: .color(.white.opacity(0.92 * s.face)))
                }
            } else {
                R.draw(context, line, gx, 110, 0.92 * s.textNumber * s.face)
            }
        }
    }
}
