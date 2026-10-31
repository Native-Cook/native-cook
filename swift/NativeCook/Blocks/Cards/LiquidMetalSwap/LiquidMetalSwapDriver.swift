import SwiftUI
import UIKit
import AVFAudio

final class LiquidMetalSwapAudio {
    private var players: [String: AVAudioPlayer] = [:]
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        for cue in ["press", "pour", "cast"] {
            if let url = Bundle.main.url(forResource: "LiquidMetalSwap-lms-\(cue)", withExtension: "wav"),
               let player = try? AVAudioPlayer(contentsOf: url) {
                player.volume = cue == "press" ? 0.5 : 0.7
                player.prepareToPlay(); players[cue] = player
            }
        }
    }
    func play(_ cue: String) { guard let player = players[cue] else { return }; player.currentTime=0; player.play() }
}

struct LiquidMetalSwapTween {
    var from = 0.0, to = 0.0, began = -100.0, duration = 0.0
    // 0: default in-out quadratic; 1: source EASE_OUT; 2: source EASE_IN_OUT
    var curve = 0
    func value(_ t: Double) -> Double {
        let x = LiquidMetalSwapMotion.seg(t,began,began+max(duration,0.00001))
        let p: Double
        if curve == 1 { p=LiquidMetalSwapMotion.bezier(x,0.23,1,0.32,1) }
        else if curve == 2 { p=LiquidMetalSwapMotion.bezier(x,0.77,0,0.175,1) }
        else { p=x < 0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2 }
        return from+(to-from)*p
    }
}

final class LiquidMetalSwapDriver: ObservableObject {
    typealias M = LiquidMetalSwapMotion
    @Published var running = false
    var frame = M.Frame()
    var clock = 0.0, shaderTime = 0.0, lift = 0.0
    var mode = 1, holding = false, turning = false
    var auto = false, reduced = false
    private var configured = false
    private var lastPin: Double?
    private var epoch = Date(), lastDate: Date?
    var autoEpoch: Double { autoStart }
    private var autoStart = 0.0, lastTouch = -100.0, resumeAsked = false
    private var yawVel = 0.0, prevX = 0.0
    private var press = LiquidMetalSwapTween(), flip = LiquidMetalSwapTween()
    private var finger = LiquidMetalSwapTween(), swapped = LiquidMetalSwapTween(), done = LiquidMetalSwapTween()
    private var completeAt = -100.0, returnAt = -100.0, returnFill = 0.0
    private var fillSpring: LiquidMetalSwapSpring?
    private var liftSpring: LiquidMetalSwapSpring?
    private var liftAt = -100.0
    private var accessAt = -100.0, accessFrom = 0.0
    private var wasUnder = false, priorAuto = -1.0
    private lazy var audio = LiquidMetalSwapAudio()

