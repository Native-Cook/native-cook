/// Live pose integration, gesture interruption, and Undo transitions matching the React Native driver.
import AVFoundation
import UIKit

/// Advances the same pose model for autoplay, dragging, release, and Undo.
final class CrumpleToDeleteDriver {
    typealias M = CrumpleToDeleteMotion
    enum Mode { case rest, drag, back, gone, undo }
    var mode: Mode = .rest
    var auto = false, t = 0.0, now = 0.0, touchAt = -10.0
    var pose = M.Pose(), vx = 0.0, vy = 0.0, vc = 0.0
    var relAt = -10.0, rx = 0.0, ry = 0.0, rc = 0.0, rspin = 0.0
    var undoAt = -10.0, sx = 0.0, sy = 0.0, lastStep = 0
    var gone = false
    private var players: [String: AVAudioPlayer] = [:]
    private let selection = UISelectionFeedbackGenerator()
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private var reduced = false
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
        for (cue, volume) in [("rustle", 0.5), ("toss", 0.4), ("bin", 0.8), ("pop", 0.5), ("open", 0.5)] {
            if let url = CrumpleToDeleteResources.url("ctd-\(cue)", "wav"),
                let player = try? AVAudioPlayer(contentsOf: url)
            {
                player.volume = Float(volume)
                player.prepareToPlay()
                players[cue] = player
            }
        }
    }
    func cue(_ name: String) {
        guard !reduced, let player = players[name] else { return }
        player.currentTime = 0
        player.play()
    }
    func reset(_ geometry: M.Geo) { pose = .init(x: geometry.hx, y: geometry.hy) }
    /// Integrates one live frame, or evaluates the exact script pose for a pinned preview.
    func step(_ dt: Double, _ geometry: M.Geo, _ autoPlay: Bool, _ reduced: Bool, _ pinned: Double?) {
        self.reduced = reduced
        if let pinned {
            pose = M.script(pinned, geometry)
            gone = pinned >= 3.45 && pinned < 6.3
            return
        }
        guard dt > 0 else { return }
        now += dt
        if autoPlay && !reduced && !auto && mode == .rest && now - touchAt > 2 && pose.c < 0.001 {
            auto = true
            t = 0
        }
        if auto {
            let before = t
            let oldC = pose.c
            t += dt
            if t >= M.loop { t -= M.loop }
            let next = M.script(t, geometry)
            vx = (next.x - pose.x) / dt
            vy = (next.y - pose.y) / dt
            pose = next
            gone = t >= 3.45 && t < 6.3
            if DemoSound.inAutoPlay && !reduced {
                if t >= 1 && t < 2.55 && Int(floor(oldC * 8)) != Int(floor(pose.c * 8)) { cue("rustle") }
                for (time, name) in [(2.83, "toss"), (3.45, "bin"), (6.0, "pop"), (6.62, "open")] {
                    if before < time && t >= time { cue(name) }
                }
            }
            return
        }
        switch mode {
        case .drag:
            let n = max(1, Int(ceil(dt * 240)))
            for _ in 0..<n {
                (pose.x, vx) = M.springTo(pose.x, vx, sx, 0.12, 0.95, dt / Double(n))
                (pose.y, vy) = M.springTo(pose.y, vy, sy, 0.12, 0.95, dt / Double(n))
            }
            let target = min(0.92, max(0, (pose.y - geometry.hy) / 300))
            pose.c += (target - pose.c) * (1 - exp(-dt * 14))
            pose.spin = (pose.x - geometry.hx) * 0.002
            pose.lift += (1 - pose.lift) * (1 - exp(-dt * 16))
            let step = Int(floor(pose.c * 8))
            if step != lastStep {
                lastStep = step
                selection.selectionChanged()
                cue("rustle")
            }
        case .back:
            let n = max(1, Int(ceil(dt * 240)))
            for _ in 0..<n {
                (pose.x, vx) = M.springTo(pose.x, vx, geometry.hx, 0.5, 0.75, dt / Double(n))
                (pose.y, vy) = M.springTo(pose.y, vy, geometry.hy, 0.5, 0.75, dt / Double(n))
                let c = M.springTo(pose.c, vc, 0, 0.6, 0.6, dt / Double(n))
                pose.c = max(0, c.0)
                vc = c.1
            }
            pose.spin *= 1 - min(1, dt * 8)
            pose.lift *= 1 - min(1, dt * 10)
            if abs(pose.x - geometry.hx) + abs(pose.y - geometry.hy) < 0.4 && pose.c < 0.002 {
                mode = .rest
                pose.c = 0
                pose.x = geometry.hx
                pose.y = geometry.hy
            }
        case .gone:
            let tr = now - relAt
            pose = M.released(tr, rx, ry, rc, rspin, geometry)
            if reduced { pose.c = 1 }
            gone = tr >= 0.9
            if tr - dt < 0.28 && tr >= 0.28 { cue("toss") }
            if tr - dt < 0.9 && tr >= 0.9 {
                medium.impactOccurred()
                cue("bin")
            }
            if tr > 7 {
                mode = .undo
                undoAt = now
            }
        case .undo:
            let tu = now - undoAt
            pose = M.undone(tu, rspin, geometry)
            if reduced { pose.c = tu > 0.5 ? 0 : 1 }
            gone = tu < 0.3
            if tu - dt < 0.62 && tu >= 0.62 { cue("open") }
            if tu > 1.82 {
                mode = .rest
                pose.c = 0
                pose.spin = 0
                pose.sc = 1
                pose.x = geometry.hx
                pose.y = geometry.hy
            }
        case .rest: break
        }
    }
    func interrupt(_ geometry: M.Geo) {
        touchAt = now
        guard auto else { return }
        auto = false
        if t >= 6 && t < 8 {
            mode = .undo
            undoAt = now - (t - 6)
            rspin = 0.12
        } else if t >= 2.55 && t < 8 {
            mode = .gone
            relAt = now - (t - 2.55)
            rx = geometry.hx + 18
            ry = geometry.hy + 210
            rc = 0.7
            rspin = 0.12
        } else if t >= 1 && t < 8 {
            mode = .back
        } else {
            mode = .rest
        }
    }
    func startDrag() {
        if mode == .rest || mode == .back {
            mode = .drag
            sx = pose.x
            sy = pose.y
            lastStep = 0
        }
    }
    func move(_ translation: CGPoint, _ geometry: M.Geo) {
        touchAt = now
        guard mode == .drag else { return }
        sx = geometry.hx + translation.x
        sy = geometry.hy + max(-40, translation.y)
    }
    func end(_ velocity: Double) {
        touchAt = now
        guard mode == .drag else { return }
        if pose.c > 0.33 || velocity > 900 { release() } else { mode = .back }
    }
    func release() {
        mode = .gone
        relAt = now
        rx = pose.x
        ry = pose.y
        rc = pose.c
        rspin = pose.spin
    }
    func undo(_ geometry: M.Geo) {
        interrupt(geometry)
        if mode == .gone {
            mode = .undo
            undoAt = now
        }
        cue("pop")
    }
}
