import SwiftUI

struct AccountScrubTabBarMessage {
    var sender: String
    var subject: String
    var time: String
}

struct AccountScrubTabBarAccount {
    var address: String
    /// The badge and the pictures of its senders.
    var color: Color
    var messages: [AccountScrubTabBarMessage]

    static let samples = [
        AccountScrubTabBarAccount(address: "nora.lind@inbox.example", color: Color(hex: 0xF5B5E6), messages: [
            .init(sender: "Ida Holm", subject: "Proofs for Friday", time: "9:41"),
            .init(sender: "Billing", subject: "Your receipt for October", time: "9:05"),
            .init(sender: "Marek Dvorak", subject: "Dinner on Saturday?", time: "8:15"),
            .init(sender: "Trail Club", subject: "Route notes for the weekend", time: "Yesterday"),
            .init(sender: "Lena Ortiz", subject: "Photos from the lake", time: "Yesterday"),
            .init(sender: "Library", subject: "Your hold is ready", time: "Mon"),
            .init(sender: "Sam Whitlow", subject: "Re: spare keys", time: "Sun"),
        ]),
        AccountScrubTabBarAccount(address: "nora@lind.studio", color: Color(hex: 0xF2D675), messages: [
            .init(sender: "Jonas Brandt", subject: "Logo, round two", time: "10:02"),
            .init(sender: "Print Room", subject: "Quote for 200 posters", time: "9:20"),
            .init(sender: "Aiko Tanabe", subject: "Invoice 0142", time: "8:40"),
            .init(sender: "Studio Notes", subject: "Shared folder updated", time: "Yesterday"),
            .init(sender: "Petra Novak", subject: "Type samples attached", time: "Tue"),
            .init(sender: "Hale & Finch", subject: "Contract, signed copy", time: "Mon"),
            .init(sender: "Workshop", subject: "Seats for November", time: "Sun"),
        ]),
        AccountScrubTabBarAccount(address: "team@oakline.example", color: Color(hex: 0x62E6C3), messages: [
            .init(sender: "Deploy Bot", subject: "Build 418 passed", time: "10:14"),
            .init(sender: "Rhea Malik", subject: "Sprint review moved", time: "9:36"),
            .init(sender: "Support", subject: "Three new tickets", time: "8:58"),
            .init(sender: "Tomas Reyes", subject: "Design handoff", time: "Yesterday"),
            .init(sender: "Calendar", subject: "Planning, Thursday 10:00", time: "Yesterday"),
            .init(sender: "Noor Haddad", subject: "Budget sheet v3", time: "Tue"),
            .init(sender: "Oakline HR", subject: "Holiday schedule", time: "Mon"),
        ]),
    ]
}

enum AccountScrubTabBarIcon {
    case home, search, tray, compose
}

struct AccountScrubTabBarTab {
    var title: String
    var icon: AccountScrubTabBarIcon

    static let samples = [
        AccountScrubTabBarTab(title: "Inbox", icon: .home),
        AccountScrubTabBarTab(title: "Search", icon: .search),
        AccountScrubTabBarTab(title: "Archive", icon: .tray),
        AccountScrubTabBarTab(title: "Drafts", icon: .compose),
    ]
}

/// A mail screen whose tab bar ends in an account badge. Holding the badge turns the whole bar into
/// an account switcher: the icons blur away, the addresses stand in a wheel beside the badge, and
/// dragging up or down scrubs through them while the page follows. A tap on the badge steps to the
/// next account.
struct AccountScrubTabBar: View {
    /// The accounts the badge scrubs through, first to last.
    var accounts = AccountScrubTabBarAccount.samples
    /// The entry after the last account, which shows every account's mail.
    var allLabel = "All accounts"
    /// The items before the badge; the first one is open.
    var tabs = AccountScrubTabBarTab.samples
    /// Holds the badge and scrubs through the accounts by itself every `Motion.loop` seconds.
    var autoPlay = false
    /// Called with the place of the account under the slot; `accounts.count` is "All accounts".
    var onSelectAccount: ((Int) -> Void)? = nil
    var onSelectTab: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var tab = 0
    /// The entry under the slot: an account, or `accounts.count` for all of them.
    @State private var entry = 0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// The autoPlay loop makes no haptics; a touch does.
    @State private var byHand = false

