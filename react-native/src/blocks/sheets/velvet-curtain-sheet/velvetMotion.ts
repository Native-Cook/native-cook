/*
 Velvet Curtain Sheet — layout of the seat map and the autoPlay script.

 LOOP = 9 s:
   0.0 – 1.4   rest: the sheet at its half detent, the curtain closed over the seat map;
               1.1 – 1.4 the "Choose seats" button is pressed (scale 0.96 and back).
   1.4 – 2.6   the curtain's rail opens (ease-in-out); the hem lags behind on a spring
               (ω 9 rad/s, damping ratio 0.35), so the cloth swings out, overshoots and settles by 3.2.
   3.2 – 5.6   seats C7 (3.6) and C8 (4.3) light up with a pop; the booking bar rises at 3.0.
   5.6 – 6.8   the curtain closes (ease-in-out), the hem swings in after it; the bar drops at 5.6.
   7.4 – 7.6   the seats switch off behind the closed curtain.
   7.6 – 9.0   rest. The hem's swing is faded out by 8.6 – 9.0 so t = 9 equals t = 0.
*/

export const LOOP = 9;

export const ROWS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
export const PER_ROW = 12;
export const SEAT_W = 20;
export const SEAT_H = 18;
export const SEAT_PITCH = 24;
export const AISLE = 18;
/** Sheet coordinates: where the curtain starts (under the pelmet), the screen arc, the first row. */
export const PELMET = 40;
export const SCREEN_Y = 62;
export const SEATS_Y = 98;
export const PRICE = 14;

/** Seats the loop selects: C7 and C8 (indices into the row-major seat list). */
export const LOOP_SEATS = [2 * PER_ROW + 6, 2 * PER_ROW + 7];
export const LOOP_SEAT_AT = [3.6, 4.3];

/** Sway of the hem: a spring trailing the rail. */
export const SWAY_OMEGA = 9;
export const SWAY_ZETA = 0.35;

export const IDLE_BEFORE_RETURN = 2;
export const RETURN_FOR = 0.8;

export function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeInOut(x: number) {
  'worklet';
  const c = clamp(x, 0, 1);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp(x, 0, 1), 3);
}

/** Row pitch that keeps the map, the legend and the bar apart on short screens. */
export function rowPitch(visible: number) {
  'worklet';
  return visible < 420 ? 22 : 24;
}

export function seatsWidth() {
  'worklet';
  return PER_ROW * SEAT_PITCH - (SEAT_PITCH - SEAT_W) + AISLE;
}

/** Top-left of seat i in sheet coordinates. */
export function seatAt(i: number, width: number, pitch: number): [number, number] {
  'worklet';
  const row = Math.floor(i / PER_ROW);
  const col = i % PER_ROW;
  const x0 = (width - seatsWidth()) / 2;
  const x = x0 + col * SEAT_PITCH + (col >= PER_ROW / 2 ? AISLE : 0);
  return [x, SEATS_Y + row * pitch];
}

export function seatName(i: number) {
  'worklet';
  return `${ROWS[Math.floor(i / PER_ROW)]}${(i % PER_ROW) + 1}`;
}

/** Taken seats: seeded, never the loop's pair or their neighbours. */
export function isTaken(i: number) {
  'worklet';
  const row = Math.floor(i / PER_ROW);
  if (row === 2 && Math.abs((i % PER_ROW) - 6.5) < 2) return false;
  const h = Math.abs(Math.sin(i * 12.9898 + 4.1) * 43758.5453) % 1;
  return h < (row < 2 ? 0.18 : row > 5 ? 0.42 : 0.3);
}

/** The rail's opening in the loop (0 closed, 1 open). */
export function railAt(t: number) {
  'worklet';
  return easeInOut(seg(t, 1.4, 2.6)) * (1 - easeInOut(seg(t, 5.6, 6.8)));
}

/** The hem's opening: the rail's, through the sway spring, integrated from t = 0 (pure in t). */
export function hemAt(t: number) {
  'worklet';
  const STEP = 1 / 60;
  let x = 0;
  let v = 0;
  const n = Math.floor(t / STEP);
  for (let i = 1; i <= n; i += 1) {
    const goal = railAt(i * STEP);
    const a = -SWAY_OMEGA * SWAY_OMEGA * (x - goal) - 2 * SWAY_ZETA * SWAY_OMEGA * v;
    v += a * STEP;
    x += v * STEP;
  }
  const rail = railAt(t);
  const fade = 1 - easeInOut(seg(t, 8.6, 9.0));
  return rail + (x - rail) * fade;
}

/** A seat's light in the loop: a pop (to 1.12, back to 1) and its glow, 0 when off. */
export function loopSeat(t: number, k: number) {
  'worklet';
  const on = seg(t, LOOP_SEAT_AT[k], LOOP_SEAT_AT[k] + 0.35);
  const off = seg(t, 7.4, 7.6);
  return on * (1 - off);
}

/** Pop scale for a seat lit `e` seconds ago (a small overshoot, settled in 0.35 s). */
export function popScale(e: number) {
  'worklet';
  if (e < 0) return 1;
  const x = clamp(e / 0.35, 0, 1);
  return 1 + 0.12 * Math.sin(Math.PI * x) * (1 - x * 0.4);
}

export function pressAt(t: number) {
  'worklet';
  return 1 - 0.04 * Math.sin(Math.PI * seg(t, 1.1, 1.4));
}

/** The booking bar's rise (0 hidden, 1 shown). */
export function barAt(t: number) {
  'worklet';
  return easeOut(seg(t, 3.0, 3.4)) * (1 - easeInOut(seg(t, 5.6, 5.9)));
}

/** The seat under a point in sheet coordinates, or -1. */
export function seatIndexAt(x: number, y: number, width: number, pitch: number) {
  'worklet';
  const row = Math.floor((y - SEATS_Y + (pitch - SEAT_H) / 2) / pitch);
  if (row < 0 || row >= ROWS.length) return -1;
  for (let col = 0; col < PER_ROW; col += 1) {
    const [sx] = seatAt(row * PER_ROW + col, width, pitch);
    if (x >= sx - 2 && x <= sx + SEAT_W + 2) return row * PER_ROW + col;
  }
  return -1;
}
