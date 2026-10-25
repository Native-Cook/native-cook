import CoreImage
import CoreText
import SwiftUI
import UIKit

/*
 Paper Cut Diorama Splash — layout and the six layers of cut paper, each baked once (off the main
 thread, Core Graphics + Core Image blurs) into its own image with the soft shadow it throws on
 the layer behind, plus the light bloom of the cut-out logo. The bare paper backing is a static
 view.

 Back to front: a dusk sky (deep blue → violet → peach, a paper moon, a few paper stars), far
 lavender hills, a row of teal pines, green meadow with a winding river, a dark grass edge with
 blades, and the cream front frame with an arched window, the logo (a little arched door) and
 the wordmark "Fable" cut right through it. Layers are 30 pt wider than the screen on each side
 so tilting never shows an edge, and run 40 pt past the bottom.
 */

enum PaperCutDioramaSplashPalette {
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
    static let bg = rgb(0xEFE6D6)
    static let ink = rgb(0x2A2230)
    static let mute = rgb(0x2A2230, 0.58)
    static let rust = rgb(0xB0663E)
}

struct PaperCutDioramaSplashLayout: Equatable {
    var W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat
    var x0: CGFloat, x1: CGFloat, winTop: CGFloat, winBottom: CGFloat, r: CGFloat
    var emblem: CGRect
    var wordY: CGFloat, tagY: CGFloat

    init(W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat) {
        x0 = 26
        x1 = W - 26
        r = (x1 - x0) / 2
        winTop = top + 34
        winBottom = max(winTop + r + 120, H * 0.67)
        let ew: CGFloat = 50, eh: CGFloat = 68
        let ey = winBottom + 26
        emblem = CGRect(x: W / 2 - ew / 2, y: ey, width: ew, height: eh)
        wordY = ey + eh + 60
        tagY = ey + eh + 92
        self.W = W
        self.H = H
        self.top = top
        self.bottom = bottom
    }

    /// The point every layer scales about: inside the logo's door.
    var ex: CGFloat { emblem.midX }
    var ey: CGFloat { emblem.minY + emblem.height * 0.62 }
}

/// One baked layer and where it lies on screen.
struct PaperCutDioramaSplashLayerArt {
    var image: UIImage
    var rect: CGRect
}

enum PaperCutDioramaSplashArt {
    typealias Lay = PaperCutDioramaSplashLayout
    typealias P = PaperCutDioramaSplashPalette
    static let pad: CGFloat = 30

    static func hash(_ n: Double) -> Double {
        let v = sin(n * 91.345 + 7.13) * 43758.5453
        return v - floor(v)
    }

