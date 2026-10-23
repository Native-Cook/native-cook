import SwiftUI

struct SwipeDeckToolbarProfile {
    var name: String
    var note: String
    var photo: String

    private static func photo(_ id: String) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=626&h=840&q=80"
    }

    static let samples = [
        SwipeDeckToolbarProfile(name: "Lena, 27", note: "2 km away", photo: photo("photo-1544005313-94ddf0286df2")),
        SwipeDeckToolbarProfile(name: "Theo, 31", note: "5 km away", photo: photo("photo-1506794778202-cad84cf45f1d")),
        SwipeDeckToolbarProfile(name: "Mira, 29", note: "1 km away", photo: photo("photo-1517841905240-472988babdf9")),
    ]
}

struct SwipeDeckToolbarPage {
    struct Row {
        var title: String
        var note: String
    }

    var title: String
    var rows: [Row]

    static let samples = [
        SwipeDeckToolbarPage(title: "Profile", rows: [
            Row(title: "Edit profile", note: "Photos, bio and interests"),
            Row(title: "Discovery", note: "Within 10 km, ages 24 to 34"),
            Row(title: "Notifications", note: "Matches and messages"),
            Row(title: "Privacy", note: "Who can see you"),
        ]),
        SwipeDeckToolbarPage(title: "Recent", rows: [
            Row(title: "Lena", note: "Passed 2 minutes ago"),
            Row(title: "Theo", note: "Liked an hour ago"),
            Row(title: "Mira", note: "Liked yesterday"),
            Row(title: "Jonas", note: "Passed on Monday"),
        ]),
        SwipeDeckToolbarPage(title: "Likes", rows: [
            Row(title: "Ada likes you", note: "3 km away"),
            Row(title: "Noor likes you", note: "6 km away"),
            Row(title: "Iris likes you", note: "2 km away"),
            Row(title: "Sam likes you", note: "9 km away"),
        ]),
    ]
}

