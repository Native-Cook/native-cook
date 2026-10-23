import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/// A kaleidoscope seen through its brass eyepiece. Coloured glass beads and shards lie loose in a
/// round chamber and tumble as it turns; mirrors fold one wedge of it into a mandala of 6 to 12
/// segments. Drag around the window to turn the chamber, pinch to change the mirrors, tap to drop
/// another bead in.
struct KaleidoscopeTwist: View {
    /// Turns the chamber, shakes it and changes the mirrors on a 14 s loop. Off by default.
    var autoPlay = false
    /// Seed of the pieces in the chamber.
    var seed = 20_261_014

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    // The chamber lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = KaleidoscopeTwistEngine()
    @State private var backdrop: Image?

    private typealias M = KaleidoscopeTwistMetrics

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
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
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let layout = KaleidoscopeTwistLayout(size: size, top: insets.top, bottom: insets.bottom)
        let seed = self.seed
        // Read here, outside the timeline's closure, so the stage is rebuilt once it is baked.
        let backdrop = self.backdrop
        engine.layout = layout
        engine.reduced = reduced
        return ZStack(alignment: .topLeading) {
            // The room: a warm dark with a little light around the eyepiece, and its shadow.
            Canvas { context, _ in KaleidoscopeTwistPainter.drawRoom(&context, layout) }
                .allowsHitTesting(false)

            TimelineView(.animation) { timeline in
                let tick = engine.step(to: timeline.date, seed: seed, looping: auto, reduced: reduced)
                if let st = engine.sim {
                    let side = layout.layerSide
                    let segs = KaleidoscopeTwistPainter.segments(st, reduced: reduced)
                    ZStack(alignment: .topLeading) {
                        // The object cell, drawn this frame into the layer the mirrors fold.
                        Canvas { context, _ in
                            _ = tick
                            KaleidoscopeTwistPainter.drawCell(&context, st, side: side, backdrop: backdrop)
                        }
                        .frame(width: side, height: side)
                        .layerEffect(
                            ShaderLibrary.kaleidoscopeTwistView(
                                .float2(side / 2, side / 2),
                                .float(layout.viewR),
                                .float2(side / 2, side / 2),
                                .float2(M.apexX, M.apexY),
                                .float(M.span),
                                .float(segs.lower),
                                .float(segs.lower + 2),
                                .float(segs.mix),
                                .float(segs.swirl),
                                .float2(cos(st.angle), sin(st.angle))
                            ),
                            maxSampleOffset: CGSize(width: side, height: side)
                        )
                        .offset(x: layout.cx - side / 2, y: layout.cy - side / 2)

                        Canvas { context, _ in
                            _ = tick
                            KaleidoscopeTwistPainter.drawGlassAndBrass(&context, layout, st)
                        }

                        readout(KaleidoscopeTwistPainter.facets(st), layout: layout)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
            }
            .allowsHitTesting(false)

            Text("KALEIDOSCOPE")
                .font(.custom("AvenirNext-DemiBold", fixedSize: 12))
                .kerning(3.2)
                .foregroundStyle(Color(hex: M.muted))
                .frame(width: size.width)
                .offset(y: insets.top + 18)
                .allowsHitTesting(false)

            Text("Drag to turn · Pinch for mirrors · Tap to drop a bead")
                .font(.custom("AvenirNext-Medium", fixedSize: 13))
                .foregroundStyle(Color(hex: M.faint))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
                .frame(width: size.width, height: size.height - insets.bottom - 22, alignment: .bottom)
                .allowsHitTesting(false)

            KaleidoscopeTwistTouchArea(engine: engine)
                .frame(width: size.width, height: size.height)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Color(hex: 0x0b090d))
        .clipped()
        .onChange(of: auto) { engine.sim?.hold = 0 }
        .task(id: displayScale) {
            self.backdrop = KaleidoscopeTwistPainter.bakeBackdrop(scale: max(2, displayScale))
        }
        .accessibilityElement()
        .accessibilityLabel("Kaleidoscope with \(engine.sim.map { KaleidoscopeTwistPainter.facets($0) } ?? 8) mirror segments. Drag around it to turn, pinch to change the mirrors, tap to drop a bead.")
    }

    private func readout(_ facets: Int, layout: KaleidoscopeTwistLayout) -> some View {
        VStack(spacing: 2) {
            Text(facets < 10 ? "0\(facets)" : "\(facets)")
                .font(.custom("AvenirNext-DemiBold", fixedSize: 40).monospacedDigit())
                .kerning(2)
                .foregroundStyle(Color(hex: M.ink))
            Text("MIRROR SEGMENTS")
                .font(.custom("AvenirNext-Medium", fixedSize: 12))
                .kerning(1.6)
                .foregroundStyle(Color(hex: M.muted))
        }
        .frame(width: layout.size.width)
        .offset(y: layout.cy + layout.outerR + 30)
    }
}

enum KaleidoscopeTwistMetrics {
    // Jewel glass: the colour of each tone in full light, its pale highlight and its deep edge.
    static let glass: [UInt32] = [0xff2d55, 0xffab1f, 0x19d08f, 0x2f6bff, 0xa04dff, 0x2fd7ff, 0xff5fb0, 0xffe03a]
    static let pale: [UInt32] = [0xffd0da, 0xfff0c8, 0xd2ffef, 0xd6e3ff, 0xecd9ff, 0xd9f8ff, 0xffe0f0, 0xfffbd6]
    static let deep: [UInt32] = [0x5c0018, 0x6a3300, 0x00442c, 0x0a1a66, 0x2e0866, 0x003f55, 0x5e0a38, 0x5f4a00]

