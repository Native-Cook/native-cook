import UIKit

/// UIKit owns gesture samples; SwiftUI's timeline supplies every displayed frame's date.
final class OwlAlarmRulerTouchView: UIView, UIGestureRecognizerDelegate {
    private typealias M = OwlAlarmRulerMotion
    private typealias T = OwlAlarmRulerTheme
    var top = 0.0
    var bottom = 0.0
    var onActivity: ((Bool) -> Void)?
    private var engine: M.Engine?
    private var motionFrame = M.REST_FRAME
    private var active = true
    private var reportedMoving = true
    private var labelTick = 0.0
    private var labelAt = -1.0
    private var lastTick = 0.0
    private var lastHaptic = -1.0
    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()
    private var origin = CGPoint.zero
    private var previousPoint = CGPoint.zero
    private var previousTime = 0.0
    private var velocity = 0.0
    private var target: Target?
    private var rejected = false
    private var backFrom = 0.0
    private var backTo = 0.0
    private var backAt = -1e9
    private var backValue = 0.0
    private var elements: [Element] = []
    private let pmFace = UILabel()
    private let amFace = UILabel()
    #if DEBUG
    /// Freeze a deterministic phase for screenshot comparisons; absent in release builds.
    private let evidenceTime: Double? = {
        let arguments = ProcessInfo.processInfo.arguments
        guard let index = arguments.firstIndex(of: "-owl-alarm-ruler-time"),
              index + 1 < arguments.count else { return nil }
        return Double(arguments[index + 1])
    }()
    #endif

