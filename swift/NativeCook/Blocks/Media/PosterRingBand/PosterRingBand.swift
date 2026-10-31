import CoreText
import SwiftUI
import UIKit

// RN reference: twelve 92 × 128 pt posters on a 150 pt ring, focal length 820 pt.
// One linear turn in 9 s; x tilt −16° ± 3° sin(4πt/9), z roll 8°.
// Drag x/150 radians, coast with exp(−2.5 dt) for 2 s, then cubic snap for 0.6 s.
struct PosterRingBand: View {
    static let loop: Double = 9
    var title = "ORBIT"
    var dates = "Fest 26 · 12–14 Jun · Porto Vale"
    var action = "Get festival passes"
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var engine = PosterRingBandEngine()
    @State private var touchRevision = 0
    @GestureState private var gestureActive = false

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            TimelineView(.animation(paused: reduced || scene != .active)) { timeline in
                let frame = engine.frame(at: timeline.date, running: scene == .active && !reduced, autoPlay: autoPlay)
                ZStack(alignment: .topLeading) {
                    PosterRingBandArt.paper
                    Canvas { context, _ in
                        PosterRingBandArt.drawCopy(context, size: size, safe: safe, turn: frame.turn,
                                                   title: title, dates: dates, action: action)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .allowsHitTesting(false)

                    ForEach(PosterRingBandGeometry.items(turn: frame.turn, clock: frame.clock, size: size)) { item in
                        Image(uiImage: item.facing ? PosterRingBandArt.posters[item.id % 6] : PosterRingBandArt.back)
                            .resizable()
                            .frame(width: 92, height: 128, alignment: .topLeading)
                            .overlay {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(PosterRingBandArt.paper.opacity(item.shade))
                            }
                            .compositingGroup()
                            .projectionEffect(item.matrix)
                            .zIndex(item.depth + 200)
                            .allowsHitTesting(false)
                    }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .clipped()
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(title). Festival line-up ring. Drag to spin.")
                .overlay(alignment: .bottom) {
                    Color.clear.frame(height: 58)
                        .padding(.horizontal, 20)
                        .padding(.bottom, safe.bottom + 14)
                        .accessibilityLabel(action)
                        .accessibilityAddTraits(.isButton)
                        .allowsHitTesting(false)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            // The RN footer is outside its GestureDetector.
            .contentShape(Rectangle().path(in: CGRect(x: 0, y: 0, width: size.width,
                                                      height: size.height - safe.bottom - 72)))
            .gesture(pan, including: reduced ? .none : .all)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onChange(of: scene) { _, _ in engine.pauseClock() }
        .onChange(of: reduced) { _, _ in engine.pauseClock() }
        .onChange(of: gestureActive) { _, active in
            if !active && engine.touching {
                Task { @MainActor in
                    await Task.yield()
                    if !gestureActive && engine.touching { engine.end(); touchRevision += 1 }
                }
            }
        }
    }

    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !reduced else { return }
                if !engine.touching { engine.begin(x: value.startLocation.x) }
                if hypot(value.translation.width, value.translation.height) >= 4 { engine.activated = true }
                if engine.activated { engine.move(x: value.location.x, velocityX: value.velocity.width) }
                touchRevision += 1
            }
            .onEnded { _ in engine.end(); touchRevision += 1 }
    }
}

// Plain frame state mirrors RN's UI-thread callback without publishing state every frame.
private final class PosterRingBandEngine {
    struct Frame { var turn: Double; var clock: Double }
    private var previous: Date?
    private var elapsed = 0.0
    private var origin = 0.0
    private var turn = 0.0
    private var clock = 0.0
    private var spin = 0.0
    private var velocity = 0.0
    private var manual = false
    private var released = -1.0
    private var settleFrom = 0.0
    private var lastX = 0.0
    private var lastFront = 0
    private let feedback = UISelectionFeedbackGenerator()
    var touching = false
    var activated = false

    func pauseClock() { previous = nil }

