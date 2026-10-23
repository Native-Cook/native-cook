import SwiftUI

/// One end of the flight.
struct FlightStatusCardStop: Equatable {
    var code: String
    var city: String
    var time: String
}

/// One of the three small fields at the bottom of the panel.
struct FlightStatusCardField: Equatable {
    var label: String
    var value: String
}

/// A flight on a square photo card: a view over the wing, and a white panel with the two airports, a
/// dashed line the plane's badge travels along, the time left and three small fields. As the flight
/// progresses the line turns solid behind the badge, the status chip on the photo flips from Boarding
/// to In flight to Landed, the time counts down and the clouds drift past; on landing the badge turns
/// green with a small bounce. Dragging the badge scrubs the flight.
struct FlightStatusCard: View {
    /// Address of the photo, three wide by two high.
    var photo = "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=1200&h=800&q=80"
    /// The photo's main colour, shown until it arrives.
    var tint = Color(hex: 0xa4abb1)
    var from = FlightStatusCardStop(code: "SUB", city: "Surabaya", time: "08:45 PM")
    var to = FlightStatusCardStop(code: "SIN", city: "Singapore", time: "12:10 AM")
    var flight = "NB-880"
    /// Length of the flight in minutes: what is left of it counts down under the line.
    var minutes = 205
    /// The chip's wording before take-off, in the air and after landing.
    var statuses = ["Boarding", "In flight", "Landed"]
    /// The words after the hours and the minutes left: for one, and for any other number.
    var hourWords = ["Hour", "Hours"]
    var minuteWords = ["Minute", "Minutes"]
    /// Shown in place of the time left once the plane has landed.
    var arrived = "Arrived"
    var fields = [
        FlightStatusCardField(label: "Airline", value: "Nimbus Air"),
        FlightStatusCardField(label: "Flight Code", value: "NB 880"),
        FlightStatusCardField(label: "Class", value: "Economy"),
    ]
    /// Plays the flight, holds on the landing, then rewinds to the gate, in a loop.
    var autoPlay = false
    /// Called with the share of the flight, 0…1, while the badge is dragged.
    var onScrub: ((Double) -> Void)? = nil
    /// Called when the plane lands.
    var onLand: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    @State private var dragFrom: Double? = nil
    /// Counts finished drags: each one restarts the wait before the loop resumes.
    @State private var touches = 0
    @State private var grabs = 0
    @State private var landings = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        // The flight: on its way from one share of the route to another, or where a finger left it.
        private var from = 0.0
        private var to = 0.0
        private var start = Date.distantPast
        private var duration = 0.0
        private var curve = UnitCurve.linear
        // The chip: the status it is leaving, the one it shows, and when that flip began.
        var statusFrom = 0
        var status = 0
        var flipStart = Date.distantPast
        // The landing, and the last time the plane left the ground again.
        var landed = false
        var landedAt = Date.distantPast
        var leftAt = Date.distantPast

        func progress(at date: Date) -> Double {
            let t = date.timeIntervalSince(start)
            guard duration > 0, t < duration else { return to }
            return from + (to - from) * curve.value(at: max(0, t) / duration)
        }

        func fly(to target: Double, duration: Double, curve: UnitCurve) {
            from = progress(at: .now)
            to = target
            start = .now
            self.duration = duration
            self.curve = curve
        }

        func hold(_ value: Double) {
            to = value
            duration = 0
        }

