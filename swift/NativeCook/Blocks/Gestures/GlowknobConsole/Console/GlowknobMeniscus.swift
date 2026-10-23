import Foundation

// Frames of stillness (light unchanged, spring at rest) before the per-frame
// simulation switches itself off: ~0.5s at 60Hz, ~0.25s at 120Hz.
private let quietFrames = 30

/// Liquid meniscus (use-meniscus.ts): the light's leading edge is a damped
/// spring driven by the light's own speed, so it stretches while moving and
/// wobbles to rest. The frame callback only runs while the light moves or the
/// spring rings; any change of the light wakes it, and it parks itself once
/// everything is still.
@MainActor
final class GlowknobMeniscus {
  let slosh = GlowknobAnimatedValue(0)
  private let velocity = GlowknobAnimatedValue(0)
  private let lastFill = GlowknobAnimatedValue(-1)
  private let quiet = GlowknobAnimatedValue(0)
  private let live = GlowknobSharedValue(false)
  private var frame: GlowknobFrameCallback!
  private let fill: () -> Double
  private(set) var on: Bool

  init(fill: @escaping () -> Double, on: Bool) {
    self.fill = fill
    self.on = on
    frame = GlowknobFrameCallback { [unowned self] since in
      let dt = (since ?? 16) / 1000
      let current = self.fill()
      let previous = self.lastFill.get()
      self.lastFill.set(current)
      if previous < 0 { return }
      let speed = ((current - previous) * GlowknobReferenceGeometry.track.width) / max(dt, 1.0 / 240)
      let next = glowknobSpringStep(self.slosh.get(), self.velocity.get(), glowknobSloshTarget(speed), dt)
      self.slosh.set(next.position)
      self.velocity.set(next.velocity)
      if current == previous && glowknobSpringSettled(next.position, next.velocity) {
        self.quiet.set(self.quiet.get() + 1)
        if self.quiet.get() > Double(quietFrames) {
          self.slosh.set(0)
          self.velocity.set(0)
          self.live.set(false)
          self.frame.setActive(false)
        }
      } else {
        self.quiet.set(0)
      }
    }
    GlowknobUIRuntime.shared.reaction({ [unowned self] in self.fill() }) { [unowned self] current, previous in
      guard self.on, let previous, current != previous, !self.live.get() else { return }
      self.live.set(true)
      self.lastFill.set(-1)
      self.quiet.set(0)
      self.frame.setActive(true)
    }
  }

  func setOn(_ value: Bool) {
    on = value
    if value { return }
    frame.setActive(false)
    live.set(false)
    slosh.set(0)
    velocity.set(0)
  }
}

/// The percentage reads as one color at a time (use-readout-legibility.ts):
/// light glyphs on the empty well, dark glyphs once the light covers more than
/// half of the glyph run (back to light below 40%). The whole number swaps with
/// a 140ms crossfade, so a light edge never splits a digit into two colors.
@MainActor
final class GlowknobReadoutLegibility {
  let numberInk = GlowknobAnimatedValue(0)
  private let readout: () -> Double
  private let fill: () -> Double
  var reduced: Bool

  var numberRun: Double {
    glowknobGlyphRun(glowknobJsRound(readout()), GlowknobReferenceGeometry.readout.size)
  }

  var coverage: Double {
    typealias G = GlowknobReferenceGeometry
    return glowknobRunCoverage(G.track.x + G.track.width * fill(), G.readout.x + G.readout.width, numberRun)
  }

  init(readout: @escaping () -> Double, fill: @escaping () -> Double, reduced: Bool) {
    self.readout = readout
    self.fill = fill
    self.reduced = reduced
    GlowknobUIRuntime.shared.reaction({ [unowned self] in self.coverage }) { [unowned self] c, previous in
      let dark = self.numberInk.get() >= 0.5
      let target = glowknobInkTarget(c, dark)
      if previous == nil {
        self.numberInk.set(target)
      } else if (target == 1) != dark {
        self.numberInk.set(glowknobWithTiming(target, duration: self.reduced ? 0 : 140, easing: GlowknobMotionTokens.easeOut))
      }
    }
  }
}
