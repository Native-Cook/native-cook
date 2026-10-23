import SwiftUI

/// Chat composer on a teal panel. While a message is written its three attachment tools fold away
/// to the right and the microphone turns into a send plane; sending lifts the text out of the field
/// as a white bubble that grows from the composer's top-right corner, and the tools unfold again.
struct FoldingMessageBar: View {
    /// The field's text when the parent owns it. Leave nil and the bar keeps its own.
    var text: Binding<String>? = nil
    /// Writes and sends `script` by itself in a loop, without the keyboard, until the field is tapped.
    var autoPlay = false
    var placeholder = "Message..."
    /// What autoPlay types.
    var script = "Here is my order ID 1209475822"
    /// The message already in the thread, with its sender and time.
    var senderName = "Customer Service"
    var message = "Can you send me your order ID so I can check right away?"
    var time = "09:52 PM"
    /// Label over a sent message.
    var ownName = "You"
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the trimmed text when the plane is tapped or the keyboard's send key is pressed.
    var onSend: ((String) -> Void)? = nil
    /// Called when the button is tapped while the field is empty.
    var onMic: (() -> Void)? = nil
    var onEmoji: (() -> Void)? = nil
    var onAttach: (() -> Void)? = nil
    var onPhoto: (() -> Void)? = nil
    var onCamera: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    /// The script is running: the field is not focused and the bar draws its own caret.
    @State private var playing = false
    /// The script's stand-in for focus.
    @State private var engaged = false
    /// The script's finger on the button.
    @State private var pressed = false
    /// When the last key went down: the caret's blink starts over from there.
    @State private var keyAt = Date.now
    @State private var sent = ""
    @State private var grown = false
    @State private var visible = false
    @State private var lifted = false
    /// The text on its way out of the field.
    @State private var ghost = ""
    @State private var ghostGone = true
    @State private var sends = 0
    @State private var resumes = 0

