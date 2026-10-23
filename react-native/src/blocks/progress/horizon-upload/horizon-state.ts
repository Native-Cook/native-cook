import type { DerivedValue, SharedValue } from "react-native-reanimated";

// What the upload is doing, and the shared values every part of the block
// draws from. One hook (horizon-upload.ts) owns and writes them; the scene,
// the file and the card only read.

export type HorizonPhase = "idle" | "uploading" | "paused" | "done" | "stopped";

/** A number that changes on the UI thread. Readers call `.get()` inside a worklet and never write. */
export type Signal = SharedValue<number> | DerivedValue<number>;

export type HorizonFx = {
  /** 0 to 1, already smoothed: the bar's fill and how far the dawn has spread along the horizon, from its top behind the card out to both edges of the screen. */
  progress: Signal;
  /** How lit the scene is: about 0.2 idle, 1 uploading and done, 0.4 paused, 0.1 stopped. */
  power: Signal;
  /** 1 when a chunk arrives, back to 0 over horizonMotion.pulse. */
  pulse: Signal;
  /** The completion's clock: 0 until the upload finishes, then 0 to 1 linearly over horizonMotion.crest, and 1 while done. */
  crest: Signal;
  /** 0 to 1 once the upload has succeeded, on a spring; what stays changed after the celebration. */
  done: Signal;
  /** 0 to 1 while the panel is held. */
  press: Signal;
  /** 0 to 1 while the close button is held. The scene draws the button's glass bead and squeezes it with this. */
  closePress: Signal;
  /** The card's displacement from rest while it is dragged, in points. The page and the horizon stay where they are; only the pane and its content move. */
  shiftX: Signal;
  shiftY: Signal;
  /** 0 while the card rests over the horizon, 1 once it has risen horizonSize.lift points after a completed upload. May overshoot 1 briefly, and carries the slow hover while done. */
  lift: Signal;
};

/** Where `t` is between `from` and `to`, clamped to 0…1. */
export function part(t: number, from: number, to: number) {
  "worklet";
  return Math.min(Math.max((t - from) / (to - from), 0), 1);
}

// The completion, as spans of `crest`. Everything in the block that takes
// part in it reads its cue from here, so the parts stay in step.
//   inhale  the card draws in and the scene dims a touch
//   flare   the light reaches both edges and a streak of flare opens along the horizon
//   sweep   a band of light climbs through the glass, bottom to top
//   flip    the file's badge turns over from the extension to a check
//   lift    the card rises off the horizon and the sun breaks out beneath it
//   settle  the bloom decays to a calm glow, the card hovers where it rose
export const HERO = {
  inhale: [0, 0.12],
  flare: [0.12, 0.3],
  sweep: [0.25, 0.7],
  flip: [0.4, 0.62],
  lift: [0.22, 0.6],
  settle: [0.7, 1],
} as const;

/** The card's scale: drawn in before the flare, back with a small overshoot as the sweep passes. The glass and the content both use it, about the card's center. */
export function cardScale(crest: number, press: number) {
  "worklet";
  const inhale = part(crest, HERO.inhale[0], HERO.inhale[1]);
  const release = part(crest, HERO.flare[1], HERO.settle[0]);
  // Ease out into the squeeze, then a damped overshoot back to 1.
  const squeeze = 1 - (1 - inhale) * (1 - inhale);
  const back = 1 - Math.exp(-6 * release) * Math.cos(9 * release);
  return 1 - 0.018 * squeeze * (1 - back) - 0.008 * press;
}
