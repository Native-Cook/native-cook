// Ink Wash Page Turn — the three pages, each drawn once into an image.
//
// A page is warm paper, a date, a title, a short poem in the system font and a small ink-wash drawing
// under it. Nothing here runs per frame: the images are baked when the screen's size is known and the
// bleed only samples them. A blurred shape is the shadow of a copy drawn off the canvas.

import UIKit

enum InkWashPageTurnPages {
    struct Layout: Equatable {
        var width: Double
        var height: Double
        var top: Double
        var bottom: Double
    }

    private static let poems: [(date: String, title: String, body: String)] = [
        ("Saturday, 14 March", "Low Tide", "The harbour pulls its blanket back\nand shows us what it kept:\na rope, a shoe, a ring of keys,\nthe print where a heron stepped.\n\nWe walk out further than we should\non ground that was the sea.\nNothing out here is in a hurry.\nFor an hour, neither are we."),
        ("Sunday, 15 March", "Kettle", "Before the house is properly awake\nthe kettle starts on its complaint.\nSteam finds the glass, writes a line,\nand rubs it out again.\n\nI hold the cup with both my hands\nthe way you hold good news,\nnot drinking yet, just letting it\nbe warm, be mine, be morning."),
        ("Monday, 16 March", "Last Bus", "The last bus hums along the coast\nwith four of us inside,\neach window carrying a moon\nit did not ask to hold.\n\nNobody speaks. The driver knows\nthe road the way you know a song.\nWe are delivered, one by one,\nto porches with the lights left on."),
    ]

    private static func srgb(_ hex: UInt32) -> CGColor {
        CGColor(srgbRed: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: 1)
    }

    private static let paper = srgb(0xF3ECDD)
    private static let paperEdge = srgb(0xE9DFCB)
    private static let text = UIColor(red: 0x26 / 255.0, green: 0x23 / 255.0, blue: 0x1E / 255.0, alpha: 1)
    private static let mute = UIColor(red: 0x8C / 255.0, green: 0x85 / 255.0, blue: 0x76 / 255.0, alpha: 1)
    private static let washTone: UInt32 = 0x2A2B36
    private static let seal: UInt32 = 0xC2553C
    private static let ochre: UInt32 = 0xD9A441

