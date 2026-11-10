import MetalKit

final class FilamentBulbDimmerRenderer:NSObject,MTKViewDelegate {
    let device=MTLCreateSystemDefaultDevice()!
    private var queue:MTLCommandQueue!
    private var pipelines:[String:MTLRenderPipelineState]=[:]
    private var compute:MTLComputePipelineState!
    private var sampler:MTLSamplerState!
    private var hdr:MTLTexture?,downs:[MTLTexture]=[],ups:[MTLTexture]=[]
    private var motes:MTLBuffer!,sparks:MTLBuffer!
    private var history=[SIMD4<Float>]()
    private var dimensions=CGSize.zero
    private var seedLayout:FilamentBulbDimmerTheme.Layout?
    var layout=FilamentBulbDimmerTheme.Layout(w:402,h:874,top:62,bottom:34)
    var frame=FilamentBulbDimmerMotion.Frame()
    var reduced=false
    var pinned:Double?
    private var replayedTime:Double?
    override init() {
        super.init(); queue=device.makeCommandQueue()
        do {
            guard let lib=device.makeDefaultLibrary() else { fatalError("FilamentBulbDimmer: missing Metal library") }
            for name in ["Scene","Dust","DownFirst","Down","Up","Out"] {
                let d=MTLRenderPipelineDescriptor()
                d.vertexFunction=lib.makeFunction(name:"filamentBulbDimmer\(name == "Dust" ? "Dust":"Full")Vs")
                d.fragmentFunction=lib.makeFunction(name:"filamentBulbDimmer\(name)Fs")
                let a=d.colorAttachments[0]!
                a.pixelFormat=name == "Out" ? .bgra8Unorm:.rgba16Float
                if name == "Dust" { a.isBlendingEnabled=true; a.sourceRGBBlendFactor = .one; a.destinationRGBBlendFactor = .one; a.sourceAlphaBlendFactor = .one; a.destinationAlphaBlendFactor = .one }
                pipelines[name]=try device.makeRenderPipelineState(descriptor:d)
            }
            compute=try device.makeComputePipelineState(function:lib.makeFunction(name:"filamentBulbDimmerDustCs")!)
            let s=MTLSamplerDescriptor(); s.minFilter = .linear; s.magFilter = .linear; s.sAddressMode = .clampToEdge; s.tAddressMode = .clampToEdge
            sampler=device.makeSamplerState(descriptor:s)
            sparks=device.makeBuffer(length:2600*32,options:.storageModeShared)
        } catch { fatalError("FilamentBulbDimmer Metal pipeline: \(error)") }
    }
    private func texture(_ w:Int,_ h:Int)->MTLTexture {
        let d=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rgba16Float,width:max(1,w),height:max(1,h),mipmapped:false)
        d.usage=[.renderTarget,.shaderRead]; d.storageMode = .private
        return device.makeTexture(descriptor:d)!
    }
    func mtkView(_ view:MTKView,drawableSizeWillChange size:CGSize) { dimensions = .zero }
    private func prepare(_ size:CGSize) {
        if dimensions != size {
            dimensions=size; let w=Int(size.width),h=Int(size.height)
            hdr=texture(w,h); downs=[]; ups=[]
            for i in 0..<6 { let div=1<<(i+1); downs.append(texture((w+div-1)/div,(h+div-1)/div)); if i<5 { ups.append(texture((w+div-1)/div,(h+div-1)/div)) } }
        }
        if seedLayout != layout {
            seedLayout=layout; replayedTime=nil; let l=layout,fx=l.anchorX,fy=l.restY+2.55*l.R,zl=2.4*l.R
            var seed=1234567.0
            func rnd()->Double { seed=(seed*1103515245+12345).truncatingRemainder(dividingBy:4294967296); seed=Double(UInt64(seed)&0x7fffffff); return seed/2147483647 }
            func gauss()->Double { sqrt(-2*log(max(1e-6,rnd())))*cos(2*Double.pi*rnd()) }
            var data=[SIMD4<Float>](); data.reserveCapacity(5200)
            for i in 0..<2600 {
                let x:Double,y:Double
                if i%10<7 { x=fx+gauss()*2.4*l.R; y=fy+gauss()*2.2*l.R }
                else { x=rnd()*l.w; y=l.anchorY-20+rnd()*(l.cy-l.anchorY+20) }
                let z=zl+l.R*(-2.2+7.5*pow(rnd(),1.6)),size=0.35+0.85*pow(rnd(),3)
                data.append(SIMD4(Float(x),Float(y),Float(z),Float(size))); data.append(SIMD4(Float(rnd()),Float(rnd()),Float(rnd()),Float(rnd())))
            }
            motes=data.withUnsafeBytes { device.makeBuffer(bytes:$0.baseAddress!,length:$0.count,options:.storageModeShared)! }
            history=Array(repeating:SIMD4(Float(fx),Float(fy),0,0),count:48)
        }
    }
    func draw(in view:MTKView) {
        guard let drawable=view.currentDrawable,let pass=view.currentRenderPassDescriptor,let command=queue.makeCommandBuffer() else { return }
        prepare(view.drawableSize)
        let l=layout,f=frame,p=l.pose(f),lc=FilamentBulbDimmerTheme.lightColor(f.heat,f.colour),ac=FilamentBulbDimmerTheme.accentLin(f.colour),lum=FilamentBulbDimmerMotion.lum(f.heat)
        func v(_ a:Double,_ b:Double,_ c:Double,_ d:Double)->SIMD4<Float> { SIMD4(Float(a),Float(b),Float(c),Float(d)) }
        let u=[v(l.w,l.h,Double(view.drawableSize.width)/l.w,f.time),v(l.anchorX,l.anchorY,l.R,2.4*l.R),v(p.h.x,p.h.y,f.th,f.tw),v(p.sag,f.heat,lum,30*lum+1.4*f.heat*f.heat),v(lc.x,lc.y,lc.z,f.on),v(ac.x,ac.y,ac.z,0),v(l.cx,l.cy,l.rOut,l.rIn),v(l.card.minX,l.card.minY,l.card.width,l.card.height),v(l.chipL.minX,l.chipR.minX,l.chipL.minY,l.chipL.width),v(l.chipL.height,46*l.sx,l.leg,reduced ? 0:2.4)]
        if let t=pinned {
            if replayedTime != t {
                // Replay the 48-frame air memory deterministically on a 1/60 s grid.
                let count=Int(max(0,t)*60)
                history=(0..<48).map { k in
                    let sample=max(0,Double(count-k)/60)
                    let point=l.pose(FilamentBulbDimmerMotion.frameAt(sample)).fil
                    return v(point.x,point.y,0,0)
                }
                if abs(t-Double(count)/60)>1e-9 { history.removeLast();history.insert(v(p.fil.x,p.fil.y,0,0),at:0) }
                replayedTime=t
            }
        } else {
            replayedTime=nil;history.removeLast();history.insert(v(p.fil.x,p.fil.y,0,0),at:0)
        }
        u.withUnsafeBytes { ub in
            if let cp=command.makeComputeCommandEncoder() {
                cp.setComputePipelineState(compute); cp.setBytes(ub.baseAddress!,length:ub.count,index:0)
                history.withUnsafeBytes { cp.setBytes($0.baseAddress!,length:$0.count,index:1) }
                cp.setBuffer(motes,offset:0,index:2); cp.setBuffer(sparks,offset:0,index:3)
                cp.dispatchThreadgroups(MTLSize(width:41,height:1,depth:1),threadsPerThreadgroup:MTLSize(width:64,height:1,depth:1)); cp.endEncoding()
            }
            func render(_ name:String,_ target:MTLTexture,_ src:MTLTexture? = nil,_ low:MTLTexture? = nil,_ dust:Bool = false) {
                let d=MTLRenderPassDescriptor(); d.colorAttachments[0].texture=target; d.colorAttachments[0].loadAction = .clear; d.colorAttachments[0].storeAction = .store
                guard let e=command.makeRenderCommandEncoder(descriptor:d) else { return }
                e.setRenderPipelineState(pipelines[name]!); e.setFragmentBytes(ub.baseAddress!,length:ub.count,index:0)
                e.setFragmentTexture(src,index:0); e.setFragmentTexture(low,index:1); e.setFragmentSamplerState(sampler,index:0)
                e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
                if dust && !reduced {
                    e.setRenderPipelineState(pipelines["Dust"]!); e.setVertexBytes(ub.baseAddress!,length:ub.count,index:0); e.setVertexBuffer(sparks,offset:0,index:1)
                    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:6,instanceCount:2600)
                }; e.endEncoding()
            }
            render("Scene",hdr!,nil,nil,true)
            for i in 0..<6 { render(i==0 ? "DownFirst":"Down",downs[i],i==0 ? hdr:downs[i-1]) }
            for i in stride(from:4,through:0,by:-1) { render("Up",ups[i],downs[i],i==4 ? downs[5]:ups[i+1]) }
            if let e=command.makeRenderCommandEncoder(descriptor:pass) {
                e.setRenderPipelineState(pipelines["Out"]!); e.setFragmentBytes(ub.baseAddress!,length:ub.count,index:0)
                e.setFragmentTexture(hdr,index:0); e.setFragmentTexture(ups[0],index:1); e.setFragmentSamplerState(sampler,index:0)
                e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3); e.endEncoding()
            }
        }
        command.present(drawable); command.commit()
    }
}
