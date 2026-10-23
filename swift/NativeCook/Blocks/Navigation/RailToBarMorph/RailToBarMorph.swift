import SwiftUI

struct RailToBarMorphItem: Identifiable, Equatable {
    var id: String
    var label: String
    /// The line icon, and the solid one that tints the lit icon of the bar: SF Symbols.
    var icon: String
    var solid: String

    static let samples = [
        RailToBarMorphItem(id: "home", label: "Home", icon: "house", solid: "house.fill"),
        RailToBarMorphItem(id: "explore", label: "Explore", icon: "safari", solid: "safari.fill"),
        RailToBarMorphItem(id: "notifications", label: "Notifications", icon: "bell", solid: "bell.fill"),
        RailToBarMorphItem(id: "messages", label: "Messages", icon: "envelope", solid: "envelope.fill"),
        RailToBarMorphItem(id: "bookmarks", label: "Bookmarks", icon: "bookmark", solid: "bookmark.fill"),
        RailToBarMorphItem(id: "lists", label: "Lists", icon: "list.bullet.rectangle", solid: "list.bullet.rectangle.fill"),
        RailToBarMorphItem(id: "profile", label: "Profile", icon: "person", solid: "person.fill"),
    ]
}

/// One white navigation surface in three shapes: a labelled list, a narrow icon rail and a bottom
/// bar. The surface itself morphs from one to the next, and every icon is the same view all the
/// way: it flies from its row in the column to its place in the bar along a curve.
struct RailToBarMorph: View {
    /// The destinations, in the order of the list.
    var items = RailToBarMorphItem.samples
    /// How many of them, from the top, the bar holds.
    var barItems = 5
    /// The field's colour, which also marks the current destination.
    var color = Color(hex: 0x2F6BFF)
    /// Goes through the three shapes by itself, back at the list every `loop` seconds.
    var autoPlay = false
    var onSelect: ((RailToBarMorphItem) -> Void)? = nil

    private typealias G = RailToBarMorphGeometry
    private typealias Tween = RailToBarMorphTween

    private enum Shape {
        case list, rail, bar
    }

    private enum Palette {
        static let ink = Color(hex: 0x111111)
        static let capsule = Color(hex: 0xE8F0FF)
        static let scrim = Color(hex: 0x081030)
        static let scrimAlpha = 0.42
        static let halo = Color.white.opacity(0.05)
        static let card = Color.white.opacity(0.14)
        static let line = Color.white.opacity(0.45)
        static let dot = Color(hex: 0x111111, opacity: 0.12)
        static let fillAlpha = 0.2
    }

    private enum M {
        static let hit = 44.0
        static let capsuleX = 16.0
        static let capsule = 44.0
        static let capsulePad = 20.0
        static let dot = 10.0
        static let dotDrop = 16.0
        static let margin = 20.0
        static let titleY = 56.0
        /// The two soft circles behind the content: radii, and how far down the screen their centre is.
        static let halos = [260.0, 420.0]
        static let haloY = 0.78
        /// A symbol is drawn larger than an icon-font glyph of the same size.
        static let symbol = 0.8

        /// The list narrows to the rail on a spring.
        static let wideSpring = Spring(mass: 1, stiffness: 200, damping: 24)
        /// A label fades and slides over `labelIn` seconds, `labelGap` after the one above it.
        static let labelIn = 0.25
        static let labelGap = 0.02
        static let labelShift = 10.0
        /// From the bar, the labels wait for the list to open; by hand the list collapses to the
        /// rail and, a moment later, on to the bar.
        static let labelsAfter = 0.55
        static let collapseAfter = 0.25
        /// The highlight moves in `select` seconds; its leading edge arrives after `lead` of that,
        /// so the capsule stretches on the way.
        static let select = 0.25
        static let lead = 0.64
        /// A tap in the bar: the icon hops for `hopUp` of the time and lands with a squash.
        static let hopFor = 0.4
        static let hopUp = 0.7
        static let hop = 8.0
        static let squash = 0.1
        /// Reduced motion: the shapes jump and the surface fades back in.
        static let fade = 0.2
        /// A swipe to the right that starts in this band brings the list out of the bar.
        static let edge = 40.0

        static let userHold = 3.0
        static let loop = 10.0
        /// The loop that plays by itself: what is due when, in seconds from the start of a loop.
        static let script: [(at: Double, shape: Shape?, current: Int?)] = [
            (0.8, nil, 1),
            (1.5, nil, 2),
            (2.3, .rail, nil),
            (3.2, .bar, nil),
            (4.4, nil, 3),
            (5.4, nil, 4),
            (6.4, nil, 0),
            (7.8, .list, nil),
        ]
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shape = Shape.list
    @State private var current = 0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0

