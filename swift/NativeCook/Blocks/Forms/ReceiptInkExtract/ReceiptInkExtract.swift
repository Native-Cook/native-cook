import SwiftUI
import UIKit

/// A deterministic ink-transfer scanner. All drawing uses a 393 × 852 reference canvas.
struct ReceiptInkExtract: View {
    static let loop: TimeInterval = 8
    var autoPlay = true
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @State private var epoch = Date()
    @State private var lastTouch: Date?
    @State private var scrub = 0.0
    @State private var dragging = false
    @State private var detent = -1

    private static let ink = Color(red: 0.149, green: 0.235, blue: 0.220)
    private static let muted = Color(red: 0.486, green: 0.529, blue: 0.502)
    private static let coral = Color(red: 0.937, green: 0.451, blue: 0.369)
    private static let backdrop = Color(red: 0.953, green: 0.945, blue: 0.922)

    var body: some View {
        GeometryReader { geo in
            let scale = min(geo.size.width / 393, geo.size.height / 852)
            let origin = CGPoint(x: (geo.size.width - 393 * scale) / 2, y: (geo.size.height - 852 * scale) / 2)
            TimelineView(.animation(paused: reducedMotion || (!autoPlay && lastTouch == nil))) { tick in
                let elapsed = tick.date.timeIntervalSince(epoch)
                let age = lastTouch.map { tick.date.timeIntervalSince($0) } ?? 100
                let returning = !dragging && age > 2 && age < 2.6
                let touchMix = lastTouch == nil ? 0 : (dragging || age <= 2 ? 1 : 1 - Self.ease(Self.segment(age, 2, 2.6)))
                let time = lastTouch != nil && age < 2.6 ? 1 + 2.2 * Self.inverseEase(scrub) : (lastTouch == nil ? elapsed : max(0, age - 2.6)).truncatingRemainder(dividingBy: Self.loop)
                Canvas { context, _ in
                    context.translateBy(x: origin.x, y: origin.y)
                    context.scaleBy(x: scale, y: scale)
                    draw(context, time: autoPlay || lastTouch != nil ? time : 0, touchMix: touchMix, returning: returning)
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                guard !reducedMotion else { return }
                scrub = min(1, max(0, ((value.location.y - origin.y) / scale - 228 - 60) / 290))
                dragging = true
                lastTouch = Date()
                let next = [2.01474, 2.20682, 2.42403].filter { 1 + 2.2 * Self.inverseEase(scrub) >= $0 }.count
                if next > 0 && next != detent { UIImpactFeedbackGenerator(style: .light).impactOccurred(); detent = next }
            }.onEnded { _ in
                dragging = false; detent = -1
                let ended = Date(); lastTouch = ended
                Task { @MainActor in
                    try? await Task.sleep(for: .seconds(2.6))
                    guard lastTouch == ended && !dragging else { return }
                    epoch = Date(); lastTouch = nil
                }
            })
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Tallyleaf. Import receipt. Morrow bowl 12.80, Citrus tea 4.60, Oat square 7.20. Total 24.60.")
            .accessibilityHint("Drag down to lift the receipt ink into three rows.")
        }
        .background(Self.backdrop.ignoresSafeArea())
        .ignoresSafeArea()
    }

    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    private static func inverseEase(_ x: Double) -> Double { x < 0.5 ? pow(x / 4, 1.0 / 3) : 1 - pow((1 - x) / 4, 1.0 / 3) }
    private static func out(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    private static func ease(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    private func text(_ string: String, _ x: Double, _ y: Double, _ size: Double, _ color: Color = ink, weight: Font.Weight = .regular, mono: Bool = false, anchor: UnitPoint = .leading, tracking: Double = 0, context: GraphicsContext) {
        context.draw(Text(string).font(.system(size: size, weight: weight, design: mono ? .monospaced : .default)).tracking(tracking).foregroundColor(color), at: CGPoint(x: x, y: y), anchor: anchor)
    }

    private func draw(_ base: GraphicsContext, time t: Double, touchMix: Double, returning: Bool) {
        var c = base
        text("Tallyleaf", 36, 87, 16, weight: .semibold, tracking: -0.3, context: c)
        var leaf = Path(); leaf.move(to: CGPoint(x: 338, y: 91)); leaf.addQuadCurve(to: CGPoint(x: 349, y: 75), control: CGPoint(x: 331, y: 75)); leaf.addQuadCurve(to: CGPoint(x: 338, y: 91), control: CGPoint(x: 354, y: 88))
        c.fill(leaf, with: .color(Self.coral))
        text("IMPORT / 01", 36, 127, 10, Self.muted, weight: .medium, tracking: 1.8, context: c)
        text("Import receipt", 36, 161, 30, weight: .semibold, tracking: -1, context: c)
        text("One receipt. Three clear details.", 36, 190, 13, Self.muted, context: c)
        text("Drag down to lift the ink", 196.5, 704, 12, Self.muted, weight: .medium, anchor: .center, context: c)
        text("DEMO RECEIPT · ON DEVICE", 196.5, 729, 9, Self.muted.opacity(0.75), weight: .medium, mono: true, anchor: .center, context: c)
        c.translateBy(x: 36.5, y: 228)
        let starts = [2.01474, 2.20682, 2.42403]
        let q = starts.enumerated().map { i, start in
            reducedMotion ? 1 : Self.out(Self.segment(t, start, start + 0.4)) * (1 - Self.ease(Self.segment(t, 5.2 + Double(i) * 0.06, 6.68 + Double(i) * 0.06))) * (returning ? (lastTouch == nil ? 1 : touchMix) : 1)
        }
        for (x, y, dx, dy) in [(10.0, 28.0, 17.0, 17.0), (310, 28, -17, 17), (10, 392, 17, -17), (310, 392, -17, -17)] {
            var corner = Path(); corner.move(to: CGPoint(x: x, y: y + dy)); corner.addLine(to: CGPoint(x: x, y: y)); corner.addLine(to: CGPoint(x: x + dx, y: y))
            c.stroke(corner, with: .color(Self.muted.opacity(0.35)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        }
        var paper = c
        paper.translateBy(x: 160, y: 211); paper.rotate(by: .degrees(-12))
        paper.opacity = 1 - 0.68 * q[2]
        let shape = paperPath()
        var rim = paper; rim.translateBy(x: 0, y: 2)
        rim.fill(shape, with: .color(Color(red: 0.835, green: 0.8, blue: 0.71)))
        var shadow = paper; shadow.addFilter(.shadow(color: Self.ink.opacity(0.13), radius: 18, x: 0, y: 16)); shadow.fill(shape, with: .color(Color(red: 0.96, green: 0.94, blue: 0.87)))
        paper.addFilter(.shadow(color: Self.ink.opacity(0.17), radius: 2, x: 0, y: 2))
        paper.fill(shape, with: .linearGradient(Gradient(colors: [Color(red: 1, green: 0.992, blue: 0.953), Color(red: 0.969, green: 0.945, blue: 0.875), Color(red: 0.918, green: 0.882, blue: 0.796)]), startPoint: CGPoint(x: -108, y: -147), endPoint: CGPoint(x: 108, y: 147)))
        paper.stroke(shape, with: .color(Color(red: 0.835, green: 0.8, blue: 0.71)), lineWidth: 0.6)
        var printContext = c; printContext.translateBy(x: 160, y: 211); printContext.rotate(by: .degrees(-12)); printContext.opacity = 1 - 0.78 * q[2]
        for i in 0..<64 {
            let x = Double((i * 67 + 19) % 210) - 105; let y = Double((i * 43 + 11) % 286) - 143
            var fiber = Path(); fiber.move(to: CGPoint(x: x, y: y)); fiber.addLine(to: CGPoint(x: x + 3 + Double(i % 5), y: y + 0.4))
            printContext.stroke(fiber, with: .color(Self.ink.opacity(0.045)), lineWidth: 0.5)
        }
        printContext.fill(Path(CGRect(x: -107, y: -9, width: 214, height: 12)), with: .linearGradient(Gradient(colors: [.clear, .white.opacity(0.3), .clear]), startPoint: CGPoint(x: 0, y: -9), endPoint: CGPoint(x: 0, y: 3)))
        text("MORROW", 0, -111, 15, weight: .bold, mono: true, anchor: .center, context: printContext)
        text("M A R K E T", 0, -94, 8, Self.muted, mono: true, anchor: .center, context: printContext)
        text("08 NOV 26 · 12:41", 0, -74, 8, Self.muted, mono: true, anchor: .center, context: printContext)
        for y in [-59.0, 89] {
            var rule = Path(); rule.move(to: CGPoint(x: -88, y: y)); rule.addLine(to: CGPoint(x: 88, y: y)); printContext.stroke(rule, with: .color(Self.muted.opacity(0.4)), style: StrokeStyle(lineWidth: 0.6, dash: [2, 3]))
        }
        text("ITEM", -88, -51, 7, Self.muted, mono: true, context: printContext)
        text("AMOUNT", 88, -51, 7, Self.muted, mono: true, anchor: .trailing, context: printContext)
        text("TOTAL", -88, 110, 12, weight: .bold, mono: true, context: printContext)
        text("24.60", 88, 110, 14, weight: .bold, mono: true, anchor: .trailing, context: printContext)
        text("A little good, every day.", 0, 129, 7, Self.muted, mono: true, anchor: .center, context: printContext)
        let names = ["Morrow bowl", "Citrus tea", "Oat square"]; let amounts = ["12.80", "4.60", "7.20"]
        for i in 0..<3 {
            let p = q[i]; let localY = -38.0 + Double(i) * 50
            let angle = -12 * (1 - p); let radians = -12.0 * Double.pi / 180
            let sourceX = 160 - sin(radians) * localY; let sourceY = 211 + cos(radians) * localY
            let targetY = 146.0 + Double(i) * 49
            var underlay = c; underlay.opacity = p * p
            let rect = CGRect(x: 28, y: targetY - 21.5, width: 264, height: 43)
            underlay.addFilter(.shadow(color: Self.ink.opacity(0.08), radius: 5, x: 0, y: 3))
            underlay.fill(Path(roundedRect: rect, cornerRadius: 9), with: .color(Color(red: 0.976, green: 0.973, blue: 0.945)))
            underlay.stroke(Path(roundedRect: rect, cornerRadius: 9), with: .color(Self.muted.opacity(0.14)), lineWidth: 0.7)
            var row = c; row.translateBy(x: sourceX + (160 - sourceX) * p, y: sourceY + (targetY - sourceY) * p - 8 * sin(Double.pi * p)); row.rotate(by: .degrees(angle))
            row.addFilter(.shadow(color: Self.ink.opacity(0.13 * sin(Double.pi * p)), radius: 2 + 3 * p, x: 0, y: 4 * sin(Double.pi * p)))
            text(names[i], -88, 0, 14, weight: .medium, mono: true, context: row)
            text(amounts[i], 88, 0, 14, weight: .medium, mono: true, anchor: .trailing, context: row)
            var check = c; check.opacity = Self.segment(p, 0.9, 1)
            check.fill(Path(ellipseIn: CGRect(x: 263, y: targetY - 5, width: 10, height: 10)), with: .color(Self.coral))
            var tick = Path(); tick.move(to: CGPoint(x: 265.5, y: targetY)); tick.addLine(to: CGPoint(x: 267.3, y: targetY + 1.6)); tick.addLine(to: CGPoint(x: 270.5, y: targetY - 2)); check.stroke(tick, with: .color(.white), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        }
        var result = c; result.opacity = q[2]
        text("3 details extracted", 160, 385, 12, weight: .semibold, anchor: .center, context: result)
        text("24.60 total", 160, 405, 11, Self.muted, mono: true, anchor: .center, context: result)
        let scan = Self.ease(Self.segment(t, 1, 3.2)); let scanY = 60 + 290 * scan
        var beam = c; beam.opacity = reducedMotion ? 0 : Self.segment(t, 1, 1.15) * (1 - Self.segment(t, 3.05, 3.2)) * (returning && lastTouch != nil ? touchMix : 1)
        beam.fill(Path(CGRect(x: 18, y: scanY - 28, width: 284, height: 28)), with: .linearGradient(Gradient(colors: [Self.coral.opacity(0), Self.coral.opacity(0.12)]), startPoint: CGPoint(x: 0, y: scanY - 28), endPoint: CGPoint(x: 0, y: scanY)))
        var glow = beam; glow.addFilter(.blur(radius: 5)); glow.fill(Path(CGRect(x: 18, y: scanY - 2, width: 284, height: 4)), with: .color(Self.coral.opacity(0.6)))
        beam.fill(Path(CGRect(x: 18, y: scanY - 0.75, width: 284, height: 1.5)), with: .color(Self.coral))
        for x in [18.0, 302] { beam.fill(Path(ellipseIn: CGRect(x: x - 1.5, y: scanY - 1.5, width: 3, height: 3)), with: .color(Self.coral)) }
    }

    private func paperPath() -> Path {
        var p = Path(); p.move(to: CGPoint(x: -108, y: -147))
        for i in 0..<27 { p.addLine(to: CGPoint(x: -104 + i * 8, y: -144)); p.addLine(to: CGPoint(x: -100 + i * 8, y: -147)) }
        p.addLine(to: CGPoint(x: 108, y: 147))
        for i in 0..<27 { p.addLine(to: CGPoint(x: 104 - i * 8, y: 144)); p.addLine(to: CGPoint(x: 100 - i * 8, y: 147)) }
        p.closeSubpath(); return p
    }
}
