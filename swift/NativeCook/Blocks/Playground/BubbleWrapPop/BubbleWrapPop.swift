import SwiftUI
import UIKit

/// A sheet of bubble wrap over a soft pastel backdrop that shows through the clear film. Each
/// bubble is a glossy dome that magnifies the colours behind it. Pressed, a bubble squashes,
/// trembles and pops into a crumpled film with a burst of tiny air rings; a slide pops a whole row,
/// and the film's edge can be tugged. A rolling counter shows how many are popped; when none are
/// left a fresh sheet slides in.
struct BubbleWrapPop: View {
    /// Pops a smiley, sweeps the rest, and lets a fresh sheet slide in, on a loop.
    var autoPlay = false
    /// Called with the running count after each pop by hand.
    var onPop: ((Int, Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The sheet lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = BubbleWrapPopEngine()

    private typealias M = BubbleWrapPopMetrics
    private typealias S = BubbleWrapPopStyle

    var body: some View {
        // The block draws under the system bars and keeps its labels clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = BubbleWrapPopSettings(
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            width: size.width,
            top: insets.top + M.header,
            bottom: size.height - insets.bottom - M.footer,
            onPop: onPop
        )
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let _ = engine.step(to: timeline.date, settings: settings)
                let frame = engine.frameCount
                Canvas { context, canvasSize in
                    _ = frame
                    guard let sheet = engine.sheet else { return }
                    BubbleWrapPopPainter.draw(&context, sheet: sheet, size: canvasSize, elapsed: engine.elapsed, counterTop: insets.top)
                }
                .allowsHitTesting(false)
            }

            HStack {
                Text("POPPED")
                Spacer(minLength: 0)
                Text("BUBBLE WRAP · A4")
            }
            .font(.system(size: 11, weight: .bold))
            .kerning(2.4)
            .foregroundStyle(Color(hex: S.inkSoft))
            .padding(.horizontal, 24)
            .padding(.top, insets.top + 18)
            .allowsHitTesting(false)

            Text("Press to pop · Slide across a row · Tug the edge")
                .font(.system(size: 12, weight: .medium))
                .kerning(0.3)
                .foregroundStyle(Color(hex: S.inkSoft))
                .multilineTextAlignment(.center)
                .frame(width: max(0, size.width - 48))
                .frame(width: size.width, height: size.height - insets.bottom - 26, alignment: .bottom)
                .allowsHitTesting(false)

            BubbleWrapPopTouches(engine: engine)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Color(hex: 0xFBF3EA))
        .clipped()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Bubble wrap")
    }
}

enum BubbleWrapPopStyle {
    static let ink: UInt32 = 0x2A2433
    static let inkSoft: UInt32 = 0x7A6F86
    // The collapse from dome to flat film after a pop, s.
    static let collapse = 0.12
    // The air rings: `airRings` per pop, thrown at `airSpeed` pt/s and slowed with `airDrag` s.
    static let airRings = 7
    static let airSpeed = 170.0
    static let airDrag = 0.15
    static let shockTime = 0.24

    static func white(_ a: Double) -> Color { Color(.sRGB, red: 1, green: 1, blue: 1, opacity: max(0, min(1, a))) }
    static func plum(_ a: Double) -> Color { Color(.sRGB, red: 0.25, green: 0.16, blue: 0.36, opacity: max(0, min(1, a))) }

    static func hash(_ a: Double, _ b: Double) -> Double {
        let v = sin(a * 127.1 + b * 311.7) * 43758.5453
        return v - v.rounded(.down)
    }

    /// The counter's digits and its "/ total": Helvetica Neue at weight 400 (Regular).
    static let bigFont: UIFont = font(46, .regular)
    static let smallFont: UIFont = font(15, .regular)

    private static func font(_ size: CGFloat, _ weight: UIFont.Weight) -> UIFont {
        let descriptor = UIFontDescriptor(fontAttributes: [
            .family: "Helvetica Neue",
            .traits: [UIFontDescriptor.TraitKey.weight: weight],
        ])
        return UIFont(descriptor: descriptor, size: size)
    }

    /// The text's advance width, which is what Skia's measureText reports as its width.
    static func advance(_ text: String, _ font: UIFont) -> Double {
        let ctFont = font as CTFont
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(ctFont, chars, &glyphs, chars.count)
        return CTFontGetAdvancesForGlyphs(ctFont, .horizontal, glyphs, nil, glyphs.count)
    }
}

