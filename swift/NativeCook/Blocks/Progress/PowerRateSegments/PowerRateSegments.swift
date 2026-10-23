import SwiftUI

/// Dark glass settings menu with a glowing green panel: a power rate counter, a change chip and a
/// row of small segments, over streaks of green light. The digits roll like an odometer, the
/// segments light one after another with a flickering glow and a pulse that runs along them, and
/// the light streaks slide, swell with the rate and carry a few drifting motes.
struct PowerRateSegments: View {
    /// Controlled power rate, 0…1. Leave `nil` to show the reference's 24.31% (or the autoPlay loop).
    var progress: Double? = nil
    /// Ramps from 0 to 24.31% by itself, holds, drops and starts over.
    var autoPlay = false
    var label = "GPU Actual Power Rate"
    /// Text of the change chip.
    var change = "-14.83%"
    var selectedRow = "Power Usage"
    var settingsRow = "Settings"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (streaks, flicker, pulse).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var target: Double { clamp01(progress ?? Metrics.rest) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                content(value: value)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(Rectangle())
        .onTapGesture { restart() }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            held.retarget(target, duration: reduceMotion ? 0.2 : 0.9, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1))
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

    private func content(value: Double) -> some View {
        ZStack {
            Text(selectedRow)
                .font(.system(size: 15.5, weight: .medium))
                .kerning(0.2)
                .foregroundStyle(.white)
                .lineLimit(1)
                .leading(57, centerY: Metrics.rowY + Metrics.rowHeight / 2)
            Text(settingsRow)
                .font(.system(size: 15.5))
                .kerning(0.2)
                .foregroundStyle(.white.opacity(0.56))
                .lineLimit(1)
                .leading(57, centerY: Metrics.settingsY)

            HStack(spacing: 0) {
                digit(4, value: value, optional: true)
                digit(3, value: value, optional: true)
                digit(2, value: value)
                Text(".")
                digit(1, value: value)
                digit(0, value: value)
                Text("%")
            }
            .font(.system(size: 22, weight: .medium))
            .foregroundStyle(.white)
            .leading(61, centerY: Metrics.panelY + 28.5)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(label)
            .accessibilityValue(String(format: "%.2f%%", target * 100))

            Text(change)
                .font(.system(size: 11.5))
                .monospacedDigit()
                .foregroundStyle(Color(hex: 0x3bd583))
                .lineLimit(1)
                .position(x: 243.5, y: Metrics.panelY + 21.5)
            Text(label)
                .font(.system(size: 15))
                .kerning(0.2)
                .foregroundStyle(Color.rgba(232, 244, 238, 0.5))
                .lineLimit(1)
                .leading(30.5, centerY: Metrics.panelY + 62.5)
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    /// One wheel of the counter. Hundreds and tens leave the row while they would only show a leading zero.
    private func digit(_ place: Int, value: Double, optional: Bool = false) -> some View {
        let unit = pow(10, Double(place - 2))
        let shown = optional ? clamp01((value * 100 - unit * 0.96) / (unit * 0.04)) : 1
        return VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .monospacedDigit()
                    .frame(width: Metrics.digitWidth, height: Metrics.digitHeight)
            }
        }
        .offset(y: -Metrics.digitHeight * digitPosition(clamp01(value), place: place))
        .frame(width: Metrics.digitWidth * shown, height: Metrics.digitHeight, alignment: .topLeading)
        .clipped()
        .opacity(shown)
    }

    private func draw(_ base: GraphicsContext, value: Double, time: Double) {
        let ambient = reduceMotion ? 0.0 : 1.0
        let width = Metrics.width
        let height = Metrics.height
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)

