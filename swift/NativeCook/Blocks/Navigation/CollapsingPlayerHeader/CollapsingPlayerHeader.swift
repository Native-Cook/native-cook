import SwiftUI

/// A run of the notes' text; a link is drawn in white.
struct CollapsingPlayerHeaderSpan: Equatable {
    var text: String
    var link = false

    static let samples: [[CollapsingPlayerHeaderSpan]] = [
        [.init(text: "Every winter the longest ceremony of the season runs past midnight, and every winter somebody promises to fix it. This year the hosts, the caterers and one very tired stage manager let us follow them from the first rehearsal to the last trophy of the night.")],
        [.init(text: "We count the speeches, time the standing ovations and ask why the technical awards always land after eleven. The answer has less to do with television than with a seating chart nobody has dared to redraw in forty years.")],
        [.init(text: "In this episode: the rehearsal nobody attends, why the envelope is still made of paper, the orchestra\u{2019}s polite way of ending a speech, and what happens to four thousand flowers at two in the morning.")],
        [.init(text: "Chapters: 00:00 The rehearsal. 06:10 Paper envelopes. 13:45 The seating chart. 21:30 The play-off music. 24:05 Four thousand flowers.")],
        [.init(text: "This episode was produced by Mara Lindqvist, edited by Tomas Oyelaran, fact-checked by Ines Carvalho, engineered by Dev Raman and hosted by Noor Halvorsen.")],
        [.init(text: "Transcript at "), .init(text: "fieldnotes.example/longest-night", link: true)],
        [.init(text: "Support Field Notes Daily by becoming a member today: "), .init(text: "fieldnotes.example/members", link: true)],
        [.init(text: "Photo: an empty ballroom on the morning after, with one silver balloon still resting against the ceiling. Photo by Jun Aoki for Field Notes.")],
        [.init(text: "Correction: an earlier version of these notes gave the running time as four hours and twelve minutes. It was four hours and twenty-one, not counting the encore.")],
        [.init(text: "Music in this episode by the Halvard Quartet. Additional reporting by Petra Voss and Sami Okafor. Special thanks to everyone who stayed until the lights came up.")],
        [.init(text: "Field Notes Daily is made in a small studio above a bakery. If an episode smells faintly of cinnamon, that is why. New episodes arrive every weekday morning.")],
        [.init(text: "Learn more about your ad choices. Visit "), .init(text: "fieldnotes.example/adchoices", link: true)],
    ]
}

/// A podcast screen whose top is a full player: date, show, a large title, a progress track and
/// the transport row. Scrolling the notes folds it into a one-line mini player that stays as the
/// header: the track travels up and narrows, the cover grows in at its left, the big button shrinks
/// to its right. Everything is placed from one number, the scroll offset.
struct CollapsingPlayerHeader: View {
    var date = "Yesterday"
    var show = "Field Notes Daily"
    /// The episode's title; a line break is kept.
    var title = "The longest night\nof the awards\nseason"
    /// The episode's length and where playback starts, in seconds.
    var duration = 1706.0
    var position = 519.0
    var notesTitle = "Episode Notes"
    /// The two tabs over the notes: the first shows them, the second only their links.
    var tabs = ["Description", "Links"]
    /// The notes, a list of paragraphs.
    var notes = CollapsingPlayerHeaderSpan.samples
    /// The colour of the cover and of the mark beside the notes' heading.
    var accent = Color(hex: 0xF2E94E)
    /// Scrolls the notes by itself every `CollapsingPlayerHeaderMotion.loop` seconds: the player folds, the notes run on, and it opens again.
    var autoPlay = false
    var onPlayingChange: ((Bool) -> Void)? = nil
    /// Called with the new position, in seconds, when a skip button is pressed.
    var onSeek: ((Double) -> Void)? = nil
    var onCollapsedChange: ((Bool) -> Void)? = nil
    var onBookmark: (() -> Void)? = nil
    var onCast: (() -> Void)? = nil
    var onMore: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// What moves the notes' scroll offset.
    @State private var motion = Motion.rest(0)
    @State private var playing = true
    /// Playback: the position at `baseAt`, running on from there; nil until something changes it.
    @State private var base: Double? = nil
    @State private var baseAt = Date.now
    @State private var tab = 0
    @State private var collapsed = false
    @State private var notesHeight = 0.0
    /// The offset the finger took hold of plus the finger's travel at that moment; nil for a touch that has not moved yet.
    @State private var grab: Double? = nil
    @State private var held = false
    @GestureState private var pressing = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Reduced motion: the screen is hidden for the moment the layout changes.
    @State private var faded = false
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private enum Motion: Equatable {
        /// Still, or under a finger. Below 0 and past the end while a pull stretches it.
        case rest(Double)
        /// The autoPlay loop, counted from this moment.
        case auto(Date)
        /// The snap. `riding`: past its target the notes do not scroll on, the screen rides up instead.
        case spring(from: Double, to: Double, speed: Double, start: Date, riding: Bool)
        /// The free scroll after a flick, between two offsets.
        case decay(from: Double, speed: Double, start: Date, low: Double, high: Double)

        var moves: Bool {
            if case .rest = self { false } else { true }
        }
    }

