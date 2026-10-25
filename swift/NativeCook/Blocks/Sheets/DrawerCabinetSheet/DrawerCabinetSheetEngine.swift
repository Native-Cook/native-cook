import Foundation

/// An item drawn this frame: where its centre is, its turn, and (out on the desk) lift and scale.
struct DrawerCabinetSheetSprite {
    let i: Int
    let x: Double
    let y: Double
    let rot: Double
    var lift = 0.0
    var scale = 1.0
}

/// What a frame draws.
struct DrawerCabinetSheetFrame {
    var open = 0.0
    var topAlpha = 1.0
    var chip = 0.0
    var inside: [DrawerCabinetSheetSprite] = []
    var out: [DrawerCabinetSheetSprite] = []
}

/*
 Drawer Cabinet Sheet — the cabinet, stepped once a frame from the TimelineView (like the RN frame
 callback). In the loop the drawer's opening and the items' lags come from the script and the
 table; under touch the drawer follows the finger or an animation (soft-close timing, a spring
 into a detent) and every item's spring is stepped live with the drawer's acceleration.
*/
@MainActor
final class DrawerCabinetSheetEngine {
    private typealias M = DrawerCabinetSheetMotion
    enum Mode { case auto, user, back }
    enum Place { case drawer, flyingOut, desk, flyingBack }

    static let idle = 2.0
    static let returnTime = 0.9
    /// The auto loop's pick: the blue fineliner.
    static let pen = 0

    var reduced = false
    /// 0: a light tap (pick, detent), 1: a soft thud (closing).
    var onFeel: ((Int) -> Void)?
    var onChip: ((String) -> Void)?

    private(set) var L: DrawerCabinetSheetLayout?
    private(set) var items: [DrawerCabinetSheetItem] = []
    private var table: [[Double]] = []

    private(set) var frame = DrawerCabinetSheetFrame()
    private(set) var moving = false
    private var mode = Mode.auto
    private var clock = 0.0
    private var now = 0.0
    private var last: Date?
    private var lastTouch = 0.0
    private(set) var dragging = false
    private var dragFrom = 0.0
    private var returnAt = 0.0

    // The drawer's opening under touch: a value, or an animation toward one.
    private enum Anim { case none, timing(from: Double, to: Double, start: Double, dur: Double, soft: Bool), spring(from: Double, to: Double, v0: Double, start: Double) }
    private var openValue = 0.0
    private var anim = Anim.none

    // Per item.
    private var place: [Place] = []
    private var start: [Double] = []
    private var from: [(Double, Double, Double)] = []
    private var slot: [Int] = []
    private var lag: [(Double, Double)] = []
    private var prevOpen = 0.0
    private var prevV = 0.0
    private var acc = 0.0
    private var chipAt = -10.0

    func configure(_ layout: DrawerCabinetSheetLayout) {
        if L == layout { return }
        L = layout
        items = DrawerCabinetSheetArt.items(layout.iw)
        table = M.buildTable(items.map(\.spring), layout.half, layout.full)
        mode = .auto
        clock = 0
        openValue = 0
        anim = .none
        resetSim()
    }

    private func resetSim() {
        let n = items.count
        place = Array(repeating: .drawer, count: n)
        start = Array(repeating: 0, count: n)
        from = Array(repeating: (0, 0, 0), count: n)
        slot = Array(repeating: 0, count: n)
        lag = Array(repeating: (0, 0), count: n)
        prevOpen = openNow()
        prevV = 0
        acc = 0
        chipAt = -10
    }

    // MARK: - Opening

