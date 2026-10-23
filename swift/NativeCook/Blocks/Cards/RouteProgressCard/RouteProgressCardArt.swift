import SwiftUI

/// A number moving towards a target on a curve or a spring, sampled against the `TimelineView` clock.
struct RouteProgressCardTrack {
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

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
    }

    /// Heads for `target` from wherever it will be `delay` seconds from now, when the move begins.
    mutating func retarget(_ target: Double, _ kind: Kind, delay: Double = 0) {
        let begin = Date.now.addingTimeInterval(delay)
        let value = value(at: begin)
        speed = velocity(at: begin)
        from = value
        if case .pulse = kind { from = 0 }
        to = target
        start = begin
        self.kind = kind
    }
}

/// The leg's icon, drawn in a 20 × 20 box.
struct RouteProgressCardIcon: View {
    let travel: RouteProgressCardTravel

    var body: some View {
        Canvas { context, _ in
            let ink = GraphicsContext.Shading.color(.white.opacity(0.9))
            switch travel {
            case .walk:
                context.fill(stepsIcon, with: ink)
            case .drive:
                context.stroke(carIcon, with: ink, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                context.fill(carLights, with: ink)
            case .fly:
                context.fill(planeIcon, with: ink)
            }
        }
    }
}

/// Everything drawn on an open card, in its 330 × 204 box: the glow, the progress ring, the outline of
/// Next Step, the bar beside the stops, and the leg's own drawing. `eased` is the marker's progress.
func drawRouteProgressCard(_ context: GraphicsContext, mode: RouteProgressCardMode, eased: Double, seconds: Double, nextPress: Double) {
    let accent = GraphicsContext.Shading.color(mode.accent)
    let along = mode.share * eased

    context.fillEllipse(
        center: CGPoint(x: 165, y: -14), radii: CGSize(width: 240, height: 138),
        gradient: Gradient(stops: [
            .init(color: mode.glow, location: 0),
            .init(color: mode.glow.opacity(0.55), location: 0.5),
            .init(color: mode.glow.opacity(0), location: 1),
        ])
    )

    // The ring fills clockwise from the top.
    context.stroke(disc(165, 28, 13), with: .color(.white.opacity(0.16)), lineWidth: 1.5)
    var arc = Path()
    arc.addArc(center: CGPoint(x: 165, y: 28), radius: 13, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * along), clockwise: false)
    context.stroke(arc, with: accent, style: StrokeStyle(lineWidth: 1.5, lineCap: .round))

    let next = Path(roundedRect: CGRect(x: 242, y: 16, width: 70, height: 24), cornerRadius: 12, style: .circular)
    context.fill(next, with: .color(.white.opacity(0.14 * nextPress)))
    context.stroke(next, with: .color(.white.opacity(0.35)), lineWidth: 1)
    context.fill(outline("M 298.5 24.5 L 304 28 L 298.5 31.5 Z"), with: .color(.white.opacity(0.9)))

    context.fill(Path(roundedRect: CGRect(x: 18, y: 155, width: 2.5, height: 31), cornerRadius: 1.25, style: .circular), with: accent)

    switch mode.travel {
    case .walk: drawTrack(context, mode: mode, along: along)
    case .drive: drawRoad(context, mode: mode, seconds: seconds)
    case .fly: drawArc(context, mode: mode, along: along)
    }
}

/// Walking: a thick track filled up to a glowing knob.
private func drawTrack(_ context: GraphicsContext, mode: RouteProgressCardMode, along: Double) {
    let knob = 27 + 276 * along
    context.fill(Path(roundedRect: CGRect(x: 18, y: 96, width: 294, height: 12), cornerRadius: 6, style: .circular), with: .color(.white.opacity(0.1)))
    context.fill(
        Path(roundedRect: CGRect(x: 18, y: 96, width: knob - 12, height: 12), cornerRadius: 6, style: .circular),
        with: .linearGradient(Gradient(colors: [mode.glow, mode.accent]), startPoint: CGPoint(x: 18, y: 0), endPoint: CGPoint(x: knob, y: 0))
    )
    var halo = context
    halo.addFilter(.blur(radius: 5))
    halo.fill(disc(knob, 102, 12), with: .color(mode.accent.opacity(0.9)))
    context.fill(disc(knob, 102, 9), with: .color(Color(hex: 0xece8ff)))
    context.stroke(disc(knob, 102, 9), with: .color(.white.opacity(0.7)), lineWidth: 1)
    context.fill(disc(knob, 102, 4), with: .color(.white.opacity(0.6)))
}

