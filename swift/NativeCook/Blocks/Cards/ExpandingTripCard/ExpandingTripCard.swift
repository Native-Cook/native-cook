import SwiftUI

struct ExpandingTripCardDay {
    var label: String
    var date: String
    var activity: String
    var duration: String
    var distance: String
}

struct ExpandingTripCardTrip {
    /// The name in capitals; a line break splits it over two lines.
    var title: String
    var dates: String
    /// The number in the pink badge, and the word under it.
    var length: String
    var lengthUnit = "DAYS"
    var arrivalLabel = "ARRIVAL"
    var arrivalPlace: String
    var arrivalTime: String
    /// Address of the photo, and the flat colour shown until it arrives.
    var photo: String
    var tint: Color
    /// The day cards shown once the trip is open.
    var days: [ExpandingTripCardDay]
}

/// A white panel with a row of tall photo cards that snaps card by card; a notch in the line under
/// the row and the arrival details follow the current card. Tapping that card grows it from its
/// place until the photo fills the panel: its title moves to the top left, the rest fades out and a
/// row of white day cards springs up from the bottom. A close button brings everything back.
struct ExpandingTripCard: View {
    var trips = expandingTripCardTrips
    /// Plays the interaction by itself: next trip, back, open, close.
    var autoPlay = false
    var heading = "TRIPS"
    var activitiesLabel = "ACTIVITIES"
    var tabLabel = "Trips"
    /// Called with the index of the trip the row snaps to.
    var onSelect: ((Int) -> Void)? = nil
    /// Called with the index of the trip that was opened or closed.
    var onOpen: ((Int) -> Void)? = nil
    var onClose: ((Int) -> Void)? = nil
    var onSearch: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Index of the card at the left of the row; fractional while the row moves.
    @State private var pos = 0.0
    @State private var open = 0.0
    /// Seconds since the trip was opened: the clock the day cards arrive on.
    @State private var stage = 0.0
    @State private var openIndex = 0
    @State private var isOpen = false
    @State private var dragFrom: Double? = nil
    /// Counts touches: a change restarts the autoPlay loop after a pause.
    @State private var touches = 0
    @State private var ticks = 0
    @State private var thumps = 0

    private var auto: Bool { autoPlay && !reduceMotion }

    private struct Run: Equatable {
        var auto: Bool
        var touches: Int
    }

