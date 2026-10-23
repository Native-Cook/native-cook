import SwiftUI

/// The disc's two faces, the case art and the HUD frame, drawn in code.
enum IridescentCdFlipArt {
    static let background = Color(hex: 0x060607)
    static let streakColors: [UInt32] = [0x3AA8FF, 0x5FD8FF, 0xE8283C, 0xFF4B3A, 0xF4E3B5, 0x3BE39A]
    static let hue: [UInt32] = [0xFF7AD9, 0x9B7BFF, 0x5BC8FF, 0x5BFFC8, 0xF6FF7A, 0xFF9A6B]

    /// The printed art's light streaks inside `rect` (the disc's square or the case's front):
    /// broad warm bands across the lower third and a fan of short cold streaks at the right.
    static func streaks(_ ctx: GraphicsContext, _ rect: CGRect) {
        let s = rect.width
        var c = ctx
        c.drawLayer { l in
            l.addFilter(.blur(radius: max(0.6, s * 0.006)))
            for k in 0..<7 {
                let y = rect.minY + s * (0.71 + Double(k) * 0.034)
                let hgt = s * (0.012 + 0.008 * Double(k % 3))
                let color = Color(hex: [0xFF6A2B, 0xE8283C, 0xFFB46B, 0xFF6A2B, 0xE8283C, 0xFF8A3D, 0xC21F3A][k])
                l.fill(Path(roundedRect: CGRect(x: rect.minX - 4, y: y, width: s + 8, height: hgt), cornerRadius: hgt / 2),
                       with: .linearGradient(Gradient(colors: [color.opacity(0.75), color, color.opacity(0.9)]),
                                             startPoint: CGPoint(x: rect.minX, y: y), endPoint: CGPoint(x: rect.maxX, y: y)))
            }
            for k in 0..<6 {
                let y = rect.minY + s * (0.2 + Double(k) * 0.075)
                let len = s * (0.18 + 0.1 * Double((k * 7) % 3))
                let hgt = s * (0.016 + 0.006 * Double(k % 2))
                let color = Color(hex: streakColors[k])
                l.fill(Path(roundedRect: CGRect(x: rect.maxX - len, y: y, width: len + 4, height: hgt), cornerRadius: hgt / 2),
                       with: .linearGradient(Gradient(colors: [color.opacity(0), color.opacity(0.95), .white.opacity(0.9)]),
                                             startPoint: CGPoint(x: rect.maxX - len, y: y), endPoint: CGPoint(x: rect.maxX, y: y)))
            }
        }
    }

