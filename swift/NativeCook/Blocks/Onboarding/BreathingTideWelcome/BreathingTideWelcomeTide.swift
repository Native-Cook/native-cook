import SwiftUI

/// The whole screen is one function of a scene time `s`, in seconds. 0 is the splash at rest. The
/// logo fades and the tide rises to `atLow`, where it waits for a breath; holding runs s to `atTop`
/// (the inhale), and from there it runs by itself through the exhale to `atC`, the welcome at rest.
/// Going back runs it on to `end`, which is the splash again (s jumps to 0).
enum BreathingTideWelcomeMotion {
    /// The splash rests this long before the tide comes.
    static let splash = 0.5
    static let logoOut = 0.4
    static let rise = 0.4
    static let atLow = logoOut + rise
    /// The inhale: how long the hold lasts. The exhale is slower, or the breath feels mechanical.
    static let inhale = 2.0
    static let atTop = atLow + inhale
    static let hold = 0.6
    static let exhale = 3.3
    static let atOut = atTop + hold + exhale
    /// The welcome fades in over the tide, which sinks out of view.
    static let arrive = 0.6
    static let atC = atOut + arrive
    /// Back to the splash: a cross-fade.
    static let back = 0.5
    static let end = atC + back
    /// An instruction fades in or out in this long.
    static let word = 0.3
    /// The yellow panel comes down this far as it fades in.
    static let panelSlide = 30.0

    // Where the tide's crest is, as fractions of the screen's height, and how much lower its ends
    // are at the screen's edges, in points: the body swells as it rises.
    static let low = 0.751
    static let top = 0.317
    static let dropLow = 34.0
    static let dropTop = 50.0
    /// The crest is this far under the screen's bottom edge when the tide is away.
    static let away = 70.0
    /// The smile is this much wider at the top of the breath.
    static let smileGrow = 0.06
    /// The ornaments float this far up and down, each on its own time.
    static let float = 4.0
    static let floatPeriods = [2.5, 2, 10.0 / 3]

    /// The checkbox lands on a spring (mass 1).
    static let tickStiffness = 300.0
    static let tickDamping = 14.0

    /// autoPlay: the tide comes at `enterAt` of every `loop` seconds, the breath starts at `pressAt`,
    /// the box is ticked at `tickAt` and the splash returns from `backAt`.
    static let loop = 10.0
    static let enterAt = splash
    static let pressAt = enterAt + atLow
    static let tickAt = enterAt + atC + 0.7
    static let backAt = enterAt + atC + 1.5
    /// After a touch autoPlay waits this long before it starts over from the splash.
    static let userHold = 3.0

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    /// Slow at both ends, like a breath.
    static func sine(_ t: Double) -> Double { 0.5 - 0.5 * cos(.pi * clamp01(t)) }

    static func easeOut(_ t: Double) -> Double {
        let u = 1 - clamp01(t)
        return 1 - u * u * u
    }

    /// How full the breath is: 0 at the low tide, 1 at the top.
    static func breath(_ s: Double) -> Double {
        if s <= atTop { return sine((s - atLow) / inhale) }
        return 1 - sine((s - atTop - hold) / exhale)
    }

    /// How much of the tide is on the screen: it rises from under it, and sinks away as the welcome comes.
    static func ashore(_ s: Double) -> Double {
        if s <= atLow { return easeOut((s - logoOut) / rise) }
        if s <= atOut { return 1 }
        let u = clamp01((s - atOut) / arrive)
        return s <= atC ? 1 - u * u : 0
    }

    /// The crest's y on a screen `height` tall. Under reduced motion the tide stays half way.
    static func crest(_ s: Double, height: Double, calm: Bool) -> Double {
        if calm { return height * (low + (top - low) * 0.5) }
        let there = ashore(s)
        let rest = height * low
        return height + away + (rest - height - away) * there + (height * top - rest) * breath(s)
    }

    static func drop(_ s: Double, calm: Bool) -> Double {
        dropLow + (dropTop - dropLow) * (calm ? 0.5 : breath(s))
    }

    /// The tide's opacity: under reduced motion it fades in and out instead of travelling, and the breath shows as a glow.
    static func tideAlpha(_ s: Double, calm: Bool) -> Double {
        calm ? ashore(s) * (0.6 + 0.4 * breath(s)) : 1
    }

    static func logoAlpha(_ s: Double) -> Double {
        s <= atC ? 1 - clamp01(s / logoOut) : clamp01((s - atC) / back)
    }

