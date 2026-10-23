import SwiftUI

struct SkyGlassStepsStep: Equatable {
    /// Its lines are broken by hand with "\n".
    var title: String
    var subtitle: String

    static let samples = [
        SkyGlassStepsStep(title: "Take a short test", subtitle: "To get your personal plan"),
        SkyGlassStepsStep(title: "Understand your\nwell-being state", subtitle: "To spark your natural drive"),
        SkyGlassStepsStep(title: "Recalibrate", subtitle: "Train your mind to focus on practical\noutcomes in any state"),
    ]
}

/// A row of the first step's preview. `icon` is the name of a system symbol.
struct SkyGlassStepsGoal: Equatable {
    var label: String
    var icon: String
    var color: Color

    static let samples = [
        SkyGlassStepsGoal(label: "Overcome trauma", icon: "diamond.fill", color: Color(hex: 0x3b82f6)),
        SkyGlassStepsGoal(label: "Get things done", icon: "camera.macro", color: Color(hex: 0xec4899)),
        SkyGlassStepsGoal(label: "Improve well-being", icon: "leaf.fill", color: Color(hex: 0x65a30d)),
        SkyGlassStepsGoal(label: "Sharpen focus", icon: "target", color: Color(hex: 0xf97316)),
    ]
}

/// A card of the second step's preview, from the back of the stack to the front.
struct SkyGlassStepsLesson: Equatable {
    var category: String
    var icon: String
    var title: String

    static let samples = [
        SkyGlassStepsLesson(category: "Daily tasks", icon: "checkmark.square.fill", title: "Small step\nstep"),
        SkyGlassStepsLesson(category: "Practice", icon: "bolt.fill", title: "Define your\nhabits"),
        SkyGlassStepsLesson(category: "Learning", icon: "book.fill", title: "Procrastination\ncycle"),
    ]
}

