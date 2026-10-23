import SwiftUI
import UIKit

/// Chat composer that starts as a white pill and grows with its text. While the text fits beside the
/// buttons it stays a pill; once it does not, the pill opens into a card whose height springs one
/// line at a time, the corners ease from a full pill to a rounded rectangle and the buttons drop
/// onto their own row. The send button replaces the microphone with the first character. Sending
/// fades the text, collapses the card into a short status pill with a shimmer, then brings the empty
/// field back.
struct GrowingComposerPill: View {
    /// The field's value when the caller owns it; left nil, the component keeps its own.
    var text: Binding<String>? = nil
    /// Types `script` by itself, sends it and starts over. Never opens the keyboard.
    var autoPlay = false
    var placeholder = "Ask anything"
    /// What autoPlay types.
    var script = "Can you help me find a great housewarming gift for my friend? maybe handmade, ceramic dinnerware, in white and tan under $100"
    /// Shown in the collapsed pill after sending.
    var status = "Thinking…"
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the trimmed message when the send button or the keyboard's send key is used.
    var onSend: ((String) -> Void)? = nil
    var onAttach: (() -> Void)? = nil
    var onVoice: (() -> Void)? = nil

    private enum Phase {
        case compose, sending, thinking
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    /// What autoPlay has typed so far; the caller's value is never touched by the script.
    @State private var typed = ""
    /// True while autoPlay owns the field.
    @State private var playing = false
    @State private var caretOn = false
    @State private var lastKey = Date.distantPast
    @State private var phase = Phase.compose
    @State private var thinkingSince = Date.distantPast
    /// Lines the keyboard's text takes, measured from the field itself.
    @State private var fieldLines = 1
    /// The send button's dip when autoPlay presses it.
    @State private var pressed = false
    @State private var run = 0
    @State private var sends = 0
    @State private var haptics = 0
    @State private var resume = 0

    private var value: Binding<String> {
        Binding(
            get: { text?.wrappedValue ?? draft },
            set: { new in
                if let text { text.wrappedValue = new } else { draft = new }
                onChangeText?(new)
            }
        )
    }

    private var shown: String { playing ? typed : value.wrappedValue }
    private var hasText: Bool { !shown.isEmpty }

    /// The text no longer fits beside the buttons: it moves to the top and the buttons to their own row.
    private var expanded: Bool {
        phase != .thinking && (shown.contains("\n") || Metrics.naturalWidth(of: shown) > Metrics.inlineWidth - Metrics.wrapMargin)
    }

    /// The script's text broken into lines by hand, so the drawn caret can follow the last one.
    private var typedLines: [String] {
        let rows = growingComposerPillWrap(typed, width: (expanded ? Metrics.fullWidth : Metrics.inlineWidth) - Metrics.caretRoom)
        return Array(rows.suffix(Metrics.maxLines))
    }

    private var lines: Int { playing ? typedLines.count : fieldLines }

    private var cardWidth: Double { phase == .thinking ? Metrics.statusWidth : Metrics.width }
    private var cardHeight: Double { expanded ? Double(min(lines, Metrics.maxLines)) * Metrics.line + Metrics.chrome : Metrics.pill }
    private var radius: Double { expanded ? Metrics.cardRadius : Metrics.pill / 2 }

    private var grow: Animation {
        reduceMotion ? .easeOut(duration: 0.2) : .interpolatingSpring(mass: 1, stiffness: Springs.growStiffness, damping: Springs.growDamping)
    }

    private var swap: Animation {
        reduceMotion ? .easeOut(duration: 0.2) : .interpolatingSpring(mass: 1, stiffness: Springs.swapStiffness, damping: Springs.swapDamping)
    }

    var body: some View {
        ZStack {
            GrowingComposerPillBackdrop()
            card
        }
        .frame(width: Metrics.panelWidth, height: Metrics.panelHeight)
        .sensoryFeedback(.impact(weight: .light), trigger: haptics)
        .onAppear { restart() }
        .onChange(of: autoPlay) { restart() }
        .onChange(of: focused) { _, isFocused in
            if isFocused { takeOver() }
            resume += 1
        }
        .task(id: run) { await play() }
        .task(id: sends) { await finishSend() }
        .task(id: resume) { await resumeWhenIdle() }
    }

