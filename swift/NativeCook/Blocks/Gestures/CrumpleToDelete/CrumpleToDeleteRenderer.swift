/// Metal mesh rendering with drawable acquisition owned by MTKView for every submitted pose.
import MetalKit
import UIKit

struct CrumpleToDeleteVertex {
    var position: SIMD2<Float>
    var uv: SIMD2<Float>
    var color: SIMD4<Float> = .init(repeating: 1)
}
struct CrumpleToDeleteUniforms {
    var viewport = SIMD4<Float>.zero, ellipse = SIMD4<Float>.zero, pivot = SIMD4<Float>.zero
    var box = SIMD4<Float>.zero, params = SIMD4<Float>(0, 0, 1, 0), tint = SIMD4<Float>.zero
}
/// Submits changing vertices through MTKViewDelegate so each frame gets a fresh drawable.
final class CrumpleToDeleteRenderer: NSObject, MTKViewDelegate {
    typealias M = CrumpleToDeleteMotion
    let device: MTLDevice
    private let queue: MTLCommandQueue
    private var pipelines: [String: MTLRenderPipelineState] = [:]
    private var textures: [String: MTLTexture] = [:]
    private let indexBuffer: MTLBuffer
    private var vertexBuffers: [MTLBuffer] = []
    private var slot = 0
    private let inflight = DispatchSemaphore(value: 3)
    private var geo: M.Geo?
    private var pose: M.Pose?
    private var didDraw = false
    init?(device: MTLDevice? = MTLCreateSystemDefaultDevice()) {
        guard let device, let queue = device.makeCommandQueue(),
            let library = device.makeDefaultLibrary()
        else { return nil }
        self.device = device
        self.queue = queue
        guard let buffer = device.makeBuffer(bytes: M.indices, length: M.indices.count * 2) else { return nil }
        indexBuffer = buffer
        super.init()
        for _ in 0..<3 {
            guard
                let vertexBuffer = device.makeBuffer(
                    length: M.gx * M.gy * MemoryLayout<CrumpleToDeleteVertex>.stride)
            else {
                return nil
            }
            vertexBuffers.append(vertexBuffer)
        }
        for name in ["Texture", "Shade", "Solid", "Shadow"] {
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = library.makeFunction(name: "crumpleToDeleteVertex")
            descriptor.fragmentFunction = library.makeFunction(name: "crumpleToDelete\(name)")
            descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
            let attachment = descriptor.colorAttachments[0]!
            attachment.isBlendingEnabled = true
            attachment.sourceRGBBlendFactor = name == "Shade" ? .destinationColor : .one
            attachment.destinationRGBBlendFactor = name == "Shade" ? .zero : .oneMinusSourceAlpha
            attachment.sourceAlphaBlendFactor = name == "Shade" ? .zero : .one
            attachment.destinationAlphaBlendFactor = name == "Shade" ? .one : .oneMinusSourceAlpha
            guard let pipeline = try? device.makeRenderPipelineState(descriptor: descriptor) else { return nil }
            pipelines[name] = pipeline
        }
    }
    func configure(_ view: MTKView, _ geometry: M.Geo) {
        view.device = device
        view.colorPixelFormat = .bgra8Unorm
        view.framebufferOnly = true
        view.isPaused = true
        view.enableSetNeedsDisplay = false
        view.autoResizeDrawable = true
        view.delegate = self
        view.layer.isOpaque = true
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        guard
            geo?.width != geometry.width || geo?.height != geometry.height || geo?.top != geometry.top
                || geo?.bottom != geometry.bottom
        else { return }
        geo = geometry
        let r = CrumpleToDeleteResources.shared
        let loader = MTKTextureLoader(device: device)
        let pair = r.noteImages(geometry)
        for (key, image) in [
            ("background", r.backdrop(geometry)), ("note", pair.0), ("bin", r.bin),
            ("binShadow", r.binShadow(geometry)),
        ] {
            if let cg = image.cgImage {
                textures[key] = try? loader.newTexture(
                    cgImage: cg, options: [.SRGB: false, .origin: MTKTextureLoader.Origin.topLeft])
            }
        }
    }
    private func quad(_ box: CGRect) -> [CrumpleToDeleteVertex] {
        let x = Float(box.minX)
        let y = Float(box.minY)
        let xx = Float(box.maxX)
        let yy = Float(box.maxY)
        return [
            .init(position: .init(x, y), uv: .init(0, 0)), .init(position: .init(xx, y), uv: .init(1, 0)),
            .init(position: .init(x, yy), uv: .init(0, 1)), .init(position: .init(xx, y), uv: .init(1, 0)),
            .init(position: .init(xx, yy), uv: .init(1, 1)), .init(position: .init(x, yy), uv: .init(0, 1)),
        ]
    }
    @discardableResult
    /// Draws the latest pose synchronously through MTKView, including gesture and Undo frames.
    func requestDraw(_ view: MTKView, _ pose: M.Pose) -> Bool {
        self.pose = pose
        didDraw = false
        // MTKView.draw() retires the previous drawable before requesting the next frame.
        // Encoding directly from CADisplayLink keeps presenting the first cached drawable.
        view.draw()
        return didDraw
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard let pose, let geo else { return }
        didDraw = render(view, pose, geo)
    }
    private func render(_ view: MTKView, _ pose: M.Pose, _ geometry: M.Geo) -> Bool {
        guard let descriptor = view.currentRenderPassDescriptor, let drawable = view.currentDrawable,
            let command = queue.makeCommandBuffer()
        else { return false }
        inflight.wait()
        slot = (slot + 1) % 3
        command.addCompletedHandler { [inflight] _ in inflight.signal() }
        descriptor.colorAttachments[0].loadAction = .clear
        descriptor.colorAttachments[0].clearColor = MTLClearColor(red: 0.85, green: 0.78, blue: 0.69, alpha: 1)
        guard let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else {
            inflight.signal()
            return false
        }
        var base = CrumpleToDeleteUniforms()
        base.viewport = .init(Float(geometry.width), Float(geometry.height), 0, 0)
        base.ellipse = .init(Float(geometry.bcx), Float(geometry.bcy + 2), Float(geometry.brx), Float(geometry.bry))
        base.pivot = .init(Float(geometry.bcx), Float(geometry.binY + geometry.bh), 0, 0)
        func uniforms(_ u: CrumpleToDeleteUniforms) {
            var u = u
            encoder.setVertexBytes(&u, length: MemoryLayout<CrumpleToDeleteUniforms>.stride, index: 1)
            encoder.setFragmentBytes(&u, length: MemoryLayout<CrumpleToDeleteUniforms>.stride, index: 1)
        }
        func drawQuad(_ key: String?, _ box: CGRect, _ u: CrumpleToDeleteUniforms, _ shader: String = "Texture") {
            guard let pipeline = pipelines[shader] else { return }
            encoder.setRenderPipelineState(pipeline)
            let v = quad(box)
            encoder.setVertexBytes(v, length: v.count * MemoryLayout<CrumpleToDeleteVertex>.stride, index: 0)
            var textureU = u
            // ImageIO's bin WebP is straight alpha; the CGContext atlases are premultiplied.
            textureU.params.w = key == "bin" ? 1 : 0
            uniforms(textureU)
            if let key { encoder.setFragmentTexture(textures[key], index: 0) }
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6)
        }
        drawQuad("background", CGRect(x: 0, y: 0, width: geometry.width, height: geometry.height), base)
        var binU = base
        binU.viewport.w = Float(pose.wobble * 0.06)
        drawQuad(
            "binShadow",
            CGRect(x: geometry.binX - 32, y: geometry.binY + geometry.bh - 50, width: geometry.bw + 64, height: 90),
            binU)
        drawQuad("bin", CGRect(x: geometry.binX, y: geometry.binY, width: geometry.bw, height: geometry.bh), binU)
        let mesh = M.crumple(pose, geometry)
        let vertexBuffer = vertexBuffers[slot]
        mesh.withUnsafeBytes { bytes in
            if let src = bytes.baseAddress { memcpy(vertexBuffer.contents(), src, bytes.count) }
        }
        func drawMesh(_ u: CrumpleToDeleteUniforms) {
            encoder.setVertexBuffer(vertexBuffer, offset: 0, index: 0)
            uniforms(u)
            encoder.setFragmentTexture(textures["note"], index: 0)
            // Resolve the texture and multiply shade for the final triangle together.
            // Skia's advanced blend resolves against the destination of the draw call;
            // accumulating hardware multiply on overlapping triangles darkens folds twice.
            for name in ["Texture"] {
                encoder.setRenderPipelineState(pipelines[name]!)
                encoder.drawIndexedPrimitives(
                    type: .triangle, indexCount: M.indices.count, indexType: .uint16, indexBuffer: indexBuffer,
                    indexBufferOffset: 0)
            }
        }
        if pose.sink > 0.02 {
            binU.viewport.z = 1
            drawMesh(binU)
            binU.tint = .init(Float(10.0 / 255 * 0.25), Float(10.0 / 255 * 0.25), Float(10.0 / 255 * 0.25), 0.25)
            drawQuad(
                nil, CGRect(x: geometry.binX, y: geometry.binY, width: geometry.bw, height: geometry.bh), binU,
                "Solid")
        }
        let c = max(0, min(1, pose.c))
        let e = c * c * (3 - 2 * c)
        let w = (geometry.nw + (88 - geometry.nw) * e) * pose.sc
        let h = (geometry.nh + (70 - geometry.nh) * e) * pose.sc
        let yy = pose.y + 10 + pose.lift * 10 + (1 - e) * 4
        let sigma = 10 + 12 * pose.lift
        var shadowU = base
        shadowU.box = .init(Float(pose.x + 4), Float(yy), Float(w), Float(h))
        let opacity = (0.32 - 0.12 * pose.lift) * (1 - pose.sink) * (pose.toast > 0.01 && pose.sink > 0.5 ? 0 : 1)
        shadowU.params = .init(Float(6 + 40 * e), Float(sigma), Float(opacity), 0)
        shadowU.tint = .init(60.0 / 255, 40.0 / 255, 20.0 / 255, 1)
        if opacity > 0 {
            drawQuad(
                nil,
                CGRect(
                    x: pose.x + 4 - w / 2 - 3 * sigma, y: yy - h / 2 - 3 * sigma, width: w + 6 * sigma,
                    height: h + 6 * sigma), shadowU, "Shadow")
        }
        if pose.sink <= 0.02 { drawMesh(base) }
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
        return true
    }
}
