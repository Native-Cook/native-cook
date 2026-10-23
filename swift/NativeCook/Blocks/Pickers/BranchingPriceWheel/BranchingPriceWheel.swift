import SwiftUI

/// A price picker made of two wheels at right angles that share one cell. The tens run down a
/// column; the amount in the middle is large and green, and from it the units branch out to the
/// right in a hairline weight. Dragging up or down turns the column (the branch folds away while
/// it moves and shoots out again from the new ten); dragging sideways slides the units through the
/// shared cell, each one thickening and turning green as it arrives. Tapping an amount goes to it.
struct BranchingPriceWheel: View {
    /// The lowest and the highest amount. The column lists every ten between them.
    var range: ClosedRange<Int> = 30...100
    /// The amount selected at first.
    var initial = 60
    /// Written after each amount.
    var currency = "$"
    var saveLabel = "Save"
    /// Slides the units and turns the column by itself, in a loop.
    var autoPlay = false
    /// Called with the amount after every step.
    var onChange: ((Int) -> Void)? = nil
    /// Called with the amount when Save is tapped.
    var onSave: ((Int) -> Void)? = nil
    var onClose: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0
    /// Count the steps a finger passes on each wheel, for the two haptics.
    @State private var tenTicks = 0
    @State private var unitTicks = 0

    private enum Axis {
        case column, branch
    }

    private enum Step {
        case unit(Int), ten(Int)
    }

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        var ready = false
        /// The column's position: the index of the ten in the middle, fractional while it turns.
        var tens = BranchingPriceWheelTrack(0)
        /// The branch's position: the unit in the shared cell, 0 for the ten itself.
        var units = BranchingPriceWheelTrack(0)
        /// 0 the branch folded into the selected amount … 1 out.
        var branch = BranchingPriceWheelTrack(1)
        /// The opacity of the rest of the column: it dims while the units move.
        var dim = BranchingPriceWheelTrack(1)
        /// Save's scale, and 0 its label … 1 the large check.
        var pop = BranchingPriceWheelTrack(1)
        var saved = BranchingPriceWheelTrack(0)

        var ten = 0
        var unit = 0
        /// The amount Save compares with: the first one, then the last one saved.
        var kept = 0
        /// The drag's axis once it is known, and the position it started from.
        var axis: Axis?
        var grabbed = 0.0
        /// Things to do at a later frame, by name: a new one replaces the one waiting under the same name.
        var later: [String: (at: Date, run: () -> Void)] = [:]

