import CoreText
import SwiftUI

// MARK: - Glyphs

/// Outlines of the drum characters and the label letters, built once, and their ink boxes measured
/// the way the React Native version's Skia measures them (the outline rounded out to whole points,
/// plus a point on every side), so letters sit on their flaps exactly as there.
enum BakeryFlapBoardGlyphs {
    struct Glyph {
        var path: Path
        /// Left edge and width of the ink box.
        var x: Double
        var w: Double
        /// Top of the ink box (negative: above the baseline).
        var y: Double
    }

    static let flapFont = CTFontCreateWithName("AvenirNextCondensed-Bold" as CFString, 25, nil)
    static let labelFont = CTFontCreateWithName("AvenirNextCondensed-DemiBold" as CFString, 10.5, nil)

    static func glyph(_ ch: Character, _ font: CTFont) -> Glyph {
        let chars = Array(String(ch).utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        // Core Text draws upward; flip so the outline hangs from a baseline at y = 0.
        var flip = CGAffineTransform(scaleX: 1, y: -1)
        let outline = glyphs.first.flatMap { CTFontCreatePathForGlyph(font, $0, &flip) } ?? CGMutablePath()
        let ink = outline.boundingBoxOfPath
        guard !ink.isNull, !ink.isEmpty else { return Glyph(path: Path(), x: 0, w: 0, y: 0) }
        let left = ink.minX.rounded(.down) - 1
        let right = ink.maxX.rounded(.up) + 1
        let top = ink.minY.rounded(.down) - 1
        return Glyph(path: Path(outline), x: left, w: right - left, y: top)
    }

    /// The drum's characters on the flap face; the space borrows H's box but is never drawn.
    static let drum: [Glyph] = BakeryFlapBoardText.drum.map { glyph($0 == " " ? "H" : $0, flapFont) }
    static let capHeight = -glyph("H", flapFont).y
}

// MARK: - Projection

/// The perspective of a half flap turned about the hinge line `axis` by `angle` (0 flat, ±π/2 edge
/// on), seen from `camera` away: the edge swinging towards the viewer grows. The same matrix as the
/// React Native version's, applied point by point since a SwiftUI context only takes affine ones.
struct BakeryFlapBoardHinge {
    let cx: Double
    let axis: Double
    let k: Double
    let c: Double

    init(cx: Double, axis: Double, angle: Double, camera: Double) {
        self.cx = cx
        self.axis = axis
        k = sin(angle) / camera
        c = cos(angle)
    }

    func map(_ p: CGPoint) -> CGPoint {
        let dy = p.y - axis
        let w = k * dy + 1
        return CGPoint(x: (p.x + cx * k * dy) / w, y: (axis + dy * (c + k * axis)) / w)
    }

    func map(_ path: Path) -> Path {
        var out = Path()
        path.forEach { element in
            switch element {
            case .move(let to): out.move(to: map(to))
            case .line(let to): out.addLine(to: map(to))
            case .quadCurve(let to, let control): out.addQuadCurve(to: map(to), control: map(control))
            case .curve(let to, let c1, let c2): out.addCurve(to: map(to), control1: map(c1), control2: map(c2))
            case .closeSubpath: out.closeSubpath()
            }
        }
        return out
    }
}

// MARK: - Painting

/// Draws the board: the static case under the flaps, the flaps themselves every frame, and the
/// splits and hinge pins over them.
enum BakeryFlapBoardPainter {
    private typealias M = BakeryFlapBoardMetrics
    private typealias G = BakeryFlapBoardGlyphs

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> GraphicsContext.Shading {
        .color(Color(hex: hex, opacity: alpha))
    }

    /// One half of a flap's face, with its top or bottom corners rounded.
    static func halfShape(_ x: Double, _ y: Double, _ w: Double, _ h: Double, top: Bool) -> Path {
        let r = 3.0
        let mid = y + h / 2
        var p = Path()
        if top {
            p.move(to: CGPoint(x: x, y: mid))
            p.addLine(to: CGPoint(x: x, y: y + r))
            p.addArc(tangent1End: CGPoint(x: x, y: y), tangent2End: CGPoint(x: x + r, y: y), radius: r)
            p.addLine(to: CGPoint(x: x + w - r, y: y))
            p.addArc(tangent1End: CGPoint(x: x + w, y: y), tangent2End: CGPoint(x: x + w, y: y + r), radius: r)
            p.addLine(to: CGPoint(x: x + w, y: mid))
        } else {
            p.move(to: CGPoint(x: x, y: mid))
            p.addLine(to: CGPoint(x: x + w, y: mid))
            p.addLine(to: CGPoint(x: x + w, y: y + h - r))
            p.addArc(tangent1End: CGPoint(x: x + w, y: y + h), tangent2End: CGPoint(x: x + w - r, y: y + h), radius: r)
            p.addLine(to: CGPoint(x: x + r, y: y + h))
            p.addArc(tangent1End: CGPoint(x: x, y: y + h), tangent2End: CGPoint(x: x, y: y + h - r), radius: r)
        }
        p.closeSubpath()
        return p
    }

