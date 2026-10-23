import CoreGraphics
import Foundation
import SwiftUI

public struct CameraZoomRange: Equatable, Sendable {
    public var min: Double
    public var max: Double

    public init(min: Double, max: Double) {
        self.min = min
        self.max = max
    }
}

/// Six-value affine matrix `[a, b, c, d, tx, ty]`.
public typealias CameraZoomRotationMatrix = [Double]

public enum CameraZoomDefaults {
    public static let min: Double = 0.5
    public static let max: Double = 10
    public static let step: Double = 0.1
    public static let degreesPerOctave: Double = 20
    public static let maxTickCount: Double = 240
}

private let maxZoomFactor: Double = 1_000
private let floatPrecision: Double = 1_000_000

private func clampFinitePositive(_ value: Double, fallback: Double) -> Double {
    let resolved = value.isFinite && value > 0 ? value : fallback
    let clamped = Swift.min(maxZoomFactor, Swift.max(1 / floatPrecision, resolved))
    return jsRound(clamped * floatPrecision) / floatPrecision
}

public func normalizeCameraZoomRange(
    minZoom: Double = CameraZoomDefaults.min,
    maxZoom: Double = CameraZoomDefaults.max
) -> CameraZoomRange {
    let first = clampFinitePositive(minZoom, fallback: CameraZoomDefaults.min)
    let second = clampFinitePositive(maxZoom, fallback: CameraZoomDefaults.max)
    let min = Swift.min(first, second)
    let max = Swift.max(first, second)

    if min == max {
        return CameraZoomRange(min: CameraZoomDefaults.min, max: CameraZoomDefaults.max)
    }
    return CameraZoomRange(min: min, max: max)
}

public func getSafeCameraZoomStep(_ step: Double) -> Double {
    clampFinitePositive(step, fallback: CameraZoomDefaults.step)
}

private func getCameraZoomBounds(minZoom: Double, maxZoom: Double) -> CameraZoomRange {
    let first = clampFinitePositive(minZoom, fallback: CameraZoomDefaults.min)
    let second = clampFinitePositive(maxZoom, fallback: CameraZoomDefaults.max)
    return CameraZoomRange(min: Swift.min(first, second), max: Swift.max(first, second))
}

public func clampCameraZoom(_ zoom: Double, minZoom: Double, maxZoom: Double) -> Double {
    let bounds = getCameraZoomBounds(minZoom: minZoom, maxZoom: maxZoom)
    let safeZoom = zoom.isFinite && zoom > 0 ? zoom : bounds.min
    return Swift.min(bounds.max, Swift.max(bounds.min, safeZoom))
}

public func roundCameraZoom(_ zoom: Double, step: Double, minZoom: Double, maxZoom: Double) -> Double {
    let safeStep = getSafeCameraZoomStep(step)
    let bounds = getCameraZoomBounds(minZoom: minZoom, maxZoom: maxZoom)
    let clamped = clampCameraZoom(zoom, minZoom: minZoom, maxZoom: maxZoom)
    let rounded = clamped == bounds.min || clamped == bounds.max
        ? clamped
        : jsRound(clamped / safeStep) * safeStep
    return jsRound(clampCameraZoom(rounded, minZoom: minZoom, maxZoom: maxZoom) * floatPrecision)
        / floatPrecision
}

public func cameraZoomToLogPosition(_ zoom: Double) -> Double {
    let safeZoom = zoom.isFinite && zoom > 0 ? zoom : CameraZoomDefaults.min
    return log2(safeZoom)
}

public func cameraLogPositionToZoom(_ position: Double) -> Double {
    pow(2, position.isFinite ? position : 0)
}

/// The reference video spaces each doubling (0.5→1→2) by approximately 20°.
public func cameraZoomToAngle(
    _ zoom: Double,
    selectedZoom: Double,
    degreesPerOctave: Double = CameraZoomDefaults.degreesPerOctave
) -> Double {
    let safeDegrees = degreesPerOctave.isFinite && degreesPerOctave > 0
        ? degreesPerOctave
        : CameraZoomDefaults.degreesPerOctave
    return (cameraZoomToLogPosition(zoom) - cameraZoomToLogPosition(selectedZoom)) * safeDegrees
}