        func runDue(_ now: Date) {
            guard later.values.contains(where: { $0.at <= now }) else { return }
            let due = later.filter { $0.value.at <= now }
            for key in due.keys { later[key] = nil }
            for step in due.values { step.run() }
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    private var tens: [Int] {
        Array(stride(from: range.lowerBound, through: max(range.lowerBound, range.upperBound), by: 10))
    }

    /// Where the first amount is: its ten's place in the column and its unit.
    private var first: (ten: Int, unit: Int) {
        let value = min(max(initial, range.lowerBound), range.upperBound)
        let ten = min(tens.count - 1, (value - range.lowerBound) / 10)
        return (ten, value - tens[ten])
    }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let _ = prepare()
            let _ = motion.runDue(now)
            let column = motion.tens.value(at: now)
            let units = motion.units.value(at: now)
            let branch = motion.branch.value(at: now)
            let dim = motion.dim.value(at: now)
            let saved = motion.saved.value(at: now)
            // The selected row's height: the branch stays on it while the column turns.
            let rowY = Metrics.centerY + (Double(motion.ten) - column) * Metrics.rowPitch

            ZStack {
                ForEach(tens.indices, id: \.self) { i in
                    let away = Double(i) - column
                    let near = clamp01(1 - abs(away))
                    // The selected ten is the branch's first number: the units push it out to the left.
                    let pushed = i == motion.ten ? units : 0
                    cell(
                        tens[i], heavy: clamp01(1 - pushed),
                        color: branchingPriceWheelMix(Metrics.navy, Metrics.green, near),
                        scale: mix(Metrics.smallSize / Metrics.largeSize, 1, near)
                    )
                    .opacity(clamp01(1 - pushed) * clamp01((4.7 - abs(away)) / 0.7) * (i == motion.ten ? 1 : dim))
                    .position(
                        x: mix(Metrics.columnX, Metrics.cellX, near) - pushed * Metrics.unitPitch + Metrics.cellWidth / 2,
                        y: Metrics.centerY + away * Metrics.rowPitch
                    )
                }

                ForEach(1..<10, id: \.self) { u in
                    if u <= maxUnit(motion.ten) {
                        let away = Double(u) - units
                        // Numbers right of the selection wait for the branch, one after the other.
                        let place = u - motion.unit
                        let out = place >= 1 ? branchingPriceWheelOut(branch, place: place) : 1
                        let x = Metrics.cellX + away * Metrics.unitPitch - Metrics.branchSlide * (1 - out)
                        let opacity = out * (away < 0 ? clamp01(1 + away) : 1) * clamp01((Metrics.width - 20 - x) / 60)
                        if opacity > 0.01 {
                            cell(tens[motion.ten] + u, heavy: clamp01(1 - abs(away)), color: Metrics.green, scale: 1)
                                .opacity(opacity)
                                .position(x: x + Metrics.cellWidth / 2, y: rowY)
                        }
                    }
                }

                Canvas { context, size in
                    var cross = Path()
                    cross.move(to: CGPoint(x: size.width / 2 - 5, y: size.height / 2 - 5))
                    cross.addLine(to: CGPoint(x: size.width / 2 + 5, y: size.height / 2 + 5))
                    cross.move(to: CGPoint(x: size.width / 2 + 5, y: size.height / 2 - 5))
                    cross.addLine(to: CGPoint(x: size.width / 2 - 5, y: size.height / 2 + 5))
                    context.stroke(cross, with: .color(Metrics.navy), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                }
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
                .onTapGesture {
                    touch()
                    onClose?()
                }
                .position(x: Metrics.closeX, y: Metrics.closeY)
                .accessibilityLabel("Close")
                .accessibilityAddTraits(.isButton)

                saveButton(saved: saved)
                    .scaleEffect(motion.pop.value(at: now))
                    .position(x: Metrics.saveX, y: Metrics.saveY)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.corner, style: .continuous))
            .contentShape(Rectangle())
            .gesture(wheelDrag)
            .shadow(color: Color(hex: 0x8fa3b8, opacity: 0.28), radius: 28, x: 0, y: 18)
        }
        .sensoryFeedback(.selection, trigger: tenTicks)
        .sensoryFeedback(.impact(flexibility: .soft, intensity: 0.6), trigger: unitTicks)
        .onChange(of: range) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    /// One amount, set large. `heavy` 1 is the heavy weight in `color`, 0 the hairline weight in pale
    /// grey; the box is anchored at its left edge.
    private func cell(_ value: Int, heavy: Double, color: Color, scale: Double) -> some View {
        ZStack(alignment: .leading) {
            Text("\(value)\(currency)")
                .font(.system(size: Metrics.largeSize, weight: .thin))
                .foregroundStyle(Metrics.pale)
                .opacity(1 - heavy)
            Text("\(value)\(currency)")
                .font(.system(size: Metrics.largeSize, weight: .heavy))
                .foregroundStyle(color)
                .opacity(heavy)
        }
        .monospacedDigit()
        .lineLimit(1)
        .fixedSize()
        .scaleEffect(scale, anchor: .leading)
        .frame(width: Metrics.cellWidth, height: 40, alignment: .leading)
        .allowsHitTesting(false)
    }

