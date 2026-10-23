import SwiftUI

struct BudgetScrubScaleCategory: Equatable {
    var name: String
    /// SF Symbol shown on the chip.
    var symbol: String
    var color: Color
    /// The category's budget at first.
    var amount: Int

    static let samples = [
        BudgetScrubScaleCategory(name: "Cafe", symbol: "fork.knife", color: Color(hex: 0x3d35e0), amount: 400),
        BudgetScrubScaleCategory(name: "Grocery", symbol: "cart.fill", color: Color(hex: 0x2eddc0), amount: 650),
        BudgetScrubScaleCategory(name: "Taxi", symbol: "car.fill", color: Color(hex: 0xf5b83d), amount: 170),
        BudgetScrubScaleCategory(name: "Gym", symbol: "dumbbell.fill", color: Color(hex: 0xf58a6b), amount: 320),
        BudgetScrubScaleCategory(name: "Bills", symbol: "doc.text.fill", color: Color(hex: 0xf06fa5), amount: 540),
    ]
}

/// What the scale says about an amount of `from` or more.
struct BudgetScrubScaleVerdict: Equatable {
    var from: Int
    var title: String
    var lines: [String]

    static let samples = [
        BudgetScrubScaleVerdict(from: 0, title: "Normal", lines: ["It's ok to eat at your place", "Up to 5% of economy"]),
        BudgetScrubScaleVerdict(from: 600, title: "That's a lot", lines: ["Sometimes, you can eat in cafe", "Up to 3% of economy"]),
        BudgetScrubScaleVerdict(from: 1200, title: "Are you crazy?", lines: ["Eat in restaurants every day", "Blow off 30% of money"]),
    ]
}

/// A budget panel built around a ruler. The ruler is dragged sideways under a fixed indicator and
/// flung with momentum; the amount above it counts along on rolling digits, and a verdict under it
/// rolls over when the amount crosses a threshold. The row of chips on top picks the category: the
/// chosen one opens into a coloured pill that carries its amount, and the ruler slides to it.
struct BudgetScrubScale: View {
    var categories = BudgetScrubScaleCategory.samples
    /// In rising order of `from`; the first one should start at 0.
    var verdicts = BudgetScrubScaleVerdict.samples
    /// Index of the category that is open at first.
    var initial = 0
    /// The scale runs from 0 to `maximum`, one mark every `step`.
    var maximum = 2000
    var step = 10
    var currency = "$"
    var saveLabel = "Save"
    /// Flings the ruler twice, picks another category and comes back by itself, in a loop.
    var autoPlay = false
    /// Called with the category's index and its amount when the ruler settles or another category opens.
    var onChange: ((Int, Int) -> Void)? = nil
    /// Called with every category's amount when Save is tapped.
    var onSave: (([Int]) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The amount under the indicator; the ruler's position follows from it.
        var amount = Track(0)
        /// Each category's amount, the open one's as of the last time the ruler settled.
        var amounts: [Int] = []
        var selected = 0
        /// Each chip from a white square, 0, to the coloured pill, 1.
        var expand: [Track] = []
        var chipPress: [Track] = []
        /// How far the row of chips has slid left.
        var scroll = Track(0)
        var savePress = Track(0)
        /// The indicator's twitch as a mark passes under it.
        var tick = Track(0)
        var notch = 0

        /// The four digit wheels, units first: the number each one has rolled to, how fast it is
        /// rolling (digits a second) and how much of its place it takes, 0…1.
        var wheels = [0.0, 0.0, 0.0, 0.0]
        var wheelSpeed = [0.0, 0.0, 0.0, 0.0]
        var presence = [1.0, 0.0, 0.0, 0.0]
        /// Points a second the ruler is moving at.
        var speed = 0.0
        var lastAmount = 0.0
        var lastFrame = Date.distantPast

        /// The verdict on show, each verdict's opacity, and the side it is on while hidden: 1 below, -1 above.
        var verdict = 0
        var verdictShown: [Track] = []
        var verdictSide: [Double] = []

        var dragging = false
        var grabAmount = 0.0
        /// A finger moved the ruler last, so its marks are felt.
        var byHand = false

        /// Brings the wheels, the ruler's speed and the verdict up to `now`.
        func advance(to now: Date, value: Double, verdicts: [BudgetScrubScaleVerdict], pointsPerUnit: Double, still: Bool) {
            let dt = min(now.timeIntervalSince(lastFrame), 0.1)
            guard dt > 0 else { return }
            lastFrame = now
            speed = (value - lastAmount) / dt * pointsPerUnit
            lastAmount = value

            let whole = max(0, Int(value.rounded()))
            var place = 1
            for k in wheels.indices {
                let target = Double(whole / place)
                let gap = target - wheels[k]
                let move = still || abs(gap) < 0.004 ? gap : gap * (1 - exp(-dt / Metrics.wheelLag))
                wheelSpeed[k] = move / dt
                wheels[k] += move
                let wanted = k == 0 || whole >= place ? 1.0 : 0.0
                presence[k] = still ? wanted : presence[k] + (wanted - presence[k]) * (1 - exp(-dt / Metrics.wheelLag))
                place *= 10
            }

            let next = verdicts.lastIndex { whole >= $0.from } ?? 0
            if next != verdict, verdictShown.indices.contains(next), verdictShown.indices.contains(verdict) {
                // The old title leaves upward, the new one comes up from below.
                verdictSide[verdict] = -1
                verdictSide[next] = 1
                verdictShown[verdict].retarget(0, still ? .jump : .tween(Metrics.verdictTime, Ease.out))
                verdictShown[next].retarget(1, still ? .jump : .tween(Metrics.verdictTime, Ease.out))
                verdict = next
            }
        }
    }

