import Foundation

// The clay's soft body: a closed ring of mass points around a pressure volume, stepped in fixed
// substeps once a frame. Units are points and seconds; every point has a mass of 1, so a force is
// an acceleration. Ported line for line from the React Native block's ClayPressBlobPhysics.ts.
enum ClayPressBlobPhysics {
    /// Points around the ring.
    static let points = 48
    // Fixed substep and the most frame time one frame may consume (a long hitch is cut short).
    static let substep = 1.0 / 240
    static let maxFrame = 1.0 / 30

    // Neighbours are tied by springs of this stiffness (1/s²), damped along the edge.
    static let edgeK = 2600.0
    static let edgeDamp = 6.0
    // Every point is pulled toward its place on the rest shape around the centroid. Soft, so the
    // clay wobbles at about 1.8 Hz; much stiffer while a pinch is holding it.
    static let shapeK = 130.0
    static let pinchK = 420.0
    // The trapped volume pushes out along each edge's normal as the area drops below its rest area.
    static let pressureK = 26000.0
    // Bending stiffness along the rim (1/s²).
    static let bendK = 900.0
    // Damping of each point's motion relative to the body (wobble), and of the body itself (air).
    static let innerDamp = 3.4
    static let airDamp = 0.6
    // The body drifts back to its home on a slightly underdamped spring.
    static let homeK = 11.0
    static let homeDamp = 4.6
    // Soft walls: a point that comes within `wallCushion` pt of one meets a damped spring that
    // grows from nothing across that cushion and stiffens past it (`wallK` × (1 + (depth /
    // `wallGive`)²)), so a thrown body slows before the wall and flattens against it into a
    // rounded squash, never a cut. `wallGrip` drags lightly along the wall. Only a far safety
    // rail, `wallRail` pt past the wall, holds a point that something has thrown wildly.
    static let wallK = 2200.0
    static let wallDamp = 40.0
    static let wallGrip = 4.0
    static let wallGive = 12.0
    static let wallCushion = 28.0
    static let wallRail = 80.0

    // Plasticity: a point pushed more than `yield` pt off its rest radius drags the rest radius
    // after it; the marks creep back round and are smoothed along the ring.
    static let yield = 3.0
    static let yieldRate = 5.5
    static let recoverRate = 0.95
    static let smoothRate = 2.2
    // Pressing spreads the clay: the rest radius grows by up to `spread` pt, most on the finger's side.
    static let spread = 7.0

    // A finger that lands outside the clay is a disc of `fingerR` pt that pushes the rim in on a
    // soft contact spring (`contactK`, `contactDamp`), never by moving points outright.
    static let fingerR = 24.0
    static let contactK = 2600.0
    static let contactDamp = 30.0
    // A finger pressed in near the rim notches it: rim points within the dent's reach are pulled
    // in by up to `notch` pt (on `notchK`), so a press at the edge dents the edge.
    static let notch = 11.0
    static let notchK = 260.0
    static let moveSlop = 9.0
    static let tapTime = 0.26
    // Drag: grab reach, the Gaussian patch, its spring and the rubber band's limit.
    static let grabReach = 46.0
    static let grabSigma = 2.4
    static let grabSpan = 6
    static let grabK = 950.0
    static let grabDamp = 38.0
    static let pullMax = 130.0
    // A pulled lump keeps `wallRoom` pt inside the walls, easing into that limit.
    static let wallRoom = 26.0
    // Throws.
    static let flickSpeed = 700.0
    static let flickShare = 0.45
    static let flickMax = 850.0
    static let throwKeep = 0.1
    // Pinch limits, and the share of the squeeze that stays.
    static let pinchMin = 0.6
    static let pinchMax = 1.5
    static let pinchSet = 0.3

    // Dents.
    static let dentRadius = 30.0
    static let dents = 6
    static let dentGrow = 6.5
    static let dentHold = 0.9
    static let dentRecover = 2.6
    static let ridgeFade = 1.5

    static let thudSpeed = 260.0
    static let recolourTime = 0.75

    /// Bits in `events`, cleared by whoever reads them.
    static let eventPress = 1
    static let eventRecolour = 2
    static let eventThud = 4
    static let eventRelease = 8
}

