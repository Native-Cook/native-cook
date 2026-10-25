import SwiftUI
import UIKit

/*
 Velvet Curtain Sheet — the static art, drawn once into bitmaps (a port of velvetArt.ts): the
 poster of "The Glass Orchard" (a dusk orchard of glass trees, drawn in code with seeded shapes)
 and the sheet's seat map (screen arc, eight rows of twelve seats with an aisle, row letters,
 legend). Skia's blur masks (the moon's halo, the mist, the screen's glow) are drawn as soft
 gradients and widened strokes here.
*/

enum VelvetCurtainSheetArt {
    static let page = Color(hex: 0x140A0F)
    static let ink = Color(hex: 0xF3E9E4)
    static let inkSoft = Color.rgba(243, 233, 228, 0.62)
    static let gold = Color(hex: 0xF2C060)

    typealias M = VelvetCurtainSheetMotion

    private static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> UIColor {
        UIColor(red: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }

    private static func hex(_ v: UInt32, _ a: Double = 1) -> UIColor {
        rgba(Double((v >> 16) & 0xff), Double((v >> 8) & 0xff), Double(v & 0xff), a)
    }

    private static func gradient(_ colors: [UIColor], _ locations: [CGFloat]) -> CGGradient? {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray, locations: locations)
    }

    /// The same seeded generator as the React Native version (Park–Miller).
    private struct Rnd {
        var s: Double
        mutating func next() -> Double {
            s = (s * 16807).truncatingRemainder(dividingBy: 2_147_483_647)
            return s / 2_147_483_647
        }
    }

    private static let label = UIFont.systemFont(ofSize: 10, weight: .semibold)

    private static func width(_ text: String) -> Double {
        NSAttributedString(string: text, attributes: [.font: label]).size().width
    }

    /// Text with its baseline at y, like Skia's drawText.
    private static func text(_ s: String, _ x: Double, _ y: Double, _ color: UIColor) {
        NSAttributedString(string: s, attributes: [.font: label, .foregroundColor: color]).draw(at: CGPoint(x: x, y: y - label.ascender))
    }

    private static func glassTree(_ c: CGContext, _ x: Double, _ base: Double, _ h: Double, _ r: inout Rnd) {
        // Trunk and two limbs, tapered, in smoked glass.
        c.move(to: CGPoint(x: x - 3, y: base))
        c.addQuadCurve(to: CGPoint(x: x - 0.8, y: base - h * 0.62), control: CGPoint(x: x - 1.5, y: base - h * 0.4))
        c.addLine(to: CGPoint(x: x + 0.8, y: base - h * 0.62))
        c.addQuadCurve(to: CGPoint(x: x + 3, y: base), control: CGPoint(x: x + 1.5, y: base - h * 0.4))
        c.closePath()
        c.setFillColor(rgba(150, 190, 220, 0.30).cgColor)
        c.fillPath()
        c.setLineCap(.round)
        c.setLineWidth(1.4)
        c.setStrokeColor(rgba(170, 210, 235, 0.32).cgColor)
        c.move(to: CGPoint(x: x, y: base - h * 0.45))
        c.addLine(to: CGPoint(x: x - h * 0.2, y: base - h * 0.72))
        c.move(to: CGPoint(x: x, y: base - h * 0.5))
        c.addLine(to: CGPoint(x: x + h * 0.22, y: base - h * 0.78))
        c.strokePath()
        // Crown: overlapping glass discs, each with a cool rim and a bright glint.
        let cy = base - h * 0.78
        for _ in 0..<16 {
            let a = r.next() * Double.pi * 2
            let d = sqrt(r.next()) * h * 0.3
            let px = x + cos(a) * d * 1.15
            let py = cy + sin(a) * d * 0.8
            let rad = h * (0.07 + r.next() * 0.08)
            let pink = r.next() > 0.72
            let disc = CGRect(x: px - rad, y: py - rad, width: rad * 2, height: rad * 2)
            c.saveGState()
            c.addEllipse(in: disc)
            c.clip()
            let colors = pink
                ? [rgba(246, 176, 206, 0.42), rgba(180, 90, 150, 0.10), rgba(60, 90, 150, 0)]
                : [rgba(190, 232, 255, 0.40), rgba(90, 140, 210, 0.08), rgba(60, 90, 150, 0)]
            if let g = gradient(colors, [0, 0.7, 1]) {
                let ctr = CGPoint(x: px - rad * 0.3, y: py - rad * 0.35)
                c.drawRadialGradient(g, startCenter: ctr, startRadius: 0, endCenter: ctr, endRadius: rad * 1.2, options: [.drawsAfterEndLocation])
            }
            c.restoreGState()
            c.setLineWidth(0.8)
            c.setStrokeColor((pink ? rgba(255, 200, 225, 0.35) : rgba(210, 240, 255, 0.35)).cgColor)
            c.strokeEllipse(in: disc)
            c.setFillColor(rgba(255, 255, 250, 0.8).cgColor)
            let g = max(0.6, rad * 0.12)
            c.fillEllipse(in: CGRect(x: px - rad * 0.35 - g, y: py - rad * 0.4 - g, width: g * 2, height: g * 2))
        }
    }

