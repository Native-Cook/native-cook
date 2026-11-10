import SwiftUI
import UIKit

struct AppIconAtelier: View {
  static let loop=AppIconAtelierMotion.loop
  var autoPlay=false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock=AppIconAtelierClock()
  private var pinned: Double? {
    #if DEBUG
    if let value=UserDefaults.standard.object(forKey:"appIconAtelierTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      let safe=UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow)?.safeAreaInsets ?? .zero
      let L=AppIconAtelierTheme.Layout(proxy.size.width,proxy.size.height,max(proxy.safeAreaInsets.top,safe.top),max(proxy.safeAreaInsets.bottom,safe.bottom))
      TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
        let f=clock.sample(timeline.date,autoPlay && !reduced,pinned)
        AppIconAtelierPage(L:L,f:f,clock:clock,reduced:reduced,pinned:pinned != nil)
      }
      .frame(width:proxy.size.width,height:proxy.size.height,alignment:.topLeading)
    }
    // The demo opens while the scene is still inactive, and the paused timeline did not start again once it was
    // active (the page stayed on its first frame): it is made anew when the scene becomes active.
    .id(scenePhase == .active)
    .ignoresSafeArea()
    .background(AppIconAtelierTheme.color(0xF7F5FB))
    .preferredColorScheme(.light)
    .onAppear { _=AppIconAtelierResources.shared; clock.configure(reduced) }
    .onChange(of: reduced) { _, value in clock.configure(value) }
    .onChange(of: scenePhase) { _,_ in clock.active() }
  }
}

