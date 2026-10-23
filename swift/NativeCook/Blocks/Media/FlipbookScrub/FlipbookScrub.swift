import CoreText
import SwiftUI
import UIKit

/// One leaf of the book: a photo printed on it, its caption and the chapter it belongs to.
struct FlipbookScrubPage: Hashable {
    let id: String
    let title: String
    let chapter: String
}

/// A thick flipbook lying on a dark table, bound along its top edge under a cloth tape, a photo
/// printed on every leaf. Dragging up thumbs the leaves over: each one curls up from its
/// bottom-right corner, shows its lighter back as it rolls over the binding and falls behind the
/// book. A fast drag puts several leaves in the air at once, so the photos run like a film; the
/// book coasts on after a flick and lays the nearest leaf down on a spring. A counter at the top
/// rolls with the leaves.
struct FlipbookScrub: View {
    /// The leaves, front to back.
    var pages: [FlipbookScrubPage] = FlipbookScrubPage.samples
    /// Turns a few leaves over one at a time, rests, turns them back one at a time and starts over.
    var autoPlay = false
    /// Called with a leaf's index when the book comes to rest on it by hand.
    var onPage: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The book's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = FlipbookScrubEngine()

    private typealias M = FlipbookScrubMetrics

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
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
        .background(Color(hex: M.table).ignoresSafeArea())
        .task(id: pages) { await loadPhotos() }
    }

    /// Each print arrives on its own and fades in; until then its leaf shows blank paper.
    private func loadPhotos() async {
        engine.loaded = [:]
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (i, leaf) in pages.enumerated() {
                group.addTask { (i, await FlipbookScrubPhotos.load(flipbookScrubPhoto(leaf.id))) }
            }
            for await (i, image) in group {
                guard let image else { continue }
                engine.loaded[i] = (Image(uiImage: image).interpolation(.high), engine.elapsed, image.size)
            }
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let book = FlipbookScrubBook(size: size, top: insets.top)
        let settings = FlipbookScrubSettings(
            count: pages.count,
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            bookMiddle: book.y + book.h / 2,
            onPage: onPage
        )
        let painter = FlipbookScrubPainter(pages: pages, reducedMotion: reduceMotion)
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, settings: settings)
            Canvas { context, _ in
                painter.draw(&context, frame: frame, book: book, size: size, insets: insets, loaded: engine.loaded)
            }
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchMoved($0, settings: settings) }
                .onEnded { engine.touchEnded($0, settings: settings) }
        )
        .accessibilityElement()
        .accessibilityLabel("Flipbook of \(pages.count) photos. Drag up to riffle through the leaves.")
    }
}

/// Where the book lies on the screen, in pt.
struct FlipbookScrubBook {
    let x: CGFloat
    let y: CGFloat
    let w: CGFloat
    let h: CGFloat

    init(size: CGSize, top: CGFloat) {
        let m = FlipbookScrubMetrics.self
        w = min(m.maxPageW, size.width - m.side * 2)
        h = (w * m.pageAspect).rounded()
        x = (size.width - w) / 2
        let headerBottom = top + m.counterY + 8
        y = max(headerBottom + h * m.riseRoom, (size.height - h - m.thick) / 2 + 24)
    }
}

/// Draws one frame of the book and its header, as the RN version records one Skia picture.
struct FlipbookScrubPainter {
    let pages: [FlipbookScrubPage]
    let reducedMotion: Bool
    let chapters: [String]
    let chapterOf: [Int]

    private typealias M = FlipbookScrubMetrics
    typealias Loaded = [Int: (image: Image, at: Double, size: CGSize)]

    static let labelFont = FlipbookScrubFont("AvenirNext-DemiBold", 11)
    static let captionFont = FlipbookScrubFont("Baskerville-Italic", 15)
    static let numberFont = FlipbookScrubFont("AvenirNext-DemiBold", 10)
    static let counterFont = FlipbookScrubFont("AvenirNext-DemiBold", M.counterSize)
    static let chapterFont = FlipbookScrubFont("Baskerville-SemiBoldItalic", 24)
    static let smallFont = FlipbookScrubFont("AvenirNext-Medium", 13)
    // The counter's digits sit in cells a little narrower than a zero.
    static let digitCell = counterFont.width("0") * 0.86

