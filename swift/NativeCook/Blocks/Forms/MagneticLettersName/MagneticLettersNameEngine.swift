import Foundation

/*
 Magnetic Letters Name — the door under a finger and the loop, stepped once a frame from the
 TimelineView. Each magnet keeps one flight record (kind, from, to, start, duration); its pose at
 any time comes from the curves in MagneticLettersNameMotion, so a flight can be interrupted by
 another from wherever it is. `now` is seconds on the frame clock.
*/
@MainActor
final class MagneticLettersNameEngine {
    typealias Pose = MagneticLettersNamePose
    typealias Kind = MagneticLettersNameMotion.Kind
    private typealias Mo = MagneticLettersNameMotion

    enum Mode { case rest, user, back }
    enum Focus { case none, show, hide }

    /// Seconds after the last touch before the loop takes the door back, and how long that takes.
    static let idle = 2.0
    static let returnTime = 0.9
    /// A shifted letter slides along the line in this long.
    static let shift = 0.28

    private(set) var L: MagneticLettersNameLayout?
    private var scriptOf: [Int] = []
    private var autoSlots: [Pose] = []
    private(set) var maxFit = 10
    var reduced = false

    private var kind: [Kind] = []
    private var from: [Pose] = []
    private var to: [Pose] = []
    private var start: [Double] = []
    private var dur: [Double] = []
    private var home: [Pose] = []
    private(set) var poses: [Pose] = []
    private var name: [Int] = []
    private var held = -1
    private var heldX = 0.0
    private var heldY = 0.0
    private var grabX = 0.0
    private var grabY = 0.0
    private var vx = 0.0
    private var readyValue = 0.0

    private var mode = Mode.rest
    private var clock = 0.0
    private(set) var now = 0.0
    private var last: Date?
    private var lastTouch = 0.0
    private var returnEnd = 0.0

    /// What the overlays show this frame.
    private(set) var ready = 0.0
    private(set) var count = 0
    /// Whether anything moved in the last step (the view pauses its timeline when not).
    private(set) var moving = false

    /// The line's letters changed (the hidden text field mirrors them).
    var onText: ((String) -> Void)?
    /// A magnet snapped onto the line under touch.
    var onSnap: (() -> Void)?

    func configure(_ layout: MagneticLettersNameLayout) {
        if let L, L.W == layout.W, L.H == layout.H, L.top == layout.top { return }
        L = layout
        maxFit = layout.maxFit
        scriptOf = layout.magnets.indices.map { layout.script.firstIndex(of: $0) ?? -1 }
        let xs = layout.slotXs(layout.script)
        autoSlots = xs.map { Pose(x: $0, y: layout.letterY, rot: 0, lift: 0, scale: 1) }
        let n = layout.magnets.count
        kind = Array(repeating: .rest, count: n)
        home = layout.magnets.map(\.home)
        from = home
        to = home
        poses = home
        start = Array(repeating: 0, count: n)
        dur = Array(repeating: 1, count: n)
        name = []
        held = -1
        readyValue = 0
        mode = .rest
        clock = 0
    }

    // MARK: - Frame

    func step(to date: Date, auto: Bool) {
        guard let L else { return }
        // A slow frame (or a pause) never skips the loop ahead.
        let dt = min(max(last.map { date.timeIntervalSince($0) } ?? 0, 0), 0.05)
        last = date
        let before = now
        let at = before + dt
        now = at
        var mv = false
        var rdy = 0.0
        var cnt = 0

        if mode == .rest {
            let t = auto ? (clock + dt).truncatingRemainder(dividingBy: MagneticLettersName.loop) : 0
            clock = t
            for i in L.magnets.indices { poses[i] = autoPose(i, t) }
            mv = auto
            rdy = Mo.autoReady(t)
            cnt = Mo.autoCount(t)
        } else {
            if mode == .user && auto && held < 0 && at - lastTouch > Self.idle { beginReturn() }
            if mode == .back && at >= returnEnd {
                mode = .rest
                clock = 0
            }
            var haptic = false
            for i in L.magnets.indices {
                let (p, m) = simPose(i, at)
                poses[i] = p
                if m { mv = true }
                if kind[i] == .snap {
                    let end = start[i] + dur[i]
                    if before < end && at >= end { haptic = true }
                }
            }
            if haptic && mode == .user { onSnap?() }
            let target = mode == .user && name.count >= 2 ? 1.0 : 0.0
            readyValue += (target - readyValue) * min(1, dt * 14)
            if abs(target - readyValue) > 0.002 { mv = true }
            rdy = readyValue
            for i in name where kind[i] != .snap || at >= start[i] + dur[i] { cnt += 1 }
        }
        ready = rdy
        count = cnt
        moving = mv || mode == .back
    }

