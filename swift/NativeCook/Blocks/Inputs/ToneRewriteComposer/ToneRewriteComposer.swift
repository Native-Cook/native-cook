import SwiftUI

enum ToneRewriteComposerIcon {
    case pencil, check, smile, shrink, heart
}

struct ToneRewriteComposerTone: Equatable {
    var label: String
    var icon: ToneRewriteComposerIcon
}

/// Rewrite sheet for a chat draft. The draft sits in the middle as a blue bubble above a row of
/// tone chips and an instruction field. Choosing a tone (or sending an instruction) dissolves the
/// bubble into a shimmering shape, then the rewrite is written into it and the bubble grows to
/// fit; dots under it tell the versions apart and a swipe on the bubble goes back to the original.
struct ToneRewriteComposer: View {
    /// The instruction field's text when the parent owns it. Leave nil and the sheet keeps its own.
    var text: Binding<String>? = nil
    /// Picks the last tone by itself in a loop, without the keyboard, until the sheet is touched.
    var autoPlay = false
    var title = "Rewrite"
    var cancelLabel = "Cancel"
    var applyLabel = "Apply"
    /// The message being rewritten.
    var draft = "It's getting ugly out here"
    /// What the rewrite comes back as. Change it from `onSend` to show another result.
    var rewritten = "Things are getting tough out here, but we can get through this together."
    /// The chips. autoPlay picks the last one.
    var tones = [
        ToneRewriteComposerTone(label: "Rephrase", icon: .pencil),
        ToneRewriteComposerTone(label: "Fix grammar", icon: .check),
        ToneRewriteComposerTone(label: "Make funnier", icon: .smile),
        ToneRewriteComposerTone(label: "Shorter", icon: .shrink),
        ToneRewriteComposerTone(label: "Make supportive", icon: .heart),
    ]
    var placeholder = "Describe your changes"
    /// Seconds the bubble shimmers before the result is written.
    var thinkTime = 1.25
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the instruction when a chip is chosen or the field is sent.
    var onSend: ((String) -> Void)? = nil
    /// Called with the version on screen.
    var onApply: ((String) -> Void)? = nil
    var onCancel: (() -> Void)? = nil

    private enum Phase {
        case idle, thinking, done
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var typedText = ""
    /// The script is running.
    @State private var playing = false
    @State private var phase = Phase.idle
    /// A rewrite exists next to the original.
    @State private var hasResult = false
    /// The version on screen: 0 is the draft, 1 the rewrite.
    @State private var index = 0
    /// Letters of the rewrite written so far.
    @State private var written = 0
    @State private var chosen: Int? = nil
    /// The instruction the sheet is working on, or has answered.
    @State private var instruction: String? = nil
    @State private var requests = 0
    @State private var thinkStart = Date.now
    @State private var spins = 0.0
    /// Sizes of the two versions' bubbles, measured from their text.
    @State private var sizes = [CGSize.zero, CGSize.zero]
    /// How far the bubble has been pulled sideways.
    @State private var pull = 0.0
    /// The script's finger.
    @State private var heldChip: Int? = nil
    @State private var heldCancel = false
    /// The chip row has been scrolled to its end by the script.
    @State private var drifted = false
    @State private var taps = 0
    @State private var steps = 0
    @State private var resumes = 0