    /// A 128 px tile of seeded grain (light, so a multiply darkens a little).
    static let grain: CGImage? = {
        let n = 128
        var bytes = [UInt8](repeating: 255, count: n * n * 4)
        for y in 0..<n {
            for x in 0..<n {
                let h = sin(Double(x) * 12.9898 + Double(y) * 78.233) * 43758.5453
                let v = UInt8(max(0, min(255, 110 + (h - floor(h)) * 145)))
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

    // MARK: Shapes

    /// An arch: straight sides from `bottom` up to the half circle over the top of `rect`'s width.
    static func arch(x0: CGFloat, x1: CGFloat, top: CGFloat, bottom: CGFloat) -> CGPath {
        let r = (x1 - x0) / 2, cx = x0 + r, cy = top + r
        let p = CGMutablePath()
        p.move(to: CGPoint(x: x0, y: bottom))
        p.addLine(to: CGPoint(x: x0, y: cy))
        for i in 1...96 {
            let th = Double.pi * (1 + Double(i) / 96)
            p.addLine(to: CGPoint(x: cx + r * CGFloat(cos(th)), y: cy + r * CGFloat(sin(th))))
        }
        p.addLine(to: CGPoint(x: x1, y: bottom))
        p.closeSubpath()
        return p
    }

    static func windowPath(_ L: Lay) -> CGPath { arch(x0: L.x0, x1: L.x1, top: L.winTop, bottom: L.winBottom) }
    static func doorPath(_ L: Lay) -> CGPath {
        let E = L.emblem
        return arch(x0: E.minX, x1: E.maxX, top: E.minY, bottom: E.maxY)
    }

    /// "Fable" in Georgia Bold 46, centred, its baseline at wordY (glyph outlines, for cutting).
    static func wordPath(_ L: Lay) -> CGPath {
        let font = UIFont(name: "Georgia-Bold", size: 46) ?? UIFont.systemFont(ofSize: 46, weight: .bold)
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: "Fable", attributes: [.font: font]))
        let width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
        let x = L.W / 2 - width / 2
        let out = CGMutablePath()
        for run in CTLineGetGlyphRuns(line) as! [CTRun] {
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count)
            var positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: count), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: count), &positions)
            let attrs = CTRunGetAttributes(run) as NSDictionary
            let runFont = attrs[kCTFontAttributeName as String] as! CTFont
            for k in 0..<count {
                let t = CGAffineTransform(translationX: x + positions[k].x, y: L.wordY).scaledBy(x: 1, y: -1)
                if let g = CTFontCreatePathForGlyph(runFont, glyphs[k], nil) { out.addPath(g, transform: t) }
            }
        }
        return out
    }

    // MARK: Baking helpers

    final class Baker {
        let L: Lay
        let scale: CGFloat
        let ci = CIContext()

        init(_ L: Lay, scale: CGFloat) {
            self.L = L
            self.scale = scale
        }

        /// A layer surface from screen y to 40 pt past the bottom, 30 pt wider each side.
        func surface(y: CGFloat, _ body: (CGContext) -> Void) -> PaperCutDioramaSplashLayerArt {
            let rect = CGRect(x: -pad, y: y, width: L.W + pad * 2, height: L.H + 40 - y)
            let format = UIGraphicsImageRendererFormat()
            format.scale = scale
            format.opaque = false
            let image = UIGraphicsImageRenderer(size: rect.size, format: format).image { rc in
                let c = rc.cgContext
                c.translateBy(x: pad, y: -y)
                body(c)
            }
            return .init(image: image, rect: rect)
        }

        /// A filled (blurred) silhouette of `path` as an image with its rect, σ in pt.
        func blurred(_ path: CGPath, _ color: CGColor, sigma: CGFloat) -> PaperCutDioramaSplashLayerArt {
            let rect = path.boundingBoxOfPath.insetBy(dx: -sigma * 3 - 2, dy: -sigma * 3 - 2).integral
            let format = UIGraphicsImageRendererFormat()
            format.scale = scale
            format.opaque = false
            let shape = UIGraphicsImageRenderer(size: rect.size, format: format).image { rc in
                let c = rc.cgContext
                c.translateBy(x: -rect.minX, y: -rect.minY)
                c.addPath(path)
                c.setFillColor(color)
                c.fillPath()
            }
            guard let cg = shape.cgImage else { return .init(image: shape, rect: rect) }
            let input = CIImage(cgImage: cg)
            let out = input.clampedToExtent().applyingGaussianBlur(sigma: Double(sigma * scale)).cropped(to: input.extent)
            guard let img = ci.createCGImage(out, from: input.extent) else { return .init(image: shape, rect: rect) }
            return .init(image: UIImage(cgImage: img, scale: scale, orientation: .up), rect: rect)
        }

        func gradient(_ colors: [UInt32], _ stops: [CGFloat]) -> CGGradient {
            CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map { P.cg($0) } as CFArray, locations: stops)!
        }

        /// Fills the current clip with a vertical gradient.
        func vgrad(_ c: CGContext, _ y0: CGFloat, _ y1: CGFloat, _ colors: [UInt32], _ stops: [CGFloat]) {
            c.drawLinearGradient(gradient(colors, stops), start: CGPoint(x: 0, y: y0), end: CGPoint(x: 0, y: y1),
                                 options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }

        func grain(_ c: CGContext, alpha: CGFloat) {
            guard let g = PaperCutDioramaSplashArt.grain else { return }
            c.saveGState()
            c.setAlpha(alpha)
            c.setBlendMode(.multiply)
            c.draw(g, in: CGRect(x: 0, y: 0, width: 64, height: 64), byTiling: true)
            c.restoreGState()
        }

        /// Fill a cut-paper shape: its shadow on the layer behind, the paper, grain, a lit top edge.
        func paper(_ c: CGContext, _ path: CGPath, shadow: CGFloat = 0.32, lit: Bool = true, fill: (CGContext) -> Void) {
            let sh = blurred(path, P.cga(24, 16, 30, shadow), sigma: 7)
            sh.image.draw(in: sh.rect.offsetBy(dx: 0, dy: -3))
            c.saveGState()
            c.addPath(path)
            c.clip()
            fill(c)
            grain(c, alpha: 0.1)
            if lit {
                c.addPath(path)
                c.setStrokeColor(P.cga(255, 248, 236, 0.22))
                c.setLineWidth(1.6)
                c.strokePath()
            }
            c.restoreGState()
        }

        func ridge(_ y0: (CGFloat) -> CGFloat, step: CGFloat = 6) -> CGPath {
            let p = CGMutablePath()
            p.move(to: CGPoint(x: -pad, y: L.H + 40))
            var x = -pad
            while x <= L.W + pad {
                p.addLine(to: CGPoint(x: x, y: y0(x)))
                x += step
            }
            p.addLine(to: CGPoint(x: L.W + pad, y: L.H + 40))
            p.closeSubpath()
            return p
        }
    }

    // MARK: The diorama

    /// The six layers, back to front, and the logo's bloom. Thread-safe: run off the main thread.
    static func bake(_ L: Lay, scale: CGFloat) -> (layers: [PaperCutDioramaSplashLayerArt], bloom: PaperCutDioramaSplashLayerArt) {
        let b = Baker(L, scale: scale)
        let W = L.W
        let span = L.winBottom - L.winTop
        func at(_ f: CGFloat) -> CGFloat { L.winTop + span * f }
        var layers: [PaperCutDioramaSplashLayerArt] = []

        // 1. Sky with a paper moon and stars.
        layers.append(b.surface(y: L.winTop - 60) { c in
            c.saveGState()
            c.clip(to: CGRect(x: -pad, y: L.winTop - 60, width: W + pad * 2, height: L.H + 100 - L.winTop))
            b.vgrad(c, L.winTop, L.winBottom, [0x26315A, 0x5B4F86, 0xB07A9C, 0xF0AE88, 0xF7D3A4], [0, 0.32, 0.55, 0.78, 1])
            c.restoreGState()
            let my = at(0.4)
            let moon = CGPath(ellipseIn: CGRect(x: W * 0.67 - 30, y: my - 30, width: 60, height: 60), transform: nil)
            b.paper(c, moon, shadow: 0.25) { c in b.vgrad(c, my - 30, my + 30, [0xFFF4DC, 0xFBE3BC, 0xF4D2A4], [0, 0.6, 1]) }
            for i in 0..<9 {
                let x = 46 + CGFloat(hash(Double(i))) * (W - 92)
                let y = L.winTop + 30 + CGFloat(hash(Double(i + 9))) * span * 0.32
                let r = 2.2 + CGFloat(hash(Double(i + 19))) * 3.2
                let s = CGMutablePath()
                let o = CGPoint(x: x, y: y)
                s.move(to: CGPoint(x: x, y: y - r * 2))
                s.addQuadCurve(to: CGPoint(x: x + r * 2, y: y), control: o)
                s.addQuadCurve(to: CGPoint(x: x, y: y + r * 2), control: o)
                s.addQuadCurve(to: CGPoint(x: x - r * 2, y: y), control: o)
                s.addQuadCurve(to: CGPoint(x: x, y: y - r * 2), control: o)
                s.closeSubpath()
                b.paper(c, s, shadow: 0.2) { c in
                    c.setFillColor(P.cg(0xF7E7C6))
                    c.fill(s.boundingBoxOfPath.insetBy(dx: -2, dy: -2))
                }
            }
        })

        // 2. Far hills.
        layers.append(b.surface(y: at(0.5) - 20) { c in
            let path = b.ridge { x in
                let dx = Double(x - W * 0.3)
                return at(0.6) + 16 * CGFloat(sin(Double(x) / 52 + 1)) + 9 * CGFloat(sin(Double(x) / 23 + 0.4)) - 22 * CGFloat(exp(-dx * dx / 3000))
            }
            b.paper(c, path) { c in b.vgrad(c, at(0.55), at(0.8), [0x9A8BC0, 0x7F74A8, 0x6A6394], [0, 0.5, 1]) }
        })

        // 3. A row of pines on a low ridge.
        layers.append(b.surface(y: at(0.52)) { c in
            func base(_ x: CGFloat) -> CGFloat { at(0.71) + 9 * CGFloat(sin(Double(x) / 41 + 2.2)) }
            let p = CGMutablePath()
            p.move(to: CGPoint(x: -pad, y: L.H + 40))
            var x = -pad
            while x <= W + pad {
                p.addLine(to: CGPoint(x: x, y: base(x)))
                x += 4
            }
            p.addLine(to: CGPoint(x: W + pad, y: L.H + 40))
            p.closeSubpath()
            var i = 0
            while CGFloat(i) * 23 - pad < W + pad {
                let tx = -pad + CGFloat(i) * 23 + CGFloat(hash(Double(i + 3))) * 8
                let h = 34 + CGFloat(hash(Double(i + 31))) * 52 * (tx > W * 0.38 && tx < W * 0.62 ? 0.55 : 1)
                let y = base(tx) + 4
                let w = 9 + h * 0.2
                // Three tiers of a paper pine.
                for k in 0..<3 {
                    let ty = y - h + CGFloat(k) * h * 0.28
                    let tw = w * (0.55 + CGFloat(k) * 0.28)
                    p.move(to: CGPoint(x: tx, y: ty))
                    p.addLine(to: CGPoint(x: tx + tw, y: ty + h * 0.42))
                    p.addLine(to: CGPoint(x: tx - tw, y: ty + h * 0.42))
                    p.closeSubpath()
                }
                p.addRect(CGRect(x: tx - 1.5, y: y - 6, width: 3, height: 8))
                i += 1
            }
            // Overlapping tiers: no lit edge, or every tier's outline would show.
            b.paper(c, p, shadow: 0.32, lit: false) { c in b.vgrad(c, at(0.55), at(0.8), [0x3F6470, 0x34545F, 0x2B4652], [0, 0.5, 1]) }
        })

        // 4. Meadow with a winding river.
        let meadowTop = at(0.735)
        layers.append(b.surface(y: meadowTop - 20) { c in
            func edge(_ x: CGFloat) -> CGFloat { meadowTop + 7 * CGFloat(sin(Double(x) / 60 + 2)) + 4 * CGFloat(sin(Double(x) / 19)) }
            b.paper(c, b.ridge(edge)) { c in b.vgrad(c, meadowTop, L.winBottom + 60, [0x7E9C68, 0x678A57, 0x4F7347], [0, 0.5, 1]) }
            let vx = W * 0.44
            let vy = edge(vx) + 2
            let yb = L.H + 40
            let river = CGMutablePath()
            river.move(to: CGPoint(x: vx - 3, y: vy))
            river.addCurve(to: CGPoint(x: W * 0.5, y: vy + 70), control1: CGPoint(x: vx - 26, y: vy + 20), control2: CGPoint(x: W * 0.7, y: vy + 40))
            river.addCurve(to: CGPoint(x: W * 0.08, y: yb), control1: CGPoint(x: W * 0.3, y: vy + 100), control2: CGPoint(x: W * 0.18, y: vy + 120))
            river.addLine(to: CGPoint(x: W * 0.62, y: yb))
            river.addCurve(to: CGPoint(x: W * 0.74, y: vy + 66), control1: CGPoint(x: W * 0.5, y: vy + 130), control2: CGPoint(x: W * 0.6, y: vy + 100))
            river.addCurve(to: CGPoint(x: vx + 3, y: vy), control1: CGPoint(x: W * 0.86, y: vy + 38), control2: CGPoint(x: vx + 26, y: vy + 18))
            river.closeSubpath()
            b.paper(c, river, shadow: 0.22) { c in b.vgrad(c, vy, L.winBottom, [0xB4D5D9, 0x86B7C6, 0x5F94AB], [0, 0.4, 1]) }
            c.saveGState()
            c.addPath(river)
            c.clip()
            c.setStrokeColor(P.cga(255, 250, 238, 0.55))
            c.setLineWidth(1.3)
            for i in 0..<7 {
                let y = vy + 18 + CGFloat(i) * 16
                let x = W * (0.48 + 0.12 * CGFloat(sin(Double(i) * 1.7)) - CGFloat(i) * 0.03)
                c.move(to: CGPoint(x: x - 14, y: y))
                c.addLine(to: CGPoint(x: x + 14, y: y + 1))
            }
            c.strokePath()
            c.restoreGState()
        })

        // 5. Grass edge with blades.
        let grassTop = at(0.925)
        layers.append(b.surface(y: grassTop - 40) { c in
            let p = CGMutablePath()
            p.move(to: CGPoint(x: -pad, y: L.H + 40))
            var x = -pad
            var i = 0
            while x <= W + pad {
                let g = grassTop + 10 * CGFloat(sin(Double(x) / 70 + 0.6))
                let h = 8 + CGFloat(hash(Double(i + 7))) * 20
                let lean = (CGFloat(hash(Double(i + 77))) - 0.5) * 7
                p.addLine(to: CGPoint(x: x, y: g))
                p.addLine(to: CGPoint(x: x + 4.5 + lean, y: g - h))
                p.addLine(to: CGPoint(x: x + 9, y: g))
                x += 9
                i += 1
            }
            p.addLine(to: CGPoint(x: W + pad, y: L.H + 40))
            p.closeSubpath()
            b.paper(c, p, shadow: 0.38) { c in b.vgrad(c, grassTop - 20, L.H, [0x3E6545, 0x2F5137, 0x22402B], [0, 0.3, 1]) }
        })

        // 6. The front frame: cream paper with the arched window, the logo door and the wordmark cut out.
        let window = windowPath(L)
        let door = doorPath(L)
        let word = wordPath(L)
        let innerShadow: PaperCutDioramaSplashLayerArt = {
            let stroke = window.copy(strokingWithWidth: 14, lineCap: .butt, lineJoin: .miter, miterLimit: 10)
            return b.blurred(stroke, P.cga(30, 18, 24, 0.42), sigma: 9)
        }()
        layers.append(b.surface(y: 0) { c in
            let full = CGRect(x: -pad, y: 0, width: W + pad * 2, height: L.H + 40)
            c.saveGState()
            c.clip(to: full)
            b.vgrad(c, 0, L.H, [0xF6EEDF, 0xF1E6D3, 0xE8DAC2], [0, 0.6, 1])
            b.grain(c, alpha: 0.09)
            c.restoreGState()
            c.saveGState()
            c.setBlendMode(.clear)
            c.addPath(window); c.fillPath()
            c.addPath(door); c.fillPath()
            c.addPath(word); c.fillPath()
            c.restoreGState()
            // The frame's own shadow falling inwards on the scene, and a lit lip on the cut edge.
            c.saveGState()
            c.addPath(window)
            c.clip()
            innerShadow.image.draw(in: innerShadow.rect.offsetBy(dx: 0, dy: 4))
            c.restoreGState()
            c.setStrokeColor(P.cga(255, 252, 244, 0.85))
            c.setLineWidth(1.2)
            c.addPath(window); c.strokePath()
            c.addPath(door); c.strokePath()
            // The tagline, tracked 2.2 pt per letter.
            let tag = UIFont.systemFont(ofSize: 13, weight: .semibold)
            let attrs: [NSAttributedString.Key: Any] = [.font: tag, .foregroundColor: UIColor(cgColor: P.cga(120, 96, 70, 0.8))]
            let tagline = "STORIES FOR SMALL HOURS"
            let widths = tagline.map { (String($0) as NSString).size(withAttributes: [.font: tag]).width }
            var tx = W / 2 - (widths.reduce(0, +) + CGFloat(tagline.count) * 2.2) / 2
            for (k, ch) in tagline.enumerated() {
                (String(ch) as NSString).draw(at: CGPoint(x: tx, y: L.tagY - tag.ascender), withAttributes: attrs)
                tx += widths[k] + 2.2
            }
        })

        // The light through the cut-outs, blurred, to be added over the frame.
        let glow = CGMutablePath()
        glow.addPath(door)
        glow.addPath(word)
        let bloom = b.blurred(glow, P.cga(255, 170, 90, 0.85), sigma: 12)
        return (layers, bloom)
    }
}

