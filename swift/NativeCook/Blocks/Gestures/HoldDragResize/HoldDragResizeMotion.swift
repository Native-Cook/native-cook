import SwiftUI
import UIKit

@MainActor
final class HoldDragResizeMotion: NSObject, ObservableObject {
    typealias T = HoldDragResizeTokens

    @Published var height = T.sizes[0]
    @Published var lag = T.sizes[0]
    @Published var held = 0.0
    @Published var press = 0.0
    @Published var clock = 0.0
    @Published var coach = 1.0
    @Published var coachProgress = 0.0
    @Published var ghost = CGPoint.zero
    @Published var ghostOpacity = 0.0
    @Published var ghostPress = 0.0
    @Published var playing = true
    @Published var sizeIndex = 0
    @Published var nearIndex = 0

    private let autoPlay: Bool
    private var reduceMotion = false
    private var viewport = CGSize(width: T.referenceWidth, height: T.sizes[2])
    private var safeTop = 0.0
    private var displayLink: CADisplayLink?
    private var lastTargetTimestamp: CFTimeInterval?
    private var phase = 0.0
    private var paused: Bool
    private var touching = false
    private var quiet = 0.0
    private var tidy = -1.0
    private var dragStartHeight = T.sizes[0]
    private var lastDetent = 0
    private var idleCoachTime = 0.0

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        paused = !autoPlay
        super.init()
    }

    var shownHeight: Double {
        height + T.demo.coachGrow * coachProgress * coach
    }

    var currentClip: Int {
        Int(clock / (Double(T.timing.clip) / 1_000)) % T.clips.count
    }

    var currentSecond: Int { Int(clock) % Int(T.loop) }

    func start(size: CGSize, safeTop: Double, reduceMotion: Bool) {
        viewport = size
        self.safeTop = safeTop
        self.reduceMotion = reduceMotion
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(frame(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        lastTargetTimestamp = nil
    }

    @objc private func frame(_ link: CADisplayLink) {
        let raw = lastTargetTimestamp.map { link.targetTimestamp - $0 } ?? link.duration
        lastTargetTimestamp = link.targetTimestamp
        let dt = min(max(raw, 0), T.maximumFrameDelta)

        let targetLag = shownHeight
        lag = reduceMotion ? targetLag : lag + (targetLag - lag) * (1 - exp(-T.upgrade.follow * dt))

        if autoPlay && !paused {
            phase = (phase + dt / T.qaSlowmo).truncatingRemainder(dividingBy: T.loop)
            applyScript(at: T.qaState >= 0 ? T.qaT : phase)
            clock = T.qaState >= 0 ? T.qaT : phase
        } else if playing {
            clock = (clock + dt).truncatingRemainder(dividingBy: T.loop)
        }
        if !autoPlay, coach > 0, !reduceMotion {
            idleCoachTime = (idleCoachTime + dt).truncatingRemainder(dividingBy: 2)
            if idleCoachTime < 0.7 {
                coachProgress = cosine(idleCoachTime / 0.7)
            } else if idleCoachTime < 1.5 {
                coachProgress = 1 - cosine((idleCoachTime - 0.7) / 0.8)
            } else {
                coachProgress = 0
            }
        }

        guard autoPlay, paused else { return }
        if !touching { quiet += dt }
        if tidy < 0, quiet >= Double(T.resumeMilliseconds) / 1_000 {
            tidy = 0
        }
        if tidy >= 0 {
            tidy += dt
            let u = min(tidy / T.demo.tidy, 1)
            height += (T.sizes[0] - height) * u
            coach = u
            if u >= 1 {
                phase = 0
                tidy = -1
                paused = false
                resetToLoopStart()
            }
        }
    }

    private func applyScript(at t: Double) {
        let d = T.demo
        coachProgress = t < d.gotItTap ? coachHandProgress(t) : 0
        if t < d.gotItTap {
            coach = 1
        } else if t < d.gotItTap + Double(T.timing.coachFade) / 1_000 {
            coach = 1 - (t - d.gotItTap) / (Double(T.timing.coachFade) / 1_000)
        } else if t >= d.coachBack {
            coach = min((t - d.coachBack) / (T.loop - d.coachBack), 1)
        } else {
            coach = 0
        }

        if t < d.growLifted {
            height = T.sizes[0]
        } else if t < d.growMid {
            height = mix(T.sizes[0], T.sizes[1], cosine((t - d.growLifted) / (d.growMid - d.growLifted)))
        } else if t < d.growHold {
            height = T.sizes[1]
        } else if t < d.growEnd {
            height = mix(T.sizes[1], T.sizes[2], cosine((t - d.growHold) / (d.growEnd - d.growHold)))
        } else if t < d.shrinkLifted {
            height = T.sizes[2]
        } else if t < d.shrinkEnd {
            height = mix(T.sizes[2], T.sizes[0], cosine((t - d.shrinkLifted) / (d.shrinkEnd - d.shrinkLifted)))
        } else {
            height = T.sizes[0]
        }
        sizeIndex = nearest(height)
        nearIndex = sizeIndex

        let growing = t >= d.growLifted && t < d.growUp
        let shrinking = t >= d.shrinkLifted && t < d.shrinkUp
        held = reduceMotion ? 0 : ((growing || shrinking) ? 1 : 0)

        ghostOpacity = ghostOpacity(at: t)
        ghostPress = ghostPress(at: t)
        if t >= d.gotItShow, t < d.gotItTap {
            ghost = CGPoint(x: T.coachButton.x + T.coachButton.width / 2, y: T.coachButton.y + T.coachButton.height / 2)
        } else if t >= d.growDown, t < d.growUp {
            let dy = (height - T.sizes[0]) / T.resize.gain
            ghost = CGPoint(x: viewport.width / 2, y: T.frames[0].top + T.sizes[0] / 2 + dy)
        } else if t >= d.shrinkDown, t < d.shrinkUp {
            let travel = (T.sizes[2] - T.sizes[0]) / T.resize.gain
            let dy = (height - T.sizes[2]) / T.resize.gain
            ghost = CGPoint(x: viewport.width / 2, y: T.frames[0].top + T.sizes[0] / 2 + travel + dy)
        }
    }

    private func coachHandProgress(_ t: Double) -> Double {
        let d = T.demo
        if t < d.coachDown { return 0 }
        if t < d.coachBottom { return cosine((t - d.coachDown) / (d.coachBottom - d.coachDown)) }
        if t < d.coachUp { return 1 - cosine((t - d.coachBottom) / (d.coachUp - d.coachBottom)) }
        return 0
    }

    private func ghostOpacity(at t: Double) -> Double {
        let windows = [(T.demo.gotItShow, T.demo.gotItTap), (T.demo.growDown, T.demo.growUp), (T.demo.shrinkDown, T.demo.shrinkUp)]
        for (start, end) in windows where t >= start && t < end {
            return min((t - start) / T.demo.fingerFade, (end - t) / T.demo.fingerFade, 1)
        }
        return 0
    }

    private func ghostPress(at t: Double) -> Double {
        let windows = [(T.demo.gotItShow, T.demo.gotItTap), (T.demo.growDown, T.demo.growLifted), (T.demo.shrinkDown, T.demo.shrinkLifted)]
        for (start, end) in windows where t >= start && t < end {
            return min(max((t - start) / (end - start), 0), 1)
        }
        return 0
    }

    func touchBegan() {
        touching = true
        quiet = 0
        tidy = -1
        if !paused {
            paused = true
            ghostOpacity = 0
            held = 0
        }
        withAnimation(.easeOut(duration: Double(T.upgrade.pressInMilliseconds) / 1_000)) { press = 1 }
    }

    func holdBegan() {
        dragStartHeight = height
        lastDetent = nearest(height)
        if !reduceMotion {
            let spring = T.springs["lift"]!
            withAnimation(.interpolatingSpring(mass: spring.mass, stiffness: spring.stiffness, damping: spring.damping, initialVelocity: 0)) { held = 1 }
        }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    func holdMoved(translation: Double) {
        var next = rubber(dragStartHeight + translation * T.resize.gain)
        for size in T.sizes {
            let distance = size - next
            if abs(distance) < T.upgrade.magnet {
                next += distance * T.upgrade.magnetPull * (1 - abs(distance) / T.upgrade.magnet)
            }
        }
        height = next
        let index = nearest(next)
        nearIndex = index
        if index != lastDetent {
            UISelectionFeedbackGenerator().selectionChanged()
            lastDetent = index
        }
    }

    func touchEnded(velocity: Double, wasHeld: Bool) {
        touching = false
        quiet = 0
        withAnimation(.easeOut(duration: Double(T.upgrade.pressOutMilliseconds) / 1_000)) { press = 0 }
        if wasHeld {
            let spring = T.springs["drop"]!
            withAnimation(.interpolatingSpring(mass: spring.mass, stiffness: spring.stiffness, damping: spring.damping, initialVelocity: 0)) { held = 0 }
            let projected = height + velocity * T.resize.gain * T.resize.project
            snap(to: nearest(projected), velocity: velocity * T.resize.gain, haptic: true)
        } else {
            playing.toggle()
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    func dismissCoach() {
        interruptAutoplay()
        withAnimation(.linear(duration: Double(T.timing.coachFade) / 1_000)) {
            coach = 0
            coachProgress = 0
        }
    }

    func step(_ delta: Int) {
        interruptAutoplay()
        snap(to: min(max(nearest(height) + delta, 0), T.sizes.count - 1), velocity: 0, haptic: false)
    }

    func togglePlay() {
        interruptAutoplay()
        playing.toggle()
    }

    private func interruptAutoplay() {
        guard autoPlay else { return }
        paused = true
        quiet = 0
        tidy = -1
        ghostOpacity = 0
    }

    private func snap(to index: Int, velocity: Double, haptic: Bool) {
        sizeIndex = index
        nearIndex = index
        if reduceMotion {
            withAnimation(.easeOut(duration: Double(T.timing.reduced) / 1_000)) { height = T.sizes[index] }
        } else {
            let spring = T.springs["snap"]!
            withAnimation(.interpolatingSpring(mass: spring.mass, stiffness: spring.stiffness, damping: spring.damping, initialVelocity: velocity / max(abs(T.sizes[index] - height), 1))) {
                height = T.sizes[index]
            }
        }
        if haptic { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }

    private func resetToLoopStart() {
        height = T.sizes[0]
        lag = T.sizes[0]
        held = 0
        press = 0
        coach = 1
        coachProgress = 0
        clock = 0
        sizeIndex = 0
        nearIndex = 0
    }

    private func nearest(_ value: Double) -> Int {
        T.sizes.indices.min(by: { abs(T.sizes[$0] - value) < abs(T.sizes[$1] - value) }) ?? 0
    }

    private func rubber(_ value: Double) -> Double {
        if value < T.sizes[0] { return T.sizes[0] - min((T.sizes[0] - value) * T.resize.rubber, T.resize.rubberMax) }
        if value > T.sizes[2] { return T.sizes[2] + min((value - T.sizes[2]) * T.resize.rubber, T.resize.rubberMax) }
        return value
    }

    private func cosine(_ value: Double) -> Double {
        let u = min(max(value, 0), 1)
        return (1 - cos(.pi * u)) / 2
    }

    private func mix(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }
}
