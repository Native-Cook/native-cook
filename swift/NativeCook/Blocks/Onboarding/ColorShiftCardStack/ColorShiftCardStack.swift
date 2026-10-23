import SwiftUI

enum ColorShiftCardStackGlyph {
    case people
    case chart
    case star
    case play
    case download
}

struct ColorShiftCardStackCard: Equatable {
    /// Two or three lines; the last word of the last line is written in the card's colour.
    var lines: [String]
    /// The colour of that word, of the glyph and of the whole screen while the card is on top.
    var color: UInt32
    var glyph: ColorShiftCardStackGlyph

    static let samples = [
        ColorShiftCardStackCard(lines: ["two", "hundred", "nineteen followers"], color: 0x1986ef, glyph: .people),
        ColorShiftCardStackCard(lines: ["five thousand", "thirty two MRR"], color: 0xee424a, glyph: .chart),
        ColorShiftCardStackCard(lines: ["one thousand", "fifty three stars"], color: 0x1c7132, glyph: .star),
        ColorShiftCardStackCard(lines: ["ten thousand", "eight hundred", "subscribers"], color: 0xf5333f, glyph: .play),
        ColorShiftCardStackCard(lines: ["six thousand", "fifty eight", "downloads"], color: 0xa7a0a2, glyph: .download),
    ]
}

