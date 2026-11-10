// Eyewear Lens Tint — the loop. Helpers first: a worklet captures only what is declared above it.
//
// A tint is carried as a droplet from the right edge to a lens and let go there: it pours in from the
// point it touched (pour 0 … 1, the new tint spreading out and settling under gravity) and runs over
// the bridge into the other lens. glare −1 … 1 moves the light's reflection across the lenses.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export const LOOP = 10;
/** how long a pour takes, s */
export const POUR = 1.4;

/* ---------------------------------------------------------------- the loop (10 s)
 0.4  a finger takes the rose droplet and carries it to the right lens (0.5 … 1.3), lets go: rose pours (1.3 … 2.7)
 3.6  the sea droplet to the left lens (3.7 … 4.5): sea pours (4.5 … 5.9)
 6.6  midnight to the right lens (6.7 … 7.5): it pours back (7.5 … 8.9)    9.2 the glare sweeps   10 = 0 */
export const T = {
  drops: [
    { at: 0.5, tint: 1, lens: 1 },
    { at: 3.7, tint: 3, lens: 0 },
    { at: 6.7, tint: 0, lens: 1 },
  ],
  carry: 0.8,
  glare: [9.0, 9.9],
} as const;

export type Frame = {
  from: number;
  to: number;
  pour: number;
  lens: number; // the lens the tint was let go on
  carry: number; // a droplet in the finger's hold, 0 … 1 along its way (−1 none)
  carryTint: number;
  glare: number;
  finger: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  let from = 0;
  let to = 0;
  let pour = 0;
  let lens = 1;
  let carry = -1;
  let carryTint = 0;
  let finger = 0;
  for (let i = 0; i < T.drops.length; i++) {
    const d = T.drops[i];
    if (t < d.at - 0.15) break;
    from = i === 0 ? 0 : T.drops[i - 1].tint;
    to = d.tint;
    lens = d.lens;
    const up = d.at + T.carry;
    if (t < up) {
      carry = easeInOut(seg(t, d.at, up));
      carryTint = d.tint;
      pour = 0;
    } else {
      pour = seg(t, up, up + POUR);
    }
    const f = easeOut(seg(t, d.at - 0.15, d.at)) * (1 - easeOut(seg(t, up, up + 0.16)));
    if (f > finger) finger = f;
  }
  if (pour >= 1) {
    from = to;
    pour = 0;
  }
  const g = seg(t, T.glare[0], T.glare[1]);
  const glare = Math.sin(Math.PI * 2 * easeInOut(g)) * 0.8;
  return { from, to, pour, lens, carry, carryTint, glare, finger };
}
