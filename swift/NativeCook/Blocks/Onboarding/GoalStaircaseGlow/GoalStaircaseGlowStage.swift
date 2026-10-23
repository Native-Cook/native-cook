import SwiftUI

// The whole scene is one drawing on a stage of 393 × 852 points, scaled to fit the screen. What
// it shows at a moment follows from that moment and a short list of times: when the scene
// appeared, when the stairs were called, when each hop began and when it all began to fade.

enum GoalStaircaseGlowMotion {
    /// autoPlay: the stairs at `stairsAt`, the climb at `climbAt`, the fade at `resetAt`, every `loop` seconds.
    static let loop = 8.5
    static let stairsAt = 2.5
    static let climbAt = 4.4
    static let resetAt = 7.4
    /// After a touch autoPlay waits this long, then fades out and starts over.
    static let userHold = 3.0
    /// A time that has not come.
    static let none = 1e9

    /// The scene appears: the ball, then the goal, whose ring swells once.
    static let ballIn = 0.4
    static let goalAfter = 0.2
    /// A dashed line is drawn from the ball towards the goal, and three question marks pop up.
    static let lineAt = 0.6
    static let lineFor = 0.5
    static let marksAt = 1.3
    static let markGap = 0.12
    static let markPop = 0.25
    static let shrugFor = 0.7
    /// They leave by themselves, or as soon as the stairs are called.
    static let askOut = 2.0
    static let askFade = 0.5
    /// The labels arrive one after the other, each sliding up into place.
    static let stairGap = 0.3
    static let stairIn = 0.3
    static let stairRise = 10.0
    /// A hop from step to step, and the last one onto the goal.
    static let hop = 0.32
    static let goalHop = 0.4
    /// The top of a hop is this far above the straight line between its ends.
    static let arc = 22.0
    /// A label lights up when the ball lands, and dips like a spring: 3 points down and back.
    static let lit = 0.12
    static let dip = 6.34
    static let dipDecay = 11.0
    static let dipSwing = 19.05
    static let squash = 0.08
    /// The goal bursts, glows on, and then everything fades to black and stays dark a moment.
    static let burst = 0.4
    static let fade = 0.5
    static let dark = 0.6
    /// How long the scene takes to appear and ask its question.
    static let intro = 3.0
    /// The frames stop this long after the last thing that moves, until the next touch.
    static let idle = 1.0

    static func clamp01(_ t: Double) -> Double {
        min(1, max(0, t))
    }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func easeOut(_ t: Double) -> Double {
        let u = 1 - clamp01(t)
        return 1 - u * u * u
    }

    static func dip(at since: Double) -> Double {
        since <= 0 ? 0 : dip * exp(-dipDecay * since) * sin(dipSwing * since)
    }

    /// A number from 0 to 1 that depends on `n` alone, the same on every platform.
    static func hash(_ n: Int) -> Double {
        var h = (UInt32(truncatingIfNeeded: n) ^ 0x9e37_79b9) &* 0x85eb_ca6b
        h ^= h >> 13
        h = h &* 0xc2b2_ae35
        h ^= h >> 16
        return Double(h) / 4_294_967_296
    }
}

struct GoalStaircaseGlowPlot {
    /// When the scene began to appear.
    var born: Double
    /// When the stairs were called.
    var stairs = GoalStaircaseGlowMotion.none
    /// When each hop began: one per step, and one onto the goal.
    var hops: [Double]
    /// When everything began to fade to black.
    var fade = GoalStaircaseGlowMotion.none

    init(born: Double, steps: Int) {
        self.born = born
        hops = Array(repeating: GoalStaircaseGlowMotion.none, count: steps + 1)
    }

    /// The plot as it stands at `t`: once a fade and the dark after it are over, the scene is new.
    func at(_ t: Double) -> GoalStaircaseGlowPlot {
        typealias M = GoalStaircaseGlowMotion
        guard fade < M.none, t >= fade + M.fade + M.dark else { return self }
        return GoalStaircaseGlowPlot(born: fade + M.fade + M.dark, steps: hops.count - 1)
    }
}

