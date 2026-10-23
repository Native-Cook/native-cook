import SwiftUI
import UIKit

/// Value-only renderer for the source's horizon, coil and handset. The
/// simulation publishes the whole frame before this view is invalidated.
struct SlideToAnswerCordScene: View {
    let frame: SlideToAnswerCordSimulation.Frame
    let buttonsY: Double
    let showsControls: Bool
    let pressRed: Double
    let pressGreen: Double

    init(
        frame: SlideToAnswerCordSimulation.Frame,
        buttonsY: Double,
        showsControls: Bool = true,
        pressRed: Double = 1,
        pressGreen: Double = 1
    ) {
        self.frame = frame
        self.buttonsY = buttonsY
        self.showsControls = showsControls
        self.pressRed = pressRed
        self.pressGreen = pressGreen
    }

    private var scale: Double { frame.geometry.scale }
    private var size: CGSize { CGSize(width: frame.geometry.width, height: frame.geometry.height) }
    private var face: Color {
        SlideToAnswerCordColor.mixGamma(
            SlideToAnswerCordColor.mixGamma(SlideToAnswerCordTheme.Colors.ring, SlideToAnswerCordTheme.Colors.connected, amount: frame.connectedMix),
            SlideToAnswerCordTheme.Colors.off,
            amount: frame.offMix
        )
    }
    private var ground: Color {
        SlideToAnswerCordColor.mixGamma(
            SlideToAnswerCordColor.mixGamma(SlideToAnswerCordTheme.Colors.ground, SlideToAnswerCordTheme.Colors.groundConnected, amount: frame.connectedMix),
            SlideToAnswerCordTheme.Colors.groundEnded,
            amount: frame.offMix
        )
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            SlideToAnswerCordSky(ground: ground, size: size)

            if !SlideToAnswerCordTheme.replica {
                SlideToAnswerCordField(center: frame.pulse.center, color: face, size: size)
                    .opacity(SlideToAnswerCordTheme.Glass.fieldAlpha * (1 - frame.sourceWeight))
                if let wash = frame.wash, wash.progress > 0, wash.progress < 1 {
                    SlideToAnswerCordWash(center: wash.center, progress: wash.progress, color: face, size: size)
                }
                SlideToAnswerCordChevrons(frame: frame)
            }

            SlideToAnswerCordGrain(size: size)
                .opacity(SlideToAnswerCordTheme.Colors.grain)
                .blendMode(.screen)

            ZStack {
                Circle()
                    .stroke(face, lineWidth: SlideToAnswerCordMotion.Ring.stroke * scale)
                Circle()
                    .stroke(face, lineWidth: SlideToAnswerCordMotion.Ring.stroke * scale)
                    .blur(radius: SlideToAnswerCordMotion.Ring.blur)
            }
            .frame(width: frame.pulse.radius * 2, height: frame.pulse.radius * 2)
            .position(frame.pulse.center)
            .opacity(frame.pulse.opacity)

            SlideToAnswerCordCoil(chain: frame.chain, scale: scale, swingTime: frame.swingTime)
            SlideToAnswerCordHandset(frame: frame, size: size)

            if showsControls && !SlideToAnswerCordTheme.replica {
                SlideToAnswerCordButtonSurfaces(
                    frame: frame,
                    buttonsY: buttonsY,
                    size: size,
                    pressRed: pressRed,
                    pressGreen: pressGreen
                )
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }
}

private struct SlideToAnswerCordSky: View {
    let ground: Color
    let size: CGSize

