import Foundation
import simd

// A rig for flat cut-out art: no extra drawings, only regions of the one PNG that bend around their
// own pivot (the creature shader inverts the warp per pixel). Coordinates are pixels of the 512x512
// source art. Mirrors HabitatRig.ts.

private typealias M = HabitatSpecimenCarouselMotion

struct HabitatSpecimenCarouselJoint {
    var pivot: (Double, Double)
    var center: (Double, Double)
    var radius: Double
    /// Degrees, cycles per loop, phase.
    var sway: [(Double, Double, Double)]
    /// A twitch: degrees and the loop seconds where it fires.
    var twitch: (degrees: Double, at: [Double])? = nil
}

/// A pose key for `flow`: seconds, value.
typealias HabitatSpecimenCarouselKey = (Double, Double)

struct HabitatSpecimenCarouselAccent {
    var joint: Int
    var degrees: Double
    /// Rise from, rise to, fall from, fall to.
    var window: (Double, Double, Double, Double)
    var hz: Double
    var phase: Double
}

struct HabitatSpecimenCarouselFlame {
    var base: (Double, Double)
    var height: Double
    var at: (Double, Double)
    var radius: (Double, Double)
}

struct HabitatSpecimenCarouselRigData {
    /// Where the feet meet the ground; squash, lean and hops pivot here.
    var feet: Double
    var chest: (Double, Double, Double)
    /// Where the legs meet the body.
    var hips: Double
    /// Joint 0 is always the head.
    var joints: [HabitatSpecimenCarouselJoint]
    /// Eye ellipses (cx, cy, rx, ry); a second eye with rx 0 is absent.
    var eyes: [(Double, Double, Double, Double)]
    /// Loop seconds of every blink; two close together make a double blink.
    var blinks: [Double]
    var glow: (at: (Double, Double), radius: Double, color: (Double, Double, Double))
    /// The face, for a 2.5D head turn: its centre and radius, and how far its middle slides.
    var face: (at: (Double, Double), radius: Double, shift: Double)
    var flame: HabitatSpecimenCarouselFlame?
    var acting: (yaw: [HabitatSpecimenCarouselKey], tilt: [HabitatSpecimenCarouselKey], blinks: [Double], accents: [HabitatSpecimenCarouselAccent])
    /// Phase offset, so three creatures never breathe in step.
    var phase: Double
}

