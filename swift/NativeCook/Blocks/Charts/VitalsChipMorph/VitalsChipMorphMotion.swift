import CoreGraphics
import Foundation
import SwiftUI

/// Pure timing, geometry and interpolation functions for Vitals Chip Morph.
///
/// Every frame is derived from the blend `(lo, hi, u)` and the loop time. No
/// mutable animation state is stored here, which keeps autoplay and touch-driven
/// frames on the same closed-form motion.
enum VitalsChipMorphMotion {
    static let LOOP = 9.6
    static let N = 180
    /// After the last touch, wait this long before returning to Readiness.
    static let RESUME_AFTER = 2.0
    static let BEAT = 60.0 / 62.0

    struct Timeline {
        static let pressFitness = 1.5
        static let pressEnd = 1.66
        static let toFitness = 1.6
        static let dragStart = 3.9
        static let dragEnd = 4.25
        static let dragTo = 0.34
        static let firstBeat = 5.2
        static let beatsEnd = 6.95
        static let returnStart = 6.95
        static let restAt = 8.85
    }

    /// The loop source calls this object `T`.
    static let T = Timeline.self

    struct SpringSpec {
        let omega: Double
        let zeta: Double
    }

    struct Springs {
        /// A chip tap: critically damped, ω 4.
        static let tap = SpringSpec(omega: 4, zeta: 1)
        /// A released drag: ζ 0.9, carrying the finger's velocity.
        static let release = SpringSpec(omega: 6, zeta: 0.9)
        /// The idle return to rest: critically damped, ω 4.5.
        static let idle = SpringSpec(omega: 4.5, zeta: 1)
        /// Reduced motion uses a 200 ms fade instead of the morph.
        static let reducedFade = 0.2
    }

    static let SPRINGS = Springs.self

    struct DragSpec {
        let span: Double
        let commitDistance: Double
        let commitVelocity: Double
        let rubber: Double
        let rubberFalloff: Double
    }

    static let DRAG = DragSpec(span: 760, commitDistance: 0.5, commitVelocity: 1.1, rubber: 0.55, rubberFalloff: 240)
    /// The simulated drag's release velocity in units of transition progress per second.
    static let DRAG_RELEASE_VELOCITY = 1.4

    // Lower-case aliases are convenient in Swift call sites while the uppercase
    // names above preserve the React Native source contract.
    static let loop = LOOP
    static let resumeAfter = RESUME_AFTER
    static let beat = BEAT

    // MARK: Pure scalar helpers

    static func clamp01(_ value: Double) -> Double {
        value < 0 ? 0 : value > 1 ? 1 : value
    }

    static func seg(_ time: Double, _ from: Double, _ to: Double) -> Double {
        clamp01((time - from) / (to - from))
    }

    static func lerp(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * progress
    }

    /// Symmetric cubic approximation to cubic-bezier(0.77, 0, 0.175, 1).
    static func easeInOut(_ value: Double) -> Double {
        value < 0.5 ? 4 * value * value * value : 1 - pow(-2 * value + 2, 3) / 2
    }

    static func easeOut(_ value: Double) -> Double {
        1 - pow(1 - value, 3)
    }

    /// A 0 → 1 → 0 bump over [from, to].
    static func bump(_ time: Double, _ from: Double, _ to: Double) -> Double {
        let progress = seg(time, from, to)
        return progress <= 0 || progress >= 1 ? 0 : sin(Double.pi * progress)
    }

    /// Closed-form damped spring from x0 (velocity v0, units/s) towards 1.
    static func spring(_ tau: Double, _ x0: Double, _ v0: Double, _ omega: Double, _ zeta: Double) -> Double {
        guard tau > 0 else { return x0 }
        let a = 1 - x0
        if zeta >= 1 {
            return 1 - exp(-omega * tau) * (a + (a * omega - v0) * tau)
        }
        let wd = omega * sqrt(1 - zeta * zeta)
        let decay = exp(-zeta * omega * tau)
        return 1 - decay * (a * cos(wd * tau) + ((a * zeta * omega - v0) / wd) * sin(wd * tau))
    }

    /// Velocity of the same spring, sampled with the RN source's 240 Hz finite difference.
    static func springVelocity(_ tau: Double, _ x0: Double, _ v0: Double, _ omega: Double, _ zeta: Double) -> Double {
        let h = 1.0 / 240.0
        return (spring(tau + h, x0, v0, omega, zeta) - spring(tau, x0, v0, omega, zeta)) / h
    }

