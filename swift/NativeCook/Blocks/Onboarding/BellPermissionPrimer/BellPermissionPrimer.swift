import SwiftUI
import UIKit

struct BellPermissionPrimer:View {
    static let loop=BellPermissionPrimerMotion.loop
    var autoPlay=false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock=BellPermissionPrimerClock()
    private var pinned:Double? {
        #if DEBUG
        if let v=UserDefaults.standard.object(forKey:"bellPermissionPrimerTime") { return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0 }
        #endif
        return nil
    }
    var body:some View {
        GeometryReader { safe in
            GeometryReader { full in
                let l=BellPermissionPrimerLayout(w:full.size.width,h:full.size.height,top:safe.safeAreaInsets.top,bottom:safe.safeAreaInsets.bottom)
                TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (!clock.running && (!autoPlay || reduced)))) { time in
                    let sample=clock.sample(time.date,autoPlay,reduced,pinned)
                    scene(l,sample)
                }
            }.ignoresSafeArea()
        }
        .background(BellPermissionPrimerResources.page)
        .preferredColorScheme(.light)
        .onAppear { clock.reset(autoPlay,reduced) }
        .onDisappear { clock.active(false) }
        .onChange(of:scenePhase) { _,v in clock.active(v == .active) }
        .onChange(of:reduced) { _,v in clock.configure(autoPlay,v) }
        .onChange(of:autoPlay) { _,_ in clock.configure(autoPlay,reduced) }
    }
    private func label(_ text:String,_ font:String,_ size:Double,_ line:Double?,_ color:Int,_ center:Bool=false,_ tracking:Double=0,_ lines:Int=1) -> BellPermissionPrimerLabel {
        BellPermissionPrimerLabel(text:text,font:font,size:size,line:line,color:BellPermissionPrimerResources.color(color),center:center,tracking:tracking,lines:lines)
    }
    private func scene(_ l:BellPermissionPrimerLayout,_ s:BellPermissionPrimerClock.Sample) -> some View {
        let f=s.frame, m=f.expand
        let bannerAlpha=BellPermissionPrimerMotion.clamp((m-0.8)/0.2)
        return ZStack(alignment:.topLeading) {
            BellPermissionPrimerBackdrop(layout:l).frame(width:l.w,height:l.h).accessibilityHidden(true)
            label("Notifications","BarlowSemiCondensed-Bold",43,52,0x25323B,true)
                .frame(width:l.w,height:52).position(x:l.w/2,y:l.y(480)-30+26)
                .accessibilityAddTraits(.isHeader)
            label("One quiet reminder from Mosswell on the mornings your plants are thirsty.","Barlow-Regular",22,33,0x545C68,true,0,0)
                .frame(width:312*l.sx,height:99).position(x:l.w/2,y:l.y(530)-20+49.5)
            BellPermissionPrimerGlow(width:114*l.sx-28)
                .frame(width:114*l.sx-28+100,height:128)
                .opacity(1-0.7*m).position(x:144*l.sx+14+(114*l.sx-28)/2,y:l.y(656)+22)
                .allowsHitTesting(false).accessibilityHidden(true)
            Button { clock.allow(reduced) } label: {
                label("ALLOW","Barlow-SemiBold",17,nil,0xFFFFFF,true,0.6)
                    .frame(width:114*l.sx,height:44)
                    .background(BellPermissionPrimerResources.color(0x489BFF),in:Capsule(style:.circular))
            }.buttonStyle(BellPermissionPrimerPressStyle(reduced:reduced))
                .accessibilityLabel("Allow notifications")
                .position(x:144*l.sx+57*l.sx,y:l.y(656)+22)
            Button {} label: {
                label("SKIP","Barlow-Bold",17,nil,0x4A8BDF,true,0.4)
                    .frame(width:120,height:36).padding(8).contentShape(Rectangle())
            }.buttonStyle(BellPermissionPrimerPressStyle(reduced:reduced))
                .accessibilityLabel("Skip")
                .position(x:l.w/2,y:l.y(769))
            BellPermissionPrimerGlass(layout:l,frame:f,time:s.t)
                .frame(width:l.w,height:l.h).allowsHitTesting(false).accessibilityHidden(true)
            BellPermissionPrimerTouchView { state,translation,velocity in
                switch state {
                case .began:clock.begin(reduced)
                case .changed:
                    if hypot(translation.width,translation.height)>=4 { clock.drag(translation.width,translation.height,velocity.dx,l.unit,reduced) }
                case .ended,.cancelled,.failed:
                    if hypot(translation.width,translation.height)<4 && state == .ended { clock.tap(reduced) }
                    clock.end(velocity.dx,velocity.dy,l.unit,reduced)
                default:break
                }
            }
            .frame(width:l.unit*1.8,height:l.unit*1.45)
            .position(x:l.px,y:l.py-10+l.unit*1.45/2)
            .accessibilityElement().accessibilityAddTraits(.isButton)
            .accessibilityLabel("Bell").accessibilityHint("Swipe to ring it")
            .accessibilityAction { clock.tap(reduced,accessibility:true) }
            Button { clock.dismiss(reduced) } label: {
                HStack(spacing:12) {
                    BellPermissionPrimerLeaf().frame(width:44,height:44)
                    VStack(alignment:.leading,spacing:0) {
                        HStack(spacing:0) {
                            label("MOSSWELL","Barlow-SemiBold",12,nil,0x8E949C,false,0.8)
                            Spacer(minLength:0)
                            label("now","Barlow-Regular",12,nil,0x8E949C).frame(width:22)
                        }.frame(height:14.4)
                        label("Time to water your monstera","Barlow-SemiBold",16,21,0xFFFFFF).frame(height:21)
                        label("Its soil is dry 2 cm down. A glass of water will do.","Barlow-Regular",14,18,0xC9CED4).frame(height:18)
                    }
                }.padding(.horizontal,18)
                    .frame(width:l.w-20*l.sx,height:92).contentShape(Rectangle())
            }.buttonStyle(.plain).compositingGroup().opacity(bannerAlpha).scaleEffect(0.96+0.04*m)
                .allowsHitTesting(m>0.5).accessibilityHidden(m<=0.5)
                .accessibilityLabel("MOSSWELL: Time to water your monstera. Tap to put the bell back")
                .position(x:l.w/2,y:l.islandY+46)
            Canvas { ctx,_ in
                guard f.finger>0 else { return }
                let x=f.fingerAt==0 ? l.px+f.fingerX*l.unit*0.6 : (f.fingerAt==1 ? l.w/2:l.w*0.6)
                let y=f.fingerAt==0 ? l.py+l.unit*0.95-150*f.lift : (f.fingerAt==1 ? l.y(656)+22:l.islandY+92*0.55)
                let r=22*(1-0.12*f.fingerPress)
                let p=Path(ellipseIn:CGRect(x:x-r,y:y-r,width:2*r,height:2*r))
                let c=Color(.sRGB,red:20/255,green:40/255,blue:90/255)
                ctx.fill(p,with:.color(c.opacity(0.08*f.finger)))
                ctx.stroke(p,with:.color(c.opacity(0.35*f.finger)),lineWidth:1.5)
            }.allowsHitTesting(false).accessibilityHidden(true)
        }.frame(width:l.w,height:l.h,alignment:.topLeading).clipped().allowsHitTesting(pinned == nil)
        // the notification covers the status bar, so the clock is hidden while it is open (as in React Native)
        .statusBarHidden(m > 0.5)
    }
}

