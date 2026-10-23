import SwiftUI

/// Neon "download" pill with a round arrow knob. A tap collapses it to a circle around the knob,
/// a white dot laps the outline as progress, is thrown off the top and drops, green, into the pill
/// that opens to say "done!", its outline wobbling from the hit; then it returns.
struct NeonDownloadButton: View {
    var label = "download"
    var doneLabel = "done!"
    /// Downloads by itself over and over, with a pause between runs.
    var autoPlay = false
    var action: () -> Void = {}

    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            action()
        } label: {
            Text(label)
        }
        .buttonStyle(Style(label: label, doneLabel: doneLabel, autoPlay: autoPlay, taps: taps))
        // The label gives way to the progress ring and the result, so name the button explicitly.
        .accessibilityLabel(label)
    }

    private struct Style: ButtonStyle {
        let label: String
        let doneLabel: String
        let autoPlay: Bool
        let taps: Int

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, doneLabel: doneLabel, autoPlay: autoPlay, taps: taps, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let width = 220.0
    static let height = 52.0
    /// Width of the pill that announces the result.
    static let doneWidth = 170.0
    static let r = height / 2
    static let knobR = 20.5
    static let stroke = 2.2
    /// The progress dot rides the middle of the outline.
    static let ringR = r - stroke / 2
    /// The canvas overflows the button by this much on every side to fit the glow and the dot's flight.
    static let bleed = 60.0
    static let autoPlayPause = 1.5
    /// How high the dot is thrown above the button before it drops in.
    static let apex = 44.0
    static let dotR = 5.5
}

/// The whole run is one clock in seconds; every part is a window on it.
private enum Clock {
    static let flash = (0.0, 0.2)
    static let ripple = (0.0, 0.5)
    static let collapse = (0.08, 0.6)
    static let labelOut = (0.1, 0.35)
    static let toStop = (0.1, 0.7)
    static let dotIn = (0.7, 0.9)
    static let arcIn = (0.85, 0.95)
    static let lap = (0.9, 2.5)
    static let arcOut = (2.5, 2.8)
    static let launch = (2.5, 2.95)
    static let knobOut = (2.55, 2.85)
    static let expand = (2.6, 3.1)
    static let fall = (2.95, 3.45)
    static let impact = 3.45
    static let dotOut = (3.45, 3.52)
    static let green = (3.45, 3.65)
    static let doneIn = (3.5, 3.8)
    static let doneOut = (5.3, 5.5)
    static let cyan = (5.3, 5.6)
    static let back = (5.3, 5.9)
    static let knobIn = (5.4, 5.75)
    static let toArrow = (5.4, 5.9)
    static let labelIn = (5.55, 5.9)
    static let total = 6.0
}

private let cyan = Color(hex: 0x02d1fe)
private let green = Color(hex: 0x00fe8c)

private func seg(_ t: Double, _ window: (Double, Double)) -> Double {
    min(1, max(0, (t - window.0) / (window.1 - window.0)))
}

private func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

private func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
}

private func easeOutBack(_ x: Double) -> Double {
    let c = 1.7
    return 1 + (c + 1) * pow(x - 1, 3) + c * pow(x - 1, 2)
}

/// Pill width: collapses to a circle, opens into the result pill, then returns to full width.
private func pillWidth(_ t: Double, still: Bool) -> Double {
    if still { return Metrics.width }
    return Metrics.width
        + (Metrics.height - Metrics.width) * easeInOut(seg(t, Clock.collapse))
        + (Metrics.doneWidth - Metrics.height) * easeOutBack(seg(t, Clock.expand))
        + (Metrics.width - Metrics.doneWidth) * easeInOut(seg(t, Clock.back))
}

/// How far the bottom edge sags: a damped wobble set off by the dot landing.
private func wobble(_ t: Double, still: Bool) -> Double {
    let since = t - Clock.impact
    if still || since <= 0 || since > 1.2 { return 0 }
    return 7 * exp(-5.5 * since) * sin(16 * since)
}

