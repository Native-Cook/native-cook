import CoreText
import SwiftUI
import UIKit

// A faithful port of the React Native reference. Fourteen 150 × 210 pt prints are mapped onto
// projected quads (900 pt focal distance, camera tilted −10°), sorted by projected depth.
//
// Eight-second loop:
//   0.0–1.0  rest, twist 0, lift 3 pt
//   1.0–3.4  twist 0 → 0.42 rad, lift 3 → 16 pt, ease-in-out cubic
//   1.0–6.8  turn 0 → 2π, ease-in-out sine
//   4.6–6.8  twist and lift return, ease-in-out cubic
//   6.8–8.0  rest, identical to the first frame
// Dragging winds by 0.42 × clamp(dx / 220, −1, 1) and turns by dx / 120. On release the pose
// holds for 2 s, then unwinds over 0.9 s to the nearest full turn and restarts the loop.

struct CardHelixTwist: View {
    var brand = "Holo"
    var title = "Spring prints"
    var action = "Open collection"
    var autoPlay = false

    static let loop: Double = 8

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var inactiveSince: Date?
    @State private var manual = false
    @State private var touching = false
    @State private var released: Date?
    @State private var userW = 0.0
    @State private var userTurn = 0.0
    @State private var fromTurn = 0.0
    @State private var touchVersion = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .soft)
    @GestureState private var gestureActive = false

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let statsHeight = ceil(CardHelixTwistArtwork.font(20).lineHeight) + 2 + ceil(UIFont.systemFont(ofSize: 13).lineHeight)
            let footerTop = size.height - safe.bottom - 14 - 58 - 16 - statsHeight
            TimelineView(.animation(paused: reduceMotion || scenePhase != .active || (!autoPlay && !manual))) { timeline in
                CardHelixTwistSurface(
                    pose: pose(at: timeline.date), safe: safe,
                    brand: brand, title: title, action: action
                )
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(drag(footerTop: footerTop, width: size.width))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            origin = .now
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, let start = inactiveSince {
                let paused = Date().timeIntervalSince(start)
                origin = origin.addingTimeInterval(paused)
                if let date = released { released = date.addingTimeInterval(paused) }
                inactiveSince = nil
            } else if phase != .active, inactiveSince == nil {
                let now = Date()
                finishTouch(at: now)
                inactiveSince = now
            }
        }
        .onChange(of: gestureActive) { _, active in
            // GestureState resets on cancellation too; this mirrors RN's onFinalize.
            if !active { finishTouch() }
        }
        .task(id: [touchVersion, scenePhase == .active ? 1 : 0, reduceMotion ? 1 : 0]) {
            guard manual, !touching, scenePhase == .active, !reduceMotion, let date = released else { return }
            // The pose is evaluated from the release date, not accumulated frame state.
            let deadline = date.addingTimeInterval(2.9)
            try? await Task.sleep(for: .seconds(max(0, deadline.timeIntervalSinceNow)))
            guard !Task.isCancelled, !touching else { return }
            manual = false
            released = nil
            origin = .now
        }
    }

    private func pose(at date: Date) -> CardHelixTwistMotion.Pose {
        guard !reduceMotion else { return .rest }
        let now = inactiveSince ?? date
        if manual {
            let k = touching ? 0 : CardHelixTwistMotion.cubic(
                CardHelixTwistMotion.clamp(((released.map { now.timeIntervalSince($0) } ?? 0) - 2) / 0.9)
            )
            let w = userW * (1 - k)
            // JavaScript Math.round chooses the higher integer at a negative half-turn too.
            let target = floor(fromTurn / (2 * .pi) + 0.5) * 2 * .pi
            return .init(twist: w, lift: 3 + 13 * abs(w) / 0.42,
                         turn: touching ? userTurn : fromTurn + (target - fromTurn) * k)
        }
        guard autoPlay else { return .rest }
        let t = max(0, now.timeIntervalSince(origin)).truncatingRemainder(dividingBy: Self.loop)
        return CardHelixTwistMotion.at(t)
    }

    private func drag(footerTop: CGFloat, width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !reduceMotion else { return }
                guard !(value.startLocation.y >= footerTop && value.startLocation.x >= 24 && value.startLocation.x <= width - 24) else { return }
                if !touching {
                    let current = pose(at: .now)
                    touching = true
                    manual = true
                    userTurn = current.turn
                    fromTurn = current.turn
                    released = nil
                    touchVersion += 1
                    haptic.impactOccurred()
                    // Match onBegin: retain userW until the first drag update arrives.
                    return
                }
                let dx = Double(value.translation.width)
                userW = 0.42 * min(1, max(-1, dx / 220))
                userTurn = fromTurn + dx / 120
            }
            .onEnded { _ in finishTouch() }
    }

    private func finishTouch(at date: Date = .now) {
        guard touching else { return }
        touching = false
        fromTurn = userTurn
        released = date
        touchVersion += 1
    }
}