    private func saveButton(saved: Double) -> some View {
        ZStack {
            HStack(spacing: 7) {
                Text(saveLabel)
                    .font(.system(size: 12, weight: .bold))
                    .lineLimit(1)
                BranchingPriceWheelCheck()
                    .stroke(style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                    .frame(width: 9, height: 7)
            }
            .opacity(1 - clamp01(saved))
            BranchingPriceWheelCheck()
                .stroke(style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
                .frame(width: 15, height: 11)
                .scaleEffect(mix(0.5, 1, saved))
                .opacity(clamp01(saved))
        }
        .foregroundStyle(.white)
        .frame(width: Metrics.saveWidth, height: Metrics.saveHeight)
        .background(Capsule().fill(Metrics.green))
        .contentShape(Capsule())
        .onTapGesture {
            touch()
            save()
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    /// One drag over the card. Its first few points decide the wheel: up and down turns the column,
    /// sideways slides the branch. A touch that does not move is a tap on the amount under it.
    private var wheelDrag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { drag in
                if motion.axis == nil {
                    guard hypot(drag.translation.width, drag.translation.height) >= Metrics.slop else { return }
                    let sideways = abs(drag.translation.width) > abs(drag.translation.height)
                    motion.axis = sideways ? .branch : .column
                    touch()
                    if sideways {
                        motion.grabbed = motion.units.value(at: .now)
                        dimColumn(true)
                    } else {
                        motion.grabbed = motion.tens.value(at: .now)
                        fold()
                    }
                }
                if motion.axis == .branch {
                    let at = rubber(motion.grabbed - drag.translation.width / Metrics.unitPitch, upTo: Double(maxUnit(motion.ten)))
                    motion.units.retarget(at, .jump)
                    pass(unit: Int(at.rounded()), tick: true)
                } else {
                    let at = rubber(motion.grabbed - drag.translation.height / Metrics.rowPitch, upTo: Double(tens.count - 1))
                    motion.tens.retarget(at, .jump)
                    pass(ten: Int(at.rounded()), tick: true)
                }
            }
            .onEnded { drag in
                defer {
                    motion.axis = nil
                    touch()
                }
                switch motion.axis {
                case .branch:
                    // The branch coasts a little way on the finger's speed before it snaps.
                    let speed = -drag.velocity.width / Metrics.unitPitch
                    let rest = (motion.units.value(at: .now) + speed * Metrics.coast).rounded()
                    slide(to: Int(min(max(rest, 0), Double(maxUnit(motion.ten)))), speed: speed, tick: true)
                case .column:
                    let speed = -drag.velocity.height / Metrics.rowPitch
                    let rest = (motion.tens.value(at: .now) + speed * Metrics.coast).rounded()
                    turn(to: Int(min(max(rest, 0), Double(tens.count - 1))), speed: speed, tick: true)
                case nil:
                    tap(at: drag.startLocation)
                }
            }
    }

    private func tap(at point: CGPoint) {
        if abs(point.x - Metrics.saveX) < Metrics.saveWidth / 2 + 6, abs(point.y - Metrics.saveY) < Metrics.saveHeight / 2 + 6 { return }
        if abs(point.x - Metrics.closeX) < 22, abs(point.y - Metrics.closeY) < 22 { return }
        if abs(point.y - Metrics.centerY) < Metrics.rowPitch / 2 {
            let unit = motion.unit + Int(((point.x - Metrics.cellX - 30) / Metrics.unitPitch).rounded())
            touch()
            slide(to: min(max(unit, 0), maxUnit(motion.ten)), tick: true)
        } else {
            let row = motion.ten + Int(((point.y - Metrics.centerY) / Metrics.rowPitch).rounded())
            guard tens.indices.contains(row) else { return }
            touch()
            turn(to: row, tick: true)
        }
    }

    // MARK: The wheels' steps

    /// The units a ten can take before the range ends.
    private func maxUnit(_ ten: Int) -> Int {
        guard tens.indices.contains(ten) else { return 0 }
        return min(9, range.upperBound - tens[ten])
    }

    /// Past either end a wheel follows the finger at a third of its speed.
    private func rubber(_ position: Double, upTo last: Double) -> Double {
        if position < 0 { return position / 3 }
        if position > last { return last + (position - last) / 3 }
        return position
    }

    /// Sets the first values before the first frame is drawn.
    private func prepare() {
        guard !motion.ready else { return }
        motion.ready = true
        reset()
    }

    private func reset() {
        motion.ten = first.ten
        motion.unit = first.unit
        motion.kept = tens[first.ten] + first.unit
        motion.tens = BranchingPriceWheelTrack(Double(first.ten))
        motion.units = BranchingPriceWheelTrack(Double(first.unit))
        motion.branch = BranchingPriceWheelTrack(1)
        motion.dim = BranchingPriceWheelTrack(1)
        motion.axis = nil
        motion.later = [:]
    }

    private func touch() {
        touches += 1
    }

    private func changed() {
        let value = tens[motion.ten] + motion.unit
        if value != motion.kept, !reduceMotion {
            // Save dips and springs back when there is something new to keep.
            motion.pop = BranchingPriceWheelTrack(0.9)
            motion.pop.retarget(1, .spring(popSpring))
        }
        onChange?(value)
    }

    private func pass(unit: Int, tick: Bool) {
        let unit = min(max(unit, 0), maxUnit(motion.ten))
        guard unit != motion.unit else { return }
        motion.unit = unit
        if tick { unitTicks += 1 }
        changed()
    }

    private func pass(ten: Int, tick: Bool) {
        let ten = min(max(ten, 0), tens.count - 1)
        guard ten != motion.ten || motion.unit != 0 else { return }
        motion.ten = ten
        motion.unit = 0
        if tick { tenTicks += 1 }
        changed()
    }

    private func dimColumn(_ dimmed: Bool) {
        motion.later["dim"] = nil
        motion.dim.retarget(dimmed ? Metrics.dimmed : 1, reduceMotion ? .jump : .tween(Metrics.dimTime, .easeInOut))
    }

    /// Folds the branch into the selected amount; the units go back to the ten itself.
    private func fold() {
        motion.later["branch"] = nil
        motion.branch.retarget(0, reduceMotion ? .jump : .tween(Metrics.foldTime, .easeInOut))
        if motion.units.value(at: .now) != 0 {
            motion.units.retarget(0, reduceMotion ? .jump : .spring(snapSpring))
        }
        dimColumn(false)
    }

    /// Slides the branch until `unit` is in the shared cell.
    private func slide(to unit: Int, speed: Double = 0, tick: Bool) {
        pass(unit: unit, tick: tick)
        motion.units.retarget(Double(motion.unit), reduceMotion ? .jump : .spring(snapSpring), speed: speed)
        dimColumn(true)
        motion.later["dim"] = (Date.now.addingTimeInterval(Metrics.snapTime), { dimColumn(false) })
    }

    /// Turns the column until row `ten` is in the middle; the branch shoots out once it has snapped.
    private func turn(to ten: Int, speed: Double = 0, tick: Bool) {
        fold()
        pass(ten: ten, tick: tick)
        motion.tens.retarget(Double(motion.ten), reduceMotion ? .jump : .spring(snapSpring), speed: speed)
        motion.later["branch"] = (Date.now.addingTimeInterval(reduceMotion ? 0 : Metrics.branchAfter), {
            motion.branch.retarget(1, reduceMotion ? .tween(Metrics.foldTime, .linear) : .tween(Metrics.branchTime, .linear))
        })
    }

    /// Keeps the amount: the label gives way to a large check for a second.
    private func save() {
        motion.kept = tens[motion.ten] + motion.unit
        motion.saved.retarget(1, reduceMotion ? .jump : .spring(popSpring))
        motion.later["saved"] = (Date.now.addingTimeInterval(Metrics.savedTime), {
            motion.saved.retarget(0, reduceMotion ? .jump : .tween(Metrics.dimTime, .easeInOut))
        })
        onSave?(motion.kept)
    }

    /// The script a finger would follow: two units along and back, two tens down, one unit along
    /// and back, and the column up again.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to the first amount, and start over once it has settled.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                guard motion.axis == nil else { return }
                if motion.ten != first.ten { turn(to: first.ten, tick: false) }
                slide(to: first.unit, tick: false)
                motion.kept = tens[first.ten] + first.unit
                try await Task.sleep(for: .seconds(Metrics.settle))
            }
            let clock = ContinuousClock()
            var begin = clock.now
            let far = min(tens.count - 1, first.ten + 2)
            let script: [(Double, Step)] = [
                (1.0, .unit(1)), (1.4, .unit(2)), (2.6, .unit(0)),
                (3.3, .ten(far)), (4.9, .unit(1)), (6.0, .unit(0)), (6.6, .ten(first.ten)),
            ]
            while true {
                for (at, step) in script {
                    try await clock.sleep(until: begin + .seconds(at))
                    switch step {
                    case .unit(let unit): slide(to: unit, tick: false)
                    case .ten(let ten): turn(to: ten, tick: false)
                    }
                }
                begin += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

private struct BranchingPriceWheelCheck: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY + rect.height * 0.55))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.36, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        return path
    }
}

