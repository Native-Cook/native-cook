import SwiftUI

/// The drawn objects: the film box (a cuboid projected in perspective, with two flaps on hinges),
/// the canister (a cylinder drawn in thin strips so its label can turn), and the spinner. The
/// lettering on both is cut from the glyph outlines below, the same in the three stacks.
enum FilmBoxUnboxingArt {
    // The box, in points: long side, height, depth.
    static let boxW = 150.0
    static let boxH = 96.0
    static let boxD = 84.0
    /// Distance from the eye to the screen, in points: the strength of the perspective.
    private static let focal = 700.0
    /// The box's resting turn: from the left, from above, and tipped up to the right (degrees).
    private static let boxYaw = -30.0
    private static let boxPitch = 22.0
    private static let boxRoll = -20.0
    /// The gap between the two closed flaps, showing the inside.
    private static let seam = 0.8

    // The canister, in points: length, radius of the body and of the end caps, length of a cap.
    private static let canLen = 156.0
    private static let canR = 44.0
    private static let capR = 48.0
    private static let capLen = 13.0
    /// How much of an end the eye sees: the width of an end's ellipse over its height.
    private static let canView = 0.4
    /// The canister lies on a diagonal, its open end up and to the left (degrees).
    private static let canTilt = 30.0
    private static let strips = 36
    // Where things sit round the canister, in radians from the middle of the label.
    private static let labelFrom = -1.2
    private static let stripeFrom = 0.8
    private static let stripeTo = 1.15
    private static let filmStep = 0.21
    /// "ALIEN" is this many points per glyph unit (a glyph is 10 units tall), centred at `textAt`.
    private static let textScale = 2.6
    private static let textAt = -0.2

    // Colours as 0–255 channels, mixed and lit per face.
    private static let kraft = [217.0, 164, 65]
    private static let kraftInside = [104.0, 72, 30]
    private static let amber = [255.0, 184, 0]
    private static let ink = [22.0, 18, 12]
    private static let red = [214.0, 58, 46]
    private static let paper = [246.0, 241, 228]
    private static let mint = [168.0, 230, 192]
    private static let mintRim = [207.0, 244, 222]
    private static let glass = [10.0, 32, 22]
    private static let silver = [233.0, 236, 234]
    private static let labelStripe = [20.0, 20, 20]
    private static let filmDark = [14.0, 52, 35]
    private static let filmLight = [34.0, 104, 68]
    private static let filmLime = [124.0, 214, 155]
    private static let spinnerGrey = [138.0, 138, 138]
    /// Light falls from the upper left, in front: the direction towards it.
    private static let light = [-0.3, -0.8, -0.5]
    private static let lightLength = (0.09 + 0.64 + 0.25).squareRoot()

    // Block capitals, 10 units tall, as outlines (x, y pairs); a second outline is a hole.
    private static let glyphs: [Character: (w: Double, c: [[Double]])] = [
        "A": (8.5, [[2.6, 0, 5.9, 0, 8.5, 10, 5.6, 10, 5.2, 8.2, 3.3, 8.2, 2.9, 10, 0, 10], [3.75, 5.8, 4.75, 5.8, 4.25, 3.4]]),
        "C": (7.5, [[2.4, 0, 7.5, 0, 7.5, 2.6, 3.6, 2.6, 3, 3.2, 3, 6.8, 3.6, 7.4, 7.5, 7.4, 7.5, 10, 2.4, 10, 0, 7.6, 0, 2.4]]),
        "E": (6.5, [[0, 0, 6.5, 0, 6.5, 2.5, 3, 2.5, 3, 3.8, 6, 3.8, 6, 6.2, 3, 6.2, 3, 7.5, 6.5, 7.5, 6.5, 10, 0, 10]]),
        "F": (7, [[0, 0, 7, 0, 7, 2.6, 3, 2.6, 3, 4, 6.2, 4, 6.2, 6.4, 3, 6.4, 3, 10, 0, 10]]),
        "G": (7.5, [[2.4, 0, 7.5, 0, 7.5, 2.6, 3.6, 2.6, 3, 3.2, 3, 6.8, 3.6, 7.4, 4.8, 7.4, 4.8, 6.4, 4, 6.4, 4, 4.4, 7.5, 4.4, 7.5, 10, 2.4, 10, 0, 7.6, 0, 2.4]]),
        "I": (3, [[0, 0, 3, 0, 3, 10, 0, 10]]),
        "L": (6.5, [[0, 0, 3, 0, 3, 7.4, 6.5, 7.4, 6.5, 10, 0, 10]]),
        "M": (10, [[0, 0, 3, 0, 5, 3.6, 7, 0, 10, 0, 10, 10, 7.2, 10, 7.2, 5, 5, 8.4, 2.8, 5, 2.8, 10, 0, 10]]),
        "N": (8, [[0, 0, 3, 0, 5.2, 4.6, 5.2, 0, 8, 0, 8, 10, 5, 10, 2.8, 5.4, 2.8, 10, 0, 10]]),
        "O": (8, [
            [2.4, 0, 5.6, 0, 8, 2.4, 8, 7.6, 5.6, 10, 2.4, 10, 0, 7.6, 0, 2.4],
            [3.4, 2.6, 4.6, 2.6, 5.2, 3.2, 5.2, 6.8, 4.6, 7.4, 3.4, 7.4, 2.8, 6.8, 2.8, 3.2],
        ]),
        "U": (7.5, [[0, 0, 3, 0, 3, 7.2, 4.5, 7.2, 4.5, 0, 7.5, 0, 7.5, 7.6, 5.1, 10, 2.4, 10, 0, 7.6]]),
        ".": (3, [[0, 7.2, 3, 7.2, 3, 10, 0, 10]]),
    ]
    private static let track = 0.8
    private static let space = 3.0
    /// Units the top of a letter leans right over its height.
    private static let italic = 0.2

