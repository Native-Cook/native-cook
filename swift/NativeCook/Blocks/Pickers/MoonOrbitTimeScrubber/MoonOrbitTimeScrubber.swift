import SwiftUI
import UIKit

/// A time scrubber over a little model of the sky. A white pill with the time rides the card's right
/// edge, midnight at the top and 23:59 at the bottom, and everything follows it: the sky turns from
/// navy through violet to periwinkle, the horizon ring and the dotted path of the moon turn as one
/// solid model around a small pyramid, the moon climbs that path between moonrise and moonset, and
/// the bar at the bottom counts the lit share of its face.
struct MoonOrbitTimeScrubber: View {
    /// The time the pill starts on, in minutes after midnight.
    var initial = 720
    /// When the moon comes over the horizon and when it goes under, in minutes after midnight.
    var moonrise = 980
    var moonset = 250
    /// Where the "Now" chip sits.
    var now = 570
    var date = "Mar 10"
    var phase = "Waxing Gibbous"
    var place = "Reykjavik"
    /// Scrubs through a night by itself, in a loop.
    var autoPlay = false
    /// Called with the time the pill settles on, in minutes after midnight.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the script and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The time, in minutes after midnight, continuous while the pill moves.
        var time = MoonOrbitTimeScrubberTrack(720)
        /// 1 while the pill is held: it grows and comes away from the edge.
        var grab = MoonOrbitTimeScrubberTrack(0)
        /// The half hour the pill was in when the last frame was drawn.
        var notch = 0
        var held = false
        /// The pill moves under a finger, not under the script: only then it ticks.
        var byHand = false
        let haptic = UISelectionFeedbackGenerator()
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { geometry in
            let width = min(geometry.size.width - Metrics.margin * 2, Metrics.maxWidth)
            let card = CGRect(x: (geometry.size.width - width) / 2, y: (geometry.size.height - Metrics.height) / 2, width: width, height: Metrics.height)

            TimelineView(.animation) { timeline in
                let time = motion.time.value(at: timeline.date)
                let _ = notice(time)

                Canvas { context, _ in
                    var inside = context
                    inside.translateBy(x: card.minX, y: card.minY)
                    draw(inside, width: width, time: time, grab: motion.grab.value(at: timeline.date))
                }
            }
            .contentShape(Rectangle())
            .gesture(drag(card))
        }
        .frame(height: Metrics.height)
        .onAppear { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
        .accessibilityElement()
        .accessibilityLabel("Time")
        .accessibilityValue(clock(motion.time.value(at: .now)))
        .accessibilityAdjustableAction { direction in
            touch()
            settle(motion.time.value(at: .now) + (direction == .increment ? 30 : -30))
        }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Drawing

    private func draw(_ context: GraphicsContext, width: Double, time: Double, grab: Double) {
        let hour = time / 60
        let day = daylight(hour)
        let frame = CGRect(x: 0, y: 0, width: width, height: Metrics.height)
        let shape = Path(roundedRect: frame, cornerRadius: Metrics.radius)
        var card = context
        card.clip(to: shape)

        // The sky: night, with day laid over it, and a wash of violet where one turns into the other.
        let down = (CGPoint(x: 0, y: 0), CGPoint(x: 0, y: Metrics.height))
        card.fill(shape, with: .linearGradient(Gradient(colors: [Color(hex: 0x0C1033), Color(hex: 0x0A0D28)]), startPoint: down.0, endPoint: down.1))
        card.fill(shape, with: .linearGradient(Gradient(colors: [Color(hex: 0x3F45C4, opacity: day), Color(hex: 0x6A55D6, opacity: day)]), startPoint: down.0, endPoint: down.1))
        let dusk = 0.6 * 4 * day * (1 - day)
        card.fill(shape, with: .linearGradient(Gradient(colors: [Color(hex: 0x5A49C9, opacity: dusk), Color(hex: 0x7C55D2, opacity: dusk)]), startPoint: down.0, endPoint: down.1))

        let center = CGPoint(x: width / 2 + Metrics.modelX, y: Metrics.modelY)
        card.fill(
            Path(ellipseIn: CGRect(x: center.x - 150, y: center.y - 150, width: 300, height: 300)),
            with: .radialGradient(Gradient(colors: [.white.opacity(0.05 + 0.07 * day), .white.opacity(0)]), center: center, startRadius: 0, endRadius: 150)
        )
        drawModel(card, center: center, hour: hour, time: time, day: day)
        drawEdges(card, width: width)
        drawBar(card, width: width, time: time)

        // The pill, and the nub that ties it to the edge.
        let y = Metrics.trackTop + (Metrics.trackBottom - Metrics.trackTop) * time / Metrics.lastMinute
        card.fill(capsule(CGRect(x: width - 3, y: y - 8, width: 6, height: 16)), with: .color(.white))
        var pill = card
        pill.translateBy(x: width - Metrics.pillInset - Metrics.pillW / 2 - Metrics.pull * grab, y: y)
        pill.scaleBy(x: 1 + 0.06 * grab, y: 1 + 0.06 * grab)
        var shade = pill
        shade.addFilter(.shadow(color: .black.opacity(0.28), radius: 6, x: 0, y: 3))
        shade.fill(capsule(CGRect(x: -Metrics.pillW / 2, y: -Metrics.pillH / 2, width: Metrics.pillW, height: Metrics.pillH)), with: .color(.white))
        pill.draw(Text(clock(time)).font(.system(size: 14, weight: .bold)).monospacedDigit().foregroundStyle(Color(hex: 0x14163A)), at: .zero)

        context.stroke(Path(roundedRect: frame.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5), with: .color(.white.opacity(0.07)), lineWidth: 1)
    }

    /// The horizon ring, the moon's dotted path over it and the pyramid in the middle: one solid model
    /// that turns once a day and tips from edge-on in the morning to wide open at night.
    private func drawModel(_ context: GraphicsContext, center: CGPoint, hour: Double, time: Double, day: Double) {
        let view = MoonOrbitTimeScrubberPose(hour: hour)
        let ink = 0.22 + 0.1 * day

        context.stroke(
            Path(ellipseIn: CGRect(x: center.x - Metrics.ring, y: center.y - Metrics.ring * view.open, width: Metrics.ring * 2, height: Metrics.ring * 2 * view.open)),
            with: .color(.white.opacity(ink)), lineWidth: 1.2
        )

        // The path's far half is drawn before the pyramid and its near half after.
        var far = Path()
        var near = Path()
        for index in 0..<Metrics.dots {
            let spot = view.onPath(Double(index) / Double(Metrics.dots - 1))
            let dot = CGRect(x: center.x + spot.x * Metrics.ring - 1.3, y: center.y + spot.y * Metrics.ring - 1.3, width: 2.6, height: 2.6)
            if spot.depth < 0 { far.addEllipse(in: dot) } else { near.addEllipse(in: dot) }
        }
        context.fill(far, with: .color(.white.opacity(ink + 0.12)))

        var ground = context
        ground.addFilter(.blur(radius: 4))
        ground.fill(
            Path(ellipseIn: CGRect(x: center.x - Metrics.solid * 1.1, y: center.y - Metrics.solid * 1.1 * view.open, width: Metrics.solid * 2.2, height: Metrics.solid * 2.2 * view.open)),
            with: .color(.black.opacity(0.16))
        )

        // The pyramid: a rounded outline in a middle tone, with each face that looks at us laid softly over it.
        let dark = MoonOrbitTimeScrubberTone(0x5E648F).mixed(MoonOrbitTimeScrubberTone(0x8A90DC), day)
        let light = MoonOrbitTimeScrubberTone(0xB9BEDD).mixed(MoonOrbitTimeScrubberTone(0xD9DCFF), day)
        func corner(_ point: (x: Double, y: Double)) -> CGPoint {
            CGPoint(x: center.x + point.x * Metrics.solid, y: center.y + point.y * Metrics.solid)
        }
        let top = corner(view.apex)
        let base = (0..<3).map { corner(view.base($0)) }
        var outline = Path()
        outline.addLines(base)
        outline.closeSubpath()
        for side in 0..<3 where view.shade(side) != nil {
            outline.addLines([top, base[side], base[(side + 1) % 3]])
            outline.closeSubpath()
        }
        let middle = dark.mixed(light, 0.5).color
        context.fill(outline, with: .color(middle))
        context.stroke(outline, with: .color(middle), style: StrokeStyle(lineWidth: 8, lineJoin: .round))
        var faces = context
        faces.addFilter(.blur(radius: 2.5))
        for side in 0..<3 {
            guard let lit = view.shade(side) else { continue }
            var face = Path()
            face.addLines([top, base[side], base[(side + 1) % 3]])
            face.closeSubpath()
            faces.fill(face, with: .color(dark.mixed(light, lit).color))
        }

        context.fill(near, with: .color(.white.opacity(ink + 0.3)))

        // The moon rides the path while it is up, and fades where the path meets the ring.
        let up = Double((moonset - moonrise + 1440) % 1440)
        let since = (time - Double(moonrise) + 1440).truncatingRemainder(dividingBy: 1440)
        guard up > 0, since <= up else { return }
        let along = since / up
        let spot = view.onPath(along)
        let radius = Metrics.moon * (1 + 0.1 * spot.depth)
        let disc = Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2))
        var moon = context
        moon.opacity = clamp01(along / 0.06) * clamp01((1 - along) / 0.06)
        moon.translateBy(x: center.x + spot.x * Metrics.ring, y: center.y + spot.y * Metrics.ring)
        var halo = moon
        halo.addFilter(.blur(radius: 7))
        halo.fill(disc, with: .color(.white.opacity(0.3 * (1 - day))))
        moon.fill(
            disc,
            with: .radialGradient(
                Gradient(stops: [.init(color: Color(hex: 0xF6F6F8), location: 0), .init(color: Color(hex: 0xC8C9D2), location: 0.55), .init(color: Color(hex: 0x8B8D9B), location: 1)]),
                center: CGPoint(x: -0.35 * radius, y: -0.4 * radius), startRadius: 0, endRadius: 1.6 * radius
            )
        )
        var seas = moon
        seas.clip(to: disc)
        seas.addFilter(.blur(radius: 1))
        for sea in Metrics.seas {
            let box = CGRect(x: (sea[0] - sea[2]) * radius, y: (sea[1] - sea[3]) * radius, width: sea[2] * 2 * radius, height: sea[3] * 2 * radius)
            seas.fill(Path(ellipseIn: box), with: .color(Color(hex: 0x70727F, opacity: 0.5)))
        }
    }

    /// What is fixed along the two edges: the ends of the day, moonset and moonrise, "Now", and the date.
    private func drawEdges(_ context: GraphicsContext, width: Double) {
        let pale = Color(hex: 0x8E93C9)
        func y(_ minute: Int) -> Double {
            Metrics.trackTop + (Metrics.trackBottom - Metrics.trackTop) * Double(minute) / Metrics.lastMinute
        }

        for (text, at, up) in [("00:00", Metrics.trackTop - 34, true), ("23:59", Metrics.trackBottom + 32, false)] {
            let box = CGRect(x: width - 12 - 52, y: at - 9, width: 52, height: 18)
            context.stroke(capsule(box.insetBy(dx: 0.5, dy: 0.5)), with: .color(.white.opacity(0.28)), lineWidth: 1)
            context.draw(Text(text).font(.system(size: 9, weight: .medium)).monospacedDigit().foregroundStyle(.white.opacity(0.6)), at: CGPoint(x: box.minX + 21, y: at))
            var chevron = Path()
            chevron.move(to: CGPoint(x: box.maxX - 14, y: at + (up ? 1.5 : -1.5)))
            chevron.addLine(to: CGPoint(x: box.maxX - 11, y: at + (up ? -1.5 : 1.5)))
            chevron.addLine(to: CGPoint(x: box.maxX - 8, y: at + (up ? 1.5 : -1.5)))
            context.stroke(chevron, with: .color(.white.opacity(0.6)), style: StrokeStyle(lineWidth: 1.1, lineCap: .round, lineJoin: .round))
        }

        // A small moon with an arrow: down where it sets, up where it rises.
        for (minute, rising) in [(moonset, false), (moonrise, true)] {
            let at = CGPoint(x: width - 21, y: y(minute))
            context.fill(Path(ellipseIn: CGRect(x: at.x - 5, y: at.y - 5, width: 10, height: 10)), with: .color(pale))
            let turn = rising ? -1.0 : 1.0
            var arrow = Path()
            arrow.move(to: CGPoint(x: at.x + 9, y: at.y - 3.5 * turn))
            arrow.addLine(to: CGPoint(x: at.x + 9, y: at.y + 3.5 * turn))
            arrow.move(to: CGPoint(x: at.x + 6.5, y: at.y + 1 * turn))
            arrow.addLine(to: CGPoint(x: at.x + 9, y: at.y + 3.5 * turn))
            arrow.addLine(to: CGPoint(x: at.x + 11.5, y: at.y + 1 * turn))
            context.stroke(arrow, with: .color(pale), style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round))
        }

        let chip = CGRect(x: width - 14 - 36, y: y(now) - 9.5, width: 36, height: 19)
        context.fill(capsule(chip), with: .color(.white.opacity(0.16)))
        context.draw(Text("Now").font(.system(size: 9.5, weight: .semibold)).foregroundStyle(.white.opacity(0.85)), at: CGPoint(x: chip.midX, y: chip.midY))

        context.fill(capsule(CGRect(x: -3, y: Metrics.dateY - 8, width: 6, height: 16)), with: .color(.white))
        let tag = CGRect(x: 14, y: Metrics.dateY - 13, width: 60, height: 26)
        context.fill(capsule(tag), with: .color(.white))
        context.draw(Text(date).font(.system(size: 12, weight: .bold)).foregroundStyle(Color(hex: 0x14163A)), at: CGPoint(x: tag.midX, y: tag.midY))
        for (at, filled) in [(120.0, false), (300.0, true), (470.0, false)] {
            let stop = Path(ellipseIn: CGRect(x: 9.5, y: at - 2.5, width: 5, height: 5))
            if filled { context.fill(stop, with: .color(.white.opacity(0.7))) } else { context.stroke(stop, with: .color(.white.opacity(0.5)), lineWidth: 1) }
        }
    }

    /// The bar at the bottom: the phase, the lit share of the moon's face, and the place.
    private func drawBar(_ context: GraphicsContext, width: Double, time: Double) {
        let pale = Color(hex: 0x8E93C9)
        let bar = CGRect(x: 12, y: Metrics.barY, width: width - 24, height: 56)
        context.fill(Path(roundedRect: bar, cornerRadius: 22), with: .color(.black.opacity(0.24)))
        context.stroke(Path(roundedRect: bar.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 21.5), with: .color(.white.opacity(0.07)), lineWidth: 1)

        // A gibbous disc: lit, with a sliver of shadow on its left.
        let tile = CGPoint(x: bar.minX + 28, y: bar.midY)
        context.fill(Path(ellipseIn: CGRect(x: tile.x - 18, y: tile.y - 18, width: 36, height: 36)), with: .color(.white.opacity(0.1)))
        let disc = Path(ellipseIn: CGRect(x: tile.x - 8, y: tile.y - 8, width: 16, height: 16))
        context.fill(disc, with: .color(Color(hex: 0xDADDF6)))
        var shadow = context
        shadow.clip(to: disc)
        shadow.fill(Path(ellipseIn: CGRect(x: tile.x - 20.5, y: tile.y - 8, width: 16, height: 16)), with: .color(Color(hex: 0x14163A, opacity: 0.6)))

        let name = context.resolve(Text(phase).font(.system(size: 14, weight: .bold)).foregroundStyle(.white))
        let nameWidth = name.measure(in: CGSize(width: 400, height: 40)).width
        context.draw(name, at: CGPoint(x: bar.minX + 56, y: bar.midY - 8), anchor: .leading)
        let share = Metrics.shareFrom + (Metrics.shareTo - Metrics.shareFrom) * time / Metrics.lastMinute
        let tenths = Int((share * 10).rounded())
        context.draw(
            Text("\(tenths / 10).\(tenths % 10)%").font(.system(size: 14, weight: .medium)).monospacedDigit().foregroundStyle(pale),
            at: CGPoint(x: bar.minX + 56 + nameWidth + 5, y: bar.midY - 8), anchor: .leading
        )
        context.draw(Text(place).font(.system(size: 11)).foregroundStyle(pale.opacity(0.85)), at: CGPoint(x: bar.minX + 56, y: bar.midY + 10), anchor: .leading)

        let button = CGRect(x: bar.maxX - 42, y: bar.midY - 15, width: 30, height: 30)
        context.fill(Path(roundedRect: button, cornerRadius: 10), with: .color(.white.opacity(0.1)))
        var calendar = Path(roundedRect: CGRect(x: button.midX - 6, y: button.midY - 5, width: 12, height: 11), cornerRadius: 2.5)
        calendar.move(to: CGPoint(x: button.midX - 6, y: button.midY - 1.5))
        calendar.addLine(to: CGPoint(x: button.midX + 6, y: button.midY - 1.5))
        context.stroke(calendar, with: .color(.white.opacity(0.8)), lineWidth: 1.2)
    }

    // MARK: The pill

    private func clock(_ time: Double) -> String {
        let minute = Int(min(max(time.rounded(), 0), Metrics.lastMinute))
        let hours = minute / 60
        let minutes = minute % 60
        return "\(hours < 10 ? "0" : "")\(hours):\(minutes < 10 ? "0" : "")\(minutes)"
    }

    private func reset() {
        let start = min(max(Double(initial), 0), Metrics.lastMinute)
        motion.time = MoonOrbitTimeScrubberTrack(start)
        motion.grab = MoonOrbitTimeScrubberTrack(0)
        motion.notch = Int(start / Metrics.notch)
        motion.held = false
    }

    /// Runs every frame: a tick for each half hour the pill passes under a finger.
    private func notice(_ time: Double) {
        let notch = Int(time / Metrics.notch)
        guard notch != motion.notch else { return }
        motion.notch = notch
        if motion.byHand { motion.haptic.selectionChanged() }
    }

    private func hold(_ down: Bool) {
        motion.grab.retarget(down ? 1 : 0, reduceMotion ? .jump : .spring(grabSpring))
    }

    /// Lets the pill go: it settles on the nearest five minutes.
    private func settle(_ time: Double) {
        let target = min(max((time / Metrics.snap).rounded() * Metrics.snap, 0), Metrics.lastMinute)
        motion.time.retarget(target, reduceMotion ? .jump : .spring(snapSpring))
        onChange?(Int(target))
    }

    private func touch() {
        motion.byHand = true
        touches += 1
    }

    /// A touch on the right-hand strip takes the pill: it comes to the finger and follows it up and down.
    private func drag(_ card: CGRect) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if !motion.held {
                    guard value.startLocation.x > card.maxX - Metrics.grip, value.startLocation.x < card.maxX + Metrics.margin else { return }
                    motion.held = true
                    motion.haptic.prepare()
                    touch()
                    hold(true)
                }
                let along = (value.location.y - card.minY - Metrics.trackTop) / (Metrics.trackBottom - Metrics.trackTop)
                motion.time.retarget(clamp01(along) * Metrics.lastMinute, reduceMotion ? .jump : .spring(followSpring))
            }
            .onEnded { _ in
                guard motion.held else { return }
                motion.held = false
                hold(false)
                settle(motion.time.target)
                touch()
            }
    }

    /// The script a finger would follow: down into the night, to the end of the day, up to before dawn and back to noon.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, glide back to the first time, and start over from rest.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                if motion.held { return }
                motion.byHand = false
                motion.time.retarget(min(max(Double(initial), 0), Metrics.lastMinute), .tween(Metrics.returnTime, .easeInOut))
                try await Task.sleep(for: .seconds(Metrics.returnTime))
            }
            motion.byHand = false
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for step in Metrics.script {
                    try await clock.sleep(until: start + .seconds(step.at))
                    let target = step.to ?? min(max(Double(initial), 0), Metrics.lastMinute)
                    hold(true)
                    motion.time.retarget(target, .tween(step.time, .easeInOut))
                    try await clock.sleep(until: start + .seconds(step.at + step.time))
                    hold(false)
                    onChange?(Int(target))
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// The model's pose at an hour of the day, and where its points fall on the screen. Points come back
/// in units of the ring's radius (the pyramid's in units of its own size), `y` down.
struct MoonOrbitTimeScrubberPose {
    /// How open the ring looks: its height over its width, from 0.06 (edge-on, at 07:00) to 0.66.
    let open: Double
    /// The model's turn about its upright axis: once round in a day.
    let turn: Double
    private let rise: Double

    init(hour: Double) {
        open = 0.36 - 0.3 * cos(tau * (hour - 7) / 24)
        // The moon's path is edge-on at 07:30 and 19:30, away from moonrise and moonset.
        turn = tau * (hour - 1.5) / 24
        rise = (1 - open * open).squareRoot()
    }

    private func project(_ x: Double, _ up: Double, _ z: Double, _ angle: Double) -> (x: Double, y: Double, depth: Double) {
        let across = x * cos(angle) + z * sin(angle)
        let depth = z * cos(angle) - x * sin(angle)
        return (across, depth * open - up * rise, depth)
    }

    /// A point of the moon's path, 0 where it rises … 1 where it sets: half an ellipse standing on the ring.
    func onPath(_ along: Double) -> (x: Double, y: Double, depth: Double) {
        project(cos(.pi * along), 0.78 * sin(.pi * along), 0, turn)
    }

    var apex: (x: Double, y: Double) {
        let point = project(0, 1.5, 0, turn)
        return (point.x, point.y)
    }

    func base(_ corner: Int) -> (x: Double, y: Double) {
        let angle = tau * Double(corner) / 3
        let point = project(cos(angle), 0, sin(angle), turn)
        return (point.x, point.y)
    }

    /// How brightly side `side` is lit, 0…1, or nil when it faces away from us.
    func shade(_ side: Int) -> Double? {
        // A side's outward normal leans up by the base's inradius over the pyramid's height.
        let angle = tau * (Double(side) + 0.5) / 3
        let length = (1.5 * 1.5 + 0.25).squareRoot()
        let normal = project(1.5 * cos(angle) / length, 0, 1.5 * sin(angle) / length, turn)
        let up = 0.5 / length
        guard open * up + rise * normal.depth > 0 else { return nil }
        // Light comes from the upper left, a little in front.
        return min(max(0.5 + 0.5 * (-0.5 * normal.x + 0.62 * up + 0.6 * normal.depth) / 0.997, 0), 1)
    }
}

/// A colour as three numbers, so two can be mixed.
struct MoonOrbitTimeScrubberTone {
    var red: Double
    var green: Double
    var blue: Double

    init(_ hex: UInt32) {
        red = Double((hex >> 16) & 0xff) / 255
        green = Double((hex >> 8) & 0xff) / 255
        blue = Double(hex & 0xff) / 255
    }

    func mixed(_ other: MoonOrbitTimeScrubberTone, _ t: Double) -> MoonOrbitTimeScrubberTone {
        var tone = self
        tone.red += (other.red - red) * t
        tone.green += (other.green - green) * t
        tone.blue += (other.blue - blue) * t
        return tone
    }

    var color: Color { Color(.sRGB, red: red, green: green, blue: blue, opacity: 1) }
}

/// A number heading for a target, sampled against the `TimelineView` clock.
struct MoonOrbitTimeScrubberTrack {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
    }

    private var from: Double
    private var to: Double
    private var speed = 0.0
    private var start = Date.distantPast
    private var kind = Kind.jump

    init(_ value: Double) {
        from = value
        to = value
    }

    /// Where it is heading.
    var target: Double { to }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return from }
        switch kind {
        case .jump: return to
        case .tween(let duration, let curve): return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
        case .spring(let spring): return t > 3 ? to : from + spring.value(target: to - from, initialVelocity: speed, time: t)
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, t <= 3, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
    }

    /// Heads for `target` from where it is now.
    mutating func retarget(_ target: Double, _ kind: Kind) {
        let now = Date.now
        let value = value(at: now)
        speed = velocity(at: now)
        from = value
        to = target
        start = now
        self.kind = kind
    }
}

