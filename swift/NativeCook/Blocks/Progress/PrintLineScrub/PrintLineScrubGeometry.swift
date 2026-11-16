import SwiftUI

/// pls-theme.makeLayout + pls-uniforms.makeGeo, in screen points throughout.
struct PrintLineScrubGeometry {
    let width: Double, height: Double, top: Double, bottom: Double
    let scale: Double, ox: Double, oy: Double
    static let camCos = cos(20 * Double.pi / 180)
    static let camSin = sin(20 * Double.pi / 180)

    init(size: CGSize, top: Double, bottom: Double) {
        width = size.width; height = size.height; self.top = top; self.bottom = bottom
        let available = size.height - top - bottom
        scale = min(size.width / 402, available / (874 - 62 - 34))
        ox = (size.width - 402 * scale) / 2
        oy = (available - (874 - 62 - 34) * scale) / 2
    }

    func x(_ value: Double) -> Double { ox + value * scale }
    func y(_ value: Double) -> Double { top + oy + (value - 62) * scale }
    func yt(_ value: Double) -> Double { top + (value - 62) * scale }
    func yb(_ value: Double) -> Double { height - bottom - (874 - 34 - value) * scale }
    var axisX: Double { x(201) }
    var baseY: Double { y(690) }
    var perY: Double { 500 * scale }
    var unit: Double { perY / Self.camCos }
    var capLeft: Double { x(201 - 340 / 2) }
    var capWidth: Double { 340 * scale }
    var capHeight: Double { 104 * scale }
    var capRadius: Double { 52 * scale }
    var headerRect: CGRect { CGRect(x: x(16), y: yt(62), width: 370 * scale, height: 56 * scale) }
    // The RN dock uses its contents' 160 pt width, rather than ref.dock.w (370).
    var dockRect: CGRect { CGRect(x: x(16), y: yb(780), width: 160 * scale, height: 56 * scale) }
    var figureRect: CGRect { CGRect(x: axisX - 200 * scale, y: baseY + (180 - 690) * scale, width: 400 * scale, height: 600 * scale) }
    func planeY(_ h: Double) -> Double { baseY - perY * h }
    func capsuleY(_ h: Double, _ duck: Double) -> Double {
        let lo = yt(118) + 12 * scale + capHeight / 2
        let hi = yb(780) - 12 * scale - capHeight / 2
        return min(hi, max(lo, planeY(h) + duck * scale))
    }
    func capsuleRect(_ look: PrintLineScrubMotion.Look, scaled: Bool = true) -> CGRect {
        let k = scaled ? 1 + 0.02 * look.press : 1
        return CGRect(x: capLeft + (capWidth - capWidth * k) / 2,
                      y: capsuleY(look.h, look.duck) - capHeight * k / 2,
                      width: capWidth * k, height: capHeight * k)
    }
    func pillX(_ h: Double) -> Double {
        let radius = 22 * scale
        return radius + (300 * scale - 44 * scale) * PrintLineScrubMotion.clamp01(PrintLineScrubMotion.percentAt(h) / 100)
    }
}
