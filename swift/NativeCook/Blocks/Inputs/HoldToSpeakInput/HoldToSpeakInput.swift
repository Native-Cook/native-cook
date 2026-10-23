import SwiftUI
import UIKit

/// A chat field that is also a push-to-talk button. Tap it to type; press and hold it to speak: the
/// suggestion chips step aside, level bars and "Listening" replace the placeholder, a mic sits under
/// the finger and the aurora on the pill's edge gathers there and follows it. Slide up to arm the
/// cancel, release in place to drop the transcript into the field.
///
/// Nothing is recorded here: the bars are a drawn pattern. Start and stop a real recorder from
/// `onRecordStart` and `onRecordEnd`, and hand its result in as `transcript`.
struct HoldToSpeakInput: View {
    /// The field's text. Leave it out and the component keeps the text itself.
    var text: Binding<String>? = nil
    /// Holds, speaks and releases by itself, then holds again and cancels, in a loop.
    var autoPlay = false
    var placeholder = "Start typing or hold to speak."
    /// The suggestion chips, one list per row, top row first.
    var suggestionRows = [["Plan my day"], ["Schedule", "Reschedule"], ["Find time", "Create todo list"]]
    /// The chip drawn with the gradient outline.
    var highlighted = "Plan my day"
    var listeningLabel = "Listening"
    var slideHint = "Slide up to cancel"
    var cancelHint = "Release to cancel"
    /// What a finished recording puts in the field.
    var transcript = "Plan my day around the 3 pm review"
    /// The aurora along the pill's edge, left to right.
    var glowColors = [Color(hex: 0x2fd6c3), Color(hex: 0x7be36a), Color(hex: 0xf2d94a)]
    /// The same while the cancel is armed.
    var cancelColors = [Color(hex: 0xff8a3d), Color(hex: 0xff4d4f), Color(hex: 0xff3b6b)]
    /// Called with the text when the keyboard's send key is pressed or a recording is released.
    var onSend: ((String) -> Void)? = nil
    var onSuggestion: ((String) -> Void)? = nil
    var onRecordStart: (() -> Void)? = nil
    /// Called when the finger lifts; `true` when the recording was cancelled.
    var onRecordEnd: ((Bool) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var sim = Simulation()
    @State private var own = ""
    /// The text in the field was typed by hand, so the loop must leave it alone.
    @State private var typed = false

    /// Per-frame state: a finger (real or scripted) and everything that eases after it.
    private final class Simulation {
        enum Phase { case idle, pressing, listening, sent, clearing }
        enum Event { case tap, listenStart, arm(Bool), send(byUser: Bool), cancel, clear }

        var last: Date? = nil
        var time = 0.0
        var clock = 0.0
        var looping = false
        /// Seconds the loop still waits after a touch.
        var hold = 0.0
        var phase = Phase.idle
        var down = false
        /// The current run was started by the loop, not by a finger.
        var scripted = false
        var px = Metrics.width / 2
        var py = 0.0
        var downAt = 0.0
        var listenAt = 0.0
        var sentAt = 0.0
        var armed = false
        var labelRight = false

        var listen = 0.0
        var field = 1.0
        var cancel = 0.0
        var side = 0.0
        var power = 0.0
        var chips = 1.0
        var micX = Chase(Metrics.width / 2)
        var lift = Chase(0)
        var glowX = Chase(Metrics.width * Metrics.restX)
        var drop = Chase(0)
        var pop = Chase(1)
        var events: [Event] = []

        func press(x: Double, scripted: Bool) {
            down = true
            self.scripted = scripted
            px = x
            py = 0
            downAt = time
            armed = false
            phase = .pressing
            if !scripted { hold = Metrics.resume }
        }

        func move(x: Double, y: Double) {
            px = x
            py = y
        }

        func release() {
            guard down else { return }
            down = false
            switch phase {
            case .pressing:
                phase = .idle
                if !scripted { events.append(.tap) }
            case .listening where armed:
                phase = .idle
                if !scripted { events.append(.cancel) }
            case .listening:
                show()
                events.append(.send(byUser: !scripted))
            default:
                break
            }
        }

        /// A text lands in the field, stays a moment and clears.
        func show() {
            phase = .sent
            sentAt = time
            field = 0
            drop = Chase(-Metrics.dropFrom)
        }

        func advance(to date: Date, engaged: Bool, still: Bool) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            time += dt

            if engaged || (!scripted && phase != .idle) { hold = Metrics.resume }
            if hold > 0 {
                // The loop was interrupted: its own press ends without sending anything.
                if down, scripted {
                    down = false
                    phase = .idle
                }
                hold -= dt
                if hold <= 0, looping { clock = 0 }
            } else if looping {
                clock += dt
                if clock >= Metrics.loop { clock -= Metrics.loop }
                if let finger = scriptedFinger(at: clock) {
                    if !down { press(x: finger.x * Metrics.width, scripted: true) }
                    move(x: finger.x * Metrics.width, y: finger.y)
                } else if down, scripted {
                    release()
                }
            }

            switch phase {
            case .pressing where time - downAt >= Metrics.holdDelay:
                phase = .listening
                listenAt = time
                labelRight = px < Metrics.width / 2
                side = labelRight ? 1 : 0
                micX = Chase(micSlot(px))
                pop = Chase(0.5)
                if !scripted { events.append(.listenStart) }
            case .listening:
                let now = py < (armed ? Metrics.disarmAt : Metrics.armAt)
                if now != armed {
                    armed = now
                    if !scripted { events.append(.arm(now)) }
                }
                // The label keeps to the side the finger is not on.
                if px > Metrics.width / 2 + Metrics.sideSlack { labelRight = false }
                if px < Metrics.width / 2 - Metrics.sideSlack { labelRight = true }
            case .sent where time - sentAt >= Metrics.transcriptHold:
                phase = .clearing
            case .clearing where field < 0.03:
                phase = .idle
                events.append(.clear)
            default:
                break
            }

            let listening = phase == .listening
            listen = approach(listen, listening ? 1 : 0, 16, dt)
            field = approach(field, listening || phase == .clearing ? 0 : 1, 12, dt)
            cancel = approach(cancel, listening && armed ? 1 : 0, 14, dt)
            side = approach(side, labelRight ? 1 : 0, 18, dt)
            power = approach(power, listening ? 1 : 0, 8, dt)
            chips = phase == .idle ? min(1, chips + dt / Metrics.chipsIn) : max(0, chips - dt / Metrics.chipsOut)
            if listening { micX.step(to: micSlot(px), omega: 30, zeta: 0.9, dt) }
            lift.step(to: listening ? min(0, max(py, -Metrics.maxLift)) : 0, omega: 22, zeta: 0.75, dt)
            let rest = Metrics.width * (Metrics.restX + (still ? 0 : Metrics.restSway * sin(tau * time / Metrics.breath)))
            glowX.step(to: listening ? micX.x : rest, omega: 12, zeta: 0.9, dt)
            drop.step(to: 0, omega: 18, zeta: 0.55, dt)
            pop.step(to: 1, omega: 20, zeta: 0.5, dt)
        }
    }

