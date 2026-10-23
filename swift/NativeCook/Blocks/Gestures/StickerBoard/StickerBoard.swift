import CoreText
import SwiftUI
import UIKit

/// A sticker: its shape, its art, where it rests on the board and how it is turned.
struct StickerBoardSticker {
    enum Kind { case circle, rounded, star, smiley, bubble, heart, badge }
    var kind: Kind
    /// Radius for round shapes; width and height for the rounded photo and the bubble (pt).
    var size: CGSize
    /// Unsplash id for a photo sticker.
    var photo: String? = nil
    /// Fill of a drawn sticker.
    var color: UInt32? = nil
    /// Words on the bubble or the badge.
    var text: String? = nil
    /// Resting place, as shares of the board's width and of its height between the safe areas.
    var home: CGPoint
    /// Resting turn, degrees.
    var turn: Double
}

/// A sticker's own art shape, its outline with the white border around it, and the rings it
/// crumples along: its outline and a lumpy paper ball of radius 1, on the same rays from its middle.
struct StickerBoardShape {
    var art: Path
    var outline: Path
    var extent: Double
    var ring: [CGPoint]
    var ball: [CGPoint]
    /// How light (+) or dark (−) each facet of the ball is.
    var shades: [Double]
}

/// A cork board covered in die-cut stickers: photo stickers in a thick white border and drawn
/// ones, a star, a smiley, a speech bubble, a seal and a heart. A sticker can be dragged, turned
/// with two fingers and pinched, all at once; it lifts when picked up and slaps down with a squash
/// when let go. A long press lifts a corner that follows the finger, curling back over the
/// sticker, until the sticker comes off. A steel pedal bin rises from the bottom edge: it leans
/// towards a loose sticker, opens its lid as it nears and pulls it in; let go there, the sticker
/// crumples into a paper ball and drops in, the lid slams and the counter on the bin ticks up. A
/// little later a new one is slapped back in its place.
struct StickerBoard: View {
    var stickers: [StickerBoardSticker] = StickerBoardSticker.samples
    /// Plays a 15 s loop: two stickers moved, turned and dropped, one peeled, crumpled and binned.
    var autoPlay = false
    /// The words on the tape at the top of the board.
    var title = "WEEKEND · Nº 14"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = StickerBoardEngine()
    @State private var shapes: [StickerBoardShape] = []

    var body: some View {
        // The block draws under the system bars and keeps its stickers clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            board(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task(id: stickers.compactMap(\.photo)) { await loadPhotos() }
    }

    private var settings: StickerBoardSettings {
        StickerBoardSettings(
            stickers: stickers,
            shapes: shapes.count == stickers.count ? shapes : StickerBoardShapes.makeAll(stickers),
            auto: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion
        )
    }

    private func board(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = settings
        return ZStack {
            Rectangle()
                .fill(ShaderLibrary.stickerBoardCork(.float2(Float(max(1, size.width)), Float(max(1, size.height)))))
            TimelineView(.animation) { timeline in
                let frame = engine.step(
                    to: timeline.date,
                    stage: StickerBoardStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom),
                    settings: settings
                )
                Canvas { ctx, _ in
                    StickerBoardPainter(frame: frame, stickers: stickers, shapes: settings.shapes, title: title, reduced: reduceMotion).draw(ctx)
                }
            }
            StickerBoardTouchSurface(engine: engine)
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: 0x8a6440))
        .clipped()
        .onAppear { if shapes.count != stickers.count { shapes = StickerBoardShapes.makeAll(stickers) } }
        .accessibilityElement()
        .accessibilityLabel("A cork board with \(stickers.count) stickers. Drag, pinch and turn a sticker with two fingers; press and hold to peel one off, and let go of it over the bin to throw it away.")
    }

    private func loadPhotos() async {
        engine.resetPhotos(count: stickers.count)
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (i, s) in stickers.enumerated() {
                guard let id = s.photo else { continue }
                group.addTask { (i, await StickerBoardPhotos.load(id)) }
            }
            for await (i, image) in group {
                if let image { engine.photoArrived(image, at: i) }
            }
        }
    }
}

// MARK: - Touches

/// Every finger on the board, passed to the engine as it is: the stand-in for the React Native
/// gesture set needs each touch, and SwiftUI's pinch and rotation do not say where the fingers are.
private struct StickerBoardTouchSurface: UIViewRepresentable {
    let engine: StickerBoardEngine

    func makeUIView(context: Context) -> StickerBoardTouchView {
        let view = StickerBoardTouchView()
        view.engine = engine
        view.isMultipleTouchEnabled = true
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ view: StickerBoardTouchView, context: Context) {
        view.engine = engine
    }
}

final class StickerBoardTouchView: UIView, UIGestureRecognizerDelegate {
    weak var engine: StickerBoardEngine?

