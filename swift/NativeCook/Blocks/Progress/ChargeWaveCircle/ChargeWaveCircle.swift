import SwiftUI

/// Dark green charging screen: a "speed up" chip, a glowing circular gauge with the charge level
/// and a liquid filling its foot, the charging modes and the time read-outs. The liquid is two
/// waves that never stop travelling; it sloshes and tilts while the level moves and rocks itself
/// calm afterwards, bubbles rise through it, a pulse of light keeps leaving the ring while the
/// bolt flashes with it, and the chip's glow breathes under a passing sheen. Tapping the chip
/// gives the liquid a kick.
struct ChargeWaveCircle: View {
    /// Controlled charge level, 0…1. Leave `nil` to show the reference's 18% (or the autoPlay loop).
    var progress: Double? = nil
    /// Charges by itself from empty to 18%, holds, drains and starts over.
    var autoPlay = false
    var chipLabel = "Speed up charging"
    var time = "10:23"
    var date = "11/28 Sunday"
    var caption = "Full Charge takes about"
    /// Minutes a charge from empty to full takes; the remaining time is derived from the level.
    var fullChargeMinutes = 227
    var onSpeedUp: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (waves, pulses).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// When a controlled value last changed / the chip last kicked the liquid.
    @State private var movedAt = Date.distantPast
    @State private var kickedAt = Date.distantPast
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var target: Double { clamp01(progress ?? Metrics.rest) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let seconds = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let clock = clock(at: date)
            let value = clamp01(controlled ? held.value(at: date) : loopValue(clock))
            let activity = activity(at: date, clock: clock)
            let percent = Int((value * 100).rounded())
            let remaining = Int((Double(fullChargeMinutes) * (1 - Double(percent) / 100)).rounded())

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: seconds, activity: activity, landed: landed(at: date, clock: clock))
                }
                .padding(-Metrics.bleed)

                content(percent: percent, remaining: remaining, activity: activity)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Charging. \(caption) \(remaining / 60) hours \(remaining % 60) minutes")
            .accessibilityValue("\(percent)%")
            .contentShape(Rectangle())
            .onTapGesture { restart() }
            .overlay(alignment: .topLeading) {
                Button {
                    if !reduceMotion { kickedAt = .now }
                    onSpeedUp()
                } label: {
                    chip(time: seconds)
                }
                .buttonStyle(ChipStyle())
                .offset(x: Metrics.chipX, y: Metrics.chipY)
            }
        }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            movedAt = .now
            held.retarget(target, duration: reduceMotion ? 0.2 : 0.9, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(2.6))
            settling = false
        }
    }

    private func sync() {
        runStart = autoPlay && !controlled && !reduceMotion ? .now : nil
        if held.duration == 0 { held = Tween(target) }
    }

    private func restart() {
        guard !controlled, !reduceMotion else { return }
        runStart = .now
    }

    /// Position in the autoPlay loop, in seconds.
    private func clock(at date: Date) -> Double {
        guard let runStart else { return Metrics.restTime }
        let elapsed = date.timeIntervalSince(runStart)
        // A tapped, one-off run stops at the resting frame.
        return autoPlay ? elapsed.truncatingRemainder(dividingBy: Metrics.loop) : min(elapsed, Metrics.restTime)
    }

    /// 0…1: how fast the level is moving.
    private func activity(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 0 }
        if !controlled { return loopActivity(clock) }
        let u = date.timeIntervalSince(movedAt) / 0.8
        return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
    }

    /// Seconds since the level last landed, or since the last boost; large when nothing happened.
    private func landed(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 10 }
        let kick = date.timeIntervalSince(kickedAt)
        if !controlled {
            return min(kick, clock < Metrics.loop - Metrics.reset && clock >= Metrics.landed ? clock - Metrics.landed : 10)
        }
        return min(kick, max(0, date.timeIntervalSince(movedAt) - 0.7))
    }

    private func content(percent: Int, remaining: Int, activity: Double) -> some View {
        let readout = Font.system(size: 20)
        let unit = Font.system(size: 9.5, weight: .medium)
        let note = Font.system(size: 11.8)
        let bright = Color(hex: 0xdfe2df)
        let dim = Color(hex: 0x7d827e)
        let half = Metrics.centerX

        return ZStack {
            Text("\(percent)")
                .font(.system(size: 56, weight: .medium))
                .kerning(0.5)
                .monospacedDigit()
                .foregroundStyle(Color(hex: 0xe6e8e6))
                .fixedSize()
                // The number swells a little while the level moves.
                .scaleEffect(1 + 0.05 * activity)
                .position(x: Metrics.centerX, y: Metrics.centerY)
            Text("%")
                .font(.system(size: 15.5))
                .foregroundStyle(Color(hex: 0xf0f4f0))
                .position(x: Metrics.centerX, y: 215.3)

            Text(time)
                .font(readout)
                .monospacedDigit()
                .foregroundStyle(bright)
                .fixedSize()
                .position(x: half / 2, y: 379.5)
            Text(date)
                .font(note)
                .foregroundStyle(dim)
                .lineLimit(1)
                .fixedSize()
                .position(x: half / 2, y: 403)
            (Text("\(remaining / 60)").font(readout) + Text(" H ").font(unit)
                + Text("\(remaining % 60)").font(readout) + Text(" M").font(unit))
                .monospacedDigit()
                .foregroundStyle(bright)
                .fixedSize()
                .position(x: half * 1.5, y: 379.5)
            Text(caption)
                .font(note)
                .foregroundStyle(dim)
                .lineLimit(1)
                .fixedSize()
                .position(x: half * 1.5, y: 403)
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    /// The "speed up" chip: its glow breathes and its bolt flickers; a sheen crosses it now and then.
    private func chip(time: Double) -> some View {
        let ambient = reduceMotion ? 0.0 : 1.0
        let pad = 12.0
        return ZStack(alignment: .leading) {
            Canvas { context, _ in
                let rect = CGRect(x: pad, y: pad, width: Metrics.chipW, height: Metrics.chipH)
                var glow = context
                glow.opacity = 0.6 + 0.25 * ambient * sin(time * 2.1)
                glow.drawLayer { layer in
                    layer.addFilter(.blur(radius: 4.5))
                    layer.stroke(capsule(rect), with: .color(.rgba(60, 214, 84, 0.9)), lineWidth: 3)
                }
                context.fill(
                    capsule(rect),
                    with: .linearGradient(
                        Gradient(colors: [Color(hex: 0x1f3f27), Color(hex: 0x1a3321)]),
                        startPoint: CGPoint(x: 0, y: pad), endPoint: CGPoint(x: 0, y: pad + Metrics.chipH)
                    )
                )
                if ambient > 0 {
                    var sheen = context
                    sheen.clip(to: capsule(rect))
                    let x = pad - 40 + (Metrics.chipW + 80) * min(1, (time + 0.8).truncatingRemainder(dividingBy: 3.8) / 1.1)
                    sheen.translateBy(x: x, y: 0)
                    sheen.concatenate(CGAffineTransform(a: 1, b: 0, c: -0.35, d: 1, tx: 0, ty: 0))
                    sheen.fill(
                        Path(CGRect(x: -14, y: pad - 4, width: 28, height: Metrics.chipH + 8)),
                        with: .linearGradient(
                            Gradient(colors: [.rgba(190, 255, 205, 0), .rgba(190, 255, 205, 0.16), .rgba(190, 255, 205, 0)]),
                            startPoint: CGPoint(x: -14, y: 0), endPoint: CGPoint(x: 14, y: 0)
                        )
                    )
                }
                context.stroke(capsule(rect.insetBy(dx: 0.6, dy: 0.6)), with: .color(Color(hex: 0x2f9a3f)), lineWidth: 1.2)

                var bolt = context
                bolt.translateBy(x: pad + 20.2, y: pad + Metrics.chipH / 2)
                bolt.scaleBy(x: 0.88, y: 0.88)
                var halo = bolt
                halo.opacity = 0.6 + 0.4 * ambient * sin(time * 5.3) * sin(time * 1.9)
                halo.drawLayer { layer in
                    layer.addFilter(.blur(radius: 3.2))
                    layer.fill(Metrics.bolt, with: .color(Color(hex: 0xd9ff3c)))
                }
                bolt.fill(Metrics.bolt, with: .color(Color(hex: 0xeaff58)))
            }
            .padding(-pad)

            Text(chipLabel)
                .font(.system(size: 9.8))
                .foregroundStyle(Color(hex: 0xe3ece4))
                .lineLimit(1)
                .fixedSize()
                .padding(.leading, 40.5)
        }
        .frame(width: Metrics.chipW, height: Metrics.chipH)
    }

    private func draw(_ base: GraphicsContext, value: Double, time: Double, activity: Double, landed: Double) {
        let ambient = reduceMotion ? 0.0 : 1.0
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)
        let cx = Metrics.centerX
        let cy = Metrics.centerY
        let center = CGPoint(x: cx, y: cy)
        let ringR = Metrics.ring
        let liquidR = Metrics.liquid
        let bowlR = Metrics.bowl
        func circle(_ c: CGPoint, _ r: Double) -> Path {
            Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
        }
        func polar(_ radius: Double, _ degrees: Double) -> CGPoint {
            CGPoint(x: cx + radius * cos(degrees * .pi / 180), y: cy + radius * sin(degrees * .pi / 180))
        }
        let green = Color(hex: 0x74e592)
        let dim = Color(hex: 0x33573f)

        // The bowl the gauge sits in: it shows towards its foot.
        context.fill(
            circle(center, bowlR),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(30, 54, 37, 0), location: 0),
                    .init(color: .rgba(30, 54, 37, 0.75), location: 0.5),
                    .init(color: .rgba(30, 56, 38, 1), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: cy - 70), endPoint: CGPoint(x: 0, y: cy + bowlR)
            )
        )
        // The selected mode's tab: a slice of the bowl under the gauge.
        var tab = Path()
        tab.addArc(center: center, radius: 97, startAngle: .degrees(74), endAngle: .degrees(106), clockwise: false)
        tab.addLine(to: polar(bowlR, 103.5))
        tab.addArc(center: center, radius: bowlR, startAngle: .degrees(103.5), endAngle: .degrees(76.5), clockwise: true)
        tab.closeSubpath()
        context.fill(
            tab,
            with: .linearGradient(
                Gradient(colors: [.rgba(52, 104, 64, 0.75), .rgba(40, 84, 52, 0.45)]),
                startPoint: CGPoint(x: 0, y: cy + 97), endPoint: CGPoint(x: 0, y: cy + bowlR)
            )
        )
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 9))
            layer.fill(circle(CGPoint(x: cx, y: cy + 3), Metrics.outer + 6), with: .color(.rgba(6, 12, 8, 0.75)))
        }

        // Mode icons
        var snow = context
        snow.translateBy(x: 82.7, y: 263.7)
        snow.stroke(Metrics.snowflake, with: .color(dim), style: StrokeStyle(lineWidth: 2.3, lineCap: .round))
        var rocket = context
        rocket.translateBy(x: 247.3, y: 264.2)
        rocket.rotate(by: .degrees(38))
        rocket.stroke(Metrics.rocket, with: .color(dim), style: StrokeStyle(lineWidth: 2.1, lineCap: .round, lineJoin: .round))
        rocket.fill(circle(CGPoint(x: 0, y: -2.4), 2.1), with: .color(dim))
        var plug = context
        plug.translateBy(x: cx, y: 294)
        var plugGlow = plug
        plugGlow.opacity = 0.55 + 0.3 * ambient * sin(time * 2.3)
        plugGlow.drawLayer { layer in
            layer.addFilter(.blur(radius: 4))
            layer.stroke(Metrics.plug, with: .color(.rgba(96, 228, 128, 0.8)), style: StrokeStyle(lineWidth: 3.4, lineCap: .round, lineJoin: .round))
        }
        plug.stroke(Metrics.plug, with: .color(green), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
        var plugBolt = plug
        plugBolt.translateBy(x: 0, y: 1.6)
        plugBolt.scaleBy(x: 0.56, y: 0.56)
        plugBolt.fill(Metrics.bolt, with: .color(green))

        // Gauge
        context.stroke(circle(center, Metrics.outer), with: .color(.rgba(86, 180, 110, 0.42)), lineWidth: 1.1)
        // A pulse of light leaves the ring towards the outer one, and the bolt flashes with it.
        let pulsePhase = time.truncatingRemainder(dividingBy: Metrics.pulse) / 1.25
        if ambient > 0, pulsePhase < 1 {
            var pulse = context
            pulse.opacity = 0.55 * (1 - pulsePhase)
            pulse.drawLayer { layer in
                layer.addFilter(.blur(radius: 1.4))
                layer.stroke(circle(center, ringR + 13 * (1 - pow(1 - pulsePhase, 2))), with: .color(Color(hex: 0x8df0a8)), lineWidth: 1.4)
            }
        }
        context.fill(
            circle(center, ringR),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x17241b), location: 0),
                    .init(color: Color(hex: 0x18281d), location: 0.62),
                    .init(color: Color(hex: 0x1e3a27), location: 0.9),
                    .init(color: Color(hex: 0x2a5a38), location: 1),
                ]),
                center: center, startRadius: 0, endRadius: ringR
            )
        )

        // The liquid sloshes while the level moves and settles with a damped rocking once it lands.
        let shown = clamp01(value / 0.05)
        let settle = landed > 0 && landed < 2 ? exp(-2.6 * landed) : 0
        let swell = shown * (ambient * 5.4 + 3.2 * activity + 3 * settle)
        let tilt = shown * (0.07 * activity * sin(time * 8) + 0.11 * settle * sin(10 * landed))
        var glass = context
        glass.clip(to: circle(center, liquidR))
        glass.fill(
            liquid(level: value + 0.018, amplitude: swell * 0.85, tilt: -tilt * 0.6, phase: -time * 1.3 + 2.1, wavelength: 118),
            with: .color(.rgba(120, 226, 148, 0.5))
        )
        glass.fill(
            liquid(level: value, amplitude: swell, tilt: tilt, phase: time * 1.7, wavelength: 99),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x45c768), Color(hex: 0x2fa54d)]),
                startPoint: CGPoint(x: 0, y: cy + 30), endPoint: CGPoint(x: 0, y: cy + liquidR)
            )
        )
        // Bubbles rise through the liquid and vanish under the surface.
        let surface = cy + liquidR - 2 * liquidR * value
        let bottom = cy + liquidR
        if ambient > 0, bottom - surface >= 8 {
            var bubbles = Path()
            for i in 0..<6 {
                let cycle = time / (2.1 + 0.35 * Double(i)) + Double(i) * 0.37
                let age = cycle - floor(cycle)
                let x = cx + (Double(i) - 2.5) * 15 + 4 * sin(time * 2 + Double(i))
                let y = bottom - 4 - (bottom - surface - 2) * age
                let r = (0.9 + 0.25 * Double(i % 3)) * sin(.pi * age)
                bubbles.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            }
            glass.fill(bubbles, with: .color(.rgba(214, 255, 224, 0.55)))
        }
        // Shade under the rim, so the liquid sits inside the glass.
        glass.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            layer.stroke(circle(center, liquidR + 3), with: .color(.rgba(10, 24, 14, 0.55)), lineWidth: 9)
        }

        // The ring's bloom breathes, brightens while charging and flashes as the level lands.
        let flashU = landed / 0.8
        let flash = flashU > 0 && flashU < 1 ? 0.35 * (1 - flashU) : 0
        var bloom = context
        bloom.opacity = min(1, 0.55 + 0.12 * ambient * sin(time * 1.7) + 0.25 * activity + flash)
        bloom.drawLayer { layer in
            layer.addFilter(.blur(radius: 6))
            layer.stroke(circle(center, ringR), with: .color(.rgba(104, 236, 138, 0.85)), lineWidth: 5.5)
        }
        context.stroke(circle(center, ringR), with: .color(Color(hex: 0x8ceca6)), lineWidth: 1.8)

        // Charging bolt
        let spark = ambient * exp(-3.2 * pulsePhase)
        var bolt = context
        bolt.translateBy(x: cx, y: 113.3)
        bolt.scaleBy(x: 1 + 0.14 * spark, y: 1 + 0.14 * spark)
        var halo = bolt
        halo.opacity = 0.25 + 0.75 * spark
        halo.drawLayer { layer in
            layer.addFilter(.blur(radius: 4))
            layer.fill(Metrics.bolt, with: .color(Color(hex: 0xbaffc9)))
        }
        let white = GraphicsContext.Shading.color(Color(hex: 0xf1f6f1))
        bolt.fill(Metrics.bolt, with: white)
        bolt.stroke(Metrics.bolt, with: white, style: StrokeStyle(lineWidth: 0.9, lineJoin: .round))

        // Divider between the read-outs
        context.fill(Path(CGRect(x: cx - 0.5, y: 375, width: 1, height: 25)), with: .color(.white.opacity(0.14)))
    }

    /// One layer of the liquid: a travelling wave on a surface at `level`, tilted by `tilt`, closed
    /// along the bottom of the gauge. The gauge's circle clips it.
    private func liquid(level: Double, amplitude: Double, tilt: Double, phase: Double, wavelength: Double) -> Path {
        let cx = Metrics.centerX
        let r = Metrics.liquid
        let y0 = Metrics.centerY + r - 2 * r * clamp01(level)
        let left = cx - r - 2
        let right = cx + r + 2
        let steps = 26
        var path = Path()
        path.move(to: CGPoint(x: left, y: Metrics.centerY + r + 4))
        for i in 0...steps {
            let x = left + (right - left) * Double(i) / Double(steps)
            path.addLine(to: CGPoint(x: x, y: y0 + amplitude * sin((x - cx) / wavelength * .pi * 2 + phase) + tilt * (x - cx)))
        }
        path.addLine(to: CGPoint(x: right, y: Metrics.centerY + r + 4))
        path.closeSubpath()
        return path
    }
}

