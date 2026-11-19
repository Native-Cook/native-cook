// Breathe Mesh: the breath, the two lights it moves between, and the onboarding film, as pure
// functions of time. No React Native imports, so the module also runs in Node for offline previews.

export const LOOP = 16; // seconds: one box breath, and the onboarding's three steps

// ---------------------------------------------------------------- rhythms

export type Rhythm = { id: string; label: string; detail: string; phases: [number, number, number, number] };

/** in, hold, out, hold (seconds). Box is the film's: 16 s, one loop. */
export const RHYTHMS: Rhythm[] = [
  { id: 'box', label: 'Box', detail: 'Box breathing', phases: [4, 4, 4, 4] },
  { id: '478', label: '4-7-8', detail: '4-7-8 breathing', phases: [4, 7, 8, 0] },
  { id: 'calm', label: 'Even', detail: 'Even breathing', phases: [5, 0, 5, 0] },
];
export const PHASE_LABELS = ['Breathe in', 'Hold', 'Breathe out', 'Hold'] as const;

export const LENGTHS = [1, 3, 5, 10]; // minutes

// ---------------------------------------------------------------- colour

export function hexToLab(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  const lin = (v: number) => {
    const c = v / 255;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  const r = lin((n >> 16) & 255);
  const g = lin((n >> 8) & 255);
  const b = lin(n & 255);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ];
}

function palette(rows: string[]): number[] {
  const out: number[] = [];
  for (const hex of rows) out.push(...hexToLab(hex));
  return out;
}

// Exhaled: cool and quiet (mist blue, lilac, sage). Inhaled: warm and open (peach, apricot, rose).
// 5 x 5, rows from the top of the screen.
const EXHALED = palette([
  '#C9D3EC', '#CFD6EE', '#D8D8EE', '#DED6EC', '#E2D5E9',
  '#C3D4E8', '#B9CBEA', '#C8C6EA', '#D9CBE8', '#E4D3E6',
  '#CFE0E2', '#BFD5E3', '#B6C3E6', '#C9C2E4', '#DCCFE3',
  '#DCE8DE', '#CBE0DA', '#C2D3E2', '#CCCBE3', '#DFD6E5',
  '#E8EFE3', '#DDEAE0', '#D5E2E6', '#DADAEA', '#E6E0EC',
]);
const INHALED = palette([
  '#F6D9CF', '#F8D4C6', '#F7CFC4', '#F2CDCB', '#EDCED4',
  '#F7E0C8', '#F9CDB4', '#F6BFAE', '#EEBFBF', '#E8C6D2',
  '#F9EBCB', '#FAD9B2', '#F8C6A2', '#F0BCB0', '#E9C3CC',
  '#F8EFD6', '#F9E3C0', '#F7D3B0', '#F1CCBC', '#ECCFD4',
  '#F7F1E2', '#F8EAD2', '#F6DDC4', '#F2D6CC', '#EED8DB',
]);

// ---------------------------------------------------------------- worklets (declared before their callers)

