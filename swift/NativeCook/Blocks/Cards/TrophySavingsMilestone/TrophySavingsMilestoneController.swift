import Foundation
import Observation
import UIKit

@MainActor @Observable
final class TrophySavingsMilestoneController {
    struct Snapshot { var progress: Double; var pose: Double; var scene: TrophySavingsMilestoneCelebration
        static let zero = Self(progress:0,pose:0,scene:TrophySavingsMilestoneCelebration())
    }
    var motion = TrophySavingsMilestoneMotion()
    var autoPlay = false
    var previewTime: Double?
    var reduced = false
    var active = true
    var overlay = false
    var reducedFeedback = 0
    private var anchor = TrophySavingsMilestoneCelebration()
    private var sceneEpoch = Date().timeIntervalSince1970
    private var paused: Snapshot?
    private var task: Task<Void,Never>?
    private var impact: UIImpactFeedbackGenerator?
    var ticking: Bool { active && !overlay && !reduced && previewTime == nil && (motion.mode == .loop || motion.mode == .event || motion.mode == .drag) }
    func configure(autoPlay: Bool, previewTime: Double?, reduced: Bool, active: Bool) {
        self.autoPlay = autoPlay; self.previewTime = previewTime; self.reduced = reduced; self.active = active; reset()
    }
    func reset() {
        task?.cancel(); task = nil; let token = motion.generation+1, now = Date().timeIntervalSince1970
        motion = TrophySavingsMilestoneMotion(); motion.generation = token; motion.epoch = now
        motion.mode = autoPlay && active && !reduced && previewTime == nil && !overlay ? .loop : .idle
        anchor = TrophySavingsMilestoneCelebration(); anchor.reduced = reduced; sceneEpoch = now; paused = nil
    }
    func frame(at date: Date) -> Snapshot {
        if reduced || !active { var zero = Snapshot.zero; zero.scene.reduced = reduced; return zero }
        if let previewTime { return Snapshot(progress:TrophySavingsMilestoneMotion.loopProgress(previewTime),pose:TrophySavingsMilestoneMotion.loopPose(previewTime),scene:.gallery(previewTime,generation:motion.generation)) }
        if let paused { return paused }
        let now = date.timeIntervalSince1970
        if motion.mode == .loop { let t = now-motion.epoch; return Snapshot(progress:TrophySavingsMilestoneMotion.loopProgress(t),pose:TrophySavingsMilestoneMotion.loopPose(t),scene:.gallery(t,generation:motion.generation)) }
        if motion.mode == .idle || motion.mode == .waiting { return .zero }
        let pose = motion.presentedPose(at:now), p = pose <= 1 ? pose : 2-pose
        var scene = anchor; scene.clock = max(anchor.clock,now-sceneEpoch); scene.progress = p; scene.generation = motion.generation; scene.active = true
        if motion.mode == .event && motion.eventDemo {
            let opening = TrophySavingsMilestoneMotion.openDuration(motion.eventOrigin)
            // Analytical latches retain every forward crossing throughout recoil and its effect tail.
            let reached = now >= motion.eventStarted+opening ? 1 : p
            func crossing(_ threshold: Double) -> Double {
                var low = 0.0, high = opening
                for _ in 0..<18 { let mid = (low+high)*0.5; let q = motion.eventOrigin+(1-motion.eventOrigin)*TrophySavingsMilestoneMotion.smooth(mid/opening)
                    if q < threshold { low = mid } else { high = mid }
                }; return motion.eventStarted-sceneEpoch+(low+high)*0.5
            }
            for i in 0..<18 where scene.wakeStarts[i] < 0 && reached >= TrophySavingsMilestoneCelebration.thresholds[i] && TrophySavingsMilestoneCelebration.thresholds[i] > motion.eventOrigin {
                scene.wakeStarts[i] = crossing(TrophySavingsMilestoneCelebration.thresholds[i])
            }
            if scene.contactStart < 0 && reached >= 0.88 && motion.eventOrigin < 0.88 { scene.contactStart = crossing(0.88) }
        }
        return Snapshot(progress:p,pose:pose,scene:scene)
    }
    private func preserve(_ snapshot: Snapshot, at now: Double) { anchor = snapshot.scene; sceneEpoch = now-anchor.clock }
    func demonstrate() {
        guard active, !overlay, previewTime == nil else { return }
        if reduced { reducedFeedback += 1; return }
        let now = Date().timeIntervalSince1970
        if motion.mode == .event && motion.eventDemo && now-motion.eventStarted < motion.peakTime+TrophySavingsMilestoneMotion.peakHold { return }
        let snapshot = frame(at:Date()); preserve(snapshot,at:now)
        if snapshot.progress <= 0.000001 && snapshot.scene.settled { anchor = TrophySavingsMilestoneCelebration(); sceneEpoch = now }
        motion.begin(progress:snapshot.progress,demo:true,userPeak:true,now:now); paused = nil; scheduleFinish()
    }
    func drag(translation: Double, velocity: Double) {
        guard active, !overlay, !reduced, previewTime == nil else { return }
        let now = Date().timeIntervalSince1970
        if motion.mode != .drag {
            let snapshot = frame(at:Date()); task?.cancel(); task = nil; preserve(snapshot,at:now)
            if snapshot.progress <= 0.000001 && snapshot.scene.settled { anchor = TrophySavingsMilestoneCelebration(); sceneEpoch = now }
            var pose = snapshot.pose; if snapshot.progress <= 0.000001 && pose >= 1.999999 { pose = 0 }
            motion.generation += 1; motion.mode = .drag; motion.progress = snapshot.progress; motion.pose = pose
            motion.contactOrigin = snapshot.progress; motion.contactPoseOrigin = pose; motion.translationOrigin = translation
            motion.contactDirection = snapshot.progress > 0 ? (motion.direction == 0 ? 1 : motion.direction) : 0
            if motion.contactDirection == 0 && abs(translation) > 0.5 { motion.contactDirection = translation < 0 ? -1 : 1 }
        }
        if motion.contactDirection == 0 && abs(translation) > 0.5 { motion.contactDirection = translation < 0 ? -1 : 1 }
        let p = TrophySavingsMilestoneMotion.clamp(motion.contactOrigin+motion.contactDirection*(translation-motion.translationOrigin)/120)
        var scene = frame(at:Date()).scene; scene.advance(clock:now-sceneEpoch,progress:p,generation:motion.generation); anchor = scene
        motion.progress = p; motion.pose = motion.contactPoseOrigin >= 1 ? 2-p : p
        motion.contactVelocity = TrophySavingsMilestoneMotion.clamp(motion.contactDirection*velocity/120,-4,4); motion.direction = motion.contactDirection
    }
    func release(cancelled: Bool) {
        guard motion.mode == .drag, active, !overlay, !reduced, previewTime == nil else { return }
        let now = Date().timeIntervalSince1970, snapshot = frame(at:Date()); preserve(snapshot,at:now)
        motion.begin(progress:snapshot.progress,demo:false,userPeak:!cancelled && snapshot.progress >= 0.985,now:now,velocity:cancelled ? 0 : motion.contactVelocity)
        scheduleFinish()
    }
    func pause() {
        guard !overlay else { return }; let now = Date(), snapshot = frame(at:now)
        task?.cancel(); task = nil; preserve(snapshot,at:now.timeIntervalSince1970); paused = snapshot; overlay = true
        motion.generation += 1; motion.mode = .paused; motion.pose = snapshot.pose; motion.progress = snapshot.progress; motion.eventUserPeak = false
    }
    func resume() {
        guard overlay else { return }; overlay = false
        guard active, !reduced, previewTime == nil else { reset(); return }
        let snapshot = paused ?? .zero, now = Date().timeIntervalSince1970; preserve(snapshot,at:now); paused = nil
        motion.begin(progress:snapshot.progress,demo:false,userPeak:false,now:now); scheduleFinish()
    }
    func stop() { task?.cancel(); task = nil; reset() }
    private func scheduleFinish() {
        task?.cancel(); let event = motion, token = motion.generation
        let paperEnd = event.eventStarted+event.endTime
        let endScene = frame(at:Date(timeIntervalSince1970:paperEnd)).scene
        let last = max(endScene.contactStart,endScene.wakeStarts.max() ?? -1)
        let effectEnd = sceneEpoch+last+TrophySavingsMilestoneCelebration.settle*endScene.cadence
        let finish = max(paperEnd,effectEnd), resume = event.eventStarted+max(2,event.endTime)
        task = Task { [weak self] in
            guard let self else { return }
            if event.eventUserPeak {
                guard await wait(until:event.eventStarted+max(0,event.peakTime-0.05),token:token) else { return }
                if impact == nil { impact = UIImpactFeedbackGenerator(style:.light) }; impact?.prepare()
                guard await wait(until:event.eventStarted+event.peakTime,token:token) else { return }; impact?.impactOccurred(intensity:0.45)
            }
            guard await wait(until:finish,token:token) else { return }
            motion.mode = .waiting; motion.progress = 0; motion.pose = 0; anchor = TrophySavingsMilestoneCelebration()
            guard await wait(until:resume,token:token) else { return }
            motion.mode = autoPlay ? .loop : .idle; motion.epoch = resume; sceneEpoch = resume
        }
    }
    private func wait(until time: Double, token: Int) async -> Bool {
        do { try await Task.sleep(for:.seconds(max(0,time-Date().timeIntervalSince1970))) } catch { return false }
        return !Task.isCancelled && motion.generation == token && active && !overlay && !reduced && previewTime == nil
    }
}