    init(pages: [FlipbookScrubPage], reducedMotion: Bool) {
        self.pages = pages
        self.reducedMotion = reducedMotion
        // The chapters in order, and each leaf's chapter as a number, for the rolling chapter line.
        var names: [String] = []
        for leaf in pages where !names.contains(leaf.chapter) { names.append(leaf.chapter) }
        chapters = names
        chapterOf = pages.map { names.firstIndex(of: $0.chapter) ?? 0 }
    }

    func draw(_ context: inout GraphicsContext, frame: FlipbookScrubFrame, book: FlipbookScrubBook, size: CGSize, insets: EdgeInsets, loaded: Loaded) {
        let total = pages.count
        guard total > 0, size.width > 0 else { return }
        let last = total - 1
        let width = size.width
        let height = size.height
        // Drawn from a place kept on the book, whatever the physics did this frame.
        let p = frame.page.isFinite ? min(Double(last) + M.rubber, max(-M.rubber, frame.page)) : 0
        let w = book.w
        let h = book.h
        let chrome = Color(hex: M.chrome)

        // The table, lit from above the book.
        context.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .radialGradient(
                Gradient(colors: [Color(hex: M.tableLit), Color(hex: M.table)]),
                center: CGPoint(x: width * 0.42, y: book.y + h * 0.35),
                startRadius: 0,
                endRadius: height * 0.62
            )
        )

        // The header: the label, the chapter under it, and the counter on the right.
        drawTracked(context, "MOTION STUDIES", font: Self.labelFont, color: chrome, x: M.side, baseline: insets.top + M.labelY, tracking: 2.2, opacity: 0.5)
        let at = min(Double(last), max(0, p))
        // The chapter rolls up a line, like the counter, while the leaf between two chapters turns.
        let whole = max(0, min(last - 1, Int(floor(at))))
        let next = min(last, whole + 1)
        let chapterAt = Double(chapterOf[whole]) + Double(chapterOf[next] - chapterOf[whole]) * M.smoothstep(0.2, 0.8, at - Double(whole))
        var chapterLine = context
        chapterLine.clip(to: Path(CGRect(x: 0, y: insets.top + M.labelY + 8, width: width * 0.6, height: 40)))
        for (j, name) in chapters.enumerated() {
            let d = Double(j) - chapterAt
            let alpha = M.clamp01(1 - abs(d) * 1.4)
            if alpha <= 0.01 { continue }
            text(chapterLine, name, font: Self.chapterFont, color: chrome, x: M.side, baseline: insets.top + M.labelY + 34 + d * 30, opacity: alpha)
        }
        let of = "/ \(Self.pad(total))"
        let ofWidth = text(context, of, font: Self.smallFont, color: chrome, x: width - M.side, baseline: insets.top + M.counterY, align: 1, opacity: 0.5)
        drawCounter(context, value: at + 1, right: width - M.side - ofWidth - 8, baseline: insets.top + M.counterY, color: chrome)
        text(context, "Drag up to riffle  ·  Tap to turn a leaf", font: Self.smallFont, color: chrome, x: width / 2, baseline: height - insets.bottom - 22, align: 0.5, opacity: 0.42)

        var ctx = context
        ctx.translateBy(x: book.x, y: book.y)

