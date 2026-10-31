import SwiftUI

struct PotionVialUpload: View {
  static let loop = PotionVialUploadMotion.LOOP
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = PotionVialUploadClock()
  private var pinned:Double? {
    #if DEBUG
    if let value=UserDefaults.standard.object(forKey:"potionVialUploadTime") { return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0 }
    #endif
    return nil
  }
  var body:some View {
    GeometryReader { safe in
      GeometryReader { full in
        let geo = PotionVialUploadGeo(full.size.width,full.size.height,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom)
        TimelineView(.animation(paused:pinned != nil || reduced || scenePhase != .active || (!autoPlay && !clock.running))) { timeline in
          let frame = clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned,geo)
          PotionVialUploadMetalView(geo:geo,frame:frame)
            .overlay {
              Canvas(opaque:false,colorMode:.nonLinear,rendersAsynchronously:false) { c,_ in PotionVialUploadArt.type(c,geo,frame) }
                .allowsHitTesting(false)
            }
            .overlay {
              if pinned == nil && !reduced { PotionVialUploadTouchView { phase,point in clock.touch(phase,point,geo) } }
            }
            .accessibilityElement(children:.ignore)
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel("Upload spells.zip, 48.2 megabytes, into the potion vial.")
            .accessibilityHint("Plays the upload.")
            .accessibilityAction { if pinned == nil { clock.replay(reduced) } }
        }
        .frame(width:full.size.width,height:full.size.height,alignment:.topLeading)
      }.ignoresSafeArea()
    }
    .background(Color(cgColor:PotionVialUploadArt.color("#0a080d")))
    .preferredColorScheme(.dark)
    .persistentSystemOverlays(.hidden)
    .onAppear { clock.reset(reduced); clock.active(scenePhase == .active,reduced,pinned != nil) }
    .onDisappear { clock.active(false,reduced,pinned != nil) }
    .onChange(of:scenePhase) { _,v in clock.active(v == .active,reduced,pinned != nil) }
    .onChange(of:reduced) { _,v in clock.reset(v); clock.active(scenePhase == .active,v,pinned != nil) }
    .onChange(of:autoPlay) { _,_ in clock.reset(reduced) }
  }
}
