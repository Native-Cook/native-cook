import Foundation

/// The letters' physics, ported step for step from the React Native `SpringLettersSim.ts`. It
/// knows nothing of the font: each letter is just a box. The view calls `step` once a frame and
/// draws what it finds.
///
/// Every letter is a rigid body (position, angle and their speeds) under gravity, standing on a
/// floor. A soft spring pulls it back over its slot and another stands it upright; springs between
/// the facing edges of neighbours chain the word together, so a letter that is dragged tugs the
/// ones beside it and they swing on the links. Contact with the floor is a corner hitting a plane:
/// an impulse with bounce and friction, which also tips the letter.
enum SpringLettersPhysics {
    static let maxLetters = 8
    static let words = ["BOUNCE", "SPRING", "WOBBLE", "JIGGLE"]

    // Gravity (pt/s²).
    static let gravity = 2600.0
    // The spring over a letter's slot: across (stiffness, damping) and, gently, up and down.
    static let homeX = 60.0
    static let homeXDamping = 4.6
    static let homeY = 10.0
    static let air = 0.5
    // Standing upright: angular stiffness and damping (1/s², 1/s).
    static let upright = 150.0
    static let uprightDamping = 8.0
    // Links between neighbours, edge to edge.
    static let link = 75.0
    static let linkDamping = 5.0
    // The finger's hold on a letter: stiff enough to follow, soft enough to lag and swing.
    static let grab = 330.0
    // A touch this far (pt) outside every letter still picks up the nearest one.
    static let grabReach = 26.0
    static let grabDamping = 20.0
    // On release, the letter takes this share of the finger's speed; speeds are capped.
    static let throwShare = 0.65
    static let maxSpeed = 2800.0
    // Floor: bounce for a hard landing, none for a soft one, and friction.
    static let bounce = 0.42
    static let bounceBelow = 140.0
    static let friction = 0.5
    // Corners are rounded by this share of the letter's smaller half-size where it meets the floor.
    static let rounding = 0.6
    // A flick up from the floor throws every letter, the nearest first, the wave crossing at this speed (pt/s).
    static let flickMin = 450.0
    static let flickWave = 1300.0
    static let flickPower = 0.7
    static let flickMax = 1750.0
    // A word change: old letters shrink and turn away over `leaveTime`, new ones drop in one by one.
    static let leaveTime = 0.45
    static let leaveStagger = 0.035
    static let dropDelay = 0.12
    static let dropStagger = 0.075
    static let dropFrom = 140.0

    static let dtCap = 1.0 / 30
    static let substeps = 6

    // autoPlay: drag the O up and fling it, flick the floor, change word, drag the G and throw it,
    // change back, rest.
    static let loop = 12.0
    // [start, end, letter, release vx, release vy, then (dx, dy) pairs the finger passes through]
    static let drags: [[Double]] = [
        [0.6, 1.75, 1, 1100, -900, 0, 0, 30, -90, 70, -190, 90, -230],
        [6.2, 7.35, 5, -1000, -700, 0, 0, -30, -80, -90, -170, -130, -210],
    ]
    static let flickAt = 3.2
    // [time, word]
    static let swaps: [(at: Double, word: Int)] = [(4.7, 1), (8.5, 0)]
    // After a touch the loop waits this long, then starts over.
    static let userHold = 3.5

    static func easeInOut(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }

    /// Did the loop clock pass `at` going from `before` to `now` (wrapping at `loop`)?
    static func crossed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        now >= before ? before < at && now >= at : before < at || now >= at
    }
}

/// The letters' state: the same fields as the RN `LettersSim`, mutated in place once a frame.
final class SpringLettersSim {
    private typealias P = SpringLettersPhysics

