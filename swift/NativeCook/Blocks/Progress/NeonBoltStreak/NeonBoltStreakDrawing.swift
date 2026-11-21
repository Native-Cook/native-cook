import SwiftUI

enum NeonBoltStreakDrawing {
    static let page = Color(red: 0.149, green: 0.153, blue: 0.169)
    static let panel = Color(red: 0.090, green: 0.094, blue: 0.106)
    static let tube = Color(red: 1.0, green: 0.851, blue: 0.102)
    static let core = Color(red: 1.0, green: 0.971, blue: 0.788)
    static let zinc = Color(red: 0.49, green: 0.49, blue: 0.47)
    static let dead = Color(red: 0.427, green: 0.431, blue: 0.451)

    static func scene(_ context: inout GraphicsContext, size: CGSize, frame: NeonBoltStreakFrame) {
        let scale = size.width / 402
        let vertical = (size.height / scale - 874) / 2
        context.translateBy(x: 0, y: vertical * scale)
        context.scaleBy(x: scale, y: scale)
        context.fill(Path(CGRect(x: 0, y: -vertical, width: 402, height: 874)), with: .color(page))
        grain(&context, phase: frame.time)
        var stage = context
        stage.translateBy(x: 0, y: frame.jolt)
        var heroContext = stage
        heroContext.opacity = Double(1 - frame.camera)
        hero(&heroContext, frame: frame)
        week(&stage, frame: frame)
    }

    static func hero(_ context: inout GraphicsContext, frame: NeonBoltStreakFrame) {
        let path = boltPath(in: CGRect(x: 106, y: 150, width: 190, height: 292))
        for (radius, opacity) in [(100.0, 0.06 + 0.13 * Double(frame.flood)), (55.0, 0.10 + 0.18 * Double(frame.flood))] {
            context.fill(Path(ellipseIn: CGRect(x: 201 - radius, y: 296 - radius, width: radius * 2, height: radius * 2)), with: .radialGradient(Gradient(colors: [tube.opacity(opacity), .clear]), center: CGPoint(x: 201, y: 296), startRadius: 0, endRadius: radius))
        }
        ghostBolts(&context, alpha: frame.ghost, yellow: frame.flood)
        context.fill(path, with: .color(page))
        var lit = context
        lit.clip(to: path)
        let level = 0.86 * min(max(frame.count, 0), 7) / 7 + 0.14 * frame.flood
        let extent = 292 * (0.42 + 1.43 * level)
        lit.fill(Path(CGRect(x: 106, y: 150, width: 190, height: 292)), with: .linearGradient(Gradient(stops: [
            .init(color: Color(red: 0.961, green: 0.824, blue: 0.122), location: 0),
            .init(color: Color(red: 0.788, green: 0.647, blue: 0.086), location: 0.55),
            .init(color: page, location: 1)
        ]), startPoint: CGPoint(x: 201, y: 150), endPoint: CGPoint(x: 201, y: 150 + extent)))
        context.stroke(path, with: .color(.white.opacity(0.10)), lineWidth: 0.7)
        var drum = context
        drum.clip(to: Path(CGRect(x: 0, y: 328, width: 402, height: 128)))
        let digit = floor(frame.count + 0.0001)
        let fraction = frame.count - digit
        for step in 0...1 {
            drawBaseline(&drum, String((Int(digit) + step + 10) % 10), at: CGPoint(x: 207, y: 440 + (CGFloat(step) - fraction) * 128), font: .system(size: 128, weight: .heavy), color: .white)
        }
        drawBaseline(&context, "days streak", at: CGPoint(x: 201, y: 495), font: .system(size: 28, weight: .bold), color: Color(red: 0.549 + 0.239 * frame.flash, green: 0.553 + 0.239 * frame.flash, blue: 0.573 + 0.239 * frame.flash))
        if frame.flash > 0 {
            context.fill(Path(ellipseIn: CGRect(x: 201 - 180, y: 295 - 180, width: 360, height: 360)), with: .radialGradient(Gradient(colors: [tube.opacity(Double(frame.flash) * 0.42), .clear]), center: CGPoint(x: 201, y: 295), startRadius: 0, endRadius: 180))
        }
    }