    private var controlled: Bool { text != nil }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion }
    private var value: String { text?.wrappedValue ?? draft }
    /// Folded: someone is writing, or has written.
    private var active: Bool { playing ? engaged : focused || !value.isEmpty }

    var body: some View {
        ZStack(alignment: .bottom) {
            RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular)
                .fill(
                    LinearGradient(
                        stops: [
                            .init(color: Color(hex: 0x010b0e), location: 0),
                            .init(color: Color(hex: 0x00222a), location: 0.5),
                            .init(color: Color(hex: 0x003e4c), location: 1),
                        ],
                        startPoint: .top, endPoint: .bottom
                    )
                )

            incoming
                .padding(.trailing, Metrics.bubbleInset)
                .frame(maxWidth: .infinity, alignment: .bottomLeading)
                .padding(.horizontal, Metrics.margin)
                .padding(.bottom, Metrics.margin + Metrics.barHeight + Metrics.threadGap)
                .offset(y: lifted ? -Metrics.lift : 0)
                .animation(liftAnimation, value: lifted)

            outgoing
                .padding(.leading, Metrics.bubbleInset)
                .frame(maxWidth: .infinity, alignment: .bottomTrailing)
                .padding(.horizontal, Metrics.margin)
                .padding(.bottom, Metrics.margin + Metrics.barHeight + Metrics.sentGap)

            composer
                .padding(.bottom, Metrics.margin)
        }
        .frame(width: Metrics.panelWidth, height: Metrics.panelHeight)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular))
        .overlay(
            RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular)
                .strokeBorder(.white.opacity(0.06), lineWidth: 1)
        )
        .sensoryFeedback(.impact(weight: .light), trigger: sends)
        .onAppear { playing = auto }
        .onChange(of: auto) { playing = auto }
        .onChange(of: focused) { _, now in
            if now {
                takeOver()
            } else if value.isEmpty {
                resumes += 1
            }
        }
        .task(id: playing) {
            guard playing else { return }
            await play()
        }
        .task(id: resumes) {
            // The loop starts over from rest a few seconds after the field is left empty.
            guard resumes > 0, auto else { return }
            try? await Task.sleep(for: .seconds(Metrics.resume))
            guard !Task.isCancelled, !focused, value.isEmpty else { return }
            clearSent()
            try? await Task.sleep(for: .seconds(0.7))
            guard !Task.isCancelled, !focused, value.isEmpty else { return }
            playing = true
        }
    }

    // MARK: Thread

    private var incoming: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                FoldingMessageBarAvatar(fill: Color(hex: 0xe8f1f2), figure: Color(hex: 0x1c4a52))
                Text(senderName)
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(.white)
                    .lineLimit(1)
            }
            .frame(height: Metrics.avatar)

            VStack(alignment: .leading, spacing: 5) {
                Text(message)
                    .font(.system(size: 13.5))
                    .lineSpacing(3)
                    .foregroundStyle(.white)
                    .padding(.vertical, 1.5)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 5) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 2.5, style: .circular).fill(Color(hex: 0x7f9ba2))
                        foldingMessageBarPath(Glyph.tick)
                            .stroke(Color(hex: 0x1b3d44), style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                    }
                    .frame(width: 10, height: 10)
                    Text(time)
                        .font(.system(size: 10.5))
                        .foregroundStyle(Color(hex: 0x7f9ba2))
                }
                .frame(height: 14)
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: Metrics.bubbleRadius, style: .circular).fill(.white.opacity(0.11)))
            .overlay(
                RoundedRectangle(cornerRadius: Metrics.bubbleRadius, style: .circular)
                    .strokeBorder(.white.opacity(0.06), lineWidth: 1)
            )
        }
        .accessibilityElement(children: .combine)
    }

    private var outgoing: some View {
        VStack(alignment: .trailing, spacing: 8) {
            HStack(spacing: 8) {
                Text(ownName)
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                FoldingMessageBarAvatar(fill: Color(hex: 0x17b6c5), figure: .white)
            }
            .frame(height: Metrics.avatar)
            .opacity(visible ? 1 : 0)
            // The label follows its bubble in.
            .animation(visible ? .easeOut(duration: 0.22).delay(0.22) : .easeOut(duration: Metrics.fade), value: visible)

            VStack(alignment: .leading, spacing: 4) {
                Text(sent)
                    .font(.system(size: 13.5, weight: .medium))
                    .foregroundStyle(Color(hex: 0x0c8f9d))
                    .lineLimit(1)
                    .frame(height: 19)
                Text(time)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Color(hex: 0x7cc7d0))
                    .frame(height: 14)
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: Metrics.bubbleRadius, style: .circular).fill(.white))
            // It starts small at the composer's top-right corner, behind the bar.
            .scaleEffect(grown || reduceMotion ? 1 : 0.3, anchor: .bottomTrailing)
            .offset(y: grown || reduceMotion ? 0 : Metrics.sentDrop)
            .animation(Metrics.growSpring, value: grown)
            .opacity(visible ? 1 : 0)
            .animation(.easeOut(duration: visible ? 0.2 : Metrics.fade), value: visible)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(!visible)
    }

    private var liftAnimation: Animation {
        if reduceMotion { return .easeOut(duration: 0.25) }
        // On the way down it waits for the sent bubble to fade.
        return lifted ? Metrics.liftSpring : Metrics.liftSpring.delay(Metrics.dropDelay)
    }

    // MARK: Composer

    private var composer: some View {
        HStack(spacing: Metrics.gap) {
            field
            Button(action: tapButton) {
                ZStack {
                    RoundedRectangle(cornerRadius: Metrics.fieldRadius, style: .circular).fill(.white)
                    glyph(Glyph.mic, shown: !active, turn: 30)
                    glyph(Glyph.plane, shown: active, turn: -30)
                }
                .frame(width: Metrics.fieldHeight, height: Metrics.fieldHeight)
            }
            .buttonStyle(FoldingMessageBarPress(held: pressed))
            .accessibilityLabel(active ? "Send" : "Record a voice message")
        }
        .padding(Metrics.barPadding)
        .frame(width: Metrics.panelWidth - Metrics.margin * 2, height: Metrics.barHeight)
        .background(RoundedRectangle(cornerRadius: Metrics.barRadius, style: .circular).fill(Color(hex: 0x1c454d)))
        .overlay(
            RoundedRectangle(cornerRadius: Metrics.barRadius, style: .circular)
                .strokeBorder(.white.opacity(0.07), lineWidth: 1)
        )
    }

    /// One of the two glyphs in the white button: the one going out shrinks and turns away.
    private func glyph(_ d: String, shown: Bool, turn: Double) -> some View {
        FoldingMessageBarIcon(d: d, color: Color(hex: 0x17b6c5), lineWidth: 1.7)
            .scaleEffect(shown || reduceMotion ? 1 : 0.5)
            .rotationEffect(.degrees(shown || reduceMotion ? 0 : turn))
            .opacity(shown ? 1 : 0)
            // The microphone comes back last, after the tools have unfolded.
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.2).delay(active ? 0 : Metrics.micDelay), value: active)
    }

    private var field: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: Metrics.fieldRadius, style: .circular).fill(Color(hex: 0x375c64))

            ZStack(alignment: .leading) {
                Text(placeholder)
                    .foregroundStyle(Color(hex: 0xc9dadd))
                    .lineLimit(1)
                    .opacity(active ? 0 : 1)
                    // It waits for the sent text to clear the field before it returns.
                    .animation(.easeOut(duration: 0.15).delay(active ? 0 : Metrics.placeholderDelay), value: active)
                    .allowsHitTesting(false)

                TextField("", text: Binding(get: { value }, set: { write($0) }))
                    .focused($focused)
                    // While the script types, the letters are drawn by the row below, with its caret.
                    .foregroundStyle(playing ? .clear : .white)
                    .tint(Metrics.caret)
                    .submitLabel(.send)
                    .onSubmit(submit)
                    .accessibilityLabel(placeholder)

                if playing, engaged {
                    HStack(spacing: 1) {
                        Text(mirror).foregroundStyle(.white).lineLimit(1)
                        TimelineView(.periodic(from: keyAt, by: Metrics.blink)) { timeline in
                            let beat = Int((timeline.date.timeIntervalSince(keyAt) + 0.05) / Metrics.blink)
                            RoundedRectangle(cornerRadius: 1, style: .circular)
                                .fill(Metrics.caret)
                                .frame(width: 2, height: 18)
                                .opacity(beat % 2 == 0 ? 1 : 0)
                        }
                    }
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                }

                Text(ghost)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .opacity(ghostGone ? 0 : 1)
                    .offset(y: ghostGone && !reduceMotion ? -12 : 0)
                    .animation(.easeOut(duration: 0.2), value: ghostGone)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
            .font(.system(size: 14))
            .padding(.leading, Metrics.textLeft)
            .padding(.trailing, 12)

            Button {
                onEmoji?()
            } label: {
                FoldingMessageBarIcon(d: Glyph.smiley, color: Metrics.tool)
            }
            .buttonStyle(.plain)
            .padding(.leading, 10)
            .accessibilityLabel("Emoji")

            HStack(spacing: Metrics.toolGap) {
                ForEach(Array(tools.enumerated()), id: \.offset) { index, tool in
                    Button(action: tool.action) {
                        FoldingMessageBarIcon(d: tool.d, color: Metrics.tool)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(tool.label)
                    .scaleEffect(active && !reduceMotion ? 0.8 : 1)
                    .offset(x: active && !reduceMotion ? Metrics.foldShift : 0)
                    .opacity(active ? 0 : 1)
                    // The camera leaves first and the paperclip comes back first.
                    .animation(
                        active
                            ? .timingCurve(0.23, 1, 0.32, 1, duration: Metrics.fold).delay(Double(tools.count - 1 - index) * Metrics.foldStagger)
                            : Metrics.unfoldSpring.delay(Double(index) * Metrics.unfoldStagger),
                        value: active
                    )
                }
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.trailing, 12)
            .allowsHitTesting(!active)
            .accessibilityHidden(active)
        }
        .frame(height: Metrics.fieldHeight)
    }

    private var tools: [(d: String, label: String, action: () -> Void)] {
        [
            (Glyph.clip, "Attach a file", { onAttach?() }),
            (Glyph.image, "Photo library", { onPhoto?() }),
            (Glyph.camera, "Camera", { onCamera?() }),
        ]
    }

    /// The typed text as the mirror row draws it: a trailing space must keep its width.
    private var mirror: String {
        value.hasSuffix(" ") ? value.dropLast() + "\u{00a0}" : value
    }

    // MARK: Actions

    private func write(_ next: String) {
        if let text { text.wrappedValue = next } else { draft = next }
        onChangeText?(next)
    }

    private func tapButton() {
        guard !playing else { return }
        if value.isEmpty { onMic?() } else { submit() }
    }

    private func submit() {
        let message = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !message.isEmpty else { return }
        sends += 1
        onSend?(message)
        write("")
        deliver(message)
    }

    /// The text leaves the field and comes back as a bubble above the bar.
    private func deliver(_ message: String) {
        var still = Transaction()
        still.disablesAnimations = true
        withTransaction(still) {
            sent = message
            ghost = message
            ghostGone = false
            grown = false
            visible = false
        }
        Task { @MainActor in
            // One frame at the starting pose, then everything moves.
            try? await Task.sleep(for: .milliseconds(20))
            ghostGone = true
            grown = true
            visible = true
            lifted = true
        }
    }

    private func clearSent() {
        visible = false
        lifted = false
    }

    /// A touch on the field ends the script: the field is emptied and the keyboard takes over.
    private func takeOver() {
        guard playing else { return }
        playing = false
        engaged = false
        pressed = false
        draft = ""
        clearSent()
    }

    /// One loop after another, every step at its own moment after the loop began.
    private func play() async {
        let letters = Array(script)
        let keys = Metrics.keyTimes(count: letters.count)
        while !Task.isCancelled {
            let start = ContinuousClock.now
            func reach(_ seconds: Double) async -> Bool {
                try? await Task.sleep(until: start + .seconds(seconds), clock: .continuous)
                return !Task.isCancelled
            }

            guard await reach(Metrics.focusAt) else { return }
            engaged = true
            keyAt = .now
            for (index, at) in keys.enumerated() {
                guard await reach(at) else { return }
                draft = String(letters[...index])
                keyAt = .now
            }
            guard await reach(Metrics.pressAt) else { return }
            pressed = true
            guard await reach(Metrics.sendAt) else { return }
            pressed = false
            engaged = false
            let message = draft
            draft = ""
            deliver(message)
            guard await reach(Metrics.clearAt) else { return }
            clearSent()
            guard await reach(Metrics.loop) else { return }
        }
    }
}

