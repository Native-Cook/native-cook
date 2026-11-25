import SwiftUI
import UIKit

/// A forecast drawn from the same eight-second clock as the RN reference.
struct KiteWindCard: View {
    static let loop = 8.0
    var autoPlay = false
    var body: some View {
        KiteWindSurface(autoPlay: autoPlay).ignoresSafeArea().preferredColorScheme(.dark)
    }
}

private struct KiteWindSurface: UIViewRepresentable {
    let autoPlay: Bool
    func makeUIView(context: Context) -> KiteWindView { KiteWindView(autoPlay: autoPlay) }
    func updateUIView(_ view: KiteWindView, context: Context) { view.autoPlay = autoPlay }
    static func dismantleUIView(_ view: KiteWindView, coordinator: ()) { view.stop() }
}

final class KiteWindView: UIView {
    var autoPlay: Bool
    private let art = KiteWindArt()
    private var touch = KiteWindMotion.Touch()
    private var clock = 0.0, base = 0.0, started = 0.0
    private var display: CADisplayLink?
    private var reduced = UIAccessibility.isReduceMotionEnabled
    private var origin = CGPoint.zero, zone = 0, lastHour = 3
    private let feedback = UISelectionFeedbackGenerator()
    private lazy var heading = UIAccessibilityElement(accessibilityContainer: self)
    private lazy var wind = WindElement(accessibilityContainer: self)

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        super.init(frame: .zero)
        isOpaque = true; isMultipleTouchEnabled = false; accessibilityIdentifier = "kite-wind-surface"
        backgroundColor = UIColor(red: 63/255, green: 176/255, blue: 1, alpha: 1)
        heading.accessibilityTraits = .header; heading.accessibilityIdentifier = "kite-wind-heading"
        wind.accessibilityIdentifier = "kite-wind-hours"; wind.accessibilityLabel = "Wind"; wind.accessibilityTraits = .adjustable
        wind.step = { [weak self] in self?.adjust($0) }
        accessibilityElements = [heading, wind]
        NotificationCenter.default.addObserver(self, selector: #selector(settingsChanged), name: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(activityChanged), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(pauseClock), name: UIApplication.willResignActiveNotification, object: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { NotificationCenter.default.removeObserver(self) }
    override func didMoveToWindow() { super.didMoveToWindow(); updateClock() }
    func stop() { display?.invalidate(); display = nil }
    private func updateClock() {
        base = clock; started = CACurrentMediaTime(); stop()
        if window != nil && !reduced && UIApplication.shared.applicationState == .active {
            let link = CADisplayLink(target: self, selector: #selector(tick))
            link.add(to: .main, forMode: .common); display = link
        }
        setNeedsDisplay(); updateAccessibility()
    }
    @objc private func settingsChanged() { reduced = UIAccessibility.isReduceMotionEnabled; updateClock() }
    @objc private func activityChanged() { updateClock() }
    @objc private func pauseClock() { stop() }
    @objc private func tick() {
        clock = base+CACurrentMediaTime()-started
        touch.step(clock, autoPlay, reduced)
        let h = Int(current.p.rounded())
        if h != lastHour {
            if touch.dragging { feedback.selectionChanged() }
            lastHour = h; updateAccessibility()
        }
        setNeedsDisplay()
    }
    private var current: KiteWindMotion.Frame { touch.frame(clock, reduced, autoPlay) }
    override func layoutSubviews() { super.layoutSubviews(); updateAccessibility(); setNeedsDisplay() }
    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        art.draw(c, bounds.size, current)
    }
    private func updateAccessibility() {
        let p = current.p.rounded(), w = KiteWindMotion.wind(p), fy = bounds.height/852, ox = (bounds.width-393)/2
        heading.accessibilityLabel = "Harbour Park, north-east wind, \(Int(w.rounded())) kilometres per hour"
        heading.accessibilityFrameInContainerSpace = CGRect(x: ox+26, y: 70*fy, width: 300, height: 220*fy)
        wind.accessibilityValue = "\(KiteWindMotion.hour(p)), \(Int(w.rounded())) kilometres per hour, gusts \(Int((w*1.4).rounded()))"
        wind.accessibilityFrameInContainerSpace = CGRect(x: ox+20, y: 694*fy, width: 353, height: 128)
    }
    private func adjust(_ delta: Double) {
        touch.takeOver(clock, reduced, autoPlay)
        let from = touch.p(clock).rounded(), to = min(9, max(0, from+delta))
        touch.go(to, clock, reduced)
        if from != to { feedback.selectionChanged() }
        lastHour = Int(to); updateAccessibility(); setNeedsDisplay()
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let pt = touches.first?.location(in: self) else { return }
        origin = pt; touch.takeOver(clock, reduced, autoPlay); feedback.prepare()
        let ox = (bounds.width-393)/2, top = 694*bounds.height/852
        let pose = KiteWindArt.pose(bounds.size, current)
        zone = CGRect(x: ox+20, y: top, width: 353, height: 128).contains(pt) ? 1 : (!reduced && hypot(pt.x-pose.0, pt.y-pose.1) <= 80 ? 2 : 0)
        if zone == 1 { touch.p0 = touch.p(clock); touch.dx = 0; touch.dragging = true }
        if zone == 2 { let k = touch.pull(clock); touch.pulling = true; touch.kx = k.0; touch.ky = k.1 }
        lastHour = Int(current.p.rounded()); setNeedsDisplay()
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let pt = touches.first?.location(in: self) else { return }
        let dx = pt.x-origin.x, dy = pt.y-origin.y
        touch.last = clock
        if zone == 1 {
            touch.dx = dx
            let h = Int(touch.p(clock).rounded())
            if h != lastHour { feedback.selectionChanged(); lastHour = h; updateAccessibility() }
        } else if zone == 2 {
            let d = hypot(dx, dy), k = d > 0 ? 30*(1-exp(-d/30))/d : 0
            touch.kx = dx*k; touch.ky = dy*k
        }
        setNeedsDisplay()
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { release() }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { release() }
    private func release() {
        if zone == 1 { touch.go(touch.p(clock).rounded(), clock, reduced) }
        if zone == 2 {
            touch.pulling = false; touch.kFromX = touch.kx; touch.kFromY = touch.ky; touch.kAt = clock; touch.last = clock
        }
        zone = 0; updateAccessibility(); setNeedsDisplay()
    }
    private final class WindElement: UIAccessibilityElement {
        var step: ((Double) -> Void)?
        override func accessibilityIncrement() { step?(1) }
        override func accessibilityDecrement() { step?(-1) }
    }
}
