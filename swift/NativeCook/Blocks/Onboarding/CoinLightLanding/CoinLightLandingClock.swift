import SwiftUI
import UIKit
import AVFoundation

final class CoinLightLandingClock: ObservableObject {
    typealias M = CoinLightLandingMotion
    @Published var running = false
    @Published var overviewPressed = false
    var f = M.Frame()
    private var date: Date?
    private var now = 0.0, t = 0.0, last = -10.0
    private var vx = 0.0, vy = 0.0, vyaw = 0.0
    private var held = false, mode = 0, at = 0.0, gx = 0.0, gy = 0.0
    private var payAt = -10.0, payFrom = M.base
    private var players: [String:AVAudioPlayer] = [:]
    private var reduced = false
    private let light = UIImpactFeedbackGenerator(style:.light)
    private let medium = UIImpactFeedbackGenerator(style:.medium)
    private let success = UINotificationFeedbackGenerator()
    func active(_ active: Bool,_ reduce: Bool) {
        date=nil; reduced=reduce
        if !active || reduce {
            for p in players.values { p.stop() }
            if reduce { players.removeAll() }
            return
        }
        guard !reduce, players.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
        for name in ["hum","spin","snap"] {
            if let url=Bundle.main.url(forResource:"CoinLightLanding-cll-\(name)",withExtension:"wav"),let p=try? AVAudioPlayer(contentsOf:url) {
                p.volume=name == "hum" ? 0.45 : 0.75; p.prepareToPlay(); players[name]=p
            }
        }
    }
    private func cue(_ name: String) {
        guard !reduced,let p=players[name] else { return }
        p.currentTime=0; p.play()
    }
    private func buzz(_ kind: Int) {
        if kind==2 { success.notificationOccurred(.success) }
        else if kind==1 { medium.impactOccurred() }
        else { light.impactOccurred() }
    }
    func sample(_ date: Date,_ size: CGSize,_ top: Double,_ auto: Bool,_ reduced: Bool,_ pinned: Double?) -> M.Frame {
        if let pinned { return M.at(pinned,size.width,size.height,top) }
        let elapsed=min(0.5,max(0,self.date.map { date.timeIntervalSince($0) } ?? 0.016))
        self.date=date; now += elapsed
        let dt=min(elapsed,0.05)
        if f.x==0 && f.y==0 { f.x=size.width/2; f.y=size.height*0.74 }
        if auto && !reduced && !held && mode==0 && now-last>=2 {
            let before=t; t=(t+elapsed).truncatingRemainder(dividingBy:M.loop)
            if DemoSound.inAutoPlay {
                for (beat,name) in [(1.0,"hum"),(3.3,"snap"),(5.8,"spin")] {
                    if (t>=before && before<beat && t>=beat) || (t<before && (before<beat || t>=beat)) { cue(name) }
                }
            }
            f=M.at(t,size.width,size.height,top)
        } else {
            let restX=size.width/2,restY=size.height*0.74,by=top+354
            if mode==1 {
                let k=M.easeOut((now-at)/0.3)
                f.x=M.mix(f.x,104,0.35); f.y=M.mix(f.y,by,0.35)
                f.s=M.mix(1,0.3,k); f.a=1-k
                if now-at>1.3 { mode=2; at=now; f.x=restX; f.y=size.height+140; f.a=1; f.s=1 }
            } else if mode==2 {
                f.y=M.mix(size.height+140,restY,M.easeOutBack((now-at)/0.8))
                if now-at>=0.8 { mode=0 }
            } else if !held {
                let k=reduced ? 300.0 : 90.0, damping=2*sqrt(k)*0.62
                vx += (k*(restX-f.x)-damping*vx)*dt
                vy += (k*(restY-f.y)-damping*vy)*dt
                f.x += vx*dt; f.y += vy*dt
            }
            vyaw *= exp(-dt*1.6); f.yaw += vyaw*dt
            if abs(vyaw)<0.6 { let target=floor(f.yaw/(2 * .pi)+0.5)*2 * .pi; f.yaw += (target-f.yaw)*(1-exp(-dt*4)) }
            f.tx += (max(-0.5,min(0.5,vx*0.0012))-f.tx)*(1-exp(-dt*10))
            f.pay=payAt>0 ? M.mix(payFrom,payFrom+48,M.easeInOut(now-payAt-0.1)) : f.pay
            f.button=payAt>0 ? exp(-max(0,now-payAt-0.1)*1.6) : 0
            f.t=t
            if !held && mode==0 && abs(vx)+abs(vy)+abs(vyaw)<0.01 && abs(f.x-restX)+abs(f.y-restY)<0.01 && f.button<0.0001 && (!auto || reduced) && running {
                DispatchQueue.main.async { [weak self] in self?.running=false }
            }
        }
        return f
    }
    func touch(_ state: UIGestureRecognizer.State,_ p: CGPoint,_ velocity: CGPoint,_ size: CGSize,_ top: Double) {
        if state == .began {
            last=now; t=0; running=true; date=nil
            guard mode==0,hypot(p.x-f.x,p.y-f.y)<=100 else { return }
            held=true; gx=f.x-p.x; gy=f.y-p.y; cue("hum"); buzz(0)
        } else if state == .changed && held {
            last=now
            let nx=p.x+gx,ny=p.y+gy
            vx=vx*0.5+(nx-f.x)*30; f.x=nx; f.y=ny
        } else if (state == .ended || state == .cancelled || state == .failed) && held {
            last=now; held=false
            if abs(f.x-104)<92 && abs(f.y-(top+354))<50 { drop(); return }
            vx=velocity.x; vy=velocity.y
            if !reduced && abs(vx)>500 { vyaw=vx*0.012; cue("spin"); buzz(1) }
        }
    }
    private func drop() {
        mode=1; at=now; payFrom=f.pay; payAt=now
        cue("snap"); buzz(2)
    }
    func become() {
        last=now; t=0; date=nil; running=true
        if mode==0 { drop() }
    }
    func overview() { buzz(0) }
}
