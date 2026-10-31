import SwiftUI

struct FriendRadarMap: View {
  static let loop = FriendRadarMapMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = FriendRadarMapClock()
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey:"friendRadarMapTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
      GeometryReader { proxy in
        TimelineView(.animation(paused:pinned != nil || scenePhase != .active || ((!autoPlay || reduced) && !clock.running))) { timeline in
          let frame = clock.sample(timeline.date,autoPlay,reduced,pinned)
          ZStack {
            Canvas { context,size in
              FriendRadarMapArt(size:size,top:top,bottom:bottom).draw(context,frame,reduced)
            }
            .accessibilityElement(children:.ignore)
            .accessibilityLabel("\(FriendRadarMapMotion.friends[frame.labelIndex].name.lowercased()) \(FriendRadarMapMotion.friends[frame.labelIndex].street.lowercased())")
            .accessibilityValue("Friend \(frame.labelIndex+1) of 3")
            .accessibilityAdjustableAction { direction in
              if pinned == nil { clock.go(direction == .increment ? 1:-1,reduced) }
            }
            FriendRadarMapControls(frame:frame,reduced:reduced,bottom:bottom)
          }
          .contentShape(Rectangle())
          .gesture(DragGesture(minimumDistance:0).onChanged { value in
            if pinned == nil { clock.changed(value,proxy.size,bottom) }
          }.onEnded { value in
            if pinned == nil { clock.ended(value,proxy.size,top,bottom,reduced) }
          })
        }
        .frame(width:proxy.size.width,height:proxy.size.height,alignment:.topLeading)
      }.ignoresSafeArea()
    }
    .background(FriendRadarMapResources.color(0x0b0b0d).ignoresSafeArea())
    .preferredColorScheme(.dark)
    .onAppear { _ = FriendRadarMapResources.shared; clock.activate(true,reduced) }
    .onDisappear { clock.activate(false,reduced) }
    .onChange(of:scenePhase) { _,p in clock.activate(p == .active,reduced) }
    .onChange(of:reduced) { _,v in clock.activate(scenePhase == .active,v) }
  }
}
