import SwiftUI

struct QrJoyPostcard: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reducedMotion
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = QrJoyPostcardClock()

  private var pinned: Double? {
    #if DEBUG
      if let number = UserDefaults.standard.object(forKey: "qrJoyTime") {
        return (number as? NSNumber)?.doubleValue ?? Double("\(number)")
      }
    #endif
    return nil
  }

  var body: some View {
    GeometryReader { proxy in
      TimelineView(
        .animation(
          paused: pinned != nil || scenePhase != .active || (reducedMotion && !clock.running))
      ) { timeline in
        let sample = clock.sample(
          timeline.date, auto: autoPlay, reduced: reducedMotion, pinned: pinned)
        Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) {
          context, size in
          if reducedMotion && pinned == nil {
            if sample.fade < 0.999 {
              QrJoyPostcardArt.draw(
                context: &context, size: size, frame: QrJoyPostcardMotion.frameAt(0), live: .init())
            }
            if sample.fade > 0.001 {
              var overlay = context
              overlay.opacity = sample.fade
              overlay.drawLayer { layer in
                QrJoyPostcardArt.draw(
                  context: &layer, size: size,
                  frame: QrJoyPostcardMotion.frameAt(QrJoyPostcardMotion.PEAK), live: .init())
              }
            }
          } else {
            QrJoyPostcardArt.draw(
              context: &context, size: size, frame: sample.frame, live: sample.live)
          }
        }
        .overlay {
          QrJoyPostcardTouchView { phase, point in
            guard pinned == nil else { return }
            clock.touch(phase, point: point, size: proxy.size, reduced: reducedMotion)
          }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(
          "Share the joy. Decorate a plain QR code with a backdrop and warm words, then send it to whoever picks up the order."
        )
        .accessibilityHint(
          "Decorates the QR code into a postcard, or turns it back when it is decorated."
        )
        .accessibilityAction { clock.accessibilityToggle(reduced: reducedMotion) }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .preferredColorScheme(.light)
    .onAppear { clock.reset(reduced: reducedMotion) }
    .onChange(of: reducedMotion) { _, value in clock.reset(reduced: value) }
    .onChange(of: autoPlay) { _, _ in clock.reset(reduced: reducedMotion) }
    .onChange(of: scenePhase) { _, _ in clock.resetDate() }
  }
}
