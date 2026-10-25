import UIKit

struct AquariumFeedListGeometry: Equatable {
    var W = 0.0
    var H = 0.0
    var top = 0.0
    var bottom = 0.0
    var listTop: Double { top + AquariumFeedListScene.header }
    var bottomY: Double { H - bottom }
    var area: AquariumFeedListArea {
        let y = listTop + 8
        return AquariumFeedListArea(x: 22, y: y, w: W - 44, h: H - bottom - 36 - y)
    }
}

/// Which card the fish are feeding at, how strongly (0–1), and its press pulse (0–1).
struct AquariumFeedListFocus: Equatable {
    var card = -1
    var amt = 0.0
    var press = 0.0
}

struct AquariumFeedListFishPose {
    var x = -200.0
    var y = 0.0
    var rot = 0.0
    var sx = 1.0
    var phase = 0.0
    var amp = 0.0
}

/// What a frame draws.
struct AquariumFeedListFrame {
    var fish = [AquariumFeedListFishPose](repeating: .init(), count: AquariumFeedListScene.fish.count)
    /// Fish-time (s), the stir, the light's phase and the list's scroll.
    var ft = 0.0
    var stir = 0.0
    var ph = 0.0
    var scroll = 0.0
    var focus = AquariumFeedListFocus()
}

/*
 Aquarium Feed List — the tank, stepped once a frame from the TimelineView (like the RN frame
 callback). In the loop the list's scroll is scripted (the engine moves the UIScrollView); under
 touch the fish keep their own clock and feel the real scroll's speed; 2 s after the last touch
 the list scrolls home, the fish swim back to their places (0.9 s) and the loop restarts at 0.
*/
@MainActor
final class AquariumFeedListEngine {
    private typealias S = AquariumFeedListScene
    enum Mode { case auto, user, back }

    static let idle = 2.0
    static let returnTime = 0.9

    weak var scrollView: UIScrollView?
    var reduced = false
    /// The list scrolled under a finger, or a card was tapped (the view wakes its timeline).
    var onTouch: (() -> Void)?

    private(set) var frame = AquariumFeedListFrame()
    private var mode = Mode.auto
    private var clock = 0.0
    private var now = 0.0
    private var last: Date?
    private var lastTouch = 0.0
    /// The list's scroll offset, as the scroll view reports it.
    var scrollY = 0.0
    private var lastScroll = 0.0
    private(set) var dragging = false
    private var tau = 0.0
    private var stir = 0.0
    private var dir = 1.0
    private var feedAt = -10.0
    private var feedCard = -1
    private var returnAt = 0.0
    private var returnScroll = 0.0
    /// The engine is moving the list itself (its scroll events are not a finger's).
    private var driving = false

    var isUser: Bool { mode == .user }

    private func touched() {
        lastTouch = now
        if mode == .auto {
            // Keep the fish where the loop had them: same fish-time, same stir and feeding.
            let t = clock
            tau = t
            let s = S.autoStir(t)
            stir = s.stir
            dir = s.dir
            if t > S.tapAt - 0.2 && t < 7.6 {
                feedAt = now - (t - S.tapAt)
                feedCard = S.tapCard
            } else {
                feedCard = -1
            }
        }
        mode = .user
    }

    // MARK: - Touch (from the scroll view and the cards)

    func tapCard(_ i: Int) {
        touched()
        feedAt = now
        feedCard = i
        onTouch?()
    }

    func scrolled(to y: Double) {
        scrollY = y
        if dragging && !driving { lastTouch = now }
    }

    func beganDrag() {
        touched()
        dragging = true
        onTouch?()
    }

    func endedDrag(decelerating: Bool) {
        lastTouch = now
        if !decelerating { dragging = false }
    }

    func endedDeceleration() {
        dragging = false
        lastTouch = now
    }

    /// User feeding: up over 0.8 s from the tap, held, gone 3.9 s after it.
    private func userFeed(_ at: Double) -> Double {
        if feedCard < 0 { return 0 }
        let k = at - feedAt
        return S.easeInOutCubic(k / 0.8) * (1 - S.easeInOutCubic((k - 3) / 0.9))
    }

    private func drive(_ y: Double) {
        guard let sv = scrollView, abs(sv.contentOffset.y - y) > 0.01 else { return }
        driving = true
        sv.setContentOffset(CGPoint(x: 0, y: y), animated: false)
        driving = false
    }

