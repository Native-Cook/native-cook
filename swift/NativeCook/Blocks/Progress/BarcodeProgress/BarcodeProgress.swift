import SwiftUI

/// Black "Progress Indicator" card: a caption, the percentage with a change chip, and a wide
/// barcode of 33 bars, white for the completed part. A scanner line with a soft bloom rides the
/// head of the fill and the bars around it duck, so a notch travels along the row and each bar
/// snaps to white as the line crosses it; the chip lands with a springy pop, and while the value
/// rests a shallow ripple keeps running through the bars.
struct BarcodeProgress: View {
    /// Controlled progress, 0…1. Leave `nil` to show the reference's 66% (or the autoPlay loop).
    var progress: Double? = nil
    /// Scans the bars from 0 to 66% by itself, holds, rewinds and starts over.
    var autoPlay = false
    var title = "Progress Indicator"
    var caption = "You are on track to finish the goal three days early"
    /// Change against the last period, in percent, shown in the chip.
    var change = 30
    var comparison = "vs. the last period"
    var onMore: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (ripple).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// When a controlled value last changed.
    @State private var movedAt = Date.distantPast
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var target: Double { clamp01(progress ?? Metrics.rest) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let clock = clock(at: date)
            let value = clamp01(controlled ? held.value(at: date) : loopValue(clock))
            let percent = Int((value * 100).rounded())
            // In the autoPlay loop the change counts up together with the bars.
            let delta = controlled ? change : Int((Double(change * percent) / (Metrics.rest * 100)).rounded())
            let chip = chipScale(clock)

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time, activity: activity(at: date, clock: clock), chip: chip)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                content(percent: percent, delta: delta, chip: chip)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(title). \(caption). Up \(delta)% \(comparison)")
                    .accessibilityValue("\(percent)%")

