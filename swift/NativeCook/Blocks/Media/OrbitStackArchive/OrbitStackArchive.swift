import CoreText
import SwiftUI
import UIKit

// Faithful port of the React Native Paper Room redesign: Anton on warm paper, eight
// bundled poster artworks, and sixteen 200 × 268 pt cards on a 150 pt orbit.
// 0–1.2 s: orbit; 1.2–2.85 s: staggered gather; 2.6–5.6 s: four 0.75 s deals;
// 5.8–7.45 s: staggered burst; 7.45–9 s: orbit. One full turn per 9 s loop.
struct OrbitStackArchive: View {
    var brand = "PAPER ROOM"
    var line = "Swiss posters 1957–1992"
    var action = "Browse the archive"
    var autoPlay = false

    static let loop: Double = 9

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var fingerDown = false
    @State private var origin = Date()
    @State private var inactiveAt: Date?
    @State private var manual = false
    @State private var touching = false
    @State private var panActivated = false
    @State private var frozenTime = 0.0
    @State private var spin = 0.0
    @State private var velocity = 0.0
    @State private var lastX = 0.0
    @State private var releasedAt: Date?
    @State private var releaseSpin = 0.0
    @State private var releaseVelocity = 0.0
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(
                width: geometry.size.width + safe.leading + safe.trailing,
                height: geometry.size.height + safe.top + safe.bottom
            )
            let centre = CGPoint(x: size.width / 2, y: size.height * 0.56)
            ZStack(alignment: .topLeading) {
                Palette.paper

                Typography.header(width: size.width - 40, brand: brand)
                    .frame(width: size.width - 40, height: 232, alignment: .topLeading)
                    .offset(x: 20, y: safe.top + 8)
                    .accessibilityLabel("\(brand). 16 / 16. Poster Archive.")
                    .allowsHitTesting(false)

                TimelineView(.animation(paused: reduceMotion || scenePhase != .active || (!autoPlay && !manual))) { context in
                    let frame = frame(at: inactiveAt ?? context.date)
                    ZStack(alignment: .topLeading) {
                        Canvas { context, _ in
                            drawArchive(context, time: frame.time, spin: frame.spin, centre: centre)
                        }
                        .accessibilityLabel("\(brand). \(line). Drag to spin the orbit.")
                        captions(time: frame.time, width: size.width - 40)
                            .frame(width: size.width - 40, height: 60, alignment: .topLeading)
                            .offset(x: 20, y: centre.y + 196)
                            .allowsHitTesting(false)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(drag(centre: centre))

                Typography.footer(width: size.width - 40, action: action)
                    .frame(width: size.width - 40, height: 56, alignment: .topLeading)
                    .offset(x: 20, y: size.height - safe.bottom - 14 - 56)
                    .accessibilityLabel(action)
                    .accessibilityAddTraits(.isButton)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            origin = .now
            haptic.prepare()
        }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced {
                manual = false
                touching = false
                spin = 0
                releasedAt = nil
                touches += 1
            }
            origin = .now
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active {
                if inactiveAt == nil { inactiveAt = .now }
            } else if let paused = inactiveAt {
                let interval = Date().timeIntervalSince(paused)
                origin = origin.addingTimeInterval(interval)
                if let released = releasedAt { releasedAt = released.addingTimeInterval(interval) }
                inactiveAt = nil
            }
        }
        .onChange(of: fingerDown) { _, down in
            // GestureState also resets on cancellation, like RN's onFinalize callback.
            if !down { releaseTouch() }
        }
        .task(id: SettleTask(touch: touches, active: scenePhase == .active)) {
            await settle()
        }
    }

