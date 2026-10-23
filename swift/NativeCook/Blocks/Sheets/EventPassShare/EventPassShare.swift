import SwiftUI
import UIKit

/// A value-for-value port of the accepted React Native event-sharing sheet.
struct EventPassShare: View {
    static let loop = EventPassShareTiming.loop
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale

    @State private var controller = EventPassShareController()
    @State private var ambientClock = EventPassShareLogicalClock()
    @State private var ambientDriver = EventPassShareAmbientDriver()
    @State private var clockCadence = EventPassShareController.ClockCadence.stopped
    @State private var revision = 0
    @State private var sheetOpen = true
    @State private var sheetOffset = 0.0
    @State private var sharePresented = false
    @State private var shareItems: [Any] = []
    @State private var activeAction: EventPassShareAction?
    @State private var error: String?
    @State private var impactHaptic = 0
    @State private var successHaptic = 0
    @State private var voiceOverEnabled = UIAccessibility.isVoiceOverRunning
    @State private var applicationActive = true

    private var quiet: Bool { reduceMotion || voiceOverEnabled }
    private var currentTime: TimeInterval { Date().timeIntervalSinceReferenceDate }

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(minimumInterval: 1 / 60, paused: timelinePaused)) { timeline in
                let _ = revision
                let now = timeline.date.timeIntervalSinceReferenceDate
                let frame = controller.frame(at: now)
                let cadence = controller.clockCadence(at: now)
                let ambientTime = ambientClock.value(at: now)
                let automaticFollower = EventPassShareAmbientPlayback.sample(at: ambientTime)
                let spill = EventPassShareAmbient.spillUniform(
                    tiltX: frame.tiltX,
                    tiltY: frame.tiltY,
                    flipAngle: frame.quietCrossfade ? 0 : frame.flipAngle
                )
                let follower = ambientDriver.sample(
                    at: ambientTime,
                    target: spill,
                    automatic: automaticFollower,
                    demoDriven: controller.isDemoDriven,
                    quiet: quiet
                )
                screen(
                    proxy: proxy,
                    frame: frame,
                    ambientTime: ambientTime,
                    follower: follower,
                    spill: spill
                )
                .onChange(of: cadence, initial: true) { _, next in
                    if clockCadence != next { clockCadence = next }
                }
            }
        }
        .ignoresSafeArea()
        .background(Color(red: 11 / 255, green: 21 / 255, blue: 24 / 255).ignoresSafeArea())
        .foregroundStyle(.white)
        .preferredColorScheme(.dark)
        .sensoryFeedback(.impact(weight: .light), trigger: impactHaptic)
        .sensoryFeedback(.success, trigger: successHaptic)
        .onAppear {
            let now = currentTime
            ambientClock.start(at: now)
            controller.appear(autoPlay: autoPlay, quiet: quiet, at: now)
            setApplicationActive(UIApplication.shared.applicationState == .active, at: now)
        }
        .onChange(of: autoPlay) { _, enabled in
            let now = currentTime
            controller.setAutoPlay(enabled, at: now)
            updateClock(at: now)
        }
        .onChange(of: quiet) { _, next in
            let now = currentTime
            controller.setQuiet(next, at: now)
            ambientDriver.reset()
            updateClock(at: now)
        }
        .onChange(of: sharePresented) { _, presented in
            let now = currentTime
            controller.setSharePresented(presented, at: now)
            if !presented {
                shareItems = []
                activeAction = nil
            }
            updateClock(at: now)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIAccessibility.voiceOverStatusDidChangeNotification)) { _ in
            voiceOverEnabled = UIAccessibility.isVoiceOverRunning
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            setApplicationActive(true, at: currentTime)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            setApplicationActive(false, at: currentTime)
        }
        .sheet(isPresented: $sharePresented) {
            EventPassShareActivityView(items: shareItems)
                .presentationDetents([.medium, .large])
        }
    }

    private var timelinePaused: Bool {
        !applicationActive || sharePresented || (quiet && clockCadence == .stopped)
    }

    private func screen(
        proxy: GeometryProxy,
        frame: EventPassShareFrame,
        ambientTime: Double,
        follower: [Double],
        spill: SIMD3<Double>
    ) -> some View {
        let size = proxy.size
        let scale = Double(size.width) / 402
        let safe = EventPassShareSafeArea.insets
        let safeTop = Double(safe.top)
        let safeBottom = Double(safe.bottom)
        let top: (Double) -> Double = { safeTop + ($0 - 62) * scale }
        let contentHeight = max(Double(size.height) + 1, top(852) + safeBottom)
        let driftAngle = quiet ? 0 : EventPassShareAmbient.ambientAngle(ambientTime)
        let ambient = quiet ? [0, 0, 0, 0, 0, 0] : follower

        return ZStack(alignment: .topLeading) {
            eventPage(
                size: size,
                scale: scale,
                safeTop: safeTop,
                driftAngle: driftAngle,
                follower: ambient
            )
            .accessibilityHidden(sheetOpen)
            .allowsHitTesting(!sheetOpen)

            sheet(
                size: size,
                scale: scale,
                top: top,
                safeBottom: safeBottom,
                contentHeight: contentHeight,
                frame: frame,
                driftAngle: driftAngle,
                follower: ambient,
                spill: spill
            )
            .offset(y: sheetOffset)
            .accessibilityHidden(!sheetOpen)
            .allowsHitTesting(sheetOpen)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func eventPage(
        size: CGSize,
        scale: Double,
        safeTop: Double,
        driftAngle: Double,
        follower: [Double]
    ) -> some View {
        ZStack {
            EventPassShareAmbient.pageBackdrop(.init(
                size: size,
                scale: scale,
                referenceYAtCanvasZero: 62 - safeTop / scale,
                sheetOffset: sheetOffset / scale,
                // SwiftUI Metal returns into the view's sRGB color space; only Skia needs the P3 workaround.
                p3: 0,
                driftAngle: driftAngle,
                tiltX: follower[0],
                tiltY: follower[2],
                glow: follower[4]
            ))
            .frame(width: size.width, height: size.height)

            VStack(spacing: 7) {
                EventPassSharePoster(scale: min(1, (size.width - 96) / 251))
                    .shadow(color: .black.opacity(0.20), radius: 20, y: 12)
                    .accessibilityHidden(true)
                Text(EventPassShareContent.title)
                    .font(.system(size: 24, weight: .semibold))
                    .lineSpacing(6)
                    .padding(.top, 15)
                Text("Saturday, July 18 · Los Angeles")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.white.opacity(0.65))
                Button("Share Event") { reopenSheet() }
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.black)
                    .frame(width: min(260, size.width - 64), height: 52)
                    .background(.white, in: Capsule())
                    .padding(.top, 15)
            }
        }
        .frame(width: size.width, height: size.height)
    }

    private func sheet(
        size: CGSize,
        scale: Double,
        top: @escaping (Double) -> Double,
        safeBottom: Double,
        contentHeight: Double,
        frame: EventPassShareFrame,
        driftAngle: Double,
        follower: [Double],
        spill: SIMD3<Double>
    ) -> some View {
        ScrollView(.vertical, showsIndicators: false) {
            ZStack(alignment: .topLeading) {
                UnevenRoundedRectangle(topLeadingRadius: 44 * scale, topTrailingRadius: 44 * scale, style: .continuous)
                    .fill(Color(red: 34 / 255, green: 33 / 255, blue: 34 / 255))
                    .frame(width: size.width, height: contentHeight - top(61) + safeBottom)
                    .offset(y: top(61))

                EventPassShareAmbient.sheetBackdrop(.init(
                    size: CGSize(width: size.width, height: contentHeight - top(61) + safeBottom + 120),
                    scale: scale,
                    referenceYAtCanvasZero: 61,
                    sheetOffset: sheetOffset / scale,
                    p3: 0,
                    driftAngle: driftAngle,
                    tiltX: follower[0],
                    tiltY: follower[2],
                    glow: follower[4],
                    card: spill
                ))
                .frame(width: size.width, height: contentHeight - top(61) + safeBottom + 120)
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 44 * scale, topTrailingRadius: 44 * scale, style: .continuous))
                .offset(y: top(61))

                Capsule()
                    .fill(.white.opacity(0.13))
                    .frame(width: 41 * scale, height: 4 * scale)
                    .offset(x: 180.5 * scale, y: top(69))

                Text("Share Event")
                    .font(.system(size: 17.4 * scale, weight: .medium))
                    .frame(width: 180 * scale, height: 44 * scale)
                    .offset(x: 111 * scale, y: top(78))

                sheetDragSurface(size: size, scale: scale, top: top)
                closeButton(scale: scale, top: top)
                copyPill(frame: frame, scale: scale, top: top)

                EventPassSharePass(frame: frame, scale: scale, onAccessibilityTap: accessibilityFlip)
                    .offset(x: 66.35 * scale, y: top(273.3))

                passTouchSurface(scale: scale)
                    .offset(x: 66.35 * scale, y: top(273.3))

                Text("common\u{2726}")
                    .font(.system(size: 20 * scale, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.31))
                    .frame(width: 130 * scale, height: 28 * scale)
                    .offset(x: 136 * scale, y: top(682))

                stageActions(frame: frame, scale: scale, top: top)

                if let error {
                    Text(error)
                        .font(.system(size: 12 * scale))
                        .foregroundStyle(Color(red: 1, green: 180 / 255, blue: 171 / 255))
                        .multilineTextAlignment(.center)
                        .frame(width: 342 * scale)
                        .offset(x: 30 * scale, y: top(746))
                        .accessibilityLabel(error)
                }

                shareButtonView(scale: scale, top: top)
            }
            .frame(width: size.width, height: contentHeight, alignment: .topLeading)
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func closeButton(scale: Double, top: @escaping (Double) -> Double) -> some View {
        Button { closeSheet() } label: {
            ZStack {
                EventPassShareGlassSurface(kind: .clear, radius: 22 * scale)
                Image(systemName: "xmark")
                    .font(.system(size: 30 * scale, weight: .regular))
                    .foregroundStyle(.white)
            }
            .frame(width: 44 * scale, height: 44 * scale)
        }
        .buttonStyle(.plain)
        .contentShape(Circle())
        .offset(x: 341 * scale, y: top(78))
        .accessibilityLabel("Close sharing sheet")
    }

    private func copyPill(frame: EventPassShareFrame, scale: Double, top: @escaping (Double) -> Double) -> some View {
        Button { copyDetails() } label: {
            ZStack {
                EventPassShareGlassSurface(kind: .clear, radius: 17 * scale)
                    .frame(height: 34 * scale)
                HStack(spacing: 7.5 * scale) {
                    Text(frame.copyConfirmed ? "Copied" : activeAction == .copy ? "Copying…" : "Event info · Jul 18")
                        .font(.system(size: 16 * scale, weight: .regular))
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)
                    Image(systemName: frame.copyConfirmed ? "checkmark" : activeAction == .copy ? "hourglass" : "doc.on.doc")
                        .font(.system(size: 15 * scale, weight: .regular))
                }
                .foregroundStyle(.white.opacity(0.76))
                .padding(.horizontal, 13.5 * scale)
            }
            .frame(width: 158 * scale, height: 44 * scale)
        }
        .buttonStyle(.plain)
        .disabled(activeAction != nil)
        .offset(x: (201 - 79) * scale, y: top(126))
        .accessibilityLabel(frame.copyConfirmed ? "Event details copied" : "Copy event details")
    }

    private func stageActions(frame: EventPassShareFrame, scale: Double, top: @escaping (Double) -> Double) -> some View {
        ZStack(alignment: .topLeading) {
            Button { copyDetails() } label: {
                Group {
                    if frame.copyConfirmed || activeAction == .copy {
                        Image(systemName: frame.copyConfirmed ? "checkmark" : "hourglass")
                            .font(.system(size: 16.6 * scale, weight: .regular))
                    } else {
                        EventPassShareStageIcon(kind: .copy)
                            .frame(width: 16.6 * scale, height: 16.6 * scale)
                    }
                }
                .foregroundStyle(.white.opacity(0.50))
                .frame(width: 44 * scale, height: 44 * scale)
            }
            .buttonStyle(.plain)
            .disabled(activeAction != nil)
            .offset(x: (318.2 - 22) * scale, y: top(710 - 22))
            .accessibilityLabel(frame.copyConfirmed ? "Event details copied" : "Copy event details")

            Button { exportImage() } label: {
                Group {
                    if activeAction == .export {
                        Image(systemName: "hourglass")
                            .font(.system(size: 16.6 * scale, weight: .regular))
                    } else {
                        EventPassShareStageIcon(kind: .download)
                            .frame(width: 16.6 * scale, height: 16.6 * scale)
                    }
                }
                .foregroundStyle(.white.opacity(0.50))
                .frame(width: 44 * scale, height: 44 * scale)
            }
            .buttonStyle(.plain)
            .disabled(activeAction != nil)
            .offset(x: (355 - 22) * scale, y: top(710 - 22))
            .accessibilityLabel("Export event image")
        }
    }

    private func shareButtonView(scale: Double, top: @escaping (Double) -> Double) -> some View {
        Button { shareEvent() } label: {
            ZStack {
                EventPassShareGlassSurface(kind: .prominent, radius: 25.5 * scale)
                Text(activeAction == .share ? "Sharing…" : "Share Event")
                    .font(.system(size: 18 * scale, weight: .medium))
                    .foregroundStyle(.black)
                    .opacity(activeAction != nil && activeAction != .share ? 0.5 : 1)
            }
            .frame(width: 361 * scale, height: 51 * scale)
        }
        .buttonStyle(.plain)
        .disabled(activeAction != nil)
        .offset(x: 20.5 * scale, y: top(775.5))
        .accessibilityLabel("Share Event")
    }

    private func passTouchSurface(scale: Double) -> some View {
        EventPassShareTouchView(
            scale: scale,
            onBegan: { now in
                let accepted = controller.beginInteraction(at: now)
                updateClock(at: now)
                return accepted
            },
            onChanged: { horizontal, vertical, now in
                controller.updateInteraction(horizontal: horizontal, vertical: vertical)
                updateClock(at: now)
            },
            onEnded: { horizontal, vertical, predictedHorizontal, predictedVertical, now in
                let committed = controller.endInteraction(
                    horizontal: horizontal,
                    vertical: vertical,
                    predictedHorizontal: predictedHorizontal,
                    predictedVertical: predictedVertical,
                    at: now,
                    quiet: quiet
                )
                if committed { impactHaptic += 1 }
                updateClock(at: now)
            },
            onCancelled: { now in
                controller.cancelInteraction(at: now)
                updateClock(at: now)
            }
        )
        .frame(width: 270.3 * scale, height: 382 * scale)
        .accessibilityHidden(true)
    }

    private func sheetDragSurface(size: CGSize, scale: Double, top: @escaping (Double) -> Double) -> some View {
        EventPassShareSheetDragView(
            activeOffset: EventPassShareSheetDismiss.activeOffset * scale,
            onChanged: { translation in
                let value = Double(translation)
                sheetOffset = value >= 0 ? value : -EventPassShareSheetDismiss.rubberBand(-value)
            },
            onEnded: { translation, velocity in
                finishSheetDrag(translation: translation, velocity: velocity, height: Double(size.height))
            },
            onCancelled: { returnSheet() }
        )
        .frame(width: size.width, height: top(126) - top(61))
        .offset(y: top(61))
        .accessibilityHidden(true)
    }

    private func copyDetails() {
        guard activeAction == nil else { return }
        activeAction = .copy
        error = nil
        UIPasteboard.general.string = EventPassShareContent.details
        let now = currentTime
        controller.confirmCopy(at: now)
        activeAction = nil
        successHaptic += 1
        updateClock(at: now)
    }

    private func shareEvent() {
        guard activeAction == nil else { return }
        activeAction = .share
        error = nil
        impactHaptic += 1
        shareItems = [EventPassShareContent.details]
        sharePresented = true
    }

    @MainActor
    private func exportImage() {
        guard activeAction == nil else { return }
        activeAction = .export
        error = nil
        impactHaptic += 1
        let renderer = ImageRenderer(content: EventPassShareExportCard().environment(\.colorScheme, .dark))
        renderer.scale = max(2, displayScale)
        guard let image = renderer.uiImage else {
            activeAction = nil
            error = "Couldn’t export the event image. Try again."
            return
        }
        shareItems = [EventPassShareImageItem(image: image)]
        sharePresented = true
    }

    private func accessibilityFlip() {
        let now = currentTime
        controller.toggleFace(at: now, quiet: quiet)
        impactHaptic += 1
        updateClock(at: now)
    }

    private func closeSheet() {
        let now = currentTime
        sheetOpen = false
        controller.setVisible(false, at: now)
        updateClock(at: now)
        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: quiet ? 0 : 0.25)) {
            sheetOffset = EventPassShareSafeArea.screenHeight + EventPassShareSheetDismiss.overshoot
        }
    }

    private func finishSheetDrag(translation: Double, velocity: Double, height: Double) {
        let close = translation > height * EventPassShareSheetDismiss.commitShare || velocity > EventPassShareSheetDismiss.commitVelocity
        if close {
            let now = currentTime
            sheetOpen = false
            controller.setVisible(false, at: now)
            impactHaptic += 1
            updateClock(at: now)
            let animation = quiet
                ? Animation.linear(duration: 0)
                : .spring(duration: EventPassShareSheetDismiss.closeDuration, bounce: 1 - EventPassShareSheetDismiss.closeDampingRatio)
            withAnimation(animation) { sheetOffset = height + EventPassShareSheetDismiss.overshoot }
        } else {
            returnSheet()
        }
    }

    private func returnSheet() {
        let animation = quiet
            ? Animation.linear(duration: 0)
            : .spring(duration: EventPassShareSheetDismiss.returnDuration, bounce: 1 - EventPassShareSheetDismiss.returnDampingRatio)
        withAnimation(animation) { sheetOffset = 0 }
    }

    private func reopenSheet() {
        let now = currentTime
        sheetOpen = true
        controller.setVisible(true, at: now)
        updateClock(at: now)
        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: quiet ? 0 : 0.30)) {
            sheetOffset = 0
        }
    }

    private func updateClock(at now: TimeInterval) {
        clockCadence = controller.clockCadence(at: now)
        revision += 1
    }

    private func setApplicationActive(_ active: Bool, at now: TimeInterval) {
        applicationActive = active
        ambientClock.setForeground(active, at: now)
        controller.setForeground(active, at: now)
        updateClock(at: now)
    }
}

