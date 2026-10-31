import CoreText
import SwiftUI
import UIKit

// Port of the RN reference: three 2.6 s stops, a 0.9 s fall with two bounces,
// and a cubic switch over local 1.9–2.6 s. The bottle pivots around its base.
// Drag 300 pt per flavour; snap for 0.35 s, hold 2 s, resume that flavour at local 1.2.
struct NutMilkFlavours: View {
    static let loop: Double = 7.8
    var brand = "Oatlier"
    var action = "Add to basket"
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var appeared = false
    @State private var epoch = Date()
    @State private var accumulated = 0.0
    @State private var clockOrigin = 0.0
    @State private var manual = false
    @State private var touching = false
    @State private var activated = false
    @State private var grabbed = 0.0
    @State private var heldIndex = 0.0
    @State private var frozenLocal = 1.0
    @State private var releaseTime = 0.0
    @State private var snapFrom = 0.0
    @State private var snapTo = 0.0
    @State private var lastStep = 0.0
    @State private var touchSerial = 0
    @State private var stillFrame = NutMilkFlavoursMotion.Frame(index: 0, local: 1, clock: 0)
    @State private var feedback = UISelectionFeedbackGenerator()
    @GestureState private var gestureActive = false

    private typealias M = NutMilkFlavoursMotion
    private typealias A = NutMilkFlavoursArt

    private func elapsed(_ date: Date, ignoringReduced: Bool = false) -> Double {
        accumulated + (appeared && scene == .active && (!reduced || ignoringReduced) ? max(0, date.timeIntervalSince(epoch)) : 0)
    }

    private func frame(_ date: Date, ignoringReduced: Bool = false) -> M.Frame {
        if reduced && !ignoringReduced { return stillFrame }
        let now = elapsed(date, ignoringReduced: ignoringReduced), clock = M.mod(now - clockOrigin, Self.loop)
        if touching { return .init(index: heldIndex, local: frozenLocal, clock: clock) }
        if manual {
            let since = max(0, now - releaseTime)
            if since < M.snap {
                return .init(index: snapFrom + (snapTo - snapFrom)*M.ease(since/M.snap), local: frozenLocal, clock: clock)
            }
            if since <= M.snap + M.hold {
                return .init(index: snapTo, local: min(1.2, since - M.snap), clock: clock)
            }
            // Also derive continuation if the settling task wakes one frame late.
            let resumed = M.mod(M.mod(snapTo, 3)*M.stop + 1.2 + since - M.snap - M.hold, Self.loop)
            return autoPlay ? M.stopAt(resumed) : .init(index: 0, local: 1.2, clock: resumed)
        }
        return autoPlay ? M.stopAt(clock) : .init(index: 0, local: 1.2, clock: clock)
    }

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            TimelineView(.animation(paused: !appeared || scene != .active || reduced)) { timeline in
                let f = frame(timeline.date)
                Canvas { context, _ in
                    A.draw(context, size: size, safe: safe, frame: f, brand: brand, action: action)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(brand). \(A.flavours[M.wrap(Int(floor(f.index + 0.5)))].name). Nut milk bottle. Swipe to change flavour.")
                .overlay(alignment: .bottom) {
                    Color.clear.frame(height: 58)
                        .padding(.horizontal, 24)
                        .padding(.bottom, safe.bottom + 14)
                        .accessibilityLabel(action)
                        .accessibilityAddTraits(.isButton)
                        .allowsHitTesting(false)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
            .contentShape(Path { path in
                path.addRect(CGRect(origin: .zero, size: size))
                // The RN footer sits above and outside the scene's GestureDetector.
                path.addRect(CGRect(x: 24, y: size.height - safe.bottom - 156,
                                    width: size.width - 48, height: safe.bottom + 156))
            }, eoFill: true)
            .gesture(pan, including: reduced ? .none : .all)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { appeared = true; epoch = .now; feedback.prepare() }
        .onDisappear { accumulated = elapsed(.now); appeared = false }
        .onChange(of: scene) { old, new in
            let now = Date()
            if old == .active && !reduced { accumulated += max(0, now.timeIntervalSince(epoch)) }
            epoch = now
            if new == .active { feedback.prepare() }
        }
        .onChange(of: reduced) { _, new in
            let now = Date()
            if new {
                stillFrame = frame(now, ignoringReduced: true)
                accumulated = elapsed(now, ignoringReduced: true)
            }
            epoch = now
        }
        .onChange(of: gestureActive) { _, active in
            if !active && touching {
                Task { @MainActor in
                    await Task.yield()
                    if !gestureActive && touching { finishPan() }
                }
            }
        }
        .task(id: touchSerial) {
            guard manual, !touching else { return }
            // RN's elapsed clock freezes when inactive or reduced; keep the same hold length.
            while elapsed(.now) - releaseTime < M.snap + M.hold {
                let remaining = M.snap + M.hold - (elapsed(.now) - releaseTime)
                do { try await Task.sleep(for: .seconds(min(0.1, remaining))) } catch { return }
            }
            guard !Task.isCancelled, !touching else { return }
            let now = elapsed(.now)
            let overshoot = max(0, now - releaseTime - M.snap - M.hold)
            clockOrigin = now - M.mod(snapTo, 3)*M.stop - 1.2 - overshoot
            manual = false
        }
    }

    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !reduced else { return }
                // RN onBegin freezes the stop before its 4 pt activation threshold.
                if !touching {
                    let f = frame(.now)
                    grabbed = f.index; heldIndex = f.index; frozenLocal = f.local
                    lastStep = floor(f.index + 0.5)
                    manual = true; touching = true; activated = false
                    touchSerial += 1
                }
                if hypot(value.translation.width, value.translation.height) >= 4 { activated = true }
                guard activated else { return }
                heldIndex = grabbed - value.translation.width/300
                let step = floor(heldIndex + 0.5)
                if step != lastStep {
                    lastStep = step
                    feedback.selectionChanged(); feedback.prepare()
                }
            }
            .onEnded { _ in finishPan() }
    }