    /// 0 as a tab bar, 1 as the account switcher.
    @State private var open = Tween(0)
    /// The entry the wheel stood on when the hold began; nil while nothing holds the badge.
    @State private var anchor: Double? = nil
    /// How far the holding finger has dragged. A real finger jumps here, the drawn one glides.
    @State private var drag = Tween(0)
    /// The wheel when the finger does not carry it: on its way to an entry, or resting on it.
    @State private var settle = Settle()
    /// The finger the autoPlay loop draws: down on the badge, and how visible.
    @State private var auto = false
    @State private var shown = Tween(0)
    /// A real finger: when it came down, whether its hold has engaged, and the wait for that.
    @State private var pressedAt: Date? = nil
    @State private var gripped = false
    @State private var gripTask: Task<Void, Never>? = nil
    /// The timeline runs only while something moves.
    @State private var moving = false
    @State private var restTask: Task<Void, Never>? = nil
    @State private var impact = UIImpactFeedbackGenerator(style: .light)
    @State private var selection = UISelectionFeedbackGenerator()

    /// The wheel after a release or a tap: a spring to an entry, or a short fade without movement.
    private struct Settle {
        var from = 0.0
        var to = 0.0
        var start = Date.distantPast
        var springy = true

        func value(at date: Date) -> Double {
            let t = max(date.timeIntervalSince(start), 0)
            if !springy { return from + (to - from) * min(t / M.pageFade, 1) }
            // Long after the spring has come to rest.
            guard t < 3 else { return to }
            return from + M.snap.value(target: to - from, initialVelocity: 0, time: t)
        }
    }

    private var last: Int { accounts.count }
    private var labels: [String] { accounts.map(\.address) + [allLabel] }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let bottom = max(Metrics.barBottom, insets.bottom - 4)
            let barWidth = proxy.size.width - Metrics.barMargin * 2
            let slot = barWidth / Double(tabs.count + 1)

