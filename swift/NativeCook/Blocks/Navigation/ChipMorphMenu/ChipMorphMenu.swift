import SwiftUI

struct ChipMorphMenuTopic: Equatable {
    var label: String
    var emoji: String
}

struct ChipMorphMenuCategory: Equatable {
    var name: String
    /// The SF Symbol on its chip.
    var symbol: String
    /// How many members are online in it.
    var members: Int
    var topics: [ChipMorphMenuTopic]

    static let samples = [
        ChipMorphMenuCategory(name: "Career", symbol: "briefcase.fill", members: 12, topics: [
            .init(label: "Speak with confidence", emoji: "🎤"), .init(label: "Plan your next move", emoji: "🧭"),
            .init(label: "Get ahead at work", emoji: "🏆"), .init(label: "Side projects", emoji: "💻"),
        ]),
        ChipMorphMenuCategory(name: "Wellness", symbol: "camera.macro", members: 11, topics: [
            .init(label: "Sleep better", emoji: "🌙"), .init(label: "Skincare routine", emoji: "🍃"),
            .init(label: "Manage stress", emoji: "🌿"), .init(label: "Fitness & nutrition", emoji: "🍎"),
        ]),
        ChipMorphMenuCategory(name: "Travel", symbol: "paperplane.fill", members: 7, topics: [
            .init(label: "Weekend escapes", emoji: "⛺"), .init(label: "Travel on a budget", emoji: "🎟"),
            .init(label: "Going solo", emoji: "🎒"), .init(label: "Food to fly for", emoji: "🍜"),
        ]),
        ChipMorphMenuCategory(name: "Money", symbol: "dollarsign.circle.fill", members: 9, topics: [
            .init(label: "Budget basics", emoji: "💰"), .init(label: "Save for a trip", emoji: "🏦"),
            .init(label: "Investing 101", emoji: "📈"), .init(label: "Split the bills", emoji: "🧾"),
        ]),
    ]
}

struct ChipMorphMenuPerson: Equatable {
    var name: String
    var photo: String
    var interest: String

    static func photo(_ id: String, _ width: Int, _ height: Int) -> String {
        "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=\(width)&h=\(height)&q=80"
    }

    static let samples = [
        ChipMorphMenuPerson(name: "Noor Haddad", photo: photo("1544005313-94ddf0286df2", 360, 360), interest: "Meeting new people"),
        ChipMorphMenuPerson(name: "Leo Marsh", photo: photo("1506794778202-cad84cf45f1d", 360, 360), interest: "Weekend escapes"),
        ChipMorphMenuPerson(name: "Mina Okoye", photo: photo("1487412720507-e7ab37603c6f", 360, 360), interest: "Sleep better"),
    ]

    static let matches = [
        ChipMorphMenuPerson(name: "Jonas Reed", photo: photo("1535713875002-d1d0cf377fde", 360, 360), interest: ""),
        ChipMorphMenuPerson(name: "Clara Wynn", photo: photo("1517841905240-472988babdf9", 360, 360), interest: ""),
        ChipMorphMenuPerson(name: "Theo Barnes", photo: photo("1531891437562-4301cf35b7e4", 360, 360), interest: ""),
    ]

    static let takes = [
        photo("1522202176988-66273c2fd55f", 324, 600),
        photo("1521572267360-ee0c2909d518", 324, 600),
        photo("1504593811423-6dd665756598", 324, 600),
        photo("1526772662000-3f88f10405ff", 324, 600),
    ]
}

