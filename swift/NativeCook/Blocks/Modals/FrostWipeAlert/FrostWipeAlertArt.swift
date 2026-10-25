import CoreImage
import Metal
import SwiftUI
import UIKit

// The pane's static layers: the ice and the haze baked by the compute kernel frostWipeAlertBake
// (FrostWipeAlert.metal) on the GPU, off the main thread, and the app under the glass rendered
// with ImageRenderer and blurred with Core Image.
//
// The ice is baked at 2 pixels per point and drawn as an ordinary resized Image (the layer of the
// ice pass), so SwiftUI rasterises it at the screen's scale with smooth filtering and the feathers
// stay thin continuous lines. The blurred app is the layer of the frost pass. Only the haze, which
// is smooth, reaches a shader as an `.image` argument, made at 1 pixel per point.

enum FrostWipeAlertArt {
    /// Pixels per point of the ice image, and of the haze and the blurred app.
    static let iceScale = 2.0
    static let softScale = 1.0
    /// Blur of the app under the glass (pt): enough that the alert's text cannot be read.
    static let contentBlur = 9.0

    struct Ice: @unchecked Sendable {
        let ice: CGImage
        let haze: CGImage
    }

    private static let device = MTLCreateSystemDefaultDevice()
    private static let pipeline: MTLComputePipelineState? = {
        guard let device, let library = device.makeDefaultLibrary(),
              let function = library.makeFunction(name: "frostWipeAlertBake") else { return nil }
        return try? device.makeComputePipelineState(function: function)
    }()
    private static let queue = device?.makeCommandQueue()
    private static let lock = NSLock()
    nonisolated(unsafe) private static var cache: [String: Ice] = [:]

    private static func key(_ size: CGSize) -> String {
        "\(Int(size.width * 10))x\(Int(size.height * 10))"
    }

    /// The ice and the haze for this size if they were baked already.
    static func cached(_ size: CGSize) -> Ice? {
        lock.withLock { cache[key(size)] }
    }

    /// Bakes the ice and the haze for a screen of `size` points, off the main thread.
    static func bake(_ size: CGSize) async -> Ice? {
        guard size.width > 0, size.height > 0 else { return nil }
        if let ice = cached(size) { return ice }
        return await Task.detached(priority: .userInitiated) { () -> Ice? in
            guard let hazeImage = render(size, scale: softScale, mode: 1),
                  let iceImage = render(size, scale: iceScale, mode: 0) else { return nil }
            let ice = Ice(ice: iceImage, haze: hazeImage)
            lock.withLock { cache[key(size)] = ice }
            return ice
        }.value
    }

    private static func render(_ size: CGSize, scale: Double, mode: Float) -> CGImage? {
        guard let device, let pipeline, let queue else { return nil }
        let pw = max(1, Int((size.width * scale).rounded(.up)))
        let ph = max(1, Int((size.height * scale).rounded(.up)))
        let desc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: pw, height: ph, mipmapped: false)
        desc.usage = [.shaderWrite, .shaderRead]
        desc.storageMode = .shared
        guard let texture = device.makeTexture(descriptor: desc),
              let commands = queue.makeCommandBuffer(),
              let encoder = commands.makeComputeCommandEncoder() else { return nil }
        var params = SIMD4<Float>(Float(size.width), Float(size.height), Float(scale), mode)
        encoder.setComputePipelineState(pipeline)
        encoder.setTexture(texture, index: 0)
        encoder.setBytes(&params, length: MemoryLayout<SIMD4<Float>>.stride, index: 0)
        let w = pipeline.threadExecutionWidth
        let h = max(1, pipeline.maxTotalThreadsPerThreadgroup / w)
        let groups = MTLSize(width: (pw + w - 1) / w, height: (ph + h - 1) / h, depth: 1)
        encoder.dispatchThreadgroups(groups, threadsPerThreadgroup: MTLSize(width: w, height: h, depth: 1))
        encoder.endEncoding()
        commands.commit()
        commands.waitUntilCompleted()

        let rowBytes = pw * 4
        var bytes = [UInt8](repeating: 0, count: rowBytes * ph)
        texture.getBytes(&bytes, bytesPerRow: rowBytes, from: MTLRegionMake2D(0, 0, pw, ph), mipmapLevel: 0)
        guard let provider = CGDataProvider(data: Data(bytes) as CFData),
              let space = CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        return CGImage(
            width: pw,
            height: ph,
            bitsPerComponent: 8,
            bitsPerPixel: 32,
            bytesPerRow: rowBytes,
            space: space,
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
            provider: provider,
            decode: nil,
            shouldInterpolate: true,
            intent: .defaultIntent
        )
    }

    private static let ciContext = CIContext()

    /// The app under the glass at one pixel per point, blurred by contentBlur pt. The snapshot is
    /// taken on the main actor; the blur runs off it.
    @MainActor static func blurred<Content: View>(_ content: Content, size: CGSize) async -> Image? {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = softScale
        renderer.isOpaque = true
        guard let snapshot = renderer.cgImage else { return nil }
        let box = Snapshot(image: snapshot)
        let output = await Task.detached(priority: .userInitiated) { () -> Snapshot? in
            let input = CIImage(cgImage: box.image)
            let blurred = input.clampedToExtent()
                .applyingGaussianBlur(sigma: contentBlur * softScale)
                .cropped(to: input.extent)
            return ciContext.createCGImage(blurred, from: input.extent).map(Snapshot.init)
        }.value
        guard let output else { return nil }
        return Image(decorative: output.image, scale: softScale)
    }

    private struct Snapshot: @unchecked Sendable {
        let image: CGImage
    }
}
