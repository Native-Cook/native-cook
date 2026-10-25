import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/*
 Piano Key Button — "Keys", a music-lessons app whose three calls to action are real piano keys.

 Look: ebony lacquer, a lesson card with a small stave (C, E, G and the C + G chord), a fallboard
 rail with a gold pinstripe, a dark recess where the hammers and their brass strings sit, a
 burgundy felt strip, then three big ivory keys (lengthwise grain, side shading, a front lip) with
 two black keys between them. Each key is a CTA: "Warm up", "Lesson 14", "Free play".

 Press: the key pivots down about its far edge (it foreshortens 5 pt, narrows 3 pt a side, its
 lip shrinks, it darkens toward the front and its neighbours shade its sides), the felt's shadow
 tightens, the hammer behind flicks up 14 pt and falls back, its three strings brighten while they
 ring, three rings of the key's colour spread from it (1.3 s, 0.12 s apart), the label rolls up
 into the note name (C / E / G), the note on the stave lights and the streak chip pulses. Two
 fingers on C and G play the chord, which completes the exercise: the streak rolls 12 → 13 days.

 Motion: the key is a spring (ω 40 rad/s, ζ 0.62); autoPlay is the analytic response of that
 spring to the script (PianoKeyButtonMotion.swift), so every frame is a function of the loop time.
 A touch takes over at once; 2 s after the last finger lifts the screen eases back to rest (0.6 s)
 and the loop starts again from t = 0. Reduced motion: no loop, no rings or hammer flicks; keys
 still press.
*/
struct PianoKeyButton: View {
    /// Plays the 8 s practice loop (C, E, G, then the chord) until touched.
    var autoPlay = false

    static let loop = PianoKeyButtonMotion.loop
    private typealias M = PianoKeyButtonMotion
    private typealias Art = PianoKeyButtonArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = PianoKeyButtonEngine()
    /// True while a finger plays or the screen eases back (the timeline runs even without autoPlay).
    @State private var handActive = false
    @State private var idleTask: Task<Void, Never>? = nil
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var success = UINotificationFeedbackGenerator()

