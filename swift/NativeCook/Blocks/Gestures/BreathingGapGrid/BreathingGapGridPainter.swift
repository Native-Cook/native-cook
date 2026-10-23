import SwiftUI
import UIKit

/// Draws the wall and its tiles into a Canvas, the way the RN version records them into a Skia picture.
struct BreathingGapGridPainter {
    let frame: BreathingGapGridFrame
    let tiles: [BreathingGapGridTile]
    let caption: String
    let background: UInt32
    let textColor: UInt32
    let reducedMotion: Bool

    private typealias M = BreathingGapGridMetrics
    private typealias L = BreathingGapGridLayout

    // Helvetica Neue, as the Skia fonts were matched on iOS; the caption's weight 600 falls to the medium.
    static let typeFont = UIFont(name: "HelveticaNeue-Bold", size: M.typeSize) ?? .boldSystemFont(ofSize: M.typeSize)
    static let captionFontName = "HelveticaNeue-Medium"

    /// A cubic ease-out.
    private static func easeOut(_ p: Double) -> Double { 1 - (1 - p) * (1 - p) * (1 - p) }
    private static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    /// A line's advance at `font`'s size.
    static func advance(_ text: String, _ font: UIFont) -> Double {
        (text as NSString).size(withAttributes: [.font: font]).width
    }

    /// The caption's letters and where each starts: drawn a letter at a time, tracked apart.
    static func glyphs(_ caption: String) -> (letters: [String], at: [Double], width: Double) {
        BreathingGapGridSkiaText.tracked(caption.uppercased(), BreathingGapGridSkiaText.font(captionFontName, M.captionSize), tracking: M.captionTracking)
    }

    // MARK: The wall

    func drawWall(in context: inout GraphicsContext) {
        let l = frame.layout
        let width = l.width
        let height = l.height
        context.fill(Path(CGRect(x: 0, y: 0, width: width, height: height)), with: .color(Color(hex: background)))
        guard width > 0 else { return }
        let now = frame.elapsed
        let o = frame.open
        let lifted = Self.clamp01(o)
        let captionY = height * M.captionAt
        let reach = hypot(width / 2, height / 2)

        let span = width / 2 + l.side
        let firstC = Int(floor(1.5 - span / l.pitch))
        let lastC = Int(ceil(1.5 + span / l.pitch))
        for c in firstC...lastC {
            let m = L.lane(c)
            let shift = M.phase[m] - l.time * M.drift[m] + l.scroll
            let first = Int(floor((-height / 2 - l.side - M.part) / l.pitch - shift))
            let last = Int(ceil((height / 2 + l.side + M.part) / l.pitch - shift))
            guard first <= last else { continue }
            for r in first...last {
                if o > 0.001 && c == frame.openC && r == frame.openR { continue }
                let centre = l.cellCentre(c, r)
                // Arrival, rippling out from the middle by where the cell started.
                let start = hypot(centre.x - width / 2, (Double(r) + M.phase[m]) * l.pitch) / reach
                let grown = reducedMotion
                    ? Self.clamp01(now / 0.3)
                    : Self.easeOut(Self.clamp01((now - M.enterDelay - start * M.enterSpread) / M.enterEach))
                if grown <= 0 { continue }
                // Tiles give way to the caption's lane as the gap opens.
                let clearance = abs(centre.y - captionY) - l.side / 2
                let clear = Self.clamp01((clearance - M.clear) / M.clearSoft)
                let alpha = grown * (1 - l.part * (1 - clear))
                let side = l.side * (M.seed + (1 - M.seed) * grown)
                drawTile(
                    in: &context,
                    index: L.tileAt(c, r, tiles.count),
                    rect: CGRect(x: centre.x - side / 2, y: centre.y - side / 2, width: side, height: side),
                    alpha: alpha
                )
            }
        }

        // The caption, in a darkened lane, a letter at a time.
        let reveal = Self.clamp01((l.part - M.captionFrom) / (1 - M.captionFrom)) * (1 - lifted)
        guard reveal > 0.001 else { return }
        let ground = Color(hex: background)
        var band = context
        band.opacity = reveal
        let lane = CGRect(x: 0, y: captionY - M.laneHeight / 2, width: width, height: M.laneHeight)
        band.fill(Path(lane), with: .linearGradient(
            Gradient(colors: [ground.opacity(0), ground.opacity(M.laneStrength), ground.opacity(0)]),
            startPoint: CGPoint(x: 0, y: lane.minY),
            endPoint: CGPoint(x: 0, y: lane.maxY)
        ))
        let glyphs = Self.glyphs(caption)
        let left = width / 2 - glyphs.width / 2
        let baseline = captionY + M.captionSize * 0.36
        let n = glyphs.letters.count
        let ink = Color(hex: textColor)
        for i in 0..<n {
            let p = Self.clamp01(reveal * (1 + M.letterSpread) - (n > 1 ? Double(i) / Double(n - 1) : 0) * M.letterSpread)
            if p <= 0 { continue }
            let e = Self.easeOut(p)
            let blur = reducedMotion ? 0 : (1 - e) * M.letterBlur
            let rise = reducedMotion ? 0 : (1 - e) * M.letterRise
            let text = Text(glyphs.letters[i]).font(.custom(Self.captionFontName, fixedSize: M.captionSize)).foregroundColor(ink)
            context.drawLayer { layer in
                layer.opacity = e
                if blur > 0.1 { layer.addFilter(.blur(radius: M.blurRadius(blur))) }
                Self.draw(text, in: &layer, x: left + glyphs.at[i], baseline: baseline + rise)
            }
        }
        // Rules drawn out from the caption to the margins, once most of it has arrived.
        let ruled = Self.easeOut(Self.clamp01((reveal - 0.45) / 0.55))
        if ruled > 0 {
            let full = left - M.ruleGap - M.ruleMargin
            let length = full * ruled
            var rule = context
            rule.opacity = 0.45 * ruled
            rule.fill(Path(CGRect(x: left - M.ruleGap - length, y: captionY - 0.5, width: length, height: 1)), with: .color(ink))
            rule.fill(Path(CGRect(x: width - left + M.ruleGap, y: captionY - 0.5, width: length, height: 1)), with: .color(ink))
        }
    }

