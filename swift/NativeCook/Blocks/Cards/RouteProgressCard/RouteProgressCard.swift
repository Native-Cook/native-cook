import SwiftUI

/// How a leg is travelled: picks the icon and the drawing in the middle of the card.
enum RouteProgressCardTravel {
    case walk, drive, fly
}

struct RouteProgressCardPhoto: Equatable {
    /// Address of the photo.
    var photo: String
    /// The photo's main colour, shown until the photo arrives.
    var tint: Color
}

struct RouteProgressCardMode: Equatable {
    var travel: RouteProgressCardTravel
    var name: String
    /// The glow across the top of the card.
    var glow: Color
    /// The brighter colour of the marker, the ring and the bar.
    var accent: Color
    var distance: Int
    var time: Int
    var timeUnit: String
    /// How far along the leg the marker stops, 0…1.
    var share: Double
    var start: String
    var arrive: String
    /// The next two stops, each with a small photo.
    var stops: [String]
    var photos: [RouteProgressCardPhoto]

    static let samples = [
        RouteProgressCardMode(
            travel: .fly, name: "Flight", glow: Color(hex: 0x0d9488), accent: Color(hex: 0x5eead4),
            distance: 953, time: 3, timeUnit: "hours", share: 0.8, start: "11:15 AM", arrive: "2:15 PM",
            stops: ["Alder Field", "Coral Bay Airport"],
            photos: [
                RouteProgressCardPhoto(photo: routeProgressCardPhoto("1436491865332-7a61a109cc05"), tint: Color(hex: 0xb4b7b8)),
                RouteProgressCardPhoto(photo: routeProgressCardPhoto("1506905925346-21bda4d32df4"), tint: Color(hex: 0x6a6d7f)),
            ]
        ),
        RouteProgressCardMode(
            travel: .drive, name: "Driving", glow: Color(hex: 0x2563eb), accent: Color(hex: 0x93c5fd),
            distance: 1090, time: 18, timeUnit: "hours", share: 0.62, start: "10:40 AM", arrive: "4:40 AM",
            stops: ["Riverton", "Lakeside"],
            photos: [
                RouteProgressCardPhoto(photo: routeProgressCardPhoto("1500530855697-b586d89ba3ee"), tint: Color(hex: 0x6e6462)),
                RouteProgressCardPhoto(photo: routeProgressCardPhoto("1501594907352-04cda38ebc29"), tint: Color(hex: 0x79949c)),
            ]
        ),
        RouteProgressCardMode(
            travel: .walk, name: "Walking", glow: Color(hex: 0x6d28d9), accent: Color(hex: 0xa78bfa),
            distance: 2, time: 31, timeUnit: "minutes", share: 0.28, start: "10:00 AM", arrive: "10:31 AM",
            stops: ["Linden Circle", "Clock Tower"],
            photos: [
                RouteProgressCardPhoto(photo: routeProgressCardPhoto("1493976040374-85c8e12f0c0e"), tint: Color(hex: 0x5b5353)),
                RouteProgressCardPhoto(photo: routeProgressCardPhoto("1583417319070-4a69db38a482"), tint: Color(hex: 0x977b78)),
            ]
        ),
    ]
}

/// The fixed words on the card.
struct RouteProgressCardLabels: Equatable {
    var from = "From"
    var to = "To"
    var go = "Go"
    var done = "Done"
    var next = "Next Step"
    var distance = "Distance"
    var time = "Time"
    var start = "Start"
    var travelTime = "Travel time"
    var arrive = "Arrive"
    var nextStop = "Next stop"
    var then = "Then"
}

private func routeProgressCardPhoto(_ id: String) -> String {
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=102&h=102&q=80"
}

