import Foundation
import UIKit

/// One clock owns the pager, material debt, presses and button transitions.
final class HoloAgendaCarouselMotion {
    typealias Theme = HoloAgendaCarouselTheme

    struct Tween {
        var from = 1.0
        var to = 1.0
        var start = 0.0
        var duration = 120.0
        var isSpring = false

        func value(_ time: Double, linear: Bool = false) -> Double {
            let progress = Theme.clamp((time - start) / duration)
            if isSpring && progress < 1 {
                let seconds = max(0, time - start) / 1000
                let ring = sqrt(41.0)
                return to + (from - to) * exp(-13 * seconds)
                    * (cos(ring * seconds) + 13 / ring * sin(ring * seconds))
            }
            return from + (to - from) * (linear ? progress : Theme.bezier(progress))
        }

        mutating func set(_ target: Double, at time: Double, duration: Double = 120,
                          spring: Bool = false, linear: Bool = false) {
            from = value(time, linear: linear)
            to = target
            start = time
            self.duration = duration
            isSpring = spring
        }
    }

    struct Frame {
        var position = 0.0
        var velocity = 0.0
        var clock = 0.0
        var centre = 0
        var arrival = 0.0
        var age = 900.0
        var surge = [0.0, 0, 0, 0]
        var squeeze = 1.0
        var pressed = -1
        var cardPress = 1.0
        var buttonScale = 1.0
        var morph = 0.0
        var labelMix = 0.0
        var selected = false
    }

    private(set) var frame = Frame()
    private(set) var elapsed = 0.0
    private(set) var playing = false
    private(set) var held = false
    private(set) var added = 0
    private var lastDate: Date?
    private var timeline = 0.0
    private var previous = 0.0
    private var lastTouch = 0.0
    private var arrivedAt = -900.0
    private var centre = 0.0
    private var owed = [0.0, 0, 0, 0]
    private var demoAdded = false
    private var spring: (from: Double, to: Double, velocity: Double, start: Double)?
    private var press = Tween()
    private var buttonPress = Tween()
    private var morph = Tween(from: 0, to: 0, duration: 320)
    private var labelMix = Tween(from: 0, to: 0, duration: 300)
    private var reduced = false
    private var autoPlay = false
    private var configured = false
    private var startPosition = 0.0
    private var startPoint = CGPoint.zero
    private var lastPoint = CGPoint.zero
    private var lastPointTime = 0.0
    private var dragVelocity = 0.0
    private var dragged = false
    private var touchRegion = 0
    private let selectionHaptic = UISelectionFeedbackGenerator()
    private let impactHaptic = UIImpactFeedbackGenerator(style: .light)

    func tick(_ date: Date, autoPlay: Bool, reduced: Bool) -> Frame {
        if !configured || self.reduced != reduced || self.autoPlay != autoPlay {
            configured = true
            self.autoPlay = autoPlay
            self.reduced = reduced
            playing = autoPlay && !reduced
            if reduced {
                frame.clock = 0
                frame.surge = [0, 0, 0, 0]
                owed = [0, 0, 0, 0]
                frame.position = Theme.rounded(frame.position)
                frame.velocity = 0
                frame.arrival = 0
                frame.age = 900
            }
        }
        let dt = lastDate.map { max(0, date.timeIntervalSince($0) * 1000) } ?? 0
        lastDate = date
        elapsed += dt
        if !reduced { frame.clock = Theme.mod(frame.clock + dt, Theme.loop) }
        if let spring, !playing {
            let seconds = max(0, (elapsed - spring.start) / 1000)
            if reduced {
                frame.position = spring.from + (spring.to - spring.from) * Theme.bezier(seconds / 0.22)
            } else {
                // Unit mass, stiffness 210, damping 26, with the finger's release velocity.
                let ring = sqrt(210.0 - 13.0 * 13.0)
                let offset = spring.from - spring.to
                frame.position = spring.to + exp(-13 * seconds)
                    * (offset * cos(ring * seconds) + (spring.velocity + 13 * offset) / ring * sin(ring * seconds))
            }
            if seconds > (reduced ? 0.22 : 1.2) {
                frame.position = spring.to
                self.spring = nil
            }
        }
        if playing {
            timeline += dt
            let state = Theme.dynamics(timeline)
            frame.position = state.pos
            frame.velocity = state.velocity
            frame.arrival = state.arrival
            frame.age = state.age
            frame.squeeze = state.squeeze
            frame.buttonScale = state.buttonScale
            demoAdded = state.demoAdded
        } else {
            if dt > 0 {
                let derivative = Theme.clamp((frame.position - previous) * 1000 / dt, -8, 8)
                frame.velocity = reduced ? 0 : frame.velocity + (derivative - frame.velocity) * (1 - exp(-dt / 70))
            }
            frame.buttonScale = 1
            if autoPlay && !reduced && !held && elapsed - lastTouch >= 3500 {
                let nearest = Theme.rounded(frame.position)
                if abs(frame.position - nearest) < 0.001 && abs(frame.velocity) < 0.01 {
                    frame.position = nearest
                    timeline = floor(nearest / 4) * Theme.loop + Theme.mod(nearest) * Theme.beat
                    playing = true
                    spring = nil
                } else if spring == nil {
                    settle(nearest)
                }
            }
        }
        previous = frame.position
        let rounded = Theme.rounded(frame.position)
        if centre != rounded {
            centre = rounded
            arrivedAt = elapsed
            if !reduced {
                owed[Int(Theme.mod(rounded))] += 1
                if !playing { selectionHaptic.selectionChanged() }
            }
        }
        frame.centre = Int(Theme.mod(centre))
        if dt > 0 {
            for index in 0..<4 {
                let step = min(owed[index], 2 * sqrt(owed[index]) * dt / 2800)
                frame.surge[index] = Theme.mod(frame.surge[index] + step, 1)
                owed[index] -= step
            }
        }
        if !playing {
            let age = Theme.clamp((elapsed - arrivedAt) / 900)
            frame.arrival = reduced ? 0 : 1 - Theme.bezier(age)
            frame.age = reduced ? 900 : age * 900
        }
        let selected = added & (1 << frame.centre) != 0 || (frame.centre == 2 && demoAdded)
        if selected != frame.selected {
            frame.selected = selected
            morph.set(selected ? 1 : 0, at: elapsed, duration: reduced ? 150 : 320)
            labelMix.set(selected ? 1 : 0, at: elapsed, duration: reduced ? 150 : 300, linear: true)
        }
        frame.cardPress = press.value(elapsed)
        frame.buttonScale *= buttonPress.value(elapsed)
        frame.morph = morph.value(elapsed)
        frame.labelMix = labelMix.value(elapsed, linear: true)
        return frame
    }

