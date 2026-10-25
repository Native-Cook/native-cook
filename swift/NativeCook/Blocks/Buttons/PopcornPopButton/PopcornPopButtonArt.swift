import SwiftUI
import UIKit

/*
 Popcorn Pop Button — the drawing. Static layers (the velvet room with the movie card, the
 button's plate with its thermometer track, the copper pan, the glass lid, the striped bowl) are
 drawn once into their own rasterised views; kernels and corns are 10 sprites rendered once.
 Per frame: the heat (arc, glow under the pan, warm tint inside it), then kernels, hull flecks and
 corns from the sprites, each under its own transform; the lid and the bowl move as whole layers.
 */
enum PopcornPopButtonArt {
    typealias Geo = PopcornPopButtonMotion.Geo
    typealias Pose = PopcornPopButtonMotion.Pose

    /// Thermometer arc: starts at 125° (clockwise from +x) and sweeps 290°, leaving the label's gap at the bottom.
    static let arcStart = 125.0
    static let arcSweep = 290.0

    static func layout(w: Double, h: Double, top: Double, bottom: Double) -> Geo {
        let cardX = 20.0
        let cardY = top + 104
        let cardW = w - 40
        let cardH = 96.0
        let footerY = h - max(bottom, 14) - 58
        let a0 = cardY + cardH + 22
        let a1 = footerY - 18
        let D = max(40, min(w - 44, a1 - a0, 344))
        let bx = w / 2
        let by = (a0 + a1) / 2
        return Geo(w: w, h: h, bx: bx, by: by, D: D, px: bx, py: by - D * 0.06, rx: D * 0.3, ry: D * 0.19,
                   wall: D * 0.055, dome: D * 0.11, bowlX: cardX + cardW - 42, bowlY: cardY + cardH / 2 - 4,
                   cardX: cardX, cardY: cardY, cardW: cardW, cardH: cardH, footerY: footerY, arcR: D / 2 - 15)
    }

    // MARK: Helpers

    /// "#RRGGBB" or "rgba(r,g,b,a)".
    static func col(_ s: String) -> Color {
        if s.hasPrefix("#") {
            return Color(hex: UInt32(s.dropFirst(), radix: 16) ?? 0)
        }
        let parts = s.drop { $0 != "(" }.dropFirst().prefix { $0 != ")" }.split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard parts.count == 4 else { return .clear }
        return Color.rgba(parts[0], parts[1], parts[2], parts[3])
    }

    static func gradient(_ list: [String], _ pos: [Double]? = nil) -> Gradient {
        let colors = list.map(col)
        guard let pos, pos.count == colors.count else { return Gradient(colors: colors) }
        return Gradient(stops: zip(colors, pos).map { Gradient.Stop(color: $0, location: $1) })
    }

