import Foundation

extension MoodOrbJarSim {
    /// Advances the same fixed-step clock as the React Native worklet.
    mutating func step(_ g: MoodOrbJarGeometry, frameDelta: Double, autoPlay: Bool, uncapped: Bool = false) {
        let wallDelta = max(0, frameDelta)
        accumulator = uncapped
            ? accumulator + wallDelta
            : min(accumulator + wallDelta, Motion.step * Double(Motion.maxSteps))
        while accumulator >= Motion.step {
            fixedStep(g, autoPlay: autoPlay)
            accumulator -= Motion.step
        }
    }

    // MARK: Jar

    /// Keeps a point inside the jar: floor, round bottom corners, straight walls, shoulders narrowing to the mouth.
    private static func bound(_ g: MoodOrbJarGeometry, _ px: inout [Double], _ py: inout [Double], _ i: Int, _ r: Double) {
        if py[i] < g.mouthY + r * 0.5 { return }
        if py[i] > g.floor - r { py[i] = g.floor - r }
        var half = (g.right - g.left) / 2
        if py[i] < g.shoulderEnd {
            let u = clamp((py[i] - g.mouthY) / (g.shoulderEnd - g.mouthY))
            half = g.mouthHalf + (half - g.mouthHalf) * (u * u * (3 - 2 * u))
        }
        let low = g.centerX - half + r
        let high = g.centerX + half - r
        if px[i] < low { px[i] = low }
        if px[i] > high { px[i] = high }
        let cornerY = g.floor - g.corner
        guard py[i] > cornerY else { return }
        let cornerX = px[i] < g.centerX ? g.left + g.corner : g.right - g.corner
        let outside = (px[i] < g.centerX && px[i] < cornerX) || (px[i] >= g.centerX && px[i] > cornerX)
        guard outside else { return }
        let dx = px[i] - cornerX
        let dy = py[i] - cornerY
        let length = sqrt(dx * dx + dy * dy)
        let d = length == 0 ? 1e-6 : length
        let limit = g.corner - r
        if d > limit {
            px[i] = cornerX + (dx / d) * limit
            py[i] = cornerY + (dy / d) * limit
        }
    }

    /// One step of the orbs in the jar: springs (pile) or gravity (landing), then collisions on predicted positions.
    mutating func stepJar(_ g: MoodOrbJarGeometry, settleAll: Bool) {
        let dt = Motion.step
        // Glass beads pack at almost their full drawn radius; leave only an antialiasing hairline.
        let r = MoodOrbJarTokens.Layout.orb * g.scale * 0.98
        let damp = exp(-Motion.Pile.damping * dt)
        let count = Self.poolCount
        var px: [Double] = []
        var py: [Double] = []
        px.reserveCapacity(count)
        py.reserveCapacity(count)
        for i in 0..<count {
            let inJar = orbs[i].state.isInJar
            if inJar {
                if orbs[i].state == .settling || settleAll {
                    orbs[i].vy += Motion.Throw.gravity * g.scale * dt
                } else {
                    // Phone tilt shifts every slot a little: the pile sloshes.
                    let slosh = tilt * Motion.Tilt.slosh * g.scale
                    orbs[i].vx += -Motion.Pile.home * (orbs[i].x - orbs[i].homeX - slosh) * dt
                    orbs[i].vy += -Motion.Pile.home * (orbs[i].y - orbs[i].homeY) * dt
                }
                orbs[i].vx *= damp
                orbs[i].vy *= damp
            }
            px.append(orbs[i].x + (inJar ? orbs[i].vx * dt : 0))
            py.append(orbs[i].y + (inJar ? orbs[i].vy * dt : 0))
        }
        for _ in 0..<Motion.Pile.passes {
            for i in 0..<count where orbs[i].state.isInJar {
                for j in (i + 1)..<count where orbs[j].state.isInJar {
                    let dx = px[j] - px[i]
                    let dy = py[j] - py[i]
                    let length = sqrt(dx * dx + dy * dy)
                    let d = length == 0 ? 1e-6 : length
                    if d >= r * 2 { continue }
                    let c = (r * 2 - d) / d / 2
                    px[i] -= dx * c
                    py[i] -= dy * c
                    px[j] += dx * c
                    py[j] += dy * c
                }
            }
            for i in 0..<count where orbs[i].state.isInJar {
                Self.bound(g, &px, &py, i, r)
            }
        }
        for i in 0..<count where orbs[i].state.isInJar {
            orbs[i].vx = (px[i] - orbs[i].x) / dt
            orbs[i].vy = (py[i] - orbs[i].y) / dt
            orbs[i].x = px[i]
            orbs[i].y = py[i]
        }
    }

