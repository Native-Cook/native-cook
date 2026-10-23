import Foundation
import CoreGraphics

enum AuxeticFitLabSide { case left, right }
enum AuxeticFitLabDetent: Double { case closed = 4, middle = 23, open = 42 }

struct AuxeticFitLabHousing: Equatable {
    var offsetX: Double
    var offsetVelocityX: Double
    var scale: Double
    var scaleVelocity: Double
    static let rest = Self(offsetX: 0, offsetVelocityX: 0, scale: 1, scaleVelocity: 0)
}
struct AuxeticFitLabFrame: Equatable {
    var angle: Double
    var angularVelocity: Double
    var left: AuxeticFitLabHousing
    var right: AuxeticFitLabHousing
    var arrowGain: Double
    var arrowGainVelocity: Double
    var width: Double { AuxeticFitLabMath.width(at: angle) }
    var widthVelocity: Double { AuxeticFitLabMath.jacobian(angle) * angularVelocity }
}
struct AuxeticFitLabMetrics {
    let extensionPercent: Double
    let openAreaPercent: Double
}
struct AuxeticFitLabInput {
    let frame: AuxeticFitLabFrame
    let requestedWidth: Double
    let requestedWidthVelocity: Double
    let actualAngularVelocity: Double?
    // Derivatives with respect to requested width let a fresh release velocity
    // survive an unchanged Up callback without retaining gesture history here.
    fileprivate let rates: AuxeticFitLabInputRates
}
enum AuxeticFitLabReleaseVelocity {
    case inherited(frame: AuxeticFitLabFrame, heldElapsed: Double)
    case user(width: Double, angular: Double?, sampleAge: Double)
}
struct AuxeticFitLabRelease {
    let target: AuxeticFitLabDetent?
    let acceptedAngularVelocity: Double
    fileprivate let from: AuxeticFitLabFrame
    fileprivate let angle: AuxeticFitLabSpring
    fileprivate let leftOffset: AuxeticFitLabSpring
    fileprivate let rightOffset: AuxeticFitLabSpring
    fileprivate let leftScale: AuxeticFitLabSpring
    fileprivate let rightScale: AuxeticFitLabSpring
    fileprivate let arrows: AuxeticFitLabSpring
}
struct AuxeticFitLabHandles {
    let leftCenter: CGPoint
    let rightCenter: CGPoint
    let leftHit: CGRect
    let rightHit: CGRect
    let pinchHit: CGRect
}

fileprivate struct AuxeticFitLabInputRates {
    var leftOffset = 0.0
    var rightOffset = 0.0
    var leftScale = 0.0
    var rightScale = 0.0
    var arrows = 0.0
}

