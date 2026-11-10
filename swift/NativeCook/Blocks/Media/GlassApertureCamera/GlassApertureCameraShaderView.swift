import MetalKit
import SwiftUI

struct GlassApertureCameraShaderView: UIViewRepresentable {
    var layout: GlassApertureCameraTheme.Layout
    var frame: GlassApertureCameraMotion.Frame
    var scale: Double
    func makeUIView(context: Context) -> GlassApertureCameraMetalView { GlassApertureCameraMetalView() }
    func updateUIView(_ view: GlassApertureCameraMetalView, context: Context) {
        let uniforms = GlassApertureCameraMetalView.Uniforms(
            view: SIMD4(Float(layout.w),Float(layout.h),Float(scale),Float(frame.t)),
            cam: SIMD4(Float(layout.viewH),Float(layout.photoH),Float(frame.fnum),Float(frame.focus)),
            iris: SIMD4(Float(frame.iris),Float(frame.snap),Float(layout.irisX),Float(layout.irisY)),
            ring: SIMD4(Float(layout.irisR),0.2,0,0))
        // The shader does not read time; held poses require no redundant GPU pass.
        let old = view.uniforms
        let changed = old.view.x != uniforms.view.x || old.view.y != uniforms.view.y || old.view.z != uniforms.view.z
            || old.cam != uniforms.cam || old.iris != uniforms.iris || old.ring != uniforms.ring
        view.uniforms = uniforms
        if view.contentScaleFactor != scale { view.contentScaleFactor = scale }
        let drawableSize = CGSize(width:layout.w*scale,height:layout.viewH*scale)
        if view.drawableSize != drawableSize { view.drawableSize = drawableSize }
        if changed { view.setNeedsDisplay() }
    }
}

final class GlassApertureCameraMetalView: MTKView {
    struct Uniforms { var view, cam, iris, ring: SIMD4<Float> }
    var uniforms = Uniforms(view: .zero,cam: .zero,iris: .zero,ring: .zero)
    private var pipeline: MTLRenderPipelineState?
    private var queue: MTLCommandQueue?
    private var photo: MTLTexture?
    init() {
        let gpu = MTLCreateSystemDefaultDevice()
        super.init(frame: .zero,device: gpu)
        isPaused = true; enableSetNeedsDisplay = true; isUserInteractionEnabled = false
        autoResizeDrawable = false
        colorPixelFormat = .bgra8Unorm
        (layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        isOpaque = false; backgroundColor = .clear; clearColor = MTLClearColorMake(0,0,0,0)
        guard let gpu else { assertionFailure("Metal is required for the viewfinder"); return }
        queue = gpu.makeCommandQueue()
        do {
            let descriptor = MTLRenderPipelineDescriptor()
            let library = gpu.makeDefaultLibrary()
            descriptor.vertexFunction = library?.makeFunction(name: "glassApertureCameraVertex")
            descriptor.fragmentFunction = library?.makeFunction(name: "glassApertureCameraFragment")
            descriptor.colorAttachments[0].pixelFormat = colorPixelFormat
            pipeline = try gpu.makeRenderPipelineState(descriptor: descriptor)
            guard let cg = GlassApertureCameraResources.shared.scene.cgImage else { return }
            photo = try MTKTextureLoader(device: gpu).newTexture(cgImage: cg,options: [
                .SRGB: false, .origin: MTKTextureLoader.Origin.topLeft, .generateMipmaps: false])
        } catch { assertionFailure("Glass Aperture Camera Metal setup: \(error)") }
    }
    required init(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func draw(_ rect: CGRect) {
        guard let pipeline,let photo,let pass = currentRenderPassDescriptor,let drawable = currentDrawable,
              let command = queue?.makeCommandBuffer(),let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentTexture(photo,index: 0)
        encoder.setFragmentBytes(&uniforms,length: MemoryLayout<Uniforms>.stride,index: 0)
        encoder.drawPrimitives(type: .triangle,vertexStart: 0,vertexCount: 3)
        encoder.endEncoding(); command.present(drawable); command.commit()
    }
}
