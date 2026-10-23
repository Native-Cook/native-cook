import Foundation

// The sheet of bubble wrap as plain state, stepped once per frame: each bubble's pressure and pop,
// the fingers, the tug of the sheet, the shake, and the swap to a fresh sheet. Units are points
// and seconds. Ported line for line from the React Native block's BubbleWrapPopSheet.ts.
enum BubbleWrapPopPhysics {
    // Bubbles: radius as a share of the column pitch; the rows sit on a hex grid.
    static let bubbleShare = 0.4
    static let columns = 6
    // The film runs this far past the outer bubbles.
    static let filmMargin = 18.0

    // A press builds pressure from 0 to 1 over `pressTime` s and the bubble pops at 1. Let go
    // early, the dome springs back (1/s², 1/s). A sliding finger pops what it crosses: a bubble it
    // reaches within `slideReach` of its radius is committed and pops `slideTime` s later.
    static let pressTime = 0.46
    static let relaxK = 420.0
    static let relaxC = 16.0
    static let slideReach = 0.85
    static let slideTime = 0.045
    static let commit = 0.72
    static let moveSlop = 8.0
    // The air rings and the collapse play out over this long after a pop.
    static let popLife = 0.7

    // The sheet tugs with the finger, rubber-banded to `tugMax` pt, and springs home on release.
    static let tugMax = 46.0
    static let tugK = 210.0
    static let tugC = 19.0
    // Each pop knocks the camera with `shakeKick` pt/s (half as much on a slide), on a stiff spring.
    static let shakeKick = 230.0
    static let shakeK = 1100.0
    static let shakeC = 24.0
    static let shakeMax = 5.0
    // Once every bubble is gone the sheet waits `swapDelay` s, then slides out as a fresh one
    // slides in over `swapTime` s.
    static let swapDelay = 0.55
    static let swapTime = 1.05
    // The counter rolls toward the count on a spring.
    static let countK = 260.0
    static let countC = 30.0
    // Pops closer together than this give one haptic.
    static let hapticGap = 0.05

    static let eventPop = 1
    static let eventSwap = 2
}

final class BubbleWrapPopFinger {
    var id = -1
    var down = false
    var scripted = false
    var x = 0.0
    var y = 0.0
    var x0 = 0.0
    var y0 = 0.0
    var lastX = 0.0
    var lastY = 0.0
    /// 0 pressing a bubble, 1 sliding, 2 tugging the sheet.
    var mode = 0
    var bubble = -1
    var moved = false
    var tugFromX = 0.0
    var tugFromY = 0.0
    var vx = 0.0
    var vy = 0.0
    var lastT = 0.0
}

final class BubbleWrapPopSheet {
    private typealias P = BubbleWrapPopPhysics

    var time = 0.0
    let width: Double
    /// The sheet's rectangle at rest.
    let left: Double
    let top: Double
    let right: Double
    let bottom: Double
    let radius: Double
    let count: Int
    /// Bubble centres, relative to the sheet's top left.
    var bx: [Double] = []
    var by: [Double] = []
    var rowOf: [Int] = []
    var pressure: [Double]
    var pressureV: [Double]
    var committed: [Bool]
    var popped: [Bool]
    /// Seconds since each bubble popped, or -1.
    var popAge: [Double]
    /// Which side the finger was on when it popped, for the bulge.
    var leanX: [Double]
    var leanY: [Double]
    var pops = 0
    var shown = 0.0
    var shownV = 0.0
    var tugX = 0.0
    var tugY = 0.0
    var tugVX = 0.0
    var tugVY = 0.0
    var shakeX = 0.0
    var shakeY = 0.0
    var shakeVX = 0.0
    var shakeVY = 0.0
    /// Seconds since every bubble went, -1 while some remain.
    var emptyFor = -1.0
    /// 0 … 1 while a fresh sheet slides in.
    var swap = 0.0
    var fingers = [BubbleWrapPopFinger(), BubbleWrapPopFinger()]
    var events = 0
    var eventsByUser = false
    var lastHaptic = -1.0
    let calm: Bool

