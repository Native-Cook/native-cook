import SwiftUI

struct FannedGuideCardsPlace {
    var name: String
    var location: String
    /// Address of the photo.
    var photo: String
    /// Flat colour shown until the photo arrives.
    var tint: Color
}

/// Three white photo cards fanned out, the middle one in front. Tapping a side card brings it to
/// the front on a spring while the front card takes its place and the third leans away; dragging the
/// front card sideways tilts it and, far enough, sends it to that side. The black "Book Now" pill
/// belongs to whichever card is in front.
struct FannedGuideCards: View {
    /// Taps the side cards in turn, in a loop that ends where it began.
    var autoPlay = false
    /// Three places: the left, the front and the right card.
    var places = [
        FannedGuideCardsPlace(
            name: "Great Pyramid", location: "Giza, Egypt",
            photo: "https://images.unsplash.com/photo-1503177119275-0aa32b3a9368?auto=format&fit=crop&w=400&h=470&q=80",
            tint: Color(hex: 0xb5a794)
        ),
        FannedGuideCardsPlace(
            name: "Cappadocia", location: "Göreme, Turkey",
            photo: "https://images.unsplash.com/photo-1641128324972-af3212f0f6bd?auto=format&fit=crop&w=400&h=470&q=80",
            tint: Color(hex: 0x7e8681)
        ),
        FannedGuideCardsPlace(
            name: "Taj Mahal", location: "Agra, India",
            photo: "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?auto=format&fit=crop&w=400&h=470&q=80",
            tint: Color(hex: 0x969faf)
        ),
    ]
    var tag = "Top rated"
    var action = "Book Now"
    /// Draws the faint diagonal grid behind the cards.
    var grid = true
    /// Called with the place's index when a tap or a drag brings its card to the front.
    var onSelect: ((Int) -> Void)? = nil
    /// Called with the place's index when the pill of the front card is tapped.
    var onBook: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = Simulation()
    @State private var moves = 0

    /// Per-frame state: the loop's clock and the deck it taps.
    private final class Simulation {
        var last: Date? = nil
        var time = 0.0
        var clock = 0.0
        var looping = false
        var touching = false
        /// Seconds the loop still waits after a touch.
        var hold = 0.0
        var deck = FannedGuideCardsDeck()

        func advance(to date: Date, calm: Bool) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            let now = time + dt
            if touching {
                hold = Metrics.userHold
            } else if hold > 0 {
                hold -= dt
                // Time is up: the loop starts again from rest.
                if hold <= 0, looping { clock = 0 }
            } else if looping {
                var t = clock + dt
                if t >= Metrics.loop { t -= Metrics.loop }
                for (tap, at) in Metrics.tapsAt.enumerated() where clock < at && t >= at {
                    deck.swap(side: Metrics.tapSides[tap], at: now, calm: calm)
                }
                clock = t
            }
            time = now
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation) { timeline in
            let _ = sim.advance(to: timeline.date, calm: reduceMotion)
            ZStack {
                if grid {
                    FannedGuideCardsGrid()
                }
                ForEach(Array(places.prefix(3).enumerated()), id: \.offset) { index, place in
                    let pose = sim.deck.pose(of: index, at: sim.time, calm: reduceMotion)
                    card(place, index: index, front: min(1, max(0, pose.front)))
                        .scaleEffect(pose.scale)
                        .rotationEffect(.degrees(pose.angle))
                        .offset(x: pose.x, y: pose.y)
                        // The card that is more in front lies on top, so two cards trade places as they pass.
                        .zIndex(min(1, max(0, pose.front)) + Double(index) * 0.001)
                        .onTapGesture { tap(index) }
                        .gesture(drag(index))
                }
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: moves)
        .onAppear {
            sim.deck = FannedGuideCardsDeck(dealtAt: reduceMotion ? nil : 0)
            sim.looping = auto
        }
        .onChange(of: auto) {
            sim.looping = auto
            sim.clock = 0
            sim.hold = 0
        }
    }

    private func tap(_ index: Int) {
        let slot = sim.deck.cards[index].slot
        guard slot != 1 else { return }
        sim.hold = Metrics.userHold
        sim.deck.swap(side: slot, at: sim.time, calm: reduceMotion)
        moves += 1
        onSelect?(index)
    }

