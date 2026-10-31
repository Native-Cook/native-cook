import SwiftUI
import AVFoundation
import UIKit

final class GemCutPaywallDriver: ObservableObject {
    typealias M = GemCutPaywallMotion
    var origin = Date.timeIntervalSinceReferenceDate
    var autoPlay = false, reduced = false, manual = false, touching = false, sliding = false
    var lastTouch = -100.0, yawBase = 0.0, loopTime = 0.0
    var frame = M.Frame(), slideFrom = 0.0, previousTranslation = CGSize.zero
    var velocity = M.idleSpin, tickTime = Date.timeIntervalSinceReferenceDate
    var resume = false, fadeFinger = 0.0, fingerFadeStart = -100.0, frozenFingerCut = 0.0
    var spinning = false
    struct Move { var start: Double, duration: Double, from: Double, to: Double, velocity: Double, spring: Bool, damping: Double = 0.8 }
    var cutMove: Move?, pitchMove: Move?
    var flareTime = -100.0, flareAmp = 0.0, flareRise = 0.12, flareFall = 1.3
    var tier = 0, previousTier = 0, tierTime = -100.0, digitFrom = [0.0,0.0,0.0,0.0]
    var opacityFrom = [1.0,0,0], opacityTo = [1.0,0,0], opacityStart = [-100.0,-100,-100]
    var ctaFrom = 1.0, ctaTo = 1.0, ctaTime = -100.0
    func opacity(_ i: Int, _ now: Double) -> Double {
        let k=M.bezier(M.clamp01((now-opacityStart[i])/0.2),0.23,1,0.32,1)
        return opacityFrom[i]+(opacityTo[i]-opacityFrom[i])*k
    }
    func freeOpacity(_ now: Double) -> Double {
        let k=M.bezier(M.clamp01((now-ctaTime)/0.2),0.23,1,0.32,1)
        return ctaFrom+(ctaTo-ctaFrom)*k
    }
    func retargetOpacity(_ next: Int, _ now: Double) {
        for i in 0..<3 {
            let to = i == next ? 1.0 : 0
            if to != opacityTo[i] { opacityFrom[i]=opacity(i,now); opacityTo[i]=to; opacityStart[i]=now }
        }
        let to = next == 0 ? 1.0 : 0
        if to != ctaTo { ctaFrom=freeOpacity(now); ctaTo=to; ctaTime=now }
    }
    var players: [String: AVAudioPlayer] = [:]
    var previousAutoTime = -1.0
    @Published var reducedAnimating = false
    var animationDeadline = 0.0
    var suspendedAt: Double?
    var isMoving: Bool { touching || reducedAnimating }
    func wake(_ duration: Double) {
        guard reduced else { return }
        reducedAnimating=true; animationDeadline=Date.timeIntervalSinceReferenceDate+duration
        DispatchQueue.main.asyncAfter(deadline:.now()+duration+0.05) { [weak self] in
            guard let self, Date.timeIntervalSinceReferenceDate>=self.animationDeadline else { return }
            self.reducedAnimating=false
        }
    }
    func suspend(_ active: Bool) {
        let now=Date.timeIntervalSinceReferenceDate
        if !active { if suspendedAt == nil { suspendedAt=now }; return }
        if let before=suspendedAt {
            let gap=now-before; origin += gap; lastTouch += gap; flareTime += gap; tierTime += gap
            cutMove?.start += gap; pitchMove?.start += gap; tickTime=now; suspendedAt=nil
        }
    }
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:[.mixWithOthers])
        for cue in ["tick","ting","shimmer"] {
            if let url = Bundle.main.url(forResource:"GemCutPaywall-gcp-\(cue)",withExtension:"wav"), let p = try? AVAudioPlayer(contentsOf:url) {
                p.volume = cue == "tick" ? 0.5 : 0.7; p.prepareToPlay(); players[cue]=p
            }
        }
    }
    func cue(_ name: String) { guard !reduced else { return }; players[name]?.currentTime=0; players[name]?.play() }
    func land(_ i: Int, _ now: Double) {
        if !reduced { flareTime=now; flareAmp=[0.2,0.45,1][i]; flareRise=0.12; flareFall=1.3 }
        cue(i == 2 ? "shimmer" : "ting")
        if i == 2 { UINotificationFeedbackGenerator().notificationOccurred(.success) } else { UIImpactFeedbackGenerator(style:.light).impactOccurred() }
    }
    func configure(_ auto: Bool, _ reduce: Bool) {
        guard autoPlay != auto || reduced != reduce else { return }
        autoPlay=auto; reduced=reduce; manual = !auto || reduce; yawBase=frame.yaw; origin=Date.timeIntervalSinceReferenceDate; previousAutoTime = -1
        if reduce { velocity=0; frame.finger=0; frame.spark=0; frame.pitch=M.pitch }
    }
    func moveValue(_ m: Move, _ now: Double) -> Double {
        let t=max(0,now-m.start)
        if m.spring { return M.spring(t,m.duration,m.damping,m.from,m.to,m.velocity) }
        let progress=M.clamp01(t/m.duration)
        return m.from+(m.to-m.from)*(reduced ? M.easeQuadratic(progress) : M.bezier(progress))
    }
    func sample(_ now: Double, pin: Double? = nil) -> M.Frame {
        let dt=max(0,min(0.05,now-tickTime)); tickTime=now
        if autoPlay && !reduced && !manual {
            let t = pin ?? (now-origin).truncatingRemainder(dividingBy:M.loop)
            loopTime=t; frame=M.frameAt(t); frame.yaw += yawBase
            if DemoSound.inAutoPlay && pin == nil {
                for (moment,cueName) in [(2.3,"shimmer"),(6.38,"ting"),(8.18,"ting")] where previousAutoTime < moment && t >= moment { cue(cueName) }
                if previousAutoTime >= 0 {
                    let prior=M.frameAt(previousAutoTime).cut
                    if Int((prior*2).rounded()) != Int((frame.cut*2).rounded()) && t < 2.3 { cue("tick") }
                }
            }
            previousAutoTime=t
        } else {
            if !spinning {
                let target = reduced ? 0 : M.idleSpin
                velocity=target+(velocity-target)*exp(-1.4*dt); frame.yaw += velocity*dt
            }
            if let m=cutMove {
                frame.cut=moveValue(m,now)
                if (m.spring && frame.cut == m.to) || (!m.spring && now-m.start >= m.duration) { frame.cut=m.to; cutMove=nil; if !resume { land(Int((m.to*2).rounded()),now) } }
            }
            if let m=pitchMove {
                frame.pitch=moveValue(m,now)
                if (m.spring && frame.pitch == m.to) || (!m.spring && now-m.start >= m.duration) { frame.pitch=m.to; pitchMove=nil }
            }
            let f=now-flareTime
            frame.spark = reduced ? 0 : flareAmp * (f < flareRise ? M.bezier(M.clamp01(f/flareRise),0.23,1,0.32,1) : 1-M.bezier(M.clamp01((f-flareRise)/flareFall),0.23,1,0.32,1))
            frame.finger=fadeFinger*(1-M.easeQuadratic(M.clamp01((now-fingerFadeStart)/0.15))); frame.press=0
            if autoPlay && !reduced && !touching && now-lastTouch>2 && !resume {
                resume=true; cutMove=Move(start:now,duration:0.5+0.7*frame.cut,from:frame.cut,to:0,velocity:0,spring:false)
                pitchMove=Move(start:now,duration:0.6,from:frame.pitch,to:M.pitch,velocity:0,spring:true,damping:0.9)
            } else if resume && frame.cut<0.002 && abs(velocity-M.idleSpin)<0.02 {
                resume=false; manual=false; origin=now; yawBase=frame.yaw; cutMove=nil; previousAutoTime = -1
            }
        }
        let next=max(0,min(2,Int((frame.cut*2).rounded())))
        if next != tier {
            digitFrom=digits(now); retargetOpacity(next,now); previousTier=tier; tier=next; tierTime=now
            if manual && sliding { cue("tick"); UISelectionFeedbackGenerator().selectionChanged() }
        }
        return frame
    }
    func digits(_ now: Double) -> [Double] {
        let chars=Array(GemCutPaywallTheme.prices[tier])
        return chars.enumerated().map { i,c in
            let to=Double(String(c)) ?? 0
            let k = reduced ? 1 : M.bezier(M.clamp01((now-tierTime)/(0.32+Double(i)*0.04)),0.23,1,0.32,1)
            return digitFrom[i]+(to-digitFrom[i])*k
        }
    }
    func takeover(_ now: Double) {
        _=sample(now); lastTouch=now; resume=false
        if !manual { manual=true; velocity=reduced ? 0 : M.idleSpin; fadeFinger=frame.finger; fingerFadeStart=now; frozenFingerCut=frame.cut }
        objectWillChange.send()
    }
    func go(_ i: Int, _ now: Double, _ v: Double = 0, glide: Bool = true) {
        wake(0.6)
        let to=Double(i)/2
        cutMove=Move(start:now,duration:reduced ? 0.15 : glide ? 0.36+0.68*abs(to-frame.cut) : 0.42,from:frame.cut,to:to,velocity:v,spring:!reduced && !glide)
    }
    func touch(_ state: UIGestureRecognizer.State, _ point: CGPoint, _ translation: CGSize, _ v: CGVector, _ plan: Bool, _ l: GemCutPaywallTheme.Layout) {
        let now=Date.timeIntervalSinceReferenceDate
        if state == .began {
            takeover(now); touching=true; spinning = !plan; wake(0.6); slideFrom=frame.cut; previousTranslation = .zero
            if plan { cutMove=nil } else { velocity=0; pitchMove=nil }
        } else if state == .changed {
            lastTouch=now
            if plan {
                if abs(translation.width)>6 { sliding=true }
                if sliding {
                    let raw=slideFrom+translation.width/l.thumbRun
                    let over=raw<0 ? raw : raw>1 ? raw-1 : 0
                    frame.cut=raw-over+over*0.18
                }
            } else if !reduced {
                frame.yaw += (translation.width-previousTranslation.width)/(l.gemR*0.9)
                frame.pitch=max(0.12,min(0.95,frame.pitch+(translation.height-previousTranslation.height)/(l.gemR*2.4)))
            }
            previousTranslation=translation
        } else if state == .ended || state == .cancelled || state == .failed {
            lastTouch=now; touching=false; spinning=false
            if plan {
                let speed=v.dx/l.thumbRun
                let i=sliding ? Int(((frame.cut+speed*0.18)*2).rounded()) : Int(floor((point.x-4)/l.segW))
                go(max(0,min(2,i)),now,speed,glide:!sliding); sliding=false
            } else {
                velocity=reduced ? 0 : max(-9,min(9,v.dx/(l.gemR*0.9)))
                if !reduced { pitchMove=Move(start:now,duration:0.7,from:frame.pitch,to:M.pitch,velocity:v.dy/(l.gemR*2.4),spring:true,damping:0.7) }
                if hypot(translation.width,translation.height)<8 {
                    if !reduced { flareTime=now; flareAmp=0.6; flareRise=0.1; flareFall=1 }
                    cue("ting"); UIImpactFeedbackGenerator(style:.light).impactOccurred()
                }
            }
            objectWillChange.send()
            DispatchQueue.main.asyncAfter(deadline:.now()+1.5) { [weak self] in self?.objectWillChange.send() }
        }
    }
}