/// How far out the branch's number in `place` (1 next to the selection) is, 0…1, when the branch as
/// a whole is at `branch`: each number sets off 40 ms after the one before it and takes 250 ms.
private func branchingPriceWheelOut(_ branch: Double, place: Int) -> Double {
    let t = clamp01((branch * Metrics.branchTime - Metrics.branchStagger * Double(place - 1)) / Metrics.branchRise)
    return 1 - pow(1 - t, 3)
}

/// A number heading for a target, sampled against the `TimelineView` clock.
private struct BranchingPriceWheelTrack {
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

    /// Heads for `target` from where it is now. A spring keeps the speed it has, plus `speed` units a second.
    mutating func retarget(_ target: Double, _ kind: Kind, speed: Double = 0) {
        let now = Date.now
        let value = value(at: now)
        self.speed = velocity(at: now) + speed
        from = value
        to = target
        start = now
        self.kind = kind
    }
}

private enum Metrics {
    static let width = 320.0
    static let height = 500.0
    static let corner = 36.0
    static let navy = Color(hex: 0x1b2540)
    static let green = Color(hex: 0x10d48a)
    static let pale = Color(hex: 0xcbd5e0)

    /// The row in the middle, and the distance between rows and between units.
    static let centerY = 250.0
    static let rowPitch = 50.0
    static let unitPitch = 76.0
    /// The left edge of the column's amounts, and of the large one in the shared cell.
    static let columnX = 70.0
    static let cellX = 38.0
    static let cellWidth = 140.0
    static let smallSize = 15.0
    static let largeSize = 28.0
    static let closeX = 286.0
    static let closeY = 34.0
    static let saveX = 252.0
    static let saveY = 446.0
    static let saveWidth = 84.0
    static let saveHeight = 36.0

