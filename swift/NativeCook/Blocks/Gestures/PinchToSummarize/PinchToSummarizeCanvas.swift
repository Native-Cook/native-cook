import SwiftUI
import UIKit

enum PinchToSummarizeAssets {
    static let field = load("PinchToSummarize-field.png")
    static let fieldB = load("PinchToSummarize-field-b.png")

    static func assertLoaded() {
        #if DEBUG
        assert(UIImage(named: "PinchToSummarize-field.png") != nil, "Missing PinchToSummarize-field.png")
        assert(UIImage(named: "PinchToSummarize-field-b.png") != nil, "Missing PinchToSummarize-field-b.png")
        assert(UIImage(named: "PinchToSummarize-hero.jpg") != nil, "Missing PinchToSummarize-hero.jpg")
        #endif
    }

    private static func load(_ name: String) -> UIImage {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing bundled asset: \(name)")
            return UIImage()
        }
        return image
    }
}

struct PinchToSummarizeFieldCanvas: View {
    let stage: PinchToSummarizeStage
    let snapshot: PinchToSummarizeSnapshot
    let reduceMotion: Bool

    var body: some View {
        Canvas { context, _ in
            drawField(context: &context)
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func drawField(context: inout GraphicsContext) {
        let layout = PinchToSummarizeTheme.Layout.self
        let clock = snapshot.clock
        let fieldScale = 1 + PinchToSummarizeConstants.Timing.fieldBreath.cgFloat
            * sin((clock / PinchToSummarizeConstants.Timing.fieldPeriod.cgFloat) * .pi * 2)
        let fieldRect = CGRect(x: 0, y: 0, width: stage.width, height: stage.height)
        var field = context
        field.translateBy(x: stage.width / 2, y: stage.height / 2)
        field.scaleBy(x: fieldScale, y: fieldScale)
        field.translateBy(x: -stage.width / 2, y: -stage.height / 2)
        field.draw(Image(uiImage: PinchToSummarizeAssets.field).interpolation(.high), in: fieldRect)
        field.opacity = snapshot.lift
        field.draw(Image(uiImage: PinchToSummarizeAssets.fieldB).interpolation(.high), in: fieldRect)

        if !reduceMotion, !PinchToSummarizeConstants.qaReplica {
            drawMotes(context: &context)
        }

        let frame = PinchToSummarizeGeometry.pageFrame(
            stage: stage,
            p: snapshot.p,
            lift: snapshot.lift,
            driftX: snapshot.driftX,
            driftY: snapshot.driftY
        )

        if !PinchToSummarizeConstants.qaReplica {
            let glowOpacity = PinchToSummarizeGeometry.clamp01((snapshot.p - 0.5) / 0.5)
                * (layout.glowRest + layout.glowThink * snapshot.think)
            if glowOpacity > 0.001 {
                let spread = layout.glowSpread
                let rect = CGRect(
                    x: frame.x - spread,
                    y: frame.y - spread * 0.5,
                    width: frame.width + spread * 2,
                    height: frame.height + spread * 1.6
                )
                var glow = context
                glow.opacity = glowOpacity
                glow.addFilter(.blur(radius: layout.glowBlur))
                glow.fill(
                    roundedRect(rect, radius: frame.radius + spread),
                    with: .color(PinchToSummarizeTheme.Colors.cardGlow)
                )
            }
        }

        let shadowOpacity = PinchToSummarizeGeometry.clamp01(snapshot.p) * layout.shadowAlpha
        if shadowOpacity > 0.001 {
            var shadow = context
            shadow.opacity = shadowOpacity
            shadow.translateBy(x: 0, y: PinchToSummarizeGeometry.clamp01(snapshot.p) * layout.shadowDrop)
            shadow.addFilter(.blur(radius: layout.shadowBlur))
            shadow.fill(
                roundedRect(
                    CGRect(x: frame.x, y: frame.y, width: frame.width, height: frame.height),
                    radius: frame.radius
                ),
                with: .color(PinchToSummarizeTheme.Colors.pageShadow)
            )
        }

        let ghostOpacity = PinchToSummarizeGeometry.clamp01((snapshot.p - 0.6) / 0.4)
        if ghostOpacity > 0.001 {
            var ghost = context
            ghost.opacity = ghostOpacity
            ghost.fill(
                roundedRect(
                    CGRect(
                        x: frame.x + layout.ghostInset,
                        y: frame.y - layout.ghostPeek,
                        width: frame.width - layout.ghostInset * 2,
                        height: frame.height
                    ),
                    radius: frame.radius
                ),
                with: .color(PinchToSummarizeTheme.Colors.ghost)
            )
        }
    }

    private func drawMotes(context: inout GraphicsContext) {
        let timing = PinchToSummarizeConstants.Timing.self
        let seconds = Double(snapshot.clock) / 1_000
        for index in 0..<Int(timing.motes) {
            let i = Double(index)
            let a = sin(i * 12.9898) * 43_758.5453
            let b = sin(i * 78.233) * 12_543.123
            let h1 = a - floor(a)
            let h2 = b - floor(b)
            let speed = timing.moteSpeedMin + (timing.moteSpeedMax - timing.moteSpeedMin) * h2
            let y = Double(stage.height) - ((h2 * Double(stage.height) + seconds * speed)
                .truncatingRemainder(dividingBy: Double(stage.height) + 20))
            let x = h1 * Double(stage.width) + sin(seconds * 0.6 + i) * 6
            let radius = 1 + 1.6 * h2
            context.fill(
                Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)),
                with: .color(PinchToSummarizeTheme.Colors.mote.opacity(0.08 + 0.14 * h1))
            )
        }
    }
}

