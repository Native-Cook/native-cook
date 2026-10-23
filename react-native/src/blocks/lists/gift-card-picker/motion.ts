import { Easing } from 'react-native-reanimated';

// The three curves of the motion system (emil-design-eng / animate-expo).
export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1); // enter and exit
export const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1); // on-screen movement and morph
export const EASE_SHEET = Easing.bezier(0.32, 0.72, 0, 1); // sheets
// Plain-function versions for worklets that shape a progress themselves.
export const EASE_OUT_FN = Easing.bezierFn(0.23, 1, 0.32, 1);
export const EASE_IN_OUT_FN = Easing.bezierFn(0.77, 0, 0.175, 1);

// Timings stepped from the pin's video (30 fps). Pin time + 0.4 s = loop time.

// Sheet slides up over the portfolio (pin 0.10 → 0.40 s).
export const PRESENT = { duration: 300, easing: EASE_SHEET }; // pin: 0.10 → 0.40 s
// Not in the pin (it cuts back to the portfolio); the reverse of the entry so the loop closes.
export const DISMISS = { duration: 240, easing: EASE_SHEET }; // exits faster than entrances

// stage 0 → 1: blob collapses into the sphere (pin 2.70 → 3.40 s), linear in stage;
// the shape of each beat lives in the stage → look mapping below.
export const DISCOVER_MS = 700;
// The sphere spins while the amount is "discovered" (pin 3.40 → 5.80 s).
export const DISCOVERING_MS = 2400;
// stage 1 → 2: sphere melts back into a wider blob with the amount (pin 5.80 → 6.40 s).
export const REVEAL_AMOUNT_MS = 600;

// Breakpoints inside a stage, as fractions of the stage's duration.
export const COLLAPSE = {
  settle: 0.243, // 2.70 → 2.87: blob eases to 93 %
  vanish: 0.471, // 2.87 → 3.03: blob falls into the centre
  sphereIn: 0.3, // sphere starts fading in
  sphereFrom: 0.35, // sphere scale when it appears
};
export const EXPAND = {
  shrink: 0.217, // 5.80 → 5.93: sphere eases to 93 %
  fall: 0.383, // 5.93 → 6.03: sphere drops to 60 %
  blobIn: 0.333, // 6.00: wide blob starts growing from 30 %
  blobFrom: 0.3,
  fadeEnd: 0.55,
};

// Push to the dark stock sheet (pin 8.20 → 9.17 s).
export const CLOSE_FADE_MS = 90;
export const PUSH = { delay: 30, duration: 250, easing: EASE_OUT };
export const UNDER_SHIFT = 0.3; // the light sheet slides left by 30 % of the width
export const CONTENT_IN = { delay: 530, duration: 200 }; // skeleton → content
export const LOGO_IN = { duration: 240 };

// A swipe in the pin, tracked on its frames at 10 fps: the finger flicks and lets go almost at
// once; the carousel follows a critically damped spring (ω 4.5 rad/s) that starts with the
// flick's speed (1.2 cards/s). Fitted to the card's path, residual < 2 %.
export const SETTLE = { stiffness: 20.25, damping: 9, mass: 1, velocity: 1.2 };
export const SNAP = { duration: 400, dampingRatio: 0.8 }; // + the finger's velocity
export const LABEL_SWITCH_MS = 270; // the button's label changes 270 ms into the swipe
export const BG_FADE_MS = 450;
export const DESC_FADE_MS = 150;

export const PRESS = { scale: 0.97, in: 120, out: 150 };
export const RM_FADE_MS = 200;

// Self-playing loop (ms) = the pin's recording, which runs 19.47 s and cuts back to the
// portfolio at 19.1 s. Loop time = pin time + 0.4 s, so the loop opens on the portfolio.
export const LOOP_MS = 19870;
export const SCRIPT = {
  present: 500,
  discover: 3100,
  choose: 8600,
  swipes: [10650, 12670, 14770, 17070],
  dismiss: 19500,
};
export const RESUME_AFTER_MS = 3500;

export type GiftCardPickerSnapshot = {
  at: number;
  sheet: number;
  stage: number;
  push: number;
  reveal: number;
  scroll: number;
  active: number;
  backgrounds: number[];
  descriptions: number[];
  layer: number;
};

const clamp01 = (value: number) => Math.min(1, Math.max(0, value));

