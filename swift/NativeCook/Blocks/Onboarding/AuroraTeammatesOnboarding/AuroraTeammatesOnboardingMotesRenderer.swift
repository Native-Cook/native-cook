import MetalKit

final class AuroraTeammatesOnboardingMotesRenderer:NSObject,MTKViewDelegate {
    typealias GL=AuroraTeammatesOnboardingMotion.GL
    let device=MTLCreateSystemDefaultDevice()
    private var queue:MTLCommandQueue?,compute:MTLComputePipelineState?,draw:MTLRenderPipelineState?,motes:MTLBuffer?
    var layout=AuroraTeammatesOnboardingTheme.Layout(W:402,H:874)
    var scene=Array(repeating:0.0,count:206)
    override init() {
        super.init()
        guard let device,let lib=device.makeDefaultLibrary() else { return }
        queue=device.makeCommandQueue()
        do {
            compute=try device.makeComputePipelineState(function:lib.makeFunction(name:"auroraTeammatesOnboardingDustCs")!)
            let d=MTLRenderPipelineDescriptor();d.vertexFunction=lib.makeFunction(name:"auroraTeammatesOnboardingDustVs");d.fragmentFunction=lib.makeFunction(name:"auroraTeammatesOnboardingDustFs")
            let a=d.colorAttachments[0]!
            a.pixelFormat = .bgra8Unorm;a.isBlendingEnabled=true;a.sourceRGBBlendFactor = .one;a.destinationRGBBlendFactor = .one;a.sourceAlphaBlendFactor = .one;a.destinationAlphaBlendFactor = .oneMinusSourceAlpha
            draw=try device.makeRenderPipelineState(descriptor:d)
            motes=device.makeBuffer(length:1536*32,options:.storageModeShared)
        } catch { print("AuroraTeammatesOnboarding dust pipeline: \(error)") }
    }
    func mtkView(_ view:MTKView,drawableSizeWillChange size:CGSize) {}
    func draw(in view:MTKView) {
        guard let compute,let draw,let motes,let command=queue?.makeCommandBuffer(),let drawable=view.currentDrawable,let pass=view.currentRenderPassDescriptor else { return }
        let l=layout,sc=scene,k=Double(view.drawableSize.width)/l.W
        var v=Array(repeating:Float(0),count:68)
        let globals=[l.W*k,l.H*k,k,sc[GL.phase]/(2 * .pi)*9,sc[GL.coreX]*k,sc[GL.coreY]*k,sc[GL.flare],sc[GL.u],sc[GL.w0],sc[GL.w1],sc[GL.w2],sc[GL.sheetTop]*k,l.half*k,sc[GL.dip]*k,sc[GL.tiltX]*10*k,sc[GL.tiltY]*7*k,sc[GL.sinkX]*k,sc[GL.sinkY]*k,sc[GL.sinkMix]>0 ? 1:0,0]
        for i in globals.indices { v[i]=Float(globals[i]) }
        let finale=sc[GL.sinkMix]>0,moving=sc[GL.u]>0 || finale,from=Int(sc[GL.from]),to=Int(sc[GL.to]),first=[0,2,7],count=[2,5,6]
        func set(_ dst:Int,_ index:Int,_ progress:Int) {
            let o=index*13
            v[dst]=Float(sc[o]*k);v[dst+1]=Float(sc[o+1]*k);v[dst+2]=Float(max(sc[o+6],sc[o+7],18)*sc[o+4]*k);v[dst+3]=Float(sc[o+progress])
        }
        for j in 0..<6 {
            let a=20+j*4,b=44+j*4
            if finale {
                set(a,7+j,11)
                let ang=Double.pi*(0.1+0.8*Double(j)/5)
                v[b]=Float((sc[GL.sinkX]+cos(ang)*l.W*0.4)*k);v[b+1]=Float((sc[GL.sinkY]-sin(ang)*l.S*70)*k);v[b+2]=Float(26*l.S*k);v[b+3]=Float(min(1,max(0,(sc[GL.burst]-0.05*abs(Double(j)-2.5))/0.8)))
            } else {
                if moving && j<count[from] { set(a,first[from]+j,11) }
                if moving && j<count[to] { set(b,first[to]+j,12) }
            }
        }
        v.withUnsafeBytes { u in
            if let e=command.makeComputeCommandEncoder() {
                e.setComputePipelineState(compute);e.setBytes(u.baseAddress!,length:u.count,index:0);e.setBuffer(motes,offset:0,index:1)
                e.dispatchThreadgroups(MTLSize(width:24,height:1,depth:1),threadsPerThreadgroup:MTLSize(width:64,height:1,depth:1));e.endEncoding()
            }
            if let e=command.makeRenderCommandEncoder(descriptor:pass) {
                e.setRenderPipelineState(draw);e.setVertexBytes(u.baseAddress!,length:u.count,index:0);e.setVertexBuffer(motes,offset:0,index:1)
                e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:6,instanceCount:1536);e.endEncoding()
            }
        }
        command.present(drawable);command.commit()
    }
}