    private static var posters: [String: Image] = [:]
    private static var seatMaps: [String: Image] = [:]

    @MainActor
    static func poster(width: Double, height: Double, scale: Double) -> Image {
        let key = "\(width)x\(height)@\(scale)"
        if let image = posters[key] { return image }
        let image = Image(uiImage: drawPoster(width: width, height: height, scale: scale))
        posters[key] = image
        return image
    }

    @MainActor
    static func seats(width: Double, pitch: Double, scale: Double) -> (image: Image, height: Double) {
        let h = legendY(width: width, pitch: pitch) + 12
        let key = "\(width)x\(pitch)@\(scale)"
        if let image = seatMaps[key] { return (image, h) }
        let image = Image(uiImage: drawSeats(width: width, height: h, pitch: pitch, scale: scale))
        seatMaps[key] = image
        return (image, h)
    }

    private static func legendY(width: Double, pitch: Double) -> Double {
        M.seatAt(M.rows.count * M.perRow - 1, width: width, pitch: pitch).y + M.seatH + 18
    }

    private static func renderer(_ w: Double, _ h: Double, _ scale: Double, opaque: Bool) -> UIGraphicsImageRenderer {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = opaque
        return UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format)
    }

    private static func drawPoster(width: Double, height: Double, scale: Double) -> UIImage {
        renderer(width, height, scale, opaque: true).image { ctx in
            let c = ctx.cgContext
            let horizon = height * 0.66
            if let g = gradient([hex(0x0A1424), hex(0x1C2A46), hex(0x47324F), hex(0x7A4A5C)], [0, 0.45, 0.85, 1]) {
                c.drawLinearGradient(g, start: .zero, end: CGPoint(x: 0, y: horizon), options: [.drawsAfterEndLocation])
            }
            var r = Rnd(s: 11)
            // Stars.
            for _ in 0..<70 {
                let a = 0.25 + r.next() * 0.6
                let sx = r.next() * width
                let sy = r.next() * horizon * 0.7
                let sr = 0.4 + r.next() * 0.9
                c.setFillColor(rgba(240, 232, 220, (a * 100).rounded() / 100).cgColor)
                c.fillEllipse(in: CGRect(x: sx - sr, y: sy - sr, width: sr * 2, height: sr * 2))
            }
            // Moon with its halo (a disc of 44 pt blurred by σ 26, as a radial falloff).
            let mx = width * 0.74
            let my = height * 0.2
            if let g = gradient([rgba(246, 226, 196, 0.17), rgba(246, 226, 196, 0.11), rgba(246, 226, 196, 0.035), rgba(246, 226, 196, 0)], [0, 0.46, 0.73, 1]) {
                c.drawRadialGradient(g, startCenter: CGPoint(x: mx, y: my), startRadius: 0, endCenter: CGPoint(x: mx, y: my), endRadius: 96, options: [])
            }
            c.saveGState()
            c.addEllipse(in: CGRect(x: mx - 24, y: my - 24, width: 48, height: 48))
            c.clip()
            if let g = gradient([hex(0xFFF4DE), hex(0xF1DDB8), hex(0xC9AE8A)], [0, 0.6, 1]) {
                let ctr = CGPoint(x: mx - 8, y: my - 8)
                c.drawRadialGradient(g, startCenter: ctr, startRadius: 0, endCenter: ctr, endRadius: 34, options: [.drawsAfterEndLocation])
            }
            c.restoreGState()
            // Far hills, then the orchard on a low rise.
            c.move(to: CGPoint(x: 0, y: horizon - 20))
            c.addCurve(to: CGPoint(x: width * 0.62, y: horizon - 30), control1: CGPoint(x: width * 0.25, y: horizon - 48), control2: CGPoint(x: width * 0.45, y: horizon - 10))
            c.addCurve(to: CGPoint(x: width, y: horizon - 30), control1: CGPoint(x: width * 0.8, y: horizon - 46), control2: CGPoint(x: width * 0.92, y: horizon - 22))
            c.addLine(to: CGPoint(x: width, y: height))
            c.addLine(to: CGPoint(x: 0, y: height))
            c.closePath()
            c.setFillColor(hex(0x1A1830).cgColor)
            c.fillPath()
            let trees = [0.12, 0.3, 0.5, 0.68, 0.86]
            for (i, tx) in trees.enumerated() {
                glassTree(c, width * tx, horizon + 8 + Double(i % 2) * 10, 120 + (i == 2 ? 40 : Double(i % 2) * 18), &r)
            }
            // Low mist across the orchard (a 36 pt band blurred by σ 18).
            if let g = gradient(
                [rgba(214, 196, 230, 0), rgba(214, 196, 230, 0.041), rgba(214, 196, 230, 0.082), rgba(214, 196, 230, 0.041), rgba(214, 196, 230, 0)],
                [0, 0.33, 0.5, 0.67, 1]
            ) {
                c.drawLinearGradient(g, start: CGPoint(x: 0, y: horizon - 50), end: CGPoint(x: 0, y: horizon + 58), options: [])
            }
            c.move(to: CGPoint(x: 0, y: horizon + 14))
            c.addQuadCurve(to: CGPoint(x: width, y: horizon + 16), control: CGPoint(x: width * 0.5, y: horizon - 4))
            c.addLine(to: CGPoint(x: width, y: height))
            c.addLine(to: CGPoint(x: 0, y: height))
            c.closePath()
            c.setFillColor(hex(0x100A14).cgColor)
            c.fillPath()
            // Fade into the page so the title reads.
            if let g = gradient([rgba(20, 10, 15, 0), rgba(20, 10, 15, 0.72), hex(0x140A0F)], [0, 0.55, 1]) {
                c.drawLinearGradient(g, start: CGPoint(x: 0, y: height * 0.38), end: CGPoint(x: 0, y: height), options: [.drawsAfterEndLocation])
            }
        }
    }

    private static func drawSeats(width: Double, height: Double, pitch: Double, scale: Double) -> UIImage {
        renderer(width, height, scale, opaque: false).image { ctx in
            let c = ctx.cgContext
            let x0 = (width - M.seatsWidth) / 2
            let x1 = x0 + M.seatsWidth
            // The screen: a lit arc with its glow.
            let arc = CGMutablePath()
            arc.move(to: CGPoint(x: x0 + 10, y: M.screenY + 8))
            arc.addQuadCurve(to: CGPoint(x: x1 - 10, y: M.screenY + 8), control: CGPoint(x: width / 2, y: M.screenY - 6))
            c.setLineCap(.round)
            for w in [26.0, 20, 14, 8] {
                c.setLineWidth(w)
                c.setStrokeColor(rgba(240, 214, 180, 0.06).cgColor)
                c.addPath(arc)
                c.strokePath()
            }
            c.setLineWidth(2.5)
            c.setStrokeColor(hex(0xEBD6B8).cgColor)
            c.addPath(arc)
            c.strokePath()
            let soft = rgba(243, 233, 228, 0.62)
            text("SCREEN", width / 2 - Self.width("SCREEN") / 2, M.screenY + 22, soft)
            // Seats.
            for i in 0..<(M.rows.count * M.perRow) {
                let (x, y) = M.seatAt(i, width: width, pitch: pitch)
                let taken = M.isTaken(i)
                let seat = CGRect(x: x, y: y, width: M.seatW, height: M.seatH)
                c.saveGState()
                c.addPath(CGPath(roundedRect: seat, cornerWidth: 5, cornerHeight: 5, transform: nil))
                c.clip()
                let cols = taken ? [hex(0x2A1C24), hex(0x1F141B), hex(0x180F15)] : [hex(0x5A3F4C), hex(0x432E39), hex(0x35232D)]
                if let g = gradient(cols, [0, 0.5, 1]) {
                    c.drawLinearGradient(g, start: CGPoint(x: x, y: y), end: CGPoint(x: x, y: y + M.seatH), options: [])
                }
                c.restoreGState()
                // Backrest seam and a lit top edge.
                c.setFillColor((taken ? rgba(0, 0, 0, 0.25) : rgba(10, 4, 8, 0.35)).cgColor)
                c.fill(CGRect(x: x + 2, y: y + 7, width: M.seatW - 4, height: 1))
                c.setFillColor((taken ? rgba(255, 230, 230, 0.05) : rgba(255, 225, 230, 0.18)).cgColor)
                c.fill(CGRect(x: x + 4, y: y + 1, width: M.seatW - 8, height: 1))
            }
            let rowInk = rgba(243, 233, 228, 0.42)
            for r in 0..<M.rows.count {
                let y = M.seatAt(r * M.perRow, width: width, pitch: pitch).y
                let w = Self.width(M.rows[r])
                text(M.rows[r], x0 - 16 - w / 2, y + M.seatH - 5, rowInk)
                text(M.rows[r], x1 + 16 - w / 2, y + M.seatH - 5, rowInk)
            }
            // Legend.
            let ly = legendY(width: width, pitch: pitch)
            let items: [(UIColor, String)] = [(hex(0x4A3440), "Available"), (hex(0x22171E), "Taken"), (hex(0xF2C060), "Selected")]
            var total = 0.0
            for item in items { total += 14 + Self.width(item.1) + 20 }
            var lx = width / 2 - (total - 20) / 2
            for item in items {
                c.setFillColor(item.0.cgColor)
                c.addPath(CGPath(roundedRect: CGRect(x: lx, y: ly - 9, width: 10, height: 10), cornerWidth: 3, cornerHeight: 3, transform: nil))
                c.fillPath()
                text(item.1, lx + 14, ly, soft)
                lx += 14 + Self.width(item.1) + 20
            }
        }
    }
}