    private var home: Int { min(max(initial, 0), max(0, categories.count - 1)) }
    private var auto: Bool { autoPlay && !reduceMotion && !categories.isEmpty }
    private var pointsPerUnit: Double { Metrics.pitch / Double(max(1, step)) }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let ready = motion.expand.count == categories.count && !categories.isEmpty
            let value = motion.amount.value(at: now)
            let _ = motion.advance(to: now, value: value, verdicts: verdicts, pointsPerUnit: pointsPerUnit, still: reduceMotion)
            let notch = Int((value / Double(max(1, step))).rounded())
            let _ = notice(notch)

            ZStack(alignment: .topLeading) {
                Color(hex: 0xf7f7f8)
                if ready {
                    chips(now: now, value: value)
                    amountCard(now: now)
                    scalePanel(now: now, value: value)
                }
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.corner, style: .continuous))
            .sensoryFeedback(trigger: notch) { _, _ in motion.byHand ? .selection : nil }
        }
        .onAppear { reset() }
        .onChange(of: categories.count) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    /// The row of categories: white squares, and one coloured pill for the open one.
    private func chips(now: Date, value: Double) -> some View {
        let open = categories.indices.map { motion.expand[$0].value(at: now) }
        let scroll = motion.scroll.value(at: now)
        var left = Metrics.inset - scroll
        var frames: [CGRect] = []
        for e in open {
            let width = Metrics.chip + (Metrics.pillW - Metrics.chip) * e
            let height = Metrics.chip + (Metrics.pillH - Metrics.chip) * e
            frames.append(CGRect(x: left, y: Metrics.rowY - height / 2, width: width, height: height))
            left += width + Metrics.chipGap
        }
        return ForEach(categories.indices, id: \.self) { i in
            let category = categories[i]
            let e = min(1, max(0, open[i]))
            let frame = frames[i]
            let amount = i == motion.selected ? snapped(value) : motion.amounts[i]
            let shape = RoundedRectangle(cornerRadius: Metrics.chipRadius, style: .continuous)

            ZStack(alignment: .topLeading) {
                Color.white
                category.color.opacity(e)
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(.black.opacity(0.2 * e))
                    .place(x: 10, y: (frame.height - 44) / 2, width: 44, height: 44, .center)
                ZStack {
                    Image(systemName: category.symbol).foregroundStyle(category.color).opacity(1 - e)
                    Image(systemName: category.symbol).foregroundStyle(.white).opacity(e)
                }
                .font(.system(size: 19, weight: .semibold))
                .place(x: 6 * e, y: (frame.height - 52) / 2, width: 52, height: 52, .center)
                VStack(alignment: .leading, spacing: 2) {
                    Text(category.name).font(.system(size: 14, weight: .light))
                    Text("\(currency) \(grouped(amount))").font(.system(size: 13, weight: .bold)).monospacedDigit()
                }
                .foregroundStyle(.white)
                .lineLimit(1)
                .fixedSize()
                .place(x: 66, y: 0, width: 80, height: frame.height, .leading)
                .opacity(min(1, max(0, e * 2 - 1)))
            }
            .frame(width: frame.width, height: frame.height, alignment: .topLeading)
            .clipShape(shape)
            .contentShape(shape)
            .onTapGesture {
                touch()
                motion.chipPress[i].retarget(1, .pulse(0.08, 0.18))
                select(i)
            }
            .shadow(color: category.color.opacity(0.4 * e), radius: 10, x: 0, y: 8)
            .scaleEffect(1 - 0.05 * motion.chipPress[i].value(at: now))
            .position(x: frame.midX, y: frame.midY)
            .accessibilityElement()
            .accessibilityLabel("\(category.name), \(currency) \(amount)")
            .accessibilityAddTraits(i == motion.selected ? [.isButton, .isSelected] : .isButton)
        }
    }

    /// The white card with the amount on rolling digits.
    private func amountCard(now: Date) -> some View {
        // Copies, so the canvas below is seen to change from one frame to the next.
        let wheels = motion.wheels
        let speeds = motion.wheelSpeed
        let presence = motion.presence
        return Group {
            UnevenRoundedRectangle(topLeadingRadius: Metrics.cardRadius, topTrailingRadius: Metrics.cardRadius, style: .continuous)
                .fill(.white)
                .place(x: 0, y: Metrics.cardY, width: Metrics.width, height: Metrics.height - Metrics.cardY, .center)
            Text(currency)
                .font(.system(size: 40, weight: .light, design: .rounded))
                .foregroundStyle(Color(hex: 0xc9c9d1))
                .place(x: Metrics.inset + 2, y: Metrics.amountY - 30, width: 34, height: 60, .leading)
            Canvas { context, _ in
                drawDigits(context, wheels: wheels, speeds: speeds, presence: presence)
            }
            .place(x: Metrics.digitsX, y: Metrics.amountY - Metrics.digitH / 2, width: Metrics.digitW * 4, height: Metrics.digitH, .center)
            // A small calculator on a lavender tile.
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color(hex: 0xecebfb))
                .overlay {
                    Canvas { context, _ in
                        let ink = GraphicsContext.Shading.color(Color(hex: 0x3d35e0))
                        let paper = GraphicsContext.Shading.color(Color(hex: 0xecebfb))
                        context.fill(RoundedRectangle(cornerRadius: 2.5).path(in: CGRect(x: 11, y: 9.5, width: 12, height: 15)), with: ink)
                        context.fill(Path(CGRect(x: 13, y: 11.5, width: 8, height: 3)), with: paper)
                        for row in 0..<2 {
                            for column in 0..<2 {
                                context.fill(Path(CGRect(x: 13 + Double(column) * 5.2, y: 16.3 + Double(row) * 3.6, width: 2.8, height: 2.2)), with: paper)
                            }
                        }
                    }
                }
                .place(x: Metrics.width - Metrics.inset - 34, y: Metrics.amountY - 17, width: 34, height: 34, .center)
        }
        .accessibilityHidden(true)
    }

    /// Four wheels of digits, left-aligned: a wheel the amount does not reach takes no room.
    private func drawDigits(_ context: GraphicsContext, wheels: [Double], speeds: [Double], presence: [Double]) {
        var x = 0.0
        for k in wheels.indices.reversed() {
            let presence = presence[k]
            guard presence > 0.01 else { continue }
            let rolled = wheels[k]
            let digit = Int(floor(rolled))
            let part = rolled - floor(rolled)
            // A wheel that is spinning smears: taller and fainter.
            let blur = min(1, abs(speeds[k]) / Metrics.smearAt)
            var column = context
            column.clip(to: Path(CGRect(x: x, y: 0, width: Metrics.digitW * presence, height: Metrics.digitH)))
            column.opacity = presence * (1 - 0.45 * blur)
            column.translateBy(x: x + Metrics.digitW * presence / 2, y: Metrics.digitH / 2)
            column.scaleBy(x: 1, y: 1 + 0.25 * blur)
            for (offset, shown) in [(-part, digit), (1 - part, digit + 1)] where abs(offset) < 1 {
                column.draw(
                    Text(String(((shown % 10) + 10) % 10))
                        .font(.system(size: 44, weight: .heavy, design: .rounded))
                        .foregroundStyle(Color(hex: 0x2e2e3a)),
                    at: CGPoint(x: 0, y: offset * Metrics.digitH)
                )
            }
            x += Metrics.digitW * presence
        }
    }

    /// The coloured panel: the ruler under its indicator, the verdict and Save.
    private func scalePanel(now: Date, value: Double) -> some View {
        let tick = motion.tick.value(at: now)
        let speed = motion.speed
        return Group {
            UnevenRoundedRectangle(topLeadingRadius: Metrics.corner, style: .continuous)
                .fill(Color(hex: 0x3d35e0))
                .contentShape(Rectangle())
                .gesture(drag)
                .place(x: 0, y: Metrics.scaleY, width: Metrics.width, height: Metrics.height - Metrics.scaleY, .center)
                .accessibilityElement()
                .accessibilityLabel(categories[motion.selected].name)
                .accessibilityValue("\(currency) \(snapped(value))")
                .accessibilityAdjustableAction { direction in
                    touch()
                    settle(on: Double(snapped(value) + (direction == .increment ? step : -step)), .spring(snapSpring))
                }
            Canvas { context, _ in
                drawRuler(context, value: value, speed: speed)
            }
            .allowsHitTesting(false)
            .place(x: 0, y: Metrics.rulerY - 12, width: Metrics.width, height: 24, .center)
            Capsule()
                .fill(Color(hex: 0x2eddc0))
                .shadow(color: Color(hex: 0x2eddc0, opacity: 0.55), radius: 6)
                .scaleEffect(x: 1, y: 1 + 0.1 * tick)
                .allowsHitTesting(false)
                .place(x: Metrics.indicatorX - 3, y: Metrics.rulerY - 17, width: 6, height: 34, .center)

            ForEach(verdicts.indices, id: \.self) { i in
                if i < motion.verdictShown.count {
                    let shown = motion.verdictShown[i].value(at: now)
                    Text(verdicts[i].title)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                        .place(x: Metrics.inset + 4, y: Metrics.verdictY + motion.verdictSide[i] * Metrics.verdictShift * (1 - shown), width: 200, height: 20, .leading)
                        .opacity(shown)
                    VStack(alignment: .leading, spacing: 3) {
                        ForEach(verdicts[i].lines, id: \.self) { line in
                            Text(line)
                        }
                    }
                    .font(.system(size: 11))
                    .foregroundStyle(.white.opacity(0.7))
                    .place(x: Metrics.inset + 4, y: Metrics.verdictY + 28, width: 200, height: 32, .topLeading)
                    .opacity(shown)
                }
            }
            .lineLimit(1)
            .allowsHitTesting(false)

            Text(saveLabel)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Color(hex: 0x2e2e3a))
                .frame(width: Metrics.save, height: Metrics.save)
                .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white))
                .shadow(color: .black.opacity(0.14), radius: 10, x: 0, y: 6)
                .contentShape(Rectangle())
                .onTapGesture {
                    touch()
                    motion.savePress.retarget(1, .pulse(0.08, 0.18))
                    commit()
                    onSave?(motion.amounts)
                }
                .scaleEffect(1 - 0.06 * motion.savePress.value(at: now))
                .position(x: Metrics.width - Metrics.inset - Metrics.save / 2, y: Metrics.height - Metrics.inset - Metrics.save / 2)
                .accessibilityAddTraits(.isButton)
        }
    }

    /// The marks in sight: dot, dot, short tick, dot, dot, tall tick. A fast ruler streaks: every
    /// mark stretches along its way into a dash and the ticks sink to the dots' height.
    private func drawRuler(_ context: GraphicsContext, value: Double, speed: Double) {
        let unit = Double(max(1, step))
        let stretch = reduceMotion ? 0 : min(abs(speed) * Metrics.streak, Metrics.streakMax)
        let flat = stretch / Metrics.streakMax
        let first = max(0, Int(((value - Metrics.indicatorX / pointsPerUnit) / unit).rounded(.down)) - 1)
        let last = min(maximum / max(1, step), Int(((value + (Metrics.width - Metrics.indicatorX) / pointsPerUnit) / unit).rounded(.up)) + 1)
        guard first <= last else { return }
        var marks = Path()
        for j in first...last {
            let x = Metrics.indicatorX + (Double(j) * unit - value) * pointsPerUnit
            let tall = j % 6 == 0 ? 12.0 : j % 6 == 3 ? 6.0 : 2.6
            let height = tall + (2.6 - tall) * flat
            let width = (tall > 3 ? 2 : 2.6) + stretch
            marks.addRoundedRect(in: CGRect(x: x - width / 2, y: 12 - height / 2, width: width, height: height), cornerSize: CGSize(width: 1.3, height: 1.3))
        }
        context.fill(marks, with: .color(.white.opacity(0.6 * (1 - 0.35 * stretch / Metrics.streakMax))))
    }

    // MARK: Scrubbing

    private func reset() {
        motion.amounts = categories.map { min(max($0.amount, 0), maximum) }
        motion.selected = home
        motion.expand = categories.indices.map { Track($0 == home ? 1 : 0) }
        motion.chipPress = categories.map { _ in Track(0) }
        motion.scroll = Track(Double(home) * (Metrics.chip + Metrics.chipGap))
        let amount = motion.amounts.isEmpty ? 0 : motion.amounts[home]
        motion.amount = Track(Double(amount))
        motion.lastAmount = Double(amount)
        motion.notch = amount / max(1, step)
        var place = 1
        for k in motion.wheels.indices {
            motion.wheels[k] = Double(amount / place)
            motion.presence[k] = k == 0 || amount >= place ? 1 : 0
            place *= 10
        }
        motion.verdict = verdicts.lastIndex { amount >= $0.from } ?? 0
        motion.verdictShown = verdicts.indices.map { Track($0 == motion.verdict ? 1 : 0) }
        motion.verdictSide = verdicts.map { _ in 1 }
    }

    private func touch() {
        motion.byHand = true
        touches += 1
    }

    /// A mark has come under the indicator: it twitches.
    private func notice(_ notch: Int) {
        guard notch != motion.notch else { return }
        motion.notch = notch
        if !reduceMotion { motion.tick.retarget(1, .pulse(0.05, 0.18)) }
    }

    /// The amount rounded to a mark of the scale.
    private func snapped(_ value: Double) -> Int {
        let unit = Double(max(1, step))
        return min(max(Int((value / unit).rounded() * unit), 0), maximum)
    }

    /// Sends the ruler to `amount` (rounded to a mark) and makes it the open category's amount.
    private func settle(on amount: Double, _ kind: Track.Kind) {
        guard categories.indices.contains(motion.selected) else { return }
        let target = snapped(amount)
        motion.amount.retarget(Double(target), reduceMotion ? .jump : kind)
        guard target != motion.amounts[motion.selected] else { return }
        motion.amounts[motion.selected] = target
        onChange?(motion.selected, target)
    }

    /// Keeps where the ruler is heading as the open category's amount.
    private func commit() {
        guard categories.indices.contains(motion.selected) else { return }
        motion.amounts[motion.selected] = snapped(motion.amount.target)
    }

    /// Opens category `index`: its chip grows into the pill, the row slides it to the left edge, and
    /// the ruler runs to its amount.
    private func select(_ index: Int) {
        guard categories.indices.contains(index), index != motion.selected else { return }
        commit()
        let old = motion.selected
        motion.selected = index
        motion.expand[old].retarget(0, reduceMotion ? .jump : .spring(chipSpring))
        motion.expand[index].retarget(1, reduceMotion ? .jump : .spring(chipSpring))
        motion.scroll.retarget(Double(index) * (Metrics.chip + Metrics.chipGap), reduceMotion ? .jump : .spring(chipSpring))
        motion.amount.retarget(Double(motion.amounts[index]), reduceMotion ? .jump : .tween(Metrics.switchTime, switchCurve))
        onChange?(index, motion.amounts[index])
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                if !motion.dragging {
                    motion.dragging = true
                    motion.grabAmount = motion.amount.value(at: .now)
                    touch()
                }
                motion.amount = Track(rubber(motion.grabAmount - value.translation.width / pointsPerUnit))
            }
            .onEnded { value in
                motion.dragging = false
                touch()
                let amount = motion.amount.value(at: .now)
                // A flick carries the ruler on and friction stops it on a mark; a slow release just snaps.
                let carry = -value.velocity.width / pointsPerUnit * Metrics.carry
                if reduceMotion || abs(carry) < Double(step) {
                    settle(on: amount, .spring(snapSpring))
                } else {
                    settle(on: amount + carry, .glide(Metrics.glideTime))
                }
            }
    }

    /// Past either end the ruler gives less and less, up to four marks.
    private func rubber(_ amount: Double) -> Double {
        let over = amount < 0 ? -amount : max(0, amount - Double(maximum))
        guard over > 0 else { return amount }
        let limit = Double(step) * 4
        let give = limit * (1 - 1 / (over / limit + 1))
        return amount < 0 ? -give : Double(maximum) + give
    }

    // MARK: autoPlay

    /// The script a finger would follow: two flings up the scale, the next category, and back.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, put every amount back and start over.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                guard !motion.dragging else { return }
                restore()
                try await Task.sleep(for: .seconds(0.9))
            }
            let clock = ContinuousClock()
            var start = clock.now
            let far = Double(maximum)
            while true {
                try await clock.sleep(until: start + .seconds(1.0))
                settle(on: far * 0.45, .glide(0.9))
                try await clock.sleep(until: start + .seconds(2.4))
                settle(on: far * 0.8, .glide(0.8))
                try await clock.sleep(until: start + .seconds(3.8))
                press((home + 1) % categories.count)
                try await clock.sleep(until: start + .seconds(5.1))
                restore()
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }

    /// The script's tap on a chip.
    private func press(_ index: Int) {
        guard index != motion.selected else { return }
        motion.chipPress[index].retarget(1, .pulse(0.08, 0.18))
        select(index)
    }

    /// Back to the first category with every amount as it began.
    private func restore() {
        motion.byHand = false
        let fresh = categories.map { min(max($0.amount, 0), maximum) }
        press(home)
        motion.amounts = fresh
        motion.amount.retarget(Double(fresh[home]), .tween(Metrics.switchTime, switchCurve))
    }
}

