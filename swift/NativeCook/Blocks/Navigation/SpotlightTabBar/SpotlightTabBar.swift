import SwiftUI

struct SpotlightTabBarTab {
    var title: String
    /// A solid glyph in a 30 × 30 box, filled even-odd.
    var glyph: Path

    /// `glyph` is SVG path data made of M, L, C and Z.
    init(title: String, glyph: String) {
        self.title = title
        self.glyph = Self.path(glyph)
    }

    static let samples = [
        SpotlightTabBarTab(
            title: "Profile",
            glyph: "M15 4.5C17.76 4.5 20 6.74 20 9.5C20 12.26 17.76 14.5 15 14.5C12.24 14.5 10 12.26 10 9.5C10 6.74 12.24 4.5 15 4.5Z"
                + "M15 17C19.42 17 23 19.01 23 21.5C23 23.99 19.42 26 15 26C10.58 26 7 23.99 7 21.5C7 19.01 10.58 17 15 17Z"
        ),
        SpotlightTabBarTab(
            title: "Home",
            glyph: "M15 4L26 13.5L26 24C26 25.1 25.1 26 24 26L6 26C4.9 26 4 25.1 4 24L4 13.5Z"
                + "M12 26L12 20C12 18.34 13.34 17 15 17C16.66 17 18 18.34 18 20L18 26Z"
        ),
        SpotlightTabBarTab(
            title: "Alerts",
            glyph: "M15 4C10.6 4 8 7.2 8 11.5L8 16.5L5.5 20.5C5.1 21.2 5.6 22 6.4 22L23.6 22C24.4 22 24.9 21.2 24.5 20.5L22 16.5L22 11.5C22 7.2 19.4 4 15 4Z"
                + "M12 23.5L18 23.5C18 25.2 16.7 26.5 15 26.5C13.3 26.5 12 25.2 12 23.5Z"
        ),
        SpotlightTabBarTab(
            title: "Camera",
            glyph: "M7.5 9L10.2 9L11.6 6.6C11.9 6.2 12.3 6 12.8 6L17.2 6C17.7 6 18.1 6.2 18.4 6.6L19.8 9L22.5 9C24.43 9 26 10.57 26 12.5L26 21.5C26 23.43 24.43 25 22.5 25L7.5 25C5.57 25 4 23.43 4 21.5L4 12.5C4 10.57 5.57 9 7.5 9Z"
                + "M15 12C17.76 12 20 14.24 20 17C20 19.76 17.76 22 15 22C12.24 22 10 19.76 10 17C10 14.24 12.24 12 15 12Z"
                + "M15 14.2C16.55 14.2 17.8 15.45 17.8 17C17.8 18.55 16.55 19.8 15 19.8C13.45 19.8 12.2 18.55 12.2 17C12.2 15.45 13.45 14.2 15 14.2Z"
        ),
    ]

    /// Reads the four commands the glyphs are written in.
    private static func path(_ data: String) -> Path {
        var path = Path()
        var command: Character = "M"
        var numbers: [Double] = []
        var digits = ""

        func flush() {
            if let value = Double(digits) { numbers.append(value) }
            digits = ""
            let needed = command == "C" ? 6 : 2
            guard numbers.count == needed else { return }
            switch command {
            case "M": path.move(to: CGPoint(x: numbers[0], y: numbers[1]))
            case "L": path.addLine(to: CGPoint(x: numbers[0], y: numbers[1]))
            default:
                path.addCurve(
                    to: CGPoint(x: numbers[4], y: numbers[5]),
                    control1: CGPoint(x: numbers[0], y: numbers[1]),
                    control2: CGPoint(x: numbers[2], y: numbers[3])
                )
            }
            numbers = []
        }

        for character in data {
            if character.isLetter {
                flush()
                if character == "Z" { path.closeSubpath() } else { command = character }
            } else if character == " " {
                flush()
            } else {
                digits.append(character)
            }
        }
        flush()
        return path
    }
}

