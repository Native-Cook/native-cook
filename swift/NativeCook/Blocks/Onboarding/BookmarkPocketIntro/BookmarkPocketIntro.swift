import SwiftUI

struct BookmarkPocketIntroCard: Equatable {
    /// A picture's address, and the colour shown until it has loaded.
    var photo: String
    var tint: Color
    /// The card is 140 points wide and this tall.
    var height: Double
    /// Up to three short lines over the picture; the middle one is the loud one.
    var overlay: [String] = []
    var badge: String? = nil
    /// With a headline the card is an article: the picture on top, the words on black below.
    var headline: String? = nil

    private static let photo = "?auto=format&fit=crop&w=420&h=600&q=80"

    static let samples = [
        BookmarkPocketIntroCard(photo: "https://images.unsplash.com/photo-1516483638261-f4dbaf036963\(photo)", tint: Color(hex: 0xb9a48c), height: 170),
        BookmarkPocketIntroCard(
            photo: "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad\(photo)", tint: Color(hex: 0x7d8794), height: 186,
            overlay: ["Best value", "RESTAURANTS", "in London"], badge: "Part 16"
        ),
        BookmarkPocketIntroCard(
            photo: "https://images.unsplash.com/photo-1502602898657-3e91760cbb34\(photo)", tint: Color(hex: 0x6f6a86), height: 220,
            headline: "53 unmissable\nthings to do in\nParis right now"
        ),
    ]
}

/// A welcome screen. Saved posts fall into a big bookmark, which gulps each one; a tap on it turns
/// it into the hub of two rings of the places things are saved from, and the tagline is typed
/// again for each picture. By hand the card at the top edge is dragged into the bookmark, and a
/// finger turns the rings.
struct BookmarkPocketIntro: View {
    var name = "Stash"
    /// What the tagline says under the big bookmark, and among the orbits.
    var taglines = ["A new home for your internet hoarding tendencies", "A new way to explore a new city"]
    var buttonLabel = "Get Started"
    /// The saved posts that fall into the bookmark, in turn.
    var cards = BookmarkPocketIntroCard.samples
    var backgroundColor = Color(hex: 0xf6f3f7)
    var inkColor = Color(hex: 0x1c1a1d)
    var accentColor = Color(hex: 0xe5484d)
    /// Feeds three cards, opens the orbits and comes back, every `BookmarkPocketIntroMotion.loop` seconds.
    var autoPlay = false
    /// Called with the card's place in `cards` when it is let go into the bookmark.
    var onFeed: ((Int) -> Void)? = nil
    /// Called when the bookmark is tapped: true for the orbits, false for the pocket.
    var onModeChange: ((Bool) -> Void)? = nil
    var onStart: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The clock's zero: every time below is in seconds since then.
    @State private var origin = Date.now
    @State private var mode = M.Mode(orbit: false, at: -100, spun: 0)
    @State private var drops: [M.Drop] = []
    /// By itself the screen has no card waiting; one comes once a finger has touched it.
    @State private var wait = M.Wait(on: false, at: -100)
    @State private var hold = M.Hold()
    @State private var next = 0
    /// How many cards the pocket has had since it was last at rest: the glow grows with it.
    @State private var fed = 0
    /// What a finger has added to the rings' turning, in degrees.
    @State private var turn = 0.0
    /// What the touch has hold of, and the last angle of the finger about the hub.
    @State private var grip = Grip.nothing
    @State private var angle = 0.0
    /// A touch is on the screen, and it has moved too far to be a tap.
    @State private var tracking = false
    @State private var moved = false
    /// At rest the tagline is empty when the screen plays by itself, and types itself otherwise.
    @State private var tagline = M.Tagline(from: "", to: "", at: -100)
    /// On how many lines the tagline is, and since when.
    @State private var lines = 1
    @State private var linesAt = -10.0
    /// The stage's y at the top of the screen.
    @State private var topEdge = 0.0
    /// When nothing moves any more, and whether that moment has passed: the timeline stops then.
    @State private var settled = 0.0
    @State private var resting = false
    @State private var restTask: Task<Void, Never>? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private enum Grip {
        case nothing, card, rings
    }

    private enum How {
        case tap, auto, rest
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let scale = screen.width / M.stageWidth
            // The stage's middle is on the screen's middle, so on most screens its top is a little off the edge.
            let offset = (screen.height - M.stageHeight * scale) / 2
            let top = -offset / scale
            let bottom = max(insets.bottom + 23, 28)