                Button(action: onMore) {
                    Color.clear.frame(width: 20, height: 15)
                }
                .buttonStyle(MoreStyle())
                .accessibilityLabel("More")
                .position(x: 326, y: 18.5)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .onTapGesture { restart() }
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
    }

    /// Position in the autoPlay loop, in seconds.
    private func clock(at date: Date) -> Double {
        guard let runStart else { return Metrics.restTime }
        let elapsed = date.timeIntervalSince(runStart)
        // A tapped, one-off run stops at the resting frame.
        return autoPlay ? elapsed.truncatingRemainder(dividingBy: Metrics.loop) : min(elapsed, Metrics.restTime)
    }

    /// 0…1: how fast the scanner is travelling.
    private func activity(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 0 }
        if !controlled { return loopActivity(clock) }
        let u = date.timeIntervalSince(movedAt) / 0.7
        return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
    }

    /// The chip shrinks away while the bars rewind and lands with an overshoot once they are filled.
    private func chipScale(_ t: Double) -> Double {
        if reduceMotion || controlled { return 1 }
        if t >= Metrics.loop - Metrics.reset { return 1 - 0.25 * clamp01((t - (Metrics.loop - Metrics.reset)) / 0.25) }
        let u = (t - Metrics.landed + 0.25) / 0.6
        if u <= 0 { return 0.75 }
        if u >= 1 { return 1 }
        return 1 - 0.25 * exp(-6 * u) * cos(11 * u)
    }

    private func content(percent: Int, delta: Int, chip: Double) -> some View {
        ZStack {
            Text(title)
                .font(.system(size: 11.6, weight: .medium))
                .kerning(0.1)
                .foregroundStyle(Color(hex: 0xf5f5f5))
                .lineLimit(1)
                .frame(width: 290, alignment: .leading)
                .position(x: 11 + 145, y: 18.8)

            Text(caption)
                .font(.system(size: 10.2))
                .kerning(0.1)
                .foregroundStyle(Color(hex: 0x9a9a9a))
                .lineLimit(1)
                .frame(width: 322, alignment: .leading)
                .position(x: 11 + 161, y: 42.7)

            Text("\(percent)%")
                .font(.system(size: 23.5))
                .kerning(-0.6)
                .monospacedDigit()
                .foregroundStyle(.white)
                .fixedSize()
                .frame(width: 60, alignment: .leading)
                .position(x: 11 + 30, y: Metrics.figureY)

            Text("\(delta)%")
                .font(.system(size: 8.2, weight: .medium, design: .monospaced))
                .foregroundStyle(Color(hex: 0xededed))
                .fixedSize()
                // Offset from the chip's centre so the label scales about the same point as its outline.
                .offset(x: 6.5)
                .scaleEffect(chip)
                .position(x: Metrics.chipX + Metrics.chipW / 2, y: Metrics.figureY)

            Text(comparison)
                .font(.system(size: 10.2))
                .kerning(0.1)
                .foregroundStyle(Color(hex: 0x9a9a9a))
                .lineLimit(1)
                .frame(width: 210, alignment: .leading)
                .position(x: 118 + 105, y: Metrics.figureY)
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    private func draw(_ base: GraphicsContext, value: Double, time: Double, activity: Double, chip: Double) {
        let ambient = reduceMotion ? 0.0 : 1.0
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)

        let card = CGRect(x: 0, y: 0, width: Metrics.width, height: Metrics.height)
        context.fill(Path(roundedRect: card, cornerRadius: Metrics.radius, style: .circular), with: .color(Color(hex: 0x0b0b0b)))
        context.stroke(
            Path(roundedRect: card.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5, style: .circular),
            with: .color(Color(hex: 0x1d1d1d)), lineWidth: 1
        )

        // "More" dots and the divider under the caption.
        for i in 0..<3 {
            let cx = 323.3 + Double(i) * 2.5
            context.fill(Path(ellipseIn: CGRect(x: cx - 0.75, y: 18.4 - 0.75, width: 1.5, height: 1.5)), with: .color(Color(hex: 0xf2f2f2)))
        }
        context.fill(Path(CGRect(x: Metrics.rowX, y: 59, width: Metrics.rowW, height: 0.5)), with: .color(Color(hex: 0x272727)))

        // Change chip: outline and arrow; the label is real text scaled with it.
        let chipX = Metrics.chipX
        let fy = Metrics.figureY
        let outline = context.scaled(chip, about: CGPoint(x: chipX + Metrics.chipW / 2, y: fy))
        let ink = GraphicsContext.Shading.color(Color(hex: 0xededed))
        outline.stroke(
            Path(
                roundedRect: CGRect(x: chipX + 0.4, y: fy - Metrics.chipH / 2 + 0.4, width: Metrics.chipW - 0.8, height: Metrics.chipH - 0.8),
                cornerRadius: Metrics.chipH / 2, style: .circular
            ),
            with: ink, lineWidth: 0.8
        )
        var arrow = Path()
        arrow.move(to: CGPoint(x: chipX + 8.2, y: fy + 2.4))
        arrow.addLine(to: CGPoint(x: chipX + 13, y: fy - 2.4))
        arrow.move(to: CGPoint(x: chipX + 9.1, y: fy - 2.5))
        arrow.addLine(to: CGPoint(x: chipX + 13.1, y: fy - 2.5))
        arrow.addLine(to: CGPoint(x: chipX + 13.1, y: fy + 1.5))
        outline.stroke(arrow, with: ink, style: StrokeStyle(lineWidth: 0.8, lineCap: .round, lineJoin: .round))

        // The bars: dim row, the lit ones on top, and the one switching on cross-fading.
        let row = CGRect(x: Metrics.rowX, y: Metrics.rowY, width: Metrics.rowW, height: Metrics.rowH)
        var bars = context
        bars.clip(to: Path(roundedRect: row, cornerRadius: Metrics.rowRadius, style: .circular))
        for index in 0..<Metrics.bars {
            let h = Metrics.rowH * barHeight(value, index, activity: activity, time: time, ambient: ambient)
            let bar = Path(CGRect(
                x: Metrics.rowX + Double(index) * Metrics.pitch, y: Metrics.rowY + (Metrics.rowH - h) / 2,
                width: Metrics.barW, height: h
            ))
            bars.fill(bar, with: .color(Color(hex: 0x474747)))
            let lit = barLit(value, index)
            if lit > 0 { bars.fill(bar, with: .color(Color(hex: 0xfafafa, opacity: lit))) }
        }

        // The scanner: a bright line riding the head of the fill, with a soft bloom, only while it moves.
        if activity > 0 {
            let x = Metrics.rowX + value * (Metrics.rowW + Metrics.gap) - Metrics.gap / 2
            var beam = context
            beam.opacity = activity
            beam.drawLayer { layer in
                layer.addFilter(.blur(radius: 7))
                layer.fill(
                    Path(CGRect(x: x - 9, y: Metrics.rowY - 2, width: 18, height: Metrics.rowH + 4)),
                    with: .color(.white.opacity(0.4))
                )
            }
            beam.drawLayer { layer in
                layer.addFilter(.blur(radius: 0.6))
                layer.fill(
                    Path(roundedRect: CGRect(x: x - 0.9, y: Metrics.rowY - 6, width: 1.8, height: Metrics.rowH + 12), cornerRadius: 0.9),
                    with: .color(.white)
                )
            }
        }
    }
}

