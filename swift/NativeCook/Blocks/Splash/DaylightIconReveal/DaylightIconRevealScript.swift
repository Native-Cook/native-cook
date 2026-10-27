import SwiftUI

// The numbers of the loop, copied from react-native/src/blocks/splash/daylight-icon-reveal/DaylightIconReveal.tsx.
// Every function here takes the loop time `t` in seconds and returns what that moment looks like,
// so a frame is a pure function of `t` (which is what makes `previewTime` work).

// MARK: - Easing

/// A cubic Bézier easing, the same curve as `Easing.bezier(x1, y1, x2, y2)` in Reanimated
/// (the WebKit `UnitBezier` solver: Newton's method, then bisection).
struct DaylightIconRevealBezier {
    private let ax: Double
    private let bx: Double
    private let cx: Double
    private let ay: Double
    private let by: Double
    private let cy: Double

    init(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) {
        cx = 3 * x1
        bx = 3 * (x2 - x1) - cx
        ax = 1 - cx - bx
        cy = 3 * y1
        by = 3 * (y2 - y1) - cy
        ay = 1 - cy - by
    }

    private func x(at t: Double) -> Double {
        ((ax * t + bx) * t + cx) * t
    }

    private func y(at t: Double) -> Double {
        ((ay * t + by) * t + cy) * t
    }

    private func slopeX(at t: Double) -> Double {
        (3 * ax * t + 2 * bx) * t + cx
    }

    /// The curve parameter whose x is `target`.
    private func parameter(forX target: Double) -> Double {
        var t = target
        for _ in 0..<8 {
            let error = x(at: t) - target
            if abs(error) < 1e-7 { return t }
            let slope = slopeX(at: t)
            if abs(slope) < 1e-6 { break }
            t -= error / slope
        }
        var low = 0.0
        var high = 1.0
        t = target
        while low < high {
            let value = x(at: t)
            if abs(value - target) < 1e-7 { return t }
            if target > value { low = t } else { high = t }
            t = (high - low) / 2 + low
            if high - low < 1e-9 { break }
        }
        return t
    }

    /// The eased value for a progress in 0...1.
    func callAsFunction(_ progress: Double) -> Double {
        if progress <= 0 { return 0 }
        if progress >= 1 { return 1 }
        return y(at: parameter(forX: progress))
    }
}

// MARK: - The script

/// One of the four copy blocks that rise into place (`Reveal` in the React Native file).
enum DaylightIconRevealBlock {
    case title
    case subtitle
    case button
    case note
}

enum DaylightIconRevealScript {
    /// `EASE` in the React Native file: the strong ease-out used for every reveal.
    static let ease = DaylightIconRevealBezier(0.23, 1, 0.32, 1)
    /// `SWEEP`: the light sweep across the icon.
    static let sweep = DaylightIconRevealBezier(0.77, 0, 0.175, 1)

    /// `IMAGE_SIZE`: the artwork's layout box, in points, before the plane transform.
    static let imageSize = 256.0
    /// `BODY`: where the icon's body sits inside the 1254 px artwork (its shadow is outside it).
    static let bodyX = 82.0 / 1254
    static let bodyY = 86.0 / 1254
    static let bodyWidth = 1093.0 / 1254
    static let bodyHeight = 1090.0 / 1254

    // MARK: Tracks

    /// `POSES`: t, horizontal extent, horizontal perspective, vertical perspective, top-centre Y,
    /// top-edge inclination, vertical extent, opacity.
    static let poses: [[Double]] = [
        [0.35, 4, 0.15, -0.3, 0.66, -0.581, 1.2, 0],
        [0.4, 4, 0.15, -0.3, 0.66, -0.581, 1.2, 0.06],
        [0.5, 3.3, 0.15, -0.25, 0.6075, -0.4072, 0.95, 0.14],
        [0.75, 2.2, 0.15, -0.27, 0.467, -0.04, 0.65, 0.48],
        [1, 1.3, 0.15, -0.27, 0.407, 0.035, 0.45, 0.76],
        [1.2, 0.86, 0.14, -0.27, 0.352, 0.055, 0.35, 0.94],
        [1.3, 0.769814, 0.135696, -0.263308, 0.33301, 0.054257, 0.260944, 1],
        [1.4, 0.695834, 0.104908, -0.216638, 0.31571, 0.038729, 0.239891, 1],
        [1.5, 0.653934, 0.08079, -0.149138, 0.300033, 0.036785, 0.247728, 1],
        [1.6, 0.617184, 0.085426, -0.102367, 0.288539, 0.0336, 0.246882, 1],
        [1.7, 0.584664, 0.056244, -0.080393, 0.278003, 0.025165, 0.239353, 1],
        [1.8, 0.56726, 0.049661, -0.026634, 0.267928, 0.023277, 0.25257, 1],
        [2, 0.517074, 0.060975, -0.038964, 0.255785, 0.01578, 0.222516, 1],
        [2.2, 0.5037, 0, 0, 0.24573, 0, 0.23549, 1],
        [2.5, 0.48889, 0, 0, 0.23891, 0, 0.22867, 1],
        [2.7, 0.48889, 0, 0, 0.23891, 0, 0.22526, 1],
        [3, 0.48889, 0, 0, 0.23891, 0, 0.22526, 1],
        [3.3, 0.5037, 0, 0, 0.23891, 0, 0.23208, 1],
        [4, 0.5037, 0, 0, 0.24232, 0, 0.23208, 1],
    ]

