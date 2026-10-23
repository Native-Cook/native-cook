import SwiftUI

let tau = Double.pi * 2

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: opacity
        )
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }
}

enum Ease {
    /// Strong ease-out used for every state change.
    static let out = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.23, y: 1),
        endControlPoint: UnitPoint(x: 0.32, y: 1)
    )
    /// Starts at 1.25× average speed and lands with zero velocity: paired with a 1.25× duration it
    /// picks up a linear loop without a speed jump.
    static let settle = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.4, y: 0.5),
        endControlPoint: UnitPoint(x: 0.6, y: 1)
    )
}

/// A number animating towards a target, sampled against the `TimelineView` clock so it can be
/// read inside a `Canvas` (which SwiftUI's implicit animations can't reach).
struct Tween {
    private var from: Double
    private var to: Double
    private var start = Date.distantPast
    private(set) var duration = 0.0
    private var curve = UnitCurve.linear

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        guard duration > 0 else { return to }
        let t = min(max(date.timeIntervalSince(start) / duration, 0), 1)
        return from + (to - from) * curve.value(at: t)
    }

    mutating func retarget(_ target: Double, at date: Date = .now, duration: Double, curve: UnitCurve) {
        from = value(at: date)
        to = target
        start = date
        self.duration = duration
        self.curve = curve
    }
}

func capsule(_ rect: CGRect) -> Path {
    Capsule(style: .circular).path(in: rect)
}

let unitCircle = Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2))

extension GraphicsContext {
    /// A copy of the context scaled about `anchor`.
    func scaled(_ scale: Double, about anchor: CGPoint) -> GraphicsContext {
        var copy = self
        copy.translateBy(x: anchor.x, y: anchor.y)
        copy.scaleBy(x: scale, y: scale)
        copy.translateBy(x: -anchor.x, y: -anchor.y)
        return copy
    }

    /// Fills an ellipse with a radial gradient that follows the ellipse's proportions.
    func fillEllipse(center: CGPoint, radii: CGSize, gradient: Gradient) {
        var copy = self
        copy.translateBy(x: center.x, y: center.y)
        copy.scaleBy(x: radii.width, y: radii.height)
        copy.fill(unitCircle, with: .radialGradient(gradient, center: .zero, startRadius: 0, endRadius: 1))
    }
}