    private static func glyphPath(_ ch: Int, x: Double, y: Double, w: Double, baseline: Double) -> Path {
        let glyph = G.drum[ch]
        return glyph.path.applying(CGAffineTransform(translationX: x + (w - glyph.w) / 2 - glyph.x, y: y + baseline))
    }

    /// One half of a flap (top or bottom) with its half of character `ch`, shaded by `shade`, turned
    /// about the hinge when `hinge` is given.
    static func half(_ context: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, top: Bool, ch: Int, ink: UInt32, shade: Double, baseline: Double, hinge: BakeryFlapBoardHinge?) {
        let mid = y + h / 2
        var shape = halfShape(x, y, w, h, top: top)
        var cut = Path(top ? CGRect(x: x, y: y, width: w, height: h / 2) : CGRect(x: x, y: mid, width: w, height: h / 2))
        var letter = BakeryFlapBoardText.drum[ch] == " " ? nil : glyphPath(ch, x: x, y: y, w: w, baseline: baseline)
        if let hinge {
            shape = hinge.map(shape)
            cut = hinge.map(cut)
            letter = letter.map(hinge.map)
        }
        context.fill(shape, with: color(top ? M.flapTop : M.flapBottom))
        if let letter {
            var c = context
            c.clip(to: cut)
            c.fill(letter, with: color(ink))
        }
        if shade > 0.003 { context.fill(shape, with: color(0x000000, shade)) }
    }

    /// A whole cell: settled, flipping (two static halves and one turning), or bouncing.
    static func cell(_ context: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ cell: BakeryFlapBoardCell, ink: UInt32, baseline: Double, reducedMotion: Bool) {
        let cx = x + w / 2
        let axis = y + h / 2
        if cell.p < 0 && cell.bounce < 0 {
            if BakeryFlapBoardText.drum[cell.cur] != " " {
                context.fill(glyphPath(cell.cur, x: x, y: y, w: w, baseline: baseline), with: color(ink))
            }
            return
        }
        let camera = M.camera * h
        if cell.p < 0 {
            // The last flap has landed and lifts once off its stop.
            let q = cell.bounce
            let lift = M.bounceAngle * sin(Double.pi * q) * (1 - q)
            half(context, x, y, w, h, top: true, ch: cell.cur, ink: ink, shade: 0, baseline: baseline, hinge: nil)
            half(context, x, y, w, h, top: false, ch: cell.cur, ink: ink, shade: M.castShade * (lift / M.bounceAngle) * 0.6, baseline: baseline, hinge: nil)
            half(context, x, y, w, h, top: false, ch: cell.cur, ink: ink, shade: lift * 0.5, baseline: baseline, hinge: BakeryFlapBoardHinge(cx: cx, axis: axis, angle: -lift, camera: camera))
            return
        }
        if reducedMotion {
            // Without the turn the letters just change, half by half.
            let ch = cell.p < 0.5 ? cell.cur : cell.next
            half(context, x, y, w, h, top: true, ch: ch, ink: ink, shade: 0, baseline: baseline, hinge: nil)
            half(context, x, y, w, h, top: false, ch: ch, ink: ink, shade: 0, baseline: baseline, hinge: nil)
            return
        }
        let p = cell.p
        let angle = Double.pi * (M.fallLinear * p + (1 - M.fallLinear) * p * p)
        let firstHalf = angle < Double.pi / 2
        // Behind the falling flap: the next letter's top, darkened while the flap still hangs over it.
        half(context, x, y, w, h, top: true, ch: cell.next, ink: ink, shade: firstHalf ? M.castShade * sin(angle) : 0, baseline: baseline, hinge: nil)
        // Below: the current letter's bottom, shaded as the flap comes down onto it.
        half(context, x, y, w, h, top: false, ch: cell.cur, ink: ink, shade: firstHalf ? 0 : M.castShade * (angle / (Double.pi / 2) - 1), baseline: baseline, hinge: nil)
        if firstHalf {
            half(context, x, y, w, h, top: true, ch: cell.cur, ink: ink, shade: M.flapShade * (angle / (Double.pi / 2)), baseline: baseline,
                 hinge: BakeryFlapBoardHinge(cx: cx, axis: axis, angle: angle, camera: camera))
        } else {
            half(context, x, y, w, h, top: false, ch: cell.next, ink: ink, shade: M.flapShade * (2 - angle / (Double.pi / 2)), baseline: baseline,
                 hinge: BakeryFlapBoardHinge(cx: cx, axis: axis, angle: angle - Double.pi, camera: camera))
        }
    }

    /// Every flap of every line this frame.
    static func flaps(_ context: GraphicsContext, _ frame: BakeryFlapBoardFrame, _ layout: BakeryFlapBoardLayout) {
        let baseline = layout.cellH / 2 + G.capHeight / 2
        for (r, row) in frame.rows.enumerated() {
            let y = layout.rows[r]
            for i in 0..<BakeryFlapBoardText.columns {
                // Prices are set in amber, the rest in cream.
                let ink = r >= M.timeRows && i >= BakeryFlapBoardText.columns - 4 ? M.price : M.letter
                cell(context, layout.cellX(i), y, layout.cellW, layout.cellH, BakeryFlapBoardMath.cell(row, i, frame.now), ink: ink, baseline: baseline, reducedMotion: frame.reducedMotion)
            }
        }
    }

