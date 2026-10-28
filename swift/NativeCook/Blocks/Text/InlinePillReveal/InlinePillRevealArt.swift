import SwiftUI
import UIKit

/// The sunrise scene inside the pill, drawn in code: sky, sun, birds, three ridges with cypresses,
/// a farmhouse with a lit window, a road and mist. Layers rise into frame as the pill grows (the
/// reveal is the sunrise) and slide sideways while it opens.
enum InlinePillRevealArt {
    typealias M = InlinePillRevealModel

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    // MARK: Layers: rise offset D (pt) and sideways factor

    struct Layer {
        let d: CGFloat
        let side: CGFloat
    }

    static let sky = Layer(d: 0, side: 0)
    static let sun = Layer(d: 85, side: 0.15)
    static let birds = Layer(d: 40, side: 0.2)
    static let far = Layer(d: 0, side: 0.3)
    static let mid = Layer(d: 10, side: 0.55)
    static let near = Layer(d: 24, side: 0.8)
    static let fore = Layer(d: 40, side: 1.0)

    /// The scene's zoom: small inside the inline pill (more landscape fits the strip), 1 at full bleed.
    static let zoomInline: CGFloat = 0.42
    /// At the inline zoom the strip looks this far above the line's centre.
    static let lookUp: CGFloat = 60

    static func farY(_ x: Double) -> Double { -40 + 10 * sin(2 * .pi * 1.3 * x + 0.6) + 6 * sin(2 * .pi * 3.1 * x + 1.9) }
    static func midY(_ x: Double) -> Double { 10 + 16 * sin(2 * .pi * 0.9 * x + 2.2) + 7 * sin(2 * .pi * 2.4 * x + 0.3) }
    static func nearY(_ x: Double) -> Double { 120 + 34 * sin(2 * .pi * 0.6 * x + 4.0) + 10 * sin(2 * .pi * 1.7 * x + 1.0) }
    static func foreY(_ x: Double) -> Double { 250 - 60 * x + 12 * sin(2 * .pi * 1.1 * x) }

    static let mistX: [Double] = [-0.18, 0.18, 0.55, 0.92, 1.36]
    static let mistY: [Double] = [0, 6, -4, 8, 2]
    static let trees: [(x: Double, h: CGFloat)] = [(-0.36, 30), (-0.31, 22), (0.10, 34), (0.14, 24), (0.56, 42), (0.60, 30), (0.64, 36), (0.90, 26), (1.24, 38), (1.29, 28), (1.52, 32)]

    /// A ridge filled down to the bottom of the screen, its edge sampled every 8 pt.
    static func ridge(_ w: CGFloat, _ vis: CGRect, _ cy: CGFloat, _ off: CGPoint, _ y: (Double) -> Double) -> Path {
        var p = Path()
        let x0 = floor(vis.minX / 8) * 8
        let x1 = vis.maxX
        let h = vis.maxY
        p.move(to: CGPoint(x: x0, y: h + 300))
        var x = x0
        while x <= x1 + 0.1 {
            let u = Double((x - off.x) / w)
            p.addLine(to: CGPoint(x: x, y: cy + CGFloat(y(u)) + off.y))
            x += 8
        }
        p.addLine(to: CGPoint(x: x1, y: h + 300))
        p.closeSubpath()
        return p
    }

