import Foundation

/// The five sources, their orbits, the camera, and the self-playing loop, as closed-form functions of time.
enum SpatialOrbitMixerModel {
    struct Source {
        let name: String, color: UInt32
        /// Orbit radius (points on the floor), start angle (degrees; 0 is straight ahead of the
        /// listener, 90 to the right), whole turns per loop, pulses and bobs a second.
        let ring: Double, start: Double, turns: Double, beat: Double, bob: Double
        /// Where it sits on the line in Stereo (x on the floor).
        let pan: Double
    }

    static let sources: [Source] = [
        Source(name: "Vocals", color: 0xB6F23A, ring: 72, start: 0, turns: 1, beat: 2, bob: 0.5, pan: 0),
        Source(name: "Keys", color: 0xA98BFF, ring: 112, start: 110, turns: 2, beat: 3, bob: 0.3, pan: 120),
        Source(name: "Guitar", color: 0xFFC247, ring: 112, start: 250, turns: 1, beat: 1.5, bob: 0.4, pan: -120),
        Source(name: "Drums", color: 0xFF6A5C, ring: 152, start: 300, turns: 0, beat: 4, bob: 0.2, pan: -60),
        Source(name: "Bass", color: 0x35D6FF, ring: 152, start: 140, turns: -1, beat: 1, bob: 0.1, pan: 60),
    ]
    static let rings = [72.0, 112, 152]
    static let modes = ["Stereo", "Spatial", "Fixed"]
    /// The stereo line runs this far in front of the listener.
    static let stereoZ = 60.0

    static let loop = 10.0
    /// Camera: the floor is seen from 40° above (the rings' height is 64 % of their width); a vertical drag tilts it 24°–56°.
    static let elevation = 40.0

