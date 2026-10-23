import SwiftUI

private typealias D = SwipeMonthDonutData

private enum Palette {
    static let background = Color(hex: 0xf2f2f7)
    static let card = Color.white
    static let ink = Color(hex: 0x111111)
    static let mute = Color(hex: 0x8e8e93)
    static let faint = Color(hex: 0xc7c7cc)
    static let rule = Color(hex: 0xe5e5ea)
    static let track = Color(hex: 0xe3e3e8)
    static let badge = Color(hex: 0xefeff4)
}

private let margin = 20.0
private let ring = 300.0
private let stroke = 22.0
private let gap = 4.0
private let pop = 6.0
private let bar = 44.0
private let segmentedW = 172.0
private let segmentedH = 30.0
private let rowHeight = 44.0
private let dot = 7.0
private let dotActive = 7.0
private let plus = 56.0

// A swipe across this share of the ring turns one page; a fast flick turns one at most.
private let dragShare = 0.8
private let flick = 0.12
// The pan starts after 10 pt sideways and fails after 14 pt up or down; a tap allows 10 pt.
private let panStart = 10.0
private let panFail = 14.0
private let tapSlop = 10.0
private let pageSpring = Spring(damping: 24, stiffness: 200, mass: 1)
private let popSpring = Spring(damping: 13, stiffness: 260, mass: 1)
private let popIn = 0.35
private let popOut = 0.25
private let rowIn = 0.26
private let rowOut = 0.18
private let rowStagger = 0.04
private let rowShift = 12.0
private let cardResize = 0.3
private let thumbSlide = 0.22
private let settleFor = 0.3
private let lift = 0.15
private let accessibleTurn = 0.4

private func smooth(_ t: Double) -> Double { D.easeInOut(t) }
private func easeOut(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
/// Reanimated's default timing curve.
private func quad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }
// The same two curves for SwiftUI's own animations, as their usual cubic-bezier fits.
private func quadAnimation(_ duration: Double) -> Animation { .timingCurve(0.455, 0.03, 0.515, 0.955, duration: duration) }
private func outAnimation(_ duration: Double) -> Animation { .timingCurve(0.215, 0.61, 0.355, 1, duration: duration) }

private func money(_ value: Double, _ currency: String) -> String {
    currency + Int(value.rounded()).formatted(.number.grouping(.automatic).locale(Locale(identifier: "en_US")))
}

private struct Spring {
    var damping: Double
    var stiffness: Double
    var mass: Double
}

/// A number moving to a target on a timed curve or a spring, read from the timeline's date so no
/// state changes while it moves.
private struct Motion {
    private var from: Double
    private var to: Double
    private var start = Date.distantPast
    private var duration = 0.0
    private var curve: (Double) -> Double = { $0 }
    private var spring: Spring? = nil
    private var velocity = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    var target: Double { to }

    /// When the value has arrived.
    var end: Date {
        guard let spring else { return start.addingTimeInterval(duration) }
        let (z, _, a, b) = coefficients(spring)
        let amplitude = hypot(a, b)
        // The swing has shrunk below a ten-thousandth of the way: nothing on screen moves after that.
        return amplitude < 1e-4 ? start : start.addingTimeInterval(log(amplitude / 1e-4) / z)
    }

    func value(at date: Date) -> Double { sample(date).value }

    /// Both springs here are underdamped, so this is the one case to solve.
    private func coefficients(_ spring: Spring) -> (z: Double, w: Double, a: Double, b: Double) {
        let z = spring.damping / (2 * spring.mass)
        let w = sqrt(spring.stiffness / spring.mass - z * z)
        let a = from - to
        return (z, w, a, (velocity + z * a) / w)
    }

    private func sample(_ date: Date) -> (value: Double, velocity: Double) {
        let t = max(0, date.timeIntervalSince(start))
        if let spring {
            if date >= end { return (to, 0) }
            let (z, w, a, b) = coefficients(spring)
            let decay = exp(-z * t)
            let offset = decay * (a * cos(w * t) + b * sin(w * t))
            let speed = decay * (velocity * cos(w * t) - (a * w + z * b) * sin(w * t))
            return (to + offset, speed)
        }
        guard duration > 0 else { return (to, 0) }
        return (from + (to - from) * curve(min(1, t / duration)), 0)
    }

