import SwiftUI

struct DepthSideDrawerItem: Identifiable, Equatable {
    var id: String
    var label: String
    /// An SF Symbol.
    var icon: String

    static let samples = [
        DepthSideDrawerItem(id: "home", label: "Home", icon: "house"),
        DepthSideDrawerItem(id: "categories", label: "Categories", icon: "square.grid.2x2"),
        DepthSideDrawerItem(id: "cart", label: "My Cart", icon: "bag"),
        DepthSideDrawerItem(id: "wishlist", label: "Wish List", icon: "heart"),
        DepthSideDrawerItem(id: "orders", label: "Track Order", icon: "shippingbox"),
        DepthSideDrawerItem(id: "account", label: "Account", icon: "person"),
        DepthSideDrawerItem(id: "settings", label: "Settings", icon: "gearshape"),
    ]
}

private enum DepthSideDrawerMotion {
    // The open page is a card scaled about the middle of its left edge, which stops `peek` points
    // short of the screen's right edge, and never over the menu's labels.
    static let peek = 131.0
    static let openMin = 262.0
    static let scale = 0.78
    /// Degrees about the card's left edge: its right edge goes away, like a door swung aside.
    static let tilt = 14.0
    static let perspective = 900.0
    static let lift = -12.0
    static let radius = 30.0
    /// The cards behind the page: how small, how far further left and how faint each one ends, and
    /// the power of the page's progress it follows, which makes it trail.
    static let ghosts = [
        Ghost(scale: 0.72, back: 18, alpha: 0.18, trail: 1.4),
        Ghost(scale: 0.66, back: 34, alpha: 0.09, trail: 1.8),
    ]
    static let openSpring = Animation.interpolatingSpring(mass: 1, stiffness: 140, damping: 17)
    static let closeSpring = Animation.interpolatingSpring(mass: 1, stiffness: 220, damping: 28)
    /// Reduced motion: the page only slides, on a short curve.
    static let slide = 0.25

    // The menu's rows come in one after another, in seconds from the moment the page starts to move.
    static let rowDelay = 0.1
    static let rowGap = 0.035
    static let rowIn = 0.35
    static let rowShift = 24.0
    static let rowsOut = 0.15
    // A chosen row is marked, the page changes behind the menu, and then the drawer closes.
    static let markIn = 0.15
    static let choiceHold = 0.4

    /// A drag opens the drawer when it starts in this band on the left of the closed page.
    static let edge = 80.0
    static let fling = 300.0

    static let row = 44.0
    static let profileLead = 155.0
    static let profileLeadMin = 56.0
    /// From the top of the profile to the bottom of log out with seven items, plus a margin.
    static let menuBody = 504.0
    static let userHold = 3.0
    static let loop = 9.0
    /// The loop that plays by itself: when each state is due, in seconds from the start of a loop.
    static let script: [(at: Double, step: Step)] = [
        (0.8, .drawer(true)),
        (3.0, .choose(3)),
        (3.4, .drawer(false)),
        (5.4, .drawer(true)),
        (7.2, .choose(0)),
        (7.6, .drawer(false)),
    ]

    struct Ghost {
        var scale: Double
        var back: Double
        var alpha: Double
        var trail: Double
    }

    enum Step {
        case drawer(Bool)
        case choose(Int)
    }

    /// A card scaled and turned about the middle of its left edge, seen from `perspective` points
    /// away, and then moved.
    static func projection(size: CGSize, scale: Double, tilt: Double, x: Double, y: Double) -> ProjectionTransform {
        let hinge = size.height / 2
        var lens = CATransform3DIdentity
        lens.m34 = -1 / perspective
        var transform = CATransform3DMakeTranslation(0, -hinge, 0)
        transform = CATransform3DConcat(transform, CATransform3DMakeScale(scale, scale, 1))
        transform = CATransform3DConcat(transform, CATransform3DMakeRotation(tilt * .pi / 180, 0, 1, 0))
        transform = CATransform3DConcat(transform, lens)
        transform = CATransform3DConcat(transform, CATransform3DMakeTranslation(x, hinge + y, 0))
        return ProjectionTransform(transform)
    }
}