    // MARK: Curves

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = min(max((x - a) / (b - a), 0), 1)
        return u * u * (3 - 2 * u)
    }
    static func easeInOut(_ x: Double) -> Double {
        let u = min(max(x, 0), 1)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }
    static func easeOutBack(_ x: Double) -> Double {
        let u = min(max(x, 0), 1), c1 = 1.6, c3 = c1 + 1
        return 1 + c3 * pow(u - 1, 3) + c1 * pow(u - 1, 2)
    }
    /// The shortest turn from a to b, in degrees.
    static func turn(_ a: Double, _ b: Double) -> Double {
        var d = (b - a).truncatingRemainder(dividingBy: 360)
        if d > 180 { d -= 360 }
        if d < -180 { d += 360 }
        return d
    }

    // MARK: Orbits

    /// The drums sway ±12° instead of orbiting.
    static func sway(_ t: Double) -> Double { 12 * sin(2 * .pi * t / loop) }

    /// A source's idle angle (degrees) on the loop clock t; it repeats every loop.
    static func idleAngle(_ i: Int, _ t: Double) -> Double {
        let s = sources[i]
        return s.start + 360 * s.turns * t / loop + (i == 3 ? sway(t) : 0)
    }

    /// A pulse 0…1 on each beat (a quick rise, an exponential fall), and how far the beat has gone.
    static func beat(_ i: Int, _ t: Double) -> (pulse: Double, phase: Double) {
        let p = (t * sources[i].beat).truncatingRemainder(dividingBy: 1)
        let q = p < 0 ? p + 1 : p
        return (exp(-5 * q) * smoothstep(0, 0.04, q), q)
    }
    static func height(_ i: Int, _ t: Double) -> Double { 6 * sin(2 * .pi * sources[i].bob * t) }

    // MARK: A scene

    struct Orb {
        /// On the floor (x right, z towards the viewer) and its height.
        var x = 0.0, z = 0.0, h = 0.0
        /// 1, or up to 1.3 while held; how bright its cone is (0…1).
        var grow = 1.0, lit = 0.0
        /// Its spatial place before the stereo mix: radius and angle (degrees).
        var r = 0.0, a = 0.0
    }

    struct Scene {
        var yaw = 0.0, elevation = SpatialOrbitMixerModel.elevation
        /// 0 spatial, 1 stereo (may overshoot); the segment indicator's place (0 Stereo … 2 Fixed).
        var stereo = 0.0, segment = 1.0
        var orbs = [Orb](repeating: Orb(), count: 5)
        /// The source being dragged, its readout's opacity, and a scripted finger's opacity.
        var held = -1, chip = 0.0, finger = 0.0
        /// Whether head tracking reads on.
        var tracking = true
    }

    static func polar(_ r: Double, _ a: Double) -> (Double, Double) {
        let rad = a * .pi / 180
        return (r * sin(rad), -r * cos(rad))
    }

    /// Mixes the spatial place of a source with its place on the stereo line.
    static func place(_ i: Int, r: Double, a: Double, t: Double, stereo m: Double) -> Orb {
        let (x, z) = polar(r, a)
        let s = sources[i]
        var o = Orb()
        o.x = x + (s.pan - x) * m
        o.z = z + (stereoZ - z) * m
        o.h = height(i, t) * (1 - min(max(m, 0), 1))
        o.r = r
        o.a = a
        return o
    }

    /// The loop at time t (0 ≤ t < loop).
    static func auto(_ t: Double) -> Scene {
        var sc = Scene()
        // The view turns 25° and back, as if the phone turned.
        if t >= 4 && t < 5.6 { sc.yaw = 25 * pow(sin(.pi * (t - 4) / 1.6), 2) }
        // Stereo at 6.0 (0.6 s), back to spatial at 7.4 (0.6 s, springy).
        var m = 0.0
        if t >= 6 && t < 7.4 { m = easeOutBack((t - 6) / 0.6) }
        if t >= 7.4 { m = 1 - easeOutBack((t - 7.4) / 0.6) }
        sc.stereo = m
        sc.segment = 1 - min(max(m, 0), 1)
        for i in 0..<5 {
            var r = sources[i].ring, a = idleAngle(i, t)
            if i == 3 && t >= 1.3 && t < 6.6 {
                // A finger drags the drums from the outer ring to the inner ring on the left (240°).
                let from = idleAngle(3, 1.3)
                let u = easeInOut((t - 1.3) / 1.6)
                r = 152 + (72 - 152) * u
                a = from + turn(from, 240) * u
                if t >= 2.9 { a = 240 + sway(t) - sway(2.9) }
            }
            var o = place(i, r: r, a: a, t: t, stereo: m)
            if i == 3 {
                let held = smoothstep(1.2, 1.4, t) * (1 - smoothstep(3.3, 3.5, t))
                o.grow = 1 + 0.3 * held
                o.lit = held
                sc.chip = smoothstep(1.3, 1.5, t) * (1 - smoothstep(3.3, 3.6, t))
                sc.finger = smoothstep(1.0, 1.25, t) * (1 - smoothstep(3.3, 3.6, t))
                if held > 0.01 { sc.held = 3 }
            }
            sc.orbs[i] = o
        }
        return sc
    }

    /// Mixes two scenes (u 0…1) field by field.
    static func mix(_ a: Scene, _ b: Scene, _ u: Double) -> Scene {
        func l(_ x: Double, _ y: Double) -> Double { x + (y - x) * u }
        var s = Scene()
        s.yaw = l(a.yaw, b.yaw); s.elevation = l(a.elevation, b.elevation)
        s.stereo = l(a.stereo, b.stereo); s.segment = l(a.segment, b.segment)
        for i in 0..<5 {
            let p = a.orbs[i], q = b.orbs[i]
            s.orbs[i] = Orb(x: l(p.x, q.x), z: l(p.z, q.z), h: l(p.h, q.h), grow: l(p.grow, q.grow), lit: l(p.lit, q.lit),
                            r: l(p.r, q.r), a: p.a + turn(p.a, q.a) * u)
        }
        s.held = u < 0.5 ? a.held : b.held
        s.chip = l(a.chip, b.chip); s.finger = l(a.finger, b.finger)
        s.tracking = u < 0.5 ? a.tracking : b.tracking
        return s
    }

    // MARK: Touch

    /// The scene under a finger, on the block's clock (seconds since it appeared).
    struct Manual {
        /// The clock time where the idle orbits' phase is 0 (the loop's start they continue from).
        var anchor = 0.0
        /// Each source's radius and its angle offset from its idle orbit; after a drop the radius
        /// eases from `snapFrom` (0.25 s) to its ring.
        var radius = sources.map(\.ring), offset = [Double](repeating: 0, count: 5)
        var snapFrom = sources.map(\.ring), snapAt = [Double](repeating: -100, count: 5)
        var yaw = 0.0, yawFrom = 0.0, yawAt = -100.0
        var elevation = SpatialOrbitMixerModel.elevation
        /// The mode (0 Stereo, 1 Spatial, 2 Fixed) and the eased change to it.
        var mode = 1
        var stereoFrom = 0.0, stereoTo = 0.0, segmentFrom = 1.0, segmentTo = 1.0, modeAt = -100.0
        /// The source under the finger (−1: none), where it is on the floor, and when it was taken and dropped.
        var held = -1, heldX = 0.0, heldZ = 0.0, heldAt = -100.0
        var dropped = -1, droppedAt = -100.0
    }

    static func unpolar(_ x: Double, _ z: Double) -> (r: Double, a: Double) {
        (hypot(x, z), atan2(x, -z) * 180 / .pi)
    }

    static func scene(_ m: Manual, at t: Double) -> Scene {
        var sc = Scene()
        let tl = t - m.anchor
        let u = (t - m.modeAt) / 0.6
        sc.stereo = m.stereoFrom + (m.stereoTo - m.stereoFrom) * easeOutBack(u)
        sc.segment = m.segmentFrom + (m.segmentTo - m.segmentFrom) * easeInOut(u)
        sc.yaw = m.yaw + (m.yawFrom - m.yaw) * (1 - easeInOut((t - m.yawAt) / 0.6))
        sc.elevation = m.elevation
        sc.tracking = m.mode != 2
        for i in 0..<5 {
            let snap = 1 - pow(1 - min(max((t - m.snapAt[i]) / 0.25, 0), 1), 3)
            var r = m.radius[i] + (m.snapFrom[i] - m.radius[i]) * (1 - snap)
            var a = idleAngle(i, tl) + m.offset[i]
            if m.held == i { (r, a) = unpolar(m.heldX, m.heldZ) }
            var o = place(i, r: r, a: a, t: tl, stereo: sc.stereo)
            var on = 0.0
            if m.held == i { on = smoothstep(0, 0.2, t - m.heldAt) }
            if m.dropped == i { on = 1 - smoothstep(0, 0.25, t - m.droppedAt) }
            o.grow = 1 + 0.3 * on
            o.lit = on
            sc.orbs[i] = o
        }
        if m.held >= 0 {
            sc.held = m.held
            sc.chip = smoothstep(0, 0.2, t - m.heldAt)
        } else if m.dropped >= 0 && t - m.droppedAt < 0.3 {
            sc.held = m.dropped
            sc.chip = 1 - smoothstep(0, 0.3, t - m.droppedAt)
        }
        return sc
    }

    /// The idle orbit's phase clock for a scene: the loop time, or the manual clock since its anchor.
    static func posmod(_ x: Double, _ m: Double) -> Double {
        let r = x.truncatingRemainder(dividingBy: m)
        return r < 0 ? r + m : r
    }

    // MARK: Projection

    struct View { var cx: Double, cy: Double, scale: Double, yaw: Double, elevation: Double }

    /// A floor point (x, z) at height h on the screen, and its depth (positive: towards the viewer).
    static func project(_ v: View, x: Double, z: Double, h: Double) -> (X: Double, Y: Double, depth: Double) {
        let y = v.yaw * .pi / 180, e = v.elevation * .pi / 180
        let xr = x * cos(y) - z * sin(y), zr = x * sin(y) + z * cos(y)
        return (v.cx + v.scale * xr, v.cy + v.scale * (zr * sin(e) - h * cos(e)), zr)
    }

    /// A screen point back on the floor (height 0).
    static func unproject(_ v: View, X: Double, Y: Double) -> (x: Double, z: Double) {
        let y = v.yaw * .pi / 180, e = v.elevation * .pi / 180
        let xr = (X - v.cx) / v.scale, zr = (Y - v.cy) / v.scale / sin(e)
        return (xr * cos(y) + zr * sin(y), -xr * sin(y) + zr * cos(y))
    }

    /// The readout for a source at (x, z): its level (0 dB on the inner ring) and its direction.
    static func readout(x: Double, z: Double) -> String {
        let r = max(hypot(x, z), 1)
        let db = 20 * log10(72 / r)
        let az = atan2(x, -z) * 180 / .pi
        let deg = Int(abs(az).rounded())
        let side = deg == 0 || deg == 180 ? "" : (az < 0 ? " L" : " R")
        return String(format: "%@%.1f dB · %d°%@", db < 0 ? "−" : "+", abs(db), deg, side)
    }
}
