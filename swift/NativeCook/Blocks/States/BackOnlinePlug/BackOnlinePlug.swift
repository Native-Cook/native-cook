import SwiftUI
import UIKit

struct BackOnlinePlug: UIViewRepresentable {
    static let loop = 7.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    func makeUIView(context: Context) -> BackOnlinePlugSurface { BackOnlinePlugSurface() }
    func updateUIView(_ view: BackOnlinePlugSurface, context: Context) {
        view.autoPlay = autoPlay; view.reduced = reduced; view.active = scene == .active
        view.updateClock()
    }
}

final class BackOnlinePlugSurface: UIView {
    typealias Motion = BackOnlinePlugMotion
    var autoPlay = false, active = true
    var reduced = false { didSet {
        if reduced != oldValue {
            previous = nil
            wasOnline = reduced ? fadeTo >= 0.5 : (touch.manual || autoPlay) && touch.online(clock) && (!touch.manual || clock >= touch.vAt)
            refreshAccessibility(); setNeedsDisplay()
        }
    } }
    private var link: CADisplayLink?
    private var previous: Double?, clock = 0.0
    private var touch = Motion.Touch()
    private var wasOnline = false
    private var fade = 0.0, fadeFrom = 0.0, fadeTo = 0.0, fadeAt = -1.0
    private var press = CGPoint.zero, moved = 0.0, onPlug = false, onButton = false
    private weak var primary: UITouch?
    private var pressTimestamp = 0.0
    private let feedback = UIImpactFeedbackGenerator(style: .medium)
    private lazy var heading = UIAccessibilityElement(accessibilityContainer: self)
    private lazy var action = BackOnlinePlugAccessibilityAction(accessibilityContainer: self)
    private let art = BackOnlinePlugArt()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = true; backgroundColor = .black; isMultipleTouchEnabled = false
        action.activate = { [weak self] in self?.toggle(); return true }
        heading.accessibilityTraits = .header; action.accessibilityTraits = .button
        heading.accessibilityIdentifier = "back-online-heading"; action.accessibilityIdentifier = "back-online-action"
        accessibilityElements = [heading, action]
        refreshAccessibility()
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func didMoveToWindow() { super.didMoveToWindow(); updateClock() }
    override func layoutSubviews() { super.layoutSubviews(); refreshAccessibility() }
    func updateClock() {
        let running = window != nil && active && ((!reduced && (autoPlay || touch.manual)) || (reduced && fadeAt >= 0))
        if running && link == nil {
            let l = CADisplayLink(target: self, selector: #selector(tick(_:)))
            l.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
            l.add(to: .main, forMode: .common); link = l
        } else if !running { link?.invalidate(); link = nil; previous = nil }
    }
    @objc private func tick(_ sender: CADisplayLink) {
        let dt = previous.map { max(0,sender.timestamp-$0) } ?? 0
        previous = sender.timestamp
        if reduced {
            fade = fadeFrom+(fadeTo-fadeFrom)*Motion.seg(sender.timestamp,fadeAt,fadeAt+0.2)
            if sender.timestamp >= fadeAt+0.2 { fade = fadeTo; fadeAt = -1; updateClock() }
        } else {
            clock += dt
            touch.step(clock,dt: min(0.1,dt),autoPlay: autoPlay)
            let live = (touch.manual || autoPlay) && touch.online(clock) && (!touch.manual || clock >= touch.vAt)
            if live != wasOnline {
                wasOnline = live; refreshAccessibility()
                if live && touch.manual { seatFeedback() }
            }
        }
        setNeedsDisplay()
    }
    private func seatFeedback() {
        feedback.impactOccurred(); UIAccessibility.post(notification: .announcement, argument: "Back online")
    }
    private func toggle() {
        if reduced {
            fadeFrom = fade; fadeTo = fade < 0.5 ? 1 : 0; fadeAt = CACurrentMediaTime()
            wasOnline = fadeTo == 1
            if wasOnline { seatFeedback() }; refreshAccessibility(); updateClock()
        } else {
            touch.takeOver(clock); touch.toggle(clock); touch.lastTouch = clock
            updateClock()
        }
    }
    private var layout: BackOnlinePlugArt.Layout { .init(size: bounds.size) }
    private func begin(_ point: CGPoint) {
        press = point; moved = 0
        onButton = layout.buttonHit(point)
        if reduced { onPlug = layout.plugHit(point,frame: fade < 0.5 ? .off : .on); return }
        touch.takeOver(clock)
        onPlug = layout.plugHit(point,frame: touch.frame(clock))
        if onPlug && touch.canGrab(clock) { touch.grab(clock) }
        touch.touching = true; touch.lastTouch = clock; feedback.prepare()
        updateClock()
    }
    private func end(tap: Bool) {
        if reduced { if tap && (onPlug || (onButton && fade < 0.5)) { toggle() }; return }
        if onPlug && touch.pk == 1 { touch.release(clock,tap: tap) }
        else if tap && onPlug { touch.toggle(clock) }
        else if tap && onButton && touch.vk != 1 { touch.toggle(clock) }
        touch.touching = false; touch.lastTouch = clock; onPlug = false
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard primary == nil, let finger = touches.first else { return }
        primary = finger; pressTimestamp = finger.timestamp; begin(finger.location(in:self))
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let finger = primary, touches.contains(finger) else { return }
        let point = finger.location(in:self), dx = point.x-press.x, dy = point.y-press.y
        moved = max(moved,hypot(dx,dy))
        if !reduced && onPlug && touch.pk == 1 { touch.drag(dx,dy,clock) }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let finger = primary, touches.contains(finger) else { return }
        end(tap: finger.timestamp-pressTimestamp < 0.3 && moved < 10); primary = nil
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let finger = primary, touches.contains(finger) else { return }
        end(tap:false); primary = nil
    }
    private func refreshAccessibility() {
        let l = layout, on = wasOnline
        heading.accessibilityLabel = on ? "Back online. Your 3 drafts are syncing now." : "Still offline. Plug back in to sync 3 drafts."
        heading.accessibilityFrameInContainerSpace = CGRect(x:l.ox+30,y:100*l.fy,width:300,height:300*l.fy)
        action.accessibilityLabel = on ? "Disconnect" : "Reconnect"; action.accessibilityValue = on ? "Online" : "Offline"
        action.accessibilityFrameInContainerSpace = CGRect(x:l.ox+32,y:772*l.fy,width:on ? 160 : 329,height:on ? 44 : 58)
    }
    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        if reduced {
            art.draw(context,size:bounds.size,frame:.off)
            if fade > 0 {
                context.saveGState(); context.setAlpha(fade); context.beginTransparencyLayer(auxiliaryInfo:nil)
                art.draw(context,size:bounds.size,frame:.on)
                context.endTransparencyLayer(); context.restoreGState()
            }
        } else { art.draw(context,size:bounds.size,frame:touch.manual || autoPlay ? touch.frame(clock) : .off) }
    }
}

private final class BackOnlinePlugAccessibilityAction: UIAccessibilityElement {
    var activate: (() -> Bool)?
    override func accessibilityActivate() -> Bool { activate?() ?? false }
}
