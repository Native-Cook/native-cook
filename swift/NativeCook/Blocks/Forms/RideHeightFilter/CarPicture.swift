import SwiftUI

enum RideHeightFilterCarMetrics {
    static let boxWidth = 496.0
    static let boxHeight = 250.0
    static let ground = 216.0
    static let wheel = CGPoint(x: 422, y: 155)
    static let tyre = 61.0
    static let rim = 49.0
    static let fog = CGPoint(x: 289, y: 151)
    static let fogRadius = 10.0
    static let spokes = 5
    static let spokeTurn = -95.0
    static let leaves = 520
    static let ragged = 3.0
    static let shadowFade = 0.45
}

private enum RideHeightFilterCarDrawing {
    private typealias S = RideHeightFilterCarMetrics
    static let body = path("M500 36C470 38 440 42 410 48C370 56 330 64 300 74C288 78 279 84 272 92C266 104 260 120 256 140C254 152 251 162 249 171L245.5 174.5L250 179.5C280 184 320 188 350 191.5L356.2 181.9A70 70 0 1 1 491.3 167.7L492 188L500 188Z")
    static let archEdge = path("M356.2 181.9A70 70 0 1 1 491.3 167.7")
    static let liner = path("M356.2 181.9A70 70 0 1 1 491.3 167.7L492 188L354 188Z")
    static let shoulder = path("M500 36C470 38 440 42 410 48C370 56 330 64 300 74C288 78 279 84 272 92C300 88 330 84 352 79C400 70 450 64 500 60Z")
    static let crease = path("M352 79C400 70 450 64 500 60")
    static let bumperLine = path("M262 131C290 128 328 120 354 104")
    static let underside = path("M246 176C280 184 320 188 352 192")
    static let splitter = path("M249 171L245.5 174.5L250 179.5C280 184 320 188 350 191.5L351.5 186C320 182.5 285 178 257 172.5Z")
    static let nose = path("M272 92C266 104 260 120 256 140C254 152 251 162 249 171L275 176C278 150 286 118 300 92Z")
    static let intake = path("M265 142L309 132C312 131.4 314 132.6 314.8 135L321 159C321.6 161.4 321 163.6 319.4 165.4L313.6 172C312 173.8 309.8 174.4 307.6 173.6L270 161C267.6 160.2 266.2 158.4 266 156L264.4 144.6C264.2 143.2 264.2 142.2 265 142Z")
    static let lens = path("M277 97C295 94 318 90 334 86C341 84 347 81 352.5 78.5L357.5 77.5C352 86 346 95 340 102.5C322 104 300 105 281 105.5C277 104 276 100 277 97Z")
    static let lensBrow = path("M277 97C295 94 318 90 334 86C341 84 347 81 352.5 78.5")
    static let headlight = path("M331 104C338 96 346 86 352.5 78.5L357.5 77.5C352 86 346 95 340 102.5Z")
    static let headlightLine = path("M336 100C342 93 348 85 353.5 79")
    static let mound = path("M-4 134C-6 112 2 92 20 84C28 68 52 60 66 70C82 62 104 70 108 88C122 94 128 114 124 134Z")
    static let planterSide = path("M132 132L139 130L139 178L132 182Z")
    static let leafTones = [Color(hex: 0xc1bfc2), Color(hex: 0xd2d0d3), Color(hex: 0xe1dfe2), Color(hex: 0xf0eef1)]
    static let slats: Path = {
        var path = Path()
        var y = 99.0
        while y <= 160 {
            let x = 268 - (y - 99) * 0.26
            path.move(to: CGPoint(x: x + 1.5, y: y)); path.addLine(to: CGPoint(x: x + 8.5, y: y - 0.6))
            y += 3.4
        }
        return path
    }()
    static let mesh: Path = {
        var path = Path(); var x = 220.0
        while x < 330 {
            path.move(to: CGPoint(x: x, y: 180)); path.addLine(to: CGPoint(x: x + 52, y: 128))
            path.move(to: CGPoint(x: x + 52, y: 180)); path.addLine(to: CGPoint(x: x, y: 128))
            x += 3.6
        }
        return path
    }()
    static let spokePath: Path = {
        var path = Path()
        for index in 0..<S.spokes {
            let at = (S.spokeTurn + Double(index) * 360 / Double(S.spokes)) * .pi / 180
            func point(_ radius: Double, _ offset: Double, _ twist: Double) -> CGPoint {
                let angle = at + twist
                return CGPoint(x: cos(angle) * radius - sin(angle) * offset, y: sin(angle) * radius + cos(angle) * offset)
            }
            path.move(to: point(8, -6.5, 0)); path.addLine(to: point(46, -11.5, 0.1))
            path.addLine(to: point(46, 11.5, 0.1)); path.addLine(to: point(8, 6.5, 0)); path.closeSubpath()
        }
        return path
    }()
    static let spokeEdges: Path = {
        var path = Path()
        for index in 0..<S.spokes {
            let at = (S.spokeTurn + Double(index) * 360 / Double(S.spokes)) * .pi / 180
            let twisted = at + 0.1
            path.move(to: CGPoint(x: cos(at) * 10 - sin(at) * 6.5, y: sin(at) * 10 + cos(at) * 6.5))
            path.addLine(to: CGPoint(x: cos(twisted) * 45 - sin(twisted) * 11.5, y: sin(twisted) * 45 + cos(twisted) * 11.5))
        }
        return path
    }()
    static let leafPaths: [Path] = {
        var paths = Array(repeating: Path(), count: leafTones.count)
        var seed: Int64 = 7
        func random() -> Double {
            seed = (seed * 16807) % 2147483647
            return Double(seed) / 2147483647
        }
        for _ in 0..<S.leaves {
            let x = -4 + random() * 130; let y = 62 + random() * 74
            let turn = random() * .pi; let long = 3.2 + random() * 3.2
            let wide = long * (0.36 + random() * 0.14); let shade = random()
            guard mound.contains(CGPoint(x: x, y: y)) || mound.contains(CGPoint(x: x, y: y + S.ragged)) else { continue }
            let light = 0.62 - (x - 60) / 260 - (y - 98) / 110 + (shade - 0.5) * 0.9
            let tone = max(0, min(leafTones.count - 1, Int(floor(light * Double(leafTones.count)))))
            let cosine = cos(turn); let sine = sin(turn)
            paths[tone].move(to: CGPoint(x: x - cosine * long, y: y - sine * long))
            paths[tone].addQuadCurve(to: CGPoint(x: x + cosine * long, y: y + sine * long), control: CGPoint(x: x - sine * wide * 2, y: y + cosine * wide * 2))
            paths[tone].addQuadCurve(to: CGPoint(x: x - cosine * long, y: y - sine * long), control: CGPoint(x: x + sine * wide * 2, y: y - cosine * wide * 2))
            paths[tone].closeSubpath()
        }
        return paths
    }()