/// A release plan stores coefficients once. Sampling has no state or history.
fileprivate struct AuxeticFitLabSpring {
    let start: Double
    let target: Double
    let velocity: Double
    let critical: Bool
    private static let omega = 2 * Double.pi / 0.40
    private static let alpha = 0.8 * omega
    private static let beta = 0.6 * omega

    func raw(_ time: Double) -> (value: Double, velocity: Double) {
        let t = max(0, time), d = start - target
        if critical {
            let c = velocity + Self.omega * d
            let e = exp(-Self.omega * t)
            return (target + e * (d + c * t), e * (velocity - Self.omega * c * t))
        }
        let b = (velocity + Self.alpha * d) / Self.beta
        let e = exp(-Self.alpha * t), c = cos(Self.beta * t), s = sin(Self.beta * t)
        return (target + e * (d * c + b * s),
                e * (velocity * c + (-Self.alpha * b - Self.beta * d) * s))
    }

    func sample(_ time: Double) -> (value: Double, velocity: Double) {
        if time <= 0 { return (start, velocity) }
        if time >= 1.20 { return (target, 0) }
        let s = raw(time)
        if time <= 0.80 { return s }
        let u = (time - 0.80) / 0.40
        let envelope = 1 - u * u * (3 - 2 * u)
        let derivative = -6 * u * (1 - u) / 0.40
        return (target + envelope * (s.value - target),
                envelope * s.velocity + derivative * (s.value - target))
    }

    /// All stationary points of the analytic, untapered underdamped spring.
    /// The finite tail is a convex move toward its in-bounds target.
    func isFeasible(lower: Double, upper: Double) -> Bool {
        let d = start - target, b = (velocity + Self.alpha * d) / Self.beta
        let c = -Self.alpha * b - Self.beta * d
        let phase = atan2(-velocity, c)
        var times = [0.0, 1.20]
        for k in -2...8 {
            let t = (phase + Double(k) * .pi) / Self.beta
            if t > 0 && t < 1.20 { times.append(t) }
        }
        return times.allSatisfy {
            let value = raw($0).value
            return value >= lower - 1e-12 && value <= upper + 1e-12
        }
    }

    static func bounded(start: Double, target: Double, velocity: Double,
                        lower: Double, upper: Double) -> Self {
        let candidate = Self(start: start, target: target, velocity: velocity, critical: false)
        if candidate.isFeasible(lower: lower, upper: upper) { return candidate }
        var lo = 0.0, hi = 1.0
        for _ in 0..<40 {
            let mid = (lo + hi) / 2
            let spring = Self(start: start, target: target, velocity: velocity * mid, critical: false)
            if spring.isFeasible(lower: lower, upper: upper) { lo = mid } else { hi = mid }
        }
        return Self(start: start, target: target, velocity: velocity * lo, critical: false)
    }
}

enum AuxeticFitLabMath {
    static let loop = 8.0
    static let edge = 49.5
    private static let radians = Double.pi / 180
    static var rest: AuxeticFitLabFrame { staticFrame(angle: 4) }
    static var reduced: AuxeticFitLabFrame { staticFrame(angle: 42) }

    static func width(at angle: Double) -> Double {
        let a = angle * radians
        return 4 * edge * (cos(a) + sin(a))
    }
    static func jacobian(_ angle: Double) -> Double {
        let a = angle * radians
        return 4 * edge * (cos(a) - sin(a)) * radians
    }
    static func angle(forWidth value: Double) -> Double {
        let lower = width(at: 4), upper = width(at: 44)
        if value <= lower { return 4 }
        if value >= upper { return 44 }
        let w = value
        return (Double.pi / 4 - acos(clamp(w / (4 * edge * sqrt(2)), -1, 1))) / radians
    }
    static func metrics(at angle: Double) -> AuxeticFitLabMetrics {
        let d = width(at: angle) / 4, d0 = width(at: 4) / 4
        return .init(extensionPercent: 100 * (d / d0 - 1),
                     openAreaPercent: 100 * (1 - edge * edge / (d * d)))
    }
    static func handles(for frame: AuxeticFitLabFrame) -> AuxeticFitLabHandles {
        let d = frame.width / 4
        let left = CGPoint(x: 196.5 - (2 * d + 20) + frame.left.offsetX, y: 399)
        let right = CGPoint(x: 196.5 + (2 * d + 20) + frame.right.offsetX, y: 399)
        return .init(leftCenter: left, rightCenter: right,
                     leftHit: CGRect(x: clamp(left.x - 22, 14, 335), y: 369, width: 44, height: 60),
                     rightHit: CGRect(x: clamp(right.x - 22, 14, 335), y: 369, width: 44, height: 60),
                     pinchHit: CGRect(x: 196.5 - 2 * d - 12, y: 399 - 1.5 * d - 12,
                                      width: 4 * d + 24, height: 3 * d + 24))
    }
    static func staticFrame(angle: Double) -> AuxeticFitLabFrame {
        .init(angle: clamp(angle, 4, 42), angularVelocity: 0, left: .rest, right: .rest,
              arrowGain: 1, arrowGainVelocity: 0)
    }