    private static func pulseValue(_ phase: Double, _ from: Double, _ to: Double) -> Double {
        guard phase >= from, phase <= to else { return 0 }
        let sine = sin(Double.pi * (phase - from) / (to - from))
        return sine * sine
    }

    /// The sibling block's 62 BPM beat: a 6% systole and a 2.5% echo.
    static func pulse(_ phase: Double) -> Double {
        0.06 * pulseValue(phase, 0.08, 0.23) + 0.025 * pulseValue(phase, 0.31, 0.45)
    }

    // MARK: Sampled shape geometry

    private static func sampleArcPoints() -> [CGPoint] {
        let gauge = VitalsChipMorphTheme.Layout.gauge
        var output: [CGPoint] = []
        output.reserveCapacity(N)
        for index in 0..<N {
            let angle = (gauge.from + (gauge.to - gauge.from) * (Double(index) / Double(N - 1))) * Double.pi / 180
            output.append(CGPoint(
                x: CGFloat(gauge.cx + gauge.r * cos(angle)),
                y: CGFloat(gauge.cy - gauge.r * sin(angle))
            ))
        }
        return output
    }

    private static func sampleCirclePoints() -> [CGPoint] {
        let rings = VitalsChipMorphTheme.Layout.rings
        var output: [CGPoint] = []
        output.reserveCapacity(N)
        for index in 0..<N {
            // t = 0 at the bottom, clockwise on screen (bottom → left → top → right).
            let angle = (270 - 360 * (Double(index) / Double(N))) * Double.pi / 180
            output.append(CGPoint(
                x: CGFloat(rings.cx + rings.r[0] * cos(angle)),
                y: CGFloat(rings.cy - rings.r[0] * sin(angle))
            ))
        }
        return output
    }

    private static func flatten(_ points: [CGPoint]) -> [Double] {
        points.flatMap { [Double($0.x), Double($0.y)] }
    }

    /// The arc is open; the circle and heart are closed. The uppercase arrays
    /// preserve the RN flattened x/y contract, while the point arrays are handy
    /// for SwiftUI Path construction.
    static let ARC_POINTS = sampleArcPoints()
    static let CIRCLE_POINTS = sampleCirclePoints()
    static let HEART_POINTS = VitalsChipMorphHeartOutline.points
    static let ARC: [Double] = flatten(ARC_POINTS)
    static let CIRCLE: [Double] = flatten(CIRCLE_POINTS)
    static let HEART: [Double] = VitalsChipMorphHeartOutline.HEART_OUTLINE
    static let SHAPES: [[Double]] = [ARC, CIRCLE, HEART]
    static let POINT_SHAPES: [[CGPoint]] = [ARC_POINTS, CIRCLE_POINTS, HEART_POINTS]
    /// Where each shape's parameter t lives: the arc spans i / (N − 1), closed shapes i / N.
    static let CLOSED: [Double] = [0, 1, 1]

    // MARK: Blend and hero frame

    static func pairOf(_ lo: Int, _ hi: Int) -> Int {
        if lo == hi { return -1 }
        if lo == 0 && hi == 1 { return 0 } // arc ↔ ring
        if lo == 1 && hi == 2 { return 1 } // ring ↔ heart
        return 2 // arc ↔ heart
    }

    static func weight(_ k: Int, _ lo: Int, _ hi: Int, _ progress: Double) -> Double {
        if lo == hi { return lo == k ? 1 : 0 }
        if k == lo { return 1 - progress }
        if k == hi { return progress }
        return 0
    }

    static func shapeProgress(_ pair: Int, _ progress: Double) -> Double {
        if pair == 0 { return easeInOut(seg(progress, 0, 0.62)) }
        if pair == 1 { return easeInOut(seg(progress, 0.06, 0.88)) }
        if pair == 2 { return easeInOut(seg(progress, 0.08, 0.9)) }
        return 0
    }

    struct Hero {
        var sh: Double
        var width: Double
        var fill: Double
        var colourMix: Double
        var t0: Double
        var t1: Double
        var accentWidth: Double
        var accentOpacity: Double
        var accentRed: Double
        var dotOpacity: Double
        var dotD: Double
        var ringR0: Double
        var ringR1: Double
        var ringO0: Double
        var ringO1: Double
        var ringP0: Double
        var ringP1: Double
        var arrows: Double
        var heartImage: Double
        var halo: Double
        var sevenOpacity: Double
        var sevenRoll: Double
        var bpm0: Double
        var bpm1: Double
        var bpmUnit: Double
    }

