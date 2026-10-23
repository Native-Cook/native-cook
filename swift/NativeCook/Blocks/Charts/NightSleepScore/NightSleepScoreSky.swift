import SwiftUI

/// The values the night layers read, sampled once per frame (the React Native `NightSkyValues`).
struct NightSleepScoreSkyValues: Equatable {
    var night = 0.0, reveal = 0.0, phase = 0.0, meteor = -1.0
    var clock = 0.0, twinkle = 1.0, cursor = 0.0, presence = 0.0
}

/// The hypnogram as one smooth ribbon (sky.ts): flat at each stage's level, an S-curve at each change.
enum NightSleepScoreRibbon {
    private typealias Chart = NightSleepScoreTheme.Card.Chart
    private typealias Aurora = NightSleepScoreTheme.Aurora

    struct Segment { let stage: NightSleepScoreStage; let x0: Double; let x1: Double; let y: Double }

    static let segments: [Segment] = {
        var minute = 0
        return NightSleepScoreData.segments.map { segment in
            let x0 = Chart.left + Chart.width * Double(minute) / Double(NightSleepScoreData.windowMinutes)
            minute += segment.minutes
            let x1 = Chart.left + Chart.width * Double(minute) / Double(NightSleepScoreData.windowMinutes)
            return Segment(stage: segment.stage, x0: x0, x1: x1, y: Chart.top + Chart.level(segment.stage))
        }
    }()

    /// Half-width of the stage change at each boundary (boundary i sits between segment i and i + 1).
    static let halfWidths: [Double] = segments.dropLast().enumerated().map { i, segment in
        min(Aurora.transitionHalfWidth, 0.45 * (segment.x1 - segment.x0), 0.45 * (segments[i + 1].x1 - segments[i + 1].x0))
    }
    static let boundaries: [Double] = segments.dropLast().map(\.x1)
    static let levels: [Double] = segments.map(\.y)

    static func nightColor(_ stage: NightSleepScoreStage) -> Color { NightSleepScoreTheme.Night.stages.color(stage) }

    /// In design points of the card.
    static let path: Path = {
        var p = Path()
        p.move(to: CGPoint(x: segments[0].x0, y: segments[0].y))
        for (i, segment) in segments.enumerated() {
            if i == segments.count - 1 { p.addLine(to: CGPoint(x: segment.x1, y: segment.y)); break }
            let h = halfWidths[i]
            let next = segments[i + 1]
            p.addLine(to: CGPoint(x: segment.x1 - h, y: segment.y))
            p.addCurve(to: CGPoint(x: segment.x1 + h, y: next.y),
                       control1: CGPoint(x: segment.x1, y: segment.y), control2: CGPoint(x: segment.x1, y: next.y))
        }
        return p
    }()

    /// Horizontal colour stops: each stage's colour on its flat run, blended across its changes.
    static let gradient: Gradient = {
        var stops: [Gradient.Stop] = []
        for (i, segment) in segments.enumerated() {
            let start = segment.x0 + (i > 0 ? halfWidths[i - 1] : 0)
            let end = segment.x1 - (i < segments.count - 1 ? halfWidths[i] : 0)
            let color = nightColor(segment.stage)
            stops.append(.init(color: color, location: (start - Chart.left) / Chart.width))
            stops.append(.init(color: color, location: (end - Chart.left) / Chart.width))
        }
        return Gradient(stops: stops)
    }()

    /// The night ring: the night as a clock, each stage's share in its colour (hard stops).
    static let ringGradient: Gradient = {
        var stops: [Gradient.Stop] = []
        var minute = 0
        for segment in NightSleepScoreData.segments {
            let color = nightColor(segment.stage)
            stops.append(.init(color: color, location: Double(minute) / Double(NightSleepScoreData.windowMinutes)))
            stops.append(.init(color: color, location: Double(minute + segment.minutes) / Double(NightSleepScoreData.windowMinutes)))
            minute += segment.minutes
        }
        return Gradient(stops: stops)
    }()

