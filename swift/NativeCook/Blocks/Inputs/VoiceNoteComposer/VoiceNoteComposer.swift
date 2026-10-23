import SwiftUI

/// Frosted chat composer on a sunset panel that records a voice note in place. The waveform icon
/// starts a take: bars scroll in from the right under a running timer. Stop turns it into a
/// preview with a play button and a scrubber; send lifts it out of the composer into a bubble that
/// shows "Generating transcript…" under a shimmer, then unfolds the transcript.
///
/// Nothing is recorded: the waveform is a fixed pattern drawn as if it were live, and
/// `onRecordStart` / `onRecordEnd` are where a real recorder is connected.
struct VoiceNoteComposer: View {
    /// Controlled text; leave nil and the field keeps its own.
    var text: Binding<String>? = nil
    /// Records, previews and sends a voice note in a loop, without opening the keyboard.
    var autoPlay = false
    var placeholder = "What’s on your mind"
    /// The message already in the chat, above the composer.
    var incoming = "How was the flight?"
    var generatingLabel = "Generating transcript…"
    /// What the sent bubble reads once the transcript has arrived.
    var transcript = "Just landed in Lisbon. The light here is unreal!"
    /// Called with the typed text when the send button or the keyboard's send key is pressed.
    var onSend: ((String) -> Void)? = nil
    var onRecordStart: (() -> Void)? = nil
    /// Called with the length of the take in seconds when stop is pressed.
    var onRecordEnd: ((Double) -> Void)? = nil
    /// Called with the length of the take in seconds when the preview is sent.
    var onSendVoice: ((Double) -> Void)? = nil
    var onDiscard: (() -> Void)? = nil
    var onAttach: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    @State private var phase = Phase.idle
    /// True while the preview, or the sent bubble, plays.
    @State private var previewPlaying = false
    @State private var bubblePlaying = false
    @State private var transcribed = false
    /// True between the send and the transcript: the label shimmers.
    @State private var generating = false
    // 0…1 each: how much of the take, of the preview and of the typed text's send arrow shows.
    @State private var rec = Tween(0)
    @State private var pre = Tween(0)
    @State private var arm = Tween(0)
    /// 0…1 with an overshoot: the preview on its way from the composer to its place as a bubble.
    @State private var lift = Tween(0)
    @State private var unfold = Tween(0)
    /// 0…1: the bubble fading away.
    @State private var gone = Tween(1)
    /// True from a send until the next take: the preview rides up with the bubble as it fades.
    @State private var carrying = false
    @State private var motion = Motion()
    /// The button the autoPlay script is pressing.
    @State private var scriptPress: Control? = nil
    /// True while the autoPlay script owns the composer.
    @State private var playing = false
    @State private var quiet = true
    @State private var runToken = 0
    @State private var resumeToken = 0
    @State private var sendToken = 0
    @State private var previewToken = 0
    @State private var bubbleToken = 0
    @State private var quietToken = 0

    private enum Phase { case idle, recording, preview }
    private enum Control { case main, play }

    /// Moments the drawing is timed from. A class, so writing them never rebuilds the view.
    private final class Motion {
        var epoch = Date.now
        var recordAt = Date.distantPast
        var stopAt = Date.distantPast
        var previewAt = Date.distantPast
        var bubbleAt = Date.distantPast
        var sentAt = Date.distantPast
        /// Seconds of the take in the composer, and of the one in the bubble.
        var take = 1.0
        var sent = 1.0
    }