/// Driving: a road in perspective whose centre dashes run towards the viewer, with a pointer swaying in its lane.
private func drawRoad(_ context: GraphicsContext, mode: RouteProgressCardMode, seconds: Double) {
    let top = 92.0
    let bottom = 140.0
    func half(_ y: Double) -> Double { 5 + 71 * (y - top) / (bottom - top) }

    context.fill(
        outline("M 160 92 L 170 92 L 241 140 L 89 140 Z"),
        with: .linearGradient(Gradient(colors: [.white.opacity(0), .white.opacity(0.1)]), startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: bottom))
    )
    context.stroke(outline("M 160 92 L 89 140 M 170 92 L 241 140"), with: .color(.white.opacity(0.3)), lineWidth: 1)

    let phase = (seconds * 1.3).truncatingRemainder(dividingBy: 1)
    for k in 0..<5 {
        // Depth is squared, so the dashes spread and speed up as they come closer.
        let near = (Double(k) + phase) / 5
        let far = min(1, near + 0.09)
        let y0 = top + (bottom - top) * near * near
        let y1 = top + (bottom - top) * far * far
        var dash = Path()
        dash.move(to: CGPoint(x: 165 - half(y0) * 0.06, y: y0))
        dash.addLine(to: CGPoint(x: 165 + half(y0) * 0.06, y: y0))
        dash.addLine(to: CGPoint(x: 165 + half(y1) * 0.06, y: y1))
        dash.addLine(to: CGPoint(x: 165 - half(y1) * 0.06, y: y1))
        dash.closeSubpath()
        context.fill(dash, with: .color(.white.opacity(0.25 + 0.6 * near)))
    }

    var pointer = context
    pointer.translateBy(x: 3 * sin(seconds * 1.7), y: 0)
    let arrow = outline("M 165 111 L 175.5 131 L 165 126.5 L 154.5 131 Z")
    var halo = pointer
    halo.addFilter(.blur(radius: 4))
    halo.fill(arrow, with: .color(mode.accent.opacity(0.9)))
    pointer.fill(arrow, with: .color(mode.accent))
}

/// Flying: an arc between two dots, drawn solid behind a glowing marker that travels along it.
private func drawArc(_ context: GraphicsContext, mode: RouteProgressCardMode, along: Double) {
    let from = CGPoint(x: 40, y: 132)
    let to = CGPoint(x: 290, y: 132)
    let control = CGPoint(x: 165, y: 56)
    // The first `along` of a quadratic curve is itself one: its control point slides along the first leg.
    let bend = CGPoint(x: mix(from.x, control.x, along), y: mix(from.y, control.y, along))
    let rest = CGPoint(x: mix(control.x, to.x, along), y: mix(control.y, to.y, along))
    let marker = CGPoint(x: mix(bend.x, rest.x, along), y: mix(bend.y, rest.y, along))

    var whole = Path()
    whole.move(to: from)
    whole.addQuadCurve(to: to, control: control)
    context.stroke(whole, with: .color(.white.opacity(0.25)), style: StrokeStyle(lineWidth: 1.2, dash: [3, 4]))
    var flown = Path()
    flown.move(to: from)
    flown.addQuadCurve(to: marker, control: bend)
    context.stroke(flown, with: .color(mode.accent), style: StrokeStyle(lineWidth: 2, lineCap: .round))

    context.fill(disc(from.x, from.y, 3), with: .color(.white.opacity(0.9)))
    context.stroke(disc(to.x, to.y, 3), with: .color(.white.opacity(0.6)), lineWidth: 1.2)
    var halo = context
    halo.addFilter(.blur(radius: 5))
    halo.fill(disc(marker.x, marker.y, 9), with: .color(mode.accent.opacity(0.9)))
    context.fill(disc(marker.x, marker.y, 4.5), with: .color(.white))
}

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func disc(_ x: Double, _ y: Double, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
}