    /// The screen's measures: centres of the notes' heading and of the transport row, open and folded.
    private struct Layout {
        let width: Double
        let height: Double
        let top: Double
        let headingOpen: Double
        let headingMini: Double
        let rowOpen: Double
        let rowMini: Double
        /// The scroll that folds the player, and the offset at which the notes end.
        let travel: Double
        let end: Double
        /// The folded player but for its button: a tap there opens it.
        let miniRow: CGRect

        init(size: CGSize, top: Double, bottom: Double, notesHeight: Double) {
            width = size.width
            height = size.height
            self.top = top
            headingOpen = size.height - Metrics.headingUp
            headingMini = top + Metrics.headingCenter
            rowOpen = headingOpen - Metrics.rowAbove
            rowMini = top + Metrics.miniCenter
            travel = headingOpen - headingMini
            end = travel + max(0, headingMini + Metrics.notesGapMini + notesHeight + Metrics.notesEnd + bottom - size.height)
            miniRow = CGRect(
                x: Metrics.margin, y: rowMini - Metrics.cover / 2,
                width: size.width - Metrics.margin - Metrics.miniRight, height: Metrics.cover
            )
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let layout = Layout(size: size, top: insets.top, bottom: insets.bottom, notesHeight: notesHeight)

            TimelineView(.animation(paused: !(autoPlay || playing || motion.moves))) { timeline in
                let now = offsets(at: timeline.date, layout)
                let raw = M.clamp01(now.scroll / layout.travel)
                // Reduced motion shows one layout or the other.
                let p = reduceMotion ? (raw >= M.snapAt ? 1.0 : 0.0) : raw

                screen(p: p, scroll: now.scroll, shift: -min(now.scroll, 0) - now.lift, seconds: seconds(at: timeline.date), layout)
                    .onChange(of: p > 0.5) { _, folded in flipped(folded) }
            }
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .simultaneousGesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .global)
                    .updating($pressing) { _, state, _ in state = true }
                    .onChanged { dragged($0.translation.height, layout) }
                    .onEnded { dropped(speed: -$0.velocity.height, from: $0.startLocation, layout) }
            )
            // A touch the system takes away never ends: it is let go here.
            .onChange(of: pressing) { _, down in
                if !down { dropped(speed: 0, from: nil, layout) }
            }
        }
        .background(Palette.page.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The screen

    private func screen(p: Double, scroll: Double, shift: Double, seconds: Int, _ l: Layout) -> some View {
        let row = M.mix(l.rowOpen, l.rowMini, p) + shift
        let track = row - M.mix(Metrics.trackAbove - Metrics.rowAbove, Metrics.trackRise, M.smooth(p, M.dropFrom, 1))
        let titleLeft = 1 - M.clamp01(p / M.titleGone)
        let squeeze = M.smooth(p, M.squeezeFrom, M.squeezeTo)
        let left = M.mix(Metrics.margin, Metrics.miniLeft, squeeze)
        let right = M.mix(l.width - Metrics.margin, l.width - Metrics.miniRight, squeeze)
        let buttonX = M.mix(l.width / 2, l.width - Metrics.margin - Metrics.small / 2, M.smooth(p, 0, 1))
        let sheetY = l.headingOpen - p * l.travel - max(0, scroll - l.travel) + shift - Metrics.headingLine / 2

        return ZStack(alignment: .topLeading) {
            sheet(p: p, l)
                .frame(width: l.width - Metrics.margin * 2, alignment: .topLeading)
                .offset(x: Metrics.margin, y: sheetY)

            // The notes pass under the mini row: page colour, then a fade.
            VStack(spacing: 0) {
                Palette.page.frame(height: l.top + Metrics.header)
                LinearGradient(colors: [Palette.page, Palette.page.opacity(0)], startPoint: .top, endPoint: .bottom)
                    .frame(height: Metrics.headerFade)
            }
            .frame(width: l.width)
            .allowsHitTesting(false)

            titles
                .frame(width: l.width - Metrics.margin * 2, alignment: .leading)
                .opacity(titleLeft * titleLeft)
                .offset(x: Metrics.margin, y: l.top + Metrics.dateTop - M.titleSpeed * p * l.travel + shift)
                .allowsHitTesting(false)

            if collapsed {
                // The mini row's tap is read from the screen's own gesture; this is its place for assistive touch.
                Color.clear
                    .frame(width: l.miniRow.width, height: l.miniRow.height)
                    .offset(x: l.miniRow.minX, y: l.miniRow.minY)
                    .allowsHitTesting(false)
                    .accessibilityElement()
                    .accessibilityLabel("\(show). Open the player")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { expand(l) }
            }

            progress(left: left, right: right, y: track, p: p, seconds: seconds)

            cover
                .scaleEffect(M.mix(0.3, 1, M.smooth(p, M.coverFrom, 1)))
                .opacity(M.smooth(p, M.coverFrom, 1))
                .offset(x: Metrics.margin, y: row - Metrics.cover / 2)
                .allowsHitTesting(false)

            transport(p: p, buttonX: buttonX, row: row, width: l.width)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .opacity(faded ? 0 : 1)
    }

    private var titles: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(date)
                .collapsingPlayerHeaderType(14, .regular, line: 18)
                .foregroundStyle(Palette.mute)
                .lineLimit(1)
            Text(show)
                .collapsingPlayerHeaderType(14, .semibold, line: 18)
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .padding(.top, 2)
            Text(title)
                .collapsingPlayerHeaderType(Metrics.titleSize, .semibold, line: Metrics.titleLine)
                .tracking(-0.6)
                .foregroundStyle(Palette.ink)
                .lineLimit(3)
                .padding(.top, 6)
                .accessibilityAddTraits(.isHeader)
        }
    }

    /// The track with the two times under its ends; they keep to its ends as it narrows.
    private func progress(left: Double, right: Double, y: Double, p: Double, seconds: Int) -> some View {
        let labelY = y + M.mix(Metrics.labelDrop, Metrics.labelDropMini, p) - Metrics.labelLine / 2
        let scale = M.mix(1, Metrics.labelShrink, p)
        let played = min(1, Double(seconds) / max(1, duration))

        return Group {
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.rule)
                Capsule().fill(Palette.fill).frame(width: (right - left) * played)
            }
            .frame(width: right - left, height: Metrics.track)
            .offset(x: left, y: y - Metrics.track / 2)
            .accessibilityElement()
            .accessibilityLabel("Position")
            .accessibilityValue("\(Self.clockText(Double(seconds))) of \(Self.clockText(duration))")

            Text(Self.clockText(Double(seconds)))
                .frame(width: Metrics.labelWidth, height: Metrics.labelLine, alignment: .leading)
                .scaleEffect(scale, anchor: .leading)
                .offset(x: left, y: labelY)
            Text("-" + Self.clockText(duration - Double(seconds)))
                .frame(width: Metrics.labelWidth, height: Metrics.labelLine, alignment: .trailing)
                .scaleEffect(scale, anchor: .trailing)
                .offset(x: right - Metrics.labelWidth, y: labelY)
        }
        .font(.system(size: 12))
        .monospacedDigit()
        .foregroundStyle(Palette.mute)
        .lineLimit(1)
    }

    private var cover: some View {
        RoundedRectangle(cornerRadius: 5)
            .fill(accent)
            .overlay(alignment: .topLeading) {
                ForEach(Metrics.coverLines, id: \.self) { line in
                    RoundedRectangle(cornerRadius: 1)
                        .fill(Palette.page)
                        .frame(width: line[2], height: 3.5)
                        .offset(x: line[0], y: line[1])
                }
            }
            .frame(width: Metrics.cover, height: Metrics.cover)
            .accessibilityHidden(true)
    }

    private func transport(p: Double, buttonX: Double, row: Double, width: Double) -> some View {
        let sides = 1 - M.clamp01(p / M.sidesGone)
        let gone = M.clamp01(p / M.skipsGone)
        let reach = Metrics.skipGap * M.mix(1, 0.6, gone)

        return Group {
            // The glyphs' outer edges, not their boxes, stand on the margins.
            Button { onBookmark?() } label: {
                CollapsingPlayerHeaderBookmark()
                    .stroke(Palette.glyphDim, style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
                    .frame(width: 24, height: 24)
                    .frame(width: Metrics.hit, height: Metrics.hit)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Bookmark")
            .opacity(sides)
            .offset(x: Metrics.margin - 17, y: row - Metrics.hit / 2)

            Button { onCast?() } label: {
                CollapsingPlayerHeaderCast()
                    .frame(width: 24, height: 24)
                    .frame(width: Metrics.hit, height: Metrics.hit)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Play on another device")
            .opacity(sides)
            .offset(x: width - Metrics.margin + 15 - Metrics.hit, y: row - Metrics.hit / 2)

            Button { skip(-15) } label: { CollapsingPlayerHeaderSkip(forward: false) }
                .accessibilityLabel("Back 15 seconds")
                .scaleEffect(M.mix(1, 0.8, gone))
                .opacity(1 - gone)
                .offset(x: buttonX - reach - Metrics.hit / 2, y: row - Metrics.hit / 2)

            Button { skip(15) } label: { CollapsingPlayerHeaderSkip(forward: true) }
                .accessibilityLabel("Forward 15 seconds")
                .scaleEffect(M.mix(1, 0.8, gone))
                .opacity(1 - gone)
                .offset(x: buttonX + reach - Metrics.hit / 2, y: row - Metrics.hit / 2)
        }
        .buttonStyle(DimStyle())
        .allowsHitTesting(!collapsed)
        .overlay(alignment: .topLeading) {
            Button { toggle() } label: {
                CollapsingPlayerHeaderGlyph(morph: playing ? 0 : 1)
                    .fill(Palette.ink)
                    .stroke(Palette.ink, style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
                    .animation(reduceMotion ? nil : .easeInOut(duration: M.morph), value: playing)
                    .frame(width: Metrics.big, height: Metrics.big)
                    .background(Circle().fill(Palette.button))
                    .contentShape(Circle())
            }
            .buttonStyle(DimStyle())
            .accessibilityLabel(playing ? "Pause" : "Play")
            .scaleEffect(M.mix(1, Metrics.small / Metrics.big, M.smooth(p, 0, 1)))
            .offset(x: buttonX - Metrics.big / 2, y: row - Metrics.big / 2)
        }
    }

    /// What scrolls: the heading, the tabs that appear under it, and the notes.
    private func sheet(p: Double, _ l: Layout) -> some View {
        let slide = M.smooth(p, M.slideFrom, M.slideTo)
        let room = M.smooth(p, M.gapFrom, M.gapTo)

        return ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(accent)
                    .frame(width: 10, height: 14)
                    .opacity(1 - M.smooth(p, M.markFrom, M.markGone))
                    .padding(.trailing, Metrics.markSpace - 10)
                Text(notesTitle)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                    .offset(x: -Metrics.markSpace * slide)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                Button { onMore?() } label: {
                    CollapsingPlayerHeaderDots()
                        .fill(Palette.mute)
                        .frame(width: 24, height: 24)
                        .contentShape(Rectangle().inset(by: -12))
                }
                .buttonStyle(DimStyle())
                .accessibilityLabel("More")
            }
            .frame(height: Metrics.headingLine)

            HStack(spacing: 0) {
                ForEach(Array(tabs.enumerated()), id: \.offset) { index, label in
                    Button { choose(index, l) } label: {
                        Text(label.uppercased())
                            .font(.system(size: 9, weight: .semibold))
                            .tracking(0.5)
                            .foregroundStyle(index == tab ? Palette.ink : Palette.tabOff)
                            .lineLimit(1)
                            .padding(.horizontal, 9)
                            .frame(height: Metrics.tabHeight)
                            .background(RoundedRectangle(cornerRadius: 5).fill(index == tab ? Palette.button : .clear))
                            .contentShape(Rectangle().inset(by: -8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(index == tab ? [.isSelected] : [])
                }
            }
            .opacity(M.smooth(p, M.tabsFrom, 1))
            .allowsHitTesting(collapsed)
            .offset(y: Metrics.headingLine / 2 + Metrics.tabsBelow - Metrics.tabHeight / 2)

            CollapsingPlayerHeaderNotes(paragraphs: shownNotes)
                .onGeometryChange(for: Double.self) { $0.size.height } action: { notesHeight = $0 }
                .offset(y: Metrics.headingLine / 2 + M.mix(Metrics.notesGap, Metrics.notesGapMini, room))
        }
    }

    /// The second tab lists the links of the notes, one to a line.
    private var shownNotes: [[CollapsingPlayerHeaderSpan]] {
        tab == 0 ? notes : notes.flatMap { paragraph in paragraph.filter(\.link).map { [$0] } }
    }

    // MARK: Behaviour

    /// The scroll offset at a moment, and how far the snap is past the folded player.
    private func offsets(at date: Date, _ l: Layout) -> (scroll: Double, lift: Double) {
        switch motion {
        case .rest(let at):
            return (at, 0)
        case .auto(let origin):
            let t = Self.loopTime(date, origin)
            let value = M.script(t, travel: l.travel, end: l.end)
            return t < M.onAt ? (min(value, l.travel), max(0, value - l.travel)) : (value, 0)
        case .spring(let from, let to, let speed, let start, let riding):
            let t = date.timeIntervalSince(start)
            let value = t >= M.settle ? to : M.spring(max(0, t), from: from, to: to, speed: speed)
            return riding ? (min(value, to), max(0, value - to)) : (value, 0)
        case .decay(let from, let speed, let start, let low, let high):
            let fall = exp(-M.decayRate * max(0, date.timeIntervalSince(start)))
            return (min(high, max(low, from + speed / M.decayRate * (1 - fall))), 0)
        }
    }

    private func seconds(at date: Date) -> Int {
        let current: Double
        if case .auto(let origin) = motion {
            // Every loop plays the same stretch of the episode.
            current = position + Self.loopTime(date, origin)
        } else {
            current = (base ?? position) + (playing ? max(0, date.timeIntervalSince(baseAt)) : 0)
        }
        return Int(min(duration, current + 0.001))
    }

    private static func loopTime(_ date: Date, _ origin: Date) -> Double {
        max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop)
    }

    /// A touch takes over at once, wherever the offset is; playback goes on from where the loop had it.
    private func takeOver(_ l: Layout) {
        let now = Date.now
        if case .auto = motion {
            base = Double(seconds(at: now))
            baseAt = now
        }
        motion = .rest(offsets(at: now, l).scroll)
    }

    private func dragged(_ travelled: Double, _ l: Layout) {
        if !held {
            held = true
            touches += 1
            takeOver(l)
        }
        guard case .rest(let at) = motion else { return }
        if grab == nil {
            guard abs(travelled) > Metrics.slop else { return }
            grab = min(l.end, max(0, at)) + travelled
        }
        let wanted = (grab ?? at) - travelled
        motion = .rest(wanted < 0 ? -M.rubber(-wanted, room: l.height) : wanted > l.end ? l.end + M.rubber(wanted - l.end, room: l.height) : wanted)
    }

    /// The finger left with `speed` (points a second, positive towards the notes' end); it came down at `from`.
    private func dropped(speed: Double, from: CGPoint?, _ l: Layout) {
        guard held else { return }
        let moved = grab != nil
        held = false
        grab = nil
        touches += 1
        guard case .rest(let at) = motion else { return }
        if !moved, collapsed, let from, l.miniRow.contains(from) {
            expand(l)
            return
        }
        // A touch that never became a drag still has to leave the player open or folded.
        let velocity = moved ? speed : 0
        if at < l.travel {
            let target = at + velocity * M.project > M.snapAt * l.travel ? l.travel : 0
            if at != target { spring(from: at, to: target, speed: velocity, l) }
        } else if at > l.end {
            spring(from: at, to: l.end, speed: 0, l)
        } else if abs(velocity) >= M.restSpeed {
            let start = Date.now
            let stopsAfter = log(abs(velocity) / M.restSpeed) / M.decayRate
            motion = .decay(from: at, speed: velocity, start: start, low: l.travel, high: l.end)
            settle(after: stopsAfter, at: offsets(at: start.addingTimeInterval(stopsAfter), l).scroll)
        }
    }

    private func spring(from: Double, to: Double, speed: Double, _ l: Layout) {
        if reduceMotion {
            motion = .rest(to)
            return
        }
        motion = .spring(from: from, to: to, speed: speed, start: .now, riding: to == l.travel)
        settle(after: M.settle, at: to)
    }

    /// When a motion has run out the offset is still again, which lets the clock stop.
    private func settle(after seconds: Double, at value: Double) {
        let expected = motion
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            if motion == expected { motion = .rest(value) }
        }
    }

    private func expand(_ l: Layout) {
        touches += 1
        spring(from: offsets(at: .now, l).scroll, to: 0, speed: 0, l)
    }

    private func flipped(_ folded: Bool) {
        collapsed = folded
        onCollapsedChange?(folded)
        // Reduced motion: the other layout fades in instead of travelling.
        guard reduceMotion else { return }
        faded = true
        Task { @MainActor in
            withAnimation(.easeOut(duration: M.reducedFade)) { faded = false }
        }
    }

    private func toggle() {
        let now = Date.now
        haptic.impactOccurred()
        base = Double(seconds(at: now))
        baseAt = now
        playing.toggle()
        onPlayingChange?(playing)
    }

    private func skip(_ by: Double) {
        let now = Date.now
        let next = min(duration, max(0, Double(seconds(at: now)) + by))
        haptic.impactOccurred()
        base = next
        baseAt = now
        onSeek?(next)
    }

    private func choose(_ index: Int, _ l: Layout) {
        guard index != tab else { return }
        haptic.impactOccurred()
        // The other list starts at its top.
        if offsets(at: .now, l).scroll > l.travel { motion = .rest(l.travel) }
        tab = index
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled, !held else { return }
            let now = Date.now
            let at: Double
            switch motion {
            case .rest(let value): at = value
            case .spring(_, let to, _, _, _): at = to
            case .decay(let from, let speed, let start, let low, let high):
                at = min(high, max(low, from + speed / M.decayRate * (1 - exp(-M.decayRate * now.timeIntervalSince(start)))))
            case .auto: return
            }
            if at != 0 {
                if reduceMotion {
                    motion = .rest(0)
                } else {
                    motion = .spring(from: at, to: 0, speed: 0, start: now, riding: false)
                    try? await Task.sleep(for: .seconds(M.settle))
                    guard !Task.isCancelled else { return }
                }
            }
            // The loop starts over: playback and the tabs are as they were given.
            playing = true
            tab = 0
        }
        motion = .auto(.now)
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private static func clockText(_ seconds: Double) -> String {
        let whole = Int(max(0, seconds.rounded()))
        return "\(whole / 60):" + (whole % 60 < 10 ? "0" : "") + "\(whole % 60)"
    }

    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.6 : 1)
        }
    }
}

