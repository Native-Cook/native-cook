import Foundation

/// All the motion of the Spring Reorder Checklist, ported one to one from `springReorderMotion.ts`. Positions are in
/// slots (one row = 1) unless named otherwise; the film is a pure function of loop time, so the neighbours' springs
/// are closed-form from the moment the held row passes them.
enum SpringReorderChecklistMotion {
    static let loop = 9.0

    static let idle = 2.0
    static let returnS = 0.75

    static let rowH = 64.0
    static let longPress = 0.26
    static let tapMax = 0.24
    static let liftScale = 1.035
    static let edgeZone = 88.0 // pt from the list's edge where auto-scroll starts
    static let edgeSpeed = 620.0 // pt/s at the very edge

    static let filmFrom = 1
    static let filmTo = 9
    static let dropPast = 0.18 // the film's finger lets go this far past the slot

    struct Spring { let stiffness: Double; let damping: Double }
    /// response 0.34 s, damping ratio 0.74
    static let partSpring = Spring(stiffness: 341.5, damping: 27.35)
    /// response 0.42 s, damping ratio 0.56: the small overshoot of a dropped row
    static let dropSpring = Spring(stiffness: 223.8, damping: 16.76)
    static let liftSpring = Spring(stiffness: 420, damping: 26)

    enum T {
        static let pressA = 0.6, dragA = 0.85, dropA = 2.55, tapA = 3.25, drawA = 3.35, drawAEnd = 3.85
        static let pressB = 4.4, dragB = 4.65, dropB = 6.35, tapB = 7.0, drawB = 7.1, drawBEnd = 7.6
    }

    static func clamp(_ v: Double, _ a: Double, _ b: Double) -> Double { min(max(v, a), b) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func smooth(_ k: Double) -> Double { k * k * (3 - 2 * k) }
    static func easeOutCubic(_ k: Double) -> Double { 1 - pow(1 - k, 3) }
    static func easeInOutCubic(_ k: Double) -> Double { k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2 }
    static func easeInOutQuad(_ k: Double) -> Double { k < 0.5 ? 2 * k * k : 1 - pow(-2 * k + 2, 2) / 2 }
    private static func easeInOutCubicInverse(_ p: Double) -> Double {
        p < 0.5 ? cbrt(p / 4) : 1 - cbrt(2 * (1 - p)) / 2
    }

    static func win(_ t: Double, _ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
        smooth(seg(t, a, b)) * (1 - smooth(seg(t, c, d)))
    }

    /// A damped spring released at rest from `from`, s seconds ago.
    static func springAt(_ s: Double, _ from: Double, _ to: Double, _ response: Double, _ ratio: Double) -> Double {
        if s <= 0 { return from }
        let w = 2 * Double.pi / response
        let zw = ratio * w
        let wd = w * sqrt(1 - ratio * ratio)
        let a = from - to
        return to + exp(-zw * s) * (a * cos(wd * s) + (zw * a / wd) * sin(wd * s))
    }

    /// The held row's position in the film, in slots.
    static func heroSlot(_ t: Double, _ from: Int, _ to: Int) -> Double {
        let f = Double(from), o = Double(to)
        if t < T.dragA { return f }
        if t < T.dropA { return mix(f, o + dropPast, easeInOutCubic(seg(t, T.dragA, T.dropA))) }
        if t < T.dragB { return springAt(t - T.dropA, o + dropPast, o, 0.42, 0.56) }
        if t < T.dropB { return mix(o, f - dropPast, easeInOutCubic(seg(t, T.dragB, T.dropB))) }
        if t < T.tapB + 0.6 { return springAt(t - T.dropB, f - dropPast, f, 0.42, 0.56) }
        return f
    }

    private static func partStep(_ s: Double) -> Double {
        if s <= 0 { return 0 }
        if s >= 1.1 { return 1 }
        return springAt(s, 0, 1, 0.34, 0.74)
    }

    /// Any row's position in the film: a row between the two ends steps up as the held row passes it and back.
    static func rowSlot(_ slot: Int, _ t: Double, _ from: Int, _ to: Int) -> Double {
        if slot == from { return heroSlot(t, from, to) }
        if slot < from || slot > to { return Double(slot) }
        let f = Double(from), o = Double(to)
        let crossing = Double(slot) - 0.5
        let forward = (crossing - f) / (o + dropPast - f)
        let back = (o - crossing) / (o - f + dropPast)
        let t1 = mix(T.dragA, T.dropA, easeInOutCubicInverse(forward))
        let t2 = mix(T.dragB, T.dropB, easeInOutCubicInverse(back))
        return Double(slot) - partStep(t - t1) + partStep(t - t2)
    }

    static func lift(_ t: Double) -> Double {
        if t < T.pressA { return 0 }
        if t < T.dragA { return easeOutCubic(seg(t, T.pressA, T.dragA)) }
        if t < T.dropA { return 1 }
        if t < T.pressB { return springAt(t - T.dropA, 1, 0, 0.36, 0.6) }
        if t < T.dragB { return easeOutCubic(seg(t, T.pressB, T.dragB)) }
        if t < T.dropB { return 1 }
        if t < T.tapB + 0.6 { return springAt(t - T.dropB, 1, 0, 0.36, 0.6) }
        return 0
    }

    static func check(_ t: Double) -> Double {
        easeOutCubic(seg(t, T.drawA, T.drawAEnd)) * (1 - easeInOutCubic(seg(t, T.drawB, T.drawBEnd)))
    }

    static func scroll(_ t: Double, _ distance: Double) -> Double {
        let down = easeInOutCubic(seg(t, T.dragA + 0.3, T.dropA - 0.1))
        let up = easeInOutCubic(seg(t, T.dragB + 0.3, T.dropB - 0.1))
        return distance * (down - up)
    }

    static func ghost(_ t: Double) -> Double {
        win(t, 0.4, 0.6, 2.55, 2.75) + win(t, 3.05, 3.25, 3.45, 3.65) + win(t, 4.2, 4.4, 6.35, 6.55) + win(t, 6.8, 7.0, 7.2, 7.4)
    }

    /// True while the ghost finger is on the check circle, false on the row's grip.
    static func ghostOnCheck(_ t: Double) -> Bool { (t >= 2.9 && t < 3.9) || t >= 6.7 }

    /// The ghost finger pressing for the two taps.
    static func ghostTap(_ t: Double) -> Double {
        win(t, T.tapA - 0.04, T.tapA + 0.06, T.tapA + 0.14, T.tapA + 0.26)
            + win(t, T.tapB - 0.04, T.tapB + 0.06, T.tapB + 0.14, T.tapB + 0.26)
    }

    /// Where every row is after moving row `id` to `toSlot`.
    static func moveOrder(_ order: [Int], _ id: Int, _ toSlot: Int) -> [Int] {
        let fromSlot = order[id]
        var next = order
        if fromSlot == toSlot { return next }
        for i in next.indices {
            let s = next[i]
            if i == id { next[i] = toSlot }
            else if fromSlot < toSlot && s > fromSlot && s <= toSlot { next[i] = s - 1 }
            else if fromSlot > toSlot && s >= toSlot && s < fromSlot { next[i] = s + 1 }
        }
        return next
    }
}
