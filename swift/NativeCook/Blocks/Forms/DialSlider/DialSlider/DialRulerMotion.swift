import Foundation
import SwiftUI

/// Frame-driven port of `useDialRulerMotion`. Owns the ruler translation and
/// the derived per-tick animation state that Reanimated computed in worklets.
final class DialRulerMotion: ObservableObject {
    private enum ActiveAnimation {
        case none
        case decay(DecayState)
        case spring(SpringState)
        case timing(TimingState)
    }

    @Published private(set) var translationX: Double
    /// Published every frame so the ruler canvas redraws time-based effects.
    @Published private(set) var frameTime: TimeInterval

    private(set) var startX: Double
    private(set) var minValue: Double
    private(set) var maxValue: Double
    private(set) var ticks: [Double] = []

    /// Latest parent state, refreshed by the view on every render.
    var currentRevision = 0
    var parentValue: Double = 0
    var reduceMotion = false
    var onValueChange: ((Double, Int) -> Void)?

    private var minTranslation: Double
    private var maxTranslation: Double
    private var isGestureActive = false
    private var gestureRevision = 0
    private var isProgrammaticMove = false
    private var animation: ActiveAnimation = .none
    private var clock: FrameClock!
    private var lastActivity: TimeInterval
    private var velocityTracker = VelocityTracker()

    // isDragging shared-value emulation: 1 while moving, then a 100ms hold and
    // a 200ms fade so the wave settles after the finger stops.
    private var dragHoldUntil: TimeInterval = -1

    // Per-tick animation state (height when nearest, color flash on crossing).
    private(set) var nearestTick: Double?
    private(set) var previousNearestTick: Double?
    private(set) var nearestChangedAt: TimeInterval = -1
    private(set) var crossedAt: [Double: TimeInterval] = [:]
    private var lastRoundedValue: Double

    private static let tickSpacing = DialConfig.tickSpacing

    init(initialValue: Double, minValue: Double, maxValue: Double) {
        self.minValue = minValue
        self.maxValue = maxValue
        let bounds = getDialTranslationBounds(
            minValue: minValue,
            maxValue: maxValue,
            tickSpacing: Self.tickSpacing
        )
        minTranslation = bounds.minTranslation
        maxTranslation = bounds.maxTranslation
        let initialTranslation = dialValueToTranslation(
            initialValue,
            minValue: minValue,
            maxValue: maxValue,
            tickSpacing: Self.tickSpacing
        )
        translationX = initialTranslation
        startX = initialTranslation
        lastRoundedValue = jsRound(
            dialTranslationToValue(
                initialTranslation,
                minValue: minValue,
                maxValue: maxValue,
                tickSpacing: Self.tickSpacing
            )
        )
        let now = FrameClock.now
        frameTime = now
        lastActivity = now
        clock = FrameClock { [weak self] time in
            self?.frame(time)
        }
    }

    deinit {
        clock.stop()
    }

    var now: TimeInterval { frameTime }

    var currentValue: Double { -translationX / Self.tickSpacing }

    func updateRange(minValue: Double, maxValue: Double) {
        guard minValue != self.minValue || maxValue != self.maxValue else { return }
        self.minValue = minValue
        self.maxValue = maxValue
        let bounds = getDialTranslationBounds(
            minValue: minValue,
            maxValue: maxValue,
            tickSpacing: Self.tickSpacing
        )
        minTranslation = bounds.minTranslation
        maxTranslation = bounds.maxTranslation
    }

    func updateTicks(_ ticks: [Double]) {
        guard ticks != self.ticks else { return }
        self.ticks = ticks
        nearestTick = getNearestDialTick(currentValue, ticks: ticks)
        previousNearestTick = nil
        nearestChangedAt = -1
        crossedAt.removeAll()
    }

    // MARK: Gesture

    func beginGesture(revision: Int) {
        let time = FrameClock.now
        cancelAnimation()
        isProgrammaticMove = false
        isGestureActive = true
        gestureRevision = revision
        startX = translationX
        velocityTracker.reset()
        velocityTracker.add(position: translationX, at: time)
        wake(at: time)
    }

    func updateGesture(translation: Double) {
        guard isGestureActive else { return }
        let time = FrameClock.now
        let nextX = Swift.max(minTranslation, Swift.min(maxTranslation, startX + translation))
        setTranslation(nextX, at: time)
        velocityTracker.add(position: nextX, at: time)
        dragHoldUntil = time + 0.1
        wake(at: time)
    }

    func endGesture() {
        guard isGestureActive else { return }
        let time = FrameClock.now
        let velocity = velocityTracker.velocity(at: time)
        dragHoldUntil = Swift.min(dragHoldUntil, time)
        isGestureActive = false
        cancelAnimation()
        if reduceMotion {
            finishDecay(at: time)
        } else {
            animation = .decay(
                DecayState(
                    velocity: velocity,
                    lastTime: time,
                    deceleration: DialConfig.flingDeceleration
                )
            )
        }
        wake(at: time)
    }

    func cancelGesture() {
        guard isGestureActive else { return }
        let time = FrameClock.now
        dragHoldUntil = Swift.min(dragHoldUntil, time)
        isGestureActive = false
        wake(at: time)
    }

