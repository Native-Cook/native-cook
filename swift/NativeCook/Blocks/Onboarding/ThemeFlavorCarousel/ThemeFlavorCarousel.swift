import SwiftUI

struct ThemeFlavorCarouselFlavor: Equatable {
    var name: String
    /// The darkest tone: a soft spot at the top of the card.
    var deep: UInt32
    /// The card's base, and the tint of the button.
    var mid: UInt32
    /// The pale tone that comes up from the bottom left corner.
    var light: UInt32
    /// The button's label and glyph on this flavor.
    var ink: UInt32

    static let samples = [
        ThemeFlavorCarouselFlavor(name: "Mint", deep: 0x0b9077, mid: 0x669990, light: 0xe7f6f5, ink: 0x0a4f43),
        ThemeFlavorCarouselFlavor(name: "Clay", deep: 0xa17d6f, mid: 0xc0a087, light: 0xfcf6f8, ink: 0x3a2f2b),
        ThemeFlavorCarouselFlavor(name: "Graphite", deep: 0x121013, mid: 0x716f73, light: 0xf7f5f9, ink: 0x2b2b2e),
        ThemeFlavorCarouselFlavor(name: "Azure", deep: 0x009efb, mid: 0x0c6ed9, light: 0xecf6fa, ink: 0x0b4f8f),
        ThemeFlavorCarouselFlavor(name: "Cobalt", deep: 0x1259ba, mid: 0x2362b6, light: 0xebeffa, ink: 0x123c7c),
        ThemeFlavorCarouselFlavor(name: "Lagoon", deep: 0x0da1af, mid: 0x3093c9, light: 0xecf3f4, ink: 0x0b5560),
    ]
}

