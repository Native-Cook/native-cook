import SwiftUI
import UIKit

/// The little picture at the right of a suggestion card.
enum MorphingMicPillGlyph {
    case ball, medal, court
}

/// A question the panel offers, and the answer it shows for it.
struct MorphingMicPillSuggestion {
    var question: String
    var glyph: MorphingMicPillGlyph
    var lead: String
    var detailTitle: String
    var detail: String
}

/// An assistant's bottom bar whose black mic capsule changes shape with what it is doing. Tap a
/// suggestion and a frosted veil sweeps up over the cards, which wobble as it passes, the round
/// buttons step back and the capsule stretches into a "Thinking…" pill; then the veil lifts on the
/// answer and the capsule springs back. Hold the capsule and it swells and shows level bars; the
/// keyboard button stretches it into a text field, and "+" brings the suggestions back.
///
/// Nothing is recorded here: the bars are a drawn pattern. Start and stop a real recorder from
/// `onRecordStart` and `onRecordEnd`.
struct MorphingMicPill: View {
    /// The field's text. Leave it out and the component keeps the text itself.
    var text: Binding<String>? = nil
    /// Taps the first suggestion, waits for the answer, holds the mic and starts over, in a loop.
    var autoPlay = false
    var heading = "You might want to ask"
    var suggestions = [
        MorphingMicPillSuggestion(
            question: "Why is grass so different\nin tennis?", glyph: .ball,
            lead: "Grass plays faster and lower than other surfaces, so points stay shorter and timing matters more.",
            detailTitle: "What changes",
            detail: "Balls skid and rise less after landing, and a good serve gets through the court quickly."
        ),
        MorphingMicPillSuggestion(
            question: "What makes a serve\nan ace?", glyph: .medal,
            lead: "An ace is a legal serve the receiver cannot touch, so the point is over after a single shot.",
            detailTitle: "Where it lands",
            detail: "Most aces go out wide or down the centre line, where the receiver's reach is longest."
        ),
        MorphingMicPillSuggestion(
            question: "How does clay change\nthe game?", glyph: .court,
            lead: "Clay slows the ball down and makes it bounce higher, so rallies run long and patience wins.",
            detailTitle: "What changes",
            detail: "Players slide into their shots, and heavy topspin is rewarded more than raw pace."
        ),
    ]
    /// The answer to a question that was typed instead of picked.
    var typedLead = "Here is a short answer to get you started. Ask a follow-up to go deeper."
    var typedDetailTitle = "Next"
    var typedDetail = "Tap + to go back to the suggestions and pick another question."
    var placeholder = "Ask anything"
    var thinkingLabel = "Thinking…"
    /// Seconds the capsule spends thinking before the answer shows.
    var thinkingTime = 2.1
    /// Called with the suggestion's index when a card is tapped.
    var onSuggestion: ((Int) -> Void)? = nil
    /// Called with the typed question when it is sent.
    var onSend: ((String) -> Void)? = nil
    /// Called when "+" brings the suggestions back.
    var onNew: (() -> Void)? = nil
    var onRecordStart: (() -> Void)? = nil
    var onRecordEnd: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var sim = Simulation()
    @State private var own = ""
    /// The capsule is a text field.
    @State private var typing = false
    /// The question that was typed last.
    @State private var typedQuestion = ""

    /// Per-frame state: what the panel is doing and everything that eases after it.
    private final class Simulation {
        enum Event { case recordStart }

        var last: Date? = nil
        var time = 0.0
        var clock = 0.0
        /// The next line of the autoPlay script.
        var step = 0
        var looping = false
        /// Seconds the loop still waits after a touch.
        var hold = 0.0

        /// The suggestion whose answer is laid out; -1 for a typed question.
        var asked = 0
        var answering = false
        var thinking = false
        var thinkAt = 0.0
        var micDown = false
        var micByLoop = false
        var micAt = 0.0
        var recording = false
        var recordAt = 0.0
        /// The control under the finger: 0…2 a card, 3 "+", 4 the keyboard button; -1 none.
        var pressed = -1