private extension View {
    /// Puts the view in a box whose top-left corner is at `x`, `y`.
    func place(x: Double, y: Double, width: Double, height: Double, _ alignment: Alignment) -> some View {
        frame(width: width, height: height, alignment: alignment)
            .position(x: x + width / 2, y: y + height / 2)
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 468.0
    static let corner = 36.0
    static let inset = 24.0

    /// The row of chips: a square, the open pill, and the gap between two.
    static let rowY = 56.0
    static let chip = 52.0
    static let chipRadius = 18.0
    static let chipGap = 16.0
    static let pillW = 150.0
    static let pillH = 64.0

    /// The white card and the amount on it.
    static let cardY = 112.0
    static let cardRadius = 30.0
    static let amountY = 174.0
    static let digitsX = 62.0
    static let digitW = 28.0
    static let digitH = 56.0
    /// Seconds a wheel lags behind the amount, and the digits a second at which it is a full smear.
    static let wheelLag = 0.05
    static let smearAt = 30.0

    /// The coloured panel, the ruler's centre line and the indicator.
    static let scaleY = 236.0
    static let rulerY = 282.0
    static let indicatorX = 48.0
    /// Points from one mark to the next.
    static let pitch = 14.0
    /// A mark stretches by the ruler's speed times this many seconds, up to `streakMax` points.
    static let streak = 0.016
    static let streakMax = 9.0
    static let verdictY = 312.0
    static let verdictShift = 12.0
    static let verdictTime = 0.25
    static let save = 64.0

    /// A flick carries the ruler on by its speed times this many seconds, over `glideTime`.
    static let carry = 0.2
    static let glideTime = 1.0
    /// The ruler's run to another category's amount.
    static let switchTime = 0.5

    static let loop = 7.0
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

private let chipSpring = Spring(mass: 1, stiffness: 220, damping: 24)
private let snapSpring = Spring(mass: 1, stiffness: 300, damping: 26)
private let switchCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.25, y: 1))

