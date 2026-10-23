import SwiftUI

enum DestinationSwipeStackIcon {
    case snow, sun, hills, sail
}

struct DestinationSwipeStackItem: Equatable {
    var title: String
    /// Second line of the title, in grey.
    var subtitle: String
    var caption: String
    /// The small pill on the photo.
    var tag: String
    var photo: String
    /// Flat colour shown until the photo arrives.
    var tint: Color
    /// Colour of the glow behind the stack while this card is in front.
    var glow: Color
    var icon: DestinationSwipeStackIcon

    static let samples = [
        DestinationSwipeStackItem(
            title: "Ama Dablam", subtitle: "Himalaya",
            caption: "A sharp granite peak above the Khumbu valley, best seen at first light.",
            tag: "12 days · $2,490",
            photo: "https://images.unsplash.com/photo-1454496522488-7a8e488e8606?auto=format&fit=crop&w=600&h=460&q=80",
            tint: Color(hex: 0x8a9bb4), glow: Color(hex: 0xa9a4f5), icon: .snow
        ),
        DestinationSwipeStackItem(
            title: "Fire Valley", subtitle: "Nevada",
            caption: "Red sandstone walls and an empty road running straight into the heat.",
            tag: "4 days · $640",
            photo: "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=600&h=460&q=80",
            tint: Color(hex: 0x9a5a44), glow: Color(hex: 0xf5a95c), icon: .sun
        ),
        DestinationSwipeStackItem(
            title: "Fairy Glen", subtitle: "Isle of Skye",
            caption: "Grassy cones and a spiral of stones in the hills above the village.",
            tag: "3 days · $380",
            photo: "https://images.unsplash.com/photo-1472214103451-9374bd1c798e?auto=format&fit=crop&w=600&h=460&q=80",
            tint: Color(hex: 0x5c8a3a), glow: Color(hex: 0x9fd67c), icon: .hills
        ),
        DestinationSwipeStackItem(
            title: "Blue Lagoon", subtitle: "Maldives",
            caption: "Water villas on a ring of coral, a short seaplane hop from the capital.",
            tag: "7 days · $3,150",
            photo: "https://images.unsplash.com/photo-1514282401047-d79a71a590e8?auto=format&fit=crop&w=600&h=460&q=80",
            tint: Color(hex: 0x3fb6c4), glow: Color(hex: 0x62d3d6), icon: .sail
        ),
    ]
}

