import CoreText
import SwiftUI
import UIKit

/// A herbarium of botanical plates whose column count follows a pinch continuously: spreading the
/// fingers walks 5 → 3 → 2 → 1 columns and pinching walks back, every tile sliding and growing
/// between the two neighbouring grids around the fingers rather than jumping. On release the grid
/// springs to the nearest column count with a tap of haptics. Names fade in under the plates at
/// two columns and one. A double tap toggles one column and three; a drag scrolls.
struct PinchDensityGrid: View {
    var plates: [PinchDensityGridPlate] = PinchDensityGridPlate.samples
    /// Pinches out to 2 and 1 columns, back in to 5 and out to 3 again, every 12 s.
    var autoPlay = false
    /// The paper and the ink.
    var background: UInt32 = PinchDensityGridMetrics.paper
    var ink: UInt32 = PinchDensityGridMetrics.ink
    /// Called with the column count each time the grid settles on a new one.
    var onColumnsChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The grid's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = PinchDensityGridEngine()

    private typealias M = PinchDensityGridMetrics

    var body: some View {
        // The block draws under the system bars and keeps its header clear of them itself.
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
        .task(id: plates.count) { await loadThumbs() }
    }

    private var settings: PinchDensityGridSettings {
        PinchDensityGridSettings(plates: plates, autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion, onColumnsChange: onColumnsChange)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = settings
        let stage = PinchDensityGridStage(
            width: size.width,
            height: size.height,
            top: insets.top + M.header,
            bottom: insets.bottom + 28
        )
        return TimelineView(.animation) { timeline in
            let scene = engine.step(to: timeline.date, stage: stage, settings: settings)
            Canvas(rendersAsynchronously: false) { context, _ in
                PinchDensityGridPainter(scene: scene, plates: plates, background: background, ink: ink, insetTop: insets.top, insetBottom: insets.bottom)
                    .draw(in: &context)
            }
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: background))
        .clipped()
        .overlay {
            PinchDensityGridTouches(engine: engine, settings: settings)
        }
        .accessibilityElement()
        .accessibilityAddTraits(.isImage)
        .accessibilityLabel("Herbarium, \(plates.count) botanical plates")
        .accessibilityHint("Pinch to change the number of columns; double tap to switch between one and three")
    }

    private func loadThumbs() async {
        engine.reset(plates: plates)
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, plate) in plates.enumerated() {
                let address = PinchDensityGridPlate.uri(plate.id, width: M.thumbWidth)
                group.addTask { (index, await PinchDensityGridPhotos.load(address)) }
            }
            for await (index, image) in group {
                if let image { engine.thumbArrived(image, at: index) }
            }
        }
    }
}

/// Draws one frame into a Canvas, the way the RN version records it into a Skia picture.
private struct PinchDensityGridPainter {
    let scene: PinchDensityGridScene
    let plates: [PinchDensityGridPlate]
    let background: UInt32
    let ink: UInt32
    let insetTop: Double
    let insetBottom: Double

    private typealias M = PinchDensityGridMetrics
    private typealias L = PinchDensityGridLayout
    private typealias T = PinchDensityGridSkiaText

    // Helvetica Neue, as the Skia fonts were matched on iOS.
    private static let titleFont = "HelveticaNeue-Medium"
    private static let mediumFont = "HelveticaNeue-Medium"
    private static let regularFont = "HelveticaNeue"

