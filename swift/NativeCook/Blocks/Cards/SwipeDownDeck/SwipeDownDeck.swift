import SwiftUI

/// A card browser with a deck at the bottom: swipe sideways to browse, swipe a card down to put it in
/// the deck. The card shrinks and tilts as it is pulled, the tray lifts and glows in its colour, and on
/// release the card drops into the tray as the front of a stack while the next one springs in.
struct SwipeDownDeck: View {
    var cards: [SwipeDownDeckCard]
    var title: String
    /// The filter chips; the first one shows every card, the others match a card's `filters`.
    var chips: [String]
    var hint: String
    var deckLabel: String
    /// Stashes and browses cards by itself every `loop` seconds, then empties the deck again.
    var autoPlay: Bool
    /// Called when a card is put in the deck.
    var onAdd: ((SwipeDownDeckCard) -> Void)?
    /// Called when another card comes to the front.
    var onSelect: ((SwipeDownDeckCard) -> Void)?
    var onMenu: (() -> Void)?

    init(
        cards: [SwipeDownDeckCard] = SwipeDownDeckCard.samples,
        title: String = "Available Cards",
        chips: [String] = ["All", "New", "Protocols", "Supplements", "Diets"],
        hint: String = "Swipe the card down to add to your deck",
        deckLabel: String = "My Deck",
        autoPlay: Bool = false,
        onAdd: ((SwipeDownDeckCard) -> Void)? = nil,
        onSelect: ((SwipeDownDeckCard) -> Void)? = nil,
        onMenu: (() -> Void)? = nil
    ) {
        self.cards = cards
        self.title = title
        self.chips = chips
        self.hint = hint
        self.deckLabel = deckLabel
        self.autoPlay = autoPlay
        self.onAdd = onAdd
        self.onSelect = onSelect
        self.onMenu = onMenu
        _slots = State(initialValue: cards.indices.map { SwipeDownDeckValue(Double($0)) })
        _depths = State(initialValue: cards.map { _ in SwipeDownDeckValue(0) })
        _presences = State(initialValue: cards.map { _ in SwipeDownDeckValue(1) })
        _wasShown = State(initialValue: cards.map { _ in true })
    }

    // Colours.
    private static let background = Color(hex: 0x121212)
    private static let chipFill = Color(hex: 0x222222)
    private static let chipInk = Color(hex: 0x8a8a8a)
    private static let chipOn = Color(hex: 0xededed)
    private static let chipOnInk = Color(hex: 0x111111)
    private static let countFill = Color(hex: 0x2a2a2a)
    private static let trayFill = Color(hex: 0x1b1b1b)
    private static let pocketFill = Color(hex: 0x232323)
    private static let hintInk = Color(hex: 0xe5e5e5)

    // Layout, in points.
    private static let margin = 24.0
    /// How much of the cards on either side shows at the screen's edges.
    private static let peek = 6.0
    private static let header = 44.0
    private static let chipsGap = 4.0
    private static let chipHeight = 32.0
    private static let cardGap = 16.0
    private static let cardRatio = 1.4
    private static let trayHeight = 92.0
    /// The least room between the card and the tray, for the drag down.
    private static let trayGap = 28.0
    // Where the deck's front card shows its top edge, below the tray's top, and how the cards behind
    // it step up and shrink; three of them show.
    private static let deckTop = 18.0
    private static let deckStep = 7.0
    private static let deckScale = 0.9
    private static let deckShrink = 0.04
    private static let deckShown = 3.0
    private static let pillY = 52.0
    /// The tray rises this much and glows while a card comes down to it.
    private static let lift = 8.0
    private static let glowHeight = 160.0

    // The drag.
    private static let stashAt = 130.0
    private static let stashMin = 50.0
    private static let fling = 900.0
    private static let browseAt = 70.0
    private static let rubber = 0.3
    /// A card dragged down leans after the finger by this share of the sideways movement, shrinks and tilts.
    private static let lean = 0.35
    private static let dragShrink = 0.1
    private static let tilt = 3.0

