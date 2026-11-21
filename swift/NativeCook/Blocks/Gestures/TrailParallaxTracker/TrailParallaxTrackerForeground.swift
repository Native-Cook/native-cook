import SwiftUI
import UIKit

struct TrailParallaxTrackerForeground:UIViewRepresentable {
    let frame:TrailParallaxTrackerFrame,dy:Double,scale:Double,a:TrailParallaxTrackerPalette,b:TrailParallaxTrackerPalette,split:(a:Int,b:Int,x:Double),reduced:Bool
    var uiOnly=false
    func makeUIView(context:Context)->TrailParallaxTrackerForegroundView {TrailParallaxTrackerForegroundView()}
    func updateUIView(_ view:TrailParallaxTrackerForegroundView,context:Context){view.frameState=frame;view.dy=dy;view.sceneScale=scale;view.pa=a;view.pb=b;view.split=split;view.reduced=reduced;view.uiOnly=uiOnly;view.setNeedsDisplay()}
}

final class TrailParallaxTrackerForegroundView:UIView {
    var frameState=TrailParallaxTrackerFrame(),dy=0.0,sceneScale=1.0,reduced=false,uiOnly=false
    var pa=TrailParallaxTrackerLight.palette(0,row:0),pb=TrailParallaxTrackerLight.palette(0,row:0),split=(a:0,b:0,x:9999.0)
    private static var sprites:[Int:UIImage]=[:]
    override init(frame:CGRect){super.init(frame:frame);isOpaque=false;backgroundColor = .clear;isUserInteractionEnabled=false}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    private func rgb(_ h:UInt32,_ a:Double=1)->UIColor {UIColor(red:Double((h>>16)&255)/255,green:Double((h>>8)&255)/255,blue:Double(h&255)/255,alpha:a)}
    private func tone(_ rgb:[Double],_ a:Double=1)->UIColor {UIColor(red:rgb[0],green:rgb[1],blue:rgb[2],alpha:a)}
    private func polygon(_ points:[[Double]])->CGPath {let p=CGMutablePath();p.move(to:CGPoint(x:points[0][0],y:points[0][1]));for q in points.dropFirst(){p.addLine(to:CGPoint(x:q[0],y:q[1]))};p.closeSubpath();return p}
    private func paint(_ c:CGContext,_ p:CGPath,_ color:UInt32){c.setFillColor(rgb(color).cgColor);c.addPath(p);c.fillPath()}
    private func text(_ value:String,_ x:Double,_ baseline:Double,_ face:String,_ size:Double,_ color:UIColor,center:Bool=false){let font=TrailParallaxTrackerAssets.font(face,size),a:[NSAttributedString.Key:Any]=[.font:font,.foregroundColor:color];let w=value.size(withAttributes:a).width;value.draw(at:CGPoint(x:center ? x-w/2:x,y:baseline-font.ascender),withAttributes:a)}
    override func draw(_ rect:CGRect){
        guard let c=UIGraphicsGetCurrentContext()else{return};let f=frameState,M=TrailParallaxTrackerMath.self,base=500+dy,scene=530+dy,h=874+dy,camera=f.d*24,s=sceneScale
        c.saveGState();c.scaleBy(x:s,y:s)
        let foot=M.ground(200+camera,base)+1
        if !uiOnly {
        let rim=CGMutablePath();for (index,x) in stride(from:-12.0,through:414.0,by:6).enumerated(){let p=CGPoint(x:x,y:M.ground(x+camera,base));if index==0{rim.move(to:p)}else{rim.addLine(to:p)}}
        let ground=CGMutablePath();ground.addPath(rim);ground.addLine(to:CGPoint(x:414,y:h+10));ground.addLine(to:CGPoint(x:-12,y:h+10));ground.closeSubpath();c.setFillColor(rgb(0x060606).cgColor);c.addPath(ground);c.fillPath()
        for side in 0..<(split.a==split.b ? 1:2){let x=side==0 ? -20:max(-20,min(split.x,460)),width=side==0 ? max(0,min(split.x,460)+20):480-x,color=tone((side==0 ? pa:pb).sky[2]);c.saveGState();c.clip(to:CGRect(x:x,y:0,width:width,height:h));c.saveGState();c.addPath(ground);c.clip();c.setStrokeColor(color.withAlphaComponent(0.24).cgColor);c.setLineWidth(28);c.setShadow(offset:.zero,blur:5,color:color.withAlphaComponent(0.24).cgColor);c.addPath(rim);c.strokePath();c.restoreGState();c.setStrokeColor(color.withAlphaComponent(0.75).cgColor);c.setLineWidth(2.5);c.addPath(rim);c.strokePath();c.restoreGState()}
        let foot=M.ground(200+camera,base)+1,slope=atan2(M.ground(206+camera,base)-M.ground(194+camera,base),12),angle=(slope*180/Double.pi*0.6+f.lean*f.face).tptClamp(-14,14)*Double.pi/180
        c.setFillColor(UIColor.black.withAlphaComponent(0.35).cgColor);c.fillEllipse(in:CGRect(x:186,y:foot-2,width:28,height:4))
        let pose=Int(f.pose).tptClampInt(0,18)
        if Self.sprites[pose]==nil,let cg=TrailParallaxTrackerAssets.image("hiker-sheet").cgImage,let cell=cg.cropping(to:CGRect(x:pose*204,y:0,width:204,height:288)){Self.sprites[pose]=UIImage(cgImage:cell)}
        c.saveGState();c.translateBy(x:200,y:foot-4*f.hop);c.rotate(by:angle);c.scaleBy(x:f.face,y:f.squash);Self.sprites[pose]?.draw(in:CGRect(x:-105.0/204*(93*204/288.0),y:-280.0/288*93,width:93*204/288.0,height:93));c.restoreGState()
        if f.skidAge>=0 && f.skidAge<0.32 {let k=f.skidAge/0.32;for(i,r)in[5.0,8,6].enumerated(){let radius=r*sin(Double.pi*min(1,k*1.15+Double(i)*0.05));c.setFillColor(tone(pa.sky[2],0.6).cgColor);c.fillEllipse(in:CGRect(x:200-f.face*(8+Double(i)*9)-radius,y:foot-3-10*k-Double(i)*2-radius,width:radius*2,height:radius*2))}}
        let occClip=CGMutablePath();occClip.move(to:CGPoint(x:-12,y:-20));for x in stride(from:-12.0,through:414.0,by:6){occClip.addLine(to:CGPoint(x:x,y:M.ground(x+camera,base)+30))};occClip.addLine(to:CGPoint(x:414,y:-20));occClip.closeSubpath();c.saveGState();c.addPath(occClip);c.clip()
        let rate=reduced ? 1.0:1.7
        let fin=201-150-rate*(camera-624),trunk=201-165-rate*(camera-1408.8),grove=201-123.75-rate*(camera-1759),wall=201-(340.0/3)*1.6-rate*(camera-2900),pines=201-(564.0/3)*1.3-rate*(camera-3350)
        if fin<420 && fin+300 > -80 {c.saveGState();c.translateBy(x:fin,y:dy+90);drawRock(c);c.restoreGState()}
        if grove<420 && grove+247.5 > -80 {c.saveGState();c.translateBy(x:grove,y:dy+90);c.scaleBy(x:0.75,y:1);drawTrunk(c,grove:true);c.restoreGState()}
        if wall<420 && wall+623.3 > -80 {TrailParallaxTrackerAssets.image("occluder-black-trunk").draw(in:CGRect(x:wall,y:scene+26-440*1.6,width:700.0/3*1.6,height:440*1.6));TrailParallaxTrackerAssets.image("occluder-black-pine-cluster").draw(in:CGRect(x:wall+700.0/3*1.6-180,y:scene+26-440*1.3,width:1000.0/3*1.3,height:440*1.3))}
        if pines<420 && pines+433.3 > -80 {TrailParallaxTrackerAssets.image("occluder-black-pine-cluster").draw(in:CGRect(x:pines,y:scene+26-440*1.3,width:1000.0/3*1.3,height:440*1.3))}
        if trunk<420 && trunk+330 > -80 {c.saveGState();c.translateBy(x:trunk,y:dy+90);drawTrunk(c,grove:false);c.restoreGState()};c.restoreGState()
        }
        if uiOnly {
        drawOdometers(c,f:f,dy:dy,reduced:reduced)
        let bs=max(f.bubS,0)
        if bs>0.01 {let float=1.5*sin(2*Double.pi*3*f.time/12),headY=foot-93-16+float,x=M.mix(200,200-f.face*14,f.bubT),y=M.mix(headY,foot-45,f.bubT);c.saveGState();c.translateBy(x:x,y:y-25);c.scaleBy(x:bs,y:bs);c.setFillColor(UIColor.white.cgColor);c.setShadow(offset:CGSize(width:0,height:4),blur:10,color:UIColor.black.withAlphaComponent(0.08).cgColor);c.addPath(UIBezierPath(roundedRect:CGRect(x:-42,y:-25,width:84,height:50),cornerRadius:12).cgPath);c.fillPath();c.setShadow(offset:.zero,blur:0,color:nil);text("Today",0,-6,"Inter-SemiBold",13,rgb(0x6B6B6B),center:true);text("0.6 mi",0,14,"Outfit-Bold",17,rgb(0x0A0A0A),center:true);c.restoreGState()}
        drawCard(c,f:f,dy:dy,reduced:reduced)
        for i in 0..<3 {c.saveGState();c.translateBy(x:[65.0,189,313][i],y:801+dy);drawIcon(c,i:i);c.restoreGState()}
        }
        c.restoreGState()
    }
    private func drawRock(_ c:CGContext){
        let left=[[-46.0,620],[-34,430],[-6,372],[0,330],[22,300],[30,240],[58,205],[64,140],[88,90],[96,30],[110,-200]],right=[[205.0,-200],[214,40],[240,70],[248,150],[276,190],[284,270],[300,320],[324,400],[342,470],[350,620]],r1=[[110.0,-200],[118,60],[104,160],[120,260],[98,360],[112,620]],r2=[[205.0,-200],[190,80],[202,190],[184,300],[198,420],[190,620]]
        paint(c,polygon(left+right),0x4B5980);paint(c,polygon(left+Array(r1.dropFirst())),0x6E6C96);paint(c,polygon(right+Array(r2.reversed().dropLast())),0x323C5A);paint(c,polygon([[150,40],[156,120],[146,210],[152,232],[162,120],[157,40]]),0x323C5A);paint(c,polygon([[118,60],[138,92],[112,118]]),0x9A8AA5)
    }
    private func drawTrunk(_ c:CGContext,grove:Bool){
        let lit:UInt32=grove ? 0x2C4F45:0xA8392B,body:UInt32=grove ? 0x0D1418:0x8E2A22,shade:UInt32=grove ? 0x080C0E:0x5E1A1C
        func band(_ a:(Double)->Double,_ b:(Double)->Double)->CGPath {let ys=Array(stride(from:-200.0,through:620.0,by:20));return polygon(ys.map{[a($0),$0]}+ys.reversed().map{[b($0),$0]})}
        let edgeL:(Double)->Double={8-8*(($0+200)/820)+2.5*sin($0/37)},edgeR:(Double)->Double={320+10*(($0+200)/820)+2*sin($0/29+1)},litEdge:(Double)->Double={72.6+5*sin($0/23)+3*sin($0/9)},shadeEdge:(Double)->Double={231+4*sin($0/31+2)+2*sin($0/11)},fissL:(Double)->Double={155.1+7*sin($0/41)-3}
        paint(c,polygon([[10,118],[-58,76],[-62,86],[8,150]]),body);paint(c,polygon([[10,118],[-58,76],[-62,86],[4,126]]),lit);paint(c,polygon([[320,236],[384,196],[390,206],[380,214],[324,270]]),grove ? 0x070B0D:0x4A1517)
        paint(c,band(edgeL,litEdge),lit);paint(c,band(litEdge,shadeEdge),body);paint(c,band(shadeEdge,edgeR),shade);paint(c,band(fissL,{fissL($0)+5+3*abs(sin($0/17))}),shade)
        for (i,n) in [[30.0,60,16,30],[110,190,22,42],[190,300,18,34],[262,110,20,38]].enumerated(){let x=n[0],y=n[1],w=n[2],h=n[3];paint(c,polygon([[x,y],[x+w/2,y+h*0.35],[x+w,y],[x+w*0.62,y+h],[x+w*0.38,y+h]]),grove ? (i==0 ? 0x1B332D:shade):(i==3 ? 0x47131A:shade))}
    }
    private func drawOdometers(_ c:CGContext,f:TrailParallaxTrackerFrame,dy:Double,reduced:Bool){
        let font=TrailParallaxTrackerAssets.font("Outfit-Bold",76),widths=(0...9).map{String($0).size(withAttributes:[.font:font]).width},cell=(widths.max() ?? 36)+1.2,dot=".".size(withAttributes:[.font:font]).width+1.2,total=4*cell+dot,x0=201-total/2,base=630+dy,m=(825.7-f.d.tptClamp(0,150))*10
        let xs=[x0+3*cell+dot,x0+2*cell,x0+cell,x0]
        for j in 0..<4 {drum(c,value:m,column:j,x:xs[j],cell:cell,base:base,step:84,font:font,face:"Outfit-Bold",size:76,color:rgb(0xFCFCFA),top:560+dy,bottom:648+dy,speed:reduced ? 0:f.v)}
        text(".",x0+3*cell+0.6,base,"Outfit-Bold",76,rgb(0xFCFCFA))
        let dayFont=TrailParallaxTrackerAssets.font("Inter-Medium",17),dayCell=(0...9).map{String($0).size(withAttributes:[.font:dayFont]).width}.max() ?? 8,labelWidth="Day ".size(withAttributes:[.font:dayFont]).width,dayX=201-(labelWidth+3*dayCell)/2,dayBase=562+dy,day=TrailParallaxTrackerMath.day(f.d.tptClamp(0,150))
        text("Day ",dayX,dayBase,"Inter-Medium",17,rgb(0x8C8C8C))
        for j in 0..<3 {drum(c,value:day,column:2-j,x:dayX+labelWidth+Double(j)*dayCell,cell:dayCell,base:dayBase,step:22,font:dayFont,face:"Inter-Medium",size:17,color:rgb(0x8C8C8C),top:dayBase-19,bottom:dayBase+6,speed:0)}
        text("miles",201,661+dy,"Inter-Medium",17,rgb(0x8C8C8C),center:true)
    }
    private func drum(_ c:CGContext,value:Double,column:Int,x:Double,cell:Double,base:Double,step:Double,font:UIFont,face:String,size:Double,color:UIColor,top:Double,bottom:Double,speed:Double){
        let p=TrailParallaxTrackerMath.odo(value,column),k=((abs(speed)*10/pow(10,Double(column))-4)/40).tptClamp(0,1),travel=step*(1-0.2*k),fade=min(16,(bottom-top)*0.2)
        c.saveGState();c.clip(to:CGRect(x:x-4,y:top,width:cell+8,height:bottom-top));c.beginTransparencyLayer(auxiliaryInfo:nil)
        for i in -1...2 {let d=((Int(floor(p))+i)%10+10)%10,y=base+(Double(i)-(p-floor(p)))*travel,width=String(d).size(withAttributes:[.font:font]).width;text(String(d),x+(cell-width)/2,y,face,size,color)}
        c.setBlendMode(.destinationIn)
        let mask=CGGradient(colorsSpace:CGColorSpaceCreateDeviceRGB(),colors:[UIColor.clear.cgColor,UIColor.black.cgColor,UIColor.black.cgColor,UIColor.clear.cgColor] as CFArray,locations:[0,fade/(bottom-top),1-fade/(bottom-top),1])!
        c.drawLinearGradient(mask,start:CGPoint(x:x,y:top),end:CGPoint(x:x,y:bottom),options:[.drawsBeforeStartLocation,.drawsAfterEndLocation]);c.endTransparencyLayer();c.restoreGState()
    }
    private func drawCard(_ c:CGContext,f:TrailParallaxTrackerFrame,dy:Double,reduced:Bool){
        let x=14.0,y=676+dy,w=374.0,h=95.0,p=reduced ? (f.cardAge/0.2).tptClamp(0,1):TrailParallaxTrackerMath.cardProgress(f.cardAge),forward=f.cardDir>=0,edge=forward ? w*(1-p):w*p
        c.saveGState();c.translateBy(x:x,y:y);c.addPath(UIBezierPath(roundedRect:CGRect(x:0,y:0,width:w,height:h),cornerRadius:14).cgPath);c.clip();c.setFillColor(rgb(0x111610).cgColor);c.fill(CGRect(x:0,y:0,width:w,height:h))
        for isNew in [false,true] {let index=Int(isNew ? f.cardTo:f.cardFrom).tptClampInt(0,7),l=isNew ? (forward ? edge:0):(forward ? 0:edge),width=isNew ? (forward ? w-edge:edge):(forward ? edge:w-edge),slide=reduced ? 0:isNew ? (forward ? 24.0:-24)*(1-p):(forward ? -24.0:24)*p
            if width<=0 {continue};c.saveGState();c.clip(to:CGRect(x:reduced ? 0:l,y:0,width:reduced ? w:width,height:h));if reduced {c.setAlpha(isNew ? p:1-p)};c.translateBy(x:slide,y:0);let wp=TrailParallaxTrackerWorld.waypoints[index]
            UIImage(systemName:"mappin.and.ellipse")?.withTintColor(.white,renderingMode:.alwaysOriginal).draw(in:CGRect(x:14,y:15,width:16,height:16));text(wp.name,38,28,"Inter-SemiBold",15,.white)
            let paragraph=NSMutableParagraphStyle();paragraph.minimumLineHeight=19;paragraph.maximumLineHeight=19;paragraph.lineBreakMode = .byTruncatingTail
            wp.note.draw(with:CGRect(x:14,y:40,width:346,height:38),options:[.usesLineFragmentOrigin,.truncatesLastVisibleLine],attributes:[.font:TrailParallaxTrackerAssets.font("Inter-Regular",13),.foregroundColor:rgb(0x8F938C),.paragraphStyle:paragraph],context:nil);c.restoreGState()
        }
        if !reduced && f.cardFrom != f.cardTo && p>0 && p<1 {c.setFillColor(rgb(0x111610).cgColor);c.fill(CGRect(x:edge-7,y:0,width:14,height:h));c.setFillColor(UIColor.white.withAlphaComponent(0.28).cgColor);c.fill(CGRect(x:edge-0.75,y:0,width:1.5,height:h))};c.restoreGState()
    }
    private func drawIcon(_ c:CGContext,i:Int){let p=CGMutablePath();if i==0 {for x in [5.0,12.5]{p.move(to:CGPoint(x:x,y:6.5));p.addLine(to:CGPoint(x:x+5.5,y:12));p.addLine(to:CGPoint(x:x,y:17.5))}}else if i==1 {p.addPath(polygon([[3.5,6.5],[9,4],[15,6.5],[20.5,4],[20.5,17.5],[15,20],[9,17.5],[3.5,20]]));p.move(to:CGPoint(x:9,y:4));p.addLine(to:CGPoint(x:9,y:17.5));p.move(to:CGPoint(x:15,y:6.5));p.addLine(to:CGPoint(x:15,y:20))}else{p.addPath(polygon([[12,3],[19.8,7.5],[19.8,16.5],[12,21],[4.2,16.5],[4.2,7.5]]));p.move(to:CGPoint(x:8.6,y:12.6));p.addLine(to:CGPoint(x:12,y:15));p.addLine(to:CGPoint(x:15.4,y:12.6))};c.setLineCap(.round);c.setLineJoin(.round);c.setLineWidth(2);c.setStrokeColor(rgb(i==0 ? 0xFFFFFF:0xB5B5B5).cgColor);c.addPath(p);c.strokePath()}
}
private extension Int {func tptClampInt(_ a:Int,_ b:Int)->Int {Swift.min(Swift.max(self,a),b)}}
