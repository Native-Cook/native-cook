import SwiftUI

struct BoardingPassStackPass: Equatable {
    /// The pass's colour at its top and at its bottom.
    var color: Color
    var deep: Color
    var fromCity: String
    var fromCode: String
    var toCity: String
    var toCode: String
    /// Arrival and local time at the destination, shown in the header.
    var arrival: String
    var localTime: String
    var flight: String
    var date: String
    var boarding: String
    var cabin: String
    var gate: String
    var seat: String
    var status: String
    var sequence: String
    var bags: String

    static let samples = [
        BoardingPassStackPass(
            color: Color(hex: 0xf0bd2e), deep: Color(hex: 0xdba616),
            fromCity: "Berlin", fromCode: "BER", toCity: "Lisbon", toCode: "LIS",
            arrival: "19:30", localTime: "18:30", flight: "TN582", date: "03 OCT", boarding: "17:35",
            cabin: "First", gate: "Z52", seat: "2A", status: "Gold", sequence: "042", bags: "2 × 32 kg"
        ),
        BoardingPassStackPass(
            color: Color(hex: 0xf58468), deep: Color(hex: 0xe5674a),
            fromCity: "Oslo", fromCode: "OSL", toCity: "Tokyo", toCode: "HND",
            arrival: "08:05", localTime: "16:05", flight: "TN204", date: "11 NOV", boarding: "12:10",
            cabin: "Business", gate: "C14", seat: "7K", status: "Gold", sequence: "118", bags: "2 × 23 kg"
        ),
        BoardingPassStackPass(
            color: Color(hex: 0x86dcbb), deep: Color(hex: 0x62c79f),
            fromCity: "Madrid", fromCode: "MAD", toCity: "Reykjavik", toCode: "KEF",
            arrival: "23:45", localTime: "21:45", flight: "TN931", date: "24 JAN", boarding: "20:15",
            cabin: "Economy", gate: "A7", seat: "23F", status: "Gold", sequence: "207", bags: "1 × 23 kg"
        ),
    ]
}

