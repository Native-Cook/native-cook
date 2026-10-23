import SwiftUI

struct RisingTourDestination {
    /// Set in huge capitals above the stack.
    var city: String
    var title: String
    var text: String
    var rating: String
    var price: String
    /// Address of the photo, and the flat colour shown until it arrives.
    var photo: String
    var tint: Color
}

/// A dark panel with a destination in huge condensed serif capitals and a stack of three tour cards.
/// The deck is dealt on appear: the letters arrive one after another, the front card rises from the
/// bottom edge, the two cards behind it pop up and the buttons fade in. Swiping the front card up
/// sends it away and brings the next destination forward; after the last one the deck is dealt again.
struct RisingTourStack: View {
    var destinations = risingTourDestinations
    /// Swipes through the destinations by itself and deals the deck again, in a loop.
    var autoPlay = false
    var heading = "Your Destination"
    var availableLabel = "Available"
    var mapLabel = "See on Map"
    /// Called with the index of the destination that comes to the front.
    var onChange: ((Int) -> Void)? = nil
    /// Called with the destination's index and whether it is now liked.
    var onLike: ((Int, Bool) -> Void)? = nil
    var onMap: ((Int) -> Void)? = nil
    var onBook: ((Int) -> Void)? = nil
    var onBack: (() -> Void)? = nil
    var onSearch: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Index of the card at the front; fractional while one is on its way out.
    @State private var front = RisingTourSpring(0)
    /// The card the stack is heading for; `destinations.count` once the last one is thrown.
    @State private var at = 0
    @State private var dealStart = Date.distantPast
    @State private var typeStart = Date.distantPast
    @State private var nameIndex = 0
    @State private var prevIndex = -1
    @State private var liked: Set<Int> = []
    @State private var heartStart = Date.distantPast
    @State private var dragging = false
    /// Keeps the timeline running while something moves.
    @State private var active = false
    @State private var wakes = 0
    /// Counts thrown decks: a change deals the next one after a pause.
    @State private var finishes = 0
    /// Counts touches: a change restarts the autoPlay loop after a pause.
    @State private var touches = 0
    @State private var thumps = 0

    private var auto: Bool { autoPlay && !reduceMotion }
    private var count: Int { destinations.count }

    private struct Run: Equatable {
        var auto: Bool
        var touches: Int
    }

    var body: some View {
        TimelineView(.animation(paused: !active)) { timeline in
            let now = timeline.date
            let f = front.value(at: now)
            let deal = min(Metrics.dealSeconds, now.timeIntervalSince(dealStart))
            let type = min(Metrics.typeSeconds, now.timeIntervalSince(typeStart))
            let shown = min(max(0, at), count - 1)

            ZStack {
                LinearGradient(colors: [Color(hex: 0x2b3238), Color(hex: 0x1c2126)], startPoint: .top, endPoint: .bottom)

                topBar

                // The name: the previous one fades while the letters of the next arrive one by one.
                ZStack {
                    if prevIndex >= 0, prevIndex < count {
                        name(destinations[prevIndex].city, type: nil)
                            .opacity(1 - min(1, type / Metrics.nameFade))
                    }
                    if nameIndex >= 0, nameIndex < count {
                        name(destinations[nameIndex].city, type: type)
                    }
                }
                .scaleEffect(x: Metrics.nameSqueeze, y: 1)
                .position(x: Metrics.width / 2, y: Metrics.nameY)
                .accessibilityHidden(true)

                ForEach(Array(destinations.enumerated().reversed()), id: \.offset) { i, item in
                    let pose = risingTourPose(i, front: f, deal: deal, reduced: reduceMotion)
                    card(item)
                        .overlay(
                            ZStack {
                                RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular).fill(Metrics.coverNear)
                                RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular)
                                    .fill(Metrics.coverFar)
                                    .opacity(pose.coverFar)
                            }
                            .opacity(pose.cover)
                        )
                        .shadow(color: .black.opacity(0.22), radius: 14, y: 10)
                        .scaleEffect(pose.scale)
                        .rotationEffect(.degrees(pose.rotation))
                        .offset(y: pose.shift)
                        .opacity(pose.opacity)
                        .position(x: Metrics.width / 2, y: Metrics.cardTop + Metrics.cardHeight / 2)
                }
                .accessibilityHidden(true)

                // Swiping up anywhere on the stack throws the front card.
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: Metrics.width, height: Metrics.buttonsTop - Metrics.stackTop)
                    .position(x: Metrics.width / 2, y: (Metrics.stackTop + Metrics.buttonsTop) / 2)
                    .gesture(drag)
                    .accessibilityElement()
                    .accessibilityLabel(Text(verbatim: "\(destinations[shown].city): \(destinations[shown].title), \(destinations[shown].price)"))
                    .accessibilityAdjustableAction { direction in
                        guard direction == .increment else { return }
                        touches += 1
                        advance(report: true)
                    }

