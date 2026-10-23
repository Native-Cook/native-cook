/**
 * Pulse Heart Card — every timing value of the living heart, as pure functions.
 *
 * The heart is drawn by a Skia RuntimeEffect (`heart-shader.ts`). The UI thread computes one
 * small set of numbers per frame from "time since the last lub" and hands them to the shader as
 * uniforms, so the Swift (Metal) and Kotlin (AGSL) ports can reuse these functions value for value.
 *
 * Units: seconds, and "ref pt" (points of the 402 pt wide pin screen).
 */

/** Autoplay loop: six beats at 62 BPM, then the heart rests (≈ 20 % of the loop: 0–0.14 s and 6.52–8 s). */
export const LOOP = 8;
export const REST_BPM = 62;
export const BEAT = 60 / REST_BPM;
/** First R-peak of the loop. The ECG pen enters the left edge half a beat before it. */
export const FIRST_LUB = 0.62;
export const LUBS = 6;
/** A finger lifted for this long hands the heart back to the autoplay loop (from rest). */
export const IDLE_RESET = 2;
/** The chart selection fades out over the last 200 ms of that wait. */
export const CURSOR_FADE = 0.2;
/** "Never happened" for a time-since-beat. */
export const NEVER = 99;

export const REFERENCE_WIDTH = 402;
export const REFERENCE_SAFE_TOP = 62;
/** The page scrolls a little: the pin's next row of metrics sits under the tab bar. */
export const REFERENCE_HEIGHT = 912;

/** The final fixture is the resting 62 BPM reading. */
export const SAMPLES = [
  82, 100, 100, 110, 100, 88, 65, 90, 60, 62,
  60, 43, 43, 120, 100, 82, 84, 60, 84, 62,
] as const;

export type BarBounds = {
  x: number;
  width: number;
  y0: number;
  y1: number;
  /** The pin draws a range as one or more capsules with small gaps. */
  segments: readonly (readonly [number, number])[];
};

/** Measured on the pin (ref pt), in the same fixture order as SAMPLES. */
const SEGMENTS: readonly (readonly [number, number])[][] = [
  [[679, 696.3]],
  [[670.3, 687]],
  [[670.7, 693]],
  [[675, 679], [681.7, 693.3]],
  [[671, 678.3], [682, 695]],
  [[678.3, 696.7]],
  [[685, 697.7]],
  [[677.3, 697.3]],
  [[688.3, 698.3]],
  [[686.3, 702]],
  [[688, 702.7]],
  [[696.7, 702.3]],
  [[697.3, 702.7]],
  [[660.7, 675], [680.7, 697.3]],
  [[672.3, 691]],
  [[682.3, 694.3]],
  [[681, 699]],
  [[688, 699.3]],
  [[679.3, 682.7], [684.3, 688.3], [692.7, 698.7]],
  [[675.7, 702.3]],
];

export const BAR_BOUNDS: readonly BarBounds[] = SEGMENTS.map((segments, index) => ({
  x: 34 + index * 7.25,
  width: 3.8,
  y0: segments[0][0],
  y1: segments[segments.length - 1][1],
  segments,
}));

/* ------------------------------------------------------------------------------------------ */
/* The beat: a damped spring kicked at each lub (S1) and, weaker, at each dub (S2).           */
/* ------------------------------------------------------------------------------------------ */

export const SPRING = {
  /** Ringing period of the heart's flesh (s): ~3.3 Hz reads as a soft, heavy muscle. */
  period: 0.3,
  /** Exponential decay time (s): one visible rebound, a faint second one. */
  decay: 0.1,
};
const OMEGA = (2 * Math.PI) / SPRING.period;
/** Normalises the first peak of the impulse response to 1 (peak at ~0.054 s). */
const PEAK = (() => {
  const t = Math.atan(OMEGA * SPRING.decay) / OMEGA;
  return Math.exp(-t / SPRING.decay) * Math.sin(OMEGA * t);
})();

export const BEAT_SHAPE = {
  /** Dub follows the lub by this long, or by this share of the period at fast rates. */
  dubDelay: 0.3,
  dubShare: 0.36,
  dubWeight: 0.45,
  /** Whole-body expansion at the lub's peak (+5.2 %). */
  body: 0.052,
  /** Pre-beat gather: the heart draws in by 1.4 % over 90 ms before the lub. */
  anticipation: 0.014,
  anticipationLead: 0.09,
  /** The lobes are heavier than the body: they arrive 35 ms late and overshoot. */
  lobe: 0.024,
  lobeLag: 0.035,
  /** Width bulge after the peak (positive = wider and shorter), 70 ms late. */
  squash: 0.022,
  squashLag: 0.07,
  /** The heart hops up by this many ref pt at the peak. */
  lift: -2.4,
  /** Halo ring pushed outwards by this many ref pt, 20 ms after the lub. */
  push: 7,
  pushLag: 0.02,
  /** Wet gloss: rises in 25 ms, fades over 320 ms. */
  glossRise: 0.025,
  glossFade: 0.32,
  /** The highlight slides this far (light-direction units) towards the centre and back. */
  light: 0.28,
  lightRise: 0.06,
  lightFade: 0.4,
  /** The reading flashes white on the R-peak and fades over 200 ms. */
  flashFade: 0.2,
};

/** Normalised impulse response of the heart's flesh, 0 before the kick. */
export function kick(tau: number): number {
  'worklet';
  if (tau < 0 || tau > 1.2) return 0;
  return (Math.exp(-tau / SPRING.decay) * Math.sin(OMEGA * tau)) / PEAK;
}

function rise(tau: number, riseTime: number, fadeTime: number): number {
  'worklet';
  if (tau < 0 || tau > 3) return 0;
  return (1 - Math.exp(-tau / riseTime)) * Math.exp(-tau / fadeTime);
}