private enum EventPassShareAction: Equatable {
    case copy
    case share
    case export
}

private final class EventPassShareLogicalClock {
    private var origin: TimeInterval?
    private var pausedAt: TimeInterval?

    func start(at now: TimeInterval) {
        if origin == nil { origin = now }
    }

    func setForeground(_ foreground: Bool, at now: TimeInterval) {
        if foreground {
            if let pausedAt {
                origin = origin.map { $0 + max(0, now - pausedAt) }
                self.pausedAt = nil
            }
        } else if pausedAt == nil {
            pausedAt = now
        }
    }

    func value(at now: TimeInterval) -> Double {
        guard let origin else { return 0 }
        return max(0, (pausedAt ?? now) - origin)
    }
}

private final class EventPassShareAmbientDriver {
    private var state = [0.0, 0, 0, 0, 0, 0]
    private var lastSample: Double?
    private var followsPureDemo = false

    func reset() {
        state = [0, 0, 0, 0, 0, 0]
        lastSample = nil
        followsPureDemo = false
    }

    func sample(at time: Double, target: SIMD3<Double>, automatic: [Double], demoDriven: Bool, quiet: Bool) -> [Double] {
        if quiet {
            reset()
            return state
        }
        if demoDriven, lastSample == nil || followsPureDemo {
            state = automatic
            lastSample = time
            followsPureDemo = true
            return state
        }
        if !demoDriven, followsPureDemo {
            state = automatic
            lastSample = time
            followsPureDemo = false
        }
        let delta = max(0, time - (lastSample ?? time))
        EventPassShareAmbient.stepAmbientFollower(
            &state,
            tiltX: target.x,
            tiltY: target.y,
            glow: target.z,
            deltaTime: delta
        )
        lastSample = time
        return state
    }
}

