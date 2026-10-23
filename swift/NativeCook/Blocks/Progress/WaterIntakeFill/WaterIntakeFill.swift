import SwiftUI

struct WaterEntry: Equatable {
    var label: String
    var time: String
}

/// Hydration tracker: a tank that fills with rippling water as the day's drinks are logged. The big
/// percentage rides the surface (and sinks under it once there is room), the millilitres in the
/// heading count along, every drink throws a splash and rocks the water, and bubbles keep rising.
/// Tap a chip or drag up and down on the tank to add water.
struct WaterIntakeFill: View {
    /// Controlled share of the daily goal, 0…1.
    var progress: Double? = nil
    /// Pours the day's drinks in one after another up to `amount`, holds, drains and starts over.
    var autoPlay = false
    var title = "Water"
    /// Millilitres drunk at the resting frame.
    var amount = 650
    /// The daily goal in millilitres.
    var goal = 2000
    var unit = "ml"
    /// The two quick-add chips, in millilitres.
    var quickAmounts = [150, 250]
    var customLabel = "Custom"
    /// What a tap on the custom pill adds.
    var customAmount = 500
    var logTitle = "Today"
    /// The latest log rows, newest first.
    var entries = [WaterEntry(label: "Water (+250)", time: "12:31"), WaterEntry(label: "Water (+150)", time: "12:31")]
    /// Called with the amount added and the new total when a chip is tapped or the level is dragged.
    var onAdd: ((Int, Int) -> Void)? = nil
    var onCustom: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = Simulation()
    /// The level under reduced motion, where nothing is integrated per frame.
    @State private var held = Tween(0)
    /// Keeps the timeline running for a change under reduced motion.
    @State private var settling = false
    /// Drinks added by hand, newest first: they push the given entries down the log.
    @State private var added: [WaterEntry] = []
    @State private var dragFrom: Double? = nil
    @State private var dragLast = 0.0
    @State private var ticks = 0

    /// Per-frame state of the water: the level chases its target like a critically damped spring.
    private final class Simulation {
        var last: Date? = nil
        var time = 0.0
        var clock = 0.0
        var looping = false
        /// Seconds the loop still waits after a touch.
        var hold = 0.0
        var target = 0.0
        var level = 0.0
        var velocity = 0.0
        /// 0…1: how hard the water was just disturbed.
        var energy = 0.0
        var splashAt = -10.0

