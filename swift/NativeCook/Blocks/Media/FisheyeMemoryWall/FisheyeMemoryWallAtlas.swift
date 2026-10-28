import SwiftUI
import UIKit

/// One period of the wall (308 × 440 pt at 4 px / pt), drawn once with Core Graphics. The lens shader
/// samples it with repeat addressing, so the wall never ends.
enum FisheyeMemoryWallAtlas {
    typealias M = FisheyeMemoryWallModel

    static let gutterColor: UInt32 = 0x151922

    static let shared: Image = {
        let cg = render()
        return Image(decorative: cg, scale: 1)
    }()

    static func render() -> CGImage {
        let s = M.atlasScale
        let size = CGSize(width: M.periodW * s, height: M.periodH * s)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        format.preferredRange = .standard
        let image = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let c = ctx.cgContext
            c.scaleBy(x: s, y: s)
            c.setFillColor(cg(gutterColor))
            c.fill(CGRect(x: 0, y: 0, width: M.periodW, height: M.periodH))
            for tile in M.tiles { draw(tile, in: c) }
        }
        return image.cgImage!
    }

    static func cg(_ v: UInt32, _ a: Double = 1) -> CGColor {
        CGColor(srgbRed: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
                blue: Double(v & 0xFF) / 255, alpha: a)
    }

    static func cg(_ v: Double, _ a: Double = 1) -> CGColor { cg(UInt32(v), a) }

    static func draw(_ tile: M.Tile, in c: CGContext) {
        let r = tile.rect
        let shape = CGPath(roundedRect: r, cornerWidth: tile.radius, cornerHeight: tile.radius, transform: nil)
        c.saveGState()
        c.addPath(shape)
        c.clip()
        // The design's unit square covers the tile, centred.
        let side = max(r.width, r.height)
        let ox = r.midX - side / 2
        let oy = r.midY - side / 2
        c.saveGState()
        if tile.flip {
            c.translateBy(x: r.midX, y: 0)
            c.scaleBy(x: -1, y: 1)
            c.translateBy(x: -r.midX, y: 0)
        }
        for op in M.designs[tile.design] {
            drawOp(op, c, r, ox, oy, side, tile)
        }
        c.restoreGState()
        if tile.tint > 0 {
            c.setFillColor(cg(M.tints[tile.tint], 0.12))
            c.fill(r)
        }
        c.restoreGState()
        // A hairline of light along the top edge.
        c.saveGState()
        c.addPath(shape)
        c.clip()
        c.setStrokeColor(cg(0xFFFFFF as UInt32, 0.10))
        c.setLineWidth(1)
        c.addPath(CGPath(roundedRect: r.insetBy(dx: 0.5, dy: 0.5), cornerWidth: tile.radius - 0.5,
                         cornerHeight: tile.radius - 0.5, transform: nil))
        c.strokePath()
        c.restoreGState()
    }

    static func drawOp(_ op: [Double], _ c: CGContext, _ r: CGRect, _ ox: Double, _ oy: Double, _ side: Double, _ tile: M.Tile) {
        func X(_ u: Double) -> Double { ox + u * side }
        func Y(_ v: Double) -> Double { oy + v * side }
        switch Int(op[0]) {
        case 0:
            let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB)!,
                               colors: [cg(op[1]), cg(op[2]), cg(op[3])] as CFArray, locations: [0, 0.55, 1])!
            c.drawLinearGradient(g, start: CGPoint(x: r.midX, y: r.minY), end: CGPoint(x: r.midX, y: r.maxY), options: [])
        case 1:
            c.setFillColor(cg(op[4], op[5]))
            let rad = op[3] * side
            c.fillEllipse(in: CGRect(x: X(op[1]) - rad, y: Y(op[2]) - rad, width: rad * 2, height: rad * 2))
        case 2:
            c.setFillColor(cg(op[5], op[6]))
            let rx = op[3] * side
            let ry = op[4] * side
            c.fillEllipse(in: CGRect(x: X(op[1]) - rx, y: Y(op[2]) - ry, width: rx * 2, height: ry * 2))
        case 3:
            c.setFillColor(cg(op[6], op[7]))
            let rect = CGRect(x: X(op[1]), y: Y(op[2]), width: op[3] * side, height: op[4] * side)
            let rad = min(op[5] * side, rect.width / 2, rect.height / 2)
            c.addPath(CGPath(roundedRect: rect, cornerWidth: rad, cornerHeight: rad, transform: nil))
            c.fillPath()
        case 4:
            c.setFillColor(cg(op[1], op[2]))
            var i = 3
            c.move(to: CGPoint(x: X(op[i]), y: Y(op[i + 1])))
            i += 2
            while i + 1 < op.count {
                c.addLine(to: CGPoint(x: X(op[i]), y: Y(op[i + 1])))
                i += 2
            }
            c.closePath()
            c.fillPath()
        case 5:
            c.setStrokeColor(cg(op[6], op[7]))
            c.setLineWidth(op[5] * side)
            c.setLineCap(.round)
            c.move(to: CGPoint(x: X(op[1]), y: Y(op[2])))
            c.addLine(to: CGPoint(x: X(op[3]), y: Y(op[4])))
            c.strokePath()
        case 6:
            let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB)!,
                               colors: [cg(op[4], op[5]), cg(op[4], 0)] as CFArray, locations: [0, 1])!
            let center = CGPoint(x: X(op[1]), y: Y(op[2]))
            c.drawRadialGradient(g, startCenter: center, startRadius: 0, endCenter: center, endRadius: op[3] * side, options: [])
        default:
            // The tile's date: the month over the day, centred (not mirrored).
            c.saveGState()
            if tile.flip {
                c.translateBy(x: r.midX, y: 0)
                c.scaleBy(x: -1, y: 1)
                c.translateBy(x: -r.midX, y: 0)
            }
            text(M.months[tile.month], size: 0.17 * side, weight: .bold, color: 0xD2553F, tracking: 0.02 * side,
                 x: r.midX, y: Y(0.33), in: c)
            text("\(tile.day)", size: 0.42 * side, weight: .heavy, color: 0x1F2430, tracking: -0.02 * side,
                 x: r.midX, y: Y(0.63), in: c)
            c.restoreGState()
        }
    }

    /// Draws a line of text centred on (x, y).
    static func text(_ s: String, size: Double, weight: UIFont.Weight, color: UInt32, tracking: Double,
                     x: Double, y: Double, in c: CGContext) {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        let attributed = NSAttributedString(string: s, attributes: [
            .font: font, .foregroundColor: UIColor(cgColor: cg(color)), .kern: tracking,
        ])
        let line = CTLineCreateWithAttributedString(attributed)
        let width = CTLineGetTypographicBounds(line, nil, nil, nil) - tracking
        c.saveGState()
        c.textMatrix = .identity
        c.translateBy(x: x - width / 2, y: y + (font.capHeight) / 2)
        c.scaleBy(x: 1, y: -1)
        c.textPosition = .zero
        CTLineDraw(line, c)
        c.restoreGState()
    }
}