/// A wallet of boarding passes on a dark panel, under a header with the destination of the pass in
/// front. A tap on a pass behind, or a swipe up, shuffles the stack: the front pass ducks down, the
/// next one is pulled up out of the stack, tips towards the viewer and drops in front, and the old
/// one tucks in at the back. The header rolls to the new city and times, and the small plane
/// between the airport codes flies across once.
struct BoardingPassStack: View {
    /// The passes, front first. At least three.
    var passes = BoardingPassStackPass.samples
    /// Brings the next pass to the front every 2.4 seconds, as if a finger pulled it.
    var autoPlay = false
    var airline = "Tern Airways"
    var passenger = "Lena Hartwell"
    var destinationLabel = "DESTINATION"
    var arrivalLabel = "Arrival time"
    var localTimeLabel = "Local time"
    var rideLabel = "Book a ride"
    /// Called with the index of the pass that is now in front.
    var onChange: ((Int) -> Void)? = nil
    var onRide: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var deck = Deck()
    /// Keeps the timeline running while a touch is being followed.
    @State private var awake = false
    @State private var touches = 0
    @State private var pulls = 0

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: !auto && !awake)) { timeline in
            let count = passes.count
            let _ = deck.advance(to: timeline.date, count: count, duration: reduceMotion ? Metrics.shuffleReduced : Metrics.shuffle)
            let pos = deck.pos
            let front = wrapIndex(Int(pos.rounded()), count)

            ZStack(alignment: .topLeading) {
                LinearGradient(colors: [Color(hex: 0x161d30), Color(hex: 0x0a0e18)], startPoint: .top, endPoint: .bottom)
                glow(Color(hex: 0x4a6fe0), opacity: 0.2, x: 60, y: 30)
                // A second light in the colour of the pass in front.
                ForEach(passes.indices, id: \.self) { index in
                    glow(passes[index].color, opacity: 0.2 * max(0, 1 - abs(centered(index, pos, count))), x: 290, y: 120)
                }

                header(pos: pos, count: count)

                ForEach(passes.indices, id: \.self) { index in
                    let pose = pose(relative(index, pos, count), count: count, reduced: reduceMotion)
                    PassCard(pass: passes[index], airline: airline, passenger: passenger, seed: index, shade: pose.shade, flight: pose.flight)
                        .scaleEffect(pose.scale)
                        .rotation3DEffect(
                            .degrees(pose.tilt), axis: (x: 1, y: 0, z: 0),
                            perspective: Metrics.passHeight / Metrics.perspective
                        )
                        .opacity(pose.alpha)
                        .offset(x: Metrics.passX, y: Metrics.frontY + pose.top - (1 - pose.scale) * Metrics.passHeight / 2)
                        .zIndex(pose.z)
                        .accessibilityHidden(index != front)
                }

                // Touches on the stack: a tap on a strip behind, or a swipe up anywhere on it.
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: Metrics.width, height: Metrics.height - Metrics.stackY)
                    .offset(y: Metrics.stackY)
                    .gesture(touch)
                    .zIndex(9)
                    .accessibilityAction(named: "Next pass") { pull(1) }
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous))
            .onChange(of: front) { onChange?(front) }
        }
        .sensoryFeedback(.impact(weight: .light), trigger: pulls)
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .task(id: touches) {
            guard touches > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.userHold + Metrics.shuffle * 2))
            awake = false
        }
    }

    private func glow(_ color: Color, opacity: Double, x: Double, y: Double) -> some View {
        Circle()
            .fill(RadialGradient(colors: [color.opacity(opacity), color.opacity(0)], center: .center, startRadius: 0, endRadius: Metrics.glowRadius))
            .frame(width: Metrics.glowRadius * 2, height: Metrics.glowRadius * 2)
            .offset(x: x - Metrics.glowRadius, y: y - Metrics.glowRadius)
            .allowsHitTesting(false)
    }

    private func header(pos: Double, count: Int) -> some View {
        let cityAt = headerPosition(pos, from: 0.3, to: 0.75)
        let timesAt = headerPosition(pos, from: 0.55, to: 0.98)
        return ZStack(alignment: .topLeading) {
            roller(width: Metrics.width - 40, height: Metrics.cityBox, at: cityAt, count: count) { index in
                Text(passes[index].toCity)
                    .font(.system(size: 42, weight: .thin))
                    .foregroundStyle(.white)
            }
            .offset(x: 20, y: 26)
            .accessibilityAddTraits(.isHeader)

            Text(destinationLabel)
                .font(.system(size: 10, weight: .semibold))
                .tracking(1.5)
                .foregroundStyle(.white.opacity(0.45))
                .frame(width: Metrics.width, height: 14)
                .offset(y: 82)

            ForEach(0..<2, id: \.self) { side in
                VStack(spacing: 2) {
                    roller(width: Metrics.column, height: Metrics.timeBox, at: timesAt, count: count) { index in
                        Text(side == 0 ? passes[index].arrival : passes[index].localTime)
                            .font(.system(size: 22, weight: .thin))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                    }
                    Text(side == 0 ? arrivalLabel : localTimeLabel)
                        .font(.system(size: 10))
                        .foregroundStyle(.white.opacity(0.5))
                        .frame(height: 13)
                }
                .frame(width: Metrics.column)
                .offset(x: 20 + Double(side) * Metrics.column * 2, y: Metrics.rowY)
                .accessibilityElement(children: .combine)
            }

            Button {
                onRide?()
            } label: {
                Text(rideLabel)
                    .font(.system(size: 15, weight: .light))
                    .foregroundStyle(.white)
                    .frame(width: Metrics.column, height: Metrics.rowHeight)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .offset(x: 20 + Metrics.column, y: Metrics.rowY)

            // Dashed rules between the three items.
            Path { path in
                for x in [20 + Metrics.column, 20 + Metrics.column * 2] {
                    path.move(to: CGPoint(x: x, y: Metrics.rowY + 3))
                    path.addLine(to: CGPoint(x: x, y: Metrics.rowY + Metrics.rowHeight - 3))
                }
            }
            .stroke(.white.opacity(0.22), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
        }
        .lineLimit(1)
    }

    /// A box the header's values roll through: the old one leaves upwards as the new one comes up from below.
    private func roller<Content: View>(
        width: Double, height: Double, at position: Double, count: Int, @ViewBuilder content: @escaping (Int) -> Content
    ) -> some View {
        ZStack {
            ForEach(passes.indices, id: \.self) { index in
                let place = centered(index, position, count)
                content(index)
                    .fixedSize()
                    .offset(y: reduceMotion ? 0 : place * height)
                    .opacity(reduceMotion ? max(0, 1 - abs(place)) : rollFade(place))
            }
        }
        .frame(width: width, height: height)
        .clipped()
    }

    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .onEnded { value in
                let move = value.translation
                if move.height < -Metrics.swipe, abs(move.height) > abs(move.width) {
                    pull(1)
                } else if abs(move.width) < Metrics.tapSlop, abs(move.height) < Metrics.tapSlop {
                    // A tap: the strips of the two passes behind lie above the front pass.
                    let y = value.startLocation.y
                    if y < Metrics.frontY - Metrics.stackY, abs(deck.pos - deck.pos.rounded()) < 0.001 {
                        pull(y < Metrics.frontY + Metrics.slotB.top - Metrics.stackY ? 2 : 1)
                    }
                }
            }
    }

    private func sync() {
        deck.looping = auto
        deck.hold = 0
        deck.clock = 0
        deck.base = deck.pos.rounded()
    }

    /// A touch takes over from the loop, which starts again from rest a little later.
    private func pull(_ passes: Double) {
        deck.hold = Metrics.userHold
        deck.requests += passes
        awake = true
        touches += 1
        pulls += 1
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 600.0
    static let radius = 32.0
    static let glowRadius = 190.0

    // Header: the city's box, the row of three items, and the boxes its values roll through.
    static let cityBox = 54.0
    static let rowY = 112.0
    static let rowHeight = 44.0
    static let column = 100.0
    static let timeBox = 28.0

    static let passX = 20.0
    static let passWidth = 300.0
    static let passHeight = 330.0
    static let passRadius = 12.0
    /// Height of the strip with the airline, the gate and the seat.
    static let strip = 52.0
    /// Where the stub tears off, and the radius of the notch at each end of that line.
    static let tear = 230.0
    static let notch = 7.0
    /// Top of the front pass in the panel, and of the area that takes the stack's touches.
    static let frontY = 246.0
    static let stackY = 170.0

    // Where a pass rests behind the front one: its top edge above the front's, scale and darkening.
    static let slotA = Slot(top: -34, scale: 0.94, shade: 0.16)
    static let slotB = Slot(top: -68, scale: 0.88, shade: 0.3)
    /// How far the front pass ducks down, and how far the next one is pulled up, before they swap.
    static let sink = 190.0
    static let lift = 130.0
    /// Degrees the pulled pass tips its top edge towards the viewer, and the eye's distance in points.
    static let tilt = 14.0
    static let perspective = 900.0
    /// Points the plane flies in from the left.
    static let flight = 56.0

    /// Seconds one shuffle takes, and the share of it after which the two passes have swapped depth.
    static let shuffle = 1.3
    static let shuffleReduced = 0.35
    static let swap = 0.45
    // The spring the pulled pass lands on.
    static let stiffness = 170.0
    static let damping = 16.0
    static let mass = 1.0

    // autoPlay (seconds): the three default passes go round once per `loop`, the first pull at
    // `first` and the next ones every `step`.
    static let loop = 7.2
    static let first = 0.8
    static let step = loop / 3
    /// After a touch the loop waits this long before it starts again.
    static let userHold = 2.5
    /// Finger travel upwards that pulls the next pass, and the most a tap may move.
    static let swipe = 36.0
    static let tapSlop = 8.0

    static let ink = Color(hex: 0x1b2233)
}

private struct Slot {
    let top: Double
    let scale: Double
    let shade: Double
}

private struct Pose {
    /// Top edge, measured from where the front pass rests.
    var top = 0.0
    var scale = 1.0
    /// Degrees around the horizontal axis; negative tips the top edge towards the viewer.
    var tilt = 0.0
    /// Opacity of the dark veil on a pass behind.
    var shade = 0.0
    var alpha = 1.0
    /// How far the plane has flown, 0…1.
    var flight = 1.0
    var z = 0.0
}

/// Per-frame state of the stack. `pos` counts the shuffles so far; its fraction is how far the running one has come.
private final class Deck {
    var last: Date? = nil
    var pos = 0.0
    var target = 0.0
    var looping = false
    /// True while the loop is the one moving the stack.
    var live = false
    /// Shuffles asked for by a finger that the next frame has to start.
    var requests = 0.0
    /// Where the loop started counting from, and its clock.
    var base = 0.0
    var clock = 0.0
    /// Seconds the loop still waits after a touch.
    var hold = 0.0

    func advance(to date: Date, count: Int, duration: Double) {
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
                // A touch took over: the shuffle the loop was in the middle of finishes first.
                target = (pos - 0.000001).rounded(.up)
                live = false
            }
            if requests > 0 {
                target = min(target + requests, pos.rounded(.down) + 2)
                requests = 0
            }
            if pos < target {
                pos = min(target, pos + dt / duration)
            } else if hold > 0 {
                hold -= dt
                if hold <= 0 {
                    base = Double(wrapIndex(Int(pos.rounded()), count))
                    pos = base
                    target = base
                    clock = 0
                }
            }
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

/// Step response of the landing spring `seconds` after it is released: 0 → 1 with one overshoot.
private func spring(_ seconds: Double) -> Double {
    guard seconds > 0 else { return 0 }
    let omega = (Metrics.stiffness / Metrics.mass).squareRoot()
    let zeta = Metrics.damping / (2 * (Metrics.stiffness * Metrics.mass).squareRoot())
    let damped = omega * (1 - zeta * zeta).squareRoot()
    return 1 - exp(-zeta * omega * seconds) * (cos(damped * seconds) + zeta * omega / damped * sin(damped * seconds))
}

/// Shuffles done `clock` seconds into the autoPlay loop.
private func script(_ clock: Double, count: Int) -> Double {
    var done = 0.0
    for index in 0..<count {
        done += segment(clock, Metrics.first + Metrics.step * Double(index), Metrics.first + Metrics.step * Double(index) + Metrics.shuffle)
    }
    return done
}

/// Place of pass `index` in the stack: 0 in front, 1 and 2 behind it, and below 0 while it ducks away.
private func relative(_ index: Int, _ pos: Double, _ count: Int) -> Double {
    let n = Double(count)
    var place = (Double(index) - pos).truncatingRemainder(dividingBy: n)
    if place <= -1 { place += n }
    if place > n - 1 { place -= n }
    return place
}

/// The same place counted from the nearest side, for things that cross-fade: −0.5 … 0.5 around the front.
private func centered(_ index: Int, _ pos: Double, _ count: Int) -> Double {
    let n = Double(count)
    var place = (Double(index) - pos).truncatingRemainder(dividingBy: n)
    if place <= -n / 2 { place += n }
    if place > n / 2 { place -= n }
    return place
}

/// The header changes later than the stack: its position moves only during a part of each shuffle.
private func headerPosition(_ pos: Double, from: Double, to: Double) -> Double {
    let whole = pos.rounded(.down)
    return whole + easeInOut(segment(pos - whole, from, to))
}

/// Opacity of a value that rolls through its box at `place`: it fades only as it leaves the box.
private func rollFade(_ place: Double) -> Double {
    smooth(min(1, max(0, (0.75 - abs(place)) / 0.3)))
}

/// Where a pass rests `depth` places behind the front. Passes deeper than the second stay hidden under it.
private func slot(_ depth: Int) -> (top: Double, scale: Double, shade: Double, alpha: Double) {
    switch depth {
    case 0: (0, 1, 0, 1)
    case 1: (Metrics.slotA.top, Metrics.slotA.scale, Metrics.slotA.shade, 1)
    case 2: (Metrics.slotB.top, Metrics.slotB.scale, Metrics.slotB.shade, 1)
    default: (Metrics.slotB.top, Metrics.slotB.scale, Metrics.slotB.shade, 0)
    }
}

private func pose(_ at: Double, count: Int, reduced: Bool) -> Pose {
    // Reduced motion: every pass jumps to its next place half-way while the stack fades through.
    var place = at
    var fade = 1.0
    if reduced {
        let nearest = place.rounded()
        fade = 1 - 2 * abs(place - nearest)
        place = nearest
    }

    var pose = Pose()
    if place <= 0 {
        // The front pass ducks down, and once the next one is over it, comes up again at the back.
        let t = -place
        let back = slot(count - 1)
        if t < Metrics.swap {
            let down = easeInOut(segment(t, 0, 0.42))
            pose.top = Metrics.sink * down
            pose.scale = 1 - 0.03 * down
            pose.shade = 0.1 * down
            pose.z = 3
        } else {
            let up = easeOut(segment(t, Metrics.swap, 0.95))
            pose.top = mix(Metrics.sink, back.top, up)
            pose.scale = mix(0.97, back.scale, up)
            pose.shade = mix(0.1, back.shade, up)
            pose.alpha = mix(1, back.alpha, up)
        }
    } else if place <= 1 {
        // The next pass is pulled up out of the stack, tips towards the viewer and drops in front.
        let part = 1 - place
        let from = slot(1)
        let rise = easeInOut(segment(part, 0, 0.42))
        if part < Metrics.swap {
            pose.top = from.top - Metrics.lift * rise
            pose.scale = mix(from.scale, 1.03, rise)
            pose.z = 2
        } else {
            let landed = mix(spring((part - Metrics.swap) * Metrics.shuffle), 1, smooth(segment(part, 0.88, 1)))
            pose.top = mix(from.top - Metrics.lift, 0, landed)
            pose.scale = mix(1.03, 1, landed)
            pose.z = 4
        }
        pose.tilt = -Metrics.tilt * sin(.pi * segment(part, 0.08, 0.85))
        pose.shade = from.shade * (1 - rise)
        pose.flight = easeOut(segment(part, 0.6, 1))
    } else {
        // The passes further back move up one place.
        let depth = Int(place.rounded(.up))
        let from = slot(depth)
        let to = slot(depth - 1)
        let step = easeInOut(segment(Double(depth) - place, 0.3, 0.8))
        pose.top = mix(from.top, to.top, step)
        pose.scale = mix(from.scale, to.scale, step)
        pose.shade = mix(from.shade, to.shade, step)
        pose.alpha = mix(from.alpha, to.alpha, step)
        pose.z = depth == 2 ? 1 : 0
    }
    pose.alpha *= fade
    return pose
}

/// One boarding pass: strip, route, fields, and the stub with its code below the tear line.
private struct PassCard: View {
    let pass: BoardingPassStackPass
    let airline: String
    let passenger: String
    let seed: Int
    let shade: Double
    let flight: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            PassShape()
                .fill(LinearGradient(colors: [pass.color, pass.deep], startPoint: .top, endPoint: .bottom))
                .shadow(color: .black.opacity(0.3), radius: 14, y: -4)
            // The strip is a shade lighter than the body.
            PassShape().fill(.white.opacity(0.1)).frame(height: Metrics.strip, alignment: .top).clipped()

            HStack(spacing: 8) {
                Mark()
                Text(airline).font(.system(size: 15, weight: .bold))
            }
            .frame(height: Metrics.strip)
            .offset(x: 18)

            HStack(spacing: 14) {
                field("GATE", pass.gate, size: 16, alignment: .trailing)
                field("SEAT", pass.seat, size: 16, alignment: .trailing)
            }
            .frame(width: Metrics.passWidth - 36, height: Metrics.strip, alignment: .trailing)
            .offset(x: 18)

            Rectangle().fill(Metrics.ink.opacity(0.14)).frame(width: Metrics.passWidth, height: 1).offset(y: Metrics.strip)

            HStack {
                Text(pass.fromCity.uppercased())
                Spacer()
                Text(pass.toCity.uppercased())
            }
            .font(.system(size: 9, weight: .semibold))
            .tracking(0.4)
            .foregroundStyle(Metrics.ink.opacity(0.62))
            .frame(width: Metrics.passWidth - 36, height: 12)
            .offset(x: 18, y: 66)

            HStack {
                Text(pass.fromCode)
                Spacer()
                Text(pass.toCode)
            }
            .font(.system(size: 44, weight: .thin))
            .foregroundStyle(Metrics.ink.opacity(0.92))
            .frame(width: Metrics.passWidth - 32, height: 48)
            .offset(x: 16, y: 78)

            // The plane flies in from the origin's side each time the pass arrives in front.
            Plane()
                .fill(Metrics.ink.opacity(0.7))
                .frame(width: 26, height: 26)
                .scaleEffect(0.8 + 0.2 * flight)
                .opacity(segment(flight, 0, 0.3))
                .offset(x: Metrics.passWidth / 2 - 13 - Metrics.flight * (1 - flight), y: 89)

            HStack(alignment: .top, spacing: 0) {
                field("FLIGHT", pass.flight).frame(width: 74, alignment: .leading)
                field("DATE", pass.date).frame(width: 72, alignment: .leading)
                field("BOARDING", pass.boarding).frame(width: 62, alignment: .leading)
                Spacer(minLength: 0)
                field("CLASS", pass.cabin, alignment: .trailing)
            }
            .frame(width: Metrics.passWidth - 36)
            .offset(x: 18, y: 140)

            HStack(alignment: .top, spacing: 0) {
                field("PASSENGER", passenger)
                Spacer(minLength: 0)
                field("STATUS", pass.status, alignment: .trailing)
            }
            .frame(width: Metrics.passWidth - 36)
            .offset(x: 18, y: 184)

            // The perforation between the two notches.
            Path { path in
                path.move(to: CGPoint(x: Metrics.notch + 6, y: Metrics.tear))
                path.addLine(to: CGPoint(x: Metrics.passWidth - Metrics.notch - 6, y: Metrics.tear))
            }
            .stroke(Metrics.ink.opacity(0.3), style: StrokeStyle(lineWidth: 1.2, lineCap: .round, dash: [1, 5]))

            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(.white.opacity(0.92))
                .frame(width: 72, height: 72)
                .overlay(CodeShape(seed: seed).fill(Metrics.ink).frame(width: 58, height: 58))
                .offset(x: 18, y: 244)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 9) {
                field("SEQUENCE", pass.sequence)
                field("BAGS", pass.bags)
            }
            .offset(x: 108, y: 247)

            Text("\(pass.fromCode) – \(pass.toCode)")
                .font(.system(size: 12, weight: .semibold))
                .monospacedDigit()
                .frame(width: Metrics.passWidth - 36, alignment: .trailing)
                .offset(x: 18, y: 247)
                .foregroundStyle(Metrics.ink.opacity(0.62))

            PassShape().fill(.black.opacity(shade))
        }
        .foregroundStyle(Metrics.ink)
        .lineLimit(1)
        .frame(width: Metrics.passWidth, height: Metrics.passHeight, alignment: .topLeading)
        .accessibilityElement(children: .combine)
    }

    private func field(_ label: String, _ value: String, size: Double = 13, alignment: HorizontalAlignment = .leading) -> some View {
        VStack(alignment: alignment, spacing: 1) {
            Text(label)
                .font(.system(size: 7.5, weight: .bold))
                .tracking(0.3)
                .foregroundStyle(Metrics.ink.opacity(0.55))
                .frame(height: 10)
            Text(value)
                .font(.system(size: size))
                .monospacedDigit()
                .frame(height: size + 4)
        }
        .fixedSize()
    }
}