            TimelineView(.animation(paused: resting)) { timeline in
                let t = resting ? settled : min(timeline.date.timeIntervalSince(origin), settled)

                ZStack(alignment: .topLeading) {
                    backgroundColor

                    Color.clear.overlay(alignment: .topLeading) {
                        stage(t, top: top)
                            .frame(width: M.stageWidth, height: M.stageHeight, alignment: .topLeading)
                            .scaleEffect(scale, anchor: .topLeading)
                            .offset(y: offset)
                    }
                    .accessibilityHidden(true)

                    bookmark(t, scale: scale, offset: offset)

                    VStack(spacing: Metrics.buttonGap) {
                        Spacer(minLength: 0)
                        words(t)
                        button
                    }
                    .padding(.horizontal, Metrics.margin)
                    .padding(.bottom, bottom)
                }
                .frame(width: screen.width, height: screen.height, alignment: .topLeading)
                .clipped()
            }
            .contentShape(Rectangle())
            .gesture(touch(scale: scale, offset: offset))
            .ignoresSafeArea()
            .onChange(of: top, initial: true) { topEdge = top }
        }
        .preferredColorScheme(.light)
        .onAppear {
            // The clock starts with the loop, so the first pass is timed like every other.
            origin = .now
            drops = cards.map { _ in M.Drop() }
            if !autoPlay {
                wait = M.Wait(on: true, at: 0)
                tagline = M.Tagline(from: "", to: taglines[0], at: M.typeAfter)
            }
            settle()
        }
        .onDisappear { restTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The stage

    /// Rings, cards and icons, in the stage's own points; the bookmark is drawn over them.
    private func stage(_ t: Double, top: Double) -> some View {
        let open = M.ringsAt(mode, t, still: reduceMotion)
        return ZStack(alignment: .topLeading) {
            ForEach(M.rings.indices, id: \.self) { index in
                Circle()
                    .stroke(Palette.ring, lineWidth: 1)
                    .frame(width: M.rings[index] * 2, height: M.rings[index] * 2)
                    .scaleEffect(reduceMotion ? 1 : open)
                    .opacity(min(1, open * 3))
                    .position(x: M.centerX, y: M.orbitY)
            }

            // Cards are cut off well behind the bookmark, so none shows through its notch.
            ZStack(alignment: .topLeading) {
                ForEach(cards.indices, id: \.self) { index in
                    let card = cards[index]
                    let drop = index < drops.count ? drops[index] : M.Drop()
                    let pose = M.cardPose(height: card.height, waiting: next == index, wait, hold, drop, top: top, t, still: reduceMotion)
                    BookmarkPocketIntroFace(card: card)
                        .overlay(Color.black.opacity(pose.dark))
                        .frame(width: Metrics.cardWidth, height: card.height)
                        .clipShape(RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
                        // Everything a card does is measured from its lower edge.
                        .scaleEffect(pose.scale, anchor: .bottom)
                        .opacity(pose.opacity)
                        .position(x: M.centerX + pose.x, y: pose.bottom - top - card.height / 2)
                }
            }
            .frame(width: M.stageWidth, height: Metrics.clip - top, alignment: .topLeading)
            .clipped()
            .offset(y: top)

            ForEach(Metrics.icons.indices, id: \.self) { index in
                let icon = Metrics.icons[index]
                let pose = M.iconPose(index, ring: icon.ring, angle: icon.angle, mode, t, turn: turn, still: reduceMotion)
                Image(systemName: icon.symbol)
                    .font(.system(size: icon.symbolSize, weight: .semibold))
                    .foregroundStyle(icon.ink)
                    .frame(width: icon.size, height: icon.size)
                    .background(
                        RoundedRectangle(cornerRadius: icon.radius, style: icon.radius * 2 == icon.size ? .circular : .continuous)
                            .fill(LinearGradient(colors: icon.colors, startPoint: UnitPoint(x: 0.2, y: 0), endPoint: UnitPoint(x: 0.8, y: 1)))
                    )
                    .scaleEffect(pose.scale)
                    .opacity(pose.opacity)
                    .position(x: M.centerX + pose.x, y: M.orbitY + pose.y)
            }
        }
    }

    private func bookmark(_ t: Double, scale: Double, offset: Double) -> some View {
        let pose = M.bookmarkPose(mode, t, still: reduceMotion)
        let gulp = M.gulpAt(drops, t, still: reduceMotion)
        return Canvas { context, _ in
            var book = context
            book.translateBy(x: 0, y: offset)
            book.scaleBy(x: scale, y: scale)
            let middle = M.bookTop + M.bookHeight / 2
            book.translateBy(x: M.centerX, y: middle + (M.orbitY - middle) * pose.lift)
            book.rotate(by: .degrees(pose.tilt))
            book.scaleBy(x: pose.scale, y: pose.scale)
            book.translateBy(x: 0, y: -M.bookHeight / 2)
            book.opacity = pose.opacity
            let outline = Metrics.bookmark(bulge: gulp.bulge)

            // Colour leaks from under the bookmark when it has had its fill.
            if gulp.glow > 0 {
                var glow = book
                glow.opacity = pose.opacity * 0.6 * gulp.glow
                glow.addFilter(.blur(radius: 30))
                glow.fill(Path(ellipseIn: CGRect(x: -M.bookWidth / 2 - 14, y: M.bookHeight - 70, width: 110, height: 80)), with: .color(Palette.glows[0]))
                glow.fill(Path(ellipseIn: CGRect(x: M.bookWidth / 2 - 96, y: M.bookHeight - 70, width: 110, height: 80)), with: .color(Palette.glows[1]))
            }
            var shadow = book
            shadow.addFilter(.blur(radius: 12))
            shadow.translateBy(x: 0, y: 10)
            shadow.fill(outline, with: .color(.black.opacity(0.18)))
            book.fill(outline, with: .linearGradient(Gradient(colors: Palette.bookShade), startPoint: .zero, endPoint: CGPoint(x: 0, y: M.bookHeight)))
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: Words and button

    private func words(_ t: Double) -> some View {
        let typed = M.typedAt(tagline, t, still: reduceMotion)
        let typedBy = M.typedBy(tagline)
        // How far the words have got since the tagline last changed its number of lines.
        let moved = reduceMotion ? 1 : M.easeOut((t - linesAt) / M.lineShift)
        return VStack(alignment: .leading, spacing: Metrics.textGap) {
            Text("\(name)\(Text(verbatim: ".").foregroundStyle(accentColor))")
                .font(.system(size: 26, weight: .bold))
                .tracking(-0.4)
                .foregroundStyle(inkColor)
                .lineLimit(1)
                .frame(height: Metrics.nameLine)
            // The cursor is steady while the tagline changes and blinks from the last keystroke on.
            TimelineView(.periodic(from: origin.addingTimeInterval(typedBy), by: M.blink)) { beat in
                let since = beat.date.timeIntervalSince(origin) - typedBy
                let on = reduceMotion || since < 0 || Int(since / M.blink) % 2 == 0
                Text("\(typed)\(Text(verbatim: "_").foregroundStyle(on ? Palette.tagline : .clear))")
                    .font(.system(size: 20))
                    .tracking(-0.2)
                    .lineSpacing(Metrics.tagLine - UIFont.systemFont(ofSize: 20).lineHeight)
                    .foregroundStyle(Palette.tagline)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .onGeometryChange(for: Int.self) { $0.size.height > Metrics.tagLine * 1.5 ? 2 : 1 } action: { count in
                        guard count != lines else { return }
                        lines = count
                        linesAt = now
                    }
                    .accessibilityLabel(typed)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // The name sits one line lower while the tagline has one line.
        .offset(y: Metrics.tagLine * (lines > 1 ? 1 - moved : moved))
        .frame(height: Metrics.nameLine + Metrics.textGap + Metrics.tagLine * 2, alignment: .top)
        // The second line of the tagline comes up from under this edge.
        .clipped()
        .allowsHitTesting(false)
    }

    private var button: some View {
        Button {
            touches += 1
            tapHaptic.impactOccurred()
            onStart?()
        } label: {
            Text(buttonLabel)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: Metrics.buttonHeight)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.button))
        }
        .buttonStyle(PressStyle(still: reduceMotion))
    }

    // MARK: Behaviour

    /// Seconds on the clock right now.
    private var now: Double { Date.now.timeIntervalSince(origin) }

    /// One touch is a tap on the bookmark, a pull on the waiting card, or a finger turning the rings.
    private func touch(scale: Double, offset: Double) -> some Gesture {
        /// A point of the screen in the stage's own points.
        func onStage(_ point: CGPoint) -> CGPoint {
            CGPoint(x: point.x / scale, y: (point.y - offset) / scale)
        }
        return DragGesture(minimumDistance: 0)
            .onChanged { value in
                if !tracking {
                    tracking = true
                    touched()
                }
                if !moved, hypot(value.translation.width, value.translation.height) > Metrics.slop {
                    moved = true
                    let point = onStage(value.startLocation)
                    let t = now
                    if mode.orbit {
                        // Anywhere above the words turns the rings, once they are open.
                        if t >= mode.at + M.orbitIn, point.y <= M.bookTop + M.bookHeight + 40 {
                            grip = .rings
                            angle = atan2(point.y - M.orbitY, point.x - M.centerX)
                        }
                    } else if wait.on, t >= wait.at + M.waitIn, abs(point.x - M.centerX) <= Metrics.cardWidth / 2 + 12, point.y <= topEdge + M.peek + 12 {
                        grip = .card
                    }
                }
                if grip == .card {
                    hold = M.Hold(dx: value.translation.width / scale, dy: max(-M.peek / 2, value.translation.height / scale), at: .infinity)
                } else if grip == .rings {
                    let point = onStage(value.location)
                    let here = atan2(point.y - M.orbitY, point.x - M.centerX)
                    // The short way round from the last angle to this one.
                    let step = atan2(sin(here - angle), cos(here - angle))
                    angle = here
                    turn += step * 180 / .pi
                }
            }
            .onEnded { value in
                if !moved {
                    tapped(onStage(value.location))
                } else if grip == .card {
                    let t = now
                    if hold.dy > M.feedAfter {
                        let card = next
                        let held = hold
                        next = (card + 1) % cards.count
                        hold = M.Hold()
                        wait = M.Wait(on: true, at: t + M.waitAfter)
                        feed(card, bottom: topEdge + M.peek + held.dy, dx: held.dx, byHand: true)
                    } else {
                        hold.at = t
                        settle()
                    }
                }
                tracking = false
                moved = false
                grip = .nothing
            }
    }

    private func tapped(_ point: CGPoint) {
        let hit = mode.orbit
            ? hypot(point.x - M.centerX, point.y - M.orbitY) <= 56
            : abs(point.x - M.centerX) <= M.bookWidth / 2 && point.y >= M.bookTop && point.y <= M.bookTop + M.bookHeight
        if hit { show(orbit: !mode.orbit, .tap) }
    }

    /// A finger is on the screen: the card it can feed comes to the top edge.
    private func touched() {
        touches += 1
        guard !wait.on, !mode.orbit else { return }
        // The card that fell longest ago is the one to wait.
        next = drops.indices.min { drops[$0].at < drops[$1].at } ?? 0
        wait = M.Wait(on: true, at: now)
        settle()
    }

    /// Changes what the tagline is going to say, from `at` on.
    private func retag(_ to: String, at: Double) {
        tagline = M.Tagline(from: M.typedAt(tagline, at, still: reduceMotion), to: to, at: at)
    }

    /// Lets a card go with its lower edge at `bottom`, `dx` aside of the middle.
    private func feed(_ card: Int, bottom: Double, dx: Double, byHand: Bool) {
        guard card < drops.count else { return }
        let t = now
        fed += 1
        drops[card] = M.Drop(at: t, bottom: bottom, dx: dx, glow: pow(min(1, Double(fed) / 3), 2))
        if tagline.to.isEmpty { retag(taglines[0], at: t + M.typeAfter) }
        settle()
        if byHand { tapHaptic.impactOccurred() }
        onFeed?(card)
    }

    /// Goes to the orbits or back to the pocket; `.rest` leaves the tagline to `rest()`.
    private func show(orbit: Bool, _ how: How) {
        let t = now
        // One change at a time: a tap during a change is ignored.
        guard mode.orbit != orbit, t >= mode.at + (mode.orbit ? M.orbitIn : M.pocketIn) else { return }
        mode = M.Mode(orbit: orbit, at: t, spun: mode.orbit ? max(0, t - mode.at - M.shrink - M.lift) : 0)
        fed = 0
        if orbit {
            turn = 0
            wait = M.Wait(on: false, at: t)
            retag(taglines[1], at: t + M.orbitIn)
        } else if how != .rest {
            if how == .tap { wait = M.Wait(on: true, at: t + M.pocketIn - M.waitIn) }
            retag(taglines[0], at: t + M.collapse)
        }
        settle()
        if how == .tap { tapHaptic.impactOccurred() }
        onModeChange?(orbit)
    }

    /// Back to the screen as it first appears.
    private func rest() {
        let t = now
        show(orbit: false, .rest)
        wait = M.Wait(on: false, at: t)
        hold = M.Hold()
        fed = 0
        retag("", at: t)
        settle()
    }

    /// Runs the timeline until nothing moves any more; the rings never stop.
    private func settle() {
        restTask?.cancel()
        resting = false
        let t = now
        var until = max(mode.at + M.orbitIn, M.typedBy(tagline) + M.lineShift, wait.at + M.waitIn, t + M.back)
        for drop in drops { until = max(until, drop.at + 1.5) }
        settled = mode.orbit && !reduceMotion ? .infinity : until
        guard settled.isFinite else { return }
        let pause = settled - t
        restTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(max(0, pause)))
            if !Task.isCancelled { resting = true }
        }
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        var start = clock.now
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // The way back to rest takes what is left of a loop after `autoRest`.
            rest()
            start = clock.now + .seconds(M.loop - M.autoRest)
        }
        while !Task.isCancelled {
            for (card, at) in M.autoFeeds.enumerated() {
                try? await clock.sleep(until: start + .seconds(at))
                guard !Task.isCancelled else { return }
                // By itself a card falls from just above the screen.
                feed(card % cards.count, bottom: topEdge, dx: 0, byHand: false)
            }
            try? await clock.sleep(until: start + .seconds(M.autoOrbit))
            guard !Task.isCancelled else { return }
            show(orbit: true, .auto)
            try? await clock.sleep(until: start + .seconds(M.autoRest))
            guard !Task.isCancelled else { return }
            rest()
            start += .seconds(M.loop)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct PressStyle: ButtonStyle {
        let still: Bool

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.8 : 1)
                .scaleEffect(configuration.isPressed && !still ? 0.98 : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
        }
    }
}

/// What a saved post looks like: a picture, a picture under a few words, or an article.
struct BookmarkPocketIntroFace: View {
    let card: BookmarkPocketIntroCard

