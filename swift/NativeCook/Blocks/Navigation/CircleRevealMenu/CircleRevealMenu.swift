import SwiftUI

struct CircleRevealMenuSection {
    /// The item of the menu.
    var title: String
    /// The two lines at the top of its page.
    var headline: String
    var line: String
    /// The captions under the photos: the current project and the next.
    var caption: String
    var nextCaption: String

    static let samples = [
        CircleRevealMenuSection(title: "Works", headline: "Smart & clean design", line: "That\u{2019}s Norde.studio", caption: "Architects concept", nextCaption: "Weekend Sprint"),
        CircleRevealMenuSection(title: "Process", headline: "How we work", line: "Four steps, no surprises", caption: "Discover and define", nextCaption: "Design and build"),
        CircleRevealMenuSection(title: "About", headline: "A small studio", line: "Six people in Tallinn", caption: "The team", nextCaption: "The workshop"),
        CircleRevealMenuSection(title: "Contact", headline: "Say hello", line: "hello@norde.studio", caption: "New projects", nextCaption: "Open roles"),
    ]

    /// Black and white, as the page asks for, and twice the size of the widest place in the row.
    private static func photo(_ id: String) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=374&h=320&q=80&sat=-100"
    }

    static let photos = [photo("photo-1496442226666-8d4d0e62e6e9"), photo("photo-1513635269975-59663e0ac1ad"), photo("photo-1540959733332-eab4deabeeaf")]
}

/// A studio page with a menu button at its top right. The menu is a dark green sheet that grows
/// out of the button as a circle until it covers the screen, while the button's three lines turn
/// into a cross; the items then rise in one after another. Choosing one closes the menu onto that
/// section's page.
struct CircleRevealMenu: View {
    var sections = CircleRevealMenuSection.samples
    /// Three photos for the row on the page; each section starts the row with another one.
    var photos = CircleRevealMenuSection.photos
    /// The two actions at the bottom of the menu.
    var actions = ["Call", "Mail"]
    var menuColor = Color(hex: 0x124A3B)
    /// Opens the menu, picks an item and closes it by itself every `Motion.loop` seconds.
    var autoPlay = false
    var onSelect: ((Int) -> Void)? = nil
    var onAction: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var open = false
    /// The chosen section and the one before it.
    @State private var current = 0
    @State private var previous = 0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// The autoPlay loop makes no haptics; a touch does.
    @State private var byHand = false

    /// How much of the way the circle has grown, and how far the list of items has come in.
    @State private var circle = Glide(0)
    @State private var list = Glide(0)
    /// How far the items' colours have changed over to the chosen section.
    @State private var swap = Glide(1)
    @State private var closing: Task<Void, Never>? = nil
    /// The timeline runs only while something moves.
    @State private var moving = false
    @State private var restTask: Task<Void, Never>? = nil
    @State private var impact = UIImpactFeedbackGenerator(style: .light)

    /// A number that stays where it is for a while and then goes to a target along a curve, read
    /// against the timeline's clock.
    private struct Glide {
        private var from: Double
        private var to: Double
        private var start = Date.distantPast
        private var duration = 0.0
        private var curve = UnitCurve.linear

        init(_ value: Double) {
            from = value
            to = value
        }

        func value(at date: Date) -> Double {
            let t = date.timeIntervalSince(start)
            guard t > 0 else { return from }
            return t < duration ? from + (to - from) * curve.value(at: t / duration) : to
        }