                buttons(shown: shown, now: now)
                    .opacity(risingTourButtons(front: f, deal: deal, count: count))
                    .offset(y: reduceMotion ? 0 : (1 - risingTourButtons(front: f, deal: deal, count: count)) * Metrics.buttonsRise)
                    .position(x: Metrics.width / 2, y: Metrics.buttonsTop + Metrics.buttonHeight / 2)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular))
            .shadow(color: .black.opacity(0.22), radius: 24, y: 16)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: thumps)
        .onAppear {
            // A self-playing deck starts dealt, so its loop begins and ends on the same frame.
            if !auto { deal() }
        }
        .task(id: wakes) {
            guard active else { return }
            try? await Task.sleep(for: .seconds(Metrics.dealSeconds + 1))
            if !Task.isCancelled { active = false }
        }
        .task(id: finishes) {
            guard finishes > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.dealPause))
            if !Task.isCancelled { deal() }
        }
        .task(id: Run(auto: auto, touches: touches)) { await play() }
    }

    private var topBar: some View {
        HStack {
            Button {
                onBack?()
            } label: {
                risingTourPath(Metrics.backIcon)
                    .stroke(.white, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                    .frame(width: 20, height: 20)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Back")
            Spacer()
            Button {
                onSearch?()
            } label: {
                risingTourPath(Metrics.searchIcon)
                    .stroke(.white, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                    .frame(width: 20, height: 20)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Search")
        }
        .buttonStyle(RisingTourPress())
        .overlay(
            Text(heading)
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(.white.opacity(0.9))
        )
        .frame(width: Metrics.width - 32, height: 36)
        .position(x: Metrics.width / 2, y: Metrics.barY)
    }

    /// The city in capitals, one view per letter. With `type` (seconds) each letter fades in while
    /// it slides into place, one after another.
    private func name(_ city: String, type: Double?) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(city.enumerated()), id: \.offset) { order, letter in
                let e = type.map { risingTourLetter($0, order: order) } ?? 1
                Text(String(letter))
                    .font(.system(size: Metrics.nameSize, weight: .regular, design: .serif))
                    .foregroundStyle(.white)
                    .opacity(e)
                    .offset(
                        x: reduceMotion ? 0 : (1 - e) * Metrics.letterFrom.width,
                        y: reduceMotion ? 0 : (1 - e) * Metrics.letterFrom.height
                    )
            }
        }
        .fixedSize()
        .frame(height: Metrics.nameLine)
    }

    private func card(_ item: RisingTourDestination) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular).fill(.white)

            RisingTourPhoto(url: item.photo, tint: item.tint)
                .frame(width: Metrics.cardWidth - Metrics.photoInset * 2, height: Metrics.photoHeight)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.photoRadius, style: .circular))
                .offset(x: Metrics.photoInset, y: Metrics.photoInset)

            HStack(spacing: 5) {
                risingTourPath(Metrics.starIcon)
                    .fill(Metrics.star)
                    .frame(width: 16, height: 16)
                Text(item.rating)
                    .font(.system(size: 12, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(.white)
            }
            .frame(width: 60, height: 28)
            .background(Capsule().fill(Color.rgba(38, 42, 46, 0.6)))
            .offset(x: 14, y: 14)

            Text(item.title)
                .font(.system(size: 15, weight: .semibold))
                .tracking(-0.2)
                .foregroundStyle(Color(hex: 0x14171a))
                .lineLimit(2)
                .frame(width: 200, height: 36, alignment: .topLeading)
                .offset(x: 20, y: 196)

            Text(item.price)
                .font(.system(size: 14, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Color(hex: 0x14171a))
                .frame(width: 62, height: 30)
                .background(Capsule().fill(Color(hex: 0xeeeff0)))
                .offset(x: Metrics.cardWidth - 20 - 62, y: 199)

            Text(item.text)
                .font(.system(size: 10, weight: .regular))
                .lineSpacing(2)
                .foregroundStyle(Color(hex: 0x6a7076))
                .lineLimit(2)
                .frame(width: Metrics.cardWidth - 40, height: 28, alignment: .topLeading)
                .offset(x: 20, y: 243)
        }
        .frame(width: Metrics.cardWidth, height: Metrics.cardHeight)
    }

    private func buttons(shown: Int, now: Date) -> some View {
        let isLiked = liked.contains(shown)
        let pop = reduceMotion ? 1 : 0.7 + 0.3 * risingTourSpringAt(now.timeIntervalSince(heartStart), stiffness: Metrics.heartStiffness, damping: Metrics.heartDamping)
        return HStack(spacing: 8) {
            Button {
                onBook?(shown)
            } label: {
                Text(availableLabel)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color(hex: 0x86e3a6))
                    .frame(width: 88, height: Metrics.buttonHeight)
                    .background(Capsule().fill(Color(hex: 0x1d3b2b)))
            }
            Button {
                onMap?(shown)
            } label: {
                HStack(spacing: 9) {
                    risingTourPath(Metrics.mapIcon)
                        .stroke(.white, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                        .frame(width: 18, height: 18)
                        .frame(width: 32, height: 32)
                        .background(Circle().fill(Color(hex: 0x474e55)))
                    Text(mapLabel)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.white)
                    Spacer(minLength: 0)
                }
                .padding(.leading, 6)
                .frame(width: 140, height: Metrics.buttonHeight)
                .background(Capsule().fill(Color(hex: 0x30363c)))
            }
            Button {
                touches += 1
                thumps += 1
                if isLiked { liked.remove(shown) } else { liked.insert(shown) }
                heartStart = .now
                wake()
                onLike?(shown, !isLiked)
            } label: {
                ZStack {
                    risingTourPath(Metrics.heartIcon).fill(Metrics.heart.opacity(isLiked ? 1 : 0))
                    risingTourPath(Metrics.heartIcon)
                        .stroke(isLiked ? Metrics.heart : .white, style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
                }
                .frame(width: 20, height: 20)
                .scaleEffect(pop)
                .frame(width: Metrics.buttonHeight, height: Metrics.buttonHeight)
                .background(Circle().fill(Color(hex: 0x30363c)))
            }
            .accessibilityLabel(isLiked ? "Unlike" : "Like")
        }
        .buttonStyle(RisingTourPress())
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                guard at < count else { return }
                if !dragging {
                    dragging = true
                    touches += 1
                }
                // Only upwards: the card follows the finger point for point.
                front.set(Double(at) + min(1, max(0, -value.translation.height / Metrics.away)))
                wake()
            }
            .onEnded { value in
                guard dragging else { return }
                dragging = false
                touches += 1
                // Faster than this and even this spring would overshoot.
                let limit = Metrics.frontStiffness.squareRoot() * (Double(at + 1) - front.value(at: .now))
                let velocity = min(limit, -value.velocity.height / Metrics.away)
                if -value.translation.height > Metrics.throwDistance || -value.velocity.height > Metrics.throwSpeed {
                    thumps += 1
                    advance(velocity: velocity, report: true)
                } else {
                    front.retarget(Double(at), velocity: 0)
                    wake()
                }
            }
    }

    private func wake() {
        active = true
        wakes += 1
    }

    /// Throws the front card. The next destination takes its place, or the deck is dealt again.
    private func advance(velocity: Double = 0, report: Bool = false) {
        guard at < count else { return }
        prevIndex = at
        at += 1
        nameIndex = at < count ? at : -1
        typeStart = .now
        if reduceMotion { front.set(Double(at)) } else { front.retarget(Double(at), velocity: velocity) }
        if at == count { finishes += 1 }
        wake()
        if report { onChange?(at % count) }
    }

    private func deal() {
        at = 0
        front.set(0)
        prevIndex = -1
        nameIndex = 0
        dealStart = .now
        typeStart = .now
        wake()
    }

    /// The autoPlay loop. After a touch it waits, throws what is left of the deck and starts over.
    private func play() async {
        guard auto, count > 0 else { return }
        do {
            if touches > 0 {
                try await Task.sleep(for: .seconds(Metrics.idle))
                guard !dragging else { return }
                if at != 0 {
                    while at < count { advance() }
                    try await Task.sleep(for: .seconds(Metrics.tail))
                }
            }
            let gap = count > 1 ? (Metrics.loop - Metrics.tail - Metrics.first) / Double(count - 1) : 0
            while true {
                for i in 0..<count {
                    try await Task.sleep(for: .seconds(i == 0 ? Metrics.first : gap))
                    advance()
                }
                try await Task.sleep(for: .seconds(Metrics.tail))
            }
        } catch {}
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 600.0
    static let radius = 32.0

    static let barY = 36.0
    // The name is set large in the system serif and squeezed sideways into a condensed face.
    static let nameY = 104.0
    static let nameSize = 88.0
    static let nameLine = 104.0
    static let nameSqueeze = 0.62
    // Each letter fades in over `letterSeconds` while it slides in from this offset, `letterGap` after the one before.
    static let letterFrom = CGSize(width: 22, height: -10)
    static let letterSeconds = 0.45
    static let letterGap = 0.07
    static let typeSeconds = 1.2
    /// The previous name is gone after this long.
    static let nameFade = 0.18

    static let cardWidth = 316.0
    static let cardHeight = 290.0
    static let cardTop = 232.0
    static let cardRadius = 22.0
    static let photoInset = 4.0
    static let photoHeight = 178.0
    static let photoRadius = 18.0
    // Each place further back shows this much above the card in front, and is this much narrower.
    static let peek = 22.0
    static let shrink = 0.115
    /// Degrees the third card leans.
    static let lean = 3.0
    /// Points a thrown card travels, and degrees it turns on the way.
    static let away = 560.0
    static let awayTurn = 7.0
    /// How far below its place the deck starts when it is dealt.
    static let rise = 380.0
    /// A card's face clears as it comes forward: the grey over it is this many times its depth.
    static let coverRate = 1.2
    static let coverNear = Color(hex: 0x9aa3a3)
    static let coverFar = Color(hex: 0x7d8787)

    static let stackTop = 180.0
    static let buttonsTop = 536.0
    static let buttonHeight = 44.0
    static let buttonsRise = 8.0

    // A release throws the card past this distance or this speed (points, points per second).
    static let throwDistance = 80.0
    static let throwSpeed = 500.0

    // The front of the stack follows a spring of mass 1, damped just short of critically: it must
    // not run past its target, or the next card would start to leave.
    static let frontStiffness = 144.0
    static let frontDamping = 23.5
    // Dealing (seconds from its start): the deck rises on one spring, the cards behind pop on another.
    static let riseAt = 0.1
    static let riseStiffness = 120.0
    static let riseDamping = 17.0
    static let popAt = 0.55
    static let popGap = 0.17
    static let popStiffness = 260.0
    static let popDamping = 16.0
    static let buttonsAt = 0.95
    static let buttonsSeconds = 0.35
    static let dealSeconds = 1.8
    /// Pause between the last card leaving and the next deal.
    static let dealPause = 0.45
    static let heartStiffness = 420.0
    static let heartDamping = 13.0

    // autoPlay (seconds): the first throw, then one per destination spread evenly, then the deal.
    static let first = 0.8
    static let tail = 3.2
    static let loop = 8.0
    /// After a touch the loop waits this long before it deals again and starts over.
    static let idle = 2.5

    static let star = Color(hex: 0xffe14d)
    static let heart = Color(hex: 0xff5a79)

    static let backIcon = "M17 10 L3 10 M8.5 4.5 L3 10 L8.5 15.5"
    static let searchIcon = "M9 2.8 C12.42 2.8 15.2 5.58 15.2 9 C15.2 12.42 12.42 15.2 9 15.2 C5.58 15.2 2.8 12.42 2.8 9 C2.8 5.58 5.58 2.8 9 2.8 Z M13.6 13.6 L17.5 17.5"
    static let mapIcon = "M1.5 4 L6.5 2 L11.5 4 L16.5 2 L16.5 14 L11.5 16 L6.5 14 L1.5 16 Z M6.5 2 L6.5 14 M11.5 4 L11.5 16"
    static let starIcon = "M8 0.8 L9.82 5.49 L14.85 5.78 L10.95 8.96 L12.23 13.83 L8 11.1 L3.77 13.83 L5.05 8.96 L1.15 5.78 L6.18 5.49 Z"
    static let heartIcon = "M10 17 C4.5 13.2 2 10.4 2 7.3 C2 4.9 3.9 3.2 6.1 3.2 C7.7 3.2 9.1 4 10 5.4 C10.9 4 12.3 3.2 13.9 3.2 C16.1 3.2 18 4.9 18 7.3 C18 10.4 15.5 13.2 10 17 Z"
}

