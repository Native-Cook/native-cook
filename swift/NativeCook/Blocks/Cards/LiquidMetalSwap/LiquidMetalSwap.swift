import SwiftUI

struct LiquidMetalSwap: View {
    static let loop = LiquidMetalSwapMotion.loop
    let autoPlay: Bool
    @StateObject private var driver=LiquidMetalSwapDriver()
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var scale
    @Environment(\.scenePhase) private var scenePhase
    private typealias C = LiquidMetalSwapTheme
    private typealias M = LiquidMetalSwapMotion
    init(autoPlay: Bool = false) { self.autoPlay=autoPlay }
    private var pin: Double? {
        #if DEBUG
        if let number=UserDefaults.standard.object(forKey:"ncPinTime") as? NSNumber { return number.doubleValue }
        if let value=UserDefaults.standard.string(forKey:"ncPinTime"), let number=Double(value) { return number }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let l=LiquidMetalSwapLayout(w:full.size.width,h:full.size.height,top:safe.safeAreaInsets.top,bottom:safe.safeAreaInsets.bottom)
                TimelineView(.animation(paused:(pin != nil && driver.mode == 0) || scenePhase != .active || (!driver.running && !(autoPlay && !reduced)))) { context in
                    let s=driver.sample(context.date,pin:pin)
                    screen(l,s)
                        .onChange(of:driver.needsFrames) { _, moving in if driver.running != moving { driver.running=moving } }
                }
            }.ignoresSafeArea()
        }
        .background(C.page)
        .preferredColorScheme(.dark)
        .onAppear { driver.configure(autoPlay:autoPlay,reduced:reduced) }
        .onChange(of:reduced) { _, value in driver.configure(autoPlay:autoPlay,reduced:value) }
        .onChange(of:autoPlay) { _, _ in driver.configure(autoPlay:autoPlay,reduced:reduced) }
    }
    private func screen(_ l: LiquidMetalSwapLayout,_ s: M.Frame) -> some View {
        let k=M.look(s.m), role=s.flip > 0.5 ? 1 : 0
        return ZStack(alignment:.topLeading) {
            C.page
            header.frame(width:l.cardW,height:44).offset(x:l.side,y:l.barTop)
            stage(l,s,k,role).frame(width:l.w,height:l.stageH).offset(y:l.stageTop)
            cards(l,s).frame(width:l.cardW,height:232,alignment:.topLeading).offset(x:l.side,y:l.cardsTop)
            VStack(spacing:0) {
                quote("Rate",C.rates[role])
                quote("Price impact","0.08%")
                quote("Network fee","$0.12")
            }.frame(width:l.cardW-8).offset(x:l.side+4,y:l.rowsTop)
            button(l,s,role).frame(width:l.cardW,height:56)
                .scaleEffect(reduced ? 1 : 1-0.04*s.press).offset(x:l.side,y:l.buttonTop)
            Circle().fill(Color.white.opacity(0.35)).overlay(Circle().strokeBorder(Color.white.opacity(0.7),lineWidth:1.5))
                .frame(width:44,height:44).scaleEffect(1-0.12*s.press).opacity(0.85*s.finger)
                .offset(x:(s.fingerAt == 1 ? l.w/2-40+90*s.fingerX : l.w/2)-22,
                        y:(s.fingerAt == 1 ? l.stageTop+l.stageH*0.42 : l.buttonTop+28)-22)
                .allowsHitTesting(false).accessibilityHidden(true)
        }.frame(width:l.w,height:l.h,alignment:.topLeading)
    }
    private var header: some View {
        HStack {
            Text("Swap").font(C.font(28,"SemiBold")).foregroundStyle(C.ink).accessibilityAddTraits(.isHeader)
            Spacer()
            Text("Slippage 0.5%").font(C.font(13,"Medium")).monospacedDigit().foregroundStyle(C.mute)
                .padding(.horizontal,12).frame(height:32).background(C.card,in:Capsule())
                .overlay(Capsule().strokeBorder(C.cardEdge,lineWidth:1))
        }
    }
    private func stage(_ l: LiquidMetalSwapLayout,_ s: M.Frame,_ k: M.Look,_ role: Int) -> some View {
        let a=C.tokens[0].tint, b=C.tokens[1].tint
        let tint=a+(b-a)*Float(k.gold)
        let sway=reduced ? 0 : M.sway(driver.mode == 0 ? (pin ?? (driver.clock-driver.autoEpoch)) : driver.clock)
        return ZStack(alignment:.bottom) {
            Rectangle().fill(.white).colorEffect(ShaderLibrary.LiquidMetalSwapMain(
                .float4(Float(l.w),Float(l.stageH),Float(scale),Float(driver.shaderTime)),
                .float4(Float(k.kind),Float(k.sink),Float(k.soft),Float(sway+s.yaw)),
                .float4(Float(k.poolR),Float(k.poolH),Float(k.wobble),Float(reduced ? 0 : k.drops)),
                .float2(0,Float(driver.lift)),.float3(tint.x,tint.y,tint.z),.float3(11/255.0,12/255.0,16/255.0)))
            ZStack {
                Text("Prism · PRSM").opacity(k.kind == 0 ? 1-k.sink : 0)
                Text("Spark · SPRK").opacity(k.kind == 1 ? 1-k.sink : 0)
            }.font(C.font(13,"Medium")).tracking(0.4).foregroundStyle(C.mute).padding(.bottom,12)
            LiquidMetalSwapTouchView { state, translation, velocity in
                if state == .began { driver.beginTurn() }
                else if state == .changed { driver.turn(x:translation.width,y:translation.height) }
                else if state == .ended || state == .cancelled || state == .failed { driver.endTurn(velocity:velocity.dx) }
            }
        }
        .accessibilityElement(children:.ignore).accessibilityAddTraits(.isImage)
        .accessibilityLabel("\(C.tokens[role].name) coin, the token you hold")
        .accessibilityHint(reduced ? "" : "Drag sideways to turn it")
    }
    private func cards(_ l: LiquidMetalSwapLayout,_ s: M.Frame) -> some View {
        ZStack(alignment:.topLeading) {
            ForEach(0..<2) { i in
                RoundedRectangle(cornerRadius:24).fill(C.card)
                    .overlay(RoundedRectangle(cornerRadius:24).strokeBorder(C.cardEdge,lineWidth:1))
                    .frame(width:l.cardW,height:112).overlay(alignment:.topLeading) {
                        Text(i == 0 ? "You pay" : "You receive").font(C.font(13,"Medium")).foregroundStyle(C.mute).padding(.leading,16).padding(.top,12)
                    }.offset(y:Double(i)*120)
            }
            tokenRow(C.tokens[0],width:l.cardW).opacity(1-0.92*sin(Double.pi*s.flip)).offset(y:s.flip*120)
                .accessibilityLabel("\(s.flip > 0.5 ? "You receive" : "You pay") 250 PRSM, $412.50")
            tokenRow(C.tokens[1],width:l.cardW).opacity(1-0.92*sin(Double.pi*s.flip)).offset(y:(1-s.flip)*120)
                .accessibilityLabel("\(s.flip > 0.5 ? "You pay" : "You receive") 1,204.83 SPRK, $412.17")
            Circle().fill(C.card).overlay(Circle().strokeBorder(C.page,lineWidth:4))
                .overlay { LiquidMetalSwapArrow().stroke(C.mute,style:StrokeStyle(lineWidth:1.8,lineCap:.round,lineJoin:.round)).frame(width:16,height:16) }
                .frame(width:36,height:36).offset(x:l.cardW/2-18,y:98).accessibilityHidden(true)
        }
    }
    private func tokenRow(_ token: LiquidMetalSwapToken,width: Double) -> some View {
        HStack(alignment:.top) {
            VStack(alignment:.leading,spacing:2) {
                Text(token.amount).font(C.font(32,"SemiBold")).foregroundStyle(C.ink).monospacedDigit().frame(height:38)
                Text(token.fiat).font(C.font(13)).foregroundStyle(C.mute).monospacedDigit()
            }
            Spacer(minLength:4)
            VStack(alignment:.trailing,spacing:8) {
                HStack(spacing:8) {
                    Circle().fill(token.colour).frame(width:24,height:24)
                    Text(token.symbol).font(C.font(15,"SemiBold")).foregroundStyle(C.ink)
                }.padding(.leading,6).padding(.trailing,12).frame(height:36).background(C.color(0x20232B),in:Capsule())
                Text(token.balance).font(C.font(13)).foregroundStyle(C.mute).monospacedDigit()
            }
        }.frame(width:width-32,alignment:.topLeading).padding(.horizontal,16).padding(.top,34)
            .frame(width:width,height:112,alignment:.topLeading).accessibilityElement(children:.ignore)
    }
    private func quote(_ label: String,_ value: String) -> some View {
        HStack { Text(label).font(C.font(14)).foregroundStyle(C.mute); Spacer(); Text(value).font(C.font(14,"Medium")).foregroundStyle(C.ink).monospacedDigit() }.frame(height:36)
    }
    private func label(_ swapped: Bool,_ dark: Bool) -> some View {
        HStack(spacing:8) {
            if swapped { LiquidMetalSwapCheck().stroke(C.page,style:StrokeStyle(lineWidth:2.2,lineCap:.round,lineJoin:.round)).frame(width:18,height:18) }
            Text(swapped ? "Swapped" : "Hold to swap").font(C.font(17,"SemiBold")).foregroundStyle(dark ? C.page : C.ink)
        }.frame(maxWidth:.infinity,maxHeight:.infinity)
    }
    private func button(_ l: LiquidMetalSwapLayout,_ s: M.Frame,_ role: Int) -> some View {
        ZStack(alignment:.leading) {
            C.color(0x23262E)
            (s.start == 1 ? C.gold : C.silver).opacity(s.swapped)
            label(false,false).opacity(1-s.swapped)
            ZStack {
                s.start == 0 ? C.gold : C.silver
                label(false,true).frame(width:l.cardW,height:56)
            }.frame(width:l.cardW,height:56).mask(alignment:.leading) { Rectangle().frame(width:M.clamp01(s.fill)*l.cardW) }.opacity(1-s.swapped)
            label(false,true).opacity(min(s.swapped,1-s.doneText))
            label(true,true).opacity(min(s.swapped,s.doneText))
            LiquidMetalSwapTouchView { state, _, _ in
                if state == .began { driver.beginHold() }
                else if state == .ended || state == .cancelled || state == .failed { driver.endHold() }
            }
        }.clipShape(Capsule()).accessibilityElement(children:.ignore).accessibilityAddTraits(.isButton)
            .accessibilityLabel("Hold to swap \(C.tokens[role].amount) \(C.tokens[role].symbol) for \(C.tokens[1-role].amount) \(C.tokens[1-role].symbol)")
            .accessibilityHint("Hold for a second and a half. With VoiceOver, double tap.")
            .accessibilityAction { driver.swapNow() }
    }
}
struct LiquidMetalSwapArrow: Shape {
    func path(in rect: CGRect) -> Path {
        var p=Path(); let sx=rect.width/16, sy=rect.height/16
        p.move(to:CGPoint(x:8*sx,y:2.5*sy)); p.addLine(to:CGPoint(x:8*sx,y:13.5*sy))
        p.move(to:CGPoint(x:3.5*sx,y:9*sy)); p.addLine(to:CGPoint(x:8*sx,y:13.5*sy)); p.addLine(to:CGPoint(x:12.5*sx,y:9*sy)); return p
    }
}
struct LiquidMetalSwapCheck: Shape {
    func path(in rect: CGRect) -> Path {
        var p=Path(); p.move(to:CGPoint(x:rect.width*4/18,y:rect.height*9.5/18))
        p.addLine(to:CGPoint(x:rect.width*7.2/18,y:rect.height*12.7/18)); p.addLine(to:CGPoint(x:rect.width*14/18,y:rect.height*5.8/18)); return p
    }
}
