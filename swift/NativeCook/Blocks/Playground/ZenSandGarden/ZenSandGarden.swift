import SwiftUI
import UIKit

/// A raked sand garden seen from above under a low warm sun. Dragging pulls a wooden rake through the
/// sand, leaving five grooves that bend round three mossy stones; tapping a stone sends rings of
/// grooves out round it; a two-finger swipe sweeps the sand level again. The sand's shape is a height
/// map drawn into a strip at a time and lit every frame by a Metal shader.
struct ZenSandGarden: View {
    /// Rakes a spiral and a field of waves, rings the three stones, then smooths the sand, on a loop.
    var autoPlay = false
    /// The line of hints along the bottom edge.
    var hint = ["Drag to rake", "Tap a stone", "Two fingers to smooth"]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = ZenSandGardenEngine()

    private typealias M = ZenSandGardenMetrics
    private static let ink: UInt32 = 0x5a4a3a

    var body: some View {
        // The block draws under the system bars and keeps its hints clear of them itself.
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
        .background(Color(hex: 0xd9cdb8).ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let auto = autoPlay && !reduceMotion
        return ZStack(alignment: .bottom) {
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, size: size, autoPlay: auto, reducedMotion: reduceMotion)
                Canvas { context, canvasSize in
                    draw(frame, in: &context, size: canvasSize)
                }
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .gesture(
                // One recogniser stands in for the race of a rake pan (after 4 pt) and a stone tap.
                DragGesture(minimumDistance: 0)
                    .onChanged { engine.touchChanged($0) }
                    .onEnded { engine.touchEnded($0) }
            )
            .background(
                ZenSandGardenTwoFingers(
                    began: { engine.twoFingersBegan() },
                    moved: { engine.twoFingersMoved($0) },
                    ended: { engine.twoFingersEnded() }
                )
            )
            .accessibilityElement()
            .accessibilityLabel("Zen sand garden")

            hints
                .padding(.horizontal, 16)
                .padding(.bottom, insets.bottom + 14)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
    }

    private var hints: some View {
        HStack(spacing: 10) {
            ForEach(hint.indices, id: \.self) { i in
                if i > 0 {
                    Circle()
                        .fill(Color(hex: Self.ink))
                        .frame(width: 3, height: 3)
                        .opacity(0.35)
                }
                Text(hint[i])
                    .font(.system(size: 12, weight: .medium))
                    .kerning(0.2)
                    .foregroundStyle(Color(hex: Self.ink))
                    .opacity(0.62)
                    .lineLimit(1)
            }
        }
    }

    private func draw(_ frame: ZenSandGardenFrame, in context: inout GraphicsContext, size: CGSize) {
        guard let heightMap = frame.heightMap, size.width > 0 else { return }

        // The light of the moment.
        let phase = frame.elapsed / M.loop * .pi * 2
        let day = sin(phase)
        let bearing = (M.sunBearing + M.sunSwing * day) * .pi / 180
        let rise = (M.sunLow + (M.sunHigh - M.sunLow) * (0.5 + 0.5 * cos(phase))) * .pi / 180
        let sx = cos(rise) * cos(bearing)
        let sy = cos(rise) * sin(bearing)
        let sz = sin(rise)
        let warm = 0.5 + 0.5 * day
        let sunColor = (0..<3).map { M.sunMorning[$0] + (M.sunEvening[$0] - M.sunMorning[$0]) * warm }
        var stones: [Float] = []
        for s in M.stones {
            let c = s.centre(size)
            stones += [c.x, c.y, s.r, s.h, s.a2, s.p2, s.a3, s.p3, s.a5, s.p5].map { Float($0) }
            stones += [s.tint[0], s.tint[1], s.tint[2], s.moss].map { Float($0) }
        }
        let rect = CGRect(origin: .zero, size: size)
        let shader = ShaderLibrary.zenSandGardenLight(
            .image(heightMap),
            .float2(size),
            .float3(sx, sy, sz),
            .float3(sunColor[0], sunColor[1], sunColor[2]),
            .float3(M.sky[0], M.sky[1], M.sky[2]),
            .floatArray(stones),
            .float(frame.elapsed)
        )
        context.fill(Path(rect), with: .shader(shader, bounds: rect))

        // The rake, standing a little above the sand, its shadow cast away from the sun.
        let alpha = frame.rakeAlpha
        guard alpha > 0.01 else { return }
        let reach = M.rakeLift / max(0.2, tan(rise))
        let ox = cos(bearing) * reach
        let oy = sin(bearing) * reach
        drawRake(in: context, at: CGPoint(x: frame.head.x - ox, y: frame.head.y - oy), angle: frame.heading, alpha: alpha, shadow: true)
        drawRake(in: context, at: frame.head, angle: frame.heading, alpha: alpha, shadow: false)
    }

    /// The wooden rake at a point heading `angle`, or its blurred shadow.
    private func drawRake(in parent: GraphicsContext, at p: CGPoint, angle: Double, alpha: Double, shadow: Bool) {
        var context = parent
        context.translateBy(x: p.x, y: p.y)
        context.rotate(by: .radians(angle))
        // The handle trails behind the head; the bar lies across the heading.
        let handle = Path(roundedRect: CGRect(x: -M.handleLength, y: -M.handleThick / 2, width: M.handleLength - 2, height: M.handleThick), cornerSize: CGSize(width: 3, height: 3))
        let bar = Path(roundedRect: CGRect(x: -M.barThick / 2, y: -M.barLength / 2, width: M.barThick, height: M.barLength), cornerSize: CGSize(width: 3, height: 3))
        if shadow {
            // Skia blur sigmas of 3.5 and 3 pt; a SwiftUI blur radius spreads about half as far.
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 7))
                layer.fill(handle, with: .color(.rgba(40, 26, 12, 0.3 * alpha)))
            }
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 6))
                layer.fill(bar, with: .color(.rgba(40, 26, 12, 0.34 * alpha)))
            }
            return
        }
        context.fill(handle, with: .linearGradient(
            Gradient(stops: [
                .init(color: .rgba(196, 160, 112, alpha), location: 0),
                .init(color: .rgba(150, 108, 66, alpha), location: 0.55),
                .init(color: .rgba(104, 70, 40, alpha), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: -M.handleThick / 2),
            endPoint: CGPoint(x: 0, y: M.handleThick / 2)
        ))
        context.fill(bar, with: .linearGradient(
            Gradient(stops: [
                .init(color: .rgba(112, 76, 44, alpha), location: 0),
                .init(color: .rgba(206, 168, 118, alpha), location: 0.3),
                .init(color: .rgba(150, 106, 62, alpha), location: 0.7),
                .init(color: .rgba(92, 60, 34, alpha), location: 1),
            ]),
            startPoint: CGPoint(x: -M.barThick / 2, y: 0),
            endPoint: CGPoint(x: M.barThick / 2, y: 0)
        ))
        context.fill(Path(ellipseIn: CGRect(x: -1.6, y: -1.6, width: 3.2, height: 3.2)), with: .color(.rgba(70, 46, 26, 0.8 * alpha)))
    }
}

