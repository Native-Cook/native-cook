import SwiftUI

struct LacedBootStudioArt: View {
    let scene: LacedBootStudioScene
    private let resources = LacedBootStudioResources.shared

    var body: some View {
        Canvas { context, _ in
            context.draw(Image(decorative: resources.paper, scale: 1), in: CGRect(x: 0, y: 0, width: 393, height: 852))
            for left in [true, false] {
                var boot = context
                boot.translateBy(x: left ? 123.5 : 272.5, y: 308)
                boot.rotate(by: .degrees(left ? -4 : 4))
                if left { boot.scaleBy(x: -1, y: 1) }
                boot.translateBy(x: -58.5, y: -160)
                boot.draw(Image(decorative: resources.shadow, scale: 1), in: LacedBootStudioResources.shadowBounds)
                blend(resources.soles, weights: scene.soles, rect: LacedBootStudioResources.soleBounds, context: &boot)
                blend(left ? resources.leftBoots : resources.bodies, weights: scene.materials,
                      rect: CGRect(x: 0, y: 0, width: 117, height: 320), context: &boot)
                if !left {
                    lace(context: &boot)
                    blend(resources.eyelets, weights: scene.materials,
                          rect: CGRect(x: 0, y: 0, width: 117, height: 190), context: &boot)
                    aglet(context: &boot)
                }
            }
            for (index, x) in [187.5, 196.5, 205.5].enumerated() {
                context.fill(Path(ellipseIn: CGRect(x: x - 1.8, y: 491.2, width: 3.6, height: 3.6)),
                             with: .color(LacedBootStudioResources.color(index == 0 ? 0x50473E : 0xC8C0B6)))
            }
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .allowsHitTesting(false).accessibilityHidden(true)
    }
    private func blend(_ images: [CGImage], weights: [Double], rect: CGRect, context: inout GraphicsContext) {
        context.drawLayer { layer in
            layer.blendMode = .plusLighter
            for i in images.indices where weights[i] > 0 {
                layer.opacity = weights[i]
                layer.draw(Image(decorative: images[i], scale: 1), in: rect)
            }
        }
    }
    private func path(_ curve: LacedBootStudioCurve) -> Path {
        var result = Path(); result.move(to: curve.a)
        result.addCurve(to: curve.b, control1: curve.c1, control2: curve.c2)
        return result
    }
    private func lace(context: inout GraphicsContext) {
        for index in 0..<11 {
            if index > 0 && scene.trims[index] <= 0 { continue }
            var spanContext = context
            spanContext.opacity = scene.spanAlpha[index]
            let curve = index == 0 ? LacedBootStudioGeometry.tail(scene) : LacedBootStudioGeometry.curve(index, slack: scene.slacks[index])
            let table = LacedBootStudioGeometry.arcs[index]
            let trim = index == 0 ? 1 : scene.trims[index]
            let start = index == 0 ? 0.035 : table.parameter(min(trim, 1.6 / table.length))
            let end = index == 0 ? 1 : table.parameter(max(0, trim - 1.6 / table.length))

            for (point, reached) in [(curve.a, true), (curve.b, trim >= 0.97)] where reached {
                spanContext.drawLayer { under in
                    under.clip(to: Path(ellipseIn: CGRect(x: point.x - 2.4, y: point.y - 2.4, width: 4.8, height: 4.8)))
                    under.stroke(path(curve), with: .color(LacedBootStudioResources.color(0x443126)),
                                 style: StrokeStyle(lineWidth: 2.8, lineCap: .round))
                }
            }
            guard end > start else { continue }
            let front = path(curve.segment(start, end))
            var shadow = spanContext; shadow.translateBy(x: 0, y: 1)
            shadow.stroke(front, with: .color(.black.opacity(0.17)), style: StrokeStyle(lineWidth: 4.8, lineCap: .round))
            if index > 0 {
                var near = spanContext; near.translateBy(x: 0, y: 0.8)
                near.stroke(front, with: .color(.black.opacity(0.14)), style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
            }
            spanContext.stroke(front, with: .color(LacedBootStudioResources.color(0x564135)),
                           style: StrokeStyle(lineWidth: 2.8, lineCap: .round))
            var ridge = spanContext; ridge.translateBy(x: -0.35, y: -0.45)
            ridge.stroke(front, with: .color(LacedBootStudioResources.color(0xA78763)),
                         style: StrokeStyle(lineWidth: 0.65, lineCap: .round))
        }
    }
    private func aglet(context: inout GraphicsContext) {
        context.opacity = scene.tipAlpha
        context.translateBy(x: scene.tip.x, y: scene.tip.y)
        context.rotate(by: .radians(atan2(scene.tangent.y, scene.tangent.x)))
        let rect = CGRect(x: -7, y: -1.4, width: 7, height: 2.8)
        let shape = Path(roundedRect: rect, cornerRadius: 0.6)
        context.fill(shape, with: .linearGradient(Gradient(colors: [LacedBootStudioResources.color(0xE4CCA0),
            LacedBootStudioResources.color(0xAD9065), LacedBootStudioResources.color(0x806447)]),
            startPoint: CGPoint(x: -4, y: -1.4), endPoint: CGPoint(x: -2, y: 1.4)))
        context.stroke(shape, with: .color(LacedBootStudioResources.color(0x715539)), lineWidth: 0.3)
    }
}