    var body: some View {
        ExpandingTripCardFace(
            trips: trips, heading: heading, activitiesLabel: activitiesLabel, tabLabel: tabLabel,
            openIndex: openIndex, isOpen: isOpen, reduced: reduceMotion,
            onClose: {
                touches += 1
                thumps += 1
                close(report: true)
            },
            onSearch: { onSearch?() },
            pos: pos, open: open, stage: stage
        )
        .frame(width: Metrics.width, height: Metrics.height)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular))
        .shadow(color: .black.opacity(0.16), radius: 24, y: 16)
        .contentShape(Rectangle())
        .gesture(drag)
        .simultaneousGesture(tap)
        .sensoryFeedback(.selection, trigger: ticks)
        .sensoryFeedback(.impact(weight: .light), trigger: thumps)
        .task(id: Run(auto: auto, touches: touches)) { await play() }
    }

    private var tap: some Gesture {
        SpatialTapGesture()
            .onEnded { value in
                guard !isOpen, Metrics.row.contains(value.location), !trips.isEmpty else { return }
                touches += 1
                // The card under the finger: the current one opens, a neighbour comes forward.
                let index = Int(((value.location.x - Metrics.cardLeft) / Metrics.step + pos).rounded(.down))
                guard index >= 0, index < trips.count else { return }
                if index == Int(pos.rounded()) {
                    thumps += 1
                    openCard(index, report: true)
                } else {
                    slide(to: Double(index), report: true)
                }
            }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                guard !isOpen, Metrics.row.contains(value.startLocation) else { return }
                if dragFrom == nil {
                    dragFrom = pos
                    touches += 1
                }
                var still = Transaction()
                still.disablesAnimations = true
                withTransaction(still) {
                    pos = rubber((dragFrom ?? 0) - value.translation.width / Metrics.step)
                }
            }
            .onEnded { value in
                guard let from = dragFrom else { return }
                dragFrom = nil
                touches += 1
                // A swipe moves one card at most, however far it is thrown.
                let thrown = from - value.predictedEndTranslation.width / Metrics.step
                let last = Double(trips.count - 1)
                let target = min(last, max(0, min(from.rounded() + 1, max(from.rounded() - 1, thrown.rounded()))))
                slide(to: target, velocity: -value.velocity.width / Metrics.step, report: target != from.rounded())
            }
    }

    /// Past either end the row follows the finger at a third of its speed.
    private func rubber(_ value: Double) -> Double {
        let last = Double(trips.count - 1)
        if value < 0 { return value * Metrics.resistance }
        if value > last { return last + (value - last) * Metrics.resistance }
        return value
    }

    private func slide(to target: Double, velocity: Double = 0, report: Bool = false) {
        guard !reduceMotion else {
            pos = target
            if report { onSelect?(Int(target)) }
            return
        }
        let distance = target - pos
        withAnimation(.interpolatingSpring(
            mass: 1, stiffness: Metrics.slideStiffness, damping: Metrics.slideDamping,
            initialVelocity: abs(distance) > 0.001 ? velocity / distance : 0
        )) { pos = target }
        if report {
            ticks += 1
            onSelect?(Int(target))
        }
    }

    private func openCard(_ index: Int, report: Bool = false) {
        openIndex = index
        isOpen = true
        let spring = Animation.interpolatingSpring(mass: 1, stiffness: Metrics.openStiffness, damping: Metrics.openDamping)
        withAnimation(reduceMotion ? Metrics.reduced : spring) { open = 1 }
        withAnimation(.linear(duration: Metrics.stageSeconds - stage)) { stage = Metrics.stageSeconds }
        if report { onOpen?(index) }
    }

    private func close(report: Bool = false) {
        isOpen = false
        let spring = Animation.interpolatingSpring(mass: 1, stiffness: Metrics.openStiffness, damping: Metrics.openDamping)
        withAnimation(reduceMotion ? Metrics.reduced : spring) { open = 0 }
        withAnimation(.linear(duration: Metrics.stageLeave)) { stage = 0 }
        if report { onClose?(openIndex) }
    }

    /// The autoPlay loop. After a touch it waits, returns to the first card and starts over.
    private func play() async {
        guard auto, trips.count > 1 else { return }
        do {
            if touches > 0 {
                try await Task.sleep(for: .seconds(Metrics.idle))
                guard dragFrom == nil else { return }
                if isOpen { close() }
                slide(to: 0)
                try await Task.sleep(for: .seconds(Metrics.settle))
            }
            while true {
                try await Task.sleep(for: .seconds(Metrics.nextAt))
                slide(to: 1)
                try await Task.sleep(for: .seconds(Metrics.backAt - Metrics.nextAt))
                slide(to: 0)
                try await Task.sleep(for: .seconds(Metrics.openAt - Metrics.backAt))
                openCard(0)
                try await Task.sleep(for: .seconds(Metrics.closeAt - Metrics.openAt))
                close()
                try await Task.sleep(for: .seconds(Metrics.loop - Metrics.closeAt))
            }
        } catch {}
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 600.0
    static let radius = 28.0

    static let cardWidth = 232.0
    static let cardHeight = 290.0
    static let cardLeft = 22.0
    static let cardTop = 108.0
    static let cardRadius = 10.0
    /// Distance from one card to the next.
    static let step = 244.0
    /// The part of the panel that swipes and taps the cards.
    static let row = CGRect(x: 0, y: cardTop, width: width, height: cardHeight)
    static let resistance = 0.3

    // The line under the row, and the notch that points up at the current card.
    static let lineY = 424.0
    static let notchX = 46.0
    static let notchHeight = 9.0
    static let arrivalTop = 446.0
    /// How far the arrival details of a neighbouring card sit to the side.
    static let arrivalShift = 36.0
    static let tabY = 548.0

    // Open: the title's place and size, and the day cards.
    static let titleInset = CGPoint(x: 16, y: 16)
    static let openTitle = CGPoint(x: 24, y: 78)
    static let openTitleScale = 1.13
    static let activitiesY = 304.0
    static let dayTop = 322.0
    static let dayLeft = 24.0
    static let daySize = CGSize(width: 146, height: 172)
    static let mapSize = CGSize(width: 122, height: 56)
    static let dayGap = 12.0
    static let dayRise = 120.0

    static let slideStiffness = 170.0
    static let slideDamping = 24.0
    static let openStiffness = 170.0
    static let openDamping = 22.0
    // The day cards spring up one after another, on a clock that runs while the trip opens.
    static let dayAt = 0.28
    static let dayGapSeconds = 0.09
    static let dayStiffness = 210.0
    static let dayDamping = 19.0
    static let stageSeconds = 1.3
    static let stageLeave = 0.3
    static let reduced = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.2)

    // autoPlay (seconds): next trip, back to the first, open it, close it.
    static let nextAt = 0.8
    static let backAt = 2.3
    static let openAt = 3.8
    static let closeAt = 6.2
    static let loop = 8.0
    /// After a touch the loop waits this long, returns to the first card, and starts over.
    static let idle = 2.5
    static let settle = 1.2

    static let ink = Color(hex: 0x14171a)
    static let mute = Color(hex: 0x9a9fa6)
    static let line = Color(hex: 0xe1e3e7)
    static let mint = Color(hex: 0x5ed3b0)
    static let blue = Color(hex: 0x7f9cf5)
    static let pink = Color(hex: 0xf0688a)
    static let pinkPale = Color(hex: 0xfde4ea)

    static let searchIcon = "M9 2.8 C12.42 2.8 15.2 5.58 15.2 9 C15.2 12.42 12.42 15.2 9 15.2 C5.58 15.2 2.8 12.42 2.8 9 C2.8 5.58 5.58 2.8 9 2.8 Z M13.6 13.6 L17.5 17.5"
    static let backIcon = "M12.5 4.5 L7 10 L12.5 15.5"
    static let bootIcon = "M5 3 L9 3 L9 8.5 L13.5 10.5 C14.6 11 15.2 11.8 15.2 13 L15.2 13.8 L3.6 13.8 L3.6 10.5 C3.6 9.4 5 8.6 5 7 Z M2.8 15.6 L15.6 15.6"
    static let arrivalIcon = "M5 5 L13 13 M13 7 L13 13 L7 13 M4 16.5 L16 16.5"
    static let tripsIcon = "M11 3.5 C15.14 3.5 18.5 6.86 18.5 11 C18.5 15.14 15.14 18.5 11 18.5 C6.86 18.5 3.5 15.14 3.5 11 C3.5 6.86 6.86 3.5 11 3.5 Z M3.5 11 L18.5 11 M11 3.5 C8 6 8 16 11 18.5 C14 16 14 6 11 3.5"
    static let savedIcon = "M6.5 4 L15.5 4 L15.5 18 L11 14.5 L6.5 18 Z"
    static let alertsIcon = "M6 15 L6 10 C6 7 8 5 11 5 C14 5 16 7 16 10 L16 15 L17.5 16.5 L4.5 16.5 Z M9.5 18.5 L12.5 18.5"
    // The little map on a day card: water, a route and a pin with its dot.
    static let mapWater = "M70 0 L126 0 L126 30 C110 34 96 22 84 26 C76 28 72 12 70 0 Z"
    static let mapRoute = "M10 44 C30 30 44 46 62 34 C74 26 80 30 88 22"
    static let mapPin = "M88 24 C84 19 82.5 17 82.5 14.5 C82.5 11.5 85 9 88 9 C91 9 93.5 11.5 93.5 14.5 C93.5 17 92 19 88 24 Z"
    static let mapDot = "M88 12.7 C89 12.7 89.8 13.5 89.8 14.5 C89.8 15.5 89 16.3 88 16.3 C87 16.3 86.2 15.5 86.2 14.5 C86.2 13.5 87 12.7 88 12.7 Z"
}