    private func finishPan() {
        guard touching else { return }
        touching = false
        snapFrom = heldIndex; snapTo = floor(heldIndex + 0.5)
        releaseTime = elapsed(.now)
        touchSerial += 1
    }

}

private enum NutMilkFlavoursMotion {
    static let stop = 2.6
    static let snap = 0.35
    static let hold = 2.0
    struct Frame { var index: Double; var local: Double; var clock: Double }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mod(_ x: Double, _ d: Double) -> Double { (x.truncatingRemainder(dividingBy: d) + d).truncatingRemainder(dividingBy: d) }
    static func wrap(_ i: Int) -> Int { (i % 3 + 3) % 3 }
    static func ease(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1 - pow(-2*x + 2, 3)/2 }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u*u*(3 - 2*u) }
    static func offset(_ j: Int, _ index: Double) -> Double {
        var r = Double(j) - mod(index, 3)
        if r > 1.5 { r -= 3 }
        if r <= -1.5 { r += 3 }
        return r
    }
    static func stopAt(_ t: Double) -> Frame {
        let k = floor(t/stop), u = t - k*stop
        return .init(index: k + ease(clamp((u - 1.9)/(stop - 1.9))), local: u, clock: t)
    }
    static func dropHeight(_ x: Double) -> Double {
        if x <= 0 { return 260 }
        if x >= 0.9 { return 0 }
        if x < 0.45 { return 260*(1 - pow(x/0.45, 2)) }
        if x < 0.72 { return 34*sin(.pi*(x - 0.45)/0.27) }
        return 9*sin(.pi*(x - 0.72)/0.18)
    }
    static func color(_ hex: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16)&255)/255, green: Double((hex >> 8)&255)/255, blue: Double(hex&255)/255, opacity: alpha)
    }
    static func uiColor(_ hex: UInt32, alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16)&255)/255, green: Double((hex >> 8)&255)/255, blue: Double(hex&255)/255, alpha: alpha)
    }
    static func mixedColor(_ a: UInt32, _ b: UInt32, _ u: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            (Double((a >> shift)&255)*(1-u) + Double((b >> shift)&255)*u).rounded()/255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }
}

