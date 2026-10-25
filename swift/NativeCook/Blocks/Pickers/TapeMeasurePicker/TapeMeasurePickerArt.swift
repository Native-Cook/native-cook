import SwiftUI

/*
 The tape measure, seen from above: a rubber-armoured case with a yellow lacquered face, chrome
 screws and a belt clip at the left; the yellow tape runs out of its mouth to the right, cupped
 (darker at both edges, a gloss line just above the middle), with millimetre ticks on both edges
 and a number at every fifth centimetre (every tenth in red). Numbers grow toward the case: the
 hook end is off screen to the right. A red hairline just outside the mouth reads the value.

 Rendered once: the paper background and the case. Per frame: the tape's body, shadow, ticks and
 the ~4 visible numbers (Canvas symbols, resolved once), then the case, then the hairline.
 */
struct TapeMeasurePickerGeo: Equatable {
    let w: Double
    let h: Double
    let tapeY: Double
    let tapeH = 72.0
    let caseX = 14.0
    let caseY: Double
    let caseS: Double
    let mouthX: Double
    let readX: Double

    init(w: Double, h: Double) {
        self.w = w
        self.h = h
        tapeY = h * 0.6
        caseS = min(170, w * 0.43)
        caseY = h * 0.6 - 24 - min(170, w * 0.43) / 2
        mouthX = 14 + min(170, w * 0.43) - 4
        readX = 14 + min(170, w * 0.43) - 4 + 22
    }
}

enum TapeMeasurePickerArt {
    struct Images {
        let background: Image
        let body: Image
    }