struct BellPermissionPrimerBackdrop:UIViewRepresentable {
    let layout:BellPermissionPrimerLayout
    final class BellPermissionPrimerBackdropCache { var layout:BellPermissionPrimerLayout? }
    func makeCoordinator() -> BellPermissionPrimerBackdropCache { BellPermissionPrimerBackdropCache() }
    func makeUIView(context:Context) -> UIImageView { let v=UIImageView(); v.isUserInteractionEnabled=false; return v }
    func updateUIView(_ view:UIImageView,context:Context) {
        guard context.coordinator.layout != layout else { return }; context.coordinator.layout=layout
        let l=layout
        let format=UIGraphicsImageRendererFormat(); format.scale=view.traitCollection.displayScale; format.preferredRange = .standard
        view.image=UIGraphicsImageRenderer(size:CGSize(width:l.w,height:l.h),format:format).image { renderer in
            let c=renderer.cgContext
            func color(_ h:Int) -> CGColor { UIColor(BellPermissionPrimerResources.color(h)).cgColor }
            func gradient(_ a:Int,_ b:Int,_ p:CGPoint,_ q:CGPoint) {
                let g=CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB),colors:[color(a),color(b)] as CFArray,locations:[0,1])!
                c.drawLinearGradient(g,start:p,end:q,options:[.drawsBeforeStartLocation,.drawsAfterEndLocation])
            }
            c.setFillColor(color(0xFDFDFE)); c.fill(CGRect(x:0,y:0,width:l.w,height:l.h))
            c.saveGState(); c.clip(to:CGRect(x:0,y:0,width:l.w,height:l.y(190)+30))
            gradient(0xF6F9FD,0xFDFDFE,CGPoint(x:0,y:l.y(190)-30),CGPoint(x:0,y:l.y(190)+30)); c.restoreGState()
            let x=200.5*l.sx,y=l.y(304),r=114*l.sx,hy=l.y(296)
            c.saveGState(); c.addEllipse(in:CGRect(x:x-r,y:y-r,width:2*r,height:2*r)); c.clip()
            gradient(0x6CC2FC,0x4C86FA,CGPoint(x:x-r*0.5,y:y-r),CGPoint(x:x+r*0.3,y:y+r*0.2))
            c.move(to:CGPoint(x:x-r*1.2,y:hy+16*l.sx))
            c.addQuadCurve(to:CGPoint(x:x+r*1.2,y:hy+16*l.sx),control:CGPoint(x:x,y:hy-10*l.sx))
            c.addLine(to:CGPoint(x:x+r*1.2,y:y+r*1.2)); c.addLine(to:CGPoint(x:x-r*1.2,y:y+r*1.2)); c.closePath(); c.clip()
            gradient(0x4A93FD,0x3550FB,CGPoint(x:x,y:hy),CGPoint(x:x,y:y+r)); c.restoreGState()
        }
    }
}

