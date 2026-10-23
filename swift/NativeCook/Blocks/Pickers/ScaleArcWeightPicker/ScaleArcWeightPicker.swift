import SwiftUI

/// A target-weight picker shaped like the window of a bathroom scale. A card holds a kg / lbs switch,
/// today's weight and the target in two boxes, and at the bottom the top of a large white dial with
/// ticks and numbers under a fixed green needle. Dragging the dial turns it with weight: it coasts,
/// settles on a whole unit, and the needle swings. A wash over the ticks spans from today's weight to
/// the target, green for a gain and amber for a loss.
struct ScaleArcWeightPicker: View {
    var title = "Your Target Weight"
    var message = "Set a target weight to personalize your health\nand progress journey."
    /// Today's weight, in kilograms.
    var current = 60
    /// The target at first, in kilograms; `autoPlay` comes back to it at the end of every loop.
    var target = 70
    /// The lightest and the heaviest target the dial offers, in kilograms.
    var range = 30...200
    /// Turns the dial to three targets and back by itself, in a loop.
    var autoPlay = false
    /// Called with the target and whether it is in pounds whenever it changes.
    var onChange: ((Int, Bool) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = ScaleArcWeightPickerSim()
    @State private var reported = 0
    @State private var pounds = false
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0
    /// A finger is on the dial.
    @State private var holding = false
    /// Count the units and the long ticks passed by hand, for the two haptics.
    @State private var ticks = 0
    @State private var thuds = 0

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let _ = sim.advance(to: timeline.date, still: reduceMotion)
                let frame = sim.frame
                Canvas { context, _ in
                    var context = context
                    context.translateBy(x: Metrics.bleed, y: Metrics.bleed)
                    scaleArcWeightPickerDraw(context, frame: frame, title: title, message: message)
                }
            }
            // The canvas is larger than the card, so its shadow has room.
            .frame(width: Metrics.width + Metrics.bleed * 2, height: Metrics.height + Metrics.bleed * 2)
            .frame(width: Metrics.width, height: Metrics.height)
            .allowsHitTesting(false)

