import SwiftUI
import UIKit

enum SwitcherDeckNavMetrics {
    // The deck. A page in the deck is `deckScale` of the screen, centred `deckY` pt below the middle.
    // Its place depends on p, its slot minus the deck's scroll (0 = the page in front): pages to the
    // right lie on top and spread wide, pages to the left tuck under, recede and darken, like the
    // pages of a fanned deck. Every page is turned about its upright axis (right edge away), more so
    // the further left it lies.
    static let deckScale = 0.6
    static let deckY = 22.0
    static let spread = 0.56
    static let spreadSkew = 0.15
    static let recede = 0.045
    static let turnLeft = 30.0
    static let turnFront = 17.0
    static let turnRight = 8.0
    static let perspective = 1100.0
    static let dimPerPage = 0.2
    static let dimMax = 0.55
    // Corner radius of a page in its own (unscaled) points: none on the full screen, then rounded
    // as soon as it starts to shrink.
    static let radiusShrinking = 50.0
    static let radiusDeck = 38.0
    // The title chip above each page, in screen points (drawn inside the page, so divided by its scale).
    static let chipHeight = 28.0
    static let chipGap = 12.0

    // Pulling the handle: the finger travels `openDist` of the screen's height to open the deck all
    // the way. A release is projected 0.18 s ahead; past `openAt` it opens, otherwise it falls back.
    static let handleZone = 70.0
    static let openDist = 0.42
    static let openAt = 0.38
    static let throwTime = 0.18
    static let followX = 0.55
    // Scrolling: one page per `scrollStep` of the page width; a release is thrown 0.22 s ahead.
    static let scrollStep = 0.55
    static let scrollThrow = 0.22
    // Flicking a page up closes it: faster than `dismissSpeed` pt/s or past `dismissAt` of its height.
    static let dismissSpeed = 800.0
    static let dismissAt = 0.28
    static let dismissTime = 0.3

    static let panSlop = 6.0
    static let tapMaxDuration = 0.35

    typealias Spring = (duration: Double, dampingRatio: Double)
    static let openSpring: Spring = (0.56, 0.82)
    static let zoomSpring: Spring = (0.54, 0.9)
    static let scrollSpring: Spring = (0.62, 0.88)
    static let slotSpring: Spring = (0.52, 0.84)
    static let backSpring: Spring = (0.42, 0.8)
    static let reducedTime = 0.22
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))

    // autoPlay (seconds). The loop starts and ends on the journal at full screen. Each stroke is a
    // virtual finger: the handle pulled up, a flick across the deck (to `to`), or a tap on a page.
    static let loop = 12.0
    static let strokes: [SwitcherDeckNavStroke] = [
        .init(kind: .handle, t0: 0.9, t1: 1.7, from: 0, to: 0),
        .init(kind: .flick, t0: 2.75, t1: 3.05, from: 0, to: 2),
        .init(kind: .flick, t0: 3.95, t1: 4.25, from: 2, to: 3),
        .init(kind: .flick, t0: 5.0, t1: 5.3, from: 3, to: 2),
        .init(kind: .tap, t0: 6.15, t1: 6.25, from: 2, to: 2),
        .init(kind: .handle, t0: 7.55, t1: 8.35, from: 2, to: 2),
        .init(kind: .flick, t0: 9.25, t1: 9.55, from: 2, to: 0),
        .init(kind: .tap, t0: 10.35, t1: 10.45, from: 0, to: 0),
    ]
    // A touch stops the loop for `userHold` s; it then goes back to the journal over `lead` s and restarts.
    static let userHold = 5.0
    static let lead = 2.2
}

struct SwitcherDeckNavStroke {
    enum Kind { case handle, flick, tap }
    var kind: Kind
    var t0: Double
    var t1: Double
    var from: Double
    var to: Double
}

/// How one page is drawn this frame.
struct SwitcherDeckNavCardState {
    var alpha: Double
    var projection: ProjectionTransform
    var radius: Double
    var dim: Double
    var chip: Double
    /// Place in the drawing order: closed pages first, then the deck from left to right.
    var order: Double
}

struct SwitcherDeckNavFrame {
    var open: Double
    var openCount: Int
    var closedCount: Int
    var cards: [SwitcherDeckNavCardState]
    var fingerX: Double
    var fingerY: Double
    var fingerAlpha: Double
    var fingerPress: Double
}

struct SwitcherDeckNavSettings {
    var count: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    var onOpenPage: ((Int) -> Void)?
}

