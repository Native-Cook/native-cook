import Foundation

/// JavaScript `Math.round` semantics: halves round toward positive infinity
/// (`Math.round(-2.5) === -2`, `Math.round(2.5) === 3`). Swift's
/// `rounded()` rounds halves away from zero, which differs for negative
/// dial values, so every port of `Math.round` goes through this helper.
@inlinable
func jsRound(_ value: Double) -> Double {
    guard value.isFinite else { return value }
    return (value + 0.5).rounded(.down)
}

/// Reproduces JavaScript `Number.prototype.toString()` for the finite values
/// this library formats: integers print without a fraction, other values use
/// the shortest round-trip representation without an exponent.
func jsNumberString(_ value: Double) -> String {
    if value.isNaN { return "NaN" }
    if value.isInfinite { return value < 0 ? "-Infinity" : "Infinity" }
    if value == value.rounded(.towardZero), abs(value) < 1e15 {
        return String(Int64(value))
    }
    let shortest = "\(value)"
    if !shortest.contains("e") { return shortest }
    var expanded = String(format: "%.20f", value)
    while expanded.hasSuffix("0") { expanded.removeLast() }
    if expanded.hasSuffix(".") { expanded.removeLast() }
    return expanded
}

/// JavaScript `Number.prototype.toFixed(digits)`.
func jsToFixed(_ value: Double, _ digits: Int) -> String {
    String(format: "%.\(Swift.max(0, digits))f", value)
}
