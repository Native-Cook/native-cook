import SwiftUI
import UIKit

struct SelfieVerifyBadge: View {
    static let loop=SelfieVerifyBadgeMotion.loop
    var autoPlay: Bool
    init(autoPlay: Bool = false) { self.autoPlay=autoPlay }
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var pixelRatio
    @StateObject private var driver=SelfieVerifyBadgeDriver()
    typealias M=SelfieVerifyBadgeMotion
    typealias C=SelfieVerifyBadgeTheme
    var body: some View {
        GeometryReader { safe in
            GeometryReader { geo in
                let l=M.Layout(w:geo.size.width,h:geo.size.height,top:safe.safeAreaInsets.top,bottom:safe.safeAreaInsets.bottom)
                TimelineView(.animation(paused: scenePhase != .active || (reduced && driver.now-driver.lastTouch>0.4))) { timeline in
                    let now=timeline.date.timeIntervalSinceReferenceDate-driver.origin
                    let f=driver.frame(now,auto:autoPlay,reduced:reduced)
                    let v=M.verified(l,f.link)
                    ZStack(alignment:.topLeading) {
                        C.dark
                        art(l,f,v,now:driver.drawingTime(now,auto:autoPlay,reduced:reduced))
                        SelfieVerifyBadgeTouchView(done:f.link>0.5,onEvent:{ state,delta,velocity in
                            driver.turn(state,delta,velocity,auto:autoPlay,reduced:reduced)
                        },onTap:{
                            driver.turn(.began,.zero,.zero,auto:autoPlay,reduced:reduced)
                            driver.turn(.ended,.zero,.zero,auto:autoPlay,reduced:reduced)
                        })
                        .frame(width:l.frame*2,height:l.frame*2).offset(x:l.frameX-l.frame,y:l.frameY-l.frame)
                        copy(l,false).opacity(1-v.light)
                        copy(l,true).opacity(v.light)
                        ZStack {
                            Text("Center your face in the frame").foregroundStyle(C.body).opacity(1-f.detected)
                            Text("Face detected").foregroundStyle(C.lime).opacity(f.detected)
                        }.font(C.font("SemiBold",14)).frame(width:l.w,height:18).offset(y:l.hintTop)
                            .opacity((1-v.light)*(1-v.puff))
                        Button { driver.capture(auto:autoPlay,reduced:reduced) } label: {
                            SelfieVerifyBadgeCamera().stroke(C.title,lineWidth:1.7).frame(width:26,height:26)
                                .frame(width:72,height:72).background(C.mix(0x2A2A2A,0xA6F23D,f.detected)).clipShape(Circle())
                        }.buttonStyle(.plain).accessibilityLabel("Take selfie").disabled(f.link>0.5)
                            .scaleEffect((1-0.06*f.shutter)*(1-0.2*v.light))
                            .opacity(1-v.light).offset(x:l.w/2-36,y:l.shutterY-36)
                        Button { driver.back(auto:autoPlay,reduced:reduced) } label: {
                            Text("Continue").font(C.font("SemiBold",16)).foregroundStyle(.white)
                                .frame(width:l.w-64,height:56).background(C.ink,in:Capsule())
                        }.buttonStyle(.plain).allowsHitTesting(f.link>0.5)
                            .opacity(v.light).offset(x:32,y:l.shutterY-28+(1-v.light)*16)
                        Color.white.opacity(f.flash*0.7).allowsHitTesting(false)
                    }.frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
                        .preferredColorScheme(f.link>0.5 ? .light : .dark)
                        .onChange(of:f.link>0.86) { old,new in
                            if new && !old && driver.isManual(now,auto:autoPlay,reduced:reduced) {UINotificationFeedbackGenerator().notificationOccurred(.success)}
                        }
                }
            }.ignoresSafeArea()
        }
    }
    private func copy(_ l: M.Layout,_ light: Bool) -> some View {
        VStack(spacing:10) {
            Text(light ? "You’re verified" : "Selfie verification").font(C.font("SemiBold",20)).tracking(-0.4)
                .foregroundStyle(light ? C.ink : C.title).accessibilityAddTraits(.isHeader)
            Text(light ? "Your identity is confirmed. Sending and receiving have no limits now." : "Take a clear selfie to confirm your identity and keep your account safe.")
                .font(C.font("Regular",15)).foregroundStyle(light ? C.inkBody : C.body)
                .lineSpacing(21-(UIFont(name:"Inter24pt-Regular",size:15)?.lineHeight ?? 18)).fixedSize(horizontal:false,vertical:true)
        }.multilineTextAlignment(.center).frame(width:l.w-80).offset(x:40,y:l.titleTop).allowsHitTesting(false)
    }
    private func art(_ l:M.Layout,_ f:M.Frame,_ v:M.Verified,now:Double) -> some View {
        ZStack(alignment:.topLeading) {
            Rectangle().fill(.white).colorEffect(ShaderLibrary.SelfieVerifyBadgeBust(
                .float2(l.frameX,l.frameY+l.frame*0.04*(1-v.collapse)),.float(l.frame*1.55),
                .float(f.yaw+(reduced ? 0 : 0.38*sin(2*Double.pi*now/M.loop))),.float(v.bust),
                .float(f.scan),.float(pixelRatio),.float(f.detected),.float(v.collapse)))
            Canvas { context,_ in
                var p=Path()
                for (sx,sy,dx,dy) in [(-1.0,-1.0,1.0,1.0),(1,-1,-1,1),(-1,1,1,-1),(1,1,-1,-1)] {
                    let x=l.frameX+sx*l.frame,y=l.frameY+sy*l.frame
                    p.move(to:CGPoint(x:x,y:y+dy*30));p.addLine(to:CGPoint(x:x,y:y+dy*10))
                    p.addQuadCurve(to:CGPoint(x:x+dx*10,y:y),control:CGPoint(x:x,y:y));p.addLine(to:CGPoint(x:x+dx*30,y:y))
                }
                let scale=1-0.18*(1-v.brackets)
                context.translateBy(x:l.frameX,y:l.frameY);context.scaleBy(x:scale,y:scale);context.translateBy(x:-l.frameX,y:-l.frameY)
                context.opacity=v.brackets
                context.stroke(p,with:.color(C.mix(0xE8E8E8,0xA6F23D,f.detected)),style:StrokeStyle(lineWidth:2.5,lineCap:.round,dash:[9,6]))
            }
            Rectangle().fill(.white).colorEffect(ShaderLibrary.SelfieVerifyBadgeCushion(
                .float2(l.frameX,l.frameY),.float(v.size),.float(v.shape),.float(v.puff),.float(v.badge),
                .float(v.yaw+f.spin),.float(v.check),.float(pixelRatio))).opacity(min(1,f.link/0.04))
        }.allowsHitTesting(false)
    }
}

