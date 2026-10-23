import Foundation

/// Where the board, the well and the figures along the bottom sit (pt).
struct TangramSnapLayout: Equatable {
    var unit = 0.0
    var boardX = 0.0
    var boardY = 0.0
    var looseX = 0.0
    var looseY = 0.0
    var thumbY = 0.0
    var width = 0.0
    var height = 0.0
    var insetTop = 0.0

    init() {}

    init(width: Double, height: Double, insetTop: Double, insetBottom: Double) {
        let m = TangramSnapModel.self
        let headerBottom = insetTop + 104
        let thumbY = height - insetBottom - 38
        let room = thumbY - 34 - headerBottom
        // The board and the well stacked between the header and the figures, as large as fits.
        let unit = max(
            20,
            min((width - 26) / (m.looseHalfW * 2 + 0.9), (width - 40) / (m.maxHalfW * 2), room / (m.maxHalfH * 2 + m.looseHalfH * 2 + 1.4))
        )
        let boardY = headerBottom + m.maxHalfH * unit
        let looseY = (boardY + m.maxHalfH * unit + 0.5 * unit + thumbY - 34) / 2
        self.unit = unit
        boardX = width / 2
        self.boardY = boardY
        looseX = width / 2 + 0.05 * unit
        self.looseY = looseY
        self.thumbY = thumbY
        self.width = width
        self.height = height
        self.insetTop = insetTop
    }
}

/// The pieces, the figure and its morphs, the autoPlay script and the gestures, stepped like the
/// React Native frame callback: whole ticks of 1/60 s, at most three a frame. Gestures arrive as
/// counters with their data, exactly as React Native's shared values carry them, and each tick
/// handles every new one once. Foundation only, so it also runs in a command-line parity check.
final class TangramSnapEngine {
    private typealias M = TangramSnapModel

    // One fixed clock for the springs and the autoPlay script.
    static let tick = 1.0 / 60
    static let maxTicks = 3
    static let quarter = Double.pi / 4
    // A piece within snap of its slot (pt) at the slot's turn snaps in on release; within magnet
    // it is already pulled towards it while held, at most magnetPull of the way.
    static let snap = 34.0
    static let magnet = 64.0
    static let magnetPull = 0.7
    // A turn within this of the slot's (rad) still counts as matching.
    static let turnSlack = 0.3
    // Seconds of speed a release carries a piece on.
    static let throwTime = 0.09
    // Pieces flying home leave one after another, this far apart (s), lifted for liftTime s.
    static let scatterGap = 0.06
    static let liftTime = 0.28
    // Solving: the colour floods out from the last piece over fillTime s.
    static let fillTime = 1.1
    // A user's next puzzle: the solved figure turns into it, holds, then comes apart.
    static let nextHold = 1.1
    // After a touch, autoPlay waits this long, then takes the figure apart and starts again.
    static let userHold = 5.0
    // Gestures: a pan starts after panSlop pt; a tap is shorter than tapTime s; a second tap within
    // doubleGap s makes a double tap (a single tap waits that long for it); two fingers start a
    // turn once they have turned turnStart rad.
    static let panSlop = 2.0
    static let tapTime = 0.5
    static let doubleGap = 0.26
    static let turnStart = 0.03

    // Haptic requests, read and cleared by the view: light (snap), success, selection, soft.
    static let buzzSnap = 1
    static let buzzSolved = 2
    static let buzzSelect = 4
    static let buzzSoft = 8
    var events = 0

    let reduced: Bool
    private var looping: Bool
    private(set) var layout = TangramSnapLayout()