    var time = 0.0
    var clock = 0.0
    var hold = 0.0
    var looping: Bool
    var still: Bool
    var seed: Int32 = 7
    // Screen: where the floor is, the side walls and the ceiling.
    var floor = 0.0
    var left = 0.0
    var right = 0.0
    var ceiling = 0.0
    // The word on the floor.
    var word = 0
    var count = 0
    var x = [Double](repeating: 0, count: P.maxLetters)
    var y = [Double](repeating: 0, count: P.maxLetters)
    var a = [Double](repeating: 0, count: P.maxLetters)
    var vx = [Double](repeating: 0, count: P.maxLetters)
    var vy = [Double](repeating: 0, count: P.maxLetters)
    var va = [Double](repeating: 0, count: P.maxLetters)
    var restX = [Double](repeating: 0, count: P.maxLetters)
    var restY = [Double](repeating: 0, count: P.maxLetters)
    var halfW = [Double](repeating: 0, count: P.maxLetters)
    var halfH = [Double](repeating: 0, count: P.maxLetters)
    // Seconds before each new letter drops in (it is hidden until then); 0 once it has.
    var wait = [Double](repeating: 0, count: P.maxLetters)
    // Seconds since the word was placed; with reduced motion the new word fades in over this.
    var arrive = 0.0
    // A pending kick from a floor flick: its strength and the seconds until it arrives.
    var kick = [Double](repeating: 0, count: P.maxLetters)
    var kickIn = [Double](repeating: -1, count: P.maxLetters)
    // The word leaving: its letters' poses and how far through leaving they are.
    var leftWord = -1
    var leftCount = 0
    var lx = [Double](repeating: 0, count: P.maxLetters)
    var ly = [Double](repeating: 0, count: P.maxLetters)
    var la = [Double](repeating: 0, count: P.maxLetters)
    var leaveAge = -1.0
    // The finger: down, where, how fast, and which letter it holds (−1 none) at what local point.
    var down = false
    var scripted = false
    var fx = 0.0
    var fy = 0.0
    var fvx = 0.0
    var fvy = 0.0
    var held = -1
    var gx = 0.0
    var gy = 0.0
    // Where the scripted finger came down, which its path is measured from.
    var dragX = 0.0
    var dragY = 0.0
    // Things a real finger did, counted; the view answers each with a haptic.
    var userGrabs = 0
    var userFlicks = 0
    var userSwaps = 0
    // The last double tap, drawn as a ripple (seconds since, or −1).
    var tapAge = -1.0
    var tapX = 0.0
    var tapY = 0.0

    init(looping: Bool, still: Bool) {
        self.looping = looping
        self.still = still
    }

    /// A seeded random number in [0, 1) (mulberry32, with JavaScript's 32-bit integer maths), so
    /// every loop plays the same.
    func random() -> Double {
        seed = seed &+ 0x6d2b_79f5
        var t = UInt32(bitPattern: seed)
        t = (t ^ (t >> 15)) &* (t | 1)
        t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
        return Double(t ^ (t >> 14)) / 4_294_967_296
    }

    /// Lays a word out on the floor. `boxes` holds each letter's width and height (pt) for every
    /// word, flat: boxes[word][2i], boxes[word][2i + 1]. With `drop`, the letters fall in from
    /// above; otherwise they stand in their slots at once.
    func placeWord(_ word: Int, _ boxes: [[Double]], _ gap: Double, drop: Bool) {
        guard word >= 0 && word < boxes.count else { return }
        let sizes = boxes[word]
        let n = min(P.maxLetters, sizes.count / 2)
        var total = gap * Double(n - 1)
        for i in 0..<n { total += max(2, sizes[i * 2]) }
        var px = (left + right) / 2 - total / 2
        self.word = word
        count = n
        arrive = drop ? 0 : 1
        for i in 0..<n {
            // Never a zero box (a glyph that failed to load): the physics divides by its size.
            let hw = max(1, sizes[i * 2] / 2)
            let hh = max(1, sizes[i * 2 + 1] / 2)
            halfW[i] = hw
            halfH[i] = hh
            restX[i] = px + hw
            restY[i] = floor - hh
            px += hw * 2 + gap
            kickIn[i] = -1
            if drop && !still {
                wait[i] = P.dropDelay + Double(i) * P.dropStagger
                x[i] = restX[i] + (random() - 0.5) * 30
                y[i] = ceiling - P.dropFrom - hh
                a[i] = (random() - 0.5) * 0.9
                vx[i] = 0
                vy[i] = 0
                va[i] = (random() - 0.5) * 6
            } else {
                wait[i] = 0
                x[i] = restX[i]
                y[i] = restY[i]
                a[i] = 0
                vx[i] = 0
                vy[i] = 0
                va[i] = 0
            }
        }
    }

    /// Swaps to word `word`: the letters on the floor leave, the new ones drop in.
    func swapWord(_ word: Int, _ boxes: [[Double]], _ gap: Double, user: Bool) {
        leftWord = self.word
        leftCount = count
        for i in 0..<count {
            lx[i] = x[i]
            ly[i] = y[i]
            la[i] = a[i]
        }
        leaveAge = 0
        held = -1
        placeWord(word, boxes, gap, drop: true)
        if user {
            userSwaps += 1
            hold = P.userHold
        }
    }