/// A swipe deck with a toolbar that changes shape for it. On the deck's tab the band's top edge
/// sinks into a valley and the two middle icons meet there as a cross and a heart in a glass
/// capsule; dragging the top card lights the side it leans to. On the other tabs the band is flat
/// and a ring slides to the tab that was pressed.
struct SwipeDeckToolbar: View {
    /// The people of the deck, top card first; they come round again after the last.
    var profiles = SwipeDeckToolbarProfile.samples
    /// The screens of the first three tabs: profile, recent, likes. The fourth tab is the deck.
    var pages = SwipeDeckToolbarPage.samples
    var accent = Color(hex: 0x4FA3E0)
    var likeColor = Color(hex: 0x2FA56C)
    var rejectColor = Color(hex: 0xB5304A)
    /// Swipes two cards and goes through the tabs by itself every `Motion.loop` seconds.
    var autoPlay = false
    var onSelectTab: ((Int) -> Void)? = nil
    /// Called when a card has left the deck, with the verdict on it.
    var onVerdict: ((SwipeDeckToolbarProfile, Bool) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var tab = Metrics.deck
    /// The place in the endless deck of the card on top.
    @State private var first = 0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// The autoPlay loop makes no haptics; a touch does.
    @State private var byHand = false

    /// 0 flat, 1 with the valley open.
    @State private var valley = Glide(1)
    @State private var ring = Glide(Double(Metrics.deck))
    @State private var ringFrom = Double(Metrics.deck)
    @State private var ringTo = Double(Metrics.deck)
    @State private var deck = Glide(1)
    /// How far a finger has pulled the top card.
    @State private var pull = Glide(0)
    /// A card that is leaving: since when, from where it was let go, toward which side (-1 or 1).
    @State private var flight: Flight? = nil
    @State private var flightTask: Task<Void, Never>? = nil
    /// The autoPlay loop is pulling the card; a finger has it.
    @State private var pulling = false
    @State private var grabbed = false
    /// The timeline runs only while something moves.
    @State private var moving = false
    @State private var restTask: Task<Void, Never>? = nil
    @State private var impact = UIImpactFeedbackGenerator(style: .light)
    @State private var selection = UISelectionFeedbackGenerator()

    private struct Flight {
        let start: Date
        let from: Double
        let side: Double
    }

    /// A number on its way to a target along a curve or on a spring, read against the timeline's clock.
    private struct Glide {
        private var from: Double
        private var to: Double
        private var start = Date.distantPast
        private var duration = 0.0
        private var curve = UnitCurve.linear
        private var spring: Spring? = nil

        init(_ value: Double) {
            from = value
            to = value
        }

        func value(at date: Date) -> Double {
            let t = date.timeIntervalSince(start)
            guard t > 0 else { return from }
            if let spring {
                // Long after the spring has come to rest.
                return t < 3 ? from + spring.value(target: to - from, initialVelocity: 0, time: t) : to
            }
            return t < duration ? from + (to - from) * curve.value(at: t / duration) : to
        }

        mutating func set(_ value: Double) {
            self = Glide(value)
        }

        mutating func tween(to target: Double, at date: Date = .now, duration: Double, curve: UnitCurve = .linear) {
            from = value(at: date)
            to = target
            start = date
            self.duration = duration
            self.curve = curve
            spring = nil
        }

        mutating func spring(to target: Double, at date: Date = .now, _ spring: Spring) {
            from = value(at: date)
            to = target
            start = date
            self.spring = spring
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width
            let bandHeight = B.height + max(B.inset, insets.bottom)
            // The deck stands a little above the middle of the room over the band.
            let room = proxy.size.height + insets.bottom - bandHeight
            let deckTop = max(12, (room - Metrics.deckHeight) * 0.4)
            let away = width + 80

            ZStack(alignment: .top) {
                Palette.page.ignoresSafeArea()
                RadialGradient(
                    stops: [.init(color: Palette.glow, location: 0), .init(color: Palette.glowFar, location: Metrics.glowMid / Metrics.glowEnd), .init(color: Palette.page, location: 1)],
                    center: Metrics.glowAt, startRadius: 0, endRadius: Metrics.glowEnd
                )
                .ignoresSafeArea()

                // A new tab's screen fades in over the old one; the deck stays, so its photos do too.
                ZStack(alignment: .top) {
                    if tab != Metrics.deck {
                        page(pages[min(tab, pages.count - 1)])
                            .id(tab)
                            .transition(.opacity)
                    }
                }
                .padding(.top, Metrics.titleTop)
                .padding(.horizontal, Metrics.margin)

                TimelineView(.animation(paused: !moving)) { timeline in
                    let date = timeline.date
                    let q = flight.map { min(1, max(0, date.timeIntervalSince($0.start) / M.fly)) } ?? 0
                    let from = flight?.from ?? 0
                    // Without movement a card that leaves stays where it was let go and fades.
                    let x = flight.map { reduceMotion ? from : from + ($0.side * away - from) * q * q } ?? pull.value(at: date)

                    ZStack(alignment: .top) {
                        cards(x: x, fly: q)
                            .padding(.top, deckTop)
                            .opacity(deck.value(at: date))
                            .allowsHitTesting(tab == Metrics.deck)
                            .accessibilityHidden(tab != Metrics.deck)

                        SwipeDeckToolbarBand(
                            valley: valley.value(at: date), ring: ring.value(at: date), ringFrom: ringFrom, ringTo: ringTo,
                            cardX: x, fly: q, side: flight?.side ?? 0, full: Metrics.full,
                            accent: accent, likeColor: likeColor, rejectColor: rejectColor, still: reduceMotion
                        )
                        .frame(width: width, height: bandHeight)
                        .overlay(alignment: .topLeading) { targets(width: width) }
                        .frame(maxHeight: .infinity, alignment: .bottom)
                        .padding(.bottom, -insets.bottom)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { impact.prepare() }
        .onDisappear {
            flightTask?.cancel()
            restTask?.cancel()
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The screens

    private func page(_ page: SwipeDeckToolbarPage) -> some View {
        VStack(alignment: .leading, spacing: Metrics.rowGap) {
            Text(page.title)
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(.white)
                .frame(height: 41)
                .padding(.bottom, 24 - Metrics.rowGap)
            ForEach(Array(page.rows.enumerated()), id: \.offset) { _, row in
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.title)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(height: 22)
                    Text(row.note)
                        .font(.system(size: 14))
                        .foregroundStyle(.white.opacity(0.55))
                        .frame(height: 18)
                }
                .padding(.horizontal, 20)
                .frame(maxWidth: .infinity, minHeight: Metrics.row, maxHeight: Metrics.row, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(.white.opacity(0.06))
                        .strokeBorder(.white.opacity(0.1), lineWidth: 1)
                )
                .accessibilityElement(children: .combine)
            }
        }
        .lineLimit(1)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func cards(x: Double, fly q: Double) -> some View {
        ZStack(alignment: .top) {
            // The last card is drawn first, so that the top one lies over the others.
            ForEach((0...Metrics.behind + 1).reversed().map { first + $0 }, id: \.self) { place in
                let behind = place - first
                // The cards behind move up one place while the top one leaves; a new one fades in at the back.
                let depth = behind == 0 ? 0 : Double(behind) - (reduceMotion ? 0 : M.easeOut(q))
                card(profiles[place % profiles.count], dim: depth * Metrics.stackDim)
                    .scaleEffect(1 - depth * Metrics.stackShrink, anchor: .bottom)
                    .rotationEffect(.degrees(behind == 0 ? x * Metrics.tilt : 0), anchor: .bottom)
                    .offset(x: behind == 0 ? x : 0, y: depth * Metrics.stackStep)
                    .opacity(behind == 0 ? (reduceMotion ? 1 - q : 1) : behind > Metrics.behind ? q : 1)
            }
        }
        .frame(width: Metrics.cardWidth, height: Metrics.deckHeight, alignment: .top)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 8)
                .onChanged { value in
                    if !grabbed {
                        grabbed = true
                        touched()
                        wake()
                    }
                    if flight == nil { pull.set(value.translation.width) }
                }
                .onEnded { _ in
                    grabbed = false
                    release()
                    touched()
                }
        )
    }

    private func card(_ profile: SwipeDeckToolbarProfile, dim: Double) -> some View {
        ZStack(alignment: .bottomLeading) {
            Color(hex: 0x16222B)
            AsyncImage(url: URL(string: profile.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                }
            }
            .frame(width: Metrics.cardWidth, height: Metrics.cardHeight)
            .clipped()
            LinearGradient(colors: [.black.opacity(0), .black.opacity(0.72)], startPoint: .top, endPoint: .bottom)
                .frame(height: 170)
            VStack(alignment: .leading, spacing: 4) {
                Text(profile.name)
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(height: 32)
                Text(profile.note)
                    .font(.system(size: 15))
                    .foregroundStyle(.white.opacity(0.7))
                    .frame(height: 20)
            }
            .lineLimit(1)
            .padding(22)
            Color.black.opacity(dim)
        }
        .frame(width: Metrics.cardWidth, height: Metrics.cardHeight)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    /// What can be pressed on the band: the tabs, and on the deck the cross and the heart.
    private func targets(width: Double) -> some View {
        let onDeck = tab == Metrics.deck
        let capsuleLeft = width / 2 - B.capsuleWidth / 2
        return ZStack(alignment: .topLeading) {
            ForEach(0..<4, id: \.self) { i in
                // On the deck the two middle tabs have become the cross and the heart.
                if !onDeck || i == 0 || i == Metrics.deck {
                    Button {
                        press(i)
                    } label: {
                        Color.clear.frame(width: Metrics.target, height: Metrics.target).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(i < pages.count ? pages[i].title : "Deck")
                    .accessibilityAddTraits(i == tab ? .isSelected : [])
                    .position(x: B.iconX(Double(i), width: width), y: B.iconY)
                }
            }
            if onDeck {
                ForEach([-1.0, 1.0], id: \.self) { to in
                    Button {
                        touched()
                        decide(to)
                    } label: {
                        Color.clear.frame(width: Metrics.target, height: B.capsuleHeight).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(to < 0 ? "Pass" : "Like")
                    .position(x: to < 0 ? capsuleLeft + B.markInset : capsuleLeft + B.capsuleWidth - B.markInset, y: B.iconY)
                }
            }
        }
    }

    // MARK: Behaviour

    /// The top card leaves to a side, and the next one is on top when it has gone.
    private func decide(_ side: Double) {
        guard flight == nil else { return }
        let now = Date.now
        flight = Flight(start: now, from: pull.value(at: now), side: side)
        if byHand { impact.impactOccurred() }
        wake()
        flightTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.fly), tolerance: .zero)
            guard !Task.isCancelled else { return }
            let place = first
            first = place + 1
            pull.set(0)
            flight = nil
            onVerdict?(profiles[place % profiles.count], side > 0)
            wake(restAfter: 0.5)
        }
    }

    /// A card let go: off to the side it was pulled to, or back to its place.
    private func release() {
        let x = pull.value(at: .now)
        if abs(x) > Metrics.threshold {
            decide(x < 0 ? -1 : 1)
        } else {
            pull.spring(to: 0, M.back)
            wake(restAfter: 1)
        }
    }

    private func select(_ index: Int) {
        guard index != tab else { return }
        let now = Date.now
        let open: Double = index == Metrics.deck ? 1 : 0
        ringFrom = ring.value(at: now)
        ringTo = Double(index)
        if reduceMotion {
            ring.set(Double(index))
            valley.tween(to: open, at: now, duration: M.pageFade)
        } else {
            ring.spring(to: Double(index), at: now, M.ring)
            valley.spring(to: open, at: now, M.valley)
        }
        deck.tween(to: open, at: now, duration: M.pageFade)
        withAnimation(.linear(duration: M.pageFade)) { tab = index }
        onSelectTab?(index)
        wake(restAfter: 1.2)
    }

    private func press(_ index: Int) {
        touched()
        guard index != tab else { return }
        selection.selectionChanged()
        select(index)
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        byHand = true
        if pulling {
            pulling = false
            pull.spring(to: 0, M.back)
            wake(restAfter: 1)
        }
        touches += 1
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

    /// The loop does what a finger does, through the same functions.
    private func perform(_ step: M.Step) {
        switch step {
        case .pull(let to):
            pulling = true
            pull.tween(to: to * M.pullTo, duration: M.pull, curve: M.easeInOut)
            wake()
        case .letGo:
            pulling = false
            release()
        case .tab(let index):
            select(index)
        case .restack:
            first = 0
        }
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            // A finger that still has the card starts the wait again when it lets go.
            guard !Task.isCancelled, !grabbed else { return }
            // The loop starts over on the deck with the first card on top.
            byHand = false
            flightTask?.cancel()
            flight = nil
            pull.set(0)
            first = 0
            select(Metrics.deck)
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for step in M.steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                perform(step.step)
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

private typealias B = SwipeDeckToolbarBandMetrics
private typealias M = SwipeDeckToolbarMotion

enum SwipeDeckToolbarMotion {
    static let loop = 10.0
    static let valley = Spring(mass: 1, stiffness: 180, damping: 20)
    static let ring = Spring(mass: 1, stiffness: 260, damping: 26)
    static let back = Spring(mass: 1, stiffness: 300, damping: 26)
    /// A card leaves in `fly` seconds, gathering speed; the cards behind move up meanwhile.
    static let fly = 0.4
    static let pageFade = 0.2
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    /// The loop starts again this long after the last touch.
    static let userHold = 3.0
    /// The autoPlay loop pulls a card this far over `pull` seconds and lets go.
    static let pullTo = 130.0
    static let pull = 0.8

    static func easeOut(_ t: Double) -> Double {
        1 - (1 - t) * (1 - t) * (1 - t)
    }

    enum Step {
        case pull(Double)
        case letGo
        case tab(Int)
        case restack
    }

    /// What the autoPlay loop does: it pulls two cards, presses tabs, and puts the first card back
    /// on top while the deck is out of sight.
    static let script: [(at: Double, step: Step)] = [
        (1.0, .pull(-1)),
        (3.0, .pull(1)),
        (5.2, .tab(0)),
        (5.8, .restack),
        (6.2, .tab(1)),
        (7.0, .tab(2)),
        (8.0, .tab(3)),
    ]

    /// The script with the moment each pulled card is let go.
    static let steps: [(at: Double, step: Step)] = script
        .flatMap { entry -> [(at: Double, step: Step)] in
            if case .pull = entry.step { return [entry, (entry.at + pull, .letGo)] }
            return [entry]
        }
        .sorted { $0.at < $1.at }
}

private enum Palette {
    static let page = Color(hex: 0x0B0D0E)
    static let glow = Color(hex: 0x2C5C82)
    static let glowFar = Color(hex: 0x172836)
}

private enum Metrics {
    /// The glow is centred low on the screen, so that the page is at its bluest behind the band's valley.
    static let glowAt = UnitPoint(x: 0.55, y: 0.78)
    static let glowMid = 260.0
    static let glowEnd = 600.0
    /// The deck's tab is the last of the four.
    static let deck = 3
    static let margin = 24.0
    static let titleTop = 22.0
    static let row = 72.0
    static let rowGap = 12.0
    static let cardWidth = 313.0
    static let cardHeight = 420.0
    static let cardRadius = 28.0
    /// Each card behind the top one is this much lower, smaller and darker.
    static let behind = 2
    static let stackStep = 12.0
    static let stackShrink = 0.05
    static let stackDim = 0.3
    static let deckHeight = cardHeight + Double(behind) * stackStep
    /// Degrees of tilt for each point the top card is pulled.
    static let tilt = 1.0 / 18
    /// A card let go this far from its place leaves; the verdict is lit fully a little further.
    static let threshold = 90.0
    static let full = 120.0
    static let target = 64.0
}

#Preview {
    SwipeDeckToolbar(autoPlay: true)
}
