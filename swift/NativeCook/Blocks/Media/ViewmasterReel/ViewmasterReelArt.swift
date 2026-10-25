import SwiftUI
import UIKit

/*
 Viewmaster Reel — layout, the reel's slides, and the static art: the room (slate gradient,
 grain, vignette), the red bakelite viewer bezel around the window (static views, drawn once),
 and the cardboard reel itself (cream card, printed ring, seven film chips, rim notches, centre
 hole), baked off the main thread into one image which the screen then only turns.
 */

enum ViewmasterReelPalette {
    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }
    static func cg(_ hex: UInt32, _ alpha: CGFloat = 1) -> CGColor {
        CGColor(srgbRed: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }
    static func cga(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat, _ a: CGFloat) -> CGColor {
        CGColor(srgbRed: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }
    static let bg = rgb(0x121C23)
    static let ink = rgb(0xEEF2F3)
    static let mute = rgb(0xEEF2F3, 0.56)
    static let accent = rgb(0xE0574A)
}

struct ViewmasterReelSlide {
    var photo: Int
    var title: String
    var place: String
    /// Crop in photo pixels (800 × 600).
    var crop: CGRect
}

struct ViewmasterReelLayout: Equatable {
    static let headerH: CGFloat = 92
    static let captionH: CGFloat = 60
    static let leverRest: Double = -110
    static let leverPulled: Double = -176

    var W: CGFloat, H: CGFloat, top: CGFloat
    var bezel: CGRect, win: CGRect
    var captionY: CGFloat
    var cx: CGFloat, cy: CGFloat, r: CGFloat
    var px: CGFloat, py: CGFloat, len: CGFloat

    init(W: CGFloat, H: CGFloat, top: CGFloat) {
        let bx: CGFloat = 20
        let bw = W - 40
        let bh = (bw * 0.75).rounded()
        let by = top + 12 + Self.headerH
        let pad: CGFloat = 12
        captionY = by + bh + 12
        r = min(170, W * 0.43)
        cy = max(captionY + Self.captionH + 18 + r, H - r - 10)
        cx = W / 2
        self.W = W
        self.H = H
        self.top = top
        bezel = CGRect(x: bx, y: by, width: bw, height: bh)
        win = CGRect(x: bx + pad, y: by + pad, width: bw - pad * 2, height: bh - pad * 2)
        px = W - 18
        py = cy - r * 0.26
        len = min(86, r * 0.52)
    }
}

enum ViewmasterReelArt {
    /// Seven slides from six photos (800 × 600 px); the last is a closer crop of the river valley.
    static let slides: [ViewmasterReelSlide] = [
        .init(photo: 0, title: "Vestrahorn at dusk", place: "Stokksnes · 64.25° N 14.97° W", crop: CGRect(x: 0, y: 0, width: 800, height: 600)),
        .init(photo: 1, title: "Seljalandsfoss", place: "South Coast · 63.62° N 19.99° W", crop: CGRect(x: 0, y: 0, width: 800, height: 600)),
        .init(photo: 2, title: "Kirkjufell, first light", place: "Snæfellsnes · 64.94° N 23.31° W", crop: CGRect(x: 0, y: 0, width: 800, height: 600)),
        .init(photo: 3, title: "Braided rivers", place: "Fjallabak · 63.98° N 19.06° W", crop: CGRect(x: 0, y: 0, width: 800, height: 600)),
        .init(photo: 4, title: "Route 1 after midnight", place: "Eastfjords · 65.26° N 14.39° W", crop: CGRect(x: 0, y: 0, width: 800, height: 600)),
        .init(photo: 5, title: "Behind the falls", place: "Gljúfrabúi trail · 63.62° N 19.98° W", crop: CGRect(x: 0, y: 0, width: 800, height: 600)),
        .init(photo: 3, title: "The golden ridge", place: "Landmannalaugar · 63.99° N 19.06° W", crop: CGRect(x: 240, y: 40, width: 480, height: 360)),
    ]

    static let photos: [UIImage] = ["vestrahorn", "falls", "kirkjufell", "river", "road", "cave"].map { name in
        guard let url = Bundle.main.url(forResource: "ViewmasterReel-\(name)", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }

    static func pixels(_ image: UIImage) -> CGSize {
        image.size.width > 0 ? CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale) : CGSize(width: 800, height: 600)
    }

    /// A 128 px tile of seeded grain (instead of noise per pixel).
    static let grain: CGImage? = {
        let n = 128
        var bytes = [UInt8](repeating: 255, count: n * n * 4)
        for y in 0..<n {
            for x in 0..<n {
                let h = sin(Double(x) * 12.9898 + Double(y) * 78.233) * 43758.5453
                let v = UInt8(max(0, min(255, (h - floor(h)) * 255)))
                let i = (y * n + x) * 4
                bytes[i] = v; bytes[i + 1] = v; bytes[i + 2] = v
            }
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: n * 4,
                       space: CGColorSpaceCreateDeviceRGB(),
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                       provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
    }()

    // MARK: The reel, baked once

    private static func gradient(_ colors: [CGColor], _ stops: [CGFloat]) -> CGGradient {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors as CFArray, locations: stops)!
    }

    /// The reel, centred in a square of side 2r, slot 0 at 12 o'clock and the rest clockwise.
    /// Thread-safe (UIGraphicsImageRenderer + Core Graphics), so it is baked off the main thread.
    static func bakeReel(r: CGFloat, scale: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let side = r * 2
        let P = ViewmasterReelPalette.self
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { rc in
            let c = rc.cgContext
            c.translateBy(x: r, y: r)
            let disc = CGRect(x: -r, y: -r, width: side, height: side)
            // Card: cream, lit from the top left.
            c.saveGState()
            c.addEllipse(in: disc)
            c.clip()
            c.drawRadialGradient(gradient([P.cg(0xF6EEDD), P.cg(0xECE1CA), P.cg(0xDCCDB1)], [0, 0.55, 1]),
                                 startCenter: CGPoint(x: -r * 0.3, y: -r * 0.35), startRadius: 0,
                                 endCenter: CGPoint(x: -r * 0.3, y: -r * 0.35), endRadius: r * 1.5, options: [.drawsAfterEndLocation])
            // Paper grain on the card only.
            if let g = grain {
                c.setAlpha(0.09)
                c.setBlendMode(.multiply)
                c.draw(g, in: CGRect(x: 0, y: 0, width: 64, height: 64), byTiling: true)
                c.setAlpha(1)
                c.setBlendMode(.normal)
            }
            c.restoreGState()

            // Printed rings.
            let ink = P.cga(122, 32, 30, 0.85)
            c.setStrokeColor(ink)
            c.setLineWidth(1.4)
            c.strokeEllipse(in: disc.insetBy(dx: r * 0.07, dy: r * 0.07))
            c.setLineWidth(0.8)
            c.strokeEllipse(in: disc.insetBy(dx: r * 0.19, dy: r * 0.19))
            c.strokeEllipse(in: disc.insetBy(dx: r * 0.77, dy: r * 0.77))

            // The ring text: as many copies of the phrase as fit, spread evenly round the ring.
            let ringFont = UIFont.systemFont(ofSize: max(7, r * 0.06), weight: .bold)
            let numFont = UIFont.systemFont(ofSize: max(8, r * 0.065), weight: .bold)
            let phrase = "ICELAND · RING ROAD · JUNE 2025 · REEL 01 · "
            let attrs: [NSAttributedString.Key: Any] = [.font: ringFont, .foregroundColor: UIColor(cgColor: P.cga(122, 32, 30, 0.9))]
            func width(_ s: String) -> CGFloat { (s as NSString).size(withAttributes: [.font: ringFont]).width }
            let phraseW = phrase.map { width(String($0)) }.reduce(0, +)
            let tr = r * 0.85
            let copies = max(1, Int(floor(2 * .pi * tr / (phraseW * 1.18))))
            let text = Array(String(repeating: phrase, count: copies))
            let widths = text.map { width(String($0)) }
            let spread = 2 * .pi * tr / widths.reduce(0, +)
            var acc: CGFloat = 0
            for (i, ch) in text.enumerated() {
                let w = widths[i] * spread
                let a = (acc + w / 2) / tr - .pi / 2
                c.saveGState()
                c.translateBy(x: cos(a) * tr, y: sin(a) * tr)
                c.rotate(by: a + .pi / 2)
                // Baseline at size × 0.36 below the ring line, as in Skia.
                let s = String(ch) as NSString
                s.draw(at: CGPoint(x: -widths[i] / 2, y: ringFont.pointSize * 0.36 - ringFont.ascender), withAttributes: attrs)
                c.restoreGState()
                acc += w
            }

            // Seven film chips, image top towards the rim, with a number on the inner side.
            let cw = r * 0.3, ch = cw * 0.75, rcen = r * 0.56
            let numAttrs: [NSAttributedString.Key: Any] = [.font: numFont, .foregroundColor: UIColor(cgColor: P.cga(122, 32, 30, 0.95))]
            for (k, sl) in slides.enumerated() {
                let a = CGFloat(k) * 2 * .pi / CGFloat(slides.count)
                c.saveGState()
                c.rotate(by: a)
                c.translateBy(x: 0, y: -rcen)
                let outer = CGRect(x: -cw / 2 - 2, y: -ch / 2 - 2, width: cw + 4, height: ch + 4)
                c.saveGState()
                c.setShadow(offset: CGSize(width: 0, height: 1), blur: 2.4, color: P.cga(60, 40, 20, 0.35))
                c.addPath(CGPath(roundedRect: outer, cornerWidth: 2, cornerHeight: 2, transform: nil))
                c.setFillColor(P.cg(0x2A2421))
                c.fillPath()
                c.restoreGState()
                let chip = CGRect(x: -cw / 2, y: -ch / 2, width: cw, height: ch)
                let img = photos[sl.photo]
                let px = pixels(img)
                let sx = cw / sl.crop.width, sy = ch / sl.crop.height
                c.saveGState()
                c.clip(to: chip)
                img.draw(in: CGRect(x: chip.minX - sl.crop.minX * sx, y: chip.minY - sl.crop.minY * sy, width: px.width * sx, height: px.height * sy))
                c.drawLinearGradient(gradient([UIColor(white: 1, alpha: 0.28).cgColor, UIColor(white: 1, alpha: 0).cgColor, UIColor(white: 1, alpha: 0.08).cgColor], [0, 0.5, 1]),
                                     start: CGPoint(x: -cw / 2, y: -ch / 2), end: CGPoint(x: cw / 2, y: ch / 2), options: [])
                c.restoreGState()
                let label = "\(k + 1)" as NSString
                let lw = label.size(withAttributes: [.font: numFont]).width
                label.draw(at: CGPoint(x: -lw / 2, y: ch / 2 + r * 0.13 - numFont.ascender), withAttributes: numAttrs)
                c.restoreGState()
            }

            // Notches in the rim between the chips, and the centre hole with a pressed rim.
            c.setFillColor(P.cga(110, 80, 50, 0.35))
            c.fillEllipse(in: CGRect(x: -r * 0.085, y: -r * 0.085, width: r * 0.17, height: r * 0.17))
            c.setBlendMode(.clear)
            for k in 0..<slides.count {
                let a = (CGFloat(k) + 0.5) * 2 * .pi / CGFloat(slides.count) - .pi / 2
                let nr = r * 0.035
                c.fillEllipse(in: CGRect(x: cos(a) * r - nr, y: sin(a) * r - nr, width: nr * 2, height: nr * 2))
            }
            c.fillEllipse(in: CGRect(x: -r * 0.065, y: -r * 0.065, width: r * 0.13, height: r * 0.13))
        }
    }
}

/// The room, the reel's shadow and the viewer's red bezel: static, drawn once.
struct ViewmasterReelRoom: View, Equatable {
    var L: ViewmasterReelLayout
    typealias C = ViewmasterReelPalette

    var body: some View {
        Canvas { ctx, _ in
            let W = L.W, H = L.H, B = L.bezel, Wn = L.win
            let full = Path(CGRect(x: 0, y: 0, width: W, height: H))
            ctx.fill(full, with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0x1A2731), location: 0), .init(color: C.rgb(0x121C23), location: 0.5), .init(color: C.rgb(0x0D1418), location: 1)]),
                startPoint: .zero, endPoint: CGPoint(x: W * 0.3, y: H)))
            ctx.fill(full, with: .radialGradient(
                Gradient(stops: [.init(color: C.rgba(120, 150, 170, 0.16), location: 0), .init(color: C.rgba(80, 110, 130, 0.05), location: 0.5), .init(color: .clear, location: 1)]),
                center: CGPoint(x: W / 2, y: B.midY), startRadius: 0, endRadius: W * 0.9))

            // Reel shadow on the table: soft ambient + tight contact.
            func disc(_ x: CGFloat, _ y: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x - L.r, y: y - L.r, width: L.r * 2, height: L.r * 2)) }
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 18))
                l.fill(disc(L.cx + 2, L.cy + 14), with: .color(.black.opacity(0.5)))
            }
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 2.5))
                l.fill(disc(L.cx, L.cy + 2), with: .color(.black.opacity(0.55)))
            }

            // Viewer bezel: red bakelite, two shadows, a lit top edge, a black recess around the window.
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 20))
                l.fill(Path(roundedRect: B.offsetBy(dx: 0, dy: 16), cornerRadius: 30), with: .color(.black.opacity(0.5)))
            }
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 2))
                l.fill(Path(roundedRect: B.offsetBy(dx: 0, dy: 2), cornerRadius: 30), with: .color(.black.opacity(0.6)))
            }
            let bezel = Path(roundedRect: B, cornerRadius: 30)
            ctx.fill(bezel, with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0xD24A3C), location: 0), .init(color: C.rgb(0xA9322A), location: 0.5), .init(color: C.rgb(0x7E2019), location: 1)]),
                startPoint: B.origin, endPoint: CGPoint(x: B.minX + B.width * 0.4, y: B.maxY)))
            ctx.fill(bezel, with: .linearGradient(
                Gradient(stops: [.init(color: C.rgba(255, 230, 220, 0.28), location: 0), .init(color: C.rgba(255, 230, 220, 0), location: 0.35), .init(color: .black.opacity(0.18), location: 1)]),
                startPoint: CGPoint(x: 0, y: B.minY), endPoint: CGPoint(x: 0, y: B.maxY)))
            ctx.stroke(Path(roundedRect: B.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 29.5), with: .color(C.rgba(255, 215, 200, 0.35)), lineWidth: 1)
            ctx.fill(Path(roundedRect: Wn.insetBy(dx: -3, dy: -3), cornerRadius: 21), with: .color(C.rgb(0x0B0A0A)))
            ctx.stroke(Path(roundedRect: Wn.insetBy(dx: -3.6, dy: -3.6), cornerRadius: 21.6), with: .color(C.rgba(255, 190, 170, 0.22)), lineWidth: 1.2)
            // Two screw heads, for scale.
            for sx in [B.midX - 34, B.midX + 34] {
                ctx.fill(Path(ellipseIn: CGRect(x: sx - 2.6, y: B.minY + 6 - 2.6, width: 5.2, height: 5.2)), with: .radialGradient(
                    Gradient(colors: [C.rgb(0xF0E4DA), C.rgb(0x8E7F76)]), center: CGPoint(x: sx, y: B.minY + 6), startRadius: 0, endRadius: 4))
            }

            if let grain = ViewmasterReelArt.grain {
                ctx.drawLayer { l in
                    l.opacity = 0.05
                    l.blendMode = .overlay
                    l.fill(full, with: .tiledImage(Image(decorative: grain, scale: 2)))
                }
            }
            ctx.fill(full, with: .radialGradient(
                Gradient(stops: [.init(color: .clear, location: 0), .init(color: .black.opacity(0.15), location: 0.6), .init(color: .black.opacity(0.55), location: 1)]),
                center: CGPoint(x: W / 2, y: H * 0.45), startRadius: 0, endRadius: H * 0.75))
        }
        .frame(width: L.W, height: L.H)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The lens over the slide: darker edges and a faint glass streak (static, under the shutter).