/// A community page with a row of category chips. A chip does not drop a menu: it swells into a
/// white card in the middle of the screen, over the darkened page, with its glyph and label as the
/// card's title and the category's topics under it. A topic, or a tap outside, sends the card back
/// into the chip; a picked topic turns the chip dark and changes the heading and the profile cards.
struct ChipMorphMenu: View {
    /// The chips; each opens into a card of its topics.
    var categories = ChipMorphMenuCategory.samples
    /// The profile cards shown while no topic is chosen.
    var people = ChipMorphMenuPerson.samples
    /// The profile cards shown for a chosen topic, which is written on them.
    var matches = ChipMorphMenuPerson.matches
    /// Photos of the row at the bottom.
    var takes = ChipMorphMenuPerson.takes
    var title = "For your first chat"
    var takesTitle = "Member Takes"
    /// Who the line under the header says came online.
    var online = "Asha Verma"
    /// Opens a chip, picks a topic, opens another and clears the choice by itself every `ChipMorphMenuMotion.loop` seconds.
    var autoPlay = false
    /// Called when a topic is picked, and with nil when the dark chip is tapped to clear it.
    var onSelect: ((ChipMorphMenuCategory, ChipMorphMenuTopic?) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The chip whose card is on the screen, opening, open or closing; `shown` is the last one, which the card keeps while hidden.
    @State private var active: Int? = nil
    @State private var shown = 0
    @State private var open = false
    /// The topic that was picked, and the chip it belongs to.
    @State private var choice: Choice? = nil
    @State private var tab = 0
    /// The topic held down by the autoPlay loop.
    @State private var autoPressed: Int? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Changes when the loop starts over, which sends the rows back to their start.
    @State private var resets = 0
    /// Where each chip is on the screen.
    @State private var frames: [Int: CGRect] = [:]
    @State private var peopleAt: Double? = nil
    @State private var haptic = UISelectionFeedbackGenerator()

    private struct Choice: Equatable {
        let category: Int
        let topic: Int
    }

    private var chosen: ChipMorphMenuTopic? {
        choice.map { categories[$0.category].topics[$0.topic] }
    }

    private var heading: String { chosen?.label ?? title }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let cardWidth = min(Metrics.cardWidth, size.width - Metrics.margin * 2)
            let card = CGRect(x: (size.width - cardWidth) / 2, y: insets.top + Metrics.cardTop, width: cardWidth, height: Metrics.cardHeight)

            ZStack(alignment: .topLeading) {
                page(width: size.width, top: insets.top)

                tabs
                    .padding(.bottom, max(insets.bottom, 16))
                    .frame(width: size.width, height: size.height, alignment: .bottom)

                // The cover lies on everything, the tab capsule too; a tap on it closes the card.
                Color.black
                    .opacity(open ? M.cover : 0)
                    .animation(.easeInOut(duration: M.coverFor), value: open)
                    .frame(width: size.width, height: size.height)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        touched()
                        close()
                    }
                    .allowsHitTesting(open)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel("Close")
                    .accessibilityHidden(!open)

                // Always in the tree, so that it is at the chip before it starts to grow.
                ChipMorphMenuCard(
                    progress: open ? 1 : 0,
                    category: categories[shown],
                    chip: frames[shown] ?? .zero,
                    card: card,
                    dark: choice?.category == shown,
                    open: open,
                    still: reduceMotion,
                    pressed: autoPressed,
                    onTopic: pressTopic
                )
                // Shown and hidden at once: only what is inside it moves.
                .opacity(active == nil ? 0 : 1)
                .animation(nil, value: active)
                .allowsHitTesting(open)
                .accessibilityHidden(active == nil)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .coordinateSpace(name: Metrics.space)
            .ignoresSafeArea()
        }
        .background(Palette.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The page

    private func page(width: Double, top: Double) -> some View {
        ZStack(alignment: .topLeading) {
            header
                .frame(width: width - Metrics.margin * 2, height: Metrics.live)
                .offset(x: Metrics.margin, y: top + Metrics.headerTop)

            HStack(spacing: 0) {
                Text(String(online.prefix(1)))
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Palette.ink)
                    .frame(width: 18, height: 18)
                    .background(Circle().fill(Palette.faceOnline))
                    .padding(.trailing, 8)
                Text(online)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Palette.ink)
                Circle().fill(Palette.green).frame(width: 6, height: 6).padding(.leading, 8).padding(.trailing, 5)
                Text("came online")
                    .font(.system(size: 13))
                    .foregroundStyle(Palette.mute)
            }
            .lineLimit(1)
            .frame(height: 20)
            .offset(x: Metrics.margin, y: top + Metrics.onlineTop)

            chips
                .frame(width: width, height: Metrics.chip)
                .offset(y: top + Metrics.chipsTop)

            // The heading and the profile cards change with the chosen topic.
            Text(heading)
                .font(.system(size: 18, weight: .semibold))
                .tracking(-0.2)
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .id(heading)
                .transition(.opacity)
                .frame(width: width - Metrics.margin * 2, height: 24, alignment: .leading)
                .offset(x: Metrics.margin, y: top + Metrics.headingTop)
                .accessibilityAddTraits(.isHeader)

            peopleRow
                .id(heading)
                .transition(.opacity)
                .frame(width: width, height: Metrics.personHeight)
                .offset(y: top + Metrics.peopleTop)

            Text(takesTitle)
                .font(.system(size: 18, weight: .semibold))
                .tracking(-0.2)
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .frame(height: 24)
                .offset(x: Metrics.margin, y: top + Metrics.takesTop)
                .accessibilityAddTraits(.isHeader)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Metrics.thumbGap) {
                    ForEach(Array(takes.enumerated()), id: \.offset) { index, url in
                        ChipMorphMenuPhoto(url: url)
                            .frame(width: Metrics.thumbWidth, height: Metrics.thumbHeight)
                            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .overlay(alignment: .topLeading) {
                                if index == 0 {
                                    Text("NEW")
                                        .font(.system(size: 9, weight: .bold))
                                        .tracking(0.4)
                                        .foregroundStyle(Palette.ink)
                                        .padding(.horizontal, 7)
                                        .frame(height: 18)
                                        .background(Capsule().fill(Palette.pale))
                                        .padding(10)
                                }
                            }
                    }
                }
                .padding(.horizontal, Metrics.margin)
            }
            .frame(width: width, height: Metrics.thumbHeight)
            .offset(y: top + Metrics.thumbsTop)
        }
        .animation(.easeInOut(duration: M.swap), value: heading)
    }

    private var header: some View {
        HStack(spacing: 12) {
            HStack(spacing: 0) {
                Circle()
                    .stroke(Palette.ink, lineWidth: 2)
                    .padding(1)
                    .overlay(Circle().fill(Palette.ink).frame(width: 6, height: 6))
                    .frame(width: 20, height: 20)
                Circle().fill(Palette.green).frame(width: 7, height: 7).padding(.leading, 12).padding(.trailing, 6)
                Text("LIVE NOW")
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1)
                    .foregroundStyle(Palette.green)
            }
            .padding(.leading, 12)
            .padding(.trailing, 16)
            .frame(height: Metrics.live)
            .background(Capsule().fill(Palette.pale))
            .overlay(Capsule().strokeBorder(Palette.paleEdge, lineWidth: 1))
            Spacer(minLength: 0)
            Image(systemName: "tray.fill")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Palette.ink)
                .frame(width: Metrics.round, height: Metrics.round)
                .background(Circle().fill(Palette.soft))
                .accessibilityLabel("Inbox")
            Text("YO")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Palette.ink)
                .frame(width: Metrics.round, height: Metrics.round)
                .background(Circle().fill(Palette.faceYou))
                .accessibilityLabel("Your profile")
        }
    }

    private var chips: some View {
        ScrollViewReader { reader in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Metrics.chipGap) {
                    Button {
                        touched()
                    } label: {
                        Image(systemName: "line.3.horizontal.decrease")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Palette.ink)
                            .frame(width: Metrics.chip, height: Metrics.chip)
                            .background(Circle().fill(Palette.soft))
                    }
                    .buttonStyle(DimStyle())
                    .accessibilityLabel("Filters")
                    .id(Metrics.start)

                    ForEach(Array(categories.enumerated()), id: \.offset) { index, category in
                        let dark = choice?.category == index && active != index
                        Button {
                            pressChip(index)
                        } label: {
                            ChipMorphMenuTitle(category: category, ink: dark ? .white : Palette.ink, mark: dark ? .white : Palette.mute, chevron: 1)
                                .padding(.leading, Metrics.chipPad)
                                .padding(.trailing, Metrics.chipEnd)
                                .frame(height: Metrics.chip)
                                .background(Capsule().fill(dark ? Palette.ink : Palette.soft))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        // Its place stays empty while it is the card.
                        .opacity(active == index ? 0 : 1)
                        .animation(nil, value: active)
                        .accessibilityLabel(dark ? "\(category.name). Clear the topic" : "\(category.name). Show topics")
                        .accessibilityAddTraits(dark ? [.isSelected] : [])
                        .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Metrics.space)) } action: { frame in
                            // A row that moves under a finger is a touch; the first measure is not.
                            if let old = frames[index], abs(old.minX - frame.minX) > 0.5, active == nil { touched() }
                            frames[index] = frame
                        }
                    }
                }
                .padding(.horizontal, Metrics.margin)
            }
            .onChange(of: resets) { withAnimation { reader.scrollTo(Metrics.start, anchor: .leading) } }
        }
    }

    private var peopleRow: some View {
        ScrollViewReader { reader in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Metrics.personGap) {
                    ForEach(Array((chosen == nil ? people : matches).enumerated()), id: \.offset) { index, person in
                        VStack(spacing: 0) {
                            ChipMorphMenuPhoto(url: person.photo)
                                .frame(width: Metrics.portrait, height: Metrics.portrait)
                                .clipShape(Circle())
                                .padding(Metrics.ring)
                                .background(Circle().fill(.white).shadow(color: .black.opacity(0.14), radius: 10, y: 8))
                                .padding(.top, 25)
                            Text(person.name)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(Palette.ink)
                                .frame(height: 22)
                                .padding(.top, 18)
                            Text("Because you are interested in")
                                .font(.system(size: 13))
                                .foregroundStyle(Palette.mute)
                                .frame(height: 18)
                                .padding(.top, 9)
                            Text(chosen?.label ?? person.interest)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(Palette.ink)
                                .frame(height: 18)
                            Button {
                                touched()
                            } label: {
                                HStack(spacing: 8) {
                                    Circle().fill(Palette.green).frame(width: 8, height: 8)
                                    Text("Chat")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundStyle(Palette.ink)
                                }
                                .padding(.horizontal, 16)
                                .frame(height: 30)
                                .background(Capsule().fill(.white))
                            }
                            .buttonStyle(DimStyle())
                            .accessibilityLabel("Chat with \(person.name)")
                            .padding(.top, 15)
                            Spacer(minLength: 0)
                        }
                        .lineLimit(1)
                        .frame(width: Metrics.personWidth, height: Metrics.personHeight)
                        .background(RoundedRectangle(cornerRadius: 30, style: .continuous).fill(Palette.panel))
                        .id(index)
                    }
                }
                .padding(.horizontal, Metrics.margin)
                .onGeometryChange(for: Double.self) { $0.frame(in: .named(Metrics.space)).minX } action: { x in
                    if let old = peopleAt, abs(old - x) > 0.5 { touched() }
                    peopleAt = x
                }
            }
            .onChange(of: resets) { withAnimation { reader.scrollTo(0, anchor: .leading) } }
        }
    }

    private var tabs: some View {
        HStack(spacing: 0) {
            ForEach(Array(["Home", "Chats"].enumerated()), id: \.offset) { index, label in
                Button {
                    touched()
                    haptic.selectionChanged()
                    tab = index
                } label: {
                    VStack(spacing: 2) {
                        Image(systemName: index == 0 ? "house.fill" : "bubble.left")
                            .font(.system(size: 15, weight: .medium))
                            .frame(width: 20, height: 20)
                        Text(label)
                            .font(.system(size: 11, weight: .semibold))
                            .frame(height: 14)
                    }
                    .foregroundStyle(tab == index ? Palette.ink : Palette.mute)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(tab == index ? [.isSelected] : [])
            }
        }
        .frame(width: Metrics.tabsWidth, height: Metrics.tabsHeight)
        .background(Capsule().fill(.white).shadow(color: .black.opacity(0.14), radius: 12, y: 8))
    }

    // MARK: Behaviour

    private var morph: Animation {
        // Reduced motion: the card does not travel, it fades with the cover.
        reduceMotion ? .easeInOut(duration: M.coverFor) : .interpolatingSpring(mass: 1, stiffness: M.stiffness, damping: M.damping)
    }

    private func show(_ index: Int) {
        guard frames[index] != nil else { return }
        active = index
        shown = index
        withAnimation(morph) { open = true }
    }

    private func close() {
        withAnimation(morph) {
            open = false
        } completion: {
            if !open { active = nil }
        }
    }

    private func choose(_ index: Int, _ topic: Int?) {
        choice = topic.map { Choice(category: index, topic: $0) }
        onSelect?(categories[index], topic.map { categories[index].topics[$0] })
    }

    private func pressChip(_ index: Int) {
        touched()
        // A card on its way back does not hold up the next one.
        guard active == nil || !open else { return }
        haptic.selectionChanged()
        if choice?.category == index { choose(index, nil) } else { show(index) }
    }

    private func pressTopic(_ topic: Int) {
        touched()
        guard let active, open else { return }
        haptic.selectionChanged()
        choose(active, topic)
        close()
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        autoPressed = nil
        touches += 1
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            if open { close() }
            while active != nil, !Task.isCancelled { try? await clock.sleep(for: .seconds(0.1)) }
            guard !Task.isCancelled else { return }
            // The loop starts over from the page as it was given.
            choice = nil
            tab = 0
            resets += 1
        }
        var origin = clock.now
        while !Task.isCancelled {
            guard await wait(clock, until: origin + .seconds(M.openAt)) else { return }
            show(0)
            guard await wait(clock, until: origin + .seconds(M.pressAt)) else { return }
            autoPressed = M.picked
            guard await wait(clock, until: origin + .seconds(M.pickAt)) else { return }
            autoPressed = nil
            choose(0, M.picked)
            close()
            guard await wait(clock, until: origin + .seconds(M.againAt)) else { return }
            show(1)
            guard await wait(clock, until: origin + .seconds(M.outAt)) else { return }
            close()
            guard await wait(clock, until: origin + .seconds(M.clearAt)) else { return }
            choose(0, nil)
            origin += .seconds(M.loop)
            guard await wait(clock, until: origin) else { return }
        }
    }

    /// Sleeps to a moment of the loop exactly; false when a touch has taken over meanwhile.
    private func wait(_ clock: ContinuousClock, until moment: ContinuousClock.Instant) async -> Bool {
        try? await clock.sleep(until: moment, tolerance: .zero)
        return !Task.isCancelled
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.6 : 1)
        }
    }
}

