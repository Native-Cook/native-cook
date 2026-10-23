import Foundation

/// Stamp Diary Calendar controller: the frame callback and the touch handlers of
/// StampDiaryCalendar.tsx, on plain Foundation types so the regression harness can run them.
/// All coordinates are reference points (402 pt wide) and `now` is seconds on any monotonic clock.
final class StampDiaryCalendarController {
    typealias M = StampDiaryCalendarMotion

    enum Mode { case auto, finger, snap, story, idle, quiet }
    enum Haptic { case selection, light, medium }

    struct Ctl {
        var mode: Mode = .idle
        var memory = StampDiaryCalendarController.autoMemory
        var t0 = 0.0
        var tau0 = 0.0
        var stopAt = 0.0
        var prevTau = 0.0
        var lastTouch = -100.0
        var cornerX = 0.0
        var cornerY = 0.0
        var dx = 0.0
        var dy = 0.0
        var moved = false
        var lift = 1.0
        var release = M.detachCurl(StampDiaryCalendarController.autoMemory)
        var quietShow = false
        var restSent = false
        /// Story speed: 1, or returnRate when a tap sends a stamp home.
        var rate = 1.0
    }

    /// May 12, the wreath.
    static let autoMemory = 3

    private(set) var ctl = Ctl()
    private(set) var pose = M.restPose(StampDiaryCalendarController.autoMemory)

    /// autoPlay prop, and neither Reduce Motion nor a screen reader on.
    var autoOn = false { didSet { restartRest() } }
    /// Reduce Motion: a crossfade at the landing spot.
    var quiet = false { didSet { restartRest() } }
    var may = true { didSet { restartRest() } }

    /// The actor layer's memory changed (the view re-renders the postmark and the art).
    var onActor: (Int) -> Void = { _ in }
    var onHaptic: (Haptic) -> Void = { _ in }
    /// An empty day was tapped (selection ring).
    var onEmptyDay: (Int) -> Void = { _ in }

    private(set) var emptyStart = -100.0

    /// Ring over a tapped empty day: 120 ms in, 1.2 s hold, 300 ms out (ease in-out).
    func emptyAlpha(at now: Double) -> Double {
        func io(_ t: Double) -> Double {
            let x = M.clamp01(t)
            return x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
        }
        let t = now - emptyStart
        if t < 0 { return 0 }
        if t < 0.12 { return io(t / 0.12) }
        if t < 1.32 { return 1 }
        return 1 - io((t - 1.32) / 0.3)
    }

    private func restartRest() {
        ctl.mode = .idle
        ctl.memory = StampDiaryCalendarController.autoMemory
        ctl.lastTouch = -100
        ctl.quietShow = false
        ctl.restSent = false
    }

    /// Does the controller need frames? False when it shows the pin and no loop is waiting.
    func isRunning(now: Double) -> Bool {
        if now - emptyStart < 1.7 { return true }
        if ctl.mode == .idle { return !ctl.restSent || (autoOn && may) }
        return true
    }

    /// The app came back to the foreground: restart the loop clock so it does not jump.
    func resumeClock(now: Double) {
        if ctl.mode == .auto { ctl.t0 = now }
    }

    // MARK: frame

