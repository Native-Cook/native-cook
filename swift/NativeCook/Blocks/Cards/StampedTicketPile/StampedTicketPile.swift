import SwiftUI

/// A passport stamp: an outline with three short lines of text in it.
struct StampedTicketPileStamp: Equatable {
    enum Kind {
        /// Two rings; the first line runs along the top of the inner one.
        case ring
        case plate
        case oval
        case triangle
    }

    var kind: Kind
    /// A small line, the date in large type, and another small line.
    var lines: [String]
    var ink: Color
    /// Centre of the stamp on its ticket, and its turn in degrees.
    var x: Double
    var y: Double
    var angle: Double
}

struct StampedTicketPileTicket: Equatable {
    var color: Color
    var date: String
    /// One label strip per line.
    var title: [String]
    var place: String
    var stamp: StampedTicketPileStamp

    static let samples = [
        StampedTicketPileTicket(
            color: Color(hex: 0xffb877), date: "Jul 11 – Sep 6", title: ["Surfing and", "beaches"], place: "Denpasar, Kuta and Umalas",
            stamp: StampedTicketPileStamp(kind: .triangle, lines: ["ENTRY", "11 JUL", "GATE 7"], ink: Color(hex: 0x1d1b1a), x: 118, y: 196, angle: 14)
        ),
        StampedTicketPileTicket(
            color: Color(hex: 0xeae1da), date: "Jan 7 – Jan 16", title: ["Ancient Egypt", "adventure"], place: "Cairo and Alexandria",
            stamp: StampedTicketPileStamp(kind: .ring, lines: ["ARRIVAL", "07 JAN", "2025"], ink: Color(hex: 0x34389a), x: 120, y: 198, angle: -14)
        ),
        StampedTicketPileTicket(
            color: Color(hex: 0xdfe4ff), date: "Apr 21 – Jun 7", title: ["Road trip", "with friends"], place: "Lisbon, Porto and Faro",
            stamp: StampedTicketPileStamp(kind: .plate, lines: ["ENTRY PERMIT", "21 APR 2025", "VISITOR · 90 DAYS"], ink: Color(hex: 0xa5362c), x: 112, y: 200, angle: -9)
        ),
        StampedTicketPileTicket(
            color: Color(hex: 0xffe500), date: "Mar 28 – Apr 4", title: ["Spring in", "the Alps"], place: "Chamonix and Annecy",
            stamp: StampedTicketPileStamp(kind: .oval, lines: ["DEPARTED", "28 MAR", "TERMINAL 2"], ink: Color(hex: 0x1d1b1a), x: 116, y: 198, angle: 11)
        ),
    ]
}

/// A loose pile of four trip tickets, each a different colour and angle, with the trip's dates,
/// its name on black label strips, a place and a passport stamp. The tickets are dealt from below
/// one by one and stamped as they land; a tap sends the top one out to the side and under the
/// pile while the others move up.
struct StampedTicketPile: View {
    /// Four tickets, the top of the pile first.
    var tickets = StampedTicketPileTicket.samples
    /// Shuffles through the pile, drops it, deals it again and starts over, every 8.6 s.
    var autoPlay = false
    var labelColor = Color(hex: 0x0e0e10)
    var labelTextColor = Color.white
    var inkColor = Color(hex: 0x1c1c1c)
    /// Called with the index of the ticket a tap brings to the top.
    var onShuffle: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = Simulation()
    /// Keeps the timeline running for motion started without autoPlay.
    @State private var awake = false
    @State private var moves = 0
    @State private var taps = 0

    private var auto: Bool { autoPlay && !reduceMotion }
    private var count: Int { min(tickets.count, Metrics.slots.count) }

