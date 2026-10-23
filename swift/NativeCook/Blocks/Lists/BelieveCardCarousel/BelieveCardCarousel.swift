import SwiftUI

struct BelieveCardCarousel: View {
    static let loop: Double = BelieveCardCarouselMotionSpec.loop

    var autoPlay = false
    var onGetStarted: (() -> Void)? = nil
    var onSignIn: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date.now
    @State private var running = false
    @State private var holding = false
    @State private var heldClock = BelieveCardCarouselMotionSpec.holdMilliseconds
    @State private var holdTask: Task<Void, Never>?
    @State private var resumeTask: Task<Void, Never>?

    @State private var scroll = 0.0
    @State private var dragStart = 0.0
    @State private var scrollFrom = 0.0
    @State private var scrollTarget = 0.0
    @State private var scrollVelocity = 0.0
    @State private var scrollStartedAt = Date.distantPast
    @State private var scrollAnimating = false
    @State private var scrollTask: Task<Void, Never>?
    @State private var screenTouchActive = false

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: !running && !scrollAnimating)) { timeline in
                let time = clock(at: timeline.date)
                let offset = scrollValue(at: timeline.date)
                BelieveCardCarouselScene(
                    size: proxy.size,
                    time: time,
                    scroll: offset,
                    showRecorderPulse: autoPlay && running && !reduceMotion,
                    onGetStarted: onGetStarted,
                    onSignIn: onSignIn
                )
                .overlay(alignment: .topLeading) {
                    BelieveCardCarouselTouchSurface(
                        onTouchDown: touchDown,
                        onTouchUp: touchUp,
                        onPanBegan: { beginPan(at: timeline.date) },
                        onPanChanged: updatePan,
                        onPanEnded: { endPan(velocity: $0, at: Date.now) }
                    )
                    .frame(width: proxy.size.width, height: BelieveCardCarouselMetrics.Card.height + 40)
                    .offset(y: BelieveCardCarouselMetrics.Row.topCTA)
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        .background(BelieveCardCarouselPalette.background.ignoresSafeArea())
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .simultaneousGesture(screenContact)
        .onAppear {
            _ = BelieveCardCarouselAssets.registered
            heldClock = autoPlay && !reduceMotion ? 0 : BelieveCardCarouselMotionSpec.holdMilliseconds
            epoch = .now
            running = autoPlay && !reduceMotion
        }
        .onChange(of: autoPlay) { _, enabled in
            holdTask?.cancel()
            resumeTask?.cancel()
            holding = false
            if enabled, !reduceMotion {
                scroll = 0
                scrollFrom = 0
                scrollTarget = 0
                heldClock = 0
                epoch = .now
                running = true
            } else {
                heldClock = BelieveCardCarouselMotionSpec.holdMilliseconds
                running = false
            }
        }
        .onChange(of: reduceMotion) { _, reduced in
            holdTask?.cancel()
            resumeTask?.cancel()
            if scrollAnimating {
                scrollTask?.cancel()
                scroll = scrollTarget
                scrollAnimating = false
            }
            if !reduced, autoPlay {
                scroll = 0
                scrollFrom = 0
                scrollTarget = 0
            }
            holding = false
            heldClock = reduced || !autoPlay ? BelieveCardCarouselMotionSpec.holdMilliseconds : 0
            epoch = .now
            running = autoPlay && !reduced
        }
        .onDisappear {
            holdTask?.cancel()
            resumeTask?.cancel()
            scrollTask?.cancel()
        }
    }

    private var screenContact: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !screenTouchActive else { return }
                screenTouchActive = true
                touchDown()
            }
            .onEnded { _ in
                screenTouchActive = false
                touchUp()
            }
    }

    private func clock(at date: Date) -> Double {
        guard running else { return heldClock }
        var time = date.timeIntervalSince(epoch) * 1_000
        time.formTruncatingRemainder(dividingBy: BelieveCardCarouselMotionSpec.loopMilliseconds)
        if time < 0 { time += BelieveCardCarouselMotionSpec.loopMilliseconds }
        if holding && time >= BelieveCardCarouselMotionSpec.holdMilliseconds {
            return BelieveCardCarouselMotionSpec.holdMilliseconds
        }
        if reduceMotion &&
            time > BelieveCardCarouselMotionSpec.holdMilliseconds - 600 &&
            time < BelieveCardCarouselMotionSpec.holdMilliseconds {
            return BelieveCardCarouselMotionSpec.holdMilliseconds
        }
        return time
    }

    private func touchDown() {
        resumeTask?.cancel()
        if reduceMotion {
            holding = false
            heldClock = BelieveCardCarouselMotionSpec.holdMilliseconds
            running = false
            return
        }
        guard !holding else { return }
        holding = true
        let now = Date.now
        let current = clock(at: now)
        if current >= BelieveCardCarouselMotionSpec.holdMilliseconds {
            heldClock = BelieveCardCarouselMotionSpec.holdMilliseconds
            running = false
            return
        }
        let remaining = BelieveCardCarouselMotionSpec.holdMilliseconds - current
        holdTask?.cancel()
        holdTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(Int64(remaining.rounded())))
            guard !Task.isCancelled, holding else { return }
            heldClock = BelieveCardCarouselMotionSpec.holdMilliseconds
            running = false
        }
    }

    private func touchUp() {
        guard autoPlay, !reduceMotion else { return }
        resumeTask?.cancel()
        resumeTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(Int64(BelieveCardCarouselMotionSpec.resumeMilliseconds)))
            guard !Task.isCancelled, autoPlay, !reduceMotion else { return }
            holdTask?.cancel()
            holding = false
            scrollTask?.cancel()
            scroll = 0
            scrollFrom = 0
            scrollTarget = 0
            scrollAnimating = false
            heldClock = 0
            epoch = .now
            running = true
        }
    }

    private func scrollValue(at date: Date) -> Double {
        guard scrollAnimating else { return scroll }
        let elapsed = date.timeIntervalSince(scrollStartedAt)
        let distance = scrollTarget - scrollFrom
        let progress = BelieveCardCarouselTimeline.snapProgress(
            elapsed: elapsed,
            duration: BelieveCardCarouselMotionSpec.snapDuration,
            dampingRatio: BelieveCardCarouselMotionSpec.snapDampingRatio,
            velocity: scrollVelocity,
            distance: distance
        )
        return scrollFrom + distance * progress
    }

    private func beginPan(at date: Date) {
        scrollTask?.cancel()
        scroll = scrollValue(at: date)
        scrollAnimating = false
        dragStart = scroll
    }

    private func updatePan(translation: Double) {
        var value = dragStart - translation / BelieveCardCarouselMotionSpec.pitch
        if value < -1 {
            value = -1 + (value + 1) * BelieveCardCarouselMotionSpec.rubberBand
        }
        if value > 1 {
            value = 1 + (value - 1) * BelieveCardCarouselMotionSpec.rubberBand
        }
        scroll = value
    }

    private func endPan(velocity: Double, at date: Date) {
        let normalizedVelocity = -velocity / BelieveCardCarouselMotionSpec.pitch
        let projected = scroll + normalizedVelocity * BelieveCardCarouselMotionSpec.velocityProjection
        let target = min(max(projected.rounded(), -1), 1)
        scrollFrom = scroll
        scrollTarget = target
        scrollVelocity = normalizedVelocity
        if reduceMotion {
            scroll = target
            scrollFrom = target
            scrollAnimating = false
            scrollTask?.cancel()
            return
        }
        scrollStartedAt = date
        scrollAnimating = true
        scrollTask?.cancel()
        scrollTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(Int64((BelieveCardCarouselMotionSpec.snapDuration * 1_000).rounded())))
            guard !Task.isCancelled else { return }
            scroll = target
            scrollAnimating = false
        }
    }
}