/// A typographic welcome on one flat colour, with a small stack of white cards to flick through.
/// Each card that comes to the top gives the whole screen its colour: the background blends to it
/// and the headline's dark, which is made from the background, follows.
struct ColorShiftCardStack: View {
    /// The cards, four or more; after the last comes the first again.
    var cards = ColorShiftCardStackCard.samples
    /// The headline's lines, and the one word of it that is white.
    var headline = ["Thanks", "for being", "an early", "adopter"]
    var highlight = "early"
    var bodyText = "Phase one, starts with a preconfigured\nfeed integration. We\u{2019}ll add more over time."
    var buttonLabel = "Sign in"
    /// Flicks through the cards by itself, one every 1.7 s: `loop` seconds for the five it comes with.
    var autoPlay = false
    /// Called when another card comes to the top.
    var onChange: ((ColorShiftCardStackCard, Int) -> Void)? = nil
    var onSignIn: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Which card is in the hand, and how far it has left: 2.25 is card 2, a quarter gone.
    @State private var position = Glide(0)
    /// The place the stack behind it has moved up to.
    @State private var rise = Glide(0)
    /// The place on the ring of colours the screen is at.
    @State private var shade = Glide(0)
    /// Where the three are heading: the card on top, or on its way there. It keeps counting round the ring.
    @State private var heading = 0
    /// The three while a finger holds the card, and what they were when it took it.
    @State private var held: Held? = nil
    @State private var grabbed = Held(position: 0, rise: 0, shade: 0)
    /// The float and the shimmer run on the time since this.
    @State private var appeared = Date()
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Under reduced motion the clock only runs while something is changing.
    @State private var stirring = false
    @State private var stirs = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private struct Held: Equatable {
        var position: Double
        var rise: Double
        var shade: Double
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let short = proxy.size.height + insets.top + insets.bottom < Metrics.short
            let cardWidth = min(Metrics.cardMax, proxy.size.width - Metrics.cardSide * 2)

            TimelineView(.animation(paused: reduceMotion && !stirring)) { timeline in
                let now = timeline.date
                let color = blend(held?.shade ?? shade.value(at: now))
                let ink = color.ink
                let wave = reduceMotion ? 0 : now.timeIntervalSince(appeared) / M.wave * tau

                ZStack {
                    color.color.ignoresSafeArea()

                    VStack(alignment: .leading, spacing: 0) {
                        Spacer(minLength: 0)
                        VStack(alignment: .leading, spacing: 0) {
                            ForEach(headline, id: \.self) { line in
                                headlineLine(line, ink: ink.color, shimmer: 1 - M.shimmer * (0.5 - 0.5 * cos(wave)))
                                    .font(.system(size: short ? Metrics.headlineShort : Metrics.headline, weight: .heavy))
                                    .tracking(-1.5)
                                    .lineLimit(1)
                                    .frame(height: short ? Metrics.headlineLineShort : Metrics.headlineLine)
                            }
                        }
                        .padding(.horizontal, Metrics.side)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(headline.joined(separator: " "))
                        .accessibilityAddTraits(.isHeader)

                        Text(bodyText)
                            .font(.system(size: 13))
                            .lineSpacing(Metrics.bodyLeading)
                            .padding(.vertical, Metrics.bodyLeading / 2)
                            .foregroundStyle(ink.color.opacity(Metrics.bodyAlpha))
                            .lineLimit(2)
                            .padding(.horizontal, Metrics.side)
                            .padding(.top, Metrics.bodyGap)

                        stack(position: held?.position ?? position.value(at: now), rise: held?.rise ?? rise.value(at: now), float: Metrics.float * sin(wave))
                            .frame(width: cardWidth, height: Metrics.cardHeight)
                            .frame(maxWidth: .infinity)
                            .padding(.top, Metrics.stackGap)

                        Button {
                            touched()
                            tapHaptic.impactOccurred()
                            onSignIn?()
                        } label: {
                            Text(buttonLabel)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Palette.cardInk)
                                .frame(width: Metrics.buttonWidth, height: Metrics.button)
                                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(.white).shadow(color: .black.opacity(0.15), radius: 5, y: 4))
                        }
                        .buttonStyle(PressStyle())
                        .frame(maxWidth: .infinity)
                        .padding(.top, Metrics.buttonGap)
                    }
                    .padding(.bottom, max(insets.bottom, 12) + Metrics.bottom - insets.bottom)
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { tapHaptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches, held: held != nil)) { await loop() }
        .task(id: stirs) {
            guard stirs > 0 else { return }
            try? await Task.sleep(for: .seconds(1))
            if !Task.isCancelled { stirring = false }
        }
    }

    // MARK: The parts

    private func headlineLine(_ line: String, ink: Color, shimmer: Double) -> Text {
        guard !highlight.isEmpty, let range = line.range(of: highlight) else { return Text(line).foregroundColor(ink) }
        return Text(line[..<range.lowerBound]).foregroundColor(ink)
            + Text(highlight).foregroundColor(.white.opacity(shimmer))
            + Text(line[range.upperBound...]).foregroundColor(ink)
    }

    /// The cards. The one `position` points at is in the hand: it leaves to the left as the position
    /// moves on. The others wait behind it, as many places down as they are behind `rise`.
    private func stack(position: Double, rise: Double, float: Double) -> some View {
        let count = Double(cards.count)
        return ZStack {
            ForEach(cards.indices, id: \.self) { index in
                let gone = M.behind(Double(index), position, count)
                let inHand = gone <= 0
                let down = inHand ? 0 : M.behind(Double(index), rise, count)
                let depth = min(down, 2)
                face(cards[index], down: down)
                    .scaleEffect(1 - Metrics.depthShrink * depth)
                    .rotationEffect(.degrees(inHand ? Metrics.turn * gone : Metrics.depthTurn * depth))
                    .offset(
                        x: inHand ? gone * Metrics.leave : Metrics.depthX * depth,
                        y: inHand ? float : Metrics.depthY * depth + float * min(1, max(0, 1 - down))
                    )
                    // A leaving card fades over the last third of its way; the card at the back of
                    // the ring comes into view as it reaches the third place.
                    .opacity(inHand ? 1 - min(1, max(0, (-gone - Metrics.fadeFrom) / (1 - Metrics.fadeFrom))) : min(1, max(0, 3 - down)))
                    .zIndex(inHand ? 100 : 90 - down * 10)
            }
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    if held == nil {
                        let now = Date.now
                        grabbed = Held(position: self.position.value(at: now), rise: self.rise.value(at: now), shade: shade.value(at: now))
                        touched()
                    }
                    // To the left the card in the hand leaves; to the right the one before it comes back.
                    let by = -value.translation.width / Metrics.leave
                    func within(_ place: Double) -> Double { min(Double(heading + 1), max(Double(heading - 1), place)) }
                    held = Held(position: within(grabbed.position + by), rise: within(grabbed.rise + by), shade: within(grabbed.shade + by))
                }
                .onEnded { value in
                    let by = -value.translation.width
                    let speed = -value.velocity.width
                    let to = heading + (by > M.commit || speed > M.fling ? 1 : by < -M.commit || speed < -M.fling ? -1 : 0)
                    release(to: to, speed: speed / Metrics.leave)
                }
        )
        .accessibilityElement()
        .accessibilityLabel(cards[lap(heading)].lines.joined(separator: " "))
        .accessibilityAdjustableAction { direction in
            touched()
            flick(to: heading + (direction == .increment ? 1 : -1), due: .now)
        }
    }

    private func face(_ card: ColorShiftCardStackCard, down: Double) -> some View {
        let lines = card.lines
        let last = lines.last ?? ""
        let cut = last.lastIndex(of: " ").map { last.index(after: $0) } ?? last.startIndex
        let tone = down < 1 ? Palette.faces[0].mixed(Palette.faces[1], max(0, down)) : Palette.faces[1].mixed(Palette.faces[2], min(1, down - 1))
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(lines.dropLast(), id: \.self) { line in
                Text(line).frame(height: 32)
            }
            (Text(last[..<cut]) + Text(last[cut...]).foregroundColor(Color(hex: card.color))).frame(height: 32)
        }
        .font(.system(size: 23, weight: .bold))
        .tracking(-0.4)
        .foregroundStyle(Palette.cardInk)
        .lineLimit(1)
        .padding(.leading, 16)
        .padding(.bottom, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .overlay(alignment: .topTrailing) {
            ColorShiftCardStackGlyphView(glyph: card.glyph, color: Color(hex: card.color), face: Palette.faces[0].color)
                .frame(width: Metrics.glyph, height: Metrics.glyph)
                .padding(.top, 12)
                .padding(.trailing, 22)
        }
        .background(
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                .fill(tone.color)
                // Only the two cards on top throw a shadow.
                .shadow(color: .black.opacity(0.22 * min(1, max(0, 2 - down))), radius: 12, y: 10)
        )
    }

    // MARK: Behaviour

    private func lap(_ place: Int) -> Int {
        ((place % cards.count) + cards.count) % cards.count
    }

    /// The screen's colour at a place on the ring of cards: a plain blend of the two nearest.
    private func blend(_ at: Double) -> Tone {
        let place = M.lap(at, Double(cards.count))
        let index = Int(place.rounded(.down)) % cards.count
        return Tone(cards[index].color).mixed(Tone(cards[(index + 1) % cards.count].color), place - place.rounded(.down))
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
        guard to != heading else { return }
        heading = to
        if byHand { tapHaptic.impactOccurred() }
        onChange?(cards[lap(to)], lap(to))
    }

    /// autoPlay's flick: the card in the hand leaves, the stack moves up and the colours change.
    /// It is timed from the moment it was due, so that every round moves alike to the frame.
    private func flick(to: Int, due: Date) {
        let place = Double(to)
        if reduceMotion {
            position.set(place)
            rise.set(place)
            shade.go(to: place, at: due, .ease(M.jump, .linear))
        } else {
            position.go(to: place, at: due, .ease(M.flick, M.flickCurve))
            rise.go(to: place, at: due, M.riseSpring)
            shade.go(to: place, at: due, .ease(M.shift, .linear))
        }
        landed(to, byHand: false)
        stir()
    }

    /// Lets the card go where the drag sent it.
    private func release(to: Int, speed: Double) {
        let from = held ?? grabbed
        let place = Double(to)
        let now = Date.now
        held = nil
        shade.set(from.shade)
        if reduceMotion {
            position.set(place)
            rise.set(place)
            shade.go(to: place, at: now, .ease(M.jump, .linear))
        } else {
            position.set(from.position)
            rise.set(from.rise)
            // A throw that points away from where the card is sent is dropped.
            position.go(to: place, at: now, M.snapSpring, velocity: (place - from.position) * speed >= 0 ? speed : 0)
            rise.go(to: place, at: now, M.riseSpring)
            shade.go(to: place, at: now, .ease(max(0.001, M.shift * min(1, abs(place - from.shade))), .linear))
        }
        landed(to, byHand: true)
        touched()
        stir()
    }

    private func loop() async {
        guard autoPlay, held == nil else { return }
        var home = 0
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // Back to the first card.
            let count = Double(cards.count)
            home = Int((position.value(at: .now) / count).rounded() * count)
            flick(to: home, due: .now)
        }
        // One round of the cards after another, timed from one origin so that rounds do not drift.
        let clock = ContinuousClock()
        var origin = clock.now
        var began = Date.now
        while !Task.isCancelled {
            for k in 0..<cards.count {
                let due = Double(k) * M.step + M.rest
                try? await clock.sleep(until: origin + .seconds(due))
                guard !Task.isCancelled else { return }
                flick(to: home + k + 1, due: began + due)
            }
            origin += .seconds(Double(cards.count) * M.step)
            began += Double(cards.count) * M.step
            home += cards.count
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

/// A number on its way to a target, read from the time since it set off: a spring, which keeps
/// the speed it had when it is sent somewhere else, or an ease of a given length.
private struct Glide {
    enum Mode {
        /// For a mass of 1.
        case spring(stiffness: Double, damping: Double)
        case ease(Double, UnitCurve)
    }

    private var from: Double
    private var to: Double
    private var velocity = 0.0
    private var start = Date.distantPast
    private var mode = Mode.ease(0, .linear)

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        let t = max(0, date.timeIntervalSince(start))
        switch mode {
        case .ease(let duration, let curve):
            return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
        case .spring(let stiffness, let damping):
            let rate = damping / 2
            let omega1 = (stiffness - rate * rate).squareRoot()
            let away = from - to
            return to + exp(-rate * t) * (away * cos(omega1 * t) + (velocity + rate * away) / omega1 * sin(omega1 * t))
        }
    }

    private func speed(at date: Date) -> Double {
        guard case .spring(let stiffness, let damping) = mode else { return 0 }
        let t = max(0, date.timeIntervalSince(start))
        let rate = damping / 2
        let omega1 = (stiffness - rate * rate).squareRoot()
        let away = from - to
        let pull = velocity + rate * away
        return exp(-rate * t) * ((pull - rate * away) * cos(omega1 * t) - (rate * pull / omega1 + away * omega1) * sin(omega1 * t))
    }

    /// Puts the number somewhere, at rest.
    mutating func set(_ value: Double) {
        from = value
        to = value
        velocity = 0
        start = .distantPast
        mode = .ease(0, .linear)
    }

    /// Sends the number to a target from where it is at `date`; a spring starts at `velocity`, or
    /// at the speed the number has.
    mutating func go(to target: Double, at date: Date, _ mode: Mode, velocity: Double? = nil) {
        let now = value(at: date)
        self.velocity = velocity ?? speed(at: date)
        from = now
        to = target
        start = date
        self.mode = mode
    }
}

