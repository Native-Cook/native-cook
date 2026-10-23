import Foundation

/// Taste Profile motion, ported value for value from react-native/.../taste-profile/motion.ts.
/// Every animated value is a pure function of time (loop time in autoPlay, time since the touch otherwise).
enum TasteProfileMotion {
    static let loop = 10.0
    static let script = [(press: 0.7, commit: 0.82, city: 2), (press: 6.6, commit: 6.72, city: 0)]
    static let strip = (down: 2.55, release: 2.9, dragTo: 0.55, to: 2, flatAt: 3.55)
    static let resumeAfter = 2.0
    static let settle = 1.6
    static let cities = ["all", "new york", "london", "paris"]
    static let rank = [(face: 0, word: 0, back: 0), (face: 0, word: 0, back: 0), (face: 1, word: 1, back: 0), (face: 1, word: 2, back: 1)]
    static let rankWords = ["new york", "london", "paris"]
    static let stats = [["125", "764", "183", "17"], ["92", "518", "120", "11"], ["21", "146", "40", "4"], ["12", "100", "23", "2"]]

    enum Press {
        static let scale = 0.97
        static let inside = 0.12
        static let outside = 0.16
    }

    enum Pill {
        static let lead = Spring(omega: 21, zeta: 0.84)
        static let trail = Spring(omega: 12.5, zeta: 0.92)
    }

    enum Coin {
        static let delay = 0.06
        static let flip = 0.56
        static let lift = 12.0
        static let grow = 0.08
        static let shadow = 0.16
        static let step = 10.0
        static let frames = 19
    }

    enum Label {
        static let delay = 0.18
        static let duration = 0.36
        static let travel = 14.0
    }

    enum Follow {
        static let duration = 0.420
        static let dampingRatio = 0.82
        static let grow = 54.0
        static let omega = durationOmega(duration: duration, zeta: dampingRatio)
        static let settleTime = duration * 1.5
    }

    enum Reduced {
        static let fade = 0.20
        static let dip = 0.45
    }

    // MARK: Departure board

    static let drum = Array(" 0123456789")

    enum Board {
        static let perspDigit = 140.0
        static let perspPhoto = 520.0
        static let digitDelay = 0.06
        static let digitTileStagger = 0.07
        static let digitSlotStagger = 0.03
        static let digitStep = 0.075
        static let digitCardIn = 0.09
        static let digitCardOut = 0.24
        static let digitCard = Card(w: 42, padY: 10, r: 7, spread: 13)
        static let countDelay = 0.32
        static let countSlotStagger = 0.03
        static let countStep = 0.075
        static let countCard = Card(w: 25, padY: 6, r: 4, spread: 0)
        static let photoDelay = 0.1
        static let photoCellStagger = 0.08
        static let photoStep = 0.17
        static let photoSteps = 3
        static let bounce = (amp: 16.0, omega: 34.0, zeta: 0.34, dur: 0.28)
        static let shutter = 1.0 / 60
        static let ghostAlpha = [0.34, 0.2, 0.09]
        static let flapDark = 0.42
        static let flapLight = 0.16
        static let glint = 0.22
        static let uncover = 0.32
        static let cover = 0.36
        static let seam = 0.38
    }

    struct Card {
        let w: Double
        let padY: Double
        let r: Double
        let spread: Double
    }

    /// Photo strip contents: indices into TasteProfileAssets.photos.
    static let stripPhotos: [[Int]] = [
        [0, 1, 2, 3, 4, 10, 16, 5],
        [4, 5, 6, 7, 8, 9],
        [10, 11, 12, 13, 14, 15],
        [16, 17, 18, 19, 20, 21],
    ]
    static let photoCount = ["1470", "958", "312", "200"]

    enum Cover {
        static let rotate = 52.0
        static let span = 1.6
        static let squeeze = 0.2
        static let shrink = 0.1
        static let dim = 0.3
        static let radius = 0.7
        static let inset = 0.05
        static let grow = 60.0
        static let inDuration = 0.22
        static let outDuration = 0.30
        static let project = 0.22
        static let rubber = 0.35
        static let snapDuration = 0.52
        static let snapZeta = 0.82
        static let takeoverDuration = 0.40
    }