private enum Metrics {
    static let panelWidth = 370.0
    static let panelHeight = 330.0
    static let panelRadius = 34.0
    /// Inset of the bar and of the thread from the panel's edge.
    static let margin = 12.0
    static let barHeight = 58.0
    static let barRadius = 16.0
    static let barPadding = 10.0
    static let fieldHeight = 38.0
    static let fieldRadius = 10.0
    static let gap = 10.0
    static let textLeft = 39.0
    static let toolGap = 14.0
    static let avatar = 22.0
    static let bubbleRadius = 12.0
    /// A bubble stops this far from the opposite edge of the thread.
    static let bubbleInset = 56.0
    /// Space between the bar and the message above it, at rest and for a sent bubble.
    static let threadGap = 16.0
    static let sentGap = 14.0
    /// How far the thread moves up for a sent bubble: its label, the bubble and the gap.
    static let lift = 101.0
    /// The sent bubble starts this far down, tucked behind the bar.
    static let sentDrop = 44.0
    static let foldShift = 14.0
    static let fold = 0.26
    static let foldStagger = 0.08
    static let unfoldStagger = 0.09
    static let micDelay = 0.3
    static let placeholderDelay = 0.18
    /// How long a sent bubble takes to fade, and how long the thread waits before it drops back.
    static let fade = 0.22
    static let dropDelay = 0.16
    static let blink = 0.53

