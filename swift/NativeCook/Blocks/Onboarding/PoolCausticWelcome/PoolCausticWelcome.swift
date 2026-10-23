import SwiftUI
import UIKit

/// A poolside welcome: caustic light refracts the title, tiles and palm shadow under the water.
struct PoolCausticWelcome: View {
    static let loop: Double = 8
    var autoPlay = false
    /// Freezes an exact loop phase for screenshots and port comparison.
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date()
    @State private var lastTouch = Date.distantPast
    @State private var touchBegan = Date.distantPast
    @State private var releasedAt = Date.distantPast
    @State private var dragStart = PoolCausticWelcomePose.rest
    @State private var heldPose = PoolCausticWelcomePose.rest
    @State private var releasedPose = PoolCausticWelcomePose.rest
    @State private var ringStart = CGPoint(x: 127, y: 738)
    @State private var ringVelocity = CGVector.zero
    @State private var dragging = false
    @State private var draggingRing = false
    @State private var manualLive = false
    @State private var crest = false
    @State private var slowDayStarted = false
    @State private var lastHaptic = Date.distantPast

    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 393, geometry.size.height / 852)
            ZStack {
                PoolCausticWelcomeStyle.surround.ignoresSafeArea()
                canonicalScreen
                    .frame(width: 393, height: 852, alignment: .topLeading)
                    .scaleEffect(scale)
                    .frame(width: geometry.size.width, height: geometry.size.height)
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .task(id: lastTouch) {
            guard manualLive else { return }
            try? await Task.sleep(for: .seconds(2.65))
            guard !Task.isCancelled, !dragging else { return }
            manualLive = false
        }
    }

    private var canonicalScreen: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 31).fill(PoolCausticWelcomeStyle.color(0x4D9DE1))
                .frame(width: 357, height: 711).position(x: 196.5, y: 438.5)
                .shadow(color: .black.opacity(0.08), radius: 2, y: 1)
                .shadow(color: .black.opacity(0.10), radius: 13, y: 8)
            TimelineView(.animation(minimumInterval: nil, paused: timelinePaused)) { timeline in
                let pose = currentPose(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    water(pose)
                    PoolCausticWelcomeFloatingObjects(pose: pose)
                        .frame(width: 393, height: 852)
                    labels
                    interactionLayer(pose)
                }
                .frame(width: 393, height: 852, alignment: .topLeading)
            }
            RoundedRectangle(cornerRadius: 31).stroke(PoolCausticWelcomeStyle.color(0xFDFDF6), lineWidth: 2)
                .frame(width: 357, height: 711).position(x: 196.5, y: 438.5)
                .allowsHitTesting(false)

        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .coordinateSpace(name: "PoolCausticWelcomeStage")
        .clipped()
    }

    private var timelinePaused: Bool {
        reduceMotion || previewTime != nil || (!autoPlay && !manualLive)
    }

    private func water(_ pose: PoolCausticWelcomePose) -> some View {
        Image(uiImage: PoolCausticWelcomeArtwork.base)
            .resizable().interpolation(.high)
            .frame(width: 357, height: 711)
            .layerEffect(
                ShaderLibrary.poolCausticWelcomeWater(
                    .float2(357, 711), .float(Float(pose.phase)), .float(Float(pose.amplitude)),
                    .float2(Float(pose.pointer.x - 18), Float(pose.pointer.y - 83)),
                    .float(Float(pose.pointerStrength)),
                    .float2(Float(pose.velocity.dx), Float(pose.velocity.dy))
                ),
                maxSampleOffset: CGSize(width: 28, height: 28), isEnabled: !reduceMotion
            )
            .clipShape(RoundedRectangle(cornerRadius: 31))
            .overlay {
                RoundedRectangle(cornerRadius: 31)
                    .stroke(LinearGradient(colors: [.white.opacity(0.24), .clear,
                                                     PoolCausticWelcomeStyle.color(0x226BAE).opacity(0.20)],
                                           startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 2)
            }
            .position(x: 196.5, y: 438.5)
            .accessibilityHidden(true)
    }

    private var labels: some View {
        Text("A slower kind\nof day.")
            .font(.system(size: 16, design: .serif).italic()).lineSpacing(-1)
            .multilineTextAlignment(.center)
            .foregroundStyle(PoolCausticWelcomeStyle.color(0xDCECE8).opacity(0.88))
            .frame(width: 180, height: 44).position(x: 196.5, y: 507)
            .allowsHitTesting(false).accessibilityHidden(true)
    }

    private func interactionLayer(_ pose: PoolCausticWelcomePose) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                Spacer().frame(height: 83)
                HStack(spacing: 0) {
                    Spacer().frame(width: 18)
                    Color.clear.frame(width: 357, height: 711).contentShape(RoundedRectangle(cornerRadius: 31))
                        .gesture(waterDrag)
                        .accessibilityElement()
                        .accessibilityLabel("Afloat pool water")
                        .accessibilityHint("Drag to bend the light through the water")
                    Spacer().frame(width: 18)
                }
                Spacer(minLength: 0)
            }
            .frame(width: 393, height: 852, alignment: .topLeading)
            VStack(spacing: 0) {
                Spacer().frame(height: 728.5)
                HStack(spacing: 0) {
                    Spacer().frame(width: 85)
                    Button(action: startSlowDay) {
                        HStack(spacing: 8) {
                            Text(slowDayStarted && reduceMotion ? "Your slow day begins" : "Start a slow day")
                                .font(.system(size: 11, weight: .medium))
                            Spacer(minLength: 4)
                            Image(systemName: "arrow.right").font(.system(size: 11, weight: .medium))
                        }
                        .foregroundStyle(Color.white.opacity(0.95)).padding(.leading, 68).padding(.trailing, 16)
                        .frame(width: 222, height: 35)
                        .background(Color.white.opacity(0.13), in: Capsule())
                        .overlay(Capsule().stroke(Color.white.opacity(0.45), lineWidth: 1))
                        .frame(width: 222, height: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(slowDayStarted && reduceMotion ? "Your slow day begins" : "Start a slow day")
                    Spacer().frame(width: 86)
                }
                Spacer(minLength: 0)
            }
            .frame(width: 393, height: 852, alignment: .topLeading)
            ringInteraction(pose)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func ringInteraction(_ pose: PoolCausticWelcomePose) -> some View {
        let center = pose.ringCenter
        return VStack(spacing: 0) {
            Spacer().frame(height: max(0, center.y - 36))
            HStack(spacing: 0) {
                Spacer().frame(width: max(0, center.x - 36))
                Color.clear.frame(width: 72, height: 72).contentShape(Circle())
                    .gesture(waterDrag)
                    .accessibilityElement()
                    .accessibilityLabel("Floating life ring")
                    .accessibilityHint("Drag the ring across the pool")
                Spacer(minLength: 0)
            }
            Spacer(minLength: 0)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private var waterDrag: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("PoolCausticWelcomeStage"))
            .onChanged { value in
                let now = Date()
                if !dragging {
                    dragStart = currentPose(at: now)
                    heldPose = dragStart
                    ringStart = dragStart.ringCenter
                    draggingRing = hypot(value.startLocation.x - ringStart.x, value.startLocation.y - ringStart.y) < 40
                    touchBegan = now
                    dragging = true
                    crest = false
                    if draggingRing { haptic() }
                }
                let distance = hypot(value.translation.width, value.translation.height)
                let activation = Self.ease(distance / 12)
                let phase = dragStart.phase + value.translation.width * 0.013 + value.translation.height * 0.007
                heldPose.phase = phase
                heldPose.amplitude = dragStart.amplitude
                heldPose.pointer = CGPoint(x: min(367, max(26, value.location.x)), y: min(786, max(91, value.location.y)))
                heldPose.pointerStrength = activation
                heldPose.velocity = CGVector(dx: min(1, max(-1, value.velocity.width / 800)),
                                             dy: min(1, max(-1, value.velocity.height / 800)))
                if draggingRing {
                    let center = CGPoint(x: min(339, max(54, ringStart.x + value.translation.width)),
                                         y: min(756, max(132, ringStart.y + value.translation.height)))
                    let bob = heldPose.ringBob
                    heldPose.ringOffset = CGVector(dx: center.x - 127 - bob.dx, dy: center.y - 738 - bob.dy)
                    heldPose.extraRingAngle = min(14, max(-14, value.translation.width * 0.09))
                }
                lastTouch = now
                manualLive = true
            }
            .onEnded { value in
                let now = Date()
                releasedPose = currentPose(at: now)
                ringVelocity = draggingRing && !reduceMotion
                    ? CGVector(dx: min(1400, max(-1400, value.velocity.width)), dy: min(1400, max(-1400, value.velocity.height)))
                    : .zero
                releasedAt = now
                lastTouch = now
                dragging = false
                manualLive = true
                if draggingRing { haptic() }
                draggingRing = false
            }
    }

    private func currentPose(at now: Date) -> PoolCausticWelcomePose {
        if let previewTime { return reduceMotion ? .rest : Self.loopPose(previewTime) }
        if reduceMotion {
            var pose = PoolCausticWelcomePose.rest
            pose.ringOffset = dragging ? heldPose.ringOffset : releasedPose.ringOffset
            return pose
        }
        if dragging {
            var pose = heldPose
            let caughtCenter = pose.ringCenter
            let activation = Self.ease(now.timeIntervalSince(touchBegan) / 0.25)
            pose.amplitude += (max(0.85, pose.amplitude) - pose.amplitude) * activation
            pose.pointerStrength = max(pose.pointerStrength, activation * 0.75)
            if draggingRing {
                pose.ringOffset.dx += caughtCenter.x - pose.ringCenter.x
                pose.ringOffset.dy += caughtCenter.y - pose.ringCenter.y
            }
            return pose
        }
        let age = now.timeIntervalSince(lastTouch)
        if age < 2.6 {
            let releaseAge = max(0, now.timeIntervalSince(releasedAt))
            var pose = releasedPose
            pose.pointerStrength *= exp(-releaseAge * 1.1)
            pose.velocity = CGVector(dx: pose.velocity.dx * exp(-releaseAge * 4), dy: pose.velocity.dy * exp(-releaseAge * 4))
            let spring = releaseAge >= 0.8 ? 0 : exp(-8 * releaseAge) * (cos(14 * releaseAge) + (8 / 14.0) * sin(14 * releaseAge))
            pose.ringOffset = CGVector(dx: releasedPose.ringOffset.dx * spring + ringVelocity.dx * releaseAge * exp(-8 * releaseAge),
                                      dy: releasedPose.ringOffset.dy * spring + ringVelocity.dy * releaseAge * exp(-8 * releaseAge))
            pose.extraRingAngle *= spring
            pose.clampRing()
            if crest {
                pose.amplitude += (1 - pose.amplitude) * Self.ease(releaseAge / 0.55)
                pose.phase += .pi * Self.ease(releaseAge / 1.2)
            }
            return pose.returningToRest(Self.ease((age - 2) / 0.6))
        }
        guard autoPlay else { return .rest }
        let time = lastTouch == .distantPast ? now.timeIntervalSince(epoch) : age - 2.6
        return Self.loopPose(time)
    }

    private func startSlowDay() {
        let now = Date()
        releasedPose = currentPose(at: now)
        releasedPose.pointerStrength = 0
        releasedAt = now
        lastTouch = now
        ringVelocity = .zero
        manualLive = true
        crest = true
        withAnimation(.easeInOut(duration: 0.15)) { slowDayStarted = true }
        haptic()
    }

    private func haptic() {
        let now = Date()
        guard now.timeIntervalSince(lastHaptic) > 0.16 else { return }
        lastHaptic = now
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.45)
    }

    private static func loopPose(_ rawTime: Double) -> PoolCausticWelcomePose {
        let t = (rawTime.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        if t < 1.4 || t >= 7.4 { return .rest }
        let amplitude: Double
        if t < 2.8 { amplitude = 0.65 * ease((t - 1.4) / 1.4) }
        else if t < 3.7 { amplitude = 0.65 + 0.35 * ease((t - 2.8) / 0.9) }
        else if t < 4.9 { amplitude = 1 }
        else if t < 6.5 { amplitude = 1 - 0.65 * ease((t - 4.9) / 1.6) }
        else { amplitude = 0.35 * (1 - ease((t - 6.5) / 0.9)) }
        var pose = PoolCausticWelcomePose.rest
        pose.amplitude = amplitude
        pose.phase = 2 * .pi * ease((t - 1.4) / 6)
        return pose
    }

    private static func ease(_ raw: Double) -> Double {
        let u = min(1, max(0, raw))
        return u * u * (3 - 2 * u)
    }
}

private struct PoolCausticWelcomePose {
    var phase = 0.0
    var amplitude = 0.0
    var pointer = CGPoint(x: 196.5, y: 420)
    var pointerStrength = 0.0
    var velocity = CGVector.zero
    var ringOffset = CGVector.zero
    var extraRingAngle = 0.0
    static let rest = PoolCausticWelcomePose()

    var ringBob: CGVector { CGVector(dx: 4 * amplitude * sin(phase), dy: 3 * amplitude * sin(2 * phase)) }
    var ringCenter: CGPoint { CGPoint(x: 127 + ringBob.dx + ringOffset.dx, y: 738 + ringBob.dy + ringOffset.dy) }
    var ringAngle: Double { 8 * amplitude * sin(phase) + extraRingAngle }
    var duckCenter: CGPoint {
        CGPoint(x: 224 + 3 * amplitude * (cos(phase) - 1), y: 353 + 2 * amplitude * sin(phase))
    }
    var duckAngle: Double { -6 * amplitude * sin(phase) }

    mutating func clampRing() {
        let center = ringCenter
        let clamped = CGPoint(x: min(339, max(54, center.x)), y: min(756, max(132, center.y)))
        ringOffset.dx += clamped.x - center.x
        ringOffset.dy += clamped.y - center.y
    }

    func returningToRest(_ amount: Double) -> Self {
        guard amount > 0 else { return self }
        var pose = self
        pose.amplitude *= 1 - amount
        let shortPhase = atan2(sin(phase), cos(phase))
        pose.phase = phase - shortPhase * amount
        if amount >= 1 { pose.phase = 0 }
        pose.pointerStrength *= 1 - amount
        pose.velocity = CGVector(dx: velocity.dx * (1 - amount), dy: velocity.dy * (1 - amount))
        pose.ringOffset = CGVector(dx: ringOffset.dx * (1 - amount), dy: ringOffset.dy * (1 - amount))
        pose.extraRingAngle *= 1 - amount
        return pose
    }
}

private struct PoolCausticWelcomeFloatingObjects: View {
    var pose: PoolCausticWelcomePose

    var body: some View {
        Canvas { context, _ in
            context.clip(to: RoundedRectangle(cornerRadius: 31)
                .path(in: CGRect(x: 18, y: 83, width: 357, height: 711)))
            shadow(center: CGPoint(x: pose.ringCenter.x + 3, y: pose.ringCenter.y + 17), size: CGSize(width: 59, height: 23), alpha: 0.30, blur: 6, context: &context)
            shadow(center: CGPoint(x: pose.duckCenter.x + 2, y: pose.duckCenter.y + 13), size: CGSize(width: 44, height: 25), alpha: 0.22, blur: 5, context: &context)
            shadow(center: CGPoint(x: pose.ringCenter.x + 1, y: pose.ringCenter.y + 4), size: CGSize(width: 53, height: 29), alpha: 0.12, blur: 2, context: &context)
            shadow(center: CGPoint(x: pose.duckCenter.x + 1, y: pose.duckCenter.y + 4), size: CGSize(width: 43, height: 29), alpha: 0.13, blur: 1.8, context: &context)
            ring(context: &context, secondary: true)
            ring(context: &context)
            duck(context: &context)
        }
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }

    private func shadow(center: CGPoint, size: CGSize, alpha: Double, blur: Double, context: inout GraphicsContext) {
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: blur))
            let path = Path(ellipseIn: CGRect(x: center.x - size.width / 2, y: center.y - size.height / 2, width: size.width, height: size.height))
            layer.fill(path, with: .color(PoolCausticWelcomeStyle.color(0x1C6098).opacity(alpha)))
        }
    }

    private func ring(context: inout GraphicsContext, secondary: Bool = false) {
        context.drawLayer { layer in
            layer.translateBy(x: pose.ringCenter.x + (secondary ? 10 : 0), y: pose.ringCenter.y - (secondary ? 26 : 0))
            layer.rotate(by: .degrees(pose.ringAngle - (secondary ? -12 : 14)))
            layer.scaleBy(x: secondary ? 1.18 : 1.38, y: secondary ? 0.78 : 0.95)
            layer.stroke(Path(ellipseIn: CGRect(x: -22, y: -22, width: 44, height: 44)),
                         with: .color(PoolCausticWelcomeStyle.color(0xDEE4DC)), lineWidth: 1.4)
            var body = Path()
            body.addEllipse(in: CGRect(x: -19, y: -19, width: 38, height: 38))
            body.addEllipse(in: CGRect(x: -10, y: -10, width: 20, height: 20))
            layer.translateBy(x: 0, y: 3)
            layer.fill(body, with: .color(PoolCausticWelcomeStyle.color(0xA8796B)), style: FillStyle(eoFill: true))
            layer.translateBy(x: 0, y: -3)
            layer.fill(body, with: .linearGradient(Gradient(colors: [PoolCausticWelcomeStyle.color(0xFFFDF0),
                                                                    PoolCausticWelcomeStyle.color(0xD6DDD9)]),
                                                   startPoint: CGPoint(x: -16, y: -18), endPoint: CGPoint(x: 18, y: 18)),
                       style: FillStyle(eoFill: true))
            for index in 0..<4 {
                let start = Double(index) * 90 - 30, end = start + 60
                var band = Path()
                band.addArc(center: .zero, radius: 19, startAngle: .degrees(start), endAngle: .degrees(end), clockwise: false)
                band.addLine(to: CGPoint(x: 10 * cos(end * .pi / 180), y: 10 * sin(end * .pi / 180)))
                band.addArc(center: .zero, radius: 10, startAngle: .degrees(end), endAngle: .degrees(start), clockwise: true)
                band.closeSubpath()
                layer.fill(band, with: .color(PoolCausticWelcomeStyle.color(0xD76060)))
            }
            layer.stroke(Path(ellipseIn: CGRect(x: -18.5, y: -18.5, width: 37, height: 37)),
                         with: .color(Color.white.opacity(0.37)), lineWidth: 0.8)
            layer.stroke(Path(ellipseIn: CGRect(x: -10, y: -10, width: 20, height: 20)),
                         with: .color(PoolCausticWelcomeStyle.color(0x6F8394).opacity(0.35)), lineWidth: 0.8)
        }
    }

    private func duck(context: inout GraphicsContext) {
        context.drawLayer { layer in
            layer.translateBy(x: pose.duckCenter.x, y: pose.duckCenter.y)
            layer.rotate(by: .degrees(pose.duckAngle - 26))
            let body = Path(ellipseIn: CGRect(x: -24, y: -23, width: 48, height: 49))
            layer.fill(body, with: .radialGradient(Gradient(colors: [PoolCausticWelcomeStyle.color(0xFFE767), PoolCausticWelcomeStyle.color(0xFFD52F), PoolCausticWelcomeStyle.color(0xEAB620)]), center: CGPoint(x: -7, y: -8), startRadius: 2, endRadius: 39))
            layer.stroke(body, with: .color(PoolCausticWelcomeStyle.color(0xFBE561).opacity(0.8)), lineWidth: 1)
            layer.fill(Path(ellipseIn: CGRect(x: -18, y: -22, width: 23, height: 24)), with: .color(PoolCausticWelcomeStyle.color(0xFFD733)))
            var beak = Path()
            beak.move(to: CGPoint(x: -18, y: -14)); beak.addQuadCurve(to: CGPoint(x: -9, y: -22), control: CGPoint(x: -28, y: -25)); beak.addLine(to: CGPoint(x: -5, y: -13)); beak.closeSubpath()
            layer.fill(beak, with: .color(PoolCausticWelcomeStyle.color(0xE97A20)))
            layer.fill(Path(ellipseIn: CGRect(x: -14, y: -13, width: 2.5, height: 2.5)), with: .color(PoolCausticWelcomeStyle.color(0x5F4C20)))
            var wing = Path()
            wing.move(to: CGPoint(x: 0, y: -1)); wing.addQuadCurve(to: CGPoint(x: 11, y: 17), control: CGPoint(x: 19, y: 1))
            layer.stroke(wing, with: .color(PoolCausticWelcomeStyle.color(0xE6BC22)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            layer.fill(Path(ellipseIn: CGRect(x: -15, y: -4, width: 9, height: 20)), with: .color(Color.white.opacity(0.18)))
        }
    }

}

private enum PoolCausticWelcomeArtwork {
    /// The material below the water is rasterized once; only shader uniforms change each frame.
    static let base: UIImage = makeBase()

    private static func makeBase() -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: 357, height: 711), format: format).image { renderer in
            let context = renderer.cgContext
            context.translateBy(x: -18, y: -83)
            let colors = [ui(0x579DE9).cgColor, ui(0x4898E4).cgColor, ui(0x3586D4).cgColor] as CFArray
            let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors, locations: [0, 0.5, 1])!
            context.drawLinearGradient(gradient, start: CGPoint(x: 196.5, y: 83), end: CGPoint(x: 196.5, y: 794), options: [])
            context.setStrokeColor(ui(0xB2D9F0).withAlphaComponent(0.13).cgColor)
            context.setLineWidth(0.6)
            for index in 0..<9 {
                let x = 18 + Double(index) * 48
                context.move(to: CGPoint(x: x, y: 83)); context.addLine(to: CGPoint(x: x, y: 794))
            }
            for index in 0..<16 {
                let y = 83 + Double(index) * 48
                context.move(to: CGPoint(x: 18, y: y)); context.addLine(to: CGPoint(x: 375, y: y))
            }
            context.strokePath()
            let palm = palmPath()
            drawSoftShadow(palm, in: context, offset: .zero, blur: 9, alpha: 0.74)
            drawSoftShadow(palm, in: context, offset: CGSize(width: 7, height: 10), blur: 13, alpha: 0.14)
            ladder(context)
            title(context)
        }
    }

    private static func palmPath() -> CGPath {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: -47, y: 725))
        path.addCurve(to: CGPoint(x: 168, y: 323), control1: CGPoint(x: 10, y: 670), control2: CGPoint(x: 79, y: 516))
        path.addLine(to: CGPoint(x: 176, y: 323))
        path.addCurve(to: CGPoint(x: -30, y: 737), control1: CGPoint(x: 94, y: 522), control2: CGPoint(x: 32, y: 680))
        path.closeSubpath()
        let center = CGPoint(x: 172, y: 323)
        let ends: [CGPoint] = [.init(x: -22, y: 257), .init(x: 11, y: 189), .init(x: 91, y: 172),
                               .init(x: 190, y: 174), .init(x: 287, y: 218), .init(x: 350, y: 298),
                               .init(x: 329, y: 388), .init(x: 268, y: 469), .init(x: 165, y: 524)]
        for (index, end) in ends.enumerated() {
            let dx = end.x - center.x, dy = end.y - center.y
            let length = hypot(dx, dy)
            let normal = CGPoint(x: -dy / length, y: dx / length)
            let bend = index.isMultiple(of: 2) ? 18.0 : -18.0
            let mid = CGPoint(x: center.x + dx * 0.58 + normal.x * bend,
                              y: center.y + dy * 0.58 + normal.y * bend)
            path.move(to: center)
            path.addQuadCurve(to: end, control: CGPoint(x: mid.x + normal.x * 44, y: mid.y + normal.y * 44))
            path.addQuadCurve(to: center, control: CGPoint(x: mid.x - normal.x * 44, y: mid.y - normal.y * 44))
            path.closeSubpath()

        }
        return path
    }

    private static func drawSoftShadow(_ path: CGPath, in context: CGContext, offset: CGSize, blur: Double, alpha: Double) {
        context.saveGState()
        // Put the caster outside the image so only its blurred blue shadow remains.
        context.setShadow(offset: CGSize(width: 600 + offset.width, height: offset.height), blur: blur,
                          color: ui(0x174AD2).withAlphaComponent(alpha).cgColor)
        context.translateBy(x: -600, y: 0)
        context.addPath(path)
        context.setFillColor(UIColor.black.cgColor)
        context.fillPath()
        context.restoreGState()
    }

    private static func ladder(_ context: CGContext) {
        context.saveGState()
        context.setLineCap(.round)
        for y in [552.0, 590.0] {
            let rail = CGMutablePath()
            rail.move(to: CGPoint(x: 2, y: y))
            rail.addCurve(to: CGPoint(x: 103, y: y + 12), control1: CGPoint(x: 52, y: y - 2), control2: CGPoint(x: 112, y: y + 1))
            context.addPath(rail); context.setStrokeColor(ui(0x376E99).withAlphaComponent(0.75).cgColor); context.setLineWidth(6); context.strokePath()
            context.addPath(rail); context.setStrokeColor(ui(0xC3DDE5).withAlphaComponent(0.95).cgColor); context.setLineWidth(3); context.strokePath()
        }
        for x in [20.0, 41, 62, 83, 101] {
            context.move(to: CGPoint(x: x, y: 552 + x * 0.08)); context.addLine(to: CGPoint(x: x - 2, y: 590 + x * 0.08))
            context.setStrokeColor(ui(0x4B718D).cgColor); context.setLineWidth(5); context.strokePath()
            context.move(to: CGPoint(x: x, y: 552 + x * 0.08)); context.addLine(to: CGPoint(x: x - 2, y: 590 + x * 0.08))
            context.setStrokeColor(ui(0xD6E0DF).withAlphaComponent(0.85).cgColor); context.setLineWidth(2.5); context.strokePath()
        }
        context.restoreGState()
    }

    private static func title(_ context: CGContext) {
        let descriptor = UIFont.systemFont(ofSize: 126).fontDescriptor.withDesign(.serif)!
        var font = UIFont(descriptor: descriptor, size: 126)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .kern: -8,
                                                       .foregroundColor: ui(0xDCECE8).withAlphaComponent(0.66)]
        let text = "afloat" as NSString
        var size = text.size(withAttributes: attributes)
        if size.width > 308 {
            font = UIFont(descriptor: descriptor, size: 126 * 308 / size.width)
            attributes[.font] = font
            size = text.size(withAttributes: attributes)
        }
        UIGraphicsPushContext(context)
        text.draw(at: CGPoint(x: 196.5 - size.width / 2, y: 420 - size.height / 2), withAttributes: attributes)
        UIGraphicsPopContext()
    }

    private static func ui(_ hex: UInt32) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
                blue: Double(hex & 255) / 255, alpha: 1)
    }
}

private enum PoolCausticWelcomeStyle {
    static let surround = color(0xF1F0E9)
    static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255)
    }
}
