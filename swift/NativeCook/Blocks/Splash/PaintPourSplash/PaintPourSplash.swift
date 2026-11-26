import SwiftUI
import MetalKit
import CoreText

/// Hue's living photograph. Two identical two-second cycles make the four-second recording loop.
/// The texture and drawable contain encoded sRGB; only the shader's c^2.2 mixing is linear.
struct PaintPourSplash: View {
    var autoPlay = true
    static let loop = 4.0
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase

    var body: some View {
        PaintPourSplashSurface(running: autoPlay && !reduced && phase == .active, reduced: reduced)
            .ignoresSafeArea()
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Hue. Colour for every wall.")
            .accessibilityAddTraits(.isHeader)
    }
}

private struct PaintPourSplashSurface: UIViewRepresentable {
    let running: Bool
    let reduced: Bool
    func makeUIView(context: Context) -> PaintPourSplashView { PaintPourSplashView() }
    func updateUIView(_ view: PaintPourSplashView, context: Context) {
        view.configure(running: running, reduced: reduced)
    }
    static func dismantleUIView(_ view: PaintPourSplashView, coordinator: ()) { view.metal.isPaused = true }
}

private final class PaintPourSplashView: UIView {
    let metal = MTKView(frame: .zero)
    private let renderer = PaintPourSplashRenderer()
    private let copy = PaintPourSplashCopy()
    private let still = PaintPourSplashStill()
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor(red: 233/255, green: 233/255, blue: 236/255, alpha: 1)
        isOpaque = true
        isUserInteractionEnabled = false
        if let renderer {
            metal.device = renderer.device
            metal.colorPixelFormat = .bgra8Unorm
            (metal.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
            metal.framebufferOnly = true
            metal.preferredFramesPerSecond = 60
            metal.isPaused = true
            metal.enableSetNeedsDisplay = false
            metal.delegate = renderer
        }
        addSubview(metal)
        addSubview(still)
        copy.backgroundColor = .clear
        copy.isOpaque = false
        addSubview(copy)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() {
        super.layoutSubviews()
        metal.frame = bounds
        still.frame = bounds
        still.setNeedsDisplay()
        copy.frame = bounds
        copy.setNeedsDisplay()
        if metal.isPaused { metal.draw() }
    }
    func configure(running: Bool, reduced: Bool) {
        renderer?.setRunning(running, reduced: reduced)
        still.isHidden = !reduced && renderer != nil
        metal.isHidden = reduced || renderer == nil
        metal.isPaused = !running
        if !running && !reduced { metal.draw() }
    }
}

private final class PaintPourSplashRenderer: NSObject, MTKViewDelegate {
    let device: MTLDevice
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let texture: MTLTexture
    private var origin = CACurrentMediaTime()
    private var elapsed = 0.0
    private var running = false
    private var reduced = false

    init?(device: MTLDevice? = MTLCreateSystemDefaultDevice()) {
        guard let device, let queue = device.makeCommandQueue(),
              let library = device.makeDefaultLibrary(),
              let url = Bundle.main.url(forResource: "PaintPourSplash-paint", withExtension: "webp"),
              let image = UIImage(contentsOfFile: url.path)?.cgImage else { return nil }
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "paintPourSplashVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "paintPourSplashFragment")
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        guard let pipeline = try? device.makeRenderPipelineState(descriptor: descriptor),
              let texture = try? MTKTextureLoader(device: device).newTexture(cgImage: image, options: [
                .SRGB: false, .origin: MTKTextureLoader.Origin.topLeft,
                .generateMipmaps: false,
              ]) else { return nil }
        self.device = device; self.queue = queue; self.pipeline = pipeline; self.texture = texture
        super.init()
    }
    func setRunning(_ next: Bool, reduced: Bool) {
        if running && !next { elapsed += CACurrentMediaTime() - origin }
        if !running && next { origin = CACurrentMediaTime() }
        running = next
        self.reduced = reduced
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard !reduced else { return }
        guard view.drawableSize.width > 0, let pass = view.currentRenderPassDescriptor,
              let drawable = view.currentDrawable, let command = queue.makeCommandBuffer(),
              let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        let time = (elapsed + (running ? CACurrentMediaTime() - origin : 0))
            .truncatingRemainder(dividingBy: PaintPourSplash.loop)
        var uniforms = SIMD4<Float>(Float(view.drawableSize.width), Float(view.drawableSize.height), Float(time), reduced ? 1 : 0)
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentTexture(texture, index: 0)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<SIMD4<Float>>.stride, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}

/// Draw the original image directly when motion is reduced, extending the edge pixels at the sides.
private final class PaintPourSplashStill: UIView {
    private let image: UIImage? = {
        guard let url = Bundle.main.url(forResource: "PaintPourSplash-paint", withExtension: "webp") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }()
    override func draw(_ rect: CGRect) {
        guard let image, let cg = image.cgImage else { return }
        let w = bounds.height * 708 / 1536
        let x = (bounds.width - w) / 2
        image.draw(in: CGRect(x: x, y: 0, width: w, height: bounds.height))
        if x > 0 {
            if let left = cg.cropping(to: CGRect(x: 0, y: 0, width: 1, height: cg.height)) {
                UIImage(cgImage: left).draw(in: CGRect(x: 0, y: 0, width: x, height: bounds.height))
            }
            if let right = cg.cropping(to: CGRect(x: cg.width - 1, y: 0, width: 1, height: cg.height)) {
                UIImage(cgImage: right).draw(in: CGRect(x: x + w, y: 0, width: x, height: bounds.height))
            }
        }
    }
}

/// Explicit glyph advances match Skia's Glyphs and omit trailing tracking from the right edge.
private final class PaintPourSplashCopy: UIView {
    private static func font(_ file: String, _ size: CGFloat) -> CTFont {
        let url = Bundle.main.url(forResource: "PaintPourSplash-" + file, withExtension: "ttf")!
        let provider = CGDataProvider(url: url as CFURL)!
        let font = CGFont(provider)!
        return CTFontCreateWithGraphicsFont(font, size, nil, nil)
    }
    private let archivo = PaintPourSplashCopy.font("ArchivoExpanded-Black", 120)
    private let inter = PaintPourSplashCopy.font("Inter-Medium", 16)
    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        context.translateBy(x: 0, y: bounds.height)
        context.scaleBy(x: 1, y: -1)
        func line(_ text: String, _ font: CTFont, _ right: CGFloat, _ baseline: CGFloat, _ tracking: CGFloat, _ color: UIColor) {
            let chars = Array(text.utf16)
            var glyphs = [CGGlyph](repeating: 0, count: chars.count)
            CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
            var advances = [CGSize](repeating: .zero, count: chars.count)
            CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
            var x = right - advances.reduce(0) { $0 + $1.width } - tracking * CGFloat(max(0, chars.count - 1))
            var positions: [CGPoint] = []
            for a in advances { positions.append(CGPoint(x: x, y: bounds.height - baseline)); x += a.width + tracking }
            context.setFillColor(color.cgColor)
            CTFontDrawGlyphs(font, glyphs, positions, glyphs.count, context)
        }
        let fy = bounds.height / 852
        line("Hue", archivo, bounds.width - 16, 400 * fy, -8, UIColor(red: 22/255, green: 22/255, blue: 26/255, alpha: 1))
        let grey = UIColor(red: 85/255, green: 85/255, blue: 94/255, alpha: 1)
        line("Colour for", inter, bounds.width - 28, 432 * fy, 0, grey)
        line("every wall.", inter, bounds.width - 28, 452 * fy, 0, grey)
    }
}
