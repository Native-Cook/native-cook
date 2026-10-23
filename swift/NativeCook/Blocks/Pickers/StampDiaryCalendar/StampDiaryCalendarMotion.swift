import Foundation

/// Stamp Diary Calendar motion, ported value for value from motion.ts. Every value on screen is a
/// pure function of the story time tau (seconds); autoPlay runs tau = (clock - start) mod loop, so
/// tau = loop equals tau = 0 (the pin).
///
///   0.00-0.90  rest = the pin
///   0.90-2.00  PEEL: the May 12 stamp rises off its cell while its bottom-right corner curls back
///   2.00-3.00  FLIGHT: it lets go, the curl springs flat, it flutters up an S-curve into the collage
///   3.00       IMPACT: it slaps down on its copy in the collage
///   3.02-4.10  INK: a postmark spreads from the ring, bleeds into the paper, dries
///   ...-5.50   hold
///   5.50-6.62  RETURN: the ink lifts, the stamp flies home on a lower arc
///   6.62-7.12  RESTICK: it lays down corner last and presses flat
///   7.12-8.60  rest
enum StampDiaryCalendarMotion {
    static let loop = 8.6

    enum T {
        static let peelStart = 0.9
        static let detach = 2.0
        static let land = 3.0
        static let ink = 3.02
        static let holdEnd = 5.5
        static let returnFlight = 5.58
        static let restick = 6.62
        static let rest = 7.12
    }

    enum Peel {
        static let lift = 1.7
        static let liftShare = 0.5
        static let rise = 3.0
        static let amount = 0.8
        static let reach = 1.2
        static let wobble = 0.12
        static let radiusPerDistance = 0.22
        static let radiusMax = 0.16
        static let shadow = 0.55
        static let release = 0.28
    }

    enum Flight {
        static let ease = (0.4, 0.0, 0.55, 0.92)
        static let sizeEase = (0.3, 0.0, 0.3, 1.0)
        static let bump = 0.16
        static let rotZ = 10.0
        static let rotX = 24.0
        static let rotY = 20.0
        static let bend = 0.8
        static let bendDepth = 0.3
        static let bendRadius = 0.55
        static let sheen = 0.32
        static let outX = 70.0, outY = -150.0
        static let inX = 110.0, inY = 40.0
        static let perspective = 520.0
    }

    enum Impact {
        static let attack = 0.045
        static let decay = 0.09
        static let period = 0.32
        static let squashX = 0.05
        static let squashY = 0.07
        static let nudge = 0.012
    }

    enum Ink {
        static let spread = 0.34
        static let bleed = 1.1
        static let thin = 0.5
        static let bleedBy = 0.13
        static let dryAfter = 0.25
        static let dry = 1.4
        static let lift = 0.34
        static let liftBy = 0.62
    }

    enum Return {
        static let preLift = 1.04
        static let lift = 1.3
        static let flutter = 0.55
        static let outX = -80.0, outY = 70.0
        static let inX = -70.0, inY = -130.0
        static let corner = 0.3
        static let cornerFrom = 0.7
        static let undershoot = 0.025
    }

    enum PileDim {
        static let to = 0.5
        static let inAt = T.land - 0.45
        static let inFor = 0.6
        static let outAt = T.holdEnd + 0.05
        static let outFor = 0.55
    }

    enum Reveal {
        static let inAt = T.detach - 0.05
        static let inFor = 0.25
        static let outAt = 6.55
        static let outFor = 0.25
    }

    enum Touch {
        static let commitShare = 0.42
        static let commitVelocity = -500.0
        static let slop = 6.0
        static let liftIn = 0.22
        static let snapTau = 0.07
        static let snapFor = 0.5
        static let resumeAfter = 2.0
        static let returnRate = 1.3
        static let fade = 0.2
    }

    static let stampPad = 0.55

    // MARK: small helpers

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    static func ease01(_ v: Double) -> Double {
        let x = clamp01(v)
        return x * x * (3 - 2 * x)
    }

