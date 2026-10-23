import SwiftUI

/// A walking-distance question answered on a round map. A circle of full colour sits on a washed-out
/// street map; a handle on its right edge is pulled outward to give more minutes and pushed inward
/// to give fewer. The map zooms with the circle, so the circle always covers the ground the minutes
/// reach, and the number in the middle rolls from minute to minute. Tapping the map moves the edge
/// there. An invented street grid is drawn in code: there are no map tiles.
struct WalkRadiusDial: View {
    /// The fewest and the most minutes; the circle is smallest and largest at these.
    var range: ClosedRange<Int> = 4...18
    /// The minutes shown at first.
    var initial = 6
    var question = "How far are you willing\nto walk?"
    var unit = "MIN"
    var nextLabel = "Next"
    /// Pulls the handle out and pushes it back by itself, in a loop.
    var autoPlay = false
    /// Called with each minute the handle passes, and with the one it is released on.
    var onChange: ((Int) -> Void)? = nil
    /// Called with the chosen minutes when the button is tapped.
    var onNext: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0
    /// Counts the minutes a finger passes, for the haptic.
    @State private var ticks = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        var ready = false
        /// The circle's radius in points. The minutes, the number and the map's zoom all follow it.
        var radius = WalkRadiusDialTrack(Metrics.minRadius)
        /// 0 the handle at rest … 1 held.
        var press = WalkRadiusDialTrack(0)
        /// The ring a released handle sends out, 0…1.
        var ripple = WalkRadiusDialTrack(1)
        var nextPress = WalkRadiusDialTrack(0)
        var minutes = 0
        var dragging = false
        /// The radius when the drag began.
        var grabbed = 0.0
        /// The speed the script's drag ends with, handed to the spring on release.
        var carry = 0.0
    }

    private var auto: Bool { autoPlay && !reduceMotion }
    private var start: Int { min(max(initial, range.lowerBound), range.upperBound) }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let _ = prepare()
            let radius = motion.radius.value(at: now)
            let press = motion.press.value(at: now)
            let ripple = motion.ripple.value(at: now)
            let center = CGPoint(x: Metrics.width / 2, y: Metrics.dialY)
            let edge = CGPoint(x: center.x + radius, y: center.y)

            ZStack {
                Text(question)
                    .font(.system(size: 24))
                    .foregroundStyle(Color(hex: 0x1e1e1e))
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                    .lineLimit(2)
                    .frame(width: Metrics.width, height: Metrics.titleHeight)
                    .position(x: Metrics.width / 2, y: Metrics.titleHeight / 2)

                Canvas { context, size in
                    drawWalkRadiusDial(context, center: CGPoint(x: size.width / 2, y: size.height / 2), radius: radius, zoom: zoom(at: radius))
                }
                .frame(width: Metrics.dial * 2, height: Metrics.dial * 2)
                .contentShape(Circle())
                .gesture(
                    SpatialTapGesture().onEnded { tap in
                        let distance = hypot(tap.location.x - Metrics.dial, tap.location.y - Metrics.dial)
                        guard distance > Metrics.badge / 2 else { return }
                        touch()
                        set(snapped(distance))
                    }
                )
                .position(center)

                badge(value: min(max(minutes(at: radius), Double(range.lowerBound)), Double(range.upperBound)), press: press)
                    .position(center)
                    .allowsHitTesting(false)

                Circle()
                    .fill(RadialGradient(colors: [Metrics.blue.opacity(0.45), Metrics.blue.opacity(0)], center: .center, startRadius: 12, endRadius: Metrics.halo))
                    .frame(width: Metrics.halo * 2, height: Metrics.halo * 2)
                    .opacity(press)
                    .position(edge)
                    .allowsHitTesting(false)
                Circle()
                    .stroke(Metrics.blue, lineWidth: 2)
                    .frame(width: 2 * mix(18, Metrics.halo, ripple), height: 2 * mix(18, Metrics.halo, ripple))
                    .opacity(0.5 * (1 - ripple))
                    .position(edge)
                    .allowsHitTesting(false)

                handle(press: press)
                    .position(edge)

                Text(nextLabel)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: Metrics.width, height: Metrics.buttonHeight)
                    .background(RoundedRectangle(cornerRadius: 6, style: .circular).fill(Color(hex: 0x1c1c1c)))
                    .contentShape(Rectangle())
                    .onTapGesture {
                        touch()
                        motion.nextPress.retarget(1, .pulse)
                        onNext?(motion.minutes)
                    }
                    .scaleEffect(1 - 0.03 * motion.nextPress.value(at: now))
                    .position(x: Metrics.width / 2, y: Metrics.height - Metrics.buttonHeight / 2)
                    .accessibilityAddTraits(.isButton)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .sensoryFeedback(.selection, trigger: ticks)
        .sensoryFeedback(.impact(weight: .light), trigger: touches)
        .onChange(of: range) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    /// The black disc in the middle. The number is a drum: half-way between two minutes the lower one
    /// has slid up and out while the higher one arrives from below.
    private func badge(value: Double, press: Double) -> some View {
        let lower = value.rounded(.down)
        let turn = clamp01((value - lower - (0.5 - Metrics.rollWindow)) / (2 * Metrics.rollWindow))
        return ZStack {
            Circle().fill(Color(hex: 0x1c1c1c))
            ZStack {
                Text("\(Int(lower))")
                    .offset(y: -turn * Metrics.rollTravel)
                    .opacity(1 - turn)
                Text("\(Int(lower) + 1)")
                    .offset(y: (1 - turn) * Metrics.rollTravel)
                    .opacity(turn)
            }
            .font(.system(size: 20, weight: .bold))
            .monospacedDigit()
            .scaleEffect(1 + 0.3 * press)
            .offset(y: -7 - 2 * press)
            Text(unit)
                .font(.system(size: 10, weight: .semibold))
                .tracking(1.6)
                .opacity(0.9)
                .offset(y: 13)
        }
        .foregroundStyle(.white)
        .lineLimit(1)
        .frame(width: Metrics.badge, height: Metrics.badge)
        .clipShape(Circle())
    }

    private func handle(press: Double) -> some View {
        Canvas { context, size in
            let ink = walkRadiusDialMix(Color(hex: 0x3ec6c0), .white, press)
            for side in [-1.0, 1.0] {
                var chevron = Path()
                chevron.move(to: CGPoint(x: size.width / 2 + side * 5, y: size.height / 2 - 4.5))
                chevron.addLine(to: CGPoint(x: size.width / 2 + side * 9.5, y: size.height / 2))
                chevron.addLine(to: CGPoint(x: size.width / 2 + side * 5, y: size.height / 2 + 4.5))
                context.stroke(chevron, with: .color(ink), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
            }
        }
        .frame(width: Metrics.handleWidth, height: Metrics.handleHeight)
        .background(Capsule().fill(walkRadiusDialMix(Color(hex: 0x1f2a2e), Metrics.blue, press)))
        .scaleEffect(1 + 0.06 * press)
        // A larger target than the capsule, so it is easy to catch.
        .frame(width: 64, height: 64)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .global)
                .onChanged { drag in
                    if !motion.dragging {
                        motion.dragging = true
                        motion.grabbed = motion.radius.value(at: .now)
                        hold(true)
                        touch()
                    }
                    let radius = rubber(motion.grabbed + drag.translation.width)
                    motion.radius.retarget(radius, .jump)
                    pass(snapped(radius), tick: true)
                }
                .onEnded { drag in
                    motion.dragging = false
                    letGo(speed: min(max(drag.velocity.width, -Metrics.maxFling), Metrics.maxFling))
                    touch()
                }
        )
        .accessibilityElement()
        .accessibilityLabel("Walking time")
        .accessibilityValue("\(motion.minutes) minutes")
        .accessibilityAdjustableAction { direction in
            touch()
            set(min(max(motion.minutes + (direction == .increment ? 1 : -1), range.lowerBound), range.upperBound))
        }
    }

    // MARK: Minutes and radius

    private func radius(for minutes: Int) -> Double {
        let share = Double(minutes - range.lowerBound) / Double(max(1, range.upperBound - range.lowerBound))
        return mix(Metrics.minRadius, Metrics.maxRadius, share)
    }

    private func minutes(at radius: Double) -> Double {
        let share = (radius - Metrics.minRadius) / (Metrics.maxRadius - Metrics.minRadius)
        return Double(range.lowerBound) + share * Double(range.upperBound - range.lowerBound)
    }

    private func snapped(_ radius: Double) -> Int {
        min(max(Int(minutes(at: radius).rounded()), range.lowerBound), range.upperBound)
    }

    /// The map's scale. The ground covered grows with the minutes, so the scale is the circle's radius
    /// over the minutes: 1 at the most minutes, where the whole map shows.
    private func zoom(at radius: Double) -> Double {
        let minutes = max(minutes(at: radius), max(1, Double(range.lowerBound) * 0.75))
        return (radius / minutes) / (Metrics.maxRadius / Double(max(1, range.upperBound)))
    }

    /// Past either end the handle follows the finger at a third of its speed, for a few points.
    private func rubber(_ radius: Double) -> Double {
        if radius > Metrics.maxRadius { return Metrics.maxRadius + min(Metrics.give, (radius - Metrics.maxRadius) / 3) }
        if radius < Metrics.minRadius { return Metrics.minRadius - min(Metrics.give, (Metrics.minRadius - radius) / 3) }
        return radius
    }

    // MARK: The dial's steps

    /// Sets the first values before the first frame is drawn.
    private func prepare() {
        guard !motion.ready else { return }
        motion.ready = true
        reset()
    }

    private func reset() {
        motion.minutes = start
        motion.radius = WalkRadiusDialTrack(radius(for: start))
        motion.press = WalkRadiusDialTrack(0)
        motion.ripple = WalkRadiusDialTrack(1)
        motion.dragging = false
    }

    private func touch() {
        touches += 1
    }

    private func pass(_ minutes: Int, tick: Bool) {
        guard minutes != motion.minutes else { return }
        motion.minutes = minutes
        if tick { ticks += 1 }
        onChange?(minutes)
    }

    /// The handle is held (blue, with a halo) or let go of.
    private func hold(_ held: Bool) {
        motion.press.retarget(held ? 1 : 0, reduceMotion ? .jump : .tween(Metrics.pressTime, Ease.out))
    }

    /// Releases the handle: the circle springs to the nearest minute, carrying `speed` points a second.
    private func letGo(speed: Double) {
        let minutes = snapped(motion.radius.value(at: .now))
        pass(minutes, tick: false)
        motion.radius.retarget(radius(for: minutes), reduceMotion ? .jump : .spring(releaseSpring), speed: speed)
        hold(false)
        if !reduceMotion {
            motion.ripple = WalkRadiusDialTrack(0)
            motion.ripple.retarget(1, .tween(Metrics.rippleTime, Ease.out))
        }
    }

    /// Moves the circle's edge to `minutes` without a drag.
    private func set(_ minutes: Int) {
        pass(minutes, tick: true)
        motion.radius.retarget(radius(for: minutes), reduceMotion ? .jump : .spring(releaseSpring))
    }

    /// The script's drag: the handle travels to `minutes` in `time` seconds and is still moving when it gets there.
    private func drag(to minutes: Int, time: Double) {
        let target = radius(for: minutes)
        motion.carry = Metrics.dragEndSpeed * (target - motion.radius.value(at: .now)) / time
        motion.radius.retarget(target, .tween(time, dragCurve))
    }

    /// The script a finger would follow: out to the most minutes, in to the fewest, back to the start.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to the first value, and start over once it has settled.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                guard !motion.dragging else { return }
                set(start)
                try await Task.sleep(for: .seconds(Metrics.settle))
            }
            let clock = ContinuousClock()
            var begin = clock.now
            let stops = [range.upperBound, range.lowerBound, start]
            while true {
                for (step, minutes) in stops.enumerated() {
                    let at = Metrics.grabAt[step]
                    try await clock.sleep(until: begin + .seconds(at))
                    hold(true)
                    try await clock.sleep(until: begin + .seconds(at + Metrics.pressTime))
                    // The drag ends on time even when it starts late.
                    let release = begin + .seconds(at + Metrics.pressTime + Metrics.dragTime[step])
                    let left = (release - clock.now).components
                    drag(to: minutes, time: max(0.2, Double(left.seconds) + Double(left.attoseconds) * 1e-18))
                    try await clock.sleep(until: release)
                    letGo(speed: motion.carry)
                }
                begin += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

// MARK: The map

/// The invented neighbourhood, in map units with the dial's centre at the origin: at zoom 1 the dial
/// shows 150 units to each side. It is drawn tilted, as a grid of avenues and streets with one
/// boulevard across it, a few parks, a route and some pins.
private enum Map {
    static let tilt = -24.0
    static let land = Color(hex: 0xf1efe9)
    static let park = Color(hex: 0xa8d08d)
    static let plaza = Color(hex: 0xf3e7b8)
    static let route = Color(hex: 0xf29e4c)
    static let transit = Color(hex: 0x3b82f6)
    static let food = Color(hex: 0xf08a3c)
    static let veil = Color(hex: 0xaeb6c2, opacity: 0.65)

    /// Streets run across every 16 units (every fourth is a wide one), avenues up and down every 30.
    static let streets: (narrow: Path, wide: Path, avenues: Path, boulevard: Path) = {
        var narrow = Path()
        var wide = Path()
        for k in 0..<22 {
            let y = -168.0 + 16.0 * Double(k)
            if k % 4 == 1 {
                wide.move(to: CGPoint(x: -190, y: y))
                wide.addLine(to: CGPoint(x: 190, y: y))
            } else {
                narrow.move(to: CGPoint(x: -190, y: y))
                narrow.addLine(to: CGPoint(x: 190, y: y))
            }
        }
        var avenues = Path()
        for k in 0..<12 {
            let x = -165.0 + 30.0 * Double(k)
            avenues.move(to: CGPoint(x: x, y: -190))
            avenues.addLine(to: CGPoint(x: x, y: 190))
        }
        var boulevard = Path()
        boulevard.move(to: CGPoint(x: -190, y: 62))
        boulevard.addLine(to: CGPoint(x: 190, y: -98))
        return (narrow, wide, avenues, boulevard)
    }()

    /// Blocks turned into parks, and some into plazas.
    static let parks = [
        CGRect(x: -42.5, y: 10, width: 25, height: 28),
        CGRect(x: 77.5, y: -38, width: 25, height: 28),
        CGRect(x: -102.5, y: -70, width: 25, height: 28),
        CGRect(x: 17.5, y: 58, width: 55, height: 28),
        CGRect(x: -162.5, y: 90, width: 25, height: 28),
        CGRect(x: 107.5, y: -134, width: 25, height: 28),
    ]
    static let plazas = [
        CGRect(x: 17.5, y: -86, width: 25, height: 12),
        CGRect(x: -72.5, y: 74, width: 25, height: 12),
        CGRect(x: 107.5, y: 26, width: 25, height: 12),
    ]

    static let routePath: Path = {
        var path = Path()
        path.addLines([
            CGPoint(x: -190, y: 72), CGPoint(x: -45, y: 72), CGPoint(x: -45, y: 24), CGPoint(x: 15, y: 24),
            CGPoint(x: 15, y: -8), CGPoint(x: 75, y: -8), CGPoint(x: 75, y: -72), CGPoint(x: 190, y: -72),
        ])
        return path
    }()

    static let transitPins = [
        CGPoint(x: -15, y: -24), CGPoint(x: 45, y: 40), CGPoint(x: -75, y: 56), CGPoint(x: 75, y: -56),
        CGPoint(x: 15, y: -88), CGPoint(x: -45, y: -72), CGPoint(x: 135, y: 8), CGPoint(x: -135, y: -8),
    ]
    static let foodPins = [
        CGPoint(x: -30, y: -16), CGPoint(x: 30, y: 16), CGPoint(x: 60, y: 48), CGPoint(x: -60, y: -48), CGPoint(x: 90, y: 16),
        CGPoint(x: 0, y: 80), CGPoint(x: 30, y: -48), CGPoint(x: -120, y: 32), CGPoint(x: 120, y: -88), CGPoint(x: -90, y: 96),
    ]
}

/// Draws the dial: the whole map under a blue-grey veil, the same map in full colour inside the
/// circle of `radius`, and the circle's white edge.
private func drawWalkRadiusDial(_ context: GraphicsContext, center: CGPoint, radius: Double, zoom: Double) {
    func disc(_ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2))
    }

    var outer = context
    outer.clip(to: disc(Metrics.dial))
    drawWalkRadiusDialMap(outer, center: center, zoom: zoom)
    outer.fill(disc(Metrics.dial), with: .color(Map.veil))

    var inner = context
    inner.clip(to: disc(radius))
    drawWalkRadiusDialMap(inner, center: center, zoom: zoom)

    context.stroke(disc(radius), with: .color(.white), lineWidth: 3)
}