    func frame(at date: Date, running: Bool, autoPlay: Bool) -> Frame {
        guard running else { previous = nil; return Frame(turn: turn, clock: clock) }
        let dt = previous.map { max(0, date.timeIntervalSince($0)) } ?? 0
        previous = date
        elapsed += dt
        clock = (elapsed - origin).truncatingRemainder(dividingBy: PosterRingBand.loop)
        if touching {
            turn = spin
        } else if manual {
            let since = elapsed - released
            if since < 2 {
                spin += velocity * dt
                velocity *= exp(-2.5 * dt)
                settleFrom = spin
            } else {
                let step = 2 * Double.pi / 12
                let target = floor(settleFrom / step + 0.5) * step
                spin = settleFrom + (target - settleFrom) * PosterRingBandGeometry.ease(min(1, max(0, (since - 2) / 0.6)))
                if since > 2.6 {
                    manual = false
                    // Preserve the reference's continuation formula, including its angle sign.
                    let frac = PosterRingBandGeometry.mod(-target / (2 * .pi), 1)
                    origin = elapsed - (1 - frac) * PosterRingBand.loop
                }
            }
            turn = spin
        } else {
            turn = autoPlay ? -2 * .pi * clock / PosterRingBand.loop : 0
        }
        return Frame(turn: turn, clock: clock)
    }

    func begin(x: Double) {
        touching = true; manual = true; activated = false
        spin = turn; velocity = 0; lastX = x
        feedback.prepare()
    }

    func move(x: Double, velocityX: Double) {
        spin += (x - lastX) / 150
        lastX = x
        velocity = velocityX / 150
        let front = Int(PosterRingBandGeometry.mod(floor(-spin / (2 * .pi / 12) + 0.5), 12))
        if front != lastFront {
            lastFront = front
            feedback.selectionChanged(); feedback.prepare()
        }
    }

