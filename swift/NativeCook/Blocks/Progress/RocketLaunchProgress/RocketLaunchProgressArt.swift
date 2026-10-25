import CoreGraphics
import CoreText
import SwiftUI
import UIKit

/*
 Rocket Launch Progress — layout and the art drawn once:
 - the back: a dusk sky (four stops), stars, a moon, two ridgelines, the ground, the pad with its
   flame trench, the service tower, floodlights, the fuel tank's shell and its line to the pad —
   one screen-sized image, baked off the main thread;
 - the rocket: a handful of static paths in its own frame (nozzle exit at the origin, nose up),
   drawn under a transform that the loop moves.
*/

enum RocketLaunchProgressPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255, opacity: a)
    }

    static let ink = hex(0xF3EEF8)
    static let inkSoft = hex(0xF3EEF8, 0.64)
    static let inkFaint = hex(0xF3EEF8, 0.4)
    static let amber = hex(0xFFA94D)
    static let night = hex(0x0B0D26)
    static let card = hex(0x17142A)
}

struct RocketLaunchProgressLayout: Equatable {
    let W: Double
    let H: Double
    let top: Double
    let groundY: Double
    let padX: Double
    let rocketH: Double
    /// Scale of the rocket's paths (designed 136 pt tall).
    let k: Double
    let tank: CGRect
    let cardTop: Double
    let cardH: Double
    let rowH: Double

    init(W: Double, H: Double, top: Double, bottom: Double) {
        self.W = W
        self.H = H
        self.top = top
        rowH = H < 740 ? 46 : 52
        cardH = rowH * 4 + 62
        cardTop = H - bottom - 12 - cardH
        groundY = cardTop - 30
        rocketH = max(96, min(150, (groundY - (top + 120)) * 0.5))
        padX = (W * 0.46).rounded()
        k = rocketH / 136
        let tankH = rocketH * 0.78
        tank = CGRect(x: padX + 70 * k + 10, y: groundY - 12 - tankH, width: 30, height: tankH)
    }

    var baseY: Double { groundY - 14 }
}

enum RocketLaunchProgressArt {
    struct Bitmap: @unchecked Sendable {
        let px: Double
        let image: CGImage
    }

    // MARK: - Helpers

    static func cg(_ hex: UInt32, _ a: Double = 1) -> CGColor {
        CGColor(srgbRed: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: a)
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> CGColor {
        CGColor(srgbRed: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }

    static func gradient(_ colors: [CGColor], _ pos: [CGFloat]?) -> CGGradient {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors as CFArray, locations: pos)!
    }

    static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGPath {
        CGPath(rect: CGRect(x: x, y: y, width: w, height: h), transform: nil)
    }

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> CGPath {
        CGPath(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2), transform: nil)
    }

