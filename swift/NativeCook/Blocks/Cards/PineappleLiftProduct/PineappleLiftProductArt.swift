import SwiftUI
import UIKit

/// Layout, artwork and drawing of Pineapple Lift Product, in a 393 pt wide design space.
enum PineappleLiftProductArt {
    typealias M = PineappleLiftProductMotion

    // MARK: Palette

    static let ink: UInt32 = 0x2A1803
    static let ink2: UInt32 = 0x3A2304
    static let cream: UInt32 = 0xFFF4D6
    static let skyTop: UInt32 = 0xFFCB2E
    static let skyMid: UInt32 = 0xFFB506
    static let skyLow: UInt32 = 0xF7A100
    static let haze: UInt32 = 0xFFE27A
    static let glowTint: UInt32 = 0xFFF1B8
    static let juice: UInt32 = 0xFFE58A
    static let juiceRim: UInt32 = 0xB86A00
    static let shade: UInt32 = 0x7A4100
    static let petal: UInt32 = 0xC8361E

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }

    static func uiColor(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
    }

    // MARK: Artwork (bundled px)

    static let pine = 0.345 // pt per px of the pineapple halves
    static let topSize = CGSize(width: 632, height: 837)
    static let topPivot = CGPoint(x: 229.6, y: 748) // centre of its cut face
    static let topTilt = 16.7 * Double.pi / 180 // the cut face's tilt in the image
    static let bottomSize = CGSize(width: 488, height: 366)
    static let bottomFace = CGPoint(x: 242.4, y: 66) // centre of its cut face
    static let faceRx = 218.0, faceRy = 53.0
    static let bottleSize = CGSize(width: 276, height: 716)
    static let bottleH = 200.0
    static var bottleScale: Double { bottleH / Double(bottleSize.height) }
    static let flowerSize = CGSize(width: 400, height: 392)

    private static func load(_ name: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: "PineappleLiftProduct-\(name)", ofType: "webp"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }

    static let topImage = load("pineapple-top")
    static let bottomImage = load("pineapple-bottom")
    static let flowerImage = load("hibiscus")

    /// The bottle with its label printed once (3×), so a frame draws one bitmap.
    static let bottleImage: UIImage = {
        let bottle = load("bottle")
        let w = Double(bottleSize.width) * bottleScale, h = bottleH
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format)
        let image = renderer.image { ctx in
            bottle.draw(in: CGRect(x: 0, y: 0, width: w, height: h))
            let cx = w / 2
            func line(_ text: String, _ font: UIFont, _ cy: Double, _ alpha: Double = 1, kern: Double = 0) {
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: font, .foregroundColor: uiColor(ink2, alpha), .kern: kern,
                ]
                let s = NSAttributedString(string: text, attributes: attrs)
                let size = s.size()
                // Centre the ink, not the trailing kern.
                s.draw(at: CGPoint(x: cx - (size.width - kern) / 2, y: cy - size.height / 2))
            }
            line("ISLA BLOOM", .systemFont(ofSize: 5.5, weight: .semibold), 88, 0.9, kern: 1.3)
            let g = ctx.cgContext
            g.setStrokeColor(uiColor(ink2, 0.5).cgColor)
            g.setLineWidth(0.5)
            g.move(to: CGPoint(x: cx - 12, y: 94.5))
            g.addLine(to: CGPoint(x: cx + 12, y: 94.5))
            g.strokePath()
            line("PINEAPPLE", UIFont(name: "Georgia-Bold", size: 10.5) ?? .boldSystemFont(ofSize: 10.5), 108, 1, kern: 0.4)
            line("body oil", UIFont(name: "Georgia-Italic", size: 9) ?? .italicSystemFont(ofSize: 9), 121)
            g.setFillColor(uiColor(petal, 0.9).cgColor)
            for i in 0..<5 {
                let a = Double(i) / 5 * 2 * Double.pi - Double.pi / 2
                g.fillEllipse(in: CGRect(x: cx + cos(a) * 2.6 - 2.2, y: 136 + sin(a) * 2.6 - 2.2, width: 4.4, height: 4.4))
            }
            g.setFillColor(uiColor(0xF2C230).cgColor)
            g.fillEllipse(in: CGRect(x: cx - 1, y: 135, width: 2, height: 2))
            line("100 ml  ·  3.4 fl oz", .systemFont(ofSize: 5, weight: .medium), 152, 0.75, kern: 0.3)
        }
        return image.preparingForDisplay() ?? image
    }()

    /// A static, seeded 128 px grain tile.
    static let grain: UIImage = {
        let n = 128
        var bytes = [UInt8](repeating: 0, count: n * n * 4)
        for i in 0..<(n * n) {
            let v = UInt8(M.hash(Double(i) * 0.731 + 0.5) * 255)
            bytes[i * 4] = v; bytes[i * 4 + 1] = v; bytes[i * 4 + 2] = v; bytes[i * 4 + 3] = 255
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)!
        let cg = CGImage(width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: n * 4,
                         space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                         provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
        return UIImage(cgImage: cg, scale: 2, orientation: .up)
    }()

    // MARK: Layout

    struct Layout {
        var height: Double
        var top: Double
        var bottom: Double

        var ctaTop: Double { height - bottom - 12 - 56 }
        var heroBase: Double { ctaTop - 132 }
        var barY: Double { top + 26 }
        var captionY: Double { ctaTop - 106 }
        var titleY: Double { ctaTop - 76 }
        var descY: Double { ctaTop - 38 }
        /// Centre of the bottom half's cut face.
        var face: CGPoint { CGPoint(x: 196.5, y: heroBase - Double(bottomSize.height) * pine + Double(bottomFace.y) * pine) }
        var rx: Double { faceRx * pine }
        var ry: Double { faceRy * pine }
    }

    struct Flower {
        var x: Double, y: Double, fromTop: Bool, size: Double, rot: Double
        var dx: Double, dy: Double, drot: Double, phase: Double
    }

    static let flowers: [Flower] = [
        Flower(x: 64, y: 150, fromTop: true, size: 128, rot: -18, dx: -16, dy: -26, drot: 14, phase: 0),
        Flower(x: 8, y: -190, fromTop: false, size: 104, rot: 28, dx: -18, dy: -14, drot: -12, phase: 2.1),
        Flower(x: 388, y: -150, fromTop: false, size: 112, rot: -36, dx: 20, dy: -24, drot: 16, phase: 4.2),
    ]

    /// Where the top half sits for an open value: its cut-face centre and rotation.
    static func topPose(_ l: Layout, open o: Double, squash: Double) -> (x: Double, y: Double, angle: Double) {
        let f = l.face
        return (f.x + 76 * o, f.y + 34 + (-226) * o + squash, -topTilt * (1 - o))
    }

    /// The bottle's centre, rotation and scale for a rise value, with its hover bob.
    static func bottlePose(_ l: Layout, rise r: Double, ambient: Double) -> (x: Double, y: Double, angle: Double, scale: Double) {
        let f = l.face
        let hover = M.clamp(r)
        let wave = 2 * Double.pi * 3 * ambient / M.loop
        let y = f.y + 135 + (-243) * r + 4 * sin(wave) * hover
        let angle = (-9 * r + 1.5 * sin(wave + 1.3) * hover) * Double.pi / 180
        return (196.5 - 10.5 * r, y, angle, 0.92 + 0.08 * hover)
    }

    // MARK: Drawing

    /// A radial falloff squashed into an ellipse: full `alpha` at the centre, 0 at the rim.
    static func softEllipse(_ ctx: GraphicsContext, x: Double, y: Double, rx: Double, ry: Double, color hex: UInt32, alpha: Double) {
        var c = ctx
        c.translateBy(x: x, y: y)
        c.scaleBy(x: 1, y: ry / rx)
        c.fill(Path(ellipseIn: CGRect(x: -rx, y: -rx, width: 2 * rx, height: 2 * rx)), with: .radialGradient(
            Gradient(colors: [color(hex, alpha), color(hex, alpha * 0.45), color(hex, 0)]),
            center: .zero, startRadius: 0, endRadius: rx))
    }

    /// Everything above the bottom half's front rim: what comes out of the fruit is drawn there.
    static func rimClip(_ l: Layout, sink: Double) -> Path {
        let f = l.face
        var p = Path()
        p.move(to: CGPoint(x: -60, y: -2000))
        p.addLine(to: CGPoint(x: 453, y: -2000))
        p.addLine(to: CGPoint(x: 453, y: f.y))
        let n = 36
        for i in 0...n {
            let a = Double(i) / Double(n) * Double.pi
            p.addLine(to: CGPoint(x: f.x + cos(a) * l.rx * 0.98, y: f.y + sin(a) * (l.ry + sink)))
        }
        p.addLine(to: CGPoint(x: -60, y: f.y))
        p.closeSubpath()
        return p
    }

    static func drawBackground(_ ctx: GraphicsContext, _ l: Layout) {
        let full = CGRect(x: 0, y: 0, width: 393, height: l.height)
        ctx.fill(Path(full), with: .linearGradient(
            Gradient(stops: [.init(color: color(skyTop), location: 0), .init(color: color(skyMid), location: 0.6),
                             .init(color: color(skyLow), location: 1)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: l.height)))
        let f = l.face
        ctx.fill(Path(full), with: .radialGradient(
            Gradient(colors: [color(haze, 0.55), color(haze, 0)]),
            center: CGPoint(x: f.x, y: f.y - 70), startRadius: 0, endRadius: 280))
        var g = ctx
        g.opacity = 0.045
        g.blendMode = .overlay
        g.fill(Path(full), with: .tiledImage(Image(uiImage: grain), sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    static func drawFlowers(_ ctx: GraphicsContext, _ l: Layout, drift: Double, ambient: Double) {
        let image = ctx.resolve(Image(uiImage: flowerImage))
        for fl in flowers {
            let a = 2 * Double.pi * ambient / M.loop + fl.phase
            let x = fl.x + fl.dx * drift + 3 * sin(a)
            let y = (fl.fromTop ? l.top : l.heroBase) + fl.y + fl.dy * drift + 2.5 * cos(a)
            let rot = (fl.rot + fl.drot * drift + 3 * sin(a + 0.7)) * Double.pi / 180
            var c = ctx
            c.translateBy(x: x, y: y)
            c.rotate(by: .radians(rot))
            let h = fl.size * Double(flowerSize.height / flowerSize.width)
            c.draw(image, in: CGRect(x: -fl.size / 2, y: -h / 2, width: fl.size, height: h))
        }
    }

    static func drawHero(_ ctx: GraphicsContext, _ l: Layout, frame fr: M.Frame, ambient: Double) {
        let f = l.face
        let p = pine
        // Contact shadow on the backdrop.
        softEllipse(ctx, x: 196.5, y: l.heroBase - 2, rx: 96, ry: 13, color: shade, alpha: 0.38)

        let bottle = bottlePose(l, rise: fr.rise, ambient: ambient)
        // Glow behind the bottle.
        if fr.glow > 0.001 {
            ctx.fill(Path(ellipseIn: CGRect(x: bottle.x - 170, y: bottle.y - 170, width: 340, height: 340)), with: .radialGradient(
                Gradient(colors: [color(glowTint, 0.6 * fr.glow), color(glowTint, 0)]),
                center: CGPoint(x: bottle.x, y: bottle.y), startRadius: 0, endRadius: 170))
        }

        // Bottom half.
        let bx = f.x - Double(bottomFace.x) * p, by = l.heroBase - Double(bottomSize.height) * p
        ctx.draw(ctx.resolve(Image(uiImage: bottomImage)),
                 in: CGRect(x: bx, y: by, width: Double(bottomSize.width) * p, height: Double(bottomSize.height) * p))

        // The bottle's shadow on the cut face.
        let hover = M.clamp(fr.rise)
        if hover > 0.001 {
            var c = ctx
            c.clip(to: Path(ellipseIn: CGRect(x: f.x - l.rx, y: f.y - l.ry, width: 2 * l.rx, height: 2 * l.ry)))
            softEllipse(c, x: f.x - 10, y: f.y + 1.5, rx: 38 - 7 * hover, ry: 8, color: shade, alpha: 0.34 * hover)
        }

        // Bottle, pulled out of the fruit.
        if fr.rise > -0.2 {
            var c = ctx
            c.clip(to: rimClip(l, sink: -2))
            c.translateBy(x: bottle.x, y: bottle.y)
            c.rotate(by: .radians(bottle.angle))
            c.scaleBy(x: bottle.scale, y: bottle.scale)
            let w = Double(bottleSize.width) * bottleScale
            let rect = CGRect(x: -w / 2, y: -bottleH / 2, width: w, height: bottleH)
            if fr.sheen >= 0 {
                c.drawLayer { layer in
                    layer.draw(layer.resolve(Image(uiImage: bottleImage)), in: rect)
                    layer.blendMode = .sourceAtop
                    drawSheen(layer, rect: rect, progress: fr.sheen)
                }
            } else {
                c.draw(c.resolve(Image(uiImage: bottleImage)), in: rect)
            }
        }

        // Top half.
        let top = topPose(l, open: fr.open, squash: fr.squash)
        var c = ctx
        c.clip(to: rimClip(l, sink: 1))
        c.translateBy(x: top.x, y: top.y)
        c.rotate(by: .radians(top.angle))
        c.scaleBy(x: p, y: p)
        c.draw(c.resolve(Image(uiImage: topImage)),
               in: CGRect(x: -topPivot.x, y: -topPivot.y, width: topSize.width, height: topSize.height))
    }

    /// A soft diagonal band of light crossing the glass from left to right.
    static func drawSheen(_ ctx: GraphicsContext, rect: CGRect, progress: Double) {
        let alpha = 0.55 * sin(Double.pi * progress)
        let x = rect.minX - 50 + (rect.width + 100) * progress
        var band = Path()
        band.move(to: CGPoint(x: x - 14, y: rect.maxY))
        band.addLine(to: CGPoint(x: x + 10, y: rect.maxY))
        band.addLine(to: CGPoint(x: x + 50, y: rect.minY))
        band.addLine(to: CGPoint(x: x + 26, y: rect.minY))
        band.closeSubpath()
        ctx.fill(band, with: .linearGradient(
            Gradient(colors: [color(0xFFFFFF, 0), color(0xFFFFFF, alpha), color(0xFFFFFF, 0)]),
            startPoint: CGPoint(x: x - 14, y: rect.midY), endPoint: CGPoint(x: x + 50, y: rect.midY)))
    }

    static func drawDrops(_ ctx: GraphicsContext, _ l: Layout, frame fr: M.Frame) {
        let f = l.face
        for b in fr.bursts {
            // A bright ring flashes off the rim as the burst leaves it.
            if b.age < 0.35 {
                let e = M.easeOutCubic(b.age / 0.35)
                let s = 1 + 0.7 * e
                let ring = CGRect(x: f.x - l.rx * s, y: f.y - l.ry * s, width: 2 * l.rx * s, height: 2 * l.ry * s)
                ctx.stroke(Path(ellipseIn: ring), with: .color(color(0xFFF6C8, 0.6 * (1 - e) * b.strength)), lineWidth: 2.5)
            }
            for d in M.drops(age: b.age, strength: b.strength, rx: l.rx, ry: l.ry) where d.alpha > 0.01 {
                let speed = hypot(d.vx, d.vy)
                let stretch = 1 + min(speed / 900, 1.2)
                var c = ctx
                c.translateBy(x: f.x + d.x, y: f.y + d.y)
                c.rotate(by: .radians(atan2(d.vy, d.vx)))
                let len = d.r * stretch, wid = d.r / sqrt(stretch)
                let shape = Path(ellipseIn: CGRect(x: -len, y: -wid, width: 2 * len, height: 2 * wid))
                c.fill(shape, with: .color(color(juice, 0.88 * d.alpha)))
                c.stroke(shape, with: .color(color(juiceRim, 0.7 * d.alpha)), lineWidth: 1)
                var h = ctx
                h.translateBy(x: f.x + d.x - 0.28 * d.r, y: f.y + d.y - 0.3 * d.r)
                let hr = 0.38 * d.r
                h.fill(Path(ellipseIn: CGRect(x: -hr, y: -hr, width: 2 * hr, height: 2 * hr)), with: .color(color(0xFFFDF0, 0.9 * d.alpha)))
            }
        }
    }

    // MARK: UI

    static func drawBag(_ ctx: GraphicsContext, center c: CGPoint) {
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 20, y: c.y - 20, width: 40, height: 40)), with: .color(color(ink2, 0.1)))
        var body = Path()
        body.addRoundedRect(in: CGRect(x: c.x - 7.5, y: c.y - 3.5, width: 15, height: 12.5), cornerSize: CGSize(width: 2.5, height: 2.5))
        var handle = Path()
        handle.move(to: CGPoint(x: c.x - 4, y: c.y - 3.5))
        handle.addCurve(to: CGPoint(x: c.x + 4, y: c.y - 3.5), control1: CGPoint(x: c.x - 4, y: c.y - 10),
                        control2: CGPoint(x: c.x + 4, y: c.y - 10))
        let style = StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round)
        ctx.stroke(body, with: .color(color(ink2)), style: style)
        ctx.stroke(handle, with: .color(color(ink2)), style: style)
        ctx.fill(Path(ellipseIn: CGRect(x: c.x + 6, y: c.y - 16, width: 16, height: 16)), with: .color(color(ink)))
    }
}
