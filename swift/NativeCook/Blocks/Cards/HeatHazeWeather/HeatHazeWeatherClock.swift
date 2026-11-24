// Heat Haze Weather — the clock: the film, the finger and the hand-over between them.
//
// A port of the React Native ticker (one useFrameCallback). Every live view asks for the frame at the
// timeline's date; the first ask of a date advances the clock, the others read the same frame, so
// nothing here is SwiftUI state and every frame of the film is a pure function of t = time % loop.

import QuartzCore
import UIKit

final class HeatHazeWeatherClock {
    private typealias M = HeatHazeWeatherMotion
    private enum Mode { case auto, touch, returning, waiting }

    struct Frame {
        var hour = M.hRest
        /// the haze's flow, 0 … 1
        var phase = 0.0
        /// the shimmer's place (pt) and strength
        var touchX = 0.0
        var touchY = 0.0
        var touchOn = 0.0
        var sliderGhostA = 0.0
        var sliderGhostP = 0.0
        var sceneGhostA = 0.0
        var sceneGhostX = 0.0
        var sceneGhostY = 0.0
        /// 1 = the conditions follow the film's hour-driven cross-fade, 0 = the leading one under the finger
        var condFilm = 1.0
        /// the condition shown under the finger (fractional while one fades into the next)
        var condPicked = 0.0
    }

    var auto = true
    var width = 393.0
    var height = 852.0
    /// the slider's track, pt
    var trackW = 313.0

    private(set) var frame = Frame()
    private var lastDate: Date?
    private var clock = 0.0
    private var mode = Mode.auto
    private var autoStart = 0.0
    private var flow = 0.0
    private var lastTouch = -10.0
    private var returnAt = 0.0
    private var returnFrom = M.hRest
    private var lastWhole = Int(M.hRest)
    // the finger
    private var touchHour = M.hRest
    private var grabHour = M.hRest
    private var velocity = 0.0
    /// the throw's spring onto the nearest whole hour
    private var settling = false
    private var settleTo = M.hRest
    // the condition under the finger: -1 while the film plays
    private var condLead = -1
    private var condFrom = 0.0
    private var condAt = -10.0
    private var sliderDown = false
    private var sceneDown = false
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var ghostFadeAt = -10.0
    private var ghostFadeSlider = 0.0
    private var ghostFadeScene = 0.0
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    /// the frame at a timeline date; the first ask of a date steps the clock
    func frame(at date: Date) -> Frame {
        // several timelines ask each frame, and not always in date order: only a newer date steps
        if let lastDate, date <= lastDate { return frame }
        let dt = lastDate.map { min(0.05, date.timeIntervalSince($0)) } ?? (1.0 / 60)
        lastDate = date
        step(dt)
        return frame
    }

    func still() {
        frame = Frame()
        mode = .auto
        clock = 0
        autoStart = 0
        flow = 0
    }

    private func step(_ dt: Double) {
        clock += dt
        let now = clock
        var f = frame
        var h = f.hour
        var aim = sceneDown ? 1.0 : 0.0
        var tx = fingerX
        var ty = fingerY

        switch mode {
        case .auto:
            if !auto { autoStart = now }
            let t = auto ? (now - autoStart).truncatingRemainder(dividingBy: M.loop) : 0
            flow = t.truncatingRemainder(dividingBy: M.flow)
            h = M.hourAt(t)
            let sg = M.sliderGhostAt(t)
            f.sliderGhostA = sg.alpha
            f.sliderGhostP = sg.press
            let g = M.sceneGhostAt(t)
            tx = g.x * width
            ty = g.y * height
            aim = g.on
            f.sceneGhostX = tx
            f.sceneGhostY = ty
            f.sceneGhostA = g.alpha
            // the ghost's shimmer is part of the film: it follows the script exactly
            f.touchOn = aim
        case .touch, .returning, .waiting:
            flow = (flow + dt).truncatingRemainder(dividingBy: M.flow)
            if mode == .touch {
                if settling { stepSettle(dt) }
                h = touchHour
                if auto, !sliderDown, !sceneDown, now - lastTouch >= M.idle {
                    returnFrom = h
                    returnAt = now
                    mode = .returning
                }
            } else if mode == .returning {
                let u = M.seg(now - returnAt, 0, M.returnTime)
                h = returnFrom + (M.hRest - returnFrom) * M.smooth(u)
                if u >= 1 { mode = .waiting }
            } else {
                h = M.hRest
                // start the loop as the flow wraps, so the haze carries on without a jump
                if flow < dt * 1.5 {
                    autoStart = now
                    flow = 0
                    mode = .auto
                }
            }
            let on = f.touchOn
            f.touchOn = on + (aim - on) * (1 - exp(-dt * M.touchEase))
            // the ghosts fade out over 150 ms when a finger takes over
            let k = 1 - M.clamp01((now - ghostFadeAt) / 0.15)
            f.sliderGhostA = ghostFadeSlider * k
            f.sceneGhostA = ghostFadeScene * k
        }
        f.touchX = tx
        f.touchY = ty
        fingerX = tx
        fingerY = ty
        f.hour = h
        f.phase = flow / M.flow

        // under a finger the leading condition fades in on its own (0.22 s), so a resting hour never
        // leaves two half drawn
        if mode == .auto {
            condLead = -1
            f.condFilm = 1
        } else {
            let lead = Self.conditionOf(h)
            if condLead < 0 {
                f.condFilm = 0
                f.condPicked = Double(lead)
                condFrom = Double(lead)
                condAt = -10
            } else if lead != condLead {
                condFrom = f.condPicked
                condAt = now
            }
            condLead = lead
            let u = M.clamp01((now - condAt) / 0.22)
            let e = u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
            f.condPicked = condFrom + (Double(lead) - condFrom) * e
        }

        // a light tick on each whole hour the finger passes
        let whole = Int(floor(h))
        if whole != lastWhole {
            lastWhole = whole
            if mode == .touch, Double(whole) >= M.hMin, Double(whole) <= M.hMax { haptic.impactOccurred() }
        }
        frame = f
    }

