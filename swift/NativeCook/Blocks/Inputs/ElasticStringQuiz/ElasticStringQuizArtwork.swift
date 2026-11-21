import SwiftUI
import UIKit
import CoreText

enum ElasticStringQuizArtwork {
    typealias M=ElasticStringQuizMotion
    static let words=["drift","muster","roam","moor","amass","stray","fix","collect"]
    static let answers=["anchor","gather","wander"]
    static let correct=[2.0,1,2,0,1,2,0,1]
    static let cords=[0x9C3A7E,0xA8396B,0xB83A5E], beads=[0xBC4388,0xC0437A,0xBC4269]
    private static var fonts:[String:UIFont]=[:], glyphs:[String:UIImage]=[:]
    private static let halo=UIImage(contentsOfFile:Bundle.main.url(forResource:"ElasticStringQuiz-halo",withExtension:"png")!.path)!
    static func font(_ size:Double,_ weight:String="SemiBold")->UIFont {
        let key="\(size)/\(weight)"; if let f=fonts[key] { return f }
        let url=Bundle.main.url(forResource:"ElasticStringQuiz-Barlow-"+weight,withExtension:"ttf")!
        let f=CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url:url as CFURL)!)!,size,nil,nil) as UIFont; fonts[key]=f; return f
    }
    static func widths(_ text:String,_ size:Double,_ weight:String="SemiBold")->[Double] {
        let face = font(size, weight) as CTFont
        let characters = Array(text.utf16)
        let count = characters.count
        var ids = [CGGlyph](repeating: 0, count: count)
        var advances = [CGSize](repeating: .zero, count: count)
        CTFontGetGlyphsForCharacters(face, characters, &ids, count)
        CTFontGetAdvancesForGlyphs(face, .horizontal, ids, &advances, count)
        return advances.map { Double($0.width) }
    }
    static func script()->M.Script {
        var centers=[Double]()
        for word in words { let ws=widths(word,52);var x=201-ws.reduce(0,+)/2; for k in 0..<10 { centers.append(k<ws.count ? x+ws[k]/2 : 201);if k<ws.count { x+=ws[k] } } }
        return M.Script(n:8,answers:correct,lens:words.map {Double($0.count)},cx:centers,cA:2,cB:1,cW:0,score:6412,top:0,arm:44)
    }
    private static func color(_ hex:Int,_ alpha:Double=1)->Color { Color(.sRGB,red:Double(hex>>16&255)/255,green:Double(hex>>8&255)/255,blue:Double(hex&255)/255,opacity:M.clamp01(alpha)) }
    private static func blend(_ a:Int,_ b:Int,_ k:Double,_ alpha:Double=1)->Color {
        let values=[16,8,0].map { M.mix(Double(a>>$0&255),Double(b>>$0&255),k)/255 }
        return Color(.sRGB,red:values[0],green:values[1],blue:values[2],opacity:M.clamp01(alpha))
    }
    private static func image(_ text:String,_ size:Double,_ weight:String)->UIImage {
        let key="\(text)/\(size)/\(weight)";if let v=glyphs[key] { return v }
        let f=font(size,weight),w=(text as NSString).size(withAttributes:[.font:f]).width
        let image=UIGraphicsImageRenderer(size:CGSize(width:ceil(w)+4,height:110)).image { _ in (text as NSString).draw(at:CGPoint(x:0,y:80-f.ascender),withAttributes:[.font:f,.foregroundColor:UIColor.white]) };glyphs[key]=image;return image
    }
    private static func text(_ c:inout GraphicsContext,_ value:String,_ x:Double,_ baseline:Double,_ size:Double,_ tint:Color,_ weight:String="SemiBold",center:Bool=false,tracking:Double=0) {
        var x=x;let ws=widths(value,size,weight),total=ws.reduce(0,+)+tracking*Double(max(0,ws.count-1));if center { x-=total/2 }
        for (i,ch) in value.enumerated() { let img=image(String(ch),size,weight);var cc=c;cc.addFilter(.colorMultiply(tint));cc.draw(Image(uiImage:img),in:CGRect(x:x,y:baseline-80,width:img.size.width,height:img.size.height));x+=ws[i]+tracking }
    }
    struct Cord {
        var a=Path(), b=Path(), pulses=[Path](repeating:Path(),count:3)
        var x=0.0,y=0.0,px=0.0,py=0.0,pulse=0.0
        var aColor=Color.clear,bColor=Color.clear,aBody=Color.clear,bBody=Color.clear,bloom=0.0
    }
    private static func sub(_ p:M.Pts,_ a:Double,_ b:Double)->Path {
        guard b-a>=0.05 else { return Path() };let first=M.pointAt(p,a),last=M.pointAt(p,b);var path=Path();path.move(to:CGPoint(x:first.x,y:first.y))
        for k in p.x.indices where p.s[k]>a && p.s[k]<b { path.addLine(to:CGPoint(x:p.x[k],y:p.y[k])) };path.addLine(to:CGPoint(x:last.x,y:last.y));return path
    }
    static func geo(_ f:M.Frame,_ i:Int)->Cord {
        let base=cords[i],line=f.cordA[i];var g=Cord();g.x=f.bx[i];g.y=f.by[i]
        if f.ropeTau[i]>=0 {
            let r=M.ropeAt(M.ROPE[i],f.ropeTau[i],f.e.snapS[i],f.e.snapB[i]);var path=Path();path.move(to:CGPoint(x:r.x[0],y:r.y[0]))
            for k in 1..<r.x.count-1 { path.addQuadCurve(to:CGPoint(x:(r.x[k]+r.x[k+1])/2,y:(r.y[k]+r.y[k+1])/2),control:CGPoint(x:r.x[k],y:r.y[k])) };path.addLine(to:CGPoint(x:r.x.last!,y:r.y.last!))
            g.b=path;g.bColor=blend(base,0xFFF8FC,0.35*(1-M.seg(f.ropeTau[i],0,0.3)),0.8*line);g.bBody=color(base,0.2*line);g.x=r.x.last!;g.y=r.y.last!;return g
        }
        let p=M.cordPoints(Double(i),f.hx,f.hy,f.by[i],f.bend[i],f.e.pl,f.t,f.waveFrom),length=p.total*f.draw[i];var split=0.0
        if f.pulseFrac[i]>=0 { split=p.total*(1-f.pulseFrac[i]);M.addBump(p,split,f.pulseAmp[i]*M.seg(split,0,30));let center=M.pointAt(p,split);g.px=center.x;g.py=center.y;g.pulse=f.pulseAmp[i]*line;g.pulses=[sub(p,split-18,split+18),sub(p,split-12,split+12),sub(p,split-6,split+6)] }
        let hot=f.heatHot[i],heat=f.heat[i];g.bloom=0.35*max(hot,heat)*line;g.a=split>0 ? sub(p,0,min(split,length)) : Path();g.b=sub(p,min(split,length),length)
        g.aColor=blend(base,0xFFF8FC,hot,(0.8+0.2*hot)*line);g.bColor=blend(base,0xFFF8FC,heat,(0.8+0.2*heat)*line);g.aBody=blend(base,0xFFF8FC,hot,0.2*line);g.bBody=blend(base,0xFFF8FC,heat,0.2*line)
        if f.tip==1 {let end=M.pointAt(p,length);g.x=end.x;g.y=end.y};return g
    }
    private static func halo(_ c:inout GraphicsContext,_ x:Double,_ y:Double,_ r:Double,_ alpha:Double) {
        guard r>0 && alpha>0 else { return };var cc=c;cc.opacity=alpha;cc.addFilter(.colorMultiply(color(0xFD5082)));cc.draw(Image(uiImage:halo),in:CGRect(x:x-r,y:y-r,width:2*r,height:2*r))
    }
    static func draw(_ c:inout GraphicsContext,_ f:M.Frame,top:Double,bottom:Double,hudY:Double,pixels:Double,matched:[[String]]) {
        let u:[Float]=[0,Float(f.fx),Float(f.fy),Float(f.gain),Float(f.cool),Float(pixels),Float(top),Float(bottom)]
        c.fill(Path(CGRect(x:0,y:top,width:402,height:bottom-top)),with:.shader(ShaderLibrary.elasticStringQuizField(.floatArray(u))))
        let gs=(0..<3).map {geo(f,$0)}
        for i in 0..<3 {
            let g=gs[i],style=StrokeStyle(lineWidth:f.bodyW[i],lineCap:.round,lineJoin:.round)
            c.stroke(g.a,with:.color(g.aBody),style:style);c.stroke(g.b,with:.color(g.bBody),style:style)
            if g.bloom>0.001 {c.drawLayer { cc in cc.blendMode = .plusLighter;cc.opacity=g.bloom;cc.addFilter(.blur(radius:4));cc.stroke(g.a,with:.color(g.aColor),style:StrokeStyle(lineWidth:14,lineCap:.round,lineJoin:.round));cc.stroke(g.b,with:.color(g.bColor),style:StrokeStyle(lineWidth:14,lineCap:.round,lineJoin:.round)) }}
            c.stroke(g.a,with:.color(g.aColor),style:StrokeStyle(lineWidth:f.width[i],lineCap:.round,lineJoin:.round));c.stroke(g.b,with:.color(g.bColor),style:StrokeStyle(lineWidth:f.width[i],lineCap:.round,lineJoin:.round))
            halo(&c,g.px,g.py,18,0.6*g.pulse)
            for j in 0..<3 {c.stroke(g.pulses[j],with:.color(.white.opacity([0.3,0.6,1][j]*g.pulse)),style:StrokeStyle(lineWidth:Double(j+2),lineCap:.round))}
        }
        for i in 0..<3 {
            let g=gs[i],h=f.halo[i],sc=f.beadSc[i],r=10*sc;var cc=c;cc.opacity=f.beadA[i]
            halo(&cc,g.x,g.y,M.mix(10,30,h)*sc,0.55*h)
            if r>0 {cc.fill(Path(ellipseIn:CGRect(x:g.x-r,y:g.y-r,width:2*r,height:2*r)),with:.radialGradient(Gradient(stops:[.init(color:blend(beads[i],0xFD5082,h),location:0),.init(color:blend(beads[i],0xFD5082,h),location:0.74),.init(color:blend(beads[i],0xFD5082,h,0),location:1)]),center:CGPoint(x:g.x,y:g.y),startRadius:0,endRadius:r))}
            let core=5*h*sc;cc.fill(Path(ellipseIn:CGRect(x:g.x-core,y:g.y-core,width:2*core,height:2*core)),with:.color(.white))
            let riding=f.mode==6,dx=(f.ropeTau[i]>=0 || riding) ? g.x-M.G.bx[i] : f.bend[i]*0.85
            let dy=riding ? g.y-640+52*(f.labelSc[i]-1) : f.ropeTau[i]>=0 ? g.y-640 : f.labelDy[i]
            var label=c;label.opacity=f.labelA[i];label.translateBy(x:M.G.bx[i]+dx,y:692+dy);label.scaleBy(x:max(f.labelSc[i],0.001),y:max(f.labelSc[i],0.001));label.translateBy(x:-M.G.bx[i],y:-692)
            text(&label,answers[i],M.G.bx[i],700,24,color(0xFFF6FA),center:true,tracking:0.4)
        }
        let word=words[Int(f.w)%words.count],ws=widths(word,52);var x=201-ws.reduce(0,+)/2
        var letters=c;if f.roll==1 {letters.clip(to:Path(CGRect(x:0,y:top-10,width:402,height:283-top)))}
        for (k,ch) in word.enumerated() {let cx=x+ws[k]/2;var letter=letters;letter.opacity=f.la[k];letter.translateBy(x:cx+f.lx[k],y:239+f.ly[k]);letter.scaleBy(x:max(f.ls[k],0.01),y:max(f.ls[k],0.01));letter.translateBy(x:-cx,y:-239);text(&letter,String(ch),x,257,52,color(0xFFF6FA));x+=ws[k]}
        let center=CGPoint(x:f.hx,y:f.hy);var hub=c;hub.opacity=f.hubA
        if f.shockR>0 {hub.stroke(Path(ellipseIn:CGRect(x:f.hx-f.shockR,y:f.hy-f.shockR,width:2*f.shockR,height:2*f.shockR)),with:.color(.white.opacity(f.shockA)),lineWidth:2)}
        var arcs=Path();let r=f.ballR+8;arcs.addArc(center:center,radius:r,startAngle:.radians(-150*Double.pi/180+f.arcRot),endAngle:.radians((-150+110*f.arcSweep)*Double.pi/180+f.arcRot),clockwise:false);arcs.addArc(center:center,radius:r,startAngle:.radians(40*Double.pi/180-f.arcRot),endAngle:.radians((40+70*f.arcSweep)*Double.pi/180-f.arcRot),clockwise:false);hub.stroke(arcs,with:.color(.white.opacity(0.7*f.arcA)),style:StrokeStyle(lineWidth:2,lineCap:.round));hub.fill(Path(ellipseIn:CGRect(x:f.hx-f.ballR,y:f.hy-f.ballR,width:2*f.ballR,height:2*f.ballR)),with:.color(.white))
        hud(&c,f,hudY)
        if f.recapA>0.001 {recap(&c,f.recapA,hudY,matched)}
    }
    private static func hud(_ c:inout GraphicsContext,_ f:M.Frame,_ y:Double) {
        let tint=color(0xE8476F);var cc=c;cc.opacity=f.scoreA
        var tally=Path(roundedRect:CGRect(x:16.875,y:y-6.125,width:12.25,height:12.25),cornerRadius:2.625)
        for x in [19.85,23,26.15] {tally.move(to:CGPoint(x:x,y:y-2.45));tally.addLine(to:CGPoint(x:x,y:y+2.45))};cc.stroke(tally,with:.color(tint),style:StrokeStyle(lineWidth:2.03,lineCap:.round,lineJoin:.round))
        let slot=0.544*15
        for k in 0..<4 {let place=Double(3-k),p=pow(10,place),from=floor(min(f.sF,9999)/p).truncatingRemainder(dividingBy:10),to=floor(min(f.sTo,9999)/p).truncatingRemainder(dividingBy:10),delta=(to-from+10).truncatingRemainder(dividingBy:10);let q=f.sDur>0 ? M.easeOut(M.seg(f.t,f.sT0+0.05*place,f.sT0+f.sDur)) : f.t>=f.sT0 ? 1.0 : 0;let pos=from+delta*q,d0=Int(floor(pos))%10,frac=pos-floor(pos),x=36+Double(k)*slot
            var digit=cc;digit.clip(to:Path(CGRect(x:x-1,y:y-10,width:slot+2,height:21)));text(&digit,String(d0),x+slot/2,y+5.3-frac*18,15,tint,"Bold",center:true);if frac>0.001 {text(&digit,String((d0+1)%10),x+slot/2,y+5.3+(1-frac)*18,15,tint,"Bold",center:true)}
        }
        cc=c;cc.opacity=f.hudA;let w=widths(":41",15,"Bold").reduce(0,+);text(&cc,":41",386-w,y+5.3,15,tint,"Bold")
    }
    private static func recap(_ c:inout GraphicsContext,_ alpha:Double,_ hudY:Double,_ matched:[[String]]) {
        var cc=c;cc.opacity=alpha;text(&cc,"WORDS MATCHED",201,207,20,color(0xFFF6FA),"Bold",center:true,tracking:0.8)
        for i in 0..<3 {let x=[99.0,201,302][i],y=[304.0,492,304][i],ly=[360.0,549,360][i];cc.fill(Path(ellipseIn:CGRect(x:x-7,y:y-7,width:14,height:14)),with:.color(color(beads[i])));text(&cc,answers[i],x,ly+7,22,color(0xFFF6FA),"Medium",center:true);for (j,word) in matched[i].enumerated(){text(&cc,word,x,ly+49+Double(j)*21,13,color(0x8E6F86),"Medium",center:true)}}
        text(&cc,"TAP TO CONTINUE",201,767,14,color(0x7A5670),center:true,tracking:0.84);let w=widths("PAUSED",13,"Bold").reduce(0,+);text(&cc,"PAUSED",386-w,hudY+23,13,color(0xE8476F,0.8),"Bold")
    }
}
