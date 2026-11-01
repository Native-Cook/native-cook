import SwiftUI

struct PopUpQuestCard: View {
  static let loop = PopUpQuestCardMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = PopUpQuestCardClock()
  private let resources = PopUpQuestCardResources.shared
  private var pinned: Double? {
    #if DEBUG
    if let v = UserDefaults.standard.object(forKey: "popUpQuestCardTime") {
      return (v as? NSNumber)?.doubleValue ?? Double(String(describing: v)) ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        let l = PopUpQuestCardMotion.Layout(size: full.size, top: safe.safeAreaInsets.top, bottom: safe.safeAreaInsets.bottom)
        TimelineView(.animation(paused: pinned != nil || scenePhase != .active || !clock.running)) { timeline in
          let pose = clock.sample(timeline.date, autoPlay, reduced, pinned)
          let active = pinned != nil ? 0 : clock.active
          let k = PopUpQuestCardMotion.smooth(0, 0.55, pose.o)
          let e = reduced ? 1.0 : pose.o
          let clip = l.clip(pose, active, reduced)
          let sheetY = l.sheetTop + (1 - PopUpQuestCardMotion.smooth(0.25, 1, pose.o)) * (l.h - l.sheetTop + 40)
          ZStack(alignment: .topLeading) {
            Color(uiColor: PopUpQuestCardResources.color("#FBF8F3"))
            Canvas { context, _ in
              context.draw(Image(uiImage: resources.grid(l, hidden: pose.o > 0.001 ? active : -1, pressed: clock.pressed)), in: CGRect(origin: .zero, size: l.size))
            }
            .scaleEffect(1 - 0.04 * k).opacity(1 - k)
            ZStack(alignment: .topLeading) {
              Color(uiColor: PopUpQuestCardResources.color(resources.quests[active].bg))
              ForEach(0..<5) { i in
                Image(uiImage: resources.quests[active].art[i])
                  .resizable().interpolation(.high)
                  .frame(width: 1200, height: 900, alignment: .topLeading)
                  .projectionEffect(l.projection(pose, active, reduced, i, resources.quests[active].hinges[i]))
                if i == 1 {
                  Image(uiImage: resources.shadows(active))
                    .resizable().frame(width: 1200, height: 900, alignment: .topLeading)
                    .projectionEffect(l.projection(pose, active, reduced, i, 700))
                    .opacity(reduced ? 0 : 0.14 * PopUpQuestCardMotion.smooth(0.4, 1, pose.o))
                }
              }
            }
            .frame(width: l.w, height: l.h, alignment: .topLeading)
            .mask {
              Path(roundedRect: clip, cornerRadius: max(0, 18 * (1 - e)))
                .fill(Color.white)
            }
            // Skia pictures replay their own paints even at group opacity zero. At rest the
            // replayed active illustration therefore covers that card's heart in the reference.
            .opacity(reduced ? PopUpQuestCardMotion.clamp(pose.o * 2) : 1)
            Canvas { context, _ in
              context.draw(Image(uiImage: resources.sheetBase(l, active, pressed: clock.pressed)), at: CGPoint(x: 0, y: sheetY), anchor: .topLeading)
              for i in 0..<6 {
                let v = PopUpQuestCardMotion.smooth(0.45 + Double(i) * 0.05, 0.8 + Double(i) * 0.05, pose.o)
                var c = context; c.opacity = v
                c.draw(Image(uiImage: resources.sheetItem(l, active, i)), at: CGPoint(x: 0, y: sheetY + (1 - v) * 12), anchor: .topLeading)
              }
              context.opacity = PopUpQuestCardMotion.smooth(0.6, 1, pose.o)
              if clock.pressed == 4 { context.translateBy(x: 38, y: l.top + 28); context.scaleBy(x: 0.96, y: 0.96); context.translateBy(x: -38, y: -l.top - 28) }
              context.draw(Image(uiImage: resources.back()), at: CGPoint(x: 16, y: l.top + 6), anchor: .topLeading)
            }
            if pinned == nil {
              PopUpQuestCardTouchSurface(clock: clock, layout: l, pose: pose, quest: resources.quests[active])
            }
          }
          .frame(width: l.w, height: l.h, alignment: .topLeading)
          .clipped()
          .onChange(of: timeline.date) { _, _ in if pinned == nil { clock.settle(autoPlay && !reduced) } }
        }
      }.ignoresSafeArea()
    }
    .background(Color(uiColor: PopUpQuestCardResources.color("#FBF8F3")))
    .preferredColorScheme(.light)
    .onAppear { if pinned == nil { clock.activate(autoPlay && !reduced) } }
    .onDisappear { clock.suspend() }
    .onChange(of: scenePhase) { _, phase in
      clock.suspend()
      if phase == .active && pinned == nil { clock.activate(autoPlay && !reduced) }
    }
    .onChange(of: reduced) { _, _ in clock.suspend(); if pinned == nil { clock.activate(autoPlay && !reduced) } }
  }
}
