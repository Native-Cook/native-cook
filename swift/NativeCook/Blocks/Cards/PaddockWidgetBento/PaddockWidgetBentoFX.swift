import SwiftUI
import CoreText

private typealias Motion = PaddockWidgetBentoMotion
private typealias Theme = PaddockWidgetBentoTheme

/// The pit stop's motion blur (FX.tsx PitTrails): behind every tile in flight, a smear of its own colour from where it
/// was 64 ms ago to where it is, transparent at the tail, stronger with speed. Drawn under the tiles.
struct PaddockWidgetBentoPitTrails: View {
    let scale: CGFloat
    let pit: Double
    let frame: PaddockWidgetBentoMotion.Frame

    private static let tiles: [(rect: CGRect, radius: Double)] = {
        let q = Theme.Layout.quick
        let a = Theme.Layout.glossary
        let r = Theme.Radius.self
        return [
            (CGRect(x: q.minX, y: Motion.slotY[0], width: q.width, height: q.height), r.quickCard),
            (CGRect(x: q.minX, y: Motion.slotY[1], width: q.width, height: q.height), r.quickCard),
            (CGRect(x: q.minX, y: Motion.slotY[2], width: q.width, height: q.height), r.quickCard),
            (Theme.Layout.pitcast, r.card), (Theme.Layout.stats, r.card),
            (CGRect(x: a.minX, y: a.minY, width: a.width, height: a.height), r.quickCard),
            (Theme.Layout.education, r.quickCard),
            (Theme.Layout.hero, r.card), (Theme.Layout.guide, r.card), (Theme.Layout.banner, r.card),
        ]
    }()