    static func easeOut3(_ v: Double) -> Double {
        let x = 1 - clamp01(v)
        return 1 - x * x * x
    }

    /// CSS-style cubic-bezier(x1, y1, x2, y2) evaluated at x (Newton, then bisection).
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        let t0 = clamp01(x)
        if t0 == 0 || t0 == 1 { return t0 }
        let cx = 3 * x1
        let bx = 3 * (x2 - x1) - cx
        let ax = 1 - cx - bx
        let cy = 3 * y1
        let by = 3 * (y2 - y1) - cy
        let ay = 1 - cy - by
        var t = t0
        for _ in 0..<6 {
            let fx = ((ax * t + bx) * t + cx) * t - t0
            let d = (3 * ax * t + 2 * bx) * t + cx
            if abs(fx) < 1e-5 || abs(d) < 1e-6 { break }
            t -= fx / d
        }
        if t < 0 || t > 1 {
            var lo = 0.0
            var hi = 1.0
            t = t0
            for _ in 0..<24 {
                let fx = ((ax * t + bx) * t + cx) * t
                if fx < t0 { lo = t } else { hi = t }
                t = (lo + hi) / 2
            }
        }
        return ((ay * t + by) * t + cy) * t
    }

    static func cubicPoint(_ p0: Double, _ p1: Double, _ p2: Double, _ p3: Double, _ t: Double) -> Double {
        let u = 1 - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }

    /// 0 at both ends, 1 in the middle, smooth (sin squared).
    static func envelope(_ u: Double) -> Double {
        let s = sin(Double.pi * clamp01(u))
        return s * s
    }

    /// Impact wobble k(t), t = seconds since contact: a fast attack then a damped cosine.
    static func impact(_ t: Double) -> Double {
        if t < 0 { return 0 }
        if t < Impact.attack { return sin((Double.pi / 2) * (t / Impact.attack)) }
        let s = t - Impact.attack
        if s > 0.9 { return 0 }
        return exp(-s / Impact.decay) * cos((2 * Double.pi * s) / Impact.period)
    }

    /// Critically damped return 1 -> 0 (snap back after a cancelled peel).
    static func settle(_ t: Double) -> Double {
        if t <= 0 { return 1 }
        let x = t / Touch.snapTau
        return (1 + x) * exp(-x)
    }

    // MARK: calendar (Gregorian, Sunday first)

    struct Month: Equatable {
        var year: Int
        var month: Int
    }

    static let may2026 = Month(year: 2026, month: 5)

    static func isMay2026(_ m: Month) -> Bool { m.year == 2026 && m.month == 5 }

    static func addMonths(_ m: Month, _ delta: Int) -> Month {
        let index = m.year * 12 + (m.month - 1) + delta
        let year = Int(floor(Double(index) / 12))
        return Month(year: year, month: ((index % 12) + 12) % 12 + 1)
    }

    static func daysIn(_ m: Month) -> Int {
        if m.month == 2 {
            let leap = (m.year % 4 == 0 && m.year % 100 != 0) || m.year % 400 == 0
            return leap ? 29 : 28
        }
        return m.month == 4 || m.month == 6 || m.month == 9 || m.month == 11 ? 30 : 31
    }

    /// Weekday of the 1st, 0 = Sunday (Sakamoto).
    static func firstWeekday(_ m: Month) -> Int {
        let table = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4]
        let y = m.month < 3 ? m.year - 1 : m.year
        let a = y + Int(floor(Double(y) / 4)) - Int(floor(Double(y) / 100)) + Int(floor(Double(y) / 400))
        return (a + table[m.month - 1] + 1) % 7
    }

    static func cellOf(_ day: Int, _ m: Month) -> (column: Int, row: Int) {
        let index = firstWeekday(m) + day - 1
        return (index % 7, index / 7)
    }

    static func cellCenter(_ day: Int, _ m: Month) -> (x: Double, y: Double) {
        let c = cellOf(day, m)
        return ((Double(c.column) + 0.5) * StampDiaryCalendarTheme.columnWidth,
                StampDiaryCalendarTheme.gridTop + (Double(c.row) + 0.5) * StampDiaryCalendarTheme.rowHeight)
    }

    static func dayAt(_ x: Double, _ y: Double, _ m: Month) -> Int {
        let g = StampDiaryCalendarTheme.self
        if x < 0 || x >= 402 || y < g.gridTop || y >= g.gridTop + g.rowHeight * Double(g.rows) { return 0 }
        let column = min(6, Int(floor(x / g.columnWidth)))
        let row = min(g.rows - 1, Int(floor((y - g.gridTop) / g.rowHeight)))
        let day = row * 7 + column - firstWeekday(m) + 1
        return day >= 1 && day <= daysIn(m) ? day : 0
    }

    static func memoryIndex(_ day: Int, _ m: Month) -> Int {
        if !isMay2026(m) { return -1 }
        for (i, mem) in StampDiaryCalendarData.memories.enumerated() where mem.day == day { return i }
        return -1
    }

    /// Where a memory lands: on its copy in the collage when that copy is sharp, otherwise the free spot.
    static func landingQuad(_ memory: Int) -> StampDiaryCalendarQuad {
        let twin = StampDiaryCalendarData.memories[memory].twin
        if twin >= 0 && StampDiaryCalendarData.pile[twin].blurBottom == 0 { return StampDiaryCalendarData.pile[twin].quad }
        return StampDiaryCalendarData.freeSpot
    }

    static func holesOfQuad(_ q: StampDiaryCalendarQuad) -> (across: Int, down: Int) {
        (10, max(4, Int((10 * q.h / q.w).rounded())))
    }

    static func holesOf(_ memory: Int) -> (across: Int, down: Int) { holesOfQuad(landingQuad(memory)) }

    // MARK: curl

    /// The paper rolls around a cylinder of radius r whose axis is the line dot(p, n) = a in the
    /// stamp's own centred coordinates; the side where dot(p, n) > a is lifted and folded back.
    struct Curl: Equatable {
        var nx: Double
        var ny: Double
        var a: Double
        var r: Double
    }

    static let flat = Curl(nx: 1, ny: 0, a: 1e4, r: 0)

    /// A corner c pulled to the finger f: the fold is the bisector, so the corner lands on the finger.
    static func curlFromFinger(_ cx: Double, _ cy: Double, _ fx: Double, _ fy: Double, _ w: Double, _ h: Double) -> Curl {
        let dx = cx - fx
        let dy = cy - fy
        let d = hypot(dx, dy)
        if d < 0.01 { return flat }
        let nx = dx / d
        let ny = dy / d
        let r = min(Peel.radiusMax * max(w, h), Peel.radiusPerDistance * d)
        let sc = (d + Double.pi * r) / 2
        return Curl(nx: nx, ny: ny, a: cx * nx + cy * ny - sc, r: r)
    }

    /// The autoplay finger: from the bottom-right corner towards the top left, with a slight drift.
    static func peelCurl(_ amount: Double, _ w: Double, _ h: Double) -> Curl {
        let base = atan2(-h, -w) + Peel.wobble * sin(Double.pi * amount)
        let travel = amount * Peel.reach * hypot(w, h)
        let cx = w / 2
        let cy = h / 2
        return curlFromFinger(cx, cy, cx + cos(base) * travel, cy + sin(base) * travel, w, h)
    }

    /// Paper bending in the air: a wide roll along an edge whose direction turns during the flight.
    static func bendCurl(_ angle: Double, _ bend: Double, _ w: Double, _ h: Double) -> Curl {
        if bend <= 0.001 { return flat }
        let nx = cos(angle)
        let ny = sin(angle)
        let extent = abs(nx) * w * 0.5 + abs(ny) * h * 0.5
        return Curl(nx: nx, ny: ny, a: extent - bend * Flight.bendDepth * min(w, h), r: Flight.bendRadius * max(w, h))
    }

    /// Blend two curls (direction by angle, then offset and radius). A flat curl blends by its offset.
    static func blendCurl(_ from: Curl, _ to: Curl, _ t: Double, _ w: Double, _ h: Double) -> Curl {
        if t <= 0 { return from }
        if t >= 1 { return to }
        let fromFlat = from.r <= 0
        let toFlat = to.r <= 0
        if fromFlat && toFlat { return flat }
        let ref = fromFlat ? to : from
        let out = toFlat ? from : to
        let refExtent = abs(ref.nx) * w * 0.5 + abs(ref.ny) * h * 0.5
        let outExtent = abs(out.nx) * w * 0.5 + abs(out.ny) * h * 0.5
        let f = fromFlat ? Curl(nx: ref.nx, ny: ref.ny, a: refExtent + 1, r: ref.r) : from
        let g = toFlat ? Curl(nx: out.nx, ny: out.ny, a: outExtent + 1, r: out.r) : to
        var a0 = atan2(f.ny, f.nx)
        let a1 = atan2(g.ny, g.nx)
        var da = a1 - a0
        while da > Double.pi { da -= 2 * Double.pi }
        while da < -Double.pi { da += 2 * Double.pi }
        a0 += da * t
        return Curl(nx: cos(a0), ny: sin(a0), a: mix(f.a, g.a, t), r: mix(f.r, g.r, t))
    }

    // MARK: pose

    struct Pose: Equatable {
        var active = 0.0
        var memory = 0
        var cx = 0.0
        var cy = 0.0
        var w = 0.0
        var h = 0.0
        var skew = 0.0
        var lift = 1.0
        var sx = 1.0
        var sy = 1.0
        var rotX = 0.0
        var rotY = 0.0
        var rotZ = 0.0
        var curl = StampDiaryCalendarMotion.flat
        var peelShadow = 0.0
        var sheenPos = 0.0
        var sheenAmt = 0.0
        var height = 0.0
        var inkSpread = 0.0
        var inkThreshold = 1.0
        var inkWet = 0.0
        var inkOn = 0.0
        var opacity = 1.0
        var pileDim = 1.0
        var pileNudge = 0.0
        var reveal = 0.0
    }

    static func restPose(_ memory: Int) -> Pose {
        let m = StampDiaryCalendarData.memories[memory]
        let c = cellCenter(m.day, may2026)
        var p = Pose()
        p.memory = memory
        p.cx = c.x
        p.cy = c.y
        p.w = m.w
        p.h = m.h
        return p
    }

    private static func quadCenter(_ q: StampDiaryCalendarQuad) -> (x: Double, y: Double) {
        (q.x + q.w / 2, q.y + q.h / 2 + q.dy / 2)
    }

    /// The peel's curl at the moment it lets go (autoPlay), the default start of the release blend.
    static func detachCurl(_ memory: Int) -> Curl {
        let m = StampDiaryCalendarData.memories[memory]
        return peelCurl(Peel.amount, m.w, m.h)
    }

    private static func applyInk(_ pose: inout Pose, _ tau: Double) {
        if tau < T.ink { return }
        let t = tau - T.ink
        pose.inkOn = 1
        pose.inkSpread = easeOut3(t / Ink.spread)
        pose.inkThreshold = Ink.thin - Ink.bleedBy * easeOut3(t / Ink.bleed) + Ink.liftBy * ease01((tau - T.holdEnd) / Ink.lift)
        pose.inkWet = 1 - ease01((t - Ink.dryAfter) / Ink.dry)
        if pose.inkThreshold > 1.05 { pose.inkOn = 0 }
    }

    /// The story pose at tau. `release` is the curl the stamp had when it let go.
    static func storyPose(_ memory: Int, _ tau: Double, _ release: Curl) -> Pose {
        var pose = restPose(memory)
        if tau < T.peelStart || tau >= T.rest { return pose }
        let m = StampDiaryCalendarData.memories[memory]
        let cell = cellCenter(m.day, may2026)
        let q = landingQuad(memory)
        let land = quadCenter(q)
        let landSkew = q.dy / q.w
        pose.active = 1
        pose.reveal = ease01((tau - Reveal.inAt) / Reveal.inFor) * (1 - ease01((tau - Reveal.outAt) / Reveal.outFor))
        pose.pileDim = mix(1, PileDim.to, ease01((tau - PileDim.inAt) / PileDim.inFor) * (1 - ease01((tau - PileDim.outAt) / PileDim.outFor)))

        if tau < T.detach {
            let p = (tau - T.peelStart) / (T.detach - T.peelStart)
            let rise = easeOut3(p / Peel.liftShare)
            pose.lift = mix(1, Peel.lift, rise)
            pose.cy = cell.y - Peel.rise * rise
            pose.curl = peelCurl(Peel.amount * bezier(0.5, 0, 0.6, 1, p), m.w, m.h)
            pose.peelShadow = Peel.shadow
            pose.height = 0.25 * rise
            return pose
        }

        if tau < T.land {
            let u = (tau - T.detach) / (T.land - T.detach)
            let e = bezier(Flight.ease.0, Flight.ease.1, Flight.ease.2, Flight.ease.3, u)
            let se = bezier(Flight.sizeEase.0, Flight.sizeEase.1, Flight.sizeEase.2, Flight.sizeEase.3, u)
            let env = envelope(u)
            let x0 = cell.x
            let y0 = cell.y - Peel.rise
            pose.cx = cubicPoint(x0, x0 + Flight.outX, land.x + Flight.inX, land.x, e)
            pose.cy = cubicPoint(y0, y0 + Flight.outY, land.y + Flight.inY, land.y, e)
            pose.w = mix(m.w, q.w, se)
            pose.h = mix(m.h, q.h, se)
            pose.skew = landSkew * se
            pose.lift = mix(Peel.lift, 1, se) * (1 + Flight.bump * env)
            pose.rotZ = Flight.rotZ * env * sin(2 * Double.pi * 1.15 * u)
            pose.rotX = Flight.rotX * env * sin(2 * Double.pi * 0.9 * u + 0.5)
            pose.rotY = -Flight.rotY * env * sin(2 * Double.pi * 1.3 * u + 1.3)
            let bend = bendCurl(0.7 + 4.5 * u, Flight.bend * env, pose.w, pose.h)
            pose.curl = blendCurl(release, bend, ease01((tau - T.detach) / Peel.release), pose.w, pose.h)
            pose.peelShadow = Peel.shadow * (1 - ease01((tau - T.detach) / Peel.release))
            pose.sheenAmt = Flight.sheen * env
            pose.sheenPos = 0.5 + 0.9 * sin(2 * Double.pi * 0.9 * u + 0.5)
            pose.height = max(0.25 * (1 - e), env)
            return pose
        }

        if tau < T.holdEnd {
            let k = impact(tau - T.land)
            pose.cx = land.x
            pose.cy = land.y
            pose.w = q.w
            pose.h = q.h
            pose.skew = landSkew
            pose.sx = 1 + Impact.squashX * k
            pose.sy = 1 - Impact.squashY * k
            pose.pileNudge = Impact.nudge * k
            applyInk(&pose, tau)
            return pose
        }

        if tau < T.restick {
            let pre = ease01((tau - T.holdEnd) / (T.returnFlight - T.holdEnd))
            let u = clamp01((tau - T.returnFlight) / (T.restick - T.returnFlight))
            let e = bezier(0.45, 0, 0.3, 1, u)
            let env = envelope(u) * Return.flutter
            let x3 = cell.x
            let y3 = cell.y - Peel.rise
            pose.cx = cubicPoint(land.x, land.x + Return.outX, x3 + Return.inX, x3, e)
            pose.cy = cubicPoint(land.y, land.y + Return.outY, y3 + Return.inY, y3, e)
            pose.w = mix(q.w, m.w, e)
            pose.h = mix(q.h, m.h, e)
            pose.skew = landSkew * (1 - e)
            pose.lift = mix(mix(1, Return.preLift, pre), Return.lift, e) * (1 + Flight.bump * env)
            pose.rotZ = -Flight.rotZ * env * sin(2 * Double.pi * 1.0 * u + 0.4)
            pose.rotX = Flight.rotX * env * sin(2 * Double.pi * 0.8 * u + 2.1)
            pose.rotY = Flight.rotY * env * sin(2 * Double.pi * 1.1 * u + 0.2)
            let bend = bendCurl(3.6 - 3.5 * u, Flight.bend * envelope(u) * Return.flutter, pose.w, pose.h)
            let corner = peelCurl(Return.corner, pose.w, pose.h)
            let share = ease01((u - Return.cornerFrom) / (1 - Return.cornerFrom))
            pose.curl = blendCurl(bend, corner, share, pose.w, pose.h)
            pose.peelShadow = Peel.shadow * share
            pose.sheenAmt = Flight.sheen * env
            pose.sheenPos = 0.5 + 0.9 * sin(2 * Double.pi * 0.8 * u + 2.1)
            pose.height = max(0.3 * pre * (1 - u), envelope(u), 0.3 * e)
            applyInk(&pose, tau)
            return pose
        }

        // Restick: the corner lays down last, the stamp sinks to the paper and presses in.
        let r = clamp01((tau - T.restick) / (T.rest - T.restick))
        let e = easeOut3(r)
        pose.curl = peelCurl(Return.corner * (1 - e), m.w, m.h)
        pose.peelShadow = Peel.shadow * (1 - e)
        pose.lift = 1 + (Return.lift - 1) * (1 - e) - Return.undershoot * sin(Double.pi * e)
        pose.cy = cell.y - Peel.rise * (1 - e)
        pose.height = 0.3 * (1 - e)
        return pose
    }

    /// A finger peeling a memory in its cell: lift by time since touch-down, curl from the finger.
    static func fingerPose(_ memory: Int, held: Double, cornerX: Double, cornerY: Double, fingerX: Double, fingerY: Double) -> Pose {
        var pose = restPose(memory)
        let m = StampDiaryCalendarData.memories[memory]
        let rise = easeOut3(held / Touch.liftIn)
        pose.active = 1
        pose.lift = mix(1, Peel.lift, rise)
        pose.cy -= Peel.rise * rise
        pose.curl = curlFromFinger(cornerX, cornerY, fingerX, fingerY, m.w, m.h)
        pose.peelShadow = Peel.shadow
        pose.height = 0.25 * rise
        return pose
    }

    /// Reduced motion: the stamp fades in on its landing spot, postmarked; v = 0...1 shown.
    static func quietPose(_ memory: Int, _ v: Double) -> Pose {
        var pose = restPose(memory)
        if v <= 0 { return pose }
        let q = landingQuad(memory)
        let land = quadCenter(q)
        pose.active = 1
        pose.cx = land.x
        pose.cy = land.y
        pose.w = q.w
        pose.h = q.h
        pose.skew = q.dy / q.w
        pose.opacity = v
        pose.inkOn = 1
        pose.inkSpread = 1
        pose.inkThreshold = Ink.thin - Ink.bleedBy
        pose.reveal = v
        pose.pileDim = mix(1, PileDim.to, v)
        return pose
    }

    /// Share of the diagonal a finger has peeled (for the commit test).
    static func peelShare(_ memory: Int, _ dx: Double, _ dy: Double) -> Double {
        let m = StampDiaryCalendarData.memories[memory]
        return hypot(dx, dy) / hypot(m.w, m.h)
    }
}
