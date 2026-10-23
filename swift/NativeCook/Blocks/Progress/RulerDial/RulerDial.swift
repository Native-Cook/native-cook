import SwiftUI

/// Black panel with a fan of ruler ticks under a white needle and the value above it. Drag sideways
/// to turn the fan: ticks swell and light up as they pass the needle, the needle bends with them and
/// springs back, the digits roll like an odometer, and every tick is a light haptic click.
struct RulerDial: View {
    /// Position on the dial, 0…1 of `max`. Leave nil to let the dial keep its own value.
    var progress: Double? = nil
    /// Sweeps the dial by itself (0 → 95 → 68 → 49 → 0) until it is dragged, and again a while after.
    var autoPlay = false
    var max = 100.0
    var label = "Dial"
    /// Called with the whole number under the needle while the user turns the dial.
    var onValueChange: (Int) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The value under the needle whenever no finger is on the dial.
    @State private var value = Tween(0)
    /// The value under the finger, and where the drag started; nil when no drag is in progress.
    @State private var dragged: Double?
    @State private var dragFrom = 0.0
    /// Set when a touch turned out to be a vertical scroll of the list rather than a turn of the dial.
    @State private var ignoring = false
    /// The whole number last clicked past by the user.
    @State private var tick = 0
    @State private var coastToken = 0
    @State private var autoRun = 0
    /// The demo sweep waits while the user turns the dial, and for a while after.
    @State private var autoPaused = false
    @State private var settling = false
    @State private var sim = DialSim()

    private var playing: Bool { autoPlay && progress == nil && !reduceMotion && !autoPaused }