    mutating func time(to target: Double, at date: Date, duration: Double, curve: @escaping (Double) -> Double = quad) {
        from = value(at: date)
        to = target
        start = date
        self.duration = duration
        self.curve = curve
        spring = nil
    }

    mutating func spring(to target: Double, at date: Date, _ spring: Spring) {
        let now = sample(date)
        from = now.value
        velocity = now.velocity
        to = target
        start = date
        self.spring = spring
    }
}

/// One finger on the ring, from touch down to lift.
private struct Touch {
    /// The page the drag started from.
    var start: Double
    var panning = false
    var failed = false
    /// The farthest the finger got from where it went down.
    var travel = 0.0
}

/// A month of spending as a ring of round-capped segments, paged with a swipe. Between two months
/// every segment's start and length follow the finger, so categories shrink into their neighbours
/// or grow out of them; the total slides with the page and the list below re-flows. Tapping a
/// segment pops it out and shows its amount in the centre.
struct SwipeMonthDonut: View {
    static let loop = D.loop

    /// The ring's categories, in its clockwise order.
    var categories = D.categories
    /// One page each; `amounts` follow the order of `categories`.
    var months = D.months
    var currency = "$"
    var kinds = ("Expense", "Income")
    /// Swipes through the months by itself and taps one segment, every `loop` seconds.
    var autoPlay = false
    var onMonthChange: ((SwipeMonthDonutMonth, Int) -> Void)? = nil
    var onCategory: ((SwipeMonthDonutCategory?) -> Void)? = nil
    var onAdd: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    @State private var month = 0
    @State private var focused = -1
    @State private var kind = 0
    // By hand: the pager, the tapped category and how far it is out, and the kinds' thumb.
    @State private var pager = Motion(0)
    @State private var focus = -1
    @State private var out = Motion(0)
    @State private var thumb = Motion(0)
    @State private var handUntil = Date.distantPast
    @State private var handMoving = false
    // autoPlay: the loop's start, whether it is between two still stretches, and the moment it stopped at.
    @State private var automatic = false
    @State private var origin = Date.now
    @State private var autoMoving = false
    @State private var restAt = Date.now
    @State private var touch: Touch? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var pageHaptic = UISelectionFeedbackGenerator()
    @State private var popHaptic = UIImpactFeedbackGenerator(style: .light)

