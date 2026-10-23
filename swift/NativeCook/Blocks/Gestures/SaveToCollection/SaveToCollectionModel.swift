import SwiftUI
import UIKit

struct SaveToCollectionLayout {
    let size: CGSize
    let scale: Double
    let verticalOffset: Double
    let rects: [CGRect]
    let bookmark: CGPoint
    let riseY: Double

    init(size: CGSize) {
        let scale = size.width / SaveToCollectionTokens.designSize.width
        let verticalOffset = (size.height - SaveToCollectionTokens.designSize.height * scale) / 2

        self.size = size
        self.scale = scale
        self.verticalOffset = verticalOffset
        self.rects = SaveToCollectionTokens.cards.map { card in
            CGRect(
                x: card.frame.minX * scale,
                y: card.frame.minY * scale + verticalOffset,
                width: card.frame.width * scale,
                height: card.frame.height * scale
            )
        }
        self.bookmark = CGPoint(
            x: size.width / 2,
            y: SaveToCollectionTokens.Layout.bookmarkY * scale + verticalOffset
        )
        self.riseY = SaveToCollectionTokens.Layout.bookmarkRiseY * scale + verticalOffset
    }
}

struct SaveToCollectionFlight: Identifiable {
    let id: Int
    let card: Int
    let from: CGRect
    let start: Double
    let duration: Double
    let isDrop: Bool
    let startScale: Double
}

struct SaveToCollectionDragPresentation {
    let card: Int
    let centre: CGPoint
    let scale: Double
    let tilt: Double
}

@MainActor
final class SaveToCollectionModel: ObservableObject {
    @Published private(set) var frameRevision = 0

    var autoPlay: Bool
    var reduceMotion = false
    private(set) var layout = SaveToCollectionLayout(size: .zero)
    private(set) var pile = 0
    private(set) var gone = Set<Int>()
    private(set) var flights: [SaveToCollectionFlight] = []
    private(set) var dragCard: Int?
    private(set) var saved = 0
    private(set) var pileCount = 0
    private(set) var dealAt: Double?
    private(set) var rushAt: Double?
    private(set) var riseAt: Double?
    private(set) var ringAt: Double?
    private(set) var bounceAt: Double?
    private(set) var countAt: Double?
    private(set) var clock = 0.0
    private(set) var scriptTime = 0.0
    private(set) var pressCard = -1
    private(set) var press = 0.0

