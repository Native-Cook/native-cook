import type { SharedValue } from "react-native-reanimated";

// The contract between the widget's state (ember-charge.tsx) and everything
// that draws it (ember-surface.tsx, ember-bar.tsx). The state side writes
// these on the UI thread; the drawing side only reads them. A value that
// both sides need is added here first.

/** How much of the picture is drawn. Decided once, in React; never a shared value. */
export type EmberQuality = "high" | "medium" | "low";

export type EmberMotion = {
  /** 0…1, how far the hold has charged the card. */
  charge: SharedValue<number>;
  /** The charge as heat: rises fast, cools slowly. Drives the light. */
  glow: SharedValue<number>;
  /** How far the light leans, a spring that rings after a kick. */
  slosh: SharedValue<number>;
  /** Time since ignition in shares of `M.ignite`, 0 before it. */
  ignition: SharedValue<number>;
  /** Free-running seconds, for the swell and the embers. */
  time: SharedValue<number>;
  /** Stripe offset in points, 0…period. */
  drift: SharedValue<number>;
  /** Heart beat phase, 0…1. */
  phase: SharedValue<number>;
  /** Share of the goal, 0…1. */
  progress: SharedValue<number>;
  /** Smoothed finger pressure, 0…1. */
  press: SharedValue<number>;
  /**
   * Where the finger is, relative to the card's centre: -1 at the left or
   * top edge, 1 at the right or bottom edge, 0 when nothing is touching.
   * Already spring-smoothed by the state side, so the drawing side may use
   * it directly; it never jumps.
   */
  aimX: SharedValue<number>;
  aimY: SharedValue<number>;
  /**
   * 0…1 as the card appears (the fade the live gate starts). The glass
   * uses it to materialise: its bevel and its bending grow in with it.
   */
  enter: SharedValue<number>;
  quality: EmberQuality;
  reduced: boolean;
};