    /// `HEADING_LIFT`: t, remaining lift of the headline (a fraction of its 0.32 start).
    static let headingLift: [[Double]] = [
        [1.75, 0.32], [1.85, 0.29], [1.9, 0.24061], [2, 0.18089], [2.1, 0.1314], [2.2, 0.09215],
        [2.3, 0.06143], [2.4, 0.03754], [2.5, 0.02048], [2.6, 0.01024], [2.7, 0.00171],
        [2.9, -0.00683], [3.3, -0.00683], [4, 0],
    ]

    /// `BUTTON_LIFT`: the same for the Continue button (fraction of its 0.25 start).
    static let buttonLift: [[Double]] = [
        [2.15, 0.25], [2.25, 0.165], [2.3, 0.13311], [2.4, 0.09215], [2.5, 0.06143],
        [2.6, 0.03754], [2.7, 0.02048], [2.8, 0.01024], [2.9, 0], [3.1, -0.00683],
        [3.5, -0.00683], [4, 0],
    ]

    // MARK: Helpers

    /// `previewPhase`: any time folded into one period.
    static func previewPhase(_ t: Double) -> Double {
        guard t.isFinite else { return 0 }
        let phase = t.truncatingRemainder(dividingBy: DaylightIconReveal.loop)
        return phase < 0 ? phase + DaylightIconReveal.loop : phase
    }

    /// `segment`: progress of `t` between two times, clamped to 0...1.
    static func segment(_ t: Double, _ start: Double, _ end: Double) -> Double {
        max(0, min(1, (t - start) / (end - start)))
    }

    /// `sampleChannel`: a monotone cubic Hermite curve through the keys of one column.
    static func sample(_ t: Double, keys: [[Double]], channel: Int) -> Double {
        let last = keys.count - 1
        if t <= keys[0][0] { return keys[0][channel] }
        if t >= keys[last][0] { return keys[last][channel] }
        var i = 0
        while keys[i + 1][0] < t { i += 1 }
        let a = keys[i][0]
        let b = keys[i + 1][0]
        let y0 = keys[i][channel]
        let y1 = keys[i + 1][channel]
        let d = (y1 - y0) / (b - a)
        let before = i > 0 ? (y0 - keys[i - 1][channel]) / (a - keys[i - 1][0]) : 0
        let after = i + 1 < last ? (keys[i + 2][channel] - y1) / (keys[i + 2][0] - b) : 0
        let m0 = before * d > 0 ? 2 * before * d / (before + d) : 0
        let m1 = after * d > 0 ? 2 * after * d / (after + d) : 0
        let p = (t - a) / (b - a)
        let p2 = p * p
        let p3 = p2 * p
        return (2 * p3 - 3 * p2 + 1) * y0
            + (p3 - 2 * p2 + p) * (b - a) * m0
            + (-2 * p3 + 3 * p2) * y1
            + (p3 - p2) * (b - a) * m1
    }

    static func pose(_ t: Double, channel: Int) -> Double {
        sample(t, keys: poses, channel: channel)
    }

    // MARK: Icon

    /// `imagePlane`: the projective transform of the 256 pt artwork box for the icon at time `t`.
    /// React Native hands these 16 numbers to the layer as a column-major matrix, with the box's
    /// top-left corner as the origin; as a plane (z = 0) only 9 of them matter:
    ///
    ///     x' = (m0 x + m4 y + m12) / (m3 x + m7 y + 1)
    ///     y' = (m1 x + m5 y + m13) / (m3 x + m7 y + 1)
    ///
    /// which is what `ProjectionTransform` (origin top-left too) expresses.
    static func imagePlane(_ t: Double, width: Double, height: Double) -> ProjectionTransform {
        let a = pose(t, channel: 1)
        let g = pose(t, channel: 2)
        let h = pose(t, channel: 3)
        let y = pose(t, channel: 4)
        let b = pose(t, channel: 5)
        let c = pose(t, channel: 6)
        let ux = 1 / (imageSize * bodyWidth)
        let u0 = -bodyX / bodyWidth - 0.5
        let vy = 1 / (imageSize * bodyHeight)
        let v0 = -bodyY / bodyHeight
        let denominator = 1 + g * u0 + h * v0
        var plane = ProjectionTransform()
        plane.m11 = width * (0.5 * g + a) * ux / denominator
        plane.m12 = height * b * ux / denominator
        plane.m13 = g * ux / denominator
        plane.m21 = width * 0.5 * h * vy / denominator
        plane.m22 = height * c * vy / denominator
        plane.m23 = h * vy / denominator
        plane.m31 = width * ((0.5 * g + a) * u0 + 0.5 * h * v0 + 0.5) / denominator
        plane.m32 = height * (y + b * u0 + c * v0) / denominator
        plane.m33 = 1
        return plane
    }

