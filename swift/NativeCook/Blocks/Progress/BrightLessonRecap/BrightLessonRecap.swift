import SwiftUI
import UIKit

/*
 Bright Lesson Recap — a lesson-complete screen with an original otter. It bursts in over broad
 diagonal green bands with "Brilliant work!" in heavy rounded Nunito, then settles into a compact
 recap under four-point sparkles: "Take a bow!", three outlined result pills that grow coloured caps
 (TOTAL XP, AMAZING, QUICK) and count up, a share button and a physical blue CLAIM XP key.

 Loop of 6.5 s (the reference's 5.851 s story, then a fade and a reset), t in seconds:
   0.33        the bands sweep in from the left and the mascot enters from the left (to 0.55).
   0.35-0.55   "Brilliant work!" appears, two lines, large.
   1.20-1.45   the mascot drifts right and compacts; the big title shrinks away.
   1.333-1.5   the bands are gone, the small title rises into place, six sparkles appear.
   1.367-1.5   "Take a bow!".
   1.9-2.35    the share button and the key fade in.
   2.367 / 2.967 / 3.65   the three pills grow their outlines; the caps rise at 2.667 / 3.25 / 3.967
               and the counts land at 3.1 / 3.283 / 3.95 (3:40 for time), each with a small kick.
   5.851-6.25  the scene fades out. 6.25-6.5 empty, identical to t = 0.

 Touch: dragging the scroll view or holding the key pauses the loop; 2 s after the last touch it
 carries on from where it stopped. Claiming (only between 2.35 and 5.851 s) freezes the finished
 recap, turns the key green ("XP CLAIMED ✓"), sends "+45 XP" 56 pt up over 700 ms and calls
 `onClaim(45)`. Tapping the mascot replays. Reduce Motion shows the finished recap (t = 5).
 Large text (content scale above 1.3) or a narrow window stacks the recap instead.
 `previewTime` freezes the scene at that time and turns the touch off.
*/
struct BrightLessonRecap: View {
    /// The loop's period in seconds.
    static let loop = BrightLessonRecapMotion.loop

    /// Plays the loop by itself. Off, the finished recap is shown until the mascot is tapped.
    var autoPlay = false
    /// Freezes the scene at this loop time and disables touch.
    var previewTime: Double?
    /// RN `onClaim(points)`.
    var onClaim: ((Int) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.scenePhase) private var scenePhase

    @State private var clock: BrightLessonRecapClock
    @State private var playing: Bool
    @State private var paused = false
    @State private var interacting = false
    @State private var claimed = false
    @State private var rewardStart: Date?
    @State private var contentHeight: CGFloat = 0
    @State private var insets = BrightLessonRecapSafeArea.insets
    @State private var impact = UIImpactFeedbackGenerator(style: .light)
    @State private var notification = UINotificationFeedbackGenerator()

    private typealias M = BrightLessonRecapMotion

    init(autoPlay: Bool = false, previewTime: Double? = nil, onClaim: ((Int) -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onClaim = onClaim
        _ = BrightLessonRecapFonts.registered
        // RN: previewTime, else the settled recap under Reduce Motion or without autoPlay, else 0.
        let initial: Double
        if let previewTime {
            initial = BrightLessonRecapMotion.normalize(previewTime)
        } else if UIAccessibility.isReduceMotionEnabled || !autoPlay {
            initial = BrightLessonRecapMotion.settled
        } else {
            initial = 0
        }
        _clock = State(initialValue: BrightLessonRecapClock(initial: initial))
        _playing = State(initialValue: autoPlay)
    }

    // MARK: State

    private var interactive: Bool { previewTime == nil }

    /// RN `useSceneClock(…, running = playing && !paused && !claimed, …)` with the foreground and
    /// Reduce Motion conditions of its frame callback.
    private var running: Bool {
        playing && !paused && !claimed && scenePhase == .active && !reduceMotion && previewTime == nil
    }

    private struct ResumeKey: Equatable {
        var paused: Bool
        var interacting: Bool
        var foreground: Bool
        var reduced: Bool
        var claimed: Bool
    }

    private var resumeKey: ResumeKey {
        ResumeKey(paused: paused, interacting: interacting, foreground: scenePhase == .active, reduced: reduceMotion, claimed: claimed)
    }

    private func displayTime(_ date: Date) -> Double {
        if let previewTime { return M.normalize(previewTime) }
        // RN `time.set(5)` on a claim, and the settled pose under Reduce Motion.
        if reduceMotion || claimed { return M.settled }
        return clock.time(at: date)
    }

    // MARK: Body

    var body: some View {
        GeometryReader { proxy in
            let metrics = BrightLessonRecapMetrics(
                width: proxy.size.width,
                height: proxy.size.height,
                insets: insets,
                fontScale: typeSize.brightLessonRecapScale
            )
            screen(metrics)
        }
        .ignoresSafeArea()
        .background(Color.white.ignoresSafeArea())
        // StatusBar style "dark": dark glyphs on the white screen.
        .preferredColorScheme(.light)
        .onAppear { insets = BrightLessonRecapSafeArea.insets }
        .onChange(of: running, initial: true) { _, on in clock.setRunning(on) }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced { clock.reset(to: M.settled) }
        }
        .task(id: resumeKey) {
            // RN: two seconds after the last touch the loop carries on.
            let key = resumeKey
            guard key.paused, !key.interacting, key.foreground, !key.reduced, !key.claimed else { return }
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            guard !Task.isCancelled else { return }
            paused = false
        }
    }

