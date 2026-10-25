import SwiftUI
import UIKit

/// Where everything sits, from the screen size and its safe area (as in React Native).
struct FuseBurnButtonLayout: Equatable {
    var width: Double
    var height: Double
    var headerTop: Double
    var poster: CGRect
    var rowsTop: Double
    var capsule: CGRect
    var hintTop: Double

    private static let header = 86.0
    private static let rows = 76.0
    private static let capsuleH = 60.0

    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width
        self.height = height
        headerTop = top + 14
        hintTop = height - max(bottom, 14) - 22
        let capsuleY = hintTop - 18 - Self.capsuleH
        let w = min(width - 48, 420)
        let x = (width - w) / 2
        rowsTop = capsuleY - 30 - Self.rows
        let posterY = headerTop + Self.header + 8
        let posterH = max(150, min(460, rowsTop - 18 - posterY))
        poster = CGRect(x: x, y: posterY, width: w, height: posterH)
        capsule = CGRect(x: x, y: capsuleY, width: w, height: Self.capsuleH)
    }
}

enum FuseBurnButtonPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(
            red: Double((v >> 16) & 0xFF) / 255,
            green: Double((v >> 8) & 0xFF) / 255,
            blue: Double(v & 0xFF) / 255,
            opacity: a
        )
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }

    static let background = hex(0x0D1226)
    static let cream = hex(0xFFE3B8)
    static let ink = hex(0xEEF0FA)
    static let mute = rgba(214, 220, 245, 0.62)
    static let hint = rgba(214, 220, 245, 0.45)
    static let inInk = hex(0x3A1606)
}

enum FuseBurnButtonArt {
    @MainActor private static var posters: [String: UIImage] = [:]

    /// The event poster: a rooftop at dusk, string lights, a skyline. Drawn once per size.
    @MainActor
    static func poster(_ frame: CGRect, scale: Double) -> UIImage {
        let size = frame.size
        let key = "\(frame.minX),\(frame.minY),\(size.width)x\(size.height)@\(scale)"
        if let image = posters[key] { return image }
        let renderer = ImageRenderer(content:
            Canvas { ctx, _ in drawPoster(&ctx, origin: frame.origin, w: size.width, h: size.height) }
                .frame(width: size.width, height: size.height)
        )
        renderer.scale = scale
        renderer.isOpaque = false
        let image = renderer.uiImage.map { $0.preparingForDisplay() ?? $0 } ?? UIImage()
        posters[key] = image
        return image
    }

