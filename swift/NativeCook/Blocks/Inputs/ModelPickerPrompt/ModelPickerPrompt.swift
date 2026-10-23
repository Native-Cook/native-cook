import SwiftUI
import UIKit

struct ModelPickerPromptModel: Equatable {
    var name: String
    /// One line under the name in the menu.
    var detail: String
}

/// Dark prompt box of an assistant, glowing on a warm vignette under its name. The box holds the
/// text, a plus, a model picker and one button that changes with the state: a waveform while the
/// field is empty, a coral send button once there is text, and a stop ring while the answer is
/// "generating" (the name lifts and three lines of a faux answer shimmer in under it). The model
/// label opens a small menu of models; choosing one closes it and rolls the label.
struct ModelPickerPrompt: View {
    /// The field's value when the caller owns it; left nil, the component keeps its own.
    var text: Binding<String>? = nil
    /// Types `script`, sends it, waits for the answer and changes the model, by itself. Never opens the keyboard.
    var autoPlay = false
    var name = "Atlas"
    var placeholder = "How can I help you today?"
    /// What autoPlay types.
    var script = "Plan three days in Lisbon in May"
    var models = [
        ModelPickerPromptModel(name: "Swift", detail: "Fastest for everyday tasks"),
        ModelPickerPromptModel(name: "Balanced", detail: "Smart and quick for most work"),
        ModelPickerPromptModel(name: "Deep", detail: "Thinks longer on hard problems"),
    ]
    /// Index of the model selected at first.
    var initialModel = 0
    /// Up to four shortcuts under the box.
    var chips = ["Create", "Write", "Learn", "Code"]
    /// How long the stop ring turns after a send, unless `onStop` ends it.
    var generateFor = 2.4
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the trimmed message when the send button or the keyboard's send key is used.
    var onSend: ((String) -> Void)? = nil
    /// Called when the stop ring is tapped while generating.
    var onStop: (() -> Void)? = nil
    /// Called with the index of the model chosen in the menu.
    var onModelChange: ((Int) -> Void)? = nil
    var onChip: ((String) -> Void)? = nil
    var onAttach: (() -> Void)? = nil
    var onVoice: (() -> Void)? = nil

    /// What autoPlay is pressing.
    private enum Press {
        case send, model
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    /// What autoPlay has typed so far; the caller's value is never touched by the script.
    @State private var typed = ""
    /// True while autoPlay owns the component.
    @State private var playing = false
    @State private var caretOn = false
    @State private var lastKey = Date.distantPast
    @State private var entered = false
    /// How many chips have flown in.
    @State private var chipsIn = 0
    /// The sent text is fading out of the field.
    @State private var sending = false
    @State private var generating = false
    @State private var generatingSince = Date.distantPast
    @State private var menuOpen = false
    @State private var model: Int? = nil
    /// The menu row autoPlay lights before choosing it.
    @State private var flashed: Int? = nil
    @State private var pressed: Press? = nil
    @State private var run = 0
    @State private var generations = 0
    @State private var resume = 0
    @State private var impacts = 0
    @State private var selections = 0

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
    private var selected: Int { min(max(model ?? initialModel, 0), max(models.count - 1, 0)) }

    /// The script's text broken into lines by hand, so the drawn caret can follow the last one.
    private var typedLines: [String] {
        Array(modelPickerPromptWrap(typed, width: Metrics.textWidth - Metrics.caretRoom).suffix(Metrics.maxLines))
    }