    /// A full sheet laid out in the given rectangle.
    init(width: Double, left: Double, top: Double, right: Double, bottom: Double, calm: Bool) {
        self.width = width
        self.left = left
        self.top = top
        self.right = right
        self.bottom = bottom
        self.calm = calm
        let inner = right - left - P.filmMargin * 2
        let pitch = inner / (Double(P.columns) + 0.5)
        radius = pitch * P.bubbleShare
        let rowPitch = pitch * 0.866
        let rows = max(1, Int(((bottom - top - P.filmMargin * 2 - pitch) / rowPitch).rounded(.down)) + 1)
        // Centre the rows in the height they leave.
        let used = Double(rows - 1) * rowPitch + pitch
        let offY = (bottom - top - used) / 2 + pitch / 2
        for r in 0..<rows {
            for c in 0..<P.columns {
                bx.append(P.filmMargin + pitch / 2 + Double(c) * pitch + Double(r % 2) * (pitch / 2))
                by.append(offY + Double(r) * rowPitch)
                rowOf.append(r)
            }
        }
        count = bx.count
        pressure = [Double](repeating: 0, count: count)
        pressureV = pressure
        committed = [Bool](repeating: false, count: count)
        popped = committed
        popAge = [Double](repeating: -1, count: count)
        leanX = pressure
        leanY = pressure
    }

    /// The intact bubble under (x, y) on screen, or -1.
    func bubbleAt(_ x: Double, _ y: Double, reach: Double) -> Int {
        let lx = x - left - tugX
        let ly = y - top - tugY
        var best = -1
        var bestD = radius * reach
        for i in 0..<count where !popped[i] {
            let d = hypot(bx[i] - lx, by[i] - ly)
            if d < bestD {
                bestD = d
                best = i
            }
        }
        return best
    }

    private func finger(_ id: Int) -> BubbleWrapPopFinger? { fingers.first { $0.down && $0.id == id } }

    private static func sign(_ v: Double) -> Double { v > 0 ? 1 : v < 0 ? -1 : 0 }

    private static func rubber(_ d: Double, _ max: Double) -> Double {
        sign(d) * max * (1 - exp(-abs(d) / max))
    }

    func down(id: Int, x: Double, y: Double, scripted: Bool) {
        if swap > 0 { return }
        guard let f = fingers.first(where: { !$0.down }) else { return }
        f.id = id
        f.down = true
        f.scripted = scripted
        f.x = x
        f.y = y
        f.x0 = x
        f.y0 = y
        f.lastX = x
        f.lastY = y
        f.lastT = time
        f.vx = 0
        f.vy = 0
        f.moved = false
        // Among the bubbles it presses (or, on a popped one, waits to slide); on the film's edge or
        // off the sheet it tugs the film.
        let b = bubbleAt(x, y, reach: 1)
        let lx = x - left - tugX
        let ly = y - top - tugY
        let amongBubbles = lx > P.filmMargin && lx < right - left - P.filmMargin && ly > P.filmMargin && ly < bottom - top - P.filmMargin
        f.bubble = b
        f.mode = b >= 0 || amongBubbles ? 0 : 2
        f.tugFromX = tugX
        f.tugFromY = tugY
        if b >= 0 {
            leanX[b] = (x - left - tugX - bx[b]) / radius
            leanY[b] = (y - top - tugY - by[b]) / radius
        }
    }

    func move(id: Int, x: Double, y: Double) {
        guard let f = finger(id) else { return }
        let px = f.x
        let py = f.y
        f.x = x
        f.y = y
        let dt = time - f.lastT
        if dt > 0.004 {
            f.vx = f.vx * 0.35 + (x - f.lastX) / dt * 0.65
            f.vy = f.vy * 0.35 + (y - f.lastY) / dt * 0.65
            f.lastX = x
            f.lastY = y
            f.lastT = time
        }
        if !f.moved && hypot(x - f.x0, y - f.y0) > P.moveSlop {
            f.moved = true
            if f.mode == 0 { f.mode = 1 }
        }
        if f.mode == 2 {
            tugX = Self.rubber(f.tugFromX + x - f.x0, P.tugMax)
            tugY = Self.rubber(f.tugFromY + y - f.y0, P.tugMax)
            tugVX = 0
            tugVY = 0
            return
        }
        if f.mode == 1 {
            // Every bubble the finger passes over on its way from the last point, a few pt apart.
            let steps = max(1, Int((hypot(x - px, y - py) / 6).rounded(.up)))
            for k in 1...steps {
                let b = bubbleAt(px + (x - px) * Double(k) / Double(steps), py + (y - py) * Double(k) / Double(steps), reach: P.slideReach)
                if b >= 0 && !committed[b] {
                    committed[b] = true
                    pressure[b] = max(pressure[b], P.commit)
                    leanX[b] = Self.sign(x - px) * 0.6
                    leanY[b] = Self.sign(y - py) * 0.6
                }
            }
            f.bubble = -1
        }
    }

