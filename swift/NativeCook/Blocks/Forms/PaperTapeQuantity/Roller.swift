import SwiftUI

enum PaperTapeQuantityRollerMetrics {
    static let bar = 84.0
    static let pitch = 53.0
    static let tail = 49.0
    static let tape = 54.0
    static let drumAt = 62.0
    static let drumWidth = 70.0
    static let drumHeight = 50.0
    static let tileAt = 124.0
    static let tileWidth = 36.0
    static let tileHeight = 50.0
    static let bleed = 24.0
    static let cap = 12.5
    static let drumRadius = 8.0
    static let well = 6.0
    static let labelWidth = 37.0
    static let labelHeight = 26.0
    static let wrap = drumHeight / 2
    static let arc = wrap * .pi / 2
    static let smear = 0.8
    static let smearMax = 6.5
    static let lean = [-5.0, 4.0, -3.0, 5.0, -4.0, 3.0, -5.0, 4.0, -2.0, 5.0]
    static let digitScale = 0.8
    static let pairScale = 0.62
    static func reach(_ max: Int) -> Double { Double(max - 1) * pitch + tail - bar / 2 }
}

private enum PaperTapeQuantityRollerDrawing {
    static let ink = Color(hex: 0x2a2a2c)
    static let paper = [Color(hex: 0xf4f3f1), Color(hex: 0xfdfdfc), Color(hex: 0xf1f0ee)]
    static let orange = [Color(hex: 0x8c3a0c), Color(hex: 0xe9620f), Color(hex: 0xff9440), Color(hex: 0xf26a1b), Color(hex: 0x7e330a)]
    static let digits = [
        "M10 3.5C5.5 3.5 4.5 11 5 16C5.5 22 9 24 11.5 22.5C15.5 20 16 9.5 14 5.5C13 3.8 11.5 3.3 10 3.5",
        "M6.5 8C8.5 6.5 10 5 11.5 3C11 9 10.6 16 10.2 23",
        "M5 8C6 3.5 13 2.5 14 7.5C14.8 12 8 17 4.5 22.5C8 20.5 12 22.5 16 21.5",
        "M5.5 5C8.5 2.5 14.5 3 14 7.5C13.6 10.5 10.5 11.8 8.5 12.2C12 12 15.5 14 14.8 18C14 22.5 7.5 23.8 4.5 20.5",
        "M12.5 3C10 8 7 12.5 4.5 15.5C8.5 15.8 12.5 15.5 16.5 15M13 9C12.6 14 12.3 19 12 23.5",
        "M14.5 3.5C11.8 3.3 9.5 3.6 7.5 4C7 6.8 6.6 9.2 6.2 12C9 10.2 14.5 10.8 14.8 15.5C15 20.5 9 24 4.8 20.8",
        "M13.5 3C8.5 6 5 12 5.5 18C6 23.5 13.5 24 14.5 18.5C15.2 14 9 12.5 6 16.5",
        "M4.5 4.5C8 4.8 12 4.4 15.5 3.5C12.5 9.5 9.8 16 8 23.5",
        "M10.5 12.5C5.5 10 6.5 3.5 10.8 3.5C15.5 3.5 15.2 9.5 10.5 12.5C5 15.5 4.5 23 10 23C15.5 23 15.5 15.5 10.5 12.5",
        "M14.5 8.5C13 2.5 5.5 3.5 6 9C6.4 13.5 12.5 13 14.5 8.5C14.6 13.5 13.5 19 11 23.5",
    ].map(path)
    static let up = path("M-6 3.6L0 -3.6L6 3.6Z")
    static let down = path("M-6 -3.6L0 3.6L6 -3.6Z")
    static let gaussian: [(offset: Double, weight: Double)] = {
        let offsets = (-4...4).map { Double($0) * 0.75 }
        let sum = offsets.reduce(0) { $0 + exp(-0.5 * $1 * $1) }
        return offsets.map { ($0, exp(-0.5 * $0 * $0) / sum) }
    }()