final class ClayPressBlobDent {
    var live = false
    /// Centre, relative to the body's centroid.
    var x = 0.0
    var y = 0.0
    var depth = 0.0
    var ridge = 0.0
    var growing = false
    /// Seconds since the finger lifted.
    var since = 0.0
    /// Seeds the fingerprint's tilt and breaks.
    var seed = 0.0
}

final class ClayPressBlobFinger {
    var id = -1
    var down = false
    /// A scripted finger from autoPlay: no haptics, and a tap sets `tapColour` instead of cycling.
    var scripted = false
    var tapColour = -1
    var x = 0.0
    var y = 0.0
    var startX = 0.0
    var startY = 0.0
    var startT = 0.0
    var lastX = 0.0
    var lastY = 0.0
    var lastT = 0.0
    var vx = 0.0
    var vy = 0.0
    var moved = false
    var dent = -1
}

final class ClayPressBlobSim {
    private typealias P = ClayPressBlobPhysics

    var time = 0.0
    var acc = 0.0
    let r0: Double
    let homeX: Double
    let homeY: Double
    let left: Double
    let top: Double
    let right: Double
    let bottom: Double
    /// The round body's area, and what pressing has spread it by since.
    let baseArea: Double
    var spreadArea = 0.0
    let edge0: Double
    var x: [Double]
    var y: [Double]
    var vx: [Double]
    var vy: [Double]
    var rest: [Double]
    // Scratch for the forces of one substep.
    var ax: [Double]
    var ay: [Double]
    var cx: Double
    var cy: Double
    var cvx = 0.0
    var cvy = 0.0
    var fingers = [ClayPressBlobFinger(), ClayPressBlobFinger()]
    /// 0 none, 1 press, 2 drag, 3 pinch.
    var mode = 0
    var grab = -1
    var grabFromX: [Double]
    var grabFromY: [Double]
    // Scratch: each point's place on the rest shape this substep.
    var tx: [Double]
    var ty: [Double]
    var pinchFrom = 1.0
    var pinchScale = 1.0
    var pinchUx = 1.0
    var pinchUy = 0.0
    var pressSide = 0.0
    var flying = false
    var throwByUser = false
    var thudAt = -10.0
    /// Reduced motion: no throws, and the wobble dies out fast.
    let calm: Bool
    var dents: [ClayPressBlobDent]
    var colourFrom = 0
    var colourTo = 0
    var recolour = 1.0
    var recolourX: Double
    var recolourY: Double
    let paletteSize: Int
    var events = 0
    /// The user (not autoPlay) caused the events in `events`.
    var eventsByUser = false
    // Unit circle of each point's angle on the ring.
    private let cosA: [Double]
    private let sinA: [Double]

    /// A fresh, round body at rest in the middle of the stage.
    init(width: Double, height: Double, top: Double, bottom: Double, paletteSize: Int, calm: Bool) {
        let n = P.points
        left = 20
        right = width - 20
        self.top = top
        self.bottom = bottom
        let r0 = min(width * 0.3, (bottom - top) * 0.3, 132)
        let homeX = width / 2
        let homeY = (top + bottom) / 2
        self.r0 = r0
        self.homeX = homeX
        self.homeY = homeY
        var cosA: [Double] = []
        var sinA: [Double] = []
        for i in 0..<n {
            let a = Double(i) / Double(n) * Double.pi * 2
            cosA.append(cos(a))
            sinA.append(sin(a))
        }
        self.cosA = cosA
        self.sinA = sinA
        x = (0..<n).map { homeX + cosA[$0] * r0 }
        y = (0..<n).map { homeY + sinA[$0] * r0 }
        let zero = [Double](repeating: 0, count: n)
        vx = zero
        vy = zero
        rest = [Double](repeating: r0, count: n)
        ax = zero
        ay = zero
        grabFromX = zero
        grabFromY = zero
        tx = zero
        ty = zero
        cx = homeX
        cy = homeY
        baseArea = 0.5 * Double(n) * r0 * r0 * sin(2 * Double.pi / Double(n))
        edge0 = 2 * r0 * sin(Double.pi / Double(n))
        self.calm = calm
        dents = (0..<P.dents).map { _ in ClayPressBlobDent() }
        recolourX = homeX
        recolourY = homeY
        self.paletteSize = paletteSize
    }