private enum CardHelixTwistMotion {
    struct Pose {
        var twist: Double
        var lift: Double
        var turn: Double
        static let rest = Pose(twist: 0, lift: 3, turn: 0)
    }

    struct Card {
        var index: Int
        var depth: Double
        var front: Bool
        var transform: CATransform3D
    }

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func cubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    static func at(_ t: Double) -> Pose {
        let wind = cubic(segment(t, 1, 3.4)) * (1 - cubic(segment(t, 4.6, 6.8)))
        let turn = 2 * Double.pi * (1 - cos(.pi * segment(t, 1, 6.8))) / 2
        return Pose(twist: 0.42 * wind, lift: 3 + 13 * wind, turn: turn)
    }

    static func cards(_ pose: Pose, size: CGSize) -> [Card] {
        let tilt = -10 * Double.pi / 180
        let ct = cos(tilt), st = sin(tilt)
        let corners: [CGPoint] = [.init(x: -75, y: -105), .init(x: 75, y: -105),
                                  .init(x: 75, y: 105), .init(x: -75, y: 105)]
        return (0..<14).map { j in
            let theta = pose.turn + pose.twist * (Double(j) - 6.5)
            let yc = (6.5 - Double(j)) * pose.lift
            var depth = 0.0
            let q: [CGPoint] = corners.map { corner in
                let x = corner.x * cos(theta)
                let z = -corner.x * sin(theta) - Double(j) * 0.6
                let y = yc + corner.y
                let y1 = y * ct - z * st
                let z1 = y * st + z * ct
                let scale = 900 / (900 - z1)
                depth += z1 / 4
                return CGPoint(x: size.width / 2 + x * scale, y: size.height * 0.52 + y1 * scale)
            }
            let cross = (q[1].x - q[0].x) * (q[3].y - q[0].y) - (q[1].y - q[0].y) * (q[3].x - q[0].x)
            return Card(index: j, depth: depth - Double(j) * 0.01, front: cross > 0,
                        transform: homography(q))
        }.sorted { $0.depth < $1.depth }
    }

    private static func homography(_ q: [CGPoint]) -> CATransform3D {
        let dx1 = q[1].x - q[2].x, dx2 = q[3].x - q[2].x
        let dx3 = q[0].x - q[1].x + q[2].x - q[3].x
        let dy1 = q[1].y - q[2].y, dy2 = q[3].y - q[2].y
        let dy3 = q[0].y - q[1].y + q[2].y - q[3].y
        let den = dx1 * dy2 - dx2 * dy1
        let g = den == 0 ? 0 : (dx3 * dy2 - dx2 * dy3) / den
        let k = den == 0 ? 0 : (dx1 * dy3 - dx3 * dy1) / den
        var m = CATransform3DIdentity
        m.m11 = (q[1].x - q[0].x + g * q[1].x) / 150
        m.m21 = (q[3].x - q[0].x + k * q[3].x) / 210
        m.m41 = q[0].x
        m.m12 = (q[1].y - q[0].y + g * q[1].y) / 150
        m.m22 = (q[3].y - q[0].y + k * q[3].y) / 210
        m.m42 = q[0].y
        m.m14 = g / 150
        m.m24 = k / 210
        return m
    }
}

private enum CardHelixTwistArtwork {
    static let width: CGFloat = 150
    static let height: CGFloat = 210
    static let hues: [[UInt32]] = [[0xFF6FD8, 0x3813C2], [0x21D4FD, 0xB721FF],
                                  [0xFAD961, 0xF76B1C], [0x43E97B, 0x38F9D7],
                                  [0xFA709A, 0xFEE140], [0x4FACFE, 0x00F2FE], [0xA18CD1, 0xFBC2EB]]
    static let titles = ["PRISM", "HALO", "EMBER", "VERDANT", "BLOOM", "TIDAL", "LILAC"]
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "CardHelixTwist-ArchivoExpanded-Black", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func color(_ hex: UInt32, alpha: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }

    static func font(_ size: CGFloat) -> UIFont {
        _ = registered
        return UIFont(name: "ArchivoExpanded-Black", size: size) ?? .systemFont(ofSize: size, weight: .black)
    }

    static let fronts: [CGImage] = (0..<14).map { j in
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = false
        // A 1 pt gutter retains the half of the outline outside the projected rectangle.
        let image = UIGraphicsImageRenderer(size: CGSize(width: 152, height: 212), format: format).image { renderer in
            let ctx = renderer.cgContext
            ctx.translateBy(x: 1, y: 1)
            let path = UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: width, height: height), cornerRadius: 14).cgPath
            ctx.saveGState()
            ctx.addPath(path)
            ctx.clip()
            let colors = hues[j % 7].map { color($0).cgColor } as CFArray
            let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors, locations: [0, 1])!
            ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: width, y: height), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            let blob = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
                                  colors: [UIColor.white.withAlphaComponent(0.75).cgColor, UIColor.white.withAlphaComponent(0).cgColor] as CFArray,
                                  locations: [0, 1])!
            let centre = CGPoint(x: width * 0.7, y: height * 0.32)
            ctx.drawRadialGradient(blob, startCenter: centre, startRadius: 0, endCenter: centre, endRadius: 70, options: .drawsAfterEndLocation)
            ctx.setStrokeColor(UIColor.white.withAlphaComponent(0.35).cgColor)
            ctx.setLineWidth(10)
            let radius = CGFloat(38 + j % 3 * 8)
            ctx.strokeEllipse(in: CGRect(x: width * 0.3 - radius, y: height * 0.62 - radius, width: radius * 2, height: radius * 2))
            ctx.restoreGState()
            baseline(titles[j % 7], size: 16, at: CGPoint(x: 12, y: 180), in: ctx)
            baseline(String(format: "No. %04d", 421 + j * 37), size: 9, at: CGPoint(x: 12, y: 196), in: ctx)
            ctx.addPath(path)
            ctx.setStrokeColor(UIColor.white.withAlphaComponent(0.55).cgColor)
            ctx.setLineWidth(1.5)
            ctx.strokePath()
        }
        return image.cgImage!
    }

    private static func baseline(_ text: String, size: CGFloat, at point: CGPoint, in ctx: CGContext) {
        ctx.saveGState()
        ctx.translateBy(x: point.x, y: point.y)
        ctx.scaleBy(x: 1, y: -1)
        ctx.textMatrix = .identity
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font(size), .foregroundColor: UIColor.white]))
        ctx.textPosition = .zero
        CTLineDraw(line, ctx)
        ctx.restoreGState()
    }

    static let glow: CGImage = {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        return UIGraphicsImageRenderer(size: CGSize(width: 520, height: 520), format: format).image { renderer in
            let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
                                      colors: [color(0x7C5CFF, alpha: 0.35).cgColor, color(0x7C5CFF, alpha: 0).cgColor] as CFArray,
                                      locations: [0, 1])!
            renderer.cgContext.drawRadialGradient(gradient, startCenter: CGPoint(x: 260, y: 260), startRadius: 0,
                                                   endCenter: CGPoint(x: 260, y: 260), endRadius: 260, options: [])
        }.cgImage!
    }()
}

/// The static print faces are decoded once. Each frame changes only 14 projective transforms,
/// their depth order, and the start/end points of the foil gradients.
private struct CardHelixTwistSurface: UIViewRepresentable {
    var pose: CardHelixTwistMotion.Pose
    var safe: EdgeInsets
    var brand: String
    var title: String
    var action: String

    func makeUIView(context: Context) -> CardHelixTwistScreen { CardHelixTwistScreen() }

    func updateUIView(_ view: CardHelixTwistScreen, context: Context) {
        view.update(pose: pose, safe: safe, brand: brand, title: title, action: action)
    }
}