private enum M {
    /// autoPlay flicks the top card away every `step` seconds, after `rest` of rest.
    static let loop = 8.5
    static let step = 1.7
    static let rest = 1.1
    /// The flick: the card is gone in `flick`, the colours have changed in `shift`.
    static let flick = 0.35
    static let flickCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.42, y: 0), endControlPoint: UnitPoint(x: 1, y: 1))
    static let shift = 0.5
    /// The stack moves up a place with a small overshoot (damping ratio 0.7).
    static let riseSpring = Glide.Mode.spring(stiffness: 200, damping: 19.8)
    /// A released card goes where it is sent without a bounce.
    static let snapSpring = Glide.Mode.spring(stiffness: 220, damping: 26.5)
    /// A card dragged this far, or let go this fast (points a second), leaves; otherwise it comes back.
    static let commit = 90.0
    static let fling = 600.0
    /// After a touch autoPlay waits this long before it goes back to the first card.
    static let userHold = 3.0
    /// Under reduced motion the cards change at once and the colours in one short step.
    static let jump = 0.25
    /// The top card floats, and the white word dims, once every `wave` seconds.
    static let wave = 1.7
    static let shimmer = 0.15
    static let press = 0.12

    /// A place on the ring of cards: 0 up to the number of cards.
    static func lap(_ value: Double, _ count: Double) -> Double {
        let rest = value.truncatingRemainder(dividingBy: count)
        return rest < 0 ? rest + count : rest
    }

    /// How many places card `index` is behind `position`: from -1, just gone, up to the last place in the ring.
    static func behind(_ index: Double, _ position: Double, _ count: Double) -> Double {
        lap(index - position + 1, count) - 1
    }
}

