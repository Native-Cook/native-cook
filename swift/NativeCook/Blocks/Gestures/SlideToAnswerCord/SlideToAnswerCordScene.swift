import SwiftUI

struct SlideToAnswerCordBackdrop: View {
    var body: some View {
        LinearGradient(
            stops: [
                .init(color: SlideToAnswerCordTheme.Colors.pageTop, location: 0),
                .init(color: SlideToAnswerCordTheme.Colors.page, location: 0.5),
                .init(color: SlideToAnswerCordTheme.Colors.pageBottom, location: 1)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .drawingGroup(opaque: true, colorMode: .nonLinear)
    }
}

struct SlideToAnswerCordScene: View {
    let frame: SlideToAnswerCordSimulation.Frame

    var body: some View {
        let geometry = frame.geometry
        let scale = geometry.scale
        let mix = frame.connectedMix

        ZStack(alignment: .topLeading) {
            RadialGradient(
                colors: [
                    SlideToAnswerCordTheme.Colors.rgba(255, 214 + 20 * mix, 170 + 30 * mix, 0.20 - 0.06 * mix),
                    SlideToAnswerCordTheme.Colors.lightEdge
                ],
                center: .center,
                startRadius: 0,
                endRadius: 300 * scale
            )
            .frame(width: 600 * scale, height: 600 * scale)
            .position(x: frame.handsetCenter.x - 40 * scale, y: frame.handsetCenter.y - 80 * scale)

            Canvas(rendersAsynchronously: true) { context, _ in
                guard let anchor = frame.chain.last else { return }
                SlideToAnswerCordArtwork.drawShadow(in: &context, anchor: CGPoint(x: anchor.x + 10 * scale, y: anchor.y + 22 * scale), angle: frame.handsetAngle, scale: scale * 1.03)

                let coil = SlideToAnswerCordArtwork.coil(points: frame.chain, scale: scale)
                context.stroke(coil.back, with: .color(SlideToAnswerCordTheme.Colors.cordBack), style: .init(lineWidth: SlideToAnswerCordTheme.Cord.tube * scale, lineCap: .round, lineJoin: .round))
                context.stroke(coil.front, with: .color(SlideToAnswerCordTheme.Colors.cordEdge), style: .init(lineWidth: (SlideToAnswerCordTheme.Cord.tube + 0.8) * scale, lineCap: .round, lineJoin: .round))
                context.stroke(coil.front, with: .color(SlideToAnswerCordTheme.Colors.cordFront), style: .init(lineWidth: SlideToAnswerCordTheme.Cord.tube * scale, lineCap: .round, lineJoin: .round))
                context.translateBy(x: -0.9 * scale, y: -0.9 * scale)
                context.stroke(coil.front, with: .color(SlideToAnswerCordTheme.Colors.cordHi), style: .init(lineWidth: 1.1 * scale, lineCap: .round))
                context.translateBy(x: 0.9 * scale, y: 0.9 * scale)

                SlideToAnswerCordArtwork.drawReceiver(in: &context, anchor: anchor, angle: frame.handsetAngle, scale: scale)
            }
        }
        .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(false)
    }
}

private enum SlideToAnswerCordArtwork {
    private static let outline: Path = {
        let h = SlideToAnswerCordTheme.Handset.self
        let r = h.cupRadius
        let a = 40.0 * Double.pi / 180
        let ex = r * cos(a)
        let ey = r * sin(a)
        let mid = (h.topCupY + h.bottomCupY) / 2
        var p = Path()
        p.move(to: CGPoint(x: ex, y: h.topCupY + ey))
        p.addCurve(to: CGPoint(x: h.waist, y: mid), control1: CGPoint(x: h.waist + 1, y: h.topCupY + ey + 14), control2: CGPoint(x: h.waist, y: mid - 22))
        p.addCurve(to: CGPoint(x: ex, y: h.bottomCupY - ey), control1: CGPoint(x: h.waist, y: mid + 22), control2: CGPoint(x: h.waist + 1, y: h.bottomCupY - ey - 14))
        p.addArc(center: CGPoint(x: 0, y: h.bottomCupY), radius: r, startAngle: .degrees(-40), endAngle: .degrees(220), clockwise: false)
        p.addCurve(to: CGPoint(x: -h.waist, y: mid), control1: CGPoint(x: -h.waist - 1, y: h.bottomCupY - ey - 14), control2: CGPoint(x: -h.waist, y: mid + 22))
        p.addCurve(to: CGPoint(x: -ex, y: h.topCupY + ey), control1: CGPoint(x: -h.waist, y: mid - 22), control2: CGPoint(x: -h.waist - 1, y: h.topCupY + ey + 14))
        p.addArc(center: CGPoint(x: 0, y: h.topCupY), radius: r, startAngle: .degrees(140), endAngle: .degrees(400), clockwise: false)
        p.closeSubpath()
        return p
    }()

    private static let gloss: Path = {
        let h = SlideToAnswerCordTheme.Handset.self
        let mid = (h.topCupY + h.bottomCupY) / 2
        var p = Path()
        p.move(to: CGPoint(x: -h.waist + 4, y: h.topCupY + 34))
        p.addCurve(to: CGPoint(x: -h.waist + 4, y: h.bottomCupY - 34), control1: CGPoint(x: -h.waist + 2.5, y: mid - 20), control2: CGPoint(x: -h.waist + 2.5, y: mid + 20))
        p.addCurve(to: CGPoint(x: -h.waist + 4, y: h.topCupY + 34), control1: CGPoint(x: -h.waist + 6.5, y: mid + 20), control2: CGPoint(x: -h.waist + 6.5, y: mid - 20))
        p.closeSubpath()
        return p
    }()

    private static let holes: Path = {
        let h = SlideToAnswerCordTheme.Handset.self
        var p = Path()
        func ring(_ cy: Double, _ radius: Double, _ count: Int, _ dot: Double, _ rotation: Double = 0) {
            for index in 0..<count {
                let a = rotation + 2 * Double.pi * Double(index) / Double(count)
                p.addEllipse(in: CGRect(x: cos(a) * radius - dot, y: cy + sin(a) * radius - dot, width: dot * 2, height: dot * 2))
            }
        }
        p.addEllipse(in: CGRect(x: -1.6, y: h.topCupY - 1.6, width: 3.2, height: 3.2))
        ring(h.topCupY, 6, 8, 1.3)
        ring(h.topCupY, 11.5, 14, 1.3, 0.2)
        p.addEllipse(in: CGRect(x: -2.2, y: h.bottomCupY - 2.2, width: 4.4, height: 4.4))
        ring(h.bottomCupY, 8, 6, 1.9, 0.5)
        return p
    }()

    static func coil(points: [CGPoint], scale: Double) -> (back: Path, front: Path) {
        var back = Path()
        var front = Path()
        let n = SlideToAnswerCordTheme.Cord.links
        guard points.count >= n + 1, scale > 0 else { return (back, front) }
        let perLink = Double(SlideToAnswerCordTheme.Cord.turns) / Double(n)
        let steps = Int(ceil(perLink * 2 * Double(SlideToAnswerCordTheme.Cord.samples)))
        var lastSide = 0
        for i in 0..<n {
            let p0 = points[max(0, i - 1)]
            let p1 = points[i]
            let p2 = points[i + 1]
            let p3 = points[min(n, i + 2)]
            let length = hypot(p2.x - p1.x, p2.y - p1.y) / scale
            let pitch = length / perLink
            let base = pow(SlideToAnswerCordTheme.Cord.wire / (2 * .pi), 2) - pow(pitch / (2 * .pi), 2)
            let radius = min(SlideToAnswerCordTheme.Cord.maxRadius, sqrt(max(base, 1.2))) * scale
            for j in 0..<steps {
                if i > 0, j == 0 { continue }
                let u = Double(j) / Double(steps)
                let u2 = u * u
                let u3 = u2 * u
                let cx = 0.5 * (2 * p1.x + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3)
                let cy = 0.5 * (2 * p1.y + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3)
                let tx = 0.5 * ((-p0.x + p2.x) + 2 * (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u + 3 * (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u2)
                let ty = 0.5 * ((-p0.y + p2.y) + 2 * (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u + 3 * (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u2)
                let tangent = max(hypot(tx, ty), 0.0001)
                let phase = 2 * Double.pi * perLink * (Double(i) + u)
                let offset = radius * sin(phase)
                let dip = radius * 0.32 * cos(phase)
                let point = CGPoint(x: cx - ty / tangent * offset + tx / tangent * dip, y: cy + tx / tangent * offset + ty / tangent * dip)
                let side = cos(phase) >= 0 ? 1 : -1
                if side > 0 {
                    side == lastSide ? front.addLine(to: point) : front.move(to: point)
                } else {
                    side == lastSide ? back.addLine(to: point) : back.move(to: point)
                }
                lastSide = side
            }
        }
        return (back, front)
    }

    static func drawShadow(in context: inout GraphicsContext, anchor: CGPoint, angle: Double, scale: Double) {
        let path = outline.applying(transform(anchor: anchor, angle: angle, scale: scale))
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 14 * scale / 1.03))
            layer.fill(path, with: .color(SlideToAnswerCordTheme.Colors.shadow))
        }
    }

    static func drawReceiver(in context: inout GraphicsContext, anchor: CGPoint, angle: Double, scale: Double) {
        let h = SlideToAnswerCordTheme.Handset.self
        let t = transform(anchor: anchor, angle: angle, scale: scale)
        let receiver = outline.applying(t)
        context.fill(receiver, with: .linearGradient(
            Gradient(stops: [
                .init(color: SlideToAnswerCordTheme.Colors.ivoryHi, location: 0),
                .init(color: SlideToAnswerCordTheme.Colors.ivory, location: 0.3),
                .init(color: SlideToAnswerCordTheme.Colors.ivoryMid, location: 0.7),
                .init(color: SlideToAnswerCordTheme.Colors.ivoryLo, location: 1)
            ]),
            startPoint: CGPoint(x: -h.cupRadius, y: 0).applying(t),
            endPoint: CGPoint(x: h.cupRadius, y: h.bottomCupY + h.cupRadius).applying(t)
        ))
        context.drawLayer { layer in
            layer.clip(to: receiver)
            layer.addFilter(.blur(radius: 3 * scale))
            layer.stroke(
                receiver,
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: .clear, location: 0),
                        .init(color: .clear, location: 0.55),
                        .init(color: SlideToAnswerCordTheme.Colors.rgba(110, 88, 56, 0.35), location: 1)
                    ]),
                    startPoint: CGPoint(x: -h.cupRadius, y: 0).applying(t),
                    endPoint: CGPoint(x: h.cupRadius, y: 0).applying(t)
                ),
                lineWidth: 6 * scale
            )
        }
        context.stroke(receiver, with: .color(SlideToAnswerCordTheme.Colors.ivoryEdge), lineWidth: scale)