        // Light fanning out from the bottom right corner: it swells with the power rate and breathes a little.
        var lights = context
        lights.translateBy(x: width, y: height)
        lights.rotate(by: .radians(Metrics.tilt))
        lights.opacity = 0.5 + 0.42 * clamp01(value / Metrics.rest) + ambient * 0.08 * sin(time * 1.3)
        lights.fillEllipse(
            center: CGPoint(x: -50, y: -24), radii: CGSize(width: 330, height: 118),
            gradient: Gradient(colors: [.rgba(22, 214, 110, 0.85), .rgba(22, 214, 110, 0.34), .rgba(22, 214, 110, 0)])
        )
        lights.fillEllipse(
            center: CGPoint(x: 10, y: 0), radii: CGSize(width: 190, height: 74),
            gradient: Gradient(colors: [.rgba(44, 246, 172, 0.95), .rgba(44, 246, 172, 0.32), .rgba(44, 246, 172, 0)])
        )
        for streak in Metrics.streaks {
            let alpha = min(1, streak.alpha * 1.3) * (0.72 + ambient * 0.28 * sin(time * streak.speed * 5.3 + streak.phase * 1.7))
            let color: (Double) -> Color = { streak.mint ? .rgba(150, 255, 205, $0) : .rgba(26, 224, 116, $0) }
            lights.fillEllipse(
                center: CGPoint(x: streak.a + ambient * streak.amp * sin(time * streak.speed * 2 + streak.phase), y: -streak.d),
                radii: CGSize(width: streak.l, height: streak.t),
                gradient: Gradient(colors: [color(alpha), color(0)])
            )
        }
        if ambient > 0 {
            for (index, mote) in Metrics.motes.enumerated() {
                let lap = (time * mote.speed + mote.phase).truncatingRemainder(dividingBy: 1)
                let alpha = sin(.pi * lap) * (0.55 + 0.45 * sin(time * 3.1 + Double(index) * 1.9))
                lights.fill(
                    Path(ellipseIn: CGRect(x: -300 + 330 * lap - mote.r, y: -mote.d - mote.r, width: mote.r * 2, height: mote.r * 2)),
                    with: .color(Color(hex: 0xc4ffe4, opacity: max(0, alpha)))
                )
            }
        }

        // Outside the glass the light is much dimmer.
        let radius = Metrics.radius
        var menu = Path()
        menu.move(to: .zero)
        menu.addLine(to: CGPoint(x: 0, y: height - radius))
        menu.addArc(center: CGPoint(x: radius, y: height - radius), radius: radius, startAngle: .degrees(180), endAngle: .degrees(90), clockwise: true)
        menu.addLine(to: CGPoint(x: width - radius, y: height))
        menu.addArc(center: CGPoint(x: width - radius, y: height - radius), radius: radius, startAngle: .degrees(90), endAngle: .degrees(0), clockwise: true)
        menu.addLine(to: CGPoint(x: width, y: 0))
        var edge = Path()
        edge.move(to: CGPoint(x: 0.5, y: 0))
        edge.addLine(to: CGPoint(x: 0.5, y: height - radius))
        edge.addArc(center: CGPoint(x: radius, y: height - radius), radius: radius - 0.5, startAngle: .degrees(180), endAngle: .degrees(90), clockwise: true)
        edge.addLine(to: CGPoint(x: width - radius, y: height - 0.5))
        edge.addArc(center: CGPoint(x: width - radius, y: height - radius), radius: radius - 0.5, startAngle: .degrees(90), endAngle: .degrees(0), clockwise: true)
        edge.addLine(to: CGPoint(x: width - 0.5, y: 0))
        menu.closeSubpath()

        var outside = context
        outside.clip(to: menu, options: .inverse)
        outside.fill(
            Path(CGRect(x: -Metrics.bleed, y: -Metrics.bleed, width: width + Metrics.bleed * 2, height: height + Metrics.bleed * 2)),
            with: .color(.rgba(2, 5, 4, 0.74))
        )