/// Where a spring of mass 1 released at rest has got to after `t` seconds, on its way from 0 to 1.
private func risingTourSpringAt(_ t: Double, stiffness: Double, damping: Double) -> Double {
    guard t > 0 else { return 0 }
    let decay = damping / 2
    let omega = (stiffness - decay * decay).squareRoot()
    return 1 - exp(-decay * t) * (cos(omega * t) + decay / omega * sin(omega * t))
}

/// A number on a spring of mass 1, sampled against the `TimelineView` clock.
private struct RisingTourSpring {
    private var offset = 0.0
    private var velocity = 0.0
    private var target: Double
    private var start = Date.distantPast

    init(_ value: Double) {
        target = value
    }

    private var decay: Double { Metrics.frontDamping / 2 }
    private var omega: Double { (Metrics.frontStiffness - decay * decay).squareRoot() }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t < 10 else { return target }
        let swing = (velocity + decay * offset) / omega
        return target + exp(-decay * t) * (offset * cos(omega * t) + swing * sin(omega * t))
    }

    private func speed(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t < 10 else { return 0 }
        let swing = (velocity + decay * offset) / omega
        return exp(-decay * t) * ((omega * swing - decay * offset) * cos(omega * t) - (omega * offset + decay * swing) * sin(omega * t))
    }

    mutating func set(_ value: Double) {
        target = value
        offset = 0
        velocity = 0
        start = .distantPast
    }

    /// Heads for `value` from wherever it is, keeping its speed unless one is given.
    mutating func retarget(_ value: Double, at date: Date = .now, velocity given: Double? = nil) {
        offset = self.value(at: date) - value
        velocity = given ?? speed(at: date)
        target = value
        start = date
    }
}

