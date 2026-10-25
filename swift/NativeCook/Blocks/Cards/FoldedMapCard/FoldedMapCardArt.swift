import SwiftUI
import UIKit

/*
 Folded Map Card — the printed map, drawn once into one bitmap (an atlas: the map's face on the
 left, the back of a panel on the right), cut into the four panel images the screen shows, plus
 the route, its elevation profile and the summit pin. A port of foldedMapArt.ts.

 Terrain: a height field (a ridge running up to the right, Kestrel Peak at its end, a basin with
 Alder Lake, and fbm noise for the folds of the ground). From it: a soft tint at one pixel per
 2 pt (paper, hill shading from the upper left, forest where a second noise is high and the ground
 is low, bare rock up high), upscaled with linear filtering; contours by marching squares on a
 3 pt grid (every fifth an index contour); a faint kilometre grid; the lake, a stream, symbols and
 labels; a title block; and paper grain at device resolution.
*/

struct FoldedMapCardArt: @unchecked Sendable {
    /// The front of each panel, then the back of a panel already mirrored so x runs along the
    /// panel's own left → right.
    let fronts: [Image]
    let back: Image
    /// The route in map coordinates.
    let route: [CGPoint]
    /// Elevation along the route, 0 (trailhead) … 1 (summit), `profileCount` samples.
    let profile: [Double]

    static let profileCount = 36

    @MainActor private static var cache: [String: FoldedMapCardArt] = [:]

    static func key(mw: Double, mh: Double, scale: Double) -> String { "\(mw)x\(mh)@\(scale)" }

    /// The art for a size if it was built already.
    @MainActor
    static func cached(mw: Double, mh: Double, scale: Double) -> FoldedMapCardArt? { cache[key(mw: mw, mh: mh, scale: scale)] }

    /// Builds the art off the main thread (it takes a moment in a debug build) and keeps it.
    @MainActor
    static func load(mw: Double, mh: Double, pw: Double, scale: Double) async -> FoldedMapCardArt {
        if let art = cached(mw: mw, mh: mh, scale: scale) { return art }
        let art = await Task.detached(priority: .userInitiated) { build(mw: mw, mh: mh, pw: pw, scale: scale) }.value
        cache[key(mw: mw, mh: mh, scale: scale)] = art
        return art
    }

    // MARK: Terrain

    private static func hash(_ x: Double, _ y: Double) -> Double {
        let s = sin(x * 127.1 + y * 311.7) * 43758.5453
        return s - floor(s)
    }

    static func vnoise(_ x: Double, _ y: Double) -> Double {
        let ix = floor(x)
        let iy = floor(y)
        var fx = x - ix
        var fy = y - iy
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        let a = hash(ix, iy)
        let b = hash(ix + 1, iy)
        let c = hash(ix, iy + 1)
        let d = hash(ix + 1, iy + 1)
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
    }

    private static func fbm(_ x: Double, _ y: Double) -> Double {
        vnoise(x, y) * 0.5 + vnoise(x * 2.03 + 5.2, y * 2.03 + 1.3) * 0.27
            + vnoise(x * 4.1 + 9.1, y * 4.1 - 3.7) * 0.15 + vnoise(x * 8.3 - 2.2, y * 8.3 + 7.7) * 0.08
    }

    static let peak = (0.86, 0.26)
    static let lake = (0.22, 0.68)

    /// Terrain height at a normalised map point (0..1 across, 0..1 down).
    static func terrain(_ nx: Double, _ ny: Double) -> Double {
        let ax = 0.05
        let ay = 0.95
        let vx = peak.0 - ax
        let vy = peak.1 - ay
        let t = max(0, min(1, ((nx - ax) * vx + (ny - ay) * vy) / (vx * vx + vy * vy)))
        let dx = nx - (ax + vx * t)
        let dy = ny - (ay + vy * t)
        let ridge = exp(-(dx * dx + dy * dy) / 0.03) * (0.25 + 0.45 * t)
        let pk = exp(-(pow(nx - peak.0, 2) + pow(ny - peak.1, 2)) / 0.018) * 0.45
        let lk = exp(-(pow(nx - lake.0, 2) + pow((ny - lake.1) * 1.4, 2)) / 0.012) * 0.3
        return ridge + pk - lk + fbm(nx * 4.2, ny * 3.1) * 0.32
    }

