import MetalKit
import UIKit

/// The same analytic chrome and enamel shader as the source, with raw premultiplied atlas samples.
final class ChromePrizeWheelRenderer: NSObject, MTKViewDelegate {
    let view: MTKView
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let textures: [MTLTexture]
    var uniforms = [Float](repeating: 0, count: 20)

    init(assets: ChromePrizeWheelAssets, fragment: String = "chromePrizeWheelFragment") {
        guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue(),
            let library = device.makeDefaultLibrary()
        else {
            preconditionFailure("Chrome Prize Wheel requires Metal")
        }
        self.queue = queue
        view = MTKView(frame: .zero, device: device)
        view.colorPixelFormat = .bgra8Unorm
        view.isOpaque = false
        view.backgroundColor = .clear
        view.clearColor = MTLClearColorMake(0, 0, 0, 0)
        view.isPaused = true
        view.enableSetNeedsDisplay = false
        view.isUserInteractionEnabled = false
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "chromePrizeWheelVertex")
        descriptor.fragmentFunction = library.makeFunction(name: fragment)
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        do {
            pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        } catch {
            preconditionFailure("Chrome Prize Wheel pipeline: \(error)")
        }
        textures = ["wheel-env.png", "wheel-env-soft.png", "wheel-prizes.png"].map { name in
            // CoreGraphics produces the same premultiplied RGBA bytes consumed by Skia.
            let image = assets.image(name).cgImage!
            let width = image.width
            let height = image.height
            var pixels = [UInt8](repeating: 0, count: width * height * 4)
            pixels.withUnsafeMutableBytes { bytes in
                let context = CGContext(
                    data: bytes.baseAddress, width: width, height: height,
                    bitsPerComponent: 8, bytesPerRow: width * 4,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
                context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            }
            let description = MTLTextureDescriptor.texture2DDescriptor(
                pixelFormat: .rgba8Unorm,
                width: width, height: height, mipmapped: false)
            let texture = device.makeTexture(descriptor: description)!
            texture.replace(
                region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0,
                withBytes: pixels, bytesPerRow: width * 4)
            return texture
        }
        super.init()
        view.delegate = self
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let drawable = view.currentDrawable, let pass = view.currentRenderPassDescriptor,
            let command = queue.makeCommandBuffer(), let encoder = command.makeRenderCommandEncoder(descriptor: pass)
        else { return }
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(uniforms, length: uniforms.count * MemoryLayout<Float>.stride, index: 0)
        for (index, texture) in textures.enumerated() {
            encoder.setFragmentTexture(texture, index: index)
        }
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}
