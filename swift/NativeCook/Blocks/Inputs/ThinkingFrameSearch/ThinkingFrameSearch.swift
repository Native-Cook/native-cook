import SwiftUI

struct ThinkingFrameSearchResult: Equatable {
    var title: String
    var detail: String
    /// The two colours of the row's small gradient tile.
    var tint: [Color]
}

/// Search field that thinks out loud. Submitting a query makes a blurred orange glow bloom behind
/// the field and sharpen into a flowing gradient frame, with "Thinking…" and the current step in a
/// band on top; the frame then melts downwards into a soft panel of results, which unrolls below
/// the field row by row.
struct ThinkingFrameSearch: View {
    /// Controlled text; leave nil and the field keeps its own.
    var text: Binding<String>? = nil
    /// Types `prompt`, submits it, shows the results and starts over, without opening the keyboard.
    var autoPlay = false
    var placeholder = "Search or ask"
    var thinkingLabel = "Thinking…"
    /// The steps named at the right of the band, one after another.
    var statuses = ["Reading notes", "Checking calendar", "Looking for files"]
    var resultsLabel = "Top results"
    var results = [
        ThinkingFrameSearchResult(title: "Standup notes", detail: "Yesterday, 09:30", tint: [Color(hex: 0x6b3fa0), Color(hex: 0xc0306a)]),
        ThinkingFrameSearchResult(title: "Design review", detail: "Yesterday, 14:00", tint: [Color(hex: 0xc0306a), Color(hex: 0xf08a1c)]),
        ThinkingFrameSearchResult(title: "yesterday.pdf", detail: "Edited 18:12", tint: [Color(hex: 0xf08a1c), Color(hex: 0xf5a623)]),
    ]
    /// What autoPlay types.
    var prompt = "Yesterday"
    /// Called with the query when the keyboard's search key is pressed.
    var onSend: ((String) -> Void)? = nil
    /// Called with the row that was tapped; the panel closes and the field clears.
    var onSelect: ((ThinkingFrameSearchResult) -> Void)? = nil
    var onVoice: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    @State private var phase = Phase.idle
    @State private var status = 0
    @State private var lastStatus = -1
    /// 0…1: the frame letting go of its shape, on the way to the results or back to rest.
    @State private var melt = Tween(1)
    /// 0…1: how far the results panel is unrolled.
    @State private var panel = Tween(0)
    /// The query that was just cleared, fading where it stood.
    @State private var ghost = ""
    @State private var ghostFade = Tween(0)
    @State private var motion = Motion()
    /// Keeps the timeline running only while something moves.
    @State private var busy = false
    /// True while the autoPlay script owns the field.
    @State private var playing = false
    @State private var runToken = 0
    @State private var resumeToken = 0
    @State private var thinkToken = 0
    @State private var quietToken = 0

    private enum Phase { case idle, thinking, results }

    /// Moments the drawing is timed from. A class, so writing them never rebuilds the view.
    private final class Motion {
        var thinkAt = Date.distantPast
        var statusAt = Date.distantPast
        var keyAt = Date.distantPast
    }

