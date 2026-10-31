import SwiftUI
import UIKit

struct TapEarnCoin: View {
    static let loop = TapEarnCoinMotion.loop
    let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @Environment(\.displayScale) private var displayScale
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @StateObject private var driver: TapEarnCoinDriver
    @State private var topInset = 0.0
    @State private var bottomInset = 0.0
    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay; _driver = StateObject(wrappedValue:TapEarnCoinDriver(autoPlay:autoPlay))
    }
    private var pinned: Bool {
        #if DEBUG
        return UserDefaults.standard.object(forKey:"ncPinTime") != nil
        #else
        return false
        #endif
    }
    var body: some View {
        GeometryReader { outer in
            GeometryReader { proxy in
                let L = TapEarnCoinTheme.makeLayout(proxy.size.width,proxy.size.height,topInset,bottomInset)
                TimelineView(.animation(paused:pinned || !driver.running)) { timeline in
                    let date = timeline.date.timeIntervalSinceReferenceDate, f = driver.frame(date), live = driver.live(date)
                    scene(L,f,live,date)
                        .onChange(of:date) { _, n in driver.tick(n) }
                }
                .frame(width:proxy.size.width,height:proxy.size.height,alignment:.topLeading)
                .clipped()
            }
            .ignoresSafeArea()
            .onAppear { topInset = outer.safeAreaInsets.top; bottomInset = outer.safeAreaInsets.bottom }
            .onChange(of:outer.safeAreaInsets) { _, v in topInset = v.top; bottomInset = v.bottom }
        }
        .background(Color.white).preferredColorScheme(.light)
        .onAppear { _ = TapEarnCoinResources.shared; driver.configure(reduced:reduced,active:phase == .active) }
        .onChange(of:reduced) { _, value in driver.configure(reduced:value,active:phase == .active) }
        .onChange(of:phase) { _, value in driver.configure(reduced:reduced,active:value == .active) }
        .onDisappear { driver.configure(reduced:reduced,active:false) }
    }
    private func scene(_ L: TapEarnCoinTheme.Layout, _ f: TapEarnCoinFrame, _ live: [Double], _ date: Double) -> some View {
        let H = TapEarnCoinTheme.self, s = f.scene
        let art = TapEarnCoinArtwork(layout:L)
        let sheetY = L.sheetTop+(1-s.sheet)*(L.sheetH+24)
        return ZStack(alignment:.topLeading) {
            LinearGradient(stops:[.init(color:H.pageTop,location:0),.init(color:H.pageMid,location:0.5),.init(color:.white,location:0.72)],startPoint:.top,endPoint:.bottom)
            VStack(spacing:0) {
                TapEarnCoinCopy(text:"Keep tapping the button\nto earn 1,000¢",size:28,weight:"Bold",line:37,tracking:-0.6,color:H.title,cap:1.25)
                    .accessibilityAddTraits(.isHeader)
                TapEarnCoinCopy(text:"Reach 1,000¢ and it’s paid out as cash.",size:16,line:22,tracking:-0.2,color:H.ink,cap:1.3)
                    .padding(.top,L.subtitleTop-L.titleTop-74)
            }
            .frame(width:L.w-48).offset(x:24,y:L.titleTop)
            .opacity(1-0.7*s.lift*(1-s.travel))
            .accessibilityHidden(s.sheet > 0.02)
            digits(L,f,live).accessibilityHidden(s.sheet > 0.02)
            label(L,f,date).accessibilityHidden(s.sheet > 0.02)
            TapEarnCoinButton(text:"Share to earn more",color:H.cta,pressed:H.ctaPressed,width:L.w-2*L.side,height:56) { driver.share() }
                .position(x:L.w/2,y:L.ctaTop+28).accessibilityHidden(s.sheet > 0.02)
            TapEarnCoinTouchView { state, point, delta, _ in
                if state == .began { driver.beginPress(point,L) }
                else if state == .ended || state == .cancelled || state == .failed {
                    driver.endPress(success:state == .ended && hypot(delta.width,delta.height) <= 80)
                }
            }
            .frame(width:2*L.coinR,height:1.6*L.coinR)
            .position(x:L.coinX,y:L.coinY-0.05*L.coinR)
            .accessibilityElement().accessibilityAddTraits(.isButton)
            .accessibilityLabel("Tap to earn coins")
            .accessibilityHint(s.label < 4 ? "Tap to earn coins. \(s.label) taps left." : "Out of taps.")
            .accessibilityAction { driver.accessibleTap() }
            .allowsHitTesting(s.sheet <= 0.02).accessibilityHidden(s.sheet > 0.02)
            H.dim.opacity(s.dim).allowsHitTesting(false)
            sheet(L,f)
                .frame(width:L.w,height:L.sheetH+40,alignment:.topLeading)
                .background(.white,in:UnevenRoundedRectangle(topLeadingRadius:18,topTrailingRadius:18))
                .opacity(f.sheetFade).offset(y:sheetY)
                .accessibilityElement(children:.contain)
                .accessibilityHidden(s.sheet < 0.02)
                .accessibilityAction(.escape) { driver.closeSheet() }
            objectLayer(L,f,live,date)
            Canvas { context,_ in art.draw(context,live,f,date,driver.refillAge(date)) }
                .allowsHitTesting(false).accessibilityHidden(true)
        }
        .frame(width:L.w,height:L.h,alignment:.topLeading)
    }
    private func objectLayer(_ L: TapEarnCoinTheme.Layout, _ f: TapEarnCoinFrame, _ live: [Double], _ date: Double) -> some View {
        let s = f.scene, o = TapEarnCoinArtwork(layout:L).objectPose(f)
        let x = o.x+f.shake*7*sin(date*70), y = o.y, unit = o.u
        let tilt = TapEarnCoinM.mix(0.56,0.12,s.morph)
        let press = s.press*(1+0.3*f.charge)
        let hop = s.hop+f.charge*0.07*sin(date*57)
        let shadow = TapEarnCoinM.mix(1,0.4,s.travel)*(1-0.85*sin(Double.pi*s.travel))*(1-0.55*s.lift*(1-s.travel))
        let glow = max(TapEarnCoinM.glowOf(live),f.charge*0.9)
        let shader = ShaderLibrary.TapEarnCoinMain(
            .float2(Float(x),Float(y)),.float(Float(unit)),.float(Float(displayScale)),
            .float(Float(tilt)),.float(Float(press)),.float(Float(hop)),
            .float(Float(s.morph)),.float(Float(s.swing)),.float(Float(shadow)),
            .float(Float(f.gleam)),.float(Float(f.fade)),.float2(Float(s.leanX),Float(s.leanZ)),.float(Float(glow)),
            .float2(Float(x-1.65*unit),Float(y-1.65*unit)))
        return Rectangle().fill(.white).frame(width:3.3*unit,height:3.3*unit)
            .colorEffect(shader).position(x:x,y:y).allowsHitTesting(false).accessibilityHidden(true)
    }
    private func digits(_ L: TapEarnCoinTheme.Layout, _ f: TapEarnCoinFrame, _ live: [Double]) -> some View {
        HStack(spacing:L.digitGap) {
            ForEach(0..<3,id:\.self) { i in
                let pos = TapEarnCoinM.columnPos(f.scene.amount,2-i).truncatingRemainder(dividingBy:10)
                VStack(spacing:0) {
                    ForEach(0..<11,id:\.self) { j in
                        Text("\(j%10)").font(TapEarnCoinTheme.font(27,"SemiBold",cap:1)).tracking(-0.3)
                            .foregroundStyle(TapEarnCoinTheme.digit).monospacedDigit()
                            .frame(width:L.digitW,height:L.digitH)
                    }
                }
                .offset(y:-pos*L.digitH)
                .frame(width:L.digitW,height:L.digitH,alignment:.top).clipped()
                .background(TapEarnCoinTheme.digitBox,in:RoundedRectangle(cornerRadius:10*L.w/402))
                .clipShape(RoundedRectangle(cornerRadius:10*L.w/402))
                .scaleEffect(1+0.07*TapEarnCoinM.boxBump(live,i))
            }
            Text("¢").font(TapEarnCoinTheme.font(22,"SemiBold",cap:1.2)).foregroundStyle(TapEarnCoinTheme.unit).padding(.leading,2)
        }
        .fixedSize().frame(width:L.w,height:L.digitH).position(x:L.w/2,y:L.digitsTop+L.digitH/2)
        .accessibilityElement(children:.ignore).accessibilityLabel("\(Int(f.scene.amount.rounded())) cents earned")
    }
    private func label(_ L: TapEarnCoinTheme.Layout, _ f: TapEarnCoinFrame, _ date: Double) -> some View {
        let s = f.scene
        return (Text(s.label >= 4 ? "Refill in " : "Taps left: ").font(TapEarnCoinTheme.font(14,"Medium",cap:1.4))
                + Text(s.label >= 4 ? "00:0\(driver.refillLabel(date))" : "\(s.label)")
                    .font(TapEarnCoinTheme.font(14,"SemiBold",cap:1.4)).foregroundColor(TapEarnCoinTheme.labelNum))
            .tracking(-0.1).foregroundStyle(TapEarnCoinTheme.ink)
            .frame(width:L.w,height:20*TapEarnCoinTheme.fontScale(1.4)).scaleEffect(1+0.08*f.labelPop)
            .offset(x:f.shake*5*sin(date*70)).position(x:L.w/2,y:L.labelTop+10*TapEarnCoinTheme.fontScale(1.4))
    }
    private func sheet(_ L: TapEarnCoinTheme.Layout, _ f: TapEarnCoinFrame) -> some View {
        let H = TapEarnCoinTheme.self
        return ZStack(alignment:.topLeading) {
            TapEarnCoinTouchView(passThrough:true) { state, _, delta, velocity in
                if state == .began { driver.takeOver() }
                else if state == .changed && abs(delta.height) > 1 { driver.dragSheet(delta.height,L.sheetH) }
                else if (state == .ended || state == .cancelled) && abs(delta.height) > 1 { driver.endSheet(delta.height,velocity.dy,L.sheetH) }
            }
            VStack(alignment:.leading,spacing:0) {
                Color.clear.frame(height:64*L.s+L.bellSize+12)
                TapEarnCoinCopy(text:"Want a reminder when\nyou can tap again?",size:23,weight:"Bold",line:32,tracking:-0.5,color:H.sheetTitle,alignment:.leading)
                    .padding(.horizontal,22)
                TapEarnCoinCopy(text:"Taps refill every hour.",size:15,line:21,color:H.sheetBody,alignment:.leading)
                    .padding(.top,8).padding(.horizontal,22)
                Button { driver.takeOver() } label: {
                    HStack {
                        TapEarnCoinCopy(text:"[Optional] Tap-to-earn reminders",size:14,line:20,color:H.sheetRow,alignment:.leading)
                        Spacer()
                        Text("›").font(H.font(22)).foregroundStyle(H.chevron).offset(y:-2)
                    }.frame(minHeight:44).contentShape(Rectangle())
                }.buttonStyle(.plain).padding(.horizontal,22).padding(.top,20)
                TapEarnCoinButton(text:"Agree and remind me",color:H.blue,pressed:H.bluePressed,width:L.w-2*L.side,height:56) { driver.remind() }
                    .padding(.horizontal,L.side).padding(.top,20)
                Button { driver.takeOver(); driver.closeSheet() } label: {
                    TapEarnCoinCopy(text:"Maybe later",size:15,weight:"Medium",line:20,color:H.sheetRow)
                        .frame(maxWidth:.infinity).contentShape(Rectangle())
                }.buttonStyle(TapEarnCoinLaterStyle(script:f.scene.later)).padding(.top,20)
            }
            .allowsHitTesting(f.scene.sheet > 0.02)
        }
    }
}

