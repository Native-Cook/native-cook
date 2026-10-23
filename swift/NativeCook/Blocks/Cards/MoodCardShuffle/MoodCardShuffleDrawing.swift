import CoreImage
import CoreText
import SwiftUI
import UIKit

// MARK: - Type

/// Avenir Next, as the RN version sets it: demi bold (600) and medium (500), never lighter.
enum MoodCardShuffleType {
    static func uiFont(_ name: String, _ size: CGFloat, _ weight: UIFont.Weight) -> UIFont {
        UIFont(name: name, size: size) ?? .systemFont(ofSize: size, weight: weight)
    }

    static let regionUI = uiFont("AvenirNext-DemiBold", 9.5, .semibold)
    static let nameUI = uiFont("AvenirNext-DemiBold", 21, .semibold)
    static let blurbUI = uiFont("AvenirNext-Medium", 12.5, .medium)
    static let chipUI = uiFont("AvenirNext-DemiBold", 11, .semibold)

    static let region = demiBold(9.5)
    static let name = demiBold(21)
    static let blurb = medium(12.5)
    static let chip = demiBold(11)

    static func medium(_ size: CGFloat) -> Font { .custom("AvenirNext-Medium", fixedSize: size) }
    static func demiBold(_ size: CGFloat) -> Font { .custom("AvenirNext-DemiBold", fixedSize: size) }

    /// A string's width as the RN version measures the chip and the blurb lines: Skia's
    /// `measureText(…).width` is the width of the glyphs' ink bounds rounded out to whole points and
    /// padded for antialiasing. Fitted on the RN rendering: ink rounded out + 1.75 pt.
    static func measure(_ text: String, _ font: UIFont) -> Double {
        guard !text.isEmpty else { return 0 }
        let attributed = NSAttributedString(string: text, attributes: [.font: font])
        let line = CTLineCreateWithAttributedString(attributed)
        let ink = CTLineGetBoundsWithOptions(line, .useGlyphPathBounds)
        guard !ink.isNull, !ink.isEmpty, ink.width.isFinite else { return 0 }
        return Double(ceil(ink.maxX) - floor(ink.minX)) + 1.75
    }

    /// Breaks `text` into at most two lines of `width` pt, the second ending in "…" if it overflows.
    static func wrapTwo(_ text: String, font: UIFont, width: Double) -> [String] {
        let words = text.split(separator: " ", omittingEmptySubsequences: false).map(String.init)
        var lines: [String] = []
        var line = ""
        for word in words {
            let next = line.isEmpty ? word : "\(line) \(word)"
            if measure(next, font) <= width || line.isEmpty {
                line = next
                continue
            }
            if lines.count == 1 {
                // Second line full: end it with an ellipsis that fits.
                var cut = "\(line)…"
                while cut.count > 1 && measure(cut, font) > width {
                    var trimmed = String(cut.dropLast(2))
                    while trimmed.last == " " { trimmed.removeLast() }
                    cut = "\(trimmed)…"
                }
                lines.append(cut)
                return lines
            }
            lines.append(line)
            line = word
        }
        if !line.isEmpty { lines.append(line) }
        return Array(lines.prefix(2))
    }

    /// A place as the canvas draws it, its blurb set for a card `cardWidth` pt wide.
    static func face(_ place: MoodCardShufflePlace, cardWidth: Double) -> MoodCardShuffleFace {
        let region = place.region.uppercased()
        return MoodCardShuffleFace(
            name: place.name,
            region: region,
            photo: place.photo,
            lines: cardWidth > 0 ? wrapTwo(place.blurb, font: blurbUI, width: cardWidth - 32) : [],
            regionLetters: letterPositions(region, regionUI),
            chipWidth: measure("Curated", chipUI) + 20
        )
    }

    /// Where each letter (UTF-16 unit, as RN indexes the string) of `text` starts: its glyph advance
    /// plus `regionTracking` between letters.
    static func letterPositions(_ text: String, _ font: UIFont) -> [(String, Double)] {
        let ctFont = font as CTFont
        var out: [(String, Double)] = []
        var at = 0.0
        for unit in text.utf16 {
            var char = unit
            var glyph: CGGlyph = 0
            var advance = 0.0
            if CTFontGetGlyphsForCharacters(ctFont, &char, &glyph, 1), glyph != 0 {
                var size = CGSize.zero
                CTFontGetAdvancesForGlyphs(ctFont, .horizontal, &glyph, &size, 1)
                if size.width.isFinite { advance = Double(size.width) }
            }
            out.append((String(utf16CodeUnits: [unit], count: 1), at))
            at += advance + MoodCardShuffleMetrics.regionTracking
        }
        return out
    }
}