/// A deck of destination cards. A sideways or downward swipe sends the front card away: it tilts,
/// drops below the stack and stays there blurred and dim, while the ghost card behind it turns
/// upright and becomes solid, the other ghosts shuffle their angles and the glow behind the stack
/// takes the new card's colour. The deck is a ring: after the last card the first one comes back.
struct DestinationSwipeStack: View {
    /// The cards, front first. At least four.
    var items = DestinationSwipeStackItem.samples
    /// Swipes a card away every two seconds, as if a finger did it.
    var autoPlay = false
    /// Colour of the surface the stack sits on: the dropped card fades into it at the bottom edge.
    var canvas = Color(hex: 0xf4f4f7)
    /// Called with the index of the card that is now in front.
    var onChange: ((Int) -> Void)? = nil
    /// Called with the index of the front card when its arrow button is tapped.
    var onOpen: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var deck = Deck()
    /// Keeps the timeline running while a touch is being followed.
    @State private var awake = false
    @State private var touches = 0
    @State private var swipes = 0

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: !auto && !awake)) { timeline in
            let count = items.count
            let _ = deck.advance(to: timeline.date, count: count, swipe: reduceMotion ? Metrics.swipeReduced : Metrics.swipe)
            let front = wrapIndex(Int(deck.pos.rounded()), count)
            let travel = deck.pos - deck.pos.rounded(.down)

            ZStack {
                ForEach(items.indices, id: \.self) { index in
                    let near = glowWeight(relative(index, deck.pos, count))
                    Circle()
                        .fill(
                            RadialGradient(
                                stops: [
                                    .init(color: items[index].glow.opacity(0.62), location: 0),
                                    .init(color: items[index].glow.opacity(0.4), location: 0.55),
                                    .init(color: items[index].glow.opacity(0), location: 1),
                                ],
                                center: .center, startRadius: 0, endRadius: Metrics.glowRadius
                            )
                        )
                        .frame(width: Metrics.glowRadius * 2, height: Metrics.glowRadius * 2)
                        // The glow swells a little while the deck is moving.
                        .scaleEffect(x: 1 + 0.07 * sin(.pi * travel), y: Metrics.glowStretch * (1 + 0.07 * sin(.pi * travel)))
                        .opacity(near)
                        .position(x: Metrics.width / 2, y: Metrics.centerY)
                }
                .allowsHitTesting(false)

                ZStack {
                    ForEach(items.indices, id: \.self) { index in
                        let pose = pose(
                            relative(index, deck.pos, count), count: count,
                            dragX: deck.dragX, dragY: deck.dragY, reduced: reduceMotion
                        )
                        Card(item: items[index], ghost: pose.ghost, face: pose.face) { onOpen?(index) }
                            .scaleEffect(pose.scale)
                            .rotationEffect(.degrees(pose.rotation))
                            .blur(radius: pose.blur)
                            .opacity(pose.alpha)
                            .position(x: Metrics.width / 2 + pose.x, y: Metrics.centerY + pose.y)
                            .zIndex(pose.z)
                            .allowsHitTesting(index == front)
                            .accessibilityHidden(index != front)
                    }
                }
                .frame(width: Metrics.width, height: Metrics.height)
                // Cut at the bottom edge only: the ghosts and a dragged card may reach past the sides.
                .mask(Rectangle().padding(.horizontal, -Metrics.bleed).padding(.top, -Metrics.bleed))
                .contentShape(Rectangle())
                .gesture(drag)

                LinearGradient(colors: [canvas.opacity(0), canvas], startPoint: .top, endPoint: .bottom)
                    .frame(width: Metrics.width, height: Metrics.fade)
                    .position(x: Metrics.width / 2, y: Metrics.height - Metrics.fade / 2)
                    .allowsHitTesting(false)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .onChange(of: front) { onChange?(front) }
            .accessibilityElement(children: .contain)
            .accessibilityAction(named: "Next destination") { next() }
        }
        .sensoryFeedback(.impact(weight: .light), trigger: swipes)
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .task(id: touches) {
            guard touches > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.userHold))
            awake = false
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                touch()
                // A swipe that is still running finishes first.
                guard deck.touching || abs(deck.pos - deck.pos.rounded()) < 0.001 else { return }
                deck.touching = true
                let move = value.translation
                deck.dragX = move.width * Metrics.dragGain
                deck.dragY = move.height * (move.height > 0 ? Metrics.dragGain : Metrics.dragGainUp)
            }
            .onEnded { value in
                guard deck.touching else { return }
                deck.touching = false
                if abs(value.translation.width) > Metrics.commit || value.translation.height > Metrics.commit { next() }
            }
    }

    private func sync() {
        deck.looping = auto
        deck.hold = 0
        deck.clock = 0
        deck.base = deck.pos.rounded()
    }

    /// A touch takes over from the loop, which starts again from rest a little later.
    private func touch() {
        deck.hold = Metrics.userHold
        awake = true
        touches += 1
    }

    private func next() {
        touch()
        deck.requests += 1
        swipes += 1
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 615.0
    /// Centre of the stack, measured from the top of the component.
    static let centerY = 270.0
    static let cardWidth = 236.0
    static let cardHeight = 342.0
    static let cardRadius = 20.0
    static let iconSize = 18.0
    /// The cards may reach this far past the sides and the top before they are cut.
    static let bleed = 40.0
    /// Height of the strip at the bottom where the dropped card fades into the canvas.
    static let fade = 96.0
    static let glowRadius = 250.0
    static let glowStretch = 1.15

    // Where a card rests in each place of the deck: offset from the centre, angle in degrees, scale.
    static let ghostA = Slot(x: 16, y: -8, rotation: 12, scale: 0.97, ghost: 0.34)
    static let ghostB = Slot(x: -18, y: 2, rotation: -15, scale: 0.94, ghost: 0.2)
    static let dropX = 12.0
    static let dropY = 359.0
    static let dropRotation = -7.0
    static let dropScale = 0.84
    static let dropAlpha = 0.5
    static let dropBlur = 5.0

    /// Seconds one swipe takes from the first tilt to the last ghost settling.
    static let swipe = 1.1
    static let swipeReduced = 0.35
    // autoPlay (seconds): the default deck of four goes round once per `loop`, the first swipe starting
    // at `first` and the next ones every `step`.
    static let loop = 8.0
    static let first = 0.8
    static let step = loop / 4
    /// After a touch the loop waits this long before it starts again.
    static let userHold = 2.5

    // The spring the arriving cards turn on.
    static let stiffness = 160.0
    static let damping = 15.0
    static let mass = 1.0
    // The spring that brings a card back when it is let go too early.
    static let returnStiffness = 260.0
    static let returnDamping = 24.0

    /// Share of the finger's travel the front card follows, sideways and down, and upwards.
    static let dragGain = 0.55
    static let dragGainUp = 0.2
    /// Degrees of tilt per point of sideways drag.
    static let dragTilt = 0.06
    /// Finger travel that sends the card away on release.
    static let commit = 56.0
}