        // Menu glass: open at the top, where the rest of the menu would continue.
        context.fill(
            menu,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(13, 15, 14, 0), location: 0),
                    .init(color: .rgba(13, 15, 14, 0.5), location: 0.07),
                    .init(color: .rgba(13, 15, 14, 0.14), location: 1),
                ]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: height)
            )
        )
        context.stroke(
            edge,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0), location: 0),
                    .init(color: .white.opacity(0.11), location: 0.12),
                    .init(color: .rgba(190, 255, 225, 0.2), location: 0.6),
                    .init(color: .rgba(150, 255, 210, 0.75), location: 1),
                ]),
                startPoint: .zero, endPoint: CGPoint(x: width, y: height)
            ),
            lineWidth: 1
        )

        // Selected row and divider.
        let inset = Metrics.inset
        let panelWidth = width - inset * 2
        context.fill(
            Path(roundedRect: CGRect(x: inset, y: Metrics.rowY, width: panelWidth, height: Metrics.rowHeight), cornerRadius: 11, style: .circular),
            with: .color(.white.opacity(0.055))
        )
        context.fill(Path(CGRect(x: inset, y: Metrics.dividerY, width: panelWidth, height: 1)), with: .color(.white.opacity(0.1)))

        let green = Color(hex: 0x2fd46d)
        var plug = context
        plug.translateBy(x: Metrics.iconX, y: Metrics.rowY + Metrics.rowHeight / 2)
        plug.stroke(Path(ellipseIn: CGRect(x: -8.6, y: -8.6, width: 17.2, height: 17.2)), with: .color(green), lineWidth: 1.5)
        plug.stroke(plugPath, with: .color(green), style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))

        var cog = context
        cog.translateBy(x: Metrics.iconX, y: Metrics.settingsY)
        cog.stroke(cogPath, with: .color(.white.opacity(0.62)), style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
        cog.stroke(Path(ellipseIn: CGRect(x: -2.6, y: -2.6, width: 5.2, height: 5.2)), with: .color(.white.opacity(0.62)), lineWidth: 1.5)

        // Power panel: dark glass on the left, lit green on the right.
        let py = Metrics.panelY
        let panel = CGRect(x: inset, y: py, width: panelWidth, height: Metrics.panelHeight)
        context.fill(
            Path(roundedRect: panel, cornerRadius: Metrics.panelRadius, style: .circular),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(27, 33, 31, 0.82), location: 0),
                    .init(color: .rgba(24, 52, 39, 0.55), location: 0.45),
                    .init(color: .rgba(40, 170, 105, 0.2), location: 1),
                ]),
                startPoint: CGPoint(x: panel.minX, y: panel.minY),
                endPoint: CGPoint(x: panel.maxX, y: panel.minY + panel.height * 0.6)
            )
        )
        context.stroke(
            Path(roundedRect: panel.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.panelRadius - 0.5, style: .circular),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.14), location: 0),
                    .init(color: .rgba(170, 255, 215, 0.22), location: 0.55),
                    .init(color: .rgba(150, 255, 205, 0.7), location: 1),
                ]),
                startPoint: CGPoint(x: panel.minX, y: panel.minY), endPoint: CGPoint(x: panel.maxX, y: panel.maxY)
            ),
            lineWidth: 1
        )

        // Chip glyph.
        var chip = context
        chip.translateBy(x: 41, y: py + 28.5)
        var pins = Path()
        for side in [-1.0, 1.0] {
            for y in [-4.0, 0, 4] {
                pins.move(to: CGPoint(x: side * 10, y: y))
                pins.addLine(to: CGPoint(x: side * 7, y: y))
            }
        }
        chip.stroke(pins, with: .color(green), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        chip.fill(Path(roundedRect: CGRect(x: -7, y: -7, width: 14, height: 14), cornerRadius: 3, style: .circular), with: .color(green))
        chip.fill(
            Path(roundedRect: CGRect(x: -2.8, y: -2.8, width: 5.6, height: 5.6), cornerRadius: 1.2, style: .circular),
            with: .color(Color(hex: 0x143524))
        )

        // Change chip with its cursor bar.
        context.fill(Path(CGRect(x: 213, y: py + 13.5, width: 61, height: 16)), with: .color(.rgba(52, 214, 122, 0.16)))
        context.fill(
            Path(CGRect(x: 276.4, y: py + 13.5, width: 1.5, height: 16)),
            with: .color(Color(hex: 0x3fe087, opacity: 0.65 + ambient * 0.35 * sin(time * 4.2)))
        )

        // Segments: the dim row fades out to the right, the lit run glows.
        let segX = Metrics.segX
        let segY = Metrics.segY
        let pitch = Metrics.segPitch
        let count = Metrics.segments
        // The lit run ends inside a segment, so the leading one shows as a sliver until it is passed.
        let litEdge = segX + max(0, clamp01(value) * Double(count) - Metrics.segLead) * pitch
        var dim = Path()
        var lit = Path()
        for i in 0..<count {
            let x = segX + Double(i) * pitch
            dim.addRoundedRect(in: CGRect(x: x, y: segY, width: Metrics.segWidth, height: Metrics.segHeight), cornerSize: CGSize(width: 1.6, height: 1.6))
            let shown = min(Metrics.segWidth, litEdge - x)
            if shown > 0 {
                let r = min(1.6, shown / 2)
                lit.addRoundedRect(in: CGRect(x: x, y: segY, width: shown, height: Metrics.segHeight), cornerSize: CGSize(width: r, height: r))
            }
        }
        let rowEnd = segX + Double(count) * pitch
        context.fill(
            dim,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(70, 190, 120, 0.4), location: 0),
                    .init(color: .rgba(70, 190, 120, 0.2), location: 0.5),
                    .init(color: .rgba(70, 190, 120, 0), location: 0.95),
                ]),
                startPoint: CGPoint(x: segX, y: 0), endPoint: CGPoint(x: rowEnd, y: 0)
            )
        )
        // Mains flicker on the lit run, and a pulse that runs along it every few seconds.
        var glow = context
        glow.opacity = 0.82 + ambient * 0.18 * sin(time * 21) * sin(time * 6.7)
        glow.addFilter(.blur(radius: 3.6))
        glow.fill(lit, with: .color(Color(hex: 0x2ff48a)))
        context.fill(
            lit,
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x7dffb6), Color(hex: 0xe2ffef), Color(hex: 0x7dffb6)]),
                startPoint: CGPoint(x: 0, y: segY), endPoint: CGPoint(x: 0, y: segY + Metrics.segHeight)
            )
        )
        let phase = time.truncatingRemainder(dividingBy: Metrics.wavePeriod) / 0.9
        if ambient > 0, phase < 1 {
            let wx = segX - 30 + (Double(count) * pitch + 30) * phase
            context.fill(
                lit,
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0), .white.opacity(0.95), .white.opacity(0)]),
                    startPoint: CGPoint(x: wx, y: 0), endPoint: CGPoint(x: wx + 30, y: 0)
                )
            )
        }
    }
}

