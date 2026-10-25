import SwiftUI
import UIKit

/*
 Drawing for Boba Pearl Progress, a port of bobaCup.ts and bobaPanel.ts.

 One clear plastic cup, drawn in its own frame (origin at the middle of its base, y down on
 screen, heights negative): back rim, milk tea with a waving surface and brown-sugar streaks, the
 pearls (one pre-rendered sprite drawn 40 times), the tea's tint over them, foam, the pour, the
 sealing film and straw, then the front of the glass (highlights, rim, condensation). Around it:
 the warm counter backdrop (a Canvas drawn once) and, per frame, the percentage, the status line
 and the four-step tracker.
 */
enum BobaPearlProgressArt {
    typealias Geo = BobaPearlProgressGeo
    private typealias M = BobaPearlProgressMotion

    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    static let ink = rgb(0x3B2418)
    static let inkSoft = rgb(0x8C6E5C)
    static let background = rgb(0xF4E2D0)
    /// Where each step sits on the track, and the percentage it starts at.
    static let stepAt: [Double] = [0, 60, 82, 100]

    static let pctFont = Font.system(size: 40, weight: .bold)
    static let statusFont = Font.system(size: 16, weight: .semibold)
    static let etaFont = Font.system(size: 13, weight: .medium)
    static let stepFont = Font.system(size: 12, weight: .semibold)

