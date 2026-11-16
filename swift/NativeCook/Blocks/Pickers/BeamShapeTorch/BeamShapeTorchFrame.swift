import SwiftUI

struct BeamShapeTorchFrame: View {
    let look: BeamShapeTorchMotion.Look
    let layout: BeamShapeTorchLayout
    let displayScale: Double
    let reduced: Bool
    let sunGrab: Double
    let fade: Double
    typealias M = BeamShapeTorchMotion
    typealias A = BeamShapeTorchArt

    var body: some View {
        let k = look, l = layout, c = l.rect(k), shape = l.shape(k), show = M.smooth(0.4, 0.92, min(k.m, k.mw))
        ZStack(alignment: .topLeading) {
            wallpaper
                .colorEffect(ShaderLibrary.beamShapeTorchGrain(.float(displayScale), .floatArray(pageUniforms(show))))
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                var ambient = context
                ambient.translateBy(x: 0, y: 18 * l.scale * M.smooth(0.2, 1, k.m))
                ambient.addFilter(.blur(radius: 20 * l.scale))
                ambient.fill(shape, with: .color(A.color(0x1E0815, M.mix(0.42, 0.28, M.clamp01(k.b)))))
                var contact = context
                contact.translateBy(x: 0, y: 2 * l.scale); contact.addFilter(.blur(radius: 2 * l.scale))
                contact.fill(shape, with: .color(A.color(0x1E0815, 0.32)))
            }.opacity(fade)
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.beamShapeTorch(.floatArray(uniforms(c, show))))
                .mask(alignment: .topLeading) { shape.fill(.white) }.opacity(fade)
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                context.stroke(shape, with: .color(.white.opacity(0.06)), lineWidth: 1 / displayScale)
            }.opacity(fade)
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                var arc = context
                arc.clip(to: shape)
                transformContents(&arc)
                drawArcs(arc)
            }
            // Blend with the actual beam below, not with an isolated transparent Canvas layer.
            .blendMode(.screen).opacity(show * fade)
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                var content = context
                content.clip(to: shape)
                transformContents(&content)
                drawContents(content)
            }.opacity(show * fade)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
    }
    private func transformContents(_ context: inout GraphicsContext) {
        let l = layout, s = l.innerScale(look)
        context.translateBy(x: l.cx, y: l.top)
        context.scaleBy(x: s, y: s)
        context.translateBy(x: -l.cx, y: -l.top)
    }
    private func uniforms(_ c: CGRect, _ show: Double) -> [Float] {
        let k = look, l = layout, o = M.opticsOf(k.b, k.w), s = l.innerScale(k)
        let u: [Double] = [l.cx, l.top + (l.lensY - l.top) * s, l.scale * s,
                           M.clamp01(k.b), M.clamp01(k.hot), o.theta, o.reach, o.gain, M.clamp01(k.w),
                           c.minX, c.minY, c.width, c.height, M.mix(18.5, 56 * l.scale, min(k.m, k.mw)),
                           show, k.phase, reduced ? 1 : 0, 9.0 / 255, 8.0 / 255, 11.0 / 255,
                           253.0 / 255, 253.0 / 255, 251.0 / 255, 1, k.after, 205, 1]
        return u.map(Float.init)
    }
    private var wallpaper: some View {
        let l = layout, factor = max(l.width / 1206, l.height / 2622)
        let w = 1206 * factor, h = 2622 * factor
        return Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
            context.fill(Path(CGRect(x: 0, y: 0, width: l.width, height: l.height)),
                         with: .linearGradient(Gradient(stops: [.init(color: A.color(0x6E1F4E), location: 0), .init(color: A.color(0xB8538B), location: 0.45), .init(color: A.color(0x982168), location: 1)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: l.height)))
            if let image = A.wallpaper {
                context.draw(Image(uiImage: image), in: CGRect(x: (l.width - w) / 2, y: 0, width: w, height: h))
            }
        }
    }
    private func pageUniforms(_ show: Double) -> [Float] {
        let k = look, l = layout
        return [l.cx, l.top, l.scale, 0.12 * (1 - M.clamp01(k.hot)) * fade,
                0.1 * M.clamp01(k.b) * M.clamp01(k.w) * show * fade,
                0.22 * M.landing(M.opticsOf(k.b, k.w).reach, k.w, k.b) * show * fade].map(Float.init)
    }
    private func drawArcs(_ context: GraphicsContext) {
        let l = layout, k = look, o = M.opticsOf(k.b, k.w), s = l.scale
        let solid = A.arc(l.cx, l.lensY - o.reach * s, o.hw * s, o.sag * s)
        let dotted = A.arc(l.cx, l.lensY - o.reachMax * s, o.hwMax * s, o.sagMax * s)
        let arc = context
        arc.stroke(dotted, with: .color(A.color(0xB4B2BE, o.dots * 0.85)), style: StrokeStyle(lineWidth: 1.7 * s, lineCap: .round, dash: [0.01, 5.5 * s]))
        arc.stroke(solid, with: .color(A.color(0xB4B2BE, o.arcAlpha * 0.78)), style: StrokeStyle(lineWidth: 1.5 * s, lineCap: .round))
    }
    private func drawContents(_ context: GraphicsContext) {
        let l = layout, k = look, o = M.opticsOf(k.b, k.w), s = l.scale
        let g = M.arcGeometry(l.cx, l.lensY - o.reach * s, o.hw * s, o.sag * s)
        var sun = context
        sun.translateBy(x: g.ex + g.tx * 19 * s, y: g.ey + g.ty * 19 * s)
        sun.scaleBy(x: s * (1 + 0.25 * sunGrab), y: s * (1 + 0.25 * sunGrab))
        sun.stroke(A.sun, with: .color(A.color(0xB4B2BE, min(o.arcAlpha * 0.95 + 0.25 * sunGrab, 1))), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
        var glyph = context; glyph.translateBy(x: l.cap.minX + 105 * s, y: l.top + 294 * s); glyph.scaleBy(x: s, y: s)
        glyph.drawLayer { halo in
            halo.opacity = 0.6 * M.clamp01(k.hot)
            var body = halo; body.addFilter(.blur(radius: 10)); body.fill(A.glyphBody, with: .color(A.color(0xE9E5F6)))
            var cap = halo; cap.addFilter(.blur(radius: 8)); cap.fill(A.glyphCap, with: .color(A.color(0xFDFCFE)))
        }
        glyph.fill(A.glyphCap, with: .color(A.color(0x5D5A68))); glyph.fill(A.glyphBody, with: .color(A.color(0x5D5A68)))
        glyph.drawLayer { lit in
            lit.opacity = M.clamp01((o.glyph - 0.45) / 0.55)
            lit.fill(A.glyphCap, with: .color(A.color(0xFDFCFE)))
            lit.fill(A.glyphBody, with: .linearGradient(Gradient(colors: [A.color(0xFDFCFE), A.color(0xE9E5F6)]), startPoint: CGPoint(x: 0, y: 11), endPoint: CGPoint(x: 0, y: 79)))
        }
    }
}