            ZStack(alignment: .top) {
                Palette.page.ignoresSafeArea()

                page
                    .padding(.top, Metrics.titleTop)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .clipped()

                TimelineView(.animation(paused: !moving)) { timeline in
                    let date = timeline.date
                    let opened = open.value(at: date)
                    let dy = drag.value(at: date)
                    let nearest = anchor.map { nearestEntry($0 - dy / Metrics.step) } ?? entry

                    bar(open: opened, wheel: wheel(at: date), width: barWidth, slot: slot)
                        .overlay(alignment: .trailing) {
                            let seen = shown.value(at: date)
                            Circle()
                                .fill(.white.opacity(Metrics.fingerOpacity * seen))
                                .frame(width: Metrics.finger, height: Metrics.finger)
                                .scaleEffect(1.25 - 0.25 * seen)
                                .offset(x: -(slot - Metrics.finger) / 2, y: dy)
                                .allowsHitTesting(false)
                        }
                        // The page follows the entry that is nearest the slot while a finger holds the wheel.
                        .onChange(of: nearest) { _, now in
                            if anchor != nil { select(now) }
                        }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, bottom - insets.bottom)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { impact.prepare() }
        .onDisappear {
            gripTask?.cancel()
            restTask?.cancel()
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The page

    private var page: some View {
        VStack(alignment: .leading, spacing: 0) {
            // A new tab or account fades in over the old one.
            ZStack(alignment: .topLeading) {
                Text(tabs[tab].title)
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                    .frame(height: 41)
                    .id(tab)
                    .transition(.opacity)
            }
            .padding(.horizontal, Metrics.margin)
            .padding(.bottom, 4)

            ZStack(alignment: .topLeading) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(labels[entry])
                        .font(.system(size: 15))
                        .foregroundStyle(Palette.mute)
                        .lineLimit(1)
                        .frame(height: 20)
                        .padding(.horizontal, Metrics.margin)
                        .padding(.bottom, 16)
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        self.row(row)
                    }
                }
                .id(tab * (last + 1) + entry)
                .transition(.opacity)
            }
        }
    }

    private func row(_ row: Row) -> some View {
        HStack(spacing: 12) {
            Text(String(row.message.sender.prefix(1)))
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(row.color)
                .frame(width: Metrics.avatar, height: Metrics.avatar)
                .background(Circle().fill(row.color.opacity(0.25)))
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(row.message.sender)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Palette.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(row.message.time)
                        .font(.system(size: 13))
                        .foregroundStyle(Palette.faint)
                }
                .frame(height: 21)
                Text(row.message.subject)
                    .font(.system(size: 15))
                    .foregroundStyle(Palette.mute)
                    .frame(height: 20)
            }
            .lineLimit(1)
            .frame(height: Metrics.row)
            .overlay(alignment: .bottom) {
                Palette.hairline.frame(height: 1 / displayScale)
            }
        }
        .padding(.horizontal, Metrics.margin)
        .accessibilityElement(children: .combine)
    }

    private struct Row {
        let message: AccountScrubTabBarMessage
        let color: Color
    }

    /// The rows of the entry: an account's own mail, or every account's in turn for the last entry.
    private var rows: [Row] {
        let lists = accounts.map { account in account.messages.map { Row(message: $0, color: account.color) } }
        var rows: [Row] = []
        if entry < last {
            rows = lists[entry]
        } else {
            let longest = lists.map(\.count).max() ?? 0
            for i in 0..<longest {
                for list in lists.reversed() where i < list.count { rows.append(list[i]) }
            }
        }
        // A stand-in for the other tabs' own screens: the same mail, starting further down.
        let from = rows.isEmpty ? 0 : (tab * 2) % rows.count
        return Array((rows[from...] + rows[..<from]).prefix(Metrics.rows))
    }

    // MARK: The bar

    private func bar(open: Double, wheel: Double, width: Double, slot: Double) -> some View {
        ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                ForEach(tabs.indices, id: \.self) { i in
                    Button {
                        press(i)
                    } label: {
                        AccountScrubTabBarGlyph(icon: tabs[i].icon, active: i == tab)
                            .frame(width: slot, height: Metrics.barHeight)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(tabs[i].title)
                    .accessibilityAddTraits(i == tab ? .isSelected : [])
                }
            }
            .blur(radius: reduceMotion ? 0 : Metrics.iconBlur * open)
            .opacity(1 - (reduceMotion ? 1 : 1 - Metrics.iconFade) * open)
            // The icons are not there to be pressed while the bar is the switcher.
            .allowsHitTesting(open < 0.5)

            // The wheel of addresses: each rides past the slot, bright only while it is in it.
            ForEach(labels.indices, id: \.self) { i in
                let away = min(1, abs(Double(i) - wheel))
                Text(labels[i])
                    .font(.system(size: 15))
                    .foregroundStyle(Palette.address)
                    .lineLimit(1)
                    .frame(
                        width: max(0, width - slot / 2 - Metrics.badge * Metrics.swell / 2 - Metrics.addressGap),
                        height: Metrics.barHeight, alignment: .trailing
                    )
                    .offset(
                        x: reduceMotion ? 0 : (1 - open) * Metrics.addressSlide,
                        y: reduceMotion ? 0 : (Double(i) - wheel) * Metrics.addressPitch
                    )
                    .opacity(open * (reduceMotion ? 1 - away : 1 - (1 - Metrics.addressAway) * away))
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }

            ZStack {
                ForEach(labels.indices, id: \.self) { i in
                    badge(i)
                        .scaleEffect(1 + (Metrics.swell - 1) * open)
                        .offset(y: reduceMotion ? 0 : (Double(i) - wheel) * Metrics.badgePitch)
                        .opacity(reduceMotion ? 1 - min(1, abs(Double(i) - wheel)) : 1)
                }
            }
            .frame(width: slot, height: Metrics.barHeight)
            .contentShape(Rectangle())
            // One gesture from the first contact, so that the drag is the bar's once the hold engages.
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if pressedAt == nil { fingerDown() }
                        if gripped { drag.retarget(value.translation.height, duration: 0, curve: .linear) }
                    }
                    .onEnded { _ in fingerUp() }
            )
            .offset(x: slot * Double(tabs.count))
            .accessibilityElement()
            .accessibilityLabel("Account")
            .accessibilityValue(labels[entry])
            .accessibilityHint("Tap for the next account, or hold and drag to pick one")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { step() }
        }
        .frame(width: width, height: Metrics.barHeight, alignment: .topLeading)
        .background(Palette.bar)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.barRadius, style: .continuous))
    }

    /// One badge of the column: a number on the account's colour, or a dot for each account.
    @ViewBuilder
    private func badge(_ index: Int) -> some View {
        if index < last {
            Text(String(index + 1))
                .font(.system(size: 12, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Palette.bar)
                .frame(width: Metrics.badge, height: Metrics.badge)
                .background(Circle().fill(accounts[index].color))
        } else {
            let colors = Array(accounts.prefix(3).map(\.color).reversed())
            ZStack(alignment: .topLeading) {
                // The dots step down to the right, each over the one before.
                ForEach(colors.indices, id: \.self) { i in
                    let at = colors.count > 1 ? Double(i) / Double(colors.count - 1) : 0.5
                    Circle()
                        .fill(colors[i])
                        .overlay(Circle().strokeBorder(Palette.bar, lineWidth: 1.5))
                        .frame(width: Metrics.dot, height: Metrics.dot)
                        .offset(x: at * (Metrics.badge - Metrics.dot + 2) - 1, y: 1.5 + at * (Metrics.badge - Metrics.dot - 3))
                }
            }
            .frame(width: Metrics.badge, height: Metrics.badge, alignment: .topLeading)
        }
    }

    // MARK: The wheel

    private func nearestEntry(_ raw: Double) -> Int {
        min(last, max(0, Int(raw.rounded())))
    }

    /// Where the wheel stands for a finger that asks for `raw`: held back more and more past its ends.
    private func rubber(_ raw: Double) -> Double {
        let end = Double(last)
        let over = raw < 0 ? -raw : raw > end ? raw - end : 0
        let give = (1 - 1 / (over * 0.55 / Metrics.rubber + 1)) * Metrics.rubber
        return raw < 0 ? -give : raw > end ? end + give : raw
    }

    /// Where the columns of badges and addresses stand, in entries; whole numbers at rest.
    private func wheel(at date: Date) -> Double {
        // Without movement the badge and the address change by a short fade, one step at a time.
        guard let anchor, !reduceMotion else { return settle.value(at: date) }
        return rubber(anchor - drag.value(at: date) / Metrics.step)
    }

    private func engage() {
        let now = Date.now
        settle = Settle(from: Double(entry), to: Double(entry), start: now, springy: !reduceMotion)
        anchor = Double(entry)
        drag = Tween(0)
        open.retarget(1, at: now, duration: M.openIn, curve: Ease.out)
        wake()
    }

    private func release() {
        let now = Date.now
        settle = Settle(from: wheel(at: now), to: Double(entry), start: now, springy: !reduceMotion)
        anchor = nil
        // The bar stays a switcher for a moment, as it stands, and then closes.
        open.retarget(open.value(at: now), at: now, duration: 0, curve: .linear)
        open.retarget(0, at: now + M.closeAfter, duration: M.closeIn, curve: Ease.out)
        wake(restAfter: M.closeAfter + M.closeIn + 0.4)
    }

    private func select(_ index: Int) {
        guard index != entry else { return }
        if reduceMotion {
            let now = Date.now
            settle = Settle(from: settle.value(at: now), to: Double(index), start: now, springy: false)
        }
        withAnimation(.linear(duration: M.pageFade)) { entry = index }
        if byHand { selection.selectionChanged() }
        onSelectAccount?(index)
    }

    /// A plain tap on the badge: the next entry, and round to the first after the last.
    private func step() {
        move(to: (entry + 1) % (last + 1))
    }

    /// The wheel goes to an entry with no finger on it.
    private func move(to index: Int) {
        let now = Date.now
        settle = Settle(from: settle.value(at: now), to: Double(index), start: now, springy: !reduceMotion)
        select(index)
        wake(restAfter: 1)
    }

    private func press(_ index: Int) {
        touched()
        guard index != tab else { return }
        selection.selectionChanged()
        withAnimation(.linear(duration: M.pageFade)) { tab = index }
        onSelectTab?(index)
    }

    // MARK: Touches

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        byHand = true
        if auto {
            auto = false
            release()
        }
        shown.retarget(0, duration: M.fingerOut, curve: .linear)
        touches += 1
    }

    private func fingerDown() {
        touched()
        pressedAt = .now
        gripTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.hold))
            guard !Task.isCancelled else { return }
            gripped = true
            engage()
            impact.impactOccurred()
        }
    }

    private func fingerUp() {
        gripTask?.cancel()
        if gripped {
            gripped = false
            release()
        } else if let pressedAt, Date.now.timeIntervalSince(pressedAt) < M.hold {
            step()
        }
        pressedAt = nil
        touched()
    }

    private func wake(restAfter seconds: Double? = nil) {
        restTask?.cancel()
        moving = true
        guard let seconds else { return }
        restTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            if !Task.isCancelled { moving = false }
        }
    }

    // MARK: The loop

    /// The finger the autoPlay loop draws does what a real one does, through the same functions.
    private func perform(_ step: M.Finger) {
        switch step {
        case .down:
            drag = Tween(0)
            shown.retarget(1, duration: M.fingerIn, curve: .linear)
            wake()
        case .hold:
            engage()
            auto = true
        case .drag(let to):
            drag.retarget(-to * Metrics.step, duration: M.move, curve: M.easeInOut)
        case .up:
            auto = false
            release()
            shown.retarget(0, duration: M.fingerOut, curve: .linear)
        }
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            // A finger that is still holding the badge starts the wait again when it lifts.
            guard !Task.isCancelled, pressedAt == nil else { return }
            // The loop starts over from the first account and the first tab.
            byHand = false
            withAnimation(.linear(duration: M.pageFade)) { tab = 0 }
            move(to: 0)
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for step in M.steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                perform(step.finger)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

private typealias M = AccountScrubTabBarMotion

enum AccountScrubTabBarMotion {
    static let loop = 10.0
    /// Seconds a press on the badge lasts before it becomes a scrub.
    static let hold = 0.25
    static let openIn = 0.25
    static let closeAfter = 0.25
    static let closeIn = 0.3
    static let pageFade = 0.15
    static let snap = Spring(mass: 1, stiffness: 300, damping: 26)
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    /// The loop starts again this long after the last touch.
    static let userHold = 3.0
    /// Each drag of the drawn finger takes this long.
    static let move = 0.4
    static let fingerIn = 0.15
    static let fingerOut = 0.2

    enum Finger {
        case down, hold, up
        case drag(Double)
    }

    /// The finger of the autoPlay loop: when it lands on the badge, how many accounts it has dragged
    /// to since (up the screen is later in the list), and when it lifts.
    static let script: [(at: Double, finger: Finger)] = [
        (0.6, .down),
        (1.1, .drag(1)),
        (1.8, .drag(2)),
        (2.5, .drag(3)),
        (3.5, .drag(2)),
        (4.2, .drag(1)),
        (5.0, .up),
        (6.3, .down),
        (6.8, .drag(1)),
        (7.5, .drag(0)),
        (8.1, .drag(-1)),
        (8.9, .up),
    ]

    /// The script with the moment each press has lasted long enough to engage.
    static let steps: [(at: Double, finger: Finger)] = script.flatMap { step -> [(at: Double, finger: Finger)] in
        if case .down = step.finger { return [step, (step.at + hold, .hold)] }
        return [step]
    }
}

private enum Palette {
    static let page = Color(hex: 0x0A0A0A)
    static let bar = Color(hex: 0x1A1A1A)
    static let ink = Color.white
    static let mute = Color(hex: 0x8E8E93)
    static let faint = Color(hex: 0x636366)
    static let hairline = Color(hex: 0x1C1C1E)
    static let icon = Color(hex: 0xB4B4B8)
    static let address = Color(hex: 0xD0D0D4)
}

private enum Metrics {
    static let margin = 20.0
    static let titleTop = 14.0
    static let row = 72.0
    static let rows = 7
    static let avatar = 40.0
    static let barMargin = 16.0
    static let barHeight = 56.0
    static let barRadius = 24.0
    /// From the bottom of the screen, where the home indicator leaves room for it.
    static let barBottom = 30.0
    static let iconBox = 22.0
    static let stroke = 1.8
    /// The open tab's lines are this much heavier.
    static let strokeOn = 1.35
    static let badge = 22.0
    /// The badge under the finger.
    static let swell = 1.25
    static let dot = 13.0
    /// How far apart two accounts are in the column of badges and in the column of addresses: a
    /// neighbouring badge is outside the bar, a neighbouring address is cut in half by its edge.
    static let badgePitch = 44.0
    static let addressPitch = 28.0
    static let addressGap = 20.0
    static let addressSlide = 12.0
    /// How much of an address is left one place away from the slot.
    static let addressAway = 0.44
    /// Finger travel that moves the wheel by one account.
    static let step = 34.0
    /// How far past its ends the wheel can be pulled, in accounts.
    static let rubber = 0.8
    static let iconBlur = 10.0
    static let iconFade = 0.15
    static let finger = 44.0
    static let fingerOpacity = 0.45
}

/// An icon of the bar in a 22-point box, an outline that fills when its tab is open.
private struct AccountScrubTabBarGlyph: View {
    let icon: AccountScrubTabBarIcon
    let active: Bool

    /// A body is an outline that fills when its tab is open; an inner line lies on a body and is cut
    /// out of the filled one; a gap clears the bar's colour under a line.
    private enum Kind {
        case body, line, inner, gap
    }

    private struct Part {
        let kind: Kind
        let path: Path
        var width = Metrics.stroke

        init(_ kind: Kind, _ d: String, width: Double = Metrics.stroke) {
            self.kind = kind
            self.path = accountScrubTabBarPath(d)
            self.width = width
        }
    }

    private static let parts: [AccountScrubTabBarIcon: [Part]] = [
        .home: [Part(.body, "M3.6 9.9 L11 3.4 L18.4 9.9 L18.4 17.6 Q18.4 18.9 17.1 18.9 L4.9 18.9 Q3.6 18.9 3.6 17.6 Z")],
        .search: [
            Part(.line, "M16.2 9.9 C16.2 13.38 13.38 16.2 9.9 16.2 C6.42 16.2 3.6 13.38 3.6 9.9 C3.6 6.42 6.42 3.6 9.9 3.6 C13.38 3.6 16.2 6.42 16.2 9.9 Z"),
            Part(.line, "M14.6 14.6 L19 19"),
        ],
        .tray: [
            Part(.body, "M6.2 3.6 L15.8 3.6 Q18.8 3.6 18.8 6.6 L18.8 15.4 Q18.8 18.4 15.8 18.4 L6.2 18.4 Q3.2 18.4 3.2 15.4 L3.2 6.6 Q3.2 3.6 6.2 3.6 Z"),
            Part(.inner, "M3.2 11.6 L7.4 11.6 Q7.9 14.6 11 14.6 Q14.1 14.6 14.6 11.6 L18.8 11.6"),
        ],
        .compose: [
            Part(.body, "M6.2 5.6 L13.4 5.6 Q16.4 5.6 16.4 8.6 L16.4 15.8 Q16.4 18.8 13.4 18.8 L6.2 18.8 Q3.2 18.8 3.2 15.8 L3.2 8.6 Q3.2 5.6 6.2 5.6 Z"),
            Part(.gap, "M10 12 L17.8 4.2", width: 6.4),
            Part(.line, "M10 12 L17.8 4.2", width: 2.6),
        ],
    ]

    var body: some View {
        Canvas { context, size in
            // The box sits in the middle of whatever frame the icon is given.
            context.translateBy(x: (size.width - Metrics.iconBox) / 2, y: (size.height - Metrics.iconBox) / 2)
            let ink = active ? Palette.ink : Palette.icon
            func stroke(_ part: Part, _ color: Color, _ width: Double) {
                context.stroke(part.path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
            }
            for part in Self.parts[icon] ?? [] {
                switch part.kind {
                case .gap: stroke(part, Palette.bar, part.width)
                case .inner: stroke(part, active ? Palette.bar : ink, Metrics.stroke)
                case .line: stroke(part, ink, part.width * (active ? Metrics.strokeOn : 1))
                case .body:
                    if active { context.fill(part.path, with: .color(ink)) }
                    stroke(part, ink, Metrics.stroke)
                }
            }
        }
    }
}

/// A path from the letters M, L, Q, C and Z with absolute coordinates, which is all the icons use.
private func accountScrubTabBarPath(_ d: String) -> Path {
    var path = Path()
    var command: Character? = nil
    var numbers: [Double] = []
    func flush() {
        let points = stride(from: 0, to: numbers.count - 1, by: 2).map { CGPoint(x: numbers[$0], y: numbers[$0 + 1]) }
        switch command {
        case "M": path.move(to: points[0])
        case "L": path.addLine(to: points[0])
        case "Q": path.addQuadCurve(to: points[1], control: points[0])
        case "C": path.addCurve(to: points[2], control1: points[0], control2: points[1])
        case "Z": path.closeSubpath()
        default: break
        }
        numbers = []
    }
    for token in d.split(separator: " ") {
        var text = token
        if let first = text.first, first.isLetter {
            flush()
            command = first
            text = text.dropFirst()
        }
        if let number = Double(text) { numbers.append(number) }
    }
    flush()
    return path
}

#Preview {
    AccountScrubTabBar(autoPlay: true)
}
