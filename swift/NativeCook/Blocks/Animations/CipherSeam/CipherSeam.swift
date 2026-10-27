import SwiftUI
import UIKit

/*
 Cipher Seam: the landing screen of an invented payments company, Cipher. A frosted nav capsule
 over a dark field where a lane of star dust runs behind a violet light seam; a sample card
 travels through the seam and comes out the other side as a dense token matrix. Below, a violet
 panel with the headline, copy and a Talk to an Expert button, a row of partner logos, a domain
 pill and a round chat button.

 autoPlay (loop = 10.8 s, kept from the reference through 9.8 s): the card rests off to the left
 until 2.75, then its left edge follows measured knots through 9.8; the card and the token matrix
 are one plane clipped at the seam. Idle, the seam is a thin lens of light; while the card
 crosses (5.0 to 8.1) it becomes a white band about 12 pt wide whose right side breaks into
 particles, and the token glyphs change twelve times a second. The previous pass's tokens are
 still leaving to the right as the loop starts (0 to 2.5), so the loop closes on itself.

 Touch: dragging across the hero scrubs the plane between 2.75 and 10.5 and a release springs to
 the nearer end (400 ms, damping ratio 0.8, with the finger's speed); Talk to an Expert (the
 white pill under the copy) replays the pass with a light haptic; the nav pill and the chat
 button open the explainer. A touch interrupts autoPlay; two seconds after the last one the loop
 starts again from 0. Reduce Motion: the card sits still across the seam, the glyphs and the
 dust hold still, and nothing loops.

 Drawing: the dust, the seam and the bowl are SwiftUI Canvases driven by one TimelineView whose
 time comes from CipherSeamPlayback; the page is a UIScrollView hosting SwiftUI so it can draw
 under the status bar like React Native's ScrollView; the top and bottom 28 pt of the scroll
 view blur what slides under them when the page overflows.
*/
struct CipherSeam: View {
    /// One pass of the loop, in seconds.
    static let loop: Double = 10.8

    /// Plays the loop by itself.
    var autoPlay = false
    /// Freezes the loop at this time (seconds; wrapped into one loop) and turns the touches that
    /// change it off. The lead uses it to inspect any frame.
    var previewTime: Double?
    /// Called when "Talk to an Expert" in the nav is pressed.
    var onLearnMore: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    @State private var playback: CipherSeamPlayback
    @State private var session = CipherSeamSession()
    /// `info`: the explainer is open.
    @State private var info = false
    /// `manual`: a touch has taken over from the loop.
    @State private var manual = false
    /// `interacting`: a finger or a replay is moving the card.
    @State private var interacting = false
    /// The page's content height, for the blurred edges.
    @State private var contentHeight: CGFloat = 0