    // MARK: Autoplay

    private mutating func runDemo(_ g: MoodOrbJarGeometry, from t0: Double, to t1: Double) {
        let pick = Motion.Script.pick
        let slotX = g.slotX[Motion.Script.mood]
        if t0 <= pick[0] && pick[0] < t1 {
            apply(.down(x: slotX, y: g.slotY, script: true), geometry: g)
        }
        if t1 > pick[0] && t0 < pick[1] && held >= 0 {
            let u = Self.clamp((min(t1, pick[1]) - pick[0]) / (pick[1] - pick[0]))
            let e = u * u
            apply(
                .move(
                    x: slotX + Motion.Script.pickBy[0] * g.scale * e,
                    y: g.slotY + Motion.Script.pickBy[1] * g.scale * e
                ),
                geometry: g
            )
        }
        if t0 < pick[1] && pick[1] <= t1 && held >= 0 {
            apply(.up(vx: Motion.Script.release[0] * g.scale, vy: Motion.Script.release[1] * g.scale), geometry: g)
        }
        let swipe = Motion.Script.swipe
        let rowCenterY = g.rowY + g.rowHeight / 2
        if t0 <= swipe[0] && swipe[0] < t1 {
            apply(.down(x: g.width * 0.72, y: rowCenterY, script: true), geometry: g)
        }
        if t1 > swipe[0] && t0 < swipe[1] && swiping {
            let u = Self.easeInOut(Self.clamp((min(t1, swipe[1]) - swipe[0]) / (swipe[1] - swipe[0])))
            apply(.move(x: g.width * 0.72 + Motion.Script.swipeBy * g.scale * u, y: rowCenterY), geometry: g)
        }
        if t0 < swipe[1] && swipe[1] <= t1 && swiping {
            apply(.up(vx: 0, vy: 0), geometry: g)
        }
    }

    // MARK: One fixed step

