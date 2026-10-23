import SwiftUI

enum WeekendMoodCarouselFaces {
    private static let sleepyInk: UInt32 = 0x29254E
    private static let sleepyEyes: UInt32 = 0x1E1D24
    private static let sleepyMouth: UInt32 = 0x532802
    private static let sleepyTongue: UInt32 = 0xDA4C3C
    private static let foodieInk: UInt32 = 0x2B1703
    private static let foodieMouth: UInt32 = 0x331700
    private static let foodieTongue: UInt32 = 0xD03B34
    private static let drool: UInt32 = 0x44C2F8
    private static let ink: UInt32 = 0x1A1615
    private static let white: UInt32 = 0xFBFBF8
    private static let frame: UInt32 = 0xF6F5F3
    private static let frameSide: UInt32 = 0xD9D7D3
    private static let lensRed = (UInt32(0xFA4250), UInt32(0xC91F2A))
    private static let lensBlue = (UInt32(0x2A4BC2), UInt32(0x0640A6))
    private static let bingeMouth: UInt32 = 0x3D1F36
    private static let bingeTongue: UInt32 = 0xCF242B
    private static let bingeBody: UInt32 = 0xCB7EF4
    private static let heart: UInt32 = 0xFB5754
    private static let scrollInk: UInt32 = 0x181514
    private static let lid: UInt32 = 0xE9393E
    private static let socialInk: UInt32 = 0x1B1B1B
    private static let crescent: UInt32 = 0x9A9AA2
    private static let wing: UInt32 = 0xE8FAA4
    private static let wingBand: UInt32 = 0x3CD43C
    private static let wingDot: UInt32 = 0x8A8AE6
    private static let butterflyInk: UInt32 = 0x14296B
    private static let butterflyBody: UInt32 = 0x9DB7F2