enum HabitatSpecimenCarouselRig {
    static let rigs: [HabitatSpecimenCarouselRigData] = [
        // Charizard: wings beat out of step, the tail flame flickers on two periods.
        HabitatSpecimenCarouselRigData(
            feet: 495, chest: (256, 392, 96), hips: 440,
            joints: [
                .init(pivot: (268, 300), center: (245, 150), radius: 88, sway: [(1.6, 3, 0.1)]),
                .init(pivot: (226, 276), center: (95, 245), radius: 122, sway: [(2.6, 4, 0), (0.9, 7, 0.3)]),
                .init(pivot: (292, 280), center: (420, 250), radius: 122, sway: [(-2.4, 4, 0.37), (-0.8, 7, 0.71)]),
                .init(pivot: (396, 446), center: (452, 372), radius: 62, sway: [(3.4, 18, 0.2), (2.0, 12, 0.55)]),
            ],
            eyes: [(239, 136, 9, 5.5), (0, 0, 0, 0)],
            blinks: [1.2, 4.6, 8.1, 8.42, 11.6],
            glow: (at: (452, 380), radius: 150, color: (1, 0.55, 0.2)),
            face: (at: (250, 140), radius: 52, shift: 5),
            flame: .init(base: (452, 402), height: 110, at: (451, 345), radius: (38, 68)),
            // Turns to its tail as the flame swells, watches the embers rise up and away, settles.
            acting: (
                yaw: [(0, 0), (0.18, 0), (0.55, 0.85), (1.15, 0.85), (1.7, 0.2), (2.3, -0.25), (3.2, -0.1), (3.9, 0)],
                tilt: [(0, 0), (0.5, -2), (1.2, -3), (2, -4), (2.8, -1), (3.9, 0)],
                blinks: [0.2, 1.72],
                // The wings beat as the flame swells, the far one a beat behind.
                accents: [
                    .init(joint: 1, degrees: 4, window: (0.2, 0.45, 1.1, 1.6), hz: 2.2, phase: 0),
                    .init(joint: 2, degrees: -3.5, window: (0.2, 0.45, 1.1, 1.6), hz: 2.2, phase: 0.12),
                ]
            ),
            phase: 0
        ),
        // Blastoise: a heavy body, so its head and arm move least and slowest.
        HabitatSpecimenCarouselRigData(
            feet: 500, chest: (300, 330, 112), hips: 405,
            joints: [
                .init(pivot: (300, 166), center: (320, 104), radius: 78, sway: [(1.8, 3, 0.4)]),
                .init(pivot: (372, 252), center: (432, 292), radius: 62, sway: [(3.0, 3, 0.6), (0.8, 6, 0.1)]),
                .init(pivot: (112, 262), center: (88, 266), radius: 26, sway: [(-3.0, 3, 0.85)]),
                .init(pivot: (0, 0), center: (0, 0), radius: 0, sway: []),
            ],
            eyes: [(309, 94, 11, 7), (0, 0, 0, 0)],
            blinks: [2.1, 5.6, 5.95, 9.4, 12.6],
            glow: (at: (409, 196), radius: 140, color: (0.5, 0.85, 1)),
            face: (at: (318, 100), radius: 58, shift: 6),
            flame: nil,
            // Braces towards the higher jet while both fire, then looks down at the spray landing.
            acting: (
                yaw: [(0, 0), (0.15, 0), (0.4, -0.45), (1.05, -0.45), (1.5, 0.15), (2.1, 0.4), (3, 0.3), (3.9, 0)],
                tilt: [(0, 0), (0.3, 1.5), (1, 1.5), (1.6, 3), (2.6, 3), (3.9, 0)],
                blinks: [0.4, 1.5],
                // Both arms brace against the recoil while the cannons fire.
                accents: [
                    .init(joint: 1, degrees: 3, window: (0.2, 0.32, 1, 1.4), hz: 0, phase: 0),
                    .init(joint: 2, degrees: -3, window: (0.24, 0.36, 1, 1.45), hz: 0, phase: 0),
                ]
            ),
            phase: 0.31
        ),
        // Bulbasaur: the bulb sways and its tip follows a beat later; one ear twitches now and then.
        HabitatSpecimenCarouselRigData(
            feet: 492, chest: (292, 420, 122), hips: 432,
            joints: [
                .init(pivot: (258, 432), center: (190, 300), radius: 152, sway: [(1.3, 3, 0.15)]),
                .init(pivot: (330, 236), center: (345, 168), radius: 96, sway: [(2.6, 3, 0.3)]),
                .init(pivot: (350, 162), center: (366, 146), radius: 42, sway: [(3.2, 3, 0.45)]),
                .init(pivot: (116, 240), center: (106, 214), radius: 27, sway: [(1.5, 6, 0.2)], twitch: (-9, [2.6, 9.9])),
            ],
            eyes: [(86, 300, 20, 32), (251, 308, 40, 29)],
            blinks: [0.4, 3.9, 7.3, 10.6, 10.94, 12.9],
            glow: (at: (377, 152), radius: 190, color: (0.8, 1, 0.45)),
            face: (at: (172, 300), radius: 132, shift: 9),
            flame: nil,
            // Looks up the beam as it fires, then down at the leaves as they fall and settle.
            acting: (
                yaw: [(0, 0), (0.3, 0), (0.6, 0.5), (1.3, 0.5), (1.8, -0.3), (2.7, -0.45), (3.4, -0.2), (3.9, 0)],
                tilt: [(0, 0), (0.5, 1.5), (1.3, 2), (2, -1.5), (2.8, -2), (3.9, 0)],
                blinks: [0.62, 1.85],
                // The bulb leans back as the beam pushes off it, and its crown flutters after.
                accents: [
                    .init(joint: 1, degrees: -2, window: (0.5, 0.62, 1.2, 1.6), hz: 0, phase: 0),
                    .init(joint: 2, degrees: 3, window: (0.55, 0.68, 1.3, 1.9), hz: 3, phase: 0.2),
                ]
            ),
            phase: 0.62
        ),
    ]

