import SwiftUI

struct RoadmapPathStepperStep: Equatable {
    var title: String
    var description: String

    static let samples = [
        RoadmapPathStepperStep(title: "You are here", description: "You've made the greatest decision of your life to use Passa"),
        RoadmapPathStepperStep(title: "Submit docs on Passa", description: "Plug your information into free templates!"),
        RoadmapPathStepperStep(title: "Passa reviews docs", description: "We'll ensure your application is error free"),
        RoadmapPathStepperStep(title: "We submit to Govt.", description: "The embassy reviews your application"),
        RoadmapPathStepperStep(title: "Track trip status", description: "We'll always update you on your trip"),
        RoadmapPathStepperStep(title: "Get there on time!", description: "You're ready to start packing for your trip!"),
    ]
}

/// A roadmap screen: a heading, and a path that leaves a pin beside it, turns under it and becomes
/// the rail of a list of steps. On entry the path draws itself, the pin rides it to the first step,
/// and the accent runs down the rail while the screen rises, bringing each step in as it reaches its
/// node. After that the list scrolls, a tap on a step moves the accent to it, and Continue goes one
/// step on.
struct RoadmapPathStepper: View {
    var steps = RoadmapPathStepperStep.samples
    /// The step that is reached when the screen opens, from 0.
    var initialStep = 0
    /// One or two lines.
    var heading = "The Roadmap to\nYour Trip"
    /// The two parts of the pill in the top bar; the second one is heavier.
    var tripLabel = "Trip on"
    var tripDate = "22 Dec, 09:52 PM"
    var continueLabel = "Continue"
    var accent = Color(hex: 0x413ef0)
    /// Replays the entrance every `M.loop` seconds, starting from the finished screen.
    var autoPlay = false
    /// Called with the step a tap chose.
    var onSelect: ((Int) -> Void)? = nil
    /// Called with the step Continue moved to.
    var onContinue: ((Int) -> Void)? = nil
    /// Called when Continue is pressed on the last step.
    var onDone: (() -> Void)? = nil
    var onBack: (() -> Void)? = nil
    var onHome: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The reached step; nil until a touch changes it.
    @State private var chosen: Int? = nil
    /// The moment the screen is empty and the entrance starts; nil when none is running.
    @State private var zero: Date? = nil
    /// Without autoPlay the entrance plays once, when the screen appears: until then it is empty.
    @State private var entered = false
    /// The accent's end under the first node, moving from one place to another.
    @State private var fill: Move? = nil
    @State private var scroll = Glide.still(0)
    /// Where the list was taken, while a finger drags it.
    @State private var grab: Double? = nil
    @State private var dragging = false
    /// How far the list scrolls on this screen.
    @State private var limit = 0.0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// The timeline runs while something moves, and until this moment.
    @State private var busyUntil = Date.distantPast
    @State private var busy = false
    @State private var runTask: Task<Void, Never>? = nil
    @State private var selectHaptic = UISelectionFeedbackGenerator()
    @State private var pressHaptic = UIImpactFeedbackGenerator(style: .light)

    private var last: Int { max(steps.count - 1, 0) }
    private var first: Int { min(max(initialStep, 0), last) }
    private var index: Int { min(chosen ?? first, last) }
    /// From the first node to the last.
    private var span: Double { Double(last) * Metrics.row }
    private var waiting: Bool { !autoPlay && !reduceMotion && !entered }
    /// The entrance is on its way: touches wait for it.
    private var playing: Bool { zero != nil || waiting }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let layout = Layout(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom,
                top: insets.top, bottom: insets.bottom, span: span
            )