    // The view: its radius (pt, at most), and the window's centre as a share of the safe height.
    static let viewMax = 158.0
    static let centerAt = 0.47
    // The wedge of the chamber the mirrors see: its tip, and how far it reaches (cell units).
    static let apexX = 0.0
    static let apexY = 0.05 * KaleidoscopeTwistConst.cellR
    static let span = 0.95 * KaleidoscopeTwistConst.cellR
    // While the count morphs the image swirls by up to this much (rad per view radius).
    static let swirl = 0.55
    // The brass: a fixed bezel `bezel` wide, then the knurled ring the hand turns, `ring` wide.
    static let bezel = 10.0
    static let ring = 22.0
    static let knurls = 132
    // Iris blades over the seam of the loop.
    static let blades = 9
    // Light falls on the glass from the upper left of the screen.
    static let lightX = -0.55
    static let lightY = -0.83
    // A drag turns the chamber; on release it keeps spinning at the finger's speed, at most maxSpin.
    static let maxSpin = 9.0
    static let grabMin = 18.0
    // Pinch: the count goes as 1 / scale^pinchPower; past the range it resists.
    static let pinchPower = 1.4
    static let rubber = 0.3
    // After a touch the loop waits this long (s), then picks up again.
    static let userHold = 4.0
    // A pan starts after this much travel (pt); a tap is a touch lifted within this long (s).
    static let panSlop = 4.0
    static let tapTime = 0.3

    static let ink: UInt32 = 0xe9dcc0
    static let muted: UInt32 = 0x8a7d66
    static let faint: UInt32 = 0x5d5446

    // The object cell spans this many units either side of its centre.
    static let cellHalf = KaleidoscopeTwistConst.cellR * 1.05
}

/// Where the eyepiece sits on this screen.
struct KaleidoscopeTwistLayout {
    let size: CGSize
    let viewR: Double
    let cx: Double
    let cy: Double
    let outerR: Double
    /// The side of the square layer the object cell is drawn into, centred on the window.
    let layerSide: Double

    init(size: CGSize, top: Double, bottom: Double) {
        typealias M = KaleidoscopeTwistMetrics
        self.size = size
        // At least 1 pt, so nothing divides by it or draws a negative radius before the first layout.
        viewR = max(1, min(M.viewMax, size.width / 2 - M.bezel - M.ring - 16))
        cx = size.width / 2
        cy = top + (size.height - top - bottom) * M.centerAt
        outerR = viewR + M.bezel + M.ring
        layerSide = (2 * max(viewR + 2, M.cellHalf + 1)).rounded(.up)
    }

    static let zero = KaleidoscopeTwistLayout(size: .zero, top: 0, bottom: 0)
}

/// Drives the simulation from the display clock, like the React Native frame callback, and turns
/// touches into the gestures of the block.
final class KaleidoscopeTwistEngine {
    private typealias K = KaleidoscopeTwistConst
    private typealias M = KaleidoscopeTwistMetrics

    private(set) var sim: KaleidoscopeTwistSim?
    private var acc = 0.0
    private var last: Date?
    private var tick = 0
    var layout = KaleidoscopeTwistLayout.zero
    var reduced = false

    private var fingerLast = 0.0
    private var pinchFrom = K.segStart
    private var shownEven = K.segStart
    private let selection = UISelectionFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)

