import SwiftUI

struct GlassTilesFuse: View {
    static let loop = GlassTilesFuseMotion.loop
    var autoPlay: Bool
    init(autoPlay: Bool = false) { self.autoPlay=autoPlay }
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var driver=GlassTilesFuseDriver()
    private var pin: Double? {
        #if DEBUG
        if UserDefaults.standard.object(forKey:"ncPinTime") != nil { return UserDefaults.standard.double(forKey:"ncPinTime") }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { geo in
                let l=GlassTilesFuseMotion.Layout(geo.size.width,geo.size.height,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom)
                TimelineView(.animation(paused:scenePhase != .active || !driver.needsFrames(auto:autoPlay,reduced:reduced))) { context in
                    let _=context.date
                    let f=driver.frame(driver.now,auto:autoPlay,reduced:reduced,pin:pin)
                    screen(l,f)
                        .onChange(of:f.link>0.86 ? 2:f.link>0.4 ? 1:0) { _,_ in
                            driver.haptics(f.link,feedback:!driver.isAutomatic(auto:autoPlay,reduced:reduced))
                        }
                }
            }.ignoresSafeArea()
        }.preferredColorScheme(.dark)
    }
    private func screen(_ l: GlassTilesFuseMotion.Layout,_ f: GlassTilesFuseDriver.Frame) -> some View {
        typealias C = GlassTilesFuseTheme
        var r=GlassTilesFuseMotion.rest(l,f.restTime)
        r.gx += f.dragX; r.gy += f.dragY; r.ga += f.dragX*0.0015
        let p=GlassTilesFuseMotion.linked(l,r,f.link)
        let connected=f.link>0.56
        return ZStack(alignment:.topLeading) {
            C.page
            Text("Connections").font(C.font("Bold",30)).tracking(-0.8).foregroundStyle(C.title)
                .accessibilityAddTraits(.isHeader).offset(x:20,y:l.titleTop)
            Text("Let the assistant use what you already have.").font(C.font("Regular",15)).foregroundStyle(C.rowSub)
                .offset(x:20,y:l.titleTop+40)
            ForEach(0..<4,id:\.self) { i in
                HStack(spacing:14) {
                    GlassTilesFuseRowIcon(index:i).frame(width:36,height:36)
                    VStack(alignment:.leading,spacing:2) {
                        Text(C.rows[i].0).font(C.font("SemiBold",16)).foregroundStyle(C.rowName)
                        Text(C.rows[i].1).font(C.font("Regular",13)).foregroundStyle(C.rowSub)
                    }.frame(maxWidth:.infinity,alignment:.leading)
                    Text("Connect").font(C.font("SemiBold",13)).foregroundStyle(C.pillInk).padding(.horizontal,14)
                        .frame(height:30).background(C.pill,in:Capsule())
                }.frame(width:l.w-40,height:l.rowH).offset(x:20,y:l.rowsTop+Double(i)*l.rowH)
            }
            Color.black.opacity(0.42).allowsHitTesting(false)
            ZStack(alignment:.topLeading) {
                C.sheet
                Capsule().fill(C.grabber).frame(width:36,height:5).offset(x:l.w/2-18,y:8)
                // The RN Canvas is opaque and is above the grabber in the original layer order.
                Rectangle().fill(C.sheet).colorEffect(ShaderLibrary.GlassTilesFuseMain(
                    .float4(Float(p.wx+f.recoil),Float(p.wy-l.sheetTop),Float(l.white*p.size),Float(p.wa)),
                    .float4(Float(p.gx+f.pull),Float(p.gy+f.pullY-l.sheetTop-6*f.lift),Float(l.glass*p.size*(1+0.06*f.lift)),Float(p.ga+f.pull*0.0008)),
                    .float(p.fuse*f.stick),.float(p.seam),.float(p.check),.float(3),.float(f.lift)))
                    .frame(width:l.w,height:l.stageH+12).allowsHitTesting(false)
                GlassTilesFuseTouchView { state,_,translation,velocity in
                    driver.event(state,translation,velocity,auto:autoPlay,reduced:reduced,pin:pin)
                }.frame(width:l.w,height:max(0,l.stageH-24)).offset(y:24)
                    .accessibilityElement().accessibilityLabel("Mailbox tile")
                    .accessibilityHint(connected ? "":"Drag to move it. Once connecting, pull it out to finish.")
                VStack(spacing:8) {
                    Text(connected ? "Mailbox connected":"Mailbox").font(C.font("SemiBold",18)).tracking(-0.3).foregroundStyle(C.title)
                    Text(connected ? "Mina’s inbox is ready to use across your chats and apps.":"Connect a Mailbox account for Orbit to use across your chats and apps.")
                        .font(C.font("Regular",15)).lineSpacing(20-C.uiFontLineHeight).foregroundStyle(C.body).multilineTextAlignment(.center)
                }.frame(width:l.w-64).offset(x:32,y:l.textTop-l.sheetTop)
                Button { driver.button(auto:autoPlay,reduced:reduced,pin:pin) } label: {
                    Text(connected ? "Done":"Connect Mailbox").font(C.font("SemiBold",16)).tracking(-0.2)
                        .foregroundStyle(C.buttonInk).frame(width:l.w-64,height:52).background(C.button,in:Capsule())
                }.buttonStyle(GlassTilesFusePressStyle(driver:driver)).scaleEffect(1-0.04*f.press)
                    .offset(x:32,y:l.buttonTop-l.sheetTop)
            }.frame(width:l.w,height:l.h-l.sheetTop+40,alignment:.topLeading)
                .clipShape(UnevenRoundedRectangle(topLeadingRadius:28,topTrailingRadius:28))
                .accessibilityAddTraits(.isModal).offset(y:l.sheetTop)
            Circle().fill(Color.white.opacity(0.35)).overlay(Circle().strokeBorder(Color.white.opacity(0.7),lineWidth:1.5))
                .frame(width:44,height:44).scaleEffect(1-0.12*f.press).opacity(0.85*f.finger)
                .offset(x:(f.fingerOn==1 ? p.gx+f.pull:l.w/2)-22,y:(f.fingerOn==1 ? p.gy:l.buttonTop+26)-22)
                .allowsHitTesting(false).accessibilityHidden(true)
        }.frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
    }
}

private struct GlassTilesFusePressStyle: ButtonStyle {
    let driver: GlassTilesFuseDriver
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of:configuration.isPressed) { _,down in driver.press(down) }
    }
}

extension GlassTilesFuseTheme {
    static var uiFontLineHeight: Double { Double(UIFont(name:names["Regular"] ?? "Inter-Regular",size:15)?.lineHeight ?? 18) }
}
