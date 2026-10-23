import SwiftUI

/// The pile's height above the bottom edge, in points.
let holdStopSheetPileHeight = 380.0

// Rows of the pile, from the bottom up, and the gap between them.
private let rows = 13
private let rowStep = 28.0
// How far a tomato falls out of sight below the screen, and how much of the motion is spread over the tomatoes.
private let fallDistance = 640.0
private let spread = 0.5

private enum Kind { case red, yellow, orange }
private enum Face { case none, dots, smile, happy, oh }

// Colours of a piece: light where the light falls, base, and the shaded edge.
private func palette(_ kind: Kind) -> [Color] {
    switch kind {
    case .red: [Color(hex: 0xff9a62), Color(hex: 0xf2622d), Color(hex: 0xcf401b)]
    case .yellow: [Color(hex: 0xffe68a), Color(hex: 0xfbc63a), Color(hex: 0xe39d12)]
    case .orange: [Color(hex: 0xffbe6a), Color(hex: 0xf88f1f), Color(hex: 0xd9690b)]
    }
}
private let leaf = Color(hex: 0x4fb03c)
private let leafDark = Color(hex: 0x2f8a2a)
private let faceInk = Color(hex: 0x3a2019)
private let blush = Color(hex: 0xff7f6e)

/// A fixed pseudo-random number in [0, 1) for a seed, so every pile is the same pile.
private func seeded(_ seed: Double) -> Double {
    let x = sin(seed * 127.1 + 311.7) * 43758.5453
    return x - floor(x)
}

/// What `Easing.bounce` of Reanimated gives: a ball that lands and bounces three times.
func holdStopSheetBounce(_ t: Double) -> Double {
    if t < 1 / 2.75 { return 7.5625 * t * t }
    if t < 2 / 2.75 {
        let u = t - 1.5 / 2.75
        return 7.5625 * u * u + 0.75
    }
    if t < 2.5 / 2.75 {
        let u = t - 2.25 / 2.75
        return 7.5625 * u * u + 0.9375
    }
    let u = t - 2.625 / 2.75
    return 7.5625 * u * u + 0.984375
}

private struct Tomato {
    var x: Double
    var y: Double
    var size: Double
    var kind: Kind
    var face: Face
    var delay: Double
    var tilt: Double
    var calyx: Double
}

/// How high the pile stands at a point `u` of the width (0 left, 1 right), in points.
private func surface(_ u: Double) -> Double {
    262 + 58 * sin(u * 5.6 + 0.9) + 26 * sin(u * 13.1 + 2.2)
}

/// The tomatoes of a pile `width` wide, back ones first.
private func pile(_ width: Double) -> [Tomato] {
    var list: [Tomato] = []
    var i = 0.0
    for row in 0..<rows {
        let r = Double(row)
        let rowY = holdStopSheetPileHeight - 20 - r * rowStep
        var x = -14 + seeded(r * 13 + 1) * 22
        while x < width + 8 {
            i += 1
            let big = seeded(i * 11 + 9) > 0.95 && row > 1 && row < 7
            let size = big ? 64 + seeded(i * 11 + 3) * 12 : 36 + seeded(i * 11 + 3) * 18
            let cx = x + size * 0.45
            let cy = rowY + (seeded(i * 11 + 4) - 0.5) * 16
            if holdStopSheetPileHeight - cy < surface(cx / width) {
                let pick = seeded(i * 11 + 6)
                let kind: Kind = pick < 0.76 ? .red : pick < 0.93 ? .yellow : .orange
                let look = seeded(i * 11 + 7)
                let face: Face = size < 40 && look < 0.5 ? .none : look < 0.35 ? .dots : look < 0.6 ? .smile : look < 0.82 ? .happy : .oh
                list.append(Tomato(
                    x: cx - size / 2,
                    y: cy - size / 2,
                    size: size,
                    kind: kind,
                    face: face,
                    delay: seeded(i * 11 + 5) * spread,
                    tilt: (seeded(i * 11 + 8) - 0.5) * 50,
                    calyx: seeded(i * 11 + 10) * 72
                ))
            }
            x += size * 0.74 + seeded(i * 11 + 2) * 8
        }
    }
    // Lower and bigger pieces sit in front.
    return list.sorted { $0.y + $0.size * 0.6 < $1.y + $1.size * 0.6 }
}

/// A five-leaf calyx around (50, 20) in the 100 x 100 box, turned by `turn` degrees.
private func calyxPath(_ turn: Double) -> Path {
    var path = Path()
    for k in 0..<5 {
        let a = (-90 + turn + Double(k) * 72) * .pi / 180
        let l = a - 0.5
        let r = a + 0.5
        path.move(to: CGPoint(x: 50, y: 20))
        path.addQuadCurve(to: CGPoint(x: 50 + cos(a) * 24, y: 20 + sin(a) * 15), control: CGPoint(x: 50 + cos(l) * 13, y: 20 + sin(l) * 9))
        path.addQuadCurve(to: CGPoint(x: 50, y: 20), control: CGPoint(x: 50 + cos(r) * 13, y: 20 + sin(r) * 9))
        path.closeSubpath()
    }
    return path
}

private func line(_ from: CGPoint, _ to: CGPoint) -> Path {
    Path { $0.move(to: from); $0.addLine(to: to) }
}

private func arc(_ from: CGPoint, _ control: CGPoint, _ to: CGPoint) -> Path {
    Path { $0.move(to: from); $0.addQuadCurve(to: to, control: control) }
}

private func ellipse(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Path {
    Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: 2 * rx, height: 2 * ry))
}