    private func drag(_ index: Int) -> some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .global)
            .onChanged { value in
                guard sim.deck.cards[index].slot == 1 else { return }
                sim.touching = true
                sim.deck.drag = FannedGuideCardsDeck.Drag(card: index, x: value.translation.width, y: value.translation.height)
            }
            .onEnded { value in
                guard sim.deck.drag.card == index else { return }
                sim.touching = false
                sim.hold = Metrics.userHold
                if abs(value.translation.width) > Metrics.dragThrow {
                    let side = value.translation.width > 0 ? 2 : 0
                    let next = sim.deck.cards.firstIndex { $0.slot == side } ?? index
                    sim.deck.swap(side: side, at: sim.time, calm: reduceMotion)
                    moves += 1
                    onSelect?(next)
                } else {
                    sim.deck.settle(at: sim.time, calm: reduceMotion)
                }
            }
    }

    /// One card; `front` is 1 when it is in front and 0 at the sides.
    private func card(_ place: FannedGuideCardsPlace, index: Int, front: Double) -> some View {
        let sheet = RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous).fill(.white)
        return ZStack(alignment: .topLeading) {
            // The front card casts a second, deeper shadow.
            sheet
                .shadow(color: Color.rgba(28, 40, 34, 0.24), radius: 20, y: 22)
                .opacity(front)
            sheet
                .shadow(color: Color.rgba(28, 40, 34, 0.14), radius: 7, y: 6)

            FannedGuideCardsImage(place: place)
                .frame(width: Metrics.photoWidth, height: Metrics.photoHeight)
                .clipShape(FannedGuideCardsPhotoShape())
                .offset(x: Metrics.frame, y: Metrics.frame)

            Text(tag)
                .font(.system(size: 9.5, weight: .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                .fixedSize()
                .padding(.horizontal, 9)
                .frame(height: 20)
                .background(Capsule().fill(Color.rgba(20, 20, 20, 0.55)))
                .offset(x: Metrics.frame + 8, y: Metrics.frame + 8)

            FannedGuideCardsArrow()
                .stroke(.white, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                .frame(width: 12, height: 12)
                .frame(width: Metrics.badge * 2, height: Metrics.badge * 2)
                .background(Circle().fill(Color(hex: 0x161616)))
                .offset(x: Metrics.cardWidth - Metrics.badgeInset - Metrics.badge * 2, y: Metrics.badgeInset)

            HStack(spacing: 6) {
                Text(place.name)
                    .font(.system(size: 15))
                    .tracking(-0.3)
                    .foregroundStyle(Color(hex: 0x1c1c1c))
                Spacer(minLength: 0)
                Text(place.location)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Color(hex: 0x8a8a8a))
                    .fixedSize()
            }
            .lineLimit(1)
            .frame(width: Metrics.cardWidth - 28, height: Metrics.nameHeight)
            .offset(x: 14, y: Metrics.frame + Metrics.photoHeight)

            Button {
                onBook?(index)
            } label: {
                ZStack {
                    Capsule().fill(Color(hex: 0xefefee))
                    Text(action).foregroundStyle(Color(hex: 0xa3a3a3))
                    // The black pill fades onto the card that comes to the front.
                    Capsule().fill(Color(hex: 0x1b1b1b)).opacity(front)
                    Text(action).foregroundStyle(.white).opacity(front)
                }
                .font(.system(size: 12.5, weight: .semibold))
                .lineLimit(1)
                .frame(width: Metrics.cardWidth - Metrics.frame * 2, height: Metrics.buttonHeight)
            }
            .buttonStyle(PressStyle())
            .allowsHitTesting(front > 0.5)
            .offset(x: Metrics.frame, y: Metrics.frame + Metrics.photoHeight + Metrics.nameHeight)
        }
        .frame(width: Metrics.cardWidth, height: Metrics.cardHeight, alignment: .topLeading)
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(verbatim: "\(place.name), \(place.location). \(tag)"))
    }

    private struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.97 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

private enum Metrics {
    /// The box the fan is laid out in; the side cards reach a little beyond it.
    static let width = 350.0
    static let height = 330.0

    static let cardWidth = 200.0
    static let cardHeight = 300.0
    static let radius = 20.0
    /// The white frame around the photo.
    static let frame = 8.0
    static let photoWidth = 184.0
    static let photoHeight = 216.0
    static let photoRadius = 14.0
    static let nameHeight = 34.0
    static let buttonHeight = 34.0
    /// The round badge in the photo's top right corner: its radius, its inset from the card's
    /// edges, the gap the photo leaves around it and the radius that rounds the cut's shoulders.
    static let badge = 16.0
    static let badgeInset = 6.0
    static let badgeGap = 4.0
    static let shoulder = 5.0

    // The spring every card moves on (mass 1), and when it counts as settled.
    static let stiffness = 180.0
    static let damping = 16.0
    static let settled = 1.4
    /// In a swap the card that leaves the front starts this long after the one that comes in, and
    /// the third card is nudged this long after that.
    static let swapStagger = 0.04
    /// How far the third card leans away from a swap, in points and degrees.
    static let kickX = 9.0
    static let kickAngle = 4.0