private final class CardHelixTwistScreen: UIView {
    private let glow = CALayer()
    private let stage = CALayer()
    private let cards = (0..<14).map { CardHelixTwistCardLayer(index: $0) }
    private let brand = UILabel()
    private let count = UILabel()
    private let title = UILabel()
    private let subtitle = UILabel()
    private let setValue = UILabel()
    private let setLabel = UILabel()
    private let rarity = UILabel()
    private let rarityLabel = UILabel()
    private let button = UIView()
    private let action = UILabel()
    private lazy var hero = UIAccessibilityElement(accessibilityContainer: self)
    private var pose = CardHelixTwistMotion.Pose.rest
    private var safe = EdgeInsets()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        backgroundColor = CardHelixTwistArtwork.color(0x0B0A12)
        glow.contents = CardHelixTwistArtwork.glow
        glow.contentsScale = 2
        for card in cards { stage.addSublayer(card) }
        configure(brand, size: 20, archivo: true)
        configure(count, size: 13, alpha: 0.55, weight: .semibold)
        count.text = "14 cards"
        count.textAlignment = .right
        configure(title, size: 34, archivo: true)
        configure(subtitle, size: 15, alpha: 0.6)
        subtitle.numberOfLines = 0
        subtitle.attributedText = paragraph("Seven artists, two of each, printed on holographic foil.", font: subtitle.font, color: subtitle.textColor, height: 20)
        // RN's Canvas follows the header, so the lifted ribbon can cross in front of its text.
        layer.addSublayer(glow)
        layer.addSublayer(stage)
        configure(setValue, size: 20, archivo: true)
        setValue.text = "2 / 7"
        configure(setLabel, size: 13, alpha: 0.55)
        setLabel.text = "Sets complete"
        configure(rarity, size: 20, archivo: true)
        rarity.text = "Rare"
        configure(rarityLabel, size: 13, alpha: 0.55)
        rarityLabel.text = "Next pull"
        button.backgroundColor = .white
        button.layer.cornerRadius = 29
        button.isAccessibilityElement = true
        button.accessibilityTraits = .button
        addSubview(button)
        action.font = CardHelixTwistArtwork.font(15)
        action.textColor = CardHelixTwistArtwork.color(0x0B0A12)
        action.textAlignment = .center
        button.addSubview(action)
        hero.accessibilityLabel = "A stack of print cards winding into a helix. Drag to wind it."
        hero.accessibilityTraits = .image
        accessibilityElements = [brand, count, title, subtitle, hero, setValue, setLabel, rarity, rarityLabel, button]
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func update(pose: CardHelixTwistMotion.Pose, safe: EdgeInsets, brand: String, title: String, action: String) {
        let needsLayout = self.safe != safe || self.brand.text != brand || self.title.text != title || self.action.text != action
        self.pose = pose
        self.safe = safe
        if self.brand.text != brand { self.brand.text = brand }
        if self.title.text != title {
            self.title.attributedText = paragraph(title, font: self.title.font, color: .white, height: 40, kern: -1)
        }
        if self.action.text != action {
            self.action.text = action
            button.accessibilityLabel = action
        }
        layoutCards()
        if needsLayout { setNeedsLayout() }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        let width = bounds.width - 48
        let head = safe.top + 8
        brand.frame = CGRect(x: 24, y: head, width: width - 80, height: 34)
        count.frame = CGRect(x: bounds.width - 104, y: head, width: 80, height: 34)
        title.frame = CGRect(x: 24, y: head + 48, width: width, height: 40)
        subtitle.frame = CGRect(x: 24, y: head + 94, width: width, height: 40)
        let buttonY = bounds.height - safe.bottom - 14 - 58
        button.frame = CGRect(x: 24, y: buttonY, width: width, height: 58)
        action.frame = button.bounds
        let statHeight = ceil(CardHelixTwistArtwork.font(20).lineHeight)
        let labelHeight = ceil(UIFont.systemFont(ofSize: 13).lineHeight)
        let statsY = buttonY - 16 - statHeight - 2 - labelHeight
        let rightX = bounds.width - 24 - max(rarity.intrinsicContentSize.width, rarityLabel.intrinsicContentSize.width)
        setValue.frame = CGRect(x: 24, y: statsY, width: 160, height: statHeight)
        setLabel.frame = CGRect(x: 24, y: statsY + statHeight + 2, width: 160, height: labelHeight)
        rarity.frame = CGRect(x: rightX, y: statsY, width: bounds.width - 24 - rightX, height: statHeight)
        rarityLabel.frame = CGRect(x: rightX, y: statsY + statHeight + 2, width: bounds.width - 24 - rightX, height: labelHeight)
        glow.frame = CGRect(x: bounds.midX - 260, y: bounds.height * 0.52 - 260, width: 520, height: 520)
        stage.frame = bounds
        hero.accessibilityFrameInContainerSpace = CGRect(x: 0, y: head + 134, width: bounds.width, height: max(0, statsY - head - 134))
        layoutCards()
        CATransaction.commit()
    }