// Pure sampler used by debug phase captures. It mirrors the same cue times and curves as the
// worklet commands, so a requested timestamp is available on the first render without replaying
// nineteen seconds of animation.
export function giftCardPickerSnapshotAt(milliseconds: number): GiftCardPickerSnapshot {
  const at = Math.min(LOOP_MS - 1, Math.max(0, milliseconds));
  const present = clamp01((at - SCRIPT.present) / PRESENT.duration);
  const dismiss = clamp01((at - SCRIPT.dismiss) / DISMISS.duration);
  const sheet = at < SCRIPT.dismiss ? Easing.bezierFn(0.32, 0.72, 0, 1)(present) : 1 - Easing.bezierFn(0.32, 0.72, 0, 1)(dismiss);

  const discoverElapsed = at - SCRIPT.discover;
  const stage = discoverElapsed <= 0 ? 0
    : discoverElapsed < DISCOVER_MS ? discoverElapsed / DISCOVER_MS
      : discoverElapsed < DISCOVER_MS + DISCOVERING_MS ? 1
        : discoverElapsed < DISCOVER_MS + DISCOVERING_MS + REVEAL_AMOUNT_MS
          ? 1 + (discoverElapsed - DISCOVER_MS - DISCOVERING_MS) / REVEAL_AMOUNT_MS
          : 2;

  const push = EASE_OUT_FN(clamp01((at - SCRIPT.choose - PUSH.delay) / PUSH.duration));
  const revealStart = SCRIPT.choose + PUSH.delay + CONTENT_IN.delay;
  const reveal = at < revealStart ? 0
    : at < revealStart + CONTENT_IN.duration ? (at - revealStart) / CONTENT_IN.duration
      : at < revealStart + CONTENT_IN.duration + LOGO_IN.duration
        ? 1 + (at - revealStart - CONTENT_IN.duration) / LOGO_IN.duration
        : 2;

  let scroll = 0;
  let velocity = 0;
  let springStart = -1;
  let springTarget = 0;
  const springAt = (time: number) => {
    if (springStart < 0) return { value: scroll, velocity };
    const elapsed = Math.max(0, (time - springStart) / 1000);
    const omega = Math.sqrt(SETTLE.stiffness / SETTLE.mass);
    const displacement = scroll - springTarget;
    const coefficient = velocity + omega * displacement;
    const envelope = Math.exp(-omega * elapsed);
    return {
      value: springTarget + envelope * (displacement + coefficient * elapsed),
      velocity: envelope * (coefficient - omega * (displacement + coefficient * elapsed)),
    };
  };
  for (const cue of SCRIPT.swipes) {
    if (cue > at) break;
    const current = springAt(cue);
    scroll = current.value;
    velocity = current.velocity + SETTLE.velocity;
    springTarget = Math.round(scroll) + 1;
    springStart = cue;
  }
  if (springStart >= 0) scroll = springAt(at).value;

  const picks = SCRIPT.swipes.filter((cue) => cue + LABEL_SWITCH_MS <= at).length;
  const active = Math.min(STOCK_COUNT - 1, picks);
  const backgrounds = Array.from({ length: STOCK_COUNT }, (_, index) => {
    if (index === 0 || index < active) return 1;
    if (index > active) return 0;
    const started = SCRIPT.swipes[index - 1] + LABEL_SWITCH_MS;
    const p = clamp01((at - started) / BG_FADE_MS);
    return p < 0.5 ? 2 * p * p : 1 - (-2 * p + 2) ** 2 / 2;
  });
  const descriptions = Array.from({ length: STOCK_COUNT }, (_, index) => {
    if (index === active) {
      if (index === 0) return 1;
      return clamp01((at - (SCRIPT.swipes[index - 1] + LABEL_SWITCH_MS)) / DESC_FADE_MS);
    }
    if (index === active - 1 && active > 0) {
      return 1 - clamp01((at - (SCRIPT.swipes[active - 1] + LABEL_SWITCH_MS)) / DESC_FADE_MS);
    }
    return 0;
  });
  const layer = sheet <= 0.001 ? 0 : push > 0.001 ? 2 : 1;
  return { at, sheet, stage, push, reveal, scroll, active, backgrounds, descriptions, layer };
}

const STOCK_COUNT = 5;

// Slow drift of the blob and spin of the sphere (s).
export const DRIFT_PERIOD = 9.75;
export const SPIN_PERIOD = 3.0; // counter-clockwise, measured on the pin's green blob
export const SPIN_AT = 4.6; // loop time (s) at which the blobs sit as SPHERE_BLOBS lists them (pin 4.2 s)
