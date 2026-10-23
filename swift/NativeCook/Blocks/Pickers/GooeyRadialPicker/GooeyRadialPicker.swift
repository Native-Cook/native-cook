import SwiftUI

/// The moods the picker offers by default, each a small drawn face.
enum GooeyRadialPickerMood: String, CaseIterable {
    case happy = "Happy"
    case joyful = "Joyful"
    case calm = "Calm"
    case playful = "Playful"
    case surprised = "Surprised"
    case unsure = "Unsure"
    case sad = "Sad"
    case sleepy = "Sleepy"
}

/// A radial picker made of drops. A white disc in the middle shows the chosen mood large; the options
/// sit round it as small translucent circles with grey faces. The chosen one is opaque, in colour, and
/// joined to the disc by a neck, as two drops that touch. Dragging round the ring (or tapping an
/// option) moves an orange marker: the neck lets go of the circle it leaves and flows out to each
/// circle the marker reaches, and the face in the middle shrinks to a dot and pops back as the new one.
struct GooeyRadialPicker: View {
    /// The options, clockwise from 12 o'clock.
    var options = GooeyRadialPickerMood.allCases
    /// The option chosen at first; `autoPlay` comes back to it at the end of every loop.
    var selection = 2
    /// Moves the marker round the ring by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the option the marker reached.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = GooeyRadialPickerSim()
    @State private var selected = 0
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0
    /// A finger is on the picker.
    @State private var holding = false
    /// Counts the options reached by hand, for the haptic.
    @State private var ticks = 0

    private var auto: Bool { autoPlay && !reduceMotion && options.count > 1 }
    private var rest: Int { min(max(selection, 0), options.count - 1) }