private struct Slot {
    let x: Double
    let y: Double
    let rotation: Double
    let scale: Double
    let ghost: Double
}

private struct Pose {
    var x = 0.0
    var y = 0.0
    var rotation = 0.0
    var scale = 1.0
    /// Opacity of the whole card.
    var alpha = 1.0
    /// Opacity of the grey body a card shows while it waits behind.
    var ghost = Metrics.ghostA.ghost
    /// Opacity of the dark face with the photo and the texts.
    var face = 0.0
    var blur = 0.0
    var z = 1.0
}

/// Per-frame state of the deck. `pos` counts the cards swiped away so far; its fraction is how far
/// the running swipe has come.
private final class Deck {
    var last: Date? = nil
    var pos = 0.0
    var target = 0.0
    var looping = false
    /// True while the loop is the one moving the deck.
    var live = false
    /// Swipes asked for by a finger that the next frame has to start.
    var requests = 0.0
    /// Where the loop started counting from, and its clock.
    var base = 0.0
    var clock = 0.0
    /// Seconds the loop still waits after a touch.
    var hold = 0.0
    var touching = false
    var dragX = 0.0
    var dragY = 0.0
    var velocityX = 0.0
    var velocityY = 0.0

    func advance(to date: Date, count: Int, swipe: Double) {
        guard let last else {
            self.last = date
            return
        }
        let dt = min(date.timeIntervalSince(last), 0.064)
        guard dt > 0 else { return }
        self.last = date

        if looping, hold <= 0 {
            let loop = Metrics.step * Double(count)
            clock += dt
            if clock >= loop { clock -= loop }
            pos = base + script(clock, count: count)
            target = pos
            live = true
        } else {
            if live {
                // A touch took over: the swipe the loop was in the middle of finishes first.
                target = (pos - 0.000001).rounded(.up)
                live = false
            }
            if requests > 0 {
                target = min(target + requests, pos.rounded(.down) + 2)
                requests = 0
            }
            if pos < target {
                pos = min(target, pos + dt / swipe)
            } else if hold > 0, !touching {
                hold -= dt
                if hold <= 0 {
                    base = Double(wrapIndex(Int(pos.rounded()), count))
                    pos = base
                    target = base
                    clock = 0
                }
            }
        }

        if !touching {
            velocityX += (-Metrics.returnStiffness * dragX - Metrics.returnDamping * velocityX) * dt
            velocityY += (-Metrics.returnStiffness * dragY - Metrics.returnDamping * velocityY) * dt
            dragX += velocityX * dt
            dragY += velocityY * dt
        } else {
            velocityX = 0
            velocityY = 0
        }
    }
}

private func wrapIndex(_ index: Int, _ count: Int) -> Int {
    ((index % count) + count) % count
}

private func segment(_ t: Double, _ from: Double, _ to: Double) -> Double {
    min(1, max(0, (t - from) / (to - from)))
}

private func easeOut(_ t: Double) -> Double { 1 - pow(1 - t, 3) }

private func easeInOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(2 - 2 * t, 3) / 2 }