private struct BelieveCardCarouselScene: View {
    let size: CGSize
    let time: Double
    let scroll: Double
    let showRecorderPulse: Bool
    let onGetStarted: (() -> Void)?
    let onSignIn: (() -> Void)?

    var body: some View {
        let beat = BelieveCardCarouselTimeline.beat(at: time)
        ZStack(alignment: .topLeading) {
            BelieveCardCarouselPalette.background

            if showRecorderPulse {
                Color(red: 0xFB / 255, green: 0xFB / 255, blue: 0xFB / 255)
                    .frame(width: 1, height: 1)
                    .opacity(Int(time / 50).isMultiple(of: 2) ? 0 : 1)
                    .offset(x: size.width - 1, y: size.height - 1)
            }

            BelieveCardCarouselBrandMark(
                size: BelieveCardCarouselMetrics.Splash.width,
                color: BelieveCardCarouselPalette.splashMark
            )
            .opacity(beat.mark)
            .offset(
                x: size.width / 2 - BelieveCardCarouselMetrics.Splash.width / 2,
                y: BelieveCardCarouselMetrics.Splash.centerY - BelieveCardCarouselMetrics.Splash.height / 2
            )

            BelieveCardCarouselHeadline(width: size.width, beat: beat)
                .offset(
                    y: BelieveCardCarouselMetrics.Title.top -
                        BelieveCardCarouselMetrics.Row.shiftTitle * BelieveCardCarouselMetrics.Row.titleTravel * beat.lift
                )

            ZStack(alignment: .topLeading) {
                ForEach(Array(believeCardCarouselCards.enumerated()), id: \.element.id) { index, card in
                    BelieveCardCarouselCardSlot(data: card, index: index, time: time, scroll: scroll)
                }
            }
            .frame(
                width: size.width,
                height: BelieveCardCarouselMetrics.Card.height + 40,
                alignment: .topLeading
            )
            .offset(y: BelieveCardCarouselMetrics.Row.topCTA)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Projects you can believe in: Halo, Beehive, aro")

            BelieveCardCarouselButtons(
                width: size.width - 2 * BelieveCardCarouselMetrics.CTA.side,
                onGetStarted: onGetStarted,
                onSignIn: onSignIn
            )
                .opacity(beat.buttonsIn)
                .offset(x: BelieveCardCarouselMetrics.CTA.side, y: BelieveCardCarouselMetrics.CTA.top + beat.buttonsY)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .contain)
    }
}