private enum NutMilkFlavoursArt {
    typealias M = NutMilkFlavoursMotion
    struct Flavour {
        let left: String, right: String, name: String, line: String, price: String
        let tint: UInt32, deep: UInt32
    }
    static let flavours = [
        Flavour(left: "Alm", right: "ond", name: "Almond milk", line: "Toasted almonds, a pinch of sea salt. 1 L", price: "$4.20", tint: 0xF4EBDD, deep: 0x6E4A26),
        Flavour(left: "Haze", right: "lnut", name: "Hazelnut milk", line: "Roasted hazelnuts, no added sugar. 1 L", price: "$4.60", tint: 0xEAD9C6, deep: 0x5B3519),
        Flavour(left: "Wal", right: "nut", name: "Walnut milk", line: "Cold-pressed walnuts and a little date. 1 L", price: "$4.90", tint: 0xE3E6D6, deep: 0x3F4A2A)
    ]
    struct Nut { let dx: Double, size: Double, delay: Double, turn: Double, sink: Double }
    static let positions = (0..<6).map { i in
        func hash(_ n: Double) -> Double {
            let x = sin(Double(i + 1)*19.31 + n*7.77)*43758.5453
            return x - floor(x)
        }
        let side: Double = i.isMultiple(of: 2) ? -1 : 1
        let dx = side*(70 + 90*hash(1))
        let size = 40 + 22*hash(2)
        let delay = Double(i)*0.1
        let turn = hash(3)*360
        let sink = hash(4)*18
        return Nut(dx: dx, size: size, delay: delay, turn: turn, sink: sink)
    }
    static let bottles = ["almond", "hazelnut", "walnut"].map { image("bottle-\($0)") }
    static let nuts = ["almond", "hazelnut", "walnut"].map { image($0) }
    static func image(_ name: String) -> Image? {
        guard let ui = UIImage(named: "NutMilkFlavours-\(name).png") else { return nil }
        return Image(uiImage: ui.preparingForDisplay() ?? ui)
    }

    static func draw(_ original: GraphicsContext, size: CGSize, safe: EdgeInsets, frame f: M.Frame, brand: String, action: String) {
        var c = original
        let w = size.width, h = size.height, cx = w/2, bottleY = h*0.42, tableY = h*0.66
        let base = Int(floor(f.index)), fraction = f.index - floor(f.index)
        c.fill(Path(CGRect(origin: .zero, size: size)), with: .color(M.mixedColor(flavours[M.wrap(base)].tint, flavours[M.wrap(base + 1)].tint, fraction)))

        let brandText = NutMilkFlavoursType.text(brand, size: 28, serif: true)
        let menu = NutMilkFlavoursType.text("Shop · Story · Basket (1)", size: 13, weight: .semibold, alpha: 0.6)
        brandText.draw(&c, x: 24, y: safe.top + 8 + (34 - brandText.size.height)/2)
        menu.draw(&c, x: w - 24 - menu.size.width, y: safe.top + 8 + (34 - menu.size.height)/2)

        let wordY = h*0.4 - 55
        for k in 0..<3 {
            for side in 0..<2 {
                let word = side == 0 ? flavours[k].left : flavours[k].right
                let letters = word.map { NutMilkFlavoursType.text(String($0), size: 70, serif: true, hex: flavours[k].deep, tracking: -2, lineHeight: 110) }
                let width = letters.reduce(0) { $0 + $1.size.width }
                var x = side == 0 ? cx - 70 - width : cx + 70
                var half = c
                half.clip(to: Path(CGRect(x: x, y: wordY, width: width, height: 110)))
                for (n, letter) in letters.enumerated() {
                    let r = M.offset(k, f.index)
                    let lag = M.clamp(abs(r)*1.25 - Double(n + (side == 0 ? 0 : 4))*0.04)
                    var glyph = half
                    glyph.opacity = 1 - lag
                    letter.draw(&glyph, x: x, y: wordY + (r <= 0 ? -1 : 1)*lag*110)
                    x += letter.size.width
                }
            }
        }
        c.fill(Path(CGRect(x: 24, y: tableY, width: w - 48, height: 1)), with: .color(M.color(0x2A1D12, alpha: 0.18)))

        if let nutImage = nuts[M.wrap(base)] {
            for nut in positions {
                let height = M.dropHeight(f.local - nut.delay)
                let y = tableY - nut.size/2 + nut.sink - height + fraction*120
                var layer = c
                layer.opacity = 1 - fraction
                layer.translateBy(x: cx + nut.dx, y: y)
                layer.rotate(by: .degrees(nut.turn + height*0.6))
                layer.draw(nutImage, in: CGRect(x: -nut.size/2, y: -nut.size/2, width: nut.size, height: nut.size))
            }
        }
        let sway = 2*sin(f.clock/NutMilkFlavours.loop * .pi*6)
        for j in 0..<3 {
            let r = M.offset(j, f.index), fade = 1 - abs(r)
            guard fade > 0, let image = bottles[j] else { continue }
            var layer = c
            layer.opacity = fade
            layer.translateBy(x: cx + r*360, y: bottleY + 180)
            layer.rotate(by: .degrees(-16*r + sway*(1 - abs(r))))
            layer.translateBy(x: 0, y: -180)
            layer.draw(image, in: CGRect(x: -180, y: -180, width: 360, height: 360))
        }

        let footerY = h - safe.bottom - 14 - 142
        for k in 0..<3 {
            let flavour = flavours[k]
            var info = c
            info.opacity = 1 - M.smooth(abs(M.offset(k, f.index))/0.45)
            let name = NutMilkFlavoursType.text(flavour.name, size: 34, serif: true)
            let price = NutMilkFlavoursType.text(flavour.price, size: 18, weight: .bold, hex: flavour.deep)
            let detail = NutMilkFlavoursType.text(flavour.line, size: 15, alpha: 0.62, lineHeight: 20)
            name.draw(&info, x: 24, y: footerY)
            let priceTop = name.baseline - price.baseline
            price.draw(&info, x: w - 24 - price.size.width, y: footerY + priceTop)
            detail.draw(&info, x: 24, y: footerY + max(name.size.height, priceTop + price.size.height) + 2)
        }
        let button = CGRect(x: 24, y: footerY + 84, width: w - 48, height: 58)
        c.fill(Path(roundedRect: button, cornerRadius: 29), with: .color(M.mixedColor(flavours[M.wrap(base)].deep, flavours[M.wrap(base + 1)].deep, fraction)))
        let actionText = NutMilkFlavoursType.text(action, size: 17, weight: .bold, hex: 0xFFFFFF)
        actionText.draw(&c, x: cx - actionText.size.width/2, y: button.midY - actionText.size.height/2)
    }
}