private enum EventPassShareAmbientPlayback {
    private static let samples: [[Double]] = {
        var result = [[0.0, 0, 0, 0, 0, 0]]
        var state = result[0]
        for index in 1...Int(EventPassShareTiming.loop * 60) {
            let time = Double(index) / 60
            let frame = EventPassShareController.demoFrame(at: time)
            let spill = EventPassShareAmbient.spillUniform(
                tiltX: frame.tiltX,
                tiltY: frame.tiltY,
                flipAngle: frame.flipAngle
            )
            EventPassShareAmbient.stepAmbientFollower(
                &state,
                tiltX: spill.x,
                tiltY: spill.y,
                glow: spill.z,
                deltaTime: 1 / 60
            )
            result.append(state)
        }
        return result
    }()

    /// Replays the RN 60 Hz follower from loop origin, making every autoplay frame a pure function of loop time.
    static func sample(at time: Double) -> [Double] {
        var local = time.truncatingRemainder(dividingBy: EventPassShareTiming.loop)
        if local < 0 { local += EventPassShareTiming.loop }
        let sample = local * 60
        let lower = Int(floor(sample))
        let upper = min(lower + 1, samples.count - 1)
        let mix = sample - Double(lower)
        if mix == 0 { return samples[lower] }
        return zip(samples[lower], samples[upper]).map { from, to in
            from + (to - from) * mix
        }
    }
}