    static func authored(_ time: Double) -> AuxeticFitLabFrame {
        let t = max(0, time).truncatingRemainder(dividingBy: loop)
        var frame = rest
        if t >= 1.24 && t < 3.90 {
            let q = curve((t - 1.24) / 2.66, 0.65, 0, 0.35, 1)
            frame.angle = 4 + 38 * q.value; frame.angularVelocity = 38 * q.velocity / 2.66
        } else if t >= 3.90 && t < 4.25 {
            let u = (t - 3.90) / 0.35, k = 2 * Double.pi * 0.82
            let peak = atan(1 / 0.82) / Double.pi
            let norm = pow(sin(.pi * peak), 2) * exp(-k * peak)
            let s = sin(.pi * u), e = exp(-k * u)
            frame.angle = 42 + s * s * e / norm
            frame.angularVelocity = e * (.pi * sin(2 * .pi * u) - k * s * s) / (norm * 0.35)
        } else if t >= 4.25 && t < 5.15 {
            frame.angle = 42
        } else if t >= 5.15 && t < 7.10 {
            let q = curve((t - 5.15) / 1.95, 0.65, 0, 0.35, 1)
            frame.angle = 42 - 38 * q.value; frame.angularVelocity = -38 * q.velocity / 1.95
        }
        var offset = 0.0, offsetVelocity = 0.0, scale = 1.0, scaleVelocity = 0.0
        if t >= 1.10 && t < 1.24 {
            let q = curve((t - 1.10) / 0.14, 0.2, 0.8, 0.2, 1)
            offset = 2 * q.value; offsetVelocity = 2 * q.velocity / 0.14
            scale = 1 - 0.06 * q.value; scaleVelocity = -0.06 * q.velocity / 0.14
        } else if t >= 1.24 && t < 1.46 {
            let q = smooth((t - 1.24) / 0.22)
            offset = 2 * (1 - q.value); offsetVelocity = -2 * q.velocity / 0.22
            scale = 0.94 + 0.06 * q.value; scaleVelocity = 0.06 * q.velocity / 0.22
        } else if t >= 7 && t < 7.10 {
            let q = smooth((t - 7) / 0.10)
            offset = q.value; offsetVelocity = q.velocity / 0.10
            scale = 1 - 0.02 * q.value; scaleVelocity = -0.02 * q.velocity / 0.10
        } else if t >= 7.10 && t < 7.28 {
            let q = curve((t - 7.10) / 0.18, 0.2, 0.8, 0.2, 1)
            offset = 1 - q.value; offsetVelocity = -q.velocity / 0.18
            scale = 0.98 + 0.02 * q.value; scaleVelocity = 0.02 * q.velocity / 0.18
        }
        frame.left = .init(offsetX: offset, offsetVelocityX: offsetVelocity, scale: scale, scaleVelocity: scaleVelocity)
        frame.right = .init(offsetX: -offset, offsetVelocityX: -offsetVelocity, scale: scale, scaleVelocity: scaleVelocity)
        let a = smooth((t - 1.24) / 0.22), b = smooth((t - 3.90) / 0.35)
        frame.arrowGain = 1 + 0.12 * a.value * (1 - b.value)
        frame.arrowGainVelocity = 0.12 * (a.velocity / 0.22 * (1 - b.value) - a.value * b.velocity / 0.35)
        return frame
    }

    static func begin(from frame: AuxeticFitLabFrame) -> AuxeticFitLabInput {
        var held = frame
        held.angularVelocity = 0; held.left.offsetVelocityX = 0; held.right.offsetVelocityX = 0
        held.left.scaleVelocity = 0; held.right.scaleVelocity = 0; held.arrowGainVelocity = 0
        return .init(frame: held, requestedWidth: frame.width, requestedWidthVelocity: 0,
                     actualAngularVelocity: nil, rates: .init())
    }
    static func drag(from capture: AuxeticFitLabFrame, side: AuxeticFitLabSide,
                     translationX: Double, velocityX: Double) -> AuxeticFitLabInput {
        let sign = side == .right ? 1.0 : -1.0
        return input(capture, requested: capture.width + sign * 2 * translationX,
                     velocity: sign * 2 * velocityX, leftRubber: side == .left, rightRubber: side == .right)
    }
    static func pinch(from capture: AuxeticFitLabFrame, scale: Double,
                      scaleVelocity: Double) -> AuxeticFitLabInput {
        input(capture, requested: capture.width * scale, velocity: capture.width * scaleVelocity,
              leftRubber: true, rightRubber: true)
    }