    private func openNow() -> Double {
        switch anim {
        case .none:
            return openValue
        case let .timing(f, t, s, d, soft):
            let u = min(1, max(0, (now - s) / d))
            // Reanimated's default timing curve is ease-in-out quad.
            let e = soft ? M.soft(u) : (u < 0.5 ? 2 * u * u : 1 - (-2 * u + 2) * (-2 * u + 2) / 2)
            if u >= 1 { anim = .none; openValue = t; return t }
            return f + (t - f) * e
        case let .spring(f, t, v0, s):
            // ζ 0.72, settling in ~0.5 s.
            let k = now - s
            if k > 1.2 { anim = .none; openValue = t; return t }
            let w0 = 12.0
            let z = 0.72
            let wd = w0 * (1 - z * z).squareRoot()
            let a = f - t
            let b = (v0 + z * w0 * a) / wd
            return t + exp(-z * w0 * k) * (a * cos(wd * k) + b * sin(wd * k))
        }
    }

    private func setOpen(_ v: Double) {
        anim = .none
        openValue = v
    }

    private func settleTo(_ target: Double, velocity: Double) {
        let o = openNow()
        if target <= 0 {
            anim = .timing(from: o, to: 0, start: now, dur: reduced ? 0.25 : 0.85, soft: true)
        } else if reduced {
            anim = .timing(from: o, to: target, start: now, dur: 0.25, soft: false)
        } else {
            anim = .spring(from: o, to: target, v0: velocity, start: now)
        }
    }

    // MARK: - Places

    /// Item i's place in the drawer on screen, for an opening and a lag.
    private func homeOf(_ i: Int, _ o: Double, _ lg: Double) -> (Double, Double, Double) {
        let L = L!
        let it = items[i]
        return (L.ix + it.x, L.boardY - o + it.y + lg, it.rot + lg / it.spring.slack * it.spring.turn)
    }

    /// Where item i lands on the desk: long things stacked on the right, small ones in a row.
    private func deskOf(_ i: Int, _ s: Int) -> (Double, Double, Double) {
        let L = L!
        let sd = Double(s)
        if items[i].w > 100 { return (L.W * 0.6, L.top + 172 + sd * 30, -0.12 + sd * 0.05) }
        return (70 + Double(s % 4) * 72, L.top + 242 + Double(s / 4) * 54, 0.2 - sd * 0.1)
    }

    private func nextSlot(_ i: Int) -> Int {
        let long = items[i].w > 100
        var k = 0
        for j in items.indices where j != i && place[j] != .drawer && place[j] != .flyingBack && (items[j].w > 100) == long { k += 1 }
        return k
    }

    private func touched() {
        guard let L else { return }
        lastTouch = now
        if mode == .user { return }
        if mode == .auto {
            let t = clock
            setOpen(M.autoOpen(t, L.half, L.full))
            resetSim()
            for i in items.indices {
                let y = M.tableAt(table[i], t)
                lag[i] = (y, (y - M.tableAt(table[i], t - 1 / Double(M.rate))) * Double(M.rate))
            }
            if t >= M.pickAt && t < M.clearAt {
                let p = Self.pen
                from[p] = homeOf(p, L.full, 0)
                place[p] = t < M.pickAt + M.liftFor + M.flyFor ? .flyingOut : .desk
                start[p] = now - (t - M.pickAt)
                slot[p] = 0
            }
        }
        mode = .user
    }

    private func pick(_ i: Int) {
        from[i] = homeOf(i, openNow(), lag[i].0)
        slot[i] = nextSlot(i)
        place[i] = .flyingOut
        start[i] = now - (reduced ? 10 : 0)
        onChip?(items[i].name)
        onFeel?(0)
    }

    private func putBack(_ i: Int, _ pose: (Double, Double, Double)) {
        from[i] = pose
        place[i] = .flyingBack
        start[i] = now - (reduced ? 10 : 0)
    }

    // MARK: - Frame