    // MARK: Card

    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .circular)
        return ZStack(alignment: .topLeading) {
            editor
                .opacity(phase == .compose ? 1 : 0)
                // Coming back from the status, the field waits for the status to clear.
                .animation(.easeOut(duration: Script.fade).delay(phase == .compose ? Script.statusFade : 0), value: phase)
                // The field moves as one piece with the card's spring, whatever its letters are doing.
                .geometryGroup()
                .frame(width: expanded ? Metrics.fullWidth : Metrics.inlineWidth, alignment: .topLeading)
                .offset(x: expanded ? Metrics.padX : Metrics.inlineX, y: Metrics.padTop)

            Button {
                onAttach?()
            } label: {
                GrowingComposerPillPlus().frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(GrowingComposerPillPress())
            .accessibilityLabel("Attach")
            .opacity(phase == .thinking ? 0 : 1)
            .position(x: Metrics.buttonInset, y: cardHeight - Metrics.buttonInset)

            Button {
                onVoice?()
            } label: {
                GrowingComposerPillMic()
                    .stroke(Palette.icon, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                    .frame(width: 20, height: 20)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(GrowingComposerPillPress())
            .accessibilityLabel("Dictate")
            .scaleEffect(hasText || phase == .thinking ? (reduceMotion ? 1 : Metrics.swapScale) : 1)
            .opacity(hasText || phase == .thinking ? 0 : 1)
            .allowsHitTesting(!hasText)
            .animation(swap, value: hasText)
            .position(x: cardWidth - Metrics.buttonInset, y: cardHeight - Metrics.buttonInset)

            Button(action: send) {
                ZStack {
                    Circle().fill(Palette.send)
                    GrowingComposerPillArrow()
                        .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
                .frame(width: Metrics.sendSize, height: Metrics.sendSize)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(GrowingComposerPillPress())
            .accessibilityLabel("Send")
            .scaleEffect(pressed ? Metrics.pressScale : 1)
            .animation(.easeOut(duration: 0.12), value: pressed)
            .scaleEffect(hasText && phase != .thinking ? 1 : (reduceMotion ? 1 : Metrics.swapScale))
            .opacity(hasText && phase != .thinking ? 1 : 0)
            .allowsHitTesting(hasText && phase == .compose)
            .animation(swap, value: hasText)
            .position(x: cardWidth - Metrics.buttonInset, y: cardHeight - Metrics.buttonInset)

            statusLabel
                .opacity(phase == .thinking ? 1 : 0)
                .animation(.easeOut(duration: phase == .thinking ? Script.fade : Script.statusFade).delay(phase == .thinking ? Script.statusFade : 0), value: phase)
                .position(x: cardWidth / 2, y: cardHeight / 2)
                .allowsHitTesting(false)
        }
        .frame(width: cardWidth, height: cardHeight, alignment: .topLeading)
        .clipShape(shape)
        .background(shape.fill(.white).shadow(color: Palette.shadow, radius: 14, y: 10))
        // While autoPlay runs, a touch anywhere on the composer hands the field to the keyboard.
        .overlay {
            if playing {
                Color.clear.contentShape(Rectangle()).onTapGesture {
                    takeOver()
                    focused = true
                }
            }
        }
        .animation(grow, value: cardHeight)
        .animation(grow, value: cardWidth)
    }

    private var editor: some View {
        ZStack(alignment: .topLeading) {
            Text(placeholder)
                .foregroundStyle(Palette.placeholder)
                .lineLimit(1)
                .padding(.top, Metrics.leading / 2)
                .opacity(hasText || caretOn ? 0 : 1)
                .allowsHitTesting(false)

            TextField("", text: value, axis: .vertical)
                .lineLimit(1...Metrics.maxLines)
                .foregroundStyle(phase == .compose ? Palette.ink : Palette.sent)
                .tint(Palette.caret)
                .focused($focused)
                .submitLabel(.send)
                .onSubmit(send)
                .padding(.top, Metrics.leading / 2)
                .onGeometryChange(for: Int.self) { proxy in
                    max(1, Int((proxy.size.height / Metrics.line).rounded()))
                } action: { count in
                    fieldLines = count
                }
                .opacity(playing ? 0 : 1)

            if playing {
                let rows = typedLines
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(rows.indices, id: \.self) { index in
                        HStack(spacing: 1) {
                            Text(rows[index])
                            if index == rows.count - 1 { caret }
                        }
                        .frame(height: Metrics.line)
                    }
                }
                .foregroundStyle(phase == .compose ? Palette.ink : Palette.sent)
                .lineLimit(1)
                .fixedSize()
                // Letters land at once: a line that wraps must not cross-fade with the one before.
                .transaction { $0.animation = nil }
                .allowsHitTesting(false)
            }
        }
        .font(.system(size: Metrics.fontSize))
        .lineSpacing(Metrics.leading)
    }

    /// The caret autoPlay draws after the last letter, since the field itself is not focused.
    private var caret: some View {
        TimelineView(.animation(paused: !caretOn)) { timeline in
            RoundedRectangle(cornerRadius: 1, style: .circular)
                .fill(Palette.caret)
                .frame(width: 2, height: Metrics.caretHeight)
                .opacity(caretOn ? growingComposerPillBlink(idle: timeline.date.timeIntervalSince(lastKey)) : 0)
        }
    }

    /// The status, one letter at a time so a band of light can sweep across it.
    private var statusLabel: some View {
        let letters = Array(status)
        return TimelineView(.animation(paused: phase != .thinking || reduceMotion)) { timeline in
            let elapsed = timeline.date.timeIntervalSince(thinkingSince) - Script.shimmerDelay
            let sweep = elapsed > 0 && !reduceMotion ? (elapsed / Script.shimmerSweep).truncatingRemainder(dividingBy: 1) : -1
            HStack(spacing: 0) {
                ForEach(letters.indices, id: \.self) { index in
                    Text(String(letters[index]))
                        .opacity(growingComposerPillShimmer(index: index, count: letters.count, sweep: sweep))
                }
            }
            .font(.system(size: Metrics.fontSize, weight: .medium))
            .foregroundStyle(Palette.ink)
            .fixedSize()
        }
    }

    // MARK: Behaviour

    private func restart() {
        playing = autoPlay
        reset()
        run += 1
    }

    private func reset() {
        typed = ""
        caretOn = false
        pressed = false
        phase = .compose
        sends += 1
    }

    /// A touch stops the script and leaves an empty field behind.
    private func takeOver() {
        guard playing else { return }
        playing = false
        reset()
        run += 1
    }

    private func send() {
        let message = shown.trimmingCharacters(in: .whitespacesAndNewlines)
        guard phase == .compose, !message.isEmpty else { return }
        if !playing {
            haptics += 1
            onSend?(message)
        }
        phase = .sending
        sends += 1
    }

    /// The text has started to fade: clear it, show the status for a while, then return to the empty field.
    private func finishSend() async {
        guard phase == .sending else { return }
        try? await Task.sleep(for: .seconds(Script.collapseAfter))
        guard !Task.isCancelled else { return }
        if playing { typed = "" } else { value.wrappedValue = "" }
        caretOn = false
        thinkingSince = .now
        phase = .thinking
        try? await Task.sleep(for: .seconds(Script.thinkFor))
        guard !Task.isCancelled else { return }
        phase = .compose
    }

    /// The autoPlay loop. Every step waits for its own moment after the loop began, so the period stays `Script.loop`.
    private func play() async {
        guard playing else { return }
        let clock = ContinuousClock()
        let keys = growingComposerPillRhythm(script)
        var start = clock.now
        while !Task.isCancelled {
            do {
                try await clock.sleep(until: start + .seconds(Script.caretAt))
                lastKey = .now
                caretOn = true
                for (index, at) in keys.enumerated() {
                    try await clock.sleep(until: start + .seconds(Script.typeAt + at))
                    typed = String(script.prefix(index + 1))
                    lastKey = .now
                }
                try await clock.sleep(until: start + .seconds(Script.pressAt))
                pressed = true
                try await clock.sleep(until: start + .seconds(Script.sendAt))
                pressed = false
                send()
                start += .seconds(Script.loop)
                try await clock.sleep(until: start)
            } catch {
                return
            }
        }
    }

    /// autoPlay comes back once the field has been left empty and unfocused for a while.
    private func resumeWhenIdle() async {
        guard autoPlay, !playing, !focused else { return }
        try? await Task.sleep(for: .seconds(Script.resumeAfter))
        guard !Task.isCancelled, !focused, value.wrappedValue.isEmpty, phase == .compose else { return }
        playing = true
        run += 1
    }
}

private enum Metrics {
    static let panelWidth = 350.0
    static let panelHeight = 420.0
    static let panelRadius = 40.0

    static let width = 320.0
    /// Height of the single-line pill and of the status pill.
    static let pill = 48.0
    static let statusWidth = 140.0
    static let cardRadius = 22.0

    static let fontSize = 16.0
    static let line = 21.0
    static let maxLines = 6
    /// Extra space between lines that brings the system font's own line height up to `line`.
    static let leading = line - UIFont.systemFont(ofSize: fontSize).lineHeight

    static let padTop = 13.5
    static let padX = 18.0
    /// Where the text starts while it shares the pill with the buttons.
    static let inlineX = 46.0
    static let inlineWidth = width - inlineX * 2
    static let fullWidth = width - padX * 2
    /// The card opens this much before the text would touch the button on the right.
    static let wrapMargin = 8.0
    /// Everything in the open card that is not text: the padding above it and the row of buttons below.
    static let chrome = 67.0
    static let caretHeight = 19.0
    /// Width kept free at the end of a line for the drawn caret.
    static let caretRoom = 3.0

    /// Distance of a button's centre from the card's side and bottom edges.
    static let buttonInset = 24.0
    static let sendSize = 30.0
    static let swapScale = 0.4
    static let pressScale = 0.86

    static func naturalWidth(of text: String) -> Double {
        (text as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: fontSize)]).width
    }
}

private enum Springs {
    // Height, width and corner radius of the card: a soft spring with a little overshoot.
    static let growStiffness = 260.0
    static let growDamping = 24.0
    // Microphone and send button trading places.
    static let swapStiffness = 420.0
    static let swapDamping = 24.0
}

/// The autoPlay loop, in seconds from its start.
private enum Script {
    static let caretAt = 0.5
    static let typeAt = 0.8
    /// The whole script is typed in this time; a short one is typed no slower than `slowestKey` per letter.
    static let typeSpan = 4.5
    static let slowestKey = 0.06
    static let pressAt = 5.75
    static let sendAt = 5.87
    static let loop = 9.4