private struct EventPassShareStageIcon: View {
    enum Kind: Equatable { case copy, download }
    var kind: Kind

    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / 15, y: size.height / 15)
            let style = StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round)
            if kind == .copy {
                var rear = Path()
                rear.move(to: CGPoint(x: 3.4, y: 9.6))
                rear.addLine(to: CGPoint(x: 3.2, y: 9.6))
                rear.addCurve(to: CGPoint(x: 1.5, y: 7.9), control1: CGPoint(x: 2.26, y: 9.6), control2: CGPoint(x: 1.5, y: 8.84))
                rear.addLine(to: CGPoint(x: 1.5, y: 3.2))
                rear.addCurve(to: CGPoint(x: 3.2, y: 1.5), control1: CGPoint(x: 1.5, y: 2.26), control2: CGPoint(x: 2.26, y: 1.5))
                rear.addLine(to: CGPoint(x: 7.9, y: 1.5))
                rear.addCurve(to: CGPoint(x: 9.6, y: 3.2), control1: CGPoint(x: 8.84, y: 1.5), control2: CGPoint(x: 9.6, y: 2.26))
                rear.addLine(to: CGPoint(x: 9.6, y: 3.4))
                context.stroke(rear, with: .color(.white.opacity(0.50)), style: style)
                context.stroke(
                    Path(roundedRect: CGRect(x: 5.4, y: 5.4, width: 8.2, height: 8.2), cornerRadius: 2.2),
                    with: .color(.white.opacity(0.50)),
                    style: style
                )
            } else {
                var arrow = Path()
                arrow.move(to: CGPoint(x: 7.5, y: 1.6))
                arrow.addLine(to: CGPoint(x: 7.5, y: 9.2))
                arrow.move(to: CGPoint(x: 4.6, y: 6.4))
                arrow.addLine(to: CGPoint(x: 7.5, y: 9.3))
                arrow.addLine(to: CGPoint(x: 10.4, y: 6.4))
                context.stroke(arrow, with: .color(.white.opacity(0.50)), style: style)
                var well = Path()
                well.move(to: CGPoint(x: 1.5, y: 9.6))
                well.addLine(to: CGPoint(x: 1.5, y: 10.5))
                well.addCurve(to: CGPoint(x: 3.6, y: 12.6), control1: CGPoint(x: 1.5, y: 11.66), control2: CGPoint(x: 2.44, y: 12.6))
                well.addLine(to: CGPoint(x: 11.4, y: 12.6))
                well.addCurve(to: CGPoint(x: 13.5, y: 10.5), control1: CGPoint(x: 12.56, y: 12.6), control2: CGPoint(x: 13.5, y: 11.66))
                well.addLine(to: CGPoint(x: 13.5, y: 9.6))
                context.stroke(well, with: .color(.white.opacity(0.50)), style: style)
            }
        }
        .accessibilityHidden(true)
    }
}

enum EventPassShareSafeArea {
    @MainActor static var insets: EdgeInsets {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        let insets = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: insets.top, leading: insets.left, bottom: insets.bottom, trailing: insets.right)
    }

    @MainActor static var screenHeight: Double {
        Double(UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?.bounds.height ?? 852)
    }
}