enum SwitcherDeckNavMath {
    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, x)) }

    /// Pushing past [lo, hi] meets a soft resistance that never quite gives.
    static func rubber(_ x: Double, _ lo: Double, _ hi: Double, _ give: Double) -> Double {
        if x > hi { return hi + (1 - 1 / (((x - hi) * 0.55) / give + 1)) * give }
        if x < lo { return lo - (1 - 1 / (((lo - x) * 0.55) / give + 1)) * give }
        return x
    }

    /// Horizontal offset of a page's centre from the screen's, for a page p places from the front.
    static func deckX(_ p: Double, _ cardWidth: Double) -> Double {
        cardWidth * p * (SwitcherDeckNavMetrics.spread + SwitcherDeckNavMetrics.spreadSkew * tanh(p))
    }

    static func deckScale(_ p: Double) -> Double {
        SwitcherDeckNavMetrics.deckScale * (1 - SwitcherDeckNavMetrics.recede * clamp(-p, 0, 3))
    }

    static func deckTurn(_ p: Double) -> Double {
        typealias M = SwitcherDeckNavMetrics
        let q = clamp(p, -3, 3)
        return q < 0 ? M.turnFront + (M.turnLeft - M.turnFront) * (-q / 3) : M.turnFront + (M.turnRight - M.turnFront) * (q / 3)
    }

    /// The slot of the topmost page under a point in the deck, or -1. Pages to the right lie on top.
    static func hitSlot(_ x: Double, _ y: Double, count: Int, scroll: Double, width: Double, height: Double) -> Int {
        let cardWidth = width * SwitcherDeckNavMetrics.deckScale
        for k in stride(from: count - 1, through: 0, by: -1) {
            let p = Double(k) - scroll
            let s = deckScale(p) / SwitcherDeckNavMetrics.deckScale
            let cx = width / 2 + deckX(p, cardWidth)
            let cy = height / 2 + SwitcherDeckNavMetrics.deckY
            if abs(x - cx) < (cardWidth * s) / 2 && abs(y - cy) < (height * SwitcherDeckNavMetrics.deckScale * s) / 2 { return k }
        }
        return -1
    }

    /// Whether a clock that went from `before` to `now` (wrapping at the loop) crossed `at`.
    static func crossed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && (at > before || at <= now))
    }

    /// React Native's `perspective, translateX, translateY, scale, rotateY` about the middle of a
    /// view `width` × `height`, as the projection SwiftUI applies to it.
    static func projection(width: Double, height: Double, tx: Double, ty: Double, scale s: Double, turn: Double) -> ProjectionTransform {
        let rad = turn * .pi / 180
        let k = sin(rad) / SwitcherDeckNavMetrics.perspective
        let c = cos(rad)
        let hw = width / 2
        let hh = height / 2
        var t = ProjectionTransform()
        t.m11 = hw * k + s * c
        t.m12 = hh * k
        t.m13 = k
        t.m21 = 0
        t.m22 = s
        t.m23 = 0
        t.m31 = hw + tx - hw * t.m11
        t.m32 = hh + ty - k * hw * hh - s * hh
        t.m33 = 1 - k * hw
        return t
    }
}

