import SwiftUI
import UIKit
import AVFoundation

final class PixelTrophyUnlockClock: ObservableObject {
    typealias M = PixelTrophyUnlockMotion
    @Published var running = false
    @Published var idle = false
    private var wake: Timer?
    private var idleAt: Date?
    private var idleTime = 0.0, idleNow = 0.0, idleDuration = 0.0
    private func cancelIdle(_ catchUp: Bool) {
        if catchUp, let at = idleAt {
            let elapsed = min(idleDuration,max(0,Date().timeIntervalSince(at)))
            time = (idleTime+elapsed).truncatingRemainder(dividingBy:M.LOOP)
            now = idleNow+elapsed
            channels = M.at(time)
        }
        wake?.invalidate();wake = nil;idleAt = nil;idle = false;date = nil
    }
    private func restUntil(_ end: Double, _ at: Date) {
        guard idleAt == nil else { return }
        let duration = end-time
        guard duration>0.02 else { return }
        idleAt = at;idleTime = time;idleNow = now;idleDuration = duration
        DispatchQueue.main.async { [weak self] in
            guard let self, self.isActive, self.idleAt != nil else { return }
            self.idle = true
            self.wake = Timer(timeInterval:duration,repeats:false) { [weak self] _ in
                guard let self, self.isActive else { return }
                // Skip only the motionless hold. The moving clock still consumes wall time.
                let late = min(0.5,max(0,Date().timeIntervalSince(at)-duration))
                self.time = (self.idleTime+duration+late-0.016).truncatingRemainder(dividingBy:M.LOOP)
                self.now = self.idleNow+duration+late-0.016
                self.idleAt = nil;self.wake = nil;self.date = nil;self.idle = false
            }
            if let timer = self.wake { RunLoop.main.add(timer,forMode:.common) }
        }
    }
    deinit { wake?.invalidate() }
    var time = 0.0, now = 0.0, last = -10.0, a0 = -100.0, b0 = -100.0
    var shake = -1, shakeAt = -10.0
    var landed = false, flying = false
    var date: Date?
    var channels = M.channels(-1,-1)
    private var isActive = false
    private var players: [String: AVAudioPlayer] = [:]
    func active(_ enabled: Bool, _ reduced: Bool, _ pinned: Bool) {
        isActive = enabled
        cancelIdle(true)
        if enabled && !reduced && !pinned && players.isEmpty {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, mode:.default,options:.mixWithOthers)
            for name in ["unlock","whoosh","land"] {
                if let url = Bundle.main.url(forResource:"PixelTrophyUnlock-ptu-"+name,withExtension:"wav"),let player = try? AVAudioPlayer(contentsOf:url) {
                    player.volume = name == "whoosh" ? 0.5 : 0.75;player.prepareToPlay();players[name] = player
                }
            }
        }
        if !enabled || reduced || pinned { for p in players.values { p.stop() } }
    }
    func cue(_ name: String, _ reduced: Bool) {
        guard !reduced, let p = players[name] else { return };p.currentTime = 0;p.play()
    }
    func buzz(_ kind: Int) {
        if kind == 2 || kind == 3 { UINotificationFeedbackGenerator().notificationOccurred(kind == 2 ? .success : .warning) }
        else { UIImpactFeedbackGenerator(style:kind == 1 ? .medium : .light).impactOccurred() }
    }
    func sample(_ date: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Channels {
        if let pinned { return M.at(pinned,reduced) }
        guard isActive else { return channels }
        // Wall time is independent of rendering throughput, including GPU-less recording hosts.
        let dt = min(0.5,max(0,self.date.map { date.timeIntervalSince($0) } ?? 0.016))
        self.date = date;now += dt
        let before = time
        var a = -1.0, b = -1.0
        let autoPlaying = auto && now-last>=2 && a0 < -50
        if autoPlaying {
            time = (time+dt).truncatingRemainder(dividingBy:M.LOOP)
            a = time>=1 ? time-1 : -1;b = time>=6.6 ? time-6.6 : -1
        } else {
            a = a0 > -50 ? now-a0 : -1;b = b0 > -50 ? now-b0 : -1
            if b>1.4 { a0 = -100;b0 = -100 }
        }
        let c = M.channels(a,b,reduced)
        let demoAudio = DemoSound.inAutoPlay && autoPlaying
        if demoAudio && before<1 && time>=1 { cue("unlock",reduced) }
        let inFlight = c.fly>0.05 && c.fly<0.95
        if inFlight && !flying && a0 > -50 { cue("whoosh",reduced) }
        // A slow recording frame may skip the narrow in-flight range entirely.
        let whooshAt = 1.95 + 0.9 * pow(0.05/4,1.0/3)
        if demoAudio && before<whooshAt && time>=whooshAt { cue("whoosh",reduced) }
        flying = inFlight
        if c.earned && !landed {
            if a0 > -50 || demoAudio { cue("land",reduced) }
            if a0 > -50 { buzz(2) }
        }
        landed = c.earned;channels = c
        if autoPlaying {
            if time<1 { restUntil(1,date) }
            else if time>=3.35 && time<6.6 { restUntil(6.6,date) }
            else if time>=7.4 { restUntil(8,date) }
        }
        let moving = a0 > -50 && (a<2.35 || (b>=0 && b<1.45)) || now-shakeAt<0.45 || now-last<2
        if running && !moving { DispatchQueue.main.async { [weak self] in self?.running = false } }
        return c
    }
    func tap(_ index: Int?, _ reduced: Bool) {
        // Bring the resting autoplay sample up to the actual tap instant.
        cancelIdle(true)
        if !running { date = nil }
        last = now;running = true
        guard let i = index else { return }
        if i == 1 {
            if !channels.earned && a0 < -50 { a0 = now;b0 = -100;cue("unlock",reduced);buzz(1) }
            else if channels.earned { if a0 < -50 { a0 = now-3 };b0 = now;buzz(0) }
        } else if i != 0 { shake = i;shakeAt = now;buzz(3) }
    }
}
