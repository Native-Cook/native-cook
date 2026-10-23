import Observation
import UIKit

@MainActor
@Observable
final class SendMoneyCardsModel {
    private enum State {
        case rest
        case hold(SendMoneyCardsSpec.Pose)
        case pressing(start: TimeInterval, from: SendMoneyCardsSpec.Pose, drag: Double)
        case releasing(start: TimeInterval, lift: Double, drag: Double, arm: Double)
        case sending(start: TimeInterval, from: SendMoneyCardsSpec.Pose)
        case open
        case closing(start: TimeInterval)
    }

    let autoPlay: Bool
    private(set) var isOpen = false
    private(set) var pressedTarget: String?
    private(set) var revision = 0
    private var state: State = .rest
    private var autoStart = Date.timeIntervalSinceReferenceDate
    private var interrupted = false
    private var resumeTask: Task<Void, Never>?
    private var completionTask: Task<Void, Never>?
    private var lastTime = Date.timeIntervalSinceReferenceDate
    private var lastPose = SendMoneyCardsSpec.Pose()
    private var width = SendMoneyCardsSpec.screenWidth
    private var height = SendMoneyCardsSpec.screenHeight
    private var offsetY = 0.0
    private var reduced = false
    @ObservationIgnored private var hintPosition = 0.0
    @ObservationIgnored private var hintVelocity = 0.0
    @ObservationIgnored private var hintTime = Date.timeIntervalSinceReferenceDate

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
    }

    var needsTimeline: Bool {
        if autoPlay && !reduced && !interrupted { return true }
        switch state {
        case .pressing, .releasing, .sending, .closing:
            return true
        case .rest, .hold, .open:
            return false
        }
    }

    func updateLayout(width: Double, height: Double, offsetY: Double, reduced: Bool) {
        self.width = width
        self.height = height
        self.offsetY = offsetY
        if self.reduced != reduced {
            resumeTask?.cancel()
            if !interrupted {
                state = .rest
                isOpen = false
                autoStart = Date.timeIntervalSinceReferenceDate
            }
        }
        self.reduced = reduced
    }

    func pose(at time: TimeInterval) -> SendMoneyCardsSpec.Pose {
        lastTime = time
        let offscreen = height + 40
        var pose: SendMoneyCardsSpec.Pose
        if autoPlay && !reduced && !interrupted {
            let elapsed = (time - autoStart) * 1000
            let clock = elapsed.truncatingRemainder(dividingBy: SendMoneyCardsSpec.loopMilliseconds)
            pose = SendMoneyCardsSpec.scriptPose(milliseconds: max(0, clock), offscreen: offscreen, reduced: reduced)
        } else {
            pose = manualPose(at: time, offscreen: offscreen)
            if !reduced {
                pose.hintLift = updateHintSpring(target: pose.lift, time: time)
            }
        }
        lastPose = pose
        return pose
    }

    func interrupt() {
        guard !interrupted else { return }
        let current = pose(at: Date.timeIntervalSinceReferenceDate)
        interrupted = true
        isOpen = current.sheet > 0.5 || current.fade > 0.5
        hintPosition = current.hintLift
        hintVelocity = 0
        hintTime = Date.timeIntervalSinceReferenceDate
        state = .hold(current)
        revision &+= 1
    }

    func beginCard() {
        let now = Date.timeIntervalSinceReferenceDate
        interrupt()
        let current = pose(at: now)
        completionTask?.cancel()
        state = .pressing(start: now, from: current, drag: 0)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        revision &+= 1
    }

    func dragCard(translationY: Double) {
        let now = Date.timeIntervalSinceReferenceDate
        switch state {
        case let .pressing(start, from, _):
            state = .pressing(start: start, from: from,
                              drag: reduced ? 0 : SendMoneyCardsSpec.rubber(translationY))
        default:
            break
        }
        _ = pose(at: now)
        revision &+= 1
    }

    func endCard(translationY: Double, velocityY: Double) {
        if translationY < -SendMoneyCardsSpec.openDrag || velocityY < -SendMoneyCardsSpec.flickVelocity {
            send()
        } else {
            release()
        }
        scheduleResume()
    }

    func cancelCard() {
        release()
        scheduleResume()
    }

    func beginSheet() {
        interrupt()
    }

    func endSheet(translationY: Double, velocityY: Double) {
        if translationY < -40 || velocityY < -SendMoneyCardsSpec.flickVelocity {
            close(haptic: true)
        }
        scheduleResume()
    }

    func closeButton() {
        interrupt()
        close(haptic: true)
        scheduleResume()
    }

    func accessibilitySend() {
        interrupt()
        send()
        scheduleResume()
    }

    func press(_ target: String?) {
        pressedTarget = target
        revision &+= 1
    }

    func stop() {
        resumeTask?.cancel()
        completionTask?.cancel()
    }

    private func manualPose(at time: TimeInterval, offscreen: Double) -> SendMoneyCardsSpec.Pose {
        switch state {
        case .rest:
            return .init()
        case let .hold(pose):
            return pose
        case let .pressing(start, from, drag):
            let elapsed = (time - start) * 1000
            if reduced {
                let arm = SendMoneyCardsSpec.mix(from.arm, 1,
                    SendMoneyCardsSpec.segment(elapsed, 0, SendMoneyCardsSpec.fadeDuration))
                return .init(arm: arm)
            }
            let lift = SendMoneyCardsSpec.mix(from.lift, SendMoneyCardsSpec.pressLift,
                SendMoneyCardsSpec.easeOutCubic(SendMoneyCardsSpec.segment(elapsed, 0, SendMoneyCardsSpec.pressDuration))) + drag
            let arm = SendMoneyCardsSpec.mix(from.arm, 1,
                SendMoneyCardsSpec.easeOutCubic(SendMoneyCardsSpec.segment(elapsed, 0, SendMoneyCardsSpec.armDuration)))
            return .init(lift: lift, arm: arm, hintLift: lift)
        case let .releasing(start, baseLift, drag, startArm):
            let elapsed = (time - start) * 1000
            let timedLift = SendMoneyCardsSpec.mix(baseLift, 0,
                SendMoneyCardsSpec.easeInOutCubic(SendMoneyCardsSpec.segment(elapsed, 0,
                    SendMoneyCardsSpec.releaseDuration)))
            let dragLift = SendMoneyCardsSpec.springDisplacement(
                elapsed,
                initial: drag,
                velocity: 0,
                mass: SendMoneyCardsSpec.snapBackMass,
                stiffness: SendMoneyCardsSpec.snapBackStiffness,
                damping: SendMoneyCardsSpec.snapBackDamping
            )
            let lift = reduced ? 0 : timedLift + dragLift
            let duration = reduced ? SendMoneyCardsSpec.fadeDuration : SendMoneyCardsSpec.armDuration
            let arm = SendMoneyCardsSpec.mix(startArm, 0, SendMoneyCardsSpec.segment(elapsed, 0, duration))
            return .init(lift: lift, arm: arm, hintLift: lift)
        case let .sending(start, from):
            let elapsed = (time - start) * 1000
            if reduced {
                let fade = SendMoneyCardsSpec.mix(from.fade, 1,
                    SendMoneyCardsSpec.segment(elapsed, 0, SendMoneyCardsSpec.fadeDuration))
                return .init(fade: fade)
            }
            let lift = SendMoneyCardsSpec.mix(from.lift, offscreen,
                SendMoneyCardsSpec.segment(elapsed, 0, SendMoneyCardsSpec.fallDuration))
            let sheet = SendMoneyCardsSpec.mix(from.sheet, 1,
                SendMoneyCardsSpec.segment(elapsed, SendMoneyCardsSpec.openDelay, SendMoneyCardsSpec.openDuration))
            return .init(lift: lift, arm: 1, sheet: sheet, hintLift: lift)
        case .open:
            return reduced ? .init(fade: 1) : .init(lift: offscreen, arm: 1, sheet: 1, hintLift: offscreen)
        case let .closing(start):
            let elapsed = (time - start) * 1000
            if reduced {
                return .init(fade: 1 - SendMoneyCardsSpec.segment(elapsed, 0, SendMoneyCardsSpec.fadeDuration), closing: true)
            }
            let sheet = 1 - SendMoneyCardsSpec.segment(elapsed, 0, SendMoneyCardsSpec.closeDuration)
            let lift: Double
            if elapsed < SendMoneyCardsSpec.returnDuration {
                lift = SendMoneyCardsSpec.table(elapsed,
                    times: SendMoneyCardsSpec.returnTimes, values: SendMoneyCardsSpec.returnLifts)
            } else {
                lift = SendMoneyCardsSpec.springSettle(elapsed - SendMoneyCardsSpec.returnDuration)
            }
            let arm = 1 - SendMoneyCardsSpec.easeInOutCubic(
                SendMoneyCardsSpec.segment(elapsed, SendMoneyCardsSpec.tabOut, SendMoneyCardsSpec.tabFade))
            let hintTime = max(0, elapsed - SendMoneyCardsSpec.hintLag)
            let hintLift = hintTime < SendMoneyCardsSpec.returnDuration
                ? SendMoneyCardsSpec.table(hintTime, times: SendMoneyCardsSpec.returnTimes, values: SendMoneyCardsSpec.returnLifts)
                : SendMoneyCardsSpec.springSettle(hintTime - SendMoneyCardsSpec.returnDuration)
            return .init(lift: lift, arm: arm, sheet: sheet, hintLift: hintLift, closing: true)
        }
    }

    private func send() {
        let now = Date.timeIntervalSinceReferenceDate
        let current = pose(at: now)
        completionTask?.cancel()
        state = .sending(start: now, from: current)
        isOpen = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        revision &+= 1
        complete(after: reduced ? SendMoneyCardsSpec.fadeDuration : max(SendMoneyCardsSpec.fallDuration, SendMoneyCardsSpec.openDuration)) { [weak self] in
            guard let self else { return }
            self.state = .open
            self.revision &+= 1
        }
    }

    private func release() {
        let now = Date.timeIntervalSinceReferenceDate
        let current = pose(at: now)
        let pieces: (lift: Double, drag: Double, arm: Double)
        if case let .pressing(start, from, drag) = state {
            let elapsed = (now - start) * 1000
            let lift = SendMoneyCardsSpec.mix(from.lift, SendMoneyCardsSpec.pressLift,
                SendMoneyCardsSpec.easeOutCubic(
                    SendMoneyCardsSpec.segment(elapsed, 0, SendMoneyCardsSpec.pressDuration)))
            let arm = SendMoneyCardsSpec.mix(from.arm, 1,
                SendMoneyCardsSpec.easeOutCubic(
                    SendMoneyCardsSpec.segment(elapsed, 0, SendMoneyCardsSpec.armDuration)))
            pieces = (lift, drag, arm)
        } else {
            pieces = (current.lift, 0, current.arm)
        }
        completionTask?.cancel()
        state = .releasing(start: now, lift: pieces.lift, drag: pieces.drag, arm: pieces.arm)
        revision &+= 1
        complete(after: reduced ? SendMoneyCardsSpec.fadeDuration : max(SendMoneyCardsSpec.releaseDuration, 700)) { [weak self] in
            guard let self else { return }
            self.state = .rest
            self.revision &+= 1
        }
    }

    private func close(haptic: Bool) {
        let now = Date.timeIntervalSinceReferenceDate
        completionTask?.cancel()
        state = .closing(start: now)
        isOpen = false
        if haptic { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
        revision &+= 1
        complete(after: reduced ? SendMoneyCardsSpec.fadeDuration : 900) { [weak self] in
            guard let self else { return }
            self.state = .rest
            self.revision &+= 1
        }
    }

    private func scheduleResume() {
        resumeTask?.cancel()
        guard autoPlay, !reduced else { return }
        resumeTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(SendMoneyCardsSpec.resumeAfter * 1_000_000))
            guard !Task.isCancelled, let self, !self.reduced else { return }
            if case .rest = self.state {
                self.restartAutoplay()
                return
            }
            let extra: Double
            if self.isOpen {
                self.close(haptic: false)
                extra = SendMoneyCardsSpec.closeDuration + 900
            } else {
                self.release()
                extra = SendMoneyCardsSpec.closeDuration + 900
            }
            try? await Task.sleep(nanoseconds: UInt64(extra * 1_000_000))
            guard !Task.isCancelled else { return }
            self.restartAutoplay()
        }
    }

    private func restartAutoplay() {
        guard autoPlay, !reduced else { return }
        state = .rest
        isOpen = false
        interrupted = false
        autoStart = Date.timeIntervalSinceReferenceDate
        hintPosition = 0
        hintVelocity = 0
        hintTime = autoStart
        revision &+= 1
    }

    private func updateHintSpring(target: Double, time: TimeInterval) -> Double {
        let elapsed = max(0, min(0.05, time - hintTime))
        hintTime = time
        guard elapsed > 0 else { return hintPosition }
        let acceleration = SendMoneyCardsSpec.hintFollowStiffness * (target - hintPosition) -
            SendMoneyCardsSpec.hintFollowDamping * hintVelocity
        hintVelocity += acceleration * elapsed
        hintPosition += hintVelocity * elapsed
        return hintPosition
    }

    private func complete(after milliseconds: Double, action: @escaping @MainActor () -> Void) {
        completionTask = Task {
            try? await Task.sleep(nanoseconds: UInt64(milliseconds * 1_000_000))
            guard !Task.isCancelled else { return }
            action()
        }
    }
}