    /// A word's outlines at (x, y), `scale` points per unit, leaning.
    private static func lettering(_ text: String, scale: Double, x: Double, y: Double) -> (contours: [[Double]], width: Double) {
        var out: [[Double]] = []
        var pen = 0.0
        for ch in text {
            if ch == " " {
                pen += space
                continue
            }
            guard let glyph = glyphs[ch] else { continue }
            for contour in glyph.c {
                var points: [Double] = []
                for i in stride(from: 0, to: contour.count, by: 2) {
                    points.append(x + (pen + contour[i] + (10 - contour[i + 1]) * italic) * scale)
                    points.append(y + contour[i + 1] * scale)
                }
                out.append(points)
            }
            pen += glyph.w + track
        }
        return (out, (pen - track) * scale)
    }

    private static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> [Double] {
        [x, y, x + w, y, x + w, y + h, x, y + h]
    }

    private static func disc(_ x: Double, _ y: Double, _ r: Double) -> [Double] {
        var points: [Double] = []
        for i in 0..<20 {
            let a = Double(i) / 20 * tau
            points.append(x + r * cos(a))
            points.append(y + r * sin(a))
        }
        return points
    }

    private struct Face {
        let idx: [Int]
        let n: [Double]
        let o: [Double]
        let u: [Double]
        let v: [Double]
    }

    private static let front = 0
    private static let left = 2

    // The six faces as corner indices (clockwise seen from outside), the outward normal, and a
    // frame for the print on it: origin, then unit vectors along and down the face.
    private static let a = boxW / 2
    private static let b = boxH / 2
    private static let c = boxD / 2
    private static let corners = [
        [-a, -b, -c], [a, -b, -c], [a, b, -c], [-a, b, -c],
        [-a, -b, c], [a, -b, c], [a, b, c], [-a, b, c],
    ]
    private static let faces = [
        Face(idx: [0, 1, 2, 3], n: [0, 0, -1], o: [-a, -b, -c], u: [1, 0, 0], v: [0, 1, 0]),
        Face(idx: [5, 4, 7, 6], n: [0, 0, 1], o: [a, -b, c], u: [-1, 0, 0], v: [0, 1, 0]),
        Face(idx: [4, 0, 3, 7], n: [-1, 0, 0], o: [-a, -b, c], u: [0, 0, -1], v: [0, 1, 0]),
        Face(idx: [1, 5, 6, 2], n: [1, 0, 0], o: [a, -b, -c], u: [0, 0, 1], v: [0, 1, 0]),
        Face(idx: [3, 2, 6, 7], n: [0, 1, 0], o: [-a, b, -c], u: [1, 0, 0], v: [0, 0, 1]),
    ]

