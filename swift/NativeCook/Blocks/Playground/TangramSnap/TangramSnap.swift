import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/// A tangram on a dark lacquered board: seven wooden pieces and the faint outline of a figure (a
/// cat, a boat, a runner, a swan). Drag a piece and it is drawn into its place when close; tap to
/// turn it 45°, double-tap to flip it, turn it with two fingers. When the last piece is in, colour
/// floods the figure and the pieces melt into one solid shape. The figures below morph the outline,
/// or the finished figure itself, into the next.
struct TangramSnap: View {
    /// Sets the cat piece by piece, fills it, morphs it into the swan and takes it apart, on a loop.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var holder = TangramSnapHolder()

    private typealias P = TangramSnapPainter

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
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let frame = holder.step(to: timeline.date)
                let name = TangramSnapModel.silhouettes[engine.puzzle].name
                ZStack(alignment: .topLeading) {
                    Canvas { context, _ in
                        _ = frame
                        P.draw(in: &context, engine: engine, insetTop: insets.top)
                    }
                    .frame(width: size.width, height: size.height)

                    VStack(alignment: .leading, spacing: 3) {
                        Text("Tangram · No. \(engine.puzzle + 1) of \(TangramSnapModel.silhouettes.count)".uppercased())
                            .font(.custom("AvenirNext-DemiBold", size: 10.5))
                            .kerning(2.4)
                            .foregroundStyle(P.muted)
                        Text(name)
                            .font(.custom("Baskerville-SemiBold", size: 30))
                            .foregroundStyle(P.ink)
                        Text(engine.solved ? "Seven pieces, one shape." : "Drag · Tap to turn · Double-tap to flip")
                            .font(.custom("AvenirNext-Medium", size: 12.5))
                            .foregroundStyle(P.muted)
                    }
                    .frame(width: max(0, size.width - 144), alignment: .leading)
                    .padding(.leading, 24)
                    .padding(.top, insets.top + 18)
                    .allowsHitTesting(false)
                }
                .accessibilityElement()
                .accessibilityLabel("Tangram, \(name). Drag the pieces into the outline; tap to turn a piece, double-tap to flip it.")
                .accessibilityAddTraits(.allowsDirectInteraction)
            }

            TangramSnapTouches(holder: holder)
                .frame(width: size.width, height: size.height)
                .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height)
        .background(P.board[2])
        .clipped()
    }
}

/// Owns the engine (built once `autoPlay` is known, so the first frame is the script's first frame),
/// steps it from the display clock and plays its haptics.
@MainActor
final class TangramSnapHolder {
    private(set) var current: TangramSnapEngine?
    private var last: Date?
    private var frameNo = 0
    private let snapFeel = UIImpactFeedbackGenerator(style: .light)
    private let solvedFeel = UINotificationFeedbackGenerator()
    private let selectFeel = UISelectionFeedbackGenerator()
    private let softFeel = UIImpactFeedbackGenerator(style: .soft)

    func engine(auto: Bool, reduced: Bool) -> TangramSnapEngine {
        if let current, current.reduced == reduced {
            current.setAuto(auto)
            return current
        }
        let made = TangramSnapEngine(auto: auto, reduced: reduced)
        current = made
        last = nil
        return made
    }

    /// Advances the engine to `date` and returns a frame number for the canvas to depend on.
    func step(to date: Date) -> Int {
        guard let engine = current else { return frameNo }
        let elapsed = last.map { max(0, date.timeIntervalSince($0)) }
        last = date
        if engine.frame(elapsed) { frameNo += 1 }
        let events = engine.events
        engine.events = 0
        if events & TangramSnapEngine.buzzSnap != 0 { snapFeel.impactOccurred() }
        if events & TangramSnapEngine.buzzSolved != 0 { solvedFeel.notificationOccurred(.success) }
        if events & TangramSnapEngine.buzzSelect != 0 { selectFeel.selectionChanged() }
        if events & TangramSnapEngine.buzzSoft != 0 { softFeel.impactOccurred() }
        return frameNo
    }
}

/// Every finger on the board, with its speed over the last 100 ms, fed to the engine's gestures
/// (one finger drags, two turn, short touches tap and double-tap). A recogniser that claims each
/// touch as it lands, so neither the gallery's back swipe nor a scroll view can cancel it.
private struct TangramSnapTouches: UIViewRepresentable {
    let holder: TangramSnapHolder

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = true
        let reader = TangramSnapTouchReader()
        reader.holder = holder
        reader.delegate = context.coordinator
        view.addGestureRecognizer(reader)
        context.coordinator.reader = reader
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.reader?.holder = holder
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    /// Runs beside the gallery's back swipe, but not beside a scroll view's pan.
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        weak var reader: TangramSnapTouchReader?

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            !(other.view is UIScrollView)
        }
    }
}

/// Reads every touch from landing to lifting and hands it to the engine with an id and a speed.
final class TangramSnapTouchReader: UIGestureRecognizer {
    fileprivate weak var holder: TangramSnapHolder?
    private var ids: [ObjectIdentifier: Int] = [:]
    private var samples: [Int: [(t: Double, x: Double, y: Double)]] = [:]
    private var nextId = 0

    init() {
        super.init(target: nil, action: nil)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
    }

    private var engine: TangramSnapEngine? { MainActor.assumeIsolated { holder?.current } }

    private func id(of touch: UITouch) -> Int {
        let key = ObjectIdentifier(touch)
        if let id = ids[key] { return id }
        nextId += 1
        ids[key] = nextId
        return nextId
    }

    private func record(_ id: Int, _ touch: UITouch) -> CGPoint {
        let p = touch.location(in: view)
        let t = touch.timestamp
        var list = samples[id] ?? []
        list.append((t, p.x, p.y))
        // Only the last 100 ms of samples measure the speed.
        while let first = list.first, t - first.t > 0.1, list.count > 2 { list.removeFirst() }
        samples[id] = list
        return p
    }

    private func velocity(_ id: Int, _ p: CGPoint, _ t: Double) -> (Double, Double) {
        guard let list = samples[id], let first = list.first(where: { t - $0.t <= 0.1 }), t - first.t > 0.008 else { return (0, 0) }
        return ((p.x - first.x) / (t - first.t), (p.y - first.y) / (t - first.t))
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        if state == .possible { state = .began }
        guard let engine else { return }
        for touch in touches {
            let id = id(of: touch)
            samples[id] = []
            let p = record(id, touch)
            engine.touchDown(id: id, x: p.x, y: p.y)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        if state == .began || state == .changed { state = .changed }
        guard let engine else { return }
        for touch in touches {
            let id = id(of: touch)
            let p = record(id, touch)
            let v = velocity(id, p, touch.timestamp)
            engine.touchMove(id: id, x: p.x, y: p.y, velocityX: v.0, velocityY: v.1)
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        if let engine {
            for touch in touches {
                let id = id(of: touch)
                let p = record(id, touch)
                let v = velocity(id, p, touch.timestamp)
                ids[ObjectIdentifier(touch)] = nil
                samples[id] = nil
                engine.touchUp(id: id, x: p.x, y: p.y, velocityX: v.0, velocityY: v.1)
            }
        }
        if ids.isEmpty { state = .ended }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        for touch in touches { ids[ObjectIdentifier(touch)] = nil }
        samples.removeAll()
        engine?.touchCancel()
        ids.removeAll()
        state = .cancelled
    }

    override func reset() {
        super.reset()
        ids.removeAll()
        samples.removeAll()
    }
}
