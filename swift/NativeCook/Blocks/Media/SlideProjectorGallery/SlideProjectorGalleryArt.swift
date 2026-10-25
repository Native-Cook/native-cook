import CoreImage
import SwiftUI
import UIKit

/*
 Slide Projector Gallery — layout, the slides, and the art drawn once: each slide at three focus
 levels (sharp, σ 4 and σ 12 px on a 600 px wide tile, blurred off the main thread with Core
 Image), and the projector (a walnut-and-cream body, the lens barrel, the round carousel tray's
 walls) as a static view over the wall. The tray's slide mounts turn, so they are rebuilt as
 two paths per frame (`mounts`).
 */

enum SlideProjectorGalleryPalette {
    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }
    static let bg = rgb(0x1A1411)
    static let ink = rgb(0xF3E8DA)
    static let mute = rgb(0xF3E8DA, 0.56)
    static let accent = rgb(0xF0B25E)
}

struct SlideProjectorGalleryLayout: Equatable {
    static let headerH: CGFloat = 92
    static let keyTop: CGFloat = 1.045
    static let keyBottom: CGFloat = 0.955

    var W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat
    /// The projected picture: centre x, top, width, height.
    var projCX: CGFloat, projTop: CGFloat, projW: CGFloat, projH: CGFloat
    var captionY: CGFloat
    var lensX: CGFloat, lensY: CGFloat
    var trayCX: CGFloat, trayCY: CGFloat, rx: CGFloat, ry: CGFloat, side: CGFloat
    var bodyTop: CGFloat

    init(W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat) {
        projTop = top + 12 + Self.headerH
        projW = W - 40
        projH = projW / 1.5
        rx = min(W * 0.42, 170)
        ry = rx * 0.34
        trayCY = H - bottom - ry - 52
        bodyTop = trayCY - ry - 40
        self.W = W
        self.H = H
        self.top = top
        self.bottom = bottom
        projCX = W / 2
        captionY = projTop + projH + 16
        lensX = W / 2
        lensY = bodyTop - 20
        trayCX = W / 2
        side = 18
    }

    /// The layer's focus tiles: the full width, at the photos' 3 : 2.
    var tile: CGSize { CGSize(width: W, height: W / 1.5) }
}

enum SlideProjectorGalleryArt {
    struct Slide { var title: String; var meta: String; var photo: String }

    static let slides: [Slide] = [
        .init(title: "Aunt Ivy at Crescent Point", meta: "July 1987 · Kodachrome-style transparency", photo: "seaside"),
        .init(title: "Under the striped umbrellas", meta: "Pine Cove · August 1987", photo: "umbrellas"),
        .init(title: "Lake day, watermelon for lunch", meta: "Lake Wren · July 1987", photo: "picnic"),
        .init(title: "The lifeguard's chair", meta: "Long Strand · August 1987", photo: "lifeguard"),
        .init(title: "Dad's blue coupe", meta: "Driveway, Elm Street · June 1987", photo: "chrome"),
    ]

    /// Each slide at three focus levels. Thread-safe: run off the main thread.
    static func bakeTiles() -> [[UIImage]] {
        let ci = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
        return slides.map { s in
            guard let url = Bundle.main.url(forResource: "SlideProjectorGallery-\(s.photo)", withExtension: "jpg"),
                  let src = UIImage(contentsOfFile: url.path), let cg = src.cgImage else { return [UIImage(), UIImage(), UIImage()] }
            let sharp = src.preparingForDisplay() ?? src
            let input = CIImage(cgImage: cg)
            // σ 4 and 12 px on a 600 px tile, scaled to the photo's own width.
            let k = CGFloat(cg.width) / 600
            let blurred: [UIImage] = [4, 12].map { sigma in
                let out = input.clampedToExtent()
                    .applyingGaussianBlur(sigma: Double(sigma * k))
                    .cropped(to: input.extent)
                guard let img = ci.createCGImage(out, from: input.extent) else { return sharp }
                return UIImage(cgImage: img)
            }
            return [sharp] + blurred
        }
    }

    /// A 128 px tile of seeded grain.
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

    static func oval(_ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
    }

    /// The hub, drawn over the far mounts and under the near ones.
    static func hub(_ L: SlideProjectorGalleryLayout) -> Path {
        oval(L.trayCX, L.trayCY, L.rx * 0.42, L.ry * 0.42)
    }

