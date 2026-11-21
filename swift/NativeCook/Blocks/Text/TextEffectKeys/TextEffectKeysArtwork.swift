import SwiftUI
import UIKit
import CoreText

enum TextEffectKeysArtwork {
    typealias M=TextEffectKeysMotion
    static let labels=["Big","Small","Shake","Nod","Explode","Ripple","Bloom","Jitter"]
    struct Run {var paths:[Path];var xs:[Double];var ws:[Double];var width:Double;var size:Double}
    private static var fonts:[String:CTFont]=[:],runs:[String:Run]=[:]
    static func font(_ size:Double,_ face:String)->CTFont {
        let key="\(face)/\(size)";if let f=fonts[key]{return f};let url=Bundle.main.url(forResource:"TextEffectKeys-Geist-"+face,withExtension:"ttf")!;let f=CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url:url as CFURL)!)!,size,nil,nil);fonts[key]=f;return f
    }
    static func run(_ value:String,_ face:String="Regular",_ size:Double=15)->Run {
        let key="\(value)/\(face)/\(size)";if let r=runs[key]{return r};let f=font(size,face),line=CTLineCreateWithAttributedString(NSAttributedString(string:value,attributes:[.font:f]));let width=CTLineGetTypographicBounds(line,nil,nil,nil)
        var paths=[Path](),xs=[Double]();for item in CTLineGetGlyphRuns(line) as! [CTRun]{let n=CTRunGetGlyphCount(item);var ids=[CGGlyph](repeating:0,count:n),positions=[CGPoint](repeating:.zero,count:n);CTRunGetGlyphs(item,CFRange(),&ids);CTRunGetPositions(item,CFRange(),&positions)
            for i in 0..<n{var flip=CGAffineTransform(scaleX:1,y:-1);paths.append(CTFontCreatePathForGlyph(f,ids[i],nil).map{Path($0.copy(using:&flip)!)} ?? Path());xs.append(positions[i].x)}
        };let ws=xs.indices.map{($0+1<xs.count ? xs[$0+1] : width)-xs[$0]};let r=Run(paths:paths,xs:xs,ws:ws,width:width,size:size);runs[key]=r;return r
    }
    static func geo()->M.Geo {
        let a=run("Whaaaaat"),b=run("Whaaaaat","Medium"),r0=run("guess who got us front row"),r1=run("FRONT. ROW."),old=run("no way","Medium")
        var g=M.makeGeo(a.ws,b.ws,[10.65,10.65,8.16,8.16,8.16,8.16,8.16,9.86],[[16,16+r0.width+24,300],[16,16+r1.width+24,340],[384-old.width-22,384,443]])
        g.fieldX=a.xs.indices.map{69+a.xs[$0]+a.ws[$0]/2};g.bubbleX=b.xs.indices.map{g.bubbleL+11+b.xs[$0]+b.ws[$0]/2};return g
    }
    private static func color(_ h:Int,_ a:Double=1)->Color{Color(.sRGB,red:Double(h>>16&255)/255,green:Double(h>>8&255)/255,blue:Double(h&255)/255,opacity:max(0,min(a,1)))}
    private static func mix(_ a:Int,_ b:Int,_ k:Double)->Color{let rgb=[16,8,0].map{M.mix(Double(a>>$0&255),Double(b>>$0&255),k)/255};return Color(.sRGB,red:rgb[0],green:rgb[1],blue:rgb[2])}
    private static func text(_ c:inout GraphicsContext,_ r:Run,_ x:Double,_ baseline:Double,_ tint:Color){for i in r.paths.indices{var cc=c;cc.translateBy(x:x+r.xs[i],y:baseline);cc.fill(r.paths[i],with:.color(tint))}}
    private static func rect(_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ radius:Double)->Path{Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:max(0,min(radius,min(w,h)/2)))}
    private static func bubble(_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ left:Bool,_ tail:Double=5)->Path {
        let r=max(0,min(16,min(w,h)/2)),t=max(0,min(tail,r));var p=Path();p.move(to:CGPoint(x:x+r,y:y));p.addLine(to:CGPoint(x:x+w-r,y:y));p.addQuadCurve(to:CGPoint(x:x+w,y:y+r),control:CGPoint(x:x+w,y:y));let br=left ? r : t,bl=left ? t : r;p.addLine(to:CGPoint(x:x+w,y:y+h-br));p.addQuadCurve(to:CGPoint(x:x+w-br,y:y+h),control:CGPoint(x:x+w,y:y+h));p.addLine(to:CGPoint(x:x+bl,y:y+h));p.addQuadCurve(to:CGPoint(x:x,y:y+h-bl),control:CGPoint(x:x,y:y+h));p.addLine(to:CGPoint(x:x,y:y+r));p.addQuadCurve(to:CGPoint(x:x+r,y:y),control:CGPoint(x:x,y:y));p.closeSubpath();return p
    }
    static func draw(_ c:inout GraphicsContext,_ f:M.Frame,geo g:M.Geo,oy:Double,navY:Double,conversation:Double,height:Double){
        c.fill(Path(CGRect(x:0,y:0,width:402,height:height)),with:.color(color(0x0F0D0B)))
        var back=Path();back.move(to:CGPoint(x:22.8,y:navY+9.2));back.addLine(to:CGPoint(x:16,y:navY+16));back.addLine(to:CGPoint(x:22.8,y:navY+22.8));c.stroke(back,with:.color(color(0xF4F1EA)),style:StrokeStyle(lineWidth:2,lineCap:.round,lineJoin:.round))
        c.fill(Path(ellipseIn:CGRect(x:185,y:navY,width:32,height:32)),with:.color(color(0x2B2723)));let mono=run("O","Medium",18);text(&c,mono,201-mono.width/2,navY+23.5,color(0xF4F1EA));let name=run("Odile","SemiBold",13);text(&c,name,201-name.width/2,navY+44.615,color(0xF4F1EA))
        var scene=c;scene.translateBy(x:0,y:oy)
        composer(&scene,f)
        var thread=scene;thread.translateBy(x:0,y:505*(1-conversation));thread.scaleBy(x:1,y:conversation);drawThread(&thread,f,g)
        scene.fill(Path(CGRect(x:0,y:554,width:402,height:height+874)),with:.color(color(0x1A1714)));scene.fill(Path(CGRect(x:0,y:554,width:402,height:1)),with:.color(.white.opacity(0.06)))
        for k in 0..<4{let r=run("Aa",["Bold","Italic","Regular","Regular"][k]),x=[60.0,153,248,341][k]-r.width/2,base=584.325;text(&scene,r,x,base,color(0xF4F1EA));if k==2{scene.fill(Path(CGRect(x:x,y:base+2.2,width:r.width,height:1.3)),with:.color(color(0xF4F1EA)))};if k==3{scene.fill(Path(CGRect(x:x-1,y:base-4.6,width:r.width+2,height:1.3)),with:.color(color(0xF4F1EA)))}}
        keys(&scene,f)
        for i in f.hero.indices{let h=f.hero[i],r=run("Whaaaaat",h.font>0.5 ? "Medium" : "Regular"),hg=g.h[i],y=h.y-hg*h.s/2,cy=y<505 ? 505+(y-505)*conversation : y,ys=M.mix(conversation,1,M.seg(y,475,505)),tint=mixColorHero(h)
            let speed=hypot(h.vx,h.vy),sigma=min(speed*0.008,14)*M.seg(h.s,1.5,3)*h.mb+h.blur,angle=atan2(h.vy,h.vx)
            if sigma>0.6{gaussian(&scene,path:r.paths[i],width:r.ws[i],height:hg,h:h,x:h.x,y:cy,ys:ys,tint:tint,sigma:sigma,angle:angle,offset:-sigma*1.1,alpha:0.7);gaussian(&scene,path:r.paths[i],width:r.ws[i],height:hg,h:h,x:h.x,y:cy,ys:ys,tint:tint,sigma:sigma*0.22,angle:angle,offset:0,alpha:1)}else{glyph(&scene,r.paths[i],h.x,cy,h.rot,h.s,ys,-r.ws[i]/2,hg/2,tint,h.w)}
        }
    }
    private static func mixColorHero(_ h:M.Hero)->Color{let a=[0xF4F1EA,0x16190A,0xD8F34A],rgb=[16,8,0].map{shift in M.mix(M.mix(Double(a[0]>>shift&255),Double(a[1]>>shift&255),h.home),Double(a[2]>>shift&255),h.lime)/255};return Color(.sRGB,red:rgb[0],green:rgb[1],blue:rgb[2])}
    private static func glyph(_ c:inout GraphicsContext,_ path:Path,_ x:Double,_ y:Double,_ rotation:Double,_ scale:Double,_ ys:Double,_ pen:Double,_ baseline:Double,_ tint:Color,_ weight:Double=0){var cc=c;cc.translateBy(x:x,y:y);cc.scaleBy(x:1,y:ys);cc.rotate(by:.degrees(rotation));cc.scaleBy(x:scale,y:scale);cc.translateBy(x:pen,y:baseline);cc.fill(path,with:.color(tint));if weight>0.05{cc.stroke(path,with:.color(tint),style:StrokeStyle(lineWidth:weight/max(scale,0.001),lineJoin:.round))}}
    private static func gaussian(_ c:inout GraphicsContext,path:Path,width:Double,height:Double,h:M.Hero,x:Double,y:Double,ys:Double,tint:Color,sigma:Double,angle:Double,offset:Double,alpha:Double){
        if sigma<0.4{glyph(&c,path,x,y,h.rot,h.s,ys,-width/2,height/2,tint,h.w);return}
        c.drawLayer{layer in layer.opacity=alpha;layer.blendMode = .plusLighter;let total=(-12...12).reduce(0.0){$0+exp(-0.5*pow(Double($1)/3,2))};for j in -12...12{let q=Double(j)/3;var tap=layer;tap.opacity=alpha*exp(-0.5*q*q)/total;let d=q*sigma+offset;glyph(&tap,path,x+cos(angle)*d,y+sin(angle)*d,h.rot,h.s,ys,-width/2,height/2,tint,h.w)}}
    }
    private static func drawThread(_ c:inout GraphicsContext,_ f:M.Frame,_ g:M.Geo){let rise=f.rise*44,ink=color(0xF4F1EA),mute=color(0x7E786F);let stamp=run("Today 21:47","Medium",13);text(&c,stamp,201-stamp.width/2,424-rise+4.615,mute)
        for j in 0..<2{let r=run(["guess who got us front row","FRONT. ROW."][j]),y=[300.0,340][j]-rise+f.dips[j];c.fill(bubble(16,y,r.width+24,32,true),with:.color(color(0x1E1B18)));text(&c,r,28,y+21.325,ink)}
        let old=run("no way","Medium"),w=old.width+22,y=443-rise+f.dips[2];c.fill(bubble(384-w,y,w,32,false),with:.color(color(0xD8F34A)));text(&c,old,395-w,y+21.325,color(0x16190A));let seen=run("Seen","SemiBold",13);text(&c,seen,384-seen.width,491.615,mute)
        if f.body>0.002{let k=f.body,cx=M.mix(366.5,g.bubbleL+g.bubbleW/2,k),cy=M.mix(522.5,459,k),w=M.mix(25,g.bubbleW,k)*(1+f.squash)*(1+f.blip),h=M.mix(25,32,k)*(1-f.squash)*(1+f.blip),full=M.seg(f.shell,0.4,1);c.fill(bubble(cx-w/2,cy-h/2,w,h,false,M.mix(12.5,5,M.seg(k,0,1))),with:.color(color(0xD8F34A,M.mix(0.1,1,full))));if full<0.99{c.stroke(bubble(cx-w/2+0.75,cy-h/2+0.75,w-1.5,h-1.5,false),with:.color(color(0xD8F34A,0.9*(1-full))),lineWidth:1.5)}}
    }
    private static func composer(_ c:inout GraphicsContext,_ f:M.Frame){c.fill(Path(ellipseIn:CGRect(x:13,y:506,width:31,height:31)),with:.color(color(0x1E1B18)));var plus=Path();plus.move(to:CGPoint(x:28.5,y:515));plus.addLine(to:CGPoint(x:28.5,y:528));plus.move(to:CGPoint(x:22,y:521.5));plus.addLine(to:CGPoint(x:35,y:521.5));c.stroke(plus,with:.color(color(0x8C857B)),style:StrokeStyle(lineWidth:1.5,lineCap:.round));c.fill(rect(56,505,328,34,17),with:.color(color(0x0F0D0B)));c.stroke(rect(56.5,505.5,327,33,17),with:.color(color(0x2B2723)),lineWidth:1)
        let word=run("Whaaaaat"),p=run("Message");if f.select>0.01{var mask=c;mask.clip(to:Path(CGRect(x:67,y:0,width:(word.width+4)*f.select,height:2000)));mask.fill(rect(67,M.FIELD_BASE-13.5,word.width+4,17.5,3),with:.color(color(0xD8F34A,0.22)))};if f.placeholder>0.01{var mask=c;mask.clip(to:Path(CGRect(x:67,y:0,width:(p.width+4)*f.placeholder,height:2000)));text(&mask,p,69,M.FIELD_BASE,color(0x7E786F))}
        var send=c;send.translateBy(x:366.5,y:522.5);send.scaleBy(x:f.sendS,y:f.sendS);send.fill(Path(ellipseIn:CGRect(x:-12.5,y:-12.5,width:25,height:25)),with:.color(color(0xD8F34A)));var arrow=Path();arrow.move(to:CGPoint(x:0,y:6+f.arrowY));arrow.addLine(to:CGPoint(x:0,y:-5.5+f.arrowY));arrow.move(to:CGPoint(x:-4.6,y:-0.9+f.arrowY));arrow.addLine(to:CGPoint(x:0,y:-5.5+f.arrowY));arrow.addLine(to:CGPoint(x:4.6,y:-0.9+f.arrowY));send.stroke(arrow,with:.color(color(0x16190A)),style:StrokeStyle(lineWidth:2,lineCap:.round,lineJoin:.round))
    }
    private static func keys(_ c:inout GraphicsContext,_ f:M.Frame){
        for k in 0..<8{let x=[15.0,206][k%2],base=[606.0,665,724,783][k/2],d=min(f.depth[k],1.2),y=base+4*d;if d>0.01{c.drawLayer{cc in cc.addFilter(.blur(radius:6));cc.fill(rect(x-2,y-1,185,49,16),with:.color(color(0xD8F34A,0.6*min(d,1))))}};c.drawLayer{cc in cc.addFilter(.blur(radius:6));cc.fill(rect(x,base+4,181,47,14),with:.color(.black.opacity(0.1)))};c.fill(rect(x,base+4,181,43,14),with:.color(color(0x14110F)));c.fill(rect(x,y,181,43,14),with:.linearGradient(Gradient(colors:[mix(0x34302B,0x2A2622,0.45*d),color(0x2A2622)]),startPoint:CGPoint(x:0,y:y),endPoint:CGPoint(x:0,y:y+43)))
            var hi=c;hi.clip(to:rect(x,y,181,43,14));hi.stroke(rect(x,y,181,43,14),with:.linearGradient(Gradient(colors:[.white.opacity(0.14-0.1*min(d,1)),.clear]),startPoint:CGPoint(x:0,y:y),endPoint:CGPoint(x:0,y:y+5)),lineWidth:3);hi.stroke(rect(x,y,181,43,14),with:.linearGradient(Gradient(colors:[.clear,.black.opacity(0.35)]),startPoint:CGPoint(x:0,y:y+39),endPoint:CGPoint(x:0,y:y+43)),lineWidth:2)
            label(&c,f,k,x+90.5,y+21.5)
        }
    }
    private static func label(_ c:inout GraphicsContext,_ f:M.Frame,_ k:Int,_ cx:Double,_ cy:Double){let r=run(labels[k],"Medium",17),n=r.paths.count,ch=17*0.71,still=f.still==1,poses=[[0.5,0.4],[0.57,0.57],[0,0],[0,0],[0.215,1],[0.27,1],[0,0],[0,0]],p=still ? poses[k][0] : f.keyP,amp=still ? poses[k][1] : f.amp*(1-f.active[k]),tint=mix(0xF4F1EA,0xD8F34A,f.active[k])
        for i in 0..<n{let a=M.effectPose(Double(k),p,Double(i),Double(n),17/15.0,false);var dx=a.dx*amp,dy=a.dy*amp,rot=a.rot*amp,sc=a.sc*amp,w=a.w*amp,blur=still ? 0 : a.blur*amp,alpha=k==4 && !still ? M.mix(1,M.explodeAlpha(p),1-f.active[k]) : 1
            if !still && f.kick[k]>=0 && f.kick[k]<1{let pp=0.05+0.9*f.kick[k],b=M.effectPose(Double(k),pp,Double(i),Double(n),17/15.0,false);dx+=b.dx;dy+=b.dy;rot+=b.rot;sc+=b.sc;w+=b.w;blur=max(blur,b.blur);if k==4{alpha=min(alpha,M.explodeAlpha(pp))}}
            if alpha<0.01{continue};let gx=cx-r.width/2+r.xs[i]+r.ws[i]/2,px=cx+(gx-cx)*(1+sc)+dx;var cc=c;cc.opacity=alpha;if blur>0.3{cc.addFilter(.blur(radius:blur))};glyph(&cc,r.paths[i],px,cy+dy,rot,1+sc,1,-r.ws[i]/2,ch/2,tint,w)
        }
    }
}