struct TapEarnCoinButton: View {
    let text: String, color: Color, pressed: Color, width: Double, height: Double
    let action: () -> Void
    var body: some View {
        Button(action:action) {
            Text(text).font(TapEarnCoinTheme.font(17,"SemiBold",cap:1.3)).tracking(-0.2).foregroundStyle(.white)
                .frame(width:width,height:height).contentShape(RoundedRectangle(cornerRadius:14))
        }.buttonStyle(TapEarnCoinButtonStyle(color:color,pressed:pressed))
    }
}
struct TapEarnCoinButtonStyle: ButtonStyle {
    let color: Color, pressed: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.background(configuration.isPressed ? pressed : color,in:RoundedRectangle(cornerRadius:14))
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.timingCurve(0.23,1,0.32,1,duration:0.12),value:configuration.isPressed)
    }
}
struct TapEarnCoinLaterStyle: ButtonStyle {
    var script: Double
    func makeBody(configuration: Configuration) -> some View {
        let value = configuration.isPressed ? 1 : script
        return configuration.label.scaleEffect(1-0.04*value).opacity(1-0.35*value)
            .animation(.linear(duration:configuration.isPressed ? 0.08 : 0.2),value:configuration.isPressed)
    }
}

/// Explicit line boxes preserve the RN line-height while allowing wrapped copy to push siblings down.
struct TapEarnCoinCopy: View {
    let text: String
    let size: Double
    var weight = "Regular"
    let line: Double
    var tracking = 0.0
    let color: Color
    var cap = Double.infinity
    var alignment: TextAlignment = .center
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    var body: some View {
        let scale = TapEarnCoinTheme.fontScale(cap)
        let leading = max(0,(line-TapEarnCoinTheme.uiFont(size,weight).lineHeight)*scale)
        Text(text).font(TapEarnCoinTheme.font(size,weight,cap:cap)).tracking(tracking)
            .foregroundStyle(color).multilineTextAlignment(alignment)
            .lineSpacing(leading).padding(.vertical,leading/2).fixedSize(horizontal:false,vertical:true)
            .frame(maxWidth:.infinity,alignment:alignment == .leading ? .leading : .center)
    }
}
