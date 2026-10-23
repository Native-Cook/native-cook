// Icon Morph Sheet — the contract between the 3D icon stage (shader, canvas, scene timeline)
// and the screen (page, sheet, copy, controls, gestures, demo loop).
//
// The icon is ONE clay object that lives in the middle of a four-step bottom sheet. Each step shows a
// different object; a step change is a true morph of the same matter (shape, material and colour move
// continuously), never a cross-fade and never a swap.
//
// Model space: x right, y up, z toward the viewer. The object at rest fits a sphere of radius ~1 around
// the origin (satellites and flying coins may reach ~1.55). Camera: a pinhole on the +z axis at CAMERA
// model units from the origin, looking at the origin. Key light: upper left, in front.
import type { SharedValue } from 'react-native-reanimated';

export const STEP_COUNT = 4;
export const CAMERA = 9;

/** The sheet's fill behind the stage (the stage is transparent; shadows and glows are drawn for this colour). */
export const SHEET_COLOUR = '#26282E';

/**
 * Each step's own loop, in the step's authored clock. A loop starts in the step's entry pose (local time 0)
 * and returns to it. STEP_PACE is how fast that clock runs against real seconds: the loops last
 * 2.0, 2.3, 2.4 and 2.0 real seconds, the pace of the reference's icons.
 */
export const STEP_LOOP = [3.2, 3.4, 3.4, 2.8] as const;
export const STEP_PACE = [1.6, 3.4 / 2.3, 3.4 / 2.4, 1.4] as const;

/**
 * What each step shows (the stage owns every detail of it):
 * 0 "zero fees"  — a grey clay "0"; gold coins pinch off it, tumble around it and are taken back in.
 *                  Entry pose: the "0" alone.
 * 1 "safe sends" — a white coin flips over and becomes a red siren on a dark round plate with a blue
 *                  shield badge at its upper right; later it flips back. Entry pose: the white coin, face on.
 * 2 "envelope"   — a blue envelope opens, a white note rises out of it, two emoji faces bud off it.
 *                  Entry pose: the closed envelope.
 * 3 "link bank"  — a yellow passbook with white pages; a green check badge buds at its upper right corner,
 *                  the book flops over and turns green, then back. Entry pose: the yellow book, no badge.
 */
export type IconStageProps = {
  /** Canvas size in points. */
  width: number;
  height: number;
  /** Where the model's origin lands on the canvas, in points. */
  centreX: number;
  centreY: number;
  /** Points per model unit at z = 0. */
  unit: number;
  /**
   * Where the flow is: 0 = step 1 … 3 = step 4. A fraction is a morph between the two neighbouring steps,
   * and it is a pure function of this number: the screen may scrub it with a finger in either direction,
   * hold it anywhere, or reverse it mid-way. Never outside 0…STEP_COUNT-1 (the screen clamps; any
   * overshoot arrives through `bounce`).
   */
  position: SharedValue<number>;
  /** Seconds, monotonic, advanced by the screen on the UI thread. The stage keeps no clock of its own. */
  clock: SharedValue<number>;
  /**
   * 0: every step sits frozen in its entry pose (local time 0). 1: the step at rest plays its loop.
   * A step's local time runs only while `position` rests on it, freezes while the flow morphs away from
   * or back to it, and returns to 0 once the flow rests on another step.
   */
  running: SharedValue<number>;
  /** A spring's residual around 0 (about -1…1, 0 at rest): the stage adds it as scale overshoot and wobble. */
  bounce: SharedValue<number>;
  /** -1…1, 0 at rest: the object leans (yaw) with the finger while a horizontal drag scrubs the flow. */
  lean: SharedValue<number>;
  /** 0…1: a finger is pressing the object itself; it squashes like soft clay. */
  press: SharedValue<number>;
  /** Reduce Motion: no spin, no loops; each step shows its hero still and a step change is a plain, slow morph. */
  reducedMotion: boolean;
  /** Called with true once the first frame can be drawn. */
  onReady?: (ready: boolean) => void;
};