    private func spring(_ stiffness: Double, _ damping: Double) -> Animation {
        reduceMotion ? .easeOut(duration: 0.2) : .interpolatingSpring(mass: 1, stiffness: stiffness, damping: damping)
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            // Larger than the component: the glow fades out on the screen around it, not at its edges.
            glow
                .frame(width: Metrics.glowRadius * 2, height: Metrics.glowRadius * 2)
                .offset(x: Metrics.width / 2 - Metrics.glowRadius, y: Metrics.glowY - Metrics.glowRadius)

            title
                .frame(width: Metrics.width, height: Metrics.titleHeight)
                .scaleEffect(generating && !reduceMotion ? Metrics.titleLiftScale : 1)
                .offset(y: Metrics.titleY - Metrics.titleHeight / 2 - (generating ? Metrics.titleLift : 0))
                .animation(spring(Springs.liftStiffness, Springs.liftDamping), value: generating)

            answer.offset(x: Metrics.boxX + Metrics.padX, y: Metrics.answerTop)

            box
                .opacity(entered ? 1 : 0)
                .blur(radius: entered ? 0 : Metrics.enterBlur)
                .scaleEffect(entered ? 1 : Metrics.enterScale)
                .offset(x: Metrics.boxX, y: Metrics.boxTop)

            chipRow
                .frame(width: Metrics.width, height: Metrics.chipHeight)
                .offset(y: Metrics.boxTop + Metrics.boxHeight + Metrics.chipGap)

            if menuOpen {
                // A touch outside the menu closes it.
                Color.clear.contentShape(Rectangle()).onTapGesture { menuOpen = false }
            }

            menu.offset(
                x: Metrics.boxX + Metrics.boxWidth - Metrics.menuWidth,
                y: Metrics.boxTop + Metrics.boxHeight + Metrics.menuGap
            )
        }
        .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        // While autoPlay runs, a touch stops it; on the text it also hands the field to the keyboard.
        .overlay {
            if playing {
                Color.clear.contentShape(Rectangle()).gesture(
                    SpatialTapGesture().onEnded { tap in
                        takeOver()
                        let field = CGRect(x: Metrics.boxX, y: Metrics.boxTop, width: Metrics.boxWidth, height: Metrics.boxHeight - Metrics.rowHeight)
                        if field.contains(tap.location) { focused = true }
                        resume += 1
                    }
                )
            }
        }
        .sensoryFeedback(.impact(weight: .light), trigger: impacts)
        .sensoryFeedback(.selection, trigger: selections)
        .onAppear { restart() }
        .onChange(of: autoPlay) { restart() }
        .onChange(of: focused) { resume += 1 }
        .onChange(of: menuOpen) { resume += 1 }
        .task { await enter() }
        .task(id: run) { await play() }
        .task(id: generations) { await generate() }
        .task(id: resume) { await resumeWhenIdle() }
    }

    // MARK: Backdrop and title

    private var glow: some View {
        ZStack {
            RadialGradient(
                colors: [Palette.ember.opacity(0.34), Palette.ember.opacity(0.12), Palette.ember.opacity(0)],
                center: .center, startRadius: 0, endRadius: Metrics.glowRadius
            )
            RadialGradient(
                colors: [Palette.ember.opacity(0.3), Palette.ember.opacity(0)],
                center: .center, startRadius: 0, endRadius: Metrics.glowRadius
            )
            .opacity(generating ? 1 : 0)
            .animation(.easeInOut(duration: 0.6), value: generating)
        }
        .allowsHitTesting(false)
    }