    /// These marker paths only use absolute moves, lines and cubic curves; parse them once.
    private static func path(_ source: String) -> Path {
        let pattern = try! NSRegularExpression(pattern: "[MCLZ]|-?[0-9]+(?:\\.[0-9]+)?")
        let ns = source as NSString
        let tokens = pattern.matches(in: source, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range) }
        var result = Path(); var index = 0; var command = "M"
        func number() -> Double { defer { index += 1 }; return Double(tokens[index])! }
        while index < tokens.count {
            if ["M", "C", "L", "Z"].contains(tokens[index]) { command = tokens[index]; index += 1 }
            switch command {
            case "M": result.move(to: CGPoint(x: number(), y: number())); command = "L"
            case "L": result.addLine(to: CGPoint(x: number(), y: number()))
            case "C":
                let c1 = CGPoint(x: number(), y: number()); let c2 = CGPoint(x: number(), y: number())
                result.addCurve(to: CGPoint(x: number(), y: number()), control1: c1, control2: c2)
            case "Z": result.closeSubpath()
            default: break
            }
        }
        return result
    }
    static func rounded(_ rect: CGRect, _ radius: Double) -> Path { RoundedRectangle(cornerRadius: radius, style: .circular).path(in: rect) }
    static func fill(_ rect: CGRect, radius: Double, color: Color, in context: GraphicsContext, blur: Double = 0) {
        var copy = context; if blur > 0 { copy.addFilter(.blur(radius: blur)) }
        copy.fill(rounded(rect, radius), with: .color(color))
    }
    static func gradient(_ rect: CGRect, radius: Double = 0, colors: [Color], positions: [Double]? = nil, horizontal: Bool = false, in context: GraphicsContext) {
        let stops = colors.enumerated().map { Gradient.Stop(color: $0.element, location: positions?[$0.offset] ?? Double($0.offset) / Double(colors.count - 1)) }
        context.fill(rounded(rect, radius), with: .linearGradient(Gradient(stops: stops), startPoint: CGPoint(x: rect.minX, y: rect.minY), endPoint: horizontal ? CGPoint(x: rect.maxX, y: rect.minY) : CGPoint(x: rect.minX, y: rect.maxY)))
    }
    /// Canvas has an isotropic blur. Gaussian-weighted isolated copies smear only vertically.
    static func verticalBlur(_ sigma: Double, in context: GraphicsContext, draw: (GraphicsContext) -> Void) {
        if sigma < 0.001 { draw(context); return }
        context.drawLayer { layer in
            for sample in gaussian {
                var sum = layer; sum.opacity = sample.weight; sum.blendMode = .plusLighter
                sum.drawLayer { image in
                    image.opacity = 1; image.blendMode = .normal
                    image.translateBy(x: 0, y: sample.offset * sigma)
                    draw(image)
                }
            }
        }
    }
    static func label(_ value: Int, in context: GraphicsContext) {
        typealias S = PaperTapeQuantityRollerMetrics
        let rect = CGRect(x: -S.labelWidth / 2, y: -S.labelHeight / 2, width: S.labelWidth, height: S.labelHeight)
        context.fill(rounded(rect, 5), with: .color(.white.opacity(0.55)))
        context.stroke(rounded(rect, 5), with: .color(.black.opacity(0.11)), lineWidth: 1)
        let numerals = String(value).compactMap { $0.wholeNumberValue }
        let scale = numerals.count > 1 ? S.pairScale : S.digitScale
        let advance = 15 * scale
        var leaned = context; leaned.rotate(by: .degrees(S.lean[value % S.lean.count]))
        for (index, number) in numerals.enumerated() {
            var digit = leaned
            digit.translateBy(x: (Double(index) - Double(numerals.count - 1) / 2) * advance - 10 * scale, y: -13 * scale)
            digit.scaleBy(x: scale, y: scale)
            digit.stroke(digits[number], with: .color(ink), style: StrokeStyle(lineWidth: 2.5 / scale, lineCap: .round, lineJoin: .round))
        }
    }
}

/// The add-to-cart roller only draws; the parent owns the controls and the frame clock.
struct PaperTapeQuantityRoller: View {
    var width: Double
    var margin: Double
    var max: Int
    var place: Double
    var upHeld: Double
    var downHeld: Double
    var canUp: Bool
    var canDown: Bool
    var still: Bool
    var smear: Double? = nil
    @State private var previousPlace: Double? = nil
    @State private var previousFrame: Date? = nil
    @State private var measuredSmear = 0.0
    private typealias S = PaperTapeQuantityRollerMetrics
    private typealias D = PaperTapeQuantityRollerDrawing

