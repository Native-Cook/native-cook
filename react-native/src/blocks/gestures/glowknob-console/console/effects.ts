// Signature effects of the tactile console. All were chosen (2026-10-02); the
// switches stay as a config so any one can be turned off in a single place.
export type Effects = {
  /** Light illuminates the housing, the knob rim and the "table" below. */
  spill: boolean;
  /** Brightness level dims the page around the device. */
  ambient: boolean;
  /** The fill's leading edge has mass: it stretches with speed and wobbles. */
  meniscus: boolean;
  /** Knob detent haptics per groove and inertial flick (decay). */
  crown: boolean;
  /** Brushed-metal sheen on the knob that stays put while grooves turn. */
  sheen: boolean;
  /** Device tilt moves highlights (bevel band, knob sheen). Needs a device. */
  tilt: boolean;
  /** On entry the light rises from 0, the number rolls up, the knob homes. */
  powerOn: boolean;
  /** Faint glass streak over the track. */
  glass: boolean;
  /** Selected mode button gets a rim in its channel color. */
  rim: boolean;
  /** Soft haptic at the trough of a mode switch, same frame as the color. */
  troughHaptic: boolean;
  /** Gentle magnet at 50% with a haptic. */
  magnet: boolean;
  /** Drag the finger below the track for ¼-speed fine adjustment. */
  fineScrub: boolean;
  /** Glass-floor reflection: the live device mirrored under itself. */
  reflection: boolean;
  /** A soft click per knob detent, with the haptic. Off by default. */
  sound: boolean;
  /** Hold the housing to pick the console up as a 3D object (Skia 3 face on
   * a three.js body), turn it over, tap to set it down. */
  lift: boolean;
  /** Lifted: the light is a real light in the 3D scene (knob, chamfer), the
   * object glows, and the glass floor mirrors the lifted object. */
  liftLight: boolean;
};

export const allEffects: Effects = {
  spill: true,
  ambient: true,
  meniscus: true,
  crown: true,
  sheen: true,
  tilt: true,
  powerOn: true,
  glass: true,
  rim: true,
  troughHaptic: true,
  magnet: true,
  fineScrub: true,
  reflection: true,
  sound: false,
  lift: true,
  liftLight: true,
};
