import CoreText
import SwiftUI
import UIKit

/// A strip of 35mm film crossing a dark light table at a slant: drawn perforations, frame numbers
/// and orange edge printing, a loupe of light in the middle where the frame under it stands a
/// little larger. Dragging winds the strip with momentum; at speed it shears and bows and the
/// frames blur along it. Tapping the centred frame lifts it out into a white-bordered print with a
/// caption while the strip dims behind; a tap drops it back.
struct FilmStripReel: View {
    var frames: [FilmStripReelFrame] = FilmStripReelFrame.samples
    /// Winds a dozen frames on, lifts the centred one into a print and drops it back, twice every 14 s.
    var autoPlay = false
    /// Shown in the corner and on every print's stamp.
    var roll = "07"
    /// Called with a frame's index when it is lifted into a print.
    var onPrint: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The roll's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = FilmStripReelEngine()

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
        .task(id: frames.count) { await loadPhotos() }
    }

    private var settings: FilmStripReelSettings {
        FilmStripReelSettings(count: frames.count, autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion, onPrint: onPrint)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = settings
        let painter = FilmStripReelPainter(width: size.width, frames: frames, roll: roll, insetTop: insets.top, insetBottom: insets.bottom, reducedMotion: reduceMotion)
        return TimelineView(.animation) { timeline in
            let scene = engine.step(to: timeline.date, width: size.width, height: size.height, settings: settings)
            Canvas(rendersAsynchronously: false) { context, _ in
                painter.draw(scene, in: &context)
            }
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: FilmStripReelMetrics.night))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchMoved($0, settings: settings) }
                .onEnded { engine.touchEnded($0, settings: settings) }
        )
        .accessibilityElement()
        .accessibilityAddTraits(.allowsDirectInteraction)
        .accessibilityLabel("Film strip, roll \(roll), \(frames.count) frames")
        .accessibilityHint("Drag to wind the film; tap the frame in the middle to lift it into a print")
    }

    private func loadPhotos() async {
        engine.resetPhotos(count: frames.count)
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, frame) in frames.enumerated() {
                group.addTask { (index, await FilmStripReelPhotos.load(frame.uri)) }
            }
            for await (index, image) in group {
                if let image { engine.photoArrived(image, at: index) }
            }
        }
    }
}

/// Draws one frame into a Canvas, the way the RN version records it into a Skia picture.
private struct FilmStripReelPainter {
    let frameW: Double
    let frameH: Double
    let pitch: Double
    let frames: [FilmStripReelFrame]
    let roll: String
    let insetTop: Double
    let insetBottom: Double
    let reducedMotion: Bool
    /// A strip segment: film base with its perforations cut out, a frame wide, centred on the origin.
    let segment: Path

    private typealias M = FilmStripReelMetrics
    private typealias T = FilmStripReelSkiaText

    // Helvetica Neue and Menlo, as the Skia fonts were matched on iOS.
    private static let code = "Menlo-Bold"
    private static let label = "HelveticaNeue-Medium"
    private static let line = "HelveticaNeue"

    init(width: Double, frames: [FilmStripReelFrame], roll: String, insetTop: Double, insetBottom: Double, reducedMotion: Bool) {
        frameW = width * M.frameW
        frameH = frameW * 2 / 3
        pitch = frameW + M.frameGap
        self.frames = frames
        self.roll = roll
        self.insetTop = insetTop
        self.insetBottom = insetBottom
        self.reducedMotion = reducedMotion
        var path = Path()
        let half = pitch / 2 + 1
        let height = frameH + M.rail * 2
        path.addRect(CGRect(x: -half, y: -height / 2, width: half * 2, height: height))
        let perfW = pitch / Double(M.perfs) * 0.5
        for k in 0..<M.perfs {
            let x = -pitch / 2 + (Double(k) + 0.25) * (pitch / Double(M.perfs))
            for y in [-height / 2 + M.edge, height / 2 - M.edge - M.perfH] {
                path.addRoundedRect(in: CGRect(x: x, y: y, width: perfW, height: M.perfH), cornerSize: CGSize(width: 2, height: 2))
            }
        }
        segment = path
    }

