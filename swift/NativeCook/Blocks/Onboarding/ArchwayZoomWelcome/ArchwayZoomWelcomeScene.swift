import SwiftUI

/// The scene is a camera travelling forwards for ever: through an archway to the bar chart behind
/// it, on through the bars, to the next archway. Everything in it is drawn from one number, the
/// seconds into a round, and each archway with its chart is a "generation" that is drawn twice:
/// once on its way out (at `tau`) and once on its way in (at `tau - loop`). The one coming in
/// stands, at the end of the round, exactly where the one going out stood at its start.
enum ArchwayZoomWelcomeScene {
    /// One round of the camera, in seconds.
    static let loop = 10.7

    // The times of a generation, in seconds from the moment it stands at rest.
    /// It comes into view small and far away, `arrive` before, and grows to its place over `grow`.
    private static let arrive = 3.6
    private static let arriveFade = 0.7
    private static let grow = 2.9
    private static let arriveScale = 0.4
    /// The push in: the arch grows to `archZoom` times its size over `push`, speeding up, and fades
    /// as its opening passes the edges of the screen.
    private static let pushAt = 0.7
    private static let push = 1.4
    private static let archZoom = 4.0
    private static let archOutAt = 1.3
    private static let archOut = 0.7
    /// The chart is further away, so it grows more slowly, to `full` by `creepAt`; then it creeps on
    /// by `creep` until `leaveAt`, and from there the camera pushes through it.
    private static let full = 7.5
    private static let creepAt = 5.0
    private static let creep = 0.3
    private static let leaveAt = 7.1
    private static let leavePush = 0.476
    /// The two key bars turn green from the top down, and a marker drops in over each.
    private static let greenIn = 0.3
    private static let markerOut = 0.7
    private static let markerBob = 4.0
    private static let markerBobEvery = 2.1
    /// The bars dissolve into mist: their feet first, then all of them; the green ones last.
    private static let mist = [7.8, 1, 8.3, 1.6]
    private static let greenMist = [8.2, 1.2, 8.8, 1.7]
    /// The hills at the horizon sink as the camera sets off.
    private static let hillsOut = 1.4
    private static let hillsDrop = 40.0
    private static let hillsIn = 1.5

    // Sizes, in points at rest. Everything scales about the point on the horizon in the middle of
    // the screen, which is `horizon` above the sheet.
    static let horizon = 184.0
    /// The arch: its base under the horizon, then its nested outlines, widest first, and its plinth.
    private static let archBase = 28.0
    private static let archWidths = [112.0, 92, 72, 62]
    private static let archHeights = [150.0, 140, 130, 125]
    private static let plinth = CGSize(width: 130, height: 8)
    /// The chart: the width of a key bar, and the ground it stands on under the horizon.
    private static let unit = 12.7
    private static let ground = 25.5

    private enum Kind: Int {
        case light, pale, mid, dark, key
    }

    /// A bar: its left and right edge and its height, in key-bar widths from the middle, and its
    /// colour. They are listed from the back to the front.
    private static let bars: [(from: Double, to: Double, height: Double, kind: Kind)] = [
        (0.18, 0.54, 1.4, .light),
        (-0.52, 0, 1.03, .pale),
        (-2.07, -1.65, 0.4, .mid),
        (1.65, 2.1, 1.75, .dark),
        (-1.53, -0.53, 1.6, .key),
        (0.56, 1.56, 2.9, .key),
        (-0.45, 0.47, 0.55, .mid),
    ]
    /// The part of a bar's width that is its side face, which is darker by `sideShade`.
    private static let side = 0.1
    private static let sideShade = 0.12
    /// A marker over a key bar: its width and height in key-bar widths, and how far over the bar it floats.
    private static let marker = (width: 0.3, height: 0.26, lift: 0.4)
    private static let markerDrop = 0.5