    private var title: some View {
        HStack(spacing: 9) {
            ModelPickerPromptMark()
                .fill(Palette.coral)
                .frame(width: Metrics.markSize, height: Metrics.markSize)
                .shadow(color: Palette.coral.opacity(0.7), radius: 9)
            Text(name)
                .font(.system(size: 38, weight: .medium, design: .serif))
                .foregroundStyle(.white)
                .lineLimit(1)
                .shadow(color: .white.opacity(0.35), radius: 10)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(name)
    }

    /// Three lines of a faux answer: each grows in from the left, and a band of light keeps crossing them.
    private var answer: some View {
        TimelineView(.animation(paused: !generating || reduceMotion)) { timeline in
            let elapsed = max(0, timeline.date.timeIntervalSince(generatingSince))
            let sweep = reduceMotion ? 0.5 : (elapsed / Script.shimmerSweep).truncatingRemainder(dividingBy: 1)
            let centre = -0.4 + 1.8 * sweep
            VStack(alignment: .leading, spacing: Metrics.answerGap) {
                ForEach(Metrics.answerWidths.indices, id: \.self) { index in
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [Palette.answer, Palette.answerLight, Palette.answer],
                                startPoint: UnitPoint(x: centre - 0.35, y: 0.5), endPoint: UnitPoint(x: centre + 0.35, y: 0.5)
                            )
                        )
                        .frame(width: Metrics.answerWidths[index], height: Metrics.answerLine)
                        .scaleEffect(x: generating || reduceMotion ? 1 : 0.2, anchor: .leading)
                        .opacity(generating ? 1 : 0)
                        .animation(
                            generating
                                ? .easeOut(duration: Script.answerGrow).delay(Script.answerAt + Double(index) * Script.answerStagger)
                                : .easeOut(duration: Script.fade),
                            value: generating
                        )
                }
            }
        }
        .allowsHitTesting(false)
    }

    // MARK: Box

    private var box: some View {
        let shape = RoundedRectangle(cornerRadius: Metrics.boxRadius, style: .circular)
        return ZStack(alignment: .topLeading) {
            editor
                .frame(width: Metrics.textWidth, alignment: .topLeading)
                .offset(x: Metrics.padX, y: Metrics.padTop)

            Button {
                onAttach?()
            } label: {
                ZStack {
                    Capsule().frame(width: 15, height: 1.6)
                    Capsule().frame(width: 1.6, height: 15)
                }
                .foregroundStyle(Palette.control)
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
            }
            .buttonStyle(ModelPickerPromptPressStyle())
            .accessibilityLabel("Attach")
            .position(x: Metrics.plusX, y: Metrics.rowY)

            Button {
                menuOpen.toggle()
            } label: {
                modelLabel
            }
            .buttonStyle(ModelPickerPromptPressStyle())
            .accessibilityLabel("Model")
            .accessibilityValue(models.indices.contains(selected) ? models[selected].name : "")
            .scaleEffect(pressed == .model ? Metrics.pressScale : 1, anchor: .trailing)
            .animation(.easeOut(duration: Script.press), value: pressed)
            .frame(width: Metrics.modelSlot, height: Metrics.rowHeight, alignment: .trailing)
            .offset(x: Metrics.modelRight - Metrics.modelSlot, y: Metrics.rowY - Metrics.rowHeight / 2)

            action.position(x: Metrics.actionX, y: Metrics.rowY)
        }
        .frame(width: Metrics.boxWidth, height: Metrics.boxHeight, alignment: .topLeading)
        .background(
            shape.fill(Palette.box)
                .shadow(color: Palette.coral.opacity(0.22), radius: 16)
        )
        .overlay(shape.strokeBorder(Palette.coral.opacity(0.3), lineWidth: 1).allowsHitTesting(false))
    }

    private var editor: some View {
        ZStack(alignment: .topLeading) {
            Text(placeholder)
                .font(.system(size: Metrics.placeholderSize))
                .foregroundStyle(Palette.placeholder)
                .lineLimit(1)
                .frame(height: Metrics.line)
                .opacity(hasText || caretOn ? 0 : 1)
                .animation(.easeOut(duration: Script.fade), value: hasText)
                .allowsHitTesting(false)

            TextField("", text: value, axis: .vertical)
                .lineLimit(1...Metrics.maxLines)
                .lineSpacing(Metrics.leading)
                .tint(Palette.coral)
                .focused($focused)
                .submitLabel(.send)
                .onSubmit(send)
                .padding(.top, Metrics.leading / 2)
                .opacity(playing || sending ? 0 : 1)

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
                .lineLimit(1)
                .fixedSize()
                .opacity(sending ? 0 : 1)
                .animation(.easeOut(duration: Script.fade), value: sending)
                .allowsHitTesting(false)
            }
        }
        .font(Font(Metrics.textFont))
        .foregroundStyle(Palette.ink)
    }

    /// The caret autoPlay draws after the last letter, since the field itself is not focused.
    private var caret: some View {
        TimelineView(.animation(paused: !caretOn)) { timeline in
            RoundedRectangle(cornerRadius: 1, style: .circular)
                .fill(Palette.coral)
                .frame(width: 2, height: Metrics.caretHeight)
                .opacity(caretOn ? modelPickerPromptBlink(idle: timeline.date.timeIntervalSince(lastKey)) : 0)
        }
    }

    /// The model's name rolls upwards when it changes; the chevron turns over while the menu is open.
    private var modelLabel: some View {
        HStack(spacing: 5) {
            ZStack(alignment: .trailing) {
                Text(models.indices.contains(selected) ? models[selected].name : "")
                    .font(.system(size: 13, weight: .medium))
                    .lineLimit(1)
                    .fixedSize()
                    .id(selected)
                    .transition(
                        reduceMotion
                            ? .opacity
                            : .asymmetric(
                                insertion: .offset(y: Metrics.roll).combined(with: .opacity),
                                removal: .offset(y: -Metrics.roll).combined(with: .opacity)
                            )
                    )
            }
            // A fixed slot: the name on its way out stays right-aligned beside the one coming in.
            .frame(width: Metrics.modelName, height: Metrics.rowHeight, alignment: .trailing)
            .clipped()
            ModelPickerPromptChevron()
                .stroke(style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                .frame(width: 8, height: 5)
                .rotationEffect(.degrees(menuOpen ? 180 : 0))
        }
        .foregroundStyle(menuOpen ? Palette.ink : Palette.control)
        .contentShape(Rectangle())
        .animation(.timingCurve(0.4, 0, 0.2, 1, duration: Script.roll), value: selected)
        .animation(.easeOut(duration: Script.fade), value: menuOpen)
    }

    /// One slot, three buttons: the waveform, the send button and the stop ring trade places.
    private var action: some View {
        let mode = generating ? 2 : (hasText ? 1 : 0)
        let swap = spring(Springs.swapStiffness, Springs.swapDamping)
        let hidden = reduceMotion ? 1 : Metrics.swapScale
        return ZStack {
            Button {
                onVoice?()
            } label: {
                ModelPickerPromptWaveform().fill(Palette.control).frame(width: 18, height: 18)
                    .frame(width: 40, height: 40).contentShape(Rectangle())
            }
            .buttonStyle(ModelPickerPromptPressStyle())
            .accessibilityLabel("Dictate")
            .scaleEffect(mode == 0 ? 1 : hidden)
            .opacity(mode == 0 ? 1 : 0)
            .allowsHitTesting(mode == 0)

            Button(action: send) {
                ZStack {
                    RoundedRectangle(cornerRadius: Metrics.sendRadius, style: .circular).fill(Palette.coral)
                    ModelPickerPromptArrow().stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
                .frame(width: Metrics.sendSize, height: Metrics.sendSize)
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
            }
            .buttonStyle(ModelPickerPromptPressStyle())
            .accessibilityLabel("Send")
            .scaleEffect(pressed == .send ? Metrics.pressScale : 1)
            .animation(.easeOut(duration: Script.press), value: pressed)
            .scaleEffect(mode == 1 ? 1 : hidden)
            .opacity(mode == 1 ? 1 : 0)
            .allowsHitTesting(mode == 1)

            Button(action: stop) {
                stopRing.frame(width: 40, height: 40).contentShape(Rectangle())
            }
            .buttonStyle(ModelPickerPromptPressStyle())
            .accessibilityLabel("Stop")
            .scaleEffect(mode == 2 ? 1 : hidden)
            .opacity(mode == 2 ? 1 : 0)
            .allowsHitTesting(mode == 2)
        }
        .animation(swap, value: mode)
    }

    /// A square inside an open ring that turns slowly while the answer is on its way.
    private var stopRing: some View {
        TimelineView(.animation(paused: !generating || reduceMotion)) { timeline in
            let turns = reduceMotion ? 0 : max(0, timeline.date.timeIntervalSince(generatingSince)) / Script.ringTurn
            ZStack {
                Circle()
                    .trim(from: 0, to: Metrics.ringArc)
                    .stroke(Palette.coral, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                    .frame(width: Metrics.ringSize, height: Metrics.ringSize)
                    .rotationEffect(.degrees(turns * 360 - 90))
                RoundedRectangle(cornerRadius: 2.5, style: .circular)
                    .fill(Palette.coral)
                    .frame(width: Metrics.stopSize, height: Metrics.stopSize)
            }
        }
    }

    // MARK: Chips and menu

    private var chipRow: some View {
        HStack(spacing: Metrics.chipSpacing) {
            ForEach(Array(chips.prefix(Metrics.maxChips).enumerated()), id: \.offset) { index, label in
                let arrived = index < chipsIn
                Button {
                    onChip?(label)
                } label: {
                    HStack(spacing: 6) {
                        ModelPickerPromptChipIcon(kind: index)
                            .stroke(Palette.control, style: StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round))
                            .frame(width: 13, height: 13)
                        Text(label)
                            .font(.system(size: 13))
                            .foregroundStyle(Palette.chipText)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 10)
                    .frame(height: Metrics.chipHeight)
                    .background(RoundedRectangle(cornerRadius: Metrics.chipRadius, style: .circular).strokeBorder(Palette.chipBorder, lineWidth: 1))
                    .contentShape(Rectangle())
                }
                .buttonStyle(ModelPickerPromptPressStyle())
                .opacity(arrived ? 1 : 0)
                .offset(x: arrived || reduceMotion ? 0 : Metrics.chipFlyX, y: arrived || reduceMotion ? 0 : Metrics.chipFlyY)
                .animation(spring(Springs.chipStiffness, Springs.chipDamping), value: arrived)
            }
        }
    }

    private var menu: some View {
        let shape = RoundedRectangle(cornerRadius: Metrics.menuRadius, style: .circular)
        return VStack(spacing: 0) {
            ForEach(models.indices, id: \.self) { index in
                Button {
                    choose(index, byHand: true)
                } label: {
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(models[index].name)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Palette.ink)
                            Text(models[index].detail)
                                .font(.system(size: 12))
                                .foregroundStyle(Palette.placeholder)
                        }
                        .lineLimit(1)
                        Spacer(minLength: 0)
                        ModelPickerPromptCheck()
                            .stroke(Palette.coral, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                            .frame(width: 12, height: 10)
                            .opacity(index == selected ? 1 : 0)
                    }
                    .padding(.horizontal, 10)
                    .frame(height: Metrics.menuRow)
                    .contentShape(Rectangle())
                }
                .buttonStyle(ModelPickerPromptRowStyle(lit: flashed == index))
            }
        }
        .padding(Metrics.menuPad)
        .frame(width: Metrics.menuWidth)
        .background(shape.fill(Palette.menu).shadow(color: .black.opacity(0.5), radius: 18, y: 12))
        .overlay(shape.strokeBorder(Palette.menuBorder, lineWidth: 1).allowsHitTesting(false))
        .scaleEffect(menuOpen || reduceMotion ? 1 : Metrics.menuScale, anchor: .topTrailing)
        .opacity(menuOpen ? 1 : 0)
        .animation(menuOpen ? spring(Springs.menuStiffness, Springs.menuDamping) : .easeOut(duration: Script.menuClose), value: menuOpen)
        .allowsHitTesting(menuOpen)
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
        pressed = nil
        flashed = nil
        menuOpen = false
        sending = false
        generating = false
        generations += 1
    }

    /// A touch stops the script and leaves the box at rest.
    private func takeOver() {
        guard playing else { return }
        playing = false
        reset()
        run += 1
    }

    private func send() {
        let message = shown.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !generating, !sending, !message.isEmpty else { return }
        if !playing {
            impacts += 1
            onSend?(message)
        }
        withAnimation(.easeOut(duration: Script.fade)) { sending = true }
        caretOn = false
        generatingSince = .now
        generating = true
        generations += 1
    }

    private func stop() {
        guard generating else { return }
        impacts += 1
        onStop?()
        // Stopped before the sent text had finished fading: it still has to go.
        if sending {
            if playing { typed = "" } else { value.wrappedValue = "" }
            sending = false
        }
        generating = false
        generations += 1
        resume += 1
    }

    /// The sent text fades and is cleared; the answer "generates" for a while, then the box is at rest again.
    private func generate() async {
        guard generating else { return }
        try? await Task.sleep(for: .seconds(Script.fade))
        guard !Task.isCancelled else { return }
        if playing { typed = "" } else { value.wrappedValue = "" }
        sending = false
        try? await Task.sleep(for: .seconds(max(0, generateFor - Script.fade)))
        guard !Task.isCancelled else { return }
        generating = false
        resume += 1
    }

    private func choose(_ index: Int, byHand: Bool) {
        if byHand {
            selections += 1
            if index != selected { onModelChange?(index) }
        }
        model = index
        flashed = nil
        menuOpen = false
    }

    /// The box fades in from a blur while it scales up, then the chips fly in one at a time.
    private func enter() async {
        guard !reduceMotion else {
            entered = true
            chipsIn = Metrics.maxChips
            return
        }
        try? await Task.sleep(for: .seconds(Enter.boxAt))
        withAnimation(.easeOut(duration: Enter.box)) { entered = true }
        try? await Task.sleep(for: .seconds(Enter.chipsAt - Enter.boxAt))
        for count in 1...Metrics.maxChips {
            chipsIn = count
            try? await Task.sleep(for: .seconds(Enter.chipEvery))
        }
    }

    /// The autoPlay loop. Every step waits for its own moment after the loop began, so the period stays `Script.loop`.
    private func play() async {
        guard playing else { return }
        let clock = ContinuousClock()
        let keys = modelPickerPromptRhythm(script)
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
                pressed = .send
                try await clock.sleep(until: start + .seconds(Script.pressAt + Script.press))
                pressed = nil
                send()
                try await clock.sleep(until: start + .seconds(Script.menuAt - Script.press))
                pressed = .model
                try await clock.sleep(until: start + .seconds(Script.menuAt))
                pressed = nil
                menuOpen = true
                // The second model, then back to the first on the next loop: two loops end where they started.
                let target = selected == 0 && models.count > 1 ? 1 : 0
                try await clock.sleep(until: start + .seconds(Script.chooseAt - Script.flash))
                flashed = target
                try await clock.sleep(until: start + .seconds(Script.chooseAt))
                choose(target, byHand: false)
                start += .seconds(Script.loop)
                try await clock.sleep(until: start)
            } catch {
                return
            }
        }
    }

    /// autoPlay comes back once nothing has been touched for a while and the box is at rest.
    private func resumeWhenIdle() async {
        guard autoPlay, !playing, !focused else { return }
        try? await Task.sleep(for: .seconds(Script.resumeAfter))
        guard !Task.isCancelled, !focused, value.wrappedValue.isEmpty, !generating, !menuOpen else { return }
        playing = true
        run += 1
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 500.0
    static let glowY = 236.0
    static let glowRadius = 250.0

    static let titleY = 150.0
    static let titleHeight = 48.0
    static let markSize = 30.0
    /// While generating, the name rises and shrinks to make room for the answer.
    static let titleLift = 42.0
    static let titleLiftScale = 0.86

    static let answerTop = 136.0
    static let answerLine = 10.0
    static let answerGap = 9.0
    static let answerWidths = [262.0, 290.0, 184.0]

    static let boxX = 14.0
    static let boxTop = 196.0
    static let boxWidth = 322.0
    static let boxHeight = 100.0
    static let boxRadius = 18.0
    static let padX = 16.0
    static let padTop = 14.0
    static let textWidth = boxWidth - padX * 2
    static let enterBlur = 12.0
    static let enterScale = 0.92

    static let fontSize = 16.0
    static let placeholderSize = 15.0
    static let line = 21.0
    static let maxLines = 2
    static let caretHeight = 19.0
    /// Width kept free at the end of a line for the drawn caret.
    static let caretRoom = 3.0
    static let uiFont: UIFont = {
        let base = UIFont.systemFont(ofSize: fontSize)
        return base.fontDescriptor.withDesign(.serif).map { UIFont(descriptor: $0, size: fontSize) } ?? base
    }()
    static let textFont = uiFont as CTFont
    /// Extra space between lines that brings the font's own line height up to `line`.
    static let leading = max(0, line - uiFont.lineHeight)

    /// Centre line and height of the row of controls along the bottom of the box.
    static let rowY = 77.0
    static let rowHeight = 30.0
    static let plusX = 24.0
    static let actionX = 299.0
    static let modelRight = 276.0
    static let modelSlot = 150.0
    /// Room for the longest model name in the label.
    static let modelName = 110.0
    static let roll = 9.0
    static let sendSize = 30.0
    static let sendRadius = 9.0
    static let ringSize = 26.0
    static let ringArc = 0.78
    static let stopSize = 9.0
    static let swapScale = 0.4
    static let pressScale = 0.88

    static let maxChips = 4
    static let chipGap = 14.0
    static let chipHeight = 30.0
    static let chipRadius = 9.0
    static let chipSpacing = 6.0
    static let chipFlyX = 44.0
    static let chipFlyY = 30.0

    static let menuGap = 6.0
    static let menuWidth = 262.0
    static let menuRow = 50.0
    static let menuPad = 6.0
    static let menuRadius = 14.0
    static let menuScale = 0.9

    static func naturalWidth(of text: String) -> Double {
        (text as NSString).size(withAttributes: [.font: uiFont]).width
    }
}