/// A colour as three numbers, so that two can be mixed.
private struct Tone {
    var r: Double
    var g: Double
    var b: Double

    init(_ hex: UInt32) {
        r = Double((hex >> 16) & 0xff) / 255
        g = Double((hex >> 8) & 0xff) / 255
        b = Double(hex & 0xff) / 255
    }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b) }

    /// The dark of the headline, made from the screen's colour: a part of it, plus a part of its
    /// brightness on every channel.
    var ink: Tone {
        let light = 0.2126 * r + 0.7152 * g + 0.0722 * b
        var tone = self
        tone.r = Metrics.inkKeep * r + Metrics.inkLight * light
        tone.g = Metrics.inkKeep * g + Metrics.inkLight * light
        tone.b = Metrics.inkKeep * b + Metrics.inkLight * light
        return tone
    }

    func mixed(_ other: Tone, _ amount: Double) -> Tone {
        var tone = self
        tone.r += (other.r - r) * amount
        tone.g += (other.g - g) * amount
        tone.b += (other.b - b) * amount
        return tone
    }
}

private enum Palette {
    static let cardInk = Color(hex: 0x1c1c1e)
    /// A card's face on top of the stack, one place down and two places down.
    static let faces = [Tone(0xfffdff), Tone(0xf0f0f2), Tone(0xe4e4e8)]
}

