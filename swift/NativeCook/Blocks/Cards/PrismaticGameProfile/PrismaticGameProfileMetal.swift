import MetalKit
import UIKit
import simd

/// Event-driven cached Metal surfaces; the parent owns the only animation clock.
@MainActor
final class PrismaticGameProfileMetal: MTKView, MTKViewDelegate {
    struct Uniforms:Equatable {
        var bounds=SIMD4<Float>(0,0,0,0)
        var shape=SIMD4<Float>(0,0,0,0)
        var light=SIMD4<Float>(0,0,0,0)
        var avoidance=SIMD4<Float>(0,0,0,0)
        var optics=SIMD4<Float>(0,0,0,0)
        var canvas=SIMD4<Float>(393,690,0,0)
        var imageFit=SIMD4<Float>(0,0,114,126)
    }
    private struct Resources {
        let device:MTLDevice;let queue:MTLCommandQueue;let card:MTLRenderPipelineState;let badge:MTLRenderPipelineState
    }
    private static let resources:Resources? = {
        guard let device=MTLCreateSystemDefaultDevice(),let queue=device.makeCommandQueue(),let library=device.makeDefaultLibrary(),let vertex=library.makeFunction(name:"prismaticGameProfileVertex") else{return nil}
        func pipeline(_ name:String)throws->MTLRenderPipelineState {
            let d=MTLRenderPipelineDescriptor();d.vertexFunction=vertex;d.fragmentFunction=library.makeFunction(name:name)
            d.colorAttachments[0].pixelFormat = .bgra8Unorm
            d.colorAttachments[0].isBlendingEnabled=true
            d.colorAttachments[0].sourceRGBBlendFactor = .one;d.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
            d.colorAttachments[0].sourceAlphaBlendFactor = .one;d.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
            return try device.makeRenderPipelineState(descriptor:d)
        }
        guard let card=try? pipeline("prismaticGameProfileCard"),let badge=try? pipeline("prismaticGameProfileBadge") else{return nil}
        return Resources(device:device,queue:queue,card:card,badge:badge)
    }()
    private static var textures=[String:MTLTexture]()
    private var texture:MTLTexture?
    private let card:Bool
    private var previous:Uniforms?
    private var pending=Uniforms()
    private(set) var available=false
    init(card:Bool,asset:String?=nil) {
        self.card=card
        super.init(frame:.zero,device:Self.resources?.device)
        isOpaque=false;backgroundColor = .clear;clearColor=MTLClearColorMake(0,0,0,0)
        colorPixelFormat = .bgra8Unorm;(layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB);framebufferOnly=true;isPaused=true;enableSetNeedsDisplay=false;autoResizeDrawable=true
        isUserInteractionEnabled=false;isAccessibilityElement=false;delegate=self
        if let asset,let resources=Self.resources {
            if let cached=Self.textures[asset] {texture=cached}
            else if let cg=PrismaticGameProfileAssets.image(asset)?.cgImage {
                // Premultiplied RGBA matches the Skia image-shader contract, without a colour-space reinterpretation.
                let loader=MTKTextureLoader(device:resources.device)
                texture=try? loader.newTexture(cgImage:cg,options:[.SRGB:false,.origin:MTKTextureLoader.Origin.topLeft])
                Self.textures[asset]=texture
            }
        }
        available=Self.resources != nil && (card || texture != nil)
    }
    required init(coder:NSCoder) {fatalError("init(coder:) has not been implemented")}
    func render(_ uniforms:Uniforms) {
        guard available,bounds.width>0,bounds.height>0,previous != uniforms else{return}
        pending=uniforms
        // MTKView owns drawable acquisition/release for each explicit draw cycle.
        draw()
    }
    nonisolated func draw(in view:MTKView){MainActor.assumeIsolated{encode()}}
    nonisolated func mtkView(_ view:MTKView,drawableSizeWillChange size:CGSize){MainActor.assumeIsolated{previous=nil}}
    private func encode(){
        guard let resources=Self.resources else{return}
        let uniforms=pending
        guard let drawable=currentDrawable,let descriptor=currentRenderPassDescriptor,let command=resources.queue.makeCommandBuffer(),let encoder=command.makeRenderCommandEncoder(descriptor:descriptor) else{return}
        previous=uniforms
        encoder.setRenderPipelineState(card ? resources.card:resources.badge)
        var u=uniforms;encoder.setFragmentBytes(&u,length:MemoryLayout<Uniforms>.stride,index:0)
        if !card {encoder.setFragmentTexture(texture,index:0)}
        encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3);encoder.endEncoding();command.present(drawable);command.commit()
    }
    func invalidateSurface(){previous=nil}
}
