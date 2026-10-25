/*
 Parachute Drop Alert — the motion. A frame is the alert card (centre, angle, squash) plus one or
 two canopies (position, size, tilt, how collapsed) and their suspension lines. The loop computes
 them from the timeline; under a finger the component simulates the card and the canopy follows.

 autoPlay (LOOP = 9 s):
   0.0 – 1.2   rest: the app, no alert.
   1.2 – 4.0   descent from above the screen (decelerating, 1 - (1 - u)^2.2) with a pendulum sway
               (0.17 rad at 0.85 Hz, dying out before touchdown) and a drift; the canopy breathes
               (±3 % at 2.1 Hz) and leans against the swing; the scrim fades in from 3.0.
   4.0         touchdown: the card squashes (6 %, 4 Hz, decaying in 0.1 s).
   4.0 – 4.7   the canopy deflates and drapes down over the card's top, lines going slack.
   4.6 – 5.1   the wind pulls it away to the right, lines trailing.
   5.0 – 6.6   hold: "Track" / "Later".
   6.6 – 7.0   a fresh canopy blooms out of the card's top (ease-out back).
   7.0 – 8.0   the card is lifted up and out (accelerating), the scrim fades.
   8.0 – 9.0   rest. 9.0 is the same frame as 0.0.
*/

export const LOOP = 9;
export const LAND_AT = 4.0;
export const LIFT_AT = 6.6;
export const GORES = 7;

export type Geo = {
  w: number;
  h: number;
  /** The card's size and landed centre. */
  cardW: number;
  cardH: number;
  landX: number;
  landY: number;
  /** Canopy size and line length (skirt to card top). */
  canW: number;
  canH: number;
  lines: number;
};

/** A canopy: skirt centre, width, height, tilt (rad), collapse 0..1, attached lines, slack 0..1, alpha. */
export type Canopy = { x: number; y: number; w: number; h: number; tilt: number; collapse: number; attached: number; slack: number; alpha: number };

export type Pose = {
  /** Card centre, angle (rad), squash x / y; visible when shown is 1. */
  cardX: number;
  cardY: number;
  cardRot: number;
  sx: number;
  sy: number;
  shown: number;
  scrim: number;
  /** Reduced motion fades the whole alert instead of moving it. */
  fade: number;
  canopies: Canopy[];
};

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOutCubic(x: number) {
  'worklet';
  const k = clamp01(x);
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

export function easeOutBack(x: number, c = 1.7) {
  'worklet';
  const k = clamp01(x) - 1;
  return 1 + (c + 1) * k * k * k + c * k * k;
}

export function easeOutCubic(x: number) {
  'worklet';
  const k = 1 - clamp01(x);
  return 1 - k * k * k;
}

/** Card touchdown squash, e seconds after landing: [sx, sy]. */
export function landSquash(e: number, out: number[]) {
  'worklet';
  if (e < 0 || e > 0.6) {
    out[0] = 1;
    out[1] = 1;
    return;
  }
  const k = Math.exp(-e / 0.1) * Math.cos(2 * Math.PI * 4 * e);
  out[0] = 1 + 0.035 * k;
  out[1] = 1 - 0.06 * k;
}

/** The canopy a card of centre (x, y) and angle rot hangs from, fully open. */
export function hangingCanopy(g: Geo, x: number, y: number, rot: number, breath: number, scale: number): Canopy {
  'worklet';
  const top = g.cardH / 2 + g.lines * scale;
  const sx = x + Math.sin(rot) * top;
  const sy = y - Math.cos(rot) * top;
  return {
    x: sx,
    y: sy,
    w: g.canW * scale * (1 + 0.03 * breath),
    h: g.canH * scale * (1 - 0.04 * breath),
    tilt: rot * 0.45,
    collapse: 0,
    attached: 1,
    slack: 0,
    alpha: 1,
  };
}

/** The collapse after touchdown, e seconds after it, for a canopy that was at c0. */
export function collapsing(g: Geo, c0: Canopy, cardX: number, cardY: number, e: number): Canopy {
  'worklet';
  const k = easeInOutCubic(seg(e, 0, 0.7));
  const away = easeInOutCubic(seg(e, 0.6, 1.1));
  // The hem settles a little below the card's top edge, so the cloth lies over it.
  const drapeY = cardY - g.cardH / 2 + 24;
  return {
    x: c0.x + (cardX + g.cardW * 0.08 - c0.x) * k + g.w * 1.05 * away,
    y: c0.y + (drapeY - c0.y) * k - 40 * away,
    w: c0.w * (1 + 0.12 * k),
    h: c0.h * (1 - 0.82 * k),
    tilt: c0.tilt * (1 - k) + 0.32 * away,
    collapse: k,
    attached: away < 0.02 ? 1 : 0,
    slack: k,
    alpha: 1,
  };
}

export function loopPose(t: number, g: Geo): Pose {
  'worklet';
  const pose: Pose = { cardX: g.landX, cardY: g.landY, cardRot: 0, sx: 1, sy: 1, shown: 0, scrim: 0, fade: 1, canopies: [] };
  const sq = [1, 1];
  const breath = Math.sin(2 * Math.PI * 2.1 * t);
  pose.scrim = 0.36 * easeInOutCubic(seg(t, 3.0, 4.0)) * (1 - easeInOutCubic(seg(t, 7.0, 7.9)));
  if (t < 1.2 || t >= 8.0) return pose;
  pose.shown = 1;
  const startY = -(g.cardH / 2 + g.lines + g.canH + 30);
  if (t < LAND_AT) {
    const u = (t - 1.2) / (LAND_AT - 1.2);
    const fall = 1 - Math.pow(1 - u, 2.2);
    const env = Math.pow(1 - u, 0.9) * seg(u, 0, 0.12);
    const rot = 0.17 * Math.sin(2 * Math.PI * 0.85 * (t - 1.2)) * env;
    pose.cardX = g.landX + 30 * Math.sin(2 * Math.PI * 0.42 * (t - 1.2)) * Math.pow(1 - u, 1.4);
    pose.cardY = startY + (g.landY - startY) * fall;
    pose.cardRot = rot * 0.6;
    pose.canopies.push(hangingCanopy(g, pose.cardX, pose.cardY, rot, breath, 1));
    return pose;
  }
  const e = t - LAND_AT;
  landSquash(e, sq);
  pose.sx = sq[0];
  pose.sy = sq[1];
  if (e < 1.2) pose.canopies.push(collapsing(g, hangingCanopy(g, g.landX, g.landY, 0, breath, 1), g.landX, g.landY, e));
  if (t >= LIFT_AT) {
    const bloom = easeOutBack(seg(t, LIFT_AT, LIFT_AT + 0.4), 1.4);
    const up = Math.pow(seg(t, LIFT_AT + 0.4, 8.0), 2.2);
    const total = g.landY + g.cardH / 2 + g.lines + g.canH + 40;
    pose.cardY = g.landY - total * up;
    const rot = 0.05 * Math.sin(2 * Math.PI * 1.1 * (t - LIFT_AT)) * seg(t, LIFT_AT + 0.2, 7.2);
    pose.cardRot = rot * 0.6;
    const c = hangingCanopy(g, pose.cardX, pose.cardY, rot, breath * bloom, 0.25 + 0.75 * bloom);
    c.alpha = seg(t, LIFT_AT, LIFT_AT + 0.08);
    pose.canopies.push(c);
  }
  return pose;
}