/// Everything that is drawn, as a function of `pos`, `open` and `stage`; SwiftUI animates the three numbers.
private struct ExpandingTripCardFace: View, Animatable {
    let trips: [ExpandingTripCardTrip]
    let heading: String
    let activitiesLabel: String
    let tabLabel: String
    let openIndex: Int
    let isOpen: Bool
    let reduced: Bool
    let onClose: () -> Void
    let onSearch: () -> Void
    var pos: Double
    var open: Double
    var stage: Double

    var animatableData: AnimatablePair<Double, AnimatablePair<Double, Double>> {
        get { AnimatablePair(pos, AnimatablePair(open, stage)) }
        set {
            pos = newValue.first
            open = newValue.second.first
            stage = newValue.second.second
        }
    }

    private var o: Double { min(1, max(0, open)) }
    /// What is behind the open card fades over the first half of its growth.
    private var base: Double { 1 - min(1, o * 2) }
    /// Under reduced motion the card jumps to the panel's size halfway through the fade.
    private var grown: Double { reduced ? (open > 0.5 ? 1 : 0) : open }

    var body: some View {
        ZStack {
            Color.white

            Group {
                header
                Canvas { context, _ in drawLine(context) }
                ForEach(Array(trips.enumerated()), id: \.offset) { i, trip in
                    arrival(trip)
                        .opacity(max(0, 1 - abs(Double(i) - pos) * 1.6))
                        .offset(x: (Double(i) - pos) * Metrics.arrivalShift)
                }
                tabs
            }
            .opacity(base)

            ForEach(Array(trips.enumerated()), id: \.offset) { i, trip in
                card(trip, index: i, g: i == openIndex ? grown : 0)
                    .opacity(i == openIndex ? 1 : base)
                    .zIndex(i == openIndex ? 1 : 0)
            }
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    private var header: some View {
        ZStack {
            Button(action: onSearch) {
                expandingTripCardPath(Metrics.searchIcon)
                    .stroke(.white, style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
                    .frame(width: 20, height: 20)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Metrics.mint))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Search")
            .position(x: Metrics.width - 22 - 18, y: 40)

            Text(heading)
                .font(.system(size: 13, weight: .heavy))
                .tracking(1.2)
                .foregroundStyle(Metrics.ink)
                .frame(width: 200, height: 16, alignment: .leading)
                .position(x: Metrics.cardLeft + 100, y: 84)
        }
    }

    private func arrival(_ trip: ExpandingTripCardTrip) -> some View {
        HStack(spacing: 12) {
            expandingTripCardPath(Metrics.arrivalIcon)
                .stroke(Metrics.pink, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                .frame(width: 20, height: 20)
                .frame(width: 40, height: 40)
                .background(Circle().fill(Metrics.pinkPale))
            VStack(alignment: .leading, spacing: 0) {
                Text(trip.arrivalLabel)
                    .font(.system(size: 8, weight: .heavy))
                    .tracking(1)
                    .foregroundStyle(Metrics.pink)
                    .frame(height: 11)
                Text(trip.arrivalPlace)
                    .font(.system(size: 12.5, weight: .heavy))
                    .tracking(0.8)
                    .foregroundStyle(Metrics.ink)
                    .frame(height: 16)
                Text(trip.arrivalTime)
                    .font(.system(size: 11, weight: .regular))
                    .foregroundStyle(Metrics.mute)
                    .frame(height: 15)
            }
            Spacer(minLength: 0)
        }
        .lineLimit(1)
        .frame(width: Metrics.width - Metrics.cardLeft * 2, height: 42)
        .position(x: Metrics.width / 2, y: Metrics.arrivalTop + 21)
    }

    private var tabs: some View {
        ZStack {
            ForEach(Array([Metrics.tripsIcon, Metrics.savedIcon, Metrics.alertsIcon].enumerated()), id: \.offset) { i, icon in
                expandingTripCardPath(icon)
                    .stroke(i == 0 ? Metrics.ink : Color(hex: 0xb9bcc2), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                    .frame(width: 22, height: 22)
                    .position(x: 68 + Double(i) * 102, y: Metrics.tabY)
            }
            Text(tabLabel)
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(Metrics.ink)
                .frame(width: 60, height: 12)
                .position(x: 68, y: Metrics.tabY + 20)
        }
    }

    /// A hairline across the panel with a bump under the card that is current; the bump shrinks as
    /// its card leaves and the next one's grows.
    private func drawLine(_ context: GraphicsContext) {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: Metrics.lineY))
        for i in trips.indices {
            let height = Metrics.notchHeight * max(0, 1 - abs(Double(i) - pos))
            let x = Metrics.cardLeft + Metrics.notchX + (Double(i) - pos) * Metrics.step
            guard height > 0.01, x > 16, x < Metrics.width - 16 else { continue }
            path.addLine(to: CGPoint(x: x - 16, y: Metrics.lineY))
            path.addCurve(
                to: CGPoint(x: x, y: Metrics.lineY - height),
                control1: CGPoint(x: x - 8, y: Metrics.lineY), control2: CGPoint(x: x - 6, y: Metrics.lineY - height)
            )
            path.addCurve(
                to: CGPoint(x: x + 16, y: Metrics.lineY),
                control1: CGPoint(x: x + 6, y: Metrics.lineY - height), control2: CGPoint(x: x + 8, y: Metrics.lineY)
            )
        }
        path.addLine(to: CGPoint(x: Metrics.width, y: Metrics.lineY))
        context.stroke(path, with: .color(Metrics.line), lineWidth: 1.2)
    }

    /// One trip. `g` runs from 0, a card in the row, to 1, the photo filling the panel.
    private func card(_ trip: ExpandingTripCardTrip, index: Int, g: Double) -> some View {
        let left = Metrics.cardLeft + (Double(index) - pos) * Metrics.step
        let width = Metrics.cardWidth + (Metrics.width - Metrics.cardWidth) * g
        let height = Metrics.cardHeight + (Metrics.height - Metrics.cardHeight) * g
        let x = left * (1 - g)
        let y = Metrics.cardTop * (1 - g)
        let near = max(0, 1 - abs(Double(index) - pos))

        return ZStack(alignment: .topLeading) {
            ExpandingTripCardPhoto(url: trip.photo, tint: trip.tint)
                .frame(width: width, height: height)
            LinearGradient(colors: [.black.opacity(0.34), .black.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: 150)

            VStack(alignment: .leading, spacing: 6) {
                Text(trip.title)
                    .font(.system(size: 23, weight: .heavy))
                    .tracking(1.5)
                Text(trip.dates)
                    .font(.system(size: 10, weight: .bold))
                    .tracking(1)
            }
            .foregroundStyle(.white)
            .fixedSize()
            .scaleEffect(1 + (Metrics.openTitleScale - 1) * g, anchor: .topLeading)
            .offset(
                x: Metrics.titleInset.x + (Metrics.openTitle.x - Metrics.titleInset.x) * g,
                y: Metrics.titleInset.y + (Metrics.openTitle.y - Metrics.titleInset.y) * g
            )

            HStack(spacing: 6) {
                expandingTripCardPath(Metrics.bootIcon)
                    .stroke(.white, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                    .frame(width: 18, height: 18)
                    .frame(width: 38, height: 38)
                    .background(Circle().fill(Metrics.blue))
                VStack(spacing: 0) {
                    Text(trip.length)
                        .font(.system(size: 13, weight: .heavy))
                        .frame(height: 14)
                    Text(trip.lengthUnit)
                        .font(.system(size: 5.5, weight: .heavy))
                        .tracking(0.5)
                        .frame(height: 7)
                }
                .foregroundStyle(.white)
                .frame(width: 38, height: 38)
                .background(Circle().fill(Metrics.pink))
            }
            .offset(x: 14, y: height - 14 - 38)
            .opacity(1 - min(1, max(0, g) * 2.5))

            if index == openIndex {
                Button(action: onClose) {
                    expandingTripCardPath(Metrics.backIcon)
                        .stroke(.white, style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                        .frame(width: 20, height: 20)
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(Color.rgba(20, 24, 28, 0.36)))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
                .offset(x: 20, y: 22)
                .opacity(min(1, max(0, o * 2 - 1)))
                .allowsHitTesting(isOpen)

                Text(activitiesLabel)
                    .font(.system(size: 10, weight: .heavy))
                    .tracking(1.5)
                    .foregroundStyle(.white)
                    .frame(height: 13)
                    .offset(x: Metrics.dayLeft, y: Metrics.activitiesY)
                    .opacity(min(1, max(0, (stage - 0.2) / 0.25)))

                ForEach(Array(trip.days.enumerated()), id: \.offset) { j, day in
                    let e = expandingTripCardDayAt(stage, order: j, reduced: reduced)
                    dayCard(day)
                        .offset(
                            x: Metrics.dayLeft + Double(j) * (Metrics.daySize.width + Metrics.dayGap),
                            y: Metrics.dayTop + (reduced ? 0 : (1 - e) * Metrics.dayRise)
                        )
                        .opacity(min(1, max(0, e * 2.5)))
                }
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.cardRadius + (Metrics.radius - Metrics.cardRadius) * min(1, max(0, g)), style: .circular))
        .position(x: x + width / 2, y: y + height / 2)
        .accessibilityElement(children: index == openIndex && isOpen ? .contain : .ignore)
        .accessibilityLabel(Text(verbatim: "\(trip.title.replacingOccurrences(of: "\n", with: " ")), \(trip.dates)"))
        .accessibilityHidden(near < 0.5 && !(index == openIndex && isOpen))
    }

    private func dayCard(_ day: ExpandingTripCardDay) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular).fill(.white)
            Text(day.label)
                .font(.system(size: 7.5, weight: .heavy))
                .tracking(0.8)
                .frame(height: 10)
                .offset(x: 12, y: 12)
            Text(day.date)
                .font(.system(size: 17, weight: .bold))
                .frame(height: 22)
                .offset(x: 12, y: 25)
            Text(day.activity)
                .font(.system(size: 10, weight: .semibold))
                .frame(height: 13)
                .offset(x: 12, y: 62)
            ZStack(alignment: .topLeading) {
                Color(hex: 0xcfe8c6)
                expandingTripCardPath(Metrics.mapWater).fill(Color(hex: 0xb9def0))
                expandingTripCardPath(Metrics.mapRoute).stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                expandingTripCardPath(Metrics.mapPin).fill(Color(hex: 0x1c1f24))
                expandingTripCardPath(Metrics.mapDot).fill(.white)
            }
            .frame(width: Metrics.mapSize.width, height: Metrics.mapSize.height)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .circular))
            .offset(x: 12, y: 82)
            HStack {
                Text(day.duration)
                Spacer()
                Text(day.distance)
            }
            .font(.system(size: 9, weight: .bold))
            .frame(width: Metrics.mapSize.width, height: 12)
            .offset(x: 12, y: 148)
        }
        .foregroundStyle(Metrics.ink)
        .lineLimit(1)
        .frame(width: Metrics.daySize.width, height: Metrics.daySize.height, alignment: .topLeading)
    }
}