    // Dragging the front card: it follows the finger, a third as much vertically, tilts with the
    // distance, and goes to the side once released this far out.
    static let dragY = 0.3
    static let tiltPerPoint = 0.07
    static let tiltMax = 12.0
    static let dragThrow = 70.0

    // On appear the cards deal out from one pile.
    static let dealAt = [0.35, 0.43, 0.51]
    static let pileScale = 0.8
    static let pileAngles = [-7.0, 0.0, 5.0]

    // autoPlay loop (seconds): taps on the right, left, left and right card bring every card to the
    // front once and leave the fan as it started.
    static let tapsAt = [1.5, 3.7, 5.9, 8.1]
    static let tapSides = [2, 0, 0, 2]
    static let loop = 8.8
    /// After a touch the loop waits this long before it starts again from rest.
    static let userHold = 2.0
}

/// Where the three cards are and how they got there. A card's place on screen is a function of
/// time: it springs from the pose it had when it last changed slot to the pose of its slot.
private struct FannedGuideCardsDeck {
    struct Pose {
        var x: Double
        var y: Double
        var angle: Double
        var scale: Double
        /// 1 in front, 0 at the sides.
        var front: Double
    }

    struct Card {
        /// 0 left, 1 front, 2 right.
        var slot: Int
        var from: Pose
        /// When it set off for its slot.
        var at: Double
        /// A nudge away from a swap it took no part in: its direction and when it began.
        var kick = 0.0
        var kickAt = 0.0
    }

    struct Drag {
        /// The card under the finger, or -1.
        var card = -1
        var x = 0.0
        var y = 0.0
    }

    static let slots = [
        Pose(x: -100, y: -26, angle: -8, scale: 0.7, front: 0),
        Pose(x: 0, y: 6, angle: -3, scale: 0.9, front: 1),
        Pose(x: 100, y: 12, angle: 12, scale: 0.7, front: 0),
    ]

    var cards: [Card]
    var drag = Drag()

    /// The fan at rest, or, with `dealtAt`, a pile that deals itself out from that moment.
    init(dealtAt: Double? = nil) {
        cards = (0..<3).map { index in
            let slot = Self.slots[index]
            guard let dealtAt else { return Card(slot: index, from: slot, at: -10) }
            let pile = Pose(x: 0, y: Self.slots[1].y, angle: Metrics.pileAngles[index], scale: Metrics.pileScale, front: slot.front)
            return Card(slot: index, from: pile, at: dealtAt + Metrics.dealAt[index])
        }
    }

    func pose(of index: Int, at time: Double, calm: Bool) -> Pose {
        let card = cards[index]
        let to = Self.slots[card.slot]
        // Reduced motion: a card jumps to its slot.
        let p = calm ? (time >= card.at ? 1 : 0) : springStep(time - card.at)
        let nudge = calm ? 0 : card.kick * springKick(time - card.kickAt)
        var pose = Pose(
            x: card.from.x + (to.x - card.from.x) * p + Metrics.kickX * nudge,
            y: card.from.y + (to.y - card.from.y) * p,
            angle: card.from.angle + (to.angle - card.from.angle) * p + Metrics.kickAngle * nudge,
            scale: card.from.scale + (to.scale - card.from.scale) * p,
            front: card.from.front + (to.front - card.from.front) * p
        )
        if drag.card == index {
            pose.x += drag.x
            pose.y += drag.y * Metrics.dragY
            pose.angle += min(Metrics.tiltMax, max(-Metrics.tiltMax, drag.x * Metrics.tiltPerPoint))
        }
        return pose
    }

    /// The card at `side` (0 left, 2 right) comes to the front, the front card takes its slot and
    /// the third card leans away for a moment.
    mutating func swap(side: Int, at time: Double, calm: Bool) {
        guard let coming = cards.firstIndex(where: { $0.slot == side }),
              let leaving = cards.firstIndex(where: { $0.slot == 1 }) else { return }
        let third = 3 - coming - leaving
        cards[coming] = Card(slot: 1, from: pose(of: coming, at: time, calm: calm), at: time)
        cards[leaving] = Card(slot: side, from: pose(of: leaving, at: time, calm: calm), at: time + Metrics.swapStagger)
        cards[third].kick = side == 2 ? -1 : 1
        cards[third].kickAt = time + Metrics.swapStagger * 2
        drag = Drag()
    }