/// Blank paper: the backing board the layers rise onto (static, drawn once).
struct PaperCutDioramaSplashBacking: View, Equatable {
    var L: PaperCutDioramaSplashLayout
    typealias C = PaperCutDioramaSplashPalette

    var body: some View {
        Canvas { ctx, _ in
            let full = Path(CGRect(x: 0, y: 0, width: L.W, height: L.H))
            ctx.fill(full, with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0xF3EBDD), location: 0), .init(color: C.rgb(0xEDE3D2), location: 0.6), .init(color: C.rgb(0xE4D7C2), location: 1)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: L.H)))
            if let grain = PaperCutDioramaSplashArt.grain {
                ctx.drawLayer { l in
                    l.opacity = 0.1
                    l.blendMode = .multiply
                    l.fill(full, with: .tiledImage(Image(decorative: grain, scale: 2)))
                }
            }
            ctx.fill(full, with: .radialGradient(
                Gradient(stops: [.init(color: C.rgba(80, 50, 20, 0), location: 0.5), .init(color: C.rgba(80, 50, 20, 0.12), location: 1)]),
                center: CGPoint(x: L.W / 2, y: L.H * 0.45), startRadius: 0, endRadius: L.H * 0.7))
        }
        .frame(width: L.W, height: L.H)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
