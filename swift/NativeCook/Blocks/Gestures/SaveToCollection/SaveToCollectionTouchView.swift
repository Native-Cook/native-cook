import SwiftUI
import UIKit

struct SaveToCollectionTouchView: UIViewRepresentable {
    let model: SaveToCollectionModel

    func makeUIView(context: Context) -> SaveToCollectionTouchSurface {
        let view = SaveToCollectionTouchSurface()
        view.model = model
        return view
    }

    func updateUIView(_ uiView: SaveToCollectionTouchSurface, context: Context) {
        uiView.model = model
        uiView.setClockActive(model.needsFrames)
    }
}

@MainActor
final class SaveToCollectionTouchSurface: UIView {
    weak var model: SaveToCollectionModel?

    private var displayLink: CADisplayLink?
    private var displayTarget: SaveToCollectionDisplayTarget?
    private var holdTimer: Timer?
    private var touchStart = CGPoint.zero
    private var currentPoint = CGPoint.zero
    private var lastPoint = CGPoint.zero
    private var lastPointTime = 0.0
    private var velocity = CGPoint.zero
    private var dragging = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isMultipleTouchEnabled = false
        isAccessibilityElement = false
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            stopDisplayLink()
        } else {
            startDisplayLink()
        }
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        let point = touch.location(in: self)
        touchStart = point
        currentPoint = point
        lastPoint = point
        lastPointTime = touch.timestamp
        velocity = .zero
        dragging = false
        model?.touchBegan(at: point)
        setClockActive(true)
        holdTimer?.invalidate()
        holdTimer = Timer.scheduledTimer(withTimeInterval: SaveToCollectionTokens.Save.hold, repeats: false) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self else { return }
                self.dragging = self.model?.beginDrag(at: self.currentPoint) == true
            }
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        let point = touch.location(in: self)
        let delta = max(
            touch.timestamp - lastPointTime,
            SaveToCollectionTokens.Clock.minimumVelocityDelta
        )
        velocity = CGPoint(x: (point.x - lastPoint.x) / delta, y: (point.y - lastPoint.y) / delta)
        currentPoint = point
        lastPoint = point
        lastPointTime = touch.timestamp
        if dragging {
            model?.moveDrag(
                translation: CGPoint(x: point.x - touchStart.x, y: point.y - touchStart.y),
                velocity: velocity
            )
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        finishTouch(at: touches.first?.location(in: self) ?? currentPoint, cancelled: false)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        finishTouch(at: currentPoint, cancelled: true)
    }

    private func finishTouch(at point: CGPoint, cancelled: Bool) {
        holdTimer?.invalidate()
        holdTimer = nil
        let wasDrag = dragging
        if dragging {
            model?.moveDrag(
                translation: CGPoint(x: point.x - touchStart.x, y: point.y - touchStart.y),
                velocity: velocity
            )
            model?.endDrag()
        }
        dragging = false
        model?.touchEnded(at: point, wasDrag: wasDrag || cancelled)
    }

    private func startDisplayLink() {
        guard displayLink == nil else { return }
        let target = SaveToCollectionDisplayTarget { [weak self] displayLink in
            guard let self else { return }
            self.model?.advance(to: displayLink.targetTimestamp)
            displayLink.isPaused = !(self.model?.needsFrames ?? false)
        }
        displayTarget = target
        let displayLink = CADisplayLink(target: target, selector: #selector(SaveToCollectionDisplayTarget.frame(_:)))
        displayLink.add(to: .main, forMode: .common)
        self.displayLink = displayLink
    }

    func setClockActive(_ active: Bool) {
        displayLink?.isPaused = !active
    }

    private func stopDisplayLink() {
        displayLink?.invalidate()
        displayLink = nil
        displayTarget = nil
        holdTimer?.invalidate()
        holdTimer = nil
    }

}

@MainActor
private final class SaveToCollectionDisplayTarget: NSObject {
    private let action: (CADisplayLink) -> Void

    init(action: @escaping (CADisplayLink) -> Void) {
        self.action = action
    }

    @objc func frame(_ displayLink: CADisplayLink) {
        action(displayLink)
    }
}
