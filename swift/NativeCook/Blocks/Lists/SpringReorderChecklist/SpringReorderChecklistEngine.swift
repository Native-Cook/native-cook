import Foundation

/// One row as drawn: y in the viewport (pt, already scrolled), the lift (0 … 1) and the check's stroke (0 … 1).
struct SpringReorderChecklistRowFrame {
    var y = 0.0
    var lift = 0.0
    var draw = 0.0
}

struct SpringReorderChecklistFrame {
    var rows: [SpringReorderChecklistRowFrame] = []
    var ghost = 0.0
    var ghostX = 0.0
    var ghostY = 0.0
    var ghostTap = 0.0
}

/// The list's state machine: the film (a pure function of loop time), and under a finger a fixed order table with a
/// spring per row. It is stepped by the view's `TimelineView` and never publishes per-frame state itself.
final class SpringReorderChecklistEngine {
    typealias M = SpringReorderChecklistMotion

    private enum Mode { case auto, manual, returning }
    private enum Touch { case none, pending, scroll, drag }

    private struct Spring1 {
        var value: Double
        var velocity = 0.0
        var target: Double
        var spring = M.partSpring

        init(_ v: Double) { value = v; target = v }

        mutating func set(_ v: Double) { value = v; target = v; velocity = 0 }
        mutating func to(_ t: Double, _ s: M.Spring) { target = t; spring = s }
        mutating func step(_ dt: Double) -> Bool {
            if value == target && velocity == 0 { return false }
            var left = dt
            while left > 0 {
                let h = min(left, 1.0 / 480)
                let a = -spring.stiffness * (value - target) - spring.damping * velocity
                velocity += a * h
                value += velocity * h
                left -= h
            }
            if abs(value - target) < 0.0005 && abs(velocity) < 0.002 { value = target; velocity = 0 }
            return true
        }
    }

    private struct Fade {
        var from: Double, to: Double, start = 0.0, duration = 0.0
        init(_ v: Double) { from = v; to = v }
        func value(_ now: Double) -> Double {
            guard duration > 0 else { return to }
            return M.mix(from, to, M.easeInOutQuad(M.clamp((now - start) / duration, 0, 1)))
        }
        mutating func set(_ v: Double) { from = v; to = v; duration = 0 }
        mutating func to(_ v: Double, _ now: Double, _ d: Double) {
            if v == to { return }
            from = value(now); to = v; start = now; duration = d
        }
        func busy(_ now: Double) -> Bool { duration > 0 && now < start + duration }
    }

    let count: Int
    let filmTo: Int
    private let home: [Int]
    private let homeChecks: [Int]

    var film = true
    var viewH = 0.0
    var viewW = 0.0
    var contentH = 0.0

    var onPackedChange: ((Int) -> Void)?
    var onHaptic: ((Int) -> Void)? // 0 lift, 1 slot / toggle, 2 drop
    var onReorder: (([Int]) -> Void)?

    private var mode = Mode.auto
    private var loopStart: Double?
    private var clock = 0.0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var holding = false

    private(set) var order: [Int]
    private(set) var checked: [Int]
    private var ys: [Spring1]
    private var lifts: [Spring1]
    private var draws: [Fade]

    private var touch = Touch.none
    private var downTime = 0.0
    private var downY = 0.0
    private var pressId = -1
    private var dragId = -1
    private var dragY = 0.0
    private var startY = 0.0
    private var startFinger = 0.0
    private var startScroll = 0.0
    private var finger = 0.0

    private(set) var scrollY = 0.0
    private var scrollBase = 0.0
    private var scrollV = 0.0
    private var scrollFree = false // decelerating or bouncing
    private var scrollAnim: Fade?

    private var packed = -1

    /// DEBUG only: `-springReorderChecklistTime <s>` pins the film to one moment, for stills of a phase.
    private let pin: Double? = {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-springReorderChecklistTime"), args.indices.contains(i + 1) { return Double(args[i + 1]) }
        #endif
        return nil
    }()

    init(items: [SpringReorderChecklistItem]) {
        count = items.count
        filmTo = min(M.filmTo, items.count - 1)
        home = Array(0..<items.count)
        homeChecks = items.map { $0.packed ? 1 : 0 }
        order = home
        checked = homeChecks
        ys = home.map { Spring1(Double($0) * M.rowH) }
        lifts = home.map { _ in Spring1(0) }
        draws = homeChecks.map { Fade(Double($0)) }
    }

    private var maxScroll: Double { max(0, contentH - viewH) }

    func configure(film: Bool) {
        guard film != self.film else { return }
        self.film = film
        if !film, mode != .manual { enterManual(at: clock) }
    }

    /// True when nothing moves and nothing is scheduled: the timeline may pause.
    var asleep: Bool {
        if mode != .manual || touch != .none || holding || scrollFree || scrollAnim != nil { return false }
        if film { return false }
        for i in 0..<count where ys[i].value != ys[i].target || ys[i].velocity != 0 || lifts[i].value != lifts[i].target { return false }
        return !draws.contains { $0.busy(clock) }
    }

