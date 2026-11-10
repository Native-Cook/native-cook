import SwiftUI

struct PerfumeInkBloom: View {
  static let loop=PerfumeInkBloomMotion.LOOP
  var autoPlay=false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scene
  @StateObject private var clock=PerfumeInkBloomClock()
  private var pinned: Double? {
    #if DEBUG
    if let v=UserDefaults.standard.object(forKey:"perfumeInkBloomTime") {
      return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        let l=PerfumeInkBloomTheme.Layout(full.size,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom)
        TimelineView(.animation(paused:pinned != nil || scene != .active || !clock.running)) { timeline in
          let s=clock.sample(timeline.date,pinned)
          ZStack(alignment:.topLeading) {
            Color.white
            PerfumeInkBloomMetal(layout:l,frame:s)
            Canvas(opaque:false,colorMode:.nonLinear) { c,_ in PerfumeInkBloomArt().draw(c,l,s,clock) }.allowsHitTesting(false)
            PerfumeInkBloomTouch(layout:l,clock:clock,disabled:pinned != nil)
          }
          .frame(width:full.size.width,height:full.size.height,alignment:.topLeading).clipped()
        }
      }.ignoresSafeArea()
    }
    .background(.white).preferredColorScheme(.light)
    .onAppear { _=PerfumeInkBloomResources.shared; clock.reset(autoPlay,reduced) }
    .onDisappear { clock.active(false) }
    .onChange(of:scene) { _,phase in clock.active(phase == .active) }
    .onChange(of:reduced) { _,v in clock.reset(autoPlay,v) }
    .onChange(of:autoPlay) { _,v in clock.reset(v,reduced) }
  }
}