    enum Expand {
        static let hold = 0.35
        static let spring = Spring(omega: 17, zeta: 0.84)
        static let out = 0.24
        static let barDelay = 0.1
        static let barStagger = 0.045
        static let barSpring = Spring(omega: 19, zeta: 0.72)
        static let shadowFrom = 0.05
        static let shadowTo = 0.16
        static let hlDuration = 0.12
    }

    static let months = ["may", "jun", "jul", "aug", "sep", "oct"]
    private static let share: [[Double]] = [
        [0.11, 0.14, 0.12, 0.18, 0.2, 0.25],
        [0.13, 0.12, 0.17, 0.16, 0.19, 0.23],
        [0.2, 0.18, 0.15, 0.16, 0.14, 0.17],
        [0.24, 0.12, 0.2, 0.14, 0.18, 0.12],
    ]

    static func monthBars(city: Int, tile: Int) -> [Int] {
        let total = Int(stats[city][tile]) ?? 0
        var out: [Int] = []
        var used = 0
        for index in 0..<6 {
            let value = index == 5 ? total - used : Int((Double(total) * share[tile][index]).rounded())
            out.append(value)
            used += value
        }
        return out
    }

    struct Hold {
        var tile: Int
        var t0: Double
        var t1: Double
        var t2: Double
        static let none = Hold(tile: -1, t0: -1, t1: -1, t2: -1)
        static let script = Hold(tile: 0, t0: 4.35, t1: 4.7, t2: 6.0)
    }

    struct HoldView: Equatable {
        var tile = -1
        var press = 0.0
        var e = 0.0
        var since = 0.0
        var hl = 5.0
    }

    // MARK: Types

    struct Spring {
        let omega: Double
        let zeta: Double
    }

    struct Change {
        var from: Int
        var to: Int
        var tau: Double
        var l0: Double
        var r0: Double
        var a0: Double
        var back0: Int
        var x0: Double
        var pressChip: Int
        var press: Double
        var reduced: Bool
    }

    struct CoinPose {
        let angle: Double
        let back: Int
        let lift: Double
    }

    struct RollStyle {
        let y: Double
        let opacity: Double
    }

    struct RollGroup {
        let layerOf: [Int]
        let values: [Int]
        let start: Double
        let duration: Double
        let stagger: Double
        let travel: Double
    }

