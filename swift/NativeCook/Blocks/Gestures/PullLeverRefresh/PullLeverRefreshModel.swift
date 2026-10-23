import Combine
import SwiftUI
import UIKit

@MainActor
final class PullLeverRefreshModel: ObservableObject {
    @Published private(set) var stage: PullLeverRefreshStage = .rest
    @Published private(set) var angle = PullLeverRefreshTokens.homeAngle
    @Published private(set) var bar: CGFloat = 0
    @Published private(set) var brew: CGFloat = 0
    @Published private(set) var cupOffset: CGFloat = 0
    @Published private(set) var cupOpacity: CGFloat = 1
    @Published private(set) var clock: TimeInterval = 0
    @Published private(set) var isHeld = false

    private var displayLink: CADisplayLink?
    private var previousTimestamp: CFTimeInterval?
    private var autoPlay = true
    private var reduceMotion = false
    private var elapsed: TimeInterval = 0
    private var resumeAt: CFTimeInterval?
    private var isManual = false
    private var manualStrokes = 0
    private var manualStartAngle = PullLeverRefreshTokens.homeAngle
    private var startPoint = CGPoint.zero
    private var pivot = CGPoint.zero
    private var scale: CGFloat = 1
    private var angularDrag = false
    private var rearmed = true
    private var manualBrewStart: CFTimeInterval?
    private var releaseStart: CFTimeInterval?
    private var releaseFrom = PullLeverRefreshTokens.homeAngle
    private var didNotifyDone = false

    deinit { displayLink?.invalidate() }

    func configure(autoPlay: Bool, reduceMotion: Bool, layout: PullLeverRefreshLayout) {
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
        pivot = layout.pivot
        scale = layout.scale
        if reduceMotion {
            reset()
            stopDisplayLink()
        } else if autoPlay {
            startDisplayLink()
        }
    }

    func disappear() { stopDisplayLink() }

    func begin(at point: CGPoint) {
        guard !reduceMotion, manualBrewStart == nil else { return }
        startDisplayLink()
        isManual = true
        resumeAt = nil
        releaseStart = nil
        startPoint = point
        manualStartAngle = angle
        isHeld = true
        let dx = point.x - pivot.x
        let dy = point.y - pivot.y
        let distance = hypot(dx, dy)
        let pointAngle = atan2(dy, dx) * 180 / .pi
        angularDrag = distance > PullLeverRefreshTokens.leverLength * scale * 0.3
            && pointAngle > -125 && pointAngle < 35
        rearmed = angle <= PullLeverRefreshTokens.rearmAngle
    }

    func move(to point: CGPoint) {
        guard isManual, isHeld else { return }
        let next: CGFloat
        if angularDrag {
            next = atan2(point.y - pivot.y, point.x - pivot.x) * 180 / .pi
        } else {
            next = manualStartAngle + (point.y - startPoint.y) / scale * PullLeverRefreshTokens.degreesPerPoint
        }
        angle = rubberBand(next)
        if angle <= PullLeverRefreshTokens.rearmAngle { rearmed = true }
        if rearmed, angle >= PullLeverRefreshTokens.strokeAngle {
            rearmed = false
            if manualStrokes < 3 {
                manualStrokes += 1
                bar = CGFloat(manualStrokes * 3)
                impact(manualStrokes == 3 ? .medium : .rigid)
            }
        }
        stage = manualStrokes == 3 ? .full : (manualStrokes == 0 ? .rest : .pumping(manualStrokes * 3))
    }

    func end(velocity: CGPoint) {
        guard isManual, isHeld else { return }
        isHeld = false
        if manualStrokes >= 3 {
            startManualBrew()
        } else {
            releaseFrom = angle
            releaseStart = CACurrentMediaTime()
            stage = manualStrokes == 0 ? .rest : .pumping(manualStrokes * 3)
        }
        resumeAt = CACurrentMediaTime() + PullLeverRefreshTokens.resumeDelay
    }

    func accessibilityActivate() {
        guard !reduceMotion, manualBrewStart == nil else { return }
        startDisplayLink()
        isManual = true
        resumeAt = CACurrentMediaTime() + PullLeverRefreshTokens.resumeDelay
        if stage == .done {
            resetManual()
            return
        }
        manualStrokes = 3
        bar = 9
        angle = PullLeverRefreshTokens.bottomAngle
        impact(.medium)
        startManualBrew()
    }

    private func tick(_ timestamp: CFTimeInterval) {
        let delta = min(max(timestamp - (previousTimestamp ?? timestamp), 0), 1.0 / 15.0)
        previousTimestamp = timestamp

        if isManual {
            renderManual(timestamp: timestamp)
            if let resumeAt, timestamp >= resumeAt, !isHeld {
                isManual = false
                self.resumeAt = nil
                reset()
            }
            return
        }
        guard autoPlay, !reduceMotion else {
            stopDisplayLink()
            return
        }
        elapsed = (elapsed + delta).truncatingRemainder(dividingBy: PullLeverRefreshTokens.loop)
        renderAuto(elapsed)
    }

