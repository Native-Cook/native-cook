import SwiftUI
import UIKit

struct GumballCrank: View {
    static let loop: Double = 10

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            GumballCrankSurface(autoPlay: autoPlay, reduceMotion: reduceMotion, safeTop: insets.top, safeBottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(uiColor: GumballCrankColor.wallTop).ignoresSafeArea())
        .preferredColorScheme(.light)
    }
}

private struct GumballCrankSurface: UIViewRepresentable {
    let autoPlay: Bool
    let reduceMotion: Bool
    let safeTop: CGFloat
    let safeBottom: CGFloat

    func makeUIView(context: Context) -> GumballCrankCanvasView {
        let view = GumballCrankCanvasView()
        view.configure(autoPlay: autoPlay, reduceMotion: reduceMotion, safeTop: safeTop, safeBottom: safeBottom)
        return view
    }

    func updateUIView(_ view: GumballCrankCanvasView, context: Context) {
        view.configure(autoPlay: autoPlay, reduceMotion: reduceMotion, safeTop: safeTop, safeBottom: safeBottom)
    }
}

private final class GumballCrankCanvasView: UIView {
    private let model = GumballCrankSimulation(reduced: false)
    private let renderer = GumballCrankRenderer()
    private var stageLayout = GumballCrankLayout.make(width: 402, height: 852, top: 0, bottom: 0)
    private var autoPlay = false
    private var reduceMotion = false
    private var safeTop: CGFloat = 0
    private var safeBottom: CGFloat = 0
    private var configured = false
    private var displayLink: CADisplayLink?
    private var previousTimestamp: CFTimeInterval?
    private let selectionFeedback = UISelectionFeedbackGenerator()
    private let softFeedback = UIImpactFeedbackGenerator(style: .soft)
    private let heavyFeedback = UIImpactFeedbackGenerator(style: .heavy)
    private let lightFeedback = UIImpactFeedbackGenerator(style: .light)
    private let notificationFeedback = UINotificationFeedbackGenerator()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = true
        backgroundColor = GumballCrankColor.wallTop
        isMultipleTouchEnabled = false
        isAccessibilityElement = true
        accessibilityTraits = .adjustable
    }

    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func configure(autoPlay: Bool, reduceMotion: Bool, safeTop: CGFloat, safeBottom: CGFloat) {
        let behaviorChanged = !configured || self.autoPlay != autoPlay || self.reduceMotion != reduceMotion
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
        self.safeTop = safeTop
        self.safeBottom = safeBottom
        if behaviorChanged { configured = true; model.start(autoPlay: autoPlay, reduceMotion: reduceMotion) }
        setNeedsLayout(); setNeedsDisplay(); ensureClock()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        stageLayout = .make(width: bounds.width, height: bounds.height, top: safeTop, bottom: safeBottom)
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        window == nil ? stopClock() : ensureClock()
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        context.setFillColor(GumballCrankColor.wallTop.cgColor); context.fill(bounds)
        let tableBottom = stageLayout.origin.y + GumballCrankMetrics.stage.height * stageLayout.scale
        if tableBottom < bounds.maxY {
            context.setFillColor(GumballCrankColor.tableFront.cgColor)
            context.fill(CGRect(x: 0, y: tableBottom - 1, width: bounds.width, height: bounds.maxY - tableBottom + 1))
        }
        renderer.draw(in: context, model: model, layout: stageLayout)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let point = touches.first?.location(in: self) else { return }
        let stage = stagePoint(point); model.pointerDown(x: stage.x, y: stage.y); ensureClock(); setNeedsDisplay(); playHaptics()
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let point = touches.first?.location(in: self) else { return }
        let stage = stagePoint(point); model.pointerMove(x: stage.x, y: stage.y); ensureClock(); setNeedsDisplay(); playHaptics()
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        let stage = stagePoint(touches.first?.location(in: self) ?? .zero); model.pointerUp(x: stage.x, y: stage.y); ensureClock(); setNeedsDisplay(); playHaptics()
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        let stage = stagePoint(touches.first?.location(in: self) ?? .zero); model.pointerUp(x: stage.x, y: stage.y); ensureClock(); setNeedsDisplay()
    }

    override func accessibilityActivate() -> Bool { model.accessibilityActivate(); ensureClock(); setNeedsDisplay(); playHaptics(); return true }
    override var accessibilityLabel: String? { get { model.accessibilityDescription } set {} }
    override var accessibilityHint: String? { get { model.accessibilityAction } set {} }

    private func stagePoint(_ point: CGPoint) -> CGPoint { CGPoint(x: (point.x - stageLayout.origin.x) / stageLayout.scale, y: (point.y - stageLayout.origin.y) / stageLayout.scale) }

    private func ensureClock() {
        guard displayLink == nil, autoPlay || model.moving else { return }
        let link = CADisplayLink(target: self, selector: #selector(frame(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: 120)
        link.add(to: .main, forMode: .common); displayLink = link
    }
    private func stopClock() { displayLink?.invalidate(); displayLink = nil; previousTimestamp = nil }
    @objc private func frame(_ link: CADisplayLink) {
        guard let previousTimestamp else { self.previousTimestamp = link.targetTimestamp; return }
        self.previousTimestamp = link.targetTimestamp
        model.step(dt: min(0.05, max(0, link.targetTimestamp - previousTimestamp)), autoPlay: autoPlay)
        playHaptics(); setNeedsDisplay()
        if !autoPlay, !model.moving { stopClock() }
    }
    private func playHaptics() {
        let events = model.drainHaptics(); guard !model.demoRunning else { return }
        for event in events {
            switch event {
            case .tick: selectionFeedback.selectionChanged()
            case .strain: softFeedback.impactOccurred()
            case .drop: heavyFeedback.impactOccurred()
            case .open: notificationFeedback.notificationOccurred(.success)
            case .collect: lightFeedback.impactOccurred()
            }
        }
    }
}