/// The motion's numbers and curves, shared by the screen and its parts.
enum CollapsingPlayerHeaderMotion {
    /// autoPlay: one fold and one return every `loop` seconds.
    static let loop = 9.0
    // A pull that speeds up, then the snap takes over, as when a finger flicks and lets go.
    static let foldAt = 1.0
    static let foldPull = 0.3
    static let foldTo = 0.42
    // The notes run on by this much, slowly.
    static let onAt = 2.0
    static let onFor = 1.2
    static let onBy = 120.0
    // The way back: a pull down through the notes, let go halfway through the fold.
    static let backAt = 5.0
    static let backPull = 0.55
    static let backTo = 0.5
    /// After a touch autoPlay waits this long before it returns to the open player and starts over.
    static let userHold = 3.0

    // The fold, as fractions of its travel. The title block leaves 1.2 times as fast as the notes rise.
    static let titleSpeed = 1.2
    static let titleGone = 0.35
    static let sidesGone = 0.15
    static let skipsGone = 0.5
    static let squeezeFrom = 0.3
    static let squeezeTo = 0.8
    static let coverFrom = 0.4
    // The mark beside the heading goes, then the heading slides into its place.
    static let markFrom = 0.5
    static let markGone = 0.7
    static let slideFrom = 0.6
    static let slideTo = 0.9
    /// The track keeps its distance above the transport row until here, then comes down into it.
    static let dropFrom = 0.6
    // The notes make room under their heading, and the tabs fade into it.
    static let gapFrom = 0.6
    static let gapTo = 0.9
    static let tabsFrom = 0.85
    /// Let go past this much of the travel and the player folds; before it, it opens again.
    static let snapAt = 0.4
    /// Seconds of the release speed that count towards that choice.
    static let project = 0.15

