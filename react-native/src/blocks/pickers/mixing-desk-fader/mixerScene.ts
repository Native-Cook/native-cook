// The scene's fixed numbers, shared by the Filament stage, the motion and the panel. glTF space: y up, metres, the floor
// is y = 0 and the desk faces the viewer (+z). The Blender build (outside the repository) writes mixer.glb with these
// nodes at these places; its previews use the same camera.

export type Vec3 = [number, number, number];

/** The Deck node: its origin (the plate's front edge, centre) and its tilt back about x (rad). Everything the app moves
 * is its child, posed in the deck's own frame: x across, y up off the plate, -z towards the back. */
export const DECK_ORIGIN: Vec3 = [0, 0.032, 0.099288];
export const DECK_SLOPE = 0.119429;
export const CHANNELS = 4;
/** Each fader's cap at v = 0 (deck frame); it slides towards the back (-z) by v x TRAVEL. Its grip is CAP_Y up. */
export const FADER_BASE: readonly Vec3[] = [
  [-0.096, 0, -0.034],
  [-0.036, 0, -0.034],
  [0.024, 0, -0.034],
  [0.084, 0, -0.034],
];
export const TRAVEL = 0.082;
export const CAP_Y = 0.011;
/** Mute buttons (deck frame); a pressed one sinks MUTE_TRAVEL. */
export const MUTE_POS: readonly Vec3[] = [
  [-0.09, 0, -0.142],
  [-0.03, 0, -0.142],
  [0.03, 0, -0.142],
  [0.09, 0, -0.142],
];
export const MUTE_TRAVEL = 0.0022;
/** Gain knobs (deck frame); they turn about y. */
export const KNOB_POS: readonly Vec3[] = [
  [-0.09, 0, -0.173],
  [-0.03, 0, -0.173],
  [0.03, 0, -0.173],
  [0.09, 0, -0.173],
];
/** A knob at 0 points KNOB_ZERO_DEG counter-clockwise from the back, and turns KNOB_SPAN_DEG clockwise to 1. */
export const KNOB_ZERO_DEG = 135;
export const KNOB_SPAN_DEG = 270;
/** Each meter has LEDS lenses, bottom up; the last two are amber and red. Emissive colours are linear. */
export const LEDS = 8;
export const LED_GREEN: Vec3 = [0.1046, 0.8228, 0.2384];
export const LED_AMBER: Vec3 = [1.0, 0.4564, 0.0452];
export const LED_RED: Vec3 = [1.0, 0.0802, 0.0513];
export const LED_ON = 5;
export const LAMP_RGB: Vec3 = [0.5776, 1.0, 0.3515];
export const LAMP_ON = 3.2;

/** Camera: it orbits the target at DIST (az + = to the viewer's right). FRAME_W x FRAME_H metres stay in frame. */
export const TARGET: Vec3 = [0, 0.09, 0];
export const DIST = 0.78;
export const AZ_REST = -8;
export const EL_REST = 68;
export const AZ_LEAN = -3;
export const EL_LEAN = 64;
export const ZOOM_LEAN = 0.02;
export const FRAME_W = 0.35;
export const FRAME_H = 0.2;
export const CAMERA_NEAR = 0.04;
export const CAMERA_FAR = 30;

/** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
export const IBL_INTENSITY = 24_000;
export const KEY_DIRECTION: Vec3 = [-0.35, -0.85, -0.4];
export const KEY_LUX = 60_000;
export const FILL_DIRECTION: Vec3 = [0.7, -0.4, -0.55];
export const FILL_LUX = 12_000;
export const EXPOSURE = 0;

/** Palette (sRGB): a dark studio. */
export const BACKDROP = "#24211E";
export const PANEL = "#1B1A19";
export const CARD = "#262523";
export const INK = "#F3F0EA";
export const MUTE = "#9D9890";
export const HAIR = "rgba(243,240,234,0.09)";
export const REC = "#FF5A4A";
export const CHANNEL_COLOURS = ["#FF7A59", "#F5C04A", "#4FC3B0", "#A68BFF"] as const;

/** Copy: an invented podcast recording app. */
export const APP = "Booth";
export const EPISODE = "Episode 42";
export const NAMES = ["Host", "Guest", "Music", "SFX"] as const;
export const PRESET_NAMES = ["Intro", "Interview", "Outro"] as const;
