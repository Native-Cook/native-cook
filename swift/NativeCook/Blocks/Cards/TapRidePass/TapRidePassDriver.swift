import AVFoundation
import SwiftUI
import UIKit

/// Owns autoplay, touch interruption, spring integration and read/print feedback.
/// Positions are points/dp; velocities are points/dp per second, tilt is degrees, clocks are seconds.
/// Modes: 0 rest, 1 drag, 2 return, 3 assist, 4 dock, 5 read, 6 close.
/// Lift, ticketProgress, roll and proximity are normalized; tickPhase counts cadence cycles.
final class TapRidePassDriver: ObservableObject {
    typealias Motion = TapRidePassMotion
    @Published var awake = false
    /// Loop and interaction clocks are seconds; negative timestamps mean no event yet.
    var scripted = false
    var mode = 0
    var loopTime = 0.0
    var breathingTime = 0.0
    var now = 0.0
    var lastTouchTime = -10.0
    var held = false
    var cardX = 0.0
    var cardY = 0.0
    var velocityX = 0.0
    var velocityY = 0.0
    var fingerX = 0.0
    var fingerY = 0.0
    var dragStartX = 0.0
    var dragStartY = 0.0
    var lift = 0.0
    var tiltDegrees = 0.0
    var tiltVelocity = 0.0
    var ticketProgress = 0.0
    var roll = 0.0
    var dockTime = -10.0
    var tapTime = -10.0
    var tapAge = -10.0
    var closeTime = -10.0
    var closeProgress = 0.0
    var tickPhase = 0.0
    var lastTickTime = -10.0
    var proximity = 0.0
    var balance = 18.4
    var geometry: TapRidePassGeometry?
    private var date: Date?
    private var pinnedTime: Double?
    private var pinnedGeometry: TapRidePassGeometry?
    private var replaying = false
    private var players: [String: AVAudioPlayer] = [:]
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let success = UINotificationFeedbackGenerator()
    private var dragStarted = false
    var reduceMotion = false
    var balanceAfterFare: Double {
        let remainingBalance = (balance * 100 - 275).rounded() / 100
        return remainingBalance < 2.75 ? remainingBalance + 20 : remainingBalance
    }
    /// Updates the home pose when geometry changes; Swift also prepares bundled feedback sounds.
    func configure(_ layout: TapRidePassGeometry) {
        if geometry != layout {
            geometry = layout
            if mode == 0 {
                cardX = layout.homeX
                cardY = layout.homeY
            }
        }
        if players.isEmpty {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
            for (name, volume) in [("tick", 0.35), ("chime", 0.7), ("print", 0.45), ("close", 0.5)] {
                if let url = Bundle.main.url(forResource: "TapRidePass-trp-\(name)", withExtension: "wav"),
                    let player = try? AVAudioPlayer(contentsOf: url)
                {
                    player.volume = Float(volume)
                    player.prepareToPlay()
                    players[name] = player
                }
            }
        }
    }
    /// Plays a prepared cue unless reduced motion or pinned-frame replay suppresses feedback.
    func cue(_ name: String) {
        if !reduceMotion && !replaying, let player = players[name] {
            player.currentTime = 0
            player.play()
        }
    }
    /// Resets frame timing on lifecycle changes and stops sounds when inactive.
    func active(_ on: Bool) {
        date = nil
        if !on { for player in players.values { player.stop() } }
    }
    /// Samples a display date, clamping elapsed time to 0.05 s.
    /// For a pinned DEBUG time, replays auxiliary spring/cadence state at 60 Hz before freezing the pose.
    func sample(_ at: Date, _ layout: TapRidePassGeometry, _ autoplay: Bool, _ reduce: Bool, _ pin: Double?) {
        configure(layout)
        reduceMotion = reduce
        if let pin {
            if pinnedTime != pin || pinnedGeometry != layout {
                pinnedTime = pin
                pinnedGeometry = layout
                // RN's at(t) freezes the scripted pose, but its cadence and velocity spring keep
                // integrating. Replay those auxiliary states as well, then freeze the entire frame.
                replaying = true
                scripted = true
                mode = 0
                loopTime = 0
                breathingTime = 0
                now = 0
                tickPhase = 0
                lastTickTime = -10
                cardX = layout.homeX
                cardY = layout.homeY
                velocityX = 0
                velocityY = 0
                tiltDegrees = 0
                tiltVelocity = 0
                lift = 0
                ticketProgress = 0
                roll = 0
                proximity = 0
                tapAge = -10
                let targetTime = max(0, pin)
                let frames = Int(floor(targetTime * 60))
                for _ in 0..<frames { step(1.0 / 60, layout, true) }
                let remainder = targetTime - Double(frames) / 60
                if remainder > 1e-10 { step(remainder, layout, true) }
                loopTime = targetTime.truncatingRemainder(dividingBy: Motion.loop)
                breathingTime = loopTime
                setPose(Motion.script(loopTime, layout))
                replaying = false
            }
            return
        }
        // A late frame is caught up in steps of at most 0.05 s (React Native's cap per step), so the
        // loop keeps wall-clock time and the site's recording is cut at exactly `Motion.loop`.
        var remaining = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 1)
        date = at
        while remaining > 1e-9 {
            let deltaTime = min(remaining, 0.05)
            step(deltaTime, layout, autoplay && !reduce)
            remaining -= deltaTime
        }
    }
    /// Copies the script-controlled pose while retaining the integrated tilt and cadence.
    func setPose(_ pose: Motion.Pose) {
        cardX = pose.cardX
        cardY = pose.cardY
        lift = pose.lift
        ticketProgress = pose.ticketProgress
        roll = pose.roll
        tapAge = pose.tapAge
    }
    /// Advances one frame in seconds. Touch motion integrates at up to 240 spring substeps per second.
    /// After 2 s without touch, autoplay rejoins a compatible resting part of the breathing cycle.
    func step(_ deltaTime: Double, _ layout: TapRidePassGeometry, _ autoplay: Bool) {
        now += deltaTime
        let previousX = cardX
        let previousLoopTime = loopTime
        if autoplay && !scripted && mode == 0 && now - lastTouchTime > 2 && ticketProgress < 0.001 {
            let breathingPhase = breathingTime.truncatingRemainder(dividingBy: 3)
            if breathingPhase < 1.15 {
                loopTime = breathingPhase
                scripted = true
            } else if breathingPhase >= 2.3 {
                loopTime = breathingPhase + 6
                scripted = true
            }
        }
        if scripted {
            loopTime = (loopTime + deltaTime).truncatingRemainder(dividingBy: 9)
            breathingTime = loopTime
            setPose(Motion.script(loopTime, layout))
            velocityX = (cardX - previousX) / deltaTime
            if DemoSound.inAutoPlay {
                if previousLoopTime < 3.12 && loopTime >= 3.12 { cue("chime") }
                if previousLoopTime < 3.7 && loopTime >= 3.7 { cue("print") }
                if previousLoopTime < 7.05 && loopTime >= 7.05 { cue("close") }
            }
        } else {
            breathingTime = (breathingTime + deltaTime).truncatingRemainder(dividingBy: 9)
            let substeps = max(1, Int(ceil(deltaTime * 240)))
            let substepTime = deltaTime / Double(substeps)
            for _ in 0..<substeps {
                var targetX = layout.homeX
                var targetY = layout.homeY
                var duration = 0.5
                var ratio = 0.8
                if mode == 1 {
                    let dx = layout.readerX - fingerX
                    let dy = layout.readerY - fingerY
                    let d = hypot(dx, dy)
                    let pull = 0.6 * pow(Motion.smooth(150, 40, d), 1.4)
                    targetX = fingerX + dx * pull
                    targetY = fingerY + dy * pull
                    duration = 0.12
                    ratio = 0.95
                } else if mode == 3 {
                    targetX = layout.readerX
                    targetY = layout.readerY
                    duration = 0.7
                    ratio = 0.88
                } else if mode == 4 || (mode == 5 && now - tapTime < 0.43) {
                    targetX = layout.readerX
                    targetY = layout.readerY
                    duration = 0.3
                    ratio = 0.66
                }
                Motion.spring(&cardX, &velocityX, targetX, duration, ratio, substepTime)
                Motion.spring(&cardY, &velocityY, targetY, duration, ratio, substepTime)
            }
            let targetLift = mode == 3 || (held && [0, 1, 2].contains(mode)) ? 1.0 : 0
            lift += (targetLift - lift) * (1 - exp(-deltaTime * (targetLift > lift ? 18 : 12)))
            if [1, 3].contains(mode) && hypot(cardX - layout.readerX, cardY - layout.readerY) < 46 {
                mode = 4
                dockTime = now
                held = false
                light.impactOccurred()
            }
            if mode == 4 && now - dockTime > 0.55 {
                mode = 5
                tapTime = now
                success.notificationOccurred(.success)
                cue("chime")
            }
            if mode == 5 {
                let timeAfterTap = now - tapTime
                if timeAfterTap - deltaTime < 0.58 && timeAfterTap >= 0.58 { cue("print") }
                ticketProgress = reduceMotion ? (timeAfterTap > 0.3 ? 1 : 0) : Motion.ticketAfter(timeAfterTap)
                roll = Motion.rollAfter(timeAfterTap)
            }
            if mode == 6 {
                let closeAmount = Motion.easeInOut(Motion.segment(now - closeTime, 0, reduceMotion ? 0.25 : 0.75))
                ticketProgress = closeProgress * (1 - closeAmount)
                if closeAmount >= 1 && abs(cardX - layout.homeX) + abs(cardY - layout.homeY) < 0.4 {
                    mode = 0
                    ticketProgress = 0
                    roll = 0
                    tapTime = -10
                }
            }
            if mode == 2 && abs(cardX - layout.homeX) + abs(cardY - layout.homeY) < 0.4
                && abs(velocityX) + abs(velocityY) < 4
            {
                mode = 0
                cardX = layout.homeX
                cardY = layout.homeY
                velocityX = 0
                velocityY = 0
            }
            tapAge = tapTime > 0 ? now - tapTime : -10
        }
        Motion.spring(
            &tiltDegrees, &tiltVelocity, reduceMotion ? 0 : max(-6, min(6, velocityX * 0.01)), 0.5, 0.7, deltaTime)
        let nearness = 1 - min(1, hypot(cardX - layout.readerX, cardY - layout.readerY) / layout.homeDistance)
        let sensing = scripted ? loopTime < 3.12 : [1, 2, 3, 4].contains(mode)
        proximity = sensing ? pow(nearness, 1.3) : 0
        let tickRate = proximity > 0.05 && !reduceMotion ? 1.2 + 7.3 * pow(proximity, 1.6) : 0
        let previousTick = floor(tickPhase)
        tickPhase += tickRate * deltaTime
        if floor(tickPhase) != previousTick {
            lastTickTime = now
            if !scripted {
                selection.selectionChanged()
                cue("tick")
            } else if DemoSound.inAutoPlay {
                cue("tick")
            }
        }
        if scripted && loopTime < 0.05 { tickPhase = 0 }
        if reduceMotion && (mode == 0 || (mode == 5 && tapAge >= 2.83)) && !held
            && abs(tiltDegrees) + abs(tiltVelocity) < 0.001 && awake
        {
            DispatchQueue.main.async { [weak self] in self?.awake = false }
        }
    }
    /// Hands the current scripted phase to touch control without resetting the visible pose.
    func interrupt() {
        lastTouchTime = now
        awake = true
        guard scripted else { return }
        scripted = false
        if loopTime >= 7.05 && ticketProgress > 0.001 {
            mode = 6
            closeTime = now
            closeProgress = ticketProgress
        } else if loopTime >= 3.12 {
            mode = 5
            tapTime = now - (loopTime - 3.12)
        } else if loopTime >= 2.8 {
            mode = 4
            dockTime = now - (loopTime - 2.8) * 0.5
        } else {
            mode = 2
        }
    }
    /// Sends a resting or returning card toward the reader after a tap or accessibility activation.
    func assist() {
        interrupt()
        if [0, 2].contains(mode) { mode = 3 }
    }
    /// Starts the ticket return and applies the fare for a manually completed ride.
    func close() {
        if !scripted { balance = balanceAfterFare }
        cue("close")
        interrupt()
        if mode == 5 || ticketProgress > 0.001 {
            mode = 6
            closeTime = now
            closeProgress = ticketProgress
        }
    }
    /// Captures a new touch; movement is classified as dragging only after the distance threshold.
    func begin() {
        interrupt()
        held = true
        dragStarted = false
    }
    /// Follows translation from touch-down in points/dp; magnetic pull is applied during integration.
    func drag(_ translation: CGSize) {
        lastTouchTime = now
        if !dragStarted && hypot(translation.width, translation.height) >= 3 {
            dragStarted = true
            if [0, 2, 3].contains(mode) {
                mode = 1
                dragStartX = cardX
                dragStartY = cardY
                fingerX = cardX
                fingerY = cardY
            }
        }
        if mode == 1 {
            fingerX = dragStartX + translation.width
            fingerY = dragStartY + translation.height
        }
    }
    /// Releases a drag toward home, or treats a stationary touch as an assisted tap.
    func end() {
        held = false
        lastTouchTime = now
        if mode == 1 { mode = 2 }
        if !dragStarted { assist() }
    }
}