    private var current: String { text?.wrappedValue ?? own }

    private var field: Binding<String> {
        Binding(
            get: { current },
            set: { new in
                store(new)
                typed = !new.isEmpty
            }
        )
    }

    var body: some View {
        TimelineView(.animation) { timeline in
            let date = timeline.date
            let _ = sim.advance(to: date, engaged: focused || typed, still: reduceMotion)
            let s = sim
            let breath = reduceMotion ? 0 : sin(tau * s.time / Metrics.breath + 1.2)
            let blink = min(1, max(0, 0.5 + 3 * sin(tau * s.time / Metrics.blink)))
            let voice = reduceMotion ? 0.4 : s.time - s.listenAt

            ZStack(alignment: .topLeading) {
                chips(s.chips)

                Canvas { context, _ in
                    drawPill(context, glowX: s.glowX.x, power: s.power, cancel: s.cancel, breath: breath)
                }
                .frame(width: Metrics.width + Metrics.bleed * 2, height: Metrics.height + Metrics.bleed * 2)
                .offset(x: -Metrics.bleed, y: Metrics.pillY - Metrics.bleed)
                .allowsHitTesting(false)

                TextField("", text: field, prompt: Text(placeholder).foregroundStyle(Color(hex: 0x9a9a9a)))
                    .font(.system(size: 16))
                    .foregroundStyle(Color(hex: 0xededed))
                    .tint(Color(hex: 0x4c8dff))
                    .focused($focused)
                    .submitLabel(.send)
                    .onSubmit(submit)
                    .frame(width: Metrics.width - Metrics.inset * 2, height: Metrics.height)
                    .offset(x: Metrics.inset, y: Metrics.pillY + (reduceMotion ? 0 : s.drop.x))
                    .opacity(s.field)
                    // Until it has the focus, the layer on top decides between a tap and a hold.
                    .allowsHitTesting(focused)

                // The caret of the resting field; the system draws its own once the field is focused.
                RoundedRectangle(cornerRadius: 1, style: .circular)
                    .fill(Color(hex: 0x4c8dff))
                    .frame(width: 2, height: 20)
                    .offset(x: Metrics.inset - 1, y: Metrics.pillY + Metrics.height / 2 - 10)
                    .opacity(focused || !current.isEmpty ? 0 : s.field * blink)
                    .allowsHitTesting(false)

                ForEach([false, true], id: \.self) { right in
                    HStack(spacing: 10) {
                        Canvas { context, _ in
                            drawBars(context, time: voice, strength: (1 - s.cancel) * s.listen)
                        }
                        .frame(width: Metrics.barPitch * 4 + Metrics.barWidth, height: Metrics.barMax)
                        Text(listeningLabel)
                            .font(.system(size: 16))
                            .foregroundStyle(Color(hex: 0x7a7a7a))
                            .lineLimit(1)
                    }
                    .frame(width: Metrics.width - Metrics.inset * 2, height: Metrics.height, alignment: right ? .trailing : .leading)
                    .offset(x: Metrics.inset, y: Metrics.pillY)
                    .opacity(s.listen * (right ? s.side : 1 - s.side))
                    .allowsHitTesting(false)
                }

                Canvas { context, _ in
                    drawMic(context)
                }
                .frame(width: 24, height: 24)
                .scaleEffect(reduceMotion ? 1 : s.pop.x)
                .offset(x: s.micX.x - 12, y: Metrics.pillY + Metrics.height / 2 - 12 + s.lift.x * 0.2)
                .opacity(s.listen * (1 - s.cancel))
                .allowsHitTesting(false)

                ZStack {
                    hint(slideHint, cross: false, color: Color(hex: 0x9a9a9a)).opacity(1 - s.cancel)
                    hint(cancelHint, cross: true, color: Color(hex: 0xe8e8e8)).opacity(s.cancel)
                }
                .frame(width: Metrics.hintWidth, height: Metrics.hintHeight)
                .offset(
                    x: min(max(s.micX.x - Metrics.hintWidth / 2, 0), Metrics.width - Metrics.hintWidth),
                    y: Metrics.pillY - Metrics.hintGap - Metrics.hintHeight + s.lift.x
                )
                .opacity(s.listen)
                .allowsHitTesting(false)

                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: Metrics.width, height: Metrics.height)
                    .offset(y: Metrics.pillY)
                    .gesture(touch)
                    .allowsHitTesting(!focused)
                    .accessibilityLabel(Text(verbatim: placeholder))
                    .accessibilityAddTraits(.isButton)
            }
            .frame(width: Metrics.width, height: Metrics.pillY + Metrics.height, alignment: .topLeading)
            // One piece for the parent: when the keyboard moves the layout, the layers move together.
            .geometryGroup()
            .onChange(of: date) { drain() }
        }
        .onAppear { sim.looping = autoPlay && !reduceMotion }
        .onChange(of: autoPlay) { sim.looping = autoPlay && !reduceMotion }
        .onChange(of: reduceMotion) { sim.looping = autoPlay && !reduceMotion }
    }

    private func chips(_ progress: Double) -> some View {
        VStack(alignment: .leading, spacing: Metrics.chipGap) {
            ForEach(Array(suggestionRows.enumerated()), id: \.offset) { index, row in
                let shown = rowProgress(progress, order: suggestionRows.count - 1 - index, rows: suggestionRows.count)
                HStack(spacing: Metrics.chipGap) {
                    ForEach(row, id: \.self) { label in
                        Button {
                            suggest(label)
                        } label: {
                            Text(label)
                                .font(.system(size: 15, weight: .medium))
                                .foregroundStyle(Color(hex: label == highlighted ? 0xdcdcdc : 0xbdbdbd))
                                .lineLimit(1)
                                .padding(.horizontal, 16)
                                .frame(height: Metrics.chipHeight)
                                .background(Capsule().fill(Color(hex: label == highlighted ? 0x1b201e : 0x1e1e1e)))
                                .overlay {
                                    if label == highlighted {
                                        Capsule()
                                            .strokeBorder(
                                                LinearGradient(colors: glowColors.map { $0.opacity(0.8) }, startPoint: .leading, endPoint: .trailing),
                                                lineWidth: 1
                                            )
                                    }
                                }
                        }
                        .buttonStyle(ChipStyle())
                    }
                }
                .opacity(shown)
                .offset(y: reduceMotion ? 0 : (1 - shown) * Metrics.chipRise)
            }
        }
        .frame(width: Metrics.width, height: Metrics.pillY - Metrics.chipsToPill, alignment: .bottomLeading)
        .allowsHitTesting(progress > 0.9)
    }

    private func hint(_ label: String, cross: Bool, color: Color) -> some View {
        VStack(spacing: 6) {
            Canvas { context, _ in
                var mark = Path()
                if cross {
                    mark.move(to: CGPoint(x: 1.5, y: 1.5))
                    mark.addLine(to: CGPoint(x: 10.5, y: 10.5))
                    mark.move(to: CGPoint(x: 10.5, y: 1.5))
                    mark.addLine(to: CGPoint(x: 1.5, y: 10.5))
                } else {
                    mark.move(to: CGPoint(x: 1, y: 8.5))
                    mark.addLine(to: CGPoint(x: 6, y: 3.5))
                    mark.addLine(to: CGPoint(x: 11, y: 8.5))
                }
                context.stroke(mark, with: .color(color), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
            }
            .frame(width: 12, height: 12)
            Text(label)
                .font(.system(size: 14))
                .foregroundStyle(color)
                .lineLimit(1)
                .frame(height: 18)
        }
    }

    /// A touch on the resting field: let go at once to type, keep it down to speak.
    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if !sim.down || sim.scripted { sim.press(x: value.startLocation.x, scripted: false) }
                sim.move(x: value.location.x, y: value.translation.height)
            }
            .onEnded { _ in sim.release() }
    }

    private func store(_ new: String) {
        if let text { text.wrappedValue = new } else { own = new }
    }

    private func submit() {
        let sent = current.trimmingCharacters(in: .whitespacesAndNewlines)
        store("")
        typed = false
        if !sent.isEmpty { onSend?(sent) }
    }

    private func suggest(_ label: String) {
        store(label)
        typed = false
        sim.scripted = false
        sim.show()
        onSuggestion?(label)
    }

    /// Applies what the last frame decided: state can't change while the frame is being drawn.
    private func drain() {
        guard !sim.events.isEmpty else { return }
        let events = sim.events
        sim.events.removeAll()
        for event in events {
            switch event {
            case .tap:
                store("")
                typed = false
                focused = true
            case .listenStart:
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                onRecordStart?()
            case .arm(let on):
                UIImpactFeedbackGenerator(style: on ? .rigid : .light).impactOccurred()
            case .send(let byUser):
                store(transcript)
                typed = false
                guard byUser else { break }
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                onRecordEnd?(false)
                onSend?(transcript)
            case .cancel:
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onRecordEnd?(true)
            case .clear:
                store("")
            }
        }
    }

    /// The aurora behind the pill, the pill, and its hairline.
    private func drawPill(_ context: GraphicsContext, glowX: Double, power: Double, cancel: Double, breath: Double) {
        let o = Metrics.bleed
        let rect = CGRect(x: o, y: o, width: Metrics.width, height: Metrics.height)
        let pill = Path(roundedRect: rect, cornerRadius: Metrics.radius, style: .circular)
        let alpha = (Metrics.restGlow + Metrics.restPulse * breath) * (1 - power) + power
        // At rest the light lies wide along the bottom edge; held, it gathers under the finger.
        let reach = Metrics.glowReach * (Metrics.restSpread - (Metrics.restSpread - 1) * power)
        let sink = Metrics.restSink * (1 - power)
        let x = o + glowX

        for (colors, share) in [(glowColors, 1 - cancel), (cancelColors, cancel * 0.9)] where share > 0.01 {
            let shading = GraphicsContext.Shading.linearGradient(
                Gradient(stops: [
                    .init(color: colors[0].opacity(0), location: 0),
                    .init(color: colors[0], location: 0.24),
                    .init(color: colors[1], location: 0.5),
                    .init(color: colors[2], location: 0.76),
                    .init(color: colors[2].opacity(0), location: 1),
                ]),
                startPoint: CGPoint(x: x - reach, y: 0), endPoint: CGPoint(x: x + reach, y: 0)
            )
            var wide = context
            wide.opacity = alpha * share * 0.55
            wide.addFilter(.blur(radius: 14))
            wide.fill(Path(roundedRect: rect.insetBy(dx: -2, dy: -2).offsetBy(dx: 0, dy: sink), cornerRadius: Metrics.radius + 2, style: .circular), with: shading)
            var tight = context
            tight.opacity = alpha * share
            tight.addFilter(.blur(radius: 5))
            tight.fill(Path(roundedRect: rect.insetBy(dx: -1, dy: -1).offsetBy(dx: 0, dy: sink * 0.5), cornerRadius: Metrics.radius + 1, style: .circular), with: shading)
        }

        context.fill(pill, with: .color(Color(hex: 0x161616)))

        let edge = Path(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5, style: .circular)
        context.stroke(edge, with: .color(.white.opacity(0.07)), lineWidth: 1)
        for (colors, share) in [(glowColors, 1 - cancel), (cancelColors, cancel)] where share > 0.01 {
            var line = context
            line.opacity = (0.45 + 0.55 * power) * share
            line.stroke(
                edge,
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: colors[0].opacity(0.15), location: 0),
                        .init(color: colors[0], location: 0.24),
                        .init(color: colors[1], location: 0.5),
                        .init(color: colors[2], location: 0.76),
                        .init(color: colors[2].opacity(0.15), location: 1),
                    ]),
                    startPoint: CGPoint(x: x - reach, y: 0), endPoint: CGPoint(x: x + reach, y: 0)
                ),
                lineWidth: 1
            )
        }
    }

    /// Five level bars that move like a voice; `strength` flattens them into dots.
    private func drawBars(_ context: GraphicsContext, time: Double, strength: Double) {
        for i in 0..<5 {
            let level = min(1, max(0, 0.5 + 0.34 * sin(time * barRates[i] + barPhases[i]) + 0.22 * sin(time * barRipples[i] + barPhases[4 - i])))
            let h = Metrics.barMin + (Metrics.barMax - Metrics.barMin) * level * strength
            let bar = CGRect(x: Double(i) * Metrics.barPitch, y: (Metrics.barMax - h) / 2, width: Metrics.barWidth, height: h)
            context.fill(Path(roundedRect: bar, cornerRadius: Metrics.barWidth / 2), with: .color(.white.opacity(0.92)))
        }
    }

    /// The microphone, in a 24-point box.
    private func drawMic(_ context: GraphicsContext) {
        context.fill(Path(roundedRect: CGRect(x: 9, y: 3, width: 6, height: 11), cornerRadius: 3), with: .color(.white))
        var stand = Path()
        stand.move(to: CGPoint(x: 6.5, y: 10.5))
        stand.addLine(to: CGPoint(x: 6.5, y: 11.5))
        stand.addArc(center: CGPoint(x: 12, y: 11.5), radius: 5.5, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: true)
        stand.addLine(to: CGPoint(x: 17.5, y: 10.5))
        stand.move(to: CGPoint(x: 12, y: 17))
        stand.addLine(to: CGPoint(x: 12, y: 20))
        stand.move(to: CGPoint(x: 8.5, y: 20))
        stand.addLine(to: CGPoint(x: 15.5, y: 20))
        context.stroke(stand, with: .color(.white), style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
    }

    private struct ChipStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.96 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 52.0
    static let radius = 22.0
    /// Text and level bars start this far from the pill's ends.
    static let inset = 22.0
    /// The canvas overflows the pill by this much on every side to fit the aurora.
    static let bleed = 40.0

    static let chipHeight = 36.0
    static let chipGap = 8.0
    static let chipsToPill = 16.0
    /// Top of the pill: three rows of chips and the gap under them.
    static let pillY = chipHeight * 3 + chipGap * 2 + chipsToPill
    static let chipRise = 10.0
    /// The rows come back one after another, the bottom one first, over this long; they leave together.
    static let chipsIn = 0.6
    static let chipsOut = 0.22
    static let chipStagger = 0.22

    static let hintWidth = 160.0
    static let hintHeight = 36.0
    /// Between the hint and the pill while the finger rests on it.
    static let hintGap = 14.0

    static let barWidth = 2.0
    static let barPitch = 7.0
    static let barMin = 3.0
    static let barMax = 14.0

    /// A press shorter than this is a tap, which focuses the field.
    static let holdDelay = 0.18
    // Sliding up this far arms the cancel; coming back down past the second line disarms it.
    static let armAt = -40.0
    static let disarmAt = -28.0
    static let maxLift = 72.0
    /// The label changes side once the finger is this far past the middle.
    static let sideSlack = 20.0
    /// The mic keeps this far from the pill's ends.
    static let micMargin = 30.0
    /// How long the transcript stays in the field, and how far above it starts.
    static let transcriptHold = 1.4
    static let dropFrom = 12.0

    // The aurora at rest: where it sits (share of the width), how far it sways, and how it breathes.
    static let restX = 0.6
    static let restSway = 0.1
    static let restGlow = 0.5
    static let restPulse = 0.1
    static let restSpread = 1.7
    static let restSink = 4.0
    /// Half the length of the lit stretch while it is gathered under the finger.
    static let glowReach = 110.0
    static let breath = 3.2
    static let blink = 1.2

    /// autoPlay period, seconds. The aurora breathes three times and the caret blinks eight times in it.
    static let loop = 9.6
    /// After a touch, or after the field loses focus, the loop waits this long before it starts over.
    static let resume = 3.0
}