    private static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, v)) }

    private static func wrap(_ i: Int, _ n: Int) -> Int { ((i % n) + n) % n }

    private static func sign(_ v: Double) -> Double { v > 0 ? 1 : v < 0 ? -1 : 0 }

    /// Is (px, py) inside the ring? An even-odd ray cast over the points.
    func contains(_ px: Double, _ py: Double) -> Bool {
        let n = P.points
        var inside = false
        var j = n - 1
        for i in 0..<n {
            let yi = y[i]
            let yj = y[j]
            if (yi > py) != (yj > py) && px < (x[j] - x[i]) * (py - yi) / (yj - yi) + x[i] { inside.toggle() }
            j = i
        }
        return inside
    }

    private func nearestPoint(_ px: Double, _ py: Double) -> (index: Int, distance: Double) {
        var best = 0
        var bestD = Double.infinity
        for i in 0..<P.points {
            let d = (x[i] - px) * (x[i] - px) + (y[i] - py) * (y[i] - py)
            if d < bestD {
                bestD = d
                best = i
            }
        }
        return (best, bestD.squareRoot())
    }

    func centroid() {
        var sx = 0.0, sy = 0.0, svx = 0.0, svy = 0.0
        for i in 0..<P.points {
            sx += x[i]
            sy += y[i]
            svx += vx[i]
            svy += vy[i]
        }
        let n = Double(P.points)
        cx = sx / n
        cy = sy / n
        cvx = svx / n
        cvy = svy / n
    }

    private func signedArea() -> Double {
        var a = 0.0
        for i in 0..<P.points {
            let j = (i + 1) % P.points
            a += x[i] * y[j] - x[j] * y[i]
        }
        return a / 2
    }

    /// A rubber band: follows a short pull one to one and never goes past `max`.
    private static func rubber(_ d: Double, _ max: Double) -> Double { max * (1 - exp(-d / max)) }

    /// How much a wall holds a point `d` pt into its cushion: 0 at the cushion's edge, 1 at the wall.
    private static func wallHold(_ d: Double) -> Double { min(1, d / P.wallCushion) }

    /// The wall's stiffness `d` pt into its cushion: growing across it, stiffening past the wall.
    private static func wallStiffness(_ d: Double) -> Double {
        let past = max(0, d - P.wallCushion) / P.wallGive
        return P.wallK * wallHold(d) * (1 + past * past)
    }

    /// `v` eased into [lo, hi]: unchanged well inside, approaching an edge without ever passing it.
    private static func softLimit(_ v: Double, _ lo: Double, _ hi: Double, _ ease: Double) -> Double {
        if v > hi - ease { return hi - ease + ease * tanh((v - (hi - ease)) / ease) }
        if v < lo + ease { return lo + ease - ease * tanh((lo + ease - v) / ease) }
        return v
    }

    private func startDent(_ px: Double, _ py: Double) -> Int {
        var slot = 0
        var shallowest = Double.infinity
        for i in 0..<P.dents {
            let d = dents[i]
            if !d.live {
                slot = i
                shallowest = -1
                break
            }
            if d.depth < shallowest {
                shallowest = d.depth
                slot = i
            }
        }
        let d = dents[slot]
        d.live = true
        d.x = px - cx
        d.y = py - cy
        d.depth = 0
        d.ridge = 0
        d.growing = true
        d.since = 0
        // A deterministic seed from where it landed (JavaScript's % keeps the sign; abs makes it positive).
        d.seed = abs(sin(px * 12.9898 + py * 78.233) * 43758.5453).truncatingRemainder(dividingBy: 1)
        return slot
    }

    private func stopDent(_ slot: Int) {
        if slot < 0 { return }
        let d = dents[slot]
        d.growing = false
        d.since = 0
        // A touch too short to leave a mark leaves none.
        if d.depth < 0.06 { d.live = false }
    }

    private func finger(_ id: Int) -> ClayPressBlobFinger? {
        fingers.first { $0.down && $0.id == id }
    }

    private func downCount() -> Int { fingers.filter(\.down).count }

    private func beginPinch() {
        let a = fingers[0]
        let b = fingers[1]
        if mode == 1 || mode == 2 {
            for f in fingers { stopDent(f.dent) }
        }
        mode = 3
        grab = -1
        pinchFrom = max(20, hypot(b.x - a.x, b.y - a.y))
        pinchScale = 1
    }

    /// A finger lands. `scripted` marks autoPlay's own fingers.
    func touchDown(id: Int, x px: Double, y py: Double, scripted: Bool, tapColour: Int) {
        guard let f = fingers.first(where: { !$0.down }) else { return }
        f.id = id
        f.down = true
        f.scripted = scripted
        f.tapColour = tapColour
        f.x = px
        f.y = py
        f.startX = px
        f.startY = py
        f.startT = time
        f.lastX = px
        f.lastY = py
        f.lastT = time
        f.vx = 0
        f.vy = 0
        f.moved = false
        f.dent = -1
        flying = false
        if downCount() == 2 {
            beginPinch()
            return
        }
        mode = 1
        grab = -1
        if contains(px, py) {
            f.dent = startDent(px, py)
            // The side of the body the finger is on, as an angle, for the spread.
            pressSide = atan2(py - cy, px - cx)
            events |= P.eventPress
            eventsByUser = !scripted
        }
    }

    /// The ring patch a drag takes hold of, or -1 for none.
    private func pickGrab(_ f: ClayPressBlobFinger) -> Int {
        let near = nearestPoint(f.startX, f.startY)
        if near.distance < P.grabReach { return near.index }
        if !contains(f.startX, f.startY) { return -1 }
        // From inside, the side the finger pulls toward.
        let dx = f.x - f.startX
        let dy = f.y - f.startY
        let h = hypot(dx, dy)
        let len = h == 0 ? 1 : h
        var best = -1
        var bestDot = -Double.infinity
        for i in 0..<P.points {
            let ox = x[i] - cx
            let oy = y[i] - cy
            let ol = hypot(ox, oy)
            let dot = (ox * dx + oy * dy) / ((ol == 0 ? 1 : ol) * len)
            if dot > bestDot {
                bestDot = dot
                best = i
            }
        }
        return best
    }

    func touchMove(id: Int, x px: Double, y py: Double) {
        guard let f = finger(id) else { return }
        f.x = px
        f.y = py
        // The finger's speed, smoothed over the moves that span at least 4 ms.
        let dt = time - f.lastT
        if dt > 0.004 {
            f.vx = f.vx * 0.35 + (px - f.lastX) / dt * 0.65
            f.vy = f.vy * 0.35 + (py - f.lastY) / dt * 0.65
            f.lastX = px
            f.lastY = py
            f.lastT = time
        }
        if !f.moved && hypot(px - f.startX, py - f.startY) > P.moveSlop {
            f.moved = true
            if mode == 1 {
                stopDent(f.dent)
                mode = 2
                grab = pickGrab(f)
                grabFromX = x
                grabFromY = y
            }
        }
    }

    /// Starts the colour change to palette entry `to`, spreading from (px, py).
    func recolourTo(_ to: Int, x px: Double, y py: Double) {
        if to == colourTo && recolour >= 1 { return }
        // A change caught half way finishes at once, so the new one starts from a single colour.
        colourFrom = recolour < 0.5 ? colourFrom : colourTo
        colourTo = to
        recolour = 0
        recolourX = px - cx
        recolourY = py - cy
    }

    /// A pinch leaves `pinchSet` of its squeeze in the clay, which then creeps back round.
    private func setPinch() {
        let sa = pinchScale
        let sb = 1 / sa.squareRoot()
        let ux = pinchUx
        let uy = pinchUy
        for i in 0..<P.points {
            let ox = cosA[i]
            let oy = sinA[i]
            let along = (ox * ux + oy * uy) * sa
            let across = (-ox * uy + oy * ux) * sb
            let squeezed = rest[i] * hypot(along, across)
            rest[i] += (squeezed - rest[i]) * P.pinchSet
        }
    }

    /// A finger lifts. Ends a press, a tap, a drag (maybe a throw) or a pinch.
    func touchUp(id: Int, cancelled: Bool) {
        guard let f = finger(id) else { return }
        f.down = false
        stopDent(f.dent)
        f.dent = -1
        // A finger that stopped before it lifted throws nothing.
        let still = time - f.lastT > 0.08
        let fvx = still ? 0 : f.vx
        let fvy = still ? 0 : f.vy
        if mode == 3 {
            // Once a pinch has started, the clay waits for every finger to lift.
            if downCount() == 0 {
                setPinch()
                mode = 0
                events |= P.eventRelease
                eventsByUser = !f.scripted
            }
            return
        }
        if cancelled {
            mode = 0
            grab = -1
            return
        }
        if mode == 1 && !f.moved && time - f.startT < P.tapTime {
            let next = f.tapColour >= 0 ? f.tapColour : (colourTo + 1) % paletteSize
            recolourTo(next, x: f.x, y: f.y)
            events |= P.eventRecolour
            eventsByUser = !f.scripted
        }
        if mode == 2 {
            let speed = hypot(fvx, fvy)
            if grab >= 0 && speed > P.flickSpeed && !calm {
                let k = min(P.flickMax, speed * P.flickShare) / speed
                // The pulled lump lets go of most of its own speed, so the body flies as one piece.
                centroid()
                for i in 0..<P.points {
                    vx[i] = cvx + (vx[i] - cvx) * P.throwKeep + fvx * k
                    vy[i] = cvy + (vy[i] - cvy) * P.throwKeep + fvy * k
                }
                flying = true
                throwByUser = !f.scripted
            }
            if grab >= 0 {
                events |= P.eventRelease
                eventsByUser = !f.scripted
            }
        }
        mode = 0
        grab = -1
    }

    /// How many real (not scripted) fingers are down.
    func userFingers() -> Int { fingers.filter { $0.down && !$0.scripted }.count }

    /// Lifts every scripted finger without a throw or a tap, when a real touch takes over from autoPlay.
    func cancelScripted() {
        for f in fingers where f.down && f.scripted { touchUp(id: f.id, cancelled: true) }
    }

    /// One fixed substep of the body.
    private func substep(_ h: Double) {
        let n = P.points
        centroid()
        let area = signedArea()
        let restArea = baseArea + spreadArea
        let pressure = P.pressureK * (restArea / max(area, restArea * 0.2) - 1)
        let stiff = mode == 3 ? P.pinchK : P.shapeK

        // The pinch as a stretch along its axis and the inverse squeeze across it.
        var sa = 1.0
        var sb = 1.0
        var ux = 1.0
        var uy = 0.0
        if mode == 3 {
            let a = fingers[0]
            let b = fingers[1]
            let span = hypot(b.x - a.x, b.y - a.y)
            let raw = span / pinchFrom
            // Past its limits the squeeze follows at a quarter of the pull.
            sa = raw < P.pinchMin ? P.pinchMin - (P.pinchMin - raw) * 0.25
                : raw > P.pinchMax ? P.pinchMax + (raw - P.pinchMax) * 0.25 : raw
            sb = 1 / sa.squareRoot()
            let s = span == 0 ? 1 : span
            ux = (b.x - a.x) / s
            uy = (b.y - a.y) / s
            pinchScale = sa
            pinchUx = ux
            pinchUy = uy
        }

        let inner = calm ? P.innerDamp * 3 : P.innerDamp
        for i in 0..<n {
            var ox = cosA[i] * rest[i]
            var oy = sinA[i] * rest[i]
            if mode == 3 {
                let along = ox * ux + oy * uy
                let across = -ox * uy + oy * ux
                ox = ux * along * sa - uy * across * sb
                oy = uy * along * sa + ux * across * sb
            }
            tx[i] = cx + ox
            ty[i] = cy + oy
            var fx = (cx + ox - x[i]) * stiff
            var fy = (cy + oy - y[i]) * stiff
            // Pressure along the outward normal of the two edges that meet here.
            let p = (i + 1) % n
            let m = (i + n - 1) % n
            fx += pressure * (y[p] - y[m]) / (2 * edge0)
            fy += pressure * -(x[p] - x[m]) / (2 * edge0)
            // Internal and air damping, and the pull home.
            fx += -(vx[i] - cvx) * inner - vx[i] * P.airDamp + (homeX - cx) * P.homeK - cvx * P.homeDamp
            fy += -(vy[i] - cvy) * inner - vy[i] * P.airDamp + (homeY - cy) * P.homeK - cvy * P.homeDamp
            ax[i] = fx
            ay[i] = fy
        }
        // Bending: each point keeps the rest shape's bend with its neighbours.
        for i in 0..<n {
            let p = (i + 1) % n
            let m = (i + n - 1) % n
            ax[i] += ((x[p] + x[m] - 2 * x[i]) - (tx[p] + tx[m] - 2 * tx[i])) * P.bendK
            ay[i] += ((y[p] + y[m] - 2 * y[i]) - (ty[p] + ty[m] - 2 * ty[i])) * P.bendK
        }
        // Edge springs.
        for i in 0..<n {
            let j = (i + 1) % n
            let dx = x[j] - x[i]
            let dy = y[j] - y[i]
            let h0 = hypot(dx, dy)
            let len = h0 == 0 ? 0.0001 : h0
            let restLen = edge0 * (rest[i] + rest[j]) / (2 * r0)
            let ex = dx / len
            let ey = dy / len
            let relV = (vx[j] - vx[i]) * ex + (vy[j] - vy[i]) * ey
            let f = (len - restLen) * P.edgeK + relV * P.edgeDamp
            ax[i] += ex * f
            ay[i] += ey * f
            ax[j] -= ex * f
            ay[j] -= ey * f
        }
        // A dragged patch rides the finger, rubber-banded.
        if mode == 2 && grab >= 0 {
            var f = fingers[0].down ? fingers[0] : fingers[1]
            if !f.down { f = fingers[0] }
            let dx = f.x - f.startX
            let dy = f.y - f.startY
            let len = hypot(dx, dy)
            let k = len > 0 ? Self.rubber(len, P.pullMax) / len : 0
            for o in -P.grabSpan...P.grabSpan {
                let i = Self.wrap(grab + o, n)
                let w = exp(-Double(o * o) / (2 * P.grabSigma * P.grabSigma))
                let gx = Self.softLimit(grabFromX[i] + dx * k, left + P.wallRoom, right - P.wallRoom, P.wallRoom)
                let gy = Self.softLimit(grabFromY[i] + dy * k, top + P.wallRoom, bottom - P.wallRoom, P.wallRoom)
                ax[i] += w * ((gx - x[i]) * P.grabK - vx[i] * P.grabDamp)
                ay[i] += w * ((gy - y[i]) * P.grabK - vy[i] * P.grabDamp)
            }
        }
        // Soft walls: px, py are how deep a point is into a wall's cushion, signed the way it is pushed.
        var hit = 0.0
        let near = P.wallCushion
        for i in 0..<n {
            let px = x[i] < left + near ? left + near - x[i] : x[i] > right - near ? right - near - x[i] : 0
            let py = y[i] < top + near ? top + near - y[i] : y[i] > bottom - near ? bottom - near - y[i] : 0
            if px != 0 {
                hit = max(hit, -Self.sign(px) * vx[i])
                let grip = Self.wallHold(abs(px))
                ax[i] += px * Self.wallStiffness(abs(px)) - (Self.sign(px) * vx[i] < 0 ? vx[i] * P.wallDamp * grip : 0)
                ay[i] -= vy[i] * P.wallGrip * grip
            }
            if py != 0 {
                hit = max(hit, -Self.sign(py) * vy[i])
                let grip = Self.wallHold(abs(py))
                ay[i] += py * Self.wallStiffness(abs(py)) - (Self.sign(py) * vy[i] < 0 ? vy[i] * P.wallDamp * grip : 0)
                ax[i] -= vx[i] * P.wallGrip * grip
            }
        }
        // A finger that landed outside the clay pushes the rim in on a soft contact spring.
        if mode == 1 {
            for f in fingers where f.down && f.dent < 0 {
                for i in 0..<n {
                    let dx = x[i] - f.x
                    let dy = y[i] - f.y
                    let d = hypot(dx, dy)
                    if d < P.fingerR && d > 0.0001 {
                        let vn = (vx[i] * dx + vy[i] * dy) / d
                        let push = (P.fingerR - d) * P.contactK - (vn < 0 ? vn * P.contactDamp : 0)
                        ax[i] += push * dx / d
                        ay[i] += push * dy / d
                    }
                }
            }
        }
        // A dent near the rim notches it: the rim points it reaches are drawn in toward the centre.
        for dent in dents where dent.live && dent.depth >= 0.02 {
            let reach = hypot(dent.x, dent.y)
            let facing = atan2(dent.y, dent.x)
            let d = dent.depth * dent.depth * (3 - 2 * dent.depth)
            for i in 0..<n {
                let ang = Double(i) / Double(n) * Double.pi * 2
                var da = ang - facing
                da -= (da / (Double.pi * 2)).rounded(.toNearestOrAwayFromZero) * Double.pi * 2
                let along = da * rest[i] / P.dentRadius
                if along > 2.6 || along < -2.6 { continue }
                let over = Self.clamp((reach + P.dentRadius * 0.9 - rest[i]) / (P.dentRadius * 0.9), 0, 1)
                if over <= 0 { continue }
                let pull = P.notch * d * over * exp(-along * along) * P.notchK
                ax[i] -= cosA[i] * pull
                ay[i] -= sinA[i] * pull
            }
        }
        // Integrate (semi-implicit Euler).
        for i in 0..<n {
            vx[i] += ax[i] * h
            vy[i] += ay[i] * h
            x[i] += vx[i] * h
            y[i] += vy[i] * h
        }
        // The far safety rail, for a point thrown wildly; the soft walls do the real work.
        for i in 0..<n {
            x[i] = Self.clamp(x[i], left - P.wallRail, right + P.wallRail)
            y[i] = Self.clamp(y[i], top - P.wallRail, bottom + P.wallRail)
        }
        if flying && hit > P.thudSpeed && time - thudAt > 0.4 {
            thudAt = time
            flying = false
            events |= P.eventThud
            eventsByUser = throwByUser
        }

        // Plasticity: rest radii yield to big pushes, creep back round and smooth along the ring.
        for i in 0..<n {
            let d = (x[i] - cx) * cosA[i] + (y[i] - cy) * sinA[i]
            let e = d - rest[i]
            var r = rest[i]
            // A pinch holds the clay to its own shape; what it leaves behind is set when it lets go.
            if mode == 3 {
                ax[i] = r
                continue
            }
            if e > P.yield { r += (e - P.yield) * P.yieldRate * h } else if e < -P.yield { r += (e + P.yield) * P.yieldRate * h }
            r += (r0 - r) * P.recoverRate * h
            r += (rest[(i + 1) % n] + rest[(i + n - 1) % n] - 2 * rest[i]) * P.smoothRate * h
            ax[i] = r
        }
        // A press spreads the clay, most on the finger's side, and the volume with it.
        spreadArea -= spreadArea * P.recoverRate * h
        let wedge = sin(2 * Double.pi / Double(n))
        if mode == 1 {
            for f in fingers where f.down && f.dent >= 0 {
                let dent = dents[f.dent]
                if !dent.growing { continue }
                let rate = (1 - dent.depth) * P.dentGrow * h
                let off = hypot(dent.x, dent.y) / r0
                for i in 0..<n {
                    let ang = Double(i) / Double(n) * Double.pi * 2
                    let facing = max(0, cos(ang - pressSide))
                    let grow = P.spread * rate * (0.35 + 0.65 * facing * facing * off)
                    ax[i] += grow
                    spreadArea += rest[i] * grow * wedge
                }
            }
        }
        for i in 0..<n { rest[i] = Self.clamp(ax[i], r0 * 0.55, r0 * 1.7) }
    }

    /// Advances dents, the colour change and the body by one frame of `frameDt` seconds.
    func step(_ frameDt: Double) {
        let dt = min(frameDt, P.maxFrame)
        time += dt
        for d in dents where d.live {
            if d.growing {
                d.depth += (1 - d.depth) * min(1, P.dentGrow * dt)
                d.ridge = d.depth
            } else {
                d.since += dt
                d.ridge = max(0, d.ridge - dt / P.ridgeFade)
                if d.since > P.dentHold { d.depth = max(0, d.depth - dt / P.dentRecover) }
                if d.depth <= 0 { d.live = false }
            }
        }
        if recolour < 1 {
            recolour = min(1, recolour + dt / P.recolourTime)
            if recolour >= 1 { colourFrom = colourTo }
        }
        acc += dt
        var steps = 0
        while acc >= P.substep && steps < 10 {
            substep(P.substep)
            acc -= P.substep
            steps += 1
        }
        centroid()
        // A guard: if anything ever went non-finite, the clay is laid back down round at home.
        if !cx.isFinite || !cy.isFinite || !cvx.isFinite || !cvy.isFinite { heal() }
    }

    /// Lays the clay back down round and still at home, keeping its colour.
    private func heal() {
        let n = P.points
        for i in 0..<n {
            x[i] = homeX + cosA[i] * r0
            y[i] = homeY + sinA[i] * r0
            vx[i] = 0
            vy[i] = 0
            rest[i] = r0
        }
        spreadArea = 0
        acc = 0
        mode = 0
        grab = -1
        flying = false
        for f in fingers { f.down = false }
        for d in dents { d.live = false }
        centroid()
    }
}