/// A personalisation step: a carousel of tall mesh-gradient cards, one per colour "flavor", that
/// goes round without an end, and a glassy button that takes the colours of the card in the middle
/// as it passes. The button gives the whole screen that flavor: the card grows over it.
struct ThemeFlavorCarousel: View {
    /// The cards, in order; the last is followed by the first. At least four.
    var flavors = ThemeFlavorCarouselFlavor.samples
    var title = "Select or Create a Flavor"
    var subtitle = "Flavors theme backgrounds, buttons, and more"
    var buttonLabel = "Select Flavor"
    /// Swipes through the flavors by itself, one every 1.6 s: `loop` seconds for the six it comes with.
    var autoPlay = false
    /// Called when another flavor comes to the middle.
    var onChange: ((ThemeFlavorCarouselFlavor, Int) -> Void)? = nil
    /// Called when the button gives the screen the flavor in the middle.
    var onSelect: ((ThemeFlavorCarouselFlavor, Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    /// How the carousel last set off; where it is now follows from the time since.
    @State private var travel = Travel(from: 0, to: 0, velocity: 0, start: .distantPast, kind: .spring)
    /// Where the carousel is while a finger holds it, in cards.
    @State private var drag: Double? = nil
    @State private var grabbed = 0.0
    /// The card the carousel is on or heading to; like the position, it keeps counting past the last flavor.
    @State private var centre = 0
    /// The flavor the screen took, and whether it still has it: the colours stay while it leaves.
    @State private var chosen = 0
    @State private var chosenOn = false
    /// 0 to 1 as the chosen flavor covers the screen.
    @State private var cover = Tween(0)
    /// The meshes turn on the time since this.
    @State private var appeared = Date()
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Under reduced motion the clock only runs while something is changing.
    @State private var stirring = false
    @State private var stirs = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    /// A card's soft shadow as a picture, made once for the card's size: blurring it anew on
    /// every frame costs far more than drawing it.
    @State private var shadow: Image? = nil

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let bottom = max(insets.bottom, 12) + Metrics.bottom
            let cardWidth = min(Metrics.cardMax, screen.width - Metrics.side * 2)
            let room = screen.height - insets.top - Metrics.header - Metrics.buttonGap - Metrics.button - bottom
            let cardHeight = max(0, min(room, cardWidth * Metrics.cardRatio))
            let frame = CGRect(x: (screen.width - cardWidth) / 2, y: insets.top + Metrics.header + (room - cardHeight) / 2, width: cardWidth, height: cardHeight)
            let step = cardWidth + Metrics.gap

            TimelineView(.animation(paused: reduceMotion && !stirring)) { timeline in
                let position = drag ?? travel.position(at: timeline.date)
                let seconds = reduceMotion ? 0 : timeline.date.timeIntervalSince(appeared)
                let covered = cover.value(at: timeline.date)
                // The button's colours follow the carousel: they are read off the ring of flavors at its position.
                let tint = tone(at: position) { $0.mid }
                let ink = tone(at: position) { $0.ink }
                // On a dark flavor the texts turn white as it covers the screen.
                let dark = Tone(flavors[chosen].deep).isDark

                ZStack {
                    Palette.background.ignoresSafeArea()

                    Canvas { context, _ in
                        draw(context, frame: frame, step: step, screen: screen, position: position, seconds: seconds, covered: covered)
                    }
                    .ignoresSafeArea()
                    .accessibilityHidden(true)

                    VStack(spacing: 0) {
                        Text(title)
                            .font(.system(size: 20, weight: .medium))
                            .foregroundStyle(Palette.title.mixed(dark ? Palette.onDark : Palette.title, covered).color)
                            .lineLimit(1)
                            .frame(height: Metrics.textLine)
                        Text(subtitle)
                            .font(.system(size: 16))
                            .lineSpacing(Metrics.subtitleLeading)
                            .padding(.vertical, Metrics.subtitleLeading / 2)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(Palette.subtitle.mixed(dark ? Palette.onDarkSubtitle : Palette.subtitle, covered).color)
                            .lineLimit(2)
                            .frame(height: Metrics.textLine * 2, alignment: .top)
                            .padding(.top, Metrics.textGap)
                            .padding(.horizontal, Metrics.textSide)

                        track(step: step)

                        button(tint: tint, ink: ink, covered: covered)
                            .padding(.top, Metrics.buttonGap / 2)
                            .padding(.bottom, bottom - insets.bottom)
                    }
                    .padding(.top, Metrics.top)
                }
            }
            .task(id: frame.size) { shadow = shadowImage(frame.size) }
        }
        .preferredColorScheme(chosenOn && Tone(flavors[chosen].deep).isDark ? .dark : .light)
        .onAppear { tapHaptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches, held: drag != nil)) { await loop() }
        .task(id: stirs) {
            guard stirs > 0 else { return }
            try? await Task.sleep(for: .seconds(M.apply))
            if !Task.isCancelled { stirring = false }
        }
    }

    // MARK: The parts

    /// The area a finger swipes in: the cards and a little under them.
    private func track(step: Double) -> some View {
        Color.clear
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if drag == nil {
                            grabbed = travel.position(at: .now)
                            touched()
                        }
                        drag = grabbed - value.translation.width / step
                    }
                    .onEnded { value in
                        let from = drag ?? grabbed
                        let velocity = min(M.throwMax, max(-M.throwMax, -value.velocity.width / step))
                        let to = Int((from + min(M.flickMax, max(-M.flickMax, velocity * M.flick))).rounded())
                        drag = nil
                        settle(from: from, to: to, velocity: velocity)
                        landed(to, byHand: true)
                        touched()
                    }
            )
            .allowsHitTesting(!chosenOn)
            .accessibilityElement()
            .accessibilityLabel("Flavor")
            .accessibilityValue(flavors[lap(centre)].name)
            .accessibilityAdjustableAction { direction in
                nudge(by: direction == .increment ? 1 : -1)
            }
    }

    private func button(tint: Tone, ink: Tone, covered: Double) -> some View {
        Button(action: select) {
            HStack(spacing: 8) {
                // A check in a ring; the ring fills once the screen has the flavor.
                ZStack {
                    Circle().fill(ink.color).opacity(covered)
                    Circle().stroke(ink.color, lineWidth: 1.5)
                    ThemeFlavorCarouselCheck()
                        .stroke(ink.color, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                        .opacity(1 - covered)
                        .frame(width: Metrics.glyph, height: Metrics.glyph)
                    ThemeFlavorCarouselCheck()
                        .stroke(.white, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                        .opacity(covered)
                        .frame(width: Metrics.glyph, height: Metrics.glyph)
                }
                .frame(width: 16.5, height: 16.5)
                .frame(width: Metrics.glyph, height: Metrics.glyph)
                Text(buttonLabel)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(ink.color)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .frame(height: Metrics.button)
            .background {
                Capsule().fill(tint.color.opacity(Metrics.glow)).blur(radius: Metrics.glowSigma).offset(y: Metrics.glowY)
                Capsule().fill(Tone.white.mixed(tint, Metrics.wash).color)
                Capsule().fill(LinearGradient(colors: [.white, .white.opacity(0)], startPoint: .top, endPoint: .bottom))
                Capsule().strokeBorder(tint.color.opacity(Metrics.borderAlpha), lineWidth: Metrics.border)
            }
            .contentShape(Capsule())
        }
        .buttonStyle(PressStyle())
        .padding(.horizontal, Metrics.buttonSide)
        .accessibilityAddTraits(chosenOn ? .isSelected : [])
    }

    /// The cards, then the chosen flavor's mesh growing from the card in the middle to the whole screen.
    private func draw(_ context: GraphicsContext, frame: CGRect, step: Double, screen: CGSize, position: Double, seconds: Double, covered: Double) {
        let count = Double(flavors.count)
        let card = Path(roundedRect: CGRect(origin: .zero, size: frame.size), cornerRadius: Metrics.radius, style: .circular)
        for (index, flavor) in flavors.enumerated() {
            let by = M.away(Double(index), position, count)
            // A card that is off the screen is not drawn.
            guard abs(by) * step - frame.width < screen.width / 2 else { continue }
            let aside = min(1, abs(by))
            let scale = 1 - Metrics.shrink * aside
            var layer = context
            layer.translateBy(x: frame.midX + by * step, y: frame.midY + Metrics.drop * aside)
            layer.scaleBy(x: scale, y: scale)
            layer.translateBy(x: -frame.width / 2, y: -frame.height / 2)
            let tone = Color(hex: flavor.mid, opacity: Metrics.shadow)
            if let shadow {
                var picture = layer.resolve(shadow)
                picture.shading = .color(tone)
                layer.draw(picture, in: CGRect(origin: .zero, size: frame.size).insetBy(dx: -Metrics.shadowRoom, dy: -Metrics.shadowRoom).offsetBy(dx: 0, dy: Metrics.shadowY))
            } else {
                layer.drawLayer { soft in
                    soft.addFilter(.blur(radius: Metrics.shadowSigma))
                    soft.fill(card.offsetBy(dx: 0, dy: Metrics.shadowY), with: .color(tone))
                }
            }
            layer.clip(to: card)
            Self.mesh(layer, flavor, index, seconds, frame.size)
        }

        guard covered > 0 else { return }
        // It grows from where its card is now, which may still be on its way to the middle. Under
        // reduced motion it does not grow: it fades in over the screen.
        let t = reduceMotion ? 1 : covered
        let by = M.away(Double(chosen), position, count)
        let aside = min(1, abs(by))
        let scale = 1 - Metrics.shrink * aside
        let from = CGRect(
            x: frame.midX + by * step - frame.width * scale / 2, y: frame.midY + Metrics.drop * aside - frame.height * scale / 2,
            width: frame.width * scale, height: frame.height * scale
        )
        let rect = CGRect(
            x: from.minX * (1 - t), y: from.minY * (1 - t),
            width: from.width + (screen.width - from.width) * t, height: from.height + (screen.height - from.height) * t
        )
        var faded = context
        faded.opacity = reduceMotion ? covered : 1
        faded.drawLayer { layer in
            layer.clip(to: Path(roundedRect: rect, cornerRadius: Metrics.radius * scale * (1 - t), style: .circular))
            layer.translateBy(x: rect.minX, y: rect.minY)
            layer.scaleBy(x: rect.width / frame.width, y: rect.height / frame.height)
            Self.mesh(layer, flavors[chosen], chosen, seconds, frame.size)
        }
    }

    /// A flavor's mesh, drawn from the corner of a card of the given size: a deep spot at the top
    /// and a pale one rising from the bottom left corner, both soft discs on the mid tone.
    private static func mesh(_ context: GraphicsContext, _ flavor: ThemeFlavorCarouselFlavor, _ index: Int, _ seconds: Double, _ size: CGSize) {
        let angle = seconds / M.driftTurn * tau + Double(index) * M.phase
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(hex: flavor.mid)))
        context.fillEllipse(
            center: CGPoint(x: Metrics.deepX * size.width + M.drift * cos(angle), y: Metrics.deepY * size.height + M.drift * sin(angle)),
            radii: CGSize(width: Metrics.deepRX * size.width, height: Metrics.deepRY * size.height), gradient: disc(flavor.deep)
        )
        let breath = 1 + M.breath * sin(angle)
        context.fillEllipse(
            center: CGPoint(x: Metrics.lightX * size.width, y: Metrics.lightY * size.height),
            radii: CGSize(width: Metrics.lightRX * size.width * breath, height: Metrics.lightRY * size.height * breath), gradient: disc(flavor.light)
        )
    }

    /// The blurred shape of a card with `shadowRoom` round it, to be filled with a flavor's colour.
    @MainActor private func shadowImage(_ size: CGSize) -> Image? {
        guard size.width > 0, size.height > 0 else { return nil }
        let renderer = ImageRenderer(
            content: RoundedRectangle(cornerRadius: Metrics.radius, style: .circular)
                .frame(width: size.width, height: size.height)
                .blur(radius: Metrics.shadowSigma)
                .padding(Metrics.shadowRoom)
        )
        renderer.scale = displayScale
        return renderer.uiImage.map { Image(uiImage: $0).renderingMode(.template) }
    }

    /// A soft disc of one colour, fading from its centre to its edge.
    private static func disc(_ hex: UInt32) -> Gradient {
        Gradient(stops: zip(Metrics.falloff, Metrics.falloffAt).map { Gradient.Stop(color: Color(hex: hex, opacity: $0), location: $1) })
    }

    // MARK: Behaviour

    private func lap(_ card: Int) -> Int {
        ((card % flavors.count) + flavors.count) % flavors.count
    }

    /// A colour of the flavors at a place on their ring, between the two nearest cards.
    private func tone(at position: Double, _ pick: (ThemeFlavorCarouselFlavor) -> UInt32) -> Tone {
        let place = M.lap(position, Double(flavors.count))
        let index = Int(place.rounded(.down)) % flavors.count
        return Tone(pick(flavors[index])).mixed(Tone(pick(flavors[(index + 1) % flavors.count])), place - place.rounded(.down))
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    /// Keeps the clock running for the length of a change when it is otherwise stopped.
    private func stir() {
        guard reduceMotion else { return }
        stirring = true
        stirs += 1
    }

    private func landed(_ to: Int, byHand: Bool) {
        guard to != centre else { return }
        centre = to
        if byHand { tapHaptic.impactOccurred() }
        onChange?(flavors[lap(to)], lap(to))
    }

    /// Lets the carousel go: it settles on a card with the spring, or in one short step.
    private func settle(from: Double, to: Int, velocity: Double) {
        // A throw that points away from the card it lands on is dropped.
        let towards = (Double(to) - from) * velocity >= 0
        travel = Travel(from: from, to: Double(to), velocity: towards ? velocity : 0, start: .now, kind: reduceMotion ? .jump : .spring)
        stir()
    }

    /// autoPlay's swipe to the next card. It is timed from the moment it was due, not from the
    /// moment the loop woke up for it, so that every round moves alike to the frame.
    private func swipe(to: Int, due: Date) {
        travel = Travel(from: travel.position(at: due), to: Double(to), velocity: 0, start: due, kind: reduceMotion ? .jump : .swipe)
        landed(to, byHand: false)
        stir()
    }

    /// One card on or back, for the accessibility action.
    private func nudge(by: Int) {
        guard !chosenOn else { return }
        touched()
        settle(from: travel.position(at: .now), to: centre + by, velocity: 0)
        landed(centre + by, byHand: true)
    }

    private func dress(_ on: Bool) {
        chosenOn = on
        cover.retarget(on ? 1 : 0, duration: reduceMotion ? M.jump : M.apply, curve: M.applyCurve)
        stir()
    }

    private func select() {
        touched()
        tapHaptic.impactOccurred()
        if chosenOn {
            dress(false)
            return
        }
        chosen = lap(centre)
        dress(true)
        onSelect?(flavors[chosen], chosen)
    }

    /// Back to the first flavor on a plain screen; answers the card the carousel is going to.
    private func rest() -> Int {
        dress(false)
        let from = travel.position(at: .now)
        let count = Double(flavors.count)
        let home = Int((from / count).rounded() * count)
        settle(from: from, to: home, velocity: 0)
        landed(home, byHand: false)
        return home
    }

    private func loop() async {
        guard autoPlay, drag == nil else { return }
        var home = 0
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            home = rest()
        }
        // One round of the flavors after another, timed from one origin so that rounds do not drift.
        let clock = ContinuousClock()
        var origin = clock.now
        var began = Date.now
        while !Task.isCancelled {
            for k in 0..<flavors.count {
                let due = Double(k) * M.step + M.rest
                try? await clock.sleep(until: origin + .seconds(due))
                guard !Task.isCancelled else { return }
                swipe(to: home + k + 1, due: began + due)
            }
            origin += .seconds(Double(flavors.count) * M.step)
            began += Double(flavors.count) * M.step
            home += flavors.count
            try? await clock.sleep(until: origin)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
        let held: Bool
    }

    private struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? Metrics.pressScale : 1)
                .animation(.easeInOut(duration: M.press), value: configuration.isPressed)
        }
    }
}

