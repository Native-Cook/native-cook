import UIKit

/*
 Icing Name Form — the marble counter and the cake (gold drum, fondant top, a ring of piped
 rosettes and dragées), baked once off the main thread at 2x. Skia's turbulence and fractal noise
 are replaced by a small seeded gradient noise baked into grey images and blended the same way.
 */

enum IcingNameFormNoise {
    private static func hash(_ x: Int, _ y: Int, _ seed: Int) -> Double {
        var h = UInt32(truncatingIfNeeded: x &* 374_761_393 &+ y &* 668_265_263 &+ seed &* 1_442_695_041)
        h = (h ^ (h >> 13)) &* 1_274_126_177
        h ^= h >> 16
        return Double(h & 0xFFFF) / 65535
    }

    private static func grad(_ ix: Int, _ iy: Int, _ seed: Int, _ dx: Double, _ dy: Double) -> Double {
        let a = hash(ix, iy, seed) * 2 * .pi
        return cos(a) * dx + sin(a) * dy
    }

    /// Gradient noise in about -0.7...0.7.
    static func noise(_ x: Double, _ y: Double, _ seed: Int) -> Double {
        let x0 = Int(floor(x)), y0 = Int(floor(y))
        let fx = x - Double(x0), fy = y - Double(y0)
        let u = fx * fx * fx * (fx * (fx * 6 - 15) + 10)
        let v = fy * fy * fy * (fy * (fy * 6 - 15) + 10)
        let n00 = grad(x0, y0, seed, fx, fy)
        let n10 = grad(x0 + 1, y0, seed, fx - 1, fy)
        let n01 = grad(x0, y0 + 1, seed, fx, fy - 1)
        let n11 = grad(x0 + 1, y0 + 1, seed, fx - 1, fy - 1)
        let a = n00 + (n10 - n00) * u
        let b = n01 + (n11 - n01) * u
        return a + (b - a) * v
    }

    /// A grey image `w` × `h` px; (fx, fy) is the base frequency per px. `turbulence` sums |noise|
    /// (Skia's turbulence), otherwise it is fractal noise around mid grey.
    static func image(w: Int, h: Int, fx: Double, fy: Double, octaves: Int, seed: Int, turbulence: Bool) -> CGImage? {
        var px = [UInt8](repeating: 0, count: w * h)
        for y in 0..<h {
            for x in 0..<w {
                var sum = 0.0
                var amp = 1.0
                var f = 1.0
                for o in 0..<octaves {
                    let n = noise(Double(x) * fx * f, Double(y) * fy * f, seed + o * 31)
                    sum += (turbulence ? abs(n) : n) * amp
                    amp *= 0.5
                    f *= 2
                }
                let v = turbulence ? sum * 1.4 : 0.5 + sum * 0.7
                px[y * w + x] = UInt8(max(0, min(255, v * 255)))
            }
        }
        guard let provider = CGDataProvider(data: Data(px) as CFData) else { return nil }
        return CGImage(
            width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: w,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }
}

enum IcingNameFormBake {
    static func ui(_ hex: UInt32, _ a: Double = 1) -> UIColor {
        UIColor(
            red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
            blue: CGFloat(hex & 255) / 255, alpha: a
        )
    }

    private static func gradient(_ colors: [UIColor], _ locations: [CGFloat]) -> CGGradient {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray, locations: locations)!
    }

    /// Fills `path` blurred like a Skia normal blur of `sigma`, drawing only its shadow.
    private static func blurFill(_ c: CGContext, _ path: CGPath, _ color: UIColor, _ sigma: CGFloat) {
        c.saveGState()
        // Shadow offset and blur are in device pixels, not affected by the CTM's 2x scale.
        let k = max(1, abs(c.ctm.a))
        let off: CGFloat = 4000
        c.setShadow(offset: CGSize(width: off * k, height: 0), blur: sigma * 2 * k, color: color.cgColor)
        c.translateBy(x: -off, y: 0)
        c.addPath(path)
        c.setFillColor(UIColor.black.cgColor)
        c.fillPath()
        c.restoreGState()
    }