struct ViewmasterReelLens: View, Equatable {
    var L: ViewmasterReelLayout
    var body: some View {
        Canvas { ctx, _ in
            let Wn = L.win, rect = Path(Wn)
            ctx.fill(rect, with: .radialGradient(
                Gradient(stops: [.init(color: .clear, location: 0), .init(color: .black.opacity(0.08), location: 0.62), .init(color: .black.opacity(0.55), location: 1)]),
                center: CGPoint(x: Wn.midX, y: Wn.midY), startRadius: 0, endRadius: Wn.width * 0.72))
            ctx.fill(rect, with: .linearGradient(
                Gradient(stops: [.init(color: .white.opacity(0), location: 0.25), .init(color: .white.opacity(0.10), location: 0.36), .init(color: .white.opacity(0), location: 0.48)]),
                startPoint: Wn.origin, endPoint: CGPoint(x: Wn.maxX, y: Wn.maxY)))
        }
        .frame(width: L.W, height: L.H)
    }
}

/// The window's inner shadow (static, over the shutter).
struct ViewmasterReelRecess: View, Equatable {
    var L: ViewmasterReelLayout
    var body: some View {
        Canvas { ctx, _ in
            ctx.addFilter(.blur(radius: 6))
            ctx.stroke(Path(roundedRect: L.win, cornerRadius: 18), with: .color(.black.opacity(0.55)), lineWidth: 10)
        }
        .frame(width: L.W, height: L.H)
    }
}