    // The print: on the front a tab, the wordmark, a line of small capitals, the eye badge and a
    // barcode sticker; on the left end three lines, a small wordmark and a sticker.
    private static let print: [(face: Int, color: [Double], contours: [[Double]])] = {
        let bars = [12.5, 15, 18, 20, 23.5, 26, 29.5].map { rect($0, 76, 1.2, 10) }
        return [
            (front, ink, [rect(10, 12, 9, 38)]),
            (front, ink, lettering("FILM CO.", scale: 1.9, x: 28, y: 36).contours),
            (front, ink, lettering("ANALOGUE FILM", scale: 0.5, x: 30, y: 61).contours),
            (front, red, [disc(131, 18, 9.5)]),
            (front, paper, [disc(130.4, 18, 5)]),
            (front, ink, [disc(131, 18.4, 2.4)]),
            (front, paper, [rect(10, 74, 24, 14)]),
            (front, ink, bars),
            (left, ink, [rect(18, 30, 46, 2.4), rect(18, 36, 46, 2.4), rect(18, 42, 30, 2.4)]),
            (left, ink, lettering("FILM CO.", scale: 0.9, x: 18, y: 54).contours),
            (left, paper, [rect(18, 72, 22, 14)]),
        ]
    }()

    private static let alien = lettering("ALIEN", scale: 1, x: 0, y: 0)