function gather(untilNext: number): number {
  'worklet';
  const lead = BEAT_SHAPE.anticipationLead;
  if (untilNext <= 0 || untilNext >= lead) return 0;
  const s = Math.sin((Math.PI * (lead - untilNext)) / lead);
  return s * s;
}

export type Beat = {
  body: number;
  lobe: number;
  squash: number;
  lift: number;
  gloss: number;
  light: number;
  push: number;
  flash: number;
};

export const REST_BEAT: Beat = { body: 0, lobe: 0, squash: 0, lift: 0, gloss: 0, light: 0, push: 0, flash: 0 };

/**
 * Everything the heart does at one instant.
 * tau0/tau1: seconds since the last and the previous lub (NEVER if none);
 * untilNext: seconds until the next lub (NEVER if none is coming); period: seconds per beat.
 */
export function beatAt(tau0: number, tau1: number, untilNext: number, period: number): Beat {
  'worklet';
  const B = BEAT_SHAPE;
  const dub = Math.min(B.dubDelay, B.dubShare * period);
  const both = (lag: number) => kick(tau0 - lag) + kick(tau1 - lag);
  const lub = both(0);
  const dubs = (both(dub) * B.dubWeight);
  const gloss = Math.min(1,
    rise(tau0, B.glossRise, B.glossFade) + rise(tau1, B.glossRise, B.glossFade)
    + B.dubWeight * (rise(tau0 - dub, B.glossRise, B.glossFade) + rise(tau1 - dub, B.glossRise, B.glossFade)));
  return {
    body: B.body * (lub + dubs) - B.anticipation * gather(untilNext),
    lobe: B.lobe * (both(B.lobeLag) + B.dubWeight * both(dub + B.lobeLag)),
    squash: B.squash * (both(B.squashLag) + B.dubWeight * both(dub + B.squashLag)),
    lift: B.lift * (lub + dubs),
    gloss,
    light: B.light * Math.min(1, rise(tau0, B.lightRise, B.lightFade) + rise(tau1, B.lightRise, B.lightFade)),
    push: B.push * Math.max(0, both(B.pushLag) + B.dubWeight * both(dub + B.pushLag)),
    flash: tau0 >= 0 && tau0 < 1.5 ? Math.exp(-tau0 / B.flashFade) : 0,
  };
}

/* ------------------------------------------------------------------------------------------ */
/* Autoplay: a pure function of loop time.                                                    */
/* ------------------------------------------------------------------------------------------ */

export type Pulse = {
  /** Continuous beat count: an integer at every R-peak. Drives the ECG pen. */
  phi: number;
  /** Trace is drawn for beat counts in [traceStart, traceEnd]; it fades after traceEnd. */
  traceStart: number;
  traceEnd: number;
  tau0: number;
  tau1: number;
  untilNext: number;
  period: number;
};

/** The ECG pen enters at the left edge (half a beat before the R-peak at the centre). */
export const TRACE_LEAD = 0.5;
/** Old trace fades with age (per beat) like phosphor; a gap opens ahead of the pen. */
export const TRACE = { decay: 1.25, gap: 0.93, fadeOut: 0.6 };

export function autoplayAt(time: number): Pulse {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  const phi = (t - FIRST_LUB) / BEAT;
  const last = Math.min(Math.floor(phi), LUBS - 1);
  const tau0 = last >= 0 ? t - (FIRST_LUB + last * BEAT) : NEVER;
  const tau1 = last >= 1 ? tau0 + BEAT : NEVER;
  const next = last + 1;
  const untilNext = next <= LUBS - 1 ? FIRST_LUB + next * BEAT - t : NEVER;
  return {
    phi,
    traceStart: -TRACE_LEAD,
    traceEnd: LUBS - 1 + TRACE_LEAD,
    tau0,
    tau1,
    untilNext,
    period: BEAT,
  };
}

/**
 * Below these the beat is invisible (body/lobe/squash as a share of size, lift/push in ref pt, the rest 0–1),
 * so the autoplay frame is the pin's and the shader need not redraw: the loop's rest stretch.
 */
export const IDLE = { shape: 0.0002, lift: 0.02, push: 0.03, gloss: 0.02, light: 0.01, flash: 0.01, ripple: 0.75 };

/** True when nothing on the heart moves: the beat has died away, its ripples are gone and the ECG trace has faded. */
export function autoplayIdle(beat: Beat, tau0: number, phi: number, traceStart: number, traceEnd: number): boolean {
  'worklet';
  if (phi >= traceStart && phi <= traceEnd + TRACE.fadeOut) return false;
  if (tau0 < IDLE.ripple) return false;
  return Math.abs(beat.body) < IDLE.shape && Math.abs(beat.lobe) < IDLE.shape && Math.abs(beat.squash) < IDLE.shape
    && Math.abs(beat.lift) < IDLE.lift && Math.abs(beat.push) < IDLE.push
    && beat.gloss < IDLE.gloss && beat.light < IDLE.light && beat.flash < IDLE.flash;
}

/* ------------------------------------------------------------------------------------------ */
/* Chart scrub helpers.                                                                       */
/* ------------------------------------------------------------------------------------------ */

export function clampIndex(index: number): number {
  'worklet';
  return Math.min(Math.max(Math.round(index), 0), SAMPLES.length - 1);
}

export function sampleTime(index: number): string {
  const hour = Math.floor(index / 2) === 0 ? 12 : Math.floor(index / 2);
  const minutes = (index % 2) * 30;
  return `${hour}:${String(minutes).padStart(2, '0')} AM`;
}

/** Press feedback on the Heart chip. */
export const PRESS = { scale: 0.97, inMs: 120, outMs: 100 };