private enum Metrics {
    static let width = 393.0
    static let height = 852.0
    static let ball = 19.0
    static let ballAt = CGPoint(x: 81, y: 484)
    static let goalAt = CGPoint(x: 288, y: 172)
    static let goal = 11.0
    static let ring = 17.0
    static let goalLabel = 213.0
    static let firstStep = CGPoint(x: 158, y: 457)
    static let lastStep = CGPoint(x: 291, y: 280)
    static let labelWidth = 78.0
    static let labelHeight = 22.0
    static let labelPad = 10.0
    static let labelRadius = 4.0
    static let lineFrom = CGPoint(x: 112, y: 455)
    static let lineTo = CGPoint(x: 210, y: 320)
    /// The question marks: where each is from the ball's centre, and its tilt in degrees.
    static let marks: [(Double, Double, Double)] = [(-15, -38, -12), (1, -49, 4), (17, -37, 14)]
    /// Everything is mirrored on the floor, which lies this far under the ball's spot: a dim,
    /// flattened copy straight below it, fainter the higher the thing is. The goal's copy is far
    /// down the screen.
    static let floor = ballAt.y + 52
    static let floorFade = 300.0
    static let goalMirror = 800.0
    static let halo = 120.0
    static let streaks = 14

    static let gold = Color(hex: 0xFED01B)

    /// Soft discs of one colour that fade from the centre to nothing (cos², so there is no rim):
    /// lights, glows and the copies on the floor are these, stretched.
    static func soft(_ hex: UInt32) -> Gradient {
        Gradient(stops: zip([1, 0.905, 0.655, 0.345, 0.095, 0], [0, 0.2, 0.4, 0.6, 0.8, 1]).map {
            Gradient.Stop(color: Color(hex: hex, opacity: $0), location: $1)
        })
    }

    static let haloLight = soft(0x241F1E)
    static let ballLight = soft(0xFFE2A8)
    static let goldLight = soft(0xFED01B)
    static let whiteLight = soft(0xFFFFFF)
    static let ballFace = Gradient(colors: [Color(hex: 0xFFF4DC), Color(hex: 0xFFE2A8)])

    /// A question mark 13 points high, drawn round its middle.
    static let mark: Path = {
        var path = Path()
        path.addArc(center: CGPoint(x: 0, y: -3), radius: 3.3, startAngle: .degrees(180), endAngle: .degrees(415), clockwise: false)
        path.addLine(to: CGPoint(x: 0.2, y: 1))
        path.addLine(to: CGPoint(x: 0, y: 2.6))
        return path
    }()

    /// The centre of step `index` (0 is the lowest) of `count`.
    static func step(_ index: Int, of count: Int) -> CGPoint {
        let u = count > 1 ? Double(index) / Double(count - 1) : 0
        return CGPoint(x: firstStep.x + (lastStep.x - firstStep.x) * u, y: firstStep.y + (lastStep.y - firstStep.y) * u)
    }
}

/// The scene at `t` seconds. It fills the screen; the stage sits scaled in its middle.
struct GoalStaircaseGlowStage: View {
    let t: Double
    let plot: GoalStaircaseGlowPlot
    let steps: [String]
    let goalLabel: String
    /// Reduced motion: the ball does not arc or squash and nothing wobbles.
    let still: Bool

