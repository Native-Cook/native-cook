import SwiftUI

/// Call-notes card with a recording row, and a glassy violet dial floating over it as the
/// recording's playback indicator: the thin hand reads the elapsed minutes and a wedge sweeps
/// round ahead of it. The wedge is a comet whose tail stretches with speed and whose leading edge
/// flares as it stops on 12 o'clock, the hand settles with a wobble, a ring leaves the bezel on
/// landing, and at rest the stars on the face twinkle while the lit foot of the bezel breathes.
struct PlaybackDial: View {
    /// Controlled playback position, 0…1 of the recording. Leave `nil` to show the reference's frame (or the autoPlay loop).
    var progress: Double? = nil
    /// Plays by itself: the dial scrubs from the start to the reference's position, holds, rewinds and starts over.
    var autoPlay = false
    var title = "Design Sync Call notes"
    var author = "Zach"
    var updated = "Last updated 2h ago"
    /// Length of the recording in seconds; the minute hand and the time label are derived from it.
    var duration = 1962
    var onPlay: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (twinkle, breathing).
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
            let activity = activity(at: date, clock: clock)
            let landed = landed(at: date, clock: clock)
            let elapsed = Int((value * Double(duration)).rounded())

            ZStack {
                Canvas { context, _ in drawCard(context, value: value, time: time) }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)

                content
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(title), recording playback")
                    .accessibilityValue("\(timecode(elapsed)) of \(timecode(duration))")

                Button(action: onPlay) {
                    PlayGlyph(running: clamp01(activity * 6))
                }
                .buttonStyle(PlayStyle())
                .accessibilityLabel("Play recording")
                .position(x: 40.5, y: Metrics.pillCY)

                Canvas { context, _ in
                    drawDial(context, value: value, time: time, activity: activity, landed: landed)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)
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
            held.retarget(target, duration: reduceMotion ? 0.2 : 0.9, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(2))
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

