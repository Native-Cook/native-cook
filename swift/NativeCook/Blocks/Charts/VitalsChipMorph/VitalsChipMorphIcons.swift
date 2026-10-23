import SwiftUI

/// Hand-drawn equivalents of the RN SVG glyphs.  Their public `s` is the
/// source's point scale (`3 * k`); each Canvas therefore keeps its original
/// 24/30/32 pt coordinate system and never relies on SF Symbols.
struct VitalsChipMorphIcons: View {
    enum Kind {
        case summary, sleep, readiness, fitness, heart, vitals, longevity, browse
    }

    let kind: Kind
    let s: Double
    /// Only the free-standing heart is reused at more than one source size.
    var size: Double?
    var color: Color?

    var body: some View {
        switch kind {
        case .summary: VitalsChipMorphSummaryIcon(s: s)
        case .sleep: VitalsChipMorphSleepIcon(s: s)
        case .readiness: VitalsChipMorphReadinessIcon(s: s)
        case .fitness: VitalsChipMorphFitnessIcon(s: s)
        case .heart: VitalsChipMorphHeartGlyph(size: size ?? 24 * s, color: color ?? VitalsChipMorphTheme.colors.heart)
        case .vitals: VitalsChipMorphVitalsIcon(s: s)
        case .longevity: VitalsChipMorphLongevityIcon(s: s)
        case .browse: VitalsChipMorphBrowseIcon(s: s)
        }
    }

    static func heartPath() -> Path {
        Path { p in
            p.move(to: CGPoint(x: 12, y: 21.35))
            p.addLine(to: CGPoint(x: 10.55, y: 20))
            p.addCurve(to: CGPoint(x: 2, y: 8.5), control1: CGPoint(x: 5.4, y: 15.36), control2: CGPoint(x: 2, y: 12.28))
            p.addCurve(to: CGPoint(x: 7.5, y: 3), control1: CGPoint(x: 2, y: 5.42), control2: CGPoint(x: 4.42, y: 3))
            p.addCurve(to: CGPoint(x: 12, y: 5.09), control1: CGPoint(x: 9.24, y: 3), control2: CGPoint(x: 10.91, y: 3.81))
            p.addCurve(to: CGPoint(x: 16.5, y: 3), control1: CGPoint(x: 13.09, y: 3.81), control2: CGPoint(x: 14.76, y: 3))
            p.addCurve(to: CGPoint(x: 22, y: 8.5), control1: CGPoint(x: 19.58, y: 3), control2: CGPoint(x: 22, y: 5.42))
            p.addCurve(to: CGPoint(x: 13.45, y: 20), control1: CGPoint(x: 22, y: 12.28), control2: CGPoint(x: 18.6, y: 15.36))
            p.closeSubpath()
        }
    }

    static func arc(_ center: CGPoint, radius: CGFloat, from: Double, to: Double) -> Path {
        var path = Path()
        path.addArc(
            center: center,
            radius: radius,
            startAngle: .degrees(from - 90),
            endAngle: .degrees(to - 90),
            clockwise: false
        )
        return path
    }

    static func stroke(_ context: inout GraphicsContext, _ path: Path, color: Color, width: Double, cap: CGLineCap = .butt) {
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: cap, lineJoin: .round))
    }

    static func text(_ context: inout GraphicsContext, _ value: String, at point: CGPoint, size: Double, weight: Font.Weight, color: Color) {
        context.draw(Text(value).font(.system(size: size, weight: weight)).foregroundStyle(color), at: point, anchor: .center)
    }

    static func baselineText(_ context: inout GraphicsContext, _ value: String, centerX: Double, baseline: Double, size: Double, weight: Font.Weight, color: Color) {
        let resolved = context.resolve(Text(value).font(.system(size: size, weight: weight)).foregroundStyle(color))
        let measured = resolved.measure(in: CGSize(width: 400, height: 400))
        context.draw(resolved, in: CGRect(x: centerX - measured.width / 2, y: baseline - resolved.firstBaseline(in: measured), width: measured.width, height: measured.height))
    }
}

