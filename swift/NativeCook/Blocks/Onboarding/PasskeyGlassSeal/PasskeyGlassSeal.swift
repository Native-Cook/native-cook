import SwiftUI
import UIKit

struct PasskeyGlassSeal: View {
    static let loop = PasskeyGlassSealMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = PasskeyGlassSealClock()
    @GestureState private var holding = false
    @State private var maxDistance = 0.0
    @State private var touching = false
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"passkeyGlassSealTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { proxy in
                let l = PasskeyGlassSealTheme.Layout(proxy.size,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom)
                ZStack(alignment:.topLeading) {
                    background(l)
                    brand(l)
                TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.moving))) { timeline in
                    let s = clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned)
                    let f = s.frame
                    ZStack(alignment:.topLeading) {
                        card(l,s)
                            .modifier(PasskeyGlassSealFlip(flip:f.flip,reduced:reduced,width:l.cw))
                            .position(x:l.x+l.cw/2,y:l.cy+l.ch/2)
                            .gesture(DragGesture(minimumDistance:0,coordinateSpace:.global)
                                .updating($holding) { _, state, _ in state = true }
                                .onChanged { value in
                                    guard pinned == nil else { return }
                                    if !touching { touching = true; maxDistance = 0; clock.begin(reduced) }
                                    maxDistance = max(maxDistance,hypot(value.translation.width,value.translation.height))
                                    clock.drag(value.translation.width,width:l.cw,reduced:reduced)
                                }
                                .onEnded { value in
                                    guard pinned == nil else { return }
                                    clock.end(reduced,tap:maxDistance <= 10,velocityX:value.velocity.width,width:l.cw)
                                    touching = false
                                })
                            .onChange(of:holding) { _, value in
                                if !value && touching {
                                    clock.end(reduced,tap:false,cancelled:true); touching = false
                                }
                            }
                            .accessibilityElement(children:.ignore)
                            .accessibilityLabel(PasskeyGlassSealMotion.faceOf(f.flip) == 1 ? "Welcome back, Mira Okafor. Tap to lock" : "Passkey. Touch and hold to sign in")
                            .accessibilityAddTraits(.isButton)
                            .accessibilityAction { guard pinned == nil else { return }; clock.activate(reduced) }
                            .allowsHitTesting(pinned == nil)
                        PasskeyGlassSealText(text:f.status,size:20,weight:.light,spacing:0.5,alpha:0.56)
                            .frame(width:l.w,height:24).position(x:l.w/2,y:l.y(730)-13+12+1.0/3)
                        if PasskeyGlassSealMotion.faceOf(f.flip) == 1 {
                            Button {} label: {
                                PasskeyGlassSealText(text:"Continue",size:15,weight:.regular,spacing:1.5)
                                    .fixedSize().padding(.horizontal,28).padding(.horizontal,1).frame(height:44)
                                    .overlay(Capsule().strokeBorder(.white.opacity(0.5),lineWidth:1))
                            }.buttonStyle(PasskeyGlassSealPressStyle(reduced:reduced))
                                .position(x:l.w/2,y:l.y(730)+34+22)
                                .accessibilityLabel("Continue")
                                .allowsHitTesting(pinned == nil)
                        }
                        Canvas { context, _ in
                            let r = 22*(1-0.12*f.press)
                            let p = Path(ellipseIn:CGRect(x:l.x+l.px-r,y:l.cy+l.py-r,width:2*r,height:2*r))
                            context.fill(p,with:.color(.white.opacity(0.1*f.finger)))
                            context.stroke(p,with:.color(.white.opacity(0.55*f.finger)),lineWidth:1.5)
                        }.allowsHitTesting(false).accessibilityHidden(true)
                    }.frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
                }
                }.frame(width:l.w,height:l.h,alignment:.topLeading)
            }.ignoresSafeArea()
        }
        .background(PasskeyGlassSealTheme.mid)
        .preferredColorScheme(.dark)
        .onAppear { clock.reset(autoPlay && !reduced,reduced) }
        .onChange(of:scenePhase) { _, _ in clock.suspend() }
        .onChange(of:reduced) { _, _ in clock.reset(autoPlay && !reduced,reduced) }
        .onChange(of:autoPlay) { _, _ in clock.reset(autoPlay && !reduced,reduced) }
    }
    private func background(_ l: PasskeyGlassSealTheme.Layout) -> some View {
        Canvas { context, _ in
            context.fill(Path(CGRect(x:0,y:0,width:l.w,height:l.h)),with:.linearGradient(
                Gradient(stops:[.init(color:PasskeyGlassSealTheme.top,location:0),.init(color:PasskeyGlassSealTheme.mid,location:0.5),.init(color:PasskeyGlassSealTheme.bottom,location:1)]),startPoint:.zero,endPoint:CGPoint(x:0,y:l.h)))
            let c = CGPoint(x:l.w*0.5,y:-l.h*0.05), r = l.w*0.9
            context.fill(Path(ellipseIn:CGRect(x:c.x-r,y:c.y-r,width:2*r,height:2*r)),with:.radialGradient(
                Gradient(colors:[PasskeyGlassSealTheme.color(0xE67092,0.55),PasskeyGlassSealTheme.color(0xE67092,0)]),center:c,startRadius:0,endRadius:r))
        }.accessibilityHidden(true)
    }
    private func brand(_ l: PasskeyGlassSealTheme.Layout) -> some View {
        HStack(spacing:29*l.sx) {
            Canvas { context, _ in
                context.stroke(PasskeyGlassSealTheme.mark,with:.color(.white),style:StrokeStyle(lineWidth:7,lineCap:.butt))
                context.fill(PasskeyGlassSealTheme.triangle,with:.color(.white))
                context.fill(Path(ellipseIn:CGRect(x:22,y:22,width:10,height:10)),with:.color(.white))
            }.frame(width:54,height:54)
            PasskeyGlassSealText(text:"ORVANE",size:19,weight:.light,spacing:10.5,alignment:.left).fixedSize()
        }.fixedSize().offset(x:120*l.sx-27,y:l.y(170)-27)
            .accessibilityElement(children:.ignore).accessibilityLabel("ORVANE").accessibilityAddTraits(.isHeader)
    }
    private func card(_ l: PasskeyGlassSealTheme.Layout, _ s: PasskeyGlassSealClock.Sample) -> some View {
        let flip = s.frame.flip
        let a = flip-PasskeyGlassSealMotion.nearestFace(flip)
        let lift = reduced ? 0 : abs(sin(.pi*a))
        return ZStack {
            ZStack {
                LinearGradient(colors:[PasskeyGlassSealTheme.card,PasskeyGlassSealTheme.cardBottom],startPoint:.top,endPoint:.bottom)
                PasskeyGlassSealMetalView(layout:l,sample:s,reduced:reduced)
            }.clipShape(RoundedRectangle(cornerRadius:12))
                .opacity(reduced ? 1-flip : PasskeyGlassSealMotion.faceOf(flip) == 0 ? 1 : 0)
            ZStack {
                PasskeyGlassSealTheme.card
                VStack(spacing:6) {
                    Canvas { context, _ in
                        let circle = PasskeyGlassSealTheme.backCircle
                        context.fill(circle,with:.color(.white.opacity(0.08)))
                        context.stroke(circle,with:.color(.white.opacity(0.85)),lineWidth:1.5)
                        context.stroke(PasskeyGlassSealTheme.check,with:.color(.white),style:StrokeStyle(lineWidth:2.4,lineCap:.round,lineJoin:.round))
                    }.frame(width:64,height:64)
                    PasskeyGlassSealText(text:"Welcome back",size:15,weight:.light,spacing:2.5,alpha:0.7).fixedSize().padding(.top,14).offset(y:-1.0/3)
                    PasskeyGlassSealText(text:"Mira Okafor",size:26,weight:.light).fixedSize().offset(x:-1.0/3)
                    PasskeyGlassSealText(text:"Passkey saved on this iPhone",size:13,weight:.regular,alpha:0.72).fixedSize().padding(.top,8)
                }
            }.clipShape(RoundedRectangle(cornerRadius:12))
                .opacity(reduced ? flip : PasskeyGlassSealMotion.faceOf(flip) == 1 ? 1 : 0)
            RoundedRectangle(cornerRadius:12)
                .fill(PasskeyGlassSealTheme.color(0x1E0E22,reduced ? 0 : 0.42*(1-cos(.pi*a))))
                .allowsHitTesting(false)
            Canvas { context, _ in
                let x = l.cw*(0.5-1.9*sin(.pi*a+0.42))
                context.opacity = reduced ? 0 : 0.5*pow(max(0,cos(.pi*a+0.42)),6)*min(1,6*abs(sin(.pi*a)))
                context.fill(Path(roundedRect:CGRect(x:0,y:0,width:l.cw,height:l.ch),cornerRadius:12),with:.linearGradient(
                    Gradient(stops:[.init(color:PasskeyGlassSealTheme.color(0xFFECF4,0),location:0.3),
                                    .init(color:PasskeyGlassSealTheme.color(0xFFECF4,0.9),location:0.5),
                                    .init(color:PasskeyGlassSealTheme.color(0xFFECF4,0),location:0.7)]),
                    startPoint:CGPoint(x:x-0.45*l.cw,y:0),endPoint:CGPoint(x:x+0.45*l.cw,y:0.35*l.ch)))
            }.allowsHitTesting(false)
        }.frame(width:l.cw,height:l.ch)
            .compositingGroup()
            .shadow(color:PasskeyGlassSealTheme.color(0x2B1430,0.5-0.2*lift),radius:22+18*lift,x:0,y:12+22*lift)
            .contentShape(Rectangle())
    }
}

