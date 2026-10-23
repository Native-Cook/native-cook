import SwiftUI

/// One piece of an icon's outline, in a box of ±24 points around its middle.
enum WeatherFanDialStroke: Equatable {
    case move(Double, Double)
    case line(Double, Double)
    /// An arc round (x, y): radius, the angle it starts at and the degrees it sweeps, clockwise from 3 o'clock.
    case arc(Double, Double, Double, Double, Double)
    case close
}

struct WeatherFanDialCondition: Equatable {
    var name: String
    /// Degrees shown next to the name.
    var temperature: Int
    /// The icon's outline.
    var icon: [WeatherFanDialStroke]

    static let samples = [
        WeatherFanDialCondition(name: "Clear", temperature: 24, icon: weatherFanDialMoon),
        WeatherFanDialCondition(name: "Cloudy", temperature: 21, icon: weatherFanDialCloud(-3, 3) + [.move(-0.4, -10.2), .arc(8, -7, 9, 201, 205)]),
        WeatherFanDialCondition(name: "Drizzle", temperature: 18, icon: weatherFanDialCloud(0, -5) + weatherFanDialDrops([(-8, 12), (0, 16), (8, 12)], 0.4, 0.8)),
        WeatherFanDialCondition(name: "Rain", temperature: 17, icon: weatherFanDialCloud(0, -6) + weatherFanDialDrops([(-7, 10), (1, 10), (9, 10)], -3, 8)),
        WeatherFanDialCondition(name: "Storm", temperature: 16, icon: weatherFanDialCloud(0, -7) + [.move(3, 7), .line(-3, 14), .line(3, 14), .line(-2, 22)]),
        WeatherFanDialCondition(name: "Snow", temperature: -1, icon: weatherFanDialCloud(0, -6) + [.move(-6.4, 13), .arc(-8, 13, 1.6, 0, 359.9), .move(1.6, 17), .arc(0, 17, 1.6, 0, 359.9), .move(9.6, 13), .arc(8, 13, 1.6, 0, 359.9)]),
        WeatherFanDialCondition(name: "Fog", temperature: 9, icon: weatherFanDialCloud(0, -8) + [.move(-13, 11), .line(13, 11), .move(-7, 18), .line(7, 18)]),
        WeatherFanDialCondition(name: "Windy", temperature: 19, icon: [
            .move(-16, -5), .line(3, -5), .arc(3, -10, 5, 90, -250),
            .move(-16, 3), .line(11, 3), .arc(11, -2, 5, 90, -250),
            .move(-16, 11), .line(1, 11), .arc(1, 16, 5, -90, 250),
        ]),
    ]
}

/// A weather dial sunk into a dark surface. The fan is the part in sight of a wheel of conditions
/// centred on the knob: dragging across it turns the wheel under a fixed pointer, a flick carries it
/// on, and it catches on the nearest segment with a spring. The segment under the pointer lights up
/// and the line below reads it out. Tapping a side segment brings it in; tapping the knob spins its
/// arrow and shakes the wheel.
struct WeatherFanDial: View {
    /// The conditions, clockwise round the wheel; it has no end, so the last one is left of the first.
    var conditions = WeatherFanDialCondition.samples
    /// Index of the condition under the pointer at first.
    var initial = 0
    /// The small line over the read-out.
    var lead = "It's"
    /// Turns the wheel one segment, flicks it two more, taps the knob and spins back by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the condition the wheel settles on.
    var onChange: ((Int) -> Void)? = nil
    /// Called when the knob is tapped.
    var onRefresh: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The wheel's position, in segments; it keeps counting past the last condition.
        var turn = Track(0)
        /// The segment the wheel is heading for.
        var target = 0
        /// The knob's arrow, in degrees, and the knob's dip under the finger.
        var arrow = Track(0)
        var knobPress = Track(0)
        /// The pointer's flicker as a segment catches.
        var flicker = Track(0)
        var caught = 0

