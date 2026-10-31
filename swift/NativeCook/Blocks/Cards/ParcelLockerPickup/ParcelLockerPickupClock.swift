import AVFoundation
import SwiftUI
import UIKit

/// Drives autoplay and touch from the same pose, with event-relative clocks in seconds.
/// The interactive hinge integrates angular velocity; autoplay samples the deterministic script.
final class ParcelLockerPickupClock: ObservableObject {
    typealias Motion = ParcelLockerPickupMotion
    @Published var sleeping = false
    var resources = ParcelLockerPickupResources.shared
    var pose = Motion.Pose()
    var automatic = false
    var loopTime = 0.0
    var elapsedTime = 0.0
    var touchAt = -10.0

    /// Interaction phase: 0 rest, 1 opening, 2 taking, 3 folding the receipt.
    var mode = 0
    var openAt = -10.0
    var takeAt = -10.0
    var doneAt = -10.0

    /// Door velocity in degrees per second; dragAngle and takeFrom are degrees.
    var velocity = 0.0
    var dragging = false
    var dragAngle = 0.0
    var takeFrom = 0.0
    private var frameDate: Date?
    private var players: [String: AVAudioPlayer] = [:]

    /// Hit target: 0 none, 1 door/parcel, 2 Open, 3 Done. Points are in scene coordinates.
    private var touchTarget = 0
    private var touchStart = CGPoint.zero
    private var lastPoint = CGPoint.zero
    private var didPan = false
    private var touchBeganAt = 0.0
    private var pressRect = CGRect.zero
    private let selection = UISelectionFeedbackGenerator()
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let heavy = UIImpactFeedbackGenerator(style: .heavy)
    private let notification = UINotificationFeedbackGenerator()

