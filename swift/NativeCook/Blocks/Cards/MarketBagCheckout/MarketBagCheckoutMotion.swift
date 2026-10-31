import Foundation

/// Item data, timings and closed-form bag physics shared by autoplay and touch.
/// Durations and event times use seconds; sizes and full-load stretch use points.
/// Item slot coordinates are fractions of the bag belly width and length; rotations use radians.
enum MarketBagCheckoutMotion {
    static let loop = 10.0
    static let fly = 0.55
    static let hold = 1.25
    static let stretch = 34.0
    struct Item {
        let key: String
        let name: String
        let meta: String
        let price: Double
        let weight: Double
        let slotX: Double
        let slotY: Double
        let size: Double
        let rotation: Double
    }
    static let items: [Item] = [
        .init(
            key: "avocado", name: "Hass avocado", meta: "1 pc · ripe today", price: 1.8, weight: 0.22, slotX: -0.3,
            slotY: 0.62, size: 66, rotation: -0.3),
        .init(
            key: "milk", name: "Whole milk", meta: "500 ml · glass bottle", price: 2.4, weight: 0.62, slotX: 0.24,
            slotY: 0.5, size: 78, rotation: 0.06),
        .init(
            key: "lemons", name: "Lemons", meta: "2 pc · unwaxed", price: 1.1, weight: 0.24, slotX: -0.12,
            slotY: 0.74,
            size: 70, rotation: 0.15),
        .init(
            key: "tomatoes", name: "Vine tomatoes", meta: "3 pc · on the vine", price: 2.9, weight: 0.36,
            slotX: 0.12,
            slotY: 0.3, size: 72, rotation: -0.1),
        .init(
            key: "baguette", name: "Country baguette", meta: "1 · baked at 6 am", price: 3.2, weight: 0.26,
            slotX: -0.18, slotY: 0.06, size: 112, rotation: -0.55),
    ]

    /// Per-item flight fractions and landing/leaving timestamps (seconds, -1 for absent events).
    /// Hold and reset are fractions; paid is seconds since payment or -1; bag is the physics clock.
    struct Pose {
        var fly = [Double](repeating: 0, count: 5)
        var land = [Double](repeating: -1, count: 5)
        var leave = [Double](repeating: -1, count: 5)
        var hold = 0.0
        var paid = -1.0
        var reset = 0.0
        var bag = 0.0
    }

    /// Clamps a value to normalized progress between two bounds.
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }

    /// Cubic smoothstep between the supplied bounds, with zero slope at both ends.
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = seg(x, a, b)
        return t * t * (3 - 2 * t)
    }

    /// Symmetric cubic easing for normalized flight and cinch progress.
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    /// Unit spring response at elapsed seconds, parameterized by period in seconds and damping ratio.
    static func springStep(_ t: Double, _ duration: Double, _ ratio: Double) -> Double {
        if t <= 0 { return 0 }
        let angularFrequency = 2 * Double.pi / duration
        let dampedFrequency = angularFrequency * sqrt(1 - ratio * ratio)
        return 1 - exp(-ratio * angularFrequency * t)
            * (cos(dampedFrequency * t) + (ratio * angularFrequency / dampedFrequency) * sin(dampedFrequency * t))
    }

    /// Weighted spring responses give stretch as a fraction of the 34-point full-load extension.
    /// Removing an item subtracts a slower, more damped spring from its landing response.
    static func bagStretch(_ pose: Pose) -> Double {
        let totalWeight = items.reduce(0) { $0 + $1.weight }
        var stretchAmount = 0.0
        for i in 0..<5 where pose.land[i] >= 0 && pose.bag >= pose.land[i] {
            let releaseBounce =
                pose.leave[i] >= 0 && pose.bag > pose.leave[i]
                ? springStep(pose.bag - pose.leave[i], 0.42 * 1.3, 0.55) : 0
            stretchAmount +=
                items[i].weight / totalWeight * (springStep(pose.bag - pose.land[i], 0.42, 0.32) - releaseBounce)
        }
        return stretchAmount
    }

    /// Ten-second loop: rest 0–1; staggered packing 1–2.83; hold 3–4.25; cinch and hop after payment;
    /// paid until 8.55; reverse stagger back to the list until 9.4; rest again from 9.5.
    static func script(_ t: Double) -> Pose {
        var pose = Pose()
        pose.bag = t
        let rest = t >= 9.5
        for i in 0..<5 {
            let packStart = 1 + Double(i) * 0.32
            let unpackStart = 8.55 + Double(4 - i) * 0.12
            if rest { continue }
            if t < unpackStart {
                pose.fly[i] = easeInOut(seg(t, packStart, packStart + fly))
                pose.land[i] = t >= packStart + fly ? packStart + fly : -1
            } else {
                pose.fly[i] = 1 - easeInOut(seg(t, unpackStart, unpackStart + fly * 0.8))
                pose.land[i] = packStart + fly
                pose.leave[i] = unpackStart
            }
        }
        let reset = seg(t, 8.55, 8.9)
        pose.hold = rest ? 0 : t < 8.55 ? smooth(3, 4.25, t) : 1 - reset
        pose.paid = !rest && t >= 4.25 ? t - 4.25 : -1
        pose.reset = rest ? 0 : reset
        return pose
    }

    /// Bag half-width in points at normalized vertical position v: flared rim, belly, rounded bottom.
    static func halfWidth(_ v: Double, _ rim: Double, _ belly: Double) -> Double {
        if v < 0.5 { return rim + (belly - rim) * sin(Double.pi / 2 * (v / 0.5)) }
        let b = (v - 0.5) / 0.5
        return belly * sqrt(max(0, 1 - b * b * 0.92))
    }
}
