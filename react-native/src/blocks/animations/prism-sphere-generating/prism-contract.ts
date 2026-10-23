import type { SharedValue } from 'react-native-reanimated';

import type { Mat3 } from './prism-math';

/**
 * The contract between the scene (PrismScene.tsx, prism-shader.ts, prism-pose.ts) and the screen
 * (PrismSphereGenerating.tsx, use-prism-motion.ts, PrismMotes.tsx).
 *
 * The core is a ribbed shell hinged on the core-space Y axis: its ribs are meridians about Y and it
 * covers a wedge of `fan` radians of azimuth about Y (PI = a half shell, a bowl; towards 2 PI = a
 * closed ribbed ball).
 */
export type PrismPose = {
  /** Core to view. */
  rotation: Mat3;
  /** Radians of azimuth the shell covers, PI … 1.975 PI. */
  fan: number;
};

/** `prismPose(phase)` in prism-pose.ts: phase 0 … 1 is one 5-second tumble, and prismPose(0) equals prismPose(1). */
export type PrismPoseFn = (phase: number) => PrismPose;

/** Props of both Skia nodes exported by PrismScene.tsx: `PrismBackdrop` and `PrismSphere`. */
export type PrismSceneProps = {
  /** Canvas size in points. */
  width: number;
  height: number;
  /** The sphere at rest, in points. */
  cx: number;
  cy: number;
  radius: number;
  /** Core to view, already including drag and spin. */
  rotation: SharedValue<Mat3>;
  fan: SharedValue<number>;
  /** Seconds. Anything the shader animates from it must repeat every 5 s. */
  clock: SharedValue<number>;
  /** Light inside the glass: 0.4 at rest, up to 1.5 in the flash. */
  energy: SharedValue<number>;
  /** A band of light running along the ribs from one hinge pole (0) to the other (1). */
  sweep: SharedValue<number>;
  /** How bright that band is, 0 … 1. */
  sweepPower: SharedValue<number>;
  /** 0 … 1: light gathered into the core before a burst (halo pulls in, a hot point grows at the hinge). */
  charge: SharedValue<number>;
  /** 0 … 1: the burst itself (rim flare, wider prism fringe, bloom on the backdrop). Decays to 0. */
  flash: SharedValue<number>;
  /** 0 = no ring; 0 … 1 = a ring of light leaving the silhouette for 2.4 radii, fading as it goes. */
  ring: SharedValue<number>;
  /** The sphere's drawn scale about its centre, 0.96 … 1.05. */
  scale: SharedValue<number>;
  /** Where the key light leans, each −1 … 1 (x right, y down); [0, 0] at rest. */
  light: SharedValue<number[]>;
};