private func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

/// Step response of the arriving cards' spring `seconds` after it is released: 0 → 1 with one overshoot.
private func spring(_ seconds: Double) -> Double {
    guard seconds > 0 else { return 0 }
    let omega = (Metrics.stiffness / Metrics.mass).squareRoot()
    let zeta = Metrics.damping / (2 * (Metrics.stiffness * Metrics.mass).squareRoot())
    let damped = omega * (1 - zeta * zeta).squareRoot()
    return 1 - exp(-zeta * omega * seconds) * (cos(damped * seconds) + zeta * omega / damped * sin(damped * seconds))
}

/// The spring started `delay` into a swipe of which `part` (0…1) has passed, landing exactly on 1.
private func settle(_ part: Double, delay: Double) -> Double {
    let value = spring((part - delay) * Metrics.swipe)
    return mix(value, 1, smooth(segment(part, 0.85, 1)))
}

/// Cards swiped away `clock` seconds into the autoPlay loop.
private func script(_ clock: Double, count: Int) -> Double {
    var done = 0.0
    for index in 0..<count {
        done += segment(clock, Metrics.first + Metrics.step * Double(index), Metrics.first + Metrics.step * Double(index) + Metrics.swipe)
    }
    return done
}

/// Place of card `index` in the deck: 0 in front, 1 and 2 the ghosts behind, `count - 1` the dropped
/// card, and below 0 on its way down.
private func relative(_ index: Int, _ pos: Double, _ count: Int) -> Double {
    let n = Double(count)
    var place = (Double(index) - pos).truncatingRemainder(dividingBy: n)
    if place <= -1 { place += n }
    if place > n - 1 { place -= n }
    return place
}

/// Share of the glow that belongs to the card at `place`.
private func glowWeight(_ place: Double) -> Double {
    smooth(max(0, 1 - abs(place)))
}

private func pose(_ place: Double, count: Int, dragX: Double, dragY: Double, reduced: Bool) -> Pose {
    // Reduced motion: every card jumps to its next place half-way while the deck fades through.
    var place = place
    var dragX = dragX
    var dragY = dragY
    var fade = 1.0
    if reduced {
        let nearest = place.rounded()
        fade = 1 - 2 * abs(place - nearest)
        place = nearest
        dragX = 0
        dragY = 0
    }

    var pose = Pose()
    let last = Double(count - 1)
    if place <= 0 {
        // The front card, on its way down: a quick tilt one way, then the fall turns it the other way.
        let t = -place
        let fall = easeInOut(segment(t, 0, 0.62))
        pose.x = Metrics.dropX * fall + dragX
        pose.y = Metrics.dropY * fall + dragY
        pose.rotation = 10 * easeOut(segment(t, 0, 0.28)) + (Metrics.dropRotation - 10) * easeInOut(segment(t, 0.22, 0.8))
            + dragX * Metrics.dragTilt
        pose.scale = mix(1, Metrics.dropScale, fall)
        pose.alpha = mix(1, Metrics.dropAlpha, smooth(segment(t, 0.1, 0.62)))
        pose.blur = Metrics.dropBlur * smooth(segment(t, 0.08, 0.6))
        pose.face = 1
        pose.z = 4
    } else if place <= 1 {
        // The first ghost turns upright and becomes the front card, with a small lift on the way.
        let part = 1 - place
        let s = settle(part, delay: 0.1)
        pose.x = Metrics.ghostA.x * (1 - s)
        pose.y = Metrics.ghostA.y * (1 - s)
        pose.rotation = Metrics.ghostA.rotation * (1 - s)
        pose.scale = mix(Metrics.ghostA.scale, 1, s) + 0.035 * sin(.pi * segment(part, 0.1, 0.75))
        pose.face = easeOut(segment(part, 0.08, 0.5))
        pose.z = 3
    } else if place <= 2 {
        // The second ghost swings over to the first ghost's angle a moment later.
        let s = settle(2 - place, delay: 0.2)
        pose.x = mix(Metrics.ghostB.x, Metrics.ghostA.x, s)
        pose.y = mix(Metrics.ghostB.y, Metrics.ghostA.y, s)
        pose.rotation = mix(Metrics.ghostB.rotation, Metrics.ghostA.rotation, s)
        pose.scale = mix(Metrics.ghostB.scale, Metrics.ghostA.scale, s)
        pose.ghost = mix(Metrics.ghostB.ghost, Metrics.ghostA.ghost, s)
        pose.z = 2
    } else if place > last - 1, last - place < 0.3 {
        // The dropped card sinks out of sight before it comes back as a ghost.
        let gone = segment(last - place, 0, 0.3)
        pose.x = Metrics.dropX
        pose.y = Metrics.dropY + 26 * gone
        pose.rotation = Metrics.dropRotation
        pose.scale = Metrics.dropScale
        pose.alpha = Metrics.dropAlpha * (1 - gone)
        pose.blur = Metrics.dropBlur
        pose.face = 1
        pose.z = 4
    } else {
        // A new ghost swings in at the back of the stack. Cards further back stay hidden.
        let part = 3 - place
        let s = settle(part, delay: 0.36)
        pose.x = Metrics.ghostB.x - 14 * (1 - s)
        pose.y = Metrics.ghostB.y + 10 * (1 - s)
        pose.rotation = Metrics.ghostB.rotation - 16 * (1 - s)
        pose.scale = Metrics.ghostB.scale - 0.03 * (1 - s)
        pose.alpha = easeOut(segment(part, 0.36, 0.8))
        pose.ghost = Metrics.ghostB.ghost
    }
    pose.alpha *= fade
    return pose
}