        var veilIn = 0.0
        var veilAlpha = 0.0
        /// Heading and cards, tilted by the veil's front as it passes them.
        var wobble = [Chase(0), Chase(0), Chase(0), Chase(0)]
        var kicked = [false, false, false, false]
        var cards = 1.0
        var answer = 0.0
        var width = Chase(Metrics.micWidth)
        var height = Chase(Metrics.barHeight)
        var sides = Chase(1)
        var think = 0.0
        var rec = 0.0
        var type = 0.0
        var press = [0.0, 0.0, 0.0, 0.0, 0.0]
        var events: [Event] = []

        func touch() {
            hold = Metrics.resume
        }

        func ask(_ index: Int) {
            guard !thinking else { return }
            asked = index
            answering = false
            thinking = true
            thinkAt = time
            veilIn = 0
            veilAlpha = 1
            kicked = [false, false, false, false]
        }

        func reset() {
            thinking = false
            answering = false
        }

        func micPress(byLoop: Bool) {
            guard !thinking else { return }
            micDown = true
            micByLoop = byLoop
            micAt = time
        }

        /// True when a recording ended; a short tap only nudges the capsule.
        func micRelease() -> Bool {
            guard micDown else { return false }
            micDown = false
            if recording {
                recording = false
                return true
            }
            width.v += Metrics.nudge
            return false
        }

        private func run(_ action: Int) {
            switch action {
            case 0: pressed = 0
            case 1:
                pressed = -1
                ask(0)
            case 2: micPress(byLoop: true)
            case 3: _ = micRelease()
            case 4: pressed = 3
            default:
                pressed = -1
                reset()
            }
        }

        func advance(to date: Date, typing: Bool, thinkingTime: Double, still: Bool) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            time += dt

            // The loop stays away while a finger is at work, and until the answer it asked for has been read.
            if typing || (micDown && !micByLoop) || (hold > 0 && thinking) { hold = Metrics.resume }
            if hold > 0 {
                hold -= dt
                if hold <= 0, looping {
                    // The loop starts over from the suggestions.
                    clock = 0
                    step = 0
                    pressed = -1
                    micDown = false
                    recording = false
                    reset()
                }
            } else if looping {
                clock += dt
                if clock >= Metrics.loop {
                    clock -= Metrics.loop
                    step = 0
                }
                while step < script.count, clock >= script[step].t {
                    run(script[step].action)
                    step += 1
                }
            }

            if thinking, time - thinkAt >= thinkingTime {
                thinking = false
                answering = true
                answer = -Metrics.answerDelay
            }
            if micDown, !recording, time - micAt >= Metrics.holdDelay {
                recording = true
                recordAt = time
                if !micByLoop { events.append(.recordStart) }
            }

            if thinking {
                veilIn = still ? 1 : min(1, veilIn + dt / Metrics.veilIn)
            } else if veilAlpha < 0.01 {
                veilIn = 0
            }
            veilAlpha = approach(veilAlpha, thinking ? 1 : 0, 5, dt)
            let front = veilFront(veilIn)
            for i in 0..<4 {
                if thinking, !still, !kicked[i], front < wobbleLines[i] {
                    kicked[i] = true
                    wobble[i].v += i % 2 == 0 ? Metrics.wobbleKick : -Metrics.wobbleKick
                }
                wobble[i].step(to: 0, omega: 13, zeta: 0.3, dt)
            }
            answer = answering ? min(1, answer + dt / Metrics.answerIn) : max(0, answer - dt / Metrics.contentOut)
            // The cards wait for the answer to be gone before they come back.
            if answering {
                cards = max(0, cards - dt / Metrics.contentOut)
            } else if answer <= 0 {
                cards = min(1, cards + dt / Metrics.cardsIn)
            }

