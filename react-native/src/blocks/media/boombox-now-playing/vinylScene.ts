// The set, in the glTF's units (metres): a 12-inch record and its sleeve floating upright over studio paper, faces
// toward +z. Built by blender/boombox-now-playing/build5.py (outside the repository); no other models.

export type Vec3 = readonly [number, number, number];

/** A large soft key from the front left and above, a cool rim from behind right; the studio IBL fills the rest. */
export const KEY_DIRECTION: Vec3 = [0.55, -0.6, -0.75];
export const RIM_DIRECTION: Vec3 = [-0.5, -0.4, 0.8];
export const KEY_LUX = 60_000;
export const RIM_LUX = 28_000;
export const IBL_INTENSITY = 32_000;
/** Exposure of the colour grading (stops). */
export const EXPOSURE_STOPS = 0.25;

export const CAMERA_NEAR = 0.02;
export const CAMERA_FAR = 20;

/** Toward the key light (unit): the groove highlight lies along the half vector between it and the eye. */
const KL = Math.hypot(KEY_DIRECTION[0], KEY_DIRECTION[1], KEY_DIRECTION[2]);
export const TO_KEY: Vec3 = [-KEY_DIRECTION[0] / KL, -KEY_DIRECTION[1] / KL, -KEY_DIRECTION[2] / KL];
/** Brightness of the groove highlight (emissive factor of the sheen discs). */
export const SHEEN_GLOW = 0.9;

/** Studio paper: lighter at the top, a touch warmer where the floor turns into the wall. */
export const PAPER_TOP = "#F4F2EE";
export const PAPER_BOTTOM = "#E4DFD8";
