/**
 * The geometry of a spirograph set, in model units (pt at a scale of 1) around the ring's centre,
 * plus the pens and the autoPlay script.
 *
 * A gear of `teeth` teeth rolls inside a ring of RING_TEETH teeth. Its centre sits at angle `phi`
 * on a circle of radius R − r; rolling without slipping turns the gear by −(R − r)/r for every
 * radian of `phi`, which is kept as its own running angle `spin` so it stays continuous when the
 * gear is changed. A pen in a hole at distance d and angle ψ on the gear traces a hypotrochoid.
 */

// The ring's pitch radius (where the teeth mesh) and its tooth count; the ring's body is RING_BODY wide.
export const RING_R = 150;
export const RING_TEETH = 96;
export const RING_BODY = 26;
// Teeth stand TOOTH_DEPTH proud of the pitch circle on both ring and gears.
export const TOOTH_DEPTH = 3.2;
// Everything the pen can reach, and the ring, fits in a square of this half-size.
export const BOUNDS = RING_R + RING_BODY + 8;

/** The gears in the set, smallest first: tinted, translucent plastic. */
export const GEARS = [
  { teeth: 30, tint: '#E3A13B' },
  { teeth: 40, tint: '#3F9C9E' },
  { teeth: 56, tint: '#D4604F' },
  { teeth: 72, tint: '#6A80C8' },
];

/** The pens: real ink colours, laid on the paper with a multiply blend. */
export const INKS = [
  { color: '#2541A8', name: 'Ultramarine' },
  { color: '#D3402A', name: 'Vermilion' },
  { color: '#11806C', name: 'Viridian' },
  { color: '#2A2830', name: 'Graphite' },
];

// Pen holes: distance from the gear's centre as a share of its radius, laid along a spiral.
export const HOLES = [0.24, 0.4, 0.56, 0.71, 0.85];
const HOLE_TURN = 1.25;

/** Pitch radius of a gear. */
export function gearRadius(teeth: number) {
  'worklet';
  return (RING_R * teeth) / RING_TEETH;
}

function gcd(a: number, b: number) {
  'worklet';
  let x = a;
  let y = b;
  while (y !== 0) {
    const t = y;
    y = x % y;
    x = t;
  }
  return x;
}

/** Turns of the gear round the ring before the pattern closes, and the loops it makes. */
export function pattern(teeth: number) {
  'worklet';
  const g = gcd(RING_TEETH, teeth);
  return { turns: teeth / g, petals: RING_TEETH / g };
}

/** Distance and angle (on the gear) of a pen hole; `hole` may be fractional while it morphs. */
export function holeAt(hole: number, radius: number) {
  'worklet';
  const i = Math.max(0, Math.min(HOLES.length - 1, hole));
  const lo = Math.floor(i);
  const hi = Math.min(HOLES.length - 1, lo + 1);
  const f = i - lo;
  return { d: radius * (HOLES[lo] + (HOLES[hi] - HOLES[lo]) * f), psi: HOLE_TURN * i };
}

/** The pen's position (from the ring's centre) for a gear of `radius` at `phi`, turned by `spin`. */
export function penAt(phi: number, spin: number, radius: number, d: number, psi: number, out: { x: number; y: number }) {
  'worklet';
  const reach = RING_R - radius;
  out.x = reach * Math.cos(phi) + d * Math.cos(spin + psi);
  out.y = reach * Math.sin(phi) + d * Math.sin(spin + psi);
}

/** How far the gear turns on itself per radian it rolls round the ring. */
export function spinRate(radius: number) {
  'worklet';
  return -(RING_R - radius) / radius;
}

/**
 * The outline of a toothed wheel of `teeth` teeth on a pitch circle of `radius`, teeth pointing
 * outwards (`outward` = 1, a gear) or inwards (−1, the ring's inner edge), as flat x, y pairs.
 */
export function toothOutline(teeth: number, radius: number, outward: number, depth = TOOTH_DEPTH) {
  'worklet';
  const pts: number[] = [];
  const step = (2 * Math.PI) / teeth;
  // Root, flank up, flat tip, flank down: a trapezoid tooth on each pitch step.
  const profile = [
    [0, -1],
    [0.18, -1],
    [0.32, 1],
    [0.68, 1],
    [0.82, -1],
  ];
  for (let k = 0; k < teeth; k += 1) {
    const a = k * step;
    for (const [t, h] of profile) {
      const r = radius + outward * h * depth * 0.5;
      pts.push(r * Math.cos(a + t * step), r * Math.sin(a + t * step));
    }
  }
  return pts;
}

/**
 * autoPlay (seconds). It starts and ends at rest on a clean sheet with the 40-tooth gear, hole 3 and
 * ultramarine ink: rolls the gear five times round (a closed twelve-loop rosette), switches to
 * vermilion ink and the 72-tooth gear, moves the pen out to hole 5, rolls three times round (four
 * wide loops over the first pattern), tears the sheet off and sets the first gear, hole and ink
 * back. Every roll is a whole number of the pattern's turns, so the gear ends where it began.
 */
export const LOOP = 16;
export const SCRIPT = {
  startGear: 1,
  startHole: 2,
  startInk: 0,
  steps: [
    { at: 0.9, roll: 5, time: 5.6 },
    { at: 7.0, ink: 1 },
    { at: 7.4, gear: 3 },
    { at: 7.9, hole: 4 },
    { at: 8.4, roll: 3, time: 3.6 },
    { at: 12.7, clear: true },
    { at: 14.2, gear: 1, hole: 2, ink: 0 },
  ] as { at: number; gear?: number; hole?: number; ink?: number; roll?: number; time?: number; clear?: boolean }[],
};
