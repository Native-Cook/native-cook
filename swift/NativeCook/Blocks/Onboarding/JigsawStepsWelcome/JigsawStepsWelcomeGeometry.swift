import CoreImage
import SwiftUI
import UIKit

/*
 Jigsaw Steps Welcome — layout, the twelve piece outlines, where each piece lies when scattered,
 and the static art: the paper background with the board's recessed tray (a static view), and
 per piece a set of sprites baked once off the main thread (the photo cut to the piece with its
 bevel, and its shadows at two softnesses), so a frame only draws images with transforms.

 The board is 4 × 3 cells over a 4:3 photo. Every inner edge carries one classic knob whose side
 is chosen by a seeded hash; the knob is four cubic Béziers over the edge (neck at 39 – 61 % of
 the edge, a round head 27 % of the edge tall). Paths are built in screen space at the piece's
 home, so drawing a piece elsewhere is only a transform.
 */

enum JigsawStepsWelcomePalette {
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
    static let bg = rgb(0xF2E9DC)
    static let ink = rgb(0x2A221C)
    static let mute = rgb(0x2A221C, 0.6)
    static let accent = rgb(0xD4643A)
}

struct JigsawStepsWelcomeLayout: Equatable {
    static let cols = 4
    static let rows = 3
    static let count = 12
    static let topBar: CGFloat = 44
    static let textH: CGFloat = 104
    static let ctaH: CGFloat = 52
    /// Which pieces each onboarding step snaps in: corners, then the outer middles, then the middle row.
    static let steps: [[Int]] = [[0, 3, 8, 11], [1, 2, 9, 10], [4, 5, 6, 7]]

    var W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat
    var board: CGRect
    var cellW: CGFloat, cellH: CGFloat
    /// Home centres of the cells, and the scattered rest pose of each piece (x, y, rotation rad).
    var home: [CGPoint]
    var scatter: [CGPoint]
    var scatterRot: [Double]
    var textY: CGFloat
    var ctaY: CGFloat

    static func hash(_ n: Double) -> Double {
        let v = sin(n * 12.9898 + 4.1414) * 43758.5453
        return v - floor(v)
    }

    init(W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat) {
        let bx: CGFloat = 20
        let bw = W - 40
        let bh = bw * 0.75
        let by = top + 8 + Self.topBar + 12
        ctaY = H - bottom - 16 - Self.ctaH
        textY = ctaY - 20 - Self.textH
        cellW = bw / CGFloat(Self.cols)
        cellH = bh / CGFloat(Self.rows)
        var home: [CGPoint] = []
        for r in 0..<Self.rows {
            for c in 0..<Self.cols {
                home.append(CGPoint(x: bx + (CGFloat(c) + 0.5) * cellW, y: by + (CGFloat(r) + 0.5) * cellH))
            }
        }
        // The pile: a loose 4 × 3 grid under the board, shuffled so no piece waits under its own column.
        let zx0 = bx + cellW * 0.42
        let zx1 = bx + bw - cellW * 0.42
        let zy0 = by + bh + cellH * 0.18
        let zy1 = max(zy0 + cellH * 0.6, textY - cellH * 0.42)
        let spot = [7, 2, 10, 0, 5, 11, 3, 8, 1, 9, 4, 6]
        var scatter: [CGPoint] = []
        var rots: [Double] = []
        for i in 0..<Self.count {
            let s = spot[i]
            let gc = CGFloat(s % 4), gr = CGFloat(s / 4)
            let x = zx0 + (zx1 - zx0) * gc / 3 + CGFloat(Self.hash(Double(i + 1)) - 0.5) * 18
            let y = zy0 + (zy1 - zy0) * gr / 2 + CGFloat(Self.hash(Double(i + 21)) - 0.5) * 14
            scatter.append(CGPoint(x: x, y: y))
            rots.append((Self.hash(Double(i + 41)) - 0.5) * 0.9)
        }
        self.W = W
        self.H = H
        self.top = top
        self.bottom = bottom
        board = CGRect(x: bx, y: by, width: bw, height: bh)
        self.home = home
        self.scatter = scatter
        scatterRot = rots
    }
}

enum JigsawStepsWelcomeArt {
    typealias Lay = JigsawStepsWelcomeLayout

