import AVFoundation
import CoreMotion
import SwiftUI
import UIKit

final class BlacklightFlightPassportClock: ObservableObject {
    typealias M = BlacklightFlightPassportMotion
    @Published var revision = 0
    @Published var dark = false
    @Published var fading = false
    var driver = M.Driver()
    private let motion = CMMotionManager()
    private var gyroInitialized = false
    private var baselinePitch = 0.0, baselineRoll = 0.0
    private var gyroX = 0.0, gyroY = 0.0
    func active(_ enabled: Bool) {
        previous = nil
        if enabled {
            if motion.isDeviceMotionAvailable && !motion.isDeviceMotionActive {
                motion.deviceMotionUpdateInterval = 1.0 / 60
                motion.startDeviceMotionUpdates()
            }
        } else {
            motion.stopDeviceMotionUpdates()
            gyroInitialized = false
            gyroX = 0; gyroY = 0
            driver.tiltX = 0; driver.tiltY = 0
        }
    }
    deinit { motion.stopDeviceMotionUpdates() }
    private func updateTilt(_ dt: Double) {
        guard let attitude = motion.deviceMotion?.attitude else { return }
        if !gyroInitialized {
            gyroInitialized = true
            baselinePitch = attitude.pitch; baselineRoll = attitude.roll
        }
        let kb = min(1, dt * 0.6)
        baselinePitch += (attitude.pitch - baselinePitch) * kb
        baselineRoll += (attitude.roll - baselineRoll) * kb
        let ks = 1 - exp(-dt * 8)
        gyroX += (max(-1, min(1, (attitude.roll - baselineRoll) / 0.35)) - gyroX) * ks
        gyroY += (max(-1, min(1, (attitude.pitch - baselinePitch) / 0.35)) - gyroY) * ks
        driver.tiltX = gyroX; driver.tiltY = gyroY
    }
    private var previous: Date?
    private var now = 0.0
    private var target = 0
    private var moved = false
    private var fade = 0.0
    private var fadeFrom = 0.0
    private var fadeAim = 0.0
    private var fadeStart: Date?
    private var players: [String: AVAudioPlayer] = [:]

    func resetDate() { previous = nil }
    func reset() {
        driver = M.Driver(); previous = nil; now = 0
        revision += 1
    }
    func sample(_ date: Date, auto: Bool, reduced: Bool, pinned: Double?) -> (M.Frame, M.Driver, Double) {
        if let pinned, !reduced {
            var still = M.Driver(); still.t = pinned; still.amb = pinned
            updateDark(M.frameAt(pinned).uv > 0.5)
            return (M.frameAt(pinned), still, 0)
        }
        if reduced {
            if let started = fadeStart {
                let progress = M.clamp(date.timeIntervalSince(started) / 0.22)
                // Reanimated withTiming defaults to inOut(quad).
                let eased = progress < 0.5 ? 2 * progress * progress : 1 - pow(-2 * progress + 2, 2) / 2
                fade = fadeFrom + (fadeAim - fadeFrom) * eased
                if progress == 1 {
                    fadeStart = nil
                    DispatchQueue.main.async { [weak self] in self?.fading = false }
                }
            }
            // The RN reduced-motion crossfade leaves its status bar in daylight style.
            updateDark(false)
            return (M.frameAt(0), M.Driver(), fade)
        }
        let dt = min(max(0, previous.map { date.timeIntervalSince($0) } ?? 0.016), 0.05)
        previous = date; now += dt
        updateTilt(dt)
        let before = driver.t
        M.step(&driver, dt, now, auto)
        // The site's recording plays the sounds a touch would make (DemoSound).
        if DemoSound.inAutoPlay && auto && driver.mode == 0 {
            if before < M.press.0 && driver.t >= M.press.0 && driver.t < M.press.0 + 1 { play("on") }
            if before < M.pressOff.0 && driver.t >= M.pressOff.0 && driver.t < M.pressOff.0 + 1 { play("off") }
        }
        let frame = M.frameAt(driver.t)
        updateDark(frame.uv > 0.5)
        return (frame, driver, 0)
    }
    private func updateDark(_ next: Bool) {
        guard dark != next else { return }
        // Only crossings publish; the frame driver itself remains outside SwiftUI state.
        DispatchQueue.main.async { [weak self] in
            guard let self, self.dark != next else { return }
            self.dark = next
        }
    }
    private func crossfade() {
        fadeFrom = fade; fadeAim = fade > 0.5 ? 0 : 1; fadeStart = Date()
        fading = true
        revision += 1
    }
    func accessibilityToggle(reduced: Bool) {
        if reduced { crossfade(); return }
        M.touched(&driver, now); M.toggle(&driver)
        revision += 1
    }
    func touch(_ point: CGPoint, phase: Int, translation: CGSize, size: CGSize, reduced: Bool) {
        let scale = size.width / 402
        let dy = (size.height - 874 * scale) / 2
        let x = point.x / scale, y = (point.y - dy) / scale
        if phase == 0 {
            moved = false
            if x > 139 && x < 261 && y > 656 && y < 707 { target = 1 }
            else if x > 21 && x < 382 && y > 178 && y < 650 { target = 2 }
            else { target = 0 }
            if !reduced {
                M.touched(&driver, now)
                if target == 1 { driver.pressAim = 1 }
                if target == 2 && !M.isUv(driver.t) { driver.torchAim = 1; driver.tx = x; driver.ty = y - 40 }
            }
        } else if phase == 1 {
            if !reduced {
                driver.last = now
                if hypot(translation.width, translation.height) > 10 { moved = true }
                if target == 2 && driver.torchAim > 0 { driver.tx = x; driver.ty = y - 40 }
            }
        } else {
            let previousTarget = target; target = 0
            driver.pressAim = 0; driver.torchAim = 0; driver.last = now
            if previousTarget == 1 && !moved {
                if reduced { crossfade() }
                else {
                    let on = M.toggle(&driver)
                    play(on ? "on" : "off")
                    UIImpactFeedbackGenerator(style: on ? .medium : .light).impactOccurred()
                }
            }
        }
        revision += 1
    }
    func prepareAudio(enabled: Bool) {
        guard enabled else {
            for player in players.values { player.stop() }
            players.removeAll()
            return
        }
        if players.isEmpty {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
            for name in ["on", "off", "route"] {
                let resource = name == "route" ? "bfp-route" : "bfp-uv-" + name
                if let url = Bundle.main.url(forResource: "BlacklightFlightPassport-" + resource, withExtension: "wav"),
                   let player = try? AVAudioPlayer(contentsOf: url) {
                    player.volume = name == "route" ? 0.6 : 0.85
                    player.prepareToPlay(); players[name] = player
                }
            }
        }
    }
    private func play(_ cue: String) {
        prepareAudio(enabled: true)
        players[cue]?.currentTime = 0
        players[cue]?.play()
    }
}