    /// 1 for the list, 0 for the rail and the bar.
    @State private var wide = Tween(1)
    /// 0 for the list and the rail, 1 for the bar: a clock, eased where it is read.
    @State private var dock = Tween(0)
    /// The highlight on its way from one row to another.
    @State private var selFrom = 0
    @State private var selTo = 0
    @State private var selClock = Tween(1)
    /// The icon that hops, and the clock of its hop.
    @State private var hopIndex = -1
    @State private var hop = Tween(1)
    /// The clock of the labels leaving or coming back, and which of the two it is.
    @State private var labels = Tween(1)
    @State private var showing = true
    /// The whole surface, which fades back in after a jump under reduced motion.
    @State private var fade = Tween(1)
    /// The timeline runs until this moment, and is paused while nothing moves.
    @State private var movingUntil = Date.distantPast
    @State private var moving = false
    @State private var haptic = UISelectionFeedbackGenerator()

    private var docked: Int { min(barItems, items.count) }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let frame = G.Frame(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom,
                top: insets.top, bar: G.bar + insets.bottom
            )
            let widths = items.map { Self.width(of: $0.label) }

            TimelineView(.animation(paused: !moving)) { timeline in
                stage(at: timeline.date, frame: frame, widths: widths)
            }
            .frame(width: frame.width, height: frame.height, alignment: .topLeading)
            .ignoresSafeArea()
            .simultaneousGesture(swipe)
        }
        .statusBarHidden()
        .onAppear { haptic.prepare() }
        .task(id: movingUntil) {
            let wait = movingUntil.timeIntervalSinceNow
            if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
            if !Task.isCancelled { moving = false }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The stage

    private func stage(at now: Date, frame: G.Frame, widths: [Double]) -> some View {
        let w = wide.value(at: now)
        let d = dock.value(at: now)
        let e = G.easeInOut(d)
        let c = selClock.value(at: now)
        let shown = fade.value(at: now)
        let panel = G.panel(wide: w, dock: d, docked: docked, frame: frame)
        let mark = G.markPoint(wide: w, dock: d, frame: frame)
        let inList = G.clamp(w) * (1 - e)
        // The content clears the rail, and takes the whole width over the bar.
        let left = G.mix(G.rail + M.margin, M.margin, e)

        return ZStack(alignment: .topLeading) {
            color

            ForEach(M.halos, id: \.self) { radius in
                Circle()
                    .fill(Palette.halo)
                    .frame(width: radius * 2, height: radius * 2)
                    .position(x: frame.width / 2, y: frame.height * M.haloY)
            }

            RailToBarMorphContent(title: items.indices.contains(current) ? items[current].label : "", index: current)
                .frame(width: max(0, frame.width - left - M.margin), alignment: .topLeading)
                .offset(x: left, y: frame.top + M.titleY)
                .accessibilityHidden(shape == .list)

            // The mark twice at one place: white on the field, and in colour on the panel, which
            // hides the white one.
            RailToBarMorphMark()
                .fill(Color.white)
                .frame(width: G.mark, height: G.mark)
                .position(mark)
                .opacity(shown)

            Palette.scrim
                .opacity(Palette.scrimAlpha * inList)
                .allowsHitTesting(false)

            ZStack(alignment: .topLeading) {
                Color.white
                capsule(clock: c, frame: frame, widths: widths)
                    .opacity(inList)
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    label(item.label, index: index, tint: tint(index, clock: c), at: now, frame: frame)
                }
                RailToBarMorphMark()
                    .fill(color)
                    .frame(width: G.mark, height: G.mark)
                    .position(mark)
            }
            .clipShape(RailToBarMorphPanel(frame: panel.rect, radii: panel.radii))
            .opacity(shown)
            .allowsHitTesting(false)

            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                icon(item, index: index, clock: c, wide: w, dock: d, at: now, frame: frame)
                    .opacity(shown)
            }

            if shape == .list {
                Color.clear
                    .contentShape(Rectangle())
                    .padding(.leading, G.list)
                    .onTapGesture {
                        touched()
                        reshape(.bar)
                    }
                    .accessibilityLabel("Close menu")
                    .accessibilityAddTraits(.isButton)
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    Color.clear
                        .contentShape(Rectangle())
                        .frame(width: G.list, height: G.pitch)
                        .offset(y: frame.top + G.rowY + Double(index) * G.pitch - G.pitch / 2)
                        .onTapGesture { pick(index) }
                        .accessibilityLabel(item.label)
                        .accessibilityAddTraits(index == current ? [.isButton, .isSelected] : .isButton)
                }
            }

