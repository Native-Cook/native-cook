import SwiftUI
import UIKit

@MainActor
final class SaveToCollectionModel: ObservableObject {
    @Published private(set) var demoRunning: Bool
    @Published private(set) var interactive = SaveToCollectionPresentation.rest

    private let autoPlay: Bool
    private var reduceMotion = false
    private var layout = SaveToCollectionLayout(size: .zero, top: 0, bottom: 0)
    private var epoch = Date()
    private var gridTouchActive = false
    private var saveTouchActive = false
    private var initialTouch = CGPoint.zero
    private var currentTouch = CGPoint.zero
    private var lastTouch = CGPoint.zero
    private var lastTouchTime = Date.timeIntervalSinceReferenceDate
    private var fingerOffset = CGPoint.zero
    private var pendingTile: Int?
    private var magnetWasNear = false
    private var holdTask: Task<Void, Never>?
    private var resumeTask: Task<Void, Never>?
    private var actionGeneration = 0

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        demoRunning = autoPlay
    }

    func configure(layout: SaveToCollectionLayout, reduceMotion: Bool) {
        let firstLayout = self.layout.size == .zero
        self.layout = layout
        if firstLayout { epoch = Date() }
        guard self.reduceMotion != reduceMotion else { return }
        self.reduceMotion = reduceMotion
        if reduceMotion {
            demoRunning = false
            resetImmediately()
        } else if autoPlay {
            epoch = Date()
            demoRunning = true
        }
    }

    func phase(at date: Date) -> Double {
        let elapsed = max(date.timeIntervalSince(epoch), 0)
        return elapsed.truncatingRemainder(dividingBy: SaveToCollection.loop)
    }

    func presentation(at date: Date) -> SaveToCollectionPresentation {
        if demoRunning {
            return SaveToCollectionAutoScript.presentation(time: phase(at: date), layout: layout)
        }
        return interactive
    }

    func gridTouchChanged(_ point: CGPoint, snapshot: SaveToCollectionPresentation) {
        if !gridTouchActive {
            gridTouchActive = true
            pauseDemo(snapshot: snapshot)
            initialTouch = point
            currentTouch = point
            lastTouch = point
            lastTouchTime = Date.timeIntervalSinceReferenceDate
            pendingTile = layout.tile(at: point)
            scheduleHold()
            return
        }

        currentTouch = point
        if interactive.carryIndex == nil {
            if SaveToCollectionMath.distance(initialTouch, point) > 10 {
                pendingTile = nil
                holdTask?.cancel()
            }
            return
        }
        moveCarried(to: point)
    }

    func gridTouchEnded(_ point: CGPoint) {
        guard gridTouchActive else { return }
        gridTouchActive = false
        currentTouch = point
        holdTask?.cancel()
        holdTask = nil
        pendingTile = nil
        if interactive.carryIndex != nil {
            moveCarried(to: point)
            releaseCarried()
        }
        scheduleResume()
    }

    func saveTouchChanged(snapshot: SaveToCollectionPresentation) {
        guard !saveTouchActive else { return }
        saveTouchActive = true
        pauseDemo(snapshot: snapshot)
        setPresentation(.easeOut(duration: 0.100)) { $0.press = 1 }
    }

    func saveTouchEnded() {
        guard saveTouchActive else { return }
        saveTouchActive = false
        setPresentation(.easeOut(duration: SaveToCollectionTokens.Motion.pressOut)) { $0.press = 0 }
        if interactive.sheet > 0.5 { closeSheet() } else { openSheet() }
        scheduleResume()
    }

    func openFromToast(snapshot: SaveToCollectionPresentation) {
        pauseDemo(snapshot: snapshot)
        openSheet()
        scheduleResume()
    }

    func closeFromSheet(snapshot: SaveToCollectionPresentation) {
        pauseDemo(snapshot: snapshot)
        closeSheet()
        scheduleResume()
    }

    func accessibilitySavePhoto(_ index: Int, snapshot: SaveToCollectionPresentation) {
        guard interactive.carryIndex == nil else { return }
        pauseDemo(snapshot: snapshot)
        let tile = layout.tiles[index]
        var next = interactive
        next.carryIndex = index
        next.centre = CGPoint(x: tile.midX, y: tile.midY)
        next.lift = reduceMotion ? 0 : 1
        interactive = next
        setPresentation(.timingCurve(0.77, 0, 0.175, 1, duration: reduceMotion ? 0.120 : SaveToCollectionTokens.Motion.drop)) {
            $0.centre = self.layout.save
            $0.pull = 1
            $0.drop = 1
        }
        finishLanding(after: reduceMotion ? 0.120 : SaveToCollectionTokens.Motion.drop)
        scheduleResume()
    }

    private func pauseDemo(snapshot: SaveToCollectionPresentation) {
        resumeTask?.cancel()
        resumeTask = nil
        guard demoRunning else { return }
        interactive = snapshot
        demoRunning = false
        if interactive.carryIndex != nil && interactive.drop < 0.001 {
            returnCarriedHome()
        }
    }

    private func scheduleHold() {
        holdTask?.cancel()
        holdTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(SaveToCollectionTokens.Motion.hold * 1_000_000_000))
            guard !Task.isCancelled, let self, self.gridTouchActive, let index = self.pendingTile,
                  self.interactive.carryIndex == nil else { return }
            self.pickUp(index: index, point: self.currentTouch)
        }
    }

    private func pickUp(index: Int, point: CGPoint) {
        let tile = layout.tiles[index]
        fingerOffset = CGPoint(x: tile.midX - point.x, y: tile.midY - point.y)
        magnetWasNear = false
        var next = interactive
        next.carryIndex = index
        next.centre = CGPoint(x: tile.midX, y: tile.midY)
        next.drop = 0
        next.pull = 0
        next.lean = 0
        interactive = next
        if !reduceMotion {
            setPresentation(.spring(duration: SaveToCollectionTokens.Motion.lift.duration, bounce: 0)) { $0.lift = 1 }
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
    }

    private func moveCarried(to point: CGPoint) {
        let now = Date.timeIntervalSinceReferenceDate
        let delta = max(now - lastTouchTime, 0.008)
        let velocityX = (point.x - lastTouch.x) / delta
        lastTouch = point
        lastTouchTime = now
        let centre = CGPoint(x: point.x + fingerOffset.x, y: point.y + fingerOffset.y)
        let distance = SaveToCollectionMath.distance(centre, layout.save)
        let pull = SaveToCollectionMath.smoothstep(
            edge0: SaveToCollectionTokens.Save.magnetFar,
            edge1: SaveToCollectionTokens.Save.magnetNear,
            value: distance
        )
        var next = interactive
        next.centre = centre
        next.pull = pull
        next.lean = reduceMotion ? 0 : min(max(velocityX * SaveToCollectionTokens.Save.lean, -SaveToCollectionTokens.Save.leanMax), SaveToCollectionTokens.Save.leanMax)
        interactive = next
        let near = pull > 0.5
        if near && !magnetWasNear && !reduceMotion {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        magnetWasNear = near
    }

    private func releaseCarried() {
        guard interactive.carryIndex != nil else { return }
        let distance = SaveToCollectionMath.distance(interactive.centre, layout.save)
        if distance < SaveToCollectionTokens.Save.dropRadius || interactive.pull > 0.5 {
            let duration = reduceMotion ? 0.120 : SaveToCollectionTokens.Motion.drop
            setPresentation(.timingCurve(0.77, 0, 0.175, 1, duration: duration)) { $0.drop = 1 }
            finishLanding(after: duration)
        } else {
            returnCarriedHome()
        }
    }

    private func returnCarriedHome() {
        guard let index = interactive.carryIndex else { return }
        actionGeneration += 1
        let generation = actionGeneration
        let tile = layout.tiles[index]
        let duration = reduceMotion ? SaveToCollectionTokens.Motion.reduced.duration : SaveToCollectionTokens.Motion.home.duration
        let bounce = reduceMotion ? 0 : 1 - SaveToCollectionTokens.Motion.home.damping
        setPresentation(.spring(duration: duration, bounce: bounce)) {
            $0.centre = CGPoint(x: tile.midX, y: tile.midY)
            $0.pull = 0
            $0.lean = 0
            $0.lift = 0
        }
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000))
            guard let self, self.actionGeneration == generation else { return }
            var next = self.interactive
            next.carryIndex = nil
            next.drop = 0
            self.interactive = next
        }
    }

    private func finishLanding(after duration: Double) {
        actionGeneration += 1
        let generation = actionGeneration
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000))
            guard let self, self.actionGeneration == generation, let index = self.interactive.carryIndex else { return }
            self.landed(index: index)
        }
    }

    private func landed(index: Int) {
        var next = interactive
        next.carryIndex = nil
        next.lift = 0
        next.lean = 0
        next.pull = 0
        next.drop = 0
        next.roll = 0
        next.count = SaveToCollectionTokens.Save.countBefore + 1
        next.markedIndex = index
        next.mark = 0
        next.toast = 0
        interactive = next
        setPresentation(.timingCurve(0.23, 1, 0.32, 1, duration: reduceMotion ? 0 : SaveToCollectionTokens.Motion.roll)) { $0.roll = 1 }
        setPresentation(.timingCurve(0.23, 1, 0.32, 1, duration: SaveToCollectionTokens.Motion.mark)) { $0.mark = 1 }
        setPresentation(.timingCurve(0.23, 1, 0.32, 1, duration: SaveToCollectionTokens.Motion.toastIn)) { $0.toast = 1 }
        if !reduceMotion {
            setPresentation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.070)) { $0.gulp = 0.86 }
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: 70_000_000)
                self?.setPresentation(.spring(duration: SaveToCollectionTokens.Motion.gulp.duration, bounce: 1 - SaveToCollectionTokens.Motion.gulp.damping)) { $0.gulp = 1 }
            }
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }

    private func openSheet() {
        setPresentation(.easeOut(duration: SaveToCollectionTokens.Motion.toastOut)) { $0.toast = 0 }
        let fresh = interactive.markedIndex != nil
        var next = interactive
        next.arrive = fresh ? 0 : 1
        interactive = next
        let animation: Animation = reduceMotion
            ? .easeInOut(duration: SaveToCollectionTokens.Motion.reduced.duration)
            : .spring(duration: SaveToCollectionTokens.Motion.sheet.duration, bounce: 1 - SaveToCollectionTokens.Motion.sheet.damping)
        setPresentation(animation) { $0.sheet = 1 }
        if fresh {
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(SaveToCollectionTokens.Motion.arriveDelay * 1_000_000_000))
                guard let self else { return }
                self.setPresentation(.timingCurve(0.23, 1, 0.32, 1, duration: self.reduceMotion ? 0.160 : SaveToCollectionTokens.Motion.arrive)) { $0.arrive = 1 }
            }
        }
        if !reduceMotion { UISelectionFeedbackGenerator().selectionChanged() }
    }

    private func closeSheet() {
        setPresentation(.timingCurve(0.23, 1, 0.32, 1, duration: reduceMotion ? 0.200 : SaveToCollectionTokens.Motion.sheetOut)) {
            $0.sheet = 0
        }
    }

    private func scheduleResume() {
        guard autoPlay, !reduceMotion else { return }
        resumeTask?.cancel()
        resumeTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(SaveToCollectionTokens.Motion.resume * 1_000_000_000))
            guard !Task.isCancelled, let self, !self.gridTouchActive, !self.saveTouchActive,
                  self.interactive.carryIndex == nil else { return }
            self.resetForDemo()
            try? await Task.sleep(nanoseconds: UInt64(SaveToCollectionTokens.Motion.tidy * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self.epoch = Date()
            self.demoRunning = true
        }
    }

    private func resetForDemo() {
        actionGeneration += 1
        setPresentation(.easeOut(duration: SaveToCollectionTokens.Motion.mark)) { presentation in
            presentation = .rest
        }
    }

    private func resetImmediately() {
        actionGeneration += 1
        holdTask?.cancel()
        resumeTask?.cancel()
        gridTouchActive = false
        saveTouchActive = false
        interactive = .rest
    }

    private func setPresentation(_ animation: Animation? = nil, update: (inout SaveToCollectionPresentation) -> Void) {
        var next = interactive
        update(&next)
        if let animation {
            withAnimation(animation) { interactive = next }
        } else {
            interactive = next
        }
    }
}