    /// The letter under a point, topmost first, or −1.
    private func letterAt(_ px: Double, _ py: Double) -> Int {
        for i in stride(from: count - 1, through: 0, by: -1) {
            if wait[i] > 0 { continue }
            let dx = px - x[i]
            let dy = py - y[i]
            let c = cos(-a[i])
            let s = sin(-a[i])
            let lx = dx * c - dy * s
            let ly = dx * s + dy * c
            if abs(lx) < halfW[i] + 12 && abs(ly) < halfH[i] + 12 { return i }
        }
        // Nothing under the finger: the nearest letter, if the finger is close to it.
        var best = -1
        var bestDistance = Double.infinity
        for i in 0..<count {
            if wait[i] > 0 { continue }
            let d = hypot(px - x[i], py - y[i])
            if d < max(halfW[i], halfH[i]) + P.grabReach && d < bestDistance {
                best = i
                bestDistance = d
            }
        }
        return best
    }

    /// Takes hold of letter `i` at the finger.
    private func grab(_ i: Int) {
        let dx = fx - x[i]
        let dy = fy - y[i]
        let c = cos(-a[i])
        let s = sin(-a[i])
        held = i
        gx = dx * c - dy * s
        gy = dx * s + dy * c
    }

    /// Lets go of the held letter, throwing it with the finger's speed.
    private func letGo() {
        let i = held
        if i < 0 { return }
        held = -1
        if still { return }
        var tx = vx[i] * (1 - P.throwShare) + fvx * P.throwShare
        var ty = vy[i] * (1 - P.throwShare) + fvy * P.throwShare
        let speed = hypot(tx, ty)
        if speed > P.maxSpeed {
            tx *= P.maxSpeed / speed
            ty *= P.maxSpeed / speed
        }
        vx[i] = tx
        vy[i] = ty
    }

    /// A flick up off the floor at `at` with upward speed `speed` (pt/s): every letter jumps, nearest first.
    private func flick(_ at: Double, _ speed: Double, user: Bool) {
        if still { return }
        let power = min(P.flickMax, 380 + abs(speed) * P.flickPower)
        let width = max(1, right - left)
        for i in 0..<count {
            let far = abs(x[i] - at)
            kick[i] = power * (1 - (0.3 * far) / width)
            kickIn[i] = far / P.flickWave
        }
        if user { userFlicks += 1 }
    }

    func fingerDown(_ px: Double, _ py: Double) {
        down = true
        scripted = false
        fx = px
        fy = py
        fvx = 0
        fvy = 0
        hold = P.userHold
        let i = letterAt(px, py)
        if i >= 0 {
            grab(i)
            userGrabs += 1
        } else {
            held = -1
        }
    }

    func fingerMove(_ px: Double, _ py: Double, _ svx: Double, _ svy: Double) {
        if !down || scripted { return }
        fx = px
        fy = py
        fvx = svx
        fvy = svy
        hold = P.userHold
    }

    /// The finger lifts: a held letter is thrown; a flick up from empty floor makes every letter jump.
    func fingerUp(_ svx: Double, _ svy: Double) {
        if !down || scripted { return }
        down = false
        fvx = svx
        fvy = svy
        hold = P.userHold
        if held >= 0 {
            letGo()
        } else if svy < -P.flickMin {
            flick(fx, svy, user: true)
        }
    }

    /// A double tap at a point: the next word.
    func doubleTap(_ px: Double, _ py: Double, _ boxes: [[Double]], _ gap: Double) {
        if boxes.isEmpty { return }
        tapAge = 0
        tapX = px
        tapY = py
        swapWord((word + 1) % boxes.count, boxes, gap, user: true)
    }