    /// Tape numbers drawn: every fifth centimetre up to the top of the scale plus its margin.
    static let numbers = Array(stride(from: 5, through: 245, by: 5))

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(hex: hex, opacity: a)
    }

    private static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ stops: [(UInt32, Double, Double)]) -> GraphicsContext.Shading {
        .linearGradient(
            Gradient(stops: stops.map { .init(color: color($0.0, $0.1), location: $0.2) }),
            startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1)
        )
    }

    private static func radial(_ x: Double, _ y: Double, _ r: Double, _ stops: [(UInt32, Double, Double)]) -> GraphicsContext.Shading {
        .radialGradient(
            Gradient(stops: stops.map { .init(color: color($0.0, $0.1), location: $0.2) }),
            center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r
        )
    }

    private static func blurred(_ c: GraphicsContext, _ sigma: Double) -> GraphicsContext {
        var copy = c
        copy.addFilter(.blur(radius: sigma))
        return copy
    }

    private static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }

    private static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }

    @MainActor static func render(_ g: TapeMeasurePickerGeo, scale: Double) -> Images {
        func image<V: View>(_ content: V) -> Image {
            let renderer = ImageRenderer(content: content.frame(width: g.w, height: g.h))
            renderer.scale = scale
            return renderer.uiImage.map { Image(uiImage: $0) } ?? Image(systemName: "circle")
        }
        return Images(
            background: image(Canvas { c, _ in drawBackground(c, g) }),
            body: image(Canvas { c, _ in drawCase(c, g) })
        )
    }

    private static func screw(_ c: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ turn: Double) {
        blurred(c, 0.8).fill(circle(x, y + 0.8, r + 0.6), with: .color(color(0x5A3A00, 0.45)))
        c.fill(circle(x, y, r), with: radial(x - r * 0.4, y - r * 0.5, r * 1.7, [(0xFFFFFF, 1, 0), (0xB9BEC5, 1, 0.45), (0x5D636B, 1, 1)]))
        var slot = c
        slot.translateBy(x: x, y: y)
        slot.rotate(by: .degrees(turn))
        var p = line(-r * 0.6, 0, r * 0.6, 0)
        p.addPath(line(0, -r * 0.6, 0, r * 0.6))
        slot.stroke(p, with: .color(color(0x2B2E33, 0.85)), lineWidth: 1)
    }

    /// Paper background with grain and a soft window light.
    private static func drawBackground(_ c: GraphicsContext, _ g: TapeMeasurePickerGeo) {
        let all = Path(CGRect(x: 0, y: 0, width: g.w, height: g.h))
        c.fill(all, with: linear(0, 0, 0, g.h, [(0xF4F1EB, 1, 0), (0xECE7DE, 1, 0.55), (0xE3DDD1, 1, 1)]))
        c.fill(all, with: radial(g.w * 0.15, g.h * 0.05, g.w * 1.1, [(0xFFFDF8, 0.7, 0), (0xFFFDF8, 0, 1)]))
        // A faint table edge behind the tape, so the case and tape sit on something.
        let edge = g.tapeY - g.caseS * 0.95
        c.fill(Path(CGRect(x: 0, y: edge, width: g.w, height: g.h - edge)), with: linear(0, edge, 0, g.h, [(0xD9D2C4, 0, 0), (0xD9D2C4, 0.35, 0.3), (0xD2CABB, 0.5, 1)]))
        // Paper grain (Skia's fractal noise, multiplied, in RN), as one batch of faint specks.
        var specks = Path()
        for i in 0..<Int(g.w * g.h / 16) {
            let ii = Double(i)
            specks.addRect(CGRect(x: hash(ii * 1.37 + 0.5) * g.w, y: hash(ii * 2.11 + 7.3) * g.h, width: 1, height: 1))
        }
        c.fill(specks, with: .color(color(0x5A4A30, 0.06)))
    }

    /// The case: belt clip, rubber armour, yellow face, screws, lock button, the chrome mouth.
    private static func drawCase(_ c: GraphicsContext, _ g: TapeMeasurePickerGeo) {
        let x = g.caseX, y = g.caseY, s = g.caseS
        let cx = x + s / 2
        let cy = y + s / 2
        // Shadows: soft ambient and a tight contact one.
        blurred(c, 14).fill(rr(x + 4, y + 14, s - 4, s - 6, 36), with: .color(color(0x3B2C14, 0.24)))
        blurred(c, 3).fill(rr(x + 1, y + 3, s - 2, s - 2, 36), with: .color(color(0x2A1E0C, 0.32)))
        // Belt clip peeking out at the left, and the lock button at the top.
        c.fill(rr(x - 7, cy - 46, 30, 92, 7), with: linear(x - 7, 0, x + 23, 0, [(0x6C727A, 1, 0), (0xD9DDE2, 1, 0.3), (0x8A9098, 1, 0.6), (0x4A4F56, 1, 1)]))
        c.fill(rr(cx + 6, y - 7, 38, 16, 7), with: linear(0, y - 7, 0, y + 9, [(0x4A4C51, 1, 0), (0x2A2B2F, 1, 0.5), (0x1B1C1F, 1, 1)]))
        c.stroke(line(cx + 12, y - 4.5, cx + 38, y - 4.5), with: .color(.white.opacity(0.18)), lineWidth: 1)
        // Rubber armour.
        c.fill(rr(x, y, s, s, 36), with: linear(0, y, 0, y + s, [(0x3C3D42, 1, 0), (0x2A2B2F, 1, 0.3), (0x1F2023, 1, 0.7), (0x161719, 1, 1)]))
        c.stroke(rr(x + 0.5, y + 0.5, s - 1, s - 1, 35.5), with: .color(.white.opacity(0.12)), lineWidth: 1)
        var ridge = Path()
        var ridgeLight = Path()
        for k in 0..<7 {
            let yy = cy - 33 + Double(k) * 11
            ridge.addPath(line(x + 3, yy, x + 9, yy))
            ridgeLight.addPath(line(x + 3, yy + 1.4, x + 9, yy + 1.4))
        }
        c.stroke(ridge, with: .color(color(0x0E0F10, 0.6)), lineWidth: 1.6)
        c.stroke(ridgeLight, with: .color(.white.opacity(0.08)), lineWidth: 0.8)
        // Yellow lacquered face.
        let inset = 11.0
        let fx = x + inset
        let fy = y + inset
        let fs = s - inset * 2
        blurred(c, 2).fill(rr(fx, fy + 1.5, fs, fs, 26), with: .color(.black.opacity(0.45)))
        let face = rr(fx, fy, fs, fs, 26)
        c.fill(face, with: linear(fx, fy, fx + fs, fy + fs, [(0xFFE36D, 1, 0), (0xFAC929, 1, 0.35), (0xEEAE17, 1, 0.7), (0xCF8D0C, 1, 1)]))
        c.fill(face, with: radial(fx + fs * 0.28, fy + fs * 0.22, fs * 0.75, [(0xFFFBE6, 0.55, 0), (0xFFF4C0, 0, 1)]))
        c.stroke(rr(fx + 0.7, fy + 0.7, fs - 1.4, fs - 1.4, 25.3), with: .color(color(0xFFFBE8, 0.6)), lineWidth: 1.2)
        c.stroke(rr(fx + 1, fy + 2, fs - 2, fs - 3, 25), with: .color(color(0x8A5A00, 0.18)), lineWidth: 1)
        // The reel cover: an embossed ring with the brand.
        c.stroke(circle(cx, cy + 1, fs * 0.33), with: .color(color(0xFFF6CC, 0.55)), lineWidth: 1.4)
        c.stroke(circle(cx, cy, fs * 0.33), with: .color(color(0x8A5800, 0.35)), lineWidth: 1.4)
        // Brand: letter-spaced 2.2 pt, an embossed light copy 0.8 pt below the ink.
        func brand(_ col: Color) -> Text {
            Text("FITLINE").font(.system(size: 15, weight: .heavy)).kerning(2.2).foregroundColor(col)
        }
        // drawText's baseline at cy + 4: the line box's centre sits about 0.355 em above it.
        c.draw(brand(color(0xFFF8D8, 0.55)), at: CGPoint(x: cx + 1.1, y: cy + 4.8 - 15 * 0.355))
        c.draw(brand(color(0x5C3C02, 0.82)), at: CGPoint(x: cx + 1.1, y: cy + 4 - 15 * 0.355))
        c.draw(
            Text("3 m · 10 ft").font(.system(size: 10, weight: .semibold)).foregroundColor(color(0x6B4705, 0.7)),
            at: CGPoint(x: cx, y: cy + 19 - 10 * 0.355)
        )
        // Screws in the face's corners.
        let sIn = 15.0
        screw(c, fx + sIn, fy + sIn, 4.6, 20)
        screw(c, fx + fs - sIn, fy + sIn, 4.6, 65)
        screw(c, fx + sIn, fy + fs - sIn, 4.6, 40)
        screw(c, fx + fs - sIn, fy + fs - sIn, 4.6, 5)
        // The mouth: a chrome guide where the tape comes out.
        let my = g.tapeY - g.tapeH / 2 - 7
        let mh = g.tapeH + 14
        blurred(c, 2.5).fill(rr(x + s - 10, my + 2, 20, mh, 5), with: .color(.black.opacity(0.35)))
        c.fill(rr(x + s - 10, my, 20, mh, 5), with: linear(0, my, 0, my + mh, [(0xF1F3F5, 1, 0), (0xA2A8B0, 1, 0.3), (0xE0E3E7, 1, 0.55), (0x6D737B, 1, 0.85), (0x3D4248, 1, 1)]))
        c.stroke(line(x + s + 8.5, my + 6, x + s + 8.5, my + mh - 6), with: .color(color(0x1D1F22, 0.8)), lineWidth: 1.6)
    }

    private static let tapeShading = Gradient(stops: [
        .init(color: color(0xC28F05), location: 0),
        .init(color: color(0xEEBD18), location: 0.1),
        .init(color: color(0xFFDC45), location: 0.3),
        .init(color: color(0xFFF1A0), location: 0.38),
        .init(color: color(0xF7C923), location: 0.5),
        .init(color: color(0xE3AD10), location: 0.82),
        .init(color: color(0xB07F04), location: 1),
    ])

    /// Draws the tape for value `v` (cm under the hairline): its shadow, cupped body, ticks and
    /// numbers, displaced by a wave of height `amp` (pt) travelling at `phase` (radians).
    static func drawTape(_ c: GraphicsContext, _ g: TapeMeasurePickerGeo, _ v: Double, _ amp: Double, _ phase: Double) {
        let x0 = g.mouthX - 26
        let x1 = g.w + 12
        let top = g.tapeY - g.tapeH / 2
        let span = max(1, x1 - g.mouthX)
        func wave(_ x: Double) -> Double {
            if amp < 0.01 || x <= g.mouthX { return 0 }
            let e = pow(min((x - g.mouthX) / span, 1), 1.3)
            return amp * e * sin((x - g.mouthX) / TapeMeasurePickerMotion.waveLength * .pi * 2 - phase)
        }

        var body = Path()
        var lipTop = Path()
        var lipBottom = Path()
        var xs: [Double] = []
        var x = x0
        while x <= x1 {
            xs.append(x)
            x += 6
        }
        let waves = xs.map(wave)
        for (i, x) in xs.enumerated() {
            let d = waves[i]
            if i == 0 {
                body.move(to: CGPoint(x: x, y: top + d))
                lipTop.move(to: CGPoint(x: x, y: top + 0.6 + d))
                lipBottom.move(to: CGPoint(x: x, y: top + g.tapeH - 0.6 + d))
            } else {
                body.addLine(to: CGPoint(x: x, y: top + d))
                lipTop.addLine(to: CGPoint(x: x, y: top + 0.6 + d))
                lipBottom.addLine(to: CGPoint(x: x, y: top + g.tapeH - 0.6 + d))
            }
        }
        for i in xs.indices.reversed() {
            body.addLine(to: CGPoint(x: xs[i], y: top + g.tapeH + waves[i]))
        }
        body.closeSubpath()

        blurred(c, 5).fill(body.offsetBy(dx: 0, dy: 6), with: .color(color(0x4A3410, 0.24)))
        blurred(c, 1.5).fill(body.offsetBy(dx: 0, dy: 1.5), with: .color(color(0x3A2808, 0.3)))
        c.fill(body, with: .linearGradient(tapeShading, startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: top + g.tapeH)))

        // Ticks: millimetres, half centimetres and centimetres on the top edge, a shorter row below.
        let ppcm = TapeMeasurePickerMotion.ppcm
        let cHi = v + (g.readX - x0) / ppcm
        let cLo = v - (x1 - g.readX) / ppcm
        var thin = Path()
        var thick = Path()
        let mStart = Int((max(0, cLo) * 10).rounded(.up))
        let mEnd = Int((cHi * 10).rounded(.down))
        if mStart <= mEnd {
            for m in mStart...mEnd {
                let x = g.readX + (v - Double(m) / 10) * ppcm
                let dy = wave(x)
                let cm = m % 10 == 0
                let len: Double = cm ? 22 : m % 5 == 0 ? 15 : 9
                let lenB: Double = cm ? 12 : m % 5 == 0 ? 9 : 5.5
                let seg = line(x, top + 1 + dy, x, top + 1 + len + dy)
                let segB = line(x, top + g.tapeH - 1 + dy, x, top + g.tapeH - 1 - lenB + dy)
                if cm {
                    thick.addPath(seg)
                    thick.addPath(segB)
                } else {
                    thin.addPath(seg)
                    thin.addPath(segB)
                }
            }
        }
        c.stroke(thin, with: .color(color(0x1D1A14, 0.82)), lineWidth: 0.8)
        c.stroke(thick, with: .color(color(0x1D1A14, 0.95)), lineWidth: 1.5)

        // A large number under every fifth centimetre line, every tenth in red (the value grows
        // toward the case); the centimetres between carry ticks only.
        var n = Int((cLo / 5).rounded(.up)) * 5
        while Double(n) <= cHi + 2 {
            if n > 0, let symbol = c.resolveSymbol(id: n) {
                let x = g.readX + (v - Double(n)) * ppcm
                let size: Double = n % 10 == 0 ? 19 : 17
                // drawText's baseline at top + 50: the line box's centre sits about 0.355 em above it.
                c.draw(symbol, at: CGPoint(x: x, y: top + 50 - size * 0.355 + wave(x)), anchor: .center)
            }
            n += 5
        }

        // Edges: a bright lip on top, a darker one below.
        c.stroke(lipTop, with: .color(color(0xFFF6C8, 0.75)), lineWidth: 0.9)
        c.stroke(lipBottom, with: .color(color(0x6E4C00, 0.55)), lineWidth: 0.9)
    }

    /// The red hairline that reads the value, with its little pointer tab.
    static func drawHairline(_ c: GraphicsContext, _ g: TapeMeasurePickerGeo) {
        let top = g.tapeY - g.tapeH / 2 - 18
        let bottom = g.tapeY + g.tapeH / 2 + 14
        let hair = line(g.readX, top + 6, g.readX, bottom)
        blurred(c, 2).stroke(hair, with: .color(color(0xE2342A, 0.28)), lineWidth: 1.4)
        c.stroke(hair, with: .color(color(0xD92B20)), lineWidth: 1.4)
        var tab = Path()
        tab.move(to: CGPoint(x: g.readX - 6, y: top - 3))
        tab.addLine(to: CGPoint(x: g.readX + 6, y: top - 3))
        tab.addLine(to: CGPoint(x: g.readX + 6, y: top + 2))
        tab.addLine(to: CGPoint(x: g.readX, y: top + 8))
        tab.addLine(to: CGPoint(x: g.readX - 6, y: top + 2))
        tab.closeSubpath()
        blurred(c, 1.5).fill(tab, with: .color(color(0x5A0C06, 0.3)))
        c.fill(tab, with: linear(0, top - 3, 0, top + 8, [(0xF05247, 1, 0), (0xD42A1F, 1, 0.5), (0xA81C13, 1, 1)]))
    }
}
