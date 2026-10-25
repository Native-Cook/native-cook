import SwiftUI
import UIKit

/*
 Piano Key Button — the drawing. Two static Canvases (the background: lacquer, the lesson card,
 the fallboard rail, the hammer recess and the stave; the overlay: the felt strip and the black
 keys), drawn once, with the parts that move drawn between and over them every frame by the
 timeline's Canvases: hammers and strings in the recess, the three ivory keys, their labels,
 glows, ring waves and the stave's notes.
*/
enum PianoKeyButtonArt {
    typealias M = PianoKeyButtonMotion

    static let keyColors: [Color] = [Color(hex: 0xF2B35B), Color(hex: 0x5FC9C1), Color(hex: 0xEC8796)]
    static let keyLabels: [(title: String, sub: String, note: String)] = [
        ("Warm up", "5 min", "C"),
        ("Lesson 14", "Triads", "E"),
        ("Free play", "Any key", "G"),
    ]
    static let ink = Color(hex: 0x3A2C20)
    static let inkSoft = Color(hex: 0x8C7660)
    static let staffInk = Color(hex: 0xE9D9C2)

    struct Scene: Equatable {
        var w: Double
        var h: Double
        var cardX: Double
        var cardY: Double
        var cardW: Double
        var cardH: Double
        /// Stave: left, right, y of the bottom line, line spacing.
        var staffL: Double
        var staffR: Double
        var staffY: Double
        var staffGap: Double
        var recessTop: Double
        var feltY: Double
        var feltH: Double
        var keyTop: Double
        var keyBottom: Double
        var keyX: [Double]
        var keyW: Double
        var gap: Double

        init(w: Double, h: Double, top: Double, bottom: Double) {
            self.w = w
            self.h = h
            cardX = 16
            cardY = top + 108
            cardW = w - 32
            cardH = (min(116, max(96, h * 0.13))).rounded()
            recessTop = cardY + cardH + 30
            feltY = recessTop + 36
            feltH = 16
            keyTop = feltY + feltH
            keyBottom = h - bottom - 54
            gap = 5
            keyW = (w - 28 - gap * 2) / 3
            keyX = [14, 14 + keyW + gap, 14 + 2 * (keyW + gap)]
            staffR = cardX + cardW - 20
            staffL = staffR - min(150, cardW * 0.42)
            staffGap = 7
            staffY = cardY + cardH / 2 + staffGap * 2 + 2
        }

        func keyAt(_ p: CGPoint) -> Int? {
            if p.y < keyTop - 12 || p.y > keyBottom + 14 { return nil }
            for i in 0..<3 where p.x >= keyX[i] - gap / 2 && p.x <= keyX[i] + keyW + gap / 2 { return i }
            return nil
        }
    }

    // MARK: Helpers

