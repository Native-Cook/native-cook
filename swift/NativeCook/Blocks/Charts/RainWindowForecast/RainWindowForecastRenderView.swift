import MetalKit
import UIKit

/// An on-demand, full-resolution Metal pane. The two JPEGs and static UI are decoded once.
final class RainWindowForecastRenderView: UIView, MTKViewDelegate {
    private let metal: MTKView
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let sharp: MTLTexture
    private let blur: MTLTexture
    private let overlay = UIImageView()
    private let finger = CAShapeLayer()
    private var sample = RainWindowForecastClock.Sample(time: 0, frame: .init())
    private var top: CGFloat = 0
    private var bottom: CGFloat = 0
    private var floats = [Float](repeating: 0, count: 76)
    private var artKey = ""

    init() {
        let device = MTLCreateSystemDefaultDevice()!
        metal = MTKView(frame: .zero, device: device)
        queue = device.makeCommandQueue()!
        let library = device.makeDefaultLibrary()!
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "rainWindowForecastVertex")!
        descriptor.fragmentFunction = library.makeFunction(name: "rainWindowForecastFragment")!
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        pipeline = try! device.makeRenderPipelineState(descriptor: descriptor)
        let loader = MTKTextureLoader(device: device)
        let assets = RainWindowForecastResources.shared
        let options: [MTKTextureLoader.Option: Any] = [.SRGB: false, .origin: MTKTextureLoader.Origin.topLeft]
        sharp = try! loader.newTexture(cgImage: assets.sharp, options: options)
        blur = try! loader.newTexture(cgImage: assets.blurred, options: options)
        super.init(frame: .zero)
        clipsToBounds = true
        metal.colorPixelFormat = .bgra8Unorm
        // Match Skia's P3 canvas, including its color-managed image-shader inputs.
        // The reference effect's explicit output conversion is applied once in Metal.
        (metal.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.displayP3)
        metal.isPaused = true
        metal.enableSetNeedsDisplay = false
        metal.isUserInteractionEnabled = false
        metal.delegate = self
        addSubview(metal)
        overlay.isUserInteractionEnabled = false
        addSubview(overlay)
        finger.lineWidth = 1.5
        layer.addSublayer(finger)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func update(_ sample: RainWindowForecastClock.Sample, _ top: CGFloat, _ bottom: CGFloat) {
        self.sample = sample
        self.top = top
        self.bottom = bottom
        refresh()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        metal.frame = bounds
        overlay.frame = bounds
        refresh()
    }
    private func refresh() {
        guard bounds.width > 0, bounds.height > 0 else { return }
        let scale = window?.screen.scale ?? contentScaleFactor
        let key = "\(bounds.size)-\(top)-\(bottom)-\(scale)"
        if key != artKey {
            artKey = key
            overlay.image = RainWindowForecastResources.shared.overlay(bounds.size, top, bottom, scale)
            let weather = UIAccessibilityElement(accessibilityContainer: self)
            weather.accessibilityLabel = "Aurenne, 14°, Light rain · stops in 25 min"
            weather.accessibilityFrameInContainerSpace = CGRect(x: 0, y: top + 24, width: bounds.width, height: 174)
            let forecast = UIAccessibilityElement(accessibilityContainer: self)
            forecast.accessibilityLabel = "Next hour: light rain now, stopping in 25 minutes"
            let hint = bounds.height - max(bottom, 16) - 28
            forecast.accessibilityFrameInContainerSpace = CGRect(x: 24, y: hint - 184, width: bounds.width - 48, height: 168)
            let instruction = UIAccessibilityElement(accessibilityContainer: self)
            instruction.accessibilityLabel = "Wipe the glass to look outside"
            instruction.accessibilityFrameInContainerSpace = CGRect(x: 0, y: hint, width: bounds.width, height: 20)
            instruction.accessibilityTraits = .staticText
            accessibilityElements = [weather, forecast, instruction]
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        let f = sample.frame
        let r = 22 * (1 - 0.12 * f.fingerPress)
        finger.path = CGPath(ellipseIn: CGRect(x: f.fx - r, y: f.fy - r, width: r * 2, height: r * 2), transform: nil)
        finger.fillColor = UIColor.white.withAlphaComponent(0.14 * f.finger).cgColor
        finger.strokeColor = UIColor.white.withAlphaComponent(0.5 * f.finger).cgColor
        CATransaction.commit()
        metal.draw()
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard bounds.width > 0, let pass = view.currentRenderPassDescriptor,
              let drawable = view.currentDrawable, let command = queue.makeCommandBuffer(),
              let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        floats[0] = Float(bounds.width)
        floats[1] = Float(bounds.height)
        floats[2] = Float(sample.time)
        floats[3] = 10
        for i in 0..<24 {
            floats[4 + i * 3] = Float(sample.frame.wipe[i].x)
            floats[5 + i * 3] = Float(sample.frame.wipe[i].y)
            floats[6 + i * 3] = Float(sample.frame.wipe[i].age)
        }
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(floats, length: floats.count * MemoryLayout<Float>.stride, index: 0)
        encoder.setFragmentTexture(sharp, index: 0)
        encoder.setFragmentTexture(blur, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}
