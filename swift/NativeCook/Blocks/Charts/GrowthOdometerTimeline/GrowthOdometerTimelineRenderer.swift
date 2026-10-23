import MetalKit
import UIKit

struct GrowthOdometerTimelineLayout {
    let width: Double
    let height: Double
    var cy: Double { (height / 8).rounded() * 4 }
    var railX: Double { (width * 0.318).rounded() }
    var x0: Double { railX + 80 }
    var knobX: Double { railX + 34 }

    static func bell(_ value: Double) -> Double {
        abs(value) < 1 ? 0.5 + 0.5 * cos(.pi * value) : 0
    }

    func rail(at y: Double) -> Double {
        railX - 14 * Self.bell((y - cy) / 44)
    }
}

private struct GrowthOdometerTimelineUniforms {
    var geometry: SIMD4<Float>
    var light: SIMD4<Float>
    var hero: SIMD4<Float>
    var screen: SIMD4<Float>
    var numberGeometry: SIMD4<Float>
    var angles: SIMD4<Float>
    var extra: SIMD4<Float>
    var motion: SIMD4<Float>
    var face: SIMD4<Float>
}

final class GrowthOdometerTimelineRenderer {
    let surface: MTKView
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let grain: MTLTexture
    private let digits: MTLTexture

    init() throws {
        guard let device = MTLCreateSystemDefaultDevice(),
              let queue = device.makeCommandQueue(),
              let library = device.makeDefaultLibrary() else {
            throw NSError(domain: "GrowthOdometerTimeline", code: 1)
        }
        self.queue = queue
        surface = MTKView(frame: .zero, device: device)
        surface.isPaused = true
        surface.enableSetNeedsDisplay = false
        surface.isUserInteractionEnabled = false
        surface.colorPixelFormat = .bgra8Unorm
        // Skia's reference canvas presents its shader colours in the display's P3 space.
        (surface.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.displayP3)
        surface.framebufferOnly = true
        surface.isOpaque = true
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "growthOdometerTimelineVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "growthOdometerTimelineFragment")
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        let loader = MTKTextureLoader(device: device)
        func texture(_ name: String) throws -> MTLTexture {
            guard let url = Bundle.main.url(forResource: "GrowthOdometerTimeline-\(name)", withExtension: "png") else {
                throw NSError(domain: "GrowthOdometerTimeline.Asset", code: 2)
            }
            // These greyscale strips are data, not sRGB colours. Keep their red values unchanged.
            return try loader.newTexture(URL: url, options: [.SRGB: false, .generateMipmaps: false])
        }
        grain = try texture("grain")
        digits = try texture("digits")
    }

