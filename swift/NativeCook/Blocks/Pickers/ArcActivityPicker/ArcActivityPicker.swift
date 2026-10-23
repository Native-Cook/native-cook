import SwiftUI
import UIKit

/// The drawing on an activity's chip, in a 24 × 24 box: its main lines, and a second set drawn fainter.
struct ArcActivityPickerIcon: Equatable {
    var lines: Path
    var soft: Path

    /// `lines` and `soft` are path commands (M, L, Q, C and Z, separated by spaces); a ring is a
    /// circle's centre and radius.
    init(_ lines: String, rings: [[Double]] = [], soft: String = "", softRings: [[Double]] = []) {
        self.lines = arcActivityPickerPath(lines, rings)
        self.soft = arcActivityPickerPath(soft, softRings)
    }
}

struct ArcActivityPickerItem: Equatable {
    var name: String
    /// The icon's colour, and the selected chip's outline and tint.
    var color: Color
    var icon: ArcActivityPickerIcon

    static let samples = [
        ArcActivityPickerItem(
            name: "Running", color: Color(hex: 0x3FC59D),
            icon: ArcActivityPickerIcon(
                "M 7.5 10.8 L 11 8.6 L 14.2 8.8 L 16.4 11.6 L 19.4 11.6 M 14.2 8.8 L 11.4 14 L 14.6 16.6 L 13.8 21 M 11.4 14 L 8.6 17.6 L 4.8 17.6",
                rings: [[15.8, 4.8, 2]], soft: "M 2.6 7.6 L 6 7.6 M 2 12.6 L 4.6 12.6"
            )
        ),
        ArcActivityPickerItem(
            name: "Sleep", color: Color(hex: 0x5B9BF0),
            icon: ArcActivityPickerIcon(
                "M 6.6 13.2 Q 8 14.6 9.4 13.2 M 11.6 13.2 Q 13 14.6 14.4 13.2 M 9.4 17 Q 10.5 17.8 11.6 17",
                rings: [[10.5, 13.8, 7.4]], soft: "M 16.6 2.6 L 21 2.6 L 16.6 7.6 L 21 7.6"
            )
        ),
        ArcActivityPickerItem(
            name: "Skipping", color: Color(hex: 0xF08A4B),
            icon: ArcActivityPickerIcon(
                "M 12 8.4 L 12 14.4 M 12 14.4 L 10 19.4 M 12 14.4 L 14 19.4 M 7.6 12.4 L 12 9.8 L 16.4 12.4",
                rings: [[12, 4.8, 2]], soft: "M 7.6 12.4 C 1.6 15.4 5 22.4 12 22.4 C 19 22.4 22.4 15.4 16.4 12.4"
            )
        ),
        ArcActivityPickerItem(
            name: "Power Training", color: Color(hex: 0xEE5A5A),
            icon: ArcActivityPickerIcon(
                "M 8.6 12 L 15.4 12 M 5.6 7 L 8.6 7 L 8.6 17 L 5.6 17 Z M 15.4 7 L 18.4 7 L 18.4 17 L 15.4 17 Z",
                soft: "M 5.6 9.6 L 2.8 9.6 L 2.8 14.4 L 5.6 14.4 M 18.4 9.6 L 21.2 9.6 L 21.2 14.4 L 18.4 14.4"
            )
        ),
        ArcActivityPickerItem(
            name: "Cycling", color: Color(hex: 0xD94FD0),
            icon: ArcActivityPickerIcon(
                "M 6 15.6 L 9.8 9 L 15.2 9 L 18 15.6 M 9.8 9 L 12 15.6 L 15.2 9 M 8 6.4 L 11 6.4 M 9.4 6.4 L 9.8 9 M 15.2 9 L 14.4 5.8 L 17 5.8",
                softRings: [[6, 15.6, 3.9], [18, 15.6, 3.9]]
            )
        ),
        ArcActivityPickerItem(
            name: "Swimming", color: Color(hex: 0x58AEEF),
            icon: ArcActivityPickerIcon(
                "M 4.4 12.2 L 9.4 7.8 L 13.6 11.4 M 2.5 16 Q 5 13.8 7.5 16 Q 10 18.2 12.5 16 Q 15 13.8 17.5 16 Q 19.6 17.8 21.5 16.4",
                rings: [[16.8, 7.6, 2]],
                soft: "M 2.5 20.2 Q 5 18 7.5 20.2 Q 10 22.4 12.5 20.2 Q 15 18 17.5 20.2 Q 19.6 22 21.5 20.6"
            )
        ),
        ArcActivityPickerItem(
            name: "Tennis", color: Color(hex: 0xF5B63C),
            icon: ArcActivityPickerIcon(
                "", rings: [[12, 12, 8.5]],
                soft: "M 5.4 6.6 C 9.2 9 9.2 15 5.4 17.4 M 18.6 6.6 C 14.8 9 14.8 15 18.6 17.4"
            )
        ),
        ArcActivityPickerItem(
            name: "Basketball", color: Color(hex: 0x9C95F2),
            icon: ArcActivityPickerIcon(
                "", rings: [[12, 12, 8.5]],
                soft: "M 12 3.5 L 12 20.5 M 3.5 12 L 20.5 12 M 6 6 C 9.4 9.4 9.4 14.6 6 18 M 18 6 C 14.6 9.4 14.6 14.6 18 18"
            )
        ),
        ArcActivityPickerItem(
            name: "Walking", color: Color(hex: 0x58C487),
            icon: ArcActivityPickerIcon(
                "M 3 16.4 L 3 10.4 C 4.6 11.6 6.6 11.4 8 9.8 L 12.6 14 C 15.2 15 18.6 14.8 20.6 16.6 C 21.4 17.4 21 18.6 20 18.6 L 4.6 18.6 C 3.6 18.6 3 17.6 3 16.4 Z",
                soft: "M 9.6 11.4 L 11.2 10 M 11.4 13 L 13 11.6 M 3 15.4 L 14 15.4"
            )
        ),
        ArcActivityPickerItem(
            name: "Hiking", color: Color(hex: 0x33B8C4),
            icon: ArcActivityPickerIcon(
                "M 2.6 19.6 L 9 7.6 L 13 14.6 L 15.6 11 L 21.4 19.6 Z",
                soft: "M 7.1 11.2 L 9 12.8 L 10.7 10.6", softRings: [[18, 5.6, 2]]
            )
        ),
    ]
}

