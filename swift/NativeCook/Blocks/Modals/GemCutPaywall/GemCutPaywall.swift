import SwiftUI

struct GemCutPaywall: View {
    static let loop = GemCutPaywallMotion.loop
    var autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var driver = GemCutPaywallDriver()
    init(autoPlay: Bool = false) { self.autoPlay=autoPlay }
    private var pin: Double? {
        #if DEBUG
        if UserDefaults.standard.object(forKey:"ncPinTime") != nil { return UserDefaults.standard.double(forKey:"ncPinTime") }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { outer in
            let safe=outer.safeAreaInsets
            GeometryReader { geo in
                let l=GemCutPaywallTheme.layout(geo.size.width,geo.size.height,safe.top,safe.bottom)
                TimelineView(.animation(paused:scenePhase != .active || (reduced && !driver.isMoving))) { timeline in
                    let now=timeline.date.timeIntervalSinceReferenceDate
                    let f=driver.sample(now,pin:pin)
                    ZStack(alignment:.topLeading) {
                        GemCutPaywallTheme.page
                        Rectangle().fill(.white).colorEffect(ShaderLibrary.GemCutPaywallMain(
                            .float2(l.gemX,l.gemY),.float(l.gemR),.float(f.cut),.float(f.yaw),.float(f.pitch),.float(f.spark),.float2(l.gemX,l.floorY),.float(displayScale)))
                            .allowsHitTesting(false)
                        Canvas { context,_ in GemCutPaywallArtwork.draw(&context,l,f,driver,now,reduced) }.allowsHitTesting(false)
                        GemCutPaywallTouchView { state,point,delta,v in driver.touch(state,point,delta,v,false,l) }
                            .frame(width:l.w,height:max(0,l.titleTop))
                            .accessibilityElement().accessibilityLabel(["A frosted rough stone","A gem with a single cut","A round brilliant diamond"][driver.tier])
                            .accessibilityHint("Drag to turn the gem").accessibilityAddTraits(.isImage)
                        GemCutPaywallTouchView { state,point,delta,v in driver.touch(state,point,delta,v,true,l) }
                            .frame(width:l.planW,height:48).offset(x:16,y:l.planTop)
                            .accessibilityElement().accessibilityLabel("Plan")
                            .accessibilityValue("\(GemCutPaywallTheme.names[driver.tier]), $\(GemCutPaywallTheme.prices[driver.tier]) a month")
                            .accessibilityAdjustableAction { direction in
                                let t=Date.timeIntervalSinceReferenceDate; driver.takeover(t)
                                driver.go(max(0,min(2,driver.tier+(direction == .increment ? 1 : -1))),t)
                            }
                        Button {} label: {
                            Path { p in p.move(to:CGPoint(x:5,y:5)); p.addLine(to:CGPoint(x:15,y:15)); p.move(to:CGPoint(x:15,y:5)); p.addLine(to:CGPoint(x:5,y:15)) }
                                .stroke(GemCutPaywallTheme.ink,style:StrokeStyle(lineWidth:2,lineCap:.round)).frame(width:20,height:20).frame(width:44,height:44).contentShape(Rectangle())
                        }.accessibilityLabel("Close").offset(x:8,y:l.barTop)
                        Button {} label: { Text("Restore").font(GemCutPaywallTheme.font(15,"Medium")).foregroundColor(GemCutPaywallTheme.mute).padding(.horizontal,4).frame(width:76,height:44,alignment:.trailing) }.accessibilityLabel("Restore purchases").offset(x:l.w-16-76,y:l.barTop)
                        Button {} label: {
                            let freeOpacity=driver.freeOpacity(now)
                            ZStack {
                                Text("Continue with Free").opacity(freeOpacity)
                                Text("Start 7-day free trial").opacity(1-freeOpacity)
                            }.font(GemCutPaywallTheme.font(17,"SemiBold")).foregroundColor(GemCutPaywallTheme.ctaInk)
                                .frame(width:l.w-32,height:56).background(.white,in:Capsule())
                        }.accessibilityLabel(driver.tier == 0 ? "Continue with Free" : "Start 7-day free trial").offset(x:16,y:l.ctaTop)
                        Color.clear.frame(width:l.w,height:34).offset(y:l.titleTop)
                            .accessibilityElement().accessibilityLabel("Facet \(GemCutPaywallTheme.names[driver.tier])")
                        Color.clear.frame(width:l.w,height:22).offset(y:l.priceTop)
                            .accessibilityElement().accessibilityLabel("$\(GemCutPaywallTheme.prices[driver.tier]) a month")
                        Color.clear.frame(width:l.w-32,height:18).offset(x:16,y:l.noteTop)
                            .accessibilityElement().accessibilityLabel(GemCutPaywallTheme.notes[driver.tier])
                        ForEach(0..<3) { i in
                            Color.clear.frame(width:l.w-64,height:24).offset(x:32,y:l.featTop+Double(i)*36)
                                .accessibilityElement().accessibilityLabel("\(GemCutPaywallTheme.features[i]), \(driver.tier >= (i == 2 ? 2 : 1) ? "included" : "not included")")
                        }
                    }.buttonStyle(GemCutPaywallPressStyle(reduced:reduced)).frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
                }
            }.ignoresSafeArea()
        }
        .onAppear { driver.configure(autoPlay,reduced) }
        .onChange(of:reduced) { _,v in driver.configure(autoPlay,v) }
        .onChange(of:scenePhase) { _,phase in driver.suspend(phase == .active) }
        .onChange(of:autoPlay) { _,v in driver.configure(v,reduced) }
        .preferredColorScheme(.dark)
    }
}

private struct GemCutPaywallPressStyle: ButtonStyle {
    var reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
            .opacity(configuration.isPressed && reduced ? 0.72 : 1)
            .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:configuration.isPressed)
    }
}
