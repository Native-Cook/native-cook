import SwiftUI

struct EvolveHoldMorph: View {
  static let loop = EvolveHoldMorphMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = EvolveHoldMorphClock()
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey: "evolveHoldMorphTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
      GeometryReader { proxy in
        let size = proxy.size, cy = (size.height*0.43).rounded(), s = (size.width*0.42).rounded()
        TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (reduced && !clock.awake))) { timeline in
          let frame = clock.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
          let art = EvolveHoldMorphArt.shared
          Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, _ in
            let full = CGRect(origin: .zero, size: size)
            context.draw(art.gradient(size), in: full)
            context.draw(art.background(size), in: full)
            var pet = context
            pet.translateBy(x: 0, y: -frame.hop)
            let shader = ShaderLibrary.evolveHoldMorphPet(
              .float2(size.width/2, cy), .float(s), .float(frame.yaw), .float(Float(frame.stage)),
              .float(Float((frame.stage+1)%3)), .float(frame.m), .float(frame.sq), .float(frame.c),
              .float(frame.blink), .float(1/(s*UIScreen.main.scale)))
            pet.fill(Path(CGRect(x: 0, y: cy-s*1.2, width: size.width, height: s*2.3)), with: .shader(shader))
            art.effects(frame, size, context)
            context.draw(art.chrome(size, top: top, bottom: bottom, stage: frame.stage), in: full)
            art.button(size, bottom: bottom, stage: frame.stage, charge: frame.c, hold: frame.hold, context: context)
          }
          .overlay(alignment: .topLeading) {
            if pinned == nil {
              EvolveHoldMorphTouch(pet: true, clock: clock)
                .frame(width: size.width-80, height: s*1.75)
                .offset(x: 40, y: cy-s*0.95)
                .accessibilityElement().accessibilityAddTraits(.isButton)
                .accessibilityLabel("Pip the \(EvolveHoldMorphMotion.names[frame.stage]). Tap to play, press and hold to evolve")
                .accessibilityAction { clock.tap() }
              EvolveHoldMorphTouch(pet: false, clock: clock)
                .frame(width: size.width-40, height: 60)
                .offset(x: 20, y: size.height-bottom-80)
                .accessibilityElement().accessibilityAddTraits(.isButton)
                .accessibilityLabel(EvolveHoldMorphMotion.cta[frame.stage])
                .accessibilityHint("Press and hold until the ring is full")
                .accessibilityAction { clock.begin() }
            }
          }
          .overlay(alignment: .topLeading) {
            Color.clear.frame(width: 180, height: 18).offset(x: 20, y: top+8)
              .accessibilityElement().accessibilityLabel("Your pet")
            Color.clear.frame(width: size.width-120, height: 32).offset(x: 20, y: top+28)
              .accessibilityElement().accessibilityLabel("Pip the \(EvolveHoldMorphMotion.names[frame.stage])")
            ForEach(0..<3) { i in
              Color.clear.frame(width: 70, height: 36)
                .offset(x: size.width/2-110+Double(i)*75, y: cy+s*0.9)
                .accessibilityElement().accessibilityLabel(EvolveHoldMorphMotion.names[i])
            }
            Color.clear.frame(width: 70, height: 34).offset(x: size.width-90, y: top+16)
              .accessibilityElement().accessibilityLabel("Level \(4+frame.stage)")
            Color.clear.frame(width: size.width-40, height: 64).offset(x: 20, y: size.height-bottom-164)
              .accessibilityElement().accessibilityLabel("Evolution energy full, 120 of 120")
          }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
      }
      .ignoresSafeArea()
    }
    .background(EvolveHoldMorphArt.color(0xEAF6EF).ignoresSafeArea())
    .preferredColorScheme(.light)
    .onChange(of: scenePhase) { _, _ in clock.resetDate() }
    .onChange(of: reduced) { _, _ in clock.preferencesChanged() }
  }
}