    private static func input(_ capture: AuxeticFitLabFrame, requested: Double, velocity: Double,
                              leftRubber: Bool, rightRubber: Bool) -> AuxeticFitLabInput {
        let lower = width(at: 4), upper = width(at: 44)
        let physical = clamp(requested, lower, upper)
        let angle = requested == capture.width ? capture.angle : angle(forWidth: physical)
        let widthIsMoving = requested >= lower && requested <= upper
            && !(requested == lower && velocity < 0) && !(requested == upper && velocity > 0)
        let physicalVelocity = widthIsMoving ? velocity : 0
        let delta = requested - capture.width, intent = abs(delta) / 2
        let q = smooth(intent / 12)
        let qWidth = q.velocity * sign(delta) / 24
        // At a bound, select the directional derivative of the physical clamp.
        let physicalWidthSlope = widthIsMoving ? 1.0 : 0.0
        let excess = (requested - physical) / 2
        let excessWidth = (1 - physicalWidthSlope) / 2
        var rates = AuxeticFitLabInputRates()
        func housing(_ from: AuxeticFitLabHousing, direction: Double, active: Bool) -> AuxeticFitLabHousing {
            let residual = from.offsetX * (1 - q.value)
            let residualWidth = -from.offsetX * qWidth
            let over = active ? direction * excess : 0
            let overWidth = active ? direction * excessWidth : 0
            let rubber = 0.45 * over / (1 + 0.45 * abs(over) / 16)
            let rubberWidth = 0.45 * overWidth / pow(1 + 0.45 * abs(over) / 16, 2)
            // A convex combination of residual and its directional 16pt limit.
            // Zero intent keeps the entire capture, including the inactive side.
            let room = 1 - abs(residual) / 16
            let roomWidth = -sign(residual) * residualWidth / 16
            let slope = residualWidth + rubberWidth * room + rubber * roomWidth
            let scaleSlope = -(from.scale - 1) * qWidth
            if direction < 0 { rates.leftOffset = slope; rates.leftScale = scaleSlope }
            else { rates.rightOffset = slope; rates.rightScale = scaleSlope }
            return .init(offsetX: residual + rubber * room, offsetVelocityX: slope * velocity,
                         scale: 1 + (from.scale - 1) * (1 - q.value), scaleVelocity: scaleSlope * velocity)
        }
        let left = housing(capture.left, direction: -1, active: leftRubber)
        let right = housing(capture.right, direction: 1, active: rightRubber)
        rates.arrows = (1.12 - capture.arrowGain) * qWidth
        let angular = physicalVelocity / jacobian(angle)
        let frame = AuxeticFitLabFrame(angle: angle, angularVelocity: angular, left: left, right: right,
                                       arrowGain: capture.arrowGain + (1.12 - capture.arrowGain) * q.value,
                                       arrowGainVelocity: rates.arrows * velocity)
        return .init(frame: frame, requestedWidth: requested, requestedWidthVelocity: velocity,
                     actualAngularVelocity: angular, rates: rates)
    }