/// One card: the grey body it shows as a ghost, and the dark face that fades in over it.
private struct Card: View {
    let item: DestinationSwipeStackItem
    let ghost: Double
    let face: Double
    let open: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                .fill(Color(hex: 0x3a3a46))
                .overlay(alignment: .topLeading) {
                    Glyph(icon: item.icon, color: .white.opacity(0.9)).padding(14)
                }
                .opacity(ghost)

            Face(item: item, open: open)
                .compositingGroup()
                .shadow(color: Color.rgba(24, 24, 40, 0.3), radius: 22, y: 16)
                .opacity(face)
        }
        .frame(width: Metrics.cardWidth, height: Metrics.cardHeight)
    }
}

private struct Face: View {
    let item: DestinationSwipeStackItem
    let open: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                .fill(LinearGradient(colors: [Color(hex: 0x2c2c34), Color(hex: 0x17171b)], startPoint: .top, endPoint: .bottom))
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                .strokeBorder(.white.opacity(0.07), lineWidth: 1)

            HStack(alignment: .top) {
                Glyph(icon: item.icon, color: Color(hex: 0x8d8d99))
                Spacer()
                Button(action: open) {
                    ZStack {
                        Circle().fill(.white.opacity(0.16))
                        Path { path in
                            path.move(to: CGPoint(x: 8.8, y: 15.2))
                            path.addLine(to: CGPoint(x: 15.2, y: 8.8))
                            path.move(to: CGPoint(x: 10.2, y: 8.8))
                            path.addLine(to: CGPoint(x: 15.2, y: 8.8))
                            path.addLine(to: CGPoint(x: 15.2, y: 13.8))
                        }
                        .stroke(.white.opacity(0.88), style: StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round))
                    }
                    .frame(width: 24, height: 24)
                    .contentShape(Circle().inset(by: -8))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Open \(item.title)")
                .padding(.top, -2)
                .padding(.trailing, -2)
            }
            .padding(14)

            VStack(spacing: 0) {
                Text(item.title.uppercased()).foregroundStyle(.white).frame(height: 26)
                Text(item.subtitle.uppercased()).foregroundStyle(Color(hex: 0xb4b4c0)).frame(height: 26)
            }
            .font(.system(size: 25, weight: .light))
            .tracking(0.3)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(width: Metrics.cardWidth - 24)
            .padding(.top, 47)
            .accessibilityElement(children: .combine)

            Photo(url: item.photo, tint: item.tint)
                .frame(width: Metrics.cardWidth - 12, height: 172)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(alignment: .bottom) {
                    HStack(spacing: 5) {
                        Circle().fill(item.glow).frame(width: 5, height: 5)
                        Text(item.tag)
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(.white.opacity(0.92))
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 9)
                    .frame(height: 20)
                    .background(Capsule().fill(Color.rgba(22, 22, 26, 0.82)))
                    .padding(.bottom, 7)
                }
                .padding(.top, 112)