    private static func drawPoster(_ c: inout GraphicsContext, origin: CGPoint, w: Double, h: Double) {
        typealias P = FuseBurnButtonPalette
        typealias M = FuseBurnButtonMotion
        let rect = CGRect(x: 0, y: 0, width: w, height: h)
        c.clip(to: Path(roundedRect: rect, cornerRadius: 24, style: .circular))
        c.fill(Path(rect), with: .linearGradient(
            Gradient(stops: [
                .init(color: P.hex(0x0B1030), location: 0),
                .init(color: P.hex(0x1E1D55), location: 0.38),
                .init(color: P.hex(0x5B2A6E), location: 0.62),
                .init(color: P.hex(0xD8694A), location: 0.84),
                .init(color: P.hex(0xF0A35E), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: h)
        ))
        // A warm glow on the horizon.
        c.fill(Path(rect), with: .radialGradient(
            Gradient(stops: [
                .init(color: P.rgba(255, 170, 100, 0.55), location: 0),
                .init(color: P.rgba(255, 120, 80, 0.15), location: 0.5),
                .init(color: P.rgba(255, 120, 80, 0), location: 1),
            ]),
            center: CGPoint(x: w * 0.55, y: h * 0.86), startRadius: 0, endRadius: w * 0.6
        ))
        // Stars.
        for i in 0..<46 {
            let fi = Double(i)
            let sx = M.hash(fi * 3.1) * w
            let sy = M.hash(fi * 7.7 + 2) * h * 0.5
            let r = 0.5 + M.hash(fi + 4) * 0.9
            c.fill(Path(ellipseIn: CGRect(x: sx - r, y: sy - r, width: r * 2, height: r * 2)),
                   with: .color(P.rgba(220, 228, 255, 0.25 + M.hash(fi + 9) * 0.6)))
        }
        // The moon with a halo.
        let mx = w * 0.78
        let my = h * 0.2
        c.fill(Path(ellipseIn: CGRect(x: mx - 60, y: my - 60, width: 120, height: 120)), with: .radialGradient(
            Gradient(colors: [P.rgba(255, 236, 210, 0.35), P.rgba(255, 236, 210, 0)]),
            center: CGPoint(x: mx, y: my), startRadius: 0, endRadius: 60
        ))
        c.fill(Path(ellipseIn: CGRect(x: mx - 16, y: my - 16, width: 32, height: 32)), with: .radialGradient(
            Gradient(stops: [
                .init(color: P.hex(0xFFF4DF), location: 0),
                .init(color: P.hex(0xF3DCB6), location: 0.6),
                .init(color: P.hex(0xD9B98F), location: 1),
            ]),
            center: CGPoint(x: mx - 5, y: my - 5), startRadius: 0, endRadius: 22
        ))
        // Skyline, two layers; each layer's buildings and windows are one path.
        let ground = h
        for layer in 0..<2 {
            var blocks = Path()
            var windows = Path()
            var x = -6.0
            var k = Double(layer * 50)
            while x < w {
                let bw = 18 + M.hash(k) * 30
                let bh = (layer == 0 ? 0.28 : 0.16) * h + M.hash(k + 1) * h * (layer == 0 ? 0.22 : 0.18)
                blocks.addRect(CGRect(x: x, y: ground - bh, width: bw - 2, height: bh))
                if layer == 1 {
                    var wy = ground - bh + 6
                    while wy < ground - 6 {
                        var wx = x + 4
                        while wx < x + bw - 6 {
                            // The React Native poster is drawn in screen points, and so is its hash.
                            if M.hash((wx + origin.x) * 0.37 + (wy + origin.y) * 1.3) > 0.72 {
                                windows.addRect(CGRect(x: wx, y: wy, width: 2, height: 3))
                            }
                            wx += 6
                        }
                        wy += 7
                    }
                }
                x += bw
                k += 2
            }
            c.fill(blocks, with: .color(layer == 0 ? P.hex(0x3A2559) : P.hex(0x140F2B)))
            if layer == 1 { c.fill(windows, with: .color(P.rgba(255, 196, 120, 0.75))) }
        }
        // The rooftop's string lights: a sagging wire with warm bulbs.
        let y0 = h * 0.34
        let y1 = h * 0.3
        var wire = Path()
        wire.move(to: CGPoint(x: -4, y: y0))
        wire.addQuadCurve(to: CGPoint(x: w + 4, y: y1), control: CGPoint(x: w * 0.5, y: h * 0.52))
        c.stroke(wire, with: .color(P.rgba(20, 16, 40, 0.9)), lineWidth: 1)
        var glows = Path()
        var bulbs = Path()
        for i in 1..<14 {
            let u = Double(i) / 14
            let bx = (1 - u) * (1 - u) * -4 + 2 * u * (1 - u) * (w * 0.5) + u * u * (w + 4)
            let by = (1 - u) * (1 - u) * y0 + 2 * u * (1 - u) * (h * 0.52) + u * u * y1 + 4
            glows.addEllipse(in: CGRect(x: bx - 7, y: by - 7, width: 14, height: 14))
            bulbs.addEllipse(in: CGRect(x: bx - 2.2, y: by - 2.2, width: 4.4, height: 4.4))
        }
        c.drawLayer { l in
            l.addFilter(.blur(radius: 5))
            l.fill(glows, with: .color(P.rgba(255, 190, 110, 0.5)))
        }
        c.fill(bulbs, with: .color(P.hex(0xFFE2A8)))
        // Vignette inside the poster.
        c.fill(Path(rect), with: .radialGradient(
            Gradient(stops: [
                .init(color: P.rgba(5, 6, 20, 0), location: 0.55),
                .init(color: P.rgba(5, 6, 20, 0.45), location: 1),
            ]),
            center: CGPoint(x: w / 2, y: h * 0.45), startRadius: 0, endRadius: max(w, h) * 0.75
        ))
    }

    /// The flame beside "Light it up" (a 16 × 18 icon).
    static let flame: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 8, y: 1.2))
        p.addCurve(to: CGPoint(x: 11.9, y: 9.6), control1: CGPoint(x: 8.6, y: 3.6), control2: CGPoint(x: 11.9, y: 5.5))
        p.addArc(center: CGPoint(x: 8, y: 9.6), radius: 3.9, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
        p.addCurve(to: CGPoint(x: 5.8, y: 5.8), control1: CGPoint(x: 4.1, y: 7.9), control2: CGPoint(x: 4.9, y: 6.7))
        p.addCurve(to: CGPoint(x: 7.4, y: 8.0), control1: CGPoint(x: 6.0, y: 6.9), control2: CGPoint(x: 6.6, y: 7.7))
        p.addCurve(to: CGPoint(x: 8, y: 1.2), control1: CGPoint(x: 7, y: 6), control2: CGPoint(x: 6.9, y: 3.4))
        p.closeSubpath()
        return p
    }()

    static let flameBase: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 3, y: 16.2))
        p.addLine(to: CGPoint(x: 13, y: 16.2))
        return p
    }()

    /// The check beside "You're in" (an 18 × 18 icon).
    static let check: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 3.5, y: 9.5))
        p.addLine(to: CGPoint(x: 7.1, y: 13.1))
        p.addLine(to: CGPoint(x: 14.5, y: 5.1))
        return p
    }()
}