    private static func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> CGPath {
        CGPath(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2), transform: nil)
    }

    private static func format() -> UIGraphicsImageRendererFormat {
        let f = UIGraphicsImageRendererFormat()
        f.scale = 2
        f.opaque = false
        f.preferredRange = .standard
        return f
    }

    /// The marble counter.
    static func table(_ g: IcingNameFormGeo) -> CGImage? {
        let w = CGFloat(g.w), h = CGFloat(g.h)
        guard w > 0, h > 0 else { return nil }
        let veins = IcingNameFormNoise.image(w: max(2, Int(w / 3)), h: max(2, Int(h / 3)), fx: 0.006 * 3, fy: 0.018 * 3, octaves: 4, seed: 21, turbulence: true)
        let grain = IcingNameFormNoise.image(w: 128, h: 128, fx: 0.9, fy: 0.9, octaves: 3, seed: 4, turbulence: false)
        let img = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format()).image { r in
            let c = r.cgContext
            c.drawLinearGradient(gradient([ui(0xF7F1EA), ui(0xEFE6DC), ui(0xE6DACD)], [0, 0.5, 1]), start: .zero, end: CGPoint(x: w * 0.4, y: h), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            if let veins {
                c.saveGState()
                c.setBlendMode(.multiply)
                c.setAlpha(0.1)
                c.interpolationQuality = .high
                c.draw(veins, in: CGRect(x: 0, y: 0, width: w, height: h))
                c.restoreGState()
            }
            if let grain {
                c.saveGState()
                c.setBlendMode(.multiply)
                c.setAlpha(0.04)
                c.draw(grain, in: CGRect(x: 0, y: 0, width: 128, height: 128), byTiling: true)
                c.restoreGState()
            }
        }
        return img.cgImage
    }

    private static func rosette(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ a0: CGFloat) {
        let path = CGMutablePath()
        let turns: CGFloat = 2.1
        for k in 0...60 {
            let u = CGFloat(k) / 60
            let ang = a0 + u * turns * .pi * 2
            let rr = r * (0.12 + 0.72 * u)
            let p = CGPoint(x: x + rr * cos(ang), y: y + rr * sin(ang))
            if k == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        blurFill(c, circle(x + r * 0.18, y + r * 0.3, r * 0.92), ui(0x6A3A2A, 0.3), r * 0.25)
        c.setLineCap(.round)
        c.setLineJoin(.round)
        let layers: [(CGFloat, UInt32, CGFloat, Double)] = [(0.46, 0xD79AA6, 0, 1), (0.34, 0xF0C3CB, 0.06, 1), (0.14, 0xFCE7EA, 0.12, 0.85)]
        for (w, hex, off, a) in layers {
            c.saveGState()
            c.translateBy(x: -r * off, y: -r * off * 1.2)
            c.setLineWidth(r * w)
            c.setStrokeColor(ui(hex, a).cgColor)
            c.addPath(path)
            c.strokePath()
            c.restoreGState()
        }
        // The star tip's grooves running along the swirl.
        for off: CGFloat in [-0.13, 0.13] {
            c.saveGState()
            c.translateBy(x: r * off * 0.6, y: r * off)
            c.setLineWidth(r * 0.045)
            c.setStrokeColor(ui(off < 0 ? 0xFFF3F5 : 0xB9707F, 0.55).cgColor)
            c.addPath(path)
            c.strokePath()
            c.restoreGState()
        }
    }

    /// The cake, centred in a square image of side (2 boardR + 48) pt.
    static func cake(_ g: IcingNameFormGeo) -> CGImage? {
        let side = CGFloat(g.side)
        guard side > 0 else { return nil }
        let o = side / 2
        let R = CGFloat(g.R), boardR = CGFloat(g.boardR)
        let crinkle = IcingNameFormNoise.image(w: Int(boardR * 2), h: Int(boardR * 2), fx: 0.12, fy: 0.12, octaves: 2, seed: 8, turbulence: true)
        let grain = IcingNameFormNoise.image(w: 128, h: 128, fx: 0.7, fy: 0.7, octaves: 2, seed: 12, turbulence: false)
        let img = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format()).image { r in
            let c = r.cgContext
            // Shadows of the drum and of the cake on it.
            blurFill(c, circle(o + 3, o + 10, boardR), ui(0x5C3B25, 0.22), 14)
            // Gold cake drum with crinkled foil: a sweep gradient drawn as thin wedges.
            let stops: [(CGFloat, UInt32)] = [(0, 0xE9CF8E), (0.28, 0xB98E3E), (0.5, 0xF6E2A8), (0.78, 0xA97C30), (1, 0xE9CF8E)]
            func sweep(_ u: CGFloat) -> UIColor {
                var i = 0
                while i < stops.count - 2 && u > stops[i + 1].0 { i += 1 }
                let (u0, a) = stops[i]
                let (u1, b) = stops[i + 1]
                let f = max(0, min(1, (u - u0) / (u1 - u0)))
                let ca = ui(a), cb = ui(b)
                var r0: CGFloat = 0, g0: CGFloat = 0, b0: CGFloat = 0, a0: CGFloat = 0
                var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
                ca.getRed(&r0, green: &g0, blue: &b0, alpha: &a0)
                cb.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
                return UIColor(red: r0 + (r1 - r0) * f, green: g0 + (g1 - g0) * f, blue: b0 + (b1 - b0) * f, alpha: 1)
            }
            c.saveGState()
            c.addPath(circle(o, o, boardR))
            c.clip()
            let n = 360
            for k in 0..<n {
                let a0 = CGFloat(k) / CGFloat(n) * 2 * .pi
                let a1 = CGFloat(k + 1) / CGFloat(n) * 2 * .pi + 0.004
                c.move(to: CGPoint(x: o, y: o))
                c.addArc(center: CGPoint(x: o, y: o), radius: boardR + 2, startAngle: a0, endAngle: a1, clockwise: false)
                c.closePath()
                c.setFillColor(sweep((CGFloat(k) + 0.5) / CGFloat(n)).cgColor)
                c.fillPath()
            }
            if let crinkle {
                c.setBlendMode(.overlay)
                c.setAlpha(0.18)
                c.draw(crinkle, in: CGRect(x: o - boardR, y: o - boardR, width: boardR * 2, height: boardR * 2))
            }
            c.restoreGState()
            c.setLineWidth(1.2)
            c.setStrokeColor(ui(0xFFF4D0, 0.6).cgColor)
            c.addPath(circle(o, o, boardR - 0.8))
            c.strokePath()
            blurFill(c, circle(o + 2, o + 5, R), ui(0x4A2C18, 0.35), 5)
            // Fondant top with a soft rounded lip at the edge.
            c.saveGState()
            c.addPath(circle(o, o, R))
            c.clip()
            c.drawRadialGradient(
                gradient([ui(0xFBF4EA), ui(0xF4E8D9), ui(0xE9D8C3)], [0, 0.6, 1]),
                startCenter: CGPoint(x: o - R * 0.2, y: o - R * 0.25), startRadius: 0,
                endCenter: CGPoint(x: o - R * 0.2, y: o - R * 0.25), endRadius: R * 1.25, options: [.drawsAfterEndLocation]
            )
            c.drawRadialGradient(
                gradient([ui(0xC9AE92, 0), ui(0xC9AE92, 0), ui(0xFFFFFF, 0.4), ui(0xB79878, 0.55)], [0, 0.86, 0.93, 1]),
                startCenter: CGPoint(x: o, y: o), startRadius: 0, endCenter: CGPoint(x: o, y: o), endRadius: R, options: []
            )
            if let grain {
                c.setBlendMode(.multiply)
                c.setAlpha(0.05)
                c.draw(grain, in: CGRect(x: 0, y: 0, width: 128, height: 128), byTiling: true)
            }
            c.restoreGState()
            // Rosettes round the edge, with gold dragées between them.
            let count = 16
            let rr = R * 0.12
            let ringR = R * 0.84
            for k in 0..<count {
                let a = CGFloat(k) / CGFloat(count) * .pi * 2
                rosette(c, o + ringR * cos(a), o + ringR * sin(a), rr, a * 1.7)
            }
            for k in 0..<count {
                let a = (CGFloat(k) + 0.5) / CGFloat(count) * .pi * 2
                let bx = o + ringR * 1.01 * cos(a)
                let by = o + ringR * 1.01 * sin(a)
                let br = R * 0.022
                c.saveGState()
                c.addPath(circle(bx, by, br))
                c.clip()
                c.drawRadialGradient(
                    gradient([ui(0xFFF6D8), ui(0xD9B25A), ui(0x8A6420)], [0, 0.45, 1]),
                    startCenter: CGPoint(x: bx - br * 0.4, y: by - br * 0.4), startRadius: 0,
                    endCenter: CGPoint(x: bx - br * 0.4, y: by - br * 0.4), endRadius: br * 1.4, options: [.drawsAfterEndLocation]
                )
                c.restoreGState()
            }
        }
        return img.cgImage
    }
}
