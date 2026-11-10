// Pearl Shell Premium — the loop. Helpers first: a worklet captures only what is declared above it.

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
/** one choice of plan: the pearl turns a third of a turn and the sparkles flare (s) */
export const PICK = 0.9;
/** the gift: the pearl lifts and glows, the sparkles are thrown out and come back (s) */
export const GIFT = 2.0;

export function pickAt(tau: number) {
  'worklet';
  const k = seg(tau, 0, PICK);
  return { spin: ((Math.PI * 2) / 3) * easeInOut(k), flare: Math.sin(Math.PI * k) * 0.35 };
}
export function giftAt(tau: number) {
  'worklet';
  const up = easeOut(seg(tau, 0, 0.45));
  const down = easeInOut(seg(tau, 1.0, GIFT));
  return { lift: up * (1 - down), burst: up * (1 - down), glow: up * (1 - down) };
}

export const T = { picks: [[1.4, 1], [3.2, 2], [5.0, 0]] as [number, number][], press: 7.0 } as const;

export type Frame = { plan: number; spin: number; lift: number; burst: number; glow: number; pressed: number; finger: number; fingerAt: number; fingerPress: number };

export function frameAt(t: number): Frame {
  'worklet';
  let plan = 0;
  let spin = 0;
  let burst = 0;
  for (let i = 0; i < 3; i++) {
    const [at, p] = T.picks[i];
    if (t >= at) {
      plan = p;
      const s = pickAt(t - at);
      spin += s.spin;
      burst = Math.max(burst, s.flare);
    }
  }
  const g = giftAt(t - T.press);
  const lift = t >= T.press ? g.lift : 0;
  burst = Math.max(burst, t >= T.press ? g.burst : 0);
  const glow = t >= T.press ? g.glow : 0;
  // three thirds of a turn: the pearl ends the loop where it began
  spin = spin % (Math.PI * 2);
  const pressed = t >= T.press - 0.04 && t < T.press + 0.16 ? 1 : 0;
  let finger = 0;
  let fingerAt = 0; // 0..2 a plan row, 3 the button
  let fingerPress = 0;
  const taps: [number, number][] = [[T.picks[0][0], 1], [T.picks[1][0], 2], [T.picks[2][0], 0], [T.press, 3]];
  for (let k = 0; k < 4; k++) {
    const [at, where] = taps[k];
    const f = easeOut(seg(t, at - 0.2, at - 0.05)) * (1 - easeOut(seg(t, at + 0.15, at + 0.32)));
    if (f > finger) {
      finger = f;
      fingerAt = where;
      fingerPress = easeOut(seg(t, at - 0.05, at + 0.05)) * (1 - easeOut(seg(t, at + 0.1, at + 0.22)));
    }
  }
  return { plan, spin, lift, burst, glow, pressed, finger, fingerAt, fingerPress };
}
