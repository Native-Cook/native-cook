import SwiftUI

/// How a mode is drawn on the ring.
enum CameraModeArcDialMark: Equatable {
    /// A tulip, for close-ups.
    case macro
    /// Two peaks and a sun.
    case landscape
    /// A word in an outlined capsule.
    case capsule(String)
    /// A letter on a white disc.
    case disc(String)
    /// Bare monospaced text.
    case plain(String)
}

struct CameraModeArcDialMode: Equatable {
    var mark: CameraModeArcDialMark
    /// Shown in the middle of the dial while the mode is under the index.
    var name: String

    static let samples = [
        CameraModeArcDialMode(mark: .macro, name: "Macro"),
        CameraModeArcDialMode(mark: .landscape, name: "Landscape"),
        CameraModeArcDialMode(mark: .capsule("AUTO"), name: "Auto"),
        CameraModeArcDialMode(mark: .disc("P"), name: "Program"),
        CameraModeArcDialMode(mark: .disc("S"), name: "Shutter priority"),
        CameraModeArcDialMode(mark: .disc("A"), name: "Aperture priority"),
        CameraModeArcDialMode(mark: .disc("M"), name: "Manual"),
        CameraModeArcDialMode(mark: .plain("C1"), name: "Custom 1"),
        CameraModeArcDialMode(mark: .plain("C2"), name: "Custom 2"),
        CameraModeArcDialMode(mark: .plain("C3"), name: "Custom 3"),
    ]
}

/// The top of a camera's mode dial, drawn flat: a fixed half circle of ticks with a long index at
/// 12 o'clock, and inside it a ring of mode marks that turns in clicks. Dragging around the centre
/// turns the ring, a flick carries it on, and it always settles with one mark under the index;
/// tapping a mark brings it there. The mode's name shows in the middle.
struct CameraModeArcDial: View {
    /// The modes, in the order they sit on the ring (clockwise).
    var modes = CameraModeArcDialMode.samples
    /// Index of the mode under the index at first, and where `autoPlay` rests.
    var initial = 2
    /// The small label over the mode's name.
    var label = "MODE"
    /// Clicks through the modes and sweeps back by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the mode the ring settles on.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The ring's position, in marks: at 3 the fourth mode is under the index.
        var turn = Track(0)
        /// The index tick's kick as a mark clicks in under it, 0…1.
        var kick = Track(0)
        /// The mode the ring is heading for.
        var selected = 0

        var dragging = false
        var grabTurn = 0.0
        var lastAngle = 0.0
        /// Degrees the finger has gone round the centre since it came down.
        var swept = 0.0
        /// A finger moved the ring last, so its clicks are felt.
        var byHand = false

        /// The mode named in the middle, the one it replaced and when.
        var caption = 0
        var captionWas = 0
        var captionAt = Date.distantPast

