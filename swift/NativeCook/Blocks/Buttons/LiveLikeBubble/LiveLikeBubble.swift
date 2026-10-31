import SwiftUI

struct LiveLikeBubble: View {
  static let loop = LiveLikeBubbleMotion.loop
  private let autoPlay: Bool
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var displayScale
  @StateObject private var driver = LiveLikeBubbleDriver()
  init(autoPlay: Bool = false) { self.autoPlay = autoPlay }
  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
      GeometryReader { full in
        let w = full.size.width, h = full.size.height
        let ppu = (w*0.32).rounded()
        let centreY = top+((h-top-bottom)*0.42).rounded()
        let buttonY = h-max(bottom,16)-8-36
        let fs = (ppu*0.5).rounded()
        let paused = scenePhase != .active || (driver.manual || !driver.allowAuto ? driver.idle : driver.pinned)
        TimelineView(.animation(paused: paused)) { timeline in
          let date = timeline.date
          let f = driver.frame(date,reduced: reduced)
          let hs = driver.hearts(date)
          ZStack(alignment: .topLeading) {
            LiveLikeBubbleTheme.page
            if scenePhase == .active {
              Canvas { context, size in
                let shader = ShaderLibrary.LiveLikeBubbleMain(
                  .float4(Float(w),Float(h),Float(ppu),Float(displayScale)),
                  .float4(Float(centreY),Float(max(0,f.swell)),Float(reduced ? 0 : f.swell),0.3),
                  .float4(hs[0].x,hs[0].y,hs[0].z,hs[0].w), .float4(hs[1].x,hs[1].y,hs[1].z,hs[1].w), .float4(hs[2].x,hs[2].y,hs[2].z,hs[2].w),
                  .float4(hs[3].x,hs[3].y,hs[3].z,hs[3].w), .float4(hs[4].x,hs[4].y,hs[4].z,hs[4].w), .float4(hs[5].x,hs[5].y,hs[5].z,hs[5].w))
                context.fill(Path(CGRect(origin:.zero,size:size)), with:.shader(shader))
              }.allowsHitTesting(false)
            }
            HStack(spacing:10) {
              Text("LIVE").font(LiveLikeBubbleTheme.font(13,"SemiBold")).tracking(0.6)
                .foregroundStyle(.white).padding(.horizontal,10).frame(height:26)
                .background(LiveLikeBubbleTheme.live,in:RoundedRectangle(cornerRadius:8))
              Text("2.4K watching").font(LiveLikeBubbleTheme.font(15)).foregroundStyle(LiveLikeBubbleTheme.ink)
            }.offset(x:20,y:top+10)
            Text("\(f.count)").font(LiveLikeBubbleTheme.font(fs,"SemiBold")).monospacedDigit().tracking(-1)
              .foregroundStyle(.white).frame(width:0.95*ppu,height:fs*1.5)
              .scaleEffect(1+0.07*max(0,f.swell))
              .position(x:w/2+0.02*ppu+0.95*ppu/2,y:centreY)
              .accessibilityLabel("\(f.count) likes in the last minute")
              .onChange(of:f.count) { _, value in
                if UIAccessibility.isVoiceOverRunning {
                  UIAccessibility.post(notification:.announcement,argument:"\(value) likes in the last minute")
                }
              }
            Text("likes in the last minute").font(LiveLikeBubbleTheme.font(15)).foregroundStyle(LiveLikeBubbleTheme.mute)
              .frame(width:w,height:19,alignment:.top).offset(y:centreY+ppu*1.35)
            Circle().fill(.white).overlay(Circle().strokeBorder(LiveLikeBubbleTheme.line,lineWidth:1))
              .overlay { LiveLikeBubbleHeart().fill(LiveLikeBubbleTheme.live).frame(width:30,height:28) }
              .shadow(color:LiveLikeBubbleTheme.finger.opacity(0.12),radius:12,x:0,y:8)
              .frame(width:72,height:72).scaleEffect(1-0.08*f.press).position(x:w/2,y:buttonY)
              .accessibilityElement().accessibilityLabel("Send a like").accessibilityHint("Hold to send many")
              .accessibilityAddTraits(.isButton)
              .accessibilityAction { driver.begin(0,Date()); driver.end(Date()) }
            Circle().fill(LiveLikeBubbleTheme.finger.opacity(0.18))
              .overlay(Circle().strokeBorder(LiveLikeBubbleTheme.finger.opacity(0.35),lineWidth:1.5))
              .frame(width:44,height:44).scaleEffect(1-0.12*f.press).opacity(0.85*f.finger)
              .position(x:w/2+f.fingerX*ppu*0.4,y:buttonY).allowsHitTesting(false).accessibilityHidden(true)
            LiveLikeBubbleTouchView(button:CGRect(x:w/2-36,y:buttonY-36,width:72,height:72)) { state, point, button in
              if state == .began {
                if button { driver.begin((point.x-w/2)/ppu,Date()) }
                else { driver.takeOver(Date()) }
              } else if state == .ended || state == .cancelled || state == .failed { driver.end(Date()) }
            }.accessibilityHidden(true)
          }.frame(width:w,height:h,alignment:.topLeading)
            .onChange(of:date) { _, new in driver.tick(new) }
        }
      }.ignoresSafeArea()
    }.preferredColorScheme(.light)
      .onAppear { configure() }
      .onChange(of:reduced) { _,_ in configure() }
      .onChange(of:scenePhase) { _, phase in
        if phase == .active { driver.previous = Date().timeIntervalSince(driver.epoch) }
      }
      .onChange(of:autoPlay) { _,_ in configure() }
  }
  private func configure() {
    let nextAuto = autoPlay && !reduced
    if driver.allowAuto && !nextAuto && !driver.manual { driver.takeOver(Date()) }
    driver.allowAuto = nextAuto
    driver.autoStart = Date()
    driver.manual = !driver.allowAuto
    if reduced { driver.fingerFrom = 0 }
  }
}