    /// Steps the simulation to `date`; returns a number that changes on every frame that stepped.
    func step(to date: Date, seed: Int, looping: Bool, reduced: Bool) -> Int {
        let st: KaleidoscopeTwistSim
        if let existing = sim {
            st = existing
        } else {
            st = KaleidoscopeTwistSim(seed: seed, looping: looping, reduced: reduced)
            sim = st
        }
        st.looping = looping
        st.reduced = reduced
        let elapsed = last.map { date.timeIntervalSince($0) } ?? 0.016
        last = date
        let dt = min(max(0, elapsed), Double(K.maxTicks) * K.tick)
        var pending = acc + dt
        var ticks = 0
        while pending >= K.tick && ticks < K.maxTicks {
            st.step()
            pending -= K.tick
            ticks += 1
        }
        acc = min(pending, K.tick)
        if ticks > 0 { tick &+= 1 }
        return tick
    }

    private func touched() {
        sim?.hold = M.userHold
    }

    // Pan: one finger turns the chamber about the window's centre.

    func panBegan(_ p: CGPoint) {
        guard let st = sim else { return }
        if hypot(p.x - layout.cx, p.y - layout.cy) < M.grabMin { return }
        touched()
        st.dragging = true
        st.angVel = 0
        fingerLast = atan2(p.y - layout.cy, p.x - layout.cx)
    }

    func panMoved(_ p: CGPoint) {
        guard let st = sim, st.dragging else { return }
        let now = atan2(p.y - layout.cy, p.x - layout.cx)
        var d = now - fingerLast
        if d > Double.pi { d -= 2 * Double.pi }
        if d < -Double.pi { d += 2 * Double.pi }
        fingerLast = now
        st.angle += d
    }

    func panEnded(_ p: CGPoint, velocity: CGVector) {
        guard let st = sim, st.dragging else { return }
        let rx = p.x - layout.cx
        let ry = p.y - layout.cy
        let spin = (rx * velocity.dy - ry * velocity.dx) / max(rx * rx + ry * ry, 900)
        st.angVel = reduced ? 0 : max(-M.maxSpin, min(M.maxSpin, spin))
        st.dragging = false
        touched()
    }

    func panCancelled() {
        sim?.dragging = false
    }

    // Pinch: the number of mirror segments.

    func pinchBegan() {
        guard let st = sim else { return }
        touched()
        st.pinching = true
        pinchFrom = st.seg
        shownEven = st.segTarget
        selection.prepare()
    }

    func pinchChanged(scale: Double) {
        guard let st = sim, st.pinching else { return }
        let raw = pinchFrom / pow(max(0.05, scale), M.pinchPower)
        var seg = raw
        if raw < K.segMin { seg = K.segMin - (K.segMin - raw) * M.rubber }
        if raw > K.segMax { seg = K.segMax + (raw - K.segMax) * M.rubber }
        st.seg = seg
        st.segVel = 0
        let even = max(K.segMin, min(K.segMax, KaleidoscopeTwistSim.jsRound(seg / 2) * 2))
        if even != shownEven {
            shownEven = even
            selection.selectionChanged()
        }
    }

    func pinchEnded() {
        guard let st = sim, st.pinching else { return }
        st.setSegments(st.seg)
        st.pinching = false
        touched()
    }

    // Tap: drops a bead under the finger.

    func tapped(_ p: CGPoint) {
        guard let st = sim else { return }
        let dx = (p.x - layout.cx) / layout.viewR
        let dy = (p.y - layout.cy) / layout.viewR
        let r = hypot(dx, dy)
        if r > 1 { return }
        touched()
        // The same fold the mirrors use, so the bead lands under the finger (and in every mirror).
        let w = 2 * Double.pi / max(2, KaleidoscopeTwistSim.jsRound(st.seg))
        let period = 2 * w
        let a = atan2(dy, dx)
        var m = a - period * (a / period).rounded(.down)
        if m > w { m = period - m }
        m += Double.pi / 2 - w / 2
        let vx = M.apexX + cos(m) * r * M.span
        let vy = M.apexY + sin(m) * r * M.span
        let c = cos(st.angle)
        let s = sin(st.angle)
        st.addPiece(c * vx + s * vy, -s * vx + c * vy)
        impact.impactOccurred()
    }
}