private enum Springs {
    // The name rising above the answer and coming back.
    static let liftStiffness = 220.0
    static let liftDamping = 22.0
    // Waveform, send button and stop ring trading places.
    static let swapStiffness = 420.0
    static let swapDamping = 24.0
    // A chip landing in the row, with a little overshoot.
    static let chipStiffness = 300.0
    static let chipDamping = 20.0
    static let menuStiffness = 420.0
    static let menuDamping = 28.0
}

/// The entrance, in seconds from the first appearance.
private enum Enter {
    static let boxAt = 0.1
    static let box = 0.5
    static let chipsAt = 0.3
    static let chipEvery = 0.12
}

/// The autoPlay loop, in seconds from its start.
private enum Script {
    static let caretAt = 0.5
    static let typeAt = 0.8
    /// The whole script is typed in this time; a short one is typed no slower than `slowestKey` per letter.
    static let typeSpan = 1.5
    static let slowestKey = 0.06
    static let pressAt = 2.75
    static let press = 0.12
    static let menuAt = 5.9
    static let flash = 0.16
    static let chooseAt = 7.0
    static let loop = 8.6

    static let fade = 0.2
    static let roll = 0.3
    static let menuClose = 0.16
    static let answerAt = 0.15
    static let answerStagger = 0.15
    static let answerGrow = 0.35
    static let shimmerSweep = 1.2
    /// Seconds per turn of the stop ring.
    static let ringTurn = 1.6
    /// After the last touch, autoPlay waits this long.
    static let resumeAfter = 3.0
}