/// The deck's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class SwitcherDeckNavEngine {
    private typealias M = SwitcherDeckNavMetrics
    private typealias X = SwitcherDeckNavMath

    private enum Mode { case none, undecided, handle, scroll, lift }

    // 0 = the active page fills the screen, 1 = the deck is open.
    private var open = SwitcherDeckNavValue(0)
    // Which slot of the deck is in front, continuously.
    private var scroll = SwitcherDeckNavValue(0)
    private var active = 0
    private var dragX = SwitcherDeckNavValue(0)
    private var liftId = -1
    private var lift = SwitcherDeckNavValue(0)
    // The page flicked away, removed once its flight ends.
    private var leaving = -1
    // Page ids in deck order; pages not in it have been closed.
    private var order: [Int] = []
    private var slots: [SwitcherDeckNavValue] = []
    private var presence: [SwitcherDeckNavValue] = []
    private var count = 0
    private var mode = Mode.none
    private var startX = 0.0
    private var startY = 0.0
    private var startOpen = 0.0
    private var startScroll = 0.0
    private var lastRound = 0
    // autoPlay: the loop's clock, a pause after a touch, and the virtual finger.
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastAuto: Bool?
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var fingerAlpha = SwitcherDeckNavValue(0)
    private var fingerPress = 0.0
    private var lastDate: Date?
    private var reduced = false
    private var onOpenPage: ((Int) -> Void)?
    private var width = 0.0
    private var height = 0.0
    private var bottomInset = 0.0
    // The touch under way.
    private var touchStart: Date?
    private var panning = false

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let selection = UISelectionFeedbackGenerator()

    private func setUp(count: Int) {
        self.count = count
        order = Array(0..<count)
        slots = (0..<count).map { SwitcherDeckNavValue(Double($0)) }
        presence = Array(repeating: SwitcherDeckNavValue(1), count: count)
    }

    func step(to date: Date, width: Double, height: Double, bottom: Double, settings: SwitcherDeckNavSettings) -> SwitcherDeckNavFrame {
        if count != settings.count { setUp(count: settings.count) }
        self.width = width
        self.height = height
        bottomInset = bottom
        reduced = settings.reducedMotion
        onOpenPage = settings.onOpenPage
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(max(raw, 0), 0.064)
        if dt > 0 {
            script(dt: dt)
            advance(dt: dt)
        }
        // A selection tick each time another page reaches the front under the user's finger.
        let round = Int((scroll.value).rounded(.toNearestOrAwayFromZero))
        if round != lastRound && mode == .scroll { selection.selectionChanged() }
        lastRound = round
        return frame()
    }

    private func advance(dt: Double) {
        open.step(dt)
        scroll.step(dt)
        dragX.step(dt)
        if lift.step(dt), leaving >= 0, leaving == liftId {
            // The page has flown off: it leaves the deck and the rest close the gap.
            let id = leaving
            leaving = -1
            setOrder(order.filter { $0 != id })
        }
        fingerAlpha.step(dt)
        for i in 0..<count {
            slots[i].step(dt)
            presence[i].step(dt)
        }
    }

    // MARK: Animations

    private func animate(_ value: inout SwitcherDeckNavValue, to target: Double, _ spring: M.Spring, velocity: Double = 0) {
        if reduced {
            value.timing(to: target, duration: M.reducedTime, curve: Ease.out)
        } else {
            value.spring(to: target, duration: spring.duration, dampingRatio: spring.dampingRatio, velocity: velocity)
        }
    }

    /// The deck order changed: a page that stays slides to its new slot; one that comes back fades in at it.
    private func setOrder(_ next: [Int]) {
        let before = order
        order = next
        for id in 0..<count {
            let was = before.firstIndex(of: id)
            let now = next.firstIndex(of: id)
            if let now, let was {
                if now != was { animate(&slots[id], to: Double(now), M.slotSpring) }
            } else if let now {
                slots[id].set(Double(now))
                presence[id].set(0)
                presence[id].timing(to: 1, duration: 0.42, curve: Ease.out)
            } else {
                presence[id].set(0)
            }
        }
    }

    private func restoreAll() {
        // The flicked-away page is still parked off screen; it comes back from its slot instead.
        liftId = -1
        leaving = -1
        lift.set(0)
        setOrder(Array(0..<count))
    }

    private func openDeck(velocity: Double, user: Bool) {
        animate(&open, to: 1, M.openSpring, velocity: velocity)
        animate(&dragX, to: 0, M.backSpring)
        if user { light.impactOccurred() }
    }

    private func closeDeck(velocity: Double) {
        animate(&open, to: 0, M.backSpring, velocity: velocity)
        animate(&dragX, to: 0, M.backSpring)
    }

    /// Zooms the page in `slot` back to the full screen.
    private func openSlot(_ slot: Int, user: Bool) {
        guard !order.isEmpty else { return }
        let k = min(max(slot, 0), order.count - 1)
        active = order[k]
        animate(&scroll, to: Double(k), M.zoomSpring)
        animate(&open, to: 0, M.zoomSpring)
        if user {
            light.impactOccurred()
            onOpenPage?(order[k])
        }
    }

    private func scrollTo(_ slot: Double, velocity: Double) {
        animate(&scroll, to: X.clamp(slot, 0, Double(order.count - 1)), M.scrollSpring, velocity: velocity)
    }

    private func touched() {
        hold = M.userHold
        fingerAlpha.timing(to: 0, duration: 0.12)
    }

    // MARK: The script

    // Driven from the frame clock so a recording of one loop joins up cleanly.
    private func script(dt: Double) {
        if mode != .none { hold = M.userHold }
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping { clock = -M.lead }
            return
        }
        guard looping, width > 0 else { return }
        let W = width
        let H = height
        let before = clock
        var t = before + dt
        if t >= M.loop { t -= M.loop }
        clock = t

        // The lead-in after a touch: everything put back, then the journal opened from the deck.
        if before < 0 {
            if X.crossed(before, t, -M.lead + 0.001) {
                if order.count < count { restoreAll() }
                if active != 0 || open.value > 0.02 { openDeck(velocity: 0, user: false) } else { clock = 0 }
            }
            if X.crossed(before, t, -1.1) { openSlot(0, user: false) }
            return
        }

        let cardWidth = W * M.deckScale
        let step = cardWidth * M.scrollStep
        let handleY = H - bottomInset - Double(SwitcherDeckNavHandleMetrics.bottom + SwitcherDeckNavHandleMetrics.height / 2)
        let deckCY = H / 2 + M.deckY
        var alpha = 0.0
        for st in M.strokes {
            let u = (t - st.t0) / (st.t1 - st.t0)
            // The finger shows a little before it moves and stays a moment after it lifts.
            let near = X.clamp(min((t - st.t0 + 0.3) / 0.2, (st.t1 + 0.35 - t) / 0.2), 0, 1)
            if near > alpha { alpha = near }
            if t < st.t0 - 0.3 || t > st.t1 + 0.35 { continue }
            let e = M.easeInOut.value(at: X.clamp(u, 0, 1))
            switch st.kind {
            case .handle:
                fingerX = W / 2 + 26 * e
                fingerY = handleY - e * 0.5 * M.openDist * H
                if u >= 0 && u <= 1 {
                    open.set(0.5 * e)
                    dragX.set(26 * e * M.followX)
                }
                if X.crossed(before, t, st.t1) { openDeck(velocity: 2.2, user: false) }
            case .flick:
                // The deck follows the finger for part of the way; the release throws it the rest.
                let travel = -(st.to - st.from) * step * 0.7
                fingerX = W / 2 - travel / 2 + travel * e
                fingerY = deckCY + 40
                if u >= 0 && u <= 1 { scroll.set(st.from - (travel * e) / step) }
                if X.crossed(before, t, st.t1) { scrollTo(st.to, velocity: (st.to - st.from).sign == .minus ? -2.2 : 2.2) }
            case .tap:
                fingerX = W / 2 + X.deckX(st.to - scroll.value, cardWidth) - cardWidth * 0.08
                fingerY = deckCY + 30
                fingerPress = X.clamp(1 - abs(t - (st.t0 + st.t1) / 2) / 0.25, 0, 1)
                if X.crossed(before, t, st.t1) { openSlot(Int(st.to), user: false) }
            }
        }
        fingerAlpha.set(alpha)
    }

    // MARK: The picture

    private func frame() -> SwitcherDeckNavFrame {
        let o = open.value
        let shown = X.clamp(o, 0, 1)
        let cardWidth = width * M.deckScale
        let rest = (0..<count).filter { !order.contains($0) }
        let drawOrder = rest + order
        var cards: [SwitcherDeckNavCardState] = []
        cards.reserveCapacity(count)
        for id in 0..<count {
            let p = slots[id].value - scroll.value
            let dx = X.deckX(p, cardWidth)
            let ds = X.deckScale(p)
            let turn = X.deckTurn(p)
            let lifted = liftId == id ? lift.value : 0
            var tx: Double
            var ty: Double
            var s: Double
            var r: Double
            var alpha = 1.0
            if active == id {
                // The open page shrinks from the full screen into its place in the deck, following the finger.
                tx = dx * o + dragX.value
                ty = M.deckY * o
                s = 1 + (ds - 1) * o
                r = turn * o
            } else {
                // The other pages wait off to their side and slide in as the deck opens.
                tx = dx + (1 - shown) * width * 1.15 * (p >= 0 ? 1 : -1)
                ty = M.deckY
                s = ds
                r = turn
                alpha = min(1, shown * 1.6)
            }
            let presenceNow = presence[id].value
            alpha *= presenceNow * (1 - X.clamp(-lifted / (height * 0.5), 0, 1))
            let radius: Double = o <= 0 ? 0 : o < 0.08 ? M.radiusShrinking * o / 0.08 : o < 1 ? M.radiusShrinking + (M.radiusDeck - M.radiusShrinking) * (o - 0.08) / 0.92 : M.radiusDeck
            cards.append(SwitcherDeckNavCardState(
                alpha: alpha,
                projection: X.projection(width: width, height: height, tx: tx, ty: ty + lifted, scale: s * (0.92 + 0.08 * presenceNow), turn: r),
                radius: radius,
                dim: X.clamp(-p * M.dimPerPage, 0, M.dimMax) * shown,
                chip: X.clamp((o - 0.55) / 0.45, 0, 1),
                order: Double(drawOrder.firstIndex(of: id) ?? 0)
            ))
        }
        return SwitcherDeckNavFrame(
            open: o,
            openCount: order.count,
            closedCount: count - order.count,
            cards: cards,
            fingerX: fingerX,
            fingerY: fingerY,
            fingerAlpha: fingerAlpha.value,
            fingerPress: fingerPress
        )
    }

    // MARK: Gestures

    // One recogniser stands in for the race of a pan (after 6 pt of travel) and a tap (a touch
    // lifted within 350 ms that never travelled that far).
    func touchChanged(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.panSlop else { return }
            panning = true
            touched()
            startX = value.startLocation.x
            startY = value.startLocation.y
            mode = .undecided
        }
        panUpdate(tx: value.translation.width, ty: value.translation.height)
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touchStart = nil
            panning = false
            mode = .none
        }
        if panning {
            panEnd(vx: value.velocity.width, vy: value.velocity.height)
        } else if let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration {
            tap(x: value.location.x, y: value.location.y)
        }
    }

    private func panUpdate(tx: Double, ty: Double) {
        let W = width
        let H = height
        let n = order.count
        if mode == .undecided {
            let vertical = abs(ty) > abs(tx)
            if open.value < 0.5 {
                // Closed: only the handle at the bottom answers, and only to a pull upwards.
                if startY > H - bottomInset - M.handleZone && vertical && ty < 0 {
                    mode = .handle
                    startOpen = open.value
                } else {
                    mode = .none
                }
            } else if vertical && ty < 0 {
                let k = X.hitSlot(startX, startY, count: n, scroll: scroll.value, width: W, height: H)
                if k >= 0 {
                    liftId = order[k]
                    leaving = -1
                    lift.set(0)
                    mode = .lift
                } else {
                    mode = .none
                }
            } else if !vertical {
                startScroll = scroll.value
                mode = .scroll
            } else {
                mode = .none
            }
        }
        switch mode {
        case .handle:
            let raw = startOpen - ty / (H * M.openDist)
            open.set(X.rubber(raw, 0, 1, 0.12))
            dragX.set(tx * M.followX)
        case .scroll:
            let raw = startScroll - tx / (W * M.deckScale * M.scrollStep)
            scroll.set(X.rubber(raw, 0, Double(n - 1), 0.35))
        case .lift:
            // The last page left cannot be closed: it only gives a little.
            lift.set(ty < 0 && n > 1 ? ty : X.rubber(ty, 0, 0, 40))
        default:
            break
        }
    }

    private func panEnd(vx: Double, vy: Double) {
        let W = width
        let H = height
        switch mode {
        case .handle:
            let v = -vy / (H * M.openDist)
            if open.value + v * M.throwTime > M.openAt {
                openDeck(velocity: v, user: true)
            } else {
                closeDeck(velocity: v)
            }
        case .scroll:
            let v = -vx / (W * M.deckScale * M.scrollStep)
            scrollTo((scroll.value + v * M.scrollThrow).rounded(.toNearestOrAwayFromZero), velocity: v)
        case .lift:
            let n = order.count
            if n > 1 && (vy < -M.dismissSpeed || lift.value < -H * M.deckScale * M.dismissAt) {
                medium.impactOccurred()
                leaving = liftId
                lift.timing(to: -H * 0.8, duration: M.dismissTime, curve: Ease.out)
                // The front slot stays in range once the page is gone.
                if scroll.value > Double(n - 2) { scrollTo(Double(n - 2), velocity: 0) }
            } else {
                animate(&lift, to: 0, M.backSpring, velocity: vy)
            }
        default:
            break
        }
    }

    private func tap(x: Double, y: Double) {
        touched()
        guard open.value >= 0.85 else { return }
        let n = order.count
        // The strip under the deck reopens the pages that were closed.
        if n < count && y > height - bottomInset - 64 {
            restoreAll()
            return
        }
        let k = X.hitSlot(x, y, count: n, scroll: scroll.value, width: width, height: height)
        openSlot(k >= 0 ? k : Int(scroll.value.rounded(.toNearestOrAwayFromZero)), user: true)
    }
}
