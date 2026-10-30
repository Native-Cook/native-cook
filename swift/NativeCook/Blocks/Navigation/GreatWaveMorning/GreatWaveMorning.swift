import SwiftUI

struct GreatWaveMorning: View {
  static let loop = GreatWaveMorningMotion.LOOP
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reducedMotion
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = GreatWaveMorningClock()

  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "greatWaveTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }

  var body: some View {
    GeometryReader { proxy in
      TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
        let frame = clock.sample(
          timeline.date, auto: autoPlay, reduced: reducedMotion, pinned: pinned)
        Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
          let painter = GreatWaveMorningArt(context)
          let geo = GreatWaveMorningArt.Geo(w: size.width, h: size.height, s: size.width / 402)
          painter.drawScene(painter, GreatWaveMorningResources.shared, geo, frame)
        }
        .colorEffect(ShaderLibrary.GreatWaveMorningPaper())
        .overlay {
          GreatWaveMorningTouchView { phase, point, translation, velocity in
            guard pinned == nil else { return }
            let scale = proxy.size.width / GreatWaveMorningTheme.REF_W
            switch phase {
            case .began: clock.begin(point, scale: scale)
            case .changed: clock.update(translation, scale: scale, reduced: reducedMotion)
            case .ended: clock.end(point, velocity: velocity, scale: scale, reduced: reducedMotion)
            }
          }
          .accessibilityHidden(true)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(
          "Good morning, Yuna. Sprint 8, 14 tasks due this week. 38 open, 6 due today, 94 done this sprint. Today's focus: Review new component specs with dev, 09:30, due now."
        )
        .accessibilityHint("Marks the next task done.")
        .accessibilityAction { clock.markNext() }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
      .clipped()
      .onAppear {
        clock.reset()
        clock.prepareAudio(reduced: reducedMotion)
        clock.prepareTilt(reduced: reducedMotion)
      }
      .onDisappear {
        clock.stopAudio()
        clock.stopTilt()
        clock.resetDate()
      }
      .onChange(of: scenePhase) { _, phase in
        clock.resetDate()
        if phase != .active {
          clock.stopAudio()
          clock.stopTilt()
        } else {
          clock.prepareTilt(reduced: reducedMotion)
        }
      }
      .onChange(of: autoPlay) { _, _ in clock.reset() }
      .onChange(of: reducedMotion) { _, reduced in
        clock.reset()
        clock.prepareAudio(reduced: reduced)
        clock.prepareTilt(reduced: reduced)
      }
      .onChange(of: proxy.size) { _, _ in clock.reset() }
    }
    .background(Color(hex: UInt32(GreatWaveMorningTheme.C.sky)))
    .ignoresSafeArea()
    .preferredColorScheme(.dark)
  }
}
