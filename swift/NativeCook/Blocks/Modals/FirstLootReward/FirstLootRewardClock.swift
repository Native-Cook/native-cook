import AVFoundation
import CoreMotion
import SwiftUI
import UIKit

final class FirstLootRewardClock: ObservableObject {
    typealias M = FirstLootRewardMotion
    @Published var revision = 0
    @Published var sleeping = false
    private var idleAt:Date?
    private var idleWake:DispatchWorkItem?
    private var buttonDown = false
    private var sleepRequested = false
    private var date:Date?
    private var now=0.0, t=0.0, last = -10.0, pressAt = -1.0, openAt = -10.0, power=1.0
    private var x=M.Spring(), y=M.Spring(), keep=M.Spring(), view=M.Spring()
    private let motion=CMMotionManager()
    private var initialized=false, pitch=0.0, roll=0.0, gx=0.0, gy=0.0
    private var players:[String:AVAudioPlayer]=[:]
    private let light=UIImpactFeedbackGenerator(style:.light)
    private let success=UINotificationFeedbackGenerator()
    private var reduced=false
    private var enabled=false
    var running:Bool { buttonDown || pressAt >= 0 || now-last < 2 || abs(keep.sample(now).0)>0.00001 || abs(view.sample(now).0)>0.00001 }
    func active(_ enabled:Bool,_ reduced:Bool,_ pinned:Bool) {
        cancelIdle(advance:false); date=nil; self.reduced=reduced; self.enabled=enabled
        if enabled && !reduced && !pinned {
            if motion.isDeviceMotionAvailable && !motion.isDeviceMotionActive { motion.deviceMotionUpdateInterval=1/60; motion.startDeviceMotionUpdates() }
            if players.isEmpty {
                try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
                for name in ["pop","open","tap"] {
                    if let url=Bundle.main.url(forResource:"FirstLootReward-flr-\(name)",withExtension:"wav"), let p=try? AVAudioPlayer(contentsOf:url) {
                        p.volume=name == "tap" ? 0.5 : 0.85; p.prepareToPlay(); players[name]=p
                    }
                }
            }
        } else {
            motion.stopDeviceMotionUpdates(); for p in players.values { p.stop() }
        }
    }
    deinit { motion.stopDeviceMotionUpdates() }
    func reset() { cancelIdle(advance:false); date=nil; t=0; revision += 1 }
    private func cancelIdle(advance:Bool) {
        if advance, let idleAt {
            let elapsed=max(0,Date().timeIntervalSince(idleAt))
            now += elapsed; t=min(3.55,t+elapsed)
        }
        idleWake?.cancel(); idleWake=nil; idleAt=nil; sleepRequested=false
        if sleeping { sleeping=false }
        date=Date()
    }
    private func restIfPossible(_ auto:Bool) {
        guard auto, !motion.isDeviceMotionAvailable, M.resting(t), now-last >= 2,
            pressAt < 0, now-openAt >= 1.7, !buttonDown, !sleepRequested else { return }
        sleepRequested=true
        DispatchQueue.main.async { [weak self] in
            guard let self, self.sleepRequested else { return }
            self.idleAt=Date(); self.sleeping=true
            let work=DispatchWorkItem { [weak self] in
                guard let self else { return }
                self.cancelIdle(advance:true); self.revision += 1
            }
            self.idleWake=work
            DispatchQueue.main.asyncAfter(deadline:.now()+max(0,3.55-self.t),execute:work)
        }
    }
    private func cue(_ name:String) { if !reduced, let p=players[name] { p.currentTime=0; p.play() } }
    func sample(_ at:Date,_ auto:Bool,_ pinned:Double?) -> M.Frame {
        if let pinned { return M.at(pinned) }
        if reduced || !enabled { return M.at(t) }
        // The loop runs on wall-clock time (a slow recorder must not stretch it); springs take a clamped step.
        let raw=max(0,date.map { at.timeIntervalSince($0) } ?? 0.016), dt=min(raw,0.05); date=at; now += dt
        let r=motion.deviceMotion?.attitude, cp=r?.pitch ?? 0, cr=r?.roll ?? 0
        if !initialized && (cp != 0 || cr != 0) { initialized=true; pitch=cp; roll=cr }
        let kb=min(1,dt*0.6); pitch += (cp-pitch)*kb; roll += (cr-roll)*kb
        let ks=1-exp(-dt*10)
        gx += (max(-1,min(1,(cr-roll)/0.35))-gx)*ks; gy += (max(-1,min(1,(cp-pitch)/0.35))-gy)*ks
        let touched=now-last < 2 || pressAt >= 0
        let before=t
        if auto && !(touched && M.resting(t)) { t += min(raw,0.5); if t >= M.loop { t -= M.loop } }
        // The chest bursts 0.25 s after its gather begins at 0.48 s.
        if DemoSound.inAutoPlay && auto && !touched && !buttonDown {
            let beat=0.48+0.25
            let crossed=before <= t ? before < beat && t >= beat : before < beat || t >= beat
            if crossed { cue("open") }
        }
        var f=M.at(t)
        let oa=now-openAt
        if pressAt >= 0 {
            let held=now-pressAt
            f.chest=M.chest(min(held,0.24)); f.charge=M.clamp((held-0.24)/1.1); f.press=min(held/0.12,1)
            if held > 0.24 { f.chest.gather=1+sin(held*46)*(0.06+0.08*f.charge) }
        } else if oa >= 0 && oa < 1.7 { f.chest=M.chest(0.25+oa); f.power=power }
        f.tiltX=max(-1,min(1,x.sample(now).0+gx*0.6)); f.tiltY=max(-1,min(1,y.sample(now).0+gy*0.6))
        f.keepPress=keep.sample(now).0; f.viewPress=view.sample(now).0
        restIfPossible(auto)
        return f
    }
    func begin(_ target:Int) {
        cancelIdle(advance:true)
        if target == 1 {
            guard !reduced && M.resting(t) else { return }
            last=now; pressAt=now; light.impactOccurred()
        } else if target == 2 { buttonDown=true; keep.aim(1,now,0.16,1) }
        else if target == 3 { buttonDown=true; view.aim(1,now,0.16,1) }
        revision += 1
    }
    func move(_ dx:Double,_ dy:Double) {
        guard pressAt >= 0 else { return }; last=now
        x.set(max(-1,min(1,dx/90))); y.set(max(-1,min(1,dy/90)))
    }
    func end(_ target:Int,_ ok:Bool) {
        guard target != 0 else { return }
        buttonDown=false
        DispatchQueue.main.asyncAfter(deadline:.now()+1.8) { [weak self] in self?.revision += 1 }
        if target == 1 {
            guard pressAt >= 0 else { return }
            power=1+0.6*M.clamp((now-pressAt-0.21)/1.1); pressAt = -1; openAt=now; last=now
            x.aim(0,now,0.6,0.55); y.aim(0,now,0.6,0.55); cue("open"); success.notificationOccurred(.success)
        } else {
            if target == 2 { keep.aim(0,now,0.32,0.7) }
            if target == 3 { view.aim(0,now,0.32,0.7) }
            guard ok else { revision += 1; return }
            cue("tap")
            if target == 2 {
                light.impactOccurred()
                if !reduced && M.resting(t) { t=3.55; last = -10 }
            } else if target == 3 {
                if !reduced && M.resting(t) { power=1; openAt=now; last=now; cue("open"); success.notificationOccurred(.success) }
                else { light.impactOccurred() }
            }
        }
        revision += 1
    }
    func activate(_ target:Int) { begin(target); end(target,true) }
}