    var body: some View {
        TimelineView(.animation(paused: !(auto || awake))) { timeline in
            // The first frame is drawn before `onAppear` has laid the pile out.
            let _ = sim.slot.count == count ? () : sim.reset(count: count, looping: auto)
            let _ = sim.advance(to: timeline.date)
            // Worked out here, not inside ForEach: its content is only run again when what it captures changes.
            let poses = (0..<count).map { sim.pose($0) }
            let stamps = (0..<count).map { stampLook(sim.time - sim.stampAt[$0]) }

            ZStack {
                ForEach(0..<count, id: \.self) { index in
                    let pose = poses[index]
                    let stamp = stamps[index]
                    StampedTicketPileCard(
                        ticket: tickets[index], labelColor: labelColor, labelTextColor: labelTextColor, inkColor: inkColor,
                        stampScale: stamp.scale, stampOpacity: stamp.opacity, lift: pose.lift
                    )
                    .scaleEffect(pose.scale)
                    .rotationEffect(.degrees(pose.turn))
                    .offset(x: pose.x, y: pose.y)
                    .opacity(pose.opacity)
                    .zIndex(pose.z)
                }
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .contentShape(Rectangle())
            .onTapGesture { tap() }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: front.map { tickets[$0].title.joined(separator: " ") } ?? ""))
            .accessibilityHint("Shows the next ticket")
            .accessibilityAddTraits(.isButton)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: taps)
        .onAppear { sync(appearing: true) }
        .onChange(of: autoPlay) { sync(appearing: false) }
        .onChange(of: reduceMotion) { sync(appearing: false) }
        .task(id: moves) {
            guard moves > 0 else { return }
            // Long enough for a deal or a shuffle to settle; the timeline may sleep again after it.
            try? await Task.sleep(for: .seconds(Metrics.dealTime + 0.5))
            awake = false
        }
    }

    /// The ticket on top of the pile.
    private var front: Int? { (0..<count).first { sim.slot[$0] == 0 } }

    private func sync(appearing: Bool) {
        sim.reset(count: count, looping: auto)
        // Without the loop the pile makes its entrance once: dealt and stamped.
        guard appearing, !auto, !reduceMotion else { return }
        sim.deal(at: Metrics.entranceAt)
        awake = true
        moves += 1
    }

    /// A tap on the pile: the top ticket goes to the back, unless the pile is being dealt.
    private func tap() {
        guard count > 1, sim.time >= sim.busyUntil else { return }
        sim.shuffle(at: sim.time, reduced: reduceMotion)
        sim.hold = Metrics.shuffleGap + Metrics.userHold
        awake = true
        moves += 1
        taps += 1
        if let front { onShuffle?(front) }
    }

    /// Where every ticket is, and where it is going.
    private final class Simulation {
        enum Kind {
            case rest, settle, tuck, drop, deal, fade
        }

        var last: Date? = nil
        var time = 0.0
        var clock = 0.0
        var looping = false
        /// The next step of the loop's script.
        var step = 0
        /// Seconds the loop still waits after a touch.
        var hold = 0.0
        /// Taps are ignored until then: the pile is dropping or being dealt.
        var busyUntil = 0.0
        /// Shuffles so far: every other one leaves by the other side.
        var shuffles = 0

        // One entry per ticket.
        var slot: [Int] = []
        var kind: [Kind] = []
        var start: [Double] = []
        var from: [Pose] = []
        var side: [Double] = []
        /// When each ticket's stamp starts coming down.
        var stampAt: [Double] = []

        func reset(count: Int, looping: Bool) {
            self.looping = looping
            clock = 0
            step = 0
            hold = 0
            busyUntil = 0
            shuffles = 0
            slot = Array(0..<count)
            kind = Array(repeating: .rest, count: count)
            start = Array(repeating: 0, count: count)
            from = slot.map { slotPose($0, count: count) }
            side = Array(repeating: 1, count: count)
            stampAt = Array(repeating: -100, count: count)
        }

        func advance(to date: Date) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            time += dt
            if hold > 0 {
                hold -= dt
                // Time is up: the loop starts over from its rest, with the pile as it is.
                if hold <= 0 {
                    clock = 0
                    step = 0
                }
            } else if looping {
                clock += dt
                if clock >= Metrics.loop {
                    clock -= Metrics.loop
                    step = 0
                }
                // The script: one shuffle per ticket, then the pile drops and is dealt again.
                while step < slot.count + 2 {
                    let at = step < slot.count ? Metrics.firstAt + Double(step) * Metrics.shuffleGap : step == slot.count ? Metrics.dropAt : Metrics.dealAt
                    guard clock >= at else { break }
                    let now = time - (clock - at)
                    if step < slot.count {
                        shuffle(at: now, reduced: false)
                    } else if step == slot.count {
                        drop(at: now)
                    } else {
                        deal(at: now)
                    }
                    step += 1
                }
            }
        }

        /// The top ticket leaves by the side and comes back under the pile; the others move up a place.
        func shuffle(at now: Double, reduced: Bool) {
            let count = slot.count
            for index in 0..<count {
                from[index] = pose(index, at: now)
                if slot[index] == 0 {
                    slot[index] = count - 1
                    kind[index] = reduced ? .fade : .tuck
                    start[index] = now
                    side[index] = shuffles % 2 == 0 ? 1 : -1
                } else {
                    slot[index] -= 1
                    kind[index] = reduced ? .rest : .settle
                    start[index] = now + Metrics.shiftDelay
                }
            }
            shuffles += 1
        }

        /// The tickets fall out of the bottom, the top one first.
        func drop(at now: Double) {
            for index in 0..<slot.count {
                from[index] = pose(index, at: now)
                kind[index] = .drop
                start[index] = now + Double(slot[index]) * Metrics.dropStagger
                side[index] = slot[index] % 2 == 0 ? 1 : -1
            }
            busyUntil = now + Metrics.dealAt - Metrics.dropAt + Metrics.dealTime
        }

        /// The tickets fly in from below, the bottom one first, and each is stamped as it lands.
        func deal(at now: Double) {
            let count = slot.count
            for index in 0..<count {
                kind[index] = .deal
                start[index] = now + Double(count - 1 - slot[index]) * Metrics.dealGap
                side[index] = slot[index] % 2 == 0 ? 1 : -1
                stampAt[index] = start[index] + Metrics.stampAfter
            }
            busyUntil = max(busyUntil, now + Metrics.dealTime)
        }

        func pose(_ index: Int) -> Pose {
            var pose = pose(index, at: time)
            // The stamp's blow shakes the ticket.
            let since = time - stampAt[index] - Metrics.slam
            if since > 0 {
                pose.turn += Metrics.shake * exp(-Metrics.shakeDecay * since) * sin(Metrics.shakeRate * since)
                pose.scale *= 1 - Metrics.dip * exp(-Metrics.dipDecay * since)
            }
            return pose
        }

        private func pose(_ index: Int, at now: Double) -> Pose {
            let count = slot.count
            let home = slotPose(slot[index], count: count)
            let t = now - start[index]
            switch kind[index] {
            case .rest:
                return home
            case .settle:
                return from[index].mixed(with: home, t > 0 ? spring(t) : 0, z: home.z)
            case .tuck:
                // Out past the edge of the pile, lifted and turning, then in again underneath.
                var out = from[index]
                out.x += side[index] * Metrics.sideX
                out.y += Metrics.sideY
                out.turn += side[index] * Metrics.sideTurn
                out.scale += Metrics.liftScale
                if t < Metrics.outTime {
                    let p = smooth(t / Metrics.outTime)
                    var pose = from[index].mixed(with: out, p, z: Double(count + 1))
                    pose.lift = sin(.pi * p)
                    return pose
                }
                return out.mixed(with: home, spring(t - Metrics.outTime), z: home.z)
            case .drop:
                let p = clamp01(t / Metrics.dropTime)
                var pose = from[index]
                pose.x += side[index] * Metrics.dropDrift * p * p
                pose.y += Metrics.dropY * p * p
                pose.turn += side[index] * Metrics.dropTurn * p * p
                pose.opacity = 1 - smooth((p - 0.6) / 0.4)
                return pose
            case .deal:
                var below = home
                below.x += side[index] * Metrics.dealDrift
                below.y += Metrics.dealY
                below.turn += side[index] * Metrics.dealTurn
                below.scale += Metrics.dealScale
                below.opacity = 0
                guard t > 0 else { return below }
                let p = spring(t)
                var pose = below.mixed(with: home, p, z: home.z)
                pose.opacity = 1
                pose.lift = clamp01(1 - p)
                return pose
            case .fade:
                var pose = home
                pose.opacity = smooth(t / Metrics.fadeTime)
                return pose
            }
        }
    }
}