        // The book's shadow on the table, and the block of leaves under the top one.
        var cast = ctx
        cast.addFilter(.blur(radius: Self.blur(18)))
        cast.fill(Path(roundedRect: CGRect(x: 8, y: 14, width: w, height: h + M.thick), cornerRadius: 8, style: .circular), with: .color(.black.opacity(0.55)))
        ctx.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: w, height: h + M.thick), cornerRadius: M.corner + 2, style: .circular), with: .color(Color(hex: M.paperEdge)))
        // The edges of the leaves, a hairline each, darker towards the table.
        var y = h + 1.6
        while y < h + M.thick - 1 {
            let depth = (y - h) / M.thick
            var line = Path()
            line.move(to: CGPoint(x: 2, y: y))
            line.addLine(to: CGPoint(x: w - 2, y: y))
            ctx.stroke(line, with: .color(Self.rgba(90, 72, 52, 0.18 + depth * 0.32)), lineWidth: 0.6)
            y += 1.7
        }
        ctx.fill(
            Path(roundedRect: CGRect(x: 0, y: h, width: w, height: M.thick), cornerRadius: M.corner + 2, style: .circular),
            with: .linearGradient(
                Gradient(colors: [Self.rgba(40, 28, 16, 0.05), Self.rgba(40, 28, 16, 0.38)]),
                startPoint: CGPoint(x: 0, y: h),
                endPoint: CGPoint(x: 0, y: h + M.thick)
            )
        )

        let now = frame.elapsed
        if reducedMotion {
            // No curl: the next leaf fades in over the one on top.
            let k = min(last, max(0, Int(floor(at))))
            drawLeaf(ctx, k, loaded: loaded, w: w, h: h, now: now)
            let f = at - Double(k)
            if f > 0.01 && k < last {
                var fade = ctx
                fade.opacity = f
                fade.drawLayer { layer in
                    layer.opacity = 1
                    drawLeaf(layer, k + 1, loaded: loaded, w: w, h: h, now: now)
                }
            }
            drawTape(ctx, w: w)
            return
        }

        let spread = min(M.spreadMax, M.spreadBase + frame.speed * M.spreadRate)
        // The first leaf still lying flat, with the tape over it; then the leaves in the air above
        // it, the one furthest over last. The turn falls leaf by leaf, so the first leaf that has
        // not started is found by walking up from the last one gone over.
        var top = max(0, Int(floor(p - 1)))
        while top < last && M.turnOf(top, p, spread, last) > 0 { top += 1 }
        let flat = M.turnOf(top, p, spread, last) <= 0
        if flat { drawLeaf(ctx, top, loaded: loaded, w: w, h: h, now: now) }
        drawTape(ctx, w: w)
        let from = min(last, flat ? top - 1 : top)
        let lowest = max(0, Int(floor(p - 1)))
        guard from >= lowest else { return }
        let pageBox = [CGPoint(x: 0, y: 0), CGPoint(x: w, y: 0), CGPoint(x: w, y: h), CGPoint(x: 0, y: h)]
        let underTape = [CGPoint(x: -1, y: M.tapeH), CGPoint(x: w + 1, y: M.tapeH), CGPoint(x: w + 1, y: h + 1), CGPoint(x: -1, y: h + 1)]
        for k in stride(from: from, through: lowest, by: -1) {
            let t = M.turnOf(k, p, spread, last)
            if t >= 1 { break }
            if t <= 0 { continue }
            let fold = M.foldOf(t, w, h)
            // What still lies flat, clipped clear of the tape that holds it.
            let keep = M.clip(underTape, fold.nx, fold.ny, fold.c, keep: -1)
            if keep.count >= 3 {
                var flatPart = ctx
                flatPart.clip(to: M.polygon(keep))
                drawLeaf(flatPart, k, loaded: loaded, w: w, h: h, now: now)
            }
            let open = M.clip(pageBox, fold.nx, fold.ny, fold.c, keep: 1)
            guard open.count >= 3 else { continue }
            // The shadow the lifted part throws on the leaf it uncovers, deepest at the fold.
            let openPath = M.polygon(open)
            ctx.fill(
                openPath,
                with: .linearGradient(
                    Gradient(colors: [Self.rgba(30, 18, 8, 0.34 * (1 - M.smoothstep(0.75, 1, t))), Self.rgba(30, 18, 8, 0)]),
                    startPoint: CGPoint(x: fold.mx, y: fold.my),
                    endPoint: CGPoint(x: fold.mx + fold.nx * M.foldShadow, y: fold.my + fold.ny * M.foldShadow)
                )
            )
            // The lifted part, mirrored over the fold; what rises above the binding folds down
            // behind the book towards the end of the turn.
            let squash = M.smoothstep(M.squashFrom, 1, t)
            let mirrored = open.map { point -> CGPoint in
                let s = point.x * fold.nx + point.y * fold.ny - fold.c
                return CGPoint(x: point.x - 2 * s * fold.nx, y: point.y - 2 * s * fold.ny)
            }
            // Split at the binding, so above it can fold away while below it stays put.
            var split: [CGPoint] = []
            let n = mirrored.count
            for i in 0..<n {
                let a = mirrored[i]
                let b = mirrored[(i + 1) % n]
                split.append(CGPoint(x: a.x, y: a.y < 0 ? a.y * (1 - squash) : a.y))
                if (a.y < 0) != (b.y < 0) {
                    let m = a.y / (a.y - b.y)
                    split.append(CGPoint(x: a.x + (b.x - a.x) * m, y: 0))
                }
            }
            if 1 - squash > 0.01 {
                drawFlap(ctx, M.polygon(split), fold: fold, squash: squash, index: k, loaded: loaded, w: w, h: h, now: now)
            }
        }
    }

    // MARK: Parts

    /// A leaf's face: paper, the photo printed on it, its caption and number.
    private func drawLeaf(_ context: GraphicsContext, _ index: Int, loaded: Loaded, w: CGFloat, h: CGFloat, now: Double) {
        // A leaf that is not there (an index off the book) draws nothing rather than trapping.
        guard pages.indices.contains(index) else { return }
        context.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: w, height: h), cornerRadius: M.corner, style: .circular), with: .color(Color(hex: M.paper)))
        let rect = CGRect(x: M.margin, y: M.tapeH + 10, width: w - M.margin * 2, height: h - M.tapeH - 10 - M.captionH)
        context.fill(Path(rect), with: .color(Color(hex: M.blank)))
        if let print = loaded[index], print.size.width > 0, print.size.height > 0 {
            // Cropped to fill the print's frame, as Skia draws the middle of the photo into it.
            let fit = max(rect.width / print.size.width, rect.height / print.size.height)
            let drawn = CGSize(width: print.size.width * fit, height: print.size.height * fit)
            var photo = context
            photo.clip(to: Path(rect))
            photo.opacity *= M.clamp01((now - print.at) / M.imageFade)
            photo.draw(print.image, in: CGRect(x: rect.midX - drawn.width / 2, y: rect.midY - drawn.height / 2, width: drawn.width, height: drawn.height))
        }
        let ink = Color(hex: M.ink)
        let base = h - M.captionH / 2 + 5
        text(context, pages[index].title, font: Self.captionFont, color: ink, x: M.margin, baseline: base)
        text(context, "\(Self.pad(index + 1)) / \(Self.pad(pages.count))", font: Self.numberFont, color: ink, x: w - M.margin, baseline: base, align: 1, opacity: 0.55)
        // The paper curves into the binding, a little darker there.
        context.fill(
            Path(CGRect(x: 0, y: M.tapeH, width: w, height: 22)),
            with: .linearGradient(
                Gradient(colors: [Self.rgba(60, 40, 20, 0.16), Self.rgba(60, 40, 20, 0)]),
                startPoint: CGPoint(x: 0, y: M.tapeH),
                endPoint: CGPoint(x: 0, y: M.tapeH + 22)
            )
        )
    }

    /// The lifted part of a leaf: its back, lighter paper, rolled along the fold.
    private func drawFlap(_ context: GraphicsContext, _ flap: Path, fold: FlipbookScrubFold, squash: Double, index: Int, loaded: Loaded, w: CGFloat, h: CGFloat, now: Double) {
        // Its shadow on whatever is under it, a little down and to the right.
        var shadow = context
        shadow.addFilter(.blur(radius: Self.blur(7)))
        shadow.fill(flap.offsetBy(dx: 5, dy: 9), with: .color(Self.rgba(18, 10, 4, 0.32)))

        let back = Color(hex: M.paperBack)
        context.fill(flap, with: .color(back))

        var inside = context
        inside.clip(to: flap)
        // The print shows faintly through the paper, mirrored by the fold.
        let through = M.showThrough * (1 - squash)
        if through > 0.005 && loaded[index] != nil {
            var mirror = inside
            mirror.concatenate(CGAffineTransform(
                a: 1 - 2 * fold.nx * fold.nx, b: -2 * fold.nx * fold.ny,
                c: -2 * fold.nx * fold.ny, d: 1 - 2 * fold.ny * fold.ny,
                tx: 2 * fold.c * fold.nx, ty: 2 * fold.c * fold.ny
            ))
            drawLeaf(mirror, index, loaded: loaded, w: w, h: h, now: now)
            inside.fill(flap, with: .color(back.opacity(1 - through)))
        }
        // The roll: a crease in shadow at the fold, a highlight on the curve, then the paper
        // turning away from the light towards the far edge.
        let start = CGPoint(x: fold.mx, y: fold.my)
        let rollEnd = CGPoint(x: fold.mx - fold.nx * M.roll, y: fold.my - fold.ny * M.roll)
        inside.fill(
            flap,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Self.rgba(70, 50, 30, 0.30), location: 0),
                    .init(color: Self.rgba(255, 255, 255, 0.75), location: 0.32),
                    .init(color: Self.rgba(255, 255, 255, 0), location: 1),
                ]),
                startPoint: start,
                endPoint: rollEnd
            )
        )
        let reach = max(M.roll * 1.5, fold.len * 0.5)
        inside.fill(
            flap,
            with: .linearGradient(
                Gradient(colors: [Self.rgba(80, 60, 40, 0), Self.rgba(80, 60, 40, 0.12 + 0.12 * squash)]),
                startPoint: rollEnd,
                endPoint: CGPoint(x: fold.mx - fold.nx * reach, y: fold.my - fold.ny * reach)
            )
        )
        context.stroke(flap, with: .color(Self.rgba(90, 70, 50, 0.28)), lineWidth: 0.75)
    }

    /// The cloth tape that binds the leaves along the top, with a row of stitches.
    private func drawTape(_ context: GraphicsContext, w: CGFloat) {
        context.fill(
            Path(roundedRect: CGRect(x: -3, y: -6, width: w + 6, height: M.tapeH + 6), cornerRadius: 5, style: .circular),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: M.tapeSheen), location: 0),
                    .init(color: Color(hex: M.tape), location: 0.45),
                    .init(color: Color(hex: M.tapeDeep), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: -6),
                endPoint: CGPoint(x: 0, y: M.tapeH)
            )
        )
        var stitches = Path()
        var x: CGFloat = 12
        while x < w - 8 {
            stitches.move(to: CGPoint(x: x, y: M.tapeH - 7))
            stitches.addLine(to: CGPoint(x: x + 5, y: M.tapeH - 7))
            x += 11
        }
        context.stroke(stitches, with: .color(Self.rgba(239, 230, 216, 0.28)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
    }

    /// The leaf number, each digit rolling up like an odometer as the leaves go by.
    private func drawCounter(_ context: GraphicsContext, value: Double, right: CGFloat, baseline: CGFloat, color: Color) {
        let cell = Self.digitCell
        let whole = Int(floor(value))
        let frac = M.smoothstep(0.38, 0.62, value - Double(whole))
        let line = M.counterSize * 1.05
        var clipped = context
        clipped.clip(to: Path(CGRect(x: right - cell * 2 - 4, y: baseline - M.counterSize * 0.92, width: cell * 2 + 8, height: M.counterSize * 1.12)))
        for place in 0..<2 {
            let unit = place == 0 ? 1 : 10
            let digit = (whole / unit) % 10
            // The tens roll only while the ones turn over from 9.
            let roll = place == 0 ? frac : whole % 10 == 9 ? frac : 0
            let x = right - cell * CGFloat(place + 1)
            for k in 0..<2 {
                let y = baseline + (Double(k) - roll) * line
                let alpha = M.clamp01(1 - abs(Double(k) - roll) * 1.4)
                if alpha <= 0.01 { continue }
                let shown = String((digit + k) % 10)
                text(clipped, shown, font: Self.counterFont, color: color, x: x + (cell - Self.counterFont.width(shown)) / 2, baseline: y, opacity: alpha)
            }
        }
    }

    /// Text set on a baseline, to its left (align 0), middle (0.5) or right (1) as Skia measures it.
    /// Returns that width.
    @discardableResult
    private func text(_ context: GraphicsContext, _ string: String, font: FlipbookScrubFont, color: Color, x: CGFloat, baseline: CGFloat, align: CGFloat = 0, opacity: Double = 1) -> CGFloat {
        var context = context
        context.opacity *= opacity
        let resolved = context.resolve(Text(string).font(font.font).foregroundStyle(color))
        let size = resolved.measure(in: CGSize(width: 2000, height: 400))
        let ascent = resolved.firstBaseline(in: size)
        let width = font.width(string)
        context.draw(resolved, in: CGRect(x: x - width * align, y: baseline - ascent, width: size.width, height: size.height))
        return width
    }

    /// Text with extra space between its letters, stepped letter by letter as the RN version does.
    private func drawTracked(_ context: GraphicsContext, _ string: String, font: FlipbookScrubFont, color: Color, x: CGFloat, baseline: CGFloat, tracking: CGFloat, opacity: Double) {
        var cursor = x
        // A space measures as nothing on its own, so it is measured between two letters.
        let space = font.width("a a") - font.width("aa")
        for letter in string {
            let glyph = String(letter)
            if letter != " " { text(context, glyph, font: font, color: color, x: cursor, baseline: baseline, opacity: opacity) }
            cursor += (letter == " " ? space : font.width(glyph)) + tracking
        }
    }

    // MARK: Helpers

    static func pad(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }

    /// A Skia blur sigma (pt) as a SwiftUI blur radius, which spreads about half as far per unit.
    static func blur(_ sigma: CGFloat) -> CGFloat { sigma * 2 }
}