private enum Metrics {
    static let width = 344.0
    static let height = 175.0
    static let radius = 7.0
    /// The canvas overflows the card by this much on every side to fit the scanner's glow.
    static let bleed = 16.0

    static let bars = 33
    static let rowX = 11.0
    static let rowY = 115.0
    static let rowW = 322.0
    static let rowH = 49.5
    static let gap = 1.3
    static let pitch = (rowW + gap) / Double(bars)
    static let barW = pitch - gap
    static let rowRadius = 2.2

    static let figureY = 84.7
    static let chipX = 66.8
    static let chipW = 40.0
    static let chipH = 17.0

    /// Value shown in the reference, and the autoPlay loop built around it (seconds).
    static let rest = 0.66
    static let start = 0.4
    static let fill = 1.9
    static let hold = 4.2
    static let reset = 0.7
    static let loop = start + fill + hold + reset
    static let landed = start + fill
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = landed + 1.2
    /// The ripple that keeps running through the bars: one pass every `ripplePeriod` seconds.
    static let ripplePeriod = 3.6
    static let ripplePass = 1.15
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Progress at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.landed { return Metrics.rest * easeInOutCubic((t - Metrics.start) / Metrics.fill) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

/// 0…1: how fast the scanner is travelling at `t` seconds into the loop.
private func loopActivity(_ t: Double) -> Double {
    let u = (t - Metrics.start) / Metrics.fill
    return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
}

/// 0…1: how lit bar `index` is. A bar switches on as the scanner crosses its middle third.
private func barLit(_ value: Double, _ index: Int) -> Double {
    clamp01((value * Double(Metrics.bars) - Double(index) - 0.5) * 3 + 0.5)
}

/// Height of bar `index`, 0…1 of the row. The bars around the moving scanner duck, so a notch
/// travels with it, and a shallow ripple keeps running through the row while it rests.
private func barHeight(_ value: Double, _ index: Int, activity: Double, time: Double, ambient: Double) -> Double {
    let near = clamp01(1 - abs(value * Double(Metrics.bars) - Double(index) - 0.5) / 2.6)
    let notch = 0.42 * activity * near * near * (3 - 2 * near)
    let phase = time.truncatingRemainder(dividingBy: Metrics.ripplePeriod) / Metrics.ripplePass
    let at = -4 + Double(Metrics.bars + 8) * phase
    let wave = phase < 1 ? exp(-pow((Double(index) - at) / 2.1, 2)) : 0
    return 1 - notch - 0.2 * ambient * (1 - activity) * wave
}

/// Invisible hit area over the "more" dots.
private struct MoreStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .overlay {
                // The dots themselves are drawn on the canvas; this dims them while pressed.
                Color(hex: 0x0b0b0b)
                    .frame(width: 12, height: 7)
                    .opacity(configuration.isPressed ? 0.6 : 0)
            }
            .contentShape(Rectangle().inset(by: -10))
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xcccccc).ignoresSafeArea()
        BarcodeProgress(autoPlay: true)
    }
}