    private static let routeControl: [Double] = [
        0.07, 0.88, 0.15, 0.8, 0.24, 0.82, 0.31, 0.71, 0.29, 0.6, 0.38, 0.55, 0.46, 0.59, 0.52, 0.47, 0.6, 0.43, 0.66, 0.47, 0.72, 0.38,
        0.78, 0.33, 0.83, 0.3, 0.86, 0.26,
    ]

    private static func catmull(_ ctrl: [CGPoint], samples: Int) -> [CGPoint] {
        let n = ctrl.count
        func p(_ k: Int) -> CGPoint { ctrl[max(0, min(n - 1, k))] }
        var out: [CGPoint] = []
        for s in 0...samples {
            let f = Double(s) / Double(samples) * Double(n - 1)
            let i = min(n - 2, Int(floor(f)))
            let t = f - Double(i)
            let t2 = t * t
            let t3 = t2 * t
            func axis(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
                0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
            }
            let p0 = p(i - 1), p1 = p(i), p2 = p(i + 1), p3 = p(i + 2)
            out.append(CGPoint(x: axis(p0.x, p1.x, p2.x, p3.x), y: axis(p0.y, p1.y, p2.y, p3.y)))
        }
        return out
    }

    // MARK: Drawing

    private static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> UIColor {
        UIColor(red: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }

    private static func hex(_ v: UInt32, _ a: Double = 1) -> UIColor {
        rgba(Double((v >> 16) & 0xff), Double((v >> 8) & 0xff), Double(v & 0xff), a)
    }

    private static func width(_ text: String, _ font: UIFont) -> Double {
        NSAttributedString(string: text, attributes: [.font: font]).size().width
    }

    /// Text with its baseline at y, like Skia's drawText, with a paper-coloured halo behind it.
    private static func label(_ c: CGContext, _ text: String, _ font: UIFont, _ x: Double, _ y: Double, _ color: UIColor, halo: Bool = true) {
        let origin = CGPoint(x: x, y: y - font.ascender)
        if halo {
            c.saveGState()
            c.setLineJoin(.round)
            NSAttributedString(string: text, attributes: [
                .font: font,
                .strokeColor: rgba(240, 232, 212, 0.85),
                .strokeWidth: 2.4 / font.pointSize * 100,
            ]).draw(at: origin)
            c.restoreGState()
        }
        NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: color]).draw(at: origin)
    }

    static func build(mw: Double, mh: Double, pw: Double, scale: Double) -> FoldedMapCardArt {
        let aw = mw + pw
        let bold = UIFont.systemFont(ofSize: 9, weight: .bold)
        let small = UIFont.systemFont(ofSize: 7.5, weight: .medium)
        let base = UIFont.systemFont(ofSize: 8.5, weight: .medium)
        let italic = base.fontDescriptor.withSymbolicTraits(.traitItalic).map { UIFont(descriptor: $0, size: 8.5) } ?? base
        let title = UIFont.systemFont(ofSize: 17, weight: .heavy)

        // 1. The tint, one pixel per 2 pt, over the whole atlas.
        let tintStep = 2.0
        let tw = Int(ceil(aw / tintStep))
        let th = Int(ceil(mh / tintStep))
        var tint = [UInt8](repeating: 255, count: tw * th * 4)
        let e = 1 / max(mw, mh)
        for y in 0..<th {
            for x in 0..<tw {
                let o = (y * tw + x) * 4
                var r = 239.0, g = 231.0, b = 210.0
                if Double(x) * tintStep < mw {
                    let nx = Double(x) * tintStep / mw
                    let ny = Double(y) * tintStep / mh
                    let h = terrain(nx, ny)
                    let hx = terrain(nx + e, ny) - h
                    let hy = terrain(nx, ny + e) - h
                    // Hill shading: light from the upper left.
                    let sh = max(-1, min(1, (-hx - hy) * 60))
                    let lift = 1 + sh * 0.07
                    let forest = vnoise(nx * 7 + 3, ny * 5.5 - 1) * 0.72 + vnoise(nx * 13, ny * 11) * 0.28
                    let f = h < 0.55 ? max(0, min(1, (forest - 0.52) * 5)) * 0.75 * min(1, (0.55 - h) * 8) : 0
                    r += (190 - r) * f
                    g += (210 - g) * f
                    b += (165 - b) * f
                    let rock = max(0, min(1, (h - 0.62) * 6))
                    r += (226 - r) * rock * 0.6
                    g += (218 - g) * rock * 0.6
                    b += (204 - b) * rock * 0.6
                    r *= lift
                    g *= lift
                    b *= lift * 0.99
                } else {
                    r = 243
                    g = 236
                    b = 219
                }
                tint[o] = UInt8(max(0, min(255, r)))
                tint[o + 1] = UInt8(max(0, min(255, g)))
                tint[o + 2] = UInt8(max(0, min(255, b)))
            }
        }
        let tintImage = cgImage(tint, tw, th, premultiplied: false)

        // 8. Paper grain at device resolution (premultiplied): one 192 px tile of the same seeded
        // noise, repeated over the sheet, so the atlas builds quickly.
        let gw = 192
        let gh = 192
        var grain = [UInt8](repeating: 0, count: gw * gh * 4)
        var seed: Int64 = 1_234_567
        for i in 0..<(gw * gh) {
            seed = (seed &* 1_103_515_245 &+ 12345) & 0x7fff_ffff
            let v = Double(seed) / Double(0x7fff_ffff)
            let dark = v < 0.5
            let a = floor(abs(v - 0.5) * 2 * 22)
            let k = a / 255
            grain[i * 4] = UInt8((dark ? 60 : 255) * k)
            grain[i * 4 + 1] = UInt8((dark ? 40 : 250) * k)
            grain[i * 4 + 2] = UInt8((dark ? 20 : 235) * k)
            grain[i * 4 + 3] = UInt8(a)
        }
        let grainImage = cgImage(grain, gw, gh, premultiplied: true)

        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        let atlas = UIGraphicsImageRenderer(size: CGSize(width: aw, height: mh), format: format).image { ctx in
            let c = ctx.cgContext
            if let tintImage {
                c.saveGState()
                c.interpolationQuality = .medium
                // CGContext draws images bottom-up; flip around the image rect.
                c.translateBy(x: 0, y: Double(th) * tintStep)
                c.scaleBy(x: 1, y: -1)
                c.draw(tintImage, in: CGRect(x: 0, y: 0, width: Double(tw) * tintStep, height: Double(th) * tintStep))
                c.restoreGState()
            }

            c.saveGState()
            c.clip(to: CGRect(x: 0, y: 0, width: mw, height: mh))
            // 2. Kilometre grid.
            c.setStrokeColor(rgba(70, 120, 170, 0.22).cgColor)
            c.setLineWidth(0.5)
            var gx = 46.0
            while gx < mw {
                c.move(to: CGPoint(x: gx, y: 0))
                c.addLine(to: CGPoint(x: gx, y: mh))
                gx += 58
            }
            var gy = 30.0
            while gy < mh {
                c.move(to: CGPoint(x: 0, y: gy))
                c.addLine(to: CGPoint(x: mw, y: gy))
                gy += 58
            }
            c.strokePath()

            // 3. Contours.
            let step = 3.0
            let cols = Int(ceil(mw / step))
            let rows = Int(ceil(mh / step))
            var hs = [Double](repeating: 0, count: (cols + 1) * (rows + 1))
            var lo = Double.infinity
            var hi = -Double.infinity
            for j in 0...rows {
                for i in 0...cols {
                    let v = terrain(Double(i) * step / mw, Double(j) * step / mh)
                    hs[j * (cols + 1) + i] = v
                    lo = min(lo, v)
                    hi = max(hi, v)
                }
            }
            let minor = CGMutablePath()
            let index = CGMutablePath()
            let levels = 22
            for l in 1..<levels {
                let L = lo + (hi - lo) * Double(l) / Double(levels)
                let path = l % 5 == 0 ? index : minor
                for j in 0..<rows {
                    for i in 0..<cols {
                        let a = hs[j * (cols + 1) + i]
                        let b = hs[j * (cols + 1) + i + 1]
                        let cc = hs[(j + 1) * (cols + 1) + i + 1]
                        let d = hs[(j + 1) * (cols + 1) + i]
                        let k = (a > L ? 8 : 0) | (b > L ? 4 : 0) | (cc > L ? 2 : 0) | (d > L ? 1 : 0)
                        if k == 0 || k == 15 { continue }
                        let x0 = Double(i) * step
                        let y0 = Double(j) * step
                        let top = CGPoint(x: x0 + step * (L - a) / (b - a), y: y0)
                        let right = CGPoint(x: x0 + step, y: y0 + step * (L - b) / (cc - b))
                        let bottom = CGPoint(x: x0 + step * (L - d) / (cc - d), y: y0 + step)
                        let left = CGPoint(x: x0, y: y0 + step * (L - a) / (d - a))
                        var segs: [(CGPoint, CGPoint)] = []
                        switch k {
                        case 1, 14: segs = [(left, bottom)]
                        case 2, 13: segs = [(bottom, right)]
                        case 3, 12: segs = [(left, right)]
                        case 4, 11: segs = [(top, right)]
                        case 6, 9: segs = [(top, bottom)]
                        case 7, 8: segs = [(top, left)]
                        case 5: segs = [(top, right), (left, bottom)]
                        case 10: segs = [(top, left), (bottom, right)]
                        default: break
                        }
                        for s in segs {
                            path.move(to: s.0)
                            path.addLine(to: s.1)
                        }
                    }
                }
            }
            c.setLineCap(.round)
            c.setLineWidth(0.6)
            c.setStrokeColor(rgba(160, 104, 58, 0.5).cgColor)
            c.addPath(minor)
            c.strokePath()
            c.setLineWidth(1.1)
            c.setStrokeColor(rgba(150, 92, 46, 0.78).cgColor)
            c.addPath(index)
            c.strokePath()

            // 4. Lake and stream.
            let lakePath = CGMutablePath()
            for i in 0...48 {
                let a = Double(i) / 48 * Double.pi * 2
                let r = 1 + 0.18 * sin(a * 3 + 0.6) + 0.1 * sin(a * 5 + 2)
                let p = CGPoint(x: lake.0 * mw + cos(a) * 26 * r, y: lake.1 * mh + sin(a) * 14 * r)
                if i == 0 { lakePath.move(to: p) } else { lakePath.addLine(to: p) }
            }
            lakePath.closeSubpath()
            c.setFillColor(hex(0xA8CDD6).cgColor)
            c.addPath(lakePath)
            c.fillPath()
            c.setLineWidth(1)
            c.setStrokeColor(hex(0x5F96AB).cgColor)
            c.addPath(lakePath)
            c.strokePath()
            let lx = lake.0 * mw
            let ly = lake.1 * mh
            c.move(to: CGPoint(x: lx + 18, y: ly + 10))
            c.addCurve(to: CGPoint(x: lx + 34, y: mh + 4), control1: CGPoint(x: lx + 40, y: ly + 30), control2: CGPoint(x: lx + 8, y: mh - 30))
            c.setLineWidth(1.4)
            c.strokePath()

            // 5. Symbols and labels.
            let px = peak.0 * mw
            let py = peak.1 * mh
            c.move(to: CGPoint(x: px, y: py - 5))
            c.addLine(to: CGPoint(x: px + 4.5, y: py + 3))
            c.addLine(to: CGPoint(x: px - 4.5, y: py + 3))
            c.closePath()
            c.setFillColor(hex(0x5A3A22).cgColor)
            c.fillPath()
            label(c, "Kestrel Peak", bold, px - width("Kestrel Peak", bold) - 8, py - 6, hex(0x4A2E1A))
            label(c, "2 214", small, px - width("2 214", small) - 8, py + 6, hex(0x6A4A30))
            label(c, "Alder Lake", italic, lx - 24, ly - 20, hex(0x3F7F98))
            let hutX = 0.46 * mw
            let hutY = 0.6 * mh
            c.setFillColor(hex(0x7A2E22).cgColor)
            c.fill(CGRect(x: hutX - 3, y: hutY - 3, width: 6, height: 6))
            label(c, "Larch Hut", small, hutX + 6, hutY + 12, hex(0x5A3A22))
            label(c, "RIDGE LINE", small, 0.42 * mw, 0.36 * mh, rgba(90, 58, 34, 0.7))
            c.restoreGState()

            // 6. The title block in the lower right (the face the folded map shows), and the creases' wear.
            let tbx = mw - 106
            let block = CGRect(x: tbx, y: mh - 44, width: 98, height: 36)
            c.setFillColor(rgba(250, 246, 234, 0.94).cgColor)
            c.fill(block)
            c.setLineWidth(0.8)
            c.setStrokeColor(rgba(60, 40, 24, 0.7).cgColor)
            c.stroke(block)
            label(c, "RIDGE LINE", bold, tbx + 6, mh - 28, hex(0x2E2016), halo: false)
            label(c, "Alder Valley · 1:25 000", small, tbx + 6, mh - 15, hex(0x5A4632), halo: false)
            c.setFillColor(rgba(255, 252, 240, 0.35).cgColor)
            c.fill(CGRect(x: pw - 0.6, y: 0, width: 1.2, height: mh))
            c.fill(CGRect(x: pw * 2 - 0.6, y: 0, width: 1.2, height: mh))

            // 7. The back of a panel: cover title and a legend.
            let bx = mw + 12
            label(c, "RIDGE LINE", title, bx, 40, hex(0x2E2016), halo: false)
            label(c, "TRAIL", title, bx, 62, hex(0x2E2016), halo: false)
            label(c, "Alder Valley · Sheet 7", small, bx, 80, hex(0x6A5640), halo: false)
            let legend: [(UIColor, String)] = [
                (hex(0xB5442E), "Route"),
                (rgba(150, 92, 46, 0.8), "Contour 20 m"),
                (hex(0x5F96AB), "Water"),
                (hex(0x9DB386), "Forest"),
            ]
            for (i, item) in legend.enumerated() {
                let y = 112 + Double(i) * 18
                c.setStrokeColor(item.0.cgColor)
                c.setLineWidth(2)
                c.move(to: CGPoint(x: bx, y: y - 3.5))
                c.addLine(to: CGPoint(x: bx + 14, y: y - 3.5))
                c.strokePath()
                label(c, item.1, small, bx + 20, y, hex(0x4A3826), halo: false)
            }
            label(c, "Printed Oct 2026", small, bx, mh - 16, hex(0x8A7458), halo: false)

            if let grainImage {
                c.saveGState()
                c.interpolationQuality = .none
                c.clip(to: CGRect(x: 0, y: 0, width: aw, height: mh))
                c.draw(grainImage, in: CGRect(x: 0, y: 0, width: Double(gw) / scale, height: Double(gh) / scale), byTiling: true)
                c.restoreGState()
            }
        }

        // The four panel images, cut from the atlas at device resolution.
        var fronts: [Image] = []
        for k in 0..<3 {
            fronts.append(crop(atlas, CGRect(x: Double(k) * pw, y: 0, width: pw, height: mh), scale: scale, mirrored: false))
        }
        let back = crop(atlas, CGRect(x: mw, y: 0, width: pw, height: mh), scale: scale, mirrored: true)

        // Route and profile.
        var ctrl: [CGPoint] = []
        for i in stride(from: 0, to: routeControl.count, by: 2) {
            ctrl.append(CGPoint(x: routeControl[i] * mw, y: routeControl[i + 1] * mh))
        }
        let route = catmull(ctrl, samples: 150)
        var heights: [Double] = []
        for s in 0..<profileCount {
            let k = Int((Double(s) / Double(profileCount - 1) * Double(route.count - 1)).rounded())
            heights.append(terrain(route[k].x / mw, route[k].y / mh))
        }
        let h0 = heights[0]
        let h1 = heights[heights.count - 1]
        let profile = heights.map { max(-0.1, ($0 - h0) / (h1 - h0)) }
        return FoldedMapCardArt(fronts: fronts, back: back, route: route, profile: profile)
    }

    private static func cgImage(_ bytes: [UInt8], _ w: Int, _ h: Int, premultiplied: Bool) -> CGImage? {
        let info: CGImageAlphaInfo = premultiplied ? .premultipliedLast : .noneSkipLast
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGBitmapInfo(rawValue: info.rawValue),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }

    private static func crop(_ atlas: UIImage, _ rect: CGRect, scale: Double, mirrored: Bool) -> Image {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        let piece = UIGraphicsImageRenderer(size: rect.size, format: format).image { ctx in
            let c = ctx.cgContext
            if mirrored {
                c.translateBy(x: rect.width, y: 0)
                c.scaleBy(x: -1, y: 1)
            }
            atlas.draw(at: CGPoint(x: -rect.minX, y: -rect.minY))
        }
        return Image(uiImage: piece)
    }

    /// A map pin with its tip at (20, 40) of a 40 × 46 pt image: a red head with a cream centre,
    /// lit from the upper left, and its own soft shadow.
    static let pin: Image = {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let ui = UIGraphicsImageRenderer(size: CGSize(width: 40, height: 46), format: format).image { ctx in
            let c = ctx.cgContext
            c.translateBy(x: 20, y: 40)
            let head = CGMutablePath()
            head.move(to: .zero)
            head.addCurve(to: CGPoint(x: -10, y: -21), control1: CGPoint(x: -3, y: -7), control2: CGPoint(x: -10, y: -13))
            head.addCurve(to: CGPoint(x: 0, y: -31), control1: CGPoint(x: -10, y: -27), control2: CGPoint(x: -5.5, y: -31))
            head.addCurve(to: CGPoint(x: 10, y: -21), control1: CGPoint(x: 5.5, y: -31), control2: CGPoint(x: 10, y: -27))
            head.addCurve(to: .zero, control1: CGPoint(x: 10, y: -13), control2: CGPoint(x: 3, y: -7))
            head.closeSubpath()
            // Shadow: the head offset (1.5, 2), blurred (σ 2).
            c.saveGState()
            c.setShadow(offset: CGSize(width: 1.5, height: 2), blur: 4, color: rgba(30, 10, 4, 0.4).cgColor)
            c.addPath(head)
            c.setFillColor(hex(0xE0643F).cgColor)
            c.fillPath()
            c.restoreGState()
            c.saveGState()
            c.addPath(head)
            c.clip()
            let colors = [hex(0xFF9A72).cgColor, hex(0xE0643F).cgColor, hex(0xA8381F).cgColor] as CFArray
            if let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors, locations: [0, 0.5, 1]) {
                c.drawLinearGradient(g, start: CGPoint(x: -10, y: -31), end: CGPoint(x: 10, y: -4), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            }
            c.restoreGState()
            c.setFillColor(hex(0xFFF4E6).cgColor)
            c.fillEllipse(in: CGRect(x: -4, y: -25, width: 8, height: 8))
            c.setFillColor(rgba(255, 236, 224, 0.55).cgColor)
            c.fillEllipse(in: CGRect(x: -6.5, y: -27.5, width: 4, height: 4))
        }
        return Image(uiImage: ui)
    }()
}
