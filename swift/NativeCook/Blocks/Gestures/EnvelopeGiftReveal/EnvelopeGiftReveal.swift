import SwiftUI
import UIKit

struct EnvelopeGiftReveal: View {
    static let loop: Double = 10

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var autoplayOrigin = Date()
    @State private var manual = false
    @State private var manualPhase: EnvelopeGiftRevealManualPhase = .sealed
    @State private var manualProgress = 0.0
    @State private var manualTilt = 0.0
    @State private var dragging = false
    @State private var dragOrigin = CGPoint.zero
    @State private var dragProgress = 0.0
    @State private var cracked = false
    @State private var folded = false
    @State private var cancelledReveal = false
    @State private var lastTouch = Date.distantPast
    @State private var deferredAction: Task<Void, Never>?

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        EnvelopeGiftRevealAssets.registerFonts()
    }

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size
            let safe = EnvelopeGiftRevealSafeArea.insets
            let top = max(geometry.safeAreaInsets.top, safe.top)
            let bottom = max(geometry.safeAreaInsets.bottom, safe.bottom)
            let layout = EnvelopeGiftRevealLayout(size: size, top: top, bottom: bottom)

            TimelineView(.animation(minimumInterval: 1 / 60, paused: reduceMotion || (!autoPlay && !manual))) { timeline in
                let frame = frame(at: timeline.date)
                EnvelopeGiftRevealScreen(
                    size: size,
                    layout: layout,
                    frame: frame,
                    open: openLetter,
                    reset: resetLetter
                )
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .simultaneousGesture(dragGesture)
            .onAppear {
                autoplayOrigin = Date()
                if reduceMotion { manual = true }
            }
            .onChange(of: reduceMotion) { _, isReduced in
                deferredAction?.cancel()
                manual = isReduced
                manualPhase = .sealed
                manualProgress = 0
                manualTilt = 0
                autoplayOrigin = Date()
            }
        }
        .ignoresSafeArea()
        .background(EnvelopeGiftRevealPalette.table)
        .preferredColorScheme(.dark)
        .onDisappear { deferredAction?.cancel() }
    }

    private func frame(at date: Date) -> EnvelopeGiftRevealFrame {
        if manual || !autoPlay || reduceMotion {
            return EnvelopeGiftRevealFrame.manual(
                phase: manualPhase,
                progress: manualProgress,
                tilt: manualTilt,
                now: date,
                reduced: reduceMotion
            )
        }
        let elapsed = date.timeIntervalSince(autoplayOrigin)
        let t = elapsed - floor(elapsed / Self.loop) * Self.loop
        return EnvelopeGiftRevealFrame.script(t: t)
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .onChanged { value in
                let now = Date()
                if !dragging {
                    beginDrag(at: value.startLocation, now: now)
                }
                guard dragging else { return }
                lastTouch = now
                let raw = dragProgress + Double(dragOrigin.y - value.location.y) / EnvelopeGiftRevealMotion.travel
                let next = raw > 1 ? 1 + (raw - 1) * 0.2 : max(0, raw)
                manualProgress = next
                let cardOut = EnvelopeGiftRevealEase.clamp((next - EnvelopeGiftRevealMotion.flapShare) / (1 - EnvelopeGiftRevealMotion.flapShare))
                manualTilt = EnvelopeGiftRevealEase.clampSigned(
                    Double(value.location.x - dragOrigin.x) * 0.08,
                    limit: EnvelopeGiftRevealMotion.tiltMax
                ) * min(1, cardOut * 2)

                if !cracked, next >= EnvelopeGiftRevealMotion.flapShare * 0.22 {
                    cracked = true
                    UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
                }
                if !folded, next >= EnvelopeGiftRevealMotion.flapShare {
                    folded = true
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
            .onEnded { value in
                let now = Date()
                lastTouch = now
                guard dragging else {
                    if cancelledReveal {
                        cancelledReveal = false
                        scheduleSealedResume()
                    } else {
                        scheduleRevealResetAndResume()
                    }
                    return
                }
                dragging = false
                let projected = Double(value.predictedEndTranslation.height - value.translation.height) / 0.15
                if manualProgress >= EnvelopeGiftRevealMotion.commit
                    || (projected < -900 && manualProgress > 0.15) {
                    commit(from: manualProgress, tilt: manualTilt, at: now, haptic: true)
                } else {
                    if manualProgress > 0.08 {
                        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
                    }
                    manualPhase = .sealed
                    withAnimation(.interpolatingSpring(stiffness: 260, damping: 18)) {
                        manualProgress = 0
                        manualTilt = 0
                    }
                    scheduleSealedResume()
                }
            }
    }

    private func beginDrag(at point: CGPoint, now: Date) {
        deferredAction?.cancel()
        let current = frame(at: now)
        manual = true
        lastTouch = now
        cancelledReveal = false
        cracked = current.progress >= EnvelopeGiftRevealMotion.flapShare * 0.22
        folded = current.progress >= EnvelopeGiftRevealMotion.flapShare

        if current.after > -0.999, current.after < 0 {
            dragging = false
            cancelledReveal = true
            manualPhase = .sealed
            manualProgress = current.progress
            manualTilt = current.tilt
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
            withAnimation(.interpolatingSpring(stiffness: 260, damping: 18)) {
                manualProgress = 0
                manualTilt = 0
            }
            return
        }

        guard current.fly <= 0.001, current.reset <= 0.001 else {
            dragging = false
            scheduleRevealResetAndResume()
            return
        }
        manualPhase = .sealed
        manualProgress = current.progress
        manualTilt = current.tilt
        dragProgress = current.progress
        dragOrigin = point
        dragging = true
    }

    private func openLetter() {
        let now = Date()
        deferredAction?.cancel()
        manual = true
        lastTouch = now
        commit(from: manualProgress, tilt: manualTilt, at: now, haptic: true)
    }

    private func commit(from progress: Double, tilt: Double, at date: Date, haptic: Bool) {
        manualPhase = .committed(date: date, progress: progress, tilt: tilt)
        manualProgress = progress
        manualTilt = tilt
        if haptic { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }

        let finish = reduceMotion ? 0.2 : 0.26 + 0.42 * (1 - progress)
        deferredAction?.cancel()
        deferredAction = Task { @MainActor in
            if !reduceMotion {
                try? await Task.sleep(for: .seconds(finish + 0.72 * 0.45))
                guard !Task.isCancelled else { return }
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
            guard autoPlay, !reduceMotion else { return }
            let quiet = max(0, 2 - Date().timeIntervalSince(lastTouch))
            try? await Task.sleep(for: .seconds(quiet))
            guard !Task.isCancelled else { return }
            startResetAndResume()
        }
    }

    private func resetLetter() {
        deferredAction?.cancel()
        manual = true
        lastTouch = Date()
        startResetAndResume()
    }

    private func startResetAndResume() {
        manualPhase = .resetting(date: Date())
        deferredAction?.cancel()
        deferredAction = Task { @MainActor in
            try? await Task.sleep(for: .seconds(reduceMotion ? 0.2 : 0.42))
            guard !Task.isCancelled else { return }
            manualPhase = .sealed
            manualProgress = 0
            manualTilt = 0
            let quiet = max(0, 2 - Date().timeIntervalSince(lastTouch))
            try? await Task.sleep(for: .seconds(quiet))
            guard !Task.isCancelled else { return }
            resumeAutoplay()
        }
    }

    private func scheduleSealedResume() {
        deferredAction?.cancel()
        deferredAction = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            resumeAutoplay()
        }
    }

    private func scheduleRevealResetAndResume() {
        deferredAction?.cancel()
        deferredAction = Task { @MainActor in
            let quiet = max(0, 2 - Date().timeIntervalSince(lastTouch))
            try? await Task.sleep(for: .seconds(quiet))
            guard !Task.isCancelled else { return }
            startResetAndResume()
        }
    }

    private func resumeAutoplay() {
        guard autoPlay, !reduceMotion else { return }
        autoplayOrigin = Date()
        manual = false
    }
}

private enum EnvelopeGiftRevealManualPhase {
    case sealed
    case committed(date: Date, progress: Double, tilt: Double)
    case revealed
    case resetting(date: Date)
}

private struct EnvelopeGiftRevealFrame {
    var progress = 0.0
    var fly = 0.0
    var after = -1.0
    var drop = 0.0
    var flip = 0.0
    var reset = 0.0
    var breath = 0.0
    var ambient = 0.0
    var tilt = 0.0
    var final = [0.0, 0.0, 0.0]

    static func script(t: Double) -> Self {
        let ambient = sin(2 * .pi * t / 5) * 2
        if t >= 8.2, t < 8.62 {
            var frame = revealedFrame()
            frame.reset = EnvelopeGiftRevealEase.clamp((t - 8.2) / 0.42)
            return frame
        }
        if t >= 8.62 {
            var frame = Self()
            frame.ambient = ambient
            if t >= 9, t <= 9.7 {
                frame.breath = sin(.pi * (t - 9) / 0.7) * 7
            }
            return frame
        }
        if t < 0.6 {
            var frame = Self()
            frame.ambient = ambient
            return frame
        }
        if t < 2.1 {
            let u = EnvelopeGiftRevealEase.clamp((t - 0.68) / 1.27)
            let e = EnvelopeGiftRevealEase.easeInOutCubic(u)
            var frame = Self()
            frame.progress = (300 / EnvelopeGiftRevealMotion.travel) * e
            frame.ambient = ambient * (1 - EnvelopeGiftRevealEase.clamp(frame.progress / 0.05))
            frame.tilt = 10 * sin(.pi * e) * min(1, EnvelopeGiftRevealEase.clamp((frame.progress - 0.33) / 0.67) * 2)
            return frame
        }
        return committed(elapsed: t - 2.1, progress: 300 / EnvelopeGiftRevealMotion.travel, tilt: 0, reduced: false)
    }

    static func manual(
        phase: EnvelopeGiftRevealManualPhase,
        progress: Double,
        tilt: Double,
        now: Date,
        reduced: Bool
    ) -> Self {
        switch phase {
        case .sealed:
            var frame = Self()
            frame.progress = progress
            frame.tilt = tilt
            frame.ambient = sin(2 * .pi * now.timeIntervalSinceReferenceDate / 5) * 2
                * (1 - EnvelopeGiftRevealEase.clamp(progress / 0.05))
            return frame
        case let .committed(date, p0, tilt0):
            return committed(elapsed: now.timeIntervalSince(date), progress: p0, tilt: tilt0, reduced: reduced)
        case .revealed:
            return revealedFrame()
        case let .resetting(date):
            var frame = revealedFrame()
            frame.reset = EnvelopeGiftRevealEase.clamp(now.timeIntervalSince(date) / (reduced ? 0.2 : 0.42))
            return frame
        }
    }

    private static func committed(elapsed: Double, progress p0: Double, tilt: Double, reduced: Bool) -> Self {
        if reduced {
            let u = EnvelopeGiftRevealEase.clamp(elapsed / 0.2)
            var frame = Self()
            frame.progress = p0 + (1 - p0) * u
            frame.after = elapsed - 0.2
            if elapsed >= 0.2 {
                frame.fly = 1
                frame.drop = 1
                frame.flip = 1
            }
            frame.tilt = tilt * (1 - u)
            let text = EnvelopeGiftRevealEase.clamp(elapsed / 0.32)
            frame.final = [text, text, text]
            return frame
        }

        let finish = 0.26 + 0.42 * (1 - p0)
        let opening = EnvelopeGiftRevealEase.easeOutCubic(EnvelopeGiftRevealEase.clamp(elapsed / finish))
        let after = elapsed - finish
        let fly = after < 0 ? 0 : EnvelopeGiftRevealEase.cubicBezier(EnvelopeGiftRevealEase.clamp(after / 0.72), 0.77, 0, 0.175, 1)
        var frame = Self()
        frame.progress = p0 + (1 - p0) * opening
        frame.after = after
        frame.fly = fly
        frame.drop = after < 0 ? 0 : EnvelopeGiftRevealEase.clamp((after - 0.06) / 0.52)
        frame.flip = after < 0 ? 0 : EnvelopeGiftRevealEase.easeOutBack(EnvelopeGiftRevealEase.clamp((after - 0.88) / 0.9), overshoot: 0.9)
        frame.tilt = tilt * (1 - fly)
        for index in 0 ..< 3 {
            let u = EnvelopeGiftRevealEase.clamp((elapsed - 0.92 - Double(index) * 0.08) / 0.32)
            frame.final[index] = EnvelopeGiftRevealEase.cubicBezier(u, 0.23, 1, 0.32, 1)
        }
        return frame
    }

    private static func revealedFrame() -> Self {
        var frame = Self()
        frame.progress = 1
        frame.fly = 1
        frame.after = 10
        frame.drop = 1
        frame.flip = 1
        frame.final = [1, 1, 1]
        return frame
    }

    var resetOut: Double { EnvelopeGiftRevealEase.clamp(reset / 0.4) }
    var resetIn: Double { EnvelopeGiftRevealEase.clamp((reset - 0.43) / 0.57) }
    var envelopeOpacity: Double { reset > 0 ? resetIn : 1 - drop }
    var cardOpacity: Double { after >= 0 ? 1 - resetOut : 0 }
    var kickerOpacity: Double { reset > 0 ? resetIn : 1 - EnvelopeGiftRevealEase.clamp(progress / 0.08) }
    var hintOpacity: Double { reset > 0 ? resetIn : 1 - EnvelopeGiftRevealEase.clamp(progress / 0.06) }

    func headlineOpacity(order: Int) -> Double {
        if reset > 0 {
            let start = 0.43 + Double(order) * 0.005 / 0.42
            return EnvelopeGiftRevealEase.easeOutCubic(EnvelopeGiftRevealEase.clamp((reset - start) / (0.16 / 0.42)))
        }
        let u = EnvelopeGiftRevealEase.clamp((progress - Double(order) * 0.006) / 0.05)
        return 1 - EnvelopeGiftRevealEase.smoothstep(u)
    }
}

private struct EnvelopeGiftRevealLayout {
    let headlineTop: CGFloat
    let envelopeTop: CGFloat
    let hintY: CGFloat
    let titleTop: CGFloat
    let cardTop: CGFloat
    let messageTop: CGFloat
    let ctaTop: CGFloat

    init(size: CGSize, top: CGFloat, bottom: CGFloat) {
        headlineTop = top + 40
        envelopeTop = round(size.height * 0.5 - 232 / 2 + 24)
        let foot = size.height - max(bottom, 16) - 16
        hintY = foot - 26
        titleTop = headlineTop
        ctaTop = foot - 52
        let titleBottom = titleTop + 88
        cardTop = round((titleBottom + ctaTop) / 2 - (214 + 60) / 2)
        messageTop = cardTop + 214 + 24
    }
}

private struct EnvelopeGiftRevealScreen: View {
    let size: CGSize
    let layout: EnvelopeGiftRevealLayout
    let frame: EnvelopeGiftRevealFrame
    let open: () -> Void
    let reset: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            Image(uiImage: EnvelopeGiftRevealAssets.table(size: size))
                .resizable()
                .frame(width: size.width, height: size.height)

            envelope
            flyingCard
            sealedText
            hint
            revealedText
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private var envelope: some View {
        let fold = EnvelopeGiftRevealEase.clamp(frame.progress / EnvelopeGiftRevealMotion.flapShare)
        let settle = CGFloat(16 * fold)
        let drop = CGFloat(frame.drop * frame.drop * 520)
        return EnvelopeGiftRevealEnvelope(progress: frame.progress, breath: frame.breath, tilt: frame.tilt)
            .frame(width: 336, height: 232, alignment: .topLeading)
            .scaleEffect(frame.reset > 0 ? 0.96 + 0.04 * frame.resetIn : 1)
            .opacity(frame.envelopeOpacity)
            .offset(x: (size.width - 336) / 2, y: layout.envelopeTop + settle + drop + frame.ambient)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Gift envelope for Sam")
            .accessibilityHint("Swipe up to open it")
    }

    @ViewBuilder private var flyingCard: some View {
        if frame.after >= 0 {
            let insideY = layout.envelopeTop + 16 + 18 + 214 * 0.92 / 2 - 222
            let finalY = layout.cardTop + 107
            let y = insideY + (finalY - insideY) * CGFloat(frame.fly) - CGFloat(36 * sin(.pi * frame.fly))
            let scale = (0.92 + 0.08 * EnvelopeGiftRevealEase.easeInOutCubic(frame.fly))
                * (1 + 0.05 * sin(.pi * EnvelopeGiftRevealEase.clamp((frame.after - 0.88) / 0.9)))
            let angle = frame.flip * 180
            let faceFront = cos(angle * .pi / 180) >= 0
            let lift = sin(.pi * EnvelopeGiftRevealEase.clamp((frame.after - 0.88) / 0.9))

            RoundedRectangle(cornerRadius: 10)
                .fill(Color.black.opacity(0.55))
                .frame(width: 324, height: 210)
                .blur(radius: 20)
                .scaleEffect(x: max(0.15, abs(cos(angle * .pi / 180))) * scale, y: scale)
                .opacity(frame.cardOpacity * (1 - 0.35 * lift))
                .offset(x: (size.width - 324) / 2 + 4, y: y - 105 + 14 + 10 * lift)

            ZStack {
                EnvelopeGiftRevealCardFront().opacity(faceFront ? 1 : 0)
                EnvelopeGiftRevealCardBack()
                    .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
                    .opacity(faceFront ? 0 : 1)
            }
            .frame(width: 340, height: 214)
            .rotationEffect(.degrees(frame.tilt))
            .rotation3DEffect(.degrees(angle), axis: (x: 0, y: 1, z: 0), perspective: 0.55)
            .scaleEffect(scale)
            .opacity(frame.cardOpacity)
            .offset(x: (size.width - 340) / 2, y: y - 107)
        }
    }

    private var sealedText: some View {
        Group {
            Text("FROM NORA · 14 OCTOBER")
                .font(EnvelopeGiftRevealAssets.ui(11))
                .tracking(1.6)
                .foregroundStyle(EnvelopeGiftRevealPalette.mute)
                .opacity(frame.kickerOpacity)
                .offset(x: 24, y: layout.headlineTop - 24)

            VStack(alignment: .leading, spacing: 0) {
                headlineLine("Something", row: 0)
                headlineLine("for you", row: 1)
            }
            .offset(x: 24, y: layout.headlineTop)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Something for you")
            .accessibilityAddTraits(.isHeader)
        }
    }

    private func headlineLine(_ value: String, row: Int) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(value.enumerated()), id: \.offset) { index, character in
                let opacity = frame.headlineOpacity(order: row * 8 + index)
                Text(String(character))
                    .font(EnvelopeGiftRevealAssets.serif(44))
                    .foregroundStyle(EnvelopeGiftRevealPalette.ink)
                    .opacity(opacity)
                    .offset(y: 24 * (1 - opacity))
            }
        }
        .frame(height: 48, alignment: .leading)
    }

    private var hint: some View {
        Button(action: open) {
            HStack(spacing: 10) {
                Image(systemName: "chevron.up")
                    .font(.system(size: 15, weight: .regular))
                Text("Lift the flap")
                    .font(EnvelopeGiftRevealAssets.ui(15))
                    .tracking(-0.1)
            }
            .foregroundStyle(EnvelopeGiftRevealPalette.ink)
            .padding(.horizontal, 24)
            .frame(height: 52)
            .background(Color.white.opacity(0.04), in: Capsule())
            .overlay(Capsule().stroke(EnvelopeGiftRevealPalette.faint, lineWidth: 2 / UIScreen.main.scale))
        }
        .buttonStyle(EnvelopeGiftRevealPressButtonStyle())
        .opacity(frame.hintOpacity)
        .scaleEffect(1 - 0.08 * (1 - frame.hintOpacity))
        .fixedSize()
        .offset(x: (size.width - 152) / 2, y: layout.hintY - 26)
        .accessibilityLabel("Open the letter")
    }

    private var revealedText: some View {
        Group {
            Text("From Nora,\nwith love")
                .font(EnvelopeGiftRevealAssets.serif(40))
                .tracking(-0.4)
                .foregroundStyle(EnvelopeGiftRevealPalette.ink)
                .lineSpacing(4)
                .opacity(frame.final[0] * (1 - frame.resetOut))
                .offset(x: 24, y: layout.titleTop + 16 * (1 - frame.final[0]))
                .accessibilityAddTraits(.isHeader)

            VStack(spacing: 4) {
                Text("$50 at Fable & Fern · in store or online")
                    .font(EnvelopeGiftRevealAssets.ui(15))
                    .foregroundStyle(EnvelopeGiftRevealPalette.ink)
                Text("Valid until 14 October 2027")
                    .font(EnvelopeGiftRevealAssets.ui(13))
                    .foregroundStyle(EnvelopeGiftRevealPalette.mute)
            }
            .frame(width: size.width - 48)
            .opacity(frame.final[1] * (1 - frame.resetOut))
            .offset(x: 24, y: layout.messageTop + 16 * (1 - frame.final[1]))

            Button(action: reset) {
                Text("Add to Wallet")
                    .font(EnvelopeGiftRevealAssets.uiBold(16))
                    .tracking(-0.2)
                    .foregroundStyle(EnvelopeGiftRevealPalette.ctaInk)
                    .frame(width: size.width - 48, height: 52)
                    .background(EnvelopeGiftRevealPalette.cta, in: Capsule())
            }
            .buttonStyle(EnvelopeGiftRevealPressButtonStyle())
            .opacity(frame.final[2] * (1 - frame.resetOut))
            .offset(x: 24, y: layout.ctaTop + 16 * (1 - frame.final[2]))
            .accessibilityLabel("Add to Wallet")
        }
    }
}

