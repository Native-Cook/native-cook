import SwiftUI

/// A plain four-icon tab bar and one round button that changes place and purpose with the tab. The
/// icon of the chosen tab turns into a heavier drawing and wiggles; the button shrinks away on the
/// tabs that have no use for it, pops in again, and slides along an arc between its two places while
/// one glyph turns into the other.
struct RoamingFabTabBar: View {
    /// The titles of the four pages, in the order of the tabs.
    var titles = ["Home", "Search", "Add friends", "Chats"]
    var searchHint = "Find people and rooms"
    var addLabel = "Add"
    /// The times at the end of the rows of the last page.
    var times = ["9:41", "9:12", "Mon", "Sun", "Fri", "Thu", "Wed", "Tue", "Mon"]
    /// The round button.
    var accent = Color(hex: 0x1274FF)
    /// Goes through the tabs by itself, every `RoamingFabTabBarMotion.loop` seconds.
    var autoPlay = false
    /// Called with the tab's place, 0 to 3.
    var onTabChange: ((Int) -> Void)? = nil
    /// Called with the tab the round button was pressed on: 0 to start something, 3 to write.
    var onAction: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The tab that is chosen, and the one that was.
    @State private var tab = 0
    @State private var previous = 0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// How far the change of tab has run, 0 to 1 at a constant speed.
    @State private var clock = Tween(1)
    /// The button: 0 at the first tab's place and 1 at the last tab's, how much of it there is, and how far it is pressed in.
    @State private var place = Tween(0)
    @State private var shown = Tween(1)
    @State private var pressed = Tween(0)
    /// How many of the values above are on their way; the timeline is paused at none.
    @State private var moving = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var selectHaptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { safe in
            let insets = safe.safeAreaInsets
            GeometryReader { proxy in
                let size = proxy.size
                let barTop = size.height - max(insets.bottom, M.barBottom) - M.bar
                let homeX = size.width / 2
                let chatX = size.width - M.chatInset

                TimelineView(.animation(paused: moving == 0)) { timeline in
                    let now = timeline.date
                    let turned = M.clamp01(clock.value(at: now) * M.change / M.pageFade)

                    ZStack(alignment: .topLeading) {
                        Palette.page
                        ForEach(0..<4, id: \.self) { index in
                            page(index, top: insets.top)
                                .opacity(index == tab ? turned : index == previous ? 1 - turned : 0)
                                .accessibilityHidden(index != tab)
                        }
                        .allowsHitTesting(false)

                        Palette.page
                            .frame(height: size.height - barTop)
                            .offset(y: barTop)

                        Canvas { context, _ in
                            draw(in: context, at: now, width: size.width, barTop: barTop)
                        }
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)

                        ForEach(0..<4, id: \.self) { index in
                            Button {
                                touched()
                                if tab != index { selectHaptic.selectionChanged() }
                                choose(index)
                            } label: {
                                Color.clear.frame(width: M.tabHit * 2, height: M.tabHit * 2).contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(titles[index])
                            .accessibilityAddTraits(tab == index ? [.isSelected] : [])
                            .position(x: size.width * (Double(index) + 0.5) / 4, y: barTop + M.iconY)
                        }

                        if tab == 0 || tab == 3 {
                            let hit = tab == 3 ? M.chatSize : M.homeSize
                            Color.clear
                                .frame(width: hit, height: hit)
                                .contentShape(Circle())
                                .gesture(press(inside: hit))
                                .accessibilityElement()
                                .accessibilityLabel(tab == 3 ? "New chat" : "Start a room")
                                .accessibilityAddTraits(.isButton)
                                .accessibilityAction { act() }
                                .position(x: tab == 3 ? chatX : homeX, y: barTop - (tab == 3 ? M.chatRise : M.homeRise))
                        }
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { tapHaptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The pages

    private func page(_ index: Int, top: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(titles[index])
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .frame(height: M.titleLine)
                .padding(.bottom, 6)
                .accessibilityAddTraits(.isHeader)
            switch index {
            case 0:
                ForEach(Metrics.cards, id: \.self) { width in
                    VStack(alignment: .leading, spacing: 0) {
                        Capsule().fill(Palette.avatar).frame(width: width, height: 12)
                        Spacer(minLength: 0)
                        HStack(spacing: -10) {
                            ForEach(0..<4, id: \.self) { _ in
                                Circle().fill(Palette.avatar).frame(width: 36, height: 36).padding(2).background(Circle().fill(Palette.placeholder))
                            }
                        }
                        Spacer(minLength: 0)
                        Capsule().fill(Palette.avatar).frame(width: 80, height: 10)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: 132)
                    .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Palette.placeholder))
                    .padding(.top, 14)
                }
            case 1:
                Text(searchHint)
                    .font(.system(size: 16))
                    .foregroundStyle(Palette.mute)
                    .lineLimit(1)
                    .padding(.leading, 44)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: 44)
                    .background(alignment: .topLeading) {
                        ZStack(alignment: .topLeading) {
                            Capsule().fill(Palette.placeholder)
                            Circle().strokeBorder(Palette.mute, lineWidth: 2).frame(width: 13, height: 13).offset(x: 16, y: 14)
                            Capsule().fill(Palette.mute).frame(width: 6, height: 2).rotationEffect(.degrees(45)).offset(x: 26, y: 26)
                        }
                    }
                    .padding(.top, 14)
                    .padding(.bottom, 6)
                rows { _ in EmptyView() }
            case 2:
                rows { _ in
                    Text(addLabel)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(accent)
                        .frame(width: 60, height: 30)
                        .background(Capsule().fill(Palette.addFill))
                }
            default:
                rows { index in
                    Text(times[index % times.count])
                        .font(.system(size: 13))
                        .monospacedDigit()
                        .foregroundStyle(Palette.mute)
                }
            }
        }
        .padding(.horizontal, M.margin)
        .padding(.top, top + M.titleTop)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    /// Nine people: a round picture and two lines, with whatever the page puts at the end of a row.
    private func rows(@ViewBuilder end: @escaping (Int) -> some View) -> some View {
        ForEach(Metrics.rows.indices, id: \.self) { index in
            HStack(spacing: 14) {
                Circle().fill(Palette.placeholder).frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 8) {
                    Capsule().fill(Palette.avatar).frame(width: Metrics.rows[index], height: 12)
                    Capsule().fill(Palette.avatar).frame(width: 80, height: 10)
                }
                Spacer(minLength: 0)
                end(index)
            }
            .frame(height: 64)
        }
    }

    // MARK: The icons and the button

    private func draw(in context: GraphicsContext, at now: Date, width: Double, barTop: Double) {
        let t = clock.value(at: now)
        let changed = M.clamp01(t * M.change / M.iconFade)
        for index in 0..<4 {
            let icon = M.icons[index]
            // The chosen tab's icon wiggles in on its pivot.
            let wiggled = index == tab && !reduceMotion ? t : 1
            let chosen = index == tab ? changed : index == previous ? 1 - changed : 0
            var layer = context
            layer.translateBy(x: width * (Double(index) + 0.5) / 4 + icon.pivot.x, y: barTop + M.iconY + icon.pivot.y)
            layer.rotate(by: .degrees(M.wiggle(at: wiggled)))
            layer.scaleBy(x: M.swell(at: wiggled), y: M.swell(at: wiggled))
            layer.translateBy(x: -icon.pivot.x, y: -icon.pivot.y)
            paint(icon.off, Palette.outline, 1 - chosen, in: layer)
            paint(icon.on, Palette.ink, chosen, in: layer)
        }

        let travelled = place.value(at: now)
        let there = shown.value(at: now)
        // Under reduced motion the button does not travel: it fades out at one place and in at the other.
        let p = reduceMotion ? (travelled < 0.5 ? 0 : 1) : travelled
        let radius = M.mix(M.homeSize, M.chatSize, p) / 2
        let scale = max(0, (reduceMotion ? 1 : there) * (1 - M.press * pressed.value(at: now)))
        var button = context
        button.translateBy(
            x: M.mix(width / 2, width - M.chatInset, p),
            y: barTop - M.mix(M.homeRise, M.chatRise, p) - M.arc * sin(.pi * M.clamp01(p))
        )
        button.scaleBy(x: scale, y: scale)
        button.opacity = reduceMotion ? M.clamp01(there) * abs(travelled * 2 - 1) : 1
        let disc = Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2))
        var lit = button
        lit.addFilter(.shadow(color: accent.opacity(M.shadowAlpha), radius: M.shadowBlur, x: 0, y: M.shadowY))
        lit.fill(disc, with: .color(accent))