    /// "Breathe in": there from the rise to the top of the breath.
    static func inAlpha(_ s: Double) -> Double {
        clamp01((s - logoOut) / rise) * (1 - clamp01((s - atTop) / word))
    }

    /// "Breathe out": there from the hold to the end of the breath.
    static func outAlpha(_ s: Double) -> Double {
        clamp01((s - atTop - word) / word) * (1 - clamp01((s - atOut) / word))
    }

    /// How much of the welcome is there.
    static func welcome(_ s: Double) -> Double {
        if s <= atC {
            let u = clamp01((s - atOut) / arrive)
            return u * u * (3 - 2 * u)
        }
        return 1 - clamp01((s - atC) / back)
    }

    /// How far up an ornament has floated, `clock` seconds after the block appeared.
    static func floated(_ index: Int, clock: Double) -> Double {
        float * sin(2 * .pi * clock / floatPeriods[index % 3] + Double(index) * 1.9)
    }
}

/// The pictures of the block, drawn into a canvas.
enum BreathingTideWelcomeArt {
    static let panelInk: UInt32 = 0xfdb703
    static let tideInk: UInt32 = 0xf47d35
    static let faceInk: UInt32 = 0x3d3531
    static let faceDisc: Double = 0xfd7203
    private static let white: Double = 0xffffff
    private static let sky: Double = 0x5aa9f8
    private static let pink: Double = 0xf9a8c4
    private static let plum: Double = 0x6b4fd8
    private static let night: Double = 0x3b2a8c
    private static let note: Double = 0x2f6bff
    private static let red: Double = 0xf0503c

    /// A quarter of a ring from the left round to the top: a band of a rainbow.
    private static func band(_ radius: Double, _ width: Double) -> [Double] {
        var points: [Double] = []
        for i in 0...6 {
            let angle = Double.pi + Double(i) / 6 * (.pi / 2)
            points += [(radius + width / 2) * cos(angle), (radius + width / 2) * sin(angle)]
        }
        for i in (0...6).reversed() {
            let angle = Double.pi + Double(i) / 6 * (.pi / 2)
            points += [(radius - width / 2) * cos(angle), (radius - width / 2) * sin(angle)]
        }
        return points
    }

    /// A star with four thin points.
    private static func star(_ size: Double) -> [Double] {
        let waist = size * 0.2
        return [0, -size, waist, -waist, size, 0, waist, waist, 0, size, -waist, waist, -size, 0, -waist, -waist]
    }

    private static let rainbowOuter: [Double] = [0, red] + band(16, 5)
    private static let rainbowInner: [Double] = [0, pink] + band(9, 5)
    private static let sparkleLarge: [Double] = [0, white] + star(9)
    private static let sparkleSmall: [Double] = [0, white] + star(6)

    /// The ornaments round the phone: where each is, and its parts. A part is a shape, a colour
    /// (0xRRGGBB) and numbers: a polygon (0, then its points), a rounded rectangle (1: x, y,
    /// width, height, radius) or a circle (2: x, y, radius).
    static let ornaments: [(x: Double, y: Double, parts: [[Double]])] = [
        // A cloud.
        (-98, -34, [[2, sky, -8, 2, 8], [2, sky, 2, -3, 10], [2, sky, 12, 3, 7], [1, sky, -16, 2, 35, 9, 4.5]]),
        // A flower.
        (-76, -92, [[2, white, 0, -8, 6], [2, white, 7.6, -2.5, 6], [2, white, 4.7, 6.5, 6], [2, white, -4.7, 6.5, 6], [2, white, -7.6, -2.5, 6], [2, pink, 0, 0, 4.5]]),
        // A moon.
        (-84, 52, [[2, plum, 0, 0, 15], [2, night, 5, -4, 11]]),
        // A music note.
        (86, 84, [[2, note, -5, 8, 5.5], [1, note, -1.5, -10, 3, 19, 1.5], [0, note, 1.5, -10, 11, -6, 11, -1, 1.5, -5]]),
        // A rainbow.
        (112, -10, [rainbowOuter, rainbowInner]),
        // A play button.
        (88, -94, [[2, faceDisc, 0, 0, 14], [0, white, -4, -6.5, 7, 0, -4, 6.5]]),
        // Two sparkles.
        (112, 36, [sparkleLarge]),
        (-112, 6, [sparkleSmall]),
    ]

    /// The phone in the picture, and the face disc that overlaps its right half.
    static let phone = (width: 118.0, height: 215.0, radius: 18.0)
    static let disc = (x: 22.0, y: 6.0, size: 150.0)