    func end() {
        guard touching else { return }
        touching = false; released = elapsed
    }
}

private enum PosterRingBandGeometry {
    struct Point { var x: Double; var y: Double; var z: Double }
    struct Item: Identifiable {
        var id: Int
        var depth: Double
        var matrix: ProjectionTransform
        var facing: Bool
        var shade: Double
    }
    static func mod(_ x: Double, _ d: Double) -> Double { (x.truncatingRemainder(dividingBy: d) + d).truncatingRemainder(dividingBy: d) }
    static func ease(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    static func project(_ x: Double, _ y: Double, _ z: Double, tilt: Double, size: CGSize) -> Point {
        let y1 = y * cos(tilt) - z * sin(tilt)
        let z1 = y * sin(tilt) + z * cos(tilt)
        let roll = 8 * Double.pi / 180
        let x2 = x * cos(roll) - y1 * sin(roll)
        let y2 = x * sin(roll) + y1 * cos(roll)
        let s = 820 / (820 - z1)
        return Point(x: size.width / 2 + x2 * s, y: size.height / 2 + y2 * s, z: z1)
    }

    static func items(turn: Double, clock: Double, size: CGSize) -> [Item] {
        let tilt = (-16 + 3 * sin(clock / PosterRingBand.loop * .pi * 4)) * .pi / 180
        return (0..<12).map { j in
            let theta = turn + Double(j) * .pi * 2 / 12
            let sx = sin(theta), cz = cos(theta)
            let px = 150 * sx, pz = 150 * cz
            let p = [project(px - cz * 46, -64, pz + sx * 46, tilt: tilt, size: size),
                     project(px + cz * 46, -64, pz - sx * 46, tilt: tilt, size: size),
                     project(px + cz * 46, 64, pz - sx * 46, tilt: tilt, size: size),
                     project(px - cz * 46, 64, pz + sx * 46, tilt: tilt, size: size)]
            let depth = p.reduce(0) { $0 + $1.z / 4 }
            let cross = (p[1].x - p[0].x) * (p[3].y - p[0].y) - (p[1].y - p[0].y) * (p[3].x - p[0].x)
            let shade = 0.55 * (1 - min(1, max(0, (depth + 150) / 300)))
            return Item(id: j, depth: depth, matrix: matrix(p), facing: cross > 0, shade: shade)
        }.sorted { $0.depth < $1.depth }
    }

    static func matrix(_ p: [Point]) -> ProjectionTransform {
        let dx1 = p[1].x - p[2].x, dx2 = p[3].x - p[2].x
        let dx3 = p[0].x - p[1].x + p[2].x - p[3].x
        let dy1 = p[1].y - p[2].y, dy2 = p[3].y - p[2].y
        let dy3 = p[0].y - p[1].y + p[2].y - p[3].y
        let den = dx1 * dy2 - dx2 * dy1
        let g = den == 0 ? 0 : (dx3 * dy2 - dx2 * dy3) / den
        let k = den == 0 ? 0 : (dx1 * dy3 - dx3 * dy1) / den
        var m = ProjectionTransform()
        m.m11 = (p[1].x - p[0].x + g * p[1].x) / 92
        m.m21 = (p[3].x - p[0].x + k * p[3].x) / 128
        m.m31 = p[0].x
        m.m12 = (p[1].y - p[0].y + g * p[1].y) / 92
        m.m22 = (p[3].y - p[0].y + k * p[3].y) / 128
        m.m32 = p[0].y
        m.m13 = g / 92; m.m23 = k / 128; m.m33 = 1
        return m
    }
}

private enum PosterRingBandArt {
    struct Act { var name: String; var day: String; var bg: UInt32; var ink: UInt32; var mark: String }
    static let acts = [
        Act(name: "LUMEN DUO", day: "FRI", bg: 0xE3241B, ink: 0xFFFFFF, mark: "L"),
        Act(name: "AMA VALE", day: "FRI", bg: 0x111111, ink: 0xF2EFE8, mark: "A"),
        Act(name: "THE ORCHARDS", day: "SAT", bg: 0xF2D13B, ink: 0x111111, mark: "O"),
        Act(name: "KIYO", day: "SAT", bg: 0x1F3BD6, ink: 0xFFFFFF, mark: "K"),
        Act(name: "RAFA & SOL", day: "SUN", bg: 0xF2EFE8, ink: 0x111111, mark: "R"),
        Act(name: "MONO BLOOM", day: "SUN", bg: 0xFF6A2B, ink: 0x111111, mark: "M")
    ]
    static func uiColor(_ hex: UInt32) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
                blue: Double(hex & 255) / 255, alpha: 1)
    }
    static let paper = Color(uiColor: uiColor(0xEDEBE6))
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "PosterRingBand-Anton-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
    static func anton(_ size: CGFloat) -> UIFont {
        _ = registered
        return UIFont(name: "Anton-Regular", size: size) ?? .systemFont(ofSize: size, weight: .black)
    }