    private var controlled: Bool { text != nil }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion }
    private var current: String { text?.wrappedValue ?? draft }
    private var hasText: Bool { !current.isEmpty }
    private var rows: [ThinkingFrameSearchResult] { Array(results.prefix(Metrics.maxRows)) }
    private var panelHeight: Double { Metrics.panelHead + Metrics.row * Double(rows.count) + Metrics.panelFoot }

    private var field: Binding<String> {
        Binding(get: { current }, set: { write($0) })
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: !busy)) { timeline in
                let date = timeline.date
                let since = date.timeIntervalSince(motion.thinkAt)
                let frame = FrameState(since: since, melt: melt.value(at: date), still: reduceMotion)
                let open = panel.value(at: date)

                ZStack(alignment: .topLeading) {
                    Canvas { context, _ in draw(context, frame: frame, open: open, since: since) }
                        .padding(-Metrics.bleed)
                        .allowsHitTesting(false)
                    band(frame, at: date, since: since)
                    resultsPanel(open)
                    Text(ghost)
                        .font(.system(size: 16))
                        .foregroundStyle(Palette.ink)
                        .opacity(ghostFade.value(at: date))
                        .frame(width: Metrics.textWidth, height: Metrics.fieldHeight, alignment: .leading)
                        .offset(x: Metrics.border + 16, y: Metrics.frameTop)
                        .allowsHitTesting(false)
                }
            }
            fieldRow
        }
        .lineLimit(1)
        .frame(width: Metrics.width, height: Metrics.frameTop + Metrics.fieldHeight + Metrics.gap + panelHeight, alignment: .topLeading)
        .onChange(of: focused) { _, now in
            if now {
                // A tap on the field takes it over from the script and starts from empty; a query
                // typed by hand stays, and only its results go away.
                if playing { clear() } else { dismiss() }
            }
            touch()
        }
        .onChange(of: auto, initial: true) { _, now in
            playing = now
            runToken += 1
        }
        .task(id: runToken) { await play() }
        .task(id: resumeToken) { await resume() }
        .task(id: thinkToken) { await think() }
        .task(id: quietToken) {
            try? await Task.sleep(for: .seconds(Metrics.settleSeconds))
            if !Task.isCancelled, phase == .idle { busy = false }
        }
    }

    // MARK: Parts

    private var fieldRow: some View {
        HStack(spacing: 4) {
            ZStack(alignment: .leading) {
                Text(placeholder)
                    .foregroundStyle(Palette.mute)
                    .opacity(hasText ? 0 : 1)
                    // It makes way at once, and comes back after the cleared query has faded.
                    .animation(hasText ? nil : .easeOut(duration: 0.18).delay(0.15), value: hasText)

                TextField("", text: field)
                    .focused($focused)
                    .foregroundStyle(Palette.ink)
                    .tint(Palette.orange)
                    .submitLabel(.search)
                    .onSubmit { send() }
                    .accessibilityLabel(placeholder)

                if playing && hasText && phase == .idle {
                    // The script does not focus the field, so until it submits it draws the caret
                    // itself: an invisible copy of the text pushes it to the end of the line.
                    TimelineView(.animation) { timeline in
                        HStack(spacing: 1) {
                            Text(current).hidden()
                            Capsule()
                                .fill(Palette.orange)
                                .frame(width: 2, height: 19)
                                .opacity(caretOpacity(timeline.date.timeIntervalSince(motion.keyAt)))
                        }
                    }
                    .allowsHitTesting(false)
                }
            }
            .font(.system(size: 16))
            .frame(maxWidth: .infinity, alignment: .leading)
            .clipped()

            Button {
                onVoice?()
            } label: {
                waveIcon
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(Press(scale: 0.9))
            .accessibilityLabel("Voice search")
        }
        .padding(.leading, 16)
        .padding(.trailing, 6)
        .frame(width: Metrics.fieldWidth, height: Metrics.fieldHeight)
        .offset(x: Metrics.border, y: Metrics.frameTop)
    }

    /// The band on top of the frame: the pulsing dots and the label at the left, the step at the right.
    private func band(_ frame: FrameState, at date: Date, since: Double) -> some View {
        let swap = min(max(date.timeIntervalSince(motion.statusAt) / Metrics.statusSeconds, 0), 1)
        let slide = reduceMotion ? 0.0 : 4.0
        return HStack(spacing: 6) {
            Canvas { context, _ in
                for (index, dot) in sparkDots.enumerated() {
                    let pulse = reduceMotion ? 1 : 0.6 + 0.4 * sin(since * 6 - Double(index) * 1.4)
                    context.fill(
                        Path(ellipseIn: CGRect(x: dot.x - dot.r, y: dot.y - dot.r, width: dot.r * 2, height: dot.r * 2)),
                        with: .color(.white.opacity(pulse))
                    )
                }
            }
            .frame(width: 12, height: 12)
            Text(thinkingLabel)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.white)
            Spacer(minLength: 8)
            ZStack(alignment: .trailing) {
                // The step that just ended leaves upwards while the next one arrives from below.
                if statuses.indices.contains(lastStatus) {
                    Text(statuses[lastStatus])
                        .opacity(1 - swap)
                        .offset(y: -slide * swap)
                }
                if statuses.indices.contains(status) {
                    Text(statuses[status])
                        .opacity(swap)
                        .offset(y: slide * (1 - swap))
                }
            }
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(Palette.statusInk)
        }
        .padding(.horizontal, 14)
        .frame(width: Metrics.width, height: Metrics.bandHeight)
        .offset(y: frame.top + frame.drop + 2)
        .opacity(frame.label)
        .allowsHitTesting(false)
        .accessibilityHidden(frame.label < 0.5)
    }

    private func resultsPanel(_ open: Double) -> some View {
        let shown = min(max(open, 0), 1)
        return VStack(alignment: .leading, spacing: 0) {
            Text(resultsLabel)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Palette.mute)
                .padding(.leading, 14)
                .padding(.top, 6)
                .frame(height: Metrics.panelHead)
                .opacity(reveal(open, after: 0.15))
            ForEach(Array(rows.enumerated()), id: \.offset) { index, result in
                let seen = reveal(open, after: 0.3 + 0.18 * Double(index))
                Button {
                    touch()
                    clear()
                    onSelect?(result)
                } label: {
                    HStack(spacing: 10) {
                        RoundedRectangle(cornerRadius: 8, style: .circular)
                            .fill(LinearGradient(colors: result.tint, startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: 30, height: 30)
                        Text(result.title)
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(Palette.ink)
                        Spacer(minLength: 8)
                        Text(result.detail)
                            .font(.system(size: 13))
                            .foregroundStyle(Palette.mute)
                    }
                    .padding(.leading, 12)
                    .padding(.trailing, 14)
                    .frame(height: Metrics.row)
                    .contentShape(Rectangle())
                }
                .buttonStyle(RowStyle())
                .opacity(seen)
                .offset(y: reduceMotion ? 0 : -8 * (1 - seen))
            }
        }
        .frame(width: Metrics.fieldWidth, height: panelHeight, alignment: .top)
        // The panel unrolls from its top edge, so the rows are uncovered one after another.
        .frame(height: reduceMotion ? panelHeight : panelHeight * max(open, 0), alignment: .top)
        .background(RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular).fill(.white))
        .clipShape(RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular))
        .opacity(min(1, shown * 3))
        .offset(x: Metrics.border, y: Metrics.frameTop + Metrics.fieldHeight + Metrics.gap)
        .allowsHitTesting(phase == .results)
        .accessibilityHidden(phase != .results)
    }

    private var waveIcon: some View {
        Path { p in
            for (index, height) in waveBars.enumerated() {
                let x = 3.6 + 3.2 * Double(index)
                p.move(to: CGPoint(x: x, y: 10 - height / 2))
                p.addLine(to: CGPoint(x: x, y: 10 + height / 2))
            }
        }
        .stroke(Palette.mute, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
        .frame(width: 20, height: 20)
    }

    // MARK: Behaviour

    private func write(_ value: String) {
        if let text { text.wrappedValue = value } else { draft = value }
    }

    /// Any touch stops the script; it comes back a few seconds after the last one.
    private func touch() {
        playing = false
        runToken += 1
        resumeToken += 1
    }

    private func send() {
        guard hasText else { return }
        touch()
        submit()
        onSend?(current)
    }

    /// The glow blooms and sharpens into the frame; the results follow by themselves.
    private func submit() {
        let now = Date.now
        phase = .thinking
        busy = true
        lastStatus = -1
        status = 0
        motion.thinkAt = now
        motion.statusAt = now
        melt = Tween(0)
        panel.retarget(0, at: now, duration: 0.25, curve: .easeInOut)
        thinkToken += 1
    }

    private func think() async {
        guard phase == .thinking else { return }
        let clock = ContinuousClock()
        let start = clock.now
        for index in statuses.indices.dropFirst() {
            let at = Metrics.thinkSeconds * Double(index) / Double(statuses.count)
            guard (try? await clock.sleep(until: start + .seconds(at))) != nil else { return }
            lastStatus = status
            status = index
            motion.statusAt = .now
        }
        guard (try? await clock.sleep(until: start + .seconds(Metrics.thinkSeconds))) != nil else { return }
        phase = .results
        melt.retarget(1, duration: 0.5, curve: .easeInOut)
        panel.retarget(1, duration: 0.7, curve: softBack)
        // Results that nobody touches are cleared like any other pause.
        if !playing { resumeToken += 1 }
    }

    /// Lets go of the frame and rolls the panel back up.
    private func dismiss() {
        guard phase != .idle else { return }
        phase = .idle
        melt.retarget(1, duration: 0.3, curve: .easeInOut)
        panel.retarget(0, duration: 0.35, curve: .easeInOut)
        thinkToken += 1
        quietToken += 1
    }

    /// Back to the empty field.
    private func clear() {
        dismiss()
        guard hasText else { return }
        busy = true
        ghost = current
        ghostFade = Tween(1)
        ghostFade.retarget(0, duration: 0.2, curve: .easeOut)
        write("")
        quietToken += 1
    }

    private func play() async {
        guard playing else { return }
        let clock = ContinuousClock()
        var start = clock.now
        let keys = keyTimes(prompt)
        while true {
            for (index, at) in keys.enumerated() {
                guard await wait(until: start + .seconds(at), on: clock) else { return }
                draft = String(prompt.prefix(index + 1))
                motion.keyAt = .now
            }
            guard await wait(until: start + .seconds(Metrics.submitAt), on: clock) else { return }
            submit()
            guard await wait(until: start + .seconds(Metrics.clearAt), on: clock) else { return }
            clear()
            start += .seconds(Metrics.loop)
            guard await wait(until: start, on: clock) else { return }
        }
    }

    private func wait(until instant: ContinuousClock.Instant, on clock: ContinuousClock) async -> Bool {
        (try? await clock.sleep(until: instant)) != nil
    }

    private func resume() async {
        guard resumeToken > 0, auto else { return }
        try? await Task.sleep(for: .seconds(Metrics.resumeAfter))
        guard !Task.isCancelled, !focused else { return }
        clear()
        playing = true
        runToken += 1
    }

    // MARK: Drawing

    private func draw(_ context: GraphicsContext, frame: FrameState, open: Double, since: Double) {
        let origin = Metrics.bleed
        let fieldRect = CGRect(
            x: origin + Metrics.border, y: origin + Metrics.frameTop, width: Metrics.fieldWidth, height: Metrics.fieldHeight
        )
        // The gradient is two periods long and slides to the right, purple arriving from the left.
        let phase = flowPhase(reduceMotion ? 0 : since)
        let flow = GraphicsContext.Shading.linearGradient(
            flowGradient,
            startPoint: CGPoint(x: origin - phase * Metrics.flowPeriod, y: 0),
            endPoint: CGPoint(x: origin + (2 - phase) * Metrics.flowPeriod, y: 0)
        )

        let shown = min(max(open, 0), 1)
        if shown > 0 {
            let panelRect = CGRect(
                x: fieldRect.minX, y: fieldRect.maxY + Metrics.gap,
                width: Metrics.fieldWidth, height: reduceMotion ? panelHeight : panelHeight * max(open, 0)
            )
            // What is left of the frame: a soft halo of its colours around the results.
            var halo = context
            halo.opacity = 0.42 * shown
            halo.addFilter(.blur(radius: 18))
            halo.fill(Path(roundedRect: panelRect.insetBy(dx: 2, dy: 2), cornerRadius: Metrics.panelRadius, style: .circular), with: flow)
            var shadow = context
            shadow.opacity = shown
            shadow.addFilter(.blur(radius: 12))
            shadow.fill(
                Path(roundedRect: panelRect.insetBy(dx: 10, dy: 6).offsetBy(dx: 0, dy: 8), cornerRadius: Metrics.panelRadius, style: .circular),
                with: .color(.black.opacity(0.1))
            )
        }

        if frame.opacity > 0 {
            let rect = CGRect(
                x: origin + Metrics.border * (1 - frame.grow),
                y: origin + frame.top + frame.drop,
                width: Metrics.fieldWidth + 2 * Metrics.border * frame.grow,
                // A melting frame sinks a little and runs down twice as far, towards the results.
                height: Metrics.fieldHeight + (Metrics.frameTop + Metrics.border) * frame.grow + 2 * frame.drop
            )
            var layer = context
            layer.opacity = frame.opacity
            if frame.blur > 0.05 { layer.addFilter(.blur(radius: frame.blur)) }
            layer.fill(
                Path(roundedRect: rect, cornerRadius: Metrics.fieldRadius + Metrics.border * frame.grow, style: .circular), with: flow
            )
        }

        let fieldPath = Path(roundedRect: fieldRect, cornerRadius: Metrics.fieldRadius, style: .circular)
        var lift = context
        lift.addFilter(.blur(radius: 6))
        lift.fill(
            Path(roundedRect: fieldRect.insetBy(dx: 6, dy: 4).offsetBy(dx: 0, dy: 4), cornerRadius: Metrics.fieldRadius, style: .circular),
            with: .color(.black.opacity(0.08))
        )
        context.fill(fieldPath, with: .color(.white))
    }

    private struct Press: ButtonStyle {
        let scale: Double

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? scale : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }

    private struct RowStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .background(Palette.rowPressed.opacity(configuration.isPressed ? 1 : 0))
                .animation(.easeOut(duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.selection, trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

/// Where the gradient frame is in its life: growing out of the field, sharp, or melting away.
private struct FrameState {
    /// 0…1 with a small overshoot: how far the frame has grown out of the field.
    let grow: Double
    let blur: Double
    let opacity: Double
    /// Opacity of the texts in the band.
    let label: Double
    /// Top edge of the frame, and how far it has sunk towards the results.
    let top: Double
    let drop: Double

    init(since: Double, melt: Double, still: Bool) {
        let sharp = smooth(since / Metrics.sharpenSeconds)
        grow = still ? 1 : backOut(min(max(since / Metrics.growSeconds, 0), 1))
        blur = still ? 0 : Metrics.bloomBlur * (1 - sharp) + Metrics.meltBlur * melt
        opacity = min(max(since / Metrics.glowSeconds, 0), 1) * (1 - melt)
        label = min(max((sharp - 0.6) / 0.3, 0), 1) * (1 - min(1, melt * 3))
        top = Metrics.frameTop * (1 - grow)
        drop = still ? 0 : Metrics.meltDrop * melt
    }
}

private enum Metrics {
    static let width = 350.0
    /// The frame's border on the sides and below the field, and the band and border above it.
    static let border = 5.0
    static let frameTop = 26.0
    static let bandHeight = 22.0
    static let fieldWidth = width - 2 * border
    static let fieldHeight = 48.0
    static let fieldRadius = 12.0
    static let textWidth = fieldWidth - 16 - 46
    /// Space between the field and the results panel.
    static let gap = 8.0
    static let panelRadius = 14.0
    static let panelHead = 30.0
    static let panelFoot = 6.0
    static let row = 46.0
    static let maxRows = 3
    /// The canvas overflows the component by this much on every side to fit the glow.
    static let bleed = 40.0

    // The frame: the glow fades in, grows with an overshoot and sharpens; later it melts.
    static let glowSeconds = 0.3
    static let growSeconds = 0.7
    static let sharpenSeconds = 0.9
    static let bloomBlur = 18.0
    static let meltBlur = 14.0
    static let meltDrop = 20.0
    /// The gradient is twice the component's width and moves this many periods a second.
    static let flowPeriod = 700.0
    static let flowStart = 0.68
    static let flowSpeed = 0.12
    static let statusSeconds = 0.25
    /// Seconds from the submit to the results.
    static let thinkSeconds = 3.1
    /// The timeline stops this long after the last change.
    static let settleSeconds = 0.7

    // autoPlay (seconds): type, submit, think, look at the results, clear, rest.
    static let loop = 9.2
    static let typeAt = 0.8
    /// The typing never takes longer than this, however long the prompt.
    static let typeSpan = 1.0
    static let submitAt = 2.0
    static let clearAt = 7.7
    /// After a touch the script waits this long, then starts again from rest.
    static let resumeAfter = 4.0
}

private enum Palette {
    static let ink = Color(hex: 0x141414)
    static let mute = Color(hex: 0x9a9a9a)
    static let orange = Color(hex: 0xf08a1c)
    static let rowPressed = Color(hex: 0xf2f2f2)
    static let statusInk = Color.white.opacity(0.78)
}

/// Seconds between two letters of the typed prompt: uneven, and the same every loop.
private let typeGaps = [0.09, 0.07, 0.11, 0.06, 0.085, 0.07, 0.12, 0.075]

/// Heights of the bars of the voice icon.
private let waveBars = [5.0, 11, 15, 9, 4]

/// The three dots in front of the label: centre and radius in a 12-point box.
private let sparkDots: [(x: Double, y: Double, r: Double)] = [(6, 2.6, 2), (2.4, 8.8, 1.7), (9.6, 8.8, 1.7)]

/// One period of the frame's colours, amber at both ends so it repeats without a seam.
private let flowStops: [(Double, Color)] = [
    (0, Color(hex: 0xf5a623)),
    (0.18, Color(hex: 0xf08a1c)),
    (0.42, Color(hex: 0xc0306a)),
    (0.6, Color(hex: 0x6b3fa0)),
    (0.74, Color(hex: 0xc0306a)),
    (0.9, Color(hex: 0xf08a1c)),
    (1, Color(hex: 0xf5a623)),
]

private let flowGradient = Gradient(
    stops: flowStops.map { .init(color: $0.1, location: $0.0 / 2) } + flowStops.map { .init(color: $0.1, location: 0.5 + $0.0 / 2) }
)

/// A slower back-out for the panel: it lands a few points past its height and settles.
private let softBack = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.3, y: 1.3), endControlPoint: UnitPoint(x: 0.6, y: 1))

private func smooth(_ t: Double) -> Double {
    let u = min(max(t, 0), 1)
    return u * u * (3 - 2 * u)
}

/// Ease-out that overshoots its target by a tenth before settling.
private func backOut(_ t: Double) -> Double {
    let u = t - 1
    return 1 + 2.70158 * u * u * u + 1.70158 * u * u
}

/// How far into its period the gradient is, `since` seconds after the submit.
private func flowPhase(_ since: Double) -> Double {
    let phase = (Metrics.flowStart - Metrics.flowSpeed * max(since, 0)).truncatingRemainder(dividingBy: 1)
    return phase < 0 ? phase + 1 : phase
}

/// 0…1 for a part of the panel that shows once the panel is `after` of the way open.
private func reveal(_ open: Double, after: Double) -> Double {
    min(max((open - after) / 0.25, 0), 1)
}

/// When each letter of the prompt appears, in seconds from the start of the loop.
private func keyTimes(_ prompt: String) -> [Double] {
    var at: [Double] = []
    var t = 0.0
    for index in 0..<prompt.count {
        at.append(t)
        t += typeGaps[index % typeGaps.count]
    }
    let scale = min(1, Metrics.typeSpan / max(t, 0.001))
    return at.map { Metrics.typeAt + $0 * scale }
}

/// The drawn caret stays lit for a moment after a letter, then blinks once a second.
private func caretOpacity(_ sinceKey: Double) -> Double {
    sinceKey.truncatingRemainder(dividingBy: 1) < 0.55 ? 1 : 0
}

#Preview {
    ZStack {
        Color(hex: 0xe8e8e8).ignoresSafeArea()
        ThinkingFrameSearch(autoPlay: true)
    }
}