private struct EnvelopeGiftRevealEnvelope: View {
    let progress: Double
    let breath: Double
    let tilt: Double

    private var fold: Double { EnvelopeGiftRevealEase.clamp(progress / EnvelopeGiftRevealMotion.flapShare) }
    private var angle: Double { fold * 180 + breath }
    private var cardOut: Double { EnvelopeGiftRevealEase.clamp((progress - EnvelopeGiftRevealMotion.flapShare) / (1 - EnvelopeGiftRevealMotion.flapShare)) }
    private var split: Double { EnvelopeGiftRevealEase.clamp(angle / 40) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.black.opacity(0.55))
                .frame(width: 328, height: 224)
                .blur(radius: 18)
                .offset(x: 10, y: 22)

            RoundedRectangle(cornerRadius: 4)
                .fill(EnvelopeGiftRevealPalette.paperInside)
                .frame(width: 336, height: 232)

            EnvelopeGiftRevealFlapShape()
                .fill(EnvelopeGiftRevealPalette.paperLow)
                .overlay {
                    EnvelopeGiftRevealLinerPattern()
                        .background(EnvelopeGiftRevealPalette.letterpress)
                        .scaleEffect(0.96, anchor: .top)
                        .offset(x: 6, y: 5)
                        .clipShape(EnvelopeGiftRevealFlapShape())
                }
                .opacity(angle >= 88 ? 1 : 0)
                .rotation3DEffect(.degrees(-angle), axis: (x: 1, y: 0, z: 0), anchor: .top, perspective: 0.65)