        var dragging = false
        var grabTurn = 0.0
        var lastAngle = 0.0
        var swept = 0.0
        /// A finger turned the wheel last, so each segment is felt.
        var byHand = false
    }

    private var count: Int { max(1, conditions.count) }
    private var home: Int { min(max(initial, 0), count - 1) }
    private var auto: Bool { autoPlay && !reduceMotion && conditions.count > 3 }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let turn = motion.turn.value(at: now)
            let nearest = Int(turn.rounded())
            let _ = notice(nearest)

            Canvas { context, _ in
                guard !conditions.isEmpty else { return }
                drawFan(context, turn: turn, flicker: motion.flicker.value(at: now))
                drawKnob(context, arrow: motion.arrow.value(at: now), press: motion.knobPress.value(at: now))
                drawReadout(context, turn: turn)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .contentShape(Rectangle())
            .gesture(drag)
            .sensoryFeedback(trigger: nearest) { _, _ in motion.byHand ? .selection : nil }
            .accessibilityElement()
            .accessibilityLabel(lead)
            .accessibilityValue(conditions.isEmpty ? "" : readout(conditions[wrap(motion.target)]))
            .accessibilityAdjustableAction { direction in
                touch()
                step(direction == .increment ? 1 : -1)
            }
        }
        .onAppear { reset() }
        .onChange(of: conditions.count) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    private func readout(_ condition: WeatherFanDialCondition) -> String {
        "\(condition.name) & \(condition.temperature)°"
    }

    /// `index` brought into the list of conditions, which goes round.
    private func wrap(_ index: Int) -> Int {
        ((index % count) + count) % count
    }

    /// How many segments `index` is clockwise of the pointer, by the shorter way round.
    private func away(_ index: Int, _ turn: Double) -> Double {
        let n = Double(count)
        var d = (Double(index) - turn).truncatingRemainder(dividingBy: n)
        if d > n / 2 { d -= n }
        if d < -n / 2 { d += n }
        return d
    }

    // MARK: Drawing

    /// The point `radius` from the knob's centre, `degrees` clockwise of straight up.
    private func point(_ degrees: Double, _ radius: Double) -> CGPoint {
        let angle = degrees * .pi / 180
        return CGPoint(x: Metrics.knobX + radius * sin(angle), y: Metrics.knobY - radius * cos(angle))
    }

    /// The fan's outline: a sector of the wheel with a round bite for the knob.
    private var fan: Path {
        var path = Path()
        let center = CGPoint(x: Metrics.knobX, y: Metrics.knobY)
        path.addArc(center: center, radius: Metrics.radius, startAngle: .degrees(-90 - Metrics.spread), endAngle: .degrees(-90 + Metrics.spread), clockwise: false)
        path.addArc(center: center, radius: Metrics.bite, startAngle: .degrees(-90 + Metrics.spread), endAngle: .degrees(-90 - Metrics.spread), clockwise: true)
        path.closeSubpath()
        return path
    }

    private func drawFan(_ context: GraphicsContext, turn: Double, flicker: Double) {
        let outline = fan
        context.fill(outline, with: .color(Color(hex: 0x1d1f25)))

        var inside = context
        inside.clip(to: outline)

        // The light that falls from the pointer onto the segment under it.
        let top = point(0, Metrics.radius - 6)
        let seated = 1 - min(1, abs(turn - turn.rounded()) * 2)
        inside.fill(
            Path(ellipseIn: CGRect(x: top.x - Metrics.glow, y: top.y - Metrics.glow, width: Metrics.glow * 2, height: Metrics.glow * 2)),
            with: .radialGradient(
                Gradient(colors: [mustard.opacity((0.2 + 0.14 * seated) * (1 + 0.5 * flicker)), mustard.opacity(0)]),
                center: top, startRadius: 0, endRadius: Metrics.glow
            )
        )

        for i in conditions.indices {
            let d = away(i, turn)
            guard abs(d) < 2.2 else { continue }
            let degrees = d * Metrics.segment
            // The divider on this segment's clockwise side, with a faint lit edge beside it.
            var divider = Path()
            divider.move(to: point(degrees + Metrics.segment / 2, Metrics.bite))
            divider.addLine(to: point(degrees + Metrics.segment / 2, Metrics.radius))
            inside.stroke(divider, with: .color(Color(hex: 0x15161a)), lineWidth: 1.5)
            var edge = Path()
            edge.move(to: point(degrees + Metrics.segment / 2 + 0.5, Metrics.bite))
            edge.addLine(to: point(degrees + Metrics.segment / 2 + 0.5, Metrics.radius))
            inside.stroke(edge, with: .color(.white.opacity(0.03)), lineWidth: 1)

            // The icon stands along its radius, and lights as it comes under the pointer.
            let lit = smooth(1 - min(1, abs(d) * 2))
            var icon = inside
            let center = point(degrees, Metrics.iconRadius)
            icon.translateBy(x: center.x, y: center.y)
            icon.rotate(by: .degrees(degrees))
            icon.scaleBy(x: Metrics.iconScale * (1 + 0.06 * lit), y: Metrics.iconScale * (1 + 0.06 * lit))
            icon.stroke(
                weatherFanDialPath(conditions[i].icon),
                with: .color(ink(lit)),
                style: StrokeStyle(lineWidth: 3.2 + 0.8 * lit, lineCap: .round, lineJoin: .round)
            )
        }

        // The fan is sunk into the surface: its edges cast a shadow inward.
        var shade = inside
        shade.addFilter(.blur(radius: 7))
        shade.stroke(outline, with: .color(.black.opacity(0.6)), lineWidth: 10)
        context.stroke(
            outline,
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0), .white.opacity(0.1)]),
                startPoint: CGPoint(x: 0, y: Metrics.knobY - Metrics.radius * 0.6), endPoint: CGPoint(x: 0, y: Metrics.knobY)
            ),
            lineWidth: 1
        )

        // The pointer: a line from the triangle's tip down to the knob.
        var line = Path()
        line.move(to: CGPoint(x: Metrics.knobX, y: Metrics.knobY - Metrics.radius + 4))
        line.addLine(to: CGPoint(x: Metrics.knobX, y: Metrics.knobY - Metrics.bite))
        context.stroke(line, with: .color(mustard.opacity(0.75 + 0.25 * flicker)), lineWidth: 1 + 0.6 * flicker)
        var tip = Path()
        tip.move(to: CGPoint(x: Metrics.knobX - 12, y: Metrics.knobY - Metrics.radius - 6))
        tip.addLine(to: CGPoint(x: Metrics.knobX + 12, y: Metrics.knobY - Metrics.radius - 6))
        tip.addLine(to: CGPoint(x: Metrics.knobX, y: Metrics.knobY - Metrics.radius + 7))
        tip.closeSubpath()
        context.fill(tip, with: .color(mustard))
    }

    /// The raised round button the wheel turns about, with its refresh arrow.
    private func drawKnob(_ context: GraphicsContext, arrow: Double, press: Double) {
        let center = CGPoint(x: Metrics.knobX, y: Metrics.knobY)
        func disc(_ radius: Double) -> Path {
            Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
        }
        // A faint indigo light under it.
        context.fillEllipse(
            center: CGPoint(x: center.x, y: center.y + 16),
            radii: CGSize(width: 62, height: 46),
            gradient: Gradient(colors: [Color(hex: 0x4b3fd9, opacity: 0.3), Color(hex: 0x4b3fd9, opacity: 0)])
        )
        context.fill(disc(Metrics.bezel), with: .linearGradient(Gradient(colors: [Color(hex: 0x17181c), Color(hex: 0x222327)]), startPoint: CGPoint(x: 0, y: center.y - Metrics.bezel), endPoint: CGPoint(x: 0, y: center.y + Metrics.bezel)))

        var raised = context
        raised.translateBy(x: center.x, y: center.y)
        raised.scaleBy(x: 1 - 0.04 * press, y: 1 - 0.04 * press)
        raised.translateBy(x: -center.x, y: -center.y)
        var lifted = raised
        lifted.addFilter(.shadow(color: .black.opacity(0.55), radius: 5, x: 0, y: 4))
        lifted.fill(disc(Metrics.knob), with: .linearGradient(Gradient(colors: [Color(hex: 0x3a3b42), Color(hex: 0x24252a)]), startPoint: CGPoint(x: 0, y: center.y - Metrics.knob), endPoint: CGPoint(x: 0, y: center.y + Metrics.knob)))
        raised.stroke(disc(Metrics.knob - 0.5), with: .linearGradient(Gradient(colors: [.white.opacity(0.14), .white.opacity(0)]), startPoint: CGPoint(x: 0, y: center.y - Metrics.knob), endPoint: CGPoint(x: 0, y: center.y)), lineWidth: 1)

        var glyph = raised
        glyph.translateBy(x: center.x, y: center.y)
        glyph.rotate(by: .degrees(arrow))
        glyph.stroke(weatherFanDialPath(weatherFanDialRefresh), with: .color(Color(hex: 0x5a5c66)), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
    }

    /// The condition under the pointer in words. The lines ride with the wheel: the one that leaves
    /// slides away and fades as the next one slides in.
    private func drawReadout(_ context: GraphicsContext, turn: Double) {
        context.draw(
            Text(lead).font(.system(size: 17, weight: .ultraLight)).foregroundStyle(Color(hex: 0x82848f)),
            at: CGPoint(x: Metrics.width / 2, y: Metrics.leadY)
        )
        for i in conditions.indices {
            let d = away(i, turn)
            let shown = 1 - min(1, abs(d) * 2)
            guard shown > 0.01 else { continue }
            var line = context
            line.opacity = shown
            line.draw(
                Text(readout(conditions[i])).font(.system(size: 30, weight: .ultraLight)).foregroundStyle(Color(hex: 0xb0b2bc)),
                at: CGPoint(x: Metrics.width / 2, y: Metrics.readoutY + d * Metrics.readoutShift)
            )
        }
    }

    private var mustard: Color { Color(hex: 0xc9b458) }

    private func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }

    /// An icon's colour: dull grey, 0x3a3c44, in the dark and cream, 0xf4efdc, under the pointer.
    private func ink(_ lit: Double) -> Color {
        .rgba(58 + (244 - 58) * lit, 60 + (239 - 60) * lit, 68 + (220 - 68) * lit, 1)
    }

    // MARK: Turning

    private func reset() {
        motion.turn = Track(Double(home))
        motion.target = home
        motion.caught = home
    }

    private func touch() {
        motion.byHand = true
        touches += 1
    }

    /// A segment has come under the pointer: the pointer flickers like a detent catching.
    private func notice(_ nearest: Int) {
        guard nearest != motion.caught else { return }
        motion.caught = nearest
        if !reduceMotion { motion.flicker.retarget(1, .pulse(0.05, 0.25)) }
    }

    /// Turns the wheel `by` segments from where it is heading: one on the catch spring, more on a
    /// softer one.
    private func step(_ by: Int) {
        guard by != 0 else { return }
        head(for: motion.target + by, abs(by) == 1 ? .spring(catchSpring) : .spring(flickSpring))
    }

    private func head(for target: Int, _ kind: Track.Kind, velocity: Double? = nil) {
        motion.turn.retarget(Double(target), reduceMotion ? .jump : kind, velocity: velocity)
        guard target != motion.target else { return }
        motion.target = target
        if !conditions.isEmpty { onChange?(wrap(target)) }
    }

    /// The knob: its arrow goes once round and the wheel shakes in its catch.
    private func refresh() {
        if !reduceMotion {
            motion.knobPress.retarget(1, .pulse(0.08, 0.2))
            motion.arrow = Track(0)
            motion.arrow.retarget(360, .tween(Metrics.refreshTime, refreshCurve))
            motion.turn.retarget(Double(motion.target), .spring(catchSpring), velocity: Metrics.shiver)
        }
        onRefresh?()
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let angle = degrees(of: value.location)
                if !motion.dragging {
                    motion.dragging = true
                    motion.grabTurn = motion.turn.value(at: .now)
                    motion.lastAngle = degrees(of: value.startLocation)
                    motion.swept = 0
                    touch()
                }
                var delta = angle - motion.lastAngle
                if delta > 180 { delta -= 360 }
                if delta < -180 { delta += 360 }
                motion.lastAngle = angle
                guard hypot(value.translation.width, value.translation.height) >= Metrics.slop, inFan(value.startLocation) else { return }
                motion.swept += delta
                motion.turn = Track(motion.grabTurn - motion.swept / Metrics.segment)
            }
            .onEnded { value in
                motion.dragging = false
                touch()
                guard hypot(value.translation.width, value.translation.height) >= Metrics.slop else {
                    tap(at: value.location)
                    return
                }
                guard inFan(value.startLocation) else { return }
                let turn = motion.turn.value(at: .now)
                guard !reduceMotion else {
                    head(for: Int(turn.rounded()), .jump)
                    return
                }
                // How fast the finger was going round the knob, in segments a second.
                let x = value.location.x - Metrics.knobX
                let y = value.location.y - Metrics.knobY
                let reach = max(x * x + y * y, 50 * 50)
                let speed = -(x * value.velocity.height - y * value.velocity.width) / reach * 180 / .pi / Metrics.segment
                head(for: Int((turn + speed * Metrics.carry).rounded()), .spring(catchSpring), velocity: speed)
            }
    }

    private func tap(at location: CGPoint) {
        let reach = hypot(location.x - Metrics.knobX, location.y - Metrics.knobY)
        if reach <= Metrics.bezel + 4 {
            refresh()
        } else if inFan(location) {
            // A side segment comes under the pointer.
            let d = (degrees(of: location) / Metrics.segment + motion.turn.value(at: .now)).rounded()
            let by = Int(d) - motion.target
            if by != 0 { step(by) }
        }
    }

    /// Degrees clockwise of straight up at which `location` lies from the knob.
    private func degrees(of location: CGPoint) -> Double {
        atan2(location.x - Metrics.knobX, Metrics.knobY - location.y) * 180 / .pi
    }

    private func inFan(_ location: CGPoint) -> Bool {
        let reach = hypot(location.x - Metrics.knobX, location.y - Metrics.knobY)
        return reach > Metrics.bite && reach < Metrics.radius + 12 && abs(degrees(of: location)) < Metrics.spread + 4
    }

    // MARK: autoPlay

    /// The script a finger would follow: one segment on, a flick of two more, the knob, and back.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, turn back to the first condition by the shorter way and start over.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                guard !motion.dragging else { return }
                motion.byHand = false
                let n = Double(count)
                let around = ((motion.turn.value(at: .now) - Double(home)) / n).rounded()
                head(for: home + Int(around) * count, .tween(Metrics.backTime, backCurve))
                try await Task.sleep(for: .seconds(Metrics.backTime + 0.2))
                reset()
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                try await clock.sleep(until: start + .seconds(1.0))
                step(1)
                try await clock.sleep(until: start + .seconds(2.4))
                step(2)
                try await clock.sleep(until: start + .seconds(4.1))
                refresh()
                try await clock.sleep(until: start + .seconds(5.2))
                head(for: home, .tween(Metrics.backTime, backCurve))
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 320.0

    /// The knob's centre: the wheel turns about it.
    static let knobX = 170.0
    static let knobY = 192.0
    static let knob = 27.0
    static let bezel = 36.0

    /// The fan: its radius, the radius of the bite round the knob, and the degrees it opens to each side.
    static let radius = 176.0
    static let bite = 43.0
    static let spread = 62.0
    /// Degrees one condition takes on the wheel.
    static let segment = 45.0
    static let iconRadius = 112.0
    static let iconScale = 1.3
    static let glow = 120.0

    static let leadY = 262.0
    static let readoutY = 294.0
    /// How far a read-out line has slid when its segment is one place from the pointer.
    static let readoutShift = 28.0

    /// A touch that moves less than this is a tap.
    static let slop = 8.0
    /// A flick carries the wheel on by its speed times this many seconds.
    static let carry = 0.18
    /// The knob's tap: the arrow's turn, and the speed the wheel is knocked with, in segments a second.
    static let refreshTime = 0.6
    static let shiver = 2.4

    /// The spin back to the first condition.
    static let backTime = 1.0
    static let loop = 7.0
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