    /// the three pages at `scale` pixels per point
    static func bake(_ l: Layout, scale: CGFloat) -> [CGImage] {
        let format = UIGraphicsImageRendererFormat()
        format.scale = min(3, scale)
        format.opaque = true
        format.preferredRange = .standard
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: l.width, height: l.height), format: format)
        return poems.indices.compactMap { index in
            renderer.image { drawPage($0.cgContext, index, l, format.scale) }.cgImage
        }
    }

    /// one paragraph, a line box of `line` pt with the glyphs centred in it; returns its height
    @discardableResult
    private static func paragraph(_ string: String, size: CGFloat, line: CGFloat, weight: UIFont.Weight, color: UIColor, x: CGFloat, y: CGFloat, spacing: CGFloat) -> CGFloat {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color, .kern: spacing]
        let lines = string.components(separatedBy: "\n")
        let box = font.ascender - font.descender
        for (i, row) in lines.enumerated() where !row.isEmpty {
            let baseline = y + CGFloat(i) * line + (line - box) / 2 + font.ascender
            NSAttributedString(string: row, attributes: attributes).draw(at: CGPoint(x: x, y: baseline - font.ascender))
        }
        return CGFloat(lines.count) * line
    }

    private static func wash(_ c: CGContext, _ hex: UInt32, _ alpha: CGFloat, blur: CGFloat, scale: CGFloat, _ shape: () -> Void) {
        let away: CGFloat = 6000
        c.saveGState()
        // a UIKit context's shadow is measured in points; its blur is about twice its sigma
        c.setShadow(offset: CGSize(width: away, height: 0), blur: 2 * blur, color: srgb(hex).copy(alpha: alpha))
        c.translateBy(x: -away, y: 0)
        c.setFillColor(gray: 0, alpha: 1)
        c.setStrokeColor(gray: 0, alpha: 1)
        c.setLineCap(.round)
        shape()
        c.restoreGState()
    }

    private static func disc(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat) {
        c.fillEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    /// Low Tide: a small red sun over four tide lines that thin out towards the shore
    private static func drawTide(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ scale: CGFloat) {
        wash(c, seal, 0.78, blur: 1.2, scale: scale) { disc(c, x + w * 0.74, y + h * 0.22, h * 0.13) }
        let rows: [(CGFloat, CGFloat, CGFloat, CGFloat, CGFloat)] = [(0.02, 0.98, 0.5, 11, 0.5), (0.14, 0.9, 0.66, 8, 0.36), (0.3, 0.78, 0.8, 6, 0.26), (0.44, 0.66, 0.92, 4, 0.18)]
        for (x0, x1, at, width, alpha) in rows {
            wash(c, washTone, alpha, blur: 2.4, scale: scale) {
                c.setLineWidth(width)
                c.move(to: CGPoint(x: x + w * x0, y: y + h * at))
                c.addCurve(to: CGPoint(x: x + w * x1, y: y + h * at),
                           control1: CGPoint(x: x + w * (x0 + (x1 - x0) * 0.3), y: y + h * (at - 0.035)),
                           control2: CGPoint(x: x + w * (x0 + (x1 - x0) * 0.68), y: y + h * (at + 0.035)))
                c.strokePath()
            }
        }
    }

    /// Kettle: a cup seen from the side and three threads of steam
    private static func drawSteam(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ scale: CGFloat) {
        let cx = x + w * 0.5
        let rim = y + h * 0.66
        wash(c, washTone, 0.5, blur: 2.2, scale: scale) {
            c.move(to: CGPoint(x: cx - h * 0.2, y: rim))
            c.addCurve(to: CGPoint(x: cx, y: rim + h * 0.3), control1: CGPoint(x: cx - h * 0.19, y: rim + h * 0.26), control2: CGPoint(x: cx - h * 0.1, y: rim + h * 0.3))
            c.addCurve(to: CGPoint(x: cx + h * 0.2, y: rim), control1: CGPoint(x: cx + h * 0.1, y: rim + h * 0.3), control2: CGPoint(x: cx + h * 0.19, y: rim + h * 0.26))
            c.closePath()
            c.fillPath()
        }
        wash(c, washTone, 0.62, blur: 1, scale: scale) {
            c.fillEllipse(in: CGRect(x: cx - h * 0.2, y: rim - h * 0.028, width: h * 0.4, height: h * 0.056))
        }
        wash(c, washTone, 0.46, blur: 1.6, scale: scale) {
            c.setLineWidth(5)
            c.move(to: CGPoint(x: cx + h * 0.19, y: rim + h * 0.06))
            c.addCurve(to: CGPoint(x: cx + h * 0.16, y: rim + h * 0.2), control1: CGPoint(x: cx + h * 0.3, y: rim + h * 0.04), control2: CGPoint(x: cx + h * 0.3, y: rim + h * 0.2))
            c.strokePath()
        }
        let threads: [(CGFloat, CGFloat, CGFloat)] = [(-0.09, 0.3, 5), (0, 0.42, 7), (0.09, 0.26, 4)]
        for (dx, alpha, width) in threads {
            let sx = cx + h * dx
            wash(c, washTone, alpha, blur: 3.2, scale: scale) {
                c.setLineWidth(width)
                c.move(to: CGPoint(x: sx, y: rim - h * 0.08))
                c.addCurve(to: CGPoint(x: sx, y: rim - h * 0.46), control1: CGPoint(x: sx - h * 0.09, y: rim - h * 0.22), control2: CGPoint(x: sx + h * 0.1, y: rim - h * 0.32))
                c.addCurve(to: CGPoint(x: sx + h * 0.01, y: rim - h * 0.66), control1: CGPoint(x: sx - h * 0.07, y: rim - h * 0.54), control2: CGPoint(x: sx + h * 0.05, y: rim - h * 0.6))
                c.strokePath()
            }
        }
        wash(c, seal, 0.78, blur: 0.8, scale: scale) { disc(c, cx - h * 0.34, rim + h * 0.24, h * 0.035) }
    }

    /// Last Bus: a pale moon, two headlands and four lit windows on the road between them
    private static func drawCoast(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ scale: CGFloat) {
        func p(_ u: CGFloat, _ v: CGFloat) -> CGPoint { CGPoint(x: x + w * u, y: y + h * v) }
        wash(c, ochre, 0.5, blur: 3, scale: scale) { disc(c, x + w * 0.3, y + h * 0.3, h * 0.2) }
        wash(c, washTone, 0.26, blur: 4, scale: scale) {
            c.move(to: p(0, 0.78))
            c.addCurve(to: p(0.62, 0.72), control1: p(0.2, 0.5), control2: p(0.42, 0.56))
            c.addCurve(to: p(1, 0.74), control1: p(0.76, 0.82), control2: p(0.9, 0.7))
            c.addLine(to: p(1, 1))
            c.addLine(to: p(0, 1))
            c.closePath()
            c.fillPath()
        }
        wash(c, washTone, 0.52, blur: 2.4, scale: scale) {
            c.move(to: p(0, 0.94))
            c.addCurve(to: p(0.74, 0.86), control1: p(0.3, 0.8), control2: p(0.56, 0.9))
            c.addCurve(to: p(1, 0.9), control1: p(0.86, 0.83), control2: p(0.94, 0.9))
            c.addLine(to: p(1, 1))
            c.addLine(to: p(0, 1))
            c.closePath()
            c.fillPath()
        }
        for i in 0..<4 {
            let k = CGFloat(i)
            wash(c, seal, 0.9, blur: 0.5, scale: scale) { disc(c, x + w * (0.6 + k * 0.03), y + h * (0.846 - k * 0.003), 2.2) }
        }
    }

    private static func drawPage(_ c: CGContext, _ index: Int, _ l: Layout, _ scale: CGFloat) {
        let width = CGFloat(l.width), height = CGFloat(l.height), top = CGFloat(l.top), bottom = CGFloat(l.bottom)
        let compact = height < 740
        let margin: CGFloat = 32
        // warm paper, a little darker towards the corners
        let space = CGColorSpace(name: CGColorSpace.sRGB)
        if let gradient = CGGradient(colorsSpace: space, colors: [paper, paper, paperEdge] as CFArray, locations: [0, 0.55, 1]) {
            let centre = CGPoint(x: width / 2, y: height * 0.42)
            c.drawRadialGradient(gradient, startCenter: centre, startRadius: 0, endCenter: centre, endRadius: hypot(width, height) * 0.62, options: [.drawsAfterEndLocation])
        }
        let poem = poems[index]
        var y = top + (compact ? 58 : 84)
        paragraph(poem.date, size: 13, line: 18, weight: .medium, color: mute, x: margin, y: y, spacing: 0)
        y += 18 + (compact ? 4 : 8)
        y += paragraph(poem.title, size: compact ? 28 : 34, line: compact ? 34 : 40, weight: .bold, color: text, x: margin, y: y, spacing: -0.6) + (compact ? 12 : 20)
        y += paragraph(poem.body, size: compact ? 16 : 17, line: compact ? 24 : 28, weight: .regular, color: text, x: margin, y: y, spacing: -0.1)

        // the drawing fills what is left above the page dots
        let floor = height - max(bottom, 12) - (compact ? 56 : 84)
        let room = max(0, floor - y - (compact ? 14 : 28))
        let h = min(room, compact ? 150 : 210)
        guard h >= 70 else { return }
        let w = width - margin * 2
        switch index {
        case 0: drawTide(c, margin, floor - h, w, h, scale)
        case 1: drawSteam(c, margin, floor - h, w, h, scale)
        default: drawCoast(c, margin, floor - h, w, h, scale)
        }
    }
}