private struct BelieveCardCarouselHeadline: View {
    let width: Double
    let beat: BelieveCardCarouselBeat

    var body: some View {
        Canvas { context, _ in
            let font = BelieveCardCarouselAssets.font(BelieveCardCarouselAssets.displayBold, size: BelieveCardCarouselMetrics.Title.size)
            let ascent = BelieveCardCarouselAssets.uiFont(
                BelieveCardCarouselAssets.displayBold,
                size: BelieveCardCarouselMetrics.Title.size
            ).ascender
            let line1 = context.resolve(
                Text("Believe in").font(font).foregroundStyle(BelieveCardCarouselPalette.ink)
            )
            let someone = context.resolve(
                Text("Someone").font(font).foregroundStyle(BelieveCardCarouselPalette.ink)
            )
            let something = context.resolve(
                Text("Something").font(font).foregroundStyle(BelieveCardCarouselPalette.ink)
            )
            let unconstrained = CGSize(width: CGFloat.infinity, height: CGFloat.infinity)
            let line1Size = line1.measure(in: unconstrained)
            let someoneSize = someone.measure(in: unconstrained)
            let somethingSize = something.measure(in: unconstrained)
            let titleOpacity = beat.titleIn * beat.titleOut
            let blur = BelieveCardCarouselMotionSpec.morphBlur * sin(.pi * beat.morph)

            context.opacity = titleOpacity
            context.draw(
                line1,
                at: CGPoint(
                    x: (width - line1Size.width) / 2,
                    y: BelieveCardCarouselMetrics.Title.line1 - ascent + beat.line1Drop
                ),
                anchor: .topLeading
            )
            context.drawLayer { layer in
                layer.opacity = 1 - beat.morph
                layer.addFilter(.blur(radius: blur))
                layer.draw(
                    someone,
                    at: CGPoint(
                        x: (width - someoneSize.width) / 2,
                        y: BelieveCardCarouselMetrics.Title.line2 - ascent + beat.line2Drop
                    ),
                    anchor: .topLeading
                )
            }
            context.drawLayer { layer in
                layer.opacity = beat.morph
                layer.addFilter(.blur(radius: blur))
                layer.draw(
                    something,
                    at: CGPoint(
                        x: (width - somethingSize.width) / 2,
                        y: BelieveCardCarouselMetrics.Title.line2 - ascent + beat.line2Drop
                    ),
                    anchor: .topLeading
                )
            }
        }
        .frame(width: width, height: BelieveCardCarouselMetrics.Title.height)
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isHeader)
        .accessibilityLabel("Believe in Something")
    }
}