    static func draw(
        _ context: inout GraphicsContext,
        kind: WeekendMoodCarouselFaceKind,
        act: Double,
        age: Double,
        phase: Double,
        still: Bool
    ) {
        switch kind {
        case .sleepy:
            curve(&context, -58.5, 196, -42.8, 211.5, -27.1, 196, 5.6, sleepyEyes)
            curve(&context, 18.5, 196, 33.8, 211.5, 49.1, 196, 5.6, sleepyEyes)
            var mouth = Path()
            mouth.addEllipse(in: CGRect(x: -25.3, y: 218.7, width: 41.6, height: 30.3))
            context.fill(mouth, with: fill(sleepyMouth))
            var mouthClip = context
            mouthClip.clip(to: mouth)
            ellipse(&mouthClip, -4.3, 250, 16.5, 8.6, sleepyTongue)
            let on = act > 0 ? 1.0 : 0.0
            let currentAge = still ? 9_999.0 : age
            zed(&context, 44.7, 161.3, 57, 176.3, 3.2, CGFloat(on * pop(currentAge / WeekendMoodCarouselMetrics.zzzPop)))
            zed(&context, 67, 138, 90.3, 164, 4.6, CGFloat(on * pop((currentAge - WeekendMoodCarouselMetrics.zzzStagger) / WeekendMoodCarouselMetrics.zzzPop)))
            zed(&context, 96, 110, 126, 139.3, 5.6, CGFloat(on * pop((currentAge - WeekendMoodCarouselMetrics.zzzStagger * 2) / WeekendMoodCarouselMetrics.zzzPop)))

        case .foodie:
            curve(&context, -47, 215.5, -30.4, 197.5, -13.7, 215.5, 5.4, foodieInk)
            curve(&context, 21.3, 214.5, 37.8, 196.5, 54.3, 214.5, 5.4, foodieInk)
            let open = CGFloat(max(WeekendMoodCarouselMetrics.mouthClosed, still ? act : weekendClamp01(age / WeekendMoodCarouselMetrics.mouth) * act))
            var mouthContext = context
            mouthContext.translateBy(x: 0, y: 230)
            mouthContext.scaleBy(x: 1, y: open)
            mouthContext.translateBy(x: 0, y: -230)
            var mouth = Path()
            mouth.move(to: CGPoint(x: -18.7, y: 233))
            mouth.addQuadCurve(to: CGPoint(x: 34.3, y: 233), control: CGPoint(x: 7.8, y: 225))
            mouth.addCurve(to: CGPoint(x: -18.7, y: 233), control1: CGPoint(x: 34.3, y: 262), control2: CGPoint(x: -18.7, y: 262))
            mouth.closeSubpath()
            mouthContext.fill(mouth, with: fill(foodieMouth))
            var tongueContext = mouthContext
            tongueContext.clip(to: mouth)
            ellipse(&tongueContext, 8.5, 260, 19.5, 9.5, foodieTongue)
            let droolAmount = CGFloat(still ? act : weekendClamp01((age - WeekendMoodCarouselMetrics.droolDelay) / WeekendMoodCarouselMetrics.drool) * act)
            if droolAmount > 0.01 {
                let top: CGFloat = 261.3
                let length: CGFloat = 17 * droolAmount
                var drop = Path()
                drop.move(to: CGPoint(x: 18.5, y: top))
                drop.addLine(to: CGPoint(x: 24.5, y: top))
                drop.addCurve(to: CGPoint(x: 21.5, y: top + length), control1: CGPoint(x: 27, y: top + length * 0.55), control2: CGPoint(x: 27, y: top + length))
                drop.addCurve(to: CGPoint(x: 18.5, y: top), control1: CGPoint(x: 16, y: top + length), control2: CGPoint(x: 16, y: top + length * 0.55))
                drop.closeSubpath()
                context.fill(drop, with: fill(drool))
            }

        case .recovery:
            curve(&context, -47, 219, -28.2, 192, -9.4, 219, 7.6, ink)
            curve(&context, 44.4, 219, 63.5, 192, 82.6, 219, 7.6, ink)
            ellipse(&context, -22.2, 253.4, 22.9, 25.4, white)
            ellipse(&context, 54.9, 253.5, 24.9, 26.2, white)
            curve(&context, -39, 252.6, -22.9, 269.5, -6.8, 252.6, 5.6, ink)
            curve(&context, 38.4, 252.3, 54.5, 269.2, 70.6, 252.3, 5.6, ink)
            curve(&context, -16.3, 301, 18.2, 323, 52.7, 301, 6.4, ink)
            let cross = weekendBlendColor(0xFBCFC4, 0xF92105, act)
            context.fill(Path(roundedRect: CGRect(x: -2, y: 184.4, width: 33.7, height: 10.8), cornerRadius: 1.5), with: .color(cross))
            context.fill(Path(roundedRect: CGRect(x: 9.5, y: 173.3, width: 10.8, height: 33), cornerRadius: 1.5), with: .color(cross))

        case .binge:
            context.fill(Path(roundedRect: CGRect(x: -76, y: 236, width: 174, height: 22), cornerRadius: 9), with: .color(Color.rgba(80, 20, 110, 0.28)))
            context.fill(Path(roundedRect: CGRect(x: -77.7, y: 197.7, width: 176.4, height: 51.3), cornerRadius: 5), with: fill(frameSide))
            context.fill(Path(roundedRect: CGRect(x: -77.7, y: 197.7, width: 176.4, height: 46.5), cornerRadius: 5), with: fill(frame))
            context.fill(Path(ellipseIn: CGRect(x: -2.5, y: 237, width: 27, height: 30)), with: fill(bingeBody))
            let flick = Int(floor(phase / 130.0))
            let swap = act > 0.5 && !still && (((flick + 1) * 7919) % 5) % 2 == 1
            lens(&context, x0: -61.3, x1: -0.3, colors: swap ? lensBlue : lensRed)
            lens(&context, x0: 22, x1: 83, colors: swap ? lensRed : lensBlue)
            var mouth = Path()
            mouth.move(to: CGPoint(x: -35, y: 263))
            mouth.addLine(to: CGPoint(x: 55, y: 263))
            mouth.addCurve(to: CGPoint(x: -35, y: 263), control1: CGPoint(x: 55, y: 324), control2: CGPoint(x: -35, y: 324))
            mouth.closeSubpath()
            context.fill(mouth, with: fill(bingeMouth))
            var mouthClip = context
            mouthClip.clip(to: mouth)
            var teeth = Path()
            teeth.move(to: CGPoint(x: -36, y: 259))
            teeth.addLine(to: CGPoint(x: 56, y: 259))
            teeth.addLine(to: CGPoint(x: 56, y: 274))
            teeth.addQuadCurve(to: CGPoint(x: -36, y: 274), control: CGPoint(x: 10, y: 281))
            teeth.closeSubpath()
            mouthClip.fill(teeth, with: .color(Color(hex: 0xFFFFFE)))
            ellipse(&mouthClip, 10, 311, 31, 18, bingeTongue)

        case .shopping:
            curve(&context, -42.6, 220, -29.2, 200, -15.8, 220, 5.6, ink)
            curve(&context, 20.8, 220, 34.2, 200, 47.6, 220, 5.6, ink)
            curve(&context, -28.4, 239.4, 2.2, 267, 32.8, 239.4, 6.2, ink)
            let beat = CGFloat(still ? 1 : 1 + WeekendMoodCarouselMetrics.heartGain * act * max(0, sin((phase / WeekendMoodCarouselMetrics.heartPeriod) * Double.pi * 2)))
            heart(&context, 3.85, 312.4, 49.7 * beat, 42.7 * beat)

        case .scroll:
            curve(&context, -32, 210, -16.8, 189, -1.6, 210, 6.6, scrollInk)
            curve(&context, 41, 210, 56.7, 189, 72.4, 210, 6.6, scrollInk)
            ellipse(&context, -12.2, 240.2, 18.5, 17.5, white)
            ellipse(&context, 49.3, 240.3, 20.3, 18, white)
            ellipse(&context, -12.5, 239.2, 8.8, 9.9, scrollInk)
            ellipse(&context, 44, 239.4, 9, 9.7, scrollInk)
            let lidAmount = CGFloat(WeekendMoodCarouselMetrics.lidRest + (WeekendMoodCarouselMetrics.lidSelected - WeekendMoodCarouselMetrics.lidRest) * act)
            for i in 0..<2 {
                let eyeX: CGFloat = i == 0 ? -12.2 : 49.3
                let radiusX: CGFloat = i == 0 ? 18.5 : 20.3
                let top: CGFloat = i == 0 ? 222.7 : 222.3
                let eye = Path(ellipseIn: CGRect(x: eyeX - radiusX, y: top, width: radiusX * 2, height: 36))
                var lidContext = context
                lidContext.clip(to: eye)
                lidContext.fill(Path(CGRect(x: eyeX - radiusX - 1, y: top - 1, width: radiusX * 2 + 2, height: 36 * lidAmount + 1)), with: fill(lid))
            }
            curve(&context, -6.5, 276.5, 22, 295, 47, 276.5, 6.2, scrollInk)
            ellipse(&context, -75.2, 249.4, 5.5, 5.5, scrollInk)

        case .social:
            curve(&context, -41.5, 212, -36, 194, -21.5, 192.5, 6.4, socialInk)
            curve(&context, 38.5, 190, 60, 188, 70.5, 212, 6.4, socialInk)
            for i in 0..<2 {
                let eyeX: CGFloat = i == 0 ? -21.0 : 41.0
                let eyeY: CGFloat = i == 0 ? 238.5 : 237.8
                ellipse(&context, eyeX + 1, eyeY + 21, 15, 4, crescent)
                ellipse(&context, eyeX, eyeY, 23.7, 22.2, socialInk)
                var glint = context
                glint.translateBy(x: eyeX - 5.6, y: eyeY - 8.4)
                glint.rotate(by: .degrees(-25))
                ellipse(&glint, 0, 0, 13.5, 10.6, white)
                ellipse(&context, eyeX + 5.3, eyeY + 8.3, 5.1, 4.6, white)
            }
            curve(&context, -14, 283.5, 9.2, 299.5, 32.5, 283.5, 5.6, socialInk)
            var flapA: CGFloat = 1
            var flapB: CGFloat = 1
            if !still, act > 0 {
                let t = max(0, age) / WeekendMoodCarouselMetrics.wingPeriod
                flapA = CGFloat(WeekendMoodCarouselMetrics.wingMinimum + (1 - WeekendMoodCarouselMetrics.wingMinimum) * abs(cos(t * Double.pi)))
                flapB = CGFloat(WeekendMoodCarouselMetrics.wingMinimum + (1 - WeekendMoodCarouselMetrics.wingMinimum) * abs(cos(t * Double.pi)))
            }
            butterfly(&context, x: -80, y: 201, angle: 0, flap: flapA)
            butterfly(&context, x: 86.5, y: 299.5, angle: -43, flap: flapB)
        }
    }