    private static let tau = Double.pi * 2
    private static let rad = Double.pi / 180
    /// Seconds into a hold of the contented double blink.
    private static let petBlinks: [Double] = [0.22, 0.56]

    /// Acting curve from rossark-motion-skill's engine: passes through every key with a continuous
    /// speed and never overshoots one (monotone cubic).
    static func flow(_ t: Double, _ k: [HabitatSpecimenCarouselKey]) -> Double {
        let n = k.count
        if t <= k[0].0 { return k[0].1 }
        if t >= k[n - 1].0 { return k[n - 1].1 }
        var i = 0
        while k[i + 1].0 < t { i += 1 }
        func slope(_ j: Int) -> Double { (k[j + 1].1 - k[j].1) / (k[j + 1].0 - k[j].0) }
        func tangent(_ j: Int) -> Double {
            if j == 0 || j == n - 1 { return 0 }
            let a = slope(j - 1), b = slope(j)
            if a * b <= 0 { return 0 }
            let w0 = 2 * (k[j + 1].0 - k[j].0) + (k[j].0 - k[j - 1].0)
            let w1 = (k[j + 1].0 - k[j].0) + 2 * (k[j].0 - k[j - 1].0)
            return (w0 + w1) / (w0 / a + w1 / b)
        }
        let h = k[i + 1].0 - k[i].0
        let s = (t - k[i].0) / h
        let s2 = s * s, s3 = s2 * s
        return (2 * s3 - 3 * s2 + 1) * k[i].1 + (s3 - 2 * s2 + s) * h * tangent(i)
            + (-2 * s3 + 3 * s2) * k[i + 1].1 + (s3 - s2) * h * tangent(i + 1)
    }

    /// 0 to 1 over [a, b], held, then back to 0 over [c, d] (the signature shader's `window`).
    private static func rises(_ x: Double, _ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
        let rise = max(0, min(1, (x - a) / (b - a)))
        let fall = max(0, min(1, (x - c) / (d - c)))
        return rise * rise * (3 - 2 * rise) * (1 - fall * fall * (3 - 2 * fall))
    }

    /// Eyelid closure (0 open, 1 shut): close 110 ms, hold 40 ms, reopen 190 ms with an ease-out.
    static func lidAt(_ seconds: Double, _ blinks: [Double]) -> Double {
        var lid = 0.0
        for blink in blinks {
            var d = seconds - blink
            if d < -M.loop / 2 { d += M.loop }
            if d > M.loop / 2 { d -= M.loop }
            var v = 0.0
            if d >= 0 && d < 0.11 { let u = d / 0.11; v = u * u * (3 - 2 * u) }
            else if d >= 0.11 && d < 0.15 { v = 1 }
            else if d >= 0.15 && d < 0.34 { let u = 1 - (d - 0.15) / 0.19; v = u * u * u }
            lid = max(lid, v)
        }
        return lid
    }

    /// A twitch: a quick turn out (90 ms) and a slower return (260 ms).
    private static func twitchAt(_ seconds: Double, _ at: [Double]) -> Double {
        var v = 0.0
        for start in at {
            let d = seconds - start
            if d >= 0 && d < 0.09 { v = max(v, sin((d / 0.09) * Double.pi / 2)) }
            else if d >= 0.09 && d < 0.35 { let u = 1 - (d - 0.09) / 0.26; v = max(v, u * u * (3 - 2 * u)) }
        }
        return v
    }

