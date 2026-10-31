import SwiftUI
import UIKit

struct CoinDropReward: View {
  static let loop = CoinDropRewardMotion.loop
  var autoPlay: Bool
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var driver = CoinDropRewardDriver()
  init(autoPlay: Bool = false) { self.autoPlay = autoPlay }

  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
      GeometryReader { full in
        let layout = CoinDropRewardLayout(w:full.size.width,h:full.size.height,top:top,bottom:bottom)
        TimelineView(.animation(paused:scenePhase != .active || !driver.ticking)) { timeline in
          let t = driver.now(timeline.date)
          let frame = driver.frame(t)
          ZStack(alignment:.topLeading) {
            CoinDropRewardTheme.page
            Canvas { context,size in
              CoinDropRewardArtwork.page(&context,layout,frame,driver.selected,[driver.left(0,t),driver.left(1,t)],driver.pressedRow)
            }.accessibilityHidden(true)
            let u = CoinDropRewardArtwork.uniforms(layout,frame,driver.selected)
            Rectangle().fill(.white)
              .colorEffect(ShaderLibrary.CoinDropRewardMain(.float4(u.0.x,u.0.y,u.0.z,u.0.w),.float4(u.1.x,u.1.y,u.1.z,u.1.w),.float4(u.2.x,u.2.y,u.2.z,u.2.w)))
              .allowsHitTesting(false).accessibilityHidden(true)
            Canvas { context,size in CoinDropRewardArtwork.finger(&context,layout,frame) }
              .allowsHitTesting(false).accessibilityHidden(true)
            CoinDropRewardTouchView(items:accessibility(layout,frame,t)) { state,point,translation,velocity in
              driver.touch(state,point,translation,velocity,layout)
            }
          }
          .onChange(of:timeline.date) { _,_ in driver.tick(t) }
        }
        .onAppear { driver.configure(autoPlay:autoPlay,reduced:reduced) }
        .onChange(of:reduced) { _,value in driver.configure(autoPlay:autoPlay,reduced:value) }
        .onChange(of:autoPlay) { _,value in driver.configure(autoPlay:value,reduced:reduced) }
        .onChange(of:scenePhase) { _,phase in driver.active(phase == .active) }
      }.ignoresSafeArea()
    }
    .preferredColorScheme(.light)
  }
  private func accessibility(_ l: CoinDropRewardLayout,_ f: CoinDropRewardFrame,_ t: Double) -> [CoinDropRewardAccessibilityItem] {
    var items = [
      CoinDropRewardAccessibilityItem(label:"Today's visit missions",rect:CGRect(x:20,y:l.titleTop,width:l.w-40,height:30),traits:.header),
      CoinDropRewardAccessibilityItem(label:"Points wallet",rect:CGRect(x:l.walletLeft,y:l.walletTop,width:124,height:40))
    ]
    for i in 0..<2 {
      let seconds = Int(ceil(driver.left(i,t)))
      items.append(CoinDropRewardAccessibilityItem(
        label:"\(CoinDropRewardTheme.missions[i]), Visit the shop · 5 P",
        hint:seconds > 0 ? "Again in \(seconds) seconds" : "Collects the reward",
        rect:CGRect(x:20,y:l.rowsTop+Double(i)*72,width:l.w-40,height:72),
        traits:seconds > 0 ? [.button,.notEnabled] : .button,
        action:seconds > 0 ? nil : { driver.open(i,driver.now()) }))
    }
    items.append(CoinDropRewardAccessibilityItem(label:"Mystery product, Opens at 1 PM",rect:CGRect(x:20,y:l.bonusTop,width:l.w-40,height:72),traits:.notEnabled))
    items.append(CoinDropRewardAccessibilityItem(label:"This week: 4 days collected",rect:CGRect(x:20,y:l.daysTop,width:l.w-40,height:60)))
    if f.out {
      items.append(CoinDropRewardAccessibilityItem(label:"Collect 5 points",rect:CGRect(x:l.tileCx-118,y:l.tileCy-118,width:236,height:236),traits:.button,action:{ driver.accessibilityCollect() }))
    }
    if f.plus > 0 && f.plus < 1 {
      items.append(CoinDropRewardAccessibilityItem(label:"+5 P",rect:CGRect(x:l.walletLeft-44,y:l.walletTop+10,width:44,height:20)))
    }
    return items
  }
}