    private static func color(_ c: [Double], _ light: Double, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: min(255, c[0] * light) / 255, green: min(255, c[1] * light) / 255, blue: min(255, c[2] * light) / 255, opacity: alpha)
    }

    private static func mix(_ a: [Double], _ b: [Double], _ t: Double) -> [Double] {
        [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]
    }

    private static func polygon(_ points: [Double]) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: points[0], y: points[1]))
        for i in stride(from: 2, to: points.count, by: 2) { path.addLine(to: CGPoint(x: points[i], y: points[i + 1])) }
        path.closeSubpath()
        return path
    }

    /// The rotation the box is drawn with: roll · pitch · yaw, as nine numbers row by row.
    private static func rotation(yaw: Double, pitch: Double, roll: Double) -> [Double] {
        let cy = cos(yaw), sy = sin(yaw), cp = cos(pitch), sp = sin(pitch), cr = cos(roll), sr = sin(roll)
        return [
            cr * cy - sr * sp * sy, -sr * cp, cr * sy + sr * sp * cy,
            sr * cy + cr * sp * sy, cr * cp, sr * sy - cr * sp * cy,
            -cp * sy, sp, cp * cy,
        ]
    }

    private static func turn(_ m: [Double], _ x: Double, _ y: Double, _ z: Double) -> [Double] {
        [m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z]
    }

    /// A turned point on the screen, the box centred at `center`.
    private static func onScreen(_ p: [Double], _ center: CGPoint) -> CGPoint {
        let k = focal / (focal + p[2])
        return CGPoint(x: center.x + p[0] * k, y: center.y + p[1] * k)
    }

    /// True when the outside of a surface through `p` with normal `n` (both turned) faces the eye.
    private static func facing(_ n: [Double], _ p: [Double]) -> Bool {
        n[0] * p[0] + n[1] * p[1] + n[2] * (p[2] + focal) < 0
    }

    private static func lit(_ n: [Double]) -> Double {
        max(0, (n[0] * light[0] + n[1] * light[1] + n[2] * light[2]) / lightLength)
    }

    private struct Surface {
        let depth: Double
        let points: [Double]
        let color: [Double]
        let light: Double
        let face: Int
    }

    struct Pose {
        /// Degrees added to the resting turn.
        var yaw: Double
        var pitch: Double
        var roll: Double
        /// Degrees the flaps stand open.
        var flap: Double
        var glow: Double
        var sweep: Double
        var dive: Double
    }

    private static func amberColor(_ alpha: Double) -> Color {
        Color(.sRGB, red: 1, green: 0.72, blue: 0, opacity: alpha)
    }

    /// The box centred at `center`; `dive` zooms into its opening and towards `focus`.
    static func drawBox(_ context: GraphicsContext, center: CGPoint, focus: CGPoint, pose: Pose) {
        let rad = Double.pi / 180
        let m = rotation(yaw: (boxYaw + pose.yaw) * rad, pitch: (boxPitch + pose.pitch) * rad, roll: (boxRoll + pose.roll) * rad)
        let turned = corners.map { turn(m, $0[0], $0[1], $0[2]) }
        let screen = turned.map { onScreen($0, center) }
        // The opening: its middle is where the dive goes.
        let top = [0, 1, 5, 4]
        let ox = top.reduce(0.0) { $0 + screen[$1].x / 4 }
        let oy = top.reduce(0.0) { $0 + screen[$1].y / 4 }

        var ctx = context
        if pose.dive > 0 {
            let scale = 1 + pose.dive * (FilmBoxUnboxingTimeline.diveScale - 1)
            ctx.translateBy(x: ox + (focus.x - ox) * pose.dive, y: oy + (focus.y - oy) * pose.dive)
            ctx.scaleBy(x: scale, y: scale)
            ctx.translateBy(x: -ox, y: -oy)
        }

        var inside: [Surface] = []
        var outside: [Surface] = []
        for (f, face) in faces.enumerated() {
            let n = turn(m, face.n[0], face.n[1], face.n[2])
            let mid = turn(m, face.n[0] * a, face.n[1] * b, face.n[2] * c)
            var points: [Double] = []
            var depth = 0.0
            for k in face.idx {
                points += [screen[k].x, screen[k].y]
                depth += turned[k][2] / 4
            }
            if facing(n, mid) {
                outside.append(Surface(depth: depth, points: points, color: kraft, light: 0.58 + 0.5 * lit(n), face: f))
            } else {
                // Inside, the floor takes the most of the light that comes out of the box.
                let glow = pose.glow * (f == 4 ? 0.85 : 0.6)
                inside.append(Surface(depth: depth, points: points, color: mix(kraftInside, amber, glow), light: 0.35 + 0.4 * lit([-n[0], -n[1], -n[2]]) + glow, face: -1))
            }
        }
        // Far to near, so that nearer surfaces paint over farther ones.
        inside.sort { $0.depth > $1.depth }
        outside.sort { $0.depth > $1.depth }

        for s in inside { ctx.fill(polygon(s.points), with: .color(color(s.color, s.light))) }

        let opening = polygon([screen[0].x, screen[0].y, screen[1].x, screen[1].y, screen[5].x, screen[5].y, screen[4].x, screen[4].y])
        if pose.glow > 0 {
            var lightCtx = ctx
            lightCtx.clip(to: opening)
            lightCtx.fill(opening, with: .radialGradient(
                Gradient(colors: [amberColor(0.85 * pose.glow), amberColor(0)]),
                center: CGPoint(x: ox, y: oy), startRadius: 0, endRadius: boxW * 0.6
            ))
        }

        var walls = Path()
        for s in outside {
            let path = polygon(s.points)
            ctx.fill(path, with: .color(color(s.color, s.light)))
            walls.addPath(path)
            // The print on this face, lit like the face.
            let face = faces[s.face]
            for item in print where item.face == s.face {
                var path = Path()
                for contour in item.contours {
                    for k in stride(from: 0, to: contour.count, by: 2) {
                        let u = contour[k], v = contour[k + 1]
                        let p = turn(
                            m,
                            face.o[0] + u * face.u[0] + v * face.v[0],
                            face.o[1] + u * face.u[1] + v * face.v[1],
                            face.o[2] + u * face.u[2] + v * face.v[2]
                        )
                        let q = onScreen(p, center)
                        if k == 0 { path.move(to: q) } else { path.addLine(to: q) }
                    }
                    path.closeSubpath()
                }
                ctx.fill(path, with: .color(color(item.color, min(1.05, s.light))), style: FillStyle(eoFill: true))
            }
        }

        // A streak of light crosses the walls while the box opens.
        if pose.sweep > 0 && pose.sweep < 1 {
            let left = screen.map(\.x).min() ?? 0
            let right = screen.map(\.x).max() ?? 0
            let t = pose.sweep
            let x = left - 40 + (right - left + 80) * (t * t * (3 - 2 * t))
            let alpha = 0.6 * sin(.pi * t)
            var streak = ctx
            streak.clip(to: walls)
            streak.translateBy(x: x, y: center.y)
            streak.rotate(by: .degrees(20))
            streak.translateBy(x: -x, y: -center.y)
            streak.fill(
                Path(CGRect(x: x - 26, y: center.y - 220, width: 52, height: 440)),
                with: .linearGradient(
                    Gradient(colors: [amberColor(0), amberColor(alpha), amberColor(0)]),
                    startPoint: CGPoint(x: x - 26, y: 0), endPoint: CGPoint(x: x + 26, y: 0)
                )
            )
        }

        // The flaps, hinged on the long top edges. Closed, they are the lid.
        let angle = pose.flap * rad
        let d = c - seam
        let lift = d * sin(angle)
        let reach = d * cos(angle)
        let shapes = [
            [[-a, -b, -c], [a, -b, -c], [a, -b - lift, -c + reach], [-a, -b - lift, -c + reach]],
            [[a, -b, c], [-a, -b, c], [-a, -b - lift, c - reach], [a, -b - lift, c - reach]],
        ]
        let normals = [[0, -cos(angle), -sin(angle)], [0, -cos(angle), sin(angle)]]
        var flaps: [Surface] = []
        for f in 0..<2 {
            var points: [Double] = []
            var depth = 0.0
            var mid = [0.0, 0, 0]
            for corner in shapes[f] {
                let p = turn(m, corner[0], corner[1], corner[2])
                let q = onScreen(p, center)
                points += [q.x, q.y]
                depth += p[2] / 4
                mid = [mid[0] + p[0] / 4, mid[1] + p[1] / 4, mid[2] + p[2] / 4]
            }
            let n = turn(m, normals[f][0], normals[f][1], normals[f][2])
            if facing(n, mid) {
                flaps.append(Surface(depth: depth, points: points, color: kraft, light: 0.58 + 0.5 * lit(n), face: -1))
            } else {
                flaps.append(Surface(depth: depth, points: points, color: mix(kraftInside, amber, pose.glow * 0.4), light: 0.5 + 0.4 * lit([-n[0], -n[1], -n[2]]), face: -1))
            }
        }
        flaps.sort { $0.depth > $1.depth }
        for s in flaps { ctx.fill(polygon(s.points), with: .color(color(s.color, s.light))) }

        // Light spills out of the opening.
        if pose.glow > 0 {
            let r = boxW * 0.9
            ctx.fill(Path(ellipseIn: CGRect(x: ox - r, y: oy - r, width: r * 2, height: r * 2)), with: .radialGradient(
                Gradient(colors: [amberColor(0.35 * pose.glow), amberColor(0)]),
                center: CGPoint(x: ox, y: oy), startRadius: 0, endRadius: r
            ))
        }
    }

    /// Wraps an angle into -π…π.
    private static func wrap(_ a: Double) -> Double {
        a - tau * (a / tau).rounded()
    }

    /// What is printed round the canister `s` radians from the middle of the label.
    private static func surface(_ s: Double) -> [Double] {
        let a = wrap(s)
        if a >= labelFrom && a < stripeFrom { return silver }
        if a >= stripeFrom && a < stripeTo { return labelStripe }
        // The film behind the window: bands along the axis, every third a bright one.
        var past = a - stripeTo
        if past < 0 { past += tau }
        let band = Int(floor(past / filmStep))
        if band % 6 == 3 { return filmLime }
        return band % 2 == 0 ? filmDark : filmLight
    }

    /// Light on the round side, ψ radians from the line facing the eye, with a highlight above.
    private static func roundLight(_ psi: Double) -> Double {
        0.42 + 0.58 * max(0, cos(psi + 0.45))
    }

    private static func shine(_ psi: Double) -> Double {
        let k = (psi + 0.75) / 0.2
        return exp(-k * k)
    }

    /// A drum from x0 to x1 of radius r, seen a little from its left end, in strips round its
    /// front half. `spin` turns the print; `printed` is false for a plain mint cap.
    private static func drum(_ ctx: GraphicsContext, _ x0: Double, _ x1: Double, _ r: Double, spin: Double, printed: Bool) {
        let e = r * canView
        let step = Double.pi / Double(strips)
        for i in 0..<strips {
            // Each strip reaches a little into the next, so no seam shows between them.
            let a = -Double.pi / 2 + Double(i) * step - 0.01
            let b = a + step + 0.02
            let mid = a + step / 2 + 0.01
            let surfaceColor = printed ? surface(mid + spin) : mint
            let light = roundLight(mid)
            let gloss = shine(mid) * (printed ? 0.55 : 0.4)
            let base = [min(255, surfaceColor[0] * light), min(255, surfaceColor[1] * light), min(255, surfaceColor[2] * light)]
            let ca = e * cos(a)
            let cb = e * cos(b)
            ctx.fill(
                polygon([x0 + ca, r * sin(a), x1 + ca, r * sin(a), x1 + cb, r * sin(b), x0 + cb, r * sin(b)]),
                with: .color(color(mix(base, [255, 255, 255], gloss), 1))
            )
        }
    }

    /// The canister centred at `center`, `spin` radians round its axis.
    static func drawCanister(_ context: GraphicsContext, center: CGPoint, scale: Double, spin: Double) {
        let half = canLen / 2
        var ctx = context
        ctx.translateBy(x: center.x, y: center.y)
        ctx.rotate(by: .degrees(canTilt))
        ctx.scaleBy(x: scale, y: scale)

        // A faint mint glow behind it.
        let glowR = half + 40
        ctx.fill(Path(ellipseIn: CGRect(x: -glowR, y: -glowR, width: glowR * 2, height: glowR * 2)), with: .radialGradient(
            Gradient(colors: [Color(.sRGB, red: 0.66, green: 0.9, blue: 0.75, opacity: 0.12), Color(.sRGB, red: 0.66, green: 0.9, blue: 0.75, opacity: 0)]),
            center: .zero, startRadius: 0, endRadius: glowR
        ))

        drum(ctx, half - capLen, half, capR, spin: spin, printed: false)
        drum(ctx, -half + capLen, half - capLen, canR, spin: spin, printed: true)

        // "ALIEN" wraps round the side: each point goes to its angle on the round surface, folded
        // flat at the edge it turns behind.
        if cos(wrap(textAt - spin)) > 0.15 {
            let e = canR * canView
            var path = Path()
            for contour in alien.contours {
                let count = contour.count / 2
                for k in 0..<count {
                    let x0 = contour[k * 2], y0 = contour[k * 2 + 1]
                    let x1 = contour[(k + 1) % count * 2], y1 = contour[(k + 1) % count * 2 + 1]
                    // Three steps along each edge keep the long strokes bent round the drum.
                    for j in 0..<3 {
                        let t = Double(j) / 3
                        let gx = x0 + (x1 - x0) * t
                        let gy = y0 + (y1 - y0) * t
                        let psi = min(Double.pi / 2, max(-Double.pi / 2, wrap(textAt + (gy - 5) * textScale / canR - spin)))
                        let point = CGPoint(x: (gx - alien.width / 2) * textScale + e * cos(psi), y: canR * sin(psi))
                        if k == 0 && j == 0 { path.move(to: point) } else { path.addLine(to: point) }
                    }
                }
                path.closeSubpath()
            }
            ctx.fill(path, with: .color(color(ink, 1)), style: FillStyle(eoFill: true))
        }

        drum(ctx, -half, -half + capLen, capR, spin: spin, printed: false)

        // The open end: a mint rim round dark glass, and the spool's hub inside.
        let er = capR * canView
        func oval(_ k: Double) -> Path {
            Path(ellipseIn: CGRect(x: -half - er * k, y: -capR * k, width: er * 2 * k, height: capR * 2 * k))
        }
        ctx.fill(oval(1), with: .color(color(mintRim, 1)))
        ctx.fill(oval(0.74), with: .color(color(glass, 1)))
        ctx.fill(oval(0.26), with: .color(color(mint, 1, 0.85)))
        ctx.fill(oval(0.1), with: .color(color(glass, 1)))
    }

    /// Eight ticks round a point, the brightest one stepping round ten times a second.
    static func drawSpinner(_ ctx: GraphicsContext, at point: CGPoint, time: Double, alpha: Double) {
        let head = Int(floor(time * 10)) % 8
        for i in 0..<8 {
            let behind = (head - i + 8) % 8
            let a = Double(i) / 8 * tau - .pi / 2
            var path = Path()
            path.move(to: CGPoint(x: point.x + 3.6 * cos(a), y: point.y + 3.6 * sin(a)))
            path.addLine(to: CGPoint(x: point.x + 7.2 * cos(a), y: point.y + 7.2 * sin(a)))
            ctx.stroke(path, with: .color(color(spinnerGrey, 1, alpha * max(0.2, 1 - Double(behind) * 0.12))), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
        }
    }
}