private enum Palette {
    static let coral = Color(hex: 0xd97757)
    static let ember = Color(hex: 0xc6613f)
    static let box = Color(hex: 0x2f2d2b)
    static let ink = Color(hex: 0xf3efea)
    static let placeholder = Color(hex: 0x8f8b86)
    static let control = Color(hex: 0xa8a39d)
    static let chipText = Color(hex: 0xcfcac4)
    static let chipBorder = Color(hex: 0x55514d)
    static let menu = Color(hex: 0x262422)
    static let menuBorder = Color(hex: 0x3f3b38)
    static let answer = Color.white.opacity(0.13)
    static let answerLight = Color.white.opacity(0.42)
}

/// Seconds after typing starts at which each letter of `script` lands: uneven like a person's typing
/// (a beat after a space, a pause after punctuation) but the same every time.
private func modelPickerPromptRhythm(_ script: String) -> [Double] {
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

/// Solid while keys are landing; after half a second without one it blinks like the system caret.
private func modelPickerPromptBlink(idle: Double) -> Double {
    guard idle > 0.5 else { return 1 }
    let t = (idle - 0.5).truncatingRemainder(dividingBy: 1)
    if t < 0.12 { return 1 - t / 0.12 }
    if t < 0.5 { return 0 }
    if t < 0.62 { return (t - 0.5) / 0.12 }
    return 1
}

/// Breaks `text` into lines no wider than `width`, word by word; a space typed at the end of a line hangs past it.
private func modelPickerPromptWrap(_ text: String, width: Double) -> [String] {
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

private struct ModelPickerPromptPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? Metrics.pressScale : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

/// A menu row lights up while it is pressed, or when autoPlay is about to choose it.
private struct ModelPickerPromptRowStyle: ButtonStyle {
    let lit: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 9, style: .circular)
                    .fill(.white.opacity(configuration.isPressed || lit ? 0.08 : 0))
            )
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed || lit)
    }
}