struct BellPermissionPrimerPressStyle:PrimitiveButtonStyle {
    let reduced:Bool
    func makeBody(configuration:Configuration) -> some View { BellPermissionPrimerPressSurface(configuration:configuration,reduced:reduced) }
}
struct BellPermissionPrimerPressSurface:View {
    let configuration:PrimitiveButtonStyleConfiguration
    let reduced:Bool
    @Environment(\.scenePhase) private var phase
    @State private var down=false
    var body:some View {
        configuration.label.scaleEffect(down && !reduced ? 0.96:1).opacity(down && reduced ? 0.72:1)
            .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:down)
            .overlay { GeometryReader { proxy in
                let retention=CGRect(origin:.zero,size:proxy.size).insetBy(dx:-16,dy:-16)
                Color.clear.contentShape(Rectangle()).gesture(DragGesture(minimumDistance:0)
                    .onChanged { v in down=retention.contains(v.location) }
                    .onEnded { v in down=false; if retention.contains(v.location) { configuration.trigger() } })
            }}.accessibilityAction { configuration.trigger() }
            .onChange(of:phase) { _,v in if v != .active { down=false } }
    }
}
struct BellPermissionPrimerLeaf:View {
    static let path:Path = {
        var p=Path(); p.move(to:CGPoint(x:11,y:17.5))
        p.addCurve(to:CGPoint(x:4.5,y:9.5),control1:CGPoint(x:11,y:12),control2:CGPoint(x:7.5,y:9.5))
        p.addCurve(to:CGPoint(x:11,y:16),control1:CGPoint(x:4.5,y:13.5),control2:CGPoint(x:7,y:16))
        p.move(to:CGPoint(x:11,y:17.5))
        p.addCurve(to:CGPoint(x:17.5,y:7.5),control1:CGPoint(x:11,y:11.5),control2:CGPoint(x:14,y:8))
        p.addCurve(to:CGPoint(x:11,y:15.5),control1:CGPoint(x:17.5,y:12),control2:CGPoint(x:15,y:15.5)); return p
    }()
    var body:some View {
        RoundedRectangle(cornerRadius:12,style:.circular).fill(BellPermissionPrimerResources.color(0x1C2A22))
            .overlay {
                RoundedRectangle(cornerRadius:6,style:.circular).fill(BellPermissionPrimerResources.color(0x3FA56B))
                    .overlay { Self.path.stroke(.white,style:StrokeStyle(lineWidth:1.5,lineCap:.round)) }.frame(width:22,height:22)
            }
    }
}

