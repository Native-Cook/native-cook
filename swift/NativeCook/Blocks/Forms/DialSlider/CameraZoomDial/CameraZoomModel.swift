import SwiftUI

struct CameraZoomProps {
    var minZoom: Double
    var maxZoom: Double
    var step: Double
    var value: Binding<Double>?
    var defaultValue: Double?
    var zoomStops: [CameraZoomStop]?
    var expanded: Binding<Bool>?
    var defaultExpanded: Bool
    var onExpandedChange: ((Bool) -> Void)?
    var onZoomChange: ((Double) -> Void)?
    var onInteractionStart: (() -> Void)?
    var onInteractionEnd: ((Double) -> Void)?
    var formatValue: ((Double) -> String)?
    var disabled: Bool
}

func getDefaultCameraZoomStops(min: Double, max: Double) -> [CameraZoomStop] {
    let preferred = [0.5, 1, 2].filter { $0 >= min && $0 <= max }
    return (preferred.isEmpty ? [min] : preferred).map { CameraZoomStop(value: $0) }
}

/// Port of the `CameraZoomDial` React state plus `useCameraZoomMotion`.
final class CameraZoomModel: ObservableObject {
    private enum ActiveAnimation {
        case none
        case spring(SpringState)
        case timing(TimingState, completion: ((Bool) -> Void)?)
    }

    var props: CameraZoomProps

    @Published var uncontrolledZoom: Double
    @Published var uncontrolledExpanded: Bool
    @Published var displayZoom: Double
    @Published var isInteracting = false
    /// log2(zoom) driving the wheel rotation. Frame-driven like a shared value.
    @Published private(set) var logZoom: Double

    var reduceMotion = false
    var radius: Double = 0

    private var interactionActive = false
    private var dragActive = false
    private var startZoom: Double
    private var lastReportedZoomUI: Double
    private var lastReportedZoomRef: Double
    private var lastDerivedZoom: Double
    private var animation: ActiveAnimation = .none
    private var clock: FrameClock!
    private var collapseWork: DispatchWorkItem?
    private var velocityTracker = VelocityTracker()
    /// Compact stop buttons ignore taps that end a long-press drag.
    private(set) var suppressCompactTapsUntil: TimeInterval = 0

    init(props: CameraZoomProps) {
        self.props = props
        let range = normalizeCameraZoomRange(minZoom: props.minZoom, maxZoom: props.maxZoom)
        let step = getSafeCameraZoomStep(props.step)
        let requestedInitial = props.defaultValue ?? (range.min <= 1 && range.max >= 1 ? 1 : range.min)
        let initialZoom = roundCameraZoom(requestedInitial, step: step, minZoom: range.min, maxZoom: range.max)
        let resolved = roundCameraZoom(
            props.value?.wrappedValue ?? initialZoom,
            step: step,
            minZoom: range.min,
            maxZoom: range.max
        )
        uncontrolledZoom = initialZoom
        uncontrolledExpanded = props.defaultExpanded
        displayZoom = resolved
        logZoom = cameraZoomToLogPosition(resolved)
        startZoom = resolved
        lastReportedZoomUI = resolved
        lastReportedZoomRef = resolved
        lastDerivedZoom = resolved
        clock = FrameClock { [weak self] time in
            self?.frame(time)
        }
    }

    deinit {
        collapseWork?.cancel()
        clock.stop()
    }

    // MARK: Derived configuration

    var range: CameraZoomRange {
        normalizeCameraZoomRange(minZoom: props.minZoom, maxZoom: props.maxZoom)
    }

    var step: Double { getSafeCameraZoomStep(props.step) }

    var zoomStops: [CameraZoomStop] {
        let range = range
        let normalized = normalizeCameraZoomStops(
            props.zoomStops ?? getDefaultCameraZoomStops(min: range.min, max: range.max),
            minZoom: range.min,
            maxZoom: range.max,
            step: step
        )
        if !normalized.isEmpty { return normalized }
        return normalizeCameraZoomStops(
            getDefaultCameraZoomStops(min: range.min, max: range.max),
            minZoom: range.min,
            maxZoom: range.max,
            step: step
        )
    }

