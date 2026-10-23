import SwiftUI

/// A latest-donations screen with a compact photographic fan under its header, ported value for
/// value from the React Native block (`DonationFanCarousel.tsx`, `ReactionMarks.tsx`, `motion.ts`,
/// `theme.ts`). The active donor is a square portrait with a lime keyline; the others recede
/// behind it on both sides. With `autoPlay` a coin and three hearts collect over the portrait while
/// the fan advances three donors and back; a horizontal drag moves the fan by hand.
///
/// - `autoPlay`: runs the 5366 ms loop from rest; a touch stops it and it starts again from rest
///   3.5 s after the last touch.
/// - `freezeAt`: holds the screen at that loop time in milliseconds, with touches off.
/// - Reduce Motion: the first donor stays, fully legible, and neither the loop nor touches move it.
struct DonationFanCarousel: View {
    /// `LOOP_MS` of `motion.ts` (5366 ms) in seconds; the recording reads this line.
    static let loop: Double = 5.366

    var autoPlay = false
    var freezeAt: Double?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var playback = DonationFanCarouselPlayback()
    @State private var springRunning = false
    @State private var springToken = 0
    @State private var resumeToken = 0

    /// `enabled(!frozen && !reduced)` on both gestures.
    private var gesturesEnabled: Bool {
        freezeAt == nil && !reduceMotion
    }

    var body: some View {
        GeometryReader { proxy in
            // `useWindowDimensions()`: the whole window, under the status bar and home indicator.
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            let geometry = DonationFanCarouselGeometry(window: size)

            ZStack(alignment: .topLeading) {
                // The loop and the spring need a frame per refresh; at rest nothing redraws.
                TimelineView(.animation(paused: !(playback.isLooping || springRunning))) { timeline in
                    let frame = playback.sample(at: timeline.date)
                    DonationFanCarouselScreen(
                        geometry: geometry,
                        size: size,
                        clock: frame.clock,
                        page: frame.page,
                        accessibilityDisabled: freezeAt != nil,
                        onAccessibilityAdjust: adjustAccessibility
                    )
                }
                if gesturesEnabled {
                    DonationFanCarouselTouchSurface(
                        onBegan: touchBegan,
                        onPanChanged: { panChanged($0, scale: geometry.scale) },
                        onPanEnded: { panEnded($0, scale: geometry.scale) },
                        onCancelled: touchCancelled,
                        onFinalize: touchFinalized
                    )
                    .frame(width: size.width, height: size.height)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(DonationFanCarouselTheme.Colors.background.ignoresSafeArea())
        .ignoresSafeArea()
        // `<StatusBar style="dark" />`: dark status bar content on the white screen.
        .preferredColorScheme(.light)
        .onAppear(perform: configure)
        .onChange(of: reduceMotion) { configure() }
        .onChange(of: autoPlay) { configure() }
        .onChange(of: freezeAt) { configure() }
        .task(id: resumeToken) { await resumeWhenDue() }
        .task(id: springToken) { await settleSpring() }
    }

    /// The `useEffect` of the React Native block: freeze, start the loop, or rest at the first donor.
    private func configure() {
        if let freezeAt {
            playback.freeze(atMilliseconds: freezeAt)
        } else if autoPlay && !reduceMotion {
            playback.startLooping(at: .now)
        } else {
            playback.reset()
        }
        springRunning = false
        springToken += 1
        resumeToken += 1
    }

    /// Both gestures' `onBegin`: stops the loop and any spring, remembers where the fan was.
    private func touchBegan() {
        playback.touchBegan(at: .now)
        springRunning = false
        springToken += 1
        resumeToken += 1
    }

    private func panChanged(_ translationX: Double, scale: CGFloat) {
        playback.drag(translationX: translationX, scale: Double(scale))
    }

    /// The pan's `onEnd`: the fan springs to the nearest donor the release velocity points at.
    private func panEnded(_ velocityX: Double, scale: CGFloat) {
        playback.release(velocityX: velocityX, scale: Double(scale), at: .now)
        springRunning = true
        springToken += 1
    }

    /// A system cancellation has no trustworthy release velocity, so settle to the nearest donor.
    private func touchCancelled() {
        playback.cancelTouch(at: .now)
        springRunning = playback.springSettleSeconds != nil
        springToken += 1
    }

    /// Both gestures' `onFinalize`: with `autoPlay`, the loop starts again 3.5 s from now.
    private func touchFinalized() {
        playback.finalizeTouch(autoPlay: autoPlay, at: .now)
        resumeToken += 1
    }

    private func adjustAccessibility(_ direction: AccessibilityAdjustmentDirection) {
        guard freezeAt == nil else { return }
        let current = playback.sample(at: .now).page
        let index = Int(max(0, min(3, DonationFanCarouselMath.jsRound(current))))
        let delta = direction == .increment ? 1 : -1
        let target = max(0, min(3, index + delta))
        guard target != index else { return }

        playback.moveFromAccessibility(
            to: Double(target),
            animated: !reduceMotion,
            autoPlay: autoPlay && !reduceMotion,
            at: .now
        )
        springRunning = playback.springSettleSeconds != nil
        springToken += 1
        resumeToken += 1
    }

    /// The frame callback's resume branch, at the time `resumeAt` names.
    private func resumeWhenDue() async {
        guard let resumeAt = playback.resumeAt else { return }
        let wait = resumeAt.timeIntervalSinceNow
        if wait > 0 {
            try? await Task.sleep(for: .seconds(wait))
        }
        guard !Task.isCancelled, playback.resumeAt == resumeAt else { return }
        playback.resume(at: .now)
        springRunning = false
        springToken += 1
    }

    /// Lets the frames stop once the spring has settled and the page rests on its target.
    private func settleSpring() async {
        guard let seconds = playback.springSettleSeconds else {
            springRunning = false
            return
        }
        try? await Task.sleep(for: .seconds(seconds + 0.05))
        guard !Task.isCancelled else { return }
        playback.finishSpring()
        springRunning = false
    }
}