    // MARK: Curves

    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }
    static func segment(_ time: Double, _ start: Double, _ end: Double) -> Double { clamp01((time - start) / (end - start)) }
    static func lerp(_ a: Double, _ b: Double, _ progress: Double) -> Double { a + (b - a) * progress }

    static func bezier(_ progress: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp01(progress)
        if x == 0 || x == 1 { return x }
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let middle = (low + high) / 2
            let inverse = 1 - middle
            let at = 3 * inverse * inverse * middle * x1 + 3 * inverse * middle * middle * x2 + middle * middle * middle
            if at < x { low = middle } else { high = middle }
        }
        let middle = (low + high) / 2
        let inverse = 1 - middle
        return 3 * inverse * inverse * middle * y1 + 3 * inverse * middle * middle * y2 + middle * middle * middle
    }

    static func easeOut(_ value: Double) -> Double { bezier(value, 0.23, 1, 0.32, 1) }
    static func easeInOut(_ value: Double) -> Double { bezier(value, 0.77, 0, 0.175, 1) }

    static func spring(_ tau: Double, _ spring: Spring) -> Double {
        guard tau > 0 else { return 0 }
        let damped = spring.omega * sqrt(1 - spring.zeta * spring.zeta)
        let envelope = exp(-spring.zeta * spring.omega * tau)
        let value = 1 - envelope * (cos(damped * tau) + ((spring.zeta * spring.omega) / damped) * sin(damped * tau))
        return envelope < 1e-4 ? 1 : value
    }

    /// A spring from `from` to `to` with initial velocity `v0` (units/s), `tau` s after it started.
    static func springV(_ tau: Double, from: Double, to: Double, v0: Double, _ spring: Spring) -> Double {
        guard tau > 0 else { return from }
        let damped = spring.omega * sqrt(1 - spring.zeta * spring.zeta)
        let a = from - to
        let b = (v0 + spring.zeta * spring.omega * a) / damped
        let envelope = exp(-spring.zeta * spring.omega * tau)
        if envelope < 1e-4 { return to }
        return to + envelope * (a * cos(damped * tau) + b * sin(damped * tau))
    }

    /// Reanimated's duration-based spring (`withSpring({ duration, dampingRatio })`): the natural frequency
    /// that makes the energy fall under a threshold after duration × 1.5 (mass 4 solver, as Reanimated does).
    static func durationOmega(duration: Double, zeta: Double) -> Double {
        let mass = 4.0
        let threshold = 6e-9
        let settling = duration * 1.5
        func energyDifference(stiffness: Double) -> Double {
            let x0 = -1.0
            let velocity = 0.0
            let dampedOmega = sqrt(stiffness / mass) * zeta
            let position = (x0 + (velocity + x0 * dampedOmega) * settling) * exp(-dampedOmega * settling)
            let nextVelocity = position * -dampedOmega + (velocity + x0 * dampedOmega) * exp(-dampedOmega * settling)
            let initialEnergy = 0.5 * stiffness * x0 * x0
            let energy = 0.5 * stiffness * position * position + 0.5 * mass * nextVelocity * nextVelocity
            return energy / initialEnergy - threshold
        }
        var low = Double.ulpOfOne
        var high = 8000.0
        let direction = energyDifference(stiffness: high) >= energyDifference(stiffness: low) ? 1.0 : -1.0
        for _ in 0..<100 {
            let middle = (low + high) / 2
            if abs(energyDifference(stiffness: middle)) <= threshold * 1e-3 { return sqrt(middle / mass) }
            if energyDifference(stiffness: middle) * direction < 0 { low = middle } else { high = middle }
        }
        return sqrt(((low + high) / 2) / mass)
    }

    /// Position and velocity of a duration-based spring started at `start` with velocity `velocity`.
    static func durationSpring(start: Double, target: Double, velocity: Double, elapsed: Double,
                               omega: Double, zeta: Double, settleTime: Double) -> (value: Double, velocity: Double) {
        guard elapsed > 0 else { return (start, velocity) }
        if elapsed >= settleTime { return (target, 0) }
        let x0 = start - target
        let damped = omega * sqrt(1 - zeta * zeta)
        let sine = sin(damped * elapsed)
        let cosine = cos(damped * elapsed)
        let envelope = exp(-zeta * omega * elapsed)
        let fragment = envelope * (sine * ((velocity + zeta * omega * x0) / damped) + x0 * cosine)
        let nextVelocity = -zeta * omega * fragment + envelope * (cosine * (velocity + zeta * omega * x0) - damped * x0 * sine)
        return (target + fragment, nextVelocity)
    }

    static func followSpring(start: Double, target: Double, velocity: Double, elapsed: Double) -> (value: Double, velocity: Double) {
        durationSpring(start: start, target: target, velocity: velocity, elapsed: elapsed,
                       omega: Follow.omega, zeta: Follow.dampingRatio, settleTime: Follow.settleTime)
    }

    static func press(at time: Double, down: Double, up: Double) -> Double {
        if time < down { return 0 }
        if time < up { return easeOut(segment(time, down, down + Press.inside)) }
        let held = easeOut(segment(up, down, down + Press.inside))
        return held * (1 - easeOut(segment(time, up, up + Press.outside)))
    }

    // MARK: City change

    static func chipEdges(_ index: Int) -> (Double, Double) {
        let chip = TasteProfileTheme.Box.chips[index]
        return (chip.x, chip.x + chip.w)
    }

    static func loopChange(_ time: Double) -> Change {
        let first = script[0]
        let second = script[1]
        let returning = time >= second.commit
        let item = returning ? second : first
        let fromCity = returning ? first.city : 0
        let edges = chipEdges(fromCity)
        let sourceRank = rank[fromCity]
        let firstPress = press(at: time, down: first.press, up: first.commit)
        let secondPress = press(at: time, down: second.press, up: second.commit)
        let pressChip = secondPress > 0 ? second.city : (firstPress > 0 ? first.city : -1)
        let amount = secondPress > 0 ? secondPress : firstPress
        return Change(from: fromCity, to: item.city, tau: time - item.commit, l0: edges.0, r0: edges.1,
                      a0: Double(sourceRank.face) * 180, back0: sourceRank.back,
                      x0: returning ? Double(strip.to) : 0, pressChip: pressChip, press: amount, reduced: false)
    }

    static func pill(_ change: Change) -> (Double, Double) {
        let target = chipEdges(change.to)
        if change.reduced { return change.tau >= Reduced.fade / 2 ? target : (change.l0, change.r0) }
        let rightward = target.0 + target.1 >= change.l0 + change.r0
        let lead = spring(change.tau, Pill.lead)
        let trail = spring(change.tau, Pill.trail)
        let left = lerp(change.l0, target.0, rightward ? trail : lead)
        let right = lerp(change.r0, target.1, rightward ? lead : trail)
        return (left, max(right, left + 1))
    }

    static func pressedPill(_ change: Change) -> (Double, Double) {
        let edges = pill(change)
        guard change.pressChip == change.to, change.press > 0 else { return edges }
        let inset = (edges.1 - edges.0) * (1 - Press.scale) * change.press / 2
        return (edges.0 + inset, edges.1 - inset)
    }

    static func coverage(_ change: Change, chip index: Int) -> Double {
        let edges = pill(change)
        let chip = chipEdges(index)
        return clamp01((min(edges.1, chip.1) - max(edges.0, chip.0)) / (chip.1 - chip.0))
    }

    static func chipScale(_ change: Change, chip index: Int) -> Double {
        change.pressChip == index ? 1 - (1 - Press.scale) * change.press : 1
    }

    static func coin(_ change: Change) -> CoinPose {
        let target = rank[change.to]
        let targetAngle = Double(target.face) * 180
        if change.reduced {
            let swap = change.tau >= Reduced.fade / 2
            return CoinPose(angle: swap ? targetAngle : change.a0, back: swap ? target.back : change.back0, lift: 0)
        }
        let progress = easeInOut(segment(change.tau, Coin.delay, Coin.delay + Coin.flip))
        let lift = sin(.pi * segment(change.tau, Coin.delay, Coin.delay + Coin.flip))
        if change.a0 == 180, targetAngle == 180, change.back0 != target.back {
            let firstHalf = progress < 0.5
            return CoinPose(angle: firstHalf ? lerp(180, 90, progress * 2) : lerp(90, 180, progress * 2 - 1),
                            back: firstHalf ? change.back0 : target.back, lift: lift)
        }
        if change.a0 == targetAngle { return CoinPose(angle: targetAngle, back: target.back, lift: 0) }
        return CoinPose(angle: lerp(change.a0, targetAngle, progress), back: targetAngle == 180 ? target.back : change.back0, lift: lift)
    }

    static func coinFrame(angle: Double, back: Int) -> Int {
        let frame = Int((angle / Coin.step).rounded())
        return frame <= 9 || back == 0 ? frame : Coin.frames + (frame - 10)
    }

    static func rollProgress(local: Double, slot: Int, duration: Double, stagger: Double) -> Double {
        easeOut(clamp01((local - Double(slot) * stagger) / duration))
    }

    static func rollStyle(_ change: Change, group: RollGroup, layer: Int, slot: Int) -> RollStyle {
        let fromLayer = group.layerOf[change.from]
        let toLayer = group.layerOf[change.to]
        if change.reduced {
            let shown = change.tau >= Reduced.fade / 2 ? toLayer : fromLayer
            return RollStyle(y: 0, opacity: layer == shown ? (fromLayer == toLayer ? 1 : reducedDip(change.tau)) : 0)
        }
        if fromLayer == toLayer { return RollStyle(y: 0, opacity: layer == toLayer ? 1 : 0) }
        guard layer == fromLayer || layer == toLayer else { return RollStyle(y: 0, opacity: 0) }
        let direction = group.values[toLayer] >= group.values[fromLayer] ? 1.0 : -1.0
        let progress = rollProgress(local: change.tau - group.start, slot: slot, duration: group.duration, stagger: group.stagger)
        if layer == fromLayer {
            return RollStyle(y: -direction * progress * group.travel, opacity: 1 - clamp01(progress / 0.6))
        }
        return RollStyle(y: direction * (1 - progress) * group.travel, opacity: clamp01((progress - 0.3) / 0.7))
    }

    static func reducedDip(_ tau: Double) -> Double {
        if tau <= 0 || tau >= Reduced.fade { return 1 }
        return 1 - (1 - Reduced.dip) * sin(.pi * tau / Reduced.fade)
    }

    // MARK: Board

    static func drumSteps(_ a: Character, _ b: Character) -> Int {
        let i = drum.firstIndex(of: a) ?? 0
        let j = drum.firstIndex(of: b) ?? 0
        return (j - i + drum.count) % drum.count
    }

    static func drumChar(_ a: Character, _ k: Int) -> Character {
        drum[((drum.firstIndex(of: a) ?? 0) + k) % drum.count]
    }

    static func bounceAt(_ t: Double) -> Double {
        if t <= 0 || t >= Board.bounce.dur { return 0 }
        let damped = Board.bounce.omega * sqrt(1 - Board.bounce.zeta * Board.bounce.zeta)
        return Board.bounce.amp * exp(-Board.bounce.zeta * Board.bounce.omega * t) * abs(sin(damped * t))
    }

    struct Flap {
        let k: Int
        let angle: Double
        let speed: Double
        let bounce: Double
    }

    static func flapAt(local: Double, n: Int, step: Double) -> Flap {
        if n <= 0 || local < 0 { return Flap(k: 0, angle: -1, speed: 0, bounce: 0) }
        let k = Int(floor(local / step))
        if k >= n { return Flap(k: n, angle: -1, speed: 0, bounce: bounceAt(local - Double(n) * step)) }
        let u = local / step - Double(k)
        return Flap(k: k, angle: 180 * u * u, speed: 360 * u / step, bounce: 0)
    }

    // MARK: Strip

    /// The strip in the loop: offset (photos) and separation (0 flat … 1 cover-flow).
    static func stripAt(_ t: Double) -> (x: Double, k: Double) {
        let s = strip
        if t < s.down || t >= script[1].commit { return (0, 0) }
        let k = t < s.flatAt ? easeOut(segment(t, s.down, s.down + Cover.inDuration))
                             : 1 - easeInOut(segment(t, s.flatAt, s.flatAt + Cover.outDuration))
        if t < s.release {
            let p = segment(t, s.down, s.release)
            return (s.dragTo * p * p, k)
        }
        let v0 = 2 * s.dragTo / (s.release - s.down)
        let snap = Spring(omega: 10, zeta: 0.82)
        return (springV(t - s.release, from: s.dragTo, to: Double(s.to), v0: v0, snap), k)
    }

    // MARK: Hold

    static func holdAt(_ t: Double, _ h: Hold) -> (tile: Int, press: Double, e: Double, since: Double) {
        if h.tile < 0 || t < h.t0 { return (-1, 0, 0, 0) }
        let commit = h.t1 >= 0 ? h.t1 : h.t2
        let amount = press(at: t, down: h.t0, up: commit)
        if h.t1 < 0 || t < h.t1 { return (amount > 0 ? h.tile : -1, amount, 0, 0) }
        var e = spring(min(t, h.t2) - h.t1, Expand.spring)
        if t > h.t2 { e *= 1 - easeOut(segment(t, h.t2, h.t2 + Expand.out)) }
        return (e > 0.0005 || amount > 0 ? h.tile : -1, amount, t >= h.t2 + Expand.out ? 0 : e, t - h.t1)
    }

    /// The autoPlay finger on the bar chart: slides to September, then August (120 ms per bar).
    static func scriptHl(_ since: Double) -> Double {
        5 - easeOut(segment(since, 0.85, 0.97)) - easeOut(segment(since, 1.1, 1.22))
    }

    static func holdView(_ now: Double, _ h: Hold, hl: Double, reduced: Bool) -> HoldView {
        let v = holdAt(now, h)
        var e = v.e
        if reduced, h.t1 >= 0, now >= h.t1 {
            e = now < h.t2 ? clamp01((now - h.t1) / 0.2) : 1 - clamp01((now - h.t2) / 0.2)
            return HoldView(tile: e > 0 ? h.tile : -1, press: 0, e: e, since: 10, hl: hl)
        }
        return HoldView(tile: v.tile, press: reduced ? 0 : v.press, e: e, since: v.since, hl: hl)
    }
}