    private var controlled: Bool { text != nil }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion }
    private var value: String { text?.wrappedValue ?? typedText }
    private var thinking: Bool { phase == .thinking }
    /// The field holds the instruction being worked on: its button turns into "again".
    private var answered: Bool { phase != .idle && instruction == value }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            stage
            chips
            field
                .padding(.horizontal, Metrics.inset)
                .padding(.top, 12)
                .padding(.bottom, Metrics.inset)
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .background(
            RoundedRectangle(cornerRadius: Metrics.radius, style: .circular)
                .fill(LinearGradient(colors: [Color(hex: 0xe9f2ff), Color(hex: 0xf3ecfb)], startPoint: .top, endPoint: .bottom))
                .shadow(color: Color.rgba(52, 72, 130, 0.2), radius: 22, y: 16)
        )
        .sensoryFeedback(.impact(weight: .light), trigger: taps)
        .sensoryFeedback(.selection, trigger: steps)
        .onAppear { playing = auto }
        .onChange(of: auto) { playing = auto }
        .onChange(of: focused) { _, now in
            if now {
                // A touch on the field ends the script: the sheet goes back to rest for the keyboard.
                if playing {
                    interrupt()
                    reset()
                }
            } else {
                resumes += 1
            }
        }
        .task(id: playing) {
            guard playing else { return }
            await play()
        }
        .task(id: requests) {
            guard thinking else { return }
            await answer()
        }
        .task(id: resumes) {
            // The loop starts over a few seconds after the sheet is left at rest.
            guard resumes > 0, auto, !playing else { return }
            try? await Task.sleep(for: .seconds(Metrics.resume))
            guard !Task.isCancelled, !focused, value.isEmpty, phase == .idle else { return }
            playing = true
        }
    }

    // MARK: Top bar

    private var topBar: some View {
        ZStack {
            HStack(spacing: 6) {
                toneRewriteComposerPath(Glyph.spark)
                    .fill(LinearGradient(colors: [Metrics.blue, Color(hex: 0xa35cff)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 20, height: 20)
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x1c1e21))
            }
            HStack {
                Button(cancelLabel) {
                    interrupt()
                    onCancel?()
                    reset()
                    resumes += 1
                }
                .buttonStyle(ToneRewriteComposerFade(held: heldCancel))
                .font(.system(size: 15))
                .foregroundStyle(Metrics.action)
                Spacer()
                Button(applyLabel) {
                    interrupt()
                    taps += 1
                    onApply?(index == 1 ? rewritten : draft)
                }
                .buttonStyle(ToneRewriteComposerFade(held: false))
                .font(.system(size: 15, weight: .semibold))
                // Pale until there is something to apply.
                .foregroundStyle(hasResult ? Metrics.action : Color(hex: 0xaebbd2))
                .animation(.easeOut(duration: 0.25), value: hasResult)
                .disabled(!hasResult)
            }
            .padding(.horizontal, 18)
        }
        .lineLimit(1)
        .frame(height: Metrics.barHeight)
    }

    // MARK: Bubble

    private var stage: some View {
        ZStack {
            ZStack {
                bubbleBack
                    .frame(width: sizes[index].width, height: sizes[index].height)
                    // The bubble takes the size of the version it shows.
                    .animation(sizes[0] == .zero ? nil : (reduceMotion ? .easeOut(duration: 0.2) : Metrics.sizeSpring), value: index)
                version(0, Text(draft))
                version(
                    1,
                    // The letters still to come hold their place, so the lines do not move.
                    Text(rewritten.prefix(written)) + Text(rewritten.dropFirst(written)).foregroundColor(.clear)
                )
            }
            .offset(x: pull)

            dots
                .frame(maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, 16)
        }
        .frame(maxWidth: .infinity)
        .frame(height: Metrics.stageHeight)
        .contentShape(Rectangle())
        .gesture(swipe)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(index == 1 ? rewritten : draft)
    }

    /// One version's text in its padding. It is laid out at rest size even while hidden, which is
    /// where the bubble's size comes from.
    private func version(_ slot: Int, _ label: Text) -> some View {
        let shown = slot == index && !thinking
        return HStack {
            label
                .font(.system(size: 15))
                .lineSpacing(2)
                .foregroundStyle(.white)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, Metrics.bubblePadX)
                .padding(.vertical, Metrics.bubblePadY)
                .onGeometryChange(for: CGSize.self) { $0.size } action: { sizes[slot] = $0 }
        }
        .frame(width: Metrics.bubbleMax)
        .opacity(shown ? 1 : 0)
        .animation(.easeOut(duration: 0.18).delay(shown ? 0.12 : 0), value: shown)
    }

    /// The bubble's shape: blue, or pale with a band of light crossing it while the rewrite is made.
    private var bubbleBack: some View {
        ZStack {
            Metrics.blue.opacity(thinking ? 0 : 1)
            TimelineView(.animation(paused: !thinking)) { timeline in
                let beat = timeline.date.timeIntervalSince(thinkStart) / Metrics.shimmer
                let sweep = beat - beat.rounded(.down)
                ZStack {
                    Color(hex: 0xcfdcf7)
                    LinearGradient(
                        colors: [.white.opacity(0), .white.opacity(0.85), .white.opacity(0)],
                        startPoint: .leading, endPoint: .trailing
                    )
                    .frame(width: Metrics.band)
                    .offset(x: reduceMotion ? 0 : (sweep - 0.5) * Metrics.sweep)
                    toneRewriteComposerPath(Glyph.star)
                        .fill(Metrics.blue.opacity(0.75))
                        .frame(width: 20, height: 20)
                        .scaleEffect(reduceMotion ? 1 : 1 + 0.18 * sin(beat * tau))
                        .rotationEffect(.degrees(reduceMotion ? 0 : 90 * beat))
                }
            }
            .opacity(thinking ? 1 : 0)
        }
        .animation(.easeOut(duration: 0.22), value: thinking)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.bubbleRadius, style: .circular))
    }

    private var dots: some View {
        HStack(spacing: 6) {
            ForEach(0..<2, id: \.self) { slot in
                Button {
                    interrupt()
                    show(slot, byUser: true)
                } label: {
                    Circle()
                        .fill(Metrics.blue.opacity(slot == index ? 1 : 0.25))
                        .frame(width: 6, height: 6)
                        .animation(.easeOut(duration: 0.2), value: index)
                        .contentShape(Rectangle().inset(by: -6))
                }
                .buttonStyle(.plain)
                .scaleEffect(hasResult || reduceMotion ? 1 : 0.3)
                .opacity(hasResult ? 1 : 0)
                .animation(
                    hasResult && !reduceMotion
                        ? Metrics.dotSpring.delay(0.15 + Double(slot) * 0.07)
                        : .easeOut(duration: 0.2),
                    value: hasResult
                )
            }
        }
        .allowsHitTesting(hasResult)
        .accessibilityHidden(true)
    }

    /// A sideways pull on the bubble turns to the other version.
    private var swipe: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { drag in
                interrupt()
                pull = drag.translation.width * Metrics.pullShare
            }
            .onEnded { drag in
                if drag.translation.width > Metrics.swipeDistance {
                    show(index - 1, byUser: true)
                } else if drag.translation.width < -Metrics.swipeDistance {
                    show(index + 1, byUser: true)
                }
                withAnimation(reduceMotion ? .easeOut(duration: 0.2) : Metrics.pullSpring) { pull = 0 }
            }
    }

    // MARK: Chips and field

    private var chips: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Metrics.chipGap) {
                    Color.clear.frame(width: Metrics.inset - Metrics.chipGap).id(Edge.leading)
                    ForEach(Array(tones.enumerated()), id: \.offset) { slot, tone in
                        chip(slot, tone)
                    }
                    Color.clear.frame(width: Metrics.inset - Metrics.chipGap).id(Edge.trailing)
                }
            }
            .onChange(of: drifted) { _, now in
                withAnimation(.easeInOut(duration: now ? Metrics.drift : Metrics.driftBack)) {
                    proxy.scrollTo(now ? Edge.trailing : Edge.leading, anchor: now ? .trailing : .leading)
                }
            }
        }
        .frame(height: Metrics.chipHeight + 2)
    }

    private func chip(_ slot: Int, _ tone: ToneRewriteComposerTone) -> some View {
        let picked = chosen == slot
        return Button {
            interrupt()
            choose(slot)
        } label: {
            HStack(spacing: 6) {
                toneRewriteComposerPath(Glyph.tone(tone.icon))
                    .stroke(style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                    .frame(width: 14, height: 14)
                Text(tone.label)
                    .font(.system(size: 13, weight: .medium))
                    .lineLimit(1)
            }
            .foregroundStyle(picked ? Metrics.action : Color(hex: 0x1c1e21))
            .padding(.horizontal, 12)
            .frame(height: Metrics.chipHeight)
            .background(Capsule().fill(picked ? Color(hex: 0xe1edff) : .white))
            .overlay(Capsule().strokeBorder(picked ? Metrics.blue.opacity(0.45) : Color.rgba(30, 50, 110, 0.07), lineWidth: 1))
            .animation(.easeOut(duration: 0.2), value: picked)
        }
        .buttonStyle(ToneRewriteComposerPress(held: heldChip == slot))
        // The other chips step back while the rewrite is made.
        .opacity(thinking && !picked ? 0.45 : 1)
        .animation(.easeOut(duration: 0.2), value: thinking)
    }

    private var field: some View {
        HStack(spacing: 8) {
            ZStack(alignment: .leading) {
                Text(placeholder)
                    .foregroundStyle(Color(hex: 0x8a8f99))
                    .lineLimit(1)
                    .opacity(value.isEmpty ? 1 : 0)
                    .allowsHitTesting(false)
                TextField("", text: Binding(get: { value }, set: { write($0) }))
                    .focused($focused)
                    .foregroundStyle(Color(hex: 0x1c1e21))
                    .tint(Metrics.action)
                    .submitLabel(.send)
                    .onSubmit {
                        interrupt()
                        rewrite(value)
                    }
                    .accessibilityLabel(placeholder)
            }
            .font(.system(size: 15))

            Button {
                interrupt()
                rewrite(value)
            } label: {
                ZStack {
                    Circle()
                        .fill(value.isEmpty ? Color(hex: 0xdbe3f1) : Metrics.blue)
                        .opacity(answered ? 0 : 1)
                    ToneRewriteComposerIconView(d: Glyph.arrow, color: .white, lineWidth: 1.9)
                        .scaleEffect(answered && !reduceMotion ? 0.5 : 1)
                        .opacity(answered ? 0 : 1)
                    // Two turns while the rewrite is made, ending as the result lands.
                    ToneRewriteComposerIconView(d: Glyph.again, color: Metrics.action, lineWidth: 1.7)
                        .rotationEffect(.degrees(reduceMotion ? 0 : 360 * spins))
                        .animation(.easeInOut(duration: thinkTime), value: spins)
                        .scaleEffect(answered || reduceMotion ? 1 : 0.5)
                        .opacity(answered ? 1 : 0)
                }
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.2), value: answered)
                .animation(.easeOut(duration: 0.15), value: value.isEmpty)
                .frame(width: Metrics.sendSize, height: Metrics.sendSize)
            }
            .buttonStyle(ToneRewriteComposerPress(held: false))
            .disabled(value.isEmpty || thinking)
            .accessibilityLabel(answered ? "Rewrite again" : "Rewrite")
        }
        .padding(.leading, 14)
        .padding(.trailing, 7)
        .frame(height: Metrics.fieldHeight)
        .background(
            RoundedRectangle(cornerRadius: Metrics.fieldRadius, style: .circular)
                .fill(.white)
                .shadow(color: Color.rgba(52, 72, 130, 0.08), radius: 5, y: 2)
        )
    }

    // MARK: Actions

    private func write(_ next: String) {
        if let text { text.wrappedValue = next } else { typedText = next }
        onChangeText?(next)
    }

    private func choose(_ slot: Int) {
        chosen = slot
        write(tones[slot].label)
        rewrite(tones[slot].label)
    }

    /// The bubble dissolves, and the result arrives `thinkTime` later.
    private func rewrite(_ request: String) {
        let request = request.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !request.isEmpty, !thinking else { return }
        if chosen.map({ tones[$0].label != request }) ?? false { chosen = nil }
        if !playing {
            taps += 1
            onSend?(request)
        }
        instruction = request
        phase = .thinking
        thinkStart = .now
        spins += 2
        requests += 1
    }

    private func answer() async {
        try? await Task.sleep(for: .seconds(thinkTime))
        guard !Task.isCancelled, thinking else { return }
        written = 0
        hasResult = true
        index = 1
        phase = .done
        // The bubble has opened by the time the first letters land.
        try? await Task.sleep(for: .seconds(Metrics.writeDelay))
        while !Task.isCancelled, written < rewritten.count {
            written += Metrics.writeStep
            try? await Task.sleep(for: .seconds(Metrics.writeTick))
        }
    }

    private func show(_ slot: Int, byUser: Bool) {
        guard hasResult, !thinking, slot != index, (0...1).contains(slot) else { return }
        index = slot
        if byUser { steps += 1 }
    }

    /// Back to the draft alone, with an empty field.
    private func reset() {
        phase = .idle
        hasResult = false
        index = 0
        chosen = nil
        instruction = nil
        drifted = false
        requests += 1
        if !value.isEmpty { write("") }
    }

    /// Any touch takes the sheet away from the script.
    private func interrupt() {
        guard playing else { return }
        playing = false
        heldChip = nil
        heldCancel = false
        resumes += 1
    }

    /// One loop after another, every step at its own moment after the loop began.
    private func play() async {
        while !Task.isCancelled {
            let start = ContinuousClock.now
            func reach(_ seconds: Double) async -> Bool {
                try? await Task.sleep(until: start + .seconds(seconds), clock: .continuous)
                return !Task.isCancelled
            }

            guard await reach(Metrics.driftAt) else { return }
            drifted = true
            guard await reach(Metrics.pressAt) else { return }
            heldChip = tones.count - 1
            guard await reach(Metrics.chooseAt) else { return }
            heldChip = nil
            choose(tones.count - 1)
            guard await reach(Metrics.swipeAt) else { return }
            withAnimation(.easeOut(duration: Metrics.swipeTime)) { pull = Metrics.swipeReach }
            guard await reach(Metrics.swipeAt + Metrics.swipeTime) else { return }
            show(0, byUser: false)
            withAnimation(Metrics.pullSpring) { pull = 0 }
            guard await reach(Metrics.cancelPressAt) else { return }
            heldCancel = true
            guard await reach(Metrics.cancelAt) else { return }
            heldCancel = false
            reset()
            guard await reach(Metrics.loop) else { return }
        }
    }
}