            if shape == .bar {
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: M.hit, height: M.hit)
                    .position(x: frame.width / 2, y: frame.top + G.markY)
                    .onTapGesture { unfold() }
                    .accessibilityLabel("Menu")
                    .accessibilityAddTraits(.isButton)
            }
        }
    }

    /// How much of the field's colour destination `index` has: all of it while it is current.
    private func tint(_ index: Int, clock: Double) -> Double {
        selTo == index ? clock : selFrom == index ? 1 - clock : 0
    }

    private func capsule(clock: Double, frame: G.Frame, widths: [Double]) -> some View {
        let to = Double(selTo)
        let lead = reduceMotion ? to : G.mix(Double(selFrom), to, G.easeOut(clock / M.lead))
        let trail = reduceMotion ? to : G.mix(Double(selFrom), to, G.easeInOut(clock))
        let middle = (lead + trail) / 2
        let above = min(widths.count - 1, Int(middle))
        let below = min(widths.count - 1, above + 1)
        let width = G.labelX + G.mix(widths[above], widths[below], middle - Double(above)) + M.capsulePad - M.capsuleX
        return RoundedRectangle(cornerRadius: M.capsule / 2)
            .fill(Palette.capsule)
            .frame(width: width, height: M.capsule + abs(lead - trail) * G.pitch)
            .offset(x: M.capsuleX, y: frame.top + G.rowY + min(lead, trail) * G.pitch - M.capsule / 2)
    }

    /// A row's name in the list. The names leave and come back one after another, from the top.
    private func label(_ text: String, index: Int, tint: Double, at now: Date, frame: G.Frame) -> some View {
        let total = M.labelIn + Double(items.count - 1) * M.labelGap
        let arrived = G.easeOut((labels.value(at: now) * total - Double(index) * M.labelGap) / M.labelIn)
        let shown = showing ? arrived : 1 - arrived
        return ZStack {
            Text(text).foregroundStyle(Palette.ink)
            Text(text).foregroundStyle(color).opacity(tint)
        }
        .font(.system(size: 17, weight: .medium))
        .lineLimit(1)
        .fixedSize()
        .frame(height: 22)
        .opacity(shown)
        .offset(x: G.labelX - (reduceMotion ? 0 : M.labelShift * (1 - shown)), y: frame.top + G.rowY + Double(index) * G.pitch - 11)
    }

    /// One destination's icon. It is the same view in the three shapes: it rides the column, flies
    /// to its place in the bar, turns to the field's colour while it is current and, in the bar,
    /// fills from the bottom and hops when it is tapped.
    private func icon(_ item: RailToBarMorphItem, index: Int, clock: Double, wide: Double, dock: Double, at now: Date, frame: G.Frame) -> some View {
        let place = G.icon(index, docked: docked, wide: wide, dock: dock, frame: frame)
        let lit = tint(index, clock: clock)
        let u = hopIndex == index ? hop.value(at: now) : 1
        let lift = u < M.hopUp ? sin(.pi * u / M.hopUp) : 0
        let squash = u < M.hopUp ? 1 : 1 - M.squash * sin(.pi * (u - M.hopUp) / (1 - M.hopUp))
        let enabled = shape == .rail || (shape == .bar && index < docked)
        let size = G.icon * M.symbol

        return Button {
            pick(index)
        } label: {
            ZStack {
                Circle()
                    .fill(Palette.dot)
                    .frame(width: M.dot, height: M.dot)
                    .scaleEffect(1 - 0.6 * lift)
                    .opacity(lit * place.way)
                    .offset(y: M.dotDrop)
                ZStack {
                    // The solid symbol shows through a window that opens from the bottom.
                    Image(systemName: item.solid)
                        .foregroundStyle(color.opacity(Palette.fillAlpha))
                        .frame(width: G.icon, height: G.icon)
                        .mask(alignment: .bottom) {
                            Rectangle().frame(height: G.icon * lit * place.way)
                        }
                    Image(systemName: item.icon)
                        .foregroundStyle(Palette.ink)
                        .opacity(1 - lit)
                    Image(systemName: item.icon)
                        .foregroundStyle(color)
                        .opacity(lit)
                }
                .font(.system(size: size))
                .frame(width: G.icon, height: G.icon)
                .scaleEffect(x: place.scale, y: place.scale * squash)
                // The squash is against the ground: the icon's foot stays where it is.
                .offset(y: -M.hop * lift + (1 - squash) * G.icon / 2)
            }
            .frame(width: M.hit, height: M.hit)
            .contentShape(Rectangle())
        }
        .buttonStyle(RailToBarMorphPlainStyle())
        .accessibilityLabel(item.label)
        .accessibilityAddTraits(index == current ? .isSelected : [])
        .accessibilityHidden(!enabled)
        .allowsHitTesting(enabled)
        .opacity(place.alpha)
        .position(x: place.x, y: place.y)
    }

    private var swipe: some Gesture {
        DragGesture(minimumDistance: 20, coordinateSpace: .global)
            .onChanged { value in
                let across = value.translation.width
                if value.startLocation.x < M.edge, across > 20, abs(value.translation.height) < 20 { unfold() }
            }
    }

    private static func width(of label: String) -> Double {
        (label as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 17, weight: .medium)]).width
    }

    // MARK: Changing

    /// Keeps the timeline running for the next `seconds`.
    private func stir(_ seconds: Double) {
        movingUntil = max(movingUntil, Date.now.addingTimeInterval(seconds + 0.15))
        moving = true
    }

    private func reshape(_ to: Shape) {
        guard to != shape else { return }
        let from = shape
        let now = Date.now
        shape = to
        let toWide = to == .list ? 1.0 : 0.0
        let toDock = to == .bar ? 1.0 : 0.0
        if reduceMotion {
            wide = Tween(toWide)
            dock = Tween(toDock)
            showing = to == .list
            labels = Tween(1)
            fade = Tween(0)
            fade.move(to: 1, at: now, duration: M.fade)
            stir(M.fade)
            return
        }
        if (from == .list) != (to == .list) {
            let total = M.labelIn + Double(items.count - 1) * M.labelGap
            let wait = from == .bar ? M.labelsAfter : 0
            showing = to == .list
            labels = Tween(0)
            labels.move(to: 1, at: now, duration: total, delay: wait)
            wide.move(to: toWide, at: now, spring: M.wideSpring)
            stir(max(wait + total, M.wideSpring.settlingDuration))
        }
        if (from == .bar) != (to == .bar) {
            let wait = from == .list ? M.collapseAfter : 0
            let takes = to == .bar ? G.dockIn : G.dockOut
            dock.move(to: toDock, at: now, duration: takes, delay: wait)
            stir(wait + takes)
        }
    }

    private func go(_ index: Int, hopping: Bool) {
        guard items.indices.contains(index) else { return }
        let now = Date.now
        if index != current {
            selFrom = current
            selTo = index
            selClock = Tween(0)
            selClock.move(to: 1, at: now, duration: M.select)
            current = index
            stir(M.select)
            onSelect?(items[index])
        }
        if hopping, shape == .bar, index < docked, !reduceMotion {
            hopIndex = index
            hop = Tween(0)
            hop.move(to: 1, at: now, duration: M.hopFor)
            stir(M.hopFor)
        }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    private func pick(_ index: Int) {
        touched()
        haptic.selectionChanged()
        go(index, hopping: true)
        // A row of the list goes there and folds the list away.
        if shape == .list { reshape(.bar) }
    }

    private func unfold() {
        guard shape == .bar else { return }
        touched()
        haptic.selectionChanged()
        reshape(.list)
    }

    // MARK: The loop

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // Back to rest; the list is open again by the time the loop first moves.
            go(0, hopping: false)
            reshape(.list)
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            // Each step sets the state that is due at its time, whatever the steps before it did.
            for step in M.script {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                if let shape = step.shape { reshape(shape) }
                if let current = step.current { go(current, hopping: true) }
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

/// A button that does not dim: the icon answers a tap by hopping.
private struct RailToBarMorphPlainStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
    }
}

