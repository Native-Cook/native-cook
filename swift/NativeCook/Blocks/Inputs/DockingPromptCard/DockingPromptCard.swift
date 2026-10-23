import SwiftUI

/// An assistant prompt on a glass card over a drifting aurora. Each word is tinted violet as it is
/// typed. On send the sentence lifts out of the field and flies up into a message bubble while the
/// card shrinks to its field and docks at the bottom of the panel; after a moment of "thinking" the
/// card rises back and its heading resolves again from left to right.
struct DockingPromptCard: View {
    /// Controlled text. Leave it nil and the component keeps its own.
    var text: Binding<String>? = nil
    /// Types the script, sends it, waits for the card to come back and starts over.
    var autoPlay = false
    var assistant = "AI Assistant"
    var heading = "What are you working on?"
    var placeholder = "Ask me anything"
    /// The question autoPlay types.
    var script = "Help me plan a product launch"
    /// Fill of the message bubble.
    var accent = Color(hex: 0x5B2BE0)
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the message when the arrow or the return key is pressed.
    var onSend: ((String) -> Void)? = nil
    var onAdd: (() -> Void)? = nil
    var onMic: (() -> Void)? = nil
    var onAssistant: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var inner = ""
    @State private var playing = false
    @State private var caretOn = true
    /// The word being typed is still tinted.
    @State private var fresh = false
    /// The arrow is held down by the script.
    @State private var pressed = false
    /// A message is on its way: nothing else can be sent until the card is back.
    @State private var busy = false
    /// Counts sends and resets, to start and cancel what follows a send.
    @State private var run = 0
    @State private var docked = false
    @State private var sent = ""
    /// Where the sentence stood in the field when it was sent.
    @State private var from = CGPoint.zero
    @State private var flown = false
    @State private var bubbleShown = false
    @State private var bubbleSize = CGSize(width: 120, height: Metrics.bubbleHeight)
    @State private var thinking = false
    @State private var pulse = false
    @State private var revealed = true
    @State private var started = Date()