/// The finger of the autoPlay loop: seconds, x as a share of the pill's width, y in points (up is
/// negative). The first pass speaks and lets go; the second slides up and cancels.
private let scriptedPasses: [[(t: Double, x: Double, y: Double)]] = [
    [(0.8, 0.8, 0), (1.5, 0.8, 0), (2.1, 0.22, 0), (2.5, 0.22, 0), (3.0, 0.56, 0), (3.4, 0.56, 0)],
    [(6.0, 0.76, 0), (6.6, 0.76, 0), (7.0, 0.6, 0), (7.4, 0.6, -64), (8.0, 0.6, -64)],
]

private func scriptedFinger(at t: Double) -> (x: Double, y: Double)? {
    for pass in scriptedPasses where t >= pass[0].t && t < pass[pass.count - 1].t {
        for i in 1..<pass.count where t < pass[i].t {
            let a = pass[i - 1]
            let b = pass[i]
            let u = (t - a.t) / (b.t - a.t)
            let eased = u * u * (3 - 2 * u)
            return (a.x + (b.x - a.x) * eased, a.y + (b.y - a.y) * eased)
        }
    }
    return nil
}

private let barRates = [9.1, 7.3, 11.2, 8.4, 10.3]
private let barRipples = [17.0, 13.6, 19.3, 15.1, 12.2]
private let barPhases = [0.3, 1.9, 4.1, 2.6, 5.2]

