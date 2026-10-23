import SwiftUI
import UIKit

/// A dark chat panel where any paragraph of the assistant's answer can be swiped right to reply to
/// it. The paragraph follows the finger with resistance, a coral reply arrow pops in beside it past
/// a threshold and the rest of the answer dims; on release it springs back with a coral bar that
/// marks it as quoted, a quote preview slides up out of the composer, the placeholder changes and a
/// "Branch" pill appears beside the field. The quote can be answered, dismissed, or branched into a
/// new thread.
struct SwipeReplyComposer: View {
    /// The field's value when the caller owns it; left nil, the component keeps its own.
    var text: Binding<String>? = nil
    /// Swipes, replies and dismisses by itself in a loop. Never opens the keyboard.
    var autoPlay = false
    var title = "Weekend in Lisbon"
    /// The title after "Branch" is tapped.
    var branchTitle = "New branch"
    /// The assistant's answer, one swipeable paragraph each.
    var paragraphs = [
        "Two days is enough for Lisbon if you stay central and walk.",
        "Start in Alfama before the trams fill up, then ride the 28 up to Graça for the best light over the river.",
        "Keep Belém for the second morning: the tower, the monastery and a warm custard tart.",
    ]
    var placeholder = "Reply to Atlas"
    /// The placeholder while a paragraph is quoted.
    var quotePlaceholder = "Reply to this part…"
    var branchLabel = "Branch"
    /// What autoPlay types as its reply.
    var script = "And if it rains?"
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the trimmed message and the index of the quoted paragraph, if any.
    var onSend: ((String, Int?) -> Void)? = nil
    /// Called with the index of the paragraph that was swiped, or nil when the quote is dismissed.
    var onQuote: ((Int?) -> Void)? = nil
    /// Called with the index of the quoted paragraph when "Branch" is tapped.
    var onBranch: ((Int) -> Void)? = nil
    var onAttach: (() -> Void)? = nil

    /// What autoPlay is pressing.
    private enum Press {
        case send, dismiss
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
    /// The paragraph under the finger and how far it has been pulled, after resistance.
    @State private var active: Int? = nil
    @State private var pull = 0.0
    /// The pull has passed the threshold: releasing now quotes the paragraph.
    @State private var armed = false
    @State private var quoted: Int? = nil
    /// The preview's text, kept while the preview slides away.
    @State private var quoteText = ""
    /// The preview is flying up into a new branch.
    @State private var flying = false
    @State private var branched = false
    /// The sent text is on its way out of the field.
    @State private var clearing = false
    @State private var pressed: Press? = nil
    @State private var run = 0
    @State private var sends = 0
    @State private var branches = 0
    @State private var resume = 0
    @State private var ticks = 0
    @State private var impacts = 0

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
    /// 0…1: how far the pull is towards the threshold.
    private var progress: Double { min(1, pull / Metrics.threshold) }

    private func spring(_ stiffness: Double, _ damping: Double) -> Animation {
        reduceMotion ? .easeOut(duration: 0.2) : .interpolatingSpring(mass: 1, stiffness: stiffness, damping: damping)
    }

    private var settle: Animation { spring(Springs.settleStiffness, Springs.settleDamping) }
    private var pop: Animation { spring(Springs.popStiffness, Springs.popDamping) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular).fill(Palette.panel)

            titleChip.offset(x: Metrics.padX, y: Metrics.titleTop)

            VStack(alignment: .leading, spacing: Metrics.paragraphGap) {
                ForEach(paragraphs.indices, id: \.self) { index in
                    paragraph(index)
                }
            }
            .offset(x: Metrics.padX, y: Metrics.answerTop)

            quotePreview.offset(x: Metrics.composerX, y: Metrics.quoteTop)

