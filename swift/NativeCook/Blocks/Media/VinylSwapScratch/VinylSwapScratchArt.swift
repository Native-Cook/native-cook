import SwiftUI

/// The two invented records and everything drawn in the block's canvas.
enum VinylSwapScratchArt {
    typealias M = VinylSwapScratchMotion

    struct RGB {
        var r, g, b: Double
        init(_ hex: UInt32) {
            r = Double((hex >> 16) & 0xff) / 255
            g = Double((hex >> 8) & 0xff) / 255
            b = Double(hex & 0xff) / 255
        }
        init(r: Double, g: Double, b: Double) { self.r = r; self.g = g; self.b = b }
        func mix(_ o: RGB, _ t: Double) -> RGB { RGB(r: r + (o.r - r) * t, g: g + (o.g - g) * t, b: b + (o.b - b) * t) }
        func color(_ a: Double = 1) -> Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
    }

    struct Album {
        let title: String
        let artist: String
        let top: RGB
        let bottom: RGB
        let glow: RGB
        let accent: RGB
    }

    static let albums = [
        Album(title: "Slow Satellites", artist: "The Amber Rooms",
              top: RGB(0x8C6A1F), bottom: RGB(0x15110A), glow: RGB(0xF2B632), accent: RGB(0xFFC845)),
        Album(title: "Glass Harbor", artist: "Noa Lind",
              top: RGB(0x2F6E7A), bottom: RGB(0x0A1416), glow: RGB(0x7FD1C7), accent: RGB(0x7FE3D6)),
    ]

    /// The screen's geometry, from its size and safe area.
    struct Geo {
        let w: Double, h: Double, top: Double, bottom: Double
        var r: Double { 0.56 * w }
        var labelR: Double { 0.42 * r }
        var center: CGPoint { CGPoint(x: 0.29 * w, y: 0.355 * h) }
        var pivot: CGPoint { CGPoint(x: 0.86 * w, y: 0.165 * h) }
        /// Where the stylus rests: 80 % out on the record, 35° below its right.
        var stylus: CGPoint {
            let a = 35.0 * .pi / 180
            return CGPoint(x: center.x + 0.8 * r * cos(a), y: center.y + 0.8 * r * sin(a))
        }
        var armLength: Double { hypot(stylus.x - pivot.x, stylus.y - pivot.y) }
        /// The arm's angle from straight down (positive towards the left), in the groove and swung out.
        var armRest: Double { atan2(-(stylus.x - pivot.x), stylus.y - pivot.y) }
        var armOut: Double { armRest - 0.42 }
        /// How far a record slides to leave the screen on either side.
        var slide: Double { max(center.x + r + 40, w - center.x + r + 40) }
        var stripTop: Double { center.y + r + 26 }
        static let stripH = 56.0
        var titleTop: Double { stripTop + Geo.stripH + 28 }
        var controlsTop: Double { max(titleTop + 76, h - max(bottom, 16) - 66) }
        /// Where the scripted finger lands, as an angle on the record.
        static let contact = -50.0
    }

    // MARK: Background

    static func background(_ ctx: GraphicsContext, _ g: Geo, mix: Double) {
        let a = albums[0], b = albums[1]
        let top = a.top.mix(b.top, mix), bottom = a.bottom.mix(b.bottom, mix), glow = a.glow.mix(b.glow, mix)
        let rect = Path(CGRect(x: 0, y: 0, width: g.w, height: g.h))
        ctx.fill(rect, with: .linearGradient(Gradient(stops: [
            .init(color: top.color(), location: 0),
            .init(color: top.mix(bottom, 0.55).color(), location: 0.45),
            .init(color: bottom.color(), location: 0.8),
        ]), startPoint: .zero, endPoint: CGPoint(x: 0, y: g.h)))
        // The record's label, as a huge blurred light behind it.
        ctx.fill(rect, with: .radialGradient(Gradient(colors: [glow.color(0.42), glow.color(0.12), glow.color(0)]),
                                             center: g.center, startRadius: 0, endRadius: g.w * 1.05))
        ctx.fill(rect, with: .radialGradient(Gradient(colors: [glow.color(0.16), glow.color(0)]),
                                             center: CGPoint(x: g.w, y: g.h * 0.62), startRadius: 0, endRadius: g.w * 0.8))
    }