    /// Colours as red, green, blue from 0 to 1.
    private typealias Tone = (Double, Double, Double)
    private static let greys: [Tone] = [(0.89, 0.89, 0.89), (0.851, 0.855, 0.851), (0.788, 0.788, 0.788), (0.745, 0.749, 0.745)]
    private static let green: Tone = (0.141, 0.769, 0.537)
    private static let greenPale: Tone = (0.698, 0.918, 0.839)
    private static let markerGreen: Tone = (0.231, 0.796, 0.596)
    private static let hill: Tone = (0.925, 0.925, 0.925)
    private static let archFaces: [Tone] = [(0.918, 0.918, 0.918), (0.941, 0.941, 0.941), (0.769, 0.769, 0.769)]
    private static let plinthGrey: Tone = (0.851, 0.855, 0.851)
    /// The sky, from the top of the screen to its bottom edge.
    private static let sky = Gradient(stops: [
        .init(color: tint((0.996, 0.996, 0.996), 1), location: 0),
        .init(color: tint((0.996, 0.996, 0.996), 1), location: 0.55),
        .init(color: tint((0.745, 0.745, 0.745), 1), location: 0.8),
        .init(color: tint((0.643, 0.643, 0.643), 1), location: 1),
    ])

    private static func smooth(_ t: Double) -> Double {
        let u = min(1, max(0, t))
        return u * u * (3 - 2 * u)
    }

    private static func tint(_ tone: Tone, _ alpha: Double) -> Color {
        Color(.sRGB, red: tone.0, green: tone.1, blue: tone.2, opacity: alpha)
    }

    private static func shaded(_ tone: Tone) -> Tone {
        (tone.0 * (1 - sideShade), tone.1 * (1 - sideShade), tone.2 * (1 - sideShade))
    }

    private static func archScale(_ tau: Double) -> Double {
        if tau < 0 { return arriveScale + (1 - arriveScale) * smooth((tau + grow) / grow) }
        let u = min(1, max(0, (tau - pushAt) / push))
        return 1 + (archZoom - 1) * u * u
    }

    private static func archAlpha(_ tau: Double) -> Double {
        tau < 0 ? smooth((tau + arrive) / arriveFade) : 1 - smooth((tau - archOutAt) / archOut)
    }

    private static func chartScale(_ tau: Double) -> Double {
        if tau < 0 { return archScale(tau) }
        if tau < creepAt { return 1 + (full - 1) * smooth((tau - pushAt) / (creepAt - pushAt)) }
        let span = leaveAt - creepAt
        if tau < leaveAt {
            let u = (tau - creepAt) / span
            return full + creep * u * u
        }
        // It leaves at the speed the creep ended with, and gathers more.
        let on = tau - leaveAt
        return full + creep + 2 * creep / span * on + leavePush * on * on
    }