    override init(frame: CGRect) {
        super.init(frame: frame)
        // A recogniser rather than the view's own touch methods: it keeps getting the touches
        // when a recogniser further up (the gallery's back swipe) takes them from the view.
        let listener = StickerBoardTouchListener(target: nil, action: nil)
        listener.cancelsTouchesInView = false
        listener.delaysTouchesBegan = false
        listener.delaysTouchesEnded = false
        listener.delegate = self
        listener.surface = self
        addGestureRecognizer(listener)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        true
    }
}

/// Hands every touch on the board to the engine, and never recognises anything itself.
final class StickerBoardTouchListener: UIGestureRecognizer {
    weak var surface: StickerBoardTouchView?
    private var down = 0

    private func points(_ touches: Set<UITouch>) -> [(Int, CGPoint)] {
        touches.map { (ObjectIdentifier($0).hashValue, $0.location(in: surface)) }
    }

    override func canBePrevented(by preventingGestureRecognizer: UIGestureRecognizer) -> Bool { false }
    override func canPrevent(_ preventedGestureRecognizer: UIGestureRecognizer) -> Bool { false }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        down += touches.count
        let list = points(touches)
        MainActor.assumeIsolated { surface?.engine?.touchesBegan(list) }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        let list = points(touches)
        MainActor.assumeIsolated { surface?.engine?.touchesMoved(list) }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        lift(touches)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        lift(touches)
    }

    private func lift(_ touches: Set<UITouch>) {
        down = max(0, down - touches.count)
        let list = points(touches)
        MainActor.assumeIsolated { surface?.engine?.touchesEnded(list) }
        if down == 0 { state = .failed }
    }

    override func reset() {
        super.reset()
        down = 0
    }
}

// MARK: - Shapes

enum StickerBoardShapes {
    /// A five-pointed star, its points rounded by the stroke drawn over it.
    static func star(_ r: Double) -> Path {
        var p = Path()
        for i in 0..<10 {
            let a = -Double.pi / 2 + Double(i) * .pi / 5
            let rr = i % 2 == 0 ? r : r * 0.5
            let pt = CGPoint(x: cos(a) * rr, y: sin(a) * rr)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
        return p
    }

    static func heart(_ w: Double, _ h: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: h * 0.95))
        p.addCurve(to: CGPoint(x: -w, y: -h * 0.25), control1: CGPoint(x: -w * 0.2, y: h * 0.7), control2: CGPoint(x: -w, y: h * 0.35))
        p.addCurve(to: CGPoint(x: 0, y: -h * 0.5), control1: CGPoint(x: -w, y: -h * 0.85), control2: CGPoint(x: -w * 0.25, y: -h * 1.05))
        p.addCurve(to: CGPoint(x: w, y: -h * 0.25), control1: CGPoint(x: w * 0.25, y: -h * 1.05), control2: CGPoint(x: w, y: -h * 0.85))
        p.addCurve(to: CGPoint(x: 0, y: h * 0.95), control1: CGPoint(x: w, y: h * 0.35), control2: CGPoint(x: w * 0.2, y: h * 0.7))
        p.closeSubpath()
        return p
    }

    /// A speech bubble: a pill with a tail off its lower left.
    static func bubble(_ w: Double, _ h: Double) -> Path {
        let body = Path(roundedRect: CGRect(x: -w / 2, y: -h / 2, width: w, height: h), cornerRadius: h / 2, style: .circular)
        var tail = Path()
        tail.move(to: CGPoint(x: -w * 0.28, y: h * 0.3))
        tail.addQuadCurve(to: CGPoint(x: -w * 0.46, y: h * 0.78), control: CGPoint(x: -w * 0.32, y: h * 0.62))
        tail.addQuadCurve(to: CGPoint(x: -w * 0.06, y: h * 0.4), control: CGPoint(x: -w * 0.18, y: h * 0.7))
        tail.closeSubpath()
        return Path(body.cgPath.union(tail.cgPath))
    }

    /// A scalloped seal: a circle with sixteen bumps around it.
    static func badge(_ r: Double) -> Path {
        var p = Path()
        let bumps = 16
        for i in 0...bumps {
            let a = Double(i) / Double(bumps) * .pi * 2
            let pt = CGPoint(x: cos(a) * r, y: sin(a) * r)
            if i == 0 {
                p.move(to: pt)
                continue
            }
            let m = a - .pi / Double(bumps)
            p.addQuadCurve(to: pt, control: CGPoint(x: cos(m) * r * 1.16, y: sin(m) * r * 1.16))
        }
        p.closeSubpath()
        return p
    }

    // Points around a sticker's outline, sampled at even angles from its middle; every fourth one
    // starts a facet of the paper ball.
    static let ringCount = 48
    static let facetStep = 4
    // Chords between ring points drawn as creases on the ball.
    static let creases: [(Int, Int)] = [(2, 21), (7, 31), (13, 40), (25, 45), (35, 10), (18, 29)]

    /// A repeatable pseudo-random number in [0, 1).
    static func hash01(_ n: Double) -> Double {
        let s = sin(n * 127.1 + 311.7) * 43758.5453
        return s - Foundation.floor(s)
    }

    /// The outermost edge of `outline` along `ringCount` rays from its middle.
    static func sampleRing(_ outline: Path, _ extent: Double) -> [CGPoint] {
        (0..<ringCount).map { i in
            let a = -Double.pi / 2 + Double(i) / Double(ringCount) * .pi * 2
            let cx = cos(a)
            let cy = sin(a)
            // March in from outside to the first point inside, then halve the step to the edge.
            var inside = 0.0
            var r = extent + 2
            while r > 0 {
                if outline.contains(CGPoint(x: cx * r, y: cy * r)) {
                    inside = r
                    break
                }
                r -= 1
            }
            var lo = inside
            var hi = inside + 1
            for _ in 0..<8 {
                let mid = (lo + hi) / 2
                if outline.contains(CGPoint(x: cx * mid, y: cy * mid)) { lo = mid } else { hi = mid }
            }
            return CGPoint(x: cx * lo, y: cy * lo)
        }
    }

    /// The ring of a crumpled ball of radius 1 on the same rays: lumpy, with a few deep folds.
    static func ballRing(_ seed: Double) -> [CGPoint] {
        let raw = (0..<ringCount).map { 0.8 + 0.3 * hash01(seed * 7.13 + Double($0) * 1.91) }
        return (0..<ringCount).map { i in
            let a = -Double.pi / 2 + Double(i) / Double(ringCount) * .pi * 2
            var f = 0.5 * raw[i] + 0.25 * (raw[(i + ringCount - 1) % ringCount] + raw[(i + 1) % ringCount])
            // A fold every so often bites into the ball.
            if i % 7 == 3 { f -= 0.13 }
            if i % 7 == 0 { f += 0.06 }
            return CGPoint(x: cos(a) * f, y: sin(a) * f)
        }
    }

    static func makeAll(_ stickers: [StickerBoardSticker]) -> [StickerBoardShape] {
        stickers.enumerated().map { make($0.element, index: $0.offset) }
    }

    static func make(_ s: StickerBoardSticker, index: Int) -> StickerBoardShape {
        let a = s.size.width
        let b = s.size.height
        let art: Path
        switch s.kind {
        case .circle, .smiley: art = Path(ellipseIn: CGRect(x: -a, y: -a, width: a * 2, height: a * 2))
        case .rounded: art = Path(roundedRect: CGRect(x: -a / 2, y: -b / 2, width: a, height: b), cornerRadius: 18, style: .circular)
        case .star: art = star(a)
        case .heart: art = heart(a, b)
        case .bubble: art = bubble(a, b)
        case .badge: art = badge(a)
        }
        let border = art.cgPath.copy(strokingWithWidth: StickerBoardMetrics.border * 2, lineCap: .round, lineJoin: .round, miterLimit: 4)
        let outline = Path(art.cgPath.union(border))
        let r = outline.boundingRect
        let extent = max(1, hypot(r.minX, r.minY), hypot(r.maxX, r.maxY))
        let seed = Double(index + 1)
        let shades = (0..<(ringCount / facetStep)).map { hash01(seed * 3.7 + Double($0) * 5.3) * 2 - 1 }
        return StickerBoardShape(art: art, outline: outline, extent: extent, ring: sampleRing(outline, extent), ball: ballRing(seed), shades: shades)
    }
}