/// One move of the carousel, in cards: where it was, where it lands, and how it gets there.
private struct Travel {
    enum Kind {
        /// autoPlay: the pull of a finger, then the spring of a release.
        case swipe
        /// A release, with the speed the finger had.
        case spring
        /// Reduced motion: one short step.
        case jump
    }

    var from: Double
    var to: Double
    /// Cards a second at the start of a spring.
    var velocity: Double
    var start: Date
    var kind: Kind

    func position(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        switch kind {
        case .swipe:
            return t >= M.travel ? to : from + (to - from) * M.swipe(max(0, t))
        case .spring:
            return to + M.springAway(max(0, t), from: from - to, velocity: velocity)
        case .jump:
            let u = min(max(t / M.jump, 0), 1)
            return from + (to - from) * (u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u))
        }
    }
}

private enum M {
    /// autoPlay goes round the six flavors once; a flavor rests, then the carousel travels to the next.
    static let loop = 9.6
    static let step = 1.6
    static let rest = 0.8
    static let travel = 0.8
    /// autoPlay swipes like a finger: it pulls the cards `pullBy` of a card in `pull` seconds,
    /// speeding up, and lets go at that speed.
    static let pull = 0.26
    static let pullBy = 0.4
    /// What a released carousel settles with (damping ratio 0.8).
    static let mass = 1.0
    static let stiffness = 260.0
    static let damping = 25.8
    /// A flick carries the carousel this many seconds of travel ahead, and at most this many cards.
    static let flick = 0.15
    static let flickMax = 0.6
    /// The fastest a release may throw the cards, in cards a second.
    static let throwMax = 8.0
    /// After a touch autoPlay waits this long before it goes back to the first flavor.
    static let userHold = 3.0
    /// The screen takes a flavor: the card in the middle grows over it.
    static let apply = 0.6
    static let applyCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    /// Under reduced motion the carousel moves in one short step.
    static let jump = 0.22
    /// The deep spot goes round a circle of `drift` points, one turn in `driftTurn` seconds, while
    /// the pale one grows and shrinks by `breath`; each card is `phase` radians ahead of the one before.
    static let drift = 20.0
    static let driftTurn = 4.8
    static let breath = 0.06
    static let phase = 0.9
    static let press = 0.12