    /// The source arcs are unrotated circles. Their SVG endpoint flags determine the exact centre.
    private static func path(_ source: String) -> Path {
        let pattern = try! NSRegularExpression(pattern: "[MCLAZ]|-?[0-9]+(?:\\.[0-9]+)?")
        let ns = source as NSString
        let tokens = pattern.matches(in: source, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range) }
        var result = Path(); var index = 0; var command = "M"; var point = CGPoint.zero
        func number() -> Double { defer { index += 1 }; return Double(tokens[index])! }
        while index < tokens.count {
            if ["M", "C", "L", "A", "Z"].contains(tokens[index]) { command = tokens[index]; index += 1 }
            switch command {
            case "M": point = CGPoint(x: number(), y: number()); result.move(to: point); command = "L"
            case "L": point = CGPoint(x: number(), y: number()); result.addLine(to: point)
            case "C":
                let c1 = CGPoint(x: number(), y: number()); let c2 = CGPoint(x: number(), y: number())
                point = CGPoint(x: number(), y: number()); result.addCurve(to: point, control1: c1, control2: c2)
            case "A":
                var radius = number(); _ = number(); _ = number()
                let large = number() != 0; let sweep = number() != 0
                let end = CGPoint(x: number(), y: number())
                let dx = (point.x - end.x) / 2; let dy = (point.y - end.y) / 2
                let square = dx * dx + dy * dy
                radius = max(radius, sqrt(square))
                let factor = (large == sweep ? -1.0 : 1.0) * sqrt(max(0, (radius * radius - square) / square))
                let centre = CGPoint(x: (point.x + end.x) / 2 + factor * dy, y: (point.y + end.y) / 2 - factor * dx)
                let startAngle = atan2(point.y - centre.y, point.x - centre.x)
                var delta = atan2(end.y - centre.y, end.x - centre.x) - startAngle
                if sweep && delta < 0 { delta += tau }
                if !sweep && delta > 0 { delta -= tau }
                result.addArc(center: centre, radius: radius, startAngle: .radians(startAngle), endAngle: .radians(startAngle + delta), clockwise: !sweep)
                point = end
            case "Z": result.closeSubpath()
            default: break
            }
        }
        return result
    }
    static func rounded(_ rect: CGRect, radius: Double) -> Path { RoundedRectangle(cornerRadius: radius, style: .circular).path(in: rect) }
    static func circle(_ centre: CGPoint, _ radius: Double) -> Path { Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2, height: radius * 2)) }
    static func fill(_ path: Path, color: Color, in context: GraphicsContext, blur: Double = 0) {
        var copy = context; if blur > 0 { copy.addFilter(.blur(radius: blur)) }
        copy.fill(path, with: .color(color))
    }
    static func stroke(_ path: Path, color: Color, width: Double, in context: GraphicsContext, blur: Double = 0, cap: CGLineCap = .butt, join: CGLineJoin = .miter, dy: Double = 0) {
        var copy = context; if blur > 0 { copy.addFilter(.blur(radius: blur)) }
        copy.translateBy(x: 0, y: dy)
        copy.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: cap, lineJoin: join))
    }
    static func gradient(_ hex: [UInt32], positions: [Double]? = nil) -> Gradient {
        Gradient(stops: hex.enumerated().map { .init(color: Color(hex: $0.element), location: positions?[$0.offset] ?? Double($0.offset) / Double(hex.count - 1)) })
    }
    static func linear(_ path: Path, colors: [UInt32], positions: [Double]? = nil, from: CGPoint, to: CGPoint, in context: GraphicsContext) {
        context.fill(path, with: .linearGradient(gradient(colors, positions: positions), startPoint: from, endPoint: to))
    }
    static func radial(_ path: Path, colors: [UInt32], positions: [Double]? = nil, centre: CGPoint, radius: Double, in context: GraphicsContext) {
        context.fill(path, with: .radialGradient(gradient(colors, positions: positions), center: centre, startRadius: 0, endRadius: radius))
    }
}