    var body: some View {
        let s = scale
        Canvas { context, _ in
            guard pit > 0 else { return }
            for i in 0..<Motion.Tile.count {
                let tile = Self.tiles[i]
                let top = i < 3 ? (frame.cardY[PaddockWidgetBentoCardID.allCases[i]] ?? tile.rect.minY) : tile.rect.minY
                let head = Motion.pitTile(i, pit)
                let alpha = Motion.smearAlpha(head.speed)
                if alpha < 0.01 { continue }
                let tail = Motion.pitTile(i, pit - Motion.Pit.ghosts * Motion.Pit.ghostStep)
                let left = min(head.x, tail.x)
                let right = max(head.x, tail.x) + tile.rect.width
                let y = (head.y + tail.y) / 2
                let x0 = (tile.rect.minX + tail.x + tile.rect.width / 2) * s
                let k = (head.x - tail.x) * s
                if abs(k) < 0.5 { continue }
                let color = Theme.tileColors[i]
                let rect = CGRect(x: (tile.rect.minX + left) * s, y: (top + y) * s, width: (right - left) * s, height: tile.rect.height * s)
                let path = Path(roundedRect: rect, cornerRadius: tile.radius * s, style: .continuous)
                let gradient = Gradient(colors: [color.opacity(0), color.opacity(min(1, alpha))])
                context.fill(path, with: .linearGradient(gradient, startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x0 + k, y: 0)))
            }
        }
        .frame(width: Theme.referenceWidth * s, height: Theme.Layout.gridHeight * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Gooey neighbours (FX.tsx GooLayer): while the cards are arranged, the three capsules are drawn again under the real
/// cards, blurred and alpha-thresholded as one layer, so where two come close they join with a liquid bridge.
struct PaddockWidgetBentoGooLayer: View {
    let scale: CGFloat
    let frame: PaddockWidgetBentoMotion.Frame
    let time: Double

    private static let colors = [Theme.Colors.violet, Theme.Colors.orange, Theme.Colors.yellow]

    var body: some View {
        let s = scale
        let pad = Motion.Goo.pad
        let q = Theme.Layout.quick
        let width = (q.width + 2 * pad) * s
        let height = (Motion.slotY[2] - Motion.slotY[0] + q.height + 2 * pad) * s
        let ids = PaddockWidgetBentoCardID.allCases
        let ordered = ids.filter { frame.selectedID != $0 } + ids.filter { frame.selectedID == $0 }
        ZStack(alignment: .topLeading) {
            ForEach(ordered, id: \.self) { id in
                let held = frame.selectedID == id
                let y = frame.cardY[id] ?? Motion.slotY[0]
                RoundedRectangle(cornerRadius: Theme.Radius.quickCard * s, style: .continuous)
                    .fill(Self.colors[id.index])
                    .frame(width: q.width * s, height: q.height * s)
                    .scaleEffect(held ? frame.selectedScale : 1)
                    .rotationEffect(.degrees(held ? frame.rotationDegrees : Motion.jiggle(id: id.index, time: time, handles: frame.handlesOpacity)))
                    .position(x: (pad + q.width / 2) * s, y: (pad + y - Motion.slotY[0] + q.height / 2) * s)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .compositingGroup()
        .blur(radius: Motion.Goo.blur * s)
        .colorEffect(ShaderLibrary.paddockWidgetBentoGoo(.float(Motion.Goo.gain), .float(Motion.Goo.bias)))
        .opacity(min(1, frame.handlesOpacity))
        .offset(x: (q.minX - pad) * s, y: (Motion.slotY[0] - pad) * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The glyph outlines of "exclusive" / "widgets" in Montserrat ExtraBold, one path per glyph, y down, at a scale.
@MainActor
final class PaddockWidgetBentoLettering {
    private static var cache: [CGFloat: PaddockWidgetBentoLettering] = [:]
    let first: [Path]
    let second: [Path]

    static func make(scale: CGFloat) -> PaddockWidgetBentoLettering {
        if let cached = cache[scale] { return cached }
        let value = PaddockWidgetBentoLettering(scale: scale)
        cache[scale] = value
        return value
    }

    private init(scale s: CGFloat) {
        let font = PaddockWidgetBentoFont.ct(.extraBold, size: Theme.TypeSize.heroGlyph * s)
        let b = Theme.Layout.self
        func glyphs(_ text: String, x: Double, baseline: Double) -> [Path] {
            let chars = Array(text.utf16)
            var ids = [CGGlyph](repeating: 0, count: chars.count)
            CTFontGetGlyphsForCharacters(font, chars, &ids, chars.count)
            var advances = [CGSize](repeating: .zero, count: chars.count)
            CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &advances, chars.count)
            var pen = x * s
            var result: [Path] = []
            for (index, glyph) in ids.enumerated() {
                if let cg = CTFontCreatePathForGlyph(font, glyph, nil) {
                    let transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: pen, ty: baseline * s)
                    result.append(Path(cg).applying(transform))
                }
                pen += advances[index].width
            }
            return result
        }
        first = glyphs("exclusive", x: Theme.Space.heroFirstX, baseline: b.heroFirst)
        second = glyphs("widgets", x: Theme.Space.heroSecondX, baseline: b.heroSecond)
    }
}

/// "exclusive widgets" as a die-cut sticker (FX.tsx StickerWordmark). The corner is pulled to `distance` along `angle`:
/// letters past the fold are cut from the card (a faint glue ghost stays) and flip over the fold as the sticker's paper
/// back, shaded at the bend and casting a soft shadow. The slap after a peel presses the lettering and throws impact lines.
struct PaddockWidgetBentoSticker: View {
    let scale: CGFloat
    let peel: PaddockWidgetBentoMotion.PeelState

    var body: some View {
        let s = scale
        let pad = Motion.Goo.pad
        let lettering = PaddockWidgetBentoLettering.make(scale: s)
        Canvas { context, _ in
            draw(&context, lettering: lettering, s: s, pad: pad)
        }
        .frame(width: (Theme.Layout.hero.width + 2 * pad) * s, height: (Theme.Layout.hero.height + 2 * pad) * s)
        .offset(x: -pad * s, y: -pad * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func letters(_ c: GraphicsContext, _ l: PaddockWidgetBentoLettering, rose: GraphicsContext.Shading, white: GraphicsContext.Shading) {
        for path in l.first { c.fill(path, with: rose) }
        for path in l.second { c.fill(path, with: white) }
    }

    /// The die-cut outline: the lettering grown by `Peel.border` on every side, counters filled.
    private func silhouette(_ context: GraphicsContext, _ l: PaddockWidgetBentoLettering, s: CGFloat, color: Color, blur: CGFloat = 0) {
        var c = context
        if blur > 0 { c.addFilter(.blur(radius: blur)) }
        let style = StrokeStyle(lineWidth: 2 * Motion.Peel.border * s, lineJoin: .round)
        for path in l.first + l.second { c.stroke(path, with: .color(color), style: style) }
        for path in l.first + l.second { c.fill(path, with: .color(color)) }
    }

    private func clipHalf(_ c: inout GraphicsContext, mx: CGFloat, my: CGFloat, ux: Double, uy: Double, side: Double) {
        let big: CGFloat = 4000
        let angle = atan2(side * uy, side * ux)
        let t = CGAffineTransform(translationX: mx, y: my).rotated(by: angle)
        c.clip(to: Path(CGRect(x: 0, y: -big, width: big, height: 2 * big)).applying(t))
    }

    /// Reflects across the line through (mx, my) perpendicular to (ux, uy): the fold of the sticker.
    private func reflect(_ c: inout GraphicsContext, mx: CGFloat, my: CGFloat, ux: Double, uy: Double) {
        let dot = ux * mx + uy * my
        c.concatenate(CGAffineTransform(a: 1 - 2 * ux * ux, b: -2 * ux * uy, c: -2 * ux * uy, d: 1 - 2 * uy * uy, tx: 2 * ux * dot, ty: 2 * uy * dot))
    }

    private func draw(_ context: inout GraphicsContext, lettering l: PaddockWidgetBentoLettering, s: CGFloat, pad: Double) {
        let hero = Theme.Layout.hero
        let slap = Motion.slapPose(peel.slap)
        var root = context
        root.translateBy(x: pad * s, y: pad * s)
        let cx = hero.width / 2 * s, cy = hero.height / 2 * s
        var body = root
        body.translateBy(x: cx, y: cy)
        body.scaleBy(x: slap.press, y: slap.press)
        body.translateBy(x: -cx, y: -cy)
        let rose = GraphicsContext.Shading.color(Theme.Colors.rose)
        let white = GraphicsContext.Shading.color(.white)
        if peel.distance < 0.5 {
            letters(body, l, rose: rose, white: white)
        } else {
            let rad = peel.angle * .pi / 180
            let ux = cos(rad), uy = sin(rad)
            let mx = Motion.Peel.cornerX * s + ux * peel.distance * s / 2
            let my = Motion.Peel.cornerY * s + uy * peel.distance * s / 2
            // The part still stuck down: the card's own red, printed with the letters.
            var stuck = body
            clipHalf(&stuck, mx: mx, my: my, ux: ux, uy: uy, side: 1)
            silhouette(stuck, l, s: s, color: Theme.Colors.red)
            letters(stuck, l, rose: rose, white: white)
            // Glue left where the flap came away.
            var glue = body
            clipHalf(&glue, mx: mx, my: my, ux: ux, uy: uy, side: -1)
            silhouette(glue, l, s: s, color: Color.white.opacity(Theme.glueGhost))
            // The flap's shadow on the card, a little further along the pull and down.
            var shadow = body
            shadow.translateBy(x: ux * Motion.Peel.shadowOffset * s, y: Motion.Peel.shadowOffset * s / 2 + uy * Motion.Peel.shadowOffset * s)
            reflect(&shadow, mx: mx, my: my, ux: ux, uy: uy)
            clipHalf(&shadow, mx: mx, my: my, ux: ux, uy: uy, side: -1)
            silhouette(shadow, l, s: s, color: Theme.Colors.stickerShadow.opacity(Theme.stickerShadowAlpha), blur: Motion.Peel.shadowBlur * s)
            // The flap: the sticker seen from the back (plain paper), darker at the bend.
            var flap = body
            reflect(&flap, mx: mx, my: my, ux: ux, uy: uy)
            clipHalf(&flap, mx: mx, my: my, ux: ux, uy: uy, side: -1)
            flap.drawLayer { layer in
                silhouette(layer, l, s: s, color: Theme.Colors.stickerBack)
                var shade = layer
                shade.blendMode = .sourceAtop
                shade.translateBy(x: mx, y: my)
                shade.rotate(by: .radians(atan2(-uy, -ux)))
                shade.scaleBy(x: Motion.Peel.foldShade * s, y: 1)
                let fold = Theme.Colors.stickerFold
                shade.fill(Path(CGRect(x: -1, y: -4000, width: 1e4, height: 8000)),
                           with: .linearGradient(Gradient(colors: [fold, fold.opacity(0)]), startPoint: .zero, endPoint: CGPoint(x: 1, y: 0)))
            }
        }
        if slap.lines >= 0 {
            let e = slap.lines
            let ox = (Motion.Peel.cornerX - 8) * s, oy = (Motion.Peel.cornerY - 30) * s
            var line = Path()
            for i in 0..<Motion.Peel.lines {
                let a = (-100 + Double(i) * 120 / Double(Motion.Peel.lines - 1)) * .pi / 180
                let inner = (Motion.Peel.lineInner + Motion.Peel.lineReach * e) * s
                let outer = inner + Motion.Peel.lineLength * (1 - e) * s
                line.move(to: CGPoint(x: ox + cos(a) * inner, y: oy + sin(a) * inner))
                line.addLine(to: CGPoint(x: ox + cos(a) * outer, y: oy + sin(a) * outer))
            }
            root.stroke(line, with: .color(Color.white.opacity(max(0, min(1, 1 - e)))), style: StrokeStyle(lineWidth: 3 * s, lineCap: .round))
        }
    }
}