    /// A place on the ring of cards: 0 up to the number of cards.
    static func lap(_ value: Double, _ count: Double) -> Double {
        let rest = value.truncatingRemainder(dividingBy: count)
        return rest < 0 ? rest + count : rest
    }

    /// How far card `index` is from the middle, in cards, going round the short way.
    static func away(_ index: Double, _ position: Double, _ count: Double) -> Double {
        lap(index - position + count / 2, count) - count / 2
    }

    /// A released spring `t` seconds on: how far it still is from its target, having started `from` away at `velocity`.
    static func springAway(_ t: Double, from: Double, velocity: Double) -> Double {
        let omega = (stiffness / mass).squareRoot()
        let zeta = damping / (2 * (stiffness * mass).squareRoot())
        let omega1 = omega * (1 - zeta * zeta).squareRoot()
        return exp(-zeta * omega * t) * (from * cos(omega1 * t) + (velocity + zeta * omega * from) / omega1 * sin(omega1 * t))
    }

    /// autoPlay's swipe `t` seconds in, as a part of one card.
    static func swipe(_ t: Double) -> Double {
        if t < pull { return pullBy * (t / pull) * (t / pull) }
        return 1 + springAway(t - pull, from: pullBy - 1, velocity: 2 * pullBy / pull)
    }
}