private enum Metrics {
    static let width = 354.0
    static let height = 332.0
    static let radius = 30.0
    /// Inset of the chips and of the field from the panel's edge.
    static let inset = 14.0
    static let barHeight = 52.0
    static let stageHeight = 176.0
    static let bubbleMax = 258.0
    static let bubblePadX = 15.0
    static let bubblePadY = 10.0
    static let bubbleRadius = 19.0
    static let chipHeight = 32.0
    static let chipGap = 8.0
    static let fieldHeight = 44.0
    static let fieldRadius = 14.0
    static let sendSize = 30.0

    static let blue = Color(hex: 0x2d7ff9)
    static let action = Color(hex: 0x1a73f0)

    /// One pass of the band of light, and of the star's pulse, in seconds.
    static let shimmer = 1.1
    static let band = 80.0
    /// How far the band travels in one pass, centred on the bubble.
    static let sweep = 360.0
    static let writeDelay = 0.2
    /// The rewrite appears this many letters at a time, this often.
    static let writeStep = 2
    static let writeTick = 0.04
    /// Share of a finger's travel the bubble follows, and the travel that turns the page.
    static let pullShare = 0.4
    static let swipeDistance = 40.0

    static let sizeSpring = Animation.interpolatingSpring(mass: 1, stiffness: 240, damping: 21)
    static let pullSpring = Animation.interpolatingSpring(mass: 1, stiffness: 300, damping: 24)
    static let dotSpring = Animation.interpolatingSpring(mass: 1, stiffness: 380, damping: 20)

