import CoreText
import SwiftUI
import UIKit

struct ShapeShiftViewfinder: View {
    var autoPlay=true
    static let loop=10.0
    @Environment(\.accessibilityReduceMotion) private var reduced
    private var pin:Double? {
        #if DEBUG
        let a=ProcessInfo.processInfo.arguments
        if let i=a.firstIndex(of:"-viewfinder-time"),a.indices.contains(i+1){return Double(a[i+1])}
        #endif
        return nil
    }
    private var manual:Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("-viewfinder-manual")
        #else
        return false
        #endif
    }
    var body:some View {ShapeShiftViewfinderSurface(autoPlay:autoPlay,reduced:reduced,pin:pin,manual:manual).ignoresSafeArea().preferredColorScheme(.dark)}
}

private struct ShapeShiftViewfinderSurface:UIViewRepresentable {
    var autoPlay:Bool,reduced:Bool,pin:Double?,manual:Bool
    func makeUIView(context:Context)->ShapeShiftViewfinderView {ShapeShiftViewfinderView()}
    func updateUIView(_ view:ShapeShiftViewfinderView,context:Context){view.autoPlay=autoPlay;view.reduced=reduced;view.pin=pin;view.manual=manual;view.wake()}
}

private final class ShapeShiftViewfinderElement:UIAccessibilityElement {
    var action:(()->Void)?,adjust:((Double)->Void)?
    override func accessibilityActivate()->Bool {action?();return action != nil}
    override func accessibilityIncrement(){adjust?(0.1)}
    override func accessibilityDecrement(){adjust?(-0.1)}
}

