import SwiftUI
import UIKit

/// Thirteen glass marbles in a walnut tray with carved cups. Dragging tilts the tray in perspective
/// and the marbles roll downhill, click off each other and fall back into cups; a fast shake
/// scatters them into the air; a tap flicks one. Each throws a shadow lit through with its own
/// colour, and the light moves across the floor as the tray tilts.
struct GlassMarblesTray: View {
    /// Tilts the tray round in a circle, shakes it, lets the marbles settle home, on a loop.
    var autoPlay = false
    var label = "Parlour games · No. 4"
    var title = "Glass Marbles"
    var subtitle = "Twelve cat’s-eyes and a shooter in a walnut tray."

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var holder = GlassMarblesTrayHolder()
    /// True while the drag is alive; it resets without `onEnded` when the system cancels the touch.
    @GestureState private var touching = false

    private typealias P = GlassMarblesTrayPainter

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
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let engine = holder.engine(auto: autoPlay && !reduceMotion, reduced: reduceMotion)
        engine.setLayout(width: size.width, height: size.height, insetTop: insets.top, insetBottom: insets.bottom)
        let top = holder.trayTop(engine: engine, scale: displayScale)
        let pad = P.floorPad
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let frame = holder.step(to: timeline.date)
                ZStack(alignment: .topLeading) {
                    Canvas { context, canvasSize in
                        _ = frame
                        P.drawUnder(in: &context, size: canvasSize, engine: engine, scale: displayScale)
                    }
                    .frame(width: size.width, height: size.height)

                    // The floor plane, drawn flat in the tray's frame and projected onto the screen.
                    // The baked top is drawn in the same canvas, so the caustics add onto the wood.
                    Canvas { context, _ in
                        _ = frame
                        if let top {
                            context.draw(Image(uiImage: top), in: CGRect(x: pad, y: pad, width: engine.trayW, height: engine.trayH))
                        }
                        P.drawFloor(in: &context, engine: engine)
                    }
                    .frame(width: engine.trayW + pad * 2, height: engine.trayH + pad * 2, alignment: .topLeading)
                    .projectionEffect(P.floorProjection(engine: engine))

                    Canvas { context, canvasSize in
                        _ = frame
                        P.drawOver(in: &context, size: canvasSize, engine: engine)
                    }
                    .frame(width: size.width, height: size.height)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }

            Text(label.uppercased())
                .font(.custom("AvenirNext-DemiBold", size: 10.5))
                .kerning(2.4)
                .foregroundStyle(P.muted)
                .padding(.leading, 24)
                .padding(.top, insets.top + 23)
                .allowsHitTesting(false)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.custom("Baskerville-SemiBold", size: 30))
                    .foregroundStyle(P.ink)
                Text(subtitle)
                    .font(.custom("AvenirNext-Medium", size: 13.5))
                    .foregroundStyle(P.muted)
            }
            .padding(.horizontal, 24)
            .frame(width: size.width, height: size.height - insets.bottom - 52, alignment: .bottomLeading)
            .allowsHitTesting(false)

            Text("Drag to tilt · Shake to scatter · Tap a marble")
                .font(.custom("AvenirNext-Medium", size: 12))
                .foregroundStyle(P.muted)
                .opacity(0.75)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
                .frame(width: size.width, height: size.height - insets.bottom - 18, alignment: .bottom)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .background(P.table[2])
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .local)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { value in holder.touch(value.location, at: value.time) }
                .onEnded { value in holder.release(value.location, at: value.time) }
        )
        .onChange(of: touching) { _, isTouching in
            if !isTouching { holder.cancelIfStuck() }
        }
        .accessibilityElement()
        .accessibilityLabel("\(title). Drag to tilt the tray, shake it to scatter the marbles, tap a marble to flick it.")
    }
}

