import UIKit
import MetalKit

final class ChromeZeroNumeralView: UIView, UIGestureRecognizerDelegate {
    let motion = ChromeZeroNumeralMotion()
    var active = true {
        didSet { updateClock() }
    }
    private var displayLink: CADisplayLink?
    private var lastTime: Double?
    private let metalView: MTKView
    private let renderer: ChromeZeroNumeralRenderer?
    private let pill = UIView()
    private let mark = UIImageView(image: UIImage(named: "chrome-zero-mark"))
    private let brand = UILabel()
    private let heading = UILabel()
    private let secondLine = UILabel()
    private let bodyLabel = UILabel()
    private let button = ChromeZeroNumeralButton()
    private let hero = ChromeZeroNumeralAccessibleView()
    private lazy var target = ChromeZeroNumeralDisplayTarget(view: self)
    private var touchStart = CGPoint.zero
    private var lastPoint = CGPoint.zero
    private var lastTouchTime = 0.0
    private var velocityX = 0.0
    private var buttonStart = CGPoint.zero
    private var buttonDownTime = 0.0
    private var buttonCancelled = false

    override init(frame: CGRect) {
        let device = MTLCreateSystemDefaultDevice()
        metalView = MTKView(frame: .zero, device: device)
        if let device {
            do { renderer = try ChromeZeroNumeralRenderer(device: device) }
            catch {
                renderer = nil
                assertionFailure("Chrome Zero renderer: \(error)")
            }
        } else { renderer = nil }
        super.init(frame: frame)
        backgroundColor = UIColor(red: 2 / 255, green: 3 / 255, blue: 15 / 255, alpha: 1)
        clipsToBounds = true
        metalView.colorPixelFormat = .bgra8Unorm
        metalView.isPaused = true
        metalView.enableSetNeedsDisplay = false
        metalView.isUserInteractionEnabled = false
        metalView.delegate = renderer
        addSubview(metalView)
        configureCopy()
        hero.backgroundColor = .clear
        hero.isAccessibilityElement = true
        hero.accessibilityLabel = "Chrome zero percent numeral"
        hero.accessibilityHint = "Drag to turn. Hold to loosen it into beads."
        hero.accessibilityTraits = [.image, .button]
        hero.activate = { [weak self] in self?.motion.activateHero() }
        addSubview(hero)
        let pan = UILongPressGestureRecognizer(target: self, action: #selector(handleHero(_:)))
        pan.minimumPressDuration = 0
        pan.allowableMovement = .greatestFiniteMagnitude
        pan.numberOfTouchesRequired = 1
        pan.delegate = self
        hero.addGestureRecognizer(pan)
        let press = UILongPressGestureRecognizer(target: self, action: #selector(handleButton(_:)))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        button.addGestureRecognizer(press)
        button.activate = { [weak self] in
            self?.motion.pressButton()
            self?.motion.releaseButton(success: true)
        }
    }
    required init?(coder: NSCoder) { fatalError("Use init(frame:)") }

    private func configureCopy() {
        pill.backgroundColor = UIColor.white.withAlphaComponent(0.06)
        pill.layer.borderWidth = 1
        pill.layer.borderColor = colour(0xB9C8FF, 0.22).cgColor
        pill.layer.cornerRadius = 14
        pill.isAccessibilityElement = true
        pill.accessibilityLabel = "nought"
        brand.text = "nought"
        brand.textColor = colour(0xE1E6FF)
        mark.contentMode = .scaleAspectFit
        pill.addSubview(mark)
        pill.addSubview(brand)
        heading.text = "0% deposit fees."
        secondLine.text = "Every time."
        heading.accessibilityTraits = .header
        secondLine.accessibilityTraits = .header
        bodyLabel.text = "Zero to get in. Everything to gain."
        bodyLabel.textColor = UIColor.white.withAlphaComponent(0.86)
        for label in [heading, secondLine] { label.textColor = colour(0xB9C8FF) }
        for label in [heading, secondLine, bodyLabel] {
            label.textAlignment = .center
            label.numberOfLines = 1
            addSubview(label)
        }
        addSubview(pill)
        addSubview(button)
    }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        updateClock()
    }
    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        lastTime = nil
    }
    private func updateClock() {
        guard window != nil && active else {
            stop()
            return
        }
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: target, selector: #selector(ChromeZeroNumeralDisplayTarget.tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        metalView.frame = bounds
        metalView.drawableSize = CGSize(width: bounds.width * displayScale, height: bounds.height * displayScale)
        render()
    }
    func tick(_ link: CADisplayLink) {
        guard renderer != nil, bounds.width > 0 else { return }
        let dt = lastTime.map { link.timestamp - $0 } ?? 0
        lastTime = link.timestamp
        motion.advance(dt)
        if motion.action == .buttonDown && motion.clock - buttonDownTime >= 20 {
            buttonCancelled = true
            motion.releaseButton(success: false)
        }
        render()
    }