    /// The scripted hand: drags, a flick and two word changes, at their times in the loop.
    private func playScript(_ before: Double, _ now: Double, _ boxes: [[Double]], _ gap: Double) {
        for d in P.drags {
            let start = d[0]
            let end = d[1]
            let letter = Int(d[2])
            if P.crossed(before, now, start) && letter < count {
                // Touch down on the letter's middle, a little above centre.
                down = true
                scripted = true
                fx = x[letter]
                fy = y[letter] - halfH[letter] * 0.25
                fvx = 0
                fvy = 0
                grab(letter)
                gx = 0
                gy = -halfH[letter] * 0.25
                dragX = fx
                dragY = fy
            }
            if scripted && down && held == letter && now > start && now < end {
                // Through the keyframes, eased, from where the finger first came down.
                let keys = (d.count - 5) / 2
                let t = P.easeInOut((now - start) / (end - start)) * Double(keys - 1)
                let k = min(keys - 2, Int(Foundation.floor(t)))
                let f = t - Double(k)
                fx = dragX + d[5 + k * 2] + (d[7 + k * 2] - d[5 + k * 2]) * f
                fy = dragY + d[6 + k * 2] + (d[8 + k * 2] - d[6 + k * 2]) * f
            }
            if P.crossed(before, now, end) && scripted && down {
                fvx = d[3]
                fvy = d[4]
                down = false
                letGo()
            }
        }
        if P.crossed(before, now, P.flickAt) { flick((left + right) / 2, -1500, user: false) }
        for swap in P.swaps where P.crossed(before, now, swap.at) {
            tapAge = 0
            tapX = (left + right) / 2
            tapY = floor - 70
            swapWord(swap.word, boxes, gap, user: false)
        }
    }

    /// Applies force (fx, fy) at world point (px, py) of letter `i` for `h` seconds.
    private func push(_ i: Int, _ fx: Double, _ fy: Double, _ px: Double, _ py: Double, _ h: Double) {
        let inertia = (4 * (halfW[i] * halfW[i] + halfH[i] * halfH[i])) / 12
        let rx = px - x[i]
        let ry = py - y[i]
        vx[i] += fx * h
        vy[i] += fy * h
        va[i] += ((rx * fy - ry * fx) / inertia) * h
    }

    /// Point on letter `i` at local (lx, ly), in world space.
    private func worldX(_ i: Int, _ lx: Double, _ ly: Double) -> Double {
        x[i] + lx * cos(a[i]) - ly * sin(a[i])
    }

    private func worldY(_ i: Int, _ lx: Double, _ ly: Double) -> Double {
        y[i] + lx * sin(a[i]) + ly * cos(a[i])
    }

    /// JavaScript's Math.sign.
    private func sign(_ v: Double) -> Double {
        v > 0 ? 1 : v < 0 ? -1 : 0
    }

    /// Corner contact with the floor, walls and ceiling: push out, bounce, rub.
    private func collide(_ i: Int) {
        let inertia = (4 * (halfW[i] * halfW[i] + halfH[i] * halfH[i])) / 12
        // The letter's lowest point, treating it as a box with well-rounded corners: as it tips it
        // rolls on a curve instead of see-sawing between corners, so it rights itself.
        let round = min(halfW[i], halfH[i]) * P.rounding
        let c = cos(a[i])
        let sn = sin(a[i])
        // "Down" in the letter's own frame.
        let dx = sn
        let dy = c
        let lx = (halfW[i] - round) * sign(dx) + round * dx
        let ly = (halfH[i] - round) * sign(dy) + round * dy
        let cx = lx * c - ly * sn
        let cy = lx * sn + ly * c
        let deepest = y[i] + cy - floor
        if deepest > 0 {
            y[i] -= deepest
            // Speed of the contact point, into the floor (+y).
            let vcx = vx[i] - va[i] * cy
            let vcy = vy[i] + va[i] * cx
            if vcy > 0 {
                let e = vcy > P.bounceBelow ? P.bounce : 0
                let j = ((1 + e) * vcy) / (1 + (cx * cx) / inertia)
                vy[i] -= j
                va[i] -= (cx * j) / inertia
                // Friction along the floor, no more than the push allows.
                let jt0 = -vcx / (1 + (cy * cy) / inertia)
                let jt = max(-P.friction * j, min(P.friction * j, jt0))
                vx[i] += jt
                va[i] -= (cy * jt) / inertia
            }
        }
        // Walls and ceiling, against the letter's reach.
        let reach = hypot(halfW[i], halfH[i]) * 0.8
        if x[i] - reach < left {
            x[i] = left + reach
            if vx[i] < 0 { vx[i] *= -0.5 }
        }
        if x[i] + reach > right {
            x[i] = right - reach
            if vx[i] > 0 { vx[i] *= -0.5 }
        }
        if y[i] - reach < ceiling && vy[i] < 0 && held != i {
            y[i] = ceiling + reach
            if vy[i] < 0 { vy[i] *= -0.5 }
        }
    }