    static func nearestDetent(angle: Double, velocity: Double,
                              previous: AuxeticFitLabDetent) -> AuxeticFitLabDetent {
        let detents: [AuxeticFitLabDetent] = [.closed, .middle, .open]
        let distance = detents.map { abs($0.rawValue - angle) }.min() ?? 0
        let ties = detents.filter { abs(abs($0.rawValue - angle) - distance) <= 1e-10 }
        if velocity > 0 { return ties.last ?? .closed }
        if velocity < 0 { return ties.first ?? .closed }
        return ties.contains(previous) ? previous : (ties.first ?? .closed)
    }
    static func makeRelease(from input: AuxeticFitLabInput,
                            velocity: AuxeticFitLabReleaseVelocity,
                            previous: AuxeticFitLabDetent, cancelled: Bool) -> AuxeticFitLabRelease {
        var from = input.frame
        var widthVelocity = 0.0, angularVelocity = 0.0
        switch velocity {
        case let .inherited(capture, elapsed):
            let a = 1 - smooth(elapsed / 0.12).value
            angularVelocity = capture.angularVelocity * a
            widthVelocity = jacobian(from.angle) * angularVelocity
            from.left.offsetVelocityX = capture.left.offsetVelocityX * a
            from.right.offsetVelocityX = capture.right.offsetVelocityX * a
            from.left.scaleVelocity = capture.left.scaleVelocity * a
            from.right.scaleVelocity = capture.right.scaleVelocity * a
            from.arrowGainVelocity = capture.arrowGainVelocity * a
        case let .user(width, angular, age):
            let a = 1 - smooth(age / 0.12).value
            widthVelocity = clamp(width * a, -600, 600)
            angularVelocity = (angular ?? width / jacobian(from.angle)) * a
            from.left.offsetVelocityX = input.rates.leftOffset * widthVelocity
            from.right.offsetVelocityX = input.rates.rightOffset * widthVelocity
            from.left.scaleVelocity = input.rates.leftScale * widthVelocity
            from.right.scaleVelocity = input.rates.rightScale * widthVelocity
            from.arrowGainVelocity = input.rates.arrows * widthVelocity
        }
        widthVelocity = clamp(widthVelocity, -600, 600)
        angularVelocity = clamp(angularVelocity, -50, 50)
        if (from.angle <= 4 && angularVelocity < 0) || (from.angle >= 44 && angularVelocity > 0) { angularVelocity = 0 }
        if cancelled {
            from.angularVelocity = 0
            return releasePlan(from: from, target: nil,
                               angle: .init(start: from.angle, target: from.angle, velocity: 0, critical: true))
        }
        let predicted = angle(forWidth: from.width + 0.12 * widthVelocity)
        let target = nearestDetent(angle: predicted, velocity: widthVelocity, previous: previous)
        let spring: AuxeticFitLabSpring
        if target == .closed {
            let omega = 2 * Double.pi / 0.40
            let v = clamp(angularVelocity, max(-50, -omega * (from.angle - 4)),
                          min(50, omega * (44 - from.angle)))
            spring = .init(start: from.angle, target: 4, velocity: v, critical: true)
        } else {
            spring = .bounded(start: from.angle, target: target.rawValue, velocity: angularVelocity, lower: 4, upper: 44)
        }
        from.angularVelocity = spring.velocity
        return releasePlan(from: from, target: target, angle: spring)
    }

