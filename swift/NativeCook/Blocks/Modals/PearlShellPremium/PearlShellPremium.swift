import SwiftUI

struct PearlShellPremium: View {
  static let loop=PearlShellPremiumMotion.loop
  var autoPlay=false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.displayScale) private var displayScale
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock=PearlShellPremiumClock()
  @State private var plan=0
  private let resources=PearlShellPremiumResources.shared
  private var pinned: Double? {
    #if DEBUG
    if let value=UserDefaults.standard.object(forKey:"pearlShellPremiumTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        let l=PearlShellPremiumTheme.Layout(full.size.width,full.size.height,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom)
        TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (reduced && !clock.running))) { tick in
          let f=clock.sample(tick.date,pinned)
          ZStack(alignment:.topLeading) {
            PearlShellPremiumSurface(layout:l,frame:f).frame(width:l.w,height:l.h)
            if pinned==nil {
              PearlShellPremiumTouchSurface { state,translation,velocity in
                clock.touch(state,translation,velocity,l.heroS)
              }
              .frame(width:l.heroS*2.6,height:l.heroS*2.1)
              .position(x:l.heroX,y:l.heroY-l.heroS*0.45)
              .accessibilityElement(children:.ignore)
              .accessibilityAddTraits(.isButton)
              .accessibilityLabel("A pearl shell")
              .accessibilityHint("Lift the shell's lip to open it")
              .accessibilityAction { clock.accessibilityToggle() }
            }
            controls(l,f).allowsHitTesting(pinned==nil)
            Canvas { context,_ in
              let r=22*(1-0.12*f.fingerPress)
              let y=l.heroY-l.heroS*(0.05+0.75*f.fingerY)
              let path=Path(ellipseIn:CGRect(x:l.heroX-r,y:y-r,width:r*2,height:r*2))
              context.fill(path,with:.color(PearlShellPremiumTheme.ink.opacity(0.14*f.finger)))
              context.stroke(path,with:.color(PearlShellPremiumTheme.ink.opacity(0.5*f.finger)),lineWidth:1.5)
            }.allowsHitTesting(false).accessibilityHidden(true)
          }
          .frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
        }
      }.ignoresSafeArea()
    }
    .background(PearlShellPremiumTheme.page.ignoresSafeArea())
    .preferredColorScheme(.dark)
    .onAppear { clock.reset(autoPlay,reduced) }
    .onChange(of:scenePhase) { _,_ in clock.activeChanged() }
    .onChange(of:reduced) { _,r in clock.configure(autoPlay,r) }
    .onChange(of:autoPlay) { _,a in clock.configure(a,reduced) }
  }
  private func label(_ text: String, _ face: String, _ size: Double, _ color: Color = PearlShellPremiumTheme.ink) -> some View {
    Text(text).font(resources.font(face,size)).foregroundStyle(color).fixedSize()
  }
  @ViewBuilder private func controls(_ l: PearlShellPremiumTheme.Layout, _ f: PearlShellPremiumMotion.Frame) -> some View {
    let h=PearlShellPremiumTheme.self
    HStack(spacing:0) {
      Button {} label: {
        h.closePath
          .stroke(h.ink,style:StrokeStyle(lineWidth:1.8,lineCap:.round)).frame(width:44,height:44)
      }.accessibilityLabel("Close")
      Spacer(minLength:0)
      Button {} label: { label("Restore","Inter-Medium",15,h.mute).offset(y:-1/displayScale).padding(.horizontal,8).frame(height:44) }
        .accessibilityLabel("Restore purchases")
    }
    .buttonStyle(PearlShellPremiumPressStyle(reduced:reduced))
    .frame(width:l.w-16,height:44).offset(x:8,y:l.barTop)
    // RCTText centers Instrument Serif in its explicit 44 pt line box; SwiftUI includes 8 pt more leading.
    label("Tidal Plus","InstrumentSerif-Regular",40).frame(width:l.w,height:44).offset(y:l.titleTop-4).accessibilityAddTraits(.isHeader)
    ForEach(0..<3,id:\.self) { i in
      let k=PearlShellPremiumMotion.clamp(f.glow*1.5-Double(i)*0.2)
      HStack(spacing:8) {
        Circle().fill(h.pearl).frame(width:6,height:6).opacity(0.35+0.65*k)
        label(h.features[i],"Inter-Regular",15).offset(y:-1/displayScale)
      }
      .frame(width:l.w,height:22).opacity(0.55+0.45*k)
      .offset(x:4*(1-k),y:l.featTop+Double(i)*30)
    }
    HStack(spacing:12) {
      ForEach(0..<2,id:\.self) { i in
        Button { plan=i;clock.selectPlan() } label: {
          VStack(alignment:.leading,spacing:2) {
            HStack(spacing:0) {
              label(i==0 ? "Yearly" : "Monthly","Inter-Medium",14,h.mute)
              Spacer(minLength:0)
              if i==0 { label("Save 44%","Inter-SemiBold",11,h.pearl).tracking(0.3) }
            }
            label(i==0 ? "$39.99" : "$5.99","Inter-SemiBold",18)
          }
          .padding(.horizontal,17).frame(width:(l.w-44)/2,height:64,alignment:.leading)
          .background(h.ink.opacity(i==plan ? 0.14 : 0.06),in:RoundedRectangle(cornerRadius:20,style:.circular))
          .overlay(RoundedRectangle(cornerRadius:20,style:.circular).strokeBorder(h.ink.opacity(i==plan ? 0.55 : 0.14),lineWidth:1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(i==0 ? "Yearly, $39.99, Save 44%" : "Monthly, $5.99")
        .accessibilityValue(i==plan ? "Selected" : "Not selected")
        .accessibilityAddTraits(i==plan ? [.isSelected] : [])
      }
    }.frame(width:l.w-32,height:64).offset(x:16,y:l.plansTop)
    Button {} label: {
      label("Start Free Week","Inter-SemiBold",17,h.page).offset(x:-1/displayScale,y:1/displayScale)
        .frame(width:l.w-32,height:56).background(h.ink,in:Capsule(style:.circular))
    }
    .buttonStyle(PearlShellPremiumPressStyle(reduced:reduced)).accessibilityLabel("Start free week")
    .offset(x:16,y:l.ctaTop)
    label(plan==0 ? "Then $39.99 a year. Cancel anytime." : "Then $5.99 a month. Cancel anytime.","Inter-Regular",13,h.mute)
      .offset(y:1/displayScale).frame(width:l.w,height:18).offset(y:l.fineTop)
  }
}