    /// 0…1: how fast the dial is turning.
    private func activity(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 0 }
        if !controlled { return loopActivity(clock) }
        let u = date.timeIntervalSince(movedAt) / 0.8
        return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
    }

    /// Seconds since the dial landed on its value; large when nothing happened.
    private func landed(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 10 }
        if !controlled { return clock < Metrics.loop - Metrics.reset ? clock - Metrics.landed : 10 }
        return date.timeIntervalSince(movedAt) - 0.7
    }

    private func timecode(_ seconds: Int) -> String {
        "\(seconds / 60):" + String(format: "%02d", seconds % 60)
    }

    private var content: some View {
        let body = Font.system(size: 10.4)
        let grey = Color(hex: 0x8b8b92)
        return ZStack {
            Text(title)
                .font(.system(size: 15.8, weight: .medium))
                .kerning(-0.1)
                .foregroundStyle(Color(hex: 0xf1f1f4))
                .lineLimit(1)
                .frame(width: 289, alignment: .leading)
                .position(x: 24.5 + 144.5, y: 32.3)

            Text(String(author.prefix(1)).uppercased())
                .font(.system(size: 5.6, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 10, height: 10)
                .background(Color(hex: 0xe2795b), in: Circle())
                .position(x: 30.2, y: 53.2)

            (Text(author).fontWeight(.medium).foregroundStyle(Color(hex: 0xe4e4e8))
                + Text("   \(updated)").foregroundStyle(Color(hex: 0x77777e)))
                .font(.system(size: 9.4))
                .kerning(0.2)
                .lineLimit(1)
                .fixedSize()
                .frame(width: 260, alignment: .leading)
                .position(x: 38 + 130, y: 53.2)

            Text(timecode(duration))
                .font(.system(size: 9.4))
                .kerning(0.3)
                .monospacedDigit()
                .foregroundStyle(Color(hex: 0x9c9ca3))
                .fixedSize()
                .frame(width: 47.5, alignment: .trailing)
                .position(x: 240 + 23.75, y: Metrics.pillCY)

            Text("Summary")
                .font(.system(size: 9.8, weight: .semibold))
                .foregroundStyle(Color(hex: 0xbcbcc2))
                .fixedSize()
                .frame(width: 100, alignment: .leading)
                .position(x: 24.5 + 50, y: 126.8)

            Text("The team brainstormed new layouts")
                .font(body)
                .foregroundStyle(grey)
                .fixedSize()
                .frame(width: 220, alignment: .leading)
                .position(x: 24.5 + 110, y: 147)
            Text("and")
                .font(body)
                .foregroundStyle(grey)
                .fixedSize()
                .frame(width: 40, alignment: .leading)
                .position(x: 252 + 20, y: 147)
            Text("aligned on what ships next.")
                .font(body)
                .foregroundStyle(grey)
                .fixedSize()
                .frame(width: 220, alignment: .leading)
                .position(x: 24.5 + 110, y: 163)
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    /// Brightness of twinkle group `group` at `time`.
    private func twinkle(_ time: Double, _ group: Int) -> Double {
        0.62 + 0.38 * (reduceMotion ? 0 : 1) * sin(time * (1.5 + 0.35 * Double(group)) + Double(group) * 2.1)
    }

    /// The note card behind the text: surface, recording pill with its waveform, skeleton lines and dust.
    private func drawCard(_ base: GraphicsContext, value: Double, time: Double) {
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)
        let width = Metrics.width
        let height = Metrics.height

        // The card dissolves towards its foot: everything in this layer is masked by the gradient at the end.
        context.drawLayer { layer in
            let card = CGRect(x: 0, y: 0, width: width, height: height + 20)
            layer.fill(
                Path(roundedRect: card, cornerRadius: Metrics.radius, style: .circular),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0x2e2e3d), location: 0),
                        .init(color: Color(hex: 0x18181d), location: 0.45),
                        .init(color: Color(hex: 0x0e0e11), location: 1),
                    ]),
                    startPoint: .zero, endPoint: CGPoint(x: width * 0.55, y: height)
                )
            )
            layer.stroke(
                Path(roundedRect: card.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5, style: .circular),
                with: .linearGradient(
                    Gradient(colors: [.rgba(150, 150, 185, 0.75), .white.opacity(0.12), .white.opacity(0.05)]),
                    startPoint: .zero, endPoint: CGPoint(x: width, y: height * 0.7)
                ),
                lineWidth: 1
            )

            // Recording pill
            let pill = CGRect(x: Metrics.pillX, y: Metrics.pillY, width: Metrics.pillW, height: Metrics.pillH)
            layer.fill(capsule(pill), with: .color(Color(hex: 0x2a2a2f)))
            layer.stroke(capsule(pill.insetBy(dx: 0.35, dy: 0.35)), with: .color(.white.opacity(0.16)), lineWidth: 0.7)
            var waveform = Path()
            for i in 0..<Metrics.waveBars {
                let h = Metrics.waveHeights[i % Metrics.waveHeights.count]
                let x = Metrics.waveX + Double(i) * Metrics.wavePitch
                waveform.move(to: CGPoint(x: x, y: Metrics.pillCY - h / 2))
                waveform.addLine(to: CGPoint(x: x, y: Metrics.pillCY + h / 2))
            }
            let bars = StrokeStyle(lineWidth: 1.25, lineCap: .round)
            layer.stroke(waveform, with: .color(.white.opacity(0.3)), style: bars)
            var played = layer
            played.clip(to: Path(CGRect(
                x: Metrics.waveX - 1, y: Metrics.pillY,
                width: value * Double(Metrics.waveBars - 1) * Metrics.wavePitch + 1.6, height: Metrics.pillH
            )))
            played.stroke(waveform, with: .color(Color(hex: 0xf4f4f6)), style: bars)
            for i in 0..<3 {
                let cx = 246.4 + Double(i) * 3.35
                layer.fill(Path(ellipseIn: CGRect(x: cx - 0.7, y: Metrics.pillCY - 0.7, width: 1.4, height: 1.4)), with: .color(Color(hex: 0x77777d)))
            }

            // Skeleton lines of the summary
            let lines: [(Double, Double, Double, Double)] = [
                (245.5, 160.8, 50.5, 0.13), (24.5, 184.5, 205, 0.11), (24.5, 198.5, 216, 0.1), (24.5, 225, 186, 0.08),
            ]
            for (x, y, w, alpha) in lines {
                layer.fill(capsule(CGRect(x: x, y: y, width: w, height: 4.6)), with: .color(.white.opacity(alpha)))
            }
            for (x, y, r, group) in Metrics.dust {
                layer.fill(
                    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                    with: .color(.white.opacity(0.8 * twinkle(time, group)))
                )
            }

            var mask = layer
            mask.blendMode = .destinationIn
            mask.fill(
                Path(CGRect(x: -2, y: -2, width: width + 4, height: height + 24)),
                with: .linearGradient(
                    Gradient(colors: [.black, .black.opacity(0)]),
                    startPoint: CGPoint(x: 0, y: 150), endPoint: CGPoint(x: 0, y: height - 6)
                )
            )
        }
    }

    /// The dial that floats over the card.
    private func drawDial(_ base: GraphicsContext, value: Double, time: Double, activity: Double, landed: Double) {
        let ambient = reduceMotion ? 0.0 : 1.0
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)

        let center = CGPoint(x: Metrics.centerX, y: Metrics.centerY)
        let outer = Metrics.outer
        let face = Metrics.face
        func circle(_ c: CGPoint, _ r: Double) -> Path {
            Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
        }
        func at(_ dx: Double, _ dy: Double) -> CGPoint { CGPoint(x: center.x + dx, y: center.y + dy) }

        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 13))
            layer.fill(circle(at(0, 9), outer - 2), with: .color(.black.opacity(0.75)))
        }
        // Glow under the lit foot of the bezel: it breathes.
        var glow = context
        glow.opacity = 0.5 + 0.2 * ambient * sin(time * 1.1) + 0.3 * activity
        glow.drawLayer { layer in
            layer.addFilter(.blur(radius: 20))
            layer.fill(circle(at(6, 26), outer - 22), with: .color(.rgba(186, 96, 240, 0.55)))
        }
        // A ring leaves the dial when it lands.
        let rippleU = landed / 0.9
        if rippleU > 0, rippleU < 1 {
            var ripple = context
            ripple.opacity = 0.5 * (1 - rippleU)
            ripple.drawLayer { layer in
                layer.addFilter(.blur(radius: 1.2))
                layer.stroke(circle(center, outer + 1 + 18 * (1 - pow(1 - rippleU, 3))), with: .color(Color(hex: 0xe7c2ff)), lineWidth: 1.6)
            }
        }

        // Bezel: grey-violet glass that lights up towards its foot.
        context.fill(
            circle(center, outer),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x56526c), location: 0),
                    .init(color: Color(hex: 0x4a4760), location: 0.42),
                    .init(color: Color(hex: 0x5f5488), location: 0.66),
                    .init(color: Color(hex: 0xa37fe6), location: 0.88),
                    .init(color: Color(hex: 0xe2b3fa), location: 1),
                ]),
                startPoint: at(-20, -outer), endPoint: at(20, outer)
            )
        )
        context.stroke(
            circle(center, outer - 0.6),
            with: .conicGradient(
                Gradient(stops: [
                    .init(color: .rgba(170, 160, 235, 0.2), location: 0),
                    .init(color: .rgba(255, 225, 255, 0.85), location: 0.22),
                    .init(color: .rgba(170, 160, 235, 0.15), location: 0.5),
                    .init(color: .rgba(176, 166, 240, 0.95), location: 0.86),
                    .init(color: .rgba(170, 160, 235, 0.2), location: 1),
                ]),
                center: center
            ),
            lineWidth: 1.2
        )

        // Face
        context.fill(
            circle(center, face),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x1c1b25), Color(hex: 0x1b1a23), Color(hex: 0x24143a)]),
                startPoint: at(0, -face), endPoint: at(0, face)
            )
        )
        var inside = context
        inside.clip(to: circle(center, face))
        inside.fill(
            circle(at(-34, face + 18), 84),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .rgba(112, 74, 232, 0.95), location: 0.2),
                    .init(color: .rgba(112, 74, 232, 0.45), location: 0.6),
                    .init(color: .rgba(112, 74, 232, 0), location: 1),
                ]),
                center: at(-34, face + 18), startRadius: 0, endRadius: 84
            )
        )
        inside.fill(
            circle(at(32, face + 16), 84),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .rgba(226, 88, 236, 1), location: 0.2),
                    .init(color: .rgba(204, 76, 226, 0.5), location: 0.58),
                    .init(color: .rgba(204, 76, 226, 0), location: 1),
                ]),
                center: at(32, face + 16), startRadius: 0, endRadius: 84
            )
        )
        // Shadow the bezel casts onto the face.
        inside.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            layer.stroke(circle(at(0, 3), face + 4), with: .color(.black.opacity(0.6)), lineWidth: 10)
        }
        context.stroke(
            circle(center, face - 0.4),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(20, 20, 28, 0.9), location: 0),
                    .init(color: .rgba(60, 50, 90, 0.6), location: 0.6),
                    .init(color: .rgba(246, 214, 255, 0.95), location: 1),
                ]),
                startPoint: at(0, -face), endPoint: at(0, face)
            ),
            lineWidth: 0.9
        )
        context.stroke(circle(center, 72.5), with: .color(.white.opacity(0.07)), lineWidth: 0.6)

        func ticks(step: Double, from: Double, to: Double, keep: (Int) -> Bool) -> Path {
            var path = Path()
            var i = 0
            while Double(i) * step < 360 {
                if keep(i) {
                    let a = (Double(i) * step - 90) * .pi / 180
                    path.move(to: at(from * cos(a), from * sin(a)))
                    path.addLine(to: at(to * cos(a), to * sin(a)))
                }
                i += 1
            }
            return path
        }
        context.stroke(
            ticks(step: 9, from: 68.5, to: 72.5) { $0 % 5 != 0 },
            with: .color(.white.opacity(0.13)), style: StrokeStyle(lineWidth: 0.6, lineCap: .round)
        )
        context.stroke(
            ticks(step: 45, from: 62, to: 67) { _ in true },
            with: .color(.rgba(238, 236, 248, 0.85)), style: StrokeStyle(lineWidth: 1.1, lineCap: .round)
        )
        for (x, y, r, group) in Metrics.stars {
            context.fill(circle(at(x, y), r), with: .color(Color(hex: 0xe9e2ff, opacity: twinkle(time, group))))
        }
        // A generic "sound" glyph under the hub: three short bars.
        var glyph = Path()
        for (dx, top, length) in [(-4.5, 23.0, 4.0), (0.0, 20.5, 9.0), (4.5, 23.0, 4.0)] {
            glyph.move(to: at(dx, top))
            glyph.addLine(to: at(dx, top + length))
        }
        context.stroke(glyph, with: .color(.rgba(178, 164, 220, 0.3)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))

        // Sweep wedge: a conic gradient whose seam is the bright leading edge. Its tail stretches with speed.
        let head = value * Metrics.sweepTurns * 360 - 90
        let length = (Metrics.tail + 76 * activity + 3 * ambient * sin(time * 1.3)) / 360
        context.fill(
            circle(center, Metrics.wedge),
            with: .conicGradient(
                Gradient(stops: [
                    .init(color: .rgba(120, 76, 236, 0), location: 0),
                    .init(color: .rgba(120, 76, 236, 0), location: 1 - length),
                    .init(color: .rgba(128, 84, 242, 0.72), location: 1 - 0.56 * length),
                    .init(color: .rgba(200, 140, 255, 1), location: 1 - 0.2 * length),
                    .init(color: Color(hex: 0xfae4ff), location: 1),
                ]),
                center: center, angle: .degrees(head)
            )
        )
        // The wedge sinks into the face towards the hub.
        context.fill(
            circle(center, Metrics.wedge),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .rgba(27, 26, 35, 0.7), location: 0.1),
                    .init(color: .rgba(27, 26, 35, 0.22), location: 0.5),
                    .init(color: .rgba(27, 26, 35, 0), location: 0.9),
                ]),
                center: center, startRadius: 0, endRadius: Metrics.wedge
            )
        )
        var edge = context
        edge.translateBy(x: center.x, y: center.y)
        edge.rotate(by: .degrees(head))
        // The edge flares up as the dial lands.
        let flareU = landed / 0.7
        var flare = edge
        flare.opacity = min(1, 0.55 + 0.25 * activity + (flareU > 0 && flareU < 1 ? 0.45 * (1 - flareU) : 0))
        flare.drawLayer { layer in
            layer.addFilter(.blur(radius: 3.5))
            layer.fill(Path(CGRect(x: 16, y: -1.5, width: Metrics.wedge - 16, height: 3)), with: .color(Color(hex: 0xf1c9ff)))
        }
        edge.fill(Path(CGRect(x: 8, y: -0.45, width: Metrics.wedge - 8, height: 0.9)), with: .color(.white.opacity(0.92)))

        // Minute hand: settles with a small damped wobble.
        let wobble = landed > 0 && landed < 1.2 ? 3.2 * exp(-5 * landed) * sin(17 * landed) : 0
        var hand = context
        hand.translateBy(x: center.x, y: center.y)
        hand.rotate(by: .degrees(value * Double(duration) / 60 * 6 + wobble))
        var line = Path()
        line.move(to: .zero)
        line.addLine(to: CGPoint(x: 0, y: -Metrics.hand))
        hand.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.2))
            layer.translateBy(x: 0.6, y: 1.2)
            layer.stroke(line, with: .color(.black.opacity(0.45)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
        }
        hand.stroke(line, with: .color(Color(hex: 0xdcdce4)), style: StrokeStyle(lineWidth: 1.05, lineCap: .round))

        // Hub
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 3))
            layer.fill(circle(at(0.5, 2.5), Metrics.hub + 0.5), with: .color(.black.opacity(0.5)))
        }
        context.fill(
            circle(center, Metrics.hub),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .white, location: 0),
                    .init(color: Color(hex: 0xececf0), location: 0.5),
                    .init(color: Color(hex: 0xb9b9c2), location: 1),
                ]),
                center: at(-2.4, -2.8), startRadius: 0, endRadius: Metrics.hub * 1.7
            )
        )
    }
}