    private static func fill(_ color: UInt32, opacity: Double = 1) -> GraphicsContext.Shading {
        .color(Color(hex: color, opacity: opacity))
    }

    private static func stroke(_ color: UInt32, width: CGFloat) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    private static func curve(_ context: inout GraphicsContext, _ x0: CGFloat, _ y0: CGFloat, _ cx: CGFloat, _ cy: CGFloat, _ x1: CGFloat, _ y1: CGFloat, _ width: CGFloat, _ color: UInt32) {
        var path = Path()
        path.move(to: CGPoint(x: x0, y: y0))
        path.addQuadCurve(to: CGPoint(x: x1, y: y1), control: CGPoint(x: cx, y: cy))
        context.stroke(path, with: fill(color), style: stroke(color, width: width))
    }

    private static func ellipse(_ context: inout GraphicsContext, _ centerX: CGFloat, _ centerY: CGFloat, _ radiusX: CGFloat, _ radiusY: CGFloat, _ color: UInt32) {
        context.fill(Path(ellipseIn: CGRect(x: centerX - radiusX, y: centerY - radiusY, width: radiusX * 2, height: radiusY * 2)), with: fill(color))
    }

    private static func pop(_ value: Double) -> Double {
        let p = weekendClamp01(value)
        let s = 1.7
        let q = p - 1
        return p <= 0 ? 0 : 1 + (s + 1) * q * q * q + s * q * q
    }

