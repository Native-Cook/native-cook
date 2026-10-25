import SwiftUI
import UIKit

/*
 Music Box Player — the drawing. Static, drawn once into a rasterised view: a violet night room
 with a warm lamp glow, the walnut box with its lid open (velvet panel, gold inlay, an oval mirror),
 the velvet well, the brass bed plate and bearings, the brass cylinder, the comb's base, the front
 face with an escutcheon and the crank's hub, and the player panel's plate. Per frame (one Canvas):
 the cylinder's seams and pins (on its visible half, shaded by their angle), the 18 teeth (tips
 bent by the pins, ringing), a warm glow at each plucked tip, floating notes, the governor's fan,
 the crank, and the playhead.
 */
enum MusicBoxPlayerArt {
    typealias Geo = MusicBoxPlayerMotion.Geo
    typealias Pose = MusicBoxPlayerMotion.Pose
    private typealias M = MusicBoxPlayerMotion

    static func layout(w: Double, h: Double, top: Double, bottom: Double) -> Geo {
        let panelH = 176.0
        let panelY = h - max(bottom, 12) - panelH
        let lidTop = top + 110
        let s = max(0.72, min(1.1, (panelY - 18 - lidTop) / 430))
        let boxX = 14.0
        let boxW = w - 28
        let rimY = lidTop + 150 * s
        let frontY = rimY + 196 * s
        let x0 = boxX + 14
        let x1 = boxX + boxW - 14
        let cylR = 25 * s
        let cylY = rimY + 24 * s + cylR
        let tipY = cylY + cylR * sin(M.contact)
        let frontH = 64 * s
        return Geo(w: w, h: h, cylX0: x0 + 40, cylX1: x1 - 22, cylY: cylY, cylR: cylR, tipY: tipY, combY: tipY + 52 * s,
                   hubX: boxX + boxW - 50, hubY: frontY + frontH / 2, arm: 30 * s, fanX: x0 + 20, fanY: cylY,
                   barX: 36, barY: panelY + 58, barW: w - 72, s: s, boxX: boxX, boxW: boxW, lidTop: lidTop, rimY: rimY,
                   frontY: frontY, frontH: frontH, x0: x0, x1: x1, panelY: panelY, panelH: panelH)
    }

    // MARK: Helpers

    /// "#RRGGBB" or "rgba(r,g,b,a)".
    static func col(_ s: String) -> Color {
        if s.hasPrefix("#") { return Color(hex: UInt32(s.dropFirst(), radix: 16) ?? 0) }
        let parts = s.drop { $0 != "(" }.dropFirst().prefix { $0 != ")" }.split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard parts.count == 4 else { return .clear }
        return Color.rgba(parts[0], parts[1], parts[2], parts[3])
    }

    static func gradient(_ list: [String], _ pos: [Double]? = nil) -> Gradient {
        let colors = list.map(col)
        guard let pos, pos.count == colors.count else { return Gradient(colors: colors) }
        return Gradient(stops: zip(colors, pos).map { Gradient.Stop(color: $0, location: $1) })
    }