    var body: some View {
        let sky = SlideToAnswerCordTheme.Colors.sky
        Rectangle()
            .fill(
                LinearGradient(
                    gradient: Gradient(stops: [
                        .init(color: sky[0], location: SlideToAnswerCordTheme.Colors.skyStops[0]),
                        .init(color: sky[1], location: SlideToAnswerCordTheme.Colors.skyStops[1]),
                        .init(color: sky[2], location: SlideToAnswerCordTheme.Colors.skyStops[2]),
                        .init(color: sky[3], location: SlideToAnswerCordTheme.Colors.skyStops[3]),
                        .init(color: ground, location: SlideToAnswerCordTheme.Colors.skyStops[4]),
                        .init(color: sky[5], location: SlideToAnswerCordTheme.Colors.skyStops[5])
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .frame(width: size.width, height: size.height)
    }
}

private struct SlideToAnswerCordField: View {
    let center: CGPoint
    let color: Color
    let size: CGSize

    var body: some View {
        let radius = SlideToAnswerCordTheme.Glass.field * size.width / SlideToAnswerCordTheme.stageWidth
        RadialGradient(colors: [color, .clear], center: .center, startRadius: 0, endRadius: radius)
            .frame(width: radius * 2, height: radius * 2)
            .position(center)
            .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

private struct SlideToAnswerCordWash: View {
    let center: CGPoint
    let progress: Double
    let color: Color
    let size: CGSize

    var body: some View {
        let radius = max(1, progress * hypot(size.width, size.height))
        RadialGradient(
            gradient: Gradient(stops: [
                .init(color: .clear, location: 0),
                .init(color: color, location: SlideToAnswerCordTheme.Glass.fieldWashTop),
                .init(color: .clear, location: 1)
            ]),
            center: .center,
            startRadius: 0,
            endRadius: radius
        )
        .frame(width: radius * 2, height: radius * 2)
        .position(center)
        .opacity(SlideToAnswerCordTheme.Glass.fieldWashAlpha * (1 - progress))
    }
}

private struct SlideToAnswerCordGrain: View {
    let size: CGSize

    var body: some View {
        Image(uiImage: SlideToAnswerCordAssets.grain)
            .interpolation(.none)
            .resizable()
            .frame(width: 500, height: 1_100, alignment: .topLeading)
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
    }
}

private struct SlideToAnswerCordChevrons: View {
    let frame: SlideToAnswerCordSimulation.Frame

    var body: some View {
        Canvas { context, _ in
            guard let phase = frame.chevronProgress else { return }
            let s = frame.geometry.scale
            for index in 0..<SlideToAnswerCordMotion.Chevron.count {
                let progress = (phase + Double(index) / Double(SlideToAnswerCordMotion.Chevron.count)).truncatingRemainder(dividingBy: 1)
                let point = frame.pulse.center
                let y = point.y - (SlideToAnswerCordTheme.Handset.comY + SlideToAnswerCordMotion.Chevron.handsetOffset + progress * SlideToAnswerCordMotion.Chevron.rise * 2) * s
                var path = Path()
                path.move(to: CGPoint(x: point.x - SlideToAnswerCordMotion.Chevron.halfWidth * s, y: y + SlideToAnswerCordMotion.Chevron.lowerY * s))
                path.addLine(to: CGPoint(x: point.x, y: y + SlideToAnswerCordMotion.Chevron.upperY * s))
                path.addLine(to: CGPoint(x: point.x + SlideToAnswerCordMotion.Chevron.halfWidth * s, y: y + SlideToAnswerCordMotion.Chevron.lowerY * s))
                context.stroke(path, with: .color(.white), style: StrokeStyle(lineWidth: SlideToAnswerCordMotion.Chevron.stroke * s, lineCap: .round, lineJoin: .round))
            }
        }
        .opacity(frame.chevronProgress == nil ? 0 : SlideToAnswerCordMotion.Chevron.alpha * (1 - frame.sourceWeight))
    }
}

private struct SlideToAnswerCordCoil: View {
    let chain: [CGPoint]
    let scale: Double
    let swingTime: Double

    var body: some View {
        Canvas { context, _ in
            let path = SlideToAnswerCordCoilPaths.make(chain: chain, scale: scale, swingTime: swingTime)
            var rightRim = context
            rightRim.translateBy(x: SlideToAnswerCordTheme.Cord.replicaRimDX * scale, y: 0)
            rightRim.stroke(
                path,
                with: .color(SlideToAnswerCordTheme.Colors.replicaRim),
                style: StrokeStyle(lineWidth: (SlideToAnswerCordTheme.Cord.replicaWidth - SlideToAnswerCordTheme.Cord.replicaRimWidthDelta) * scale, lineCap: .round)
            )
            var leftRim = context
            leftRim.translateBy(x: -SlideToAnswerCordTheme.Cord.replicaRimDX * SlideToAnswerCordTheme.Cord.replicaLeftRimDXMultiplier * scale, y: 0)
            leftRim.stroke(
                path,
                with: .color(SlideToAnswerCordTheme.Colors.replicaRimLeft),
                style: StrokeStyle(lineWidth: (SlideToAnswerCordTheme.Cord.replicaWidth - SlideToAnswerCordTheme.Cord.replicaRimWidthDelta) * scale, lineCap: .round)
            )
            context.stroke(
                path,
                with: .color(SlideToAnswerCordTheme.Colors.replicaCord),
                style: StrokeStyle(lineWidth: SlideToAnswerCordTheme.Cord.replicaWidth * scale, lineCap: .round)
            )
        }
    }
}

private enum SlideToAnswerCordCoilPaths {
    static func make(chain: [CGPoint], scale: Double, swingTime: Double) -> Path {
        guard chain.count >= SlideToAnswerCordTheme.Cord.links + 1 else { return Path() }
        let links = SlideToAnswerCordTheme.Cord.links
        let perLink = SlideToAnswerCordTheme.Cord.replicaTurns / Double(links)
        let steps = Int(ceil(perLink * 2 * Double(SlideToAnswerCordTheme.Cord.samples)))
        let roll = replicaRoll(at: swingTime)
        var path = Path()
        var hasPoint = false

        for index in 0..<links {
            let p0 = chain[max(0, index - 1)]
            let p1 = chain[index]
            let p2 = chain[index + 1]
            let p3 = chain[min(links, index + 2)]
            let radius = SlideToAnswerCordTheme.Cord.replicaWave * scale

            for step in 0..<steps {
                if index > 0 && step == 0 { continue }
                let u = Double(step) / Double(steps)
                let point = catmull(p0, p1, p2, p3, u: u)
                let tangent = catmullTangent(p0, p1, p2, p3, u: u)
                let rawTangentLength = hypot(tangent.x, tangent.y)
                let tangentLength = rawTangentLength == 0 ? 1 : rawTangentLength
                let phase = 2 * .pi * perLink * (Double(index) + u)
                    + SlideToAnswerCordTheme.Cord.replicaPhase + roll
                let offset = radius * sin(phase)
                let location = CGPoint(
                    x: point.x - tangent.y / tangentLength * offset,
                    y: point.y + tangent.x / tangentLength * offset
                )
                if hasPoint {
                    path.addLine(to: location)
                } else {
                    path.move(to: location)
                    hasPoint = true
                }
            }
        }
        return path
    }

    private static func replicaRoll(at time: Double) -> Double {
        let keys = SlideToAnswerCordTheme.Cord.replicaRoll
        for index in 0..<(keys.count - 1) where time >= keys[index].time && time <= keys[index + 1].time {
            let progress = (time - keys[index].time) / (keys[index + 1].time - keys[index].time)
            return keys[index].angle + (keys[index + 1].angle - keys[index].angle) * progress
        }
        return 0
    }

    private static func catmull(_ p0: CGPoint, _ p1: CGPoint, _ p2: CGPoint, _ p3: CGPoint, u: Double) -> CGPoint {
        let u2 = u * u
        let u3 = u2 * u
        return CGPoint(
            x: 0.5 * (2 * p1.x + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3),
            y: 0.5 * (2 * p1.y + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3)
        )
    }

    private static func catmullTangent(_ p0: CGPoint, _ p1: CGPoint, _ p2: CGPoint, _ p3: CGPoint, u: Double) -> CGPoint {
        CGPoint(
            x: 0.5 * ((-p0.x + p2.x) + 2 * (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u + 3 * (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u * u),
            y: 0.5 * ((-p0.y + p2.y) + 2 * (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u + 3 * (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u * u)
        )
    }
}

private struct SlideToAnswerCordHandset: View {
    let frame: SlideToAnswerCordSimulation.Frame
    let size: CGSize

    private var anchor: CGPoint { frame.chain.last ?? .zero }
    private var localScale: Double { frame.geometry.scale }
    private var pinkOpacity: Double {
        (1 - frame.connectedMix) * (1 - (1 - SlideToAnswerCordTheme.Handset.offOpacity) * frame.offMix)
    }
    private var greenOpacity: Double {
        frame.connectedMix * (1 - (1 - SlideToAnswerCordTheme.Handset.offOpacity) * frame.offMix)
    }
    private var sourceWeights: [Double] {
        let source = frame.sourceWeight
        return [
            source * swingWeight(take: 0),
            source * swingWeight(take: 1) + (1 - source),
            source * swingWeight(take: 2)
        ]
    }

    var body: some View {
        let weights = sourceWeights
        ZStack(alignment: .topLeading) {
            sourceSprite(SlideToAnswerCordAssets.sourceA).opacity(weights[0] * pinkOpacity)
            sourceSprite(SlideToAnswerCordAssets.sourceRest).opacity(weights[1] * pinkOpacity)
            sourceSprite(SlideToAnswerCordAssets.sourceB).opacity(weights[2] * pinkOpacity)
            sourceSprite(SlideToAnswerCordAssets.sourceAGreen).opacity(weights[0] * greenOpacity)
            sourceSprite(SlideToAnswerCordAssets.sourceRestGreen).opacity(weights[1] * greenOpacity)
            sourceSprite(SlideToAnswerCordAssets.sourceBGreen).opacity(weights[2] * greenOpacity)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .rotationEffect(.radians(frame.handsetAngle), anchor: UnitPoint(x: size.width > 0 ? anchor.x / size.width : 0.5, y: size.height > 0 ? anchor.y / size.height : 0.5))
    }

    private func sourceSprite(_ image: UIImage) -> some View {
        let box = SlideToAnswerCordMotion.ReplicaSwing.sourceBox
        let attach = SlideToAnswerCordMotion.ReplicaSwing.attach
        return Image(uiImage: image)
            .resizable()
            .interpolation(.high)
            .frame(width: box.width * localScale, height: box.height * localScale)
            .position(
                x: anchor.x + (box.midX - attach.x) * localScale,
                y: anchor.y + (box.midY - attach.y) * localScale
            )
            .blendMode(.plusLighter)
    }

    private func swingWeight(take: Int) -> Double {
        let keys = SlideToAnswerCordMotion.ReplicaSwing.keys
        for index in 0..<(keys.count - 1) {
            let first = keys[index]
            let second = keys[index + 1]
            if frame.swingTime >= first.time, frame.swingTime <= second.time {
                let progress = (frame.swingTime - first.time) / (second.time - first.time)
                return (first.take == take ? 1 - progress : 0) + (second.take == take ? progress : 0)
            }
        }
        return take == 0 ? 1 : 0
    }
}

struct SlideToAnswerCordButtonSurfaces: View {
    let frame: SlideToAnswerCordSimulation.Frame
    let buttonsY: Double
    let size: CGSize
    let pressRed: Double
    let pressGreen: Double

    private var scale: Double { frame.geometry.scale }
    private var half: Double { SlideToAnswerCordTheme.Layout.button * scale / 2 }
    private var left: Double { size.width / 2 - SlideToAnswerCordTheme.Layout.buttonGap * scale }
    private var right: Double { size.width / 2 + SlideToAnswerCordTheme.Layout.buttonGap * scale }
    private var acceptOpacity: Double { 1 - SlideToAnswerCordTheme.Glass.disabledAlpha * frame.offMix }

    var body: some View {
        let redX = left + (size.width / 2 - left) * frame.connectedMix
        let pillWidth = size.width - SlideToAnswerCordTheme.Layout.side * 2 * scale
        let pillCenter = CGPoint(
            x: right + (size.width / 2 - right) * frame.morph,
            y: buttonsY + SlideToAnswerCordTheme.Glass.pillY * scale * frame.morph
        )
        let pillHeight = half * 2 + (SlideToAnswerCordTheme.Glass.pillH * scale - half * 2) * frame.morph
        let pillActualWidth = half * 2 + (pillWidth - half * 2) * frame.morph

        ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                SlideToAnswerCordGlassShape(color: SlideToAnswerCordTheme.Glass.decline, width: half * 2, height: half * 2)
                    .position(x: redX, y: buttonsY)
                SlideToAnswerCordGlyph(up: false, scale: scale)
                    .position(x: redX, y: buttonsY)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .scaleEffect(pressRed, anchor: UnitPoint(x: redX / size.width, y: buttonsY / size.height))
            .opacity(acceptOpacity)

            ZStack(alignment: .topLeading) {
                SlideToAnswerCordGlassShape(
                    color: SlideToAnswerCordColor.mixGamma(SlideToAnswerCordTheme.Glass.accept, SlideToAnswerCordTheme.Glass.neutral, amount: frame.morph),
                    width: pillActualWidth,
                    height: pillHeight
                )
                .position(pillCenter)
                SlideToAnswerCordGlyph(up: true, scale: scale)
                    .position(x: right, y: buttonsY)
                    .opacity(1 - min(1, frame.morph * SlideToAnswerCordTheme.Glass.glyphFadeMultiplier))
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .scaleEffect(pressGreen, anchor: UnitPoint(x: right / size.width, y: buttonsY / size.height))
            .opacity(acceptOpacity)
        }
    }
}

/// The source draws these halos immediately before its two BackdropBlur calls.
/// They are rendered once for the visible scene and once in the clipped
/// backdrop copy above, preserving their contribution to the glass sample.
struct SlideToAnswerCordButtonHalos: View {
    let frame: SlideToAnswerCordSimulation.Frame
    let buttonsY: Double
    let size: CGSize
    let pressRed: Double
    let pressGreen: Double

    var body: some View {
        let scale = frame.geometry.scale
        let half = SlideToAnswerCordTheme.Layout.button * scale / 2
        let left = size.width / 2 - SlideToAnswerCordTheme.Layout.buttonGap * scale
        let right = size.width / 2 + SlideToAnswerCordTheme.Layout.buttonGap * scale
        let redX = left + (size.width / 2 - left) * frame.connectedMix
        let opacity = 1 - SlideToAnswerCordTheme.Glass.disabledAlpha * frame.offMix
        ZStack(alignment: .topLeading) {
            Circle().fill(SlideToAnswerCordTheme.Colors.decline)
                .frame(width: (half + SlideToAnswerCordTheme.Glass.haloExtra) * 2, height: (half + SlideToAnswerCordTheme.Glass.haloExtra) * 2)
                .blur(radius: SlideToAnswerCordTheme.Glass.haloBlur)
                .position(x: redX, y: buttonsY)
                .scaleEffect(pressRed)
                .opacity((SlideToAnswerCordTheme.Glass.haloBaseAlpha + SlideToAnswerCordTheme.Glass.haloGrowthAlpha * frame.declineGlow) * opacity)
            ZStack(alignment: .topLeading) {
                Circle().stroke(SlideToAnswerCordTheme.Colors.accept, lineWidth: SlideToAnswerCordTheme.Glass.rimWidth)
                    .frame(width: (half + SlideToAnswerCordTheme.Glass.haloExtra + SlideToAnswerCordTheme.Glass.haloGrowth * frame.acceptGlow) * 2, height: (half + SlideToAnswerCordTheme.Glass.haloExtra + SlideToAnswerCordTheme.Glass.haloGrowth * frame.acceptGlow) * 2)
                    .position(x: right, y: buttonsY)
                    .opacity(SlideToAnswerCordTheme.Glass.rimAlpha * (1 - frame.morph))
                Circle().fill(SlideToAnswerCordTheme.Colors.accept)
                    .frame(width: (half + SlideToAnswerCordTheme.Glass.haloExtra) * 2, height: (half + SlideToAnswerCordTheme.Glass.haloExtra) * 2)
                    .blur(radius: SlideToAnswerCordTheme.Glass.haloBlur)
                    .position(x: right, y: buttonsY)
                    .opacity((SlideToAnswerCordTheme.Glass.haloBaseAlpha + SlideToAnswerCordTheme.Glass.haloGrowthAlpha * frame.acceptGlow) * (1 - frame.morph))
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .scaleEffect(pressGreen, anchor: UnitPoint(x: right / size.width, y: buttonsY / size.height))
            .opacity(opacity)
        }
    }
}

/// The exact evolving circle/pill clipping geometry for the fixed-radius
/// backdrop copy. The tint and specular rim remain in `ButtonSurfaces`.
struct SlideToAnswerCordGlassBlurMask: View {
    let frame: SlideToAnswerCordSimulation.Frame
    let buttonsY: Double
    let size: CGSize
    let pressRed: Double
    let pressGreen: Double

    var body: some View {
        let scale = frame.geometry.scale
        let half = SlideToAnswerCordTheme.Layout.button * scale / 2
        let left = size.width / 2 - SlideToAnswerCordTheme.Layout.buttonGap * scale
        let right = size.width / 2 + SlideToAnswerCordTheme.Layout.buttonGap * scale
        let redX = left + (size.width / 2 - left) * frame.connectedMix
        let fullWidth = size.width - SlideToAnswerCordTheme.Layout.side * 2 * scale
        let pillCenter = CGPoint(
            x: right + (size.width / 2 - right) * frame.morph,
            y: buttonsY + SlideToAnswerCordTheme.Glass.pillY * scale * frame.morph
        )
        let pillWidth = half * 2 + (fullWidth - half * 2) * frame.morph
        let pillHeight = half * 2 + (SlideToAnswerCordTheme.Glass.pillH * scale - half * 2) * frame.morph
        let opacity = 1 - SlideToAnswerCordTheme.Glass.disabledAlpha * frame.offMix

        ZStack {
            Circle()
                .frame(width: half * 2, height: half * 2)
                .position(x: redX, y: buttonsY)
                .scaleEffect(pressRed)
                .opacity(opacity)
            RoundedRectangle(cornerRadius: pillHeight / 2)
                .frame(width: pillWidth, height: pillHeight)
                .position(pillCenter)
                .scaleEffect(pressGreen, anchor: UnitPoint(
                    x: pillWidth == 0 ? 0.5 : (right - (pillCenter.x - pillWidth / 2)) / pillWidth,
                    y: pillHeight == 0 ? 0.5 : (buttonsY - (pillCenter.y - pillHeight / 2)) / pillHeight
                ))
                .opacity(opacity)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

private struct SlideToAnswerCordGlassShape: View {
    let color: Color
    let width: Double
    let height: Double

    var body: some View {
        RoundedRectangle(cornerRadius: height / 2)
            .fill(color)
            .overlay {
                RoundedRectangle(cornerRadius: height / 2)
                    .stroke(LinearGradient(colors: [SlideToAnswerCordTheme.Glass.edgeTop, SlideToAnswerCordTheme.Glass.edgeBottom], startPoint: .top, endPoint: .bottom), lineWidth: SlideToAnswerCordTheme.Glass.edgeWidth)
            }
            .frame(width: width, height: height)
    }
}

private struct SlideToAnswerCordGlyph: View {
    let up: Bool
    let scale: Double

    var body: some View {
        SlideToAnswerCordGlyphShape()
            .fill(SlideToAnswerCordTheme.Colors.glyph)
            .frame(width: 24 * scale, height: 16 * scale)
            .rotationEffect(up ? .degrees(-135) : .zero)
    }
}

private struct SlideToAnswerCordGlyphShape: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 24
        let sy = rect.height / 16
        func point(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: rect.midX + x * sx, y: rect.midY + y * sy) }
        var path = Path()
        path.move(to: point(-12, 1))
        path.addCurve(to: point(0, -8), control1: point(-12, -6), control2: point(-6, -8))
        path.addCurve(to: point(12, 1), control1: point(6, -8), control2: point(12, -6))
        path.addLine(to: point(12, 4))
        path.addCurve(to: point(9.5, 5.6), control1: point(12, 5.5), control2: point(10.5, 6))
        path.addLine(to: point(5.5, 4.2))
        path.addCurve(to: point(4, 2), control1: point(4.6, 3.9), control2: point(4, 3))
        path.addLine(to: point(4, -1.5))
        path.addCurve(to: point(-4, -1.5), control1: point(1.5, -2.4), control2: point(-1.5, -2.4))
        path.addLine(to: point(-4, 2))
        path.addCurve(to: point(-5.5, 4.2), control1: point(-4, 3), control2: point(-4.6, 3.9))
        path.addLine(to: point(-9.5, 5.6))
        path.addCurve(to: point(-12, 4), control1: point(-10.5, 6), control2: point(-12, 5.5))
        path.closeSubpath()
        return path
    }
}

private enum SlideToAnswerCordColor {
    /// Reanimated's `interpolateColor` defaults to gamma 2.2. Preserve that
    /// rather than mixing encoded sRGB components directly.
    static func mixGamma(_ a: Color, _ b: Color, amount: Double) -> Color {
        let t = max(0, min(1, amount))
        let ca = UIColor(a)
        let cb = UIColor(b)
        var ar = CGFloat.zero, ag = CGFloat.zero, ab = CGFloat.zero, aa = CGFloat.zero
        var br = CGFloat.zero, bg = CGFloat.zero, bb = CGFloat.zero, ba = CGFloat.zero
        ca.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        cb.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
        func component(_ lhs: CGFloat, _ rhs: CGFloat) -> Double {
            pow(pow(Double(lhs), 2.2) + (pow(Double(rhs), 2.2) - pow(Double(lhs), 2.2)) * t, 1 / 2.2)
        }
        return Color(.sRGB, red: component(ar, br), green: component(ag, bg), blue: component(ab, bb), opacity: Double(aa) + (Double(ba) - Double(aa)) * t)
    }
}