    private func render() {
        guard bounds.width > 0, bounds.height > 0 else { return }
        let f = motion.frame
        let width = bounds.width
        let height = bounds.height
        let k = min(width / 402, height / 874)
        let typeScale = max(k, 0.86)
        let offset = (width - 402 * k) / 2
        let visible = motion.reduced ? min(1, motion.clock / 0.3) : 1
        motion.centre = CGPoint(x: offset + 214 * k, y: 286 * k)
        motion.unit = 158 * k
        var values: [Double] = [width, height, displayScale, k, offset, motion.centre.x, motion.centre.y,
            motion.unit, f.yaw, f.pitch, f.scale, f.build, f.drain, f.fuse, f.ripple, f.rippleX,
            f.rippleY, f.sweep, f.percent, f.flash, f.dim, f.drift, f.card, f.cardOpacity,
            f.cardExit, motion.reduced ? 1 : 0, f.glint, visible]
        for i in 0..<3 { values += [f.sparkles[i], f.sparkleRotation[i], f.sparkleOpacity[i], f.twinkle[i]] }
        renderer?.uniforms = values.map(Float.init)
        metalView.draw()

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for row in [pill, heading, secondLine, bodyLabel, button] { row.transform = .identity }
        let pillTop = 538 * k
        let headlineTop = max(grid(584 * k), grid(pillTop + 44))
        let headlineLine = 40 * typeScale
        let bodyTop = max(grid(676 * k), grid(headlineTop + 2 * headlineLine + 12))
        brand.font = .systemFont(ofSize: 13 * typeScale, weight: .medium)
        brand.sizeToFit()
        let pillWidth = 24 + 14 + 6 + brand.bounds.width
        pill.bounds = CGRect(x: 0, y: 0, width: pillWidth, height: 28)
        pill.center = CGPoint(x: width / 2, y: pillTop + 14)
        mark.frame = CGRect(x: 12, y: 7, width: 14, height: 14)
        brand.frame = CGRect(x: 32, y: 0, width: brand.bounds.width, height: 28)
        for label in [heading, secondLine] {
            label.font = .systemFont(ofSize: 34 * typeScale, weight: .semibold)
            label.attributedText = NSAttributedString(string: label.text ?? "", attributes: [.kern: -0.6])
        }
        heading.frame = CGRect(x: 0, y: headlineTop, width: width, height: headlineLine)
        secondLine.frame = CGRect(x: 0, y: headlineTop + headlineLine, width: width, height: headlineLine)
        bodyLabel.font = .systemFont(ofSize: 17 * typeScale)
        bodyLabel.frame = CGRect(x: 8, y: bodyTop, width: width - 16, height: 24 * typeScale)
        let buttonHeight = height < 700 ? 48.0 : 56.0
        let bottom = safeAreaInsets.bottom
        button.transform = .identity
        button.frame = CGRect(x: 24, y: height - bottom - 24 - buttonHeight, width: width - 48, height: buttonHeight)
        button.configure(frame: f, typeScale: typeScale, reduced: motion.reduced)
        let rows: [UIView] = [pill, heading, secondLine, bodyLabel, button]
        for (i, row) in rows.enumerated() {
            row.alpha = f.copy[i] * visible
            let dy = motion.reduced ? 0 : f.copyY[i]
            row.transform = CGAffineTransform(translationX: 0, y: dy)
            if i == 4 { row.transform = row.transform.scaledBy(x: 1 - 0.03 * f.buttonPress, y: 1 - 0.03 * f.buttonPress) }
            row.accessibilityElementsHidden = row.alpha < 0.05
        }
        hero.frame = CGRect(x: 0, y: 0, width: width, height: 536 * k)
        hero.accessibilityValue = f.fuse < 0.65 ? "Chrome beads" : "Solid chrome"
        CATransaction.commit()
    }
    private var displayScale: Double { Double(window?.screen.scale ?? traitCollection.displayScale) }

    private func grid(_ n: Double) -> Double { (n / 4).rounded() * 4 }
    private func colour(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
                blue: Double(hex & 255) / 255, alpha: alpha)
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handleHero(_ gesture: UILongPressGestureRecognizer) {
        let point = gesture.location(in: self)
        let now = CACurrentMediaTime()
        switch gesture.state {
        case .began:
            touchStart = point
            lastPoint = point
            lastTouchTime = now
            velocityX = 0
            motion.panBegin(point)
        case .changed:
            let dt = now - lastTouchTime
            if dt > 0 { velocityX = (point.x - lastPoint.x) / dt }
            lastPoint = point
            lastTouchTime = now
            motion.panChange(point, translation: CGPoint(x: point.x - touchStart.x, y: point.y - touchStart.y))
        case .ended, .cancelled, .failed:
            if now - lastTouchTime > 0.1 { velocityX = 0 }
            motion.panFinalize(velocityX: velocityX, success: gesture.state == .ended, point: point)
        default: break
        }
    }
    @objc private func handleButton(_ gesture: UILongPressGestureRecognizer) {
        let point = gesture.location(in: button)
        switch gesture.state {
        case .began:
            buttonStart = point
            buttonDownTime = motion.clock
            buttonCancelled = false
            motion.pressButton()
        case .changed:
            if !buttonCancelled && hypot(point.x - buttonStart.x, point.y - buttonStart.y) > 24 {
                buttonCancelled = true
                motion.releaseButton(success: false)
            }
        case .ended, .cancelled, .failed:
            guard !buttonCancelled else { return }
            let success = gesture.state == .ended && button.bounds.contains(point)
                && hypot(point.x - buttonStart.x, point.y - buttonStart.y) <= 24
            motion.releaseButton(success: success)
        default: break
        }
    }
}

private final class ChromeZeroNumeralDisplayTarget: NSObject {
    weak var view: ChromeZeroNumeralView?
    init(view: ChromeZeroNumeralView) { self.view = view }
    @objc func tick(_ link: CADisplayLink) { view?.tick(link) }
}

class ChromeZeroNumeralAccessibleView: UIView {
    var activate: (() -> Void)?
    override func accessibilityActivate() -> Bool {
        activate?()
        return true
    }
}