    // MARK: Record

    static func disc(_ ctx: GraphicsContext, _ g: Geo, album: Int, center c: CGPoint, angle: Double, alpha: Double) {
        guard alpha > 0.001 else { return }
        var ctx = ctx
        ctx.opacity = alpha
        let r = g.r
        let circle = { (rad: Double) in Path(ellipseIn: CGRect(x: c.x - rad, y: c.y - rad, width: rad * 2, height: rad * 2)) }
        // Shadow on the plinth.
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 18))
            l.fill(Path(ellipseIn: CGRect(x: c.x - r + 4, y: c.y - r + 14, width: r * 2, height: r * 2)), with: .color(.black.opacity(0.55)))
        }
        ctx.fill(circle(r), with: .color(Color(red: 0.043, green: 0.043, blue: 0.047)))
        // Grooves: hairlines of alternating brightness, two wider gaps between tracks.
        let inner = g.labelR + 8, outer = r - 7
        for i in 0..<46 {
            let rad = inner + (outer - inner) * Double(i) / 45
            ctx.stroke(circle(rad), with: .color(.white.opacity(i % 2 == 0 ? 0.035 : 0.065)), lineWidth: 0.7)
        }
        for f in [0.34, 0.68] {
            ctx.stroke(circle(inner + (outer - inner) * f), with: .color(Color(red: 0.09, green: 0.09, blue: 0.1)), lineWidth: 3)
        }
        ctx.stroke(circle(r - 0.75), with: .color(.white.opacity(0.12)), lineWidth: 1.5)
        // The sheen: two soft wedges fixed to the light, so the spin reads as gloss.
        var ring = Path()
        ring.addPath(circle(r - 2))
        ring.addPath(circle(g.labelR + 2))
        ctx.fill(ring, with: .conicGradient(Gradient(stops: [
            .init(color: .white.opacity(0), location: 0),
            .init(color: .white.opacity(0.13), location: 0.07),
            .init(color: .white.opacity(0), location: 0.17),
            .init(color: .white.opacity(0), location: 0.5),
            .init(color: .white.opacity(0.09), location: 0.57),
            .init(color: .white.opacity(0), location: 0.67),
            .init(color: .white.opacity(0), location: 1),
        ]), center: c, angle: .degrees(-70)), style: FillStyle(eoFill: true))

        // Everything below turns with the platter.
        var turn = ctx
        turn.translateBy(x: c.x, y: c.y)
        turn.rotate(by: .degrees(angle + (album == 1 ? 140 : 0)))
        // Dust on the vinyl.
        for i in 0..<6 {
            let a = Double(i) * 1.9 + 0.4, d = g.labelR + 14 + (r - g.labelR - 30) * M.hash(i, 9)
            turn.fill(Path(ellipseIn: CGRect(x: d * cos(a) - 0.8, y: d * sin(a) - 0.8, width: 1.6, height: 1.6)), with: .color(.white.opacity(0.28)))
        }
        label(turn, album: album, r: g.labelR)
        // Spindle.
        ctx.fill(circle(4.5), with: .radialGradient(Gradient(colors: [Color(white: 0.92), Color(white: 0.5)]), center: CGPoint(x: c.x - 1, y: c.y - 1), startRadius: 0, endRadius: 4.5))
        ctx.fill(circle(1.6), with: .color(Color(white: 0.2)))
    }

    /// The label, drawn around the origin of an already rotated context.
    static func label(_ ctx: GraphicsContext, album: Int, r: Double) {
        let disc = Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2))
        var l = ctx
        l.clip(to: disc)
        if album == 0 {
            l.fill(disc, with: .color(Color(hex: 0xF2B632)))
            // Sunburst.
            var rays = Path()
            for i in 0..<16 {
                let a0 = Double(i) * .pi / 8, a1 = a0 + .pi / 16
                rays.move(to: .zero)
                rays.addLine(to: CGPoint(x: 2 * r * cos(a0), y: 2 * r * sin(a0)))
                rays.addLine(to: CGPoint(x: 2 * r * cos(a1), y: 2 * r * sin(a1)))
                rays.closeSubpath()
            }
            l.fill(rays, with: .color(Color(hex: 0xE0951C)))
            l.stroke(Path(ellipseIn: CGRect(x: -0.88 * r, y: -0.88 * r, width: 1.76 * r, height: 1.76 * r)),
                     with: .color(Color(hex: 0x5A1A0B, opacity: 0.45)), lineWidth: 1)
            l.fill(Path(ellipseIn: CGRect(x: 0.12 * r, y: -0.62 * r, width: 0.5 * r, height: 0.5 * r)), with: .color(Color(hex: 0x8E2A12)))
            l.draw(Text("THE AMBER ROOMS").font(.system(size: max(7, r * 0.1), weight: .heavy)).tracking(1.2).foregroundColor(Color(hex: 0x5A1A0B)),
                   at: CGPoint(x: 0, y: 0.5 * r))
            l.draw(Text("SIDE A · 33⅓").font(.system(size: max(6, r * 0.075), weight: .semibold, design: .monospaced)).foregroundColor(Color(hex: 0x5A1A0B, opacity: 0.8)),
                   at: CGPoint(x: 0, y: 0.68 * r))
        } else {
            l.fill(disc, with: .color(Color(hex: 0xEDE6D3)))
            let c = CGPoint(x: -0.28 * r, y: 0.22 * r)
            for (i, f) in [0.28, 0.5, 0.72, 0.94].enumerated() {
                l.stroke(Path(ellipseIn: CGRect(x: c.x - f * r, y: c.y - f * r, width: 2 * f * r, height: 2 * f * r)),
                         with: .color(Color(hex: 0x1F6F78, opacity: 1 - Double(i) * 0.18)), lineWidth: r * 0.06)
            }
            l.fill(Path(ellipseIn: CGRect(x: 0.2 * r, y: -0.62 * r, width: 0.36 * r, height: 0.36 * r)), with: .color(Color(hex: 0xFF6B4A)))
            l.draw(Text("NOA LIND").font(.system(size: max(7, r * 0.11), weight: .heavy)).tracking(2).foregroundColor(Color(hex: 0x0E3B40)),
                   at: CGPoint(x: 0.18 * r, y: 0.56 * r))
            l.draw(Text("SIDE B · 33⅓").font(.system(size: max(6, r * 0.075), weight: .semibold, design: .monospaced)).foregroundColor(Color(hex: 0x0E3B40, opacity: 0.8)),
                   at: CGPoint(x: 0.18 * r, y: 0.72 * r))
        }
        ctx.stroke(Path(ellipseIn: CGRect(x: -r + 0.5, y: -r + 0.5, width: 2 * r - 1, height: 2 * r - 1)), with: .color(.black.opacity(0.35)), lineWidth: 1)
    }

    // MARK: Tonearm

    static func arm(_ ctx: GraphicsContext, _ g: Geo, out: Double, lift: Double) {
        let p = g.pivot
        let theta = g.armRest + (g.armOut - g.armRest) * out
        let len = g.armLength
        let base = 0.095 * g.w
        // The pivot's round base.
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 10))
            l.fill(Path(ellipseIn: CGRect(x: p.x - base + 3, y: p.y - base + 8, width: base * 2, height: base * 2)), with: .color(.black.opacity(0.45)))
        }
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - base, y: p.y - base, width: base * 2, height: base * 2)),
                 with: .radialGradient(Gradient(colors: [Color(hex: 0x2C2C2F), Color(hex: 0x141416)]),
                                       center: CGPoint(x: p.x - base * 0.3, y: p.y - base * 0.4), startRadius: 0, endRadius: base * 1.3))
        ctx.stroke(Path(ellipseIn: CGRect(x: p.x - base, y: p.y - base, width: base * 2, height: base * 2)), with: .color(.white.opacity(0.09)), lineWidth: 1)

        // The arm itself, drawn pointing down from the pivot, then turned.
        func shape(_ c: GraphicsContext, shadow: Bool) {
            var c = c
            c.translateBy(x: p.x, y: p.y)
            c.rotate(by: .radians(theta))
            let s = 1 + 0.04 * lift
            c.scaleBy(x: s, y: s)
            let ink = { (color: Color) -> GraphicsContext.Shading in shadow ? .color(.black) : .color(color) }
            // Counterweight.
            let weight = Path(roundedRect: CGRect(x: -10, y: -50, width: 20, height: 36), cornerRadius: 4)
            c.fill(weight, with: shadow ? .color(.black) : .linearGradient(Gradient(colors: [Color(hex: 0x3A3A3E), Color(hex: 0x76767C), Color(hex: 0x2A2A2D)]),
                                                                            startPoint: CGPoint(x: -10, y: 0), endPoint: CGPoint(x: 10, y: 0)))
            if !shadow {
                for y in [-42.0, -24.0] { c.fill(Path(CGRect(x: -10, y: y, width: 20, height: 1.2)), with: .color(.black.opacity(0.5))) }
            }
            // Tube.
            let tube = Path(roundedRect: CGRect(x: -3, y: 0, width: 6, height: len - 26), cornerRadius: 3)
            c.fill(tube, with: shadow ? .color(.black) : .linearGradient(Gradient(stops: [
                .init(color: Color(hex: 0x86868C), location: 0),
                .init(color: Color(hex: 0xF5F5F7), location: 0.4),
                .init(color: Color(hex: 0xA6A6AB), location: 0.7),
                .init(color: Color(hex: 0x5E5E63), location: 1),
            ]), startPoint: CGPoint(x: -3, y: 0), endPoint: CGPoint(x: 3, y: 0)))
            // Headshell, with its finger lift.
            c.fill(Path(roundedRect: CGRect(x: 6, y: len - 30, width: 14, height: 3), cornerRadius: 1.5), with: ink(Color(hex: 0x8A8A90)))
            let shell = Path(roundedRect: CGRect(x: -8, y: len - 34, width: 16, height: 36), cornerRadius: 3)
            c.fill(shell, with: shadow ? .color(.black) : .linearGradient(Gradient(colors: [Color(hex: 0x2A2A2E), Color(hex: 0x111113)]),
                                                                           startPoint: CGPoint(x: -8, y: 0), endPoint: CGPoint(x: 8, y: 0)))
            if !shadow {
                c.stroke(shell, with: .color(.white.opacity(0.12)), lineWidth: 0.8)
                c.fill(Path(ellipseIn: CGRect(x: -1.6, y: len - 8, width: 3.2, height: 3.2)), with: .color(Color(hex: 0xFFB13B)))
            }
            // Pivot cap.
            c.fill(Path(ellipseIn: CGRect(x: -13, y: -13, width: 26, height: 26)), with: shadow ? .color(.black) : .radialGradient(
                Gradient(colors: [Color(hex: 0xF2F2F4), Color(hex: 0x7C7C82), Color(hex: 0x3A3A3E)]), center: CGPoint(x: -4, y: -5), startRadius: 0, endRadius: 16))
            if !shadow { c.fill(Path(ellipseIn: CGRect(x: -4.5, y: -4.5, width: 9, height: 9)), with: .color(Color(hex: 0x1A1A1C))) }
        }
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 3 + 6 * lift))
            l.opacity = 0.4 - 0.12 * lift
            l.translateBy(x: 4 + 10 * lift, y: 8 + 16 * lift)
            shape(l, shadow: true)
        }
        shape(ctx, shadow: false)
    }

    // MARK: Waveform

    static func waveform(_ ctx: GraphicsContext, _ g: Geo, pose p: M.Pose) {
        let x0 = 24.0, w = g.w - 48, top = g.stripTop, h = Geo.stripH
        let strip = Path(roundedRect: CGRect(x: x0, y: top, width: w, height: h), cornerRadius: 14)
        ctx.fill(strip, with: .color(.black.opacity(0.26)))
        ctx.stroke(strip, with: .color(.white.opacity(0.07)), lineWidth: 1)
        let pitch = 6.0
        let pos = p.angle / 360 * M.barsPerTurn
        let mid = x0 + w / 2
        let speed = p.speed / 360 * M.barsPerTurn * pitch
        let intensity = M.clamp01((abs(p.speed) - 250) / 350)
        let smear = min(9, max(0, abs(speed) - 70) * 0.06)
        let accent = albums[0].accent.mix(albums[1].accent, p.mix)
        let loud = 0.12 + 0.88 * p.needle
        let n = Int(w / 2 / pitch) + 2
        let first = Int(floor(pos)) - n
        var c = ctx
        c.clip(to: strip)
        for k in first...(first + 2 * n + 1) {
            let x = mid + (Double(k) - pos) * pitch
            if x < x0 + 6 || x > x0 + w - 6 { continue }
            let edge = min(1, min(x - x0 - 6, x0 + w - 6 - x) / 26)
            let hv = M.bar(0, k) + (M.bar(1, k) - M.bar(0, k)) * p.waveMix
            let bh = 4 + (h - 16) * hv * loud
            let played = Double(k) < pos
            var color = Color.white.opacity((played ? 0.92 : 0.32) * edge)
            if M.isKick(k) && played { color = accent.color(edge) }
            if intensity > 0 { color = played || M.isKick(k) ? accent.color(edge * (0.92 + 0.08 * intensity)) : accent.color(edge * (0.32 + 0.4 * intensity)) }
            // Bars smear behind their motion when the record is pushed fast.
            let bw = 3 + smear
            let bx = speed >= 0 ? x - 1.5 : x + 1.5 - bw
            c.fill(Path(roundedRect: CGRect(x: bx, y: top + h / 2 - bh / 2, width: bw, height: bh), cornerRadius: 1.5),
                   with: .color(color.opacity(smear > 0 ? 3 / bw + 0.25 : 1)))
        }
        // Playhead.
        c.fill(Path(CGRect(x: mid - 0.75, y: top + 6, width: 1.5, height: h - 12)), with: .color(.white))
        ctx.fill(Path(ellipseIn: CGRect(x: mid - 3, y: top - 3, width: 6, height: 6)), with: .color(.white))
    }

    // MARK: Hand

    static func scratchMarks(_ ctx: GraphicsContext, _ g: Geo, pose p: M.Pose) {
        let intensity = M.clamp01((abs(p.speed) - 250) / 350)
        guard intensity > 0.01 else { return }
        let c = g.center
        for j in 0..<5 {
            let flick = 0.5 + 0.5 * sin(p.clock * 47 + Double(j) * 2.3)
            let rad = g.r + 8 + Double(j % 3) * 6
            let mid = VinylSwapScratchArt.Geo.contact + Double(j - 2) * 16 + p.fingerTurn * 0.4
            var arc = Path()
            arc.addArc(center: c, radius: rad, startAngle: .degrees(mid - 7), endAngle: .degrees(mid + 7), clockwise: false)
            ctx.stroke(arc, with: .color(.white.opacity(0.7 * intensity * flick)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
        }
    }

    static func finger(_ ctx: GraphicsContext, _ g: Geo, pose p: M.Pose) {
        guard p.fingerAlpha > 0.001 else { return }
        let a = (Geo.contact + p.fingerTurn) * .pi / 180
        let c = g.center
        let pt = CGPoint(x: c.x + 0.72 * g.r * cos(a), y: c.y + 0.72 * g.r * sin(a))
        let r = 22 * p.fingerScale
        let dot = Path(ellipseIn: CGRect(x: pt.x - r, y: pt.y - r, width: r * 2, height: r * 2))
        ctx.fill(dot, with: .color(.white.opacity(0.2 * p.fingerAlpha)))
        ctx.stroke(dot, with: .color(.white.opacity(0.6 * p.fingerAlpha)), lineWidth: 1.5)
    }
}
