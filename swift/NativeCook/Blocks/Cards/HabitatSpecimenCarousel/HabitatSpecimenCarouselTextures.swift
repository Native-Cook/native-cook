import Metal
import MetalPerformanceShaders
import UIKit

/// The three creatures' art, as premultiplied RGBA textures, and a normal map baked once from each
/// one's alpha: a wide blur gives the body its dome, a near one keeps limbs and edges crisp. Any
/// cut-out PNG gets a volume this way, with no extra asset. Mirrors bakeNormals in
/// HabitatCreature.tsx and NORMAL_BAKE in HabitatShaders.ts.
final class HabitatSpecimenCarouselTextures {
    static let art = 512
    let images: [MTLTexture]
    let normals: [MTLTexture]

    init?(device: MTLDevice, queue: MTLCommandQueue, library: MTLLibrary) {
        guard let function = library.makeFunction(name: "habitatSCNormalBake"),
              let bake = try? device.makeComputePipelineState(function: function),
              let commands = queue.makeCommandBuffer() else { return nil }
        var images: [MTLTexture] = []
        var normals: [MTLTexture] = []
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: Self.art, height: Self.art, mipmapped: false)
        descriptor.usage = [.shaderRead, .shaderWrite]
        let blur = { (sigma: Float) -> MPSImageGaussianBlur in
            let kernel = MPSImageGaussianBlur(device: device, sigma: sigma)
            kernel.edgeMode = .zero
            return kernel
        }
        // Skia's blur filters with sigma 16 (the dome) and 5 (limbs and edges).
        let wideBlur = blur(16), nearBlur = blur(5)
        for specimen in HabitatSpecimenCarouselSpecimen.all {
            guard let url = Bundle.main.url(forResource: "HabitatSpecimenCarousel-" + specimen.asset, withExtension: "png"),
                  let source = UIImage(contentsOfFile: url.path)?.cgImage,
                  let pixels = Self.premultiplied(source),
                  let image = device.makeTexture(descriptor: descriptor),
                  let wide = device.makeTexture(descriptor: descriptor),
                  let near = device.makeTexture(descriptor: descriptor),
                  let normal = device.makeTexture(descriptor: descriptor) else { return nil }
            image.replace(region: MTLRegionMake2D(0, 0, Self.art, Self.art), mipmapLevel: 0, withBytes: pixels, bytesPerRow: Self.art * 4)
            wideBlur.encode(commandBuffer: commands, sourceTexture: image, destinationTexture: wide)
            nearBlur.encode(commandBuffer: commands, sourceTexture: image, destinationTexture: near)
            guard let encoder = commands.makeComputeCommandEncoder() else { return nil }
            encoder.setComputePipelineState(bake)
            encoder.setTexture(wide, index: 0)
            encoder.setTexture(near, index: 1)
            encoder.setTexture(image, index: 2)
            encoder.setTexture(normal, index: 3)
            let group = MTLSize(width: 16, height: 16, depth: 1)
            let grid = MTLSize(width: (Self.art + 15) / 16, height: (Self.art + 15) / 16, depth: 1)
            encoder.dispatchThreadgroups(grid, threadsPerThreadgroup: group)
            encoder.endEncoding()
            images.append(image)
            normals.append(normal)
        }
        commands.commit()
        commands.waitUntilCompleted()
        self.images = images
        self.normals = normals
    }

    /// The PNG drawn into 512 x 512 premultiplied RGBA, the contract of a Skia image shader.
    private static func premultiplied(_ image: CGImage) -> [UInt8]? {
        let size = art
        var pixels = [UInt8](repeating: 0, count: size * size * 4)
        let space = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        let ok = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: size, height: size, bitsPerComponent: 8, bytesPerRow: size * 4,
                space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
            ) else { return false }
            context.interpolationQuality = .high
            context.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
            return true
        }
        return ok ? pixels : nil
    }
}
