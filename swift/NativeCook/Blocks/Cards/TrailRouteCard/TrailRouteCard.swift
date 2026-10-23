import SwiftUI

/// A trail card: a mountain photo with the trail's name and a frosted "Directions" pill on its dark
/// lower band, then the difficulty, three figures and a small drawing of the route. Tapping the
/// pill walks the trail: the route draws itself with a dot travelling along it, the figures count
/// up in step, an elevation profile rises behind them, the photo slowly zooms and a flag pops at
/// the end, before the route fades back to its faint outline.
struct TrailRouteCard: View {
    /// Walks the trail by itself every 8 s.
    var autoPlay = false
    var title = "Embercrest Ridge"
    var range = "Silverpine Mountains"
    /// Address of a landscape photo, about 4 : 3.
    var photo = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=636&h=480&q=80"
    /// Flat colour shown until the photo arrives.
    var photoTint = Color(hex: 0x656778)
    var buttonLabel = "Directions"
    var difficulty = "Hard"
    /// How many of the three difficulty bars are lit, 0…3.
    var level = 3
    var note = "First climbed in 1886"
    var distanceKm = 12.4
    var distanceLabel = "Distance"
    /// Metres climbed over the whole trail.
    var elevationM = 870
    var elevationLabel = "Elevation"
    var durationMin = 285
    var durationLabel = "Duration"
    /// Colour of the lit bars, the elevation profile and the flag.
    var accent = Color(hex: 0xe0672f)
    var onDirections: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = Simulation()
    /// Keeps the timeline running for a walk started by hand without autoPlay.
    @State private var awake = false
    @State private var taps = 0
    @State private var arrivals = 0

    private final class Simulation {
        var last: Date? = nil
        var clock = 0.0
        var looping = false
        /// Seconds into the walk, or nil at rest.
        var run: Double? = nil
        /// The loop's own finger is on the pill.
        var scripted = false
        /// 0…1: how far the loop's finger has pressed the pill in.
        var press = 0.0
        /// A touch started this walk: the loop waits for it to end, then `hold` more seconds.
        var touched = false
        var hold = 0.0

