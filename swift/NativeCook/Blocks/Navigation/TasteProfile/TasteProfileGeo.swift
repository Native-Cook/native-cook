import SwiftUI
import QuartzCore

/// Screen measures: ref px (the pin's 1206-wide pixels) to points, and the offset of the pin's y axis.
struct TasteProfileGeo: Equatable {
    var width: Double
    var height: Double
    var scale: Double
    var pointScale: Double
    var y0: Double
    var pixel: Double

    func p(_ value: Double) -> Double { value * scale }
    func y(_ value: Double) -> Double { value * scale + y0 }
}

/// 3 × 3 projective maps (the same matrices Skia takes in the React Native build) as SwiftUI projections.
/// A projection is applied about the view's top-left corner, so every matrix is written in the view's own
/// coordinates.
enum TasteProfileProjection {
    /// Row-major 3 × 3: x' = (a x + b y + c) / w, y' = (d x + e y + f) / w, w = g x + h y + i.
    static func make(_ m: [Double]) -> ProjectionTransform {
        var t = CATransform3DIdentity
        t.m11 = m[0]; t.m21 = m[1]; t.m41 = m[2]
        t.m12 = m[3]; t.m22 = m[4]; t.m42 = m[5]
        t.m14 = m[6]; t.m24 = m[7]; t.m44 = m[8]
        return ProjectionTransform(t)
    }

    /// A flap turning about the horizontal hinge (hx, hy) by `deg` toward the viewer; `below`: it hangs under the hinge.
    static func flap(deg: Double, hx: Double, hy: Double, d: Double, below: Bool) -> ProjectionTransform {
        let a = deg * .pi / 180
        let c = cos(a)
        let p = (below ? -sin(a) : sin(a)) / d
        return make([1, hx * p, -hx * p * hy, 0, c + hy * p, -c * hy + hy - p * hy * hy, 0, p, 1 - p * hy])
    }

    /// A card turning about its vertical axis through (cx, cy) by `deg`.
    static func turn(deg: Double, cx: Double, cy: Double, d: Double) -> ProjectionTransform {
        let a = deg * .pi / 180
        let cs = cos(a)
        let q = sin(a) / d
        return make([cs + cx * q, 0, -cs * cx + cx - q * cx * cx, cy * q, 1, -q * cx * cy, q, 0, 1 - q * cx])
    }
}

/// The pin's "1": a flag and a stem with no foot, in a 17 × 36 box.
struct TasteProfileOneShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width / 17
        let h = rect.height / 36
        var path = Path()
        let points: [(Double, Double)] = [(16.5, 0), (8, 0), (0, 4.5), (0, 13), (8.5, 13), (8.5, 36), (16.5, 36)]
        for (index, point) in points.enumerated() {
            let cg = CGPoint(x: rect.minX + point.0 * w, y: rect.minY + point.1 * h)
            if index == 0 { path.move(to: cg) } else { path.addLine(to: cg) }
        }
        path.closeSubpath()
        return path
    }
}

/// One glyph of a run at its measured ink box (ref px), placed from the top-left of its container,
/// whose own origin is (ox, oy) points from the screen's. A parent must align its children top-leading.
struct TasteProfileGlyph: View {
    let character: Character
    let inkLeft: Double
    let inkWidth: Double
    let run: TasteProfileTheme.GlyphRun
    let color: Color
    let geo: TasteProfileGeo
    var ox = 0.0
    var oy = 0.0

    var body: some View {
        if character == " " || inkWidth <= 0 {
            EmptyView()
        } else if character == "1" {
            TasteProfileOneShape()
                .fill(color)
                .frame(width: geo.p(inkWidth), height: geo.p(run.digitH))
                .offset(x: geo.p(inkLeft) - ox, y: geo.y(run.baseline - run.digitH) - oy)
        } else if let metric = TasteProfileTheme.GlyphToken.em[character] {
            let fontSize = run.size * geo.pointScale
            let horizontal = inkWidth / (metric.width * run.size * 3)
            Text(String(character))
                .font(.custom(run.font, fixedSize: fontSize))
                .foregroundStyle(color)
                .fixedSize()
                .scaleEffect(x: horizontal, y: 1, anchor: .leading)
                .offset(x: geo.p(inkLeft) - ox - metric.bearing * fontSize * horizontal,
                        y: geo.y(run.baseline) - TasteProfileTheme.ascent * fontSize - oy)
        }
    }
}
