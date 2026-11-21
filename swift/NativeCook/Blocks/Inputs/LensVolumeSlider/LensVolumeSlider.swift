import CoreText
import SwiftUI
import UIKit

/// A close-up speaker plate with a smoked-glass volume capsule.  The capsule's leading ball is a real SwiftUI layer
/// effect: it resamples the photo and the live glyph/value layer underneath it, so its content flips and disperses
/// instead of being a decorative overlay.  All automatic poses come from `frame(at:)`, making the eight-second
/// recording loop close exactly at its rest frame.
struct LensVolumeSlider: View {
    var autoPlay = true

    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var mode: LensVolumeSliderMode = .automatic
    @State private var loopStart = Date()
    @State private var manualValue = 52.0
    @State private var dragStartValue = 52.0
    @State private var lastNonzero = 52.0
    @State private var isHolding = false
    @State private var dragged = false
    @State private var returnToken = UUID()
    @State private var hapticPulse = 0
    @GestureState private var gestureActive = false
    @State private var takeoverFrame: LensVolumeSliderFrame?
    @State private var takeoverDate = Date()

    // DEBUG-only launch controls keep poster/phase QA reproducible without affecting shipped behavior.
    private var qaTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-lens-time"), args.indices.contains(i + 1) { return Double(args[i + 1]) }
        #endif
        return nil
    }
    private var qaNeutral: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("-lens-neutral")
        #else
        return false
        #endif
    }

    private enum LensVolumeSliderMode: Equatable { case automatic, manual }

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
        LensVolumeSliderFont.register()
    }

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            let scale = size.width / LensVolumeSliderMetrics.width
            let canvasHeight = size.height / scale
            let track = LensVolumeSliderMetrics.track(in: canvasHeight)

            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: timelinePaused)) { timeline in
                let frame = shownFrame(at: timeline.date)
                let fill = LensVolumeSliderMetrics.fill(for: frame.value, lift: frame.lift, stretch: frame.stretch)
                let ball = CGPoint(x: fill.maxX - fill.height / 2, y: track.midY)

                ZStack(alignment: .topLeading) {
                    plate(value: frame.zoomValue, time: frame.time, size: size, scale: scale, dim: frame.dim)

                    LensVolumeSliderTrack(track: track, scale: scale, stretch: frame.trackStretch, flash: frame.flash)
                        .frame(width: size.width, height: size.height, alignment: .topLeading)

                    // This is a duplicate of the *real* source layer (photo + current glyph + value).  Metal samples
                    // that layer inside the circular mask, making the information behind the cap invert naturally.
                    plate(value: frame.zoomValue, time: frame.time, size: size, scale: scale, dim: frame.dim)
                        .layerEffect(
                            ShaderLibrary.lensVolumeSliderRefraction(
                                .float2(ball.x * scale, ball.y * scale),
                                .float(fill.height * scale / 2),
                                .float(1.8 + 0.3 * frame.lift),
                                .float(frame.marble), .float(scale)
                            ),
                            maxSampleOffset: CGSize(width: size.width, height: size.height)
                        )
                        .mask {
                            Capsule()
                                .frame(width: (fill.maxX - fill.minX) * scale, height: fill.height * scale)
                                .position(x: (fill.minX + fill.maxX) / 2 * scale, y: ball.y * scale)
                        }
                        .allowsHitTesting(false)

                    controlContent(frame: frame, track: track, fill: fill, scale: scale)
                        .frame(width: size.width, height: size.height, alignment: .topLeading)

                    LensVolumeSliderGlass(fill: fill, ball: ball, frame: frame, scale: scale)
                        .frame(width: size.width, height: size.height, alignment: .topLeading)
                        .allowsHitTesting(false)

                    Color.clear
                        .contentShape(Rectangle())
                        .gesture(dragGesture(track: track, scale: scale, at: timeline.date))
                        .accessibilityElement()
                        .accessibilityLabel("Speaker volume")
                        .accessibilityValue("\(Int(manualOrFrameValue(frame))) percent")
                        .accessibilityHint("Drag left or right to change volume. Tap the speaker icon to mute.")
                        .accessibilityAdjustableAction { direction in
                            enterManual(at: timeline.date)
                            let change = direction == .increment ? 5.0 : -5.0
                            setManualValue(manualValue + change, animated: !reduceMotion)
                            scheduleReturn()
                        }
                }
                .frame(width: size.width, height: size.height)
                .clipped()
            }
        }
        .ignoresSafeArea()
        .background(Color(red: 0.10, green: 0.105, blue: 0.11))
        .preferredColorScheme(.dark)
        .sensoryFeedback(.selection, trigger: hapticPulse)
        .onAppear { restartLoop() }
        .onChange(of: gestureActive) { _, active in
            if !active, isHolding {
                // Cancellation has no release velocity or tap. Always release the held material.
                isHolding = false
                scheduleReturn()
            }
        }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced {
                mode = .manual
                manualValue = 52
            } else if autoPlay {
                restartLoop()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, autoPlay, !reduceMotion, mode == .automatic { restartLoop() }
        }
    }

    private var timelinePaused: Bool {
        (reduceMotion || !autoPlay) && !isHolding && mode == .manual
    }

    private func shownFrame(at date: Date) -> LensVolumeSliderFrame {
        if mode == .automatic, let qaTime { return .frame(at: qaTime) }
        if reduceMotion || !autoPlay || qaNeutral || mode == .manual {
            let time = takeoverFrame?.time ?? 0
            let live = LensVolumeSliderFrame.manual(value: manualValue, time: time, holding: isHolding)
            if let takeoverFrame {
                return .blend(takeoverFrame, live, amount: min(max(date.timeIntervalSince(takeoverDate) / 0.18, 0), 1))
            }
            return live
        }
        return LensVolumeSliderFrame.frame(at: date.timeIntervalSince(loopStart))
    }

    private func manualOrFrameValue(_ frame: LensVolumeSliderFrame) -> Double {
        mode == .manual || reduceMotion ? manualValue : frame.value
    }

    @ViewBuilder
    private func plate(value: Double, time: Double, size: CGSize, scale: CGFloat, dim: Double) -> some View {
        let drift = LensVolumeSliderFrame.drift(at: time)
        let tile = Image(uiImage: LensVolumeSliderAssets.plate)
            .resizable()
            .scaledToFill()
            .frame(width: size.width, height: size.height)
            .clipped()
        ZStack {
            tile
            tile.scaleEffect(x: -1, y: 1).offset(x: size.width)
        }
            .offset(x: (drift.x - 26) * scale, y: drift.y * scale)
            .scaleEffect(1.05 + 0.04 * value / 100)
            .saturation(1 - 0.2 * dim)
            .brightness(-0.12 * dim)
            .clipped()
    }

    @ViewBuilder
    private func controlContent(frame: LensVolumeSliderFrame, track: CGRect, fill: LensVolumeSliderFill, scale: CGFloat) -> some View {
        let glyphX = (63 + frame.marble * 13) * scale
        let glyphY = track.midY * scale
        let numberX = 107 * scale
        let numberY = track.midY * scale
        LensVolumeSliderSpeakerGlyph(arcOne: frame.arcOne, arcTwo: frame.arcTwo, slashed: frame.slashed, muted: frame.marble > 0.55)
            .frame(width: 30 * scale, height: 30 * scale)
            .foregroundStyle(frame.marble > 0.55 ? Color(red: 0.18, green: 0.18, blue: 0.18) : .white.opacity(0.94))
            .position(x: glyphX, y: glyphY)

        Text("\(Int(frame.value.rounded()))")
            .font(.custom("InterDisplay-SemiBold", size: 28 * scale))
            .monospacedDigit()
            .foregroundStyle(.white.opacity(0.95))
            .opacity(1 - min(max((frame.marble - 0.28) / 0.45, 0), 1))
            .position(x: numberX, y: numberY)
    }

    @ViewBuilder
    private func lensSource(frame: LensVolumeSliderFrame, track: CGRect, fill: LensVolumeSliderFill, size: CGSize,
                            scale: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            plate(value: frame.zoomValue, time: frame.time, size: size, scale: scale, dim: frame.dim)
            controlContent(frame: frame, track: track, fill: fill, scale: scale)
        }
        .frame(width: size.width, height: size.height)
    }

    private func dragGesture(track: CGRect, scale: CGFloat, at now: Date) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { gesture in
                let point = CGPoint(x: gesture.location.x / scale, y: gesture.location.y / scale)
                guard isHolding || LensVolumeSliderMetrics.touchBounds(for: track).contains(point) else { return }
                if !isHolding {
                    enterManual(at: now)
                    dragStartValue = manualValue
                    isHolding = true
                    dragged = false
                    hapticPulse += 1
                }
                let dx = gesture.translation.width / scale
                if abs(dx) > 4 { dragged = true }
                if dragged {
                    let raw = dragStartValue + Double(dx / LensVolumeSliderMetrics.pointsPerUnit)
                    setManualValue(raw, animated: false)
                }
            }
            .onEnded { gesture in
                defer { isHolding = false; scheduleReturn() }
                guard isHolding else { return }
                let point = CGPoint(x: gesture.location.x / scale, y: gesture.location.y / scale)
                if dragged {
                    let velocity = Double(gesture.velocity.width / scale / LensVolumeSliderMetrics.pointsPerUnit)
                    setManualValue((manualValue + velocity * 0.12).rounded(), animated: !reduceMotion)
                } else {
                    tap(at: point, track: track)
                }
            }
    }

    private func enterManual(at now: Date) {
        // Re-grabbing also invalidates an in-flight idle return.
        returnToken = UUID()
        let frame = shownFrame(at: now)
        takeoverFrame = frame
        takeoverDate = now
        manualValue = frame.value
        lastNonzero = max(frame.value, lastNonzero)
        mode = .manual
        returnToken = UUID()
    }

    private func tap(at point: CGPoint, track: CGRect) {
        guard LensVolumeSliderMetrics.touchBounds(for: track).contains(point) else { return }
        if LensVolumeSliderMetrics.glyphHitBox(in: track).contains(point), manualValue >= 0.5 {
            lastNonzero = manualValue
            setManualValue(0, animated: !reduceMotion)
            hapticPulse += 1
        } else if manualValue < 0.5, point.x < track.minX + 108 {
            setManualValue(lastNonzero, animated: !reduceMotion)
            hapticPulse += 1
        } else {
            let target = (point.x + 44 - (track.minX + 8) - 88) / LensVolumeSliderMetrics.pointsPerUnit
            setManualValue(target.rounded(), animated: !reduceMotion)
        }
    }

    private func setManualValue(_ raw: Double, animated: Bool) {
        let value = min(max(raw, 0), 100)
        if value > 0.5 { lastNonzero = value }
        if animated {
            withAnimation(.spring(response: 0.42, dampingFraction: 0.72)) { manualValue = value }
        } else {
            manualValue = value
        }
    }

    private func scheduleReturn() {
        guard autoPlay, !reduceMotion, !qaNeutral, qaTime == nil else { return }
        let token = UUID()
        returnToken = token
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            guard token == returnToken, mode == .manual, !isHolding else { return }
            withAnimation(.spring(response: 0.55, dampingFraction: 0.84)) { manualValue = 52 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.72) {
                guard token == returnToken, !isHolding else { return }
                restartLoop()
            }
        }
    }

    private func restartLoop() {
        loopStart = Date()
        manualValue = 52
        takeoverFrame = nil
        mode = autoPlay && !reduceMotion && !qaNeutral ? .automatic : .manual
    }
}

