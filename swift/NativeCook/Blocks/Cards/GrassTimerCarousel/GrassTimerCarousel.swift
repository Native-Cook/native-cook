import SwiftUI

/// A full-screen calm timer whose grass card becomes the timer itself after a held or accessible start.
struct GrassTimerCarousel: View {
    static let loop = 8.0

    /// The public default is still; the gallery demo opts into the deterministic eight-second loop.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOverEnabled
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var chipGestureHeld = false
    @State private var controller = GrassTimerCarouselController()
    @State private var revision = 0
    @State private var chipGestureStarted = false
    @State private var chipGestureCancelled = false
    @State private var carouselGestureActive = false
    @State private var clockCadence = GrassTimerCarouselController.ClockCadence.stopped
    @State private var handledActivationSequence = 0
    @State private var handledSelectionSequence = 0
    @State private var hapticTick = 0
    @State private var selectedMix: String?

    private static let lime = Color(hex: 0xDFFF8A)
    private static let muted = Color.white.opacity(0.60)

    var body: some View {
        GeometryReader { proxy in
            let scale = proxy.size.width / 402
            let contentHeight = max(proxy.size.height + 160, (920 - 62) * scale + 72)
            ScrollView(.vertical, showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    heroTimeline(scale: scale)
                    editorialContent(scale: scale)
                }
                .frame(width: proxy.size.width, height: contentHeight, alignment: .topLeading)
                .contentShape(Rectangle())
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
            .simultaneousGesture(carouselGesture(scale: scale))
            .overlay(alignment: .bottom) {
                bottomTabs(scale: scale)
                    .offset(y: proxy.safeAreaInsets.bottom - 27 * scale)
            }
        }
        .background(Color.black.ignoresSafeArea())
        .foregroundStyle(.white)
        .preferredColorScheme(.dark)
        .onAppear {
            let now = Date().timeIntervalSinceReferenceDate
            controller.appear(at: now)
            controller.autoPlayChanged(autoPlay, at: now)
            controller.setQuiet(calm, autoPlay: autoPlay, at: now)
            controller.scenePhaseChanged(isActive: scenePhase == .active, at: now)
            updateClock(at: now)
        }
        .onChange(of: autoPlay) { _, enabled in
            let now = Date().timeIntervalSinceReferenceDate
            controller.autoPlayChanged(enabled, at: now)
            updateClock(at: now)
        }
        .onChange(of: calm) { _, quiet in
            let now = Date().timeIntervalSinceReferenceDate
            controller.setQuiet(quiet, autoPlay: autoPlay, at: now)
            updateClock(at: now)
        }
        .onChange(of: scenePhase) { _, next in
            let now = Date().timeIntervalSinceReferenceDate
            if next != .active {
                chipGestureStarted = false
                chipGestureCancelled = false
                carouselGestureActive = false
            }
            controller.scenePhaseChanged(isActive: next == .active, at: now)
            updateClock(at: now)
        }
        .onDisappear {
            let now = Date().timeIntervalSinceReferenceDate
            controller.cancelPress(at: now)
            controller.cancelCarousel(at: now)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: hapticTick)
    }

    private func heroTimeline(scale: Double) -> some View {
        TimelineView(.animation(minimumInterval: clockCadence.interval, paused: timelinePaused)) { timeline in
            // Reading this state makes a touch restart a previously-paused hero timeline.
            let _ = revision
            let now = timeline.date.timeIntervalSinceReferenceDate
            let frame = controller.frame(at: now, autoPlay: autoPlay, reducedMotion: calm)
            let cadence = controller.clockCadence(at: now, autoPlay: autoPlay, reducedMotion: calm)
            hero(frame: frame, scale: scale)
                .onChange(of: cadence, initial: true) { _, next in
                    if clockCadence != next { clockCadence = next }
                }
                .onChange(of: frame.activationSequence, initial: true) { _, sequence in
                    guard sequence > handledActivationSequence else { return }
                    hapticTick += sequence - handledActivationSequence
                    handledActivationSequence = sequence
                }
                .onChange(of: frame.selectionSequence, initial: true) { _, sequence in
                    guard sequence > handledSelectionSequence else { return }
                    hapticTick += sequence - handledSelectionSequence
                    handledSelectionSequence = sequence
                }
        }
    }