/// A route planner that unfolds from two pills. "From" and "To" sit side by side with a Go button
/// between them; tapping it merges them into a dark card for the first leg, whose marker travels while
/// its distance and time count up. The other ways to travel wait as pills above and below the card:
/// tapping one (or "Next Step") shrinks the card into a pill and opens that one, each with its own
/// glow colour and drawing. After the last leg the card folds back into the two pills.
struct RouteProgressCard: View {
    var from = "Alder Park"
    var to = "Coral Bay"
    /// The three ways to travel, top to bottom. The route is followed from the last to the first.
    var modes = RouteProgressCardMode.samples
    var distanceUnit = "miles"
    var labels = RouteProgressCardLabels()
    /// Taps Go, steps through the legs and folds the card away by itself, in a loop.
    var autoPlay = false
    /// Called when Go opens the card.
    var onStart: (() -> Void)? = nil
    /// Called with the index of the leg that was opened.
    var onSelect: ((Int) -> Void)? = nil
    /// Called when the card folds back into the two pills.
    var onFinish: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts taps: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    private enum Phase {
        case idle, open, closing
    }

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The Go button: 0 away … 1 between the pills, which it pushes apart.
        var go = RouteProgressCardTrack(0)
        /// 0 two pills … 1 merged into one capsule.
        var merge = RouteProgressCardTrack(0)
        /// 0 that capsule … 1 the open card with the other legs around it.
        var open = RouteProgressCardTrack(0)
        /// Position in the list of the leg whose card is open; it slides when another is picked.
        var active = RouteProgressCardTrack(0)
        /// Each leg's marker, 0…1.
        var progress: [RouteProgressCardTrack] = []
        /// Press feedback of each leg's pill, of Go and of Next Step.
        var press: [RouteProgressCardTrack] = []
        var goPress = RouteProgressCardTrack(0)
        var nextPress = RouteProgressCardTrack(0)

        var phase = Phase.idle
        /// The leg whose card is open, or opens next.
        var shown = 0
        /// Go is on screen and can be tapped.
        var armed = false
        /// The button between the pills reads Done instead of Go.
        var done = false
        var began = Date.now
        /// Things to do at a later frame; a new run of the card drops what is still waiting.
        var later: [(at: Date, run: () -> Void)] = []

        func runDue(_ now: Date) {
            guard later.contains(where: { $0.at <= now }) else { return }
            let due = later.filter { $0.at <= now }
            later.removeAll { $0.at <= now }
            for step in due { step.run() }
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion && modes.count > 1 }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let _ = motion.runDue(now)
            let go = motion.go.value(at: now)
            let merge = motion.merge.value(at: now)
            let open = motion.open.value(at: now)
            let panels = routeProgressCardLayout(open: open, active: motion.active.value(at: now), count: modes.count)
            let seen = clamp01((open - 0.45) / 0.4)
            let gap = mix(Metrics.pillGap, Metrics.goW + 12, go)

