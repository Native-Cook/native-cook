import SwiftUI
import UIKit

/// SwiftUI controls surround a native Core Graphics drawing surface.
/// CADisplayLink advances one shared clock for all 96 particles and four icons.
struct AnimationSurface: UIViewRepresentable {
    let replayID: Int
    let speed: Double
    let isPlaying: Bool

    func makeUIView(context: Context) -> SignInCanvasView { SignInCanvasView() }
    func updateUIView(_ view: SignInCanvasView, context: Context) {
        view.configure(replayID: replayID, speed: speed, isPlaying: isPlaying)
    }
    static func dismantleUIView(_ view: SignInCanvasView, coordinator: ()) { view.stop() }
}

final class SignInCanvasView: UIView {
    private var displayLink: CADisplayLink?
    private var lastTimestamp: CFTimeInterval?
    private var replayID = 0
    private var speed = 1.0
    private var isPlaying = true
    private var playhead = 0.0
    private let avatar = UIImage(named: "particle-sign-in-avatar")
    private let artwork = Dictionary(uniqueKeysWithValues: SignInMotion.iconNames.compactMap { name in
        UIImage(named: name).map { (name, $0) }
    })
    private let fixedTime: Double?
    private lazy var displayTarget = DisplayTarget(view: self)

    override init(frame: CGRect) {
        let arguments = ProcessInfo.processInfo.arguments
        if let index = arguments.firstIndex(of: "--snapshot-time-ms"), arguments.count > index + 1 {
            fixedTime = Double(arguments[index + 1])
        } else {
            fixedTime = nil
        }
        super.init(frame: frame)
        backgroundColor = .white
        isOpaque = true
        isUserInteractionEnabled = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError("Use init(frame:)") }

    func configure(replayID: Int, speed: Double, isPlaying: Bool) {
        if self.replayID != replayID {
            self.replayID = replayID
            playhead = 0
            lastTimestamp = nil
            setNeedsDisplay()
        }
        self.speed = min(4, max(0.25, speed.isFinite ? speed : 1))
        self.isPlaying = isPlaying
        updateDisplayLink()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        updateDisplayLink()
    }

    private func updateDisplayLink() {
        if window == nil || !isPlaying || fixedTime != nil {
            stop()
            if fixedTime != nil { setNeedsDisplay() }
            return
        }
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: displayTarget, selector: #selector(DisplayTarget.tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        lastTimestamp = nil
    }

    fileprivate func advance(_ link: CADisplayLink) {
        let delta = lastTimestamp.map { (link.timestamp - $0) * 1000 } ?? (1000 / 60)
        lastTimestamp = link.timestamp
        playhead += min(32, max(0, delta)) * speed
        setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        UIColor.white.setFill()
        context.fill(bounds)
        let size = SignInMotion.stageSize
        // App.tsx centers a 441-point stack, translates it down by 64 points,
        // then offsets the 331-point stage by (-0.0044, 0.0276) × size.
        let center = CGPoint(x: bounds.midX - size * 0.0044,
                             y: bounds.midY + 64 - (size + 48 + 62) / 2 + size / 2 + size * 0.0276)
        let frame = SignInMotion.frame(at: fixedTime ?? playhead, size: size)
        for dot in frame.dots {
            context.setFillColor(UIColor(red: dot.color.r, green: dot.color.g, blue: dot.color.b, alpha: dot.opacity).cgColor)
            context.fillEllipse(in: CGRect(x: center.x + dot.x - dot.radius, y: center.y + dot.y - dot.radius,
                                          width: dot.radius * 2, height: dot.radius * 2))
        }
        drawIdentity(frame.identity, center: center, size: size, in: context)
        for icon in frame.artwork where icon.opacity > 0 {
            guard let image = artwork[icon.name] else { continue }
            context.saveGState()
            context.translateBy(x: center.x + icon.x, y: center.y + icon.y)
            context.scaleBy(x: icon.scale, y: icon.scale)
            context.setAlpha(icon.opacity)
            // Same 90-point circular mask and oversized 112-point artwork as App.tsx.
            context.addEllipse(in: CGRect(x: -45, y: -45, width: 90, height: 90))
            context.clip()
            image.draw(in: CGRect(x: -56, y: -56, width: 112, height: 112))
            context.restoreGState()
        }
        drawText(size: size)
    }

