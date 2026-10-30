import SwiftUI

struct BookFanSignin: View {
  static let loop = BookFanSigninMotion.LOOP
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = BookFanSigninClock()
  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "bookFanTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(
        .animation(paused: pinned != nil || scenePhase != .active || (reduced && !clock.fading))
      ) { timeline in
        let sample = clock.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
        Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) {
          context, size in
          context.withCGContext { cg in
            let art = BookFanSigninArt.shared
            if reduced {
              art.scene(cg, size, BookFanSigninMotion.frameAt(0), BookFanSigninMotion.Driver())
              if sample.fade > 0.001 {
                cg.saveGState()
                cg.setAlpha(sample.fade)
                cg.beginTransparencyLayer(auxiliaryInfo: nil)
                art.scene(
                  cg, size, BookFanSigninMotion.frameAt(BookFanSigninMotion.PEAK),
                  BookFanSigninMotion.Driver())
                cg.endTransparencyLayer()
                cg.restoreGState()
              }
            } else {
              art.scene(cg, size, BookFanSigninMotion.frameAt(sample.driver.t), sample.driver)
            }
          }
        }
        .overlay {
          BookFanSigninTouchView { phase, point, translation, velocity in
            guard pinned == nil else { return }
            clock.touch(phase, point, translation, velocity, size: proxy.size, reduced: reduced)
          }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Which book is on your mind? Sign in.")
        .accessibilityHint("Opens the sign-in sheet, or closes it when it is open.")
        .accessibilityAction { clock.accessibilityToggle(reduced: reduced) }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
      .onChange(of: scenePhase) { _, _ in clock.resetDate() }
      .onChange(of: autoPlay) { _, _ in clock.reset() }
      .onChange(of: reduced) { _, _ in clock.reset() }
    }
    .ignoresSafeArea()
    .background(.white)
    .preferredColorScheme(.light)
  }
}
