import SwiftUI

/// Scene, chrome and capsule preserve RN's paint order. All masks use screen-space geometry.
struct PrintLineScrubFrame: View {
    let look: PrintLineScrubMotion.Look
    let geometry: PrintLineScrubGeometry
    let pixelRatio: Double
    let reduceMotion: Bool
    typealias M = PrintLineScrubMotion
    typealias R = PrintLineScrubResources

    var body: some View {
        let g = geometry
        ZStack(alignment: .topLeading) {
            stage
            strip(g.headerRect)
            strip(g.dockRect)
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in PrintLineScrubArt.chrome(c, g) }
            capsule
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
        .clipped()
    }

    private var stage: some View {
        ZStack(alignment: .topLeading) {
            scene(glass: false)
            gizmo
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                let g = geometry, s = g.scale, k = look
                let y = g.capsuleY(k.h, k.duck) + g.capHeight / 2 - 14 * s + 18 * s * (1 + 0.1 * k.press)
                var c = context; c.addFilter(.blur(radius: 12 * s))
                let rect = CGRect(x: g.capLeft + 10 * s, y: y, width: g.capWidth - 20 * s, height: g.capHeight * 0.5)
                c.fill(Path(roundedRect: rect, cornerRadius: g.capRadius * 0.5), with: .color(R.color(0x3A2420, 0.08)))
            }
        }
        .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
    }
    private var gizmo: some View {
        Canvas(opaque: false, colorMode: .nonLinear) { c, _ in PrintLineScrubArt.gizmo(c, look, geometry) }
            .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
    }

    private func scene(glass: Bool) -> some View {
        Rectangle().fill(.white)
            .colorEffect(ShaderLibrary.printLineScrubScene(.floatArray(sceneUniforms(glass: glass)), .image(R.atlas)))
            .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
    }

    private func sceneUniforms(glass: Bool) -> [Float] {
        let g = geometry, k = look, figure = g.figureRect, cy = g.capsuleY(k.h, k.duck)
        let row = (g.planeY(k.h) - figure.minY) / figure.height
        let spans = PrintLineScrubFigure.silhouetteAt(row)
        let scale = 1 + 0.02 * k.press
        var values: [Double] = [g.axisX, g.baseY, g.unit, PrintLineScrubGeometry.camCos, PrintLineScrubGeometry.camSin]
        values += [figure.minX, figure.minY, figure.width, figure.height, 1024, 1536]
        values += spans
        values += [max(-4 * .pi / 180, min(4 * .pi / 180, k.yaw)), k.h, k.band * g.scale, reduceMotion ? 0 : k.heat, k.bead,
                   M.clamp01(M.percentAt(k.h) / 100), pixelRatio, 1, glass ? 1 : 0]
        if glass {
            values += [g.capLeft + g.capWidth / 2, cy, g.capWidth / 2 * scale, g.capHeight / 2 * scale,
                       g.capRadius * scale, 1.05, 9 * g.scale]
        } else { values += [0, 0, 1, 1, 1, 1, 0] }
        return values.map(Float.init)
    }

    private func strip(_ rect: CGRect) -> some View {
        let s = geometry.scale, path = Path(roundedRect: rect, cornerRadius: 28 * s)
        return ZStack(alignment: .topLeading) {
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                var shadow = c; shadow.addFilter(.blur(radius: 10 * s))
                shadow.translateBy(x: 0, y: 6 * s)
                shadow.fill(path, with: .color(R.color(0x3A2420, 0.10)))
            }
            // Re-render the actual backdrop in place; do not use a system material with a different tint.
            ZStack(alignment: .topLeading) {
                stage
                Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                    var shadow = c; shadow.addFilter(.blur(radius: 10 * s))
                    shadow.translateBy(x: 0, y: 6 * s)
                    shadow.fill(path, with: .color(R.color(0x3A2420, 0.10)))
                }
            }
            .compositingGroup()
            .blur(radius: 10 * s)
            .mask { Canvas { c, _ in c.fill(path, with: .color(.white)) } }
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                c.fill(path, with: .color(.white.opacity(0.34)))
                let gradient = Gradient(colors: [.white.opacity(0.95), .white.opacity(0.4), .white.opacity(0.25)])
                c.stroke(path, with: .linearGradient(gradient, startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.maxY)), lineWidth: s)
            }
        }
        .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
    }

    private var capsule: some View {
        let g = geometry, k = look, s = g.scale
        let cy = g.capsuleY(k.h, k.duck), rect = g.capsuleRect(k), scale = 1 + 0.02 * k.press
        let path = Path(roundedRect: rect, cornerRadius: g.capRadius * scale)
        let top = cy - g.capHeight / 2, plane = g.planeY(k.h)
        let tintStart = CGPoint(x: 0, y: top), tintEnd = CGPoint(x: 0, y: top + g.capHeight)
        return ZStack(alignment: .topLeading) {
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                var ambient = c; ambient.addFilter(.blur(radius: 12 * s)); ambient.translateBy(x: 0, y: (12 + 8 * k.press) * s)
                ambient.fill(path, with: .color(R.color(0x3A2420, 0.13)))
                var contact = c; contact.addFilter(.blur(radius: 2 * s)); contact.translateBy(x: 0, y: 2 * s)
                contact.fill(path, with: .color(R.color(0x3A2420, 0.16)))
            }
            ZStack(alignment: .topLeading) {
                ZStack(alignment: .topLeading) { scene(glass: true); gizmo }
                    .compositingGroup().blur(radius: 2.5 * s)
                Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                    let gradient = Gradient(colors: [.white.opacity(0.40), .white.opacity(0.22)])
                    context.fill(path, with: .linearGradient(gradient, startPoint: tintStart, endPoint: tintEnd))
                    var inner = context; inner.addFilter(.blur(radius: 2 * s))
                    inner.stroke(path, with: .color(R.color(0x3A2420, 0.10)), lineWidth: 4 * s)
                }
            }
            .mask { Canvas { c, _ in c.fill(path, with: .color(.white)) } }
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                c.stroke(path, with: .linearGradient(Gradient(colors: [.white.opacity(0.95), .white.opacity(0.38), .white.opacity(0.2)]), startPoint: tintStart, endPoint: tintEnd), lineWidth: s)
                let proximity = M.clamp01(1 - abs(plane - top) / (70 * s))
                let heat = M.clamp01(0.12 + 0.4 * proximity + 0.35 * k.heat)
                let a = floor(0.9 * heat * 1000 + 0.5) / 1000
                let gradient = Gradient(colors: [R.color(0xFF6A3A, 0), R.color(0xFF9646, a), R.color(0xFF6A3A, 0)])
                c.stroke(path, with: .linearGradient(gradient, startPoint: CGPoint(x: 0, y: plane - 70 * s), endPoint: CGPoint(x: 0, y: plane + 70 * s)), lineWidth: 1.6 * s)
            }
            content
                .scaleEffect(scale, anchor: UnitPoint(x: (g.capLeft + g.capWidth / 2) / g.width, y: cy / g.height))
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
    }

    private var content: some View {
        let g = geometry, k = look, s = g.scale
        let cy = g.capsuleY(k.h, k.duck), top = cy - g.capHeight / 2
        let barRect = CGRect(x: g.capLeft + 20 * s - 6, y: top, width: 300 * s + 12, height: g.capHeight)
        let bar: [Double] = [g.capLeft + 20 * s, top + 46 * s, s, 300, 40, g.pillX(k.h) / s, k.over * s, k.tail, k.flash, 1]
        let pillX = g.capLeft + 20 * s + g.pillX(k.h), pillY = top + 46 * s + 40 * s / 2
        let percentage = min(100, max(0, Int(floor(M.percentAt(k.h) + 0.5))))
        let text = R.percentages[percentage]
        return ZStack(alignment: .topLeading) {
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                R.draw(c, R.title, x: g.capLeft + 28 * s, baseline: top + 33 * s, scale: s, color: R.color(0x111111), opacity: 1 - k.held)
                R.draw(c, R.layers[M.layerAt(k.h)], x: g.capLeft + 28 * s, baseline: top + 33 * s, scale: s, color: R.color(0x111111), opacity: k.held)
            }
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.printLineScrubBar(.float2(barRect.origin), .floatArray(bar.map(Float.init))))
                .frame(width: barRect.width, height: barRect.height, alignment: .topLeading)
                .offset(x: barRect.minX, y: barRect.minY)
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                var shadow = c; shadow.addFilter(.blur(radius: 4 * s))
                PrintLineScrubArt.circle(shadow, x: pillX, y: pillY + 2 * s, radius: 22 * s, color: R.color(0x3A2420, 0.16))
                PrintLineScrubArt.circle(c, x: pillX, y: pillY, radius: 22 * s, color: R.color(0xFBFAF8))
                R.draw(c, text, x: pillX - text.width * s / 2, baseline: pillY + 0.35 * 15 * s, scale: s, color: R.color(0x111111))
            }
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
    }
}