            ForEach(0..<2, id: \.self) { side in
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: Metrics.switchW / 2, height: 44)
                    .offset(x: Metrics.width / 2 + (side == 0 ? -Metrics.switchW / 2 : 0), y: Metrics.switchY - 22)
                    .onTapGesture {
                        touches += 1
                        ticks += 1
                        sim.convert(toPounds: side == 1)
                    }
                    .accessibilityLabel(side == 0 ? "kg" : "lbs")
                    .accessibilityAddTraits(.isButton)
            }

            Color.clear
                .contentShape(Rectangle())
                .frame(width: Metrics.width, height: Metrics.height - Metrics.dialY + 24)
                .offset(y: Metrics.dialY - 24)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            if !holding {
                                holding = true
                                touches += 1
                                sim.grab()
                            }
                            sim.drag(by: value.translation.width)
                        }
                        .onEnded { value in
                            sim.release(speed: value.velocity.width)
                            holding = false
                            touches += 1
                        }
                )
                .accessibilityElement()
                .accessibilityLabel(title)
                .accessibilityValue("\(reported) \(pounds ? "lbs" : "kg")")
                .accessibilityAdjustableAction { direction in
                    touches += 1
                    sim.nudge(by: direction == .increment ? 1 : -1)
                }
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .sensoryFeedback(.selection, trigger: ticks)
        .sensoryFeedback(.impact(weight: .medium), trigger: thuds)
        .onAppear { reset() }
        .onChange(of: current) { reset() }
        .onChange(of: target) { reset() }
        .onChange(of: range) { reset() }
        .task { await watch() }
        .task(id: LoopKey(auto: auto, touches: touches, holding: holding)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
        let holding: Bool
    }

    private func reset() {
        reported = target
        pounds = false
        sim.reset(current: current, target: target, range: range)
    }

    /// Reports the target and gives the haptics. The dial turns inside the drawing loop, which may
    /// not change the view's state, so this looks at it from outside.
    private func watch() async {
        while !Task.isCancelled {
            let unit = sim.frame.pounds
            let value = Int(sim.frame.value.rounded())
            if value != reported || unit != pounds {
                // A firmer tick at each long tick; none while the dial turns by itself.
                if sim.byHand {
                    if value % Metrics.longEvery == 0 { thuds += 1 } else { ticks += 1 }
                }
                reported = value
                pounds = unit
                onChange?(value, unit)
            }
            try? await Task.sleep(for: .milliseconds(16))
        }
    }

    /// The script a finger would follow: up six units, down twelve, on below today's weight, and back.
    private func runLoop() async {
        guard auto, !holding else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to kilograms and the first target, and start over.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                if sim.frame.pounds {
                    sim.convert(toPounds: false)
                    try await Task.sleep(for: .seconds(Metrics.convertTime))
                }
                sim.glide(to: Double(target), duration: Metrics.backTime)
                try await Task.sleep(for: .seconds(Metrics.backTime + 0.6))
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for move in Metrics.script {
                    try await clock.sleep(until: start + .seconds(move.at))
                    sim.glide(to: Double(target + move.offset), duration: move.time)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// Everything that moves, stepped in small fixed steps from the drawing loop.
final class ScaleArcWeightPickerSim {
    /// What a frame is drawn from.
    struct Frame {
        /// The weight under the needle and today's weight, in the unit shown.
        var value = 70.0
        var current = 60.0
        var pounds = false
        /// The switch's knob: 0 on kg … 1 on lbs.
        var knob = 0.0
        /// The needle's lean, in degrees.
        var needle = 0.0
        /// The triangle's bob, in points, and the swell of the target's box.
        var bob = 0.0
        var pulse = 0.0
    }

    private(set) var frame = Frame()
    /// The dial was last moved by a finger, not by the script.
    private(set) var byHand = false

    private enum Mode {
        /// A finger holds the dial.
        case held
        /// The dial follows a set course: the script, or the spin to the other unit.
        case gliding
        /// The dial coasts to the whole unit it will rest on.
        case free
    }

    private var mode = Mode.free
    private var kilograms = 30...200
    private var goal = 70.0
    private var speed = 0.0
    // A course: where the target and today's weight start and end, and how far along it is.
    private var from = 0.0
    private var to = 0.0
    private var currentFrom = 0.0
    private var currentTo = 0.0
    private var elapsed = 0.0
    private var duration = 0.0
    /// The dial's place when the finger came down.
    private var grabbed = 0.0
    private var moving = false
    /// The way the dial last turned: 1 towards heavier, -1 towards lighter.
    private var heading = 1.0
    private var whole = 70
    private var knobSpeed = 0.0
    private var needleSpeed = 0.0
    private var bobSpeed = 0.0
    private var pulseSpeed = 0.0
    private var last: Date? = nil

    private var low: Double { frame.pounds ? (Double(kilograms.lowerBound) * Metrics.pound).rounded() : Double(kilograms.lowerBound) }
    private var high: Double { frame.pounds ? (Double(kilograms.upperBound) * Metrics.pound).rounded() : Double(kilograms.upperBound) }

    func reset(current: Int, target: Int, range: ClosedRange<Int>) {
        kilograms = range
        frame = Frame()
        frame.value = Double(target)
        frame.current = Double(current)
        goal = frame.value
        whole = target
        mode = .free
        byHand = false
        settle()
    }

    func grab() {
        mode = .held
        byHand = true
        grabbed = frame.value
    }

    /// The finger has moved `points` to the right since it came down: the ticks go with it.
    func drag(by points: Double) {
        guard mode == .held else { return }
        goal = min(max(grabbed - points / Metrics.unitArc, low), high)
    }

    /// The finger lifted at `speed` points a second: the dial coasts on and rests on a whole unit.
    func release(speed points: Double) {
        guard mode == .held else { return }
        mode = .free
        speed = -points / Metrics.unitArc
        goal = min(max((frame.value + speed * Metrics.coast).rounded(), low), high)
        moving = true
    }

    /// One unit up or down, for the accessibility action.
    func nudge(by units: Double) {
        mode = .free
        byHand = true
        goal = min(max(goal.rounded() + units, low), high)
        moving = true
    }

    /// Sends the dial to `value` by itself, easing in and out.
    func glide(to value: Double, duration: Double) {
        byHand = false
        course(to: min(max(value, low), high), current: frame.current, duration: duration)
    }

    /// Shows the same weights in the other unit: the dial spins to the new numbers.
    func convert(toPounds: Bool) {
        guard toPounds != frame.pounds else { return }
        byHand = false
        let factor = toPounds ? Metrics.pound : 1 / Metrics.pound
        frame.pounds = toPounds
        course(to: min(max((goal.rounded() * factor).rounded(), low), high), current: (frame.current.rounded() * factor).rounded(), duration: Metrics.convertTime)
    }

    private func course(to value: Double, current: Double, duration: Double) {
        mode = .gliding
        from = frame.value
        to = value
        currentFrom = frame.current
        currentTo = current
        goal = value
        elapsed = 0
        self.duration = duration
        moving = true
    }

    func advance(to now: Date, still: Bool) {
        guard let before = last else {
            last = now
            return
        }
        let passed = min(now.timeIntervalSince(before), Metrics.longestFrame)
        guard passed > 0 else { return }
        last = now
        let steps = max(1, Int((passed / Metrics.tick).rounded(.up)))
        for _ in 0..<steps { move(passed / Double(steps)) }
        // Under reduced motion nothing turns: every change is a jump.
        if still { settle() }
    }

    private func move(_ h: Double) {
        let before = frame.value
        switch mode {
        case .held:
            // A touch closer than a frame apart, so the speed has no steps in it.
            frame.value += (goal - frame.value) * (1 - exp(-h / Metrics.grip))
            speed = (frame.value - before) / h
        case .gliding:
            elapsed += h
            let t = min(1, elapsed / max(duration, 0.001))
            let eased = scaleArcWeightPickerInOut(t)
            frame.value = from + (to - from) * eased
            frame.current = currentFrom + (currentTo - currentFrom) * eased
            speed = (frame.value - before) / h
            if t >= 1 {
                mode = .free
                speed = 0
            }
        case .free:
            speed += (Metrics.settle.stiffness * (goal - frame.value) - Metrics.settle.damping * speed) * h
            frame.value += speed * h
        }

        if abs(speed) > 0.5 { heading = speed > 0 ? 1 : -1 }
        // Each unit that passes under the needle knocks the triangle.
        let unit = Int(frame.value.rounded())
        if unit != whole {
            whole = unit
            bobSpeed -= Metrics.knock
        }
        // At rest: the needle gets a push the way the dial was going, and the box swells.
        if moving, mode == .free, abs(goal - frame.value) < 0.02, abs(speed) < 0.4 {
            moving = false
            needleSpeed -= heading * Metrics.swing
            pulseSpeed += Metrics.swell
        }

        // The needle is dragged along by the dial while it turns.
        let lean = min(max(-speed * Metrics.drag, -Metrics.lean), Metrics.lean)
        needleSpeed += (Metrics.needle.stiffness * (lean - frame.needle) - Metrics.needle.damping * needleSpeed) * h
        frame.needle += needleSpeed * h
        bobSpeed += (-Metrics.bob.stiffness * frame.bob - Metrics.bob.damping * bobSpeed) * h
        frame.bob = min(max(frame.bob + bobSpeed * h, -3), 3)
        pulseSpeed += (-Metrics.pulse.stiffness * frame.pulse - Metrics.pulse.damping * pulseSpeed) * h
        frame.pulse += pulseSpeed * h
        knobSpeed += (Metrics.knob.stiffness * ((frame.pounds ? 1 : 0) - frame.knob) - Metrics.knob.damping * knobSpeed) * h
        frame.knob += knobSpeed * h
    }

    private func settle() {
        if mode == .gliding {
            frame.current = currentTo
            mode = .free
        }
        if mode == .free { frame.value = goal.rounded() }
        speed = 0
        moving = false
        whole = Int(frame.value.rounded())
        frame.knob = frame.pounds ? 1 : 0
        knobSpeed = 0
        frame.needle = 0
        needleSpeed = 0
        frame.bob = 0
        bobSpeed = 0
        frame.pulse = 0
        pulseSpeed = 0
    }
}

private enum Metrics {
    /// The card, and the room for its shadow round it.
    static let width = 340.0
    static let height = 356.0
    static let radius = 32.0
    static let bleed = 44.0
    static let titleY = 38.0
    static let messageY = 67.0
    // The kg / lbs switch.
    static let switchY = 110.0
    static let switchW = 96.0
    static let switchH = 28.0
    // The two boxes with today's weight and the target.
    static let boxY = 162.0
    static let boxW = 54.0
    static let boxH = 48.0
    static let boxGap = 84.0
    static let triangleY = 198.0
    /// The dial is the top of a circle of `dialR` whose highest point is at `dialY`.
    static let dialY = 214.0
    static let dialR = 380.0
    static let band = 90.0
    /// Degrees between two units, and the points a finger travels for one unit.
    static let pitch = 2.6
    static let unitArc = 16.6
    static let longEvery = 5
    static let pound = 2.2046

    /// The simulation's step, in seconds, and the longest a frame is counted as.
    static let tick = 1.0 / 240
    static let longestFrame = 0.25
    static let settle = (stiffness: 60.0, damping: 15.0)
    /// A released dial rests this many seconds of its speed further on.
    static let coast = 0.25
    /// A held dial follows the finger this closely, in seconds.
    static let grip = 0.03
    static let needle = (stiffness: 180.0, damping: 11.0)
    /// The needle leans up to `lean` degrees, `drag` degrees for each unit a second, and gets a
    /// push of `swing` degrees a second when the dial comes to rest.
    static let lean = 1.2
    static let drag = 0.06
    static let swing = 36.0
    static let bob = (stiffness: 500.0, damping: 22.0)
    static let knock = 60.0
    static let pulse = (stiffness: 300.0, damping: 20.0)
    static let swell = 1.2
    static let knob = (stiffness: 320.0, damping: 26.0)
    static let convertTime = 0.7

    // autoPlay (seconds). Each turn: when it starts, the units from the first target, how long it takes.
    static let loop = 8.0
    static let backTime = 0.8
    static let script: [(at: Double, offset: Int, time: Double)] = [
        (1.0, 6, 0.9), (2.7, -6, 1.1), (4.5, -13, 0.6), (5.9, 0, 1.0),
    ]
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

/// Cubic ease-in-out, for the dial's set courses.
private func scaleArcWeightPickerInOut(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }

private let green = (r: 24.0, g: 182.0, b: 60.0)
private let amber = (r: 245.0, g: 166.0, b: 35.0)

/// Green for a gain, amber for a loss.
private func tone(_ loss: Double, _ opacity: Double) -> Color {
    .rgba(mix(green.r, amber.r, loss), mix(green.g, amber.g, loss), mix(green.b, amber.b, loss), opacity)
}

private func label(_ text: String, size: Double, weight: Font.Weight, color: Color) -> Text {
    Text(text).font(.system(size: size, weight: weight)).foregroundStyle(color)
}

/// Draws the card with its top-left corner at the origin.
private func scaleArcWeightPickerDraw(_ context: GraphicsContext, frame: ScaleArcWeightPickerSim.Frame, title: String, message: String) {
    let width = Metrics.width
    let middle = width / 2
    let card = RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous).path(in: CGRect(x: 0, y: 0, width: width, height: Metrics.height))
    let ink = Color(hex: 0x1e1e1e)
    let leaf = Color(hex: 0x18b63c)
    let unit = frame.pounds ? "lbs" : "kg"
    // 0 when the target is above today's weight, 1 when it is below.
    let loss = clamp01((frame.current - frame.value) / 0.6 + 0.5)

    var shadowed = context
    shadowed.addFilter(.shadow(color: Color(hex: 0x7a6fb0, opacity: 0.22), radius: 22, x: 0, y: 14))
    shadowed.fill(card, with: .color(.white))

    var inside = context
    inside.clip(to: card)
    // A wash of lilac into aqua, fading to white at the top.
    inside.fill(card, with: .linearGradient(Gradient(colors: [Color(hex: 0xd9c4f4), Color(hex: 0xbfeaf0)]), startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: width, y: 0)))
    inside.fill(card, with: .linearGradient(Gradient(colors: [.white, .white.opacity(0)]), startPoint: CGPoint(x: 0, y: 20), endPoint: CGPoint(x: 0, y: 210)))

    inside.draw(label(title, size: 17, weight: .semibold, color: ink), at: CGPoint(x: middle, y: Metrics.titleY), anchor: .center)
    let lines = message.split(separator: "\n")
    for (n, line) in lines.enumerated() {
        inside.draw(
            label(String(line), size: 11, weight: .regular, color: Color(hex: 0x7a7f87)),
            at: CGPoint(x: middle, y: Metrics.messageY + (Double(n) - Double(lines.count - 1) / 2) * 15), anchor: .center
        )
    }

    // The unit switch: a white capsule with a grey knob under the chosen unit.
    let half = Metrics.switchW / 2
    inside.fill(capsule(CGRect(x: middle - half, y: Metrics.switchY - Metrics.switchH / 2, width: Metrics.switchW, height: Metrics.switchH)), with: .color(.white))
    inside.fill(capsule(CGRect(x: middle - half + 3 + frame.knob * (half - 3), y: Metrics.switchY - Metrics.switchH / 2 + 3, width: half - 3, height: Metrics.switchH - 6)), with: .color(Color(hex: 0xeef0f2)))
    for (name, x, on) in [("kg", middle - half / 2 + 1.5, 1 - frame.knob), ("lbs", middle + half / 2 - 1.5, frame.knob)] {
        inside.draw(label(name, size: 11, weight: .medium, color: ink.opacity(mix(0.45, 1, clamp01(on)))), at: CGPoint(x: x, y: Metrics.switchY), anchor: .center)
    }

    // The boxes: today's weight, a chevron, and the target, outlined, over the needle.
    for (x, weight, outlined) in [(middle - Metrics.boxGap, frame.current, false), (middle, frame.value, true)] {
        var box = inside
        if outlined {
            box.translateBy(x: x, y: Metrics.boxY)
            box.scaleBy(x: 1 + frame.pulse, y: 1 + frame.pulse)
            box.translateBy(x: -x, y: -Metrics.boxY)
        }
        let rect = CGRect(x: x - Metrics.boxW / 2, y: Metrics.boxY - Metrics.boxH / 2, width: Metrics.boxW, height: Metrics.boxH)
        let shape = RoundedRectangle(cornerRadius: 11, style: .continuous).path(in: rect)
        box.fill(shape, with: .color(.white))
        if outlined { box.stroke(RoundedRectangle(cornerRadius: 10.25, style: .continuous).path(in: rect.insetBy(dx: 0.75, dy: 0.75)), with: .color(leaf), lineWidth: 1.5) }
        // The number rolls like a counter's wheel: most of the way through a unit it stands still.
        box.clip(to: Path(rect.insetBy(dx: 2, dy: 6)))
        let below = weight.rounded(.down)
        let rolled = smooth(clamp01((weight - below - 0.2) / 0.6))
        for step in 0...1 {
            var digits = box
            digits.opacity = step == 0 ? 1 - rolled : rolled
            digits.draw(
                label(String(Int(below) + step), size: 18, weight: .semibold, color: ink).monospacedDigit(),
                at: CGPoint(x: x, y: Metrics.boxY + (Double(step) - rolled) * 26), anchor: .center
            )
        }
    }
    var chevron = Path()
    chevron.move(to: CGPoint(x: middle - Metrics.boxGap / 2 - 2.5, y: Metrics.boxY - 5))
    chevron.addLine(to: CGPoint(x: middle - Metrics.boxGap / 2 + 2.5, y: Metrics.boxY))
    chevron.addLine(to: CGPoint(x: middle - Metrics.boxGap / 2 - 2.5, y: Metrics.boxY + 5))
    inside.stroke(chevron, with: .color(Color(hex: 0x9ba1a8)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))

    // How far the target is from today's weight.
    let gap = Int(frame.value.rounded()) - Int(frame.current.rounded())
    inside.draw(
        label("\(gap > 0 ? "+" : gap < 0 ? "\u{2212}" : "")\(abs(gap)) \(unit)", size: 13, weight: .semibold, color: tone(loss, 1)).monospacedDigit(),
        at: CGPoint(x: middle + Metrics.boxW / 2 + 12, y: Metrics.boxY), anchor: .leading
    )

    var triangle = Path()
    let tip = Metrics.triangleY - 5 + frame.bob
    triangle.move(to: CGPoint(x: middle, y: tip))
    triangle.addLine(to: CGPoint(x: middle + 6.5, y: tip + 10))
    triangle.addLine(to: CGPoint(x: middle - 6.5, y: tip + 10))
    triangle.closeSubpath()
    inside.fill(triangle, with: .color(leaf))
    inside.stroke(triangle, with: .color(leaf), style: StrokeStyle(lineWidth: 2, lineJoin: .round))

    // The dial: a white band with a paler disc inside it, both far larger than the card.
    let center = CGPoint(x: middle, y: Metrics.dialY + Metrics.dialR)
    func disc(_ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
    }
    /// The point `radius` from the dial's centre, `degrees` clockwise from straight up.
    func at(_ degrees: Double, _ radius: Double) -> CGPoint {
        let angle = degrees * .pi / 180
        return CGPoint(x: center.x + sin(angle) * radius, y: center.y - cos(angle) * radius)
    }
    var band = inside
    band.addFilter(.shadow(color: Color(hex: 0x6a5fa8, opacity: 0.2), radius: 12, x: 0, y: -2))
    band.fill(disc(Metrics.dialR), with: .color(.white))
    inside.fill(disc(Metrics.dialR - Metrics.band), with: .color(Color(hex: 0xf1eefa)))

    // The wash from today's weight to the needle, over the ticks.
    let start = min(max((frame.current - frame.value) * Metrics.pitch, -32), 32)
    if abs(start) > 0.05 {
        var wash = Path()
        let steps = max(2, Int(abs(start).rounded(.up)))
        wash.move(to: at(start, Metrics.dialR - 8))
        for n in 1...steps { wash.addLine(to: at(start * Double(steps - n) / Double(steps), Metrics.dialR - 8)) }
        for n in 0...steps { wash.addLine(to: at(start * Double(n) / Double(steps), Metrics.dialR - 46)) }
        wash.closeSubpath()
        inside.fill(wash, with: .color(tone(loss, 0.25)))
    }

    // Ticks and numbers turn round the dial's centre and fade out towards the card's sides.
    let first = Int((frame.value - 12).rounded(.up))
    for mark in first...(first + 24) {
        let degrees = (Double(mark) - frame.value) * Metrics.pitch
        let opacity = clamp01((27 - abs(degrees)) / 5)
        guard opacity > 0 else { continue }
        let long = mark % Metrics.longEvery == 0
        var tick = Path()
        tick.move(to: at(degrees, Metrics.dialR - 14))
        tick.addLine(to: at(degrees, Metrics.dialR - (long ? 40 : 30)))
        inside.stroke(tick, with: .color(Color(hex: 0x9ba1a8, opacity: opacity)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        if long {
            var turned = inside
            let place = at(degrees, Metrics.dialR - 58)
            turned.translateBy(x: place.x, y: place.y)
            turned.rotate(by: .degrees(degrees))
            turned.opacity = opacity
            turned.draw(label(String(mark), size: 13, weight: .regular, color: Color(hex: 0x6f757d)), at: .zero, anchor: .center)
        }
    }

    var needle = Path()
    needle.move(to: at(frame.needle, Metrics.dialR))
    needle.addLine(to: at(frame.needle, Metrics.dialR - 46))
    inside.stroke(needle, with: .color(leaf), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
}

#Preview {
    ZStack {
        Color(hex: 0xf4f3fa).ignoresSafeArea()
        ScaleArcWeightPicker(autoPlay: true)
    }
}