/// A transparent view that reads every touch on the block and sorts them into a pan (one finger,
/// after 4 pt), a pinch (two fingers before the pan starts) and a tap (lifted within 0.3 s), the
/// race of the React Native gesture handlers.
private struct KaleidoscopeTwistTouchArea: UIViewRepresentable {
    let engine: KaleidoscopeTwistEngine

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = true
        let reader = KaleidoscopeTwistTouchReader()
        reader.engine = engine
        reader.delegate = context.coordinator
        view.addGestureRecognizer(reader)
        context.coordinator.reader = reader
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.reader?.engine = engine
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    /// Lets the reader run beside the gallery's back swipe, but not beside a scroll view's pan:
    /// the reader begins on touch-down, so an enclosing scroll can no longer take the drag away.
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        weak var reader: KaleidoscopeTwistTouchReader?

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            !(other.view is UIScrollView)
        }
    }
}

/// A recogniser that claims every touch as soon as it lands and follows it to the end.
final class KaleidoscopeTwistTouchReader: UIGestureRecognizer {
    fileprivate var engine: KaleidoscopeTwistEngine?

    private enum Mode { case idle, maybe, pan, pinch, done }
    private var mode = Mode.idle
    private var touches: [UITouch] = []
    private var start = CGPoint.zero
    private var began = 0.0
    private var pinchDistance = 1.0
    private var samples: [(t: Double, p: CGPoint)] = []

    init() {
        super.init(target: nil, action: nil)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
    }

    private func distance() -> Double {
        guard touches.count >= 2 else { return 1 }
        let a = touches[0].location(in: view)
        let b = touches[1].location(in: view)
        return max(1, hypot(a.x - b.x, a.y - b.y))
    }

    override func touchesBegan(_ set: Set<UITouch>, with event: UIEvent) {
        if state == .possible { state = .began }
        for t in set { touches.append(t) }
        if mode == .idle, let first = touches.first {
            mode = .maybe
            start = first.location(in: view)
            began = first.timestamp
            samples = [(first.timestamp, start)]
        }
        if mode == .maybe && touches.count >= 2 {
            mode = .pinch
            pinchDistance = distance()
            engine?.pinchBegan()
        }
    }

    override func touchesMoved(_ set: Set<UITouch>, with event: UIEvent) {
        if state == .began || state == .changed { state = .changed }
        guard let first = touches.first else { return }
        let p = first.location(in: view)
        switch mode {
        case .maybe:
            if hypot(p.x - start.x, p.y - start.y) >= KaleidoscopeTwistMetrics.panSlop {
                mode = .pan
                engine?.panBegan(p)
                record(first)
            }
        case .pan:
            record(first)
            engine?.panMoved(p)
        case .pinch:
            engine?.pinchChanged(scale: distance() / pinchDistance)
        default:
            break
        }
    }

    private func record(_ t: UITouch) {
        samples.append((t.timestamp, t.location(in: view)))
        if samples.count > 8 { samples.removeFirst(samples.count - 8) }
    }

    /// The finger's speed over its last ~0.1 s (pt/s).
    private func velocity() -> CGVector {
        guard let last = samples.last else { return .zero }
        let from = samples.first { last.t - $0.t <= 0.1 } ?? last
        let dt = last.t - from.t
        if dt <= 0 { return .zero }
        return CGVector(dx: (last.p.x - from.p.x) / dt, dy: (last.p.y - from.p.y) / dt)
    }

    override func touchesEnded(_ set: Set<UITouch>, with event: UIEvent) {
        finish(set, cancelled: false)
        if touches.isEmpty { state = .ended }
    }

    override func touchesCancelled(_ set: Set<UITouch>, with event: UIEvent) {
        finish(set, cancelled: true)
        if touches.isEmpty { state = .cancelled }
    }

    override func reset() {
        super.reset()
        // A gesture cut short without its touches ending must not leave the chamber held.
        if mode == .pan { engine?.panCancelled() }
        if mode == .pinch { engine?.pinchEnded() }
        touches.removeAll()
        mode = .idle
    }

    private func finish(_ set: Set<UITouch>, cancelled: Bool) {
        let lifted = touches.first.map { set.contains($0) } ?? false
        let first = touches.first
        touches.removeAll { set.contains($0) }
        switch mode {
        case .maybe:
            if !cancelled, lifted, let first, first.timestamp - began <= KaleidoscopeTwistMetrics.tapTime {
                engine?.tapped(first.location(in: view))
            }
            mode = .done
        case .pan:
            if lifted, let first {
                if cancelled {
                    engine?.panCancelled()
                } else {
                    record(first)
                    engine?.panEnded(first.location(in: view), velocity: velocity())
                }
                mode = .done
            }
        case .pinch:
            engine?.pinchEnded()
            mode = .done
        default:
            break
        }
        if touches.isEmpty { mode = .idle }
    }
}