    private func current(at date: Date) -> Double {
        dragged ?? value.value(at: date)
    }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let now = current(at: date)
            Canvas { context, _ in
                // The needle's spring and the ticks' swell are integrated here, once per frame.
                let motion = reduceMotion ? (heat: 0.0, lean: 0.0) : sim.step(value: now, at: date)
                draw(context, value: now, heat: motion.heat, lean: motion.lean)
            }
            .padding(-Metrics.bleed)
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        // Simultaneous, so a vertical drag that starts on the dial still scrolls the list around it.
        .simultaneousGesture(turn, including: progress == nil ? .all : .subviews)
        .sensoryFeedback(.selection, trigger: tick)
        .onChange(of: tick) { _, tick in onValueChange(tick) }
        .accessibilityElement()
        .accessibilityLabel(label)
        .accessibilityValue("\(Int(current(at: .now).rounded()))")
        .accessibilityAdjustableAction { direction in
            guard progress == nil else { return }
            let to = min(max, Swift.max(0, current(at: .now).rounded() + (direction == .increment ? 1 : -1)))
            glide(to: to, duration: 0.2, curve: Ease.out)
            tick = Int(to)
        }
        .onChange(of: progress, initial: true) { old, progress in
            guard let progress else { return }
            // The first call only reports the starting value: show it without a run-up.
            let first = old == progress
            glide(to: min(Swift.max(progress, 0), 1) * max, duration: first ? 0 : reduceMotion ? 0.2 : 0.7, curve: Ease.out)
        }
        // The demo sweep: from wherever the dial is to each stop in turn, with a rest at each.
        .task(id: "\(playing) \(autoRun)") {
            guard playing else { return }
            while !Task.isCancelled {
                for stop in Metrics.autoStops {
                    try? await Task.sleep(for: .seconds(Metrics.autoHold))
                    if Task.isCancelled { return }
                    let to = min(max, stop)
                    let duration = 0.7 + abs(to - value.value(at: .now)) * 0.022
                    glide(to: to, duration: duration, curve: Metrics.easeInOut)
                    try? await Task.sleep(for: .seconds(duration))
                    if Task.isCancelled { return }
                }
            }
        }
        // While the dial coasts after a flick, keep clicking for every tick it passes.
        .task(id: coastToken) {
            guard coastToken > 0 else { return }
            let end = Date.now.addingTimeInterval(value.duration)
            while !Task.isCancelled, Date.now < end {
                try? await Task.sleep(for: .milliseconds(16))
                let whole = Int(value.value(at: .now).rounded())
                if whole != tick { tick = whole }
            }
            if Task.isCancelled { return }
            settling = false
            // The demo picks up again a while after the dial was let go.
            try? await Task.sleep(for: .seconds(Metrics.autoResume))
            if !Task.isCancelled {
                autoPaused = false
                autoRun += 1
            }
        }
    }

    private func glide(to target: Double, duration: Double, curve: UnitCurve) {
        value.retarget(target, duration: duration, curve: curve)
        guard reduceMotion else { return }
        settling = true
        Task {
            try? await Task.sleep(for: .seconds(duration + 0.05))
            settling = false
        }
    }

    /// A sideways drag turns the dial; lifting lets it coast to the nearest tick.
    private var turn: some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { drag in
                if ignoring { return }
                if dragged == nil {
                    if abs(drag.translation.height) > abs(drag.translation.width) {
                        ignoring = true
                        return
                    }
                    // Take over from the demo sweep, wherever it was.
                    dragFrom = value.value(at: .now)
                    autoPaused = true
                    coastToken = 0
                }
                // Dragging left brings higher numbers under the needle, like sliding a ruler.
                let now = min(max, Swift.max(0, dragFrom - drag.translation.width / Metrics.unitPt))
                dragged = now
                value = Tween(now)
                tick = Int(now.rounded())
            }
            .onEnded { drag in
                defer { ignoring = false }
                guard let now = dragged else { return }
                let speed = -drag.velocity.width / Metrics.unitPt
                let coast = min(40, Swift.max(-40, speed * 0.22))
                let to = min(max, Swift.max(0, (now + coast).rounded()))
                value = Tween(now)
                value.retarget(to, duration: 0.32 + abs(coast) * 0.014, curve: Ease.out)
                dragged = nil
                settling = true
                coastToken += 1
            }
    }

    private func draw(_ context: GraphicsContext, value: Double, heat: Double, lean: Double) {
        let o = Metrics.bleed
        let w = Metrics.width
        let h = Metrics.height
        let cx = o + w / 2
        let pivot = o + Metrics.pivotY
        let panel = Path(roundedRect: CGRect(x: o, y: o, width: w, height: h), cornerRadius: Metrics.radius)

        var shadow = context
        shadow.addFilter(.blur(radius: 14))
        shadow.fill(
            Path(roundedRect: CGRect(x: o + 8, y: o + 14, width: w - 16, height: h - 8), cornerRadius: Metrics.radius),
            with: .color(.black.opacity(0.3))
        )
        context.fill(panel, with: .color(.black))
        var inside = context
        inside.clip(to: panel)

        // Every tick in view, as one path; the ones passing the needle stand a little taller.
        var ticks = Path()
        let nearest = Int(value.rounded())
        for k in (nearest - Metrics.ticksEachSide)...(nearest + Metrics.ticksEachSide) {
            let steps = Double(k) - value
            let a = steps * Metrics.step
            let outer = Metrics.tickOuter + 5 * heat * exp(-steps * steps * 1.2)
            ticks.move(to: CGPoint(x: cx + Metrics.tickInner * sin(a), y: pivot - Metrics.tickInner * cos(a)))
            ticks.addLine(to: CGPoint(x: cx + outer * sin(a), y: pivot - outer * cos(a)))
        }
        // Blue on the left, grey under the needle, gold on the right; the colours stay put while the ticks pass.
        inside.stroke(
            ticks,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(110, 128, 196, 0), location: 0),
                    .init(color: .rgba(118, 136, 200, 0.7), location: 0.14),
                    .init(color: Color(hex: 0x96a4d2), location: 0.34),
                    .init(color: Color(hex: 0xb4b6bc), location: 0.5),
                    .init(color: Color(hex: 0xc0b996), location: 0.66),
                    .init(color: .rgba(196, 174, 96, 0.7), location: 0.86),
                    .init(color: .rgba(190, 166, 84, 0), location: 1),
                ]),
                startPoint: CGPoint(x: cx - 124, y: 0), endPoint: CGPoint(x: cx + 124, y: 0)
            ),
            lineWidth: Metrics.tickW
        )
        // The ticks nearest the needle catch its light while the dial turns.
        if heat > 0.01 {
            var lit = inside
            lit.opacity = heat
            lit.stroke(
                ticks,
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0), .white.opacity(0.85), .white.opacity(0)]),
                    startPoint: CGPoint(x: cx - 22, y: 0), endPoint: CGPoint(x: cx + 22, y: 0)
                ),
                lineWidth: Metrics.tickW
            )
        }

        // The needle is bent by the passing ticks about a point below the panel.
        var needle = inside
        needle.translateBy(x: cx, y: o + h + 30)
        needle.rotate(by: .radians(lean))
        needle.translateBy(x: -cx, y: -(o + h + 30))
        let top = o + Metrics.needleTop
        if heat > 0.01 {
            var glow = needle
            glow.opacity = 0.75 * heat
            glow.addFilter(.blur(radius: 7))
            glow.fill(capsule(CGRect(x: cx - 5, y: top - 3, width: 10, height: 40)), with: .color(.white))
        }
        needle.fill(
            capsule(CGRect(x: cx - Metrics.needleW / 2, y: top, width: Metrics.needleW, height: h - Metrics.needleTop + 4)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white, location: 0),
                    .init(color: .white, location: 0.62),
                    .init(color: .white.opacity(0.12), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: o + h)
            )
        )

        drawReadout(context, value: min(max, Swift.max(0, value)))
    }

    /// The read-out: one strip of digits per place, each rolling like an odometer wheel.
    private func drawReadout(_ context: GraphicsContext, value v: Double) {
        let o = Metrics.bleed
        let cx = o + Metrics.width / 2
        let cy = o + Metrics.valueY
        // Places come and go as the number gains or loses a digit, and the read-out stays centred.
        let places = 1 + seg(v, 9, 10) + seg(v, 99, 100)
        for place in 0..<3 {
            let unit = pow(10, Double(place))
            let opacity = place == 0 ? 1 : seg(v, unit - 1, unit - 0.4)
            guard opacity > 0 else { continue }
            // A wheel only turns while the wheel to its right goes from 9 to 0.
            let below = place == 0 ? 1 : seg(v.truncatingRemainder(dividingBy: unit) - (unit - 1), 0, 1)
            let digit = place == 0
                ? v.truncatingRemainder(dividingBy: 10)
                : (v / unit).rounded(.down).truncatingRemainder(dividingBy: 10) + below
            let whole = digit.rounded(.down)
            let turned = digit - whole
            let x = cx + (places / 2 - Double(place) - 0.5) * Metrics.digitW
            var wheel = context
            wheel.opacity = opacity
            wheel.clip(to: Path(CGRect(
                x: x - Metrics.digitW / 2, y: cy - Metrics.digitH / 2, width: Metrics.digitW, height: Metrics.digitH
            )))
            for (offset, number) in [(0.0, Int(whole) % 10), (1.0, (Int(whole) + 1) % 10)] {
                if offset == 1, turned == 0 { continue }
                wheel.draw(
                    Text(verbatim: "\(number)").font(.system(size: 16).monospacedDigit()).foregroundStyle(.white),
                    at: CGPoint(x: x, y: cy + (offset - turned) * Metrics.digitH), anchor: .center
                )
            }
        }
    }
}