    /// Letters `text` spaced by `track` pt from `x` on `baseline`; returns the width they take.
    @discardableResult
    static func spaced(_ context: GraphicsContext, _ text: String, x: Double, baseline: Double, track: Double, ink: UInt32) -> Double {
        var at = x
        for ch in text {
            if ch == " " {
                at += 10.5 * 0.32 + track
                continue
            }
            let glyph = G.glyph(ch, G.labelFont)
            context.fill(glyph.path.applying(CGAffineTransform(translationX: at, y: baseline)), with: color(ink))
            at += glyph.w + track
        }
        return at - x - track
    }

    /// The case, the flap backs and the section labels: everything under the letters that never moves.
    static func under(_ context: GraphicsContext, _ layout: BakeryFlapBoardLayout, labels: [String]) {
        let b = layout
        let caseRect = CGRect(x: b.x, y: b.y, width: b.w, height: b.h)
        var shadow = context
        shadow.addFilter(.blur(radius: 10))
        shadow.fill(Path(roundedRect: caseRect.offsetBy(dx: 0, dy: 8), cornerRadius: 14), with: color(0x000000, 0.45))
        let rounded = Path(roundedRect: caseRect, cornerRadius: 14)
        context.fill(rounded, with: color(0x120d0a))
        let rim = Gradient(colors: [0x7a5a2c, 0xe8c787, 0x946f35, 0xdcb66f, 0x5f4420].map { Color(hex: $0) })
        context.stroke(rounded, with: .linearGradient(rim, startPoint: CGPoint(x: b.x, y: b.y), endPoint: CGPoint(x: b.x + b.w, y: b.y + b.h)), lineWidth: 2.5)
        // Brass screws in the corners.
        for (sx, sy) in [(b.x + 9, b.y + 9), (b.x + b.w - 9, b.y + 9), (b.x + 9, b.y + b.h - 9), (b.x + b.w - 9, b.y + b.h - 9)] {
            context.fill(Path(ellipseIn: CGRect(x: sx - 3.2, y: sy - 3.2, width: 6.4, height: 6.4)), with: color(M.brassDark))
            context.fill(Path(ellipseIn: CGRect(x: sx - 0.6 - 2.3, y: sy - 0.6 - 2.3, width: 4.6, height: 4.6)), with: color(M.brass))
            var slot = Path()
            slot.move(to: CGPoint(x: sx - 1.8, y: sy + 1.2))
            slot.addLine(to: CGPoint(x: sx + 1.2, y: sy - 1.8))
            context.stroke(slot, with: color(M.brassDark), lineWidth: 1 / 3)
        }

        for (k, y) in b.labels.enumerated() where k < labels.count {
            let width = spaced(context, labels[k], x: b.left, baseline: y, track: 2.6, ink: M.brass)
            let from = b.left + width + 10
            context.fill(Path(CGRect(x: from, y: y - 4, width: b.x + b.w - M.pad - from, height: 0.8)), with: color(M.brass, 0.35))
        }

        for r in b.rows.indices {
            for i in 0..<BakeryFlapBoardText.columns {
                let x = b.cellX(i)
                let y = b.rows[r]
                context.fill(Path(roundedRect: CGRect(x: x, y: y + 1.2, width: b.cellW, height: b.cellH), cornerRadius: 3), with: color(0x000000, 0.6))
                context.fill(halfShape(x, y, b.cellW, b.cellH, top: true), with: color(M.flapTop))
                context.fill(halfShape(x, y, b.cellW, b.cellH, top: false), with: color(M.flapBottom))
            }
        }
    }

    /// The split across every flap and its two brass hinge pins, drawn over the letters.
    static func over(_ context: GraphicsContext, _ layout: BakeryFlapBoardLayout) {
        let b = layout
        var splits = Path()
        var lights = Path()
        var pins = Path()
        for r in b.rows.indices {
            for i in 0..<BakeryFlapBoardText.columns {
                let x = b.cellX(i)
                let mid = b.rows[r] + b.cellH / 2
                splits.addRect(CGRect(x: x, y: mid - 0.7, width: b.cellW, height: 1.4))
                lights.addRect(CGRect(x: x + 1, y: b.rows[r] + 0.6, width: b.cellW - 2, height: 0.6))
                pins.addEllipse(in: CGRect(x: x + 1.6 - 1.2, y: mid - 1.2, width: 2.4, height: 2.4))
                pins.addEllipse(in: CGRect(x: x + b.cellW - 1.6 - 1.2, y: mid - 1.2, width: 2.4, height: 2.4))
            }
        }
        context.fill(splits, with: color(M.split))
        context.fill(lights, with: color(0xffffff, 0.05))
        context.fill(pins, with: color(M.brassDark))
    }
}