    static let photo: UIImage = {
        guard let url = Bundle.main.url(forResource: "JigsawStepsWelcome-beach", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }()

    // The knob along a unit edge: x along it, y outwards. Four cubics after the start point.
    private static let knob: [[CGFloat]] = [
        [0.3, 0.0, 0.42, -0.04, 0.39, 0.08],
        [0.36, 0.2, 0.41, 0.27, 0.5, 0.27],
        [0.59, 0.27, 0.64, 0.2, 0.61, 0.08],
        [0.58, -0.04, 0.7, 0.0, 1.0, 0.0],
    ]

    /// One edge from a to b; sign +1 knob out of the piece, −1 into it, 0 straight. `back` is true
    /// for the bottom and left edges, which neighbours walk the other way: the knob's sideways
    /// shift flips so both pieces share exactly the same curve.
    private static func edge(_ p: inout Path, _ a: CGPoint, _ b: CGPoint, _ sign: CGFloat, _ jitter: Double, _ back: Bool) {
        if sign == 0 {
            p.addLine(to: b)
            return
        }
        let dx = b.x - a.x, dy = b.y - a.y
        // Outward normal for a clockwise outline (screen y down).
        let nx = dy, ny = -dx
        let shift = CGFloat(jitter - 0.5) * 0.08 * (back ? -1 : 1)
        func P(_ u: CGFloat, _ v: CGFloat) -> CGPoint {
            let uu = u + (u > 0.05 && u < 0.95 ? shift : 0)
            return CGPoint(x: a.x + dx * uu + nx * v * sign, y: a.y + dy * uu + ny * v * sign)
        }
        for k in knob {
            p.addCurve(to: P(k[4], k[5]), control1: P(k[0], k[1]), control2: P(k[2], k[3]))
        }
    }

    /// The twelve outlines at their homes.
    static func paths(_ L: Lay) -> [Path] {
        let B = L.board
        var hE: [[CGFloat]] = [], vE: [[CGFloat]] = []
        for r in 0..<Lay.rows {
            hE.append([]); vE.append([])
            for c in 0..<Lay.cols {
                hE[r].append(Lay.hash(Double(r * 7 + c * 3 + 1)) > 0.5 ? 1 : -1)
                vE[r].append(Lay.hash(Double(r * 5 + c * 11 + 2)) > 0.5 ? 1 : -1)
            }
        }
        var out: [Path] = []
        for r in 0..<Lay.rows {
            for c in 0..<Lay.cols {
                let x0 = B.minX + CGFloat(c) * L.cellW, y0 = B.minY + CGFloat(r) * L.cellH
                let x1 = x0 + L.cellW, y1 = y0 + L.cellH
                let top: CGFloat = r == 0 ? 0 : -hE[r - 1][c]
                let right: CGFloat = c == Lay.cols - 1 ? 0 : vE[r][c]
                let bottom: CGFloat = r == Lay.rows - 1 ? 0 : hE[r][c]
                let left: CGFloat = c == 0 ? 0 : -vE[r][c - 1]
                var p = Path()
                p.move(to: CGPoint(x: x0, y: y0))
                edge(&p, CGPoint(x: x0, y: y0), CGPoint(x: x1, y: y0), top, Lay.hash(Double(r * 13 + c)), false)
                edge(&p, CGPoint(x: x1, y: y0), CGPoint(x: x1, y: y1), right, Lay.hash(Double(r * 17 + c + 5)), false)
                edge(&p, CGPoint(x: x1, y: y1), CGPoint(x: x0, y: y1), bottom, Lay.hash(Double(r * 13 + c + 13)), true)
                edge(&p, CGPoint(x: x0, y: y1), CGPoint(x: x0, y: y0), left, Lay.hash(Double(r * 17 + c + 4)), true)
                p.closeSubpath()
                out.append(p)
            }
        }
        return out
    }

    /// One piece's sprites, each drawn into `rect` (screen space at the piece's home).
    struct Sprites {
        var rect: CGRect
        /// The photo cut to the piece, with the bevel light inside its edge.
        var art: CGImage
        /// Ambient shadow at blur 3 (resting) and 12 (lifted); contact shadow at blur 1.4.
        var soft: CGImage
        var softer: CGImage
        var contact: CGImage
    }

    /// Bakes the twelve pieces' sprites. Thread-safe: run off the main thread.
    static func bake(_ L: Lay, scale: CGFloat) -> [Sprites] {
        let outlines = paths(L)
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let ci = CIContext()
        return outlines.map { path in
            let rect = path.boundingRect.insetBy(dx: -40, dy: -40).integral
            let cgPath = path.cgPath
            let renderer = UIGraphicsImageRenderer(size: rect.size, format: format)
            func draw(_ body: (CGContext) -> Void) -> CGImage {
                renderer.image { rc in
                    let c = rc.cgContext
                    c.translateBy(x: -rect.minX, y: -rect.minY)
                    body(c)
                }.cgImage!
            }
            func shadow(_ sigma: CGFloat, _ color: CGColor) -> CGImage {
                // The silhouette, blurred with Core Image (σ in pixels = σ in pt × scale).
                let shape = draw { c in
                    c.addPath(cgPath)
                    c.setFillColor(color)
                    c.fillPath()
                }
                let input = CIImage(cgImage: shape)
                let out = input.clampedToExtent().applyingGaussianBlur(sigma: Double(sigma * scale)).cropped(to: input.extent)
                return ci.createCGImage(out, from: input.extent) ?? shape
            }
            let art = draw { c in
                c.addPath(cgPath)
                c.clip()
                photo.draw(in: L.board)
                c.addPath(cgPath)
                c.setStrokeColor(CGColor(srgbRed: 1, green: 250 / 255, blue: 240 / 255, alpha: 0.32))
                c.setLineWidth(2.6)
                c.strokePath()
            }
            return Sprites(rect: rect, art: art,
                           soft: shadow(3, JigsawStepsWelcomePalette.cg(0x3B2614)),
                           softer: shadow(12, JigsawStepsWelcomePalette.cg(0x3B2614)),
                           contact: shadow(1.4, JigsawStepsWelcomePalette.cg(0x2A1A0E)))
        }
    }

    /// A 128 px tile of seeded grain.
    static let grain: CGImage? = {
        let n = 128
        var bytes = [UInt8](repeating: 255, count: n * n * 4)
        for y in 0..<n {
            for x in 0..<n {
                let h = sin(Double(x) * 12.9898 + Double(y) * 78.233) * 43758.5453
                let v = UInt8(max(0, min(255, 128 + (h - floor(h)) * 127)))
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
}

/// Cream paper, grain and the board's recessed tray with the faint cut lines printed in it.
struct JigsawStepsWelcomePaper: View, Equatable {
    var L: JigsawStepsWelcomeLayout
    typealias C = JigsawStepsWelcomePalette

    var body: some View {
        Canvas { ctx, _ in
            let W = L.W, H = L.H, B = L.board
            let full = Path(CGRect(x: 0, y: 0, width: W, height: H))
            ctx.fill(full, with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0xF7F1E7), location: 0), .init(color: C.rgb(0xF1E8DA), location: 0.5), .init(color: C.rgb(0xE9DDCB), location: 1)]),
                startPoint: .zero, endPoint: CGPoint(x: W * 0.4, y: H)))
            ctx.fill(full, with: .radialGradient(
                Gradient(colors: [C.rgba(255, 214, 170, 0.35), C.rgba(255, 214, 170, 0)]),
                center: CGPoint(x: W * 0.15, y: H * 0.05), startRadius: 0, endRadius: W * 1.1))
            let trayRect = B.insetBy(dx: -6, dy: -6)
            let tray = Path(roundedRect: trayRect, cornerRadius: 16)
            ctx.fill(tray, with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0xE2D5C1), location: 0), .init(color: C.rgb(0xDCCDB6), location: 0.5), .init(color: C.rgb(0xD6C6AD), location: 1)]),
                startPoint: CGPoint(x: 0, y: B.minY), endPoint: CGPoint(x: 0, y: B.maxY)))
            // Recess: a dark inner edge at the top, a light lip at the bottom.
            ctx.drawLayer { l in
                l.clip(to: tray)
                l.drawLayer { s in
                    s.addFilter(.blur(radius: 6))
                    s.stroke(Path(roundedRect: CGRect(x: B.minX - 6, y: B.minY - 9, width: B.width + 12, height: B.height + 12), cornerRadius: 16),
                             with: .color(C.rgba(90, 64, 40, 0.22)), lineWidth: 10)
                }
                var cuts = Path()
                for p in JigsawStepsWelcomeArt.paths(L) { cuts.addPath(p) }
                l.stroke(cuts, with: .color(C.rgba(110, 82, 56, 0.24)), lineWidth: 0.8)
            }
            ctx.stroke(Path(roundedRect: CGRect(x: B.minX - 6.5, y: B.minY - 5.5, width: B.width + 13, height: B.height + 12.5), cornerRadius: 16.5),
                       with: .color(C.rgba(255, 252, 246, 0.9)), lineWidth: 1)
            if let grain = JigsawStepsWelcomeArt.grain {
                ctx.drawLayer { l in
                    l.opacity = 0.07
                    l.blendMode = .multiply
                    l.fill(full, with: .tiledImage(Image(decorative: grain, scale: 2)))
                }
            }
        }
        .frame(width: L.W, height: L.H)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