/// The assistant's mark: a four-pointed star with a smaller one turned 45° behind it, like a compass rose.
private struct ModelPickerPromptMark: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        star(&path, in: rect, radius: 0.5, turn: 0, pinch: 0.1)
        star(&path, in: rect, radius: 0.3, turn: .pi / 4, pinch: 0.16)
        return path
    }

    /// `pinch` pulls the sides towards the centre: 0 is a needle-thin star, 0.5 a diamond.
    private func star(_ path: inout Path, in rect: CGRect, radius: Double, turn: Double, pinch: Double) {
        let r = radius * min(rect.width, rect.height)
        func point(_ angle: Double, _ distance: Double) -> CGPoint {
            CGPoint(x: rect.midX + cos(angle) * distance, y: rect.midY + sin(angle) * distance)
        }
        path.move(to: point(turn, r))
        for step in 1...4 {
            let angle = turn + Double(step) * .pi / 2
            path.addQuadCurve(to: point(angle, r), control: point(angle - .pi / 4, r * pinch * 2.0.squareRoot()))
        }
        path.closeSubpath()
    }
}

/// Five bars of a voice waveform in an 18 × 18 box.
private struct ModelPickerPromptWaveform: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        for (index, height) in [6.0, 12, 16, 9, 5].enumerated() {
            let bar = CGRect(x: 1.1 + Double(index) * 3.5, y: 9 - height / 2, width: 1.8, height: height)
            path.addRoundedRect(in: bar, cornerSize: CGSize(width: 0.9, height: 0.9))
        }
        return path
    }
}