    static let caret = Color(hex: 0x5fdbe8)
    static let tool = Color(hex: 0xd5e3e3)

    static let unfoldSpring = Animation.interpolatingSpring(mass: 1, stiffness: 320, damping: 22)
    static let growSpring = Animation.interpolatingSpring(mass: 1, stiffness: 260, damping: 20)
    static let liftSpring = Animation.interpolatingSpring(mass: 1, stiffness: 200, damping: 22)

    // autoPlay loop, in seconds from its start.
    static let focusAt = 0.8
    static let typeAt = 1.3
    /// The longest the typing may take; a longer script is typed faster.
    static let typeSpan = 2.6
    static let pressAt = 4.2
    static let sendAt = 4.4
    static let clearAt = 6.7
    static let loop = 8.0
    /// After the field is left empty, the loop waits this long before it starts again.
    static let resume = 3.0

    /// Milliseconds before each key, used in turn: uneven like a hand, the same in every loop.
    static let rhythm = [90.0, 60, 110, 70, 50, 130, 80, 60, 100, 70, 150, 60, 90]

    /// The moment each letter of the script lands.
    static func keyTimes(count: Int) -> [Double] {
        var total = 0.0
        let sums = (0..<count).map { index -> Double in
            total += rhythm[index % rhythm.count] / 1000
            return total
        }
        let pace = total > typeSpan ? typeSpan / total : 1
        return sums.map { typeAt + $0 * pace }
    }
}

