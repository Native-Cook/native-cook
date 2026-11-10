import SwiftUI
import UIKit

final class SerumDropperRoutineDriver: ObservableObject {
    typealias M = SerumDropperRoutineMotion
    struct Tween {
        var from: Double, to: Double, start: Double, duration: Double
        var custom = false
        func value(_ now: Double) -> Double { from+(to-from)*M.timing((now-start)/duration,custom:custom) }
    }
    @Published var running = false
    @Published var manualMode = false
    @Published var ticks = [true,true,false,false]
    var frame = M.Frame()
    var elapsed: Double { now }
    private var previous: Date?
    private var now = 0.0, autoStart = 0.0, lastTouch = -100.0, lastDrop = -100.0
    private var manual = false, holding = false
    private var dropAt = [-100.0,-100.0,-100.0]
    private var nextDrop = 0
    private var tweens: [String:Tween] = [:]
    private let selection = UISelectionFeedbackGenerator()
    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let success = UINotificationFeedbackGenerator()
    private let sound = SerumDropperRoutineSound()
    func reset(_ auto: Bool, preserveTicks: Bool = false) {
        let otherTicks = preserveTicks ? Array(ticks.dropFirst()) : [true,false,false]
        previous = nil; now = 0; autoStart = 0; lastTouch = -100; lastDrop = -100
        manual = !auto; manualMode = !auto; holding = false; nextDrop = 0; dropAt = [-100,-100,-100]
        tweens.removeAll(); frame = M.Frame(); ticks = [true]+otherTicks; running = false
    }
    func active() { previous = nil }
    private func animate(_ key: String, _ from: Double, _ to: Double, _ duration: Double, custom: Bool = false) {
        tweens[key] = Tween(from:from,to:to,start:now,duration:duration,custom:custom)
    }
    private func takeOver() {
        lastTouch = now
        if manual { return }
        manual = true; manualMode = true
        animate("finger",frame.finger,0,0.15)
        frame.press = 0
        dropAt = frame.drops.map { $0 >= 0 ? now-$0 : -100 }
        lastDrop = now
        if !holding { animate("lift",frame.lift,0,0.5,custom:true) }
        animate("squeeze",frame.squeeze,0,0.2)
    }
    func begin(_ reduced: Bool) {
        takeOver(); holding = true
        if !frame.drops.contains(where: { $0 >= 0 }) { nextDrop = 0 }
        animate("lift",frame.lift,1,reduced ? 0.2 : 0.65,custom:true)
        lastDrop = now+(reduced ? 0 : 0.2)
        selection.selectionChanged(); sound.play(.tick); running = true; previous = nil
    }
    func end(_ reduced: Bool) {
        lastTouch = now; holding = false
        animate("squeeze",frame.squeeze,0,0.2)
        animate("lift",frame.lift,0,reduced ? 0.2 : 0.6,custom:true)
        running = true
    }
    func toggle(_ i: Int) {
        selection.selectionChanged(); sound.play(.tick); ticks[i].toggle()
        if i == 0 {
            takeOver()
            animate("done",frame.done,frame.done < 0.5 ? 1 : 0,0.3)
            running = true; previous = nil
        }
    }
    func sample(_ date: Date, auto: Bool, pinned: Double?) -> M.Frame {
        if let pinned { return M.at(pinned) }
        // The loop uses elapsed wall time even on the slow recording simulator.
        let dt = min(0.5,max(0,previous.map { date.timeIntervalSince($0) } ?? 0.016))
        previous = date; now += dt
        let oldDone = frame.done > 0.5
        if !manual && auto {
            frame = M.at((now-autoStart).truncatingRemainder(dividingBy:M.loop))
            if DemoSound.inAutoPlay {
                let before = now-autoStart-dt, after = now-autoStart
                let cues: [(Double,SerumDropperRoutineSound.Cue)] = [
                    (0.5,.tick),(1.2,.tick),
                    (2.1+M.grow+M.neck+0.25,.drop),
                    (2.9+M.grow+M.neck+0.25,.drop),
                    (3.7+M.grow+M.neck+0.25,.drop),(4.6,.done)
                ]
                for (time,cue) in cues where floor((before-time)/M.loop) < floor((after-time)/M.loop) {
                    sound.play(cue)
                }
            }
        } else {
            for (key,tween) in tweens {
                let v = tween.value(now)
                switch key {
                case "lift": frame.lift = v
                case "squeeze": frame.squeeze = v
                case "done": frame.done = v
                case "finger": frame.finger = v
                default: break
                }
            }
            tweens = tweens.filter { now < $0.value.start+$0.value.duration }
            if holding && frame.lift > 0.98 && nextDrop < 3 && now-lastDrop > 0.8 {
                dropAt[nextDrop] = now; nextDrop += 1; lastDrop = now; frame.soak = 0
            }
            let before = frame.drops.filter { $0 > M.grow+M.neck+0.25 }.count
            frame.drops = dropAt.map { $0 > -50 ? now-$0 : -1 }
            var pulse = 0.0
            for age in frame.drops where age >= 0 && age < M.grow+M.neck { pulse = max(pulse,sin(.pi*age/(M.grow+M.neck))) }
            if holding && frame.lift > 0.98 { frame.squeeze = 0.55+0.45*pulse; tweens["squeeze"] = nil }
            let last = frame.drops.filter { $0 >= 0 }.max() ?? -1
            if last > 2.2 { frame.soak = min(1,(last-2.2)/3.8) }
            if last > 6.4 { dropAt = [-100,-100,-100]; nextDrop = 0; frame.soak = 0 }
            let after = frame.drops.filter { $0 > M.grow+M.neck+0.25 }.count
            if after > before {
                soft.impactOccurred(); sound.play(.drop)
                if after == 1 && frame.done < 0.5 {
                    animate("done",frame.done,1,0.35); success.notificationOccurred(.success); sound.play(.done)
                }
            }
            if auto && !holding && now-lastTouch > 2.6 && frame.lift < 0.001 && nextDrop == 0 && frame.done > 0.999 {
                autoStart = now; manual = false
                DispatchQueue.main.async { [weak self] in self?.manualMode = false; self?.running = false }
            }
            if manual && !holding && tweens.isEmpty && nextDrop == 0 && frame.drops.allSatisfy({$0 < 0}) && (!auto || frame.done < 0.999) {
                if running { DispatchQueue.main.async { [weak self] in self?.running = false } }
            }
        }
        if oldDone != (frame.done > 0.5) {
            let checked = frame.done > 0.5
            DispatchQueue.main.async { [weak self] in self?.ticks[0] = checked }
        }
        return frame
    }
}