    static func vertical(_ c: inout GraphicsContext, _ path: Path, _ top: CGFloat, _ bottom: CGFloat, _ a: UInt32, _ b: UInt32) {
        c.fill(path, with: .linearGradient(Gradient(colors: [color(a), color(b)]),
                                           startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: bottom)))
    }

    /// The whole scene at full bleed; `open` and `full` place the layers, `t` drives the idle life.
    static func scene(_ c: inout GraphicsContext, w: CGFloat, h: CGFloat, cy: CGFloat, open: Double, full: Double, t: Double) {
        let rise = 0.1 * open + 0.9 * full
        let k = CGFloat(1 - rise)
        let side = CGFloat(1 - open) * 26
        func off(_ l: Layer) -> CGPoint { CGPoint(x: side * l.side, y: k * l.d) }
        let loopPhase = 2 * Double.pi * t / M.loop

        // Zoom about the line: screen = cy + (natural − (cy + look)) · zoom.
        let zp = 0.12 * open + 0.88 * full
        let zoom = M.lerp(zoomInline, 1, zp)
        let look = -lookUp * CGFloat(1 - zp)
        var s = c
        s.translateBy(x: w / 2, y: cy)
        s.scaleBy(x: zoom, y: zoom)
        s.translateBy(x: -w / 2, y: -(cy + look))
        let vis = CGRect(x: w / 2 - w / 2 / zoom - 40, y: cy + look - cy / zoom - 40,
                         width: w / zoom + 80, height: h / zoom + 80)

        // Sky
        let so = off(sky)
        s.fill(Path(vis), with: .linearGradient(
            Gradient(stops: [
                .init(color: color(0x4F6F96), location: 0),
                .init(color: color(0x8EA3C0), location: 0.45),
                .init(color: color(0xE4B8A2), location: 0.78),
                .init(color: color(0xF7D3A8), location: 0.93),
                .init(color: color(0xFBE2BD), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: so.y), endPoint: CGPoint(x: 0, y: cy - 30 + so.y)))

        // Sun and its glow
        let sunO = off(sun)
        let sunC = CGPoint(x: 0.70 * w + sunO.x, y: cy - 175 + sunO.y)
        let breathe = 1 + 0.06 * sin(2 * loopPhase)
        let glowR = 150 * CGFloat(breathe)
        s.fill(Path(ellipseIn: CGRect(x: sunC.x - glowR, y: sunC.y - glowR, width: 2 * glowR, height: 2 * glowR)),
               with: .radialGradient(Gradient(colors: [color(0xFFE2A8, 0.55), color(0xFFE2A8, 0.18), color(0xFFE2A8, 0)]),
                                     center: sunC, startRadius: 0, endRadius: glowR))
        s.fill(Path(ellipseIn: CGRect(x: sunC.x - 24, y: sunC.y - 24, width: 48, height: 48)), with: .color(color(0xFFF5DE)))

        // Birds
        let bo = off(birds)
        let flock: [(x: Double, y: CGFloat, k: Double, ph: Double)] = [(0.40, -205, 9, 0), (0.46, -192, 10, 1.7), (0.35, -186, 11, 3.1)]
        for (i, b) in flock.enumerated() {
            let bx = CGFloat(b.x) * w + bo.x + 8 * CGFloat(sin(loopPhase + Double(i)))
            let by = cy + b.y + bo.y + 3 * CGFloat(sin(2 * loopPhase + Double(i) * 2))
            let wing = CGFloat(sin(b.k * loopPhase + b.ph))
            var p = Path()
            p.move(to: CGPoint(x: bx - 7, y: by - 2 - 3 * wing))
            p.addQuadCurve(to: CGPoint(x: bx, y: by), control: CGPoint(x: bx - 3, y: by - 4 - 2 * wing))
            p.addQuadCurve(to: CGPoint(x: bx + 7, y: by - 2 - 3 * wing), control: CGPoint(x: bx + 3, y: by - 4 - 2 * wing))
            s.stroke(p, with: .color(color(0x4E3436, 0.7)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
        }

        // Far ridge and mist
        let fo = off(far)
        vertical(&s, ridge(w, vis, cy, fo, farY), cy - 52 + fo.y, cy + 60 + fo.y, 0xC48F88, 0xA9736D)
        for i in 0 ..< 5 {
            let mx = CGFloat(mistX[i]) * w + fo.x + 14 * CGFloat(sin(loopPhase + Double(i) * 2.1))
            let my = cy - 12 + fo.y + CGFloat(mistY[i])
            var m = s
            m.translateBy(x: mx, y: my)
            m.scaleBy(x: 1, y: 0.14)
            m.fill(Path(ellipseIn: CGRect(x: -120, y: -120, width: 240, height: 240)), with: .radialGradient(
                Gradient(colors: [color(0xFFF1E4, 0.30), color(0xFFF1E4, 0)]),
                center: .zero, startRadius: 0, endRadius: 120))
        }

        // Mid ridge with cypresses
        let mo = off(mid)
        for tree in trees {
            let tx = CGFloat(tree.x) * w + mo.x
            let base = cy + CGFloat(midY(tree.x)) + mo.y + 5
            let tw = tree.h * 0.24
            var p = Path()
            p.move(to: CGPoint(x: tx - tw / 2, y: base))
            p.addQuadCurve(to: CGPoint(x: tx, y: base - tree.h), control: CGPoint(x: tx - tw * 0.62, y: base - tree.h * 0.62))
            p.addQuadCurve(to: CGPoint(x: tx + tw / 2, y: base), control: CGPoint(x: tx + tw * 0.62, y: base - tree.h * 0.62))
            p.closeSubpath()
            s.fill(p, with: .color(color(0x5A322C)))
        }
        vertical(&s, ridge(w, vis, cy, mo, midY), cy - 10 + mo.y, cy + 120 + mo.y, 0xA35E54, 0x86483F)

        // Near hill with the farmhouse
        let no = off(near)
        vertical(&s, ridge(w, vis, cy, no, nearY), cy + 80 + no.y, cy + 240 + no.y, 0x7A4036, 0x5D2E27)
        let hx = 0.30 * w + no.x
        let hb = cy + CGFloat(nearY(0.30)) + no.y + 7
        let windowC = CGPoint(x: hx + 4, y: hb - 6)
        s.fill(Path(ellipseIn: CGRect(x: windowC.x - 22, y: windowC.y - 22, width: 44, height: 44)),
               with: .radialGradient(Gradient(colors: [color(0xFFD58F, 0.45), color(0xFFD58F, 0)]),
                                     center: windowC, startRadius: 0, endRadius: 22))
        s.fill(Path(CGRect(x: hx - 11, y: hb - 13, width: 22, height: 13)), with: .color(color(0xE6BFA2)))
        var roof = Path()
        roof.move(to: CGPoint(x: hx - 14, y: hb - 12))
        roof.addLine(to: CGPoint(x: hx - 4, y: hb - 21))
        roof.addLine(to: CGPoint(x: hx + 14, y: hb - 21))
        roof.addLine(to: CGPoint(x: hx + 15, y: hb - 12))
        roof.closeSubpath()
        s.fill(roof, with: .color(color(0x5A2B24)))
        s.fill(Path(CGRect(x: windowC.x - 2, y: windowC.y - 2.5, width: 4, height: 5)), with: .color(color(0xFFD58F)))

        // Foreground and the road
        let fg = off(fore)
        vertical(&s, ridge(w, vis, cy, fg, foreY), cy + 200 + fg.y, h + fg.y, 0x4A2622, 0x2F1714)
        s.fill(road(w, cy, fg), with: .color(color(0xF2C9A4, 0.32)))

        // Vignette and a soft scrim behind the second line
        c.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .radialGradient(
            Gradient(colors: [color(0x000000, 0), color(0x000000, 0), color(0x1A0C08, 0.26)]),
            center: CGPoint(x: w / 2, y: cy), startRadius: 0, endRadius: max(w, h) * 0.62))
        var scrim = c
        scrim.translateBy(x: w / 2, y: cy)
        scrim.scaleBy(x: 1, y: 0.45)
        scrim.fill(Path(ellipseIn: CGRect(x: -210, y: -210, width: 420, height: 420)), with: .radialGradient(
            Gradient(colors: [color(0x2A1410, 0.22 * full), color(0x2A1410, 0)]),
            center: .zero, startRadius: 0, endRadius: 210))
    }

    /// A road winding down the foreground, widening towards the viewer.
    static func road(_ w: CGFloat, _ cy: CGFloat, _ off: CGPoint) -> Path {
        let x0 = 0.42
        let p0 = CGPoint(x: CGFloat(x0) * w + off.x, y: cy + CGFloat(foreY(x0)) + off.y + 3)
        let p3 = CGPoint(x: 0.58 * w + off.x, y: p0.y + 260)
        let p1 = CGPoint(x: 0.30 * w + off.x, y: p0.y + 80)
        let p2 = CGPoint(x: 0.80 * w + off.x, y: p0.y + 150)
        var left: [CGPoint] = []
        var right: [CGPoint] = []
        let n = 24
        for i in 0 ... n {
            let s = CGFloat(i) / CGFloat(n)
            let a = (1 - s) * (1 - s) * (1 - s), b = 3 * (1 - s) * (1 - s) * s, cc = 3 * (1 - s) * s * s, d = s * s * s
            let pt = CGPoint(x: a * p0.x + b * p1.x + cc * p2.x + d * p3.x, y: a * p0.y + b * p1.y + cc * p2.y + d * p3.y)
            let da = -3 * (1 - s) * (1 - s), db = 3 * (1 - s) * (1 - s) - 6 * (1 - s) * s, dc = 6 * (1 - s) * s - 3 * s * s, dd = 3 * s * s
            let tx = da * p0.x + db * p1.x + dc * p2.x + dd * p3.x
            let ty = da * p0.y + db * p1.y + dc * p2.y + dd * p3.y
            let len = max(0.001, hypot(tx, ty))
            let hw = 0.8 + 13 * s * s
            left.append(CGPoint(x: pt.x - ty / len * hw, y: pt.y + tx / len * hw))
            right.append(CGPoint(x: pt.x + ty / len * hw, y: pt.y - tx / len * hw))
        }
        var p = Path()
        p.move(to: left[0])
        for pt in left.dropFirst() { p.addLine(to: pt) }
        for pt in right.reversed() { p.addLine(to: pt) }
        p.closeSubpath()
        return p
    }

    // MARK: Grain

    /// A 128 × 128 tile of seeded noise, made once.
    static let grain: Image = {
        let n = 128
        var bytes = [UInt8](repeating: 0, count: n * n * 4)
        for i in 0 ..< n * n {
            let v = sin(Double(i) * 12.9898 + 78.233) * 43758.5453
            let r = v - floor(v)
            let g = UInt8(r > 0.5 ? 255 : 0)
            let a = UInt8(abs(r - 0.5) * 2 * 255)
            // premultiplied
            let pm = UInt8(Int(g) * Int(a) / 255)
            bytes[i * 4] = pm
            bytes[i * 4 + 1] = pm
            bytes[i * 4 + 2] = pm
            bytes[i * 4 + 3] = a
        }
        let space = CGColorSpaceCreateDeviceRGB()
        let made: CGImage? = bytes.withUnsafeMutableBytes { raw in
            CGContext(data: raw.baseAddress, width: n, height: n, bitsPerComponent: 8, bytesPerRow: n * 4,
                      space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)?.makeImage()
        }
        guard let image = made else { return Image(systemName: "circle") }
        return Image(decorative: image, scale: 2)
    }()

    static func grain(_ c: inout GraphicsContext, _ w: CGFloat, _ h: CGFloat, _ amount: Double) {
        var g = c
        g.opacity = amount
        g.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .tiledImage(grain, scale: 1))
    }
}