/// How far letter number `order` has arrived, `type` seconds after the name changed.
private func risingTourLetter(_ type: Double, order: Int) -> Double {
    let p = min(1, max(0, (type - Double(order) * Metrics.letterGap) / Metrics.letterSeconds))
    return 1 - pow(1 - p, 3)
}

/// The buttons fade in at the end of a deal, and out while the last card leaves.
private func risingTourButtons(front: Double, deal: Double, count: Int) -> Double {
    let p = min(1, max(0, (deal - Metrics.buttonsAt) / Metrics.buttonsSeconds))
    let leaving = min(1, max(0, (front - Double(count - 1)) / 0.3))
    return min(1 - pow(1 - p, 3), 1 - leaving)
}

private struct RisingTourPose {
    /// Vertical offset from the front card's place, applied after scale and rotation about the centre.
    var shift: Double
    var scale: Double
    var rotation: Double
    var opacity: Double
    /// The grey that hides a card's face while it waits behind, and how much of the darker grey is over it.
    var cover: Double
    var coverFar: Double
}

/// Where card `i` is: `front` is the index of the card at the front and `deal` the seconds since the deck was dealt.
private func risingTourPose(_ i: Int, front: Double, deal: Double, reduced: Bool) -> RisingTourPose {
    let rel = Double(i) - front
    if rel < 0 {
        // Thrown: up and away, turning a little. Under reduced motion it fades where it is.
        let gone = min(1, -rel)
        return RisingTourPose(
            shift: reduced ? 0 : -gone * Metrics.away, scale: 1, rotation: reduced ? 0 : gone * Metrics.awayTurn,
            opacity: reduced ? 1 - gone : 1, cover: 0, coverFar: 0
        )
    }
    let moving = reduced ? Metrics.dealSeconds : deal
    let rise = risingTourSpringAt(moving - Metrics.riseAt, stiffness: Metrics.riseStiffness, damping: Metrics.riseDamping)
    // A card behind stays hidden under the front one until its turn to pop up.
    let pop = i == 0 ? 1 : risingTourSpringAt(
        moving - Metrics.popAt - Double(i - 1) * Metrics.popGap, stiffness: Metrics.popStiffness, damping: Metrics.popDamping
    )
    let depth = min(rel, 2) * pop
    let scale = 1 - Metrics.shrink * depth
    return RisingTourPose(
        // Scaling about the centre lifts the top edge; the shift puts it back, `peek` above the card in front.
        shift: -Metrics.peek * depth - (1 - scale) * Metrics.cardHeight / 2 + (1 - rise) * Metrics.rise,
        scale: scale,
        rotation: Metrics.lean * min(1, max(0, depth - 1)),
        opacity: rel > 2.5 ? 0 : (reduced ? min(1, deal / 0.2) : 1),
        cover: min(1, max(0, depth * Metrics.coverRate)),
        coverFar: min(1, max(0, depth - 1))
    )
}