    private func screen(_ metrics: BrightLessonRecapMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            Color.white
            ScrollView(.vertical, showsIndicators: false) {
                content(metrics)
                    .frame(width: metrics.width)
                    .background {
                        GeometryReader { proxy in
                            Color.clear.preference(key: BrightLessonRecapContentHeightKey.self, value: proxy.size.height)
                        }
                    }
                    .background {
                        BrightLessonRecapScrollProbe(onBegan: pause, onEnded: release)
                    }
            }
            .scrollBounceBehavior(.always)
            .onPreferenceChange(BrightLessonRecapContentHeightKey.self) { contentHeight = $0 }
            .frame(width: metrics.width, height: metrics.height)
            .allowsHitTesting(interactive)

            // RN `overflowsY`: the blur shows only when the content is taller than the viewport.
            if contentHeight > metrics.height + 4 {
                BrightLessonRecapBlurEdges(width: metrics.width, height: metrics.height, solid: reduceTransparency)
            }
        }
        .frame(width: metrics.width, height: metrics.height, alignment: .topLeading)
        .clipped()
    }

    /// RN `contentContainerStyle`: padding of the insets plus 18 pt, and at least the window's height.
    private func content(_ metrics: BrightLessonRecapMetrics) -> some View {
        TimelineView(.animation(paused: !running)) { timeline in
            let t = displayTime(timeline.date)
            let actionsEnabled = claimed || (t >= M.actionsStart && t < M.sourceEnd)
            let actionsOpacity = claimed ? 1 : M.phase(t, 1.9, 2.35) * (1 - M.phase(t, M.sourceEnd, M.fadeEnd))
            BrightLessonRecapColumn(
                minHeight: metrics.height - metrics.padTop - metrics.padBottom,
                gap: metrics.actionGap,
                flexible: metrics.flow,
                actionsMargin: metrics.flow ? 8 : 0
            ) {
                BrightLessonRecapScene(
                    t: t,
                    metrics: metrics,
                    reduced: reduceMotion,
                    replayEnabled: t > M.mascotStart && t < M.fadeEnd && interactive,
                    onReplay: replay
                )
                BrightLessonRecapActions(
                    metrics: metrics,
                    claimed: claimed,
                    reduced: reduceMotion,
                    enabled: actionsEnabled && interactive,
                    rewardStart: rewardStart,
                    onShare: share,
                    onClaim: claim,
                    onReplay: replay,
                    onPressChange: { $0 ? pause() : release() }
                )
                .opacity(actionsOpacity)
            }
            .onChange(of: timeline.date) { _, _ in
                // RN: without autoPlay the clock stops where the story ends.
                if !autoPlay && playing && t >= M.sourceEnd { playing = false }
            }
        }
        .padding(.top, metrics.padTop)
        .padding(.bottom, metrics.padBottom)
        .padding(.horizontal, metrics.contentInset)
    }

    // MARK: Actions

    /// RN `pause`: a touch stops the loop.
    private func pause() {
        guard interactive else { return }
        interacting = true
        paused = true
    }

    /// RN `release`: the finger is up; the two-second wait starts (see `.task(id: resumeKey)`).
    private func release() {
        guard interactive else { return }
        interacting = false
    }

    /// RN `replay`: back to the start (or the finished recap under Reduce Motion), with a light tap.
    private func replay() {
        guard interactive else { return }
        interacting = false
        claimed = false
        paused = false
        rewardStart = nil
        clock.reset(to: reduceMotion ? M.settled : 0)
        playing = !reduceMotion
        impact.impactOccurred()
    }

    /// RN `claim`: only while the key is live; freezes the finished recap and sends the points up.
    private func claim() {
        guard interactive, !claimed else { return }
        let t = displayTime(Date())
        guard t >= M.actionsStart, t < M.sourceEnd else { return }
        interacting = false
        claimed = true
        paused = true
        clock.reset(to: M.settled)
        onClaim?(45)
        if !reduceMotion { rewardStart = Date() }
        notification.notificationOccurred(.success)
        UIAccessibility.post(notification: .announcement, argument: "45 XP added to this demo session.")
    }

    /// RN `Share.share`.
    private func share() {
        BrightLessonRecapShare.present("Perfect lesson: 45 XP, 100% accuracy, 3:40.")
    }
}

// MARK: - Column

/// RN's scroll content: the scene, a gap, and the actions. In the compact layout the gap is fixed
/// (`actionGap`); with large text it is a flexible spacer of at least 28 pt that pushes the actions to
/// the bottom. Either way the column is at least `minHeight` tall (`flexGrow: 1, minHeight`).
struct BrightLessonRecapColumn: Layout {
    var minHeight: CGFloat
    var gap: CGFloat
    var flexible: Bool
    var actionsMargin: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let child = ProposedViewSize(width: proposal.width, height: nil)
        let scene = subviews[0].sizeThatFits(child)
        let actions = subviews[1].sizeThatFits(child)
        let space = spacer(scene: scene.height, actions: actions.height)
        let natural = scene.height + space + actionsMargin + actions.height
        return CGSize(width: proposal.width ?? max(scene.width, actions.width), height: max(minHeight, natural))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let child = ProposedViewSize(width: bounds.width, height: nil)
        let scene = subviews[0].sizeThatFits(child)
        let actions = subviews[1].sizeThatFits(child)
        let space = spacer(scene: scene.height, actions: actions.height)
        subviews[0].place(
            at: CGPoint(x: bounds.midX, y: bounds.minY),
            anchor: .top,
            proposal: ProposedViewSize(width: bounds.width, height: scene.height)
        )
        subviews[1].place(
            at: CGPoint(x: bounds.midX, y: bounds.minY + scene.height + space + actionsMargin),
            anchor: .top,
            proposal: ProposedViewSize(width: bounds.width, height: actions.height)
        )
    }

    private func spacer(scene: CGFloat, actions: CGFloat) -> CGFloat {
        guard flexible else { return gap }
        return max(gap, minHeight - scene - actionsMargin - actions)
    }
}