// MARK: - Type

/// Text as outlines, measured the way Skia's `measureText` measures a string: the union of its
/// glyphs' bounds, each rounded out to whole points with a point of padding on either side.
@MainActor
enum StickerBoardType {
    static let bold = CTFontCreateWithName("AvenirNext-Heavy" as CFString, 18, nil)
    static let small = CTFontCreateWithName("AvenirNext-Heavy" as CFString, 13, nil)
    static let hand = CTFontCreateWithName("MarkerFelt-Wide" as CFString, 17, nil)

    private static var cache: [String: (path: Path, width: CGFloat)] = [:]

    /// The string's outline with its baseline at y = 0 starting at x = 0, and its measured width.
    static func line(_ text: String, _ font: CTFont) -> (path: Path, width: CGFloat) {
        let key = "\(CTFontCopyPostScriptName(font))|\(CTFontGetSize(font))|\(text)"
        if let hit = cache[key] { return hit }
        var out = Path()
        var at: CGFloat = 0
        var lo = CGFloat.infinity
        var hi = -CGFloat.infinity
        for char in text {
            let units = Array(String(char).utf16)
            var ids = [CGGlyph](repeating: 0, count: units.count)
            CTFontGetGlyphsForCharacters(font, units, &ids, units.count)
            var advance = CGSize.zero
            CTFontGetAdvancesForGlyphs(font, .horizontal, &ids, &advance, 1)
            var ink = CGRect.zero
            CTFontGetBoundingRectsForGlyphs(font, .horizontal, &ids, &ink, 1)
            if !ink.isEmpty {
                lo = min(lo, at + floor(ink.minX) - 1)
                hi = max(hi, at + ceil(ink.maxX) + 1)
            }
            var flip = CGAffineTransform(scaleX: 1, y: -1)
            if let glyph = CTFontCreatePathForGlyph(font, ids[0], &flip) {
                out.addPath(Path(glyph), transform: CGAffineTransform(translationX: at, y: 0))
            }
            at += advance.width
        }
        let entry = (out, hi > lo ? hi - lo : 0)
        cache[key] = entry
        return entry
    }
}