    // The snap: a spring with a response of 0.35 s that passes its target by about 4 points.
    static let omega = 2 * Double.pi / 0.35
    static let zeta = 0.82
    static let settle = 0.7
    // The free scroll after a flick loses speed at this rate a second and stops below `restSpeed`.
    static let decayRate = 2.0
    static let restSpeed = 4.0
    static let rubberBand = 0.55
    static let morph = 0.2
    static let reducedFade = 0.25

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func mix(_ from: Double, _ to: Double, _ t: Double) -> Double { from + (to - from) * t }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    /// Where a spring released at `from` with `speed` is after `t` seconds on its way to `to`.
    static func spring(_ t: Double, from: Double, to: Double, speed: Double) -> Double {
        let damped = omega * (1 - zeta * zeta).squareRoot()
        let away = from - to
        let swing = (speed + zeta * omega * away) / damped
        return to + exp(-zeta * omega * t) * (away * cos(damped * t) + swing * sin(damped * t))
    }

    /// A pull past an end gives less and less.
    static func rubber(_ over: Double, room: Double) -> Double {
        (1 - 1 / (over * rubberBand / room + 1)) * room
    }

    /// The scroll offset autoPlay asks for `t` seconds into a loop; above `travel` before `onAt` is the snap's overshoot.
    static func script(_ t: Double, travel: Double, end: Double) -> Double {
        let on = min(onBy, end - travel)
        if t < foldAt { return 0 }
        if t < foldAt + foldPull {
            let u = (t - foldAt) / foldPull
            return foldTo * travel * u * u
        }
        if t < onAt {
            let at = t - foldAt - foldPull
            return at >= settle ? travel : spring(at, from: foldTo * travel, to: travel, speed: 2 * foldTo * travel / foldPull)
        }
        if t < backAt { return travel + on * smooth(t, onAt, onAt + onFor) }
        let from = travel + on
        let to = backTo * travel
        if t < backAt + backPull {
            let u = (t - backAt) / backPull
            return from + (to - from) * u * u
        }
        let at = t - backAt - backPull
        return at >= settle ? 0 : spring(at, from: to, to: 0, speed: 2 * (to - from) / backPull)
    }
}

