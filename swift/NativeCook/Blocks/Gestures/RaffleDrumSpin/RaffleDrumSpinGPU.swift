import MetalKit
import simd
import SwiftUI

struct RaffleDrumSpinGPU: UIViewRepresentable {
    enum Layer { case drum, foil }

    let layer: Layer
    let frame: RaffleDrumSpinMotion.Frame

    func makeCoordinator() -> Renderer { Renderer(layer: layer) }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: MTLCreateSystemDefaultDevice())
        view.isOpaque = false
        view.backgroundColor = .clear
        view.clearColor = MTLClearColorMake(0, 0, 0, 0)
        view.colorPixelFormat = .bgra8Unorm
        view.depthStencilPixelFormat = .depth32Float
        view.sampleCount = 4
        view.framebufferOnly = true
        // Publish the Metal surfaces with the Canvas layers from the same story frame.
        view.presentsWithTransaction = true
        view.enableSetNeedsDisplay = true
        view.isPaused = true
        context.coordinator.attach(view)
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        if context.coordinator.update(frame) { view.setNeedsDisplay() }
    }

    final class Renderer: NSObject, MTKViewDelegate {
        private struct FrameUniforms {
            var viewport = SIMD4<Float>()
            var drum = SIMD4<Float>()
            var cam = SIMD4<Float>()
            var misc = SIMD4<Float>()
            var burst = SIMD4<Float>()
            var ghost = SIMD4<Float>()
        }

        private struct BallUniforms {
            var pos = SIMD4<Float>()
            var rot = SIMD4<Float>()
            var fx = SIMD4<Float>()
        }

        private let layer: Layer
        private var frame: RaffleDrumSpinMotion.Frame
        private weak var view: MTKView?
        private var queue: MTLCommandQueue?
        private var drumPipeline: MTLRenderPipelineState?
        private var ghostPipeline: MTLRenderPipelineState?
        private var ballPipeline: MTLRenderPipelineState?
        private var foilPipeline: MTLRenderPipelineState?
        private var depthState: MTLDepthStencilState?
        private var ghostDepthState: MTLDepthStencilState?
        private var vertexBuffer: MTLBuffer?
        private var vertexCount = 0
        private var envTexture: MTLTexture?
        private var confettiTexture: MTLTexture?

        init(layer: Layer) {
            self.layer = layer
            frame = RaffleDrumSpinMotion.frameAt(0)
        }

        func attach(_ view: MTKView) {
            self.view = view
            guard let device = view.device, let library = device.makeDefaultLibrary() else { return }
            queue = device.makeCommandQueue()
            if layer == .drum {
                let mesh = RaffleDrumSpinMesh.build()
                vertexCount = mesh.count
                vertexBuffer = device.makeBuffer(bytes: mesh, length: mesh.count * MemoryLayout<RaffleDrumSpinMesh.Vertex>.stride)
                drumPipeline = pipeline(device, library, "raffleDrumSpinDrumVertex", "raffleDrumSpinDrumFragment", depthWrite: true)
                ghostPipeline = pipeline(device, library, "raffleDrumSpinDrumVertex", "raffleDrumSpinDrumFragment", depthWrite: false)
                ballPipeline = pipeline(device, library, "raffleDrumSpinBallVertex", "raffleDrumSpinBallFragment", depthWrite: true)
            } else {
                foilPipeline = pipeline(device, library, "raffleDrumSpinFoilVertex", "raffleDrumSpinFoilFragment", depthWrite: false)
            }
            let depth = MTLDepthStencilDescriptor()
            depth.depthCompareFunction = .less
            depth.isDepthWriteEnabled = true
            depthState = device.makeDepthStencilState(descriptor: depth)
            depth.isDepthWriteEnabled = false
            ghostDepthState = device.makeDepthStencilState(descriptor: depth)
            let loader = MTKTextureLoader(device: device)
            if layer == .drum, let url = Bundle.main.url(forResource: "RaffleDrumSpin-env-studio", withExtension: "jpg") {
                envTexture = try? loader.newTexture(URL: url, options: [.SRGB: false, .generateMipmaps: false])
            }
            if layer == .foil, let url = Bundle.main.url(forResource: "RaffleDrumSpin-confetti-atlas", withExtension: "webp") {
                confettiTexture = Self.loadConfettiTexture(device: device, url: url)
            }
            view.delegate = self
        }

        /// The source atlas stores hand-authored mip levels side by side. React Native copies
        /// those rectangles into a 1024-square GPU texture; generating mips from the packed image
        /// samples unrelated atlas cells and visibly changes the burst.
        private static func loadConfettiTexture(device: MTLDevice, url: URL) -> MTLTexture? {
            guard let source = UIImage(contentsOfFile: url.path)?.cgImage else { return nil }
            let descriptor = MTLTextureDescriptor.texture2DDescriptor(
                pixelFormat: .rgba8Unorm,
                width: 1024,
                height: 1024,
                mipmapped: true
            )
            descriptor.mipmapLevelCount = 5
            descriptor.usage = [.shaderRead]
            guard let texture = device.makeTexture(descriptor: descriptor) else { return nil }
            let levels = [
                CGRect(x: 0, y: 0, width: 1024, height: 1024),
                CGRect(x: 1024, y: 0, width: 512, height: 512),
                CGRect(x: 1024, y: 512, width: 256, height: 256),
                CGRect(x: 1280, y: 512, width: 128, height: 128),
                CGRect(x: 1408, y: 512, width: 64, height: 64),
            ]
            for (level, rect) in levels.enumerated() {
                guard let crop = source.cropping(to: rect) else { return nil }
                let width = Int(rect.width)
                let height = Int(rect.height)
                var bytes = [UInt8](repeating: 0, count: width * height * 4)
                guard let context = CGContext(
                    data: &bytes,
                    width: width,
                    height: height,
                    bitsPerComponent: 8,
                    bytesPerRow: width * 4,
                    space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
                ) else { return nil }
                context.draw(crop, in: CGRect(x: 0, y: 0, width: width, height: height))
                // WebGPU's copyExternalImageToTexture call explicitly uploads straight alpha.
                // Core Graphics rasterizes into premultiplied RGBA, so undo that conversion
                // before Metal samples the atlas (the fragment shader applies coverage itself).
                for pixel in 0..<(width * height) {
                    let offset = pixel * 4
                    let alpha = Int(bytes[offset + 3])
                    guard alpha > 0, alpha < 255 else { continue }
                    bytes[offset] = UInt8(min(255, Int(bytes[offset]) * 255 / alpha))
                    bytes[offset + 1] = UInt8(min(255, Int(bytes[offset + 1]) * 255 / alpha))
                    bytes[offset + 2] = UInt8(min(255, Int(bytes[offset + 2]) * 255 / alpha))
                }
                texture.replace(
                    region: MTLRegionMake2D(0, 0, width, height),
                    mipmapLevel: level,
                    withBytes: bytes,
                    bytesPerRow: width * 4
                )
            }
            return texture
        }

        private func pipeline(_ device: MTLDevice, _ library: MTLLibrary, _ vertex: String, _ fragment: String, depthWrite: Bool) -> MTLRenderPipelineState? {
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = library.makeFunction(name: vertex)
            descriptor.fragmentFunction = library.makeFunction(name: fragment)
            descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
            descriptor.colorAttachments[0].isBlendingEnabled = true
            descriptor.colorAttachments[0].rgbBlendOperation = .add
            descriptor.colorAttachments[0].alphaBlendOperation = .add
            descriptor.colorAttachments[0].sourceRGBBlendFactor = .one
            descriptor.colorAttachments[0].sourceAlphaBlendFactor = .one
            descriptor.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
            descriptor.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
            descriptor.depthAttachmentPixelFormat = .depth32Float
            descriptor.rasterSampleCount = 4
            return try? device.makeRenderPipelineState(descriptor: descriptor)
        }

        func update(_ frame: RaffleDrumSpinMotion.Frame) -> Bool {
            let redraw = layer == .drum || frame.burst >= 0 || self.frame.burst >= 0
            self.frame = frame
            return redraw
        }
        func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) { view.setNeedsDisplay() }

        func draw(in view: MTKView) {
            guard let pass = view.currentRenderPassDescriptor,
                  let drawable = view.currentDrawable,
                  let command = queue?.makeCommandBuffer(),
                  let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
            let scale = Float(view.window?.screen.scale ?? UIScreen.main.scale)
            let width = Float(view.drawableSize.width)
            let height = Float(view.drawableSize.height)
            var uniforms = FrameUniforms(
                viewport: SIMD4(width, height, scale, Float(frame.t)),
                drum: SIMD4(Float(205 * Double(width) / 402), Float(178 * Double(height) / 874), Float(70 * 6 * Double(width) / 402), Float(frame.theta)),
                cam: SIMD4(Float(cos(0.2)), Float(sin(0.2)), Float(cos(-0.27)), Float(sin(-0.27))),
                misc: SIMD4(6, Float(frame.speed), Float(frame.glow), Float(frame.burst)),
                burst: SIMD4(Float(201 * Double(width) / 402), Float(430 * Double(height) / 874), Float(56 * Double(width) / 402), 200),
                ghost: SIMD4(Float(abs(frame.omega) / 60 / 5), abs(frame.omega) > 4 ? 5 : 0, envTexture == nil ? 0 : 1, confettiTexture == nil ? 0 : 1)
            )
            encoder.setVertexBytes(&uniforms, length: MemoryLayout<FrameUniforms>.stride, index: 1)
            encoder.setFragmentBytes(&uniforms, length: MemoryLayout<FrameUniforms>.stride, index: 1)
            if layer == .drum {
                drawDrum(encoder, uniforms: &uniforms, scale: scale)
            } else if frame.burst >= 0 {
                drawFoil(encoder)
            }
            encoder.endEncoding()
            command.commit()
            command.waitUntilScheduled()
            drawable.present()
        }

        private func drawDrum(_ encoder: MTLRenderCommandEncoder, uniforms: inout FrameUniforms, scale: Float) {
            guard let drumPipeline, let vertexBuffer else { return }
            encoder.setDepthStencilState(depthState)
            encoder.setRenderPipelineState(drumPipeline)
            encoder.setVertexBuffer(vertexBuffer, offset: 0, index: 0)
            encoder.setFragmentTexture(envTexture, index: 0)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: vertexCount)

            if let ballPipeline {
                let balls = (0..<3).map { index -> BallUniforms in
                    guard index < frame.balls.count else { return BallUniforms(pos: SIMD4(0, 0, 1, 5), rot: .zero, fx: .zero) }
                    let ball = frame.balls[index]
                    return BallUniforms(
                        pos: SIMD4(Float(ball.x) * scale, Float(ball.y) * scale, Float(ball.r) * scale, Float(ball.z)),
                        rot: SIMD4(Float(ball.rx), Float(ball.ry), Float(ball.rz), Float(ball.a)),
                        fx: SIMD4(Float(ball.glow), 0, 0, 0)
                    )
                }
                balls.withUnsafeBytes { bytes in
                    encoder.setRenderPipelineState(ballPipeline)
                    encoder.setVertexBytes(bytes.baseAddress!, length: bytes.count, index: 2)
                    encoder.setFragmentBytes(bytes.baseAddress!, length: bytes.count, index: 2)
                    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: 3)
                }
            }
            if abs(frame.omega) > 4, let ghostPipeline {
                encoder.setDepthStencilState(ghostDepthState)
                encoder.setRenderPipelineState(ghostPipeline)
                encoder.setVertexBuffer(vertexBuffer, offset: 0, index: 0)
                encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: vertexCount, instanceCount: 5, baseInstance: 1)
            }
        }

        private func drawFoil(_ encoder: MTLRenderCommandEncoder) {
            guard let foilPipeline else { return }
            encoder.setDepthStencilState(ghostDepthState)
            encoder.setRenderPipelineState(foilPipeline)
            encoder.setFragmentTexture(confettiTexture, index: 1)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: 200)
        }
    }
}

