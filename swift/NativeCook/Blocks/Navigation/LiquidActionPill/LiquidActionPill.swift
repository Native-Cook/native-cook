import SwiftUI

/// A two-tab bar whose top edge dips into a bowl that holds a round button. Pressing the button
/// throws it out of the bowl, which flattens behind it, and stretches it into a pill; the white dot
/// the plus shrank into divides into four, which turn into the pill's icons. Closing runs it back.
struct LiquidActionPill: View {
    /// The titles of the two pages, the house's first.
    var titles = ["Home", "Profile"]
    /// What the four icons of the pill are called: camera, picture, chart and the cross.
    var actionLabels = ["Camera", "Photos", "Insights", "Close"]
    /// The page behind everything, from its top left corner to its bottom right.
    var background = [Color(hex: 0xB3A3B9), Color(hex: 0xCDBACB)]
    /// The button and the pill.
    var accent = Color(hex: 0x7B16E8)
    /// The two tab icons.
    var tint = Color(hex: 0x7A2BE2)
    var barColor = Color.white
    /// Opens and closes the pill and changes the page by itself, every `LiquidActionPillMotion.loop` seconds.
    var autoPlay = false
    /// Called with 0 or 1 when a tab is chosen.
    var onTabChange: ((Int) -> Void)? = nil
    /// Called with 0, 1 or 2 when one of the pill's first three icons is pressed.
    var onAction: ((Int) -> Void)? = nil
    var onOpenChange: ((Bool) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var open = false
    @State private var tab = 0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// 0 with the button in its bowl, 1 with the pill open; it moves at a constant speed.
    @State private var way = Tween(0)
    /// 1 while the pill opens, 0 while it closes: the bowl behaves differently each way.
    @State private var opening = Tween(1)
    /// 0 on the first page, 1 on the second.
    @State private var page = Tween(0)
    /// The pill icon under a finger, and how far it is pressed in.
    @State private var held = -1
    @State private var depth = Tween(0)
    /// How many of the values above are on their way; the timeline is paused at none.
    @State private var moving = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var selectHaptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { safe in
            let insets = safe.safeAreaInsets
            GeometryReader { proxy in
                let size = proxy.size
                let barWidth = size.width - M.barMargin * 2
                let barTop = size.height - max(insets.bottom, M.barBottom) - M.barHeight
                let tabX = barWidth / 2 - M.tabInset

                TimelineView(.animation(paused: moving == 0)) { timeline in
                    let now = timeline.date
                    let turn = page.value(at: now)

                    ZStack(alignment: .topLeading) {
                        LinearGradient(colors: background, startPoint: .topLeading, endPoint: .bottomTrailing)
                        content(0, top: insets.top).opacity(1 - turn)
                        content(1, top: insets.top).opacity(turn)

                        if open {
                            // A touch on the page closes the pill.
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    touched()
                                    move(false)
                                }
                                .accessibilityHidden(true)
                        }

                        Canvas { context, _ in
                            var context = context
                            context.translateBy(x: size.width / 2, y: barTop)
                            draw(in: context, at: now, barWidth: barWidth)
                        }
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)

                        ForEach(0..<2, id: \.self) { index in
                            Button {
                                touched()
                                guard tab != index else { return }
                                selectHaptic.selectionChanged()
                                choose(index)
                            } label: {
                                Color.clear.frame(width: Metrics.tabHit, height: M.barHeight).contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(titles[index])
                            .accessibilityAddTraits(tab == index ? [.isSelected] : [])
                            .position(x: size.width / 2 + (index == 0 ? -tabX : tabX), y: barTop + M.barHeight / 2)
                        }

                        if open {
                            ForEach(0..<4, id: \.self) { index in
                                Color.clear
                                    .frame(width: M.iconGap, height: M.button)
                                    .contentShape(Rectangle())
                                    .gesture(press(index))
                                    .accessibilityElement()
                                    .accessibilityLabel(actionLabels[index])
                                    .accessibilityAddTraits(.isButton)
                                    .accessibilityAction { act(index) }
                                    .position(x: size.width / 2 + (Double(index) - 1.5) * M.iconGap, y: barTop - M.buttonRaise - M.rise)
                            }
                        } else {
                            Button {
                                touched()
                                tapHaptic.impactOccurred()
                                move(true)
                            } label: {
                                Color.clear.frame(width: Metrics.buttonHit, height: Metrics.buttonHit).contentShape(Circle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Actions")
                            .position(x: size.width / 2, y: barTop - M.buttonRaise)
                        }
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
            }
            .ignoresSafeArea()
        }
        .onAppear { tapHaptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The pages

    private func content(_ index: Int, top: Double) -> some View {
        let cards = index == 0 ? Metrics.homeCards : Metrics.profileCards
        return VStack(alignment: .leading, spacing: 0) {
            Text(titles[index])
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(height: Metrics.titleLine)
                .accessibilityAddTraits(.isHeader)
            ForEach(cards.indices, id: \.self) { n in
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Palette.frost)
                    .frame(height: cards[n].height)
                    .overlay(alignment: .topLeading) {
                        ZStack(alignment: .topLeading) {
                            if index == 1 && n == 0 {
                                Circle().fill(Palette.line).frame(width: Metrics.avatar, height: Metrics.avatar).offset(x: 20, y: 49)
                            }
                            ForEach(cards[n].lines, id: \.self) { line in
                                Capsule().fill(Palette.line).frame(width: line[2], height: line[3]).offset(x: line[0], y: line[1])
                            }
                        }
                    }
                    .padding(.top, n == 0 ? Metrics.cardsTop - Metrics.titleTop - Metrics.titleLine : Metrics.cardGap)
            }
        }
        .padding(.horizontal, M.barMargin)
        .padding(.top, top + Metrics.titleTop)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(tab != index)
    }

    // MARK: The bar, the button and the pill

    /// Draws around the middle of the bar's top edge.
    private func draw(in context: GraphicsContext, at now: Date, barWidth: Double) {
        let travelled = way.value(at: now)
        // Under reduced motion nothing travels: the button fades out, and the pill fades in where it belongs.
        let p = reduceMotion ? (travelled < 0.5 ? 0 : 1) : travelled
        let up = opening.value(at: now)
        let turn = page.value(at: now)
        let white = GraphicsContext.Shading.color(.white)

        // The bowl stays under reduced motion.
        let bar = M.bar(
            width: barWidth,
            depth: reduceMotion ? M.bowlDepth : M.bowlDepth(at: p, opening: up),
            half: reduceMotion ? M.bowlHalf : M.bowlHalf(at: p, opening: up)
        )
        var shaded = context
        shaded.addFilter(.shadow(color: Palette.barShadow, radius: Metrics.barShadowBlur, x: 0, y: Metrics.barShadowY))
        shaded.fill(bar, with: .color(barColor))

        let tabX = barWidth / 2 - M.tabInset
        let tabStroke = StrokeStyle(lineWidth: Metrics.tabStroke, lineCap: .round, lineJoin: .round)
        var house = context
        house.translateBy(x: -tabX - M.tabIcon / 2, y: (M.barHeight - M.tabIcon) / 2)
        house.fill(Art.house, with: .color(tint.opacity(Metrics.tabFill * (1 - turn))))
        house.stroke(Art.house, with: .color(tint), style: tabStroke)
        house.stroke(Art.door, with: .color(tint), style: tabStroke)
        var person = context
        person.translateBy(x: tabX - M.tabIcon / 2, y: (M.barHeight - M.tabIcon) / 2)
        person.fill(Art.person, with: .color(tint.opacity(Metrics.tabFill * turn)))
        person.stroke(Art.person, with: .color(tint), style: tabStroke)

        var pill = context
        pill.translateBy(x: 0, y: -M.buttonRaise + M.lift(p))
        pill.opacity = reduceMotion ? abs(travelled * 2 - 1) : 1
        let width = M.pillWidth(at: p)
        let height = M.pillHeight(at: p)
        let body = Path(roundedRect: CGRect(x: -width / 2, y: -height / 2, width: width, height: height), cornerRadius: height / 2, style: .circular)
        var lit = pill
        lit.addFilter(.shadow(color: accent.opacity(Metrics.pillShadowAlpha), radius: Metrics.pillShadowBlur, x: 0, y: Metrics.pillShadowY))
        lit.fill(body, with: .color(accent))

        let plus = M.plusScale(p)
        if plus > 0 {
            let arm = M.plusArm * plus
            var cross = Path()
            cross.move(to: CGPoint(x: -arm, y: 0))
            cross.addLine(to: CGPoint(x: arm, y: 0))
            cross.move(to: CGPoint(x: 0, y: -arm))
            cross.addLine(to: CGPoint(x: 0, y: arm))
            pill.stroke(cross, with: white, style: StrokeStyle(lineWidth: Metrics.plusStroke, lineCap: .round))
        }
        pill.fill(M.necks(p), with: white)

        let radius = M.dotRadius(p)
        let pressed = depth.value(at: now)
        let iconStroke = StrokeStyle(lineWidth: Metrics.iconStroke, lineCap: .round, lineJoin: .round)
        for index in 0..<4 {
            let x = M.dotX(p, index)
            let shown = M.iconIn(p, index)
            // The dot shrinks and fades as its icon grows out of it, its strokes being drawn.
            let r = radius * (1 - (1 - Metrics.iconFromScale) * shown)
            if r > 0, shown < 1 {
                pill.fill(Path(ellipseIn: CGRect(x: x - r, y: -r, width: r * 2, height: r * 2)), with: .color(.white.opacity(1 - shown)))
            }
            if shown > 0 {
                let scale = (Metrics.iconFromScale + (1 - Metrics.iconFromScale) * shown) * (held == index ? 1 - (1 - Metrics.pressedScale) * pressed : 1)
                var icon = pill
                icon.translateBy(x: x, y: 0)
                icon.scaleBy(x: scale, y: scale)
                icon.translateBy(x: -M.icon / 2, y: -M.icon / 2)
                icon.opacity *= shown
                for stroke in Art.icons[index] {
                    icon.stroke(stroke.trimmedPath(from: 0, to: shown), with: white, style: iconStroke)
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
    private func run(for seconds: Double, from date: Date) {
        moving += 1
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(max(0, date.timeIntervalSinceNow + seconds) + 0.1))
            moving -= 1
        }
    }

    private func move(_ next: Bool, at date: Date = .now) {
        open = next
        onOpenChange?(next)
        let target = next ? 1.0 : 0.0
        let at = way.value(at: date)
        // Turned round half way, the bowl changes over smoothly; from either end there is nothing to smooth.
        let settled = at <= 0 || at >= 1
        opening.retarget(target, at: date, duration: settled || reduceMotion ? 0 : M.turn, curve: .linear)
        let duration = abs(target - at) * (reduceMotion ? M.reducedDuration : M.duration)
        way.retarget(target, at: date, duration: duration, curve: .linear)
        run(for: max(duration, M.turn), from: date)
    }

    private func choose(_ index: Int, at date: Date = .now) {
        tab = index
        onTabChange?(index)
        page.retarget(Double(index), at: date, duration: M.tabFade, curve: .easeInOut)
        run(for: M.tabFade, from: date)
    }

    private func act(_ index: Int) {
        touched()
        tapHaptic.impactOccurred()
        if index < 3 { onAction?(index) }
        move(false)
    }

    /// A pill icon gives under the finger and acts when the finger is lifted over it.
    private func press(_ index: Int) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard held != index || depth.value(at: .now) == 0 else { return }
                held = index
                depth.retarget(1, duration: Metrics.pressIn, curve: .easeOut)
                run(for: Metrics.pressIn, from: .now)
            }
            .onEnded { value in
                depth.retarget(0, duration: Metrics.pressOut, curve: .easeOut)
                run(for: Metrics.pressOut, from: .now)
                let inside = CGRect(x: 0, y: 0, width: M.iconGap, height: M.button).contains(value.location)
                if inside { act(index) }
            }
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // Back to rest; the loop's first second covers the way there.
            move(false)
            choose(0)
        }
        var origin = clock.now
        var date = Date.now
        // Each step is given the moment it was due, so a late wake-up does not make a loop differ.
        let steps: [(Double, (Date) -> Void)] = [
            (M.openAt, { move(true, at: $0) }),
            (M.closeAt, { move(false, at: $0) }),
            (M.profileAt, { choose(1, at: $0) }),
            (M.homeAt, { choose(0, at: $0) }),
        ]
        while !Task.isCancelled {
            for (at, step) in steps {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step(date.addingTimeInterval(at))
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

private typealias M = LiquidActionPillMotion

private enum Palette {
    static let barShadow = Color(hex: 0x5E4676, opacity: 0.2)
    static let frost = Color.white.opacity(0.22)
    static let line = Color.white.opacity(0.5)
}

private enum Metrics {
    // The shadows' offsets and blur radii.
    static let barShadowY = 8.0
    static let barShadowBlur = 10.0
    static let pillShadowY = 6.0
    static let pillShadowBlur = 6.0
    static let pillShadowAlpha = 0.35
    /// How strongly the chosen tab's icon is filled.
    static let tabFill = 0.15
    static let tabStroke = 1.8
    static let iconStroke = 1.6
    static let plusStroke = 2.0
    static let pressedScale = 0.9
    static let pressIn = 0.08
    static let pressOut = 0.16
    /// An icon starts this small and its dot ends this small while one turns into the other.
    static let iconFromScale = 0.6

    static let titleTop = 24.0
    static let titleLine = 41.0
    static let cardGap = 16.0
    static let cardsTop = titleTop + titleLine + 20
    static let tabHit = 72.0
    static let buttonHit = 64.0
    static let avatar = 72.0

    struct Card {
        let height: Double
        /// The placeholder lines: left, top, width, height inside the card.
        let lines: [[Double]]
    }

    static let homeCards = [
        Card(height: 170, lines: [[20, 24, 96, 10], [20, 48, 200, 18], [20, 136, 140, 10]]),
        Card(height: 110, lines: [[20, 24, 120, 10], [20, 50, 220, 10], [20, 76, 160, 10]]),
    ]
    static let profileCards = [
        Card(height: 170, lines: [[112, 58, 140, 14], [112, 86, 180, 10], [112, 108, 110, 10]]),
        Card(height: 110, lines: [[20, 24, 90, 10], [20, 50, 250, 10], [20, 76, 190, 10]]),
    ]
}

private enum Art {
    private static func path(_ draw: (inout Path) -> Void) -> Path {
        var path = Path()
        draw(&path)
        return path
    }

    private static func point(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }

    private static let frame = Path(roundedRect: CGRect(x: 2.8, y: 2.8, width: 14.4, height: 14.4), cornerRadius: 3.2, style: .circular)

    /// The pill's icons in a 20-point box, one path for each stroke so that each can be drawn in.
    static let icons: [[Path]] = [
        [
            path {
                $0.move(to: point(2.5, 7.2))
                $0.addQuadCurve(to: point(4.2, 5.5), control: point(2.5, 5.5))
                $0.addLine(to: point(6.4, 5.5))
                $0.addLine(to: point(7.6, 3.6))
                $0.addLine(to: point(12.4, 3.6))
                $0.addLine(to: point(13.6, 5.5))
                $0.addLine(to: point(15.8, 5.5))
                $0.addQuadCurve(to: point(17.5, 7.2), control: point(17.5, 5.5))
                $0.addLine(to: point(17.5, 14.6))
                $0.addQuadCurve(to: point(15.8, 16.3), control: point(17.5, 16.3))
                $0.addLine(to: point(4.2, 16.3))
                $0.addQuadCurve(to: point(2.5, 14.6), control: point(2.5, 16.3))
                $0.closeSubpath()
            },
            Path(ellipseIn: CGRect(x: 10 - 2.9, y: 10.8 - 2.9, width: 5.8, height: 5.8)),
        ],
        [
            frame,
            Path(ellipseIn: CGRect(x: 7.3 - 1.3, y: 7.4 - 1.3, width: 2.6, height: 2.6)),
            path { $0.addLines([point(3.2, 14.2), point(7.6, 10.4), point(10.6, 13), point(13, 11), point(16.8, 14.4)]) },
        ],
        [
            frame,
            path { $0.addLines([point(5.8, 12.8), point(8.6, 9.8), point(10.9, 11.6), point(14.2, 7.6)]) },
            path { $0.addLines([point(11.6, 7.4), point(14.4, 7.4), point(14.4, 10.2)]) },
        ],
        [
            path { $0.addLines([point(5, 5), point(15, 15)]) },
            path { $0.addLines([point(15, 5), point(5, 15)]) },
        ],
    ]

    // The tabs' icons in a 24-point box: the shape that is stroked and filled, and what is only stroked.
    static let house = path {
        $0.move(to: point(4, 10.4))
        $0.addLine(to: point(12, 3.6))
        $0.addLine(to: point(20, 10.4))
        $0.addLine(to: point(20, 18.3))
        $0.addQuadCurve(to: point(17.9, 20.4), control: point(20, 20.4))
        $0.addLine(to: point(6.1, 20.4))
        $0.addQuadCurve(to: point(4, 18.3), control: point(4, 20.4))
        $0.closeSubpath()
    }
    static let door = path {
        $0.move(to: point(9.6, 20.4))
        $0.addLine(to: point(9.6, 15.4))
        $0.addQuadCurve(to: point(12, 13.4), control: point(9.6, 13.4))
        $0.addQuadCurve(to: point(14.4, 15.4), control: point(14.4, 13.4))
        $0.addLine(to: point(14.4, 20.4))
    }
    static let person = path {
        $0.addEllipse(in: CGRect(x: 12 - 3.9, y: 8.2 - 3.9, width: 7.8, height: 7.8))
        $0.addEllipse(in: CGRect(x: 5.6, y: 15, width: 12.8, height: 5.6))
    }
}

#Preview {
    LiquidActionPill(autoPlay: true)
}