    /// Height of the incoming specimen's hop across a crossing, as a fraction of its size.
    static let hopHeight = 0.11
    static func hopLift(_ f: Double) -> Double {
        let u = max(0, min(1, (f - 0.3) / 0.62))
        return 4 * u * (1 - u)
    }

    /// Contact squash after a landing (0 to 1 over landMS): a fast squash, one overshoot, then rest.
    static func landEnvelope(_ progress: Double) -> Double {
        if progress <= 0 || progress >= 1 { return 0 }
        let s = progress * (M.landMS / 1000)
        if s < 0.05 { let u = s / 0.05; return u * u * (3 - 2 * u) }
        return exp(-(s - 0.05) * 7.5) * cos((s - 0.05) * 15)
    }

    struct LifeInput {
        /// Ambient loop time 0..1.
        var t: Double
        var reduced: Bool
        var role: Int
        var f: Double
        var press: Double
        var look: Double
        var pet: Double
        /// Seconds since a hold began.
        var petClock: Double
        /// Landing squash envelope for this creature.
        var land: Double
        /// Signature progress for this creature, 0 outside its move.
        var signature: Double
        /// Seconds of this creature's move's effect clock; -1 when idle.
        var fx: Double
        var index: Int
    }

    struct Pose {
        var sy = 1.0, sx = 1.0, lean = 0.0, tx = 0.0, ty = 0.0, lift = 0.0
        var angles: [Double] = [0, 0, 0, 0]
        var headShift = 0.0, yaw = 0.0, breath = 0.0, shift = 0.0, hang = 0.0
        var lid = 0.0, squint = 0.0, flameGrow = 0.0, flameStretch = 0.0
    }