    private static var segment: VitalsChipMorphTheme.Layout.Segment {
        VitalsChipMorphTheme.Layout.segment
    }

    private static var rings: VitalsChipMorphTheme.Layout.Rings {
        VitalsChipMorphTheme.Layout.rings
    }

    /// Everything the hero draws for the pair (lo, hi) at progress u.
    static func heroFrame(_ lo: Int, _ hi: Int, _ progress: Double) -> Hero {
        let pair = pairOf(lo, hi)
        // The settled screen uses the pair that ends there at its endpoint.
        var choreography = pair
        var value = progress
        if pair < 0 {
            choreography = lo == 0 ? 0 : lo == 1 ? 0 : 1
            value = lo == 0 ? 0 : 1
        }

        let shape = shapeProgress(choreography, value)
        let segmentSpec = segment
        let gauge = VitalsChipMorphTheme.Layout.gauge
        let dot = VitalsChipMorphTheme.Layout.dot
        var frame = Hero(
            sh: shape,
            width: 0,
            fill: 0,
            colourMix: 0,
            t0: segmentSpec.t0,
            t1: segmentSpec.t1,
            accentWidth: gauge.stroke,
            accentOpacity: 1,
            accentRed: 0,
            dotOpacity: 1,
            dotD: dot.d,
            ringR0: 1,
            ringR1: 1,
            ringO0: 0,
            ringO1: 0,
            ringP0: 0,
            ringP1: 0,
            arrows: 0,
            heartImage: 0,
            halo: 0,
            sevenOpacity: 0,
            sevenRoll: 7,
            bpm0: 0,
            bpm1: 0,
            bpmUnit: 0
        )
        let ringStroke = rings.stroke

        if choreography == 0 {
            // Readiness ↔ Fitness: the arc closes into the Move ring.
            frame.width = lerp(gauge.stroke, ringStroke, shape)
            frame.colourMix = easeInOut(seg(value, 0.08, 0.45))
            let lead = easeInOut(seg(value, 0.2, 1))
            frame.t0 = lerp(segmentSpec.t0, 0.5, easeOut(seg(value, 0, 0.5)))
            frame.t1 = lerp(segmentSpec.t1, 0.5 + rings.progress[0], lead)
            frame.accentWidth = frame.width
            frame.accentRed = easeInOut(seg(value, 0.05, 0.38))
            frame.dotOpacity = 1 - seg(value, 0.5, 0.8)
            frame.dotD = lerp(dot.d, ringStroke * 0.62, seg(value, 0.1, 0.6))
            for index in 0..<2 {
                let delay = 0.1 * Double(index)
                let expansion = easeOut(seg(value, 0.32 + delay, 0.78 + delay))
                let radius = lerp(1, rings.r[index + 1] / rings.r[0], expansion)
                let opacity = easeOut(seg(value, 0.3 + delay, 0.42 + delay))
                let progress = rings.progress[index + 1] * easeInOut(seg(value, 0.5 + delay, 1))
                if index == 0 {
                    frame.ringR0 = radius
                    frame.ringO0 = opacity
                    frame.ringP0 = progress
                } else {
                    frame.ringR1 = radius
                    frame.ringO1 = opacity
                    frame.ringP1 = progress
                }
            }
            frame.arrows = seg(value, 0.82, 1)
            frame.sevenOpacity = 1 - seg(value, 0, 0.22)
            frame.sevenRoll = 7
        } else if choreography == 1 {
            // Fitness ↔ Heart: the ring fills in and becomes the heart.
            frame.width = lerp(ringStroke, 0, seg(value, 0.08, 0.86))
            frame.fill = easeInOut(seg(value, 0.14, 0.62))
            frame.colourMix = easeInOut(seg(value, 0.04, 0.42))
            let shrink = easeInOut(seg(value, 0, 0.45))
            frame.t0 = 0.5
            frame.t1 = lerp(0.5 + rings.progress[0], 0.5, shrink)
            frame.accentWidth = frame.width
            frame.accentOpacity = 1 - seg(value, 0.22, 0.45)
            frame.accentRed = 1
            frame.dotOpacity = 0
            for index in 0..<2 {
                let delay = 0.08 * Double(index)
                let expansion = easeInOut(seg(value, delay, 0.5 + delay))
                let base = rings.r[index + 1] / rings.r[0]
                let radius = lerp(base, base * 0.45, expansion)
                let opacity = 1 - seg(value, 0.12 + delay, 0.42 + delay)
                if index == 0 {
                    frame.ringR0 = radius
                    frame.ringO0 = opacity
                    frame.ringP0 = rings.progress[1]
                } else {
                    frame.ringR1 = radius
                    frame.ringO1 = opacity
                    frame.ringP1 = rings.progress[2]
                }
            }
            frame.arrows = 1 - seg(value, 0, 0.18)
            frame.heartImage = seg(value, 0.62, 0.95)
            frame.halo = seg(value, 0.5, 1)
            frame.bpm0 = easeOut(seg(value, 0.6, 0.9))
            frame.bpm1 = easeOut(seg(value, 0.65, 0.95))
            frame.bpmUnit = seg(value, 0.75, 1)
        } else {
            // Readiness ↔ Heart: the heart opens at its tip into the arch.
            frame.width = lerp(gauge.stroke, 0, seg(value, 0.15, 0.85))
            frame.fill = easeInOut(seg(value, 0.5, 0.88))
            frame.colourMix = seg(value, 0.2, 0.7)
            let back = 1 - value
            let glide = easeOut(seg(back, 0.55, 0.97))
            frame.t0 = segmentSpec.t0
            frame.t1 = lerp(segmentSpec.t0 + 0.004, segmentSpec.t1, glide)
            frame.accentWidth = frame.width
            frame.accentOpacity = seg(back, 0.5, 0.6)
            frame.dotOpacity = seg(back, 0.5, 0.62)
            frame.heartImage = seg(value, 0.7, 0.95)
            frame.halo = seg(value, 0.55, 1)
            frame.sevenOpacity = seg(back, 0.5, 0.62)
            frame.sevenRoll = 7 * glide
            frame.bpm0 = easeOut(seg(value, 0.62, 0.9))
            frame.bpm1 = easeOut(seg(value, 0.67, 0.95))
            frame.bpmUnit = seg(value, 0.75, 1)
        }
        return frame
    }