    // MARK: - Frame

    func step(to date: Date, auto: Bool, geo: AquariumFeedListGeometry) -> AquariumFeedListFrame {
        let dt = min(max(last.map { date.timeIntervalSince($0) } ?? 0, 0), 0.05)
        last = date
        let at = now + dt
        now = at
        let sy = scrollY
        let v = (sy - lastScroll) / max(dt, 0.001)
        lastScroll = sy

        var ft = 0.0
        var st = 0.0
        var dr = 1.0
        var fd = 0.0
        var card = -1
        var press = 0.0
        var blend = 0.0
        var scroll = sy
        if mode == .auto {
            let t = auto ? (clock + dt).truncatingRemainder(dividingBy: AquariumFeedList.loop) : 0
            clock = t
            ft = t
            let s = S.autoStir(t)
            st = s.stir
            dr = s.dir
            fd = S.autoFeed(t)
            card = S.tapCard
            press = sin(Double.pi * min(1, max(0, (t - S.tapAt) / 0.18)))
            scroll = auto ? S.autoScroll(t) : 0
            if auto { drive(scroll) }
        } else {
            // Under touch the fish keep their own clock and feel the real scroll.
            ft = (tau + (reduced ? 0 : dt)).truncatingRemainder(dividingBy: AquariumFeedList.loop)
            tau = ft
            let target = reduced ? 0 : min(1, abs(v) / 1400)
            let s0 = stir
            st = s0 + (target - s0) * min(1, (target > s0 ? 6 : 1.4) * dt)
            stir = st
            if abs(v) > 60 { dir = v > 0 ? 1 : -1 }
            dr = dir
            fd = userFeed(at)
            card = feedCard
            press = card >= 0 ? sin(Double.pi * min(1, max(0, (at - feedAt) / 0.18))) : 0
            if mode == .user && auto && !dragging && at - lastTouch > Self.idle {
                returnAt = at
                returnScroll = sy
                mode = .back
            }
            if mode == .back {
                blend = S.easeInOutCubic((at - returnAt) / Self.returnTime)
                scroll = returnScroll * (1 - blend)
                drive(scroll)
                if blend >= 1 {
                    mode = .auto
                    clock = 0
                    stir = 0
                    feedCard = -1
                }
            }
        }

        // The fed card's centre on screen (fish circle it, and follow it if the list moves).
        let fc = card >= 0 ? card : S.tapCard
        let tx = geo.W / 2
        let ty = geo.listTop + 16 + Double(fc) * S.row + S.cardH / 2 - scroll
        let area = geo.area
        let h = 1.0 / 60
        var fish = frame.fish
        for i in S.fish.indices {
            let f = S.fish[i]
            let a = S.fishAt(i, ft, area, st, dr, fd, tx, ty)
            var b = S.fishAt(i, ft - h, area, st, dr, fd, tx, ty)
            var x = a.x
            var y = a.y
            var vx = (a.x - b.x) / h
            var vy = (a.y - b.y) / h
            if blend > 0 {
                let r = S.fishAt(i, 0, area, 0, 1, 0, tx, ty)
                x += (r.x - x) * blend
                y += (r.y - y) * blend
                b = S.fishAt(i, -h, area, 0, 1, 0, tx, ty)
                vx += ((r.x - b.x) / h - vx) * blend
                vy += ((r.y - b.y) / h - vy) * blend
            }
            // Facing: the body narrows to edge-on as the fish turns round (tanh of its sideways speed).
            let sx = tanh(vx / 22)
            let rot = max(-0.45, min(0.45, atan2(vy, abs(vx) + 12) * 0.7)) * (vx >= 0 ? 1 : -1)
            let phase = 2 * Double.pi * f.beats * ft / AquariumFeedList.loop
            let amp = reduced ? 0 : 0.16 + 0.24 * st + 0.1 * fd
            fish[i] = AquariumFeedListFishPose(
                x: x, y: y, rot: rot,
                sx: abs(sx) < 0.08 ? (sx < 0 ? -0.08 : 0.08) : sx,
                phase: phase, amp: amp
            )
        }
        frame = AquariumFeedListFrame(
            fish: fish, ft: ft, stir: st, ph: 2 * Double.pi * ft / AquariumFeedList.loop, scroll: scroll,
            focus: AquariumFeedListFocus(card: card, amt: fd, press: press)
        )
        return frame
    }
}