enum SaveToCollectionAutoScript {
    static func presentation(time: Double, layout: SaveToCollectionLayout) -> SaveToCollectionPresentation {
        var result = SaveToCollectionPresentation.rest
        guard layout.tiles.count > SaveToCollectionTokens.Demo.tile else { return result }
        let demo = SaveToCollectionTokens.Demo.self
        let motion = SaveToCollectionTokens.Motion.self
        let tile = layout.tiles[demo.tile]
        let fingerStart = CGPoint(x: tile.midX, y: tile.minY + tile.height * 0.42)

        let savedAt = demo.release + motion.drop
        let resetEnd = demo.reset + motion.mark
        if time >= savedAt && time < resetEnd {
            result.count = time < demo.reset
                ? SaveToCollectionTokens.Save.countBefore + 1
                : SaveToCollectionTokens.Save.countBefore
            result.markedIndex = demo.tile
            result.roll = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, savedAt, savedAt + motion.roll))
            let markIn = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, savedAt, savedAt + motion.mark))
            let markOut = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.reset, resetEnd))
            result.mark = markIn * (1 - markOut)
        }

        let toastIn = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, savedAt, savedAt + motion.toastIn))
        let toastOut = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.tap, demo.tap + motion.toastOut))
        result.toast = toastIn * (1 - toastOut)

        if time >= demo.lift && time < savedAt {
            result.carryIndex = demo.tile
            result.lift = SaveToCollectionMath.spring(time - demo.lift, duration: motion.lift.duration, damping: motion.lift.damping)
            var centre = CGPoint(x: tile.midX, y: tile.midY)
            if time >= demo.carryStart {
                let linear = SaveToCollectionMath.segment(time, demo.carryStart, demo.carryEnd)
                let eased = (1 - cos(.pi * linear)) / 2
                centre = CGPoint(
                    x: tile.midX + (layout.save.x - tile.midX) * eased - sin(.pi * eased) * 28,
                    y: tile.midY + (layout.save.y - tile.midY) * eased
                )
                result.lean = sin(.pi * eased * 2) * 4
            }
            result.centre = centre
            result.pull = SaveToCollectionMath.smoothstep(
                edge0: SaveToCollectionTokens.Save.magnetFar,
                edge1: SaveToCollectionTokens.Save.magnetNear,
                value: SaveToCollectionMath.distance(centre, layout.save)
            )
            result.drop = SaveToCollectionMath.easeInOut(SaveToCollectionMath.segment(time, demo.release, savedAt))
        }

        if time >= savedAt {
            let pressIn = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, savedAt, savedAt + 0.070))
            let springBack = SaveToCollectionMath.spring(time - savedAt - 0.070, duration: motion.gulp.duration, damping: motion.gulp.damping)
            result.gulp = time < savedAt + 0.070
                ? SaveToCollectionMath.mix(1, 0.86, pressIn)
                : SaveToCollectionMath.mix(0.86, 1, springBack)
        }

        if time >= demo.tap && time < demo.close + motion.sheetOut {
            let sheetIn = SaveToCollectionMath.spring(time - demo.tap, duration: motion.sheet.duration, damping: motion.sheet.damping)
            let sheetOut = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.close, demo.close + motion.sheetOut))
            result.sheet = sheetIn * (1 - sheetOut)
            result.arrive = SaveToCollectionMath.easeOut(
                SaveToCollectionMath.segment(time, demo.tap + motion.arriveDelay, demo.tap + motion.arriveDelay + motion.arrive)
            )
        }

        let firstGhostIn = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.fingerIn, demo.fingerIn + motion.finger))
        let firstGhostOut = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.release, demo.release + motion.finger))
        if time >= demo.fingerIn && time < demo.release + motion.finger {
            result.ghostOpacity = firstGhostIn * (1 - firstGhostOut)
            result.ghostPress = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.down, demo.down + motion.finger))
            result.ghostCentre = fingerStart
            if time >= demo.carryStart {
                let linear = SaveToCollectionMath.segment(time, demo.carryStart, demo.carryEnd)
                let eased = (1 - cos(.pi * linear)) / 2
                let target = CGPoint(x: layout.save.x, y: layout.save.y - tile.height * 0.08)
                result.ghostCentre = CGPoint(
                    x: fingerStart.x + (target.x - fingerStart.x) * eased - sin(.pi * eased) * 28,
                    y: fingerStart.y + (target.y - fingerStart.y) * eased
                )
            }
        } else if time >= demo.tapIn && time < demo.tap + 0.080 + motion.finger {
            let ghostIn = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.tapIn, demo.tapIn + motion.finger))
            let ghostOut = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.tap + 0.080, demo.tap + 0.080 + motion.finger))
            result.ghostOpacity = ghostIn * (1 - ghostOut)
            result.ghostPress = SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.tap - 0.100, demo.tap))
                * (1 - SaveToCollectionMath.easeOut(SaveToCollectionMath.segment(time, demo.tap, demo.tap + motion.pressOut)))
            result.ghostCentre = layout.save
            result.press = result.ghostPress
        }
        return result
    }
}