    var zoomIsControlled: Bool { props.value != nil }
    var expansionIsControlled: Bool { props.expanded != nil }

    var resolvedZoom: Double {
        let range = range
        return roundCameraZoom(
            props.value?.wrappedValue ?? uncontrolledZoom,
            step: step,
            minZoom: range.min,
            maxZoom: range.max
        )
    }

    var resolvedExpanded: Bool {
        props.expanded?.wrappedValue ?? uncontrolledExpanded
    }

    var enabled: Bool { !props.disabled && radius > 0 }

    func formattedValue(_ zoom: Double) -> String {
        props.formatValue?(zoom) ?? formatCameraZoomValue(zoom, step: step)
    }

    // MARK: Expansion

    /// Called by the view (inside `withAnimation`) to animate the reveal.
    var animateExpansion: ((Bool) -> Void)?

    func requestExpanded(_ nextExpanded: Bool) {
        collapseWork?.cancel()
        collapseWork = nil
        if let controlled = props.expanded {
            if controlled.wrappedValue == nextExpanded {
                // Keep the visual state aligned with the controlled binding.
                animateExpansion?(nextExpanded)
                return
            }
            controlled.wrappedValue = nextExpanded
            props.onExpandedChange?(nextExpanded)
            return
        }
        if uncontrolledExpanded == nextExpanded { return }
        uncontrolledExpanded = nextExpanded
        animateExpansion?(nextExpanded)
        props.onExpandedChange?(nextExpanded)
    }

    // MARK: Zoom reporting

    private func handleZoomChange(_ nextZoom: Double) {
        if let controlled = props.value {
            controlled.wrappedValue = nextZoom
        } else {
            uncontrolledZoom = nextZoom
        }
        props.onZoomChange?(nextZoom)
    }

    private func reportZoom(_ nextZoom: Double) {
        displayZoom = nextZoom
        if lastReportedZoomRef == nextZoom { return }
        lastReportedZoomRef = nextZoom
        handleZoomChange(nextZoom)
    }

    private func beginInteraction() {
        isInteracting = true
        requestExpanded(true)
        props.onInteractionStart?()
    }