    private mutating func fixedStep(_ g: MoodOrbJarGeometry, autoPlay: Bool) {
        let dt = Motion.step

        if demoOn {
            let t0 = demoTime
            var t1 = t0 + dt
            runDemo(g, from: t0, to: t1)
            if t1 * 1000 >= Motion.loopMS {
                t1 -= Motion.loopMS / 1000
                for i in 0..<Self.pileCount where orbs[i].state == .pile {
                    orbs[i].x = orbs[i].homeX
                    orbs[i].y = orbs[i].homeY
                    orbs[i].vx = 0
                    orbs[i].vy = 0
                }
            }
            demoTime = t1
        } else if autoPlay && held < 0 && !swiping {
            quiet += dt
            var busy = false
            for i in Self.pileCount..<Self.poolCount where orbs[i].state != .off { busy = true }
            if quiet * 1000 >= Motion.resumeMS && !busy {
                demoOn = true
                demoTime = 0
            }
        }

        // Flight, return and departure are kinematic; landing and the pile are physics.
        let r = MoodOrbJarTokens.Layout.orb * g.scale
        var anyAir = false
        for i in 0..<Self.poolCount {
            switch orbs[i].state {
            case .flying:
                anyAir = true
                flyStep(i, g, r: r, dt: dt)
            case .returning, .leaving:
                anyAir = anyAir || orbs[i].state == .leaving
                travelStep(i, g, dt: dt)
            default:
                break
            }
        }

        for i in 0..<Self.poolCount {
            orbs[i].previousVY = orbs[i].vy
        }
        stepJar(g, settleAll: false)
        for i in 0..<Self.poolCount where orbs[i].state.isInJar {
            softBodyStep(i, g, dt: dt)
        }
        hopStep(g)
        advanceEffects(dt: dt)
        anyAir = restStep(g, dt: dt, anyAir: anyAir)

        for i in 0..<Self.poolCount {
            let hidden = orbs[i].state == .off || orbs[i].state == .leaving
            orbs[i].fade = min(1, orbs[i].fade + (hidden ? 0 : dt / Motion.Reduced.fade))
        }
        for m in 0..<Tokens.moods.count {
            var out = false
            for i in Self.pileCount..<Self.poolCount where orbs[i].mood == m && (orbs[i].state == .held || orbs[i].state == .returning) {
                out = true
            }
            if !out && slotScale[m] < 1 {
                slotScale[m] = min(1, slotScale[m] + dt / Motion.Throw.regrow)
            }
        }

        // Lid: lifted while an orb is in the air near the mouth, and a moment after it lands, as in the source.
        lidHold = anyAir ? Motion.Lid.hold : max(0, lidHold - dt)
        let want = lidHold > 0 && !reduced ? Motion.Lid.open : 0
        lidVelocity += (-Motion.Lid.stiffness * (lid - want) - Motion.Lid.damping * lidVelocity) * dt
        lid += lidVelocity * dt
        // Row: springs back unless a finger holds it.
        if !swiping {
            rowVelocity += (-Motion.Undo.rowStiffness * rowX - Motion.Undo.rowDamping * rowVelocity) * dt
            rowX += rowVelocity * dt
        }
        clock += dt
    }

    private mutating func flyStep(_ i: Int, _ g: MoodOrbJarGeometry, r: Double, dt: Double) {
        orbs[i].vy += Motion.Throw.gravity * g.scale * dt
        orbs[i].x += orbs[i].vx * dt
        orbs[i].y += orbs[i].vy * dt
        if orbs[i].vy > 0 && orbs[i].y > g.mouthY + r && abs(orbs[i].x - g.centerX) < g.mouthHalf {
            orbs[i].state = .settling
            orbs[i].restTime = 0
            orbs[i].time = -1 // not landed yet: the first impact gives one haptic
        }
        if orbs[i].y > g.height + r {
            orbs[i].state = .off
            slotScale[orbs[i].mood] = 0
        }
    }

    /// The return to the picker after a weak release, and the undo's rise out of the jar and arc back.
    private mutating func travelStep(_ i: Int, _ g: MoodOrbJarGeometry, dt: Double) {
        orbs[i].time += dt
        let targetX = g.slotX[orbs[i].mood]
        if orbs[i].state == .returning {
            let u = Self.easeInOut(Self.clamp(orbs[i].time / 0.3))
            orbs[i].x = orbs[i].fromX + (targetX - orbs[i].fromX) * u
            orbs[i].y = orbs[i].fromY + (g.slotY - orbs[i].fromY) * u
            if u >= 1 {
                orbs[i].state = .off
                slotScale[orbs[i].mood] = 1
            }
        } else if reduced {
            orbs[i].fade = 1 - Self.clamp(orbs[i].time / Motion.Reduced.fade)
            if orbs[i].time >= Motion.Reduced.fade {
                orbs[i].state = .off
            }
        } else if orbs[i].time < Motion.Undo.rise {
            // Straight up out of the jar, then an arc back to its slot in the picker.
            let u = Self.easeInOut(orbs[i].time / Motion.Undo.rise)
            orbs[i].x = orbs[i].fromX + (g.centerX - orbs[i].fromX) * u
            orbs[i].y = orbs[i].fromY + (g.mouthY - 50 * g.scale - orbs[i].fromY) * u
        } else {
            let u = Self.easeInOut(Self.clamp((orbs[i].time - Motion.Undo.rise) / Motion.Undo.fly))
            let anchorX = g.centerX
            let anchorY = g.mouthY - 50 * g.scale
            orbs[i].x = anchorX + (targetX - anchorX) * u
            orbs[i].y = anchorY + (g.slotY - anchorY) * u - sin(Double.pi * u) * 60 * g.scale
            if u >= 1 {
                orbs[i].state = .off
            }
        }
    }