/// An introduction in three steps under a drifting sky. A headline floats on the sky, and the
/// steps are panes of frosted glass in a carousel without end; each pane shows a small preview of
/// its step that plays when the card comes to the middle. Swipe to move on, tap a row or a square
/// to change it.
struct SkyGlassSteps: View {
    /// Its lines are broken by hand with "\n".
    var headline = "It took a lot of\nsteps to become\nwho you are"
    var subline = "It will take 3 steps to start\ntransformation"
    /// The word on each card's chip; the step's number follows it.
    var stepLabel = "Step"
    /// The three cards: a short test, a stack of lessons, a routine.
    var steps = SkyGlassStepsStep.samples
    /// Four rows for the first card.
    var goals = SkyGlassStepsGoal.samples
    /// Three lesson cards for the second card, back to front.
    var lessons = SkyGlassStepsLesson.samples
    var routineTitle = "New routine"
    var routineBadge = "Mastered"
    /// Seven column heads for the third card's grid.
    var days = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"]
    /// The squares of the grid that tick by themselves, counted along the rows from 0 to 27.
    var checkedDays = [1, 4, 5, 8, 11]
    /// The sky from top to bottom.
    var skyColors = [Color(hex: 0x2c57a4), Color(hex: 0x4a78bd), Color(hex: 0x7fa3d6)]
    /// What a pane of glass adds over the blurred sky.
    var glassColor = Color.rgba(58, 74, 102, 0.35)
    /// The checked squares.
    var accentColor = Color(hex: 0x5ba80f)
    /// The text on the light parts of the previews.
    var inkColor = Color(hex: 0x1f2937)
    /// Goes through the steps by itself every `SkyGlassStepsMotion.loop` seconds.
    var autoPlay = false
    var onStepChange: ((Int) -> Void)? = nil
    var onGoalSelect: ((Int) -> Void)? = nil
    var onDayToggle: ((Int, Bool) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The clock's zero: every time below is in seconds since then.
    @State private var origin = Date.now
    /// The screen opens as it is `lead` after the last card was left: the first step in, and still.
    @State private var slide = SkyGlassStepsSlide(page: 0, from: -1, velocity: 0, at: -M.lead)
    @State private var focus = SkyGlassStepsFocus(page: 0, was: 2, held: M.quiet, at: -M.lead, warm: false, row: 0)
    /// A finger on the carousel: where it has it, where it took it, the card it started from, and
    /// how far it had already moved when it took hold.
    @State private var drag: Drag? = nil
    @State private var pull = SkyGlassStepsPull(x: 0, released: 0)
    @State private var pick = SkyGlassStepsPick(row: -1, from: 0, at: 0)
    /// When a finger last changed each square: positive for checked, negative for cleared.
    @State private var toggles = [Double](repeating: 0, count: Metrics.squares)
    /// With reduced motion the sky is still: the timeline stops once a change has played out.
    @State private var settled = M.quiet
    @State private var resting = false
    @State private var restTask: Task<Void, Never>? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause; it waits while a finger holds.
    @State private var touches = 0
    @State private var selectHaptic = UISelectionFeedbackGenerator()
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private struct Drag: Equatable {
        var position: Double
        var from: Double
        var origin: Int
        var grab: Double
    }

    /// What made the focus change: a finger, the loop that plays by itself, or the return to rest.
    private enum How {
        case swipe, auto, rest
    }

    /// The screen in the block's own points.
    private struct Layout {
        var fit: Double
        var width: Double
        var height: Double
        var top: Double
        var cardTop: Double
        var cardLeft: Double { (width - Sky.cardWidth) / 2 }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let fit = min(1, screen.height / Metrics.minHeight)
            let height = screen.height / fit
            let layout = Layout(
                fit: fit,
                width: screen.width / fit,
                height: height,
                top: insets.top / fit,
                cardTop: height - max(Metrics.cardBottom, insets.bottom / fit + Metrics.cardClear) - Sky.cardHeight
            )

            TimelineView(.animation(paused: reduceMotion && resting)) { timeline in
                let live = timeline.date.timeIntervalSince(origin)
                let t = reduceMotion ? (resting ? settled : min(live, settled)) : live
                let position = drag?.position ?? M.slideAt(slide, t, still: reduceMotion)

                ZStack(alignment: .topLeading) {
                    Canvas { context, _ in
                        var context = context
                        context.scaleBy(x: layout.fit, y: layout.fit)
                        Sky.draw(
                            in: context,
                            size: CGSize(width: layout.width, height: layout.height),
                            turn: reduceMotion ? 0 : (t / M.loop).truncatingRemainder(dividingBy: 1),
                            position: position,
                            cardTop: layout.cardTop,
                            colors: skyColors,
                            glass: glassColor
                        )
                    }
                    .frame(width: screen.width, height: screen.height)
                    .accessibilityHidden(true)

                    ZStack(alignment: .topLeading) {
                        head(t, layout)
                        ForEach(0 ..< min(M.steps, steps.count), id: \.self) { index in
                            card(index, t)
                                .offset(x: layout.cardLeft + M.offsetOf(index, position) * M.pitch, y: layout.cardTop)
                        }
                        .accessibilityHidden(true)
                        carousel(layout)
                    }
                    .frame(width: layout.width, height: layout.height, alignment: .topLeading)
                    .scaleEffect(layout.fit, anchor: .topLeading)
                }
                .frame(width: screen.width, height: screen.height, alignment: .topLeading)
                .clipped()
            }
            .contentShape(Rectangle())
            .gesture(touch(layout))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            // The clock starts with the loop, so the first pass is timed like every other.
            origin = .now
            settle()
        }
        .onDisappear { restTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches, holding: drag != nil)) { await loop() }
    }

    // MARK: Headline

    private func head(_ t: Double, _ layout: Layout) -> some View {
        VStack(spacing: Metrics.sublineGap) {
            // The lines are the headline's own, so it shrinks before it would wrap.
            Text(headline)
                .font(.system(size: Metrics.headline, weight: .semibold))
                .tracking(-0.5)
                .lineSpacing(Metrics.leading(Metrics.headline, .semibold, 40))
                .foregroundStyle(.white)
                .lineLimit(headline.split(separator: "\n").count)
                .minimumScaleFactor(0.7)
                .shadow(color: Palette.textShadow, radius: 6, y: 1)
                .accessibilityAddTraits(.isHeader)
            Text(subline)
                .font(.system(size: Metrics.subline))
                .lineSpacing(Metrics.leading(Metrics.subline, .regular, 24))
                .foregroundStyle(.white.opacity(0.85))
                .lineLimit(subline.split(separator: "\n").count)
                .minimumScaleFactor(0.7)
                .shadow(color: Palette.textShadow, radius: 4, y: 1)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, Metrics.headMargin)
        .frame(width: layout.width, height: max(0, layout.cardTop - layout.top))
        .offset(x: M.parallaxAt(pull, t, still: reduceMotion), y: layout.top)
    }

    // MARK: Cards

    /// What stands on a pane of glass: a chip, a title, a line under it and the step's preview.
    private func card(_ index: Int, _ t: Double) -> some View {
        let step = steps[index]
        return ZStack(alignment: .topLeading) {
            Text("\(stepLabel) \(index + 1)")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(width: Metrics.chipWidth, height: Metrics.chipHeight)
                .background(Capsule().fill(.white.opacity(0.14)))
                .offset(x: (Sky.cardWidth - Metrics.chipWidth) / 2, y: Metrics.chipTop)

            VStack(spacing: Metrics.subtitleGap) {
                Text(step.title)
                    .font(.system(size: Metrics.title, weight: .semibold))
                    .tracking(-0.2)
                    .lineSpacing(Metrics.leading(Metrics.title, .semibold, 27))
                    .foregroundStyle(.white)
                    .lineLimit(step.title.split(separator: "\n").count)
                    .minimumScaleFactor(0.8)
                Text(step.subtitle)
                    .font(.system(size: Metrics.subtitle))
                    .lineSpacing(Metrics.leading(Metrics.subtitle, .regular, 19))
                    .foregroundStyle(.white.opacity(0.7))
                    .lineLimit(step.subtitle.split(separator: "\n").count)
                    .minimumScaleFactor(0.8)
            }
            .multilineTextAlignment(.center)
            .frame(width: Sky.cardWidth - Metrics.textMargin * 2)
            .offset(x: Metrics.textMargin, y: Metrics.titleTop)

            preview(index, t)
                .frame(width: Metrics.previewWidth, height: Metrics.previewHeight, alignment: .topLeading)
                .offset(x: Metrics.previewLeft, y: Metrics.previewTop)
        }
        .frame(width: Sky.cardWidth, height: Sky.cardHeight, alignment: .topLeading)
    }

    @ViewBuilder
    private func preview(_ index: Int, _ t: Double) -> some View {
        let state = M.previewAt(index, focus, t)
        if state.shown > 0 {
            switch index {
            case 0: goalRows(t, state)
            case 1: lessonStack(state)
            default: routine(t, state)
            }
        }
    }

    /// Step 1: four goals; a white fill marks one and steps down the list.
    private func goalRows(_ t: Double, _ state: (e: Double, shown: Double, warm: Bool)) -> some View {
        let row = M.rowAt(state.e, from: state.warm ? focus.row : 0, pick, t, still: reduceMotion)
        // The fill comes in with the row it is on.
        let fill = M.enter(state.e, delay: row.rounded() * M.rowStagger, shift: M.rowShift, warm: state.warm, still: reduceMotion)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.rowRadius, style: .continuous)
                .fill(.white.opacity(0.86))
                .frame(width: Metrics.previewWidth, height: Metrics.rowHeight)
                .opacity(fill.opacity * state.shown)
                .offset(y: row * (Metrics.rowHeight + Metrics.rowGap) + fill.y)

            ForEach(Array(goals.prefix(4).enumerated()), id: \.offset) { index, goal in
                let pose = M.enter(state.e, delay: Double(index) * M.rowStagger, shift: M.rowShift, warm: state.warm, still: reduceMotion)
                HStack(spacing: 10) {
                    Image(systemName: goal.icon)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(goal.color)
                        .frame(width: Metrics.tile, height: Metrics.tile)
                        .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(.white))
                    Text(goal.label)
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(inkColor)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(inkColor.opacity(0.7))
                }
                .padding(.leading, 10)
                .padding(.trailing, 16)
                .frame(width: Metrics.previewWidth, height: Metrics.rowHeight)
                .background(RoundedRectangle(cornerRadius: Metrics.rowRadius, style: .continuous).fill(.white.opacity(0.26)))
                .opacity(pose.opacity * state.shown)
                .offset(y: Double(index) * (Metrics.rowHeight + Metrics.rowGap) + pose.y)
            }
        }
    }

    /// Step 2: three lesson cards, back to front; each is the front card's layout, scaled about
    /// the middle of its top edge.
    private func lessonStack(_ state: (e: Double, shown: Double, warm: Bool)) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(lessons.prefix(3).enumerated()), id: \.offset) { index, lesson in
                let pose = M.enter(state.e, delay: Double(index) * M.lessonStagger, shift: M.lessonShift, warm: state.warm, still: reduceMotion)
                let lift = index == 2 ? M.liftAt(state.e, still: reduceMotion) : 0
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Palette.lessonBar)
                        .frame(width: 6, height: 65)
                        .offset(x: 12, y: 11.5)
                    HStack(spacing: 5) {
                        Image(systemName: lesson.icon)
                            .font(.system(size: 9, weight: .semibold))
                        Text(lesson.category)
                            .font(.system(size: 11))
                            .lineLimit(1)
                    }
                    .foregroundStyle(Palette.mute)
                    .frame(height: 16)
                    .offset(x: 28, y: 11)
                    Text(lesson.title)
                        .font(.system(size: Metrics.lessonTitle, weight: .semibold))
                        .tracking(-0.2)
                        .lineSpacing(Metrics.leading(Metrics.lessonTitle, .semibold, 24))
                        .foregroundStyle(inkColor)
                        .lineLimit(2)
                        .frame(width: Metrics.previewWidth - 40, alignment: .leading)
                        .offset(x: 28, y: 30)
                }
                .frame(width: Metrics.previewWidth, height: Metrics.lessonHeight, alignment: .topLeading)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Metrics.lessonFills[index])
                        .shadow(color: index == 2 ? Palette.lessonShadow : .clear, radius: 8, y: 6)
                )
                .scaleEffect(Metrics.lessonScales[index], anchor: .top)
                .opacity(pose.opacity * state.shown)
                .offset(y: Metrics.lessonTops[index] + pose.y - lift)
            }
        }
    }

    /// Step 3: a month of a routine; squares tick green one by one, and a finger can change any of them.
    private func routine(_ t: Double, _ state: (e: Double, shown: Double, warm: Bool)) -> some View {
        let pose = M.panelAt(state.e, warm: state.warm, still: reduceMotion)
        // The canvas draws from copies, so it does not hold on to the view.
        let (order, toggles, accent, still) = (self.order, self.toggles, accentColor, reduceMotion)
        return ZStack(alignment: .topLeading) {
            Text(routineTitle)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(inkColor)
                .lineLimit(1)
                .frame(height: 18)
                .offset(x: Metrics.panelMargin, y: Metrics.headerY - 9)
            HStack(spacing: 4) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 11, weight: .semibold))
                Text(routineBadge)
                    .font(.system(size: 11.5, weight: .medium))
                    .lineLimit(1)
            }
            .foregroundStyle(Palette.mute)
            .frame(height: 18)
            .scaleEffect(M.pulseAt(state.e, ticks: checkedDays.count, still: reduceMotion))
            .frame(width: Metrics.previewWidth - Metrics.panelMargin * 2, alignment: .trailing)
            .offset(x: Metrics.panelMargin, y: Metrics.headerY - 9)

            ForEach(Array(days.prefix(Metrics.columns).enumerated()), id: \.offset) { index, day in
                Text(day)
                    .font(.system(size: 11))
                    .foregroundStyle(Palette.day)
                    .lineLimit(1)
                    .frame(width: Metrics.squarePitchX, height: 16)
                    .offset(x: Metrics.squareX + (Double(index) - 0.5) * Metrics.squarePitchX, y: Metrics.daysY - 8)
            }

            Canvas { context, _ in
                let box = Path(
                    roundedRect: CGRect(x: -Metrics.square / 2, y: -Metrics.square / 2, width: Metrics.square, height: Metrics.square),
                    cornerRadius: Metrics.squareRadius,
                    style: .circular
                )
                let stroke = StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
                let (a, b, c) = Metrics.check
                for i in 0 ..< Metrics.squares {
                    let row = i / Metrics.columns
                    let fade = Metrics.rowFades[row]
                    let tick = M.tickAt(state.e, order: order[i], toggled: toggles[i], t, still: still)
                    var cell = context
                    cell.translateBy(
                        x: Metrics.squareX + Double(i % Metrics.columns) * Metrics.squarePitchX,
                        y: Metrics.squareY + Double(row) * Metrics.squarePitchY
                    )
                    cell.scaleBy(x: tick.pop, y: tick.pop)
                    cell.fill(box, with: .color(.white.opacity(Metrics.emptyAlpha * fade)))
                    guard tick.q > 0 else { continue }
                    cell.fill(box, with: .color(accent.opacity(M.smooth(tick.q, 0, 0.6) * fade)))
                    // With motion the check is drawn stroke by stroke; without, it fades in whole.
                    let drawn = still ? 1 : M.clamp01((tick.q - Metrics.checkAfter) / (1 - Metrics.checkAfter))
                    guard drawn > 0 else { continue }
                    let short = min(1, drawn / Metrics.checkShort)
                    var mark = Path()
                    mark.move(to: a)
                    mark.addLine(to: CGPoint(x: a.x + (b.x - a.x) * short, y: a.y + (b.y - a.y) * short))
                    if drawn > Metrics.checkShort {
                        let long = (drawn - Metrics.checkShort) / (1 - Metrics.checkShort)
                        mark.addLine(to: CGPoint(x: b.x + (c.x - b.x) * long, y: b.y + (c.y - b.y) * long))
                    }
                    cell.stroke(mark, with: .color(.white.opacity((still ? tick.q : 1) * fade)), style: stroke)
                }
            }
            .frame(width: Metrics.previewWidth, height: Metrics.previewHeight)
        }
        .frame(width: Metrics.previewWidth, height: Metrics.previewHeight, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(.white.opacity(0.45)))
        .scaleEffect(pose.scale)
        .opacity(pose.opacity * state.shown)
    }

    /// For each square its place among those that tick by themselves, or -1.
    private var order: [Int] {
        let sorted = checkedDays.sorted()
        return (0 ..< Metrics.squares).map { sorted.firstIndex(of: $0) ?? -1 }
    }

    /// The carousel as one thing a screen reader can step through.
    private func carousel(_ layout: Layout) -> some View {
        let step = steps[min(M.stepOf(focus.page), steps.count - 1)]
        return Color.clear
            .frame(width: layout.width, height: Sky.cardHeight)
            .offset(y: layout.cardTop)
            .accessibilityElement()
            .accessibilityLabel("\(stepLabel) \(M.stepOf(focus.page) + 1)")
            .accessibilityValue("\(step.title.replacingOccurrences(of: "\n", with: " ")). \(step.subtitle.replacingOccurrences(of: "\n", with: " "))")
            .accessibilityAdjustableAction { direction in
                touches += 1
                move(direction == .increment ? 1 : -1, .swipe)
            }
    }

    // MARK: Behaviour

    /// Seconds on the clock right now.
    private var now: Double { Date.now.timeIntervalSince(origin) }

    /// Where the carousel is right now.
    private var place: Double { drag?.position ?? M.slideAt(slide, now, still: reduceMotion) }

    /// One touch is a swipe of the carousel or a tap: on a row, on a square, or on a card at the side.
    private func touch(_ layout: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let across = value.translation.width
                if drag == nil, abs(across) > Metrics.slop {
                    let at = place
                    drag = Drag(position: at, from: at, origin: Int(at.rounded()), grab: across)
                    pull = SkyGlassStepsPull(x: 0, released: -1)
                    wake()
                }
                guard let finger = drag else { return }
                let moved = (across - finger.grab) / layout.fit
                let at = finger.from - moved / M.pitch
                drag?.position = at
                pull = SkyGlassStepsPull(x: moved, released: -1)
                // The card nearest the middle has the focus, also under a finger.
                if Int(at.rounded()) != focus.page { focusOn(Int(at.rounded()), .swipe) }
            }
            .onEnded { value in
                guard let finger = drag else {
                    if hypot(value.translation.width, value.translation.height) <= Metrics.slop {
                        tapped(x: value.location.x / layout.fit, y: value.location.y / layout.fit, layout)
                    }
                    return
                }
                let t = now
                let velocity = max(-M.flickMax, min(M.flickMax, -value.velocity.width / layout.fit / M.pitch))
                // A flick carries one card further at most.
                let page = max(finger.origin - 1, min(finger.origin + 1, Int((finger.position + velocity * M.flick).rounded())))
                drag = nil
                slide = SkyGlassStepsSlide(page: page, from: finger.position, velocity: velocity, at: t)
                pull = SkyGlassStepsPull(x: pull.x, released: t)
                touches += 1
                if page != focus.page { focusOn(page, .swipe) }
                settle()
            }
    }

    private func tapped(x: Double, y: Double, _ layout: Layout) {
        let y = y - layout.cardTop
        guard y >= 0, y <= Sky.cardHeight else { return }
        touches += 1
        let x = x - layout.cardLeft
        // The slivers of the cards on either side bring those cards in.
        if x < 0 || x > Sky.cardWidth {
            move(x < 0 ? -1 : 1, .swipe)
            return
        }
        let px = x - Metrics.previewLeft
        let py = y - Metrics.previewTop
        guard px >= 0, px <= Metrics.previewWidth, py >= 0, py <= Metrics.previewHeight else { return }
        switch M.stepOf(slide.page) {
        case 0:
            let row = Int(py / (Metrics.rowHeight + Metrics.rowGap))
            if row < 4, py - Double(row) * (Metrics.rowHeight + Metrics.rowGap) <= Metrics.rowHeight { choose(row) }
        case 2:
            let column = Int(((px - Metrics.squareX) / Metrics.squarePitchX).rounded())
            let row = Int(((py - Metrics.squareY) / Metrics.squarePitchY).rounded())
            let near = abs(px - Metrics.squareX - Double(column) * Metrics.squarePitchX) <= Metrics.squarePitchX / 2
                && abs(py - Metrics.squareY - Double(row) * Metrics.squarePitchY) <= Metrics.squarePitchY / 2
            if near, column >= 0, column < Metrics.columns, row >= 0, row < Metrics.squares / Metrics.columns {
                toggle(row * Metrics.columns + column)
            }
        default:
            break
        }
    }

    /// Gives a page the focus: its preview starts from the beginning, and the one before fades.
    private func focusOn(_ page: Int, _ how: How) {
        let t = now
        let old = focus
        let step = M.stepOf(page)
        let same = step == M.stepOf(old.page)
        let e = t - old.at
        focus = SkyGlassStepsFocus(
            page: page,
            was: same ? old.was : M.stepOf(old.page),
            // A card that had started over was already whole.
            held: same ? old.held : old.warm ? max(e, 1) : e,
            at: t,
            warm: same,
            row: same && step == 0 ? M.rowAt(e, from: old.warm ? old.row : 0, pick, t, still: reduceMotion) : 0
        )
        // What a finger changed on a card is forgotten when the card comes round again.
        if step == 0 { pick = SkyGlassStepsPick(row: -1, from: 0, at: 0) }
        if step == 2 { toggles = [Double](repeating: 0, count: Metrics.squares) }
        settle()
        if how == .swipe { selectHaptic.selectionChanged() }
        onStepChange?(step)
    }

    /// Sends the carousel to a page. Taps beside the card, the loop that plays by itself and the
    /// return to rest all come through here.
    private func goTo(_ page: Int, _ how: How) {
        slide = SkyGlassStepsSlide(page: page, from: place, velocity: 0, at: now)
        drag = nil
        settle()
        if page != focus.page || how == .rest { focusOn(page, how) }
    }

    /// One card on or back.
    private func move(_ by: Int, _ how: How) {
        goTo(slide.page + by, how)
    }

    /// Back to the first step, the short way round.
    private func rest() {
        goTo(Int((place / Double(M.steps)).rounded()) * M.steps, .rest)
    }

    /// Step 1: moves the white fill to a row.
    private func choose(_ row: Int) {
        let t = now
        pick = SkyGlassStepsPick(row: row, from: M.rowAt(t - focus.at, from: focus.warm ? focus.row : 0, pick, t, still: reduceMotion), at: t)
        settle()
        tapHaptic.impactOccurred()
        onGoalSelect?(row)
    }

    /// Step 3: checks or clears a square.
    private func toggle(_ index: Int) {
        // Zero stands for "never touched", so a touch is never stamped with it.
        let t = max(now, 0.001)
        let checked = toggles[index] != 0 ? toggles[index] > 0 : order[index] >= 0
        toggles[index] = checked ? -t : t
        settle()
        tapHaptic.impactOccurred()
        onDayToggle?(index, !checked)
    }

    /// Starts the timeline again.
    private func wake() {
        restTask?.cancel()
        resting = false
    }

    /// Runs the timeline until the last change has played out; only reduced motion ever stops it.
    private func settle() {
        wake()
        settled = now + M.quiet
        guard reduceMotion else { return }
        restTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.quiet))
            if !Task.isCancelled, drag == nil { resting = true }
        }
    }

    private func loop() async {
        guard autoPlay, drag == nil else { return }
        let clock = ContinuousClock()
        var start = clock.now
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // A loop starts `lead` after the first step took the focus.
            rest()
            start = clock.now + .seconds(M.lead)
        }
        while !Task.isCancelled {
            for at in M.autoSwipes {
                try? await clock.sleep(until: start + .seconds(at))
                guard !Task.isCancelled else { return }
                move(1, .auto)
            }
            start += .seconds(M.loop)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
        let holding: Bool
    }
}