private enum Metrics {
    static let inkKeep = 0.2
    static let inkLight = 0.14
    static let bodyAlpha = 0.8
    static let side = 24.0
    static let headline = 50.0
    static let headlineLine = 68.0
    /// On a short screen the headline is set smaller.
    static let short = 700.0
    static let headlineShort = 44.0
    static let headlineLineShort = 60.0
    static let bodyGap = 17.0
    /// What a 13 point line lacks to be 18 tall.
    static let bodyLeading = 18 - UIFont.systemFont(ofSize: 13).lineHeight
    static let stackGap = 35.0
    static let cardSide = 42.0
    static let cardMax = 340.0
    static let cardHeight = 142.0
    static let cardRadius = 20.0
    static let glyph = 16.0
    static let buttonGap = 46.0
    static let buttonWidth = 120.0
    static let button = 46.0
    static let bottom = 26.0
    static let pressScale = 0.96
    /// A card leaving to the left goes this far, turns this much and fades over the last third of the way.
    static let leave = 340.0
    static let turn = 8.0
    static let fadeFrom = 2.0 / 3.0
    /// Each place down the stack: turned, moved right and up, and smaller.
    static let depthTurn = 2.0
    static let depthX = 6.0
    static let depthY = -3.0
    static let depthShrink = 0.04
    /// The top card floats this far up and down.
    static let float = 2.0
}

/// A card's glyph in a 16 × 16 box, in the card's colour with details in the colour of its face.
struct ColorShiftCardStackGlyphView: View {
    let glyph: ColorShiftCardStackGlyph
    let color: Color
    let face: Color

    var body: some View {
        Canvas { context, size in
            var context = context
            context.scaleBy(x: size.width / 16, y: size.height / 16)
            func disc(_ x: Double, _ y: Double, _ r: Double, _ fill: Color) {
                context.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(fill))
            }
            func block(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ fill: Color) {
                context.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular), with: .color(fill))
            }
            func line(_ points: [(Double, Double)], closed: Bool = false) -> Path {
                var path = Path()
                path.addLines(points.map { CGPoint(x: $0.0, y: $0.1) })
                if closed { path.closeSubpath() }
                return path
            }
            switch glyph {
            case .people:
                disc(11.6, 5.3, 2.1, color)
                block(9.4, 9.2, 6, 4.8, 2.4, color)
                disc(5.6, 4.5, 3.3, face)
                block(0.2, 7.8, 10.8, 7, 3.5, face)
                disc(5.6, 4.5, 2.5, color)
                block(1, 8.6, 9.2, 5.4, 2.7, color)
            case .chart:
                block(0.5, 0.5, 15, 15, 4, color)
                block(4, 8.5, 2, 4, 1, face)
                block(7, 4.5, 2, 8, 1, face)
                block(10, 6.5, 2, 6, 1, face)
            case .star:
                // Ten corners round (8, 8.4): outer 7.6, inner 3.2.
                let corners = (0..<10).map { corner -> (Double, Double) in
                    let radius = corner % 2 == 0 ? 7.6 : 3.2
                    let angle = Double(corner) * .pi / 5 - .pi / 2
                    return (8 + radius * cos(angle), 8.4 + radius * sin(angle))
                }
                context.fill(line(corners, closed: true), with: .color(color))
            case .play:
                block(0.5, 2.5, 15, 11, 3.2, color)
                context.fill(line([(6.4, 5.4), (10.8, 8), (6.4, 10.6)], closed: true), with: .color(face))
            case .download:
                disc(8, 8, 7.5, color)
                let stroke = StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round)
                context.stroke(line([(8, 4.2), (8, 11.2)]), with: .color(face), style: stroke)
                context.stroke(line([(5.2, 8.6), (8, 11.4), (10.8, 8.6)]), with: .color(face), style: stroke)
            }
        }
        .accessibilityHidden(true)
    }
}

#Preview {
    ColorShiftCardStack(autoPlay: true)
}
