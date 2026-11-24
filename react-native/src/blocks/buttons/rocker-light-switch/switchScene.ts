// The scene's fixed numbers, shared by the Filament stage, the Skia light layer and the panel. glTF space: y up,
// metres, the wall is the plane z = 0 and faces the camera (+z). The Blender build (outside the repository) writes
// switch.glb with these nodes at these positions; its previews use the same shots.

export type Vec3 = [number, number, number];

/** The rocker's axis (the node "Rocker"): it turns about x through this point. */
export const ROCKER_PIVOT: Vec3 = [0.032, -0.082, 0.004];
/** The rocker rests at +ROCK (off: bottom half in) or -ROCK (on: top half in). */
export const ROCK = (7 * Math.PI) / 180;
/** Half the rocker's height, and how far in front of the axis its face is (for the touch dot and the hit area). */
export const ROCKER_HALF_H = 0.036;
export const ROCKER_HALF_W = 0.02;
export const ROCKER_FACE_Z = 0.0052;
/** The plate, for the touch target (centre = the pivot's x, y). */
export const PLATE_HALF_W = 0.043;
export const PLATE_HALF_H = 0.061;

/** The sconce's opal globe: centre and radius. The point light sits at its centre. */
export const GLOBE: Vec3 = [-0.066, 0.062, 0.1];
export const GLOBE_R = 0.043;

/** Camera. The frame must hold FRAME_W x FRAME_H metres of wall around the target on every phone, so the vertical
 * field of view is fitted to the stage's aspect (see fovY). */
export const TARGET: Vec3 = [-0.012, 0.023, 0];
export const EYE_REST: Vec3 = [0.085, -0.005, 0.66];
export const EYE_ORBIT: Vec3 = [-0.07, 0.005, 0.662];
export const FRAME_W = 0.305;
export const FRAME_H = 0.44;
export const CAMERA_NEAR = 0.02;
export const CAMERA_FAR = 10;

/** Light (Filament units). The IBL is Poly Haven's photo_studio_loft_hall baked with cmgen, kept low: it is the dusk. */
export const IBL_INTENSITY = 9_000;
/** The bulb: a point light inside the globe (lumens at full brightness) with a long falloff. */
export const BULB_LUMENS = 42_000;
export const BULB_FALLOFF = 2.5;
/** The room's warm bounce when the light is on: a soft directional fill from the globe's side. */
export const BOUNCE_DIRECTION: Vec3 = [0.35, -0.3, -0.89];
export const BOUNCE_LUX = 16_000;
/** The globe's glow (emissiveFactor at full brightness, linear) and the plate shadow's colour (linear) and alpha. */
export const GLOBE_EMISSIVE = 7;
export const SHADOW_RGB: Vec3 = [0.0242, 0.0152, 0.0103];
export const EXPOSURE = 0;

/** A filament warming up: the light's colour (linear RGB) when it barely glows, and at full brightness. */
export const WARM_LOW: Vec3 = [1, 0.42, 0.14];
export const WARM_FULL: Vec3 = [1, 0.78, 0.52];

/** Palette (sRGB). */
export const DUSK = "#16233F";
export const PANEL = "#F5F2EC";
export const CARD = "#FFFFFF";
export const INK = "#1D1B18";
export const MUTE = "#7A746B";
export const AMBER = "#E39B2E";

/** Copy: an invented smart-home app. */
export const APP = "Lumen Home";
export const ROOM = "Living room";
export const LIGHT_NAME = "Wall sconce";
/** Lights on elsewhere in the room (the sconce makes it one more). */
export const OTHER_LIGHTS_ON = 2;