    private var lastTargetTimestamp: Double?
    private var paused: Bool
    private var touching = false
    private var quiet = 0.0
    private var nextPileAt: Double?
    private var flightSequence = 0
    private var scriptDragging = false
    private var scriptDropAt: Double?
    private var manualDragging = false
    private var magnetWasOpen = false
    private var baseCentre = CGPoint.zero
    private var dragX = SaveToCollectionSpringValue()
    private var dragY = SaveToCollectionSpringValue()
    private var dragScale = SaveToCollectionSpringValue(value: 1, target: 1)
    private var dragTilt = SaveToCollectionSpringValue()
    private var magnetOpen = 0.0
    private var lastVelocity = CGPoint.zero
    private var pressTween: SaveToCollectionTween?
    private var dimCard: Int?
    private var dimValue = 0.0
    private var dimTween: SaveToCollectionTween?

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        paused = !autoPlay
    }

    var visible: [Int] {
        SaveToCollectionTokens.cards.indices.filter {
            SaveToCollectionTokens.cards[$0].pile == pile && !gone.contains($0)
        }
    }

    var needsFrames: Bool {
        autoPlay || touching || pressTween != nil || dimTween != nil || !flights.isEmpty || dragCard != nil ||
            riseAt != nil || ringAt != nil || bounceAt != nil || dealAt != nil
    }

    var openProgress: Double {
        if scriptDragging, let card = dragCard {
            let rect = layout.rects[card]
            let duration = SaveToCollectionTokens.Demo.dragEnd - SaveToCollectionTokens.Demo.dragLifted
            let progress = SaveToCollectionMath.cosineEase((scriptTime - SaveToCollectionTokens.Demo.dragLifted) / duration)
            let finger = CGPoint(
                x: rect.midX + (layout.bookmark.x - rect.midX) * progress + sin(.pi * progress) * SaveToCollectionTokens.Demo.dragBow,
                y: rect.midY + (layout.bookmark.y - rect.midY) * progress
            )
            let distance = hypot(layout.bookmark.x - finger.x, layout.bookmark.y - finger.y)
            let raw = min(max(1 - distance / SaveToCollectionTokens.Save.magnetRadius, 0), 1)
            return raw * raw * (3 - 2 * raw)
        }
        if let scriptDropAt {
            return 1 - SaveToCollectionMath.cubicInOut((clock - scriptDropAt) / SaveToCollectionTokens.Timing.dropFlight)
        }
        return magnetOpen
    }

    var bookmarkPress: Double {
        if autoPlay, !paused {
            for tap in [SaveToCollectionTokens.Demo.saveAll, SaveToCollectionTokens.Demo.saveAllAgain] {
                let start = tap - SaveToCollectionTokens.Demo.tapLead
                let peak = start + SaveToCollectionTokens.Timing.pressIn
                let end = peak + SaveToCollectionTokens.Timing.pressOut
                if scriptTime >= start, scriptTime < peak {
                    return SaveToCollectionMath.cubicBezier(
                        (scriptTime - start) / SaveToCollectionTokens.Timing.pressIn,
                        SaveToCollectionTokens.Easing.easeOut
                    )
                }
                if scriptTime >= peak, scriptTime < end {
                    return 1 - SaveToCollectionMath.cubicBezier(
                        (scriptTime - peak) / SaveToCollectionTokens.Timing.pressOut,
                        SaveToCollectionTokens.Easing.easeOut
                    )
                }
            }
        }
        return pressCard == -2 ? press : 0
    }

    func configure(size: CGSize, reduceMotion: Bool) {
        guard size.width > 0, size.height > 0 else { return }
        layout = SaveToCollectionLayout(size: size)
        self.reduceMotion = reduceMotion
        frameRevision &+= 1
    }

    func start(autoPlay: Bool) {
        self.autoPlay = autoPlay
        guard autoPlay else { return }
        resetToRest()
        paused = false
        lastTargetTimestamp = nil
        frameRevision &+= 1
    }

    func advance(to targetTimestamp: Double) {
        let rawDelta = lastTargetTimestamp.map { targetTimestamp - $0 } ?? 0
        lastTargetTimestamp = targetTimestamp
        let wallDelta = max(rawDelta, 0)
        let delta = min(wallDelta, SaveToCollectionTokens.Clock.maxFrameDelta)
        guard wallDelta > 0 else { return }

        if paused {
            clock += wallDelta
            if !touching { quiet += wallDelta }
            if autoPlay, quiet >= SaveToCollectionTokens.Clock.resumeDelay, canResume {
                resetToRest()
                paused = false
            }
        } else if autoPlay {
            let old = scriptTime
            let held = SaveToCollectionTokens.qaHoldAt
            let step = held != nil && old >= held! ? 0 : wallDelta
            let new = old + step
            clock = new
            processScript(from: old, to: new)
            scriptTime = new
            if scriptTime >= SaveToCollectionTokens.Clock.loop {
                let remainder = scriptTime.truncatingRemainder(dividingBy: SaveToCollectionTokens.Clock.loop)
                resetToRest()
                scriptTime = remainder
                clock = remainder
            }
        } else {
            clock += wallDelta
        }

        updateManualMotion(delta: delta)
        updateTimedState()
        frameRevision &+= 1
    }

    func touchBegan(at point: CGPoint) {
        touching = true
        quiet = 0
        if !paused {
            paused = true
            scriptDragging = false
            if dragCard != nil {
                cancelDragToRest()
            }
            if visible.isEmpty {
                if let riseAt {
                    nextPileAt = max(
                        clock,
                        riseAt + SaveToCollectionTokens.Timing.rise + SaveToCollectionTokens.Timing.riseHold
                    )
                } else {
                    completePileIfNeeded()
                }
            }
        }
        pressCard = target(at: point)
        beginPress(to: 1, duration: SaveToCollectionTokens.Timing.pressIn)
    }

    func touchEnded(at point: CGPoint, wasDrag: Bool) {
        touching = false
        quiet = 0
        beginPress(to: 0, duration: SaveToCollectionTokens.Timing.pressOut)
        guard !wasDrag else { return }
        let target = target(at: point)
        if target == -2 {
            saveAll(silent: false)
        } else if target >= 0 {
            fly(cards: [target], silent: false)
        }
    }

    func accessibilitySave(card: Int) {
        if !paused {
            paused = true
            scriptDragging = false
        }
        fly(cards: [card], silent: false)
        frameRevision &+= 1
    }

    func accessibilitySaveAll() {
        if !paused {
            paused = true
            scriptDragging = false
        }
        saveAll(silent: false)
        frameRevision &+= 1
    }

    func beginDrag(at point: CGPoint) -> Bool {
        let card = hitCard(at: point)
        guard card >= 0, !manualDragging else { return false }
        let rect = layout.rects[card]
        manualDragging = true
        dragCard = card
        dimCard = card
        beginDim(to: 1)
        baseCentre = CGPoint(x: rect.midX, y: rect.midY)
        dragX = SaveToCollectionSpringValue(value: baseCentre.x, target: baseCentre.x)
        dragY = SaveToCollectionSpringValue(value: baseCentre.y, target: baseCentre.y)
        dragScale = SaveToCollectionSpringValue(value: 1, target: reduceMotion ? 1 : SaveToCollectionTokens.Save.liftScale)
        dragTilt = SaveToCollectionSpringValue()
        magnetOpen = 0
        magnetWasOpen = false
        playHaptic(.medium)
        return true
    }

    func moveDrag(translation: CGPoint, velocity: CGPoint) {
        guard manualDragging else { return }
        lastVelocity = velocity
        let finger = CGPoint(x: baseCentre.x + translation.x, y: baseCentre.y + translation.y)
        let distance = hypot(layout.bookmark.x - finger.x, layout.bookmark.y - finger.y)
        let raw = min(max(1 - distance / SaveToCollectionTokens.Save.magnetRadius, 0), 1)
        let pull = raw * raw * (3 - 2 * raw)
        if !magnetWasOpen, pull > 0 {
            playHaptic(.light)
            magnetWasOpen = true
        }
        magnetOpen = pull
        dragX.snap(to: finger.x + (layout.bookmark.x - finger.x) * pull * SaveToCollectionTokens.Save.magnetPull)
        dragY.snap(to: finger.y + (layout.bookmark.y - finger.y) * pull * SaveToCollectionTokens.Save.magnetPull)
        dragScale.snap(to: reduceMotion ? 1 : SaveToCollectionTokens.Save.liftScale * (1 - SaveToCollectionTokens.Save.magnetShrink * pull))
        let tilt = min(max(velocity.x * SaveToCollectionTokens.Save.tiltPerVelocity, -SaveToCollectionTokens.Save.tiltMax), SaveToCollectionTokens.Save.tiltMax)
        dragTilt.target = reduceMotion ? 0 : tilt
    }

    func endDrag() {
        guard manualDragging, let card = dragCard else { return }
        manualDragging = false
        dragTilt.target = 0
        let speed = hypot(lastVelocity.x, lastVelocity.y)
        let vector = CGPoint(x: layout.bookmark.x - dragX.value, y: layout.bookmark.y - dragY.value)
        let vectorLength = max(hypot(vector.x, vector.y), SaveToCollectionTokens.Clock.minimumVectorLength)
        let aim = speed > 0
            ? (lastVelocity.x * vector.x + lastVelocity.y * vector.y) / (speed * vectorLength)
            : 0
        let flicked = speed > SaveToCollectionTokens.Save.flickSpeed && aim > SaveToCollectionTokens.Save.flickAim
        if magnetOpen > SaveToCollectionTokens.Save.commitOpenThreshold || flicked {
            playHaptic(.heavy)
            let duration = reduceMotion
                ? SaveToCollectionTokens.Timing.reduced
                : (flicked && magnetOpen <= SaveToCollectionTokens.Save.commitOpenThreshold
                    ? SaveToCollectionTokens.Timing.flickFlight
                    : SaveToCollectionTokens.Timing.dropFlight)
            let from = CGRect(
                x: dragX.value - layout.rects[card].width / 2,
                y: dragY.value - layout.rects[card].height / 2,
                width: layout.rects[card].width,
                height: layout.rects[card].height
            )
            dragCard = nil
            magnetOpen = 0
            gone.insert(card)
            addFlight(
                card: card,
                from: from,
                delay: 0,
                duration: duration,
                isDrop: true,
                startScale: dragScale.value
            )
        } else {
            cancelDragToRest()
        }
    }

    func dragPresentation() -> SaveToCollectionDragPresentation? {
        guard let card = dragCard else { return nil }
        if scriptDragging {
            let start = layout.rects[card]
            let centre = CGPoint(x: start.midX, y: start.midY)
            let duration = SaveToCollectionTokens.Demo.dragEnd - SaveToCollectionTokens.Demo.dragLifted
            let u = SaveToCollectionMath.cosineEase((scriptTime - SaveToCollectionTokens.Demo.dragLifted) / duration)
            let translationX = (layout.bookmark.x - centre.x) * u + sin(.pi * u) * SaveToCollectionTokens.Demo.dragBow
            let translationY = (layout.bookmark.y - centre.y) * u
            let finger = CGPoint(x: centre.x + translationX, y: centre.y + translationY)
            let distance = hypot(layout.bookmark.x - finger.x, layout.bookmark.y - finger.y)
            let raw = min(max(1 - distance / SaveToCollectionTokens.Save.magnetRadius, 0), 1)
            let pull = raw * raw * (3 - 2 * raw)
            let drawn = CGPoint(
                x: finger.x + (layout.bookmark.x - finger.x) * pull * SaveToCollectionTokens.Save.magnetPull,
                y: finger.y + (layout.bookmark.y - finger.y) * pull * SaveToCollectionTokens.Save.magnetPull
            )
            let scale = reduceMotion ? 1 : SaveToCollectionTokens.Save.liftScale * (1 - SaveToCollectionTokens.Save.magnetShrink * pull)
            let rawTime = (scriptTime - SaveToCollectionTokens.Demo.dragLifted) / duration
            let du = rawTime > 0 && rawTime < 1 ? (.pi / (2 * duration)) * sin(.pi * rawTime) : 0
            let velocity = (
                layout.bookmark.x - centre.x +
                    .pi * cos(.pi * u) * SaveToCollectionTokens.Demo.dragBow
            ) * du
            let tilt = reduceMotion ? 0 : min(max(velocity * SaveToCollectionTokens.Save.tiltPerVelocity, -SaveToCollectionTokens.Save.tiltMax), SaveToCollectionTokens.Save.tiltMax)
            return .init(card: card, centre: drawn, scale: scale, tilt: tilt)
        }
        if let dropAt = scriptDropAt {
            let progress = SaveToCollectionMath.cubicInOut((clock - dropAt) / SaveToCollectionTokens.Timing.dropFlight)
            return .init(
                card: card,
                centre: layout.bookmark,
                scale: SaveToCollectionMath.mix(SaveToCollectionTokens.Save.liftScale * (1 - SaveToCollectionTokens.Save.magnetShrink), SaveToCollectionTokens.Save.endScale, progress),
                tilt: 0
            )
        }
        return .init(card: card, centre: CGPoint(x: dragX.value, y: dragY.value), scale: dragScale.value, tilt: dragTilt.value)
    }

    func cardDealProgress(order: Int) -> Double {
        guard let dealAt else { return 1 }
        let delay = Double(order) * SaveToCollectionTokens.Timing.dealStagger
        return SaveToCollectionMath.cubicBezier(
            (clock - dealAt - delay) / SaveToCollectionTokens.Timing.deal,
            SaveToCollectionTokens.Easing.easeOut
        )
    }

    func cardPress(for index: Int) -> Double {
        pressCard == index ? press : 0
    }

    func cardDim(for index: Int) -> Double {
        guard dimCard == index else { return 0 }
        if scriptDragging || scriptDropAt != nil {
            return SaveToCollectionMath.clamp(
                (clock - SaveToCollectionTokens.Demo.dragLifted) / SaveToCollectionTokens.Timing.dim
            )
        }
        return dimValue
    }

    func riseProgress() -> Double {
        guard let riseAt else { return 0 }
        let elapsed = clock - riseAt
        let up = reduceMotion ? SaveToCollectionTokens.Timing.reduced : SaveToCollectionTokens.Timing.rise
        let down = reduceMotion ? SaveToCollectionTokens.Timing.reduced : SaveToCollectionTokens.Timing.riseDown
        if elapsed < 0 { return 0 }
        if elapsed < up {
            return SaveToCollectionMath.cubicBezier(elapsed / up, SaveToCollectionTokens.Easing.rise)
        }
        if elapsed < up + SaveToCollectionTokens.Timing.riseHold || (SaveToCollectionTokens.qaHoldAt != nil && SaveToCollectionTokens.replicaOnly) {
            return 1
        }
        if elapsed < up + SaveToCollectionTokens.Timing.riseHold + down {
            return SaveToCollectionMath.cubicInOut(1 - (elapsed - up - SaveToCollectionTokens.Timing.riseHold) / down)
        }
        return 0
    }

    func ringProgress() -> Double? {
        guard let ringAt, !reduceMotion else { return nil }
        let progress = (clock - ringAt) / SaveToCollectionTokens.Timing.landRing
        return progress < 1 ? SaveToCollectionMath.cubicBezier(progress, SaveToCollectionTokens.Easing.easeOut) : nil
    }

    func bounceProgress() -> Double {
        guard let bounceAt, !reduceMotion else { return 0 }
        let elapsed = clock - bounceAt
        if elapsed <= SaveToCollectionTokens.Timing.gulpUp {
            return max(0, elapsed / SaveToCollectionTokens.Timing.gulpUp)
        }
        return SaveToCollectionMath.springDecay(
            elapsed - SaveToCollectionTokens.Timing.gulpUp,
            token: SaveToCollectionTokens.Spring.bounce
        )
    }

    func countEntrance() -> Double {
        guard let countAt else { return 1 }
        return SaveToCollectionMath.cubicBezier(
            (clock - countAt) / SaveToCollectionTokens.Timing.countIn,
            SaveToCollectionTokens.Easing.easeOut
        )
    }

    func streamProgress() -> Double? {
        guard let rushAt, !reduceMotion else { return nil }
        let elapsed = clock - rushAt
        guard elapsed >= 0, elapsed <= SaveToCollectionTokens.Stream.total else { return nil }
        return elapsed / SaveToCollectionTokens.Stream.total
    }

    func ghostPresentation() -> (centre: CGPoint, opacity: Double, press: Double)? {
        guard autoPlay, !paused else { return nil }
        let card = SaveToCollectionTokens.cards.firstIndex { $0.id == SaveToCollectionTokens.Demo.dragCard } ?? 0
        let rect = layout.rects[card]
        let t = scriptTime
        if t >= SaveToCollectionTokens.Demo.dragDown, t <= SaveToCollectionTokens.Demo.dragUp + SaveToCollectionTokens.Demo.fingerFade {
            let fadeIn = min(max((t - SaveToCollectionTokens.Demo.dragDown) / SaveToCollectionTokens.Demo.fingerFade, 0), 1)
            let fadeOut = min(max((SaveToCollectionTokens.Demo.dragUp + SaveToCollectionTokens.Demo.fingerFade - t) / SaveToCollectionTokens.Demo.fingerFade, 0), 1)
            let u = SaveToCollectionMath.cosineEase((t - SaveToCollectionTokens.Demo.dragLifted) / (SaveToCollectionTokens.Demo.dragEnd - SaveToCollectionTokens.Demo.dragLifted))
            let centre = CGPoint(
                x: rect.midX + (layout.bookmark.x - rect.midX) * u + sin(.pi * u) * SaveToCollectionTokens.Demo.dragBow,
                y: rect.midY + (layout.bookmark.y - rect.midY) * u
            )
            let pressed = min(max((t - SaveToCollectionTokens.Demo.dragDown) / (SaveToCollectionTokens.Demo.dragLifted - SaveToCollectionTokens.Demo.dragDown), 0), 1)
            let release = min(max((SaveToCollectionTokens.Demo.dragUp + SaveToCollectionTokens.Timing.demoRelease - t) / SaveToCollectionTokens.Timing.demoRelease, 0), 1)
            return (centre, min(fadeIn, fadeOut), min(pressed, release))
        }
        for tap in [SaveToCollectionTokens.Demo.saveAll, SaveToCollectionTokens.Demo.saveAllAgain] {
            let start = tap - SaveToCollectionTokens.Demo.tapLead
            let end = tap + SaveToCollectionTokens.Demo.fingerFade
            if t >= start, t <= end {
                let opacity = min(
                    min(max((t - start) / SaveToCollectionTokens.Demo.fingerFade, 0), 1),
                    min(max((end - t) / SaveToCollectionTokens.Demo.fingerFade, 0), 1)
                )
                let down = min(max((t - start) / SaveToCollectionTokens.Timing.demoRelease, 0), 1)
                let up = min(max((end - t) / SaveToCollectionTokens.Timing.pressOut, 0), 1)
                return (layout.bookmark, opacity, min(down, up))
            }
        }
        return nil
    }

    private var canResume: Bool {
        !manualDragging &&
            dragCard == nil &&
            flights.isEmpty &&
            dealAt == nil &&
            riseProgress() == 0 &&
            visible.count == SaveToCollectionTokens.cards.filter { $0.pile == 0 }.count &&
            visible.first.map { SaveToCollectionTokens.cards[$0].pile == 0 } == true
    }

    private func processScript(from old: Double, to new: Double) {
        func crossed(_ value: Double) -> Bool { old < value && new >= value }
        let dragIndex = SaveToCollectionTokens.cards.firstIndex { $0.id == SaveToCollectionTokens.Demo.dragCard } ?? 0
        if crossed(SaveToCollectionTokens.Demo.dragLifted) {
            dragCard = dragIndex
            dimCard = dragIndex
            scriptDragging = true
            let rect = layout.rects[dragIndex]
            baseCentre = CGPoint(x: rect.midX, y: rect.midY)
        }
        if crossed(SaveToCollectionTokens.Demo.dragUp) {
            scriptDragging = false
            scriptDropAt = new
        }
        if crossed(SaveToCollectionTokens.Demo.dragUp + SaveToCollectionTokens.Timing.dropFlight) {
            dragCard = nil
            dimCard = nil
            scriptDropAt = nil
            gone.insert(dragIndex)
            land(silent: true)
        }
        if crossed(SaveToCollectionTokens.Demo.saveAll) {
            saveAll(silent: true)
        }
        if crossed(SaveToCollectionTokens.Demo.rise) {
            pileCount = SaveToCollectionTokens.cards.filter { $0.pile == 0 }.count
            startRise()
        }
        if crossed(SaveToCollectionTokens.Demo.rise + SaveToCollectionTokens.Timing.rise + SaveToCollectionTokens.Timing.riseHold) {
            nextPile()
        }
        if crossed(SaveToCollectionTokens.Demo.saveAllAgain) {
            saveAll(silent: true)
        }
        if crossed(SaveToCollectionTokens.Demo.riseAgain) {
            pileCount = SaveToCollectionTokens.cards.filter { $0.pile == 1 }.count
            startRise()
        }
        if crossed(SaveToCollectionTokens.Demo.riseAgain + SaveToCollectionTokens.Timing.rise + SaveToCollectionTokens.Timing.riseHold) {
            nextPile()
        }
    }

    private func updateManualMotion(delta: Double) {
        if let tween = pressTween {
            let progress = min(max((clock - tween.start) / tween.duration, 0), 1)
            press = SaveToCollectionMath.mix(tween.from, tween.to, SaveToCollectionMath.cubicBezier(progress, SaveToCollectionTokens.Easing.easeOut))
            if progress >= 1 { pressTween = nil }
        }
        if let tween = dimTween {
            let progress = min(max((clock - tween.start) / tween.duration, 0), 1)
            dimValue = SaveToCollectionMath.mix(tween.from, tween.to, progress)
            if progress >= 1 {
                dimTween = nil
                if dimValue == 0 { dimCard = nil }
            }
        }
        dragX.step(delta: delta, token: SaveToCollectionTokens.Spring.back)
        dragY.step(delta: delta, token: SaveToCollectionTokens.Spring.back)
        dragScale.step(delta: delta, token: manualDragging ? SaveToCollectionTokens.Spring.lift : SaveToCollectionTokens.Spring.back)
        dragTilt.step(delta: delta, token: SaveToCollectionTokens.Spring.tilt)
        if !manualDragging, dragCard != nil,
           abs(dragX.value - baseCentre.x) < SaveToCollectionTokens.Clock.returnTolerance,
           abs(dragY.value - baseCentre.y) < SaveToCollectionTokens.Clock.returnTolerance {
            dragCard = nil
            if dimValue == 0 { dimCard = nil }
        }
    }

    private func updateTimedState() {
        let completed = flights.filter { clock >= $0.start + $0.duration }
        if !completed.isEmpty {
            let ids = Set(completed.map(\.id))
            flights.removeAll { ids.contains($0.id) }
            completed.forEach { _ in land(silent: autoPlay && !paused) }
            completePileIfNeeded()
        }
        if let nextPileAt, clock >= nextPileAt {
            self.nextPileAt = nil
            nextPile()
        }
        if let riseAt {
            let up = reduceMotion ? SaveToCollectionTokens.Timing.reduced : SaveToCollectionTokens.Timing.rise
            let down = reduceMotion ? SaveToCollectionTokens.Timing.reduced : SaveToCollectionTokens.Timing.riseDown
            if clock > riseAt + up + SaveToCollectionTokens.Timing.riseHold + down {
                self.riseAt = nil
            }
        }
        if let ringAt, clock - ringAt >= SaveToCollectionTokens.Timing.landRing {
            self.ringAt = nil
        }
        if let bounceAt, clock - bounceAt >= SaveToCollectionTokens.Timing.bounceSettle {
            self.bounceAt = nil
        }
        if let dealAt {
            let count = SaveToCollectionTokens.cards.filter { $0.pile == pile }.count
            let end = dealAt + Double(max(count - 1, 0)) * SaveToCollectionTokens.Timing.dealStagger + SaveToCollectionTokens.Timing.deal
            if clock >= end { self.dealAt = nil }
        }
    }

    private func fly(cards: [Int], silent: Bool) {
        let cards = cards.filter { card in
            !gone.contains(card) && !flights.contains(where: { $0.card == card })
        }
        guard !cards.isEmpty else { return }
        if reduceMotion {
            cards.forEach {
                gone.insert($0)
                land(silent: silent)
            }
            completePileIfNeeded()
            return
        }
        for (order, card) in cards.enumerated() {
            gone.insert(card)
            addFlight(
                card: card,
                from: layout.rects[card],
                delay: Double(order) * SaveToCollectionTokens.Timing.flockStagger,
                duration: SaveToCollectionTokens.Timing.flight
            )
        }
    }

    private func addFlight(
        card: Int,
        from: CGRect,
        delay: Double,
        duration: Double,
        isDrop: Bool = false,
        startScale: Double = 1
    ) {
        flights.append(.init(
            id: flightSequence,
            card: card,
            from: from,
            start: clock + delay,
            duration: duration,
            isDrop: isDrop,
            startScale: startScale
        ))
        flightSequence += 1
    }

    private func saveAll(silent: Bool) {
        let cards = visible.reversed()
        guard !cards.isEmpty else { return }
        if !reduceMotion { rushAt = clock }
        fly(cards: Array(cards), silent: silent)
    }

    private func land(silent: Bool) {
        saved += 1
        bounceAt = clock
        if !reduceMotion { ringAt = clock }
        if !silent { playHaptic(.soft) }
    }

    private func completePileIfNeeded() {
        guard !autoPlay || paused else { return }
        guard visible.isEmpty, flights.isEmpty, dragCard == nil, riseAt == nil else { return }
        pileCount = SaveToCollectionTokens.cards.filter { $0.pile == pile }.count
        countAt = clock
        playHaptic(.success)
        startRise()
        nextPileAt = clock + SaveToCollectionTokens.Timing.rise + SaveToCollectionTokens.Timing.riseHold
    }

    private func startRise() {
        riseAt = clock
        countAt = clock
    }

    private func nextPile() {
        pile = pile == 0 ? 1 : 0
        gone.removeAll()
        dealAt = clock
        rushAt = nil
    }

    private func cancelDragToRest() {
        manualDragging = false
        scriptDragging = false
        scriptDropAt = nil
        magnetOpen = 0
        dragX.target = baseCentre.x
        dragY.target = baseCentre.y
        dragScale.target = 1
        dragTilt.target = 0
        beginDim(to: 0)
    }

    private func target(at point: CGPoint) -> Int {
        if hypot(point.x - layout.bookmark.x, point.y - layout.bookmark.y) < SaveToCollectionTokens.Layout.bookmarkHitRadius {
            return -2
        }
        return hitCard(at: point)
    }

    private func hitCard(at point: CGPoint) -> Int {
        for index in visible.reversed() where layout.rects[index].contains(point) {
            return index
        }
        return -1
    }

    private func beginPress(to value: Double, duration: Double) {
        pressTween = .init(from: press, to: value, start: clock, duration: duration)
    }

    private func beginDim(to value: Double) {
        dimTween = .init(
            from: dimValue,
            to: value,
            start: clock,
            duration: SaveToCollectionTokens.Timing.dim
        )
    }

    private func resetToRest() {
        pile = 0
        gone.removeAll()
        flights.removeAll()
        dragCard = nil
        saved = 0
        pileCount = 0
        dealAt = nil
        rushAt = nil
        riseAt = nil
        ringAt = nil
        bounceAt = nil
        countAt = nil
        nextPileAt = nil
        scriptDragging = false
        scriptDropAt = nil
        manualDragging = false
        magnetOpen = 0
        press = 0
        pressCard = -1
        dimCard = nil
        dimValue = 0
        dimTween = nil
        quiet = 0
        scriptTime = 0
        clock = 0
    }

    private enum Haptic {
        case light, medium, heavy, soft, success
    }

    private func playHaptic(_ kind: Haptic) {
        guard !autoPlay || paused else { return }
        switch kind {
        case .light:
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case .medium:
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case .heavy:
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        case .soft:
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        case .success:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }
}