    var body: some View {
        Canvas { context, size in
            typealias M = GoalStaircaseGlowMotion
            let plot = plot.at(t)
            // For the frame in which the list of steps has changed and the plot has not yet.
            let count = min(steps.count, plot.hops.count - 1)
            let life = t - plot.born
            let out = plot.fade < M.none ? 1 - M.smooth(t, plot.fade, plot.fade + M.fade) : 1
            guard life >= 0, out > 0 else { return }
            let scale = min(size.width / Metrics.width, size.height / Metrics.height)
            var stage = context
            stage.translateBy(x: (size.width - Metrics.width * scale) / 2, y: (size.height - Metrics.height * scale) / 2)
            stage.scaleBy(x: scale, y: scale)

            func light(_ gradient: Gradient, _ x: Double, _ y: Double, _ rx: Double, _ ry: Double, _ alpha: Double) {
                guard alpha > 0 else { return }
                var layer = stage
                layer.opacity = min(1, alpha)
                layer.fillEllipse(center: CGPoint(x: x, y: y), radii: CGSize(width: rx, height: ry), gradient: gradient)
            }
            /// Draws a word centred on (x, y).
            func write(_ word: GraphicsContext.ResolvedText, _ x: Double, _ y: Double, _ alpha: Double) {
                guard alpha > 0 else { return }
                var layer = stage
                layer.opacity = alpha
                layer.draw(word, at: CGPoint(x: x, y: y), anchor: .center)
            }
            func label(_ text: String, _ color: Color) -> GraphicsContext.ResolvedText {
                stage.resolve(Text(text).font(.system(size: 10, weight: .bold)).tracking(0.8).foregroundColor(color))
            }

            let ballIn = M.smooth(life, 0, M.ballIn) * out
            let goalIn = M.smooth(life, M.goalAfter, M.goalAfter + M.ballIn) * out
            let burst = t - (plot.hops[count] + M.goalHop)
            let glowing = M.smooth(burst, 0, 0.15)

            // Where the ball is: on its spot, on a step, or on the arc of a hop.
            var x = Metrics.ballAt.x
            var y = Metrics.ballAt.y
            var landed = -M.none
            for k in 0...count {
                let from = plot.hops[k]
                if t < from { break }
                let to = k < count ? Metrics.step(k, of: count) : Metrics.goalAt
                let toY = k < count ? to.y - Metrics.labelHeight / 2 - Metrics.ball : to.y
                let duration = k < count ? M.hop : M.goalHop
                let u = (t - from) / duration
                if u >= 1 || still {
                    x = to.x
                    y = toY + (k < count ? M.dip(at: t - from - M.hop) : 0)
                    landed = from + duration
                } else {
                    // One curve through a point above the middle, at a steady pace: up fast, over slowly, down fast.
                    let midX = (x + to.x) / 2
                    let midY = (y + toY) / 2 - M.arc * 2
                    x = (1 - u) * (1 - u) * x + 2 * u * (1 - u) * midX + u * u * to.x
                    y = (1 - u) * (1 - u) * y + 2 * u * (1 - u) * midY + u * u * toY
                    landed = -M.none
                }
            }
            let since = t - landed
            // It flattens for a moment where it lands, shrugs at its question, and gathers itself before the climb.
            var squash = since >= 0 && since < 0.2 ? M.squash * (1 - M.smooth(since, 0.06, 0.2)) : 0
            let shrug = sin(.pi * M.clamp01((life - M.marksAt) / M.shrugFor))
            if !still { squash += 0.06 * shrug * shrug }
            let ballAlpha = ballIn * (1 - M.smooth(burst, 0, M.lit))

            light(Metrics.haloLight, x, y, Metrics.halo, Metrics.halo, ballAlpha)
            light(Metrics.haloLight, Metrics.goalAt.x, Metrics.goalAt.y, Metrics.halo * 1.1, Metrics.halo * 1.1, goalIn)
            light(
                Metrics.ballLight, x, Metrics.floor, Metrics.ball * 1.5, Metrics.ball * 0.9,
                0.25 * ballAlpha * (1 - 0.6 * M.clamp01((Metrics.ballAt.y - y) / Metrics.floorFade))
            )
            light(Metrics.goldLight, Metrics.goalAt.x, Metrics.goalMirror, 30, 12, 0.25 * goalIn * (1 + glowing))

            // The question: a dashed line towards the goal, and three marks over the ball.
            let leave = min(plot.born + M.askOut, plot.stairs)
            let asking = (1 - M.smooth(t, leave, leave + M.askFade)) * out
            let drawn = M.clamp01((life - M.lineAt) / M.lineFor) * (1 - M.smooth(t, leave, leave + M.askFade))
            if drawn > 0, asking > 0 {
                var line = Path()
                line.move(to: Metrics.lineFrom)
                line.addLine(to: CGPoint(
                    x: Metrics.lineFrom.x + (Metrics.lineTo.x - Metrics.lineFrom.x) * drawn,
                    y: Metrics.lineFrom.y + (Metrics.lineTo.y - Metrics.lineFrom.y) * drawn
                ))
                stage.stroke(line, with: .color(.white.opacity(0.55 * asking)), style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
            }

            // The stairs, from the lowest label up.
            for i in 0..<count {
                let at = Metrics.step(i, of: count)
                let arrived = still ? M.smooth(t - plot.stairs, 0, M.stairIn) : M.easeOut((t - plot.stairs - Double(i) * M.stairGap) / M.stairIn)
                guard arrived > 0 else { continue }
                let landing = plot.hops[i] + M.hop
                let lit = M.smooth(t, landing, landing + M.lit)
                let shown = arrived * out
                let word = label(steps[i], lit > 0.5 ? Color(hex: 0x111111) : .white.opacity(0.6))
                let w = max(Metrics.labelWidth, word.measure(in: CGSize(width: Metrics.width, height: 100)).width + Metrics.labelPad * 2)
                let cy = at.y + (still ? 0 : M.stairRise * (1 - arrived) + M.dip(at: t - landing))
                light(Metrics.whiteLight, at.x, Metrics.floor + 4, w * 0.6, 7, shown * (0.06 + 0.2 * lit))
                let rect = CGRect(x: at.x - w / 2, y: cy - Metrics.labelHeight / 2, width: w, height: Metrics.labelHeight)
                let box = Path(roundedRect: rect, cornerRadius: Metrics.labelRadius, style: .circular)
                if lit > 0 {
                    // A soft glow: three wider, fainter copies.
                    for g in stride(from: 3.0, through: 1, by: -1) {
                        let d = g * 3
                        stage.fill(
                            Path(roundedRect: rect.insetBy(dx: -d, dy: -d), cornerRadius: Metrics.labelRadius + d, style: .circular),
                            with: .color(.white.opacity(shown * lit * (0.13 - g * 0.03)))
                        )
                    }
                    stage.fill(box, with: .color(.white.opacity(shown * lit)))
                }
                stage.stroke(box, with: .color(.white.opacity(0.35 * shown * (1 - lit))), lineWidth: 1)
                write(word, at.x, cy, shown)
            }

            // The goal: a gold disc in a thin ring, its glow, and its name.
            let swell = still ? 1 : 1 + 0.3 * sin(.pi * M.clamp01((life - M.goalAfter) / M.ballIn))
            let reach = (Metrics.goal + 40) * (1 + 0.3 * glowing)
            light(Metrics.goldLight, Metrics.goalAt.x, Metrics.goalAt.y, reach, reach, 0.35 * goalIn * (1 + glowing))
            let ring = Metrics.ring * swell
            stage.stroke(
                Path(ellipseIn: CGRect(x: Metrics.goalAt.x - ring, y: Metrics.goalAt.y - ring, width: ring * 2, height: ring * 2)),
                with: .color(.white.opacity(0.6 * goalIn)), lineWidth: 1
            )
            stage.fill(
                Path(ellipseIn: CGRect(x: Metrics.goalAt.x - Metrics.goal, y: Metrics.goalAt.y - Metrics.goal, width: Metrics.goal * 2, height: Metrics.goal * 2)),
                with: .color(Metrics.gold.opacity(goalIn))
            )
            let name = stage.resolve(
                Text(goalLabel).font(.system(size: 15, weight: .semibold)).tracking(1.5).foregroundColor(glowing > 0.5 ? Metrics.gold : .white)
            )
            write(name, Metrics.goalAt.x, Metrics.goalLabel, goalIn)
            if burst > 0, burst < M.burst, !still {
                // Short streaks fly out of it and fade.
                let p = burst / M.burst
                var streaks = Path()
                for i in 0..<Metrics.streaks {
                    let angle = (Double(i) + M.hash(i * 2 + 1) * 0.6) / Double(Metrics.streaks) * 2 * .pi
                    let from = Metrics.ring + (30 + 20 * M.hash(i * 2 + 2)) * M.easeOut(p)
                    let to = from + 12 * (1 - p)
                    streaks.move(to: CGPoint(x: Metrics.goalAt.x + cos(angle) * from, y: Metrics.goalAt.y + sin(angle) * from))
                    streaks.addLine(to: CGPoint(x: Metrics.goalAt.x + cos(angle) * to, y: Metrics.goalAt.y + sin(angle) * to))
                }
                stage.stroke(streaks, with: .color(Metrics.gold.opacity((1 - p) * out)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }

            // The ball, with its glow.
            if ballAlpha > 0 {
                light(Metrics.ballLight, x, y, Metrics.ball + 30, Metrics.ball + 30, 0.35 * ballAlpha)
                var face = stage
                face.translateBy(x: x, y: y + Metrics.ball * squash)
                face.scaleBy(x: 1 + squash * 0.5, y: 1 - squash)
                face.opacity = ballAlpha
                let b = Metrics.ball
                face.fill(
                    Path(ellipseIn: CGRect(x: -b, y: -b, width: b * 2, height: b * 2)),
                    with: .linearGradient(Metrics.ballFace, startPoint: CGPoint(x: -b * 0.7, y: -b * 0.7), endPoint: CGPoint(x: b * 0.7, y: b * 0.7))
                )
            }
            if asking > 0 {
                for (i, mark) in Metrics.marks.enumerated() {
                    let u = M.clamp01((life - M.marksAt - Double(i) * M.markGap) / M.markPop) - 1
                    guard u > -1 else { continue }
                    // Each pops up past its size and settles, then sways.
                    let pop = still ? 1 : 1 + 2.7 * u * u * u + 1.7 * u * u
                    let sway = still ? 0 : 8 * sin(life * 6 + Double(i) * 2)
                    let alpha = asking * (still ? M.smooth(u, -1, 0) : 1)
                    var glyph = stage
                    glyph.translateBy(x: Metrics.ballAt.x + mark.0, y: Metrics.ballAt.y + mark.1)
                    glyph.rotate(by: .degrees(mark.2 + sway))
                    glyph.scaleBy(x: pop, y: pop)
                    glyph.stroke(Metrics.mark, with: .color(.white.opacity(alpha)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    glyph.fill(Path(ellipseIn: CGRect(x: -1.3, y: 4.1, width: 2.6, height: 2.6)), with: .color(.white.opacity(alpha)))
                }
            }
        }
    }
}
