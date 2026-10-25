import SwiftUI
import UIKit
import CoreImage

/// Static art of the Crayon Rubbing Card, made once off the main thread: the plaque's hidden relief
/// as a height image (white = raised), how much relief each grid cell holds, the felt's grain and
/// the demo's percentage curve. Also the room and the crayon, drawn with SwiftUI's Canvas.
enum CrayonRubbingCardArt {
    typealias M = CrayonRubbingCardMotion

    struct Baked {
        /// Height image of the relief over the paper, 2 px per point.
        let relief: CGImage
        let weights: [Double]
        let total: Double
        let felt: CGImage
        /// The demo's shown percentage at 60 Hz over one loop.
        let shown: [Double]
    }

    static let reliefBake = 2.0

    static let shared: Task<Baked, Never> = Task.detached(priority: .userInitiated) { make() }

    static func make() -> Baked {
        let relief = bakeRelief()
        let (weights, total) = reliefWeights(relief)
        return Baked(relief: relief, weights: weights, total: total,
                     felt: noise(w: 100, h: 100, fx: 0.7, fy: 0.7, octaves: 3, seed: 8),
                     shown: M.shownTable(weights: weights, total: total))
    }

    // MARK: Relief

    private static func centredText(_ text: String, x: Double, y: Double, size: Double, track: Double) {
        let font = UIFont(name: "Georgia-Bold", size: size) ?? .boldSystemFont(ofSize: size)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white]
        let widths = text.map { (String($0) as NSString).size(withAttributes: attrs).width }
        let total = widths.reduce(0) { $0 + $1 + track } - track
        var at = x - total / 2
        for (i, ch) in text.enumerated() {
            (String(ch) as NSString).draw(at: CGPoint(x: at, y: y - font.ascender), withAttributes: attrs)
            at += widths[i] + track
        }
    }

    private static func white(_ c: CGContext, _ a: Double) {
        c.setFillColor(UIColor(white: 1, alpha: a).cgColor)
        c.setStrokeColor(UIColor(white: 1, alpha: a).cgColor)
    }

    private static func stroke(_ c: CGContext, _ path: CGPath, _ width: Double, _ alpha: Double, gray: Double = 1) {
        c.setStrokeColor(UIColor(white: gray, alpha: alpha).cgColor)
        c.setLineWidth(width)
        c.setLineCap(.round)
        c.setLineJoin(.round)
        c.addPath(path)
        c.strokePath()
    }

    private static func fill(_ c: CGContext, _ path: CGPath, _ alpha: Double, gray: Double = 1) {
        c.setFillColor(UIColor(white: gray, alpha: alpha).cgColor)
        c.addPath(path)
        c.fillPath()
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> CGPath {
        CGPath(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2), transform: nil)
    }

    /// The heron: body, S-curved neck, head with crest and beak, folded wing, legs. Paper points.
    private static func heron(_ c: CGContext) {
        let body = CGMutablePath()
        body.move(to: CGPoint(x: 146, y: 168))
        body.addCurve(to: CGPoint(x: 226, y: 206), control1: CGPoint(x: 170, y: 160), control2: CGPoint(x: 204, y: 172))
        body.addCurve(to: CGPoint(x: 244, y: 236), control1: CGPoint(x: 232, y: 216), control2: CGPoint(x: 236, y: 226))
        body.addCurve(to: CGPoint(x: 204, y: 236), control1: CGPoint(x: 226, y: 232), control2: CGPoint(x: 214, y: 230))
        body.addCurve(to: CGPoint(x: 148, y: 222), control1: CGPoint(x: 186, y: 246), control2: CGPoint(x: 160, y: 240))
        body.addCurve(to: CGPoint(x: 146, y: 168), control1: CGPoint(x: 138, y: 206), control2: CGPoint(x: 136, y: 182))
        body.closeSubpath()
        fill(c, body, 0.95)
        let neck = CGMutablePath()
        neck.move(to: CGPoint(x: 150, y: 176))
        neck.addCurve(to: CGPoint(x: 140, y: 116), control1: CGPoint(x: 126, y: 160), control2: CGPoint(x: 152, y: 132))
        neck.addCurve(to: CGPoint(x: 120, y: 90), control1: CGPoint(x: 132, y: 104), control2: CGPoint(x: 118, y: 100))
        stroke(c, neck, 11, 0.95)
        fill(c, circle(122, 88, 8.5), 0.97)
        let beak = CGMutablePath()
        beak.move(to: CGPoint(x: 116, y: 84))
        beak.addLine(to: CGPoint(x: 70, y: 96))
        beak.addLine(to: CGPoint(x: 116, y: 93))
        beak.closeSubpath()
        fill(c, beak, 1)
        let crest = CGMutablePath()
        crest.move(to: CGPoint(x: 126, y: 82))
        crest.addCurve(to: CGPoint(x: 160, y: 74), control1: CGPoint(x: 140, y: 76), control2: CGPoint(x: 150, y: 78))
        stroke(c, crest, 2.4, 0.9)
        let wing = CGMutablePath()
        wing.move(to: CGPoint(x: 162, y: 178))
        wing.addCurve(to: CGPoint(x: 236, y: 230), control1: CGPoint(x: 188, y: 176), control2: CGPoint(x: 214, y: 196))
        wing.addCurve(to: CGPoint(x: 164, y: 208), control1: CGPoint(x: 210, y: 222), control2: CGPoint(x: 182, y: 222))
        wing.addCurve(to: CGPoint(x: 162, y: 178), control1: CGPoint(x: 154, y: 198), control2: CGPoint(x: 154, y: 186))
        wing.closeSubpath()
        fill(c, wing, 1)
        for i in 0..<3 {
            let d = Double(i)
            let cut = CGMutablePath()
            cut.move(to: CGPoint(x: 172 + d * 14, y: 190 + d * 6))
            cut.addCurve(to: CGPoint(x: 212 + d * 10, y: 222), control1: CGPoint(x: 186 + d * 14, y: 198 + d * 6),
                         control2: CGPoint(x: 200 + d * 14, y: 210 + d * 6))
            stroke(c, cut, 1.6, 1, gray: 0x8A / 255.0)
        }
        fill(c, circle(124, 86, 1.8), 1, gray: 0x9A / 255.0)
        let legs = CGMutablePath()
        legs.move(to: CGPoint(x: 184, y: 238)); legs.addLine(to: CGPoint(x: 180, y: 272)); legs.addLine(to: CGPoint(x: 184, y: 304))
        legs.move(to: CGPoint(x: 198, y: 236)); legs.addLine(to: CGPoint(x: 204, y: 270)); legs.addLine(to: CGPoint(x: 200, y: 304))
        legs.move(to: CGPoint(x: 172, y: 306)); legs.addLine(to: CGPoint(x: 186, y: 304)); legs.addLine(to: CGPoint(x: 196, y: 308))
        legs.move(to: CGPoint(x: 192, y: 306)); legs.addLine(to: CGPoint(x: 202, y: 304)); legs.addLine(to: CGPoint(x: 214, y: 308))
        stroke(c, legs, 3.2, 0.92)
    }

    private static func reeds(_ c: CGContext) {
        let stems: [(Double, Double, Double)] = [(246, 304, 150), (258, 304, 132), (270, 306, 170), (284, 306, 196), (54, 306, 188), (66, 306, 214)]
        for (x, y0, top) in stems {
            let bend = (x - 160) * 0.08
            let stem = CGMutablePath()
            stem.move(to: CGPoint(x: x, y: y0))
            stem.addQuadCurve(to: CGPoint(x: x + bend * 1.6, y: top), control: CGPoint(x: x + bend, y: (y0 + top) / 2))
            stroke(c, stem, 2.2, 0.85)
            if top < 180 {
                fill(c, CGPath(ellipseIn: CGRect(x: x + bend * 1.6 - 3.2, y: top - 20, width: 6.4, height: 22), transform: nil), 0.9)
            }
            let side = x > 160 ? 1.0 : -1.0
            let blade = CGMutablePath()
            blade.move(to: CGPoint(x: x + bend * 0.5, y: y0 - 40))
            blade.addQuadCurve(to: CGPoint(x: x + bend * 0.5 + side * 30, y: y0 - 96), control: CGPoint(x: x + bend * 0.5 + side * 18, y: y0 - 70))
            stroke(c, blade, 2.6, 0.8)
        }
    }

    private static func reliefShapes(_ c: CGContext) {
        let w = M.paper.width, h = M.paper.height
        stroke(c, CGPath(roundedRect: CGRect(x: 16, y: 16, width: w - 32, height: h - 32), cornerWidth: 6, cornerHeight: 6, transform: nil), 4, 0.95)
        stroke(c, CGPath(roundedRect: CGRect(x: 25, y: 25, width: w - 50, height: h - 50), cornerWidth: 3, cornerHeight: 3, transform: nil), 1.6, 0.85)
        for (x, y) in [(25.0, 25.0), (w - 25, 25), (25, h - 25), (w - 25, h - 25)] {
            fill(c, circle(x, y, 7), 1)
            for k in 0..<8 {
                let a = Double(k) * Double.pi / 4
                fill(c, circle(x + cos(a) * 10.5, y + sin(a) * 10.5, 2.4), 0.9)
            }
        }
        // A low sun behind the heron: a ring and its rays.
        stroke(c, circle(206, 128, 46), 3, 0.6)
        for k in 0..<14 {
            let a = Double.pi + Double(k) * Double.pi / 13
            let r0 = 54.0, r1 = 70 + Double(k % 2) * 10
            let ray = CGMutablePath()
            ray.move(to: CGPoint(x: 206 + cos(a) * r0, y: 128 + sin(a) * r0))
            ray.addLine(to: CGPoint(x: 206 + cos(a) * r1, y: 128 + sin(a) * r1))
            stroke(c, ray, 2, 0.5)
        }
        reeds(c)
        heron(c)
        for i in 0..<4 {
            let y = 314 + Double(i) * 9
            let path = CGMutablePath()
            let x0 = 44 + Double(i) * 10, x1 = w - 44 - Double(i) * 10
            var x = x0
            while x <= x1 {
                let yy = y + sin(x / 22 * Double.pi * 2 + Double(i)) * 1.8
                if x == x0 { path.move(to: CGPoint(x: x, y: yy)) } else { path.addLine(to: CGPoint(x: x, y: yy)) }
                x += 3
            }
            stroke(c, path, 2, 0.7 - Double(i) * 0.1)
        }
        white(c, 1)
        centredText("THE RIVER KEEPS", x: w / 2, y: 372, size: 19, track: 2.4)
        centredText("ITS OWN TIME", x: w / 2, y: 394, size: 19, track: 2.4)
    }

    /// The relief's height image: black flat, white raised, softened (σ 1.6 pt) into sloped edges.
    static func bakeRelief() -> CGImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = reliefBake
        format.opaque = true
        format.preferredRange = .standard
        let sharp = UIGraphicsImageRenderer(size: M.paper.size, format: format).image { r in
            let c = r.cgContext
            c.setFillColor(UIColor.black.cgColor)
            c.fill(CGRect(origin: .zero, size: M.paper.size))
            reliefShapes(c)
        }
        let input = CIImage(cgImage: sharp.cgImage!)
        let blurred = input.clampedToExtent()
            .applyingGaussianBlur(sigma: 1.6 * reliefBake)
            .cropped(to: input.extent)
        let ci = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
        return ci.createCGImage(blurred, from: input.extent, format: .RGBA8, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!) ?? sharp.cgImage!
    }

    /// How much raised relief each grid cell holds (0 … 1), and their sum, for the reveal %.
    static func reliefWeights(_ relief: CGImage) -> ([Double], Double) {
        let W = relief.width, H = relief.height
        var px = [UInt8](repeating: 0, count: W * H * 4)
        let ctx = CGContext(data: &px, width: W, height: H, bitsPerComponent: 8, bytesPerRow: W * 4,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        ctx?.draw(relief, in: CGRect(x: 0, y: 0, width: W, height: H))
        var weights = [Double](repeating: 0, count: M.gx * M.gy)
        let step = Int(M.cell * reliefBake)
        for gy in 0..<M.gy {
            for gx in 0..<M.gx {
                var raised = 0, n = 0
                var y = gy * step
                while y < min(H, (gy + 1) * step) {
                    var x = gx * step
                    while x < min(W, (gx + 1) * step) {
                        if Double(px[(y * W + x) * 4]) / 255 > 0.35 { raised += 1 }
                        n += 1
                        x += 2
                    }
                    y += 2
                }
                weights[gy * M.gx + gx] = n > 0 ? Double(raised) / Double(n) : 0
            }
        }
        return (weights, weights.reduce(0, +))
    }

    // MARK: Noise

    private static func hash(_ x: Int, _ y: Int, _ seed: Int) -> Double {
        var h = UInt32(truncatingIfNeeded: x &* 374_761_393 &+ y &* 668_265_263 &+ seed &* 1_442_695_041)
        h = (h ^ (h >> 13)) &* 1_274_126_177
        h ^= h >> 16
        return Double(h & 0xFFFF) / 65535
    }

    /// Grey fractal value noise, 1 px per point, tiling where each octave's cells fit the size.
    static func noise(w: Int, h: Int, fx: Double, fy: Double, octaves: Int, seed: Int) -> CGImage {
        var bytes = [UInt8](repeating: 255, count: w * h * 4)
        for y in 0..<h {
            for x in 0..<w {
                var v = 0.0, a = 0.5, total = 0.0
                for o in 0..<octaves {
                    let f = Double(1 << o)
                    let px = max(1, Int((Double(w) * fx * f).rounded()))
                    let py = max(1, Int((Double(h) * fy * f).rounded()))
                    let gx = Double(x) / Double(w) * Double(px), gy = Double(y) / Double(h) * Double(py)
                    let ix = Int(floor(gx)), iy = Int(floor(gy))
                    let ux = gx - Double(ix), uy = gy - Double(iy)
                    let sx = ux * ux * (3 - 2 * ux), sy = uy * uy * (3 - 2 * uy)
                    let s = seed + o * 101
                    let top = hash(ix % px, iy % py, s) + (hash((ix + 1) % px, iy % py, s) - hash(ix % px, iy % py, s)) * sx
                    let bottom = hash(ix % px, (iy + 1) % py, s) + (hash((ix + 1) % px, (iy + 1) % py, s) - hash(ix % px, (iy + 1) % py, s)) * sx
                    v += a * (top + (bottom - top) * sy)
                    total += a
                    a *= 0.5
                }
                let b = UInt8(min(max(0.5 + (v / total - 0.5) * 1.7, 0), 1) * 255)
                let i = (y * w + x) * 4
                bytes[i] = b; bytes[i + 1] = b; bytes[i + 2] = b
            }
        }
        return image(bytes, w, h)
    }

    static func image(_ bytes: [UInt8], _ w: Int, _ h: Int) -> CGImage {
        let data = CFDataCreate(nil, bytes, bytes.count)!
        return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
                       space: CGColorSpace(name: CGColorSpace.sRGB)!,
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                       provider: CGDataProvider(data: data)!, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
    }

    /// The rubbed field as a tiny grey image (one pixel per cell).
    static func fieldImage(_ grid: [Double]) -> CGImage {
        var bytes = [UInt8](repeating: 255, count: M.gx * M.gy * 4)
        for i in 0..<(M.gx * M.gy) {
            let v = UInt8((min(max(grid[i], 0), 1) * 255).rounded())
            bytes[i * 4] = v; bytes[i * 4 + 1] = v; bytes[i * 4 + 2] = v
        }
        return image(bytes, M.gx, M.gy)
    }

    // MARK: The room

    private static func stops(_ list: [(Color, Double)]) -> Gradient { Gradient(stops: list.map { .init(color: $0.0, location: $0.1) }) }

    /// The museum table: warm grey felt with grain, a pool of gallery light, a vignette.
    static func drawRoom(_ ctx: GraphicsContext, size: CGSize, ox: Double, oy: Double, k: Double, art: Baked?) {
        let w = size.width, h = size.height
        let all = Path(CGRect(origin: .zero, size: size))
        ctx.fill(all, with: .linearGradient(stops([(Color(hex: 0x2E2925), 0), (Color(hex: 0x24201C), 0.5), (Color(hex: 0x191613), 1)]),
                                            startPoint: .zero, endPoint: CGPoint(x: w * 0.3, y: h)))
        if let art {
            var g = ctx
            g.blendMode = .softLight
            g.opacity = 0.3
            g.fill(all, with: .tiledImage(Image(decorative: art.felt, scale: 1)))
        }
        ctx.fill(all, with: .radialGradient(stops([(.rgba(255, 226, 180, 0.16), 0), (.rgba(255, 226, 180, 0.05), 0.5), (.rgba(255, 226, 180, 0), 1)]),
                                            center: CGPoint(x: ox + 196 * k, y: oy + 330 * k), startRadius: 0, endRadius: 380 * k))
        ctx.fill(all, with: .radialGradient(stops([(.clear, 0), (.clear, 0.55), (.rgba(6, 4, 3, 0.6), 1)]),
                                            center: CGPoint(x: w / 2, y: h * 0.45), startRadius: 0, endRadius: h * 0.75))
    }

    // MARK: The crayon

    /// The crayon: its shadow (tight while it presses, wider and softer when lifted), the wax stick
    /// with a worn tip and a sheen, and the paper wrapper with its name. (x, y) is the tip in canvas
    /// points; the stick runs along +x, turned by `angle` degrees.
    static func drawCrayon(_ ctx: GraphicsContext, x: Double, y: Double, angle: Double, contact: Double, tones: [Color], name: String) {
        var c = ctx
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(angle))
        let up = 1 - contact
        c.drawLayer { l in
            l.addFilter(.blur(radius: 3 + 3 * up))
            l.fill(Path(roundedRect: CGRect(x: 4 + 7 * up, y: -9 + 3 + 12 * up, width: 92, height: 18), cornerRadius: 5),
                   with: .color(Color(hex: 0x0A0806).opacity(0.5 - 0.18 * up)))
        }
        c.fill(Path(roundedRect: CGRect(x: 8, y: -9, width: 88, height: 18), cornerRadius: 4),
               with: .linearGradient(stops([(tones[0], 0), (tones[1], 0.5), (tones[2], 1)]), startPoint: CGPoint(x: 0, y: -9), endPoint: CGPoint(x: 0, y: 9)))
        var tip = Path()
        tip.move(to: CGPoint(x: 10, y: -9))
        tip.addLine(to: CGPoint(x: 1, y: -3))
        tip.addQuadCurve(to: CGPoint(x: 1, y: 3), control: CGPoint(x: -1, y: 0))
        tip.addLine(to: CGPoint(x: 10, y: 9))
        tip.closeSubpath()
        c.fill(tip, with: .color(tones[1]))
        var facet = Path()
        facet.move(to: CGPoint(x: 10, y: -9))
        facet.addLine(to: CGPoint(x: 1, y: -3))
        facet.addLine(to: CGPoint(x: 3, y: -3))
        facet.addLine(to: CGPoint(x: 11, y: -7))
        facet.closeSubpath()
        c.fill(facet, with: .color(.white.opacity(0.22)))
        c.fill(Path(CGRect(x: 32, y: -9.6, width: 56, height: 19.2)),
               with: .linearGradient(stops([(Color(hex: 0xFBF4E2), 0), (Color(hex: 0xEADCBE), 0.55), (Color(hex: 0xCDBB98), 1)]),
                                     startPoint: CGPoint(x: 0, y: -9.6), endPoint: CGPoint(x: 0, y: 9.6)))
        c.fill(Path(CGRect(x: 32, y: -6.6, width: 56, height: 1.6)), with: .color(tones[1]))
        c.fill(Path(CGRect(x: 32, y: 5, width: 56, height: 1.6)), with: .color(tones[1]))
        let label = c.resolve(Text(name).font(.system(size: 6.5, weight: .bold)).foregroundStyle(tones[2]))
        c.draw(label, at: CGPoint(x: 39, y: 2.4 + 6.5 * 0.22), anchor: .bottomLeading)
        c.fill(Path(CGRect(x: 12, y: -5.5, width: 82, height: 1.2)), with: .color(.white.opacity(0.3)))
    }
}
