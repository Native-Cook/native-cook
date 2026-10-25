import SwiftUI
import UIKit

/*
 Compass Needle Nav — the drawing. Static Canvases drawn once: the ground (night-green, the dock
 plate), four pages (Map, Trips, Gear, Me) that hang on a wheel centred on the compass and are
 rotated as views each frame, the compass base (housing, bezel with degrees, the liquid well and
 its card) and the glass dome. Every frame the timeline's Canvas draws the target glow, the
 needle with its shadow, the air bubble and the finger's bead.
*/
enum CompassNeedleNavArt {
    typealias M = CompassNeedleNavMotion

    static let tabs = ["Map", "Trips", "Gear", "Me"]
    static let accent = Color(hex: 0xF07A3A)
    static let ink = Color(hex: 0xEEF1E8)
    static let mute = Color.rgba(238, 241, 232, 0.62)
    static let faint = Color.rgba(238, 241, 232, 0.38)
    static let cardColors = [Color(hex: 0x1E2F28), Color(hex: 0x1A2923), Color(hex: 0x16231E)]

    struct Scene: Equatable {
        var w: Double
        var h: Double
        var cx: Double
        var cy: Double
        var r: Double
        var pageTop: Double
        var pageH: Double
        /// The compass is drawn in a box of this size around (cx, cy).
        var box: Double

        init(w: Double, h: Double, top: Double, bottom: Double) {
            self.w = w
            self.h = h
            r = min(104, w * 0.27, (h - top - bottom) * 0.13)
            cy = h - bottom - 46 - r
            cx = w / 2
            pageTop = top + 44
            pageH = cy - r - 72 - pageTop
            box = (r * 2 + 48).rounded(.up)
        }
    }

    // MARK: Helpers