private struct SaveToCollectionTween {
    let from: Double
    let to: Double
    let start: Double
    let duration: Double
}

private struct SaveToCollectionSpringValue {
    var value = 0.0
    var velocity = 0.0
    var target = 0.0

    mutating func snap(to value: Double) {
        self.value = value
        target = value
        velocity = 0
    }

    mutating func step(delta: Double, token: SaveToCollectionTokens.SpringToken) {
        var remaining = delta
        while remaining > 0 {
            let step = min(remaining, SaveToCollectionTokens.Clock.integrationStep)
            let acceleration = (-token.stiffness * (value - target) - token.damping * velocity) / token.mass
            velocity += acceleration * step
            value += velocity * step
            remaining -= step
        }
    }
}

enum SaveToCollectionMath {
    static func clamp(_ value: Double) -> Double { min(max(value, 0), 1) }
    static func mix(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * clamp(progress)
    }
    static func cosineEase(_ value: Double) -> Double {
        (1 - cos(.pi * clamp(value))) / 2
    }
    static func cubicInOut(_ value: Double) -> Double {
        let value = clamp(value)
        return value < 0.5 ? 4 * value * value * value : 1 - pow(-2 * value + 2, 3) / 2
    }
    static func cubicSpeed(_ value: Double) -> Double {
        let value = clamp(value)
        return value < 0.5 ? 12 * value * value : 3 * pow(-2 * value + 2, 2)
    }
    static func cubicBezier(_ value: Double, _ control: (Double, Double, Double, Double)) -> Double {
        let x = clamp(value)
        var low = 0.0
        var high = 1.0
        var t = x
        for _ in 0..<12 {
            t = (low + high) / 2
            let estimate = cubic(t, control.0, control.2)
            if estimate < x { low = t } else { high = t }
        }
        return cubic(t, control.1, control.3)
    }
    static func springDecay(_ time: Double, token: SaveToCollectionTokens.SpringToken) -> Double {
        guard time > 0 else { return 1 }
        let omega = sqrt(token.stiffness / token.mass)
        let damping = token.damping / (2 * sqrt(token.stiffness * token.mass))
        if damping < 1 {
            let wd = omega * sqrt(1 - damping * damping)
            return exp(-damping * omega * time) * (cos(wd * time) + damping * omega / wd * sin(wd * time))
        }
        return exp(-omega * time)
    }
    private static func cubic(_ t: Double, _ first: Double, _ second: Double) -> Double {
        let u = 1 - t
        return 3 * u * u * t * first + 3 * u * t * t * second + t * t * t
    }
}