    static func week(_ context: inout GraphicsContext, frame: NeonBoltStreakFrame) {
        let rect = CGRect(x: 16, y: 560, width: 370, height: 168)
        context.fill(Path(roundedRect: rect, cornerRadius: 20), with: .color(panel))
        context.stroke(Path(roundedRect: rect, cornerRadius: 20), with: .color(Color(red: 0.23, green: 0.235, blue: 0.255)), lineWidth: 2)
        if frame.borderLit { border(&context, rect: rect, progress: frame.border) }
        for index in 0..<7 {
            let center = CGPoint(x: 16 + 370 / 7 * (CGFloat(index) + 0.5), y: 629)
            dayBolt(&context, center: center, frame: frame, index: index)
            drawBaseline(&context, NeonBoltStreakMotion.days[index], at: CGPoint(x: center.x, y: 689), font: .system(size: 13, weight: .medium), color: dead)
            var label = context
            label.opacity = Double(frame.label[index])
            drawBaseline(&label, NeonBoltStreakMotion.days[index], at: CGPoint(x: center.x, y: 689), font: .system(size: 13, weight: .medium), color: Color(red: 0.949, green: 0.812, blue: 0.165))
        }
        if frame.strikeDay >= 0, frame.strikeDrop < 1 {
            let x = 23 + 370 / 7 * (CGFloat(frame.strikeDay) + 0.5)
            let height: CGFloat = frame.camera > 0.5 ? 170 : 60
            let y = 601 - height * (1 - frame.strikeDrop)
            let path = boltPath(in: CGRect(x: x - 7, y: y - 11, width: 14, height: 22))
            var spark = context
            spark.addFilter(.shadow(color: tube.opacity(0.8), radius: 12, x: 0, y: 0))
            spark.fill(path, with: .color(core))
        }
        if frame.strikeDay >= 0, frame.charge > 0.02 {
            let x = 16 + 370 / 7 * (CGFloat(frame.strikeDay) + 0.5)
            let arc = Path(ellipseIn: CGRect(x: x - 25, y: 604, width: 50, height: 50)).trimmedPath(from: 0.08, to: 0.08 + frame.charge * 0.84)
            context.stroke(arc, with: .color(tube.opacity(Double(0.3 + frame.charge * 0.7))), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }
    }

    static func dayBolt(_ context: inout GraphicsContext, center: CGPoint, frame: NeonBoltStreakFrame, index: Int) {
        let centre = CGPoint(x: center.x, y: center.y + frame.dy[index])
        let right = chainPath(rightChain, center: centre)
        let left = chainPath(leftChain, center: centre)
        let fill = min(frame.fill[index], 1)
        let unburntRight = remainingChain(rightChain, fill: frame.burn[index])
        let unburntLeft = remainingChain(leftChain, fill: frame.burn[index])
        var zincPath = chainPath(unburntRight, center: centre)
        for point in unburntLeft.reversed() { zincPath.addLine(to: CGPoint(x: centre.x + point.x, y: centre.y + point.y)) }
        zincPath.closeSubpath()
        context.fill(zincPath, with: .linearGradient(Gradient(colors: [Color(red: 0.79, green: 0.784, blue: 0.753), Color(red: 0.545, green: 0.541, blue: 0.518), Color(red: 0.33, green: 0.329, blue: 0.31)]), startPoint: CGPoint(x: centre.x - 20, y: centre.y - 30), endPoint: CGPoint(x: centre.x + 20, y: centre.y + 30)))
        context.stroke(zincPath, with: .color(Color.white.opacity(0.12)), lineWidth: 0.6)
        for path in [right, left] {
            context.stroke(path.trimmedPath(from: min(frame.burn[index], 1), to: 1), with: .color(zinc), style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
        }
        guard fill > 0 else { return }
        let litLeft = left.trimmedPath(from: 0, to: fill)
        let litRight = right.trimmedPath(from: 0, to: fill)
        var glow = context
        glow.addFilter(.shadow(color: tube.opacity(0.80 * Double(frame.lit[index])), radius: 15, x: 0, y: 0))
        var nearGlow = context
        nearGlow.addFilter(.blur(radius: 3.5))
        let gain = Double(min(frame.lit[index], 1))
        for path in [litLeft, litRight] {
            nearGlow.stroke(path, with: .color(tube.opacity(0.8 * gain)), style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
            glow.stroke(path, with: .color(tube.opacity(gain)), style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
            context.stroke(path, with: .color(tube.opacity(gain)), style: StrokeStyle(lineWidth: 3.5, lineCap: .round, lineJoin: .round))
            context.stroke(path, with: .color(core.opacity(gain)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
        }
        if frame.head[index] > 0 {
            for chain in [rightChain, leftChain] {
                let offset = chainPoint(chain, fill: fill)
                let point = CGPoint(x: centre.x + offset.x, y: centre.y + offset.y)
                context.fill(Path(ellipseIn: CGRect(x: point.x - 7, y: point.y - 7, width: 14, height: 14)), with: .radialGradient(Gradient(colors: [core.opacity(Double(frame.head[index])), .clear]), center: point, startRadius: 0, endRadius: 7))
            }
        }
    }

    static func border(_ context: inout GraphicsContext, rect: CGRect, progress: CGFloat) {
        let path = Path(roundedRect: rect.insetBy(dx: 3, dy: 3), cornerRadius: 17)
        let lit = path.trimmedPath(from: 0, to: min(progress, 1))
        var glow = context
        glow.addFilter(.shadow(color: tube.opacity(0.65), radius: 10, x: 0, y: 0))
        glow.stroke(lit, with: .color(tube), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        context.stroke(lit, with: .color(core), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
    }

    static func ghostBolts(_ context: inout GraphicsContext, alpha: CGFloat, yellow: CGFloat) {
        let positions = [CGPoint(x: 24, y: 296), CGPoint(x: 92, y: 296), CGPoint(x: 310, y: 296), CGPoint(x: 378, y: 296)]
        for point in positions {
            let width: CGFloat = 190 * 0.62
            let height: CGFloat = 292 * 0.62
            let path = boltPath(in: CGRect(x: point.x - width / 2, y: point.y - height / 2, width: width, height: height))
            let tint = Color(red: 1, green: 1 - 0.15 * yellow, blue: 1 - 0.90 * yellow)
            context.fill(path, with: .color(tint.opacity(Double(alpha))))
        }
    }

    static func grain(_ context: inout GraphicsContext, phase: Double) {
        for index in 0..<90 {
            let x = CGFloat((index * 47) % 401)
            let y = CGFloat((index * 71) % 870)
            let alpha = 0.014 + 0.008 * sin(phase * 2 * .pi / NeonBoltStreakMotion.loop + Double(index))
            context.fill(Path(CGRect(x: x, y: y, width: 1, height: 1)), with: .color(.white.opacity(alpha)))
        }
    }

    static func drawText(_ context: inout GraphicsContext, _ text: String, at point: CGPoint, font: Font, color: Color, anchor: UnitPoint) {
        context.draw(Text(text).font(font).foregroundColor(color), at: point, anchor: anchor)
    }

    static func drawBaseline(_ context: inout GraphicsContext, _ text: String, at point: CGPoint, font: Font, color: Color) {
        let resolved = context.resolve(Text(text).font(font).foregroundColor(color))
        let bounds = CGSize(width: 402, height: 200)
        let width = resolved.measure(in: bounds).width
        context.draw(resolved, at: CGPoint(x: point.x - width / 2, y: point.y - resolved.firstBaseline(in: bounds)), anchor: .topLeading)
    }

    static let rightChain: [CGPoint] = [(-5.89,28),(-4.5,27.67),(-3.34,26.83),(17.14,4.96),(17.91,2.01),(15.86,-0.25),(7.36,-3.22),(6.29,-4.11),(6.06,-5.49),(9.61,-23.96),(7.79,-27.64),(7.77,-27.64)].map { CGPoint(x: $0.0, y: $0.1) }
    static let leftChain: [CGPoint] = [(-9.23,26.18),(-9.7,24.83),(-9.63,23.41),(-5.54,7.02),(-5.75,5.43),(-6.98,4.4),(-15.82,1.43),(-17.9,-0.8),(-17.17,-3.75),(3.75,-26.88),(7.77,-27.64)].map { CGPoint(x: $0.0, y: $0.1) }

    static func chainPath(_ points: [CGPoint], center: CGPoint) -> Path {
        Path { path in
            for (index, point) in points.enumerated() {
                let translated = CGPoint(x: center.x + point.x, y: center.y + point.y)
                if index == 0 { path.move(to: translated) } else { path.addLine(to: translated) }
            }
        }
    }

    static func chainPoint(_ points: [CGPoint], fill: CGFloat) -> CGPoint {
        let lengths = zip(points, points.dropFirst()).map { hypot($1.x - $0.x, $1.y - $0.y) }
        var remaining = lengths.reduce(0, +) * min(max(fill, 0), 1)
        for (index, length) in lengths.enumerated() {
            if remaining <= length {
                let k = remaining / max(length, 0.001)
                return CGPoint(x: points[index].x + (points[index + 1].x - points[index].x) * k, y: points[index].y + (points[index + 1].y - points[index].y) * k)
            }
            remaining -= length
        }
        return points.last ?? .zero
    }

    static func remainingChain(_ points: [CGPoint], fill: CGFloat) -> [CGPoint] {
        guard fill < 1 else { return [points.last ?? .zero] }
        let lengths = zip(points, points.dropFirst()).map { hypot($1.x - $0.x, $1.y - $0.y) }
        var remaining = lengths.reduce(0, +) * max(fill, 0)
        for (index, length) in lengths.enumerated() {
            if remaining <= length { return [chainPoint(points, fill: fill)] + Array(points[(index + 1)...]) }
            remaining -= length
        }
        return [points.last ?? .zero]
    }

    static func boltPath(in rect: CGRect) -> Path {
        var path = Path()
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * rect.width / 190, y: rect.minY + y * rect.height / 292) }
        path.move(to: point(133.509,2.582))
        path.addLine(to: point(2.685,131.354))
        path.addQuadCurve(to: point(6.418,146.390), control: point(-7,141))
        path.addLine(to: point(63.853,163.584))
        path.addQuadCurve(to: point(67.910,170.950), control: point(70,165))
        path.addLine(to: point(36.838,281.841))
        path.addQuadCurve(to: point(50.055,289.796), control: point(32,299))
        path.addLine(to: point(187.207,159.329))
        path.addQuadCurve(to: point(183.680,144.215), control: point(197,150))
        path.addLine(to: point(128.252,126.957))
        path.addQuadCurve(to: point(124.851,121.129), control: point(123,125))
        path.addLine(to: point(148.620,10.893))
        path.addQuadCurve(to: point(133.509,2.582), control: point(153,-7))
        path.closeSubpath()
        return path
    }

    static func tubePath(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + rect.width * 0.29, y: rect.maxY - 2))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.42, y: rect.minY + rect.height * 0.54))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.10, y: rect.minY + rect.height * 0.45))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.62, y: rect.minY + 2))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.47, y: rect.minY + rect.height * 0.42))
        path.addLine(to: CGPoint(x: rect.maxX - 2, y: rect.minY + rect.height * 0.56))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.29, y: rect.maxY - 2))
        return path
    }
}