    func up(id: Int) {
        guard let f = finger(id) else { return }
        f.down = false
        if f.mode == 2 {
            let still = time - f.lastT > 0.08
            tugVX = still ? 0 : f.vx
            tugVY = still ? 0 : f.vy
        }
        f.bubble = -1
    }

    func userFingers() -> Int { fingers.filter { $0.down && !$0.scripted }.count }

    func cancelScripted() {
        for f in fingers where f.down && f.scripted { up(id: f.id) }
    }

    private func pop(_ b: Int, sliding: Bool, byUser: Bool) {
        popped[b] = true
        popAge[b] = 0
        pressure[b] = 1
        pops += 1
        if !calm {
            // A knock in a direction set by which bubble it was, so the shake is the same every time.
            let a = (Double(b) * 2.399963).truncatingRemainder(dividingBy: Double.pi * 2)
            let kick = P.shakeKick * (sliding ? 0.5 : 1)
            shakeVX += cos(a) * kick
            shakeVY += sin(a) * kick
        }
        events |= P.eventPop
        if byUser && time - lastHaptic > P.hapticGap {
            eventsByUser = true
            lastHaptic = time
        }
    }

    /// Advances everything by one frame.
    func step(_ frameDt: Double) {
        let dt = min(frameDt, 1.0 / 30)
        time += dt
        // Pressure: a held bubble builds toward its pop; a committed one runs out fast; the rest relax.
        var pressing: [Int] = []
        var pressedByUser = false
        for f in fingers where f.down && f.mode == 0 && f.bubble >= 0 && !popped[f.bubble] {
            pressing.append(f.bubble)
            if !f.scripted { pressedByUser = true }
        }
        var anyByUser = pressedByUser
        for f in fingers where f.down && !f.scripted { anyByUser = true }
        for i in 0..<count {
            if popped[i] {
                if popAge[i] >= 0 { popAge[i] += dt }
                continue
            }
            if committed[i] {
                pressure[i] += dt / P.slideTime * (1 - P.commit)
                if pressure[i] >= 1 { pop(i, sliding: true, byUser: anyByUser) }
                continue
            }
            if pressing.contains(i) {
                pressure[i] += dt / P.pressTime
                pressureV[i] = 0
                if pressure[i] >= 1 { pop(i, sliding: false, byUser: pressedByUser) }
                continue
            }
            if pressure[i] != 0 || pressureV[i] != 0 {
                pressureV[i] += (-pressure[i] * P.relaxK - pressureV[i] * P.relaxC) * dt
                pressure[i] += pressureV[i] * dt
                if abs(pressure[i]) < 0.001 && abs(pressureV[i]) < 0.01 {
                    pressure[i] = 0
                    pressureV[i] = 0
                }
            }
        }
        // The tug springs home once no finger holds it.
        let tugging = fingers.contains { $0.down && $0.mode == 2 }
        if !tugging {
            tugVX += (-tugX * P.tugK - tugVX * P.tugC) * dt
            tugVY += (-tugY * P.tugK - tugVY * P.tugC) * dt
            tugX += tugVX * dt
            tugY += tugVY * dt
        }
        // Shake, in four substeps: it is stiff.
        for _ in 0..<4 {
            let h = dt / 4
            shakeVX += (-shakeX * P.shakeK - shakeVX * P.shakeC) * h
            shakeVY += (-shakeY * P.shakeK - shakeVY * P.shakeC) * h
            shakeX = max(-P.shakeMax, min(P.shakeMax, shakeX + shakeVX * h))
            shakeY = max(-P.shakeMax, min(P.shakeMax, shakeY + shakeVY * h))
        }
        // The counter.
        shownV += ((Double(pops) - shown) * P.countK - shownV * P.countC) * dt
        shown += shownV * dt
        // An empty sheet gives way to a fresh one.
        if swap > 0 {
            swap += dt / P.swapTime
            if swap >= 1 {
                swap = 0
                for i in 0..<count {
                    popped[i] = false
                    committed[i] = false
                    pressure[i] = 0
                    pressureV[i] = 0
                    popAge[i] = -1
                }
                emptyFor = -1
            }
        } else if pops >= count {
            emptyFor = emptyFor < 0 ? 0 : emptyFor + dt
            if emptyFor >= P.swapDelay {
                swap = 0.0001
                pops = 0
                events |= P.eventSwap
                for f in fingers { f.down = false }
            }
        }
    }
}
