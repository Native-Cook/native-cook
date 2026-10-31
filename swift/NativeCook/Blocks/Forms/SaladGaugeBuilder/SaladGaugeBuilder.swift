import CoreText
import SwiftUI
import UIKit

// Port of the RN Bowlful builder, in screen coordinates. Loop 8 s:
// rest 0–0.8; toppings at 0.8/1.5/2.2/2.9/3.6, each over 0.45 s;
// full 4.1–5.9; lift out 5.9–7 with a 0.07 s stagger; rest 7–8.
// A chip tap takes over; 2 s after the last tap, empty over 0.6 s and restart.
struct SaladGaugeBuilder: View {
    var brand = "Bowlful"
    var title = "Fill up\nyour bowl"
    var autoPlay = false
    static let loop: Double = 8

    private typealias M = SaladGaugeBuilderMotion
    private typealias A = SaladGaugeBuilderAssets
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var clockOrigin = Date()
    @State private var pausedAt: Date?
    @State private var cycleOrigin = 0.0
    @State private var manual: M.Manual?
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let chips = A.chipRects(size: size, safe: safe)
            TimelineView(.animation(paused: reduceMotion || scenePhase != .active || (!autoPlay && manual == nil))) { timeline in
                let f = frame(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    Canvas { context, _ in
                        A.draw(context, size: size, safe: safe, frame: f, brand: brand, title: title)
                    }
                    .accessibilityLabel("\(brand). Fill up your bowl. Salad bowl gauge, up to 500 kcal.")
                    ForEach(M.items.indices, id: \.self) { i in
                        Button { toggle(i) } label: { Color.clear.contentShape(Capsule()) }
                            .buttonStyle(.plain)
                            .frame(width: chips[i].width, height: chips[i].height)
                            .position(x: chips[i].midX, y: chips[i].midY)
                            .accessibilityLabel("Toggle \(M.items[i].name)")
                            .accessibilityValue(f.amounts[i] > 0.25 ? "Selected" : "Not selected")
                    }
                    Color.clear
                        .frame(width: size.width - 40, height: 58)
                        .position(x: size.width / 2, y: size.height - safe.bottom - 43)
                        .allowsHitTesting(false)
                        .accessibilityLabel(f.needle > 0.9 ? "Order bowl · $12.40" : "Pick your toppings")
                        .accessibilityAddTraits(.isButton)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .background(M.background.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            A.prepare()
            clockOrigin = .now
            cycleOrigin = 0
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active {
                if pausedAt == nil { pausedAt = .now }
            } else if let pausedAt {
                clockOrigin = clockOrigin.addingTimeInterval(Date().timeIntervalSince(pausedAt))
                self.pausedAt = nil
            }
        }
        .onChange(of: reduceMotion) { _, _ in
            manual = nil
            cycleOrigin = elapsed(at: .now)
        }
        .task(id: touches) { await settle() }
    }

    private func elapsed(at date: Date) -> Double {
        max(0, (pausedAt ?? date).timeIntervalSince(clockOrigin))
    }
    private func frame(at date: Date) -> M.Frame {
        if reduceMotion { return M.Frame(amounts: [1, 1, 1, 1, 1], needle: 1) }
        let now = elapsed(at: date)
        if let manual { return manual.frame(at: now) }
        return autoPlay ? M.bowl(at: max(0, now - cycleOrigin).truncatingRemainder(dividingBy: Self.loop)) : .rest
    }
    private func toggle(_ i: Int) {
        guard !reduceMotion else { return }
        let date = Date(), now = elapsed(at: date)
        let current = frame(at: date)
        let next = manual ?? M.Manual(on: current.amounts.map { $0 > 0.5 },
                                     changed: Array(repeating: -10, count: M.items.count),
                                     lastTap: now, needleFrom: current.needle)
        next.on[i].toggle()
        next.changed[i] = now
        next.lastTap = now
        next.resetNeedle(current.needle, at: now)
        manual = next
        touches += 1
        haptic.impactOccurred()
        haptic.prepare()
    }
    private func settle() async {
        guard touches > 0 else { return }
        while let manual {
            let remaining = manual.lastTap + M.hold + M.empty - elapsed(at: .now)
            if remaining <= 0 {
                cycleOrigin = elapsed(at: .now)
                self.manual = nil
                return
            }
            do { try await Task.sleep(for: .seconds(min(remaining, 0.1))) }
            catch { return }
        }
    }
}

private enum SaladGaugeBuilderMotion {
    static let bowlSize: CGFloat = 270
    static let inner: CGFloat = 150
    static let outer: CGFloat = 168
    static let eAngle = 195.0 * .pi / 180
    static let fAngle = 345.0 * .pi / 180
    static let hold = 2.0
    static let empty = 0.6
    static let ink = color(0x2B1838)
    static let green = color(0x33C27A)
    static let background = color(0xE4C6EC)
    struct Item {
        var name: String
        var asset: String
        var kcal: Double
        var angle: Double
        var radius: CGFloat
        var size: CGFloat
        var at: Double
    }
    static let items = [
        Item(name: "Tomato", asset: "tomato", kcal: 30, angle: 180, radius: 52, size: 112, at: 0.8),
        Item(name: "Salmon", asset: "salmon", kcal: 210, angle: 270, radius: 54, size: 112, at: 1.5),
        Item(name: "Greens", asset: "greens", kcal: 20, angle: 0, radius: 52, size: 122, at: 2.2),
        Item(name: "Egg", asset: "egg", kcal: 80, angle: 90, radius: 54, size: 100, at: 2.9),
        Item(name: "Avocado", asset: "avocado", kcal: 160, angle: 0, radius: 0, size: 96, at: 3.6)
    ]
    struct Frame {
        var amounts: [Double]
        var needle: Double
        static let rest = Frame(amounts: [0, 0, 0, 0, 0], needle: 0)
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func cubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func back(_ x: Double) -> Double { 1 + 2.7 * pow(x - 1, 3) + 1.7 * pow(x - 1, 2) }
    static func spring(_ x: Double) -> Double { x <= 0 ? 0 : 1 - exp(-5 * x) * cos(9 * x) }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
    static func bowl(at t: Double) -> Frame {
        // RN's residual spring at 7 s is below a pixel; pin rest exactly to zero.
        if t >= 7 { return .rest }
        var amounts: [Double] = []
        var needle = 0.0
        for i in items.indices {
            let out = seg(t, 5.9 + Double(i) * 0.07, 6.5 + Double(i) * 0.07)
            amounts.append(out > 0 ? -(1 - out) : seg(t, items[i].at, items[i].at + 0.45))
            needle += spring(t - items[i].at) / Double(items.count)
        }
        needle -= cubic(seg(t, 5.9, 7))
        return Frame(amounts: amounts, needle: needle)
    }
    @MainActor final class Manual {
        var on: [Bool]
        var changed: [Double]
        var lastTap: Double
        private var needle: Double
        private var lastFrameTime: Double
        private var snapshot: Frame?

