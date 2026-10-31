import SwiftUI

struct GlassPiggySavingsArt: View {
    typealias M=GlassPiggySavingsMotion
    typealias C=GlassPiggySavingsTheme
    let frame: M.Frame
    let layout: M.Layout
    private let resources=GlassPiggySavingsResources.shared
    private var transform: CGAffineTransform {
        let f=frame, l=layout, fx=l.pigX+170, fy=l.pigY+800*M.scale-20
        let breathe=1+sin(2*Double.pi*2*f.t/M.loop)*0.006
        return CGAffineTransform(translationX:fx,y:fy)
            .translatedBy(x:0,y:-120).rotated(by:f.pig.wob*6*Double.pi/180)
            .translatedBy(x:0,y:120)
            .scaledBy(x:(1+f.pig.sq*0.06)*breathe,y:(1-f.pig.sq*0.08)*breathe)
            .translatedBy(x:-fx,y:-fy)
    }
    var body: some View {
        let l=layout, f=frame, coin=M.coin(f,l)
        let paths=resources.paths(l)
        let sweep=M.seg(f.t,2.6,3.7)
        let pos=M.mix(l.pigX-200,l.pigX+340+300,sweep)
        let strength=sweep>0 && sweep<1 ? 0.22*sin(sweep*Double.pi) : 0
        let breathe=1+sin(2*Double.pi*2*f.t/M.loop)*0.006
        let sx=(1+f.pig.sq*0.06)*breathe
        let sy=(1-f.pig.sq*0.08)*breathe
        let angle=f.pig.wob*6*Double.pi/180
        let pigShader=ShaderLibrary.glassPiggySavingsPig(
            .image(resources.pig),.float2(l.pigX,l.pigY),
            .float2(l.pigX+170,l.pigY+800*M.scale-20),
            .float2(sx,sy),.float(angle),.float(pos),.float(strength))
        ZStack(alignment:.topLeading) {
            C.background.colorEffect(ShaderLibrary.glassPiggySavingsGlow(
                .float2(Float(l.w/2),Float(l.pigY+140)),
                .float(Float(f.t>=2.5 ? exp(-max(0,f.t-2.5)*3) : 0))))
            Canvas(colorMode:.nonLinear) { c,_ in
                c.opacity=0.5
                c.draw(resources.shadow,in:CGRect(x:l.pigX+50-56,y:l.pigY+290-56,width:362,height:138))
            }
            ZStack(alignment:.topLeading) {
                // Sample the original 900×800 texture after the inverse pig transform.
                // Rasterising a resized Image before breathing adds a second filter pass.
                Color.white.colorEffect(pigShader)
                Canvas(colorMode:.nonLinear) { c,_ in
                    if coin.alpha>0 && coin.clipped {
                        c.clip(to:paths.lip)
                        Self.drawCoin(&c,coin)
                    }
                }.transformEffect(transform)
            }
            .frame(width:l.w,height:l.h,alignment:.topLeading)
            Canvas(colorMode:.nonLinear) { c,_ in
                if coin.alpha>0 && !coin.clipped { Self.drawCoin(&c,coin) }
                let since=max(f.t>=2.5 ? f.t-2.5 : -1,f.held.dropAt>0 ? f.now-f.held.dropAt-0.23 : -1)
                if since>=0 && since<1 {
                    for i in 0..<16 {
                        let ii=Double(i), a = -Double.pi/2+(M.hash(ii*3.3)-0.5)*2.4
                        let v=70+M.hash(ii*1.7)*110,d=(1-exp(-since*4))/4
                        var spark=c; spark.opacity=1-M.smooth(M.seg(since,0.35,0.9))
                        spark.translateBy(x:l.slotX+cos(a)*v*d,y:l.slotY+sin(a)*v*d+80*since*since)
                        let r=1.4+M.hash(ii)*1.6; spark.scaleBy(x:r,y:r)
                        spark.fill(C.circle,with:.color(C.color(i%3==0 ? 0xfff4c2 : 0xffd25c)))
                    }
                }
            }
        }
        .frame(width:l.w,height:l.h,alignment:.topLeading)
        .allowsHitTesting(false).accessibilityHidden(true)
    }
    static func drawCoin(_ ctx: inout GraphicsContext, _ coin: M.Coin) {
        guard coin.alpha>0.01 else { return }
        var c=ctx; c.translateBy(x:coin.x,y:coin.y)
        let r=coin.radius,sx=max(0.06,abs(coin.flip))
        let side=sqrt(max(0,1-sx*sx))*r*0.2*(coin.flip<0 ? -1 : 1)
        var edge=c; edge.opacity=coin.alpha
        edge.translateBy(x:side,y:0); edge.scaleBy(x:r*sx,y:r)
        edge.fill(C.circle,with:.color(C.color(0x9a6516)))
        if abs(side)>0.5 {
            var bridge=c; bridge.opacity=coin.alpha
            bridge.translateBy(x:min(0,side),y:-r); bridge.scaleBy(x:abs(side),y:2*r)
            bridge.fill(C.rect,with:.color(C.color(0x9a6516)))
        }
        c.scaleBy(x:sx,y:1)
        c.opacity=coin.alpha
        var disc=c; disc.scaleBy(x:r,y:r)
        disc.fill(C.circle,with:.linearGradient(Gradient(stops:[
            .init(color:C.color(0xfff3b8),location:0),.init(color:C.color(0xf0bf45),location:0.45),.init(color:C.color(0xb57a1d),location:1)]),startPoint:CGPoint(x:-1,y:-1),endPoint:CGPoint(x:0.7,y:1)))
        var face=c; face.scaleBy(x:r*0.82,y:r*0.82)
        let colors=coin.flip<0 ? [C.color(0xf2c457),C.color(0xc98f27)] : [C.color(0xffe48f),C.color(0xdca031)]
        face.fill(C.circle,with:.linearGradient(Gradient(colors:colors),startPoint:CGPoint(x:0.8/0.82,y:0.8/0.82),endPoint:CGPoint(x:-0.8/0.82,y:-0.8/0.82)))
        var ring=c; ring.opacity=0.7*coin.alpha; ring.scaleBy(x:r*0.66,y:r*0.66)
        ring.stroke(C.circle,with:.color(C.color(0xb98222)),lineWidth:max(0.8,r*0.05)/(r*0.66))
        var gl=c
        gl.opacity=0.45*coin.alpha
        gl.scaleBy(x:r/30,y:r/30)
        gl.draw(GlassPiggySavingsResources.shared.coinHighlight,in:CGRect(x:-45,y:-45,width:90,height:90))
    }
}