// MARK: - Painter

/// Draws one frame the way the React Native version records its Skia picture.
@MainActor
private struct StickerBoardPainter {
    let frame: StickerBoardFrame
    let stickers: [StickerBoardSticker]
    let shapes: [StickerBoardShape]
    let title: String
    let reduced: Bool

    private typealias M = StickerBoardMetrics
    private typealias P = StickerBoardMath

    private var k: Double { frame.stage.width / M.designWidth }

    func draw(_ ctx: GraphicsContext) {
        let room = frame.stage
        guard room.width > 0, shapes.count == stickers.count else { return }
        drawTape(ctx)

        let p = frame.peel
        for i in frame.order where frame.live.indices.contains(i) {
            // A sticker rising out of the pile keeps a copy right under the one that covered it.
            for (r, up) in frame.live.enumerated() where up.rise < 1 && up.riseUnder == i && up.gone < 0 && !(p.index == r && p.detached) {
                drawOne(ctx, r, fade: 1)
            }
            let s = frame.live[i]
            if s.gone >= 0 { continue }
            if p.index == i && p.detached { continue }
            drawOne(ctx, i, fade: s.rise < 1 ? P.easeInOut(s.rise) : 1)
        }

        // The bin, a ball dropping into it, a sticker crumpling over it, and a loose one above it all.
        let binFrame = StickerBoardBinArt.frame(frame.bin, k: k, width: room.width, height: room.height, bottom: room.bottom, reduced: reduced)
        StickerBoardBinArt.draw(ctx, binFrame, frame.bin, k: k) { inside in
            if reduced { return }
            for (i, s) in frame.live.enumerated() where s.gone >= M.crumple && s.gone < M.tossEnd {
                let e = P.clamp01((s.gone - M.crumple) / M.drop)
                let y0 = -(StickerBoardBinArt.body + StickerBoardBinArt.hover)
                let y1 = -StickerBoardBinArt.body + StickerBoardBinArt.mouthDrop + 6
                let turn = s.goneTurn + M.ballSpin + 120 * e
                var c = inside
                c.translateBy(x: 0, y: y0 + (y1 - y0) * e * e - sin(.pi * e) * M.ballHop)
                c.scaleBy(x: 1 / max(0.0001, binFrame.sx), y: 1 / max(0.0001, binFrame.sy))
                c.rotate(by: .degrees(turn - binFrame.rot))
                drawCrumpled(c, i, c: 1, from: k * s.goneScale, radius: M.ball * k * (1 - 0.15 * e), turn: turn)
            }
        }
        for (i, s) in frame.live.enumerated() where s.gone >= 0 && s.gone < M.crumple {
            let e = P.clamp01(s.gone / M.crumple)
            if reduced {
                // No crumple: it fades where it was let go.
                var layer = ctx
                layer.opacity = 1 - e
                let t = CGAffineTransform(translationX: s.goneX, y: s.goneY).rotated(by: s.goneTurn * .pi / 180).scaledBy(x: k * s.goneScale, y: k * s.goneScale)
                layer.drawLayer { l in
                    l.transform = t
                    drawFace(l, i, turn: s.goneTurn)
                }
                continue
            }
            let c = P.easeInOut(e)
            let m = P.easeOut(e)
            let to = binFrame.point(0, -(StickerBoardBinArt.body + StickerBoardBinArt.hover))
            let x = s.goneX + (to.x - s.goneX) * m
            let y = s.goneY + (to.y - s.goneY) * m
            let turn = s.goneTurn + M.ballSpin * c
            let from = k * s.goneScale
            let radius = M.ball * k
            var g = ctx
            g.translateBy(x: x, y: y)
            g.rotate(by: .degrees(turn))
            // Its shadow on the board, shrinking with it.
            let a = -turn * .pi / 180
            let ox = 5 * k
            let oy = 14 * k
            var shadow = g
            shadow.addFilter(.blur(radius: 7 * k))
            shadow.fill(crumplePath(i, c: c, from: from, radius: radius).applying(CGAffineTransform(translationX: ox * cos(a) - oy * sin(a), y: ox * sin(a) + oy * cos(a))), with: .color(Color(hex: 0x2a1505, opacity: 0.3)))
            drawCrumpled(g, i, c: c, from: from, radius: radius, turn: turn)
        }
        if p.index >= 0 && p.index < frame.live.count && p.detached && frame.live[p.index].gone < 0 { drawOne(ctx, p.index, fade: 1) }
    }