    // MARK: Programmatic synchronization

    /// Port of the `value`/`revision` reaction: moves the ruler to the parent's
    /// value when the change did not originate from this gesture.
    func syncToValue(_ value: Double, revisionChanged: Bool) {
        guard !isGestureActive else { return }
        let time = FrameClock.now
        if revisionChanged { cancelAnimation() }

        let fromX = jsRound(
            dialTranslationToValue(
                translationX,
                minValue: minValue,
                maxValue: maxValue,
                tickSpacing: Self.tickSpacing
            )
        )
        if fromX == jsRound(value) {
            if revisionChanged { isProgrammaticMove = false }
            return
        }

        let target = dialValueToTranslation(
            jsRound(value),
            minValue: minValue,
            maxValue: maxValue,
            tickSpacing: Self.tickSpacing
        )
        cancelAnimation()
        isProgrammaticMove = true
        if reduceMotion {
            setTranslation(target, at: time)
            isProgrammaticMove = false
            wake(at: time)
            return
        }
        animation = .timing(
            TimingState(
                from: translationX,
                to: target,
                startTime: time,
                duration: 0.12,
                easing: DialEasing.inOutQuad
            )
        )
        wake(at: time)
    }

    // MARK: Derived animation state

    /// 0...1 drag intensity that feeds the tick wave.
    func dragLevel(at time: TimeInterval) -> Double {
        if time < dragHoldUntil { return 1 }
        return Swift.max(0, 1 - (time - dragHoldUntil) / 0.2)
    }

    /// Height contributed by the "nearest tick" timing animation (150ms).
    func nearestTickHeight(for tick: Double, at time: TimeInterval) -> Double {
        let progress = nearestChangedAt < 0
            ? 1
            : Swift.min(1, Swift.max(0, (time - nearestChangedAt) / 0.15))
        let eased = DialEasing.inOutQuad(progress)
        if tick == nearestTick {
            return DialConfig.tickHeight + (DialConfig.centerTickHeight - DialConfig.tickHeight) * eased
        }
        if tick == previousNearestTick {
            return DialConfig.centerTickHeight - (DialConfig.centerTickHeight - DialConfig.tickHeight) * eased
        }
        return DialConfig.tickHeight
    }

    /// 1 right after a tick is crossed, fading to 0 over 300ms.
    func crossingFlash(for tick: Double, at time: TimeInterval) -> Double {
        guard let crossed = crossedAt[tick] else { return 0 }
        let progress = Swift.min(1, Swift.max(0, (time - crossed) / 0.3))
        return 1 - DialEasing.inOutQuad(progress)
    }

    // MARK: Internals

    private func cancelAnimation() {
        animation = .none
    }

    private func wake(at time: TimeInterval) {
        lastActivity = time
        clock.start()
    }

    private func setTranslation(_ x: Double, at time: TimeInterval) {
        translationX = x
        let value = -x / Self.tickSpacing

        let nearest = getNearestDialTick(value, ticks: ticks)
        if nearest != nearestTick {
            previousNearestTick = nearestTick
            nearestTick = nearest
            nearestChangedAt = time
        }

        let rounded = jsRound(
            dialTranslationToValue(
                x,
                minValue: minValue,
                maxValue: maxValue,
                tickSpacing: Self.tickSpacing
            )
        )
        guard rounded != lastRoundedValue else { return }
        let previous = lastRoundedValue
        lastRoundedValue = rounded
        for tick in ticks
        where (previous < tick && rounded >= tick) || (previous > tick && rounded <= tick) {
            crossedAt[tick] = time
        }

        if isProgrammaticMove { return }
        if gestureRevision != currentRevision { return }
        if rounded != jsRound(parentValue) {
            onValueChange?(rounded, currentRevision)
        }
    }

    private func finishDecay(at time: TimeInterval) {
        let target = snapDialTranslation(
            translationX,
            minValue: minValue,
            maxValue: maxValue,
            tickSpacing: Self.tickSpacing
        )
        if reduceMotion {
            setTranslation(target, at: time)
            animation = .none
            return
        }
        animation = .spring(
            SpringState(
                target: target,
                velocity: 0,
                lastTime: time,
                mass: 0.75,
                stiffness: 280,
                damping: 34
            )
        )
    }

    private func frame(_ time: TimeInterval) {
        frameTime = time
        switch animation {
        case .none:
            break
        case .decay(var state):
            let result = state.step(
                position: translationX,
                clamp: minTranslation...maxTranslation,
                now: time
            )
            setTranslation(result.position, at: time)
            if result.finished {
                finishDecay(at: time)
            } else {
                animation = .decay(state)
            }
        case .spring(var state):
            let result = state.step(position: translationX, now: time)
            setTranslation(result.position, at: time)
            animation = result.finished ? .none : .spring(state)
        case .timing(let state):
            let result = state.value(at: time)
            setTranslation(result.position, at: time)
            if result.finished {
                animation = .none
                isProgrammaticMove = false
            }
        }

        if case .none = animation, !isGestureActive, time - lastActivity > 0.6 {
            clock.stop()
        } else if case .none = animation, !isGestureActive {
            // Still fading tick/drag effects; keep rendering briefly.
        } else {
            lastActivity = time
        }
    }
}