private enum RaffleDrumSpinMesh {
    struct Vertex {
        var px: Float
        var py: Float
        var pz: Float
        var nx: Float
        var ny: Float
        var nz: Float
        var material: Float
        var spins: Float
    }

    private final class Builder {
        var data: [Vertex] = []
        func push(_ p: SIMD3<Double>, _ n: SIMD3<Double>, _ material: Double, _ spins: Double) {
            data.append(Vertex(
                px: Float(p.x), py: Float(p.y), pz: Float(p.z),
                nx: Float(n.x), ny: Float(n.y), nz: Float(n.z),
                material: Float(material), spins: Float(spins)
            ))
        }
        func tri(_ a: (SIMD3<Double>, SIMD3<Double>), _ b: (SIMD3<Double>, SIMD3<Double>), _ c: (SIMD3<Double>, SIMD3<Double>), _ material: Double, _ spins: Double) {
            push(a.0, a.1, material, spins); push(b.0, b.1, material, spins); push(c.0, c.1, material, spins)
        }
        func tube(_ path: [SIMD3<Double>], _ rw: Double, _ rh: Double, _ closed: Bool, _ material: Double, _ spins: Double, _ sides: Int = 10, _ up: SIMD3<Double> = SIMD3(0, 0, 1)) {
            var rings: [[(SIMD3<Double>, SIMD3<Double>)]] = []
            for index in path.indices {
                let previous = path[closed ? (index - 1 + path.count) % path.count : max(0, index - 1)]
                let next = path[closed ? (index + 1) % path.count : min(path.count - 1, index + 1)]
                let tangent = normalize(next - previous)
                var side = simd_cross(tangent, up)
                if simd_length(side) < 1e-4 { side = simd_cross(tangent, SIMD3(0, 1, 0)) }
                side = normalize(side)
                let lift = normalize(simd_cross(side, tangent))
                rings.append((0..<sides).map { j in
                    let angle = Double(j) / Double(sides) * 2 * .pi
                    let offset = side * (cos(angle) * rw) + lift * (sin(angle) * rh)
                    let normal = normalize(side * (cos(angle) / rw) + lift * (sin(angle) / rh))
                    return (path[index] + offset, normal)
                })
            }
            let segments = closed ? path.count : path.count - 1
            for index in 0..<segments {
                for j in 0..<sides {
                    let j1 = (j + 1) % sides
                    tri(rings[index][j], rings[(index + 1) % path.count][j], rings[(index + 1) % path.count][j1], material, spins)
                    tri(rings[index][j], rings[(index + 1) % path.count][j1], rings[index][j1], material, spins)
                }
            }
            if !closed {
                for (index, direction) in [(0, -1.0), (path.count - 1, 1.0)] {
                    let tangent = normalize(path[min(path.count - 1, index + 1)] - path[max(0, index - 1)])
                    let normal = tangent * direction
                    for j in 0..<sides {
                        tri((path[index], normal), (rings[index][j].0, normal), (rings[index][(j + 1) % sides].0, normal), material, spins)
                    }
                }
            }
        }
        func box(_ minimum: SIMD3<Double>, _ maximum: SIMD3<Double>, _ material: Double, _ spins: Double) {
            let x0 = minimum.x, y0 = minimum.y, z0 = minimum.z, x1 = maximum.x, y1 = maximum.y, z1 = maximum.z
            let faces: [(SIMD3<Double>, SIMD3<Double>, SIMD3<Double>, SIMD3<Double>, SIMD3<Double>)] = [
                (SIMD3(x0,y1,z0),SIMD3(x1,y1,z0),SIMD3(x1,y1,z1),SIMD3(x0,y1,z1),SIMD3(0,1,0)),
                (SIMD3(x0,y0,z1),SIMD3(x1,y0,z1),SIMD3(x1,y0,z0),SIMD3(x0,y0,z0),SIMD3(0,-1,0)),
                (SIMD3(x0,y0,z1),SIMD3(x0,y1,z1),SIMD3(x1,y1,z1),SIMD3(x1,y0,z1),SIMD3(0,0,1)),
                (SIMD3(x1,y0,z0),SIMD3(x1,y1,z0),SIMD3(x0,y1,z0),SIMD3(x0,y0,z0),SIMD3(0,0,-1)),
                (SIMD3(x1,y0,z1),SIMD3(x1,y1,z1),SIMD3(x1,y1,z0),SIMD3(x1,y0,z0),SIMD3(1,0,0)),
                (SIMD3(x0,y0,z0),SIMD3(x0,y1,z0),SIMD3(x0,y1,z1),SIMD3(x0,y0,z1),SIMD3(-1,0,0))]
            for face in faces {
                tri((face.0,face.4),(face.1,face.4),(face.2,face.4),material,spins)
                tri((face.0,face.4),(face.2,face.4),(face.3,face.4),material,spins)
            }
        }