private enum Metrics {
    static let height = 640.0
    static let margin = 16.0
    static let maxWidth = 380.0
    static let radius = 36.0
    static let lastMinute = 1439.0

    // The pill's run down the right edge: midnight at `trackTop`, 23:59 at `trackBottom`.
    static let trackTop = 64.0
    static let trackBottom = 508.0
    static let pillW = 74.0
    static let pillH = 30.0
    static let pillInset = 20.0
    /// How far the pill comes away from the edge while it is held.
    static let pull = 8.0
    /// The strip along the right edge where a touch takes the pill.
    static let grip = 120.0
    /// The pill settles on a multiple of this many minutes, and ticks every `notch` minutes.
    static let snap = 5.0
    static let notch = 30.0

    // The model: its centre (left of the card's centre line), the ring's radius, the pyramid's and the moon's size.
    static let modelX = -16.0
    static let modelY = 268.0
    static let ring = 96.0
    static let solid = 24.0
    static let moon = 13.0
    static let dots = 30
    /// The moon's seas: centre and radii, in units of its radius.
    static let seas = [[-0.3, -0.2, 0.36, 0.28], [0.32, 0.22, 0.26, 0.22], [0.02, -0.56, 0.2, 0.13], [-0.34, 0.46, 0.18, 0.14]]