/// A wheel of activities seen from its left flank. The rows ride an arc whose centre is far off the
/// right edge: each is a label and an icon chip, tilted along its spoke and fading towards the ends.
/// Dragging turns the wheel, and the row that reaches the knob on the rim becomes the selection: its
/// chip widens into a tinted pill and its label gets a chip of its own. Ten rows or more wrap around.
struct ArcActivityPicker: View {
    var items = ArcActivityPickerItem.samples
    /// Index of the row that rests at the knob.
    var initial = 4
    /// Turns the wheel through a short script by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the row the wheel is settling on.
    var onSelect: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the script and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The wheel: the index at the knob, continuous while it turns and unbounded as it wraps.
        var wheel = ArcActivityPickerTrack(0)
        /// How far each row has seated as the selection, 0…1.
        var seats: [ArcActivityPickerTrack] = []
        /// The knob's reaction to a row arriving, 1 falling to 0.
        var knock = ArcActivityPickerTrack(0)
        /// The row nearest the knob when the last frame was drawn.
        var nearest = 0
        /// Where the wheel was when a finger landed; nil while nothing touches it.
        var dragFrom: Double?
        /// The finger's travel when the touch turned into a drag; nil while it may still be a tap.
        var dragZero: Double?
        /// The wheel is turning under a finger, not under the script: only then it ticks.
        var byHand = false
        let haptic = UISelectionFeedbackGenerator()
    }

    private var auto: Bool { autoPlay && !reduceMotion && items.count > 1 }

    var body: some View {
        GeometryReader { geometry in
            let center = CGPoint(x: geometry.size.width / 2, y: geometry.size.height / 2)

            TimelineView(.animation) { timeline in
                let now = timeline.date
                let wheel = motion.wheel.value(at: now)
                let _ = notice(wheel)
                let knock = motion.knock.value(at: now)

                ZStack {
                    Canvas { context, _ in
                        drawRim(context, center: center, wheel: wheel, knock: knock)
                    }

                    ForEach(items.indices, id: \.self) { index in
                        let away = distance(index, wheel)
                        if abs(away) < Metrics.reach, index < motion.seats.count {
                            row(items[index], away: away, seat: motion.seats[index].value(at: now))
                                .position(
                                    x: center.x + Metrics.chipX + Metrics.radiusX * (1 - cos(away * Metrics.step)),
                                    y: center.y + Metrics.radiusY * sin(away * Metrics.step)
                                )
                        }
                    }

                    // The knob rocks like a pawl as rows pass it, and dips when one arrives.
                    Capsule()
                        .fill(Color(hex: 0x4A4D52))
                        .frame(width: Metrics.knobW, height: Metrics.knobH)
                        .scaleEffect(x: 1 - 0.08 * knock, y: 1 + 0.06 * knock)
                        .rotationEffect(.degrees(Metrics.rock * sin(tau * (wheel - wheel.rounded()))))
                        .shadow(color: .black.opacity(0.2), radius: 5, x: 0, y: 3)
                        .position(x: center.x + Metrics.knobX, y: center.y)
                }
            }
            .contentShape(Rectangle())
            .gesture(drag(center))
        }
        .frame(height: Metrics.height)
        .clipped()
        .onAppear { reset() }
        .onChange(of: items.count) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
        .accessibilityElement()
        .accessibilityLabel("Activity")
        .accessibilityValue(items.isEmpty ? "" : items[wrapped(motion.nearest)].name)
        .accessibilityAdjustableAction { direction in
            touch()
            settle(Double(motion.nearest) + (direction == .increment ? 1 : -1))
        }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    /// One activity, laid out around its chip's centre: the label ends 28 pt to the chip's left.
    private func row(_ item: ArcActivityPickerItem, away: Double, seat: Double) -> some View {
        let far = abs(away)
        let seen = clamp01(seat)
        let chip = RoundedRectangle(cornerRadius: mix(Metrics.chipRadius, Metrics.chipH / 2, seat), style: .circular)
        let plate = RoundedRectangle(cornerRadius: 10, style: .circular)

        return ZStack {
            Text(item.name)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color(white: mix(0x9B, 0x2B, seen) / 255))
                .lineLimit(1)
                .fixedSize()
                .padding(.horizontal, Metrics.platePad)
                .padding(.vertical, 5)
                .background {
                    plate.fill(Color(hex: 0xF8F8F8))
                        .overlay(plate.strokeBorder(Color(hex: 0xE0E0E0), lineWidth: 1))
                        .opacity(seen)
                }
                .frame(width: Metrics.labelW, alignment: .trailing)
                .offset(x: -(Metrics.chipW / 2 + Metrics.labelGap - Metrics.platePad + Metrics.labelW / 2))

            ZStack {
                chip.fill(Color(hex: 0xEBEBEB))
                chip.fill(Color(hex: 0xFDFDFD)).opacity(seen)
                chip.fill(item.color.opacity(0.17 * seen))
                chip.strokeBorder(item.color.opacity(0.85 * seen), lineWidth: 1.5)
                ZStack {
                    item.icon.soft.stroke(item.color.opacity(0.45), style: Metrics.pen)
                    item.icon.lines.stroke(item.color, style: Metrics.pen)
                }
                .frame(width: 24, height: 24)
                .scaleEffect(Metrics.glyph * (1 + 0.1 * seat))
            }
            .frame(width: mix(Metrics.chipW, Metrics.chipSeated, seat), height: Metrics.chipH)
            .shadow(color: item.color.opacity(0.24 * seen), radius: 9, x: 0, y: 5)
        }
        .frame(width: Metrics.chipW, height: Metrics.chipH)
        .scaleEffect(1 - Metrics.shrink * far)
        // Rows above the knob lean clockwise and rows below it the other way, each along its spoke.
        .rotationEffect(.degrees(-Metrics.tilt * away))
        .opacity(fade(far, count: items.count))
        .zIndex(seat)
    }

    /// The wheel's rim: a hairline with a bead where each row's spoke meets it, and a short stretch
    /// beside the knob that darkens when a row arrives.
    private func drawRim(_ context: GraphicsContext, center: CGPoint, wheel: Double, knock: Double) {
        let hub = CGPoint(x: center.x + Metrics.rimX + Metrics.rimR, y: center.y)
        let rim = Path(ellipseIn: CGRect(x: hub.x - Metrics.rimR, y: hub.y - Metrics.rimR, width: Metrics.rimR * 2, height: Metrics.rimR * 2))
        context.stroke(rim, with: .color(Color(hex: 0xD9D9D9)), lineWidth: 1.2)

        for index in items.indices {
            let away = distance(index, wheel)
            guard abs(away) < Metrics.reach else { continue }
            let y = Metrics.radiusY * sin(away * Metrics.step)
            let x = hub.x - (Metrics.rimR * Metrics.rimR - y * y).squareRoot()
            let bead = CGRect(x: x - 2, y: center.y + y - 2, width: 4, height: 4)
            context.fill(Path(ellipseIn: bead), with: .color(Color(hex: 0xBDBDBD, opacity: fade(abs(away), count: items.count))))
        }

        guard knock > 0 else { return }
        let glint = Gradient(stops: [
            .init(color: Color(hex: 0x4A4D52, opacity: 0), location: 0),
            .init(color: Color(hex: 0x4A4D52, opacity: 0.7 * knock), location: 0.5),
            .init(color: Color(hex: 0x4A4D52, opacity: 0), location: 1),
        ])
        var near = context
        near.clip(to: Path(CGRect(x: 0, y: center.y - Metrics.glint, width: center.x * 2, height: Metrics.glint * 2)))
        near.stroke(
            rim,
            with: .linearGradient(glint, startPoint: CGPoint(x: 0, y: center.y - Metrics.glint), endPoint: CGPoint(x: 0, y: center.y + Metrics.glint)),
            lineWidth: 1.6
        )
    }

    // MARK: The wheel

    private func wrapped(_ index: Int) -> Int {
        let count = max(items.count, 1)
        return ((index % count) + count) % count
    }

    /// How many rows `index` is below the knob (above it when negative), taking the short way round.
    private func distance(_ index: Int, _ wheel: Double) -> Double {
        let count = Double(max(items.count, 1))
        let away = (Double(index) - wheel).truncatingRemainder(dividingBy: count)
        return away > count / 2 ? away - count : away < -count / 2 ? away + count : away
    }

    private func reset() {
        let start = wrapped(initial)
        motion.wheel = ArcActivityPickerTrack(Double(start))
        motion.seats = items.indices.map { ArcActivityPickerTrack($0 == start ? 1 : 0) }
        motion.knock = ArcActivityPickerTrack(0)
        motion.nearest = start
        motion.dragFrom = nil
        motion.dragZero = nil
    }

    /// Runs every frame: when another row comes nearest the knob, the seat changes hands and the knob reacts.
    private func notice(_ wheel: Double) {
        let nearest = Int(wheel.rounded())
        guard nearest != motion.nearest, !motion.seats.isEmpty else { return }
        // Under reduced motion nothing eases: the seat jumps and the knob stays still.
        motion.seats[wrapped(motion.nearest)].retarget(0, reduceMotion ? .jump : .spring(seatSpring))
        motion.seats[wrapped(nearest)].retarget(1, reduceMotion ? .jump : .spring(seatSpring))
        motion.nearest = nearest
        if !reduceMotion { motion.knock.retarget(1, .pulse) }
        if motion.byHand { motion.haptic.selectionChanged() }
    }

    /// Sends the wheel to a whole position, carrying on at `velocity` rows per second.
    private func settle(_ target: Double, velocity: Double = 0) {
        motion.wheel.retarget(target, reduceMotion ? .jump : .spring(wheelSpring), velocity: velocity)
        if !items.isEmpty { onSelect?(wrapped(Int(target))) }
    }

    /// Brings row `index` to the knob the short way round.
    private func select(_ index: Int) {
        let wheel = motion.wheel.value(at: .now)
        settle((wheel + distance(index, wheel)).rounded())
    }

    private func touch() {
        motion.byHand = true
        touches += 1
    }

    /// One gesture for the whole wheel: a touch catches it, a drag turns it and lets go with momentum,
    /// and a touch that never moved selects the row under it.
    private func drag(_ center: CGPoint) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if motion.dragFrom == nil {
                    let held = motion.wheel.value(at: .now)
                    motion.wheel.retarget(held, .jump)
                    motion.dragFrom = held
                    motion.dragZero = nil
                    motion.haptic.prepare()
                    touch()
                }
                let travel = value.translation.height
                if motion.dragZero == nil, abs(travel) > Metrics.slop { motion.dragZero = travel }
                guard let from = motion.dragFrom, let zero = motion.dragZero else { return }
                motion.wheel.retarget(from - (travel - zero) / Metrics.pitch, .jump)
            }
            .onEnded { value in
                let wheel = motion.wheel.value(at: .now)
                if motion.dragZero == nil {
                    // A tap: find the row at that height, and take it if the touch was on its label or chip.
                    let lift = (value.startLocation.y - center.y) / Metrics.radiusY
                    let away = abs(lift) < 1 ? (asin(lift) / Metrics.step).rounded() : Metrics.reach
                    let chip = center.x + Metrics.chipX + Metrics.radiusX * (1 - cos(away * Metrics.step))
                    let reach = value.startLocation.x - chip
                    let hit = abs(away) < Metrics.reach - 0.5 && reach > -Metrics.labelW - Metrics.chipW && reach < Metrics.chipW
                    settle((wheel + (hit ? away : 0)).rounded())
                } else {
                    let speed = min(max(-value.velocity.height / Metrics.pitch, -Metrics.maxSpeed), Metrics.maxSpeed)
                    settle((wheel + speed * Metrics.glide).rounded(), velocity: speed)
                }
                motion.dragFrom = nil
                motion.dragZero = nil
                touch()
            }
    }

    /// The script a finger would follow: it turns the wheel to four rows in turn and ends where it began.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, bring the first row back, and start over from rest.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                if motion.dragFrom != nil { return }
            }
            motion.byHand = false
            select(initial)
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for step in Metrics.script {
                    try await clock.sleep(until: start + .seconds(step.at))
                    select(initial + step.rows)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// A number heading for a target, sampled against the `TimelineView` clock.
struct ArcActivityPickerTrack {
    enum Kind {
        case jump
        case spring(Spring)
        /// Up to the target in 50 ms and back to 0 in 150 ms: a knock.
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
        // Past four seconds a spring has long settled: the wheel is then exactly on its row.
        case .spring(let spring): return t > 4 ? to : from + spring.value(target: to - from, initialVelocity: speed, time: t)
        case .pulse: return to * (t < 0.05 ? t / 0.05 : max(0, 1 - (t - 0.05) / 0.15))
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, t <= 4, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
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

private enum Metrics {
    static let height = 700.0

    // The rows ride an ellipse (a wheel seen at an angle), one every 15°.
    static let step = Double.pi / 12
    static let radiusX = 205.0
    static let radiusY = 362.0
    /// The selected chip's centre, right of the panel's centre line.
    static let chipX = 44.0
    /// Rows further than this from the knob are not drawn; their opacity reaches 0 there.
    static let reach = 4.5
    /// Degrees a row leans per row of distance, and how much it shrinks.
    static let tilt = 4.8
    static let shrink = 0.035

    static let chipW = 68.0
    static let chipSeated = 76.0
    static let chipH = 52.0
    static let chipRadius = 21.0
    static let labelGap = 28.0
    static let labelW = 190.0
    static let platePad = 10.0
    /// The icons are drawn in a 24 pt box and shown this much larger.
    static let glyph = 1.15
    static let pen = StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round)

    // The rim is a circle whose leftmost point is `rimX` right of the centre line, and the knob sits on it.
    static let rimX = 116.0
    static let rimR = 480.0
    static let knobX = 125.0
    static let knobW = 46.0
    static let knobH = 17.0
    /// Degrees the knob rocks as a row passes.
    static let rock = 7.0
    /// Half the height of the stretch of rim that darkens.
    static let glint = 64.0

    // Dragging: points of travel per row, the travel that makes a touch a drag, and how far a flick carries.
    static let pitch = 94.0
    static let slop = 8.0
    static let glide = 0.16
    static let maxSpeed = 24.0

    // autoPlay (seconds): when the wheel sets off, and to which row, counted from the resting one.
    static let script: [(at: Double, rows: Int)] = [(1.0, 2), (2.6, -2), (4.4, -3), (5.65, 0)]
    static let loop = 8.0
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

private let wheelSpring = Spring(mass: 1, stiffness: 180, damping: 17.5)
private let seatSpring = Spring(mass: 1, stiffness: 300, damping: 20)

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

/// A row's opacity `far` rows from the knob: 1, 0.8, 0.57, 0.33, 0.1 and gone half a row later.
/// A short list also fades where it wraps, half a turn away.
private func fade(_ far: Double, count: Int) -> Double {
    let seam = clamp01((Double(count) / 2 - far) / 0.5)
    if far < 1 { return min(seam, 1 - 0.2 * far) }
    if far < 4 { return min(seam, 0.8 - 0.7 / 3 * (far - 1)) }
    return min(seam, max(0, 0.1 * (1 - (far - 4) / 0.5)))
}

private func arcActivityPickerPath(_ commands: String, _ rings: [[Double]]) -> Path {
    var path = Path()
    let parts = commands.split(separator: " ")
    var at = 0
    func point() -> CGPoint {
        defer { at += 2 }
        return CGPoint(x: Double(parts[at]) ?? 0, y: Double(parts[at + 1]) ?? 0)
    }
    while at < parts.count {
        let command = parts[at]
        at += 1
        switch command {
        case "M": path.move(to: point())
        case "L": path.addLine(to: point())
        case "Q":
            let control = point()
            path.addQuadCurve(to: point(), control: control)
        case "C":
            let first = point()
            let second = point()
            path.addCurve(to: point(), control1: first, control2: second)
        default: path.closeSubpath()
        }
    }
    for ring in rings where ring.count == 3 {
        path.addEllipse(in: CGRect(x: ring[0] - ring[2], y: ring[1] - ring[2], width: ring[2] * 2, height: ring[2] * 2))
    }
    return path
}

#Preview {
    ZStack {
        Color(hex: 0xF3F3F3).ignoresSafeArea()
        ArcActivityPicker(autoPlay: true)
    }
}
