import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/// A shallow porcelain dish of black ferrofluid under studio light, drawn entirely by one Metal
/// shader. A finger is a magnet: the fluid heaps up under it and a crown of hexagonal spikes rises,
/// leaning toward it and trailing a drag; a second finger raises a second crown, a tap makes the
/// spikes flinch up, and lifting lets them melt back with a wobble.
struct FerrofluidBlob: View {
    /// Sweeps a magnet over the dish, pulses it, brings in a second one, and lets the fluid settle.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = FerrofluidBlobEngine()

    private typealias M = FerrofluidBlobMetrics

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
        let layout = FerrofluidBlobLayout(size: size)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .top) {
            M.background

            TimelineView(.animation) { timeline in
                let u = engine.step(to: timeline.date, layout: layout, auto: auto, reduced: reduced)
                Rectangle()
                    .colorEffect(ShaderLibrary.ferrofluidBlob(
                        .float2(layout.cx, layout.cy),
                        .float2(max(1, size.width), max(1, size.height)),
                        .float(layout.dishR),
                        .float2(M.sinE, M.cosE),
                        .float4(u.magA.0, u.magA.1, u.magA.2, u.magA.3),
                        .float4(u.magB.0, u.magB.1, u.magB.2, u.magB.3),
                        .float4(u.lean.0, u.lean.1, u.lean.2, u.lean.3)
                    ))
                    .frame(width: size.width, height: size.height)
            }

            FerrofluidBlobTouches(
                onDown: { id, p in engine.touchDown(id, p, layout: layout, reduced: reduced) },
                onMove: { id, p in engine.touchMove(id, p, layout: layout) },
                onUp: { id in engine.touchUp(id) }
            )
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)

            VStack(alignment: .leading, spacing: 5) {
                Text("FERROFLUID")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(3)
                    .foregroundStyle(M.ink)
                Text("Magnetic study in a porcelain dish")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(M.inkSoft)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.top, insets.top + 22)
            .allowsHitTesting(false)

            Text("Hold to raise · Two fingers, two crowns · Tap to pulse")
                .font(.system(size: 12, weight: .medium))
                .kerning(0.3)
                .multilineTextAlignment(.center)
                .foregroundStyle(M.inkSoft)
                .padding(.horizontal, 24)
                .frame(width: size.width, height: size.height - insets.bottom - 26, alignment: .bottom)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .clipped()
        .accessibilityElement()
        .accessibilityLabel("A dish of ferrofluid. Touch to raise spikes, use two fingers for two crowns, tap to pulse.")
    }
}

enum FerrofluidBlobMetrics {
    // The camera looks down at the table from cameraElevation degrees above the horizon, so the
    // round dish reads as an ellipse and the spikes stand up off the screen plane.
    static let cameraElevation = 38.0
    static let sinE = sin(cameraElevation * .pi / 180)
    static let cosE = cos(cameraElevation * .pi / 180)
    // The dish: its radius on the table (a share of the width, at most dishMax pt), and where its
    // centre sits on the screen (a share of the height).
    static let dishShare = 0.47
    static let dishMax = 215.0
    static let dishAt = 0.56
    // The fluid's resting level in pt, used to land a finger on its surface.
    static let level = 6.0
    // After a touch the loop waits this long, then starts over from rest.
    static let userHold = 3.5

    static let ink = Color(red: 0xE9 / 255, green: 0xE4 / 255, blue: 0xDA / 255)
    static let inkSoft = ink.opacity(0.5)
    static let background = Color(red: 0x0B / 255, green: 0x0B / 255, blue: 0x0C / 255)
}

struct FerrofluidBlobLayout {
    let cx: Double
    let cy: Double
    let dishR: Double

    init(size: CGSize) {
        cx = size.width / 2
        cy = size.height * FerrofluidBlobMetrics.dishAt
        dishR = min(size.width * FerrofluidBlobMetrics.dishShare, FerrofluidBlobMetrics.dishMax)
    }

    /// A screen point, landed on the fluid's surface, in ground coordinates (dish centre = 0).
    func toGround(_ p: CGPoint) -> (Double, Double) {
        let m = FerrofluidBlobMetrics.self
        return (p.x - cx, (p.y - cy + m.level * m.cosE) / m.sinE)
    }
}

/// What the shader reads each frame.
struct FerrofluidBlobUniforms {
    var magA: (Double, Double, Double, Double) = (0, 0, 0, 0)
    var magB: (Double, Double, Double, Double) = (0, 0, 0, 0)
    var lean: (Double, Double, Double, Double) = (0, 0, 0, 0)
}

/// The simulation and the autoPlay clock, stepped once a frame like the RN frame callback.
@MainActor
final class FerrofluidBlobEngine {
    private typealias T = FerrofluidBlobTuning

    private var sim: FerrofluidBlobSim?
    private var last: Date?
    private var clock = 0.0
    private var hold = 0.0
    private var wasAuto: Bool?
    private let rise = UIImpactFeedbackGenerator(style: .soft)
    private let pulse = UIImpactFeedbackGenerator(style: .rigid)