/// A path from the absolute `M`, `L`, `Q`, `C` and `Z` commands of an SVG path, separated by spaces.
private func outline(_ commands: String) -> Path {
    let tokens = commands.split(separator: " ")
    var path = Path()
    var index = 0
    func point() -> CGPoint {
        defer { index += 2 }
        return CGPoint(x: Double(tokens[index]) ?? 0, y: Double(tokens[index + 1]) ?? 0)
    }
    while index < tokens.count {
        let command = tokens[index]
        index += 1
        switch command {
        case "M": path.move(to: point())
        case "L": path.addLine(to: point())
        case "Q":
            let control = point()
            path.addQuadCurve(to: point(), control: control)
        case "C":
            let first = point()
            let second = point()
            path.addCurve(to: point(), control1: first, control2: second)
        default: path.closeSubpath()
        }
    }
    return path
}

// Two footprints, each a sole and a heel.
private let stepsIcon = outline(
    "M 3.7 8.2 C 3.7 5.77 5 3.8 6.6 3.8 C 8.2 3.8 9.5 5.77 9.5 8.2 C 9.5 10.63 8.2 12.6 6.6 12.6 C 5 12.6 3.7 10.63 3.7 8.2 Z "
        + "M 5 15.7 C 5 14.54 5.9 13.6 7 13.6 C 8.1 13.6 9 14.54 9 15.7 C 9 16.86 8.1 17.8 7 17.8 C 5.9 17.8 5 16.86 5 15.7 Z "
        + "M 10.7 6 C 10.7 3.57 12 1.6 13.6 1.6 C 15.2 1.6 16.5 3.57 16.5 6 C 16.5 8.43 15.2 10.4 13.6 10.4 C 12 10.4 10.7 8.43 10.7 6 Z "
        + "M 11.2 13.5 C 11.2 12.34 12.1 11.4 13.2 11.4 C 14.3 11.4 15.2 12.34 15.2 13.5 C 15.2 14.66 14.3 15.6 13.2 15.6 C 12.1 15.6 11.2 14.66 11.2 13.5 Z"
)

// A car seen from the front: body, windscreen line and wheels are stroked, the headlights filled.
private let carIcon = outline(
    "M 3 15 L 3 10 Q 3 8.5 4.2 8 L 5.5 4.5 Q 6 3 7.6 3 L 12.4 3 Q 14 3 14.5 4.5 L 15.8 8 Q 17 8.5 17 10 L 17 15 Z "
        + "M 5.2 8 L 14.8 8 M 5.2 15 L 5.2 17 M 14.8 15 L 14.8 17"
)
private let carLights = outline(
    "M 5.3 11.4 C 5.3 10.79 5.79 10.3 6.4 10.3 C 7.01 10.3 7.5 10.79 7.5 11.4 C 7.5 12.01 7.01 12.5 6.4 12.5 C 5.79 12.5 5.3 12.01 5.3 11.4 Z "
        + "M 12.5 11.4 C 12.5 10.79 12.99 10.3 13.6 10.3 C 14.21 10.3 14.7 10.79 14.7 11.4 C 14.7 12.01 14.21 12.5 13.6 12.5 C 12.99 12.5 12.5 12.01 12.5 11.4 Z"
)

// A plane seen from above, nose up.
private let planeIcon = outline(
    "M 10 1.5 Q 11.4 1.5 11.4 4 L 11.4 7.6 L 18 11.6 L 18 13.4 L 11.4 11.4 L 11.4 15.4 L 13.6 17 L 13.6 18.4 L 10 17.4 "
        + "L 6.4 18.4 L 6.4 17 L 8.6 15.4 L 8.6 11.4 L 2 13.4 L 2 11.6 L 8.6 7.6 L 8.6 4 Q 8.6 1.5 10 1.5 Z"
)