            let wide = typing ? Metrics.fieldWidth : thinking ? Metrics.thinkWidth : recording ? Metrics.recordWidth : Metrics.micWidth
            width.step(to: wide, omega: 17, zeta: 0.55, dt)
            height.step(to: typing ? Metrics.fieldHeight : recording ? Metrics.recordHeight : Metrics.barHeight, omega: 17, zeta: 0.55, dt)
            sides.step(to: thinking ? 0 : 1, omega: 15, zeta: 0.5, dt)
            think = approach(think, thinking && !typing ? 1 : 0, 14, dt)
            rec = approach(rec, recording ? 1 : 0, 14, dt)
            type = approach(type, typing ? 1 : 0, 14, dt)
            for i in press.indices {
                press[i] = approach(press[i], pressed == i ? 1 : 0, 30, dt)
            }
        }
    }

    private var current: String { text?.wrappedValue ?? own }

    private var field: Binding<String> {
        Binding(get: { current }, set: { store($0) })
    }

    var body: some View {
        TimelineView(.animation) { timeline in
            let date = timeline.date
            let _ = sim.advance(to: date, typing: typing, thinkingTime: thinkingTime, still: reduceMotion)
            let s = sim
            let shape = RoundedRectangle(cornerRadius: Metrics.radius, style: .circular)

            ZStack(alignment: .topLeading) {
                shape
                    .fill(Color(hex: 0xf7f6f3))
                    .shadow(color: .black.opacity(0.1), radius: 24, y: 14)

                ZStack(alignment: .topLeading) {
                    Text(heading)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x1c1b19))
                        .lineLimit(1)
                        .frame(width: Metrics.width, height: 20)
                        .rotationEffect(.degrees(s.wobble[0].x))
                        .offset(y: Metrics.headingY)
                        .opacity(rowProgress(s.cards, order: 0, rows: 4))

                    ForEach(Array(suggestions.prefix(3).enumerated()), id: \.offset) { index, item in
                        let shown = rowProgress(s.cards, order: index + 1, rows: 4)
                        Button {
                            sim.touch()
                            sim.ask(index)
                            onSuggestion?(index)
                        } label: {
                            card(item)
                        }
                        .buttonStyle(PressStyle(scale: 1 - Metrics.pressDip * s.press[index]) { down in press(index, down) })
                        .rotationEffect(.degrees(s.wobble[index + 1].x))
                        .offset(
                            x: Metrics.inset,
                            y: Metrics.cardsY + Double(index) * (Metrics.cardHeight + Metrics.cardGap) + (reduceMotion ? 0 : (1 - shown) * Metrics.rise)
                        )
                        .opacity(shown)
                        .allowsHitTesting(s.cards > 0.9 && !s.thinking)
                    }

                    answer(s.asked, progress: max(0, s.answer))

                    sideButton(index: 3, x: Metrics.sideX, scale: s.sides.x, press: s.press[3], dim: s.rec) {
                        sim.touch()
                        sim.reset()
                        onNew?()
                    } icon: {
                        Canvas { context, _ in drawPlus(context) }
                    }
                    // The keyboard button turns into the send button while the field is open.
                    sideButton(index: 4, x: Metrics.width - Metrics.sideX, scale: s.sides.x, press: s.press[4], dim: s.rec) {
                        sim.touch()
                        keyboardTapped()
                    } icon: {
                        Canvas { context, _ in drawKeyboard(context) }.opacity(1 - s.type)
                        Canvas { context, _ in drawArrow(context) }.opacity(s.type)
                    }

                    veil(front: veilFront(s.veilIn), alpha: s.veilAlpha, sheen: reduceMotion ? 0.3 : (s.time - s.thinkAt) / Metrics.sheenPeriod)
                }
                .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
                .clipShape(shape)

                capsule(s)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
            // One piece for the parent: when the keyboard moves the layout, the layers move together.
            .geometryGroup()
            .onChange(of: date) { drain() }
        }
        .onAppear { sim.looping = autoPlay && !reduceMotion }
        .onChange(of: autoPlay) { sim.looping = autoPlay && !reduceMotion }
        .onChange(of: reduceMotion) { sim.looping = autoPlay && !reduceMotion }
        .onChange(of: focused) {
            // A field left empty folds back into the mic capsule.
            if !focused, current.isEmpty { typing = false }
        }
    }

    private func card(_ item: MorphingMicPillSuggestion) -> some View {
        HStack(spacing: 10) {
            Text(item.question)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Color(hex: 0x1c1b19))
                .lineSpacing(2)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
            Canvas { context, _ in
                drawGlyph(context, item.glyph)
            }
            .frame(width: 36, height: 36)
            Canvas { context, _ in
                var arrow = Path()
                arrow.move(to: CGPoint(x: 2, y: 7))
                arrow.addLine(to: CGPoint(x: 12, y: 7))
                arrow.move(to: CGPoint(x: 8, y: 3))
                arrow.addLine(to: CGPoint(x: 12, y: 7))
                arrow.addLine(to: CGPoint(x: 8, y: 11))
                context.stroke(arrow, with: .color(Color(hex: 0x1c1b19)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
            }
            .frame(width: 14, height: 14)
        }
        .padding(.leading, 16)
        .padding(.trailing, 14)
        .frame(width: Metrics.width - Metrics.inset * 2, height: Metrics.cardHeight)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .circular)
                .fill(.white)
                .shadow(color: Color.rgba(60, 50, 40, 0.09), radius: 7, y: 4)
        )
    }

    /// The answer, where the cards were: its four lines arrive one after another.
    private func answer(_ index: Int, progress: Double) -> some View {
        let item = suggestions.indices.contains(index) ? suggestions[index] : nil
        let lines: [(String, Font, UInt32, Double)] = [
            ((item?.question ?? typedQuestion).replacingOccurrences(of: "\n", with: " "), .system(size: 12, weight: .medium), 0x8a8782, 12),
            (item?.lead ?? typedLead, .system(size: 16), 0x1c1b19, 22),
            (item?.detailTitle ?? typedDetailTitle, .system(size: 14, weight: .semibold), 0x1c1b19, 8),
            (item?.detail ?? typedDetail, .system(size: 13), 0x5c5953, 0),
        ]
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(lines.enumerated()), id: \.offset) { order, line in
                let shown = rowProgress(progress, order: order, rows: 4)
                Text(line.0)
                    .font(line.1)
                    .foregroundStyle(Color(hex: line.2))
                    .lineSpacing(3)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.bottom, line.3)
                    .opacity(shown)
                    .offset(y: reduceMotion ? 0 : (1 - shown) * Metrics.rise)
            }
        }
        .frame(width: Metrics.width - Metrics.answerInset * 2, height: Metrics.barY - Metrics.answerY, alignment: .topLeading)
        .offset(x: Metrics.answerInset, y: Metrics.answerY)
        .allowsHitTesting(false)
    }

    /// One of the two white round buttons beside the capsule.
    private func sideButton<Icon: View>(
        index: Int, x: Double, scale: Double, press: Double, dim: Double,
        action: @escaping () -> Void, @ViewBuilder icon: () -> Icon
    ) -> some View {
        let shown = min(1, max(0, scale))
        return Button(action: action) {
            ZStack(content: icon)
                .frame(width: Metrics.barHeight, height: Metrics.barHeight)
                .background(Circle().fill(.white).shadow(color: Color.rgba(60, 50, 40, 0.14), radius: 6, y: 3))
        }
        .buttonStyle(PressStyle(scale: (0.5 + 0.5 * scale) * (1 - Metrics.pressDip * 1.5 * press)) { down in self.press(index, down) })
        .opacity(shown * (1 - 0.55 * dim))
        .offset(x: x - Metrics.barHeight / 2, y: Metrics.barY)
        .allowsHitTesting(shown > 0.9)
    }

    /// The veil: a tinted sheet with a soft front edge rising from the bar, and a pale sheen drifting across it.
    private func veil(front: Double, alpha: Double, sheen: Double) -> some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0xa9a3bd, opacity: 0), location: 0),
                    .init(color: Color(hex: 0xa9a3bd, opacity: 0.52), location: Metrics.veilEdge / (Metrics.height + Metrics.veilEdge)),
                    .init(color: Color(hex: 0xa9a3bd, opacity: 0.52), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
            .frame(width: Metrics.width, height: Metrics.height + Metrics.veilEdge)
            .offset(y: front)

            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0xffd6ec, opacity: 0), location: 0),
                    .init(color: Color(hex: 0xffd6ec, opacity: 0.34), location: 0.3),
                    .init(color: Color(hex: 0xc4e8ff, opacity: 0.34), location: 0.5),
                    .init(color: Color(hex: 0xd6ffec, opacity: 0.28), location: 0.7),
                    .init(color: Color(hex: 0xd6ffec, opacity: 0), location: 1),
                ],
                startPoint: .leading, endPoint: .trailing
            )
            // Taller than the panel, so the tilted band has no corner inside it.
            .frame(width: Metrics.sheenWidth, height: Metrics.height * 1.6)
            .rotationEffect(.degrees(Metrics.sheenTilt))
            .offset(
                x: -Metrics.sheenWidth * 1.3 + (Metrics.width + Metrics.sheenWidth * 1.6) * (sheen - floor(sheen)),
                y: -Metrics.height * 0.3
            )
            .opacity(front < 0 ? 1 : max(0, 1 - front / Metrics.height))
        }
        .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        .opacity(alpha)
        .allowsHitTesting(false)
    }

    /// The black capsule: mic, level bars, the "Thinking…" label or the text field, whichever it is now.
    private func capsule(_ s: Simulation) -> some View {
        let voice = reduceMotion ? 0.4 : s.time - s.recordAt
        let beat = reduceMotion ? 0.3 : s.time - s.thinkAt
        let letters = Array(thinkingLabel)
        // The contents ride on the capsule as an overlay, so the widest of them can't stretch it.
        let contents = ZStack {
            Canvas { context, _ in
                drawMic(context)
            }
            .frame(width: 20, height: 20)
            .offset(x: -Metrics.recordShift * s.rec)
            .opacity((1 - s.think) * (1 - s.type))

            Canvas { context, _ in
                drawBars(context, time: voice, strength: s.rec)
            }
            .frame(width: Metrics.barPitch * 6 + Metrics.barWidth, height: Metrics.barMax)
            .offset(x: Metrics.recordShift * 0.45)
            .opacity(s.rec * (1 - s.think))

            HStack(spacing: 7) {
                Canvas { context, _ in
                    let ring = Path(ellipseIn: CGRect(x: 1.5, y: 1.5, width: 11, height: 11))
                    context.stroke(ring, with: .color(.white), lineWidth: 1.6)
                    let r = 2.5 * (0.85 + 0.3 * sin(tau * beat / Metrics.pulse))
                    context.fill(Path(ellipseIn: CGRect(x: 7 - r, y: 7 - r, width: r * 2, height: r * 2)), with: .color(.white))
                }
                .frame(width: 14, height: 14)
                HStack(spacing: 0) {
                    ForEach(Array(letters.enumerated()), id: \.offset) { index, letter in
                        Text(String(letter))
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(.white)
                            .opacity(shimmer(index, count: letters.count, time: beat))
                    }
                }
            }
            .fixedSize()
            .opacity(s.think)

            TextField("", text: field, prompt: Text(placeholder).foregroundStyle(Color(hex: 0x8e8e93)))
                .font(.system(size: 15))
                .foregroundStyle(.white)
                .tint(.white)
                .focused($focused)
                .submitLabel(.send)
                .onSubmit(send)
                .frame(width: Metrics.fieldWidth - 36)
                .opacity(s.type)
                .allowsHitTesting(typing)
        }
        return Capsule(style: .circular)
            .fill(Color(hex: 0x111111))
            .shadow(color: .black.opacity(0.22), radius: 8, y: 5)
            .overlay(Capsule(style: .circular).strokeBorder(.white, lineWidth: 1.5).opacity(s.think))
            .frame(width: max(Metrics.barHeight, s.width.x), height: max(30, s.height.x))
            .overlay(contents)
            .contentShape(Capsule())
            .gesture(typing ? nil : micHold)
            .accessibilityLabel(Text(verbatim: "Hold to speak"))
            .position(x: Metrics.width / 2, y: Metrics.barY + Metrics.barHeight / 2)
    }

    private var micHold: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                if !sim.micDown || sim.micByLoop {
                    sim.touch()
                    sim.micPress(byLoop: false)
                }
            }
            .onEnded { _ in
                if sim.micRelease() { onRecordEnd?() }
            }
    }

    private func press(_ index: Int, _ down: Bool) {
        if down {
            sim.touch()
            sim.pressed = index
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } else if sim.pressed == index {
            sim.pressed = -1
        }
    }

    private func store(_ new: String) {
        if let text { text.wrappedValue = new } else { own = new }
    }

    /// The keyboard button opens the field; once there is text in it, it sends.
    private func keyboardTapped() {
        if !typing {
            typing = true
            focused = true
        } else if current.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            store("")
            focused = false
            typing = false
        } else {
            send()
        }
    }

    private func send() {
        let question = current.trimmingCharacters(in: .whitespacesAndNewlines)
        store("")
        focused = false
        typing = false
        guard !question.isEmpty else { return }
        typedQuestion = question
        sim.touch()
        sim.ask(-1)
        onSend?(question)
    }

    /// Applies what the last frame decided: state can't change while the frame is being drawn.
    private func drain() {
        guard !sim.events.isEmpty else { return }
        sim.events.removeAll()
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        onRecordStart?()
    }

    private struct PressStyle: ButtonStyle {
        let scale: Double
        let onPress: (Bool) -> Void

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(scale)
                .onChange(of: configuration.isPressed) { _, pressed in onPress(pressed) }
        }
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 400.0
    static let radius = 36.0
    /// Cards keep this far from the panel's sides.
    static let inset = 22.0
    static let headingY = 30.0
    static let cardsY = 72.0
    static let cardHeight = 62.0
    static let cardGap = 8.0
    static let answerInset = 28.0
    static let answerY = 36.0
    /// Rows and lines climb this far as they come in.
    static let rise = 10.0
    static let pressDip = 0.04

    /// Top of the bottom bar, and the height of its three controls at rest.
    static let barY = 334.0
    static let barHeight = 40.0
    /// Centre of the round buttons, from the panel's sides.
    static let sideX = 42.0
    // The capsule's width as a mic, thinking, recording and as a text field.
    static let micWidth = 76.0
    static let thinkWidth = 126.0
    static let recordWidth = 138.0
    static let recordHeight = 46.0
    static let fieldWidth = 210.0
    static let fieldHeight = 44.0
    /// The mic slides this far left to make room for the level bars.
    static let recordShift = 40.0
    /// A tap on the capsule only nudges it, as a hint to hold: points per second added to its width.
    static let nudge = 260.0
    /// A press shorter than this is a tap.
    static let holdDelay = 0.2

    static let barWidth = 2.5
    static let barPitch = 7.0
    static let barMin = 4.0
    static let barMax = 18.0

    /// The veil's front edge is this soft, and takes this long to climb the panel.
    static let veilEdge = 110.0
    static let veilIn = 0.7
    static let sheenPeriod = 1.6
    static let sheenWidth = 300.0
    static let sheenTilt = 18.0
    /// Degrees per second a card is thrown at when the veil's front passes it.
    static let wobbleKick = 52.0
    static let pulse = 0.9

    // How long the cards, and the answer's lines, take to arrive; both leave faster.
    static let cardsIn = 0.6
    static let answerIn = 1.0
    static let answerDelay = 0.15
    static let contentOut = 0.2
    static let stagger = 0.18

    /// autoPlay period, seconds.
    static let loop = 8.8
    /// After a touch, or once the field is closed, the loop waits this long before it starts over.
    static let resume = 3.0
}