/// Owns the engine (built once `autoPlay` is known, so the first frame is the script's first frame)
/// and the baked tray top, steps the engine from the display clock, plays its haptics, and turns
/// raw touches into its gestures.
@MainActor
final class GlassMarblesTrayHolder {
    private var engine: GlassMarblesTrayEngine?
    private var last: Date?
    private var frameNo = 0
    private var down = false
    private var samples: [(t: Double, x: Double, y: Double)] = []
    private var baked: UIImage?
    private var bakedFor = CGSize.zero
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let select = UISelectionFeedbackGenerator()
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let light = UIImpactFeedbackGenerator(style: .light)

    func engine(auto: Bool, reduced: Bool) -> GlassMarblesTrayEngine {
        if let engine, engine.reduced == reduced {
            engine.setAuto(auto)
            return engine
        }
        let made = GlassMarblesTrayEngine(auto: auto, reduced: reduced)
        engine = made
        last = nil
        return made
    }

    /// The tray's top, baked once per size at the screen's scale.
    func trayTop(engine: GlassMarblesTrayEngine, scale: CGFloat) -> UIImage? {
        guard let tray = engine.tray else { return nil }
        let size = CGSize(width: engine.trayW, height: engine.trayH)
        if let baked, bakedFor == size { return baked }
        let canvas = Canvas { context, _ in
            GlassMarblesTrayPainter.drawTrayTop(in: &context, trayW: size.width, trayH: size.height, tray: tray)
        }
        .frame(width: size.width, height: size.height)
        let renderer = ImageRenderer(content: canvas)
        renderer.scale = scale
        baked = renderer.uiImage
        bakedFor = size
        return baked
    }

    /// Advances the engine to `date` and returns a frame number for the canvases to depend on.
    func step(to date: Date) -> Int {
        guard let engine else { return frameNo }
        let elapsed = last.map { max(0, date.timeIntervalSince($0)) }
        last = date
        if engine.frame(elapsed) { frameNo += 1 }
        let events = engine.events
        engine.events = 0
        if events & GlassMarblesTrayEngine.eventClick != 0 {
            if engine.clickHardness > 420 { rigid.impactOccurred() } else { select.selectionChanged() }
        }
        if events & GlassMarblesTrayEngine.eventShake != 0 { medium.impactOccurred() }
        if events & GlassMarblesTrayEngine.eventFlick != 0 { light.impactOccurred() }
        return frameNo
    }

    /// The finger's speed over its last 100 ms of samples (pt/s).
    private func velocity(at t: Double, x: Double, y: Double) -> (Double, Double) {
        guard let first = samples.first(where: { t - $0.t <= 0.1 }), t - first.t > 0.008 else { return (0, 0) }
        return ((x - first.x) / (t - first.t), (y - first.y) / (t - first.t))
    }

    func touch(_ p: CGPoint, at time: Date) {
        guard let engine else { return }
        let t = time.timeIntervalSinceReferenceDate
        if !down {
            down = true
            samples = [(t, p.x, p.y)]
            engine.touchDown(x: p.x, y: p.y, time: t)
            return
        }
        samples.append((t, p.x, p.y))
        while let first = samples.first, t - first.t > 0.1, samples.count > 2 { samples.removeFirst() }
        let (vx, vy) = velocity(at: t, x: p.x, y: p.y)
        engine.touchMove(x: p.x, y: p.y, time: t, velocityX: vx, velocityY: vy)
    }

    func release(_ p: CGPoint, at time: Date) {
        guard let engine, down else { return }
        down = false
        samples = []
        engine.touchUp(x: p.x, y: p.y, time: time.timeIntervalSinceReferenceDate)
    }

    /// The drag ended without `onEnded` (cancelled): let go of the tray so it does not stay tilted.
    /// Deferred a turn so a normal `onEnded` in the same pass always wins.
    func cancelIfStuck() {
        Task { @MainActor [weak self] in self?.cancelNow() }
    }

    private func cancelNow() {
        guard let engine, down else { return }
        down = false
        samples = []
        engine.touchCancel()
    }
}