    @discardableResult
    func tick(now: Double) -> StampDiaryCalendarMotion.Pose {
        let c = ctl
        var next: M.Pose?
        switch c.mode {
        case .idle:
            if autoOn && may && now - c.lastTouch > M.Touch.resumeAfter {
                ctl.mode = .auto
                ctl.memory = Self.autoMemory
                ctl.t0 = now
                ctl.restSent = false
                if c.memory != Self.autoMemory { onActor(Self.autoMemory) }
                return pose
            }
            if c.restSent { return pose }
            ctl.restSent = true
            next = M.restPose(c.memory)
        case .auto:
            let tau = (now - c.t0).truncatingRemainder(dividingBy: M.loop)
            next = M.storyPose(c.memory, tau, M.detachCurl(c.memory))
        case .finger:
            var p = M.fingerPose(c.memory, held: now - c.t0, cornerX: c.cornerX, cornerY: c.cornerY, fingerX: c.cornerX + c.dx, fingerY: c.cornerY + c.dy)
            if !c.moved { p.curl = M.flat }
            next = p
        case .snap:
            let k = M.settle(now - c.t0)
            var p = M.fingerPose(c.memory, held: 1, cornerX: c.cornerX, cornerY: c.cornerY, fingerX: c.cornerX + c.dx * k, fingerY: c.cornerY + c.dy * k)
            p.lift = M.mix(1, c.lift, k)
            p.cy = M.cellCenter(StampDiaryCalendarData.memories[c.memory].day, M.may2026).y - M.Peel.rise * ((p.lift - 1) / (M.Peel.lift - 1))
            next = p
            if now - c.t0 > M.Touch.snapFor {
                ctl.mode = .idle
                ctl.restSent = false
                next = M.restPose(c.memory)
            }
        case .story:
            var tau = c.tau0 + (now - c.t0) * c.rate
            if tau >= c.stopAt { tau = c.stopAt }
            if c.prevTau < M.T.land && tau >= M.T.land { onHaptic(.medium) }
            if c.prevTau < M.T.rest - 0.12 && tau >= M.T.rest - 0.12 { onHaptic(.light) }
            next = M.storyPose(c.memory, tau, c.release)
            if tau >= c.stopAt && c.stopAt >= M.T.rest {
                ctl.mode = .idle
                ctl.lastTouch = now
                ctl.restSent = false
                ctl.prevTau = tau
            } else if tau >= c.stopAt && autoOn && now - c.lastTouch > M.Touch.resumeAfter {
                // Held after a touch: two seconds later the stamp goes home and the loop restarts.
                ctl.tau0 = M.T.holdEnd
                ctl.t0 = now
                ctl.stopAt = M.T.rest
                ctl.prevTau = tau
                ctl.rate = 1
            } else {
                ctl.prevTau = tau
            }
        case .quiet:
            let v = M.clamp01((now - c.t0) / M.Touch.fade)
            next = M.quietPose(c.memory, c.quietShow ? v : 1 - v)
            if !c.quietShow && v >= 1 {
                ctl.mode = .idle
                ctl.restSent = false
            }
        }
        if let next { pose = next }
        return pose
    }

    // MARK: touch

    private func startStory(_ memory: Int, tau0: Double, stopAt: Double, now: Double, release: M.Curl) {
        let previous = ctl.memory
        ctl.mode = .story
        ctl.memory = memory
        ctl.tau0 = tau0
        ctl.stopAt = stopAt
        ctl.t0 = now
        ctl.prevTau = tau0
        ctl.lastTouch = now
        ctl.release = release
        ctl.restSent = false
        ctl.rate = 1
        if memory != previous { onActor(memory) }
    }

    func tapMemory(_ memory: Int, now: Double) {
        let c = ctl
        if quiet {
            let showing = c.mode == .quiet && c.quietShow && c.memory == memory
            ctl.mode = .quiet
            ctl.memory = memory
            ctl.t0 = now
            ctl.quietShow = !showing
            ctl.lastTouch = now
            ctl.restSent = false
            if memory != c.memory { onActor(memory) }
            onHaptic(.selection)
            return
        }
        startStory(memory, tau0: M.T.peelStart, stopAt: M.T.holdEnd, now: now, release: M.detachCurl(memory))
        onHaptic(.selection)
    }

    func sendHome(now: Double) {
        let c = ctl
        if c.mode == .quiet && c.quietShow {
            ctl.t0 = now
            ctl.quietShow = false
            ctl.lastTouch = now
            return
        }
        if c.mode == .story && c.stopAt == M.T.holdEnd {
            let tau = min(c.stopAt, c.tau0 + (now - c.t0) * c.rate)
            if tau >= M.T.land {
                ctl.tau0 = max(tau, M.T.holdEnd)
                ctl.t0 = now
                ctl.stopAt = M.T.rest
                ctl.lastTouch = now
                ctl.rate = M.Touch.returnRate
                onHaptic(.light)
            }
        }
    }

    /// Is the controller showing the pin (nothing moving)? Touch on a running loop takes it over.
    private func takeOver(now: Double) -> Bool {
        let c = ctl
        switch c.mode {
        case .auto:
            let tau = (now - c.t0).truncatingRemainder(dividingBy: M.loop)
            if tau < M.T.peelStart - 0.05 || tau >= M.T.rest {
                ctl.mode = .idle
                ctl.lastTouch = now
                ctl.restSent = false
                return true
            }
            // Mid-story: keep playing to the hold, then wait for the user.
            ctl.mode = .story
            ctl.tau0 = tau
            ctl.t0 = now
            ctl.stopAt = tau < M.T.holdEnd ? M.T.holdEnd : M.T.rest
            ctl.prevTau = tau
            ctl.lastTouch = now
            ctl.release = M.detachCurl(c.memory)
            ctl.rate = 1
            return false
        case .idle:
            ctl.lastTouch = now
            return true
        case .quiet:
            return true
        case .snap:
            ctl.mode = .idle
            ctl.lastTouch = now
            ctl.restSent = false
            return true
        default:
            ctl.lastTouch = now
            return false
        }
    }

