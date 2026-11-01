import SwiftUI

struct RibbonRoadOnboarding: View {
  static let loop=RibbonRoadOnboardingMotion.loop
  var autoPlay=false
  init(autoPlay: Bool=false) { self.autoPlay=autoPlay; _=RibbonRoadOnboardingResources.shared }
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock=RibbonRoadOnboardingClock()
  private var pinned: Double? {
    #if DEBUG
    if let value=UserDefaults.standard.object(forKey:"ribbonRoadOnboardingTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      let top=safe.safeAreaInsets.top, bottom=safe.safeAreaInsets.bottom
      GeometryReader { full in
        let size=full.size
        TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (reduced && !clock.running))) { timeline in
          let d=clock.sample(timeline.date,size,autoPlay,reduced,pinned)
          ZStack(alignment:.topLeading) {
            Color(uiColor:RibbonRoadOnboardingResources.color(0x18181b))
            Canvas(opaque:false,colorMode:.nonLinear) { context,_ in
              context.withCGContext { c in clock.art.ribbons(c,d,reduced) }
            }.accessibilityHidden(true)
            if pinned==nil { RibbonRoadOnboardingTouch { state,point in clock.touch(state,point,reduced) } }
            RibbonRoadOnboardingHeader()
              .frame(width:size.width-56,alignment:.leading)
              .offset(x:28,y:top+22).allowsHitTesting(false)
            VStack(spacing:0) {
              Spacer(minLength:0)
              Text("Your personal assistant to enrich your social life")
                .font(.custom("Manrope-Regular",fixedSize:15))
                .lineSpacing(RibbonRoadOnboardingResources.copyLeading)
                .padding(.vertical,RibbonRoadOnboardingResources.copyLeading/2)
                .offset(y:1.0/3)
                .foregroundStyle(Color.white.opacity(0.78))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal:false,vertical:true)
            }
            .frame(width:size.width-176,height:size.height-bottom-108)
            .offset(x:88).allowsHitTesting(false)
            HStack(spacing:10) {
              Button { if pinned==nil { clock.start(reduced) } } label: {
                HStack(spacing:8) {
                  Text("Get started").font(.custom("Manrope-SemiBold",fixedSize:17)).offset(x:-1.0/3,y:1.0/3)
                  Image(uiImage:RibbonRoadOnboardingResources.icons[1]).frame(width:18,height:18)
                }
                .foregroundStyle(Color(uiColor:RibbonRoadOnboardingResources.color(0x17171a)))
                .frame(width:size.width-180,height:56)
                .background(Color(uiColor:RibbonRoadOnboardingResources.color(0xffe14d)),in:RoundedRectangle(cornerRadius:28))
                .contentShape(Rectangle())
              }.scaleEffect(1-0.04*d.button).accessibilityLabel("Get started")
              Button {} label: {
                Text("@").font(.custom("Manrope-SemiBold",fixedSize:22)).offset(x:-1.0/3,y:-5.0/3).frame(width:56,height:56)
                  .background(Color(uiColor:RibbonRoadOnboardingResources.color(0x2a2a2f)),in:Circle()).contentShape(Rectangle())
              }.accessibilityLabel("Continue with email")
              Button {} label: {
                Image(uiImage:RibbonRoadOnboardingResources.icons[2]).frame(width:56,height:56)
                  .background(Color(uiColor:RibbonRoadOnboardingResources.color(0x2a2a2f)),in:Circle()).contentShape(Rectangle())
              }.accessibilityLabel("Continue with a passkey")
            }
            .foregroundStyle(.white)
            .buttonStyle(RibbonRoadOnboardingButtonStyle())
            .allowsHitTesting(pinned==nil)
            .position(x:size.width/2,y:size.height-bottom-52)
          }
          .frame(width:size.width,height:size.height,alignment:.topLeading)
        }
      }.ignoresSafeArea()
    }
    .preferredColorScheme(.dark)
    .onAppear { clock.active(true,reduced,pinned != nil) }
    .onDisappear { clock.active(false,reduced,pinned != nil) }
    .onChange(of:scenePhase) { _,phase in clock.active(phase == .active,reduced,pinned != nil) }
    .onChange(of:reduced) { _,value in clock.reset(); clock.active(scenePhase == .active,value,pinned != nil) }
  }
}
struct RibbonRoadOnboardingHeader: View {
  private var headFont: Font { .custom("Manrope-SemiBold",fixedSize:28) }
  var body: some View {
    VStack(alignment:.leading,spacing:0) {
      HStack(spacing:0) {
        Text("Try new ").offset(y:-4.0/3)
        Text("social").offset(y:-4.0/3).frame(height:34).padding(.horizontal,8)
          .frame(height:32).background(Color(uiColor:RibbonRoadOnboardingResources.color(0x3d34ec)),in:RoundedRectangle(cornerRadius:10))
      }.frame(height:36)
      Text("connections with").offset(y:-4.0/3).frame(height:36)
      HStack(spacing:0) {
        Text("our ").offset(y:-4.0/3)
        Image(uiImage:RibbonRoadOnboardingResources.icons[0]).frame(width:30,height:30)
          .background(Color(uiColor:RibbonRoadOnboardingResources.color(0xff6a3d)),in:Circle())
        Text(" assistance").offset(y:-4.0/3)
      }.frame(height:36)
    }
    .font(headFont).tracking(-0.4).foregroundStyle(.white)
    .accessibilityElement(children:.ignore)
    .accessibilityLabel("Try new social connections with our assistance")
    .accessibilityAddTraits(.isHeader)
  }
}
struct RibbonRoadOnboardingButtonStyle: ButtonStyle {
  func makeBody(configuration: Configuration) -> some View {
    configuration.label.scaleEffect(configuration.isPressed ? 0.96 : 1).opacity(configuration.isPressed ? 0.92 : 1)
  }
}