    /// A drag picks its wheel after this many points, and coasts for this long on the finger's speed.
    static let slop = 8.0
    static let coast = 0.18
    static let dimmed = 0.3
    static let dimTime = 0.2
    static let foldTime = 0.12
    /// The branch shoots out this long after the column is let go: 250 ms a number, 40 ms apart.
    static let branchAfter = 0.3
    static let branchRise = 0.25
    static let branchStagger = 0.04
    static let branchTime = branchRise + branchStagger * 3
    static let branchSlide = 20.0
    /// The column is lit again this long after the units are let go.
    static let snapTime = 0.45
    static let savedTime = 1.0

    static let loop = 8.5
    /// After a touch the loop waits this long, returns to the first amount, and starts over once it has settled.
    static let resumeAfter = 2.0
    static let settle = 1.0
}

/// Response 0.4 s, damping ratio 0.8.
private let snapSpring = Spring(mass: 1, stiffness: 247, damping: 25)
private let popSpring = Spring(mass: 1, stiffness: 400, damping: 18)

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func branchingPriceWheelMix(_ a: Color, _ b: Color, _ t: Double) -> Color {
    let from = a.resolve(in: EnvironmentValues())
    let to = b.resolve(in: EnvironmentValues())
    let k = Float(clamp01(t))
    return Color(
        .sRGB,
        red: Double(from.red + (to.red - from.red) * k),
        green: Double(from.green + (to.green - from.green) * k),
        blue: Double(from.blue + (to.blue - from.blue) * k),
        opacity: 1
    )
}

#Preview {
    ZStack {
        Color(hex: 0xeef3f7).ignoresSafeArea()
        BranchingPriceWheel(autoPlay: true)
    }
}