    private func drawIdentity(_ identity: SignInMotion.IdentityFrame, center: CGPoint, size: Double, in context: CGContext) {
        if identity.appleOpacity > 0 {
            context.saveGState()
            context.translateBy(x: center.x, y: center.y)
            context.scaleBy(x: identity.appleScale, y: identity.appleScale)
            drawApple(size: size * 0.28, color: UIColor(red: 6 / 255, green: 105 / 255, blue: 210 / 255, alpha: 1), opacity: identity.appleOpacity * identity.blueAppleOpacity, in: context)
            drawApple(size: size * 0.28, color: .black, opacity: identity.appleOpacity * identity.blackAppleOpacity, in: context)
            context.restoreGState()
        }
        if identity.avatarOpacity > 0, let avatar {
            let diameter = size * 0.47 * identity.avatarScale
            let imageRect = CGRect(x: center.x - diameter / 2, y: center.y - diameter / 2, width: diameter, height: diameter)
            context.saveGState()
            context.setAlpha(identity.avatarOpacity)
            context.addEllipse(in: imageRect)
            context.clip()
            UIColor(red: 242 / 255, green: 242 / 255, blue: 247 / 255, alpha: 1).setFill()
            context.fill(imageRect)
            avatar.draw(in: imageRect)
            context.restoreGState()
            context.saveGState()
            context.setAlpha(identity.avatarOpacity)
            context.setStrokeColor(UIColor.white.withAlphaComponent(0.92).cgColor)
            context.setLineWidth(1.5 * identity.avatarScale)
            context.strokeEllipse(in: imageRect.insetBy(dx: 0.75 * identity.avatarScale, dy: 0.75 * identity.avatarScale))
            context.restoreGState()
        }
    }

    private func drawApple(size: Double, color: UIColor, opacity: Double, in context: CGContext) {
        guard opacity > 0, let font = UIFont(name: "FontAwesome", size: size) else { return }
        let glyph = "\u{f179}" as NSString
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let measured = glyph.size(withAttributes: attributes)
        context.saveGState()
        context.setAlpha(opacity)
        glyph.draw(at: CGPoint(x: -measured.width / 2, y: -measured.height / 2), withAttributes: attributes)
        context.restoreGState()
    }

    private func drawText(size: Double) {
        let top = bounds.midY + 64 - (size + 48 + 62) / 2 + size + 48 + size * 0.0141
        drawLine("Ngọc Lê", y: top, height: 25, font: .systemFont(ofSize: 20, weight: .bold), color: UIColor(white: 5 / 255, alpha: 1), kern: -0.35)
        drawLine("Signing in...", y: top + 30, height: 21, font: .systemFont(ofSize: 16, weight: .regular), color: UIColor(red: 142 / 255, green: 142 / 255, blue: 147 / 255, alpha: 1), kern: -0.15)
    }

    private func drawLine(_ text: String, y: Double, height: Double, font: UIFont, color: UIColor, kern: Double) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color, .kern: kern, .paragraphStyle: paragraph]
        let verticalInset = (height - font.lineHeight) / 2
        (text as NSString).draw(in: CGRect(x: bounds.midX - 150, y: y + verticalInset, width: 300, height: height + font.lineHeight), withAttributes: attributes)
    }
}

private final class DisplayTarget: NSObject {
    weak var view: SignInCanvasView?
    init(view: SignInCanvasView) { self.view = view }
    @objc func tick(_ link: CADisplayLink) { view?.advance(link) }
}