/// A side menu with depth. The menu fills the screen underneath; opening it swings the page aside
/// as a rounded card, scaled down and turned about its left edge, with two faint cards trailing
/// behind it, while the menu's rows come in one after another. Choosing a row changes the page
/// behind the menu and closes the drawer.
struct DepthSideDrawer: View {
    /// The menu's destinations; the page starts on the first one.
    var items = DepthSideDrawerItem.samples
    var name = "Mara Ellis"
    var place = "Portland"
    /// The address of the round picture next to the name.
    var photo = URL(string: "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=208&h=208&q=80")
    var logoutLabel = "Logout"
    /// The menu's colour, which also marks the lit tab of the page.
    var menuColor = Color(hex: 0x52349F)
    /// Opens the menu and picks destinations by itself, back at the start every `loop` seconds.
    var autoPlay = false
    var onSelect: ((DepthSideDrawerItem) -> Void)? = nil
    var onLogout: (() -> Void)? = nil

    private typealias M = DepthSideDrawerMotion

    private enum Drag {
        case idle
        case ignored
        case moving(from: Double)
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var open = false
    /// 0 with the page in place, 1 with the drawer open; a spring may pass 1 a little.
    @State private var progress = 0.0
    /// The menu's rows are in: with the drawer, and from the start of a drag that opens it.
    @State private var rowsShown = false
    @State private var destination = 0
    /// The status bar is over the menu once the page is more than half way aside.
    @State private var overMenu = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// A row was chosen by hand: the drawer closes a moment later unless another touch comes first.
    @State private var chosen = false
    @State private var drag = Drag.idle
    @State private var haptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let openX = max(M.openMin, screen.width - M.peek)
            let lead = min(M.profileLead, max(M.profileLeadMin, screen.height - insets.top - insets.bottom - M.menuBody))
            let current = items.indices.contains(destination) ? items[destination] : items[0]

            ZStack(alignment: .topLeading) {
                menuColor

                menu(top: insets.top, lead: lead)

                ForEach([1, 0], id: \.self) { index in
                    RoundedRectangle(cornerRadius: M.radius, style: .continuous)
                        .fill(Color.white)
                        .modifier(DepthSideDrawerGhost(progress: progress, ghost: M.ghosts[index], size: screen, openX: openX, still: reduceMotion))
                        .allowsHitTesting(false)
                }

                DepthSideDrawerPage(
                    title: current.label, index: destination, active: current.id, accent: menuColor, insets: insets,
                    onMenu: { press(true) },
                    onTab: { id in
                        touched()
                        haptic.selectionChanged()
                        if let index = items.firstIndex(where: { $0.id == id }) { go(index) }
                    }
                )
                .frame(width: screen.width, height: screen.height)
                .modifier(DepthSideDrawerSwing(progress: progress, size: screen, openX: openX, still: reduceMotion) { overMenu = $0 })
                .allowsHitTesting(!open)
                .accessibilityHidden(open)

                if open {
                    Color.clear
                        .contentShape(Rectangle())
                        .padding(.leading, openX - M.ghosts[1].back)
                        .onTapGesture { press(false) }
                        .accessibilityLabel("Close menu")
                        .accessibilityAddTraits(.isButton)
                }
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .ignoresSafeArea()
            .simultaneousGesture(dragging(openX: openX))
        }
        .preferredColorScheme(overMenu ? .dark : .light)
        .onAppear { haptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
        .task(id: ChoiceKey(chosen: chosen, touches: touches)) { await closeAfterChoice() }
    }

    // MARK: The menu