    static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> CGPath {
        let k = min(r, w / 2, h / 2)
        return CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: k, cornerHeight: k, transform: nil)
    }

    static func fill(_ c: CGContext, _ p: CGPath, _ color: CGColor) {
        c.setFillColor(color)
        c.addPath(p)
        c.fillPath()
    }

    static func fillLinear(_ c: CGContext, _ path: CGPath, _ p0: CGPoint, _ p1: CGPoint, _ colors: [CGColor], _ pos: [CGFloat]? = nil) {
        c.saveGState()
        c.addPath(path)
        c.clip()
        c.drawLinearGradient(gradient(colors, pos), start: p0, end: p1, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        c.restoreGState()
    }

    static func fillRadial(_ c: CGContext, _ path: CGPath, _ center: CGPoint, _ r: Double, _ colors: [CGColor], _ pos: [CGFloat]? = nil) {
        c.saveGState()
        c.addPath(path)
        c.clip()
        c.drawRadialGradient(gradient(colors, pos), startCenter: center, startRadius: 0, endCenter: center, endRadius: r, options: [.drawsAfterEndLocation])
        c.restoreGState()
    }

    /// Fills `path` blurred (a Gaussian of `sigma` pt): drawn far off to the left, its shadow falls back.
    static func blurred(_ c: CGContext, _ path: CGPath, sigma: Double, px: Double, color: CGColor) {
        let far = 40000.0
        c.saveGState()
        c.setShadow(offset: CGSize(width: far, height: 0), blur: CGFloat(sigma * 2 * px), color: color)
        let m = c.ctm.inverted()
        let d = CGPoint(x: -far, y: 0).applying(CGAffineTransform(a: m.a, b: m.b, c: m.c, d: m.d, tx: 0, ty: 0))
        c.translateBy(x: d.x, y: d.y)
        c.setFillColor(CGColor(gray: 0, alpha: 1))
        c.addPath(path)
        c.fillPath()
        c.restoreGState()
    }

    static let hash: (Double) -> Double = { n in
        let v = sin(n * 127.1 + 311.7) * 43758.5453
        return v - floor(v)
    }

    private static func ridge(_ c: CGContext, _ W: Double, _ base: Double, _ amp: Double, _ seed: Double, _ color: CGColor) {
        let p = CGMutablePath()
        p.move(to: CGPoint(x: 0, y: base + 40))
        var x = 0.0
        while x <= W + 20 {
            let h = sin(x * 0.012 + seed) * 0.5 + sin(x * 0.031 + seed * 2.1) * 0.3 + hash(floor(x / 20) + seed * 10) * 0.2
            p.addLine(to: CGPoint(x: x, y: base - amp * (0.5 + h)))
            x += 20
        }
        p.addLine(to: CGPoint(x: W + 20, y: base + 40))
        p.closeSubpath()
        fill(c, p, color)
    }

    // MARK: - The back

    static func bakeBack(_ L: RocketLaunchProgressLayout, px: Double) -> Bitmap {
        let W = L.W, H = L.H, g = L.groundY, padX = L.padX, tank = L.tank, k = L.k
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        format.preferredRange = .standard
        let img = UIGraphicsImageRenderer(size: CGSize(width: ceil(W * px), height: ceil(H * px)), format: format).image { ctx in
            let c = ctx.cgContext
            c.scaleBy(x: px, y: px)
            fill(c, rect(0, 0, W, H), cg(0x0B0D26))
            // Sky: night overhead, violet, a warm band at the horizon.
            fillLinear(c, rect(0, 0, W, g), .zero, CGPoint(x: 0, y: g), [cg(0x090B22), cg(0x1C1844), cg(0x4E2C5E), cg(0xC46A58)], [0, 0.42, 0.78, 1])
            for i in 0..<170 {
                let id = Double(i)
                let x = hash(id * 3.7) * W
                let y = hash(id * 5.1) * g * 0.62
                let r = 0.4 + hash(id * 9.3) * 0.9
                fill(c, circle(x, y, r), cg(0xFFF6E6, (0.25 + hash(id * 2.2) * 0.65) * (1 - y / (g * 0.7))))
            }
            let mx = W * 0.82
            let my = L.top + 150
            fillRadial(c, circle(mx, my, 46), CGPoint(x: mx, y: my), 46, [rgba(255, 236, 200, 0.28), rgba(255, 220, 180, 0)])
            fillRadial(c, circle(mx, my, 13), CGPoint(x: mx - 4, y: my - 4), 16, [cg(0xFFF8E8), cg(0xEADBBE), cg(0xBFAE90)], [0, 0.6, 1])
            ridge(c, W, g - 34, 46, 1.3, cg(0x3B2550, 0.85))
            ridge(c, W, g - 8, 30, 4.1, cg(0x22183A))
            // Ground: dark, warm under the pad, a horizon haze.
            fillLinear(c, rect(0, g - 4, W, H - g + 4), CGPoint(x: 0, y: g - 4), CGPoint(x: 0, y: H), [cg(0x211A2E), cg(0x140F1F), cg(0x0D0A15)], [0, 0.3, 1])
            blurred(c, rect(0, g - 26, W, 30), sigma: 10, px: px, color: cg(0xE39A7A, 0.14))
            // Floodlights: two poles, their pools of light on the pad.
            for side in [-1.0, 1.0] {
                let lx = padX + side * 150
                fill(c, rect(lx - 1, g - 120 * k, 2, 120 * k), cg(0x3A3150))
                fill(c, circle(lx, g - 120 * k, 3), cg(0xFFF4D6))
                fillRadial(c, circle(lx, g - 120 * k, 16), CGPoint(x: lx, y: g - 120 * k), 16, [rgba(255, 240, 200, 0.5), rgba(255, 240, 200, 0)])
            }
            fillRadial(c, CGPath(ellipseIn: CGRect(x: padX - 150, y: g - 18, width: 300, height: 36), transform: nil), CGPoint(x: padX, y: g), 150,
                       [rgba(255, 226, 180, 0.24), rgba(255, 200, 150, 0.06), rgba(0, 0, 0, 0)], [0, 0.6, 1])

            // The service tower: two rails, X braces, arms, a red beacon.
            let tx0 = padX - 52 * k
            let tx1 = padX - 34 * k
            let tTop = g - 14 - L.rocketH * 1.12
            for x in [tx0, tx1] {
                fillLinear(c, rect(x - 1.5, tTop, 3, g - tTop), CGPoint(x: tx0, y: 0), CGPoint(x: tx1, y: 0), [cg(0x2E2742), cg(0x5A4E72), cg(0x2A2340)], [0, 0.5, 1])
            }
            c.setStrokeColor(cg(0x4A3F62))
            c.setLineWidth(1.2)
            let step = 14 * k
            var y = tTop
            while y < g - 14 {
                c.move(to: CGPoint(x: tx0, y: y)); c.addLine(to: CGPoint(x: tx1, y: y + step))
                c.move(to: CGPoint(x: tx1, y: y)); c.addLine(to: CGPoint(x: tx0, y: y + step))
                c.move(to: CGPoint(x: tx0, y: y)); c.addLine(to: CGPoint(x: tx1, y: y))
                c.strokePath()
                y += step
            }
            for f in [0.25, 0.6] {
                let ay = g - 14 - L.rocketH * f
                fill(c, rect(tx1, ay - 1.5, padX - 15 * k - tx1, 3), cg(0x4E4366))
            }
            fillRadial(c, circle(tx0 + 9 * k, tTop - 3, 8), CGPoint(x: tx0 + 9 * k, y: tTop - 3), 8, [rgba(255, 80, 60, 0.7), rgba(255, 60, 40, 0)])
            fill(c, circle(tx0 + 9 * k, tTop - 3, 2), cg(0xFF6A50))

            // The pad: a concrete deck with a lit top edge and the trench under the nozzle.
            let pw = 128 * k
            blurred(c, rect(padX - pw / 2 - 6, g - 2, pw + 12, 10), sigma: 5, px: px, color: cg(0x000000, 0.5))
            fillLinear(c, rect(padX - pw / 2, g - 14, pw, 14), CGPoint(x: 0, y: g - 14), CGPoint(x: 0, y: g), [cg(0x6C6280), cg(0x463D58), cg(0x2C2539)], [0, 0.3, 1])
            fill(c, rect(padX - pw / 2, g - 14, pw, 1), cg(0xB8AEC9, 0.8))
            fillLinear(c, rect(padX - 12 * k, g - 14, 24 * k, 14), CGPoint(x: 0, y: g - 14), CGPoint(x: 0, y: g), [cg(0x120E1A), cg(0x2A2238)])

            // The fuel line from the tank's foot to the pad.
            c.setStrokeColor(cg(0x5B5070))
            c.setLineWidth(4)
            c.move(to: CGPoint(x: tank.midX, y: tank.maxY))
            c.addQuadCurve(to: CGPoint(x: padX + pw / 2 - 4, y: g - 8), control: CGPoint(x: tank.midX, y: g - 8))
            c.strokePath()
            // The tank's cradle and its dark well (the fuel is drawn over it each frame).
            fillLinear(c, rect(tank.minX - 4, g - 14, tank.width + 8, 14), CGPoint(x: 0, y: g - 14), CGPoint(x: 0, y: g), [cg(0x5E5474), cg(0x2E273D)])
            fillLinear(c, rr(tank.minX - 3, tank.minY - 3, tank.width + 6, tank.height + 6, tank.width / 2 + 3),
                       CGPoint(x: tank.minX, y: 0), CGPoint(x: tank.maxX, y: 0), [cg(0x4C4462), cg(0x9C93B4), cg(0x3A3350)], [0, 0.4, 1])
            fillLinear(c, rr(tank.minX, tank.minY, tank.width, tank.height, tank.width / 2),
                       CGPoint(x: 0, y: tank.minY), CGPoint(x: 0, y: tank.maxY), [cg(0x1A1526), cg(0x110D1A)])
            let font = UIFont.systemFont(ofSize: 8, weight: .bold) as CTFont
            let attrs: [NSAttributedString.Key: Any] = [
                NSAttributedString.Key(kCTFontAttributeName as String): font,
                NSAttributedString.Key(kCTForegroundColorAttributeName as String): cg(0xCFC6E2, 0.7),
            ]
            let line = CTLineCreateWithAttributedString(NSAttributedString(string: "FUEL", attributes: attrs))
            c.saveGState()
            c.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
            c.textPosition = CGPoint(x: tank.maxX + 7, y: tank.minY + 10)
            CTLineDraw(line, c)
            c.restoreGState()

            // Grain over the sky and ground.
            let light = cg(0xFFFFFF, 0.035)
            let dark = cg(0x000000, 0.035)
            for i in 0..<5000 {
                let id = Double(i)
                c.setFillColor(hash(id * 4.4) > 0.5 ? light : dark)
                c.fill(CGRect(x: hash(id * 1.9) * W, y: hash(id * 2.6 + 3) * H, width: 0.7, height: 0.7))
            }
        }
        return Bitmap(px: px, image: img.cgImage!)
    }

    // MARK: - The rocket

    /// The rocket's parts, nozzle exit at (0, 0), nose up, 136 pt tall; scaled by k when drawn.
    struct Parts {
        let nozzle: Path
        let body: Path
        let nose: Path
        let finL: Path
        let finR: Path
        let finC: Path
        let band: Path
    }

    static let parts: Parts = {
        func P(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }
        var nozzle = Path()
        nozzle.move(to: P(-8, 0)); nozzle.addLine(to: P(8, 0)); nozzle.addQuadCurve(to: P(5, -12), control: P(6, -6))
        nozzle.addLine(to: P(-5, -12)); nozzle.addQuadCurve(to: P(-8, 0), control: P(-6, -6)); nozzle.closeSubpath()
        var body = Path()
        body.move(to: P(-15, -12)); body.addLine(to: P(15, -12)); body.addLine(to: P(15, -96)); body.addLine(to: P(-15, -96)); body.closeSubpath()
        var nose = Path()
        nose.move(to: P(-15, -96)); nose.addCurve(to: P(0, -136), control1: P(-15, -114), control2: P(-8, -128))
        nose.addCurve(to: P(15, -96), control1: P(8, -128), control2: P(15, -114)); nose.closeSubpath()
        var finL = Path()
        finL.move(to: P(-15, -16)); finL.addLine(to: P(-28, -6)); finL.addQuadCurve(to: P(-15, -46), control: P(-30, -24)); finL.closeSubpath()
        var finR = Path()
        finR.move(to: P(15, -16)); finR.addLine(to: P(28, -6)); finR.addQuadCurve(to: P(15, -46), control: P(30, -24)); finR.closeSubpath()
        var finC = Path()
        finC.move(to: P(-2.5, -8)); finC.addLine(to: P(2.5, -8)); finC.addLine(to: P(2.5, -40)); finC.addLine(to: P(-2.5, -40)); finC.closeSubpath()
        var band = Path()
        band.move(to: P(-15, -58)); band.addLine(to: P(15, -58)); band.addLine(to: P(15, -64)); band.addLine(to: P(-15, -64)); band.closeSubpath()
        return Parts(nozzle: nozzle, body: body, nose: nose, finL: finL, finR: finR, finC: finC, band: band)
    }()
}