// MARK: - Drawing

enum MoodCardShufflePaint {
    private typealias M = MoodCardShuffleMetrics

    /// Room around a card's own drawing for its shadow, so the view that holds it never clips it.
    static let margin = 64.0

    private static let ink = Color(.sRGB, red: 0.08, green: 0.08, blue: 0.06)

    private static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(.sRGB, red: r, green: g, blue: b, opacity: a)
    }

    /// The bookmark glyph, 9 × 12, centred on (0, 0).
    private static let bookmark: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -4.5, y: -6))
        p.addLine(to: CGPoint(x: 4.5, y: -6))
        p.addLine(to: CGPoint(x: 4.5, y: 6))
        p.addLine(to: CGPoint(x: 0, y: 2.6))
        p.addLine(to: CGPoint(x: -4.5, y: 6))
        p.closeSubpath()
        return p
    }()

    private static func roundRect(_ rect: CGRect, _ r: Double) -> Path {
        Path(CGPath(roundedRect: rect, cornerWidth: r, cornerHeight: r, transform: nil))
    }

    /// Draws `text` with its baseline at `baseline`, starting at `x`.
    private static func text(_ ctx: GraphicsContext, _ string: String, _ font: Font, _ color: Color, x: Double, baseline: Double) {
        let resolved = ctx.resolve(Text(string).font(font).foregroundColor(color))
        let size = resolved.measure(in: CGSize(width: 2000, height: 400))
        let ascent = resolved.firstBaseline(in: size)
        ctx.draw(resolved, at: CGPoint(x: x, y: baseline - Double(ascent)), anchor: .topLeading)
    }

    /// One card, drawn in its own coordinates (w × h, centred on the context's origin).
    static func card(_ context: GraphicsContext, item: MoodCardShuffleItem, w: Double, h: Double, now: Double, shadow: UIImage?) {
        let p = item.pose
        let rect = CGRect(x: -w / 2, y: -h / 2, width: w, height: h)
        let shape = roundRect(rect, M.cardRadius)
        var ctx = context
        if item.alpha < 0.996 {
            // The RN layer that fades the card ends 40 pt around it.
            ctx.clip(to: Path(rect.insetBy(dx: -40, dy: -40)))
        }
        if item.shadow, let shadow {
            var s = ctx
            s.opacity = M.shadowAlpha * (1 - p.dim * 0.6)
            let pad = M.shadowBlur * 3
            s.draw(
                Image(uiImage: shadow),
                in: CGRect(x: -w / 2 + 10 - pad, y: -h / 2 + M.shadowDrop - pad, width: w - 20 + pad * 2, height: h - 6 + pad * 2)
            )
        }

        var face = ctx
        face.clip(to: shape)
        // Until the photo arrives the card is a warm grey with a soft fall of light.
        face.fill(
            Path(rect),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x5a5848), Color(hex: 0x34332a)]),
                startPoint: CGPoint(x: 0, y: -h / 2),
                endPoint: CGPoint(x: 0, y: h / 2)
            )
        )
        if let loaded = item.loaded {
            let iw = Double(loaded.image.size.width)
            let ih = Double(loaded.image.size.height)
            if iw > 0 && ih > 0 {
                // Cover: crop the photo to the card's shape.
                let k = max(w / iw, h / ih)
                var art = face
                art.opacity = MoodCardShuffleMath.clamp01((now - loaded.at) / M.imageFade)
                art.draw(
                    Image(uiImage: loaded.image).interpolation(.high),
                    in: CGRect(x: -iw * k / 2, y: -ih * k / 2, width: iw * k, height: ih * k)
                )
            }
        }
        if p.text > 0.01 {
            // The shade under the text, then the chip, the bookmark and the words, all fading together.
            let t = p.text
            face.fill(
                Path(rect),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: rgba(0.03, 0.03, 0.02, 0), location: 0.32),
                        .init(color: rgba(0.03, 0.03, 0.02, 0.2 * t), location: 0.55),
                        .init(color: rgba(0.03, 0.03, 0.02, 0.8 * t), location: 1),
                    ]),
                    startPoint: CGPoint(x: 0, y: -h / 2),
                    endPoint: CGPoint(x: 0, y: h / 2)
                )
            )

            let left = -w / 2 + 16
            let top = -h / 2 + 14
            let f = item.face
            face.fill(roundRect(CGRect(x: left - 2, y: top, width: f.chipWidth, height: 22), 11), with: .color(.white.opacity(0.94 * t)))
            text(face, "Curated", MoodCardShuffleType.chip, ink.opacity(t), x: left + 8, baseline: top + 15)

            // The bookmark: a round glass button, white when the place is saved.
            var mark = face
            mark.translateBy(x: w / 2 - 14 - 15, y: top + 11 + 4)
            mark.scaleBy(x: item.pop, y: item.pop)
            let ring = Path(ellipseIn: CGRect(x: -15, y: -15, width: 30, height: 30))
            mark.fill(ring, with: .color(item.saved ? .white.opacity(t) : ink.opacity(0.32 * t)))
            if item.saved {
                mark.fill(bookmark, with: .color(ink.opacity(t)))
            } else {
                mark.stroke(Path(ellipseIn: CGRect(x: -14.5, y: -14.5, width: 29, height: 29)), with: .color(.white.opacity(0.4 * t)), lineWidth: 1)
                mark.stroke(bookmark, with: .color(.white.opacity(t)), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
            }

            let lines = f.lines
            let bottom = h / 2 - 17
            for (i, line) in lines.enumerated() {
                text(face, line, MoodCardShuffleType.blurb, .white.opacity(0.78 * t), x: left, baseline: bottom - Double(lines.count - 1 - i) * 17)
            }
            let nameY = bottom - Double(max(0, lines.count - 1)) * 17 - 22
            text(face, f.name, MoodCardShuffleType.name, .white.opacity(t), x: left, baseline: nameY)
            for (letter, x) in f.regionLetters where letter != " " {
                text(face, letter, MoodCardShuffleType.region, .white.opacity(0.72 * t), x: left + x, baseline: nameY - 24)
            }
        }
        if p.dim > 0.004 {
            face.fill(Path(rect), with: .color(rgba(0.04, 0.04, 0.03, min(1, p.dim))))
        }
        // A hairline of light along the rim.
        ctx.stroke(shape, with: .color(.white.opacity(0.14 * max(0, 1 - p.dim))), lineWidth: 1)
    }

    /// The blurred backdrop photos, cover-fit over the stage, then the warm veil.
    static func backdrop(_ ctx: GraphicsContext, size: CGSize, frame: MoodCardShuffleFrame) {
        let full = CGRect(origin: .zero, size: size)
        ctx.fill(Path(full), with: .color(Color(hex: 0x2a2820)))
        for (image, alpha) in frame.backdrops {
            let iw = Double(image.size.width)
            let ih = Double(image.size.height)
            guard iw > 0, ih > 0, alpha > 0.002 else { continue }
            let k = max(Double(size.width) / iw, Double(size.height) / ih)
            var c = ctx
            c.opacity = MoodCardShuffleMath.clamp01(alpha)
            c.draw(
                Image(uiImage: image).interpolation(.high),
                in: CGRect(x: (Double(size.width) - iw * k) / 2, y: (Double(size.height) - ih * k) / 2, width: iw * k, height: ih * k)
            )
        }
        // A warm veil: darker at the top for the title, lighter around the deck.
        ctx.fill(
            Path(full),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: rgba(0.07, 0.066, 0.047, 0.46), location: 0),
                    .init(color: rgba(0.07, 0.066, 0.047, 0.1), location: 0.45),
                    .init(color: rgba(0.07, 0.066, 0.047, 0.4), location: 1),
                ]),
                startPoint: .zero,
                endPoint: CGPoint(x: 0, y: size.height)
            )
        )
    }
}