/// The parts of the dial that have momentum: integrated frame by frame from the value's speed.
private final class DialSim {
    private var heat = 0.0
    private var lean = 0.0
    private var leanSpeed = 0.0
    private var last: (value: Double, date: Date)?

    func step(value: Double, at date: Date) -> (heat: Double, lean: Double) {
        guard let last else {
            self.last = (value, date)
            return (0, 0)
        }
        let elapsed = date.timeIntervalSince(last.date)
        guard elapsed > 0 else { return (heat, lean) }
        let dt = min(0.05, Swift.max(0.001, elapsed))
        let speed = (value - last.value) / dt
        self.last = (value, date)
        heat += (min(1, abs(speed) / 24) - heat) * 0.2
        // The passing ticks push the needle over; a spring pulls it back and lets it wobble.
        let pushed = Swift.max(-0.07, min(0.07, -speed * 0.0022))
        leanSpeed += ((pushed - lean) * 260 - leanSpeed * 13) * dt
        lean += leanSpeed * dt
        return (heat, lean)
    }
}

private enum Metrics {
    static let width = 330.0
    static let height = 124.0
    static let radius = 18.0
    /// The canvas overflows the panel by this much on every side to fit its shadow.
    static let bleed = 40.0
    /// The ticks fan out from a point well below the panel.
    static let pivotY = 211.0
    static let tickOuter = 150.6
    static let tickInner = 123.4
    static let tickW = 1.7
    /// One unit of value turns the fan by this much, which is one tick.
    static let step = 3.55 * Double.pi / 180
    /// Ticks further than this from the needle fall outside the panel.
    static let ticksEachSide = 15
    /// How far a finger travels per unit: the tick spacing under the needle.
    static let unitPt = tickOuter * step
    static let needleTop = 50.0
    static let needleW = 2.9
    static let valueY = 27.5
    static let digitW = 10.2
    static let digitH = 22.0
    static let autoStops = [95.0, 68, 49, 0]
    static let autoHold = 1.3
    static let autoResume = 3.0
    static let easeInOut = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1)
    )
}

private func seg(_ t: Double, _ from: Double, _ to: Double) -> Double {
    min(1, max(0, (t - from) / (to - from)))
}

#Preview {
    ZStack {
        Color(hex: 0xf5f5f6).ignoresSafeArea()
        RulerDial(autoPlay: true)
    }
}
