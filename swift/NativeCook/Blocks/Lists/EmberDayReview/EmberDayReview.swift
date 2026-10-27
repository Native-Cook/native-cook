import SwiftUI
import UIKit

/*
 Ember Day Review: a warm journal for the end of a day. Three glossy orange four-point stars sit in
 a soft peach glow over a big "Yesterday", a summary card with laurels and a timeline of three
 completed actions; "Today" with a date badge waits at the foot. Lowering the day reveals an
 iOS-style Review page behind it, and moving on shows Today's checklist with an Add Action field.

 A port of react-native/src/blocks/lists/ember-day-review/EmberDayReview.tsx, value for value.

 autoPlay (loop = 10 s), t in seconds:
   0.25-0.80  the hero opens while the glow grows from 60%; the main star turns edge-on, face,
              edge-on and settles 0.48-1.65 (the small ones 120 and 200 ms behind)
   0.85-1.40  the summary resolves; the timeline rows enter 180 ms apart from 0.72
   4.55-4.95  the day surface drops to the foot of the screen, shrinking to 92%, and becomes the card
              that reads "Showing unfinished for selected day"
   6.03-6.35  it comes back
   7.00-7.50  the page slides to Today; 9.25-9.85 it slides back
   9.60-10.0  everything fades out and closes, ready for the next period (at rest = t 0)

 Touch: any finger on a page pauses the loop for 2 s (never while typing); ticking an action fills
 its box with an overshoot and a selection haptic; a typed action joins the list. Reduce Motion
 shows the finished screen and cross-fades the pages.

 The screen is a function of one clock (`EmberDayReviewMotion.sample`), read once a frame by a
 TimelineView that runs only while something moves.
*/
struct EmberDayReview: View {
    /// Period of the autoPlay loop, in seconds (`LOOP`).
    static let loop: Double = 10

    var autoPlay = false
    /// Holds the loop at this time (seconds) and ignores touch: for freezing a frame.
    var previewTime: Double?
    var onCommit: ((Int) -> Void)?

    @StateObject private var motion: EmberDayReviewMotion
    @State private var keyboard: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var typeSize