    // MARK: - the finger

    private func takeOver() {
        if mode != .touch {
            touchHour = frame.hour
            // a throw still springing when the idle return began must not pull the new hour back to its landing
            settling = false
            velocity = 0
            ghostFadeAt = clock
            ghostFadeSlider = frame.sliderGhostA
            ghostFadeScene = frame.sceneGhostA
            mode = .touch
        }
        lastTouch = clock
    }

    /// the condition that shows most at hour h
    private static func conditionOf(_ h: Double) -> Int {
        var best = 0
        for i in 1 ..< 3 where M.conditionAt(h, i) > M.conditionAt(h, best) { best = i }
        return best
    }

    func sliderBegan(x: Double) {
        takeOver()
        settling = false
        sliderDown = true
        let range = M.hMax - M.hMin
        let w = max(trackW, 1)
        let thumbX = (touchHour - M.hMin) / range * w
        // a touch away from the thumb jumps it there
        if abs(x - thumbX) > 22 { touchHour = M.hMin + M.clamp01(x / w) * range }
        grabHour = touchHour - x / w * range
        haptic.prepare()
    }

    func sliderMoved(x: Double) {
        lastTouch = clock
        let range = M.hMax - M.hMin
        let w = max(trackW, 1)
        let raw = grabHour + x / w * range
        // rubber band past either end
        let over = raw < M.hMin ? raw - M.hMin : raw > M.hMax ? raw - M.hMax : 0
        let band = over / (1 + abs(over) / 0.6)
        touchHour = over == 0 ? raw : (over < 0 ? M.hMin : M.hMax) + band * 0.55
    }

    func sliderEnded(velocityX: Double) {
        let range = M.hMax - M.hMin
        let w = max(trackW, 1)
        settle(velocityX / w * range)
        sliderDown = false
        lastTouch = clock
    }

    /// The throw lands where a 0.994 decay would (v × 0.166 s), on the nearest whole hour: a whole hour's
    /// forecast is whole numbers, so the counters never rest half rolled.
    private func settle(_ v: Double) {
        settleTo = min(max(touchHour + v * 0.166, M.hMin), M.hMax).rounded()
        velocity = v
        settling = true
    }

    /// Reanimated's withSpring (stiffness 140, damping 22, mass 1), stepped in small slices
    private func stepSettle(_ dt: Double) {
        let n = max(1, Int(ceil(dt / 0.002)))
        let h = dt / Double(n)
        for _ in 0 ..< n {
            let a = -140 * (touchHour - settleTo) - 22 * velocity
            velocity += a * h
            touchHour += velocity * h
        }
        if abs(touchHour - settleTo) < 0.0005, abs(velocity) < 0.005 {
            touchHour = settleTo
            velocity = 0
            settling = false
        }
    }

    func sceneBegan(x: Double, y: Double) {
        takeOver()
        sceneDown = true
        fingerX = x
        fingerY = y
        haptic.impactOccurred()
    }

    func sceneMoved(x: Double, y: Double) {
        lastTouch = clock
        fingerX = x
        fingerY = y
    }

    func sceneEnded() {
        sceneDown = false
        lastTouch = clock
    }

    /// VoiceOver's adjust: half an hour a step
    func nudge(_ hours: Double) {
        takeOver()
        settling = false
        touchHour = min(max((touchHour * 2).rounded() / 2 + hours, M.hMin), M.hMax)
    }

    func sliderCancelled() {
        if sliderDown { settle(0) }
        sliderDown = false
        lastTouch = clock
    }
}