        /// Keeps the chip and the landing in step with the flight.
        func follow(_ progress: Double, at date: Date) {
            let now = statusAt(progress)
            if now != status {
                statusFrom = status
                status = now
                flipStart = date
            }
            if (now == 2) != landed {
                landed = now == 2
                if landed { landedAt = date } else { leftAt = date }
            }
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let progress = motion.progress(at: now)
            let _ = motion.follow(progress, at: now)
            let flip = now.timeIntervalSince(motion.flipStart)
            // 0 black … 1 green, and the bounce the badge gives as the plane touches down.
            let green = motion.landed
                ? clamp01(now.timeIntervalSince(motion.landedAt) / Metrics.greenIn)
                : 1 - clamp01(now.timeIntervalSince(motion.leftAt) / Metrics.greenOut)
            let bounce = motion.landed && !reduceMotion ? kick(now.timeIntervalSince(motion.landedAt)) : 0
            let left = Int((Double(minutes) * (1 - progress)).rounded())

            ZStack {
                tint
                // The photo is wider than the card: it slides past and grows a touch as the flight goes on.
                AsyncImage(url: URL(string: photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    }
                }
                .frame(width: Metrics.photoW, height: Metrics.photoH)
                .scaleEffect(reduceMotion ? 1 : 1 + Metrics.zoom * progress)
                .position(
                    x: Metrics.size / 2 + (reduceMotion ? 0 : Metrics.drift * (1 - 2 * progress)),
                    y: Metrics.photoY + Metrics.photoH / 2
                )
                // The photo ends behind the panel: below it the card fades into the colour of the dusk.
                LinearGradient(
                    stops: [
                        .init(color: Color(hex: 0x7c7a86, opacity: 0), location: 0),
                        .init(color: Color(hex: 0x7c7a86), location: 0.23),
                        .init(color: Color(hex: 0x4a5068), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(width: Metrics.size, height: Metrics.size - Metrics.fadeY)
                .position(x: Metrics.size / 2, y: (Metrics.size + Metrics.fadeY) / 2)

                ForEach(statuses.indices.prefix(3), id: \.self) { k in
                    let look = chipLook(k, flip: flip)
                    HStack(spacing: 6) {
                        Circle().fill(chipDots[k]).frame(width: 6, height: 6)
                        Text(statuses[k])
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    .padding(.horizontal, 10)
                    .frame(height: 26)
                    .background(Capsule().fill(Color(hex: 0x10141e, opacity: 0.5)))
                    .overlay(Capsule().strokeBorder(.white.opacity(0.25), lineWidth: 1))
                    .fixedSize()
                    .frame(width: 160, height: 26, alignment: .leading)
                    .rotation3DEffect(.degrees(look.angle), axis: (x: 1, y: 0, z: 0), perspective: 0.6)
                    .opacity(look.opacity)
                    .position(x: 14 + 80, y: 14 + 13)
                }

                panel(progress: progress, green: green, bounce: bounce, left: left)
                    .frame(width: Metrics.panelW, height: Metrics.panelH)
                    .background(RoundedRectangle(cornerRadius: 22, style: .circular).fill(.white))
                    .position(x: Metrics.size / 2, y: Metrics.panelY + Metrics.panelH / 2)
            }
            .lineLimit(1)
            .frame(width: Metrics.size, height: Metrics.size)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular))
            .overlay(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular).strokeBorder(.white.opacity(0.9), lineWidth: 2))
            .shadow(color: .black.opacity(0.12), radius: 15, x: 0, y: 16)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: "\(flight), \(from.city) to \(to.city)"))
            .accessibilityValue(Text(verbatim: "\(statuses[min(motion.status, statuses.count - 1)]), \(remaining(left))"))
        }
        .sensoryFeedback(.impact(weight: .light), trigger: grabs)
        .sensoryFeedback(.success, trigger: landings)
        .task(id: LoopKey(auto: auto, touching: dragFrom != nil, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touching: Bool
        let touches: Int
    }

    /// The white panel, laid out in a 324 × 160 box.
    private func panel(progress: Double, green: Double, bounce: Double, left: Int) -> some View {
        ZStack {
            stop(from, x: 20, .leading)
            stop(to, x: 124, .trailing)

            Text(flight)
                .font(.system(size: 10.5))
                .foregroundStyle(grey)
                .place(x: 102, y: 14, width: 120, height: 14, .center)
            Canvas { context, _ in
                drawLine(context, progress: progress, green: green, bounce: bounce)
            }
            .allowsHitTesting(false)
            Text(remaining(left))
                .font(.system(size: 10.5))
                .monospacedDigit()
                .foregroundStyle(grey)
                .place(x: 82, y: 62, width: 160, height: 14, .center)

            RoundedRectangle(cornerRadius: 14, style: .circular)
                .fill(Color(hex: 0xfafafa))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .circular).strokeBorder(Color(hex: 0xececee), lineWidth: 1))
                .place(x: 12, y: 88, width: 300, height: 60, .center)
            ForEach(fields.indices.prefix(3), id: \.self) { k in
                if k > 0 {
                    Color(hex: 0xececee).place(x: 12 + Double(k) * 100 - 0.5, y: 100, width: 1, height: 36, .center)
                }
                Text(fields[k].label)
                    .font(.system(size: 10.5))
                    .foregroundStyle(grey)
                    .place(x: 12 + Double(k) * 100, y: 100, width: 100, height: 14, .center)
                Text(fields[k].value)
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(ink)
                    .place(x: 12 + Double(k) * 100, y: 119, width: 100, height: 17, .center)
            }

            // Dragging along the line scrubs the flight.
            Color.clear
                .contentShape(Rectangle())
                .place(x: Metrics.lineX - 16, y: 22, width: Metrics.lineW + 32, height: 44, .center)
                .gesture(drag)
        }
    }

    private func stop(_ stop: FlightStatusCardStop, x: Double, _ alignment: Alignment) -> some View {
        Group {
            Text(stop.time)
                .font(.system(size: 10.5))
                .monospacedDigit()
                .foregroundStyle(grey)
                .place(x: x, y: 14, width: 180, height: 14, alignment)
            Text(stop.code)
                .font(.system(size: 22, weight: .bold))
                .tracking(-0.2)
                .foregroundStyle(ink)
                .place(x: x, y: 30, width: 180, height: 28, alignment)
            Text(stop.city)
                .font(.system(size: 12))
                .foregroundStyle(Color(hex: 0x6f7379))
                .place(x: x, y: 60, width: 180, height: 16, alignment)
        }
    }

    private func remaining(_ left: Int) -> String {
        guard left > 0 else { return arrived }
        let hours = left / 60
        let rest = left % 60
        let hourWord = (hours == 1 ? hourWords.first : hourWords.last) ?? ""
        let minuteWord = (rest == 1 ? minuteWords.first : minuteWords.last) ?? ""
        return "\(hours) \(hourWord) \(rest < 10 ? "0" : "")\(rest) \(minuteWord)"
    }

    /// Angle in degrees and opacity of chip `k`: the old status tips back out of sight, then the new one tips in.
    private func chipLook(_ k: Int, flip: Double) -> (angle: Double, opacity: Double) {
        if reduceMotion {
            let seen = clamp01(flip / Metrics.fadeTime)
            return (0, k == motion.status ? seen : k == motion.statusFrom ? 1 - seen : 0)
        }
        if k == motion.status {
            let turn = clamp01((flip - Metrics.flipOut) / Metrics.flipIn)
            return (-90 * (1 - turn) * (1 - turn), flip >= Metrics.flipOut ? 1 : 0)
        }
        if k == motion.statusFrom {
            let turn = clamp01(flip / Metrics.flipOut)
            return (90 * turn * turn, flip < Metrics.flipOut ? 1 : 0)
        }
        return (0, 0)
    }

    /// The dashed line, its solid part behind the badge, and the badge with its plane.
    private func drawLine(_ context: GraphicsContext, progress: Double, green: Double, bounce: Double) {
        let x = Metrics.lineX + Metrics.badge + (Metrics.lineW - Metrics.badge * 2) * progress
        let y = Metrics.lineY
        var dashed = Path()
        dashed.move(to: CGPoint(x: Metrics.lineX, y: y))
        dashed.addLine(to: CGPoint(x: Metrics.lineX + Metrics.lineW, y: y))
        context.stroke(dashed, with: .color(Color(hex: 0xcfd2d6)), style: StrokeStyle(lineWidth: 1.2, dash: [3, 3]))
        var flown = Path()
        flown.move(to: CGPoint(x: Metrics.lineX, y: y))
        flown.addLine(to: CGPoint(x: x, y: y))
        context.stroke(flown, with: .color(ink), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))

        var badge = context
        badge.translateBy(x: x, y: y)
        badge.scaleBy(x: 1 + bounce, y: 1 + bounce)
        let disc = Path(ellipseIn: CGRect(x: -Metrics.badge, y: -Metrics.badge, width: Metrics.badge * 2, height: Metrics.badge * 2))
        var shadow = badge
        shadow.addFilter(.blur(radius: 3))
        shadow.translateBy(x: 0, y: 2)
        shadow.fill(disc, with: .color(.black.opacity(0.22)))
        badge.fill(disc, with: .color(ink))
        badge.fill(disc, with: .color(Color(hex: 0x1fa055, opacity: green)))
        // The plane is drawn nose up in a 20 × 20 box: turned to face the way it flies.
        badge.rotate(by: .degrees(90))
        badge.scaleBy(x: 0.62, y: 0.62)
        badge.translateBy(x: -10, y: -10)
        badge.fill(planeIcon, with: .color(.white))
    }

    // MARK: Touch and the autoPlay loop

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if dragFrom == nil {
                    dragFrom = motion.progress(at: .now)
                    grabs += 1
                }
                let before = motion.progress(at: .now)
                let next = clamp01((dragFrom ?? 0) + value.translation.width / (Metrics.lineW - Metrics.badge * 2))
                motion.hold(next)
                onScrub?(next)
                if statusAt(next) == 2, statusAt(before) != 2 {
                    landings += 1
                    onLand?()
                }
            }
            .onEnded { _ in
                dragFrom = nil
                touches += 1
            }
    }

    /// The flight plays, holds on the landing and rewinds; after a touch it first goes back to the gate.
    private func runLoop() async {
        guard auto, dragFrom == nil else { return }
        do {
            if touches > 0 {
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                motion.fly(to: 0, duration: Metrics.rewindTime, curve: rewindCurve)
                try await Task.sleep(for: .seconds(Metrics.rewindTime))
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                try await clock.sleep(until: start + .seconds(Metrics.takeOffAt))
                motion.fly(to: 1, duration: Metrics.flightTime, curve: flightCurve)
                try await clock.sleep(until: start + .seconds(Metrics.takeOffAt + Metrics.flightTime))
                onLand?()
                try await clock.sleep(until: start + .seconds(Metrics.rewindAt))
                motion.fly(to: 0, duration: Metrics.rewindTime, curve: rewindCurve)
                start += .seconds(Metrics.loop)
            }
        } catch {}
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
    static let size = 340.0
    static let radius = 28.0

    // The photo, 400 × 267, hangs over the card's sides and top so that it can slide and grow.
    static let photoW = 400.0
    static let photoH = 267.0
    static let photoY = -60.0
    /// How far the photo slides to either side of the middle, and how much it grows by the landing.
    static let drift = 16.0
    static let zoom = 0.05
    /// Where the card starts to fade from the photo into a plain dusk.
    static let fadeY = 166.0

    static let panelW = 324.0
    static let panelH = 160.0
    static let panelY = 172.0
    // The line the badge travels along, inside the panel, and the badge's radius.
    static let lineX = 98.0
    static let lineW = 128.0
    static let lineY = 44.0
    static let badge = 11.0

    /// The share of the flight at which the plane has left the gate.
    static let airborne = 0.04
    // The chip's flip: the old status tips out, then the new one tips in. Seconds.
    static let flipOut = 0.16
    static let flipIn = 0.22
    /// Length of the cross-fade that replaces the flip under reduced motion.
    static let fadeTime = 0.2
    static let greenIn = 0.25
    static let greenOut = 0.2
    // The landing knocks the badge's scale at this speed (per second); a spring of mass 1 brings it back.
    static let bounceKick = 5.0
    static let bounceStiffness = 260.0
    static let bounceDamping = 12.0

    // autoPlay (seconds): take-off, the flight, a look at the landing, a quick rewind, a rest.
    static let takeOffAt = 0.8
    static let flightTime = 4.6
    static let rewindAt = 7.2
    static let rewindTime = 0.7
    static let loop = 9.0
    /// After a touch the loop waits this long, then rewinds and starts over.
    static let resumeAfter = 2.0
}

