import UIKit

@MainActor
final class CassetteDeckSurface: UIView {
    private var session: CassetteDeckSession
    private var renderer: CassetteDeckRenderer?
    private var renderedLayout: CassetteDeckLayout?
    private var configuredAutoPlay: Bool
    private var configuredReduceMotion: Bool
    private var previousTimestamp: CFTimeInterval?
    private var displayLink: CADisplayLink?
    private let displayLinkProxy = CassetteDeckDisplayLinkProxy()

    init(autoPlay: Bool, reduceMotion: Bool) {
        configuredAutoPlay = autoPlay
        configuredReduceMotion = reduceMotion
        session = CassetteDeckSession(reduceMotion: reduceMotion, autoPlay: autoPlay)
        super.init(frame: .zero)
        displayLinkProxy.surface = self
        backgroundColor = CassetteDeckTheme.plate
        isOpaque = true
        contentMode = .redraw
        isMultipleTouchEnabled = false
        isAccessibilityElement = true
        accessibilityTraits = .allowsDirectInteraction
        accessibilityCustomActions = CassetteDeckTheme.keyNames.enumerated().map { index, name in
            UIAccessibilityCustomAction(name: name, target: self, selector: [#selector(rewindAction), #selector(playAction), #selector(fastForwardAction), #selector(stopAction), #selector(pauseAction)][index])
        }
        refreshAccessibilityLabel()
    }

    required init?(coder: NSCoder) { nil }

    func configure(autoPlay: Bool, reduceMotion: Bool) {
        guard autoPlay != configuredAutoPlay || reduceMotion != configuredReduceMotion else { return }
        configuredAutoPlay = autoPlay
        configuredReduceMotion = reduceMotion
        session = CassetteDeckSession(reduceMotion: reduceMotion, autoPlay: autoPlay)
        previousTimestamp = nil
        refreshAccessibilityLabel()
        setNeedsDisplay()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil { startDisplayLink() } else { stopDisplayLink() }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let next = CassetteDeckLayout(size: bounds.size, safeArea: safeAreaInsets)
        guard next != renderedLayout else { return }
        renderedLayout = next
        renderer = CassetteDeckRenderer(layout: next)
        setNeedsDisplay()
    }

    override func safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        renderedLayout = nil
        setNeedsLayout()
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext(), let renderer else { return }
        renderer.draw(in: context, model: session.model, bounds: bounds)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let point = touches.first?.location(in: self), let layout = renderedLayout else { return }
        let stage = layout.stagePoint(point)
        session.touchStart(x: stage.x, y: stage.y)
        playPendingHaptics()
        refreshAccessibilityLabel()
        setNeedsDisplay()
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { endTouch() }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { endTouch() }

    func displayLinkDidFire(_ link: CADisplayLink) {
        let previous = previousTimestamp ?? link.timestamp
        previousTimestamp = link.timestamp
        if session.advance(link.timestamp - previous) {
            playPendingHaptics()
            refreshAccessibilityLabel()
            setNeedsDisplay()
        }
    }

    @objc private func rewindAction() -> Bool { performAccessibilityAction(0) }
    @objc private func playAction() -> Bool { performAccessibilityAction(1) }
    @objc private func fastForwardAction() -> Bool { performAccessibilityAction(2) }
    @objc private func stopAction() -> Bool { performAccessibilityAction(3) }
    @objc private func pauseAction() -> Bool { performAccessibilityAction(4) }

    private func performAccessibilityAction(_ index: Int) -> Bool {
        session.accessibilityPress(index)
        playPendingHaptics()
        refreshAccessibilityLabel()
        setNeedsDisplay()
        return true
    }

    private func endTouch() {
        session.touchEnd()
        refreshAccessibilityLabel()
        setNeedsDisplay()
    }

    private func startDisplayLink() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: displayLinkProxy, selector: #selector(CassetteDeckDisplayLinkProxy.tick(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    private func stopDisplayLink() {
        displayLink?.invalidate()
        displayLink = nil
        previousTimestamp = nil
    }

    private func playPendingHaptics() {
        for event in session.model.events {
            switch event {
            case .latch: UIImpactFeedbackGenerator(style: .medium).impactOccurred(intensity: 0.7)
            case .key, .memory: UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.65)
            }
        }
        session.model.events.removeAll(keepingCapacity: true)
    }

    private func refreshAccessibilityLabel() {
        accessibilityLabel = "Glass Hours, side A. \(session.model.mode.accessibilityLabel)."
        accessibilityValue = session.model.memory ? "Memory rewind enabled" : nil
    }
}

@MainActor
private final class CassetteDeckDisplayLinkProxy: NSObject {
    weak var surface: CassetteDeckSurface?
    @objc func tick(_ link: CADisplayLink) { surface?.displayLinkDidFire(link) }
}
