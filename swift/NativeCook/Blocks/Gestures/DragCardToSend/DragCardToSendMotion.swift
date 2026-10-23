import SwiftUI
import UIKit

@MainActor
final class DragCardToSendMotion: NSObject, ObservableObject {
    typealias T = DragCardToSendTokens

    enum Phase { case rest, pulling, holding, docked, sending, closing, sent }

    @Published var phase: Phase = .rest
    @Published var g = 0.0
    @Published var cardOffset = CGSize.zero
    @Published var cardScale = 1.0
    @Published var cardPress = 1.0
    @Published var cardOpacity = 1.0
    @Published var sway = 0.0
    @Published var hoverIndex = -1
    @Published var hoverAmount = 0.0
    @Published var sendIndex = -1
    @Published var ring = 0.0
    @Published var check = 0.0
    @Published var sent = 0.0
    @Published var undo = 0.0
    @Published var clock = 0.0
    @Published var balance = T.startBalance
    @Published var transactions = T.transactions
    @Published var lastPerson: T.Person?

    private let autoPlay: Bool
    private var reduceMotion = false
    private var viewport = T.referenceSize
    private var displayLink: CADisplayLink?
    private var lastTargetTimestamp: CFTimeInterval?
    private var loopTime = 0.0
    private var paused: Bool
    private var touching = false
    private var quiet = 0.0
    private var dragStart = CGPoint.zero
    private var cardStart = CGSize.zero
    private var lastPoint = CGPoint.zero
    private var selectedIndex = -1
    private var interactiveReceipt = false
    private var receiptRemaining = 0.0

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        paused = !autoPlay
        super.init()
    }

    var isSheetInteractive: Bool { phase == .docked || phase == .holding }
    var isReduced: Bool { reduceMotion }
    var cardWidth: Double { viewport.width - T.layout.side * 2 }
    var cardCenter: CGPoint { CGPoint(x: viewport.width / 2, y: T.layout.cardTop + T.layout.cardHeight / 2) }
    var people: [T.Person] { T.favourites + T.contacts }

    func start(size: CGSize, reduceMotion: Bool) {
        viewport = size
        self.reduceMotion = reduceMotion
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(frame(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        lastTargetTimestamp = nil
    }

    @objc private func frame(_ link: CADisplayLink) {
        let rawDelta = lastTargetTimestamp.map { link.targetTimestamp - $0 } ?? link.duration
        lastTargetTimestamp = link.targetTimestamp
        let delta = min(max(rawDelta, 0), T.maximumFrameDelta)
        clock = (clock + delta).truncatingRemainder(dividingBy: T.loop)

        if interactiveReceipt, phase == .sent {
            receiptRemaining = max(0, receiptRemaining - delta)
            undo = receiptRemaining / T.send.undo
            if receiptRemaining == 0 {
                interactiveReceipt = false
                sent = 0
                phase = .rest
            }
        }

        if autoPlay, !paused {
            loopTime = (loopTime + delta / T.qaSlowmo).truncatingRemainder(dividingBy: T.loop)
            applyAutoplay(T.qaHoldAt.map { min(loopTime, $0) } ?? loopTime)
        } else if autoPlay, paused, !touching {
            quiet += delta
            if quiet >= T.resumeAfter {
                resetToRest()
                loopTime = 0
                paused = false
                quiet = 0
            }
        }
    }

    private func applyAutoplay(_ time: Double) {
        resetVisualsForScript()
        let script = T.script
        if time < script.down { return }

        if time < script.pullEnd {
            let u = easeInOut((time - script.down - T.autoplay.pressBeat) / (script.pullEnd - script.down - T.autoplay.pressBeat))
            phase = .pulling
            cardOffset = CGSize(width: T.autoplay.pullX * u * T.pull.sideFollow, height: script.pullBy * u)
            g = reduceMotion ? 0 : min(cardOffset.height / T.pull.full, 1)
            cardPress = T.press.scale
            return
        }

        let target = targetPoint(T.autoplay.target)
        let start = CGPoint(x: cardCenter.x + T.autoplay.pullX * T.pull.sideFollow, y: cardCenter.y + script.pullBy)
        if time < script.hoverStart {
            let u = smooth((time - script.pullEnd) / (script.hoverStart - script.pullEnd))
            phase = .holding
            g = reduceMotion ? 2 : mix(T.autoplay.startingDrop, 2, u)
            cardScale = mix(1 - T.pull.shrink, T.tokenScale, u)
            cardOffset = CGSize(width: start.x - cardCenter.x, height: start.y - cardCenter.y)
            return
        }

        if time < script.hoverArrive {
            let u = easeInOut((time - script.hoverStart) / (script.hoverArrive - script.hoverStart))
            let control = CGPoint(x: target.x + T.autoplay.arcX, y: (start.y + target.y) / 2 + T.autoplay.arcY)
            let point = quadratic(start, control, target, u)
            phase = .holding
            g = 2
            cardScale = T.tokenScale
            cardOffset = CGSize(width: point.x - cardCenter.x, height: point.y - cardCenter.y)
            hoverIndex = u > T.autoplay.hoverStart ? T.autoplay.target : -1
            hoverAmount = smooth((u - T.autoplay.hoverStart) / T.autoplay.hoverSpan)
            return
        }

        if time < script.release {
            phase = .holding
            g = 2
            cardScale = T.tokenScale
            cardOffset = CGSize(width: target.x - cardCenter.x, height: target.y - cardCenter.y)
            hoverIndex = T.autoplay.target
            hoverAmount = 1
            return
        }

        let absorbEnd = script.release + T.send.absorb
        let ringEnd = absorbEnd + T.send.ring
        let closeStart = ringEnd + T.send.hold
        let closeEnd = closeStart + T.send.close
        if time < closeEnd {
            phase = time < closeStart ? .sending : .closing
            sendIndex = T.autoplay.target
            hoverIndex = T.autoplay.target
            let absorb = cubicBezier((time - script.release) / T.send.absorb, T.curve.absorb)
            hoverAmount = time < absorbEnd ? 1 + (T.autoplay.hoverPeak - 1) * smooth((absorb - T.autoplay.peakStart) / T.autoplay.peakSpan) : 1
            cardOffset = CGSize(width: target.x - cardCenter.x, height: target.y - cardCenter.y)
            cardScale = mix(T.tokenScale, T.send.absorbScale, absorb)
            cardOpacity = 1 - smooth((absorb - T.autoplay.fadeStart) / T.autoplay.fadeSpan)
            ring = easeInOut((time - absorbEnd) / T.send.ring)
            check = springPop((time - ringEnd) / T.autoplay.popDuration)
            if time >= closeStart { g = reduceMotion ? 2 : 2 * (1 - cubicBezier((time - closeStart) / T.send.close, T.curve.close)) }
            else { g = 2 }
            // Match the React Native receipt: it only begins during the final 45% of
            // the sheet close, then yields to the rest pill at the scripted undo tap.
            let receiptStart = closeStart + T.send.close * T.autoplay.pillSwapStart
            sent = time < receiptStart ? 0 : smooth((time - receiptStart) / T.send.pillSwap)
            return
        }

        phase = .sent
        lastPerson = T.favourites[T.autoplay.target]
        let undoStart = script.undoTap
        if time < undoStart {
            sent = 1
            undo = max(0, 1 - (time - closeEnd) / T.send.undo)
            let paidBalance = T.startBalance - T.amounts[T.defaultAmountIndex] * 100
            balance = Int(mix(Double(T.startBalance), Double(paidBalance), smooth((time - closeEnd) / T.send.roll)).rounded())
            if transactions.first?.id != "sent" { transactions.insert(sentTransaction(), at: 0) }
            let enter = springPop((time - closeEnd) / T.autoplay.popDuration)
            cardScale = mix(T.send.enterFrom, 1, enter)
            cardOpacity = smooth((time - closeEnd) / T.send.fade)
            return
        }

        let undoing = smooth((time - undoStart) / T.send.pillSwap)
        sent = 1 - undoing
        undo = 0
        let paidBalance = T.startBalance - T.amounts[T.defaultAmountIndex] * 100
        balance = Int(mix(Double(paidBalance), Double(T.startBalance), smooth((time - undoStart) / T.send.roll)).rounded())
        if undoing >= 1 {
            transactions.removeAll { $0.id == "sent" }
            phase = .rest
            lastPerson = nil
        }
    }

    func touchBegan(at point: CGPoint) {
        touching = true
        quiet = 0
        if autoPlay { paused = true }
        let currentCenter = CGPoint(x: cardCenter.x + cardOffset.width, y: cardCenter.y + cardOffset.height)
        guard abs(point.x - currentCenter.x) <= cardWidth * cardScale / 2,
              abs(point.y - currentCenter.y) <= T.layout.cardHeight * cardScale / 2,
              phase != .sending, phase != .closing else { return }
        dragStart = point
        lastPoint = point
        cardStart = cardOffset
        withAnimation(.easeOut(duration: T.press.duration)) { cardPress = T.press.scale }
        if phase == .holding || phase == .docked {
            phase = .holding
            cardOpacity = 1
            cardScale = T.tokenScale
        } else {
            if phase == .sent { undoPayment(haptic: false) }
            phase = .pulling
        }
    }

    func touchMoved(to point: CGPoint, velocityX: Double) {
        guard phase == .pulling || phase == .holding else { return }
        lastPoint = point
        if phase == .pulling {
            var dy = cardStart.height + point.y - dragStart.y
            if dy < 0 { dy *= T.pull.upResist }
            cardOffset = CGSize(width: cardStart.width + (point.x - dragStart.x) * T.pull.sideFollow, height: dy)
            if !reduceMotion { g = min(max(dy / T.pull.full, 0), 1) }
            if dy >= T.pull.commit { commit(at: point) }
        } else {
            cardOffset = CGSize(width: point.x - cardCenter.x, height: point.y - cardCenter.y)
            sway = min(max(velocityX * T.gesture.swayVelocity, -T.gesture.swayLimit), T.gesture.swayLimit)
            updateHover(point)
        }
    }

    func touchEnded(velocity: CGPoint) {
        touching = false
        quiet = 0
        withAnimation(.interpolatingSpring(mass: T.springHover.mass, stiffness: T.springHover.stiffness, damping: T.springHover.damping)) { cardPress = 1 }
        if phase == .pulling {
            if cardOffset.height > T.gesture.lightPull { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            returnHome(velocity: velocity)
        } else if phase == .holding {
            if hoverIndex >= 0 { send(to: hoverIndex) }
            else { dock() }
        }
    }

    func openSheet() {
        interruptAutoplay()
        phase = .docked
        withAnimation(.interpolatingSpring(mass: T.springOpen.mass, stiffness: T.springOpen.stiffness, damping: T.springOpen.damping)) { g = 2 }
        withAnimation(.linear(duration: T.send.fade)) { cardOpacity = 0 }
    }

    func select(_ index: Int) {
        interruptAutoplay()
        selectedIndex = index
        hoverIndex = index
        withAnimation(.interpolatingSpring(mass: T.springHover.mass, stiffness: T.springHover.stiffness, damping: T.springHover.damping)) { hoverAmount = 1 }
        UISelectionFeedbackGenerator().selectionChanged()
    }

    func sendSelected() {
        guard selectedIndex >= 0 else { return }
        send(to: selectedIndex)
    }

    func closeSheet() {
        interruptAutoplay()
        phase = .closing
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(.easeOut(duration: T.send.close)) { g = 0 }
        withAnimation(.easeOut(duration: T.send.fade)) { cardOpacity = 1; cardScale = 1; cardOffset = .zero }
        phase = .rest
    }

    func undoPayment(haptic: Bool = true) {
        interruptAutoplay()
        if haptic { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        interactiveReceipt = false
        receiptRemaining = 0
        withAnimation(.easeOut(duration: T.send.roll)) { balance = T.startBalance }
        transactions.removeAll { $0.id == "sent" }
        lastPerson = nil
        withAnimation(.easeOut(duration: T.send.pillSwap)) { sent = 0; undo = 0 }
        phase = .rest
    }

    private func commit(at point: CGPoint) {
        phase = .holding
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        cardScale = T.tokenScale
        cardOffset = CGSize(width: point.x - cardCenter.x, height: point.y - cardCenter.y)
        withAnimation(.interpolatingSpring(mass: T.springOpen.mass, stiffness: T.springOpen.stiffness, damping: T.springOpen.damping)) { g = 2 }
    }

    private func send(to index: Int) {
        phase = .sending
        sendIndex = index
        let target = targetPoint(index)
        withAnimation(.timingCurve(T.curve.absorb.0, T.curve.absorb.1, T.curve.absorb.2, T.curve.absorb.3, duration: T.send.absorb)) {
            cardOffset = CGSize(width: target.x - cardCenter.x, height: target.y - cardCenter.y)
            cardScale = T.send.absorbScale
            cardOpacity = 0
            hoverAmount = T.autoplay.hoverPeak
        }
        withAnimation(.easeInOut(duration: T.send.ring).delay(T.send.absorb)) { ring = 1 }
        withAnimation(.interpolatingSpring(mass: T.springCheck.mass, stiffness: T.springCheck.stiffness, damping: T.springCheck.damping).delay(T.send.absorb + T.send.ring)) { check = 1 }
        let finishDelay = T.send.absorb + T.send.ring + T.send.hold
        withAnimation(.timingCurve(T.curve.close.0, T.curve.close.1, T.curve.close.2, T.curve.close.3, duration: T.send.close).delay(finishDelay)) { g = 0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + finishDelay + T.send.close) { [weak self] in self?.finishSend(index) }
    }

    private func finishSend(_ index: Int) {
        lastPerson = people[index]
        withAnimation(.easeOut(duration: T.send.roll)) {
            balance -= T.amounts[T.defaultAmountIndex] * 100
            transactions.insert(sentTransaction(name: people[index].name), at: 0)
        }
        phase = .sent
        g = 0
        ring = 0
        check = 0
        sendIndex = -1
        hoverIndex = -1
        cardOffset = .zero
        cardScale = T.send.enterFrom
        cardOpacity = 1
        withAnimation(.interpolatingSpring(mass: T.springEnter.mass, stiffness: T.springEnter.stiffness, damping: T.springEnter.damping)) { cardScale = 1 }
        withAnimation(.easeOut(duration: T.send.pillSwap)) { sent = 1 }
        interactiveReceipt = true
        receiptRemaining = T.send.undo
        undo = 1
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func dock() {
        phase = .docked
        hoverIndex = -1
        hoverAmount = 0
        withAnimation(.easeOut(duration: T.send.fade)) { cardOpacity = 0; cardOffset = .zero; cardScale = 1; sway = 0 }
    }

    private func returnHome(velocity: CGPoint) {
        phase = .rest
        let spring = T.springRetract
        withAnimation(.interpolatingSpring(mass: spring.mass, stiffness: spring.stiffness, damping: spring.damping, initialVelocity: velocity.y / max(abs(cardOffset.height), 1))) {
            cardOffset = .zero
            cardScale = 1
            sway = 0
            g = 0
        }
    }

    private func updateHover(_ point: CGPoint) {
        var best = -1
        var bestDistance = Double.greatestFiniteMagnitude
        for index in people.indices {
            let target = targetPoint(index)
            let distance = index < T.favourites.count ? hypot(point.x - target.x, point.y - target.y) : abs(point.y - target.y) * T.gesture.rowDistanceWeight
            if distance < bestDistance { best = index; bestDistance = distance }
        }
        let next = bestDistance < T.magnet.over ? best : -1
        if next != hoverIndex, next >= 0 { UISelectionFeedbackGenerator().selectionChanged() }
        hoverIndex = next
        hoverAmount = next >= 0 ? 1 : 0
    }

    func targetPoint(_ index: Int) -> CGPoint {
        if index < T.favourites.count {
            let innerX = T.sheet.inset + T.sheet.favPad
            let innerWidth = viewport.width - innerX * 2
            return CGPoint(x: innerX + innerWidth * (Double(index) + 0.5) / 4, y: T.sheet.top + T.sheet.favTop + T.sheet.favSize / 2)
        }
        let row = index - T.favourites.count
        return CGPoint(x: T.sheet.inset + T.sheet.rowAvatarX, y: T.sheet.top + T.sheet.rowsTop + Double(row) * T.sheet.rowHeight + T.sheet.rowHeight / 2)
    }

    private func interruptAutoplay() {
        guard autoPlay else { return }
        paused = true
        quiet = 0
    }

    private func resetVisualsForScript() {
        phase = .rest
        g = 0
        cardOffset = .zero
        cardScale = 1
        cardPress = 1
        cardOpacity = 1
        sway = 0
        hoverIndex = -1
        hoverAmount = 0
        sendIndex = -1
        ring = 0
        check = 0
        sent = 0
        undo = 0
        balance = T.startBalance
        transactions.removeAll { $0.id == "sent" }
        lastPerson = nil
        interactiveReceipt = false
        receiptRemaining = 0
    }

    private func resetToRest() { resetVisualsForScript() }

    private func sentTransaction(name: String = "Samir") -> T.Transaction {
        T.Transaction(id: "sent", name: name, date: "Just now", amount: -T.amounts[T.defaultAmountIndex] * 100, incoming: false)
    }

    private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * min(max(t, 0), 1) }
    private func smooth(_ value: Double) -> Double { let t = min(max(value, 0), 1); return t * t * (3 - 2 * t) }
    private func easeInOut(_ value: Double) -> Double { let t = min(max(value, 0), 1); return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
    private func cubicBezier(_ value: Double, _ curve: (Double, Double, Double, Double)) -> Double {
        let x = min(max(value, 0), 1)
        var low = 0.0
        var high = 1.0
        var t = x
        for _ in 0..<10 {
            t = (low + high) / 2
            let sampleX = bezierCoordinate(t, curve.0, curve.2)
            if sampleX < x { low = t } else { high = t }
        }
        return bezierCoordinate(t, curve.1, curve.3)
    }
    private func bezierCoordinate(_ t: Double, _ p1: Double, _ p2: Double) -> Double {
        let u = 1 - t
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t
    }
    private func springPop(_ value: Double) -> Double { let t = min(max(value, 0), 1); return min(1.12, 1 - exp(-8 * t) * cos(12 * t)) }
    private func quadratic(_ a: CGPoint, _ b: CGPoint, _ c: CGPoint, _ t: Double) -> CGPoint {
        let u = min(max(t, 0), 1)
        return CGPoint(x: (1-u)*(1-u)*a.x + 2*(1-u)*u*b.x + u*u*c.x, y: (1-u)*(1-u)*a.y + 2*(1-u)*u*b.y + u*u*c.y)
    }
}