private typealias M = CollapsingPlayerHeaderMotion

private enum Palette {
    static let page = Color(hex: 0x110E11)
    static let ink = Color.white
    static let mute = Color(hex: 0x8C8A8F)
    static let body = Color(hex: 0xB4B2B7)
    static let rule = Color(hex: 0x3A383C)
    static let fill = Color(hex: 0xC9C7CC)
    static let button = Color(hex: 0x2A282C)
    static let glyphDim = Color(hex: 0x5C5A5F)
    static let tabOff = Color(hex: 0x6C6A6F)
}

private enum Metrics {
    static let margin = 24.0
    /// The open player's title block, from the safe area's top.
    static let dateTop = 44.0
    static let titleSize = 40.0
    static let titleLine = 52.0
    // The open player's lower half hangs from the notes' heading, which sits this far above the
    // screen's bottom: the track and the transport row are this far above the heading's centre.
    static let headingUp = 100.0
    static let trackAbove = 202.0
    static let rowAbove = 95.0
    // The folded player, from the safe area's top: the mini row, the heading and the tabs under it.
    static let miniCenter = 60.0
    static let headingCenter = 135.0
    static let tabsBelow = 31.0
    static let headingLine = 20.0
    // From the heading's centre to the first line of the notes, open and folded (the tabs come between).
    static let notesGap = 16.0
    static let notesGapMini = 57.0
    static let notesEnd = 24.0
    static let paragraphGap = 10.0
    static let track = 4.0
    /// In the mini row the track sits a little above the centre, with the times under it.
    static let trackRise = 7.0
    static let labelLine = 16.0
    static let labelWidth = 80.0
    static let labelDrop = 20.0
    static let labelDropMini = 16.0
    static let labelShrink = 11.0 / 12.0
    static let cover = 48.0
    static let big = 76.0
    static let small = 40.0
    static let skip = 30.0
    static let skipGap = 85.0
    static let hit = 44.0
    static let markSpace = 18.0
    static let tabHeight = 22.0
    // Where the track ends in the mini row: after the cover, before the button.
    static let miniLeft = margin + cover + 18
    static let miniRight = margin + small + 24
    // The notes pass under the mini row: page colour down to here, then a fade.
    static let header = miniCenter + 34
    static let headerFade = 24.0
    /// A touch becomes a drag after this much travel.
    static let slop = 8.0
    /// The cover's three lines of "text": left edge, top, width.
    static let coverLines = [[7.0, 10.0, 22.0], [7.0, 17.0, 28.0], [7.0, 24.0, 16.0]]
}