private let flightCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.55, y: 1))
private let rewindCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.5, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))

private let ink = Color(hex: 0x121316)
private let grey = Color(hex: 0x8b8f97)
/// The dot in the chip: amber at the gate, sky blue in the air, green on the ground.
private let chipDots = [Color(hex: 0xfbbf24), Color(hex: 0x7dd3fc), Color(hex: 0x4ade80)]

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

/// How far the knocked spring is from rest `t` seconds after the landing.
private func kick(_ t: Double) -> Double {
    guard t > 0 else { return 0 }
    let decay = Metrics.bounceDamping / 2
    let ring = (Metrics.bounceStiffness - decay * decay).squareRoot()
    return Metrics.bounceKick / ring * exp(-decay * t) * sin(ring * t)
}

/// 0 at the gate, 1 in the air, 2 landed.
private func statusAt(_ progress: Double) -> Int {
    progress >= 0.999 ? 2 : progress > Metrics.airborne ? 1 : 0
}

/// A plane seen from above, nose up, in a 20 × 20 box.
private let planeIcon: Path = {
    var path = Path()
    path.move(to: CGPoint(x: 10, y: 1.5))
    path.addQuadCurve(to: CGPoint(x: 11.4, y: 4), control: CGPoint(x: 11.4, y: 1.5))
    for point in [(11.4, 7.6), (18, 11.6), (18, 13.4), (11.4, 11.4), (11.4, 15.4), (13.6, 17), (13.6, 18.4), (10, 17.4), (6.4, 18.4), (6.4, 17), (8.6, 15.4), (8.6, 11.4), (2, 13.4), (2, 11.6), (8.6, 7.6), (8.6, 4)] {
        path.addLine(to: CGPoint(x: point.0, y: point.1))
    }
    path.addQuadCurve(to: CGPoint(x: 10, y: 1.5), control: CGPoint(x: 8.6, y: 1.5))
    path.closeSubpath()
    return path
}()

#Preview {
    ZStack {
        Color(hex: 0xf1f1f1).ignoresSafeArea()
        FlightStatusCard(autoPlay: true)
    }
}
