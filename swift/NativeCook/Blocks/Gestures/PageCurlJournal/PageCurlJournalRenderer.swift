import CoreGraphics
import Metal
import MetalKit
import UIKit

/// Metal side of the block: the four pages as textures, the curl shader for up to three sheets in flight and the foil.
/// It draws only when the surface hands it a frame; at rest nothing is drawn at all.
@MainActor
final class PageCurlJournalRenderer: NSObject, MTKViewDelegate {
    let device: MTLDevice
    private let queue: MTLCommandQueue
    private let curlPipeline: MTLRenderPipelineState
    private let foilPipeline: MTLRenderPipelineState
    private var pages: [MTLTexture] = []
    private var pending: PageCurlJournalFrame?

    private init(device: MTLDevice, queue: MTLCommandQueue, curl: MTLRenderPipelineState, foil: MTLRenderPipelineState) {
        self.device = device
        self.queue = queue
        curlPipeline = curl
        foilPipeline = foil
        super.init()
    }

    /// Nil when the device has no Metal or the shaders do not load; the block then stays a still white page.
    static func make() -> PageCurlJournalRenderer? {
        guard let device = MTLCreateSystemDefaultDevice(),
              let queue = device.makeCommandQueue(),
              let library = device.makeDefaultLibrary() else { return nil }
        do {
            let curl = try pipeline(device, library, vertex: "pageCurlJournalCurlVertex", fragment: "pageCurlJournalCurlFragment")
            let foil = try pipeline(device, library, vertex: "pageCurlJournalFoilVertex", fragment: "pageCurlJournalFoilFragment")
            return PageCurlJournalRenderer(device: device, queue: queue, curl: curl, foil: foil)
        } catch {
            assertionFailure("Page Curl Journal pipelines: \(error)")
            return nil
        }
    }

    var hasPages: Bool { pages.count == 4 }

    /// Uploads the four page images as they are: raw sRGB-encoded values in a plain BGRA texture.
    func setPages(_ images: [CGImage]) {
        pages = images.compactMap(texture(from:))
    }

    func configure(_ view: MTKView) {
        view.device = device
        view.colorPixelFormat = .bgra8Unorm
        view.framebufferOnly = true
        view.isPaused = true
        view.enableSetNeedsDisplay = false
        view.isOpaque = false
        view.backgroundColor = .clear
        view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        view.delegate = self
        // The pages are drawn in sRGB; the shader works on those values directly.
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
    }

    func render(_ frame: PageCurlJournalFrame, in view: MTKView) {
        pending = frame
        view.draw()
        pending = nil
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let frame = pending, hasPages,
              let pass = view.currentRenderPassDescriptor,
              let drawable = view.currentDrawable,
              let command = queue.makeCommandBuffer(),
              let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        encoder.setRenderPipelineState(curlPipeline)
        for index in 0..<4 { encoder.setFragmentTexture(pages[index], index: index) }
        for var uniforms in frame.curls {
            encoder.setFragmentBytes(&uniforms, length: MemoryLayout<PageCurlJournalCurlUniforms>.stride, index: 0)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        }
        if var foil = frame.foil {
            encoder.setRenderPipelineState(foilPipeline)
            encoder.setVertexBytes(&foil, length: MemoryLayout<PageCurlJournalFoilUniforms>.stride, index: 0)
            encoder.setFragmentBytes(&foil, length: MemoryLayout<PageCurlJournalFoilUniforms>.stride, index: 0)
            encoder.setFragmentTexture(pages[3], index: 0)
            encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
        }
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }

    private static func pipeline(
        _ device: MTLDevice, _ library: MTLLibrary, vertex: String, fragment: String
    ) throws -> MTLRenderPipelineState {
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: vertex)
        descriptor.fragmentFunction = library.makeFunction(name: fragment)
        let color = descriptor.colorAttachments[0]!
        color.pixelFormat = .bgra8Unorm
        // The shaders return premultiplied colour: a sheet over another canvas keeps what is below it.
        color.isBlendingEnabled = true
        color.sourceRGBBlendFactor = .one
        color.sourceAlphaBlendFactor = .one
        color.destinationRGBBlendFactor = .oneMinusSourceAlpha
        color.destinationAlphaBlendFactor = .oneMinusSourceAlpha
        return try device.makeRenderPipelineState(descriptor: descriptor)
    }

    private func texture(from image: CGImage) -> MTLTexture? {
        let width = image.width
        let height = image.height
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm, width: width, height: height, mipmapped: false)
        descriptor.usage = .shaderRead
        guard let texture = device.makeTexture(descriptor: descriptor),
              let space = CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = bytes.withUnsafeMutableBytes { buffer -> Bool in
            let bitmap = CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
            guard let context = CGContext(
                data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                bytesPerRow: width * 4, space: space, bitmapInfo: bitmap
            ) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            texture.replace(
                region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0,
                withBytes: buffer.baseAddress!, bytesPerRow: width * 4
            )
            return true
        }
        return drawn ? texture : nil
    }
}