struct AppIconAtelierPage: View {
  typealias H=AppIconAtelierTheme
  let L: H.Layout
  let f: AppIconAtelierMotion.Frame
  let clock: AppIconAtelierClock
  let reduced,pinned: Bool
  private var current: Bool { f.shown==f.applied }
  private var cta: String { clock.updated ? "Icon Updated" : current ? "Current Icon" : "Use This Icon" }
  private let transition=Animation.timingCurve(0.23,1,0.32,1,duration:0.22)
  private func text(_ value: String,_ face: String,_ size: Double,_ color: Color) -> Text {
    Text(value).font(AppIconAtelierResources.shared.font(face,size)).foregroundColor(color)
  }
  var body: some View {
    ZStack(alignment:.topLeading) {
      AppIconAtelierRenderer(layout:L,frame:f).allowsHitTesting(false)
      Image(uiImage:AppIconAtelierResources.shared.art(L)).resizable().frame(width:L.w,height:L.h).allowsHitTesting(false)
      Circle().stroke(H.ink,lineWidth:1.5).frame(width:58,height:58).position(x:L.chip(f.sel),y:L.chipsTop+26).allowsHitTesting(false)
      if !pinned {
        AppIconAtelierTouch { event,dx,vx in clock.hero(event,dx,vx,L.heroS) }
          .frame(width:L.w,height:L.eyebrowTop-L.barTop-44).offset(y:L.barTop+44)
          .accessibilityElement(children:.ignore)
          .accessibilityLabel("Amora app icon")
          .accessibilityValue(H.glasses[f.shown].name)
          .accessibilityHint("Swipe left or right to change the glass. Touch and hold to preview it on the Home Screen.")
          .accessibilityAdjustableAction { direction in clock.step(direction == .increment ? 1 : -1) }
      }
      text("App Icon","Inter-SemiBold",17,H.ink).frame(width:L.w,height:44).offset(y:L.barTop+1.0/3).accessibilityAddTraits(.isHeader)
      Button {} label: {
        HStack(spacing:6) {
          Path { p in p.move(to:CGPoint(x:10,y:2)); p.addLine(to:CGPoint(x:2,y:10)); p.addLine(to:CGPoint(x:10,y:18)) }
            .stroke(H.ink,style:StrokeStyle(lineWidth:2.2,lineCap:.round,lineJoin:.round)).frame(width:12,height:20)
          text("Settings","Inter-Regular",17,H.ink)
        }.padding(.horizontal,8).frame(height:44)
      }.buttonStyle(AppIconAtelierPressStyle(reduced:reduced)).offset(x:8,y:L.barTop+1.0/3).accessibilityLabel("Back to Settings")
      ZStack(alignment:.topLeading) {
        text("AMORA · ICON \(f.shown+1) OF 5","Inter-Medium",12,H.mute).tracking(1.2).frame(width:L.w,height:16).offset(y:L.eyebrowTop)
        ForEach(0..<5) { i in
          text(H.glasses[i].name,"InstrumentSerif-Regular",38,H.ink)
            .frame(width:L.w-32,height:42).offset(x:16,y:L.nameTop-11.0/3+(i==f.shown || reduced ? 0 : 6))
            .opacity(i==f.shown ? 1 : 0).animation(pinned ? nil : transition,value:f.shown).accessibilityHidden(i != f.shown)
          text(H.glasses[i].note,"Inter-Regular",15,H.mute)
            .frame(width:L.w-32,height:20).offset(x:16,y:L.noteTop+(i==f.shown || reduced ? 0 : 6))
            .opacity(i==f.shown ? 1 : 0).animation(pinned ? nil : transition,value:f.shown).accessibilityHidden(i != f.shown)
          Button { clock.pick(i) } label: { Circle().fill(Color.clear).frame(width:64,height:64).contentShape(Circle()) }
            .buttonStyle(.plain).position(x:L.chip(Double(i)),y:L.chipsTop+26)
            .accessibilityLabel(H.glasses[i].name).accessibilityAddTraits(i==f.shown ? [.isSelected] : [])
        }
      }.frame(width:L.w,height:L.h,alignment:.topLeading).opacity(1-0.65*AppIconAtelierMotion.clamp(f.peek))
      text("Home Screen","Inter-SemiBold",13,H.mute).tracking(0.2).frame(height:18).offset(x:L.cardX+4,y:L.homeLabelTop+1.0/3)
      ZStack(alignment:.topLeading) {
        text("9:41","Inter-SemiBold",13,.white).offset(x:20,y:14)
        ForEach(0..<4) { i in
          text(["Notes","Tides","Amora","Ledger"][i],"Inter-Medium",11,.white)
            .frame(width:80).shadow(color:H.color(0x281450,0.35),radius:3,x:0,y:0.5)
            .offset(x:L.appX[i]-L.cardX-40,y:L.appsY-L.cardY+36)
        }
      }.frame(width:L.cardW,height:168,alignment:.topLeading).offset(x:L.cardX,y:L.cardY)
        .allowsHitTesting(false).accessibilityElement(children:.ignore).accessibilityLabel("Home Screen preview. Amora uses \(H.glasses[f.applied].name).")
      Button { clock.apply() } label: {
        text(cta,"Inter-SemiBold",17,current && !clock.updated ? H.mute : H.color(0xFBFAFE)).offset(x:-1.0/3,y:1.0/3)
          .frame(width:L.w-32,height:56)
          .background { if !current || clock.updated { Capsule().fill(H.ink) } }
          .overlay { if current && !clock.updated { Capsule().strokeBorder(H.color(0x1C1630,0.14),lineWidth:1) } }
      }.buttonStyle(AppIconAtelierPressStyle(reduced:reduced)).disabled(current && !clock.updated).offset(x:16,y:L.ctaTop).accessibilityLabel(cta)
      if f.finger>0 {
        let x=f.fingerAt==0 ? L.heroX+f.fingerX*L.heroS : L.chip(f.fingerX)
        let y=f.fingerAt==0 ? L.heroY+0.18*L.heroS : L.chipsTop+26
        let r=22*(1-0.12*f.fingerPress)
        Circle().fill(H.color(0x1C1630,0.1*f.finger)).overlay { Circle().stroke(H.color(0x1C1630,0.32*f.finger),lineWidth:1.5) }.frame(width:r*2,height:r*2).position(x:x,y:y).allowsHitTesting(false).accessibilityHidden(true)
      }
    }.frame(width:L.w,height:L.h,alignment:.topLeading).clipped().allowsHitTesting(!pinned)
  }
}
struct AppIconAtelierPressStyle: ButtonStyle {
  let reduced: Bool
  func makeBody(configuration: Configuration) -> some View {
    configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
      .opacity(configuration.isPressed && reduced ? 0.72 : 1)
      .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:configuration.isPressed)
  }
}