            EnvelopeGiftRevealCardFront()
                .frame(width: 340, height: 214)
                .scaleEffect(0.92)
                .rotationEffect(.degrees(tilt))
                .offset(x: -2, y: 18 - 222 * cardOut)

            EnvelopeGiftRevealSideLeftShape()
                .fill(LinearGradient(colors: [EnvelopeGiftRevealPalette.paperHigh, EnvelopeGiftRevealPalette.paper], startPoint: .topLeading, endPoint: .bottomTrailing))
            EnvelopeGiftRevealSideRightShape()
                .fill(LinearGradient(colors: [EnvelopeGiftRevealPalette.paper, EnvelopeGiftRevealPalette.paperLow], startPoint: .topTrailing, endPoint: .bottomLeading))
            EnvelopeGiftRevealPocketShape()
                .fill(LinearGradient(colors: [EnvelopeGiftRevealPalette.paperHigh, EnvelopeGiftRevealPalette.paper], startPoint: .topLeading, endPoint: .bottom))
                .overlay(EnvelopeGiftRevealPocketShape().stroke(EnvelopeGiftRevealPalette.crease, lineWidth: 1.2).blur(radius: 0.8))

            Image(uiImage: EnvelopeGiftRevealAssets.paper(size: CGSize(width: 336, height: 232), seed: 3))
                .resizable()
                .frame(width: 336, height: 232)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .allowsHitTesting(false)