/// The sample screen behind the navigation: the destination's name over three cards.
private struct RailToBarMorphContent: View {
    var title: String
    var index: Int

    private static let cards = [150.0, 96.0, 96.0]
    /// The two lines on a card, as parts of its width; a destination starts at its own place in the table.
    private static let lines = [[0.62, 0.4], [0.48, 0.3], [0.7, 0.52]]
    private static let swap = 0.25

    var body: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .frame(height: 41)
                    .padding(.bottom, 20)
                ForEach(Array(Self.cards.enumerated()), id: \.offset) { card, height in
                    let lines = Self.lines[(index + card) % Self.lines.count]
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(Color.white.opacity(0.14))
                        .frame(height: height)
                        .overlay {
                            GeometryReader { box in
                                VStack(alignment: .leading, spacing: 10) {
                                    ForEach(lines, id: \.self) { part in
                                        Capsule().fill(Color.white.opacity(0.45)).frame(width: box.size.width * part, height: 10)
                                    }
                                }
                                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                            }
                            .padding(18)
                        }
                        .padding(.bottom, 12)
                }
            }
            .id(index)
            .transition(.opacity)
        }
        .animation(.linear(duration: Self.swap), value: index)
    }
}

#Preview {
    RailToBarMorph(autoPlay: true)
}