    static let fade = 0.22
    /// The status label leaves faster than it came, before the placeholder returns.
    static let statusFade = 0.12
    /// From the send to the collapse into the status pill.
    static let collapseAfter = 0.26
    static let thinkFor = 1.87
    static let shimmerDelay = 0.25
    static let shimmerSweep = 0.8
    /// After the field is left empty and unfocused, autoPlay waits this long.
    static let resumeAfter = 3.0
}

private enum Palette {
    static let ink = Color(hex: 0x111114)
    static let sent = Color(hex: 0xa9a9b4)
    static let placeholder = Color(hex: 0x9c9ca8)
    static let icon = Color(hex: 0x4a4a52)
    static let caret = Color(hex: 0x2d6bff)
    static let send = Color(hex: 0x0e0e10)
    static let shadow = Color(hex: 0x5a3fc0, opacity: 0.2)
}

/// Seconds after typing starts at which each letter of `script` lands: uneven like a person's typing
/// (a beat after a space, a pause after punctuation) but the same every time.
private func growingComposerPillRhythm(_ script: String) -> [Double] {
    let jitter = [0, 0.45, -0.2, 0.6, 0.1, -0.3, 0.75]
    var weights: [Double] = []
    var previous: Character? = nil
    for (index, character) in script.enumerated() {
        var weight = 1 + jitter[index % jitter.count]
        if previous == " " { weight += 0.6 }
        if let previous, ",.?!".contains(previous) { weight += 3.2 }
        weights.append(index == 0 ? 0 : weight)
        previous = character
    }
    let unit = min(Script.typeSpan / max(weights.reduce(0, +), 1), Script.slowestKey)
    var at = 0.0
    return weights.map { weight in
        at += weight * unit
        return at
    }
}

/// Opacity of one status letter while the band of light, `sweep` 0…1 across the word, passes it.
private func growingComposerPillShimmer(index: Int, count: Int, sweep: Double) -> Double {
    guard sweep >= 0, count > 0 else { return 1 }
    let x = (Double(index) + 0.5) / Double(count)
    let centre = -0.25 + 1.5 * sweep
    let distance = (x - centre) / 0.2
    return 1 - 0.72 * exp(-distance * distance)
}

/// Solid while keys are landing; after half a second without one it blinks like the system caret.
private func growingComposerPillBlink(idle: Double) -> Double {
    guard idle > 0.5 else { return 1 }
    let t = (idle - 0.5).truncatingRemainder(dividingBy: 1)
    if t < 0.12 { return 1 - t / 0.12 }
    if t < 0.5 { return 0 }
    if t < 0.62 { return (t - 0.5) / 0.12 }
    return 1
}

/// Breaks `text` into lines no wider than `width`, word by word; a space typed at the end of a line hangs past it.
private func growingComposerPillWrap(_ text: String, width: Double) -> [String] {
    var lines: [String] = []
    for paragraph in text.split(separator: "\n", omittingEmptySubsequences: false) {
        var line = ""
        for (index, word) in paragraph.split(separator: " ", omittingEmptySubsequences: false).enumerated() {
            let candidate = index == 0 ? String(word) : line + " " + word
            if index > 0, !word.isEmpty, Metrics.naturalWidth(of: candidate) > width {
                lines.append(line)
                line = String(word)
            } else {
                line = candidate
            }
        }
        lines.append(line)
    }
    return lines
}

private struct GrowingComposerPillPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? Metrics.pressScale : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

private struct GrowingComposerPillPlus: View {
    var body: some View {
        ZStack {
            Capsule().frame(width: 16, height: 1.7)
            Capsule().frame(width: 1.7, height: 16)
        }
        .foregroundStyle(Palette.icon)
    }
}

/// Outline microphone in a 20 × 20 box.
private struct GrowingComposerPillMic: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRoundedRect(in: CGRect(x: 7.25, y: 2.5, width: 5.5, height: 9.5), cornerSize: CGSize(width: 2.75, height: 2.75))
        path.move(to: CGPoint(x: 4.5, y: 9.5))
        path.addArc(center: CGPoint(x: 10, y: 9.5), radius: 5.5, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: true)
        path.move(to: CGPoint(x: 10, y: 15))
        path.addLine(to: CGPoint(x: 10, y: 17.75))
        return path
    }
}

