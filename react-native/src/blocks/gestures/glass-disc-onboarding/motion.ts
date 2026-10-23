/** Every motion value of the block, timed on the source (4 fps frames). */
import { SPACE } from './theme';

export const PULL = {
  // The finger travels ~0.3 H in the source from rest to the release: progress = pull / threshold.
  threshold: 260,
  // Past the threshold the disc gives with rubber: extra = L · over / (over + L).
  rubber: 120,
  // Release at or past this progress (or flick up faster than flickVelocity past flickMin) commits.
  commitAt: 0.85,
  flickVelocity: -900,
  flickMin: 0.4,
  // The iridescent band: in from the first moment, peaks at ringPeakAt, gone by ringOutAt (frames 3–12).
  ringPeakAt: 0.42,
  ringOutAt: 0.62,
  // The hidden headline shows through the glass from revealFrom to revealTo (frames 12–17).
  revealFrom: 0.52,
  revealTo: 0.72,
  // The intro fades over the first part of the pull (frames 3–9).
  introOutBy: 0.62,
  hintOutBy: 0.2,
  tickEvery: 0.125,
} as const;

export const BACK = {
  // Dragging the bead down: it grows to its lens size over growBy points; past commitBy the screen rewinds.
  growBy: 300,
  commitBy: 200,
  flickVelocity: 900,
  flickMin: 90,
} as const;

export const RISE = {
  // After landing the bead rises and tightens while the bubbles leave (frames 28 → 40).
  ms: 1600,
} as const;

// Springs in the designer form (animate-expo §5): reposition / snap back after a drag, the finger's velocity handed over.
export const SPRING = {
  // The dome falling back when let go short.
  back: { duration: 400, dampingRatio: 0.8 },
  // Lens → bead (frames 25–27, ~0.5 s).
  bead: { duration: 400, dampingRatio: 0.8 },
  // Bead → dome on a rewind.
  dome: { duration: 400, dampingRatio: 0.8 },
} as const;

// Strong ease-out for fades in and out (animate-expo §5; never ease-in on UI).
export const EASE_OUT = [0.23, 1, 0.32, 1] as const;

export const FADE = {
  settle: 260,
  // Headline in as the bead lands; the rest of the screen 250 ms later over 750 ms (frames 27–31).
  headlineMs: 260,
  restDelay: 250,
  restMs: 750,
  // Everything of the second screen out on a rewind (frames 66–67), the intro back.
  outMs: 200,
  introBack: 280,
  reducedMs: 200,
} as const;

export const BURST = {
  // Bubbles start 0.75 s after the bead lands (frame 30) and are out by frame 40.
  delayMs: 650,
  staggerMs: 26,
  // They leave the bead small and grow as they climb.
  startScale: 0.12,
  growOver: 150,
  // …and are full size this long after leaving, however short their climb.
  growMs: 520,
  launchSpeed: 320,
} as const;

export const STICKERS = {
  step: 1 / 120,
  stiffness: 14,
  damping: 5.2,
  separation: 0.5,
  // Neighbours may overlap: they are only pushed apart closer than this share of their radii (the source's marbles overlap).
  overlap: 0.62,
  // Finger pushes bubbles within this radius.
  push: 56,
  float: 2.2,
  floatPeriodMs: 4200,
  // Rewind: the cloud drifts up and off the top (frames 67–83), staggered from the top down.
  exitLift: 1.1,
  exitStiffness: 5,
  exitDamping: 3.5,
  exitStaggerMs: 30,
  maxFrameMs: 250,
} as const;

// One autoplay period; it ends on the frame it started with, so the preview loops without a jump.
export const LOOP_MS = 12500;

// QA only: freezes the autoplay script at this time (ms); -1 in the shipped block.
export const DEBUG_FREEZE_MS = -1;

export const AUTOPLAY = {
  resumeDelayMs: 3500,
  // The thumb starts on the dome's right side, as in the source.
  startX: 0.66,
  startY: 0.84,
  pullStart: 600,
  pullEnd: 3400,
  pullBy: 236,
  // A finger sweeps through the cloud (frames 41–48)…
  sweepStart: 6700,
  sweepEnd: 7600,
  // …then drags the bead down to rewind (frames 52–66).
  backStart: 8200,
  backEnd: 9500,
  backBy: 250,
} as const;

/** The upgrade on top of the replica (docs/gestures-design/glass-disc-onboarding.md → Upgrade options). */
export const UPGRADE = {
  // Touch-down: the dome lifts 4 pt within a frame and its rim brightens (anticipation).
  nudge: SPACE.xs,
  nudgeSpring: { duration: 300, dampingRatio: 0.8 },
  // Squash and stretch from the disc's vertical speed (pt/s → share), eased towards its target each frame.
  stretchPerSpeed: 1 / 9000,
  stretchMax: 0.14,
  stretchFollow: 0.25,
  // Dispersion grows with speed.
  dispersionPerSpeed: 1 / 1400,
  dispersionMax: 1.6,
  // A glint runs round the resting dome's rim every 6 s (ambient), and once on commit.
  glintEveryMs: 6000,
  glintMs: 1300,
  glintFrom: -2.7,
  glintTo: -0.45,
  // The ring's light on the backdrop.
  glow: 0.35,
  // Payoff: three pressure rings leave the bead with the bubbles, and the bead tightens with each.
  pulses: [0, 230, 460],
  pulseMs: 760,
  pulseGrow: 96,
  pulseSquash: 0.06,
  // Bubbles stretch along their flight, and take a share of a finger's speed.
  wobblePerSpeed: 1 / 2600,
  wobbleMax: 0.16,
  fingerCarry: 0.35,
  // Second screen: lines rise 8 pt (SPACE.sm; was 6) as they fade in, 60 ms apart.
  rise: SPACE.sm,
  lineStagger: 60,
  // The glow field behind the second screen drifts ±8 pt (SPACE.sm; was 10) over 8 s.
  fieldDrift: SPACE.sm,
  fieldPeriodMs: 8000,
  // The hint rests a little dimmer and brightens with the glint.
  hintBase: 0.82,
  // A pressed button: the lens and the 0.97 press arrive fast, and let go slower (asymmetric).
  pressIn: 100,
  pressOut: 220,
  pressScale: 0.97,
} as const;
