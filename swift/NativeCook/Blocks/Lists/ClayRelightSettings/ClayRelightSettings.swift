import SwiftUI
import UIKit

struct ClayRelightSettings: View {
  static let loop = ClayRelightSettingsMotion.loop
  typealias M = ClayRelightSettingsMotion
  typealias T = ClayRelightSettingsTheme
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var scale
  @StateObject private var state: ClayRelightSettingsState
  init(autoPlay: Bool = false) {
    _state = StateObject(wrappedValue:ClayRelightSettingsState(autoPlay:autoPlay))
  }
  var body: some View {
    GeometryReader { geo in
      TimelineView(.animation(paused:scenePhase != .active || (reduced && state.reducedRest))) { context in
        let now = state.time(context.date)
        let f = state.snapshot(now,reduced:reduced)
        screen(width:geo.size.width,top:geo.safeAreaInsets.top,f:f,drift:reduced ? 0 : (state.pin ?? now))
          .onChange(of:f.m > 0.47) { _, _ in
            if state.isUserDriven(now,reduced:reduced) && !reduced { UISelectionFeedbackGenerator().selectionChanged() }
          }
      }
      .frame(width:geo.size.width,height:geo.size.height+geo.safeAreaInsets.top+geo.safeAreaInsets.bottom,alignment:.topLeading)
      .offset(y:-geo.safeAreaInsets.top)
    }
    .onChange(of:scenePhase) { _, phase in state.setActive(phase == .active) }
    .task(id:"\(reduced)-\(state.revision)") {
      guard reduced else { return }
      let wait = max(0,state.reducedAnimationEnd-state.time())
      do { try await Task.sleep(for:.seconds(wait)) } catch { return }
      state.reducedRest = true
    }
  }
  private func screen(width: Double, top: Double, f: M.Frame, drift: Double) -> some View {
    let cardW = width-32, titleTop = top+8, heroTop = titleTop+60
    let sectionTop = heroTop+308+24, listTop = sectionTop+26
    let phase = 2 * Double.pi*drift/M.loop
    let sky = SIMD4<Float>(Float(f.m),Float(M.sunAngle(f.m)),Float(M.moonAngle(f.m)),Float(M.night(f.m)))
    return ZStack(alignment:.topLeading) {
      T.tone("page",f.m,f.theme).ignoresSafeArea()
      Text("Settings").font(T.font(34,"SemiBold")).tracking(-0.6)
        .foregroundStyle(T.tone("ink",f.m,f.theme)).frame(height:44)
        .offset(x:20,y:titleTop).accessibilityAddTraits(.isHeader)
      VStack(spacing:0) {
        ZStack(alignment:.topLeading) {
          Rectangle().fill(.white).colorEffect(ShaderLibrary.ClayRelightSettingsHero(
            .float4(Float(cardW),232,Float(scale),Float(phase)),.float4(sky.x,sky.y,sky.z,sky.w),
            .float4(Float(M.dusk(f.m)),Float(M.windows(f.m)),Float(M.lamp(f.m,0)),Float(phase)),
            .float4(0,0,Float(f.grab),0)))
          Text(M.clockText(f.m)).font(T.font(13,"SemiBold")).monospacedDigit()
            .foregroundStyle(T.tone("pillInk",f.m,f.theme)).frame(minWidth:60)
            .padding(.horizontal,10).frame(height:28)
            .background(T.tone("pill",f.m,f.theme),in:Capsule())
            .offset(x:12,y:12).accessibilityHidden(true)
          ClayRelightSettingsTouchView { event,delta,velocity in
            switch event {
            case .began: state.beginDrag(reduced:reduced)
            case .changed: state.drag(delta.width,span:cardW*0.85)
            case .ended,.cancelled,.failed: state.endDrag(velocity.dx,span:cardW*0.85,reduced:reduced)
            default: break
            }
          }
        }.frame(height:232)
        .accessibilityElement(children:.ignore)
        .accessibilityLabel("Appearance. A clay house on a hill; drag across the sky to move the sun")
        .accessibilityValue(f.theme > 0.5 ? "Night" : "Day")
        .accessibilityAdjustableAction { direction in
          state.setTarget(direction == .increment ? 1 : 0,reduced:reduced)
        }
        HStack(spacing:12) {
          VStack(alignment:.leading,spacing:2) {
            Text("Dark mode").font(T.font(16,"Medium")).frame(height:22)
              .foregroundStyle(T.tone("ink",f.m,f.theme))
            let k = M.smooth(0.56,0.66,f.m)
            ZStack(alignment:.leading) {
              Text("Off · sunset at 6:50 PM").opacity(1-M.smooth(0,0.6,k)).offset(y:-6*k)
              Text("On until sunrise, 6:20 AM").opacity(M.smooth(0.4,1,k)).offset(y:6*(1-k))
            }.font(T.font(13)).foregroundStyle(T.tone("mute",f.m,f.theme)).frame(height:18)
          }.frame(maxWidth:.infinity,alignment:.leading)
          switchView(f)
        }.padding(.horizontal,20).frame(height:76)
      }.frame(width:cardW,height:308)
        .background(T.tone("card",f.m,f.theme))
        .clipShape(RoundedRectangle(cornerRadius:28,style:.continuous)).offset(x:16,y:heroTop)
      Text("GENERAL").font(T.font(13,"Medium")).tracking(0.6).frame(height:18)
        .foregroundStyle(T.tone("mute",f.m,f.theme)).offset(x:32,y:sectionTop)
      ZStack(alignment:.topLeading) {
        T.tone("card",f.m,f.theme)
        Rectangle().fill(.white).frame(width:64,height:300).colorEffect(ShaderLibrary.ClayRelightSettingsIcon(
          .float4(64,300,Float(scale),60),.float4(sky.x,sky.y,sky.z,sky.w),
          .float4(Float(M.lamp(f.m,0)),Float(M.lamp(f.m,1)),Float(M.lamp(f.m,2)),Float(M.lamp(f.m,3))),
          .float(Float(M.lamp(f.m,4))),.float(Float(M.dusk(f.m))),.float(20)))
        ForEach(0..<5) { i in row(i,f:f).frame(width:cardW,height:60).offset(y:Double(i)*60) }
      }.frame(width:cardW,height:300).clipShape(RoundedRectangle(cornerRadius:28,style:.continuous))
        .offset(x:16,y:listTop)
      let fx = f.fingerAt == 1 ? 16+cardW*(0.78-0.4*f.fingerX) : 16+cardW-16-25.5
      let fy = f.fingerAt == 1 ? heroTop+232*0.36 : heroTop+232+38
      Circle().fill(.white.opacity(0.4)).overlay(Circle().stroke(.white.opacity(0.8),lineWidth:1.5))
        .frame(width:44,height:44).shadow(color:.black.opacity(0.2),radius:3,y:1)
        .scaleEffect(1-0.12*f.press).opacity(0.85*f.finger).offset(x:fx-22,y:fy-22)
        .allowsHitTesting(false).accessibilityHidden(true)
    }.frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.topLeading)
      .preferredColorScheme(f.theme > 0.5 ? .dark : .light)
  }
  private func switchView(_ f: M.Frame) -> some View {
    ZStack(alignment:.leading) {
      // Interpolating in sRGB gamma 2.2 matches Reanimated's track interpolation.
      let off = UIColor(T.tone("trackOff",f.m,f.theme))
      let on = UIColor(red:91/255,green:91/255,blue:214/255,alpha:1)
      let c = trackColour(off,on,f.knob)
      Capsule().fill(c)
      Circle().fill(.white).frame(width:27,height:27)
        .shadow(color:.black.opacity(0.18),radius:3,y:2)
        .scaleEffect(x:1+0.12*f.press,y:1).offset(x:2+20*f.knob)
    }.frame(width:51,height:31)
      .overlay {
      ClayRelightSettingsTouchView { event,delta,_ in
        switch event {
        case .began: state.switchPress(true,reduced:reduced)
        case .ended:
          if state.time()-state.lastTouch < 0.8 && hypot(delta.width,delta.height) <= 10 {
            state.setTarget(f.on == 1 ? 0 : 1,reduced:reduced)
            UIImpactFeedbackGenerator(style:.light).impactOccurred()
          }
          state.switchPress(false,reduced:reduced)
        case .cancelled,.failed: state.switchPress(false,reduced:reduced)
        default: break
        }
      }.frame(width:75,height:55)
    }.scaleEffect(reduced ? 1 : 1-0.04*f.press)
      .accessibilityElement(children:.ignore).accessibilityLabel("Dark mode")
      .accessibilityValue(f.theme > 0.5 ? "On" : "Off")
      .accessibilityRepresentation {
        Toggle("Dark mode",isOn:Binding(get:{ f.theme > 0.5 },set:{ _ in
          state.setTarget(f.on == 1 ? 0 : 1,reduced:reduced)
        }))
      }
  }
  private func trackColour(_ a: UIColor, _ b: UIColor, _ x: Double) -> Color {
    var ar: CGFloat = 0, ag: CGFloat = 0, ab: CGFloat = 0, aa: CGFloat = 0
    var br: CGFloat = 0, bg: CGFloat = 0, bb: CGFloat = 0, ba: CGFloat = 0
    a.getRed(&ar,green:&ag,blue:&ab,alpha:&aa); b.getRed(&br,green:&bg,blue:&bb,alpha:&ba)
    func c(_ u: Double, _ v: Double) -> Double { pow(pow(u,2.2)*(1-x)+pow(v,2.2)*x,1/2.2) }
    return Color(.sRGB,red:c(ar,br),green:c(ag,bg),blue:c(ab,bb))
  }
  private func row(_ index: Int, f: M.Frame) -> some View {
    let r = T.rows[index], k = M.lamp(f.m,index)
    return ZStack(alignment:.bottomLeading) {
      HStack(spacing:12) {
        Text(r.0).font(T.font(16,"Medium")).lineLimit(1)
          .foregroundStyle(T.tone("ink",f.m,f.theme)).frame(maxWidth:.infinity,alignment:.leading)
        HStack(spacing:8) {
          if !r.1.isEmpty {
            ZStack(alignment:.trailing) {
              Text(r.1.count > r.2.count ? r.1 : r.2).hidden()
              Text(r.1).opacity(1-M.smooth(0,0.6,k)).offset(y:-5*k)
              Text(r.2).opacity(M.smooth(0.4,1,k)).offset(y:5*(1-k))
            }.font(T.font(15)).foregroundStyle(T.tone("mute",f.m,f.theme))
          }
          Path { p in p.move(to:CGPoint(x:1.5,y:1.5)); p.addLine(to:CGPoint(x:6.5,y:6.5)); p.addLine(to:CGPoint(x:1.5,y:11.5)) }
            .stroke(Color(red:154/255,green:160/255,blue:174/255),
                    style:StrokeStyle(lineWidth:1.8,lineCap:.round,lineJoin:.round)).frame(width:8,height:13)
        }
      }.padding(.leading,68).padding(.trailing,16).frame(height:60)
      if index < 4 { T.tone("line",f.m,f.theme).frame(height:1/scale).padding(.leading,68) }
    }.accessibilityElement(children:.ignore).accessibilityLabel(r.0).accessibilityAddTraits(.isButton)
  }
}
