import UIKit

@MainActor
final class PrismaticGameProfileBadge: UIView {
    typealias G=PrismaticGameProfileGeometry
    let index:Int
    private let actor=UIView(),materialGroup=UIView(),plate=UIImageView()
    private let artwork:PrismaticGameProfileMetal
    private let caustic:PrismaticGameProfileMetal
    private let fallback=UIImageView()
    private static let names=["crown","shield","diamond"]
    private static var plates=[Int:UIImage]()
    init(index:Int) {
        self.index=index;artwork=PrismaticGameProfileMetal(card:false,asset:Self.names[index]);caustic=PrismaticGameProfileMetal(card:false,asset:Self.names[index])
        super.init(frame:CGRect(x:0,y:0,width:393,height:852))
        isUserInteractionEnabled=false;isAccessibilityElement=false
        layer.anchorPoint=CGPoint(x:0.5,y:380/852);layer.position=CGPoint(x:196.5,y:380)
        actor.bounds=CGRect(x:0,y:0,width:158,height:158);addSubview(actor)
        plate.frame=actor.bounds;plate.image=Self.plate(index);actor.addSubview(plate)
        materialGroup.frame=actor.bounds;actor.addSubview(materialGroup)
        caustic.frame=actor.bounds;materialGroup.addSubview(caustic)
        artwork.frame=CGRect(x:22,y:16,width:114,height:126);materialGroup.addSubview(artwork)
        fallback.frame=artwork.frame;fallback.image=PrismaticGameProfileAssets.image(Self.names[index]);fallback.contentMode = .scaleAspectFit;fallback.isHidden=artwork.available;materialGroup.addSubview(fallback)
    }
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    private static func plate(_ index:Int)->UIImage {
        if let cached=plates[index]{return cached}
        let palette:[[UInt32]]=[[0xB766F7,0xC780FA],[0xFFCA4C,0xFFD866],[0xD5E43B,0xE3EC67]]
        let format=UIGraphicsImageRendererFormat();format.scale=3
        let image=UIGraphicsImageRenderer(size:CGSize(width:158,height:158),format:format).image{r in
            for n in 0..<52 {let a=Double(n)*2*Double.pi/52,b=Double(n+1)*2*Double.pi/52
                let path=UIBezierPath();path.move(to:CGPoint(x:79,y:79));path.addArc(withCenter:CGPoint(x:79,y:79),radius:79,startAngle:a,endAngle:b,clockwise:true);path.close();PrismaticGameProfileAssets.color(palette[index][n%2]).setFill();path.fill()
            }
        };plates[index]=image;return image
    }
    func apply(_ raw:G.Frame,_ p:Double,_ selected:Int,_ origin:G.Origin,_ content:Double,_ tiltX:Double,_ tiltY:Double,_ g:G.Source,_ identity:G.Identity) {
        let own=index==selected,progress=own ? p:0,phase=G.sealChannels(progress,raw.reduced),source=G.sourceBadge(raw.rail,index,g)
        layer.zPosition=own ? 1002:0
        layer.transform=G.matrix(own ? progress:0,tiltX,tiltY,raw.reduced)
        alpha=raw.reduced && own && progress>0 ? content:1
        let x=phase.y>0 ? origin.x:source.x,y=phase.y>0 ? origin.y:source.y,size=phase.y>0 ? origin.size:source.size
        actor.center=CGPoint(x:G.mix(x,identity.sealX,phase.x),y:G.mix(y,587,phase.y)-G.sealLift(progress,raw.reduced))
        let s=G.mix(size/158,identity.sealScale,phase.scale);actor.transform=CGAffineTransform(scaleX:s,y:s)
        let d=min(1,abs(Double(index)-raw.rail));actor.alpha=1-0.3*d*(1-phase.y)
        let t=G.clamp((p-0.015)/0.155);plate.alpha=own ? 1-t*t*(3-2*t):1
        let ratio=index==1 ? 728.0/768:index==0 ? 512.0/410:512.0/408
        let fit=min(132-52*d,(140-68*d)*ratio)*g.rail.size/164
        let factor=fit/(114*source.size/158),materialScale=1+(factor-1)*(1-phase.scale)
        materialGroup.transform=CGAffineTransform(scaleX:materialScale,y:materialScale)
        var u=PrismaticGameProfileMetal.Uniforms();u.canvas=SIMD4(114,126,0,0)
        let png=PrismaticGameProfileAssets.image(Self.names[index])?.size ?? CGSize(width:114,height:126)
        let contain=min(114/png.width,126/png.height),w=png.width*contain,h=png.height*contain
        u.imageFit=SIMD4(Float((114-w)/2),Float((126-h)/2),Float(w),Float(h))
        u.optics=SIMD4(Float(raw.light.material==index ? raw.light.phase:0),Float(raw.light.material==index && !raw.reduced ? raw.light.power:0),Float(index),0)
        let nativeDiamond=index==2 && !raw.reduced && raw.light.material==2 && raw.light.power>0
        if nativeDiamond {artwork.frame=CGRect(x:22,y:22,width:114,height:114);u.canvas=SIMD4(114,114,0,0);u.optics.w=2}
        else {artwork.frame=CGRect(x:22,y:16,width:114,height:126)}
        caustic.isHidden=nativeDiamond
        artwork.render(u)
        if index != 1 && !nativeDiamond {u.canvas=SIMD4(158,158,0,0);u.optics.z=index==2 ? 1:0;u.optics.w=1;caustic.render(u)}
    }
}