        for cy in [h.topCupY, h.bottomCupY] {
            let cup = Path(ellipseIn: CGRect(x: -h.inner, y: cy - h.inner, width: h.inner * 2, height: h.inner * 2)).applying(t)
            let gradientStart = CGPoint(x: -h.inner, y: cy - h.inner).applying(t)
            let gradientEnd = CGPoint(x: h.inner, y: cy + h.inner).applying(t)
            context.fill(cup, with: .linearGradient(
                Gradient(stops: [
                    .init(color: SlideToAnswerCordTheme.Colors.cupLo, location: 0),
                    .init(color: SlideToAnswerCordTheme.Colors.ivory, location: 0.55),
                    .init(color: SlideToAnswerCordTheme.Colors.cupHi, location: 1)
                ]), startPoint: gradientStart, endPoint: gradientEnd
            ))
            context.stroke(cup, with: .color(SlideToAnswerCordTheme.Colors.ivoryEdge), lineWidth: 1.2 * scale)
            let rr = h.inner + 1.4
            let rim = Path(ellipseIn: CGRect(x: -rr, y: cy - rr, width: rr * 2, height: rr * 2)).applying(t)
            context.stroke(rim, with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.9), location: 0),
                    .init(color: .white.opacity(0), location: 0.5),
                    .init(color: .white.opacity(0.25), location: 1)
                ]), startPoint: gradientStart, endPoint: gradientEnd
            ), lineWidth: 1.4 * scale)
        }

        context.fill(holes.applying(t), with: .color(SlideToAnswerCordTheme.Colors.hole))
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 2.2 * scale))
            layer.fill(gloss.applying(t), with: .color(SlideToAnswerCordTheme.Colors.specular))
        }
        var crescents = Path()
        for cy in [h.topCupY, h.bottomCupY] {
            crescents.addArc(center: CGPoint(x: 0, y: cy), radius: h.cupRadius - 3.5, startAngle: .degrees(200), endAngle: .degrees(258), clockwise: false)
        }
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.6 * scale))
            layer.stroke(crescents.applying(t), with: .color(SlideToAnswerCordTheme.Colors.specular), style: .init(lineWidth: 3 * scale, lineCap: .round))
        }
    }

    private static func transform(anchor: CGPoint, angle: Double, scale: Double) -> CGAffineTransform {
        CGAffineTransform(translationX: anchor.x, y: anchor.y).rotated(by: angle).scaledBy(x: scale, y: scale)
    }
}
