import CoreText
import SwiftUI
import UIKit

/// A large, detailed photo fills the screen, dimmed, with a round magnifying loupe over it. Inside
/// the glass the photo is enlarged 2.5 times through a curved lens: it bulges, its colours split
/// into fringes towards the thick metal rim, and a soft gloss slides across it as it moves. The
/// loupe trails the finger on a spring; a pinch changes the zoom, a tap on the glass looks closer,
/// and a tap anywhere else sends the loupe there on an arc.
struct LoupeMagnifier: View {
    /// Unsplash id of a large, detailed photo; it is fetched 1600 px wide.
    var photo = "photo-1529926691761-20fb82067c71"
    /// Two lines at the bottom: a title and a line of small print.
    var title = "Harrow Strait Bridge"
    var meta = "CABLE-STAYED · 471 M SPAN · F/8"
    /// Wanders the loupe over the photo on a 13 s path, pausing on details.
    var autoPlay = false
    /// Where the wander stops, as shares of the screen, and the zoom at each stop.
    var stops: [LoupeMagnifierStop] = LoupeMagnifierStop.samples

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = LoupeMagnifierEngine()

    var body: some View {
        // The block draws under the system bars and keeps its caption clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            screen(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task(id: photo) {
            if let image = await LoupeMagnifierPhotos.load(photo) { engine.photoArrived(image) }
        }
    }

    private func screen(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = LoupeMagnifierSettings(stops: stops, auto: autoPlay && !reduceMotion, reducedMotion: reduceMotion)
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let frame = engine.step(
                    to: timeline.date,
                    stage: LoupeMagnifierStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom),
                    settings: settings
                )
                ZStack(alignment: .topLeading) {
                    LoupeMagnifierPhoto(frame: frame)
                    Canvas { ctx, _ in LoupeMagnifierPainter(frame: frame, title: title, meta: meta).draw(ctx) }
                }
            }
            LoupeMagnifierTouchSurface(engine: engine)
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: 0x0b0c10))
        .clipped()
        .accessibilityElement()
        .accessibilityLabel("\(title). A magnifying loupe over the photo: drag to move it, pinch to zoom, tap elsewhere to send it there.")
    }
}

/// The photo, cover-fitted, run through the lens shader; plain dark until it arrives.
private struct LoupeMagnifierPhoto: View {
    let frame: LoupeMagnifierFrame

    private typealias M = LoupeMagnifierMetrics

    var body: some View {
        let room = frame.stage
        if let image = frame.image, room.width > 0, room.height > 0, image.size.width > 0, image.size.height > 0 {
            let s = max(room.width / image.size.width, room.height / image.size.height)
            let w = image.size.width * s
            let h = image.size.height * s
            let origin = CGPoint(x: (room.width - w) / 2, y: (room.height - h) / 2)
            let grow = 1 + M.jumpLift * frame.lift
            let r = M.radius * grow
            let l = frame.lift
            let shadow = (0..<3).map { M.shadow[$0] + (M.shadowLifted[$0] - M.shadow[$0]) * l }
            Image(uiImage: image)
                .resizable()
                .interpolation(.high)
                .frame(width: w, height: h)
                .layerEffect(
                    ShaderLibrary.loupeMagnifierLens(
                        .float2(origin),
                        .float2(CGSize(width: w, height: h)),
                        .float2(CGSize(width: room.width, height: room.height)),
                        .float2(CGPoint(x: frame.x, y: frame.y)),
                        .float(r),
                        .float(M.rim * grow),
                        .float(frame.zoom),
                        .float(M.dim),
                        .float2(CGPoint(x: shadow[0], y: shadow[1])),
                        .float(shadow[2]),
                        .float(M.barrel),
                        .float(M.fringe),
                        .float(frame.shown)
                    ),
                    // The lens reads the photo up to its radius away from where it draws.
                    maxSampleOffset: CGSize(width: r * 1.5, height: r * 1.5)
                )
                .offset(x: origin.x, y: origin.y)
                .frame(width: room.width, height: room.height, alignment: .topLeading)
        } else {
            Color(hex: 0x0b0c10)
        }
    }
}

// MARK: - Touches

/// Every finger on the photo, passed to the engine as it is: the pan follows the fingers' average
/// while a pinch zooms, which SwiftUI's drag and magnify gestures cannot give together.
private struct LoupeMagnifierTouchSurface: UIViewRepresentable {
    let engine: LoupeMagnifierEngine

    func makeUIView(context: Context) -> LoupeMagnifierTouchView {
        let view = LoupeMagnifierTouchView()
        view.engine = engine
        view.isMultipleTouchEnabled = true
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ view: LoupeMagnifierTouchView, context: Context) {
        view.engine = engine
    }
}

