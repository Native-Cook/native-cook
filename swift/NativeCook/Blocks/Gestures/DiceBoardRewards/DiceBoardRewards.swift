import SwiftUI

struct DiceBoardRewards: View {
  static let loop = DiceBoardRewardsMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var phase
  @StateObject private var clock = DiceBoardRewardsClock()
  @StateObject private var staticArt = DiceBoardRewardsStaticArt()
  @GestureState private var pressed = false
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey:"diceBoardRewardsTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { proxy in
        let g = DiceBoardRewardsGeometry(width:proxy.size.width,height:proxy.size.height,topInset:safe.safeAreaInsets.top,bottomInset:safe.safeAreaInsets.bottom)
        let art = DiceBoardRewardsArt(g:g)
        TimelineView(.animation(paused:phase != .active || !clock.needsAnimation(Date(),autoPlay && !reduced,pinned))) { timeline in
          let out = clock.sample(timeline.date,autoPlay && !reduced,pinned)
          Canvas(opaque:true,colorMode:.nonLinear) { c,size in
            c.fill(Path(CGRect(origin:.zero,size:size)),with:.linearGradient(Gradient(colors:[DiceBoardRewardsResources.color("FFE9D6"),DiceBoardRewardsResources.color("FFF6EC"),DiceBoardRewardsResources.color("F0EAFF")]),startPoint:.zero,endPoint:CGPoint(x:0,y:size.height)))
            art.board(c,out,reduced,staticArt.geometry == g ? staticArt.tiles : [])
            if staticArt.geometry == g && staticArt.controls.count == 8 {
              c.draw(staticArt.controls[clock.rolls*2+(pressed ? 1 : 0)],at:.zero,anchor:.topLeading)
            } else { art.controls(c,clock.rolls,pressed) }
            art.toast(c,out)
          }
          .accessibilityHidden(true)
          .overlay(alignment:.topLeading) {
            if pinned == nil {
              Color.clear.frame(width:44,height:44).contentShape(Circle())
                .accessibilityLabel("Close").accessibilityAddTraits(.isButton)
                .offset(x:16,y:g.topInset+8)
              Color.clear.frame(width:160,height:120).contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance:1).onEnded { e in
                  if e.translation.height < -30 || e.velocity.height < -500 { clock.throwDie() }
                })
                .accessibilityLabel("Die. Flick up to roll").accessibilityAddTraits(.isButton)
                .accessibilityAction { clock.throwDie() }
                .offset(x:g.cx-80,y:g.trayY-70)
              Color.clear.frame(width:art.rollWidth,height:56).contentShape(Capsule())
                .gesture(DragGesture(minimumDistance:0).updating($pressed) { e,state,_ in
                  state = e.location.x >= -20 && e.location.x <= art.rollWidth+20 && e.location.y >= -20 && e.location.y <= 76
                }.onEnded { e in
                  if e.location.x >= -20 && e.location.x <= art.rollWidth+20 && e.location.y >= -20 && e.location.y <= 76 { clock.throwDie() }
                })
                .accessibilityLabel("Roll").accessibilityAddTraits(.isButton)
                .accessibilityAction { clock.throwDie() }
                .offset(x:g.width-20-art.rollWidth,y:g.height-g.bottomInset-72)
              Color.clear.frame(width:140,height:44).accessibilityElement().accessibilityLabel("Lucky Lap")
                .accessibilityAddTraits(.isHeader).allowsHitTesting(false).offset(x:g.cx-60,y:g.topInset+8)
              Color.clear.frame(width:94,height:44).accessibilityElement().accessibilityLabel("2,480 coins").allowsHitTesting(false).offset(x:g.width-110,y:g.topInset+8)
              Color.clear.frame(width:g.width-48,height:24).accessibilityElement()
                .accessibilityLabel("Land on a coupon to win it for your basket").allowsHitTesting(false).offset(x:24,y:g.topInset+62)
              Color.clear.frame(width:160,height:24).accessibilityElement().accessibilityLabel("Flick the die up to roll").allowsHitTesting(false).offset(x:g.cx-80,y:g.trayY+52)
              Color.clear.frame(width:180,height:24).accessibilityElement()
                .accessibilityLabel("\(clock.rolls) rolls left today").allowsHitTesting(false).offset(x:20,y:g.height-g.bottomInset-60)
              if DiceBoardRewardsMotion.toastIndex(out) >= 0 {
                Color.clear.frame(width:g.width-48,height:44).accessibilityElement()
                  .accessibilityLabel(DiceBoardRewardsMotion.prizes[DiceBoardRewardsMotion.toastIndex(out)]).allowsHitTesting(false).offset(x:24,y:g.topInset+96)
              }
            }
          }
        }
        .frame(width:proxy.size.width,height:proxy.size.height,alignment:.topLeading)
        .onAppear { staticArt.prepare(g) }
        .onChange(of:g) { _,value in staticArt.prepare(value) }
      }
      .ignoresSafeArea()
    }
    .background(DiceBoardRewardsResources.color("FFF6EC"))
    .preferredColorScheme(.light)
    .onAppear { _ = DiceBoardRewardsResources.shared; clock.reset() }
    .onChange(of:phase) { _,_ in clock.active() }
  }
}