private enum LensVolumeSliderMetrics {
    static let width: CGFloat = 402
    static let pointsPerUnit: CGFloat = 2.5

    static func track(in canvasHeight: CGFloat) -> CGRect {
        CGRect(x: 24, y: canvasHeight - 181, width: 354, height: 104)
    }

    static func touchBounds(for track: CGRect) -> CGRect { track.insetBy(dx: -12, dy: -12) }
    static func glyphHitBox(in track: CGRect) -> CGRect { CGRect(x: track.minX + 12, y: track.midY - 26, width: 48, height: 52) }

    static func fill(for value: Double, lift: Double, stretch: Double) -> LensVolumeSliderFill {
        let x = 32.0
        let h = 88 + 8 * lift
        let width = max(88, min(338, 88 + pointsPerUnit * value + stretch))
        return LensVolumeSliderFill(minX: x, maxX: x + width, height: h)
    }
}

private struct LensVolumeSliderFill {
    let minX: CGFloat
    let maxX: CGFloat
    let height: CGFloat
}

private struct LensVolumeSliderTrack: View {
    let track: CGRect
    let scale: CGFloat
    let stretch: Double
    let flash: Double

    var body: some View {
        let rect = CGRect(x: track.minX, y: track.minY, width: track.width + CGFloat(stretch), height: track.height)
        Capsule()
            .fill(.ultraThinMaterial)
            .overlay(Capsule().fill(Color(red: 0.235, green: 0.204, blue: 0.192).opacity(0.55)))
            .overlay(Capsule().stroke(LinearGradient(colors: [Color(red: 0.62, green: 0.67, blue: 0.69).opacity(0.72), .white.opacity(0.12)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1.5 * scale))
            .shadow(color: .black.opacity(0.23), radius: 18 * scale, y: 6 * scale)
            .shadow(color: .black.opacity(0.25), radius: 2 * scale, y: scale)
            .overlay(Capsule().stroke(.white.opacity(0.45 * flash), lineWidth: 2 * scale))
            .frame(width: rect.width * scale, height: rect.height * scale)
            .position(x: rect.midX * scale, y: rect.midY * scale)
    }
}

private struct LensVolumeSliderGlass: View {
    let fill: LensVolumeSliderFill
    let ball: CGPoint
    let frame: LensVolumeSliderFrame
    let scale: CGFloat

    var body: some View {
        let r = fill.height / 2
        let bodyWidth = max(0, fill.maxX - fill.minX)
        let bodyCenter = CGPoint(x: (fill.minX + fill.maxX) / 2, y: ball.y)
        let ballDiameter = fill.height
        let causticX = ball.x + r + 21

        ZStack(alignment: .topLeading) {
            Capsule()
                .fill(LinearGradient(colors: [Color(red: 0.31, green: 0.33, blue: 0.35).opacity(0.58), Color(red: 0.24, green: 0.24, blue: 0.25).opacity(0.48)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .overlay(Capsule().stroke(.white.opacity(0.26), lineWidth: scale))
                .frame(width: bodyWidth * scale, height: fill.height * scale)
                .mask {
                    ZStack(alignment: .trailing) {
                        Rectangle()
                        Circle().frame(width: fill.height * scale, height: fill.height * scale)
                            .blendMode(.destinationOut)
                    }.compositingGroup()
                }
                .position(x: bodyCenter.x * scale, y: bodyCenter.y * scale)

            Circle()
                .fill(LinearGradient(colors: [.white.opacity(0.07), Color(red: 0.18, green: 0.19, blue: 0.20).opacity(0.15), .black.opacity(0.15)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .overlay(Circle().stroke(LinearGradient(colors: [Color(red: 0.60, green: 0.74, blue: 0.83).opacity(0.88), .white.opacity(0.10), Color(red: 0.35, green: 0.46, blue: 0.57).opacity(0.70)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1.5 * scale))
                .overlay(Circle().stroke(Color(red: 0.69, green: 0.54, blue: 0.42).opacity(0.35), lineWidth: 2.75 * scale).padding(1.4 * scale))
                .overlay(alignment: .topLeading) {
                    Circle().fill(.white.opacity(0.24)).frame(width: 18 * scale, height: 9 * scale).blur(radius: 3 * scale).offset(x: 18 * scale, y: 16 * scale)
                }
                .shadow(color: .black.opacity(0.34), radius: 14 * scale, y: (8 + 5 * frame.lift) * scale)
                .frame(width: ballDiameter * scale, height: ballDiameter * scale)
                .scaleEffect(x: frame.squash, y: 2 - frame.squash)
                .position(x: ball.x * scale, y: ball.y * scale)

            Circle()
                .fill(Color(red: 0.74, green: 0.86, blue: 0.94).opacity((0.10 + 0.24 * frame.caustic) * (frame.marble > 0.4 ? 0.35 : 1)))
                .blur(radius: 5 * scale)
                .frame(width: (frame.marble > 0.4 ? 18 : 26) * scale, height: (frame.marble > 0.4 ? 18 : 56) * scale)
                .position(x: causticX * scale, y: ball.y * scale)
        }
    }
}

private struct LensVolumeSliderSpeakerGlyph: View {
    let arcOne: Double
    let arcTwo: Double
    let slashed: Double
    let muted: Bool

    var body: some View {
        ZStack {
            Image(systemName: "speaker.fill")
                .font(.system(size: 22, weight: .semibold))
            Image(systemName: "wave.2.right")
                .font(.system(size: 21, weight: .medium))
                .opacity(arcOne)
            Image(systemName: "wave.3.right")
                .font(.system(size: 25, weight: .medium))
                .opacity(arcTwo * 0.8)
            Rectangle()
                .fill(Color.white.opacity(slashed))
                .frame(width: 34, height: 2.4)
                .rotationEffect(.degrees(-45))
                .opacity(muted ? 1 : slashed)
        }
    }
}

private struct LensVolumeSliderFrame {
    let time: Double
    let value: Double
    let zoomValue: Double
    let lift: Double
    let stretch: Double
    let trackStretch: Double
    let flash: Double
    let marble: Double
    let dim: Double
    let squash: Double
    let arcOne: Double
    let arcTwo: Double
    let slashed: Double
    let caustic: Double

    static func blend(_ a: Self, _ b: Self, amount: Double) -> Self {
        let k = amount * amount * (3 - 2 * amount)
        func mix(_ x: Double, _ y: Double) -> Double { x + (y - x) * k }
        return Self(time: a.time, value: b.value, zoomValue: mix(a.zoomValue, b.zoomValue), lift: mix(a.lift, b.lift),
                    stretch: mix(a.stretch, b.stretch), trackStretch: mix(a.trackStretch, b.trackStretch), flash: mix(a.flash, b.flash),
                    marble: mix(a.marble, b.marble), dim: mix(a.dim, b.dim), squash: mix(a.squash, b.squash),
                    arcOne: mix(a.arcOne, b.arcOne), arcTwo: mix(a.arcTwo, b.arcTwo), slashed: mix(a.slashed, b.slashed), caustic: mix(a.caustic, b.caustic))
    }

    static func manual(value: Double, time: Double, holding: Bool) -> Self {
        let muted = value < 0.5 ? 1.0 : 0.0
        return Self(time: time, value: value, zoomValue: value, lift: holding ? 1 : 0, stretch: 0, trackStretch: 0,
                    flash: 0, marble: muted, dim: muted, squash: 1, arcOne: 1 - muted, arcTwo: value >= 34 ? 1 : 0,
                    slashed: muted, caustic: 0.8)
    }

    static func frame(at rawTime: Double) -> Self {
        let t = wrap(rawTime)
        let current = value(at: t)
        let previous = value(at: t - 0.08)
        let speed = (current - previous) / 0.08
        let stretch = lead(for: speed)
        let marble = smooth(segment(t, 4.10, 4.26)) * (1 - smooth(segment(t, 5.25, 5.60)))
        let dim = smooth(segment(t, 4.10, 4.60)) * (1 - smooth(segment(t, 5.20, 5.70)))
        let squashing = t > 4.1 && t < 5.3 ? 1 + 0.045 * sin((t - 4.1) * .pi * 3) * exp(-(t - 4.1) * 3) : 1
        let flash = t > 6.55 ? exp(-(t - 6.55) / 0.16) * segment(t, 6.55, 6.58) : 0
        let lift = press(t, start: 0.2, end: 1.12) + press(t, start: 2.6, end: 3.32) + press(t, start: 5.0, end: 6.52) + press(t, start: 6.96, end: 7.1)
        let muted = marble > 0.5 ? 1.0 : 0.0
        let arcs: (Double, Double, Double)
        if t >= 2.75 && t < 5.2 {
            arcs = (current > 0.5 ? 1 : 0, current >= 34 ? 1 : 0, current < 0.5 ? 1 : 0)
        } else {
            arcs = (1, 1, 0)
        }
        return Self(time: t, value: current, zoomValue: zoomValue(at: t), lift: min(lift, 1), stretch: stretch,
                    trackStretch: flash * 10, flash: flash, marble: marble, dim: dim, squash: squashing,
                    arcOne: arcs.0 * (1 - muted), arcTwo: arcs.1, slashed: max(arcs.2, muted),
                    caustic: 0.8 + 0.4 * min(abs(speed) / 40, 1))
    }

    static func drift(at t: Double) -> (x: CGFloat, y: CGFloat) {
        let a = 2 * Double.pi * t / LensVolumeSlider.loop
        return (8 * sin(a), 5 * sin(2 * a + 1))
    }

    private static func value(at t: Double) -> Double {
        let t = wrap(t)
        if t < 0.34 { return 52 }
        if t < 0.55 { return 52 - 2 * easeOut(segment(t, 0.34, 0.55)) }
        if t < 1.12 {
            let k = segment(t, 0.55, 1.12)
            let h01 = -2 * k * k * k + 3 * k * k
            let h11 = k * k * k - k * k
            return 50 + 30 * h01 + 24 * h11
        }
        if t < 2.75 { return spring(t - 1.12, from: 80, to: 85, velocity: 42.1, damping: 5.5, stiffness: 170) }
        if t < 4.37 { return max(0, 85 * (1 - 1.06 * easeInOutQuad(segment(t, 2.75, 4.37)))) }
        if t < 7.1 { return min(100, 108 * easeInOutCubic(segment(t, 5.2, 6.7))) }
        return spring(t - 7.1, from: 100, to: 52, velocity: 0, damping: 22.1, stiffness: 157.9)
    }

    private static func zoomValue(at t: Double) -> Double {
        // A short causal kernel approximates the reference's 400 ms critically damped camera response.
        let weights = [0.04, 0.10, 0.16, 0.18, 0.17, 0.14, 0.10, 0.07, 0.04]
        return weights.enumerated().reduce(0) { $0 + $1.element * value(at: t - Double($1.offset) * 0.05) }
    }

    private static func wrap(_ t: Double) -> Double {
        let r = t.truncatingRemainder(dividingBy: LensVolumeSlider.loop)
        return r < 0 ? r + LensVolumeSlider.loop : r
    }
    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    private static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
    private static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    private static func easeInOutQuad(_ x: Double) -> Double { x < 0.5 ? 2 * x * x : 1 - 2 * pow(1 - x, 2) }
    private static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    private static func lead(for speed: Double) -> Double {
        let x = speed * LensVolumeSliderMetrics.pointsPerUnit * 0.06
        return x / pow(1 + pow(abs(x) / 16, 4), 0.25)
    }
    private static func press(_ t: Double, start: Double, end: Double) -> Double {
        guard t >= start else { return 0 }
        if t < start + 0.12 { return easeOut(segment(t, start, start + 0.12)) }
        guard t >= end else { return 1 }
        return max(0, spring(t - end, from: 1, to: 0, velocity: 0, damping: 12, stiffness: 220))
    }
    private static func spring(_ t: Double, from: Double, to: Double, velocity: Double, damping: Double, stiffness: Double) -> Double {
        guard t > 0 else { return from }
        let x0 = from - to, w0 = sqrt(stiffness), zeta = damping / (2 * w0)
        if zeta < 1 {
            let wd = w0 * sqrt(1 - zeta * zeta), e = exp(-zeta * w0 * t)
            return to + e * (x0 * cos(wd * t) + ((velocity + zeta * w0 * x0) / wd) * sin(wd * t))
        }
        let e = exp(-w0 * t)
        return to + e * (x0 + (velocity + w0 * x0) * t)
    }
}

private enum LensVolumeSliderAssets {
    static let plate: UIImage = {
        guard let url = Bundle.main.url(forResource: "LensVolumeSlider-plate", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image
    }()
}

private enum LensVolumeSliderFont {
    static func register() {
        _ = registered
    }
    private static let registered: Bool = {
        guard let url = Bundle.main.url(forResource: "LensVolumeSlider-InterDisplay-SemiBold", withExtension: "ttf") else { return false }
        return CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()
}