final class LoupeMagnifierTouchView: UIView, UIGestureRecognizerDelegate {
    weak var engine: LoupeMagnifierEngine?

    override init(frame: CGRect) {
        super.init(frame: frame)
        // A recogniser rather than the view's own touch methods: it keeps getting the touches
        // when a recogniser further up (the gallery's back swipe) takes them from the view.
        let listener = LoupeMagnifierTouchListener(target: nil, action: nil)
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

/// Hands every touch on the photo to the engine, and never recognises anything itself.
final class LoupeMagnifierTouchListener: UIGestureRecognizer {
    weak var surface: LoupeMagnifierTouchView?
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
        // Reset with fingers still down (the view left the window mid-touch): the engine lets go.
        if down > 0 { MainActor.assumeIsolated { surface?.engine?.touchesLost() } }
        down = 0
    }
}

// MARK: - Type

/// Text as outlines, measured the way Skia's `measureText` measures a string: the union of its
/// glyphs' bounds, each rounded out to whole points with a point of padding on either side.
@MainActor
enum LoupeMagnifierType {
    static let tab = CTFontCreateWithName("Menlo-Bold" as CFString, 11, nil)
    static let title = CTFontCreateWithName("AvenirNext-Bold" as CFString, 22, nil)
    static let meta = CTFontCreateWithName("Menlo-Regular" as CFString, 10, nil)

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
        // Small caches: the zoom label changes every frame while it springs.
        if cache.count > 200 { cache.removeAll() }
        let entry = (out, hi > lo ? hi - lo : 0)
        cache[key] = entry
        return entry
    }
}

// MARK: - Painter

/// Everything over the photo: the caption under a dark fade (kept off the loupe), the glass's
/// gloss, and the metal rim with its zoom tab.
@MainActor
private struct LoupeMagnifierPainter {
    let frame: LoupeMagnifierFrame
    let title: String
    let meta: String

    private typealias M = LoupeMagnifierMetrics

    // Brushed metal around the rim, light to dark and back.
    private static let rimTones: [UInt32] = [0xf4f3ef, 0x8c8b86, 0xe2e0da, 0x5e5d59, 0xd3d1cb, 0x7d7c78, 0xf4f3ef]