        /// Notes the mark that has come under the index: the caption changes and the index kicks.
        func arrive(_ index: Int, at now: Date, still: Bool) {
            guard index != caption else { return }
            captionWas = caption
            caption = index
            captionAt = still ? .distantPast : now
            if !still { kick.retarget(1, .pulse) }
        }
    }

    private var last: Int { max(0, modes.count - 1) }
    private var home: Int { min(max(initial, 0), last) }
    private var auto: Bool { autoPlay && !reduceMotion && modes.count > 1 }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let turn = motion.turn.value(at: now)
            let nearest = min(max(Int(turn.rounded()), 0), last)
            let _ = motion.arrive(nearest, at: now, still: reduceMotion)
            let kick = motion.kick.value(at: now)
            let fade = min(1, max(0, now.timeIntervalSince(motion.captionAt) / Metrics.captionFade))

            Canvas { context, _ in
                drawTicks(context, kick: kick)
                for i in modes.indices {
                    drawMark(context, modes[i].mark, away: Double(i) - turn)
                }
                if fade < 1, modes.indices.contains(motion.captionWas) {
                    drawCaption(context, modes[motion.captionWas].name, opacity: 1 - fade)
                }
                if modes.indices.contains(motion.caption) {
                    drawCaption(context, modes[motion.caption].name, opacity: fade)
                }
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .contentShape(Rectangle())
            .gesture(drag)
            .sensoryFeedback(trigger: nearest) { _, _ in motion.byHand ? .selection : nil }
            .accessibilityElement()
            .accessibilityLabel(label)
            .accessibilityValue(modes.indices.contains(motion.selected) ? modes[motion.selected].name : "")
            .accessibilityAdjustableAction { direction in
                touch()
                select(motion.selected + (direction == .increment ? 1 : -1))
            }
        }
        .onAppear { reset() }
        .onChange(of: modes.count) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Drawing

    /// The fixed half circle of ticks; the one at 12 o'clock is the index.
    private func drawTicks(_ context: GraphicsContext, kick: Double) {
        var ticks = Path()
        for i in 0..<Metrics.ticks where i != Metrics.ticks / 2 {
            let angle = (-90 + 180 * Double(i) / Double(Metrics.ticks - 1)) * .pi / 180
            ticks.move(to: point(angle, Metrics.radius - Metrics.tick))
            ticks.addLine(to: point(angle, Metrics.radius))
        }
        context.stroke(ticks, with: .color(.white.opacity(0.9)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))

        var index = Path()
        index.move(to: point(0, Metrics.radius - Metrics.index))
        index.addLine(to: point(0, Metrics.radius + Metrics.kick * kick))
        context.stroke(index, with: .color(.white), style: StrokeStyle(lineWidth: 1.5 + 0.5 * kick, lineCap: .round))
    }

    /// One mode's mark, `away` marks clockwise of the index. Its top points along the ring, so it
    /// stands upright at 9 o'clock and reads top to bottom under the index.
    private func drawMark(_ context: GraphicsContext, _ mark: CameraModeArcDialMark, away: Double) {
        let degrees = away * Metrics.step
        guard abs(degrees) < 200 else { return }
        let near = max(0, 1 - abs(away))
        var context = context
        let center = point(degrees * .pi / 180, Metrics.ring)
        context.translateBy(x: center.x, y: center.y)
        context.rotate(by: .degrees(degrees + 90))
        context.scaleBy(x: 1 + Metrics.grow * near, y: 1 + Metrics.grow * near)
        // Marks more than one place from the index dim a little, down to 60% four places away.
        context.opacity = 1 - 0.4 * min(1, max(0, (abs(away) - 1) / 3))

        let white = GraphicsContext.Shading.color(.white)
        switch mark {
        case .macro:
            context.fill(cameraModeArcDialTulip, with: white)
        case .landscape:
            context.fill(cameraModeArcDialPeaks, with: white)
        case .capsule(let word):
            let box = CGRect(x: -Metrics.capsuleW / 2, y: -Metrics.capsuleH / 2, width: Metrics.capsuleW, height: Metrics.capsuleH)
            context.stroke(Capsule().path(in: box.insetBy(dx: 0.75, dy: 0.75)), with: white, lineWidth: 1.5)
            context.draw(Text(word).font(.system(size: 11, weight: .heavy)).tracking(0.4).foregroundStyle(.white), at: .zero)
        case .disc(let letter):
            let r = Metrics.disc / 2
            context.fill(Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2)), with: white)
            context.draw(Text(letter).font(.system(size: 14, weight: .bold)).foregroundStyle(.black), at: .zero)
        case .plain(let word):
            context.draw(Text(word).font(.system(size: 13, weight: .bold, design: .monospaced)).foregroundStyle(.white), at: .zero)
        }
    }

    /// The label and the mode's name, in the middle of the ring.
    private func drawCaption(_ context: GraphicsContext, _ name: String, opacity: Double) {
        var context = context
        context.draw(
            Text(label).font(.system(size: 9.5, weight: .semibold)).tracking(2).foregroundStyle(.white.opacity(0.45)),
            at: CGPoint(x: Metrics.centerX, y: Metrics.centerY - 11)
        )
        context.opacity = opacity
        context.draw(
            Text(name).font(.system(size: 15, weight: .semibold)).foregroundStyle(.white),
            at: CGPoint(x: Metrics.centerX, y: Metrics.centerY + 7)
        )
    }

    /// The point `radius` from the dial's centre, `angle` radians clockwise of 12 o'clock.
    private func point(_ angle: Double, _ radius: Double) -> CGPoint {
        CGPoint(x: Metrics.centerX + radius * sin(angle), y: Metrics.centerY - radius * cos(angle))
    }

    // MARK: Turning

    private func reset() {
        motion.turn = Track(Double(home))
        motion.selected = home
        motion.caption = home
        motion.captionWas = home
    }

    private func touch() {
        motion.byHand = true
        touches += 1
    }

    /// Turns the ring until mode `index` is under the index: one click on a spring to a neighbour,
    /// one sweep that runs a little past and comes back to a mode further away.
    private func select(_ index: Int) {
        let index = min(max(index, 0), last)
        let distance = abs(Double(index) - motion.turn.value(at: .now))
        if reduceMotion {
            motion.turn.retarget(Double(index), .jump)
        } else if distance <= 1.2 {
            motion.turn.retarget(Double(index), .spring(clickSpring))
        } else {
            motion.turn.retarget(Double(index), .tween(min(Metrics.sweepMax, 0.3 + 0.1 * distance), sweepCurve))
        }
        settle(on: index)
    }

    private func settle(on index: Int) {
        guard index != motion.selected else { return }
        motion.selected = index
        onChange?(index)
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
                // The finger's angle wraps at 6 o'clock; the turn it adds up to does not.
                var delta = angle - motion.lastAngle
                if delta > 180 { delta -= 360 }
                if delta < -180 { delta += 360 }
                motion.lastAngle = angle
                guard hypot(value.translation.width, value.translation.height) >= Metrics.slop else { return }
                motion.swept += delta
                motion.turn = Track(rubber(motion.grabTurn - motion.swept / Metrics.step))
            }
            .onEnded { value in
                motion.dragging = false
                touch()
                guard hypot(value.translation.width, value.translation.height) >= Metrics.slop else {
                    if let hit = mark(at: value.location) { select(hit) }
                    return
                }
                let turn = motion.turn.value(at: .now)
                guard !reduceMotion else {
                    select(Int(turn.rounded()))
                    return
                }
                // How fast the finger was going round the centre, in marks a second.
                let x = value.location.x - Metrics.centerX
                let y = value.location.y - Metrics.centerY
                let reach = max(x * x + y * y, 40 * 40)
                let speed = -(x * value.velocity.height - y * value.velocity.width) / reach * 180 / .pi / Metrics.step
                let target = min(max((turn + speed * Metrics.carry).rounded(), 0), Double(last))
                motion.turn.retarget(target, .spring(flingSpring), velocity: speed)
                settle(on: Int(target))
            }
    }

    /// Degrees clockwise of 12 o'clock at which `location` lies from the dial's centre.
    private func degrees(of location: CGPoint) -> Double {
        atan2(location.x - Metrics.centerX, Metrics.centerY - location.y) * 180 / .pi
    }

    /// Past either end the ring gives less and less, up to 0.6 of a mark.
    private func rubber(_ turn: Double) -> Double {
        let over = turn < 0 ? -turn : max(0, turn - Double(last))
        guard over > 0 else { return turn }
        let give = 0.6 * (1 - 1 / (over * 0.9 + 1))
        return turn < 0 ? -give : Double(last) + give
    }

    /// The mode whose mark is under `location`, if any.
    private func mark(at location: CGPoint) -> Int? {
        let turn = motion.turn.value(at: .now)
        return modes.indices.first { i in
            let center = point((Double(i) - turn) * Metrics.step * .pi / 180, Metrics.ring)
            return hypot(location.x - center.x, location.y - center.y) < Metrics.reach
        }
    }

    // MARK: autoPlay

    /// The script a finger would follow: click through the modes after the first, pausing halfway,
    /// then sweep back.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, turn back to the first mode and start over.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                guard !motion.dragging else { return }
                motion.byHand = false
                select(home)
                try await Task.sleep(for: .seconds(Metrics.sweepMax + 0.2))
            }
            let forward = home < last
            let count = forward ? last - home : home
            let gap = min(Metrics.stepGap, 4.8 / Double(count + 1))
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for k in 0..<count {
                    let slot = Double(k >= (count + 1) / 2 ? k + 1 : k)
                    try await clock.sleep(until: start + .seconds(Metrics.rest + slot * gap))
                    select(home + (forward ? k + 1 : -(k + 1)))
                }
                try await clock.sleep(until: start + .seconds(Metrics.rest + Double(count + 1) * gap + 0.5))
                select(home)
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