    private var timelinePaused: Bool {
        scenePhase != .active || clockCadence == .stopped
    }

    private var calm: Bool { reduceMotion || voiceOverEnabled }

    private func hero(frame: GrassTimerCarouselFrame, scale: Double) -> some View {
        let selected = frame.selectedCard
        let aperture = frame.aperture
        let x = (aperture.x + frame.carouselOffset) * scale
        let y = (aperture.y - 62) * scale
        let width = aperture.width * scale
        let height = aperture.height * scale
        return ZStack(alignment: .topLeading) {
            secondCard(selected: selected, frame: frame, scale: scale)
            GrassTimerCarouselAperture(image: GrassTimerCarouselAssets.image(selected == 0 ? "card-grass" : "card-quiet"), frame: frame, scale: scale)
                .offset(x: frame.carouselOffset * scale)
                .shadow(color: .black.opacity(0.26), radius: 22 * scale, y: 15 * scale)

            heroCopy(frame: frame, scale: scale)
                .opacity(frame.copyOpacity)
                .accessibilityHidden(frame.copyOpacity < 0.5)
                .offset(x: frame.carouselOffset * scale)

            Circle()
                .stroke(Self.lime.opacity(0.42), style: StrokeStyle(lineWidth: 2.5 * scale))
                .frame(width: max(0, width - 10 * scale), height: max(0, height - 10 * scale))
                .position(x: x + width * 0.5, y: y + height * 0.5)
                .opacity(frame.ringOpacity)
                .allowsHitTesting(false)

            Circle()
                .trim(from: 0, to: frame.ringProgress)
                .stroke(Self.lime, style: StrokeStyle(lineWidth: 2.5 * scale, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: max(0, width - 10 * scale), height: max(0, height - 10 * scale))
                .position(x: x + width * 0.5, y: y + height * 0.5)
                .opacity(frame.ringOpacity)
                .allowsHitTesting(false)

            Circle()
                .fill(Self.lime.opacity(0.74))
                .blur(radius: 5 * scale)
                .frame(width: 12 * scale, height: 12 * scale)
                .position(x: x + width * 0.5, y: y + 6 * scale)
                .opacity(frame.landingHighlightOpacity)
                .allowsHitTesting(false)

            timerLabel(frame: frame, scale: scale)
                .position(x: x + width * 0.5, y: y + height * 0.5)
                .opacity(frame.timerOpacity)

            if frame.timerOpacity > 0.10, frame.phase == .active || frame.phase == .morphing || frame.phase == .returning {
                let returning = frame.phase == .returning
                Button(returning ? "Resume" : "Stop") {
                    let actionTime = Date().timeIntervalSinceReferenceDate
                    if returning { activate(at: actionTime) } else { stop(at: actionTime) }
                }
                    .font(.system(size: 14 * scale, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.9))
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 8 * scale)
                    .frame(minWidth: 64 * scale, minHeight: 44 * scale)
                    .background(.black.opacity(0.25), in: Capsule())
                    .overlay(Capsule().strokeBorder(.white.opacity(0.18), lineWidth: 1))
                    .position(x: x + width * 0.5, y: y + height * 0.5 + 54 * scale)
                    .accessibilityLabel(returning ? "Resume focus timer" : "Stop focus timer")
            }
        }
        .frame(width: 402 * scale, height: 330 * scale, alignment: .topLeading)
    }