    // MARK: - Frame

    func frame(at now: Double) -> SpringReorderChecklistFrame {
        let dt = M.clamp(now - clock, 1.0 / 240, 0.05)
        clock = now
        var out = SpringReorderChecklistFrame()
        if mode == .auto && !film { enterManual(at: now) }

        if mode == .auto {
            guard viewH > 0 else { return staticFrame() }
            if loopStart == nil { loopStart = now }
            let t = pin ?? (now - loopStart!).truncatingRemainder(dividingBy: M.loop)
            let distance = min(maxScroll, max(0, Double(filmTo + 1) * M.rowH + 18 - viewH))
            scrollY = M.scroll(t, distance)
            for i in 0..<count {
                ys[i].set(M.rowSlot(i, t, M.filmFrom, filmTo) * M.rowH)
                if i == M.filmFrom {
                    lifts[i].set(M.lift(t))
                    draws[i].set(max(Double(homeChecks[i]), M.check(t)))
                } else {
                    lifts[i].set(0)
                    draws[i].set(Double(homeChecks[i]))
                }
            }
            out.ghost = M.clamp(M.ghost(t), 0, 1)
            out.ghostX = M.ghostOnCheck(t) ? 33 : viewW - 34
            out.ghostY = M.heroSlot(t, M.filmFrom, filmTo) * M.rowH + M.rowH / 2 - scrollY
            out.ghostTap = M.ghostTap(t)
            var n = checked.reduce(0, +)
            if checked[M.filmFrom] == 0 && M.check(t) > 0.5 { n += 1 }
            report(n)
        } else {
            if mode == .manual {
                if touch == .pending, pressId >= 0, now - downTime >= M.longPress { startDrag(now) }
                if dragId >= 0 {
                    autoScroll(dt)
                } else if film && !holding && now - lastTouch > M.idle {
                    goHome(now)
                }
            } else if now >= returnAt {
                scrollAnim = nil
                scrollFree = false
                scrollY = 0
                loopStart = now
                mode = .auto
                return frame(at: now)
            }
            stepScroll(now, dt)
            for i in 0..<count {
                if i == dragId { ys[i].set(dragY) } else { _ = ys[i].step(dt) }
                _ = lifts[i].step(dt)
            }
            report(checked.reduce(0, +))
        }

        out.rows = (0..<count).map { i in
            SpringReorderChecklistRowFrame(y: ys[i].value - scrollY, lift: lifts[i].value, draw: draws[i].value(now))
        }
        return out
    }

    private func staticFrame() -> SpringReorderChecklistFrame {
        var out = SpringReorderChecklistFrame()
        out.rows = (0..<count).map { i in
            SpringReorderChecklistRowFrame(y: Double(order[i]) * M.rowH - scrollY, lift: 0, draw: Double(checked[i]))
        }
        return out
    }

    private func report(_ n: Int) {
        guard n != packed else { return }
        packed = n
        let cb = onPackedChange
        DispatchQueue.main.async { cb?(n) }
    }

    private func haptic(_ kind: Int) {
        let cb = onHaptic
        DispatchQueue.main.async { cb?(kind) }
    }

    // MARK: - Modes

    private func enterManual(at now: Double) {
        if mode == .auto, let start = loopStart {
            let t = (now - start).truncatingRemainder(dividingBy: M.loop)
            let slot = Int(M.clamp((M.heroSlot(t, M.filmFrom, filmTo)).rounded(), Double(M.filmFrom), Double(filmTo)))
            order = M.moveOrder(order, M.filmFrom, slot)
            if M.check(t) > 0.5 { checked[M.filmFrom] = 1 }
        }
        mode = .manual
        scrollAnim = nil
        for i in 0..<count {
            ys[i].velocity = 0
            ys[i].to(Double(order[i]) * M.rowH, M.partSpring)
            lifts[i].to(i == dragId ? 1 : 0, M.liftSpring)
            draws[i].to(Double(checked[i]), clock, checked[i] == 1 ? 0.42 : 0.26)
        }
    }

    private func goHome(_ now: Double) {
        mode = .returning
        returnAt = now + M.returnS
        order = home
        checked = homeChecks
        for i in 0..<count {
            ys[i].to(Double(home[i]) * M.rowH, M.partSpring)
            lifts[i].to(0, M.liftSpring)
            draws[i].to(Double(homeChecks[i]), now, homeChecks[i] == 1 ? 0.42 : 0.26)
        }
        scrollFree = false
        var anim = Fade(scrollY)
        anim.to(0, now, 0.35)
        scrollAnim = anim
    }

    /// The first touch during the film: its order and checks become the real ones.
    private func takeOver(_ now: Double) {
        clock = max(clock, now)
        holding = true
        lastTouch = now
        if mode == .auto { enterManual(at: now) } else { mode = .manual }
        scrollAnim = nil
    }

    // MARK: - Touch