    /// Returns the hero's flattened N-point x/y array for a blend.
    static func heroPoints(_ lo: Int, _ hi: Int, _ shape: Double) -> [Double] {
        var result: [CGPoint] = []
        result.reserveCapacity(N)
        let points = heroPointArray(lo, hi, shape, out: &result)
        return flatten(points)
    }

    /// Writes the hero's flattened N-point x/y array into an output buffer.
    @discardableResult
    static func heroPoints(_ lo: Int, _ hi: Int, _ shape: Double, out: inout [Double]) -> [Double] {
        let from = SHAPES[lo]
        let to = SHAPES[hi]
        out.removeAll(keepingCapacity: true)
        out.reserveCapacity(2 * N)
        for index in 0..<(2 * N) {
            out.append(from[index] + (to[index] - from[index]) * shape)
        }
        return out
    }

    /// Point form of `heroPoints`, for SwiftUI paths and Canvas rendering.
    @discardableResult
    static func heroPointArray(_ lo: Int, _ hi: Int, _ shape: Double, out: inout [CGPoint]) -> [CGPoint] {
        let from = POINT_SHAPES[lo]
        let to = POINT_SHAPES[hi]
        out.removeAll(keepingCapacity: true)
        out.reserveCapacity(N)
        for index in 0..<N {
            let x = Double(from[index].x) + (Double(to[index].x) - Double(from[index].x)) * shape
            let y = Double(from[index].y) + (Double(to[index].y) - Double(from[index].y)) * shape
            out.append(CGPoint(x: CGFloat(x), y: CGFloat(y)))
        }
        return out
    }

    static func heroPointArray(_ lo: Int, _ hi: Int, _ shape: Double) -> [CGPoint] {
        var result: [CGPoint] = []
        return heroPointArray(lo, hi, shape, out: &result)
    }

    /// Main curve colour per screen.
    static let MAIN_COLOUR: [Color] = [
        VitalsChipMorphTheme.Colors.track,
        VitalsChipMorphTheme.Colors.moveTrack,
        VitalsChipMorphTheme.Colors.heartFlat,
    ]

    // MARK: AutoPlay loop

    struct Blend {
        var lo: Int
        var hi: Int
        var u: Double
        var beat: Double
        var press: Double
        var pressChip: Int
        var target: Int
        /// Liquid-material inputs. They do not change the settled visual state.
        var fxTime = 0.0
        var speed = 0.0
        var beatCount = -1.0
    }