    // Motion, in seconds.
    private static let fly = 0.42
    private static let pulseOut = 0.7
    private static let presence = 0.22
    private static let tintFade = 0.4
    private static let clearFor = 0.5
    private static let clearStagger = 0.06
    private static let clearTotal = clearFor + clearStagger * 3
    private static let veilOut = 0.3
    private static let veilIn = 0.45
    private static let veilDrop = 14.0
    private static let veilShrink = 0.05

    // The autoPlay loop: two cards stashed, a browse past the third, two more stashed, then the deck
    // empties and the first card comes back. Each drag takes `dragFor`, each browse `browseFor`.
    static let loop = 10.0
    private static let script: [(Double, Step)] = [(0.9, .stash), (2.5, .stash), (4.1, .next), (5.3, .stash), (6.9, .stash)]
    private static let clearAt = 8.55
    private static let dragFor = 0.55
    private static let dragX = 14.0
    private static let dragY = 170.0
    private static let browseFor = 0.34
    private static let browseX = 120.0
    private static let userHold = 3.0

    private enum Step { case stash, next }
    private enum Mode { case none, browse, pull }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var chip = 0
    /// Cards in the deck, in the order they went in.
    @State private var stashed: [Int] = []
    @State private var focus = 0
    /// Cards that have landed in the deck, for its count.
    @State private var landed = 0
    @State private var epoch = 0
    @State private var touches = 0
    /// The card whose colour the tray glows in.
    @State private var glowId = 0

    // The carousel's position, in cards, and each card's place in it, depth in the deck and presence.
    @State private var scroll = SwipeDownDeckValue(0)
    @State private var scrollTarget = 0
    @State private var slots: [SwipeDownDeckValue]
    @State private var depths: [SwipeDownDeckValue]
    @State private var presences: [SwipeDownDeckValue]
    @State private var wasShown: [Bool]
    // The drags: sideways for the whole carousel, and the front card pulled down.
    @State private var bx = SwipeDownDeckValue(0)
    @State private var dx = SwipeDownDeckValue(0)
    @State private var dy = SwipeDownDeckValue(0)
    @State private var mode = Mode.none
    @State private var dragging = false
    /// The card in flight to the deck, when it left, and from where (x, y, pull, tilt).
    @State private var flyId = -1
    @State private var flyStart: Date? = nil
    @State private var flyFrom = (x: 0.0, y: 0.0, pull: 0.0, tilt: 0.0)
    /// The deck's slide out, and the carousel's fade while the loop starts over.
    @State private var clearStart: Date? = nil
    @State private var veilOutStart: Date? = nil
    @State private var veilInStart: Date? = nil
    @State private var clearing = false
    @State private var restoreTask: Task<Void, Never>? = nil
    @State private var scripted = false
    @State private var pitch = 0.0
    /// The timeline runs until then; afterwards the screen is still and it pauses.
    @State private var motionUntil = Date.distantPast
    @State private var idle = false
    @State private var pressHaptic = UIImpactFeedbackGenerator(style: .medium)
    @State private var landHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var selectHaptic = UISelectionFeedbackGenerator()

    private var visible: [Int] { visible(chip: chip) }

    private func visible(chip: Int) -> [Int] {
        cards.indices.filter { !stashed.contains($0) && (chip == 0 || cards[$0].filters.contains(chips[chip])) }
    }

    private struct Layout {
        var width: Double
        var cardW: Double
        var cardH: Double
        var pitch: Double
        var cardTop: Double
        var trayTop: Double
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let cardW = width - Self.margin * 2
            let cardTop = insets.top + Self.header + Self.chipsGap + Self.chipHeight + Self.cardGap
            let trayTop = height - max(insets.bottom, 16) - Self.trayHeight
            let layout = Layout(
                width: width, cardW: cardW, cardH: min(cardW * Self.cardRatio, trayTop - Self.trayGap - cardTop),
                pitch: cardW + Self.margin - Self.peek, cardTop: cardTop, trayTop: trayTop
            )