    /// A strip of masking tape across the top, with the board's name in marker.
    private func drawTape(_ ctx: GraphicsContext) {
        let room = frame.stage
        let t = CGAffineTransform(translationX: room.width / 2, y: room.top + 30 * k).rotated(by: -2.5 * .pi / 180)
        let tw = 184 * k
        let th = 30 * k
        var jag = Path()
        jag.move(to: CGPoint(x: -tw / 2, y: -th / 2))
        jag.addLine(to: CGPoint(x: tw / 2, y: -th / 2))
        for j in 0...6 { jag.addLine(to: CGPoint(x: tw / 2 + (j % 2 == 0 ? 0 : -3 * k), y: -th / 2 + th * Double(j) / 6)) }
        jag.addLine(to: CGPoint(x: -tw / 2, y: th / 2))
        for j in stride(from: 6, through: 0, by: -1) { jag.addLine(to: CGPoint(x: -tw / 2 + (j % 2 == 0 ? 0 : 3 * k), y: -th / 2 + th * Double(j) / 6)) }
        jag.closeSubpath()
        var shadow = ctx
        shadow.addFilter(.blur(radius: 2))
        shadow.fill(jag.applying(CGAffineTransform(translationX: 0, y: 1.5).concatenating(t)), with: .color(.rgba(60, 30, 5, 0.25)))
        var c = ctx
        c.transform = t
        c.fill(jag, with: .color(.rgba(244, 233, 196, 0.92)))
        let line = StickerBoardType.line(title, StickerBoardType.hand)
        c.fill(line.path.applying(CGAffineTransform(translationX: -line.width / 2, y: 6)), with: .color(Color(hex: 0x3f2d1b)))
    }

    /// The transform that draws sticker `s` from its own units.
    private func transform(_ s: StickerBoardLive, base: Double, extra: Double, turn: Double) -> CGAffineTransform {
        CGAffineTransform(translationX: s.x, y: s.y)
            .rotated(by: turn * .pi / 180)
            .scaledBy(x: base * extra * (1 + s.squash), y: base * extra * (1 - s.squash))
    }

    private func drawOne(_ ctx: GraphicsContext, _ i: Int, fade: Double) {
        let s = frame.live[i]
        let p = frame.peel
        let grow = reduced ? 1 : 1 + M.liftGrow * min(s.lift, 1.4)
        let shrink = p.index == i && p.detached ? 1 - M.magnetShrink * P.clamp01(frame.bin.magnet) : 1
        let base = k * s.scale * grow * shrink
        let turn = s.turn + s.sway
        var alpha = fade
        var extra = 1.0
        if s.spawn >= 0 {
            let e = P.clamp01(s.spawn / M.spawnTime)
            alpha *= e
            extra = reduced ? 1 : 1 + 0.25 * (1 - P.easeOut(e))
        }
        if alpha < 0.004 { return }
        let t = transform(s, base: base, extra: extra, turn: turn)
        let paint: (GraphicsContext) -> Void = { c in
            let peeled = p.index == i
            var cx = 0.0, cy = 0.0, fx = 0.0, fy = 0.0
            if peeled {
                cx = p.cx
                cy = p.cy
                if p.flatten >= 0 {
                    let e = P.easeOut(P.clamp01(p.flatten / M.flattenTime))
                    fx = p.flatX + (p.cx - p.flatX) * e
                    fy = p.flatY + (p.cy - p.flatY) * e
                } else if p.detached {
                    fx = p.cx
                    fy = p.cy
                } else {
                    (fx, fy) = P.toLocal(s, k, p.fx, p.fy)
                }
            }
            let len = hypot(fx - cx, fy - cy)
            if peeled && len > 0.5 {
                // The shadow of the part still stuck down, then the face with its flap.
                let nx = (fx - cx) / len
                let ny = (fy - cy) / len
                var clipped = c
                clipped.clip(to: keepSide((cx + fx) / 2, (cy + fy) / 2, nx, ny).applying(t))
                drawShadow(clipped, i, s, t: t, base: base, turn: turn, alpha: alpha)
                drawPeeled(c, i, t: t, cx: cx, cy: cy, fx: fx, fy: fy, turn: turn, base: base)
            } else {
                drawShadow(c, i, s, t: t, base: base, turn: turn, alpha: alpha)
                var face = c
                face.transform = t
                drawFace(face, i, turn: turn)
            }
        }
        if alpha < 0.995 {
            var layer = ctx
            layer.opacity = alpha
            layer.drawLayer { l in paint(l) }
        } else {
            paint(ctx)
        }
    }