            Text("for Sam")
                .font(EnvelopeGiftRevealAssets.hand(30))
                .foregroundStyle(EnvelopeGiftRevealPalette.pen)
                .rotationEffect(.degrees(-1.5))
                .offset(x: 246, y: 184)

            EnvelopeGiftRevealFlapFront(
                split: split,
                curl: 28 * sin(.pi * min(180, angle) / 180)
            )
            .frame(width: 336, height: 232, alignment: .topLeading)
            .opacity(angle < 92 ? 1 : 0)
            .rotation3DEffect(.degrees(-angle), axis: (x: 1, y: 0, z: 0), anchor: .top, perspective: 0.65)
        }
        .frame(width: 336, height: 232, alignment: .topLeading)
    }
}

private struct EnvelopeGiftRevealFlapFront: View {
    let split: Double
    let curl: Double

    private let fold = CGFloat(232 * 0.66 * 0.55)

    var body: some View {
        ZStack(alignment: .topLeading) {
            surface
                .mask(alignment: .top) {
                    Rectangle().frame(width: 336, height: fold)
                }

            ZStack(alignment: .topLeading) {
                surface
                    .mask(alignment: .bottom) {
                        Rectangle().frame(width: 336, height: 232 - fold)
                    }
                EnvelopeGiftRevealWaxSeal(split: split)
                    .offset(x: 140, y: 116)
            }
            .frame(width: 336, height: 232, alignment: .topLeading)
            .rotation3DEffect(
                .degrees(curl),
                axis: (x: 1, y: 0, z: 0),
                anchor: UnitPoint(x: 0.5, y: fold / 232),
                perspective: 0.7
            )
        }
        .frame(width: 336, height: 232, alignment: .topLeading)
    }

