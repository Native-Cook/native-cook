import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

// Penny, a budgeting app, on its first run: the home screen, the tour over it and the autoPlay script.
// Ported from the React Native block (its source is the reference).

private typealias M = CoachMarkSpotlightMotion

/// The autoPlay script: every value is a pure function of loop time.
enum CoachMarkSpotlightScript {
    /// A tap's press: in 100 ms before the finger lifts at `at`, out 120 ms after.
    static func pressAt(_ t: Double, _ at: Double) -> Double {
        let d = t - at
        if d <= -0.1 || d >= 0.12 { return 0 }
        return d < 0 ? 1 + d / 0.1 : 1 - d / 0.12
    }

    /// The ring's breathing in the holds: phase and how much shows (fades at each hold's edges).
    static func pulseAt(_ t: Double) -> (phase: Double, gate: Double) {
        for (start, end) in M.holds {
            let tt = end > M.loop && t < end - M.loop ? t + M.loop : t
            if tt >= start && tt <= end {
                let phase = ((tt - start) / M.pulsePeriod).truncatingRemainder(dividingBy: 1)
                let gate = M.seg(tt, start, 0.15) * (1 - M.seg(tt, end - 0.15, 0.15))
                return (phase, gate)
            }
        }
        return (0, 0)
    }

    static func pose(_ t: Double) -> CoachMarkSpotlightPose {
        let T = M.T.self
        let before = t < T.reset
        func morph(_ a: Double) -> Double { M.easeInOutCubic(M.seg(t, a, M.morph)) }
        var p = CoachMarkSpotlightPose()
        p.p = before ? morph(T.morph1) + morph(T.morph2) - morph(T.morph3) : 0
        // The tooltip rides the cutout's curve tipLag behind it, so it never arrives first.
        p.tipP = before ? morph(T.morph1 + M.tipLag) + morph(T.morph2 + M.tipLag) - morph(T.morph3 + M.tipLag) : 0

        var x = 0.0
        if t > T.dragStart && t < T.dragEnd {
            x = M.ghostDrag * M.easeInOutCubic((t - T.dragStart) / (T.dragEnd - T.dragStart))
        } else if t >= T.dragEnd {
            x = M.ghostDrag * (1 - M.springAt(t - T.dragEnd, M.dragSpring))
        }
        p.tipX = x

        let shown = t < T.intro
            ? 1 - M.easeInOutCubic(M.seg(t, T.fadeOut, T.fadeOutSpan))
            : M.easeOutCubic(M.seg(t, T.intro, 0.4))
        p.shown = shown
        // The spring's tail is forced to exactly 0 over the rest, so the loop's last frame is its first.
        p.iris = before ? 0 : (1 - M.springAt(t - T.intro, M.irisSpring)) * (1 - M.seg(t, T.rest, 0.4))
        p.tipShow = t < T.tipIn
            ? 1 - M.easeInOutCubic(M.seg(t, T.fadeOut, 0.25))
            : M.easeOutCubic(M.seg(t, T.tipIn, T.tipInSpan))
        p.visible = shown > 0.5

        let pulse = pulseAt(t)
        p.pulse = pulse.phase
        p.pulseGate = pulse.gate
        p.nextPress = pressAt(t, T.next1)
        p.targetPress = pressAt(t, T.tapCard)
        p.skipPress = pressAt(t, T.skip)
        return p
    }

    struct Point { let t: Double; let x: Double; let y: Double }

    static func ghostAt(_ path: [Point], _ t: Double) -> CGPoint {
        if t <= path[0].t { return CGPoint(x: path[0].x, y: path[0].y) }
        for i in 1..<path.count where t <= path[i].t {
            let u = M.easeInOutCubic((t - path[i - 1].t) / (path[i].t - path[i - 1].t))
            return CGPoint(x: M.mix(u, path[i - 1].x, path[i].x), y: M.mix(u, path[i - 1].y, path[i].y))
        }
        return CGPoint(x: path[path.count - 1].x, y: path[path.count - 1].y)
    }