    var body: some View {
        TimelineView(.animation) { timeline in
        Canvas { context, _ in
            let above = S.reach(max) + S.bleed
            let cy = above + S.bar / 2
            let bar = CGRect(x: margin, y: above, width: width - margin * 2, height: S.bar)
            let drumX = margin + S.drumAt
            let drum = CGRect(x: drumX - S.drumWidth / 2, y: cy - S.drumHeight / 2, width: S.drumWidth, height: S.drumHeight)
            let tapeX = drumX - S.tape / 2
            let tile = CGRect(x: margin + S.tileAt, y: cy - S.tileHeight / 2, width: S.tileWidth, height: S.tileHeight)
            let numbers = max > 0 ? Array(1...max) : []
            let sigma = still ? 0 : Swift.min(S.smearMax, smear ?? measuredSmear)
            D.verticalBlur(sigma, in: context) { paper in
                var travel = paper; travel.translateBy(x: 0, y: -(place - 1) * S.pitch)
                let length = Double(max - 1) * S.pitch + S.tail * 2
                D.fill(CGRect(x: tapeX + 5, y: cy - S.tail + 7, width: S.tape, height: length), radius: 4, color: .black.opacity(0.2), in: travel, blur: 8)
                D.gradient(CGRect(x: tapeX, y: cy - S.tail, width: S.tape, height: length), radius: 3, colors: D.paper, horizontal: true, in: travel)
                for value in numbers {
                    var label = travel; label.translateBy(x: drumX, y: cy + Double(value - 1) * S.pitch)
                    D.label(value, in: label)
                }
            }
            D.fill(bar.offsetBy(dx: 0, dy: 12), radius: S.bar / 2, color: Color.rgba(60, 50, 40, 0.26), in: context, blur: 14)
            D.gradient(bar, radius: S.bar / 2, colors: [Color.rgba(240, 239, 237, 0.93), Color.rgba(226, 224, 222, 0.9), Color.rgba(217, 214, 212, 0.9), Color.rgba(234, 233, 231, 0.93)], positions: [0, 0.3, 0.62, 1], in: context)
            context.stroke(D.rounded(bar.insetBy(dx: 1, dy: 1), S.bar / 2 - 1), with: .linearGradient(Gradient(colors: [.white, .white.opacity(0.75), .white.opacity(0.95)]), startPoint: CGPoint(x: 0, y: bar.minY), endPoint: CGPoint(x: 0, y: bar.maxY)), lineWidth: 2)

            let well = CGRect(x: drum.minX - S.well, y: drum.minY - S.well + 1, width: S.drumWidth + S.well * 2, height: S.drumHeight + S.well * 2 - 2)
            D.fill(well, radius: S.drumRadius + 4, color: Color(hex: 0xcfccca), in: context)
            var sunk = context; sunk.clip(to: D.rounded(well, S.drumRadius + 4)); sunk.addFilter(.blur(radius: 3))
            sunk.stroke(D.rounded(CGRect(x: drum.minX - S.well, y: drum.minY - S.well - 3, width: S.drumWidth + S.well * 2, height: S.drumHeight + S.well * 2), S.drumRadius + 4), with: .color(.black.opacity(0.3)), lineWidth: 5)
            context.stroke(D.rounded(well, S.drumRadius + 4), with: .color(.white.opacity(0.8)), lineWidth: 1)
            for x in [drum.minX - 5, drum.minX + S.drumWidth - 2] {
                D.gradient(CGRect(x: x, y: cy - 13, width: 7, height: 26), radius: 2, colors: [Color(hex: 0x7d8482), Color(hex: 0xc9cfcd), Color(hex: 0x9ba19f), Color(hex: 0x6f7573)], positions: [0, 0.35, 0.7, 1], in: context)
            }
            D.fill(CGRect(x: drum.minX + 1, y: drum.minY + 4, width: S.drumWidth - 2, height: S.drumHeight), radius: S.drumRadius, color: .black.opacity(0.3), in: context, blur: 4)
            var cylinder = context; cylinder.clip(to: D.rounded(drum, S.drumRadius))
            cylinder.fill(Path(drum), with: .color(Color(hex: 0xfbfaf9)))
            D.verticalBlur(sigma, in: cylinder) { blurred in
                for value in numbers {
                    let distance = (Double(value) - place) * S.pitch
                    if abs(distance) < S.arc {
                        let turn = Swift.max(-S.arc, Swift.min(S.arc, distance)) / S.wrap
                        var wrapped = blurred
                        wrapped.translateBy(x: drumX, y: cy + S.wrap * sin(turn))
                        wrapped.scaleBy(x: 1, y: Swift.max(0.02, cos(turn)))
                        D.label(value, in: wrapped)
                    }
                }
            }
            for (side, x) in [drum.minX, drum.minX + S.drumWidth - S.cap].enumerated() {
                D.gradient(CGRect(x: x, y: drum.minY, width: S.cap, height: S.drumHeight), colors: D.orange, positions: [0, 0.2, 0.42, 0.72, 1], in: cylinder)
                for rib in [2.4, 5.0, 7.6, 10.2] {
                    var path = Path(); path.move(to: CGPoint(x: x + rib, y: drum.minY)); path.addLine(to: CGPoint(x: x + rib, y: drum.maxY))
                    cylinder.stroke(path, with: .color(Color.rgba(255, 214, 170, 0.5)), lineWidth: 1)
                }
                cylinder.fill(Path(CGRect(x: side == 0 ? x + S.cap - 1 : x, y: drum.minY, width: 1, height: S.drumHeight)), with: .color(Color.rgba(90, 30, 0, 0.55)))
            }
            D.gradient(drum, colors: [.black.opacity(0.42), .black.opacity(0.06), .white.opacity(0.14), .black.opacity(0.05), .black.opacity(0.4)], positions: [0, 0.2, 0.4, 0.74, 1], in: cylinder)

            D.fill(tile.offsetBy(dx: 0, dy: 3), radius: 8, color: .black.opacity(0.2), in: context, blur: 4)
            var arrows = context; arrows.translateBy(x: 0, y: -0.6 * upHeld + 0.6 * downHeld)
            D.gradient(tile, radius: 8, colors: [Color(hex: 0xf7f6f4), Color(hex: 0xeceae8)], in: arrows)
            arrows.stroke(D.rounded(tile.insetBy(dx: 0.5, dy: 0.5), 7.5), with: .color(.white.opacity(0.9)), lineWidth: 1)
            var halves = arrows; halves.clip(to: D.rounded(tile, 8))
            halves.fill(Path(CGRect(x: tile.minX, y: tile.minY, width: S.tileWidth, height: S.tileHeight / 2)), with: .color(.black.opacity(0.1 * upHeld)))
            halves.fill(Path(CGRect(x: tile.minX, y: cy, width: S.tileWidth, height: S.tileHeight / 2)), with: .color(.black.opacity(0.1 * downHeld)))
            arrows.fill(Path(CGRect(x: tile.minX + 8, y: cy - 0.5, width: S.tileWidth - 16, height: 1)), with: .color(.black.opacity(0.08)))
            arrows.fill(D.up.applying(CGAffineTransform(translationX: tile.midX, y: cy - 11.5)), with: .color(D.ink.opacity(canUp ? 0.9 : 0.25)))
            arrows.fill(D.down.applying(CGAffineTransform(translationX: tile.midX, y: cy + 11.5)), with: .color(D.ink.opacity(canDown ? 0.9 : 0.25)))
        }
        .onChange(of: timeline.date) { _, date in
            if let before = previousPlace, let last = previousFrame, date > last {
                measuredSmear = still ? 0 : Swift.min(S.smearMax, S.smear * abs(place - before) / date.timeIntervalSince(last))
            }
            previousPlace = place; previousFrame = date
        }
        }
        .frame(width: width, height: (S.reach(max) + S.bleed) * 2 + S.bar)
        .accessibilityHidden(true)
    }
}