    /// Resets the frame timestamp on lifecycle changes and prepares bundled audio when permitted.
    func active(_ active: Bool, _ reducedMotion: Bool) {
        frameDate = nil
        if !active {
            for player in players.values {
                player.stop()
            }
            return
        }
        guard !reducedMotion, players.isEmpty else {
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
        for (name, volume) in [
            "beep": 0.3, "latch": 0.7, "creak": 0.35, "hum": 0.25, "lift": 0.4, "thud": 0.8, "done": 0.6, "fold": 0.4,
        ] {
            if let url = Bundle.main.url(forResource: "ParcelLockerPickup-plp-\(name)", withExtension: "wav"),
                let player = try? AVAudioPlayer(contentsOf: url)
            {
                player.volume = Float(volume)
                player.prepareToPlay()
                players[name] = player
            }
        }
    }

    /// Restarts a prepared cue; reduced motion suppresses sound alongside the large movements.
    private func cue(_ name: String, _ reducedMotion: Bool) {
        if !reducedMotion, let player = players[name] {
            player.currentTime = 0
            player.play()
        }
    }

    /// Haptic kinds match the interaction beats: digit, latch, door impact and pickup success.
    private func buzz(_ kind: Int) {
        if kind == 0 {
            selection.selectionChanged()
        } else if kind == 1 {
            medium.impactOccurred()
        } else if kind == 2 {
            heavy.impactOccurred()
        } else {
            notification.notificationOccurred(.success)
        }
    }

    /// Samples a pinned loop time or advances by at most 0.05 s per frame.
    /// After 2 s without touch, a resting pose can re-enter autoplay; spring substeps run at 240 Hz.
    func sample(_ frameDate: Date, _ autoPlay: Bool, _ reducedMotion: Bool, _ pinnedTime: Double?) -> Motion.Pose {
        if let pinnedTime {
            loopTime = pinnedTime
            return Motion.script(pinnedTime)
        }
        // The script follows the wall clock, so a dropped frame never stretches the loop (the site's
        // recording is cut at exactly `Motion.loop`); physics and touch keep React Native's 0.05 s cap.
        let wallDelta = min(max(0, self.frameDate.map { frameDate.timeIntervalSince($0) } ?? 0.016), 1)
        let deltaTime = min(wallDelta, 0.05)
        self.frameDate = frameDate
        let previousTime = elapsedTime
        elapsedTime += deltaTime
        if autoPlay && !reducedMotion && !automatic && mode == 0 && elapsedTime - touchAt > 2 && pose.angle < 0.01
            && pose.sheet < 0.001
        {
            automatic = true
            loopTime = 0
        }
        if automatic {
            let previousLoopTime = loopTime
            loopTime = (loopTime + wallDelta).truncatingRemainder(dividingBy: Motion.loop)
            pose = Motion.script(loopTime)
            velocity = 0
            if DemoSound.inAutoPlay {
                let events: [(Double, String)] = [
                    (1.55, "beep"), (1.67, "beep"), (1.79, "beep"), (1.91, "beep"), (2.12, "latch"), (2.35, "creak"),
                    (2.45, "hum"), (4, "lift"), (4.45, "done"),
                    (
                        4.62 + (Double.pi - atan(sqrt(1 - 0.42 * 0.42) / 0.42))
                            / (2 * Double.pi / 0.55 * sqrt(1 - 0.42 * 0.42)),
                        "thud"
                    ), (8.4, "fold"),
                ]
                for (eventTime, name) in events where previousLoopTime < eventTime && loopTime >= eventTime {
                    cue(name, reducedMotion)
                }
            }
            return pose
        }
        let openAge = elapsedTime - openAt
        let takeAge = elapsedTime - takeAt
        let doneAge = elapsedTime - doneAt
        if mode == 1 {
            if openAge - deltaTime < 0.72 && openAge >= 0.72 {
                buzz(1)
                cue("latch", reducedMotion)
                velocity += 120
            }
            if openAge - deltaTime < 1.05 && openAge >= 1.05 {
                cue("hum", reducedMotion)
            }
            if openAge - deltaTime < 0.95 && openAge >= 0.95 {
                cue("creak", reducedMotion)
            }
        }
        var targetAngle = 0.0
        var duration = 0.95
        var ratio = 0.7
        if mode == 1 && openAge >= 0.95 {
            targetAngle = 104
        }
        if mode == 2 && takeAge < 0.62 {
            targetAngle = takeFrom
        }
        if mode == 2 && takeAge >= 0.62 {
            duration = 0.55
            ratio = 0.42
        }
        if dragging {
            targetAngle = dragAngle
            duration = 0.18
            ratio = 0.9
        }
        let stepCount = max(1, Int(ceil(deltaTime * 240)))
        let step = deltaTime / Double(stepCount)
        let angularFrequency = 2 * Double.pi / duration
        for _ in 0..<stepCount {
            velocity +=
                (angularFrequency * angularFrequency * (targetAngle - pose.angle) - 2 * ratio * angularFrequency
                    * velocity) * step
            pose.angle += velocity * step
            if pose.angle < 0 {
                pose.angle = -pose.angle * 0.3
                if velocity < -40 {
                    buzz(2)
                    cue("thud", reducedMotion)
                }
                velocity = -velocity * 0.3
            }
        }
        pose.light =
            mode == 1 || mode == 2
            ? Motion.lightAt(openAge) * (mode == 2 ? 1 - Motion.smooth(0.77, 1.02, takeAge) : 1) : 0
        pose.led =
            mode == 1
            ? (openAge < 0.72 ? 0 : 1) : mode == 2 ? (takeAge > 1.32 && pose.angle < 3 ? 2 : 1) : mode == 3 ? 2 : 0
        pose.parcel = mode == 2 || mode == 3 ? Motion.easeInOut(Motion.seg(takeAge, 0, 0.92)) : 0
        pose.sheet =
            mode == 2
            ? (reducedMotion ? (takeAge > 0.45 ? 1 : 0) : Motion.springStep(takeAge - 0.45, 0.6, 0.86))
            : mode == 3 ? 1 - Motion.easeInOut(Motion.seg(doneAge, 0, 0.5)) : 0
        pose.digits =
            mode == 1 || mode == 2 ? Motion.digitsAt(openAge) : mode == 3 ? 4 * (1 - Motion.seg(doneAge, 0, 0.3)) : 0
        pose.takeAge = mode == 2 ? takeAge : -10
        pose.fold = mode == 3 ? Motion.easeInOut(Motion.seg(doneAge, 0, 0.5)) : 0
        pose.press = max(0, pose.press - deltaTime * 6)
        if mode == 2 && takeAge - deltaTime < 0.45 && takeAge >= 0.45 {
            buzz(3)
            cue("done", reducedMotion)
        }
        if mode == 3 && doneAge > 0.55 {
            mode = 0
            openAt = -10
            takeAt = -10
        }
        if mode == 1 && floor(pose.digits) != floor(Motion.digitsAt(previousTime - openAt)) {
            buzz(0)
            cue("beep", reducedMotion)
        }
        if mode == 2 && takeAge > 4 && !sleeping {
            DispatchQueue.main.async { [weak self] in
                self?.sleeping = true
            }
        }
        return pose
    }

    /// Reconstructs event timestamps from the current script beat so touch takes over in place.
    func interrupt() {
        sleeping = false
        touchAt = elapsedTime
        if !automatic {
            return
        }
        automatic = false
        if loopTime >= 8.4 && loopTime < 9.2 {
            mode = 3
            doneAt = elapsedTime - (loopTime - 8.4)
            takeAt = elapsedTime - (loopTime - 4)
        } else if loopTime >= 4 && loopTime < 9.2 {
            mode = 2
            takeAt = elapsedTime - (loopTime - 4)
            openAt = elapsedTime - (loopTime - 1.4)
            takeFrom = Motion.openAngle(2.6)
        } else if loopTime >= 1.4 && loopTime < 9.2 {
            mode = 1
            openAt = elapsedTime - (loopTime - 1.4)
        } else {
            mode = 0
        }
    }

    /// Starts the code/latch/swing sequence when the pickup is resting.
    func open() {
        interrupt()
        if mode == 0 {
            mode = 1
            openAt = elapsedTime
            pose.press = 1
        }
    }

    /// Begins folding a collected receipt and plays its closing cue.
    func done(_ reducedMotion: Bool) {
        interrupt()
        if mode == 2 {
            mode = 3
            doneAt = elapsedTime
        }
        cue("fold", reducedMotion)
    }

    /// Starts parcel flight only after the door has opened far enough to reach inside.
    func take(_ reducedMotion: Bool) {
        if mode == 1 && elapsedTime - openAt > 1.05 && pose.angle > 60 {
            mode = 2
            takeAt = elapsedTime
            takeFrom = pose.angle
            cue("lift", reducedMotion)
        }
    }

    /// Arbitrates parcel tap/drag, door drag and retained button presses in scene points.
    /// The 4 pt door threshold competes with the 6 pt parcel threshold in the reference gesture race.
    func touch(
        _ state: UIGestureRecognizer.State, _ point: CGPoint, _ geometry: ParcelLockerPickupGeometry,
        _ reducedMotion: Bool
    ) {
        switch state {
        case .began:
            touchStart = point
            lastPoint = point
            touchTarget = 0
            didPan = false
            touchBeganAt = ProcessInfo.processInfo.systemUptime
            let sheet = geometry.sheet(pose.sheet)
            if CGRect(
                x: geometry.doorX - 30, y: geometry.doorY - 20, width: geometry.doorWidth + 60,
                height: geometry.doorHeight + 40
            ).contains(point) {
                touchTarget = 1
                interrupt()
            } else if point.x >= 38 && point.x <= geometry.width - 38 {
                let labels = resources.labels
                if pose.sheet > 0.5 {
                    let buttonY =
                        sheet.minY + 22 + max(28, labels["picked"]?.size.height ?? 32) + 2
                        + (labels["closed"]?.size.height ?? 19) + 18 + 64 + 20
                    if point.y >= buttonY && point.y <= buttonY + 50 {
                        touchTarget = 3
                        pressRect = CGRect(x: 18, y: buttonY - 20, width: geometry.width - 36, height: 100)
                    }
                } else {
                    let buttonY =
                        sheet.minY + 20 + (labels["brand"]?.size.height ?? 14) + 12
                        + (labels["ready"]?.size.height ?? 28) + 2 + (labels["order"]?.size.height ?? 19) + 16 + 50 + 18
                    if point.y >= buttonY && point.y <= buttonY + 52 {
                        touchTarget = 2
                        pressRect = CGRect(x: 18, y: buttonY - 20, width: geometry.width - 36, height: 102)
                    }
                }
            }
        case .changed:
            if touchTarget == 1 {
                touchAt = elapsedTime
                if !didPan && hypot(point.x - touchStart.x, point.y - touchStart.y) >= 4 {
                    didPan = true
                    // Race(parcelPan(minDistance: 6), tap, doorPan(minDistance: 4)).
                    // A first movement crossing both thresholds lets parcelPan win.
                    if hypot(point.x - touchStart.x, point.y - touchStart.y) >= 6 {
                        interrupt()
                        take(reducedMotion)
                    } else if mode == 1 && elapsedTime - openAt > 0.72 {
                        dragging = true
                        dragAngle = pose.angle
                    }
                }
                if dragging {
                    dragAngle = min(118, max(0, dragAngle - (point.x - lastPoint.x) * 0.9))
                }
            }
            lastPoint = point
        case .ended, .cancelled, .failed:
            let tap =
                hypot(point.x - touchStart.x, point.y - touchStart.y) < 6
                && ProcessInfo.processInfo.systemUptime - touchBeganAt <= 0.5
            if touchTarget == 1 {
                if state == .ended && !didPan && tap {
                    interrupt()
                    take(reducedMotion)
                }
                dragging = false
                touchAt = elapsedTime
            } else if touchTarget == 2 && state == .ended && pressRect.contains(point) {
                open()
            } else if touchTarget == 3 && state == .ended && pressRect.contains(point) {
                done(reducedMotion)
            }
            touchTarget = 0
        default: break
        }
    }
}