    /// The dragged card is let go and springs back to its slot.
    mutating func settle(at time: Double, calm: Bool) {
        guard drag.card >= 0 else { return }
        let index = drag.card
        cards[index] = Card(slot: cards[index].slot, from: pose(of: index, at: time, calm: calm), at: time)
        drag = Drag()
    }
}

/// A spring let go at 0 and heading for 1, `t` seconds on (zero before it starts).
private func springStep(_ t: Double) -> Double {
    if t <= 0 { return 0 }
    if t >= Metrics.settled { return 1 }
    let decay = Metrics.damping / 2
    let pulse = (Metrics.stiffness - decay * decay).squareRoot()
    return 1 - exp(-decay * t) * (cos(pulse * t) + decay / pulse * sin(pulse * t))
}

/// The same spring struck at rest: it swings out and back to 0.
private func springKick(_ t: Double) -> Double {
    if t <= 0 || t >= Metrics.settled { return 0 }
    let decay = Metrics.damping / 2
    let pulse = (Metrics.stiffness - decay * decay).squareRoot()
    return exp(-decay * t) * sin(pulse * t)
}

/// The photo's outline: a rounded rectangle whose top right corner curves in around the badge.
private struct FannedGuideCardsPhotoShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = rect.height
        let r = Metrics.photoRadius
        // The badge's centre in the photo, the radius of the cut around it and of its shoulders.
        let cx = w + Metrics.frame - Metrics.badgeInset - Metrics.badge
        let cy = Metrics.badgeInset + Metrics.badge - Metrics.frame
        let cut = Metrics.badge + Metrics.badgeGap
        let f = Metrics.shoulder
        let reach = ((cut + f) * (cut + f) - (cy - f) * (cy - f)).squareRoot()
        let lean = atan2(cy - f, reach) * 180 / .pi

        var path = Path()
        path.move(to: CGPoint(x: r, y: 0))
        path.addArc(center: CGPoint(x: cx - reach, y: f), radius: f, startAngle: .degrees(-90), endAngle: .degrees(lean), clockwise: false)
        path.addArc(center: CGPoint(x: cx, y: cy), radius: cut, startAngle: .degrees(180 + lean), endAngle: .degrees(90 - lean), clockwise: true)
        path.addArc(center: CGPoint(x: w - f, y: cy + reach), radius: f, startAngle: .degrees(270 - lean), endAngle: .degrees(360), clockwise: false)
        path.addArc(center: CGPoint(x: w - r, y: h - r), radius: r, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        path.addArc(center: CGPoint(x: r, y: h - r), radius: r, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        path.addArc(center: CGPoint(x: r, y: r), radius: r, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        path.closeSubpath()
        return path
    }
}

/// The arrow pointing up and to the right, in a 12-point box.
private struct FannedGuideCardsArrow: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 2, y: 10))
        path.addLine(to: CGPoint(x: 10, y: 2))
        path.move(to: CGPoint(x: 3.5, y: 2))
        path.addLine(to: CGPoint(x: 10, y: 2))
        path.addLine(to: CGPoint(x: 10, y: 8.5))
        return path
    }
}

/// A photo that fades in over its tint once it has loaded.
private struct FannedGuideCardsImage: View {
    let place: FannedGuideCardsPlace

    var body: some View {
        place.tint.overlay {
            AsyncImage(url: URL(string: place.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                }
            }
        }
    }
}

/// A diagonal grid that fades out towards its edges, so it needs no frame.
private struct FannedGuideCardsGrid: View, Equatable {
    static let size = 500.0
    static let pitch = 24.0

    var body: some View {
        Canvas { context, _ in
            var lines = Path()
            var offset = -Self.size
            while offset <= Self.size {
                lines.move(to: CGPoint(x: offset, y: 0))
                lines.addLine(to: CGPoint(x: offset + Self.size, y: Self.size))
                lines.move(to: CGPoint(x: offset + Self.size, y: 0))
                lines.addLine(to: CGPoint(x: offset, y: Self.size))
                offset += Self.pitch
            }
            let ink = Color(hex: 0x7d8a82)
            context.stroke(
                lines,
                with: .radialGradient(
                    Gradient(colors: [ink.opacity(0.34), ink.opacity(0)]),
                    center: CGPoint(x: Self.size / 2, y: Self.size / 2), startRadius: 0, endRadius: Self.size / 2
                ),
                lineWidth: 1
            )
        }
        .frame(width: Self.size, height: Self.size)
        .allowsHitTesting(false)
    }
}

#Preview {
    ZStack {
        Color(hex: 0xf0f2ef).ignoresSafeArea()
        FannedGuideCards(autoPlay: true)
    }
}