    func step(to date: Date, auto: Bool) -> DrawerCabinetSheetFrame {
        guard let L, !items.isEmpty else { return frame }
        let dt = min(max(last.map { date.timeIntervalSince($0) } ?? 0, 0), 0.05)
        last = date
        let at = now + dt
        now = at
        let n = items.count
        var o = 0.0
        var topAlpha = 1.0
        var chip = 0.0
        var lags = [Double](repeating: 0, count: n)
        var out: [DrawerCabinetSheetSprite] = []
        var mv = true

        if mode == .auto {
            mv = auto
            let t = auto ? (clock + dt).truncatingRemainder(dividingBy: DrawerCabinetSheet.loop) : 0
            clock = t
            o = M.autoOpen(t, L.half, L.full)
            for i in 0..<n { lags[i] = reduced ? 0 : M.tableAt(table[i], t) }
            if t >= M.pickAt && t < M.clearAt + 0.5 {
                let p = Self.pen
                let h = homeOf(p, L.full, 0)
                let d = deskOf(p, 0)
                let q = M.pickPose(h.0, h.1, h.2, d.0, d.1, d.2, t - M.pickAt)
                out.append(DrawerCabinetSheetSprite(i: p, x: q.x, y: q.y, rot: q.rot, lift: q.lift, scale: q.scale))
                topAlpha = 1 - M.easeOutCubic((t - M.clearAt) / 0.5)
            }
            chip = M.clamp01((t - 4.95) / 0.25) * (1 - M.clamp01((t - 6.8) / 0.25))
        } else {
            if mode == .user && auto && !dragging && at - lastTouch > Self.idle {
                // Back to rest: soft-close, everything on the desk flies home.
                anim = .timing(from: openNow(), to: 0, start: now, dur: Self.returnTime - 0.05, soft: true)
                for i in 0..<n where place[i] == .flyingOut || place[i] == .desk {
                    putBack(i, deskOf(i, slot[i]))
                }
                returnAt = at
                mode = .back
            }
            o = openNow()
            // The drawer's acceleration, smoothed, shakes the springs (four substeps a frame).
            let v = (o - prevOpen) / max(dt, 0.001)
            let a = (v - prevV) / max(dt, 0.001)
            if dt > 0 { acc += (a - acc) * min(1, dt * 30) }
            if prevOpen > 0.5 && o <= 0.5 && v < 0 && mode == .user { onFeel?(1) }
            if dt > 0 {
                prevOpen = o
                prevV = v
            }
            for i in 0..<n {
                if !reduced && dt > 0 {
                    for _ in 0..<4 { M.stepItem(items[i].spring, &lag[i], acc, dt / 4) }
                }
                lags[i] = lag[i].0
            }
            for i in 0..<n {
                let p = place[i]
                if p == .drawer { continue }
                let u = at - start[i]
                let d = deskOf(i, slot[i])
                let q: M.PickPose
                if p == .flyingOut || p == .desk {
                    q = M.pickPose(from[i].0, from[i].1, from[i].2, d.0, d.1, d.2, u)
                    if p == .flyingOut && u >= M.liftFor + M.flyFor {
                        place[i] = .desk
                        chipAt = at
                    }
                } else {
                    let h = homeOf(i, o, lag[i].0)
                    q = M.pickPose(from[i].0, from[i].1, from[i].2, h.0, h.1, h.2, u)
                    if u >= M.liftFor + M.flyFor {
                        place[i] = .drawer
                        lag[i] = (0, 0)
                        lags[i] = 0
                        continue
                    }
                }
                out.append(DrawerCabinetSheetSprite(i: i, x: q.x, y: q.y, rot: q.rot, lift: q.lift, scale: q.scale))
            }
            chip = M.clamp01((at - chipAt) / 0.25) * (1 - M.clamp01((at - chipAt - 2.4) / 0.25))
            // Still when the drawer, the springs and every flight have settled.
            mv = mode == .back || dragging || abs(v) > 0.05 || abs(acc) > 0.5 || (at - chipAt > 0 && at - chipAt < 2.8)
            for i in 0..<n {
                if abs(lag[i].0) > 0.02 || abs(lag[i].1) > 0.05 || place[i] == .flyingOut || place[i] == .flyingBack { mv = true }
                if place[i] == .desk && at - start[i] < M.liftFor + M.flyFor + 0.6 { mv = true }
            }
            if case .none = anim {} else { mv = true }
            if mode == .back && at - returnAt >= Self.returnTime {
                mode = .auto
                clock = 0
                setOpen(0)
                resetSim()
                onChip?("Blue fineliner")
            }
        }

        // In the drawer: every item not out on the desk.
        var isOut = [Bool](repeating: false, count: n)
        for s in out { isOut[s.i] = true }
        var inside: [DrawerCabinetSheetSprite] = []
        inside.reserveCapacity(n)
        for i in 0..<n where !isOut[i] {
            let h = homeOf(i, o, lags[i])
            inside.append(DrawerCabinetSheetSprite(i: i, x: h.0, y: h.1, rot: h.2))
        }
        frame = DrawerCabinetSheetFrame(open: o, topAlpha: topAlpha, chip: chip, inside: inside, out: out)
        moving = mv
        return frame
    }

