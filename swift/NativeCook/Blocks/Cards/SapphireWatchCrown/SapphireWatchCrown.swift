import SwiftUI

struct SapphireWatchCrown: View {
  static let loop = SapphireWatchCrownMotion.loop
  var autoPlay=false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock=SapphireWatchCrownClock()
  @State private var sizeAt=1
  @State private var winding=false
  private let art=SapphireWatchCrownArt.shared
  private var pinned: Double? {
    #if DEBUG
    if let value=UserDefaults.standard.object(forKey:"sapphireWatchCrownTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        let l=SapphireWatchCrownLayout(full.size.width,full.size.height,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom)
        TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.running))) { tick in
          let s=clock.sample(tick.date,autoPlay && !reduced,reduced,pinned)
          let f=s.frame
          ZStack(alignment:.topLeading) {
            art.background(l).allowsHitTesting(false).accessibilityHidden(true)
            Canvas { c,_ in
              c.translateBy(x:l.cx,y:l.y(355)); c.rotate(by:.degrees(f.flip*180))
              c.scaleBy(x:l.sx,y:l.sx); c.translateBy(x:-175,y:-175)
              c.clip(to:Path(CGRect(x:0,y:0,width:350,height:350)))
              c.stroke(art.arrows,with:.color(.white.opacity(0.7)),style:StrokeStyle(lineWidth:1.4,lineCap:.round))
            }.allowsHitTesting(false).accessibilityHidden(true)
            SapphireWatchCrownMetalView(layout:l,sample:s).frame(width:l.w,height:l.h)
            SapphireWatchCrownTouchView { phase,d,v in
              if phase == .began { clock.takeOver() }
              else if phase == .ended {
                if abs(d.width) > 40 || abs(v.dx) > 500 { clock.turnOver(f.flip > 0.5 ? 0:1,reduced) }
                clock.end()
              } else if phase == .cancelled { clock.end() }
            }
            .frame(width:l.r*2.4,height:l.r*2.4).contentShape(Circle())
            .accessibilityElement().accessibilityAddTraits(.isButton)
            .accessibilityLabel(f.flip > 0.5 ? "The watch from behind, its movement running":"The watch. Swipe to turn it over")
            .accessibilityAction { clock.flip(reduced) }
            .offset(x:l.cx-l.r*1.2,y:l.cy-l.r*1.2)
            SapphireWatchCrownTouchView { phase,d,_ in
              if phase == .began { clock.takeOver(); winding=false }
              else if phase == .changed {
                if !winding && hypot(d.width,d.height) >= 2 { winding=true; clock.windStart() }
                if winding { clock.wind(d.width,d.height) }
              } else { winding=false; clock.end() }
            }
            .frame(width:60,height:60).contentShape(Circle())
            .accessibilityElement().accessibilityLabel("Crown. Slide along it to set the time")
            .accessibilityAdjustableAction { direction in clock.adjust(direction == .increment) }
            .offset(x:l.crownX-30,y:l.crownY-30)
            controls(l,f)
            Circle().fill(.white.opacity(0.1)).overlay(Circle().strokeBorder(.white.opacity(0.6),lineWidth:(1.5*UIScreen.main.scale).rounded()/UIScreen.main.scale))
              .frame(width:44,height:44).scaleEffect(1-0.12*f.press).opacity(f.finger)
              .offset(x:l.finger(f).x-22,y:l.finger(f).y-22).allowsHitTesting(false).accessibilityHidden(true)
          }
          .frame(width:l.w,height:l.h,alignment:.topLeading).clipped().allowsHitTesting(pinned == nil).accessibilityHidden(pinned != nil)
        }
      }.ignoresSafeArea()
    }
    .background(Color(.sRGB,white:5.0/255,opacity:1).ignoresSafeArea()).preferredColorScheme(.dark)
    .onAppear { clock.reset(autoPlay && !reduced) }
    .onDisappear { clock.active(false); SapphireWatchCrownResources.shared.stop() }
    .onChange(of:scenePhase) { _,phase in clock.active(phase == .active); if phase != .active { SapphireWatchCrownResources.shared.stop() } }
    .onChange(of:reduced) { _,value in clock.reset(autoPlay && !value) }
    .onChange(of:autoPlay) { _,value in clock.reset(value && !reduced) }
  }
  private func controls(_ l: SapphireWatchCrownLayout,_ f: SapphireWatchCrownMotion.Frame) -> some View {
    ZStack(alignment:.topLeading) {
      SapphireWatchCrownPressControl(reduced:reduced) { art.control(0,l) }
        .frame(width:68,height:68*l.k).offset(x:26*l.sx,y:l.y(62)).accessibilityLabel("Back")
      SapphireWatchCrownPressControl(reduced:reduced) { art.control(1,l) }
        .frame(width:68,height:68*l.k).offset(x:307*l.sx,y:l.y(62)).accessibilityLabel("Cart, 1 item")
      ForEach(0..<4) { i in
        Button { clock.pick(i,reduced) } label: {
          ZStack {
            if f.selectedMetal == i { Circle().strokeBorder(.white.opacity(0.75),lineWidth:(1.2*UIScreen.main.scale).rounded()/UIScreen.main.scale).frame(width:36,height:36) }
            Circle().fill(Color(uiColor:SapphireWatchCrownArt.color(SapphireWatchCrownArt.metalHex[i])))
              .frame(width:f.selectedMetal == i ? 26:18,height:f.selectedMetal == i ? 26:18)
          }.frame(width:40,height:40).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityLabel(SapphireWatchCrownArt.metalNames[i])
          .accessibilityAddTraits(f.selectedMetal == i ? .isSelected:[])
          .offset(x:SapphireWatchCrownLayout.swatches[i]*l.sx-20,y:l.y(683)-20)
        Button { sizeAt=i } label: {
          Text(SapphireWatchCrownArt.sizeNames[i]).font(.custom(sizeAt == i ? "Poppins-SemiBold":"Poppins-Regular",fixedSize:sizeAt == i ? 13:11.5))
            .foregroundStyle(.white.opacity(sizeAt == i ? 1:0.32))
            .offset(x:-1/UIScreen.main.scale,y:sizeAt == i ? -1/UIScreen.main.scale:0)
            .frame(width:44,height:40).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityLabel("\(SapphireWatchCrownArt.sizeNames[i]) millimetres")
          .accessibilityAddTraits(sizeAt == i ? .isSelected:[])
          .offset(x:SapphireWatchCrownLayout.sizes[i]*l.sx-22,y:l.y(683)-20)
      }
      art.control(2,l).offset(x:26*l.sx,y:l.y(768)).accessibilityHidden(true)
      SapphireWatchCrownPressControl(reduced:reduced,scale:0.97) {
        art.control(3,l)
      }.offset(x:175*l.sx,y:l.y(768)).accessibilityLabel("Buy Online")
      // Static raster art has separate accessibility labels with the same order as the RN Text nodes.
      Color.clear.frame(width:140,height:24).accessibilityElement().accessibilityLabel("Store Online").allowsHitTesting(false).accessibilityAddTraits(.isHeader).offset(x:118*l.sx,y:l.y(77)-13)
      Color.clear.frame(width:140,height:24).accessibilityElement().accessibilityLabel("Easy Shopping").allowsHitTesting(false).offset(x:118*l.sx,y:l.y(112)-9)
      Color.clear.frame(width:140,height:24).accessibilityElement().accessibilityLabel("Maison Aurel (Swiss Made)").allowsHitTesting(false).offset(x:26*l.sx,y:l.y(580)-15)
      Color.clear.frame(width:140,height:24).accessibilityElement().accessibilityLabel("7.9/10").allowsHitTesting(false).offset(x:315*l.sx,y:l.y(580)-14)
      Color.clear.frame(width:140,height:24).accessibilityElement().accessibilityLabel("Color").allowsHitTesting(false).offset(x:26*l.sx,y:l.y(640)-10)
      Color.clear.frame(width:140,height:24).accessibilityElement().accessibilityLabel("Sizing").allowsHitTesting(false).offset(x:228*l.sx,y:l.y(640)-10)
      Color.clear.frame(width:140,height:24).accessibilityElement().accessibilityLabel("$ 450.23").allowsHitTesting(false).offset(x:26*l.sx,y:l.y(768))
    }
  }
}
