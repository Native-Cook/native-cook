import QuartzCore
import SwiftUI
import UIKit

struct GiftSlotDropTouchView: UIViewRepresentable {
    let model: GiftSlotDropModel

    func makeUIView(context: Context) -> GiftSlotDropTouchSurface { GiftSlotDropTouchSurface(model: model) }
    func updateUIView(_ view: GiftSlotDropTouchSurface, context: Context) {
        view.model = model
        view.updateAccessibility()
    }
    static func dismantleUIView(_ view: GiftSlotDropTouchSurface, coordinator: Void) { view.stopClock() }
}

@MainActor
final class GiftSlotDropTouchSurface: UIView, UIGestureRecognizerDelegate {
    var model: GiftSlotDropModel
    private var previousPoint = CGPoint.zero
    private var previousTime = 0.0
    private var velocity = CGVector.zero
    private var displayLink: CADisplayLink?
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let success = UINotificationFeedbackGenerator()

    init(model: GiftSlotDropModel) {
        self.model = model
        super.init(frame: .zero)
        backgroundColor = .clear
        isOpaque = false
        isAccessibilityElement = true
        accessibilityTraits = [.adjustable, .button]
        accessibilityHint = "Swipe up or down to change gift. Double tap to activate it."
        let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.numberOfTouchesRequired = 1
        gesture.cancelsTouchesInView = false
        gesture.delaysTouchesBegan = false
        gesture.delegate = self
        addGestureRecognizer(gesture)
        selection.prepare(); light.prepare(); medium.prepare(); success.prepare()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { stopClock() } else { startClock() }
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool { model.isChoosing }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    override func accessibilityActivate() -> Bool {
        model.takeOver(); model.activate(); model.letGo()
        displayLink?.isPaused = false
        return true
    }
    override func accessibilityIncrement() { adjust(1) }
    override func accessibilityDecrement() { adjust(-1) }

    func updateAccessibility() {
        let gift = model.gift
        accessibilityLabel = "\(gift.shop), \(gift.kind): \(gift.offer) \(gift.line), \(gift.cap). Gift \(model.selected + 1) of \(GiftSlotDropTokens.gifts.count)"
        accessibilityElementsHidden = !model.isChoosing
        let layout = model.screenLayout
        accessibilityFrame = convert(CGRect(
            x: layout.cx - GiftSlotDropTokens.Layout.cardW / 2,
            y: layout.cardY - GiftSlotDropTokens.Layout.cardH / 2,
            width: GiftSlotDropTokens.Layout.cardW,
            height: GiftSlotDropTokens.Layout.cardH
        ), to: nil)
    }

    func stopClock() { displayLink?.invalidate(); displayLink = nil }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            displayLink?.isPaused = false
            model.takeOver()
            previousPoint = point
            previousTime = now
            velocity = .zero
            model.touchBegan(at: point)
        case .changed:
            let elapsed = max(1.0 / 240.0, now - previousTime)
            velocity = CGVector(dx: (point.x - previousPoint.x) / elapsed, dy: (point.y - previousPoint.y) / elapsed)
            previousPoint = point
            previousTime = now
            model.touchMoved(to: point)
        case .ended:
            if now - previousTime > 0.1 { velocity = .zero }
            model.touchMoved(to: point)
            model.touchEnded(velocity: velocity)
            model.letGo()
        case .cancelled, .failed:
            model.touchEnded(velocity: .zero, cancelled: true)
            model.letGo()
        default: break
        }
    }

    private func adjust(_ direction: Int) {
        model.takeOver(); model.step(direction); model.letGo()
        displayLink?.isPaused = false
    }

    private func startClock() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(frame(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
        link.add(to: .main, forMode: .common)
        link.isPaused = !model.needsFrames
        displayLink = link
    }

    @objc private func frame(_ link: CADisplayLink) {
        model.advance(to: link.targetTimestamp)
        for event in model.drainHaptics() {
            switch event {
            case .select: selection.selectionChanged(); selection.prepare()
            case .armed, .light: light.impactOccurred(); light.prepare()
            case .insert: medium.impactOccurred(); medium.prepare()
            case .success: success.notificationOccurred(.success); success.prepare()
            }
        }
        updateAccessibility()
        link.isPaused = !model.needsFrames
    }
}