private func drawWalkRadiusDialMap(_ base: GraphicsContext, center: CGPoint, zoom: Double) {
    var context = base
    context.translateBy(x: center.x, y: center.y)
    context.scaleBy(x: zoom, y: zoom)
    context.rotate(by: .degrees(Map.tilt))

    context.fill(Path(CGRect(x: -260, y: -260, width: 520, height: 520)), with: .color(Map.land))
    context.stroke(Map.streets.narrow, with: .color(.white), lineWidth: 1.8)
    context.stroke(Map.streets.wide, with: .color(.white), lineWidth: 3.4)
    context.stroke(Map.streets.avenues, with: .color(.white), lineWidth: 3)
    for block in Map.parks {
        context.fill(Path(roundedRect: block, cornerRadius: 2.5), with: .color(Map.park))
    }
    for block in Map.plazas {
        context.fill(Path(roundedRect: block, cornerRadius: 2.5), with: .color(Map.plaza))
    }
    context.stroke(Map.streets.boulevard, with: .color(.white), lineWidth: 5)
    context.stroke(Map.routePath, with: .color(Map.route), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    for pin in Map.transitPins {
        context.fill(Path(ellipseIn: CGRect(x: pin.x - 2.6, y: pin.y - 2.6, width: 5.2, height: 5.2)), with: .color(Map.transit))
        context.fill(Path(ellipseIn: CGRect(x: pin.x - 1, y: pin.y - 1, width: 2, height: 2)), with: .color(.white))
    }
    for pin in Map.foodPins {
        context.fill(Path(ellipseIn: CGRect(x: pin.x - 2.1, y: pin.y - 2.1, width: 4.2, height: 4.2)), with: .color(Map.food))
    }
}

/// A number heading for a target, sampled against the `TimelineView` clock.
private struct WalkRadiusDialTrack {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
        /// Up to the target in 80 ms, a 40 ms hold, back to 0 in 180 ms: a tap.
        case pulse
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

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return from }
        switch kind {
        case .jump: return to
        case .tween(let duration, let curve): return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
        case .spring(let spring): return from + spring.value(target: to - from, initialVelocity: speed, time: t)
        case .pulse: return to * (t < 0.08 ? t / 0.08 : t < 0.12 ? 1 : max(0, 1 - (t - 0.12) / 0.18))
        }
    }

    /// Heads for `target` from where it is now. A spring starts at `speed` units a second.
    mutating func retarget(_ target: Double, _ kind: Kind, speed: Double = 0) {
        let now = Date.now
        from = value(at: now)
        if case .pulse = kind { from = 0 }
        to = target
        self.speed = speed
        start = now
        self.kind = kind
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 476.0
    static let titleHeight = 68.0
    static let buttonHeight = 52.0

    /// The dial's radius and the height of its centre.
    static let dial = 150.0
    static let dialY = 246.0
    /// The circle's radius at the fewest and at the most minutes.
    static let minRadius = 72.0
    static let maxRadius = 145.5
    /// How far past either end a finger can pull the handle.
    static let give = 4.5
    static let badge = 66.0
    static let handleWidth = 42.0
    static let handleHeight = 30.0
    static let halo = 36.0
    static let blue = Color(hex: 0x2e8fd0)

    /// The number rolls over this share of a minute to each side of the half-way point, by this many points.
    static let rollWindow = 0.25
    static let rollTravel = 18.0

    static let pressTime = 0.15
    static let rippleTime = 0.45
    /// The fastest release the spring is given, in points a second.
    static let maxFling = 600.0
    /// The script's drag ends at this multiple of its average speed (the slope at the end of `dragCurve`).
    static let dragEndSpeed = 1.25

    // autoPlay (seconds): the handle is held at `grabAt`, and dragged for `dragTime` 150 ms later.
    static let grabAt = [0.9, 3.15, 5.5]
    static let dragTime = [1.2, 1.3, 0.5]
    static let loop = 8.0
    /// After a touch the loop waits this long, returns to the first value, and starts over once it has settled.
    static let resumeAfter = 2.0
    static let settle = 0.9
}

private let releaseSpring = Spring(mass: 1, stiffness: 180, damping: 12)
private let dragCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.8, y: 0.75))

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func walkRadiusDialMix(_ a: Color, _ b: Color, _ t: Double) -> Color {
    let from = a.resolve(in: EnvironmentValues())
    let to = b.resolve(in: EnvironmentValues())
    let k = Float(clamp01(t))
    return Color(
        .sRGB,
        red: Double(from.red + (to.red - from.red) * k),
        green: Double(from.green + (to.green - from.green) * k),
        blue: Double(from.blue + (to.blue - from.blue) * k),
        opacity: 1
    )
}

#Preview {
    ZStack {
        Color.white.ignoresSafeArea()
        WalkRadiusDial(autoPlay: true)
    }
}