/// A tab bar lit by a lamp: a white bar on the rim of a grey pill throws a cone of light into it,
/// and the glyph under the lamp turns white. Choosing a tab slides the lamp there, its beam leaning
/// back as it goes and flaring as it arrives; the glyphs it passes catch the light for a moment.
/// The lamp can also be dragged along the pill. The page above changes with the tab.
struct SpotlightTabBar: View {
    var tabs = SpotlightTabBarTab.samples
    /// The tab that is lit at first, from 0.
    var initialTab = 0
    /// The screen, the pill, and a glyph in the shade and in the light.
    var field = Color(hex: 0xc4c4c4)
    var pill = Color(hex: 0x686868)
    var shade = Color(hex: 0x3a3a3a)
    var light = Color.white
    /// Goes round the tabs by itself every `M.loop` seconds.
    var autoPlay = false
    /// Called with the tab a tap or a drag chose.
    var onSelect: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The open tab; nil until one is chosen.
    @State private var chosen: Int? = nil
    /// Where the lamp's move started, or the finger that holds it, and when; nil while the first tab rests.
    @State private var lamp: Lamp? = nil
    @State private var lampStart = Date.distantPast
    /// The finger's place along the pill, in tabs, while it holds the lamp.
    @State private var finger = 0.0
    @State private var dragging = false
    /// Where the change of page started, and when.
    @State private var turn: Turn? = nil
    @State private var turnStart = Date.distantPast
    /// The timeline runs while something moves.
    @State private var busy = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    private var last: Int { max(tabs.count - 1, 0) }
    private var first: Int { min(max(initialTab, 0), last) }
    private var index: Int { chosen ?? first }
    private var lampNow: Lamp { lamp ?? .resting(first) }
    private var turnNow: Turn { turn ?? Turn(target: first, from: first, presence: 0) }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let pillWidth = width - Metrics.side * 2
            let pillTop = height - max(insets.bottom, 16) - Metrics.pillLift - Metrics.pillHeight
            // From one glyph's centre to the next.
            let pitch = last > 0 ? (pillWidth - Metrics.edge * 2) / Double(last) : 0