    static func ghostOpacity(_ t: Double) -> Double {
        let T = M.T.self
        let a = M.seg(t, T.ghostIn, 0.18) * (1 - M.seg(t, T.next1 + 0.15, 0.2))
        let b = M.seg(t, T.tapCard - 0.4, 0.2) * (1 - M.seg(t, T.tapCard + 0.15, 0.2))
        let c = M.seg(t, T.dragStart - 0.4, 0.2) * (1 - M.seg(t, T.dragEnd + 0.08, 0.2))
        let d = M.seg(t, T.skip - 0.4, 0.2) * (1 - M.seg(t, T.skip + 0.15, 0.2))
        return max(a, b, c, d)
    }

    static func ghostPress(_ t: Double) -> Double {
        let T = M.T.self
        let drag = M.seg(t, T.dragStart - 0.08, 0.08) * (1 - M.seg(t, T.dragEnd, 0.1))
        return max(pressAt(t, T.next1), pressAt(t, T.tapCard), pressAt(t, T.skip), drag)
    }
}

/// Who drives the tour: the loop, a finger, or the return to rest between them.
@Observable
final class CoachMarkSpotlightDirector {
    enum Mode { case auto, manual, returning }

    var mode: Mode = .manual
    var clockStart = Date.now
    var poke = 0
    let motion: CoachMarkSpotlightTourMotion
    var playing = false
    private var fingers = 0
    private var resume: DispatchWorkItem?

    init(count: Int) {
        motion = CoachMarkSpotlightTourMotion(count: count)
    }

    func start(playing: Bool, reduced: Bool) {
        self.playing = playing
        motion.calm = reduced
        motion.pulsing = playing
        resume?.cancel()
        if playing {
            mode = .auto
            clockStart = .now
        } else {
            mode = .manual
            motion.load(CoachMarkSpotlightPose(), at: .now)
            motion.step = 0
        }
    }

    func loopTime(_ date: Date) -> Double {
        let t = date.timeIntervalSince(clockStart).truncatingRemainder(dividingBy: M.loop)
        return t < 0 ? t + M.loop : t
    }

    func touchBegan() {
        resume?.cancel()
        guard mode != .manual else { return }
        let now = Date.now
        if mode == .auto {
            motion.load(CoachMarkSpotlightScript.pose(loopTime(now)), at: now)
            motion.adopt(at: now)
        }
        mode = .manual
    }

    func fingersChanged(_ n: Int) {
        let was = fingers
        fingers = n
        if n > 0 && was == 0 { touchBegan() }
        if n == 0 { scheduleResume() }
    }

    func grab() { touchBegan() }

    private func scheduleResume() {
        resume?.cancel()
        guard playing else {
            DispatchQueue.main.asyncAfter(deadline: .now() + max(0, motion.busyUntil.timeIntervalSinceNow) + 0.05) { [weak self] in
                self?.poke += 1
            }
            return
        }
        let item = DispatchWorkItem { [weak self] in self?.returnToRest() }
        resume = item
        DispatchQueue.main.asyncAfter(deadline: .now() + M.resumeAfter, execute: item)
    }

    /// Two seconds after the last touch: the tour reopens on step 1, then the loop restarts at t = 0.
    private func returnToRest() {
        guard mode == .manual, fingers == 0 else { return }
        let now = Date.now
        motion.open(at: now)
        mode = .returning
        let wait = max(0.3, motion.busyUntil.timeIntervalSince(now))
        let item = DispatchWorkItem { [weak self] in
            guard let self, self.mode == .returning else { return }
            self.clockStart = .now
            self.mode = .auto
        }
        resume = item
        DispatchQueue.main.asyncAfter(deadline: .now() + wait, execute: item)
    }
}

struct CoachMarkSpotlight: View {
    static let loop = M.loop

    var autoPlay: Bool = true
    var steps: [CoachMarkSpotlightStep] = CoachMarkSpotlightData.steps

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var director: CoachMarkSpotlightDirector