/// Where a ticket is: its offset from the middle of the pile, its turn in degrees, and its place in the stack.
private struct Pose {
    var x: Double
    var y: Double
    var turn: Double
    var scale = 1.0
    var opacity = 1.0
    /// 0…1: how far the ticket is off the pile, which softens its shadow.
    var lift = 0.0
    var z: Double

    func mixed(with other: Pose, _ p: Double, z: Double) -> Pose {
        Pose(
            x: x + (other.x - x) * p, y: y + (other.y - y) * p, turn: turn + (other.turn - turn) * p,
            scale: scale + (other.scale - scale) * p, z: z
        )
    }
}

/// A ticket's place at rest: slot 0 is the top of the pile.
private func slotPose(_ slot: Int, count: Int) -> Pose {
    let place = Metrics.slots[slot]
    return Pose(x: place[0], y: place[1], turn: place[2], z: Double(count - slot))
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func smooth(_ t: Double) -> Double {
    let p = clamp01(t)
    return p * p * (3 - 2 * p)
}

/// A spring let go at 0 and settling at 1, `t` seconds on, with a small overshoot.
private func spring(_ t: Double) -> Double {
    let decay = Metrics.damping / (2 * Metrics.mass)
    let omega = (Metrics.stiffness / Metrics.mass - decay * decay).squareRoot()
    return 1 - exp(-decay * t) * (cos(omega * t) + decay / omega * sin(omega * t))
}

/// The stamp `since` seconds after it starts coming down: it shrinks onto the paper as its ink
/// appears, then the ink dries a little.
private func stampLook(_ since: Double) -> (scale: Double, opacity: Double) {
    if since <= 0 { return (Metrics.stampScale, 0) }
    if since < Metrics.slam {
        let p = since / Metrics.slam
        return (Metrics.stampScale - (Metrics.stampScale - 1) * p * p, Metrics.inkFresh * clamp01(since / Metrics.inkIn))
    }
    return (1, Metrics.inkDry + (Metrics.inkFresh - Metrics.inkDry) * exp(-Metrics.inkDryRate * (since - Metrics.slam)))
}

/// One ticket: dates, the trip's name on label strips, the place and the stamp.
private struct StampedTicketPileCard: View {
    let ticket: StampedTicketPileTicket
    let labelColor: Color
    let labelTextColor: Color
    let inkColor: Color
    let stampScale: Double
    let stampOpacity: Double
    let lift: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            // Two shadows cross-faded: a tight one on the pile, a wide one while the ticket is lifted.
            stampedTicketOutline
                .fill(ticket.color)
                .shadow(color: .black.opacity(Metrics.shadowOpacity * (1 - lift)), radius: Metrics.shadowRadius, y: Metrics.shadowY)
                .shadow(color: .black.opacity(Metrics.liftShadowOpacity * lift), radius: Metrics.liftShadowRadius, y: Metrics.liftShadowY)

            Text(ticket.date)
                .font(.system(size: 9.5, weight: .medium))
                .foregroundStyle(inkColor)
                .lineLimit(1)
                .padding(.horizontal, Metrics.chipPad)
                .frame(height: Metrics.chipHeight)
                .background(RoundedRectangle(cornerRadius: Metrics.chipRadius, style: .circular).fill(.black.opacity(0.07)))
                .offset(x: Metrics.left, y: Metrics.chipTop)

            ForEach(Array(ticket.title.enumerated()), id: \.offset) { line, text in
                Text(text)
                    .font(.system(size: 19, weight: .bold))
                    .tracking(-0.2)
                    .foregroundStyle(labelTextColor)
                    .lineLimit(1)
                    .padding(.horizontal, Metrics.stripPad)
                    .frame(height: Metrics.stripHeight)
                    .background(RoundedRectangle(cornerRadius: Metrics.stripRadius, style: .circular).fill(labelColor))
                    // The strips are stuck on by hand, each a little askew.
                    .rotationEffect(.degrees(line % 2 == 0 ? Metrics.stripTilt : -Metrics.stripTilt * 0.75), anchor: .leading)
                    .offset(x: Metrics.stripLeft, y: Metrics.titleTop + Double(line) * Metrics.stripStep)
            }

            HStack(spacing: 4) {
                ZStack {
                    stampedTicketPin.fill(inkColor)
                    Circle().fill(ticket.color).frame(width: 3, height: 3).position(x: 4, y: 4)
                }
                .frame(width: 8, height: 11)
                Text(ticket.place)
                    .font(.system(size: 9.5))
                    .foregroundStyle(inkColor)
                    .lineLimit(1)
            }
            .frame(height: 12)
            .offset(x: Metrics.left, y: Metrics.titleTop + Double(ticket.title.count) * Metrics.stripStep + Metrics.placeGap)

            StampedTicketPileMark(stamp: ticket.stamp)
                .scaleEffect(stampScale)
                .opacity(stampOpacity)
                .rotationEffect(.degrees(ticket.stamp.angle))
                .position(x: ticket.stamp.x, y: ticket.stamp.y)
        }
        .frame(width: Metrics.ticketWidth, height: Metrics.ticketHeight, alignment: .topLeading)
    }
}