    /// `y` and `x` are in the viewport's space.
    func touchBegan(x: Double, y: Double, at now: Double) {
        takeOver(now)
        touch = .pending
        downTime = now
        downY = y
        finger = y
        scrollFree = false
        scrollV = 0
        let slot = Int(floor((y + scrollY) / M.rowH))
        pressId = order.firstIndex(of: slot) ?? -1
    }

    func touchMoved(y: Double, at now: Double) {
        lastTouch = now
        finger = y
        switch touch {
        case .pending:
            if abs(y - downY) > 10 {
                touch = .scroll
                scrollBase = scrollY + downY
            }
            if touch == .scroll { dragScroll(y) }
        case .scroll:
            dragScroll(y)
        case .drag:
            applyDrag()
        case .none:
            break
        }
    }

    func touchEnded(velocityY: Double, at now: Double) {
        switch touch {
        case .drag:
            drop()
        case .pending:
            if now - downTime < M.tapMax, pressId >= 0 { toggle(pressId) }
        case .scroll:
            scrollV = -velocityY
            scrollFree = true
        case .none:
            break
        }
        touch = .none
        pressId = -1
        holding = false
        lastTouch = now
    }

    func toggle(_ id: Int) {
        if mode != .manual { takeOver(clock); holding = false }
        checked[id] = checked[id] == 1 ? 0 : 1
        draws[id].to(Double(checked[id]), clock, checked[id] == 1 ? 0.42 : 0.26)
        lastTouch = clock
        haptic(1)
    }

    private func startDrag(_ now: Double) {
        let id = pressId
        touch = .drag
        dragId = id
        startY = Double(order[id]) * M.rowH
        dragY = startY
        startFinger = finger
        startScroll = scrollY
        scrollFree = false
        if scrollY < 0 || scrollY > maxScroll { scrollY = M.clamp(scrollY, 0, maxScroll) }
        lifts[id].to(1, M.liftSpring)
        haptic(0)
    }

    private func applyDrag() {
        let id = dragId
        guard id >= 0 else { return }
        let y = startY + (finger - startFinger) + (scrollY - startScroll)
        dragY = M.clamp(y, -M.rowH * 0.4, Double(count - 1) * M.rowH + M.rowH * 0.4)
        let slot = Int(M.clamp((dragY / M.rowH).rounded(), 0, Double(count - 1)))
        if order[id] != slot {
            order = M.moveOrder(order, id, slot)
            for i in 0..<count where i != id { ys[i].to(Double(order[i]) * M.rowH, M.partSpring) }
            haptic(1)
        }
    }

    private func drop() {
        let id = dragId
        guard id >= 0 else { return }
        dragId = -1
        ys[id].velocity = 0
        ys[id].to(Double(order[id]) * M.rowH, M.dropSpring)
        lifts[id].to(0, M.liftSpring)
        haptic(2)
        var ids = Array(repeating: 0, count: count)
        for (i, slot) in order.enumerated() { ids[slot] = i }
        let cb = onReorder
        DispatchQueue.main.async { cb?(ids) }
    }

    // MARK: - Scroll

    private func rubber(_ over: Double) -> Double {
        let d = max(viewH, 1)
        return (1 - 1 / (over * 0.55 / d + 1)) * d
    }

    private func dragScroll(_ y: Double) {
        let raw = scrollBase - y
        if raw < 0 { scrollY = -rubber(-raw) }
        else if raw > maxScroll { scrollY = maxScroll + rubber(raw - maxScroll) }
        else { scrollY = raw }
    }

    private func autoScroll(_ dt: Double) {
        let center = dragY - scrollY + M.rowH / 2
        var v = 0.0
        if center < M.edgeZone { v = -M.edgeSpeed * pow((M.edgeZone - center) / M.edgeZone, 2) }
        else if center > viewH - M.edgeZone { v = M.edgeSpeed * pow((center - (viewH - M.edgeZone)) / M.edgeZone, 2) }
        guard v != 0 else { return }
        let next = M.clamp(scrollY + v * dt, 0, maxScroll)
        if next != scrollY {
            scrollY = next
            applyDrag()
        }
    }

    private func stepScroll(_ now: Double, _ dt: Double) {
        if let anim = scrollAnim {
            scrollY = anim.value(now)
            if !anim.busy(now) { scrollAnim = nil }
            return
        }
        guard scrollFree else { return }
        let limit = M.clamp(scrollY, 0, maxScroll)
        if scrollY != limit {
            // Outside the content: a critically damped pull back, carrying the velocity in.
            let k = 160.0, c = 2 * sqrt(k)
            var left = dt
            while left > 0 {
                let h = min(left, 1.0 / 480)
                scrollV += (-k * (scrollY - limit) - c * scrollV) * h
                scrollY += scrollV * h
                left -= h
            }
            if abs(scrollY - limit) < 0.3 && abs(scrollV) < 5 { scrollY = limit; scrollV = 0; scrollFree = false }
            return
        }
        scrollV *= pow(0.998, dt * 1000)
        scrollY += scrollV * dt
        if abs(scrollV) < 6 && scrollY >= 0 && scrollY <= maxScroll { scrollV = 0; scrollFree = false }
        lastTouch = now
    }
}