/// Watches for a second finger without ever taking the touches: a recogniser on the window that stays
/// `.possible` (so it cancels nothing, and the gallery's back swipe cannot cancel it) and reports the
/// two-finger centroid's travel.
private struct ZenSandGardenTwoFingers: UIViewRepresentable {
    var began: () -> Void
    var moved: (CGSize) -> Void
    var ended: () -> Void

    func makeUIView(context: Context) -> Host {
        let view = Host()
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        view.update(self)
        return view
    }

    func updateUIView(_ view: Host, context: Context) { view.update(self) }

    static func dismantleUIView(_ view: Host, coordinator: ()) { view.detach() }

    final class Host: UIView {
        private let watcher = Watcher()

        func update(_ owner: ZenSandGardenTwoFingers) {
            watcher.began = owner.began
            watcher.moved = owner.moved
            watcher.ended = owner.ended
            watcher.area = self
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            detach()
            window?.addGestureRecognizer(watcher)
        }

        func detach() { watcher.view?.removeGestureRecognizer(watcher) }
    }

    final class Watcher: UIGestureRecognizer, UIGestureRecognizerDelegate {
        var began: (() -> Void)?
        var moved: ((CGSize) -> Void)?
        var ended: (() -> Void)?
        weak var area: UIView?
        private var touches = Set<UITouch>()
        private var start: CGPoint?

        override init(target: Any?, action: Selector?) {
            super.init(target: target, action: action)
            cancelsTouchesInView = false
            delaysTouchesBegan = false
            delaysTouchesEnded = false
            delegate = self
        }

        convenience init() { self.init(target: nil, action: nil) }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        private func centroid() -> CGPoint? {
            guard let area, touches.count >= 2 else { return nil }
            let points = touches.prefix(2).map { $0.location(in: area) }
            return CGPoint(x: (points[0].x + points[1].x) / 2, y: (points[0].y + points[1].y) / 2)
        }

        override func touchesBegan(_ set: Set<UITouch>, with event: UIEvent) {
            guard let area else { return }
            for t in set where area.bounds.contains(t.location(in: area)) { touches.insert(t) }
            if start == nil, let c = centroid() {
                start = c
                began?()
            }
        }

        override func touchesMoved(_ set: Set<UITouch>, with event: UIEvent) {
            guard let s = start, let c = centroid() else { return }
            moved?(CGSize(width: c.x - s.x, height: c.y - s.y))
        }

        override func touchesEnded(_ set: Set<UITouch>, with event: UIEvent) { lift(set) }
        override func touchesCancelled(_ set: Set<UITouch>, with event: UIEvent) { lift(set) }

        private func lift(_ set: Set<UITouch>) {
            if start != nil, !touches.isDisjoint(with: set) {
                start = nil
                ended?()
            }
            touches.subtract(set)
            // Never recognised: fail once every finger is up, so UIKit resets it for the next touch.
            if touches.isEmpty { state = .failed }
        }

        override func reset() {
            touches.removeAll()
            start = nil
        }
    }
}