/// The autoPlay loop: press and tap the first card, hold the mic for a moment, press and tap "+".
private let script: [(t: Double, action: Int)] = [(0.8, 0), (0.95, 1), (4.6, 2), (6.0, 3), (6.9, 4), (7.05, 5)]

/// Where the veil's front has to be for the heading and each card to feel it.
private let wobbleLines = [
    Metrics.headingY - 20,
    Metrics.cardsY - 10,
    Metrics.cardsY + Metrics.cardHeight + Metrics.cardGap - 10,
    Metrics.cardsY + (Metrics.cardHeight + Metrics.cardGap) * 2 - 10,
]

private let barRates = [9.1, 7.3, 11.2, 8.4, 10.3, 6.7, 12.1]
private let barRipples = [17.0, 13.6, 19.3, 15.1, 12.2, 18.4, 14.3]
private let barPhases = [0.3, 1.9, 4.1, 2.6, 5.2, 3.3, 0.9]

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

/// Top of the veil for a sweep that is `progress` (0…1) done: it starts under the panel and ends above it.
private func veilFront(_ progress: Double) -> Double {
    let eased = 1 - pow(1 - progress, 3)
    return Metrics.height - (Metrics.height + Metrics.veilEdge) * eased
}

/// How far a row has come in, 0…1: `order` 0 is the first to arrive.
private func rowProgress(_ progress: Double, order: Int, rows: Int) -> Double {
    let window = 1 - Metrics.stagger * Double(rows - 1)
    let p = min(1, max(0, (progress - Double(order) * Metrics.stagger) / window))
    return 1 - pow(1 - p, 3)
}