    /// The ribbon's height at a card x, the same curve as the path (smoothstep across each change).
    static func y(at x: Double) -> Double {
        for i in boundaries.indices {
            let b = boundaries[i], h = halfWidths[i]
            if x < b - h { return levels[i] }
            if x <= b + h {
                let u = (x - (b - h)) / (2 * h)
                return levels[i] + (levels[i + 1] - levels[i]) * u * u * (3 - 2 * u)
            }
        }
        return levels[levels.count - 1]
    }

    static func headColor(at x: Double) -> Color {
        var i = 0
        while i < boundaries.count && x > boundaries[i] { i += 1 }
        return nightColor(segments[i].stage)
    }
}

/// One tick of everything: the controller's frame, the sky's chased values and whether anything is still moving.
struct NightSleepScoreSample {
    var frame: NightSleepScoreFrame
    var sky: NightSleepScoreSkyValues
    var running: Bool
}

/// Owns the controller and the sky chase (NightSleepScore.tsx `updateSkyOnUI`): after a touch the sky values follow
/// their targets through a critically damped spring; in pure autoplay they equal the frame exactly.
final class NightSleepScoreDriver {
    let controller = NightSleepScoreController()
    private typealias K = NightSleepScoreMotion.Chase

    private var night = 0.0, vNight = 0.0, reveal = 0.0, vReveal = 0.0, phase = 0.0, vPhase = 0.0
    private var locked = true
    private var last = -1.0
    private var clock = 0.0

    func sample(at now: TimeInterval) -> NightSleepScoreSample {
        let frame = controller.frame(at: now)
        let dt = last < 0 ? 1.0 / 60 : min(0.1, max(0, now - last))
        let manual = controller.hasManualSelection
        let quiet = controller.quiet
        if quiet || (!manual && locked) {
            let inv = dt > 0 ? 1 / dt : 0
            vNight = (frame.night - night) * inv; night = frame.night
            vReveal = (frame.reveal - reveal) * inv; reveal = frame.reveal
            vPhase = (frame.cursorProgress - phase) * inv; phase = frame.cursorProgress
            locked = true
        } else {
            let n = NightSleepScoreMotion.chase(night, vNight, to: frame.night, dt: dt, omega: K.night)
            let r = NightSleepScoreMotion.chase(reveal, vReveal, to: frame.reveal, dt: dt, omega: K.reveal)
            let p = NightSleepScoreMotion.chase(phase, vPhase, to: frame.cursorProgress, dt: dt, omega: K.phase)
            let settled = !manual && abs(n.0 - frame.night) < K.lockDistance && abs(r.0 - frame.reveal) < K.lockDistance
                && abs(p.0 - frame.cursorProgress) < K.lockDistance && abs(n.1) < K.lockVelocity
                && abs(r.1) < K.lockVelocity && abs(p.1) < K.lockVelocity
            if settled {
                night = frame.night; vNight = 0; reveal = frame.reveal; vReveal = 0
                phase = frame.cursorProgress; vPhase = 0; locked = true
            } else {
                night = min(1, max(0, n.0)); vNight = n.1
                reveal = min(1, max(0, r.0)); vReveal = r.1
                phase = min(1, max(0, p.0)); vPhase = p.1
                locked = false
            }
        }
        last = now
        if night > 0.0005 && !quiet { clock = now.truncatingRemainder(dividingBy: 600) }
        let sky = NightSleepScoreSkyValues(
            night: night, reveal: reveal, phase: phase, meteor: locked && !manual ? frame.meteor : -1,
            clock: clock, twinkle: quiet ? 0 : 1, cursor: frame.cursorProgress, presence: frame.selectionPresence)
        let skyMoving = !locked || (night > 0.0005 && !quiet)
        let running = controller.cadence(at: now) == .animation || skyMoving
        return NightSleepScoreSample(frame: frame, sky: sky, running: running)
    }
}

// MARK: - Sky backdrop

/// The sky behind the card's content (Metal shader). Transparent at rest.
struct NightSleepScoreSkyBackdrop: View {
    let scale: Double
    let sky: NightSleepScoreSkyValues