        func advance(to date: Date) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            if let run {
                self.run = run + dt < Metrics.runTime ? run + dt : nil
            }
            if touched {
                if run == nil {
                    hold -= dt
                    if hold <= 0 {
                        touched = false
                        clock = 0
                    }
                }
            } else if looping {
                clock += dt
                if clock >= Metrics.loop { clock -= Metrics.loop }
                let down = clock >= Metrics.pressAt && clock < Metrics.pressAt + Metrics.pressTime
                // The loop's finger lifts: the walk starts, as it does after a tap.
                if scripted, !down, run == nil { run = max(0, clock - Metrics.pressAt - Metrics.pressTime) }
                scripted = down
            }
            press += ((scripted ? 1 : 0) - press) * (1 - exp(-dt * Metrics.pressRate))
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: !(auto || awake))) { timeline in
            let _ = sim.advance(to: timeline.date)
            let state = TrailRouteCardState(run: sim.run, reduced: reduceMotion)
            let distance = distanceKm * state.share
            let climbed = Int((Double(elevationM) * climbShare(state.share)).rounded())
            let minutes = Int((Double(durationMin) * state.share).rounded())

            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: Metrics.radius, style: .circular)
                    .fill(.white)
                    .shadow(color: .black.opacity(0.1), radius: 30, y: 22)
                    .shadow(color: .black.opacity(0.04), radius: 4, y: 2)

                photoBlock(zoom: state.zoom)
                    .offset(x: Metrics.photoInset, y: Metrics.photoInset)

                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                        .font(.system(size: 13.5, weight: .medium))
                        .foregroundStyle(.white)
                        .frame(height: 18)
                    Text(range)
                        .font(.system(size: 10.5))
                        .foregroundStyle(.white.opacity(0.6))
                        .frame(height: 14)
                }
                .lineLimit(1)
                .frame(width: Metrics.pillLeft - Metrics.titleLeft - 8, alignment: .leading)
                .offset(x: Metrics.titleLeft, y: Metrics.titleTop)

                Button {
                    press()
                } label: {
                    Text(buttonLabel)
                        .font(.system(size: 14, weight: .medium))
                        .tracking(0.4)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .frame(width: Metrics.pillWidth, height: Metrics.pillHeight)
                        .background(RoundedRectangle(cornerRadius: Metrics.pillRadius, style: .circular).fill(.white.opacity(0.22)))
                        .overlay(RoundedRectangle(cornerRadius: Metrics.pillRadius, style: .circular).strokeBorder(.white.opacity(0.12), lineWidth: 1))
                }
                .buttonStyle(PressStyle(scale: Metrics.pressScale))
                .scaleEffect(1 - (1 - Metrics.pressScale) * sim.press)
                .offset(x: Metrics.pillLeft, y: Metrics.pillTop)

                HStack(alignment: .bottom, spacing: Metrics.barsGap) {
                    Text(difficulty)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Metrics.ink)
                        .lineLimit(1)
                        .frame(height: 18)
                    HStack(alignment: .bottom, spacing: Metrics.barSpacing) {
                        ForEach(0..<3, id: \.self) { bar in
                            let lit = bar < level ? state.barLight(bar) : 0
                            ZStack {
                                RoundedRectangle(cornerRadius: 1, style: .circular).fill(.black.opacity(0.12))
                                RoundedRectangle(cornerRadius: 1, style: .circular).fill(accent).opacity(lit)
                            }
                            .frame(width: Metrics.barWidth, height: Metrics.barHeights[bar])
                            .scaleEffect(y: 1 + Metrics.barBump * sin(.pi * lit), anchor: .bottom)
                        }
                    }
                    .padding(.bottom, 4)
                }
                .offset(x: Metrics.left, y: Metrics.difficultyTop)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: difficulty))

                Text(note)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Metrics.mute)
                    .lineLimit(1)
                    .frame(width: Metrics.statsWidth, height: 14, alignment: .leading)
                    .offset(x: Metrics.left, y: Metrics.noteTop)

                Rectangle()
                    .fill(Color(hex: 0xe6e6e6))
                    .frame(width: Metrics.statsWidth, height: 1)
                    .offset(x: Metrics.left, y: Metrics.ruleTop)

                // The climb so far, drawn behind the figures.
                Canvas { context, _ in
                    drawProfile(context, state: state)
                }
                .frame(width: Metrics.statsWidth, height: Metrics.profileHeight + 2)
                .offset(x: Metrics.left, y: Metrics.profileBase - Metrics.profileHeight - 2)
                .allowsHitTesting(false)

                ForEach(0..<3, id: \.self) { column in
                    VStack(alignment: .leading, spacing: 1) {
                        Text([distanceText(distance), "\(climbed)m", durationText(minutes)][column])
                            .font(.system(size: 14, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(Metrics.ink)
                            .frame(height: 18)
                        Text([distanceLabel, elevationLabel, durationLabel][column])
                            .font(.system(size: 9.5))
                            .foregroundStyle(Metrics.mute)
                            .frame(height: 12)
                    }
                    .lineLimit(1)
                    .fixedSize()
                    .opacity(state.statsOpacity)
                    .offset(x: Metrics.statLefts[column], y: Metrics.statsTop)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: "\(distanceText(distanceKm)), \(elevationM)m, \(durationText(durationMin))"))

                Canvas { context, _ in
                    drawRoute(context, state: state)
                }
                .frame(width: Metrics.mapWidth, height: Metrics.mapHeight)
                .background(RoundedRectangle(cornerRadius: Metrics.mapRadius, style: .circular).fill(Color(hex: 0xf3f3f3)))
                .offset(x: Metrics.mapLeft, y: Metrics.mapTop)
                .accessibilityHidden(true)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        }
        .sensoryFeedback(.success, trigger: arrivals)
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .task(id: taps) {
            guard taps > 0 else { return }
            // The flag of a walk started by hand lands with a haptic.
            try? await Task.sleep(for: .seconds(reduceMotion ? 0 : Metrics.flagAt))
            arrivals += 1
            try? await Task.sleep(for: .seconds(Metrics.runTime))
            awake = false
        }
    }

    private func photoBlock(zoom: Double) -> some View {
        ZStack {
            photoTint
            AsyncImage(url: URL(string: photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                } else {
                    Color.clear
                }
            }
            .frame(width: Metrics.photoWidth, height: Metrics.photoHeight)
            .scaleEffect(zoom)
            // The band the title and the pill sit on.
            LinearGradient(
                stops: [
                    .init(color: Color.rgba(18, 15, 12, 0), location: 0.42),
                    .init(color: Color.rgba(18, 15, 12, 0.6), location: 0.68),
                    .init(color: Color.rgba(18, 15, 12, 0.94), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
        }
        .frame(width: Metrics.photoWidth, height: Metrics.photoHeight)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.photoRadius, style: .circular))
        .accessibilityHidden(true)
    }

    private func sync() {
        sim.looping = auto
        sim.clock = 0
        sim.scripted = false
        sim.touched = false
        if auto { sim.run = nil }
    }

    /// A tap on the pill: the walk starts unless one is under way, and the loop waits for it.
    private func press() {
        onDirections?()
        guard sim.run == nil else { return }
        sim.run = 0
        sim.touched = true
        sim.hold = Metrics.userHold
        awake = true
        taps += 1
    }

    private func drawProfile(_ context: GraphicsContext, state: TrailRouteCardState) {
        guard state.ink > 0 else { return }
        let width = Metrics.statsWidth
        let base = Metrics.profileHeight + 2
        let points = Metrics.profile.enumerated().map { index, height in
            CGPoint(x: width * Double(index) / Double(Metrics.profile.count - 1), y: base - Metrics.profileHeight * height * state.rise)
        }
        var line = Path()
        line.move(to: points[0])
        for index in 1..<points.count - 1 {
            let next = points[index + 1]
            line.addQuadCurve(to: CGPoint(x: (points[index].x + next.x) / 2, y: (points[index].y + next.y) / 2), control: points[index])
        }
        line.addLine(to: points[points.count - 1])
        var area = line
        area.addLine(to: CGPoint(x: width, y: base))
        area.addLine(to: CGPoint(x: 0, y: base))
        area.closeSubpath()

        // Only the part already walked shows.
        var walked = context
        walked.clip(to: Path(CGRect(x: 0, y: 0, width: width * state.progress, height: base)))
        walked.opacity = state.ink
        walked.fill(
            area,
            with: .linearGradient(
                Gradient(colors: [accent.opacity(Metrics.profileTop), accent.opacity(Metrics.profileBottom)]),
                startPoint: CGPoint(x: 0, y: base - Metrics.profileHeight), endPoint: CGPoint(x: 0, y: base)
            )
        )
    }

    private func drawRoute(_ context: GraphicsContext, state: TrailRouteCardState) {
        let style = StrokeStyle(lineWidth: Metrics.routeStroke, lineCap: .round, lineJoin: .round)
        context.stroke(routeLine(to: 1).path, with: .color(.black.opacity(Metrics.routeFaint)), style: style)
        let walked = routeLine(to: state.progress)
        if state.ink > 0 {
            context.stroke(walked.path, with: .color(Metrics.ink.opacity(state.ink)), style: style)
        }

        if state.flag > 0 {
            let end = trailRoute[trailRoute.count - 1]
            var flag = context.scaled(state.flag, about: end)
            flag.opacity = min(1, state.flag)
            var pole = Path()
            pole.move(to: end)
            pole.addLine(to: CGPoint(x: end.x, y: end.y - Metrics.flagPole))
            flag.stroke(pole, with: .color(Metrics.ink), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            var cloth = Path()
            cloth.move(to: CGPoint(x: end.x, y: end.y - Metrics.flagPole))
            cloth.addLine(to: CGPoint(x: end.x + Metrics.flagWidth, y: end.y - Metrics.flagPole + Metrics.flagDrop / 2))
            cloth.addLine(to: CGPoint(x: end.x, y: end.y - Metrics.flagPole + Metrics.flagDrop))
            cloth.closeSubpath()
            flag.fill(cloth, with: .color(accent))
        }

        let dot = state.dotAtStart ? trailRoute[0] : walked.end
        var marker = context
        marker.opacity = state.dot
        marker.fill(Path(ellipseIn: CGRect(x: dot.x - Metrics.dotRing, y: dot.y - Metrics.dotRing, width: Metrics.dotRing * 2, height: Metrics.dotRing * 2)), with: .color(Color(hex: 0xf3f3f3)))
        marker.fill(Path(ellipseIn: CGRect(x: dot.x - Metrics.dotRadius, y: dot.y - Metrics.dotRadius, width: Metrics.dotRadius * 2, height: Metrics.dotRadius * 2)), with: .color(Metrics.ink))
    }

    private struct PressStyle: ButtonStyle {
        let scale: Double

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? scale : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

/// Everything the card draws, worked out from how far into the walk it is.
private struct TrailRouteCardState {
    /// 0…1 along the trail.
    var progress = 0.0
    /// Share of the figures shown: they stand at their full values at rest.
    var share = 1.0
    var statsOpacity = 1.0
    /// Opacity of what the walk draws: the black route and the elevation profile.
    var ink = 0.0
    /// 0…1: the profile's height as it rises from its baseline.
    var rise = 1.0
    var dot = 1.0
    var dotAtStart = true
    /// Scale of the flag, past 1 while it pops.
    var flag = 0.0
    var zoom = 1.0
    private var running = false

    init(run: Double?, reduced: Bool) {
        guard let run else { return }
        running = true
        // Under reduced motion nothing travels: the finished route fades in, then out.
        progress = reduced ? 1 : pace((run - Metrics.hikeAt) / Metrics.hikeTime)
        let reset = smooth((run - Metrics.resetAt) / Metrics.resetTime)
        share = progress
        statsOpacity = reduced ? 1 : Metrics.statsDip + (1 - Metrics.statsDip) * smooth(run / Metrics.statsFade)
        ink = smooth(run / Metrics.inkFade) * (1 - reset)
        rise = reduced ? 1 : easeOut(run / Metrics.riseTime)
        dot = abs(1 - 2 * reset)
        dotAtStart = reset >= 0.5
        flag = (reduced ? smooth(run / Metrics.inkFade) : pop(run - Metrics.flagAt)) * (1 - reset)
        zoom = reduced ? 1 : 1 + Metrics.zoom * progress * (1 - reset)
    }

    /// 0…1 for a difficulty bar: all lit at rest, lighting one by one along the walk.
    func barLight(_ bar: Int) -> Double {
        running ? clamp01((progress - Metrics.barAt[bar]) / Metrics.barFade) : 1
    }
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func smooth(_ t: Double) -> Double {
    let p = clamp01(t)
    return p * p * (3 - 2 * p)
}

private func easeOut(_ t: Double) -> Double {
    let p = 1 - clamp01(t)
    return 1 - p * p * p
}

/// The walk's pace, 0…1: a short run-up, a steady stride and a short slow-down.
private func pace(_ t: Double) -> Double {
    let p = clamp01(t)
    let ramp = Metrics.paceRamp
    if p < ramp { return p * p / (2 * ramp * (1 - ramp)) }
    if p > 1 - ramp { return 1 - (1 - p) * (1 - p) / (2 * ramp * (1 - ramp)) }
    return (p - ramp / 2) / (1 - ramp)
}

/// A spring let go at 0 and settling at 1, `t` seconds on: it overshoots by about a fifth.
private func pop(_ t: Double) -> Double {
    guard t > 0 else { return 0 }
    let decay = Metrics.flagDamping / (2 * Metrics.flagMass)
    let omega = (Metrics.flagStiffness / Metrics.flagMass - decay * decay).squareRoot()
    return 1 - exp(-decay * t) * (cos(omega * t) + decay / omega * sin(omega * t))
}

/// Share of the whole climb done at `progress` along the trail: only the way up counts.
private func climbShare(_ progress: Double) -> Double {
    let knots = Metrics.profile
    let at = clamp01(progress) * Double(knots.count - 1)
    var climbed = 0.0
    var total = 0.0
    for index in 0..<knots.count - 1 {
        let gain = max(0, knots[index + 1] - knots[index])
        total += gain
        climbed += gain * clamp01(at - Double(index))
    }
    return climbed / total
}

private func distanceText(_ km: Double) -> String {
    let tenths = Int((km * 10).rounded())
    return "\(tenths / 10).\(tenths % 10)km"
}

private func durationText(_ minutes: Int) -> String {
    "\(minutes / 60)h \(minutes % 60)m"
}

/// The route as a polyline: each curve of `Metrics.route` cut into short straight pieces.
private let trailRoute: [CGPoint] = {
    let c = Metrics.route
    var points = [CGPoint(x: c[0], y: c[1])]
    var index = 2
    while index + 5 < c.count {
        let from = points[points.count - 1]
        for step in 1...Metrics.routeSteps {
            let t = Double(step) / Double(Metrics.routeSteps)
            let u = 1 - t
            points.append(CGPoint(
                x: u * u * u * from.x + 3 * u * u * t * c[index] + 3 * u * t * t * c[index + 2] + t * t * t * c[index + 4],
                y: u * u * u * from.y + 3 * u * u * t * c[index + 1] + 3 * u * t * t * c[index + 3] + t * t * t * c[index + 5]
            ))
        }
        index += 6
    }
    return points
}()

/// Length of the route up to each of its points.
private let trailRouteLengths: [Double] = {
    var lengths = [0.0]
    for index in 1..<trailRoute.count {
        lengths.append(lengths[index - 1] + hypot(trailRoute[index].x - trailRoute[index - 1].x, trailRoute[index].y - trailRoute[index - 1].y))
    }
    return lengths
}()

/// The route from its start to `progress` of its length, and the point it reaches.
private func routeLine(to progress: Double) -> (path: Path, end: CGPoint) {
    let reach = clamp01(progress) * trailRouteLengths[trailRouteLengths.count - 1]
    var path = Path()
    var end = trailRoute[0]
    path.move(to: end)
    for index in 1..<trailRoute.count {
        if trailRouteLengths[index] <= reach {
            end = trailRoute[index]
        } else {
            let t = (reach - trailRouteLengths[index - 1]) / (trailRouteLengths[index] - trailRouteLengths[index - 1])
            end = CGPoint(
                x: trailRoute[index - 1].x + (trailRoute[index].x - trailRoute[index - 1].x) * t,
                y: trailRoute[index - 1].y + (trailRoute[index].y - trailRoute[index - 1].y) * t
            )
        }
        path.addLine(to: end)
        if trailRouteLengths[index] > reach { break }
    }
    return (path, end)
}

private enum Metrics {
    static let width = 340.0
    static let height = 368.0
    static let radius = 34.0
    static let ink = Color(hex: 0x151515)
    static let mute = Color(hex: 0x9a9a9a)

    static let photoInset = 11.0
    static let photoWidth = width - photoInset * 2
    static let photoHeight = 240.0
    static let photoRadius = 26.0
    static let titleLeft = 30.0
    static let titleTop = 194.0
    static let pillLeft = 168.0
    static let pillTop = 193.0
    static let pillWidth = 138.0
    static let pillHeight = 36.0
    static let pillRadius = 12.0
    static let pressScale = 0.96
    /// How fast the pill follows the loop's finger, per second.
    static let pressRate = 22.0

    static let left = 25.0
    static let difficultyTop = 270.0
    static let barsGap = 7.0
    static let barWidth = 3.5
    static let barSpacing = 2.5
    static let barHeights = [6.0, 9.0, 12.0]
    /// How much taller a bar gets for a moment as it lights.
    static let barBump = 0.3
    /// Progress along the trail at which each bar lights, and the share of it the fade takes.
    static let barAt = [0.06, 0.42, 0.78]
    static let barFade = 0.04
    static let noteTop = 290.0
    static let ruleTop = 311.0
    static let statsTop = 319.0
    static let statsWidth = 171.0
    static let statLefts = [25.0, 88.0, 146.0]

    /// Heights of the trail at even steps along it, 0…1.
    static let profile = [0.1, 0.22, 0.18, 0.4, 0.55, 0.48, 0.72, 0.9, 1, 0.7, 0.46, 0.3]
    static let profileBase = 352.0
    static let profileHeight = 38.0
    // A silhouette without an outline: a line through the labels would read as struck-out text.
    static let profileTop = 0.3
    static let profileBottom = 0.05

    static let mapLeft = 220.0
    static let mapTop = 269.0
    static let mapWidth = 104.0
    static let mapHeight = 81.0
    static let mapRadius = 26.0
    /// The route in the map's own points: its start, then three points for each cubic curve.
    static let route = [
        24.0, 54,
        16, 50, 16, 44, 22, 40,
        28, 34, 30, 24, 40, 26,
        48, 28, 48, 36, 56, 34,
        64, 32, 68, 26, 76, 30,
        84, 34, 88, 40, 84, 44,
        80, 50, 74, 58, 66, 56,
        58, 54, 52, 48, 44, 52,
    ]
    /// Straight pieces each curve is cut into.
    static let routeSteps = 16
    static let routeStroke = 1.8
    static let routeFaint = 0.14
    static let dotRadius = 3.2
    static let dotRing = 4.8
    static let flagPole = 13.0
    static let flagWidth = 8.0
    static let flagDrop = 6.0

    // A walk, in seconds from the tap: the figures drop to zero, the trail is walked, the flag pops,
    // and after a look at the result the route fades back to its outline.
    static let hikeAt = 0.1
    static let hikeTime = 4.0
    /// Share of the walk spent speeding up, and again slowing down.
    static let paceRamp = 0.15
    /// The flag goes up as the dot comes to a stop.
    static let flagAt = 3.8
    static let resetAt = 5.4
    static let resetTime = 0.7
    static let runTime = resetAt + resetTime
    static let inkFade = 0.25
    static let riseTime = 0.6
    /// The figures blink down to this opacity as they restart from zero.
    static let statsDip = 0.35
    static let statsFade = 0.3
    static let zoom = 0.08

    // The flag's pop: a spring from 0 to 1.
    static let flagMass = 1.0
    static let flagStiffness = 260.0
    static let flagDamping = 14.0

    // autoPlay loop (seconds): a finger presses the pill, lifts, and the walk plays out.
    static let pressAt = 0.8
    static let pressTime = 0.14
    static let loop = 8.0
    /// After a walk started by hand the loop waits this long before it restarts.
    static let userHold = 2.0
}

#Preview {
    ZStack {
        Color(hex: 0xf0f0f0).ignoresSafeArea()
        TrailRouteCard(autoPlay: true)
    }
}