private enum Metrics {
    static let width = 330.0
    static let height = 250.0
    static let radius = 10.0
    /// The canvases overflow the card by this much on every side to fit the dial's shadow and ripple.
    static let bleed = 30.0

    static let centerX = 165.0
    static let centerY = 127.0
    static let outer = 88.0
    static let face = 77.7
    static let wedge = 58.0
    static let hand = 41.7
    static let hub = 8.15

    static let pillX = 24.5
    static let pillY = 70.4
    static let pillW = 273.0
    static let pillH = 31.6
    static let pillCY = pillY + pillH / 2
    static let waveX = 57.4
    static let wavePitch = 2.8
    static let waveBars = 66
    static let waveHeights: [Double] = [3.6, 3.6, 5.4, 3.6, 13.5, 3.6, 9, 15, 15, 12, 6, 10, 4, 14, 8, 5]

    /// The reference's frame: 16:30 into a 32:42 recording. The minute hand reads the elapsed time on
    /// a 60 minute face; the wedge is a faster sweep geared to land on 12 o'clock at that position.
    static let rest = 990.0 / 1962.0
    static let sweepTurns = 2 / rest
    static let tail = 64.0

    /// The autoPlay loop built around it (seconds).
    static let start = 0.5
    static let run = 2.8
    static let hold = 4.2
    static let reset = 1.0
    static let loop = start + run + hold + reset
    static let landed = start + run
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = landed + 1.8

