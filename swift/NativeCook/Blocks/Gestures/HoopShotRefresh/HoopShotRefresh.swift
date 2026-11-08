import SwiftUI

/// One drill card of the Courtside feed.
struct HoopShotRefreshDrill: Identifiable, Equatable {
    var id: Int
    var title: String
    var coach: String
    var focus: String
    var minutes: Int
    var tint: (UInt32, UInt32)

    static func == (a: Self, b: Self) -> Bool { a.id == b.id }

    /// Four cards: the whole feed fits above the tab bar at 393×852 pt, so no card is ever cut.
    static let samples = [
        Self(id: 0, title: "Crossover Ladder", coach: "Coach Dana Reyes", focus: "Ball handling", minutes: 12, tint: (0xFF7A9E, 0xE8457A)),
        Self(id: 1, title: "Corner Three Reps", coach: "Coach Malik Osei", focus: "Shooting", minutes: 18, tint: (0x6C6CFF, 0x3A3AD6)),
        Self(id: 2, title: "Mikan Drill", coach: "Coach Priya Nair", focus: "Finishing", minutes: 8, tint: (0xFFB347, 0xFF8A3D)),
        Self(id: 3, title: "Defensive Slides", coach: "Coach Tom Varga", focus: "Footwork", minutes: 10, tint: (0x3CC8A8, 0x1FA88A)),
    ]
}

private enum HoopShotRefreshPalette {
    static let bar = Color(hex: 0xE8457A)
    static let ball = Color(hex: 0xF0558A)
    static let ballLight = Color(hex: 0xFF8DB1)
    static let ballDark = Color(hex: 0xD63A6F)
    static let seam = Color(hex: 0xC83468)
    static let court = Color(hex: 0x2B2B30)
    static let courtEdge = Color(hex: 0x1F1F24)
    static let card = Color(hex: 0xFFFFFF)
    static let cardTint = Color(hex: 0xFCE4EC)
    static let ink = Color(hex: 0x1E1E22)
    static let mute = Color(hex: 0x7A7A82)
    static let label = Color(hex: 0x9A9AA2)
    static let tab = Color(hex: 0xF2F2F4)
    static let tabLine = Color(hex: 0xE2E2E6)
    static let tabIcon = Color(hex: 0xB0B0B6)
    static let board = Color(hex: 0xF4F4F6)
    static let boardSquare = Color(hex: 0xD6D6DC)
    static let bracket = Color(hex: 0xC9C9D0)
    static let net = Color.white.opacity(0.85)
}

enum HoopShotRefreshMetrics {
    static let barContent = 56.0
    static let tabContent = 56.0
    static let margin = 20.0
    static let cardHeight = 118.0
    static let cardGap = 14.0
    static let cardRadius = 18.0
    static let labelTop = 18.0
    static let firstCard = 44.0
    static let threshold = 150.0
    static let rubberLimit = 420.0
    static let hoopTop = 14.0
    static let boardWidth = 72.0
    static let boardHeight = 44.0
    static let rimGap = 8.0
    static let rimRx = 28.0
    static let rimRy = 7.0
    static let netLength = 40.0
    static let netStretch = 28.0
    static let chargeMin = 24.0
    static let chargeMax = 140.0
    static let flightRadius = 15.0
    static let dropRadius = 9.0
    static let dropDepth = 100.0
}

enum HoopShotRefreshMotion {
    static let loop = 8.0
    static let pullFrom = 1.0
    static let pullTo = 2.5
    static let releaseAt = 2.8
    /// The shot sequence, in seconds after the release.
    static let flightAt = 0.2
    static let flightFor = 0.8
    static let dropAt = 1.0
    static let dropFor = 0.6
    static let netAt = 1.1
    static let netFor = 0.45
    static let closeAt = 2.0
    static let closeFor = 0.6
    static let flashAt = 2.15
    static let flashFor = 0.35
    static let stagger = 0.06
    static let popFor = 0.6
    static let done = 3.1
    /// The "Updated just now" note stays this long after the feed has closed.
    static let noteFor = 1.5
    static let noteFade = 0.3
    static let reducedCloseAt = 0.1
    static let reducedDone = 0.9
    /// Seconds after the last touch at which the screen eases back to rest and the loop restarts.
    static let resume = 2.0
    /// The ease back to rest: the feed closes as after a shot, the cards still flash, the note shows briefly.
    static let settleFor = 1.0
    static let settleFade = 0.3
    /// A drag starts once the finger has travelled this far; before that a touch only holds the screen.
    static let dragSlop = 8.0