private enum Metrics {
    static let width = 330.0
    static let height = 414.0
    /// The canvas overflows the component by this much on every side to fit the chip's glow.
    static let bleed = 20.0

    static let centerX = 165.0
    static let centerY = 167.0
    static let bowl = 155.0
    static let outer = 90.7
    static let ring = 84.2
    static let liquid = 82.8

    static let chipX = 94.8
    static let chipY = 12.6
    static let chipW = 140.4
    static let chipH = 32.9

    /// Value shown in the reference, and the autoPlay loop built around it (seconds).
    static let rest = 0.18
    static let start = 0.5
    static let fill = 2.3
    static let hold = 4.8
    static let reset = 0.9
    static let loop = start + fill + hold + reset
    static let landed = start + fill
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = landed + 2
    /// A pulse leaves the ring every `pulse` seconds while it charges.
    static let pulse = 2.6

    static let bolt: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 1.6, y: -9))
        path.addLine(to: CGPoint(x: -5.6, y: 1.4))
        path.addLine(to: CGPoint(x: -0.5, y: 1.4))
        path.addLine(to: CGPoint(x: -1.9, y: 9))
        path.addLine(to: CGPoint(x: 5.6, y: -1.5))
        path.addLine(to: CGPoint(x: 0.5, y: -1.5))
        path.closeSubpath()
        return path
    }()

    static let plug: Path = {
        var path = Path()
        path.move(to: CGPoint(x: -9.4, y: -5.6))
        path.addLine(to: CGPoint(x: 9.4, y: -5.6))
        path.addLine(to: CGPoint(x: 9.4, y: 1.8))
        path.addCurve(to: CGPoint(x: 0, y: 11.2), control1: CGPoint(x: 9.4, y: 7.6), control2: CGPoint(x: 5.2, y: 11.2))
        path.addCurve(to: CGPoint(x: -9.4, y: 1.8), control1: CGPoint(x: -5.2, y: 11.2), control2: CGPoint(x: -9.4, y: 7.6))
        path.closeSubpath()
        for x in [-4.6, 4.6] {
            path.move(to: CGPoint(x: x, y: -11.2))
            path.addLine(to: CGPoint(x: x, y: -5.6))
        }
        return path
    }()

    static let rocket: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: -12.5))
        path.addCurve(to: CGPoint(x: 4.2, y: 8), control1: CGPoint(x: 6, y: -7.5), control2: CGPoint(x: 7, y: 1))
        path.addLine(to: CGPoint(x: -4.2, y: 8))
        path.addCurve(to: CGPoint(x: 0, y: -12.5), control1: CGPoint(x: -7, y: 1), control2: CGPoint(x: -6, y: -7.5))
        path.closeSubpath()
        for side in [-1.0, 1.0] {
            path.move(to: CGPoint(x: side * 4.9, y: 3.6))
            path.addLine(to: CGPoint(x: side * 9.2, y: 9.8))
            path.addLine(to: CGPoint(x: side * 4.2, y: 8.4))
        }
        path.move(to: CGPoint(x: -2, y: 11))
        path.addQuadCurve(to: CGPoint(x: 2, y: 11), control: CGPoint(x: 0, y: 14.6))
        return path
    }()

    static let snowflake: Path = {
        var path = Path()
        for arm in 0..<6 {
            let a = (Double(arm) * 60 + 30) * .pi / 180
            path.move(to: .zero)
            path.addLine(to: CGPoint(x: 13 * cos(a), y: 13 * sin(a)))
            // Two short barbs near the tip of every arm.
            let base = CGPoint(x: 7.6 * cos(a), y: 7.6 * sin(a))
            for side in [-1.0, 1.0] {
                let b = a + side * 52 * .pi / 180
                path.move(to: base)
                path.addLine(to: CGPoint(x: base.x + 4.6 * cos(b), y: base.y + 4.6 * sin(b)))
            }
        }
        return path
    }()
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Charge level at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.landed { return Metrics.rest * easeInOutCubic((t - Metrics.start) / Metrics.fill) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

/// 0…1: how fast the level is moving at `t` seconds into the loop.
private func loopActivity(_ t: Double) -> Double {
    if t >= Metrics.loop - Metrics.reset { return sin(.pi * clamp01((t - (Metrics.loop - Metrics.reset)) / Metrics.reset)) }
    let u = (t - Metrics.start) / Metrics.fill
    return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
}

private struct ChipStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Capsule())
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

#Preview {
    ZStack {
        Color(hex: 0x141a16).ignoresSafeArea()
        ChargeWaveCircle(autoPlay: true)
    }
}