    func configure(autoPlay: Bool, reduced: Bool) {
        self.reduced=reduced; auto=autoPlay && !reduced
        if !reduced { _ = audio }
        if configured && !auto { mode=1; running=needsFrames; return }
        configured=true; mode=auto ? 0 : 1
        epoch=Date(); lastDate=nil; clock=0; autoStart=0; priorAuto = -1
        frame=M.Frame(); holding=false; turning=false; running = !reduced
    }
    func sample(_ date: Date, pin: Double?) -> M.Frame {
        lastPin=pin
        let dt=min(0.05,max(0,date.timeIntervalSince(lastDate ?? date)))
        lastDate=date
        clock=max(0,date.timeIntervalSince(epoch)); shaderTime=clock
        if mode == 0 {
            let t=pin ?? ((clock-autoStart).truncatingRemainder(dividingBy:M.loop)+M.loop).truncatingRemainder(dividingBy:M.loop)
            frame=M.frameAt(t)
            if let pin { shaderTime=pin }
            if DemoSound.inAutoPlay && pin == nil {
                if priorAuto >= 0 && t >= priorAuto {
                    for pressTime in [M.T.press1,M.T.press2,M.T.press3] where priorAuto < pressTime && t >= pressTime { audio.play("press") }
                    for doneTime in [M.T.press1+M.hold,M.T.press3+M.hold] where priorAuto < doneTime && t >= doneTime { audio.play("cast") }
                    if !wasUnder && M.look(frame.m).sink > 0.96 { audio.play("pour") }
                }
                priorAuto=t
            }
            wasUnder=M.look(frame.m).sink > 0.96
            return frame
        }
        if holding {
            frame.fill=min(1,frame.fill+dt/M.hold)
            if frame.fill >= 1 { complete() }
        } else if accessAt > -99 {
            frame.fill=accessFrom+(1-accessFrom)*M.bezier(M.seg(clock,accessAt,accessAt+0.9),0.77,0,0.175,1)
            if clock >= accessAt+0.9 { accessAt = -100; complete() }
        } else if returnAt > -99 {
            let tau=clock-returnAt
            frame.fill=reduced ? returnFill*(1-LiquidMetalSwapTween(from:0,to:1,began:0,duration:0.15).value(tau)) : (fillSpring?.value(tau) ?? 0)
            if frame.fill < 0.00001 { frame.fill=0; returnAt = -100 }
        }
        if !turning && !reduced {
            var v=yawVel*exp(-2.2*dt)
            if abs(v) < 2.5 { let aim=floor(frame.yaw/Double.pi+0.5)*Double.pi; v += (-14*(frame.yaw-aim)-5*v)*dt }
            yawVel=v; frame.yaw += v*dt
            let tau=max(0,clock-liftAt)
            lift=liftSpring?.value(tau) ?? 0
        }
        frame.press=press.value(clock); frame.flip=flip.value(clock); frame.finger=finger.value(clock)
        if completeAt > -99 {
            let tau=clock-completeAt
            frame.swapped=1-LiquidMetalSwapTween(from:0,to:1,began:1.1,duration:0.3).value(tau)
            frame.doneText=tau < 1.1 ? LiquidMetalSwapTween(from:0,to:1,began:0,duration:0.2).value(tau) : 1-LiquidMetalSwapTween(from:0,to:1,began:1.1,duration:0.3).value(tau)
        } else { frame.swapped=swapped.value(clock); frame.doneText=done.value(clock) }
        frame.m=reduced ? frame.start : (frame.start == 0 ? frame.fill : 1-frame.fill)
        let under=M.look(frame.m).sink > 0.96
        if under && !wasUnder && !reduced { audio.play("pour"); UIImpactFeedbackGenerator(style:.medium).impactOccurred() }
        wasUnder=under
        if auto && !resumeAsked && !holding && !turning && clock-lastTouch > 2 && frame.fill < 0.002 && abs(yawVel) < 0.05 {
            resumeAsked=true
            if frame.start == 1 { holding=true }
        } else if resumeAsked && !holding && frame.start == 0 && frame.fill < 0.002 && abs(frame.yaw-floor(frame.yaw/(2*Double.pi)+0.5)*2*Double.pi) < 0.02 {
            resumeAsked=false; frame.yaw=0; autoStart=clock; mode=0; priorAuto = -1
        }
        return frame
    }
    var needsFrames: Bool {
        !reduced || auto || holding || turning || accessAt > -99 || returnAt > -99 || (!reduced && (abs(yawVel) > 0.0001 ||
        abs(frame.yaw-floor(frame.yaw/Double.pi+0.5)*Double.pi) > 0.0001)) ||
        clock < completeAt+1.4 || clock < press.began+press.duration || clock < finger.began+finger.duration || clock < liftAt+1.4
    }
    func takeOver() {
        _ = sample(Date(),pin:lastPin)
        lastTouch=clock; resumeAsked=false
        if mode == 0 {
            mode=1; yawVel=0
            finger=LiquidMetalSwapTween(from:frame.finger,to:0,began:clock,duration:0.15)
            press=LiquidMetalSwapTween(from:0,to:0,began:clock,duration:0)
            swapped=LiquidMetalSwapTween(from:frame.swapped,to:0,began:clock,duration:0.2)
            done=LiquidMetalSwapTween(from:frame.doneText,to:0,began:clock,duration:0.2)
            flip=LiquidMetalSwapTween(from:frame.flip,to:frame.flip,began:clock,duration:0)
            completeAt = -100
        }
        running=true
    }
    private func complete() {
        holding=false; returnAt = -100; accessAt = -100
        frame.start=1-frame.start; frame.fill=0
        flip=LiquidMetalSwapTween(from:frame.flip,to:frame.start,began:clock,duration:0.35,curve:2)
        completeAt=clock
        if !reduced { audio.play("cast") }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }
    func beginHold() {
        takeOver(); returnAt = -100; accessAt = -100; holding=true
        press=LiquidMetalSwapTween(from:frame.press,to:1,began:clock,duration:0.1,curve:1)
        if !reduced { audio.play("press") }
        UIImpactFeedbackGenerator(style:.light).impactOccurred()
    }
    func endHold() {
        lastTouch=clock
        press=LiquidMetalSwapTween(from:frame.press,to:0,began:clock,duration:0.15,curve:1)
        if holding { holding=false; returnFill=frame.fill; returnAt=clock; fillSpring=LiquidMetalSwapSpring(displacement:frame.fill,velocity:1/M.hold,duration:0.6,damping:1) }
        running=true
    }
    func beginTurn() { takeOver(); turning=true; yawVel=0; prevX=0 }
    func turn(x: Double, y: Double) {
        lastTouch=clock
        let dx=x-prevX; prevX=x
        if !reduced { frame.yaw += dx/(78*0.9); lift=max(-0.12,min(0.12,-y/900)) }
    }
    func endTurn(velocity: Double) {
        lastTouch=clock; turning=false
        if !reduced { yawVel=max(-14,min(14,velocity/(78*0.9))); liftAt=clock; liftSpring=LiquidMetalSwapSpring(displacement:lift,duration:0.7,damping:0.8) }
        running=true
    }
    func swapNow() {
        takeOver(); holding=false; returnAt = -100
        if reduced { frame.fill=1; complete() }
        else { accessAt=clock; accessFrom=frame.fill }
    }
}