    init(autoPlay: Bool = false, previewTime: Double? = nil, onLearnMore: (() -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onLearnMore = onLearnMore
        _playback = State(initialValue: CipherSeamPlayback(start: Self.normalized(previewTime) ?? 0))
        _ = CipherSeamFonts.registered
    }

    // MARK: - Body

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                stage(size: size, insets: insets)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(CipherSeamPalette.screen.ignoresSafeArea())
        // `<StatusBar style="light" />`.
        .preferredColorScheme(.dark)
        .onChange(of: syncKey, initial: true) { _, _ in syncPlayback() }
        .onChange(of: foreground) { _, active in
            if !active { interacting = false }
        }
        .task(id: idleKey) {
            // Two seconds after the last touch the loop takes over again.
            guard manual, !interacting, !info, foreground else { return }
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            manual = false
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let layout = CipherSeamLayout(
            size: size, top: insets.top, bottom: insets.bottom,
            fontScale: cipherSeamFontScale(dynamicTypeSize)
        )
        return ZStack(alignment: .topLeading) {
            CipherSeamPalette.screen
            CipherSeamScroll { page(layout) }
                .frame(width: size.width, height: size.height)
                .cipherSeamBlurViewport(enabled: true, contentHeight: contentHeight, viewportHeight: size.height)
                .accessibilityHidden(info)
            chatButton(layout)
                .accessibilityHidden(info)
            if info {
                CipherSeamModal(size: size, top: insets.top, bottom: insets.bottom, onClose: closeInfo)
                    .transition(.opacity)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        // `animationType="fade"`.
        .animation(.easeInOut(duration: 0.3), value: info)
    }

    private func page(_ layout: CipherSeamLayout) -> some View {
        CipherSeamPage(
            layout: layout,
            playback: playback,
            reduced: reduceMotion,
            timelinePaused: !running,
            touchEnabled: !reduceMotion && Self.normalized(previewTime) == nil,
            onTalk: talk,
            onReplay: replay,
            onDragStart: dragStarted,
            onDragMove: { dragMoved($0, heroWidth: layout.heroWidth) },
            onDragEnd: { dragEnded(velocity: $0, heroWidth: layout.heroWidth) },
            onDragFinish: dragFinalized,
            onContentHeight: { height in
                if abs(height - contentHeight) > 0.5 { contentHeight = height }
            }
        )
    }

    /// `styles.fab`: the round chat button, 6 pt from the right and 37 pt above the bottom inset.
    private func chatButton(_ layout: CipherSeamLayout) -> some View {
        Button(action: openInfo) {
            CipherSeamIcon(glyph: .chatbox, size: 22, color: .white)
                .frame(width: 44, height: 44)
                .background(Circle().fill(CipherSeamPalette.rgb(0x5B33F2)))
                .background {
                    // `0px 8px 20px -6px rgba(91,51,242,0.8)`: the disc shrunk by the 6 pt spread,
                    // 8 pt lower, blurred by 20 pt (a 10 pt Gaussian).
                    Circle()
                        .fill(CipherSeamPalette.rgb(0x5B33F2, alpha: 204))
                        .padding(6)
                        .blur(radius: 10)
                        .offset(y: 8)
                }
        }
        .buttonStyle(CipherSeamPressStyle(reduced: reduceMotion))
        .accessibilityLabel("Chat with us")
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
        .padding(.trailing, 6)
        .padding(.bottom, layout.bottom + 37)
    }

    // MARK: - State the effects read

    private var foreground: Bool {
        scenePhase == .active
    }

    /// Whether the hero's clock should be running.
    private var running: Bool {
        Self.normalized(previewTime) == nil && !reduceMotion && foreground && !info && (autoPlay || manual)
    }

    /// `normalizedPreview`: the preview time wrapped into one loop.
    private static func normalized(_ value: Double?) -> Double? {
        guard let value, value.isFinite else { return nil }
        let remainder = value.truncatingRemainder(dividingBy: loop)
        return remainder >= 0 ? remainder : remainder + loop
    }

    /// The inputs of the effect that starts and stops the clock.
    private struct SyncKey: Equatable {
        let autoPlay: Bool
        let foreground: Bool
        let info: Bool
        let manual: Bool
        let preview: Double?
        let reduced: Bool
    }

    private var syncKey: SyncKey {
        SyncKey(
            autoPlay: autoPlay, foreground: foreground, info: info, manual: manual,
            preview: Self.normalized(previewTime), reduced: reduceMotion
        )
    }

    /// The inputs of the two-second timer.
    private struct IdleKey: Equatable {
        let manual: Bool
        let interacting: Bool
        let info: Bool
        let foreground: Bool
    }

    private var idleKey: IdleKey {
        IdleKey(manual: manual, interacting: interacting, info: info, foreground: foreground)
    }

    // MARK: - The clock

    /// The first effect of the React Native file: preview time pins the clock; otherwise it
    /// stops while the app is away, the explainer is open, motion is reduced, or nothing plays;
    /// and `autoPlay` with no touch in charge restarts the loop from 0.
    private func syncPlayback() {
        let now = Date()
        if let preview = Self.normalized(previewTime) {
            cancelAnimations(at: now)
            playback.set(preview)
            return
        }
        if !foreground || info || reduceMotion || (!autoPlay && !manual) {
            cancelAnimations(at: now)
            return
        }
        if autoPlay && !manual {
            cancelAnimations(at: now)
            playback.startLoop(at: now)
        }
    }

    /// `cancelAnimation(time)`: the clock stays where it is, and a replay in flight ends (its
    /// callback releases the interaction that started it).
    private func cancelAnimations(at now: Date) {
        playback.freeze(at: now)
        session.replayTask?.cancel()
        session.replayTask = nil
        if let owner = session.replayOwner {
            session.replayOwner = nil
            release(owner)
        }
    }

    /// `begin`: a new interaction takes charge.
    @discardableResult
    private func begin() -> Int {
        session.epoch += 1
        manual = true
        interacting = true
        return session.epoch
    }

    /// `release`: the interaction `owner` (or any, when nil) lets go.
    private func release(_ owner: Int? = nil) {
        if owner == nil || owner == session.epoch { interacting = false }
    }

    // MARK: - Buttons

    private func talk() {
        begin()
        info = true
        onLearnMore?()
    }

    private func openInfo() {
        begin()
        info = true
    }

    private func closeInfo() {
        info = false
        release()
    }

    /// Replays the pass: 2.75 to 10.5 s in real time (under Reduce Motion it jumps to 6 and is
    /// done), with a light haptic.
    private func replay() {
        guard Self.normalized(previewTime) == nil else { return }
        let now = Date()
        let owner = begin()
        cancelAnimations(at: now)
        if reduceMotion {
            playback.set(6)
            release(owner)
        } else {
            playback.startTiming(from: 2.75, to: 10.5, duration: 7.75, at: now)
            session.replayOwner = owner
            session.replayTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(7.75))
                guard !Task.isCancelled else { return }
                session.replayOwner = nil
                session.replayTask = nil
                release(owner)
            }
        }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    // MARK: - The drag

    /// `onStart`: the finger took over; the clock stops where it is.
    private func dragStarted() {
        let now = Date()
        cancelAnimations(at: now)
        session.dragStart = CipherSeamMath.clamp(playback.time(at: now), 2.75, 10.5)
        session.dragOwner = begin()
    }

    /// `onUpdate`: the finger's travel over the hero's width is 7.75 s of the pass.
    private func dragMoved(_ translation: CGFloat, heroWidth: CGFloat) {
        guard session.dragOwner != nil else { return }
        let value = session.dragStart + Double(translation) / Double(heroWidth) * 7.75
        playback.set(CipherSeamMath.clamp(value, 2.75, 10.5))
    }

    /// `onEnd`: a flick over 110 pt/s picks the end it points to, a slow release the nearer end;
    /// the clock springs there with the finger's speed.
    private func dragEnded(velocity: CGFloat, heroWidth: CGFloat) {
        guard session.dragOwner != nil else { return }
        let now = Date()
        let current = playback.time(at: now)
        let speed = Double(velocity)
        let rightward = abs(speed) > 110 ? speed > 0 : current > 6.5
        let target = rightward ? 10.5 : 2.75
        playback.startSpring(from: current, target: target, velocity: speed / Double(heroWidth) * 7.75, at: now)
    }

    /// `onFinalize`: the finger is up.
    private func dragFinalized() {
        guard let owner = session.dragOwner else { return }
        session.dragOwner = nil
        release(owner)
    }
}

/// The interaction bookkeeping that does not draw anything (refs in the React Native file), so
/// changing it does not redraw the screen.
final class CipherSeamSession {
    /// `interactionEpoch`: counts interactions, so a late release of an old one is ignored.
    var epoch = 0
    /// `dragOwner`.
    var dragOwner: Int?
    /// `dragStart`: the clock when the finger took over, kept to within 2.75 and 10.5.
    var dragStart = 0.0
    /// The interaction a running replay belongs to, and the task that ends it.
    var replayOwner: Int?
    var replayTask: Task<Void, Never>?
}