    /// The magnets in draw order: flat ones by their place in the heap, lifted ones above by height.
    func drawOrder() -> [Int] {
        guard let L else { return [] }
        let n = L.magnets.count
        var keyed: [(Int, Double)] = []
        keyed.reserveCapacity(n)
        for r in 0..<n {
            let i = L.order[r]
            let p = poses[i]
            keyed.append((i, (p.lift > 0.01 ? 10 + p.lift : 0) + Double(r) / Double(n)))
        }
        keyed.sort { $0.1 < $1.1 }
        return keyed.map(\.0)
    }

    // MARK: - Touch

    /// Takes the door from the loop: every magnet keeps its pose and its flight's progress.
    private func touched() {
        lastTouch = now
        if mode == .user { return }
        if mode == .rest {
            takeOver(clock)
            readyValue = ready
            syncText()
        }
        mode = .user
    }

    var nameText: String {
        guard let L else { return "" }
        return String(name.map { L.magnets[$0].letter })
    }

    private func syncText() { onText?(nameText) }

    /// The keyboard's text, applied as backspaces and typed letters; the line is the truth.
    func applyText(_ next: String) {
        guard L != nil else { return }
        touched()
        let cur = Array(nameText)
        let nx = Array(next)
        var p = 0
        while p < cur.count && p < nx.count && cur[p] == nx[p] { p += 1 }
        while name.count > p { removeAt(name.count - 1, land: true) }
        for j in p..<max(p, nx.count) { typeLetter(nx[j]) }
        syncText()
    }

    func backspace() {
        touched()
        guard !name.isEmpty else { return }
        removeAt(name.count - 1, land: true)
        syncText()
    }

    func tap(_ x: Double, _ y: Double) -> Focus {
        guard let L else { return .none }
        touched()
        let i = hit(x, y)
        if i >= 0 {
            if let k = name.firstIndex(of: i) { removeAt(k, land: true) } else { typeMagnet(i) }
            syncText()
            return .none
        }
        if y > L.lineY - L.capH - 44 && y < L.lineY + 24 { return .show }
        return .hide
    }

    func grab(_ x: Double, _ y: Double) {
        touched()
        let i = hit(x, y)
        guard i >= 0 else { return }
        let wasOnLine = name.contains(i)
        grab(i, x, y)
        if wasOnLine { syncText() }
    }

    func drag(_ x: Double, _ y: Double, _ v: Double) {
        guard held >= 0 else { return }
        lastTouch = now
        heldX = x
        heldY = y
        vx = v
    }

    func dropHeld() {
        guard held >= 0 else { return }
        lastTouch = now
        if drop() { syncText() }
    }

    /// 2 s after the last touch: the line empties and every magnet flies home, staggered.
    private func beginReturn() {
        sendHome()
        syncText()
        returnEnd = now + Self.returnTime
        mode = .back
    }

    // MARK: - Poses

    /// Magnet i in the loop at loop time t.
    private func autoPose(_ i: Int, _ t: Double) -> Pose {
        let h = L!.magnets[i].home
        let k = scriptOf[i]
        if k >= 0 {
            let p = Mo.autoPhase(k, t)
            if p.phase == 1 { return Mo.snapPose(h, autoSlots[k], p.since, Mo.fly) }
            if p.phase == 2 { return Mo.landPose(autoSlots[k], h, p.since, Mo.pop) }
        }
        return Pose(x: h.x, y: h.y, rot: h.rot, lift: 0, scale: 1)
    }

    /// Magnet i under touch at time `at`, and whether it is still moving.
    private func simPose(_ i: Int, _ at: Double) -> (Pose, Bool) {
        let u = at - start[i]
        switch kind[i] {
        case .held:
            let lift = reduced ? 1 : Mo.easeOutCubic(u / 0.12)
            var out = Pose()
            out.x = heldX + grabX
            out.y = heldY + grabY
            out.rot = from[i].rot + max(-0.3, min(0.3, vx * 0.0005))
            out.lift = max(from[i].lift, lift)
            out.scale = 1 + 0.06 * out.lift
            return (out, true)
        case .snap:
            return (Mo.snapPose(from[i], to[i], u, dur[i]), u < dur[i] + 0.7)
        case .land:
            return (Mo.landPose(from[i], to[i], u, dur[i]), u < dur[i] + 0.7)
        case .drop:
            return (Mo.dropPose(from[i], u), u < dur[i] + 0.7)
        case .rest:
            let p = to[i]
            return (Pose(x: p.x, y: p.y, rot: p.rot, lift: 0, scale: 1), false)
        }
    }

    private func startFlight(_ i: Int, _ k: Kind, _ target: Pose, _ at: Double, _ d: Double) {
        from[i] = poses[i]
        to[i] = Pose(x: target.x, y: target.y, rot: target.rot, lift: 0, scale: 1)
        kind[i] = k
        start[i] = at
        dur[i] = reduced ? 0.001 : d
    }

    private func slotPose(_ k: Int) -> Pose {
        let L = L!
        var x = L.lineX0
        for j in 0..<k { x += L.magnets[name[j]].w + L.gap }
        return Pose(x: x + L.magnets[name[k]].w / 2, y: L.letterY, rot: 0, lift: 0, scale: 1)
    }