/// What lies over the reel and does not turn: the light, the gate bracket, the pivot cap.
struct ViewmasterReelGate: View, Equatable {
    var L: ViewmasterReelLayout
    typealias C = ViewmasterReelPalette

    var body: some View {
        Canvas { ctx, _ in
            let r = L.r
            ctx.fill(Path(ellipseIn: CGRect(x: L.cx - r, y: L.cy - r, width: r * 2, height: r * 2)), with: .radialGradient(
                Gradient(stops: [.init(color: C.rgba(255, 252, 240, 0.22), location: 0), .init(color: C.rgba(255, 252, 240, 0), location: 0.45), .init(color: C.rgba(40, 20, 0, 0.16), location: 1)]),
                center: CGPoint(x: L.cx - r * 0.4, y: L.cy - r * 0.5), startRadius: 0, endRadius: r * 1.6))
            let cw = r * 0.3, ch = cw * 0.75, gateY = L.cy - r * 0.56
            let chip = CGRect(x: L.cx - cw / 2, y: gateY - ch / 2, width: cw, height: ch)
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 4))
                l.fill(Path(roundedRect: chip.insetBy(dx: -7, dy: -7), cornerRadius: 5), with: .color(.black.opacity(0.35)))
            }
            ctx.stroke(Path(roundedRect: chip.insetBy(dx: -6, dy: -6), cornerRadius: 5), with: .color(C.rgb(0x2B2321)), lineWidth: 5)
            ctx.stroke(Path(roundedRect: chip.insetBy(dx: -3.5, dy: -3.5), cornerRadius: 3), with: .color(C.rgba(255, 220, 200, 0.35)), lineWidth: 0.8)
            ctx.drawLayer { l in
                l.blendMode = .screen
                l.fill(Path(chip), with: .color(C.rgba(255, 236, 200, 0.22)))
            }
        }
        .frame(width: L.W, height: L.H)
        .allowsHitTesting(false)
    }
}