    // The scene.
    private(set) var pieces = TangramSnapPieces()
    private var rest = TangramSnapPieces()
    private(set) var ready = false
    /// The silhouette shown, and the morph to another (0…1; 1 when settled).
    private(set) var puzzle = 0
    private(set) var from = 0
    private(set) var morph = 1.0
    /// Slot poses at the morph's start and end (pt, rad, flip).
    private var ax = [Double](repeating: 0, count: 7)
    private var ay = [Double](repeating: 0, count: 7)
    private var aa = [Double](repeating: 0, count: 7)
    private var af = [Double](repeating: 0, count: 7)
    private var bx = [Double](repeating: 0, count: 7)
    private var by = [Double](repeating: 0, count: 7)
    private var ba = [Double](repeating: 0, count: 7)
    private var bf = [Double](repeating: 0, count: 7)
    /// Pieces that ride the morph in their slots, with their own start and end turns.
    private var ride = [Bool](repeating: false, count: 7)
    private var pa = [Double](repeating: 0, count: 7)
    private var pf = [Double](repeating: 0, count: 7)
    private var qa = [Double](repeating: 0, count: 7)
    private var qf = [Double](repeating: 0, count: 7)
    /// Solving: whether every piece is in, and the colour's flood (0…1) and where it starts.
    private(set) var solved = false
    private(set) var fill = 0.0
    private(set) var revealX = 0.0
    private(set) var revealY = 0.0
    /// Per piece: seconds until it flies home (-1 none), and seconds left lifted.
    private var scatter = [Double](repeating: -1, count: 7)
    private var lifted = [Double](repeating: 0, count: 7)
    /// Per slot: seconds since a piece snapped in (for the pulse), -1 none.
    private(set) var pulse = [Double](repeating: -1, count: 7)
    /// The piece in the finger (-1), where it was grabbed, and the slot it is drawn towards.
    private(set) var held = -1
    private var grabX = 0.0
    private var grabY = 0.0
    private(set) var magnetSlot = -1
    /// The piece two fingers turn, and its turn when they landed.
    private var turning = -1
    private var turnFrom = 0.0
    /// The piece the script is carrying, from where, to which slot.
    private var carry = -1
    private var carryX = 0.0
    private var carryY = 0.0
    private var carryA0 = 0.0
    private var carryA = 0.0
    private var carryF = 0.0
    private var carryAt = 0.0
    /// A user's next puzzle: seconds until the solved figure comes apart (-1 none).
    private var apartIn = -1.0
    /// Gesture events already handled.
    private var seen = [0, 0, 0, 0, 0, 0]

    private var acc = 0.0
    private(set) var clock = 0.0
    private(set) var hold = 0.0

    // Gesture events, as counters with their data (React Native's shared values).
    private(set) var downN = 0
    private var downX = 0.0
    private var downY = 0.0
    private var fingerX = 0.0
    private var fingerY = 0.0
    private(set) var upN = 0
    private var upVX = 0.0
    private var upVY = 0.0
    private(set) var tapN = 0
    private var tapX = 0.0
    private var tapY = 0.0
    private(set) var dblN = 0
    private var dblX = 0.0
    private var dblY = 0.0
    private(set) var turnN = 0
    private(set) var turnEndN = 0
    private var turnX = 0.0
    private var turnY = 0.0
    private var turnBy = 0.0

    /// `auto` is passed in here so the very first frame is already the script's first frame.
    init(auto: Bool, reduced: Bool) {
        looping = auto
        self.reduced = reduced
    }

    func setAuto(_ auto: Bool) {
        guard auto != looping else { return }
        looping = auto
        hold = 0
        clock = 0
    }

    /// A new size starts the board over.
    func setLayout(width: Double, height: Double, insetTop: Double, insetBottom: Double) {
        let l = TangramSnapLayout(width: width, height: height, insetTop: insetTop, insetBottom: insetBottom)
        if l == layout { return }
        layout = l
        ready = false
    }

    var placedCount: Int { pieces.slot.filter { $0 >= 0 }.count }

    // MARK: Slots

    /// Slot `k` of silhouette `p` on screen, at its first listed pose.
    func slotAt(_ p: Int, _ k: Int) -> (x: Double, y: Double, angle: Double, flip: Double) {
        let slot = M.silhouettes[p].slots[k]
        return (layout.boardX + slot.x * layout.unit, layout.boardY + slot.y * layout.unit, (Double(slot.turns[0]) * Double.pi) / 4, Double(slot.flips[0]))
    }