            Text(item.caption)
                .font(.system(size: 10))
                .lineSpacing(1.5)
                .multilineTextAlignment(.center)
                .foregroundStyle(Color(hex: 0x8c8c98))
                .lineLimit(2)
                .frame(width: 196, height: 30, alignment: .top)
                .padding(.top, 296)
        }
        .frame(width: Metrics.cardWidth, height: Metrics.cardHeight)
    }
}

/// The photo over a flat tint of its main colour, faded in when it arrives.
private struct Photo: View {
    let url: String
    let tint: Color

    var body: some View {
        tint.overlay {
            AsyncImage(url: URL(string: url), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                } else {
                    Color.clear
                }
            }
        }
        .accessibilityHidden(true)
    }
}

/// A small line icon, drawn from the same strokes in every stack.
private struct Glyph: View {
    let icon: DestinationSwipeStackIcon
    let color: Color

    var body: some View {
        Path { path in
            let unit = Metrics.iconSize / 20
            for line in strokes(icon) {
                for index in stride(from: 0, to: line.count, by: 2) {
                    let point = CGPoint(x: line[index] * unit, y: line[index + 1] * unit)
                    if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                }
            }
            for circle in circles(icon) {
                path.addEllipse(in: CGRect(
                    x: (circle[0] - circle[2]) * unit, y: (circle[1] - circle[2]) * unit,
                    width: circle[2] * 2 * unit, height: circle[2] * 2 * unit
                ))
            }
        }
        .stroke(color, style: StrokeStyle(lineWidth: 1.25, lineCap: .round, lineJoin: .round))
        .frame(width: Metrics.iconSize, height: Metrics.iconSize)
        .accessibilityHidden(true)
    }
}

/// Polylines of an icon in a 20 × 20 box, as x, y pairs.
private func strokes(_ icon: DestinationSwipeStackIcon) -> [[Double]] {
    switch icon {
    case .snow:
        [
            [10, 2.5, 10, 17.5], [3.5, 6.25, 16.5, 13.75], [3.5, 13.75, 16.5, 6.25],
            [7.8, 4.2, 10, 6.2, 12.2, 4.2], [13.92, 5.19, 13.29, 8.1, 16.12, 9],
            [16.12, 11, 13.29, 11.9, 13.92, 14.81], [12.2, 15.8, 10, 13.8, 7.8, 15.8],
            [6.08, 14.81, 6.71, 11.9, 3.88, 11], [3.88, 9, 6.71, 8.1, 6.08, 5.19],
        ]
    case .sun:
        [
            [10, 1.8, 10, 3.8], [10, 16.2, 10, 18.2], [1.8, 10, 3.8, 10], [16.2, 10, 18.2, 10],
            [4.2, 4.2, 5.6, 5.6], [15.8, 4.2, 14.4, 5.6], [4.2, 15.8, 5.6, 14.4], [15.8, 15.8, 14.4, 14.4],
        ]
    case .hills:
        [[1.8, 16.2, 7.4, 6.2, 10.6, 11.4, 12.9, 8.2, 18.2, 16.2, 1.8, 16.2]]
    case .sail:
        [
            [10, 2.6, 10, 12], [10, 3.4, 15.6, 12, 10, 12], [8.4, 6, 4.6, 12, 8.4, 12, 8.4, 6],
            [2.8, 14.6, 17.2, 14.6, 14.6, 17.6, 5.4, 17.6, 2.8, 14.6],
        ]
    }
}

/// Circles of an icon in the same box, as centre x, centre y, radius.
private func circles(_ icon: DestinationSwipeStackIcon) -> [[Double]] {
    switch icon {
    case .sun: [[10, 10, 3.3]]
    case .hills: [[14.9, 4.6, 1.5]]
    default: []
    }
}

#Preview {
    ZStack {
        Color(hex: 0xf4f4f7).ignoresSafeArea()
        DestinationSwipeStack(autoPlay: true)
    }
}
