import Combine
import QuartzCore
import UIKit

final class BeamCheckoutStepperController: NSObject, ObservableObject {
    @Published private(set) var revision = 0
    @Published private(set) var form = BeamCheckoutStepperForm()
    @Published private(set) var currentLabel = "Continue"
    @Published private(set) var previousLabel = "Continue"

    let motion: BeamCheckoutStepperMotion
    let autoPlay: Bool
    private var link: CADisplayLink?
    private var lastStamp = 0.0
    private var remainder = 0.0
    private var demoTime = 0.0
    private var demoCursor = 0
    private var demoActive: Bool
    private var demoBegun = false
    private var demoResuming = false
    private var demoScrubbing = false
    private var idle = 0.0
    private var fingers = 0
    private var focused = false
    private var deliveryOrigin = 0.0

    init(autoPlay: Bool, still: Bool) {
        self.autoPlay = autoPlay
        demoActive = autoPlay
        motion = BeamCheckoutStepperMotion(still: still)
        super.init()
    }

    func start() {
        guard link == nil else { return }
        let displayLink = CADisplayLink(target: self, selector: #selector(displayFrame(_:)))
        displayLink.add(to: .main, forMode: .common)
        link = displayLink
    }

    func stop() {
        link?.invalidate()
        link = nil
        lastStamp = 0
    }

    func setReducedMotion(_ value: Bool) {
        motion.still = value
        revision &+= 1
    }

    @objc private func displayFrame(_ link: CADisplayLink) {
        if lastStamp == 0 { lastStamp = link.timestamp; return }
        let dt = min(max(0, link.timestamp - lastStamp), 1.0 / 30.0)
        lastStamp = link.timestamp
        remainder += dt
        let h = 1.0 / 240.0
        let count = Int((remainder + 1e-10) * 240)
        remainder -= Double(count) * h
        for _ in 0..<count {
            motion.tick(h)
            processEvents()
            stepDemo(h)
        }
        revision &+= 1
    }

    private func processEvents() {
        let events = motion.events
        motion.events.removeAll()
        for event in events {
            switch event {
            case .settled(let target):
                form.step = target
                form.furthest = max(form.furthest, target)
                form.errorField = -1
                form.errorMessage = ""
                form.paid = target == 4
            case .cleared:
                form = BeamCheckoutStepperForm()
            case .restarted:
                form = BeamCheckoutStepperForm()
                if demoResuming {
                    demoResuming = false
                    demoActive = true
                    demoBegun = true
                    demoTime = 0
                    demoCursor = 0
                    idle = 0
                }
            case .newOrder:
                form.newOrder = true
            case .paid:
                form.paid = true
            case .haptic(let kind):
                if !motion.silent {
                    switch kind {
                    case 0: UISelectionFeedbackGenerator().selectionChanged()
                    case 1: UIImpactFeedbackGenerator(style: .soft).impactOccurred()
                    case 2: UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
                    case 3: UINotificationFeedbackGenerator().notificationOccurred(.error)
                    default: UINotificationFeedbackGenerator().notificationOccurred(.success)
                    }
                }
            }
        }
        updateLabel()
    }

    private func updateLabel() {
        let label = form.cta
        guard label != currentLabel else { return }
        previousLabel = currentLabel
        currentLabel = label
        motion.changeLabel()
    }

    private struct DemoEvent {
        var at: Double
        var action: Action
    }

    private enum Action {
        case edit(Int, String)
        case focus(Int)
        case press(Int, Bool)
        case next
        case delivery(Bool)
    }

    private static let demoEvents: [DemoEvent] = {
        var events: [DemoEvent] = []
        func add(_ at: Double, _ action: Action) { events.append(.init(at: at, action: action)) }
        func type(_ at: Double, _ field: Int, _ value: String) {
            let characters = Array(value)
            for i in 1...characters.count {
                add(at + Double(i - 1) * 0.07, .edit(field, String(characters.prefix(i))))
            }
        }
        func tap(_ at: Double, delivery: Bool = false) {
            let index = delivery ? BeamCheckoutStepperF.segPress : BeamCheckoutStepperF.ctaPress
            let duration = delivery ? 0.1 : 0.12
            add(at, .press(index, true))
            add(at + duration, .press(index, false))
            add(at + duration, delivery ? .delivery(true) : .next)
        }
        add(0.5, .focus(0)); type(0.7, 0, "mira@northlane.co"); tap(2.2)
        add(3.4, .focus(1)); type(3.5, 1, "18 Alder Lane")
        add(4.55, .focus(2)); type(4.65, 2, "Portland")
        add(5.35, .focus(3)); type(5.45, 3, "97209")
        add(6, .focus(-1)); tap(6.3)
        add(7.5, .focus(4)); type(7.6, 4, "4242424242424241")
        add(8.85, .focus(5)); type(8.95, 5, "0832")
        add(9.35, .focus(6)); type(9.45, 6, "314")
        add(9.8, .focus(-1)); tap(10)
        add(11.3, .edit(4, "424242424242424"))
        add(11.5, .edit(4, "4242424242424242"))
        add(11.75, .focus(-1)); tap(11.9)
        tap(15.1, delivery: true); tap(15.7); tap(17.3); tap(20.6)
        return events.sorted { $0.at < $1.at }
    }()

    private func stepDemo(_ dt: Double) {
        guard autoPlay, motion.frame[BeamCheckoutStepperF.present] >= 1 else { return }
        if !demoActive {
            if fingers == 0 && !focused && !demoResuming {
                idle += dt
                if idle >= 3.5 {
                    demoResuming = true
                    motion.silent = true
                    motion.restart()
                }
            }
            return
        }
        if !demoBegun { demoBegun = true; return }
        demoTime += dt
        if demoTime >= 22 - 1e-8 {
            demoTime -= 22
            demoCursor = 0
        }
        motion.silent = true
        while demoCursor < Self.demoEvents.count && Self.demoEvents[demoCursor].at <= demoTime + 1e-8 {
            perform(Self.demoEvents[demoCursor].action)
            demoCursor += 1
        }
        let local = max(0, min(0.7, demoTime - 13.6))
        let fingerDown = demoTime >= 13.6 && demoTime < 14.3
        if fingerDown && !demoScrubbing {
            motion.beginDrag()
            demoScrubbing = true
        }
        if fingerDown || demoScrubbing {
            let displacement: Double
            let speed: Double
            if local < 0.6 {
                let u = local / 0.6
                let now = BeamCheckoutStepperMotion.bezier(u, 0.23, 1, 0.32, 1)
                let before = BeamCheckoutStepperMotion.bezier(max(0, u - 0.0001), 0.23, 1, 0.32, 1)
                displacement = -1.975 * now
                speed = -1.975 * (now - before) / 0.00006
            } else {
                displacement = -1.975 - 0.25 * (local - 0.6)
                speed = -0.25
            }
            motion.drag(displacement, speed)
            if !fingerDown {
                motion.endDrag(speed)
                demoScrubbing = false
            }
        }
    }

    private func perform(_ action: Action) {
        switch action {
        case .edit(let field, let value): edit(field, value, scripted: true)
        case .focus(let field): focus(field, scripted: true)
        case .press(let index, let down): motion.press(index, down)
        case .next: next(scripted: true)
        case .delivery(let selected): delivery(selected, scripted: true)
        }
    }

    func touch(_ down: Bool) {
        fingers = max(0, fingers + (down ? 1 : -1))
        idle = 0
        if autoPlay && down && demoActive {
            demoActive = false
            motion.press(BeamCheckoutStepperF.ctaPress, false)
            motion.press(BeamCheckoutStepperF.segPress, false)
            if demoScrubbing {
                motion.endDrag(motion.frame[BeamCheckoutStepperF.velocity])
                demoScrubbing = false
            }
        }
        if down { motion.silent = false }
    }

    func focus(_ field: Int, scripted: Bool = false) {
        if !scripted {
            touch(true)
            touch(false)
            focused = field >= 0
        }
        motion.focus(field)
    }

    func edit(_ field: Int, _ value: String, scripted: Bool = false) {
        if !scripted { touch(true); touch(false) }
        let formatted = BeamCheckoutStepperForm.format(field, value)
        form.values[field] = formatted
        motion.setFilled(field, !formatted.isEmpty)
        if form.errorField == field {
            form.errorField = -1
            form.errorMessage = ""
            motion.clearError()
        }
        revision &+= 1
    }

    func next(scripted: Bool = false) {
        if !scripted { touch(true); touch(false) }
        if form.step == 3 && form.paid { return }
        if form.step == 4 {
            guard !form.restarting else { return }
            form.restarting = true
            motion.restart()
            updateLabel()
            return
        }
        if let error = form.validation() {
            form.errorField = error.0
            form.errorMessage = error.1
            motion.reject(error.0)
            if !scripted { UIAccessibility.post(notification: .announcement, argument: error.1) }
        } else {
            form.errorField = -1
            form.errorMessage = ""
            if form.step == 3 { motion.pay() }
            else if form.step < form.furthest { motion.go(form.furthest) }
            else { motion.advance() }
        }
        updateLabel()
        revision &+= 1
    }

    func go(_ target: Int) {
        touch(true)
        touch(false)
        if !form.paid && target <= form.furthest { motion.go(target) }
    }

    func delivery(_ selected: Bool, scripted: Bool = false) {
        if !scripted { touch(true); touch(false) }
        form.express = selected
        motion.setDelivery(selected)
        updateLabel()
        revision &+= 1
    }

    func press(_ index: Int, down: Bool) {
        touch(down)
        motion.press(index, down)
    }

    func beginRail() {
        touch(true)
        motion.beginDrag()
    }

    func updateRail(_ displacement: Double, _ speed: Double) {
        motion.drag(displacement / 68, speed / 68)
    }

    func endRail(_ speed: Double) {
        motion.endDrag(speed / 68)
        touch(false)
    }

    func beginDeliveryDrag() {
        touch(true)
        deliveryOrigin = motion.frame[BeamCheckoutStepperF.segLead]
    }

    func updateDeliveryDrag(_ distance: Double) {
        motion.dragDelivery(deliveryOrigin + distance / 161)
    }

    func endDeliveryDrag(_ speed: Double) {
        let selected = motion.endDelivery(speed / 161)
        form.express = selected
        touch(false)
    }
}
