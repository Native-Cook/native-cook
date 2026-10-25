import SwiftUI
import UIKit

/*
 Film Negative Card — layout and the static art (the desk, the light table, the photo crops of
 each frame and the rest of the roll). These views take only the layout, so SwiftUI draws them
 once; the timeline never redraws them.

 Layout (pt), from the screen size and safe area: a header block, then the strip resting on the
 desk, the light table under it, and a footer row. The strip is 35 mm film: frame height = strip
 height × 24 / 35, frame width = 1.5 × frame height, perforation pitch = frame width / 7.58
 (4.75 mm on a 36 mm frame), holes 2.8 × 2 mm. On 393 × 852 the strip is 361 × 250.
 */

enum FilmNegativeCardPalette {
    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static let bg = rgb(0x16110F)
    static let ink = rgb(0xF4EADB)
    static let mute = rgb(0xF4EADB, 0.58)
    static let accent = rgb(0xF2A65A)
    static let edgeDark = Color(.sRGB, red: 58 / 255, green: 24 / 255, blue: 8 / 255, opacity: 0.8)
    static let edgeLit = Color(.sRGB, red: 1, green: 226 / 255, blue: 182 / 255, opacity: 0.82)
}

struct FilmNegativeCardLayout: Equatable {
    static let headerH: CGFloat = 112
    static let footerH: CGFloat = 56
    static let side: CGFloat = 16
    static let tablePad: CGFloat = 18
    static let stripMaxH: CGFloat = 250

    var W: CGFloat, H: CGFloat, top: CGFloat
    var stripX: CGFloat, stripW: CGFloat, stripH: CGFloat
    var restY: CGFloat, onY: CGFloat
    var table: CGRect, panel: CGRect, frame: CGRect
    var gap: CGFloat
    var pitch: CGFloat, holeW: CGFloat, holeH: CGFloat, phase: CGFloat, holeTop: CGFloat, holeBottom: CGFloat
    var footerY: CGFloat

    init(W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat) {
        let a = top + 12 + Self.headerH
        footerY = H - bottom - 16 - Self.footerH
        let b = footerY - 16
        stripW = W - Self.side * 2
        stripH = max(40, min(Self.stripMaxH, stripW * 0.7, (b - a - Self.tablePad * 2 - 22) / 2))
        let tableH = stripH + Self.tablePad * 2
        let tableY = b - tableH
        restY = a + max(0, (tableY - 14 - a - stripH) * 0.5)
        let fh = stripH * 24 / 35
        let fw = fh * 1.5
        let mm = fw / 36
        gap = 2 * mm
        let fx = (stripW - fw) / 2
        let fy = (stripH - fh) / 2
        pitch = 4.75 * mm
        holeH = 2 * mm
        holeW = 2.8 * mm
        phase = fx + pitch * 0.5
        holeTop = fy * 0.36 + holeH * 0.5 + 2
        holeBottom = stripH - (fy * 0.36 + holeH * 0.5 + 2)
        self.W = W
        self.H = H
        self.top = top
        stripX = Self.side
        onY = tableY + Self.tablePad
        table = CGRect(x: 10, y: tableY, width: W - 20, height: tableH)
        panel = CGRect(x: 20, y: tableY + 10, width: W - 40, height: tableH - 20)
        frame = CGRect(x: fx, y: fy, width: fw, height: fh)
    }

    /// Where the strip rests: partly over the top of the light table.
    var homeY: CGFloat { restY + CGFloat(FilmNegativeCardMotion.restOn) * (onY - restY) }
}

enum FilmNegativeCardAssets {
    static let photo: UIImage = {
        guard let url = Bundle.main.url(forResource: "FilmNegativeCard-portrait", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }()

    /// Photo size in pixels (the crops are written in pixels, as in the React Native shader).
    static var photoPixels: CGSize {
        let s = photo.size, k = photo.scale
        return s.width > 0 ? CGSize(width: s.width * k, height: s.height * k) : CGSize(width: 900, height: 600)
    }

    /// A 128 px tile of seeded grain, tiled over the desk (instead of noise per pixel).
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
        let data = Data(bytes) as CFData
        guard let provider = CGDataProvider(data: data) else { return nil }
        return CGImage(width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: n * 4,
                       space: CGColorSpaceCreateDeviceRGB(),
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                       provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
    }()
}

/// The desk (warm near-black, safelight glow, grain, vignette) and the light table.
struct FilmNegativeCardDesk: View, Equatable {
    var L: FilmNegativeCardLayout
    typealias C = FilmNegativeCardPalette