    func draw(_ state: GrowthOdometerTimelineSimulation, layout: GrowthOdometerTimelineLayout) {
        guard layout.width > 0, layout.height > 0,
              let pass = surface.currentRenderPassDescriptor,
              let drawable = surface.currentDrawable,
              let command = queue.makeCommandBuffer(),
              let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        func vector(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> SIMD4<Float> {
            SIMD4(Float(a), Float(b), Float(c), Float(d))
        }
        func blur(_ index: Int) -> Double {
            GrowthOdometerTimelineSimulation.clamp(state.drums[index].omega / 100, -0.22, 0.22)
        }
        let ratio = surface.drawableSize.width / layout.width
        var uniforms = GrowthOdometerTimelineUniforms(
            geometry: vector(layout.width, layout.height, layout.cy, layout.railX),
            light: vector(state.halo, state.time, state.grip, state.tick),
            hero: vector(state.heroAge, state.surge, max(0, state.p - 12), state.reduced ? 1 : 0),
            screen: vector(ratio, layout.x0, 0, 0),
            numberGeometry: vector((layout.x0 * ratio).rounded() / ratio, layout.cy, ratio, state.halo),
            angles: vector(state.drums[0].theta, state.drums[1].theta, state.drums[2].theta, state.drums[3].theta),
            extra: vector(state.drums[4].theta, blur(0), blur(1), blur(2)),
            motion: vector(blur(3), blur(4), state.flash, state.heroAge),
            face: vector(state.numberAlpha, state.reduced ? 1 : 0, 0, 0)
        )
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<GrowthOdometerTimelineUniforms>.stride, index: 0)
        encoder.setFragmentTexture(grain, index: 0)
        encoder.setFragmentTexture(digits, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}

/// Weak display-link target lets removal from the gallery release the entire instrument.
final class GrowthOdometerTimelineClock: NSObject {
    weak var view: GrowthOdometerTimelineView?

    @objc func frame(_ link: CADisplayLink) {
        view?.frame(link)
    }
}

final class GrowthOdometerTimelineAccessibleElement: UIAccessibilityElement {
    var adjust: ((Double) -> Void)?
    var activate: (() -> Void)?

    override func accessibilityIncrement() { adjust?(1) }
    override func accessibilityDecrement() { adjust?(-1) }
    override func accessibilityActivate() -> Bool {
        guard let activate else { return false }
        activate()
        return true
    }
}

final class GrowthOdometerTimelineView: UIView, UIGestureRecognizerDelegate {
    var state: GrowthOdometerTimelineSimulation
    private var configuredAuto: Bool
    private let renderer: GrowthOdometerTimelineRenderer?
    private let drawing = GrowthOdometerTimelineDrawing()
    private var displayLink: CADisplayLink?
    private let clockTarget = GrowthOdometerTimelineClock()
    private var lastTimestamp = 0.0
    private var presentationTime = 0.0
    private var touchOrigin = CGPoint.zero
    private var lastPoint = CGPoint.zero
    private var lastPointTime = 0.0
    private var touchTime = 0.0
    private var touchVelocity = 0.0
    private var maxTravel = 0.0
    private var beganOnKnob = false
    private let selection = UISelectionFeedbackGenerator()
    private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    private let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    private lazy var wheelElement = GrowthOdometerTimelineAccessibleElement(accessibilityContainer: self)
    private lazy var knobElement = GrowthOdometerTimelineAccessibleElement(accessibilityContainer: self)
    private lazy var summaryElement = UIAccessibilityElement(accessibilityContainer: self)

    var layout: GrowthOdometerTimelineLayout {
        GrowthOdometerTimelineLayout(width: bounds.width, height: bounds.height)
    }

    init(autoPlay: Bool, reduced: Bool) {
        state = GrowthOdometerTimelineSimulation(autoPlay: autoPlay, reduced: reduced)
        configuredAuto = autoPlay
        renderer = try? GrowthOdometerTimelineRenderer()
        super.init(frame: .zero)
        backgroundColor = .black
        clipsToBounds = true
        if let renderer { addSubview(renderer.surface) }
        addSubview(drawing)
        drawing.isUserInteractionEnabled = false
        drawing.isAccessibilityElement = false
        drawing.accessibilityElementsHidden = true
        let touch = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
        touch.minimumPressDuration = 0
        touch.allowableMovement = .greatestFiniteMagnitude
        touch.numberOfTouchesRequired = 1
        touch.cancelsTouchesInView = false
        touch.delegate = self
        addGestureRecognizer(touch)
        clockTarget.view = self
        wheelElement.accessibilityLabel = "Month"
        wheelElement.accessibilityTraits = .adjustable
        wheelElement.adjust = { [weak self] direction in self?.state.nextMonth(direction: direction, rewind: false) }
        knobElement.accessibilityLabel = "Next month"
        knobElement.accessibilityTraits = .button
        knobElement.activate = { [weak self] in self?.state.nextMonth() }
        summaryElement.accessibilityTraits = .staticText
        accessibilityElements = [wheelElement, knobElement, summaryElement]
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func configure(autoPlay: Bool, reduced: Bool) {
        guard configuredAuto != autoPlay || state.reduced != reduced else { return }
        configuredAuto = autoPlay
        let month = state.month
        state = GrowthOdometerTimelineSimulation(autoPlay: autoPlay, reduced: reduced)
        if reduced {
            state.p = Double(month)
            state.target = Double(month)
            state.reducedMonth = month
            state.displayMonth = month
            state.halo = (GrowthOdometerTimelineSimulation.values[month] - 3000) / 7000
            state.haloFrom = state.halo
        }
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            guard displayLink == nil else { return }
            lastTimestamp = 0
            let link = CADisplayLink(target: clockTarget, selector: #selector(GrowthOdometerTimelineClock.frame(_:)))
            link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60)
            link.add(to: .main, forMode: .common)
            displayLink = link
        } else {
            stop()
        }
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        lastTimestamp = 0
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        renderer?.surface.frame = bounds
        renderer?.surface.contentScaleFactor = window?.screen.scale ?? traitCollection.displayScale
        renderer?.surface.drawableSize = CGSize(width: bounds.width * (window?.screen.scale ?? 3), height: bounds.height * (window?.screen.scale ?? 3))
        drawing.frame = bounds
        drawing.contentScaleFactor = window?.screen.scale ?? traitCollection.displayScale
        updateAccessibility()
    }

    func frame(_ link: CADisplayLink) {
        let delta = lastTimestamp == 0 ? 0 : link.timestamp - lastTimestamp
        lastTimestamp = link.timestamp
        presentationTime += min(delta, 0.05)
        if presentationTime >= 0.3 { state.advance(delta) }
        renderer?.surface.alpha = min(1, presentationTime / 0.3)
        drawing.alpha = min(1, presentationTime / 0.3)
        renderer?.draw(state, layout: layout)
        drawing.state = state
        drawing.setNeedsDisplay()
        if state.events & 4 != 0 { heavyImpact.impactOccurred() }
        else if state.events & 2 != 0 { lightImpact.impactOccurred() }
        else if state.events & 1 != 0 { selection.selectionChanged() }
        if state.events & 8 != 0 { updateAccessibility() }
    }

    private func updateAccessibility() {
        let m = layout
        let value = Int(GrowthOdometerTimelineSimulation.values[state.announced]).formatted(.number.locale(Locale(identifier: "en_US")))
        let description = "\(GrowthOdometerTimelineSimulation.fullMonths[state.announced]), \(value) followers"
        wheelElement.accessibilityFrameInContainerSpace = CGRect(x: 0, y: 0, width: m.railX + 4, height: m.height)
        wheelElement.accessibilityValue = description
        knobElement.accessibilityFrameInContainerSpace = CGRect(x: m.knobX - 28, y: m.cy - 28, width: 56, height: 56)
        knobElement.accessibilityValue = description
        summaryElement.accessibilityFrameInContainerSpace = CGRect(x: m.x0, y: m.cy - 66, width: m.width - m.x0, height: 120)
        summaryElement.accessibilityLabel = "Nova Lin. \(description)"
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            touchOrigin = point
            lastPoint = point
            touchTime = now
            lastPointTime = now
            touchVelocity = 0
            maxTravel = 0
            beganOnKnob = CGRect(x: layout.knobX - 28, y: layout.cy - 28, width: 56, height: 56).contains(point)
            state.touch()
            state.pressed = beganOnKnob
            selection.prepare()
            lightImpact.prepare()
            heavyImpact.prepare()
        case .changed:
            let interval = now - lastPointTime
            if interval > 0 { touchVelocity = (point.y - lastPoint.y) / interval }
            lastPoint = point
            lastPointTime = now
            maxTravel = max(maxTravel, hypot(point.x - touchOrigin.x, point.y - touchOrigin.y))
            if abs(point.y - touchOrigin.y) > 6 || state.mode == .drag {
                state.drag(translationY: point.y - touchOrigin.y, velocityY: touchVelocity)
            }
            if maxTravel > 8 { state.pressed = false }
        case .ended, .cancelled, .failed:
            if now - lastPointTime > 0.08 { touchVelocity = 0 }
            if recognizer.state == .ended && beganOnKnob && maxTravel <= 8 && now - touchTime <= 0.28 {
                state.nextMonth(fromGrab: true)
            }
            state.pressed = false
            state.release(velocityY: touchVelocity)
        default:
            break
        }
    }
}