    static let ink = Color(hex: 0xF3E7D6)
    static let mute = Color.rgba(243, 231, 214, 0.62)
    static let faint = Color.rgba(243, 231, 214, 0.42)
    static let amber = Color(hex: 0xF2B35B)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let s = Art.Scene(w: proxy.size.width + insets.leading + insets.trailing,
                              h: proxy.size.height + insets.top + insets.bottom,
                              top: insets.top, bottom: insets.bottom)
            stage(s, top: insets.top)
                .frame(width: s.w, height: s.h, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x15100D).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            light.prepare()
            success.prepare()
        }
        .onDisappear { idleTask?.cancel() }
    }

    private func stage(_ s: Art.Scene, top: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            PianoKeyButtonBackground(scene: s).equatable()

            TimelineView(.animation(paused: !auto && !handActive)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let pose = framePose(now, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in
                        Art.notes(ctx, s, pose)
                        var rc = ctx
                        rc.clip(to: Path(CGRect(x: 0, y: s.recessTop, width: s.w, height: s.feltY - s.recessTop)))
                        Art.hammers(rc, s, pose)
                        Art.keyShadows(ctx, s, pose)
                        for i in 0..<3 {
                            let l = Art.keyLabels[i]
                            let labels = [
                                ctx.resolve(Text(l.title).font(.system(size: 17, weight: .semibold)).foregroundStyle(Art.ink)),
                                ctx.resolve(Text(l.sub).font(.system(size: 13)).foregroundStyle(Art.inkSoft)),
                            ]
                            let note = ctx.resolve(Text(l.note).font(.custom("Georgia-Bold", size: 46)).foregroundStyle(Art.ink))
                            Art.key(ctx, s, i, pose.keys[i], labels: labels, note: note)
                        }
                    }
                    PianoKeyButtonOverlay(scene: s).equatable()
                    Canvas { ctx, _ in Art.rings(ctx, s, pose) }

                    header(s, top: top, pose: pose)
                }
                .frame(width: s.w, height: s.h, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            cardText(s)
            Text("Tap a key · Hold C and G together for the chord")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Self.faint)
                .multilineTextAlignment(.center)
                .frame(width: s.w - 48)
                .offset(x: 24, y: s.keyBottom + 28)
                .allowsHitTesting(false)

            PianoKeyButtonTouches(
                onDown: { id, p in touchDown(id, p, s, auto: auto) },
                onUp: { id in touchUp(id) }
            )
            .frame(width: s.w, height: s.h)
            .allowsHitTesting(false)
        }
        .frame(width: s.w, height: s.h, alignment: .topLeading)
        .clipped()
        .accessibilityElement()
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Three piano keys: Warm up, Lesson 14 and Free play. Press C and G together to finish the exercise.")
    }

    private func framePose(_ now: Double, auto: Bool, reduced: Bool) -> M.Pose {
        var p = engine.pose(at: now, auto: auto, reduced: reduced)
        if reduced {
            // No ripples or hammer flicks: the key still travels and its label still turns.
            for i in 0..<p.keys.count { p.keys[i].downAge = 99 }
        }
        return p
    }

    // MARK: Overlaid text

    private func header(_ s: Art.Scene, top: Double, pose: M.Pose) -> some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text("KEYS")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(3)
                    .foregroundStyle(Self.amber)
                Text("Daily practice")
                    .font(.system(size: 30, weight: .bold))
                    .kerning(-0.5)
                    .foregroundStyle(Self.ink)
                Text("Wednesday · 18 of 20 min")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Self.mute)
            }
            .layoutPriority(1)
            Spacer(minLength: 0)
            PianoKeyButtonChip(streak: pose.streak, pulse: pose.pulse)
                .padding(.top, 20)
        }
        .frame(width: s.w - 36, alignment: .topLeading)
        .offset(x: 20, y: top + 12)
    }

    private func cardText(_ s: Art.Scene) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("TODAY’S EXERCISE")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.6)
                .foregroundStyle(Self.amber)
            Text("C, E, G, then the chord")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Self.ink)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
            Text("Lesson 14 · 72 BPM")
                .font(.system(size: 13, weight: .medium).monospacedDigit())
                .foregroundStyle(Self.faint)
        }
        .frame(width: s.staffL - s.cardX - 32, alignment: .leading)
        .offset(x: s.cardX + 20, y: s.cardY + 20)
        .allowsHitTesting(false)
    }

    // MARK: Touch

    private func touchDown(_ id: Int, _ p: CGPoint, _ s: Art.Scene, auto: Bool) {
        guard let i = s.keyAt(p) else { return }
        let now = Date.now.timeIntervalSinceReferenceDate
        idleTask?.cancel()
        handActive = true
        engine.owner[id] = i
        if engine.press(i, at: now, auto: auto) {
            success.notificationOccurred(.success)
        } else {
            light.impactOccurred()
        }
    }

    private func touchUp(_ id: Int) {
        guard let i = engine.owner.removeValue(forKey: id) else { return }
        engine.release(i, at: Date.now.timeIntervalSinceReferenceDate)
        guard engine.owner.isEmpty else { return }
        // Once the screen is back at rest, the timeline may stop again (without autoPlay).
        idleTask?.cancel()
        idleTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.idleBeforeBack + M.backFor + 0.2))
            if !Task.isCancelled && engine.owner.isEmpty { handActive = false }
        }
    }
}

/// The background, drawn once.
private struct PianoKeyButtonBackground: View, Equatable {
    let scene: PianoKeyButtonArt.Scene
    var body: some View {
        Canvas { ctx, _ in PianoKeyButtonArt.background(ctx, scene) }
            .frame(width: scene.w, height: scene.h)
    }
}

/// The felt strip and the black keys, drawn once, over the moving ivory keys.
private struct PianoKeyButtonOverlay: View, Equatable {
    let scene: PianoKeyButtonArt.Scene
    var body: some View {
        Canvas { ctx, _ in PianoKeyButtonArt.overlay(ctx, scene) }
            .frame(width: scene.w, height: scene.h)
    }
}