    private func finishInteraction(_ finalZoom: Double) {
        displayZoom = finalZoom
        isInteracting = false
        props.onInteractionEnd?(finalZoom)
        collapseWork?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.requestExpanded(false)
            self.collapseWork = nil
        }
        collapseWork = work
        DispatchQueue.main.asyncAfter(
            deadline: .now() + CameraZoomDialGeometry.collapseDelay,
            execute: work
        )
    }

    /// Port of the `zoom` prop effect: realign the wheel when not interacting.
    func syncZoomFromProps() {
        if isInteracting || interactionActive { return }
        let range = range
        let next = roundCameraZoom(resolvedZoom, step: step, minZoom: range.min, maxZoom: range.max)
        lastReportedZoomUI = next
        lastReportedZoomRef = next
        displayZoom = next
        animateLogZoom(to: cameraZoomToLogPosition(next), duration: 0.18, completion: nil)
    }

    // MARK: Gesture

    func gestureBegan() {
        guard enabled, !dragActive else { return }
        let time = FrameClock.now
        dragActive = true
        cancelAnimation()
        startZoom = cameraLogPositionToZoom(logZoom)
        interactionActive = true
        velocityTracker.reset()
        velocityTracker.add(position: 0, at: time)
        suppressCompactTapsUntil = .infinity
        beginInteraction()
        wake()
    }

    func gestureChanged(translationX: Double) {
        guard dragActive else { return }
        let range = range
        let next = cameraZoomFromTranslation(
            startZoom: startZoom,
            translationX: translationX,
            radius: radius,
            minZoom: range.min,
            maxZoom: range.max,
            degreesPerOctave: CameraZoomDialGeometry.degreesPerOctave
        )
        velocityTracker.add(position: translationX, at: FrameClock.now)
        setLogZoom(cameraZoomToLogPosition(next))
    }

    func gestureEnded() {
        guard dragActive else { return }
        dragActive = false
        suppressCompactTapsUntil = FrameClock.now + 0.3
        guard interactionActive else { return }
        let time = FrameClock.now
        let range = range
        let finalZoom = roundCameraZoom(
            cameraLogPositionToZoom(logZoom),
            step: step,
            minZoom: range.min,
            maxZoom: range.max
        )
        let pointsPerOctave = Swift.max(
            1,
            radius * (CameraZoomDialGeometry.degreesPerOctave * Double.pi / 180)
        )
        let velocity = -velocityTracker.velocity(at: time) / pointsPerOctave
        interactionActive = false
        lastReportedZoomUI = finalZoom
        let target = cameraZoomToLogPosition(finalZoom)
        if reduceMotion {
            setLogZoom(target)
            animation = .none
        } else {
            // Reanimated: duration 400ms, dampingRatio 1, overshootClamping.
            animation = .spring(
                SpringState(
                    target: target,
                    velocity: velocity,
                    lastTime: time,
                    mass: 1,
                    stiffness: 272,
                    damping: 33
                )
            )
        }
        wake()
        reportZoom(finalZoom)
        finishInteraction(finalZoom)
    }

    // MARK: Programmatic moves

    func moveToZoom(_ requestedZoom: Double) {
        guard enabled else { return }
        let range = range
        let next = roundCameraZoom(
            clampCameraZoom(requestedZoom, minZoom: range.min, maxZoom: range.max),
            step: step,
            minZoom: range.min,
            maxZoom: range.max
        )
        if next == displayZoom { return }
        cancelAnimation()
        interactionActive = true
        lastReportedZoomUI = displayZoom
        isInteracting = true
        props.onInteractionStart?()
        animateLogZoom(to: cameraZoomToLogPosition(next), duration: 0.18) { [weak self] finished in
            guard let self, finished else { return }
            self.interactionActive = false
            self.lastReportedZoomUI = next
            self.reportZoom(next)
            self.finishInteraction(next)
        }
    }

    func handleCompactStopPress(_ stopValue: Double, selected: Bool) {
        if FrameClock.now < suppressCompactTapsUntil { return }
        if selected {
            requestExpanded(true)
            return
        }
        moveToZoom(stopValue)
    }

    func handleAccessibilityStep(increment: Bool) {
        moveToZoom(displayZoom + (increment ? step : -step))
    }

    // MARK: Internals

    private func setLogZoom(_ position: Double) {
        logZoom = position
        let range = range
        let zoom = roundCameraZoom(
            cameraLogPositionToZoom(position),
            step: step,
            minZoom: range.min,
            maxZoom: range.max
        )
        let previous = lastDerivedZoom
        lastDerivedZoom = zoom
        if interactionActive, zoom != previous, zoom != lastReportedZoomUI {
            lastReportedZoomUI = zoom
            reportZoom(zoom)
        }
    }

    private func animateLogZoom(to target: Double, duration: TimeInterval, completion: ((Bool) -> Void)?) {
        cancelAnimation()
        if reduceMotion {
            setLogZoom(target)
            completion?(true)
            return
        }
        let easing = CubicBezierEasing.easeOut
        animation = .timing(
            TimingState(
                from: logZoom,
                to: target,
                startTime: FrameClock.now,
                duration: duration,
                easing: { easing.value($0) }
            ),
            completion: completion
        )
        wake()
    }

    private func cancelAnimation() {
        if case .timing(_, let completion) = animation {
            completion?(false)
        }
        animation = .none
    }

    private func wake() {
        clock.start()
    }

    private func frame(_ time: TimeInterval) {
        switch animation {
        case .none:
            clock.stop()
        case .spring(var state):
            let result = state.step(position: logZoom, now: time)
            setLogZoom(result.position)
            animation = result.finished ? .none : .spring(state)
        case .timing(let state, let completion):
            let result = state.value(at: time)
            setLogZoom(result.position)
            if result.finished {
                animation = .none
                completion?(true)
            }
        }
    }
}