    var body: some View {
        if let headline = card.headline {
            VStack(alignment: .leading, spacing: 0) {
                photo.frame(height: 92).clipped().padding(.bottom, 9)
                Group {
                    Text(headline)
                        .font(.system(size: 11, weight: .bold))
                        .lineSpacing(14 - UIFont.systemFont(ofSize: 11, weight: .bold).lineHeight)
                        .foregroundStyle(.white)
                        .padding(.bottom, 8)
                    ForEach([104.0, 112, 72], id: \.self) { width in
                        Capsule().fill(.white.opacity(0.28)).frame(width: width, height: 3).padding(.bottom, 4)
                    }
                    Capsule().stroke(.white.opacity(0.5), lineWidth: 1).frame(width: 61, height: 15).padding(.top, 6.5)
                }
                .padding(.horizontal, 9)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(Color(hex: 0x0b0b0c))
        } else {
            photo
                .overlay {
                    if !card.overlay.isEmpty {
                        VStack(spacing: 0) {
                            ForEach(Array(card.overlay.enumerated()), id: \.offset) { index, line in
                                Text(line)
                                    .font(.system(size: index == 1 ? 12 : 9, weight: index == 1 ? .heavy : .semibold))
                                    .tracking(index == 1 ? 0.6 : 0)
                                    .frame(height: index == 1 ? 16 : 12)
                            }
                        }
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.black.opacity(0.28))
                        .overlay(alignment: .bottom) {
                            if let badge = card.badge {
                                Text(badge)
                                    .font(.system(size: 7, weight: .bold))
                                    .foregroundStyle(Color(hex: 0x1c1a1d))
                                    .padding(.horizontal, 7)
                                    .frame(height: 16)
                                    .background(Capsule().fill(.white))
                                    .padding(.bottom, 10)
                            }
                        }
                    }
                }
        }
    }