            ZStack {
                ForEach(modes.indices, id: \.self) { i in
                    if i < panels.count, i < motion.progress.count {
                        panel(i, layout: panels[i], seen: seen, now: now)
                            .opacity(merge)
                            .zIndex(i == motion.shown ? 1 : 0)
                    }
                }

                endPill(labels.from, from)
                    .position(x: mix(Metrics.width / 2 - gap / 2 - Metrics.endW, Metrics.width / 2 - Metrics.capsuleW / 2, merge) + Metrics.endW / 2, y: Metrics.centerY)
                    .opacity(1 - merge)
                endPill(labels.to, to)
                    .position(x: mix(Metrics.width / 2 + gap / 2, Metrics.width / 2 + Metrics.capsuleW / 2 - Metrics.endW, merge) + Metrics.endW / 2, y: Metrics.centerY)
                    .opacity(1 - merge)

                Text(motion.done ? labels.done : labels.go)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: Metrics.goW, height: Metrics.capsuleH)
                    .background(
                        Capsule().fill(LinearGradient(colors: [Color(hex: 0x8b5cf6), Color(hex: 0x6d28d9)], startPoint: .top, endPoint: .bottom))
                    )
                    .overlay(Capsule().strokeBorder(.white.opacity(0.22), lineWidth: 1))
                    .shadow(color: Color(hex: 0x6d28d9, opacity: 0.45), radius: 8, x: 0, y: 5)
                    .contentShape(Capsule())
                    .onTapGesture {
                        touch()
                        tapGo()
                    }
                    .scaleEffect(max(0, go) * (1 - 0.08 * motion.goPress.value(at: now)))
                    .opacity(clamp01(go * 4))
                    .position(x: Metrics.width / 2, y: Metrics.centerY)
                    .accessibilityAddTraits(.isButton)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: touches)
        .onAppear { reset() }
        .onChange(of: modes.count) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    /// One leg: a pill while it waits, the card while it is open.
    private func panel(_ i: Int, layout: RouteProgressCardPanel, seen: Double, now: Date) -> some View {
        let mode = modes[i]
        let expand = layout.expand
        let eased = inOut(motion.progress[i].value(at: now))
        let shape = RoundedRectangle(cornerRadius: layout.radius, style: .circular)
        // The pill's edge that faces the card catches the leg's colour.
        let above = clamp01(motion.active.value(at: now) - Double(i))
        let below = clamp01(Double(i) - motion.active.value(at: now))

        return ZStack(alignment: .top) {
            Color(hex: 0x0b0b0f)
            LinearGradient(colors: [mode.glow.opacity(0), mode.glow.opacity(0.5)], startPoint: UnitPoint(x: 0.5, y: 0.35), endPoint: .bottom)
                .frame(width: layout.frame.width, height: layout.frame.height)
                .opacity((1 - expand) * above)
            LinearGradient(colors: [mode.glow.opacity(0.5), mode.glow.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.65))
                .frame(width: layout.frame.width, height: layout.frame.height)
                .opacity((1 - expand) * below)

            RouteProgressCardFace(
                mode: mode, labels: labels, distanceUnit: distanceUnit, eased: eased,
                seconds: reduceMotion ? 0 : now.timeIntervalSince(motion.began),
                nextPress: motion.nextPress.value(at: now),
                onNext: {
                    touch()
                    motion.nextPress.retarget(1, .pulse)
                    next()
                }
            )
            .frame(width: Metrics.cardW, height: Metrics.cardH)
            .opacity(smooth(clamp01((expand - 0.45) / 0.55)) * seen)
            .allowsHitTesting(expand > 0.9)

            HStack(spacing: 8) {
                RouteProgressCardIcon(travel: mode.travel)
                    .frame(width: 20, height: 20)
                Text(mode.name)
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.9))
            }
            .fixedSize()
            .scaleEffect(mix(0.9, 1, expand), anchor: .leading)
            .frame(width: layout.frame.width, height: Metrics.pillH, alignment: .leading)
            .offset(x: mix(12, 18, expand), y: mix(0, 12, expand))
            .opacity(seen)
            .allowsHitTesting(false)

            Text("~\(mode.time) \(mode.timeUnit)")
                .font(.system(size: 11.5))
                .monospacedDigit()
                .foregroundStyle(.white.opacity(0.85))
                .fixedSize()
                .frame(width: layout.frame.width, height: Metrics.pillH, alignment: .trailing)
                .offset(x: -12)
                .opacity((1 - clamp01(expand * 3)) * seen)
                .allowsHitTesting(false)
        }
        .lineLimit(1)
        .frame(width: layout.frame.width, height: layout.frame.height, alignment: .top)
        .clipShape(shape)
        .overlay(shape.strokeBorder(.white.opacity(0.16), lineWidth: 1))
        .contentShape(shape)
        .onTapGesture {
            guard expand < 0.5 else { return }
            touch()
            motion.press[i].retarget(1, .pulse)
            select(i)
        }
        .shadow(color: .black.opacity(0.22), radius: 10, x: 0, y: 8)
        .scaleEffect(1 - 0.04 * motion.press[i].value(at: now))
        .position(x: layout.frame.midX, y: layout.frame.midY)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(expand < 0.5 ? .isButton : [])
    }

    /// One of the two pills the card unfolds from.
    private func endPill(_ label: String, _ place: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label)
                .font(.system(size: 7.5))
                .foregroundStyle(.white.opacity(0.5))
            Text(place)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(.white)
        }
        .lineLimit(1)
        .padding(.leading, 18)
        .frame(width: Metrics.endW, height: Metrics.capsuleH, alignment: .leading)
        .background(
            ZStack {
                Color(hex: 0x0b0b0f)
                LinearGradient(colors: [Color(hex: 0x6d28d9, opacity: 0.55), Color(hex: 0x6d28d9, opacity: 0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.6))
            }
        )
        .clipShape(Capsule())
        .overlay(Capsule().strokeBorder(.white.opacity(0.16), lineWidth: 1))
        .shadow(color: .black.opacity(0.22), radius: 10, x: 0, y: 8)
        .accessibilityElement(children: .combine)
    }

    // MARK: The card's steps

    private func move(_ track: ReferenceWritableKeyPath<Motion, RouteProgressCardTrack>, to target: Double, _ kind: RouteProgressCardTrack.Kind, delay: Double = 0) {
        // Under reduced motion nothing travels: every change is a jump.
        motion[keyPath: track].retarget(target, reduceMotion ? .jump : kind, delay: delay)
    }

    private func reset() {
        motion.progress = modes.map { _ in RouteProgressCardTrack(0) }
        motion.press = modes.map { _ in RouteProgressCardTrack(0) }
        motion.shown = modes.count - 1
        motion.active = RouteProgressCardTrack(Double(motion.shown))
        motion.open = RouteProgressCardTrack(0)
        motion.merge = RouteProgressCardTrack(0)
        motion.go = RouteProgressCardTrack(0)
        motion.phase = .idle
        motion.armed = false
        motion.later = [(Date.now.addingTimeInterval(Metrics.goIn), showGo)]
    }

    private func showGo() {
        motion.done = false
        motion.armed = true
        move(\.go, to: 1, .spring(popSpring))
    }

    private func touch() {
        touches += 1
    }

    private func tapGo() {
        guard motion.phase == .idle, motion.armed else { return }
        motion.goPress.retarget(1, .pulse)
        start()
    }

    /// Go: the pills merge, and the capsule they make grows into the first leg's card.
    private func start() {
        guard motion.phase == .idle, motion.armed else { return }
        motion.phase = .open
        motion.armed = false
        motion.later = []
        motion.shown = modes.count - 1
        motion.active = RouteProgressCardTrack(Double(motion.shown))
        move(\.go, to: 0, .tween(Metrics.goOut, easeIn))
        move(\.merge, to: 1, .tween(Metrics.mergeTime, easeInOut))
        move(\.open, to: 1, .spring(openSpring), delay: Metrics.openDelay)
        play(motion.shown, delay: Metrics.playDelay + Metrics.openDelay)
        onStart?()
    }

    /// Opens leg `i`: the open card shrinks to a pill as this one grows into the card.
    private func select(_ i: Int) {
        guard motion.phase == .open, i != motion.shown, modes.indices.contains(i) else { return }
        motion.shown = i
        move(\.active, to: Double(i), .spring(stackSpring))
        play(i, delay: Metrics.playDelay)
        onSelect?(i)
    }

    private func play(_ i: Int, delay: Double) {
        motion.progress[i] = RouteProgressCardTrack(0)
        move(\.progress[i], to: 1, .tween(Metrics.playTime, .linear), delay: delay)
    }

    private func next() {
        if motion.shown > 0 { select(motion.shown - 1) } else { finish() }
    }

    /// The card folds back into the two pills; Done shows between them for a moment, then Go returns.
    private func finish() {
        guard motion.phase == .open else { return }
        motion.phase = .closing
        motion.done = true
        move(\.open, to: 0, .spring(openSpring))
        move(\.merge, to: 0, .tween(Metrics.mergeTime, easeInOut), delay: Metrics.splitDelay)
        move(\.go, to: 1, .spring(popSpring), delay: Metrics.doneDelay)
        let now = Date.now
        motion.later = [
            (now.addingTimeInterval(Metrics.doneOut), {
                move(\.go, to: 0, .tween(Metrics.goOut, easeIn))
                motion.phase = .idle
            }),
            (now.addingTimeInterval(Metrics.closeTime), showGo),
        ]
        onFinish?()
    }

    /// The script a finger would follow: Go, then each leg in turn, then the end.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, fold the card away if it is open, and start over from the pills.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                if motion.phase == .open { finish() }
                if motion.phase != .idle || !motion.armed {
                    try await Task.sleep(for: .seconds(Metrics.closeTime - Metrics.goIn))
                }
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                try await clock.sleep(until: start + .seconds(Metrics.goAt))
                tapGo()
                for step in 1..<modes.count {
                    try await clock.sleep(until: start + .seconds(Metrics.goAt + Double(step) * Metrics.legTime))
                    let leg = modes.count - 1 - step
                    motion.press[leg].retarget(1, .pulse)
                    select(leg)
                }
                try await clock.sleep(until: start + .seconds(Metrics.goAt + Double(modes.count) * Metrics.legTime))
                finish()
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// The inside of an open card, laid out in a 330 × 204 box.
private struct RouteProgressCardFace: View {
    let mode: RouteProgressCardMode
    let labels: RouteProgressCardLabels
    let distanceUnit: String
    /// The marker's eased progress, 0…1: the figures count up with it.
    let eased: Double
    let seconds: Double
    let nextPress: Double
    let onNext: () -> Void

