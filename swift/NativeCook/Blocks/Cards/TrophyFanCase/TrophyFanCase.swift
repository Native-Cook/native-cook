import SwiftUI

struct TrophyFanCase: View {
  static let loop = TrophyFanCaseMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = TrophyFanCaseClock()
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey: "trophyFanCaseTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        let size = full.size
        let l = TrophyFanCaseMotion.Layout(width: size.width, top: safe.safeAreaInsets.top)
        let art = TrophyFanCaseArt()
        ZStack(alignment: .topLeading) {
          Canvas(opaque: true, colorMode: .nonLinear) { context, bounds in
            context.fill(Path(CGRect(origin: .zero, size: bounds)), with: .shader(
              ShaderLibrary.trophyFanCaseBackground(.float2(bounds.width, bounds.height))))
          }.allowsHitTesting(false).accessibilityHidden(true)
          TrophyFanCaseLabel(text: "TROPHY CASE", weight: "Medium", size: 13,
                             color: TrophyFanCaseTheme.color(0xf2f2f2), tracking: 1.6)
            .frame(width: size.width).offset(y: safe.safeAreaInsets.top + 64 + 1.0 / 3)
          TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
            let hand = clock.sample(timeline.date, l, autoPlay && !reduced, reduced, pinned, active: scenePhase == .active)
            Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, _ in
              art.draw(context, hand)
            }
            .overlay {
              if pinned == nil {
                TrophyFanCaseTouchView(hand: hand, onEvent: clock.touch, onOpen: clock.accessibilityOpen)
              }
            }
          }
          TrophyFanCaseLabel(text: "4 of 12 trophies · 2026 season", size: 13,
                             color: TrophyFanCaseTheme.color(0xffebdc, 0.7))
            .frame(width: size.width, height: size.height, alignment: .bottom)
            .offset(y: -safe.safeAreaInsets.bottom - 28 + 1.0 / 3)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
      }.ignoresSafeArea()
    }
    .background(Color.black.ignoresSafeArea())
    .preferredColorScheme(.dark)
    .onAppear { clock.active(scenePhase == .active, reduced, pinned != nil) }
    .onDisappear { clock.active(false, reduced, pinned != nil) }
    .onChange(of: scenePhase) { _, phase in clock.active(phase == .active, reduced, pinned != nil) }
    .onChange(of: reduced) { _, value in
      clock.reset(value)
      clock.active(scenePhase == .active, value, pinned != nil)
    }
  }
}