/// A font as SwiftUI draws it, measured the way the RN version's Skia fonts measure text.
///
/// Skia's `measureText` returns the union of the glyphs' ink bounds, each rounded out to whole
/// points and padded by one on either side, with the glyphs laid at their advances (no kerning).
/// The RN code places right-aligned and centred text and steps tracked letters by that width.
struct FlipbookScrubFont {
    let font: Font
    let ct: CTFont
    private static let widths = NSCache<NSString, NSNumber>()

    init(_ name: String, _ size: CGFloat) {
        font = .custom(name, fixedSize: size)
        ct = CTFontCreateWithName(name as CFString, size, nil)
    }

    func width(_ string: String) -> CGFloat {
        let key = "\(CTFontCopyPostScriptName(ct)) \(CTFontGetSize(ct)) \(string)" as NSString
        if let hit = Self.widths.object(forKey: key) { return CGFloat(hit.doubleValue) }
        let chars = Array(string.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(ct, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(ct, .horizontal, glyphs, &advances, chars.count)
        var bounds = [CGRect](repeating: .zero, count: chars.count)
        CTFontGetBoundingRectsForGlyphs(ct, .horizontal, glyphs, &bounds, chars.count)
        var pen: CGFloat = 0
        var low = CGFloat.infinity
        var high = -CGFloat.infinity
        for i in 0..<chars.count {
            if !bounds[i].isEmpty {
                low = min(low, pen + floor(bounds[i].minX) - 1)
                high = max(high, pen + ceil(bounds[i].maxX) + 1)
            }
            pen += advances[i].width
        }
        let width = high > low ? high - low : 0
        Self.widths.setObject(NSNumber(value: Double(width)), forKey: key)
        return width
    }
}

// MARK: - Samples

// Each photo is fetched at about three times the size it is printed, cropped to the print.
func flipbookScrubPhoto(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=750&h=800&q=80"
}

extension FlipbookScrubPage {
    static let samples: [FlipbookScrubPage] = [
        .init(id: "photo-1547153760-18fc86324498", title: "Arabesque", chapter: "The Dancers"),
        .init(id: "photo-1511715282680-fbf93a50e721", title: "Grand jeté", chapter: "The Dancers"),
        .init(id: "photo-1529229504105-4ea795dcbf59", title: "Relevé", chapter: "The Dancers"),
        .init(id: "photo-1563775957383-f7b1abbe63ec", title: "Pirouette", chapter: "The Dancers"),
        .init(id: "photo-1525426250015-4e6473cdbf17", title: "Port de bras", chapter: "The Dancers"),
        .init(id: "photo-1531318701087-32c11653dd77", title: "Attitude", chapter: "The Dancers"),
        .init(id: "photo-1611879531844-24b7ddf40b26", title: "Développé", chapter: "The Dancers"),
        .init(id: "photo-1524330685423-3e1966445abe", title: "Tour en l’air", chapter: "The Dancers"),
        .init(id: "photo-1690267286998-bae912638f40", title: "Pas de chat", chapter: "The Dancers"),
        .init(id: "photo-1593078886626-aabc65c1d311", title: "Fouetté", chapter: "The Dancers"),
        .init(id: "photo-1681152322586-7ddde43866a4", title: "Plié", chapter: "The Dancers"),
        .init(id: "photo-1529154547048-4d1e71e6a760", title: "Révérence", chapter: "The Dancers"),
        .init(id: "photo-1571008887538-b36bb32f4571", title: "Set", chapter: "The Runners"),
        .init(id: "photo-1727094141271-9bea5bc8c757", title: "Drive", chapter: "The Runners"),
        .init(id: "photo-1744060204728-f68e434a3edf", title: "Stride", chapter: "The Runners"),
        .init(id: "photo-1480179087180-d9f0ec044897", title: "Kick", chapter: "The Runners"),
        .init(id: "photo-1759674861540-afed9f86f94a", title: "Cadence", chapter: "The Runners"),
        .init(id: "photo-1461896836934-ffe607ba8211", title: "Lean", chapter: "The Runners"),
        .init(id: "photo-1581889470536-467bdbe30cd0", title: "Pace", chapter: "The Runners"),
        .init(id: "photo-1594882645126-14020914d58d", title: "Surge", chapter: "The Runners"),
        .init(id: "photo-1698671823406-035c77ff6fcd", title: "Float", chapter: "The Runners"),
        .init(id: "photo-1728532483490-708f6562b738", title: "Reach", chapter: "The Runners"),
        .init(id: "photo-1758922769578-68c5ba000d87", title: "Push", chapter: "The Runners"),
        .init(id: "photo-1744706908605-ac30eb45f98c", title: "Finish", chapter: "The Runners"),
    ]
}