    /// Every channel of one creature for one frame: key poses come from the exchange, the press and
    /// the signature move; an idle layer keeps even a held pose alive; the head follows the body's
    /// lean 110 ms late and the bulb tip follows its bulb.
    static func lifePose(_ input: LifeInput) -> Pose {
        let rig = rigs[input.index]
        let live = input.reduced ? 0.0 : 1.0
        let seconds = input.t * M.loop
        func wave(_ cycles: Double, _ phase: Double, _ t: Double? = nil) -> Double {
            sin(tau * ((t ?? input.t) * cycles + phase + rig.phase))
        }
        func leanAt(_ t: Double) -> Double { 0.7 * wave(3, 0.2, t) }
        let lean = live * (leanAt(input.t) + input.pet * 1.6 * sin(input.petClock * tau * 1.6)) * rad
        let acting = input.fx >= 0
        var angles: [Double] = []
        for j in 0..<4 {
            let joint = rig.joints[j]
            var degrees = 0.0
            for s in joint.sway { degrees += s.0 * wave(s.1, s.2) }
            if let twitch = joint.twitch { degrees += twitch.degrees * twitchAt(seconds, twitch.at) }
            if acting {
                for accent in rig.acting.accents where accent.joint == j {
                    let (r0, r1, f0, f1) = accent.window
                    degrees += accent.degrees * rises(input.fx, r0, r1, f0, f1)
                        * (accent.hz > 0 ? sin(tau * (input.fx * accent.hz + accent.phase)) : 1)
                }
            }
            if j == 0 {
                // The head rights itself a beat after the body leans, looks towards the finger,
                // tilts while petted, and tilts to watch what its move throws.
                degrees += -0.45 * leanAt(input.t - 0.11 / M.loop) + input.look * 5 + input.pet * 4
                    + (acting ? flow(input.fx, rig.acting.tilt) : 0)
            }
            angles.append(live * degrees * rad)
        }
        let breath = live * 0.012 * (0.5 + 0.5 * wave(4, 0))
        let lift = live * (input.role == 1 ? hopLift(input.f) : 0)
        let signature = M.signaturePose(input.signature, input.index)
        // Height changes keep the volume: width scales by 1 / sy^0.6.
        let wiggle = input.pet * (0.5 + 0.5 * sin(input.petClock * tau * 3.2))
        let air = live * 0.05 * sin(Double.pi * min(1, lift))
        let squash = (1 - 0.035 * input.press) * (1 - 0.15 * input.land) * (1 - 0.03 * live * wiggle) * (1 + air) * (1 + 0.005 * live * wave(4, 0.1))
        var pose = Pose()
        pose.sy = squash * signature.sy
        pose.sx = signature.sx / pow(squash, 0.6)
        pose.lean = lean
        pose.tx = live * signature.tx
        pose.ty = live * signature.ty
        pose.lift = lift
        pose.angles = angles
        pose.headShift = live * input.look * 4
        // The head turns (2.5D): a slow glance that never quite stops, and the move's acting turns.
        pose.yaw = live * (0.2 * wave(2, 0.15) + (acting ? flow(input.fx, rig.acting.yaw) : 0) + input.look * 0.35)
        pose.breath = breath
        // The weight drifts from one foot to the other, and in the air the legs hang a little longer.
        pose.shift = live * 4 * wave(3, 0.35)
        pose.hang = 0.07 * lift
        // A petted creature answers with a slow, contented double blink; it also blinks on each acting turn.
        pose.lid = input.reduced ? 0 : max(
            lidAt(seconds, rig.blinks),
            input.pet > 0 ? lidAt(input.petClock, petBlinks) : 0,
            acting ? lidAt(input.fx, rig.acting.blinks) : 0
        )
        pose.squint = 0
        // The live tail flame: it swells into a plume during the move, and stretches 3-6% on two waves.
        pose.flameGrow = acting ? rises(input.fx / (M.signatureMS / 1000), 0.1, 0.28, 0.6, 0.96) : 0
        pose.flameStretch = live * (0.03 * wave(19, 0.1) + 0.02 * wave(12, 0.4))
        return pose
    }

    /// Where one creature stands this frame: its layout, its place in the exchange and its pose.
    struct BodyFrame {
        var index: Int
        /// Art square and the transparent margin around it, in points.
        var size: Double
        var pad: Double
        /// Art square's top-left inside the hero, the hero's top, and the screen.
        var left: Double
        var top: Double
        var heroTop: Double
        var width: Double
        var screenHeight: Double
        var scale: Double
        var exchange: HabitatSpecimenCarouselMotion.Exchange
        var driftPx: Double
        var pose: Pose
    }

    private struct Placement {
        var k: Double, fx: Double, fy: Double, z: Double, ox: Double, oy: Double, c: Double, s: Double
        var origin: (Double, Double)
    }

    /// The body's pose as one affine about the feet, and where its canvas sits on screen.
    private static func placement(_ frame: BodyFrame) -> Placement {
        let size = frame.size, pad = frame.pad, pose = frame.pose
        let k = size / 512
        let fx = size / 2
        let fy = rigs[frame.index].feet * k
        let view = size + pad * 2
        let z = frame.exchange.scale
        return Placement(
            k: k, fx: fx, fy: fy, z: z,
            ox: pad + fx + pose.tx * frame.scale,
            oy: pad + fy + pose.ty * frame.scale - pose.lift * hopHeight * size,
            c: cos(pose.lean), s: sin(pose.lean),
            origin: (frame.left - pad + (view / 2) * (1 - z) + frame.exchange.drift * frame.driftPx,
                     frame.heroTop + frame.top - pad + (view / 2) * (1 - z))
        )
    }

