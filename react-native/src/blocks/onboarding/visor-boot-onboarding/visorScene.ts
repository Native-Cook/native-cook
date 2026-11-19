// The scene's fixed numbers, shared by the Filament stage, the Skia layers and the overlay. glTF space: y up,
// the helmet faces +z, its own right side is -x (where the side port is). Units are the model's (the helmet is
// about 2.9 tall); lux and linear colours for the lights. The Blender build (outside the repository) writes
// visor.glb with the same PLINTH_H and HELMET_Y, and its previews use the cameras below.

export type Vec3 = [number, number, number];
export type Rgb = [number, number, number];

/** Plinth: a graphite disc from y = 0 to PLINTH_H, radius PLINTH_R. */
export const PLINTH_H = 0.22;
export const PLINTH_R = 1.55;
/** Where the node HelmetRig sits at rest (the bust's lowest point on the plinth). */
export const HELMET_Y = 1.6787;
/** How far the helmet lifts off the plinth in the "Get started" beat. */
export const RISE = 0.14;

/** Camera: the horizontal field of view is fixed, so the helmet keeps its width on every phone. */
export const HFOV_DEG = 36;
export const CAMERA_NEAR = 0.2;
export const CAMERA_FAR = 60;

export type Shot = { eye: Vec3; target: Vec3 };
/** The film's shots. Each one is fitted (by a script next to the Blender build) so the helmet and the plinth stay
 * inside x 0.05..0.95 and y 0.12..0.655 of a 402 x 874 screen: whole silhouette, clear of the copy. */
export const SHOTS = {
  poster: { eye: [-1.95, 2.58, 5.0], target: [0, 0.44, 0.05] } as Shot,
  front: { eye: [0, 2.03, 5.37], target: [0, 0.7, 0.71] } as Shot,
  frontDrift: { eye: [0.42, 2.06, 5.36], target: [0.04, 0.7, 0.71] } as Shot,
  side: { eye: [-5.38, 2.05, -0.06], target: [0, 0.48, -0.01] } as Shot,
  sideDrift: { eye: [-5.35, 2.17, 0.55], target: [-0.02, 0.48, 0.02] } as Shot,
  top: { eye: [1.29, 5.47, 3.02], target: [0.15, 0.24, 0.34] } as Shot,
  topDrift: { eye: [0.98, 5.46, 3.04], target: [0.11, 0.26, 0.32] } as Shot,
  rise: { eye: [-1.28, 1.86, 5.23], target: [0.01, 0.48, -0.02] } as Shot,
  riseDrift: { eye: [-1.52, 2.0, 5.17], target: [0.02, 0.51, -0.02] } as Shot,
} as const;

/** One shot per page of the onboarding (0 = intro / poster, 1–3 = the steps, 4 = Get started), for touch. */
export const PAGE_SHOTS: readonly Shot[] = [SHOTS.poster, SHOTS.frontDrift, SHOTS.sideDrift, SHOTS.topDrift, SHOTS.riseDrift];
export const PAGES = 5;

/** HUD layer: a quad floating just in front of the visor plate (helmet space), in the plate's tilt. Canonical HUD
 * units are HUD_W x HUD_H; TL, TR, BR, BL. */
export const HUD_QUAD: readonly Vec3[] = [
  [-0.9, 0.886, 0.933],
  [0.9, 0.886, 0.933],
  [0.9, 0.274, 1.307],
  [-0.9, 0.274, 1.307],
];
/** The visor plate's outward normal (helmet space): the HUD fades as the camera leaves its front. */
export const HUD_NORMAL: Vec3 = [0, 0.52, 0.85];
export const HUD_W = 260;
export const HUD_H = 108;

/** Ear sensor (helmet space): the radar's centre just outside the round ear housing, its normal, and two axes of
 * its plane. */
export const PORT_CENTER: Vec3 = [-0.98, 0, -0.68];
export const PORT_NORMAL: Vec3 = [-1, 0, 0];
export const PORT_AXIS_A: Vec3 = [0, 1, 0];
export const PORT_AXIS_B: Vec3 = [0, 0, -1];
export const RADAR_R = 0.8;

/** Mouthpiece (helmet space) and the plane the voice rings ripple in. */
export const MOUTH: Vec3 = [0, -0.836, 1.26];
export const MOUTH_AXIS_A: Vec3 = [1, 0, 0];
export const MOUTH_AXIS_B: Vec3 = [0, 0.92, -0.4];

/** Light (Filament units; tuned against the 256 px studio_small_08 bake). */
export const IBL_INTENSITY = 24_000;
/** Warm key from the front left, high. */
export const KEY_DIRECTION: Vec3 = [0.55, -0.6, -0.55];
export const KEY_LUX = 52_000;
/** Cool rim from behind, grazing the shell's top and the bust's edge. */
export const RIM_DIRECTION: Vec3 = [0.5, -0.25, 0.85];
export const RIM_LUX = 60_000;
/** The "Get started" light sweep: a hard white light that swings across the front (azimuth in degrees). */
export const SWEEP_LUX = 90_000;
export const SWEEP_FROM_DEG = -70;
export const SWEEP_TO_DEG = 70;

/** Panel lamps (emissive mask in the shell's texture, already amber): strength at level 1, scene-referred. */
export const LAMP_STRENGTH = 1.5;

/** Colours (sRGB) used by the 2D layers and the copy. */
export const C = {
  bgCentre: "#28303A",
  bgMid: "#161B21",
  bgEdge: "#090B0E",
  ink: "#EEF0F2",
  mute: "#99A1AB",
  faint: "#5E6670",
  amber: "#FFC27A",
  amberDeep: "#E8933C",
  hair: "rgba(238,240,242,0.14)",
} as const;
