import CoreImage
import SwiftUI
import UIKit

/*
 Static layers of the chart card, drawn once per size into bitmaps (a port of tideGaugeArt.ts):
 the card's backdrop (night bands before sunrise and after sunset, grid, axis labels, sun markers,
 the high and low tags), the buoy drawn about its waterline, and the soft glow of its lamp. Skia's
 blur masks are Core Image Gaussian blurs here.
*/

enum TideGaugeChartArt {
    static let ink = Color(hex: 0xE6F0F3)
    static let inkSoft = Color.rgba(200, 222, 230, 0.62)

    typealias M = TideGaugeChartMotion

    /// A piece of art and the rectangle it covers around its own origin (pt).
    struct Piece {
        let image: Image
        let rect: CGRect
    }

    private static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> UIColor {
        UIColor(red: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }

    private static func hex(_ v: UInt32, _ a: Double = 1) -> UIColor {
        rgba(Double((v >> 16) & 0xff), Double((v >> 8) & 0xff), Double(v & 0xff), a)
    }

    private static let inkUI = hex(0xE6F0F3)
    private static let inkSoftUI = rgba(200, 222, 230, 0.62)
    private static let sun = hex(0xF6C46A)
    private static let small = UIFont.systemFont(ofSize: 11, weight: .semibold)
    private static let tiny = UIFont.systemFont(ofSize: 10, weight: .semibold)

    private static func width(_ text: String, _ font: UIFont) -> Double {
        NSAttributedString(string: text, attributes: [.font: font]).size().width
    }

    /// Text with its baseline at y, like Skia's drawText.
    private static func text(_ s: String, _ font: UIFont, _ x: Double, _ y: Double, _ color: UIColor) {
        NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: color]).draw(at: CGPoint(x: x, y: y - font.ascender))
    }

    private static func renderer(_ size: CGSize, _ scale: Double) -> UIGraphicsImageRenderer {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        return UIGraphicsImageRenderer(size: size, format: format)
    }

    private static func piece(_ rect: CGRect, _ scale: Double, _ draw: (CGContext) -> Void) -> Piece {
        let ui = renderer(rect.size, scale).image { ctx in
            ctx.cgContext.translateBy(x: -rect.minX, y: -rect.minY)
            draw(ctx.cgContext)
        }
        return Piece(image: Image(uiImage: ui), rect: rect)
    }

    private static let ci = CIContext(options: [.useSoftwareRenderer: false])

    /// A shape filled with a colour and blurred by σ (in points), drawn into the context.
    private static func blurred(_ path: CGPath, color: UIColor, sigma: Double, scale: Double) {
        let pad = ceil(sigma * 3 + 2)
        let box = path.boundingBox.insetBy(dx: -pad, dy: -pad).integral
        let shape = renderer(box.size, scale).image { ctx in
            let g = ctx.cgContext
            g.translateBy(x: -box.minX, y: -box.minY)
            g.addPath(path)
            g.setFillColor(color.cgColor)
            g.fillPath()
        }
        guard let cg = shape.cgImage, let filter = CIFilter(name: "CIGaussianBlur") else { return }
        let input = CIImage(cgImage: cg)
        filter.setValue(input.clampedToExtent(), forKey: kCIInputImageKey)
        filter.setValue(sigma * scale, forKey: kCIInputRadiusKey)
        guard let out = filter.outputImage?.cropped(to: input.extent), let result = ci.createCGImage(out, from: input.extent) else { return }
        UIImage(cgImage: result, scale: scale, orientation: .up).draw(in: box)
    }

    private static var backdrops: [String: Image] = [:]
    private static var buoys: [Double: (buoy: Piece, lamp: Piece)] = [:]

    @MainActor
    static func backdrop(width: Double, height: Double, plot: M.Plot, scale: Double) -> Image {
        let key = "\(width)x\(height)@\(scale)"
        if let image = backdrops[key] { return image }
        let image = Image(uiImage: renderer(CGSize(width: width, height: height), scale).image { ctx in
            drawBackdrop(ctx.cgContext, plot)
        })
        backdrops[key] = image
        return image
    }

    @MainActor
    static func buoy(scale: Double) -> (buoy: Piece, lamp: Piece) {
        if let made = buoys[scale] { return made }
        let made = (
            buoy: piece(CGRect(x: -30, y: -50, width: 60, height: 70), scale) { drawBuoy($0, scale) },
            lamp: piece(CGRect(x: -30, y: -60.5, width: 60, height: 60), scale) { _ in
                blurred(CGPath(ellipseIn: CGRect(x: -9, y: -39.5, width: 18, height: 18), transform: nil), color: hex(0xFFC967), sigma: 6, scale: scale)
            }
        )
        buoys[scale] = made
        return made
    }

    private static func sunMark(_ c: CGContext, _ x: Double, _ y: Double, rising: Bool) {
        // Half a sun on a horizon line, with five short rays and an arrow for up or down.
        c.setFillColor(sun.cgColor)
        c.move(to: CGPoint(x: x - 6, y: y))
        c.addArc(center: CGPoint(x: x, y: y), radius: 6, startAngle: .pi, endAngle: 0, clockwise: false)
        c.closePath()
        c.fillPath()
        c.setLineWidth(1.4)
        c.setLineCap(.round)
        c.setStrokeColor(sun.cgColor)
        for i in 0..<5 {
            let a = Double.pi + Double.pi * (Double(i) + 0.5) / 5
            c.move(to: CGPoint(x: x + cos(a) * 8.5, y: y + sin(a) * 8.5))
            c.addLine(to: CGPoint(x: x + cos(a) * 11, y: y + sin(a) * 11))
        }
        c.strokePath()
        c.setStrokeColor(rgba(246, 196, 106, 0.55).cgColor)
        c.move(to: CGPoint(x: x - 12, y: y + 0.5))
        c.addLine(to: CGPoint(x: x + 12, y: y + 0.5))
        c.strokePath()
        let dir = rising ? -1.0 : 1.0
        c.setStrokeColor(sun.cgColor)
        c.setLineWidth(1.2)
        c.move(to: CGPoint(x: x - 2.5, y: y + 5 - dir * 1.5))
        c.addLine(to: CGPoint(x: x, y: y + 5 + dir * 1.5))
        c.addLine(to: CGPoint(x: x + 2.5, y: y + 5 - dir * 1.5))
        c.strokePath()
    }

    private static func drawBackdrop(_ c: CGContext, _ plot: M.Plot) {
        // Night before sunrise and after sunset.
        c.setFillColor(rgba(3, 10, 18, 0.32).cgColor)
        c.fill(CGRect(x: plot.x, y: plot.y, width: M.hourToX(M.sunrise, plot) - plot.x, height: plot.h))
        c.fill(CGRect(x: M.hourToX(M.sunset, plot), y: plot.y, width: plot.x + plot.w - M.hourToX(M.sunset, plot), height: plot.h))

        // Level lines every metre, labelled at the right edge.
        c.setLineWidth(1)
        c.setStrokeColor(rgba(200, 222, 230, 0.16).cgColor)
        for m in 0...Int(floor(M.hMax)) {
            if Double(m) < M.hMin { continue }
            let y = M.levelToY(Double(m), plot)
            c.move(to: CGPoint(x: plot.x, y: y))
            c.addLine(to: CGPoint(x: plot.x + plot.w, y: y))
            c.strokePath()
            let t = "\(m) m"
            text(t, tiny, plot.x + plot.w - width(t, tiny) - 4, y - 4, inkSoftUI)
        }

        // Hour ticks every 3 h, labels every 6 h.
        c.setStrokeColor(rgba(200, 222, 230, 0.10).cgColor)
        for h in stride(from: 0, through: Int(M.day), by: 3) {
            let x = M.hourToX(Double(h), plot)
            c.move(to: CGPoint(x: x, y: plot.y))
            c.addLine(to: CGPoint(x: x, y: plot.y + plot.h))
            c.strokePath()
            if h % 6 == 0 {
                let t = String(format: "%02d:00", h)
                let w = width(t, small)
                let tx = min(max(x - w / 2, plot.x), plot.x + plot.w - w)
                text(t, small, tx, plot.y + plot.h + 18, inkSoftUI)
            }
        }

        // Sunrise and sunset under the axis.
        sunMark(c, M.hourToX(M.sunrise, plot), plot.y + plot.h + 32, rising: true)
        sunMark(c, M.hourToX(M.sunset, plot), plot.y + plot.h + 32, rising: false)
        text("06:12", tiny, M.hourToX(M.sunrise, plot) + 15, plot.y + plot.h + 36, inkSoftUI)
        text("18:47", tiny, M.hourToX(M.sunset, plot) - 15 - width("18:47", tiny), plot.y + plot.h + 36, inkSoftUI)

        // "Now" marker above the plot.
        let nx = M.hourToX(M.now, plot)
        let tag = "NOW 10:40"
        let tw = width(tag, tiny)
        c.setFillColor(rgba(230, 240, 243, 0.10).cgColor)
        c.addPath(CGPath(roundedRect: CGRect(x: nx - tw / 2 - 7, y: plot.y - 24, width: tw + 14, height: 16), cornerWidth: 8, cornerHeight: 8, transform: nil))
        c.fillPath()
        text(tag, tiny, nx - tw / 2, plot.y - 12.5, inkUI)
        c.saveGState()
        c.setStrokeColor(rgba(230, 240, 243, 0.28).cgColor)
        c.setLineDash(phase: 0, lengths: [2, 3])
        c.move(to: CGPoint(x: nx, y: plot.y - 8))
        c.addLine(to: CGPoint(x: nx, y: plot.y + plot.h))
        c.strokePath()
        c.restoreGState()

        // High and low tags just above the curve's turning points.
        let tags: [(Double, Bool)] = [
            (M.tideHighAt - M.tidePeriod, true),
            (M.tideHighAt - M.tidePeriod / 2, false),
            (M.tideHighAt, true),
            (M.tideHighAt + M.tidePeriod / 2, false),
        ]
        for (hour, high) in tags {
            if hour < 0.5 || hour > M.day - 0.5 { continue }
            let x = M.hourToX(hour, plot)
            let y = M.levelToY(high ? 2.4 : 0.3, plot) - 14
            let t = high ? "H 2.4" : "L 0.3"
            text(t, tiny, x - width(t, tiny) / 2, y, inkSoftUI)
        }
    }

    /// The buoy at the origin: waterline at y = 0, mast up, light at (0, -31).
    private static func drawBuoy(_ c: CGContext, _ scale: Double) {
        // Shadow ring on the water.
        blurred(CGPath(ellipseIn: CGRect(x: -17, y: -2, width: 34, height: 7), transform: nil), color: rgba(4, 24, 40, 0.45), sigma: 2.5, scale: scale)

        // Hull: a rounded float, red with a white band, lit from the upper left.
        let hull = CGMutablePath()
        hull.move(to: CGPoint(x: -13, y: 1))
        hull.addCurve(to: CGPoint(x: 0, y: -13), control1: CGPoint(x: -13, y: -9), control2: CGPoint(x: -8, y: -13))
        hull.addCurve(to: CGPoint(x: 13, y: 1), control1: CGPoint(x: 8, y: -13), control2: CGPoint(x: 13, y: -9))
        hull.addCurve(to: CGPoint(x: -13, y: 1), control1: CGPoint(x: 8, y: 4), control2: CGPoint(x: -8, y: 4))
        hull.closeSubpath()
        c.saveGState()
        c.addPath(hull)
        c.clip()
        let colors = [hex(0xFF8A5C).cgColor, hex(0xE5462B).cgColor, hex(0x8E2117).cgColor] as CFArray
        if let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors, locations: [0, 0.5, 1]) {
            c.drawLinearGradient(g, start: CGPoint(x: -13, y: -13), end: CGPoint(x: 13, y: 3), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
        c.setFillColor(rgba(244, 240, 232, 0.92).cgColor)
        c.fill(CGRect(x: -14, y: -8, width: 28, height: 3.5))
        c.restoreGState()
        // Specular on the hull.
        blurred(CGPath(ellipseIn: CGRect(x: -9, y: -11.5, width: 6, height: 3.5), transform: nil), color: rgba(255, 236, 226, 0.55), sigma: 1.2, scale: scale)

        // Mast, cage and lamp housing.
        c.setFillColor(hex(0x2B3540).cgColor)
        c.fill(CGRect(x: -1.2, y: -27, width: 2.4, height: 14))
        c.setLineWidth(1.2)
        c.setStrokeColor(hex(0x3A4652).cgColor)
        c.move(to: CGPoint(x: -6, y: -13))
        c.addLine(to: CGPoint(x: -1, y: -27))
        c.move(to: CGPoint(x: 6, y: -13))
        c.addLine(to: CGPoint(x: 1, y: -27))
        c.strokePath()
        c.setFillColor(hex(0xE9E2D2).cgColor)
        c.addPath(CGPath(roundedRect: CGRect(x: -3.5, y: -34, width: 7, height: 7), cornerWidth: 2, cornerHeight: 2, transform: nil))
        c.fillPath()
        c.setFillColor(hex(0xFFD27A).cgColor)
        c.fillEllipse(in: CGRect(x: -2.2, y: -32.7, width: 4.4, height: 4.4))
    }
}
