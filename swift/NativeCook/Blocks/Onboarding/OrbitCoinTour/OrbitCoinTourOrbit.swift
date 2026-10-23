import SwiftUI

// Everything is laid out on a stage of 393 × 852 points that is scaled to fit the screen, so the
// same numbers place the orbit and the cards on every phone. The orbit is drawn in one pass.

enum OrbitCoinTourMotion {
    /// autoPlay: the orbit, the three scenes in turn, and back, every `loop` seconds.
    static let loop = 10.5
    static let openAt = 2.0
    static let secondAt = 4.2
    static let thirdAt = 6.8
    static let closeAt = 9.0
    /// Seconds for the push from the orbit into a scene, and for the pull back out.
    static let push = 1.0
    /// Seconds for one scene to slide to the next, and for a swipe to settle.
    static let slide = 0.8
    static let settle = 0.35
    /// A scene's contents come in over this long, starting `revealAfter` into the slide.
    static let reveal = 0.9
    static let revealAfter = 0.5
    /// Reduced motion: the orbit and the scenes cross-fade in this long.
    static let fade = 0.3
    /// After a touch autoPlay waits this long, then goes back to the orbit and starts over.
    static let userHold = 3.0
    /// The orbit's clock starts again at the pull back, so it is this far on when a loop begins.
    static let phaseAtStart = loop - closeAt
    /// Rings spun by hand coast for this long, ever slower: the curve starts 6.25 times as steep as it is long.
    static let coast = 2.0
    static let coastReach = coast / 6.25
    static let coastCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.16, y: 1), endControlPoint: UnitPoint(x: 0.3, y: 1))
    static let inOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    static let unspin = 0.6

    static func clamp01(_ t: Double) -> Double {
        min(1, max(0, t))
    }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func ease(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
}

enum OrbitCoinTourStage {
    static let width = 393.0
    static let height = 852.0
    static let cx = 196.5
    static let cy = 355.0

    typealias Rgb = (Double, Double, Double)
    static let sand: Rgb = (237, 228, 217)
    static let ink: Rgb = (55, 52, 46)
    static let coral: Rgb = (250, 119, 107)
    static let grey: Rgb = (181, 179, 182)
    /// The background of each scene.
    static let grounds: [Rgb] = [sand, (54, 113, 104), (201, 190, 178)]

    static let rings = [120.0, 185, 260, 350, 450]
    static let ringStroke = 1.5
    static let ringAlpha = 0.13
    static let disc = 184.0
    /// The letter on the disc, a polygon in a 60 × 72 box, drawn 68 points high.
    static let letter: [(Double, Double)] = [(0, 72), (0, 0), (17, 0), (43, 44), (43, 0), (60, 0), (60, 72), (43, 72), (17, 28), (17, 72)]
    static let letterHeight = 68.0

    /// The coin of each scene: its icon, the ring it rides and where on it (degrees, at phase 0).
    static let coins: [(icon: Int, ring: Double, angle: Double)] = [(0, 185, -64), (1, 260, 98), (2, 260, 238)]
    static let coin = 46.0
    /// Where each scene's coin stops, above its card.
    static let coinTop = [152.0, 152, 127]
    /// Small coins that keep flipping, and plain dots.
    static let moons: [(ring: Double, angle: Double, turn: Double)] = [(185, 172, 0), (260, 48, 2)]
    static let moon = 17.0
    static let moonFlip = 1.6
    static let dots: [(ring: Double, angle: Double, r: Double, color: Rgb)] = [(350, -62, 6, ink), (185, 30, 9, ink), (185, 140, 9, grey)]
    /// A coin seen edge-on is this thick.
    static let edge = 9.0
    static let rim = 3.0

    /// The push: the rings grow past the camera and turn, and everything on them fades.
    static let pushGrow = 1.5
    static let pushTurn = Double.pi / 2

    /// What the disc becomes in each scene: the block at the top of its card.
    static let heroes: [(x: Double, y: Double, w: Double, h: Double, r: Double, color: Rgb)] = [
        (cx, 286, 304, 260, 14, (19, 111, 99)),
        (cx, 345, 240, 360, 16, (232, 98, 98)),
        (cx, 248, 304, 220, 14, (54, 59, 66)),
    ]

    /// Degrees a second along a ring of this radius: the inner rings are faster.
    static func speed(of ring: Double) -> Double {
        1300 / ring
    }

    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double {
        a + (b - a) * t
    }

