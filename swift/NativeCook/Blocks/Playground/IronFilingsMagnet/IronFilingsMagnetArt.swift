import SwiftUI
import UIKit

/*
 Drawing for Iron Filings Magnet, a port of ironArt.ts: the walnut desk and the sheet of graph
 paper with its printed header and footer labels (a Canvas drawn once), the two bar magnets seen
 faintly through the paper (one pre-rendered image, placed per frame), the live readings, and the
 filing sprites drawn 900 times a frame (shadow, then metal).
 */
enum IronFilingsMagnetArt {
    typealias Geo = IronFilingsMagnetGeo
    private typealias M = IronFilingsMagnetMotion

    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    static let ink = rgb(0x2A2622)
    static let inkSoft = rgb(0x6F665B)
    static let background = rgb(0x2A1F18)
    /// The filing sprite: 12 × 3 pt drawn at 4 units per pt.
    static let spriteW = 48.0
    static let spriteH = 12.0
    /// Metal sprites for this many steps of "standing" (a tuft filing is drawn darker).
    static let standSteps = 6

    static let kickerFont = Font.system(size: 11, weight: .bold)
    static let titleFont = Font.custom("Georgia-Bold", size: 34)
    static let subFont = Font.custom("Georgia", size: 14)
    static let labelFont = Font.system(size: 10, weight: .bold)
    static let valueFont = Font.system(size: 22, weight: .semibold)
    static let poleFont = Font.system(size: 12, weight: .heavy)

    private static let unbounded = CGSize(width: 10_000, height: 1_000)

    private static func text(_ c: GraphicsContext, _ string: String, font: Font, color: Color, x: Double, baseline y: Double, trailing: Bool = false, centered: Bool = false) -> Double {
        let resolved = c.resolve(Text(string).font(font).foregroundStyle(color))
        let size = resolved.measure(in: unbounded)
        let base = resolved.firstBaseline(in: unbounded)
        var left = x
        if trailing { left = x - size.width }
        if centered { left = x - size.width / 2 }
        c.draw(resolved, in: CGRect(x: left, y: y - base, width: size.width + 1, height: size.height))
        return size.width
    }

    // MARK: - Sprites, made once

    private static var filingPath: Path {
        var body = Path()
        body.move(to: CGPoint(x: 1.5, y: 6))
        body.addCurve(to: CGPoint(x: 46.5, y: 4.6), control1: CGPoint(x: 9, y: 1.2), control2: CGPoint(x: 36, y: 0.8))
        body.addLine(to: CGPoint(x: 46.5, y: 7.2))
        body.addCurve(to: CGPoint(x: 1.5, y: 6), control1: CGPoint(x: 36, y: 11.2), control2: CGPoint(x: 9, y: 10.8))
        body.closeSubpath()
        return body
    }

    @MainActor
    private static func render(width: Double, height: Double, scale: Double, _ draw: @escaping (GraphicsContext) -> Void) -> Image {
        let renderer = ImageRenderer(content:
            Canvas { c, _ in draw(c) }
                .frame(width: width, height: height)
        )
        renderer.scale = scale
        renderer.isOpaque = false
        guard let ui = renderer.uiImage else { return Image(systemName: "minus").resizable() }
        return Image(uiImage: ui.preparingForDisplay() ?? ui)
    }

    struct Sprites {
        /// Metal, darker step by step (index = how much the filing stands up, 0 … standSteps − 1).
        let metal: [Image]
        let shadow: Image
        let magnet: Image
        /// The magnet image's frame around the magnet's centre.
        let magnetRect: CGRect
    }

    @MainActor private static var cache: (geo: Geo, scale: Double, sprites: Sprites)?