        func advance(to date: Date, amount: Double, goal: Double) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            let now = time + dt
            if hold > 0 {
                hold -= dt
                // Time is up: the loop takes over again by draining what was added.
                if hold <= 0, looping { clock = Metrics.drainAt }
            } else if looping {
                clock += dt
                if clock >= Metrics.loop { clock -= Metrics.loop }
                let next = scriptTarget(clock, amount: amount)
                if next > target { splashAt = now }
                target = next
            }
            velocity += (Metrics.omega * Metrics.omega * (target - level) - 2 * Metrics.omega * velocity) * dt
            level += velocity * dt
            // Fast changes disturb the water; it calms down by itself.
            energy = max(energy * exp(-dt * 1.5), min(1, abs(velocity) / goal * 2.2))
            time = now
        }
    }

    private var controlled: Bool { progress != nil }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion }
    private var rest: Double { controlled ? min(1, max(0, progress ?? 0)) * Double(goal) : Double(amount) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let _ = reduceMotion ? () : sim.advance(to: date, amount: Double(amount), goal: Double(goal))
            let level = reduceMotion ? held.value(at: date) : sim.level
            let time = reduceMotion ? 0 : sim.time
            let energy = reduceMotion ? 0 : sim.energy
            let base = baseline(level, goal: Double(goal))
            let shownMl = Int((level / 10).rounded()) * 10
            let percent = min(100, (shownMl * 100 + goal / 2) / goal)
            let tilt = 0.085 * energy * sin(time * 5.4)
            let rows = Array((added + entries).prefix(2))

            ZStack {
                Canvas { context, _ in
                    drawBack(context, base: base, time: time, energy: energy)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                // The percentage and everything in front of it, clipped to the card.
                ZStack {
                    Text("\(percent)%")
                        .font(.system(size: 54, weight: .light))
                        .tracking(-1.5)
                        .monospacedDigit()
                        .foregroundStyle(.white.opacity(0.94))
                        .fixedSize()
                        .frame(width: 200, height: 60, alignment: .leading)
                        .rotationEffect(.radians(atan(tilt) * 0.7))
                        .position(
                            x: Metrics.percentLeft + 100,
                            y: min(surfaceAt(Metrics.percentX, base: base, time: time, energy: energy, layer: 0) + 6, Metrics.percentMaxTop) + 30
                        )
                    Canvas { context, _ in
                        drawFront(context, base: base, time: time, energy: energy, splash: time - sim.splashAt)
                    }
                }
                .frame(width: Metrics.width, height: Metrics.height)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular))
                .allowsHitTesting(false)

                VStack(alignment: .leading, spacing: 0) {
                    Text(title).foregroundStyle(.white).frame(height: 27)
                    (Text("today: ") + Text(grouped(shownMl)).foregroundColor(.white) + Text(" of \(grouped(goal))"))
                        .frame(height: 27)
                    Text(unit).frame(height: 27)
                }
                .font(.system(size: 24, weight: .medium))
                .tracking(-0.4)
                .monospacedDigit()
                .foregroundStyle(Color.rgba(214, 240, 248, 0.5))
                .lineSpacing(0)
                .lineLimit(1)
                .fixedSize()
                .frame(width: Metrics.width - 44, height: 81, alignment: .topLeading)
                .position(x: Metrics.width / 2, y: 20 + 40.5)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: "\(title) today"))
                .accessibilityValue(Text(verbatim: "\(shownMl) of \(goal) \(unit), \(percent)%"))

                // Dragging up and down on the tank raises and lowers the level in 50 ml steps.
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: Metrics.width, height: 140)
                    .position(x: Metrics.width / 2, y: 104 + 70)
                    .gesture(drag)

                HStack(spacing: 14) {
                    ForEach(quickAmounts, id: \.self) { ml in
                        Button {
                            add(ml)
                        } label: {
                            HStack(spacing: 7) {
                                ZStack {
                                    Circle().fill(Color.rgba(12, 44, 62, 0.62))
                                    Capsule().fill(.white.opacity(0.85)).frame(width: 9, height: 1.4)
                                    Capsule().fill(.white.opacity(0.85)).frame(width: 1.4, height: 9)
                                }
                                .frame(width: 28, height: 28)
                                Text("\(ml) \(unit)")
                                    .font(.system(size: 11.5, weight: .medium))
                                    .monospacedDigit()
                                    .foregroundStyle(.white.opacity(0.82))
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(PressStyle(scale: 0.94))
                        .accessibilityLabel("Add \(ml) \(unit)")
                    }
                }
                .fixedSize()
                .frame(width: Metrics.width - 40, height: 28, alignment: .leading)
                .position(x: Metrics.width / 2, y: 268)

                Button {
                    onCustom?()
                    add(customAmount)
                } label: {
                    Text(customLabel)
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x1d3a2c))
                        .frame(width: 80, height: 38)
                        .background(Capsule().fill(Color(hex: 0xf1e40c)))
                }
                .buttonStyle(PressStyle(scale: 0.97))
                .position(x: Metrics.width - 18 - 40, y: 268)

                VStack(alignment: .leading, spacing: 0) {
                    Text(logTitle)
                        .font(.system(size: 23, weight: .medium))
                        .tracking(-0.3)
                        .foregroundStyle(Color(hex: 0x1b1a24))
                        .frame(height: 28)
                        .padding(.bottom, 7)
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        HStack {
                            Text(row.label)
                            Spacer()
                            Text(row.time)
                        }
                        .font(.system(size: 11.5, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(Color(hex: 0x2a2834))
                        .frame(height: 24)
                    }
                }
                .lineLimit(1)
                .frame(width: Metrics.width - 48, height: 83, alignment: .topLeading)
                .position(x: Metrics.width / 2, y: Metrics.panelY + 13 + 41.5)
                .allowsHitTesting(false)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .sensoryFeedback(.selection, trigger: ticks)
        .onAppear { sync(initial: true) }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) { sync() }
        .onChange(of: amount) { sync() }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(0.35))
            settling = false
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                guard !controlled else { return }
                if dragFrom == nil {
                    dragFrom = sim.target.rounded()
                    dragLast = sim.target.rounded()
                }
                let raw = (dragFrom ?? 0) - value.translation.height / Metrics.dragSpan * Double(goal)
                let next = min(Double(goal), max(0, (raw / Metrics.dragStep).rounded() * Metrics.dragStep))
                guard next != dragLast else { return }
                dragLast = next
                move(to: next)
                sim.hold = Metrics.userHold
                ticks += 1
            }
            .onEnded { _ in
                if let from = dragFrom, dragLast != from { onAdd?(Int(dragLast - from), Int(dragLast)) }
                dragFrom = nil
            }
    }

    private func sync(initial: Bool = false) {
        sim.looping = auto
        sim.hold = 0
        sim.clock = 0
        sim.target = auto ? 0 : rest
        if initial {
            sim.level = sim.target
            held = Tween(rest)
        } else if reduceMotion {
            sim.level = rest
            held.retarget(rest, duration: 0.25, curve: Ease.out)
            settling = true
        }
    }

    private func move(to ml: Double) {
        sim.target = ml
        if reduceMotion {
            sim.level = ml
            held.retarget(ml, duration: 0.25, curve: Ease.out)
            settling = true
        }
    }

    private func add(_ ml: Int) {
        if controlled {
            onAdd?(ml, Int(rest.rounded()) + ml)
            return
        }
        let total = min(goal, Int(sim.target.rounded()) + ml)
        move(to: Double(total))
        sim.splashAt = sim.time
        sim.hold = Metrics.userHold
        added = Array(([WaterEntry(label: "\(title) (+\(ml))", time: entries.first?.time ?? "")] + added).prefix(2))
        onAdd?(ml, total)
    }

    /// Card, sky, ghosted tumbler and the body of the water: everything behind the percentage.
    private func drawBack(_ context: GraphicsContext, base: Double, time: Double, energy: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let card = Path(
            roundedRect: CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height),
            cornerRadius: Metrics.radius, style: .circular
        )

        var shadow = context
        shadow.addFilter(.blur(radius: 16))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: x + 14, y: y + 22, width: Metrics.width - 28, height: Metrics.height - 14),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(Color.rgba(4, 30, 48, 0.5))
        )

        var inside = context
        inside.clip(to: card)
        inside.fill(
            card,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x082c47), location: 0),
                    .init(color: Color(hex: 0x0f4f6c), location: 0.45),
                    .init(color: Color(hex: 0x1b7890), location: 1),
                ]),
                startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x + Metrics.width * 0.25, y: y + Metrics.height)
            )
        )
        // Two soft shafts of light falling across the screen.
        inside.fill(
            shaft(x: x, y: y, top: 150, topEnd: 236, bottom: 10, bottomEnd: 96),
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.07), .white.opacity(0.015)]),
                startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: y + Metrics.height)
            )
        )
        inside.fill(shaft(x: x, y: y, top: 268, topEnd: 300, bottom: 128, bottomEnd: 160), with: .color(.white.opacity(0.035)))

        var glass = inside
        glass.translateBy(x: x, y: y)
        glass.stroke(glassOutline, with: .color(.white.opacity(0.13)), lineWidth: 1.5)
        glass.stroke(glassRim, with: .color(.white.opacity(0.3)), style: StrokeStyle(lineWidth: 2, lineCap: .round))

        inside.fill(
            wave(x, y, base: base, time: time, energy: energy, layer: 1, lift: -5, closed: true),
            with: .color(Color.rgba(104, 208, 226, 0.42))
        )
        inside.fill(
            wave(x, y, base: base, time: time, energy: energy, layer: 0, lift: 0, closed: true),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x3fb0c9), Color(hex: 0x1f89aa)]),
                startPoint: CGPoint(x: x, y: y + Metrics.yFull), endPoint: CGPoint(x: x, y: y + Metrics.panelY)
            )
        )
    }

    /// What lies in front of the percentage: a tinted front wave, bubbles, the splash, and the log panel.
    private func drawFront(_ context: GraphicsContext, base: Double, time: Double, energy: Double, splash: Double) {
        context.fill(
            wave(0, 0, base: base, time: time, energy: energy, layer: 2, lift: 4, closed: true),
            with: .color(Color.rgba(74, 196, 220, 0.3))
        )
        context.stroke(
            wave(0, 0, base: base, time: time, energy: energy, layer: 0, lift: 0, closed: false),
            with: .color(Color.rgba(226, 250, 255, 0.6)), lineWidth: 1.3
        )

        var bubbles = Path()
        for b in bubbleSpecs {
            let bx = b.x * Metrics.width + 4 * sin(time * 1.3 + b.phase)
            let by = Metrics.panelY - (time * b.speed + b.phase).truncatingRemainder(dividingBy: Metrics.bubbleSpan)
            // A bubble exists only under water: it vanishes as it reaches the surface.
            if by > surfaceAt(bx, base: base, time: time, energy: energy, layer: 0) + b.r + 5 {
                bubbles.addEllipse(in: CGRect(x: bx - b.r, y: by - b.r, width: b.r * 2, height: b.r * 2))
            }
        }
        context.stroke(bubbles, with: .color(.white.opacity(0.38)), lineWidth: 1)

        if splash > 0, splash < Metrics.dropLife {
            var drops = Path()
            for d in dropSpecs {
                let dx = d.x * Metrics.width + d.vx * splash
                let rise = d.vy * splash - 0.5 * Metrics.gravity * splash * splash
                guard rise > 0 else { continue }
                let r = d.r * (1 - 0.5 * (splash / Metrics.dropLife))
                let dy = surfaceAt(dx, base: base, time: time, energy: energy, layer: 0) - rise
                drops.addEllipse(in: CGRect(x: dx - r, y: dy - r, width: r * 2, height: r * 2))
            }
            context.fill(drops, with: .color(Color.rgba(204, 244, 252, 0.92)))
        }

        context.fill(
            Path(
                roundedRect: CGRect(x: 0, y: Metrics.panelY, width: Metrics.width, height: Metrics.height - Metrics.panelY + 40),
                cornerRadius: 30, style: .circular
            ),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xf1e6ef), Color(hex: 0xdccfe8)]),
                startPoint: CGPoint(x: 0, y: Metrics.panelY), endPoint: CGPoint(x: Metrics.width, y: Metrics.height)
            )
        )
    }

    private struct PressStyle: ButtonStyle {
        let scale: Double

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? scale : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

private enum Metrics {
    static let width = 320.0
    static let height = 404.0
    static let radius = 34.0
    /// The canvas overflows the card by this much on every side to fit its shadow.
    static let bleed = 36.0

    // Where the water's surface sits when the tank is empty and when the goal is reached.
    static let yEmpty = 292.0
    static let yFull = 112.0
    /// Top of the lavender "Today" panel.
    static let panelY = 304.0
    // The percentage rides just under the surface, but never sinks below the chips.
    static let percentLeft = 20.0
    static let percentMaxTop = 190.0
    static let percentX = 74.0
    // Vertical drag distance that spans the whole goal, and the step the drag snaps to.
    static let dragSpan = yEmpty - yFull
    static let dragStep = 50.0

    // autoPlay loop (seconds): three drinks, a long look at the result, then the tank drains.
    static let pourAt = [0.6, 2.0, 3.4]
    static let pourShare = [0.385, 0.615, 1]
    static let drainAt = 7.6
    static let loop = 8.8
    /// After a touch the loop waits this long before it drains and starts over.
    static let userHold = 6.0

    /// The level follows its target like a critically damped spring.
    static let omega = 6.0

    static let dropLife = 0.85
    static let gravity = 520.0
    static let bubbleSpan = 210.0
}

private struct Drop {
    let x: Double
    let vx: Double
    let vy: Double
    let r: Double
}

private let dropSpecs = [
    Drop(x: 0.16, vx: -22, vy: 120, r: 2.6), Drop(x: 0.27, vx: 14, vy: 165, r: 2.1),
    Drop(x: 0.38, vx: -8, vy: 205, r: 3.0), Drop(x: 0.49, vx: 20, vy: 150, r: 1.8),
    Drop(x: 0.58, vx: -16, vy: 190, r: 2.4), Drop(x: 0.69, vx: 6, vy: 135, r: 2.0),
    Drop(x: 0.8, vx: 24, vy: 175, r: 2.8), Drop(x: 0.9, vx: -12, vy: 110, r: 1.7),
]

private struct Bubble {
    let x: Double
    let speed: Double
    let r: Double
    let phase: Double
}

private let bubbleSpecs = [
    Bubble(x: 0.08, speed: 22, r: 2.2, phase: 10), Bubble(x: 0.2, speed: 31, r: 1.4, phase: 120),
    Bubble(x: 0.34, speed: 18, r: 2.8, phase: 60), Bubble(x: 0.46, speed: 27, r: 1.6, phase: 170),
    Bubble(x: 0.57, speed: 20, r: 2.0, phase: 95), Bubble(x: 0.68, speed: 34, r: 1.3, phase: 30),
    Bubble(x: 0.79, speed: 24, r: 2.5, phase: 150), Bubble(x: 0.91, speed: 29, r: 1.7, phase: 75),
]

/// A tumbler ghosted into the background, as in the reference.
private let glassOutline: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 96, y: 304))
    p.addLine(to: CGPoint(x: 96, y: 152))
    p.addQuadCurve(to: CGPoint(x: 118, y: 130), control: CGPoint(x: 96, y: 130))
    p.addLine(to: CGPoint(x: 202, y: 130))
    p.addQuadCurve(to: CGPoint(x: 224, y: 152), control: CGPoint(x: 224, y: 130))
    p.addLine(to: CGPoint(x: 224, y: 304))
    return p
}()