    private static func zed(_ context: inout GraphicsContext, _ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat, _ width: CGFloat, _ scale: CGFloat) {
        guard scale > 0.001 else { return }
        let centerX = (x0 + x1) / 2
        let centerY = (y0 + y1) / 2
        let halfWidth = (x1 - x0) / 2 - width / 2
        let halfHeight = (y1 - y0) / 2 - width / 2
        var z = context
        z.translateBy(x: centerX, y: centerY)
        z.scaleBy(x: scale, y: scale)
        z.rotate(by: .degrees(WeekendMoodCarouselMetrics.zzzTilt))
        var path = Path()
        path.move(to: CGPoint(x: -halfWidth * 0.82, y: -halfHeight))
        path.addLine(to: CGPoint(x: halfWidth * 0.82, y: -halfHeight))
        path.addLine(to: CGPoint(x: -halfWidth * 0.82, y: halfHeight))
        path.addLine(to: CGPoint(x: halfWidth * 0.82, y: halfHeight))
        z.stroke(path, with: fill(sleepyInk), style: StrokeStyle(lineWidth: width, lineCap: .square, lineJoin: .miter))
    }

    private static func heart(_ context: inout GraphicsContext, _ centerX: CGFloat, _ centerY: CGFloat, _ width: CGFloat, _ height: CGFloat) {
        let half = width / 2
        let top = centerY - height / 2
        let bottom = centerY + height / 2
        var path = Path()
        path.move(to: CGPoint(x: centerX, y: bottom))
        path.addCurve(to: CGPoint(x: centerX - half, y: top + height * 0.3), control1: CGPoint(x: centerX - half * 0.3, y: bottom - height * 0.22), control2: CGPoint(x: centerX - half, y: centerY + height * 0.02))
        path.addCurve(to: CGPoint(x: centerX, y: top + height * 0.2), control1: CGPoint(x: centerX - half, y: top - height * 0.02), control2: CGPoint(x: centerX - half * 0.15, y: top - height * 0.06))
        path.addCurve(to: CGPoint(x: centerX + half, y: top + height * 0.3), control1: CGPoint(x: centerX + half * 0.15, y: top - height * 0.06), control2: CGPoint(x: centerX + half, y: top - height * 0.02))
        path.addCurve(to: CGPoint(x: centerX, y: bottom), control1: CGPoint(x: centerX + half, y: centerY + height * 0.02), control2: CGPoint(x: centerX + half * 0.3, y: bottom - height * 0.22))
        path.closeSubpath()
        context.fill(path, with: fill(heart))
    }

    private static func lens(_ context: inout GraphicsContext, x0: CGFloat, x1: CGFloat, colors: (UInt32, UInt32)) {
        // Platform caveat: Canvas GraphicsContext has no Core Graphics
        // CornerPathEffect equivalent. This quadratic outline is the closest
        // SwiftUI-only approximation of Skia's CornerPathEffect(3).
        let path = roundedPolygon(
            [
                CGPoint(x: x0, y: 207),
                CGPoint(x: x1, y: 207),
                CGPoint(x: x1 - (x0 < 0 ? 4 : 0), y: 242),
                CGPoint(x: x0 + (x0 > 0 ? 4 : 0), y: 242),
            ],
            radius: 3
        )
        context.fill(path, with: .linearGradient(Gradient(colors: [Color(hex: colors.0), Color(hex: colors.1)]), startPoint: CGPoint(x: x0, y: 207), endPoint: CGPoint(x: x1, y: 242)))
    }