    private var value: String { text?.wrappedValue ?? inner }
    private var ready: Bool { !value.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        ZStack(alignment: .topLeading) {
            aurora
            DockingPromptCardProgress(value: docked ? 1 : 0) { card($0) }
                .animation(reduceMotion ? nil : Metrics.dock, value: docked)
            thinkingRow
            bubble
        }
        .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        .background(Color(hex: 0x262626))
        .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular))
        .overlay(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular).strokeBorder(.white.opacity(0.07), lineWidth: 1))
        .onAppear {
            playing = autoPlay && text == nil
            pulse = true
        }
        .onChange(of: focused) { _, now in
            // A touch takes the field over from the script.
            if now, playing {
                playing = false
                reset()
            }
        }
        .task(id: playing) { await play() }
        .task(id: run) { await afterSend() }
        .task(id: "\(focused)|\(busy)") {
            // The script resumes once the field has been left empty, and the card is back, for a while.
            guard autoPlay, text == nil, !focused, !playing, !busy else { return }
            try? await Task.sleep(for: .seconds(Metrics.resumeAfter))
            if !Task.isCancelled, value.isEmpty { playing = true }
        }
        .task(id: value) {
            // The newest word keeps its tint until the keys have stopped for a moment.
            fresh = !value.isEmpty
            try? await Task.sleep(for: .seconds(Metrics.tintHold))
            if !Task.isCancelled { fresh = false }
        }
        .task(id: "\(value)|\(focused)|\(playing)") {
            // The caret stays solid while keys arrive and blinks once they stop.
            caretOn = true
            guard focused || playing else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.blink))
                if Task.isCancelled { return }
                caretOn.toggle()
            }
        }
    }

    /// Five soft streaks of violet and magenta, each circling slowly once or twice per loop.
    private var aurora: some View {
        TimelineView(.animation(paused: reduceMotion)) { timeline in
            let time = reduceMotion ? 0 : timeline.date.timeIntervalSince(started)
            Canvas { context, _ in
                for blob in auroraBlobs {
                    let angle = tau * blob.turns * time / Metrics.loop + blob.phase
                    var layer = context
                    layer.translateBy(x: blob.x + blob.dx * sin(angle), y: blob.y + blob.dy * cos(angle))
                    layer.rotate(by: .degrees(blob.tilt))
                    layer.scaleBy(x: blob.rx, y: blob.ry)
                    layer.fill(
                        unitCircle,
                        with: .radialGradient(
                            Gradient(stops: auroraFalloff.map { .init(color: blob.color.opacity(blob.alpha * $0.alpha), location: $0.at) }),
                            center: .zero, startRadius: 0, endRadius: 1
                        )
                    )
                }
            }
        }
        .allowsHitTesting(false)
    }

    /// The glass card at `p` of the way from the middle of the panel (0) to its dock (1).
    private func card(_ p: Double) -> some View {
        let width = lerp(Metrics.cardWidth, Metrics.dockWidth, p)
        let height = lerp(Metrics.cardHeight, Metrics.dockHeight, p)
        let shape = RoundedRectangle(cornerRadius: max(0, lerp(Metrics.cardRadius, Metrics.dockRadius, p)), style: .circular)
        // The label and the heading are gone before the card is half way down.
        let fade = clamp01(1 - p * 2.5)
        return ZStack(alignment: .topLeading) {
            shape.fill(Color.rgba(18, 14, 30, 0.4))
            shape.fill(.white.opacity(0.05))
            // A magenta glow along the bottom edge.
            Canvas { context, size in
                context.fillEllipse(
                    center: CGPoint(x: size.width * 0.56, y: size.height + 4),
                    radii: CGSize(width: size.width * 0.62, height: min(72, size.height * 0.62)),
                    gradient: Gradient(colors: [Metrics.magenta.opacity(0.8), Metrics.magenta.opacity(0)])
                )
            }
            .allowsHitTesting(false)

            Button {
                onAssistant?()
            } label: {
                HStack(spacing: 5) {
                    Text(assistant)
                        .font(.system(size: 12.5, weight: .medium))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .fixedSize()
                    chevronIcon
                        .stroke(.white, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                        .frame(width: 10, height: 10)
                }
                .frame(height: 20)
                .contentShape(Rectangle())
            }
            .buttonStyle(DockingPromptCardPress())
            .opacity(fade)
            .offset(x: 18, y: 14)

            headingView
                .opacity(fade)
                .frame(width: width, height: 28)
                .offset(y: Metrics.headingY)
                .allowsHitTesting(false)

            field(p).offset(x: lerp(Metrics.fieldX, 0, p), y: lerp(Metrics.fieldY, 0, p))
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipShape(shape)
        .overlay(
            shape.strokeBorder(
                LinearGradient(colors: [Metrics.violet.opacity(0.8), Metrics.magenta.opacity(0.95)], startPoint: .top, endPoint: .bottom),
                lineWidth: 1
            )
        )
        .offset(x: lerp(Metrics.cardX, Metrics.dockX, p), y: lerp(Metrics.cardY, Metrics.dockY, p))
    }

    /// The field: a two-row box in the card, a slim capsule that fills it once docked.
    private func field(_ p: Double) -> some View {
        let width = lerp(Metrics.fieldWidth, Metrics.dockWidth, p)
        let height = lerp(Metrics.fieldHeight, Metrics.dockHeight, p)
        let inset = lerp(Metrics.iconInset, Metrics.dockHeight / 2, p)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: max(0, lerp(Metrics.fieldRadius, Metrics.dockRadius, p)), style: .circular)
                .fill(.white.opacity(lerp(0.16, 0.08, p)))
            sentence
                .frame(width: Metrics.textWidth, height: Metrics.line)
                .offset(x: lerp(Metrics.textX, Metrics.dockTextX, p), y: lerp(Metrics.textY, (Metrics.dockHeight - Metrics.line) / 2, p))
            Button {
                onAdd?()
            } label: {
                plusIcon
                    .stroke(.white.opacity(0.85), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                    .frame(width: 16, height: 16)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(DockingPromptCardPress())
            .position(x: inset, y: height - inset)
            actionButton.position(x: width - inset, y: height - inset)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
    }

    /// The words on one line, the caret after the last one, and the real field that owns the text.
    private var sentence: some View {
        let words = value.split(separator: " ").map(String.init)
        let caret = focused || (playing && !words.isEmpty)
        let spaced = value.last == " "
        return ZStack(alignment: .leading) {
            Text(placeholder)
                .font(Metrics.font)
                .foregroundStyle(.white.opacity(0.42))
                .lineLimit(1)
                .overlay(alignment: .leading) { caretMark(caret && words.isEmpty) }
                .opacity(words.isEmpty ? 1 : 0)
                .animation(words.isEmpty ? .easeOut(duration: Metrics.fade) : nil, value: words.isEmpty)

            DockingPromptCardLine(spacing: Metrics.gap) {
                ForEach(Array(words.enumerated()), id: \.offset) { index, word in
                    let last = index == words.count - 1
                    let tinted = last && fresh
                    Text(word)
                        .font(Metrics.font)
                        .foregroundStyle(tinted ? Metrics.tint : .white)
                        .lineLimit(1)
                        .fixedSize()
                        // The tint arrives at once and fades away.
                        .animation(tinted ? nil : .easeOut(duration: Metrics.tintFade), value: tinted)
                        .overlay(alignment: .trailing) {
                            if last {
                                Color.clear.frame(width: 0, height: Metrics.line).overlay(alignment: .leading) {
                                    caretMark(caret).offset(x: spaced ? Metrics.gap : 0.5)
                                }
                            }
                        }
                }
            }
            .frame(width: Metrics.textWidth, height: Metrics.line)
            .clipped()

            // The real field: it takes the keyboard, the words above are drawn from its text.
            TextField("", text: Binding(get: { value }, set: { edit($0) }))
                .font(Metrics.font)
                .foregroundStyle(.clear)
                .tint(.clear)
                .autocorrectionDisabled()
                .submitLabel(.send)
                .focused($focused)
                .onSubmit { send(byHand: true) }
                .accessibilityLabel(placeholder)
        }
    }

    private func caretMark(_ visible: Bool) -> some View {
        RoundedRectangle(cornerRadius: 0.75)
            .fill(.white)
            .frame(width: 1.5, height: 18)
            .opacity(visible && caretOn ? 1 : 0)
            .animation(.easeOut(duration: 0.08), value: caretOn)
    }

    /// A microphone while the field is empty, a white disc with an arrow once there is something to send.
    private var actionButton: some View {
        Button {
            if ready { send(byHand: true) } else { onMic?() }
        } label: {
            ZStack {
                micIcon
                    .stroke(.white.opacity(0.85), style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                    .frame(width: 16, height: 16)
                    .scaleEffect(ready && !reduceMotion ? 0.5 : 1)
                    .opacity(ready ? 0 : 1)
                arrowIcon
                    .stroke(Color(hex: 0x1B1626), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                    .frame(width: 16, height: 16)
                    .frame(width: 28, height: 28)
                    .background(Circle().fill(.white))
                    .scaleEffect(ready || reduceMotion ? (pressed ? 0.86 : 1) : 0.5)
                    .opacity(ready ? 1 : 0)
            }
            .frame(width: 36, height: 36)
            .contentShape(Rectangle())
            .animation(reduceMotion ? .easeOut(duration: 0.15) : Metrics.swap, value: ready)
            .animation(.easeOut(duration: Metrics.press), value: pressed)
        }
        .buttonStyle(DockingPromptCardPress())
        .accessibilityLabel(ready ? "Send" : "Dictate")
    }

    /// The heading, letter by letter: each one fades, sharpens and slides in a little after the one before.
    private var headingView: some View {
        let words = headingWords
        let count = Double(max(1, words.last.map { $0.start + $0.letters.count } ?? 1))
        return DockingPromptCardProgress(value: revealed ? 1 : 0) { p in
            HStack(spacing: Metrics.headingGap) {
                ForEach(words, id: \.start) { word in
                    HStack(spacing: 0) {
                        ForEach(Array(word.letters.enumerated()), id: \.offset) { index, letter in
                            let begin = Double(word.start + index) / count * (1 - Metrics.revealSpan)
                            let q = reduceMotion ? clamp01(p) : clamp01((p - begin) / Metrics.revealSpan)
                            Text(String(letter))
                                .opacity(q)
                                .blur(radius: (1 - q) * Metrics.revealBlur)
                                .offset(x: (q - 1) * Metrics.revealShift)
                        }
                    }
                }
            }
            .font(.system(size: 20))
            .foregroundStyle(.white)
            .fixedSize()
        }
        .animation(revealed && !reduceMotion ? .linear(duration: Metrics.reveal) : .easeOut(duration: Metrics.fade), value: revealed)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(heading)
    }

    /// The heading's words with the position of their first letter among all letters.
    private var headingWords: [(start: Int, letters: [Character])] {
        var start = 0
        return heading.split(separator: " ").map { word in
            defer { start += word.count }
            return (start, Array(word))
        }
    }

    /// The sent message: it leaves the field as bare text and lands at the top right as a bubble.
    private var bubble: some View {
        DockingPromptCardProgress(value: flown ? 1 : 0) { p in
            let q = clamp01(p)
            let lift = sin(.pi * q)
            let fill = clamp01((q - 0.15) / 0.6)
            let shape = RoundedRectangle(cornerRadius: Metrics.bubbleHeight / 2, style: .circular)
            Text(sent)
                .font(Metrics.font)
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
                .frame(minHeight: Metrics.line)
                .padding(.horizontal, Metrics.bubblePadX)
                .padding(.vertical, Metrics.bubblePadY)
                .background(shape.fill(accent.opacity(fill)))
                .overlay(shape.strokeBorder(Metrics.bubbleEdge.opacity(0.7 * fill), lineWidth: 1))
                .onGeometryChange(for: CGSize.self) { proxy in
                    proxy.size
                } action: { size in
                    bubbleSize = size
                }
                .scaleEffect(1 + Metrics.flyScale * lift)
                .frame(width: Metrics.bubbleMax, alignment: .topLeading)
                .offset(
                    x: lerp(from.x - Metrics.bubblePadX, Metrics.width - Metrics.margin - bubbleSize.width, p),
                    y: lerp(from.y - Metrics.bubblePadY, Metrics.bubbleTop, p) - lift * Metrics.flyArc
                )
        }
        .animation(reduceMotion ? nil : Metrics.fly, value: flown)
        .opacity(bubbleShown ? 1 : 0)
        // It appears in place of the words at once, and fades when the card comes back.
        .animation(bubbleShown && !reduceMotion ? nil : .easeOut(duration: Metrics.fade), value: bubbleShown)
        .allowsHitTesting(false)
    }

    private var thinkingRow: some View {
        HStack(spacing: 5) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(.white)
                    .frame(width: 6, height: 6)
                    .opacity(pulse || reduceMotion ? 1 : 0.35)
                    .offset(y: pulse && !reduceMotion ? -2.5 : 1)
                    .animation(
                        reduceMotion ? nil
                            : .easeInOut(duration: Metrics.pulse).repeatForever(autoreverses: true).delay(Double(index) * Metrics.pulseStagger),
                        value: pulse
                    )
            }
        }
        .frame(width: 52, height: 30)
        .background(Capsule(style: .circular).fill(.white.opacity(0.1)))
        .overlay(Capsule(style: .circular).strokeBorder(.white.opacity(0.14), lineWidth: 1))
        .scaleEffect(thinking || reduceMotion ? 1 : 0.8, anchor: .leading)
        .opacity(thinking ? 1 : 0)
        .animation(thinking && !reduceMotion ? Metrics.pop : .easeOut(duration: Metrics.fade), value: thinking)
        .offset(x: Metrics.margin, y: Metrics.bubbleTop + bubbleSize.height + 12)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// Where the sentence starts inside the panel while the card is at `p`.
    private func textOrigin(_ p: Double) -> CGPoint {
        CGPoint(
            x: lerp(Metrics.cardX, Metrics.dockX, p) + lerp(Metrics.fieldX, 0, p) + lerp(Metrics.textX, Metrics.dockTextX, p),
            y: lerp(Metrics.cardY, Metrics.dockY, p) + lerp(Metrics.fieldY, 0, p) + lerp(Metrics.textY, (Metrics.dockHeight - Metrics.line) / 2, p)
        )
    }

    private func edit(_ new: String) {
        guard new != value else { return }
        if let text { text.wrappedValue = new } else { inner = new }
        onChangeText?(new)
    }

    private func send(byHand: Bool) {
        let message = value.split(separator: " ").joined(separator: " ")
        focused = false
        guard !message.isEmpty, !busy else { return }
        busy = true
        if byHand { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        onSend?(message)
        // The bubble takes the place of the words before anything moves.
        withTransaction(Transaction(animation: nil)) {
            from = textOrigin(docked ? 1 : 0)
            sent = message
            pressed = false
        }
        edit("")
        run += 1
    }

    /// What a send sets off: the flight and the docking, the thinking dots, and the way back.
    private func afterSend() async {
        guard busy else { return }
        bubbleShown = true
        // One frame with the bubble over the words, so the flight starts from there.
        try? await Task.sleep(for: .milliseconds(20))
        if Task.isCancelled { return }
        flown = true
        docked = true
        revealed = false
        try? await Task.sleep(for: .seconds(Metrics.thinkAfter))
        if Task.isCancelled { return }
        thinking = true
        try? await Task.sleep(for: .seconds(Metrics.backAfter - Metrics.thinkAfter))
        if Task.isCancelled { return }
        thinking = false
        bubbleShown = false
        try? await Task.sleep(for: .seconds(Metrics.fade))
        if Task.isCancelled { return }
        docked = false
        flown = false
        try? await Task.sleep(for: .seconds(Metrics.revealAfter))
        if Task.isCancelled { return }
        revealed = true
        busy = false
    }

    /// Back to the resting card at once, whatever was going on.
    private func reset() {
        withTransaction(Transaction(animation: nil)) {
            busy = false
            pressed = false
            thinking = false
            bubbleShown = false
            flown = false
            docked = false
            revealed = true
        }
        run += 1
        edit("")
    }

    private func play() async {
        guard playing else { return }
        var origin = Date()
        func wait(until at: Double) async {
            let wait = origin.addingTimeInterval(at).timeIntervalSinceNow
            if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
        }
        while !Task.isCancelled {
            var at = Metrics.typeAt
            var typed = ""
            for character in script {
                await wait(until: at)
                if Task.isCancelled { return }
                typed.append(character)
                edit(typed)
                at += Metrics.key * Metrics.rhythm[(typed.count - 1) % Metrics.rhythm.count]
            }
            at += Metrics.sendAfter
            await wait(until: at - Metrics.press)
            if Task.isCancelled { return }
            pressed = true
            await wait(until: at)
            if Task.isCancelled { return }
            send(byHand: false)
            // The card is back, and the heading resolved, before the loop is over.
            origin = origin.addingTimeInterval(max(Metrics.loop, at + Metrics.backAfter + Metrics.fade + Metrics.revealAfter + Metrics.reveal + Metrics.typeAt))
            await wait(until: 0)
        }
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 440.0
    static let radius = 32.0
    static let margin = 16.0

    // The card in the middle of the panel, and docked at its bottom as a capsule.
    static let cardX = 18.0
    static let cardY = 114.0
    static let cardWidth = 314.0
    static let cardHeight = 200.0
    static let cardRadius = 20.0
    static let dockX = 14.0
    static let dockY = 374.0
    static let dockWidth = 322.0
    static let dockHeight = 52.0
    static let dockRadius = 26.0

    // The field inside the resting card. Docked, it fills the capsule.
    static let fieldX = 16.0
    static let fieldY = 100.0
    static let fieldWidth = 282.0
    static let fieldHeight = 84.0
    static let fieldRadius = 12.0
    static let headingY = 50.0
    static let headingGap = 5.0
    /// Centre of the plus and of the action button, from the field's bottom corners.
    static let iconInset = 22.0

    static let font = Font.system(size: 15)
    static let line = 20.0
    static let gap = 4.0
    static let textX = 14.0
    static let textY = 12.0
    static let dockTextX = 50.0
    static let textWidth = 222.0
    /// Room kept for the caret when a long sentence scrolls.
    static let caretRoom = 4.0

    static let bubbleTop = 20.0
    static let bubbleMax = 260.0
    static let bubblePadX = 14.0
    static let bubblePadY = 8.0
    static let bubbleHeight = line + bubblePadY * 2

    static let violet = Color(hex: 0x6A2BD9)
    static let magenta = Color(hex: 0xD63CFF)
    static let tint = Color(hex: 0xA77BFF)
    static let bubbleEdge = Color(hex: 0x9A7BFF)

    // autoPlay (seconds). Keys land `key` apart on average, stretched by `rhythm` in turn.
    static let typeAt = 0.8
    static let key = 0.06
    static let rhythm = [1.0, 0.72, 1.3, 0.85, 1.12, 0.64, 1.42, 0.95]
    /// From the last key to the tap on the arrow.
    static let sendAfter = 0.5
    static let loop = 9.0
    /// After the field is left empty, the script waits this long before it starts again.
    static let resumeAfter = 3.0

    // What follows a send, counted from it.
    static let thinkAfter = 0.5
    static let backAfter = 2.9
    /// From the card starting to rise to its heading starting to resolve.
    static let revealAfter = 0.35

    static let blink = 0.53
    static let press = 0.12
    static let fade = 0.25
    static let tintHold = 0.45
    static let tintFade = 0.4
    static let pulse = 0.45
    static let pulseStagger = 0.15

    /// The heading resolves over `reveal` seconds; each letter takes `revealSpan` of that.
    static let reveal = 0.9
    static let revealSpan = 0.35
    static let revealBlur = 5.0
    static let revealShift = 6.0

    /// The bubble rises this far above a straight path, and swells by this much, half way through its flight.
    static let flyArc = 18.0
    static let flyScale = 0.05

    static let dock = Animation.interpolatingSpring(mass: 1, stiffness: 150, damping: 19)
    static let fly = Animation.interpolatingSpring(mass: 1, stiffness: 130, damping: 17)
    static let swap = Animation.interpolatingSpring(mass: 1, stiffness: 500, damping: 24)
    static let pop = Animation.interpolatingSpring(mass: 1, stiffness: 420, damping: 22)
}

private struct DockingPromptCardBlob {
    let x: Double
    let y: Double
    let rx: Double
    let ry: Double
    /// Degrees its long axis is turned clockwise.
    let tilt: Double
    /// How far its centre swings each way.
    let dx: Double
    let dy: Double
    /// Whole circles per loop, so the loop closes.
    let turns: Double
    let phase: Double
    let color: Color
    let alpha: Double
}

private let auroraBlobs = [
    DockingPromptCardBlob(x: 70, y: 70, rx: 250, ry: 135, tilt: -35, dx: 24, dy: 16, turns: 1, phase: 0, color: Color(hex: 0x7A1FFF), alpha: 0.9),
    DockingPromptCardBlob(x: 300, y: 350, rx: 240, ry: 125, tilt: -30, dx: -20, dy: 18, turns: 1, phase: 1.9, color: Color(hex: 0xC81FFF), alpha: 0.8),
    DockingPromptCardBlob(x: 280, y: 90, rx: 170, ry: 110, tilt: 25, dx: 16, dy: -20, turns: 2, phase: 4.1, color: Color(hex: 0x4B1FD6), alpha: 0.65),
    DockingPromptCardBlob(x: 60, y: 410, rx: 170, ry: 90, tilt: 20, dx: 18, dy: 12, turns: 1, phase: 3.0, color: Color(hex: 0xE040FB), alpha: 0.45),
    DockingPromptCardBlob(x: 175, y: 225, rx: 210, ry: 90, tilt: -40, dx: 14, dy: -14, turns: 1, phase: 5.2, color: Color(hex: 0x5A14C8), alpha: 0.5),
]

/// How a streak fades from its centre to its rim: steep at first, then a long soft tail.
private let auroraFalloff: [(at: Double, alpha: Double)] = [(0, 1), (0.25, 0.72), (0.5, 0.4), (0.7, 0.16), (0.87, 0.04), (1, 0)]

// Icons, as outlines in a 16 pt box (the chevron in 10 pt).
private let chevronIcon = dockingPromptCardPath("M2.5 3.75 L5 6.25 L7.5 3.75")
private let plusIcon = dockingPromptCardPath("M8 3.25 L8 12.75 M3.25 8 L12.75 8")
private let arrowIcon = dockingPromptCardPath("M8 12.25 L8 3.75 M4.5 7.25 L8 3.75 L11.5 7.25")
private let micIcon = dockingPromptCardPath(
    "M8 2 C9.1 2 10 2.9 10 4 L10 7.5 C10 8.6 9.1 9.5 8 9.5 C6.9 9.5 6 8.6 6 7.5 L6 4 C6 2.9 6.9 2 8 2 Z "
        + "M4 7.5 C4 9.71 5.79 11.5 8 11.5 C10.21 11.5 12 9.71 12 7.5 M8 11.5 L8 13.75 M6 13.75 L10 13.75"
)

/// Builds a path from the part of SVG path data the icons use: absolute M, L, C and Z.
private func dockingPromptCardPath(_ data: String) -> Path {
    var path = Path()
    let scanner = Scanner(string: data)
    var command: Character = "M"
    func point() -> CGPoint {
        CGPoint(x: scanner.scanDouble() ?? 0, y: scanner.scanDouble() ?? 0)
    }
    while !scanner.isAtEnd {
        if let letter = scanner.scanCharacters(from: .letters)?.first { command = letter }
        switch command {
        case "M":
            path.move(to: point())
            command = "L"
        case "L":
            path.addLine(to: point())
        case "C":
            let first = point()
            let second = point()
            path.addCurve(to: point(), control1: first, control2: second)
        default:
            path.closeSubpath()
        }
    }
    return path
}

private func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double {
    from + (to - from) * t
}

private func clamp01(_ t: Double) -> Double {
    min(1, max(0, t))
}

/// Hands its content an animated number, so one spring can drive a whole layout.
private struct DockingPromptCardProgress<Content: View>: View, Animatable {
    var value: Double
    @ViewBuilder let content: (Double) -> Content

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View {
        content(value)
    }
}

/// One line of words. A sentence longer than the line slides left, so its end and the caret stay in view.
private struct DockingPromptCardLine: Layout {
    let spacing: Double

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        CGSize(width: proposal.width ?? 0, height: proposal.height ?? Metrics.line)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let widths = subviews.map { $0.sizeThatFits(.unspecified).width }
        let total = widths.reduce(0, +) + spacing * Double(max(0, widths.count - 1))
        var x = bounds.minX - max(0, total + Metrics.caretRoom - bounds.width)
        for (subview, width) in zip(subviews, widths) {
            subview.place(at: CGPoint(x: x, y: bounds.midY), anchor: .leading, proposal: .unspecified)
            x += width + spacing
        }
    }
}

private struct DockingPromptCardPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

#Preview {
    ZStack {
        Color(hex: 0x171717).ignoresSafeArea()
        DockingPromptCard(autoPlay: true)
    }
}