    static func lin(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ colors: [Color], _ stops: [Double]? = nil) -> GraphicsContext.Shading {
        let pos = stops ?? colors.indices.map { colors.count > 1 ? Double($0) / Double(colors.count - 1) : 0 }
        let g = Gradient(stops: zip(colors, pos).map { Gradient.Stop(color: $0, location: $1) })
        return .linearGradient(g, startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    static func rad(_ x: Double, _ y: Double, _ r: Double, _ colors: [Color], _ stops: [Double]? = nil) -> GraphicsContext.Shading {
        let pos = stops ?? colors.indices.map { colors.count > 1 ? Double($0) / Double(colors.count - 1) : 0 }
        let g = Gradient(stops: zip(colors, pos).map { Gradient.Stop(color: $0, location: $1) })
        return .radialGradient(g, center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r)
    }

    static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: max(0, w), height: max(0, h)), cornerRadius: min(r, max(0, w) / 2, max(0, h) / 2), style: .circular)
    }

    static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    /// A shape filled with a Gaussian blur (a soft shadow or glow).
    static func blurred(_ ctx: GraphicsContext, _ path: Path, _ color: Color, _ alpha: Double, _ sigma: Double) {
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: sigma))
            l.fill(path, with: .color(color.opacity(alpha)))
        }
    }

    /// Monochrome grain laid over with `overlay` at `alpha`.
    static func grain(_ ctx: GraphicsContext, _ rect: CGRect, _ tile: Image, _ alpha: Double) {
        var g = ctx
        g.blendMode = .overlay
        g.opacity = alpha
        g.fill(Path(rect), with: .tiledImage(tile, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    /// Draws resolved text with its baseline at `baseline`, centred on x or starting at x.
    static func text(_ ctx: GraphicsContext, _ r: GraphicsContext.ResolvedText, x: Double, baseline: Double, centered: Bool) {
        let s = r.measure(in: CGSize(width: 2000, height: 2000))
        let b = r.firstBaseline(in: s)
        ctx.draw(r, in: CGRect(x: centered ? x - s.width / 2 : x, y: baseline - b, width: s.width, height: s.height))
    }

    // MARK: Grain tiles (value noise, periodic, built once; small enough to make at once)

    /// Fine grain everywhere (cells ≈ 1.1 pt), tiling 128 pt.
    static let fineGrain = noiseTile(points: 128, cellX: 1.1, cellY: 1.1, seed: 3)
    /// Felt nap: a little coarser across.
    static let feltGrain = noiseTile(points: 128, cellX: 0.64, cellY: 1.1, seed: 11)
    /// Ivory: long faint streaks along the key.
    static let ivoryGrain = noiseTile(points: 128, cellX: 1.1, cellY: 25, seed: 5, heightPoints: 250)

    /// A tile of two-octave periodic value noise, grey around 0.5, at 2 px per point.
    static func noiseTile(points: Int, cellX: Double, cellY: Double, seed: UInt32, heightPoints: Int? = nil) -> Image {
        let scale = 2
        let wPx = points * scale
        let hPx = (heightPoints ?? points) * scale
        let nx = max(1, Int((Double(points) / cellX).rounded()))
        let ny = max(1, Int((Double(heightPoints ?? points) / cellY).rounded()))
        func hash(_ x: Int, _ y: Int, _ s: UInt32) -> Double {
            var h = UInt32(truncatingIfNeeded: x &* 374_761_393 &+ y &* 668_265_263) &+ s &* 2_246_822_519
            h = (h ^ (h >> 13)) &* 1_274_126_177
            h ^= h >> 16
            return Double(h & 0xFFFF) / 65535
        }
        func noise(_ u: Double, _ v: Double, _ px: Int, _ py: Int, _ s: UInt32) -> Double {
            let x = u * Double(px), y = v * Double(py)
            let ix = Int(floor(x)), iy = Int(floor(y))
            let fx = x - Double(ix), fy = y - Double(iy)
            let sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy)
            let x0 = ((ix % px) + px) % px, x1 = (((ix + 1) % px) + px) % px
            let y0 = ((iy % py) + py) % py, y1 = (((iy + 1) % py) + py) % py
            let a = hash(x0, y0, s), b = hash(x1, y0, s), c = hash(x0, y1, s), d = hash(x1, y1, s)
            return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy
        }
        var bytes = [UInt8](repeating: 128, count: wPx * hPx)
        for j in 0..<hPx {
            let v = (Double(j) + 0.5) / Double(hPx)
            for i in 0..<wPx {
                let u = (Double(i) + 0.5) / Double(wPx)
                let n = noise(u, v, nx, ny, seed) * 0.67 + noise(u, v, nx * 2, ny * 2, seed &+ 17) * 0.33
                let g = 0.5 + (n - 0.5) * 1.6
                bytes[j * wPx + i] = UInt8(max(0, min(255, g * 255)))
            }
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)!
        let cg = CGImage(width: wPx, height: hPx, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: wPx,
                         space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
                         provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
        return Image(decorative: cg, scale: CGFloat(scale))
    }

    // MARK: Static layers

    /// Lacquer, card, fallboard and recess: everything behind the moving parts.
    static func background(_ c: GraphicsContext, _ s: Scene) {
        let w = s.w, h = s.h
        let full = CGRect(x: 0, y: 0, width: w, height: h)
        // Ebony lacquer with a warm pool of light where the keys are, a vignette and grain.
        c.fill(Path(full), with: lin(0, 0, 0, h, [Color(hex: 0x1E1612), Color(hex: 0x18110E), Color(hex: 0x130E0C), Color(hex: 0x0F0B0A)], [0, 0.35, 0.75, 1]))
        c.fill(Path(full), with: rad(w * 0.5, s.keyTop + 40, h * 0.6, [.rgba(120, 74, 38, 0.30), .rgba(70, 40, 22, 0.12), .rgba(0, 0, 0, 0)], [0, 0.45, 1]))
        c.fill(Path(full), with: rad(w * 0.5, h * 0.48, h * 0.75, [.rgba(0, 0, 0, 0), .rgba(0, 0, 0, 0), .rgba(4, 2, 1, 0.18), .rgba(4, 2, 1, 0.55)], [0, 0.45, 0.78, 1]))
        grain(c, full, fineGrain, 0.07)

        // The lesson card: two shadows, a three-stop body, a hairline highlight on top.
        let card = rr(s.cardX, s.cardY, s.cardW, s.cardH, 20)
        blurred(c, rr(s.cardX, s.cardY + 14, s.cardW, s.cardH, 20), .black, 0.42, 16)
        blurred(c, rr(s.cardX, s.cardY + 1.5, s.cardW, s.cardH, 20), .black, 0.5, 1.5)
        c.fill(card, with: lin(0, s.cardY, 0, s.cardY + s.cardH, [Color(hex: 0x33271F), Color(hex: 0x2A2019), Color(hex: 0x231A15)]))
        var cc = c
        cc.clip(to: card)
        grain(cc, CGRect(x: s.cardX, y: s.cardY, width: s.cardW, height: s.cardH), fineGrain, 0.08)
        c.stroke(rr(s.cardX + 0.5, s.cardY + 0.5, s.cardW - 1, s.cardH - 1, 19.5), with: .color(Color(hex: 0xFFE7C8).opacity(0.12)), lineWidth: 1)

        // The stave: five lines and a common-time sign.
        for i in 0..<5 {
            let y = s.staffY - Double(i) * s.staffGap
            c.stroke(line(s.staffL, y, s.staffR, y), with: .color(staffInk.opacity(0.32)), lineWidth: 1)
        }
        c.stroke(line(s.staffR, s.staffY, s.staffR, s.staffY - 4 * s.staffGap), with: .color(staffInk.opacity(0.32)), lineWidth: 1.2)
        let four = c.resolve(Text("4").font(.custom("Georgia-Bold", size: 15)).foregroundStyle(staffInk.opacity(0.5)))
        text(c, four, x: s.staffL + 2, baseline: s.staffY - 2 * s.staffGap - 1, centered: false)
        text(c, four, x: s.staffL + 2, baseline: s.staffY + 0.5, centered: false)

        // Fallboard rail: lacquer with a gold pinstripe, then the dark recess with the hammers' rail.
        let railY = s.recessTop - 14
        c.fill(Path(CGRect(x: 0, y: railY, width: w, height: 14)), with: lin(0, railY, 0, s.recessTop, [Color(hex: 0x2B211C), Color(hex: 0x1A1311), Color(hex: 0x100C0A)]))
        c.stroke(line(0, railY + 0.5, w, railY + 0.5), with: .color(Color(hex: 0xFFDDB0).opacity(0.16)), lineWidth: 1)
        c.stroke(line(10, railY + 7, w - 10, railY + 7), with: .color(Color(hex: 0xC9A15E).opacity(0.55)), lineWidth: 1)
        c.fill(Path(CGRect(x: 0, y: s.recessTop, width: w, height: s.feltY - s.recessTop)), with: lin(0, s.recessTop, 0, s.feltY, [Color(hex: 0x080605), Color(hex: 0x140E0B), Color(hex: 0x1E1611)]))
        blurred(c, Path(CGRect(x: 0, y: s.recessTop, width: w, height: 8)), .black, 0.7, 4)
        // A brass rail the strings run over, across the back of the recess.
        c.fill(Path(CGRect(x: 0, y: s.recessTop + 3, width: w, height: 5)), with: lin(0, s.recessTop + 3, 0, s.recessTop + 8, [Color(hex: 0x7C5B2E), Color(hex: 0xD8B678), Color(hex: 0x6A4B24)]))

        // The keybed floor in the gaps, and the keyslip under the keys.
        c.fill(Path(CGRect(x: 0, y: s.keyTop, width: w, height: s.keyBottom - s.keyTop + 8)), with: lin(0, s.keyTop, 0, s.keyBottom, [Color(hex: 0x0C0908), Color(hex: 0x130E0C), Color(hex: 0x0E0A09)]))
        let slipY = s.keyBottom + 8
        c.fill(Path(CGRect(x: 0, y: slipY, width: w, height: 14)), with: lin(0, slipY, 0, slipY + 14, [Color(hex: 0x2E231D), Color(hex: 0x1C1512), Color(hex: 0x140F0D)]))
        c.stroke(line(0, slipY + 0.5, w, slipY + 0.5), with: .color(Color(hex: 0xFFE2BC).opacity(0.14)), lineWidth: 1)
    }

    /// Felt strip and black keys: what sits in front of the hammers and over the ivory keys.
    static func overlay(_ c: GraphicsContext, _ s: Scene) {
        let w = s.w
        // Burgundy felt with a fuzzy nap and a soft shadow it throws onto the keys.
        let felt = CGRect(x: 0, y: s.feltY, width: w, height: s.feltH)
        c.fill(Path(felt), with: lin(0, s.feltY, 0, s.feltY + s.feltH, [Color(hex: 0x7A2433), Color(hex: 0x5E1A27), Color(hex: 0x45121D)]))
        var fc = c
        fc.clip(to: Path(felt))
        grain(fc, felt, feltGrain, 0.32)
        c.stroke(line(0, s.feltY + 0.5, w, s.feltY + 0.5), with: .color(Color(hex: 0xFFB3BE).opacity(0.22)), lineWidth: 1)
        // Black keys sit in the gaps, over the upper half of the ivory keys.
        let bw = min(30, s.keyW * 0.26)
        let bh = (s.keyBottom - s.keyTop) * 0.52
        for i in 0..<2 {
            let cx = s.keyX[i + 1] - s.gap / 2
            let x = cx - bw / 2
            let y = s.keyTop - 2
            // Two shadows on the ivory: tight contact and soft ambient, both falling down-screen.
            blurred(c, rr(x - 3, y + 10, bw + 6, bh + 6, 6), Color(hex: 0x1A0E06), 0.38, 9)
            blurred(c, rr(x - 0.5, y, bw + 1, bh + 2, 4), .black, 0.6, 1.5)
            c.fill(rr(x, y, bw, bh, 4), with: lin(x, 0, x + bw, 0, [Color(hex: 0x18130F), Color(hex: 0x2C2520), Color(hex: 0x1F1A16), Color(hex: 0x110E0C)], [0, 0.3, 0.7, 1]))
            // The sloped front where a finger lands, catching a little light.
            c.fill(rr(x + 2, y + bh - 18, bw - 4, 16, 3), with: lin(0, y + bh - 18, 0, y + bh, [Color(hex: 0x2A231E), Color(hex: 0x3D342D), Color(hex: 0x231D19)]))
            // Gloss: a long specular strip down the left of the top face.
            c.fill(rr(x + bw * 0.22, y + 4, bw * 0.16, bh - 26, 2), with: lin(0, y, 0, y + bh - 20, [.rgba(255, 236, 214, 0.20), .rgba(255, 236, 214, 0.07), .rgba(255, 236, 214, 0)]))
        }
    }

    // MARK: Moving parts

    /// One ivory key's outline: square under the felt, rounded at the front, narrowing as it pivots.
    static func keyPath(_ x: Double, _ top: Double, _ w: Double, _ bottom: Double, _ inset: Double) -> Path {
        let r = 7.0
        var b = Path()
        b.move(to: CGPoint(x: x, y: top))
        b.addLine(to: CGPoint(x: x + w, y: top))
        b.addLine(to: CGPoint(x: x + w - inset, y: bottom - r))
        b.addQuadCurve(to: CGPoint(x: x + w - inset - r, y: bottom), control: CGPoint(x: x + w - inset, y: bottom))
        b.addLine(to: CGPoint(x: x + inset + r, y: bottom))
        b.addQuadCurve(to: CGPoint(x: x + inset, y: bottom - r), control: CGPoint(x: x + inset, y: bottom))
        b.closeSubpath()
        return b
    }

    static func depthOf(_ k: M.Key) -> Double { max(-0.1, min(1.15, k.depth)) }

    /// The keys' shadows on the keyslip, in one blurred layer.
    static func keyShadows(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        var deepest = 0.0
        for k in pose.keys { deepest = max(deepest, depthOf(k)) }
        c.drawLayer { l in
            l.addFilter(.blur(radius: 6 - 2 * deepest))
            for i in 0..<pose.keys.count {
                let p = depthOf(pose.keys[i])
                let x = s.keyX[i], w = s.keyW
                let bottom = s.keyBottom - 5 * p
                let inset = 3.2 * p
                let lip = 7 - 4 * p
                l.fill(rr(x + 4 + inset, bottom + lip - 2, w - 8 - inset * 2, 10 - 4 * p, 5), with: .color(.black.opacity(0.55 - 0.15 * p)))
            }
        }
    }

    static func key(_ c: GraphicsContext, _ s: Scene, _ i: Int, _ k: M.Key, labels: [GraphicsContext.ResolvedText], note: GraphicsContext.ResolvedText) {
        let p = depthOf(k)
        let x = s.keyX[i]
        let w = s.keyW
        // Pivoting down 6° around the far edge: the near end drops away, so it foreshortens and narrows.
        let bottom = s.keyBottom - 5 * p
        let inset = 3.2 * p
        let lip = 7 - 4 * p
        let glow = M.keyGlow(k)

        // Front lip (the key's front face), darker ivory.
        c.fill(rr(x + inset, bottom - 8, w - inset * 2, 8 + lip, 7), with: lin(0, bottom - 8, 0, bottom + lip, [Color(hex: 0xD8C8AB), Color(hex: 0xC9B693), Color(hex: 0xAE9A78)]))

        let path = keyPath(x, s.keyTop, w, bottom, inset)
        c.fill(path, with: lin(0, s.keyTop, 0, bottom, [Color(hex: 0xF7F0DF), Color(hex: 0xF2E8D3), Color(hex: 0xECE0C8), Color(hex: 0xE3D5B8)], [0, 0.4, 0.8, 1]))
        var kc = c
        kc.clip(to: path)
        let body = CGRect(x: x, y: s.keyTop, width: w, height: bottom - s.keyTop)
        // Ivory grain, side shading (a highlight on the left edge, the right edge in shade).
        grain(kc, body, ivoryGrain, 0.1)
        kc.fill(Path(body), with: lin(x, 0, x + w, 0, [.rgba(255, 255, 250, 0.55), .rgba(255, 255, 250, 0), .rgba(80, 56, 30, 0), .rgba(80, 56, 30, 0.16)], [0, 0.08, 0.86, 1]))
        // Going down it leaves the light: darker toward the front, and the neighbours shade its sides.
        if p > 0.01 {
            kc.fill(Path(body), with: lin(0, s.keyTop, 0, bottom, [.rgba(60, 38, 18, 0), .rgba(60, 38, 18, 0.11 * p)]))
            let a = 0.16 * p
            kc.fill(Path(body), with: lin(x, 0, x + w, 0, [.rgba(40, 24, 10, a), .rgba(40, 24, 10, 0), .rgba(40, 24, 10, 0), .rgba(40, 24, 10, a)], [0, 0.1, 0.9, 1]))
        }
        // The felt's shadow across the top tightens as the key's far end lifts toward it.
        kc.fill(Path(CGRect(x: x, y: s.keyTop, width: w, height: 18)), with: lin(0, s.keyTop, 0, s.keyTop + 16 - 7 * p, [.rgba(40, 10, 14, 0.42 + 0.12 * p), .rgba(40, 10, 14, 0)]))
        // A warm wash of the key's colour while it sounds.
        if glow > 0.005 {
            var wc = kc
            wc.blendMode = .multiply
            wc.opacity = M.clamp01(glow * 0.55)
            wc.fill(Path(body), with: rad(x + w / 2, bottom - 60, w * 1.1, [keyColors[i], .rgba(0, 0, 0, 0)]))
        }
        // Labels roll: the CTA leaves upward and fades, the note name comes up from below.
        let cx = x + w / 2
        let base = bottom - 44
        var lc = kc
        lc.clip(to: Path(CGRect(x: x + 4, y: base - 50, width: w - 8, height: 76)))
        let r = M.clamp01(k.label)
        if r < 0.999 {
            let up = -30 * r
            var tc = lc
            tc.opacity = M.clamp01(1 - r * 1.7)
            text(tc, labels[0], x: cx, baseline: base - 8 + up, centered: true)
            text(tc, labels[1], x: cx, baseline: base + 12 + up, centered: true)
        }
        if r > 0.001 {
            var nc = lc
            nc.opacity = M.clamp01(r * 1.7 - 0.7)
            nc.translateBy(x: cx, y: base + 8 + 44 * (1 - r))
            nc.scaleBy(x: 1, y: 1 - 0.02 * p)
            text(nc, note, x: 0, baseline: 0, centered: true)
        }
        // A small marker of the key's colour above the label.
        c.fill(Path(ellipseIn: CGRect(x: cx - 3.5, y: base - 64 - 3.5, width: 7, height: 7)), with: .color(keyColors[i].opacity(0.9)))
    }

    /// Hammers and strings in the recess (the caller clips to it).
    static func hammers(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        // String glows and hammer shadows go in one blurred layer each.
        var anyRing = false
        var maxLift = 0.0
        for k in pose.keys {
            if M.stringRing(k) > 0.02 { anyRing = true }
            maxLift = max(maxLift, M.hammerLift(k.downAge))
        }
        if anyRing {
            c.drawLayer { l in
                l.addFilter(.blur(radius: 5))
                for i in 0..<pose.keys.count {
                    let ring = M.stringRing(pose.keys[i])
                    guard ring > 0.02 else { continue }
                    let cx = s.keyX[i] + s.keyW / 2
                    l.fill(Path(CGRect(x: cx - 9, y: s.recessTop + 4, width: 18, height: s.feltY - s.recessTop - 4)), with: .color(keyColors[i].opacity(0.35 * ring)))
                }
            }
        }
        let hw = 40.0, hh = 26.0
        for i in 0..<pose.keys.count {
            // Strings: a unison of three per key, brightening and thickening while they ring.
            let ring = M.stringRing(pose.keys[i])
            let cx = s.keyX[i] + s.keyW / 2
            var strings = Path()
            for j in -1...1 {
                let x = cx + Double(j) * 5
                strings.move(to: CGPoint(x: x, y: s.recessTop + 4))
                strings.addLine(to: CGPoint(x: x, y: s.feltY))
            }
            c.stroke(strings, with: .color(Color(hex: 0xD9B97F).opacity(0.28 + 0.6 * ring)), style: StrokeStyle(lineWidth: 0.8 + 1.2 * ring, lineCap: .round))
        }
        c.drawLayer { l in
            l.addFilter(.blur(radius: 4 + 2 * maxLift))
            for i in 0..<pose.keys.count {
                let cx = s.keyX[i] + s.keyW / 2
                let y = s.feltY - 16 - 14 * M.hammerLift(pose.keys[i].downAge)
                l.fill(rr(cx - hw / 2, y + 6, hw, hh, 9), with: .color(.black.opacity(0.55)))
            }
        }
        for i in 0..<pose.keys.count {
            // The hammer: walnut molding with a cream felt head, flicking up toward the strings.
            let cx = s.keyX[i] + s.keyW / 2
            let y = s.feltY - 16 - 14 * M.hammerLift(pose.keys[i].downAge)
            c.fill(rr(cx - hw / 2, y, hw, hh, 9), with: lin(cx - hw / 2, 0, cx + hw / 2, 0, [Color(hex: 0x4A3121), Color(hex: 0x7A5236), Color(hex: 0x3C281B)]))
            c.fill(rr(cx - hw / 2 + 3, y + 1, hw - 6, 12, 6), with: lin(0, y, 0, y + 12, [Color(hex: 0xF1E6D2), Color(hex: 0xDCCDB3), Color(hex: 0xBBA88A)]))
        }
    }

    static func rings(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        var items: [(cx: Double, cy: Double, r: Double, fade: Double, u: Double, color: Color)] = []
        for i in 0..<pose.keys.count {
            let k = pose.keys[i]
            if k.downAge > M.ringLife + M.ringDelay * 2 { continue }
            let cx = s.keyX[i] + s.keyW / 2
            let cy = s.keyTop + (s.keyBottom - s.keyTop) * 0.62
            for j in 0..<3 {
                let age = k.downAge - Double(j) * M.ringDelay
                if age <= 0 || age >= M.ringLife { continue }
                let u = age / M.ringLife
                let radius = 22 + s.w * 0.85 * M.easeOut(u)
                let fade = pow(1 - u, 1.6) * (1 - Double(j) * 0.22)
                items.append((cx, cy, radius, fade, u, keyColors[i]))
            }
        }
        guard !items.isEmpty else { return }
        var sc = c
        sc.blendMode = .screen
        sc.drawLayer { l in
            l.addFilter(.blur(radius: 7))
            for it in items {
                l.stroke(Path(ellipseIn: CGRect(x: it.cx - it.r, y: it.cy - it.r, width: it.r * 2, height: it.r * 2)), with: .color(it.color.opacity(0.5 * it.fade)), lineWidth: 10)
            }
        }
        for it in items {
            sc.stroke(Path(ellipseIn: CGRect(x: it.cx - it.r, y: it.cy - it.r, width: it.r * 2, height: it.r * 2)), with: .color(it.color.opacity(0.95 * it.fade)), lineWidth: 3 - 2 * it.u)
        }
    }

    static func notes(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        let g = s.staffGap
        let x0 = s.staffL + 30
        let step = (s.staffR - 14 - x0) / 3
        // Steps above the bottom line (E4 = 0): C4 = -2, E4 = 0, G4 = 2; the chord stacks C4 and G4.
        let heads: [[Double]] = [[-2], [0], [2], [-2, 2]]
        let colors = [keyColors[0], keyColors[1], keyColors[2], keyColors[0]]
        // Glows first, in one blurred layer.
        if pose.notes.contains(where: { $0 > 0.01 }) {
            c.drawLayer { l in
                l.addFilter(.blur(radius: 6))
                for n in 0..<4 {
                    let lit = M.clamp01(pose.notes[n])
                    guard lit > 0.01 else { continue }
                    let x = x0 + step * Double(n)
                    for st in heads[n] {
                        let y = s.staffY - st * g / 2
                        let col = n == 3 && st == 2 ? keyColors[2] : colors[n]
                        l.fill(Path(ellipseIn: CGRect(x: x - 10, y: y - 10, width: 20, height: 20)), with: .color(col.opacity(0.55 * lit)))
                    }
                }
            }
        }
        for n in 0..<4 {
            let x = x0 + step * Double(n)
            let lit = M.clamp01(pose.notes[n])
            for st in heads[n] {
                let y = s.staffY - st * g / 2
                if st == -2 {
                    c.stroke(line(x - 8, y, x + 8, y), with: .color(staffInk.opacity(0.45)), lineWidth: 1)
                }
                let col = n == 3 && st == 2 ? keyColors[2] : colors[n]
                var hc = c
                hc.translateBy(x: x, y: y)
                hc.rotate(by: .degrees(-20))
                hc.fill(Path(ellipseIn: CGRect(x: -5, y: -3.6, width: 10, height: 7.2)), with: .color((lit > 0.5 ? col : staffInk).opacity(0.5 + 0.5 * lit)))
            }
            let top = heads[n][heads[n].count - 1]
            let sy = s.staffY - top * g / 2
            c.stroke(line(x + 4.6, sy - 1, x + 4.6, sy - 24), with: .color((lit > 0.5 ? colors[n] : staffInk).opacity(0.5 + 0.5 * lit)), lineWidth: 1.2)
        }
    }
}
