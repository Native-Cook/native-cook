import SwiftUI

private typealias M = CandlePatternWalkthroughMotion
private typealias S = CandlePatternWalkthroughStyle

/// Tap to play to the next chapter boundary, or drag horizontally through the whole lesson.
struct CandlePatternWalkthrough: View {
    var title = "Trend line breakout"
    var hint = "Tap for the next step · swipe to scrub"
    var chapters = ["Swing highs", "Trend line", "Breakout", "Re-test", "Confirmation", "Risk to reward", "Target"]
    var swingHighLabel = "swing\nhigh"
    var trendLineLabel = "trend line"
    var breakoutLabel = "breakout"
    var retestLabel = "re-test"
    var confirmationLabel = "confirmation"
    var ratioPrefix = "RR=1:"
    var upColor = Color(hex: 0x3bff3b)
    var downColor = Color(hex: 0xff2d2d)
    var autoPlay = false
    var onChapterChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var mode = Mode.hand
    @State private var origin = Date.now
    @State private var held = 0.0
    @State private var manual: Run? = nil
    @State private var returning: Date? = nil
    @State private var scrubbing = false
    @State private var scrubFrom = 0.0
    @State private var lastTouch = Date.now
    @State private var revision = 0
    @State private var ready = false
    @State private var active = false
    @State private var captionActive = false
    @State private var chapter = 0
    @State private var previousChapter: Int? = nil
    @State private var captionStart = Date.distantPast
    @State private var haptic = UISelectionFeedbackGenerator()