private enum Metrics {
    static let width = 336.0
    static let height = 304.0
    static let centerX = 168.0
    static let centerY = 166.0

    /// Outer radius of the ticks, their length and the index's.
    static let radius = 160.0
    static let tick = 10.0
    static let index = 26.0
    /// How far the index reaches outward as a mark clicks in.
    static let kick = 5.0
    static let ticks = 49

    /// Radius of the ring of marks, and the degrees from one mark to the next.
    static let ring = 106.0
    static let step = 19.0
    static let capsuleW = 46.0
    static let capsuleH = 23.0
    static let disc = 27.0
    /// The mark under the index is this much larger.
    static let grow = 0.12
    /// A tap this close to a mark's centre picks it.
    static let reach = 20.0
    /// A touch that moves less than this is a tap.
    static let slop = 8.0
    /// A flick carries the ring on by its speed times this many seconds.
    static let carry = 0.18

    static let captionFade = 0.12
    static let sweepMax = 0.9

    // autoPlay (seconds): rest, a click every `stepGap` with one gap skipped halfway, the sweep back.
    static let rest = 0.9
    static let stepGap = 0.6
    static let loop = 8.0
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

private let clickSpring = Spring(mass: 1, stiffness: 190, damping: 18)
private let flingSpring = Spring(mass: 1, stiffness: 140, damping: 20)
/// Ease-in-out that runs 4.5% past its target at 79% of the time and comes back.
private let sweepCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.5, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1.3))

