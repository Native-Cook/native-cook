import Observation
import SwiftUI
import UIKit

// Hold this long on the housing to pick the console up.
private let HOLD = 320.0
// A half turn per 260pt of horizontal drag; pitch follows vertical drag.
private let YAW_PER_PT = Double.pi / 260
private let PITCH_PER_PT = 0.006
private let PITCH_MAX = 0.75
private let RAISE = GlowknobSpringConfig.physics(stiffness: 210, damping: 17, mass: 1)
private let SETTLE = (stiffness: 150.0, damping: 19.0, mass: 1.0)
// Frames (ms) both drawings are up at a handoff: the 3D frame showing the
// new state reaches the screen a frame or two after the shared value flips.
private let OVERLAP = 70.0
private let LOWER = GlowknobSpringConfig.physics(stiffness: 240, damping: 26, mass: 1, overshootClamping: true)

// Stages.
let GLOWKNOB_REST = 0, PICKING = 1, HELD = 2, LOWERING = 3

/// The console's light as a scene light: its leading edge (drawing units),
/// linear RGB color and strength (0…1).
struct GlowknobGlow {
  var tip: Double
  var color: SIMD3<Double>
  var power: Double
}

/// Link between the lift choreography and the scene (lift-scene.ts GlowknobLiftLink).
@MainActor
final class GlowknobLiftLink {
  /// While false the scene neither reads the pose nor renders.
  var live = false
  /// A rendered frame exists (the renderer is up).
  var ready = false
  /// A freshly recorded face to paint before the next render.
  var face: CGImage?
  /// The console's own light at the moment it was picked up.
  var light: GlowknobGlow?
  // [lift, yaw, pitch, tiltX, tiltY], pushed every frame it changes.
  private(set) var latest: [Double] = [0, 0, 0, 0, 0]
  private var onFace: () -> Void = {}
  private var onRender: () -> Void = {}

  func setPose(_ pose: [Double]) { latest = pose }
  /// Shows `face` on the object's face and starts following the pose.
  func start(_ face: CGImage, light: GlowknobGlow?) {
    self.face = face
    self.light = light
    live = true
  }
  func stop() { live = false }
  func takeFace() -> CGImage? {
    defer { face = nil }
    return face
  }
  func on(face: @escaping () -> Void, render: @escaping () -> Void) {
    onFace = face
    onRender = render
  }
  /// After a render; `revealed` when it is the first with a new face.
  func rendered(_ revealed: Bool) {
    ready = true
    onRender()
    if revealed { onFace() }
  }
}

/// The light as it is when picked up.
@MainActor
private func glowOf(_ control: GlowknobTactileControl) -> GlowknobGlow {
  let fill = control.fill
  let c = glowknobInterpolateColors(control.lightMix, GlowknobTactile.transferAt, GlowknobTactile.transfer[1])
  // sRGB → linear, the scene's working space.
  func lin(_ v: Double) -> Double { v <= 0.04045 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4) }
  return GlowknobGlow(tip: GlowknobReferenceGeometry.track.x + GlowknobReferenceGeometry.track.width * fill,
              color: SIMD3(lin(c.r), lin(c.g), lin(c.b)),
              power: control.lightAlpha * min(1, 0.25 + fill))
}

/// Pick-up choreography of the console (console-lift.tsx useConsoleLift).
/// Holding the housing records the live artwork (DeviceArt, baked) into the
/// face texture, and swaps the 2D drawing for the 3D object at the same
/// pixels; the object then rises toward the viewer and follows the finger. A
/// flick turns it over to its back; a tap sets it down, face up.
@MainActor
@Observable
final class GlowknobConsoleLift {
  static let HOLD = 320.0

  private(set) var lifted = false
  private(set) var used = false
  /// liftable && warm: the pick-up gesture is live.
  var enabled = false

  @ObservationIgnored let lift = GlowknobAnimatedValue(0)
  @ObservationIgnored let yaw = GlowknobAnimatedValue(0)
  @ObservationIgnored let pitch = GlowknobAnimatedValue(0)
  @ObservationIgnored let stage = GlowknobSharedValue(GLOWKNOB_REST)
  // 1 while the 3D object is drawn (it sits above the 2D drawing)...
  @ObservationIgnored let shown = GlowknobAnimatedValue(0)
  // ...and 1 while the 2D drawing under it is hidden. The two overlap for a
  // few frames at each handoff, so neither direction shows a gap.
  @ObservationIgnored let covered = GlowknobAnimatedValue(0)
  // Changes after each 3D render.
  @ObservationIgnored let tick = GlowknobAnimatedValue(0)
  @ObservationIgnored let grab = GlowknobSharedValue(SIMD2<Double>(0, 0))
  @ObservationIgnored let link = GlowknobLiftLink()

  @ObservationIgnored private let control: GlowknobTactileControl
  @ObservationIgnored private var fx: GlowknobEffects
  @ObservationIgnored private let tilt: GlowknobSharedValue<GlowknobTilt>
  @ObservationIgnored var haptics = true
  /// Records the face (DeviceArt baked, still tilt) at pick-up.
  @ObservationIgnored var recordFace: (() -> CGImage?)?

