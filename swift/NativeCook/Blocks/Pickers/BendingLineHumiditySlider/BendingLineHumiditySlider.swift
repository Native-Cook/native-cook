import SwiftUI
import UIKit

/// The fixed words beside the slider.
struct BendingLineHumiditySliderLabels: Equatable {
    var temperatureTitle = "RETURN TEMPERATURE"
    var temperature = "20°C"
    var humidityTitle = "CURRENT HUMIDITY"
    var absoluteTitle = "ABSOLUTE HUMIDITY"
    var absolute = "4gr/ft3"
    var warning = "— extreme humidity levels. Use precaution for set-points outside of 20%-55%."
}

/// A vertical humidity slider drawn as one thin line that swerves around its knob like a string pushed
/// aside by a bead. The line is cyan through the recommended band and turns magenta outside it; a
/// ladder of ticks left of it bends with it, and the labels of an uneven scale stand further left, the
/// one beside the knob replaced by the live set-point. Right of the line three read-outs are stacked;
/// the large one rolls its digits like a counter as it follows the set-point.
struct BendingLineHumiditySlider: View {
    /// The set-point at first, in percent, and the one the self-playing demo rests on.
    var initial = 35.0
    /// The labels of the scale from top to bottom. They are evenly spaced although the values are not.
    var stops: [Double] = [100, 75, 50, 45, 40, 35, 30, 25, 10, 0]
    /// The labels that carry a yellow dot.
    var extremes: Set<Double> = [100, 75, 50, 25, 10, 0]
    /// The recommended band: the line is cyan inside it.
    var safe = 20.0...55.0
    var labels = BendingLineHumiditySliderLabels()
    /// Moves the knob up and down by itself, in a loop.
    var autoPlay = false
    /// Called with the set-point, in whole percent, each time it changes.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The knob's height on the panel.
        var knob = Track(0)
        /// The point the ends of the bump are centred on. It follows the knob on a spring, so the bump leans on a quick move.
        var lag = 0.0
        var lagSpeed = 0.0
        /// The large figure, which rolls towards the set-point.
        var shown = 0.0
        /// 0 at rest … 1 while the knob is held.
        var press = Track(0)
        /// The knob's size, which dips while it is held and bounces on release.
        var size = Track(1)
        /// When the set-point last left the recommended band: the warning pulses.
        var warned = Date.distantPast
        var wasSafe = true
        var dragging = false
        var dragged = false
        var dragFrom = 0.0
        /// The whole percent at the last frame, for the haptic ticks and `onChange`.
        var ticked = 0
        /// The moves come from a finger, so they tick; the script's do not.
        var felt = false
        var last = Date.now
        let haptic = UISelectionFeedbackGenerator()
    }

    private var auto: Bool { autoPlay && !reduceMotion && stops.count > 1 }
    private var scale: Scale { Scale(stops: stops) }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let _ = step(now)
            let scale = scale
            let y = motion.knob.value(at: now)
            let value = scale.value(at: y)
            let press = motion.press.value(at: now)
            let warn = reduceMotion ? 0 : pulse(now.timeIntervalSince(motion.warned))

            ZStack {
                Canvas { context, size in
                    draw(context, size: size, scale: scale, y: y, press: press, knobSize: motion.size.value(at: now))
                }

                ForEach(stops.indices, id: \.self) { k in
                    let at = scale.top + Double(k) * scale.gap
                    // A label the knob comes close to makes room for the live one.
                    Text("\(Int(stops[k]))%")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.white)
                        .place(x: Metrics.labelX, y: at - 10, width: 60, height: 20, .leading)
                        .opacity(clamp01((abs(at - y) - Metrics.hideWithin) / Metrics.hideFade))
                }
                Text("\(Int(value.rounded()))%")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(tone(value))
                    .place(x: Metrics.labelX - 2, y: y - 12, width: 70, height: 24, .leading)

                readouts(shown: motion.shown, warn: warn)
            }
            .monospacedDigit()
            .lineLimit(1)
            .frame(width: Metrics.width, height: Metrics.height)
            .contentShape(Rectangle())
            .gesture(drag(scale))
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .onAppear { reset() }
        .onChange(of: stops) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(labels.humidityTitle)
        .accessibilityValue("\(motion.ticked)%")
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    /// The three read-outs and the warning, right of the line.
    private func readouts(shown: Double, warn: Double) -> some View {
        let x = Metrics.readX
        return Group {
            Text(labels.temperatureTitle).font(.system(size: 10, weight: .medium)).tracking(0.4).foregroundStyle(Palette.dim)
                .place(x: x, y: 158, width: 150, height: 14, .leading)
            Text(labels.temperature).font(.system(size: 20, weight: .bold)).foregroundStyle(.white)
                .place(x: x, y: 176, width: 150, height: 26, .leading)

            Text(labels.humidityTitle).font(.system(size: 10, weight: .medium)).tracking(0.4).foregroundStyle(Palette.dim)
                .place(x: x, y: 238, width: 150, height: 14, .leading)
            counter(shown)
                .place(x: x - 2, y: 254, width: 160, height: Metrics.digitH, .leading)

            Text(labels.absoluteTitle).font(.system(size: 10, weight: .medium)).tracking(0.4).foregroundStyle(Palette.dim)
                .place(x: x, y: 338, width: 150, height: 14, .leading)
            Text(labels.absolute).font(.system(size: 20, weight: .bold)).foregroundStyle(.white)
                .place(x: x, y: 356, width: 150, height: 26, .leading)

            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Palette.yellow)
                .scaleEffect(1 + 0.25 * warn)
                .place(x: x - 2, y: 410, width: 22, height: 22, .center)
            (Text("• ").foregroundStyle(Palette.yellow) + Text(labels.warning).foregroundStyle(blend(Palette.noteInk, Palette.yellowInk, warn)))
                .font(.system(size: 10))
                .lineSpacing(3)
                .lineLimit(3)
                .multilineTextAlignment(.leading)
                .place(x: x, y: 438, width: 148, height: 48, .topLeading)
        }
    }

    /// The large figure as a counter: each digit is a strip of numerals that rolls behind a window, and
    /// a strip only turns while the one to its right goes from 9 to 0.
    private func counter(_ shown: Double) -> some View {
        let units = shown.truncatingRemainder(dividingBy: 10)
        let tens = (shown / 10).rounded(.down) + max(0, units - 9)
        let hundreds = (shown / 100).rounded(.down) + max(0, shown.truncatingRemainder(dividingBy: 100) - 99)
        // Columns that would show a leading zero are left out, and the rest moves up to the left edge.
        let columns = shown > 99 ? 3 : shown > 9 ? 2 : 1
        return HStack(spacing: 0) {
            if columns > 2 { strip(hundreds) }
            if columns > 1 { strip(tens) }
            strip(units)
            Text("%")
                .font(.system(size: 52, weight: .heavy))
                .foregroundStyle(.white)
                .frame(height: Metrics.digitH)
                .padding(.leading, 1)
        }
    }

    private func strip(_ position: Double) -> some View {
        let turn = position.truncatingRemainder(dividingBy: 10)
        return VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { digit in
                Text("\(digit % 10)")
                    .font(.system(size: 52, weight: .heavy))
                    .foregroundStyle(.white)
                    .frame(width: Metrics.digitW, height: Metrics.digitH)
            }
        }
        .offset(y: -turn * Metrics.digitH)
        .frame(width: Metrics.digitW, height: Metrics.digitH, alignment: .top)
        .clipped()
    }

    /// The line with its bump, the ladder of ticks, the dots of the extreme labels and the knob.
    private func draw(_ context: GraphicsContext, size: CGSize, scale: Scale, y: Double, press: Double, knobSize: Double) {
        let lean = min(max(motion.lag - y, -Metrics.maxLean), Metrics.maxLean)
        func bend(_ at: Double) -> Double {
            let s = at < y ? (y - at) / (Metrics.span - lean) : (at - y) / (Metrics.span + lean)
            return s >= 1 ? 0 : (1 + cos(.pi * s)) / 2
        }

        // Ticks every tenth of a step, a longer one every fifth; those beside the bump are pushed left with it.
        for n in 0...(stops.count - 1) * 10 {
            let at = scale.top + Double(n) * scale.gap / 10
            let push = bend(at)
            let right = Metrics.axis - Metrics.tickInset - push * Metrics.bump
            var tick = Path()
            tick.move(to: CGPoint(x: right - (n % 5 == 0 ? 12 : 6), y: at))
            tick.addLine(to: CGPoint(x: right, y: at))
            context.stroke(tick, with: .color(blend(Palette.tick, Palette.tickLit, push)), lineWidth: 1)
        }
        for k in stops.indices where extremes.contains(stops[k]) {
            let at = scale.top + Double(k) * scale.gap
            context.fill(Path(ellipseIn: CGRect(x: Metrics.dotX - 1.5, y: at - 1.5, width: 3, height: 3)), with: .color(Palette.yellow))
        }

        var line = Path()
        var at = 0.0
        line.move(to: CGPoint(x: Metrics.axis - bend(0) * Metrics.bump, y: 0))
        while at < size.height {
            at = min(at + 2, size.height)
            line.addLine(to: CGPoint(x: Metrics.axis - bend(at) * Metrics.bump, y: at))
        }
        // The line's colour belongs to the height, so the bump takes the colour of where it is; both ends fade out.
        let stopCount = 48
        let gradient = Gradient(stops: (0...stopCount).map { n in
            let u = Double(n) / Double(stopCount)
            let end = clamp01(min(u, 1 - u) * size.height / Metrics.endFade)
            return Gradient.Stop(color: tone(scale.value(at: u * size.height)).opacity(end), location: u)
        })
        context.stroke(
            line,
            with: .linearGradient(gradient, startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)),
            style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
        )

        // The knob: a white bead with a pair of chevrons; held, it shows a grey ring and the chevrons dim.
        var knob = context
        knob.translateBy(x: Metrics.axis + Metrics.knobOut, y: y)
        knob.scaleBy(x: knobSize, y: knobSize)
        knob.fillEllipse(center: .zero, radii: CGSize(width: 30, height: 30), gradient: Gradient(colors: [.black.opacity(0.45), .black.opacity(0)]))
        knob.fill(Path(ellipseIn: CGRect(x: -17, y: -17, width: 34, height: 34)), with: .color(.white))
        knob.stroke(Path(ellipseIn: CGRect(x: -12.5, y: -12.5, width: 25, height: 25)), with: .color(Palette.ring.opacity(press)), lineWidth: 2)
        var chevrons = Path()
        chevrons.move(to: CGPoint(x: -3.5, y: -2.5))
        chevrons.addLine(to: CGPoint(x: 0, y: -6))
        chevrons.addLine(to: CGPoint(x: 3.5, y: -2.5))
        chevrons.move(to: CGPoint(x: -3.5, y: 2.5))
        chevrons.addLine(to: CGPoint(x: 0, y: 6))
        chevrons.addLine(to: CGPoint(x: 3.5, y: 2.5))
        knob.stroke(chevrons, with: .color(Color(hex: Palette.night, opacity: 1 - 0.6 * press)), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
    }

    /// Cyan inside the recommended band, through violet to magenta outside it.
    private func tone(_ value: Double) -> Color {
        let over = max((value - safe.upperBound) / Metrics.blendAbove, (safe.lowerBound - value) / Metrics.blendBelow)
        return blend(Palette.cyan, Palette.magenta, clamp01(over))
    }

    // MARK: The knob's moves

    private func drag(_ scale: Scale) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if !motion.dragging {
                    touches += 1
                    motion.dragging = true
                    motion.dragged = false
                    motion.felt = true
                    motion.dragFrom = motion.knob.value(at: .now)
                    motion.knob.set(motion.dragFrom)
                    hold(true)
                }
                guard motion.dragged || abs(value.translation.height) > Metrics.tapSlop else { return }
                motion.dragged = true
                motion.knob.set(scale.clamped(motion.dragFrom + value.translation.height))
            }
            .onEnded { value in
                touches += 1
                motion.dragging = false
                hold(false)
                // A tap on the scale sends the knob there; either way it comes to rest on a whole percent.
                let y = !motion.dragged && value.location.x < Metrics.readX - 10 ? scale.clamped(value.location.y) : motion.knob.value(at: .now)
                move(to: scale.value(at: y).rounded(), .spring(Spring(mass: 1, stiffness: Metrics.stiffness, damping: Metrics.damping)))
            }
    }

    private func reset() {
        let scale = scale
        let y = scale.y(of: initial)
        motion.knob = Track(y)
        motion.lag = y
        motion.lagSpeed = 0
        motion.shown = initial.rounded()
        motion.ticked = Int(initial.rounded())
        motion.wasSafe = safe.contains(initial)
        motion.last = .now
    }

    /// Runs once a frame: the bump's ends and the large figure catch up with the knob, each whole
    /// percent ticks, and leaving the recommended band sets off the warning.
    private func step(_ now: Date) {
        let dt = min(max(now.timeIntervalSince(motion.last), 0), 1.0 / 30)
        motion.last = now
        let scale = scale
        let y = motion.knob.value(at: now)
        let value = scale.value(at: y)
        if reduceMotion {
            motion.lag = y
            motion.shown = value.rounded()
        } else {
            motion.lagSpeed += (Metrics.lagStiffness * (y - motion.lag) - Metrics.lagDamping * motion.lagSpeed) * dt
            motion.lag += motion.lagSpeed * dt
            let gap = value.rounded() - motion.shown
            motion.shown = abs(gap) < 0.01 ? value.rounded() : motion.shown + gap * (1 - exp(-dt / Metrics.rollTime))
        }
        let whole = Int(value.rounded())
        if whole != motion.ticked {
            motion.ticked = whole
            if motion.felt { motion.haptic.selectionChanged() }
            onChange?(whole)
        }
        let isSafe = safe.contains(Double(whole))
        if isSafe != motion.wasSafe {
            motion.wasSafe = isSafe
            if !isSafe { motion.warned = now }
        }
    }

    /// The knob is pressed or let go: held, it shrinks a little under a grey ring; released, it bounces back.
    private func hold(_ down: Bool) {
        if reduceMotion {
            motion.press.set(down ? 1 : 0)
        } else if down {
            motion.press.retarget(1, .tween(Metrics.pressTime, .linear))
            motion.size.retarget(Metrics.pressScale, .tween(Metrics.pressTime, Ease.out))
        } else {
            motion.press.retarget(0, .tween(Metrics.releaseTime, .linear))
            motion.size.retarget(1, .spring(Spring(mass: 1, stiffness: 420, damping: 13)))
        }
    }

    private func move(to value: Double, _ kind: Track.Kind) {
        motion.knob.retarget(scale.y(of: value), reduceMotion ? .jump : kind)
    }

    /// The script a finger would follow: up, down past three labels, down into the magenta, and back.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, glide back to the resting set-point, and start over from there.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                guard !motion.dragging else { return }
                motion.felt = false
                move(to: initial, .tween(Metrics.homeTime, easeInOut))
                try await Task.sleep(for: .seconds(Metrics.homeTime + 0.6))
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for step in Metrics.script {
                    try await clock.sleep(until: start + .seconds(step.at))
                    hold(true)
                    move(to: step.to ?? initial, .tween(step.time, step.to == nil ? settleCurve : easeInOut))
                    try await clock.sleep(until: start + .seconds(step.at + step.time))
                    hold(false)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// The scale: the labels are `gap` apart whatever their values, and a height between two of them
/// stands for a value between theirs.
private struct Scale {
    let stops: [Double]
    let top = Metrics.scaleTop
    var gap: Double { (Metrics.height - Metrics.scaleTop * 2) / Double(max(1, stops.count - 1)) }

    func clamped(_ y: Double) -> Double {
        min(max(y, top), top + gap * Double(stops.count - 1))
    }

    func value(at y: Double) -> Double {
        guard stops.count > 1 else { return stops.first ?? 0 }
        let u = (clamped(y) - top) / gap
        let k = min(Int(u), stops.count - 2)
        return stops[k] + (stops[k + 1] - stops[k]) * (u - Double(k))
    }

    func y(of value: Double) -> Double {
        guard stops.count > 1 else { return top }
        for k in 0..<stops.count - 1 where value <= stops[k] && value >= stops[k + 1] && stops[k] != stops[k + 1] {
            return top + (Double(k) + (stops[k] - value) / (stops[k] - stops[k + 1])) * gap
        }
        return value > stops[0] ? top : top + gap * Double(stops.count - 1)
    }
}

private enum Palette {
    static let night: UInt32 = 0x070a26
    static let cyan: UInt32 = 0x1aa9e0
    static let magenta: UInt32 = 0xd4127a
    static let tick: UInt32 = 0x2a2f55
    static let tickLit: UInt32 = 0x5d68b8
    static let noteInk: UInt32 = 0x6a72a8
    static let yellowInk: UInt32 = 0xf5d21f
    static let yellow = Color(hex: 0xf5d21f)
    static let dim = Color(hex: 0x4b5390)
    static let ring = Color(hex: 0xb9bdcc)
}

private enum Metrics {
    static let width = 340.0
    static let height = 600.0

    // The line, its bump and the knob.
    static let axis = 122.0
    /// How far the line swerves, and how far above and below the knob it leaves its axis.
    static let bump = 23.0
    static let span = 46.0
    /// The furthest the bump's ends trail behind the knob.
    static let maxLean = 18.0
    /// The knob sits this far right of the axis.
    static let knobOut = 3.0
    static let endFade = 28.0
    /// Percent over which the line turns from cyan to magenta above and below the recommended band.
    static let blendAbove = 12.0
    static let blendBelow = 8.0

    // The scale.
    static let scaleTop = 48.0
    static let labelX = 26.0
    static let dotX = 17.0
    static let tickInset = 5.0
    /// A label hides while the knob is within this many points of it, and fades in over the next few.
    static let hideWithin = 18.0
    static let hideFade = 12.0

    // The read-outs.
    static let readX = 182.0
    static let digitW = 34.0
    static let digitH = 60.0
    /// Seconds the large figure takes to cover most of the way to the set-point.
    static let rollTime = 0.18

    // The knob's settling, the spring the bump's ends follow it on, and the press.
    static let stiffness = 300.0
    static let damping = 26.0
    static let lagStiffness = 260.0
    static let lagDamping = 24.0
    static let pressScale = 0.92
    static let pressTime = 0.12
    static let releaseTime = 0.2
    static let tapSlop = 4.0

    // autoPlay (seconds): when each move starts, the set-point it goes to (nil: the resting one) and how long it takes.
    static let loop = 8.0
    static let script: [(at: Double, to: Double?, time: Double)] = [
        (1.0, 48, 0.9),
        (2.6, 28, 1.1),
        (4.4, 18, 0.5),
        (5.5, nil, 0.9),
    ]
    /// After a touch the loop waits this long, then takes `homeTime` to return to the resting set-point.
    static let resumeAfter = 2.0
    static let homeTime = 0.7
}

/// A number that jumps, eases or springs towards a target, sampled against the `TimelineView` clock.
private struct Track {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
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
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
    }

    mutating func set(_ value: Double) {
        from = value
        to = value
        kind = .jump
    }

    /// Heads for `target` from where it is now, keeping the speed a spring had.
    mutating func retarget(_ target: Double, _ kind: Kind) {
        let now = Date.now
        let value = value(at: now)
        speed = velocity(at: now)
        from = value
        to = target
        start = now
        self.kind = kind
    }
}

private extension View {
    /// Puts the view in a box whose top-left corner is at `x`, `y`.
    func place(x: Double, y: Double, width: Double, height: Double, _ alignment: Alignment) -> some View {
        frame(width: width, height: height, alignment: alignment)
            .position(x: x + width / 2, y: y + height / 2)
    }
}

private let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.55, y: 1))
/// Ease-in-out that runs a little past the end and comes back.
private let settleCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.3, y: 1.18))

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func blend(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
    func channel(_ shift: UInt32) -> Double {
        mix(Double((a >> shift) & 0xff), Double((b >> shift) & 0xff), t) / 255
    }
    return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
}

/// One swell and back in half a second, for the warning.
private func pulse(_ t: Double) -> Double {
    guard t > 0, t < 0.5 else { return 0 }
    return sin(.pi * t / 0.5)
}

#Preview {
    ZStack {
        Color(hex: 0x070a26).ignoresSafeArea()
        BendingLineHumiditySlider(autoPlay: true)
    }
}
