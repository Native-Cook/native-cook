import SwiftUI

enum HeatShapePickerArt {
    typealias M = HeatShapePickerModel

    static func path(_ pose: HeatShapePickerPose, center: CGPoint, scale: Double,
                     time: Double?, bulge: Double = 0, direction: Double = 0) -> Path {
        var path = Path()
        for j in 0..<240 {
            let theta = Double(j) * 2 * .pi / 240
            let delta = atan2(sin(theta - direction), cos(theta - direction))
            let r = M.radius(theta, pose.p, pose.c, pose.k, pose.phi, time) * scale
                * (1 + 0.10 * bulge * exp(-pow(delta / 0.6, 2)))
            let point = CGPoint(x: center.x + r * sin(theta), y: center.y - r * cos(theta))
            if j == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }

    static func draw(_ context: GraphicsContext, frame: HeatShapePickerFrame,
                     layout: HeatShapePickerLayout, reduced: Bool,
                     dx: Double, dy: Double, grip: Double, direction: Double) {
        let center = CGPoint(x: layout.center.x + (reduced ? 0 : dx), y: layout.center.y + (reduced ? 0 : dy))
        let scale = layout.sx * (reduced ? 1 : 1 + 0.08 * grip)
        for layer in frame.layers where layer.opacity > 0.001 {
            var c = context; c.opacity = layer.opacity
            let pose = layer.pose
            let path = path(pose, center: center, scale: scale, time: reduced ? nil : frame.time,
                            bulge: reduced ? 0 : grip, direction: direction)
            let r = 110 * layout.sx, glow = frame.glow * (1 + 0.4 * grip)
            c.fill(Path(ellipseIn: CGRect(x: center.x - 250, y: center.y - 250, width: 500, height: 500)),
                   with: .radialGradient(Gradient(colors: [M.ramp(pose.h + 0.5, 0.22), M.ramp(pose.h + 0.5, 0)]),
                                         center: center, startRadius: 0, endRadius: 250))
            stroke(c, path, color: M.color(0x5546FF, min(1, 0.80 * glow)), width: 34, blur: 20)
            stroke(c, path, color: M.color(0xFF4A1E, min(1, 0.90 * glow)), width: 14, blur: 8)
            c.fill(path, with: .linearGradient(M.gradient(pose.h),
                startPoint: CGPoint(x: center.x, y: center.y - 1.15 * r),
                endPoint: CGPoint(x: center.x, y: center.y + 1.15 * r)))
            var inner = c; inner.clip(to: path)
            inner.fill(path, with: .radialGradient(Gradient(colors: [.white.opacity(0.26), .white.opacity(0)]),
                center: CGPoint(x: center.x - 0.22 * r, y: center.y - 0.30 * r), startRadius: 0, endRadius: 0.95 * r))
            stroke(inner, path, color: M.ramp(pose.h - 0.25, 0.55), width: 22, blur: 12)
            stroke(c, path, color: M.color(0xFF2A12), width: 2.2, blur: 0.8)
            let hot = M.clamp(1 - abs((pose.h - floor(pose.h)) - 0.56) / 0.2)
            stroke(c, path, color: .white.opacity(0.55 * hot), width: 1, blur: 0)
            if !reduced && frame.morph > 0 && frame.morph < 1 {
                c.drawLayer { sparks in
                    sparks.addFilter(.blur(radius: 1.5))
                    for j in 0..<16 {
                        let theta = Double(j) * .pi / 8 + .pi / 20
                        let delta = atan2(sin(theta - direction), cos(theta - direction))
                        let edge = M.radius(theta, pose.p, pose.c, pose.k, pose.phi, frame.time) * scale
                            * (1 + 0.10 * grip * exp(-pow(delta / 0.6, 2)))
                        let distance = edge + 6 + 70 * M.out(frame.morph)
                        let seed = sin(Double(j) * 12.9898 + 3.1) * 43758.5453
                        let dot = 1.4 + 1.2 * (seed - floor(seed))
                        let x = center.x + distance * sin(theta), y = center.y - distance * cos(theta)
                        sparks.fill(Path(ellipseIn: CGRect(x: x - dot, y: y - dot, width: 2 * dot, height: 2 * dot)),
                                    with: .color(M.ramp(pose.h + 0.1, sin(.pi * frame.morph) * 0.9)))
                    }
                }
            }
        }
    }

    private static func stroke(_ context: GraphicsContext, _ path: Path, color: Color, width: Double, blur: Double) {
        context.drawLayer { c in
            if blur > 0 { c.addFilter(.blur(radius: blur)) }
            c.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
        }
    }

    static func glyph(_ context: GraphicsContext, index: Int, selected: Double) {
        let pose = M.modes[index], center = CGPoint(x: 38, y: 34)
        let path = path(pose, center: center, scale: 15.0 / 110, time: nil)
        context.stroke(path, with: .color(.white.opacity(0.70 * (1 - selected))),
                       style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
        var filled = context; filled.opacity = selected
        filled.fill(path, with: .linearGradient(M.gradient(pose.h),
            startPoint: CGPoint(x: 38, y: 34 - 17.25), endPoint: CGPoint(x: 38, y: 34 + 17.25)))
        filled.stroke(path, with: .color(M.color(0xFF2A12)), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
    }
}