    private var controlled: Bool { text != nil }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion }
    private var current: String { text?.wrappedValue ?? draft }
    private var hasText: Bool { !current.isEmpty }
    private var active: Bool { playing || phase != .idle || generating || bubblePlaying || !quiet }

    private var field: Binding<String> {
        Binding(get: { current }, set: { write($0) })
    }

    var body: some View {
        TimelineView(.animation(paused: !active)) { timeline in
            let now = timeline.date
            let frame = Frame(
                rec: rec.value(at: now), pre: pre.value(at: now), arm: arm.value(at: now),
                lift: lift.value(at: now), unfold: unfold.value(at: now), gone: gone.value(at: now),
                carrying: carrying, still: reduceMotion
            )
            ZStack(alignment: .topLeading) {
                Canvas { context, _ in draw(context, frame: frame, at: now) }
                    .allowsHitTesting(false)
                Text(incoming)
                    .font(.system(size: 15))
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 14)
                    .frame(height: Metrics.incomingHeight)
                    .offset(x: Metrics.pad, y: Metrics.incomingY)
                bubbleContents(frame, at: now)
                composer(frame, at: now)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular))
        }
        .lineLimit(1)
        .onChange(of: hasText) { _, now in
            arm.retarget(now ? 1 : 0, duration: 0.15, curve: .easeOut)
            wake()
        }
        .onChange(of: focused) { _, now in
            // A tap on the field takes the composer over from the script and starts from rest.
            if now && playing { reset() }
            touch()
        }
        .onChange(of: auto, initial: true) { _, now in
            playing = now
            runToken += 1
        }
        .task(id: runToken) { await play() }
        .task(id: resumeToken) { await resume() }
        .task(id: sendToken) {
            guard sendToken > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.generateSeconds))
            guard !Task.isCancelled else { return }
            generating = false
            transcribed = true
            unfold.retarget(1, duration: 0.45, curve: softBack)
            wake()
            // A transcript that nobody touches is cleared like any other pause.
            if !playing { resumeToken += 1 }
        }
        .task(id: previewToken) {
            guard previewPlaying else { return }
            try? await Task.sleep(for: .seconds(motion.take))
            if !Task.isCancelled { previewPlaying = false }
        }
        .task(id: bubbleToken) {
            guard bubblePlaying else { return }
            try? await Task.sleep(for: .seconds(motion.sent))
            if !Task.isCancelled { bubblePlaying = false }
        }
        .task(id: quietToken) {
            try? await Task.sleep(for: .seconds(Metrics.settleSeconds))
            if !Task.isCancelled { quiet = true }
        }
    }

    // MARK: Parts

    private func composer(_ frame: Frame, at now: Date) -> some View {
        let idle = (1 - frame.rec) * (1 - frame.pre)
        let elapsed = now.timeIntervalSince(motion.recordAt)
        return ZStack(alignment: .topLeading) {
            Button {
                onAttach?()
            } label: {
                strokeIcon(plusPath, width: 1.8)
                    .frame(width: Metrics.plus, height: Metrics.plus)
                    .contentShape(Circle())
            }
            .buttonStyle(Press(held: false))
            .accessibilityLabel("Attach")
            .offset(x: Metrics.pad, y: Metrics.rowY + 2)

            ZStack(alignment: .leading) {
                Text(placeholder)
                    .foregroundStyle(Palette.mute)
                    .opacity(hasText ? 0 : 1)
                TextField("", text: field)
                    .focused($focused)
                    .foregroundStyle(Palette.ink)
                    .tint(Palette.ink)
                    .submitLabel(.send)
                    .onSubmit { sendText() }
                    .accessibilityLabel(placeholder)
                if playing && phase == .idle {
                    // The script does not focus the field, so it draws the caret a focused one shows.
                    Capsule()
                        .fill(Palette.ink)
                        .frame(width: 1.5, height: 20)
                        .offset(x: -2)
                        .opacity(now.timeIntervalSince(motion.epoch).truncatingRemainder(dividingBy: 1) < 0.55 ? 1 : 0)
                }
            }
            .font(.system(size: 15))
            .frame(width: Metrics.pillWidth - 16 - 44, height: Metrics.pillHeight, alignment: .leading)
            .opacity(idle)
            .offset(x: Metrics.pillX + 16, y: Metrics.rowY)
            .allowsHitTesting(phase == .idle)

            Text(clock(phase == .recording ? elapsed : motion.take))
                .font(.system(size: 13, weight: .medium))
                .monospacedDigit()
                .foregroundStyle(Palette.ink)
                .frame(width: 40, height: Metrics.pillHeight, alignment: .trailing)
                .opacity(frame.rec)
                .offset(x: Metrics.pillX + Metrics.timerRight - 40, y: Metrics.rowY)
                .allowsHitTesting(false)

            Text(clock(motion.take))
                .font(.system(size: 13, weight: .medium))
                .monospacedDigit()
                .foregroundStyle(Palette.ink)
                .frame(width: 34, height: Metrics.pillHeight, alignment: .trailing)
                .opacity(frame.pre)
                .offset(x: Metrics.pillX + Metrics.discardX - 2 - 34, y: Metrics.rowY)
                .offset(frame.carry)
                .allowsHitTesting(false)

            Button {
                touch()
                togglePreview()
            } label: {
                playIcon(playing: previewPlaying)
                    .frame(width: 36, height: 36)
                    .contentShape(Circle())
            }
            .buttonStyle(Press(held: scriptPress == .play))
            .opacity(frame.pre)
            .offset(x: Metrics.pillX + 4, y: Metrics.rowY + 4)
            .offset(frame.carry)
            .allowsHitTesting(phase == .preview)
            .accessibilityHidden(phase != .preview)
            .accessibilityLabel(previewPlaying ? "Stop" : "Play")

            Button {
                touch()
                discard()
                onDiscard?()
            } label: {
                strokeIcon(crossPath, width: 1.7, color: Palette.mute)
                    .frame(width: 30, height: 30)
                    .contentShape(Rectangle())
            }
            .buttonStyle(Press(held: false))
            .opacity(frame.pre)
            .offset(x: Metrics.pillX + Metrics.discardX, y: Metrics.rowY + 7)
            .allowsHitTesting(phase == .preview)
            .accessibilityHidden(phase != .preview)
            .accessibilityLabel("Discard")

            mainButton(frame, idle: idle)
        }
    }

    /// The button at the right end of the pill: it starts a take, stops it, or sends what is there.
    private func mainButton(_ frame: Frame, idle: Double) -> some View {
        let wave = idle * (1 - frame.arm)
        let arrow = max(frame.pre, idle * frame.arm)
        return Button {
            touch()
            pressMain(byHand: true)
        } label: {
            ZStack {
                Circle().fill(Palette.well).opacity(max(frame.rec, arrow))
                strokeIcon(wavePath, width: 1.8)
                    .scaleEffect(0.6 + 0.4 * wave)
                    .opacity(wave)
                RoundedRectangle(cornerRadius: 3.5, style: .circular)
                    .fill(Palette.ink)
                    .frame(width: 12, height: 12)
                    .scaleEffect(0.6 + 0.4 * frame.rec)
                    .opacity(frame.rec)
                strokeIcon(arrowPath, width: 1.9)
                    .scaleEffect(0.6 + 0.4 * arrow)
                    .opacity(arrow)
            }
            .frame(width: 36, height: 36)
            .contentShape(Circle())
        }
        .buttonStyle(Press(held: scriptPress == .main))
        .offset(x: Metrics.pillX + Metrics.mainX, y: Metrics.rowY + 4)
        .accessibilityLabel(phase == .recording ? "Stop recording" : phase == .preview || hasText ? "Send" : "Record a voice note")
    }

    /// What sits on the sent bubble: its play button, the label or the transcript, and the length.
    private func bubbleContents(_ frame: Frame, at now: Date) -> some View {
        let rect = frame.bubble
        let sweep = max(now.timeIntervalSince(motion.sentAt), 0).truncatingRemainder(dividingBy: Metrics.shimmerSeconds) / Metrics.shimmerSeconds
        return ZStack(alignment: .topLeading) {
            Button {
                touch()
                toggleBubble()
            } label: {
                ZStack {
                    Circle().fill(Palette.well)
                    playIcon(playing: bubblePlaying)
                }
                .frame(width: 32, height: 32)
                .contentShape(Circle())
            }
            .buttonStyle(Press(held: false))
            .offset(x: 12, y: 12)
            .accessibilityLabel(bubblePlaying ? "Stop" : "Play voice note")

            ZStack(alignment: .leading) {
                Text(generatingLabel)
                    .foregroundStyle(Palette.mute)
                    .frame(width: Metrics.bubbleText, alignment: .leading)
                // The shimmer: two windows, one inside the other, keep crossing the label and
                // show a darker copy of it, so only the letters change.
                ForEach(shimmerWindows.indices, id: \.self) { index in
                    let window = shimmerWindows[index]
                    let x = -Metrics.shimmerWidth + (Metrics.bubbleText + Metrics.shimmerWidth) * sweep + (Metrics.shimmerWidth - window.width) / 2
                    Text(generatingLabel)
                        .foregroundStyle(Palette.ink.opacity(window.ink))
                        .frame(width: Metrics.bubbleText, alignment: .leading)
                        .offset(x: -x)
                        .frame(width: window.width, alignment: .leading)
                        .clipped()
                        .offset(x: x)
                        .opacity(reduceMotion ? 0 : 1)
                }
            }
            .frame(width: Metrics.bubbleText, height: Metrics.textLine, alignment: .leading)
            .clipped()
            .opacity(1 - min(1, frame.unfold * 3))
            .offset(x: 12, y: Metrics.bubbleTextY)

            // The transcript is uncovered from its first line down as the bubble grows.
            Text(transcript)
                .foregroundStyle(Palette.ink)
                .lineLimit(2)
                .lineSpacing(Metrics.textLine - 15.5)
                .frame(width: Metrics.bubbleText, height: Metrics.textLine * 2, alignment: .topLeading)
                .frame(height: Metrics.textLine * (1 + min(max(frame.unfold, 0), 1)), alignment: .top)
                .clipped()
                .opacity(transcribed ? min(max(frame.unfold * 1.5, 0), 1) : 0)
                .offset(x: 12, y: Metrics.bubbleTextY)

            Text(clock(motion.sent))
                .font(.system(size: 12))
                .monospacedDigit()
                .foregroundStyle(Palette.mute)
                .frame(width: 36, height: Metrics.textLine, alignment: .trailing)
                .offset(x: Metrics.bubbleWidth - 12 - 36, y: rect.height - 12 - Metrics.textLine)
        }
        .font(.system(size: 13))
        .frame(width: Metrics.bubbleWidth, height: rect.height, alignment: .topLeading)
        .offset(x: rect.minX, y: rect.minY)
        .opacity(frame.bubbleInk)
        .allowsHitTesting(frame.bubbleInk > 0.5)
        .accessibilityHidden(frame.bubbleInk < 0.5)
    }

    private func strokeIcon(_ path: Path, width: Double, color: Color = Palette.ink) -> some View {
        path
            .stroke(color, style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
            .frame(width: 18, height: 18)
    }

    private func playIcon(playing: Bool) -> some View {
        (playing ? pausePath : trianglePath)
            .fill(Palette.ink)
            .overlay {
                (playing ? pausePath : trianglePath)
                    .stroke(Palette.ink, style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
            }
            .frame(width: 18, height: 18)
    }

    // MARK: Behaviour

    private func write(_ value: String) {
        if let text { text.wrappedValue = value } else { draft = value }
    }

    /// Any touch stops the script; it comes back a few seconds after the last one.
    private func touch() {
        playing = false
        scriptPress = nil
        runToken += 1
        resumeToken += 1
    }

    private func wake() {
        quiet = false
        quietToken += 1
    }

    private func pressMain(byHand: Bool) {
        switch phase {
        case .recording:
            stopRecording()
            if byHand { onRecordEnd?(motion.take) }
        case .preview:
            sendVoice()
            if byHand { onSendVoice?(motion.sent) }
        case .idle:
            if hasText {
                sendText()
            } else {
                startRecording()
                if byHand { onRecordStart?() }
            }
        }
    }

    private func sendText() {
        let sent = current
        guard !sent.isEmpty else { return }
        write("")
        onSend?(sent)
    }

    private func startRecording() {
        phase = .recording
        focused = false
        motion.recordAt = .now
        rec.retarget(1, duration: 0.2, curve: .easeOut)
        wake()
    }

    private func stopRecording() {
        let now = Date.now
        motion.take = max(Metrics.shortestTake, now.timeIntervalSince(motion.recordAt))
        motion.stopAt = now
        phase = .preview
        carrying = false
        rec.retarget(0, at: now, duration: 0.18, curve: .easeOut)
        pre.retarget(1, at: now, duration: 0.22, curve: .easeOut)
        wake()
        // A take that nobody touches is cleared like any other pause.
        if !playing { resumeToken += 1 }
    }

    private func togglePreview() {
        previewPlaying.toggle()
        motion.previewAt = .now
        previewToken += 1
    }

    private func toggleBubble() {
        bubblePlaying.toggle()
        motion.bubbleAt = .now
        bubbleToken += 1
    }

    private func discard() {
        phase = .idle
        previewPlaying = false
        pre.retarget(0, duration: 0.2, curve: .easeOut)
        wake()
    }

    /// The preview leaves the composer and becomes the bubble; the transcript follows by itself.
    private func sendVoice() {
        let now = Date.now
        motion.sent = motion.take
        motion.sentAt = now
        phase = .idle
        previewPlaying = false
        bubblePlaying = false
        transcribed = false
        generating = true
        carrying = true
        pre.retarget(0, at: now, duration: 0.3, curve: .easeOut)
        lift = Tween(0)
        lift.retarget(1, at: now, duration: reduceMotion ? 0.2 : 0.5, curve: reduceMotion ? .easeOut : liftBack)
        unfold = Tween(0)
        gone = Tween(0)
        sendToken += 1
        wake()
    }

    private func fadeBubble() {
        bubblePlaying = false
        generating = false
        gone.retarget(1, duration: 0.35, curve: .easeInOut)
        wake()
    }

    /// Back to the empty composer, whatever it was doing.
    private func reset() {
        phase = .idle
        previewPlaying = false
        rec.retarget(0, duration: 0.2, curve: .easeOut)
        pre.retarget(0, duration: 0.2, curve: .easeOut)
        wake()
    }

    private func play() async {
        guard playing else { return }
        let clock = ContinuousClock()
        var start = clock.now
        while true {
            for step in script {
                guard await wait(until: start + .seconds(step.at), on: clock) else { return }
                scriptPress = step.control
                guard await wait(until: start + .seconds(step.at + Metrics.pressSeconds), on: clock) else { return }
                scriptPress = nil
                if step.control == .play { togglePreview() } else { pressMain(byHand: false) }
            }
            guard await wait(until: start + .seconds(Metrics.fadeAt), on: clock) else { return }
            fadeBubble()
            start += .seconds(Metrics.loop)
            guard await wait(until: start, on: clock) else { return }
        }
    }

    /// autoPlay (seconds): record, stop, play a little, send; the bubble fades before the loop closes.
    private var script: [(at: Double, control: Control)] {
        [(Metrics.recordAt, .main), (Metrics.stopAt, .main), (Metrics.playAt, .play), (Metrics.sendAt, .main)]
    }

    private func wait(until instant: ContinuousClock.Instant, on clock: ContinuousClock) async -> Bool {
        (try? await clock.sleep(until: instant)) != nil
    }

    private func resume() async {
        guard resumeToken > 0, auto else { return }
        try? await Task.sleep(for: .seconds(Metrics.resumeAfter))
        // A take in progress is never cut short.
        guard !Task.isCancelled, !focused, phase != .recording else { return }
        reset()
        fadeBubble()
        draft = ""
        playing = true
        runToken += 1
    }

    // MARK: Drawing

    private func draw(_ context: GraphicsContext, frame: Frame, at now: Date) {
        let panel = CGRect(x: 0, y: 0, width: Metrics.width, height: Metrics.height)
        context.fill(
            Path(panel),
            with: .linearGradient(skyGradient, startPoint: .zero, endPoint: CGPoint(x: 0, y: Metrics.height))
        )
        let frost = GraphicsContext.Shading.color(Palette.frost)
        let ink = GraphicsContext.Shading.color(Palette.ink)
        let faint = GraphicsContext.Shading.color(Palette.ink.opacity(0.32))

        let message = CGRect(x: Metrics.pad, y: Metrics.incomingY, width: Metrics.incomingWidth, height: Metrics.incomingHeight)
        context.fill(Path(roundedRect: message, cornerRadius: 18, style: .circular), with: frost)
        context.fill(Path(ellipseIn: CGRect(x: Metrics.pad, y: Metrics.rowY + 2, width: Metrics.plus, height: Metrics.plus)), with: frost)
        let pill = CGRect(x: Metrics.pillX, y: Metrics.rowY, width: Metrics.pillWidth, height: Metrics.pillHeight)
        context.fill(capsule(pill), with: frost)

        // The take: a bar is born at the right end every sixteenth of a second and travels left.
        if frame.rec > 0 {
            let head = max(now.timeIntervalSince(motion.recordAt), 0) * Metrics.rate
            let newest = Int(head.rounded(.down)) - 1
            var bars = Path()
            if newest >= 0 {
                for k in max(0, newest - Metrics.liveBars)...newest {
                    let age = head - Double(k + 1)
                    let grown = 1 - pow(1 - min(age / (Metrics.growSeconds * Metrics.rate), 1), 3)
                    addBar(&bars, x: pill.minX + Metrics.liveRight - age * Metrics.pitch, y: pill.midY, level: level(k) * (reduceMotion ? 1 : grown))
                }
            }
            var live = context
            live.opacity = frame.rec
            // Bars fade out as they reach the left end of the pill.
            live.fill(
                bars,
                with: .linearGradient(
                    Gradient(colors: [Palette.ink.opacity(0), Palette.ink]),
                    startPoint: CGPoint(x: pill.minX + Metrics.liveLeft, y: 0),
                    endPoint: CGPoint(x: pill.minX + Metrics.liveLeft + Metrics.liveFade, y: 0)
                )
            )
        }

        // The preview: the same take, squeezed between the play button and its length.
        if frame.pre > 0 {
            let settle = now.timeIntervalSince(motion.stopAt)
            let played = previewPlaying ? min(max(now.timeIntervalSince(motion.previewAt) / motion.take, 0), 1) : 0
            var layer = context
            layer.opacity = frame.pre
            drawTake(
                layer, bars: Metrics.previewBars, x: pill.minX + Metrics.previewX + frame.carry.width,
                y: pill.midY + frame.carry.height, seconds: motion.take,
                played: previewPlaying ? played : nil, settle: reduceMotion ? nil : settle, ink: ink, faint: faint
            )
        }

        if frame.bubbleFill > 0 {
            let rect = frame.bubble
            var layer = context
            layer.opacity = frame.bubbleFill
            layer.fill(Path(roundedRect: rect, cornerRadius: frame.bubbleRadius, style: .circular), with: frost)
            var inked = context
            inked.opacity = frame.bubbleInk
            let played = bubblePlaying ? min(max(now.timeIntervalSince(motion.bubbleAt) / motion.sent, 0), 1) : 0
            drawTake(
                inked, bars: Metrics.bubbleBars, x: rect.minX + 52, y: rect.minY + 28, seconds: motion.sent,
                played: bubblePlaying ? played : nil, settle: nil, ink: ink, faint: faint
            )
        }
    }

    /// A finished take as `bars` bars. While it plays, the part behind the playhead keeps its ink.
    private func drawTake(
        _ context: GraphicsContext, bars: Int, x: Double, y: Double, seconds: Double, played: Double?, settle: Double?,
        ink: GraphicsContext.Shading, faint: GraphicsContext.Shading
    ) {
        let samples = max(1, Int(seconds * Metrics.rate))
        var before = Path()
        var after = Path()
        for index in 0..<bars {
            // Stopping sends one ripple along the bars before they stand still.
            let ripple = settle.map { 1 + Metrics.ripple * wobble($0 - Double(index) * 0.008) } ?? 1
            let height = level(index * samples / bars) * ripple
            if let played, Double(index) >= played * Double(bars) {
                addBar(&after, x: x + Double(index) * Metrics.pitch, y: y, level: height)
            } else {
                addBar(&before, x: x + Double(index) * Metrics.pitch, y: y, level: height)
            }
        }
        context.fill(before, with: ink)
        context.fill(after, with: faint)
        if let played {
            let head = x + played * Double(bars) * Metrics.pitch - 1
            context.fill(capsule(CGRect(x: head - 0.75, y: y - 14, width: 1.5, height: 28)), with: ink)
        }
    }

    private func addBar(_ path: inout Path, x: Double, y: Double, level: Double) {
        let height = Metrics.barMin + (Metrics.barMax - Metrics.barMin) * level
        path.addRoundedRect(in: CGRect(x: x, y: y - height / 2, width: 2, height: height), cornerSize: CGSize(width: 1, height: 1), style: .circular)
    }

    private struct Press: ButtonStyle {
        /// True while the autoPlay script holds the button down.
        let held: Bool

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed || held ? 0.86 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed || held)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

/// Where everything stands at one instant.
private struct Frame {
    let rec: Double
    let pre: Double
    let arm: Double
    let lift: Double
    let unfold: Double
    /// The bubble: its box, its corners, the opacity of its fill and of what is written on it.
    let bubble: CGRect
    let bubbleRadius: Double
    let bubbleFill: Double
    let bubbleInk: Double
    /// How far a sent preview has been carried away from the pill.
    let carry: CGSize

    init(rec: Double, pre: Double, arm: Double, lift: Double, unfold: Double, gone: Double, carrying: Bool, still: Bool) {
        self.rec = rec
        self.pre = pre
        self.arm = arm
        self.lift = lift
        self.unfold = unfold
        let height = Metrics.bubbleHeight + Metrics.textLine * unfold
        let place = CGRect(
            x: Metrics.width - Metrics.pad - Metrics.bubbleWidth, y: Metrics.rowY - Metrics.bubbleGap - height,
            width: Metrics.bubbleWidth, height: height
        )
        // It starts as the pill itself and lands a little past its place; a fading one drifts up.
        let t = still ? 1 : lift
        let rise = still ? 0 : -8 * gone
        bubble = CGRect(
            x: Metrics.pillX + (place.minX - Metrics.pillX) * t,
            y: Metrics.rowY + (place.minY - Metrics.rowY) * t + rise,
            width: Metrics.pillWidth + (place.width - Metrics.pillWidth) * t,
            height: Metrics.pillHeight + (place.height - Metrics.pillHeight) * t
        )
        bubbleRadius = Metrics.pillHeight / 2 + (18 - Metrics.pillHeight / 2) * min(max(t, 0), 1)
        bubbleFill = min(1, max(lift, 0) * 6) * (1 - gone)
        bubbleInk = min(max((lift - 0.6) / 0.3, 0), 1) * (1 - gone)
        carry = carrying ? CGSize(width: bubble.minX - Metrics.pillX, height: bubble.minY - Metrics.rowY) : .zero
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 300.0
    static let radius = 32.0
    static let pad = 16.0
    static let incomingY = 24.0
    static let incomingWidth = 166.0
    static let incomingHeight = 40.0
    /// Top of the composer row: the round plus, then the pill.
    static let rowY = height - pad - pillHeight
    static let plus = 40.0
    static let pillX = pad + plus + 8
    static let pillWidth = width - pad - pillX
    static let pillHeight = 44.0
    /// Inside the pill: the button at its right end, the discard cross, the right edge of the timer.
    static let mainX = pillWidth - 4 - 36
    static let discardX = mainX - 30
    static let timerRight = mainX - 8

    // The waveform: sixteen bars a second, 2 points wide and 2 apart.
    static let rate = 16.0
    static let pitch = 4.0
    static let barMin = 3.0
    static let barMax = 22.0
    /// Seconds a new bar takes to reach its height.
    static let growSeconds = 0.12
    /// Where the live bars are born and where they have faded out, inside the pill.
    static let liveRight = timerRight - 44
    static let liveLeft = 14.0
    static let liveFade = 18.0
    static let liveBars = 44
    static let previewX = 44.0
    static let previewBars = 28
    static let ripple = 0.35
    static let shortestTake = 0.4

    // The sent bubble: 12 points of padding, the play button and the bars, then one line of text.
    static let bubbleWidth = 220.0
    static let bubbleHeight = 82.0
    static let bubbleGap = 14.0
    static let bubbleBars = 38
    static let bubbleTextY = 52.0
    static let bubbleText = bubbleWidth - 24 - 40
    static let textLine = 18.0
    static let shimmerWidth = 48.0
    static let shimmerSeconds = 1.1
    /// Seconds between the send and the transcript.
    static let generateSeconds = 1.4
    /// The timeline stops this long after the last change.
    static let settleSeconds = 0.9

    // autoPlay (seconds): each press lasts `pressSeconds`, and the take is 2.4 s long. The loop is
    // a whole number of seconds, so the drawn caret blinks in step with it.
    static let loop = 9.0
    static let recordAt = 0.8
    static let stopAt = 3.2
    static let playAt = 3.9
    static let sendAt = 4.9
    static let fadeAt = 8.3
    static let pressSeconds = 0.12
    /// After a touch the script waits this long, then starts again from rest.
    static let resumeAfter = 4.0
}

private enum Palette {
    static let ink = Color(hex: 0x243447)
    static let mute = Color(hex: 0x243447, opacity: 0.55)
    static let frost = Color.white.opacity(0.55)
    /// The darker disc behind stop, send and the bubble's play button.
    static let well = Color(hex: 0x243447, opacity: 0.1)
}

private let skyGradient = Gradient(stops: [
    .init(color: Color(hex: 0x8fa3c4), location: 0),
    .init(color: Color(hex: 0xc5b5c6), location: 0.36),
    .init(color: Color(hex: 0xf6c7a0), location: 0.68),
    .init(color: Color(hex: 0xf7b25e), location: 1),
])

/// The shimmer's windows: how wide each is, and how much ink its copy of the label adds.
private let shimmerWindows: [(width: Double, ink: Double)] = [(48, 0.3), (22, 0.45)]

/// The preview lands a little past its place as a bubble and settles.
private let liftBack = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.34, y: 1.4), endControlPoint: UnitPoint(x: 0.64, y: 1))
private let softBack = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.3, y: 1.3), endControlPoint: UnitPoint(x: 0.6, y: 1))