  init(control: GlowknobTactileControl, fx: GlowknobEffects, tilt: GlowknobSharedValue<GlowknobTilt>) {
    self.control = control
    self.fx = fx
    self.tilt = tilt
    // Pushes the pose to the scene each frame it changes while the object
    // is up (and once more as it comes to rest).
    struct PoseStage: Equatable { var pose: [Double]; var stage: Int }
    GlowknobUIRuntime.shared.reaction({ [unowned self] in
      PoseStage(pose: [self.lift.get(), self.yaw.get(), self.pitch.get(), tilt.get().x, tilt.get().y],
                stage: self.stage.get())
    }) { [unowned self] now, before in
      if now.stage != GLOWKNOB_REST || (before.map { $0.stage != GLOWKNOB_REST } ?? false) { self.link.setPose(now.pose) }
    }
    var frames = 0
    link.on(face: { [unowned self] in
      self.shown.set(1)
      self.covered.set(glowknobWithDelay(OVERLAP, glowknobWithTiming(1, duration: 0)))
      self.stage.set(HELD)
      self.lift.set(glowknobWithSpring(1, RAISE))
    }, render: { [unowned self] in
      frames += 1
      self.tick.set(Double(frames % 2))
    })
  }

  func setEffects(_ next: GlowknobEffects) { fx = next }

  private func buzz(_ style: GlowknobHaptics.ImpactStyle) {
    if haptics { GlowknobHaptics.impact(style) }
  }

  private func tickFace() {
    if haptics { GlowknobHaptics.selection() }
  }

  private func begin() {
    if !link.ready {
      stage.set(GLOWKNOB_REST)
      return
    }
    buzz(.medium)
    lifted = true
    used = true
    guard let picture = recordFace?() else {
      stage.set(GLOWKNOB_REST)
      lifted = false
      return
    }
    link.start(picture, light: fx.liftLight ? glowOf(control) : nil)
    GlowknobUIRuntime.shared.wake()
  }

  private func landed() {
    link.stop()
    lifted = false
    buzz(.soft)
  }

  // MARK: Gestures

  private func follow(_ x: Double, _ y: Double) {
    let start = grab.get()
    yaw.set(start.x + x * YAW_PER_PT)
    let p = start.y + y * PITCH_PER_PT
    let over = abs(p) - PITCH_MAX
    pitch.set(over > 0 ? glowknobJsSign(p) * (PITCH_MAX + glowknobRubberBand(over, 0.25)) : p)
  }

  // Release: rest on whichever face the flick carries it to.
  private func release(_ vx: Double, _ vy: Double) {
    let v = vx * YAW_PER_PT
    let from = glowknobRestingHalfTurn(yaw.get(), 0)
    let to = glowknobRestingHalfTurn(yaw.get(), v)
    yaw.set(glowknobWithSpring(to * Double.pi, .physics(stiffness: SETTLE.stiffness, damping: SETTLE.damping,
                                                 mass: SETTLE.mass, velocity: v)) { [unowned self] done in
      if done && to != from { self.tickFace() }
    })
    pitch.set(glowknobWithSpring(0, .physics(stiffness: SETTLE.stiffness, damping: SETTLE.damping, mass: SETTLE.mass,
                                     velocity: vy * PITCH_PER_PT)))
  }

  private func hold() {
    yaw.cancel()
    pitch.cancel()
    grab.set(SIMD2(yaw.get(), pitch.get()))
  }

  private var moving: Bool {
    let s = stage.get()
    return s == PICKING || s == HELD
  }

  /// pickUp: Pan().activateAfterLongPress(HOLD).
  func pickUpStarted() {
    if stage.get() != GLOWKNOB_REST { return }
    GlowknobPerf.canvas("pickUpStarted") {
      stage.set(PICKING)
      hold()
      begin()
    }
  }

  func pickUpChanged(translation t: CGPoint) {
    if moving { follow(t.x, t.y) }
  }

  func pickUpEnded(velocity v: CGPoint) {
    if moving { release(v.x, v.y) }
  }

  /// held: Race(turn, putDown) on the lift layer.
  func turnStarted() {
    if stage.get() == HELD { hold() }
  }

  func turnChanged(translation t: CGPoint) {
    if stage.get() == HELD { follow(t.x, t.y) }
  }

  func turnEnded(velocity v: CGPoint) {
    if stage.get() == HELD { release(v.x, v.y) }
  }

  // Set down: face up whichever way it was turned, then lowered onto the
  // stage; the 2D drawing takes over on contact.
  func putDown() {
    if stage.get() != HELD { return }
    stage.set(LOWERING)
    let target = glowknobFaceUpYaw(yaw.get())
    let away = abs(target - yaw.get())
    yaw.set(glowknobWithSpring(target, .physics(stiffness: 200, damping: 24, mass: 1)))
    pitch.set(glowknobWithSpring(0, .physics(stiffness: 200, damping: 24, mass: 1)))
    lift.set(glowknobWithDelay(min(260, away * 90), glowknobWithSpring(0, LOWER) { [unowned self] done in
      if !done { return }
      self.yaw.set(0)
      self.pitch.set(0)
      self.covered.set(0)
      self.shown.set(glowknobWithDelay(OVERLAP, glowknobWithTiming(0, duration: 0)))
      self.stage.set(GLOWKNOB_REST)
      self.landed()
    }))
  }

  /// Leaving the page mid-lift: drop straight back to the 2D drawing.
  func reset() {
    link.stop()
    shown.set(0)
    covered.set(0)
    stage.set(GLOWKNOB_REST)
    lift.set(0)
    yaw.set(0)
    pitch.set(0)
    lifted = false
  }
}
