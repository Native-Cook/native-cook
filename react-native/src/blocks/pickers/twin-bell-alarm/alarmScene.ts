// The scene's fixed numbers, shared by the Filament stage, the motion and the panel. glTF space: y up, metres, the
// floor is y = 0 and the dial faces the viewer (+z). The Blender build (outside the repository) writes alarm.glb with
// these nodes at these places; its previews use the same camera.

export type Vec3 = [number, number, number];

/** The hands turn about z through the dial's centre (clockwise for the viewer = a negative angle); the model has
 * both pointing at 12. */
export const HOUR_PIVOT: Vec3 = [0, 0.092, 0.0263];
export const MINUTE_PIVOT: Vec3 = [0, 0.092, 0.0276];
/** The hammer swings about z through its pivot on top of the drum; HAMMER_REACH (deg) is where its striker meets a
 * bell's rim. */
export const HAMMER_PIVOT: Vec3 = [0, 0.156, 0];
export const HAMMER_REACH = 10.9;
/** Each bell shakes about z through the foot of its post. */
export const BELL_R_PIVOT: Vec3 = [0.03145, 0.15116, 0];
export const BELL_L_PIVOT: Vec3 = [-0.03145, 0.15116, 0];
/** Set to false to leave the glass out (it is the scene's one blended surface besides the contact shadow). */
export const GLASS_ON = true;

/** Camera: it orbits the target at DIST (az 0 = straight in front, + to the viewer's right; el above the floor).
 * The frame holds FRAME_W x FRAME_H metres around the target on every phone (see tanHalfFovY). */
export const TARGET: Vec3 = [0, 0.142, 0];
export const DIST = 0.82;
export const AZ_REST = -12;
export const EL_REST = 10;
export const FRAME_W = 0.25;
export const FRAME_H = 0.36;
export const CAMERA_NEAR = 0.03;
export const CAMERA_FAR = 30;

/** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
export const IBL_INTENSITY = 30_000;
export const KEY_DIRECTION: Vec3 = [0.4, -0.85, -0.35];
export const KEY_LUX = 72_000;
export const FILL_DIRECTION: Vec3 = [-0.7, -0.3, -0.6];
export const FILL_LUX = 15_000;
export const EXPOSURE = 0;

/** Palette (sRGB). */
export const BACKDROP = "#B9C5DA";
export const PANEL = "#FBFAF7";
export const CARD = "#FFFFFF";
export const INK = "#1C1D22";
export const MUTE = "#767A86";
export const HAIR = "rgba(28,29,34,0.08)";
export const YELLOW = "#E9A61C";

/** Copy: an invented alarm app. The alarm rests at REST_MIN and the film sets it to FILM_MIN (minutes of the day);
 * "now" is NOW_MIN, for the "Rings in" line. */
export const APP = "Earlybird";
export const REPEATS = "Weekdays";
export const REST_MIN = 7 * 60;
export const FILM_MIN = 6 * 60 + 30;
export const NOW_MIN = 22 * 60 + 18;
/** The wheel moves in steps of this many minutes. */
export const WHEEL_STEP = 5;