    init(autoPlay: Bool = false, previewTime: Double? = nil, onCommit: ((Int) -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onCommit = onCommit
        _motion = StateObject(wrappedValue: EmberDayReviewMotion(autoPlay: autoPlay, previewTime: previewTime))
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            let metrics = EmberDayReviewMetrics(
                width: size.width,
                height: size.height,
                top: insets.top,
                bottom: insets.bottom,
                fontScale: EmberDayReviewScale.fontScale(typeSize)
            )
            let _ = mirrorEnvironment()
            let running = motion.isRunning
            TimelineView(.animation(paused: !running)) { timeline in
                stage(metrics, now: running ? timeline.date : Date())
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(EmberDayReviewPalette.screen.ignoresSafeArea())
        // The footer stays where it is when the keyboard opens, as in React Native.
        .ignoresSafeArea(.keyboard)
        .preferredColorScheme(.light)
        .statusBarHidden(false)
        .id(typeSize)
        .onAppear {
            mirrorEnvironment()
            motion.sync()
        }
        .onChange(of: reduceMotion) { _, _ in
            mirrorEnvironment()
            motion.sync()
        }
        .onChange(of: scenePhase) { _, _ in
            mirrorEnvironment()
            motion.sync()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { note in
            guard let frame = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
            keyboard = max(0, UIScreen.main.bounds.height - frame.minY)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            keyboard = 0
        }
    }

    /// The motion model reads Reduce Motion and the scene phase without being published to.
    private func mirrorEnvironment() {
        motion.reduced = reduceMotion
        motion.foreground = scenePhase == .active
        motion.onCommit = onCommit
    }

    // MARK: - Scene

    private func stage(_ metrics: EmberDayReviewMetrics, now: Date) -> some View {
        let sample = motion.sample(at: now)
        let reduced = reduceMotion
        let yesterday = EmberDayReviewYesterdayFrame.at(t: sample.t, reduced: reduced)
        return ZStack(alignment: .topLeading) {
            EmberDayReviewReviewPage(motion: motion, metrics: metrics, visible: sample.reviewVisible)
            daySurface(metrics, sample: sample, yesterday: yesterday, reduced: reduced)
        }
        .frame(width: metrics.width, height: metrics.height, alignment: .topLeading)
    }

    /// `daySurfaceStyle`: the white surface that drops to the foot of the screen and shrinks to 92%
    /// about its top centre while the Review page shows behind it.
    private func daySurface(
        _ metrics: EmberDayReviewMetrics,
        sample: EmberDayReviewSample,
        yesterday: EmberDayReviewYesterdayFrame,
        reduced: Bool
    ) -> some View {
        let review = sample.review
        let math = EmberDayReviewMath.self
        return ZStack(alignment: .topLeading) {
            glow(metrics, sample: sample, reduced: reduced)
            VStack(spacing: 0) {
                EmberDayReviewTopBar(
                    motion: motion,
                    metrics: metrics,
                    visibleDay: sample.visibleDay,
                    tint: math.clamp((review - 0.65) / 0.35),
                    contentOpacity: 1 - math.interpolate(review, [0.65, 0.9], [0, 1])
                )
                viewport(metrics, sample: sample, yesterday: yesterday, reduced: reduced)
            }
            EmberDayReviewCompactCard(
                width: metrics.width,
                opacity: reduced ? 0 : math.interpolate(review, [0.75, 1], [0, 1])
            )
            EmberDayReviewFooter(
                motion: motion,
                metrics: metrics,
                visibleDay: sample.visibleDay,
                committed: motion.committed,
                reduced: reduced
            )
            .frame(width: metrics.width, height: metrics.height, alignment: .bottom)
        }
        .frame(width: metrics.width, height: metrics.height, alignment: .topLeading)
        .background(EmberDayReviewPalette.screen)
        .clipShape(RoundedRectangle(cornerRadius: review > 0 ? 30 : 0, style: .continuous))
        .opacity(reduced ? 1 - review : 1)
        .scaleEffect(reduced ? 1 : 1 - 0.08 * review, anchor: .top)
        .offset(y: reduced ? 0 : review * metrics.height * 0.752)
        .allowsHitTesting(!sample.reviewVisible)
        .accessibilityHidden(sample.reviewVisible)
    }

    /// The peach light behind the day: 460 pt, centred on the hero, fading in as the hero opens and
    /// leaving with the page and the lowered surface.
    private func glow(_ metrics: EmberDayReviewMetrics, sample: EmberDayReviewSample, reduced: Bool) -> some View {
        let math = EmberDayReviewMath.self
        let appear = reduced ? 1 : math.interpolate(sample.t, [0.4, 0.9, 9.6, 10], [0, 1, 1, 0])
        let scale = reduced ? 1 : math.interpolate(sample.t, [0.25, 0.8], [0.6, 1])
        return EmberDayReviewGlow()
            .scaleEffect(scale)
            .opacity(appear * (1 - sample.page) * (1 - sample.review))
            .offset(x: reduced ? 0 : -sample.page * metrics.width)
            .position(
                x: metrics.width / 2,
                y: metrics.top + 52 + (metrics.compact ? 100 : 131)
            )
    }

    /// The two pages, side by side under the top bar, clipped to the space between bar and screen foot.
    private func viewport(
        _ metrics: EmberDayReviewMetrics,
        sample: EmberDayReviewSample,
        yesterday: EmberDayReviewYesterdayFrame,
        reduced: Bool
    ) -> some View {
        let math = EmberDayReviewMath.self
        let onYesterday = sample.visibleDay == .yesterday
        let onToday = sample.visibleDay == .today
        return ZStack(alignment: .topLeading) {
            EmberDayReviewYesterdayPanel(
                motion: motion,
                metrics: metrics,
                frame: yesterday,
                reduced: reduced,
                active: onYesterday && !sample.reviewVisible
            )
            .opacity(reduced ? 1 - sample.page : 1)
            .offset(x: reduced ? 0 : -sample.page * metrics.width)
            .allowsHitTesting(onYesterday)
            .accessibilityHidden(!onYesterday)
            EmberDayReviewTodayPanel(
                motion: motion,
                metrics: metrics,
                tasks: motion.tasks,
                completed: motion.completed,
                committed: motion.committed,
                reduced: reduced,
                active: onToday && !sample.reviewVisible,
                keyboard: keyboard
            )
            .opacity(reduced ? sample.page : 1)
            .offset(x: reduced ? 0 : (1 - sample.page) * metrics.width)
            .allowsHitTesting(onToday)
            .accessibilityHidden(!onToday)
        }
        .frame(width: metrics.width, height: metrics.viewportHeight, alignment: .topLeading)
        .clipped()
        .opacity(reduced ? 1 : math.interpolate(sample.review, [0, 0.4, 0.85, 1], [1, 1, 0.15, 0]))
    }
}

#Preview {
    EmberDayReview(autoPlay: true)
}