    /// The shadow a sticker casts, offset in screen terms however it is turned or scaled.
    private func drawShadow(_ ctx: GraphicsContext, _ i: Int, _ s: StickerBoardLive, t: CGAffineTransform, base: Double, turn: Double, alpha: Double) {
        let l = min(s.lift, 1.6)
        let ox = M.shadowNear[0] + (M.shadowFar[0] - M.shadowNear[0]) * l
        let oy = M.shadowNear[1] + (M.shadowFar[1] - M.shadowNear[1]) * l
        let blur = M.shadowNear[2] + (M.shadowFar[2] - M.shadowNear[2]) * l
        let a = -turn * .pi / 180
        // Offset in the sticker's own units, so the transform carries it back to (ox, oy) on screen.
        let local = CGAffineTransform(translationX: (ox * cos(a) - oy * sin(a)) / base, y: (ox * sin(a) + oy * cos(a)) / base)
        var c = ctx
        c.transform = .identity
        c.addFilter(.blur(radius: blur * sqrt(abs(t.a * t.d - t.b * t.c)) / base))
        c.fill(shapes[i].outline.applying(local.concatenating(t)), with: .color(Color(hex: 0x2a1505, opacity: alpha * (0.42 - 0.1 * min(l, 1)))))
    }

    /// The half of the plane on the finger's side of the fold.
    private func keepSide(_ mx: Double, _ my: Double, _ nx: Double, _ ny: Double) -> Path {
        let far = 2000.0
        let px = -ny
        let py = nx
        var p = Path()
        p.move(to: CGPoint(x: mx + px * far, y: my + py * far))
        p.addLine(to: CGPoint(x: mx - px * far, y: my - py * far))
        p.addLine(to: CGPoint(x: mx - px * far + nx * far, y: my - py * far + ny * far))
        p.addLine(to: CGPoint(x: mx + px * far + nx * far, y: my + py * far + ny * far))
        p.closeSubpath()
        return p
    }

    /**
     * A sticker with a corner lifted. The fold is the line halfway between the corner and the
     * finger, square to the pull: the face is kept on the finger's side, and the part beyond the
     * fold is mirrored across it to show its pale backing, lit at the crease, casting a shadow on
     * the rest.
     */
    private func drawPeeled(_ ctx: GraphicsContext, _ i: Int, t: CGAffineTransform, cx: Double, cy: Double, fx: Double, fy: Double, turn: Double, base: Double) {
        let len = hypot(fx - cx, fy - cy)
        let nx = (fx - cx) / len
        let ny = (fy - cy) / len
        let mx = (cx + fx) / 2
        let my = (cy + fy) / 2
        let d = mx * nx + my * ny
        let mirror = CGAffineTransform(a: 1 - 2 * nx * nx, b: -2 * nx * ny, c: -2 * nx * ny, d: 1 - 2 * ny * ny, tx: 2 * d * nx, ty: 2 * d * ny)
        let outline = shapes[i].outline

        var c = ctx
        c.transform = t
        c.clip(to: keepSide(mx, my, nx, ny))
        drawFace(c, i, turn: turn)
        // The flap's shadow on the face below it, nudged away from the crease.
        var shade = c
        shade.transform = .identity
        let scale = sqrt(abs(t.a * t.d - t.b * t.c))
        shade.addFilter(.blur(radius: 5 * scale / base))
        shade.fill(outline.applying(mirror.concatenating(CGAffineTransform(translationX: nx * 4, y: ny * 4 + 2)).concatenating(t)), with: .color(Color(hex: 0x1e1004, opacity: 0.34)))
        // The flap: the backing paper, bright along the crease and greyer towards its tip.
        c.drawLayer { l in
            l.transform = t
            l.fill(outline.applying(mirror), with: .color(Color(hex: 0xece7dc)))
            l.blendMode = .sourceAtop
            let light = Gradient(stops: [
                .init(color: Color(hex: 0xffffff), location: 0),
                .init(color: Color(hex: 0xf1ece2), location: 0.18),
                .init(color: Color(hex: 0xc9c1b2), location: 1),
            ])
            l.fill(
                Path(CGRect(x: mx - 600, y: my - 600, width: 1200, height: 1200)),
                with: .linearGradient(light, startPoint: CGPoint(x: mx, y: my), endPoint: CGPoint(x: mx + nx * len / 2, y: my + ny * len / 2))
            )
        }
    }