private typealias M = SkyGlassStepsMotion
private typealias Sky = SkyGlassStepsSky

private enum Palette {
    static let mute = Color(hex: 0x6b7280)
    static let day = Color(hex: 0x374151)
    static let lessonBar = Color(hex: 0xb5d98b)
    static let textShadow = Color.rgba(16, 36, 84, 0.28)
    static let lessonShadow = Color.rgba(16, 28, 60, 0.2)
}

private enum Metrics {
    /// The screen is laid out for at least this height and scaled down where it is shorter.
    static let minHeight = 720.0
    /// The cards stand this far above the bottom edge, or clear of the home indicator.
    static let cardBottom = 44.0
    static let cardClear = 8.0
    /// A finger has to travel this far sideways before it takes hold of the carousel.
    static let slop = 8.0

    static let headline = 34.0
    static let subline = 16.0
    static let sublineGap = 14.0
    static let headMargin = 24.0

    // Inside a card, from its top left corner.
    static let chipTop = 33.0
    static let chipWidth = 56.0
    static let chipHeight = 24.0
    static let titleTop = 72.0
    static let title = 21.0
    static let subtitle = 13.0
    static let subtitleGap = 5.0
    static let textMargin = 20.0
    static let previewLeft = 34.0
    static let previewTop = 172.0
    static let previewWidth = 230.0
    static let previewHeight = 244.0