            TimelineView(.animation(paused: !busy)) { timeline in
                let frame = frame(at: timeline.date, layout: layout)

                ZStack(alignment: .topLeading) {
                    Palette.screen

                    stage(frame, layout: layout)
                        .offset(y: layout.stageTop + frame.rise)

                    continuePanel(layout: layout)
                        .offset(y: layout.height - layout.panel + frame.panelSlide)

                    Palette.screen
                        .opacity(frame.veil)
                        .allowsHitTesting(false)

                    topBar
                        .frame(width: layout.width, height: Metrics.bar)
                        .offset(y: insets.top + Metrics.barTop)
                }
                .frame(width: layout.width, height: layout.height, alignment: .topLeading)
                .clipped()
            }
            // The reader starts inside the safe area; the screen is laid out from its real corner.
            .offset(x: -insets.leading, y: -insets.top)
            .onChange(of: layout.maxScroll, initial: true) { _, value in limit = value }
        }
        .preferredColorScheme(.light)
        .onAppear {
            selectHaptic.prepare()
            if waiting { play(fromRest: false) }
            entered = true
        }
        .onDisappear { runTask?.cancel() }
        .task(id: busyUntil) {
            try? await Task.sleep(for: .seconds(max(0, busyUntil.timeIntervalSinceNow)))
            if !Task.isCancelled { busy = false }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The screen

    private func stage(_ frame: Frame, layout: Layout) -> some View {
        ZStack(alignment: .topLeading) {
            Text(heading)
                .font(.system(size: 26, weight: .medium))
                .lineSpacing(Metrics.headingGap)
                .foregroundStyle(Palette.ink)
                .lineLimit(2)
                .frame(width: layout.width - Metrics.headingX - Metrics.side, alignment: .leading)
                .opacity(frame.heading)
                .offset(x: Metrics.headingX, y: Metrics.headingGap / 2)
                .accessibilityAddTraits(.isHeader)

            Canvas { context, _ in
                var rail = Path()
                rail.move(to: CGPoint(x: Metrics.railX, y: frame.greyFrom))
                rail.addLine(to: CGPoint(x: Metrics.railX, y: frame.greyTo))
                context.stroke(rail, with: .color(Palette.track), lineWidth: Metrics.rail)

                let path = Self.path(to: Metrics.listTop + Metrics.nodeInset + layout.span + Metrics.overscroll)
                let style = StrokeStyle(lineWidth: Metrics.stroke, lineCap: .round)
                if frame.ghostOpacity > 0 {
                    context.stroke(path.trimmedPath(from: 0, to: frame.ghost / layout.total), with: .color(accent.opacity(frame.ghostOpacity)), style: style)
                }
                if frame.solid > 0 {
                    context.stroke(path.trimmedPath(from: 0, to: frame.solid / layout.total), with: .color(accent), style: style)
                }
            }
            .frame(width: layout.width, height: layout.stageHeight)
            .allowsHitTesting(false)

            ZStack(alignment: .topLeading) {
                ForEach(Array(steps.enumerated()), id: \.offset) { k, step in
                    row(k, step: step, shown: frame.shown[k], reached: frame.reached[k], layout: layout)
                        .offset(y: Double(k) * Metrics.row - frame.offset + (1 - frame.shown[k]) * M.slide)
                }
            }
            .frame(width: layout.width, height: max(0, layout.stageHeight - Metrics.listTop), alignment: .topLeading)
            .clipped()
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 6).onChanged(drag).onEnded(release))
            .onTapGesture { location in tap(at: location) }
            .offset(y: Metrics.listTop)

            UnevenRoundedRectangle(
                cornerRadii: RectangleCornerRadii(
                    topLeading: Metrics.node / 2,
                    bottomLeading: Metrics.pinTip + (Metrics.node / 2 - Metrics.pinTip) * frame.pinRound,
                    bottomTrailing: Metrics.node / 2,
                    topTrailing: Metrics.node / 2
                ),
                style: .circular
            )
            .fill(accent)
            .frame(width: Metrics.node, height: Metrics.node)
            .rotationEffect(.degrees(-45))
            .overlay {
                Circle()
                    .fill(Palette.screen)
                    .frame(width: Metrics.pinDot, height: Metrics.pinDot)
                    .scaleEffect(1 - (1 - Metrics.dot / Metrics.pinDot) * frame.pinRound)
            }
            .scaleEffect(frame.pinScale)
            .opacity(frame.pinShown ? 1 : 0)
            .offset(x: frame.pin.x - Metrics.node / 2, y: frame.pin.y - Metrics.node / 2)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .frame(width: layout.width, height: layout.stageHeight, alignment: .topLeading)
    }

    private func row(_ k: Int, step: RoadmapPathStepperStep, shown: Double, reached: Double, layout: Layout) -> some View {
        ZStack(alignment: .topLeading) {
            Text("\(k + 1)")
                .font(.system(size: 13, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Palette.ink)
                .frame(width: 40, height: 18)
                .offset(x: Metrics.numberX - 20, y: Metrics.nodeInset - 9)

            Circle()
                .fill(Palette.screen)
                .overlay(Circle().strokeBorder(Palette.outline, lineWidth: 1.5))
                .frame(width: Metrics.hollow, height: Metrics.hollow)
                .offset(x: Metrics.railX - Metrics.hollow / 2, y: Metrics.nodeInset - Metrics.hollow / 2)

            Circle()
                .fill(accent)
                .overlay(Circle().fill(Palette.screen).frame(width: Metrics.dot, height: Metrics.dot))
                .frame(width: Metrics.node, height: Metrics.node)
                .scaleEffect(0.6 + 0.4 * reached)
                .opacity(reached)
                .offset(x: Metrics.railX - Metrics.node / 2, y: Metrics.nodeInset - Metrics.node / 2)

            Text(step.title)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .frame(width: max(0, layout.width - Metrics.textX - Metrics.side), height: 22, alignment: .leading)
                .offset(x: Metrics.textX, y: Metrics.nodeInset - 11)

            Text(step.description)
                .font(.system(size: 14))
                .lineSpacing(Metrics.descriptionGap)
                .foregroundStyle(Palette.mute)
                .lineLimit(3)
                .frame(width: layout.textWidth, alignment: .leading)
                .offset(x: Metrics.textX, y: Metrics.nodeInset + 17 + Metrics.descriptionGap / 2)
        }
        .frame(width: layout.width, height: Metrics.row, alignment: .topLeading)
        .opacity(shown)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(k + 1). \(step.title). \(step.description)")
        .accessibilityAddTraits(k == index ? [.isButton, .isSelected] : .isButton)
        .accessibilityAction { select(k) }
    }

    private func continuePanel(layout: Layout) -> some View {
        Button(action: advance) {
            Text(continueLabel)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Palette.screen)
                .lineLimit(1)
                .frame(maxWidth: .infinity)
                .frame(height: Metrics.button)
                .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(accent))
        }
        .buttonStyle(DimStyle())
        .padding(.top, Metrics.panelPad)
        .padding(.horizontal, Metrics.side)
        .frame(width: layout.width, height: layout.panel, alignment: .top)
        .background(Palette.screen)
    }

    private var topBar: some View {
        HStack(spacing: 8) {
            Button {
                touched()
                onBack?()
            } label: {
                Image(systemName: "arrow.left")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(accent)
                    .frame(width: Metrics.round, height: Metrics.round)
                    .overlay(Circle().strokeBorder(accent, lineWidth: 1.5))
                    .contentShape(Circle())
            }
            .buttonStyle(DimStyle())
            .accessibilityLabel("Back")

            Spacer(minLength: 0)

            HStack(spacing: 6) {
                Image(systemName: "checkmark.shield.fill")
                    .font(.system(size: 11.5))
                (Text(tripLabel + " ") + Text(tripDate).fontWeight(.medium))
                    .font(.system(size: 13))
                    .lineLimit(1)
            }
            .foregroundStyle(Palette.screen)
            .padding(.horizontal, 14)
            .frame(height: Metrics.bar)
            .background(Capsule().fill(accent))
            .accessibilityElement(children: .combine)

            Spacer(minLength: 0)

            Button {
                touched()
                onHome?()
            } label: {
                Image(systemName: "house.fill")
                    .font(.system(size: 11))
                    .foregroundStyle(Palette.screen)
                    .frame(width: Metrics.round, height: Metrics.round)
                    .background(Circle().fill(accent))
                    .contentShape(Circle())
            }
            .buttonStyle(DimStyle())
            .accessibilityLabel("Home")
        }
        .padding(.horizontal, Metrics.side)
    }

    // MARK: One frame

    /// Where everything is at a moment of the timeline.
    private func frame(at date: Date, layout: Layout) -> Frame {
        let length = reduceMotion ? M.fade : M.end
        let e = zero.map { min(date.timeIntervalSince($0), length) } ?? (waiting ? 0 : M.end)
        // While the entrance runs it places everything; before and after, the list's own values do.
        let live = !reduceMotion && playing && e >= 0
        // How far the accent has run down the rail during the entrance.
        let tour = layout.span * M.steady((e - M.tourAt) / M.tour)
        let offset = live ? min(max(tour - layout.riseSpan, 0), layout.maxScroll) : scroll.value(at: date)
        let from = fill?.from ?? Double(index) * Metrics.row
        let to = fill?.to ?? from
        let moved = fill?.progress(at: date) ?? 1
        let landed = e >= M.travelAt + M.travel

        var frame = Frame(offset: offset)
        for k in 0...last {
            if live {
                frame.shown.append(k == 0 ? M.smooth(e / M.headIn) : M.smooth((tour - (Double(k) * Metrics.row - M.lead)) / M.reveal))
                frame.reached.append(!landed ? 0 : k == 0 ? 1 : M.reached(by: tour, k))
            } else {
                frame.shown.append(1)
                // With reduced motion the two states cross-fade; otherwise the accent passes the node.
                frame.reached.append(
                    reduceMotion ? M.reached(by: from, k) * (1 - moved) + M.reached(by: to, k) * moved : M.reached(by: from + (to - from) * moved, k)
                )
            }
        }

        // The accent's length along the path: one stroke, and with reduced motion a second one
        // that fades in or out over the stretch that changes.
        func lengthFor(_ fill: Double) -> Double { max(Metrics.pathToList, Metrics.pathToNode + fill - offset) }
        if live {
            frame.solid = e < M.draw ? Metrics.pathToNode * M.easeOut(e / M.draw) : lengthFor(tour)
        } else if reduceMotion {
            frame.solid = lengthFor(min(from, to))
            frame.ghost = lengthFor(max(from, to))
            frame.ghostOpacity = to > from ? moved : 1 - moved
        } else {
            frame.solid = lengthFor(from + (to - from) * moved)
        }

        let top = max(Metrics.listTop, Metrics.listTop + Metrics.nodeInset - offset)
        let full = min(max(top, Metrics.listTop + Metrics.nodeInset + layout.span - offset), layout.stageHeight)
        frame.greyFrom = top
        frame.greyTo = top + (full - top) * (live ? M.easeOut((e - M.greyAt) / M.grey) : 1)

        if live {
            let lifted = layout.riseSpan > 0 ? min(tour / layout.riseSpan, 1) : M.steady((e - M.tourAt) / M.tour)
            frame.rise = layout.rise * (1 - lifted)
            frame.heading = M.clamp(e / M.headIn)
            frame.panelSlide = layout.panel * (1 - M.easeOut((e - M.buttonAt) / M.buttonIn))
            let along = M.easeInOut((e - M.travelAt) / M.travel)
            frame.pin = Self.pathPoint(Metrics.pathToNode * along)
            // Near the node the tip rounds off and the pin is the node.
            frame.pinRound = M.smooth((along - 0.7) / 0.3)
            frame.pinScale = M.overshoot(e / M.pop)
            frame.pinShown = !landed
        }
        frame.veil = e < 0 ? M.clamp((e + M.out + M.gap) / M.out) : reduceMotion && zero != nil ? 1 - M.clamp(e / M.fade) : 0
        return frame
    }

    // MARK: Behaviour

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    /// Keeps the timeline running for this long.
    private func stir(for seconds: Double) {
        busyUntil = max(busyUntil, Date.now.addingTimeInterval(seconds))
        busy = true
    }

    /// Moves the accent, and the reached node, to a step.
    private func reach(_ step: Int) {
        let now = Date.now
        let from = fill?.value(at: now) ?? Double(index) * Metrics.row
        let to = Double(step) * Metrics.row
        let duration = reduceMotion ? M.fade : abs(to - from) / M.fillSpeed
        chosen = step
        fill = Move(from: from, to: to, start: now, duration: duration)
        stir(for: duration + 0.1)
    }

    private func scrollTo(_ to: Double) {
        let now = Date.now
        scroll = reduceMotion ? .still(to) : Glide(from: scroll.value(at: now), to: to, velocity: 0, start: now, damping: M.returnDamping)
        stir(for: M.settle)
    }

    /// Plays the entrance: over the finished screen, which fades out first, or from an empty one.
    private func play(fromRest: Bool) {
        let lead = fromRest ? M.out + M.gap : 0
        let length = reduceMotion ? M.fade : M.end
        let start = Date.now.addingTimeInterval(lead)
        let ended = start.addingTimeInterval(length)
        zero = start
        stir(for: lead + length + M.settle)
        runTask?.cancel()
        runTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(lead + length))
            guard !Task.isCancelled else { return }
            // The entrance ends at the last step: it hands the list over there, and the list goes back.
            if !reduceMotion {
                let rest = Double(index) * Metrics.row
                scroll = Glide(from: limit, to: 0, velocity: 0, start: ended, damping: M.returnDamping)
                fill = Move(from: span, to: rest, start: ended, duration: M.returnIn)
            }
            zero = nil
        }
    }

    private func select(_ step: Int) {
        guard !playing else { return }
        selectHaptic.selectionChanged()
        reach(step)
        onSelect?(step)
    }

    private func advance() {
        touched()
        guard !playing else { return }
        pressHaptic.impactOccurred()
        guard index < last else {
            onDone?()
            return
        }
        let next = index + 1
        // The step it moved to is brought under the one before it at the top.
        scrollTo(min(Double(index) * Metrics.row, limit))
        reach(next)
        onContinue?(next)
    }

    private func tap(at location: CGPoint) {
        touched()
        let k = Int(((location.y + scroll.value(at: .now)) / Metrics.row).rounded(.down))
        if k >= 0, k <= last { select(k) }
    }

    private func drag(_ value: DragGesture.Value) {
        if !dragging {
            dragging = true
            touched()
            // The list is taken where it is, not where the finger was when the drag was recognised.
            grab = playing ? nil : Self.unbanded(scroll.value(at: .now), limit) + value.translation.height
        }
        guard let grab else { return }
        scroll = .still(Self.banded(grab - value.translation.height, limit))
    }

    private func release(_ value: DragGesture.Value) {
        dragging = false
        guard grab != nil else { return }
        grab = nil
        let now = scroll.to
        let velocity = -value.velocity.height
        let to = min(max(now + velocity * M.glide, 0), limit)
        scroll = Glide(from: now, to: to, velocity: velocity, start: .now, damping: M.glideDamping)
        stir(for: M.settle)
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            while zero != nil, !Task.isCancelled { try? await Task.sleep(for: .seconds(0.1)) }
            guard !Task.isCancelled else { return }
            // The loop starts over from the screen it was given.
            reach(first)
            scrollTo(0)
            try? await Task.sleep(for: .seconds(M.settle))
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            try? await clock.sleep(until: origin + .seconds(M.playAt))
            guard !Task.isCancelled else { return }
            play(fromRest: true)
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }

    // MARK: Geometry

    /// The path from the pin: left, down, right, and down the rail to `end`.
    private static func path(to end: Double) -> Path {
        let x = Metrics.bracketX, y = Metrics.pinY, drop = Metrics.pinY + Metrics.bracketDrop, r = Metrics.corner
        var path = Path()
        path.move(to: CGPoint(x: Metrics.pinX, y: y))
        path.addLine(to: CGPoint(x: x + r, y: y))
        path.addArc(tangent1End: CGPoint(x: x, y: y), tangent2End: CGPoint(x: x, y: y + r), radius: r)
        path.addLine(to: CGPoint(x: x, y: drop - r))
        path.addArc(tangent1End: CGPoint(x: x, y: drop), tangent2End: CGPoint(x: x + r, y: drop), radius: r)
        path.addLine(to: CGPoint(x: Metrics.railX - r, y: drop))
        path.addArc(tangent1End: CGPoint(x: Metrics.railX, y: drop), tangent2End: CGPoint(x: Metrics.railX, y: drop + r), radius: r)
        path.addLine(to: CGPoint(x: Metrics.railX, y: end))
        return path
    }

    /// The point at a length along the path: three straight runs, three corners, then the rail.
    private static func pathPoint(_ length: Double) -> CGPoint {
        let x = Metrics.bracketX, y = Metrics.pinY, drop = Metrics.pinY + Metrics.bracketDrop, r = Metrics.corner
        var l = length
        if l <= Metrics.runA { return CGPoint(x: Metrics.pinX - l, y: y) }
        l -= Metrics.runA
        if l <= Metrics.turn { return CGPoint(x: x + r - r * sin(l / r), y: y + r - r * cos(l / r)) }
        l -= Metrics.turn
        if l <= Metrics.runB { return CGPoint(x: x, y: y + r + l) }
        l -= Metrics.runB
        if l <= Metrics.turn { return CGPoint(x: x + r - r * cos(l / r), y: drop - r + r * sin(l / r)) }
        l -= Metrics.turn
        if l <= Metrics.runC { return CGPoint(x: x + r + l, y: drop) }
        l -= Metrics.runC
        if l <= Metrics.turn { return CGPoint(x: Metrics.railX - r + r * sin(l / r), y: drop + r - r * cos(l / r)) }
        return CGPoint(x: Metrics.railX, y: drop + r + l - Metrics.turn)
    }

    /// A pull past an edge moves the list less and less.
    private static func banded(_ raw: Double, _ max: Double) -> Double {
        if raw < 0 { return -(-raw / (1 - raw / M.band)) }
        if raw > max { return max + (raw - max) / (1 + (raw - max) / M.band) }
        return raw
    }

    private static func unbanded(_ shown: Double, _ max: Double) -> Double {
        let past = shown < 0 ? -shown : shown > max ? shown - max : 0
        let pulled = past / (1 - min(past, M.band - 1) / M.band)
        return shown < 0 ? -pulled : shown > max ? max + pulled : shown
    }

    // MARK: Types

    private struct Layout {
        let width: Double
        let height: Double
        let stageTop: Double
        let stageHeight: Double
        let panel: Double
        let span: Double
        let maxScroll: Double
        /// The entrance starts with the heading's first line at mid-screen, this far under its place.
        let rise: Double
        let riseSpan: Double
        let total: Double
        let textWidth: Double

        init(width: Double, height: Double, top: Double, bottom: Double, span: Double) {
            self.width = width
            self.height = height
            self.span = span
            stageTop = top + Metrics.stageTop
            stageHeight = height - stageTop
            panel = Metrics.panelPad + Metrics.button + max(bottom, 16) + Metrics.buttonLift
            maxScroll = max(0, Metrics.nodeInset + span + Metrics.tail - (stageHeight - panel - Metrics.listTop))
            rise = max(0, height / 2 - stageTop - Metrics.pinY)
            riseSpan = min(rise, span)
            total = Metrics.pathToNode + span + Metrics.overscroll
            textWidth = max(0, min(Metrics.textWidth, width - Metrics.textX - Metrics.side))
        }
    }

    private struct Frame {
        var offset: Double
        var rise = 0.0
        var heading = 1.0
        var panelSlide = 0.0
        var veil = 0.0
        /// For each step, how much of it shows and how far its node has turned.
        var shown: [Double] = []
        var reached: [Double] = []
        var solid = 0.0
        var ghost = 0.0
        var ghostOpacity = 0.0
        var greyFrom = 0.0
        var greyTo = 0.0
        var pin = CGPoint.zero
        var pinRound = 0.0
        var pinScale = 0.0
        var pinShown = false
    }

    /// A value going from one place to another at a constant speed.
    private struct Move {
        let from: Double
        let to: Double
        let start: Date
        let duration: Double

        func progress(at date: Date) -> Double {
            duration > 0 ? M.clamp(date.timeIntervalSince(start) / duration) : 1
        }

        func value(at date: Date) -> Double {
            from + (to - from) * progress(at: date)
        }
    }

    /// The list's scroll position on a spring of mass 1.
    private struct Glide {
        let from: Double
        let to: Double
        let velocity: Double
        let start: Date
        let damping: Double

        static func still(_ value: Double) -> Glide {
            Glide(from: value, to: value, velocity: 0, start: .distantPast, damping: M.glideDamping)
        }

        func value(at date: Date) -> Double {
            let t = date.timeIntervalSince(start)
            if t <= 0 { return from }
            if from == to, velocity == 0 { return to }
            let natural = M.stiffness.squareRoot()
            let ratio = damping / (2 * natural)
            let x = from - to
            if ratio < 1 {
                let damped = natural * (1 - ratio * ratio).squareRoot()
                return to + exp(-ratio * natural * t) * (x * cos(damped * t) + (velocity + ratio * natural * x) / damped * sin(damped * t))
            }
            return to + exp(-natural * t) * (x + (velocity + natural * x) * t)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.8 : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
        }
    }

    private enum Palette {
        static let screen = Color.white
        static let ink = Color(hex: 0x1f1f24)
        static let mute = Color(hex: 0x6b6b76)
        static let track = Color(hex: 0xe1e1e1)
        static let outline = Color(hex: 0xd5d5dc)
    }

    private enum Metrics {
        // The top bar, below the safe area.
        static let side = 20.0
        static let barTop = 11.0
        static let bar = 32.0
        static let round = 26.0
        // The stage holds the heading, the path and the list. Everything below is measured from
        // its top left corner, which sits this far under the safe area.
        static let stageTop = 59.0
        static let headingX = 86.0
        static let headingLine = 34.0
        // The path: from the pin beside the heading's first line, left, down, right, and down the rail.
        static let pinX = 72.0
        static let pinY = 17.0
        static let bracketX = 31.0
        static let bracketDrop = 68.0
        static let corner = 10.0
        static let railX = 98.0
        static let stroke = 2.5
        static let rail = 2.0
        // The list scrolls under this line; a step's node sits `nodeInset` under the top of its row.
        static let listTop = 113.0
        static let nodeInset = 16.0
        static let row = 120.0
        // What the last step needs under its node: three lines of description and some air.
        static let tail = 96.0
        static let numberX = 63.0
        static let textX = 129.0
        static let textWidth = 220.0
        static let node = 16.0
        static let hollow = 14.0
        static let dot = 5.0
        // The pin is a node with one corner left sharp, turned to point down, and a larger dot.
        static let pinTip = 2.0
        static let pinDot = 6.0
        static let button = 44.0
        static let panelPad = 12.0
        static let buttonLift = 8.0

        static let runA = pinX - bracketX - corner
        static let runB = bracketDrop - corner * 2
        static let runC = railX - bracketX - corner * 2
        static let turn = Double.pi * corner / 2
        // The length of the path down to the list's top edge, and to the first node.
        static let pathToList = runA + runB + runC + turn * 3 + (listTop - pinY - bracketDrop - corner)
        static let pathToNode = pathToList + nodeInset
        // The path is drawn this much longer than the list, for a pull past the top.
        static let overscroll = 240.0

        // What a text's own line height leaves of the line it is set on.
        static let headingGap = headingLine - UIFont.systemFont(ofSize: 26, weight: .medium).lineHeight
        static let descriptionGap = 19 - UIFont.systemFont(ofSize: 14).lineHeight
    }

    /// Seconds. The loop rests on the finished screen, clears it, and plays the entrance again.
    private enum M {
        static let loop = 10.0
        static let playAt = 1.5
        // Before the entrance the finished screen fades to white, then stays empty for a beat.
        static let out = 0.45
        static let gap = 0.1
        // The entrance, from the moment the screen is empty: the heading and the first step fade
        // in at mid-screen while the pin pops and the path draws itself; the pin rides the path to
        // the first node; the screen rises as the accent runs down the rail and brings in a step at
        // each node; Continue slides up; then the list goes back to the reached step.
        static let headIn = 0.4
        static let pop = 0.45
        static let draw = 0.5
        static let greyAt = 0.1
        static let grey = 0.5
        static let travelAt = 0.4
        static let travel = 0.8
        static let tourAt = 1.2
        static let tour = 4.6
        // The share of the tour spent speeding up, and slowing down.
        static let ramp = 0.2
        static let buttonAt = 5.3
        static let buttonIn = 0.4
        static let end = 6.3
        // After the entrance the accent takes this long to go back up to the reached step.
        static let returnIn = 1.2
        // A step starts to show when the accent is `lead` above its node and is whole `reveal` further on.
        static let lead = 56.0
        static let reveal = 44.0
        static let slide = 12.0
        // A node turns over the last points before the accent's end reaches its centre.
        static let nodeSpan = 10.0
        // Points a second, for the accent on the rail after a tap.
        static let fillSpeed = 300.0
        // With reduced motion a change is a fade this long.
        static let fade = 0.25
        // After a touch the loop waits, puts the screen back and waits for that to end.
        static let userHold = 3.0
        static let settle = 2.2
        // A flick carries the list this many seconds of its speed further.
        static let glide = 0.3
        static let band = 180.0
        // A flick settles without a bounce (critical damping); a scripted move overshoots a little.
        static let stiffness = 90.0
        static let glideDamping = 2 * 90.0.squareRoot()
        static let returnDamping = 14.0

        static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }

        static func easeOut(_ t: Double) -> Double {
            let u = 1 - clamp(t)
            return 1 - u * u * u
        }

        static func easeInOut(_ t: Double) -> Double {
            let u = clamp(t)
            return u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u)
        }

        /// Ease-out that goes a little past 1 and comes back.
        static func overshoot(_ t: Double) -> Double {
            let u = clamp(t) - 1
            return 1 + 2.70158 * u * u * u + 1.70158 * u * u
        }

        static func smooth(_ t: Double) -> Double {
            let u = clamp(t)
            return u * u * (3 - 2 * u)
        }

        /// A constant speed with a short ramp at each end.
        static func steady(_ t: Double) -> Double {
            let u = clamp(t)
            let area = 2 * ramp * (1 - ramp)
            if u < ramp { return u * u / area }
            if u > 1 - ramp { return 1 - (1 - u) * (1 - u) / area }
            return (u - ramp / 2) / (1 - ramp)
        }

        /// How far a node has turned for an accent that reaches `fill` points under the first node.
        static func reached(by fill: Double, _ index: Int) -> Double {
            clamp((fill - Double(index) * Metrics.row) / nodeSpan + 1)
        }
    }
}