    // MARK: - Touch

    /// The topmost item under a point: on the desk first, then in the open part of the drawer.
    private func hitItem(_ x: Double, _ y: Double) -> (i: Int, desk: Bool, pose: (Double, Double, Double))? {
        guard let L else { return nil }
        func inside(_ pose: (Double, Double, Double), _ it: DrawerCabinetSheetItem, _ padX: Double, _ padY: Double) -> Bool {
            let dx = x - pose.0
            let dy = y - pose.1
            let lx = dx * cos(pose.2) + dy * sin(pose.2)
            let ly = -dx * sin(pose.2) + dy * cos(pose.2)
            return abs(lx) < it.w / 2 + padX && abs(ly) < it.h / 2 + padY
        }
        for i in items.indices.reversed() where place[i] == .desk {
            let pose = deskOf(i, slot[i])
            if inside(pose, items[i], 10, 12) { return (i, true, pose) }
        }
        let o = openNow()
        if y > L.boardY || y < L.boardY - o { return nil }
        for i in items.indices.reversed() where place[i] == .drawer {
            let pose = homeOf(i, o, lag[i].0)
            if inside(pose, items[i], 8, 10) { return (i, false, pose) }
        }
        return nil
    }

    func tap(_ x: Double, _ y: Double) {
        guard let L else { return }
        touched()
        if let h = hitItem(x, y) {
            if h.desk { putBack(h.i, h.pose) } else { pick(h.i) }
            return
        }
        let o = openNow()
        let top = L.boardY - o - DrawerCabinetSheetLayout.panel
        if y >= top && y <= top + DrawerCabinetSheetLayout.panel {
            settleTo(o < 10 ? L.half : 0, velocity: 0)
            onFeel?(0)
        }
    }

    /// Starts a drag at y0 if it began on the drawer; returns whether it did.
    func dragBegan(atY y0: Double) -> Bool {
        guard let L else { return false }
        let top = L.boardY - openNow() - DrawerCabinetSheetLayout.panel
        if y0 < top - 12 || y0 > L.boardY + 40 { return false }
        touched()
        dragging = true
        let o = openNow()
        setOpen(o)
        dragFrom = o
        return true
    }

    func dragMoved(translationY: Double) {
        guard let L, dragging else { return }
        lastTouch = now
        var o = dragFrom - translationY
        if o > L.full {
            let over = o - L.full
            o = L.full + over * 0.55 / (1 + over / 120)
        } else if o < 0 {
            o *= 0.2
        }
        setOpen(o)
    }

    func dragEnded(velocityY: Double) {
        guard let L, dragging else { return }
        dragging = false
        lastTouch = now
        let o = openNow()
        let aim = o - velocityY * 0.18
        var best = 0.0
        for p in [0, L.half, L.full] where abs(aim - p) < abs(aim - best) { best = p }
        settleTo(best, velocity: -velocityY)
        if best > 0 { onFeel?(0) }
    }
}
