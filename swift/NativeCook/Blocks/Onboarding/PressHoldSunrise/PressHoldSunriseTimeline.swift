import SwiftUI

/// The whole screen is one function of a scene time `s`, in seconds. 0 is the welcome at rest;
/// holding the button runs s to `hold`; from there it runs by itself through the tunnel to `atB`,
/// the first page at rest; going back runs it on to `end`, which is the welcome again (s jumps to 0).
enum PressHoldSunriseMotion {
    /// How long the button has to be held.
    static let hold = 1.4
    /// A release before that springs the button back: seconds, and the spring (mass 1).
    static let release = 0.6
    static let releaseStiffness = 220.0
    static let releaseDamping = 22.0

    // After the release: the texts fade, the orange covers the screen, and five shapes are born in
    // the centre one after another, each growing until it covers the screen in turn.
    static let textOut = 0.2
    static let cover = 0.4
    static let shapeFirst = 0.3
    static let shapeGap = 0.2
    static let shapeGrow = 0.5
    /// Degrees a shape turns while it grows.
    static let shapeTurn = 25.0
    /// A shape is born this many points across.
    static let shapeBorn = 60.0

    // The first page arrives on the plain dark screen, each part a little after the one before.
    static let arrive = hold + 1.7
    static let markIn = 0.3
    static let headingAfter = 0.1
    static let headingIn = 0.4
    static let headingSlide = 24.0
    static let discAfter = 0.2
    static let discIn = 0.4
    static let ballAfter = 0.3
    static let ballGap = 0.1
    static let ballDrop = 70.0
    static let ballFade = 0.12
    static let ballStiffness = 180.0
    static let ballDamping = 16.0
    /// The balls' spring is taken as settled after this long.
    static let ballSettle = 0.7
    static let pagerAfter = 0.6
    static let pagerIn = 0.4
    static let actionAfter = 0.7
    static let actionGap = 0.1
    static let actionIn = 0.35
    static let actionRise = 16.0
    static let atB = arrive + 1.2

    // Going back: the page fades and three shapes shrink into the centre, one behind the other.
    static let pageOut = 0.15
    static let backGap = 0.15
    static let backShrink = 0.5
    static let labelBack = 0.25
    static let end = atB + 2 * backGap + backShrink
    /// Under reduced motion the two screens cross-fade instead.
    static let calmFade = 0.3

    /// autoPlay: the button is pressed at `pressAt` of every `loop` seconds, the page left at `backAt`.
    static let loop = 9.0
    static let pressAt = 1.2
    static let backAt = pressAt + atB + 2
    /// After a touch autoPlay waits this long before it starts over from the welcome.
    static let userHold = 3.0

    // Sizes, in points.
    static let button = 58.0
    /// The button once the hold is complete: the white disc keeps a thin rim round it.
    static let buttonFull = 232.0
    /// The small button sits this far above the disc's centre, which is where it grows to.
    static let buttonRise = 10.0
    static let disc = 259.0
    /// How much the disc swells while the button is held.
    static let discSwell = 0.04

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func easeOut(_ t: Double) -> Double {
        let u = 1 - clamp01(t)
        return 1 - u * u * u
    }

    /// Where a spring of mass 1 that was let go at 0 is after `t` seconds, on its way to 1.
    static func spring(_ t: Double, stiffness: Double, damping: Double) -> Double {
        if t <= 0 { return 0 }
        let w = stiffness.squareRoot()
        let z = damping / (2 * w)
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
    }

    /// The timing curve of an early release: the spring above, over `release` seconds.
    static func releaseEase(_ x: Double) -> Double {
        spring(x * release, stiffness: releaseStiffness, damping: releaseDamping)
    }

    /// How far the button has grown, 0 to 1: steady, easing out a little at the end. Below 0 while a
    /// release overshoots, which makes the button dip under its size for a moment.
    static func held(_ s: Double) -> Double {
        let p = min(1, s / hold)
        return p + 0.12 * sin(.pi * p)
    }

    /// The orange circle's radius. `reach` is the radius that covers the screen.
    static func buttonRadius(_ s: Double, reach: Double, calm: Bool) -> Double {
        if s <= hold { return button / 2 + (buttonFull - button) / 2 * held(s) }
        if s > atB { return button / 2 }
        if calm { return buttonFull / 2 }
        let u = clamp01((s - hold) / cover)
        return buttonFull / 2 + (reach - buttonFull / 2) * u * u * u
    }

    /// How far above the disc's centre the orange circle's centre is.
    static func buttonLift(_ s: Double) -> Double {
        if s <= hold { return buttonRise * (1 - clamp01(held(s))) }
        return s > atB ? buttonRise : 0
    }

    static func discScale(_ s: Double) -> Double {
        s <= hold ? 1 + discSwell * clamp01(held(s)) : 1
    }

    /// Under reduced motion: how much of the dark page shows over the welcome.
    static func dusk(_ s: Double) -> Double {
        if s <= hold { return 0 }
        return s <= atB ? clamp01((s - hold) / calmFade) : 1 - clamp01((s - atB) / calmFade)
    }

    /// The welcome's texts, which lie under the tunnel.
    static func textAlpha(_ s: Double, calm: Bool) -> Double {
        if calm || s <= hold || s > atB { return 1 }
        return 1 - clamp01((s - hold) / textOut)
    }

    /// The bolt and the button's label, which lie over it.
    static func labelAlpha(_ s: Double, calm: Bool) -> Double {
        if calm { return 1 - dusk(s) }
        if s <= hold { return 1 }
        if s <= atB { return 1 - clamp01((s - hold) / textOut) }
        return clamp01((s - (end - labelBack)) / labelBack)
    }