    /// An arch's outline: a rectangle with a round top, standing on the arch's base.
    private static func outline(_ path: inout Path, _ width: Double, _ height: Double) {
        let half = width / 2
        let spring = archBase - height + half
        path.move(to: CGPoint(x: -half, y: archBase))
        path.addLine(to: CGPoint(x: -half, y: spring))
        path.addArc(center: CGPoint(x: 0, y: spring), radius: half, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
        path.addLine(to: CGPoint(x: half, y: archBase))
        path.closeSubpath()
    }

    /// One archway with its chart and its hills, `tau` seconds after it stood at rest (before, if negative).
    private static func paintGeneration(_ context: GraphicsContext, _ tau: Double, _ middle: Double, _ horizon: Double) {
        guard tau >= -arrive else { return }
        let arriving = tau < 0 ? archAlpha(tau) : 1

        let hills = tau < 0 ? smooth((tau + arrive) / hillsIn) : 1 - smooth((tau - pushAt) / hillsOut)
        if hills > 0 {
            let y = horizon + 8 + (tau < 0 ? 0 : hillsDrop * smooth((tau - pushAt) / hillsOut))
            var ridge = Path()
            ridge.move(to: CGPoint(x: middle - 230, y: y))
            ridge.addQuadCurve(to: CGPoint(x: middle + 40, y: y), control: CGPoint(x: middle - 90, y: y - 34))
            ridge.closeSubpath()
            ridge.move(to: CGPoint(x: middle - 60, y: y))
            ridge.addQuadCurve(to: CGPoint(x: middle + 240, y: y), control: CGPoint(x: middle + 100, y: y - 42))
            ridge.closeSubpath()
            context.fill(ridge, with: .color(tint(hill, hills)))
        }

        // The chart, a bar at a time.
        let scale = chartScale(tau)
        let unit = Self.unit * scale
        let ground = horizon + Self.ground * scale
        let green = tau < 0 ? 0 : smooth((tau - pushAt) / greenIn)
        // A face of a bar between `top` and `bottom`, shaded from the bar's `head` down to the
        // ground. A side face has a slant: its top runs down and away.
        func bar(_ left: Double, _ right: Double, _ top: Double, _ bottom: Double, _ head: Double, _ from: Color, _ to: Color, _ slant: Double) {
            var face = Path()
            face.addLines([CGPoint(x: left, y: top), CGPoint(x: right, y: top + slant), CGPoint(x: right, y: bottom), CGPoint(x: left, y: bottom)])
            face.closeSubpath()
            context.fill(face, with: .linearGradient(Gradient(colors: [from, to]), startPoint: CGPoint(x: 0, y: head), endPoint: CGPoint(x: 0, y: ground)))
        }
        for (index, item) in bars.enumerated() {
            let key = item.kind == .key
            let fade = key ? greenMist : mist
            let foot = arriving * (1 - smooth((tau - fade[0]) / fade[1]))
            let head = arriving * (1 - smooth((tau - fade[2]) / fade[3]))
            guard head > 0 else { continue }
            let left = middle + item.from * unit
            let right = middle + item.to * unit
            let top = ground - item.height * unit
            let side = (right - left) * Self.side
            let grey = greys[key ? Kind.mid.rawValue : item.kind.rawValue]
            // A key bar is green down to `turn`, and still grey below it.
            let turn = key ? top + (ground - top) * green : top
            if turn > top {
                bar(left, right - side, top, turn, top, tint(Self.green, head), tint(greenPale, foot), 0)
                bar(right - side, right, top, turn, top, tint(shaded(Self.green), head), tint(shaded(greenPale), foot), side * 0.6)
            }
            if turn < ground {
                bar(left, right - side, turn, ground, top, tint(grey, head), tint(grey, foot), 0)
                bar(right - side, right, turn, ground, top, tint(shaded(grey), head), tint(shaded(grey), foot), turn > top ? 0 : side * 0.6)
            }
            if key, tau > pushAt, tau < leaveAt + markerOut {
                // The marker drops into place, bobs, and sinks onto its bar at the end.
                let sink = smooth((tau - leaveAt) / markerOut)
                let lift = marker.lift + markerDrop * (1 - green) - marker.lift * sink
                let bob = markerBob * sin(tau / markerBobEvery * tau2 + Double(index)) * smooth(tau - pushAt - greenIn) * (1 - sink)
                let x = (left + right - side) / 2
                let tip = top - lift * unit + bob
                var shape = Path()
                shape.addLines([
                    CGPoint(x: x - marker.width * unit / 2, y: tip - marker.height * unit),
                    CGPoint(x: x + marker.width * unit / 2, y: tip - marker.height * unit),
                    CGPoint(x: x, y: tip),
                ])
                shape.closeSubpath()
                context.fill(shape, with: .color(tint(markerGreen, green * (1 - sink))))
            }
        }

        // The arch in front of it: three faces, each the gap between two outlines, on a plinth.
        let alpha = archAlpha(tau)
        guard alpha > 0 else { return }
        var layer = context
        layer.translateBy(x: middle, y: horizon)
        let zoom = archScale(tau)
        layer.scaleBy(x: zoom, y: zoom)
        layer.fill(Path(CGRect(x: -plinth.width / 2, y: archBase, width: plinth.width, height: plinth.height)), with: .color(tint(plinthGrey, alpha)))
        for step in archFaces.indices {
            var ring = Path()
            outline(&ring, archWidths[step], archHeights[step])
            outline(&ring, archWidths[step + 1], archHeights[step + 1])
            layer.fill(ring, with: .color(tint(archFaces[step], alpha)), style: FillStyle(eoFill: true))
        }
    }

    private static let tau2 = Double.pi * 2

    /// Paints the scene on a screen of the given size, `tau` seconds into a round (0 up to `loop`),
    /// with the horizon at `horizon` points from the top.
    static func paint(_ context: GraphicsContext, size: CGSize, horizon: Double, tau: Double) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(sky, startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
        // The one coming in is further away, so it is behind the one going out.
        paintGeneration(context, tau - loop, size.width / 2, horizon)
        paintGeneration(context, tau, size.width / 2, horizon)
    }
}