    static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ list: [String], _ pos: [Double]? = nil) -> GraphicsContext.Shading {
        .linearGradient(gradient(list, pos), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    static func radial(_ x: Double, _ y: Double, _ r: Double, _ list: [String], _ pos: [Double]? = nil) -> GraphicsContext.Shading {
        .radialGradient(gradient(list, pos), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: max(0.1, r))
    }

    static func oval(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
    }

    static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        Path(CGRect(x: x, y: y, width: w, height: h))
    }

    static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
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

    static let grainTile: Image = {
        let side = 96
        var bytes = [UInt8](repeating: 255, count: side * side * 4)
        var seed: UInt32 = 0x85EBCA6B
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

    static func grain(_ c: GraphicsContext, _ r: CGRect, _ alpha: Double) {
        var g = c
        g.blendMode = .overlay
        g.opacity = alpha
        g.fill(Path(r), with: .tiledImage(grainTile, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    static func walnut(_ c: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ seed: Double) {
        c.fill(rect(x, y, w, h), with: linear(x, y, x, y + h, ["#6A3E24", "#55301B", "#40230F"]))
        var cc = c
        cc.clip(to: rect(x, y, w, h))
        var dark = Path()
        var light = Path()
        for i in 0..<Int((h / 4).rounded()) {
            let di = Double(i)
            var b = Path()
            let yy = y + di * 4 + 2
            b.move(to: CGPoint(x: x, y: yy))
            for k in 1...10 {
                b.addLine(to: CGPoint(x: x + w * Double(k) / 10, y: yy + sin(Double(k) * 0.9 + di * 1.7 + seed) * 1.6))
            }
            if i % 3 == 0 { dark.addPath(b) } else { light.addPath(b) }
        }
        stroke(cc, dark, "#2A140A", 0.7, 0.35)
        stroke(cc, light, "#7C4A2C", 0.7, 0.35)
    }

    static func screw(_ c: GraphicsContext, _ x: Double, _ y: Double, _ r: Double) {
        c.fill(oval(x, y, r, r), with: radial(x - r * 0.4, y - r * 0.4, r * 1.6, ["#FFF0BE", "#C89A45", "#6E4F1C"]))
        stroke(c, line(x - r * 0.6, y + r * 0.3, x + r * 0.6, y - r * 0.3), "#4A3410", 0.8)
    }

    static let timeFont = Font.system(size: 12, weight: .semibold, design: .monospaced)

    /// Draws a line of text with its baseline at y; `anchor` 0 = left edge at x, 1 = right edge.
    static func text(_ c: GraphicsContext, _ s: String, _ color: String, _ alpha: Double, _ x: Double, _ y: Double, anchor: Double = 0) {
        let t = c.resolve(Text(s).font(timeFont).foregroundColor(col(color).opacity(alpha)))
        let big = CGSize(width: 1000, height: 200)
        let size = t.measure(in: big)
        let base = t.firstBaseline(in: big)
        c.draw(t, in: CGRect(x: x - size.width * anchor, y: y - base, width: size.width, height: size.height))
    }

    // MARK: Static scene

    static func scene(_ c: GraphicsContext, _ g: Geo) {
        let w = g.w, h = g.h, s = g.s
        let all = rect(0, 0, w, h)
        c.fill(all, with: linear(0, 0, 0, h, ["#231D3A", "#1B1730", "#141126", "#0E0C1C"], [0, 0.4, 0.75, 1]))
        c.fill(all, with: radial(w * 0.5, g.rimY, w * 0.95, ["rgba(255,190,120,0.26)", "rgba(180,120,120,0.1)", "rgba(40,30,70,0)"], [0, 0.5, 1]))
        // Soft bokeh from the night-light, out of focus behind the box.
        for i in 0..<7 {
            let di = Double(i)
            let x = w * (0.08 + 0.84 * abs(sin(di * 7.31 + 0.4)))
            let y = g.lidTop * (0.35 + 0.6 * abs(sin(di * 3.17 + 1)))
            let r = 10 + 18 * abs(sin(di * 1.3))
            blurred(c, oval(x, y, r, r), i % 2 == 0 ? "#F3C987" : "#B9A6F0", 0.1, 8)
        }
        let bx = g.boxX, bw = g.boxW
        // Shadow under the box.
        blurred(c, Path(ellipseIn: CGRect(x: bx - 4, y: g.frontY + g.frontH - 10, width: bw + 8, height: 34)), "#05040A", 0.7, 14)
        // The lid, standing open behind the box: a trapezoid (tilted back), walnut frame, velvet inside.
        let inset = bw * 0.035
        var lid = Path()
        lid.move(to: CGPoint(x: bx + inset, y: g.lidTop))
        lid.addLine(to: CGPoint(x: bx + bw - inset, y: g.lidTop))
        lid.addLine(to: CGPoint(x: bx + bw, y: g.rimY))
        lid.addLine(to: CGPoint(x: bx, y: g.rimY))
        lid.closeSubpath()
        var lc = c
        lc.clip(to: lid)
        walnut(lc, bx, g.lidTop, bw, g.rimY - g.lidTop, 3)
        let pad = 12 * s
        var vel = Path()
        vel.move(to: CGPoint(x: bx + inset + pad, y: g.lidTop + pad))
        vel.addLine(to: CGPoint(x: bx + bw - inset - pad, y: g.lidTop + pad))
        vel.addLine(to: CGPoint(x: bx + bw - pad, y: g.rimY - pad * 0.5))
        vel.addLine(to: CGPoint(x: bx + pad, y: g.rimY - pad * 0.5))
        vel.closeSubpath()
        c.fill(vel, with: radial(bx + bw * 0.4, g.lidTop + 20, bw * 0.8, ["#4C3A78", "#352866", "#241A48"], [0, 0.5, 1]))
        var vc = c
        vc.clip(to: vel)
        grain(vc, CGRect(x: bx, y: g.lidTop, width: bw, height: g.rimY - g.lidTop), 0.12)
        stroke(c, vel, "#D9B56A", 1.4, 0.8)
        // The oval mirror, catching the lamp.
        let mx = g.w / 2
        let my = (g.lidTop + g.rimY) / 2
        let mrx = bw * 0.2
        let mry = (g.rimY - g.lidTop) * 0.3
        c.fill(oval(mx, my, mrx + 4, mry + 4), with: linear(0, my - mry, 0, my + mry, ["#F5DA94", "#B98B3C", "#7A5520"]))
        let mirror = oval(mx, my, mrx, mry)
        c.fill(mirror, with: linear(mx - mrx, my - mry, mx + mrx, my + mry, ["#9AA4C8", "#5A5F8A", "#2C2E55", "#6E6A98"], [0, 0.35, 0.7, 1]))
        var mc = c
        mc.clip(to: mirror)
        mc.fill(rect(mx - mrx * 0.6, my - mry, mrx * 0.35, mry * 2), with: linear(mx - mrx * 0.6, 0, mx - mrx * 0.25, 0, ["rgba(255,255,255,0)", "rgba(255,255,255,0.3)", "rgba(255,255,255,0)"]))
        blurred(mc, oval(mx + mrx * 0.3, my + mry * 0.2, mry * 0.45, mry * 0.45), "#FFC98A", 0.35, 10)
        // Brass hinges.
        for hx in [bx + bw * 0.22, bx + bw * 0.78] {
            c.fill(rect(hx - 16, g.rimY - 5, 32, 8), with: linear(0, g.rimY - 5, 0, g.rimY + 3, ["#F4D78C", "#A7782E", "#6B4A18"]))
        }
        // Box rim and the velvet well.
        walnut(c, bx, g.rimY, bw, g.frontY - g.rimY, 9)
        stroke(c, line(bx, g.rimY + 0.5, bx + bw, g.rimY + 0.5), "#E8B888", 1, 0.5)
        let well = rr(g.x0, g.rimY + 10 * s, g.x1 - g.x0, g.frontY - g.rimY - 18 * s, 6)
        c.fill(well, with: linear(0, g.rimY, 0, g.frontY, ["#2A1E4C", "#3A2A66", "#2E2254"]))
        var wc = c
        wc.clip(to: well)
        grain(wc, CGRect(x: g.x0, y: g.rimY, width: g.x1 - g.x0, height: g.frontY - g.rimY), 0.14)
        blurred(wc, rr(g.x0 - 10, g.rimY + 2 * s, g.x1 - g.x0 + 20, 18 * s, 6), "#0C0818", 0.7, 8)
        // Brass bed plate with screws, and the bearings at the cylinder's ends.
        let py0 = g.cylY - g.cylR - 10 * s
        let py1 = g.combY + 26 * s
        blurred(c, rr(g.cylX0 - 12, py0 + 3, g.cylX1 - g.cylX0 + 24, py1 - py0, 5), "#0A0614", 0.6, 5)
        c.fill(rr(g.cylX0 - 12, py0, g.cylX1 - g.cylX0 + 24, py1 - py0, 5), with: linear(0, py0, 0, py1, ["#D8B56C", "#B58C44", "#8C6628"]))
        stroke(c, rr(g.cylX0 - 12, py0, g.cylX1 - g.cylX0 + 24, py1 - py0, 5), "#FFE9B0", 0.8, 0.5)
        screw(c, g.cylX0 - 4, py1 - 8 * s, 3 * s)
        screw(c, g.cylX1 + 4, py1 - 8 * s, 3 * s)
        for ex in [g.cylX0 - 6, g.cylX1 + 6] {
            c.fill(rect(ex - 5, g.cylY - g.cylR - 6, 10, g.cylR * 2 + 12), with: linear(ex - 5, 0, ex + 5, 0, ["#8C6628", "#F0D08A", "#7A5520"]))
            fill(c, oval(ex, g.cylY, 3, 3), "#4A3410")
        }
        // The cylinder: brass, lit from above.
        let cyl = CGRect(x: g.cylX0, y: g.cylY - g.cylR, width: g.cylX1 - g.cylX0, height: g.cylR * 2)
        c.fill(Path(cyl), with: linear(0, g.cylY - g.cylR, 0, g.cylY + g.cylR, ["#5E4316", "#C99D4C", "#F6DE9A", "#D9AE5C", "#8A6224", "#3F2B0C"], [0, 0.18, 0.34, 0.5, 0.78, 1]))
        grain(c, cyl, 0.12)
        // The comb's base: a steel block with screws.
        let cb = g.combY
        c.fill(rr(g.cylX0 - 2, cb, g.cylX1 - g.cylX0 + 4, 16 * s, 3), with: linear(0, cb, 0, cb + 16 * s, ["#E9EEF2", "#9AA4AE", "#5C656F"]))
        for k in 0..<5 { screw(c, g.cylX0 + 14 + (g.cylX1 - g.cylX0 - 28) * Double(k) / 4, cb + 8 * s, 2.6 * s) }
        // Front face: walnut with an inlay band, a keyhole escutcheon and the crank's hub plate.
        walnut(c, bx, g.frontY, bw, g.frontH, 13)
        c.fill(rect(bx, g.frontY, bw, g.frontH), with: linear(0, g.frontY, 0, g.frontY + g.frontH, ["rgba(255,220,180,0.1)", "rgba(0,0,0,0)", "rgba(10,4,0,0.35)"]))
        stroke(c, line(bx, g.frontY + 0.5, bx + bw, g.frontY + 0.5), "#F0C090", 1, 0.45)
        stroke(c, line(bx + 16, g.frontY + g.frontH * 0.5, g.hubX - 44, g.frontY + g.frontH * 0.5), "#D9B56A", 1.2, 0.6)
        let ex = g.w * 0.42
        let ey = g.frontY + g.frontH * 0.5
        c.fill(Path(ellipseIn: CGRect(x: ex - 13, y: ey - 17, width: 26, height: 34)), with: linear(0, ey - 17, 0, ey + 17, ["#F6DA90", "#B98B3C", "#6E4D1A"]))
        fill(c, oval(ex, ey - 4, 3.2, 3.2), "#1A0E05")
        fill(c, rect(ex - 1.4, ey - 4, 2.8, 10), "#1A0E05")
        blurred(c, oval(g.hubX, g.hubY, 16 * s, 16 * s), "#0A0408", 0.5, 4)
        c.fill(oval(g.hubX, g.hubY, 14 * s, 14 * s), with: radial(g.hubX - 4, g.hubY - 5, 18 * s, ["#FBE6A8", "#C2913E", "#6E4D1A"]))
        // Player panel plate.
        blurred(c, rr(16, g.panelY + 10, w - 32, g.panelH, 24), "#05040A", 0.55, 16)
        c.fill(rr(16, g.panelY, w - 32, g.panelH, 24), with: linear(0, g.panelY, 0, g.panelY + g.panelH, ["#2C2645", "#231E3A", "#1C1830"]))
        stroke(c, rr(16.5, g.panelY + 0.5, w - 33, g.panelH - 1, 23.5), "#E9E3FF", 1, 0.1)
        fill(c, rr(g.barX, g.barY - 2, g.barW, 4, 2), "#E9E3FF", 0.14)
        text(c, "0:\(M.songSeconds)", "#E9E3FF", 0.5, g.barX + g.barW, g.barY + 22, anchor: 1)
        c.fill(all, with: radial(w / 2, h * 0.45, hypot(w, h) * 0.62, ["rgba(8,6,16,0)", "rgba(8,6,16,0.25)", "rgba(6,4,12,0.6)"], [0, 0.6, 1]))
    }

    /// An eighth note, about 16 pt tall, its head's centre at the origin.
    static let note: Path = {
        var p = Path()
        p.addEllipse(in: CGRect(x: -4.6, y: -3.2, width: 9.2, height: 6.4))
        p.move(to: CGPoint(x: 3.6, y: -1))
        p.addLine(to: CGPoint(x: 3.6, y: -16))
        p.addLine(to: CGPoint(x: 5.2, y: -16))
        p.addCurve(to: CGPoint(x: 9.5, y: -6.5), control1: CGPoint(x: 6.5, y: -12), control2: CGPoint(x: 10.5, y: -11.5))
        p.addCurve(to: CGPoint(x: 5.2, y: -11), control1: CGPoint(x: 9, y: -9.5), control2: CGPoint(x: 7, y: -10.5))
        p.addLine(to: CGPoint(x: 5.2, y: -1))
        p.closeSubpath()
        return p
    }()

    static func wrap(_ a: Double) -> Double {
        var x = M.posMod(a, 2 * .pi)
        if x > .pi { x -= 2 * .pi }
        return x
    }

    // MARK: Per frame

    static func frame(_ c: GraphicsContext, _ g: Geo, _ p: Pose) {
        let s = g.s
        // Seams on the cylinder so its turning reads between pins.
        for k in 0..<8 {
            let phi = wrap(Double(k) * .pi / 4 + p.angle)
            let cv = cos(phi)
            if cv <= 0.05 { continue }
            let y = g.cylY + g.cylR * sin(phi)
            stroke(c, line(g.cylX0, y, g.cylX1, y), "#3F2B0C", 0.7, 0.28 * cv)
        }
        // Pins.
        let pin = col("#FFF3CF")
        let pinShade = col("#3A2608")
        for i in 0..<M.pinCount {
            let phi = wrap(M.pinAngle(i) + p.angle)
            let cv = cos(phi)
            if cv <= 0.04 { continue }
            let x = M.toothX(g, M.pins[i].tooth)
            let y = g.cylY + g.cylR * sin(phi)
            let r = 1.9 * s
            let hgt = r * 2 * max(0.3, cv)
            c.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r * cv + 1.2, width: r * 2, height: hgt)), with: .color(pinShade.opacity(0.5 * cv)))
            c.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r * cv, width: r * 2, height: hgt)), with: .color(pin.opacity(0.35 + 0.65 * cv)))
        }
        // Teeth: steel strips from the base up to the cylinder, tips bent by the pins.
        let pitch = (g.cylX1 - g.cylX0 - 24) / Double(M.teeth)
        let tw = pitch * 0.64
        var teeth = Path()
        var edges = Path()
        var shades = Path()
        for j in 0..<M.teeth {
            let x = M.toothX(g, j)
            let ty = g.tipY + 1 + p.tips[j]
            let w0 = tw * (1.12 - 0.24 * Double(j) / Double(M.teeth))
            teeth.move(to: CGPoint(x: x - w0 / 2, y: g.combY + 1))
            teeth.addLine(to: CGPoint(x: x - w0 / 2 + p.tips[j] * 0.05, y: ty + 1.5))
            teeth.addQuadCurve(to: CGPoint(x: x + w0 / 2 + p.tips[j] * 0.05, y: ty + 1.5), control: CGPoint(x: x, y: ty - 1))
            teeth.addLine(to: CGPoint(x: x + w0 / 2, y: g.combY + 1))
            teeth.closeSubpath()
            edges.move(to: CGPoint(x: x - w0 / 2 + 0.7, y: g.combY))
            edges.addLine(to: CGPoint(x: x - w0 / 2 + 0.7, y: ty + 2))
            shades.move(to: CGPoint(x: x + w0 / 2 - 0.6, y: g.combY))
            shades.addLine(to: CGPoint(x: x + w0 / 2 - 0.6, y: ty + 2))
        }
        c.fill(teeth, with: linear(0, g.tipY, 0, g.combY, ["#F7FAFC", "#B7C0C9", "#7D8791"], [0, 0.4, 1]))
        stroke(c, edges, "#FFFFFF", 0.8, 0.7)
        stroke(c, shades, "#3E464E", 0.8, 0.5)
        // Warm glow where a tooth was plucked.
        for j in 0..<M.teeth where p.glow[j] >= 0.02 {
            let x = M.toothX(g, j)
            var gc = c
            gc.opacity = p.glow[j]
            gc.blendMode = .plusLighter
            gc.fill(oval(x, g.tipY, 22 * s, 22 * s), with: radial(x, g.tipY, 22 * s, ["rgba(255,214,140,0.9)", "rgba(255,180,100,0.3)", "rgba(255,160,80,0)"], [0, 0.4, 1]))
        }
        // Notes floating up.
        for n in p.notes where n.alpha >= 0.01 {
            var nc = c
            nc.translateBy(x: n.x, y: n.y)
            nc.rotate(by: .degrees(n.tilt))
            nc.scaleBy(x: n.scale * s, y: n.scale * s)
            nc.opacity = n.alpha
            nc.fill(oval(2, -6, 16, 16), with: radial(2, -6, 16, ["rgba(255,210,140,0.45)", "rgba(255,210,140,0)"]))
            fill(nc, note, "#FFF1D6")
        }
        // The governor's fan: two blades turning about a vertical axle.
        let fw = 9 * s
        let fh = 16 * s
        stroke(c, line(g.fanX, g.fanY - fh - 4, g.fanX, g.fanY + fh + 4), "#C9A050", 1.6)
        for b in 0..<2 {
            let ang = p.fan + Double(b) * .pi
            let cw = cos(ang)
            let half = abs(fw * sin(ang))
            let dir = cw >= 0 ? 1.0 : -1.0
            if half > 0.3 {
                c.fill(rect(dir > 0 ? g.fanX : g.fanX - half * 2, g.fanY - fh / 2, half * 2, fh),
                       with: linear(g.fanX, 0, g.fanX + dir * half * 2, 0, ["#FFF0C0", "#B8893A"]))
            }
        }
        // The crank: arm and ivory knob turning about the hub.
        let kx = g.hubX + cos(p.crank) * g.arm
        let ky = g.hubY + sin(p.crank) * g.arm
        stroke(c, line(g.hubX + 2, g.hubY + 3, kx + 2, ky + 3), "#0A0408", 7 * s, 0.35)
        stroke(c, line(g.hubX, g.hubY, kx, ky), "#D6AA55", 6 * s)
        stroke(c, line(g.hubX, g.hubY, kx, ky), "#FFF0C4", 1.6 * s, 0.6)
        blurred(c, oval(kx + 2, ky + 3, 10 * s, 10 * s), "#0A0408", 0.45, 3)
        c.fill(oval(kx, ky, 9.5 * s, 9.5 * s), with: radial(kx - 3, ky - 4, 13 * s, ["#FFFBF0", "#EADDC4", "#B9A27E"]))
        fill(c, oval(g.hubX, g.hubY, 5 * s, 5 * s), "#6E4D1A")
        fill(c, oval(g.hubX - 1, g.hubY - 1, 2 * s, 2 * s), "#FFF0C4", 0.8)
        // Playhead.
        let prog = min(1, max(0, p.progress))
        let px = g.barX + g.barW * prog
        c.fill(rr(g.barX, g.barY - 2, max(4, px - g.barX), 4, 2), with: linear(g.barX, 0, g.barX + g.barW, 0, ["#F3C987", "#E9A6C8"]))
        blurred(c, oval(px, g.barY, 7, 7), "#000000", 0.35, 2)
        fill(c, oval(px, g.barY, 6, 6), "#FFF6EA")
        let sec = Int(floor(prog * Double(M.songSeconds) + 1e-4))
        text(c, "0:\(sec < 10 ? "0" : "")\(sec)", "#E9E3FF", 0.75, g.barX, g.barY + 22)
    }
}

/// The room and the box, drawn once and rasterised.
struct MusicBoxPlayerScene: View, Equatable {
    let g: MusicBoxPlayerMotion.Geo

    var body: some View {
        Canvas { c, _ in MusicBoxPlayerArt.scene(c, g) }
            .frame(width: g.w, height: g.h)
            .drawingGroup()
    }
}