    private func layoutCards() {
        guard bounds.width > 0 else { return }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for (order, geometry) in CardHelixTwistMotion.cards(pose, size: bounds.size).enumerated() {
            let card = cards[geometry.index]
            card.zPosition = CGFloat(order)
            card.transform = geometry.transform
            card.show(front: geometry.front, turn: pose.turn, index: geometry.index)
        }
        CATransaction.commit()
    }

    private func configure(_ label: UILabel, size: CGFloat, archivo: Bool = false, alpha: CGFloat = 1, weight: UIFont.Weight = .regular) {
        label.font = archivo ? CardHelixTwistArtwork.font(size) : .systemFont(ofSize: size, weight: weight)
        label.textColor = .white.withAlphaComponent(alpha)
        addSubview(label)
    }

    private func paragraph(_ text: String, font: UIFont, color: UIColor, height: CGFloat, kern: CGFloat = 0) -> NSAttributedString {
        let style = NSMutableParagraphStyle()
        style.minimumLineHeight = height
        style.maximumLineHeight = height
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color, .paragraphStyle: style, .kern: kern]
        if height >= font.lineHeight { attributes[.baselineOffset] = (height - font.lineHeight) / 2 }
        return NSAttributedString(string: text, attributes: attributes)
    }
}

private final class CardHelixTwistCardLayer: CALayer {
    private let frontFace = CALayer()
    private let foil = CAGradientLayer()

    init(index: Int) {
        super.init()
        bounds = CGRect(x: 0, y: 0, width: 150, height: 210)
        anchorPoint = .zero
        position = .zero
        isDoubleSided = true
        frontFace.frame = CGRect(x: -1, y: -1, width: 152, height: 212)
        frontFace.contents = CardHelixTwistArtwork.fronts[index]
        frontFace.contentsScale = 3
        frontFace.minificationFilter = .trilinear
        addSublayer(frontFace)
        foil.frame = bounds
        foil.cornerRadius = 14
        foil.masksToBounds = true
        // Extend the original gradient from t = −1 to t = 3 with mirrored stops. This is
        // Skia TileMode.Mirror, including the regions before/after the original endpoints.
        let colors: [UInt32] = [0x1B1530, 0xFF6FD8, 0x38F9D7, 0x7C5CFF, 0x1B1530,
                                0x7C5CFF, 0x38F9D7, 0xFF6FD8, 0x1B1530,
                                0xFF6FD8, 0x38F9D7, 0x7C5CFF, 0x1B1530,
                                0x7C5CFF, 0x38F9D7, 0xFF6FD8, 0x1B1530]
        foil.colors = colors.map { CardHelixTwistArtwork.color($0).cgColor }
        let stops: [Double] = [0, 0.3, 0.5, 0.7, 1, 1.3, 1.5, 1.7, 2,
                               2.3, 2.5, 2.7, 3, 3.3, 3.5, 3.7, 4]
        foil.locations = stops.map { NSNumber(value: $0 / 4) }
        let rings = CAShapeLayer()
        let path = CGMutablePath()
        for radius: CGFloat in [26, 14] {
            path.addEllipse(in: CGRect(x: 75 - radius, y: 105 - radius, width: radius * 2, height: radius * 2))
        }
        rings.path = path
        rings.fillColor = UIColor.clear.cgColor
        rings.strokeColor = UIColor.white.withAlphaComponent(0.6).cgColor
        rings.lineWidth = 2
        foil.addSublayer(rings)
        addSublayer(foil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override init(layer: Any) { super.init(layer: layer) }

    func show(front: Bool, turn: Double, index: Int) {
        frontFace.isHidden = !front
        foil.isHidden = front
        let s = (turn * 0.5 + Double(index) * 0.2).truncatingRemainder(dividingBy: 1)
        foil.startPoint = CGPoint(x: -2 + s * 2, y: -1)
        foil.endPoint = CGPoint(x: 2 + s * 2, y: 3)
    }
}

#Preview {
    CardHelixTwist(autoPlay: true)
}