    private var surface: some View {
        ZStack(alignment: .topLeading) {
            EnvelopeGiftRevealFlapShape()
                .fill(LinearGradient(
                    colors: [EnvelopeGiftRevealPalette.paperHigh, EnvelopeGiftRevealPalette.paper],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ))
            Image(uiImage: EnvelopeGiftRevealAssets.paper(size: CGSize(width: 336, height: 232), seed: 11))
                .resizable()
                .frame(width: 336, height: 232)
                .clipShape(EnvelopeGiftRevealFlapShape())
        }
        .frame(width: 336, height: 232, alignment: .topLeading)
    }
}

private struct EnvelopeGiftRevealCardFront: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 8).fill(EnvelopeGiftRevealPalette.card)
            Image(uiImage: EnvelopeGiftRevealAssets.paper(size: CGSize(width: 340, height: 214), seed: 31))
                .resizable().clipShape(RoundedRectangle(cornerRadius: 8))
            RoundedRectangle(cornerRadius: 3)
                .stroke(EnvelopeGiftRevealPalette.letterpress.opacity(0.7), lineWidth: 1)
                .padding(12)
            RoundedRectangle(cornerRadius: 2)
                .stroke(EnvelopeGiftRevealPalette.letterpress.opacity(0.5), lineWidth: 0.5)
                .padding(15)
            Text("Fable & Fern")
                .font(EnvelopeGiftRevealAssets.serif(32))
                .foregroundStyle(EnvelopeGiftRevealPalette.letterpress)
                .offset(x: 28, y: 21)
            Text("BOOKSELLERS · EST. 1987")
                .font(EnvelopeGiftRevealAssets.uiBold(9))
                .foregroundStyle(EnvelopeGiftRevealPalette.cardMute)
                .offset(x: 29, y: 61)
            Text("GIFT CARD")
                .font(EnvelopeGiftRevealAssets.uiBold(9))
                .foregroundStyle(EnvelopeGiftRevealPalette.cardMute)
                .offset(x: 29, y: 174)
            Text("$50")
                .font(EnvelopeGiftRevealAssets.serif(60))
                .foregroundStyle(EnvelopeGiftRevealPalette.letterpress)
                .frame(width: 284, alignment: .trailing)
                .offset(x: 28, y: 117)
            RoundedRectangle(cornerRadius: 8)
                .stroke(EnvelopeGiftRevealPalette.cardEdge, lineWidth: 1)
                .padding(0.5)
        }
        .frame(width: 340, height: 214)
    }
}