    /// The face: two closed eyes and a smile under them, as three arcs. (x, y) is the middle of the
    /// line the eyes hang from; at `scale` 1 an eye is 34 wide and the smile 130.
    static func face(_ context: inout GraphicsContext, x: Double, y: Double, scale: Double, stroke: Double, wide: Double, alpha: Double) {
        let half = 65 * scale * wide
        var path = Path()
        path.move(to: CGPoint(x: x - 41 * scale, y: y))
        path.addQuadCurve(to: CGPoint(x: x - 7 * scale, y: y), control: CGPoint(x: x - 24 * scale, y: y + 24 * scale))
        path.move(to: CGPoint(x: x + 7 * scale, y: y))
        path.addQuadCurve(to: CGPoint(x: x + 41 * scale, y: y), control: CGPoint(x: x + 24 * scale, y: y + 24 * scale))
        path.move(to: CGPoint(x: x - half, y: y + 16 * scale))
        path.addQuadCurve(to: CGPoint(x: x + half, y: y + 16 * scale), control: CGPoint(x: x, y: y + 72 * scale))
        context.stroke(path, with: .color(Color(hex: faceInk, opacity: alpha)), style: StrokeStyle(lineWidth: stroke, lineCap: .round))
    }

    /// The tide at scene time `s`: a fill under an arched edge, and the face that rides on it.
    static func tide(_ context: inout GraphicsContext, s: Double, calm: Bool, size: CGSize, color: Color) {
        let alpha = M.tideAlpha(s, calm: calm)
        let y = M.crest(s, height: size.height, calm: calm)
        guard alpha > 0.003, y < size.height else { return }
        let fall = M.drop(s, calm: calm)
        var path = Path()
        path.move(to: CGPoint(x: 0, y: y + fall))
        // The edge is an arc through the crest: the curve's middle is half way to its control point.
        path.addQuadCurve(to: CGPoint(x: size.width, y: y + fall), control: CGPoint(x: size.width / 2, y: y - fall))
        path.addLine(to: CGPoint(x: size.width, y: size.height))
        path.addLine(to: CGPoint(x: 0, y: size.height))
        path.closeSubpath()
        context.fill(path, with: .color(color.opacity(alpha)))
        let full = calm ? 0.5 : M.breath(s)
        face(&context, x: size.width / 2, y: y + 36, scale: 1, stroke: 5, wide: 1 + M.smileGrow * full * full, alpha: alpha)
    }

    /// The picture on the welcome round `center`: a phone, the face on a disc, and the ornaments, which float.
    static func picture(_ context: inout GraphicsContext, clock: Double?, center: CGPoint) {
        let box = CGRect(x: center.x - phone.width / 2, y: center.y - phone.height / 2, width: phone.width, height: phone.height)
        context.fill(Path(roundedRect: box, cornerRadius: phone.radius, style: .circular), with: .color(.white))
        let middle = CGPoint(x: center.x + disc.x, y: center.y + disc.y)
        context.fill(
            Path(ellipseIn: CGRect(x: middle.x - disc.size / 2, y: middle.y - disc.size / 2, width: disc.size, height: disc.size)),
            with: .color(Color(hex: UInt32(faceDisc)))
        )
        face(&context, x: middle.x, y: middle.y - 14, scale: 0.6, stroke: 3, wide: 1, alpha: 1)

        for (n, ornament) in ornaments.enumerated() {
            let x = center.x + ornament.x
            let y = center.y + ornament.y - (clock.map { M.floated(n, clock: $0) } ?? 0)
            for part in ornament.parts {
                let shading = GraphicsContext.Shading.color(Color(hex: UInt32(part[1])))
                if part[0] == 2 {
                    context.fill(Path(ellipseIn: CGRect(x: x + part[2] - part[4], y: y + part[3] - part[4], width: part[4] * 2, height: part[4] * 2)), with: shading)
                } else if part[0] == 1 {
                    context.fill(Path(roundedRect: CGRect(x: x + part[2], y: y + part[3], width: part[4], height: part[5]), cornerRadius: part[6], style: .circular), with: shading)
                } else {
                    var path = Path()
                    var i = 2
                    while i + 1 < part.count {
                        let point = CGPoint(x: x + part[i], y: y + part[i + 1])
                        if i == 2 { path.move(to: point) } else { path.addLine(to: point) }
                        i += 2
                    }
                    path.closeSubpath()
                    context.fill(path, with: shading)
                }
            }
        }
    }
}

private typealias M = BreathingTideWelcomeMotion