private extension View {
    /// System type on a fixed line height, as the other stacks set it.
    func collapsingPlayerHeaderType(_ size: Double, _ weight: Font.Weight, line: Double) -> some View {
        let natural = UIFont.systemFont(ofSize: size).lineHeight
        return font(.system(size: size, weight: weight))
            .lineSpacing(line - natural)
            .padding(.vertical, (line - natural) / 2)
    }
}

/// The notes: paragraphs of grey text with their links in white.
struct CollapsingPlayerHeaderNotes: View, Equatable {
    let paragraphs: [[CollapsingPlayerHeaderSpan]]

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.paragraphGap) {
            ForEach(Array(paragraphs.enumerated()), id: \.offset) { _, paragraph in
                Text(Self.attributed(paragraph))
                    .collapsingPlayerHeaderType(14, .regular, line: 18)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private static func attributed(_ paragraph: [CollapsingPlayerHeaderSpan]) -> AttributedString {
        var out = AttributedString()
        for span in paragraph {
            var part = AttributedString(span.text)
            part.foregroundColor = span.link ? Palette.ink : Palette.body
            out.append(part)
        }
        return out
    }
}

/// The pause bars and the play triangle of the big button, each as two four-cornered shapes
/// (top left, top right, bottom right, bottom left) so one can turn into the other. 76-point box.
struct CollapsingPlayerHeaderGlyph: Shape {
    /// 0 is pause, 1 is play.
    var morph: Double

    var animatableData: Double {
        get { morph }
        set { morph = newValue }
    }

    static let pause: [[Double]] = [[26.5, 25, 33.5, 25, 33.5, 51, 26.5, 51], [42.5, 25, 49.5, 25, 49.5, 51, 42.5, 51]]
    static let play: [[Double]] = [[29, 24, 41, 31, 41, 45, 29, 52], [41, 31, 53, 38, 53, 38, 41, 45]]

    func path(in rect: CGRect) -> Path {
        let s = rect.width / 76
        var path = Path()
        for shape in 0..<2 {
            for corner in stride(from: 0, to: 8, by: 2) {
                let point = CGPoint(
                    x: rect.minX + M.mix(Self.pause[shape][corner], Self.play[shape][corner], morph) * s,
                    y: rect.minY + M.mix(Self.pause[shape][corner + 1], Self.play[shape][corner + 1], morph) * s
                )
                if corner == 0 { path.move(to: point) } else { path.addLine(to: point) }
            }
            path.closeSubpath()
        }
        return path
    }
}

/// A circular arrow round "15": back when it turns to the left, forward when mirrored.
struct CollapsingPlayerHeaderSkip: View {
    let forward: Bool

    var body: some View {
        ZStack {
            Arc().stroke(Palette.ink, style: StrokeStyle(lineWidth: 1.7 * Metrics.skip / 24, lineCap: .round))
            Head().fill(Palette.ink)
        }
        .scaleEffect(x: forward ? -1 : 1)
        .overlay {
            Text("15")
                .font(.system(size: 9.5, weight: .bold))
                .foregroundStyle(Palette.ink)
                .offset(y: Metrics.skip / 24)
        }
        .frame(width: Metrics.skip, height: Metrics.skip)
        .frame(width: Metrics.hit, height: Metrics.hit)
        .contentShape(Rectangle())
    }

    /// Three hundred degrees of a circle from the top, clockwise, in a 24-point box.
    private struct Arc: Shape {
        func path(in rect: CGRect) -> Path {
            let s = rect.width / 24
            var path = Path()
            path.addArc(
                center: CGPoint(x: rect.minX + 12 * s, y: rect.minY + 13 * s), radius: 8.5 * s,
                startAngle: .degrees(-90), endAngle: .degrees(210), clockwise: false
            )
            return path
        }
    }

    /// The arrowhead at the arc's start.
    private struct Head: Shape {
        func path(in rect: CGRect) -> Path {
            let s = rect.width / 24
            var path = Path()
            path.move(to: CGPoint(x: rect.minX + 8.2 * s, y: rect.minY + 4.5 * s))
            path.addLine(to: CGPoint(x: rect.minX + 12.4 * s, y: rect.minY + 1.6 * s))
            path.addLine(to: CGPoint(x: rect.minX + 12.4 * s, y: rect.minY + 7.4 * s))
            path.closeSubpath()
            return path
        }
    }
}

/// A bookmark's outline in a 24-point box.
struct CollapsingPlayerHeaderBookmark: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 24
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 7 * s, y: rect.minY + 4.5 * s))
        path.addLine(to: CGPoint(x: rect.minX + 17 * s, y: rect.minY + 4.5 * s))
        path.addLine(to: CGPoint(x: rect.minX + 17 * s, y: rect.minY + 20 * s))
        path.addLine(to: CGPoint(x: rect.minX + 12 * s, y: rect.minY + 16 * s))
        path.addLine(to: CGPoint(x: rect.minX + 7 * s, y: rect.minY + 20 * s))
        path.closeSubpath()
        return path
    }
}

