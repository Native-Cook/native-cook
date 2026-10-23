import CoreText
import SwiftUI
import UIKit

/// Forty photos on a sheet of paper that morph between five layouts, each card travelling on its
/// own spring, a little after its neighbour and tumbling on the way: a spinning vortex, a drift
/// through depth, a single stack, a strip of slivers and a grid edge to edge. A tap moves on to the
/// next layout; a drag spins the vortex, turns the drift, pulls the stack or the grid, and slides
/// the strip open card by card.
struct PhotoVortexMorph: View {
    /// The photos, forty of them for the 5 by 8 grid; the last one tops the stack.
    var photos: [PhotoVortexMorphPhotoSource] = PhotoVortexMorphPhotoSource.samples
    /// Cycles vortex, drift, stack, strip and grid by itself, with holds, every 14.4 s.
    var autoPlay = false
    /// The paper the photos lie on.
    var background: UInt32 = PhotoVortexMorphMetrics.paper
    /// Called with the layout's index whenever a tap moves to the next one.
    var onLayoutChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The cards' motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = PhotoVortexMorphEngine()

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
        .task(id: photos.count) { await loadPhotos() }
    }

    private var settings: PhotoVortexMorphSettings {
        PhotoVortexMorphSettings(
            count: photos.count,
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            onLayoutChange: onLayoutChange
        )
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = settings
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, width: size.width, height: size.height, settings: settings)
            Canvas(rendersAsynchronously: false) { context, _ in
                PhotoVortexMorphPainter(frame: frame, background: background, top: insets.top, bottom: insets.bottom, reducedMotion: reduceMotion)
                    .draw(in: &context)
            }
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: background))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchMoved($0, settings: settings) }
                .onEnded { engine.touchEnded($0, settings: settings) }
        )
        .accessibilityElement()
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Photo vortex")
        .accessibilityHint("Tap to morph the photos into the next layout; drag to spin, turn or slide them")
    }

    // Each photo arrives on its own; until then its card shows the card tone.
    private func loadPhotos() async {
        engine.resetPhotos(count: photos.count)
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, photo) in photos.enumerated() {
                group.addTask { (index, await PhotoVortexMorphPhotos.load(photo.uri)) }
            }
            for await (index, image) in group {
                if let image { engine.photoArrived(image, at: index) }
            }
        }
    }
}

/// Draws one frame into a Canvas, the way the RN version records it into a Skia picture.
private struct PhotoVortexMorphPainter {
    let frame: PhotoVortexMorphFrame
    let background: UInt32
    let top: Double
    let bottom: Double
    let reducedMotion: Bool

    private typealias M = PhotoVortexMorphMetrics

    // Helvetica Neue, as the Skia fonts were matched on iOS.
    private static let labelFont = "HelveticaNeue-Medium"
    private static let titleFont = "HelveticaNeue-Medium"
    private static let lineFont = "HelveticaNeue"

    func draw(in context: inout GraphicsContext) {
        let width = frame.width
        let height = frame.height
        let n = frame.count
        context.fill(Path(CGRect(x: 0, y: 0, width: width, height: height)), with: .color(Color(hex: background)))
        guard frame.state.count == n * M.k * 2 else { return }

        if frame.settled {
            // A ring of overlapping cards has a seam where the last card meets the first. It is drawn
            // twice, the seam on opposite sides, and each copy shows only the half away from its seam.
            let far = hypot(width, height)
            let turn = CGAffineTransform(translationX: width / 2, y: height / 2).rotated(by: frame.spin)
            for side in [-1.0, 1.0] {
                var half = context
                // The half-plane on one side of the line through the middle across card 0's spoke.
                half.clip(to: Path(CGRect(x: side < 0 ? -far : 0, y: -far, width: far, height: far * 2)).applying(turn))
                let start = side < 0 ? 0 : n / 2
                for k in 0..<n {
                    drawCard(in: &half, (start + k) % n)
                }
            }
        } else {
            let state = frame.state
            let order = (0..<n).sorted { state[$0 * M.k + M.layer] < state[$1 * M.k + M.layer] }
            for i in order { drawCard(in: &context, i) }
        }

        drawLabels(in: &context)
    }

    /// A photo cropped to fill its card, faded in once it arrives.
    ///
    /// The card's pose is the React Native version's perspective matrix about the card's middle; a
    /// GraphicsContext takes affine transforms only, so it is drawn with that matrix's linear part,
    /// which is exact for a flat card and within a few percent for the slightly tipped ones.
    private func drawCard(in context: inout GraphicsContext, _ i: Int) {
        let s = frame.state
        let o = i * M.k
        let w = s[o + M.w]
        let h = s[o + M.h]
        guard w >= 0.5, h >= 0.5 else { return }
        let roll = s[o + M.roll]
        let yaw = s[o + M.yaw]
        let pitch = s[o + M.pitch]
        let cY = cos(yaw), sY = sin(yaw)
        let cP = cos(pitch), sP = sin(pitch)
        let cR = cos(roll), sR = sin(roll)
        var card = context
        card.concatenate(CGAffineTransform(
            a: cY * cR + sY * sP * sR,
            b: cP * sR,
            c: -cY * sR + sY * sP * cR,
            d: cP * cR,
            tx: s[o + M.x],
            ty: s[o + M.y]
        ))
        let rect = CGRect(x: -w / 2, y: -h / 2, width: w, height: h)
        card.opacity = frame.alpha
        card.fill(Path(rect), with: .color(Color(hex: M.cardTone)))
        guard frame.photos.indices.contains(i), let loaded = frame.photos[i] else { return }
        let shown = M.clamp((frame.elapsed - loaded.at) / M.imageFade, 0, 1)
        guard shown > 0 else { return }
        let iw = loaded.image.size.width
        let ih = loaded.image.size.height
        let fit = max(w / iw, h / ih)
        card.opacity = frame.alpha * shown
        card.clip(to: Path(rect))
        card.draw(
            Image(uiImage: loaded.image).interpolation(.medium),
            in: CGRect(x: -iw * fit / 2, y: -ih * fit / 2, width: iw * fit, height: ih * fit)
        )
    }

