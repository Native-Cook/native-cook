/*
 Embroidery Headline — the autoPlay script, a pure function of the loop time t.

   0.0 – 1.2   rest: "Slow made." satin-stitched on linen, the subheading below.
   1.2 – 2.2   the needle passes left to right and unpicks the letters (each in reverse, ~0.12 s a
               letter); the subheading fades out (1.2 – 1.6).
   1.75 – 6.6  close behind it the letters are stitched again, one after another, each taking time
               in proportion to its stitches: parallel satin threads laid across the letter at its
               own angle.
   6.6 – 7.0   the full stop is a French knot: it pops up with a spring.
   6.9 – 7.5   the subheading rises in (ease-out); hold to 9.0 = rest.
 */

export const LOOP = 9;
const UNPICK_FROM = 1.2;
const UNPICK_TO = 2.2;
const STITCH_FROM = 1.75;
const STITCH_TO = 6.6;
export const KNOT_FROM = 6.6;
export const KNOT_TO = 7.0;

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - a) / (b - a)));
}

export function easeInOut(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function easeOut(u: number) {
  'worklet';
  return 1 - Math.pow(1 - u, 3);
}

/** A spring-like pop for the knot: 0.55 → 1 with one soft overshoot. */
export function pop(u: number) {
  'worklet';
  if (u <= 0) return 0.55;
  if (u >= 1) return 1;
  return 1 - 0.45 * Math.exp(-6 * u) * Math.cos(u * 9.5);
}

/**
 * Per glyph, how many of its satin threads are laid at t. `counts` are the glyphs' thread counts
 * (the knot is not among them). Unpicking runs letter by letter left to right, each letter's
 * threads coming out last-laid first; stitching follows, each letter's share of the time in
 * proportion to its threads.
 */
export function glyphPresent(t: number, counts: number[]): number[] {
  'worklet';
  const n = counts.length;
  let total = 0;
  for (let i = 0; i < n; i += 1) total += counts[i];
  const out: number[] = [];
  let acc = 0;
  for (let i = 0; i < n; i += 1) {
    const c = counts[i];
    const u0 = UNPICK_FROM + ((UNPICK_TO - UNPICK_FROM) * i) / n;
    const u1 = UNPICK_FROM + ((UNPICK_TO - UNPICK_FROM) * (i + 1)) / n;
    const s0 = STITCH_FROM + ((STITCH_TO - STITCH_FROM) * acc) / total;
    const s1 = STITCH_FROM + ((STITCH_TO - STITCH_FROM) * (acc + c)) / total;
    acc += c;
    if (t < u0) out.push(c);
    else if (t < s0) out.push(c * (1 - easeInOut(seg(t, u0, u1))));
    else out.push(c * seg(t, s0, s1));
  }
  return out;
}

/** The knot: [scale, alpha]. */
export function knotState(t: number): number[] {
  'worklet';
  if (t >= UNPICK_FROM && t < KNOT_FROM) {
    const out = seg(t, UNPICK_TO - 0.15, UNPICK_TO);
    return [1 - 0.3 * out, 1 - out];
  }
  const u = seg(t, KNOT_FROM, KNOT_TO);
  return [t < UNPICK_FROM ? 1 : pop(u), t < UNPICK_FROM ? 1 : Math.min(1, u * 4)];
}

/** The subheading: [alpha, rise in pt]. */
export function subheading(t: number): number[] {
  'worklet';
  const out = easeInOut(seg(t, 1.2, 1.6));
  const back = easeOut(seg(t, 6.9, 7.5));
  const a = t < 6.9 ? 1 - out : back;
  return [a, t < 6.9 ? 0 : (1 - back) * 10];
}

/** Whether the needle is out (1) or resting off the work (0), with smooth hand-offs. */
export function needleOut(t: number) {
  'worklet';
  return easeInOut(seg(t, 1.0, 1.3)) * (1 - easeInOut(seg(t, 7.0, 7.5)));
}