    /// The loop's simulated finger reaches the release point with the same velocity
    /// that seeds the following spring: u = a p² + b p³.
    static func dragCurve(_ progress: Double) -> Double {
        let end = DRAG_RELEASE_VELOCITY * (Timeline.dragEnd - Timeline.dragStart)
        let b = end - 2 * Timeline.dragTo
        let a = Timeline.dragTo - b
        return a * progress * progress + b * progress * progress * progress
    }

    private static func loopBlendBase(_ time: Double) -> Blend {
        let press = bump(time, Timeline.pressFitness, Timeline.pressEnd)
        if time < Timeline.toFitness {
            return Blend(lo: 0, hi: 1, u: 0, beat: 0, press: press, pressChip: 3, target: 1)
        }
        if time < Timeline.dragStart {
            let progress = clamp01(spring(time - Timeline.toFitness, 0, 0, Springs.tap.omega, Springs.tap.zeta))
            return Blend(lo: 0, hi: 1, u: progress, beat: 0, press: press, pressChip: 3, target: 1)
        }
        if time < Timeline.dragEnd {
            let progress = dragCurve(seg(time, Timeline.dragStart, Timeline.dragEnd))
            return Blend(lo: 1, hi: 2, u: progress, beat: 0, press: 0, pressChip: -1, target: 2)
        }
        if time < Timeline.returnStart {
            let progress = clamp01(spring(time - Timeline.dragEnd, Timeline.dragTo, DRAG_RELEASE_VELOCITY, Springs.release.omega, Springs.release.zeta))
            let beat: Double
            if time >= Timeline.firstBeat && time < Timeline.beatsEnd {
                beat = pulse(((time - Timeline.firstBeat) / BEAT).truncatingRemainder(dividingBy: 1))
            } else {
                beat = 0
            }
            return Blend(lo: 1, hi: 2, u: progress, beat: beat, press: 0, pressChip: -1, target: 2)
        }
        // Return: pair (0, 2) with u running 1 → 0.
        let back = time >= Timeline.restAt
            ? 1
            : clamp01(spring(time - Timeline.returnStart, 0, 0, Springs.idle.omega, Springs.idle.zeta))
        return Blend(lo: 0, hi: 2, u: 1 - back, beat: 0, press: 0, pressChip: -1, target: 0)
    }

    /// Maps loop time (0 ≤ t ≤ LOOP) to the same blend and liquid inputs used by touch interaction.
    static func loopBlend(_ time: Double) -> Blend {
        var result = loopBlendBase(time)
        result.fxTime = time
        result.speed = loopSpeed(time)
        result.beatCount = loopBeatCount(time)
        return result
    }

    // MARK: Liquid material

    struct LiquidSpec {
        let melt = 0.14
        let set = 0.3
        let wobbleSpeed = 1.6
        let rippleAt = 0.1 * (60.0 / 62.0)
        let speedSmoothing = 0.25
        let loudBeats = 3
        let quietRipple = 0.3
    }

    static let LIQUID = LiquidSpec()

    static func liquidAmount(_ lo: Int, _ hi: Int, _ progress: Double) -> Double {
        guard lo != hi else { return 0 }
        return easeInOut(seg(progress, 0, LIQUID.melt))
            * easeInOut(seg(1 - progress, 0, LIQUID.set))
    }

    static func wobbleAmount(_ speed: Double) -> Double {
        clamp01(abs(speed) / LIQUID.wobbleSpeed)
    }

    struct Ripple {
        let age: Double
        let amp: Double
    }

    static func rippleOf(_ count: Double) -> Ripple {
        guard count >= 0 else { return Ripple(age: 0, amp: 0) }
        let index = floor(count)
        let age = (count - index) * BEAT - LIQUID.rippleAt
        guard age >= 0 else { return Ripple(age: 0, amp: 0) }
        return Ripple(age: age, amp: index < Double(LIQUID.loudBeats) ? 1 : LIQUID.quietRipple)
    }

    static func loopBeatCount(_ time: Double) -> Double {
        guard time >= Timeline.firstBeat, time < Timeline.beatsEnd else { return -1 }
        return (time - Timeline.firstBeat) / BEAT
    }

    /// Exact 120 Hz backward difference used by the React Native loop.
    static func loopSpeed(_ time: Double) -> Double {
        let h = 1.0 / 120.0
        guard time >= h else { return 0 }
        let before = loopBlendBase(time - h)
        let current = loopBlendBase(time)
        guard before.lo == current.lo, before.hi == current.hi else { return 0 }
        return (current.u - before.u) / h
    }
}