    func draw(in context: inout GraphicsContext) {
        let v = scene.view
        let width = v.stage.width
        let height = v.stage.height
        let paper = Color(hex: background)
        context.fill(Path(CGRect(x: 0, y: 0, width: width, height: height)), with: .color(paper))

        let zc = M.clamp(v.z, 0, Double(M.last))
        let labels = M.clamp((zc - M.labelFrom) / (M.labelTo - M.labelFrom), 0, 1) * scene.alpha
        let grow = M.clamp(zc - 2, 0, 1)
        for i in 0..<plates.count {
            let f = L.onScreen(i, v)
            if f.y > height || f.y + f.h + 60 < 0 || f.x > width || f.x + f.w < 0 { continue }
            drawPlate(in: &context, f, i)
            if labels > 0.01 { drawLabel(in: &context, f, i, shown: labels, grow: grow) }
        }

        // The paper runs under the header and the home indicator, fading into the grid.
        let head = v.stage.top - M.edgeFade + 8
        context.fill(Path(CGRect(x: 0, y: 0, width: width, height: head)), with: .color(paper))
        let clear = paper.opacity(0)
        context.fill(
            Path(CGRect(x: 0, y: head, width: width, height: M.edgeFade)),
            with: .linearGradient(Gradient(colors: [paper, clear]), startPoint: CGPoint(x: 0, y: head), endPoint: CGPoint(x: 0, y: head + M.edgeFade))
        )
        let foot = height - insetBottom - M.edgeFade
        context.fill(
            Path(CGRect(x: 0, y: foot, width: width, height: height - foot)),
            with: .linearGradient(Gradient(colors: [clear, paper]), startPoint: CGPoint(x: 0, y: foot), endPoint: CGPoint(x: 0, y: height))
        )

        // Title, count and the ladder, the current rung marked by a pill that slides between them.
        let titleY = insetTop + 34
        let inkColor = Color(hex: ink)
        drawText("Herbarium", font: Self.titleFont, size: 30, color: inkColor, in: &context, x: 18, baseline: titleY, alpha: 1)
        drawTracked("\(plates.count) PLATES · PINCH", size: 10, tracking: 1.1, color: inkColor, in: &context, x: 20, baseline: titleY + 20, alpha: 0.5)
        let step = 28.0
        let right = width - 18
        let left = right - step * Double(M.ladder.count)
        let pillX = left + step * (zc + 0.5)
        context.fill(Path(roundedRect: CGRect(x: pillX - 12, y: titleY - 19, width: 24, height: 24), cornerRadius: 12), with: .color(inkColor))
        let rungFont = T.font(Self.mediumFont, 12)
        for k in 0..<M.ladder.count {
            let label = String(M.ladder[k].cols)
            let near = M.clamp(1 - abs(zc - Double(k)), 0, 1)
            let x = left + step * (Double(k) + 0.5) - T.width(label, rungFont) / 2
            drawText(label, font: Self.mediumFont, size: 12, color: inkColor, in: &context, x: x, baseline: titleY - 3, alpha: 0.45 * (1 - near))
            drawText(label, font: Self.mediumFont, size: 12, color: paper, in: &context, x: x, baseline: titleY - 3, alpha: near)
        }
        let cols = "COLUMNS"
        let colsWidth = T.tracked(cols, T.font(Self.mediumFont, 10), tracking: 1.1).width
        drawTracked(cols, size: 10, tracking: 1.1, color: inkColor, in: &context, x: right - colsWidth - 6, baseline: titleY + 20, alpha: 0.5)

        if let clock = scene.scriptClock { drawFingers(in: &context, clock: clock, zc: zc) }
    }

    /// The scripted pinch's two fingers, while it moves.
    private func drawFingers(in context: inout GraphicsContext, clock: Double, zc: Double) {
        let v = scene.view
        for (index, scripted) in M.script.enumerated() {
            let into = clock - scripted.at
            let shown = M.clamp(min((into + M.touchFade) / M.touchFade, (scripted.span + M.touchFade - into) / M.touchFade), 0, 1)
            if shown <= 0 { continue }
            let fromRung = M.script[(index + M.script.count - 1) % M.script.count].to
            let outward = scripted.to > fromRung
            let spread = (outward ? 34.0 : 120.0) * pow(L.columnsAt(fromRung) / L.columnsAt(zc), 0.85)
            let d = M.clamp(spread, 26, 150) * 0.5.squareRoot()
            for sign in [-1.0, 1.0] {
                let r = M.touchRadius
                let circle = Path(ellipseIn: CGRect(x: v.focalX + sign * d - r, y: v.focalY + sign * d - r, width: r * 2, height: r * 2))
                context.fill(circle, with: .color(Color.white.opacity(0.5 * shown)))
                context.stroke(circle, with: .color(Color(hex: ink, opacity: 0.45 * shown)), lineWidth: 1.5)
            }
        }
    }

    /// One plate: its photo cropped to fill a rounded frame, the sharp copy fading in over the small one.
    private func drawPlate(in context: inout GraphicsContext, _ f: PinchDensityGridFrame, _ i: Int) {
        let rect = CGRect(x: f.x, y: f.y, width: f.w, height: f.h)
        var plate = context
        plate.clip(to: Path(roundedRect: rect, cornerRadius: max(0, f.r)))
        plate.opacity = scene.alpha
        plate.fill(Path(rect), with: .color(Color(hex: M.tileTone)))
        for copy in [scene.thumbs.indices.contains(i) ? scene.thumbs[i] : nil, scene.sharp.indices.contains(i) ? scene.sharp[i] : nil] {
            guard let copy else { continue }
            let shown = M.clamp((scene.now - copy.at) / M.imageFade, 0, 1)
            guard shown > 0 else { continue }
            let iw = copy.image.size.width
            let ih = copy.image.size.height
            let scale = max(f.w / iw, f.h / ih)
            plate.opacity = scene.alpha * shown
            plate.draw(
                Image(uiImage: copy.image).interpolation(.medium),
                in: CGRect(x: rect.midX - iw * scale / 2, y: rect.midY - ih * scale / 2, width: iw * scale, height: ih * scale)
            )
        }
    }