    private func captions(time: Double, width: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            Typography.caption(width: width, title: "Sixteen posters", meta: line)
                .opacity(Motion.captionOpacity(time, 0))
            ForEach(Array(Motion.tops.enumerated()), id: \.offset) { slot, design in
                Typography.caption(width: width, title: Motion.designs[design].title, meta: Motion.designs[design].meta)
                    .opacity(Motion.captionOpacity(time, slot + 1))
            }
        }
    }

    private func drawArchive(_ context: GraphicsContext, time: Double, spin: Double, centre: CGPoint) {
        context.stroke(
            Path(ellipseIn: CGRect(x: centre.x - 150, y: centre.y - 150, width: 300, height: 300)),
            with: .color(Palette.ink.opacity(0.14)), lineWidth: 1
        )
        let base = 2 * Double.pi * time / Self.loop + spin - Double.pi / 2
        let done = Motion.dealsAt(time)
        let shuffles = Int(floor(done))
        let u = done - Double(shuffles)
        var order = (0..<16).map { ($0 - shuffles + 64) % 16 }
        let dealing = shuffles < 4 && done > 0 ? (15 - shuffles + 16) % 16 : -1
        if dealing >= 0, u >= 0.5, let index = order.firstIndex(of: dealing) {
            order.remove(at: index)
            order.insert(dealing, at: 0)
        }
        var items: [PosterFrame] = []
        for index in 0..<16 {
            let gather = Motion.gatherAt(time, index)
            let z = gather < 0.5 ? gather : 1 + Double(order.firstIndex(of: index)!)
            items.append(PosterFrame(index: index, gather: gather, z: z))
        }
        items.sort { lhs, rhs in
            if lhs.z == rhs.z { return lhs.index < rhs.index }
            return lhs.z < rhs.z
        }

        for item in items {
            let i = item.index
            let g = item.gather
            let angle = base + Double(i) * 2 * Double.pi / 16
            let pile = Motion.pile[i]
            let orbitX = Double(centre.x) + cos(angle) * 150
            let orbitY = Double(centre.y) + sin(angle) * 150
            var x = orbitX + (Double(centre.x) + pile.x - orbitX) * g
            var y = orbitY + (Double(centre.y) + pile.y - orbitY) * g
            var rotation = pile.tilt * g
            if i == dealing {
                let swing = sin(Double.pi * u)
                x += 92 * swing
                y -= 26 * swing
                rotation += 16 * swing
            }
            let scale = 0.22 + (1 - 0.22) * g
            var card = context
            card.translateBy(x: x, y: y)
            card.rotate(by: .degrees(rotation))
            card.scaleBy(x: scale, y: scale)
            card.translateBy(x: -100, y: -134)
            var shadow = card
            shadow.addFilter(.blur(radius: 4 + 8 * g))
            shadow.fill(
                Path(roundedRect: CGRect(x: 4, y: 10, width: 196, height: 264), cornerRadius: 3),
                with: .color(.black.opacity(0.08 + 0.14 * g))
            )
            card.draw(PosterArt.images[i % 8], in: CGRect(x: 0, y: 0, width: 200, height: 268))
        }
    }

    private func frame(at date: Date) -> Frame {
        guard !reduceMotion else { return Frame(time: 0, spin: 0) }
        if manual {
            if touching || releasedAt == nil { return Frame(time: frozenTime, spin: spin) }
            let since = max(0, date.timeIntervalSince(releasedAt!))
            // Integral of RN's exp(-3 dt) decay, without a per-frame state mutation.
            let carried = releaseSpin + releaseVelocity * (1 - exp(-3 * min(since, 2))) / 3
            if since < 2 { return Frame(time: frozenTime, spin: carried) }
            let target = floor(carried / (2 * .pi) + 0.5) * 2 * .pi
            let k = Motion.ease(Motion.segment(since, 2, 2.8))
            return Frame(time: frozenTime, spin: carried + (target - carried) * k)
        }
        let time = autoPlay ? max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: Self.loop) : 0
        return Frame(time: time, spin: 0)
    }

    private func drag(centre: CGPoint) -> some Gesture {
        // RN onBegin freezes the clock on touch-down, before its 4 pt pan activates.
        DragGesture(minimumDistance: 0)
            .updating($fingerDown) { _, down, _ in down = true }
            .onChanged { value in
                guard !reduceMotion else { return }
                if !touching {
                    let current = frame(at: .now)
                    frozenTime = current.time
                    spin = current.spin
                    touching = true
                    panActivated = false
                    manual = true
                    velocity = 0
                    releasedAt = nil
                    lastX = value.startLocation.x
                    touches += 1
                    haptic.impactOccurred()
                }
                if hypot(value.translation.width, value.translation.height) >= 4 {
                    panActivated = true
                }
                guard panActivated else { return }
                let side = value.location.y > centre.y ? -1.0 : 1.0
                spin += side * (value.location.x - lastX) / 150
                lastX = value.location.x
                velocity = side * value.velocity.width / 150
            }
            .onEnded { _ in releaseTouch() }
    }

    private func releaseTouch() {
        guard !reduceMotion, touching else { return }
        touching = false
        releasedAt = .now
        releaseSpin = spin
        releaseVelocity = velocity
        touches += 1
    }

    private func settle() async {
        guard manual, !touching, scenePhase == .active, let release = releasedAt else { return }
        let remaining = max(0, 2.8 - Date().timeIntervalSince(release))
        try? await Task.sleep(for: .seconds(remaining))
        guard !Task.isCancelled, !touching, scenePhase == .active else { return }
        spin = 0
        manual = false
        releasedAt = nil
        // Resume the phase held by the RN frame callback throughout the gesture.
        origin = Date().addingTimeInterval(-frozenTime)
    }

    private struct Frame {
        var time: Double
        var spin: Double
    }

    private struct PosterFrame {
        var index: Int
        var gather: Double
        var z: Double
    }

    private struct SettleTask: Equatable {
        var touch: Int
        var active: Bool
    }

    private enum Motion {
        static let designs = [
            (title: "Form & Field", meta: "Kunsthalle Basel · 1966"),
            (title: "Ascent", meta: "Architektur Forum · 1974"),
            (title: "Yep.", meta: "Lime Press · 1989"),
            (title: "Blue Hour", meta: "Studio Hafen · 1979"),
            (title: "Draw", meta: "Werkschule Bern · 1961"),
            (title: "Bloom", meta: "Gartenamt Zürich · 1983"),
            (title: "No. 7", meta: "Galerie Raster · 1957"),
            (title: "Loud Type", meta: "Radio Zürich · 1991")
        ]
        static let tops = [7, 6, 5, 4, 3]
        static let pile = (0..<16).map { i in
            (tilt: (hash(i, 1) - 0.5) * 16, x: (hash(i, 2) - 0.5) * 20, y: (hash(i, 3) - 0.5) * 14)
        }

        static func hash(_ i: Int, _ n: Int) -> Double {
            let x = sin(Double(i + 1) * 17.371 + Double(n) * 91.719) * 43758.5453
            return x - floor(x)
        }
        static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
        static func segment(_ t: Double, _ start: Double, _ end: Double) -> Double { clamp((t - start) / (end - start)) }
        static func ease(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
        static func smooth(_ x: Double) -> Double {
            let u = clamp(x)
            return u * u * (3 - 2 * u)
        }
        static func gatherAt(_ t: Double, _ i: Int) -> Double {
            let delay = 0.03 * Double(i)
            let reverseDelay = 0.03 * Double(15 - i)
            return ease(segment(t, 1.2 + delay, 2.4 + delay)) * (1 - ease(segment(t, 5.8 + reverseDelay, 7 + reverseDelay)))
        }
        static func dealsAt(_ t: Double) -> Double { min(4, max(0, (t - 2.6) / 0.75)) }
        static func captionOpacity(_ t: Double, _ slot: Int) -> Double {
            if slot == 0 { return 1 - smooth(segment(t, 1.6, 2)) + smooth(segment(t, 6.3, 6.7)) }
            let pile = smooth(segment(t, 2.2, 2.5)) * (1 - smooth(segment(t, 5.8, 6.1)))
            return pile * (1 - smooth((abs(dealsAt(t) - Double(slot - 1)) - 0.35) / 0.15))
        }
    }

    private enum Palette {
        static let paper = Color(uiColor: ui(0xEEECE6))
        static let ink = Color(uiColor: ui(0x121212))
        static func ui(_ hex: UInt32, alpha: CGFloat = 1) -> UIColor {
            UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: alpha)
        }
    }

    private enum Typography {
        static let registered: Void = {
            if let url = Bundle.main.url(forResource: "OrbitStackArchive-Anton-Regular", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }()
        private static var cache: [String: Image] = [:]

        static func anton(_ size: CGFloat) -> UIFont {
            _ = registered
            return UIFont(name: "Anton-Regular", size: size) ?? .systemFont(ofSize: size, weight: .black)
        }

        // RN uses TextKit without font leading. Its explicit line heights are shorter than
        // Anton's natural metrics; rendering with the same layout avoids SwiftUI recentering.
        static func label(_ text: String, font: UIFont, color: UIColor, x: CGFloat, y: CGFloat,
                          width: CGFloat, lineHeight: CGFloat? = nil, tracking: CGFloat = 0,
                          alignment: NSTextAlignment = .left) {
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = alignment
            if let lineHeight {
                paragraph.minimumLineHeight = lineHeight
                paragraph.maximumLineHeight = lineHeight
            }
            var attributes: [NSAttributedString.Key: Any] = [
                .font: font, .foregroundColor: color, .kern: tracking, .paragraphStyle: paragraph
            ]
            if let lineHeight, lineHeight >= font.lineHeight {
                attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2
            }
            let storage = NSTextStorage(attributedString: NSAttributedString(string: text, attributes: attributes))
            let layout = NSLayoutManager()
            layout.usesFontLeading = false
            let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
            container.lineFragmentPadding = 0
            container.maximumNumberOfLines = 1
            layout.addTextContainer(container)
            storage.addLayoutManager(layout)
            let glyphs = layout.glyphRange(for: container)
            layout.drawGlyphs(forGlyphRange: glyphs, at: CGPoint(x: x, y: y))
        }

        private static func image(key: String, size: CGSize, draw: () -> Void) -> Image {
            if let cached = cache[key] { return cached }
            let format = UIGraphicsImageRendererFormat()
            format.scale = 3
            format.opaque = false
            let rendered = UIGraphicsImageRenderer(size: size, format: format).image { _ in draw() }
            let result = Image(uiImage: rendered)
            cache[key] = result
            return result
        }

        static func header(width: CGFloat, brand: String) -> Image {
            image(key: "head|\(width)|\(brand)", size: CGSize(width: width, height: 232)) {
                let ink = Palette.ui(0x121212)
                ink.setFill()
                UIRectFill(CGRect(x: 0, y: 28.5, width: width, height: 1.5))
                let brandFont = anton(18)
                label(brand, font: brandFont, color: ink, x: 0, y: (28.5 - brandFont.lineHeight) / 2,
                      width: width, tracking: 1)
                let countFont = UIFont.systemFont(ofSize: 12, weight: .bold)
                label("16 / 16", font: countFont, color: ink, x: 0, y: (28.5 - countFont.lineHeight) / 2,
                      width: width, tracking: 0.5, alignment: .right)
                label("POSTER", font: anton(88), color: ink, x: 0, y: 32,
                      width: width, lineHeight: 112, tracking: -0.5)
                label("ARCHIVE", font: anton(88), color: ink, x: 0, y: 120,
                      width: width, lineHeight: 112, tracking: -0.5)
            }
        }

        static func caption(width: CGFloat, title: String, meta: String) -> Image {
            image(key: "caption|\(width)|\(title)|\(meta)", size: CGSize(width: width, height: 60)) {
                label(title.uppercased(), font: anton(34), color: Palette.ui(0x121212), x: 0, y: 0,
                      width: width, lineHeight: 40, alignment: .center)
                label(meta, font: .systemFont(ofSize: 13, weight: .semibold), color: Palette.ui(0x8A877F), x: 0, y: 42,
                      width: width, lineHeight: 18, tracking: 0.3, alignment: .center)
            }
        }

        static func footer(width: CGFloat, action: String) -> Image {
            image(key: "footer|\(width)|\(action)", size: CGSize(width: width, height: 56)) {
                Palette.ui(0x121212).setFill()
                UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: width, height: 56), cornerRadius: 4).fill()
                let font = UIFont.systemFont(ofSize: 16, weight: .bold)
                label(action, font: font, color: Palette.ui(0xEEECE6), x: 20, y: (56 - font.lineHeight) / 2,
                      width: width - 40, tracking: 0.2)
                let arrow = UIFont.systemFont(ofSize: 20, weight: .bold)
                label("→", font: arrow, color: Palette.ui(0xEEECE6), x: 20, y: (56 - arrow.lineHeight) / 2,
                      width: width - 40, alignment: .right)
            }
        }
    }

    private enum PosterArt {
        // Decode and compose the eight posters once. Frames only transform these bitmaps.
        static let images: [Image] = ["bust", "stair", "blob", "sphere", "hand", "flower", "grid", "waves"].enumerated().map { design, name in
            let format = UIGraphicsImageRendererFormat()
            format.scale = 3
            format.opaque = false
            let image = UIGraphicsImageRenderer(size: CGSize(width: 200, height: 268), format: format).image { renderer in
                let context = renderer.cgContext
                let card = UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 200, height: 268), cornerRadius: 3)
                context.saveGState()
                card.addClip()
                Palette.ui(0xD9D6CF).setFill()
                card.fill()
                if let url = Bundle.main.url(forResource: "OrbitStackArchive-poster-\(name)", withExtension: "jpg"),
                   let art = UIImage(contentsOfFile: url.path) {
                    let scale = max(200 / art.size.width, 268 / art.size.height)
                    let size = CGSize(width: art.size.width * scale, height: art.size.height * scale)
                    art.draw(in: CGRect(x: (200 - size.width) / 2, y: (268 - size.height) / 2, width: size.width, height: size.height))
                }
                let meta = Motion.designs[design].meta
                switch design {
                case 0:
                    word("FORM", x: 12, baseline: 254, size: 86, color: 0xFFFFFF, context: context)
                    metadata(meta, baseline: 22, color: 0xFFFFFF, alpha: 0.9, context: context)
                case 1:
                    rectangle(x: 0, y: 202, width: 200, height: 66, color: 0xE3241B, context: context)
                    word("ASCENT", x: 12, baseline: 254, size: 46, color: 0xFFFFFF, context: context)
                    metadata(meta, baseline: 22, color: 0x121212, alpha: 0.85, context: context)
                case 2:
                    word("YEP.", x: 10, baseline: 256, size: 96, color: 0x121212, context: context)
                    metadata(meta, baseline: 22, color: 0x121212, alpha: 0.85, context: context)
                case 3:
                    word("BLUE", x: 12, baseline: 72, size: 62, color: 0xFFFFFF, context: context)
                    word("HOUR", x: 12, baseline: 132, size: 62, color: 0xFFFFFF, context: context)
                    metadata(meta, baseline: 254, color: 0xFFFFFF, alpha: 0.85, context: context)
                case 4:
                    word("DRAW", x: 188, baseline: 254, size: 84, color: 0xE3241B, right: true, context: context)
                    metadata(meta, baseline: 22, color: 0x121212, alpha: 0.85, context: context)
                case 5:
                    word("BLOOM", x: 12, baseline: 254, size: 70, color: 0xFFFFFF, context: context)
                    metadata(meta, baseline: 22, color: 0xFFFFFF, alpha: 0.9, context: context)
                case 6:
                    word("No.7", x: 12, baseline: 256, size: 92, color: 0x121212, context: context)
                    metadata(meta, baseline: 22, color: 0x121212, alpha: 0.85, context: context)
                default:
                    rectangle(x: 10, y: 206, width: 180, height: 52, color: 0xFFFFFF, context: context)
                    word("LOUD TYPE", x: 20, baseline: 248, size: 38, color: 0x121212, context: context)
                    metadata(meta, baseline: 22, color: 0xFFFFFF, alpha: 0.9, context: context)
                }
                context.restoreGState()
            }
            return Image(uiImage: image)
        }

        private static func rectangle(x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat, color: UInt32, context: CGContext) {
            context.setFillColor(Palette.ui(color).cgColor)
            context.fill(CGRect(x: x, y: y, width: width, height: height))
        }

        private static func word(_ string: String, x: CGFloat, baseline: CGFloat, size: CGFloat, color: UInt32,
                                 right: Bool = false, context: CGContext) {
            text(string, font: Typography.anton(size), x: x, baseline: baseline,
                 color: Palette.ui(color), right: right, context: context)
        }

        private static func metadata(_ string: String, baseline: CGFloat, color: UInt32, alpha: CGFloat, context: CGContext) {
            let descriptor = UIFontDescriptor(fontAttributes: [
                .family: "Helvetica Neue", .traits: [UIFontDescriptor.TraitKey.weight: UIFont.Weight.semibold]
            ])
            text(string, font: UIFont(descriptor: descriptor, size: 10), x: 13, baseline: baseline,
                 color: Palette.ui(color, alpha: alpha), context: context)
        }

        private static func text(_ string: String, font: UIFont, x: CGFloat, baseline: CGFloat, color: UIColor,
                                 right: Bool = false, context: CGContext) {
            let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [
                .font: font, .foregroundColor: color, .kern: 0, .ligature: 0
            ]))
            let width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
            context.saveGState()
            context.translateBy(x: right ? x - width : x, y: baseline)
            context.scaleBy(x: 1, y: -1)
            context.textMatrix = .identity
            context.textPosition = .zero
            CTLineDraw(line, context)
            context.restoreGState()
        }
    }
}

#Preview {
    OrbitStackArchive(autoPlay: true)
}