            TimelineView(.animation(paused: !dragging && Date() > motionUntil)) { timeline in
                screen(layout: layout, insetTop: insets.top, now: timeline.date)
            }
            .ignoresSafeArea()
            .onChange(of: layout.pitch, initial: true) { pitch = layout.pitch }
        }
        .background(Self.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            pressHaptic.prepare()
            selectHaptic.prepare()
        }
        .onDisappear { restoreTask?.cancel() }
        .task(id: motionUntil) {
            // Wakes the view once the last motion has ended, so the timeline pauses.
            let wait = motionUntil.timeIntervalSinceNow
            guard wait > 0 else { return }
            try? await Task.sleep(for: .seconds(wait + 0.05))
            if !Task.isCancelled { idle.toggle() }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await runLoop() }
    }

    // MARK: The screen

    private func screen(layout: Layout, insetTop: Double, now: Date) -> some View {
        let glow = glow(at: now)
        let lift = Self.lift * glow
        let clear = clear(at: now)
        let tint = Color(hex: cards.indices.contains(glowId) ? cards[glowId].color : 0xffffff)

        return ZStack(alignment: .topLeading) {
            ForEach(cards.indices, id: \.self) { i in
                let color = Color(hex: cards[i].color)
                Ellipse()
                    .fill(EllipticalGradient(colors: [color.opacity(0.42), color.opacity(0)], center: .center, startRadiusFraction: 0, endRadiusFraction: 0.5))
                    .frame(width: layout.cardW * 1.6, height: layout.cardH * 1.24)
                    .opacity(focus == i && !stashed.contains(i) ? 1 : 0)
                    .animation(.easeInOut(duration: Self.tintFade), value: focus == i && !stashed.contains(i))
                    .offset(x: layout.width / 2 - layout.cardW * 0.8, y: layout.cardTop + layout.cardH * 0.42 - layout.cardH * 0.62)
                    .allowsHitTesting(false)
            }

            header
                .frame(width: layout.width, height: Self.header)
                .offset(y: insetTop)

            chipRow
                .frame(width: layout.width, height: Self.chipHeight)
                .offset(y: insetTop + Self.header + Self.chipsGap)

            LinearGradient(colors: [tint.opacity(0), tint.opacity(0.5)], startPoint: .top, endPoint: .bottom)
                .frame(width: layout.width, height: Self.glowHeight)
                .opacity(glow)
                .offset(y: layout.trayTop - Self.glowHeight)
                .allowsHitTesting(false)

            tray(layout: layout, tint: tint, glow: glow)
                .offset(y: layout.trayTop - lift)

            cardLayer(layout: layout, now: now)
                .frame(width: layout.width, height: layout.trayTop - layout.cardTop, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(drag(pitch: layout.pitch))
                .offset(y: layout.cardTop)

            // The pill gives way to a card falling into the deck and comes back with the new count.
            pill
                .opacity((1 - min(1, max(0, clear / Self.clearFor))) * (1 - pulse(at: now)))
                .frame(width: layout.width)
                .offset(y: layout.trayTop + Self.deckTop + Self.pillY - lift)
                .allowsHitTesting(false)
        }
        .frame(width: layout.width, height: layout.trayTop + Self.trayHeight + 200, alignment: .topLeading)
    }

    private var header: some View {
        HStack(spacing: 0) {
            Text(title)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
            Text("\(cards.count - stashed.count)")
                .font(.system(size: 12, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Color(hex: 0xdadada))
                .padding(.horizontal, 8)
                .frame(minWidth: 30)
                .frame(height: 22)
                .background(Capsule().fill(Self.countFill))
                .padding(.leading, 8)
            Spacer(minLength: 0)
            Button {
                touched()
                onMenu?()
            } label: {
                SwipeDownDeckIcon.menu.path
                    .stroke(.white.opacity(0.8), style: StrokeStyle(lineWidth: 1.7, lineCap: .round))
                    .frame(width: 18, height: 18)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Menu")
        }
        .padding(.leading, 16)
        .padding(.trailing, 4)
    }

    private var chipRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                SwipeDownDeckIcon.search.path
                    .stroke(Self.chipInk, style: StrokeStyle(lineWidth: 1.7 * 15 / 16, lineCap: .round))
                    .frame(width: 15, height: 15)
                    .frame(width: Self.chipHeight, height: Self.chipHeight)
                    .background(Circle().fill(Self.chipFill))
                ForEach(chips.indices, id: \.self) { i in
                    Button {
                        selectChip(i)
                    } label: {
                        Text(chips[i])
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(chip == i ? Self.chipOnInk : Self.chipInk)
                            .padding(.horizontal, 14)
                            .frame(height: Self.chipHeight)
                            .background(Capsule().fill(chip == i ? Self.chipOn : Self.chipFill))
                            .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: 0.2), value: chip == i)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(chip == i ? .isSelected : [])
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func tray(layout: Layout, tint: Color, glow: Double) -> some View {
        ZStack(alignment: .top) {
            UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .continuous)
                .fill(Self.trayFill)
            Capsule()
                .fill(tint)
                .frame(width: layout.width - 56, height: 1.5)
                .opacity(glow)
            VStack(spacing: 8) {
                SwipeDownDeckIcon.tray.path
                    .stroke(Self.hintInk, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                    .frame(width: 16, height: 16)
                    .frame(width: 30, height: 30)
                    .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(Color(hex: 0x333333)))
                Text(hint)
                    .font(.system(size: 12))
                    .foregroundStyle(Self.hintInk)
                    .lineLimit(1)
            }
            .frame(width: layout.width - 24, height: Self.trayHeight - 24)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(Self.pocketFill)
                    .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.08), lineWidth: 1))
            )
            .padding(.top, 12)
            .opacity(stashed.isEmpty ? 1 : 0)
            .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: 0.25), value: stashed.isEmpty)
        }
        .frame(width: layout.width, height: Self.trayHeight + 200, alignment: .top)
    }

    private var pill: some View {
        HStack(spacing: 6) {
            SwipeDownDeckIcon.up.path
                .stroke(.white, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                .frame(width: 12, height: 12)
            Text("\(deckLabel) · \(landed)")
                .font(.system(size: 12, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 12)
        .frame(height: 28)
        .background(Capsule().fill(.black.opacity(0.32)))
        .opacity(landed > 0 ? 1 : 0)
        // It fades in with the first card and goes at once when the deck is emptied.
        .animation(landed > 0 ? .timingCurve(0.25, 0.1, 0.25, 1, duration: 0.2) : nil, value: landed > 0)
    }

    private func cardLayer(layout: Layout, now: Date) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(cards.indices, id: \.self) { i in
                let order = stashed.firstIndex(of: i)
                let pose = pose(i, layout: layout, now: now)
                SwipeDownDeckCardFace(card: cards[i], width: layout.cardW, height: layout.cardH) { add(i) }
                    .compositingGroup()
                    .shadow(color: .black.opacity(0.45), radius: 18, y: 18)
                    .rotationEffect(.degrees(pose.r))
                    .scaleEffect(pose.s)
                    .opacity(pose.o)
                    .offset(x: (layout.width - layout.cardW) / 2 + pose.x, y: pose.y)
                    .zIndex(order.map { 20 + Double($0) } ?? (focus == i ? 40 : 10))
                    .allowsHitTesting(order == nil && visible.contains(i))
            }
        }
    }

    // MARK: Poses

    private func pose(_ i: Int, layout: Layout, now: Date) -> (x: Double, y: Double, s: Double, r: Double, o: Double) {
        let flying = flyId == i
        if stashed.contains(i) || flying {
            let d = depths[i].value(at: now)
            let lift = Self.lift * glow(at: now)
            let scale = Self.deckScale - d * Self.deckShrink
            let top = layout.trayTop + Self.deckTop - d * Self.deckStep - lift
            let deckY = top + layout.cardH * scale / 2 - (layout.cardTop + layout.cardH / 2)
            let out = min(1, max(0, (clear(at: now) - d * Self.clearStagger) / Self.clearFor))
            let deckX = -(layout.width + 40) * out * out * out
            let t = flying ? flyProgress(at: now) : 1
            return (
                mix(flyFrom.x, deckX, t), mix(flyFrom.y, deckY, t),
                mix(1 - Self.dragShrink * flyFrom.pull, scale, t), mix(flyFrom.tilt, 0, t),
                min(1, max(0, Self.deckShown - d))
            )
        }
        let veil = veil(at: now)
        var x = (slots[i].value(at: now) - scroll.value(at: now)) * layout.pitch + bx.value(at: now)
        var y = Self.veilDrop * veil
        var s = 1 - Self.veilShrink * veil
        var r = 0.0
        if focus == i {
            let ddx = dx.value(at: now)
            let ddy = dy.value(at: now)
            let p = Self.progress(ddy)
            x += ddx * Self.lean
            y += ddy
            s *= 1 - Self.dragShrink * p
            r = Self.tilt(ddx, p)
        }
        return (x, y, s, r, presences[i].value(at: now) * (1 - veil))
    }

    private static func progress(_ dy: Double) -> Double {
        min(1, max(0, dy / stashAt))
    }

    private static func tilt(_ dx: Double, _ progress: Double) -> Double {
        tilt * progress + min(1, max(-1, dx / 120)) * tilt
    }

    private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double {
        a + (b - a) * t
    }

    private func duration(_ seconds: Double) -> Double {
        reduceMotion ? 0 : seconds
    }

    private func flyProgress(at now: Date) -> Double {
        guard let start = flyStart else { return 1 }
        let d = duration(Self.fly)
        guard d > 0 else { return 1 }
        return SwipeDownDeckEase.fly.value(at: min(1, max(0, now.timeIntervalSince(start) / d)))
    }

    /// Rises with the falling card and fades after it lands.
    private func pulse(at now: Date) -> Double {
        guard let start = flyStart, !reduceMotion else { return 0 }
        let t = now.timeIntervalSince(start)
        if t < Self.fly { return pow(max(0, t) / Self.fly, 2) }
        let u = min(1, (t - Self.fly) / Self.pulseOut)
        return (1 - u) * (1 - u)
    }

    private func glow(at now: Date) -> Double {
        max(Self.progress(dy.value(at: now)), pulse(at: now))
    }

    /// Seconds into the deck's slide out.
    private func clear(at now: Date) -> Double {
        guard let start = clearStart else { return 0 }
        return reduceMotion ? Self.clearTotal : min(Self.clearTotal, now.timeIntervalSince(start))
    }

    private func veil(at now: Date) -> Double {
        if let start = veilInStart {
            let d = duration(Self.veilIn)
            let u = d > 0 ? min(1, max(0, now.timeIntervalSince(start) / d)) : 1
            return pow(1 - u, 3)
        }
        if let start = veilOutStart {
            let d = duration(Self.veilOut)
            let u = d > 0 ? min(1, max(0, now.timeIntervalSince(start) / d)) : 1
            return u * u
        }
        return 0
    }

    private func keepMoving(_ seconds: Double = SwipeDownDeckValue.settle + 0.1) {
        let until = Date.now + seconds
        if until > motionUntil { motionUntil = until }
    }

    // MARK: Behaviour

    /// Brings every card's place, depth and presence in step with the state, with springs unless `snap`.
    private func sync(at now: Date, snap: Bool = false) {
        let list = visible
        let still = reduceMotion
        for i in cards.indices {
            let place = list.firstIndex(of: i)
            let shown = place != nil
            if let place {
                if snap || !wasShown[i] {
                    slots[i].set(Double(place))
                } else if slots[i].target != Double(place) {
                    slots[i].spring(to: Double(place), at: now, still: still)
                }
            }
            if snap {
                presences[i].set(shown ? 1 : 0)
            } else if presences[i].target != (shown ? 1 : 0) {
                presences[i].tween(to: shown ? 1 : 0, duration: duration(Self.presence), at: now, curve: SwipeDownDeckEase.quadInOut)
            }
            if let order = stashed.firstIndex(of: i), !snap {
                let target = Double(stashed.count - 1 - order)
                if depths[i].target != target { depths[i].spring(to: target, at: now, still: still) }
            } else {
                depths[i].set(0)
            }
            wasShown[i] = shown
        }
        let target = max(0, list.firstIndex(of: focus) ?? 0)
        if scrollTarget != target {
            scrollTarget = target
            scroll.spring(to: Double(target), at: now, still: still)
        }
        keepMoving()
    }

    private func drag(pitch: Double) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { g in
                let t = g.translation
                if !dragging {
                    dragging = true
                    touched()
                    guard visible.contains(focus) else {
                        mode = .none
                        return
                    }
                    let vertical = abs(t.height) > abs(t.width)
                    mode = vertical ? .pull : .browse
                    if vertical { glowId = focus }
                }
                switch mode {
                case .pull:
                    dx.set(t.width)
                    dy.set(t.height > 0 ? t.height : t.height * Self.rubber)
                case .browse:
                    let last = visible.count - 1
                    bx.set((scrollTarget <= 0 && t.width > 0) || (scrollTarget >= last && t.width < 0) ? t.width * Self.rubber : t.width)
                case .none:
                    break
                }
            }
            .onEnded { g in
                dragging = false
                release(vx: g.velocity.width, vy: g.velocity.height, auto: false, force: false, pitch: pitch)
            }
    }

    /// Ends a drag: a card pulled far enough drops into the deck, a sideways drag pages. `force` makes
    /// the outcome certain whatever the distance: the add button, and the loop.
    private func release(vx: Double, vy: Double, auto: Bool, force: Bool, pitch: Double) {
        let now = Date.now
        let kind = mode
        mode = .none
        let ids = visible
        let still = reduceMotion
        if kind == .pull || (force && kind == .none) {
            let id = focus
            let y = dy.value(at: now)
            if let k = ids.firstIndex(of: id), flyProgress(at: now) >= 1,
               force || y > Self.stashAt || (vy > Self.fling && y > Self.stashMin) {
                let x = dx.value(at: now)
                let p = Self.progress(y)
                flyFrom = (x * Self.lean, y, p, Self.tilt(x, p))
                dx.set(0)
                dy.set(0)
                let next = k + 1 < ids.count ? ids[k + 1] : k > 0 ? ids[k - 1] : -1
                flyId = id
                flyStart = now
                stashed.append(id)
                focus = next
                sync(at: now)
                keepMoving(Self.fly + Self.pulseOut + 0.1)
                if !auto { pressHaptic.impactOccurred() }
                onAdd?(cards[id])
                if next >= 0 { onSelect?(cards[next]) }
                let round = epoch
                Task { @MainActor in
                    try? await Task.sleep(for: .seconds(duration(Self.fly)), tolerance: .zero)
                    guard round == epoch, flyId == id else { return }
                    landed += 1
                    if !auto { landHaptic.impactOccurred() }
                }
                return
            }
            dx.spring(to: 0, at: now, still: still)
            dy.spring(to: 0, at: now, still: still)
            keepMoving()
            return
        }
        if kind == .browse {
            let k = scrollTarget
            let offset = bx.value(at: now)
            var target = k
            if force || offset < -Self.browseAt || vx < -Self.fling {
                target = k + 1
            } else if offset > Self.browseAt || vx > Self.fling {
                target = k - 1
            }
            target = min(ids.count - 1, max(0, target))
            // The drag becomes part of the position, so the spring starts where the finger let go.
            scroll.set(scroll.value(at: now) - offset / pitch)
            bx.set(0)
            scrollTarget = target
            scroll.spring(to: Double(target), at: now, velocity: -vx / pitch, still: still)
            keepMoving()
            if target != k {
                focus = ids[target]
                if !auto { selectHaptic.selectionChanged() }
                onSelect?(cards[ids[target]])
            }
        }
    }

    private func add(_ id: Int) {
        touched()
        guard focus == id else { return }
        glowId = id
        release(vx: 0, vy: 0, auto: false, force: true, pitch: pitch)
    }

    private func selectChip(_ index: Int) {
        touched()
        guard index != chip else { return }
        selectHaptic.selectionChanged()
        let next = visible(chip: index)
        chip = index
        if !next.contains(focus) { focus = next.first ?? -1 }
        sync(at: .now)
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
        let now = Date.now
        if scripted {
            scripted = false
            mode = .none
            dx.spring(to: 0, at: now, still: reduceMotion)
            dy.spring(to: 0, at: now, still: reduceMotion)
            bx.spring(to: 0, at: now, still: reduceMotion)
            keepMoving()
        }
        if clearing { restore() }
    }

    /// Slides the deck out to the left and fades the carousel, then puts every card back.
    private func clearDeck() {
        let now = Date.now
        clearing = true
        clearStart = now
        veilInStart = nil
        veilOutStart = now
        keepMoving(Self.clearTotal + Self.veilIn + 0.1)
        restoreTask?.cancel()
        restoreTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(Self.clearTotal), tolerance: .zero)
            if !Task.isCancelled { restore() }
        }
    }

    private func restore() {
        restoreTask?.cancel()
        let now = Date.now
        clearing = false
        flyId = -1
        flyStart = nil
        scroll.set(0)
        scrollTarget = 0
        bx.set(0)
        dx.set(0)
        dy.set(0)
        mode = .none
        stashed = []
        focus = cards.isEmpty ? -1 : 0
        landed = 0
        chip = 0
        epoch += 1
        clearStart = nil
        veilOutStart = nil
        veilInStart = now
        sync(at: now, snap: true)
        keepMoving(Self.veilIn + 0.1)
    }

    /// The loop's hand: starts the same drags a finger makes.
    private func play(_ step: Step) {
        guard visible.contains(focus) else { return }
        let now = Date.now
        scripted = true
        switch step {
        case .stash:
            glowId = focus
            mode = .pull
            dx.tween(to: Self.dragX, duration: duration(Self.dragFor), at: now) { SwipeDownDeckEase.drag.value(at: $0) }
            dy.tween(to: Self.dragY, duration: duration(Self.dragFor), at: now) { SwipeDownDeckEase.drag.value(at: $0) }
        case .next:
            mode = .browse
            bx.tween(to: -Self.browseX, duration: duration(Self.browseFor), at: now) { SwipeDownDeckEase.drag.value(at: $0) }
        }
        keepMoving()
    }

    private func letGo() {
        guard scripted else { return }
        scripted = false
        release(vx: 0, vy: 0, auto: true, force: true, pitch: pitch)
    }

    private var atRest: Bool { stashed.isEmpty && focus == 0 && chip == 0 }

    private func runLoop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        var origin = clock.now
        if touches > 0 {
            try? await clock.sleep(until: origin + .seconds(Self.userHold), tolerance: .zero)
            while dragging, !Task.isCancelled { try? await Task.sleep(for: .seconds(0.5)) }
            guard !Task.isCancelled else { return }
            origin = clock.now
            if !atRest {
                // Back to rest the way the loop ends, then the loop starts over from there.
                clearDeck()
                origin += .seconds(Self.loop - Self.clearAt)
            }
        }
        var events: [(Double, () -> Void)] = []
        for (time, step) in Self.script {
            events.append((time, { play(step) }))
            events.append((time + (step == .stash ? Self.dragFor : Self.browseFor), { letGo() }))
        }
        events.append((Self.clearAt, { clearDeck() }))
        events.sort { $0.0 < $1.0 }
        while !Task.isCancelled {
            for (time, run) in events {
                try? await clock.sleep(until: origin + .seconds(time), tolerance: .zero)
                guard !Task.isCancelled else { return }
                run()
            }
            origin += .seconds(Self.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}