    static func easeInOut(_ t: Double) -> Double {
        let u = min(1, max(0, t))
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    static func smooth(_ t: Double) -> Double {
        let u = min(1, max(0, t))
        return u * u * (3 - 2 * u)
    }

    /// The morph's eased progress.
    var morphEase: Double { reduced ? TangramSnapEngine.smooth(morph) : TangramSnapEngine.easeInOut(morph) }

    /// Where slot `k` is drawn now: settled, or partway through a morph.
    func slotNow(_ k: Int) -> (x: Double, y: Double, angle: Double, flip: Double) {
        if morph >= 1 { return slotAt(puzzle, k) }
        let e = morphEase
        return (ax[k] + (bx[k] - ax[k]) * e, ay[k] + (by[k] - ay[k]) * e, aa[k] + (ba[k] - aa[k]) * e, af[k] + (bf[k] - af[k]) * e)
    }

    /// Corners of shape `k` at a pose, with the flip drawn as a squeeze of the local x (cos π·flip).
    static func corners(_ k: Int, _ x: Double, _ y: Double, _ angle: Double, _ flip: Double, _ unit: Double, _ grow: Double) -> [Double] {
        let shape = M.shapes[k]
        let c = cos(angle)
        let s = sin(angle)
        let sx = cos(Double.pi * flip)
        var out: [Double] = []
        out.reserveCapacity(shape.count)
        for i in stride(from: 0, to: shape.count, by: 2) {
            let lx = shape[i] * unit * grow * sx
            let ly = shape[i + 1] * unit * grow
            out.append(x + lx * c - ly * s)
            out.append(y + lx * s + ly * c)
        }
        return out
    }

    // MARK: Moves

    /// Sends piece `i` home to the well, after `delay` s.
    private func sendHome(_ i: Int, _ delay: Double) {
        pieces.slot[i] = -1
        scatter[i] = delay
    }

    /// Takes piece `i` out of its slot (the figure is no longer whole).
    private func unsnap(_ i: Int) {
        if pieces.slot[i] < 0 { return }
        pieces.slot[i] = -1
        if solved { solved = false }
    }

    /// Snaps piece `i` into a matching free slot near (x, y) at its turn, if there is one.
    @discardableResult
    private func trySnap(_ i: Int, _ x: Double, _ y: Double, _ user: Bool) -> Bool {
        if morph < 1 { return false }
        let p = pieces
        let sil = M.silhouettes[puzzle]
        let flip = p.tf[i] > 0.5 ? 1 : 0
        var best = -1
        var bestD = TangramSnapEngine.snap
        for k in 0..<sil.slots.count {
            if M.pieces[k] != M.pieces[i] { continue }
            var taken = false
            for j in 0..<p.slot.count where j != i && p.slot[j] == k { taken = true }
            if taken || !M.turnMatches(sil.slots[k], p.ta[i], flip, TangramSnapEngine.turnSlack) { continue }
            let at = slotAt(puzzle, k)
            let d = hypot(at.x - x, at.y - y)
            if d < bestD {
                bestD = d
                best = k
            }
        }
        if best < 0 { return false }
        guard let pose = M.slotPose(sil.slots[best], p.ta[i], flip, false) else { return false }
        let at = slotAt(puzzle, best)
        p.slot[i] = best
        p.mode[i] = 2
        p.tx[i] = at.x
        p.ty[i] = at.y
        p.ta[i] = pose.angle
        p.tl[i] = 0
        pulse[best] = 0
        if user { events |= TangramSnapEngine.buzzSnap }
        // Every piece in: the figure is whole.
        let all = !p.slot.contains { $0 < 0 }
        if all && !solved {
            solved = true
            revealX = at.x
            revealY = at.y
            if user { events |= TangramSnapEngine.buzzSolved }
        }
        return true
    }

    /// Morphs to silhouette `to`. A solved figure carries its pieces along; otherwise they go home.
    private func startMorph(_ to: Int, _ keepSolved: Bool) {
        let p = pieces
        let previous = puzzle
        for k in 0..<7 {
            let a = slotNow(k)
            ax[k] = a.x
            ay[k] = a.y
            aa[k] = a.angle
            af[k] = a.flip
            let b = slotAt(to, k)
            let pose = M.slotPose(M.silhouettes[to].slots[k], a.angle, a.flip > 0.5 ? 1 : 0, true)!
            bx[k] = b.x
            by[k] = b.y
            ba[k] = pose.angle
            bf[k] = Double(pose.flip)
        }
        let carrying = keepSolved && solved
        for i in 0..<7 {
            ride[i] = false
            if carrying && p.slot[i] >= 0 {
                let k = p.slot[i]
                ride[i] = true
                pa[i] = p.ta[i]
                pf[i] = p.tf[i]
                let pose = M.slotPose(M.silhouettes[to].slots[k], p.ta[i], p.tf[i] > 0.5 ? 1 : 0, true)!
                qa[i] = pose.angle
                qf[i] = Double(pose.flip)
                p.mode[i] = 1
            } else {
                unsnap(i)
                sendHome(i, TangramSnapEngine.scatterGap * Double(i))
            }
        }
        if !carrying && solved { solved = false }
        from = previous
        puzzle = to
        morph = to == previous && !carrying ? 1 : 0
    }

    /// The topmost piece under (x, y), or the nearest within reach.
    private func pieceAt(_ x: Double, _ y: Double) -> Int {
        let p = pieces
        let unit = layout.unit
        for k in stride(from: p.order.count - 1, through: 0, by: -1) {
            let i = p.order[k]
            if M.insideConvex(TangramSnapEngine.corners(i, p.x[i], p.y[i], p.angle[i], p.flip[i], unit, 1.08), x, y) { return i }
        }
        var best = -1
        var bestD = unit * 0.55
        for i in 0..<7 {
            let d = hypot(p.x[i] - x, p.y[i] - y)
            if d < bestD {
                bestD = d
                best = i
            }
        }
        return best
    }

    /// Lets go of the scripted piece where it is (a touch interrupts the script).
    private func dropCarry() {
        let i = carry
        if i < 0 { return }
        let p = pieces
        let q = TangramSnapEngine.quarter
        p.mode[i] = 0
        p.tx[i] = p.x[i]
        p.ty[i] = p.y[i]
        p.ta[i] = tangramSnapRound(p.ta[i] / q) * q
        p.tf[i] = p.tf[i] > 0.5 ? 1 : 0
        p.tl[i] = 0
        carry = -1
    }

    private func touched() {
        hold = TangramSnapEngine.userHold
        dropCarry()
    }

    /// Gesture events, in the order React Native handles them.
    private func handleInput() {
        let p = pieces
        let q = TangramSnapEngine.quarter
        let unit = layout.unit
        if downN != seen[0] {
            seen[0] = downN
            touched()
            let i = pieceAt(downX, downY)
            if i >= 0 && morph >= 1 {
                unsnap(i)
                scatter[i] = -1
                held = i
                grabX = p.x[i] - downX
                grabY = p.y[i] - downY
                fingerX = downX
                fingerY = downY
                p.mode[i] = 1
                p.tl[i] = 1
                p.raise(i)
            }
        }
        if turnN != seen[3] {
            seen[3] = turnN
            touched()
            // Not during a morph: a piece riding it must keep its slot.
            let i = morph < 1 ? -1 : held >= 0 ? held : pieceAt(turnX, turnY)
            turning = i
            if i >= 0 {
                unsnap(i)
                turnFrom = p.ta[i]
            }
        }
        if turning >= 0 {
            p.ta[turning] = turnFrom + (reduced ? 0 : 1) * turnBy
            if reduced { p.ta[turning] = turnFrom + tangramSnapRound(turnBy / q) * q }
        }
        if turnEndN != seen[4] {
            seen[4] = turnEndN
            let i = turning
            turning = -1
            if i >= 0 {
                p.ta[i] = tangramSnapRound(p.ta[i] / q) * q
                events |= TangramSnapEngine.buzzSelect
                if held != i { trySnap(i, p.tx[i], p.ty[i], true) }
            }
        }
        if upN != seen[1] {
            seen[1] = upN
            let i = held
            held = -1
            magnetSlot = -1
            if i >= 0 {
                p.mode[i] = 0
                p.tl[i] = 0
                if turning != i { p.ta[i] = tangramSnapRound(p.ta[i] / q) * q }
                // The finger's speed carries on into the spring, and decides where it settles.
                let vx = reduced ? 0 : upVX
                let vy = reduced ? 0 : upVY
                p.vx[i] = vx
                p.vy[i] = vy
                let gx = p.tx[i] + vx * TangramSnapEngine.throwTime
                let gy = p.ty[i] + vy * TangramSnapEngine.throwTime
                if !trySnap(i, p.tx[i], p.ty[i], true) && !trySnap(i, gx, gy, true) {
                    let m = unit * 0.6
                    p.tx[i] = min(layout.width - m, max(m, gx))
                    p.ty[i] = min(layout.thumbY - 30, max(layout.insetTop + 40, gy))
                }
            }
        }
        if tapN != seen[2] {
            seen[2] = tapN
            touched()
            // The figures along the bottom: tap one to morph into it.
            if abs(tapY - layout.thumbY) < 30 {
                let k = Int(tangramSnapRound((tapX - layout.width / 2) / 64 + 1.5))
                if k >= 0 && k < M.silhouettes.count && abs(tapX - (layout.width / 2 + (Double(k) - 1.5) * 64)) < 30 && k != puzzle && morph >= 1 {
                    let wasSolved = solved
                    startMorph(k, true)
                    if wasSolved { apartIn = M.morphTime + TangramSnapEngine.nextHold }
                    events |= TangramSnapEngine.buzzSoft
                }
            } else {
                let i = pieceAt(tapX, tapY)
                if i >= 0 && morph >= 1 {
                    unsnap(i)
                    p.ta[i] = tangramSnapRound(p.ta[i] / q) * q + q
                    p.raise(i)
                    events |= TangramSnapEngine.buzzSelect
                    trySnap(i, p.tx[i], p.ty[i], true)
                }
            }
        }
        if dblN != seen[5] {
            seen[5] = dblN
            touched()
            let i = pieceAt(dblX, dblY)
            if i >= 0 && morph >= 1 {
                unsnap(i)
                p.tf[i] = p.tf[i] > 0.5 ? 0 : 1
                p.raise(i)
                events |= TangramSnapEngine.buzzSelect
                trySnap(i, p.tx[i], p.ty[i], true)
            }
        }
        // The held piece follows the finger, pulled towards a matching slot when it is close.
        if held >= 0 {
            let i = held
            var tx = fingerX + grabX
            var ty = fingerY + grabY
            magnetSlot = -1
            let sil = M.silhouettes[puzzle]
            let flip = p.tf[i] > 0.5 ? 1 : 0
            var k = 0
            while k < sil.slots.count && morph >= 1 {
                defer { k += 1 }
                if M.pieces[k] != M.pieces[i] || !M.turnMatches(sil.slots[k], p.ta[i], flip, TangramSnapEngine.turnSlack) { continue }
                var taken = false
                for j in 0..<7 where j != i && p.slot[j] == k { taken = true }
                if taken { continue }
                let at = slotAt(puzzle, k)
                let d = hypot(at.x - tx, at.y - ty)
                if d < TangramSnapEngine.magnet {
                    let pull = TangramSnapEngine.magnetPull * pow(1 - d / TangramSnapEngine.magnet, 1.5)
                    tx += (at.x - tx) * pull
                    ty += (at.y - ty) * pull
                    magnetSlot = k
                    break
                }
            }
            p.tx[i] = tx
            p.ty[i] = ty
        }
    }

    /// autoPlay: the script between `before` and `t`.
    private func script(_ before: Double, _ t: Double) {
        let p = pieces
        for i in 0..<7 {
            let at = M.placeFrom + M.placeEvery * Double(i)
            if before < at && t >= at && puzzle == 0 && morph >= 1 && p.slot[i] < 0 {
                let pose = M.slotPose(M.silhouettes[0].slots[i], p.ta[i], p.tf[i] > 0.5 ? 1 : 0, true)!
                carry = i
                carryX = p.x[i]
                carryY = p.y[i]
                carryA0 = p.ta[i]
                carryA = pose.angle
                carryF = Double(pose.flip)
                carryAt = at
                p.mode[i] = 1
                p.tl[i] = 1
                p.tf[i] = Double(pose.flip)
                p.raise(i)
            }
        }
        if carry >= 0 {
            let i = carry
            let u = (t - carryAt) / M.placeTime
            let at = slotAt(0, i)
            if u >= 1 {
                p.ta[i] = carryA
                carry = -1
                p.mode[i] = 0
                p.tx[i] = at.x
                p.ty[i] = at.y
                trySnap(i, at.x, at.y, false)
            } else {
                let e = TangramSnapEngine.easeInOut(u)
                // An arc: up off the well and down into the slot.
                p.tx[i] = carryX + (at.x - carryX) * e
                p.ty[i] = carryY + (at.y - carryY) * e - sin(Double.pi * e) * layout.unit * 0.6
                p.ta[i] = carryA0 + (carryA - carryA0) * min(1, e * 1.3)
            }
        }
        if before < M.morphAt && t >= M.morphAt && solved { startMorph(M.autoSecond, true) }
        if before < M.resetAt && t >= M.resetAt { startMorph(0, false) }
    }

    private func tick() {
        let p = pieces
        let dt = TangramSnapEngine.tick
        handleInput()
        if hold > 0 {
            if held < 0 && turning < 0 { hold -= dt }
            // Back to the loop: take the figure apart and start from the empty cat again.
            if hold <= 0 && looping { clock = M.resetAt - dt }
        } else if looping {
            let before = clock
            var t = before + dt
            if t >= M.loop {
                t -= M.loop
                p.copy(from: rest)
                puzzle = 0
                morph = 1
                fill = 0
                solved = false
                scatter = [Double](repeating: -1, count: 7)
                lifted = [Double](repeating: 0, count: 7)
                pulse = [Double](repeating: -1, count: 7)
                ride = [Bool](repeating: false, count: 7)
                carry = -1
                apartIn = -1
            }
            clock = t
            script(before, t)
        }
        if apartIn > 0 {
            apartIn -= dt
            if apartIn <= 0 { startMorph(puzzle, false) }
        }
        // The morph, and the pieces riding it.
        if morph < 1 {
            morph = min(1, morph + dt / (reduced ? 0.3 : M.morphTime))
            let e = morphEase
            for i in 0..<7 where ride[i] {
                let k = p.slot[i]
                if k < 0 {
                    ride[i] = false
                    p.mode[i] = 0
                    continue
                }
                let at = slotNow(k)
                p.tx[i] = at.x
                p.ty[i] = at.y
                p.ta[i] = pa[i] + (qa[i] - pa[i]) * e
                p.tf[i] = pf[i] + (qf[i] - pf[i]) * e
                if morph >= 1 {
                    p.mode[i] = 2
                    p.ta[i] = qa[i]
                    p.tf[i] = qf[i]
                    ride[i] = false
                }
            }
        }
        // Pieces flying home, one after another, lifted on the way.
        for i in 0..<7 {
            if scatter[i] >= 0 {
                scatter[i] -= dt
                if scatter[i] < 0 {
                    let home = M.loose[i]
                    p.mode[i] = 0
                    p.tx[i] = layout.looseX + home.0 * layout.unit
                    p.ty[i] = layout.looseY + home.1 * layout.unit
                    p.ta[i] = M.nearestTurn((home.2 * Double.pi) / 4, p.ta[i])
                    p.tf[i] = home.3
                    p.tl[i] = 1
                    lifted[i] = TangramSnapEngine.liftTime
                    scatter[i] = -1
                }
            }
            if lifted[i] > 0 {
                lifted[i] -= dt
                if lifted[i] <= 0 && held != i && carry != i { p.tl[i] = 0 }
            }
            if pulse[i] >= 0 {
                pulse[i] += dt
                if pulse[i] > 0.6 { pulse[i] = -1 }
            }
        }
        let goal = solved ? 1.0 : 0.0
        fill = goal > fill ? min(1, fill + dt / TangramSnapEngine.fillTime) : max(0, fill - dt / (TangramSnapEngine.fillTime * 0.6))
        p.step(dt, calm: reduced)
    }

    private func setUp() {
        let l = layout
        let p = TangramSnapPieces()
        for i in 0..<7 {
            let home = M.loose[i]
            p.x[i] = l.looseX + home.0 * l.unit
            p.y[i] = l.looseY + home.1 * l.unit
            p.angle[i] = (home.2 * Double.pi) / 4
            p.flip[i] = home.3
            p.tx[i] = p.x[i]
            p.ty[i] = p.y[i]
            p.ta[i] = p.angle[i]
            p.tf[i] = p.flip[i]
        }
        let r = TangramSnapPieces()
        r.copy(from: p)
        pieces = p
        rest = r
        puzzle = 0
        from = 0
        morph = 1
        ax = [Double](repeating: 0, count: 7)
        ay = ax; aa = ax; af = ax; bx = ax; by = ax; ba = ax; bf = ax
        ride = [Bool](repeating: false, count: 7)
        pa = ax; pf = ax; qa = ax; qf = ax
        solved = false
        fill = 0
        revealX = l.boardX
        revealY = l.boardY
        scatter = [Double](repeating: -1, count: 7)
        lifted = [Double](repeating: 0, count: 7)
        pulse = [Double](repeating: -1, count: 7)
        held = -1
        grabX = 0
        grabY = 0
        magnetSlot = -1
        turning = -1
        turnFrom = 0
        carry = -1
        carryX = 0
        carryY = 0
        carryA0 = 0
        carryA = 0
        carryF = 0
        carryAt = 0
        apartIn = -1
        seen = [downN, upN, tapN, turnN, turnEndN, dblN]
        clock = 0
        ready = true
    }

    /// One display frame of `elapsed` seconds (nil on the first frame). Returns whether it ticked.
    @discardableResult
    func frame(_ elapsed: Double?) -> Bool {
        if layout.width == 0 { return false }
        realTime += elapsed ?? 0
        firePendingTap()
        if !ready { setUp() }
        let dt = min(elapsed ?? 0.016, Double(TangramSnapEngine.maxTicks) * TangramSnapEngine.tick)
        var pending = acc + dt
        var ticks = 0
        while pending >= TangramSnapEngine.tick && ticks < TangramSnapEngine.maxTicks {
            tick()
            pending -= TangramSnapEngine.tick
            ticks += 1
        }
        acc = min(pending, TangramSnapEngine.tick)
        return ticks > 0
    }

    // MARK: Gestures, as React Native's Gesture Handler reports them

    func panBegin(x: Double, y: Double) {
        downX = x
        downY = y
        downN += 1
    }

    func panUpdate(x: Double, y: Double, velocityX: Double, velocityY: Double) {
        fingerX = x
        fingerY = y
        upVX = velocityX
        upVY = velocityY
    }

    func panEnd(velocityX: Double, velocityY: Double) {
        upVX = velocityX
        upVY = velocityY
    }

    func panFinalize(success: Bool) {
        if !success {
            upVX = 0
            upVY = 0
        }
        upN += 1
    }

    func rotateStart(anchorX: Double, anchorY: Double) {
        turnX = anchorX
        turnY = anchorY
        turnBy = 0
        turnN += 1
    }

    func rotateUpdate(_ rotation: Double) {
        turnBy = rotation
    }

    func rotateFinalize() {
        turnEndN += 1
    }

    func tap(x: Double, y: Double) {
        tapX = x
        tapY = y
        tapN += 1
    }

    func doubleTap(x: Double, y: Double) {
        dblX = x
        dblY = y
        dblN += 1
    }

    // MARK: Raw touches from the view

    // Seconds of display time, for timing taps.
    private var realTime = 0.0
    private var fingers: [Int: (x: Double, y: Double)] = [:]
    private var firstFinger = -1
    private var startX = 0.0
    private var startY = 0.0
    private var startAt = 0.0
    private var panActive = false
    private var multi = false
    private var turnActive = false
    private var turnBase = 0.0
    private var turnLast = 0.0
    private var turnSum = 0.0
    private var pendingTap: (x: Double, y: Double, deadline: Double, downBy: Double)?

    private func firePendingTap() {
        if let p = pendingTap, realTime >= p.deadline {
            pendingTap = nil
            tap(x: p.x, y: p.y)
        }
    }

    private func twoFingerAngle() -> (angle: Double, cx: Double, cy: Double)? {
        let ids = fingers.keys.sorted()
        guard ids.count >= 2, let a = fingers[ids[0]], let b = fingers[ids[1]] else { return nil }
        return (atan2(b.y - a.y, b.x - a.x), (a.x + b.x) / 2, (a.y + b.y) / 2)
    }

    func touchDown(id: Int, x: Double, y: Double) {
        fingers[id] = (x, y)
        if fingers.count == 1 {
            firstFinger = id
            startX = x
            startY = y
            startAt = realTime
            panActive = false
            multi = false
            turnActive = false
            panBegin(x: x, y: y)
        } else {
            multi = true
            if let a = twoFingerAngle() {
                turnBase = a.angle
                turnLast = a.angle
                turnSum = 0
            }
        }
    }

    func touchMove(id: Int, x: Double, y: Double, velocityX: Double, velocityY: Double) {
        fingers[id] = (x, y)
        if id == firstFinger {
            let dx = x - startX
            let dy = y - startY
            if !panActive && dx * dx + dy * dy >= TangramSnapEngine.panSlop * TangramSnapEngine.panSlop { panActive = true }
            if panActive { panUpdate(x: x, y: y, velocityX: velocityX, velocityY: velocityY) }
        }
        if fingers.count >= 2, let a = twoFingerAngle() {
            var d = a.angle - turnLast
            if d > Double.pi { d -= 2 * Double.pi }
            if d < -Double.pi { d += 2 * Double.pi }
            turnLast = a.angle
            turnSum += d
            if !turnActive && abs(turnSum) >= TangramSnapEngine.turnStart {
                turnActive = true
                rotateStart(anchorX: a.cx, anchorY: a.cy)
            }
            if turnActive { rotateUpdate(turnSum) }
        }
    }

    func touchUp(id: Int, x: Double, y: Double, velocityX: Double, velocityY: Double) {
        fingers[id] = nil
        if fingers.count < 2 && turnActive {
            turnActive = false
            rotateFinalize()
        }
        if !fingers.isEmpty { return }
        if panActive {
            panEnd(velocityX: velocityX, velocityY: velocityY)
            panFinalize(success: true)
        } else {
            panFinalize(success: false)
            if !multi && realTime - startAt <= TangramSnapEngine.tapTime {
                // A second tap soon after the first makes a double tap; a lone tap waits for it.
                if let p = pendingTap, startAt <= p.downBy {
                    pendingTap = nil
                    doubleTap(x: x, y: y)
                } else {
                    pendingTap = (x, y, realTime + TangramSnapEngine.doubleGap, realTime + TangramSnapEngine.doubleGap)
                }
            }
        }
        panActive = false
        firstFinger = -1
    }

    func touchCancel() {
        fingers.removeAll()
        if turnActive {
            turnActive = false
            rotateFinalize()
        }
        panFinalize(success: false)
        panActive = false
        firstFinger = -1
    }
}