/// The front quarter of a car beside a kerb. Only its body rides; the wheel and planting stay put.
struct RideHeightFilterCarPicture: View {
    var width: Double
    var lift: Double
    var pitch: Double
    var maxLift: Double
    private typealias S = RideHeightFilterCarMetrics
    private typealias D = RideHeightFilterCarDrawing

    var body: some View {
        Canvas { context, _ in
            drawGround(in: context)
            let fraction = min(1, max(0, lift / maxLift))
            D.fill(Path(ellipseIn: CGRect(x: 262, y: 208, width: 250, height: 13)), color: .black.opacity(0.36 * (1 - S.shadowFade * fraction)), in: context, blur: 5 + 4 * fraction)
            D.fill(Path(ellipseIn: CGRect(x: 384, y: 211, width: 82, height: 8)), color: .black.opacity(0.3), in: context, blur: 3)
            var body = context
            body.translateBy(x: S.wheel.x, y: S.wheel.y - lift)
            body.rotate(by: .radians(pitch))
            body.translateBy(x: -S.wheel.x, y: -S.wheel.y)
            drawBody(in: body)
            var wheel = context; wheel.translateBy(x: S.wheel.x, y: S.wheel.y)
            drawWheel(in: wheel)
        }
        // Scaling the complete canvas also scales its mask sigmas from reference-box units.
        .frame(width: S.boxWidth, height: S.boxHeight)
        .clipped()
        .scaleEffect(width / S.boxWidth, anchor: .topLeading)
        .frame(width: width, height: S.boxHeight * width / S.boxWidth, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private func drawGround(in context: GraphicsContext) {
        D.fill(D.mound, color: Color(hex: 0xaeacaf), in: context)
        for index in D.leafPaths.indices { D.fill(D.leafPaths[index], color: D.leafTones[index], in: context) }
        D.linear(Path(CGRect(x: -2, y: 132, width: 134, height: 50)), colors: [0xd9d7da, 0xcfcdd0], from: CGPoint(x: 0, y: 132), to: CGPoint(x: 0, y: 182), in: context)
        D.fill(D.planterSide, color: Color(hex: 0xc1bfc2), in: context)
        D.fill(Path(CGRect(x: -2, y: 131, width: 134, height: 2.2)), color: Color(hex: 0xecebee), in: context)
        D.fill(Path(CGRect(x: -4, y: 176, width: 146, height: 7)), color: .black.opacity(0.16), in: context, blur: 3)
        D.fill(Path(CGRect(x: -10, y: S.ground, width: 232, height: 3)), color: .black.opacity(0.3), in: context, blur: 2.5)
        let kerb = D.rounded(CGRect(x: -12, y: 174, width: 231, height: 44), radius: 9)
        D.linear(kerb, colors: [0xeae8eb, 0xdddbde, 0xc8c6c9], positions: [0, 0.45, 1], from: CGPoint(x: 0, y: 174), to: CGPoint(x: 0, y: 218), in: context)
        var clipped = context; clipped.clip(to: kerb)
        D.fill(Path(CGRect(x: 196, y: 174, width: 24, height: 44)), color: .black.opacity(0.045), in: clipped)
        D.fill(Path(CGRect(x: 195.4, y: 174, width: 1.1, height: 44)), color: .black.opacity(0.13), in: clipped)
        D.fill(Path(CGRect(x: -12, y: 174.6, width: 231, height: 1.4)), color: .white.opacity(0.7), in: clipped)
    }
    private func drawBody(in context: GraphicsContext) {
        D.linear(D.liner, colors: [0x313133, 0x48484a, 0x757376], positions: [0, 0.55, 1], from: CGPoint(x: 0, y: 88), to: CGPoint(x: 0, y: 190), in: context)
        D.linear(D.body, colors: [0xe9e7ea, 0xe3e1e4, 0xdedcdf, 0xd1cfd2], positions: [0, 0.3, 0.6, 1], from: CGPoint(x: 0, y: 36), to: CGPoint(x: 0, y: 192), in: context)
        var panels = context; panels.clip(to: D.body)
        D.fill(D.shoulder, color: Color(hex: 0xf4f2f5), in: panels, blur: 2.5)
        D.stroke(D.crease, color: .black.opacity(0.07), width: 5, in: panels, blur: 3, dy: 4)
        D.fill(D.nose, color: .black.opacity(0.05), in: panels, blur: 7)
        D.stroke(D.bumperLine, color: .black.opacity(0.085), width: 5, in: panels, blur: 3, dy: 3.5)
        D.stroke(D.bumperLine, color: .white.opacity(0.75), width: 1.4, in: panels, blur: 0.7, cap: .round)
        D.stroke(D.underside, color: .black.opacity(0.1), width: 12, in: panels, blur: 5)
        D.stroke(D.archEdge, color: .black.opacity(0.11), width: 9, in: panels, blur: 3)
        D.stroke(D.archEdge, color: .white.opacity(0.55), width: 2.2, in: panels, blur: 0.6)
        D.stroke(D.slats, color: .black.opacity(0.2), width: 1.3, in: context, cap: .round)
        D.linear(D.intake, colors: [0xa3a1a4, 0xb9b7ba], from: CGPoint(x: 0, y: 132), to: CGPoint(x: 0, y: 174), in: context)
        var intake = context; intake.clip(to: D.intake)
        D.stroke(D.mesh, color: .white.opacity(0.42), width: 0.8, in: intake)
        D.stroke(D.intake, color: .black.opacity(0.2), width: 5, in: intake, blur: 2.5)
        D.stroke(D.intake, color: .white.opacity(0.7), width: 1.2, in: context)
        D.fill(D.circle(CGPoint(x: S.fog.x, y: S.fog.y + 1.2), S.fogRadius + 1), color: .black.opacity(0.22), in: context, blur: 2)
        D.radial(D.circle(S.fog, S.fogRadius), colors: [0xfbf9fc, 0xdedcdf], centre: CGPoint(x: S.fog.x - 3, y: S.fog.y - 4), radius: S.fogRadius * 1.6, in: context)
        D.stroke(D.circle(S.fog, S.fogRadius - 3.2), color: .black.opacity(0.1), width: 1, in: context)
        D.fill(D.lens, color: Color(hex: 0xecebee), in: context)
        var lens = context; lens.clip(to: D.lens)
        D.stroke(D.lensBrow, color: .black.opacity(0.16), width: 7, in: lens, blur: 2.5)
        D.stroke(D.lens, color: .black.opacity(0.1), width: 0.8, in: context, join: .round)
        D.fill(D.headlight, color: Color(hex: 0xf9f7fa), in: context)
        D.stroke(D.headlight, color: .black.opacity(0.16), width: 1, in: context, join: .round)
        D.stroke(D.headlightLine, color: .black.opacity(0.14), width: 0.9, in: context)
        D.fill(D.splitter, color: Color(hex: 0xefedf0), in: context)
        D.stroke(D.splitter, color: .black.opacity(0.12), width: 0.8, in: context, join: .round)
    }
    private func drawWheel(in context: GraphicsContext) {
        D.radial(D.circle(.zero, S.tyre), colors: [0xf1eff2, 0xe2e0e3, 0xc9c7ca], positions: [0, 0.6, 1], centre: CGPoint(x: -16, y: -26), radius: S.tyre * 1.5, in: context)
        context.stroke(D.circle(.zero, S.tyre - 2.4), with: .color(.black.opacity(0.075)), style: StrokeStyle(lineWidth: 3.6, dash: [1.6, 4.54]))
        D.stroke(D.circle(.zero, S.rim + 1), color: .black.opacity(0.14), width: 1.6, in: context, blur: 1)
        D.radial(D.circle(.zero, S.rim - 2), colors: [0xb4b2b5, 0xaba9ac, 0x8b898c, 0x7e7c7f], positions: [0, 0.58, 0.62, 1], centre: .zero, radius: S.rim - 2, in: context)
        D.radial(D.spokePath, colors: [0xf4f2f5, 0xe1dfe2], centre: CGPoint(x: -10, y: -16), radius: S.rim * 1.5, in: context)
        D.stroke(D.spokeEdges, color: .black.opacity(0.14), width: 1.6, in: context, blur: 0.8)
        context.stroke(D.circle(.zero, S.rim - 2.5), with: .radialGradient(D.gradient([0xf5f3f6, 0xdcdadd]), center: CGPoint(x: -14, y: -22), startRadius: 0, endRadius: S.rim * 1.6), lineWidth: 5)
        D.radial(D.circle(.zero, 12.5), colors: [0xf3f1f4, 0xd9d7da], centre: CGPoint(x: -3, y: -5), radius: 16, in: context)
        for index in 0..<S.spokes {
            let at = (S.spokeTurn + 36 + Double(index) * 360 / Double(S.spokes)) * .pi / 180
            D.fill(D.circle(CGPoint(x: cos(at) * 8, y: sin(at) * 8), 1.7), color: Color(hex: 0xb3b1b4), in: context)
        }
        D.fill(D.circle(.zero, 3.6), color: Color(hex: 0xcfcdd0), in: context)
    }
}