    /// One substep of forces for every letter that is on the floor (not still waiting to drop).
    private func forces(_ h: Double) {
        for i in 0..<count {
            if wait[i] > 0 { continue }
            // Home, upright, gravity, air.
            vx[i] += (P.homeX * (restX[i] - x[i]) - P.homeXDamping * vx[i]) * h
            vy[i] += (P.gravity + P.homeY * (restY[i] - y[i]) - P.air * vy[i]) * h
            va[i] += (-P.upright * a[i] - P.uprightDamping * va[i]) * h
        }
        // Links: the right edge of each letter to the left edge of the next.
        if count >= 2 {
            for i in 0..<(count - 1) {
                let j = i + 1
                if wait[i] > 0 || wait[j] > 0 { continue }
                let ax = worldX(i, halfW[i], 0)
                let ay = worldY(i, halfW[i], 0)
                let bx = worldX(j, -halfW[j], 0)
                let by = worldY(j, -halfW[j], 0)
                let rest = restX[j] - halfW[j] - (restX[i] + halfW[i])
                let dx = bx - ax
                let dy = by - ay
                let hl = hypot(dx, dy)
                let length = hl == 0 ? 1e-3 : hl
                let ux = dx / length
                let uy = dy / length
                // Speeds of the two link ends, and how fast the link is stretching.
                let vax = vx[i] - va[i] * (ay - y[i])
                let vay = vy[i] + va[i] * (ax - x[i])
                let vbx = vx[j] - va[j] * (by - y[j])
                let vby = vy[j] + va[j] * (bx - x[j])
                let stretch = (vbx - vax) * ux + (vby - vay) * uy
                let f = P.link * (length - rest) + P.linkDamping * stretch
                push(i, f * ux, f * uy, ax, ay, h)
                push(j, -f * ux, -f * uy, bx, by, h)
            }
        }
        // The finger, on the point where it took hold.
        let g = held
        if g >= 0 && wait[g] <= 0 {
            let px = worldX(g, gx, gy)
            let py = worldY(g, gx, gy)
            let vpx = vx[g] - va[g] * (py - y[g])
            let vpy = vy[g] + va[g] * (px - x[g])
            let pullX = P.grab * (fx - px) - P.grabDamping * vpx
            let pullY = P.grab * (fy - py) - P.grabDamping * vpy
            push(g, pullX, pullY, px, py, h)
        }
    }

    /// One frame: the script, the drops and kicks, the physics, the leaving word.
    func step(_ frameDt: Double, _ boxes: [[Double]], _ gap: Double) {
        guard frameDt.isFinite else { return }
        let dt = min(max(frameDt, 0), P.dtCap)
        if dt <= 0 { return }
        time += dt
        arrive += dt

        if hold > 0 {
            if !down { hold -= dt }
            if hold <= 0 && looping {
                // The loop starts over from its first word, at rest.
                clock = 0
                if word != 0 { swapWord(0, boxes, gap, user: false) }
            }
        } else if looping {
            let before = clock
            clock = (clock + dt).truncatingRemainder(dividingBy: P.loop)
            playScript(before, clock, boxes, gap)
        }

        for i in 0..<count {
            if wait[i] > 0 {
                wait[i] = max(0, wait[i] - dt)
            }
            if kickIn[i] >= 0 {
                kickIn[i] -= dt
                if kickIn[i] < 0 && wait[i] <= 0 {
                    vy[i] = -kick[i]
                    vx[i] += (random() - 0.5) * 160
                    va[i] += (random() - 0.5) * 9
                }
            }
        }

        let h = dt / Double(P.substeps)
        for _ in 0..<P.substeps {
            forces(h)
            for i in 0..<count {
                if wait[i] > 0 { continue }
                x[i] += vx[i] * h
                y[i] += vy[i] * h
                a[i] += va[i] * h
                collide(i)
            }
        }

        if leaveAge >= 0 {
            leaveAge += dt
            if leaveAge > P.leaveTime + P.leaveStagger * Double(P.maxLetters) { leaveAge = -1 }
        }
        if tapAge >= 0 {
            tapAge += dt
            if tapAge > 0.6 { tapAge = -1 }
        }
    }

    /// How far letter `i` of the leaving word is through leaving, 0 … 1.
    func leaving(_ i: Int) -> Double {
        if leaveAge < 0 { return 1 }
        return min(1, max(0, (leaveAge - Double(i) * P.leaveStagger) / P.leaveTime))
    }
}