    private func menu(top: Double, lead: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                press(false)
            } label: {
                ZStack {
                    Capsule().fill(Color.white).frame(width: 24, height: 2).rotationEffect(.degrees(45))
                    Capsule().fill(Color.white).frame(width: 24, height: 2).rotationEffect(.degrees(-45))
                }
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close menu")
            .padding(.leading, 11)
            .modifier(reveal(0))

            HStack(spacing: 14) {
                AsyncImage(url: photo) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.white.opacity(0.2)
                }
                .frame(width: 52, height: 52)
                .clipShape(Circle())
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(name)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(height: 22)
                    Text(place)
                        .font(.system(size: 13))
                        .foregroundStyle(.white.opacity(0.6))
                        .frame(height: 18)
                }
                .lineLimit(1)
            }
            .frame(height: 52)
            .padding(.leading, 24)
            .padding(.top, lead - 40)
            .modifier(reveal(0))

            hairline
                .padding(.top, 24)
                .modifier(reveal(1))

            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                Button {
                    choose(index)
                } label: {
                    row(icon: item.icon, label: item.label, marked: index == destination)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(index == destination ? .isSelected : [])
                .padding(.top, index == 0 ? 19 : 0)
                .modifier(reveal(index + 2))
            }

            hairline
                .padding(.top, 20)
                .modifier(reveal(items.count + 2))

            Button {
                touched()
                haptic.selectionChanged()
                onLogout?()
            } label: {
                row(icon: "rectangle.portrait.and.arrow.right", label: logoutLabel, marked: false)
            }
            .buttonStyle(.plain)
            .padding(.top, 19)
            .modifier(reveal(items.count + 3))
        }
        .padding(.top, top - 4)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .allowsHitTesting(open)
        .accessibilityHidden(!open)
    }

    private var hairline: some View {
        Color.white.opacity(0.14)
            .frame(width: 200, height: 1)
            .padding(.leading, 24)
    }

    private func row(icon: String, label: String, marked: Bool) -> some View {
        HStack(spacing: 16) {
            // A symbol is drawn larger than an icon-font glyph of the same size.
            Image(systemName: icon)
                .font(.system(size: 16))
                .frame(width: 20, height: 20)
                .accessibilityHidden(true)
            Text(label)
                .font(.system(size: 16))
                .lineLimit(1)
        }
        .foregroundStyle(.white)
        .padding(.leading, 14)
        .frame(width: 216, height: M.row, alignment: .leading)
        .background {
            Capsule()
                .fill(Color.white.opacity(0.12))
                .padding(.vertical, 2)
                .opacity(marked ? 1 : 0)
                .animation(.easeInOut(duration: M.markIn), value: marked)
        }
        .contentShape(Rectangle())
        .padding(.leading, 12)
    }

    private func reveal(_ order: Int) -> DepthSideDrawerReveal {
        DepthSideDrawerReveal(shown: rowsShown, order: order, still: reduceMotion)
    }

    // MARK: Moving

    private func slide(_ to: Double) {
        let animation = reduceMotion ? Animation.timingCurve(0.215, 0.61, 0.355, 1, duration: M.slide) : to == 1 ? M.openSpring : M.closeSpring
        withAnimation(animation) { progress = to }
    }

    private func setDrawer(_ to: Bool) {
        guard to != open else { return }
        open = to
        rowsShown = to
        slide(to ? 1 : 0)
    }

    private func go(_ index: Int) {
        guard items.indices.contains(index) else { return }
        destination = index
        onSelect?(items[index])
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        chosen = false
        touches += 1
    }

    private func press(_ to: Bool) {
        touched()
        haptic.selectionChanged()
        setDrawer(to)
    }

    private func choose(_ index: Int) {
        touched()
        haptic.selectionChanged()
        go(index)
        chosen = true
    }

    private func dragging(openX: Double) -> some Gesture {
        DragGesture(minimumDistance: 12, coordinateSpace: .global)
            .onChanged { value in
                if case .idle = drag {
                    // Closed, the page is dragged from its left edge; open, by the card.
                    let start = value.startLocation.x
                    let within = progress < 0.5 ? start < M.edge : start > openX - M.ghosts[1].back
                    if within, abs(value.translation.width) > abs(value.translation.height) {
                        drag = .moving(from: progress)
                        touched()
                        if !open { rowsShown = true }
                    } else {
                        drag = .ignored
                    }
                }
                guard case .moving(let from) = drag else { return }
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    progress = min(1, max(0, from + value.translation.width / openX))
                }
            }
            .onEnded { value in
                defer { drag = .idle }
                guard case .moving = drag else { return }
                let speed = value.velocity.width
                let to = speed > M.fling ? true : speed < -M.fling ? false : progress > 0.5
                if to != open { haptic.selectionChanged() }
                open = to
                rowsShown = to
                slide(to ? 1 : 0)
            }
    }

    // MARK: The loop

    private func closeAfterChoice() async {
        guard chosen else { return }
        try? await Task.sleep(for: .seconds(M.choiceHold))
        guard !Task.isCancelled else { return }
        chosen = false
        setDrawer(false)
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // Back to rest; the drawer has closed by the time the loop first opens it.
            setDrawer(false)
            destination = 0
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            // Each step sets the state that is due at its time, whatever the steps before it did.
            for (at, step) in M.script {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                switch step {
                case .drawer(let to): setDrawer(to)
                case .choose(let index): go(index)
                }
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct ChoiceKey: Equatable {
        let chosen: Bool
        let touches: Int
    }
}

/// One line of the menu: it waits its turn, then slides in from the left while it fades in. The
/// lines leave together.
private struct DepthSideDrawerReveal: ViewModifier {
    var shown: Bool
    var order: Int
    var still: Bool

    private typealias M = DepthSideDrawerMotion

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(x: shown || still ? 0 : -M.rowShift)
            .animation(animation, value: shown)
    }

    private var animation: Animation {
        if !shown || still { return .easeInOut(duration: M.rowsOut) }
        return .timingCurve(0.215, 0.61, 0.355, 1, duration: M.rowIn).delay(M.rowDelay + Double(order) * M.rowGap)
    }
}

