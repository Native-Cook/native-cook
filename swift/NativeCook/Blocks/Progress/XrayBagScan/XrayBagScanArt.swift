import SwiftUI
import UIKit

/// Everything of X-Ray Bag Scan drawn in code (terminal wall, belt, scanner gantry, x-ray window,
/// detection boxes, small icons), plus the two bundled suitcase images. Coordinates are in the
/// 393-pt-wide design space, scene y measured for a 59-pt safe-area top.
enum XrayBagScanArt {
    typealias M = XrayBagScanMotion

    // MARK: Colour

    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }

    static func mix(_ a: Int, _ b: Int, _ f: Double, _ alpha: Double = 1) -> Color {
        func ch(_ h: Int, _ s: Int) -> Double { Double((h >> s) & 255) / 255 }
        let g = M.clamp(f)
        return Color(.sRGB, red: ch(a, 16) + (ch(b, 16) - ch(a, 16)) * g,
                     green: ch(a, 8) + (ch(b, 8) - ch(a, 8)) * g,
                     blue: ch(a, 0) + (ch(b, 0) - ch(a, 0)) * g, opacity: alpha)
    }

    static let ink = 0x2B1A0E
    static let brown = 0x5A3418
    static let muted = 0x8A6A50
    static let card = 0xFFF7EC
    static let track = 0xF0E2CF
    static let accent = 0xF08A2C
    static let green = 0x3DBE6B
    static let cyan = 0x6FE6FF
    static let boxGreen = 0x7CF2A0
    static let navy = 0x0A1A38

    // MARK: Images

    /// The photoreal bag and its x-ray: the same crop, so one rect draws either.
    static let photo = load("XrayBagScan-suitcase")
    static let xray = load("XrayBagScan-suitcase-xray")
    static let imageAspect = 329.0 / 600.0
    static var imageW: Double { M.bagH * imageAspect }

    private static func load(_ name: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: name, ofType: "webp"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }

    /// A detected item: its box as fractions of the bag image, and where its label tab sits.
    struct Item {
        let label: String
        let x0: Double, y0: Double, x1: Double, y1: Double
        let below: Bool
        let right: Bool
    }

    static let items: [Item] = [
        Item(label: "Liquids 100 ml", x0: 0.12, y0: 0.29, x1: 0.50, y1: 0.47, below: false, right: false),
        Item(label: "Headphones", x0: 0.53, y0: 0.31, x1: 0.91, y1: 0.50, below: true, right: true),
        Item(label: "Laptop", x0: 0.10, y0: 0.50, x1: 0.89, y1: 0.70, below: true, right: false),
    ]

    /// Item centres relative to the bag's centre (pt), for the scrub reveal.
    static var itemOffsets: [Double] { items.map { (($0.x0 + $0.x1) / 2 - 0.5) * imageW } }

    // MARK: Wall and floor

    static func drawBackground(_ ctx: GraphicsContext, width: Double, height: Double, sy: Double) {
        let wallBottom = 204 + sy
        ctx.fill(Path(CGRect(x: 0, y: 0, width: width, height: wallBottom)), with: .linearGradient(
            Gradient(colors: [color(0xF6A54E), color(0xEF9238), color(0xE6812B)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: wallBottom)))
        for x in [131.0, 262.0] {
            ctx.fill(Path(CGRect(x: x, y: 0, width: 1, height: wallBottom)), with: .color(color(0x000000, 0.07)))
            ctx.fill(Path(CGRect(x: x + 1, y: 0, width: 1, height: wallBottom)), with: .color(color(0xFFFFFF, 0.10)))
        }
        ctx.fill(Path(CGRect(x: 0, y: 0, width: width, height: wallBottom)), with: .radialGradient(
            Gradient(colors: [color(0xFFFFFF, 0.20), color(0xFFFFFF, 0)]),
            center: CGPoint(x: 70, y: 100 + sy), startRadius: 0, endRadius: 300))

        // Olive tiled wall behind the belt.
        let tileTop = wallBottom, tileBottom = M.beltTop + sy
        ctx.fill(Path(CGRect(x: 0, y: tileTop, width: width, height: tileBottom - tileTop)), with: .linearGradient(
            Gradient(colors: [color(0x5E7A56), color(0x4F6A4A), color(0x425A3F)]),
            startPoint: CGPoint(x: 0, y: tileTop), endPoint: CGPoint(x: 0, y: tileBottom)))
        var dark = Path(), light = Path()
        var y = tileTop + 22
        while y < tileBottom {
            dark.addRect(CGRect(x: 0, y: y, width: width, height: 1))
            light.addRect(CGRect(x: 0, y: y + 1, width: width, height: 1))
            y += 22
        }
        var x = 9.5
        while x < width {
            dark.addRect(CGRect(x: x, y: tileTop, width: 1, height: tileBottom - tileTop))
            light.addRect(CGRect(x: x + 1, y: tileTop, width: 1, height: tileBottom - tileTop))
            x += 22
        }
        ctx.fill(dark, with: .color(color(0x1E2B1C, 0.22)))
        ctx.fill(light, with: .color(color(0xFFFFFF, 0.05)))
        ctx.fill(Path(CGRect(x: 0, y: tileTop, width: width, height: tileBottom - tileTop)), with: .radialGradient(
            Gradient(colors: [color(0xFFF3C8, 0.16), color(0xFFF3C8, 0)]),
            center: CGPoint(x: 196.5, y: 420 + sy), startRadius: 0, endRadius: 230))
        // Orange ledge between the two walls.
        ctx.fill(Path(CGRect(x: 0, y: tileTop - 2, width: width, height: 10)), with: .linearGradient(
            Gradient(colors: [color(0xF7B15F), color(0xD8732A)]),
            startPoint: CGPoint(x: 0, y: tileTop - 2), endPoint: CGPoint(x: 0, y: tileTop + 8)))
        ctx.fill(Path(CGRect(x: 0, y: tileTop + 8, width: width, height: 6)), with: .linearGradient(
            Gradient(colors: [color(0x000000, 0.22), color(0x000000, 0)]),
            startPoint: CGPoint(x: 0, y: tileTop + 8), endPoint: CGPoint(x: 0, y: tileTop + 14)))

        // Floor.
        let floorTop = M.skirtBottom + sy
        ctx.fill(Path(CGRect(x: 0, y: floorTop, width: width, height: height - floorTop)), with: .linearGradient(
            Gradient(colors: [color(0x9C8D78), color(0x857762)]),
            startPoint: CGPoint(x: 0, y: floorTop), endPoint: CGPoint(x: 0, y: height)))

        drawSign(ctx, sy: sy)
        drawPoster(ctx, sy: sy)
    }

    /// Yellow wayfinding sign on the left of the gantry.
    static func drawSign(_ ctx: GraphicsContext, sy: Double) {
        let r = CGRect(x: 16, y: 236 + sy, width: 92, height: 26)
        ctx.fill(Path(roundedRect: r.offsetBy(dx: 0, dy: 3), cornerRadius: 5), with: .color(color(0x1E2B1C, 0.25)))
        ctx.fill(Path(roundedRect: r, cornerRadius: 5), with: .linearGradient(
            Gradient(colors: [color(0xFADE52), color(0xEFC92F)]), startPoint: CGPoint(x: 0, y: r.minY), endPoint: CGPoint(x: 0, y: r.maxY)))
        ctx.draw(Text("Bag drop").font(.system(size: 12, weight: .bold)).foregroundColor(color(ink)),
                 at: CGPoint(x: r.minX + 9, y: r.midY), anchor: .leading)
        var arrow = Path()
        arrow.move(to: CGPoint(x: r.maxX - 22, y: r.midY)); arrow.addLine(to: CGPoint(x: r.maxX - 10, y: r.midY))
        arrow.move(to: CGPoint(x: r.maxX - 15, y: r.midY - 4.5)); arrow.addLine(to: CGPoint(x: r.maxX - 10, y: r.midY))
        arrow.addLine(to: CGPoint(x: r.maxX - 15, y: r.midY + 4.5))
        ctx.stroke(arrow, with: .color(color(ink)), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    }

    /// A tilted orange poster on the right wall.
    static func drawPoster(_ ctx: GraphicsContext, sy: Double) {
        var c = ctx
        c.translateBy(x: 338, y: 300 + sy)
        c.rotate(by: .degrees(5))
        let r = CGRect(x: -32, y: -42, width: 64, height: 84)
        c.fill(Path(roundedRect: r.offsetBy(dx: 2, dy: 4), cornerRadius: 4), with: .color(color(0x1E2B1C, 0.28)))
        c.fill(Path(roundedRect: r, cornerRadius: 4), with: .linearGradient(
            Gradient(colors: [color(0xF47A43), color(0xE45A2E)]), startPoint: CGPoint(x: 0, y: r.minY), endPoint: CGPoint(x: 0, y: r.maxY)))
        c.draw(Text("Pack\nlight,\nfly\nbright").font(.system(size: 13, weight: .heavy)).foregroundColor(color(0xFFF1DE)),
               at: CGPoint(x: r.minX + 8, y: r.minY + 7), anchor: .topLeading)
        c.fill(Path(ellipseIn: CGRect(x: -3, y: r.minY - 2, width: 6, height: 6)), with: .color(color(0xE8E2D6)))
    }

    // MARK: Belt

    /// The rubber top of the belt with its moving slats; the skirt below it is static.
    static func drawBelt(_ ctx: GraphicsContext, width: Double, d: Double, sy: Double) {
        let top = M.beltTop + sy, bottom = M.skirtTop + sy
        ctx.fill(Path(CGRect(x: 0, y: top, width: width, height: bottom - top)), with: .linearGradient(
            Gradient(colors: [color(0x4A484F), color(0x3A3940), color(0x2C2B31)]),
            startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: bottom)))
        var seams = Path(), shine = Path()
        var x = M.mod(d, M.pitch) - M.pitch
        while x < width + M.pitch {
            seams.addRect(CGRect(x: x, y: top + 1, width: 1.6, height: bottom - top - 1))
            shine.addRect(CGRect(x: x + 1.6, y: top + 1, width: 1, height: bottom - top - 1))
            x += M.pitch
        }
        ctx.fill(seams, with: .color(color(0x1B1A1F)))
        ctx.fill(shine, with: .color(color(0x6A6870, 0.7)))
        ctx.fill(Path(CGRect(x: 0, y: top, width: width, height: 1.2)), with: .color(color(0xFFFFFF, 0.22)))

        let st = M.skirtTop + sy, sb = M.skirtBottom + sy
        ctx.fill(Path(CGRect(x: 0, y: st, width: width, height: sb - st)), with: .linearGradient(
            Gradient(colors: [color(0xD6D9DE), color(0xBCC0C7), color(0x979CA5)]),
            startPoint: CGPoint(x: 0, y: st), endPoint: CGPoint(x: 0, y: sb)))
        ctx.fill(Path(CGRect(x: 0, y: st, width: width, height: 1.2)), with: .color(color(0xFFFFFF, 0.6)))
        ctx.fill(Path(CGRect(x: 0, y: st + 9, width: width, height: 1)), with: .color(color(0x7C818A, 0.5)))
        var bolts = Path()
        var bx = 24.5
        while bx < width { bolts.addEllipse(in: CGRect(x: bx - 2, y: st + 16, width: 4, height: 4)); bx += 49 }
        ctx.fill(bolts, with: .color(color(0x80858E)))
        ctx.fill(Path(CGRect(x: 0, y: sb, width: width, height: 12)), with: .linearGradient(
            Gradient(colors: [color(0x2A2218, 0.30), color(0x2A2218, 0)]),
            startPoint: CGPoint(x: 0, y: sb), endPoint: CGPoint(x: 0, y: sb + 12)))
    }

    /// The same belt as seen by the scanner: cyan outlines.
    static func drawBeltXray(_ ctx: GraphicsContext, d: Double, sy: Double) {
        let top = M.beltTop + sy, bottom = M.skirtTop + sy
        ctx.fill(Path(CGRect(x: M.winL, y: top, width: M.winR - M.winL, height: bottom - top)), with: .color(color(cyan, 0.08)))
        ctx.fill(Path(CGRect(x: M.winL, y: top, width: M.winR - M.winL, height: 1.2)), with: .color(color(cyan, 0.65)))
        ctx.fill(Path(CGRect(x: M.winL, y: bottom - 1, width: M.winR - M.winL, height: 1)), with: .color(color(cyan, 0.4)))
        var seams = Path()
        var x = M.mod(d, M.pitch) - M.pitch
        while x < M.winR + M.pitch {
            seams.addRect(CGRect(x: x, y: top + 2, width: 1, height: bottom - top - 3))
            x += M.pitch
        }
        ctx.fill(seams, with: .color(color(cyan, 0.32)))
    }

    // MARK: Bags

    /// Soft contact shadow of a bag on the belt.
    static func drawBagShadow(_ ctx: GraphicsContext, x: Double, sy: Double) {
        var c = ctx
        let w = imageW * 1.08
        c.translateBy(x: x, y: M.beltY + sy + 1)
        c.scaleBy(x: 1, y: 0.12)
        c.fill(Path(ellipseIn: CGRect(x: -w / 2, y: -w / 2, width: w, height: w)), with: .radialGradient(
            Gradient(stops: [.init(color: color(0x0E0D10, 0.55), location: 0), .init(color: color(0x0E0D10, 0.3), location: 0.6),
                             .init(color: color(0x0E0D10, 0), location: 1)]),
            center: .zero, startRadius: 0, endRadius: w / 2))
    }

    /// A bag image standing on the belt at `x`, leaning `lean` degrees about its wheels.
    static func drawBag(_ ctx: GraphicsContext, _ image: GraphicsContext.ResolvedImage, x: Double, lean: Double, sy: Double) {
        var c = ctx
        c.translateBy(x: x, y: M.beltY + sy)
        c.rotate(by: .degrees(lean))
        c.draw(image, in: CGRect(x: -imageW / 2, y: -M.bagH, width: imageW, height: M.bagH))
    }

    /// Detection boxes in the bag's own frame (they ride and lean with it).
    static func drawBoxes(_ ctx: GraphicsContext, x: Double, lean: Double, sy: Double,
                          boxes: [(scale: Double, alpha: Double)], green: Double) {
        var c = ctx
        c.translateBy(x: x, y: M.beltY + sy)
        c.rotate(by: .degrees(lean))
        c.translateBy(x: -imageW / 2, y: -M.bagH)
        let line = mix(cyan, boxGreen, green)
        for (i, item) in items.enumerated() where i < boxes.count && boxes[i].alpha > 0.002 {
            let (s, a) = boxes[i]
            let r = CGRect(x: item.x0 * imageW, y: item.y0 * M.bagH, width: (item.x1 - item.x0) * imageW, height: (item.y1 - item.y0) * M.bagH)
            var b = c
            b.opacity = a
            b.translateBy(x: r.midX, y: r.midY)
            b.scaleBy(x: s, y: s)
            let local = CGRect(x: -r.width / 2, y: -r.height / 2, width: r.width, height: r.height)
            b.fill(Path(roundedRect: local, cornerRadius: 3), with: .color(mix(cyan, boxGreen, green, 0.10)))
            b.stroke(Path(roundedRect: local, cornerRadius: 3), with: .color(mix(cyan, boxGreen, green, 0.35)), lineWidth: 0.8)
            b.stroke(brackets(local, 6), with: .color(line), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))

            let text = b.resolve(Text(item.label).font(.system(size: 9.5, weight: .semibold)).foregroundColor(color(0x061226)))
            let size = text.measure(in: CGSize(width: 200, height: 40))
            let tw = size.width + 10, th = 14.0
            let tx = item.right ? local.maxX - tw : local.minX
            let ty = item.below ? local.maxY + 1 : local.minY - th - 1
            let tab = CGRect(x: tx, y: ty, width: tw, height: th)
            b.fill(Path(roundedRect: tab, cornerRadius: 4), with: .color(line))
            b.draw(text, at: CGPoint(x: tab.midX, y: tab.midY), anchor: .center)
        }
    }

    static func brackets(_ r: CGRect, _ l: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.minY + l)); p.addLine(to: CGPoint(x: r.minX, y: r.minY)); p.addLine(to: CGPoint(x: r.minX + l, y: r.minY))
        p.move(to: CGPoint(x: r.maxX - l, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.minY + l))
        p.move(to: CGPoint(x: r.maxX, y: r.maxY - l)); p.addLine(to: CGPoint(x: r.maxX, y: r.maxY)); p.addLine(to: CGPoint(x: r.maxX - l, y: r.maxY))
        p.move(to: CGPoint(x: r.minX + l, y: r.maxY)); p.addLine(to: CGPoint(x: r.minX, y: r.maxY)); p.addLine(to: CGPoint(x: r.minX, y: r.maxY - l))
        return p
    }

    // MARK: Scanner window

    static func windowRect(sy: Double) -> CGRect {
        CGRect(x: M.winL, y: M.winT + sy, width: M.winR - M.winL, height: M.winB - M.winT)
    }

    /// The navy screen behind the x-ray: gradient, grid and scan lines.
    static func drawWindowBack(_ ctx: GraphicsContext, sy: Double) {
        let r = windowRect(sy: sy)
        ctx.fill(Path(r), with: .linearGradient(Gradient(colors: [color(0x0E2148), color(0x0A1A38), color(0x061229)]),
                                               startPoint: CGPoint(x: 0, y: r.minY), endPoint: CGPoint(x: 0, y: r.maxY)))
        var minor = Path(), major = Path()
        var i = 0
        var x = r.minX + 6
        while x < r.maxX {
            let line = CGRect(x: x, y: r.minY, width: 0.7, height: r.height)
            if i % 4 == 0 { major.addRect(line) } else { minor.addRect(line) }
            x += 14; i += 1
        }
        i = 0
        var y = r.minY + 6
        while y < r.maxY {
            let line = CGRect(x: r.minX, y: y, width: r.width, height: 0.7)
            if i % 4 == 0 { major.addRect(line) } else { minor.addRect(line) }
            y += 14; i += 1
        }
        ctx.fill(minor, with: .color(color(cyan, 0.07)))
        ctx.fill(major, with: .color(color(cyan, 0.15)))
        // Corner ticks of the readout.
        let l = 9.0
        ctx.stroke(brackets(r.insetBy(dx: 6, dy: 6), l), with: .color(color(cyan, 0.45)), lineWidth: 1.2)
    }

    /// Scan lines, the sweep and the glass on top of the x-ray.
    static func drawWindowFront(_ ctx: GraphicsContext, sy: Double, sweepY: Double, sweepAlpha: Double) {
        let r = windowRect(sy: sy)
        var lines = Path()
        var y = r.minY + 1
        while y < r.maxY { lines.addRect(CGRect(x: r.minX, y: y, width: r.width, height: 1)); y += 3 }
        ctx.fill(lines, with: .color(color(0x000814, 0.16)))
        if sweepAlpha > 0.001 {
            let sy2 = sweepY + sy
            ctx.fill(Path(CGRect(x: r.minX, y: sy2 - 26, width: r.width, height: 26)), with: .linearGradient(
                Gradient(colors: [color(cyan, 0), color(cyan, 0.22 * sweepAlpha)]),
                startPoint: CGPoint(x: 0, y: sy2 - 26), endPoint: CGPoint(x: 0, y: sy2)))
            ctx.fill(Path(CGRect(x: r.minX, y: sy2 - 0.8, width: r.width, height: 1.6)), with: .color(color(0xD8FBFF, 0.95 * sweepAlpha)))
        }
        // Vignette and glass sheen.
        ctx.fill(Path(r), with: .radialGradient(Gradient(colors: [color(0x000000, 0), color(0x000814, 0.45)]),
                                                center: CGPoint(x: r.midX, y: r.midY), startRadius: r.width * 0.45, endRadius: r.height * 0.75))
        var sheen = Path()
        sheen.move(to: CGPoint(x: r.minX + 30, y: r.minY)); sheen.addLine(to: CGPoint(x: r.minX + 70, y: r.minY))
        sheen.addLine(to: CGPoint(x: r.minX, y: r.minY + 120)); sheen.addLine(to: CGPoint(x: r.minX, y: r.minY + 60))
        sheen.closeSubpath()
        ctx.fill(sheen, with: .color(color(0xFFFFFF, 0.05)))
    }

    /// The light curtains on the window's two edges: the glowing mask edge.
    static func drawCurtains(_ ctx: GraphicsContext, sy: Double, left: Double, right: Double) {
        let r = windowRect(sy: sy)
        for (x, g, dir) in [(r.minX, left, 1.0), (r.maxX, right, -1.0)] {
            let band = CGRect(x: dir > 0 ? x : x - 22, y: r.minY, width: 22, height: r.height)
            ctx.fill(Path(band), with: .linearGradient(
                Gradient(colors: [color(cyan, 0.22 * (g - 0.35) / 0.65), color(cyan, 0)]),
                startPoint: CGPoint(x: x, y: 0), endPoint: CGPoint(x: x + 22 * dir, y: 0)))
            var glow = ctx
            glow.addFilter(.blur(radius: 5))
            glow.fill(Path(CGRect(x: x - 4, y: r.minY, width: 8, height: r.height)), with: .color(color(cyan, 0.55 * g)))
            ctx.fill(Path(CGRect(x: x - 1, y: r.minY, width: 2, height: r.height)), with: .linearGradient(
                Gradient(colors: [color(0xCFF8FF, 0.25 + 0.4 * g), color(0xCFF8FF, 0.5 + 0.5 * g), color(0xCFF8FF, 0.25 + 0.4 * g)]),
                startPoint: CGPoint(x: 0, y: r.minY), endPoint: CGPoint(x: 0, y: r.maxY)))
        }
    }

    // MARK: Gantry

    static func drawGantry(_ ctx: GraphicsContext, sy: Double, beacon: Double, scanning: Double, cleared: Double, t: Double) {
        let r = windowRect(sy: sy)
        let beamTop = 292 + sy
        // Soft shadow on the wall.
        var shadow = ctx
        shadow.addFilter(.blur(radius: 8))
        shadow.fill(Path(roundedRect: CGRect(x: 108, y: beamTop + 6, width: 186, height: 40), cornerRadius: 10), with: .color(color(0x1E2B1C, 0.30)))
        shadow.fill(Path(CGRect(x: 116, y: beamTop + 40, width: 14, height: 250)), with: .color(color(0x1E2B1C, 0.22)))
        shadow.fill(Path(CGRect(x: 270, y: beamTop + 40, width: 14, height: 250)), with: .color(color(0x1E2B1C, 0.22)))

        // Pillars down to the floor.
        let metal = Gradient(colors: [color(0xA4A9B1), color(0xDCDFE3), color(0xC2C6CC), color(0x989DA6)])
        for px in [112.5, 266.5] {
            let p = CGRect(x: px, y: beamTop + 20, width: 14, height: 586 - 292 - 20)
            ctx.fill(Path(roundedRect: p, cornerRadius: 3), with: .linearGradient(metal, startPoint: CGPoint(x: p.minX, y: 0), endPoint: CGPoint(x: p.maxX, y: 0)))
            ctx.fill(Path(CGRect(x: p.minX + 2, y: M.skirtTop + sy + 6, width: p.width - 4, height: 3)), with: .color(color(0xE9883A)))
        }
        // Window frame.
        ctx.stroke(Path(roundedRect: r.insetBy(dx: -1.5, dy: -1.5), cornerRadius: 3), with: .color(color(0x7F858E)), lineWidth: 3)
        ctx.stroke(Path(roundedRect: r.insetBy(dx: -3.2, dy: -3.2), cornerRadius: 4), with: .color(color(0xFFFFFF, 0.25)), lineWidth: 0.8)

        // Beam.
        let beam = CGRect(x: 104.5, y: beamTop, width: 184, height: 40)
        ctx.fill(Path(roundedRect: beam, cornerRadius: 10), with: .linearGradient(
            Gradient(colors: [color(0xDDE0E4), color(0xC5C9CF), color(0xA6ABB3)]),
            startPoint: CGPoint(x: 0, y: beam.minY), endPoint: CGPoint(x: 0, y: beam.maxY)))
        ctx.fill(Path(roundedRect: CGRect(x: beam.minX, y: beam.maxY - 5, width: beam.width, height: 5), cornerRadius: 2.5), with: .color(color(0xE9883A)))
        ctx.stroke(Path(roundedRect: beam.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 10), with: .color(color(0xFFFFFF, 0.45)), lineWidth: 1)
        let panel = CGRect(x: 150, y: beamTop + 8, width: 93, height: 20)
        ctx.fill(Path(roundedRect: panel, cornerRadius: 6), with: .color(color(0x1D2230)))
        for i in 0..<3 {
            let cx = 176.5 + 20 * Double(i), cy = panel.midY
            let chase = 0.5 + 0.5 * sin(2 * Double.pi * (6 * t / M.loop - Double(i) / 3))
            let amber = scanning * (0.35 + 0.65 * chase)
            let on = max(amber, cleared)
            let hue = mix(0xFFB23E, 0x5BE38A, cleared)
            var g = ctx
            g.addFilter(.blur(radius: 3))
            g.fill(Path(ellipseIn: CGRect(x: cx - 6, y: cy - 6, width: 12, height: 12)), with: .color(hue.opacity(0.7 * on)))
            ctx.fill(Path(ellipseIn: CGRect(x: cx - 3.5, y: cy - 3.5, width: 7, height: 7)), with: .color(color(0x3A4152)))
            ctx.fill(Path(ellipseIn: CGRect(x: cx - 3.5, y: cy - 3.5, width: 7, height: 7)), with: .color(hue.opacity(on)))
        }
        ctx.fill(Path(roundedRect: CGRect(x: 118, y: beamTop + 14, width: 22, height: 8), cornerRadius: 2), with: .color(color(0xE9883A)))
        ctx.fill(Path(roundedRect: CGRect(x: 253, y: beamTop + 14, width: 22, height: 8), cornerRadius: 2), with: .color(color(0xE9883A)))

        drawBeacon(ctx, sy: sy, glow: beacon)
    }

    /// The caged amber lamp on top of the gantry.
    static func drawBeacon(_ ctx: GraphicsContext, sy: Double, glow: Double) {
        let cx = 196.5, base = 292 + sy
        if glow > 0.001 {
            ctx.fill(Path(ellipseIn: CGRect(x: cx - 46, y: base - 58, width: 92, height: 92)), with: .radialGradient(
                Gradient(colors: [color(0xFFC25A, 0.55 * glow), color(0xFFB23E, 0)]),
                center: CGPoint(x: cx, y: base - 12), startRadius: 0, endRadius: 46))
        }
        ctx.fill(Path(roundedRect: CGRect(x: cx - 12, y: base - 7, width: 24, height: 8), cornerRadius: 2), with: .color(color(0x585D66)))
        let dome = CGRect(x: cx - 8.5, y: base - 27, width: 17, height: 21)
        ctx.fill(Path(roundedRect: dome, cornerRadii: RectangleCornerRadii(topLeading: 8.5, bottomLeading: 1, bottomTrailing: 1, topTrailing: 8.5)),
                 with: .linearGradient(Gradient(colors: [mix(0xE59A4C, 0xFFF0B8, glow), mix(0xC8682A, 0xFFB23E, glow)]),
                                       startPoint: CGPoint(x: 0, y: dome.minY), endPoint: CGPoint(x: 0, y: dome.maxY)))
        var cage = Path()
        for dx in [-4.5, 0, 4.5] { cage.move(to: CGPoint(x: cx + dx, y: dome.minY + (dx == 0 ? 0 : 2))); cage.addLine(to: CGPoint(x: cx + dx, y: dome.maxY)) }
        cage.move(to: CGPoint(x: dome.minX, y: dome.minY + 9)); cage.addLine(to: CGPoint(x: dome.maxX, y: dome.minY + 9))
        ctx.stroke(cage, with: .color(color(0x4A4F58)), lineWidth: 1.3)
        ctx.stroke(Path(roundedRect: dome, cornerRadii: RectangleCornerRadii(topLeading: 8.5, bottomLeading: 1, bottomTrailing: 1, topTrailing: 8.5)),
                   with: .color(color(0x4A4F58)), lineWidth: 1.3)
    }

    // MARK: Small icons

    /// Status icon (46 pt): suitcase (ready), spinner (scanning), check badge (cleared).
    static func drawStatusIcon(_ ctx: GraphicsContext, stage: Double, spin: Double, check: (scale: Double, stroke: Double)) {
        let s = M.mod(stage, 4)
        let ready = M.clamp(max(1 - s, s - 3))
        let spinner = M.clamp(min(s, 3 - s))
        let c = CGPoint(x: 23, y: 23)
        ctx.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: 46, height: 46)), with: .color(color(accent, 1 - M.clamp(check.scale))))
        if ready > 0.001 {
            var g = ctx
            g.opacity = ready
            drawSuitcaseGlyph(g, c)
        }
        if spinner > 0.001 {
            var g = ctx
            g.opacity = spinner
            g.stroke(Path(ellipseIn: CGRect(x: 12, y: 12, width: 22, height: 22)), with: .color(color(0xFFF3E3, 0.3)), lineWidth: 3)
            var arc = Path()
            arc.addArc(center: c, radius: 11, startAngle: .radians(spin), endAngle: .radians(spin + 1.9), clockwise: false)
            g.stroke(arc, with: .color(color(0xFFF3E3)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        }
        if check.scale > 0.001 {
            var g = ctx
            g.translateBy(x: c.x, y: c.y)
            g.scaleBy(x: check.scale, y: check.scale)
            g.fill(Path(ellipseIn: CGRect(x: -23, y: -23, width: 46, height: 46)), with: .color(color(green)))
            var tick = Path()
            tick.move(to: CGPoint(x: -9, y: 0.5)); tick.addLine(to: CGPoint(x: -3, y: 6.5)); tick.addLine(to: CGPoint(x: 9.5, y: -6))
            g.stroke(tick.trimmedPath(from: 0, to: check.stroke), with: .color(.white), style: StrokeStyle(lineWidth: 3.4, lineCap: .round, lineJoin: .round))
        }
    }

    static func drawSuitcaseGlyph(_ ctx: GraphicsContext, _ c: CGPoint) {
        let body = CGRect(x: c.x - 9, y: c.y - 6, width: 18, height: 15)
        ctx.stroke(Path(roundedRect: body, cornerRadius: 3.5), with: .color(color(0xFFF3E3)), lineWidth: 2.2)
        var handle = Path()
        handle.move(to: CGPoint(x: c.x - 4, y: body.minY)); handle.addLine(to: CGPoint(x: c.x - 4, y: body.minY - 4))
        handle.addLine(to: CGPoint(x: c.x + 4, y: body.minY - 4)); handle.addLine(to: CGPoint(x: c.x + 4, y: body.minY))
        ctx.stroke(handle, with: .color(color(0xFFF3E3)), style: StrokeStyle(lineWidth: 2.2, lineJoin: .round))
        var ribs = Path()
        ribs.move(to: CGPoint(x: c.x - 3, y: body.minY + 4)); ribs.addLine(to: CGPoint(x: c.x - 3, y: body.maxY - 4))
        ribs.move(to: CGPoint(x: c.x + 3, y: body.minY + 4)); ribs.addLine(to: CGPoint(x: c.x + 3, y: body.maxY - 4))
        ctx.stroke(ribs, with: .color(color(0xFFF3E3)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
    }

    /// Three-segment bar with its fill (0 … 1).
    static func drawBar(_ ctx: GraphicsContext, width: Double, fill: Double, done: Double) {
        let gap = 4.0, seg = (width - 2 * gap) / 3
        for i in 0..<3 {
            let r = CGRect(x: Double(i) * (seg + gap), y: 0, width: seg, height: 8)
            ctx.fill(Path(roundedRect: r, cornerRadius: 4), with: .color(color(track)))
            let f = M.clamp((fill - Double(i) / 3) * 3)
            if f > 0.001 {
                var c = ctx
                c.clip(to: Path(roundedRect: r, cornerRadius: 4))
                c.fill(Path(CGRect(x: r.minX, y: 0, width: seg * f, height: 8)), with: .color(mix(accent, green, done)))
            }
        }
    }

    /// Circular arrow for "Scan again" (18 pt).
    static func drawAgainGlyph(_ ctx: GraphicsContext, tint: Color) {
        var arc = Path()
        arc.addArc(center: CGPoint(x: 9, y: 9), radius: 6.5, startAngle: .degrees(-60), endAngle: .degrees(230), clockwise: false)
        ctx.stroke(arc, with: .color(tint), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
        let tip = CGPoint(x: 9 + 6.5 * cos(-60 * Double.pi / 180), y: 9 + 6.5 * sin(-60 * Double.pi / 180))
        var head = Path()
        head.move(to: CGPoint(x: tip.x - 5, y: tip.y - 1.2)); head.addLine(to: tip); head.addLine(to: CGPoint(x: tip.x - 0.6, y: tip.y + 5))
        ctx.stroke(head, with: .color(tint), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
    }

    /// The airline's bird mark (16 pt).
    static func drawBird(_ ctx: GraphicsContext) {
        var p = Path()
        p.move(to: CGPoint(x: 1, y: 9))
        p.addQuadCurve(to: CGPoint(x: 8, y: 7), control: CGPoint(x: 4, y: 4))
        p.addQuadCurve(to: CGPoint(x: 15, y: 3), control: CGPoint(x: 11, y: 2))
        p.addQuadCurve(to: CGPoint(x: 9, y: 11), control: CGPoint(x: 13, y: 9))
        p.addQuadCurve(to: CGPoint(x: 1, y: 9), control: CGPoint(x: 5, y: 12))
        p.closeSubpath()
        ctx.fill(p, with: .color(color(accent)))
    }
}