private struct Streak {
    var a: Double
    var d: Double
    var l: Double
    var t: Double
    var alpha: Double
    var amp: Double
    var speed: Double
    var phase: Double
    var mint: Bool
}

private enum Metrics {
    static let width = 308.0
    static let height = 300.0
    static let radius = 16.0
    /// The canvas overflows the menu by this much on every side: the light streaks carry on outside it.
    static let bleed = 46.0

    static let inset = 17.5
    static let rowY = 12.0
    static let rowHeight = 48.0
    static let dividerY = 82.0
    static let panelY = 109.0
    static let panelHeight = 115.0
    static let panelRadius = 10.0
    static let settingsY = 262.0
    static let iconX = 37.0

    static let segments = 25
    static let segX = 30.0
    static let segY = 194.0
    static let segWidth = 6.0
    static let segHeight = 17.5
    static let segPitch = 9.0
    static let segLead = 0.75

    static let digitWidth = 13.4
    static let digitHeight = 28.0

    /// Value shown in the reference, and the autoPlay loop built around it (seconds).
    static let rest = 0.2431
    static let start = 0.4
    static let fill = 2.2
    static let hold = 3.8
    static let reset = 0.6
    static let loop = start + fill + hold + reset
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = start + fill + 1
    static let wavePeriod = 2.6

    /// The streaks rise to the right at this angle and fan out from the menu's bottom right corner.
    static let tilt = -22 * Double.pi / 180

    /// Light streaks in the tilted frame: `a` along the streak from the corner (negative = left),
    /// `d` above the corner, half length `l`, half thickness `t`, then how far and how fast it slides.
    static let streaks = [
        Streak(a: -120, d: 150, l: 210, t: 5, alpha: 0.3, amp: 30, speed: 0.23, phase: 0.0, mint: false),
        Streak(a: -60, d: 128, l: 190, t: 2.2, alpha: 0.42, amp: 40, speed: 0.31, phase: 1.3, mint: true),
        Streak(a: -150, d: 112, l: 240, t: 7, alpha: 0.34, amp: 26, speed: 0.19, phase: 2.1, mint: false),
        Streak(a: -40, d: 96, l: 170, t: 1.6, alpha: 0.55, amp: 46, speed: 0.37, phase: 0.7, mint: true),
        Streak(a: -100, d: 80, l: 230, t: 6, alpha: 0.42, amp: 30, speed: 0.21, phase: 3.0, mint: false),
        Streak(a: -30, d: 66, l: 180, t: 2.4, alpha: 0.6, amp: 50, speed: 0.33, phase: 4.2, mint: true),
        Streak(a: -140, d: 52, l: 250, t: 8, alpha: 0.45, amp: 24, speed: 0.17, phase: 5.0, mint: false),
        Streak(a: -50, d: 40, l: 200, t: 1.8, alpha: 0.7, amp: 44, speed: 0.41, phase: 2.6, mint: true),
        Streak(a: -90, d: 26, l: 240, t: 7, alpha: 0.5, amp: 28, speed: 0.2, phase: 1.1, mint: false),
        Streak(a: -20, d: 14, l: 190, t: 2.2, alpha: 0.75, amp: 52, speed: 0.36, phase: 5.6, mint: true),
        Streak(a: -120, d: 0, l: 260, t: 9, alpha: 0.5, amp: 22, speed: 0.16, phase: 3.7, mint: false),
        Streak(a: -60, d: -14, l: 220, t: 2, alpha: 0.6, amp: 40, speed: 0.3, phase: 0.3, mint: true),
        Streak(a: -100, d: -30, l: 260, t: 8, alpha: 0.4, amp: 26, speed: 0.18, phase: 4.6, mint: false),
        Streak(a: -180, d: 176, l: 200, t: 3, alpha: 0.2, amp: 30, speed: 0.25, phase: 2.9, mint: true),
    ]

