import SwiftUI

/// A feature tour. Three coins ride the rings round a coral disc; a tap on one pushes the camera
/// through the rings while the coin flies to the top of the screen and the disc opens into that
/// product's card. Swipes go from scene to scene, each on its own ground, and a swipe down or a
/// tap on the coin pulls back out to the orbit. In the orbit a drag spins the rings.
struct OrbitCoinTour: View {
    /// Under the orbit.
    var caption = "Your financial\nwellness account"
    /// The title and the line under it of the three scenes: savings, card, insights.
    var scenes = OrbitCoinTourSceneText.samples
    /// The balance on the savings and the insights card.
    var balance = 23520.0
    /// Written before an amount.
    var symbol = "$"
    /// Plays the orbit and the three scenes in turn, every `OrbitCoinTourMotion.loop` seconds.
    var autoPlay = false
    /// Called with 0 for the orbit and 1 to 3 for a scene when it comes to the front.
    var onScene: ((Int) -> Void)? = nil
    /// Called when the savings card's button is pressed.
    var onAction: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 0 is the orbit, 1 to 3 the scene in front.
    @State private var scene = 0
    /// How far the push from the orbit into the tour has gone, at a steady pace.
    @State private var tour = Tween(0)
    /// The scene in front, 1 to 3, between two of them while they slide.
    @State private var page = Tween(1)
    /// The scene the disc opens into, and shrinks back from.
    @State private var hero = 1
    /// The orbit's clock counts from here; it starts again at every pull back.
    @State private var phaseSince = Date().addingTimeInterval(-M.phaseAtStart)
    /// What the hand added to the rings, in radians.
    @State private var spin = Tween(0)
    /// When each scene's contents start to come in.
    @State private var revealAt: [Date] = Array(repeating: .distantFuture, count: 3)
    /// The drag in progress, and where it took hold.
    @State private var mode: Mode? = nil
    @State private var grab = 0.0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Changes whenever something starts to move, and `settled` says the screen has come to rest in a scene.
    @State private var activity = 0
    @State private var settled = false
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private enum Mode {
        case idle, spin, pages, pull
    }

    var body: some View {
        GeometryReader { proxy in
            // The reader is laid out inside the safe area; the screen itself is that much larger.
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let scale = min(size.width / S.width, size.height / S.height)
            let origin = CGPoint(x: (size.width - S.width * scale) / 2, y: (size.height - S.height * scale) / 2)

            TimelineView(.animation(paused: settled)) { timeline in
                let now = timeline.date
                let pushed = tour.value(at: now)
                let at = page.value(at: now)
                ZStack {
                    Color(hex: 0xEDE4D9)
                    ZStack {
                        OrbitCoinTourOrbit(
                            tour: reduceMotion ? 0 : pushed, scene: hero,
                            phase: reduceMotion ? M.phaseAtStart : now.timeIntervalSince(phaseSince),
                            spin: spin.value(at: now), scale: scale
                        )
                        Text(caption)
                            .font(.system(size: 34, weight: .light))
                            .multilineTextAlignment(.center)
                            .lineSpacing(2)
                            .foregroundStyle(Color(hex: 0x37342E))
                            .frame(width: S.width, height: 86)
                            .position(x: S.width / 2, y: 726)
                            .frame(width: S.width, height: S.height)
                            .scaleEffect(scale)
                            .opacity(1 - M.smooth(pushed, 0, 0.3))
                    }
                    // The scenes take over at the end of the push, when the disc has become the card's top.
                    .opacity(reduceMotion ? 1 - pushed : 1 - M.smooth(pushed, 0.85, 1))

                    ZStack {
                        ForEach(1...3, id: \.self) { index in
                            let away = Double(index) - at
                            let reveal = reduceMotion ? 1 : M.clamp01(now.timeIntervalSince(revealAt[index - 1]) / M.reveal)
                            ZStack {
                                S.color(S.grounds[index - 1])
                                ZStack(alignment: .topLeading) {
                                    content(index, reveal: reveal)
                                    // The coin arrives turning: edge-on half-way, face-on when the scene is in front.
                                    OrbitCoinTourCoin(icon: S.coins[index - 1].icon, flip: reduceMotion ? 1 : cos(.pi * min(1, max(-1, away))))
                                        .frame(width: Metrics.coinBox, height: Metrics.coinBox)
                                        .offset(x: S.cx - Metrics.coinBox / 2, y: S.coinTop[index - 1] - Metrics.coinBox / 2)
                                        .allowsHitTesting(false)
                                }
                                .frame(width: S.width, height: S.height, alignment: .topLeading)
                                .scaleEffect(scale)
                            }
                            // Reduced motion: scenes cross-fade in place.
                            .offset(x: reduceMotion ? 0 : away * size.width)
                            .opacity(reduceMotion ? 1 - min(1, abs(away)) : 1)
                        }
                    }
                    .clipped()
                    .opacity(reduceMotion ? pushed : M.smooth(pushed, 0.8, 0.95))
                    .allowsHitTesting(scene != 0)
                }
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .gesture(drag(size: size, scale: scale, origin: origin))
            .ignoresSafeArea()
        }
        .preferredColorScheme(scene == 2 ? .dark : .light)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(scene == 0 ? caption.replacingOccurrences(of: "\n", with: " ") : scenes[scene - 1].title)
        .accessibilityAdjustableAction { direction in
            let to = scene + (direction == .increment ? 1 : -1)
            if scene == 0, to == 1 { pick(1) } else if to == 0 { leave() } else if (1...3).contains(to) { settle(to) }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches, holding: mode != nil)) { await loop() }
        .task(id: activity) {
            // Once a scene has come to rest nothing moves, and the frames stop.
            settled = false
            try? await Task.sleep(for: .seconds(M.push + M.reveal + 0.5))
            if !Task.isCancelled { settled = scene != 0 || reduceMotion }
        }
    }