            composer.offset(x: Metrics.composerX, y: Metrics.composerTop)
        }
        .frame(width: Metrics.panelWidth, height: Metrics.panelHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular))
        .sensoryFeedback(.impact(weight: .medium), trigger: ticks)
        .sensoryFeedback(.impact(weight: .light), trigger: impacts)
        .onAppear { restart() }
        .onChange(of: autoPlay) { restart() }
        .onChange(of: focused) { _, isFocused in
            if isFocused { takeOver() }
            resume += 1
        }
        .task(id: run) { await play() }
        .task(id: sends) { await finishSend() }
        .task(id: branches) { await finishBranch() }
        .task(id: resume) { await resumeWhenIdle() }
    }

    // MARK: Title

    private var titleChip: some View {
        let label = branched ? branchTitle : title
        return HStack(spacing: 6) {
            if branched {
                SwipeReplyComposerBranch()
                    .stroke(Palette.coral, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                    .frame(width: 14, height: 14)
                    .transition(.opacity.combined(with: .scale(scale: 0.5)))
            }
            ZStack(alignment: .leading) {
                Text(label)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                    .fixedSize()
                    .id(label)
                    .transition(
                        reduceMotion
                            ? .opacity
                            : .asymmetric(
                                insertion: .offset(y: Metrics.roll).combined(with: .opacity),
                                removal: .offset(y: -Metrics.roll).combined(with: .opacity)
                            )
                    )
            }
        }
        .padding(.horizontal, 12)
        .frame(height: Metrics.titleHeight)
        .background(Capsule().fill(Palette.raised))
        .clipShape(Capsule())
        .animation(.timingCurve(0.4, 0, 0.2, 1, duration: Script.roll), value: branched)
    }

    // MARK: Answer

    private func paragraph(_ index: Int) -> some View {
        let isActive = active == index
        let isQuoted = quoted == index
        // The others dim as the pull approaches the threshold, and stay dim while a paragraph is quoted.
        let dimmed: Double = {
            if quoted != nil { return isQuoted ? 0 : 1 }
            if active != nil { return isActive ? 0 : progress }
            return 0
        }()
        return ZStack(alignment: .leading) {
            ZStack {
                Circle().fill(Palette.coral)
                SwipeReplyComposerReply()
                    .stroke(Palette.panel, style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                    .frame(width: 16, height: 16)
            }
            .frame(width: Metrics.arrowSize, height: Metrics.arrowSize)
            .scaleEffect(isActive ? (armed ? 1 : Metrics.arrowHint * progress) : (reduceMotion ? 1 : 0.01))
            .opacity(isActive ? (armed ? 1 : 0.7 * progress) : 0)
            // Centred in the gap the paragraph has opened to the left of its bar.
            .offset(x: isActive ? (pull - Metrics.barGap - Metrics.arrowSize) / 2 : -Metrics.arrowSize / 2)

            Text(paragraphs[index])
                .font(Font(Metrics.answerFont))
                .lineSpacing(Metrics.answerLeading)
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .frame(width: Metrics.answerWidth, alignment: .leading)
                .overlay(alignment: .leading) {
                    Capsule()
                        .fill(Palette.coral)
                        .frame(width: Metrics.barWidth)
                        .padding(.vertical, 3)
                        .offset(x: -Metrics.barGap)
                        .opacity(isQuoted ? 1 : (isActive ? progress : 0))
                }
                .opacity(1 - Metrics.dim * dimmed)
                .offset(x: isActive ? pull : 0)
        }
        .contentShape(Rectangle())
        .gesture(swipe(index))
        .accessibilityAction(named: "Reply to this part") { quote(index, byHand: true) }
    }

    private func swipe(_ index: Int) -> some Gesture {
        DragGesture(minimumDistance: Metrics.slop)
            .onChanged { drag in
                takeOver()
                guard quoted != index, !flying else { return }
                active = index
                pull = Metrics.resist(drag.translation.width)
                let isArmed = pull >= Metrics.threshold
                if isArmed != armed {
                    if isArmed { ticks += 1 }
                    withAnimation(pop) { armed = isArmed }
                }
            }
            .onEnded { _ in
                guard active == index else { return }
                if armed {
                    quote(index, byHand: true)
                } else {
                    release()
                }
                resume += 1
            }
    }

    // MARK: Quote preview and composer

    private var quotePreview: some View {
        let visible = quoted != nil && !flying
        return HStack(spacing: 9) {
            Capsule().fill(Palette.coral).frame(width: Metrics.barWidth, height: 16)
            Text(quoteText)
                .font(.system(size: 13))
                .foregroundStyle(Palette.quote)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                dismissQuote(byHand: true)
            } label: {
                SwipeReplyComposerCross()
                    .stroke(Palette.mute, style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                    .frame(width: 9, height: 9)
                    .frame(width: 22, height: 22)
                    .background(Circle().fill(Palette.field))
                    .frame(width: 36, height: Metrics.quoteHeight)
                    .contentShape(Rectangle())
            }
            .buttonStyle(SwipeReplyComposerPressStyle())
            .accessibilityLabel("Dismiss quote")
            .scaleEffect(pressed == .dismiss ? Metrics.pressScale : 1)
            .animation(.easeOut(duration: Script.press), value: pressed)
        }
        .padding(.leading, 14)
        .frame(width: Metrics.composerWidth, height: Metrics.quoteHeight)
        .background(RoundedRectangle(cornerRadius: Metrics.quoteRadius, style: .circular).fill(Palette.raised))
        .opacity(visible ? 1 : 0)
        // Hidden, it waits behind the composer; flying into a branch, it leaves upwards.
        .offset(y: reduceMotion ? 0 : (flying ? -Metrics.quoteFly : (quoted != nil ? 0 : Metrics.quoteDrop)))
        .allowsHitTesting(visible)
    }

    private var composer: some View {
        let quoting = quoted != nil
        let fieldWidth = quoting ? Metrics.fieldNarrow : Metrics.fieldWide
        return ZStack(alignment: .leading) {
            Button {
                guard !handOver() else { return }
                onAttach?()
            } label: {
                ZStack {
                    Circle().fill(Palette.field)
                    Capsule().fill(Palette.quote).frame(width: 14, height: 1.6)
                    Capsule().fill(Palette.quote).frame(width: 1.6, height: 14)
                }
                .frame(width: Metrics.plusSize, height: Metrics.plusSize)
            }
            .buttonStyle(SwipeReplyComposerPressStyle())
            .accessibilityLabel("Attach")

            field
                .frame(width: fieldWidth, height: Metrics.fieldHeight, alignment: .leading)
                .background(Capsule().fill(Palette.field))
                .offset(x: Metrics.plusSize + Metrics.composerGap)

            Button {
                branch()
            } label: {
                HStack(spacing: 5) {
                    SwipeReplyComposerBranch()
                        .stroke(Palette.coral, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                        .frame(width: 14, height: 14)
                    Text(branchLabel)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .lineLimit(1)
                }
                .frame(width: Metrics.branchWidth, height: Metrics.fieldHeight)
                .background(Capsule().fill(Palette.field))
            }
            .buttonStyle(SwipeReplyComposerPressStyle())
            .scaleEffect(quoting || reduceMotion ? 1 : Metrics.branchScale, anchor: .trailing)
            .opacity(quoting ? 1 : 0)
            .allowsHitTesting(quoting && !flying)
            .offset(x: Metrics.composerWidth - Metrics.branchWidth)
        }
        .frame(width: Metrics.composerWidth, height: Metrics.fieldHeight, alignment: .leading)
    }

    private var field: some View {
        let quoting = quoted != nil
        return ZStack(alignment: .leading) {
            ZStack(alignment: .leading) {
                Text(quoting ? quotePlaceholder : placeholder)
                    .foregroundStyle(Palette.mute)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .id(quoting)
                    .transition(.opacity)
            }
            .opacity(hasText || caretOn ? 0 : 1)
            .padding(.leading, Metrics.fieldPadX)
            .padding(.trailing, Metrics.sendSlot)
            .animation(.easeOut(duration: Script.fade), value: quoting)
            .allowsHitTesting(false)

            TextField("", text: value)
                .foregroundStyle(Palette.ink)
                .tint(Palette.coral)
                .focused($focused)
                .submitLabel(.send)
                .onSubmit(send)
                .opacity(playing || clearing ? 0 : 1)
                .offset(y: clearing && !reduceMotion ? -Metrics.clearRise : 0)
                .padding(.leading, Metrics.fieldPadX)
                .padding(.trailing, Metrics.sendSlot)
                .frame(height: Metrics.fieldHeight)

            if playing {
                HStack(spacing: 1) {
                    Text(typed).foregroundStyle(Palette.ink).lineLimit(1).fixedSize()
                    TimelineView(.animation(paused: !caretOn)) { timeline in
                        RoundedRectangle(cornerRadius: 1, style: .circular)
                            .fill(Palette.coral)
                            .frame(width: 2, height: 18)
                            .opacity(caretOn ? swipeReplyComposerBlink(idle: timeline.date.timeIntervalSince(lastKey)) : 0)
                    }
                }
                .opacity(clearing ? 0 : 1)
                .offset(y: clearing && !reduceMotion ? -Metrics.clearRise : 0)
                .padding(.leading, Metrics.fieldPadX)
                // While autoPlay runs, a touch on the field hands it to the keyboard.
                Color.clear.contentShape(Rectangle()).onTapGesture {
                    takeOver()
                    focused = true
                }
            }

            Button {
                guard !handOver() else { return }
                send()
            } label: {
                ZStack {
                    Circle().fill(hasText ? Palette.coral : Palette.sendIdle)
                    SwipeReplyComposerArrow()
                        .stroke(hasText ? Palette.panel : Palette.mute, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
                .frame(width: Metrics.sendSize, height: Metrics.sendSize)
                .frame(width: Metrics.sendSlot, height: Metrics.fieldHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(SwipeReplyComposerPressStyle())
            .accessibilityLabel("Send")
            .scaleEffect(pressed == .send ? Metrics.pressScale : 1)
            .animation(.easeOut(duration: Script.press), value: pressed)
            .animation(.easeOut(duration: Script.fade), value: hasText)
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .font(.system(size: Metrics.fieldFont))
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
        clearing = false
        flying = false
        withAnimation(settle) {
            pull = 0
            armed = false
            active = nil
            quoted = nil
            branched = false
        }
        sends += 1
        branches += 1
    }

    /// A touch stops the script and puts the panel back at rest.
    private func takeOver() {
        guard playing else { return }
        playing = false
        reset()
        run += 1
    }

    /// For buttons: true when the touch only stopped autoPlay, so the button should do nothing else.
    private func handOver() -> Bool {
        guard playing else { return false }
        takeOver()
        resume += 1
        return true
    }

    /// The paragraph springs back, marked as quoted, and the composer changes to answer it.
    private func quote(_ index: Int, byHand: Bool) {
        guard paragraphs.indices.contains(index) else { return }
        if byHand { onQuote?(index) }
        quoteText = paragraphs[index]
        withAnimation(settle) {
            pull = 0
            active = nil
            quoted = index
        }
        withAnimation(pop) { armed = false }
    }

    private func release() {
        withAnimation(settle) {
            pull = 0
            active = nil
        }
        withAnimation(pop) { armed = false }
    }

    private func dismissQuote(byHand: Bool) {
        if byHand {
            guard !handOver() else { return }
            impacts += 1
            onQuote?(nil)
            resume += 1
        }
        withAnimation(settle) { quoted = nil }
    }

    private func branch() {
        guard !handOver(), let index = quoted, !flying else { return }
        impacts += 1
        onBranch?(index)
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .timingCurve(0.4, 0, 1, 1, duration: Script.fly)) { flying = true }
        branches += 1
    }

    /// The preview has flown off: the quote is over and the panel is now the new branch.
    private func finishBranch() async {
        guard flying else { return }
        try? await Task.sleep(for: .seconds(Script.fly))
        guard !Task.isCancelled else { return }
        withAnimation(settle) {
            quoted = nil
            branched = true
        }
        flying = false
        resume += 1
    }

    private func send() {
        let message = shown.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clearing, !message.isEmpty else { return }
        if !playing {
            impacts += 1
            onSend?(message, quoted)
        }
        caretOn = false
        withAnimation(.easeIn(duration: Script.fade)) { clearing = true }
        sends += 1
    }

    /// The sent text has left the field: empty it, and the quote goes with it.
    private func finishSend() async {
        guard clearing else { return }
        try? await Task.sleep(for: .seconds(Script.fade))
        guard !Task.isCancelled else { return }
        if playing { typed = "" } else { value.wrappedValue = "" }
        clearing = false
        withAnimation(settle) { quoted = nil }
        resume += 1
    }

    /// One scripted swipe: the pull grows as if a finger dragged it, arms on the way, holds, and lets go.
    private func swipeByScript(_ index: Int, from start: ContinuousClock.Instant, at time: Double) async throws {
        let clock = ContinuousClock()
        try await clock.sleep(until: start + .seconds(time))
        if !reduceMotion {
            withAnimation(.timingCurve(0.2, 0.7, 0.3, 1, duration: Script.drag)) {
                active = index
                pull = Metrics.scriptPull
            }
            try await clock.sleep(until: start + .seconds(time + Script.armAfter))
            withAnimation(pop) { armed = true }
        }
        try await clock.sleep(until: start + .seconds(time + Script.releaseAfter))
        quote(index, byHand: false)
    }

    /// The autoPlay loop. Every step waits for its own moment after the loop began, so the period stays `Script.loop`.
    private func play() async {
        guard playing, paragraphs.count > 1 else { return }
        let clock = ContinuousClock()
        let keys = swipeReplyComposerRhythm(script)
        var start = clock.now
        do {
            while true {
                try await swipeByScript(1, from: start, at: Script.swipeAt)
                try await clock.sleep(until: start + .seconds(Script.caretAt))
                lastKey = .now
                caretOn = true
                for (index, at) in keys.enumerated() {
                    try await clock.sleep(until: start + .seconds(Script.typeAt + at))
                    typed = String(script.prefix(index + 1))
                    lastKey = .now
                }
                try await clock.sleep(until: start + .seconds(Script.sendAt - Script.press))
                pressed = .send
                try await clock.sleep(until: start + .seconds(Script.sendAt))
                pressed = nil
                send()
                try await swipeByScript(paragraphs.count - 1, from: start, at: Script.secondSwipeAt)
                try await clock.sleep(until: start + .seconds(Script.dismissAt - Script.press))
                pressed = .dismiss
                try await clock.sleep(until: start + .seconds(Script.dismissAt))
                pressed = nil
                dismissQuote(byHand: false)
                start += .seconds(Script.loop)
                try await clock.sleep(until: start)
            }
        } catch {}
    }

    /// autoPlay comes back once nothing has been touched for a while and the panel is at rest.
    private func resumeWhenIdle() async {
        guard autoPlay, !playing, !focused else { return }
        try? await Task.sleep(for: .seconds(Script.resumeAfter))
        guard !Task.isCancelled, !focused, value.wrappedValue.isEmpty, quoted == nil, active == nil else { return }
        playing = true
        reset()
        run += 1
    }
}

private enum Metrics {
    static let panelWidth = 350.0
    static let panelHeight = 412.0
    static let panelRadius = 40.0
    static let padX = 22.0

    static let titleTop = 20.0
    static let titleHeight = 30.0
    static let roll = 8.0

    static let answerTop = 68.0
    static let answerWidth = panelWidth - padX * 2
    static let paragraphGap = 16.0
    static let answerSize = 15.0
    static let answerLine = 22.0
    static let uiFont: UIFont = {
        let base = UIFont.systemFont(ofSize: answerSize)
        return base.fontDescriptor.withDesign(.serif).map { UIFont(descriptor: $0, size: answerSize) } ?? base
    }()
    static let answerFont = uiFont as CTFont
    /// Extra space between lines that brings the font's own line height up to `answerLine`.
    static let answerLeading = max(0, answerLine - uiFont.lineHeight)

    /// The finger has to move this far before a swipe starts.
    static let slop = 12.0
    /// The pull approaches this distance but never reaches it.
    static let pullLimit = 110.0
    static let resistance = 0.55
    /// Pull at which the reply arrow pops in and a release quotes the paragraph.
    static let threshold = 44.0
    /// Where autoPlay's swipe ends.
    static let scriptPull = 56.0
    static let arrowSize = 28.0
    /// Scale the arrow reaches just before the threshold.
    static let arrowHint = 0.55
    static let barWidth = 2.5
    static let barGap = 10.0
    /// How much the other paragraphs fade.
    static let dim = 0.68

    static let composerX = 18.0
    static let composerWidth = panelWidth - composerX * 2
    static let composerTop = 352.0
    static let composerGap = 6.0
    static let plusSize = 38.0
    static let fieldHeight = 40.0
    static let fieldPadX = 14.0
    static let fieldFont = 15.0
    static let sendSize = 30.0
    static let sendSlot = 40.0
    static let branchWidth = 78.0
    static let branchScale = 0.6
    static let fieldWide = composerWidth - plusSize - composerGap
    static let fieldNarrow = fieldWide - branchWidth - composerGap
    static let pressScale = 0.86
    static let clearRise = 6.0

    static let quoteHeight = 36.0
    static let quoteRadius = 14.0
    static let quoteTop = composerTop - 8 - quoteHeight
    /// Hidden, the preview sits this far down, behind the composer.
    static let quoteDrop = 26.0
    static let quoteFly = 70.0

    /// The paragraph's offset for a finger that has moved `distance` to the right: it follows less and less.
    static func resist(_ distance: Double) -> Double {
        let travelled = max(0, distance) * resistance
        return (1 - 1 / (travelled / pullLimit + 1)) * pullLimit
    }
}

private enum Springs {
    // The paragraph going back, the preview, the field's width and the Branch pill.
    static let settleStiffness = 300.0
    static let settleDamping = 26.0
    // The reply arrow popping in past the threshold, with overshoot.
    static let popStiffness = 520.0
    static let popDamping = 18.0
}

/// The autoPlay loop, in seconds from its start.
private enum Script {
    static let swipeAt = 0.8
    /// A scripted swipe: how long the pull takes, when it arms and when the finger lets go.
    static let drag = 0.65
    static let armAfter = 0.3
    static let releaseAfter = 0.95
    static let caretAt = 2.5
    static let typeAt = 2.7
    /// The whole reply is typed in this time; a short one is typed no slower than `slowestKey` per letter.
    static let typeSpan = 1.0
    static let slowestKey = 0.075
    static let sendAt = 4.3
    static let secondSwipeAt = 5.3
    static let dismissAt = 7.3
    static let loop = 8.8

    static let press = 0.12
    static let fade = 0.2
    static let roll = 0.3
    static let fly = 0.28
    /// After the last touch, autoPlay waits this long.
    static let resumeAfter = 3.0
}

private enum Palette {
    static let panel = Color(hex: 0x1e1e1c)
    static let raised = Color(hex: 0x282826)
    static let field = Color(hex: 0x2e2e2b)
    static let sendIdle = Color(hex: 0x3a3a37)
    static let ink = Color(hex: 0xeceae4)
    static let quote = Color(hex: 0xc9c7c1)
    static let mute = Color(hex: 0x8a8a86)
    static let coral = Color(hex: 0xe8743b)
}

/// Seconds after typing starts at which each letter of `script` lands: uneven like a person's typing
/// (a beat after a space, a pause after punctuation) but the same every time.
private func swipeReplyComposerRhythm(_ script: String) -> [Double] {
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
private func swipeReplyComposerBlink(idle: Double) -> Double {
    guard idle > 0.5 else { return 1 }
    let t = (idle - 0.5).truncatingRemainder(dividingBy: 1)
    if t < 0.12 { return 1 - t / 0.12 }
    if t < 0.5 { return 0 }
    if t < 0.62 { return (t - 0.5) / 0.12 }
    return 1
}

private struct SwipeReplyComposerPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? Metrics.pressScale : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

/// Reply arrow in a 16 × 16 box: a stem that rises and turns right into an arrowhead.
private struct SwipeReplyComposerReply: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 3, y: 12.5))
        path.addLine(to: CGPoint(x: 3, y: 10.5))
        path.addQuadCurve(to: CGPoint(x: 7, y: 6.5), control: CGPoint(x: 3, y: 6.5))
        path.addLine(to: CGPoint(x: 13, y: 6.5))
        path.move(to: CGPoint(x: 9.6, y: 3))
        path.addLine(to: CGPoint(x: 13.1, y: 6.5))
        path.addLine(to: CGPoint(x: 9.6, y: 10))
        return path
    }
}

/// Branching arrow in a 14 × 14 box: a stem going down with an arrow leaving it to the right.
private struct SwipeReplyComposerBranch: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 3.5, y: 1.5))
        path.addLine(to: CGPoint(x: 3.5, y: 12.5))
        path.move(to: CGPoint(x: 3.5, y: 4.5))
        path.addQuadCurve(to: CGPoint(x: 7.5, y: 8.5), control: CGPoint(x: 3.5, y: 8.5))
        path.addLine(to: CGPoint(x: 12, y: 8.5))
        path.move(to: CGPoint(x: 9.6, y: 6))
        path.addLine(to: CGPoint(x: 12.1, y: 8.5))
        path.addLine(to: CGPoint(x: 9.6, y: 11))
        return path
    }
}

/// Up arrow in the 30 × 30 send button.
private struct SwipeReplyComposerArrow: Shape {
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

/// Cross in a 9 × 9 box.
private struct SwipeReplyComposerCross: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0.8, y: 0.8))
        path.addLine(to: CGPoint(x: 8.2, y: 8.2))
        path.move(to: CGPoint(x: 8.2, y: 0.8))
        path.addLine(to: CGPoint(x: 0.8, y: 8.2))
        return path
    }
}

#Preview {
    ZStack {
        Color(hex: 0x111110).ignoresSafeArea()
        SwipeReplyComposer(autoPlay: true)
    }
}