    private enum Mode { case auto, hand, returning }
    private struct Run { let start: Date; let from: Double; let to: Double }

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: !active && !captionActive)) { timeline in
                let date = active || captionActive ? timeline.date : Date.now
                let t = time(at: date)
                VStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: S.HINT_MARGIN) {
                        Text(title).font(.system(size: S.TITLE_SIZE, weight: .heavy)).tracking(S.TITLE_TRACKING).foregroundStyle(S.INK).frame(height: S.TITLE_HEIGHT, alignment: .leading)
                        Text(hint).font(.system(size: S.HINT_SIZE)).foregroundStyle(S.MUTE).frame(height: S.HINT_HEIGHT, alignment: .leading)
                    }
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, S.MARGIN)
                    .padding(.vertical, S.HEADER_PAD)
                    GeometryReader { stage in
                        Canvas { context, size in
                            let drawing = CandlePatternWalkthroughDrawing(swingHighLabel: swingHighLabel, trendLineLabel: trendLineLabel, breakoutLabel: breakoutLabel, retestLabel: retestLabel, confirmationLabel: confirmationLabel, ratioPrefix: ratioPrefix, upColor: upColor, downColor: downColor)
                            var clipped = context
                            clipped.clip(to: Path(CGRect(origin: .zero, size: size)))
                            for pass in 0..<8 { drawing.draw(clipped, size.width, size.height, t, veil(at: date), reduceMotion, pass) }
                        }
                        .overlay(alignment: .top) { LinearGradient(colors: [S.BACKGROUND, S.BACKGROUND.opacity(0)], startPoint: .top, endPoint: .bottom).frame(height: S.EDGE_FADE).allowsHitTesting(false) }
                        .overlay(alignment: .bottom) { LinearGradient(colors: [S.BACKGROUND.opacity(0), S.BACKGROUND], startPoint: .top, endPoint: .bottom).frame(height: S.EDGE_FADE).allowsHitTesting(false) }
                        .clipped()
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                            guard scrubbing || abs(value.translation.width) > S.PAN_SLOP else { return }
                            if !scrubbing { takeOver(); scrubbing = true; scrubFrom = held }
                            held = min(M.LOOP - S.SCRUB_END_EPSILON, max(0, scrubFrom + value.translation.width / stage.size.width * M.LOOP))
                            report(M.chapterAt(held), byHand: true)
                        }.onEnded { value in
                            if scrubbing {
                                scrubbing = false; lastTouch = .now; revision += 1
                            } else if abs(value.translation.width) <= S.PAN_SLOP && abs(value.translation.height) <= S.TAP_SLOP { next() }
                        })
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(title)
                        .accessibilityValue(chapters[chapter])
                        .accessibilityAdjustableAction { if $0 == .increment { next() } }
                    }
                    footer(time: t, at: date)
                        .padding(.bottom, max(proxy.safeAreaInsets.bottom, S.FOOTER_PAD) - proxy.safeAreaInsets.bottom)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .background(S.BACKGROUND.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            guard !ready else { return }
            ready = true; mode = autoPlay ? .auto : .hand; origin = .now; revision += 1; haptic.prepare()
        }
        .task(id: revision) { await drive() }
        .task(id: captionStart) {
            guard previousChapter != nil, !reduceMotion else { return }
            captionActive = true
            await until(captionStart.addingTimeInterval(S.CHAPTER_IN))
            guard !Task.isCancelled else { return }
            captionActive = false; previousChapter = nil
        }
    }

    private func footer(time: Double, at date: Date) -> some View {
        VStack(alignment: .leading, spacing: S.CAPTION_MARGIN) {
            HStack(spacing: S.SEGMENT_GAP) {
                ForEach(chapters.indices, id: \.self) { k in
                    let fill = time >= M.FADE[1] ? 0 : M.clamp01((time - M.CHAPTER_STARTS[k]) / (M.STOPS[k] - M.CHAPTER_STARTS[k])) * (1 - M.smooth(time, M.FADE[0], M.FADE[1]))
                    RoundedRectangle(cornerRadius: S.SEGMENT_RADIUS).fill(S.TRACK)
                        .overlay { Rectangle().fill(S.INK).scaleEffect(x: fill, y: 1, anchor: .leading) }
                        .clipShape(RoundedRectangle(cornerRadius: S.SEGMENT_RADIUS))
                        .frame(height: S.SEGMENT_HEIGHT)
                }
            }
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text(String(format: "%02d", chapter + 1)).font(.system(size: S.STEP_SIZE, weight: .semibold)).monospacedDigit().foregroundStyle(S.MUTE).frame(height: S.STEP_HEIGHT).frame(width: S.STEP_WIDTH, alignment: .leading)
                let age = date.timeIntervalSince(captionStart)
                Text(chapters[chapter]).font(.system(size: S.CHAPTER_SIZE, weight: .bold)).foregroundStyle(S.INK).lineLimit(1).frame(height: S.CHAPTER_HEIGHT)
                    .opacity(reduceMotion ? 1 : fade(age / S.CHAPTER_IN))
                    .overlay(alignment: .leading) {
                        if let previousChapter {
                            Text(chapters[previousChapter]).font(.system(size: S.CHAPTER_SIZE, weight: .bold)).foregroundStyle(S.INK).lineLimit(1).opacity(reduceMotion ? 0 : 1 - fade(age / S.CHAPTER_OUT))
                        }
                    }
            }
            .frame(height: S.CAPTION_HEIGHT, alignment: .topLeading)
        }
        .padding(.horizontal, S.MARGIN).padding(.top, S.FOOTER_PAD)
    }

    private func fade(_ value: Double) -> Double {
        let u = M.clamp01(value)
        return u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u)
    }

    private func time(at date: Date) -> Double {
        if mode == .auto { return max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.LOOP) }
        if let manual { return min(manual.to, manual.from + max(0, date.timeIntervalSince(manual.start))) }
        return held
    }

    private func veil(at date: Date) -> Double { returning.map { max(0, 1 - date.timeIntervalSince($0) / S.VEIL) } ?? 1 }
    private func report(_ index: Int, byHand: Bool) {
        guard index != chapter else { return }
        previousChapter = chapter; chapter = index; captionStart = .now
        if byHand { haptic.selectionChanged() }
        onChapterChange?(index)
    }

    private func takeOver() {
        held = time(at: .now); mode = .hand; manual = nil; returning = nil; active = false; revision += 1
    }

    private func next() {
        takeOver()
        let stop = M.STOPS.first { $0 > held + S.STOP_EPSILON } ?? M.LOOP
        manual = Run(start: .now, from: held, to: stop)
        active = held >= M.SWING_AT && held < M.REPRINT + Double(M.BASE - 1) * M.REPRINT_STEP + M.PRINT_GROW
        revision += 1
    }

    /// Every wait is tied to a date; timer lateness never changes the loop's period.
    private func until(_ date: Date) async {
        let clock = ContinuousClock()
        try? await clock.sleep(until: clock.now + .seconds(max(0, date.timeIntervalSinceNow)), tolerance: .zero)
    }

    private func drive() async {
        guard ready else { return }
        if let run = manual {
            if run.from < M.SWING_AT {
                await until(run.start.addingTimeInterval(M.SWING_AT - run.from))
                guard !Task.isCancelled else { return }
                active = true
            }
            for boundary in M.CHAPTER_STARTS where boundary > run.from && boundary <= run.to {
                await until(run.start.addingTimeInterval(boundary - run.from))
                guard !Task.isCancelled else { return }
                report(M.chapterAt(boundary), byHand: true)
            }
            let rest = M.REPRINT + Double(M.BASE - 1) * M.REPRINT_STEP + M.PRINT_GROW
            if run.to > rest {
                await until(run.start.addingTimeInterval(max(0, rest - run.from)))
                guard !Task.isCancelled else { return }
                active = false
            }
            await until(run.start.addingTimeInterval(run.to - run.from))
            guard !Task.isCancelled else { return }
            held = run.to >= M.LOOP ? 0 : run.to
            manual = nil; lastTouch = .now; active = false
            report(M.chapterAt(held), byHand: true)
            revision += 1
            return
        }
        guard !scrubbing else { return }
        if mode == .hand {
            guard autoPlay else { return }
            await until(lastTouch.addingTimeInterval(S.USER_HOLD))
            guard !Task.isCancelled else { return }
            if held >= M.SWING_AT && held < M.FADE[0] {
                mode = .returning; returning = .now; active = true
                await until(returning!.addingTimeInterval(S.VEIL))
                guard !Task.isCancelled else { return }
                held = M.FADE[1]; returning = nil
            }
            mode = .auto; origin = Date.now.addingTimeInterval(-held)
            report(M.chapterAt(held), byHand: false)
        }
        var cycle = floor(max(0, Date.now.timeIntervalSince(origin)) / M.LOOP)
        let rest = M.REPRINT + Double(M.BASE - 1) * M.REPRINT_STEP + M.PRINT_GROW
        while !Task.isCancelled {
            let start = origin.addingTimeInterval(cycle * M.LOOP)
            let now = Date.now.timeIntervalSince(start)
            active = now >= M.SWING_AT && now < rest
            let events = M.CHAPTER_STARTS + [rest, M.LOOP]
            for event in events where event > now {
                await until(start.addingTimeInterval(event))
                guard !Task.isCancelled else { return }
                active = event >= M.SWING_AT && event < rest
                report(event >= M.LOOP ? 0 : M.chapterAt(event), byHand: false)
            }
            cycle += 1
        }
    }
}
