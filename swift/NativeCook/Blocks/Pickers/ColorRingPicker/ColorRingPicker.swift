import SwiftUI

/// A colour picker laid out as a ring. Every colour is a small outlined circle on the ring, except
/// the chosen one: its place is empty and it sits in the middle as a large, thick ring. Tapping a
/// circle (or sliding a finger over it) swaps the two: the large ring shrinks back to its place while
/// the tapped one travels to the middle and grows.
struct ColorRingPicker: View {
    /// The colours, clockwise from 12 o'clock.
    var colors = ColorRingPicker.samples
    /// The colour chosen at first; `autoPlay` comes back to it at the end of every loop.
    var selection = 1
    var title = "Pick a color"
    var message = "Color coding a space makes it easier\nto spot when you need it."
    var action = "FINISH & SAVE"
    /// Picks one colour after another by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the colour that was picked.
    var onChange: ((Int) -> Void)? = nil
    /// Called with the index of the chosen colour when the button is tapped.
    var onFinish: ((Int) -> Void)? = nil

    static let samples: [Color] = [
        Color(hex: 0xe5383b), Color(hex: 0x3ddc97), Color(hex: 0x8e9196), Color(hex: 0xf06a8c),
        Color(hex: 0x1b1e22), Color(hex: 0x1b8db8), Color(hex: 0x1fa463), Color(hex: 0xf3c1ae),
        Color(hex: 0x2456e0), Color(hex: 0xc6f221), Color(hex: 0xf0743e), Color(hex: 0xa9a2d6),
        Color(hex: 0x5fa8a8), Color(hex: 0xb58a93), Color(hex: 0xf2e14c), Color(hex: 0x6a2fbf),
    ]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    @State private var selected = 0
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0
    /// A finger is on the ring.
    @State private var holding = false
    /// Counts the picks made by hand, for the haptic.
    @State private var ticks = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// Each colour's place: 0 in its slot on the ring … 1 in the middle, large.
        var lift: [Track] = []
        /// The swell of a circle that was just tapped.
        var swell: [Track] = []
        var press = Track(0)
    }

    private var auto: Bool { autoPlay && !reduceMotion && colors.count > 1 }
    private var rest: Int { min(max(selection, 0), colors.count - 1) }

    var body: some View {
        VStack(spacing: 0) {
            Text(title)
                .font(.system(size: 30, weight: .light, design: .serif))
                .foregroundStyle(.white)
                .frame(height: 38)
            Text(message)
                .font(.system(size: 15))
                .lineSpacing(3)
                .multilineTextAlignment(.center)
                .foregroundStyle(Color(hex: 0x8a8f98))
                .frame(height: 42)
                .padding(.top, 10)

            TimelineView(.animation) { timeline in
                let now = timeline.date
                let lifts = motion.lift.map { $0.value(at: now) }
                let swells = motion.swell.map { $0.value(at: now) }
                let chosen = selected
                Canvas { context, size in
                    draw(context, size: size, lifts: lifts, swells: swells, chosen: chosen)
                }
            }
            .frame(width: Metrics.box, height: Metrics.box)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !holding {
                            holding = true
                            touches += 1
                        }
                        if let hit = slot(at: value.location), hit != selected {
                            ticks += 1
                            select(hit)
                        }
                    }
                    .onEnded { _ in
                        holding = false
                        touches += 1
                    }
            )
            .padding(.top, 22)
            .accessibilityElement()
            .accessibilityLabel(title)
            .accessibilityValue("\(selected + 1) of \(colors.count)")
            .accessibilityAdjustableAction { direction in
                touches += 1
                select((selected + (direction == .increment ? 1 : colors.count - 1)) % colors.count)
            }

            TimelineView(.animation) { timeline in
                Text(action)
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.4)
                    .foregroundStyle(.white)
                    .frame(width: Metrics.buttonW, height: Metrics.buttonH)
                    .background(Capsule().fill(Color(hex: 0xf2431e)))
                    .scaleEffect(1 - 0.06 * motion.press.value(at: timeline.date))
            }
            .contentShape(Capsule())
            .onTapGesture {
                touches += 1
                ticks += 1
                if !reduceMotion { motion.press.retarget(1, .pulse) }
                onFinish?(selected)
            }
            .padding(.top, 24)
            .accessibilityAddTraits(.isButton)
        }
        .frame(width: Metrics.width)
        .sensoryFeedback(.selection, trigger: ticks)
        .onAppear { reset() }
        .onChange(of: colors.count) { reset() }
        .onChange(of: selection) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches, holding: holding)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
        let holding: Bool
    }

    // MARK: Drawing

    /// Centre of slot `i` in a box of the ring's size.
    private func place(_ i: Int) -> CGPoint {
        let angle = Double(i) / Double(max(colors.count, 1)) * tau - .pi / 2
        return CGPoint(x: Metrics.box / 2 + cos(angle) * Metrics.radius, y: Metrics.box / 2 + sin(angle) * Metrics.radius)
    }

    private func draw(_ context: GraphicsContext, size: CGSize, lifts: [Double], swells: [Double], chosen: Int) {
        guard lifts.count == colors.count else { return }
        let middle = CGPoint(x: size.width / 2, y: size.height / 2)
        // The chosen circle is drawn last, over the one it crosses on its way in.
        for i in colors.indices.sorted(by: { ($0 == chosen ? 2 : lifts[$0]) < ($1 == chosen ? 2 : lifts[$1]) }) {
            let t = lifts[i]
            let from = place(i)
            let center = CGPoint(x: mix(from.x, middle.x, t), y: mix(from.y, middle.y, t))
            let line = max(1.5, mix(Metrics.smallLine, Metrics.largeLine, t))
            // A tapped circle swells in place before it sets off.
            let across = max(8, mix(Metrics.small, Metrics.large, t)) * (1 + 0.25 * swells[i] * (1 - clamp01(t * 3)))
            let r = (across - line) / 2
            context.stroke(
                Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)),
                with: .color(colors[i]), lineWidth: line
            )
        }
    }

    /// The slot under a finger, if any. The chosen colour's slot is empty.
    private func slot(at point: CGPoint) -> Int? {
        var best: (index: Int, distance: Double)? = nil
        for i in colors.indices {
            let at = place(i)
            let distance = hypot(point.x - at.x, point.y - at.y)
            if distance < Metrics.reach, distance < (best?.distance ?? .infinity) { best = (i, distance) }
        }
        return best?.index
    }

    // MARK: Steps

    private func reset() {
        selected = rest
        motion.lift = colors.indices.map { Track($0 == rest ? 1 : 0) }
        motion.swell = colors.map { _ in Track(0) }
    }

    /// Picks colour `i`: it travels to the middle while the one that was there goes back to its slot.
    private func select(_ i: Int) {
        guard colors.indices.contains(i), i != selected, motion.lift.count == colors.count else { return }
        // Under reduced motion nothing travels: the two circles change places at once.
        motion.lift[selected].retarget(0, reduceMotion ? .jump : .spring(travelSpring))
        motion.lift[i].retarget(1, reduceMotion ? .jump : .spring(travelSpring))
        if !reduceMotion { motion.swell[i].retarget(1, .pulse) }
        selected = i
        onChange?(i)
    }

    /// The script a finger would follow: six picks on alternating sides of the ring, the last one
    /// back on the colour the loop started with.
    private func runLoop() async {
        guard auto, !holding else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to the first colour and start over.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                select(rest)
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for (n, turn) in Metrics.turns.enumerated() {
                    try await clock.sleep(until: start + .seconds(Metrics.firstAt + Double(n) * Metrics.stepTime))
                    select((rest + Int((turn * Double(colors.count)).rounded())) % colors.count)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// A number moving towards a target on a spring, sampled against the `TimelineView` clock.
private struct Track {
    enum Kind {
        case jump
        case spring(Spring)
        /// Up to the target in 80 ms, a 40 ms hold, back to 0 in 180 ms: a tap.
        case pulse
    }

    private var from: Double
    private var to: Double
    private var speed = 0.0
    private var start = Date.distantPast
    private var kind = Kind.jump

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return from }
        switch kind {
        case .jump: return to
        case .spring(let spring): return from + spring.value(target: to - from, initialVelocity: speed, time: t)
        case .pulse: return to * (t < 0.08 ? t / 0.08 : t < 0.12 ? 1 : max(0, 1 - (t - 0.12) / 0.18))
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
    }

    /// Heads for `target` from where it is now, keeping its speed.
    mutating func retarget(_ target: Double, _ kind: Kind) {
        let now = Date.now
        let value = value(at: now)
        speed = velocity(at: now)
        from = value
        if case .pulse = kind { from = 0 }
        to = target
        start = now
        self.kind = kind
    }
}

private enum Metrics {
    static let width = 340.0
    /// The square the ring is drawn in; the slots sit on a circle of `radius` around its centre.
    static let box = 240.0
    static let radius = 98.0
    // A circle's width across and its line, on the ring and in the middle.
    static let small = 26.0
    static let smallLine = 2.5
    static let large = 100.0
    static let largeLine = 10.0
    /// A finger this close to a slot's centre picks it.
    static let reach = 22.0
    static let buttonW = 146.0
    static let buttonH = 36.0

    // autoPlay (seconds): the first pick, then one every `stepTime`.
    static let loop = 8.0
    static let firstAt = 1.0
    static let stepTime = 1.15
    /// Where each pick is, in turns of the ring clockwise from the resting colour.
    static let turns = [0.125, 0.5, 0.25, 0.875, 0.4375, 0]
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

private let travelSpring = Spring(mass: 1, stiffness: 220, damping: 21)

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

#Preview {
    ZStack {
        Color(hex: 0x121417).ignoresSafeArea()
        ColorRingPicker(autoPlay: true)
    }
}