private struct SelfieVerifyBadgeCamera: Shape {
    func path(in rect:CGRect) -> Path {
        var p=Path()
        p.move(to:CGPoint(x:4,y:8.5));p.addArc(center:CGPoint(x:6.5,y:8.5),radius:2.5,startAngle:.degrees(180),endAngle:.degrees(270),clockwise:false)
        p.addLine(to:CGPoint(x:7.7,y:6));p.addLine(to:CGPoint(x:9,y:4));p.addLine(to:CGPoint(x:15,y:4));p.addLine(to:CGPoint(x:16.3,y:6));p.addLine(to:CGPoint(x:17.5,y:6))
        p.addArc(center:CGPoint(x:17.5,y:8.5),radius:2.5,startAngle:.degrees(270),endAngle:.degrees(360),clockwise:false);p.addLine(to:CGPoint(x:20,y:16.5))
        p.addArc(center:CGPoint(x:17.5,y:16.5),radius:2.5,startAngle:.degrees(0),endAngle:.degrees(90),clockwise:false);p.addLine(to:CGPoint(x:6.5,y:19))
        p.addArc(center:CGPoint(x:6.5,y:16.5),radius:2.5,startAngle:.degrees(90),endAngle:.degrees(180),clockwise:false);p.closeSubpath()
        p.addEllipse(in:CGRect(x:8.6,y:9.1,width:6.8,height:6.8))
        return p.applying(CGAffineTransform(scaleX:rect.width/24,y:rect.height/24))
    }
}
