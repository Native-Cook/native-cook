import Foundation

// Particles that outlive a signature move, as analytic physics in seconds of the effect clock. The
// GPU particle world draws them live; the signature shader draws these when a frame is frozen for
// review (previewTime) or the GPU world is not running. Mirrors HabitatParticles.ts.

private typealias M = HabitatSpecimenCarouselMotion

enum HabitatSpecimenCarouselParticles {
    static let leaves = 12
    /// Gravity on a drop of water, in units per second squared.
    static let dropGravity = 1500.0

    private static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = max(0, min(1, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }

    private static func hash(_ n: Double) -> Double {
        let s = sin(n * 127.1 + 311.7) * 43758.5453
        return s - s.rounded(.down)
    }

    private struct LeafPath { var x, y, angle, roll: Double }

    /// A leaf's steady fall speed once its burst is spent.
    private static func leafFall(_ i: Int, _ unit: Double) -> Double {
        (210 + 70 * hash(Double(i) * 7.31 + 5)) * unit
    }

    /// One leaf thrown out of the bulb. A leaf is mostly drag: its burst dies away exponentially while
    /// it settles to a terminal fall, and once slow it flutters like a pendulum.
    private static func leafPath(_ i: Int, _ t: Double, _ unit: Double) -> LeafPath {
        func h(_ k: Double) -> Double { hash(Double(i) * 7.31 + k) }
        let launch = -Double.pi / 2 + ((Double(i) + 0.5) / Double(leaves) - 0.5) * 2.6 + (h(2) - 0.5) * 0.24
        let speed = (220 + 90 * h(3)) * unit
        let drag = 2.3 + 0.8 * h(4)
        let fall = leafFall(i, unit)
        let wind = (14 + 14 * h(6)) * unit
        let omega = (Double.pi * 2) / (0.85 + 0.45 * h(7))
        let phase = Double.pi * 2 * h(8)
        let swing = (11 + 11 * h(9)) * unit
        let decay = exp(-drag * t)
        let spent = (1 - decay) / drag
        let flutter = (1 - decay) * (1 - decay)
        let sway = omega * t + phase
        let spin = (h(10) - 0.5) * 24
        let tumble = (h(11) * Double.pi * 2) + spin * spent
        // The burst's spin dies into a near-flat attitude (long axis across the fall).
        let flat = M.round(tumble / Double.pi) * Double.pi
        return LeafPath(
            x: (cos(launch) * speed - wind) * spent + wind * t + swing * flutter * sin(sway),
            y: (sin(launch) * speed - fall) * spent + fall * t + swing * 0.22 * flutter * cos(2 * sway),
            angle: tumble + (flat - tumble) * flutter + 0.55 * flutter * cos(sway),
            roll: (h(12) - 0.5) * 18 * spent + 0.75 * flutter * sin(sway)
        )
    }

    /// Every leaf for one frame: position and attitude (x, y, angle, roll) and its state (opacity,
    /// size, how settled on the ground, layer: 0 behind the creature, 1 in front).
    static func leafUniforms(seconds: Double, originX: Double, originY: Double, floorY: Double, unit: Double) -> (leaf: [SIMD4<Float>], state: [SIMD4<Float>]) {
        var leaf: [SIMD4<Float>] = []
        var state: [SIMD4<Float>] = []
        func f4(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> SIMD4<Float> { SIMD4(Float(a), Float(b), Float(c), Float(d)) }
        let ending = 1 - smooth(M.fxSeconds - 0.5, M.fxSeconds, seconds)
        for i in 0..<leaves {
            func h(_ k: Double) -> Double { hash(Double(i) * 7.31 + k) }
            let born = 0.56 + 0.022 * Double(i) + 0.02 * h(1)
            let age = seconds - born
            if age <= 0 || ending <= 0 { leaf.append(.zero); state.append(.zero); continue }
            // Some fall behind the creature, the rest in front of it.
            let behind = h(13) < 0.4
            let depth = (behind ? -(4 + 14 * h(14)) : 3 + 18 * h(14)) * unit
            let ground = floorY + depth - originY
            // First touch of the floor: Newton's method from the steady-fall estimate.
            let fall = leafFall(i, unit)
            var landed = max(0.3, ground / fall)
            for _ in 0..<5 {
                let y = leafPath(i, landed, unit).y - ground
                let slope = (leafPath(i, landed + 0.01, unit).y - ground - y) / 0.01
                landed = max(0.3, landed - y / max(slope, fall * 0.2))
            }
            let scale = (1 + depth / (260 * unit)) * (0.62 + 0.38 * smooth(0, 0.14, age))
            if age < landed {
                let p = leafPath(i, age, unit)
                leaf.append(f4(originX + p.x, originY + p.y, p.angle, p.roll))
                state.append(f4(smooth(0, 0.06, age) * ending, scale, 0, behind ? 0 : 1))
                continue
            }
            // On the floor: a short slide in the direction it was moving, then flat and still.
            let touch = leafPath(i, landed, unit)
            let after = leafPath(i, landed + 0.02, unit)
            let rest = 1 - pow(1 - min(1, (age - landed) / 0.26), 3)
            let lying = M.round(touch.angle / Double.pi) * Double.pi + (h(15) - 0.5) * 0.5
            let rock = 0.14 * sin(rest * Double.pi * 2) * (1 - rest)
            let roll = touch.roll + ((cos(touch.roll) > 0 ? 1.05 : Double.pi - 1.05) - touch.roll) * rest
            leaf.append(f4(
                originX + touch.x + (after.x - touch.x) * 2.5 * rest,
                originY + ground,
                touch.angle + (lying - touch.angle) * rest + rock,
                roll
            ))
            state.append(f4((1 - smooth(0.55, 1.05, age - landed)) * ending, scale, rest, behind ? 0 : 1))
        }
        return (leaf, state)
    }
}
