import SwiftUI

struct FrostedDestinationCarouselPlace: Equatable {
    var name: String
    /// The small line under the name.
    var caption: String
    /// Address of the photo.
    var photo: String
    /// The photo's main colour: the lower half of the card melts into it, and its shadow is cast in it.
    var tint: Color
    /// The dot after the name, in a colour of the country's flag.
    var dot: Color

    static let samples = [
        FrostedDestinationCarouselPlace(
            name: "Turkey", caption: "1,991 Hotels • 42 Packages",
            photo: frostedDestinationCarouselPhoto("1641128324972-af3212f0f6bd"), tint: Color(hex: 0xa8672d), dot: Color(hex: 0xe30a17)
        ),
        FrostedDestinationCarouselPlace(
            name: "Indonesia", caption: "1,345 Hotels • 24 Packages",
            photo: frostedDestinationCarouselPhoto("1537996194471-e657df975ab4"), tint: Color(hex: 0x3f6b54), dot: Color(hex: 0xff4d4d)
        ),
        FrostedDestinationCarouselPlace(
            name: "Dubai", caption: "2,345 Hotels • 54 Packages",
            photo: frostedDestinationCarouselPhoto("1518684079-3c830dcef090"), tint: Color(hex: 0x155e75), dot: Color(hex: 0x22c55e)
        ),
        FrostedDestinationCarouselPlace(
            name: "Vietnam", caption: "2,178 Hotels • 32 Packages",
            photo: frostedDestinationCarouselPhoto("1528127269322-539801943592"), tint: Color(hex: 0x2e7366), dot: Color(hex: 0xffcd00)
        ),
        FrostedDestinationCarouselPlace(
            name: "Greece", caption: "1,764 Hotels • 38 Packages",
            photo: frostedDestinationCarouselPhoto("1570077188670-e3a8d69ac5ff"), tint: Color(hex: 0x4a5aa8), dot: Color(hex: 0x60a5fa)
        ),
    ]
}

private func frostedDestinationCarouselPhoto(_ id: String) -> String {
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=540&h=750&q=80"
}

/// A row of tall photo cards on a rounded panel. The lower half of each card melts into a frosted
/// tint of its photo, under the country's name, a small line and an "Explore Now" bar. The row
/// scrolls sideways and snaps: the card that becomes the selected one rises on a spring over a
/// growing shadow of its own colour, its text brightens and its photo zooms a little, while the panel
/// behind takes on its colour.
struct FrostedDestinationCarousel: View {
    var places = FrostedDestinationCarouselPlace.samples
    /// The card selected at rest.
    var initialIndex = 1
    var exploreLabel = "Explore Now"
    /// Steps to the right three times and back again by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the card that became the selected one.
    var onSelect: ((Int) -> Void)? = nil
    /// Called with the index of the card whose bar was tapped.
    var onExplore: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    @State private var dragFrom: Double? = nil
    /// Counts finished touches: each one restarts the wait before the loop resumes.
    @State private var touches = 0
    @State private var ticks = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The row's position, in cards: 2 means the third card is in the middle.
        var scroll = FrostedDestinationCarouselSpring(0, scrollSpring)
        /// How far each card is raised, 0…1.
        var lifts: [FrostedDestinationCarouselSpring] = []
        var selected = 0
        /// Where the row is heading.
        var target = 0

        /// Raises the card nearest the middle and lowers the one that was.
        func follow(_ position: Double, at date: Date, still: Bool) {
            let nearest = min(max(Int(position.rounded()), 0), lifts.count - 1)
            guard nearest != selected, lifts.indices.contains(nearest) else { return }
            if still {
                lifts[selected].hold(0)
                lifts[nearest].hold(1)
            } else {
                lifts[selected].head(for: 0, at: date)
                lifts[nearest].head(for: 1, at: date)
            }
            selected = nearest
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion && places.count > 1 }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let scroll = motion.scroll.value(at: now)
            let _ = motion.follow(scroll, at: now, still: reduceMotion)

