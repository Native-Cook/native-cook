import SwiftUI

/// A compact duration scrubber. A dark strip holds the start time and the end time in two chips and
/// a row of ticks between them; a red marker stands in the ticks at the chosen duration, with the
/// duration in a pill riding above it. Dragging along the strip (or tapping it) moves the marker:
/// the ticks it passes light up behind it like a comet's tail, the pill's digits roll, and the end
/// time follows.
struct DurationScrubStrip: View {
    /// The longest duration, in minutes.
    var range = 720
    /// Minutes between two ticks.
    var tickEvery = 30
    /// The duration at first; `autoPlay` comes back to it at the end of every loop.
    var minutes = 0
    /// When the stretch of time begins, in minutes after midnight.
    var start = 16 * 60 + 30
    /// Scrubs to three durations and back by itself, in a loop.
    var autoPlay = false
    /// Called with the duration in minutes whenever it changes.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = DurationScrubStripSim()
    @State private var reported = 0
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0
    /// A finger is on the strip.
    @State private var holding = false
    /// Counts the ticks passed by hand, for the haptic.
    @State private var ticks = 0
    @State private var notch = 0

    private var auto: Bool { autoPlay && !reduceMotion && range > 0 }
    private var rest: Int { min(max(minutes, 0), range) }

    var body: some View {
        TimelineView(.animation) { timeline in
            let _ = sim.advance(to: timeline.date, still: reduceMotion)
            let frame = sim.frame
            Canvas { context, _ in
                durationScrubStripDraw(context, frame: frame, start: start)
            }
        }
        .frame(width: Metrics.width, height: Metrics.top + Metrics.height)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    let share = min(max((value.location.x - Metrics.trackX) / Metrics.trackW, 0), 1)
                    let target = (share * Double(range)).rounded()
                    let passed = Int(target) / max(tickEvery, 1)
                    if !holding {
                        holding = true
                        touches += 1
                        notch = Int(sim.target) / max(tickEvery, 1)
                    }
                    if passed != notch {
                        notch = passed
                        ticks += 1
                    }
                    sim.scrub(to: target)
                }
                .onEnded { _ in
                    holding = false
                    touches += 1
                }
        )
        .accessibilityElement()
        .accessibilityLabel("Duration")
        .accessibilityValue(durationScrubStripLabel(reported))
        .accessibilityAdjustableAction { direction in
            touches += 1
            let step = Double(direction == .increment ? tickEvery : -tickEvery)
            sim.scrub(to: min(max(sim.target + step, 0), Double(range)))
        }
        .sensoryFeedback(.selection, trigger: ticks)
        .onAppear { reset() }
        .onChange(of: range) { reset() }
        .onChange(of: tickEvery) { reset() }
        .onChange(of: minutes) { reset() }
        .task { await watch() }
        .task(id: LoopKey(auto: auto, touches: touches, holding: holding)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
        let holding: Bool
    }

    private func reset() {
        reported = rest
        sim.reset(range: range, tickEvery: tickEvery, minutes: rest)
    }

    /// Reports the duration. The marker moves inside the drawing loop, which may not change the
    /// view's state, so this looks at it from outside.
    private func watch() async {
        while !Task.isCancelled {
            if sim.frame.minutes != reported {
                reported = sim.frame.minutes
                onChange?(reported)
            }
            try? await Task.sleep(for: .milliseconds(16))
        }
    }

    /// The script a finger would follow: three runs up the strip, each further, and a sweep back.
    private func runLoop() async {
        guard auto, !holding else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to the first duration and start over.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                sim.glide(to: Double(rest), duration: Metrics.backTime)
                try await Task.sleep(for: .seconds(Metrics.backTime))
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for move in Metrics.script {
                    try await clock.sleep(until: start + .seconds(move.at))
                    sim.glide(to: move.share.map { ($0 * Double(range)).rounded() } ?? Double(rest), duration: move.time)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// Everything that moves, stepped in small fixed steps from the drawing loop.
final class DurationScrubStripSim {
    /// One character of the pill: the one it shows, the one it showed before, and how far the
    /// change has come, 0…1.
    struct Slot {
        var now: Character? = nil
        var before: Character? = nil
        var roll = 1.0
    }

    /// What a frame is drawn from.
    struct Frame {
        var range = 720.0
        /// The marker's place, in minutes, and the whole minutes it reads as.
        var value = 0.0
        var minutes = 0
        /// The pill's centre and width, in points.
        var badge = 0.0
        var badgeWidth = 0.0
        var slots = Array(repeating: Slot(), count: 7)
        /// +1 when the duration last grew, -1 when it shrank: the way the digits roll.
        var way = 1.0
        /// Each tick: how lit it is, 0…1, and the flash it got when the marker passed.
        var lit: [Double] = []
        var flash: [Double] = []
    }

    private(set) var frame = Frame()
    /// Where the marker is heading, in minutes.
    private(set) var target = 0.0

    private var tickEvery = 30.0
    // The marker's journey when it travels by itself.
    private var from = 0.0
    private var to = 0.0
    private var elapsed = 0.0
    private var duration = 0.0
    private var valueSpeed = 0.0
    private var badgeSpeed = 0.0
    private var widthSpeed = 0.0
    private var label = ""
    private var passed: [Bool] = []
    private var last: Date? = nil

    func reset(range: Int, tickEvery: Int, minutes: Int) {
        frame.range = Double(max(range, 1))
        self.tickEvery = Double(max(tickEvery, 1))
        let count = Int(frame.range / self.tickEvery) + 1
        frame.lit = Array(repeating: 0, count: count)
        frame.flash = Array(repeating: 0, count: count)
        passed = Array(repeating: false, count: count)
        target = Double(minutes)
        duration = 0
        settle()
    }

    /// A finger put the marker at `minutes`.
    func scrub(to minutes: Double) {
        duration = 0
        target = minutes
    }

    /// Sends the marker to `minutes` by itself, easing in and out.
    func glide(to minutes: Double, duration: Double) {
        from = target
        to = minutes
        elapsed = 0
        self.duration = duration
    }

    func advance(to now: Date, still: Bool) {
        guard let before = last else {
            last = now
            return
        }
        let gone = min(now.timeIntervalSince(before), Metrics.longestFrame)
        guard gone > 0 else { return }
        last = now
        let steps = max(1, Int((gone / Metrics.tick).rounded(.up)))
        for _ in 0..<steps { move(gone / Double(steps)) }
        // Under reduced motion nothing glides: every change is a jump.
        if still { settle() }
    }

    private func x(_ minutes: Double) -> Double { Metrics.trackX + minutes / frame.range * Metrics.trackW }

    private func move(_ h: Double) {
        if duration > 0 {
            elapsed += h
            let t = min(1, elapsed / duration)
            target = from + (to - from) * durationScrubStripInOut(t)
            if t >= 1 { duration = 0 }
        }

        // The marker follows its target on a firm spring, the pill follows the marker on a loose one.
        valueSpeed += (Metrics.follow.stiffness * (target - frame.value) - Metrics.follow.damping * valueSpeed) * h
        frame.value += valueSpeed * h
        badgeSpeed += (Metrics.ride.stiffness * (x(frame.value) - frame.badge) - Metrics.ride.damping * badgeSpeed) * h
        frame.badge += badgeSpeed * h

        read()
        widthSpeed += (Metrics.stretch.stiffness * (durationScrubStripWidth(label) - frame.badgeWidth) - Metrics.stretch.damping * widthSpeed) * h
        frame.badgeWidth += widthSpeed * h
        for i in frame.slots.indices { frame.slots[i].roll = min(1, frame.slots[i].roll + h / Metrics.rollTime) }

        let at = frame.value / tickEvery
        for i in frame.lit.indices {
            let on = Double(i) <= at + 0.02
            if on, !passed[i] { frame.flash[i] = 1 }
            passed[i] = on
            // A tick lights at once and goes out slowly.
            frame.lit[i] += ((on ? 1 : 0) - frame.lit[i]) * (1 - exp(-h / (on ? Metrics.lightTime : Metrics.dimTime)))
            frame.flash[i] *= exp(-h / Metrics.flashTime)
        }
    }

    /// Turns the marker's place into whole minutes and gives each character of the pill its place.
    private func read() {
        let minutes = Int(min(max(frame.value, 0), frame.range).rounded())
        guard minutes != frame.minutes || label.isEmpty else { return }
        frame.way = minutes >= frame.minutes ? 1 : -1
        frame.minutes = minutes
        label = durationScrubStripLabel(minutes)
        let characters = Array(label)
        for i in frame.slots.indices {
            let character = i < characters.count ? characters[i] : nil
            if character != frame.slots[i].now {
                frame.slots[i] = Slot(now: character, before: frame.slots[i].now, roll: 0)
            }
        }
    }

    private func settle() {
        frame.value = target
        valueSpeed = 0
        frame.badge = x(target)
        badgeSpeed = 0
        label = ""
        read()
        frame.badgeWidth = durationScrubStripWidth(label)
        widthSpeed = 0
        for i in frame.slots.indices { frame.slots[i].roll = 1 }
        for i in frame.lit.indices {
            passed[i] = Double(i) <= target / tickEvery + 0.02
            frame.lit[i] = passed[i] ? 1 : 0
            frame.flash[i] = 0
        }
    }
}

private enum Metrics {
    static let width = 330.0
    static let height = 53.0
    /// Room above the strip for the pill, which straddles its top edge.
    static let top = 14.0
    static let radius = 20.0
    static let pad = 8.0
    static let chipW = 74.0
    static let chipRadius = 9.0
    /// The ticks run from `trackX` across `trackW`.
    static let trackX = 93.0
    static let trackW = 144.0
    static let tickW = 1.5
    static let tickH = 20.0
    static let markerW = 2.0
    static let markerH = 26.0
    static let badgeH = 22.0
    static let badgePad = 8.0
    /// The comet's tail is this many ticks long.
    static let tail = 9.0

    /// The simulation's step, in seconds.
    static let tick = 1.0 / 240
    /// A frame that took longer than this is counted as this long.
    static let longestFrame = 0.25
    static let follow = (stiffness: 420.0, damping: 36.0)
    static let ride = (stiffness: 600.0, damping: 30.0)
    static let stretch = (stiffness: 400.0, damping: 30.0)
    static let rollTime = 0.16
    static let lightTime = 0.03
    static let dimTime = 0.12
    static let flashTime = 0.2

    // autoPlay (seconds). Each run: when it starts, the share of the range it goes to (nil: back to
    // the first duration), how long it takes.
    static let loop = 8.0
    static let backTime = 0.7
    static let script: [(at: Double, share: Double?, time: Double)] = [
        (0.8, 0.325, 1.1), (2.5, 0.686, 1.0), (4.1, 1, 0.8), (5.8, nil, backTime),
    ]
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

/// Cubic ease-in-out, for the marker's runs.
private func durationScrubStripInOut(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// "0m", "54m", "3h 54m", "12h".
private func durationScrubStripLabel(_ minutes: Int) -> String {
    let h = minutes / 60
    let m = minutes % 60
    if h == 0 { return "\(m)m" }
    return m == 0 ? "\(h)h" : "\(h)h \(m)m"
}

/// The room a character of the pill takes: digits all take the same, so the pill does not shiver.
private func advance(_ character: Character) -> Double {
    switch character {
    case " ": return 3
    case "h": return 6.6
    case "m": return 9.8
    default: return 6.8
    }
}

private func durationScrubStripWidth(_ label: String) -> Double {
    label.reduce(Metrics.badgePad * 2) { $0 + advance($1) }
}

/// A time of day as "4:30 PM".
private func clock(_ minutes: Int) -> String {
    let day = (minutes % 1440 + 1440) % 1440
    let hour = day / 60 % 12
    let minute = day % 60
    return "\(hour == 0 ? 12 : hour):\(minute < 10 ? "0" : "")\(minute) \(day < 720 ? "AM" : "PM")"
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

/// Draws the strip in a box `Metrics.width` wide, its top edge `Metrics.top` down.
private func durationScrubStripDraw(_ context: GraphicsContext, frame: DurationScrubStripSim.Frame, start: Int) {
    let top = Metrics.top
    let middle = top + Metrics.height / 2
    let red = Color(hex: 0xff3b1f)

    let strip = RoundedRectangle(cornerRadius: Metrics.radius, style: .circular)
        .path(in: CGRect(x: 0.5, y: top + 0.5, width: Metrics.width - 1, height: Metrics.height - 1))
    context.fill(strip, with: .color(Color(hex: 0x1b1b1b)))
    context.stroke(strip, with: .color(Color(hex: 0x3a3a3a)), lineWidth: 1)

    let chipH = Metrics.height - Metrics.pad * 2
    for (x, minutes) in [(Metrics.pad, start), (Metrics.width - Metrics.pad - Metrics.chipW, start + frame.minutes)] {
        let chip = RoundedRectangle(cornerRadius: Metrics.chipRadius, style: .circular)
            .path(in: CGRect(x: x + 0.5, y: top + Metrics.pad + 0.5, width: Metrics.chipW - 1, height: chipH - 1))
        context.fill(chip, with: .color(Color(hex: 0x2a2a2a)))
        context.stroke(chip, with: .color(Color(hex: 0x3f3f3f)), lineWidth: 1)
        context.draw(
            Text(clock(minutes)).font(.system(size: 12, weight: .bold, design: .monospaced)).foregroundStyle(.white),
            at: CGPoint(x: x + Metrics.chipW / 2, y: middle), anchor: .center
        )
    }

    let count = frame.lit.count
    let at = frame.value / frame.range * Double(max(count - 1, 1))
    for i in 0..<count {
        let x = Metrics.trackX + Double(i) / Double(max(count - 1, 1)) * Metrics.trackW
        // Not reached: dim, and dimmer towards the end. Passed: bright just behind the marker, greyer further back.
        let dim = mix(0.16, 0.06, Double(i) / Double(max(count - 1, 1)))
        let bright = mix(0.92, 0.34, clamp01((at - Double(i)) / Metrics.tail))
        let glow = mix(dim, bright, frame.lit[i])
        let opacity = glow + (1 - glow) * frame.flash[i] * frame.lit[i]
        context.fill(
            capsule(CGRect(x: x - Metrics.tickW / 2, y: middle - Metrics.tickH / 2, width: Metrics.tickW, height: Metrics.tickH)),
            with: .color(.white.opacity(opacity))
        )
    }

    let x = Metrics.trackX + frame.value / frame.range * Metrics.trackW
    context.fill(
        capsule(CGRect(x: x - Metrics.markerW / 2, y: middle - Metrics.markerH / 2, width: Metrics.markerW, height: Metrics.markerH)),
        with: .color(red)
    )

    // The pill leans back while it is behind the marker, about the middle of its lower edge.
    var pill = context
    pill.translateBy(x: frame.badge, y: top + Metrics.badgeH / 2)
    pill.rotate(by: .degrees(min(max((frame.badge - x) * 0.6, -8), 8)))
    pill.translateBy(x: 0, y: -Metrics.badgeH / 2)
    pill.fill(capsule(CGRect(x: -frame.badgeWidth / 2, y: -Metrics.badgeH / 2, width: frame.badgeWidth, height: Metrics.badgeH)), with: .color(red))

    // The characters are laid out by what the pill reads now; one that changed rolls in over the one it replaces.
    let shown = frame.slots.compactMap(\.now)
    var left = -(shown.reduce(0) { $0 + advance($1) }) / 2
    for slot in frame.slots {
        let room = advance(slot.now ?? slot.before ?? " ")
        let center = left + room / 2
        let eased = 1 - pow(1 - slot.roll, 3)
        if let character = slot.before, eased < 1 {
            var old = pill
            old.opacity = 1 - eased
            old.draw(durationScrubStripText(character), at: CGPoint(x: center, y: -frame.way * 9 * eased), anchor: .center)
        }
        if let character = slot.now {
            var new = pill
            new.opacity = eased
            new.draw(durationScrubStripText(character), at: CGPoint(x: center, y: frame.way * 9 * (1 - eased)), anchor: .center)
            left += room
        }
    }
}

private func durationScrubStripText(_ character: Character) -> Text {
    Text(String(character)).font(.system(size: 11, weight: .medium)).monospacedDigit().foregroundStyle(.white)
}

#Preview {
    ZStack {
        Color(hex: 0x161616).ignoresSafeArea()
        DurationScrubStrip(autoPlay: true)
    }
}