/// The motion's numbers, shared by the screen and its card.
enum ChipMorphMenuMotion {
    /// autoPlay: Career, a topic, Wellness and out again, then the choice is cleared.
    static let loop = 10.0
    static let openAt = 0.8
    static let pressAt = 2.45
    static let pickAt = 2.6
    static let picked = 2
    static let againAt = 4.4
    static let outAt = 6.2
    static let clearAt = 7.4
    /// After a touch autoPlay waits this long, and for the card to be closed, before it starts over.
    static let userHold = 3.0

    // The chip's frame springs to the card's and back: response 0.4 s, about 3% past its target.
    static let stiffness = 247.0
    static let damping = 23.4
    /// The page under an open card.
    static let cover = 0.72
    static let coverFor = 0.3
    // The members line and the topics come up one after another, and leave together.
    static let riseAfter = 0.15
    static let riseStep = 0.06
    static let riseFor = 0.4
    static let riseBy = 10.0
    static let leaveFor = 0.12
    static let chevronGone = 0.3
    /// The card is white but for the last part of its way back, where it takes the chip's colour.
    static let tintBy = 0.4
    /// The heading and the profile cards change with the chosen topic.
    static let swap = 0.25
    static let press = 0.95

    static func mix(_ from: Double, _ to: Double, _ t: Double) -> Double { from + (to - from) * t }

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }
}