private let plusPath = Path { p in
    p.move(to: CGPoint(x: 9, y: 3.5))
    p.addLine(to: CGPoint(x: 9, y: 14.5))
    p.move(to: CGPoint(x: 3.5, y: 9))
    p.addLine(to: CGPoint(x: 14.5, y: 9))
}

private let crossPath = Path { p in
    p.move(to: CGPoint(x: 5, y: 5))
    p.addLine(to: CGPoint(x: 13, y: 13))
    p.move(to: CGPoint(x: 13, y: 5))
    p.addLine(to: CGPoint(x: 5, y: 13))
}

private let arrowPath = Path { p in
    p.move(to: CGPoint(x: 9, y: 14.5))
    p.addLine(to: CGPoint(x: 9, y: 3.5))
    p.move(to: CGPoint(x: 4.5, y: 8))
    p.addLine(to: CGPoint(x: 9, y: 3.5))
    p.addLine(to: CGPoint(x: 13.5, y: 8))
}

/// Five bars of a waveform: the icon that starts a take.
private let wavePath = Path { p in
    for (index, height) in [4.0, 9, 14, 8, 3].enumerated() {
        let x = 3 + 3 * Double(index)
        p.move(to: CGPoint(x: x, y: 9 - height / 2))
        p.addLine(to: CGPoint(x: x, y: 9 + height / 2))
    }
}