private struct EnvelopeGiftRevealCardBack: View {
    private let lines = ["Sam —", "for the long train rides.", "Pick something you", "would never buy yourself.", "— Nora x"]

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 8).fill(EnvelopeGiftRevealPalette.card)
            Image(uiImage: EnvelopeGiftRevealAssets.paper(size: CGSize(width: 340, height: 214), seed: 77))
                .resizable().clipShape(RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(lines.enumerated()), id: \.offset) { index, line in
                    Text(line)
                        .font(EnvelopeGiftRevealAssets.hand(25))
                        .foregroundStyle(EnvelopeGiftRevealPalette.pen.opacity(0.92))
                        .frame(height: 32, alignment: .leading)
                        .offset(x: index == 4 ? 150 : (index.isMultiple(of: 2) ? 0 : 2), y: index == 4 ? 4 : 0)
                }
            }
            .offset(x: 26, y: 21)
            Text("$50")
                .font(EnvelopeGiftRevealAssets.serif(22))
                .foregroundStyle(EnvelopeGiftRevealPalette.letterpress)
                .frame(width: 296, alignment: .trailing)
                .offset(x: 22, y: 10)
            Text("FABLE & FERN")
                .font(EnvelopeGiftRevealAssets.uiBold(9))
                .foregroundStyle(EnvelopeGiftRevealPalette.cardMute)
                .frame(width: 296, alignment: .trailing)
                .offset(x: 22, y: 39)
            RoundedRectangle(cornerRadius: 8)
                .stroke(EnvelopeGiftRevealPalette.cardEdge, lineWidth: 1)
                .padding(0.5)
        }
        .frame(width: 340, height: 214)
    }
}