    private let dim = Color.white.opacity(0.45)

    var body: some View {
        let walking = mode.travel == .walk
        let labelY = walking ? 116.0 : 90.0

        ZStack {
            Canvas { context, _ in
                drawRouteProgressCard(context, mode: mode, eased: eased, seconds: seconds, nextPress: nextPress)
            }

            Text("\(Int((mode.share * eased * 100).rounded()))%")
                .font(.system(size: 8.5, weight: .medium))
                .foregroundStyle(.white.opacity(0.9))
                .place(x: 145, y: 20, width: 40, height: 16, .center)
            Text(labels.next)
                .font(.system(size: 9.5))
                .foregroundStyle(.white.opacity(0.9))
                .place(x: 248, y: 16, width: 48, height: 24, .center)
            Color.clear
                .contentShape(Rectangle())
                .place(x: 236, y: 8, width: 82, height: 40, .center)
                .onTapGesture(perform: onNext)
                .accessibilityLabel(labels.next)
                .accessibilityAddTraits(.isButton)

            Text(labels.distance).font(.system(size: 7.5)).foregroundStyle(dim)
                .place(x: 18, y: 48, width: 120, height: 10, .leading)
            Text("\(amount(mode.distance, eased)) \(distanceUnit)")
                .font(.system(size: 21, weight: .light)).tracking(-0.3).foregroundStyle(.white)
                .place(x: 18, y: 58, width: 180, height: 26, .leading)
            Text(labels.time).font(.system(size: 7.5)).foregroundStyle(dim)
                .place(x: 192, y: 48, width: 120, height: 10, .trailing)
            Text("~\(amount(mode.time, eased)) \(mode.timeUnit)")
                .font(.system(size: 21, weight: .light)).tracking(-0.3).foregroundStyle(.white)
                .place(x: 132, y: 58, width: 180, height: 26, .trailing)

            stamp(labels.start, mode.start, x: 18, y: labelY, .leading)
            if walking {
                stamp(labels.travelTime, "\(mode.time) min", x: 106, y: labelY, .leading)
            }
            stamp(labels.arrive, mode.arrive, x: 232, y: labelY, .trailing)

            ForEach(0..<2, id: \.self) { row in
                if row < mode.stops.count {
                    Text(row == 0 ? labels.nextStop : labels.then).font(.system(size: 7.5)).foregroundStyle(dim)
                        .place(x: 28, y: 156 + Double(row) * 16, width: 44, height: 12, .leading)
                    Text(mode.stops[row]).font(.system(size: 10.5)).foregroundStyle(.white.opacity(0.9))
                        .place(x: 74, y: 155 + Double(row) * 16, width: 150, height: 14, .leading)
                }
                if row < mode.photos.count {
                    RouteProgressCardThumb(photo: mode.photos[row])
                        .equatable()
                        .place(x: 238 + Double(row) * 40, y: 153, width: 34, height: 34, .center)
                }
            }
        }
        .monospacedDigit()
        .lineLimit(1)
    }