// MARK: - Photos

/// Every photo, downloaded once (800 px wide) and kept decoded, with its blurred backdrop, and the
/// cards' soft shadow, made once.
@MainActor
final class MoodCardShufflePhotos {
    private typealias M = MoodCardShuffleMetrics

    private(set) var loaded: [String: MoodCardShuffleLoaded] = [:]
    private var loading: Set<String> = []
    private var shadowKey: CGSize = .zero
    private var shadowScale: CGFloat = 0
    private var shadowImage: UIImage?

    func load(_ ids: [String], clock: @escaping @MainActor () -> Double) async {
        await withTaskGroup(of: Void.self) { group in
            for id in Set(ids) where loaded[id] == nil && !loading.contains(id) {
                loading.insert(id)
                group.addTask { await self.fetch(id, clock: clock) }
            }
        }
    }

    private func fetch(_ id: String, clock: @MainActor () -> Double) async {
        defer { loading.remove(id) }
        let url = URL(string: "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(M.cardPhotoPx)&q=80")
        guard let url,
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data),
              image.size.width > 0, image.size.height > 0
        else { return }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let prepared = await image.byPreparingForDisplay() ?? image
        let blur = await Self.backdrop(of: image)
        if Task.isCancelled { return }
        loaded[id] = MoodCardShuffleLoaded(image: prepared, blur: blur, at: clock())
    }

    /// The cards' shadow: a blurred rounded rect, `shadowBlur` × 3 pt of room around it.
    func shadow(for card: CGSize, scale: CGFloat) -> UIImage? {
        guard card.width > 30, card.height > 30, scale > 0 else { return nil }
        if card == shadowKey && scale == shadowScale { return shadowImage }
        shadowKey = card
        shadowScale = scale
        shadowImage = Self.makeShadow(card: card, scale: scale)
        return shadowImage
    }

    // No colour management, like Skia on a plain surface: blur and colour matrix on the stored values.
    nonisolated private static let plainContext = CIContext(options: [.workingColorSpace: NSNull(), .outputColorSpace: NSNull()])
    nonisolated private static let context = CIContext()

    /// The blurred backdrop for a photo: cover-fit into 120 × 260 px, muted, blurred (sigma 9 px),
    /// then scaled up four times with a Mitchell cubic, standing in for the RN's cubic sampling.
    private static func backdrop(of image: UIImage) async -> UIImage? {
        await Task.detached(priority: .userInitiated) { () -> UIImage? in
            guard let cg = image.cgImage else { return nil }
            let input = CIImage(cgImage: cg)
            let iw = input.extent.width
            let ih = input.extent.height
            guard iw > 0, ih > 0 else { return nil }
            let w = CGFloat(M.backdropW)
            let h = CGFloat(M.backdropH)
            let k = max(w / iw, h / ih)
            let sw = w / k
            let sh = h / k
            let crop = CGRect(x: (iw - sw) / 2, y: (ih - sh) / 2, width: sw, height: sh)
            let small = input.cropped(to: crop).clampedToExtent()
                .transformed(by: CGAffineTransform(translationX: -crop.minX, y: -crop.minY))
                .transformed(by: CGAffineTransform(scaleX: k, y: k))
                .cropped(to: CGRect(x: 0, y: 0, width: w, height: h))
            // Muted towards grey, so the backdrop sets a mood without competing with the cards.
            let s = M.backdropSaturation
            let r = (1 - s) * 0.2126
            let g = (1 - s) * 0.7152
            let b = (1 - s) * 0.0722
            let muted = small.applyingFilter("CIColorMatrix", parameters: [
                "inputRVector": CIVector(x: r + s, y: g, z: b, w: 0),
                "inputGVector": CIVector(x: r, y: g + s, z: b, w: 0),
                "inputBVector": CIVector(x: r, y: g, z: b + s, w: 0),
                "inputAVector": CIVector(x: 0, y: 0, z: 0, w: 1),
                "inputBiasVector": CIVector(x: 0, y: 0, z: 0, w: 0),
            ])
            let blurred = muted.clampedToExtent()
                .applyingGaussianBlur(sigma: M.backdropBlur)
                .cropped(to: CGRect(x: 0, y: 0, width: w, height: h))
            let up = blurred.clampedToExtent()
                .applyingFilter("CIBicubicScaleTransform", parameters: [
                    kCIInputScaleKey: 4.0,
                    "inputB": 1.0 / 3,
                    "inputC": 1.0 / 3,
                ])
                .cropped(to: CGRect(x: 0, y: 0, width: w * 4, height: h * 4))
            guard let out = plainContext.createCGImage(
                up, from: CGRect(x: 0, y: 0, width: w * 4, height: h * 4),
                format: .RGBA8, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)
            ) else { return nil }
            return UIImage(cgImage: out)
        }.value
    }

    private static func makeShadow(card: CGSize, scale: CGFloat) -> UIImage? {
        let pad = CGFloat(M.shadowBlur * 3)
        let body = CGSize(width: card.width - 20, height: card.height - 6)
        let full = CGSize(width: body.width + pad * 2, height: body.height + pad * 2)
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let solid = UIGraphicsImageRenderer(size: full, format: format).image { ctx in
            let path = CGPath(
                roundedRect: CGRect(x: pad, y: pad, width: body.width, height: body.height),
                cornerWidth: M.cardRadius, cornerHeight: M.cardRadius, transform: nil
            )
            ctx.cgContext.setFillColor(UIColor.black.cgColor)
            ctx.cgContext.addPath(path)
            ctx.cgContext.fillPath()
        }
        guard let cg = solid.cgImage else { return nil }
        let input = CIImage(cgImage: cg)
        let blurred = input.applyingGaussianBlur(sigma: M.shadowBlur * Double(scale)).cropped(to: input.extent)
        guard let out = context.createCGImage(blurred, from: input.extent) else { return nil }
        return UIImage(cgImage: out, scale: scale, orientation: .up)
    }
}
