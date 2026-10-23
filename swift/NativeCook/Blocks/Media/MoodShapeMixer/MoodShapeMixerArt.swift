import SwiftUI

/// The source's compact picker and its quiet line-drawn desk. No remote artwork is required.
enum MoodShapeMixerArt {
    typealias M = MoodShapeMixerModel
    static let paper = rgb(0xECE7DB)
    static let ink = rgb(0x211F1D)
    static let line = rgb(0x8F8A7F)
    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func label(_ context: inout GraphicsContext, _ text: String, x: Double, y: Double, size: Double, weight: Font.Weight = .regular, color: Color = ink, tracking: Double = 0) {
        context.draw(Text(text).font(.system(size: size, weight: weight)).tracking(tracking).foregroundStyle(color), at: CGPoint(x: x, y: y))
    }
    static func stroked(_ context: inout GraphicsContext, _ path: Path, color: Color = line, width: Double = 1) {
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
    static func rect(_ context: inout GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ color: Color, radius: Double = 0) {
        context.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: radius), with: .color(color))
    }
    static func ellipse(_ context: inout GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ color: Color) {
        context.fill(Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h)), with: .color(color))
    }

    static let roomLines: Path = {
        var p = Path()
        // Window frame and sill.
        p.addRect(CGRect(x: -4, y: -20, width: 269, height: 215))
        p.addRect(CGRect(x: -4, y: 195, width: 282, height: 10))
        p.move(to: CGPoint(x: 105, y: -20)); p.addLine(to: CGPoint(x: 105, y: 195))
        p.move(to: CGPoint(x: 113, y: -20)); p.addLine(to: CGPoint(x: 113, y: 195))
        p.move(to: CGPoint(x: 256, y: -20)); p.addLine(to: CGPoint(x: 256, y: 195))
        // Sleeping cat and the curved tail behind the clock.
        p.move(to: CGPoint(x: 42, y: 324))
        p.addCurve(to: CGPoint(x: 81, y: 243), control1: CGPoint(x: 33, y: 295), control2: CGPoint(x: 49, y: 261))
        p.addLine(to: CGPoint(x: 78, y: 261)); p.addLine(to: CGPoint(x: 107, y: 253)); p.addLine(to: CGPoint(x: 131, y: 258))
        p.addCurve(to: CGPoint(x: 139, y: 323), control1: CGPoint(x: 143, y: 283), control2: CGPoint(x: 148, y: 303))
        p.move(to: CGPoint(x: 132, y: 261))
        p.addCurve(to: CGPoint(x: 263, y: 266), control1: CGPoint(x: 178, y: 230), control2: CGPoint(x: 224, y: 230))
        p.addCurve(to: CGPoint(x: 286, y: 342), control1: CGPoint(x: 291, y: 279), control2: CGPoint(x: 294, y: 315))
        p.move(to: CGPoint(x: 230, y: 276))
        p.addCurve(to: CGPoint(x: 198, y: 233), control1: CGPoint(x: 201, y: 261), control2: CGPoint(x: 179, y: 223))
        p.addCurve(to: CGPoint(x: 241, y: 268), control1: CGPoint(x: 215, y: 220), control2: CGPoint(x: 218, y: 257))
        p.addCurve(to: CGPoint(x: 273, y: 263), control1: CGPoint(x: 273, y: 283), control2: CGPoint(x: 292, y: 265))
        p.addCurve(to: CGPoint(x: 234, y: 255), control1: CGPoint(x: 254, y: 246), control2: CGPoint(x: 240, y: 250))
        p.move(to: CGPoint(x: 67, y: 308)); p.addQuadCurve(to: CGPoint(x: 87, y: 308), control: CGPoint(x: 77, y: 301))
        p.move(to: CGPoint(x: 111, y: 307)); p.addQuadCurve(to: CGPoint(x: 130, y: 308), control: CGPoint(x: 121, y: 301))
        p.move(to: CGPoint(x: 98, y: 312)); p.addLine(to: CGPoint(x: 104, y: 318)); p.addLine(to: CGPoint(x: 110, y: 312)); p.closeSubpath()
        p.move(to: CGPoint(x: 61, y: 316)); p.addLine(to: CGPoint(x: 87, y: 319))
        p.move(to: CGPoint(x: 121, y: 318)); p.addLine(to: CGPoint(x: 145, y: 314))
        p.move(to: CGPoint(x: 0, y: 342)); p.addQuadCurve(to: CGPoint(x: 73, y: 324), control: CGPoint(x: 22, y: 315))
        p.addLine(to: CGPoint(x: 259, y: 324)); p.addQuadCurve(to: CGPoint(x: 297, y: 357), control: CGPoint(x: 285, y: 336))
        // A plain framed print on the right wall.
        p.addRect(CGRect(x: 324, y: 82, width: 167, height: 321))
        p.addRect(CGRect(x: 333, y: 92, width: 151, height: 302))
        p.move(to: CGPoint(x: 382, y: 161)); p.addCurve(to: CGPoint(x: 405, y: 283), control1: CGPoint(x: 374, y: 225), control2: CGPoint(x: 357, y: 264))
        p.addQuadCurve(to: CGPoint(x: 440, y: 164), control: CGPoint(x: 447, y: 280))
        // Cube on the desk: all visible interior grid lines.
        let a = CGPoint(x: 128, y: 585), b = CGPoint(x: 186, y: 581), c = CGPoint(x: 221, y: 605), d = CGPoint(x: 166, y: 611)
        p.move(to: a); p.addLine(to: b); p.addLine(to: c); p.addLine(to: d); p.closeSubpath()
        p.move(to: a); p.addLine(to: CGPoint(x: 128, y: 644)); p.addLine(to: CGPoint(x: 166, y: 668)); p.addLine(to: CGPoint(x: 221, y: 668)); p.addLine(to: c)
        p.move(to: d); p.addLine(to: CGPoint(x: 166, y: 668))
        p.move(to: CGPoint(x: 147, y: 598)); p.addLine(to: CGPoint(x: 203, y: 593))
        p.move(to: CGPoint(x: 149, y: 584)); p.addLine(to: CGPoint(x: 183, y: 609)); p.addLine(to: CGPoint(x: 183, y: 668))
        p.move(to: CGPoint(x: 128, y: 614)); p.addLine(to: CGPoint(x: 166, y: 639)); p.addLine(to: CGPoint(x: 221, y: 639))
        p.move(to: CGPoint(x: 147, y: 598)); p.addLine(to: CGPoint(x: 147, y: 656))
        // Sketchbook and pencil.
        p.move(to: CGPoint(x: -20, y: 708)); p.addLine(to: CGPoint(x: 79, y: 687)); p.addLine(to: CGPoint(x: 141, y: 766)); p.addLine(to: CGPoint(x: 0, y: 806))
        p.move(to: CGPoint(x: 124, y: 713)); p.addLine(to: CGPoint(x: 259, y: 773)); p.addLine(to: CGPoint(x: 246, y: 780)); p.closeSubpath()
        p.move(to: CGPoint(x: 125, y: 714)); p.addLine(to: CGPoint(x: 133, y: 729)); p.addLine(to: CGPoint(x: 140, y: 721))
        p.move(to: CGPoint(x: 10, y: 736)); p.addLine(to: CGPoint(x: 17, y: 730)); p.addLine(to: CGPoint(x: 24, y: 733)); p.addLine(to: CGPoint(x: 31, y: 725)); p.addLine(to: CGPoint(x: 39, y: 727)); p.addLine(to: CGPoint(x: 49, y: 722)); p.addLine(to: CGPoint(x: 72, y: 716))
        // Glass and ice.
        p.addEllipse(in: CGRect(x: 300, y: 521, width: 154, height: 65))
        p.move(to: CGPoint(x: 300, y: 552)); p.addLine(to: CGPoint(x: 321, y: 740))
        p.move(to: CGPoint(x: 454, y: 552)); p.addLine(to: CGPoint(x: 438, y: 740))
        p.addEllipse(in: CGRect(x: 321, y: 714, width: 117, height: 47))
        p.addEllipse(in: CGRect(x: 309, y: 589, width: 139, height: 53))
        p.move(to: CGPoint(x: 335, y: 603)); p.addLine(to: CGPoint(x: 360, y: 583)); p.addLine(to: CGPoint(x: 393, y: 600)); p.addLine(to: CGPoint(x: 369, y: 620)); p.closeSubpath()
        p.addEllipse(in: CGRect(x: 375, y: 657, width: 5, height: 8)); p.addEllipse(in: CGRect(x: 369, y: 682, width: 4, height: 6))
        return p
    }()

    static func room(_ context: inout GraphicsContext) {
        rect(&context, 0, 0, 393, 852, paper)
        rect(&context, 0, -20, 103, 215, rgb(0x9ABBF5))
        rect(&context, 114, -20, 142, 215, rgb(0x9ABBF5))
        ellipse(&context, 40, 69, 42, 42, rgb(0xF1F8FA))
        ellipse(&context, 167, 113, 14, 14, rgb(0xFFF24A))
        stroked(&context, roomLines, width: 1)
        let clock = Path(roundedRect: CGRect(x: 15, y: 355, width: 288, height: 207), cornerRadius: 34)
        context.fill(clock, with: .color(rgb(0xE9E5DB)))
        stroked(&context, clock, width: 1)
        rect(&context, 28, 371, 260, 180, rgb(0x242323), radius: 24)
        ellipse(&context, 47, 389, 9, 9, rgb(0xF3EED9))
        ellipse(&context, 50, 387, 8, 8, rgb(0x242323))
        label(&context, "PM", x: 68, y: 394, size: 12, weight: .medium, color: rgb(0xF3EED9))
        label(&context, "Tuesday", x: 242, y: 394, size: 12, weight: .medium, color: rgb(0xF3EED9))
        for (index, digit) in [1, 3, 1, 4].enumerated() {
            let xs = [43.0, 96, 165, 218], angles = [-3.0, 1, -4, 2]
            var c = context
            c.translateBy(x: xs[index] + 23.5, y: 468)
            c.rotate(by: .degrees(angles[index]))
            rect(&c, -23.5, -50, 47, 100, rgb(0xE6E6D8), radius: 2)
            var glyph = Path()
            if digit == 1 {
                glyph.move(to: CGPoint(x: -10, y: -25)); glyph.addQuadCurve(to: CGPoint(x: 1, y: -34), control: CGPoint(x: 2, y: -45)); glyph.addLine(to: CGPoint(x: -2, y: 34))
            } else if digit == 3 {
                glyph.move(to: CGPoint(x: -13, y: -28)); glyph.addCurve(to: CGPoint(x: 5, y: -1), control1: CGPoint(x: 12, y: -48), control2: CGPoint(x: 18, y: -13)); glyph.addCurve(to: CGPoint(x: -13, y: 31), control1: CGPoint(x: 28, y: 14), control2: CGPoint(x: 5, y: 48))
            } else {
                glyph.move(to: CGPoint(x: 1, y: -35)); glyph.addLine(to: CGPoint(x: -12, y: 9)); glyph.addLine(to: CGPoint(x: 15, y: 9))
                glyph.move(to: CGPoint(x: 9, y: -21)); glyph.addLine(to: CGPoint(x: 7, y: 34))
            }
            stroked(&c, glyph, color: rgb(0x242323), width: 6)
            var crease = Path(); crease.move(to: CGPoint(x: -23.5, y: 0)); crease.addLine(to: CGPoint(x: 23.5, y: 0))
            stroked(&c, crease, color: rgb(0x242323), width: 1)
        }
        // Clock top handle and desk toolbar.
        var handle = Path(); handle.move(to: CGPoint(x: 91, y: 330)); handle.addLine(to: CGPoint(x: 99, y: 317)); handle.addLine(to: CGPoint(x: 177, y: 317)); handle.addLine(to: CGPoint(x: 189, y: 331)); handle.closeSubpath()
        context.fill(handle, with: .color(paper)); stroked(&context, handle)
        var power = Path(); power.addArc(center: CGPoint(x: 42, y: 789), radius: 10, startAngle: .degrees(-55), endAngle: .degrees(235), clockwise: false)
        power.move(to: CGPoint(x: 42, y: 777)); power.addLine(to: CGPoint(x: 42, y: 787)); stroked(&context, power, color: ink, width: 2)
        var sliders = Path()
        for i in 0..<3 {
            let y = 781.0 + Double(i) * 8
            sliders.move(to: CGPoint(x: 341, y: y)); sliders.addLine(to: CGPoint(x: 366, y: y))
            sliders.addEllipse(in: CGRect(x: 346 + Double((i + 1) % 2) * 9, y: y - 2.5, width: 5, height: 5))
        }
        stroked(&context, sliders, color: ink, width: 1.5)
    }

    static func blob(_ context: inout GraphicsContext, x: Double, y: Double, r: Double, m: Double) {
        let (red, green, blue) = M.color(m)
        ellipse(&context, x - r, y - r, r * 2, r * 2, Color(red: red, green: green, blue: blue))
        let radii = M.outline(m), scale = r / 72
        var shape = Path()
        for (i, radius) in radii.enumerated() {
            let a = Double(i) / Double(radii.count) * 2 * .pi
            let point = CGPoint(x: x + cos(a) * radius * 44 * scale, y: y + sin(a) * radius * 44 * scale)
            if i == 0 { shape.move(to: point) } else { shape.addLine(to: point) }
        }
        shape.closeSubpath()
        context.fill(shape, with: .color(.white))
        let tired = faceMetric(m, 7)
        let eyeHalf = faceMetric(m, 0), eyeY = faceMetric(m, 1), eyeSize = faceMetric(m, 2)
        for side in [-1.0, 1.0] {
            ellipse(&context, x + (side * eyeHalf - eyeSize / 2) * scale, y + (eyeY - eyeSize / 2) * scale, eyeSize * scale, eyeSize * scale, ink)
            if tired > 0.01 {
                var eye = Path()
                eye.move(to: CGPoint(x: x + (side * eyeHalf - 4) * scale, y: y + 3 * scale))
                eye.addQuadCurve(to: CGPoint(x: x + (side * eyeHalf + 4) * scale, y: y + 3 * scale), control: CGPoint(x: x + side * eyeHalf * scale, y: y + 1.5 * scale))
                stroked(&context, eye, color: ink.opacity(tired * 0.4), width: 1.5 * scale)
            }
        }
        let half = faceMetric(m, 3), mouthY = faceMetric(m, 4), depth = faceMetric(m, 5)
        var mouth = Path()
        mouth.move(to: CGPoint(x: x - half * scale, y: y + mouthY * scale))
        mouth.addQuadCurve(to: CGPoint(x: x + half * scale, y: y + mouthY * scale), control: CGPoint(x: x, y: y + (mouthY + depth) * scale))
        stroked(&context, mouth, color: ink.opacity(1 - tired * 0.3), width: faceMetric(m, 6) * scale)
    }

    // Source eye and mouth proportions vary by mood, rather than widening every smile equally.
    // eye half-spacing, eye y, eye diameter, mouth half-width, mouth y, curve depth, stroke, tired.
    static let faces: [[Double]] = [
        [10.4, -1.5, 7.2, 10.9, 12, -11, 3.2, 0],
        [9.3, -3, 6.2, 10, 10, -4, 2.3, 1],
        [9, -5, 6.2, 6.8, 4.8, -1.3, 2.5, 0],
        [9, -5.3, 6.2, 7, 4.5, 0, 2.5, 0],
        [8.9, -3, 6.2, 6.5, 6.8, 3, 2.5, 0],
        [8.2, -5.4, 5.7, 7.8, 5.5, 8.2, 2.5, 0],
        [7.8, -4.5, 5.6, 8.6, 6.3, 8.5, 2.4, 0]
    ]
    static func faceMetric(_ mood: Double, _ feature: Int) -> Double {
        let value = M.clamp(mood), index = Int(floor(value))
        return M.mix(faces[index][feature], faces[min(index + 1, 6)][feature], M.ease(value - Double(index)))
    }

    static func icon(_ context: inout GraphicsContext, _ index: Int, x: Double, y: Double, color: Color) {
        var p = Path()
        switch index {
        case 0:
            p.addEllipse(in: CGRect(x: x - 11, y: y - 11, width: 22, height: 22)); p.addEllipse(in: CGRect(x: x - 7, y: y - 7, width: 14, height: 14)); p.addEllipse(in: CGRect(x: x - 3, y: y - 3, width: 6, height: 6))
        case 1:
            p.move(to: CGPoint(x: x - 12, y: y - 10)); p.addLine(to: CGPoint(x: x, y: y - 7)); p.addLine(to: CGPoint(x: x + 12, y: y - 10)); p.addLine(to: CGPoint(x: x + 12, y: y + 10)); p.addLine(to: CGPoint(x: x, y: y + 12)); p.addLine(to: CGPoint(x: x - 12, y: y + 10)); p.closeSubpath(); p.move(to: CGPoint(x: x, y: y - 7)); p.addLine(to: CGPoint(x: x, y: y + 12))
        case 2:
            p.addEllipse(in: CGRect(x: x - 11, y: y - 11, width: 22, height: 22)); for offset in [-5.0, 4] { p.move(to: CGPoint(x: x - 10, y: y + offset)); p.addQuadCurve(to: CGPoint(x: x + 10, y: y + offset + 2), control: CGPoint(x: x, y: y + offset - 5)) }
        case 3:
            for i in 0..<4 { let a = Double(i) * .pi / 4; p.move(to: CGPoint(x: x - 13 * cos(a), y: y - 13 * sin(a))); p.addLine(to: CGPoint(x: x + 13 * cos(a), y: y + 13 * sin(a))) }
        default:
            p.addArc(center: CGPoint(x: x, y: y), radius: 11, startAngle: .degrees(-50), endAngle: .degrees(230), clockwise: false); p.addQuadCurve(to: CGPoint(x: x + 7, y: y - 8), control: CGPoint(x: x + 1, y: y + 4)); p.closeSubpath()
            p.addEllipse(in: CGRect(x: x + 4, y: y - 7, width: 2, height: 2))
        }
        stroked(&context, p, color: color, width: 1.5)
    }

    static func stage(_ context: inout GraphicsContext, _ s: M.Shown, still: Bool) {
        var roomPill = context
        roomPill.opacity = (1 - s.chooser) * (s.origin > 0.5 && s.panel > 0 ? 0 : 1)
        rect(&roomPill, 127.5, 765, 138, 50, .white, radius: 25)
        blob(&roomPill, x: 171, y: 790, r: 13, m: s.m)
        label(&roomPill, M.moods[M.nearest(s.m)].name, x: 220, y: 790, size: 10, weight: .semibold)
        var chooser = context
        chooser.opacity = s.chooser
        chooser.fill(Path(CGRect(x: 0, y: 0, width: 393, height: 852)), with: .linearGradient(Gradient(stops: [.init(color: paper.opacity(0.45), location: 0), .init(color: paper, location: 0.18), .init(color: paper, location: 1)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 852)))
        let names = ["FOCUS", "STUDYING", "RELAX", "WORK", "SLEEP"]
        let colors: [UInt32] = [0x7EC9D2, 0xEACD73, 0xDB92B5, 0xA5A0E0, 0xB59ACF]
        var menu = chooser
        menu.opacity *= 0.58 * (1 - s.panel * 0.4)
        let widths = [183.4, 209.6, 181.9, 186.3, 177.6]
        let firstY = M.menuTrack(s.panel, 158.7, 186.3, 215.4)
        let spacing = M.menuTrack(s.panel, 89, 67.15, 36.2)
        let scale = M.menuTrack(s.panel, 1, 0.96, 0.55)
        for i in 0..<5 {
            var pill = menu
            pill.translateBy(x: 196.5, y: firstY + Double(i) * spacing)
            pill.scaleBy(x: scale, y: scale)
            rect(&pill, -widths[i] / 2, -33.5, widths[i], 67, .white.opacity(0.65), radius: 33.5)
            icon(&pill, i, x: -45.5, y: 0, color: rgb(colors[i]))
            label(&pill, names[i], x: 21.5, y: 0, size: 12, weight: .semibold, color: rgb(0xAAA69C), tracking: 0.2)
        }
        var question = chooser; question.opacity *= 1 - 0.9 * M.ease(M.segment(s.panel, 0.355, 0.8))
        label(&question, "HOW ARE YOU FEELING?", x: 196.5, y: M.menuTrack(s.panel, 599, 507, 374), size: 14, weight: .bold)
        label(&question, "Automatically match sounds based on mood", x: 196.5, y: M.menuTrack(s.panel, 624, 531, 396), size: 11, color: rgb(0x99958A))
        ellipse(&chooser, 172.5, 760, 48, 48, rgb(0xE7E1D5, 0.6))
        var close = Path(); close.move(to: CGPoint(x: 189.5, y: 777)); close.addLine(to: CGPoint(x: 203.5, y: 791)); close.move(to: CGPoint(x: 203.5, y: 777)); close.addLine(to: CGPoint(x: 189.5, y: 791)); stroked(&chooser, close, color: ink, width: 1.5)
        if s.closing {
            var item = chooser
            let body = M.ease(M.segment(s.panel, 0.15, 1))
            item.opacity *= 1 - body
            rect(&item, 196.5 - M.itemWidth(s.m) / 2, 652, M.itemWidth(s.m), 68, .white, radius: 34)
            inline(&item, s.m, origin: 0)
            var expanded = context
            expanded.opacity = s.panel
            picker(&expanded, s, progress: 1, still: still, bodyAlpha: s.panel > 0.001 ? body / s.panel : 0)
        } else {
            var surface = context
            surface.opacity = s.origin > 0.5 && s.panel > 0 ? 1 : s.chooser
            picker(&surface, s, progress: s.panel, still: still)
        }
    }

    static func inline(_ context: inout GraphicsContext, _ mood: Double, origin: Double) {
        blob(&context, x: M.mix(247 - M.itemWidth(mood) / 2, 171, origin), y: M.mix(686, 790, origin), r: M.mix(17, 13, origin), m: mood)
        label(&context, M.moods[M.nearest(mood)].name, x: M.mix(216, 220, origin), y: M.mix(686, 790, origin), size: M.mix(13, 10, origin), weight: .semibold, color: origin > 0.5 ? ink : rgb(0x77746E))
        var arrow = context; arrow.opacity *= 1 - origin
        let arrowX = 168 + M.itemWidth(mood) / 2
        var down = Path(); down.move(to: CGPoint(x: arrowX - 5, y: 682)); down.addLine(to: CGPoint(x: arrowX, y: 687)); down.addLine(to: CGPoint(x: arrowX + 5, y: 682)); stroked(&arrow, down, color: rgb(0xAAA69D), width: 1.5)
    }

    static func picker(_ context: inout GraphicsContext, _ s: M.Shown, progress p: Double, still: Bool, bodyAlpha: Double = 1) {
        let origin = s.closing ? 0 : s.origin
        let x = M.mix(M.mix(196.5 - M.itemWidth(s.m) / 2, 127.5, origin), 10.2, p)
        let y = M.mix(M.mix(652, 765, origin), 418, p)
        let w = M.mix(M.mix(M.itemWidth(s.m), 138, origin), 372.6, p)
        let h = M.mix(M.mix(68, 50, origin), 306, p)
        let bounds = Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: min(h / 2, 56))
        let content = M.ease(M.segment(p, 0.35, 0.96))
        let q = 1 - pow(1 - p, 3)
        var panel = context
        panel.clip(to: bounds)
        var body = panel; body.opacity *= bodyAlpha
        body.fill(bounds, with: .color(.white))
        var old = panel; old.opacity *= 1 - content
        inline(&old, s.m, origin: origin)
        var details = panel; details.opacity *= content
        var footer = details; footer.opacity *= bodyAlpha
        rect(&footer, x, y + h - 68, w, 68, rgb(0xFAF7F5))
        blob(&details, x: M.mix(M.mix(247 - M.itemWidth(s.m) / 2, 171, origin), 196.5, q), y: M.mix(M.mix(686, 790, origin), 563, q), r: M.mix(M.mix(17, 13, origin), 72, q), m: s.m)
        ellipse(&details, 54 + s.m * 47.8 - 14 * content, y + h - 35 - 14 * content, 28 * content, 28 * content, .white)
        // The source title fades above the still-expanding card rather than being clipped by it.
        var title = context; title.opacity *= content
        var ghost = title; ghost.opacity *= 0.18
        label(&ghost, M.title(s.m, step: Int(floor(s.clock * 24)), ghost: true, still: still), x: 198.5, y: 464, size: 19, weight: .bold, tracking: 0.2)
        label(&title, M.title(s.m, step: Int(floor(s.clock * 24)), still: still), x: 196.5, y: 459, size: 19, weight: .bold, tracking: 0.2)

    }
}