struct PinchToSummarizeLightCanvas: View {
    let stage: PinchToSummarizeStage
    let snapshot: PinchToSummarizeSnapshot
    let reduceMotion: Bool

    var body: some View {
        Canvas { context, _ in
            drawLight(context: &context)
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func drawLight(context: inout GraphicsContext) {
        let layout = PinchToSummarizeTheme.Layout.self
        let timing = PinchToSummarizeConstants.Timing.self
        let breath = reduceMotion ? 0 : PinchToSummarizeGeometry.breathing(
            think: snapshot.think,
            clock: snapshot.clock,
            amplitude: timing.breathe.cgFloat,
            period: timing.breathePeriod.cgFloat
        )
        let frame = PinchToSummarizeGeometry.pageFrame(
            stage: stage,
            p: snapshot.p,
            lift: snapshot.lift,
            driftX: snapshot.driftX,
            driftY: snapshot.driftY,
            breath: breath
        )
        let inset = layout.rimWidth / 2
        let rimRect = CGRect(
            x: frame.x + inset,
            y: frame.y + inset,
            width: max(0, frame.width - inset * 2),
            height: max(0, frame.height - inset * 2)
        )
        let rimPath = roundedRect(rimRect, radius: max(0, frame.radius - inset))

        if !reduceMotion, !PinchToSummarizeConstants.qaReplica {
            drawScan(context: &context, frame: frame, rimPath: rimPath)
            drawRim(context: &context, frame: frame, rimPath: rimPath)
        }
        drawThinkingLabel(context: &context)
        if !reduceMotion {
            drawBloom(context: &context, frame: frame)
            drawFingers(context: &context)
        }
    }

    private func drawScan(
        context: inout GraphicsContext,
        frame: PinchToSummarizePageFrame,
        rimPath: Path
    ) {
        let opacity = snapshot.think * (1 - snapshot.lift) * PinchToSummarizeConstants.Rim.scan.cgFloat
        guard opacity > 0.001 else { return }
        let duration = PinchToSummarizeConstants.Timing.scanMs.cgFloat
        let u = snapshot.clock.truncatingRemainder(dividingBy: duration) / duration
        let height = PinchToSummarizeTheme.Layout.scanHeight
        let y = frame.y - height + u * (frame.height + height)
        var scan = context
        scan.opacity = opacity
        scan.clip(to: rimPath)
        scan.fill(
            Path(CGRect(x: 0, y: y, width: stage.width, height: height)),
            with: .linearGradient(
                Gradient(colors: [
                    .white.opacity(0),
                    Color(red: 200 / 255, green: 215 / 255, blue: 1).opacity(0.38),
                    .white.opacity(0)
                ]),
                startPoint: CGPoint(x: 0, y: y),
                endPoint: CGPoint(x: 0, y: y + height)
            )
        )
    }

    private func drawRim(
        context: inout GraphicsContext,
        frame: PinchToSummarizePageFrame,
        rimPath: Path
    ) {
        let opacity = PinchToSummarizeGeometry.clamp01(snapshot.rim)
        guard opacity > 0.001 else { return }
        let angle = Angle.radians(
            Double(snapshot.clock / PinchToSummarizeConstants.Timing.rimTurnMs.cgFloat) * .pi * 2
        )
        let shading = GraphicsContext.Shading.conicGradient(
            Gradient(colors: PinchToSummarizeTheme.Colors.rim),
            center: CGPoint(x: frame.x + frame.width / 2, y: frame.y + frame.height / 2),
            angle: angle
        )
        var glow = context
        glow.opacity = opacity * 0.75
        glow.addFilter(.blur(radius: PinchToSummarizeTheme.Layout.rimGlow))
        glow.stroke(rimPath, with: shading, lineWidth: PinchToSummarizeTheme.Layout.rimGlow)

        var edge = context
        edge.opacity = opacity
        edge.stroke(rimPath, with: shading, lineWidth: PinchToSummarizeTheme.Layout.rimWidth)
    }

    private func drawThinkingLabel(context: inout GraphicsContext) {
        let opacity = snapshot.think * (1 - snapshot.lift)
        guard opacity > 0.001 else { return }
        let font = Font.custom("Helvetica Neue", fixedSize: PinchToSummarizeTheme.Typography.thinking)
            .weight(.semibold)
        var resolved = context.resolve(
            Text(PinchToSummarizeTheme.Copy.thinking)
                .font(font)
                .foregroundStyle(.white)
        )
        let measured = resolved.measure(in: CGSize(width: stage.width, height: 40))
        let x = (stage.width - measured.width) / 2
        let y = PinchToSummarizeGeometry.cardTop(lift: 0)
            + PinchToSummarizeTheme.Layout.thinkHeight
            + PinchToSummarizeTheme.Layout.thinkingGap
        let shimmerDuration = PinchToSummarizeConstants.Timing.shimmerMs.cgFloat
        let shimmerX = x - 80
            + (snapshot.clock.truncatingRemainder(dividingBy: shimmerDuration) / shimmerDuration)
            * (measured.width + 160)
        resolved.shading = .linearGradient(
            Gradient(colors: [.white.opacity(0.55), .white, .white.opacity(0.55)]),
            startPoint: CGPoint(x: shimmerX, y: y),
            endPoint: CGPoint(x: shimmerX + 80, y: y)
        )
        var label = context
        label.opacity = opacity
        label.draw(
            resolved,
            at: CGPoint(x: x, y: y - resolved.firstBaseline(in: measured)),
            anchor: .topLeading
        )
    }

    private func drawBloom(context: inout GraphicsContext, frame: PinchToSummarizePageFrame) {
        guard snapshot.bloomAt >= 0 else { return }
        let u = PinchToSummarizeGeometry.clamp01(
            (snapshot.clock - snapshot.bloomAt) / PinchToSummarizeConstants.Timing.bloomMs.cgFloat
        )
        guard u < 1 else { return }
        let eased = 1 - pow(1 - u, 3)
        let grow = eased * PinchToSummarizeConstants.Timing.bloomReach.cgFloat
        let path = roundedRect(
            CGRect(
                x: frame.x - grow,
                y: frame.y - grow,
                width: frame.width + grow * 2,
                height: frame.height + grow * 2
            ),
            radius: frame.radius + grow
        )
        var bloom = context
        bloom.opacity = 1 - u
        bloom.addFilter(.blur(radius: PinchToSummarizeTheme.Layout.rimGlow))
        bloom.stroke(
            path,
            with: .color(PinchToSummarizeTheme.Colors.bloom),
            lineWidth: PinchToSummarizeTheme.Layout.rimWidth * 2
        )
    }

    private func drawFingers(context: inout GraphicsContext) {
        guard snapshot.fingerAlpha > 0.001 else { return }
        var fingers = context
        fingers.opacity = snapshot.fingerAlpha
        for center in [
            CGPoint(x: snapshot.fingerAX, y: snapshot.fingerAY),
            CGPoint(x: snapshot.fingerBX, y: snapshot.fingerBY)
        ] {
            let radius = PinchToSummarizeTheme.Layout.fingerRadius
            let path = Path(ellipseIn: CGRect(
                x: center.x - radius,
                y: center.y - radius,
                width: radius * 2,
                height: radius * 2
            ))
            fingers.fill(path, with: .color(PinchToSummarizeTheme.Colors.finger))
            fingers.stroke(path, with: .color(PinchToSummarizeTheme.Colors.fingerRing), lineWidth: 1.5)
        }
    }
}

private func roundedRect(_ rect: CGRect, radius: CGFloat) -> Path {
    Path(roundedRect: rect, cornerRadius: max(0, radius))
}

private extension Double {
    var cgFloat: CGFloat { CGFloat(self) }
}