struct VitalsChipMorphHeartGlyph: View {
    let size: Double
    let color: Color

    var body: some View {
        Canvas { context, _ in
            let scale = size / 24
            context.scaleBy(x: scale, y: scale)
            context.fill(VitalsChipMorphIcons.heartPath(), with: .color(color))
        }
        .frame(width: size, height: size)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphSummaryIcon: View {
    let s: Double

    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            var heart = context
            heart.translateBy(x: 4, y: 1)
            heart.scaleBy(x: 0.92, y: 0.92)
            heart.fill(
                VitalsChipMorphIcons.heartPath(),
                with: .linearGradient(
                    Gradient(colors: [VitalsChipMorphTheme.colors.summaryHeartTop, VitalsChipMorphTheme.colors.summaryHeartBottom]),
                    startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: 24)
                )
            )
            context.fill(Path(roundedRect: CGRect(x: 2, y: 27, width: 26, height: 2), cornerRadius: 1), with: .color(VitalsChipMorphTheme.colors.faint.opacity(0.75)))
            context.fill(Path(roundedRect: CGRect(x: 2, y: 33.5, width: 17, height: 2), cornerRadius: 1), with: .color(VitalsChipMorphTheme.colors.faint.opacity(0.75)))
        }
        .frame(width: 30 * s, height: 40 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphSleepIcon: View {
    let s: Double

    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            let c = CGPoint(x: 16, y: 16)
            VitalsChipMorphIcons.stroke(&context, VitalsChipMorphIcons.arc(c, radius: 13, from: 268, to: 358), color: VitalsChipMorphTheme.colors.sleepTeal, width: 5.2)
            VitalsChipMorphIcons.stroke(&context, VitalsChipMorphIcons.arc(c, radius: 13, from: 4, to: 58), color: VitalsChipMorphTheme.colors.sleepCoral, width: 5.2)
            VitalsChipMorphIcons.stroke(&context, VitalsChipMorphIcons.arc(c, radius: 13, from: 64, to: 262), color: VitalsChipMorphTheme.colors.sleepBlue, width: 5.2)
            VitalsChipMorphIcons.baselineText(&context, "84", centerX: 16, baseline: 20.3, size: 12, weight: .bold, color: VitalsChipMorphTheme.colors.ink)
        }
        .frame(width: 32 * s, height: 32 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphReadinessIcon: View {
    let s: Double

    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            let c = CGPoint(x: 16, y: 18)
            VitalsChipMorphIcons.stroke(&context, VitalsChipMorphIcons.arc(c, radius: 12, from: -78, to: 78), color: VitalsChipMorphTheme.colors.track, width: 4.6, cap: .round)
            VitalsChipMorphIcons.stroke(&context, VitalsChipMorphIcons.arc(c, radius: 12, from: -2, to: 33), color: VitalsChipMorphTheme.colors.segment, width: 4.6, cap: .round)
            let angle = 30.0 * .pi / 180
            context.fill(Path(ellipseIn: CGRect(x: 16 + 12 * sin(angle) - 2.6, y: 18 - 12 * cos(angle) - 2.6, width: 5.2, height: 5.2)), with: .color(VitalsChipMorphTheme.colors.blue))
            VitalsChipMorphIcons.baselineText(&context, "7", centerX: 15.5, baseline: 25, size: 11, weight: .bold, color: VitalsChipMorphTheme.colors.blue)
        }
        .frame(width: 32 * s, height: 32 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphFitnessIcon: View {
    let s: Double

    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            let rings: [(Double, Double, Color, Color)] = [
                (12.6, 0.75, VitalsChipMorphTheme.colors.moveStart, VitalsChipMorphTheme.colors.moveTrack),
                (8.4, 0.63, VitalsChipMorphTheme.colors.exerciseStart, VitalsChipMorphTheme.colors.exerciseTrack),
                (4.2, 0.33, VitalsChipMorphTheme.colors.standStart, VitalsChipMorphTheme.colors.standTrack),
            ]
            let center = CGPoint(x: 16, y: 16)
            for ring in rings {
                VitalsChipMorphIcons.stroke(&context, Path(ellipseIn: CGRect(x: 16 - ring.0, y: 16 - ring.0, width: 2 * ring.0, height: 2 * ring.0)), color: ring.3, width: 3.8)
            }
            for ring in rings {
                VitalsChipMorphIcons.stroke(&context, VitalsChipMorphIcons.arc(center, radius: ring.0, from: 0, to: 360 * ring.1), color: ring.2, width: 3.8, cap: .round)
            }
        }
        .frame(width: 32 * s, height: 32 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphVitalsIcon: View {
    let s: Double

    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            let points: [(Double, Double, Color)] = [
                (15, 7, VitalsChipMorphTheme.colors.vitalsPink), (7, 15, VitalsChipMorphTheme.colors.vitalsBlue),
                (23, 15, VitalsChipMorphTheme.colors.vitalsBlue), (15, 23, VitalsChipMorphTheme.colors.vitalsBlue),
            ]
            for point in points {
                VitalsChipMorphIcons.stroke(&context, Path(ellipseIn: CGRect(x: point.0 - 4.8, y: point.1 - 4.8, width: 9.6, height: 9.6)), color: point.2, width: 2.8)
            }
        }
        .frame(width: 30 * s, height: 30 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphTargetIcon: View {
    let s: Double
    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            let c = CGPoint(x: 9.5, y: 9.5)
            context.fill(Path(ellipseIn: CGRect(x: 0.9, y: 0.9, width: 17.2, height: 17.2)), with: .color(VitalsChipMorphTheme.colors.iconGrey.opacity(0.9)))
            for radius in [6.6, 4.4, 2.2] {
                VitalsChipMorphIcons.stroke(&context, Path(ellipseIn: CGRect(x: c.x - radius, y: c.y - radius, width: 2 * radius, height: 2 * radius)), color: .white.opacity(0.55), width: 0.9)
            }
            context.fill(Path(ellipseIn: CGRect(x: 8.6, y: 8.6, width: 1.8, height: 1.8)), with: .color(.white))
        }
        .frame(width: 19 * s, height: 19 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphDotsIcon: View {
    let s: Double
    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            for (x, y, opacity) in [(9.5, 3.6, 1.0), (3.6, 9.5, 0.55), (15.4, 9.5, 0.55), (9.5, 15.4, 0.55)] {
                VitalsChipMorphIcons.stroke(&context, Path(ellipseIn: CGRect(x: x - 2.7, y: y - 2.7, width: 5.4, height: 5.4)), color: VitalsChipMorphTheme.colors.iconGrey.opacity(opacity), width: 1.5)
            }
        }
        .frame(width: 19 * s, height: 19 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphBedIcon: View {
    let s: Double
    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            func fill(_ rect: CGRect, radius: Double, _ color: Color) { context.fill(Path(roundedRect: rect, cornerRadius: radius), with: .color(color)) }
            fill(CGRect(x: 2, y: 1, width: 18, height: 6), radius: 1.6, VitalsChipMorphTheme.colors.iconGrey)
            fill(CGRect(x: 4, y: 3, width: 6, height: 3), radius: 1, .white.opacity(0.4))
            fill(CGRect(x: 12, y: 3, width: 6, height: 3), radius: 1, .white.opacity(0.4))
            fill(CGRect(x: 0.5, y: 7, width: 21, height: 6), radius: 1.4, VitalsChipMorphTheme.colors.iconGrey)
            fill(CGRect(x: 0.5, y: 12, width: 2.4, height: 3.5), radius: 0.6, VitalsChipMorphTheme.colors.iconGrey)
            fill(CGRect(x: 19.1, y: 12, width: 2.4, height: 3.5), radius: 0.6, VitalsChipMorphTheme.colors.iconGrey)
        }
        .frame(width: 22 * s, height: 16 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphCheckCircle: View {
    let size: Double
    var body: some View {
        Canvas { context, _ in
            let scale = size / 20
            context.scaleBy(x: scale, y: scale)
            context.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: 20, height: 20)), with: .color(VitalsChipMorphTheme.colors.check))
            var check = Path()
            check.move(to: CGPoint(x: 5.6, y: 10.4))
            check.addLine(to: CGPoint(x: 8.6, y: 13.3))
            check.addLine(to: CGPoint(x: 14.4, y: 7))
            VitalsChipMorphIcons.stroke(&context, check, color: .white, width: 2.2, cap: .round)
        }
        .frame(width: size, height: size)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphChevron: View {
    let s: Double
    var color: Color = VitalsChipMorphTheme.colors.faint
    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            var path = Path()
            path.move(to: CGPoint(x: 3.5, y: 2.5)); path.addLine(to: CGPoint(x: 8.5, y: 8)); path.addLine(to: CGPoint(x: 3.5, y: 13.5))
            VitalsChipMorphIcons.stroke(&context, path, color: color, width: 1.8, cap: .round)
        }
        .frame(width: 12 * s, height: 16 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphMiniChartIcon: View {
    enum Kind { case heart, lungs, temp, oxygen, wave }
    let kind: Kind
    let s: Double

    var body: some View {
        if kind == .heart {
            VitalsChipMorphHeartGlyph(size: 14 * s, color: VitalsChipMorphTheme.colors.link)
        } else {
            Canvas { context, _ in
                context.scaleBy(x: s, y: s)
                let color = VitalsChipMorphTheme.colors.link
                switch kind {
                case .lungs:
                    var left = Path(); left.move(to: CGPoint(x: 6.2, y: 2)); left.addLine(to: CGPoint(x: 6.2, y: 6.2)); left.addCurve(to: CGPoint(x: 2.6, y: 12), control1: CGPoint(x: 6.2, y: 9), control2: CGPoint(x: 4.8, y: 12)); left.addCurve(to: CGPoint(x: 1, y: 9.6), control1: CGPoint(x: 1.3, y: 12), control2: CGPoint(x: 1, y: 10.8)); left.addCurve(to: CGPoint(x: 4.6, y: 4), control1: CGPoint(x: 1, y: 7), control2: CGPoint(x: 2.6, y: 4)); left.addCurve(to: CGPoint(x: 6.2, y: 5.6), control1: CGPoint(x: 5.5, y: 4), control2: CGPoint(x: 6.2, y: 4.6)); left.closeSubpath()
                    var right = Path(); right.move(to: CGPoint(x: 7.8, y: 2)); right.addLine(to: CGPoint(x: 7.8, y: 6.2)); right.addCurve(to: CGPoint(x: 11.4, y: 12), control1: CGPoint(x: 7.8, y: 9), control2: CGPoint(x: 9.2, y: 12)); right.addCurve(to: CGPoint(x: 13, y: 9.6), control1: CGPoint(x: 12.7, y: 12), control2: CGPoint(x: 13, y: 10.8)); right.addCurve(to: CGPoint(x: 9.4, y: 4), control1: CGPoint(x: 13, y: 7), control2: CGPoint(x: 11.4, y: 4)); right.addCurve(to: CGPoint(x: 7.8, y: 5.6), control1: CGPoint(x: 8.5, y: 4), control2: CGPoint(x: 7.8, y: 4.6)); right.closeSubpath(); context.fill(left, with: .color(color)); context.fill(right, with: .color(color))
                case .temp:
                    VitalsChipMorphIcons.stroke(&context, Path(roundedRect: CGRect(x: 5.1, y: 0.8, width: 3.8, height: 9), cornerRadius: 1.9), color: color, width: 1.1); context.fill(Path(ellipseIn: CGRect(x: 4.6, y: 8.8, width: 4.8, height: 4.8)), with: .color(color)); context.fill(Path(CGRect(x: 6.4, y: 4, width: 1.2, height: 6)), with: .color(color))
                case .oxygen:
                    var drop = Path(); drop.move(to: CGPoint(x: 6, y: 1.2)); drop.addCurve(to: CGPoint(x: 2.4, y: 8.4), control1: CGPoint(x: 4.4, y: 3.6), control2: CGPoint(x: 2.4, y: 6)); drop.addArc(center: CGPoint(x: 6, y: 8.4), radius: 3.6, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false); drop.addCurve(to: CGPoint(x: 6, y: 1.2), control1: CGPoint(x: 9.6, y: 6), control2: CGPoint(x: 7.6, y: 3.6)); VitalsChipMorphIcons.stroke(&context, drop, color: color, width: 1.2); context.fill(Path(ellipseIn: CGRect(x: 4.5, y: 7.1, width: 3, height: 3)), with: .color(color)); VitalsChipMorphIcons.baselineText(&context, "2", centerX: 11.4, baseline: 13.4, size: 4.4, weight: .bold, color: color)
                case .wave:
                    context.fill(Path(roundedRect: CGRect(x: 0.6, y: 2.2, width: 12.8, height: 9.6), cornerRadius: 1.6), with: .color(color)); var wave = Path(); wave.move(to: CGPoint(x: 2.2, y: 7.6)); wave.addLine(to: CGPoint(x: 3.8, y: 5.2)); wave.addLine(to: CGPoint(x: 5.4, y: 8.8)); wave.addLine(to: CGPoint(x: 7, y: 5.2)); wave.addLine(to: CGPoint(x: 8.6, y: 8.8)); wave.addLine(to: CGPoint(x: 10.2, y: 5.2)); wave.addLine(to: CGPoint(x: 11.8, y: 7.4)); VitalsChipMorphIcons.stroke(&context, wave, color: .white, width: 1)
                case .heart: break
                }
            }
            .frame(width: 14 * s, height: 14 * s)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
    }
}

struct VitalsChipMorphLongevityIcon: View {
    let s: Double
    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            context.fill(Path(ellipseIn: CGRect(x: 1, y: 1, width: 24, height: 24)), with: .color(VitalsChipMorphTheme.colors.ink))
            for degree in stride(from: 0.0, to: 360, by: 45) {
                var petal = Path(); petal.move(to: CGPoint(x: 13, y: 13)); petal.addLine(to: CGPoint(x: 13, y: 1.6)); petal.addArc(center: CGPoint(x: 13, y: 13), radius: 11.4, startAngle: .degrees(-90), endAngle: .degrees(-66.8), clockwise: false); petal.closeSubpath()
                var layer = context; layer.translateBy(x: 13, y: 13); layer.rotate(by: .degrees(degree + 12)); layer.translateBy(x: -13, y: -13); layer.fill(petal, with: .color(.white.opacity(0.92)))
            }
            context.fill(Path(ellipseIn: CGRect(x: 8.8, y: 8.8, width: 8.4, height: 8.4)), with: .color(.white)); context.fill(Path(ellipseIn: CGRect(x: 10.4, y: 10.4, width: 5.2, height: 5.2)), with: .color(VitalsChipMorphTheme.colors.ink))
        }
        .frame(width: 26 * s, height: 26 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct VitalsChipMorphBrowseIcon: View {
    let s: Double
    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: s, y: s)
            for row in 0..<2 { for column in 0..<2 { context.fill(Path(roundedRect: CGRect(x: 1 + Double(column) * 12, y: 1 + Double(row) * 12, width: 10, height: 10), cornerRadius: 2.2), with: .color(VitalsChipMorphTheme.colors.ink)) } }
        }
        .frame(width: 24 * s, height: 24 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