    func touch() {
        if demoAdded { added |= 4 }
        demoAdded = false
        playing = false
        held = true
        lastTouch = elapsed
        spring = nil
        frame.squeeze = 1
        frame.buttonScale = 1
    }

    func release() {
        held = false
        lastTouch = elapsed
    }

    func settle(_ target: Double, velocity: Double = 0) {
        spring = (frame.position, target, velocity, elapsed)
    }

    func move(_ delta: Double) {
        touch()
        settle(Theme.rounded(frame.position) + delta)
        release()
    }

    func toggle() {
        touch()
        added ^= 1 << Int(Theme.mod(Theme.rounded(frame.position)))
        release()
        if !reduced { impactHaptic.impactOccurred() }
    }

    /// Input coordinates are already converted to the reference stage's points.
    func begin(_ point: CGPoint) {
        touch()
        startPosition = frame.position
        startPoint = point
        lastPoint = point
        lastPointTime = elapsed
        dragVelocity = 0
        dragged = false
        touchRegion = (96...416).contains(point.y) ? 1
            : CGRect(x: 24, y: 706, width: 354, height: 56).contains(point) ? 2 : 0
        if touchRegion == 1 {
            let dx = point.x - 201
            let delta = abs(dx) < 96 ? 0.0 : dx > 0 ? 1.0 : -1.0
            frame.pressed = Int(Theme.mod(Theme.rounded(frame.position) + delta))
            press.set(0.97, at: elapsed)
        } else if touchRegion == 2 {
            buttonPress.set(0.97, at: elapsed)
        }
    }

    func change(_ point: CGPoint) {
        guard held else { return }
        let dx = point.x - startPoint.x
        let dy = point.y - startPoint.y
        if touchRegion == 1 {
            if !dragged && abs(dy) > 14 && abs(dx) < 8 { touchRegion = 0 }
            if abs(dx) >= 8 { dragged = true }
            if dragged {
                frame.position = startPosition - dx / 124
                let dt = elapsed - lastPointTime
                if dt > 0 { dragVelocity = -(point.x - lastPoint.x) / 124 * 1000 / dt }
                lastTouch = elapsed
            }
        }
        lastPoint = point
        lastPointTime = elapsed
    }

    func end(_ point: CGPoint, cancelled: Bool = false) {
        guard held else { return }
        if touchRegion == 1 {
            if cancelled {
                settle(Theme.rounded(frame.position))
            } else if dragged {
                let velocity = elapsed - lastPointTime > 100 ? 0 : dragVelocity
                settle(Theme.releaseTarget(frame.position, startPosition, velocity), velocity: velocity)
            } else {
                let dx = point.x - 201
                settle(Theme.rounded(frame.position) + (abs(dx) < 96 ? 0 : dx > 0 ? 1 : -1))
            }
        } else if touchRegion == 2 && !cancelled
                    && CGRect(x: 24, y: 706, width: 354, height: 56).contains(point)
                    && hypot(point.x - startPoint.x, point.y - startPoint.y) < 20 {
            toggle()
        }
        press.set(1, at: elapsed, duration: 1200, spring: true)
        buttonPress.set(1, at: elapsed, duration: reduced ? 120 : 1200, spring: !reduced)
        release()
        touchRegion = 0
    }
}
