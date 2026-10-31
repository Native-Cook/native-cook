import SwiftUI

struct CoinLightLandingArt {
    typealias R = CoinLightLandingResources
    typealias M = CoinLightLandingMotion
    // Skia interpolates unpremultiplied gradient stops. Dense stops preserve that interpolation
    // in Canvas, whose two-stop interpolation premultiplies the endpoint colors.
    static let haloGradient = Gradient(stops:(0...100).map { index in
        let p=Double(index)/100
        let k=p<=0.45 ? p/0.45 : (p-0.45)/0.55
        let g=p<=0.45 ? M.mix(190,150,k) : 150
        let b=p<=0.45 ? M.mix(90,50,k) : 50
        let a=p<=0.45 ? M.mix(0.55,0.12,k) : M.mix(0.12,0,k)
        return Gradient.Stop(color:Color(.sRGB,red:1,green:g/255,blue:b/255,opacity:a),location:p)
    })
    let size: CGSize
    let top: Double
    let reduced: Bool
    let overviewPressed: Bool
    let bounds: Path
    let rims: [(Double,Double,Path)]
    let glowShape: Path
    static let coinFunction=ShaderFunction(library:.default,name:"coinLightLandingCoin")
    static let primaryShape=Path(roundedRect:CGRect(x:-82,y:-22,width:164,height:44),cornerRadius:22)
    init(size: CGSize,top: Double,reduced: Bool,overviewPressed: Bool) {
        self.size=size; self.top=top; self.reduced=reduced; self.overviewPressed=overviewPressed
        bounds=Path(CGRect(origin:.zero,size:size))
        rims=[(104.0,164.0),(284.0,176.0)].map { x,w in
            (x,w,Path(roundedRect:CGRect(x:x-w/2,y:top+332,width:w,height:44),cornerRadius:22))
        }
        glowShape=Path(roundedRect:CGRect(x:22,y:top+332,width:164,height:44),cornerRadius:22)
    }
    func draw(_ c: GraphicsContext,_ f: M.Frame,_ page: Image) {
        c.draw(page,in:CGRect(origin:.zero,size:size))
        var payout=c
        payout.opacity=1-f.dip
        payout.draw(R.shared.payout(Int(f.pay.rounded()),size.width),in:CGRect(x:0,y:top+276+f.dip*4,width:size.width,height:42))
        var button=c
        button.translateBy(x:104,y:top+354)
        button.scaleBy(x:1+0.04*f.button,y:1+0.04*f.button)
        let fill=Color(.sRGB,red:1,green:(255-50*f.button).rounded()/255,blue:(255-150*f.button).rounded()/255)
        button.fill(Self.primaryShape,with:.color(fill))
        button.draw(R.shared.primary,in:CGRect(x:-82,y:-22,width:164,height:44))
        var secondary=c
        secondary.translateBy(x:284,y:top+354)
        secondary.scaleBy(x:overviewPressed ? 0.97 : 1,y:overviewPressed ? 0.97 : 1)
        secondary.draw(R.shared.secondary,in:CGRect(x:-88,y:-22,width:176,height:44))
        let lx=M.mix(104,f.x,f.a), ly=M.mix(top+354,f.y,f.a)
        let power=reduced ? 0 : max(f.a,f.button)
        func radial(_ stops: [(Int,Double,Double)],_ x: Double,_ y: Double,_ radius: Double) -> GraphicsContext.Shading {
            .radialGradient(Gradient(stops:stops.map { .init(color:R.color($0.0,$0.1),location:$0.2) }),center:CGPoint(x:x,y:y),startRadius:0,endRadius:radius)
        }
        if !reduced { c.fill(bounds,with:radial([(0x050506,0,0),(0x050506,0.2,0.55),(0x050506,0.48,1)],lx,ly,M.mix(220,360,power))) }
        c.fill(bounds,with:radial([(0xffaa46,0.22*power,0),(0xffaa46,0,1)],lx,ly,190))
        for (x,w,path) in rims {
            let y=top+354, dx=lx-x,dy=ly-y,d=max(1,hypot(dx,dy)),k=M.clamp(1-d/360)*power
            if k>=0.02 {
                let gradient=Gradient(stops:[.init(color:R.color(0xffcd78,k),location:0),.init(color:R.color(0xffcd78,0),location:0.7)])
                c.stroke(path,with:.linearGradient(gradient,startPoint:CGPoint(x:x+dx/d*w/2,y:y+dy/d*22),endPoint:CGPoint(x:x-dx/d*w/2,y:y-dy/d*22)),lineWidth:1.6)
            }
        }
        if f.button>0.01 {
            var glow=c
            // Skia MakeBlur's argument is sigma; CoreGraphics/SwiftUI blur uses the same sigma.
            glow.addFilter(.blur(radius:18))
            glow.fill(glowShape,with:.color(R.color(0xffc24a,0.55*f.button)))
        }
        if f.a>0.01 {
            var halo=c; halo.opacity=f.a
            halo.fill(bounds,with:.radialGradient(Self.haloGradient,center:CGPoint(x:f.x,y:f.y),startRadius:0,endRadius:182*f.s))
            var coin=c; coin.opacity=f.a
            let coinBounds=Path(CGRect(x:f.x-100*f.s,y:f.y-100*f.s,width:200*f.s,height:200*f.s))
            coin.fill(coinBounds,with:.shader(Shader(function:Self.coinFunction,arguments:[.float2(Float(f.x),Float(f.y)),.float(Float(70*f.s)),.float(Float(f.yaw+f.tx)),.float(0.55),.float(Float(f.button))])))
        }
    }
}