    /// The sticker's face in its own units: white border, art, and a gloss that stays put as it turns.
    private func drawFace(_ ctx: GraphicsContext, _ i: Int, turn: Double) {
        let def = stickers[i]
        let shape = shapes[i]
        let art = shape.art
        let outline = shape.outline
        ctx.fill(outline, with: .color(Color(hex: 0xfbfaf6)))
        ctx.stroke(outline, with: .color(.rgba(70, 45, 20, 0.18)), lineWidth: 0.8)

        if def.photo != nil {
            ctx.fill(art, with: .color(Color(hex: 0xd8cfc2)))
            if let loaded = frame.photos[i] {
                var c = ctx
                c.clip(to: art)
                c.opacity = P.clamp01((frame.elapsed - loaded.at) / M.imageFade)
                let r = art.boundingRect
                let image = loaded.image
                let fit = max(r.width / image.size.width, r.height / image.size.height)
                let drawn = CGRect(x: r.midX - image.size.width * fit / 2, y: r.midY - image.size.height * fit / 2, width: image.size.width * fit, height: image.size.height * fit)
                c.draw(Image(uiImage: image).interpolation(.high), in: drawn)
            }
            ctx.stroke(art, with: .color(.black.opacity(0.12)), lineWidth: 1)
        } else {
            let color = Color(hex: def.color ?? 0xffc531)
            ctx.fill(art, with: .color(color))
            switch def.kind {
            case .star:
                // Round the star's points with a stroke of its own colour.
                ctx.stroke(art, with: .color(color), style: StrokeStyle(lineWidth: 5, lineJoin: .round))
            case .smiley:
                let r = def.size.width
                let ink = Color(hex: 0x3b2a12)
                ctx.fill(Path(ellipseIn: CGRect(x: -r * 0.38, y: -r * 0.42, width: r * 0.2, height: r * 0.3)), with: .color(ink))
                ctx.fill(Path(ellipseIn: CGRect(x: r * 0.18, y: -r * 0.42, width: r * 0.2, height: r * 0.3)), with: .color(ink))
                // The smile: an arc of the oval, 25° to 155° clockwise from the right.
                var smile = Path()
                let oval = CGRect(x: -r * 0.52, y: -r * 0.42, width: r * 1.04, height: r * 0.96)
                for step in 0...32 {
                    let a = (25 + 130 * Double(step) / 32) * .pi / 180
                    let pt = CGPoint(x: oval.midX + cos(a) * oval.width / 2, y: oval.midY + sin(a) * oval.height / 2)
                    if step == 0 { smile.move(to: pt) } else { smile.addLine(to: pt) }
                }
                ctx.stroke(smile, with: .color(ink), style: StrokeStyle(lineWidth: r * 0.1, lineCap: .round, lineJoin: .round))
                let cheek = Color.rgba(255, 110, 90, 0.45)
                ctx.fill(Path(ellipseIn: CGRect(x: -r * 0.56 - r * 0.13, y: r * 0.14 - r * 0.13, width: r * 0.26, height: r * 0.26)), with: .color(cheek))
                ctx.fill(Path(ellipseIn: CGRect(x: r * 0.56 - r * 0.13, y: r * 0.14 - r * 0.13, width: r * 0.26, height: r * 0.26)), with: .color(cheek))
            case .bubble, .badge:
                let font = def.kind == .bubble ? StickerBoardType.bold : StickerBoardType.small
                let line = StickerBoardType.line(def.text ?? "", font)
                if def.kind == .badge {
                    let ring = Path(ellipseIn: CGRect(x: -def.size.width * 0.78, y: -def.size.width * 0.78, width: def.size.width * 1.56, height: def.size.width * 1.56))
                    ctx.stroke(ring, with: .color(.white.opacity(0.75)), style: StrokeStyle(lineWidth: 1.4, dash: [3, 2.5]))
                    ctx.fill(line.path.applying(CGAffineTransform(translationX: -line.width / 2, y: 5)), with: .color(.white))
                } else {
                    ctx.fill(line.path.applying(CGAffineTransform(translationX: -line.width / 2, y: 6)), with: .color(.white))
                }
            default:
                break
            }
        }
        // The vinyl gloss: a soft band along a fixed screen diagonal, whatever the sticker's turn.
        let extent = shape.extent
        let a = -turn * .pi / 180
        let dx = cos(a) * extent - sin(a) * extent
        let dy = sin(a) * extent + cos(a) * extent
        let gloss = Gradient(stops: [
            .init(color: .white.opacity(0), location: 0.3),
            .init(color: .white.opacity(0.26), location: 0.42),
            .init(color: .white.opacity(0), location: 0.55),
            .init(color: .white.opacity(0.08), location: 0.8),
        ])
        var c = ctx
        c.clip(to: outline)
        c.fill(Path(CGRect(x: -extent, y: -extent, width: extent * 2, height: extent * 2)), with: .linearGradient(gloss, startPoint: CGPoint(x: -dx, y: -dy), endPoint: CGPoint(x: dx, y: dy)))
    }

    /// Point `i` of the ring part way from the outline (`c` = 0) to the ball (`c` = 1), in points.
    private func ringAt(_ i: Int, _ shape: StickerBoardShape, c: Double, from: Double, radius: Double) -> CGPoint {
        let n = min(shape.ring.count, shape.ball.count)
        if n == 0 { return .zero }
        let j = ((i % n) + n) % n
        let r = shape.ring[j]
        let b = shape.ball[j]
        return CGPoint(x: r.x * from * (1 - c) + b.x * radius * c, y: r.y * from * (1 - c) + b.y * radius * c)
    }