    init(autoPlay: Bool = true, steps: [CoachMarkSpotlightStep] = CoachMarkSpotlightData.steps) {
        self.autoPlay = autoPlay
        self.steps = Array(steps.prefix(3))
        _director = State(initialValue: CoachMarkSpotlightDirector(count: min(steps.count, 3)))
    }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            screen(size: size, top: insets.top, bottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(CoachMarkSpotlightTouchProbe { director.fingersChanged($0) })
        .onAppear { director.start(playing: autoPlay && !reduceMotion, reduced: reduceMotion) }
        .onChange(of: reduceMotion) { _, r in director.start(playing: autoPlay && !r, reduced: r) }
    }

    /// The three things the tour explains, padded into cutouts: a circle, a wide card, a tab item.
    private func targets(_ layout: CoachMarkSpotlightLayout) -> [CoachMarkSpotlightBox] {
        let fab = layout.fab
        let card = layout.card
        let tab = layout.tab
        let d = fab.w + M.pad * 2
        return Array([
            CoachMarkSpotlightBox(x: fab.x - M.pad, y: fab.y - M.pad, w: d, h: d, r: d / 2),
            CoachMarkSpotlightBox(x: card.x - 6, y: card.y - 6, w: card.w + 12, h: card.h + 12, r: card.r + 6),
            CoachMarkSpotlightBox(x: tab.x, y: tab.y, w: tab.w, h: tab.h, r: tab.r),
        ].prefix(steps.count))
    }

    @ViewBuilder
    private func screen(size: CGSize, top: Double, bottom: Double) -> some View {
        let width = size.width
        let height = size.height
        let layout = CoachMarkSpotlightLayout(width: width, height: height, insetTop: top, insetBottom: bottom)
        let boxes = targets(layout)
        let d = director
        let _ = d.poke
        let paused = d.mode == .manual && !d.motion.pulsing && Date.now > d.motion.busyUntil

        ZStack(alignment: .topLeading) {
            CoachMarkSpotlightHome(layout: layout, height: height)

            TimelineView(.animation(paused: paused)) { context in
                let now = context.date
                let auto = d.mode == .auto
                let t = d.loopTime(now)
                let pose = auto ? CoachMarkSpotlightScript.pose(t) : d.motion.pose(at: now)
                ZStack(alignment: .topLeading) {
                    CoachMarkSpotlightMarks(steps: steps, targets: boxes, pose: pose, motion: d.motion,
                                            width: width, height: height, grab: { d.grab() })
                    if auto {
                        ghost(t: t, layout: layout, targets: boxes, width: width, height: height, pose: pose)
                    }
                }
                .frame(width: width, height: height, alignment: .topLeading)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipped()
    }

    // MARK: Ghost finger

    private func ghost(t: Double, layout: CoachMarkSpotlightLayout, targets: [CoachMarkSpotlightBox],
                       width: Double, height: Double, pose: CoachMarkSpotlightPose) -> some View {
        let S = CoachMarkSpotlightScript.self
        let T = M.T.self
        let tip = CoachMarkSpotlightTip.self
        let tipW = min(width - 32, M.tipMaxW)
        let places = tip.places(targets, width: width, height: height, tipW: tipW)
        func at(_ i: Int) -> CoachMarkSpotlightTipPlace { places[min(i, places.count - 1)] }
        func nextX(_ i: Int) -> Double { at(i).x + tipW - tip.pad - tip.nextW / 2 }
        func footY(_ i: Int) -> Double { at(i).y + M.tipH - tip.pad - tip.footH / 2 }
        func skipX(_ i: Int) -> Double { at(i).x + tipW - tip.pad - tip.nextW - 6 - tip.skipW / 2 }
        let card = targets[min(1, targets.count - 1)]
        let cardX = card.x + card.w * 0.62
        let cardY = card.y + card.h * 0.55
        let grabX = at(2).x + tipW * 0.36
        let grabY = at(2).y + 64
        let path: [CoachMarkSpotlightScript.Point] = [
            .init(t: T.ghostIn, x: nextX(0) + 24, y: footY(0) + 40),
            .init(t: T.next1 - 0.08, x: nextX(0), y: footY(0)),
            .init(t: T.next1 + 0.12, x: nextX(0), y: footY(0)),
            .init(t: T.next1 + 0.45, x: nextX(0) + 12, y: footY(0) + 30),
            .init(t: T.tapCard - 0.4, x: cardX + 20, y: cardY + 40),
            .init(t: T.tapCard - 0.08, x: cardX, y: cardY),
            .init(t: T.tapCard + 0.12, x: cardX, y: cardY),
            .init(t: T.tapCard + 0.45, x: cardX + 10, y: cardY + 30),
            .init(t: T.dragStart - 0.4, x: grabX + 20, y: grabY + 36),
            .init(t: T.dragStart - 0.04, x: grabX, y: grabY),
            .init(t: T.dragStart, x: grabX, y: grabY),
            .init(t: T.dragEnd, x: grabX + M.ghostDrag, y: grabY),
            .init(t: T.dragEnd + 0.3, x: grabX + M.ghostDrag + 12, y: grabY + 24),
            .init(t: T.skip - 0.4, x: skipX(1) + 20, y: footY(1) + 36),
            .init(t: T.skip - 0.08, x: skipX(1), y: footY(1)),
            .init(t: T.skip + 0.12, x: skipX(1), y: footY(1)),
            .init(t: T.skip + 0.45, x: skipX(1) + 10, y: footY(1) + 30),
        ]
        let inDrag = t > T.dragStart && t < T.dragEnd
        let p = S.ghostAt(path, t)
        let x = inDrag ? grabX + pose.tipX : p.x
        let size = 44.0
        return Circle()
            .fill(Color.rgba(236, 240, 236, 0.34))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.8), lineWidth: 1))
            .frame(width: size, height: size)
            .scaleEffect(1 - 0.16 * S.ghostPress(t))
            .opacity(S.ghostOpacity(t) * 0.9)
            .offset(x: x - size / 2, y: p.y - size / 2)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// Watches every finger on the window without taking part in any gesture, so a touch anywhere hands the
/// tour to the finger and the last finger lifting starts the 2 s countdown to autoPlay.
private struct CoachMarkSpotlightTouchProbe: UIViewRepresentable {
    let onChange: (Int) -> Void