    private func secondCard(selected: Int, frame: GrassTimerCarouselFrame, scale: Double) -> some View {
        let index = selected == 0 ? 1 : 0
        let cardX = frame.neighborCardX * scale
        let title = index == 0 ? "Touch Grass" : "Quiet Day"
        let duration = index == 0 ? "8 hrs" : "1 day"
        return ZStack(alignment: .topLeading) {
            Image(uiImage: GrassTimerCarouselAssets.image(index == 0 ? "card-grass" : "card-quiet"))
                .resizable()
                .scaledToFill()
                .frame(width: 232 * scale, height: 327 * scale)
            Text("Timer")
                .font(.system(size: 18 * scale, weight: .medium))
                .foregroundStyle(.white)
                .position(x: 183 * scale, y: 87 * scale)
            VStack(alignment: .leading, spacing: 2 * scale) {
                Text(title).font(.system(size: 22 * scale, weight: .semibold))
                Text("No Distracting Apps").font(.system(size: 17 * scale)).foregroundStyle(Self.muted)
            }
            .frame(width: 200 * scale, alignment: .leading)
            .position(x: 125 * scale, y: 225 * scale)
            HStack(spacing: 8 * scale) {
                Image(systemName: "play.fill").font(.system(size: 14 * scale, weight: .bold))
                Text(duration).font(.system(size: 16 * scale, weight: .medium))
            }
            .foregroundStyle(.white)
            .frame(width: 80 * scale, height: 40 * scale)
            .background(.white.opacity(0.14), in: Capsule())
            .overlay(Capsule().strokeBorder(.white.opacity(0.12), lineWidth: 1))
            .position(x: 65 * scale, y: 282 * scale)
        }
        .frame(width: 232 * scale, height: 327 * scale)
        .clipShape(RoundedRectangle(cornerRadius: 36 * scale, style: .continuous))
        .position(x: cardX + 116 * scale, y: (-62 + 163.5) * scale)
        .opacity(frame.secondCardOpacity)
        .allowsHitTesting(false)
    }

    private func heroCopy(frame: GrassTimerCarouselFrame, scale: Double) -> some View {
        let title = frame.selectedCard == 0 ? "Touch Grass" : "Quiet Day"
        let duration = frame.selectedCard == 0 ? "8 hrs" : "1 day"
        return ZStack(alignment: .topLeading) {
            Text("Timer")
                .font(.system(size: 18 * scale, weight: .medium))
                .foregroundStyle(.white)
                .position(x: 197 * scale, y: (87 - 62) * scale)

            VStack(alignment: .leading, spacing: 2 * scale) {
                Text(title).font(.system(size: 22 * scale, weight: .semibold))
                Text("No Distracting Apps").font(.system(size: 17 * scale)).foregroundStyle(Self.muted)
            }
            .frame(width: 200 * scale, alignment: .leading)
            .position(x: 139 * scale, y: (225 - 62) * scale)

            HStack(spacing: 8 * scale) {
                Image(systemName: "play.fill").font(.system(size: 14 * scale, weight: .bold))
                Text(duration).font(.system(size: 16 * scale, weight: .medium))
            }
            .foregroundStyle(.white)
            .frame(width: 80 * scale, height: 40 * scale)
            .background(.white.opacity(0.14), in: Capsule())
            .overlay(Capsule().strokeBorder(.white.opacity(0.12), lineWidth: 1))
            .scaleEffect(frame.chipScale)
            .overlay(alignment: .leading) {
                Capsule()
                    .trim(from: 0, to: frame.pressProgress)
                    .stroke(Self.lime.opacity(0.9), style: StrokeStyle(lineWidth: 1.5 * scale, lineCap: .round))
            }
            .padding(.vertical, 2 * scale)
            .contentShape(Rectangle())
            .simultaneousGesture(chipPressGesture(scale: scale))
            .onChange(of: chipGestureHeld) { _, held in
                guard !held, chipGestureStarted else { return }
                cancelChipGesture(at: Date().timeIntervalSinceReferenceDate)
            }
            .position(x: 79 * scale, y: (282 - 62) * scale)
            .accessibilityElement()
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel("Start \(duration) focus timer")
            .accessibilityHint("Double tap to start, or touch and hold for 650 milliseconds")
            .accessibilityAction { activate(at: Date().timeIntervalSinceReferenceDate) }
        }
        .frame(width: 402 * scale, height: 330 * scale, alignment: .topLeading)
        .allowsHitTesting(frame.phase != .active)
    }