    /// The ring of a sticker part way to a paper ball.
    private func crumplePath(_ i: Int, c: Double, from: Double, radius: Double) -> Path {
        let shape = shapes[i]
        var p = Path()
        let n = min(shape.ring.count, shape.ball.count)
        for j in 0..<n {
            let pt = ringAt(j, shape, c: c, from: from, radius: radius)
            if j == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
        return p
    }

    /**
     * A sticker crumpling into a paper ball, in screen points around its middle. Its face shrinks
     * inside the crumpling outline, so its colours show between the creases at first; then facets
     * of light and shade, a paler paper and a few sharp creases make it read as a ball.
     */
    private func drawCrumpled(_ ctx: GraphicsContext, _ i: Int, c: Double, from: Double, radius: Double, turn: Double) {
        let shape = shapes[i]
        guard min(shape.ring.count, shape.ball.count) >= 3 else { return }
        let poly = crumplePath(i, c: c, from: from, radius: radius)
        var clip = ctx
        clip.clip(to: poly)
        let face = from + (radius * 1.35 / shape.extent - from) * c
        var f = clip
        f.scaleBy(x: face, y: face)
        drawFace(f, i, turn: turn)
        // The paper shows more and more as it balls up.
        clip.fill(Path(CGRect(x: -2000, y: -2000, width: 4000, height: 4000)), with: .color(Color(hex: 0xf4efe4, opacity: 0.5 * c * c)))
        // Facets fanned from a point off the middle, each tilted to the light or away from it.
        let q = CGPoint(x: radius * 0.24 * c, y: -radius * 0.18 * c)
        for (j, sh) in shape.shades.enumerated() {
            let i0 = j * StickerBoardShapes.facetStep
            var tri = Path()
            tri.move(to: q)
            tri.addLine(to: ringAt(i0, shape, c: c, from: from, radius: radius))
            tri.addLine(to: ringAt(i0 + StickerBoardShapes.facetStep, shape, c: c, from: from, radius: radius))
            tri.closeSubpath()
            let alpha = P.clamp01(abs(sh) * (sh >= 0 ? 0.42 : 0.32) * c)
            clip.fill(tri, with: .color(Color(hex: sh >= 0 ? 0xffffff : 0x5a3f22, opacity: alpha)))
        }
        // Sharp creases: a dark fold with a lit edge beside it.
        for (ca, cb) in StickerBoardShapes.creases {
            let a = ringAt(ca, shape, c: c, from: from, radius: radius)
            let b = ringAt(cb, shape, c: c, from: from, radius: radius)
            var crease = Path()
            crease.move(to: a)
            crease.addLine(to: CGPoint(x: (a.x + b.x) / 2 + q.x * 0.5, y: (a.y + b.y) / 2 + q.y * 0.5))
            crease.addLine(to: b)
            clip.stroke(crease, with: .color(Color(hex: 0x4a3218, opacity: 0.45 * c)), style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
            clip.stroke(crease.applying(CGAffineTransform(translationX: 0.7, y: 0.7)), with: .color(.white.opacity(0.7 * c)), style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
        }
        // A crisp paper edge.
        ctx.stroke(poly, with: .color(Color(hex: 0x6b4f30, opacity: 0.2 + 0.25 * c)), style: StrokeStyle(lineWidth: 0.8, lineJoin: .round))
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum StickerBoardPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ id: String) async -> UIImage? {
        let address = "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=400&q=80"
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}

// MARK: - Samples

extension StickerBoardSticker {
    static let samples: [StickerBoardSticker] = [
        .init(kind: .circle, size: CGSize(width: 56, height: 56), photo: "photo-1559181567-c3190ca9959b", home: CGPoint(x: 0.27, y: 0.17), turn: -6),
        .init(kind: .rounded, size: CGSize(width: 108, height: 132), photo: "photo-1518020382113-a7e8fc38eac9", home: CGPoint(x: 0.72, y: 0.19), turn: 7),
        .init(kind: .star, size: CGSize(width: 48, height: 48), color: 0xffc531, home: CGPoint(x: 0.17, y: 0.36), turn: -14),
        .init(kind: .circle, size: CGSize(width: 46, height: 46), photo: "photo-1629740067905-bd3f515aa739", home: CGPoint(x: 0.52, y: 0.37), turn: 4),
        .init(kind: .bubble, size: CGSize(width: 124, height: 60), color: 0xff6f91, text: "good boy!", home: CGPoint(x: 0.8, y: 0.41), turn: 8),
        .init(kind: .rounded, size: CGSize(width: 128, height: 96), photo: "photo-1609639643505-3c158a56de42", home: CGPoint(x: 0.33, y: 0.56), turn: -5),
        .init(kind: .smiley, size: CGSize(width: 40, height: 40), color: 0xffd23f, home: CGPoint(x: 0.75, y: 0.58), turn: 10),
        .init(kind: .badge, size: CGSize(width: 42, height: 42), color: 0x2f9e64, text: "FRESH", home: CGPoint(x: 0.18, y: 0.75), turn: -9),
        .init(kind: .circle, size: CGSize(width: 50, height: 50), photo: "photo-1558788353-f76d92427f16", home: CGPoint(x: 0.5, y: 0.74), turn: 3),
        .init(kind: .heart, size: CGSize(width: 44, height: 40), color: 0xff4b5c, home: CGPoint(x: 0.81, y: 0.75), turn: -8),
    ]
}