/// A stamp in a 100-point box: its outline, and its three lines of text.
private struct StampedTicketPileMark: View {
    let stamp: StampedTicketPileStamp

    var body: some View {
        let look = Metrics.stampLooks[stamp.kind] ?? Metrics.plateLook
        ZStack {
            outline
            if stamp.kind == .ring {
                // The first line follows the inner ring, one letter at a time.
                let letters = Array(stamp.lines[0])
                ForEach(Array(letters.enumerated()), id: \.offset) { index, letter in
                    let angle = (Double(index) - Double(letters.count - 1) / 2) * Metrics.arcStep
                    Text(String(letter))
                        .font(.system(size: look.topSize, weight: .heavy))
                        .fixedSize()
                        .rotationEffect(.degrees(angle))
                        .position(
                            x: Metrics.stampBox / 2 + Metrics.arcRadius * sin(angle * .pi / 180),
                            y: Metrics.stampBox / 2 - Metrics.arcRadius * cos(angle * .pi / 180)
                        )
                }
            } else {
                Text(stamp.lines[0])
                    .font(.system(size: look.topSize, weight: .heavy))
                    .tracking(look.topTracking)
                    .fixedSize()
                    .position(x: Metrics.stampBox / 2, y: look.topY)
            }
            Text(stamp.lines[1])
                .font(.system(size: look.mainSize, weight: .heavy))
                .tracking(0.4)
                .fixedSize()
                .position(x: Metrics.stampBox / 2, y: look.mainY)
            Text(stamp.lines[2])
                .font(.system(size: look.bottomSize, weight: .bold))
                .tracking(look.bottomTracking)
                .fixedSize()
                .position(x: Metrics.stampBox / 2, y: look.bottomY)
        }
        .lineLimit(1)
        .foregroundStyle(stamp.ink)
        .frame(width: Metrics.stampBox, height: Metrics.stampBox)
    }