    // autoPlay loop, in seconds from its start.
    static let driftAt = 0.8
    /// The chip row takes this long to reach its end, and this long to come back.
    static let drift = 1.4
    static let driftBack = 0.6
    static let pressAt = 2.65
    static let chooseAt = 2.85
    static let swipeAt = 6.6
    static let swipeTime = 0.25
    /// How far the script pulls the bubble before it lets go.
    static let swipeReach = 38.0
    static let cancelPressAt = 7.5
    static let cancelAt = 7.7
    static let loop = 9.0
    /// After the sheet is left at rest, the loop waits this long before it starts again.
    static let resume = 3.0
}

/// Outlines, the same strings in the three stacks: 20 pt boxes, and 14 pt boxes for the chips.
private enum Glyph {
    static let spark = "M 8.4 3.6 C 8.4 9.06 10.74 11.4 16.2 11.4 C 10.74 11.4 8.4 13.74 8.4 19.2 C 8.4 13.74 6.06 11.4 0.6 11.4 C 6.06 11.4 8.4 9.06 8.4 3.6 Z M 16.4 0.8 C 16.4 3.18 17.42 4.2 19.8 4.2 C 17.42 4.2 16.4 5.22 16.4 7.6 C 16.4 5.22 15.38 4.2 13 4.2 C 15.38 4.2 16.4 3.18 16.4 0.8 Z"
    static let star = "M 10 1.4 C 10 7.42 12.58 10 18.6 10 C 12.58 10 10 12.58 10 18.6 C 10 12.58 7.42 10 1.4 10 C 7.42 10 10 7.42 10 1.4 Z"
    static let arrow = "M 10 15.2 L 10 5 M 5.8 9.2 L 10 5 L 14.2 9.2"
    static let again = "M 4.01 8.4 C 4.69 5.88 6.86 4.05 9.46 3.82 C 12.06 3.6 14.52 5.02 15.62 7.38 M 12.95 6.02 L 15.62 7.38 M 16.29 4.46 L 15.62 7.38 M 15.99 11.6 C 15.31 14.12 13.14 15.95 10.54 16.18 C 7.94 16.4 5.48 14.98 4.38 12.62 M 7.05 13.98 L 4.38 12.62 M 3.71 15.54 L 4.38 12.62"