    static func lin(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ colors: [Color], _ stops: [Double]? = nil) -> GraphicsContext.Shading {
        let pos = stops ?? colors.indices.map { colors.count > 1 ? Double($0) / Double(colors.count - 1) : 0 }
        return .linearGradient(Gradient(stops: zip(colors, pos).map { .init(color: $0, location: $1) }), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    static func rad(_ x: Double, _ y: Double, _ r: Double, _ colors: [Color], _ stops: [Double]? = nil) -> GraphicsContext.Shading {
        let pos = stops ?? colors.indices.map { colors.count > 1 ? Double($0) / Double(colors.count - 1) : 0 }
        return .radialGradient(Gradient(stops: zip(colors, pos).map { .init(color: $0, location: $1) }), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r)
    }

    static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: max(0, w), height: max(0, h)), cornerRadius: min(r, max(0, w) / 2, max(0, h) / 2), style: .circular)
    }

    static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    static func blurred(_ ctx: GraphicsContext, _ path: Path, _ color: Color, _ alpha: Double, _ sigma: Double) {
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: sigma))
            l.fill(path, with: .color(color.opacity(alpha)))
        }
    }

    static func stroke(_ c: GraphicsContext, _ path: Path, _ color: Color, _ alpha: Double, _ width: Double) {
        c.stroke(path, with: .color(color.opacity(alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round))
    }

    static func grain(_ ctx: GraphicsContext, _ rect: CGRect, _ tile: Image, _ alpha: Double) {
        var g = ctx
        g.blendMode = .overlay
        g.opacity = alpha
        g.fill(Path(rect), with: .tiledImage(tile, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    @discardableResult
    static func text(_ ctx: GraphicsContext, _ s: String, _ font: Font, _ color: Color, x: Double, baseline: Double, anchor: Double = 0) -> Double {
        let r = ctx.resolve(Text(s).font(font).foregroundStyle(color))
        let size = r.measure(in: CGSize(width: 2000, height: 2000))
        let b = r.firstBaseline(in: size)
        ctx.draw(r, in: CGRect(x: x - size.width * anchor, y: baseline - b, width: size.width, height: size.height))
        return size.width
    }

    static func hash(_ n: Double) -> Double {
        let v = sin(n * 12.9898 + 4.1414) * 43758.5453
        return v - floor(v)
    }

    static let title = Font.system(size: 32, weight: .bold)
    static let body = Font.system(size: 15)
    static let bodyBold = Font.system(size: 16, weight: .semibold)
    static let small = Font.system(size: 13)
    static let tiny = Font.system(size: 10, weight: .bold)
    static let big = Font.system(size: 22, weight: .bold)

    // MARK: Grain tile (periodic value noise, built once)

    static let fineGrain: Image = {
        let width = 128, scale = 2
        let wPx = width * scale
        let n = Int((Double(width) / 1.1).rounded())
        func hash(_ x: Int, _ y: Int) -> Double {
            var h = UInt32(truncatingIfNeeded: x &* 374_761_393 &+ y &* 668_265_263) &+ 5 &* 2_246_822_519
            h = (h ^ (h >> 13)) &* 1_274_126_177
            h ^= h >> 16
            return Double(h & 0xFFFF) / 65535
        }
        func noise(_ u: Double, _ v: Double, _ p: Int) -> Double {
            let x = u * Double(p), y = v * Double(p)
            let ix = Int(floor(x)), iy = Int(floor(y))
            let fx = x - Double(ix), fy = y - Double(iy)
            let sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy)
            let x0 = ix % p, x1 = (ix + 1) % p, y0 = iy % p, y1 = (iy + 1) % p
            let a = hash(x0, y0), b = hash(x1, y0), c = hash(x0, y1), d = hash(x1, y1)
            return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy
        }
        var bytes = [UInt8](repeating: 128, count: wPx * wPx)
        for j in 0..<wPx {
            let v = (Double(j) + 0.5) / Double(wPx)
            for i in 0..<wPx {
                let u = (Double(i) + 0.5) / Double(wPx)
                let g = noise(u, v, n) * 0.67 + noise(u, v, n * 2) * 0.33
                bytes[j * wPx + i] = UInt8(max(0, min(255, (0.5 + (g - 0.5) * 1.6) * 255)))
            }
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)!
        let cg = CGImage(width: wPx, height: wPx, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: wPx,
                         space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
                         provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
        return Image(decorative: cg, scale: CGFloat(scale))
    }()

    // MARK: Static layers

    /// Ground and the dock plate the compass sits on.
    static func ground(_ c: GraphicsContext, _ s: Scene) {
        let w = s.w, h = s.h
        let full = CGRect(x: 0, y: 0, width: w, height: h)
        c.fill(Path(full), with: lin(0, 0, 0, h, [Color(hex: 0x13221D), Color(hex: 0x0F1C18), Color(hex: 0x0C1714), Color(hex: 0x0A1310)], [0, 0.4, 0.75, 1]))
        c.fill(Path(full), with: rad(w * 0.5, s.pageTop + s.pageH * 0.4, h * 0.6, [.rgba(120, 160, 130, 0.10), .rgba(0, 0, 0, 0)]))
        grain(c, full, fineGrain, 0.1)
        let dockY = s.cy - s.r - 62
        blurred(c, rr(0, dockY - 6, w, h - dockY + 40, 34), .black, 0.45, 18)
        c.fill(rr(0, dockY, w, h - dockY + 40, 34), with: lin(0, dockY, 0, h, [Color(hex: 0x1B2B25), Color(hex: 0x15231E), Color(hex: 0x101B17)]))
        stroke(c, rr(0.5, dockY + 0.5, w - 1, h - dockY + 40, 34), Color(hex: 0xD8F0E0), 0.1, 1)
    }

    private static func cardRect(_ c: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double = 20) {
        blurred(c, rr(x, y + 8, w, h, r), .black, 0.35, 12)
        c.fill(rr(x, y, w, h, r), with: lin(0, y, 0, y + h, cardColors))
        stroke(c, rr(x + 0.5, y + 0.5, w - 1, h - 1, r - 0.5), Color(hex: 0xD8F0E0), 0.08, 1)
    }

    private static func header(_ c: GraphicsContext, _ title: String, _ sub: String) {
        text(c, title, Self.title, ink, x: 20, baseline: 34)
        text(c, sub, body, mute, x: 20, baseline: 58)
    }

    /// One page (k = 0 … 3) in its own W × pageH frame.
    static func page(_ c: GraphicsContext, _ s: Scene, _ k: Int) {
        switch k {
        case 0: mapPage(c, s.w, s.pageH)
        case 1: tripsPage(c, s.w, s.pageH)
        case 2: gearPage(c, s.w, s.pageH)
        default: mePage(c, s.w, s.pageH)
        }
    }

    private static func mapPage(_ c: GraphicsContext, _ W: Double, _ H: Double) {
        header(c, "Map", "Larch Ridge · day 2 of 3")
        let x = 16.0, y = 74.0, w = W - 32, h = H - y - 8
        cardRect(c, x, y, w, h, 24)
        var mc = c
        mc.clip(to: rr(x, y, w, h, 24))
        mc.fill(Path(CGRect(x: x, y: y, width: w, height: h)), with: lin(x, y, x + w, y + h, [Color(hex: 0x22352C), Color(hex: 0x1D2E26), Color(hex: 0x192820)]))
        // A tarn in the lower left.
        var lake = Path()
        lake.move(to: CGPoint(x: x - 10, y: y + h * 0.72))
        lake.addCurve(to: CGPoint(x: x + w * 0.24, y: y + h + 10), control1: CGPoint(x: x + w * 0.2, y: y + h * 0.62), control2: CGPoint(x: x + w * 0.34, y: y + h * 0.8))
        lake.addLine(to: CGPoint(x: x - 10, y: y + h + 10))
        lake.closeSubpath()
        mc.fill(lake, with: lin(x, y + h * 0.65, x + w * 0.3, y + h, [Color(hex: 0x2E5966), Color(hex: 0x244955), Color(hex: 0x1C3A44)]))
        // Contours round two peaks; every fourth line is an index line.
        let peaks: [(Double, Double, Double)] = [(x + w * 0.66, y + h * 0.36, 1), (x + w * 0.28, y + h * 0.3, 0.7)]
        for (p, peak) in peaks.enumerated() {
            let (px, py, sz) = peak
            let fp = Double(p)
            for k in 0..<11 {
                let fk = Double(k)
                var b = Path()
                for i in 0...72 {
                    let a = Double(i) / 72 * .pi * 2
                    let r = (10 + fk * 13) * sz + 6 * sin(3 * a + fk * 0.5 + fp) + 4 * sin(5 * a + fk * 0.9) + 3 * sin(2 * a - fk * 0.3)
                    let pt = CGPoint(x: px + cos(a) * r * 1.15, y: py + sin(a) * r * 0.85)
                    if i == 0 { b.move(to: pt) } else { b.addLine(to: pt) }
                }
                b.closeSubpath()
                stroke(mc, b, Color(hex: 0x9CC4A6), k % 4 == 0 ? 0.32 : 0.16, k % 4 == 0 ? 1.2 : 0.8)
            }
        }
        // The trail: dashed, from the trailhead to the ridge, with a glow.
        var trail = Path()
        trail.move(to: CGPoint(x: x + w * 0.12, y: y + h * 0.92))
        trail.addCurve(to: CGPoint(x: x + w * 0.48, y: y + h * 0.5), control1: CGPoint(x: x + w * 0.38, y: y + h * 0.82), control2: CGPoint(x: x + w * 0.3, y: y + h * 0.56))
        trail.addCurve(to: CGPoint(x: x + w * 0.66, y: y + h * 0.36), control1: CGPoint(x: x + w * 0.62, y: y + h * 0.45), control2: CGPoint(x: x + w * 0.58, y: y + h * 0.3))
        mc.drawLayer { l in
            l.addFilter(.blur(radius: 4))
            l.stroke(trail, with: .color(accent.opacity(0.35)), style: StrokeStyle(lineWidth: 7, lineCap: .round))
        }
        mc.stroke(trail, with: .color(accent), style: StrokeStyle(lineWidth: 2.6, lineCap: .round, dash: [7, 6]))
        mc.fill(circle(x + w * 0.12, y + h * 0.92, 6), with: .color(ink))
        mc.fill(circle(x + w * 0.12, y + h * 0.92, 3), with: .color(accent))
        // You are here: a halo and a dot on the trail.
        mc.fill(circle(x + w * 0.48, y + h * 0.5, 16), with: .color(Color(hex: 0x7FB8FF).opacity(0.18)))
        mc.fill(circle(x + w * 0.48, y + h * 0.5, 7), with: .color(ink))
        mc.fill(circle(x + w * 0.48, y + h * 0.5, 5), with: .color(Color(hex: 0x3D8BFF)))
        // Summit flag.
        let fx = x + w * 0.66, fy = y + h * 0.36
        stroke(mc, line(fx, fy, fx, fy - 18), ink, 0.9, 1.4)
        var flag = Path()
        flag.move(to: CGPoint(x: fx, y: fy - 18))
        flag.addLine(to: CGPoint(x: fx + 12, y: fy - 14))
        flag.addLine(to: CGPoint(x: fx, y: fy - 10))
        flag.closeSubpath()
        mc.fill(flag, with: .color(accent))
        text(mc, "Larch Peak 2,184 m", tiny, ink.opacity(0.8), x: fx + 6, baseline: fy + 16)
        // Route chip over the map.
        let chipW = min(w - 24, 236)
        let cy = y + h - 64
        blurred(c, rr(x + 12, cy + 6, chipW, 52, 16), .black, 0.4, 8)
        c.fill(rr(x + 12, cy, chipW, 52, 16), with: .color(Color(hex: 0x0F1A16).opacity(0.86)))
        text(c, "Larch Ridge Loop", bodyBold, ink, x: x + 26, baseline: cy + 22)
        text(c, "11.4 km  ·  +640 m  ·  4 h 20", small, mute, x: x + 26, baseline: cy + 40)
    }

    private static func tripsPage(_ c: GraphicsContext, _ W: Double, _ H: Double) {
        header(c, "Trips", "3 planned this month")
        let trips: [(name: String, meta: String, seed: Double)] = [
            ("Larch Ridge Loop", "Sat 14 Jun · 11.4 km · +640 m", 1),
            ("Cinder Falls", "Sun 22 Jun · 6.2 km · +310 m", 2),
            ("Mirror Tarns overnight", "Fri 27 Jun · 18.9 km · +1,020 m", 3),
        ]
        let top = 76.0, gap = 12.0
        let ch = min(104, (H - top - 8 - gap * 2) / 3)
        for (i, t) in trips.enumerated() {
            let y = top + Double(i) * (ch + gap)
            cardRect(c, 16, y, W - 32, ch)
            let nameW = text(c, t.name, bodyBold, ink, x: 32, baseline: y + ch / 2 - 4)
            text(c, t.meta, small, mute, x: 32, baseline: y + ch / 2 + 16)
            // Elevation profile on the right.
            let px = W - 32 - 84
            let py = y + ch / 2 + 18
            var b = Path()
            b.move(to: CGPoint(x: px, y: py))
            for k in 0...14 {
                let fk = Double(k)
                let v = 0.3 + 0.7 * abs(sin(fk * 0.45 + t.seed) * 0.6 + sin(fk * 0.21 + t.seed * 2) * 0.4)
                b.addLine(to: CGPoint(x: px + 84 * fk / 14, y: py - v * 34))
            }
            b.addLine(to: CGPoint(x: px + 84, y: py))
            b.closeSubpath()
            c.fill(b, with: lin(0, py - 34, 0, py, [.rgba(240, 122, 58, 0.55), .rgba(240, 122, 58, 0.05)]))
            if i == 0 {
                // A "next" pill after the name, on its line.
                let px0 = 32 + nameW + 8
                let py0 = y + ch / 2 - 18
                c.fill(rr(px0, py0, 42, 18, 9), with: .color(accent.opacity(0.18)))
                text(c, "NEXT", tiny, accent, x: px0 + 21, baseline: py0 + 12.5, anchor: 0.5)
            }
        }
    }

    private static func gearPage(_ c: GraphicsContext, _ W: Double, _ H: Double) {
        header(c, "Gear", "Pack weight 7.4 of 9.0 kg")
        let y0 = 78.0
        c.fill(rr(20, y0, W - 40, 8, 4), with: .color(ink.opacity(0.1)))
        c.fill(rr(20, y0, (W - 40) * 0.82, 8, 4), with: lin(20, 0, W - 20, 0, [Color(hex: 0xF5A15E), accent, Color(hex: 0xD85A24)]))
        let items: [(String, String, Bool)] = [
            ("Rain shell", "320 g", true), ("Headlamp", "86 g", true), ("Water filter", "140 g", false),
            ("First-aid kit", "210 g", true), ("Trekking poles", "480 g", false), ("Down jacket", "390 g", true),
        ]
        let top = y0 + 24
        cardRect(c, 16, top, W - 32, H - top - 8)
        let rowH = min(52, (H - top - 16) / Double(items.count))
        for (i, item) in items.enumerated() {
            let (name, weight, done) = item
            let y = top + 8 + Double(i) * rowH
            let cyy = y + rowH / 2
            if done {
                c.fill(circle(36, cyy, 10), with: .color(accent))
                var tick = Path()
                tick.move(to: CGPoint(x: 31.5, y: cyy))
                tick.addLine(to: CGPoint(x: 35, y: cyy + 3.5))
                tick.addLine(to: CGPoint(x: 41, y: cyy - 3.5))
                c.stroke(tick, with: .color(Color(hex: 0x14211C)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
            } else {
                stroke(c, circle(36, cyy, 9.5), ink, 0.35, 1.5)
            }
            text(c, name, body, done ? ink : mute, x: 58, baseline: cyy + 5)
            text(c, weight, small, faint, x: W - 32, baseline: cyy + 5, anchor: 1)
            if i < items.count - 1 { stroke(c, line(58, y + rowH, W - 32, y + rowH), ink, 0.07, 1) }
        }
    }

    private static func mePage(_ c: GraphicsContext, _ W: Double, _ H: Double) {
        header(c, "Me", "Joined 2023 · Cascade Club")
        let top = 76.0
        cardRect(c, 16, top, W - 32, 92)
        c.fill(circle(56, top + 46, 26), with: lin(30, top + 20, 82, top + 72, [Color(hex: 0xF5A15E), accent, Color(hex: 0x9C3A16)]))
        text(c, "AK", bodyBold, Color(hex: 0xFFF4EA), x: 56, baseline: top + 52, anchor: 0.5)
        text(c, "Ari Kestrel", bodyBold, ink, x: 96, baseline: top + 42)
        text(c, "Next summit: Larch Peak", small, mute, x: 96, baseline: top + 62)
        let stats = [("312", "km hiked"), ("41", "summits"), ("18,920", "m climbed")]
        let sy = top + 108
        let sw = (W - 32 - 16) / 3
        let sh = min(96, H - sy - 8)
        for (i, st) in stats.enumerated() {
            let x = 16 + Double(i) * (sw + 8)
            cardRect(c, x, sy, sw, sh, 18)
            text(c, st.0, big, ink, x: x + 14, baseline: sy + sh / 2 + 2)
            text(c, st.1, small, mute, x: x + 14, baseline: sy + sh / 2 + 22)
        }
        let by = sy + sh + 16
        if by + 60 < H {
            text(c, "BADGES", tiny, faint, x: 20, baseline: by + 12)
            for i in 0..<5 {
                let x = 40 + Double(i) * 58
                let y = by + 44
                if y + 24 > H { break }
                c.fill(circle(x, y, 22), with: rad(x - 6, y - 8, 30, [Color(hex: 0x2E4A3E), Color(hex: 0x1C2E26)]))
                stroke(c, circle(x, y, 22), i < 3 ? accent : ink, i < 3 ? 0.7 : 0.2, 1.5)
                var m = Path()
                m.move(to: CGPoint(x: x - 11, y: y + 7))
                m.addLine(to: CGPoint(x: x - 3, y: y - 6 - hash(Double(i)) * 3))
                m.addLine(to: CGPoint(x: x + 2, y: y + 1))
                m.addLine(to: CGPoint(x: x + 6, y: y - 4))
                m.addLine(to: CGPoint(x: x + 12, y: y + 7))
                m.closeSubpath()
                c.fill(m, with: .color(ink.opacity(i < 3 ? 0.85 : 0.3)))
            }
        }
    }

    /// Housing, bezel with degrees and letters, and the liquid well with its card (no needle), in a box × box frame.
    static func compass(_ c: GraphicsContext, _ s: Scene) {
        let B = s.box
        let cx = B / 2, cy = B / 2
        let R = s.r
        blurred(c, circle(cx, cy + 10, R + 12), .black, 0.5, 14)
        blurred(c, circle(cx, cy + 2, R + 12), .black, 0.6, 2)
        // Housing: dark anodised metal with a knurled rim and a bright chamfer.
        let sweep = [0x3A4440, 0x8E9893, 0x2F3835, 0x6E7873, 0x2C3431, 0x7E8883, 0x3A4440].map { Color(hex: UInt32($0)) }
        c.fill(circle(cx, cy, R + 12), with: .conicGradient(Gradient(colors: sweep), center: CGPoint(x: cx, y: cy), angle: .zero))
        var knurl = Path()
        for i in 0..<120 {
            let a = Double(i) / 120 * .pi * 2
            knurl.move(to: CGPoint(x: cx + cos(a) * (R + 8), y: cy + sin(a) * (R + 8)))
            knurl.addLine(to: CGPoint(x: cx + cos(a) * (R + 12), y: cy + sin(a) * (R + 12)))
        }
        stroke(c, knurl, Color(hex: 0x0E1412), 0.5, 1)
        c.fill(circle(cx, cy, R + 1), with: lin(cx, cy - R, cx, cy + R, [Color(hex: 0xC9D2CD), Color(hex: 0x5E6864), Color(hex: 0x2A322F)]))
        // Bezel: matte black ring with ticks every 5°, numbers every 30°, the cardinals.
        c.fill(circle(cx, cy, R), with: rad(cx, cy - R * 0.3, R * 1.2, [Color(hex: 0x26302C), Color(hex: 0x1A2220), Color(hex: 0x121816)]))
        var minor = Path(), major = Path()
        for d in stride(from: 0, to: 360, by: 5) {
            let a = Double(d - 90) * .pi / 180
            let isMajor = d % 30 == 0
            let r0 = R - 3, r1 = R - (isMajor ? 12 : 7)
            let seg = line(cx + cos(a) * r0, cy + sin(a) * r0, cx + cos(a) * r1, cy + sin(a) * r1)
            if isMajor { major.addPath(seg) } else { minor.addPath(seg) }
        }
        stroke(c, major, Color(hex: 0xE9EEE6), 0.85, 1.4)
        stroke(c, minor, Color(hex: 0xE9EEE6), 0.4, 0.8)
        for d in stride(from: 30, to: 360, by: 30) where d % 90 != 0 {
            let a = Double(d - 90) * .pi / 180
            let r = R - 21
            var tc = c
            tc.translateBy(x: cx + cos(a) * r, y: cy + sin(a) * r)
            tc.rotate(by: .degrees(Double(d)))
            text(tc, "\(d)", .system(size: 8.5, weight: .semibold), Color(hex: 0xE9EEE6).opacity(0.7), x: 0, baseline: 3.5, anchor: 0.5)
        }
        let cards = ["N", "E", "S", "W"]
        for i in 0..<4 {
            let a = Double(i * 90 - 90) * .pi / 180
            let r = R - 22
            var tc = c
            tc.translateBy(x: cx + cos(a) * r, y: cy + sin(a) * r)
            tc.rotate(by: .degrees(Double(i * 90)))
            text(tc, cards[i], .system(size: 15, weight: .bold), i == 0 ? Color(hex: 0xF0563F) : Color(hex: 0xF2F4EE), x: 0, baseline: 6, anchor: 0.5)
        }
        // The liquid well: deep tinted glass, an inner shadow at its rim, a faint rose.
        let wr = R * 0.66
        c.fill(circle(cx, cy, wr + 2), with: .color(Color(hex: 0x0A100E)))
        c.fill(circle(cx, cy, wr), with: rad(cx - wr * 0.25, cy - wr * 0.3, wr * 1.4, [Color(hex: 0x24453F), Color(hex: 0x16302C), Color(hex: 0x0D1E1C)]))
        var rose = Path()
        for d in stride(from: 0, to: 360, by: 10) {
            let a = Double(d) * .pi / 180
            let r1 = d % 90 == 0 ? wr * 0.7 : wr * 0.86
            rose.addPath(line(cx + cos(a) * wr * 0.92, cy + sin(a) * wr * 0.92, cx + cos(a) * r1, cy + sin(a) * r1))
        }
        stroke(c, rose, Color(hex: 0xBFE3D6), 0.16, 0.8)
        stroke(c, circle(cx, cy, wr * 0.5), Color(hex: 0xBFE3D6), 0.1, 0.8)
        var wc = c
        wc.clip(to: circle(cx, cy, wr))
        wc.drawLayer { l in
            l.addFilter(.blur(radius: 6))
            l.stroke(circle(cx, cy + 2, wr + 3), with: .color(.black.opacity(0.7)), lineWidth: 8)
        }
    }

    /// The glass dome over the well: a crescent highlight and a sharp glint, in a box × box frame.
    static func glass(_ c: GraphicsContext, _ s: Scene) {
        let B = s.box
        let cx = B / 2, cy = B / 2
        let wr = s.r * 0.66
        var gc = c
        gc.clip(to: circle(cx, cy, wr))
        let crescent = circle(cx, cy, wr).subtracting(circle(cx + wr * 0.22, cy + wr * 0.26, wr * 1.02))
        gc.fill(crescent, with: lin(cx - wr, cy - wr, cx, cy, [.rgba(255, 255, 255, 0.32), .rgba(255, 255, 255, 0.06)]))
        blurred(gc, Path(ellipseIn: CGRect(x: cx - wr * 0.55, y: cy - wr * 0.72, width: wr * 0.32, height: wr * 0.14)), .white, 0.55, 2)
        stroke(c, circle(cx, cy, wr), Color(hex: 0xDDEDE6), 0.22, 1)
    }

    // MARK: Moving parts (screen coordinates)

    /// The target glow, the needle, its cap and shadow, and the air bubble (under the glass).
    static func needle(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose, bob: Double) {
        // The heading being steered to glows on the bezel.
        let ta = (pose.target - 90) * .pi / 180
        var gc = c
        gc.blendMode = .plusLighter
        blurred(gc, circle(s.cx + cos(ta) * (s.r - 8), s.cy + sin(ta) * (s.r - 8), 9), accent, 0.65, 6)

        // The needle floats: a shadow offset on the card below it, a slight bob.
        let len = s.r * 0.6
        let lift = 1 + 0.012 * sin(bob)
        var sh = Path()
        sh.move(to: CGPoint(x: -len * 0.13, y: 0))
        sh.addLine(to: CGPoint(x: 0, y: -len))
        sh.addLine(to: CGPoint(x: len * 0.13, y: 0))
        sh.addLine(to: CGPoint(x: 0, y: len * 0.86))
        sh.closeSubpath()
        c.drawLayer { l in
            l.addFilter(.blur(radius: 3))
            l.translateBy(x: s.cx + 3, y: s.cy + 5)
            l.rotate(by: .degrees(pose.needle))
            l.fill(sh, with: .color(.black.opacity(0.45)))
        }
        var nc = c
        nc.translateBy(x: s.cx, y: s.cy)
        nc.rotate(by: .degrees(pose.needle))
        let nl = len * lift
        let wdt = nl * 0.13
        let tail = nl * 0.86
        // Four facets: north red (lit side and shade side), south cream.
        func facet(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ color: UInt32) {
            var p = Path()
            p.move(to: .zero)
            p.addLine(to: CGPoint(x: x0, y: y0))
            p.addLine(to: CGPoint(x: x1, y: y1))
            p.closeSubpath()
            nc.fill(p, with: .color(Color(hex: color)))
        }
        facet(-wdt, 0, 0, -nl, 0xE5533F)
        facet(wdt, 0, 0, -nl, 0xA82F22)
        facet(-wdt, 0, 0, tail, 0xF1ECE0)
        facet(wdt, 0, 0, tail, 0xB8B1A2)
        stroke(nc, line(0, -nl + 2, 0, -4), Color(hex: 0xFFD2C8), 0.35, 0.6)
        c.fill(circle(s.cx, s.cy, 7), with: rad(s.cx - 2, s.cy - 2, 8, [Color(hex: 0xF7E4B0), Color(hex: 0xB68C42), Color(hex: 0x5E4219)]))
        // An air bubble under the glass drifts against the spin.
        let lagDeg = max(-50, min(50, -pose.spin * 0.12))
        let ba = (225 + lagDeg) * .pi / 180
        let br = s.r * 0.48
        let bx = s.cx + cos(ba) * br
        let by = s.cy + sin(ba) * br
        c.fill(circle(bx, by, 4.2), with: .color(Color(hex: 0xCFEDE4).opacity(0.18)))
        stroke(c, circle(bx, by, 4.2), Color(hex: 0xE8FFF6), 0.5, 0.8)
        c.fill(circle(bx - 1.3, by - 1.3, 1.1), with: .color(.white.opacity(0.8)))
    }

    /// The finger's bead on the bezel while dragging (over the glass).
    static func bead(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        guard let finger = pose.finger else { return }
        let fa = (finger - 90) * .pi / 180
        let fx = s.cx + cos(fa) * (s.r + 4)
        let fy = s.cy + sin(fa) * (s.r + 4)
        blurred(c, circle(fx, fy, 12), accent, 0.5, 6)
        c.fill(circle(fx, fy, 6), with: .color(accent))
        stroke(c, circle(fx, fy, 6), Color(hex: 0xFFE3D0), 0.8, 1.2)
    }
}