private func grouped(_ n: Int) -> String {
    var digits = String(n)
    var out = ""
    while digits.count > 3 {
        out = "," + digits.suffix(3) + out
        digits = String(digits.dropLast(3))
    }
    return digits + out
}

/// A number heading for a target, sampled against the `TimelineView` clock.
private struct Track {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
        /// Friction: starts fast and loses speed exponentially, at rest on the target after the given time.
        case glide(Double)
        /// Up to the target in the first time and back to 0 in the second.
        case pulse(Double, Double)
    }

    private var from: Double
    private var to: Double
    private var speed = 0.0
    private var start = Date.distantPast
    private var kind = Kind.jump

    init(_ value: Double) {
        from = value
        to = value
    }

    var target: Double { to }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return from }
        switch kind {
        case .jump: return to
        case .tween(let duration, let curve): return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
        case .spring(let spring): return from + spring.value(target: to - from, initialVelocity: speed, time: t)
        case .glide(let duration): return t >= duration ? to : from + (to - from) * (1 - exp(-5 * t / duration)) / (1 - exp(-5))
        case .pulse(let up, let down): return to * (t < up ? t / up : max(0, 1 - (t - up) / down))
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return 0 }
        switch kind {
        case .spring(let spring): return spring.velocity(target: to - from, initialVelocity: speed, time: t)
        case .tween, .glide: return (value(at: date.addingTimeInterval(0.004)) - value(at: date)) / 0.004
        default: return 0
        }
    }

    /// Heads for `target` from where it is now, keeping its speed.
    mutating func retarget(_ target: Double, _ kind: Kind) {
        let now = Date.now
        let value = value(at: now)
        speed = velocity(at: now)
        from = value
        if case .pulse = kind { from = 0 }
        to = target
        start = now
        self.kind = kind
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe6e6ec).ignoresSafeArea()
        BudgetScrubScale(autoPlay: true)
    }
}