    @ViewBuilder private var outline: some View {
        let bold = StrokeStyle(lineWidth: Metrics.stampStroke, lineJoin: .round)
        let fine = StrokeStyle(lineWidth: Metrics.stampHairline, lineJoin: .round)
        let dashed = StrokeStyle(lineWidth: Metrics.stampHairline, dash: Metrics.stampDash)
        switch stamp.kind {
        case .ring:
            Circle().stroke(stamp.ink, style: bold).frame(width: 84, height: 84)
            Circle().stroke(stamp.ink, style: dashed).frame(width: 72, height: 72)
        case .plate:
            RoundedRectangle(cornerRadius: 8, style: .circular).stroke(stamp.ink, style: bold).frame(width: 96, height: 60)
            RoundedRectangle(cornerRadius: 4, style: .circular).stroke(stamp.ink, style: fine).frame(width: 86, height: 50)
        case .oval:
            Ellipse().stroke(stamp.ink, style: bold).frame(width: 96, height: 62)
            Ellipse().stroke(stamp.ink, style: dashed).frame(width: 85, height: 51)
        case .triangle:
            stampedTicketTriangle(Metrics.triangle).stroke(stamp.ink, style: bold)
            stampedTicketTriangle(Metrics.triangleInner).stroke(stamp.ink, style: fine)
        }
    }
}