    private static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = max(0, min(1, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }

    /// Where a point of the source art is on screen this frame: the creature shader's warp run
    /// forwards. Effects that leave the body start here, so they stay attached as it moves.
    static func bodyPoint(_ frame: BodyFrame, _ x: Double, _ y: Double) -> (Double, Double) {
        let rig = rigs[frame.index]
        let p = placement(frame)
        let pose = frame.pose
        let k = p.k
        let ax = x * k, ay = y * k
        var dx = 0.0, dy = 0.0
        for j in 0..<4 {
            let joint = rig.joints[j]
            if joint.radius <= 0 { continue }
            let r = joint.radius * k
            let w = 1 - smooth(r * 0.45, r * 1.1, hypot(ax - joint.center.0 * k, ay - joint.center.1 * k))
            if w <= 0 { continue }
            let t = pose.angles[j] * w
            let px = ax - joint.pivot.0 * k
            let py = ay - joint.pivot.1 * k
            dx += cos(t) * px - sin(t) * py - px
            dy += sin(t) * px + cos(t) * py - py
            if j == 0 { dx += pose.headShift * frame.scale * w }
        }
        let face = rig.face
        dx += pose.yaw * face.shift * k * (1 - smooth(face.radius * k * 0.15, face.radius * k * 0.95, hypot(ax - face.at.0 * k, ay - face.at.1 * k)))
        let chest = 1 - smooth(rig.chest.2 * k * 0.3, rig.chest.2 * k * 1.25, hypot(ax - rig.chest.0 * k, ay - rig.chest.1 * k))
        dx += (ax - rig.chest.0 * k) * 0.5 * pose.breath * chest
        dy += (ay - p.fy) * pose.breath * chest
        let hips = rig.hips * k
        dx += pose.shift * k * (1 - smooth(hips - 10 * k, p.fy, ay))
        dy += max(ay - hips, 0) * pose.hang
        let u = (ax + dx - p.fx) * pose.sx
        let v = (ay + dy - p.fy) * pose.sy
        return (p.origin.0 + (p.ox + p.c * u - p.s * v) * p.z, p.origin.1 + (p.oy + p.s * u + p.c * v) * p.z)
    }

    /// The creature shader's uniforms for one frame.
    struct LifeFrame {
        var body: BodyFrame
        var phase: Double
        /// Element of the incoming habitat.
        var element: Int
        /// Key light direction and how much of the baked volume shows.
        var light: (Double, Double, Double)
        var tint: (Double, Double, Double)
        var glow: Double
        /// Ambient loop seconds (the live flame's flicker loops with it) and Reduced Motion.
        var seconds: Double
        var reduced: Bool
    }

    static func lifeUniforms(_ frame: LifeFrame) -> HabitatSpecimenCarouselCreatureUniforms {
        let body = frame.body
        let index = body.index, size = body.size, pose = body.pose, exchange = body.exchange
        let rig = rigs[index]
        let p = placement(body)
        let k = p.k
        func f4(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> SIMD4<Float> { SIMD4(Float(a), Float(b), Float(c), Float(d)) }
        func joint(_ j: Int) -> (SIMD4<Float>, Double, Double) {
            let data = rig.joints[j]
            return (f4(data.pivot.0 * k, data.pivot.1 * k, data.center.0 * k, data.center.1 * k), data.radius * k, pose.angles[j])
        }
        let j0 = joint(0), j1 = joint(1), j2 = joint(2), j3 = joint(3)
        func eye(_ e: Int) -> SIMD4<Float> {
            let v = rig.eyes[e]
            return f4(v.0 * k, v.1 * k, v.2 * k, v.3 * k)
        }
        var u = HabitatSpecimenCarouselCreatureUniforms()
        u.boxPd = f4(size, p.fy, 0.9, exchange.scale)
        u.invX = f4(p.c / pose.sx, p.s / pose.sx, p.fx - (p.c * p.ox + p.s * p.oy) / pose.sx, exchange.f)
        u.invY = f4(-p.s / pose.sy, p.c / pose.sy, p.fy - (-p.s * p.ox + p.c * p.oy) / pose.sy, frame.phase)
        u.j0 = j0.0; u.j1 = j1.0; u.j2 = j2.0; u.j3 = j3.0
        u.k01 = f4(j0.1, j0.2, j1.1, j1.2)
        u.k23 = f4(j2.1, j2.2, j3.1, j3.2)
        u.chest = f4(rig.chest.0 * k, rig.chest.1 * k, rig.chest.2 * k, pose.breath)
        u.hips = f4(rig.hips * k, pose.shift * k, pose.hang, 10 * k)
        u.eyeA = eye(0)
        u.eyeB = eye(1)
        u.lidOrigin = f4(pose.lid, pose.squint, p.origin.0, p.origin.1)
        u.screenShift = f4(body.width, body.screenHeight, pose.headShift * body.scale, exchange.role == 1 ? 1 : 0)
        // Pushed back into the old world's shade as the new one covers it.
        u.misc = f4(Double(frame.element), 0.6, exchange.role == 0 ? 0.3 * exchange.f : 0, exchange.role < 0 ? 1 : 0)
        u.tint = f4(frame.tint.0, frame.tint.1, frame.tint.2, 0)
        u.light = f4(frame.light.0, frame.light.1, frame.light.2, 0)
        u.glowAt = f4(rig.glow.at.0 * k, rig.glow.at.1 * k, rig.glow.radius * k, frame.glow)
        u.glowColor = f4(rig.glow.color.0, rig.glow.color.1, rig.glow.color.2, 0)
        u.face = f4(rig.face.at.0 * k, rig.face.at.1 * k, rig.face.radius * k, pose.yaw * rig.face.shift * k)
        if let flame = rig.flame {
            u.flame = f4(flame.base.0 * k, flame.base.1 * k, flame.height * k, 1)
            u.flameArea = f4(flame.at.0 * k, flame.at.1 * k, flame.radius.0 * k, flame.radius.1 * k)
        } else {
            u.flame = f4(0, 0, 1, 0)
            u.flameArea = f4(0, 0, 1, 1)
        }
        u.flameState = f4(pose.flameGrow, frame.seconds, pose.flameStretch, frame.reduced ? 0 : 1)
        return u
    }
}

/// Mirrors `CreatureUniforms` in the .metal file: every member is a float4, so the layouts agree.
struct HabitatSpecimenCarouselCreatureUniforms {
    var boxPd = SIMD4<Float>(repeating: 0)
    var invX = SIMD4<Float>(repeating: 0)
    var invY = SIMD4<Float>(repeating: 0)
    var j0 = SIMD4<Float>(repeating: 0)
    var j1 = SIMD4<Float>(repeating: 0)
    var j2 = SIMD4<Float>(repeating: 0)
    var j3 = SIMD4<Float>(repeating: 0)
    var k01 = SIMD4<Float>(repeating: 0)
    var k23 = SIMD4<Float>(repeating: 0)
    var chest = SIMD4<Float>(repeating: 0)
    var hips = SIMD4<Float>(repeating: 0)
    var eyeA = SIMD4<Float>(repeating: 0)
    var eyeB = SIMD4<Float>(repeating: 0)
    var lidOrigin = SIMD4<Float>(repeating: 0)
    var screenShift = SIMD4<Float>(repeating: 0)
    var misc = SIMD4<Float>(repeating: 0)
    var tint = SIMD4<Float>(repeating: 0)
    var light = SIMD4<Float>(repeating: 0)
    var glowAt = SIMD4<Float>(repeating: 0)
    var glowColor = SIMD4<Float>(repeating: 0)
    var face = SIMD4<Float>(repeating: 0)
    var flame = SIMD4<Float>(repeating: 0)
    var flameArea = SIMD4<Float>(repeating: 0)
    var flameState = SIMD4<Float>(repeating: 0)
}