    /// The face of the disc (radius r) in a 2r × 2r square, seen at turn `angle` (degrees, for the light).
    /// `back` draws the data side. `streaks` is the printed side's art drawn once by the view (at
    /// `scale` pixels a point); without it the streaks are drawn here, blur and all.
    static func face(_ ctx: GraphicsContext, r: Double, angle: Double, back: Bool, streaks art: CGImage? = nil, scale: Double = 1) {
        let c = CGPoint(x: r, y: r)
        func circle(_ rad: Double) -> Path { Path(ellipseIn: CGRect(x: c.x - rad, y: c.y - rad, width: 2 * rad, height: 2 * rad)) }
        var g = ctx
        g.clip(to: circle(r))
        let a = angle * .pi / 180
        if back {
            g.fill(circle(r), with: .radialGradient(Gradient(stops: [
                .init(color: Color(hex: 0xE3E5EA), location: 0),
                .init(color: Color(hex: 0xA9ACB4), location: 0.6),
                .init(color: Color(hex: 0xD5D7DD), location: 1),
            ]), center: c, startRadius: 0, endRadius: r))
            // The diffraction rainbow: two hue cycles around the disc, swept round as it turns.
            var ring = Path()
            ring.addPath(circle(r))
            ring.addPath(circle(r * 0.33))
            var stops: [Gradient.Stop] = []
            for k in 0..<12 {
                stops.append(.init(color: Color(hex: hue[k % 6]).opacity(0.7), location: Double(k) / 12))
                stops.append(.init(color: Color(hex: hue[k % 6]).opacity(0.08), location: (Double(k) + 0.55) / 12))
            }
            stops.append(.init(color: Color(hex: hue[0]).opacity(0.7), location: 1))
            g.fill(ring, with: .conicGradient(Gradient(stops: stops), center: c, angle: .degrees(2 * angle)), style: FillStyle(eoFill: true))
            for k in 0..<18 {
                let rad = r * (0.36 + 0.62 * Double(k) / 17)
                g.stroke(circle(rad), with: .color(.black.opacity(k % 3 == 0 ? 0.07 : 0.035)), lineWidth: 0.6)
            }
        } else {
            g.fill(circle(r), with: .radialGradient(Gradient(colors: [Color(hex: 0x17171B), Color(hex: 0x08080A)]),
                                                    center: c, startRadius: 0, endRadius: r))
            if let art {
                g.draw(Image(decorative: art, scale: scale), in: CGRect(x: 0, y: 0, width: 2 * r, height: 2 * r))
            } else {
                streaks(g, CGRect(x: 0, y: 0, width: 2 * r, height: 2 * r))
            }
            g.draw(Text("MIRROR INDEX").font(.system(size: max(7, r * 0.075), weight: .semibold, design: .monospaced)).tracking(1.5)
                    .foregroundColor(Color(hex: 0xD8B25A)),
                   at: CGPoint(x: r * 0.24, y: r * 0.62), anchor: .leading)
            g.stroke(circle(r * 0.34), with: .color(Color(hex: 0xE8283C, opacity: 0.8)), lineWidth: 1)
            g.stroke(circle(r * 0.355), with: .color(Color(hex: 0x3BE39A, opacity: 0.7)), lineWidth: 1)
        }
        // The light: a soft diagonal band that slides across as the disc turns.
        let u = 0.5 + 0.6 * sin(a)
        let p0 = CGPoint(x: 2 * r * (u - 0.35), y: 2 * r * 0.2), p1 = CGPoint(x: 2 * r * (u + 0.35), y: 2 * r * 0.8)
        g.fill(circle(r), with: .linearGradient(Gradient(stops: [
            .init(color: .white.opacity(0), location: 0),
            .init(color: .white.opacity(back ? 0.38 : 0.16), location: 0.5),
            .init(color: .white.opacity(0), location: 1),
        ]), startPoint: p0, endPoint: p1))
        // Hub: a clear plastic ring, a grey clamping ring, the hole.
        g.fill(circle(r * 0.31), with: .radialGradient(Gradient(colors: back
            ? [Color(hex: 0xEDEEF2, opacity: 0.9), Color(hex: 0xB9BCC4, opacity: 0.9)]
            : [Color(hex: 0x8C8F96, opacity: 0.9), Color(hex: 0x4A4C52, opacity: 0.9)]),
            center: c, startRadius: r * 0.08, endRadius: r * 0.31))
        g.stroke(circle(r * 0.31), with: .color(.white.opacity(0.35)), lineWidth: 1)
        g.stroke(circle(r * 0.2), with: .color(.white.opacity(back ? 0.5 : 0.25)), lineWidth: 1.2)
        g.fill(circle(r * 0.075), with: .color(background))
        g.stroke(circle(r * 0.075), with: .color(.white.opacity(0.2)), lineWidth: 0.8)
        // The rim.
        g.stroke(circle(r - 0.75), with: .linearGradient(Gradient(colors: [.white.opacity(0.55), .white.opacity(0.12), .white.opacity(0.4)]),
                                                         startPoint: .zero, endPoint: CGPoint(x: 2 * r, y: 2 * r)), lineWidth: 1.5)
    }

    /// The chamfered HUD frame: corners cut at the top right and bottom left.
    static func frame(_ rect: CGRect, cut: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX - cut, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + cut))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX + cut, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY - cut))
        p.closeSubpath()
        return p
    }

    /// Film grain: fixed specks over the black.
    static func grain(_ ctx: GraphicsContext, size: CGSize) {
        typealias M = IridescentCdFlipMotion
        for i in 0..<700 {
            let x = M.hash(i, 3) * size.width, y = M.hash(i, 4) * size.height
            let s = 0.6 + 1.2 * M.hash(i, 5)
            ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: s, height: s)), with: .color(.white.opacity(0.03 + 0.07 * M.hash(i, 6))))
        }
    }
}