/// A number heading for a target, sampled against the `TimelineView` clock.
private struct Track {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
        /// Up to the target in 60 ms and back to 0 in 220 ms.
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
        case .tween(let duration, let curve): return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
        case .spring(let spring): return from + spring.value(target: to - from, initialVelocity: speed, time: t)
        case .pulse: return to * (t < 0.06 ? t / 0.06 : max(0, 1 - (t - 0.06) / 0.22))
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

/// The macro tulip, in a 24 pt box around the origin.
private let cameraModeArcDialTulip: Path = {
    var path = Path()
    path.move(to: CGPoint(x: -6.5, y: -10.5))
    path.addLine(to: CGPoint(x: -3.25, y: -7.5))
    path.addLine(to: CGPoint(x: 0, y: -10.5))
    path.addLine(to: CGPoint(x: 3.25, y: -7.5))
    path.addLine(to: CGPoint(x: 6.5, y: -10.5))
    path.addLine(to: CGPoint(x: 6.5, y: -4.5))
    path.addArc(center: CGPoint(x: 0, y: -4.5), radius: 6.5, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
    path.closeSubpath()
    path.addRect(CGRect(x: -1, y: 1.5, width: 2, height: 10))
    for side in [-1.0, 1.0] {
        path.move(to: CGPoint(x: 0, y: 11.5))
        path.addQuadCurve(to: CGPoint(x: side * 9, y: 3.5), control: CGPoint(x: side * 9, y: 11.5))
        path.addQuadCurve(to: CGPoint(x: 0, y: 11.5), control: CGPoint(x: side * 2, y: 5.5))
        path.closeSubpath()
    }
    return path
}()

/// The landscape's two peaks and sun, in a 24 pt box around the origin.
private let cameraModeArcDialPeaks: Path = {
    var path = Path()
    path.move(to: CGPoint(x: -11, y: 9))
    path.addLine(to: CGPoint(x: -3.5, y: -8))
    path.addLine(to: CGPoint(x: 4, y: 9))
    path.closeSubpath()
    path.move(to: CGPoint(x: 1.5, y: 9))
    path.addLine(to: CGPoint(x: 6.5, y: 0))
    path.addLine(to: CGPoint(x: 11.5, y: 9))
    path.closeSubpath()
    path.addEllipse(in: CGRect(x: 5.4, y: -9, width: 5.2, height: 5.2))
    return path
}()

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        CameraModeArcDial(autoPlay: true)
    }
}