        init(on: [Bool], changed: [Double], lastTap: Double, needleFrom: Double) {
            self.on = on
            self.changed = changed
            self.lastTap = lastTap
            needle = needleFrom
            lastFrameTime = lastTap
        }
        func resetNeedle(_ value: Double, at now: Double) {
            needle = value
            lastFrameTime = now
            snapshot = nil
        }
        func frame(at now: Double) -> Frame {
            // SwiftUI may request a timestamp more than once. Only advance this
            // non-published manual integrator for a newer frame; autoplay stays pure.
            if now <= lastFrameTime, let snapshot { return snapshot }
            let dt = max(0, now - lastFrameTime)
            let since = max(0, now - lastTap)
            let drain = clamp((since - hold) / empty)
            var next: [Double] = []
            var target = 0.0
            for i in items.indices {
                let x = now - changed[i]
                var a = on[i] ? clamp(x / 0.45) : -(1 - clamp(x / 0.45))
                if a == 1 && drain > 0 { a = -(1 - drain) }
                if a == -1 { a = 0 }
                next.append(a)
                target += on[i] ? 1 - drain : 0
            }
            needle += (target / Double(items.count) - needle) * (1 - exp(-9 * dt))
            lastFrameTime = max(lastFrameTime, now)
            let result = Frame(amounts: next, needle: needle)
            snapshot = result
            return result
        }
    }
}

private enum SaladGaugeBuilderAssets {
    typealias M = SaladGaugeBuilderMotion
    private static let registered: String = {
        guard let url = Bundle.main.url(forResource: "SaladGaugeBuilder-ArchivoExpanded-Black", withExtension: "ttf") else {
            return "ArchivoExpanded-Black"
        }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        if let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider), let name = font.postScriptName {
            return name as String
        }
        return "ArchivoExpanded-Black"
    }()
    private static func image(_ name: String) -> Image? {
        guard let url = Bundle.main.url(forResource: "SaladGaugeBuilder-\(name)", withExtension: "png"),
              let source = UIImage(contentsOfFile: url.path) else { return nil }
        return Image(uiImage: source.preparingForDisplay() ?? source)
    }
    private static let bowl = image("bowl")
    private static let foods = M.items.map { image($0.asset) }
    static func prepare() { _ = registered; _ = bowl; _ = foods }
    struct Sprite {
        var image: Image
        var size: CGSize
        func draw(_ c: inout GraphicsContext, x: CGFloat, y: CGFloat) {
            c.draw(image, in: CGRect(origin: CGPoint(x: x, y: y), size: size))
        }
    }
    private static var textCache: [String: Sprite] = [:]
    private struct GaugeText {
        var line: CTLine
        var width: CGFloat
        func draw(_ c: inout GraphicsContext, x: CGFloat, baseline: CGFloat, scale: CGFloat) {
            c.withCGContext { cg in
                cg.saveGState()
                cg.translateBy(x: x, y: baseline)
                cg.scaleBy(x: scale, y: -scale)
                cg.textMatrix = .identity
                cg.textPosition = .zero
                CTLineDraw(line, cg)
                cg.restoreGState()
            }
        }
    }
    private static var gaugeCache: [String: GaugeText] = [:]
    // RN's gauge text is drawn from a 100 pt font and scaled around an explicit baseline.
    private static func gaugeText(_ string: String, hex: UInt32 = 0x2B1838) -> GaugeText {
        let key = "\(string)|\(hex)"
        if let cached = gaugeCache[key] { return cached }
        let font = UIFont(name: registered, size: 100) ?? .systemFont(ofSize: 100, weight: .black)
        let color = UIColor(red: CGFloat((hex >> 16) & 255) / 255,
                            green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: 1)
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [.font: font, .foregroundColor: color]))
        let result = GaugeText(line: line, width: CTLineGetBoundsWithOptions(line, .useGlyphPathBounds).width)
        gaugeCache[key] = result
        return result
    }
    private static func text(_ string: String, size: CGFloat, black: Bool = true,
                             weight: UIFont.Weight = .regular, hex: UInt32 = 0x2B1838,
                             tracking: CGFloat = 0, lineHeight: CGFloat? = nil) -> Sprite {
        let key = "\(string)|\(size)|\(black)|\(weight.rawValue)|\(hex)|\(tracking)|\(lineHeight ?? 0)"
        if let cached = textCache[key] { return cached }
        let font = black ? (UIFont(name: registered, size: size) ?? .systemFont(ofSize: size, weight: .black))
                         : .systemFont(ofSize: size, weight: weight)
        let color = UIColor(red: CGFloat((hex >> 16) & 255) / 255,
                            green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: 1)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        if tracking != 0 { attributes[.kern] = tracking }
        if let lineHeight {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            attributes[.paragraphStyle] = paragraph
            if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
        }
        let storage = NSTextStorage(string: string, attributes: attributes)
        let layout = NSLayoutManager()
        layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: 2000, height: 300))
        container.lineFragmentPadding = 0
        container.maximumNumberOfLines = 1
        container.lineBreakMode = .byClipping
        layout.addTextContainer(container)
        storage.addLayoutManager(layout)
        let range = layout.glyphRange(for: container)
        let bounds = layout.usedRect(for: container)
        let scale = UIScreen.main.scale
        let dimensions = CGSize(width: max(1, ceil(bounds.width * scale) / scale),
                                height: max(1, ceil(bounds.height * scale) / scale))
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        let image = UIGraphicsImageRenderer(size: dimensions, format: format).image { _ in
            layout.drawGlyphs(forGlyphRange: range, at: .zero)
        }
        let sprite = Sprite(image: Image(uiImage: image), size: dimensions)
        textCache[key] = sprite
        return sprite
    }
    static func chipRects(size: CGSize, safe: EdgeInsets) -> [CGRect] {
        let widths = M.items.map { text($0.name, size: 14, black: false, weight: .bold).size.width + 22 }
        let gap = max(0, (size.width - 40 - widths.reduce(0, +)) / CGFloat(widths.count - 1))
        let y = size.height - safe.bottom - 14 - 58 - 16 - 38
        var x: CGFloat = 20
        return widths.map { width in
            defer { x += width + gap }
            return CGRect(x: x, y: y, width: width, height: 38)
        }
    }
    private static func polar(_ cx: CGFloat, _ cy: CGFloat, _ angle: Double, _ radius: CGFloat) -> CGPoint {
        CGPoint(x: cx + cos(angle) * radius, y: cy + sin(angle) * radius)
    }
    private static func line(_ c: inout GraphicsContext, _ a: CGPoint, _ b: CGPoint, color: Color, width: CGFloat) {
        var path = Path()
        path.move(to: a)
        path.addLine(to: b)
        c.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
    }
    private static func circle(_ point: CGPoint, radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2))
    }
    static func draw(_ original: GraphicsContext, size: CGSize, safe: EdgeInsets, frame f: M.Frame, brand: String, title: String) {
        var c = original
        let cx = size.width / 2, cy = size.height * 0.47
        c.fill(Path(CGRect(origin: .zero, size: size)), with: .color(M.background))
        let wordmark = text(brand, size: 18)
        wordmark.draw(&c, x: 24, y: safe.top + 8 + (34 - wordmark.size.height) / 2)
        let meta = text("Lunch · 12:30", size: 13, black: false, weight: .semibold)
        var faded = c
        faded.opacity = 0.6
        meta.draw(&faded, x: size.width - 24 - meta.size.width, y: safe.top + 8 + (34 - meta.size.height) / 2)
        for (i, string) in title.components(separatedBy: "\n").enumerated() {
            let label = text(string, size: 34, tracking: -1, lineHeight: 40)
            label.draw(&c, x: 24, y: safe.top + 54 + CGFloat(i) * 40)
        }
        for k in 0..<11 {
            let angle = M.eAngle + (M.fAngle - M.eAngle) * Double(k) / 10
            let major = k.isMultiple(of: 5)
            line(&c, polar(cx, cy, angle, major ? M.inner - 6 : M.inner), polar(cx, cy, angle, M.outer),
                 color: k == 0 ? M.color(0xFF4D6D) : .white, width: major ? 7 : 5)
        }
        let full = M.clamp((f.needle - 0.9) / 0.1)
        let e = gaugeText("E", hex: 0xFFFFFF)
        let ep = polar(cx, cy, M.eAngle, M.outer - 6)
        e.draw(&c, x: ep.x - 8, baseline: ep.y + 34, scale: 0.2)
        let labelF = gaugeText("F", hex: full > 0.5 ? 0x33C27A : 0xFFFFFF)
        let fp = polar(cx, cy, M.fAngle, M.outer - 6)
        labelF.draw(&c, x: fp.x - 6, baseline: fp.y + 34, scale: 0.2)
        if full > 0 {
            var glow = c
            glow.opacity = 0.45 * full
            // Skia BlurStyle.Solid preserves the disk and blurs only its exterior.
            glow.drawLayer { layer in
                let disk = circle(polar(cx, cy, M.fAngle, M.outer), radius: 18)
                var blurred = layer
                blurred.addFilter(.blur(radius: 14))
                blurred.fill(disk, with: .color(M.green))
                layer.fill(disk, with: .color(M.green))
            }
        }
        let shadowCentre = CGPoint(x: cx - 26, y: cy + 30)
        let shadow = Gradient(stops: [.init(color: M.color(0x5A286E).opacity(0.35), location: 0.62),
                                      .init(color: M.color(0x5A286E).opacity(0), location: 1)])
        c.fill(circle(shadowCentre, radius: M.bowlSize * 0.56),
               with: .radialGradient(shadow, center: shadowCentre, startRadius: 0, endRadius: M.bowlSize * 0.56))
        if let bowl { c.draw(bowl, in: CGRect(x: cx - 135, y: cy - 135, width: M.bowlSize, height: M.bowlSize)) }
        for i in M.items.indices {
            let a = f.amounts[i]
            guard a != 0, let image = foods[i] else { continue }
            let item = M.items[i]
            let centre = polar(cx, cy, item.angle * .pi / 180, item.radius)
            let scale = a > 0 ? 1 + 0.35 * (1 - M.back(a)) : 1 + 0.15 * (1 + a)
            let width = item.size * scale
            var ingredient = c
            ingredient.opacity = a > 0 ? M.clamp(a * 3) : -a
            ingredient.draw(image, in: CGRect(x: centre.x - width / 2, y: centre.y - width / 2, width: width, height: width))
        }
        let angle = M.eAngle + (M.fAngle - M.eAngle) * max(-0.03, min(1.04, f.needle))
        let start = polar(cx, cy, angle, M.inner - 22)
        line(&c, start, polar(cx, cy, angle, M.outer + 8), color: M.green, width: 5)
        c.fill(circle(start, radius: 4), with: .color(M.green))
        var kcal = 0.0
        for i in M.items.indices {
            let a = f.amounts[i]
            kcal += M.items[i].kcal * (a > 0 ? M.clamp(a * 2) : -a)
        }
        let calories = gaugeText("\(Int(kcal.rounded())) kcal")
        calories.draw(&c, x: cx - calories.width * 0.3 / 2, baseline: cy + M.bowlSize / 2 + 62, scale: 0.3)
        let chips = chipRects(size: size, safe: safe)
        for i in M.items.indices {
            let a = f.amounts[i], on = a > 0 ? M.clamp(a * 2) : 0
            c.fill(Path(roundedRect: chips[i], cornerRadius: 19), with: .color(M.ink.opacity(0.06 + 0.94 * on)))
            let label = text(M.items[i].name, size: 14, black: false, weight: .bold, hex: a > 0.25 ? 0xFFFFFF : 0x2B1838)
            label.draw(&c, x: chips[i].midX - label.size.width / 2, y: chips[i].midY - label.size.height / 2)
        }
        let button = CGRect(x: 20, y: size.height - safe.bottom - 72, width: size.width - 40, height: 58)
        c.fill(Path(roundedRect: button, cornerRadius: 29), with: .color(f.needle > 0.9 ? M.green : M.ink))
        for (string, opacity) in [("Pick your toppings", 1 - full), ("Order bowl · $12.40", full)] {
            let label = text(string, size: 15, hex: 0xFFFFFF)
            var action = c
            action.opacity = opacity
            label.draw(&action, x: button.midX - label.size.width / 2, y: button.midY - label.size.height / 2)
        }
    }
}

#Preview {
    SaladGaugeBuilder(autoPlay: true)
}