    /// Soft bodies: a sudden stop squashes the orb, the spring overshoots into a stretch.
    private mutating func softBodyStep(_ i: Int, _ g: MoodOrbJarGeometry, dt: Double) {
        let dv = orbs[i].previousVY - orbs[i].vy
        if dv > Motion.Soft.trigger * g.scale && !reduced {
            orbs[i].squashVelocity += min(Motion.Soft.max, dv * Motion.Soft.perSpeed / g.scale) * 30
            if orbs[i].state == .settling && orbs[i].time < 0 {
                orbs[i].time = 0
                orbs[i].squash = max(orbs[i].squash, Motion.Soft.landing)
                emit(.haptic(dv > Motion.Soft.hard * g.scale ? .landHard : dv > Motion.Soft.soft * g.scale ? .land : .landSoft))
                rippleTime = 0
                rippleX = orbs[i].x
                rippleY = orbs[i].y
                rippleMood = orbs[i].mood
            }
        }
        orbs[i].squashVelocity += (-Motion.Soft.stiffness * orbs[i].squash - Motion.Soft.damping * orbs[i].squashVelocity) * dt
        orbs[i].squash = max(-Motion.Soft.max, min(Motion.Soft.max, orbs[i].squash + orbs[i].squashVelocity * dt))
    }

    /// Ambient: deterministic hops at 1.5, 3.5 and 5.5 s; the pile is still at the seam.
    private mutating func hopStep(_ g: MoodOrbJarGeometry) {
        let loopTime = demoOn ? demoTime : clock
        let hop = Int(floor((loopTime + Motion.Hop.phase) / Motion.Hop.every))
        guard hop != hopIndex else { return }
        hopIndex = hop
        let k = (hop * 7) % Self.pileCount
        if hop != 0, loopTime < Motion.Hop.last, !reduced, orbs[k].state == .pile {
            orbs[k].vy -= Motion.Hop.speed * g.scale
        }
    }

    private mutating func advanceEffects(dt: Double) {
        if ghostTime >= 0 {
            ghostTime += dt
            if ghostTime > Motion.Ghost.duration {
                ghostTime = -1
                growTime = 0 // container morph: the card grows out of the avatar
            }
        }
        if growTime >= 0 {
            growTime += dt
        }
        if rippleTime >= 0 {
            rippleTime += dt
            if rippleTime > Motion.Ripple.duration {
                rippleTime = -1
            }
        }
    }

    /// A landed orb that stays slow for a moment joins the pile. Returns whether any orb is still in the air.
    private mutating func restStep(_ g: MoodOrbJarGeometry, dt: Double, anyAir: Bool) -> Bool {
        var air = anyAir
        for i in 0..<Self.poolCount where orbs[i].state == .settling {
            let speed = hypot(orbs[i].vx, orbs[i].vy)
            orbs[i].restTime = speed < Motion.Pile.restSpeed * g.scale ? orbs[i].restTime + dt : 0
            if orbs[i].restTime >= Motion.Pile.restTime {
                orbs[i].state = .pile
                orbs[i].homeX = orbs[i].x
                orbs[i].homeY = orbs[i].y
                added(i)
            }
            if orbs[i].y < g.shoulderEnd {
                air = true
            }
        }
        return air
    }
}