private typealias M = ChipMorphMenuMotion

private enum Palette {
    static let page = Color.white
    static let ink = Color(hex: 0x1C1C1E)
    static let mute = Color(hex: 0x8E8E93)
    static let soft = Color(hex: 0xF4F4F4)
    static let panel = Color(hex: 0xF6F6F6)
    static let rule = Color(hex: 0xEFEFEF)
    static let green = Color(hex: 0x2DBE6C)
    static let pale = Color(hex: 0xDDF7E8)
    static let paleEdge = Color(hex: 0xBFEFD3)
    /// The little faces of the members line, and of the people in the header.
    static let faces = [Color(hex: 0xF2B8A0), Color(hex: 0xA9C7F5), Color(hex: 0xBFE3B4)]
    static let faceOnline = Color(hex: 0xF5C9A8)
    static let faceYou = Color(hex: 0xC9C2F0)

    /// A grey between two greys: `ink` (0x1C) to `soft` (0xF4) to white are all near neutral.
    static func blend(_ from: (Double, Double, Double), _ to: (Double, Double, Double), _ t: Double) -> Color {
        Color(.sRGB, red: M.mix(from.0, to.0, t) / 255, green: M.mix(from.1, to.1, t) / 255, blue: M.mix(from.2, to.2, t) / 255)
    }

