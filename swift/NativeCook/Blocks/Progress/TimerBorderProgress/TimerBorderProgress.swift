import SwiftUI

/// Timer live activity: a compact black pill with a small ring and the time, and an expanded
/// black panel whose border is an orange stroke that shrinks as the time runs out, with pause and
/// cancel buttons around the big time. The tip of the stroke burns: it glows, beats with every
/// second and throws sparks (a shower while the time is being wound), the digits of the big
/// time roll like an odometer on each tick, and a paused timer breathes.
struct TimerBorderProgress: View {
    /// Controlled share of the timer that is left, 0…1. Leave `nil` to show the reference's 13:12 (or the autoPlay loop).
    var progress: Double? = nil
    /// Runs by itself: winds up to the full duration, races down to 13:12, ticks there in real time, and starts over.
    var autoPlay = false
    /// Length of the timer in seconds.
    var duration = 975
    /// Time of day the timer goes off.
    var alarm = "14:44"
    var label = "Timer"
    var onPauseChange: (Bool) -> Void = { _ in }
    var onCancel: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (sparks, breathing).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    /// When the timer was paused: the loop's clock stands still from then on.
    @State private var pausedAt: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// When a controlled value last changed.
    @State private var movedAt = Date.distantPast
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var target: Double { clamp01(progress ?? Metrics.rest) }
    private var paused: Bool { pausedAt != nil }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let clock = clock(at: date)
            let value = clamp01(controlled ? held.value(at: date) : loopValue(clock))
            let seconds = value * (controlled ? Double(duration) : Metrics.duration)
            let activity = activity(at: date, clock: clock)
            let running = runStart != nil && !paused && (autoPlay || clock < Metrics.landed)
            let shown = Int((seconds - 1e-4).rounded(.up))

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, seconds: seconds, time: time, activity: activity, running: running)
                }
                .padding(-Metrics.bleed)

                readouts(seconds: seconds, shown: shown, rolling: running && activity == 0)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(label), alarm at \(alarm)\(paused ? ", paused" : "")")
            .accessibilityValue("\(timecode(shown)) left")
        }
        .contentShape(Rectangle())
        .onTapGesture { restart() }
        .overlay {
            ZStack {
                Button {
                    togglePause()
                } label: {
                    PauseGlyph(paused: paused)
                }
                .buttonStyle(RoundStyle(color: Color(hex: 0x262628)))
                .accessibilityLabel(paused ? "Resume" : "Pause")
                .position(x: Metrics.pauseX, y: Metrics.buttonY)

                Button {
                    pausedAt = nil
                    restart()
                    onCancel()
                } label: {
                    CancelGlyph()
                }
                .buttonStyle(RoundStyle(color: Color(hex: 0x4a1010)))
                .accessibilityLabel("Cancel")
                .position(x: Metrics.cancelX, y: Metrics.buttonY)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            movedAt = .now
            held.retarget(target, duration: reduceMotion ? 0.2 : 0.8, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1.6))
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
        if paused { pausedAt = .now }
    }

    /// Pause really pauses: the run's start is pushed back by the time spent paused.
    private func togglePause() {
        if let pausedAt {
            if let runStart { self.runStart = runStart.addingTimeInterval(Date.now.timeIntervalSince(pausedAt)) }
            self.pausedAt = nil
        } else {
            pausedAt = .now
        }
        onPauseChange(paused)
    }

    /// Position in the autoPlay loop, in seconds.
    private func clock(at date: Date) -> Double {
        guard let runStart else { return Metrics.landed }
        let elapsed = max(0, (pausedAt ?? date).timeIntervalSince(runStart))
        // A tapped, one-off run stops at the resting frame.
        return autoPlay ? elapsed.truncatingRemainder(dividingBy: Metrics.loop) : min(elapsed, Metrics.landed)
    }

    /// 0…1: how fast the time is being wound; 0 while it ticks in real time.
    private func activity(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 0 }
        if !controlled { return runStart == nil ? 0 : loopActivity(clock) }
        let u = date.timeIntervalSince(movedAt) / 0.7
        return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
    }

    private func timecode(_ seconds: Int) -> String {
        String(format: "%02d:%02d", max(0, seconds) / 60, max(0, seconds) % 60)
    }

    private func readouts(seconds: Double, shown: Int, rolling: Bool) -> some View {
        let now = Array(timecode(shown))
        let before = Array(timecode(shown + 1))
        // Seconds since the digits last changed: a new digit rolls up into place like an odometer.
        let since = Double(shown) - seconds
        let grey = Color(hex: 0x5a5a5e)

        return ZStack {
            Text(timecode(shown))
                .font(.system(size: 14.6, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Metrics.orange)
                .fixedSize()
                .frame(width: 80, alignment: .trailing)
                .position(x: 255.6 - 40, y: Metrics.ringY)

            Text(alarm)
                .font(.system(size: 14.8, weight: .medium))
                .monospacedDigit()
                .foregroundStyle(grey)
                .fixedSize()
                .frame(width: 80, alignment: .leading)
                .position(x: 153.4 + 40, y: 95.4)

            HStack(spacing: 0) {
                ForEach([0, 1, 2, 3, 4], id: \.self) { index in
                    if index == 2 {
                        Text(":")
                            .font(.system(size: 40.5, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 12, height: Metrics.digitH)
                            .offset(y: -3)
                    } else {
                        digit(now[index], before: before[index], since: rolling ? since : 1)
                    }
                }
            }
            .position(x: Metrics.width / 2, y: 128.4)

            Text(label)
                .font(.system(size: 15.6, weight: .medium))
                .foregroundStyle(grey)
                .fixedSize()
                .position(x: Metrics.width / 2, y: 159.8)
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    private func digit(_ now: Character, before: Character, since: Double) -> some View {
        let rolls = now != before && since < 0.32
        let roll = rolls ? Ease.out.value(at: clamp01(since / 0.32)) : 1
        let fade = rolls ? clamp01(since / 0.2) : 1
        let font = Font.system(size: 40.5, weight: .bold)
        return ZStack {
            if rolls {
                Text(String(before))
                    .font(font)
                    .monospacedDigit()
                    .offset(y: -Metrics.digitH * 0.55 * roll)
                    .opacity(1 - fade)
            }
            Text(String(now))
                .font(font)
                .monospacedDigit()
                .offset(y: Metrics.digitH * 0.55 * (1 - roll))
                .opacity(fade)
        }
        .foregroundStyle(.white)
        .frame(width: Metrics.digitW, height: Metrics.digitH)
        .clipped()
    }

    private func draw(_ base: GraphicsContext, value: Double, seconds: Double, time: Double, activity: Double, running: Bool) {
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)
        let width = Metrics.width
        let panelY = Metrics.panelY
        let panelH = Metrics.height - panelY
        // A paused timer breathes.
        let lit = paused ? 0.7 - 0.2 * sin(time * 3.4) : 1

        // The lighter sheet the expanded panel sits on.
        context.fill(
            Path(roundedRect: CGRect(x: -9, y: panelY - 11, width: width + 18, height: panelH + 60), cornerRadius: 46, style: .circular),
            with: .linearGradient(
                Gradient(colors: [.rgba(246, 246, 246, 1), .rgba(246, 246, 246, 0)]),
                startPoint: CGPoint(x: 0, y: panelY + panelH - 50), endPoint: CGPoint(x: 0, y: panelY + panelH + 44)
            )
        )
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 14))
            layer.fill(
                Path(roundedRect: CGRect(x: 10, y: panelY + 16, width: width - 20, height: panelH - 10), cornerRadius: Metrics.panelR, style: .circular),
                with: .color(.black.opacity(0.3))
            )
        }

        // Compact pill; its ring mirrors the stroke.
        context.fill(capsule(CGRect(x: Metrics.pillX, y: 0, width: Metrics.pillW, height: Metrics.pillH)), with: .color(.black))
        let ringCenter = CGPoint(x: Metrics.ringX, y: Metrics.ringY)
        var track = Path()
        track.addArc(center: ringCenter, radius: Metrics.ringR, startAngle: .zero, endAngle: .degrees(360), clockwise: false)
        context.stroke(track, with: .color(Color(hex: 0x2c2c2e)), lineWidth: 2.2)
        var ring = Path()
        ring.addArc(
            center: ringCenter, radius: Metrics.ringR,
            startAngle: .degrees(-90), endAngle: .degrees(-90 + min(359.9, max(0.01, value * 360))), clockwise: false
        )
        context.stroke(ring, with: .color(Metrics.orange.opacity(lit)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))

        // Expanded panel
        context.fill(
            Path(roundedRect: CGRect(x: 0, y: panelY, width: width, height: panelH), cornerRadius: Metrics.panelR, style: .circular),
            with: .color(.black)
        )
        let border = Metrics.border
        let left = border.trimmedPath(from: 0, to: max(0.0001, value))
        let stroke = StrokeStyle(lineWidth: Metrics.stroke, lineCap: .round)
        context.stroke(border, with: .color(Color(hex: 0x2a2a2c)), style: stroke)
        // A faint bloom along the whole stroke, stronger while the time is being wound.
        if running {
            var bloom = context
            bloom.opacity = 0.22 + 0.5 * activity
            bloom.drawLayer { layer in
                layer.addFilter(.blur(radius: 5))
                layer.stroke(left, with: .color(.rgba(255, 159, 10, 0.35)), style: StrokeStyle(lineWidth: Metrics.stroke + 2, lineCap: .round))
            }
        }
        context.stroke(left, with: .color(Metrics.orange.opacity(lit)), style: stroke)

        // The burning tip of the stroke: it beats with every second and flares while the time is being wound.
        guard running, let tip = left.currentPoint else {
            drawBell(context)
            return
        }
        let beat = (1 - activity) * exp(-5 * (seconds.rounded(.up) - seconds))
        let glowAlpha = 0.55 + 0.3 * beat + 0.15 * activity
        let glowRadius = 5.5 + 3 * beat + 3.5 * activity
        var glow = context
        glow.opacity = glowAlpha
        glow.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            layer.fill(
                Path(ellipseIn: CGRect(x: tip.x - glowRadius, y: tip.y - glowRadius, width: glowRadius * 2, height: glowRadius * 2)),
                with: .color(.rgba(255, 176, 46, 0.9))
            )
        }
        var core = context
        core.opacity = 0.5 + 0.5 * max(beat, activity)
        core.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.1))
            layer.fill(Path(ellipseIn: CGRect(x: tip.x - 2.7, y: tip.y - 2.7, width: 5.4, height: 5.4)), with: .color(Color(hex: 0xfff3d6)))
        }
        // Sparks thrown off the tip: a couple while it ticks, a shower while the time is being wound.
        var sparks = Path()
        for i in 0..<Int((2 + 7 * activity).rounded()) {
            let cycle = time / 0.75 + Double(i) / 9
            let age = cycle - floor(cycle)
            let seed = floor(cycle) * 9 + Double(i)
            let angle = noise(seed) * .pi * 2
            let reach = (9 + 14 * noise(seed + 0.5)) * (1 - pow(1 - age, 2))
            let r = 1.35 * (1 - age)
            let x = tip.x + cos(angle) * reach
            let y = tip.y + sin(angle) * reach + 7 * age * age
            sparks.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
        }
        context.fill(sparks, with: .color(Color(hex: 0xffd98a)))
        drawBell(context)
    }

    /// Alarm bell in front of the alarm time.
    private func drawBell(_ context: GraphicsContext) {
        var bell = Path()
        bell.move(to: CGPoint(x: -4.7, y: 3.1))
        bell.addCurve(to: CGPoint(x: -3.4, y: -1), control1: CGPoint(x: -3.5, y: 2.1), control2: CGPoint(x: -3.4, y: 0.6))
        bell.addCurve(to: CGPoint(x: 0, y: -4.9), control1: CGPoint(x: -3.4, y: -3.3), control2: CGPoint(x: -1.9, y: -4.9))
        bell.addCurve(to: CGPoint(x: 3.4, y: -1), control1: CGPoint(x: 1.9, y: -4.9), control2: CGPoint(x: 3.4, y: -3.3))
        bell.addCurve(to: CGPoint(x: 4.7, y: 3.1), control1: CGPoint(x: 3.4, y: 0.6), control2: CGPoint(x: 3.5, y: 2.1))
        bell.closeSubpath()
        bell.addArc(center: CGPoint(x: 0, y: 4.1), radius: 1.3, startAngle: .zero, endAngle: .degrees(180), clockwise: false)
        bell.closeSubpath()
        context.fill(bell.offsetBy(dx: 142.9, dy: 95.4), with: .color(Color(hex: 0x5a5a5e)))
    }
}