/// The streak chip: a flame, a rolling units digit and "days"; it swells and glows with a pulse.
private struct PianoKeyButtonChip: View {
    let streak: Double
    let pulse: Double
    private static let digitH = 22.0

    var body: some View {
        HStack(spacing: 6) {
            PianoKeyButtonFlame()
                .fill(LinearGradient(stops: [
                    .init(color: Color(hex: 0xFFD58A), location: 0),
                    .init(color: Color(hex: 0xF2A24B), location: 0.55),
                    .init(color: Color(hex: 0xD9682F), location: 1),
                ], startPoint: .top, endPoint: .bottom))
                .frame(width: 12, height: 15)
            HStack(spacing: 0) {
                digit(1)
                VStack(spacing: 0) {
                    ForEach(0..<10, id: \.self) { digit($0) }
                }
                .offset(y: -(streak - 10) * Self.digitH)
                .frame(height: Self.digitH, alignment: .top)
                .clipped()
            }
            .frame(height: Self.digitH)
            Text("days")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(PianoKeyButton.mute)
        }
        .padding(.horizontal, 12)
        .frame(height: 36)
        .background {
            ZStack {
                Capsule().fill(Color(hex: 0x2A1F18))
                    .shadow(color: .black.opacity(0.3), radius: 10, y: 8)
                    .shadow(color: .black.opacity(0.45), radius: 1, y: 1)
                Capsule().strokeBorder(LinearGradient(colors: [.rgba(255, 236, 210, 0.12), .clear], startPoint: .top, endPoint: .center), lineWidth: 1)
                Capsule().fill(Color.rgba(242, 179, 91, 0.16))
                    .shadow(color: .rgba(242, 179, 91, 0.55), radius: 9)
                    .opacity(PianoKeyButtonMotion.clamp01(pulse))
                Capsule().strokeBorder(Color.rgba(255, 214, 160, 0.22), lineWidth: 0.5)
            }
        }
        .scaleEffect(1 + 0.08 * pulse)
    }

    private func digit(_ d: Int) -> some View {
        Text("\(d)")
            .font(.system(size: 17, weight: .bold).monospacedDigit())
            .foregroundStyle(PianoKeyButton.ink)
            .frame(height: Self.digitH)
    }
}

private struct PianoKeyButtonFlame: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 12, sy = rect.height / 15
        func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: rect.minX + x * sx, y: rect.minY + y * sy) }
        var path = Path()
        path.move(to: p(6.2, 0.6))
        path.addCurve(to: p(10.4, 6.8), control1: p(6.8, 3), control2: p(9.2, 4.4))
        path.addCurve(to: p(6, 14.4), control1: p(11.9, 9.9), control2: p(10.1, 14.2))
        path.addCurve(to: p(1.1, 8.4), control1: p(2.2, 14.6), control2: p(0.2, 11.4))
        path.addCurve(to: p(3.2, 4.4), control1: p(1.6, 6.8), control2: p(2.8, 5.9))
        path.addCurve(to: p(4.1, 7.4), control1: p(4, 5.3), control2: p(4.2, 6.4))
        path.addCurve(to: p(6.2, 0.6), control1: p(5.6, 6), control2: p(6.6, 3.4))
        path.closeSubpath()
        return path
    }
}

/// Every finger on the block, from the moment it lands, so two fingers can play the chord. It only
/// listens: the recogniser sits on the window and never recognises anything, so the gallery's back
/// swipe still works beside it.
private struct PianoKeyButtonTouches: UIViewRepresentable {
    let onDown: (Int, CGPoint) -> Void
    let onUp: (Int) -> Void

    func makeUIView(context: Context) -> Probe {
        let probe = Probe()
        probe.onDown = onDown
        probe.onUp = onUp
        return probe
    }

    func updateUIView(_ probe: Probe, context: Context) {
        probe.onDown = onDown
        probe.onUp = onUp
    }

    final class Probe: UIView, UIGestureRecognizerDelegate {
        var onDown: (Int, CGPoint) -> Void = { _, _ in }
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

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
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