/// Opacity of one letter of the thinking label: a bright band runs through the word again and again.
private func shimmer(_ index: Int, count: Int, time: Double) -> Double {
    let cycle = time / Metrics.sheenPeriod
    let band = (cycle - floor(cycle)) * Double(count + 8) - 4
    let d = Double(index) - band
    return 0.45 + 0.55 * exp(-d * d / 5)
}

private func drawBars(_ context: GraphicsContext, time: Double, strength: Double) {
    for i in 0..<7 {
        let level = min(1, max(0, 0.5 + 0.34 * sin(time * barRates[i] + barPhases[i]) + 0.22 * sin(time * barRipples[i] + barPhases[6 - i])))
        let h = Metrics.barMin + (Metrics.barMax - Metrics.barMin) * level * strength
        let bar = CGRect(x: Double(i) * Metrics.barPitch, y: (Metrics.barMax - h) / 2, width: Metrics.barWidth, height: h)
        context.fill(Path(roundedRect: bar, cornerRadius: Metrics.barWidth / 2), with: .color(.white))
    }
}

/// The microphone, in a 20-point box.
private func drawMic(_ context: GraphicsContext) {
    context.fill(Path(roundedRect: CGRect(x: 7.5, y: 2, width: 5, height: 9.5), cornerRadius: 2.5), with: .color(.white))
    var stand = Path()
    stand.move(to: CGPoint(x: 5.2, y: 8.6))
    stand.addLine(to: CGPoint(x: 5.2, y: 9.4))
    stand.addArc(center: CGPoint(x: 10, y: 9.4), radius: 4.8, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: true)
    stand.addLine(to: CGPoint(x: 14.8, y: 8.6))
    stand.move(to: CGPoint(x: 10, y: 14.2))
    stand.addLine(to: CGPoint(x: 10, y: 17))
    stand.move(to: CGPoint(x: 7, y: 17))
    stand.addLine(to: CGPoint(x: 13, y: 17))
    context.stroke(stand, with: .color(.white), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
}

// The icons of the round buttons, each in a 40-point box.

private func drawPlus(_ context: GraphicsContext) {
    var plus = Path()
    plus.move(to: CGPoint(x: 13, y: 20))
    plus.addLine(to: CGPoint(x: 27, y: 20))
    plus.move(to: CGPoint(x: 20, y: 13))
    plus.addLine(to: CGPoint(x: 20, y: 27))
    context.stroke(plus, with: .color(Color(hex: 0x1c1b19)), style: StrokeStyle(lineWidth: 1.7, lineCap: .round))
}

private func drawKeyboard(_ context: GraphicsContext) {
    let ink = Color(hex: 0x1c1b19)
    context.stroke(Path(roundedRect: CGRect(x: 11, y: 14, width: 18, height: 12), cornerRadius: 3), with: .color(ink), lineWidth: 1.4)
    var keys = Path()
    for x in [14.6, 18.2, 21.8, 25.4] {
        keys.addEllipse(in: CGRect(x: x - 0.8, y: 16.9, width: 1.6, height: 1.6))
        keys.addEllipse(in: CGRect(x: x - 0.8, y: 19.6, width: 1.6, height: 1.6))
    }
    context.fill(keys, with: .color(ink))
    var space = Path()
    space.move(to: CGPoint(x: 16, y: 23))
    space.addLine(to: CGPoint(x: 24, y: 23))
    context.stroke(space, with: .color(ink), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
}

private func drawArrow(_ context: GraphicsContext) {
    var arrow = Path()
    arrow.move(to: CGPoint(x: 20, y: 27))
    arrow.addLine(to: CGPoint(x: 20, y: 13.5))
    arrow.move(to: CGPoint(x: 14.5, y: 19))
    arrow.addLine(to: CGPoint(x: 20, y: 13.5))
    arrow.addLine(to: CGPoint(x: 25.5, y: 19))
    context.stroke(arrow, with: .color(Color(hex: 0x1c1b19)), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
}

/// A suggestion's picture on its tinted disc, in a 36-point box.
private func drawGlyph(_ context: GraphicsContext, _ glyph: MorphingMicPillGlyph) {
    let disc = Path(ellipseIn: CGRect(x: 0, y: 0, width: 36, height: 36))
    switch glyph {
    case .ball:
        context.fill(disc, with: .color(Color(hex: 0xf6ebe8)))
        context.fill(
            Path(ellipseIn: CGRect(x: 8, y: 8, width: 20, height: 20)),
            with: .radialGradient(
                Gradient(colors: [Color(hex: 0xf0796c), Color(hex: 0xd9463b), Color(hex: 0xb5342c)]),
                center: CGPoint(x: 15, y: 14), startRadius: 0, endRadius: 15
            )
        )
        context.fill(Path(ellipseIn: CGRect(x: 12.5, y: 11.5, width: 5, height: 3.6)), with: .color(.white.opacity(0.45)))
    case .medal:
        context.fill(disc, with: .color(Color(hex: 0xf7f0de)))
        var ribbon = Path()
        ribbon.move(to: CGPoint(x: 12.5, y: 7.5))
        ribbon.addLine(to: CGPoint(x: 18, y: 18))
        ribbon.addLine(to: CGPoint(x: 23.5, y: 7.5))
        context.stroke(ribbon, with: .color(Color(hex: 0xe5484d)), style: StrokeStyle(lineWidth: 3.2, lineCap: .butt, lineJoin: .miter))
        context.fill(
            Path(ellipseIn: CGRect(x: 11.5, y: 15.5, width: 13, height: 13)),
            with: .radialGradient(
                Gradient(colors: [Color(hex: 0xffd978), Color(hex: 0xf2b84b), Color(hex: 0xd99a2b)]),
                center: CGPoint(x: 16.5, y: 20), startRadius: 0, endRadius: 9
            )
        )
        context.stroke(Path(ellipseIn: CGRect(x: 14.5, y: 18.5, width: 7, height: 7)), with: .color(Color(hex: 0xc98a1e, opacity: 0.7)), lineWidth: 1)
    case .court:
        context.fill(disc, with: .color(Color(hex: 0xf8ebe2)))
        context.fill(Path(roundedRect: CGRect(x: 8, y: 11, width: 20, height: 14), cornerRadius: 2.5), with: .color(Color(hex: 0xe0703a)))
        var lines = Path()
        lines.addRect(CGRect(x: 10.5, y: 13.5, width: 15, height: 9))
        lines.move(to: CGPoint(x: 18, y: 13.5))
        lines.addLine(to: CGPoint(x: 18, y: 22.5))
        lines.move(to: CGPoint(x: 13.5, y: 18))
        lines.addLine(to: CGPoint(x: 22.5, y: 18))
        context.stroke(lines, with: .color(.white.opacity(0.92)), lineWidth: 1)
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe6e4df).ignoresSafeArea()
        MorphingMicPill(autoPlay: true)
    }
}