    @ViewBuilder
    private func content(_ index: Int, reveal: Double) -> some View {
        switch index {
        case 1: OrbitCoinTourSavings(reveal: reveal, text: scenes[0], balance: balance, symbol: symbol, onAction: onAction)
        case 2: OrbitCoinTourPayCard(reveal: reveal, text: scenes[1])
        default: OrbitCoinTourInsights(reveal: reveal, text: scenes[2], balance: balance, symbol: symbol)
        }
    }

    // MARK: Touch

    private func drag(size: CGSize, scale: Double, origin: CGPoint) -> some Gesture {
        /// The finger on the stage, from the orbit's centre.
        func arm(_ at: CGPoint) -> CGPoint {
            CGPoint(x: (at.x - origin.x) / scale - S.cx, y: (at.y - origin.y) / scale - S.cy)
        }
        return DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date()
                if mode == nil {
                    guard hypot(value.translation.width, value.translation.height) > Metrics.slop else { return }
                    let pushed = tour.value(at: now)
                    if pushed == 0 {
                        mode = .spin
                        spin.retarget(spin.value(at: now), at: now, duration: 0, curve: .linear)
                        let from = arm(value.location)
                        grab = atan2(from.y, from.x)
                    } else if pushed == 1 {
                        mode = abs(value.translation.width) > abs(value.translation.height) ? .pages : .pull
                        grab = page.value(at: now) + value.translation.width / size.width
                    } else {
                        mode = .idle
                    }
                    activity += 1
                }
                if mode == .spin {
                    // The rings turn by the angle the finger sweeps round the centre.
                    let to = arm(value.location)
                    let angle = atan2(to.y, to.x)
                    var swept = angle - grab
                    swept -= 2 * .pi * (swept / (2 * .pi)).rounded()
                    spin.retarget(spin.value(at: now) + swept, at: now, duration: 0, curve: .linear)
                    grab = angle
                } else if mode == .pages {
                    page.retarget(min(3 + Metrics.over, max(1 - Metrics.over, grab - value.translation.width / size.width)), at: now, duration: 0, curve: .linear)
                }
            }
            .onEnded { value in
                let now = Date()
                switch mode {
                case nil:
                    tap(at: CGPoint(x: (value.location.x - origin.x) / scale, y: (value.location.y - origin.y) / scale))
                    return
                case .spin:
                    // They coast on at the speed the finger had round the centre.
                    let r = arm(value.location)
                    let speed = (r.x * value.velocity.height - r.y * value.velocity.width) / scale / max(1, r.x * r.x + r.y * r.y)
                    spin.retarget(spin.value(at: now) + speed * M.coastReach, at: now, duration: M.coast, curve: M.coastCurve)
                case .pages:
                    let to = (page.value(at: now) - value.velocity.width / size.width * Metrics.flick).rounded()
                    settle(Int(min(3, max(1, to))))
                case .pull:
                    if value.translation.height > Metrics.leave { leave() }
                default:
                    break
                }
                mode = nil
                touches += 1
                activity += 1
            }
    }

    private func tap(at point: CGPoint) {
        let now = Date()
        let pushed = tour.value(at: now)
        if pushed == 0 {
            let phase = reduceMotion ? M.phaseAtStart : now.timeIntervalSince(phaseSince)
            for index in S.coins.indices {
                let place = S.coinPlace(index, phase: phase, spin: spin.value(at: now))
                if hypot(point.x - place.x, point.y - place.y) < S.coin + 10 {
                    pick(index + 1)
                    return
                }
            }
        } else if pushed == 1 {
            // The coin at the top of a scene is the way back.
            let front = Int(page.value(at: now).rounded())
            if hypot(point.x - S.cx, point.y - S.coinTop[front - 1]) < S.coin + 10 { leave() }
        }
    }

    private func pick(_ to: Int) {
        touches += 1
        tapHaptic.impactOccurred()
        open(to)
    }

    private func leave() {
        touches += 1
        tapHaptic.impactOccurred()
        go(max(scene, 1), seconds: M.settle)
        close()
    }

    private func settle(_ to: Int) {
        if to != scene { tapHaptic.impactOccurred() }
        go(to, seconds: M.settle)
    }

    // MARK: Scenes

    /// Starts scene `to`'s contents coming in, `after` seconds from now.
    private func reveal(_ to: Int, after: Double) {
        revealAt[to - 1] = Date().addingTimeInterval(after)
    }

    /// From the orbit into scene `to`.
    private func open(_ to: Int) {
        scene = to
        onScene?(to)
        hero = to
        page = Tween(Double(to))
        reveal(to, after: M.push * 0.8)
        tour.retarget(1, duration: reduceMotion ? M.fade : M.push, curve: .linear)
        activity += 1
    }

    /// To scene `to` from the one in front, in `seconds`.
    private func go(_ to: Int, seconds: Double = M.slide) {
        if to != scene {
            scene = to
            onScene?(to)
            reveal(to, after: seconds == M.slide ? M.revealAfter : seconds * 0.6)
        }
        hero = to
        page.retarget(Double(to), duration: reduceMotion ? M.fade : seconds, curve: seconds == M.slide ? M.inOut : Ease.out)
        activity += 1
    }

    /// Back out to the orbit, whose clock starts again.
    private func close() {
        scene = 0
        onScene?(0)
        phaseSince = Date()
        tour.retarget(0, duration: reduceMotion ? M.fade : M.push, curve: .linear)
        activity += 1
    }

    private func loop() async {
        guard autoPlay, mode == nil else { return }
        if touches > 0 {
            // A touch takes over; a while later the tour goes back to the orbit with the rings as
            // they were, and a loop begins as long after a pull back as it always does.
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            let inside = scene != 0
            if inside { close() }
            spin.retarget(0, duration: M.unspin, curve: Ease.out)
            activity += 1
            try? await Task.sleep(for: .seconds(inside ? M.phaseAtStart : M.unspin))
        }
        let steps: [(Double, () -> Void)] = [(M.openAt, { open(1) }), (M.secondAt, { go(2) }), (M.thirdAt, { go(3) }), (M.closeAt, { close() })]
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for (at, step) in steps {
                try? await clock.sleep(until: origin + .seconds(at))
                guard !Task.isCancelled else { return }
                step()
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
        let holding: Bool
    }
}

private typealias M = OrbitCoinTourMotion
private typealias S = OrbitCoinTourStage

private enum Metrics {
    static let coinBox = (OrbitCoinTourStage.coin + 8) * 2
    /// A finger that moves less than this has tapped.
    static let slop = 8.0
    /// A swipe down by this much leaves the tour; a flick carries a page on by this many seconds of its speed.
    static let leave = 80.0
    static let flick = 0.2
    /// The first and the last scene give this much of a page when pulled further.
    static let over = 0.2
}

#Preview {
    OrbitCoinTour(autoPlay: true)
}