    /// The status bar's icons are light from the moment the orange has covered the screen.
    static func night(_ s: Double, calm: Bool) -> Bool {
        if calm { return dusk(s) > 0.5 }
        return s > hold + cover && s < atB + backShrink
    }

    /// Shape `index` of the tunnel (0 to 4): radius, opacity and turn in degrees.
    static func grown(_ s: Double, index: Int, reach: Double) -> (radius: Double, alpha: Double, turn: Double) {
        let u = (s - hold - shapeFirst - Double(index) * shapeGap) / shapeGrow
        let c = clamp01(u)
        // Translucent while small, so the shape behind tints it. Gone once the next one covers it.
        let covered = index < 4 && u >= 1 + shapeGap / shapeGrow
        let alpha = u <= 0 || s > atB || covered ? 0 : min(1, u / 0.12) * (0.6 + 0.4 * smooth(u, 0.1, 0.5))
        return (shapeBorn / 2 * pow(reach / (shapeBorn / 2), c), alpha, shapeTurn * c)
    }

    /// Shape `index` of the way back (0 is in front): the same, shrinking.
    static func shrunk(_ s: Double, index: Int, reach: Double) -> (radius: Double, alpha: Double, turn: Double) {
        let w = clamp01((s - atB - Double(index) * backGap) / backShrink)
        let alpha = s <= atB ? 0 : 1 - smooth(w, 0.55, 1)
        return (reach * pow(shapeBorn / 2 / reach, w), alpha, -shapeTurn * w)
    }

    /// The first page fades as soon as the way back starts.
    static func pageLeft(_ s: Double) -> Double {
        s > atB ? 1 - clamp01((s - atB) / pageOut) : 1
    }

    /// A part of the first page that fades in over `length`, `after` seconds into the arrival.
    static func arrived(_ s: Double, after: Double, length: Double, calm: Bool) -> Double {
        calm ? 1 : easeOut((s - arrive - after) / length)
    }

    /// The first page's opacity as a whole (each part multiplies its own arrival into it).
    static func pageAlpha(_ s: Double, calm: Bool) -> Double {
        if calm { return dusk(s) }
        return s < arrive ? 0 : pageLeft(s)
    }

    /// Ball `index`: how far above its place it is, and its opacity.
    static func dropped(_ s: Double, index: Int, calm: Bool) -> (lift: Double, alpha: Double) {
        if calm { return (0, 1) }
        let t = s - arrive - ballAfter - Double(index) * ballGap
        let settled = t >= ballSettle ? 1 : spring(t, stiffness: ballStiffness, damping: ballDamping)
        return (ballDrop * (1 - settled), clamp01(t / ballFade))
    }

    /// How much of the pager's arc is drawn, from its left end.
    static func pagerDrawn(_ s: Double, calm: Bool) -> Double {
        calm ? 1 : smooth(s - arrive, pagerAfter, pagerAfter + pagerIn)
    }
}

/// The tunnel's shapes, as outlines round the origin with a radius of about 1. Each is a closed
/// line through many points, so the three stacks draw exactly the same outline.
enum PressHoldSunriseShapes {
    private static let steps = 144

    private static func polar(_ radius: (Double) -> Double) -> Path {
        var path = Path()
        path.addLines((0..<steps).map { i in
            let angle = Double(i) / Double(steps) * .pi * 2
            let r = radius(angle)
            return CGPoint(x: r * cos(angle), y: r * sin(angle))
        })
        path.closeSubpath()
        return path
    }

    /// A square of half-side 1 with corners rounded by 0.3.
    static let square: Path = {
        let corner = 0.3
        var points: [CGPoint] = []
        for quarter in 0..<4 {
            let base = Double(quarter) * .pi / 2
            let cx = (1 - corner) * (cos(base + .pi / 4) < 0 ? -1.0 : 1.0)
            let cy = (1 - corner) * (sin(base + .pi / 4) < 0 ? -1.0 : 1.0)
            for i in 0...12 {
                let angle = base + Double(i) / 12 * (.pi / 2)
                points.append(CGPoint(x: cx + corner * cos(angle), y: cy + corner * sin(angle)))
            }
        }
        var path = Path()
        path.addLines(points)
        path.closeSubpath()
        return path
    }()

    /// A circle with twelve shallow lobes.
    static let scallop = polar { 1 + 0.06 * sin(12 * $0) }
    /// A superellipse: |x|⁴ + |y|⁴ = 1.
    static let squircle = polar { pow(pow(cos($0), 4) + pow(sin($0), 4), -0.25) }
    /// A circle with a slow wave in its edge.
    static let blob = polar { 1 + 0.09 * sin(3 * $0 + 0.5) + 0.04 * sin(5 * $0 + 2) }
    static let round = Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2))
    /// The wavy shape is the one that reaches least far for its size: the others cover with room to spare.
    static let reach = 1 / 0.87 + 0.03

    /// A lightning bolt in a 16 × 16 box.
    static let bolt: Path = {
        var path = Path()
        path.addLines([
            CGPoint(x: 9.4, y: 0.8), CGPoint(x: 3, y: 9.1), CGPoint(x: 7.3, y: 9.1),
            CGPoint(x: 6.3, y: 15.2), CGPoint(x: 13, y: 6.7), CGPoint(x: 8.6, y: 6.7),
        ])
        path.closeSubpath()
        return path
    }()
}