    // Cache the static poster artwork once; only the homographies and shade move per frame.
    static let posters: [UIImage] = acts.map { act in
        UIGraphicsImageRenderer(size: CGSize(width: 92, height: 128)).image { renderer in
            uiColor(act.bg).setFill()
            UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 92, height: 128), cornerRadius: 4).fill()
            func word(_ string: String, _ x: Double, _ y: Double, _ size: Double) {
                let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [
                    .font: anton(size), .foregroundColor: uiColor(act.ink)
                ]))
                let ctx = renderer.cgContext
                ctx.saveGState(); ctx.translateBy(x: x, y: y); ctx.scaleBy(x: 1, y: -1)
                ctx.textMatrix = .identity; ctx.textPosition = .zero
                CTLineDraw(line, ctx); ctx.restoreGState()
            }
            word(act.mark, 7, 92, 92)
            uiColor(act.ink).withAlphaComponent(0.6).setFill()
            UIRectFill(CGRect(x: 7, y: 99, width: 78, height: 1.5))
            word(act.name, 7, 113, 11)
            word(act.day, 67, 123, 9)
            word("ORBIT 26", 7, 123, 8)
        }
    }
    static let back = UIGraphicsImageRenderer(size: CGSize(width: 92, height: 128)).image { _ in
        uiColor(0x2A2A2A).setFill()
        UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 92, height: 128), cornerRadius: 4).fill()
        uiColor(0x3A3A3A).setFill()
        UIRectFill(CGRect(x: 10, y: 10, width: 72, height: 108))
    }

    struct TextSprite { var image: Image; var size: CGSize }
    private static var textCache: [String: TextSprite] = [:]
    static func text(_ string: String, font: UIFont, ink: UInt32 = 0x111111,
                     tracking: Double = 0, lineHeight: Double? = nil) -> TextSprite {
        let key = "\(string)|\(font.fontName)|\(font.pointSize)|\(ink)|\(tracking)|\(lineHeight ?? 0)"
        if let cached = textCache[key] { return cached }
        var attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: uiColor(ink), .kern: tracking]
        if let lineHeight {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight; paragraph.maximumLineHeight = lineHeight
            attrs[.paragraphStyle] = paragraph
            if lineHeight >= font.lineHeight { attrs[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
        }
        let storage = NSTextStorage(string: string, attributes: attrs)
        let layout = NSLayoutManager(); layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: 2000, height: 300))
        container.lineFragmentPadding = 0; container.maximumNumberOfLines = 1; container.lineBreakMode = .byClipping
        layout.addTextContainer(container); storage.addLayoutManager(layout)
        let range = layout.glyphRange(for: container)
        let used = layout.usedRect(for: container)
        let scale = UIScreen.main.scale
        let dimensions = CGSize(width: max(1, ceil(used.width * scale) / scale),
                                height: max(1, ceil(used.height * scale) / scale))
        let image = UIGraphicsImageRenderer(size: dimensions).image { _ in layout.drawGlyphs(forGlyphRange: range, at: .zero) }
        let sprite = TextSprite(image: Image(uiImage: image), size: dimensions)
        textCache[key] = sprite
        return sprite
    }

    static func drawCopy(_ context: GraphicsContext, size: CGSize, safe: EdgeInsets, turn: Double,
                         title: String, dates: String, action: String) {
        func draw(_ sprite: TextSprite, x: Double, y: Double, opacity: Double = 1) {
            var ctx = context; ctx.opacity = opacity
            ctx.draw(sprite.image, in: CGRect(origin: CGPoint(x: x, y: y), size: sprite.size))
        }
        draw(text(title, font: anton(132), tracking: -2, lineHeight: 150), x: 20, y: safe.top + 6)
        draw(text(dates, font: .systemFont(ofSize: 15, weight: .bold), tracking: 0.3), x: 20, y: safe.top + 146)
        for k in 0..<12 {
            let pos = -turn / (2 * .pi / 12)
            var d = abs(PosterRingBandGeometry.mod(pos - Double(k) + 6, 12) - 6)
            d = min(d, 12 - d)
            let opacity = 1 - min(1, max(0, d / 0.5))
            if opacity > 0 {
                let act = acts[k % 6]
                let name = text(act.name, font: anton(36), lineHeight: 44)
                let slot = text("\(act.day) · Main stage · \(["20:00", "21:30", "23:00"][k % 3])",
                                font: .systemFont(ofSize: 14, weight: .semibold), ink: 0x6E6B64)
                let y = size.height / 2 + 150 + 10 * (1 - opacity)
                draw(name, x: (size.width - name.size.width) / 2, y: y, opacity: opacity)
                draw(slot, x: (size.width - slot.size.width) / 2, y: y + 44, opacity: opacity)
            }
        }
        let button = CGRect(x: 20, y: size.height - safe.bottom - 72, width: size.width - 40, height: 58)
        context.fill(Path(roundedRect: button, cornerRadius: 4), with: .color(Color(uiColor: uiColor(0x111111))))
        let label = text(action, font: .systemFont(ofSize: 16, weight: .bold), ink: 0xEDEBE6, tracking: 0.3)
        draw(label, x: button.midX - label.size.width / 2, y: button.midY - label.size.height / 2)
    }
}

#Preview { PosterRingBand(autoPlay: true) }