    // MARK: Labels

    /// The labels, in white set to difference, so they read dark on the paper and light on photos.
    private func drawLabels(in context: inout GraphicsContext) {
        var ink = context
        ink.blendMode = .difference
        let width = frame.width
        let topLine = top + 22
        let label = PhotoVortexMorphSkiaText.font(Self.labelFont, 11)
        drawTracked("KINETIC ARCHIVE", in: &ink, x: 20, y: topLine, alpha: 1)
        let fresh = frame.fresh
        let base = frame.height - bottom - 26
        if frame.layout != frame.previous && fresh < 1 {
            drawCaption(in: &ink, index: frame.previous, shown: 1 - fresh, rise: -fresh * M.captionRise, top: topLine, base: base)
        }
        drawCaption(in: &ink, index: frame.layout, shown: fresh, rise: (1 - fresh) * M.captionRise, top: topLine, base: base)
        let hint = "TAP TO MORPH"
        let hintWidth = PhotoVortexMorphSkiaText.tracked(hint, label, tracking: 1.2).width
        drawTracked(hint, in: &ink, x: width - 20 - hintWidth, y: base, alpha: 0.5)
    }

    /// The layout's number in the top corner and its name and line at the bottom, `rise` pt low.
    private func drawCaption(in context: inout GraphicsContext, index: Int, shown: Double, rise: Double, top: Double, base: Double) {
        guard shown > 0.01 else { return }
        let count = String(format: "%02d / %02d", index + 1, M.layouts)
        let label = PhotoVortexMorphSkiaText.font(Self.labelFont, 11)
        let countWidth = PhotoVortexMorphSkiaText.tracked(count, label, tracking: 1.2).width
        drawTracked(count, in: &context, x: frame.width - 20 - countWidth, y: top + rise * 0.5, alpha: shown)
        drawText(M.names[index], font: Self.titleFont, size: 30, in: &context, x: 18, baseline: base - 22 + rise, alpha: shown)
        drawText(M.lines[index], font: Self.lineFont, size: 13, in: &context, x: 20, baseline: base + rise, alpha: shown * 0.62)
    }

    private func drawTracked(_ text: String, in context: inout GraphicsContext, x: Double, y: Double, alpha: Double) {
        let run = PhotoVortexMorphSkiaText.tracked(text, PhotoVortexMorphSkiaText.font(Self.labelFont, 11), tracking: 1.2)
        for (letter, at) in zip(run.letters, run.at) where letter != " " {
            drawText(letter, font: Self.labelFont, size: 11, in: &context, x: x + at, baseline: y, alpha: alpha)
        }
    }

    /// Text whose baseline starts at (`x`, `baseline`).
    private func drawText(_ string: String, font: String, size: CGFloat, in context: inout GraphicsContext, x: Double, baseline: Double, alpha: Double) {
        var layer = context
        layer.opacity = alpha
        let resolved = layer.resolve(Text(string).font(.custom(font, fixedSize: size)).foregroundColor(.white))
        let bounds = resolved.measure(in: CGSize(width: 10_000, height: 10_000))
        let ascent = resolved.firstBaseline(in: bounds)
        layer.draw(resolved, in: CGRect(x: x, y: baseline - ascent, width: bounds.width, height: bounds.height))
    }
}

/// Text measured and spaced the way the RN version's Skia fonts do it, so tracked labels line up.
///
/// Skia's `measureText` returns the ink bounds of the glyphs, rounded out to whole points and
/// padded by one on each side, not their advance; the RN code steps from letter to letter by that
/// width plus its tracking.
enum PhotoVortexMorphSkiaText {
    private static let fonts = NSCache<NSString, CTFont>()

    static func font(_ name: String, _ size: CGFloat) -> CTFont {
        let key = "\(name)-\(size)" as NSString
        if let hit = fonts.object(forKey: key) { return hit }
        let font = CTFontCreateWithName(name as CFString, size, nil)
        fonts.setObject(font, forKey: key)
        return font
    }

    static func width(_ text: String, _ font: CTFont) -> Double {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
        let ink = CTLineGetBoundsWithOptions(line, .useGlyphPathBounds)
        guard ink.width > 0 else { return 0 }
        return Double(ceil(ink.maxX) - floor(ink.minX)) + 2
    }

    /// A letter's step; a lone space has no ink, so it is taken from between two letters.
    static func advance(_ letter: String, _ font: CTFont) -> Double {
        if letter != " " { return width(letter, font) }
        return width("a a", font) - width("aa", font)
    }

    /// Each letter of `text` and where it starts, `tracking` pt apart, and the whole run's width.
    static func tracked(_ text: String, _ font: CTFont, tracking: Double) -> (letters: [String], at: [Double], width: Double) {
        var letters: [String] = []
        var at: [Double] = []
        var x = 0.0
        for letter in text {
            letters.append(String(letter))
            at.append(x)
            x += advance(String(letter), font) + tracking
        }
        return (letters, at, x - tracking)
    }
}