    private var photo: some View {
        card.tint
            .overlay {
                AsyncImage(url: URL(string: card.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill().transition(.opacity)
                    }
                }
            }
            .clipped()
    }
}

private typealias M = BookmarkPocketIntroMotion

private enum Palette {
    static let tagline = Color(hex: 0x3a3a3c)
    static let button = Color(hex: 0x050505)
    static let ring = Color(hex: 0xdcd8de)
    static let bookShade = [Color(hex: 0x484649), Color(hex: 0x141113)]
    static let glows = [Color(hex: 0xff5fa2), Color(hex: 0xffd34d)]
}

private enum Metrics {
    // The bookmark's corners: at the top, at its two tips, and in the notch between them.
    static let topRadius = 12.0
    static let tipRadius = 8.0
    static let notch = 35.0
    static let notchRadius = 5.0
    static let cardWidth = 140.0
    static let cardRadius = 10.0
    /// Cards are cut off at this height, well behind the bookmark, so none shows through its notch.
    static let clip = 430.0
    /// A touch that moves less than this is a tap.
    static let slop = 4.0

    static let margin = 16.0
    static let nameLine = 32.0
    static let tagLine = 27.0
    static let textGap = 3.0
    static let buttonHeight = 43.0
    static let buttonGap = 6.0