        mutating func go(to target: Double, at date: Date = .now, after delay: Double = 0, duration: Double, curve: UnitCurve = .linear) {
            from = value(at: date)
            to = target
            start = date + delay
            self.duration = duration
            self.curve = curve
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            // The whole screen, in its own coordinates: the circle's centre is the button's.
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let top = insets.top + Metrics.barTop
            // Where something hangs under the bar's top: closer on a screen shorter than the design's.
            let near = min(1, (size.height - top) / Metrics.design)
            let button = CGPoint(x: size.width - Metrics.buttonInset, y: top + Metrics.bar / 2)
            // The circle grows from the button's centre until it has the farthest corner.
            let reach = hypot(max(button.x, size.width - button.x), max(button.y, size.height - button.y))
            let logoAway = button.x - (Metrics.margin + Metrics.logo / 2)
            let stripInset = max(0, insets.bottom - Metrics.stripInset)

            TimelineView(.animation(paused: !moving)) { timeline in
                let grown = circle.value(at: timeline.date)
                let listed = list.value(at: timeline.date)
                let swapped = swap.value(at: timeline.date)
                let radius = reach * grown
                // The button's lines and the logo turn white when the circle's edge has passed them.
                let overButton = reduceMotion ? grown : min(1, radius / (Metrics.lineWidth / 2 + 5))
                let overLogo = reduceMotion ? grown : min(1, max(0, (radius - (logoAway - Metrics.edge / 2)) / Metrics.edge))

                ZStack(alignment: .topLeading) {
                    Palette.paper

                    page(top: top, near: near, size: size)
                        .scaleEffect(reduceMotion ? 1 : 1 - Metrics.pageShrink * grown)
                        .offset(y: reduceMotion ? 0 : Metrics.pageSink * grown)
                        .accessibilityHidden(open)

                    // Without movement the sheet fades in whole.
                    Circle()
                        .fill(menuColor)
                        .frame(width: reach * 2, height: reach * 2)
                        .scaleEffect(reduceMotion ? 1 : grown)
                        .opacity(reduceMotion ? grown : 1)
                        .offset(x: button.x - reach, y: button.y - reach)
                        .allowsHitTesting(false)

                    menu(listed: listed, swapped: swapped, top: top + Metrics.itemsTop * near, size: size, stripInset: stripInset)
                        .allowsHitTesting(open)
                        .accessibilityHidden(!open)

                    logo(color: Palette.mix(Palette.inkRGB, Palette.whiteRGB, overLogo))
                        .offset(x: Metrics.margin, y: top + (Metrics.bar - Metrics.logo) / 2)
                    menuButton(color: Palette.mix(Palette.inkRGB, Palette.whiteRGB, overButton), grown: grown)
                        .offset(x: button.x - Metrics.bar / 2, y: top)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .clipped()
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(open ? .dark : .light)
        .onAppear { impact.prepare() }
        .onDisappear {
            closing?.cancel()
            restTask?.cancel()
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The page

    private func page(top: Double, near: Double, size: CGSize) -> some View {
        let section = sections[min(current, sections.count - 1)]
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Text(section.headline).fontWeight(.bold).frame(height: 36)
                Text(section.line).frame(height: 36)
            }
            .font(.system(size: 30))
            .foregroundStyle(Palette.ink)
            .lineLimit(1)
            .offset(x: Metrics.margin, y: top + Metrics.headlineTop * near)

            // The row runs off the right edge.
            HStack(spacing: Metrics.photoGap) {
                ForEach(Metrics.photoWidths.indices, id: \.self) { i in
                    ZStack {
                        Color(hex: 0xE4E2DE)
                        AsyncImage(url: URL(string: photos[(i + current) % photos.count]), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                            if let image = phase.image {
                                image.resizable().scaledToFill().transition(.opacity)
                            }
                        }
                    }
                    .frame(width: Metrics.photoWidths[i], height: Metrics.photoHeight)
                    .clipped()
                }
            }
            .fixedSize()
            .offset(y: top + Metrics.photosTop * near)
            .accessibilityHidden(true)

            HStack(alignment: .top) {
                VStack(spacing: 6) {
                    Text(section.caption).foregroundStyle(Palette.ink).frame(height: 19)
                    Circle().fill(Palette.ink).frame(width: 4, height: 4)
                }
                Spacer(minLength: 8)
                Text(section.nextCaption).foregroundStyle(Palette.grey).frame(height: 19)
            }
            .font(.system(size: 15))
            .lineLimit(1)
            .frame(width: size.width - Metrics.margin * 2)
            .offset(x: Metrics.margin, y: top + Metrics.captionsTop * near)

            // The top of the next project.
            HStack(spacing: 8) {
                Palette.tile.frame(width: 72, height: 56)
                Palette.tileFar.frame(width: 60, height: 56)
            }
            .padding(.leading, 28)
            .padding(.top, 36)
            .frame(width: size.width - Metrics.margin * 2, height: max(0, size.height - top - Metrics.slabTop * near), alignment: .topLeading)
            .background(Palette.slab)
            .offset(x: Metrics.margin, y: top + Metrics.slabTop * near)
            .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    // MARK: The menu

    private func menu(listed: Double, swapped: Double, top: Double, size: CGSize, stripInset: Double) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(sections.indices, id: \.self) { i in
                    let shown = M.easeOut((listed * M.listIn - Double(i) * M.stagger) / M.itemIn)
                    // The chosen item turns white as the one before it turns muted.
                    let white = i == current ? swapped : i == previous ? 1 - swapped : 0
                    Button {
                        touched()
                        impact.impactOccurred()
                        choose(i)
                    } label: {
                        Text(sections[i].title)
                            .font(.system(size: 50))
                            .foregroundStyle(Palette.mix(Palette.mutedRGB, Palette.whiteRGB, white))
                            .lineLimit(1)
                            .padding(.leading, Metrics.itemLeft)
                            .frame(width: size.width, height: Metrics.itemPitch, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(i == current ? .isSelected : [])
                    .opacity(shown)
                    .offset(y: reduceMotion ? 0 : (1 - shown) * Metrics.rise)
                }
            }
            .offset(y: top)

            HStack(spacing: 0) {
                ForEach(actions.indices, id: \.self) { i in
                    Button {
                        touched()
                        impact.impactOccurred()
                        onAction?(i)
                    } label: {
                        Text(actions[i])
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .overlay(alignment: .leading) {
                        if i > 0 { Palette.hairline.frame(width: 1) }
                    }
                }
            }
            .padding(.bottom, stripInset)
            .frame(width: size.width, height: Metrics.strip + stripInset)
            .overlay(alignment: .top) { Palette.hairline.frame(height: 1) }
            .opacity(M.easeOut((listed * M.listIn - M.stripAfter) / (M.listIn - M.stripAfter)))
            .offset(y: size.height - Metrics.strip - stripInset)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    /// Two slanted strokes side by side.
    private func logo(color: Color) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach([4.0, 14.5], id: \.self) { x in
                Rectangle()
                    .fill(color)
                    .frame(width: 6, height: Metrics.logo)
                    .rotationEffect(.degrees(20))
                    .offset(x: x)
            }
        }
        .frame(width: Metrics.logo, height: Metrics.logo, alignment: .topLeading)
        .accessibilityElement()
        .accessibilityLabel("Norde.studio")
    }

    /// The three lines become a cross: the outer two turn and meet in the middle, the middle one goes.
    private func menuButton(color: Color, grown: Double) -> some View {
        let cross = reduceMotion ? (grown > 0.5 ? 1 : 0) : min(1, grown * 2.5)
        let apart = (Metrics.lineGap + Metrics.line) * (1 - cross)
        return Button {
            touched()
            impact.impactOccurred()
            if open { hide() } else { show() }
        } label: {
            ZStack {
                Capsule().frame(width: Metrics.lineWidth, height: Metrics.line).rotationEffect(.degrees(45 * cross)).offset(y: -apart)
                Capsule().frame(width: Metrics.lineWidth, height: Metrics.line).opacity(1 - cross)
                Capsule().frame(width: Metrics.lineWidth, height: Metrics.line).rotationEffect(.degrees(-45 * cross)).offset(y: apart)
            }
            .foregroundStyle(color)
            .frame(width: Metrics.bar, height: Metrics.bar)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(open ? "Close menu" : "Menu")
    }

    // MARK: Behaviour

    private func show() {
        closing?.cancel()
        open = true
        let now = Date.now
        circle.go(to: 1, at: now, duration: M.circleIn, curve: M.easeInOut)
        list.go(to: 1, at: now, after: M.listAfter, duration: M.listIn)
        wake(restAfter: M.listAfter + M.listIn + 0.3)
    }

    private func hide() {
        closing?.cancel()
        open = false
        let now = Date.now
        list.go(to: 0, at: now, duration: M.listOut)
        circle.go(to: 0, at: now, after: M.circleAfter, duration: M.circleOut, curve: M.easeInOut)
        wake(restAfter: M.circleAfter + M.circleOut + 0.3)
    }

    /// An item of the menu: it turns white, and the menu closes onto its page a moment later.
    private func choose(_ index: Int) {
        if index != current {
            previous = current
            current = index
            swap = Glide(0)
            swap.go(to: 1, duration: M.swap)
            onSelect?(index)
        }
        wake()
        closing?.cancel()
        closing = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.chosenHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            hide()
        }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        byHand = true
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

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // The loop starts over on the first section with the menu closed.
            byHand = false
            if open { hide() }
            if current != 0 {
                previous = current
                current = 0
            }
            // The menu has to be gone before the loop presses the button again.
            try? await Task.sleep(for: .seconds(M.listOut + M.circleOut))
            guard !Task.isCancelled else { return }
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            // The loop presses what a finger presses, through the same functions.
            for step in M.script {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                if let index = step.choose {
                    choose(index)
                } else if open {
                    hide()
                } else {
                    show()
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
}

private typealias M = CircleRevealMenuMotion

enum CircleRevealMenuMotion {
    static let loop = 9.0
    /// Opening: the circle, and then the items one after another, the strip last.
    static let circleIn = 0.55
    static let listAfter = 0.35
    static let listIn = 0.55
    static let stagger = 0.07
    static let itemIn = 0.3
    static let stripAfter = 0.33
    /// Closing: the items in reverse, and then the circle.
    static let listOut = 0.4
    static let circleAfter = 0.25
    static let circleOut = 0.45
    /// A chosen item turns white, and the menu closes a moment later.
    static let swap = 0.2
    static let chosenHold = 0.4
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    /// The loop starts again this long after the last touch.
    static let userHold = 3.0
    /// What the autoPlay loop presses: the button, and an item of the open menu.
    static let script: [(at: Double, choose: Int?)] = [(0.8, nil), (3.0, 1), (5.6, nil), (7.4, 0)]

    static func easeOut(_ t: Double) -> Double {
        let u = 1 - min(1, max(0, t))
        return 1 - u * u * u
    }
}

private enum Palette {
    static let paper = Color.white
    static let ink = Color(hex: 0x111111)
    static let grey = Color(hex: 0x9A9A9A)
    static let slab = Color(hex: 0xB9B4AE)
    static let tile = Color(hex: 0x1F1E1C)
    static let tileFar = Color(hex: 0x3A3835)
    static let hairline = Color.white.opacity(0.1)
    static let inkRGB = (17.0, 17.0, 17.0)
    static let whiteRGB = (255.0, 255.0, 255.0)
    static let mutedRGB = (94.0, 140.0, 124.0)

    /// A colour part of the way from one to another.
    static func mix(_ a: (Double, Double, Double), _ b: (Double, Double, Double), _ t: Double) -> Color {
        Color.rgba(a.0 + (b.0 - a.0) * t, a.1 + (b.1 - a.1) * t, a.2 + (b.2 - a.2) * t, 1)
    }
}

private enum Metrics {
    static let margin = 24.0
    /// The top bar under the status bar, and the menu button's lines in it.
    static let barTop = 8.0
    static let bar = 44.0
    static let lineWidth = 18.0
    static let line = 2.0
    static let lineGap = 4.5
    /// The button's centre from the right edge.
    static let buttonInset = 33.0
    static let logo = 24.0
    /// Everything hangs from the bar's top, at these distances on a screen with `design` points
    /// under the bar; a shorter screen draws them closer together.
    static let design = 804.0
    static let headlineTop = 117.0
    static let photosTop = 271.0
    static let photoHeight = 160.0
    static let photoWidths = [96.0, 110.0, 187.0]
    static let photoGap = 8.0
    static let captionsTop = 603.0
    static let slabTop = 690.0
    static let itemsTop = 190.0
    static let itemPitch = 84.0
    static let itemLeft = 30.0
    static let strip = 64.0
    /// The least bottom inset the strip is drawn for.
    static let stripInset = 34.0
    /// The page under the circle is this much smaller and lower.
    static let pageShrink = 0.04
    static let pageSink = 12.0
    static let rise = 16.0
    /// The logo turns white while this much of the circle's edge passes over it.
    static let edge = 24.0
}

#Preview {
    CircleRevealMenu(autoPlay: true)
}