    @MainActor
    static func sprites(_ g: Geo, scale: Double) -> Sprites {
        if let cache, cache.geo == g, cache.scale == scale { return cache.sprites }
        // A sliver of iron: tapered, gunmetal with a bright ridge (modulated darker for tufts).
        let metal = (0..<standSteps).map { step -> Image in
            let stand = Double(step) / Double(standSteps - 1)
            let k = 1 - 0.38 * stand
            func dim(_ hex: UInt32, _ a: Double = 1) -> Color {
                let r = Double((hex >> 16) & 0xFF) / 255 * k
                let gg = Double((hex >> 8) & 0xFF) / 255 * k
                let b = Double(hex & 0xFF) / 255 * k
                return Color(.sRGB, red: r, green: gg, blue: b, opacity: a)
            }
            return render(width: spriteW, height: spriteH, scale: 2) { c in
                c.fill(filingPath, with: .linearGradient(
                    Gradient(stops: [
                        .init(color: dim(0x8E939C), location: 0),
                        .init(color: dim(0x3A3C42), location: 0.45),
                        .init(color: dim(0x17181C), location: 1),
                    ]),
                    startPoint: CGPoint(x: 0, y: 2),
                    endPoint: CGPoint(x: 0, y: 10)
                ))
                var ridge = Path()
                ridge.move(to: CGPoint(x: 8, y: 4.2))
                ridge.addLine(to: CGPoint(x: 38, y: 3.8))
                c.stroke(ridge, with: .color(dim(0xE1E6EE, 0.75)), style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
            }
        }
        // And its soft shadow.
        let shadow = render(width: spriteW, height: spriteH, scale: 2) { c in
            c.drawLayer { l in
                l.addFilter(.blur(radius: 1.6))
                l.fill(filingPath, with: .color(rgb(0x281E12, 0.55)))
            }
        }
        // A bar magnet seen through the paper: faint, soft-edged, red north and blue south.
        let L = g.half + 8 * g.u
        let T = g.thick
        let pad = 4.0
        let magnetRect = CGRect(x: -L - pad, y: -T - pad, width: 2 * (L + pad), height: 2 * (T + pad))
        let magnet = render(width: magnetRect.width, height: magnetRect.height, scale: scale) { c in
            var c = c
            c.translateBy(x: L + pad, y: T + pad)
            let halves: [([UInt32], Double)] = [([0x7EA2D6, 0x3E6AAE, 0x2A4C86], -1), ([0xE58A80, 0xC8443B, 0x9A2F29], 1)]
            for (stops, side) in halves {
                let shading = GraphicsContext.Shading.linearGradient(
                    Gradient(stops: [
                        .init(color: rgb(stops[0]), location: 0),
                        .init(color: rgb(stops[1]), location: 0.45),
                        .init(color: rgb(stops[2]), location: 1),
                    ]),
                    startPoint: CGPoint(x: 0, y: -T),
                    endPoint: CGPoint(x: 0, y: T)
                )
                c.drawLayer { l in
                    l.opacity = 0.66
                    l.addFilter(.blur(radius: 0.9))
                    let r = side < 0 ? CGRect(x: -L, y: -T, width: L, height: 2 * T) : CGRect(x: 0, y: -T, width: L, height: 2 * T)
                    l.fill(Path(roundedRect: r, cornerRadius: side < 0 ? 4 : 0.5), with: shading)
                    if side > 0 {
                        l.fill(Path(roundedRect: CGRect(x: L - 8, y: -T, width: 8, height: 2 * T), cornerRadius: 4), with: shading)
                    }
                }
            }
            _ = text(c, "N", font: poleFont, color: .white, x: L - 16 * g.u, baseline: 4 * g.u, centered: true)
            _ = text(c, "S", font: poleFont, color: .white, x: -L + 16 * g.u, baseline: 4 * g.u, centered: true)
        }
        let s = Sprites(metal: metal, shadow: shadow, magnet: magnet, magnetRect: magnetRect)
        cache = (g, scale, s)
        return s
    }

    /// Noise for the paper's grain and, stretched sideways, the desk's wood grain.
    private static func noise(width: Int, height: Int, seed: UInt32) -> Image {
        var state = seed
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { ctx in
            for y in 0..<height {
                for x in 0..<width {
                    state ^= state << 13
                    state ^= state >> 17
                    state ^= state << 5
                    ctx.cgContext.setFillColor(UIColor(white: CGFloat(state & 0xFF) / 255, alpha: 1).cgColor)
                    ctx.cgContext.fill(CGRect(x: x, y: y, width: 1, height: 1))
                }
            }
        }
        return Image(uiImage: image)
    }

    private static let paperGrain = noise(width: 128, height: 128, seed: 0x68E31DA4)
    private static let woodGrain = noise(width: 8, height: 256, seed: 0x1B873593)

    // MARK: - The paper, drawn once

    static func drawPaper(_ c: GraphicsContext, _ g: Geo) {
        let all = CGRect(x: 0, y: 0, width: g.w, height: g.h)
        c.fill(Path(all), with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0x3B2C22), location: 0),
                .init(color: rgb(0x2A1F18), location: 0.5),
                .init(color: rgb(0x1E1612), location: 1),
            ]),
            startPoint: .zero,
            endPoint: CGPoint(x: g.w, y: g.h)
        ))
        var wood = c
        wood.opacity = 0.1
        wood.draw(woodGrain, in: all)

        let rect = CGRect(x: g.paperX, y: g.paperY, width: g.paperW, height: g.paperH)
        let sheet = Path(roundedRect: rect, cornerRadius: 6)
        c.drawLayer { l in
            l.addFilter(.blur(radius: 14))
            l.translateBy(x: 0, y: 10)
            l.fill(sheet, with: .color(Color.black.opacity(0.45)))
        }
        c.drawLayer { l in
            l.addFilter(.blur(radius: 1.5))
            l.translateBy(x: 0, y: 1.5)
            l.fill(sheet, with: .color(Color.black.opacity(0.5)))
        }
        c.fill(sheet, with: .radialGradient(
            Gradient(stops: [
                .init(color: rgb(0xF4ECDA), location: 0),
                .init(color: rgb(0xEDE3CE), location: 0.6),
                .init(color: rgb(0xE2D5BB), location: 1),
            ]),
            center: CGPoint(x: g.w * 0.45, y: g.paperY + g.paperH * 0.4),
            startRadius: 0,
            endRadius: g.paperH * 0.75
        ))
        var inside = c
        inside.clip(to: sheet)
        var grain = inside
        grain.opacity = 0.06
        grain.fill(Path(all), with: .tiledImage(paperGrain, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 0.5))
        // Graph ruling, every fifth line a little darker, and the red margin.
        let step = 9 * g.u
        var light = Path()
        var dark = Path()
        var k = 0
        var x = g.paperX + 6
        while x < g.paperX + g.paperW {
            let r = CGRect(x: x, y: g.paperY, width: 0.6, height: g.paperH)
            if k % 5 == 0 { dark.addRect(r) } else { light.addRect(r) }
            x += step
            k += 1
        }
        k = 0
        var y = g.paperY + 6
        while y < g.paperY + g.paperH {
            let r = CGRect(x: g.paperX, y: y, width: g.paperW, height: 0.6)
            if k % 5 == 0 { dark.addRect(r) } else { light.addRect(r) }
            y += step
            k += 1
        }
        inside.fill(light, with: .color(rgb(0x466EA0, 0.06)))
        inside.fill(dark, with: .color(rgb(0x466EA0, 0.13)))
        inside.fill(Path(CGRect(x: g.paperX + 22, y: g.paperY, width: 0.9, height: g.paperH)), with: .color(rgb(0xC44A46, 0.35)))

        // Printed header.
        let hx = g.paperX + 34
        _ = text(c, "FIELD NOTES  ·  NO. 7", font: kickerFont, color: inkSoft, x: hx, baseline: g.paperY + 34 * g.u)
        _ = text(c, "Magnetism", font: titleFont, color: ink, x: hx, baseline: g.paperY + 72 * g.u)
        _ = text(c, "Exp. 04 — A bar magnet under the page", font: subFont, color: inkSoft, x: hx, baseline: g.paperY + 94 * g.u)
        _ = text(c, "Oct 25", font: subFont, color: inkSoft, x: g.paperX + g.paperW - 18, baseline: g.paperY + 34 * g.u, trailing: true)
        // Footer labels (values are drawn live).
        let fy = g.footerY
        c.fill(Path(CGRect(x: hx, y: fy - 14 * g.u, width: g.paperX + g.paperW - 18 - hx, height: 0.8)), with: .color(rgb(0x2A2622, 0.25)))
        let cols = [hx, hx + (g.paperW - 52) * 0.42, hx + (g.paperW - 52) * 0.72]
        for (i, label) in ["FIELD AT CENTRE", "POLES", "FILINGS"].enumerated() {
            _ = text(c, label, font: labelFont, color: inkSoft, x: cols[i], baseline: fy + 6 * g.u)
        }
        _ = text(c, "\(M.filings)", font: valueFont, color: ink, x: cols[2], baseline: fy + 30 * g.u)
    }

    // MARK: - Per frame

    /// Magnets (under the filings) and B's pull tab while it is parked.
    static func drawUnder(_ c: GraphicsContext, _ g: Geo, _ m: [Double], _ sp: Sprites) {
        for k in 0..<2 {
            var l = c
            l.translateBy(x: m[k * 3], y: m[k * 3 + 1])
            l.rotate(by: .radians(m[k * 3 + 2]))
            l.draw(sp.magnet, in: sp.magnetRect)
        }
        let parked = max(0, min(1, (m[3] - (g.w - 40 * g.u)) / (40 * g.u)))
        if parked > 0.01 {
            let tx = g.paperX + g.paperW - 30
            c.fill(Path(roundedRect: CGRect(x: tx, y: m[4] - 22, width: 26, height: 44), cornerRadius: 13), with: .color(rgb(0x2A2622, 0.85 * parked)))
            var chev = Path()
            chev.move(to: CGPoint(x: tx + 15, y: m[4] - 7))
            chev.addLine(to: CGPoint(x: tx + 9, y: m[4]))
            chev.addLine(to: CGPoint(x: tx + 15, y: m[4] + 7))
            c.stroke(chev, with: .color(rgb(0xEFE6D2, parked)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }
    }

    static func drawReadings(_ c: GraphicsContext, _ g: Geo, millitesla: Double, poles: Int) {
        let x0 = g.paperX + 34
        let x1 = x0 + (g.paperW - 52) * 0.42
        let y = g.footerY + 30 * g.u
        let v = "\(Int(millitesla.rounded()))"
        let w = text(c, v, font: valueFont, color: ink, x: x0, baseline: y)
        _ = text(c, " mT", font: subFont, color: inkSoft, x: x0 + w, baseline: y)
        _ = text(c, "\(poles)", font: valueFont, color: ink, x: x1, baseline: y)
    }
}