/// Draws the room, the object cell, the iris and the brass.
enum KaleidoscopeTwistPainter {
    private typealias K = KaleidoscopeTwistConst
    private typealias M = KaleidoscopeTwistMetrics

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color { Color(hex: hex, opacity: alpha) }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    /// An arc of the circle inscribed in `rect`, from `start` through `sweep` degrees (Skia's `drawArc`).
    private static func arc(_ rect: CGRect, _ start: Double, _ sweep: Double) -> Path {
        var p = Path()
        p.addRelativeArc(center: CGPoint(x: rect.midX, y: rect.midY), radius: rect.width / 2, startAngle: .degrees(start), delta: .degrees(sweep))
        return p
    }

    /// A hash of two integers to [0, 1), with JavaScript's 32-bit wrapping maths.
    static func hash(_ a: Int, _ b: Int) -> Double {
        var h = Int32(truncatingIfNeeded: a) &* 374_761_393 &+ Int32(truncatingIfNeeded: b) &* 668_265_263
        h = (h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 13)) &* 1_274_126_177
        h ^= Int32(bitPattern: UInt32(bitPattern: h) >> 16)
        return Double(UInt32(bitPattern: h)) / 4_294_967_296
    }

    /// The count the readout shows: where the mirrors are heading.
    static func facets(_ st: KaleidoscopeTwistSim) -> Int {
        let v = st.pinching ? max(K.segMin, min(K.segMax, KaleidoscopeTwistSim.jsRound(st.seg / 2) * 2)) : st.segTarget
        // Int(_:) traps on NaN; the count is always finite, but never let a bad frame crash the readout.
        return v.isFinite ? Int(v) : Int(K.segStart)
    }

    /// Two even counts either side of the morph, blended, with a swirl that peaks halfway.
    static func segments(_ st: KaleidoscopeTwistSim, reduced: Bool) -> (lower: Double, mix: Double, swirl: Double) {
        let seg = max(K.segMin - 1.5, min(K.segMax + 1.5, st.seg))
        let lower = max(4, (seg / 2).rounded(.down) * 2)
        let t = (seg - lower) / 2
        return (lower, t, reduced ? 0 : M.swirl * sin(Double.pi * t))
    }

    static func drawRoom(_ c: inout GraphicsContext, _ layout: KaleidoscopeTwistLayout) {
        let size = layout.size
        c.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: color(0x241c22), location: 0),
                    .init(color: color(0x120e13), location: 0.4),
                    .init(color: color(0x070608), location: 1),
                ]),
                center: CGPoint(x: layout.cx, y: layout.cy - 20),
                startRadius: 0,
                endRadius: max(size.width, size.height) * 0.75
            )
        )
        c.drawLayer { l in
            l.addFilter(.blur(radius: 22))
            l.fill(circle(layout.cx, layout.cy + 16, layout.outerR + 6), with: .color(.black.opacity(0.6)))
        }
    }

    /// The frosted diffuser behind the pieces: it does not change, so it is baked once.
    @MainActor
    static func bakeBackdrop(scale: Double) -> Image? {
        let half = M.cellHalf
        let R = K.cellR
        let renderer = ImageRenderer(content: Canvas { c, _ in
            c.translateBy(x: half, y: half)
            c.fill(
                Path(CGRect(x: -R * 1.5, y: -R * 1.5, width: R * 3, height: R * 3)),
                with: .radialGradient(
                    Gradient(stops: [
                        .init(color: color(0x22184a), location: 0),
                        .init(color: color(0x100c2a), location: 0.55),
                        .init(color: color(0x06050f), location: 1),
                    ]),
                    center: .zero, startRadius: 0, endRadius: R * 1.4
                )
            )
            // Frost on the diffuser glass: a few soft tints.
            let frost: [(Double, Double, Double, UInt32)] = [(-0.4, -0.5, 0.5, 0x4a2a8a), (0.5, -0.2, 0.42, 0x123f6a), (-0.1, 0.45, 0.55, 0x3a1450), (0.35, 0.6, 0.3, 0x0f4a52)]
            for (fx, fy, fr, col) in frost {
                c.drawLayer { l in
                    l.addFilter(.blur(radius: fr * R * 0.6))
                    l.fill(circle(fx * R, fy * R, fr * R), with: .color(color(col, 0.35)))
                }
            }
        }.frame(width: 2 * half, height: 2 * half))
        renderer.scale = scale
        renderer.isOpaque = true
        return renderer.cgImage.map { Image(decorative: $0, scale: scale) }
    }

    /// The soft glow a Skia blur mask gives a disc of radius `ratio` σ: (location, strength) stops
    /// out to `ratio + 3` σ, worked out once by integrating the Gaussian over the disc.
    private static func blurredDisc(ratio: Double) -> [(Double, Double)] {
        let outer = ratio + 3
        let samples = 12
        let rings = 36
        let spokes = 48
        var out: [(Double, Double)] = []
        for k in 0...samples {
            let d = outer * Double(k) / Double(samples)
            var sum = 0.0
            for i in 0..<rings {
                let rho = ratio * (Double(i) + 0.5) / Double(rings)
                for j in 0..<spokes {
                    let th = 2 * Double.pi * (Double(j) + 0.5) / Double(spokes)
                    let dx = rho * cos(th) - d
                    let dy = rho * sin(th)
                    sum += exp(-(dx * dx + dy * dy) / 2) * rho
                }
            }
            let v = sum * (ratio / Double(rings)) * (2 * Double.pi / Double(spokes)) / (2 * Double.pi)
            out.append((Double(k) / Double(samples), min(1, v)))
        }
        return out
    }

    // A bead's glow: a disc of 1.3 r blurred by 0.55 r; a shard's: about 0.87 r blurred by 0.5 r.
    private static let beadGlow = blurredDisc(ratio: 1.3 / 0.55)
    private static let shardGlow = blurredDisc(ratio: 0.87 / 0.5)

    private static func glow(_ c: inout GraphicsContext, _ x: Double, _ y: Double, outer: Double, profile: [(Double, Double)], hex: UInt32, alpha: Double) {
        let stops = profile.map { Gradient.Stop(color: color(hex, alpha * $0.1), location: $0.0) }
        c.fill(circle(x, y, outer), with: .radialGradient(Gradient(stops: stops), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: outer))
    }

    /// A round glass bead, lit from (lx, ly): a glow on the diffuser, the body, a caustic, a glint.
    private static func drawBead(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ tone: Int, _ lx: Double, _ ly: Double) {
        var add = c
        add.blendMode = .plusLighter
        glow(&add, x, y, outer: r * (1.3 + 3 * 0.55), profile: beadGlow, hex: M.glass[tone], alpha: 0.32)
        c.fill(
            circle(x, y, r),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: color(M.pale[tone], 0.94), location: 0),
                    .init(color: color(M.glass[tone], 0.94), location: 0.45),
                    .init(color: color(M.deep[tone], 0.94), location: 1),
                ]),
                center: CGPoint(x: x + lx * r * 0.35, y: y + ly * r * 0.35),
                startRadius: 0,
                endRadius: r * 1.25
            )
        )
        // Light focused through the bead lands on its far side.
        let back = atan2(-ly, -lx) * 180 / Double.pi
        c.stroke(arc(CGRect(x: x - r * 0.66, y: y - r * 0.66, width: r * 1.32, height: r * 1.32), back - 38, 76), with: .color(color(M.pale[tone], 0.75)), style: StrokeStyle(lineWidth: r * 0.16, lineCap: .round))
        c.fill(circle(x + lx * r * 0.42, y + ly * r * 0.42, r * 0.2), with: .color(.white.opacity(0.9)))
        c.stroke(circle(x, y, r - 0.4), with: .color(color(M.deep[tone], 0.6)), lineWidth: 0.8)
    }

    /// A flat shard of glass: an irregular polygon, its bevelled edges catching the light.
    private static func drawShard(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ ang: Double, _ tone: Int, _ shape: Int, _ lx: Double, _ ly: Double) {
        let n = 5 + shape % 2
        var path = Path()
        for k in 0..<n {
            let a = ang + (Double(k) + (hash(shape, k) - 0.5) * 0.55) * 2 * Double.pi / Double(n)
            let d = r * (0.72 + 0.3 * hash(shape, k + 9))
            let p = CGPoint(x: x + cos(a) * d, y: y + sin(a) * d)
            if k == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        path.closeSubpath()
        var add = c
        add.blendMode = .plusLighter
        glow(&add, x, y, outer: r * (0.87 + 3 * 0.5), profile: shardGlow, hex: M.glass[tone], alpha: 0.26)
        c.fill(
            path,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: color(M.pale[tone], 0.82), location: 0),
                    .init(color: color(M.glass[tone], 0.82), location: 0.5),
                    .init(color: color(M.deep[tone], 0.82), location: 1),
                ]),
                startPoint: CGPoint(x: x + lx * r, y: y + ly * r),
                endPoint: CGPoint(x: x - lx * r, y: y - ly * r)
            )
        )
        c.stroke(path, with: .color(color(M.pale[tone], 0.7)), lineWidth: 0.9)
    }

    /// The object cell this frame, centred in a layer `side` pt square: the diffuser and every piece.
    static func drawCell(_ context: inout GraphicsContext, _ st: KaleidoscopeTwistSim, side: Double, backdrop: Image?) {
        let half = M.cellHalf
        var c = context
        c.translateBy(x: side / 2, y: side / 2)
        // The cell ends at its edge, like the React Native block's offscreen image.
        let box = CGRect(x: -half, y: -half, width: 2 * half, height: 2 * half)
        c.clip(to: Path(box))
        if let backdrop {
            c.draw(backdrop, in: box)
        } else {
            c.fill(Path(box), with: .color(color(0x100c2a)))
        }
        // Light from the view's upper left, seen from inside the turned chamber.
        let ca = cos(st.angle)
        let sa = sin(st.angle)
        let lx = ca * M.lightX + sa * M.lightY
        let ly = -sa * M.lightX + ca * M.lightY
        for kind in [K.shard, K.bead, K.seed] {
            for i in 0..<st.count where st.kind[i] == kind {
                let r = st.grownRadius(i)
                let x = st.x[i]
                let y = st.y[i]
                if kind == K.shard {
                    drawShard(&c, x, y, r, st.ang[i], st.tone[i], st.shape[i], lx, ly)
                } else if kind == K.bead {
                    drawBead(&c, x, y, r, st.tone[i], lx, ly)
                } else {
                    c.fill(circle(x, y, r), with: .color(color(M.glass[st.tone[i]], 0.95)))
                    c.fill(circle(x + lx * r * 0.4, y + ly * r * 0.4, r * 0.35), with: .color(.white.opacity(0.85)))
                }
            }
        }
    }

    /// Fall-off at the window's edge, the eyepiece's glass, the iris and the brass.
    static func drawGlassAndBrass(_ c: inout GraphicsContext, _ layout: KaleidoscopeTwistLayout, _ st: KaleidoscopeTwistSim) {
        let cx = layout.cx
        let cy = layout.cy
        let viewR = layout.viewR
        c.fill(
            circle(cx, cy, viewR + 1),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .black.opacity(0), location: 0),
                    .init(color: .black.opacity(0), location: 0.6),
                    .init(color: .black.opacity(0.45), location: 0.88),
                    .init(color: .black.opacity(0.92), location: 1),
                ]),
                center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: viewR
            )
        )
        c.drawLayer { l in
            l.addFilter(.blur(radius: 2.5))
            l.stroke(
                arc(CGRect(x: cx - viewR * 0.82, y: cy - viewR * 0.82, width: viewR * 1.64, height: viewR * 1.64), 198, 52),
                with: .color(.white.opacity(0.07)),
                style: StrokeStyle(lineWidth: 5, lineCap: .round)
            )
        }
        drawIris(&c, cx, cy, viewR, st.iris)
        drawBrass(&c, cx, cy, viewR, st.angle)
    }

    /// The iris over the window, `open` 0 (shut) … 1 (wide open): curved steel leaves.
    private static func drawIris(_ c: inout GraphicsContext, _ cx: Double, _ cy: Double, _ viewR: Double, _ open: Double) {
        if open >= 0.999 { return }
        let a = viewR * 1.02 * open
        let turn = (1 - open) * 0.9
        var blades = circle(cx, cy, viewR + 1)
        // The opening: a polygon whose sides are the blades' edges, turning as it closes.
        for k in 0...M.blades {
            let t = turn + Double(k) * 2 * Double.pi / Double(M.blades)
            let p = CGPoint(x: cx + cos(t) * a, y: cy + sin(t) * a)
            if k == 0 { blades.move(to: p) } else { blades.addLine(to: p) }
        }
        blades.closeSubpath()
        c.fill(
            blades,
            with: .conicGradient(
                Gradient(stops: [
                    .init(color: color(0x26262c), location: 0),
                    .init(color: color(0x4b4b54), location: 0.25),
                    .init(color: color(0x1b1b20), location: 0.5),
                    .init(color: color(0x3d3d45), location: 0.75),
                    .init(color: color(0x26262c), location: 1),
                ]),
                center: CGPoint(x: cx, y: cy)
            ),
            style: FillStyle(eoFill: true, antialiased: true)
        )
        // Each blade's leading edge runs from a corner of the opening out to the rim.
        for k in 0..<M.blades {
            let t = turn + Double(k) * 2 * Double.pi / Double(M.blades)
            let t1 = t + 1.25
            var p = Path()
            p.move(to: CGPoint(x: cx + cos(t) * a, y: cy + sin(t) * a))
            p.addQuadCurve(
                to: CGPoint(x: cx + cos(t1) * (viewR + 1), y: cy + sin(t1) * (viewR + 1)),
                control: CGPoint(x: cx + cos(t + 0.55) * (a + (viewR - a) * 0.75), y: cy + sin(t + 0.55) * (a + (viewR - a) * 0.75))
            )
            c.stroke(p, with: .color(color(0x0b0b0e, 0.9)), lineWidth: 1.2)
            var shine = c
            shine.translateBy(x: 0.6, y: 0.6)
            shine.stroke(p, with: .color(color(0x9a9aa6, 0.5)), lineWidth: 0.6)
        }
    }

    private static let brass = Gradient(stops: [
        .init(color: color(0x5b3d15), location: 0),
        .init(color: color(0xc99a45), location: 0.12),
        .init(color: color(0xfff0c0), location: 0.2),
        .init(color: color(0xb8862f), location: 0.32),
        .init(color: color(0x4a3010), location: 0.5),
        .init(color: color(0xa9782a), location: 0.64),
        .init(color: color(0xf4d690), location: 0.72),
        .init(color: color(0x7b5620), location: 0.86),
        .init(color: color(0x5b3d15), location: 1),
    ])

    /// The brass eyepiece: a fixed bezel and the knurled ring that turns with the chamber.
    private static func drawBrass(_ c: inout GraphicsContext, _ cx: Double, _ cy: Double, _ viewR: Double, _ angle: Double) {
        let center = CGPoint(x: cx, y: cy)
        // Bezel: turned brass, its highlights starting at -30°.
        c.stroke(circle(cx, cy, viewR + M.bezel / 2), with: .conicGradient(brass, center: center, angle: .degrees(-30)), lineWidth: M.bezel)
        // The knurled ring, a shade darker and a little proud of the bezel.
        let ringR = viewR + M.bezel + M.ring / 2
        c.stroke(circle(cx, cy, ringR), with: .conicGradient(brass, center: center, angle: .degrees(-20)), lineWidth: M.ring)
        c.stroke(circle(cx, cy, ringR), with: .color(.black.opacity(0.22)), lineWidth: M.ring)
        var dark = Path()
        var light = Path()
        let r0 = viewR + M.bezel + 3
        let r1 = viewR + M.bezel + M.ring - 3
        for k in 0..<M.knurls {
            let t = angle + Double(k) * 2 * Double.pi / Double(M.knurls)
            dark.move(to: CGPoint(x: cx + cos(t) * r0, y: cy + sin(t) * r0))
            dark.addLine(to: CGPoint(x: cx + cos(t) * r1, y: cy + sin(t) * r1))
            let u = t + 0.012
            light.move(to: CGPoint(x: cx + cos(u) * r0, y: cy + sin(u) * r0))
            light.addLine(to: CGPoint(x: cx + cos(u) * r1, y: cy + sin(u) * r1))
        }
        c.stroke(dark, with: .color(color(0x2a1a06, 0.55)), lineWidth: 1.3)
        c.stroke(light, with: .color(color(0xfff2c8, 0.35)), lineWidth: 0.8)
        // An engraved index on the ring, so its turn reads at a glance.
        let tick = angle - Double.pi / 2
        c.fill(circle(cx + cos(tick) * ringR, cy + sin(tick) * ringR, 3.2), with: .color(color(0x2a1a06, 0.85)))
        c.fill(circle(cx + cos(tick) * ringR - 0.6, cy + sin(tick) * ringR - 0.6, 1.4), with: .color(color(0xfff2c8, 0.5)))
        // Grooves and the rim's catchlights.
        c.stroke(circle(cx, cy, viewR + M.bezel), with: .color(color(0x1d1206, 0.9)), lineWidth: 1.4)
        c.stroke(circle(cx, cy, viewR + 0.5), with: .color(color(0x120b03)), lineWidth: 1.6)
        c.stroke(circle(cx, cy, viewR + M.bezel + M.ring), with: .color(color(0x1d1206, 0.9)), lineWidth: 1.4)
        let outer = viewR + M.bezel + M.ring + 0.4
        c.stroke(arc(CGRect(x: cx - outer, y: cy - outer, width: outer * 2, height: outer * 2), 200, 70), with: .color(color(0xfff4d0, 0.55)), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
    }
}
