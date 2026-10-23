import MetalKit
import UIKit

final class ChromeZeroNumeralRenderer: NSObject, MTKViewDelegate {
    let device: MTLDevice
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let textures: [MTLTexture]
    var uniforms = [Float](repeating: 0, count: 40)

    init(device: MTLDevice) throws {
        self.device = device
        guard let queue = device.makeCommandQueue(), let library = device.makeDefaultLibrary() else {
            throw ChromeZeroNumeralRenderError.unavailable
        }
        self.queue = queue
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "chromeZeroNumeralVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "chromeZeroNumeralFragment")
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        textures = try ["env", "card", "spark", "bokeh"].map { name in
            guard let image = UIImage(named: "chrome-zero-\(name)")?.cgImage else {
                throw ChromeZeroNumeralRenderError.missingAsset(name)
            }
            let width = image.width
            let height = image.height
            var bytes = [UInt8](repeating: 0, count: width * height * 4)
            let space = CGColorSpace(name: CGColorSpace.sRGB)!
            let drawn = bytes.withUnsafeMutableBytes { storage -> Bool in
                guard let context = CGContext(data: storage.baseAddress, width: width, height: height,
                    bitsPerComponent: 8, bytesPerRow: width * 4, space: space,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
                context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
                return true
            }
            guard drawn else { throw ChromeZeroNumeralRenderError.unavailable }
            // Display-space atlas values stay raw; there is no sRGB GPU decode or second premultiplication.
            let format = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm,
                width: width, height: height, mipmapped: false)
            format.usage = .shaderRead
            guard let texture = device.makeTexture(descriptor: format) else {
                throw ChromeZeroNumeralRenderError.unavailable
            }
            bytes.withUnsafeBytes { storage in
                texture.replace(region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0,
                                withBytes: storage.baseAddress!, bytesPerRow: width * 4)
            }
            return texture
        }
        super.init()
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard let drawable = view.currentDrawable, let pass = view.currentRenderPassDescriptor,
              let command = queue.makeCommandBuffer(), let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
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

private enum ChromeZeroNumeralRenderError: Error {
    case unavailable
    case missingAsset(String)
}