    static func clamp(_ t: Double) -> Double { min(1, max(0, t)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeOutQuad(_ x: Double) -> Double { 1 - (1 - x) * (1 - x) }
    static func easeInQuad(_ x: Double) -> Double { x * x }
    static func easeOutBack(_ x: Double) -> Double { 1 + 2.2 * pow(x - 1, 3) + 1.2 * pow(x - 1, 2) }
    /// Finger travel to feed offset: direct at first, giving less the further it goes.
    static func rubber(_ dy: Double) -> Double {
        let d = max(0, dy) * 0.6
        return d / (1 + d / HoopShotRefreshMetrics.rubberLimit)
    }
}

/// Everything one frame draws, derived from a time (the loop's or a touch's).
struct HoopShotRefreshFrame: Equatable {
    struct Ball: Equatable {
        var x: Double
        var y: Double
        var r: Double
        var opacity: Double
        var seam: Double
        /// In front of the feed (charging, flying) or behind it (falling away).
        var front: Bool
    }
    var pull = 0.0
    var ball: Ball? = nil
    var net = 0.0
    var flash = 0.0
    /// Seconds since the cards started their refresh flash; negative when they have not.
    var refresh = -1.0
    var hint = 0.0
    /// Opacity of the "Updated just now" note that replaces "Today" after a refresh.
    var note = 0.0

    static let rest = HoopShotRefreshFrame()
}

/// The pure functions behind the frames: the same arithmetic serves the loop and the finger.
enum HoopShotRefreshScene {
    private typealias S = HoopShotRefreshMetrics
    private typealias M = HoopShotRefreshMotion

    /// The ball as it charges with the pull, rising from behind the tab bar.
    static func charge(pull: Double, cx: Double, tabTop: Double) -> HoopShotRefreshFrame.Ball {
        let k = M.clamp(pull / S.threshold)
        let r = S.chargeMin + (S.chargeMax - S.chargeMin) * k
        return .init(x: cx, y: tabTop + 30 - r * 0.15, r: r, opacity: 1, seam: 0.3 + k * 0.4, front: true)
    }

    /// The loop's pull: out to the threshold with a small overshoot, then held until the release.
    static func loopPull(_ t: Double) -> Double {
        let u = M.seg(t, M.pullFrom, M.pullTo)
        if u < 0.77 { return 162 * M.easeInOutCubic(u / 0.77) }
        return 162 - 12 * M.easeOutQuad((u - 0.77) / 0.23)
    }

    /// The shot, `s` seconds after the release from a feed offset `p0`.
    static func sequence(_ s: Double, from p0: Double, cx: Double, cavityTop: Double, tabTop: Double, reduced: Bool) -> HoopShotRefreshFrame {
        var f = HoopShotRefreshFrame()
        if reduced {
            let close = M.easeInOutCubic(M.seg(s, M.reducedCloseAt, M.reducedCloseAt + 0.3))
            f.pull = p0 * (1 - close)
            f.refresh = s >= M.reducedCloseAt + 0.05 ? s - (M.reducedCloseAt + 0.05) : -1
            f.note = M.seg(s, M.reducedCloseAt, M.reducedCloseAt + M.noteFade) * (1 - M.seg(s, M.reducedDone + M.noteFor, M.reducedDone + M.noteFor + M.noteFade))
            return f
        }
        // The feed settles on the threshold and holds there while the ball is in the air.
        let held = S.threshold + (p0 - S.threshold) * exp(-s * 10)
        let close = M.easeInOutCubic(M.seg(s, M.closeAt, M.closeAt + M.closeFor))
        f.pull = held * (1 - close)
        f.hint = 1 - M.seg(s, 0, M.flightAt)

        let rimY = cavityTop + S.hoopTop + S.boardHeight + S.rimGap + S.rimRy
        let apexY = rimY - 14
        if s < M.flightAt {
            // Anticipation: a short dip before the launch.
            var b = charge(pull: S.threshold, cx: cx, tabTop: tabTop)
            b.y += 6 * sin(.pi * s / M.flightAt)
            f.ball = b
        } else if s < M.dropAt {
            let u = M.seg(s, M.flightAt, M.flightAt + M.flightFor)
            let start = charge(pull: S.threshold, cx: cx, tabTop: tabTop)
            let y = start.y + (apexY - start.y) * M.easeOutQuad(u)
            let r = start.r + (S.flightRadius - start.r) * M.easeOutQuad(u)
            f.ball = .init(x: cx + 26 * sin(.pi * u), y: y, r: r, opacity: 1, seam: 0.7 + u * 2.2, front: true)
        } else if s < M.dropAt + M.dropFor {
            let d = M.seg(s, M.dropAt, M.dropAt + M.dropFor)
            let y = apexY + S.dropDepth * M.easeInQuad(d)
            let r = S.flightRadius + (S.dropRadius - S.flightRadius) * d
            f.ball = .init(x: cx, y: y, r: r, opacity: 1 - M.seg(d, 0.7, 1), seam: 2.9 + d, front: false)
        }
        // The net stretches as the ball passes, then wobbles back.
        if s >= M.netAt && s < M.netAt + M.netFor {
            f.net = sin(.pi * M.seg(s, M.netAt, M.netAt + M.netFor))
        } else if s >= M.netAt + M.netFor {
            let e = s - (M.netAt + M.netFor)
            f.net = 0.12 * exp(-8 * e) * sin(2 * .pi * e * 5)
        }
        f.flash = M.seg(s, M.netAt, M.netAt + 0.3)
        f.refresh = s >= M.flashAt ? s - M.flashAt : -1
        f.note = M.seg(s, M.flashAt, M.flashAt + M.noteFade) * (1 - M.seg(s, M.done + M.noteFor, M.done + M.noteFor + M.noteFade))
        return f
    }

    static func loopFrame(_ t: Double, cx: Double, cavityTop: Double, tabTop: Double) -> HoopShotRefreshFrame {
        if t < M.pullFrom { return .rest }
        if t < M.releaseAt {
            var f = HoopShotRefreshFrame()
            f.pull = loopPull(t)
            f.ball = charge(pull: f.pull, cx: cx, tabTop: tabTop)
            f.hint = M.seg(f.pull, 110, 140)
            return f
        }
        let s = t - M.releaseAt
        // The same sequence as a hand shot, note included; rest again at 7.7 s.
        if s >= M.done + M.noteFor + M.noteFade { return .rest }
        return sequence(s, from: S.threshold, cx: cx, cavityTop: cavityTop, tabTop: tabTop, reduced: false)
    }

    /// The feed springing back from `p0` after a release short of the threshold: critically damped, at rest after about 0.6 s.
    static func cancelFrame(_ e: Double, from p0: Double, cx: Double, tabTop: Double, reduced: Bool) -> HoopShotRefreshFrame {
        var f = HoopShotRefreshFrame()
        let k = 9.0
        f.pull = p0 * (1 + k * e) * exp(-k * e)
        if f.pull < 0.5 { return .rest }
        if !reduced { f.ball = charge(pull: f.pull, cx: cx, tabTop: tabTop) }
        f.hint = M.seg(f.pull, 110, 140)
        return f
    }

    /// Whatever the screen showed at the resume deadline (`f0`), `e` seconds later, on its way back to rest.
    /// The feed closes as after a shot; the ball, net, hint and note fade. A shot that had not refreshed
    /// the feed yet (`refreshing`) still does: the cards flash as the feed closes and the note shows briefly.
    static func settleFrame(_ e: Double, from f0: HoopShotRefreshFrame, refreshing: Bool) -> HoopShotRefreshFrame {
        var f = HoopShotRefreshFrame()
        let fade = 1 - M.seg(e, 0, M.settleFade)
        f.pull = f0.pull * (1 - M.easeInOutCubic(M.seg(e, 0, M.closeFor)))
        if f.pull < 0.5 { f.pull = 0 }
        if var ball = f0.ball, fade > 0 {
            ball.opacity *= fade
            f.ball = ball
        }
        f.net = f0.net * fade
        f.hint = f0.hint * fade
        if f0.refresh >= 0 {
            f.refresh = f0.refresh + e
            f.note = f0.note * fade
        } else if refreshing {
            let flashAt = M.flashAt - M.closeAt
            f.refresh = e >= flashAt ? e - flashAt : -1
            f.note = M.seg(e, flashAt, flashAt + M.noteFade) * (1 - M.seg(e, M.settleFor - M.noteFade, M.settleFor))
        } else {
            f.note = f0.note * fade
        }
        return f
    }
}

/// A basketball feed whose pull-to-refresh charges a ball and, on release, shoots it through the hoop.
struct HoopShotRefresh: View {
    var drills: [HoopShotRefreshDrill]
    var autoPlay: Bool
    var title: String
    var sectionLabel: String
    var onRefresh: (() -> Void)?
    static let loop = HoopShotRefreshMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var mode = Mode.loop
    @State private var origin = Date.now
    /// A finger is on the screen: the loop never restarts while it is.
    @State private var touchDown = false
    /// The finger has travelled past the slop and owns the pull.
    @State private var dragging = false
    @State private var base = 0.0
    @State private var hapticFired = false
    /// Counts touch ends (lifts and cancellations); each one restarts the resume deadline.
    @State private var touchEnds = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    private typealias S = HoopShotRefreshMetrics
    private typealias M = HoopShotRefreshMotion
    private typealias P = HoopShotRefreshPalette

    init(drills: [HoopShotRefreshDrill] = HoopShotRefreshDrill.samples, autoPlay: Bool = false,
         title: String = "Courtside", sectionLabel: String = "Latest drills", onRefresh: (() -> Void)? = nil) {
        self.drills = drills; self.autoPlay = autoPlay; self.title = title; self.sectionLabel = sectionLabel; self.onRefresh = onRefresh
    }

    private enum Mode: Equatable {
        case loop
        case pulling(Double)
        case cancel(Double, Date)
        case shot(Double, Date)
        /// Easing what the screen showed at the resume deadline back to rest, before the loop restarts.
        case settle(HoopShotRefreshFrame, Bool, Date)
    }

    private var paused: Bool {
        switch mode {
        case .loop: return !autoPlay || reduceMotion
        case .pulling: return true
        default: return false
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let barHeight = insets.top + S.barContent
            let tabTop = height - insets.bottom - S.tabContent
            let cx = width / 2
            TimelineView(.animation(paused: paused)) { timeline in
                let frame = frame(at: timeline.date, cx: cx, cavityTop: barHeight, tabTop: tabTop)
                ZStack(alignment: .topLeading) {
                    court(frame: frame, width: width, height: height, cx: cx, cavityTop: barHeight)
                    feed(frame: frame, width: width, top: barHeight)
                        .frame(width: width, height: height, alignment: .topLeading)
                        .clipped()
                    // The feed steps back while the court is open, so the shot owns the screen.
                    P.courtEdge.opacity(0.35 * M.seg(frame.pull, 0, S.threshold))
                        .frame(width: width, height: max(0, tabTop - barHeight))
                        .offset(y: barHeight)
                    if let ball = frame.ball, ball.front {
                        Canvas { context, _ in Self.drawBall(ball, in: context) }
                            .frame(width: width, height: height)
                            .allowsHitTesting(false)
                    }
                    tabBar(width: width, top: tabTop, bottom: insets.bottom)
                    topBar(width: width, height: barHeight, top: insets.top)
                }
                .frame(width: width, height: height, alignment: .topLeading)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            // UIKit touches, not a DragGesture: a tap or a hold must pause the loop too, and a cancelled
            // touch must be told apart from a lift, which only `touchesCancelled` does reliably.
            .overlay(HoopShotRefreshTouchSurface { phase, dy in
                touch(phase, dy: dy, cx: cx, cavityTop: barHeight, tabTop: tabTop)
            })
            .offset(x: -insets.leading, y: -insets.top)
            .task(id: touchEnds) { await resume(cx: cx, cavityTop: barHeight, tabTop: tabTop) }
        }
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .accessibilityElement(children: .contain)
        .accessibilityAction(named: "Refresh") { refreshByHand() }
    }

    // MARK: Frames

    private func frame(at date: Date, cx: Double, cavityTop: Double, tabTop: Double) -> HoopShotRefreshFrame {
        switch mode {
        case .loop:
            guard autoPlay, !reduceMotion else { return .rest }
            let t = max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop)
            return HoopShotRefreshScene.loopFrame(t, cx: cx, cavityTop: cavityTop, tabTop: tabTop)
        case .pulling(let p):
            var f = HoopShotRefreshFrame()
            f.pull = p
            if !reduceMotion { f.ball = HoopShotRefreshScene.charge(pull: p, cx: cx, tabTop: tabTop) }
            f.hint = M.seg(p, 110, 140)
            return f
        case .cancel(let p0, let at):
            return HoopShotRefreshScene.cancelFrame(date.timeIntervalSince(at), from: p0, cx: cx, tabTop: tabTop, reduced: reduceMotion)
        case .shot(let p0, let at):
            let s = date.timeIntervalSince(at)
            if s >= (reduceMotion ? M.reducedDone : M.done) + M.noteFor + M.noteFade { return .rest }
            return HoopShotRefreshScene.sequence(s, from: p0, cx: cx, cavityTop: cavityTop, tabTop: tabTop, reduced: reduceMotion)
        case .settle(let f0, let refreshing, let at):
            let e = date.timeIntervalSince(at)
            if e >= M.settleFor { return .rest }
            return HoopShotRefreshScene.settleFrame(e, from: f0, refreshing: refreshing)
        }
    }

    private func loopTime(at date: Date) -> Double {
        max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop)
    }

    // MARK: Touch

    /// Every touch, from the finger landing to its lift or cancellation; `dy` is its travel in points.
    private func touch(_ phase: HoopShotRefreshTouchView.Phase, dy: Double, cx: Double, cavityTop: Double, tabTop: Double) {
        let now = Date.now
        switch phase {
        case .began:
            touchDown = true
            dragging = false
            hapticFired = false
            // The finger takes the screen over where it is, so nothing jumps: a pull in progress is held,
            // a shot in the air keeps flying, and a settle hands its offset to the finger.
            switch mode {
            case .loop:
                let t = loopTime(at: now)
                if !autoPlay || reduceMotion || t < M.releaseAt {
                    mode = .pulling(autoPlay && !reduceMotion ? HoopShotRefreshScene.loopPull(t) : 0)
                } else {
                    mode = .shot(S.threshold, now.addingTimeInterval(-(t - M.releaseAt)))
                }
            case .settle:
                mode = .pulling(frame(at: now, cx: cx, cavityTop: cavityTop, tabTop: tabTop).pull)
            default:
                break
            }
        case .moved:
            if !dragging {
                guard abs(dy) >= M.dragSlop else { return }
                dragging = true
                base = frame(at: now, cx: cx, cavityTop: cavityTop, tabTop: tabTop).pull
            }
            let p = base + M.rubber(dy)
            mode = .pulling(p)
            if p >= S.threshold && !hapticFired {
                hapticFired = true
                haptic.impactOccurred()
            }
        case .ended, .cancelled:
            touchDown = false
            if case .pulling(let p) = mode {
                // Only a lift past the threshold refreshes; a cancelled touch springs back without a shot.
                if phase == .ended && dragging && p >= S.threshold {
                    mode = .shot(p, now)
                    onRefresh?()
                } else {
                    mode = .cancel(p, now)
                }
            }
            dragging = false
            touchEnds += 1
        }
    }

    private func refreshByHand() {
        dragging = false
        mode = .shot(S.threshold, .now)
        onRefresh?()
        touchEnds += 1
    }

    /// Two seconds after the last touch the screen eases back to rest (at once if it is there already),
    /// and the loop starts again from rest. A finger landing in the meantime holds everything.
    private func resume(cx: Double, cavityTop: Double, tabTop: Double) async {
        guard touchEnds > 0 else { origin = .now; return }
        try? await Task.sleep(for: .seconds(M.resume))
        guard !Task.isCancelled, !touchDown else { return }
        let now = Date.now
        let f0 = frame(at: now, cx: cx, cavityTop: cavityTop, tabTop: tabTop)
        if f0 == .rest {
            origin = now
            mode = .loop
            return
        }
        var refreshing = false
        if case .shot = mode { refreshing = true }
        mode = .settle(f0, refreshing, now)
        try? await Task.sleep(for: .seconds(M.settleFor))
        guard !Task.isCancelled, !touchDown else { return }
        origin = .now
        mode = .loop
    }

    // MARK: Layers

    private func court(frame: HoopShotRefreshFrame, width: Double, height: Double, cx: Double, cavityTop: Double) -> some View {
        Canvas { context, _ in
            let courtRect = CGRect(x: 0, y: 0, width: width, height: height)
            context.fill(Path(courtRect), with: .color(P.court))
            context.fill(Path(courtRect), with: .radialGradient(
                Gradient(stops: [.init(color: P.court.opacity(0), location: 0.45), .init(color: P.courtEdge, location: 1)]),
                center: CGPoint(x: cx, y: cavityTop + 90), startRadius: 0, endRadius: width * 0.9))

            let boardTop = cavityTop + S.hoopTop
            let rimY = boardTop + S.boardHeight + S.rimGap + S.rimRy
            // The hoop lives in the cavity the pull opens: nothing of it shows while the feed rests.
            guard frame.pull > 0.5 else { return }
            var context = context
            context.clip(to: Path(CGRect(x: 0, y: cavityTop, width: width, height: frame.pull)))
            // Backboard, square and bracket.
            context.fill(Path(roundedRect: CGRect(x: cx - S.boardWidth / 2, y: boardTop, width: S.boardWidth, height: S.boardHeight), cornerRadius: 6), with: .color(P.board))
            context.stroke(Path(roundedRect: CGRect(x: cx - 15, y: boardTop + 14, width: 30, height: 22), cornerRadius: 2), with: .color(P.boardSquare), lineWidth: 2)
            context.fill(Path(CGRect(x: cx - 5, y: boardTop + S.boardHeight - 2, width: 10, height: S.rimGap + 2)), with: .color(P.bracket))
            // Back half of the rim.
            context.stroke(Self.rimArc(cx: cx, y: rimY, from: 180, to: 360), with: .color(P.ballDark), lineWidth: 4)
            if frame.flash > 0 && frame.flash < 1 {
                let k = 1 + 0.6 * M.easeOutQuad(frame.flash)
                var ring = context
                ring.translateBy(x: cx, y: rimY); ring.scaleBy(x: k, y: k); ring.translateBy(x: -cx, y: -rimY)
                ring.opacity = 0.6 * (1 - frame.flash)
                ring.stroke(Self.rimArc(cx: cx, y: rimY, from: 0, to: 360), with: .color(.white), lineWidth: 1.5)
            }
            if let ball = frame.ball, !ball.front { Self.drawBall(ball, in: context) }
            // Front half of the rim and the net.
            context.stroke(Self.rimArc(cx: cx, y: rimY, from: 0, to: 180), with: .color(P.bar), lineWidth: 4)
            Self.drawNet(in: context, cx: cx, rimY: rimY, stretch: frame.net)
            if frame.hint > 0 {
                let ready = frame.pull >= S.threshold
                let text = Text(ready ? "Release to refresh" : "Pull to shoot")
                    .font(.system(size: 12, weight: .medium)).foregroundStyle(.white.opacity(0.55 * frame.hint))
                context.draw(text, at: CGPoint(x: cx, y: rimY + S.netLength + 22), anchor: .center)
            }
        }
        .frame(width: width, height: height)
    }

    private static func rimArc(cx: Double, y: Double, from: Double, to: Double) -> Path {
        var path = Path()
        path.addArc(center: .zero, radius: 1, startAngle: .degrees(from), endAngle: .degrees(to), clockwise: false)
        return path.applying(CGAffineTransform(translationX: cx, y: y).scaledBy(x: S.rimRx, y: S.rimRy))
    }

    private static func drawNet(in context: GraphicsContext, cx: Double, rimY: Double, stretch: Double) {
        let s = stretch
        let bottomY = rimY + S.netLength + S.netStretch * s
        let bottomRx = 15 + 5 * s
        let bottomRy = 4.0
        var strands = Path()
        for i in 0..<7 {
            let a = Double.pi * Double(i) / 6
            let top = CGPoint(x: cx + S.rimRx * cos(a), y: rimY + S.rimRy * sin(a))
            let bottom = CGPoint(x: cx + bottomRx * cos(a), y: bottomY + bottomRy * sin(a))
            let mid = CGPoint(x: (top.x + bottom.x) / 2, y: (top.y + bottom.y) / 2)
            let inward = (4 * (1 - s) - 6 * s) * cos(a)
            strands.move(to: top)
            strands.addQuadCurve(to: bottom, control: CGPoint(x: mid.x - inward, y: mid.y))
        }
        context.stroke(strands, with: .color(P.net), lineWidth: 1.5)
        for f in [0.38, 0.72, 1.0] {
            let rx = S.rimRx + (bottomRx - S.rimRx) * f
            let ry = S.rimRy + (bottomRy - S.rimRy) * f
            let y = rimY + (bottomY - rimY) * f
            context.stroke(Path(ellipseIn: CGRect(x: cx - rx, y: y - ry, width: rx * 2, height: ry * 2)), with: .color(P.net), lineWidth: f == 1 ? 1.5 : 1.2)
        }
    }

    private static func drawBall(_ ball: HoopShotRefreshFrame.Ball, in context: GraphicsContext) {
        guard ball.opacity > 0 else { return }
        var c = context
        c.opacity = ball.opacity
        let r = ball.r
        let rect = CGRect(x: ball.x - r, y: ball.y - r, width: r * 2, height: r * 2)
        c.fill(Path(ellipseIn: rect), with: .radialGradient(
            Gradient(stops: [.init(color: P.ballLight, location: 0), .init(color: P.ball, location: 0.5), .init(color: P.ballDark, location: 1)]),
            center: CGPoint(x: ball.x - 0.35 * r, y: ball.y - 0.35 * r), startRadius: 0, endRadius: 1.5 * r))
        let w = min(3, max(1.2, r / 45))
        var seams = c
        seams.clip(to: Path(ellipseIn: rect.insetBy(dx: 0.5, dy: 0.5)))
        seams.translateBy(x: ball.x, y: ball.y)
        seams.rotate(by: .radians(ball.seam))
        seams.stroke(Path(ellipseIn: CGRect(x: -0.42 * r, y: -r, width: 0.84 * r, height: 2 * r)), with: .color(P.seam.opacity(0.8)), lineWidth: w)
        seams.stroke(Path(ellipseIn: CGRect(x: -r, y: -0.42 * r, width: 2 * r, height: 0.84 * r)), with: .color(P.seam.opacity(0.8)), lineWidth: w)
        c.stroke(Path(ellipseIn: rect.insetBy(dx: w / 2, dy: w / 2)), with: .color(P.ballDark.opacity(0.5)), lineWidth: w)
    }

    private func feed(frame: HoopShotRefreshFrame, width: Double, top: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(sectionLabel.uppercased()).font(.system(size: 12, weight: .bold)).tracking(1.2).foregroundStyle(P.label)
                Spacer()
                ZStack(alignment: .trailing) {
                    Text("Today").font(.system(size: 12, weight: .medium)).foregroundStyle(P.label).opacity(1 - frame.note)
                    Text("Updated just now").font(.system(size: 12, weight: .semibold)).foregroundStyle(P.bar).opacity(frame.note)
                }
            }
            .frame(height: 14)
            .padding(.top, S.labelTop)
            .padding(.horizontal, S.margin + 2)
            Color.clear.frame(height: S.firstCard - S.labelTop - 14)
            ForEach(Array(drills.enumerated()), id: \.element.id) { index, drill in
                card(drill, index: index, refresh: frame.refresh)
                    .padding(.horizontal, S.margin)
                    .padding(.bottom, S.cardGap)
            }
        }
        .frame(width: width, alignment: .topLeading)
        .offset(y: top + frame.pull)
    }

    private func card(_ drill: HoopShotRefreshDrill, index: Int, refresh: Double) -> some View {
        let at = refresh - Double(index) * M.stagger
        let flash = reduceMotion ? 0 : (at >= 0 ? sin(.pi * M.clamp(at / M.flashFor)) : 0)
        let pop = reduceMotion || at < 0 ? 1 : 0.94 + 0.06 * M.easeOutBack(M.clamp(at / M.popFor))
        let fade = reduceMotion && at >= 0 ? 0.55 + 0.45 * M.clamp(at / 0.3) : 1
        return HStack(alignment: .top, spacing: 16) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(LinearGradient(colors: [Color(hex: drill.tint.0), Color(hex: drill.tint.1)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 52, height: 52)
                .overlay(Image(systemName: "basketball.fill").font(.system(size: 20)).foregroundStyle(.white.opacity(0.9)))
            VStack(alignment: .leading, spacing: 4) {
                Text(drill.title).font(.system(size: 17, weight: .semibold)).foregroundStyle(P.ink).lineLimit(1)
                Text(drill.coach).font(.system(size: 13)).foregroundStyle(P.mute).lineLimit(1)
                Spacer(minLength: 0)
                HStack(spacing: 8) {
                    Text("\(drill.minutes) min").font(.system(size: 11, weight: .bold)).foregroundStyle(P.bar)
                        .padding(.horizontal, 9).frame(height: 22)
                        .background(Capsule().fill(P.cardTint))
                    Text(drill.focus).font(.system(size: 12)).foregroundStyle(P.mute).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(18)
        .frame(height: S.cardHeight)
        .scaleEffect(pop)
        .opacity(fade)
        .background(
            RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous)
                .fill(P.card)
                .overlay(RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous).fill(P.cardTint.opacity(flash * 0.8)))
                .shadow(color: .black.opacity(0.18), radius: 7, y: 6)
        )
    }

    private func topBar(width: Double, height: Double, top: Double) -> some View {
        ZStack(alignment: .topLeading) {
            P.bar
            HStack(spacing: 10) {
                ZStack {
                    Circle().fill(Color(hex: 0x2A2A2E))
                    Circle().fill(Color(hex: 0xF7D7E2)).frame(width: 12, height: 12)
                }
                .frame(width: 28, height: 28)
                Spacer()
                ForEach(["bell.fill", "magnifyingglass"], id: \.self) { name in
                    Circle().fill(.white.opacity(0.18)).frame(width: 28, height: 28)
                        .overlay(Image(systemName: name).font(.system(size: 13, weight: .semibold)).foregroundStyle(.white))
                }
            }
            .padding(.horizontal, S.margin)
            .frame(width: width, height: S.barContent)
            .offset(y: top)
            Text(title).font(.system(size: 22, weight: .semibold, design: .rounded)).tracking(-0.3).foregroundStyle(.white)
                .frame(width: width, height: S.barContent)
                .offset(y: top)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func tabBar(width: Double, top: Double, bottom: Double) -> some View {
        let items: [(String, String, Bool)] = [("house.fill", "Home", true), ("figure.basketball", "Drills", false), ("chart.bar.fill", "Stats", false), ("person.fill", "Me", false)]
        return VStack(spacing: 0) {
            P.tabLine.frame(height: 1)
            HStack(spacing: 0) {
                ForEach(items, id: \.1) { icon, label, active in
                    VStack(spacing: 4) {
                        Image(systemName: icon).font(.system(size: 20, weight: .semibold)).frame(height: 24)
                        Text(label).font(.system(size: 10, weight: .semibold))
                    }
                    .foregroundStyle(active ? P.bar : P.tabIcon)
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: S.tabContent - 1)
            Color.clear.frame(height: bottom)
        }
        .frame(width: width)
        .background(P.tab)
        .offset(y: top)
        .allowsHitTesting(false)
    }
}

/// A transparent view that reports the first finger's landing, travel, lift and cancellation.
private struct HoopShotRefreshTouchSurface: UIViewRepresentable {
    var onTouch: (HoopShotRefreshTouchView.Phase, Double) -> Void

    func makeUIView(context: Context) -> HoopShotRefreshTouchView {
        let view = HoopShotRefreshTouchView()
        view.backgroundColor = .clear
        view.onTouch = onTouch
        return view
    }

    func updateUIView(_ uiView: HoopShotRefreshTouchView, context: Context) { uiView.onTouch = onTouch }
}

final class HoopShotRefreshTouchView: UIView {
    enum Phase { case began, moved, ended, cancelled }
    var onTouch: ((Phase, Double) -> Void)?
    private var tracked: UITouch?
    private var startY = 0.0

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard tracked == nil, let touch = touches.first else { return }
        tracked = touch
        startY = touch.location(in: self).y
        onTouch?(.began, 0)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = tracked, touches.contains(touch) else { return }
        onTouch?(.moved, touch.location(in: self).y - startY)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = tracked, touches.contains(touch) else { return }
        tracked = nil
        onTouch?(.ended, touch.location(in: self).y - startY)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = tracked, touches.contains(touch) else { return }
        tracked = nil
        onTouch?(.cancelled, touch.location(in: self).y - startY)
    }
}

#Preview { HoopShotRefresh(autoPlay: true) }