/// The pill's outline around `center`, with the bottom edge bent by `sag`.
private func outline(center: CGPoint, width: Double, sag: Double) -> Path {
    let r = Metrics.r
    let left = center.x - width / 2
    let right = center.x + width / 2
    let top = center.y - r
    var path = Path()
    path.move(to: CGPoint(x: left + r, y: top))
    path.addLine(to: CGPoint(x: right - r, y: top))
    // SwiftUI's `clockwise` is measured in a y-up space, so `false` runs clockwise on screen.
    path.addArc(center: CGPoint(x: right - r, y: center.y), radius: r, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false)
    path.addQuadCurve(to: CGPoint(x: left + r, y: top + Metrics.height), control: CGPoint(x: center.x, y: top + Metrics.height + sag * 2))
    path.addArc(center: CGPoint(x: left + r, y: center.y), radius: r, startAngle: .degrees(90), endAngle: .degrees(270), clockwise: false)
    path.closeSubpath()
    return path
}

private func disc(_ center: CGPoint, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
}

private struct Surface: View {
    let label: String
    let doneLabel: String
    let autoPlay: Bool
    let taps: Int
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the current run started; nil while idle.
    @State private var start: Date?

    var body: some View {
        TimelineView(.animation(paused: start == nil)) { timeline in
            let t = start.map { min(max(timeline.date.timeIntervalSince($0), 0), Clock.total) } ?? 0
            // Without motion the label simply gives way to the result when the outline turns green.
            let out = seg(t, reduceMotion ? Clock.green : Clock.labelOut)
            let back = seg(t, Clock.labelIn)
            let shown = seg(t, Clock.doneIn)
            let gone = seg(t, Clock.doneOut)
            let slide = reduceMotion ? 0.0 : 12.0

            ZStack {
                Canvas { context, _ in
                    draw(context, t: t)
                }
                .padding(-Metrics.bleed)

                // The idle label is centred in the space to the right of the knob.
                Text(label)
                    .frame(width: Metrics.width - Metrics.height + 8)
                    .padding(.leading, Metrics.height - 8)
                    .opacity(1 - out + back)
                    .offset(y: slide * (back > 0 ? 1 - easeOut(back) : easeOut(out)))
                    .accessibilityHidden(true)
                Text(doneLabel)
                    .opacity(shown - gone)
                    .offset(y: slide * (1 - easeOut(shown)) + slide * easeOut(gone))
                    .accessibilityHidden(true)
            }
            .font(.system(size: 16, weight: .light))
            .kerning(0.2)
            .lineLimit(1)
            .foregroundStyle(.white)
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: taps) {
            Task { await run() }
        }
        .task(id: autoPlay) {
            guard autoPlay, !reduceMotion else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.autoPlayPause))
                if Task.isCancelled { return }
                await run()
            }
        }
    }

    /// One run of the clock. The clock ends where it began (label showing), so dropping back to
    /// idle afterwards is invisible.
    private func run() async {
        guard start == nil else { return }
        start = .now
        try? await Task.sleep(for: .seconds(Clock.total))
        start = nil
    }

    private func draw(_ context: GraphicsContext, t: Double) {
        let still = reduceMotion
        let r = Metrics.r
        let c = CGPoint(x: Metrics.bleed + Metrics.width / 2, y: Metrics.bleed + r)
        let width = pillWidth(t, still: still)
        let shape = outline(center: c, width: width, sag: wobble(t, still: still))

        var glow = context
        glow.addFilter(.blur(radius: 7))
        glow.stroke(shape, with: .color(.rgba(2, 209, 254, 0.7)), lineWidth: 6)
        context.fill(shape, with: .color(Color(hex: 0x2c2e2f)))
        context.stroke(shape, with: .color(cyan), lineWidth: Metrics.stroke)
        let turned = seg(t, Clock.green) - seg(t, Clock.cyan)
        if turned > 0 {
            context.stroke(shape, with: .color(green.opacity(turned)), lineWidth: Metrics.stroke)
        }

        // Progress: a white dot laps the ring and leaves a white arc behind it.
        let lap = easeInOut(seg(t, Clock.lap))
        let arcOpacity = still ? 0 : seg(t, Clock.arcIn) * (1 - seg(t, Clock.arcOut))
        if arcOpacity > 0, lap > 0.001 {
            var arc = Path()
            arc.addArc(
                center: c, radius: Metrics.ringR,
                startAngle: .degrees(-90), endAngle: .degrees(-90 + min(360 * lap, 359.9)), clockwise: false
            )
            context.stroke(
                arc, with: .color(.white.opacity(arcOpacity)),
                style: StrokeStyle(lineWidth: 2.8, lineCap: .round)
            )
        }

        // A white ring spreads from where the knob was pressed.
        if !still, t > 0, t < Clock.ripple.1 {
            let spread = seg(t, Clock.ripple)
            context.stroke(
                disc(CGPoint(x: Metrics.bleed + r, y: c.y), Metrics.knobR + 14 * easeOut(spread)),
                with: .color(.white.opacity(0.9 * (1 - spread))), lineWidth: 2
            )
        }

        // The knob keeps its place at the pill's left end, which is the middle once it is a circle. It
        // fades out there while the result pill opens around it, and comes back at the left end.
        let knobShown = still ? 1 : 1 - seg(t, Clock.knobOut) + seg(t, Clock.knobIn)
        if knobShown > 0 {
            var knob = context
            knob.opacity = knobShown
            let centred = t > Clock.expand.0 && t < Clock.knobIn.0
            knob.translateBy(x: centred ? c.x : c.x - width / 2 + r, y: c.y)
            knob.scaleBy(x: 0.6 + 0.4 * knobShown, y: 0.6 + 0.4 * knobShown)
            knob.fill(disc(.zero, Metrics.knobR), with: .color(cyan))
            if t > 0, t < Clock.flash.1 {
                knob.fill(disc(.zero, Metrics.knobR), with: .color(.white.opacity(0.8 * (1 - seg(t, Clock.flash)))))
            }

            // 0 = download arrow, 1 = stop square.
            let morph = still ? 0 : seg(t, Clock.toStop) - seg(t, Clock.toArrow)
            let arrowOpacity = 1 - min(1, morph / 0.45)
            if arrowOpacity > 0 {
                var arrow = knob
                arrow.rotate(by: .radians(morph * 0.75 * .pi))
                var path = Path()
                path.move(to: CGPoint(x: 0, y: -7.5))
                path.addLine(to: CGPoint(x: 0, y: 7.5))
                path.move(to: CGPoint(x: -6, y: 1.5))
                path.addLine(to: CGPoint(x: 0, y: 7.5))
                path.addLine(to: CGPoint(x: 6, y: 1.5))
                arrow.stroke(
                    path, with: .color(.white.opacity(arrowOpacity)),
                    style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
                )
            }
            let squareOpacity = seg(morph, (0.3, 0.7))
            if squareOpacity > 0 {
                var square = knob
                square.rotate(by: .radians((morph - 1) * 0.75 * .pi))
                square.scaleBy(x: 0.6 + 0.4 * morph, y: 0.6 + 0.4 * morph)
                square.fill(
                    Path(roundedRect: CGRect(x: -5.5, y: -5.5, width: 11, height: 11), cornerRadius: 1.5),
                    with: .color(.white.opacity(squareOpacity))
                )
            }
        }

        // Then the dot is thrown off the top, turns green and drops into the opened pill.
        let dotR = still ? 0 : Metrics.dotR * easeOutBack(seg(t, Clock.dotIn)) * (1 - seg(t, Clock.dotOut))
        if dotR > 0 {
            var center = c
            var radii = CGSize(width: dotR, height: dotR)
            if t <= Clock.lap.1 {
                let a = (-90 + 360 * lap) * .pi / 180
                center = CGPoint(x: c.x + Metrics.ringR * cos(a), y: c.y + Metrics.ringR * sin(a))
            } else {
                let apex = -Metrics.ringR - Metrics.apex
                let fall = seg(t, Clock.fall)
                let y = t < Clock.launch.1
                    ? -Metrics.ringR - Metrics.apex * easeOut(seg(t, Clock.launch))
                    : apex + (r - 7 - apex) * fall * fall
                // Faster means longer and thinner.
                let speed = t < Clock.launch.1 ? 1 - seg(t, Clock.launch) : fall
                center = CGPoint(x: c.x, y: c.y + y)
                radii = CGSize(width: dotR * (1 - 0.2 * speed), height: dotR * (1 + 0.5 * speed))
            }
            let drop = Path(ellipseIn: CGRect(
                x: center.x - radii.width, y: center.y - radii.height,
                width: radii.width * 2, height: radii.height * 2
            ))
            context.fill(drop, with: .color(.white))
            context.fill(drop, with: .color(green.opacity(seg(t, (Clock.launch.0, Clock.launch.0 + 0.3)))))
        }
    }
}

#Preview {
    ZStack {
        Color(hex: 0x313536).ignoresSafeArea()
        NeonDownloadButton(autoPlay: true)
    }
}