    static let inkParts = (28.0, 28.0, 30.0)
    static let softParts = (244.0, 244.0, 244.0)
    static let muteParts = (142.0, 142.0, 147.0)
    static let whiteParts = (255.0, 255.0, 255.0)
}

private enum Metrics {
    static let space = "ChipMorphMenu"
    static let start = "ChipMorphMenuStart"
    static let margin = 20.0
    // The page, from the safe area's top.
    static let headerTop = 14.0
    static let live = 40.0
    static let round = 38.0
    static let onlineTop = 87.0
    static let chipsTop = 144.0
    static let headingTop = 217.0
    static let peopleTop = 263.0
    static let takesTop = 585.0
    static let thumbsTop = 629.0
    // A chip: its glyph, its label and a chevron.
    static let chip = 38.0
    static let chipGap = 10.0
    static let chipPad = 12.0
    static let chipEnd = 10.0
    static let glyph = 18.0
    static let labelGap = 6.0
    static let chevron = 16.0
    static let chevronGap = 4.0
    static let titleLine = 20.0
    /// What a chip is wider than its glyph and label by.
    static let titleTrim = chipPad + chevronGap + chevron + chipEnd
    static let personWidth = 247.0
    static let personHeight = 302.0
    static let personGap = 14.0
    static let portrait = 118.0
    static let ring = 4.0
    static let thumbWidth = 108.0
    static let thumbHeight = 200.0
    static let thumbGap = 10.0
    static let tabsWidth = 184.0
    static let tabsHeight = 54.0
    // The card a chip opens into, and where its parts sit from its top.
    static let cardWidth = 340.0
    static let cardHeight = 388.0
    static let cardTop = 152.0
    static let cardRadius = 28.0
    static let titleAt = 38.0
    static let titleGrown = 17.0 / 14.0
    static let membersAt = 82.0
    static let topicsAt = 144.0
    static let topicStep = 58.0
    static let topic = 40.0
}