/// Overshoots a segment by about a ninth and settles in 0.45 s.
private let catchSpring = Spring(mass: 1, stiffness: 240, damping: 18)
private let flickSpring = Spring(mass: 1, stiffness: 110, damping: 14)
private let refreshCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.25, y: 1))
/// Ease-in-out that runs 4.5% past its target at 79% of the time and comes back.
private let backCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.5, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1.3))

/// A crescent open to the upper right.
private let weatherFanDialMoon: [WeatherFanDialStroke] = [
    .move(14.85, 2.07), .arc(0, 0, 15, 7.9, 254.1), .arc(7, -7, 12, 220.9, -171.8), .close,
]

/// The knob's arrow: three quarters of a circle, clockwise, with a head at its end.
private let weatherFanDialRefresh: [WeatherFanDialStroke] = [
    .move(9.77, -3.56), .arc(0, 0, 10.4, -20, 275),
    .move(-6.9, -5.8), .line(-2.7, -10.05), .line(-8.5, -11.6),
]

/// A cloud about 38 points wide whose middle is at (x, y).
private func weatherFanDialCloud(_ x: Double, _ y: Double) -> [WeatherFanDialStroke] {
    [
        .move(x - 12, y + 9), .line(x + 12, y + 9),
        .arc(x + 12, y + 2, 7, 90, -180),
        .arc(x + 1.8, y - 3.5, 10.31, -8.4, -168.5),
        .arc(x - 12, y + 2, 7, -60, -210),
        .close,
    ]
}