    var body: some View {
        Canvas { ctx, _ in
            let W = L.W, H = L.H, T = L.table, P = L.panel
            let full = Path(CGRect(x: 0, y: 0, width: W, height: H))
            ctx.fill(full, with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0x1C1512), location: 0), .init(color: C.rgb(0x16110F), location: 0.55), .init(color: C.rgb(0x110D0C), location: 1)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: H)))
            ctx.fill(full, with: .radialGradient(
                Gradient(stops: [.init(color: Color(.sRGB, red: 196 / 255, green: 74 / 255, blue: 42 / 255, opacity: 0.22), location: 0),
                                 .init(color: Color(.sRGB, red: 150 / 255, green: 52 / 255, blue: 32 / 255, opacity: 0.08), location: 0.45),
                                 .init(color: .clear, location: 1)]),
                center: CGPoint(x: W * 0.92, y: -20), startRadius: 0, endRadius: W * 0.95))

            // Light spilling out of the table onto the desk.
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 30))
                l.fill(Path(roundedRect: CGRect(x: T.minX - 6, y: T.minY - 4, width: T.width + 12, height: T.height + 14), cornerRadius: 22),
                       with: .color(Color(.sRGB, red: 1, green: 214 / 255, blue: 150 / 255, opacity: 0.24)))
            }
            // The table: two shadows, an aluminium bezel with a lit top edge.
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 16))
                l.fill(Path(roundedRect: T.offsetBy(dx: 0, dy: 14), cornerRadius: 18), with: .color(.black.opacity(0.45)))
            }
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 2))
                l.fill(Path(roundedRect: T.offsetBy(dx: 0, dy: 1.5), cornerRadius: 18), with: .color(.black.opacity(0.6)))
            }
            ctx.fill(Path(roundedRect: T, cornerRadius: 18), with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0x4A4541), location: 0), .init(color: C.rgb(0x2B2725), location: 0.5), .init(color: C.rgb(0x38332F), location: 1)]),
                startPoint: CGPoint(x: 0, y: T.minY), endPoint: CGPoint(x: 0, y: T.maxY)))
            ctx.stroke(Path(roundedRect: T.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 17.5), with: .linearGradient(
                Gradient(stops: [.init(color: Color(.sRGB, red: 1, green: 244 / 255, blue: 228 / 255, opacity: 0.30), location: 0),
                                 .init(color: Color(.sRGB, red: 1, green: 244 / 255, blue: 228 / 255, opacity: 0.04), location: 0.3),
                                 .init(color: .black.opacity(0.35), location: 1)]),
                startPoint: CGPoint(x: 0, y: T.minY), endPoint: CGPoint(x: 0, y: T.maxY)), lineWidth: 1)

            // The panel: opal glass lit from below, warm in the middle, cooler at the edges.
            let panel = Path(roundedRect: P, cornerRadius: 9)
            ctx.fill(panel, with: .radialGradient(
                Gradient(stops: [.init(color: C.rgb(0xFFFBF2), location: 0), .init(color: C.rgb(0xFBF1DE), location: 0.6), .init(color: C.rgb(0xEADCC2), location: 1)]),
                center: CGPoint(x: P.midX, y: P.midY), startRadius: 0, endRadius: max(P.width, P.height) * 0.62))
            ctx.drawLayer { l in
                l.clip(to: panel)
                var grid = Path()
                var x = P.minX + 12
                while x < P.maxX { grid.move(to: CGPoint(x: x, y: P.minY)); grid.addLine(to: CGPoint(x: x, y: P.maxY)); x += 20 }
                var y = P.minY + 10
                while y < P.maxY { grid.move(to: CGPoint(x: P.minX, y: y)); grid.addLine(to: CGPoint(x: P.maxX, y: y)); y += 20 }
                l.stroke(grid, with: .color(Color(.sRGB, red: 150 / 255, green: 120 / 255, blue: 80 / 255, opacity: 0.13)), lineWidth: 0.6)
                l.drawLayer { s in
                    s.addFilter(.blur(radius: 4))
                    s.stroke(panel, with: .color(Color(.sRGB, red: 120 / 255, green: 90 / 255, blue: 50 / 255, opacity: 0.16)), lineWidth: 6)
                }
            }

            // Grain over everything, then a vignette.
            if let grain = FilmNegativeCardAssets.grain {
                ctx.drawLayer { l in
                    l.opacity = 0.05
                    l.blendMode = .overlay
                    l.fill(full, with: .tiledImage(Image(decorative: grain, scale: 2)))
                }
            }
            ctx.fill(full, with: .radialGradient(
                Gradient(stops: [.init(color: .clear, location: 0), .init(color: .black.opacity(0.12), location: 0.6), .init(color: .black.opacity(0.5), location: 1)]),
                center: CGPoint(x: W / 2, y: H * 0.46), startRadius: 0, endRadius: H * 0.72))
        }
        .frame(width: L.W, height: L.H)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The strip-sized layer the shader reads: each frame's crop of the photo drawn in its frame
/// rect. `roll` draws the crops of frames 3 – 5 (the rest of the roll on the desk).
struct FilmNegativeCardScene: View, Equatable {
    var L: FilmNegativeCardLayout
    var roll: Bool