    func draw(_ ctx: GraphicsContext) {
        let room = frame.stage
        guard room.width > 0 else { return }
        let grow = 1 + M.jumpLift * frame.lift
        let r = M.radius * grow
        let cx = frame.x
        let cy = frame.y

        // The caption, over a dark fade at the bottom, under the loupe.
        var caption = ctx
        var hole = Path(CGRect(x: 0, y: 0, width: room.width, height: room.height))
        hole.addEllipse(in: CGRect(x: cx - (r + M.rim * grow), y: cy - (r + M.rim * grow), width: (r + M.rim * grow) * 2, height: (r + M.rim * grow) * 2))
        caption.clip(to: hole, style: FillStyle(eoFill: true))
        caption.fill(
            Path(CGRect(x: 0, y: room.height - 170, width: room.width, height: 170)),
            with: .linearGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.7)]), startPoint: CGPoint(x: 0, y: room.height - 170), endPoint: CGPoint(x: 0, y: room.height))
        )
        let ink = Color(hex: 0xf3f1ea)
        let base = room.height - room.bottom - 18
        caption.fill(LoupeMagnifierType.line(title, LoupeMagnifierType.title).path.applying(CGAffineTransform(translationX: 22, y: base - 18)), with: .color(ink))
        caption.fill(LoupeMagnifierType.line(meta, LoupeMagnifierType.meta).path.applying(CGAffineTransform(translationX: 22, y: base)), with: .color(ink.opacity(0.6)))

        drawGloss(ctx, cx: cx, cy: cy, r: r, tx: frame.tiltX, ty: frame.tiltY)
        drawRim(ctx, cx: cx, cy: cy, r: r, rim: M.rim * grow, spin: frame.tiltX * 90, zoom: frame.zoom)
    }

    /// Points along a circle from `start` degrees, `sweep` degrees clockwise on screen.
    private func arc(_ cx: Double, _ cy: Double, _ r: Double, start: Double, sweep: Double) -> Path {
        var p = Path()
        let steps = 24
        for i in 0...steps {
            let a = (start + sweep * Double(i) / Double(steps)) * .pi / 180
            let pt = CGPoint(x: cx + cos(a) * r, y: cy + sin(a) * r)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }

    /// The glass's gloss: a soft crescent of light and a pin-point glint, sliding against the speed.
    private func drawGloss(_ ctx: GraphicsContext, cx: Double, cy: Double, r: Double, tx: Double, ty: Double) {
        var c = ctx
        c.clip(to: Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)))
        let hx = cx - r * 0.34 + tx * r
        let hy = cy - r * 0.42 + ty * r
        let soft = Gradient(stops: [
            .init(color: .white.opacity(0.34), location: 0),
            .init(color: .white.opacity(0.08), location: 0.55),
            .init(color: .white.opacity(0), location: 1),
        ])
        var turned = c
        turned.translateBy(x: hx, y: hy)
        turned.rotate(by: .degrees(-32 + tx * 40))
        turned.fill(
            Path(ellipseIn: CGRect(x: -r * 0.6, y: -r * 0.3, width: r * 1.2, height: r * 0.6)),
            with: .radialGradient(soft, center: .zero, startRadius: 0, endRadius: r * 0.62)
        )
        // A thin bright arc along the lower right of the glass: light leaving through the far side.
        c.stroke(arc(cx, cy, r - 5, start: 20 - tx * 30, sweep: 70), with: .color(.white.opacity(0.16)), lineWidth: 3)
        let gx = cx - r * 0.5 + tx * r * 1.2
        let gy = cy - r * 0.52 + ty * r * 1.2
        let g = r * 0.035
        c.fill(Path(ellipseIn: CGRect(x: gx - g, y: gy - g, width: g * 2, height: g * 2)), with: .color(.white.opacity(0.85)))
    }

    /// The metal rim: a brushed ring with bevels and a knurled outer band, and the zoom on a tab.
    private func drawRim(_ ctx: GraphicsContext, cx: Double, cy: Double, r: Double, rim: Double, spin: Double, zoom: Double) {
        let ring = Path(ellipseIn: CGRect(x: cx - r - rim / 2, y: cy - r - rim / 2, width: (r + rim / 2) * 2, height: (r + rim / 2) * 2))
        // The brushed light turns a little as the loupe swings.
        let metal = Gradient(colors: Self.rimTones.map { Color(hex: $0) })
        ctx.stroke(ring, with: .conicGradient(metal, center: CGPoint(x: cx, y: cy), angle: .degrees(spin)), lineWidth: rim)
        // Knurling on the outer half of the band.
        var ticks = Path()
        for k in 0..<90 {
            let a = Double(k) / 90 * .pi * 2
            let co = cos(a)
            let si = sin(a)
            ticks.move(to: CGPoint(x: cx + co * (r + rim * 0.58), y: cy + si * (r + rim * 0.58)))
            ticks.addLine(to: CGPoint(x: cx + co * (r + rim * 0.94), y: cy + si * (r + rim * 0.94)))
        }
        ctx.stroke(ticks, with: .color(.black.opacity(0.2)), lineWidth: 0.9)
        // Bevels: dark at the glass and at the outer edge, a bright line just inside the outer edge.
        func circle(_ radius: Double) -> Path { Path(ellipseIn: CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2)) }
        ctx.stroke(circle(r), with: .color(.black.opacity(0.6)), lineWidth: 1.4)
        ctx.stroke(circle(r + rim), with: .color(.black.opacity(0.45)), lineWidth: 1)
        ctx.stroke(circle(r + 2), with: .color(.white.opacity(0.5)), lineWidth: 1)
        ctx.stroke(circle(r + rim - 1.5), with: .color(.white.opacity(0.25)), lineWidth: 1)

        // The zoom on a small dark tab fixed to the rim, up and to the right.
        let label = String(format: "%.1f×", zoom)
        let line = LoupeMagnifierType.line(label, LoupeMagnifierType.tab)
        let w = line.width + 14
        let tx = cx + cos(-Double.pi / 4) * (r + rim) - 4
        let ty = cy + sin(-Double.pi / 4) * (r + rim) - 6
        let tab = Path(roundedRect: CGRect(x: tx, y: ty - 11, width: w, height: 22), cornerRadius: 11, style: .circular)
        ctx.fill(tab, with: .color(.rgba(14, 14, 16, 0.86)))
        ctx.stroke(tab, with: .color(.white.opacity(0.22)), lineWidth: 1)
        ctx.fill(line.path.applying(CGAffineTransform(translationX: tx + 7, y: ty + 4)), with: .color(Color(hex: 0xf3f1ea)))
    }
}

// MARK: - Photos

/// Downloads the photo once and keeps it decoded.
enum LoupeMagnifierPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ id: String) async -> UIImage? {
        let address = "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=1600&q=80"
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

extension LoupeMagnifierStop {
    static let samples: [LoupeMagnifierStop] = [
        .init(x: 0.42, y: 0.44, zoom: 2.6),
        .init(x: 0.36, y: 0.16, zoom: 3.2),
        .init(x: 0.27, y: 0.3, zoom: 3),
        .init(x: 0.48, y: 0.7, zoom: 3.4),
        .init(x: 0.74, y: 0.77, zoom: 3),
    ]
}