        // One glyph turns into the other on the way across, and the plus makes a quarter turn as it shrinks away.
        let glyph = StrokeStyle(lineWidth: M.glyphStroke, lineCap: .round, lineJoin: .round)
        var plus = button
        plus.rotate(by: .radians(reduceMotion ? 0 : -.pi * p + .pi / 2 * (1 - M.clamp01(there))))
        plus.opacity *= 1 - M.clamp01(p)
        plus.stroke(M.plus, with: .color(.white), style: glyph)
        var compose = button
        compose.rotate(by: .radians(reduceMotion ? 0 : .pi * (1 - p)))
        compose.scaleBy(x: M.chatSize / M.homeSize * M.composeScale, y: M.chatSize / M.homeSize * M.composeScale)
        compose.opacity *= M.clamp01(p)
        compose.stroke(M.bubble, with: .color(.white), style: glyph)
        compose.stroke(M.pencil, with: .color(.white), style: glyph)
    }

    /// One drawing of an icon.
    private func paint(_ pieces: [M.Piece], _ color: Color, _ opacity: Double, in context: GraphicsContext) {
        guard opacity > 0 else { return }
        var layer = context
        layer.opacity *= opacity
        // Drawn as one layer, so that a cut-out detail does not show through while the drawing fades.
        layer.drawLayer { layer in
            for piece in pieces {
                let shading = GraphicsContext.Shading.color(piece.paint == .stroke || piece.paint == .fill ? color : Palette.page)
                if piece.paint == .fill || piece.paint == .cutFill {
                    layer.fill(piece.path, with: shading)
                } else {
                    layer.stroke(piece.path, with: shading, style: StrokeStyle(lineWidth: piece.width, lineCap: .round, lineJoin: .round))
                }
            }
        }
    }

    // MARK: Behaviour

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    /// Keeps the timeline running for as long as a value is on its way.
    private func keepMoving(for seconds: Double, from date: Date) {
        moving += 1
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(max(0, date.timeIntervalSinceNow + seconds) + 0.1))
            moving -= 1
        }
    }

    private func choose(_ next: Int, at date: Date = .now) {
        guard next != tab else { return }
        let hides = next == 1 || next == 2
        let hidden = tab == 1 || tab == 2
        let target = next == 3 ? 1.0 : 0.0
        previous = tab
        tab = next
        onTabChange?(next)
        clock.retarget(0, at: date, duration: 0, curve: .linear)
        clock.retarget(1, at: date, duration: M.change, curve: .linear)
        if hides {
            shown.retarget(0, at: date, duration: M.shrink, curve: reduceMotion ? .linear : M.shrinkEase)
        } else if hidden {
            // Out of sight, the button takes its new place at once and pops in there.
            place.retarget(target, at: date, duration: 0, curve: .linear)
            shown.retarget(1, at: date, duration: M.pop, curve: reduceMotion ? .linear : M.popEase)
        } else {
            place.retarget(target, at: date, duration: reduceMotion ? M.reducedSlide : M.slide, curve: reduceMotion ? .linear : M.slideEase)
        }
        keepMoving(for: M.change, from: date)
    }

    private func act() {
        touched()
        tapHaptic.impactOccurred()
        onAction?(tab)
    }

    /// The round button gives under the finger and acts when the finger is lifted over it.
    private func press(inside hit: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard pressed.value(at: .now) == 0 else { return }
                pressed.retarget(1, duration: M.pressIn, curve: .easeOut)
                keepMoving(for: M.pressIn, from: .now)
            }
            .onEnded { value in
                pressed.retarget(0, duration: M.pressOut, curve: .easeOut)
                keepMoving(for: M.pressOut, from: .now)
                if CGRect(x: 0, y: 0, width: hit, height: hit).contains(value.location) { act() }
            }
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // Back to rest; the loop's first second covers the way there.
            choose(0)
        }
        var origin = clock.now
        var date = Date.now
        while !Task.isCancelled {
            // Each step is given the moment it was due, so a late wake-up does not make a loop differ.
            for (at, index) in M.steps {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                choose(index, at: date.addingTimeInterval(at))
            }
            origin += .seconds(M.loop)
            date.addTimeInterval(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

private typealias M = RoamingFabTabBarMotion

private enum Palette {
    static let page = Color.white
    static let ink = Color(hex: 0x111111)
    static let outline = Color(hex: 0x55555A)
    static let mute = Color(hex: 0x9A9AA0)
    static let placeholder = Color(hex: 0xF1F1F3)
    static let avatar = Color(hex: 0xE2E2E6)
    static let addFill = Color(hex: 0xEEF4FF)
}

private enum Metrics {
    /// The widths of the first line of the four cards, and of the nine rows.
    static let cards = [110.0, 150.0, 130.0, 120.0]
    static let rows = [120.0, 150.0, 100.0, 140.0, 110.0, 130.0, 152.0, 104.0, 136.0]
}

#Preview {
    RoamingFabTabBar(autoPlay: true)
}
