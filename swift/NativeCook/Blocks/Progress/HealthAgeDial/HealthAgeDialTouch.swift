import SwiftUI
import UIKit

struct HealthAgeDialTouch: UIViewRepresentable {
    let playback: HealthAgeDialPlayback
    let dialCenter: CGPoint
    let chipRect: CGRect
    let screenHeight: Double
    let maximumScroll: Double

    func makeUIView(context: Context) -> HealthAgeDialTouchView {
        let view = HealthAgeDialTouchView()
        view.isAccessibilityElement = false
        view.accessibilityElementsHidden = true
        return view
    }

    func updateUIView(_ view: HealthAgeDialTouchView, context: Context) {
        view.playback = playback
        view.dialCenter = dialCenter
        view.chipRect = chipRect
        view.screenHeight = screenHeight
        playback.setMaximumScroll(maximumScroll)
    }
}

final class HealthAgeDialTouchView: UIView, UIGestureRecognizerDelegate {
    var playback: HealthAgeDialPlayback?
    var dialCenter = CGPoint.zero
    var chipRect = CGRect.zero
    var screenHeight = 0.0

    private var target = HealthAgeDialPlayback.GestureTarget.scroll
    private var origin = CGPoint.zero
    private var originScroll = 0.0
    private var previous = CGPoint.zero
    private var previousTime = 0.0
    private var velocity = CGPoint.zero
    private var chipPressed = false
    private var landingTimer: Timer?
    private let selection = UISelectionFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
    }

    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        guard let playback else { return }
        let point = recognizer.location(in: self)
        let now = Date().timeIntervalSinceReferenceDate
        switch recognizer.state {
        case .began:
            landingTimer?.invalidate()
            landingTimer = nil
            chipPressed = false
            origin = point
            previous = point
            previousTime = now
            velocity = .zero
            let frame = playback.frame(at: now)
            originScroll = frame.scroll
            let distance = hypot(point.x - dialCenter.x, point.y - dialCenter.y)
            let dial = HealthAgeDialTheme.Layout.Dial.self
            if chipRect.contains(point), frame.scroll > 1 {
                target = .chip
                chipPressed = true
                playback.chipPress(true, at: now)
            } else if frame.scroll < 4, abs(distance - dial.radius) < dial.stroke / 2 + 14 {
                target = .dial
                selection.prepare()
                playback.beginDial(at: now)
                updateDial(point, at: now)
            } else {
                target = .scroll
                impact.prepare()
                playback.beginScroll(at: now)
            }
        case .changed:
            let delta = max(1 / 240, now - previousTime)
            let weight = 1 - exp(-delta / 0.035)
            velocity.x += (((point.x - previous.x) / delta) - velocity.x) * weight
            velocity.y += (((point.y - previous.y) / delta) - velocity.y) * weight
            previous = point
            previousTime = now
            switch target {
            case .dial:
                updateDial(point, at: now)
            case .scroll:
                let value = originScroll - (point.y - origin.y)
                if playback.updateScroll(value, at: now, screenHeight: screenHeight) { impact.impactOccurred() }
            case .chip:
                let inside = chipRect.insetBy(dx: -12, dy: -12).contains(point)
                if inside != chipPressed {
                    chipPressed = inside
                    playback.chipPress(inside, at: now)
                }
            }
        case .ended, .cancelled, .failed:
            if now - previousTime > 0.1 { velocity = .zero }
            let success = recognizer.state == .ended
            switch target {
            case .dial:
                let frame = playback.frame(at: now)
                let radians = frame.years * HealthAgeDialTheme.Layout.Dial.degreesPerYear * .pi / 180
                let angular = (velocity.x * cos(radians) + velocity.y * sin(radians)) / HealthAgeDialTheme.Layout.Dial.radius
                let yearsPerSecond = angular * 180 / .pi / HealthAgeDialTheme.Layout.Dial.degreesPerYear
                playback.endDial(velocity: yearsPerSecond, at: now)
            case .scroll:
                if let delay = playback.endScroll(velocity: -velocity.y, at: now, screenHeight: screenHeight) {
                    landingTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
                        self?.impact.impactOccurred()
                    }
                }
            case .chip:
                if chipPressed { playback.chipPress(false, at: now) }
                chipPressed = false
                if success, chipRect.insetBy(dx: -12, dy: -12).contains(point) { playback.glideToDial(at: now) }
            }
        default:
            break
        }
    }

    private func updateDial(_ point: CGPoint, at now: Double) {
        guard let playback else { return }
        let angle = atan2(point.x - dialCenter.x, -(point.y - dialCenter.y)) * 180 / .pi
        if playback.updateDial(angle: angle, at: now) { selection.selectionChanged() }
    }
}