    private enum Target { case ruler, cta, back }

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = true
        clipsToBounds = true
        backgroundColor = UIColor(white: 237 / 255, alpha: 1)
        let hold = UILongPressGestureRecognizer(target: self, action: #selector(touch(_:)))
        hold.minimumPressDuration = 0
        hold.allowableMovement = .greatestFiniteMagnitude
        hold.delegate = self
        addGestureRecognizer(hold)
        for face in [pmFace, amFace] {
            face.isUserInteractionEnabled = false
            face.isAccessibilityElement = false
            addSubview(face)
        }
        makeAccessibility()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func configure(autoPlay: Bool, still: Bool, active: Bool, at time: Double) {
        let now = max(time, engine?.lastClock ?? time)
        if var value = engine {
            if value.autoPlay != autoPlay || value.still != still {
                value = M.setAutoPlay(value, autoPlay, still, now)
            }
            if active && !self.active {
                value.autoStart = now
                value.lastClock = now
                value.dragging = false
                if value.mode == M.AUTO { value.glideAt = -1 }
            }
            engine = value
        } else {
            engine = M.createEngine(now, autoPlay, still)
        }
        self.active = active
        if active { sample(now) }
    }

    private func sample(_ now: Double) {
        guard let current = engine else { return }
        let result = M.step(current, now)
        let e = result.e
        engine = e
        motionFrame = result.f
        #if DEBUG
        if let evidenceTime {
            motionFrame = M.frameAt(evidenceTime)
            labelTick = floor(motionFrame.pos + 0.5)
            updateMeridiem()
            updateAccessibility()
            setNeedsDisplay()
            reportActivity(false)
            return
        }
        #endif
        let tick = floor(motionFrame.pos + 0.5)
        if tick != lastTick {
            if result.haptic && now - lastHaptic >= M.HAPTIC_GAP {
                selection.selectionChanged()
                lastHaptic = now
            }
            lastTick = tick
        }
        if tick != labelTick && now - labelAt >= M.LABEL_GAP {
            labelTick = tick
            labelAt = now
        }
        backValue = backPress(now)
        updateMeridiem()
        let moving = e.mode != M.LIVE || e.dragging || e.glideAt >= 0
            || motionFrame.startle >= 0 || motionFrame.press > 0.001
            || abs(e.fx) > 0.01 || abs(e.fv) > 0.1
            || (e.attention > 0.005 && e.attention < 0.995)
            || abs(motionFrame.flip - e.flipTo) > 0.05 || motionFrame.blink > 0
            || (e.autoPlay && !e.still) || abs(backValue - backTo) > 0.0001
        reportActivity(moving)
        updateAccessibility()
        setNeedsDisplay()
    }

    private func updateMeridiem() {
        OwlAlarmRulerRenderer.updateMeridiem(
            pm: pmFace, am: amFace, width: bounds.width, top: top,
            frame: motionFrame, still: engine?.still ?? false
        )
    }

    private func reportActivity(_ moving: Bool) {
        guard moving != reportedMoving else { return }
        reportedMoving = moving
        // Only cadence transitions invalidate SwiftUI, never the frame samples.
        DispatchQueue.main.async { [weak self] in
            guard let self, self.reportedMoving == moving else { return }
            self.onActivity?(moving)
        }
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        OwlAlarmRulerRenderer.draw(context: context, size: bounds.size, top: top, bottom: bottom,
                                  frame: motionFrame, still: engine?.still ?? false,
                                  label: M.timeLabel(labelTick), backPress: backValue)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateAccessibility()
        updateMeridiem()
        setNeedsDisplay()
    }

    private var rulerRect: CGRect {
        CGRect(x: 0, y: top + T.layout.ruler.touch.top, width: bounds.width, height: T.layout.ruler.touch.h)
    }

    private var ctaRect: CGRect {
        CGRect(x: T.space.screenInset,
               y: bounds.height - bottom - T.space.ctaBottom - T.layout.cta.h,
               width: bounds.width - 2 * T.space.screenInset, height: T.layout.cta.h)
    }

    private var backRect: CGRect {
        let d = T.layout.back.d
        return CGRect(x: T.space.backInset, y: top + T.space.headerTop, width: d, height: d)
            .insetBy(dx: -((T.space.hit - d) / 2 + 2), dy: -((T.space.hit - d) / 2 + 2))
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        let point = touch.location(in: self)
        return rulerRect.contains(point) || ctaRect.contains(point) || backRect.contains(point)
    }

    private var now: Double { Date.now.timeIntervalSinceReferenceDate }

    @objc private func touch(_ recognizer: UILongPressGestureRecognizer) {
        guard var e = engine else { return }
        let time = now
        let point = recognizer.location(in: self)
        switch recognizer.state {
        case .began:
            origin = point
            previousPoint = point
            previousTime = time
            velocity = 0
            rejected = false
            if rulerRect.contains(point) {
                target = .ruler
                e = M.rulerDown(e, time)
                selection.prepare()
            } else if ctaRect.contains(point) {
                target = .cta
                e = M.pressIn(e, time)
                success.prepare()
            } else {
                target = .back
                setBack(true, at: time)
            }
        case .changed:
            if target == .ruler && !rejected {
                let dx = point.x - origin.x
                let dy = point.y - origin.y
                if !e.dragging && abs(dy) > 14 {
                    rejected = true
                    e = M.release(e, time, 0, T.layout.ruler.spacing)
                } else {
                    if !e.dragging && abs(dx) > 6 { e = M.dragStart(e, time) }
                    if time > previousTime {
                        velocity = (point.x - previousPoint.x) / (time - previousTime)
                    }
                    e = M.dragMove(e, time, dx, velocity, T.layout.ruler.spacing)
                }
                previousPoint = point
                previousTime = time
            }
        case .ended, .cancelled, .failed:
            if target == .ruler && !rejected {
                if time - previousTime > 0.1 || recognizer.state != .ended { velocity = 0 }
                e = M.release(e, time, velocity, T.layout.ruler.spacing)
            } else if target == .cta {
                e = M.pressOut(e, time, false)
                if recognizer.state == .ended && ctaRect.contains(point) {
                    commit(&e, at: time)
                }
            } else if target == .back {
                setBack(false, at: time)
            }
            target = nil
        default:
            return
        }
        engine = e
        sample(time)
    }

    private func commit(_ e: inout M.Engine, at time: Double) {
        success.notificationOccurred(.success)
        if !e.still && time - e.startleAt >= M.STARTLE.end { e.startleAt = time }
        e.lastTouch = time
    }

    private func backPress(_ time: Double) -> Double {
        let duration = backTo == 1 ? M.PRESS.in : M.PRESS.out
        return M.lerp(backFrom, backTo, M.easeOut(M.seg(time, backAt, backAt + duration)))
    }

    private func setBack(_ pressed: Bool, at time: Double) {
        backFrom = backPress(time)
        backTo = pressed ? 1 : 0
        backAt = time
    }

    private func adjust(_ direction: Double) {
        guard let e = engine else { return }
        let time = now
        engine = M.stepBy(e, time, direction)
        sample(time)
    }

    private func activateCTA() {
        guard let e = engine else { return }
        let time = now
        var updated = M.pressIn(e, time)
        updated = M.pressOut(updated, time, false)
        commit(&updated, at: time)
        engine = updated
        sample(time)
    }

    private func makeAccessibility() {
        let back = Element(accessibilityContainer: self)
        back.accessibilityLabel = T.copy.a11yBack
        back.accessibilityTraits = .button
        back.activate = { true }
        let progress = Element(accessibilityContainer: self)
        progress.accessibilityLabel = "Setup progress"
        progress.accessibilityValue = "65 of 180"
        let headline = Element(accessibilityContainer: self)
        headline.accessibilityLabel = T.copy.line1 + " " + T.copy.line2
        headline.accessibilityTraits = [.header, .staticText]
        let ruler = Element(accessibilityContainer: self)
        ruler.accessibilityLabel = T.copy.a11yRuler
        ruler.accessibilityHint = T.copy.a11yRulerHint
        ruler.accessibilityTraits = .adjustable
        ruler.adjust = { [weak self] in self?.adjust($0) }
        let caption = Element(accessibilityContainer: self)
        caption.accessibilityLabel = T.copy.caption
        caption.accessibilityTraits = .staticText
        let cta = Element(accessibilityContainer: self)
        cta.accessibilityTraits = .button
        cta.activate = { [weak self] in self?.activateCTA(); return true }
        elements = [back, progress, headline, ruler, caption, cta]
        accessibilityElements = elements
    }

    private func updateAccessibility() {
        guard elements.count == 6 else { return }
        let label = M.timeLabel(labelTick)
        elements[0].accessibilityFrameInContainerSpace = backRect
        elements[1].accessibilityFrameInContainerSpace = CGRect(
            x: (bounds.width - T.layout.track.w) / 2,
            y: top + T.space.headerTop + T.layout.back.d / 2 - T.layout.track.h / 2,
            width: T.layout.track.w, height: T.layout.track.h)
        elements[2].accessibilityFrameInContainerSpace = CGRect(
            x: 0, y: top + T.layout.headline[0].base - T.ASCENT * 30,
            width: bounds.width, height: 70)
        elements[3].accessibilityFrameInContainerSpace = rulerRect
        elements[3].accessibilityValue = label
        elements[4].accessibilityFrameInContainerSpace = CGRect(
            x: 0, y: top + T.layout.caption.base - T.ASCENT * 14.2,
            width: bounds.width, height: 20)
        elements[5].accessibilityFrameInContainerSpace = ctaRect
        elements[5].accessibilityLabel = "\(T.copy.cta) \(label)"
    }

    private final class Element: UIAccessibilityElement {
        var adjust: ((Double) -> Void)?
        var activate: (() -> Bool)?
        override func accessibilityIncrement() { adjust?(1) }
        override func accessibilityDecrement() { adjust?(-1) }
        override func accessibilityActivate() -> Bool { activate?() ?? false }
    }
}