    /// The plate's name, its note and its number under it, set larger at one column.
    private func drawLabel(in context: inout GraphicsContext, _ f: PinchDensityGridFrame, _ i: Int, shown: Double, grow: Double) {
        let s = 1 + grow * M.labelGrow
        var label = context
        label.translateBy(x: f.x, y: f.y + f.h)
        label.scaleBy(x: s, y: s)
        let room = f.w / s
        let item = plates[i]
        let number = String(format: "No. %02d", i + 1)
        let small = T.font(Self.mediumFont, 10)
        let numberWidth = T.tracked(number, small, tracking: 0.6).width
        let inkColor = Color(hex: ink)
        let name = T.fit(item.name, T.font(Self.mediumFont, 14), max: room - numberWidth - 8)
        drawText(name, font: Self.mediumFont, size: 14, color: inkColor, in: &label, x: 1, baseline: 19, alpha: shown)
        let note = T.fit(item.note, T.font(Self.regularFont, 12), max: room - 2)
        drawText(note, font: Self.regularFont, size: 12, color: inkColor, in: &label, x: 1, baseline: 35, alpha: shown * 0.55)
        drawTracked(number, size: 10, tracking: 0.6, color: inkColor, in: &label, x: room - numberWidth, baseline: 18, alpha: shown * 0.55)
    }

    private func drawTracked(_ text: String, size: CGFloat, tracking: Double, color: Color, in context: inout GraphicsContext, x: Double, baseline: Double, alpha: Double) {
        let run = T.tracked(text, T.font(Self.mediumFont, size), tracking: tracking)
        for (letter, at) in zip(run.letters, run.at) where letter != " " {
            drawText(letter, font: Self.mediumFont, size: size, color: color, in: &context, x: x + at, baseline: baseline, alpha: alpha)
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
/// width plus its tracking, and cuts labels to fit by it.
enum PinchDensityGridSkiaText {
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

    /// A label cut to `max` pt, with an ellipsis when it does not fit.
    static func fit(_ text: String, _ font: CTFont, max: Double) -> String {
        if width(text, font) <= max { return text }
        var cut = text
        while cut.count > 1 && width(cut + "…", font) > max { cut.removeLast() }
        while cut.last == " " { cut.removeLast() }
        return cut + "…"
    }
}

/// The pinch, the one-finger drag and the double tap, read with UIKit's recognisers so the pinch
/// reports where its fingers are as they move (SwiftUI's magnify gesture reports only where it began).
private struct PinchDensityGridTouches: UIViewRepresentable {
    let engine: PinchDensityGridEngine
    let settings: PinchDensityGridSettings

    func makeCoordinator() -> Coordinator { Coordinator(engine: engine) }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        let c = context.coordinator
        let pinch = UIPinchGestureRecognizer(target: c, action: #selector(Coordinator.pinched(_:)))
        let pan = UIPanGestureRecognizer(target: c, action: #selector(Coordinator.panned(_:)))
        pan.maximumNumberOfTouches = 1
        let doubleTap = UITapGestureRecognizer(target: c, action: #selector(Coordinator.tapped(_:)))
        doubleTap.numberOfTapsRequired = 2
        for recogniser in [pinch, pan, doubleTap] as [UIGestureRecognizer] {
            recogniser.delegate = c
            view.addGestureRecognizer(recogniser)
        }
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.settings = settings
    }

    @MainActor
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        let engine: PinchDensityGridEngine
        var settings: PinchDensityGridSettings?

        init(engine: PinchDensityGridEngine) {
            self.engine = engine
        }

        @objc func pinched(_ r: UIPinchGestureRecognizer) {
            let focal = r.location(in: r.view)
            switch r.state {
            case .began:
                engine.pinchBegan(focal: focal)
                engine.pinchChanged(scale: r.scale, focal: focal)
            case .changed:
                engine.pinchChanged(scale: r.scale, focal: focal)
            case .ended, .cancelled, .failed:
                if let settings { engine.pinchEnded(scale: r.scale, velocity: r.velocity, settings: settings) }
            default:
                break
            }
        }

        @objc func panned(_ r: UIPanGestureRecognizer) {
            switch r.state {
            case .began:
                engine.panBegan()
                engine.panChanged(translationY: r.translation(in: r.view).y)
            case .changed:
                engine.panChanged(translationY: r.translation(in: r.view).y)
            case .ended, .cancelled:
                engine.panEnded(velocityY: r.velocity(in: r.view).y)
            default:
                break
            }
        }

        @objc func tapped(_ r: UITapGestureRecognizer) {
            guard r.state == .ended, let settings else { return }
            engine.doubleTapped(at: r.location(in: r.view), settings: settings)
        }

        // The pinch runs alongside the drag and the double tap; those two race each other.
        func gestureRecognizer(_ a: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith b: UIGestureRecognizer) -> Bool {
            a is UIPinchGestureRecognizer || b is UIPinchGestureRecognizer
        }
    }
}
