import CoreGraphics
import Foundation
import SwiftUI

/// Progress away from the neutral value. Neutral is zero when it is in range,
/// otherwise the nearest range boundary.
public func getBipolarProgress(value: Double, minValue: Double, maxValue: Double) -> Double {
    guard value.isFinite, minValue.isFinite, maxValue.isFinite else { return 0 }

    let min = Swift.min(minValue, maxValue)
    let max = Swift.max(minValue, maxValue)
    let clampedValue = Swift.min(max, Swift.max(min, value))
    let neutral = Swift.min(max, Swift.max(min, 0))

    if clampedValue >= neutral {
        let positiveRange = max - neutral
        return positiveRange <= 0
            ? 0
            : Swift.min(Swift.max((clampedValue - neutral) / positiveRange, 0), 1)
    }
    let negativeRange = neutral - min
    return negativeRange <= 0
        ? 0
        : Swift.min(Swift.max((neutral - clampedValue) / negativeRange, 0), 1)
}

public func getSignedProgressColor<Paint>(
    value: Double,
    positiveColor: Paint,
    negativeColor: Paint
) -> Paint {
    value < 0 ? negativeColor : positiveColor
}

public enum ArcDirection: Int, Sendable {
    /// Positive values sweep clockwise from 12 o'clock.
    case clockwise = 1
    /// Negative values sweep counter-clockwise from 12 o'clock.
    case counterClockwise = -1
}

func polarPoint(centerX: Double, centerY: Double, radius: Double, angleDegrees: Double) -> CGPoint {
    let angleRadians = angleDegrees * Double.pi / 180
    return CGPoint(
        x: centerX + radius * cos(angleRadians),
        y: centerY + radius * sin(angleRadians)
    )
}

/// A continuous arc starting at 12 o'clock, the geometric equivalent of the
/// SVG path emitted by the React Native implementation.
public struct CircularArc: Equatable, Sendable {
    public var centerX: Double
    public var centerY: Double
    public var radius: Double
    public var startAngleDegrees: Double
    public var sweepDegrees: Double
    public var direction: ArcDirection

    public var endAngleDegrees: Double {
        startAngleDegrees + Double(direction.rawValue) * sweepDegrees
    }

    public var largeArc: Bool { sweepDegrees > 180 }

    public var startPoint: CGPoint {
        polarPoint(centerX: centerX, centerY: centerY, radius: radius, angleDegrees: startAngleDegrees)
    }

    public var endPoint: CGPoint {
        polarPoint(centerX: centerX, centerY: centerY, radius: radius, angleDegrees: endAngleDegrees)
    }

    /// SwiftUI path for the arc. SwiftUI uses a y-down coordinate system, so a
    /// visually clockwise sweep is `clockwise: false` for `Path.addArc`.
    public func path() -> Path {
        var path = Path()
        path.addArc(
            center: CGPoint(x: centerX, y: centerY),
            radius: radius,
            startAngle: .degrees(startAngleDegrees),
            endAngle: .degrees(endAngleDegrees),
            clockwise: direction == .counterClockwise
        )
        return path
    }
}

/// Builds a continuous arc from 12 o'clock.
/// Positive direction sweeps clockwise; negative sweeps counter-clockwise.
public func createCircularArc(
    centerX: Double,
    centerY: Double,
    radius: Double,
    progress: Double,
    direction: ArcDirection
) -> CircularArc? {
    guard centerX.isFinite, centerY.isFinite, radius.isFinite, progress.isFinite,
          radius > 0, progress > 0
    else { return nil }

    let clampedProgress = Swift.min(progress, 1)
    // A 360° arc whose endpoints coincide cannot be represented, so cap it.
    let sweepDegrees = Swift.min(clampedProgress * 360, 359.999)
    return CircularArc(
        centerX: centerX,
        centerY: centerY,
        radius: radius,
        startAngleDegrees: -90,
        sweepDegrees: sweepDegrees,
        direction: direction
    )
}

/// The exact SVG path string produced by the React Native implementation,
/// kept for parity and testing.
public func createCircularArcPathDescription(
    centerX: Double,
    centerY: Double,
    radius: Double,
    progress: Double,
    direction: ArcDirection
) -> String? {
    guard let arc = createCircularArc(
        centerX: centerX,
        centerY: centerY,
        radius: radius,
        progress: progress,
        direction: direction
    ) else { return nil }
    let start = arc.startPoint
    let end = arc.endPoint
    let largeArcFlag = arc.largeArc ? 1 : 0
    let sweepFlag = direction == .clockwise ? 1 : 0
    return [
        "M \(jsNumberString(start.x)) \(jsNumberString(start.y))",
        "A \(jsNumberString(radius)) \(jsNumberString(radius)) 0 \(largeArcFlag) \(sweepFlag) \(jsNumberString(end.x)) \(jsNumberString(end.y))",
    ].joined(separator: " ")
}
