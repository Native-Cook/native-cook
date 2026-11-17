import SwiftUI

/// Pearls → jeweller's cut → suspended crystal → magnetic lock → platinum level.
struct FrostHeartRewards: View {
  typealias M = FrostHeartRewardsMotion
  typealias T = FrostHeartRewardsTheme
  static let loop = M.LOOP
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var displayScale
  @StateObject private var clock = FrostHeartRewardsClock()

  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "frostHeartRewardsTime") {
        return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
      }
    #endif
    return nil
  }

  var body: some View {
    GeometryReader { proxy in
      let width = Double(proxy.size.width)
      let S = width / 402
      let offsetY = max(0, Double(proxy.safeAreaInsets.top) - 70 * S)
      let paused = pinned != nil || scenePhase != .active
        || (!clock.moving(FrostHeartRewardsClock.wall(), autoPlay: autoPlay, reduced: reduced) && !clock.touchSession)
      ZStack(alignment: .topLeading) {
        FrostHeartRewardsBackdrop(S: S, offsetY: offsetY)
        TimelineView(.animation(paused: paused)) { timeline in
          let sample = clock.sample(timeline.date, autoPlay: autoPlay, reduced: reduced, pinned: pinned)
          let slots = FrostHeartRewardsStage.slots(sample.scene, tiltX: sample.tiltX, tiltY: sample.tiltY, jelly: sample.jelly, reduced: reduced, pixel: 1 / (displayScale * S))
          ZStack(alignment: .topLeading) {
            Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, _ in
              var c = context
              c.translateBy(x: 0, y: offsetY)
              c.scaleBy(x: S, y: S)
              FrostHeartRewardsStage().drawBack(c, slots)
            }
            FrostHeartRewardsGemView(frame: sample.gem, width: width)
              .frame(width: FrostHeartRewardsGemLayout.viewport.width * S, height: FrostHeartRewardsGemLayout.viewport.height * S)
              .offset(x: FrostHeartRewardsGemLayout.viewport.minX * S, y: FrostHeartRewardsGemLayout.viewport.minY * S + offsetY)
            Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, _ in
              var c = context
              c.translateBy(x: 0, y: offsetY)
              c.scaleBy(x: S, y: S)
              FrostHeartRewardsStage().drawFront(c, slots)
            }
            Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, _ in
              var c = context
              c.scaleBy(x: S, y: S)
              c.translateBy(x: -280, y: -136)
              FrostHeartRewardsStage().drawTag(c, slots)
            }
            .frame(width: 92 * S, height: 112 * S)
            .projectionEffect(Self.tagTurn(slots.tagAngle, width: 92 * S, height: 112 * S))
            .offset(x: 280 * S, y: 136 * S + offsetY)
            FrostHeartRewardsStaticLayer(S: S, offsetY: offsetY)
            Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, _ in
              var c = context
              c.translateBy(x: 0, y: offsetY)
              c.scaleBy(x: S, y: S)
              Self.drawPageState(c, sample, reduced: reduced)
            }
          }
          .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        if pinned == nil { touchLayer(S: S, offsetY: offsetY) }
        accessibilityLayer(S: S, offsetY: offsetY)
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
      .coordinateSpace(name: "frostHeartRewards")
    }
    .ignoresSafeArea()
    .background(T.background)
    .preferredColorScheme(.dark)
    .onAppear {
      clock.resetAll()
      FrostHeartRewardsResources.shared.prepareSounds()
      _ = FrostHeartRewardsGPU.available
      if pinned == nil && !reduced { clock.startMotion() }
    }
    .onDisappear {
      clock.stopMotion()
      clock.pause()
    }
    .onChange(of: scenePhase) { _, phase in if phase != .active { clock.pause() } }
  }

  /// [{perspective: 700}, {rotateX}] about the tag view's centre.
  static func tagTurn(_ degrees: Double, width: Double, height: Double) -> ProjectionTransform {
    guard degrees != 0 else { return ProjectionTransform() }
    var p = CATransform3DIdentity
    p.m34 = -1 / 700
    let toCenter = CATransform3DMakeTranslation(-width / 2, -height / 2, 0)
    let rotate = CATransform3DMakeRotation(degrees * .pi / 180, 1, 0, 0)
    let back = CATransform3DMakeTranslation(width / 2, height / 2, 0)
    return ProjectionTransform(CATransform3DConcat(CATransform3DConcat(CATransform3DConcat(toCenter, rotate), p), back))
  }

  /// The clipped page-state band (y 380…568), crossfaded and blurred with the rest scene during the return.
  static func drawPageState(_ context: GraphicsContext, _ sample: FrostHeartRewardsClock.Sample, reduced: Bool) {
    var c = context
    c.clip(to: Path(CGRect(x: 0, y: 380, width: 402, height: 188)))
    let reset = sample.scene.reset
    // One blur-bridged change of the same layer (as React Native's FhrStage): the level-up state fades
    // and blurs out, the resting state takes its place at the midpoint and sharpens in.
    let blur = reduced ? 0 : sin(reset * Double.pi) * 5
    let state = FrostHeartRewardsPageState()
    let opacity = abs(1 - 2 * reset)
    guard opacity > 0 else { return }
    var input = FrostHeartRewardsPageState.Input(scene: sample.scene, chipAge: sample.chipAge, reduced: reduced)
    if reset >= 0.5 {
      let rest = M.sceneAt(0)
      var ages = [Double](repeating: 1000, count: 7)
      for i in 2..<7 { ages[i] = rest.t - M.CLAIMS[i - 2] }
      input = .init(scene: rest, chipAge: ages, reduced: reduced)
    }
    var g = c
    g.opacity = opacity
    g.drawLayer { l in
      if blur > 0.001 { l.addFilter(.blur(radius: blur)) }
      l.drawLayer { inner in state.draw(inner, input) }
    }
  }

  // MARK: touch

  @ViewBuilder private func touchLayer(S: Double, offsetY: Double) -> some View {
    let hero = CGRect(x: 44 * S, y: 130 * S + offsetY, width: 325 * S, height: 259 * S)
    Color.clear
      .contentShape(Rectangle())
      .frame(width: hero.width, height: hero.height)
      .offset(x: hero.minX, y: hero.minY)
      .gesture(
        DragGesture(minimumDistance: 0, coordinateSpace: .named("frostHeartRewards"))
          .onChanged { value in
            let now = FrostHeartRewardsClock.wall()
            let p = CGPoint(x: value.location.x / S, y: (value.location.y - offsetY) / S)
            if !clock.finger {
              let start = CGPoint(x: value.startLocation.x / S, y: (value.startLocation.y - offsetY) / S)
              clock.panBegan(start, now, reduced: reduced)
            }
            clock.panChanged(p, value.translation, now, reduced: reduced)
            clock.objectWillChange.send()
          }
          .onEnded { value in
            clock.panEnded(Double(value.velocity.width), FrostHeartRewardsClock.wall(), reduced: reduced)
            clock.objectWillChange.send()
          }
      )
      .accessibilityHidden(true)
    ForEach(2..<7, id: \.self) { i in
      let b = T.chips[i].body
      Color.clear
        .contentShape(Rectangle())
        .frame(width: b.width * S, height: 44 * S)
        .offset(x: b.minX * S, y: b.minY * S + offsetY - 4)
        .onTapGesture {
          clock.claimChip(i - 2, FrostHeartRewardsClock.wall())
          clock.report(FrostHeartRewardsClock.wall(), reduced: reduced)
          clock.objectWillChange.send()
        }
        .accessibilityHidden(true)
    }
  }

  // MARK: accessibility

  @ViewBuilder private func accessibilityLayer(S: Double, offsetY: Double) -> some View {
    let spoken = clock.spoken
    Color.clear
      .frame(width: 160 * S, height: 76 * S)
      .offset(x: (44 + 51) * S, y: (130 + 17) * S + offsetY)
      .accessibilityElement()
      .accessibilityAddTraits(.isImage)
      .accessibilityLabel("Pink jelly bear. Tap for a jelly bounce.")
      .accessibilityAction(named: "Bounce the bear") { clock.bounceBear(FrostHeartRewardsClock.wall()); clock.objectWillChange.send() }
      .accessibilityAction { clock.bounceBear(FrostHeartRewardsClock.wall()); clock.objectWillChange.send() }
      .allowsHitTesting(false)
    Color.clear
      .frame(width: 208 * S, height: 142 * S)
      .offset(x: (44 + 31) * S, y: (130 + 95) * S + offsetY)
      .accessibilityElement()
      .accessibilityAddTraits(.isImage)
      .accessibilityLabel("Frosted crystal heart. Rest a finger to breathe through the frost; hold to polish; drag to turn the gem. Tilt to move its fire.")
      .accessibilityAction(named: "Reveal the next level") { clock.revealLevel(FrostHeartRewardsClock.wall(), reduced: reduced); clock.objectWillChange.send() }
      .accessibilityAction { clock.revealLevel(FrostHeartRewardsClock.wall(), reduced: reduced); clock.objectWillChange.send() }
      .allowsHitTesting(false)
    ForEach(0..<7, id: \.self) { i in
      let chip = T.chips[i]
      Group {
        if i < 2 {
          Color.clear
            .frame(width: chip.body.width * S, height: chip.body.height * S)
            .accessibilityElement()
            .accessibilityLabel("\(chip.value) UPs, already claimed")
        } else {
          Color.clear
            .frame(width: chip.body.width * S, height: 44 * S)
            .accessibilityElement()
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel("Claim \(chip.value) UPs")
            .accessibilityValue(spoken.claimed[i] ? "Claimed" : "")
            .accessibilityAction { clock.a11yClaim(i - 2, FrostHeartRewardsClock.wall()); clock.objectWillChange.send() }
        }
      }
      .offset(x: chip.body.minX * S, y: chip.body.minY * S + offsetY - (i < 2 ? 0 : 4))
      .allowsHitTesting(false)
    }
    Text("my lumi").opacity(0).frame(width: 1, height: 1).offset(x: 0, y: 75 * S + offsetY)
      .accessibilityAddTraits(.isHeader).allowsHitTesting(false)
    Text("\(spoken.balance.formatted(.number.locale(Locale(identifier: "en_US")))) points. Level \(spoken.level).")
      .opacity(0).frame(width: 1, height: 1).offset(x: 0, y: 402 * S + offsetY)
      .accessibilityAddTraits(.updatesFrequently).allowsHitTesting(false)
    Text("UPs every day!").opacity(0).frame(width: 1, height: 1).offset(x: 0, y: 479 * S + offsetY)
      .accessibilityAddTraits(.isHeader).allowsHitTesting(false)
    Text("All. Deals. Pharmacy. Beauty. Discover more at lumi: sportswear essentials. Plus 200 UPs. See what's in our latest collection and choose a new favourite. 2 hours 58 minutes.")
      .opacity(0).frame(width: 1, height: 1).offset(x: 0, y: 647 * S + offsetY).allowsHitTesting(false)
  }
}

/// The page rays (RAYS_SKSL with zero uniforms) over the #131313 fill: static, drawn once.
struct FrostHeartRewardsBackdrop: View {
  let S: Double
  let offsetY: Double
  var body: some View {
    Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
      context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(FrostHeartRewardsTheme.background))
      var c = context
      c.translateBy(x: 0, y: offsetY)
      c.scaleBy(x: S, y: S)
      c.fill(Path(CGRect(x: 0, y: -offsetY / S, width: size.width / S, height: size.height / S)), with: .shader(ShaderLibrary.frostHeartRewardsRays()))
    }
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }
}

/// The page's recorded pictures (navigation, filters, task card, tabs, labels): static, drawn once.
struct FrostHeartRewardsStaticLayer: View {
  let S: Double
  let offsetY: Double
  var body: some View {
    Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, _ in
      var c = context
      c.translateBy(x: 0, y: offsetY)
      c.scaleBy(x: S, y: S)
      FrostHeartRewardsStaticPage().draw(c)
    }
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }
}
