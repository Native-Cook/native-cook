import AVFoundation
import SwiftUI
import UIKit

final class GlassPiggySavingsClock: ObservableObject {
    typealias M=GlassPiggySavingsMotion
    @Published var revision=0
    @Published var running=false
    private var frame=M.Frame()
    private var date: Date?
    private var idleAt: Date?
    private var players: [String: AVAudioPlayer]=[:]
    private let light=UIImpactFeedbackGenerator(style:.light)
    private let success=UINotificationFeedbackGenerator()
    private var last = -10.0
    private var active=false
    private var automatic=false
    var moving: Bool {
        frame.held.on || frame.now-frame.held.dropAt<0.3 || frame.now-frame.held.backAt<0.45 ||
        frame.now-frame.bonusAt<2.4 || abs(frame.pig.sq)+abs(frame.pig.sv)+abs(frame.pig.wob)+abs(frame.pig.wv)>0.00001
    }
    func configure(_ active: Bool, _ reduced: Bool) {
        self.active=active; date=nil
        idleAt=active && !reduced ? Date() : nil
        if active && !reduced && players.isEmpty {
            try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
            for name in ["tap","wiggle","clink"] {
                if let url=Bundle.main.url(forResource:"GlassPiggySavings-gps-\(name)",withExtension:"wav"),
                   let player=try? AVAudioPlayer(contentsOf:url) {
                    player.volume=name=="tap" ? 0.5 : 0.85; player.prepareToPlay(); players[name]=player
                }
            }
        }
        if !active || reduced { players.values.forEach { $0.stop() } }
    }
    private func cue(_ name: String, _ reduced: Bool) {
        guard !reduced, active, let p=players[name] else { return }; p.currentTime=0; p.play()
    }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Frame {
        if let pinned { return M.replay(pinned) }
        automatic=auto
        guard active && !reduced else { return frame }
        // The loop runs on wall-clock time (a slow recorder must not stretch it); springs take a clamped step.
        let raw=max(0,date.map { at.timeIntervalSince($0) } ?? 0.016), dt=min(raw,0.05)
        date=at; idleAt=at; frame.now += dt
        let touched=frame.held.on || frame.now-last<2
        if auto && !touched {
            let before=frame.t; frame.t += min(raw,0.5)
            if frame.t>=M.loop { frame.t -= M.loop }
            if before<2 && frame.t>=2 { frame.pig.sv -= 0.7 }
            if before<2.5 && frame.t>=2.5 { frame.pig.sv -= 2.2; frame.plus=20 }
            // The coin finishes sinking into the slot at 2.5 s.
            if DemoSound.inAutoPlay {
                let beat=2.5
                let crossed=before <= frame.t
                    ? before < beat && frame.t >= beat : before < beat || frame.t >= beat
                if crossed { cue("clink",reduced) }
            }
        }
        let hd=frame.held
        if hd.dropAt>0 && frame.now-hd.dropAt>=0.23 && frame.now-hd.dropAt-dt<0.23 {
            frame.pig.sv -= 2.2; frame.bonus += M.chips[hd.chip]; frame.bonusAt=frame.now
            frame.plus=M.chips[hd.chip]; cue("clink",reduced); success.notificationOccurred(.success)
        }
        frame.pig.step(dt)
        if !auto && !moving && running {
            DispatchQueue.main.async { [weak self] in
                guard let self, !self.moving else { return }
                self.running=false
            }
        }
        return frame
    }
    private func changed() { revision += 1 }
    private func touched(_ reduced: Bool) {
        if !automatic && !running && active && !reduced, let stamp=date ?? idleAt {
            frame.now += max(0,Date().timeIntervalSince(stamp)); date=nil; idleAt=nil
        }
        running = !reduced
        last=frame.now
    }
    func touch(_ state: UIGestureRecognizer.State, _ p: CGPoint, _ l: M.Layout, _ reduced: Bool) {
        if state == .began {
            touched(reduced)
            for i in 0..<3 where abs(p.x-l.chipX(i))<52 && abs(p.y-l.chipY)<30 {
                frame.held.on=true; frame.held.x=p.x; frame.held.y=p.y-30; frame.held.chip=i
                light.impactOccurred(); changed(); return
            }
            if p.x>l.pigX && p.x<l.pigX+340 && p.y>l.pigY && p.y<l.pigY+800*M.scale {
                frame.pig.wv += 3.2; cue("wiggle",reduced); light.impactOccurred()
            }
        } else if state == .changed {
            if frame.held.on { touched(reduced); frame.held.x=p.x; frame.held.y=p.y-30 }
        } else if state == .ended || state == .cancelled || state == .failed {
            guard frame.held.on else { return }
            touched(reduced); let x=p.x,y=p.y-30
            let over=abs(x-l.slotX)<120 && y>l.pigY-80 && y<l.pigY+160
            frame.held.on=false
            if over && !reduced {
                frame.held.dropAt=frame.now; frame.held.fromX=x; frame.held.fromY=min(y,l.slotY-30)
            } else if over {
                frame.bonus += M.chips[frame.held.chip]; frame.bonusAt=frame.now
                frame.plus=M.chips[frame.held.chip]; success.notificationOccurred(.success)
            } else {
                frame.held.backAt=frame.now; frame.held.fromX=x; frame.held.fromY=y
            }
        }
        changed()
    }
    func drop(_ i: Int, _ l: M.Layout, _ reduced: Bool) {
        touched(reduced)
        if reduced || !active {
            frame.bonus += M.chips[i]; frame.bonusAt=frame.now; frame.plus=M.chips[i]
        } else {
            frame.held.on=false; frame.held.chip=i; frame.held.dropAt=frame.now
            frame.held.fromX=l.slotX; frame.held.fromY=l.slotY-90
        }
        changed()
    }
}