    private static func releasePlan(from: AuxeticFitLabFrame, target: AuxeticFitLabDetent?,
                                    angle: AuxeticFitLabSpring) -> AuxeticFitLabRelease {
        let leftOffset = AuxeticFitLabSpring.bounded(start: from.left.offsetX, target: 0,
            velocity: from.left.offsetVelocityX, lower: -16, upper: 16)
        let rightOffset = AuxeticFitLabSpring.bounded(start: from.right.offsetX, target: 0,
            velocity: from.right.offsetVelocityX, lower: -16, upper: 16)
        let leftScale = AuxeticFitLabSpring(start: from.left.scale, target: 1, velocity: from.left.scaleVelocity, critical: false)
        let rightScale = AuxeticFitLabSpring(start: from.right.scale, target: 1, velocity: from.right.scaleVelocity, critical: false)
        // Gain approaches its resting lower boundary without crossing it.
        let omega = 2 * Double.pi / 0.40
        let gainVelocity = clamp(from.arrowGainVelocity, -omega * (from.arrowGain - 1),
                                 omega * (1.12 - from.arrowGain))
        let arrows = AuxeticFitLabSpring(start: from.arrowGain, target: 1, velocity: gainVelocity, critical: true)
        return .init(target: target, acceptedAngularVelocity: angle.velocity, from: from, angle: angle,
                     leftOffset: leftOffset, rightOffset: rightOffset, leftScale: leftScale,
                     rightScale: rightScale, arrows: arrows)
    }
    static func released(_ release: AuxeticFitLabRelease, elapsed: Double) -> AuxeticFitLabFrame {
        if release.target == nil {
            let q = smooth(elapsed / 0.22)
            var f = release.from
            f.angularVelocity = 0
            f.left = relaxed(f.left, q: q.value, rate: q.velocity / 0.22)
            f.right = relaxed(f.right, q: q.value, rate: q.velocity / 0.22)
            f.arrowGainVelocity = (1 - f.arrowGain) * q.velocity / 0.22
            f.arrowGain += (1 - f.arrowGain) * q.value
            return f
        }
        let a = release.angle.sample(elapsed), lo = release.leftOffset.sample(elapsed)
        let ro = release.rightOffset.sample(elapsed), ls = release.leftScale.sample(elapsed)
        let rs = release.rightScale.sample(elapsed), gain = release.arrows.sample(elapsed)
        return .init(angle: a.value, angularVelocity: a.velocity,
                     left: .init(offsetX: lo.value, offsetVelocityX: lo.velocity, scale: ls.value, scaleVelocity: ls.velocity),
                     right: .init(offsetX: ro.value, offsetVelocityX: ro.velocity, scale: rs.value, scaleVelocity: rs.velocity),
                     arrowGain: gain.value, arrowGainVelocity: gain.velocity)
    }
    static func recovering(from frame: AuxeticFitLabFrame, elapsed: Double) -> AuxeticFitLabFrame {
        if elapsed <= 0 { return frame }
        if elapsed >= 0.60 { return rest }
        let q = curve(elapsed / 0.60, 0.65, 0, 0.35, 1), rate = q.velocity / 0.60
        return .init(angle: frame.angle + (4 - frame.angle) * q.value,
                     angularVelocity: (4 - frame.angle) * rate,
                     left: relaxed(frame.left, q: q.value, rate: rate),
                     right: relaxed(frame.right, q: q.value, rate: rate),
                     arrowGain: frame.arrowGain + (1 - frame.arrowGain) * q.value,
                     arrowGainVelocity: (1 - frame.arrowGain) * rate)
    }
    private static func relaxed(_ from: AuxeticFitLabHousing, q: Double, rate: Double) -> AuxeticFitLabHousing {
        .init(offsetX: from.offsetX * (1 - q), offsetVelocityX: -from.offsetX * rate,
              scale: from.scale + (1 - from.scale) * q, scaleVelocity: (1 - from.scale) * rate)
    }
    static func clamp(_ value: Double, _ lower: Double, _ upper: Double) -> Double { min(upper, max(lower, value)) }
    private static func sign(_ value: Double) -> Double { value == 0 ? 0 : (value > 0 ? 1 : -1) }
    private static func smooth(_ value: Double) -> (value: Double, velocity: Double) {
        let u = clamp(value, 0, 1)
        return (u * u * (3 - 2 * u), value <= 0 || value >= 1 ? 0 : 6 * u * (1 - u))
    }
    /// Solve x once; return y and dy/dx from that same Bézier parameter.
    private static func curve(_ value: Double, _ x1: Double, _ y1: Double,
                              _ x2: Double, _ y2: Double) -> (value: Double, velocity: Double) {
        let x = clamp(value, 0, 1)
        func coordinate(_ u: Double, _ a: Double, _ b: Double) -> Double {
            let v = 1 - u
            return 3 * v * v * u * a + 3 * v * u * u * b + u * u * u
        }
        func derivative(_ u: Double, _ a: Double, _ b: Double) -> Double {
            3 * (1 - u) * (1 - u) * a + 6 * (1 - u) * u * (b - a) + 3 * u * u * (1 - b)
        }
        if x == 0 { return (0, y1 / x1) }
        if x == 1 { return (1, (1 - y2) / (1 - x2)) }
        var lo = 0.0, hi = 1.0
        for _ in 0..<30 {
            let mid = (lo + hi) / 2
            if coordinate(mid, x1, x2) < x { lo = mid } else { hi = mid }
        }
        let u = (lo + hi) / 2
        return (coordinate(u, y1, y2), derivative(u, y1, y2) / derivative(u, x1, x2))
    }
}