/// Cache TextKit line boxes to match RN font metrics without laying out moving glyphs per frame.
private enum NutMilkFlavoursType {
    struct Sprite {
        let image: Image
        let size: CGSize
        let baseline: CGFloat
        func draw(_ context: inout GraphicsContext, x: CGFloat, y: CGFloat) {
            context.draw(image, in: CGRect(origin: CGPoint(x: x, y: y), size: size))
        }
    }
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "NutMilkFlavours-InstrumentSerif-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
    private static var cache: [String: Sprite] = [:]
    static func text(_ string: String, size: CGFloat, serif: Bool = false, weight: UIFont.Weight = .regular, hex: UInt32 = 0x2A1D12, alpha: Double = 1, tracking: CGFloat = 0, lineHeight: CGFloat? = nil) -> Sprite {
        _ = registered
        let key = "\(string)|\(size)|\(serif)|\(weight.rawValue)|\(hex)|\(alpha)|\(tracking)|\(lineHeight ?? 0)"
        if let cached = cache[key] { return cached }
        let font = serif ? (UIFont(name: "InstrumentSerif-Regular", size: size) ?? .systemFont(ofSize: size)) : .systemFont(ofSize: size, weight: weight)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: NutMilkFlavoursMotion.uiColor(hex, alpha: alpha)]
        if tracking != 0 { attributes[.kern] = tracking }
        if let lineHeight {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight; paragraph.maximumLineHeight = lineHeight
            attributes[.paragraphStyle] = paragraph
            if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight)/2 }
        }
        let storage = NSTextStorage(string: string, attributes: attributes)
        let layout = NSLayoutManager()
        layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: 2000, height: 300))
        container.lineFragmentPadding = 0
        container.maximumNumberOfLines = 1
        container.lineBreakMode = .byClipping
        layout.addTextContainer(container); storage.addLayoutManager(layout)
        let range = layout.glyphRange(for: container)
        let bounds = layout.usedRect(for: container)
        let scale = UIScreen.main.scale
        let dimensions = CGSize(width: ceil(bounds.width*scale)/scale, height: ceil(bounds.height*scale)/scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        let image = UIGraphicsImageRenderer(size: dimensions, format: format).image { _ in
            layout.drawGlyphs(forGlyphRange: range, at: .zero)
        }
        let sprite = Sprite(image: Image(uiImage: image), size: dimensions, baseline: layout.location(forGlyphAt: 0).y)
        cache[key] = sprite
        return sprite
    }
}