    /// Slides every letter from position k on to its slot (after an insert or a removal).
    private func relayout(_ k: Int) {
        guard k < name.count else { return }
        for j in k..<name.count where name[j] != held {
            startFlight(name[j], .snap, slotPose(j), now, Self.shift)
        }
    }

    /// Whether one more magnet of width w still fits on the line.
    private func fits(_ w: Double) -> Bool {
        let L = L!
        if name.count >= maxFit { return false }
        var x = L.lineX0
        for i in name { x += L.magnets[i].w + L.gap }
        return x + w <= L.lineX1
    }

    private func typeMagnet(_ i: Int) {
        guard fits(L!.magnets[i].w), !name.contains(i) else { return }
        name.append(i)
        startFlight(i, .snap, slotPose(name.count - 1), now, Mo.fly)
    }

    private func removeAt(_ k: Int, land: Bool) {
        let m = name.remove(at: k)
        if land { startFlight(m, .land, home[m], now, Mo.pop) }
        relayout(k)
    }

    private func typeLetter(_ ch: Character) {
        guard let L else { return }
        for i in L.magnets.indices where L.magnets[i].letter == ch && !name.contains(i) && held != i {
            typeMagnet(i)
            return
        }
    }

    /// The topmost magnet under a point, or -1.
    private func hit(_ x: Double, _ y: Double) -> Int {
        guard let L else { return -1 }
        var best = -1
        var bestKey = -1.0
        for i in L.magnets.indices {
            let p = poses[i]
            let dx = x - p.x
            let dy = y - p.y
            let r = L.magnets[i].r + 6
            if dx * dx + dy * dy < r * r {
                let key = p.lift * 10 + p.y / 10000 + 1
                if key > bestKey { bestKey = key; best = i }
            }
        }
        return best
    }

    /// Copies the loop's state at loop time t into the records, so a touch continues from it.
    private func takeOver(_ t: Double) {
        let L = L!
        name = []
        held = -1
        let at = now
        for i in L.magnets.indices {
            let h = L.magnets[i].home
            home[i] = h
            kind[i] = .rest
            to[i] = h
            let k = scriptOf[i]
            if k < 0 { continue }
            let p = Mo.autoPhase(k, t)
            if p.phase == 1 {
                kind[i] = .snap; from[i] = h; to[i] = autoSlots[k]
                start[i] = at - p.since; dur[i] = Mo.fly
            } else if p.phase == 2 && p.since < Mo.pop + 0.7 {
                kind[i] = .land; from[i] = autoSlots[k]; to[i] = h
                start[i] = at - p.since; dur[i] = Mo.pop
            }
        }
        // The line holds the script letters that have left the heap and not yet popped, in order.
        for k in L.script.indices where Mo.autoPhase(k, t).phase == 1 { name.append(L.script[k]) }
    }

    /// Picks up magnet i under the finger at (x, y); a letter taken off the line closes the gap.
    private func grab(_ i: Int, _ x: Double, _ y: Double) {
        if let k = name.firstIndex(of: i) {
            name.remove(at: k)
            relayout(k)
        }
        let cur = poses[i]
        from[i] = cur
        kind[i] = .held
        start[i] = now
        held = i
        heldX = x; heldY = y
        grabX = cur.x - x; grabY = cur.y - y
        vx = 0
    }

    /// Lets go of the held magnet: onto the line where it was dropped, or back down onto the steel.
    private func drop() -> Bool {
        let L = L!
        let i = held
        guard i >= 0 else { return false }
        held = -1
        let p = poses[i]
        if abs(p.y - L.letterY) < 56 && fits(L.magnets[i].w) {
            var k = 0
            while k < name.count && slotPose(k).x < p.x { k += 1 }
            name.insert(i, at: k)
            startFlight(i, .snap, slotPose(k), now, Mo.fly * 0.8)
            relayout(k + 1)
            return true
        }
        let x = min(L.W - 40, max(24, p.x))
        let y = min(L.pile.y1, max(L.lineY + 30, p.y))
        home[i] = Pose(x: x, y: y, rot: p.rot, lift: 0, scale: 1)
        from[i] = Pose(x: x, y: y, rot: p.rot, lift: p.lift, scale: p.scale)
        to[i] = home[i]
        kind[i] = .drop
        start[i] = now
        dur[i] = Mo.drop
        return false
    }

    /// The line empties and every magnet away from its home flies back, staggered (within 0.85 s).
    private func sendHome() {
        let L = L!
        name = []
        held = -1
        var j = 0
        for i in L.order {
            let h = L.magnets[i].home
            let p = poses[i]
            home[i] = h
            let far = abs(p.x - h.x) + abs(p.y - h.y) + abs(p.rot - h.rot) * 20 + p.lift * 20 > 0.5
            if far {
                startFlight(i, .land, h, now + min(0.4, Double(j) * 0.04), Mo.pop)
                j += 1
            } else {
                kind[i] = .rest
                to[i] = h
            }
        }
    }
}