// UILabel uses the same system font and NSAttributedString kern metrics as RN Text on iOS.
struct PasskeyGlassSealText: UIViewRepresentable {
    var text: String
    var size: Double
    var weight: UIFont.Weight
    var spacing: Double = 0
    var alpha: Double = 1
    var alignment: NSTextAlignment = .center
    func makeCoordinator() -> PasskeyGlassSealTextResources {
        PasskeyGlassSealTextResources(size:size,weight:weight,spacing:spacing,alpha:alpha)
    }
    func makeUIView(context: Context) -> UILabel {
        let v = UILabel(); v.textAlignment = alignment; v.backgroundColor = .clear
        v.setContentHuggingPriority(.required,for:.horizontal)
        return v
    }
    func updateUIView(_ v: UILabel, context: Context) {
        if v.attributedText?.string == text { return }
        let label = NSAttributedString(string:text,attributes:context.coordinator.attributes)
        context.coordinator.size = label.size()
        v.attributedText = label
    }
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UILabel, context: Context) -> CGSize? {
        let size = context.coordinator.size
        let scale = uiView.traitCollection.displayScale
        return CGSize(width:proposal.width ?? ceil(size.width*scale)/scale,height:proposal.height ?? ceil(size.height*scale)/scale)
    }
}

struct PasskeyGlassSealFlip: ViewModifier {
    var flip: Double
    var reduced: Bool
    var width: Double
    func body(content: Content) -> some View {
        let a = reduced ? 0 : flip-PasskeyGlassSealMotion.nearestFace(flip)
        let scale = 1+0.07*abs(sin(.pi*a))
        // RN's uniform scale includes z before perspective; SwiftUI scaleEffect is only x/y.
        // Scaling perspective by the lift reproduces that same projected camera distance.
        content
            .rotation3DEffect(.degrees(180*a),axis:(x:0,y:1,z:0),perspective:width*scale/1100)
            .scaleEffect(scale)
    }
}

final class PasskeyGlassSealTextResources {
    let attributes: [NSAttributedString.Key:Any]
    var size = CGSize.zero
    init(size: Double, weight: UIFont.Weight, spacing: Double, alpha: Double) {
        var attributes: [NSAttributedString.Key:Any] = [.font:UIFont.systemFont(ofSize:size,weight:weight),.foregroundColor:UIColor.white.withAlphaComponent(alpha)]
        if spacing != 0 { attributes[.kern] = spacing }
        self.attributes = attributes
    }
}
