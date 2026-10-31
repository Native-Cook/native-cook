import SwiftUI

struct JellyScaleExchange: View {
  static let loop = JellyScaleExchangeMotion.loop
  let autoPlay: Bool
  init(autoPlay: Bool = false) { self.autoPlay = autoPlay }
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var displayScale
  @StateObject private var driver = JellyScaleExchangeDriver()
  @State private var touchButton = -1
  @State private var touchStart = CGPoint.zero
  @State private var touchStartDate = Date()
  @State private var tapCancelled = false
  private typealias M = JellyScaleExchangeMotion
  private typealias T = JellyScaleExchangeTheme
  var body: some View {
    GeometryReader { outer in
      let top = outer.safeAreaInsets.top, bottom = outer.safeAreaInsets.bottom
      GeometryReader { geo in
        let l = T.Layout(w:geo.size.width,h:geo.size.height,top:top,bottom:bottom)
        TimelineView(.animation(paused: !driver.moving || scenePhase != .active || (driver.automatic && driver.pin != nil))) { timeline in
          let f = driver.frame(timeline.date)
          screen(l,f)
        }
      }.ignoresSafeArea()
    }
    .preferredColorScheme(.dark)
    .onAppear { driver.configure(autoPlay:autoPlay,reduced:reduced) }
    .onChange(of:reduced) { _, value in driver.endPour(); driver.configure(autoPlay:autoPlay,reduced:value) }
    .onDisappear { driver.stop() }
  }
  private func screen(_ l: T.Layout, _ f: M.Frame) -> some View {
    let count = f.coin > 0.6 ? 0 : Int(floor(f.pour+0.0001))
    let wobble = M.wobbleAt(f.wobbleU)
    let buttonW = (l.w-52)/2
    return ZStack(alignment:.topLeading) {
      T.page
      VStack(alignment:.leading,spacing:2) {
        Text("Jelly shop").font(T.font(26)).foregroundStyle(T.ink).frame(height:32).accessibilityAddTraits(.isHeader)
        Text("Swap jellies for points").font(T.font(14,"Regular")).foregroundStyle(T.mute).frame(height:20)
      }.frame(width:l.w-170,alignment:.leading).offset(x:20,y:l.top+10)
      HStack(spacing:8) {
        Canvas { context, _ in
          context.fill(Path(ellipseIn:CGRect(x:1,y:1,width:20,height:20)),with:.color(T.accent))
          var star = Path()
          let pts: [(Double,Double)] = [(11,5.5),(12.6,8.9),(16.3,9.3),(13.5,11.8),(14.3,15.5),(11,13.6),(7.7,15.5),(8.5,11.8),(5.7,9.3),(9.4,8.9)]
          star.move(to:CGPoint(x:pts[0].0,y:pts[0].1)); for p in pts.dropFirst() { star.addLine(to:CGPoint(x:p.0,y:p.1)) }; star.closeSubpath()
          context.fill(star,with:.color(T.color(0xB97A00)))
        }.frame(width:22,height:22)
        Text("Points").font(T.font(15)).foregroundStyle(T.ink)
      }.frame(width:120,height:40).background(T.card,in:Capsule()).overlay(Capsule().strokeBorder(T.line,lineWidth:1))
        .scaleEffect(x:1+0.12*wobble,y:1-0.1*wobble).offset(x:l.pillX-60,y:l.pillY-20)
        .accessibilityElement(children:.ignore).accessibilityLabel("Points")
      Text("+80").font(T.font(16)).foregroundStyle(T.accent).frame(width:44,alignment:.trailing)
        .opacity(sin(.pi*f.plus)).offset(x:l.pillX-110,y:l.pillY-10+14-26*f.plus)
      Rectangle().fill(.white).colorEffect(shader(l,f)).allowsHitTesting(false)
      Text(String(format:"%02d",count)).font(T.font(l.lcdH*0.62)).monospacedDigit().tracking(2).foregroundStyle(T.lcd)
        .frame(width:l.lcdW,height:l.lcdH).offset(x:l.w/2-l.lcdW/2,y:l.lcdY-l.lcdH/2)
        .accessibilityLabel("\(count) jellies on the scale")
      button("Hold to pour",width:buttonW,accent:false).scaleEffect(1-0.04*f.pressPour).offset(x:20,y:l.buttonsTop)
      button("Exchange",width:buttonW,accent:true).scaleEffect(1-0.04*f.pressSwap)
        .opacity(f.pour > 0.5 || driver.busy ? 1 : 0.45).offset(x:32+buttonW,y:l.buttonsTop)
      Circle().fill(.white.opacity(0.35)).overlay(Circle().strokeBorder(.white.opacity(0.8),lineWidth:1.5))
        .frame(width:44,height:44).opacity(0.85*f.finger)
        .offset(x:(f.fingerAt == 0 ? 20+(l.w-52)/4 : l.w-20-(l.w-52)/4)-22,y:l.buttonsTop+6)
        .allowsHitTesting(false)
      JellyScaleExchangeTouchView { state, point in
        switch state {
        case .began:
          driver.takeOver()
          touchStart = point; touchStartDate = Date(); tapCancelled = false
          touchButton = point.y >= l.buttonsTop && point.y <= l.buttonsTop+56 ? (point.x >= 20 && point.x <= 20+buttonW ? 0 : (point.x >= 32+buttonW && point.x <= l.w-20 ? 1 : -1)) : -1
          if touchButton == 0 { driver.beginPour() }; if touchButton == 1 { driver.beginSwap() }
        case .changed:
          if touchButton == 1 && hypot(point.x-touchStart.x,point.y-touchStart.y) > 10 && !tapCancelled {
            tapCancelled = true; driver.endSwap(activate:false)
          }
        case .ended, .cancelled, .failed:
          if touchButton == 0 { driver.endPour() }
          if touchButton == 1 { driver.endSwap(activate:state == .ended && !tapCancelled && Date().timeIntervalSince(touchStartDate) <= 0.5 && point.x >= 32+buttonW && point.x <= l.w-20 && point.y >= l.buttonsTop && point.y <= l.buttonsTop+56) }
          touchButton = -1
        default: break
        }
      }.accessibilityHidden(true)
      // Separate semantic controls leave the zero-delay recognizer in charge of physical touches.
      Color.clear.frame(width:buttonW,height:56).contentShape(Capsule()).offset(x:20,y:l.buttonsTop)
        .allowsHitTesting(false).accessibilityElement().accessibilityLabel("Pour jellies").accessibilityHint("Pours while held").accessibilityAddTraits(.isButton)
        .accessibilityAction { driver.beginPour(); DispatchQueue.main.asyncAfter(deadline:.now()+1/6) { driver.endPour() } }
      Color.clear.frame(width:buttonW,height:56).offset(x:32+buttonW,y:l.buttonsTop).allowsHitTesting(false)
        .accessibilityElement().accessibilityLabel("Exchange \(count) jellies").accessibilityAddTraits(.isButton)
        .disabled(count == 0)
        .accessibilityAction { driver.takeOver(); driver.exchange() }
    }.frame(width:l.w,height:l.h,alignment:.topLeading)
  }
  private func button(_ text: String, width: Double, accent: Bool) -> some View {
    Text(text).font(T.font(16)).foregroundStyle(accent ? T.accentInk : T.ink)
      .frame(width:width,height:56).background(accent ? T.accent : T.card,in:Capsule())
      .overlay(Capsule().strokeBorder(accent ? .clear : T.line,lineWidth:1)).accessibilityHidden(true)
  }
  private func shader(_ l: T.Layout, _ f: M.Frame) -> Shader {
    let riseSy = 1.05*cos(M.tilt)-0.25*sin(M.tilt)
    let targetSx = (l.pillX-28-l.w/2)/l.ppu, targetSy = -(l.pillY-l.stageY)/l.ppu
    let dx = targetSx*f.fly, dy = ((targetSy-riseSy)*f.fly)/cos(M.tilt)+sin(.pi*f.fly)*0.6
    return ShaderLibrary.JellyScaleExchangeMain(
      .float4(Float(l.w),Float(l.h),Float(l.ppu),Float(displayScale)),
      .float4(Float(reduced ? ceil(f.pour-0.001) : f.pour),Float(f.melt),Float(f.coin),Float(f.spin)),
      .float4(Float(dx),Float(dy),Float(1-0.78*f.fly),Float(1-M.smooth(0.85,1,f.fly))),
      .float4(Float(M.sinkOf(f.pour,f.coin)),Float(M.tilt),0,Float(l.stageY)))
  }
}