    private var last: Int { months.count - 1 }
    private var table: [[Double]] { D.shares(months) }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = min(ring, proxy.size.width - margin * 2) + pop * 2
            let running = automatic ? autoMoving : handMoving
            TimelineView(.animation(paused: !running)) { timeline in
                // At rest the date only has to land on the same values: the end of autoPlay's last
                // stretch, or any time after the last hand-made motion.
                let date = running ? timeline.date : automatic ? restAt : Date.distantFuture
                let now = state(at: date)
                ZStack(alignment: .top) {
                    VStack(spacing: 0) {
                        topBar(date)
                        donut(size: size, page: now.page, focus: now.focus, out: now.out)
                            .padding(.top, 28)
                        dots(page: now.page)
                            .padding(.top, 20)
                        list
                            .padding(.top, 24)
                            .padding(.horizontal, margin)
                    }
                    .padding(.top, insets.top)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

                    addButton
                        .frame(maxHeight: .infinity, alignment: .bottom)
                        .padding(.bottom, max(insets.bottom, 16) + 12)
                }
                .ignoresSafeArea()
                .onChange(of: Self.round(now.page, last)) { _, next in turned(next) }
                .onChange(of: now.out > 0.01 ? now.focus : -1) { _, next in focused = next }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            pageHaptic.prepare()
            popHaptic.prepare()
        }
        .task(id: handUntil) { await settle() }
        .task(id: Restart(on: autoPlay, touches: touches, still: still)) { await autoplay() }
    }

    // MARK: Where everything is

    private struct Frame {
        var page: Double
        var focus: Int
        var out: Double
    }

    private func state(at date: Date) -> Frame {
        guard automatic else { return Frame(page: pager.value(at: date), focus: focus, out: out.value(at: date)) }
        let t = (date.timeIntervalSince(origin).truncatingRemainder(dividingBy: D.loop) + D.loop).truncatingRemainder(dividingBy: D.loop)
        let p = still ? Self.round(D.pageAt(t), Int.max) : D.pageAt(t)
        let away: Double
        if still {
            away = t >= D.focusOn && t < D.focusOff ? 1 : 0
        } else if t < D.focusOn {
            away = 0
        } else if t < D.focusOff {
            away = D.springOut((t - D.focusOn) / popIn)
        } else {
            away = 1 - D.clamp01((t - D.focusOff) / popOut)
        }
        return Frame(page: p, focus: D.focusCategory, out: away)
    }

    /// The page the pager is nearest, rounding halves up as JavaScript does.
    private static func round(_ page: Double, _ last: Int) -> Double {
        Double(min(last, max(0, Int(floor(page + 0.5)))))
    }

    /// The shares between the two pages the pager is between.
    private func shares(_ page: Double) -> [Double] {
        let p = min(Double(last), max(0, page))
        let from = min(last, Int(floor(p)))
        let to = min(last, from + 1)
        let u = p - Double(from)
        return zip(table[from], table[to]).map { $0 + ($1 - $0) * u }
    }

    // MARK: The screen

    private func topBar(_ date: Date) -> some View {
        HStack {
            Button(action: takeOver) {
                Image(systemName: "ellipsis").font(.system(size: 18, weight: .bold)).foregroundStyle(Palette.mute).frame(width: 22, height: 22)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("More")
            Spacer()
            segmented(date)
            Spacer()
            Button(action: takeOver) {
                Image(systemName: "clock").font(.system(size: 19)).foregroundStyle(Palette.mute).frame(width: 24, height: 24)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("History")
        }
        .padding(.horizontal, margin)
        .frame(height: bar)
    }

    private func segmented(_ date: Date) -> some View {
        let labels = [kinds.0, kinds.1]
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .fill(.white)
                .shadow(color: .black.opacity(0.12), radius: 1.5, x: 0, y: 1)
                .frame(width: segmentedW / 2 - 2, height: segmentedH - 4)
                .offset(x: 2 + thumb.value(at: date) * (segmentedW / 2 - 2), y: 2)
            HStack(spacing: 0) {
                ForEach(labels.indices, id: \.self) { index in
                    Button {
                        takeOver()
                        kind = index
                        if still { thumb = Motion(Double(index)) } else { thumb.time(to: Double(index), at: .now, duration: thumbSlide, curve: smooth) }
                        run(until: thumb.end)
                    } label: {
                        Text(labels[index])
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Palette.ink)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(kind == index ? [.isSelected] : [])
                }
            }
            .padding(2)
        }
        .frame(width: segmentedW, height: segmentedH)
        .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(Palette.track))
    }

    private func donut(size: Double, page: Double, focus: Int, out: Double) -> some View {
        let radius = size / 2 - pop - stroke / 2 - 1
        let inner = (radius - stroke / 2) * 2 - 8
        let current = months[month]
        let shown = focused >= 0 ? categories[focused] : nil
        let shownAmount = focused >= 0 ? current.amounts[focused] : 0
        let away = D.clamp01(out)
        return ZStack {
            SwipeMonthDonutDonutRing(
                size: size, stroke: stroke, gap: gap, pop: pop, colors: categories.map(\.color),
                shares: shares(page), focus: focus, out: out
            )
            ZStack {
                ZStack {
                    ForEach(months.indices, id: \.self) { index in
                        monthPage(months[index], away: Double(index) - page, width: inner)
                    }
                }
                .opacity(1 - away)
                VStack(spacing: 0) {
                    if let shown {
                        Text("\(shown.icon) \(shown.name)").font(.system(size: 15)).foregroundStyle(Palette.mute).frame(height: 20)
                        amountText(money(shownAmount, currency))
                        Text("\(current.total > 0 ? Int((shownAmount / current.total * 100).rounded()) : 0)% of the month")
                            .font(.system(size: 15).monospacedDigit()).foregroundStyle(Palette.mute).frame(height: 20)
                    }
                }
                .opacity(away)
                .scaleEffect(0.94 + 0.06 * away)
            }
            .frame(width: inner, height: inner)
            .clipShape(Circle())
            .allowsHitTesting(false)
        }
        .frame(width: size, height: size)
        .contentShape(Rectangle())
        .gesture(ringGesture(size: size, radius: radius, page: page))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(current.label), \(money(current.total, currency)) spent")
        .accessibilityAdjustableAction { direction in
            let next = min(last, max(0, month + (direction == .increment ? 1 : -1)))
            let now = Date.now
            takeOverQuietly(now)
            pager.time(to: Double(next), at: now, duration: still ? 0 : accessibleTurn, curve: smooth)
            run(until: pager.end)
            touched()
        }
    }

    private func amountText(_ value: String) -> some View {
        Text(value)
            .font(.system(size: 44, weight: .bold).monospacedDigit())
            .tracking(-0.8)
            .foregroundStyle(Palette.ink)
            .lineLimit(1)
            .fixedSize()
            .frame(height: 52)
            .padding(.top, 4)
    }

    /// The centre of one page: the month, its total, and the change from the month before.
    private func monthPage(_ month: SwipeMonthDonutMonth, away: Double, width: Double) -> some View {
        VStack(spacing: 0) {
            Text(month.label).font(.system(size: 15)).foregroundStyle(Palette.mute).frame(height: 20)
            amountText(money(month.total, currency))
            HStack(spacing: 5) {
                Image(systemName: month.change.up ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                    .font(.system(size: 15))
                    .foregroundStyle(Palette.faint)
                Text("\(money(month.change.amount, currency)) (\(month.change.percent)%)")
                    .font(.system(size: 15).monospacedDigit())
                    .foregroundStyle(Palette.mute)
                    .frame(height: 20)
            }
            .padding(.top, 4)
        }
        .fixedSize()
        .frame(width: width, height: width)
        // Reduced motion: pages cross-fade in place instead of sliding.
        .opacity(still ? D.clamp01(1 - abs(away) * 2) : D.clamp01(1.4 - abs(away)))
        .offset(x: still ? 0 : away * width)
    }

    private func dots(page: Double) -> some View {
        HStack(spacing: 8) {
            ForEach(months.indices, id: \.self) { index in
                let near = D.clamp01(1 - abs(Double(index) - page))
                Circle()
                    .fill(Palette.ink)
                    .frame(width: dot, height: dot)
                    .scaleEffect((dot + (dotActive - dot) * near) / dot)
                    .opacity(0.3 + 0.7 * near)
            }
        }
    }

    private struct Row: Identifiable {
        var index: Int
        var amount: Double
        var count: Int
        var id: Int { index }
    }

    private var rows: [Row] {
        let current = months[month]
        return categories.indices
            .map { Row(index: $0, amount: current.amounts[$0], count: current.counts[$0]) }
            .filter { $0.amount > 0 }
            .sorted { $0.amount > $1.amount }
    }

    private var list: some View {
        let rows = rows
        return VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.id) { order, row in
                rowView(row, order: order)
                    .transition(still ? .identity : rowTransition(order))
            }
        }
        .padding(.horizontal, 14)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    /// Rows come in from 12 points above with a stagger, and leave 12 points down.
    private func rowTransition(_ order: Int) -> AnyTransition {
        let delay = Double(order) * rowStagger
        return .asymmetric(
            insertion: AnyTransition.opacity.animation(quadAnimation(rowIn).delay(delay))
                .combined(with: AnyTransition.offset(y: -rowShift).animation(outAnimation(rowIn).delay(delay))),
            removal: AnyTransition.opacity.animation(quadAnimation(rowOut))
                .combined(with: AnyTransition.offset(y: rowShift).animation(outAnimation(rowOut)))
        )
    }

    private func rowView(_ row: Row, order: Int) -> some View {
        let category = categories[row.index]
        return HStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 3).fill(category.color).frame(width: 10, height: 10).padding(.trailing, 14)
            Text(category.icon).font(.system(size: 19)).frame(width: 26, height: 24, alignment: .leading).padding(.trailing, 10)
            HStack(spacing: 0) {
                Text(category.name).font(.system(size: 16)).foregroundStyle(Palette.ink).lineLimit(1).frame(height: 21)
                Text("\(row.count)")
                    .font(.system(size: 12).monospacedDigit())
                    .foregroundStyle(Palette.mute)
                    .padding(.horizontal, 6)
                    .frame(minWidth: 20, minHeight: 20)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Palette.badge))
                    .padding(.leading, 8)
                Spacer(minLength: 0)
                Text(money(row.amount, currency)).font(.system(size: 16).monospacedDigit()).foregroundStyle(Palette.mute).padding(.trailing, 6)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Palette.faint).frame(width: 16)
            }
            .frame(height: rowHeight)
            .overlay(alignment: .top) {
                if order > 0 { Rectangle().fill(Palette.rule).frame(height: 1 / 3) }
            }
        }
        .frame(height: rowHeight)
    }

    private var addButton: some View {
        Button {
            takeOver()
            onAdd?()
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 24, weight: .medium))
                .foregroundStyle(.white)
                .frame(width: plus, height: plus)
                .background(Circle().fill(Palette.ink))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Add expense")
    }

    // MARK: Touch

    private func ringGesture(size: Double, radius: Double, page: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if touch == nil {
                    // A finger down stops autoPlay and the pager where they are.
                    takeOverQuietly(now)
                    pager = Motion(pager.value(at: now))
                    touch = Touch(start: Self.round(pager.value(at: now), Int.max))
                }
                guard var finger = touch else { return }
                let dx = value.translation.width
                let dy = value.translation.height
                finger.travel = max(finger.travel, hypot(dx, dy))
                if !finger.panning && !finger.failed {
                    if abs(dx) > panStart {
                        finger.panning = true
                        out.time(to: 0, at: now, duration: still ? 0 : lift)
                        run(until: out.end)
                    } else if abs(dy) > panFail {
                        finger.failed = true
                    }
                }
                touch = finger
                guard finger.panning else { return }
                var p = finger.start - dx / (size * dragShare)
                // Past the first or last page the ring resists.
                if p < 0 { p /= 3 }
                if p > Double(last) { p = Double(last) + (p - Double(last)) / 3 }
                pager = Motion(p)
            }
            .onEnded { value in
                defer {
                    touch = nil
                    touched()
                }
                guard let finger = touch else { return }
                let now = Date.now
                if finger.panning {
                    let flung = pager.value(at: now) - value.velocity.width / (size * dragShare) * flick
                    let target = min(Double(last), max(0, min(finger.start + 1, max(finger.start - 1, Self.round(flung, Int.max)))))
                    if still { pager = Motion(target) } else { pager.spring(to: target, at: now, pageSpring) }
                    run(until: pager.end)
                    if target != finger.start {
                        pageHaptic.selectionChanged()
                        pageHaptic.prepare()
                    }
                } else if !finger.failed && finger.travel <= tapSlop {
                    tap(value.location, size: size, radius: radius, now: now)
                }
            }
    }

    private func tap(_ location: CGPoint, size: Double, radius: Double, now: Date) {
        let dx = location.x - size / 2
        let dy = location.y - size / 2
        var hit = -1
        if abs(hypot(dx, dy) - radius) < stroke {
            // Clockwise from the top, in turns.
            let turn = (atan2(dy, dx) / (.pi * 2) + 1.25).truncatingRemainder(dividingBy: 1)
            var at = 0.0
            for (i, share) in shares(pager.value(at: now)).enumerated() {
                if share > 0 && turn >= at && turn < at + share { hit = i }
                at += share
            }
        }
        let showing = out.value(at: now) > 0.5 ? focus : -1
        if hit < 0 || hit == showing {
            out.time(to: 0, at: now, duration: still ? 0 : popOut)
            run(until: out.end)
            onCategory?(nil)
            return
        }
        focus = hit
        out = Motion(0)
        if still { out = Motion(1) } else { out.spring(to: 1, at: now, popSpring) }
        run(until: out.end)
        popHaptic.impactOccurred()
        popHaptic.prepare()
        onCategory?(categories[hit])
    }

    /// The pager's page changed: the list re-flows and the month is reported.
    private func turned(_ next: Double) {
        let index = Int(next)
        guard index != month else { return }
        if still {
            month = index
        } else {
            withAnimation(quadAnimation(cardResize)) { month = index }
        }
        onMonthChange?(months[index], index)
    }

    /// autoPlay hands over to the hand-made state where it is.
    private func takeOverQuietly(_ now: Date) {
        guard automatic else { return }
        let frame = state(at: now)
        pager = Motion(frame.page)
        out = Motion(frame.out)
        focus = frame.focus
        automatic = false
        autoMoving = false
    }

    /// A tap off the ring stops autoPlay too: the pager settles on the nearest page.
    private func takeOver() {
        let now = Date.now
        if automatic {
            takeOverQuietly(now)
            pager.time(to: Self.round(pager.value(at: now), Int.max), at: now, duration: still ? 0 : settleFor, curve: smooth)
            out.time(to: 0, at: now, duration: still ? 0 : popOut)
            run(until: max(pager.end, out.end))
        }
        touched()
    }

    private func touched() {
        lastTouch = .now
        touches += 1
    }

    private func run(until date: Date) {
        handMoving = true
        handUntil = max(handUntil, date)
    }

    private func settle() async {
        do {
            try await wait(until: handUntil)
            handMoving = false
        } catch {}
    }

    private struct Restart: Equatable {
        var on: Bool
        var touches: Int
        var still: Bool
    }

    /// Plays the loop from its start; after a touch, first waits, then glides home.
    private func autoplay() async {
        guard autoPlay else {
            automatic = false
            return
        }
        do {
            if touches > 0 {
                try await wait(until: lastTouch.addingTimeInterval(D.userHold))
                // A finger still on the ring keeps the screen; its lift starts the wait again.
                guard touch == nil else { return }
                let now = Date.now
                pager.time(to: 0, at: now, duration: still ? 0 : D.returnFor, curve: smooth)
                out.time(to: 0, at: now, duration: still ? 0 : D.returnFor, curve: smooth)
                run(until: now.addingTimeInterval(D.returnFor))
                try await wait(until: lastTouch.addingTimeInterval(D.userHold + D.returnFor))
            }
            var start = Date.now
            origin = start
            restAt = start
            autoMoving = false
            automatic = true
            while true {
                for (at, moving) in events {
                    let date = start.addingTimeInterval(at)
                    try await wait(until: date)
                    restAt = date
                    autoMoving = moving
                }
                start = start.addingTimeInterval(D.loop)
                try await wait(until: start)
                restAt = start
            }
        } catch {}
    }

    /// When autoPlay starts and stops moving, in seconds from the start of the loop. Under reduced
    /// motion nothing moves, but the screen still has to look again when a page or the pop flips.
    private var events: [(Double, Bool)] {
        if still {
            // Just past the middle of each swipe, where the rounded page flips.
            return (D.swipes.map { ($0.at + $0.duration / 2 + 0.01, false) } + [(D.focusOn, false), (D.focusOff, false)]).sorted { $0.0 < $1.0 }
        }
        var spans = D.swipes.map { ($0.at, $0.at + $0.duration) }
        spans += [(D.focusOn, D.focusOn + popIn), (D.focusOff, D.focusOff + popOut)]
        spans.sort { $0.0 < $1.0 }
        // Spans that touch run as one.
        var merged: [(Double, Double)] = []
        for span in spans {
            if let tail = merged.last, span.0 <= tail.1 { merged[merged.count - 1].1 = max(tail.1, span.1) } else { merged.append(span) }
        }
        return merged.flatMap { [($0.0, true), ($0.1, false)] }
    }

    private func wait(until date: Date) async throws {
        while date > .now {
            try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero)
        }
        try Task.checkCancellation()
    }
}

#Preview {
    SwipeMonthDonut(autoPlay: true)
}