    static let dateY = 205.0
    static let barY = 568.0
    /// The lit share of the moon's face at midnight and at 23:59, in percent.
    static let shareFrom = 82.2
    static let shareTo = 88.2

    // autoPlay (seconds): when the pill sets off, to which minute of the day (nil: back to where it began), and how long it takes.
    static let script: [(at: Double, to: Double?, time: Double)] = [(0.8, 1320, 2.2), (3.9, 1430, 0.7), (5.1, 240, 1.6), (7.2, nil, 1.3)]
    static let loop = 9.0
    /// After a touch the loop waits this long, then takes `returnTime` to glide back.
    static let resumeAfter = 2.0
    static let returnTime = 0.8
}

private let followSpring = Spring(mass: 1, stiffness: 600, damping: 46)
private let snapSpring = Spring(mass: 1, stiffness: 300, damping: 24)
private let grabSpring = Spring(mass: 1, stiffness: 320, damping: 20)

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func smooth(_ t: Double) -> Double {
    let x = clamp01(t)
    return x * x * (3 - 2 * x)
}

/// How much of the sky is day at `hour`: dawn runs from 04:36 to 06:36 and dusk from 17:54 to 19:24.
private func daylight(_ hour: Double) -> Double {
    smooth((hour - 4.6) / 2) * (1 - smooth((hour - 17.9) / 1.5))
}

#Preview {
    ZStack {
        Color(hex: 0x05061A).ignoresSafeArea()
        MoonOrbitTimeScrubber(autoPlay: true)
    }
}