    /// Motes drifting up the light: height above the corner, radius, speed (laps per second), phase.
    static let motes: [(d: Double, r: Double, speed: Double, phase: Double)] = [
        (34, 1.1, 0.07, 0.1), (58, 0.8, 0.05, 0.45), (12, 1.3, 0.06, 0.7), (84, 0.7, 0.045, 0.25), (-8, 1, 0.08, 0.9),
    ]
}

/// Plug glyph, drawn around its own centre.
private let plugPath: Path = {
    var path = Path()
    path.move(to: CGPoint(x: -2.2, y: -4.6))
    path.addLine(to: CGPoint(x: -2.2, y: -2))
    path.move(to: CGPoint(x: 2.2, y: -4.6))
    path.addLine(to: CGPoint(x: 2.2, y: -2))
    path.move(to: CGPoint(x: -3.7, y: -2))
    path.addLine(to: CGPoint(x: 3.7, y: -2))
    path.addLine(to: CGPoint(x: 3.7, y: 0.2))
    path.addArc(center: CGPoint(x: 0, y: 0.2), radius: 3.7, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
    path.closeSubpath()
    path.move(to: CGPoint(x: 0, y: 3.9))
    path.addLine(to: CGPoint(x: 0, y: 6.2))
    return path
}()

/// Eight-toothed cog outline, drawn around its own centre.
private let cogPath: Path = {
    let teeth = 8
    let step = tau / Double(teeth)
    var path = Path()
    for i in 0..<teeth {
        let a = Double(i) * step
        let points = [(a - step * 0.14, 8.2), (a + step * 0.14, 8.2), (a + step * 0.3, 6.2), (a + step * 0.7, 6.2)]
        for (index, point) in points.enumerated() {
            let p = CGPoint(x: point.1 * cos(point.0), y: point.1 * sin(point.0))
            if i == 0, index == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
    }
    path.closeSubpath()
    return path
}()

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Long tail, so the last digits of the counter are seen rolling into place.
private func easeOutQuart(_ t: Double) -> Double { 1 - pow(1 - t, 4) }

/// Power rate at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.fill { return Metrics.rest * easeOutQuart((t - Metrics.start) / Metrics.fill) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

/// Where digit `place` of the counter sits (0…10), rolling over like a mechanical odometer.
private func digitPosition(_ value: Double, place: Int) -> Double {
    // Hundredths of a percent, snapped so 24.31 does not come out as 24.3099….
    let y = (value * 1e6).rounded() / 100 / pow(10, Double(place))
    let base = y.rounded(.down)
    let frac = y - base
    // The last wheel turns freely; the others only move while the wheel below passes from 9 to 0.
    let roll = place == 0 ? frac : max(0, (frac - 0.9) * 10)
    return base.truncatingRemainder(dividingBy: 10) + roll
}

private extension View {
    /// Places the view by its leading edge and vertical centre inside the card.
    func leading(_ x: Double, centerY: Double) -> some View {
        fixedSize()
            .frame(width: 320, alignment: .leading)
            .position(x: x + 160, y: centerY)
    }
}

#Preview {
    ZStack {
        Color(hex: 0x020403).ignoresSafeArea()
        PowerRateSegments(autoPlay: true)
    }
}