    func makeUIView(context: Context) -> Host { Host(onChange: onChange) }
    func updateUIView(_ view: Host, context: Context) { view.probe.onChange = onChange }
    static func dismantleUIView(_ view: Host, coordinator: ()) { view.detach() }

    final class Probe: UIGestureRecognizer, UIGestureRecognizerDelegate {
        var onChange: (Int) -> Void
        private var touches = Set<UITouch>()

        init(onChange: @escaping (Int) -> Void) {
            self.onChange = onChange
            super.init(target: nil, action: nil)
            cancelsTouchesInView = false
            delaysTouchesBegan = false
            delaysTouchesEnded = false
            delegate = self
        }

        override func touchesBegan(_ t: Set<UITouch>, with event: UIEvent) {
            touches.formUnion(t)
            onChange(touches.count)
        }
        override func touchesEnded(_ t: Set<UITouch>, with event: UIEvent) { lift(t) }
        override func touchesCancelled(_ t: Set<UITouch>, with event: UIEvent) { lift(t) }
        private func lift(_ t: Set<UITouch>) {
            touches.subtract(t)
            onChange(touches.count)
            if touches.isEmpty { state = .failed }
        }
        override func reset() {
            super.reset()
            if !touches.isEmpty {
                touches.removeAll()
                onChange(0)
            }
        }
        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
        override func canPrevent(_ preventedGestureRecognizer: UIGestureRecognizer) -> Bool { false }
        override func canBePrevented(by preventingGestureRecognizer: UIGestureRecognizer) -> Bool { false }
    }

    final class Host: UIView {
        let probe: Probe
        init(onChange: @escaping (Int) -> Void) {
            probe = Probe(onChange: onChange)
            super.init(frame: .zero)
            isUserInteractionEnabled = false
        }
        required init?(coder: NSCoder) { fatalError() }
        override func didMoveToWindow() {
            super.didMoveToWindow()
            probe.view?.removeGestureRecognizer(probe)
            window?.addGestureRecognizer(probe)
        }
        func detach() { probe.view?.removeGestureRecognizer(probe) }
    }
}