struct BellPermissionPrimerTouchView:UIViewRepresentable {
    var onEvent:(UIGestureRecognizer.State,CGSize,CGVector)->Void
    func makeUIView(context:Context) -> BellPermissionPrimerTouchSurface { let v=BellPermissionPrimerTouchSurface(); v.onEvent=onEvent; return v }
    func updateUIView(_ view:BellPermissionPrimerTouchSurface,context:Context) { view.onEvent=onEvent }
}
final class BellPermissionPrimerTouchSurface:UIView,UIGestureRecognizerDelegate {
    var onEvent:((UIGestureRecognizer.State,CGSize,CGVector)->Void)?
    private var start=CGPoint.zero,last=CGPoint.zero,lastTime=0.0,velocity=CGVector.zero
    override init(frame:CGRect) {
        super.init(frame:frame); backgroundColor = .clear; isOpaque=false
        let g=UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
        g.minimumPressDuration=0; g.allowableMovement = .greatestFiniteMagnitude; g.delegate=self; addGestureRecognizer(g)
    }
    @available(*,unavailable) required init?(coder:NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool { true }
    @objc private func handle(_ g:UILongPressGestureRecognizer) {
        let p=g.location(in:self),t=ProcessInfo.processInfo.systemUptime
        if g.state == .began { start=p; last=p; lastTime=t; velocity = .zero }
        else if g.state == .changed { let dt=max(0.001,t-lastTime); velocity=CGVector(dx:(p.x-last.x)/dt,dy:(p.y-last.y)/dt); last=p; lastTime=t }
        onEvent?(g.state,CGSize(width:p.x-start.x,height:p.y-start.y),velocity)
    }
}

struct BellPermissionPrimerGlow:UIViewRepresentable {
    let width:Double
    final class BellPermissionPrimerGlowCache { var width=0.0 }
    func makeCoordinator() -> BellPermissionPrimerGlowCache { BellPermissionPrimerGlowCache() }
    func makeUIView(context:Context) -> UIImageView { UIImageView() }
    func updateUIView(_ view:UIImageView,context:Context) {
        guard context.coordinator.width != width else { return }; context.coordinator.width=width
        let format=UIGraphicsImageRendererFormat(); format.scale=view.traitCollection.displayScale; format.preferredRange = .standard
        view.image=UIGraphicsImageRenderer(size:CGSize(width:width+100,height:128),format:format).image { r in
            let c=r.cgContext, rect=CGRect(x:50,y:50,width:width,height:28)
            let path=CGPath(roundedRect:rect,cornerWidth:14,cornerHeight:14,transform:nil)
            // the shadow of a blue pill that the button hides (no hole in it: a hole read as a pale ghost
            // button under ALLOW)
            c.setShadow(offset:CGSize(width:0,height:14),blur:22,color:UIColor(BellPermissionPrimerResources.color(0x489BFF).opacity(0.45)).cgColor)
            c.setFillColor(UIColor(BellPermissionPrimerResources.color(0x489BFF)).cgColor); c.addPath(path); c.fillPath()
        }
    }
}