private func stampedTicketTriangle(_ points: [Double]) -> Path {
    var path = Path()
    path.move(to: CGPoint(x: points[0], y: points[1]))
    path.addLine(to: CGPoint(x: points[2], y: points[3]))
    path.addLine(to: CGPoint(x: points[4], y: points[5]))
    path.closeSubpath()
    return path
}

/// A rounded rectangle with a half circle cut out of the middle of its bottom edge.
private let stampedTicketOutline: Path = {
    let w = Metrics.ticketWidth
    let h = Metrics.ticketHeight
    let r = Metrics.ticketRadius
    let n = Metrics.notch
    // Control-point distance that makes a cubic curve a quarter circle.
    let k = 0.5523
    var p = Path()
    p.move(to: CGPoint(x: r, y: 0))
    p.addLine(to: CGPoint(x: w - r, y: 0))
    p.addCurve(to: CGPoint(x: w, y: r), control1: CGPoint(x: w - r + r * k, y: 0), control2: CGPoint(x: w, y: r - r * k))
    p.addLine(to: CGPoint(x: w, y: h - r))
    p.addCurve(to: CGPoint(x: w - r, y: h), control1: CGPoint(x: w, y: h - r + r * k), control2: CGPoint(x: w - r + r * k, y: h))
    p.addLine(to: CGPoint(x: w / 2 + n, y: h))
    p.addCurve(to: CGPoint(x: w / 2, y: h - n), control1: CGPoint(x: w / 2 + n, y: h - n * k), control2: CGPoint(x: w / 2 + n * k, y: h - n))
    p.addCurve(to: CGPoint(x: w / 2 - n, y: h), control1: CGPoint(x: w / 2 - n * k, y: h - n), control2: CGPoint(x: w / 2 - n, y: h - n * k))
    p.addLine(to: CGPoint(x: r, y: h))
    p.addCurve(to: CGPoint(x: 0, y: h - r), control1: CGPoint(x: r - r * k, y: h), control2: CGPoint(x: 0, y: h - r + r * k))
    p.addLine(to: CGPoint(x: 0, y: r))
    p.addCurve(to: CGPoint(x: r, y: 0), control1: CGPoint(x: 0, y: r - r * k), control2: CGPoint(x: r - r * k, y: 0))
    p.closeSubpath()
    return p
}()

/// A map pin, 8 × 11, with its tip at the bottom.
private let stampedTicketPin: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 4, y: 0))
    p.addCurve(to: CGPoint(x: 0, y: 4), control1: CGPoint(x: 1.8, y: 0), control2: CGPoint(x: 0, y: 1.8))
    p.addCurve(to: CGPoint(x: 4, y: 11), control1: CGPoint(x: 0, y: 7), control2: CGPoint(x: 4, y: 11))
    p.addCurve(to: CGPoint(x: 8, y: 4), control1: CGPoint(x: 4, y: 11), control2: CGPoint(x: 8, y: 7))
    p.addCurve(to: CGPoint(x: 4, y: 0), control1: CGPoint(x: 8, y: 1.8), control2: CGPoint(x: 6.2, y: 0))
    p.closeSubpath()
    return p
}()

/// Where a kind of stamp puts its three lines, and how large it sets them.
private struct StampLook {
    var topY: Double
    var topSize: Double
    var topTracking: Double
    var mainY: Double
    var mainSize: Double
    var bottomY: Double
    var bottomSize: Double
    var bottomTracking: Double
}

private enum Metrics {
    static let width = 350.0
    static let height = 460.0

    static let ticketWidth = 196.0
    static let ticketHeight = 280.0
    static let ticketRadius = 18.0
    /// Radius of the half circle cut out of the bottom edge.
    static let notch = 20.0
    /// Where the tickets lie, the top of the pile first: offset from the middle, and turn in degrees.
    static let slots = [[34.0, 66, -9], [-44, 24, 8], [40, -34, 16], [-40, -60, -15]]
    static let shadowOpacity = 0.16
    static let shadowRadius = 9.0
    static let shadowY = 6.0
    static let liftShadowOpacity = 0.2
    static let liftShadowRadius = 18.0
    static let liftShadowY = 16.0