/// A colour as three numbers, so that two can be mixed.
private struct Tone {
    var r: Double
    var g: Double
    var b: Double

    static let white = Tone(0xffffff)

    init(_ hex: UInt32) {
        r = Double((hex >> 16) & 0xff) / 255
        g = Double((hex >> 8) & 0xff) / 255
        b = Double(hex & 0xff) / 255
    }

    init(r: Double, g: Double, b: Double) {
        self.r = r
        self.g = g
        self.b = b
    }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b) }
    var isDark: Bool { 0.2126 * r + 0.7152 * g + 0.0722 * b < 0.6 }

    func mixed(_ other: Tone, _ amount: Double) -> Tone {
        Tone(r: r + (other.r - r) * amount, g: g + (other.g - g) * amount, b: b + (other.b - b) * amount)
    }
}

private enum Palette {
    static let background = Color(hex: 0xfdfdfd)
    static let title = Tone(0x1c1c1e)
    static let subtitle = Tone(0x3c3c43)
    /// The texts once the screen has taken a dark flavor.
    static let onDark = Tone(0xffffff)
    static let onDarkSubtitle = Tone(0xe9eef2)
}

private enum Metrics {
    /// From the top of the safe area: the title, the two lines under it, then the cards.
    static let top = 12.0
    static let textLine = 24.0
    static let textGap = 8.0
    static let textSide = 40.0
    static let header = top + textLine + textGap + textLine * 2 + 21
    /// What a 16 point line lacks to be `textLine` tall.
    static let subtitleLeading = textLine - UIFont.systemFont(ofSize: 16).lineHeight
    static let side = 50.0
    static let cardMax = 340.0
    /// A card is this much taller than wide when the screen has the room.
    static let cardRatio = 503.0 / 292.0
    static let radius = 34.0
    static let gap = 20.0
    /// A card next to the middle one is smaller and sits lower.
    static let shrink = 0.04
    static let drop = 8.0
    static let shadowY = 12.0
    static let shadowSigma = 15.0
    static let shadow = 0.18
    /// How far past the card its shadow's picture reaches.
    static let shadowRoom = 48.0
    static let button = 52.0
    static let buttonSide = 51.0
    static let buttonGap = 70.0
    static let bottom = 22.0
    static let border = 1.5
    static let borderAlpha = 0.7
    /// How much of the flavor the bottom of the button takes; its top stays white.
    static let wash = 0.3
    static let glowY = 6.0
    static let glowSigma = 7.0
    static let glow = 0.35
    static let glyph = 20.0
    static let pressScale = 0.96

    /// The mesh of a card, in fractions of its width and height.
    static let deepX = 0.5
    static let deepY = 0.12
    static let deepRX = 1.3
    static let deepRY = 0.56
    static let lightX = 0.04
    static let lightY = 1.02
    static let lightRX = 1.35
    static let lightRY = 0.84
    /// How a disc fades from its centre to its edge.
    static let falloff = [1, 0.9, 0.5, 0.1, 0]
    static let falloffAt = [0, 0.25, 0.5, 0.75, 1]
}

/// A check in a 20 × 20 box.
struct ThemeFlavorCarouselCheck: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 6.3 * s, y: rect.minY + 10.3 * s))
        path.addLine(to: CGPoint(x: rect.minX + 8.9 * s, y: rect.minY + 12.8 * s))
        path.addLine(to: CGPoint(x: rect.minX + 13.8 * s, y: rect.minY + 7.5 * s))
        return path
    }
}

#Preview {
    ThemeFlavorCarousel(autoPlay: true)
}