    static let rowHeight = 55.0
    static let rowGap = 8.0
    static let rowRadius = 14.0
    static let tile = 36.0

    // A lesson card is laid out at the front card's size and scaled about the middle of its top edge.
    static let lessonHeight = 88.0
    static let lessonTitle = 19.0
    static let lessonTops = [18.0, 82, 156]
    static let lessonScales = [0.78, 0.9, 1]
    static let lessonFills = [Color.white.opacity(0.3), Color.white.opacity(0.5), Color.white]

    // The grid: seven columns and four rows of squares, placed by their middles.
    static let columns = 7
    static let squares = 28
    static let square = 20.0
    static let squareRadius = 5.5
    static let squareX = 28.0
    static let squareY = 98.0
    static let squarePitchX = 29.0
    static let squarePitchY = 39.0
    static let daysY = 62.0
    static let headerY = 26.0
    static let panelMargin = 18.0
    /// The lower rows fade out.
    static let rowFades = [1, 0.8, 0.45, 0.25]
    static let emptyAlpha = 0.6
    /// The check is two strokes, from a by b to c; the short one takes `checkShort` of the drawing.
    static let check = (CGPoint(x: -4.5, y: 0.4), CGPoint(x: -1.3, y: 3.4), CGPoint(x: 4.5, y: -3))
    static let checkShort = 0.38
    static let checkAfter = 0.35

    /// The space to add between lines so that a text of this size has lines `line` points apart.
    static func leading(_ size: Double, _ weight: UIFont.Weight, _ line: Double) -> Double {
        max(0, line - UIFont.systemFont(ofSize: size, weight: weight).lineHeight)
    }
}

#Preview {
    SkyGlassSteps(autoPlay: true)
}
