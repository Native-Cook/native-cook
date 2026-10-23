import QuartzCore
import UIKit

final class ChromePrizeWheelStage: UIView, UIGestureRecognizerDelegate {
    typealias Motion = ChromePrizeWheelMotion
    typealias Geometry = ChromePrizeWheelGeometry
    var scene = Motion.initialScene()
    let config: Motion.Config
    let assets = ChromePrizeWheelAssets()
    var layout = Geometry.wheelLayout(402, 874, 62, 34)
    var clock = 0.0
    private let background = ChromePrizeWheelDrawingView()
    private let foreground = ChromePrizeWheelDrawingView()
    private var renderer: ChromePrizeWheelRenderer!
    private var rays: ChromePrizeWheelRenderer!
    private var displayLink: CADisplayLink?
    private var lastTime: Double?
    private var lastPhase = -1.0
    private let spinButton = UIButton(type: .custom)
    private let collectButton = UIButton(type: .custom)
    private let heading = UILabel()
    private let subtitle = UILabel()
    private let reward = UILabel()
    private var touchMode = "wheel"
    private var origin = CGPoint.zero
    private var previous = CGPoint.zero
    private var velocity = CGPoint.zero
    private var previousTime = 0.0
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let success = UINotificationFeedbackGenerator()

    init(autoPlay: Bool, reduced: Bool) {
        config = Motion.Config(reduced: reduced, autoPlay: autoPlay)
        super.init(frame: .zero)
        backgroundColor = UIColor(red: 3 / 255, green: 3 / 255, blue: 14 / 255, alpha: 1)
        clipsToBounds = true
        renderer = ChromePrizeWheelRenderer(assets: assets)
        addSubview(background)
        addSubview(renderer.view)
        rays = ChromePrizeWheelRenderer(assets: assets, fragment: "chromePrizeWheelRays")
        addSubview(rays.view)
        addSubview(foreground)
        background.render = { [weak self] context in self?.drawBackground(context) }
        foreground.render = { [weak self] context in self?.drawForeground(context) }
        // Native accessibility elements follow the canvas's measured control frames.
        for button in [spinButton, collectButton] {
            button.backgroundColor = .clear
            button.isAccessibilityElement = true
            addSubview(button)
        }
        spinButton.accessibilityLabel = "Prize wheel"
        spinButton.accessibilityHint = "Swipe to spin. Double tap to spin."
        spinButton.addTarget(self, action: #selector(accessibleSpin), for: .touchUpInside)
        collectButton.accessibilityLabel = "Collect"
        collectButton.addTarget(self, action: #selector(pressIn), for: .touchDown)
        collectButton.addTarget(self, action: #selector(pressOut), for: [.touchUpOutside, .touchCancel])
        collectButton.addTarget(self, action: #selector(collect), for: .touchUpInside)
        for label in [heading, subtitle, reward] {
            label.textColor = .clear
            label.isAccessibilityElement = true
            addSubview(label)
        }
        heading.text = "Daily Spin"
        heading.accessibilityTraits = .header
        let drag = UILongPressGestureRecognizer(target: self, action: #selector(handleDrag(_:)))
        drag.minimumPressDuration = 0
        drag.allowableMovement = .greatestFiniteMagnitude
        drag.cancelsTouchesInView = false
        drag.delegate = self
        addGestureRecognizer(drag)
        accessibilityElements = [heading, subtitle, spinButton, reward, collectButton]
        NotificationCenter.default.addObserver(
            self, selector: #selector(suspendClock),
            name: UIApplication.willResignActiveNotification, object: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            stop()
        } else if displayLink == nil {
            let link = CADisplayLink(target: self, selector: #selector(frame(_:)))
            link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
            link.add(to: .main, forMode: .common)
            displayLink = link
        }
    }

    @objc private func suspendClock() { lastTime = nil }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        lastTime = nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0 else { return }
        let safe = window?.safeAreaInsets ?? safeAreaInsets
        layout = Geometry.wheelLayout(bounds.width, bounds.height, safe.top, safe.bottom)
        config.cx = layout.cx
        config.cy = layout.cy
        config.radius = layout.radius
        config.buttonX = layout.buttonX
        config.buttonY = layout.buttonY
        background.frame = bounds
        foreground.frame = bounds
        rays.view.frame = bounds
        renderer.view.transform = .identity
        renderer.view.frame = CGRect(
            x: layout.cx - layout.radius - 1, y: layout.cy - layout.radius - 1,
            width: layout.radius * 2 + 2, height: layout.radius * 2 + 2)
        heading.frame = CGRect(x: 24, y: layout.titleTop, width: bounds.width - 48, height: layout.titleLine)
        subtitle.frame = CGRect(x: 24, y: layout.subtitleTop, width: bounds.width - 48, height: layout.bodyLine)
        reward.frame = CGRect(
            x: 24, y: layout.captionTop, width: bounds.width - 48, height: layout.numeralLine + layout.bodyLine + 4)
        spinButton.frame = CGRect(
            x: layout.cx - layout.radius, y: layout.cy - layout.radius,
            width: layout.radius * 2, height: layout.radius * 2)
        collectButton.frame = CGRect(x: 24, y: layout.buttonTop, width: layout.buttonWidth, height: layout.buttonHeight)
        updateFrame()
    }

    @objc private func frame(_ link: CADisplayLink) {
        guard bounds.width > 0 else { return }
        if let lastTime {
            let elapsed = link.timestamp - lastTime
            // Preserve every fixed step during rendering hitches; only backgrounding pauses the clock.
            if UIApplication.shared.applicationState == .active {
                Motion.advanceScene(scene, elapsed, config)
                if !config.reduced { clock += elapsed }
                fireHaptics()
            }
        }
        lastTime = link.timestamp
        updateFrame()
    }

    func dispatch(_ action: Motion.Action) {
        Motion.actWheel(scene, action, config)
        fireHaptics()
        updateFrame()
    }

    private func fireHaptics() {
        let events = Int(scene.events)
        if events & 8 != 0 {
            success.notificationOccurred(.success)
        } else if events & 4 != 0 {
            medium.impactOccurred()
        } else if events & 2 != 0 {
            rigid.impactOccurred()
        } else if events & 1 != 0 {
            light.impactOccurred()
        }
        scene.events = 0
    }

    private func updateFrame() {
        let v = scene.values
        let rotation = v.theta.truncatingRemainder(dividingBy: .pi * 2)
        let center = layout.radius + 1
        renderer.uniforms = [
            center, center, layout.radius, Double(contentScaleFactor), rotation,
            Motion.clamp(v.omega / 72, -0.3, 0.3), v.tiltX + 0.008 * sin(rotation),
            v.tiltY + 0.008 * cos(rotation), v.envTurn, v.swayA, v.swayB, v.winner,
            v.lift, v.glow, Geometry.printedPrizeOpacity(v.phase, v.heroSquash, v.winnerPrize),
            v.winnerPrizeScale, v.dim, v.comet, v.hubPress, center * 2,
        ].map(Float.init)
        renderer.view.transform = CGAffineTransform(scaleX: v.wheelScale, y: v.wheelScale)
        renderer.view.alpha = v.wheelOpacity
        renderer.view.draw()
        rays.uniforms = [
            layout.heroX, layout.heroY, layout.radius, v.rays, config.reduced ? 0 : clock, layout.width, layout.height,
        ].map(Float.init)
        rays.view.draw()
        background.setNeedsDisplay()
        foreground.setNeedsDisplay()
        let ready = v.phase == Motion.PHASE.reveal && v.hero > 0.9 && v.button > 0.9
        collectButton.isHidden = !ready
        spinButton.isHidden = v.phase == Motion.PHASE.reveal || v.phase == Motion.PHASE.collecting
        // The drag recognizer owns physical wheel touches; this button supplies VoiceOver activation.
        spinButton.isUserInteractionEnabled = UIAccessibility.isVoiceOverRunning
        spinButton.accessibilityValue = v.phase == Motion.PHASE.spinning ? "Spinning" : "Ready"
        let won = v.phase == Motion.PHASE.reveal || v.phase == Motion.PHASE.collecting
        subtitle.text =
            won
            ? (v.winner == 1 ? "Jackpot. The whole chest is yours." : "Nice spin. This one is yours.")
            : "Swipe the wheel. Every slice wins."
        reward.isHidden = !won
        if v.winner >= 0 {
            let prize = Motion.PRIZES[Int(v.winner)]
            reward.text = "You won \(Int(prize.count)). \(prize.label)."
            if lastPhase != v.phase && v.phase == Motion.PHASE.reveal {
                UIAccessibility.post(notification: .announcement, argument: reward.text)
            }
        }
        lastPhase = v.phase
    }

    @objc private func accessibleSpin() { dispatch(Motion.Action(type: "hub", random: .random(in: 0..<1))) }
    @objc private func pressIn() { dispatch(Motion.Action(type: "pressIn")) }
    @objc private func pressOut() { dispatch(Motion.Action(type: "pressOut")) }
    @objc private func collect() { dispatch(Motion.Action(type: "collect")) }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool { true }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        touch.view !== collectButton && touch.view !== spinButton
    }

    @objc private func handleDrag(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            origin = point
            previous = point
            previousTime = now
            velocity = .zero
            let v = scene.values
            let heroRect = CGRect(
                x: layout.heroX + v.heroX - layout.heroSize / 2,
                y: layout.heroY + v.heroY - layout.heroSize / 2,
                width: layout.heroSize, height: layout.heroSize)
            touchMode = v.phase == Motion.PHASE.reveal && v.hero > 0.9 && heroRect.contains(point) ? "hero" : "wheel"
            if touchMode == "hero" {
                dispatch(Motion.Action(type: "heroDown"))
            } else {
                dispatch(Motion.Action(type: "down", x: point.x - layout.cx, y: point.y - layout.cy))
            }
        case .changed:
            let dt = max(0.001, now - previousTime)
            velocity = CGPoint(x: (point.x - previous.x) / dt, y: (point.y - previous.y) / dt)
            previous = point
            previousTime = now
            if touchMode == "hero" {
                dispatch(Motion.Action(type: "heroMove", x: point.x - origin.x, y: point.y - origin.y, vx: velocity.x))
            } else {
                dispatch(Motion.Action(type: "move", x: point.x - layout.cx, y: point.y - layout.cy))
            }
        case .ended, .cancelled, .failed:
            if now - previousTime > 0.1 { velocity = .zero }
            if touchMode == "hero" {
                dispatch(Motion.Action(type: "heroRelease"))
            } else {
                dispatch(
                    Motion.Action(
                        type: "release", x: point.x - layout.cx, y: point.y - layout.cy,
                        vx: velocity.x, vy: velocity.y, cancelled: recognizer.state != .ended,
                        random: .random(in: 0..<1)))
            }
        default: break
        }
    }
}