    // MARK: A tile

    func drawTile(in context: inout GraphicsContext, index: Int, rect: CGRect, alpha: Double, sharper: BreathingGapGridPhoto? = nil, vector: Bool = false) {
        guard alpha > 0.002, rect.width > 0.5 else { return }
        let now = frame.elapsed
        switch tiles[index].kind {
        case let .photo(_, _, tint):
            let loaded = frame.photos.indices.contains(index) ? frame.photos[index] : nil
            let shown = loaded.map { Self.clamp01((now - $0.at) / M.imageFade) } ?? 0
            if shown < 1 {
                var ground = context
                ground.opacity = alpha
                ground.fill(Path(rect), with: .color(Color(hex: tint)))
            }
            guard let loaded, shown > 0 else { return }
            Self.drawSquare(loaded.image, in: &context, rect: rect, alpha: alpha * shown)
            if let sharper {
                // The larger copy, once it arrives, fades in over the small one.
                Self.drawSquare(sharper.image, in: &context, rect: rect, alpha: alpha * Self.clamp01((now - sharper.at) / M.imageFade))
            }
            // The veil darkening its lower half is baked into the photo when it arrives.
        case let .type(lines, ink, paper):
            if !vector {
                // On the wall the tile is placed as the image it was set into once.
                var page = context
                page.opacity = alpha
                page.draw(Image(uiImage: BreathingGapGridTypeImages.image(lines: lines, ink: ink, paper: paper)).interpolation(.medium), in: rect)
                return
            }
            var page = context
            page.opacity = alpha
            page.fill(Path(rect), with: .color(Color(hex: paper)))
            let widths = lines.map { Self.advance($0, Self.typeFont) }
            let widest = widths.max() ?? 1
            let lineHeight = M.typeSize * 0.86
            let block = lineHeight * Double(lines.count)
            let scale = min(rect.width * M.typeFill / widest, rect.width * M.typeFill / block)
            page.translateBy(x: rect.midX, y: rect.midY)
            page.scaleBy(x: scale, y: scale)
            for (i, line) in lines.enumerated() {
                let baseline = -block / 2 + lineHeight * Double(i + 1) - M.typeSize * 0.14
                let text = Text(line).font(.custom("HelveticaNeue-Bold", fixedSize: M.typeSize)).foregroundColor(Color(hex: ink))
                Self.draw(text, in: &page, x: -widths[i] / 2, baseline: baseline)
            }
        }
    }

    /// The middle square of a photo, sampled smoothly at any size.
    private static func drawSquare(_ image: UIImage, in context: inout GraphicsContext, rect: CGRect, alpha: Double) {
        guard alpha > 0 else { return }
        var layer = context
        layer.opacity = alpha
        let w = image.size.width
        let h = image.size.height
        let s = min(w, h)
        if abs(w - h) < 0.5 {
            layer.draw(Image(uiImage: image).interpolation(.medium), in: rect)
        } else {
            let scale = rect.width / s
            layer.clip(to: Path(rect))
            layer.draw(Image(uiImage: image).interpolation(.medium), in: CGRect(
                x: rect.midX - w * scale / 2, y: rect.midY - h * scale / 2, width: w * scale, height: h * scale
            ))
        }
    }

    /// Text with its baseline's left end at (x, baseline).
    static func draw(_ text: Text, in context: inout GraphicsContext, x: Double, baseline: Double) {
        let resolved = context.resolve(text)
        let size = resolved.measure(in: CGSize(width: 10_000, height: 10_000))
        let top = resolved.firstBaseline(in: size)
        context.draw(resolved, in: CGRect(x: x, y: baseline - top, width: size.width, height: size.height))
    }
}