    /// A place things are saved from: which ring, where on it in degrees, its tile and its symbol.
    /// SF Symbols are drawn larger than icon-font glyphs, hence the smaller sizes.
    struct Icon {
        let ring: Int
        let angle: Double
        let size: Double
        let radius: Double
        let colors: [Color]
        let ink: Color
        let symbol: String
        let symbolSize: Double
    }

    static let icons = [
        Icon(ring: 0, angle: -131, size: 64, radius: 15, colors: [Color(hex: 0xd62f8a), Color(hex: 0xf7a23b)], ink: .white, symbol: "camera", symbolSize: 26),
        Icon(ring: 0, angle: 19, size: 66, radius: 33, colors: [Color(hex: 0x39b5f5), Color(hex: 0x0960ea)], ink: .white, symbol: "globe", symbolSize: 30),
        Icon(ring: 0, angle: 164, size: 44, radius: 22, colors: [Color(hex: 0xfff3c4)], ink: Color(hex: 0xf2b600), symbol: "lightbulb.fill", symbolSize: 18),
        Icon(ring: 0, angle: -39, size: 44, radius: 22, colors: [Color(hex: 0xdcebff)], ink: Color(hex: 0x1877f2), symbol: "location.fill", symbolSize: 17),
        Icon(ring: 1, angle: -54, size: 58, radius: 13, colors: [Color(hex: 0x18181a), Color(hex: 0x050505)], ink: .white, symbol: "music.note", symbolSize: 25),
        Icon(ring: 1, angle: -140, size: 44, radius: 22, colors: [Color(hex: 0xecdffb)], ink: Color(hex: 0x8e44ec), symbol: "film", symbolSize: 17),
        Icon(ring: 1, angle: 145, size: 44, radius: 22, colors: [Color(hex: 0xfde0e0)], ink: Color(hex: 0xf0443f), symbol: "fork.knife", symbolSize: 18),
        Icon(ring: 1, angle: 36, size: 44, radius: 22, colors: [Color(hex: 0xdaf5e0)], ink: Color(hex: 0x22b455), symbol: "figure.run", symbolSize: 19),
    ]

