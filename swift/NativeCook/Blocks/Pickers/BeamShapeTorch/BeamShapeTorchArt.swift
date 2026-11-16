import SwiftUI
import UIKit

/// Literal paths from bst-geometry.ts; the panel is the 0.6-smoothed Figma squircle.
enum BeamShapeTorchArt {
    static let wallpaper: UIImage? = {
        guard let url = Bundle.main.url(forResource: "BeamShapeTorch-wallpaper-pro", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        return image.preparingForDisplay() ?? image
    }()
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func squircle(_ rect: CGRect, _ radius: Double) -> Path {
        let budget = max(min(rect.width, rect.height) / 2, 0.01), r = min(radius, budget)
        let xi = (1.6 * r > budget) ? max(budget / max(r, 0.01) - 1, 0) : 0.6
        let p = (1 + xi) * r, arcMeasure = 90 * (1 - xi), arcLen = sin(arcMeasure / 2 * .pi / 180) * r * sqrt(2)
        let alpha = (90 - arcMeasure) / 2, p3p4 = r * tan(alpha / 2 * .pi / 180), beta = 45 * xi * .pi / 180
        let c = p3p4 * cos(beta), d = c * tan(beta), b = (p - arcLen - c - d) / 3, a = 2 * b
        let corners = [CGPoint(x: rect.maxX, y: rect.minY), CGPoint(x: rect.maxX, y: rect.maxY),
                       CGPoint(x: rect.minX, y: rect.maxY), CGPoint(x: rect.minX, y: rect.minY)]
        var path = Path()
        for i in 0..<4 {
            let rotation = Double(i) * .pi / 2, co = cos(rotation), si = sin(rotation), corner = corners[i]
            func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: corner.x + x * co - y * si, y: corner.y + x * si + y * co) }
            if i == 0 { path.move(to: pt(-p, 0)) } else { path.addLine(to: pt(-p, 0)) }
            path.addCurve(to: pt(-p + a + b + c, d), control1: pt(-p + a, 0), control2: pt(-p + a + b, 0))
            path.addArc(center: pt(-r, r), radius: r, startAngle: .radians(-.pi / 2 + beta + rotation),
                        endAngle: .radians(-beta + rotation), clockwise: false)
            let ex = -p + a + b + c + arcLen, ey = d + arcLen
            path.addCurve(to: pt(ex + d, ey + a + b + c), control1: pt(ex + d, ey + c), control2: pt(ex + d, ey + b + c))
        }
        path.closeSubpath()
        return path
    }
    static func arc(_ cx: Double, _ y: Double, _ hw: Double, _ sag: Double) -> Path {
        let g = BeamShapeTorchMotion.arcGeometry(cx, y, hw, sag)
        var p = Path()
        if g.straight { p.move(to: CGPoint(x: cx - hw, y: y)); p.addLine(to: CGPoint(x: cx + hw, y: y)) }
        else { p.addArc(center: CGPoint(x: cx, y: g.cy), radius: g.r, startAngle: .radians(-.pi / 2 - g.half), endAngle: .radians(-.pi / 2 + g.half), clockwise: false) }
        return p
    }
    static let sun: Path = {
        var p = Path(ellipseIn: CGRect(x: -3.8, y: -3.8, width: 7.6, height: 7.6))
        for i in 0..<8 {
            let a = Double(i) / 8 * .pi * 2, cx = cos(a), sy = sin(a)
            p.move(to: CGPoint(x: cx * 6.6, y: sy * 6.6)); p.addLine(to: CGPoint(x: cx * 7.6, y: sy * 7.6))
        }
        return p
    }()
    static let glyphCap: Path = {
        var p = Path(); p.move(to: CGPoint(x: 0, y: 8)); p.addLine(to: CGPoint(x: 0, y: 2.6))
        p.addQuadCurve(to: CGPoint(x: 2.6, y: 0), control: .zero); p.addLine(to: CGPoint(x: 25.4, y: 0))
        p.addQuadCurve(to: CGPoint(x: 28, y: 2.6), control: CGPoint(x: 28, y: 0)); p.addLine(to: CGPoint(x: 28, y: 8)); p.closeSubpath(); return p
    }()
    static let glyphBody: Path = {
        let l = 5.7, r = 22.3
        var p = Path(); p.move(to: CGPoint(x: 0.3, y: 11)); p.addLine(to: CGPoint(x: 27.7, y: 11)); p.addLine(to: CGPoint(x: 27.2, y: 21))
        p.addCurve(to: CGPoint(x: r, y: 32.5), control1: CGPoint(x: 26.8, y: 25.5), control2: CGPoint(x: r + 0.2, y: 28))
        p.addLine(to: CGPoint(x: r, y: 75.5)); p.addQuadCurve(to: CGPoint(x: r - 3.5, y: 79), control: CGPoint(x: r, y: 79))
        p.addLine(to: CGPoint(x: l + 3.5, y: 79)); p.addQuadCurve(to: CGPoint(x: l, y: 75.5), control: CGPoint(x: l, y: 79))
        p.addLine(to: CGPoint(x: l, y: 32.5)); p.addCurve(to: CGPoint(x: 0.8, y: 21), control1: CGPoint(x: l - 0.2, y: 28), control2: CGPoint(x: 1.2, y: 25.5))
        p.closeSubpath(); return p
    }()
}

struct BeamShapeTorchLayout {
    var width: Double, height: Double, scale: Double, top: Double
    var cap: CGRect, island: CGRect
    init(_ size: CGSize, _ insetTop: Double) {
        width = size.width; height = size.height; scale = size.width / 402
        top = insetTop >= 47 ? 11 : insetTop + 6
        let w = 240 * scale, h = min(430 * scale, height - top - 120 * scale)
        cap = CGRect(x: (width - w) / 2, y: top, width: w, height: h)
        island = CGRect(x: (width - 126) / 2, y: top, width: 126, height: 37)
    }
    var cx: Double { width / 2 }
    var lensY: Double { top + 276 * scale }
    func rect(_ k: BeamShapeTorchMotion.Look) -> CGRect {
        let h = BeamShapeTorchMotion.mix(37, cap.height, k.m) * k.press
        let w = BeamShapeTorchMotion.mix(126, cap.width, k.mw) * k.press
        return CGRect(x: cx - w / 2, y: top, width: w, height: h)
    }
    func innerScale(_ k: BeamShapeTorchMotion.Look) -> Double { BeamShapeTorchMotion.mix(0.55, 1, min(k.m, k.mw)) * k.press }
    func shape(_ k: BeamShapeTorchMotion.Look) -> Path {
        BeamShapeTorchArt.squircle(rect(k), BeamShapeTorchMotion.mix(18.5, 56 * scale, min(k.m, k.mw)))
    }
}
