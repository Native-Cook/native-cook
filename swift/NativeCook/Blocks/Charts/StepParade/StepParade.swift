import SwiftUI

struct StepParade: View {
  static let loop = StepParadeMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = StepParadeClock()
  @State private var panning = false
  @State private var panOffsetX = 0.0
  @State private var touchStarted: Date?
  @State private var tapLeftScene = false
  private var pinned: Double? {
    #if DEBUG
      if let v = UserDefaults.standard.object(forKey: "stepParadeTime") {
        return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top
      let bottom = safe.safeAreaInsets.bottom
      GeometryReader { full in
        let art = StepParadeArt(
          width: full.size.width, height: full.size.height, top: top, bottom: bottom)
        ZStack(alignment: .topLeading) {
          StepParadeChrome(
            width: full.size.width, height: full.size.height, top: top, bottom: bottom
          )
          .equatable()
          TimelineView(
            .animation(
              paused: pinned != nil || scenePhase != .active
                || (!(autoPlay && !reduced) && !clock.running))
          ) { timeline in
            let _ = clock.revision
            let hour = clock.sample(timeline.date, autoPlay && !reduced, reduced, pinned)
            Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) {
              context, _ in
              art.draw(
                context, hour, pinned == nil ? clock.selected : -1,
                pinned == nil ? clock.bubbleSelected : -1)
            }
            .overlay(alignment: .topLeading) {
              if pinned == nil {
                Color.clear.frame(width: art.g.width, height: art.g.height)
                  .contentShape(Rectangle())
                  .gesture(
                    DragGesture(minimumDistance: 0, coordinateSpace: .local)
                      .onChanged { v in
                        if touchStarted == nil {
                          touchStarted = v.time
                          tapLeftScene = false
                        }
                        if v.location.x < 0 || v.location.x > art.g.width
                          || v.location.y < 0 || v.location.y > art.g.height
                        {
                          tapLeftScene = true
                        }
                        if abs(v.translation.width) > 8 || panning {
                          if !panning { panOffsetX = v.translation.width }
                          panning = true
                          clock.pan(v.translation.width - panOffsetX, art.g.width)
                        }
                      }
                      .onEnded { v in
                        if panning {
                          clock.endPan()
                        } else if !tapLeftScene
                          && v.time.timeIntervalSince(touchStarted ?? v.time) <= 0.5
                        {
                          clock.tap(
                            .init(x: v.location.x + art.g.minX, y: v.location.y + art.g.minY), art.g
                          )
                        } else {
                          clock.endPan()
                        }
                        panning = false
                        touchStarted = nil
                      }
                  )
                  .accessibilityElement()
                  .accessibilityLabel(
                    "Your friends walking through the day. Drag sideways to replay the day"
                  )
                  .accessibilityAdjustableAction { _ in }
                  .offset(x: art.g.minX, y: art.g.minY)
              }
            }
            .overlay(alignment: .topLeading) {
              StepParadeAccessibility(
                width: full.size.width, height: full.size.height, top: top, bottom: bottom,
                hour: hour, bubbleSelected: clock.bubbleSelected)
            }
          }
        }
      }.ignoresSafeArea()
    }
    .background(Color(hex: 0xF7F8FA).ignoresSafeArea())
    .preferredColorScheme(.light)
    .onAppear { clock.reset(autoPlay && !reduced) }
    .onChange(of: reduced) { _, _ in clock.reset(autoPlay && !reduced) }
    .onChange(of: scenePhase) { _, phase in
      if phase != .active && panning {
        clock.endPan()
        panning = false
        touchStarted = nil
      }
      clock.suspend()
    }
  }
}

private struct StepParadeAccessibility: View {
  @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
  let width, height, top, bottom, hour: Double
  let bubbleSelected: Int
  var body: some View {
    ZStack(alignment: .topLeading) {
      target("Showing friends. Change group", 16, top + 8, 104, 36, .isButton)
      target("Your profile", width - 54, top + 7, 38, 38)
      target(
        "This week's winners: Monday Leo, Tuesday You, Wednesday Kofi, Thursday Leo, Friday Ana, Saturday Juno, today Leo",
        16, top + 60, width - 32, 53)
      target(
        "Your steps today", width / 2 - 110,
        top + 140 + StepParadeResources.shared.lineHeight("SemiBold", 15), 220, 64
      )
      .accessibilityValue(StepParadeMotion.number(StepParadeMotion.steps(1, hour)))
      if voiceOver {
        target("Steps today", width / 2 - 70, top + 140, 140, 19)
        target(
          StepParadeMotion.rank(hour), width / 2 - 150,
          top + 204 + StepParadeResources.shared.lineHeight("SemiBold", 15), 300, 17)
        target(StepParadeMotion.time(hour), 59, top + 288, 62, 16)
        ForEach(0..<6, id: \.self) { friend in
          let scene = CGRect(x: 16, y: top + 268, width: width - 32, height: 252)
          let pose = StepParadeMotion.place(scene, friend, StepParadeMotion.steps(friend, hour))
          target(
            StepParadeMotion.friends[friend].name,
            pose.x - 40, pose.y - 60 * pose.s - 28, 80, 22
          )
          .accessibilityValue(
            bubbleSelected == friend
              ? StepParadeMotion.number(StepParadeMotion.steps(friend, hour)) : "")
        }
      }
      target("Distance", 16, top + 590, (width - 44) / 2, 64)
      target("Active time", width / 2 + 6, top + 590, (width - 44) / 2, 64)
      ForEach(0..<4, id: \.self) { i in
        target(
          ["Home", "Friends", "Challenges", "Me"][i], Double(i) * width / 4, height - bottom - 54,
          width / 4, 48, i == 1 ? [.isButton, .isSelected] : .isButton)
      }
    }.allowsHitTesting(false)
  }
  func target(
    _ label: String, _ x: Double, _ y: Double, _ w: Double, _ h: Double,
    _ traits: AccessibilityTraits = []
  ) -> some View {
    Color.clear.frame(width: w, height: h).accessibilityElement().accessibilityLabel(label)
      .accessibilityAddTraits(traits).offset(x: x, y: y)
  }
}

/// The static page is retained across both clock ticks and finger-driven updates.
private struct StepParadeChrome: View, Equatable {
  let width, height, top, bottom: Double
  var body: some View {
    Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
      StepParadeArt(width: width, height: height, top: top, bottom: bottom).chrome(context)
    }
  }
}