    static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ list: [String], _ pos: [Double]? = nil, repeating: Bool = false) -> GraphicsContext.Shading {
        .linearGradient(gradient(list, pos), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1), options: repeating ? .repeat : [])
    }

    static func radial(_ x: Double, _ y: Double, _ r: Double, _ list: [String], _ pos: [Double]? = nil) -> GraphicsContext.Shading {
        .radialGradient(gradient(list, pos), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: max(0.1, r))
    }

    static func oval(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
    }

    /// An arc of an ellipse, angles in degrees clockwise from +x (as Skia's drawArc).
    static func arc(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double, _ start: Double, _ sweep: Double) -> Path {
        var p = Path()
        p.addRelativeArc(center: .zero, radius: 1, startAngle: .degrees(start), delta: .degrees(sweep),
                         transform: CGAffineTransform(a: rx, b: 0, c: 0, d: ry, tx: cx, ty: cy))
        return p
    }

    /// Appends an elliptical arc to a path, joined by a line (Skia's arcToOval).
    static func arcTo(_ p: inout Path, _ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double, _ start: Double, _ sweep: Double) {
        p.addRelativeArc(center: .zero, radius: 1, startAngle: .degrees(start), delta: .degrees(sweep),
                         transform: CGAffineTransform(a: rx, b: 0, c: 0, d: ry, tx: cx, ty: cy))
    }

    static func rrect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }

    static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    static func stroke(_ c: GraphicsContext, _ path: Path, _ color: String, _ width: Double, _ alpha: Double = 1) {
        c.stroke(path, with: .color(col(color).opacity(alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round))
    }

    static func fill(_ c: GraphicsContext, _ path: Path, _ color: String, _ alpha: Double = 1) {
        c.fill(path, with: .color(col(color).opacity(alpha)))
    }

    static func blurred(_ c: GraphicsContext, _ path: Path, _ color: String, _ alpha: Double, _ sigma: Double) {
        var b = c
        b.addFilter(.blur(radius: sigma))
        b.fill(path, with: .color(col(color).opacity(alpha)))
    }

    static func rotated(_ c: GraphicsContext, _ deg: Double, _ x: Double, _ y: Double) -> GraphicsContext {
        var r = c
        r.translateBy(x: x, y: y)
        r.rotate(by: .degrees(deg))
        r.translateBy(x: -x, y: -y)
        return r
    }

    /// A small tile of seeded grey noise, overlaid as film grain.
    static let grainTile: Image = {
        let side = 96
        var bytes = [UInt8](repeating: 255, count: side * side * 4)
        var seed: UInt32 = 0x9E3779B9
        for i in 0..<(side * side) {
            seed = seed &* 1664525 &+ 1013904223
            let v = UInt8(truncatingIfNeeded: seed >> 24)
            bytes[i * 4] = v
            bytes[i * 4 + 1] = v
            bytes[i * 4 + 2] = v
        }
        let space = CGColorSpaceCreateDeviceRGB()
        let image = bytes.withUnsafeMutableBytes { raw -> CGImage? in
            CGContext(data: raw.baseAddress, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                      space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)?.makeImage()
        }
        return image.map { Image(decorative: $0, scale: 3) } ?? Image(systemName: "circle")
    }()

    static func grain(_ c: GraphicsContext, _ rect: CGRect, _ alpha: Double) {
        var g = c
        g.blendMode = .overlay
        g.opacity = alpha
        g.fill(Path(rect), with: .tiledImage(grainTile, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    // MARK: Static layers

    /// The room: a wine velvet wall with a curtain over the header, a warm spot behind the button, the movie card.
    static func room(_ c: GraphicsContext, _ g: Geo, top: Double) {
        let w = g.w
        let h = g.h
        let all = Path(CGRect(x: 0, y: 0, width: w, height: h))
        c.fill(all, with: linear(0, 0, 0, h, ["#2B1016", "#200B11", "#16070C", "#110509"], [0, 0.35, 0.75, 1]))
        // Curtain folds behind the header, fading out downwards.
        let band = top + 150
        let bandRect = Path(CGRect(x: 0, y: 0, width: w, height: band))
        c.drawLayer { l in
            l.fill(bandRect, with: linear(0, 0, 31, 0, ["#2A0C12", "#5C1D27", "#7A2A33", "#4A161E", "#2A0C12"], [0, 0.3, 0.45, 0.7, 1], repeating: true))
            l.blendMode = .destinationIn
            l.fill(bandRect, with: linear(0, 0, 0, band, ["rgba(0,0,0,0.85)", "rgba(0,0,0,0.55)", "rgba(0,0,0,0)"], [0, 0.5, 1]))
        }
        // A warm spot behind the button.
        c.fill(all, with: radial(g.bx, g.by - g.D * 0.05, g.D * 0.95, ["rgba(140,58,34,0.55)", "rgba(96,34,26,0.25)", "rgba(60,18,20,0)"], [0, 0.55, 1]))
        // Vignette.
        c.fill(all, with: radial(w / 2, h * 0.52, hypot(w, h) * 0.62, ["rgba(10,3,5,0)", "rgba(10,3,5,0.25)", "rgba(8,2,4,0.62)"], [0, 0.6, 1]))
        grain(c, CGRect(x: 0, y: 0, width: w, height: h), 0.07)
        // The movie card: two shadows, a plum face, a lit top edge.
        let x = g.cardX, y = g.cardY, cw = g.cardW, ch = g.cardH
        blurred(c, rrect(x, y + 14, cw, ch, 20), "#050102", 0.5, 16)
        blurred(c, rrect(x, y + 1.5, cw, ch, 20), "#050102", 0.45, 2)
        c.fill(rrect(x, y, cw, ch, 20), with: linear(x, y, x, y + ch, ["#43202A", "#33171F", "#2A1219"], [0, 0.5, 1]))
        c.stroke(rrect(x + 0.5, y + 0.5, cw - 1, ch - 1, 19.5),
                 with: linear(x, y, x, y + ch, ["rgba(255,214,190,0.22)", "rgba(255,214,190,0.05)", "rgba(0,0,0,0.25)"], [0, 0.4, 1]), lineWidth: 1)
        // The poster: a harbour at night under a big moon.
        let px = x + 12, py = y + 12, pw = 60.0, ph = ch - 24
        var pc = c
        pc.clip(to: rrect(px, py, pw, ph, 10))
        let posterRect = CGRect(x: px, y: py, width: pw, height: ph)
        pc.fill(Path(posterRect), with: linear(0, py, 0, py + ph, ["#16223F", "#2C3F66", "#9A5A55", "#E39A5E"], [0, 0.4, 0.62, 0.7]))
        blurred(pc, oval(px + pw * 0.66, py + ph * 0.3, 15, 15), "#FBE7C0", 0.35, 8)
        pc.fill(oval(px + pw * 0.66, py + ph * 0.3, 9.5, 9.5), with: radial(px + pw * 0.62, py + ph * 0.26, 11, ["#FFF6DF", "#F3DDB0", "#D9B880"]))
        pc.fill(Path(CGRect(x: px, y: py + ph * 0.7, width: pw, height: ph * 0.3)), with: linear(0, py + ph * 0.7, 0, py + ph, ["#233A5C", "#14213B", "#0C1528"]))
        for i in 0..<7 {
            let ly = py + ph * (0.74 + Double(i) * 0.035)
            let lw = 6 + 10 * PopcornPopButtonMotion.hash(Double(i) + 3)
            stroke(pc, line(px + pw * 0.66 - lw / 2, ly, px + pw * 0.66 + lw / 2, ly), "#F6D9A8", 1, 0.55 - Double(i) * 0.06)
        }
        // A lighthouse on the left headland.
        var land = Path()
        land.move(to: CGPoint(x: px, y: py + ph * 0.7))
        land.addLine(to: CGPoint(x: px + pw * 0.34, y: py + ph * 0.68))
        land.addLine(to: CGPoint(x: px + pw * 0.4, y: py + ph * 0.72))
        land.addLine(to: CGPoint(x: px, y: py + ph * 0.74))
        land.closeSubpath()
        fill(pc, land, "#0D1424")
        fill(pc, Path(CGRect(x: px + pw * 0.16, y: py + ph * 0.5, width: 4, height: ph * 0.19)), "#E9E0CF")
        blurred(pc, oval(px + pw * 0.16 + 2, py + ph * 0.5, 3, 3), "#FFE7A8", 0.9, 3)
        grain(pc, posterRect, 0.08)
        // The bowl's place: a soft round well on the card's right.
        fill(c, oval(g.bowlX, g.bowlY + 12, 30, 30), "#1E0B11", 0.55)
        c.stroke(oval(g.bowlX, g.bowlY + 12, 30, 30), with: linear(0, g.bowlY - 18, 0, g.bowlY + 42, ["rgba(0,0,0,0.35)", "rgba(255,214,190,0.12)"]), lineWidth: 1)
        // Footer rule and dividers.
        stroke(c, line(24, g.footerY - 10, w - 24, g.footerY - 10), "#F3D9C8", 1, 0.08)
        for i in 1..<3 {
            let lx = w * Double(i) / 3
            stroke(c, line(lx, g.footerY + 6, lx, g.footerY + 40), "#F3D9C8", 1, 0.1)
        }
    }

    /// The button's plate: shadows, a deep cocoa disc with a lit rim, the thermometer's track and ticks.
    static func plate(_ c: GraphicsContext, _ g: Geo) {
        let R = g.D / 2
        let bx = g.bx, by = g.by
        blurred(c, oval(bx, by + 20, R * 0.98, R * 0.98), "#040102", 0.55, 26)
        blurred(c, oval(bx, by + 2, R, R), "#040102", 0.5, 3)
        c.fill(oval(bx, by, R, R), with: radial(bx - R * 0.3, by - R * 0.45, R * 1.6, ["#56281E", "#3A1914", "#24100D", "#170A08"], [0, 0.35, 0.7, 1]))
        var gc = c
        gc.clip(to: oval(bx, by, R, R))
        grain(gc, CGRect(x: bx - R, y: by - R, width: R * 2, height: R * 2), 0.06)
        c.stroke(oval(bx, by, R - 0.75, R - 0.75), with: linear(0, by - R, 0, by + R, ["rgba(255,200,165,0.42)", "rgba(255,200,165,0.08)", "rgba(0,0,0,0.4)"], [0, 0.45, 1]), lineWidth: 1.5)
        // Recessed track.
        let ar = g.arcR
        stroke(c, arc(bx, by, ar, ar, arcStart, arcSweep), "#0D0504", 9)
        stroke(c, arc(bx, by + 1.2, ar, ar, arcStart + 4, arcSweep - 8), "#FFC8A8", 1, 0.07)
        for i in 0...10 {
            let a = (arcStart + arcSweep * Double(i) / 10) * .pi / 180
            let r0 = ar + 9
            let r1 = ar + (i % 5 == 0 ? 14 : 12)
            stroke(c, line(bx + cos(a) * r0, by + sin(a) * r0, bx + cos(a) * r1, by + sin(a) * r1), "#F2C9A8", 1.2, i % 5 == 0 ? 0.4 : 0.2)
        }
    }

    /// The copper pan seen from above at an angle: front wall, rim, dark seasoned floor, a walnut handle.
    static func pan(_ c: GraphicsContext, _ g: Geo) {
        let px = g.px, py = g.py, rx = g.rx, ry = g.ry, wall = g.wall, D = g.D
        // Contact shadow on the plate.
        blurred(c, oval(px + 2, py + wall + ry * 0.2, rx * 1.02, ry * 0.9), "#050102", 0.55, 8)
        // Handle: brass collar and walnut grip, angled up to the left.
        var hc = c
        hc.translateBy(x: px - rx * 0.96, y: py + ry * 0.12)
        hc.rotate(by: .degrees(-10))
        let len = D * 0.17
        hc.fill(rrect(-len, -6.5, len - 8, 13, 6.5), with: linear(0, -7, 0, 7, ["#8A5634", "#5B3320", "#3A1F13"], [0, 0.45, 1]))
        stroke(hc, line(-len + 7, -3.5, -14, -3.5), "#D9A47A", 1.2, 0.35)
        hc.fill(rrect(-12, -5, 14, 10, 2), with: linear(0, -5, 0, 5, ["#F2D08A", "#B8863C", "#6E4A1C"]))
        fill(hc, oval(-6, 0, 1.6, 1.6), "#FFF0C8", 0.7)
        // Front wall: from the rim down to the base ellipse.
        var body = Path()
        body.move(to: CGPoint(x: px - rx - 3, y: py))
        body.addLine(to: CGPoint(x: px - rx * 0.95, y: py + wall))
        arcTo(&body, px, py + wall, rx * 0.95, ry * 0.95, 180, -180)
        body.addLine(to: CGPoint(x: px + rx + 3, y: py))
        body.closeSubpath()
        c.fill(body, with: linear(px - rx, 0, px + rx, 0, ["#5E2A15", "#B8693F", "#E7A475", "#A9592F", "#4E220F"], [0, 0.28, 0.42, 0.7, 1]))
        c.fill(body, with: linear(0, py, 0, py + wall + ry, ["rgba(0,0,0,0)", "rgba(20,6,2,0.45)"]))
        // Rim: a thick rolled copper lip.
        c.fill(oval(px, py, rx + 3, ry + 2.5), with: linear(px - rx, py - ry, px + rx, py + ry, ["#F6C79B", "#C9794A", "#8C4423", "#D88B58", "#F0B888"], [0, 0.3, 0.55, 0.8, 1]))
        // Floor: dark seasoned steel with an oil sheen.
        c.fill(oval(px, py + 1, rx - 3, ry - 2.5), with: radial(px - rx * 0.15, py - ry * 0.2, rx * 1.1, ["#4A4340", "#2E2927", "#1B1716", "#141110"], [0, 0.4, 0.8, 1]))
        for i in 1...4 {
            let k = Double(i) / 5
            stroke(c, oval(px, py + 1, (rx - 3) * k, (ry - 2.5) * k), "#8D817A", 0.6, 0.06)
        }
        // Inner wall: the far side of the pan's inside catches light, the near side is in shadow.
        c.stroke(oval(px, py + 1.5, rx - 4.5, ry - 4), with: linear(0, py - ry, 0, py + ry, ["#C47A4E", "#6B3418", "#2A120A"], [0, 0.45, 1]), lineWidth: 4)
        stroke(c, arc(px, py, rx + 1.5, ry + 1.2, 200, 110), "#FFE2C4", 1.4, 0.55)
        stroke(c, arc(px - rx * 0.1, py - ry * 0.05, rx * 0.55, ry * 0.4, 200, 70), "#FFFFFF", 3, 0.05)
    }

    /// A glass lid: steel rim, a faint dome, two curved specular streaks, a bakelite knob.
    static func lid(_ c: GraphicsContext, _ g: Geo) {
        let px = g.px, py = g.py, dome = g.dome
        let lx = g.rx * 1.05
        let ly = g.ry * 1.05
        let top = py - (ly + dome * 0.45) * 1.33
        var outline = Path()
        outline.move(to: CGPoint(x: px - lx, y: py))
        outline.addCurve(to: CGPoint(x: px + lx, y: py), control1: CGPoint(x: px - lx, y: top), control2: CGPoint(x: px + lx, y: top))
        var domePath = outline
        arcTo(&domePath, px, py, lx, ly, 0, 180)
        domePath.closeSubpath()
        c.fill(domePath, with: radial(px - lx * 0.3, py - dome - ly * 0.2, lx * 1.4, ["rgba(255,244,230,0.16)", "rgba(220,235,245,0.07)", "rgba(200,220,235,0.03)"], [0, 0.5, 1]))
        // A broad soft reflection of the room across the far half of the dome.
        var dc = c
        dc.clip(to: domePath)
        blurred(dc, oval(px - lx * 0.2, py - ly * 0.75 - dome * 0.4, lx * 0.85, ly * 0.55), "#FFF1E4", 0.12, 10)
        // The dome's silhouette and a darker band where it meets the rim.
        stroke(c, outline, "#F4F8FF", 1.2, 0.22)
        stroke(c, arc(px, py - dome * 0.18, lx * 0.96, ly * 0.96, 185, 170), "#0B0E12", 2.4, 0.12)
        // Streaks follow the dome's curve.
        var streak = Path()
        streak.move(to: CGPoint(x: px - lx * 0.78, y: py - ly * 0.1))
        streak.addCurve(to: CGPoint(x: px + lx * 0.08, y: py - dome * 1.05 - ly * 0.42),
                        control1: CGPoint(x: px - lx * 0.74, y: py - dome - ly * 0.45),
                        control2: CGPoint(x: px - lx * 0.25, y: py - dome * 1.15 - ly * 0.45))
        var sc = c
        sc.addFilter(.blur(radius: 1.1))
        stroke(sc, streak, "#FFFFFF", 3.2, 0.42)
        var streak2 = Path()
        streak2.move(to: CGPoint(x: px + lx * 0.5, y: py - dome * 0.7 - ly * 0.3))
        streak2.addQuadCurve(to: CGPoint(x: px + lx * 0.8, y: py - ly * 0.05), control: CGPoint(x: px + lx * 0.72, y: py - dome * 0.35 - ly * 0.2))
        stroke(c, streak2, "#FFFFFF", 1.6, 0.3)
        // Rim.
        c.stroke(oval(px, py, lx, ly), with: linear(px - lx, py - ly, px + lx, py + ly, ["#F4F6F8", "#9AA1A9", "#5D646C", "#C3C9CF", "#EEF1F4"], [0, 0.3, 0.55, 0.8, 1]), lineWidth: 3.2)
        stroke(c, arc(px, py - 1, lx, ly, 190, 120), "#FFFFFF", 1, 0.6)
        // Knob on top.
        let kx = px
        let ky = py - dome * 1.15
        blurred(c, oval(kx, ky + 5, 11, 4), "#000000", 0.35, 2.5)
        fill(c, Path(CGRect(x: kx - 3.5, y: ky - 1, width: 7, height: 6)), "#8E959C")
        c.fill(oval(kx, ky - 3, 11, 6.5), with: radial(kx - 3, ky - 6, 13, ["#5A5352", "#2A2423", "#151111"], [0, 0.5, 1]))
        stroke(c, arc(kx, ky - 4, 8, 4, 200, 90), "#FFFFFF", 1.2, 0.45)
    }

    /// One corn: craggy puffs in cream and butter, a sliver of hull, a soft shadow. Drawn around (0, 0).
    static func corn(_ c: GraphicsContext, _ R: Double, _ v: Int) {
        let hash = PopcornPopButtonMotion.hash
        let dv = Double(v)
        blurred(c, oval(R * 0.06, R * 0.2, R * 0.9, R * 0.9), "#1A0703", 0.3, R * 0.16)
        var s = c
        s.scaleBy(x: 1 + 0.16 * hash(dv + 41), y: 1 - 0.08 * hash(dv + 43))
        let n = 7 + v % 3
        var lobes: [(Double, Double, Double)] = []
        for k in 0..<n {
            let dk = Double(k)
            let a = dk / Double(n) * .pi * 2 + hash(dv * 13 + dk) * 0.9
            let d = R * (0.3 + 0.32 * hash(dv * 7 + dk + 1))
            lobes.append((cos(a) * d, sin(a) * d * 0.9, R * (0.22 + 0.3 * hash(dv * 5 + dk + 2))))
        }
        lobes.append((-R * 0.05, -R * 0.08, R * 0.4))
        let idx = lobes.indices.sorted { (lobes[$0].1 + lobes[$0].2 * 0.3) < (lobes[$1].1 + lobes[$1].2 * 0.3) }
        for (j, k) in idx.enumerated() {
            let (x, y, r) = lobes[k]
            if j == Int(floor(Double(idx.count) * 0.6)) {
                let hc = rotated(s, -18 + 40 * hash(dv + 3), R * 0.2, R * 0.3)
                hc.fill(Path(ellipseIn: CGRect(x: 0, y: R * 0.2, width: R * 0.42, height: R * 0.22)),
                        with: linear(R * 0.05, R * 0.2, R * 0.4, R * 0.44, ["#D58E36", "#94501A", "#6E3810"]))
            }
            let butter = (v + k) % 3 == 0
            s.fill(oval(x, y, r, r), with: radial(x - r * 0.4, y - r * 0.45, r * 1.55,
                                                  butter ? ["#FFF6D6", "#F5DE9C", "#E2BC72", "#B88C4A"] : ["#FFFBF1", "#F4E5C6", "#E2C691", "#B99560"],
                                                  [0, 0.38, 0.72, 1]))
            // A darker crease on the lower right where the next puff folds in.
            stroke(s, arc(x, y, r, r, 10, 120), "#8E6232", max(0.5, r * 0.12), 0.28)
            blurred(s, oval(x - r * 0.38, y - r * 0.42, r * 0.2, r * 0.2), "#FFFFFF", 0.45, r * 0.12)
        }
    }

    /// One kernel: an amber teardrop with a pale tip. Drawn around (0, 0).
    static func kernel(_ c: GraphicsContext, _ L: Double, _ v: Int) {
        let w = L * (0.74 + 0.08 * Double(v % 2))
        blurred(c, Path(ellipseIn: CGRect(x: -w * 0.55, y: L * 0.15, width: w * 1.1, height: L * 0.5)), "#050101", 0.5, L * 0.12)
        var drop = Path()
        drop.move(to: CGPoint(x: 0, y: -L / 2))
        drop.addCurve(to: CGPoint(x: 0, y: L / 2), control1: CGPoint(x: w * 0.62, y: -L * 0.28), control2: CGPoint(x: w * 0.52, y: L / 2))
        drop.addCurve(to: CGPoint(x: 0, y: -L / 2), control1: CGPoint(x: -w * 0.52, y: L / 2), control2: CGPoint(x: -w * 0.62, y: -L * 0.28))
        drop.closeSubpath()
        c.fill(drop, with: linear(-w / 2, 0, w / 2, L * 0.2, v == 3 ? ["#F2B957", "#D78A2A", "#9C5416"] : ["#F9CF63", "#E59B30", "#AE5F18"], [0, 0.5, 1]))
        fill(c, Path(ellipseIn: CGRect(x: -w * 0.2, y: -L * 0.5, width: w * 0.4, height: L * 0.26)), "#F8EBC8", 0.75)
        stroke(c, line(-w * 0.22, -L * 0.18, -w * 0.18, L * 0.22), "#FFFFFF", L * 0.08, 0.45)
    }

    /// A red-and-cream striped bowl with a dome of popcorn on it.
    static func bowl(_ c: GraphicsContext, _ g: Geo) {
        let x = g.bowlX
        let y = g.bowlY
        let R = 6.5
        let dome: [Double] = [-11, 0, 1, -4, -5, 2, 4, -6, 3, 11, -1, 5, 0, 2, 0, -6, 3, 4, 7, 4, 1]
        for i in stride(from: 0, to: dome.count, by: 3) {
            var cc = c
            cc.translateBy(x: x + dome[i], y: y + dome[i + 1] - 1)
            cc.scaleBy(x: 0.95, y: 0.95)
            corn(cc, R, Int(dome[i + 2]))
        }
        let tw = 44.0
        let bw = 30.0
        let hh = 38.0
        var body = Path()
        body.move(to: CGPoint(x: x - tw / 2, y: y))
        body.addLine(to: CGPoint(x: x + tw / 2, y: y))
        body.addLine(to: CGPoint(x: x + bw / 2, y: y + hh))
        body.addQuadCurve(to: CGPoint(x: x - bw / 2, y: y + hh), control: CGPoint(x: x, y: y + hh + 3))
        body.closeSubpath()
        blurred(c, Path(ellipseIn: CGRect(x: x - bw / 2 - 2, y: y + hh - 3, width: bw + 4, height: 8)), "#050102", 0.5, 3)
        var bc = c
        bc.clip(to: body)
        let full = Path(CGRect(x: x - tw, y: y, width: tw * 2, height: hh + 6))
        fill(bc, full, "#F2E4CC")
        for s in stride(from: -3, through: 3, by: 2) {
            let t0 = Double(s) * (tw / 7)
            let b0 = Double(s) * (bw / 7)
            var stripe = Path()
            stripe.move(to: CGPoint(x: x + t0, y: y))
            stripe.addLine(to: CGPoint(x: x + t0 + tw / 7, y: y))
            stripe.addLine(to: CGPoint(x: x + b0 + bw / 7, y: y + hh + 4))
            stripe.addLine(to: CGPoint(x: x + b0, y: y + hh + 4))
            stripe.closeSubpath()
            fill(bc, stripe, "#C93A33")
        }
        bc.fill(full, with: linear(x - tw / 2, 0, x + tw / 2, 0, ["rgba(40,6,6,0.5)", "rgba(255,255,255,0.12)", "rgba(40,6,6,0.05)", "rgba(40,6,6,0.55)"], [0, 0.3, 0.6, 1]))
        c.fill(rrect(x - tw / 2 - 2, y - 2, tw + 4, 6, 3), with: linear(0, y - 2, 0, y + 5, ["#FFF7E8", "#E6D2B0", "#B79C74"]))
    }

    /// The rect each static layer is rasterised in (as the React Native bakes).
    static func rect(_ kind: PopcornPopButtonLayer.Kind, _ g: Geo) -> CGRect {
        switch kind {
        case .room:
            return CGRect(x: 0, y: 0, width: g.w, height: g.h)
        case .plate:
            let R = g.D / 2 + 34
            return CGRect(x: g.bx - R, y: g.by - R, width: R * 2, height: R * 2 + 16)
        case .pan:
            let panX = g.px - g.rx - g.D * 0.22
            return CGRect(x: panX, y: g.py - g.ry - 8, width: g.px + g.rx + 14 - panX, height: g.ry * 2 + g.wall + 30)
        case .lid:
            let lw = g.rx * 1.05 + 6
            return CGRect(x: g.px - lw, y: g.py - g.dome - g.ry * 1.05 - 16, width: lw * 2, height: g.dome + g.ry * 2.1 + 26)
        case .bowl:
            return CGRect(x: g.bowlX - 28, y: g.bowlY - 22, width: 56, height: 70)
        }
    }

    // MARK: Sprites

    /// The cell a sprite is drawn in (pt).
    static func cell(_ g: Geo) -> Double { g.D * 0.125 }

    /// 4 kernels and 6 corns, rendered once.
    @MainActor
    static func sprites(_ g: Geo) -> [Image] {
        let cell = cell(g)
        return (0..<10).map { v in
            let view = Canvas { c, _ in
                var cc = c
                cc.translateBy(x: cell / 2, y: cell / 2)
                if v < 4 { kernel(cc, g.D * 0.03, v) } else { corn(cc, g.D * 0.05, v - 4) }
            }
            .frame(width: cell, height: cell)
            let renderer = ImageRenderer(content: view)
            renderer.scale = 3
            if let image = renderer.uiImage {
                return Image(uiImage: image).interpolation(.high)
            }
            return Image(systemName: "circle")
        }
    }

    static func sprite(_ c: GraphicsContext, _ image: GraphicsContext.ResolvedImage, _ cell: Double, _ x: Double, _ y: Double, _ rot: Double, _ sx: Double, _ sy: Double, alpha: Double = 1) {
        var s = c
        s.translateBy(x: x, y: y)
        s.rotate(by: .degrees(rot))
        s.scaleBy(x: sx, y: sy)
        if alpha < 1 { s.opacity = alpha }
        s.draw(image, in: CGRect(x: -cell / 2, y: -cell / 2, width: cell, height: cell))
    }

    // MARK: Per frame

    static let arcShading: (Geo) -> GraphicsContext.Shading = { g in
        .conicGradient(gradient(["#FFD86E", "#FF9F3E", "#F45A2F", "#D8302A", "#D8302A"], [0, 110.0 / 360, 210.0 / 360, arcSweep / 360, 1]),
                       center: CGPoint(x: g.bx, y: g.by), angle: .degrees(arcStart))
    }

    /// The thermometer arc filling with the heat.
    static func heatArc(_ c: GraphicsContext, _ g: Geo, _ p: Pose) {
        guard p.heat > 0.004 else { return }
        let sweep = arcSweep * p.heat
        let a = arc(g.bx, g.by, g.arcR, g.arcR, arcStart, sweep)
        var glow = c
        glow.addFilter(.blur(radius: 6))
        stroke(glow, a, "#FF7A2E", 13, 0.5 * p.heat)
        c.stroke(a, with: arcShading(g), style: StrokeStyle(lineWidth: 6, lineCap: .round))
        let e = (arcStart + sweep) * .pi / 180
        fill(c, oval(g.bx + cos(e) * g.arcR, g.by + sin(e) * g.arcR, 2.2, 2.2), "#FFF4DA", 0.9)
    }

    /// The orange glow under the pan.
    static func panGlow(_ c: GraphicsContext, _ g: Geo, _ p: Pose) {
        guard p.heat > 0.004 else { return }
        var gc = c
        gc.opacity = 0.75 * p.heat
        gc.fill(oval(g.px, g.py + g.wall, g.rx * 1.35, g.ry * 1.5),
                with: radial(g.px, g.py + g.wall, g.rx * 1.35, ["rgba(255,128,46,0.9)", "rgba(255,92,34,0.35)", "rgba(255,60,20,0)"], [0, 0.5, 1]))
    }

    /// The warm tint inside the pan, the kernels, the hull flecks and the corns.
    static func inside(_ c: GraphicsContext, _ g: Geo, _ p: Pose, _ sprites: [GraphicsContext.ResolvedImage]) {
        if p.heat > 0.004 {
            var tc = c
            tc.opacity = 0.55 * p.heat
            tc.blendMode = .screen
            tc.fill(oval(g.px, g.py + 1, g.rx - 3, g.ry - 2.5),
                    with: radial(g.px, g.py + g.ry * 0.2, g.rx, ["rgba(255,120,50,0.5)", "rgba(255,90,40,0.15)", "rgba(255,80,30,0)"], [0, 0.6, 1]))
        }
        guard sprites.count == 10 else { return }
        let cell = cell(g)
        for k in p.kernels {
            sprite(c, sprites[k.v], cell, k.x, k.y, k.rot, 1, 1, alpha: k.alpha)
        }
        if !p.flecks.isEmpty {
            let fleck = col("#8A4A18")
            for f in p.flecks {
                c.fill(Path(ellipseIn: CGRect(x: f.x - 1.6, y: f.y - 1, width: 3.2, height: 2)), with: .color(fleck.opacity(f.alpha)))
            }
        }
        for k in p.corns {
            sprite(c, sprites[4 + k.v], cell, k.x, k.y, k.rot, k.sx, k.sy)
        }
    }

    /// Corns on their way to the bowl.
    static func flying(_ c: GraphicsContext, _ g: Geo, _ p: Pose, _ sprites: [GraphicsContext.ResolvedImage]) {
        guard sprites.count == 10 else { return }
        let cell = cell(g)
        for f in p.flying {
            sprite(c, sprites[4 + f.v], cell, f.x, f.y, f.rot, f.scale, f.scale)
        }
    }
}

/// One static layer, drawn once in its own rect and rasterised; it only moves as a whole.
struct PopcornPopButtonLayer: View, Equatable {
    enum Kind { case room, plate, pan, lid, bowl }
    let kind: Kind
    let g: PopcornPopButtonMotion.Geo
    let top: Double

    var body: some View {
        let r = PopcornPopButtonArt.rect(kind, g)
        Canvas { c, _ in
            var cc = c
            cc.translateBy(x: -r.minX, y: -r.minY)
            switch kind {
            case .room: PopcornPopButtonArt.room(cc, g, top: top)
            case .plate: PopcornPopButtonArt.plate(cc, g)
            case .pan: PopcornPopButtonArt.pan(cc, g)
            case .lid: PopcornPopButtonArt.lid(cc, g)
            case .bowl: PopcornPopButtonArt.bowl(cc, g)
            }
        }
        .frame(width: r.width, height: r.height)
        .drawingGroup()
    }
}