    static func tint(_ a: Rgb, _ b: Rgb, _ t: Double, _ alpha: Double) -> Color {
        Color(.sRGB, red: mix(a.0, b.0, t) / 255, green: mix(a.1, b.1, t) / 255, blue: mix(a.2, b.2, t) / 255, opacity: alpha)
    }

    static func color(_ rgb: Rgb, _ alpha: Double = 1) -> Color {
        tint(rgb, rgb, 0, alpha)
    }

    /// Where coin `index` is on its ring, in stage points.
    static func coinPlace(_ index: Int, phase: Double, spin: Double) -> CGPoint {
        let coin = coins[index]
        let angle = (coin.angle + speed(of: coin.ring) * phase) * .pi / 180 + spin
        return CGPoint(x: cx + cos(angle) * coin.ring, y: cy + sin(angle) * coin.ring)
    }

    static let letterPath: Path = {
        var path = Path()
        path.addLines(letter.map { CGPoint(x: $0.0, y: $0.1) })
        path.closeSubpath()
        return path
    }()

    /// A coin at `at`: `flip` is the cosine of its turn about its vertical axis, so 1 is face-on,
    /// 0 edge-on and below 0 its blank back. `icon` is 0 the house, 1 the card, 2 the rings, -1 none.
    static func drawCoin(_ context: GraphicsContext, at: CGPoint, radius: Double, flip: Double, icon: Int, alpha: Double) {
        guard alpha > 0 else { return }
        let k = radius / coin
        let wide = abs(flip)
        let thick = edge * k * (1 - wide * wide).squareRoot()
        let dark = GraphicsContext.Shading.color(color(ink, alpha))
        // The back and the band between the two faces, which is what shows when the coin is edge-on.
        context.fill(Path(ellipseIn: CGRect(x: at.x - thick / 2 - radius * wide, y: at.y - radius, width: radius * wide * 2, height: radius * 2)), with: dark)
        context.fill(Path(CGRect(x: at.x - thick / 2, y: at.y - radius, width: thick, height: radius * 2)), with: dark)
        var face = context
        face.translateBy(x: at.x + thick / 2, y: at.y)
        face.scaleBy(x: max(wide, 0.001), y: 1)
        func circle(_ r: Double, _ x: Double = 0, _ y: Double = 0) -> Path {
            Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
        }
        face.fill(circle(radius), with: dark)
        face.fill(circle(radius - rim * k), with: .color(color(sand, alpha)))
        face.stroke(circle(radius * 0.8), with: .color(color(ink, alpha * 0.3)), lineWidth: 1)
        guard flip > 0, icon >= 0 else { return }
        face.translateBy(x: -20, y: -20)
        let line = StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
        let dot = GraphicsContext.Shading.color(color(coral, alpha))
        var path = Path()
        switch icon {
        case 0:
            path.addLines([CGPoint(x: 6, y: 19), CGPoint(x: 20, y: 8), CGPoint(x: 34, y: 19)])
            path.addLines([CGPoint(x: 10, y: 18), CGPoint(x: 10, y: 32), CGPoint(x: 30, y: 32), CGPoint(x: 30, y: 18)])
            face.stroke(path, with: dark, style: line)
            face.fill(circle(2.6, 20, 23), with: dot)
        case 1:
            path.addRoundedRect(in: CGRect(x: 5, y: 10, width: 30, height: 21), cornerSize: CGSize(width: 4, height: 4), style: .circular)
            path.move(to: CGPoint(x: 5, y: 17))
            path.addLine(to: CGPoint(x: 35, y: 17))
            path.move(to: CGPoint(x: 24, y: 31))
            path.addArc(center: CGPoint(x: 35, y: 31), radius: 11, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
            path.move(to: CGPoint(x: 29, y: 31))
            path.addArc(center: CGPoint(x: 35, y: 31), radius: 6, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
            face.stroke(path, with: dark, style: line)
            face.fill(circle(2.6, 11.5, 25), with: dot)
        default:
            face.stroke(circle(13, 20, 20), with: dark, style: line)
            face.stroke(circle(7, 20, 20), with: dark, style: line)
            face.fill(circle(2.6, 20, 20), with: dot)
        }
    }
}

/// The orbit: rings, dots, coins and the disc, which becomes the top of a scene's card as the
/// camera pushes through. It fills the screen; the stage sits scaled in its middle.
struct OrbitCoinTourOrbit: View {
    /// How far the push into `scene` (1 to 3) has gone, at a steady pace.
    let tour: Double
    let scene: Int
    /// The orbit's clock in seconds, and what the hand added to the rings in radians.
    let phase: Double
    let spin: Double
    let scale: Double

    var body: some View {
        Canvas { context, size in
            typealias S = OrbitCoinTourStage
            let e = OrbitCoinTourMotion.ease(tour)
            let hero = scene - 1
            let target = S.heroes[hero]
            // Everything on the rings is gone before the push ends.
            let away = 1 - OrbitCoinTourMotion.smooth(e, 0.3, 0.9)
            let grow = 1 + S.pushGrow * e
            let turn = spin + S.pushTurn * e

            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(S.tint(S.sand, S.grounds[hero], e, 1)))
            var stage = context
            stage.translateBy(x: (size.width - S.width * scale) / 2, y: (size.height - S.height * scale) / 2)
            stage.scaleBy(x: scale, y: scale)

            // The rings keep their thin line while they grow: the radius is scaled, not the layer.
            for ring in S.rings {
                let r = ring * grow
                stage.stroke(Path(ellipseIn: CGRect(x: S.cx - r, y: S.cy - r, width: r * 2, height: r * 2)), with: .color(S.color(S.ink, S.ringAlpha * away)), lineWidth: S.ringStroke)
            }
            for dot in S.dots {
                let angle = (dot.angle + S.speed(of: dot.ring) * phase) * .pi / 180 + turn
                let x = S.cx + cos(angle) * dot.ring * grow
                let y = S.cy + sin(angle) * dot.ring * grow
                stage.fill(Path(ellipseIn: CGRect(x: x - dot.r, y: y - dot.r, width: dot.r * 2, height: dot.r * 2)), with: .color(S.color(dot.color, away)))
            }
            for moon in S.moons {
                let angle = (moon.angle + S.speed(of: moon.ring) * phase) * .pi / 180 + turn
                let at = CGPoint(x: S.cx + cos(angle) * moon.ring * grow, y: S.cy + sin(angle) * moon.ring * grow)
                S.drawCoin(stage, at: at, radius: S.moon, flip: cos(S.moonFlip * phase + moon.turn), icon: -1, alpha: away)
            }

            // The disc, on its way to being the top of the scene's card.
            let w = S.mix(S.disc, target.w, e)
            let h = S.mix(S.disc, target.h, e)
            let r = S.mix(S.disc / 2, target.r, e)
            let x = S.mix(S.cx, target.x, e)
            let y = S.mix(S.cy, target.y, e)
            stage.fill(
                Path(roundedRect: CGRect(x: x - w / 2, y: y - h / 2, width: w, height: h), cornerRadius: r, style: .circular),
                with: .color(S.tint(S.coral, target.color, e, 1))
            )
            let letter = 1 - OrbitCoinTourMotion.smooth(e, 0, 0.35)
            if letter > 0 {
                let k = S.letterHeight / 72 * (w / S.disc)
                var mark = stage
                mark.translateBy(x: x - 30 * k, y: y - 36 * k)
                mark.scaleBy(x: k, y: k)
                mark.fill(S.letterPath, with: .color(S.color(S.sand, letter)))
            }

            for (i, coin) in S.coins.enumerated() {
                let angle = (coin.angle + S.speed(of: coin.ring) * phase) * .pi / 180 + spin
                if i == hero {
                    // The scene's coin rides round to the top of the screen, flipping once on the way.
                    var arc = -Double.pi / 2 - angle
                    arc -= 2 * .pi * (arc / (2 * .pi)).rounded()
                    let at = angle + arc * e
                    let reach = S.mix(coin.ring, S.cy - S.coinTop[i], e)
                    S.drawCoin(stage, at: CGPoint(x: S.cx + cos(at) * reach, y: S.cy + sin(at) * reach), radius: S.coin, flip: cos(2 * .pi * e), icon: coin.icon, alpha: 1)
                } else {
                    let at = angle + S.pushTurn * e
                    S.drawCoin(stage, at: CGPoint(x: S.cx + cos(at) * coin.ring * grow, y: S.cy + sin(at) * coin.ring * grow), radius: S.coin, flip: 1, icon: coin.icon, alpha: away)
                }
            }
        }
    }
}

/// A scene's coin alone, for the top of its card.
struct OrbitCoinTourCoin: View {
    let icon: Int
    let flip: Double

    var body: some View {
        Canvas { context, size in
            OrbitCoinTourStage.drawCoin(context, at: CGPoint(x: size.width / 2, y: size.height / 2), radius: OrbitCoinTourStage.coin, flip: flip, icon: icon, alpha: 1)
        }
    }
}