/// Outlines drawn in a 20 pt box (the tick in a 10 pt one), the same strings in the three stacks.
private enum Glyph {
    static let smiley = "M 17.6 10 C 17.6 14.2 14.2 17.6 10 17.6 C 5.8 17.6 2.4 14.2 2.4 10 C 2.4 5.8 5.8 2.4 10 2.4 C 14.2 2.4 17.6 5.8 17.6 10 Z M 7.5 8.4 L 7.51 8.4 M 12.5 8.4 L 12.51 8.4 M 13.53 11.95 C 12.89 13.32 11.52 14.2 10 14.2 C 8.48 14.2 7.11 13.32 6.47 11.95"
    static let clip = "M 14.6 9.93 L 9.86 14.67 C 8.61 15.92 6.58 15.92 5.33 14.67 C 4.08 13.42 4.08 11.39 5.33 10.14 L 10.85 4.63 C 11.71 3.77 13.1 3.77 13.96 4.63 C 14.82 5.49 14.82 6.88 13.96 7.74 L 8.8 12.9 C 8.33 13.37 7.57 13.37 7.1 12.9 C 6.63 12.43 6.63 11.67 7.1 11.2 L 10.99 7.31"
    static let image = "M 5.4 3.6 L 14.6 3.6 C 16.04 3.6 17.2 4.76 17.2 6.2 L 17.2 13.8 C 17.2 15.24 16.04 16.4 14.6 16.4 L 5.4 16.4 C 3.96 16.4 2.8 15.24 2.8 13.8 L 2.8 6.2 C 2.8 4.76 3.96 3.6 5.4 3.6 Z M 8.7 8 C 8.7 8.77 8.07 9.4 7.3 9.4 C 6.53 9.4 5.9 8.77 5.9 8 C 5.9 7.23 6.53 6.6 7.3 6.6 C 8.07 6.6 8.7 7.23 8.7 8 Z M 3.4 14.6 L 7.4 11 L 10.4 13.8 M 9.6 13 L 12.6 10.4 L 16.8 14"
    static let camera = "M 4.8 5.6 L 15.2 5.6 C 16.53 5.6 17.6 6.67 17.6 8 L 17.6 14 C 17.6 15.33 16.53 16.4 15.2 16.4 L 4.8 16.4 C 3.47 16.4 2.4 15.33 2.4 14 L 2.4 8 C 2.4 6.67 3.47 5.6 4.8 5.6 Z M 6.9 5.6 L 8 3.8 L 12 3.8 L 13.1 5.6 M 12.9 10.9 C 12.9 12.5 11.6 13.8 10 13.8 C 8.4 13.8 7.1 12.5 7.1 10.9 C 7.1 9.3 8.4 8 10 8 C 11.6 8 12.9 9.3 12.9 10.9 Z"
    static let mic = "M 10 2.4 C 11.44 2.4 12.6 3.56 12.6 5 L 12.6 9.2 C 12.6 10.64 11.44 11.8 10 11.8 C 8.56 11.8 7.4 10.64 7.4 9.2 L 7.4 5 C 7.4 3.56 8.56 2.4 10 2.4 Z M 15.2 9.2 C 15.2 12.07 12.87 14.4 10 14.4 C 7.13 14.4 4.8 12.07 4.8 9.2 M 10 14.4 L 10 17.4"
    static let plane = "M 4.2 3.9 L 16.8 10 L 4.2 16.1 L 6.4 10 Z M 6.4 10 L 10.8 10"
    static let tick = "M 2.6 5.2 L 4.4 7 L 7.5 3.2"
}