    private func renderAuto(_ time: TimeInterval) {
        clock = time
        let strokes: [(down: Double, up: Double)] = [(0.70, 1.26), (1.75, 2.31), (2.80, 4.01)]
        var completed = 0
        var lever = PullLeverRefreshTokens.homeAngle
        for (index, stroke) in strokes.enumerated() {
            if time >= stroke.down, time < stroke.down + 0.46 {
                let p = PullLeverRefreshTokens.easeInOut(CGFloat((time - stroke.down) / 0.46))
                lever = mix(PullLeverRefreshTokens.homeAngle, PullLeverRefreshTokens.bottomAngle, p)
                completed = index
                if lever >= PullLeverRefreshTokens.strokeAngle { completed = index + 1 }
                break
            } else if time >= stroke.down + 0.46, time < stroke.up {
                lever = PullLeverRefreshTokens.bottomAngle
                completed = index + 1
                break
            } else if time >= stroke.up, time < stroke.up + 0.62 {
                let p = springHome(CGFloat((time - stroke.up) / 0.62))
                lever = mix(PullLeverRefreshTokens.bottomAngle, PullLeverRefreshTokens.homeAngle, p)
                completed = index + 1
                break
            } else if time >= stroke.up + 0.62 {
                completed = index + 1
            }
        }

        angle = lever
        bar = CGFloat(completed * 3)
        brew = 0
        cupOffset = 0
        cupOpacity = 1
        isHeld = strokes.contains { time >= $0.down && time < $0.up }

        if time < 0.70 {
            stage = .rest
            bar = 0
        } else if time < 2.80 {
            stage = .pumping(completed * 3)
        } else if time < 4.01 {
            stage = completed >= 3 ? .full : .pumping(completed * 3)
        } else if time < 8.61 {
            stage = .brewing
            brew = CGFloat((time - 4.01) / PullLeverRefreshTokens.brewDuration)
            bar = mix(9, 1.4, PullLeverRefreshTokens.easeOut(brew))
        } else if time < 10.60 {
            stage = .done
            brew = 1
            bar = 1.4
        } else if time < 10.98 {
            stage = .done
            brew = 1
            let p = PullLeverRefreshTokens.easeInOut(CGFloat((time - 10.60) / 0.38))
            cupOffset = 46 * p
            cupOpacity = 1 - p
        } else if time < 11.44 {
            stage = .rest
            bar = 0
            let p = PullLeverRefreshTokens.easeOut(CGFloat((time - 10.98) / 0.46))
            cupOffset = 46 * (1 - p)
            cupOpacity = p
        } else {
            stage = .rest
            angle = PullLeverRefreshTokens.homeAngle
            bar = 0
        }
    }

    private func renderManual(timestamp: CFTimeInterval) {
        clock = timestamp
        if let releaseStart, !isHeld {
            let p = PullLeverRefreshTokens.easeOut(CGFloat((timestamp - releaseStart) / 0.62))
            angle = mix(releaseFrom, PullLeverRefreshTokens.homeAngle, p)
            if p >= 1 { self.releaseStart = nil }
        }
        guard let brewStart = manualBrewStart else { return }
        let p = min(max(CGFloat((timestamp - brewStart) / PullLeverRefreshTokens.brewDuration), 0), 1)
        brew = p
        angle = mix(PullLeverRefreshTokens.bottomAngle, PullLeverRefreshTokens.homeAngle, springHome(min(p * 3, 1)))
        bar = mix(9, 1.4, PullLeverRefreshTokens.easeOut(p))
        stage = p < 1 ? .brewing : .done
        if p >= 1, !didNotifyDone {
            didNotifyDone = true
            notification()
        }
    }

    private func startManualBrew() {
        manualBrewStart = CACurrentMediaTime()
        didNotifyDone = false
        stage = .brewing
        brew = 0
        releaseStart = nil
    }

    private func resetManual() {
        manualStrokes = 0
        manualBrewStart = nil
        releaseStart = nil
        angle = PullLeverRefreshTokens.homeAngle
        bar = 0
        brew = 0
        stage = .rest
    }

    private func reset() {
        elapsed = 0
        isManual = false
        resumeAt = nil
        resetManual()
        cupOffset = 0
        cupOpacity = 1
        clock = 0
        isHeld = false
    }

    private func rubberBand(_ raw: CGFloat) -> CGFloat {
        let low = PullLeverRefreshTokens.homeAngle
        let high = PullLeverRefreshTokens.bottomAngle
        if raw < low {
            let overflow = low - raw
            return low - 5 * (1 - exp(-overflow / 40))
        }
        if raw > high {
            let overflow = raw - high
            return high + 6 * (1 - exp(-overflow / 40))
        }
        return raw
    }

    private func springHome(_ value: CGFloat) -> CGFloat {
        let x = min(max(value, 0), 1)
        return min(max(1 - exp(-7.5 * x) * cos(9.5 * x), 0), 1)
    }

    private func mix(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat {
        a + (b - a) * min(max(t, 0), 1)
    }

    private func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        UIImpactFeedbackGenerator(style: style).impactOccurred(intensity: 0.72)
    }

    private func notification() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    private func startDisplayLink() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(displayLinkTick(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    private func stopDisplayLink() {
        displayLink?.invalidate()
        displayLink = nil
        previousTimestamp = nil
    }

    @objc private func displayLinkTick(_ link: CADisplayLink) {
        tick(link.timestamp)
    }
}