    /// A small grey label over its value.
    private func stamp(_ label: String, _ value: String, x: Double, y: Double, _ alignment: Alignment) -> some View {
        Group {
            Text(label).font(.system(size: 7.5)).foregroundStyle(dim)
                .place(x: x, y: y, width: 80, height: 10, alignment)
            Text(value).font(.system(size: 9.5)).foregroundStyle(.white.opacity(0.8))
                .place(x: x, y: y + 10, width: 80, height: 13, alignment)
        }
    }
}

private struct RouteProgressCardThumb: View, Equatable {
    let photo: RouteProgressCardPhoto

    var body: some View {
        photo.tint
            .overlay {
                AsyncImage(url: URL(string: photo.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    }
                }
            }
            .frame(width: 34, height: 34)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .circular))
    }
}

private extension View {
    /// Puts the view in a box whose top-left corner is at `x`, `y`.
    func place(x: Double, y: Double, width: Double, height: Double, _ alignment: Alignment) -> some View {
        frame(width: width, height: height, alignment: alignment)
            .position(x: x + width / 2, y: y + height / 2)
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 356.0

    static let cardW = 330.0
    static let cardH = 204.0
    /// Top of the open card: there is room for two pills above it and two below.
    static let cardY = 76.0
    static let centerY = cardY + cardH / 2
    // The two pills the card unfolds from, the capsule they merge into and the button between them.
    static let endW = 128.0
    static let capsuleW = 236.0
    static let capsuleH = 44.0
    static let pillGap = 6.0
    static let goW = 56.0

    // autoPlay (seconds): Go, then a new leg every `legTime`, then the card folds away.
    static let goIn = 0.35
    static let goAt = 0.9
    static let legTime = 2.3
    static let loop = 10.0
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0

    // Opening and closing, in seconds.
    static let goOut = 0.16
    static let mergeTime = 0.22
    static let openDelay = 0.14
    /// The marker sets off this long after its card starts to open, and travels for `playTime`.
    static let playDelay = 0.4
    static let playTime = 1.5
    static let splitDelay = 0.34
    static let doneDelay = 0.44
    static let doneOut = 1.25
    /// From the end of the last leg until Go is back.
    static let closeTime = 2.55

    // A waiting leg is a pill: wide next to the card, narrower one further away.
    static let pillH = 32.0
    static let nearW = 240.0
    static let farW = 184.0
    static let gap = 6.0
    static let cardRadius = 26.0
}

private struct RouteProgressCardPanel {
    var frame: CGRect
    var radius: Double
    /// 0 a pill … 1 the card.
    var expand: Double
}

/// Where every leg's panel is. `active` is the open card's place in the list: as it slides from one
/// leg to the next, one card shrinks to a pill while the other grows, and the card's top stays put.
/// At `open` 0 every panel is the capsule the two pills merged into.
private func routeProgressCardLayout(open: Double, active: Double, count: Int) -> [RouteProgressCardPanel] {
    var y = Metrics.cardY - active * (Metrics.pillH + Metrics.gap)
    return (0..<count).map { i in
        let away = abs(Double(i) - active)
        // A little past 1 near the card, so the spring's overshoot does not squeeze it.
        let expand = clamp01((1 - away) * 1.1)
        let width = mix(mix(Metrics.nearW, Metrics.farW, clamp01(away - 1)), Metrics.cardW, expand)
        let height = mix(Metrics.pillH, Metrics.cardH, expand)
        let top = y
        y += height + Metrics.gap
        return RouteProgressCardPanel(
            frame: CGRect(
                x: Metrics.width / 2 - mix(Metrics.capsuleW, width, open) / 2,
                y: mix(Metrics.centerY - Metrics.capsuleH / 2, top, open),
                width: mix(Metrics.capsuleW, width, open),
                height: mix(Metrics.capsuleH, height, open)
            ),
            radius: mix(Metrics.capsuleH / 2, mix(Metrics.pillH / 2, Metrics.cardRadius, expand), open),
            expand: expand
        )
    }
}

private let popSpring = Spring(mass: 1, stiffness: 320, damping: 18)
private let openSpring = Spring(mass: 1, stiffness: 200, damping: 22)
private let stackSpring = Spring(mass: 1, stiffness: 190, damping: 20)
private let easeIn = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.5, y: 0), endControlPoint: UnitPoint(x: 1, y: 1))
private let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.55, y: 1))

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }

/// Cubic ease-in-out, for the marker's run.
private func inOut(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// The share `eased` of `total`: a whole number, except that totals under ten count in tenths.
private func amount(_ total: Int, _ eased: Double) -> String {
    let value = Double(total) * eased
    if total >= 10 { return grouped(Int(value.rounded())) }
    let tenths = Int((value * 10).rounded())
    return tenths % 10 == 0 ? String(tenths / 10) : "\(tenths / 10).\(tenths % 10)"
}

private func grouped(_ n: Int) -> String {
    var digits = String(n)
    var out = ""
    while digits.count > 3 {
        out = "," + digits.suffix(3) + out
        digits = String(digits.dropLast(3))
    }
    return digits + out
}

#Preview {
    ZStack {
        Color(hex: 0xe8e8ea).ignoresSafeArea()
        RouteProgressCard(autoPlay: true)
    }
}