        func lathe(
            x0: Double, x1: Double, y: Double, z: Double,
            radius: (Double) -> Double, material: Double, spins: Double,
            steps: Int = 24, sides: Int = 18
        ) {
            func ring(_ index: Int) -> [(SIMD3<Double>, SIMD3<Double>)] {
                let u = Double(index) / Double(steps)
                let r = radius(u)
                let dr = (radius(min(1, u + 0.01)) - radius(max(0, u - 0.01))) / (0.02 * (x1 - x0))
                return (0...sides).map { side in
                    let angle = Double(side) / Double(sides) * 2 * .pi
                    return (
                        SIMD3(x0 + (x1 - x0) * u, y + cos(angle) * r, z + sin(angle) * r),
                        normalize(SIMD3(-dr, cos(angle), sin(angle)))
                    )
                }
            }
            var previous = ring(0)
            for index in 1...steps {
                let next = ring(index)
                for side in 0..<sides {
                    tri(previous[side], next[side], next[side + 1], material, spins)
                    tri(previous[side], next[side + 1], previous[side + 1], material, spins)
                }
                previous = next
            }
        }

        func slab(
            halfWidth: Double, halfDepth: Double, y0: Double, y1: Double,
            cornerRadius: Double, bevel: Double, top: Double, side: Double
        ) {
            struct Outline { let point: SIMD2<Double>; let normal: SIMD2<Double> }
            struct Profile { let offset: Double; let y: Double; let radial: Double; let vertical: Double; let material: Double }
            let corners: [(Double, Double, Double)] = [
                (halfWidth - cornerRadius, halfDepth - cornerRadius, 0),
                (-(halfWidth - cornerRadius), halfDepth - cornerRadius, .pi / 2),
                (-(halfWidth - cornerRadius), -(halfDepth - cornerRadius), .pi),
                (halfWidth - cornerRadius, -(halfDepth - cornerRadius), 3 * .pi / 2),
            ]
            var outline: [Outline] = []
            for (cx, cz, a0) in corners {
                for index in 0...8 {
                    let angle = a0 + Double(index) / 8 * (.pi / 2)
                    outline.append(Outline(
                        point: SIMD2(cx + cos(angle) * cornerRadius, cz + sin(angle) * cornerRadius),
                        normal: SIMD2(cos(angle), sin(angle))
                    ))
                }
            }
            var profile: [Profile] = []
            for index in 0...5 {
                let f = (.pi / 2) * (1 - Double(index) / 5)
                profile.append(Profile(
                    offset: -bevel + bevel * cos(f), y: y1 - bevel + bevel * sin(f),
                    radial: cos(f), vertical: sin(f), material: index < 3 ? top : side
                ))
            }
            for index in 0...5 {
                let f = -(.pi / 2) * (Double(index) / 5)
                profile.append(Profile(
                    offset: -bevel + bevel * cos(f), y: y0 + bevel + bevel * sin(f),
                    radial: cos(f), vertical: sin(f), material: side
                ))
            }
            func point(_ outlineIndex: Int, _ profileIndex: Int) -> (SIMD3<Double>, SIMD3<Double>) {
                let edge = outline[outlineIndex % outline.count]
                let section = profile[profileIndex]
                return (
                    SIMD3(
                        edge.point.x + edge.normal.x * section.offset,
                        section.y,
                        edge.point.y + edge.normal.y * section.offset
                    ),
                    normalize(SIMD3(edge.normal.x * section.radial, section.vertical, edge.normal.y * section.radial))
                )
            }
            for edge in outline.indices {
                for section in 0..<(profile.count - 1) {
                    let material = profile[section].material
                    tri(point(edge, section), point(edge + 1, section), point(edge + 1, section + 1), material, 0)
                    tri(point(edge, section), point(edge + 1, section + 1), point(edge, section + 1), material, 0)
                }
            }
            for (section, vertical, material) in [(0, 1.0, top), (profile.count - 1, -1.0, side)] {
                let centre = (SIMD3(0.0, profile[section].y, 0.0), SIMD3(0.0, vertical, 0.0))
                for edge in outline.indices {
                    let a = point(edge, section)
                    let d = point(edge + 1, section)
                    tri(centre, (a.0, SIMD3(0, vertical, 0)), (d.0, SIMD3(0, vertical, 0)), material, 0)
                }
            }
        }
    }