    static let left = 20.0
    static let chipTop = 24.0
    static let chipHeight = 18.0
    static let chipPad = 7.0
    static let chipRadius = 6.0
    static let titleTop = 50.0
    static let stripLeft = 16.0
    static let stripHeight = 30.0
    static let stripStep = 27.0
    static let stripPad = 9.0
    static let stripRadius = 7.0
    static let stripTilt = -2.0
    static let placeGap = 12.0

    static let stampBox = 100.0
    static let stampStroke = 2.2
    static let stampHairline = 0.9
    static let stampDash: [CGFloat] = [2.2, 2.2]
    /// The ring's first line: the radius it runs along, and the angle from one letter to the next.
    static let arcRadius = 28.5
    static let arcStep = 14.0
    static let triangle = [50.0, 6, 96, 86, 4, 86]
    static let triangleInner = [50.0, 20, 84, 79, 16, 79]
    static let plateLook = StampLook(topY: 33, topSize: 7, topTracking: 1.2, mainY: 49.5, mainSize: 11.5, bottomY: 66, bottomSize: 6.5, bottomTracking: 0.8)
    static let stampLooks: [StampedTicketPileStamp.Kind: StampLook] = [
        .ring: StampLook(topY: 0, topSize: 8, topTracking: 0, mainY: 52, mainSize: 14, bottomY: 66, bottomSize: 7.5, bottomTracking: 1),
        .plate: plateLook,
        .oval: StampLook(topY: 36, topSize: 6.5, topTracking: 1.2, mainY: 50, mainSize: 12.5, bottomY: 64, bottomSize: 6.5, bottomTracking: 0.8),
        .triangle: StampLook(topY: 47, topSize: 7, topTracking: 0.8, mainY: 61, mainSize: 11.5, bottomY: 73, bottomSize: 6, bottomTracking: 0.6),
    ]

    // Tickets settle on a spring with a small overshoot.
    static let mass = 1.0
    static let stiffness = 210.0
    static let damping = 19.0

    // A shuffle: the top ticket goes out to the side for `outTime`, then springs back in underneath.
    static let outTime = 0.34
    static let sideX = 330.0
    static let sideY = -26.0
    static let sideTurn = 16.0
    static let liftScale = 0.05
    /// The other tickets wait this long before they move up.
    static let shiftDelay = 0.1
    /// Under reduced motion the shuffled ticket fades in at the back instead.
    static let fadeTime = 0.25

    // The drop: one ticket after another falls out of the bottom, speeding up.
    static let dropStagger = 0.07
    static let dropTime = 0.5
    static let dropY = 760.0
    static let dropDrift = 30.0
    static let dropTurn = 24.0

    // The deal: a ticket every `dealGap` flies in from below, and is stamped `stampAfter` its start.
    static let dealGap = 0.65
    static let dealY = 640.0
    static let dealDrift = 46.0
    static let dealTurn = 28.0
    static let dealScale = 0.08
    static let stampAfter = 0.44
    /// Seconds a whole deal takes, the last stamp included.
    static let dealTime = 2.9

    // The stamp comes down from `stampScale` in `slam` seconds while its ink appears, and shakes the ticket.
    static let slam = 0.14
    static let stampScale = 1.6
    static let inkIn = 0.07
    static let inkFresh = 0.96
    static let inkDry = 0.8
    static let inkDryRate = 8.0
    static let shake = 1.8
    static let shakeDecay = 16.0
    static let shakeRate = 46.0
    static let dip = 0.02
    static let dipDecay = 20.0

    // autoPlay loop (seconds): four shuffles bring the pile back to its order, it drops, and it is dealt again.
    static let firstAt = 0.8
    static let shuffleGap = 0.8
    static let dropAt = 4.2
    static let dealAt = 5.0
    static let loop = 8.6
    /// After a tap the loop waits this long once the shuffle is over.
    static let userHold = 2.0
    /// Without autoPlay the pile is dealt this long after it appears.
    static let entranceAt = 0.2
}

#Preview {
    ZStack {
        Color(hex: 0xf7f1f1).ignoresSafeArea()
        StampedTicketPile(autoPlay: true)
    }
}