    func touchBegan(x rx: Double, y ry: Double, now: Double) {
        let free = takeOver(now: now)
        let c = ctl
        // While a stamp is out, any tap sends it home once it has landed.
        if !free {
            let landed = c.mode == .story && c.stopAt == M.T.holdEnd && c.tau0 + (now - c.t0) * c.rate >= M.T.land
            ctl.moved = false
            ctl.dx = 0
            ctl.dy = 0
            ctl.cornerX = landed ? -1 : 0
            ctl.cornerY = 0
            return
        }
        let month: M.Month? = may ? M.may2026 : nil
        if c.mode == .quiet && c.quietShow {
            ctl.cornerX = -1
            ctl.moved = false
            return
        }
        let day = month.map { M.dayAt(rx, ry, $0) } ?? 0
        let memory = month != nil && day > 0 ? M.memoryIndex(day, month!) : -1
        if memory >= 0 && !quiet {
            let mem = StampDiaryCalendarData.memories[memory]
            ctl.mode = .finger
            ctl.memory = memory
            ctl.t0 = now
            ctl.moved = false
            ctl.dx = 0
            ctl.dy = 0
            ctl.cornerX = mem.w / 2
            ctl.cornerY = mem.h / 2
            ctl.lastTouch = now
            ctl.restSent = false
            if memory != c.memory { onActor(memory) }
            onHaptic(.selection)
        } else {
            ctl.cornerX = memory >= 0 ? Double(-2 - memory) : 0
            ctl.cornerY = Double(day)
            ctl.moved = false
        }
    }

    func touchMoved(translationX: Double, translationY: Double, now: Double) {
        let c = ctl
        if c.mode != .finger {
            if hypot(translationX, translationY) > M.Touch.slop { ctl.moved = true }
            return
        }
        var cornerX = c.cornerX
        var cornerY = c.cornerY
        var moved = c.moved
        if !moved && hypot(translationX, translationY) > M.Touch.slop {
            let mem = StampDiaryCalendarData.memories[c.memory]
            moved = true
            // The corner opposite the pull is the one that lifts.
            cornerX = translationX < 0 ? mem.w / 2 : -mem.w / 2
            cornerY = translationY < 0 ? mem.h / 2 : -mem.h / 2
        }
        let lift = pose.lift
        ctl.moved = moved
        ctl.cornerX = cornerX
        ctl.cornerY = cornerY
        ctl.dx = translationX / lift
        ctl.dy = translationY / lift
        ctl.lastTouch = now
    }

    /// Release (or cancel). A tap never activates a pan, so everything is decided here.
    /// `velocityY` is the finger's vertical speed in reference points per second.
    func touchEnded(velocityY: Double, now: Double) {
        let c = ctl
        if c.mode == .finger {
            if !c.moved {
                // A tap: play the peel from where the press already lifted it.
                let lifted = M.clamp01((now - c.t0) / M.Touch.liftIn)
                startStory(c.memory, tau0: M.T.peelStart + 0.25 * lifted * (M.T.detach - M.T.peelStart), stopAt: M.T.holdEnd, now: now, release: M.detachCurl(c.memory))
                return
            }
            let share = M.peelShare(c.memory, c.dx, c.dy)
            if share > M.Touch.commitShare || velocityY < M.Touch.commitVelocity {
                startStory(c.memory, tau0: M.T.detach, stopAt: M.T.holdEnd, now: now, release: pose.curl)
                onHaptic(.light)
            } else {
                ctl.mode = .snap
                ctl.t0 = now
                ctl.lift = pose.lift
                ctl.lastTouch = now
            }
            return
        }
        if c.moved { return }
        if c.cornerX == -1 { sendHome(now: now); return }
        if c.cornerX <= -2 { tapMemory(Int(-2 - c.cornerX), now: now); return }
        if c.cornerY > 0 {
            emptyStart = now
            onEmptyDay(Int(c.cornerY))
            onHaptic(.selection)
        }
    }

    /// Accessibility activation of a day (VoiceOver).
    func select(day: Int, month: StampDiaryCalendarMotion.Month, now: Double) {
        let memory = M.memoryIndex(day, month)
        if memory >= 0 { tapMemory(memory, now: now) } else {
            emptyStart = now
            onEmptyDay(day)
        }
    }
}