    private static let sprite = 96.0
    private static let spriteR = 44.0

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func round(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    /// An arc of the ellipse inscribed in `rect`, from `start` sweeping `sweep` degrees (Skia's
    /// convention: 0° = +x, 90° = +y, i.e. down), as line segments.
    private static func ellipseArc(_ path: inout Path, _ rect: CGRect, _ start: Double, _ sweep: Double, move: Bool) {
        let cx = rect.midX
        let cy = rect.midY
        let rx = rect.width / 2
        let ry = rect.height / 2
        let n = 32
        for i in 0...n {
            let a = (start + sweep * Double(i) / Double(n)) * Double.pi / 180
            let p = CGPoint(x: cx + cos(a) * rx, y: cy + sin(a) * ry)
            if i == 0 && move { path.move(to: p) } else { path.addLine(to: p) }
        }
    }

    private static let unbounded = CGSize(width: 10_000, height: 1_000)

    private static func text(_ c: GraphicsContext, _ string: String, font: Font, color: Color, x: Double, baseline y: Double, trailing: Bool = false) {
        let resolved = c.resolve(Text(string).font(font).foregroundStyle(color))
        let size = resolved.measure(in: unbounded)
        let base = resolved.firstBaseline(in: unbounded)
        let left = trailing ? x - size.width : x
        c.draw(resolved, in: CGRect(x: left, y: y - base, width: size.width + 1, height: size.height))
    }

    private static func width(_ c: GraphicsContext, _ string: String, font: Font) -> Double {
        c.resolve(Text(string).font(font)).measure(in: unbounded).width
    }

    // MARK: - Drawn once

    @MainActor
    private static func render(width: Double, height: Double, scale: Double, _ draw: @escaping (GraphicsContext) -> Void) -> Image {
        let renderer = ImageRenderer(content:
            Canvas { c, _ in draw(c) }
                .frame(width: width, height: height)
        )
        renderer.scale = scale
        renderer.isOpaque = false
        guard let ui = renderer.uiImage else { return Image(systemName: "circle").resizable() }
        return Image(uiImage: ui.preparingForDisplay() ?? ui)
    }

    /// The pre-rendered images a frame draws: two pearls (front, and a darker one for the back
    /// layer), and the cup's soft shadows on the table.
    struct Sprites {
        let front: Image
        let back: Image
        let shadow: Image
        let glow: Image
        /// The shadow images' frames in the cup's frame.
        let shadowRect: CGRect
        let glowRect: CGRect
    }

    @MainActor private static var spriteCache: (geo: Geo, scale: Double, sprites: Sprites)?

    @MainActor
    static func sprites(_ g: Geo, scale: Double) -> Sprites {
        if let spriteCache, spriteCache.geo == g, spriteCache.scale == scale { return spriteCache.sprites }
        func pearl(_ k: Int) -> Image {
            render(width: sprite, height: sprite, scale: scale) { c in
                let cx = sprite / 2
                let cy = sprite / 2
                let stops: [UInt32] = k == 0 ? [0x6B3A22, 0x3E1F11, 0x1F0E07] : [0x4E2918, 0x2C150B, 0x150904]
                c.fill(circle(cx, cy, spriteR), with: .radialGradient(
                    Gradient(stops: [
                        .init(color: rgb(stops[0]), location: 0),
                        .init(color: rgb(stops[1]), location: 0.5),
                        .init(color: rgb(stops[2]), location: 1),
                    ]),
                    center: CGPoint(x: cx - 12, y: cy - 14),
                    startRadius: 0,
                    endRadius: spriteR * 1.35
                ))
                // Translucent rim, a warm bounce at the bottom right, and a sharp window highlight.
                c.stroke(circle(cx, cy, spriteR - 1.5), with: .color(rgb(0x965430, 0.35)), lineWidth: 3)
                c.drawLayer { l in
                    l.addFilter(.blur(radius: 5))
                    l.fill(circle(cx + 14, cy + 16, 12), with: .color(rgb(0xD68C5A, 0.28)))
                }
                c.drawLayer { l in
                    l.addFilter(.blur(radius: 4))
                    l.fill(Path(ellipseIn: CGRect(x: cx - 26, y: cy - 30, width: 26, height: 16)), with: .color(rgb(0xFFF0E1, 0.45)))
                }
                c.fill(Path(ellipseIn: CGRect(x: cx - 21, y: cy - 26, width: 12, height: 7)), with: .color(rgb(0xFFFAF4, k == 0 ? 0.9 : 0.6)))
            }
        }
        let u = g.u
        // Shadow on the table, with room for its blur.
        let pad = 30.0
        let shadowRect = CGRect(x: -g.baseW * 1.25 + 8 * u - pad, y: -4 * u - pad, width: g.baseW * 2.5 + 2 * pad, height: 18 * u + 2 * pad)
        let shadow = render(width: shadowRect.width, height: shadowRect.height, scale: scale) { c in
            var c = c
            c.translateBy(x: -shadowRect.minX, y: -shadowRect.minY)
            c.drawLayer { l in
                l.addFilter(.blur(radius: 9))
                l.fill(Path(ellipseIn: CGRect(x: -g.baseW * 1.25 + 8 * u, y: -4 * u, width: g.baseW * 2.5, height: 18 * u)), with: .color(rgb(0x5C321C, 0.20)))
            }
            c.drawLayer { l in
                l.addFilter(.blur(radius: 2))
                l.fill(Path(ellipseIn: CGRect(x: -g.baseW * 1.02, y: -3 * u, width: g.baseW * 2.04, height: 7 * u)), with: .color(rgb(0x5C321C, 0.25)))
            }
        }
        let glowRect = CGRect(x: g.baseW * 0.2 - pad, y: -pad, width: g.baseW * 1.4 + 2 * pad, height: 16 * u + 2 * pad)
        let glow = render(width: glowRect.width, height: glowRect.height, scale: scale) { c in
            var c = c
            c.translateBy(x: -glowRect.minX, y: -glowRect.minY)
            c.drawLayer { l in
                l.addFilter(.blur(radius: 10))
                l.fill(Path(ellipseIn: CGRect(x: g.baseW * 0.2, y: 0, width: g.baseW * 1.4, height: 16 * u)), with: .color(rgb(0xD68C46, 1)))
            }
        }
        let s = Sprites(front: pearl(0), back: pearl(1), shadow: shadow, glow: glow, shadowRect: shadowRect, glowRect: glowRect)
        spriteCache = (g, scale, s)
        return s
    }

    /// A tile of fine grey noise, made once, for the backdrop's grain.
    private static let grain: Image = {
        let side = 128
        var state: UInt32 = 0x2545F491
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { ctx in
            for y in 0..<side {
                for x in 0..<side {
                    state ^= state << 13
                    state ^= state >> 17
                    state ^= state << 5
                    ctx.cgContext.setFillColor(UIColor(white: CGFloat(state & 0xFF) / 255, alpha: 1).cgColor)
                    ctx.cgContext.fill(CGRect(x: x, y: y, width: 1, height: 1))
                }
            }
        }
        return Image(uiImage: image)
    }()

    static func drawBackdrop(_ c: GraphicsContext, _ g: Geo) {
        let all = Path(CGRect(x: 0, y: 0, width: g.w, height: g.h))
        c.fill(all, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0xF8EBDD), location: 0),
                .init(color: rgb(0xF2DCC6), location: 0.55),
                .init(color: rgb(0xE9C6AA), location: 1),
            ]),
            startPoint: .zero,
            endPoint: CGPoint(x: 0, y: g.h)
        ))
        // A soft light behind the cup.
        c.fill(all, with: .radialGradient(
            Gradient(stops: [
                .init(color: rgb(0xFFFAF2, 0.75), location: 0),
                .init(color: rgb(0xFFF6EC, 0.25), location: 0.5),
                .init(color: rgb(0xFFF6EC, 0), location: 1),
            ]),
            center: CGPoint(x: g.cx, y: g.top + g.cupH * 0.45),
            startRadius: 0,
            endRadius: g.cupH * 0.85
        ))
        // The counter's edge behind the cup's base.
        c.fill(Path(CGRect(x: 0, y: g.base - 26 * g.u, width: g.w, height: 66 * g.u)), with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0x96603E, 0), location: 0),
                .init(color: rgb(0x96603E, 0.10), location: 0.45),
                .init(color: rgb(0x96603E, 0), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: g.base - 26 * g.u),
            endPoint: CGPoint(x: 0, y: g.base + 40 * g.u)
        ))
        var grainLayer = c
        grainLayer.opacity = 0.045
        grainLayer.fill(all, with: .tiledImage(grain, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 0.5))
    }

    // MARK: - The cup, per frame

    private static func cupPath(_ g: Geo, _ inset: Double) -> Path {
        let rw = g.rimW - inset
        let bw = g.baseW - inset
        var p = Path()
        p.move(to: CGPoint(x: -rw, y: -g.cupH))
        p.addLine(to: CGPoint(x: -bw, y: 0))
        ellipseArc(&p, CGRect(x: -bw, y: -g.baseRy, width: bw * 2, height: g.baseRy * 2), 180, -180, move: false)
        p.addLine(to: CGPoint(x: rw, y: -g.cupH))
        p.closeSubpath()
        return p
    }

    private static func surfaceY(_ x: Double, _ lvl: Double, _ tilt: Double, _ amp: Double, _ clock: Double, _ u: Double) -> Double {
        let w = (2 * Double.pi * clock) / M.loop
        return -lvl + tan(tilt) * x + amp * (sin((x * 0.09) / u + w * 6) * 0.6 + sin((x * 0.05) / u - w * 4 + 1.3) * 0.4)
    }

    static func drawCup(_ c: GraphicsContext, _ g: Geo, _ cup: BobaPearlProgressCup, _ pearls: [BobaPearlProgressMotion.Pearl], _ sp: Sprites, clock: Double) {
        let u = g.u
        let tc = cup.tc
        let lvl = M.level(g, tc)
        let (pourIn, pourOut) = M.pour(tc)
        let pouring = pourIn > 0 && pourOut < 1
        var c = c
        c.translateBy(x: g.cx + cup.dx, y: g.base)

        // Shadow on the table (outside the rotation), with a little caramel light through the tea.
        c.draw(sp.shadow, in: sp.shadowRect)
        if lvl > 1 {
            var gc = c
            gc.opacity = 0.16 * (lvl / g.maxLevel)
            gc.draw(sp.glow, in: sp.glowRect)
        }

        c.rotate(by: .radians(cup.rot))
        let outer = cupPath(g, 0)
        let inner = cupPath(g, 3 * u)
        // Back of the cup: a faint fill and the far half of the rim.
        c.fill(outer, with: .color(Color.white.opacity(0.13)))
        let rimRect = CGRect(x: -g.rimW, y: -g.cupH - g.rimRy, width: g.rimW * 2, height: g.rimRy * 2)
        var backRim = Path()
        ellipseArc(&backRim, rimRect, 180, 180, move: true)
        c.stroke(backRim, with: .color(Color.white.opacity(0.45)), style: round(1.5))

        var inside = c
        inside.clip(to: inner)
        let amp = (1.2 + 3 * (pouring ? 1 : 0) + 4 * cup.shake) * u
        var body: Path?
        if lvl > 0.5 {
            var bb = Path()
            let span = g.rimW + 14 * u
            bb.move(to: CGPoint(x: -span, y: surfaceY(-span, lvl, cup.tilt, amp, clock, u)))
            for i in 1...16 {
                let x = -span + Double(i) * span * 2 / 16
                bb.addLine(to: CGPoint(x: x, y: surfaceY(x, lvl, cup.tilt, amp, clock, u)))
            }
            bb.addLine(to: CGPoint(x: span, y: 30 * u))
            bb.addLine(to: CGPoint(x: -span, y: 30 * u))
            bb.closeSubpath()
            body = bb
            inside.fill(bb, with: .linearGradient(
                Gradient(stops: [
                    .init(color: rgb(0xD3A273), location: 0),
                    .init(color: rgb(0xBC8455), location: 0.5),
                    .init(color: rgb(0x94592F), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: -lvl),
                endPoint: .zero
            ))
            // Brown-sugar streaks climbing the walls.
            var streaks = Path()
            for side in [-1.0, 1.0] {
                for k in 0..<2 {
                    for j in 0...8 {
                        let y = lvl * (0.95 - Double(k) * 0.3) * Double(j) / 8
                        let x = side * (M.halfWidth(g, y) - (4 + Double(k) * 16) * u - 9 * u * sin(Double(j) * 0.9 + Double(k) * 2.1 + side))
                        if j == 0 { streaks.move(to: CGPoint(x: x, y: -y)) } else { streaks.addLine(to: CGPoint(x: x, y: -y)) }
                    }
                }
            }
            var streakLayer = inside
            streakLayer.clip(to: bb)
            streakLayer.drawLayer { l in
                l.addFilter(.blur(radius: 3.2 * u))
                l.stroke(streaks, with: .color(rgb(0x683414, 0.38)), style: StrokeStyle(lineWidth: 9 * u, lineCap: .round))
            }
        }

        // Pearls: one sprite each, resolved once.
        let front = inside.resolve(sp.front)
        let back = inside.resolve(sp.back)
        for (i, p) in pearls.enumerated() {
            let (px, py) = M.pearlAt(g, p, tc)
            if py < 0 { continue }
            let settled = max(0, 1 - py / (g.pearlR * 10))
            let jig = cup.shake * g.pearlR * 0.35
            let jx = sin(cup.phase * 31 + Double(i) * 1.7) * jig
            let jy = abs(sin(cup.phase * 27 + Double(i) * 2.3)) * jig
            let hw = M.halfWidth(g, py) - p.r
            let x = max(-hw, min(hw, px + cup.roll * settled * (0.6 + 0.4 * (py / (g.pearlR * 6))) + jx))
            let s = p.r / spriteR
            let rect = CGRect(x: x - s * sprite / 2, y: -py - jy - s * sprite / 2, width: sprite * s, height: sprite * s)
            inside.draw(p.back ? back : front, in: rect)
        }

        if let body {
            inside.fill(body, with: .color(rgb(0xC48C5C, 0.30)))
            // Foam from the shake, then the surface seen from a little above.
            let f = M.foam(tc)
            let rx = M.halfWidth(g, lvl)
            if f > 0 {
                inside.fill(Path(CGRect(x: -g.rimW, y: -lvl, width: g.rimW * 2, height: 12 * u * f)), with: .color(rgb(0xF0DEC4, 0.85)))
            }
            var top = inside
            top.translateBy(x: 0, y: surfaceY(0, lvl, cup.tilt, amp, clock, u))
            top.rotate(by: .radians(cup.tilt))
            top.fill(
                Path(ellipseIn: CGRect(x: -rx, y: -rx * 0.13, width: rx * 2, height: rx * 0.26)),
                with: .linearGradient(
                    Gradient(colors: f > 0.5 ? [rgb(0xF3E4CE), rgb(0xE6CFAE)] : [rgb(0xDDB58C), rgb(0xC99B6E)]),
                    startPoint: CGPoint(x: -rx, y: 0),
                    endPoint: CGPoint(x: rx, y: 0)
                )
            )
            top.fill(Path(ellipseIn: CGRect(x: -rx * 0.7, y: -rx * 0.09, width: rx * 0.9, height: rx * 0.07)), with: .color(rgb(0xFFF8EC, 0.35)))
        }

        // The pour, from a spout above the frame.
        if pouring {
            let topY = -g.cupH - 170 * u
            let surf = -lvl
            let y0 = topY + (surf - topY) * pourOut
            let y1 = topY + (surf - topY) * pourIn
            if y1 > y0 + 1 {
                var stream = Path()
                let steps = 14
                let phase = clock * 2 * Double.pi * 9 / M.loop
                for i in 0...steps {
                    let y = y0 + (y1 - y0) * Double(i) / Double(steps)
                    let x = -16 * u + 1.6 * u * sin(y * 0.06 + phase) - 3.5 * u
                    if i == 0 { stream.move(to: CGPoint(x: x, y: y)) } else { stream.addLine(to: CGPoint(x: x, y: y)) }
                }
                for i in stride(from: steps, through: 0, by: -1) {
                    let y = y0 + (y1 - y0) * Double(i) / Double(steps)
                    let x = -16 * u + 1.6 * u * sin(y * 0.06 + phase) + 3.5 * u + (y - topY) * 0.004
                    stream.addLine(to: CGPoint(x: x, y: y))
                }
                stream.closeSubpath()
                c.fill(stream, with: .linearGradient(
                    Gradient(stops: [
                        .init(color: rgb(0xA86E42), location: 0),
                        .init(color: rgb(0xE2B68A), location: 0.4),
                        .init(color: rgb(0xB57B4D), location: 1),
                    ]),
                    startPoint: CGPoint(x: -21 * u, y: 0),
                    endPoint: CGPoint(x: -11 * u, y: 0)
                ))
                if pourIn >= 1 && pourOut <= 0 {
                    c.fill(Path(ellipseIn: CGRect(x: -28 * u, y: surf - 4 * u, width: 24 * u, height: 8 * u)), with: .color(rgb(0xF0D6B6, 0.9)))
                }
            }
        }

        // Seal film and straw.
        let (drop, squash) = M.seal(tc)
        let (come, pop) = M.straw(tc)
        if drop > 0 {
            var fc = c
            fc.translateBy(x: 0, y: -g.cupH - (1 - drop) * 40 * u)
            fc.scaleBy(x: 1, y: 1 - 0.08 * squash)
            let show = min(1, drop * 3)
            fc.fill(Path(ellipseIn: CGRect(x: -g.rimW - 2 * u, y: -g.rimRy - 1 * u, width: g.rimW * 2 + 4 * u, height: g.rimRy * 2 + 2 * u)), with: .color(rgb(0xFCF0E2, show * 0.66)))
            fc.stroke(Path(ellipseIn: CGRect(x: -g.rimW * 0.5, y: -g.rimRy * 0.5, width: g.rimW, height: g.rimRy)), with: .color(rgb(0xD66078, show * 0.7)), lineWidth: 1.6 * u)
            fc.fill(Path(ellipseIn: CGRect(x: -g.rimW * 0.75, y: -g.rimRy * 0.55, width: g.rimW * 0.6, height: g.rimRy * 0.35)), with: .color(Color.white.opacity(0.35)))
            if pop > 0 && pop < 1 {
                fc.stroke(
                    Path(ellipseIn: CGRect(x: 33 * u - (4 + 16 * pop) * u, y: -(2 + 5 * pop) * u, width: (8 + 32 * pop) * u, height: (4 + 10 * pop) * u)),
                    with: .color(Color.white.opacity(1 - pop)),
                    lineWidth: 1.2 * u
                )
            }
        }

        if come > 0 {
            var sc = c
            sc.translateBy(x: 0, y: -(1 - come) * 170 * u)
            let sx0 = 12 * u
            let sx1 = 40 * u
            var strawPath = Path()
            strawPath.move(to: CGPoint(x: sx0 - 7 * u, y: -12 * u))
            strawPath.addLine(to: CGPoint(x: sx1 - 7 * u, y: -g.cupH - 104 * u))
            strawPath.addLine(to: CGPoint(x: sx1 + 7 * u, y: -g.cupH - 104 * u))
            strawPath.addLine(to: CGPoint(x: sx0 + 7 * u, y: -12 * u))
            strawPath.closeSubpath()
            let shading = GraphicsContext.Shading.linearGradient(
                Gradient(stops: [
                    .init(color: rgb(0xE58FA2), location: 0),
                    .init(color: rgb(0xFBD3DB), location: 0.35),
                    .init(color: rgb(0xE4899D), location: 1),
                ]),
                startPoint: CGPoint(x: sx1 - 7 * u, y: 0),
                endPoint: CGPoint(x: sx1 + 7 * u, y: 0)
            )
            // Above the film it is solid; inside the cup it shows dimly through the tea.
            let filmY = -g.cupH + (1 - come) * 170 * u
            var above = sc
            above.clip(to: Path(CGRect(x: -g.w, y: filmY - g.h, width: g.w * 2, height: g.h)))
            above.fill(strawPath, with: shading)
            var below = sc
            below.clip(to: Path(CGRect(x: -g.w, y: filmY, width: g.w * 2, height: g.h)))
            below.opacity = 0.38
            below.fill(strawPath, with: shading)
        }

        // Front of the glass: highlights, rims, condensation where the cold tea is.
        var hl = Path()
        hl.move(to: CGPoint(x: -g.rimW + 9 * u, y: -g.cupH + 8 * u))
        hl.addLine(to: CGPoint(x: -g.baseW + 8 * u, y: -6 * u))
        hl.addLine(to: CGPoint(x: -g.baseW + 17 * u, y: -6 * u))
        hl.addLine(to: CGPoint(x: -g.rimW + 21 * u, y: -g.cupH + 8 * u))
        hl.closeSubpath()
        c.fill(hl, with: .linearGradient(
            Gradient(stops: [
                .init(color: Color.white.opacity(0.42), location: 0),
                .init(color: Color.white.opacity(0.12), location: 0.6),
                .init(color: Color.white.opacity(0.3), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: -g.cupH),
            endPoint: .zero
        ))
        var edgeR = Path()
        edgeR.move(to: CGPoint(x: g.rimW - 5 * u, y: -g.cupH + 10 * u))
        edgeR.addLine(to: CGPoint(x: g.baseW - 4 * u, y: -8 * u))
        c.stroke(edgeR, with: .color(Color.white.opacity(0.35)), style: round(2 * u))
        c.stroke(outer, with: .color(Color.white.opacity(0.55)), style: round(1.2))
        var frontRim = Path()
        ellipseArc(&frontRim, rimRect, 0, 180, move: true)
        c.stroke(frontRim, with: .color(Color.white.opacity(0.8)), style: round(2.2))
        var baseRing = Path()
        ellipseArc(&baseRing, CGRect(x: -g.baseW + 4 * u, y: -g.baseRy - 6 * u, width: (g.baseW - 4 * u) * 2, height: g.baseRy * 2), 0, 180, move: true)
        c.stroke(baseRing, with: .color(Color.white.opacity(0.4)), style: round(1.4))
        for i in 0..<26 {
            let fi = Double(i)
            let hy = ((fi * 0.6180339).truncatingRemainder(dividingBy: 1) * 0.9 + 0.04) * g.cupH
            let cold = max(0, min(1, (lvl - hy) / (20 * u)))
            if cold <= 0 { continue }
            let side = (fi * 0.7548776).truncatingRemainder(dividingBy: 1) * 2 - 1
            let hx = side * (M.halfWidth(g, hy) - 4 * u)
            let r = (0.9 + (fi * 0.5698403).truncatingRemainder(dividingBy: 1) * 1.4) * u
            c.fill(circle(hx + 0.5, -hy + 0.8, r), with: .color(rgb(0x5A321A, 0.16 * cold)))
            c.fill(circle(hx - r * 0.3, -hy - r * 0.3, r * 0.45), with: .color(Color.white.opacity(0.7 * cold)))
        }
    }

    // MARK: - The panel, per frame

    private static func trackX(_ g: Geo, _ pct: Double) -> Double {
        let x0 = 32.0
        let x1 = g.w - 32
        var k = 0.0
        for i in 0..<3 where pct >= stepAt[i] {
            k = Double(i) + min(1, (pct - stepAt[i]) / (stepAt[i + 1] - stepAt[i]))
        }
        return x0 + (x1 - x0) * k / 3
    }

    static func drawPanel(_ c: GraphicsContext, _ g: Geo, _ sc: BobaPearlProgressScene) {
        // Percentage, cross-fading during the cup swap.
        let py = g.percentY + 34
        let fading = sc.pct.2 > 0 && sc.pct.2 < 1
        if fading {
            text(c, "\(sc.pct.0)%", font: pctFont, color: ink.opacity(1 - sc.pct.2), x: 24, baseline: py - 8 * sc.pct.2)
            text(c, "\(sc.pct.1)%", font: pctFont, color: ink.opacity(sc.pct.2), x: 24, baseline: py + 8 * (1 - sc.pct.2))
        } else {
            text(c, "\(Int(floor(sc.percent)))%", font: pctFont, color: ink, x: 24, baseline: py)
        }
        // Status and time, right aligned, cross-fading on a step change.
        let rx = g.w - 24
        for (idx, alpha, shift) in [(sc.prevStep, 1 - sc.stepFade, -6 * sc.stepFade), (sc.step, sc.stepFade, 6 * (1 - sc.stepFade))] {
            if alpha <= 0.01 { continue }
            text(c, M.status[idx], font: statusFont, color: ink.opacity(alpha), x: rx, baseline: py - 18 + shift, trailing: true)
            text(c, M.eta[idx], font: etaFont, color: inkSoft.opacity(alpha), x: rx, baseline: py + shift, trailing: true)
        }

        // The tracker.
        let y = g.stepsY
        let x0 = 32.0
        let x1 = g.w - 32
        c.fill(Path(roundedRect: CGRect(x: x0, y: y - 2, width: x1 - x0, height: 4), cornerRadius: 2), with: .color(rgb(0x3B2418, 0.12)))
        let fx = trackX(g, sc.percent)
        if fx > x0 + 0.5 {
            c.fill(Path(roundedRect: CGRect(x: x0, y: y - 2, width: fx - x0, height: 4), cornerRadius: 2), with: .linearGradient(
                Gradient(stops: [
                    .init(color: rgb(0xD29A68), location: 0),
                    .init(color: rgb(0xA9653A), location: 0.5),
                    .init(color: rgb(0x7D4423), location: 1),
                ]),
                startPoint: CGPoint(x: x0, y: 0),
                endPoint: CGPoint(x: x1, y: 0)
            ))
        }
        for i in 0..<4 {
            let nx = x0 + (x1 - x0) * Double(i) / 3
            let reached = sc.percent >= stepAt[i] - 0.01 && (i > 0 || sc.percent > 0.5)
            let active = sc.step == i
            if active {
                // A soft halo (a 13 pt disc blurred by 4), as one radial gradient.
                c.fill(circle(nx, y, 25), with: .radialGradient(
                    Gradient(stops: [
                        .init(color: rgb(0xA9653A, 0.25), location: 0),
                        .init(color: rgb(0xA9653A, 0.25 * 0.977), location: 5.0 / 25),
                        .init(color: rgb(0xA9653A, 0.25 * 0.841), location: 9.0 / 25),
                        .init(color: rgb(0xA9653A, 0.25 * 0.5), location: 13.0 / 25),
                        .init(color: rgb(0xA9653A, 0.25 * 0.159), location: 17.0 / 25),
                        .init(color: rgb(0xA9653A, 0.25 * 0.023), location: 21.0 / 25),
                        .init(color: rgb(0xA9653A, 0), location: 1),
                    ]),
                    center: CGPoint(x: nx, y: y),
                    startRadius: 0,
                    endRadius: 25
                ))
            }
            c.fill(circle(nx, y + 1, 9), with: .color(rgb(0x3B2418, 0.18)))
            c.fill(circle(nx, y, 9), with: .color(reached ? rgb(0x8A4F2C) : rgb(0xF6E6D6)))
            if !reached {
                c.stroke(circle(nx, y, 8.25), with: .color(rgb(0x3B2418, 0.22)), lineWidth: 1.5)
            } else if i < sc.step || (i == 3 && sc.step == 3) {
                var p = Path()
                p.move(to: CGPoint(x: nx - 3.6, y: y + 0.2))
                p.addLine(to: CGPoint(x: nx - 1, y: y + 2.8))
                p.addLine(to: CGPoint(x: nx + 3.8, y: y - 2.6))
                c.stroke(p, with: .color(rgb(0xFFF6EC)), style: round(1.8))
            } else {
                c.fill(circle(nx, y, 3), with: .color(rgb(0xFFF6EC)))
            }
            let label = M.steps[i]
            let lw = width(c, label, font: stepFont)
            let lx = max(16, min(g.w - 16 - lw, nx - lw / 2))
            text(c, label, font: stepFont, color: active ? ink : inkSoft, x: lx, baseline: y + 28)
        }
    }
}