    /// The bookmark with its top edge on y = 0 and its middle on x = 0. `bulge` is how far each
    /// side bows out; the top edge dips in proportion.
    static func bookmark(bulge: Double) -> Path {
        let w = M.bookWidth / 2
        let h = M.bookHeight
        let dip = M.dip * bulge / M.bulge
        // The unit step from a tip towards the notch.
        let run = hypot(w, notch)
        let ux = w / run
        let uy = notch / run
        var path = Path()
        path.move(to: CGPoint(x: -w + topRadius, y: 0))
        path.addQuadCurve(to: CGPoint(x: w - topRadius, y: 0), control: CGPoint(x: 0, y: 2 * dip))
        path.addQuadCurve(to: CGPoint(x: w, y: topRadius), control: CGPoint(x: w, y: 0))
        path.addQuadCurve(to: CGPoint(x: w, y: h - tipRadius), control: CGPoint(x: w + 2 * bulge, y: h / 2))
        path.addQuadCurve(to: CGPoint(x: w - tipRadius * ux, y: h - tipRadius * uy), control: CGPoint(x: w, y: h))
        path.addLine(to: CGPoint(x: notchRadius * ux, y: h - notch + notchRadius * uy))
        path.addQuadCurve(to: CGPoint(x: -notchRadius * ux, y: h - notch + notchRadius * uy), control: CGPoint(x: 0, y: h - notch))
        path.addLine(to: CGPoint(x: -w + tipRadius * ux, y: h - tipRadius * uy))
        path.addQuadCurve(to: CGPoint(x: -w, y: h - tipRadius), control: CGPoint(x: -w, y: h))
        path.addQuadCurve(to: CGPoint(x: -w, y: topRadius), control: CGPoint(x: -w - 2 * bulge, y: h / 2))
        path.addQuadCurve(to: CGPoint(x: -w + topRadius, y: 0), control: CGPoint(x: -w, y: 0))
        path.closeSubpath()
        return path
    }
}

#Preview {
    BookmarkPocketIntro(autoPlay: true)
}
