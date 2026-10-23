import SwiftUI

/// A number that is set directly, eased over a duration, or carried by a spring, sampled against the
/// timeline's date. The spring is the React Native version's: mass 1, stiffness 195, damping 23.74.
struct SwipeDownDeckValue {
    private enum Kind { case still, tween, spring }

    static let stiffness = 195.0
    static let damping = 23.74
    /// The spring is within a millionth of its distance after this long.
    static let settle = 1.2

    private var kind = Kind.still
    private var from: Double
    private(set) var target: Double
    private var velocity0 = 0.0
    private var start = Date.distantPast
    private var duration = 0.0
    private var curve: (Double) -> Double = { $0 }

    init(_ value: Double) {
        from = value
        target = value
    }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        switch kind {
        case .still:
            return target
        case .tween:
            guard duration > 0, t < duration else { return target }
            return from + (target - from) * curve(max(0, t) / duration)
        case .spring:
            guard t < Self.settle else { return target }
            let t = max(0, t)
            let w0 = Self.stiffness.squareRoot()
            let zeta = Self.damping / (2 * w0)
            let wd = w0 * (1 - zeta * zeta).squareRoot()
            let a = from - target
            let b = (velocity0 + zeta * w0 * a) / wd
            return target + exp(-zeta * w0 * t) * (a * cos(wd * t) + b * sin(wd * t))
        }
    }

    func velocity(at date: Date) -> Double {
        guard kind != .still else { return 0 }
        let h = 0.004
        return (value(at: date + h) - value(at: date - h)) / (2 * h)
    }

    /// When the value stops moving.
    var end: Date {
        switch kind {
        case .still: .distantPast
        case .tween: start + duration
        case .spring: start + Self.settle
        }
    }

    mutating func set(_ value: Double) {
        kind = .still
        from = value
        target = value
    }

    mutating func tween(to value: Double, duration: Double, at date: Date, curve: @escaping (Double) -> Double) {
        from = self.value(at: date)
        target = value
        start = date
        self.duration = duration
        self.curve = curve
        kind = .tween
    }

    /// Springs from where the value is now, keeping its speed unless another one is given.
    mutating func spring(to value: Double, at date: Date, velocity: Double? = nil, still: Bool = false) {
        guard !still else { return set(value) }
        let v = velocity ?? self.velocity(at: date)
        from = self.value(at: date)
        velocity0 = v
        target = value
        start = date
        kind = .spring
    }
}

enum SwipeDownDeckEase {
    static let fly = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.4, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))
    static let drag = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.25, y: 1))

    static func quadInOut(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }
}