    /// Stars on the dial face, relative to its centre: x, y, radius, twinkle group.
    static let stars: [(Double, Double, Double, Int)] = [
        (-35.5, -34, 0.6, 0), (-31.7, -20.8, 0.7, 1), (37.4, -34, 0.6, 2), (34, -20.8, 0.7, 0),
        (-63, -29.3, 0.9, 1), (65, -29.3, 0.9, 2), (-58, 23.9, 1.1, 2), (60, 23.9, 1.1, 0),
        (-27.2, 37.8, 0.6, 0), (29.4, 37.8, 0.6, 1), (-4, 43.5, 0.5, 2), (5.7, 43.5, 0.5, 1),
        (-46, 63.4, 1, 1), (48, 63.4, 1, 2), (-61.6, 16.3, 0.4, 0), (63.4, 16.3, 0.4, 1),
    ]
    /// Dust at the foot of the card, in card coordinates.
    static let dust: [(Double, Double, Double, Int)] = [
        (44.7, 184, 0.6, 0), (50.7, 200.8, 0.5, 1), (61.3, 200.8, 0.4, 2), (97.5, 210, 0.5, 0),
        (235, 210, 0.5, 1), (269.7, 200.8, 0.5, 2), (281.8, 200, 0.6, 0), (287.8, 184, 0.6, 1),
    ]
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Playback position at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.landed { return Metrics.rest * easeInOutCubic((t - Metrics.start) / Metrics.run) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

/// 0…1: how fast the dial is turning at `t` seconds into the loop.
private func loopActivity(_ t: Double) -> Double {
    if t >= Metrics.loop - Metrics.reset { return sin(.pi * clamp01((t - (Metrics.loop - Metrics.reset)) / Metrics.reset)) }
    let u = (t - Metrics.start) / Metrics.run
    return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
}

/// Violet round button: play while the dial rests, pause while it runs.
private struct PlayGlyph: View {
    var running: Double

    var body: some View {
        Canvas { context, _ in
            let circle = Path(ellipseIn: CGRect(x: 1.4, y: 1.4, width: 21.2, height: 21.2))
            context.fill(
                circle,
                with: .linearGradient(
                    Gradient(colors: [Color(hex: 0x8777fb), Color(hex: 0x6654e6)]),
                    startPoint: CGPoint(x: 4, y: 2), endPoint: CGPoint(x: 20, y: 22)
                )
            )
            context.stroke(Path(ellipseIn: CGRect(x: 1.8, y: 1.8, width: 20.4, height: 20.4)), with: .color(.white.opacity(0.22)), lineWidth: 0.8)

            var triangle = Path()
            triangle.move(to: CGPoint(x: 10.1, y: 8.9))
            triangle.addLine(to: CGPoint(x: 15.2, y: 12))
            triangle.addLine(to: CGPoint(x: 10.1, y: 15.1))
            triangle.closeSubpath()
            let play = GraphicsContext.Shading.color(.white.opacity(1 - running))
            context.fill(triangle, with: play)
            context.stroke(triangle, with: play, style: StrokeStyle(lineWidth: 1.1, lineJoin: .round))

            var pause = Path()
            pause.move(to: CGPoint(x: 10.2, y: 9))
            pause.addLine(to: CGPoint(x: 10.2, y: 15))
            pause.move(to: CGPoint(x: 13.8, y: 9))
            pause.addLine(to: CGPoint(x: 13.8, y: 15))
            context.stroke(pause, with: .color(.white.opacity(running)), style: StrokeStyle(lineWidth: 1.9, lineCap: .round))
        }
        .frame(width: 24, height: 24)
    }
}

private struct PlayStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Circle().inset(by: -8))
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

#Preview {
    ZStack {
        Color(hex: 0x040404).ignoresSafeArea()
        PlaybackDial(autoPlay: true)
    }
}
