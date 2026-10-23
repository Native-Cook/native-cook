// Signature effects of the tactile console. All were chosen (2026-10-02); the
// switches stay as a config so any one can be turned off in a single place.
struct GlowknobEffects: Equatable, Sendable {
  /// Light illuminates the housing, the knob rim and the "table" below.
  var spill = true
  /// Brightness level dims the page around the device.
  var ambient = true
  /// The fill's leading edge has mass: it stretches with speed and wobbles.
  var meniscus = true
  /// Knob detent haptics per groove and inertial flick (decay).
  var crown = true
  /// Brushed-metal sheen on the knob that stays put while grooves turn.
  var sheen = true
  /// Device tilt moves highlights (bevel band, knob sheen). Needs a device.
  var tilt = true
  /// On entry the light rises from 0, the number rolls up, the knob homes.
  var powerOn = true
  /// Faint glass streak over the track.
  var glass = true
  /// Selected mode button gets a rim in its channel color.
  var rim = true
  /// Soft haptic at the trough of a mode switch, same frame as the color.
  var troughHaptic = true
  /// Gentle magnet at 50% with a haptic.
  var magnet = true
  /// Drag the finger below the track for ¼-speed fine adjustment.
  var fineScrub = true
  /// Glass-floor reflection: the live device mirrored under itself.
  var reflection = true
  /// A soft click per knob detent, with the haptic. Off by default.
  var sound = false
  /// Hold the housing to pick the console up as a 3D object (SwiftUI face on
  /// a SceneKit body), turn it over, tap to set it down.
  var lift = true
  /// Lifted: the light is a real light in the 3D scene (knob, chamfer), the
  /// object glows, and the glass floor mirrors the lifted object.
  var liftLight = true

  static let all = GlowknobEffects()
}