    /// The icon's own alpha track (last column of `POSES`); one under Reduce Motion.
    static func iconOpacity(_ t: Double, reduced: Bool) -> Double {
        reduced ? 1 : pose(min(t, 4), channel: 7)
    }

    /// The time the icon's tracks are read at: settled (4 s) under Reduce Motion, otherwise
    /// the loop time up to 4 s.
    static func iconTime(_ t: Double, reduced: Bool) -> Double {
        reduced ? 4 : min(t, 4)
    }

    /// `recede`: the settled icon leaves at the end of the loop, 4% smaller and out of focus.
    static func recede(_ t: Double, reduced: Bool) -> (opacity: Double, blur: Double, scale: Double) {
        let p = reduced ? 0 : ease(segment(t, 7.45, 8.15))
        return (1 - p, p * 8, 1 - 0.04 * p)
    }

    /// `glint`: one sweep of light crosses the icon as it comes to rest.
    static func glint(_ t: Double, reduced: Bool) -> (opacity: Double, translateX: Double) {
        let p = segment(t, 2.5, 3.4)
        return (reduced ? 0 : sin(p * .pi), -110 + sweep(p) * 400)
    }

    /// `glow`: the faint warm and cool light behind the icon.
    static func glow(_ t: Double, reduced: Bool) -> Double {
        reduced ? 0.55 : 0.55 * segment(t, 1.4, 2.6) * (1 - segment(t, 7.45, 8.1))
    }

    // MARK: Copy

    /// What `Reveal` does to a block at time `t`: opacity, blur radius and the vertical offset.
    /// `distance` is the block's start distance below its place (a fraction of the window height
    /// for the first three, 8 pt for the small print).
    static func reveal(
        _ block: DaylightIconRevealBlock, t: Double, reduced: Bool, distance: Double
    ) -> (opacity: Double, blur: Double, translateY: Double) {
        let opacityStart: Double
        switch block {
        case .title: opacityStart = 1.75
        case .subtitle: opacityStart = 1.8
        case .button: opacityStart = 2.15
        case .note: opacityStart = 3.25
        }
        let progress = reduced ? 1 : ease(segment(t, opacityStart, opacityStart + 0.75))
        let returnProgress = reduced ? 0 : ease(segment(t, 7.2, 7.55))
        let remaining: Double
        switch block {
        case .title: remaining = sample(t, keys: headingLift, channel: 1) / 0.32
        case .subtitle: remaining = sample(t - 0.1, keys: headingLift, channel: 1) / 0.32
        case .button: remaining = sample(t, keys: buttonLift, channel: 1) / 0.25
        case .note: remaining = 1 - progress
        }
        // Copy materialises out of a soft focus and leaves upward into one, never as a hard fade.
        let blur = reduced ? 0 : max(0, 1 - progress) * 6 + returnProgress * 4
        return (
            progress * (1 - returnProgress),
            blur,
            reduced ? 0 : distance * remaining - returnProgress * 10
        )
    }

    /// The button answers a touch only while it is on screen and settled: from 2.25 s to 7.2 s.
    static func isReady(_ t: Double, reduced: Bool) -> Bool {
        reduced || (t >= 2.25 && t < 7.2)
    }

    // MARK: Swapping text

    /// What `SwapText` does `elapsed` seconds after its text changed: the old words fade out
    /// fast (140 ms) and the new ones settle a beat later from 10 pt below (260 ms); under
    /// Reduce Motion it is a plain cross-fade (120 ms out, 180 ms in).
    /// `delay` is the text's own extra delay in seconds (`delay` prop / 1000).
    static func swap(
        elapsed: Double?, reduced: Bool, delay: Double
    ) -> (incoming: Double, lift: Double, outgoing: Double) {
        guard let elapsed else { return (1, 0, 0) }
        if reduced {
            let out = quadInOut(max(0, min(1, elapsed / 0.12)))
            let into = quadInOut(max(0, min(1, elapsed / 0.18)))
            return (into, 0, 1 - out)
        }
        let out = ease(max(0, min(1, elapsed / 0.14)))
        let into = ease(max(0, min(1, (elapsed - (0.06 + delay)) / 0.26)))
        return (into, 10 * (1 - into), 1 - out)
    }

    /// The seconds after which a swap has fully settled (the longest delay plus 260 ms).
    static let swapDuration = 0.06 + 0.1 + 0.26

    /// `Easing.inOut(Easing.quad)`, Reanimated's default for timing animations.
    static func quadInOut(_ p: Double) -> Double {
        p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
    }
}