            ZStack {
                LinearGradient(colors: [Color(hex: 0xf1f4f9), Color(hex: 0xdfe5ee)], startPoint: .top, endPoint: .bottom)
                // The panel takes on the colour of whichever card is raised.
                ForEach(places.indices, id: \.self) { i in
                    if i < motion.lifts.count {
                        LinearGradient(colors: [places[i].tint.opacity(0.34), places[i].tint.opacity(0.1)], startPoint: .topLeading, endPoint: .bottomTrailing)
                            .opacity(motion.lifts[i].value(at: now))
                    }
                }

                Canvas { context, _ in
                    drawShadows(context, scroll: scroll, now: now)
                }

                ForEach(places.indices, id: \.self) { i in
                    if i < motion.lifts.count, abs(Double(i) - scroll) < 2.2 {
                        let lift = motion.lifts[i].value(at: now)
                        card(i, lift: lift)
                            .position(
                                x: Metrics.width / 2 + (Double(i) - scroll) * Metrics.step,
                                y: Metrics.cardY - Metrics.lift * lift + Metrics.cardH / 2
                            )
                    }
                }
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular))
            .overlay(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular).strokeBorder(.white.opacity(0.7), lineWidth: 1.5))
            .contentShape(Rectangle())
            .gesture(drag)
        }
        .sensoryFeedback(.selection, trigger: ticks)
        .onAppear { reset() }
        .onChange(of: places.count) { reset() }
        .task(id: LoopKey(auto: auto, touching: dragFrom != nil, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touching: Bool
        let touches: Int
    }

    private func card(_ i: Int, lift: Double) -> some View {
        let place = places[i]
        return ZStack {
            place.tint
            AsyncImage(url: URL(string: place.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                }
            }
            .frame(width: Metrics.cardW, height: Metrics.cardH)
            .scaleEffect(1 + Metrics.zoom * lift)
            // The lower half melts into the photo's colour, a little darker towards the bottom.
            LinearGradient(
                stops: [
                    .init(color: place.tint.opacity(0), location: 0.36),
                    .init(color: place.tint.opacity(0.94), location: 0.6),
                    .init(color: place.tint, location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
            LinearGradient(
                stops: [.init(color: .black.opacity(0), location: 0.55), .init(color: .black.opacity(0.28), location: 1)],
                startPoint: .top, endPoint: .bottom
            )

            Group {
                HStack(spacing: 7) {
                    Text(place.name)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(.white)
                    Circle()
                        .fill(place.dot)
                        .overlay(Circle().strokeBorder(.white.opacity(0.9), lineWidth: 1.5))
                        .frame(width: 10, height: 10)
                }
                .fixedSize()
                .frame(width: Metrics.cardW - 28, height: 22, alignment: .leading)
                .position(x: Metrics.cardW / 2, y: 150 + 11)
                Text(place.caption)
                    .font(.system(size: 9.5))
                    .monospacedDigit()
                    .foregroundStyle(.white.opacity(0.85))
                    .frame(width: Metrics.cardW - 28, height: 12, alignment: .leading)
                    .position(x: Metrics.cardW / 2, y: 175 + 6)
            }
            // The text is dimmer on the cards that wait.
            .opacity(Metrics.dim + (1 - Metrics.dim) * min(1, max(0, lift)))
            .allowsHitTesting(false)

            HStack {
                Text(exploreLabel)
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(.white.opacity(0.92))
                Spacer()
                chevron
                    .stroke(.white.opacity(0.92), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                    .frame(width: 4, height: 8)
            }
            .padding(.horizontal, 12)
            .frame(width: Metrics.cardW - 24, height: 36)
            .background(RoundedRectangle(cornerRadius: 10, style: .circular).fill(.black.opacity(0.22)))
            .overlay(RoundedRectangle(cornerRadius: 10, style: .circular).strokeBorder(.white.opacity(0.12), lineWidth: 1))
            .contentShape(Rectangle())
            .onTapGesture {
                touches += 1
                if i == motion.target { onExplore?(i) } else { select(i) }
            }
            .position(x: Metrics.cardW / 2, y: 196 + 18)
            .accessibilityAddTraits(.isButton)
        }
        .lineLimit(1)
        .frame(width: Metrics.cardW, height: Metrics.cardH)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular))
        .overlay(RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular).strokeBorder(.white.opacity(0.16), lineWidth: 1))
        .contentShape(Rectangle())
        .onTapGesture {
            touches += 1
            select(i)
        }
        .accessibilityElement(children: .combine)
    }

    /// A chevron pointing right, in a 4 × 8 box.
    private var chevron: Path {
        var path = Path()
        path.move(to: .zero)
        path.addLine(to: CGPoint(x: 4, y: 4))
        path.addLine(to: CGPoint(x: 0, y: 8))
        return path
    }

    /// Each card's shadow, in its colour. It stays on the panel while the card rises, and spreads.
    private func drawShadows(_ context: GraphicsContext, scroll: Double, now: Date) {
        for i in places.indices where i < motion.lifts.count && abs(Double(i) - scroll) < 2.2 {
            let lift = motion.lifts[i].value(at: now)
            var shadow = context
            shadow.translateBy(x: Metrics.width / 2 + (Double(i) - scroll) * Metrics.step, y: Metrics.cardY + Metrics.cardH / 2 + Metrics.shadowDrop)
            shadow.scaleBy(x: 1 + Metrics.shadowGrow * lift, y: 1 + Metrics.shadowGrow * lift)
            shadow.addFilter(.blur(radius: 14))
            shadow.opacity = Metrics.shadowDim + (Metrics.shadowFull - Metrics.shadowDim) * lift
            shadow.fill(
                Path(
                    roundedRect: CGRect(
                        x: -Metrics.cardW / 2 + Metrics.shadowInset, y: -Metrics.cardH / 2 + Metrics.shadowInset,
                        width: Metrics.cardW - Metrics.shadowInset * 2, height: Metrics.cardH - Metrics.shadowInset
                    ),
                    cornerRadius: Metrics.cardRadius, style: .circular
                ),
                with: .color(places[i].tint)
            )
        }
    }

    // MARK: Touch and the autoPlay loop

    private func reset() {
        let first = min(max(initialIndex, 0), max(places.count - 1, 0))
        motion.scroll.hold(Double(first))
        motion.lifts = places.indices.map { FrostedDestinationCarouselSpring($0 == first ? 1 : 0, liftSpring) }
        motion.selected = first
        motion.target = first
    }

    /// Scrolls to card `i`, carrying on at `speed` cards per second if a drag just ended.
    private func scroll(to i: Int, speed: Double? = nil) {
        let index = min(max(i, 0), places.count - 1)
        if index != motion.target { onSelect?(index) }
        motion.target = index
        if reduceMotion { motion.scroll.hold(Double(index)) } else { motion.scroll.head(for: Double(index), at: .now, speed: speed) }
    }

    private func select(_ i: Int) {
        guard i != motion.target else { return }
        ticks += 1
        scroll(to: i)
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                if dragFrom == nil { dragFrom = motion.scroll.value(at: .now) }
                let free = (dragFrom ?? 0) - value.translation.width / Metrics.step
                // Past either end the row follows the finger at a third of its speed.
                let last = Double(places.count - 1)
                let position = free < 0 ? free * Metrics.rubber : free > last ? last + (free - last) * Metrics.rubber : free
                if Int(position.rounded()) != Int(motion.scroll.value(at: .now).rounded()) { ticks += 1 }
                motion.scroll.hold(position)
            }
            .onEnded { value in
                let from = dragFrom ?? 0
                let speed = -value.velocity.width / Metrics.step
                // A flick carries the row on, but never more than one card past where the drag began.
                let landing = (motion.scroll.value(at: .now) + speed * Metrics.coast).rounded()
                let nearest = from.rounded()
                dragFrom = nil
                touches += 1
                scroll(to: Int(min(max(landing, nearest - 1), nearest + 1)), speed: speed)
            }
    }

    /// The script a finger would follow: three steps to the right, then three back.
    private func runLoop() async {
        guard auto, dragFrom == nil else { return }
        let first = min(max(initialIndex, 0), places.count - 1)
        let steps = min(Metrics.autoSteps, places.count - 1 - first)
        guard steps > 0 else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, then go back to the first card before the script restarts.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                scroll(to: first)
                try await Task.sleep(for: .seconds(Metrics.stepTime))
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for k in 0..<steps {
                    try await clock.sleep(until: start + .seconds(Metrics.stepAt + Double(k) * Metrics.stepTime))
                    scroll(to: first + k + 1)
                }
                for k in 0..<steps {
                    try await clock.sleep(until: start + .seconds(Metrics.backAt + Double(k) * Metrics.stepTime))
                    scroll(to: first + steps - k - 1)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// A spring heading for a target, restarted from its current value and speed whenever the target changes.
private struct FrostedDestinationCarouselSpring {
    private var from: Double
    private var to: Double
    private var speed = 0.0
    private var start = Date.distantPast
    private let spring: Spring

    init(_ value: Double, _ spring: Spring) {
        from = value
        to = value
        self.spring = spring
    }

    func value(at date: Date) -> Double {
        from + spring.value(target: to - from, initialVelocity: speed, time: max(0, date.timeIntervalSince(start)))
    }

    mutating func head(for target: Double, at date: Date, speed: Double? = nil) {
        let elapsed = max(0, date.timeIntervalSince(start))
        let value = value(at: date)
        self.speed = speed ?? spring.velocity(target: to - from, initialVelocity: self.speed, time: elapsed)
        from = value
        to = target
        start = date
    }

    mutating func hold(_ value: Double) {
        from = value
        to = value
        speed = 0
        start = .now
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 380.0
    static let radius = 30.0

    static let cardW = 176.0
    static let cardH = 244.0
    static let cardRadius = 16.0
    /// From one card's middle to the next.
    static let step = cardW + 14
    /// Top of a waiting card; the selected one is `lift` higher.
    static let cardY = 78.0
    static let lift = 20.0
    /// How much the selected card's photo grows.
    static let zoom = 0.07
    /// Opacity of the text on a waiting card.
    static let dim = 0.72

    // The shadow: narrower than the card and lower, growing and darkening under the raised card.
    static let shadowInset = 18.0
    static let shadowDrop = 16.0
    static let shadowGrow = 0.1
    static let shadowDim = 0.4
    static let shadowFull = 0.75

    /// Past either end the row follows the finger at this share of its speed.
    static let rubber = 0.35
    /// A flick carries the row on for this many seconds of its speed.
    static let coast = 0.18

    // autoPlay (seconds): three steps to the right, then three back, `stepTime` apart.
    static let autoSteps = 3
    static let stepAt = 0.8
    static let stepTime = 1.1
    static let backAt = 4.4
    static let loop = 8.4
    /// After a touch the loop waits this long, then goes back to the first card and starts over.
    static let resumeAfter = 2.0
}

private let scrollSpring = Spring(mass: 1, stiffness: 170, damping: 20)
private let liftSpring = Spring(mass: 1, stiffness: 260, damping: 18)

#Preview {
    ZStack {
        Color(hex: 0xe6eaf0).ignoresSafeArea()
        FrostedDestinationCarousel(autoPlay: true)
    }
}