/// A remote photo cropped to fill, over a flat tint until it arrives.
private struct RisingTourPhoto: View {
    let url: String
    let tint: Color

    var body: some View {
        tint.overlay(
            AsyncImage(url: URL(string: url), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                }
            }
        )
        .clipped()
    }
}

private struct RisingTourPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

private func risingTourPath(_ svg: String) -> Path {
    var path = Path()
    let scanner = Scanner(string: svg)
    func point() -> CGPoint {
        let x = scanner.scanDouble() ?? 0
        return CGPoint(x: x, y: scanner.scanDouble() ?? 0)
    }
    while let command = scanner.scanCharacter() {
        switch command {
        case "M": path.move(to: point())
        case "L": path.addLine(to: point())
        case "C":
            let first = point()
            let second = point()
            path.addCurve(to: point(), control1: first, control2: second)
        default: path.closeSubpath()
        }
    }
    return path
}

private func risingTourPhotoURL(_ id: String) -> String {
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=800&q=80"
}

let risingTourDestinations: [RisingTourDestination] = [
    RisingTourDestination(
        city: "PARIS", title: "Eiffel Tower 2nd Floor or Summit Guided Tour",
        text: "Make the best use of your precious time in Paris with pre-reserved tickets to the Eiffel Tower. Choose from a guided tour of the 2nd floor or the summit.",
        rating: "4.5", price: "€ 99",
        photo: risingTourPhotoURL("1502602898657-3e91760cbb34"), tint: Color(hex: 0x8a7f9c)
    ),
    RisingTourDestination(
        city: "ROME", title: "Colosseum Arena Floor and Roman Forum Tour",
        text: "Step onto the arena floor through the gladiators' gate, then follow your guide across the Roman Forum and up the Palatine Hill.",
        rating: "4.8", price: "€ 64",
        photo: risingTourPhotoURL("1552832230-c0197dd311b5"), tint: Color(hex: 0x8d8478)
    ),
    RisingTourDestination(
        city: "VENICE", title: "Grand Canal Gondola Ride with Live Commentary",
        text: "Glide under the Rialto Bridge and along the quiet side canals while your gondolier points out the palaces that line the water.",
        rating: "4.6", price: "€ 38",
        photo: risingTourPhotoURL("1523906834658-6e24ef2386f9"), tint: Color(hex: 0x7fa3ad)
    ),
]

#Preview {
    ZStack {
        Color(hex: 0xebebeb).ignoresSafeArea()
        RisingTourStack(autoPlay: true)
    }
}
