import Foundation

// Pure, frame-thread-safe rules behind the console's motion and readout. No
// UI imports, so they can be unit-tested on their own.

private func clamp(_ value: Double, _ lo: Double, _ hi: Double) -> Double {
  max(lo, min(hi, value))
}

/// Width, in drawing units, of the right-aligned "NN%" glyph run.
func glowknobGlyphRun(_ value: Double, _ size: Double, advance: Double = 0.62) -> Double {
  let digits: Double = value >= 100 ? 3 : value >= 10 ? 2 : 1
  return (digits + 1) * advance * size
}

/// Share (0…1) of a right-aligned glyph run that the light's edge covers.
func glowknobRunCoverage(_ edge: Double, _ boxRight: Double, _ run: Double) -> Double {
  if run <= 0 { return 0 }
  return clamp((edge - (boxRight - run)) / run, 0, 1)
}

/// Whether the readout should be dark (1) or light (0). Hysteresis keeps it
/// from flickering while the light's edge rests inside the number: it turns
/// dark above `on` and only turns light again below `off`.
func glowknobInkTarget(_ coverage: Double, _ dark: Bool, on: Double = 0.5, off: Double = 0.4) -> Double {
  dark ? (coverage < off ? 0 : 1) : (coverage > on ? 1 : 0)
}

/// Whether a readout step should roll. A channel swap never rolls (it happens
/// under the fade). While dragging, a step that changes the tens or hundreds
/// digit is applied instantly, since two digits rolling at once read as blur.
func glowknobRollsOnStep(_ previous: Double?, _ next: Double, _ dragging: Bool, _ inPlace: Bool) -> Bool {
  if !inPlace { return false }
  guard dragging, let previous else { return true }
  return (next / 10).rounded(.down) == (previous / 10).rounded(.down)
}

/// GlowknobMeniscus target: how far the leading edge leans for a given light speed.
func glowknobSloshTarget(_ speed: Double) -> Double {
  clamp(speed * 0.02, -18, 24)
}

/// One semi-implicit Euler step of the meniscus spring. The step is clamped
/// to 50ms so a hitch (or a backgrounded frame) cannot blow the spring up.
func glowknobSpringStep(_ position: Double, _ velocity: Double, _ target: Double, _ dt: Double,
                stiffness: Double = 360, damping: Double = 16) -> (position: Double, velocity: Double) {
  let h = clamp(dt, 0, 0.05)
  let acceleration = stiffness * (target - position) - damping * velocity
  let v = velocity + acceleration * h
  return (position + v * h, v)
}

/// True once the meniscus spring is visually at rest.
func glowknobSpringSettled(_ position: Double, _ velocity: Double) -> Bool {
  abs(position) < 0.05 && abs(velocity) < 0.5
}

/// Lifted console released with `velocity` (rad/s): the half turn it comes
/// to rest on (0 = face, ±1 = back, ±2 = face again…). The flick projects the
/// yaw forward by `reach` seconds before rounding to the nearest face.
func glowknobRestingHalfTurn(_ yaw: Double, _ velocity: Double, reach: Double = 0.2) -> Double {
  glowknobJsRound((yaw + velocity * reach) / Double.pi)
}

/// Yaw to set the console down at: the nearest full turn, face up.
func glowknobFaceUpYaw(_ yaw: Double) -> Double {
  glowknobJsRound(yaw / (Double.pi * 2)) * Double.pi * 2
}