            TimelineView(.animation(paused: !busy)) { timeline in
                let now = lights(at: timeline.date)
                let turned = timeline.date.timeIntervalSince(turnStart)

                ZStack(alignment: .topLeading) {
                    field

                    ForEach(Array(tabs.enumerated()), id: \.offset) { k, tab in
                        page(tab, width: width - Metrics.margin * 2, mark: turned < M.pageOut ? 1 : M.clamp((turned - M.pageOut - M.markAfter) / M.markIn))
                            .opacity(Self.presence(of: k, in: turnNow, at: turned))
                            .offset(x: Metrics.margin, y: insets.top + Metrics.titleTop)
                            .accessibilityHidden(k != index)
                    }

                    Canvas { context, _ in
                        var bar = context
                        bar.translateBy(x: Metrics.bleed, y: Metrics.bleed)
                        let outline = Path(roundedRect: CGRect(x: 0, y: 0, width: pillWidth, height: Metrics.pillHeight), cornerRadius: Metrics.pillHeight / 2)

                        var shadowed = bar
                        shadowed.addFilter(.shadow(color: .black.opacity(0.28), radius: Metrics.shadowSigma, x: 0, y: Metrics.shadowDrop))
                        shadowed.fill(outline, with: .color(pill))

                        // The beams stay inside the pill; the lamps lie across its edge.
                        var inside = bar
                        inside.clip(to: outline)
                        Self.drawBeam(in: inside, top: Metrics.edge + now.x * pitch, foot: Metrics.edge + now.foot * pitch, color: light, opacity: (M.beam + now.flare) * now.power)
                        if now.nextPower > 0 {
                            let x = Metrics.edge + now.nextX * pitch
                            Self.drawBeam(in: inside, top: x, foot: x, color: light, opacity: M.beam * now.nextPower)
                        }

                        for (k, tab) in tabs.enumerated() {
                            var glyph = bar
                            glyph.translateBy(x: Metrics.edge + Double(k) * pitch - Metrics.glyph / 2, y: (Metrics.pillHeight - Metrics.glyph) / 2)
                            glyph.fill(tab.glyph, with: .color(shade), style: FillStyle(eoFill: true))
                            // The lit colour fades in over the shaded one.
                            glyph.opacity = Self.lit(now, k)
                            glyph.fill(tab.glyph, with: .color(light), style: FillStyle(eoFill: true))
                        }

                        Self.drawLamp(in: bar, at: Metrics.edge + now.x * pitch, color: light.opacity(now.power))
                        if now.nextPower > 0 {
                            Self.drawLamp(in: bar, at: Metrics.edge + now.nextX * pitch, color: light.opacity(now.nextPower))
                        }
                    }
                    .frame(width: pillWidth + Metrics.bleed * 2, height: Metrics.pillHeight + Metrics.bleed * 2)
                    .offset(x: Metrics.side - Metrics.bleed, y: pillTop - Metrics.bleed)
                    .allowsHitTesting(false)

                    // Dragging along the pill takes the lamp with the finger; it goes to the nearest tab on release.
                    Color.clear
                        .frame(width: pillWidth, height: Metrics.pillHeight)
                        .contentShape(Capsule())
                        .gesture(
                            DragGesture(minimumDistance: 8)
                                .onChanged { value in drag(to: (value.location.x - Metrics.edge) / pitch) }
                                .onEnded { _ in release() }
                        )
                        .onTapGesture { location in
                            touches += 1
                            select(min(max(Int(((location.x - Metrics.edge) / pitch).rounded()), 0), last), byHand: true)
                        }
                        .accessibilityRepresentation {
                            HStack {
                                ForEach(Array(tabs.enumerated()), id: \.offset) { k, tab in
                                    Button(tab.title) { select(k, byHand: true) }
                                        .accessibilityAddTraits(k == index ? .isSelected : [])
                                }
                            }
                        }
                        .offset(x: Metrics.side, y: pillTop)
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .clipped()
            }
            // The reader starts inside the safe area; the screen is laid out from its real corner.
            .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .task(id: BusyKey(lamp: lampStart, turn: turnStart)) {
            try? await Task.sleep(for: .seconds(M.rest))
            if !Task.isCancelled { busy = false }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The page

    private func page(_ tab: SpotlightTabBarTab, width: Double, mark: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(tab.title)
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .frame(height: 41)
                .padding(.bottom, 18 - Metrics.cardGap)
                .accessibilityAddTraits(.isHeader)
            ForEach(Array(Metrics.cards.enumerated()), id: \.offset) { k, cardHeight in
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(Palette.card)
                    .frame(height: cardHeight)
                    .overlay(alignment: .bottomLeading) {
                        VStack(alignment: .leading, spacing: 12) {
                            RoundedRectangle(cornerRadius: 5).fill(Palette.line).frame(width: 150, height: 10)
                            RoundedRectangle(cornerRadius: 5).fill(Palette.line).frame(width: 96, height: 10)
                        }
                        .padding(22)
                    }
                    .overlay {
                        if k == 0 {
                            // The mark leaves with its page and comes back after the next one.
                            tab.glyph
                                .applying(CGAffineTransform(scaleX: Metrics.cardMark / Metrics.glyph, y: Metrics.cardMark / Metrics.glyph))
                                .fill(Palette.mark, style: FillStyle(eoFill: true))
                                .frame(width: Metrics.cardMark, height: Metrics.cardMark)
                                .opacity(mark)
                        }
                    }
                    .padding(.top, Metrics.cardGap)
            }
        }
        .frame(width: max(0, width), alignment: .leading)
        .allowsHitTesting(false)
    }

    // MARK: Behaviour

    private func lights(at date: Date) -> Lights {
        Self.lights(lampNow, date.timeIntervalSince(lampStart), finger: finger, last: Double(last), reduced: reduceMotion)
    }

    /// Sends the lamp to a tab from wherever it is, and turns the page if the tab is another one.
    private func go(_ target: Int) {
        let moment = Date.now
        let was = lights(at: moment)
        // With reduced motion the light that is stronger is the one that fades out.
        let x = was.nextPower > 0.5 ? was.nextX : was.x
        lamp = Lamp(held: false, offset: 0, target: Double(target), x: x, foot: was.nextPower > 0.5 ? x : was.foot, dim: 1 - was.power)
        lampStart = moment
        busy = true
        let page = turnNow
        guard page.target != target else { return }
        let t = moment.timeIntervalSince(turnStart)
        let leaving = t < M.pageOut ? page.from : page.target
        turn = Turn(target: target, from: leaving, presence: Self.presence(of: leaving, in: page, at: t))
        turnStart = moment
    }

    /// Opens a tab: the same path for a tap and for the loop, which is silent.
    private func select(_ target: Int, byHand: Bool) {
        guard target != index else { return }
        if byHand { haptic.selectionChanged() }
        chosen = target
        go(target)
        if byHand { onSelect?(target) }
    }

    private func drag(to place: Double) {
        if !dragging {
            dragging = true
            touches += 1
            let moment = Date.now
            let was = lights(at: moment)
            lamp = Lamp(held: true, offset: was.x - place, target: was.x, x: was.x, foot: was.x, dim: 1 - was.power)
            lampStart = moment
            busy = true
        }
        finger = place
    }

    private func release() {
        dragging = false
        let target = Int(lights(at: .now).x.rounded())
        let changed = target != index
        if changed {
            haptic.selectionChanged()
            chosen = target
        }
        go(target)
        if changed { onSelect?(target) }
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // The loop starts over from the tab it was given.
            select(first, byHand: false)
            try? await Task.sleep(for: .seconds(M.settle))
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for k in 0..<tabs.count {
                try? await clock.sleep(until: origin + .seconds(M.hopAt + Double(k) * M.hopEvery))
                guard !Task.isCancelled else { return }
                select((index + 1) % tabs.count, byHand: false)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }

    // MARK: One frame

    /// The lights, `t` seconds into a move or a hold.
    private static func lights(_ lamp: Lamp, _ t: Double, finger: Double, last: Double, reduced: Bool) -> Lights {
        if lamp.held {
            let x = min(max(finger + lamp.offset * (1 - M.easeOut(t / M.catchUp)), 0), last)
            return Lights(x: x, foot: x, power: 1 - lamp.dim * (1 - M.clamp(t / M.dimFade)), flare: 0, nextX: x, nextPower: 0)
        }
        if reduced {
            let faded = M.clamp(t / M.fade)
            return Lights(x: lamp.x, foot: lamp.x, power: 1 - faded, flare: 0, nextX: lamp.target, nextPower: faded)
        }
        let distance = abs(lamp.target - lamp.x)
        let slide = M.slide + M.slidePerTab * max(distance - 1, 0)
        let far = M.dim * M.clamp((distance - 1) / 2)
        let dim = max(far * sin(.pi * M.clamp(t / slide)), lamp.dim * (1 - M.clamp(t / M.dimFade)))
        return Lights(
            x: lamp.x + (lamp.target - lamp.x) * M.easeInOut(t / slide),
            foot: lamp.foot + (lamp.target - lamp.foot) * M.easeInOut((t - M.lag) / slide),
            power: 1 - dim,
            flare: M.flare * sin(.pi * M.clamp((t - slide) / M.flareFor)),
            nextX: lamp.target,
            nextPower: 0
        )
    }

    /// How much light falls on a glyph.
    private static func lit(_ now: Lights, _ index: Int) -> Double {
        let i = Double(index)
        return M.clamp(M.smooth(1 - abs(now.x - i) / M.reach) * now.power + M.smooth(1 - abs(now.nextX - i) / M.reach) * now.nextPower)
    }

    /// How much of a page shows, `t` seconds into a change.
    private static func presence(of page: Int, in turn: Turn, at t: Double) -> Double {
        if t < M.pageOut { return page == turn.from ? turn.presence * (1 - max(t, 0) / M.pageOut) : 0 }
        return page == turn.target ? M.clamp((t - M.pageOut) / M.pageIn) : 0
    }

    // MARK: Drawing

    /// A beam in the pill's box: from the lamp's width at the top edge to its foot at the bottom.
    private static func drawBeam(in context: GraphicsContext, top: Double, foot: Double, color: Color, opacity: Double) {
        var beam = Path()
        beam.move(to: CGPoint(x: top - Metrics.lamp / 2, y: 0))
        beam.addLine(to: CGPoint(x: top + Metrics.lamp / 2, y: 0))
        beam.addLine(to: CGPoint(x: foot + Metrics.beamFoot / 2, y: Metrics.pillHeight))
        beam.addLine(to: CGPoint(x: foot - Metrics.beamFoot / 2, y: Metrics.pillHeight))
        beam.closeSubpath()
        context.fill(
            beam,
            with: .linearGradient(
                Gradient(colors: [color.opacity(opacity), color.opacity(0)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: Metrics.pillHeight)
            )
        )
    }

    private static func drawLamp(in context: GraphicsContext, at x: Double, color: Color) {
        let box = CGRect(x: x - Metrics.lamp / 2, y: -Metrics.lampHeight / 2, width: Metrics.lamp, height: Metrics.lampHeight)
        context.fill(Path(roundedRect: box, cornerRadius: Metrics.lampHeight / 2), with: .color(color))
    }

    // MARK: Types

    /// Where the lamp's move started, or the finger that holds it.
    private struct Lamp {
        /// A finger holds the lamp: it is at the finger, `offset` tabs aside at first.
        var held: Bool
        var offset: Double
        var target: Double
        /// The lamp's place and the beam's foot at the start, in tabs.
        var x: Double
        var foot: Double
        /// How dim the lamp was at the start.
        var dim: Double

        static func resting(_ tab: Int) -> Lamp {
            Lamp(held: false, offset: 0, target: Double(tab), x: Double(tab), foot: Double(tab), dim: 0)
        }
    }

    /// The lights on the bar: one, and with reduced motion a second one fading in at the target.
    private struct Lights {
        var x: Double
        var foot: Double
        var power: Double
        var flare: Double
        var nextX: Double
        var nextPower: Double
    }

    /// Where a change of page started.
    private struct Turn {
        var target: Int
        var from: Int
        var presence: Double
    }

    private struct BusyKey: Equatable {
        let lamp: Date
        let turn: Date
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private enum Palette {
        static let ink = Color(hex: 0x3a3a3a)
        static let card = Color(hex: 0xd0d0d0)
        static let line = Color(hex: 0xb6b6b6)
        static let mark = Color(hex: 0x9a9a9a)
    }

    private enum Metrics {
        // The pill: its margins, its height, and the first and last glyph's distance from its ends.
        static let side = 28.0
        static let pillHeight = 64.0
        static let pillLift = 6.0
        static let edge = 62.0
        static let glyph = 30.0
        // The lamp lies on the pill's top edge; its beam widens from the lamp to the pill's bottom edge.
        static let lamp = 62.0
        static let lampHeight = 6.0
        static let beamFoot = 72.0
        // Room around the pill in its canvas, for the shadow and the lamp.
        static let bleed = 44.0
        static let shadowDrop = 10.0
        static let shadowSigma = 11.0
        // The page.
        static let margin = 24.0
        static let titleTop = 25.0
        static let cards = [180.0, 110.0, 110.0]
        static let cardGap = 14.0
        static let cardMark = 44.0
    }

    /// Seconds. The loop goes once round the tabs: a move every `hopEvery` from `hopAt`.
    private enum M {
        static let loop = 9.0
        static let hopAt = 2.0
        static let hopEvery = 1.6
        // One move: the lamp slides for `slide`, and `slidePerTab` longer for each tab after the
        // first. The foot of the beam follows `lag` behind, so the beam leans back and straightens
        // on arrival.
        static let slide = 0.45
        static let slidePerTab = 0.05
        static let lag = 0.06
        // The beam's strength at rest; on arrival it flares by `flare` and settles in `flareFor`.
        static let beam = 0.55
        static let flare = 0.15
        static let flareFor = 0.25
        // Across the whole bar the lamp dims by `dim` in mid-move; a dim left by a move that was
        // cut short fades in `dimFade`.
        static let dim = 0.6
        static let dimFade = 0.15
        // A glyph is lit while the lamp is within this many tabs of it.
        static let reach = 0.55
        // The page: the old one fades out, the new one in, and the mark on its top card a little later.
        static let pageOut = 0.15
        static let pageIn = 0.25
        static let markAfter = 0.1
        static let markIn = 0.25
        // A finger that takes the lamp away from where it is gets it in this time.
        static let catchUp = 0.15
        // The clocks of a move run this long, well past its last change.
        static let rest = 1.2
        // With reduced motion the light fades from one tab to the next in this time.
        static let fade = 0.2
        // After a touch the loop waits, then goes back to its first tab and waits for that to end.
        static let userHold = 3.0
        static let settle = 1.0

        static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }

        static func smooth(_ t: Double) -> Double {
            let u = clamp(t)
            return u * u * (3 - 2 * u)
        }

        static func easeOut(_ t: Double) -> Double {
            let u = 1 - clamp(t)
            return 1 - u * u * u
        }

        static func easeInOut(_ t: Double) -> Double {
            let u = clamp(t)
            return u < 0.5 ? 4 * u * u * u : 1 - 4 * (1 - u) * (1 - u) * (1 - u)
        }
    }
}