    var body: some View {
        let card = NightSleepScoreTheme.Card.self
        Rectangle()
            .fill(Color.black)
            .frame(width: card.width * scale, height: card.height * scale)
            .colorEffect(ShaderLibrary.nightSleepScoreSky(
                .float2(CGSize(width: card.width, height: card.height)), .float(scale), .float(sky.night),
                .float(sky.phase), .float(sky.clock), .float(sky.meteor), .float(sky.twinkle)))
            .clipShape(RoundedRectangle(cornerRadius: card.radius * scale, style: .continuous))
            .allowsHitTesting(false)
    }
}

// MARK: - Night ring, aurora hypnogram and playhead

/// Drawn over the day chart and ring while they fade (NightSky.tsx `NightSkyOverlay`).
struct NightSleepScoreOverlay: View {
    let scale: Double
    let sky: NightSleepScoreSkyValues

    private typealias A = NightSleepScoreTheme.Aurora
    private typealias Chart = NightSleepScoreTheme.Card.Chart
    private typealias Ring = NightSleepScoreTheme.Card.Ring

    var body: some View {
        Canvas { context, size in draw(&context) }
            .frame(width: NightSleepScoreTheme.Card.width * scale, height: NightSleepScoreTheme.Card.height * scale)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    private func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x * scale, y: y * scale) }

    private func arc(from: Double, to: Double) -> Path {
        var p = Path()
        let steps = max(2, Int(((to - from) * 360 / 1.5).rounded(.up)))
        for i in 0...steps {
            let turn = from + (to - from) * Double(i) / Double(steps)
            let a = turn * 2 * .pi
            let point = pt(Ring.cx + Ring.radius * cos(a), Ring.cy + Ring.radius * sin(a))
            if i == 0 { p.move(to: point) } else { p.addLine(to: point) }
        }
        return p
    }

    private func draw(_ context: inout GraphicsContext) {
        let M = NightSleepScoreMotion.self
        let C = NightSleepScoreTheme.self
        let ringOpacity = M.nightAt(sky.night, C.Card.At.ring)
        let chartOpacity = M.nightAt(sky.night, C.Card.At.chart)
        let playheadX = Chart.left + Chart.width * sky.cursor
        let headColor = NightSleepScoreRibbon.headColor(at: playheadX)

        // Night ring: the night as a clock, filling with the stage colours as the playhead passes.
        context.drawLayer { layer in
            layer.opacity = ringOpacity
            let circle = Path(ellipseIn: CGRect(x: (Ring.cx - Ring.radius) * scale, y: (Ring.cy - Ring.radius) * scale,
                                                width: Ring.radius * 2 * scale, height: Ring.radius * 2 * scale))
            layer.stroke(circle, with: .color(C.Night.trackRing), lineWidth: Ring.stroke * scale)
            if sky.reveal > 0.0005 {
                layer.stroke(arc(from: 0, to: min(1, sky.reveal)),
                             with: .conicGradient(NightSleepScoreRibbon.ringGradient, center: pt(Ring.cx, Ring.cy), angle: .degrees(-90)),
                             style: StrokeStyle(lineWidth: Ring.stroke * scale, lineCap: .butt))
            }
            let r = sky.reveal
            let headOpacity = min(1, r * 30) * (1 - min(1, max(0, (r - 0.97) / 0.03)))
            let turn = r * 2 * .pi - .pi / 2
            let head = pt(Ring.cx + Ring.radius * cos(turn), Ring.cy + Ring.radius * sin(turn))
            layer.drawLayer { glow in
                glow.opacity = headOpacity
                glow.drawLayer { g in
                    g.addFilter(.blur(radius: (A.headGlowBlur + 2) * scale))
                    let rr = (A.headGlow + 3) * scale
                    g.fill(Path(ellipseIn: CGRect(x: head.x - rr, y: head.y - rr, width: rr * 2, height: rr * 2)), with: .color(headColor))
                }
                let rc = (A.headCore + 1) * scale
                glow.fill(Path(ellipseIn: CGRect(x: head.x - rc, y: head.y - rc, width: rc * 2, height: rc * 2)), with: .color(C.Night.playhead))
            }
        }

        guard chartOpacity > 0.0005 else { return }
        let ribbon = NightSleepScoreRibbon.path.applying(CGAffineTransform(scaleX: scale, y: scale))
        let gradient = Shading.linearGradient(NightSleepScoreRibbon.gradient, startPoint: pt(Chart.left, 0), endPoint: pt(Chart.left + Chart.width, 0))
        let edge = Chart.left + Chart.width * sky.reveal
        context.drawLayer { layer in
            layer.opacity = chartOpacity
            // Aurora curtain: blurred, lifted copies of the ribbon cut into rays.
            layer.drawLayer { curtain in
                for c in A.curtains {
                    curtain.drawLayer { l in
                        l.opacity = c.opacity
                        l.addFilter(.blur(radius: c.blur * scale))
                        l.stroke(ribbon.applying(CGAffineTransform(translationX: 0, y: -c.lift * scale)), with: gradient,
                                 style: StrokeStyle(lineWidth: c.width * scale, lineJoin: .round))
                    }
                }
                curtain.blendMode = .destinationIn
                let rect = CGRect(x: (Chart.left - 12) * scale, y: (Chart.top - 34) * scale,
                                  width: (Chart.width + 24) * scale, height: (Chart.height + 46) * scale)
                curtain.fill(Path(rect), with: .shader(ShaderLibrary.nightSleepScoreRays(.float(scale), .float(sky.clock), .float(edge))))
            }
            // Aurora ribbon, drawn up to the playhead.
            let clipX = Chart.left + Chart.width * sky.reveal
            let clip = CGRect(x: (Chart.left - 12) * scale, y: (Chart.top - 34) * scale,
                              width: max(0, clipX - Chart.left + 12) * scale, height: (Chart.height + 46) * scale)
            layer.drawLayer { ribbonLayer in
                ribbonLayer.clip(to: Path(clip))
                ribbonLayer.drawLayer { glow in
                    glow.opacity = A.glowOpacity
                    glow.addFilter(.blur(radius: A.glowBlur * scale))
                    glow.stroke(ribbon, with: gradient, style: StrokeStyle(lineWidth: A.glowWidth * scale, lineCap: .round, lineJoin: .round))
                }
                ribbonLayer.stroke(ribbon, with: gradient, style: StrokeStyle(lineWidth: A.core * scale, lineCap: .round, lineJoin: .round))
                ribbonLayer.drawLayer { hot in
                    hot.opacity = A.hotCoreOpacity
                    hot.stroke(ribbon, with: .color(.white), style: StrokeStyle(lineWidth: A.hotCore * scale, lineCap: .round, lineJoin: .round))
                }
            }
            // Playhead and its light where it meets the ribbon.
            layer.drawLayer { head in
                head.opacity = sky.presence
                let rect = CGRect(x: (playheadX - A.playheadWidth / 2) * scale, y: (Chart.top - 2) * scale,
                                  width: A.playheadWidth * scale, height: (Chart.height + 4) * scale)
                head.fill(Path(rect), with: .linearGradient(
                    Gradient(colors: [.white.opacity(0), .white.opacity(0.7), .white.opacity(0)]),
                    startPoint: pt(playheadX, Chart.top - 2), endPoint: pt(playheadX, Chart.top + Chart.height + 2)))
                let y = NightSleepScoreRibbon.y(at: playheadX)
                head.drawLayer { g in
                    g.addFilter(.blur(radius: A.headGlowBlur * scale))
                    let rr = A.headGlow * scale
                    g.fill(Path(ellipseIn: CGRect(x: playheadX * scale - rr, y: y * scale - rr, width: rr * 2, height: rr * 2)), with: .color(headColor))
                }
                let rc = A.headCore * scale
                head.fill(Path(ellipseIn: CGRect(x: playheadX * scale - rc, y: y * scale - rc, width: rc * 2, height: rc * 2)), with: .color(C.Night.playhead))
            }
        }
    }

    private typealias Shading = GraphicsContext.Shading
}