/// A value that follows its target like a spring (mass 1, stiffness omega², damping 2·zeta·omega).
private struct Chase {
    var x: Double
    var v = 0.0

    init(_ x: Double) {
        self.x = x
    }

    mutating func step(to target: Double, omega: Double, zeta: Double, _ dt: Double) {
        v += (omega * omega * (target - x) - 2 * zeta * omega * v) * dt
        x += v * dt
    }
}

/// Moves `x` towards `target`, covering the same share of the distance in the same time at any frame rate.
private func approach(_ x: Double, _ target: Double, _ rate: Double, _ dt: Double) -> Double {
    x + (target - x) * (1 - exp(-rate * dt))
}

private func micSlot(_ x: Double) -> Double {
    min(max(x, Metrics.micMargin), Metrics.width - Metrics.micMargin)
}

/// How far a row of chips has come in, 0…1: `order` 0 is the first to arrive.
private func rowProgress(_ progress: Double, order: Int, rows: Int) -> Double {
    let window = 1 - Metrics.chipStagger * Double(rows - 1)
    let p = min(1, max(0, (progress - Double(order) * Metrics.chipStagger) / window))
    return 1 - pow(1 - p, 3)
}

#Preview {
    ZStack {
        Color(hex: 0x2b2b2b).ignoresSafeArea()
        HoldToSpeakInput(autoPlay: true)
    }
}