private let glassRim: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 112, y: 130))
    p.addLine(to: CGPoint(x: 208, y: 130))
    return p
}()

private func shaft(x: Double, y: Double, top: Double, topEnd: Double, bottom: Double, bottomEnd: Double) -> Path {
    var p = Path()
    p.move(to: CGPoint(x: x + top, y: y))
    p.addLine(to: CGPoint(x: x + topEnd, y: y))
    p.addLine(to: CGPoint(x: x + bottomEnd, y: y + Metrics.height))
    p.addLine(to: CGPoint(x: x + bottom, y: y + Metrics.height))
    p.closeSubpath()
    return p
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

/// Y of the resting surface for `level` millilitres.
private func baseline(_ level: Double, goal: Double) -> Double {
    Metrics.yEmpty - (Metrics.yEmpty - Metrics.yFull) * clamp01(level / goal)
}

/// Y of the surface at `x`. `energy` (0…1) is how hard the water was just disturbed: it raises the
/// waves and rocks the whole surface from side to side. `layer` offsets the phase of each wave.
private func surfaceAt(_ x: Double, base: Double, time: Double, energy: Double, layer: Double) -> Double {
    let amp = 1.7 + 6.5 * energy
    let slope = 0.085 * energy * sin(time * 5.4)
    let dir = layer == 1 ? -1.0 : 1.0
    return base
        + slope * (x - Metrics.width / 2)
        + amp * sin(x * 0.026 + time * 2.2 * dir + layer * 1.9)
        + amp * 0.55 * sin(x * 0.047 - time * 3.1 * dir + layer * 0.7)
}

private func wave(_ ox: Double, _ oy: Double, base: Double, time: Double, energy: Double, layer: Double, lift: Double, closed: Bool) -> Path {
    var path = Path()
    for i in 0...32 {
        let x = Metrics.width * Double(i) / 32
        let point = CGPoint(x: ox + x, y: oy + surfaceAt(x, base: base, time: time, energy: energy, layer: layer) + lift)
        if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
    }
    if closed {
        path.addLine(to: CGPoint(x: ox + Metrics.width, y: oy + Metrics.height))
        path.addLine(to: CGPoint(x: ox, y: oy + Metrics.height))
        path.closeSubpath()
    }
    return path
}

/// Millilitres the autoPlay loop wants in the tank at `t` seconds.
private func scriptTarget(_ t: Double, amount: Double) -> Double {
    if t >= Metrics.drainAt { return 0 }
    var share = 0.0
    for (index, at) in Metrics.pourAt.enumerated() where t >= at { share = Metrics.pourShare[index] }
    return (amount * share / 10).rounded() * 10
}

private func grouped(_ n: Int) -> String {
    var digits = String(n)
    var out = ""
    while digits.count > 3 {
        out = "," + digits.suffix(3) + out
        digits = String(digits.dropLast(3))
    }
    return digits + out
}

#Preview {
    ZStack {
        Color(hex: 0xb9cf7a).ignoresSafeArea()
        WaterIntakeFill(autoPlay: true)
    }
}