/// Builds the affine matrix for a rotation around a fixed point.
public func cameraZoomRotationMatrix(
    angleDegrees: Double,
    centerX: Double,
    centerY: Double? = nil
) -> CameraZoomRotationMatrix {
    let safeAngle = angleDegrees.isFinite ? angleDegrees : 0
    let safeCenterX = centerX.isFinite ? centerX : 0
    let resolvedCenterY = centerY ?? centerX
    let safeCenterY = resolvedCenterY.isFinite ? resolvedCenterY : 0
    let radians = safeAngle * Double.pi / 180
    let cosine = cos(radians)
    let sine = sin(radians)

    return [
        cosine,
        sine,
        -sine,
        cosine,
        safeCenterX - safeCenterX * cosine + safeCenterY * sine,
        safeCenterY - safeCenterX * sine - safeCenterY * cosine,
    ]
}

/// Returns one half of an isosceles triangle's base for its tip angle.
public func getCameraZoomTriangleHalfWidth(height: Double, apexAngleDegrees: Double) -> Double {
    let safeHeight = height.isFinite ? Swift.max(0, height) : 0
    let safeAngle = apexAngleDegrees.isFinite && apexAngleDegrees > 0 && apexAngleDegrees < 180
        ? apexAngleDegrees
        : 90
    return safeHeight * tan(safeAngle * Double.pi / 360)
}

/// Geometry of a closed triangle with a quadratic curve at every corner.
public struct CameraZoomRoundedTriangle: Equatable {
    public var leftX: Double
    public var rightX: Double
    public var centerX: Double
    public var topY: Double
    public var apexY: Double
    /// Zero when the triangle is sharp.
    public var cornerRadius: Double
    public var baseOffset: Double
    public var baseSideX: Double
    public var baseSideY: Double
    public var apexSideX: Double
    public var apexSideY: Double

    public var isSharp: Bool { cornerRadius == 0 }

    /// SwiftUI path equivalent of the SVG path emitted by React Native.
    public func path() -> Path {
        var path = Path()
        if isSharp {
            path.move(to: CGPoint(x: leftX, y: topY))
            path.addLine(to: CGPoint(x: rightX, y: topY))
            path.addLine(to: CGPoint(x: centerX, y: apexY))
            path.closeSubpath()
            return path
        }
        path.move(to: CGPoint(x: leftX + baseOffset, y: topY))
        path.addLine(to: CGPoint(x: rightX - baseOffset, y: topY))
        path.addQuadCurve(
            to: CGPoint(x: rightX - baseSideX, y: topY + baseSideY),
            control: CGPoint(x: rightX, y: topY)
        )
        path.addLine(to: CGPoint(x: centerX + apexSideX, y: apexY - apexSideY))
        path.addQuadCurve(
            to: CGPoint(x: centerX - apexSideX, y: apexY - apexSideY),
            control: CGPoint(x: centerX, y: apexY)
        )
        path.addLine(to: CGPoint(x: leftX + baseSideX, y: topY + baseSideY))
        path.addQuadCurve(
            to: CGPoint(x: leftX + baseOffset, y: topY),
            control: CGPoint(x: leftX, y: topY)
        )
        path.closeSubpath()
        return path
    }

    /// The SVG path string produced by the React Native implementation.
    public var svgDescription: String {
        let n = jsNumberString
        if isSharp {
            return "M \(n(leftX)) \(n(topY)) L \(n(rightX)) \(n(topY)) L \(n(centerX)) \(n(apexY)) Z"
        }
        return [
            "M \(n(leftX + baseOffset)) \(n(topY))",
            "L \(n(rightX - baseOffset)) \(n(topY))",
            "Q \(n(rightX)) \(n(topY)) \(n(rightX - baseSideX)) \(n(topY + baseSideY))",
            "L \(n(centerX + apexSideX)) \(n(apexY - apexSideY))",
            "Q \(n(centerX)) \(n(apexY)) \(n(centerX - apexSideX)) \(n(apexY - apexSideY))",
            "L \(n(leftX + baseSideX)) \(n(topY + baseSideY))",
            "Q \(n(leftX)) \(n(topY)) \(n(leftX + baseOffset)) \(n(topY))",
            "Z",
        ].joined(separator: " ")
    }
}