/// Up arrow in the 30 × 30 send button.
private struct ModelPickerPromptArrow: Shape {
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

/// Chevron pointing down in an 8 × 5 box.
private struct ModelPickerPromptChevron: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0.5, y: 0.75))
        path.addLine(to: CGPoint(x: 4, y: 4.25))
        path.addLine(to: CGPoint(x: 7.5, y: 0.75))
        return path
    }
}

/// Check mark in a 12 × 10 box.
private struct ModelPickerPromptCheck: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 1, y: 5.4))
        path.addLine(to: CGPoint(x: 4.4, y: 8.8))
        path.addLine(to: CGPoint(x: 11, y: 1.2))
        return path
    }
}

/// The chips' outline icons in a 13 × 13 box: a sparkle, a pencil, an open book and code brackets.
private struct ModelPickerPromptChipIcon: Shape {
    let kind: Int

    func path(in rect: CGRect) -> Path {
        var path = Path()
        switch kind % 4 {
        case 0:
            path.move(to: CGPoint(x: 6.5, y: 1))
            path.addQuadCurve(to: CGPoint(x: 12, y: 6.5), control: CGPoint(x: 7.2, y: 5.8))
            path.addQuadCurve(to: CGPoint(x: 6.5, y: 12), control: CGPoint(x: 7.2, y: 7.2))
            path.addQuadCurve(to: CGPoint(x: 1, y: 6.5), control: CGPoint(x: 5.8, y: 7.2))
            path.addQuadCurve(to: CGPoint(x: 6.5, y: 1), control: CGPoint(x: 5.8, y: 5.8))
            path.closeSubpath()
        case 1:
            path.move(to: CGPoint(x: 1.8, y: 11.2))
            path.addLine(to: CGPoint(x: 2.5, y: 8.3))
            path.addLine(to: CGPoint(x: 8.9, y: 1.9))
            path.addLine(to: CGPoint(x: 11.1, y: 4.1))
            path.addLine(to: CGPoint(x: 4.7, y: 10.5))
            path.closeSubpath()
            path.move(to: CGPoint(x: 7.4, y: 3.4))
            path.addLine(to: CGPoint(x: 9.6, y: 5.6))
        case 2:
            path.move(to: CGPoint(x: 6.5, y: 3.4))
            path.addQuadCurve(to: CGPoint(x: 1.5, y: 2.6), control: CGPoint(x: 4.4, y: 2.1))
            path.addLine(to: CGPoint(x: 1.5, y: 10.2))
            path.addQuadCurve(to: CGPoint(x: 6.5, y: 11), control: CGPoint(x: 4.4, y: 9.7))
            path.addQuadCurve(to: CGPoint(x: 11.5, y: 10.2), control: CGPoint(x: 8.6, y: 9.7))
            path.addLine(to: CGPoint(x: 11.5, y: 2.6))
            path.addQuadCurve(to: CGPoint(x: 6.5, y: 3.4), control: CGPoint(x: 8.6, y: 2.1))
            path.addLine(to: CGPoint(x: 6.5, y: 11))
        default:
            path.move(to: CGPoint(x: 4.4, y: 3.2))
            path.addLine(to: CGPoint(x: 1.2, y: 6.5))
            path.addLine(to: CGPoint(x: 4.4, y: 9.8))
            path.move(to: CGPoint(x: 8.6, y: 3.2))
            path.addLine(to: CGPoint(x: 11.8, y: 6.5))
            path.addLine(to: CGPoint(x: 8.6, y: 9.8))
        }
        return path
    }
}

#Preview {
    ZStack {
        Color(hex: 0x0e0d0c).ignoresSafeArea()
        ModelPickerPrompt(autoPlay: true)
    }
}