private enum Metrics {
    static let width = 330.0
    static let height = 200.0
    /// The canvas overflows the widget by this much on every side to fit the panel's shadow.
    static let bleed = 30.0

    static let pillX = 67.3
    static let pillW = 195.4
    static let pillH = 35.3
    static let ringX = 86.2
    static let ringY = pillH / 2
    static let ringR = 10.6

    static let panelY = 57.4
    static let panelR = 36.0
    /// The progress stroke runs round the panel, leaving a gap at the top centre.
    static let stroke = 7.0
    static let edge = 10.5
    static let gapFrom = 107.5
    static let gapTo = 222.5

    static let buttonY = 128.6
    static let buttonR = 32.1
    static let pauseX = 67.2
    static let cancelX = 262.8
    static let digitW = 24.4
    static let digitH = 48.0

    /// The reference's frame: 13:12 left of a 16:15 timer.
    static let duration = 975.0
    static let restSeconds = 792.0
    static let rest = restSeconds / duration
    /// The autoPlay loop (seconds): wind up to the full duration, race down to the reference, tick in real time.
    static let wind = 0.7
    static let race = 2.6
    static let hold = 5.2
    static let loop = wind + race + hold
    static let landed = wind + race

    static let orange = Color(hex: 0xff9f0a)

    /// Starts right of the gap and runs clockwise back to its left.
    static let border: Path = {
        let left = edge
        let right = width - edge
        let top = panelY + edge
        let bottom = height - edge
        let r = panelR - edge
        var path = Path()
        path.move(to: CGPoint(x: gapTo, y: top))
        path.addLine(to: CGPoint(x: right - r, y: top))
        path.addArc(center: CGPoint(x: right - r, y: top + r), radius: r, startAngle: .degrees(-90), endAngle: .zero, clockwise: false)
        path.addLine(to: CGPoint(x: right, y: bottom - r))
        path.addArc(center: CGPoint(x: right - r, y: bottom - r), radius: r, startAngle: .zero, endAngle: .degrees(90), clockwise: false)
        path.addLine(to: CGPoint(x: left + r, y: bottom))
        path.addArc(center: CGPoint(x: left + r, y: bottom - r), radius: r, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        path.addLine(to: CGPoint(x: left, y: top + r))
        path.addArc(center: CGPoint(x: left + r, y: top + r), radius: r, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        path.addLine(to: CGPoint(x: gapFrom, y: top))
        return path
    }()
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Share of the reference timer left at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    let full = Metrics.duration
    let rest = Metrics.restSeconds
    if t < Metrics.wind { return (rest - Metrics.hold + (full - rest + Metrics.hold) * easeInOutCubic(t / Metrics.wind)) / full }
    if t < Metrics.landed { return (full - (full - rest) * easeInOutCubic((t - Metrics.wind) / Metrics.race)) / full }
    return (rest - (t - Metrics.landed)) / full
}

/// 0…1: how fast the time is being wound at `t` seconds into the loop; 0 while it ticks in real time.
private func loopActivity(_ t: Double) -> Double {
    if t < Metrics.wind { return sin(.pi * (t / Metrics.wind)) }
    if t < Metrics.landed { return sin(.pi * ((t - Metrics.wind) / Metrics.race)) }
    return 0
}

/// A cheap repeatable random number in 0…1.
private func noise(_ seed: Double) -> Double {
    let v = sin(seed * 127.1 + 31.7) * 43758.5453
    return v - floor(v)
}

/// Two bars while the timer runs, a triangle while it is paused.
private struct PauseGlyph: View {
    var paused: Bool

    var body: some View {
        ZStack {
            HStack(spacing: 8) {
                Capsule().frame(width: 6.6, height: 23.8)
                Capsule().frame(width: 6.6, height: 23.8)
            }
            .opacity(paused ? 0 : 1)
            Canvas { context, size in
                var triangle = Path()
                triangle.move(to: CGPoint(x: size.width / 2 - 6.5, y: size.height / 2 - 10.5))
                triangle.addLine(to: CGPoint(x: size.width / 2 + 11, y: size.height / 2))
                triangle.addLine(to: CGPoint(x: size.width / 2 - 6.5, y: size.height / 2 + 10.5))
                triangle.closeSubpath()
                context.fill(triangle, with: .color(.white))
                context.stroke(triangle, with: .color(.white), style: StrokeStyle(lineWidth: 5, lineJoin: .round))
            }
            .opacity(paused ? 1 : 0)
        }
        .foregroundStyle(.white)
        .animation(.easeOut(duration: 0.2), value: paused)
    }
}

private struct CancelGlyph: View {
    var body: some View {
        Canvas { context, size in
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            var cross = Path()
            cross.move(to: CGPoint(x: c.x - 8.3, y: c.y - 8.3))
            cross.addLine(to: CGPoint(x: c.x + 8.3, y: c.y + 8.3))
            cross.move(to: CGPoint(x: c.x + 8.3, y: c.y - 8.3))
            cross.addLine(to: CGPoint(x: c.x - 8.3, y: c.y + 8.3))
            context.stroke(cross, with: .color(Color(hex: 0xff3b3b)), style: StrokeStyle(lineWidth: 7, lineCap: .round))
        }
    }
}

/// Round button face; scales down while pressed.
private struct RoundStyle: ButtonStyle {
    var color: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(width: Metrics.buttonR * 2, height: Metrics.buttonR * 2)
            .background(color, in: Circle())
            .contentShape(Circle())
            .scaleEffect(configuration.isPressed ? 0.93 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe7e7e7).ignoresSafeArea()
        TimerBorderProgress(autoPlay: true)
    }
}