    private static func roundedPolygon(_ points: [CGPoint], radius: CGFloat) -> Path {
        guard points.count > 2 else { return Path() }
        func distance(_ a: CGPoint, _ b: CGPoint) -> CGFloat {
            hypot(a.x - b.x, a.y - b.y)
        }
        func point(from origin: CGPoint, toward target: CGPoint, by amount: CGFloat) -> CGPoint {
            let length = distance(origin, target)
            guard length > 0 else { return origin }
            return CGPoint(
                x: origin.x + (target.x - origin.x) * amount / length,
                y: origin.y + (target.y - origin.y) * amount / length
            )
        }

        var path = Path()
        for index in points.indices {
            let previous = points[(index + points.count - 1) % points.count]
            let corner = points[index]
            let next = points[(index + 1) % points.count]
            let inset = min(radius, min(distance(previous, corner) / 2, distance(corner, next) / 2))
            let start = point(from: corner, toward: previous, by: inset)
            let end = point(from: corner, toward: next, by: inset)
            if index == points.startIndex {
                path.move(to: start)
            } else {
                path.addLine(to: start)
            }
            path.addQuadCurve(to: end, control: corner)
        }
        path.closeSubpath()
        return path
    }

    private static func butterfly(_ context: inout GraphicsContext, x: CGFloat, y: CGFloat, angle: CGFloat, flap: CGFloat) {
        let upper: [(CGFloat, CGFloat)] = [(-3, -10), (-36, -12), (-38, -5), (-31, 3), (-4, 2)]
        let lower: [(CGFloat, CGFloat)] = [(-4, 4), (-30, 7), (-27, 16), (-19, 19), (-6, 13)]
        var butterflyContext = context
        butterflyContext.translateBy(x: x, y: y)
        butterflyContext.rotate(by: .degrees(Double(angle)))
        for side in 0..<2 {
            func mirrored(_ points: [(CGFloat, CGFloat)]) -> [(CGFloat, CGFloat)] {
                side == 0 ? points : points.map { (-$0.0, $0.1) }
            }
            var wingContext = butterflyContext
            wingContext.scaleBy(x: flap, y: 1)
            // Platform caveat: GraphicsContext cannot apply Skia's
            // CornerPathEffect(4), so use a fixed-radius quadratic outline.
            let upperPath = roundedPolygon(mirrored(upper).map { CGPoint(x: $0.0, y: $0.1) }, radius: 4)
            let lowerPath = roundedPolygon(mirrored(lower).map { CGPoint(x: $0.0, y: $0.1) }, radius: 4)
            wingContext.fill(lowerPath, with: fill(wing))
            var band = wingContext
            band.clip(to: lowerPath)
            band.fill(Path(CGRect(x: side == 0 ? -34 : 4, y: 6, width: 30, height: 5)), with: fill(wingBand))
            wingContext.stroke(lowerPath, with: fill(butterflyInk), style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
            wingContext.fill(upperPath, with: fill(wing))
            wingContext.stroke(upperPath, with: fill(butterflyInk), style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
            let sign: CGFloat = side == 1 ? -1 : 1
            ellipse(&wingContext, -24 * sign, -3, 3, 3, wingDot)
            ellipse(&wingContext, -17 * sign, 11, 3, 3, wingDot)
            ellipse(&wingContext, -15 * sign, -2, 1.3, 1.3, butterflyInk)
        }
        butterflyContext.fill(Path(roundedRect: CGRect(x: -2.8, y: -11, width: 5.6, height: 35), cornerRadius: 2.8), with: fill(butterflyBody))
        butterflyContext.stroke(Path(roundedRect: CGRect(x: -2.8, y: -11, width: 5.6, height: 35), cornerRadius: 2.8), with: fill(butterflyInk), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
        ellipse(&butterflyContext, 0, -14.5, 4.6, 4.6, butterflyBody)
        butterflyContext.stroke(Path(ellipseIn: CGRect(x: -4.6, y: -19.1, width: 9.2, height: 9.2)), with: fill(butterflyInk), style: StrokeStyle(lineWidth: 2.2))
        curve(&butterflyContext, -1.5, -18.5, -2.5, -23, -7.5, -23.5, 2.2, butterflyInk)
        curve(&butterflyContext, 1.5, -18.5, 2.5, -23, 7.5, -23.5, 2.2, butterflyInk)
    }
}