    static func tone(_ icon: ToneRewriteComposerIcon) -> String {
        switch icon {
        case .pencil: "M 2.6 11.4 L 2.6 9.2 L 9.5 2.3 L 11.7 4.5 L 4.8 11.4 Z M 8 3.8 L 10.2 6"
        case .check: "M 2.4 7.5 L 5.4 10.5 L 11.6 3.7"
        case .smile: "M 12.4 7 C 12.4 9.98 9.98 12.4 7 12.4 C 4.02 12.4 1.6 9.98 1.6 7 C 1.6 4.02 4.02 1.6 7 1.6 C 9.98 1.6 12.4 4.02 12.4 7 Z M 5.2 5.9 L 5.21 5.9 M 8.8 5.9 L 8.81 5.9 M 9.63 8.33 C 9.15 9.35 8.13 10 7 10 C 5.87 10 4.85 9.35 4.37 8.33"
        case .shrink: "M 1.2 7 L 5.8 7 M 3.6 4.6 L 6 7 L 3.6 9.4 M 12.8 7 L 8.2 7 M 10.4 4.6 L 8 7 L 10.4 9.4"
        case .heart: "M 7 11.9 C 7 11.9 1.9 8.9 1.9 5.4 C 1.9 3.7 3.2 2.5 4.7 2.5 C 5.7 2.5 6.5 3 7 3.8 C 7.5 3 8.3 2.5 9.3 2.5 C 10.8 2.5 12.1 3.7 12.1 5.4 C 12.1 8.9 7 11.9 7 11.9 Z"
        }
    }
}

/// Builds a path from "M x y L x y C x1 y1 x2 y2 x y Z": absolute commands, separated by spaces.
private func toneRewriteComposerPath(_ d: String) -> Path {
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

private struct ToneRewriteComposerIconView: View {
    let d: String
    let color: Color
    var lineWidth = 1.5

    var body: some View {
        toneRewriteComposerPath(d)
            .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
            .frame(width: 20, height: 20)
    }
}

/// A chip or a round button dips under a finger, or under the script's.
private struct ToneRewriteComposerPress: ButtonStyle {
    let held: Bool

    func makeBody(configuration: Configuration) -> some View {
        let down = configuration.isPressed || held
        configuration.label
            .scaleEffect(down ? 0.94 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: down)
    }
}

/// A text button dims under a finger, or under the script's.
private struct ToneRewriteComposerFade: ButtonStyle {
    let held: Bool

    func makeBody(configuration: Configuration) -> some View {
        let down = configuration.isPressed || held
        configuration.label
            .opacity(down ? 0.35 : 1)
            .animation(.easeOut(duration: 0.13), value: down)
    }
}
