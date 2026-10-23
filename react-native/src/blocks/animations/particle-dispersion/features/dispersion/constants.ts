import type { SubjectBounds } from '../../native/subject-segmentation';

export const DISPERSION_BACKGROUND_COLOR = '#7D614B';

/** Master UI clock; the scene derives a slower subject clock from this progress. */
export const DISPERSION_DURATION_MS = 3_900;
export const REDUCED_MOTION_DURATION_MS = 260;
export const DUST_START_SECONDS = 0.417;
export const DUST_DURATION_SECONDS = 2.6;
export const DUST_END_SECONDS = DUST_START_SECONDS + DUST_DURATION_SECONDS;
// Share the first warm scan's clock: the impact finishes before matte reveal.
export const SUBJECT_IMPACT_START_SECONDS = 0.100;
export const SUBJECT_IMPACT_DURATION_SECONDS = 0.280;
export const SUBJECT_IMPACT_AMPLITUDE_RATIO = 0.04;
// Fade the surrounding photo displacement gently to zero before the draw edge.
export const SUBJECT_IMPACT_PADDING_MULTIPLIER = 4;
// Gaussian shadow width, relative to the subject's height.
export const SUBJECT_DARK_SCAN_BLUR_RATIO = 0.18;

export const MASTER_DURATION_SECONDS = DISPERSION_DURATION_MS / 1_000;
// Hold the static part of the reference subject timeline while the longer dust
// sequence plays, then keep the user's slower pop/shadow/scan pacing.
export const SUBJECT_REFERENCE_ANCHOR_SECONDS = 1.35;
export const SUBJECT_MOTION_RATE = 0.8;
export const DEFAULT_SUBJECT_BOUNDS: SubjectBounds = {
  x: 0.18,
  y: 0.08,
  width: 0.64,
  height: 0.84,
};
