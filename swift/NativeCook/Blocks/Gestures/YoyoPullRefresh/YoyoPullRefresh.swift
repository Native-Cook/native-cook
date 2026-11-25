import CoreText
import SwiftUI
import UIKit

/// A self-contained inbox. Touch always takes over the current demonstration frame.
struct YoyoPullRefresh: UIViewRepresentable {
    static let loop = 6.0
    var autoPlay = false
    func makeUIView(context: Context) -> YoyoPullRefreshInboxView { YoyoPullRefreshInboxView(autoPlay: autoPlay) }
    func updateUIView(_ view: YoyoPullRefreshInboxView, context: Context) { view.autoPlay = autoPlay }
}

private let yoyoCards: [(UInt32, String, String)] = [
    (0x2B6BFF, "Weekend plans", "Ines · 2 min"), (0xFF5C8A, "Your order shipped", "Parcel 7 · 1 h"),
    (0x36D39A, "New release notes", "Studio · 3 h"), (0xFFB21E, "Lunch?", "Kai · 5 h"),
    (0x9B6BFF, "Flight check-in", "Wren Air · 6 h"), (0xFF7A3D, "Rent reminder", "Home · 1 d"),
    (0x3DC8FF, "Photos from Sunday", "Mira · 2 d")
]

final class YoyoPullRefreshInboxView: UIView {
    var autoPlay: Bool
    private var touch = YoyoPullRefreshTouch()
    private var clock = 0.0, lastStamp = 0.0, lastBeat = -1.0, pressY = 0.0
    private var originStamp = 0.0, clockBase = 0.0
    private var reduced = UIAccessibility.isReduceMotionEnabled
    private var link: CADisplayLink?
    private var observers: [NSObjectProtocol] = []
    private var nodes: [UIAccessibilityElement] = []
    private let image = UIImage(contentsOfFile: Bundle.main.path(forResource: "YoyoPullRefresh-yoyo", ofType: "png") ?? "")
    private let titleFont = YoyoPullRefreshInboxView.font("InterDisplay-Bold", 40)
    private let statusFont = YoyoPullRefreshInboxView.font("Inter-Medium", 15)
    private let cardFont = YoyoPullRefreshInboxView.font("Inter-SemiBold", 17)
    private let metaFont = YoyoPullRefreshInboxView.font("Inter-Medium", 14)

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        super.init(frame: .zero)
        touch.manual = !autoPlay
        backgroundColor = .black; isOpaque = true; isMultipleTouchEnabled = false
        isAccessibilityElement = false
        let heading = UIAccessibilityElement(accessibilityContainer: self)
        heading.accessibilityLabel = "Inbox"; heading.accessibilityTraits = .header
        heading.accessibilityIdentifier = "yoyo-inbox-heading"
        nodes = [heading]
        for (i, card) in yoyoCards.enumerated() {
            let node = UIAccessibilityElement(accessibilityContainer: self)
            node.accessibilityIdentifier = "yoyo-inbox-card-\(i)"
            node.accessibilityLabel = "\(card.1), \(card.2)"
            node.accessibilityCustomActions = [UIAccessibilityCustomAction(name: "Refresh") { [weak self] _ in
                self?.startRefresh(); return true
            }]
            nodes.append(node)
        }
        accessibilityElements = nodes
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self else { return }
            let overflow = self.touch.overflow
            self.reduced = UIAccessibility.isReduceMotionEnabled; self.touch = YoyoPullRefreshTouch(); self.lastBeat = -1
            self.touch.overflow = overflow
            self.touch.manual = !self.autoPlay; self.touch.loopStart = self.clock
            self.setNeedsDisplay()
        })
        observers.append(center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in self?.link?.isPaused = true })
        observers.append(center.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in self?.lastStamp = 0; self?.link?.isPaused = false })
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { link?.invalidate(); observers.forEach(NotificationCenter.default.removeObserver) }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        link?.invalidate(); link = nil; lastStamp = 0
        if window != nil {
            let next = CADisplayLink(target: YoyoPullRefreshDisplayTarget(self), selector: #selector(YoyoPullRefreshDisplayTarget.tick(_:)))
            next.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
            next.add(to: .main, forMode: .common); link = next
        }
    }
    fileprivate func tick(_ link: CADisplayLink) {
        if lastStamp == 0 { originStamp = link.timestamp; clockBase = clock }
        let dt = lastStamp == 0 ? 0 : min(0.1, max(0, link.timestamp - lastStamp))
        lastStamp = link.timestamp; clock = clockBase + (link.timestamp - originStamp)
        if reduced {
            if touch.rmAt >= 0 && clock - touch.rmAt > 3.9 { touch.rmAt = -1; touch.realRefresh = false }
        } else { touch.step(clock, dt, autoPlay: autoPlay) }
        let beat = reduced ? (touch.rmAt >= 0 ? 1.7 + clock - touch.rmAt : -1) : touch.manual ? touch.beat(clock) : -1
        // Side effects belong only to a real refresh, never to the demonstration or a takeover.
        if touch.realRefresh && lastBeat >= 0 && beat >= 0 {
            if lastBeat < 2.05 && beat >= 2.05 { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            if lastBeat < 3.7 && beat >= 3.7 { UIAccessibility.post(notification: .announcement, argument: "Up to date") }
        }
        lastBeat = beat
        updateAccessibility(); setNeedsDisplay()
    }
    private func startRefresh() {
        if reduced {
            guard touch.rmAt < 0 else { return }
            touch.rmAt = clock; touch.realRefresh = true; lastBeat = 1.7
        } else {
            touch.takeOver(clock)
            guard touch.canGrab(clock) else { return }
            touch.refresh(clock, real: true); lastBeat = 1.7
        }
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let point = touches.first?.location(in: self) else { return }
        pressY = point.y
        if reduced { return }
        touch.takeOver(clock)
        if touch.canGrab(clock) { touch.grab(clock) }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard !reduced, touch.touching, let point = touches.first?.location(in: self) else { return }
        touch.drag = point.y - pressY; touch.lastTouch = clock
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        if reduced {
            if let point = touches.first?.location(in: self), YoyoPullRefreshMotion.dragD(point.y - pressY, 0) > 120 { startRefresh() }
        } else if touch.touching { touch.release(clock) }
        else { touch.lastTouch = clock }
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        if !reduced && touch.touching { touch.release(clock) }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        touch.overflow = max(0, 770 * bounds.height / 852 + 112 - bounds.height)
        updateAccessibility()
    }
    private func updateAccessibility() {
        let ox = (bounds.width - 393) / 2, fy = bounds.height / 852
        let d = touch.frame(clock, reduced: reduced).d
        nodes[0].accessibilityFrameInContainerSpace = CGRect(x: ox + 28, y: 64 * fy, width: 120, height: 44)
        for i in 0..<7 { nodes[i + 1].accessibilityFrameInContainerSpace = CGRect(x: ox + 20, y: Double(170 + 100 * i) * fy + d, width: 353, height: 88) }
    }
    private static func color(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, alpha: alpha)
    }
    private static func font(_ name: String, _ size: Double) -> CTFont {
        if let url = Bundle.main.url(forResource: "YoyoPullRefresh-" + name, withExtension: "ttf"),
           let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider) {
            return CTFontCreateWithGraphicsFont(font, size, nil, nil)
        }
        return UIFont.systemFont(ofSize: size) as CTFont
    }
    private func text(_ value: String, _ font: CTFont, _ x: Double, _ baseline: Double, _ hex: UInt32,
                      _ alpha: Double = 1, tracking: Double = 0, in c: CGContext) {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: value, attributes: [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            .foregroundColor: Self.color(hex, alpha), .kern: tracking
        ]))
        c.saveGState(); c.translateBy(x: x, y: baseline); c.scaleBy(x: 1, y: -1)
        c.textMatrix = .identity; c.textPosition = .zero; CTLineDraw(line, c); c.restoreGState()
    }
    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        let f = touch.frame(clock, reduced: reduced), ox = (bounds.width - 393) / 2, fy = bounds.height / 852
        c.setFillColor(UIColor.black.cgColor); c.fill(bounds)
        text("Inbox", titleFont, ox + 28, 98 * fy, 0xFFFFFF, tracking: -1, in: c)
        text(YoyoPullRefreshMotion.status[f.status.shown], statusFont, ox + 30, 124 * fy, 0x7B7F92, f.status.alpha, in: c)
        for (i, card) in yoyoCards.enumerated() {
            let top = Double(170 + i * 100) * fy + f.d, x = ox + 20
            if top > bounds.height || top + 88 < 0 { continue }
            c.setFillColor(Self.color(0x121318).cgColor)
            c.addPath(UIBezierPath(roundedRect: CGRect(x: x, y: top, width: 353, height: 88), cornerRadius: 24).cgPath); c.fillPath()
            c.setFillColor(Self.color(card.0).cgColor)
            c.addPath(UIBezierPath(roundedRect: CGRect(x: x + 18, y: top + 18, width: 52, height: 52), cornerRadius: 16).cgPath); c.fillPath()
            text(card.1, cardFont, x + 86, top + 40, 0xF1F2F7, in: c)
            text(card.2, metaFont, x + 86, top + 64, 0x7B7F92, in: c)
        }
        let x = ox + 260, y = f.y * fy
        if y > -60 * fy && f.alpha > 0 {
            c.saveGState(); c.setAlpha(f.alpha); c.beginTransparencyLayer(auxiliaryInfo: nil)
            let pulse = f.spin / 5 * (0.75 + 0.25 * sin(f.pulse * 4 * .pi))
            radial(c, center: CGPoint(x: x, y: y), radius: 120, start: 10,
                   colors: [Self.color(0x2E6BFF, 0.38 * pulse).cgColor, Self.color(0x2E6BFF, 0).cgColor])
            c.setStrokeColor(Self.color(0xF2F2F2, 0.85).cgColor); c.setLineWidth(1.5)
            c.move(to: CGPoint(x: x, y: -2)); c.addLine(to: CGPoint(x: x, y: y)); c.strokePath()
            if let image {
                for k in 0..<7 {
                    let angle = f.turns * 2 * .pi + f.spin * 2 * .pi / 30 * 0.8 * (Double(k) / 6 - 0.5)
                    c.saveGState(); c.translateBy(x: x, y: y); c.rotate(by: angle)
                    image.draw(in: CGRect(x: -70, y: -70, width: 140, height: 140), blendMode: .normal, alpha: 1 / Double(k + 1)); c.restoreGState()
                }
            }
            c.saveGState(); c.addEllipse(in: CGRect(x: x - 69, y: y - 69, width: 138, height: 138)); c.clip()
            radial(c, center: CGPoint(x: x - 26, y: y - 28), radius: 52, start: 2,
                   colors: [Self.color(0xFFFFFF, 0.28).cgColor, Self.color(0xFFFFFF, 0).cgColor])
            c.restoreGState(); c.endTransparencyLayer(); c.restoreGState()
        }
        if f.finger > 0 {
            c.setFillColor(Self.color(0xFFFFFF, 0.3 * f.finger).cgColor)
            c.fillEllipse(in: CGRect(x: ox + 236, y: 320 * fy + f.d - 24, width: 48, height: 48))
        }
    }
    private func radial(_ c: CGContext, center: CGPoint, radius: Double, start: Double, colors: [CGColor]) {
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: [start / radius, 1]) else { return }
        c.drawRadialGradient(gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius, options: [.drawsBeforeStartLocation])
    }
}

private final class YoyoPullRefreshDisplayTarget: NSObject {
    weak var view: YoyoPullRefreshInboxView?
    init(_ view: YoyoPullRefreshInboxView) { self.view = view }
    @objc func tick(_ link: CADisplayLink) { view?.tick(link) }
}
