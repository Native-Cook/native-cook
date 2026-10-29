import CoreText
import SwiftUI
import UIKit

/// Layout, palette and drawing of Moka Flood Welcome, in design points (393 wide).
enum MokaFloodWelcomeArt {
    typealias M = MokaFloodWelcomeMotion

    // MARK: Palette

    static let peach = 0xF8D0C3
    static let coffee = 0x402D06
    static let crema = 0x6E4D17
    static let speck = 0x8A6427
    static let pot = 0x8C0F22
    static let potBand = 0x6E0A1A
    static let potLid = 0x7A0C1D
    static let potLine = 0xE99AA6
    static let handle = 0xD47E8D
    static let knob = 0xD6A4A3
    static let glassLine = 0x3B2A96
    static let shadow = 0xE3A898
    static let cream = 0xF6EEDB

    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }

    // MARK: Fonts

    static let handFont: String = {
        if let url = Bundle.main.url(forResource: "MokaFloodWelcome-GochiHand-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        return "GochiHand-Regular"
    }()

    static func hand(_ size: CGFloat) -> Font { .custom(handFont, fixedSize: size) }

    // MARK: Layout

    struct Layout {
        var height: Double
        var top: Double
        var bottom: Double

        var barY: Double { top + 26 }
        var ctaTop: Double { height - bottom - 16 - 56 }
        var dotsY: Double { ctaTop - 30 }
        var hintY: Double { ctaTop - 70 }
        let glassX = 132.0
        var glassBottom: Double { ctaTop - 130 }
        var glassTop: Double { glassBottom - 172 }
        var pivot: CGPoint { CGPoint(x: 266, y: glassTop - 170) }
        var titleY: Double { glassTop - 74 } // centre of the first title line
        var subY: Double { titleY + 3 * 64 - 6 }
        var floor: Double { glassBottom - 5 } // the puddle's back edge, where the flood starts rising
        /// The flood surface's base height and wave share for a frame, or nil while there is no
        /// flood surface (only the puddle, or nothing).
        func flood(_ f: M.Frame) -> (base: Double, amp: Double)? {
            if let d = f.drain { return (-70 + (height + 110) * d, 1) }
            guard f.volume > 1.75 else { return nil }
            let rise = M.clamp((f.volume - 1.75) / 1.25)
            return (floor - (floor + 70) * rise, M.easeOutCubic(M.clamp((f.volume - 1.75) / 0.4)))
        }
    }

    // MARK: Pot

    static let spoutTip = CGPoint(x: -99, y: -125)
    static let potScale = 0.86

    static func rotate(_ p: CGPoint, _ deg: Double, around c: CGPoint) -> CGPoint {
        let a = deg * Double.pi / 180
        let x = p.x * potScale, y = p.y * potScale
        return CGPoint(x: c.x + x * cos(a) - y * sin(a), y: c.y + x * sin(a) + y * cos(a))
    }

    static func arch(_ p: inout Path, xb: Double, xt: Double, w: Double, yb: Double, yt: Double) {
        p.move(to: CGPoint(x: xb - w / 2, y: yb))
        p.addLine(to: CGPoint(x: xt - w / 2, y: yt + w / 2))
        p.addQuadCurve(to: CGPoint(x: xt + w / 2, y: yt + w / 2), control: CGPoint(x: xt, y: yt - w * 0.5))
        p.addLine(to: CGPoint(x: xb + w / 2, y: yb))
    }

    static let potPaths: (handle: Path, upper: Path, lid: Path, knob: Path, spout: Path, band: Path, boiler: Path, lines: Path) = {
        var handle = Path()
        handle.move(to: CGPoint(x: 62, y: -102))
        handle.addCurve(to: CGPoint(x: 58, y: -36), control1: CGPoint(x: 122, y: -118), control2: CGPoint(x: 118, y: -26))
        var upper = Path()
        upper.addLines([CGPoint(x: -52, y: -6), CGPoint(x: -70, y: -112), CGPoint(x: 70, y: -112), CGPoint(x: 52, y: -6)])
        upper.closeSubpath()
        let lid = Path(roundedRect: CGRect(x: -67, y: -121, width: 134, height: 10), cornerRadius: 3)
        let knob = Path(roundedRect: CGRect(x: -36, y: -131, width: 16, height: 12), cornerRadius: 3)
        var spout = Path()
        spout.addLines([CGPoint(x: -58, y: -114), CGPoint(x: -99, y: -125), CGPoint(x: -68, y: -86)])
        spout.closeSubpath()
        let band = Path(roundedRect: CGRect(x: -57, y: -8, width: 114, height: 16), cornerRadius: 2)
        var boiler = Path()
        boiler.move(to: CGPoint(x: -52, y: 7))
        boiler.addLine(to: CGPoint(x: -76, y: 116))
        boiler.addQuadCurve(to: CGPoint(x: -70, y: 122), control: CGPoint(x: -77, y: 122))
        boiler.addLine(to: CGPoint(x: 70, y: 122))
        boiler.addQuadCurve(to: CGPoint(x: 76, y: 116), control: CGPoint(x: 77, y: 122))
        boiler.addLine(to: CGPoint(x: 52, y: 7))
        boiler.closeSubpath()
        var lines = Path()
        for (xb, xt) in [(-36.0, -50.0), (-12, -17), (12, 17), (36, 50)] {
            arch(&lines, xb: xb, xt: xt, w: 21, yb: -20, yt: -100)
        }
        for (xt, xb) in [(-34.0, -54.0), (-11, -18), (11, 18), (34, 54)] {
            arch(&lines, xb: xb, xt: xt, w: 19, yb: 110, yt: 20)
        }
        return (handle, upper, lid, knob, spout, band, boiler, lines)
    }()

    static func drawPot(_ g: GraphicsContext, _ l: Layout, angle: Double) {
        var c = g
        c.translateBy(x: l.pivot.x, y: l.pivot.y)
        c.rotate(by: .degrees(angle))
        c.scaleBy(x: potScale, y: potScale)
        let p = potPaths
        c.stroke(p.handle, with: .color(color(handle)), style: StrokeStyle(lineWidth: 18, lineCap: .round))
        c.fill(p.spout, with: .color(color(pot)))
        c.fill(p.upper, with: .color(color(pot)))
        c.fill(p.boiler, with: .color(color(pot)))
        c.fill(p.lid, with: .color(color(potLid)))
        c.fill(p.knob, with: .color(color(knob)))
        c.fill(p.band, with: .color(color(potBand)))
        c.stroke(p.lines, with: .color(color(potLine)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
        // A soft light band down the boiler's left side.
        var sheen = Path()
        sheen.addLines([CGPoint(x: -46, y: 14), CGPoint(x: -66, y: 112)])
        c.stroke(sheen, with: .color(color(0xFFFFFF, 0.10)), style: StrokeStyle(lineWidth: 6, lineCap: .round))
    }

    // MARK: Glass

    static func glassInterior(_ l: Layout) -> Path {
        let x = l.glassX, t = l.glassTop, b = l.glassBottom - 14
        var p = Path()
        p.move(to: CGPoint(x: x - 73, y: t - 40))
        p.addLine(to: CGPoint(x: x - 73, y: t))
        p.addLine(to: CGPoint(x: x - 59.5, y: b))
        p.addQuadCurve(to: CGPoint(x: x + 59.5, y: b), control: CGPoint(x: x, y: b + 14))
        p.addLine(to: CGPoint(x: x + 73, y: t))
        p.addLine(to: CGPoint(x: x + 73, y: t - 40))
        p.closeSubpath()
        return p
    }

    static func glassBody(_ l: Layout) -> Path {
        let x = l.glassX, t = l.glassTop, b = l.glassBottom
        var p = Path()
        p.move(to: CGPoint(x: x - 76, y: t))
        p.addLine(to: CGPoint(x: x - 61.5, y: b - 12))
        p.addQuadCurve(to: CGPoint(x: x - 48, y: b), control: CGPoint(x: x - 60, y: b))
        p.addLine(to: CGPoint(x: x + 48, y: b))
        p.addQuadCurve(to: CGPoint(x: x + 61.5, y: b - 12), control: CGPoint(x: x + 60, y: b))
        p.addLine(to: CGPoint(x: x + 76, y: t))
        for i in 1...24 { // the back of the rim
            let a = Double.pi * Double(i) / 24
            p.addLine(to: CGPoint(x: x + 76 * cos(a), y: t - 9 * sin(a)))
        }
        p.closeSubpath()
        return p
    }

    static func halfEllipse(_ p: inout Path, cx: Double, cy: Double, rx: Double, ry: Double, front: Bool) {
        let n = 24
        for i in 0...n {
            let a = Double.pi * Double(i) / Double(n)
            let pt = CGPoint(x: cx - rx * cos(a), y: cy + (front ? 1 : -1) * ry * sin(a))
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
    }

    static func glassLines(_ l: Layout) -> Path {
        let x = l.glassX, t = l.glassTop, b = l.glassBottom
        var p = Path()
        p.move(to: CGPoint(x: x - 76, y: t))
        p.addLine(to: CGPoint(x: x - 61.5, y: b - 12))
        p.addQuadCurve(to: CGPoint(x: x - 48, y: b), control: CGPoint(x: x - 60, y: b))
        p.addLine(to: CGPoint(x: x + 48, y: b))
        p.addQuadCurve(to: CGPoint(x: x + 61.5, y: b - 12), control: CGPoint(x: x + 60, y: b))
        p.addLine(to: CGPoint(x: x + 76, y: t))
        halfEllipse(&p, cx: x, cy: t, rx: 76, ry: 9, front: true)
        halfEllipse(&p, cx: x, cy: t, rx: 76, ry: 9, front: false)
        halfEllipse(&p, cx: x, cy: t + 28, rx: 73.5, ry: 8, front: true)
        halfEllipse(&p, cx: x, cy: b - 14, rx: 60, ry: 7, front: true)
        let yt = t + 50, yb = b - 19
        for k in [-2.0, -1, 0, 1, 2] {
            arch(&p, xb: x + k * 23, xt: x + k * 27.5, w: 17, yb: yb + abs(k) * -2, yt: yt)
        }
        return p
    }

    /// The liquid's surface height at x inside the glass.
    static func meniscus(_ x: Double, level: Double, f: M.Frame, l: Layout, impactX: Double?) -> Double {
        let u = (x - (l.glassX - 73)) / 146
        var y = level + 2.6 * f.slosh * cos(Double.pi * u) * sin(7.1 * f.time)
            + 1.1 * f.slosh * sin(2 * Double.pi * u * 1.5 + 9.3 * f.time)
        if let ix = impactX {
            let d = abs(x - ix)
            y += 2.2 * sin(0.2 * d - 17 * f.time) * exp(-d / 38)
        }
        // A thin meniscus climbing the walls.
        let wall = min(u, 1 - u) * 146
        y -= 2.2 * exp(-wall / 4)
        return y
    }

    static func glassLevel(_ l: Layout, volume: Double) -> Double {
        let bottom = l.glassBottom - 14, rim = l.glassTop + 1
        return bottom - (bottom - rim) * M.clamp(volume)
    }

    /// The coffee in the glass, its dome over the rim and the spills.
    static func drawGlassCoffee(_ g: GraphicsContext, _ l: Layout, _ f: M.Frame, impactX: Double?) {
        guard f.drain == nil, f.volume > 0.001 else { return }
        let x = l.glassX
        let level = glassLevel(l, volume: f.volume)
        var liquid = Path()
        let x0 = x - 74, x1 = x + 74
        var first = true
        var px = x0
        while px <= x1 + 0.1 {
            let pt = CGPoint(x: px, y: meniscus(px, level: level, f: f, l: l, impactX: impactX))
            if first { liquid.move(to: pt); first = false } else { liquid.addLine(to: pt) }
            px += 4
        }
        liquid.addLine(to: CGPoint(x: x1, y: l.glassBottom))
        liquid.addLine(to: CGPoint(x: x0, y: l.glassBottom))
        liquid.closeSubpath()
        var c = g
        c.clip(to: glassInterior(l))
        c.fill(liquid, with: .color(color(coffee)))
        // Crema line just under the surface.
        if f.volume < 1.02 {
            var band = Path()
            var bx = x0
            var started = false
            while bx <= x1 + 0.1 {
                let pt = CGPoint(x: bx, y: meniscus(bx, level: level, f: f, l: l, impactX: impactX) + 2.5)
                if started { band.addLine(to: pt) } else { band.move(to: pt); started = true }
                bx += 4
            }
            c.stroke(band, with: .color(color(crema, 0.9)), lineWidth: 2.5)
        }

        // The dome over the rim.
        let dome = M.clamp((f.volume - 1) / 0.15)
        if dome > 0 {
            let h = 14 * dome * (1 + 0.06 * sin(6.3 * f.time))
            var d = Path()
            let n = 28
            for i in 0...n {
                let u = Double(i) / Double(n)
                let pt = CGPoint(x: x - 78 + 156 * u,
                                 y: l.glassTop + 3 - h * pow(sin(Double.pi * u), 0.6) + 1.2 * sin(9 * u + 8 * f.time) * dome)
                if i == 0 { d.move(to: pt) } else { d.addLine(to: pt) }
            }
            d.addLine(to: CGPoint(x: x + 76, y: l.glassTop + 8))
            d.addLine(to: CGPoint(x: x - 76, y: l.glassTop + 8))
            d.closeSubpath()
            g.fill(d, with: .color(color(coffee)))
        }
    }

    static func drawSpills(_ g: GraphicsContext, _ l: Layout, _ f: M.Frame) {
        guard f.drain == nil else { return }
        let left = M.clamp((f.volume - 1.08) / 0.24)
        let right = M.clamp((f.volume - 1.16) / 0.22)
        for (side, s, w) in [(-1.0, left, 10.5), (1.0, right, 7.5)] where s > 0 {
            let x = l.glassX, t = l.glassTop, b = l.glassBottom
            let pts = [CGPoint(x: x + side * 77, y: t + 4), CGPoint(x: x + side * 63.5, y: b - 8),
                       CGPoint(x: x + side * 58, y: b + 1)]
            var lengths: [Double] = [0]
            for i in 1..<pts.count {
                lengths.append(lengths[i - 1] + hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y))
            }
            let front = pow(s, 1.5) * lengths.last!
            var p = Path()
            p.move(to: pts[0])
            for i in 1..<pts.count {
                if lengths[i] <= front {
                    p.addLine(to: pts[i])
                } else {
                    let k = (front - lengths[i - 1]) / (lengths[i] - lengths[i - 1])
                    p.addLine(to: CGPoint(x: pts[i - 1].x + (pts[i].x - pts[i - 1].x) * k,
                                          y: pts[i - 1].y + (pts[i].y - pts[i - 1].y) * k))
                    break
                }
            }
            g.stroke(p, with: .color(color(coffee)), style: StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round))
        }
    }

    // MARK: Stream

    /// The stream's top and bottom in pt, or nil.
    static func drawStream(_ g: GraphicsContext, _ l: Layout, _ f: M.Frame, tip: CGPoint, landY: Double) {
        guard let s = f.stream else { return }
        let top = tip.y + s.tail
        let bottom = min(tip.y + s.head, landY + 2)
        guard bottom > top + 1 else { return }
        let w = 11 * s.width
        var p = Path()
        let n = 18
        var right: [CGPoint] = []
        var centers: [CGPoint] = []
        for i in 0...n {
            let y = top + (bottom - top) * Double(i) / Double(n)
            let fall = y - tip.y
            let bend = -5 * (1 - exp(-fall / 16)) + 0.5 * sin(0.06 * y - 21 * f.time) * min(1, fall / 40)
            let taper = w * (1 - 0.18 * min(1, fall / 260)) * (s.tail > 0 ? 0.85 : 1)
            let cx = tip.x + bend
            if i == 0 { p.move(to: CGPoint(x: cx - taper / 2, y: y)) } else { p.addLine(to: CGPoint(x: cx - taper / 2, y: y)) }
            right.append(CGPoint(x: cx + taper / 2, y: y))
            centers.append(CGPoint(x: cx, y: y))
        }
        for pt in right.reversed() { p.addLine(to: pt) }
        p.closeSubpath()
        g.fill(p, with: .color(color(coffee)))
        // Round ends: the head (a drop) and, when broken, the tail.
        let endW = w * 0.82
        let head = centers.last!, tail = centers.first!
        g.fill(Path(ellipseIn: CGRect(x: head.x - endW / 2, y: bottom - endW / 2, width: endW, height: endW)),
               with: .color(color(coffee)))
        if s.tail > 0 {
            g.fill(Path(ellipseIn: CGRect(x: tail.x - endW / 2, y: top - endW / 2, width: endW, height: endW)),
                   with: .color(color(coffee)))
        }
    }

    // MARK: Flood

    static func floodY(_ x: Double, base: Double, amp: Double, time: Double) -> Double {
        let tilt = 0.06 * sin(1.7 * time + 0.4) * (x - 196.5)
        let waves = 9 * (0.6 * sin(x * 2 * Double.pi / 230 + 2.1 * time) + 0.4 * sin(x * 2 * Double.pi / 120 - 3.3 * time + 1.3))
        return base + amp * (waves + tilt)
    }

    static func floodPath(_ l: Layout, base: Double, amp: Double, time: Double) -> Path {
        var p = surfaceLine(base, amp: amp, time: time, offset: 0)
        p.addLine(to: CGPoint(x: 405, y: l.height + 60))
        p.addLine(to: CGPoint(x: -12, y: l.height + 60))
        p.closeSubpath()
        return p
    }

    static func surfaceLine(_ base: Double, amp: Double, time: Double, offset: Double) -> Path {
        var p = Path()
        var x = -12.0
        p.move(to: CGPoint(x: x, y: floodY(x, base: base, amp: amp, time: time) + offset))
        while x < 405 {
            x += 6
            p.addLine(to: CGPoint(x: x, y: floodY(x, base: base, amp: amp, time: time) + offset))
        }
        return p
    }

    /// The puddle spreading over the table from the glass's foot (volume 1.28–1.75), flat at the
    /// back where the flood surface will start.
    static func puddlePath(_ l: Layout, _ f: M.Frame) -> Path? {
        let s = M.clamp((f.volume - 1.28) / 0.47)
        guard f.drain == nil, s > 0, f.volume <= 1.75 else { return nil }
        let rx = 50 + 710 * pow(s, 1.25)
        let ry = 0.42 * rx
        let cx = l.glassX - 6, cy = l.glassBottom + 4
        var p = Path()
        let n = 72
        for i in 0..<n {
            let th = 2 * Double.pi * Double(i) / Double(n)
            let wob = 1 + 0.05 * sin(5 * th + 3.1 * f.time) + 0.03 * sin(9 * th - 4.3 * f.time)
            let pt = CGPoint(x: cx + rx * wob * cos(th), y: max(l.floor, cy + ry * wob * sin(th)))
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
        return p
    }

    static func drawFloodLife(_ g: GraphicsContext, _ l: Layout, base: Double, amp: Double, time: Double) {
        // Crema band at the surface.
        g.stroke(surfaceLine(base, amp: amp, time: time, offset: 1.5), with: .color(color(crema, 0.85)), lineWidth: 4)
        // Crema specks drifting just under the surface.
        for i in 0..<26 {
            let fi = Double(i)
            let x = M.mod(hash(fi, 1) * 417 + time * (5 + 9 * hash(fi, 2)), 417) - 12
            let y = floodY(x, base: base, amp: amp, time: time) + 7 + 16 * hash(fi, 3)
            let r = 1.1 + 1.7 * hash(fi, 4)
            g.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)), with: .color(color(speck, 0.75)))
        }
        // Bubbles rising through the coffee.
        let span = l.height + 60
        for i in 0..<18 {
            let fi = Double(i)
            let x = 18 + 357 * hash(fi, 5) + 6 * sin(1.3 * time + fi)
            let y = l.height + 30 - M.mod(time * (26 + 44 * hash(fi, 6)) + hash(fi, 7) * span, span)
            let r = 1.6 + 3.4 * hash(fi, 8)
            g.stroke(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)),
                     with: .color(color(crema, 0.8)), lineWidth: 1.3)
        }
    }

    static func hash(_ i: Double, _ salt: Double) -> Double { M.hash(i * 7.31 + salt * 1.97) }

    // MARK: Background

    static let grain: Image = {
        let size = 160
        var pixels = [UInt8](repeating: 0, count: size * size * 4)
        var seed: UInt32 = 0x9E3779B9
        for i in 0..<(size * size) {
            seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5
            let v = UInt8(seed & 0xFF)
            pixels[i * 4] = v; pixels[i * 4 + 1] = v; pixels[i * 4 + 2] = v; pixels[i * 4 + 3] = 255
        }
        let provider = CGDataProvider(data: Data(pixels) as CFData)!
        let cg = CGImage(width: size, height: size, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: size * 4,
                         space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                         provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
        return Image(decorative: cg, scale: 2)
    }()

    static func drawGrain(_ g: GraphicsContext, _ l: Layout, alpha: Double) {
        var c = g
        c.opacity = alpha
        c.blendMode = .overlay
        c.fill(Path(CGRect(x: 0, y: 0, width: 393, height: l.height)),
               with: .tiledImage(grain, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    static func drawShadow(_ g: GraphicsContext, _ l: Layout) {
        let b = l.glassBottom
        var p = Path()
        p.move(to: CGPoint(x: 112, y: b - 1))
        p.addLine(to: CGPoint(x: 296, y: b + 1))
        p.addQuadCurve(to: CGPoint(x: 294, y: b + 7), control: CGPoint(x: 302, y: b + 4))
        p.addLine(to: CGPoint(x: 112, y: b + 7))
        p.closeSubpath()
        g.fill(p, with: .linearGradient(Gradient(colors: [color(shadow), color(shadow, 0.95), color(shadow, 0.0)]),
                                        startPoint: CGPoint(x: 150, y: b), endPoint: CGPoint(x: 300, y: b)))
    }

    // MARK: Copy

    struct Ink {
        var main: Color
        var soft: Color
        var ctaFill: Color
        var ctaText: Color
    }

    static let peachInk = Ink(main: color(coffee), soft: color(coffee, 0.62), ctaFill: color(coffee), ctaText: color(cream))
    static let coffeeInk = Ink(main: color(cream), soft: color(cream, 0.7), ctaFill: color(cream), ctaText: color(coffee))

    /// The bar, dots and CTA (both layers), and the hint (peach layer only).
    static func drawChrome(_ g: GraphicsContext, _ l: Layout, ink: Ink, hint: Bool) {
        g.draw(Text("brewday").font(hand(28)).foregroundStyle(ink.main), at: CGPoint(x: 24, y: l.barY), anchor: .leading)
        g.draw(Text("Skip").font(.system(size: 15, weight: .semibold)).foregroundStyle(ink.soft),
               at: CGPoint(x: 369, y: l.barY), anchor: .trailing)
        if hint {
            g.draw(Text("hold the pot to pour").font(hand(22)).foregroundStyle(ink.soft),
                   at: CGPoint(x: 196.5, y: l.hintY), anchor: .center)
        }
        let dotsW = 22.0 + 8 + 8 + 8 + 8
        var x = 196.5 - dotsW / 2
        for i in 0..<3 {
            let w = i == 0 ? 22.0 : 8
            g.fill(Path(roundedRect: CGRect(x: x, y: l.dotsY - 4, width: w, height: 8), cornerRadius: 4),
                   with: .color(i == 0 ? ink.main : ink.main.opacity(0.3)))
            x += w + 8
        }
        g.fill(Path(roundedRect: CGRect(x: 24, y: l.ctaTop, width: 345, height: 56), cornerRadius: 28), with: .color(ink.ctaFill))
        g.draw(Text("Get started").font(.system(size: 17, weight: .semibold)).foregroundStyle(ink.ctaText),
               at: CGPoint(x: 196.5, y: l.ctaTop + 28), anchor: .center)
    }

    static let titleLines = ["brew your", "morning", "routine!"]
    static let subLines = ["Brewday lines up your first cup,", "your stretch and the plan for today."]

    static func drawTitle(_ g: GraphicsContext, _ l: Layout, _ f: M.Frame) {
        for (i, line) in titleLines.enumerated() {
            let p = M.lineProgress(f.title, i)
            guard p > 0 else { continue }
            let text = g.resolve(Text(line).font(hand(60)).foregroundStyle(color(cream)))
            let w = text.measure(in: CGSize(width: 600, height: 200)).width
            var c = g
            c.clip(to: Path(CGRect(x: 20, y: l.titleY + Double(i) * 64 - 50, width: 12 + p * (w + 16), height: 100)))
            c.draw(text, at: CGPoint(x: 32, y: l.titleY + Double(i) * 64), anchor: .leading)
        }
        if f.sub > 0 {
            var c = g
            c.opacity = f.sub
            for (i, line) in subLines.enumerated() {
                c.draw(Text(line).font(.system(size: 16)).foregroundStyle(color(cream, 0.78)),
                       at: CGPoint(x: 32, y: l.subY + Double(i) * 22 + 8 * (1 - f.sub)), anchor: .leading)
            }
        }
    }

    // MARK: Scene

    static func draw(_ g: GraphicsContext, _ l: Layout, _ f: M.Frame) {
        g.fill(Path(CGRect(x: 0, y: 0, width: 393, height: l.height)), with: .color(color(peach)))
        drawShadow(g, l)
        drawPot(g, l, angle: f.angle)

        let flood = l.flood(f)
        let tip = rotate(spoutTip, f.angle, around: l.pivot)
        let level = glassLevel(l, volume: f.volume)
        let inGlass = tip.x > l.glassX - 70 && tip.x < l.glassX + 70
        var land = l.height + 40
        if inGlass && f.drain == nil {
            land = f.volume > 1 ? l.glassTop + 3 - 14 * M.clamp((f.volume - 1) / 0.15) : (f.volume > 0.001 ? level : l.glassBottom - 10)
        }
        if let fl = flood { land = min(land, floodY(tip.x, base: fl.base, amp: fl.amp, time: f.time)) }
        drawStream(g, l, f, tip: tip, landY: land)

        g.fill(glassBody(l), with: .color(color(0xFFFFFF, 0.18)))
        let streamHits = f.stream.map { tip.y + $0.head >= land - 1 && $0.tail < land - tip.y } ?? false
        drawGlassCoffee(g, l, f, impactX: streamHits && inGlass ? tip.x - 4 : nil)
        g.stroke(glassLines(l), with: .color(color(glassLine)), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
        drawSpills(g, l, f)
        drawGrain(g, l, alpha: 0.06)
        drawChrome(g, l, ink: peachInk, hint: true)

        let region: Path
        if let fl = flood {
            guard fl.base < l.height + 20 else { return }
            region = floodPath(l, base: fl.base, amp: fl.amp, time: f.time)
        } else if let puddle = puddlePath(l, f) {
            region = puddle
        } else {
            return
        }
        g.fill(region, with: .color(color(coffee)))
        var c = g
        c.clip(to: region)
        drawGrain(c, l, alpha: 0.07)
        if let fl = flood { drawFloodLife(c, l, base: fl.base, amp: fl.amp, time: f.time) }
        drawChrome(c, l, ink: coffeeInk, hint: false)
        drawTitle(c, l, f)
    }
}