    func draw(_ scene: FilmStripReelScene, in context: inout GraphicsContext) {
        let width = scene.width
        let height = scene.height
        let n = frames.count
        let motion: Double = reducedMotion ? 0 : 1
        let cx = width / 2
        let cy = height * M.stripY
        let up = scene.lift
        let upc = M.clamp(up, 0, 1)
        context.fill(Path(CGRect(x: 0, y: 0, width: width, height: height)), with: .color(Color(hex: M.night)))

        // The light table, glowing under the loupe; it shows through the perforations.
        let glow = Gradient(stops: [
            .init(color: Color(hex: M.glow, opacity: 0.5), location: 0),
            .init(color: Color(hex: M.glow, opacity: 0.12), location: 0.4),
            .init(color: Color(hex: M.glow, opacity: 0), location: 1),
        ])
        context.fill(
            Path(CGRect(x: 0, y: 0, width: width, height: height)),
            with: .radialGradient(glow, center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: width * 0.9)
        )
        guard n > 0 else { return }

        // The strip, shrinking back a little while a print is up; the middle frame drawn last.
        var strip = context
        let back = 1 - M.shrink * upc * motion
        strip.translateBy(x: cx, y: cy)
        strip.scaleBy(x: back, y: back)
        strip.translateBy(x: -cx, y: -cy)
        let blur = M.clamp(abs(scene.speed) * M.blur, 0, M.blurMax) * motion
        let o = scene.offset
        let order = (0..<n)
            .filter { abs(M.wrap(Double($0) - o, n)) < 3.2 }
            .sorted { abs(M.wrap(Double($0) - o, n)) > abs(M.wrap(Double($1) - o, n)) }
        for i in order {
            let d = M.wrap(Double(i) - o, n)
            let pose = FilmStripReelPose(d: d, pitch: pitch, speed: scene.speed, cx: cx, cy: cy, motion: motion)
            let shade = min(M.fadeMax, max(0, abs(d) - 0.15) * M.fadeStep)
            drawSegment(in: &strip, scene, i, pose, empty: i == scene.lifted && up > 0.001, shade: shade, blur: blur)
        }

        // The loupe: a ring of light over the middle frame.
        let r = frameW * M.loupe
        context.stroke(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)), with: .color(Color(hex: M.cream, opacity: 0.22 * (1 - upc))), lineWidth: 1.25)
        let r2 = r + 6
        context.stroke(Path(ellipseIn: CGRect(x: cx - r2, y: cy - r2, width: r2 * 2, height: r2 * 2)), with: .color(Color(hex: M.cream, opacity: 0.08 * (1 - upc))), lineWidth: 1.25)

        // The room dims behind a print.
        if up > 0.001 {
            context.fill(Path(CGRect(x: 0, y: 0, width: width, height: height)), with: .color(Color(hex: M.night, opacity: M.dim * upc)))
        }

        if scene.lifted >= 0 && up > 0.001 { drawPrint(in: &context, scene) }

        // Corners: the roll, the frame count, and how to use it.
        let cream = Color(hex: M.cream)
        let font = T.font(Self.label, 11)
        let topY = insetTop + 24
        drawTracked("ROLL \(roll) — COASTLINE", font: Self.label, size: 11, tracking: 1.4, color: cream, in: &context, x: 20, baseline: topY, alpha: 0.75)
        let centred = o.isFinite ? ((Int(o.rounded(.toNearestOrAwayFromZero)) % n) + n) % n : 0
        let count = String(format: "%02d/%d", centred + 1, n)
        drawTracked(count, font: Self.label, size: 11, tracking: 1.4, color: cream, in: &context, x: width - 20 - T.tracked(count, font, tracking: 1.4).width, baseline: topY, alpha: 0.5)
        let hint = up > 0.5 ? "TAP TO RETURN IT TO THE ROLL" : "DRAG TO WIND · TAP THE FRAME TO PRINT"
        drawTracked(hint, font: Self.label, size: 11, tracking: 1.4, color: cream, in: &context, x: (width - T.tracked(hint, font, tracking: 1.4).width) / 2, baseline: height - insetBottom - 22, alpha: 0.38)
    }

    /// One frame of film: the base and its perforations, the photo, and the orange edge printing.
    private func drawSegment(in context: inout GraphicsContext, _ scene: FilmStripReelScene, _ index: Int, _ pose: FilmStripReelPose, empty: Bool, shade: Double, blur: Double) {
        var seg = context
        seg.translateBy(x: pose.x, y: pose.y)
        seg.rotate(by: .radians(pose.angle))
        seg.scaleBy(x: pose.scale, y: pose.scale)
        seg.concatenate(CGAffineTransform(a: 1, b: 0, c: pose.shear, d: 1, tx: 0, ty: 0))
        seg.fill(segment, with: .color(Color(hex: M.base)), style: FillStyle(eoFill: true, antialiased: true))

        // The photo, blurred along the strip at speed; an empty window once it has been lifted out.
        let fx = -frameW / 2
        let fy = -frameH / 2
        let window = CGRect(x: fx, y: fy, width: frameW, height: frameH)
        var photo = seg
        photo.clip(to: Path(window))
        photo.fill(Path(window), with: .color(Color(hex: M.window)))
        if !empty {
            drawPhoto(in: &photo, scene.photos.indices.contains(index) ? scene.photos[index] : nil, window, now: scene.now, alpha: 1, blur: blur > 0.4 ? blur : 0)
        } else {
            photo.stroke(Path(window.insetBy(dx: 6, dy: 6)), with: .color(Color(hex: M.emptyLine)), lineWidth: 1)
        }

        // Edge printing: the frame number by the perforations above, the stock and roll below.
        let orange = Color(hex: M.orange)
        let number = String(index + 1)
        let top = fy - M.rail + M.edge + M.perfH + 11
        let bottom = fy + frameH + M.rail - M.edge - M.perfH - 4
        drawText(number, font: Self.code, size: 8, color: orange, in: &seg, x: fx + 4, baseline: top, alpha: 0.85)
        drawText("▸ \(number)A", font: Self.code, size: 8, color: orange, in: &seg, x: fx + frameW * 0.5, baseline: top, alpha: 0.85)
        drawText("NC 400T  5207  \(roll)", font: Self.code, size: 8, color: orange, in: &seg, x: fx + 4, baseline: bottom, alpha: 0.6)
        drawText("\(number)A", font: Self.code, size: 8, color: orange, in: &seg, x: fx + frameW - 22, baseline: bottom, alpha: 0.6)

        // Frames away from the loupe fall into the dark.
        if shade > 0.005 {
            seg.fill(
                Path(CGRect(x: -pitch / 2 - 1, y: -frameH / 2 - M.rail, width: pitch + 2, height: frameH + M.rail * 2)),
                with: .color(Color(hex: M.night, opacity: shade))
            )
        }
    }

    /// A photo cropped to fill `rect`, faded in once it has loaded.
    ///
    /// The RN version blurs it along the strip with a one-axis Gaussian image filter; here the
    /// same Gaussian is sampled with copies of the photo slid along the strip and averaged.
    private func drawPhoto(in context: inout GraphicsContext, _ loaded: FilmStripReelPhoto?, _ rect: CGRect, now: Double, alpha: Double, blur: Double) {
        guard let loaded else { return }
        let shown = alpha * M.clamp((now - loaded.at) / M.imageFade, 0, 1)
        guard shown > 0 else { return }
        let iw = loaded.image.size.width
        let ih = loaded.image.size.height
        let fit = max(rect.width / iw, rect.height / ih)
        let dest = CGRect(x: rect.midX - iw * fit / 2, y: rect.midY - ih * fit / 2, width: iw * fit, height: ih * fit)
        var layer = context
        layer.clip(to: Path(rect))
        let image = layer.resolve(Image(uiImage: loaded.image).interpolation(.medium))
        if blur <= 0 {
            layer.opacity = shown
            layer.draw(image, in: dest)
            return
        }
        layer.opacity = shown
        layer.drawLayer { taps in
            // Each copy drawn over the ones before at its share of the weight so far: the result is
            // the weighted average of all of them.
            let count = 9
            var total = 0.0
            for k in 0..<count {
                let x = (Double(k) / Double(count - 1) * 2 - 1) * 2 * blur
                let weight = exp(-x * x / (2 * blur * blur))
                total += weight
                taps.opacity = weight / total
                taps.draw(image, in: dest.offsetBy(dx: x, dy: 0))
            }
        }
    }

    /// The print, flying from its window in the strip to the middle of the screen.
    private func drawPrint(in context: inout GraphicsContext, _ scene: FilmStripReelScene) {
        // The lifted frame indexes both the photos and the captions below.
        guard frames.indices.contains(scene.lifted), scene.photos.indices.contains(scene.lifted) else { return }
        let width = scene.width
        let height = scene.height
        let up = scene.lift
        let upc = M.clamp(up, 0, 1)
        let motion: Double = reducedMotion ? 0 : 1
        let lifting = scene.lifted
        let from = FilmStripReelPose(d: M.wrap(Double(lifting) - scene.offset, frames.count), pitch: pitch, speed: scene.speed, cx: width / 2, cy: height * M.stripY, motion: motion)
        let k = reducedMotion ? 1 : up
        let printW = width - M.printMargin * 2
        let photoW = printW - M.border * 2
        let photoH = photoW * 2 / 3
        let s = M.mix(from.scale * (frameW / photoW), 1, k)
        let border = M.border * M.clamp(up, 0, 1.2)
        let caption = M.caption * M.clamp(up, 0, 1.2)
        let alpha = reducedMotion ? upc : 1
        var print = context
        print.translateBy(x: M.mix(from.x, width / 2, k), y: M.mix(from.y, height * M.printY, k))
        print.rotate(by: .radians(M.mix(from.angle, M.printTilt, k)))
        print.scaleBy(x: s, y: s)
        let left = -photoW / 2 - border
        let top = -photoH / 2 - border
        let paperW = photoW + border * 2
        let paperH = photoH + border + caption

        // A soft shadow under the paper.
        var shadow = print
        shadow.opacity = 0.5 * upc * alpha
        shadow.drawLayer { layer in
            layer.addFilter(.blur(radius: M.shadowSigma * 2))
            layer.fill(Path(CGRect(x: left + 4, y: top + 18 * upc, width: paperW - 8, height: paperH)), with: .color(.black))
        }
        print.fill(Path(CGRect(x: left, y: top, width: paperW, height: paperH)), with: .color(Color(hex: M.paper, opacity: alpha)))
        drawPhoto(in: &print, scene.photos[lifting], CGRect(x: -photoW / 2, y: -photoH / 2, width: photoW, height: photoH), now: scene.now, alpha: alpha, blur: 0)

        // The date stamp, burnt in orange in the photo's corner, and the caption on the paper.
        let words = M.clamp((up - 0.55) / 0.4, 0, 1) * alpha
        guard words > 0.01 else { return }
        let day = String(lifting % 28 + 1)
        let stamp = "'98 7 " + (day.count < 2 ? " " + day : day)
        let stampWidth = T.width(stamp, T.font(Self.code, 11))
        drawText(stamp, font: Self.code, size: 11, color: Color(hex: M.orange), in: &print, x: photoW / 2 - 12 - stampWidth, baseline: photoH / 2 - 12, alpha: 0.9 * words)
        let ink = Color(hex: M.printInk)
        let item = frames[lifting]
        drawText(item.title, font: Self.label, size: 18, color: ink, in: &print, x: left + M.border + 2, baseline: photoH / 2 + 30, alpha: words)
        drawText(item.line, font: Self.line, size: 12, color: ink, in: &print, x: left + M.border + 2, baseline: photoH / 2 + 48, alpha: 0.55 * words)
        let number = "\(lifting + 1)A / \(roll)"
        let numberWidth = T.tracked(number, T.font(Self.label, 11), tracking: 1).width
        drawTracked(number, font: Self.label, size: 11, tracking: 1, color: ink, in: &print, x: photoW / 2 - numberWidth - 2, baseline: photoH / 2 + 30, alpha: 0.55 * words)
    }

    private func drawTracked(_ text: String, font: String, size: CGFloat, tracking: Double, color: Color, in context: inout GraphicsContext, x: Double, baseline: Double, alpha: Double) {
        let run = T.tracked(text, T.font(font, size), tracking: tracking)
        for (letter, at) in zip(run.letters, run.at) where letter != " " {
            drawText(letter, font: font, size: size, color: color, in: &context, x: x + at, baseline: baseline, alpha: alpha)
        }
    }

    /// Text whose baseline starts at (`x`, `baseline`).
    private func drawText(_ string: String, font: String, size: CGFloat, color: Color, in context: inout GraphicsContext, x: Double, baseline: Double, alpha: Double) {
        guard alpha > 0.001 else { return }
        var layer = context
        layer.opacity = alpha
        let resolved = layer.resolve(Text(string).font(.custom(font, fixedSize: size)).foregroundColor(color))
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
enum FilmStripReelSkiaText {
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