    var body: some View {
        TimelineView(.animation) { timeline in
            let _ = sim.advance(to: timeline.date, still: reduceMotion)
            let frame = sim.frame
            Canvas { context, size in
                var context = context
                context.translateBy(x: size.width / 2, y: size.height / 2)
                gooeyRadialPickerDraw(context, frame: frame, options: options)
            }
        }
        // The canvas is larger than the picker, so the shadow has room.
        .frame(width: Metrics.size + Metrics.bleed * 2, height: Metrics.size + Metrics.bleed * 2)
        .allowsHitTesting(false)
        .frame(width: Metrics.size, height: Metrics.size)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    if !holding {
                        holding = true
                        touches += 1
                    }
                    sim.point(at: CGPoint(x: value.location.x - Metrics.size / 2, y: value.location.y - Metrics.size / 2))
                }
                .onEnded { _ in
                    sim.release()
                    holding = false
                    touches += 1
                }
        )
        .accessibilityElement()
        .accessibilityLabel("Mood")
        .accessibilityValue(options.indices.contains(selected) ? options[selected].rawValue : "")
        .accessibilityAdjustableAction { direction in
            touches += 1
            sim.travel(by: direction == .increment ? 1 : -1, duration: Metrics.stepTime)
        }
        .sensoryFeedback(.selection, trigger: ticks)
        .onAppear { reset() }
        .onChange(of: options.count) { reset() }
        .onChange(of: selection) { reset() }
        .task { await watch() }
        .task(id: LoopKey(auto: auto, touches: touches, holding: holding)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
        let holding: Bool
    }

    private func reset() {
        selected = rest
        sim.reset(count: options.count, rest: rest)
    }

    /// Reports the option the marker reached. The marker moves inside the drawing loop, which may not
    /// change the view's state, so this looks at it from outside.
    private func watch() async {
        while !Task.isCancelled {
            if sim.count == options.count, sim.hot != selected {
                selected = sim.hot
                if holding { ticks += 1 }
                onChange?(selected)
            }
            try? await Task.sleep(for: .milliseconds(16))
        }
    }

    /// The script a finger would follow: half a turn against the clock, two single steps, and the
    /// rest of the turn back to where the loop started.
    private func runLoop() async {
        guard auto, !holding else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to the first option and start over.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                sim.travel(to: rest, duration: Metrics.sweepTime)
                try await Task.sleep(for: .seconds(Metrics.sweepTime))
            }
            let count = options.count
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                sim.home(rest)
                var gone = 0
                for (n, move) in Metrics.script.enumerated() {
                    try await clock.sleep(until: start + .seconds(move.at))
                    // The last move finishes the turn, whatever the number of options.
                    let sectors = n == Metrics.script.count - 1 ? (count - gone % count) % count : max(1, Int((move.turn * Double(count)).rounded()))
                    gone += sectors
                    if sectors > 0 { sim.travel(by: -sectors, duration: move.time) }
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// Everything that moves, stepped in small fixed steps from the drawing loop.
final class GooeyRadialPickerSim {
    /// What a frame is drawn from.
    struct Frame {
        var pull: [Double] = []
        var shown = 0
        var face = 1.0
        var bump = 0.0
        var lead = CGPoint.zero
        var lag = CGPoint.zero
    }

    private(set) var count = 0
    /// The option the marker is nearest to.
    private(set) var hot = 0
    private(set) var frame = Frame()

    /// Where the marker points, as an angle round the ring (0 is 3 o'clock, growing clockwise).
    private var theta = 0.0
    // The marker's journey when it travels by itself.
    private var from = 0.0
    private var to = 0.0
    private var elapsed = 0.0
    private var duration = 0.0
    /// How fast each option's pull, the face and the disc's wobble are changing.
    private var speed: [Double] = []
    private var faceSpeed = 0.0
    private var faceTarget = 1.0
    private var bumpSpeed = 0.0
    /// Seconds since the marker last reached another option.
    private var calm = 0.0
    private var finger: CGPoint? = nil
    private var last: Date? = nil

    private var step: Double { tau / Double(max(count, 1)) }

    func angle(_ index: Int) -> Double { Double(index) * step - .pi / 2 }

    func reset(count: Int, rest: Int) {
        self.count = count
        hot = rest
        theta = angle(rest)
        duration = 0
        finger = nil
        speed = Array(repeating: 0, count: count)
        frame.pull = (0..<count).map { $0 == rest ? 1 : 0 }
        settle()
    }

    /// The marker's angle becomes exactly the resting option's: the same place, without the turns
    /// the loop has added up.
    func home(_ rest: Int) {
        guard duration == 0, finger == nil, hot == rest else { return }
        theta = angle(rest)
    }

    /// Sends the marker `sectors` options round the ring, clockwise when positive.
    func travel(by sectors: Int, duration: Double) {
        guard count > 0 else { return }
        finger = nil
        // Counted from where the marker is going, if it is still on its way.
        let base = duration > 0 ? to : theta
        from = theta
        to = ((base + .pi / 2) / step).rounded() * step - .pi / 2 + Double(sectors) * step
        elapsed = 0
        self.duration = duration
    }

    /// Sends the marker to option `index` the short way round.
    func travel(to index: Int, duration: Double) {
        guard count > 0 else { return }
        var sectors = (index - hot) % count
        if sectors > count / 2 { sectors -= count }
        if sectors < -count / 2 { sectors += count }
        travel(by: sectors, duration: duration)
    }

    /// A finger is at `point`, measured from the middle of the picker.
    func point(at point: CGPoint) {
        duration = 0
        finger = point
        // Too near the middle the direction means nothing.
        if hypot(point.x, point.y) > Metrics.deadZone { theta = atan2(point.y, point.x) }
    }

    /// The finger lifted: the marker settles under the option it reached.
    func release() {
        finger = nil
        theta = angle(hot)
    }

    func advance(to now: Date, still: Bool) {
        guard count > 0, let before = last else {
            last = now
            return
        }
        let passed = min(now.timeIntervalSince(before), Metrics.longestFrame)
        guard passed > 0 else { return }
        last = now
        let steps = max(1, Int((passed / Metrics.tick).rounded(.up)))
        for _ in 0..<steps { move(passed / Double(steps)) }
        // Under reduced motion nothing flows: every change is a jump.
        if still { settle() }
    }

    private func sector(_ theta: Double) -> Int {
        let n = Int(((theta + .pi / 2) / step).rounded())
        return (n % count + count) % count
    }

    /// Where the marker heads: under the finger, or just below the circle it points at.
    private var goal: CGPoint {
        finger ?? CGPoint(x: cos(theta) * Metrics.ring, y: sin(theta) * Metrics.ring + Metrics.dot)
    }

    private func move(_ h: Double) {
        if duration > 0 {
            elapsed += h
            let t = min(1, elapsed / duration)
            theta = from + (to - from) * gooeyRadialPickerInOut(t)
            if t >= 1 { duration = 0 }
        }

        let nearest = sector(theta)
        if nearest != hot {
            // The marker reached another option: the face shrinks away and the disc gets a knock.
            hot = nearest
            calm = 0
            faceTarget = 0
            bumpSpeed += Metrics.knock
        }
        calm += h
        if faceTarget == 0, calm >= Metrics.popAfter {
            frame.shown = hot
            faceTarget = 1
        }

        for i in 0..<count {
            // Joining is loose and overshoots; letting go is quick.
            let spring = i == hot ? Metrics.join : Metrics.part
            speed[i] += (spring.stiffness * ((i == hot ? 1 : 0) - frame.pull[i]) - spring.damping * speed[i]) * h
            frame.pull[i] += speed[i] * h
        }
        let spring = faceTarget > 0.5 ? Metrics.pop : Metrics.shrink
        faceSpeed += (spring.stiffness * (faceTarget - frame.face) - spring.damping * faceSpeed) * h
        frame.face += faceSpeed * h
        bumpSpeed += (-Metrics.wobble.stiffness * frame.bump - Metrics.wobble.damping * bumpSpeed) * h
        frame.bump += bumpSpeed * h

        // The marker's head follows closely and its tail lags, so a fast move smears it into a pill.
        let target = goal
        let near = 1 - exp(-h / Metrics.leadTime)
        let far = 1 - exp(-h / Metrics.lagTime)
        frame.lead.x += (target.x - frame.lead.x) * near
        frame.lead.y += (target.y - frame.lead.y) * near
        frame.lag.x += (frame.lead.x - frame.lag.x) * far
        frame.lag.y += (frame.lead.y - frame.lag.y) * far
    }

    private func settle() {
        for i in 0..<count {
            frame.pull[i] = i == hot ? 1 : 0
            speed[i] = 0
        }
        frame.shown = hot
        frame.face = 1
        faceSpeed = 0
        faceTarget = 1
        frame.bump = 0
        bumpSpeed = 0
        frame.lead = goal
        frame.lag = goal
    }
}

private enum Metrics {
    /// The square the picker is laid out in, and the room for the shadow round it.
    static let size = 344.0
    static let bleed = 44.0
    /// Radius of the disc in the middle, of an option's circle, and of the ring the options sit on.
    static let disc = 80.0
    static let dot = 35.0
    static let ring = 123.0
    /// A joined circle moves this far towards the disc.
    static let reach = 4.0
    /// Radius of a face in the middle and on an option.
    static let large = 40.0
    static let small = 14.5
    /// The marker: its radius and the width of its line.
    static let marker = 13.0
    static let markerLine = 7.0
    static let deadZone = 28.0

    /// The simulation's step, in seconds.
    static let tick = 1.0 / 240
    /// A frame that took longer than this is counted as this long.
    static let longestFrame = 0.25
    static let join = (stiffness: 260.0, damping: 13.0)
    static let part = (stiffness: 300.0, damping: 26.0)
    static let pop = (stiffness: 300.0, damping: 19.0)
    static let shrink = (stiffness: 900.0, damping: 60.0)
    static let wobble = (stiffness: 400.0, damping: 16.0)
    /// The speed the disc's radius gets when the marker reaches another option, in points a second.
    static let knock = 60.0
    /// The new face pops out once the marker has stayed on an option this long.
    static let popAfter = 0.22
    static let leadTime = 0.03
    static let lagTime = 0.08

    // autoPlay (seconds). Each move: when it starts, how much of a turn it covers, how long it takes.
    static let loop = 8.0
    static let sweepTime = 0.8
    static let stepTime = 0.6
    static let script: [(at: Double, turn: Double, time: Double)] = [
        (1.0, 0.5, sweepTime), (2.7, 0.125, stepTime), (4.1, 0.125, stepTime), (5.5, 0.25, sweepTime),
    ]
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

/// Cubic ease-in-out, for the marker's journeys.
private func gooeyRadialPickerInOut(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func circle(_ center: CGPoint, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
}

private func polar(_ center: CGPoint, _ angle: Double, _ radius: Double) -> CGPoint {
    CGPoint(x: center.x + cos(angle) * radius, y: center.y + sin(angle) * radius)
}

/// The bridge between a circle of radius `r1` at the origin and one of radius `r2` at `c2`: two
/// curves that leave each circle along its edge and meet in between. `v` is how far round the
/// circles the bridge takes hold, 0 a thread … 0.5 a thick neck.
private func neck(r1: Double, c2: CGPoint, r2: Double, v: Double) -> Path {
    let d = hypot(c2.x, c2.y)
    guard d > abs(r1 - r2), v > 0 else { return Path() }
    // Where the two circles cross, when they overlap.
    let overlap = d < r1 + r2
    let u1 = overlap ? acos((r1 * r1 + d * d - r2 * r2) / (2 * r1 * d)) : 0
    let u2 = overlap ? acos((r2 * r2 + d * d - r1 * r1) / (2 * r2 * d)) : 0
    let base = atan2(c2.y, c2.x)
    let spread = acos((r1 - r2) / d)
    let a1 = base + u1 + (spread - u1) * v
    let a2 = base - u1 - (spread - u1) * v
    let a3 = base + .pi - u2 - (.pi - u2 - spread) * v
    let a4 = base - .pi + u2 + (.pi - u2 - spread) * v
    let p1 = polar(.zero, a1, r1)
    let p2 = polar(.zero, a2, r1)
    let p3 = polar(c2, a3, r2)
    let p4 = polar(c2, a4, r2)
    // The curves' handles lie along the circles' edges, longer when the bridge is longer.
    let handle = min(v * 2.4, hypot(p1.x - p3.x, p1.y - p3.y) / (r1 + r2)) * min(1, d * 2 / (r1 + r2))
    var path = Path()
    path.move(to: p1)
    path.addCurve(to: p3, control1: polar(p1, a1 - .pi / 2, r1 * handle), control2: polar(p3, a3 + .pi / 2, r2 * handle))
    path.addLine(to: p4)
    path.addCurve(to: p2, control1: polar(p4, a4 - .pi / 2, r2 * handle), control2: polar(p2, a2 + .pi / 2, r1 * handle))
    path.closeSubpath()
    return path
}

/// The marker: a ring at rest, stretched into a pill between its tail and its head as it moves.
private func marker(from tail: CGPoint, to head: CGPoint) -> (path: Path, line: Double) {
    let length = hypot(head.x - tail.x, head.y - tail.y)
    // The hole closes as the pill gets longer.
    let line = mix(Metrics.markerLine, Metrics.marker, clamp01(length / 26))
    let r = Metrics.marker - line / 2
    let u = length > 0.01 ? CGPoint(x: (head.x - tail.x) / length, y: (head.y - tail.y) / length) : CGPoint(x: 1, y: 0)
    let n = CGPoint(x: -u.y, y: u.x)
    let k = 0.5523 * r
    func at(_ p: CGPoint, _ along: Double, _ across: Double) -> CGPoint {
        CGPoint(x: p.x + u.x * along + n.x * across, y: p.y + u.y * along + n.y * across)
    }
    var path = Path()
    path.move(to: at(tail, 0, r))
    path.addLine(to: at(head, 0, r))
    path.addCurve(to: at(head, r, 0), control1: at(head, k, r), control2: at(head, r, k))
    path.addCurve(to: at(head, 0, -r), control1: at(head, r, -k), control2: at(head, k, -r))
    path.addLine(to: at(tail, 0, -r))
    path.addCurve(to: at(tail, -r, 0), control1: at(tail, -k, -r), control2: at(tail, -r, -k))
    path.addCurve(to: at(tail, 0, r), control1: at(tail, -r, k), control2: at(tail, -k, r))
    path.closeSubpath()
    return (path, line)
}

/// Draws the picker round the origin.
private func gooeyRadialPickerDraw(_ context: GraphicsContext, frame: GooeyRadialPickerSim.Frame, options: [GooeyRadialPickerMood]) {
    let count = options.count
    guard count > 0, frame.pull.count == count else { return }
    let white = GraphicsContext.Shading.color(.white)
    let disc = Metrics.disc + frame.bump
    let centers = (0..<count).map { i in
        polar(.zero, Double(i) / Double(count) * tau - .pi / 2, Metrics.ring - Metrics.reach * min(max(frame.pull[i], 0), 1.2))
    }

    for center in centers {
        context.fill(circle(center, Metrics.dot), with: .color(.white.opacity(0.55)))
    }

    // The disc, the necks and the circles they hold are one shape with one shadow.
    var lifted = context
    lifted.addFilter(.shadow(color: Color(hex: 0x8fa3b5, opacity: 0.45), radius: 16, x: 12, y: 16))
    lifted.drawLayer { layer in
        layer.fill(circle(.zero, disc), with: white)
        for i in 0..<count where frame.pull[i] > 0.02 {
            layer.fill(neck(r1: disc, c2: centers[i], r2: Metrics.dot, v: 0.5 * min(frame.pull[i], 1.3)), with: white)
            layer.fill(circle(centers[i], Metrics.dot), with: .color(.white.opacity(clamp01(frame.pull[i] * 2.5))))
        }
    }

    for i in 0..<count {
        gooeyRadialPickerFace(context, options[i], at: centers[i], radius: Metrics.small, tint: clamp01(frame.pull[i]))
    }
    if frame.face > 0.03, options.indices.contains(frame.shown) {
        gooeyRadialPickerFace(context, options[frame.shown], at: .zero, radius: Metrics.large * frame.face, tint: 1)
    }

    let pill = marker(from: frame.lag, to: frame.lead)
    context.stroke(pill.path, with: .color(Color(hex: 0xf2b45a, opacity: 0.8)), lineWidth: pill.line)
}

/// A face's colours: in colour, or the grey of an option that is not chosen.
private struct Palette {
    var fill: Color
    var side: Color
    var ink: Color
    var cheek: Color?

    static let color = Palette(fill: Color(hex: 0xfbdd2e), side: Color(hex: 0xf2b03a), ink: Color(hex: 0x4a3a2a), cheek: Color(hex: 0xf79e86))
    static let grey = Palette(fill: Color(hex: 0xc9cdd2), side: Color(hex: 0xaeb2b8), ink: Color(hex: 0x8e9399), cheek: nil)
}

/// A mood's face. `tint` 0 draws it grey, 1 in colour.
private func gooeyRadialPickerFace(_ context: GraphicsContext, _ mood: GooeyRadialPickerMood, at center: CGPoint, radius: Double, tint: Double) {
    if tint < 0.99 { face(context, mood, at: center, radius: radius, palette: .grey) }
    if tint > 0.01 {
        var over = context
        over.opacity = tint
        over.drawLayer { face($0, mood, at: center, radius: radius, palette: .color) }
    }
}

private enum Eye {
    case dot, up, down, line
}

private enum Mouth {
    case smile, grin, round, slant, frown, flat
}

private func features(of mood: GooeyRadialPickerMood) -> (left: Eye, right: Eye, mouth: Mouth, cheeks: Bool) {
    switch mood {
    case .happy: return (.dot, .dot, .smile, true)
    case .joyful: return (.up, .up, .grin, false)
    case .calm: return (.down, .down, .smile, true)
    case .playful: return (.dot, .up, .grin, true)
    case .surprised: return (.dot, .dot, .round, false)
    case .unsure: return (.dot, .dot, .slant, false)
    case .sad: return (.dot, .dot, .frown, false)
    case .sleepy: return (.line, .line, .flat, false)
    }
}

/// Drawn in units of the face's radius round its centre.
private func face(_ context: GraphicsContext, _ mood: GooeyRadialPickerMood, at center: CGPoint, radius r: Double, palette: Palette) {
    func at(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: center.x + x * r, y: center.y + y * r) }
    let look = features(of: mood)
    let ink = GraphicsContext.Shading.color(palette.ink)
    let pen = StrokeStyle(lineWidth: 0.1 * r, lineCap: .round, lineJoin: .round)

    // A second disc behind gives the face a thick edge, down and to the right.
    context.fill(circle(at(0.07, 0.09), r), with: .color(palette.side))
    context.fill(circle(center, r), with: .color(palette.fill))
    if look.cheeks, let cheek = palette.cheek {
        context.fill(circle(at(-0.6, 0.2), 0.14 * r), with: .color(cheek))
        context.fill(circle(at(0.6, 0.2), 0.14 * r), with: .color(cheek))
    }

    for (eye, x) in [(look.left, -0.34), (look.right, 0.34)] {
        let y = -0.14
        var line = Path()
        switch eye {
        case .dot:
            context.fill(circle(at(x, y), 0.11 * r), with: ink)
        case .up:
            line.move(to: at(x - 0.15, y + 0.06))
            line.addQuadCurve(to: at(x + 0.15, y + 0.06), control: at(x, y - 0.2))
        case .down:
            line.move(to: at(x - 0.15, y - 0.04))
            line.addQuadCurve(to: at(x + 0.15, y - 0.04), control: at(x, y + 0.2))
        case .line:
            line.move(to: at(x - 0.14, y))
            line.addLine(to: at(x + 0.14, y))
        }
        context.stroke(line, with: ink, style: pen)
    }

    var mouth = Path()
    switch look.mouth {
    case .smile:
        mouth.move(to: at(-0.28, 0.26))
        mouth.addQuadCurve(to: at(0.28, 0.26), control: at(0, 0.6))
    case .grin:
        mouth.move(to: at(-0.34, 0.24))
        mouth.addLine(to: at(0.34, 0.24))
        mouth.addCurve(to: at(-0.34, 0.24), control1: at(0.32, 0.7), control2: at(-0.32, 0.7))
        mouth.closeSubpath()
        context.fill(mouth, with: ink)
    case .round:
        context.fill(Path(ellipseIn: CGRect(x: center.x - 0.14 * r, y: center.y + 0.22 * r, width: 0.28 * r, height: 0.36 * r)), with: ink)
        return
    case .slant:
        mouth.move(to: at(-0.22, 0.46))
        mouth.addLine(to: at(0.22, 0.34))
    case .frown:
        mouth.move(to: at(-0.26, 0.5))
        mouth.addQuadCurve(to: at(0.26, 0.5), control: at(0, 0.22))
    case .flat:
        mouth.move(to: at(-0.2, 0.4))
        mouth.addLine(to: at(0.2, 0.4))
    }
    context.stroke(mouth, with: ink, style: pen)
}

#Preview {
    ZStack {
        Color(hex: 0xe9eef2).ignoresSafeArea()
        GooeyRadialPicker(autoPlay: true)
    }
}