    /// The cardboard slide mounts standing in the tray at `pos` (slots turned), as two paths: the
    /// far half (in shade) and the near half (lit). The slot at the gate (far side, 12 o'clock)
    /// is empty: its slide is in the projector.
    static func mounts(_ L: SlideProjectorGalleryLayout, pos: Double, slots: Int) -> (far: Path, near: Path) {
        var near = Path(), far = Path()
        let step = 2 * Double.pi / Double(slots)
        let cx = Double(L.trayCX), cy = Double(L.trayCY), rx = Double(L.rx), ry = Double(L.ry)
        for k in 0..<slots {
            let a = -Double.pi / 2 + (Double(k) - pos) * step
            let d = abs(atan2(sin(a + .pi / 2), cos(a + .pi / 2)))
            if d < step * 0.5 { continue }
            let ca = cos(a), sa = sin(a)
            let x0 = cx + ca * rx * 0.5, y0 = cy + sa * ry * 0.5
            let x1 = cx + ca * rx * 0.88, y1 = cy + sa * ry * 0.88
            let up = 3 + 4 * (sa + 1) * 0.5
            let nx = -sa * ry, ny = ca * rx
            let nl = max(1e-3, hypot(nx, ny))
            let tx = nx / nl * 0.9, ty = ny / nl * 0.9
            var p = Path()
            p.move(to: CGPoint(x: x0 - tx, y: y0 - ty - up))
            p.addLine(to: CGPoint(x: x1 - tx, y: y1 - ty - up))
            p.addLine(to: CGPoint(x: x1 + tx, y: y1 + ty - up))
            p.addLine(to: CGPoint(x: x1 + tx, y: y1 + ty))
            p.addLine(to: CGPoint(x: x0 + tx, y: y0 + ty))
            p.closeSubpath()
            if sa > 0 { near.addPath(p) } else { far.addPath(p) }
        }
        return (far, near)
    }
}

/// The projector and the tray's static parts, transparent elsewhere (static, drawn once).
struct SlideProjectorGalleryBody: View, Equatable {
    var L: SlideProjectorGalleryLayout
    typealias C = SlideProjectorGalleryPalette
    typealias Art = SlideProjectorGalleryArt