/// A chip's glyph, label and chevron: the row that becomes the card's title.
struct ChipMorphMenuTitle: View {
    let category: ChipMorphMenuCategory
    let ink: Color
    let mark: Color
    /// The chevron's opacity.
    let chevron: Double

    var body: some View {
        HStack(spacing: 0) {
            // SF Symbols are drawn larger than their point size; 14 fills the 18-point box.
            Image(systemName: category.symbol)
                .font(.system(size: 14, weight: .medium))
                .frame(width: Metrics.glyph, height: Metrics.glyph)
            Text(category.name)
                .font(.system(size: 14, weight: .medium))
                .lineLimit(1)
                .padding(.leading, Metrics.labelGap)
            Image(systemName: "chevron.down")
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(mark)
                .frame(width: Metrics.chevron, height: Metrics.chevron)
                .padding(.leading, Metrics.chevronGap)
                .opacity(chevron)
        }
        .foregroundStyle(ink)
        .frame(height: Metrics.titleLine)
        .fixedSize()
    }
}

/// The chip on its way to the card and back: one frame, one fill and one title, all placed from
/// `progress` (0 is the chip, 1 the card), which SwiftUI animates with the spring.
struct ChipMorphMenuCard: View, Animatable {
    var progress: Double
    let category: ChipMorphMenuCategory
    /// The chip's frame and the card's, on the screen.
    let chip: CGRect
    let card: CGRect
    /// The chip is the dark one: its topic has just been picked.
    let dark: Bool
    let open: Bool
    /// Reduced motion: the card keeps its place and `progress` is its opacity.
    let still: Bool
    /// The topic held down by the autoPlay loop.
    let pressed: Int?
    let onTopic: (Int) -> Void

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        let g = still ? 1 : progress
        let flat = M.clamp01(g)
        let tint = M.clamp01(g / M.tintBy)
        let frame = CGRect(
            x: M.mix(chip.minX, card.minX, g), y: M.mix(chip.minY, card.minY, g),
            width: max(0, M.mix(chip.width, card.width, g)), height: max(0, M.mix(chip.height, card.height, g))
        )
        let shape = RoundedRectangle(cornerRadius: M.mix(Metrics.chip / 2, Metrics.cardRadius, flat))
        let scale = M.mix(1, Metrics.titleGrown, g)
        let titleWidth = chip.width - Metrics.titleTrim