/// One tomato in its 100 x 100 box: a lit body, its leaves, a highlight and maybe a face.
private func draw(_ tomato: Tomato, in context: GraphicsContext) {
    let colors = palette(tomato.kind)
    let edge = colors[2]
    context.fill(
        ellipse(50, 56, 46, 42),
        with: .radialGradient(Gradient(colors: colors), center: CGPoint(x: 36, y: 36), startRadius: 0, endRadius: 70)
    )
    if tomato.kind == .orange {
        for (x, y) in [(30.0, 50.0), (62, 42), (44, 74), (70, 66), (52, 58)] {
            context.fill(ellipse(x, y, 1.6, 1.6), with: .color(edge.opacity(0.35)))
        }
        context.stroke(line(CGPoint(x: 50, y: 18), CGPoint(x: 51, y: 9)), with: .color(leafDark), style: StrokeStyle(lineWidth: 4, lineCap: .round))
        let blade = Path {
            $0.move(to: CGPoint(x: 52, y: 15))
            $0.addQuadCurve(to: CGPoint(x: 80, y: 10), control: CGPoint(x: 66, y: 2))
            $0.addQuadCurve(to: CGPoint(x: 52, y: 15), control: CGPoint(x: 70, y: 22))
            $0.closeSubpath()
        }
        context.fill(blade, with: .color(leaf))
        context.stroke(blade, with: .color(leafDark), style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
    } else {
        let calyx = calyxPath(tomato.calyx)
        context.fill(calyx, with: .color(leaf))
        context.stroke(calyx, with: .color(leafDark), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
        context.stroke(line(CGPoint(x: 50, y: 20), CGPoint(x: 52, y: 9)), with: .color(leafDark), style: StrokeStyle(lineWidth: 4.5, lineCap: .round))
    }
    var shine = context
    shine.translateBy(x: 30, y: 40)
    shine.rotate(by: .degrees(-38))
    shine.fill(ellipse(0, 0, 10, 5.5), with: .color(.white.opacity(0.45)))

    guard tomato.face != .none else { return }
    context.fill(ellipse(34, 66, 6, 3.4), with: .color(blush.opacity(0.5)))
    context.fill(ellipse(66, 66, 6, 3.4), with: .color(blush.opacity(0.5)))
    let stroke = StrokeStyle(lineWidth: 2.6, lineCap: .round)
    if tomato.face == .happy {
        context.stroke(arc(CGPoint(x: 37, y: 59), CGPoint(x: 41, y: 54), CGPoint(x: 45, y: 59)), with: .color(faceInk), style: stroke)
        context.stroke(arc(CGPoint(x: 55, y: 59), CGPoint(x: 59, y: 54), CGPoint(x: 63, y: 59)), with: .color(faceInk), style: stroke)
    } else {
        context.fill(ellipse(41, 58, 3.4, 3.4), with: .color(faceInk))
        context.fill(ellipse(59, 58, 3.4, 3.4), with: .color(faceInk))
    }
    if tomato.face == .smile || tomato.face == .happy {
        context.stroke(arc(CGPoint(x: 45, y: 66), CGPoint(x: 50, y: 72), CGPoint(x: 55, y: 66)), with: .color(faceInk), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
    }
    if tomato.face == .oh {
        context.fill(ellipse(50, 68, 2.6, 3.2), with: .color(faceInk))
    }
}

/// Each tomato drawn once into an image, turned by its tilt, so a frame of the fall only moves images.
@MainActor
private enum Pictures {
    private static var cache: [Double: [(Tomato, Image)]] = [:]

    static func of(width: Double, scale: Double) -> [(Tomato, Image)] {
        if let found = cache[width] { return found }
        let made = pile(width).map { tomato in
            let s = tomato.size
            let renderer = ImageRenderer(content: Canvas { context, _ in
                context.translateBy(x: s / 2, y: s / 2)
                context.rotate(by: .degrees(tomato.tilt))
                context.translateBy(x: -s / 2, y: -s / 2)
                context.scaleBy(x: s / 100, y: s / 100)
                draw(tomato, in: context)
            }
            .frame(width: s, height: s))
            renderer.scale = scale
            let image = renderer.cgImage.map { Image(decorative: $0, scale: scale) } ?? Image(systemName: "circle")
            return (tomato, image)
        }
        cache[width] = made
        return made
    }
}

/// A pile of tomatoes along the bottom of a screen `drop` high, which is also how far above it
/// they rain in from; the canvas covers the whole screen so they can be seen falling. `fall` goes
/// 0 to 1 as the pile drops away below the screen, `rise` 0 to 1 as it rains back in from above
/// and lands with bounces. Each tomato is a little behind the one before it, which is what makes a
/// pile and not a sheet.
struct HoldStopSheetTomatoes: View {
    var width: Double
    var drop: Double
    var fall: Double
    var rise: Double
    var reduced: Bool
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        let pictures = Pictures.of(width: width, scale: displayScale)
        Canvas { context, _ in
            context.translateBy(x: 0, y: drop - holdStopSheetPileHeight)
            for (tomato, image) in pictures {
                let down = min(1, max(0, fall * (1 + spread) - tomato.delay))
                let up = min(1, max(0, rise * (1 + spread) - tomato.delay))
                var drawn = context
                if reduced {
                    drawn.opacity = 1 - down * (1 - up)
                } else if rise > 0 {
                    drawn.translateBy(x: 0, y: -drop * (1 - holdStopSheetBounce(up)))
                } else {
                    drawn.translateBy(x: 0, y: fallDistance * down * down)
                }
                drawn.draw(image, in: CGRect(x: tomato.x, y: tomato.y, width: tomato.size, height: tomato.size))
            }
        }
        .frame(width: width, height: drop)
        .allowsHitTesting(false)
    }
}