private struct BelieveCardCarouselCardSlot: View {
    let data: BelieveCardCarouselCardData
    let index: Int
    let time: Double
    let scroll: Double

    var body: some View {
        let position = Double(index - 1) - scroll
        let x = BelieveCardCarouselTimeline.interpolate(
            position,
            input: [-1, 0, 1],
            output: BelieveCardCarouselMetrics.Row.x,
            clamp: false
        )
        let y = BelieveCardCarouselTimeline.interpolate(
            position,
            input: [-1, 0, 1],
            output: BelieveCardCarouselMetrics.Row.y,
            clamp: true
        )
        let slotScale = BelieveCardCarouselTimeline.interpolate(
            position,
            input: [-1, 0, 1],
            output: BelieveCardCarouselMetrics.Row.scale,
            clamp: true
        )
        let rotation = BelieveCardCarouselTimeline.interpolate(
            position,
            input: [-1, 0, 1],
            output: BelieveCardCarouselMetrics.Row.rotation,
            clamp: true
        )
        let cardBeat = BelieveCardCarouselTimeline.card(at: time, index: index)

        BelieveCardCarouselCard(
            data: data,
            textShift: BelieveCardCarouselMetrics.Row.textShift[index],
            iconShift: BelieveCardCarouselMetrics.Row.iconShift[index],
            textY: BelieveCardCarouselMetrics.Row.textY[index],
            inPerspective: index == 1
        )
        .rotationEffect(.degrees(rotation))
        .scaleEffect(slotScale * cardBeat.scale)
        .opacity(cardBeat.shown)
        .offset(
            x: x - BelieveCardCarouselMetrics.Card.width / 2,
            y: y + cardBeat.rise +
                BelieveCardCarouselMetrics.Row.shiftTitle * (1 - cardBeat.lift)
        )
        .zIndex((10 - abs(position) * 4).rounded())
    }
}

private struct BelieveCardCarouselButtons: View {
    let width: Double
    let onGetStarted: (() -> Void)?
    let onSignIn: (() -> Void)?

    var body: some View {
        VStack(spacing: BelieveCardCarouselMetrics.CTA.gap) {
            Button("Get Started", action: { onGetStarted?() })
                .buttonStyle(BelieveCardCarouselButtonStyle(primary: true))
                .disabled(onGetStarted == nil)
            Button("Already have an account", action: { onSignIn?() })
                .buttonStyle(BelieveCardCarouselButtonStyle(primary: false))
                .disabled(onSignIn == nil)
            Text("By continuing, you agree to accept our\nTerms of Use and Privacy Policy.")
                .font(BelieveCardCarouselAssets.font(BelieveCardCarouselAssets.regular, size: BelieveCardCarouselMetrics.CTA.legalSize))
                .foregroundStyle(BelieveCardCarouselPalette.legal)
                .multilineTextAlignment(.center)
                .lineSpacing(BelieveCardCarouselMetrics.CTA.legalLineHeight - BelieveCardCarouselMetrics.CTA.legalSize)
                .frame(maxWidth: .infinity)
                .padding(.top, 12)
        }
        .frame(width: width, alignment: .top)
    }
}

private struct BelieveCardCarouselButtonStyle: ButtonStyle {
    let primary: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(BelieveCardCarouselAssets.font(BelieveCardCarouselAssets.semibold, size: 17.7))
            .tracking(-0.2)
            .foregroundStyle(primary ? Color.white : BelieveCardCarouselPalette.ink)
            .frame(maxWidth: .infinity)
            .frame(height: BelieveCardCarouselMetrics.CTA.height)
            .background(
                primary ? BelieveCardCarouselPalette.primary : BelieveCardCarouselPalette.secondary,
                in: Capsule()
            )
            .scaleEffect(configuration.isPressed ? BelieveCardCarouselMotionSpec.pressScale : 1)
            .animation(.easeInOut(duration: BelieveCardCarouselMotionSpec.pressDuration), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { _, pressed in
                guard primary, pressed else { return }
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
    }
}