enum BubbleWrapPopPainter {
    private typealias S = BubbleWrapPopStyle
    private typealias P = BubbleWrapPopPhysics
    private typealias M = BubbleWrapPopMetrics

    private static func backdrop(_ res: CGSize, _ phase: Double, lens: (Double, Double, Double) = (0, 0, 1)) -> GraphicsContext.Shading {
        .shader(ShaderLibrary.bubbleWrapPopBackdrop(
            .float2(Float(res.width), Float(res.height)),
            .float(Float(phase)),
            .float3(Float(lens.0), Float(lens.1), Float(lens.2))
        ))
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    /// One dome as drawn: where it is, how squashed, which way it leans, how opaque.
    private struct Dome {
        var x: Double
        var y: Double
        var r: Double
        var squash: Double
        var leanX: Double
        var leanY: Double
        var alpha: Double
    }

    /// The whole scene, under the light camera shake.
    static func draw(_ context: inout GraphicsContext, sheet s: BubbleWrapPopSheet, size: CGSize, elapsed: Double, counterTop: Double) {
        let phase = elapsed.truncatingRemainder(dividingBy: M.loop) / M.loop * Double.pi * 2
        var scene = context
        scene.translateBy(x: s.shakeX, y: s.shakeY)
        // The backdrop overhangs the screen for the shake.
        scene.fill(Path(CGRect(x: -12, y: -12, width: size.width + 24, height: size.height + 24)), with: backdrop(size, phase))
        if s.swap > 0 {
            let e = M.easeInOut(s.swap)
            let away = size.width * 1.1
            sheet(&scene, s, size, phase, ox: -e * away, oy: 0, look: 1)
            sheet(&scene, s, size, phase, ox: (1 - e) * away, oy: 0, look: 2)
        } else {
            sheet(&scene, s, size, phase, ox: s.tugX, oy: s.tugY, look: 0)
        }
        counter(&scene, s, x: 24, baseline: counterTop + 92)
    }

    /// The sheet: 1 draws every bubble popped long ago, 2 every bubble whole; 0 the sheet as it is.
    private static func sheet(_ context: inout GraphicsContext, _ s: BubbleWrapPopSheet, _ size: CGSize, _ phase: Double, ox: Double, oy: Double, look: Int) {
        let l = s.left + ox
        let t = s.top + oy
        let w = s.right - s.left
        let h = s.bottom - s.top
        let rect = CGRect(x: l, y: t, width: w, height: h)
        let rounded = Path(roundedRect: rect, cornerRadius: M.sheetRadius)
        context.drawLayer { shadow in
            shadow.addFilter(.blur(radius: 20))
            shadow.translateBy(x: 0, y: 14)
            shadow.fill(rounded, with: .color(S.plum(0.16)))
        }
        // Clear film over the backdrop: a little whitening, the backdrop redrawn so the shadow above
        // only shows around the edges.
        var film = context
        film.clip(to: rounded)
        film.fill(Path(rect), with: backdrop(size, phase))
        let r = s.radius

        // Under every intact dome: its shadow and the light it focuses onto what is behind it.
        // Bubbles never overlap, so each kind is blurred in one layer for the whole sheet.
        var under: [(x: Double, y: Double, squash: Double)] = []
        for i in 0..<s.count {
            let popped = look == 1 || (look == 0 && s.popped[i])
            if !popped { under.append((l + s.bx[i], t + s.by[i], look == 0 ? M.clamp01(s.pressure[i]) : 0)) }
        }
        film.drawLayer { layer in
            layer.addFilter(.blur(radius: 3.5))
            for u in under {
                layer.fill(circle(u.x + r * 0.12, u.y + r * 0.2, r * (0.96 + u.squash * 0.12)), with: .color(S.plum(0.13 * (1 - u.squash * 0.5))))
            }
        }
        film.drawLayer { layer in
            layer.addFilter(.blur(radius: 4))
            for u in under {
                layer.fill(Path(ellipseIn: CGRect(x: u.x + r * 0.05, y: u.y + r * 0.32, width: r * 0.7, height: r * 0.34)), with: .color(S.white(0.42 * (1 - u.squash))))
            }
        }
        film.fill(Path(rect), with: .linearGradient(
            Gradient(stops: [.init(color: S.white(0.2), location: 0), .init(color: S.white(0.1), location: 0.55), .init(color: S.white(0.18), location: 1)]),
            startPoint: CGPoint(x: l, y: t),
            endPoint: CGPoint(x: l + w, y: t + h)
        ))
        film.fill(Path(rect), with: .shader(ShaderLibrary.bubbleWrapPopCrinkle(.float(0.13), .float2(Float(l), Float(t)))))
        // A broad sheen across the film that slides as the film is tugged.
        let sx = l + w * 0.3 + s.tugX * 2.2
        film.fill(Path(rect), with: .linearGradient(
            Gradient(colors: [S.white(0), S.white(0.16), S.white(0)]),
            startPoint: CGPoint(x: sx - 90, y: t),
            endPoint: CGPoint(x: sx + 90, y: t + h * 0.6)
        ))

        var domes: [Dome] = []
        var wrinkles: [(x: Double, y: Double, seed: Double, k: Double)] = []
        for i in 0..<s.count {
            let x = l + s.bx[i]
            let y = t + s.by[i]
            if look == 2 || (look == 0 && !s.popped[i]) {
                var squash = look == 0 ? M.clamp01(s.pressure[i]) : 0
                // Close to bursting it trembles.
                if squash > 0.7 && !s.calm { squash += sin(s.time * 95 + Double(i)) * 0.04 * ((squash - 0.7) / 0.3) }
                domes.append(Dome(x: x, y: y, r: r, squash: squash, leanX: s.leanX[i], leanY: s.leanY[i], alpha: 1))
                continue
            }
            let age = look == 1 ? 10 : s.popAge[i]
            let k = M.clamp01(age / S.collapse)
            if k < 1 { domes.append(Dome(x: x, y: y, r: r * (1 - 0.25 * k), squash: 1, leanX: s.leanX[i], leanY: s.leanY[i], alpha: 1 - k)) }
            wrinkles.append((x, y, Double(i) * 1.7 + 0.3, k))
        }
        drawDomes(&film, domes, size, phase)
        for wr in wrinkles { wrinkle(&film, wr.x, wr.y, r, seed: wr.seed, alpha: wr.k) }

        // The film's edge.
        film.stroke(
            Path(roundedRect: rect.insetBy(dx: 0.6, dy: 0.6), cornerRadius: M.sheetRadius - 0.6),
            with: .linearGradient(Gradient(colors: [S.white(0.85), S.white(0.35)]), startPoint: CGPoint(x: l, y: t), endPoint: CGPoint(x: l, y: t + h)),
            lineWidth: 1.2
        )
        // Bursts fly past the film's edge.
        if look == 0 {
            for i in 0..<s.count where s.popped[i] && s.popAge[i] >= 0 && s.popAge[i] < P.popLife {
                burst(&context, l + s.bx[i], t + s.by[i], r, age: s.popAge[i], seed: Double(i) * 3.3 + 1)
            }
        }
    }

    private struct DomeShape {
        var cx: Double
        var cy: Double
        var rx: Double
        var ry: Double
        var height: Double
    }

    private static func shape(_ d: Dome) -> DomeShape {
        // Squashed, the dome flattens and bulges out, away from the finger.
        let height = 1 - 0.6 * d.squash
        let grow = 1 + 0.17 * d.squash
        return DomeShape(
            cx: d.x - d.leanX * d.r * 0.14 * d.squash,
            cy: d.y - d.leanY * d.r * 0.14 * d.squash,
            rx: d.r * grow * (1 + abs(d.leanY) * 0.05 * d.squash),
            ry: d.r * grow * (1 + abs(d.leanX) * 0.05 * d.squash),
            height: height
        )
    }

    private static func drawDomes(_ context: inout GraphicsContext, _ domes: [Dome], _ size: CGSize, _ phase: Double) {
        for d in domes {
            let g = shape(d)
            let oval = Path(ellipseIn: CGRect(x: g.cx - g.rx, y: g.cy - g.ry, width: g.rx * 2, height: g.ry * 2))
            let a = d.alpha
            // Through the dome the backdrop is magnified and pushed toward the light.
            var lens = context
            lens.opacity = a
            lens.fill(oval, with: backdrop(size, phase, lens: (g.cx + d.r * 0.18, g.cy + d.r * 0.22, 1 + 0.26 * g.height)))
            // The plastic is thicker toward the rim and catches more light there.
            context.fill(oval, with: .radialGradient(
                Gradient(stops: [
                    .init(color: S.white(0.03 * a), location: 0),
                    .init(color: S.white(0.06 * a), location: 0.6),
                    .init(color: S.white(0.34 * a), location: 0.93),
                    .init(color: S.white(0.1 * a), location: 1),
                ]),
                center: CGPoint(x: g.cx, y: g.cy),
                startRadius: 0,
                endRadius: max(g.rx, g.ry)
            ))
            // Rim: lit on the upper left, shadowed on the lower right.
            context.stroke(
                Path(ellipseIn: CGRect(x: g.cx - g.rx + 0.6, y: g.cy - g.ry + 0.6, width: g.rx * 2 - 1.2, height: g.ry * 2 - 1.2)),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: S.white(0.75 * a), location: 0),
                        .init(color: S.white(0.12 * a), location: 0.6),
                        .init(color: S.plum(0.16 * a), location: 1),
                    ]),
                    startPoint: CGPoint(x: g.cx - g.rx, y: g.cy - g.ry),
                    endPoint: CGPoint(x: g.cx + g.rx, y: g.cy + g.ry)
                ),
                lineWidth: 1.1
            )
        }
        // Pressure shows as a tight bright band along the rim.
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.4))
            for d in domes where d.squash > 0.02 {
                let g = shape(d)
                layer.stroke(
                    Path(ellipseIn: CGRect(x: g.cx - g.rx + 2, y: g.cy - g.ry + 2, width: g.rx * 2 - 4, height: g.ry * 2 - 4)),
                    with: .color(S.white(0.4 * d.squash * d.alpha)),
                    lineWidth: 2.2
                )
            }
        }
        // Specular: a sharp window on the upper left that flattens and slides as the dome squashes.
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 0.9))
            for d in domes {
                let g = shape(d)
                var c = layer
                c.translateBy(x: g.cx - g.rx * (0.36 + 0.12 * d.squash), y: g.cy - g.ry * (0.4 - 0.1 * d.squash))
                c.rotate(by: .degrees(-36))
                let sw = d.r * (0.42 + 0.2 * d.squash)
                let shh = d.r * 0.2 * g.height
                c.fill(Path(ellipseIn: CGRect(x: -sw / 2, y: -shh / 2, width: sw, height: shh)), with: .color(S.white(0.9 * d.alpha * (0.55 + 0.45 * g.height))))
            }
        }
        // And a small glint low on the right.
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.2))
            for d in domes {
                let g = shape(d)
                layer.fill(circle(g.cx + g.rx * 0.4, g.cy + g.ry * 0.42, d.r * 0.08), with: .color(S.white(0.55 * d.alpha * g.height)))
            }
        }
    }

    /// A popped bubble: the film it leaves, crumpled flat with a few creases.
    private static func wrinkle(_ context: inout GraphicsContext, _ x: Double, _ y: Double, _ r: Double, seed: Double, alpha: Double) {
        if alpha < 0.01 { return }
        // A soft, lumpy outline: two seeded waves around the circle, joined with smooth curves.
        let n = 18
        let ph1 = S.hash(seed, 1) * Double.pi * 2
        let ph2 = S.hash(seed, 2) * Double.pi * 2
        var px: [Double] = []
        var py: [Double] = []
        for i in 0..<n {
            let a = Double(i) / Double(n) * Double.pi * 2
            let rr = r * (0.86 + 0.07 * sin(a * 3 + ph1) + 0.05 * sin(a * 5 + ph2) + 0.03 * (S.hash(Double(i), seed) - 0.5))
            px.append(x + cos(a) * rr)
            py.append(y + sin(a) * rr)
        }
        var film = Path()
        film.move(to: CGPoint(x: (px[n - 1] + px[0]) / 2, y: (py[n - 1] + py[0]) / 2))
        for i in 0..<n {
            let j = (i + 1) % n
            film.addQuadCurve(to: CGPoint(x: (px[i] + px[j]) / 2, y: (py[i] + py[j]) / 2), control: CGPoint(x: px[i], y: py[i]))
        }
        film.closeSubpath()
        context.fill(film, with: .radialGradient(
            Gradient(colors: [S.white(0.34 * alpha), S.white(0.12 * alpha)]),
            center: CGPoint(x: x - r * 0.2, y: y - r * 0.25),
            startRadius: 0,
            endRadius: r * 1.2
        ))
        context.stroke(film, with: .color(S.white(0.7 * alpha)), style: StrokeStyle(lineWidth: 0.9, lineJoin: .round))
        // Creases: a light line with a faint shadow beside it.
        var lines = Path()
        for k in 0..<5 {
            let kd = Double(k)
            let a = S.hash(seed + kd, 3.1) * Double.pi * 2
            let from = r * (0.05 + 0.25 * S.hash(kd, seed + 1))
            let to = r * (0.5 + 0.3 * S.hash(seed + 2, kd))
            let bend = (S.hash(seed, kd + 9) - 0.5) * 0.5
            lines.move(to: CGPoint(x: x + cos(a) * from, y: y + sin(a) * from))
            lines.addLine(to: CGPoint(x: x + cos(a + bend) * (from + to) * 0.55, y: y + sin(a + bend) * (from + to) * 0.55))
            lines.addLine(to: CGPoint(x: x + cos(a) * to, y: y + sin(a) * to))
        }
        let ink = StrokeStyle(lineWidth: 0.8, lineCap: .round, lineJoin: .round)
        var shadow = context
        shadow.translateBy(x: 0.6, y: 0.7)
        shadow.stroke(lines, with: .color(S.plum(0.14 * alpha)), style: ink)
        context.stroke(lines, with: .color(S.white(0.85 * alpha)), style: ink)
    }

    /// The burst: a shock ring and small rings of air thrown out and fading.
    private static func burst(_ context: inout GraphicsContext, _ x: Double, _ y: Double, _ r: Double, age: Double, seed: Double) {
        let k = age / S.shockTime
        if k < 1 {
            let e = 1 - (1 - k) * (1 - k)
            context.stroke(circle(x, y, r * (0.85 + 1.25 * e)), with: .color(S.white(0.7 * (1 - k))), lineWidth: 2.4 * (1 - k) + 0.4)
        }
        let life = M.clamp01(age / P.popLife)
        if life >= 1 { return }
        let fade = pow(1 - life, 1.4)
        for j in 0..<S.airRings {
            let jd = Double(j)
            let a = jd / Double(S.airRings) * Double.pi * 2 + S.hash(seed, jd) * 0.8
            let v = S.airSpeed * (0.6 + 0.8 * S.hash(jd, seed))
            let d = r * 0.7 + v * S.airDrag * (1 - exp(-age / S.airDrag))
            let rr = 1.2 + 3.2 * life * (0.6 + 0.6 * S.hash(seed + jd, 2))
            context.stroke(circle(x + cos(a) * d, y + sin(a) * d - 10 * life, rr), with: .color(S.white(0.85 * fade)), lineWidth: 1.1)
        }
    }

    /// Draws `text` with its baseline at `baseline`, like Skia's drawText.
    private static func text(_ context: inout GraphicsContext, _ string: String, _ font: UIFont, _ color: UInt32, x: Double, baseline: Double) {
        let resolved = context.resolve(Text(string).font(Font(font as CTFont)).foregroundColor(Color(hex: color)))
        let base = resolved.firstBaseline(in: CGSize(width: 1000, height: 1000))
        context.draw(resolved, at: CGPoint(x: x, y: baseline - base), anchor: .topLeading)
    }

    /// The count, as an odometer: each digit rolls through to the next.
    private static func counter(_ context: inout GraphicsContext, _ s: BubbleWrapPopSheet, x: Double, baseline: Double) {
        let cell = S.advance("0", S.bigFont)
        let lineH = 50.0
        let shown = max(0, s.shown)
        let places = s.count >= 100 ? 3 : 2
        var digits = context
        digits.clip(to: Path(CGRect(x: x - 4, y: baseline - lineH + 6, width: cell * Double(places) + 8, height: lineH + 2)))
        for place in 0..<places {
            let unit = pow(10, Double(places - 1 - place))
            // A higher digit turns only while the digit below rolls from 9 to 10.
            let below = shown.truncatingRemainder(dividingBy: unit)
            let pos = (shown / unit).rounded(.down) + (unit > 1 ? max(0, below - (unit - 1)) : 0)
            let whole = pos.rounded(.down)
            let frac = pos - whole
            for k in 0..<2 {
                let digit = ((Int(whole) + k) % 10 + 10) % 10
                let y = baseline - (frac - Double(k)) * lineH
                let glyph = "\(digit)"
                let gw = S.advance(glyph, S.bigFont)
                text(&digits, glyph, S.bigFont, S.ink, x: x + Double(place) * cell + (cell - gw) / 2, baseline: y)
            }
        }
        text(&context, "/ \(s.count)", S.smallFont, S.inkSoft, x: x + cell * Double(places) + 10, baseline: baseline)
    }
}