        ZStack(alignment: .topLeading) {
            // The card's parts keep their places on the screen while its frame grows round them.
            inside
                .frame(width: card.width, height: card.height, alignment: .topLeading)
                .offset(x: card.minX - frame.minX, y: card.minY - frame.minY)
                .frame(width: frame.width, height: frame.height, alignment: .topLeading)
                .background(Palette.blend(dark ? Palette.inkParts : Palette.softParts, Palette.whiteParts, tint))
                .clipShape(shape)
                .shadow(color: .black.opacity(0.3 * flat), radius: 25, y: 20)
                .offset(x: frame.minX, y: frame.minY)

            // The chip's glyph and label are the card's title: one row that travels and grows.
            ChipMorphMenuTitle(
                category: category,
                ink: Palette.blend(dark ? Palette.whiteParts : Palette.inkParts, Palette.inkParts, tint),
                mark: Palette.blend(dark ? Palette.whiteParts : Palette.muteParts, Palette.muteParts, tint),
                chevron: 1 - M.clamp01(g / M.chevronGone)
            )
            .scaleEffect(scale, anchor: .leading)
            .offset(
                x: M.mix(chip.minX + Metrics.chipPad, card.midX - titleWidth * scale / 2, g),
                y: M.mix(chip.midY, card.minY + Metrics.titleAt, g) - Metrics.titleLine / 2
            )
            .allowsHitTesting(false)
        }
        .opacity(still ? M.clamp01(progress) : 1)
        .accessibilityAddTraits(.isModal)
    }

    private var inside: some View {
        ZStack(alignment: .top) {
            HStack(spacing: 0) {
                HStack(spacing: -6) {
                    ForEach(Array(Palette.faces.enumerated()), id: \.offset) { _, color in
                        Circle()
                            .fill(color)
                            .overlay(Circle().strokeBorder(.white, lineWidth: 1.5))
                            .frame(width: 18, height: 18)
                    }
                }
                .padding(.trailing, 8)
                Text("\(category.members) Members")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Palette.ink)
                Text(" online")
                    .font(.system(size: 13))
                    .foregroundStyle(Palette.mute)
            }
            .frame(height: 20)
            .modifier(Rise(shown: open, order: 0))
            .offset(y: Metrics.membersAt - 10)

            ForEach(Array(category.topics.enumerated()), id: \.offset) { index, topic in
                Button {
                    onTopic(index)
                } label: {
                    HStack(spacing: 8) {
                        Text(topic.emoji).font(.system(size: 16))
                        Text(topic.label)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Palette.ink)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 14)
                    .frame(height: Metrics.topic)
                    .background(Capsule().fill(.white).shadow(color: .black.opacity(0.08), radius: 6, y: 4))
                    .overlay(Capsule().strokeBorder(Palette.rule, lineWidth: 1))
                    .contentShape(Capsule())
                }
                .buttonStyle(PressStyle(held: pressed == index))
                .modifier(Rise(shown: open, order: index + 1))
                .offset(y: Metrics.topicsAt + Double(index) * Metrics.topicStep - Metrics.topic / 2)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    /// A part of the card that comes up after the card has started to open: `order` sets its turn.
    private struct Rise: ViewModifier {
        let shown: Bool
        let order: Int

        func body(content: Content) -> some View {
            content
                .opacity(shown ? 1 : 0)
                .offset(y: shown ? 0 : M.riseBy)
                .animation(
                    shown
                        ? .timingCurve(0.23, 1, 0.32, 1, duration: M.riseFor).delay(M.riseAfter + Double(order) * M.riseStep)
                        : .linear(duration: M.leaveFor),
                    value: shown
                )
        }
    }

    private struct PressStyle: ButtonStyle {
        let held: Bool

        func makeBody(configuration: Configuration) -> some View {
            let pressed = configuration.isPressed || held
            configuration.label
                .scaleEffect(pressed ? M.press : 1)
                .animation(.easeOut(duration: 0.1), value: pressed)
        }
    }
}

/// A photo from the network on a soft grey until it arrives.
struct ChipMorphMenuPhoto: View {
    let url: String

    var body: some View {
        Palette.rule
            .overlay {
                AsyncImage(url: URL(string: url), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill().transition(.opacity)
                    }
                }
            }
            .clipped()
            .accessibilityHidden(true)
    }
}

#Preview {
    ChipMorphMenu(autoPlay: true)
}