/// The lever at rest angle 0 (pointing right from its pivot); the screen rotates it.
struct ViewmasterReelLever: View, Equatable {
    var L: ViewmasterReelLayout
    typealias C = ViewmasterReelPalette

    var body: some View {
        Canvas { ctx, _ in
            let px = L.px, py = L.py, len = L.len
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 5))
                l.fill(Path(roundedRect: CGRect(x: px + 2, y: py - 2, width: len, height: 14), cornerRadius: 7), with: .color(.black.opacity(0.45)))
            }
            ctx.fill(Path(roundedRect: CGRect(x: px, y: py - 7, width: len, height: 14), cornerRadius: 7), with: .linearGradient(
                Gradient(colors: [C.rgb(0xE26A5A), C.rgb(0xB3362C), C.rgb(0x7E2019)]),
                startPoint: CGPoint(x: 0, y: py - 7), endPoint: CGPoint(x: 0, y: py + 7)))
            ctx.fill(Path(ellipseIn: CGRect(x: px + len - 15, y: py - 15, width: 30, height: 30)), with: .radialGradient(
                Gradient(stops: [.init(color: C.rgb(0xF08A7A), location: 0), .init(color: C.rgb(0xC23E33), location: 0.5), .init(color: C.rgb(0x7A1E18), location: 1)]),
                center: CGPoint(x: px + len - 5, y: py - 6), startRadius: 0, endRadius: 18))
        }
        .frame(width: L.W, height: L.H)
        .allowsHitTesting(false)
    }
}

/// The lever's chrome pivot cap.
struct ViewmasterReelPivot: View, Equatable {
    var L: ViewmasterReelLayout
    typealias C = ViewmasterReelPalette

    var body: some View {
        Canvas { ctx, _ in
            ctx.fill(Path(ellipseIn: CGRect(x: L.px - 11, y: L.py - 11, width: 22, height: 22)), with: .radialGradient(
                Gradient(stops: [.init(color: C.rgb(0xFBF7F2), location: 0), .init(color: C.rgb(0xA8A29C), location: 0.55), .init(color: C.rgb(0x57524D), location: 1)]),
                center: CGPoint(x: L.px - 3, y: L.py - 4), startRadius: 0, endRadius: 13))
        }
        .frame(width: L.W, height: L.H)
        .allowsHitTesting(false)
    }
}