/// Builds a path from "M x y L x y C x1 y1 x2 y2 x y Z": absolute commands, separated by spaces.
private func foldingMessageBarPath(_ d: String) -> Path {
    var path = Path()
    var command: Character = "M"
    var numbers: [Double] = []
    for token in d.split(separator: " ") {
        guard let number = Double(token) else {
            command = token.first ?? "M"
            numbers = []
            if command == "Z" { path.closeSubpath() }
            continue
        }
        numbers.append(number)
        switch (command, numbers.count) {
        case ("M", 2):
            path.move(to: CGPoint(x: numbers[0], y: numbers[1]))
        case ("L", 2):
            path.addLine(to: CGPoint(x: numbers[0], y: numbers[1]))
        case ("C", 6):
            path.addCurve(
                to: CGPoint(x: numbers[4], y: numbers[5]),
                control1: CGPoint(x: numbers[0], y: numbers[1]),
                control2: CGPoint(x: numbers[2], y: numbers[3])
            )
        default:
            break
        }
    }
    return path
}

private struct FoldingMessageBarIcon: View {
    let d: String
    let color: Color
    var lineWidth = 1.5

    var body: some View {
        foldingMessageBarPath(d)
            .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
            .frame(width: 20, height: 20)
            .contentShape(Rectangle())
    }
}

/// A plain head-and-shoulders figure in a disc.
private struct FoldingMessageBarAvatar: View {
    let fill: Color
    let figure: Color

    var body: some View {
        ZStack {
            Circle().fill(fill)
            Circle().fill(figure).frame(width: 7.6, height: 7.6).offset(y: -2.6)
            Ellipse().fill(figure).frame(width: 15, height: 13).offset(y: 9.6)
        }
        .frame(width: Metrics.avatar, height: Metrics.avatar)
        .clipShape(Circle())
    }
}

/// The button dips under a finger, or under the script's.
private struct FoldingMessageBarPress: ButtonStyle {
    let held: Bool

    func makeBody(configuration: Configuration) -> some View {
        let down = configuration.isPressed || held
        configuration.label
            .scaleEffect(down ? 0.9 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: down)
    }
}