private let trianglePath = Path { p in
    p.move(to: CGPoint(x: 6, y: 4.2))
    p.addLine(to: CGPoint(x: 14, y: 9))
    p.addLine(to: CGPoint(x: 6, y: 13.8))
    p.closeSubpath()
}

private let pausePath = Path { p in
    p.addRect(CGRect(x: 5.4, y: 4.6, width: 2, height: 8.8))
    p.addRect(CGRect(x: 10.6, y: 4.6, width: 2, height: 8.8))
}

/// How loud sample `k` of the take is, 0…1: a fixed pattern with the rise and fall of speech.
private func level(_ k: Int) -> Double {
    let x = Double(k)
    let phrase = 0.55 + 0.45 * sin(x * 0.45 + 1)
    let grain = 0.3 + 0.7 * abs(sin(x * 2.3 + 0.7) * cos(x * 0.9))
    return min(1, max(0.08, phrase * grain * 1.3))
}

/// One swing that dies away within a third of a second.
private func wobble(_ t: Double) -> Double {
    t <= 0 ? 0 : sin(22 * t) * exp(-9 * t)
}

/// Seconds as "0:07".
private func clock(_ seconds: Double) -> String {
    let whole = max(0, Int(seconds))
    return "\(whole / 60):" + (whole % 60 < 10 ? "0" : "") + "\(whole % 60)"
}

#Preview {
    ZStack {
        Color(hex: 0x161d27).ignoresSafeArea()
        VoiceNoteComposer(autoPlay: true)
    }
}