    var body: some View {
        Canvas { ctx, _ in
            let img = Image(uiImage: FilmNegativeCardAssets.photo)
            let px = FilmNegativeCardAssets.photoPixels
            let F = L.frame
            let pitchF = F.width + L.gap
            for i in -1...1 {
                let o = CGRect(x: F.minX + CGFloat(i) * pitchF, y: F.minY, width: F.width, height: F.height)
                var k: CGFloat = 1
                var off = CGPoint.zero
                if roll {
                    k = 0.62
                    off = CGPoint(x: 60 + CGFloat(i + 1) * 110, y: 40 + CGFloat(i + 1) * 30)
                } else if i == -1 {
                    k = 0.78; off = CGPoint(x: 150, y: 90)
                } else if i == 1 {
                    k = 0.82; off = CGPoint(x: 10, y: 70)
                }
                // The frame shows the photo's pixels [off, off + size × k].
                let sx = o.width / (px.width * k), sy = o.height / (px.height * k)
                var c = ctx
                c.clip(to: Path(o))
                c.draw(img, in: CGRect(x: o.minX - off.x * sx, y: o.minY - off.y * sy, width: px.width * sx, height: px.height * sy))
            }
        }
        .frame(width: L.stripW, height: L.stripH)
    }
}

/// The negative strip: the scene layer through the film shader.
struct FilmNegativeCardStripView: View {
    var L: FilmNegativeCardLayout
    var roll: Bool
    /// Lit panel and loupe in the strip's own space; sheen = the strip's screen y.
    var light: CGRect
    var loupe: (x: CGFloat, y: CGFloat, r: CGFloat, a: CGFloat)
    var sheen: CGFloat

    var body: some View {
        FilmNegativeCardScene(L: L, roll: roll)
            .equatable()
            .layerEffect(
                ShaderLibrary.default.FilmNegativeCardStrip(
                    .float4(0, 0, L.stripW, L.stripH),
                    .float4(L.frame.minX, L.frame.minY, L.frame.width, L.frame.height),
                    .float(L.gap),
                    .float4(light.minX, light.minY, light.width, light.height),
                    .float4(loupe.x, loupe.y, loupe.r, loupe.a),
                    .float4(L.pitch, L.holeW, L.holeH, L.phase),
                    .float2(L.holeTop, L.holeBottom),
                    .float2(sheen, roll ? 1 : 0)
                ),
                maxSampleOffset: .zero
            )
    }
}

/// The edge print: "LUMA 400", frame numbers and their "A" halves along both margins.
enum FilmNegativeCardEdge {
    struct Label { var x: CGFloat; var y: CGFloat; var text: String }

    static func labels(_ L: FilmNegativeCardLayout, roll: Bool) -> [Label] {
        var out: [Label] = []
        let yTop = L.holeTop - L.holeH / 2 - 3.5
        let yBot = L.holeBottom + L.holeH / 2 + 9
        let F = L.frame
        for i in -1...1 {
            let x0 = F.minX + CGFloat(i) * (F.width + L.gap)
            let n = 7 + i - (roll ? 4 : 0)
            out.append(Label(x: x0 + 6, y: yTop, text: "LUMA 400"))
            out.append(Label(x: x0 + F.width * 0.64, y: yTop, text: "\(n)"))
            out.append(Label(x: x0 + F.width * 0.12, y: yBot, text: "\(n)"))
            out.append(Label(x: x0 + F.width * 0.58, y: yBot, text: "\(n)A"))
        }
        return out
    }

    /// Draws the labels with their baseline at y (Skia's text origin).
    static func draw(_ ctx: GraphicsContext, _ labels: [Label], color: Color) {
        let font = UIFont.systemFont(ofSize: 7.5, weight: .bold)
        for l in labels {
            let t = ctx.resolve(Text(l.text).font(.system(size: 7.5, weight: .bold)).foregroundColor(color))
            ctx.draw(t, at: CGPoint(x: l.x, y: l.y - font.descender), anchor: .bottomLeading)
        }
    }
}

/// The rest of the roll, lying dimmed on the desk behind the strip: same shader, no light.
struct FilmNegativeCardRoll: View, Equatable {
    var L: FilmNegativeCardLayout

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.black.opacity(0.5))
                .frame(width: L.stripW - 8, height: L.stripH)
                .blur(radius: 10)
                .offset(x: 4, y: 10)
            FilmNegativeCardStripView(L: L, roll: true, light: CGRect(x: -9999, y: -9999, width: 1, height: 1),
                                      loupe: (0, 0, 1, 0), sheen: L.restY - 80)
            Canvas { ctx, _ in
                FilmNegativeCardEdge.draw(ctx, FilmNegativeCardEdge.labels(L, roll: true), color: FilmNegativeCardPalette.edgeDark)
            }
            .frame(width: L.stripW, height: L.stripH)
        }
        .frame(width: L.stripW, height: L.stripH, alignment: .topLeading)
        .compositingGroup()
        .opacity(0.62)
        .transformEffect(CGAffineTransform(translationX: L.stripX + 6, y: L.restY - 10).rotated(by: -0.022).scaledBy(x: 0.95, y: 0.95))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