private struct EnvelopeGiftRevealWaxSeal: View {
    let split: Double

    var body: some View {
        let fade = 1 - EnvelopeGiftRevealEase.clamp((split - 0.55) / 0.45)
        ZStack {
            sealFace
                .mask(Rectangle().frame(width: 28, height: 64).offset(x: -14))
                .offset(x: -12 * split, y: 10 * split * split)
                .rotationEffect(.radians(-0.3 * split))
            sealFace
                .mask(Rectangle().frame(width: 28, height: 64).offset(x: 14))
                .offset(x: 12 * split, y: 12 * split * split)
                .rotationEffect(.radians(0.36 * split))
        }
        .frame(width: 56, height: 56)
        .scaleEffect(1 + 0.08 * sin(.pi * EnvelopeGiftRevealEase.clamp(split * 2.5)))
        .opacity(fade)
        .shadow(color: Color.black.opacity(0.35), radius: 3, y: 2.5)
    }

    private var sealFace: some View {
        ZStack {
            EnvelopeGiftRevealWaxShape()
                .fill(RadialGradient(colors: [EnvelopeGiftRevealPalette.waxHigh, EnvelopeGiftRevealPalette.wax, EnvelopeGiftRevealPalette.waxLow], center: UnitPoint(x: 0.33, y: 0.3), startRadius: 0, endRadius: 46))
            Circle().stroke(EnvelopeGiftRevealPalette.waxLow.opacity(0.75), lineWidth: 1.6).padding(9)
            Circle().stroke(EnvelopeGiftRevealPalette.waxHigh.opacity(0.6), lineWidth: 0.8).padding(8.4).offset(x: 0.6, y: 0.8)
            Text("N")
                .font(EnvelopeGiftRevealAssets.serif(30))
                .foregroundStyle(EnvelopeGiftRevealPalette.waxLow)
                .offset(y: -1)
        }
        .frame(width: 56, height: 56)
    }
}

private struct EnvelopeGiftRevealFlapShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let tip = rect.height * 0.66
        path.move(to: CGPoint(x: 0, y: 0))
        path.addLine(to: CGPoint(x: rect.width, y: 0))
        path.addLine(to: CGPoint(x: rect.width / 2 + 26, y: tip - 12))
        path.addQuadCurve(to: CGPoint(x: rect.width / 2 - 26, y: tip - 12), control: CGPoint(x: rect.width / 2, y: tip + 4))
        path.closeSubpath()
        return path
    }
}

private struct EnvelopeGiftRevealSideLeftShape: Shape {
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: .zero)
            path.addLine(to: CGPoint(x: rect.width * 0.47, y: rect.height * 0.56))
            path.addLine(to: CGPoint(x: 0, y: rect.height))
            path.closeSubpath()
        }
    }
}

private struct EnvelopeGiftRevealSideRightShape: Shape {
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: CGPoint(x: rect.width, y: 0))
            path.addLine(to: CGPoint(x: rect.width * 0.53, y: rect.height * 0.56))
            path.addLine(to: CGPoint(x: rect.width, y: rect.height))
            path.closeSubpath()
        }
    }
}

private struct EnvelopeGiftRevealPocketShape: Shape {
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: CGPoint(x: 0, y: rect.height))
            path.addQuadCurve(to: CGPoint(x: rect.width / 2, y: rect.height * 0.44), control: CGPoint(x: rect.width * 0.3, y: rect.height * 0.5))
            path.addQuadCurve(to: CGPoint(x: rect.width, y: rect.height), control: CGPoint(x: rect.width * 0.7, y: rect.height * 0.5))
            path.closeSubpath()
        }
    }
}

private struct EnvelopeGiftRevealWaxShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let centre = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2
        for index in 0 ... 72 {
            let angle = Double(index) / 72 * Double.pi * 2
            let r = radius * (1 + 0.05 * sin(5 * angle + 0.6) + 0.035 * sin(9 * angle + 2.1) + 0.02 * sin(14 * angle + 4))
            let point = CGPoint(x: centre.x + cos(angle) * r, y: centre.y + sin(angle) * r)
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}

private struct EnvelopeGiftRevealLinerPattern: View {
    var body: some View {
        Canvas { context, size in
            for index in 0 ..< 22 {
                var path = Path()
                path.move(to: CGPoint(x: CGFloat(index) * 18 - 120, y: 0))
                path.addLine(to: CGPoint(x: CGFloat(index) * 18 + 60, y: size.height))
                context.stroke(path, with: .color(Color(red: 214 / 255, green: 184 / 255, blue: 120 / 255).opacity(0.28)), lineWidth: 0.6)
            }
        }
    }
}

private struct EnvelopeGiftRevealPressButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: configuration.isPressed ? 0.1 : 0.16), value: configuration.isPressed)
    }
}

private enum EnvelopeGiftRevealMotion {
    static let travel = 320.0
    static let commit = 0.42
    static let flapShare = 0.33
    static let tiltMax = 10.0
}

private enum EnvelopeGiftRevealEase {
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func clampSigned(_ value: Double, limit: Double) -> Double { min(limit, max(-limit, value)) }
    static func smoothstep(_ value: Double) -> Double { value * value * (3 - 2 * value) }
    static func easeOutCubic(_ value: Double) -> Double { 1 - pow(1 - clamp(value), 3) }
    static func easeInOutCubic(_ value: Double) -> Double {
        let value = clamp(value)
        return value < 0.5 ? 4 * value * value * value : 1 - pow(-2 * value + 2, 3) / 2
    }
    static func easeOutBack(_ value: Double, overshoot: Double) -> Double {
        let u = clamp(value) - 1
        return 1 + (overshoot + 1) * u * u * u + overshoot * u * u
    }
    static func cubicBezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let target = clamp(x)
        var low = 0.0
        var high = 1.0
        var t = target
        for _ in 0 ..< 12 {
            let current = bezier(t, x1, x2)
            if current < target { low = t } else { high = t }
            t = (low + high) / 2
        }
        return bezier(t, y1, y2)
    }
    private static func bezier(_ t: Double, _ a: Double, _ b: Double) -> Double {
        let u = 1 - t
        return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t
    }
}

private enum EnvelopeGiftRevealSafeArea {
    static var insets: UIEdgeInsets {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?.safeAreaInsets ?? .zero
    }
}