/// The outline of a pass: a rounded rectangle with a half-round notch at each end of the tear line.
private struct PassShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = Metrics.passHeight
        let r = Metrics.passRadius
        let n = Metrics.notch
        let tear = Metrics.tear
        var path = Path()
        path.move(to: CGPoint(x: r, y: 0))
        path.addLine(to: CGPoint(x: w - r, y: 0))
        path.addArc(center: CGPoint(x: w - r, y: r), radius: r, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
        path.addLine(to: CGPoint(x: w, y: tear - n))
        path.addArc(center: CGPoint(x: w, y: tear), radius: n, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: true)
        path.addLine(to: CGPoint(x: w, y: h - r))
        path.addArc(center: CGPoint(x: w - r, y: h - r), radius: r, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        path.addLine(to: CGPoint(x: r, y: h))
        path.addArc(center: CGPoint(x: r, y: h - r), radius: r, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        path.addLine(to: CGPoint(x: 0, y: tear + n))
        path.addArc(center: CGPoint(x: 0, y: tear), radius: n, startAngle: .degrees(90), endAngle: .degrees(-90), clockwise: true)
        path.addLine(to: CGPoint(x: 0, y: r))
        path.addArc(center: CGPoint(x: r, y: r), radius: r, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        path.closeSubpath()
        return path
    }
}

/// The airline's mark: a tern's wing in a ring, in a 20 × 20 box.
private struct Mark: View {
    var body: some View {
        Path { path in
            path.addEllipse(in: CGRect(x: 1.5, y: 1.5, width: 17, height: 17))
            path.move(to: CGPoint(x: 5, y: 12.6))
            path.addLine(to: CGPoint(x: 8.8, y: 8.4))
            path.addLine(to: CGPoint(x: 10.8, y: 11))
            path.addLine(to: CGPoint(x: 15, y: 6.6))
        }
        .stroke(style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
        .frame(width: 20, height: 20)
        .accessibilityHidden(true)
    }
}

/// A plane seen from above, flying to the right, in a 24 × 24 box.
private struct Plane: Shape {
    func path(in rect: CGRect) -> Path {
        let unit = rect.width / 24
        var path = Path()
        for (index, point) in planeOutline.enumerated() {
            let p = CGPoint(x: point.0 * unit, y: point.1 * unit)
            if index == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        path.closeSubpath()
        return path
    }
}

private let planeOutline: [(Double, Double)] = [
    (22, 12), (20.6, 12.75), (15, 12.9), (10.2, 20.5), (8.2, 20.5), (10.6, 12.9), (5.6, 12.85), (4.2, 16.2), (2.8, 16.2),
    (3.4, 12), (2.8, 7.8), (4.2, 7.8), (5.6, 11.15), (10.6, 11.1), (8.2, 3.5), (10.2, 3.5), (15, 11.1), (20.6, 11.25),
]

/// A drawn stand-in for the pass's code: 21 × 21 squares with the three corner marks of a QR code.
private struct CodeShape: Shape {
    let seed: Int

    func path(in rect: CGRect) -> Path {
        let unit = rect.width / 21
        var path = Path()
        for y in 0..<21 {
            for x in 0..<21 where codeSquare(x, y, seed: seed) {
                path.addRect(CGRect(x: Double(x) * unit, y: Double(y) * unit, width: unit, height: unit))
            }
        }
        return path
    }
}

/// Whether square (x, y) of the code is dark. The pattern only has to look right: it encodes nothing.
private func codeSquare(_ x: Int, _ y: Int, seed: Int) -> Bool {
    // The corner marks: a 7 × 7 ring around a 3 × 3 block, with a clear band around them.
    for (cx, cy) in [(0, 0), (14, 0), (0, 14)] {
        let lx = x - cx
        let ly = y - cy
        if lx >= -1, lx <= 7, ly >= -1, ly <= 7 {
            if lx < 0 || lx > 6 || ly < 0 || ly > 6 { return false }
            return lx == 0 || lx == 6 || ly == 0 || ly == 6 || (lx >= 2 && lx <= 4 && ly >= 2 && ly <= 4)
        }
    }
    return (x * 73 + y * 151 + x * y * 17 + (seed + 1) * 31) % 97 % 2 == 0
}

#Preview {
    ZStack {
        Color(hex: 0x05070c).ignoresSafeArea()
        BoardingPassStack(autoPlay: true)
    }
}