/// How far day card number `order` has come up, `stage` seconds after the trip was opened: a
/// spring of mass 1 released at rest, so it runs a little past its place and settles.
private func expandingTripCardDayAt(_ stage: Double, order: Int, reduced: Bool) -> Double {
    let t = stage - Metrics.dayAt - Double(order) * Metrics.dayGapSeconds
    guard t > 0 else { return 0 }
    if reduced { return min(1, t / 0.2) }
    let decay = Metrics.dayDamping / 2
    let omega = (Metrics.dayStiffness - decay * decay).squareRoot()
    return 1 - exp(-decay * t) * (cos(omega * t) + decay / omega * sin(omega * t))
}

/// A remote photo cropped to fill, over a flat tint until it arrives.
private struct ExpandingTripCardPhoto: View {
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

private func expandingTripCardPath(_ svg: String) -> Path {
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

private func expandingTripCardPhotoURL(_ id: String) -> String {
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=900&q=80"
}

let expandingTripCardTrips: [ExpandingTripCardTrip] = [
    ExpandingTripCardTrip(
        title: "SEILAND\nNORWAY", dates: "3 AUG - 9 AUG", length: "6",
        arrivalPlace: "OSLO LUFTHAVN", arrivalTime: "3 Aug, 12:00 PM",
        photo: expandingTripCardPhotoURL("1464822759023-fed622ff2c3b"), tint: Color(hex: 0x6f8aa0),
        days: [
            ExpandingTripCardDay(label: "DAY 1", date: "3 Aug.", activity: "Hiking", duration: "8 hours", distance: "22 km"),
            ExpandingTripCardDay(label: "DAY 2", date: "4 Aug.", activity: "Kayaking", duration: "5 hours", distance: "14 km"),
            ExpandingTripCardDay(label: "DAY 3", date: "5 Aug.", activity: "Fishing", duration: "4 hours", distance: "6 km"),
        ]
    ),
    ExpandingTripCardTrip(
        title: "MALLORCA\nSPAIN", dates: "18 AUG - 25 AUG", length: "7",
        arrivalPlace: "PALMA DE MALLORCA", arrivalTime: "18 Aug, 9:40 AM",
        photo: expandingTripCardPhotoURL("1506929562872-bb421503ef21"), tint: Color(hex: 0x3fb3b8),
        days: [
            ExpandingTripCardDay(label: "DAY 1", date: "18 Aug.", activity: "Sailing", duration: "6 hours", distance: "30 km"),
            ExpandingTripCardDay(label: "DAY 2", date: "19 Aug.", activity: "Snorkelling", duration: "3 hours", distance: "2 km"),
            ExpandingTripCardDay(label: "DAY 3", date: "20 Aug.", activity: "Cycling", duration: "5 hours", distance: "48 km"),
        ]
    ),
    ExpandingTripCardTrip(
        title: "DOLOMITES\nITALY", dates: "2 SEP - 8 SEP", length: "6",
        arrivalPlace: "VENICE MARCO POLO", arrivalTime: "2 Sep, 2:15 PM",
        photo: expandingTripCardPhotoURL("1501785888041-af3ef285b470"), tint: Color(hex: 0x4aa39c),
        days: [
            ExpandingTripCardDay(label: "DAY 1", date: "2 Sep.", activity: "Rowing", duration: "2 hours", distance: "4 km"),
            ExpandingTripCardDay(label: "DAY 2", date: "3 Sep.", activity: "Hiking", duration: "7 hours", distance: "18 km"),
            ExpandingTripCardDay(label: "DAY 3", date: "4 Sep.", activity: "Climbing", duration: "6 hours", distance: "9 km"),
        ]
    ),
]

#Preview {
    ZStack {
        Color(hex: 0xe9eaec).ignoresSafeArea()
        ExpandingTripCard(autoPlay: true)
    }
}