    static func build() -> [Vertex] {
        let mesh = Builder()
        let tilt = 0.4, ct = cos(tilt), st = sin(tilt)
        for k in 0..<6 {
            let angle = Double(k) / 6 * .pi + 0.26
            let path = (0..<120).map { index -> SIMD3<Double> in
                let u = Double(index) / 120 * 2 * .pi
                let x = sin(u) * cos(angle), y = cos(u), z = sin(u) * sin(angle)
                return SIMD3((ct*x-st*y)*0.99, (st*x+ct*y)*0.99, z*0.99)
            }
            mesh.tube(path, 0.022, 0.022, true, 0, 1, 8)
        }
        mesh.tube((0..<120).map { let u = Double($0)/120*2*Double.pi; return SIMD3(cos(u)*0.995,0,sin(u)*0.995) }, 0.022, 0.022, true, 0, 1, 8, SIMD3(0,1,0))
        for pair in [(-1.28,-0.96),(0.96,1.34)] { mesh.tube([SIMD3(pair.0,0,0),SIMD3(pair.1,0,0)],0.028,0.028,false,0,1,12,SIMD3(0,1,0)) }
        mesh.tube([SIMD3(1.32,0.02,0),SIMD3(1.32,-0.2,0)],0.03,0.022,false,0,1,10,SIMD3(1,0,0))
        mesh.lathe(x0: 1.29, x1: 1.37, y: -0.2, z: 0, radius: { 0.05 + 0.012 * sin(.pi * $0) }, material: 0, spins: 1, steps: 6, sides: 16)
        mesh.lathe(x0: 1.37, x1: 1.78, y: -0.2, z: 0, radius: { u in
            let end = u > 0.86 ? sqrt(max(0, 1 - pow((u - 0.86) / 0.14, 2))) : 1
            return (0.046 + 0.02 * pow(sin(.pi * min(1, u * 1.08)), 0.8)) * end
        }, material: 3, spins: 1, steps: 28, sides: 18)
        for side in [-1.0,1.0] {
            mesh.tube([SIMD3(side*0.97,0,0),SIMD3(side*1.13,0,0)],0.07,0.07,false,0,1,16,SIMD3(0,1,0))
            mesh.tube([SIMD3(side*1.14,0,0),SIMD3(side*1.2,0,0)],0.075,0.09,false,1,0,12,SIMD3(0,1,0))
            mesh.box(SIMD3(side > 0 ? 1.11 : -1.23,-0.15,-0.08), SIMD3(side > 0 ? 1.23 : -1.11,0.11,0.08),1,0)
            let p0=SIMD3(side*1.2,-0.1,0.0), p1=SIMD3(side*1.44,-0.42,0.0), p2=SIMD3(side*1.44,-1.06,0.0), p3=SIMD3(side*1.02,-1.32,0.0)
            let path=(0...30).map { i -> SIMD3<Double> in let t=Double(i)/30, u=1-t; return p0*(u*u*u)+p1*(3*u*u*t)+p2*(3*u*t*t)+p3*(t*t*t) }
            mesh.tube(path,0.045,0.085,false,1,0,12)
        }
        mesh.slab(halfWidth: 1.08, halfDepth: 0.56, y0: -1.44, y1: -1.3, cornerRadius: 0.18, bevel: 0.035, top: 2, side: 1)
        return mesh.data
    }
}