/// Up arrow in the 30 × 30 send button.
private struct GrowingComposerPillArrow: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 15, y: 21))
        path.addLine(to: CGPoint(x: 15, y: 9.5))
        path.move(to: CGPoint(x: 10, y: 14.25))
        path.addLine(to: CGPoint(x: 15, y: 9.25))
        path.addLine(to: CGPoint(x: 20, y: 14.25))
        return path
    }
}

/// The pastel panel the composer floats on: a lavender-to-sky wash with soft blobs of colour.
private struct GrowingComposerPillBackdrop: View {
    private struct Blob {
        let color: Color
        let x: Double
        let y: Double
        let radius: Double
    }

    private let blobs = [
        Blob(color: Color(hex: 0xa9d4f7), x: 0.22, y: 0.1, radius: 230),
        Blob(color: Color(hex: 0xc9b8f5), x: 0.98, y: 0.22, radius: 240),
        Blob(color: Color(hex: 0xf5b8d8), x: 0.82, y: 0.96, radius: 250),
        Blob(color: Color(hex: 0xfafaff), x: 0.06, y: 0.6, radius: 190),
        Blob(color: Color(hex: 0xb3a2f0), x: 0.3, y: 1.08, radius: 170),
    ]

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(hex: 0xcfc2f8), Color(hex: 0xb4d0f8)],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
            ForEach(blobs.indices, id: \.self) { index in
                let blob = blobs[index]
                RadialGradient(
                    colors: [blob.color, blob.color.opacity(0)],
                    center: UnitPoint(x: blob.x, y: blob.y), startRadius: 0, endRadius: blob.radius
                )
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular))
    }
}

#Preview {
    ZStack {
        Color(hex: 0xf1eff8).ignoresSafeArea()
        GrowingComposerPill(autoPlay: true)
    }
}
