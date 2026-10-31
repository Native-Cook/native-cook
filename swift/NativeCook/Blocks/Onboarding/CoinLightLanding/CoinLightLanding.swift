import SwiftUI

struct CoinLightLanding: View {
    static let loop = 8.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var clock=CoinLightLandingClock()
    private var pinned: Double? {
        #if DEBUG
        if let value=UserDefaults.standard.object(forKey:"coinLightLandingTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { outer in
            let top=outer.safeAreaInsets.top, bottom=outer.safeAreaInsets.bottom
            GeometryReader { proxy in
                let page=Image(uiImage:CoinLightLandingResources.shared.page(proxy.size,top,bottom))
                let art=CoinLightLandingArt(size:proxy.size,top:top,reduced:reduced,overviewPressed:clock.overviewPressed)
                TimelineView(.animation(paused:pinned != nil || phase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                    let f=clock.sample(timeline.date,proxy.size,top,autoPlay,reduced,pinned)
                    Canvas(opaque:true,colorMode:.nonLinear) { c,_ in
                        art.draw(c,f,page)
                    }
                    .accessibilityHidden(true)
                    .overlay(alignment:.topLeading) {
                        accessibilityAndTouch(proxy.size,top,bottom,Int(f.pay.rounded()),f.button)
                    }
                }
                .frame(width:proxy.size.width,height:proxy.size.height,alignment:.topLeading)
            }.ignoresSafeArea()
        }
        .background(CoinLightLandingResources.color(0x050506).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .persistentSystemOverlays(.visible)
        .onAppear { clock.active(phase == .active,reduced) }
        .onDisappear { clock.active(false,reduced) }
        .onChange(of:phase) { _,p in clock.active(p == .active,reduced) }
        .onChange(of:reduced) { _,r in clock.active(phase == .active,r) }
    }
    @ViewBuilder private func target(_ label:String,_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ action:(()->Void)?=nil) -> some View {
        let view=Color.clear.frame(width:w,height:h).contentShape(Rectangle())
            .accessibilityElement().accessibilityLabel(label).offset(x:x,y:y)
        if let action {
            view.accessibilityAddTraits(.isButton).accessibilityAction { if pinned == nil { action() } }
        } else { view }
    }
    private func accessibilityAndTouch(_ size:CGSize,_ top:Double,_ bottom:Double,_ paid:Int,_ button:Double) -> some View {
        ZStack(alignment:.topLeading) {
            target("Plinth",22,top+10,22,36)
            target("Menu",size.width-58,top+10,36,36,{})
            target("Get paid to create",22,top+58,330,80).accessibilityAddTraits(.isHeader)
            ForEach(0..<4) { i in
                target(["Sell your work on the Plinth Market","Keep 100% of your Market earnings","Refer creators and get up to 50% commission","$\(paid.formatted(.number.locale(Locale(identifier:"en_US")))) paid to creators in October"][i],22,top+156+Double(i)*40,size.width-44,24)
            }
            target("plinth.market",size.width/2-60,size.height-bottom-48,120,30)
            if pinned == nil {
                Button(action: { clock.become() }) { Color.clear.contentShape(Rectangle()) }.buttonStyle(.plain).accessibilityLabel("Become a creator").frame(width:164,height:44).scaleEffect(1+0.04*button).offset(x:22,y:top+332)
                Button(action: { clock.overview() }) { Color.clear.contentShape(Rectangle()) }.buttonStyle(CoinLightLandingOverviewStyle(clock:clock)).accessibilityLabel("Watch the overview").frame(width:176,height:44).scaleEffect(clock.overviewPressed ? 0.97 : 1).offset(x:196,y:top+332)
                CoinLightLandingTouch { state,p,v in clock.touch(state,p,v,size,top) }
                    .frame(width:180,height:180).offset(x:size.width/2-90,y:size.height*0.74-90)
                    .accessibilityElement().accessibilityAddTraits(.isButton)
                    .accessibilityLabel("Gold coin. Drag it around to light the page, or drop it on Become a creator.")
                    .accessibilityAction { clock.become() }
            }
        }.frame(width:size.width,height:size.height,alignment:.topLeading)
    }
}