    func step(to date: Date, layout: FerrofluidBlobLayout, auto: Bool, reduced: Bool) -> FerrofluidBlobUniforms {
        if wasAuto != auto {
            wasAuto = auto
            clock = 0
            hold = 0
        }
        // The script and the simulation share one clock, capped so a slow frame cannot skip ahead.
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), T.maxFrame)
        let s = sim ?? FerrofluidBlobSim(reduced: reduced)
        sim = s
        if layout.dishR > 0 && dt > 0 {
            // A finger resting on the dish keeps the script away.
            if s.userHolding { hold = FerrofluidBlobMetrics.userHold }
            if hold > 0 {
                hold -= dt
                if hold <= 0 { clock = 0 }
            } else if auto {
                let before = clock
                let now = (before + dt).truncatingRemainder(dividingBy: T.loop)
                clock = now
                s.script(before: before, now: now, radius: layout.dishR)
            }
            s.step(dt)
            if s.events != 0 {
                if s.eventsByUser { feel(s.events) }
                s.events = 0
                s.eventsByUser = false
            }
        }
        let a = s.m[0]
        let b = s.m[1]
        return FerrofluidBlobUniforms(
            magA: (a.x, a.y, a.field, a.spike),
            magB: (b.x, b.y, b.field, b.spike),
            lean: (a.leanX, a.leanY, b.leanX, b.leanY)
        )
    }

    private func feel(_ events: Int) {
        if events & T.eventPulse != 0 {
            pulse.impactOccurred()
        } else if events & T.eventRise != 0 {
            rise.impactOccurred()
        }
    }

    private func touched(reduced: Bool) -> FerrofluidBlobSim {
        hold = FerrofluidBlobMetrics.userHold
        let s = sim ?? FerrofluidBlobSim(reduced: reduced)
        sim = s
        s.cancelScripted()
        return s
    }

    func touchDown(_ id: Int, _ p: CGPoint, layout: FerrofluidBlobLayout, reduced: Bool) {
        let s = touched(reduced: reduced)
        let (gx, gy) = layout.toGround(p)
        s.touchDown(id, gx, gy, scripted: false, power: 1)
    }

    func touchMove(_ id: Int, _ p: CGPoint, layout: FerrofluidBlobLayout) {
        guard let s = sim else { return }
        hold = FerrofluidBlobMetrics.userHold
        let (gx, gy) = layout.toGround(p)
        s.touchMove(id, gx, gy, power: 1)
    }

    func touchUp(_ id: Int) {
        sim?.touchUp(id)
    }
}

/// Every finger on the block, from the moment it lands, so the field rises at once and a second
/// finger raises a second crown. It only listens: the recogniser sits on the window and never
/// recognises anything, so the gallery's back swipe still works beside it.
private struct FerrofluidBlobTouches: UIViewRepresentable {
    let onDown: (Int, CGPoint) -> Void
    let onMove: (Int, CGPoint) -> Void
    let onUp: (Int) -> Void

    func makeUIView(context: Context) -> Probe {
        let probe = Probe()
        update(probe)
        return probe
    }

    func updateUIView(_ probe: Probe, context: Context) {
        update(probe)
    }

    private func update(_ probe: Probe) {
        probe.onDown = onDown
        probe.onMove = onMove
        probe.onUp = onUp
    }

    final class Probe: UIView, UIGestureRecognizerDelegate {
        var onDown: (Int, CGPoint) -> Void = { _, _ in }
        var onMove: (Int, CGPoint) -> Void = { _, _ in }
        var onUp: (Int) -> Void = { _ in }
        private var listener: Listener?

        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
            backgroundColor = .clear
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if let listener { listener.view?.removeGestureRecognizer(listener) }
            listener = nil
            guard let window else { return }
            let listener = Listener(probe: self)
            listener.cancelsTouchesInView = false
            listener.delaysTouchesBegan = false
            listener.delaysTouchesEnded = false
            listener.delegate = self
            window.addGestureRecognizer(listener)
            self.listener = listener
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }
    }

    final class Listener: UIGestureRecognizer {
        private weak var probe: Probe?
        private var ids: [ObjectIdentifier: Int] = [:]
        private var nextID = 1

        init(probe: Probe) {
            self.probe = probe
            super.init(target: nil, action: nil)
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            guard let probe else { return }
            for touch in touches {
                let point = touch.location(in: probe)
                guard probe.bounds.contains(point) else { continue }
                let id = nextID
                nextID += 1
                ids[ObjectIdentifier(touch)] = id
                probe.onDown(id, point)
            }
        }

        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
            guard let probe else { return }
            for touch in touches {
                guard let id = ids[ObjectIdentifier(touch)] else { continue }
                probe.onMove(id, touch.location(in: probe))
            }
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
            lift(touches, event)
        }

        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
            lift(touches, event)
        }

        private func lift(_ touches: Set<UITouch>, _ event: UIEvent) {
            for touch in touches {
                guard let id = ids.removeValue(forKey: ObjectIdentifier(touch)) else { continue }
                probe?.onUp(id)
            }
            // Once every finger is up the recogniser bows out, which resets it for the next touch.
            let down = event.allTouches?.contains { $0.phase != .ended && $0.phase != .cancelled } ?? false
            if !down { state = .failed }
        }

        override func reset() {
            super.reset()
            for id in ids.values { probe?.onUp(id) }
            ids.removeAll()
        }
    }
}