export function clamp01(x: number): number {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number): number {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOutSine(x: number): number {
  'worklet';
  return 0.5 - 0.5 * Math.cos(Math.PI * x);
}

export function easeOutCubic(x: number): number {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/**
 * Blend two OKLab grids (count colours) and keep the chroma: a straight Lab blend of a cool and a warm
 * colour passes through grey, so each blended colour's a,b are rescaled to the blended chroma.
 */
export function mixLab(a: number[], b: number[], f: number, count: number, out: number[]): void {
  'worklet';
  for (let i = 0; i < count; i++) {
    const k = i * 3;
    const ca = Math.sqrt(a[k + 1] * a[k + 1] + a[k + 2] * a[k + 2]);
    const cb = Math.sqrt(b[k + 1] * b[k + 1] + b[k + 2] * b[k + 2]);
    const L = a[k] + (b[k] - a[k]) * f;
    const x = a[k + 1] + (b[k + 1] - a[k + 1]) * f;
    const y = a[k + 2] + (b[k + 2] - a[k + 2]) * f;
    const c = Math.sqrt(x * x + y * y);
    const s = c > 0.0001 ? (ca + (cb - ca) * f) / c : 1;
    out[k] = L;
    out[k + 1] = x * s;
    out[k + 2] = y * s;
  }
}

/** Period of a rhythm, seconds. */
export function period(r: number): number {
  'worklet';
  const p = RHYTHMS[r].phases;
  return p[0] + p[1] + p[2] + p[3];
}

/**
 * The breath at time s (seconds, any range) for rhythm r: out[0] = fullness 0..1 (eased in and out of
 * each hold, so the mesh never jerks), out[1] = phase index 0..3, out[2] = seconds left in the phase.
 */
export function breathAt(s: number, r: number, out: number[]): void {
  'worklet';
  const p = RHYTHMS[r].phases;
  const per = p[0] + p[1] + p[2] + p[3];
  let x = ((s % per) + per) % per;
  let k = 0;
  while (k < 3 && (p[k] === 0 || x >= p[k])) {
    x -= p[k];
    k++;
  }
  const f = p[k] > 0 ? x / p[k] : 0;
  out[0] = k === 0 ? easeInOutSine(f) : k === 1 ? 1 : k === 2 ? 1 - easeInOutSine(f) : 0;
  out[1] = k;
  out[2] = p[k] - x;
  out[3] = p[k];
}

/** The light's 75 OKLab numbers at fullness b. */
export function lightAt(b: number, out: number[]): void {
  'worklet';
  mixLab(EXHALED, INHALED, b, 25, out);
}

/**
 * Control-point offsets (50 numbers) at fullness b and ambient time amb (seconds). The inner points
 * swell away from the orb on the inhale and gather back on the exhale; all of them drift on whole-number
 * frequencies of the loop. Corners stay; edge points slide along their edge. (cx, cy) = the orb's
 * centre in screen fractions.
 */
export function offsetsAt(b: number, amb: number, cx: number, cy: number, out: number[]): void {
  'worklet';
  const tau = (amb / LOOP) * Math.PI * 2;
  for (let j = 0; j < 5; j++) {
    for (let i = 0; i < 5; i++) {
      const k = (j * 5 + i) * 2;
      const gx = i / 4;
      const gy = j / 4;
      const s = (i * 5 + j * 11) * 0.41;
      let dx = 0.035 * Math.sin(tau * (1 + ((i + j) % 2)) + s);
      let dy = 0.025 * Math.cos(tau * (1 + (j % 2)) + s * 1.3);
      // swell: away from the orb, most for the points nearest it
      const rx = gx - cx;
      const ry = (gy - cy) * 2.1; // the screen is about twice as tall as wide
      const dist = Math.sqrt(rx * rx + ry * ry) + 0.0001;
      const near = Math.exp(-dist * 2.2);
      dx += (rx / dist) * 0.09 * b * near;
      dy += ((ry / dist) * 0.09 * b * near) / 2.1;
      if (i === 0 || i === 4) dx = 0;
      if (j === 0 || j === 4) dy = 0;
      out[k] = dx;
      out[k + 1] = dy;
    }
  }
}

// ---------------------------------------------------------------- the film

/** Onboarding steps on the loop: [fade-in start, visible, fade-out start, gone]. */
export const PHASE = {
  toStep2: [5.0, 5.7], // Welcome -> Your rhythm
  pickLength: 7.4, // the length wheel turns from 3 to 5 min
  toStep3: [9.6, 10.3], // -> Your first session
  resetLength: 12.4, // the hidden wheel goes back to 3 min
  toStep1: [15.2, 16.0], // -> Welcome again
} as const;

/**
 * Visibility of step k (0..2) at loop time t. A change is sequential, never a crossfade: the old step
 * fades out over the first 45 % of the transition (ease-in) and the new one fades in over the last
 * 55 % (ease-out cubic), so two texts are never on screen together.
 */
export function stepVis(k: number, t: number): number {
  'worklet';
  const T = [PHASE.toStep2, PHASE.toStep3, PHASE.toStep1];
  // transition j takes step j out (step 0 for j = 2 is "step 2 out, step 0 in")
  const from = [0, 1, 2];
  const to = [1, 2, 0];
  let v = k === 0 ? 1 : 0;
  for (let j = 0; j < 3; j++) {
    const a = T[j][0];
    const b = T[j][1];
    const mid = a + (b - a) * 0.45;
    const out = seg(t, a, mid);
    const inn = easeOutCubic(seg(t, mid, b));
    if (from[j] === k && t >= a) v = 1 - out * out;
    if (to[j] === k && t >= mid) v = inn;
  }
  return v;
}

/** Which step the film is on (the one whose content is interactive). */
export function filmStep(t: number): number {
  'worklet';
  if (t < PHASE.toStep2[0] + 0.35) return 0;
  if (t < PHASE.toStep3[0] + 0.35) return 1;
  if (t < PHASE.toStep1[0] + 0.4) return 2;
  return 0;
}

export function filmLength(t: number): number {
  'worklet';
  return t >= PHASE.pickLength && t < PHASE.resetLength ? 5 : 3;
}