/// Builds a closed triangle with a quadratic curve at every corner.
public func getCameraZoomRoundedTriangle(
    centerX: Double,
    topY: Double,
    halfWidth: Double,
    apexY: Double,
    cornerRadius: Double
) -> CameraZoomRoundedTriangle {
    let safeCenterX = centerX.isFinite ? centerX : 0
    let safeTopY = topY.isFinite ? topY : 0
    let safeHalfWidth = halfWidth.isFinite ? Swift.max(0, halfWidth) : 0
    let safeApexY = apexY.isFinite ? Swift.max(safeTopY, apexY) : safeTopY
    let height = safeApexY - safeTopY
    let leftX = safeCenterX - safeHalfWidth
    let rightX = safeCenterX + safeHalfWidth
    let sharp = CameraZoomRoundedTriangle(
        leftX: leftX,
        rightX: rightX,
        centerX: safeCenterX,
        topY: safeTopY,
        apexY: safeApexY,
        cornerRadius: 0,
        baseOffset: 0,
        baseSideX: 0,
        baseSideY: 0,
        apexSideX: 0,
        apexSideY: 0
    )
    let requestedRadius = cornerRadius.isFinite ? Swift.max(0, cornerRadius) : 0

    if requestedRadius == 0 || safeHalfWidth == 0 || height == 0 {
        return sharp
    }

    let sideLength = hypot(safeHalfWidth, height)
    let baseAngle = atan2(height, safeHalfWidth)
    let apexAngle = Double.pi - 2 * baseAngle
    let baseTangent = tan(baseAngle / 2)
    let apexTangent = tan(apexAngle / 2)

    if baseTangent <= 0 || apexTangent <= 0 { return sharp }

    let maxBaseRadius = safeHalfWidth * baseTangent
    let maxSideRadius = sideLength / (1 / baseTangent + 1 / apexTangent)
    let radius = Swift.min(requestedRadius, maxBaseRadius, maxSideRadius)
    let baseOffset = radius / baseTangent
    let apexOffset = radius / apexTangent
    let sideUnitX = safeHalfWidth / sideLength
    let sideUnitY = height / sideLength

    return CameraZoomRoundedTriangle(
        leftX: leftX,
        rightX: rightX,
        centerX: safeCenterX,
        topY: safeTopY,
        apexY: safeApexY,
        cornerRadius: radius,
        baseOffset: baseOffset,
        baseSideX: sideUnitX * baseOffset,
        baseSideY: sideUnitY * baseOffset,
        apexSideX: sideUnitX * apexOffset,
        apexSideY: sideUnitY * apexOffset
    )
}

/// SVG-string form of `getCameraZoomRoundedTriangle`, kept for parity tests.
public func getCameraZoomRoundedTrianglePath(
    centerX: Double,
    topY: Double,
    halfWidth: Double,
    apexY: Double,
    cornerRadius: Double
) -> String {
    getCameraZoomRoundedTriangle(
        centerX: centerX,
        topY: topY,
        halfWidth: halfWidth,
        apexY: apexY,
        cornerRadius: cornerRadius
    ).svgDescription
}

/// Maps a horizontal drag to the tangential rotation of the circular scale.
public func cameraZoomFromTranslation(
    startZoom: Double,
    translationX: Double,
    radius: Double,
    minZoom: Double,
    maxZoom: Double,
    degreesPerOctave: Double = CameraZoomDefaults.degreesPerOctave
) -> Double {
    let safeRadius = radius.isFinite && radius > 0 ? radius : 1
    let safeDegrees = degreesPerOctave.isFinite && degreesPerOctave > 0
        ? degreesPerOctave
        : CameraZoomDefaults.degreesPerOctave
    let pointsPerOctave = safeRadius * (safeDegrees * Double.pi / 180)
    let octaveDelta = -(translationX.isFinite ? translationX : 0) / Swift.max(1, pointsPerOctave)
    return clampCameraZoom(startZoom * pow(2, octaveDelta), minZoom: minZoom, maxZoom: maxZoom)
}