/// Two arcs of sound over a triangle, in a 24-point box: play on another device.
struct CollapsingPlayerHeaderCast: View {
    var body: some View {
        Canvas { context, size in
            let s = size.width / 24
            for radius in [8.0, 4.5] {
                var arc = Path()
                arc.addArc(center: CGPoint(x: 12 * s, y: 11.5 * s), radius: radius * s, startAngle: .degrees(150), endAngle: .degrees(390), clockwise: false)
                context.stroke(arc, with: .color(Palette.glyphDim), style: StrokeStyle(lineWidth: 1.6 * s, lineCap: .round))
            }
            var base = Path()
            base.move(to: CGPoint(x: 12 * s, y: 14.5 * s))
            base.addLine(to: CGPoint(x: 16.5 * s, y: 20.5 * s))
            base.addLine(to: CGPoint(x: 7.5 * s, y: 20.5 * s))
            base.closeSubpath()
            context.fill(base, with: .color(Palette.glyphDim))
            context.stroke(base, with: .color(Palette.glyphDim), style: StrokeStyle(lineWidth: 1 * s, lineJoin: .round))
        }
    }
}

/// Three dots in a 24-point box.
struct CollapsingPlayerHeaderDots: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 24
        var path = Path()
        for x in [5.0, 12.0, 19.0] {
            path.addEllipse(in: CGRect(x: rect.minX + (x - 1.6) * s, y: rect.minY + (12 - 1.6) * s, width: 3.2 * s, height: 3.2 * s))
        }
        return path
    }
}

#Preview {
    CollapsingPlayerHeader(autoPlay: true)
}