    private func timerLabel(frame: GrassTimerCarouselFrame, scale: Double) -> some View {
        VStack(spacing: 5 * scale) {
            Text(timeString(frame.remainingSeconds))
                .font(.system(size: 31 * scale, weight: .medium, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(.white)
            Text(frame.selectedCard == 0 ? "TOUCH GRASS" : "QUIET DAY")
                .font(.system(size: 10 * scale, weight: .semibold))
                .tracking(1.4 * scale)
                .foregroundStyle(.white.opacity(0.75))
        }
        .fixedSize()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Focus timer, \(timeString(frame.remainingSeconds)) remaining")
        .accessibilityHidden(frame.timerOpacity < 0.5)
    }

    private func editorialContent(scale: Double) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 4 * scale) {
                HStack(spacing: 8 * scale) {
                    Text("Mixes").font(.system(size: 18 * scale, weight: .medium))
                    Image(systemName: "chevron.right").font(.system(size: 13 * scale, weight: .semibold)).foregroundStyle(Self.muted)
                }
                Text("Personalized soundscapes to focus, relax\nand sleep.")
                    .font(.system(size: 16.7 * scale))
                    .foregroundStyle(Self.muted)
                    .lineSpacing(5.7 * scale)
            }
            .frame(width: 342 * scale, alignment: .leading)
            .position(x: 201 * scale, y: (407 - 62) * scale)

            mixes(scale: scale)
                .position(x: 196.5 * scale, y: (520 - 62) * scale)

            VStack(alignment: .leading, spacing: 0) {
                Text("Sleep Stories").font(.system(size: 18 * scale, weight: .medium))
                Text("Relax and drift off to these original stories")
                    .font(.system(size: 16.7 * scale))
                    .foregroundStyle(Self.muted)
            }
            .frame(width: 342 * scale, height: 44 * scale, alignment: .topLeading)
            .position(x: 201 * scale, y: (653 - 62) * scale)

            stories(scale: scale)
                .position(x: 208 * scale, y: (780 - 62) * scale)
        }
        .foregroundStyle(.white)
        .frame(width: 402 * scale, height: (920 - 62) * scale, alignment: .topLeading)
    }

    private func mixes(scale: Double) -> some View {
        HStack(spacing: 25 * scale) {
            mix("orb-ocean", label: "Ocean", scale: scale)
            mix("orb-waves", label: "Waves", scale: scale)
            mix("orb-airplane", label: "Airplane", scale: scale)
        }
    }

    private func mix(_ artwork: String, label: String, scale: Double) -> some View {
        Button {
            selectedMix = selectedMix == label ? nil : label
            hapticTick += 1
        } label: {
            VStack(spacing: 10 * scale) {
                ZStack {
                    Image(uiImage: GrassTimerCarouselAssets.image(artwork))
                        .resizable().scaledToFill()
                        .frame(width: 98 * scale, height: 98 * scale)
                        .clipShape(Circle())
                    Image(systemName: selectedMix == label ? "pause.fill" : "play.fill")
                        .font(.system(size: 28 * scale, weight: .medium))
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(0.22), radius: 3, y: 2)
                }
                .frame(width: 105 * scale, height: 105 * scale)
                .overlay(Circle().strokeBorder(.white.opacity(0.30), lineWidth: 1.5 * scale))
                Text(label).font(.system(size: 14 * scale, weight: .semibold))
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(label), \(selectedMix == label ? "playing" : "paused")")
    }

    private func stories(scale: Double) -> some View {
        HStack(spacing: 17 * scale) {
            story("story-mountains", width: 272, scale: scale)
            story("story-clouds", width: 95, scale: scale)
        }
        .frame(width: 388 * scale, alignment: .leading)
        .clipped()
    }

    private func story(_ image: String, width: Double, scale: Double) -> some View {
        Image(uiImage: GrassTimerCarouselAssets.image(image))
            .resizable().scaledToFill()
            .frame(width: width * scale, height: 182 * scale)
            .clipShape(UnevenRoundedRectangle(
                topLeadingRadius: 36 * scale,
                bottomLeadingRadius: 36 * scale,
                bottomTrailingRadius: image == "story-clouds" ? 0 : 36 * scale,
                topTrailingRadius: image == "story-clouds" ? 0 : 36 * scale
            ))
    }

    private func bottomTabs(scale: Double) -> some View {
        HStack(spacing: 0) {
            tab(symbol: "house", label: "Home", selected: false, scale: scale)
            tab(symbol: "square.grid.2x2.fill", label: "My Apps", selected: false, scale: scale)
            tab(symbol: "play.fill", label: "Timer", selected: true, scale: scale)
        }
        .frame(width: 280 * scale, height: 62 * scale)
        .background {
            Capsule().fill(.ultraThinMaterial)
                .overlay(Capsule().fill(Color(hex: 0x5485BD).opacity(0.18)))
        }
        .environment(\.colorScheme, .dark)
        .overlay(Capsule().strokeBorder(.white.opacity(0.18), lineWidth: 1))
    }

    private func tab(symbol: String, label: String, selected: Bool, scale: Double) -> some View {
        VStack(spacing: 4 * scale) {
            Image(systemName: symbol).font(.system(size: 21 * scale, weight: .semibold))
            Text(label).font(.system(size: 12 * scale, weight: .semibold))
        }
        .foregroundStyle(.white.opacity(selected ? 1 : 0.72))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(selected ? .black.opacity(0.20) : .clear, in: Capsule())
    }

    private func carouselGesture(scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                let horizontal = abs(value.translation.width) > abs(value.translation.height)
                let now = Date().timeIntervalSinceReferenceDate
                if !carouselGestureActive {
                    guard horizontal, controller.beginCarousel(at: now) else { return }
                    carouselGestureActive = true
                }
                controller.updateCarousel(translation: value.translation.width / scale)
                updateClock(at: now)
            }
            .onEnded { value in
                guard carouselGestureActive else { return }
                let now = Date().timeIntervalSinceReferenceDate
                controller.endCarousel(
                    translation: value.translation.width / scale,
                    predictedTranslation: value.predictedEndTranslation.width / scale,
                    at: now
                )
                carouselGestureActive = false
                updateClock(at: now)
            }
    }

    private func chipPressGesture(scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($chipGestureHeld) { _, held, _ in held = true }
            .onChanged { value in
                let now = Date().timeIntervalSinceReferenceDate
                if !chipGestureStarted {
                    chipGestureStarted = true
                    chipGestureCancelled = false
                    beginPress(at: now)
                }
                let distance = hypot(value.translation.width, value.translation.height)
                if distance > 18 * scale, !chipGestureCancelled {
                    chipGestureCancelled = true
                    controller.cancelPress(at: now)
                    updateClock(at: now)
                }
            }
            .onEnded { _ in
                let now = Date().timeIntervalSinceReferenceDate
                if chipGestureStarted, !chipGestureCancelled { endPress(at: now) }
                else if chipGestureStarted { controller.cancelPress(at: now) }
                chipGestureStarted = false
                chipGestureCancelled = false
                updateClock(at: now)
            }
    }

    private func beginPress(at now: TimeInterval) {
        controller.beginPress(at: now, reducedMotion: calm)
        updateClock(at: now)
    }

    private func endPress(at now: TimeInterval) {
        controller.endPress(at: now, reducedMotion: calm)
        updateClock(at: now)
    }

    private func activate(at now: TimeInterval) {
        _ = controller.activate(at: now, reducedMotion: calm)
        updateClock(at: now)
    }

    private func stop(at now: TimeInterval) {
        controller.stop(at: now, reducedMotion: calm)
        updateClock(at: now)
    }

    private func cancelChipGesture(at now: TimeInterval) {
        controller.cancelPress(at: now)
        chipGestureStarted = false
        chipGestureCancelled = false
        updateClock(at: now)
    }

    private func updateClock(at now: TimeInterval) {
        clockCadence = controller.clockCadence(at: now, autoPlay: autoPlay, reducedMotion: calm)
        revision += 1
    }

    private func timeString(_ seconds: Int) -> String {
        String(format: "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
}