    var body: some View {
        Canvas { ctx, _ in
            let W = L.W, H = L.H, bodyTop = L.bodyTop
            let lx = L.lensX, ly = L.lensY
            let cx = L.trayCX, cy = L.trayCY, rx = L.rx, ry = L.ry, side = L.side

            // Body: two shadows on the wall side, then a walnut shell with a cream top deck.
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 22))
                l.fill(Path(roundedRect: CGRect(x: 10, y: bodyTop - 10, width: W - 20, height: H - bodyTop + 40), cornerRadius: 34), with: .color(.black.opacity(0.55)))
            }
            let shellRect = CGRect(x: 14, y: bodyTop, width: W - 28, height: H - bodyTop + 30)
            let shell = Path(roundedRect: shellRect, cornerRadius: 30)
            ctx.fill(shell, with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0x5A3E2C), location: 0), .init(color: C.rgb(0x3E2A1E), location: 0.4), .init(color: C.rgb(0x2A1C14), location: 1)]),
                startPoint: CGPoint(x: 0, y: bodyTop), endPoint: CGPoint(x: 0, y: H)))
            let deckH = cy + ry + 24 - bodyTop
            ctx.fill(Path(roundedRect: CGRect(x: 22, y: bodyTop + 6, width: W - 44, height: deckH), cornerRadius: 24), with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0xD9CBB2), location: 0), .init(color: C.rgb(0xC4B394), location: 0.55), .init(color: C.rgb(0xA8977A), location: 1)]),
                startPoint: CGPoint(x: 0, y: bodyTop + 6), endPoint: CGPoint(x: 0, y: cy + ry + 30)))
            ctx.stroke(Path(roundedRect: CGRect(x: 22.5, y: bodyTop + 6.5, width: W - 45, height: deckH - 1), cornerRadius: 23.5),
                       with: .color(C.rgba(255, 250, 236, 0.55)), lineWidth: 1)
            // Vent slots either side of the tray.
            for i in 0..<6 {
                let y = bodyTop + 22 + CGFloat(i) * 7
                ctx.fill(Path(roundedRect: CGRect(x: 34, y: y, width: 26, height: 2.6), cornerRadius: 1.3), with: .color(C.rgba(60, 44, 30, 0.55)))
                ctx.fill(Path(roundedRect: CGRect(x: W - 60, y: y, width: 26, height: 2.6), cornerRadius: 1.3), with: .color(C.rgba(60, 44, 30, 0.55)))
            }
            // A chrome trim along the shell.
            ctx.fill(Path(CGRect(x: 18, y: cy + ry + 36, width: W - 36, height: 2)), with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0x8F8A84), location: 0), .init(color: C.rgb(0xF2EEE8), location: 0.5), .init(color: C.rgb(0x8F8A84), location: 1)]),
                startPoint: CGPoint(x: 14, y: 0), endPoint: CGPoint(x: W - 14, y: 0)))

            // Lens barrel: a short black cylinder with a chrome ring, pointing at the wall.
            let barrel = GraphicsContext.Shading.linearGradient(
                Gradient(stops: [.init(color: C.rgb(0x1A1716), location: 0), .init(color: C.rgb(0x4A4542), location: 0.4), .init(color: C.rgb(0x121010), location: 1)]),
                startPoint: CGPoint(x: lx - 24, y: 0), endPoint: CGPoint(x: lx + 24, y: 0))
            ctx.fill(Path(CGRect(x: lx - 24, y: ly, width: 48, height: bodyTop + 12 - ly)), with: barrel)
            ctx.fill(Art.oval(lx, bodyTop + 12, 24, 8), with: barrel)
            ctx.fill(Art.oval(lx, ly, 26, 9), with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0x7F7A75), location: 0), .init(color: C.rgb(0xF4F0EA), location: 0.45), .init(color: C.rgb(0x6E6964), location: 1)]),
                startPoint: CGPoint(x: lx - 26, y: 0), endPoint: CGPoint(x: lx + 26, y: 0)))
            ctx.fill(Art.oval(lx, ly, 20, 6.5), with: .radialGradient(
                Gradient(stops: [.init(color: C.rgb(0x6E7C86), location: 0), .init(color: C.rgb(0x1D2328), location: 0.5), .init(color: C.rgb(0x0B0D0F), location: 1)]),
                center: CGPoint(x: lx - 5, y: ly - 2), startRadius: 0, endRadius: 22))

            // Tray: drop shadow, the outer wall (a cylinder band), the top rim and the dark slot well.
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 10))
                l.fill(Art.oval(cx, cy + side + 6, rx + 4, ry + 4), with: .color(C.rgba(30, 20, 10, 0.45)))
            }
            var band = Path()
            band.move(to: CGPoint(x: cx - rx, y: cy))
            band.addLine(to: CGPoint(x: cx - rx, y: cy + side))
            for i in 1...48 {
                let th = Double.pi * (1 - Double(i) / 48)
                band.addLine(to: CGPoint(x: cx + rx * CGFloat(cos(th)), y: cy + side + ry * CGFloat(sin(th))))
            }
            band.addLine(to: CGPoint(x: cx + rx, y: cy))
            band.closeSubpath()
            ctx.fill(band, with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0x1E1B1A), location: 0), .init(color: C.rgb(0x4B4644), location: 0.35), .init(color: C.rgb(0x2A2625), location: 0.7), .init(color: C.rgb(0x141211), location: 1)]),
                startPoint: CGPoint(x: cx - rx, y: 0), endPoint: CGPoint(x: cx + rx, y: 0)))
            ctx.fill(Art.oval(cx, cy, rx, ry), with: .linearGradient(
                Gradient(stops: [.init(color: C.rgb(0x3A3532), location: 0), .init(color: C.rgb(0x2A2624), location: 0.5), .init(color: C.rgb(0x3C3734), location: 1)]),
                startPoint: CGPoint(x: 0, y: cy - ry), endPoint: CGPoint(x: 0, y: cy + ry)))
            ctx.fill(Art.oval(cx, cy, rx * 0.92, ry * 0.92), with: .color(C.rgb(0x141110)))
            ctx.stroke(Art.oval(cx, cy, rx - 0.6, ry - 0.6), with: .color(C.rgba(255, 240, 220, 0.25)), lineWidth: 1.2)

            if let grain = Art.grain {
                ctx.drawLayer { l in
                    l.clip(to: shell)
                    l.opacity = 0.06
                    l.blendMode = .overlay
                    l.fill(Path(CGRect(x: 0, y: bodyTop, width: W, height: H - bodyTop)), with: .tiledImage(Image(decorative: grain, scale: 2)))
                }
            }
        }
        .frame(width: L.W, height: L.H)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The slide in the gate at its three focus levels, stacked at the top of a full-screen view:
/// the layer the wall shader samples.
struct SlideProjectorGalleryGate: View, Equatable {
    var L: SlideProjectorGalleryLayout
    var slide: Int
    var ready: Bool
    var tiles: [UIImage]

    static func == (a: Self, b: Self) -> Bool { a.L == b.L && a.slide == b.slide && a.ready == b.ready }

    var body: some View {
        let t = L.tile
        ZStack(alignment: .topLeading) {
            Color.black.frame(width: L.W, height: L.H)
            ForEach(0..<tiles.count, id: \.self) { level in
                Image(uiImage: tiles[level])
                    .resizable()
                    .interpolation(.high)
                    .frame(width: t.width, height: t.height)
                    .offset(y: CGFloat(level) * t.height)
            }
        }
        .frame(width: L.W, height: L.H, alignment: .topLeading)
    }
}