/// The page on its way aside: its corners round, its shadow grows, and it is scaled and turned.
private struct DepthSideDrawerSwing: ViewModifier, Animatable {
    var progress: Double
    var size: CGSize
    var openX: Double
    var still: Bool
    /// Called when the page passes the half-way point, with whether it is now beyond it.
    var onHalf: (Bool) -> Void

    private typealias M = DepthSideDrawerMotion

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let p = progress
        let settled = min(1, max(0, p))
        let shape = RoundedRectangle(cornerRadius: M.radius * settled, style: .continuous)
        content
            .clipShape(shape)
            .background {
                shape
                    .fill(Color(hex: 0xFAFCFE))
                    .shadow(color: Color(hex: 0x1E0F4D, opacity: 0.35 * settled), radius: 20, x: -12, y: 16)
            }
            .projectionEffect(
                still
                    ? ProjectionTransform(CGAffineTransform(translationX: openX * p, y: 0))
                    : M.projection(size: size, scale: 1 - (1 - M.scale) * p, tilt: M.tilt * p, x: openX * p, y: M.lift * p)
            )
            .onChange(of: p > 0.5) { _, over in onHalf(over) }
    }
}

/// One of the faint cards behind the page. It follows a power of the page's progress, so it trails.
private struct DepthSideDrawerGhost: ViewModifier, Animatable {
    var progress: Double
    var ghost: DepthSideDrawerMotion.Ghost
    var size: CGSize
    var openX: Double
    var still: Bool

    private typealias M = DepthSideDrawerMotion

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let g = pow(max(0, progress), ghost.trail)
        content
            // Nothing of a ghost shows behind the closed page, so it is not painted there.
            .opacity(still ? 0 : ghost.alpha * min(1, max(0, progress * 5)))
            .projectionEffect(
                M.projection(size: size, scale: 1 - (1 - ghost.scale) * g, tilt: M.tilt * g, x: (openX - ghost.back) * g, y: M.lift * g)
            )
    }
}

#Preview {
    DepthSideDrawer(autoPlay: true)
}