/// Short strokes under a cloud, each from a point down by (dx, dy).
private func weatherFanDialDrops(_ from: [(Double, Double)], _ dx: Double, _ dy: Double) -> [WeatherFanDialStroke] {
    from.flatMap { [WeatherFanDialStroke.move($0.0, $0.1), .line($0.0 + dx, $0.1 + dy)] }
}

private func weatherFanDialPath(_ strokes: [WeatherFanDialStroke]) -> Path {
    var path = Path()
    for stroke in strokes {
        switch stroke {
        case .move(let x, let y): path.move(to: CGPoint(x: x, y: y))
        case .line(let x, let y): path.addLine(to: CGPoint(x: x, y: y))
        case .arc(let x, let y, let radius, let start, let sweep):
            path.addArc(center: CGPoint(x: x, y: y), radius: radius, startAngle: .degrees(start), endAngle: .degrees(start + sweep), clockwise: sweep < 0)
        case .close: path.closeSubpath()
        }
    }
    return path
}

/// A number heading for a target, sampled against the `TimelineView` clock.
private struct Track {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
        /// Up to the target in the first time and back to 0 in the second.
        case pulse(Double, Double)
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
        case .tween(let duration, let curve): return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
        case .spring(let spring): return from + spring.value(target: to - from, initialVelocity: speed, time: t)
        case .pulse(let up, let down): return to * (t < up ? t / up : max(0, 1 - (t - up) / down))
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return 0 }
        switch kind {
        case .spring(let spring): return spring.velocity(target: to - from, initialVelocity: speed, time: t)
        case .tween(let duration, _) where t < duration: return (value(at: date.addingTimeInterval(0.004)) - value(at: date)) / 0.004
        default: return 0
        }
    }

    /// Heads for `target` from where it is now, keeping its speed unless `velocity` gives another.
    mutating func retarget(_ target: Double, _ kind: Kind, velocity: Double? = nil) {
        let now = Date.now
        let value = value(at: now)
        speed = velocity ?? self.velocity(at: now)
        from = value
        if case .pulse = kind { from = 0 }
        to = target
        start = now
        self.kind = kind
    }
}

#Preview {
    ZStack {
        Color(hex: 0x2c2d32).ignoresSafeArea()
        WeatherFanDial(autoPlay: true)
    }
}