public func buildCameraZoomTicks(
    minZoom: Double,
    maxZoom: Double,
    step: Double,
    maxTickCount: Double = CameraZoomDefaults.maxTickCount
) -> [Double] {
    let range = normalizeCameraZoomRange(minZoom: minZoom, maxZoom: maxZoom)
    let min = range.min
    let max = range.max
    let baseStep = getSafeCameraZoomStep(step)
    let safeMaxCount = Swift.max(2, maxTickCount.rounded(.down))
    let estimatedCount = ((max - min) / baseStep).rounded(.down) + 1
    let tickStep = baseStep * Swift.max(1, (estimatedCount / safeMaxCount).rounded(.up))
    let firstAligned = (min / tickStep).rounded(.up) * tickStep
    let epsilon = tickStep / floatPrecision
    var ticks = [min]

    var value = firstAligned
    while value < max - epsilon {
        let rounded = jsRound(value * floatPrecision) / floatPrecision
        if rounded > min + epsilon { ticks.append(rounded) }
        if Double(ticks.count) >= safeMaxCount - 1 { break }
        value += tickStep
    }

    if abs(ticks[ticks.count - 1] - max) > epsilon { ticks.append(max) }
    return ticks
}

public func isCameraZoomMajorTick<Stop: CameraZoomStopValue>(
    _ zoom: Double,
    stops: [Stop],
    step: Double,
    minZoom: Double,
    maxZoom: Double
) -> Bool {
    let tolerance = Swift.max(getSafeCameraZoomStep(step) / 100, 1e-6)
    return abs(zoom - minZoom) <= tolerance
        || abs(zoom - maxZoom) <= tolerance
        || abs(zoom - jsRound(zoom)) <= tolerance
        || stops.contains { abs($0.value - zoom) <= tolerance }
}

public func normalizeCameraZoomStops<Stop: CameraZoomStopValue>(
    _ stops: [Stop],
    minZoom: Double,
    maxZoom: Double,
    step: Double? = nil
) -> [Stop] {
    let range = normalizeCameraZoomRange(minZoom: minZoom, maxZoom: maxZoom)
    var seen = Set<Int64>()
    return stops
        .filter { $0.value.isFinite && $0.value >= range.min && $0.value <= range.max }
        .map { stop -> Stop in
            guard let step else { return stop }
            let value = roundCameraZoom(stop.value, step: step, minZoom: range.min, maxZoom: range.max)
            if value == stop.value { return stop }
            var copy = stop
            copy.value = value
            return copy
        }
        .filter { stop in
            let key = Int64(jsRound(stop.value * floatPrecision))
            if seen.contains(key) { return false }
            seen.insert(key)
            return true
        }
        .sorted { $0.value < $1.value }
}

public func findCameraZoomStop<Stop: CameraZoomStopValue>(
    _ zoom: Double,
    stops: [Stop],
    step: Double
) -> Stop? {
    let tolerance = Swift.max(getSafeCameraZoomStep(step) / 2, 1e-6)
    return stops.first { abs($0.value - zoom) <= tolerance }
}

public func findNearestCameraZoomStop<Stop: CameraZoomStopValue>(
    _ zoom: Double,
    stops: [Stop]
) -> Stop? {
    let validStops = stops.filter { $0.value.isFinite && $0.value > 0 }
    guard var nearest = validStops.first else { return nil }

    let zoomPosition = cameraZoomToLogPosition(zoom)
    for candidate in validStops.dropFirst() {
        let nearestDistance = abs(cameraZoomToLogPosition(nearest.value) - zoomPosition)
        let candidateDistance = abs(cameraZoomToLogPosition(candidate.value) - zoomPosition)
        if candidateDistance < nearestDistance { nearest = candidate }
    }
    return nearest
}

private func getCameraZoomPrecision(_ value: Double) -> Int {
    var source = jsToFixed(abs(value), 6)
    while source.hasSuffix("0") { source.removeLast() }
    guard let decimalIndex = source.firstIndex(of: ".") else { return 0 }
    return source.distance(from: decimalIndex, to: source.endIndex) - 1
}

public func formatCameraZoomNumber(_ zoom: Double, step: Double) -> String {
    let precision = Swift.max(
        getCameraZoomPrecision(getSafeCameraZoomStep(step)),
        getCameraZoomPrecision(zoom)
    )
    let fixed = Double(jsToFixed(zoom, precision)) ?? zoom
    return jsNumberString(fixed)
}

public func formatCameraZoomCompactNumber(_ zoom: Double, step: Double) -> String {
    let formatted = formatCameraZoomNumber(zoom, step: step)
    return formatted.hasPrefix("0.") ? String(formatted.dropFirst()) : formatted
}

public func formatCameraZoomValue(_ zoom: Double, step: Double) -> String {
    "\(formatCameraZoomNumber(zoom, step: step))x"
}