private final class ShapeShiftViewfinderView:UIView,UIGestureRecognizerDelegate {
    var autoPlay=true,reduced=false,pin:Double?,manual=false
    private let engine=ShapeShiftViewfinderEngine()
    private var link:CADisplayLink?,lastAction=0.0
    private var startX=0.0,startY=0.0,startK=1.0,startRot=0.0,anchorX=0.0,anchorY=0.0
    private var panKind=0,scrollStart=0.0
    private var active=Set<String>()
    private var shapePath=CGPath(rect:.zero,transform:nil)
    private var s=1.0,left=0.0,stageTop=65.0,stageHeight=633.0,controlY=760.0,sheetTop=635.0
    private let colors:[UInt32]=[0xF02FA6,0xB9836B,0x315FEA,0xF4E59B,0xC5AFF3,0x171820,0xF4F1E7,0x43B8E5,0x7548CF,0xC61CCB,0xFFC58F,0x9EDFE4]
    private let dynamic:[UInt32]=[0x118169,0x2E6E54,0x4E4A5A,0x555274,0x5B4A55,0x3F5A4E,0xA9C9B4,0x4A6B38]
    override init(frame:CGRect){
        super.init(frame:frame);backgroundColor = .black;isMultipleTouchEnabled=true;isAccessibilityElement=false
        ShapeShiftViewfinderAssets.register()
        let pan=UIPanGestureRecognizer(target:self,action:#selector(pan(_:)));pan.delegate=self;addGestureRecognizer(pan)
        let pinch=UIPinchGestureRecognizer(target:self,action:#selector(pinch(_:)));pinch.delegate=self;addGestureRecognizer(pinch)
        let rotation=UIRotationGestureRecognizer(target:self,action:#selector(rotate(_:)));rotation.delegate=self;addGestureRecognizer(rotation)
        let tap=UITapGestureRecognizer(target:self,action:#selector(tap(_:)))
        let twice=UITapGestureRecognizer(target:self,action:#selector(doubleTap(_:)));twice.numberOfTapsRequired=2;tap.require(toFail:twice);addGestureRecognizer(tap);addGestureRecognizer(twice)
    }
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    override func didMoveToWindow(){super.didMoveToWindow();if window==nil {link?.invalidate();link=nil}else if link==nil {let l=CADisplayLink(target:self,selector:#selector(tick));l.preferredFrameRateRange=CAFrameRateRange(minimum:30,maximum:120,preferred:60);l.add(to:.main,forMode:.common);link=l}}
    func wake(){lastAction=now;link?.isPaused=false;setNeedsDisplay()}
    private var now:Double {CACurrentMediaTime()}
    @objc private func tick(){setNeedsDisplay();if reduced && active.isEmpty && now-lastAction>0.35 {link?.isPaused=true}}
    private func layoutScene(){
        let insets=window?.safeAreaInsets ?? safeAreaInsets,h=Double(bounds.height),w=Double(bounds.width),bottom=max(Double(insets.bottom),12)
        stageTop=max(Double(insets.top),20)+3;s=min(w/402,max(1,h-stageTop-bottom)/590);left=(w-402*s)/2
        controlY=h-bottom-80*s;stageHeight=(controlY-60*s-stageTop)/s
        sheetTop=h-bottom+5*s-210*s
    }
    private func color(_ rgb:UInt32,_ a:Double=1)->UIColor {UIColor(red:Double((rgb>>16)&255)/255,green:Double((rgb>>8)&255)/255,blue:Double(rgb&255)/255,alpha:a)}
    private func path(_ points:[ShapeShiftViewfinderPoint])->CGPath {
        let p=CGMutablePath();guard let first=points.first else{return p};p.move(to:CGPoint(x:first.x,y:first.y))
        for j in 0..<points.count {let a=points[(j+191)%192],b=points[j],c=points[(j+1)%192],d=points[(j+2)%192];p.addCurve(to:CGPoint(x:c.x,y:c.y),control1:CGPoint(x:b.x+(c.x-a.x)/6,y:b.y+(c.y-a.y)/6),control2:CGPoint(x:c.x-(d.x-b.x)/6,y:c.y-(d.y-b.y)/6))};p.closeSubpath();return p
    }
    override func draw(_ rect:CGRect){
        guard let c=UIGraphicsGetCurrentContext() else{return};layoutScene()
        let f=engine.frame(now:now,autoPlay:autoPlay,pin:pin,manual:manual,reduced:reduced)
        c.setFillColor(UIColor.black.cgColor);c.fill(bounds)
        let points=ShapeShiftViewfinderGeometry.points(f,stageHeight);shapePath=path(points)
        c.saveGState();c.translateBy(x:left+6*s,y:stageTop);c.scaleBy(x:s,y:s)
        c.addPath(UIBezierPath(roundedRect:CGRect(x:0,y:0,width:390,height:stageHeight),cornerRadius:44).cgPath);c.clip()
        c.setFillColor(color(f.base).cgColor);c.fill(CGRect(x:0,y:0,width:390,height:stageHeight))
        if f.f1u>=0 {flood(c,path:shapePath,progress:f.f1u,tone:f.f1c)}
        if f.f2u>=0 {flood(c,path:shapePath,progress:f.f2u,tone:f.f2c)}
        for (offset,blur,alpha) in [(2.0,5.0,0.24),(14+7*f.lift,30+5*f.lift,0.16+0.03*f.lift)] {
            c.saveGState();c.setShadow(offset:CGSize(width:0,height:offset),blur:blur,color:color(f.dot,alpha).cgColor);c.setFillColor(color(f.dot,0.35).cgColor);c.addPath(shapePath);c.fillPath();c.restoreGState()
        }
        if reduced,let previous=engine.previousFrame,engine.crossfade<1 {picture(c,f:previous,path:path(ShapeShiftViewfinderGeometry.points(previous,stageHeight)),alpha:1-engine.crossfade)}
        picture(c,f:f,path:shapePath,alpha:reduced ? engine.crossfade:1)
        c.saveGState();c.addPath(shapePath);c.clip();c.addPath(shapePath);c.setLineWidth(28);c.replacePathWithStrokedPath();c.clip()
        let cx=195+f.x,cy=stageHeight/2+f.y
        let bevel=CGGradient(colorsSpace:CGColorSpaceCreateDeviceRGB(),colors:[UIColor.white.withAlphaComponent(0.30).cgColor,UIColor.clear.cgColor,UIColor.black.withAlphaComponent(0.16).cgColor] as CFArray,locations:[0,0.5,1])!
        c.drawLinearGradient(bevel,start:CGPoint(x:cx-112.5*f.scale,y:cy-112.5*f.scale),end:CGPoint(x:cx+112.5*f.scale,y:cy+112.5*f.scale),options:[.drawsBeforeStartLocation,.drawsAfterEndLocation]);c.restoreGState()
        c.saveGState();c.addPath(shapePath);c.setLineWidth(1.5);c.replacePathWithStrokedPath();c.clip()
        let rim=CGGradient(colorsSpace:CGColorSpaceCreateDeviceRGB(),colors:[UIColor.white.withAlphaComponent(0.75).cgColor,UIColor.clear.cgColor,UIColor.clear.cgColor] as CFArray,locations:[0,0.45,1])!
        c.drawLinearGradient(rim,start:CGPoint(x:cx-135*f.scale,y:cy-135*f.scale),end:CGPoint(x:cx+135*f.scale,y:cy+135*f.scale),options:[.drawsBeforeStartLocation,.drawsAfterEndLocation]);c.restoreGState()
        if f.blinkA>0 {echo(c,path:shapePath,distance:f.blinkD,width:2,tone:.white,alpha:f.blinkA)}
        let radii=ShapeShiftViewfinderGeometry.radii(f)
        for i in 0..<2 where f.rings[i*3]>0.001 {
            let angle=f.rings[i*3+1],j=Int(((angle.truncatingRemainder(dividingBy:2*Double.pi)+2*Double.pi).truncatingRemainder(dividingBy:2*Double.pi))/(2*Double.pi)*192).clampedSSV(0,191)
            let r=radii[j]*150*max(f.iris,0.001),x=r*sin(angle),y = -r*cos(angle),a=(f.rot+f.lean)*Double.pi/180,ring=13*f.rings[i*3+2]
            let p=CGPoint(x:cx+f.scale*(x*cos(a)-y*sin(a)),y:cy+f.scale*(x*sin(a)+y*cos(a)))
            c.setFillColor(UIColor.white.withAlphaComponent(f.rings[i*3]*0.45).cgColor);c.setStrokeColor(UIColor.white.withAlphaComponent(f.rings[i*3]).cgColor);c.setLineWidth(2);c.addEllipse(in:CGRect(x:p.x-ring,y:p.y-ring,width:2*ring,height:2*ring));c.drawPath(using:.fillStroke)
        }
        let chip=CGRect(x:162,y:stageHeight-62,width:66,height:38)
        c.setFillColor(UIColor.black.withAlphaComponent(0.34).cgColor);c.addPath(UIBezierPath(roundedRect:chip,cornerRadius:19).cgPath);c.fillPath()
        label(abs(f.k-1)<0.05 ? "1×":String(format:"%.1f×",f.k),center:CGPoint(x:195,y:stageHeight-43),size:17,tone:.white,medium:false)
        c.restoreGState()
        let hints=["Tap to change shape","Pinch to resize","Drag to move"]
        for i in 0..<3 where f.hint[i]>0.001 {
            c.saveGState();c.setAlpha(f.hint[i]);let text=hints[i],font=ShapeShiftViewfinderAssets.font(false,17*s),width=text.size(withAttributes:[.font:font]).width,total=width+30*s,x=(bounds.width-total)/2,y=stageTop+(stageHeight/2-175)*s
            UIImage(systemName:i==0 ? "hand.tap":i==1 ? "hand.pinch":"hand.draw")?.withTintColor(.white,renderingMode:.alwaysOriginal).draw(in:CGRect(x:x,y:y-11*s,width:22*s,height:22*s))
            text.draw(at:CGPoint(x:x+30*s,y:y-font.lineHeight/2),withAttributes:[.font:font,.foregroundColor:UIColor(red:0.98,green:0.98,blue:0.973,alpha:1),.kern:-0.1*s]);c.restoreGState()
        }
        drawControls(c,f:f)
        if f.sheet>0.001 {drawSheet(c,f:f)}
        updateAccessibility(f)
    }
    private func picture(_ c:CGContext,f:ShapeShiftViewfinderFrame,path:CGPath,alpha:Double){
        c.saveGState();c.setAlpha(alpha);c.addPath(path);c.clip()
        c.translateBy(x:195+f.x,y:stageHeight/2+f.y);c.rotate(by:(f.rot+f.lean)*Double.pi/180);c.scaleBy(x:f.scale,y:f.scale)
        c.saveGState();c.translateBy(x:f.sx+f.lx,y:f.sy+f.ly)
        let front=f.flip>=0.5,fr=front ? [0.5,0.56,440.0,0]:f.fr,size=fr[2]*1.04
        c.rotate(by:(fr[3]+f.sr)*Double.pi/180);c.scaleBy(x:max(abs(cos(Double.pi*f.flip)),0.002),y:1)
        ShapeShiftViewfinderAssets.drawPlate(front:front,rect:CGRect(x:-fr[0]*size,y:-fr[1]*size,width:size,height:size),alpha:alpha);c.restoreGState()
        c.saveGState()
        c.translateBy(x:f.shx,y:f.shy)
        c.rotate(by:f.shr*Double.pi/180)
        ShapeShiftViewfinderAssets.shade.draw(in:CGRect(x:-320,y:-320,width:640,height:640),blendMode:.multiply,alpha:alpha*0.55)
        c.restoreGState()
        if f.dim<1 {c.setFillColor(UIColor.black.withAlphaComponent(1-f.dim).cgColor);c.fill(CGRect(x:-1000,y:-1000,width:2000,height:2000))}
        if f.flash>0 {c.setFillColor(UIColor.white.withAlphaComponent(f.flash).cgColor);c.fill(CGRect(x:-1000,y:-1000,width:2000,height:2000))};c.restoreGState()
    }
    private func echo(_ c:CGContext,path:CGPath,distance:Double,width:Double,tone:UIColor,alpha:Double){
        if distance<=0.5 || alpha<=0.01{return};c.saveGState();c.setAlpha(alpha);c.beginTransparencyLayer(auxiliaryInfo:nil);c.setLineJoin(.round);c.setStrokeColor(tone.cgColor);c.setLineWidth(2*distance+width);c.addPath(path);c.strokePath();c.setBlendMode(.clear);c.setLineWidth(max(2*distance-width,0));c.addPath(path);c.strokePath();c.addPath(path);c.fillPath();c.endTransparencyLayer();c.restoreGState()
    }
    private func flood(_ c:CGContext,path:CGPath,progress:Double,tone:UInt32){
        let front=560*(1-pow(1-progress.ssvClamp(0,1),1.6)),fill=max(front-52*(1-ShapeShiftViewfinderMath.seg(front,120,260)),0),fade=1-ShapeShiftViewfinderMath.seg(front,190,260)
        c.saveGState();c.setFillColor(color(tone).cgColor);c.setStrokeColor(color(tone).cgColor);c.setLineWidth(2*fill);c.setLineJoin(.round);c.addPath(path);c.drawPath(using:.fillStroke);c.restoreGState()
        let light=color(ShapeShiftViewfinderMath.color(tone,0xFFFFFF,0.25))
        for (d,w,a) in [(52.0,2.0,0.2),(26,2.5,0.4),(0,3,0.7)] {echo(c,path:path,distance:front-d,width:w,tone:light,alpha:a*fade)}
    }
    private func label(_ text:String,center:CGPoint,size:Double,tone:UIColor,medium:Bool){let font=ShapeShiftViewfinderAssets.font(medium,size),a:[NSAttributedString.Key:Any]=[.font:font,.foregroundColor:tone];let z=text.size(withAttributes:a);text.draw(at:CGPoint(x:center.x-z.width/2,y:center.y-z.height/2),withAttributes:a)}
    private func drawControls(_ c:CGContext,f:ShapeShiftViewfinderFrame){
        c.saveGState();c.setAlpha(1-ShapeShiftViewfinderMath.seg(f.sheet,0,0.6))
        for x in [80.0,322] {c.setFillColor(color(0x1A1A1C).cgColor);c.fillEllipse(in:CGRect(x:left+(x-22)*s,y:controlY-22*s,width:44*s,height:44*s))}
        c.setFillColor(color(f.dot).cgColor);c.fillEllipse(in:CGRect(x:left+67*s,y:controlY-13*s,width:26*s,height:26*s))
        c.setStrokeColor(color(0x1C1C1E).cgColor);c.setLineWidth(6*s);c.strokeEllipse(in:CGRect(x:left+164*s,y:controlY-37*s,width:74*s,height:74*s))
        c.setFillColor(color(0xFAFAF8).cgColor);let r=32*f.disc*s;c.fillEllipse(in:CGRect(x:left+201*s-r,y:controlY-r,width:2*r,height:2*r))
        UIImage(systemName:"arrow.triangle.2.circlepath")?.withTintColor(.white,renderingMode:.alwaysOriginal).draw(in:CGRect(x:left+311*s,y:controlY-11*s,width:22*s,height:22*s));c.restoreGState()
    }
    private func drawSheet(_ c:CGContext,f:ShapeShiftViewfinderFrame){
        let y=sheetTop+(1-f.sheet)*(bounds.height-sheetTop+12),palette=engine.solidTarget ? colors:dynamic
        c.saveGState();c.translateBy(x:left+14*s,y:y);c.scaleBy(x:s,y:s)
        c.addPath(UIBezierPath(roundedRect:CGRect(x:0,y:0,width:374,height:210),cornerRadius:28).cgPath);c.clip();c.setFillColor(color(f.dot).cgColor);c.fill(CGRect(x:0,y:0,width:374,height:210));c.setFillColor(UIColor.black.withAlphaComponent(0.2).cgColor);c.fill(CGRect(x:0,y:0,width:374,height:210))
        c.setFillColor(UIColor.white.withAlphaComponent(0.3).cgColor);c.addPath(UIBezierPath(roundedRect:CGRect(x:169,y:5,width:36,height:5),cornerRadius:2.5).cgPath);c.fillPath();label("Background Color",center:CGPoint(x:187,y:35),size:24,tone:.white,medium:false)
        c.setFillColor(UIColor.black.withAlphaComponent(0.45).cgColor);c.addPath(UIBezierPath(roundedRect:CGRect(x:19,y:72,width:336,height:36),cornerRadius:18).cgPath);c.fillPath()
        c.setFillColor(color(0x2B2B2E).cgColor);c.addPath(UIBezierPath(roundedRect:CGRect(x:19+168*f.segment,y:72,width:168,height:36),cornerRadius:18).cgPath);c.fillPath();label("Dynamic",center:CGPoint(x:103,y:90),size:15,tone:.white,medium:true);label("Solid",center:CGPoint(x:271,y:90),size:15,tone:.white,medium:true)
        c.saveGState();c.clip(to:CGRect(x:19,y:123,width:336,height:62))
        for (i,tone) in palette.enumerated(){let x=24+Double(i)*50-engine.rowScroll;let rr=CGRect(x:x,y:128,width:42,height:52);c.setFillColor(color(tone).cgColor);c.addPath(UIBezierPath(roundedRect:rr,cornerRadius:14).cgPath);c.fillPath();if i==engine.selectedSwatch {c.setStrokeColor(UIColor.white.cgColor);c.setLineWidth(2.5);c.addPath(UIBezierPath(roundedRect:rr.insetBy(dx:-5,dy:-5),cornerRadius:19).cgPath);c.strokePath()}}
        c.restoreGState();c.restoreGState()
    }
    private func stagePoint(_ p:CGPoint)->CGPoint {CGPoint(x:(p.x-left-6*s)/s,y:(p.y-stageTop)/s)}
    private func changed(_ key:String,_ isActive:Bool){if isActive {active.insert(key)}else{active.remove(key)};engine.held = !active.isEmpty;wake()}
    @objc private func tap(_ g:UITapGestureRecognizer){guard g.state == .ended else{return};let p=g.location(in:self),f=engine.f;wake()
        if f.sheet>0.5 {
            let local=CGPoint(x:(p.x-left-14*s)/s,y:(p.y-sheetTop)/s)
            if local.y>=72 && local.y<=108 {engine.takeOver(now:now);engine.solidTarget=local.x>187;engine.selectedSwatch=0;engine.rowScroll=0}
            else if local.y>=123 && local.y<=190 {let i=Int(((local.x-24+engine.rowScroll)/50).rounded(.down)),palette=engine.solidTarget ? colors:dynamic;if palette.indices.contains(i){engine.color(palette[i],index:i,now:now)}}
            else {engine.sheet(now:now,open:false)};return
        }
        if abs(p.y-controlY)<44*s {
            let x=(p.x-left)/s
            if abs(x-80)<30 {engine.sheet(now:now)}else if abs(x-201)<44 {engine.capture(now:now)}else if abs(x-322)<30 {engine.flip(now:now)};return
        }
        let q=stagePoint(p)
        if q.y>stageHeight-62 && abs(q.x-195)<40 {engine.takeOver(now:now);engine.f.k=1;engine.kGoal=1;return}
        if shapePath.contains(q) {engine.morph(now:now,angle:atan2(q.x-195-f.x,-(q.y-stageHeight/2-f.y))-(f.rot+f.lean)*Double.pi/180);UISelectionFeedbackGenerator().selectionChanged()}
    }
    @objc private func doubleTap(_ g:UITapGestureRecognizer){if g.state == .ended {engine.takeOver(now:now);engine.home(now:now);wake()}}
    @objc private func pan(_ g:UIPanGestureRecognizer){let p=g.location(in:self),q=stagePoint(p),translation=g.translation(in:self)
        if g.state == .began {
            engine.takeOver(now:now);startX=engine.f.x;startY=engine.f.y;scrollStart=engine.rowScroll
            panKind=engine.f.sheet>0.5 ? (p.y>sheetTop+120*s ? 3:2):(shapePath.contains(q) ? 1:0)
            if panKind==1 {changed("pan",true)}
        } else if g.state == .changed {
            if panKind==1 {engine.f.x=(startX+translation.x/s).ssvClamp(-195,195);engine.f.y=(startY+translation.y/s).ssvClamp(-stageHeight/2,stageHeight/2)}
            if panKind==2 {engine.f.sheet=(1-max(translation.y,0)/(210*s)).ssvClamp(0,1)}
            if panKind==3 {let count=engine.solidTarget ? colors.count:dynamic.count;engine.rowScroll=(scrollStart-translation.x/s).ssvClamp(0,max(0,Double(count)*50-336+10))};wake()
        } else if [.ended,.cancelled,.failed].contains(g.state) {
            if panKind==1 {let v=g.velocity(in:self);engine.release(now:now,cancelled:g.state != .ended,velocityX:v.x/s,velocityY:v.y/s,height:stageHeight);changed("pan",false)}
            if panKind==2 {engine.sheet(now:now,open:engine.f.sheet>=0.6)};wake()
        }
    }
    @objc private func pinch(_ g:UIPinchGestureRecognizer){let q=stagePoint(g.location(in:self))
        if g.state == .began {engine.takeOver(now:now);startK=engine.f.k;startX=engine.f.x;startY=engine.f.y;anchorX=195+startX-q.x;anchorY=stageHeight/2+startY-q.y;changed("pinch",true)}
        if g.state == .changed {engine.f.k=(startK*g.scale).ssvClamp(0.45,1.3);let ratio=engine.f.k/startK;engine.f.x=startX+anchorX*(ratio-1);engine.f.y=startY+anchorY*(ratio-1);wake()}
        if [.ended,.cancelled,.failed].contains(g.state){engine.release(now:now,cancelled:true,height:stageHeight);changed("pinch",false)}
    }
    @objc private func rotate(_ g:UIRotationGestureRecognizer){if g.state == .began {engine.takeOver(now:now);startRot=engine.f.rot;changed("rotation",true)};if g.state == .changed {engine.f.rot=startRot+g.rotation*180/Double.pi;wake()};if [.ended,.cancelled,.failed].contains(g.state){engine.release(now:now,cancelled:true,height:stageHeight);changed("rotation",false)}}
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool {gestureRecognizer is UIPinchGestureRecognizer || gestureRecognizer is UIRotationGestureRecognizer || otherGestureRecognizer is UIPinchGestureRecognizer || otherGestureRecognizer is UIRotationGestureRecognizer}
    private func updateAccessibility(_ f:ShapeShiftViewfinderFrame){
        var out=[ShapeShiftViewfinderElement]()
        func element(_ label:String,_ rect:CGRect,_ value:String?=nil,_ action:@escaping()->Void){let e=ShapeShiftViewfinderElement(accessibilityContainer:self);e.accessibilityLabel=label;e.accessibilityValue=value;e.accessibilityTraits = .button;e.accessibilityFrameInContainerSpace=rect;e.action=action;out.append(e)}
        element("Change shape",CGRect(x:left+6*s,y:stageTop,width:390*s,height:stageHeight*s),ShapeShiftViewfinderGeometry.names[engine.target]){[weak self]in guard let self else{return};self.engine.morph(now:self.now,angle:0);self.wake()}
        element("Background color",CGRect(x:left+58*s,y:controlY-22*s,width:44*s,height:44*s)){[weak self]in guard let self else{return};self.engine.sheet(now:self.now);self.wake()}
        element("Take photo",CGRect(x:left+161*s,y:controlY-40*s,width:80*s,height:80*s)){[weak self]in guard let self else{return};self.engine.capture(now:self.now);self.wake()}
        element("Flip camera",CGRect(x:left+300*s,y:controlY-22*s,width:44*s,height:44*s),engine.flipTarget>0.5 ? "Front":"Back"){[weak self]in guard let self else{return};self.engine.flip(now:self.now);self.wake()}
        let z=ShapeShiftViewfinderElement(accessibilityContainer:self);z.accessibilityLabel="Zoom";z.accessibilityValue=String(format:"%.1f times",f.k);z.accessibilityTraits = .adjustable;z.accessibilityFrameInContainerSpace=CGRect(x:left+168*s,y:stageTop+(stageHeight-62)*s,width:66*s,height:38*s);z.adjust={[weak self]d in guard let self else{return};self.engine.takeOver(now:self.now);self.engine.kGoal=(self.engine.f.k+d).ssvClamp(0.45,1.3);self.wake()};out.append(z)
        if f.sheet>0.5 {
            for (i,label) in ["Dynamic","Solid"].enumerated(){element(label,CGRect(x:left+(33+168*Double(i))*s,y:sheetTop+72*s,width:168*s,height:36*s)){[weak self]in guard let self else{return};self.engine.takeOver(now:self.now);self.engine.solidTarget=i==1;self.engine.rowScroll=0;self.wake()}}
            for (i,tone) in (engine.solidTarget ? colors:dynamic).enumerated(){let x=left+(38+Double(i)*50-engine.rowScroll)*s;if x<left+33*s || x+42*s>left+369*s {continue};element("Color \(i+1)",CGRect(x:x,y:sheetTop+128*s,width:42*s,height:52*s)){[weak self]in guard let self else{return};self.engine.color(tone,index:i,now:self.now);self.wake()}}
        };accessibilityElements=out
    }
}

private extension Int {func clampedSSV(_ a:Int,_ b:Int)->Int {Swift.min(Swift.max(self,a),b)}}
@MainActor private enum ShapeShiftViewfinderAssets {
    static func register(){_ = mediumFace;_ = semiboldFace}
    private static func face(_ name:String)->CGFont? {guard let url=Bundle.main.url(forResource:"ShapeShiftViewfinder-"+name,withExtension:"ttf"),let provider=CGDataProvider(url:url as CFURL) else{return nil};return CGFont(provider)}
    private static let mediumFace=face("Inter-Medium"),semiboldFace=face("Inter-SemiBold")
    private static var fonts:[String:UIFont]=[:]
    static func font(_ medium:Bool,_ size:Double)->UIFont {
        let key="\(medium)-\(size)";if let font=fonts[key] {return font}
        guard let face=medium ? mediumFace:semiboldFace else{return .systemFont(ofSize:size,weight:medium ? .medium:.semibold)}
        let font=CTFontCreateWithGraphicsFont(face,size,nil,nil) as UIFont;fonts[key]=font;return font
    }
    private static func image(_ name:String,_ ext:String)->UIImage {guard let u=Bundle.main.url(forResource:"ShapeShiftViewfinder-"+name,withExtension:ext),let i=UIImage(contentsOfFile:u.path) else{return UIImage()};return i}
    static let plate=image("plate","jpg"),front=image("plate-front","jpg"),shade=image("shade","png")
    private static func edges(_ image:UIImage)->[UIImage] {
        guard let cg=image.cgImage else{return []};let w=cg.width,h=cg.height
        return [CGRect(x:0,y:0,width:1,height:h),CGRect(x:w-1,y:0,width:1,height:h),CGRect(x:0,y:0,width:w,height:1),CGRect(x:0,y:h-1,width:w,height:1)].compactMap{cg.cropping(to:$0).map{UIImage(cgImage:$0)}}
    }
    private static let plateEdges=edges(plate),frontEdges=edges(front)
    static func drawPlate(front useFront:Bool,rect:CGRect,alpha:CGFloat) {
        let image=useFront ? front:plate,edges=useFront ? frontEdges:plateEdges
        // Match ImageShader's CLAMP while the picture collapses to a thin slice during a camera flip.
        if edges.count==4 {
            edges[0].draw(in:CGRect(x:rect.minX-2000,y:rect.minY,width:2000,height:rect.height),blendMode:.normal,alpha:alpha)
            edges[1].draw(in:CGRect(x:rect.maxX,y:rect.minY,width:2000,height:rect.height),blendMode:.normal,alpha:alpha)
            edges[2].draw(in:CGRect(x:rect.minX,y:rect.minY-2000,width:rect.width,height:2000),blendMode:.normal,alpha:alpha)
            edges[3].draw(in:CGRect(x:rect.minX,y:rect.maxY,width:rect.width,height:2000),blendMode:.normal,alpha:alpha)
        }
        image.draw(in:rect,blendMode:.normal,alpha:alpha)
    }
}
