import { HALF, type Mat, type Pose, TABLE, THROW_END, type Throw, axisAngle, clamp, easeInOut, easeOut, faceUp, makeThrow, mix, mul, seg, slidePose, throwPose } from './diceMath';

/*
 Dice Roll Picker — the autoPlay timeline (LOOP = 8 s):
   0.0 – 1.2   rest: the dice show 3 and 4 (sum 7), the line reads "Flick the dice to pick who starts".
   1.2 – 1.6   pick up: both dice lift 34 pt and come toward the player, tipping back 10° (ease-out).
   1.6 – 3.8   the throw (die B 0.05 s behind): first landing at +0.55 s, a bounce off the far rim at
               +0.85 s, hops of 96, 34 and 10 pt, two and three whole turns that ease out by +1.85 s,
               then a rock on the face (+1.85 – 2.2 s). They land on 5 and 6.
   3.75 – 4.05 the line rolls to "11 · Priya starts"; Priya's avatar pops (spring, ~20 % overshoot)
               and wears a "Starts" tag.
   3.8 – 5.6   hold.
   5.6 – 7.2   the dice slide home, tipping over as they turn back to 3 and 4 (ease-in-out, 8 pt hop);
               the line and the avatar settle back (5.6 – 6.0).
   7.2 – 8.0   rest.
 */

export const LOOP = 8;
export const RIM_Y = TABLE.y1 - HALF - 6;

export type Spot = { face: number; yaw: number; x: number; y: number };

export const REST: Spot[] = [
  { face: 3, yaw: 0.34, x: -50, y: -6 },
  { face: 4, yaw: -0.24, x: 50, y: 8 },
];
const PICK = [
  [-36, -76],
  [36, -70],
];
export const FINAL: Spot[] = [
  { face: 5, yaw: -0.46, x: -38, y: 44 },
  { face: 6, yaw: 0.38, x: 42, y: 26 },
];
const TURNS = [2, 3];
const DELAY = [0, 0.05];

export const REST_M: Mat[] = REST.map((s) => faceUp(s.face, s.yaw));
const FINAL_M: Mat[] = FINAL.map((s) => faceUp(s.face, s.yaw));
const THROWS: Throw[] = [0, 1].map((i) => makeThrow(REST_M[i], FINAL_M[i], PICK[i], [FINAL[i].x, FINAL[i].y], 34, TURNS[i], RIM_Y));

export const AUTO_SUM = FINAL[0].face + FINAL[1].face;

export function restPose(i: number): Pose {
  'worklet';
  return { m: REST_M[i], x: REST[i].x, y: REST[i].y, hop: 0 };
}

/** A die's pose at loop time t. */
export function autoPose(i: number, t: number): Pose {
  'worklet';
  if (t < 1.2 || t >= 7.2) return restPose(i);
  if (t < 1.6) {
    const u = seg(t, 1.2, 1.6);
    const e = easeOut(u);
    const tip = axisAngle([1, 0, 0], -0.18 * Math.sin(Math.PI * u));
    return { m: mul(tip, REST_M[i]), x: mix(REST[i].x, PICK[i][0], easeInOut(u)), y: mix(REST[i].y, PICK[i][1], easeInOut(u)), hop: 34 * e };
  }
  const s = t - 1.6 - DELAY[i];
  if (t < 5.6) {
    if (s <= 0) return { m: REST_M[i], x: PICK[i][0], y: PICK[i][1], hop: 34 };
    return throwPose(THROWS[i], Math.min(s, THROW_END));
  }
  return slidePose(FINAL_M[i], REST_M[i], [FINAL[i].x, FINAL[i].y], [REST[i].x, REST[i].y], seg(t, 5.6, 7.2));
}

/** A spring-like pop from 0 to 1, ~20 % overshoot, after time x ≥ 0. */
export function springIn(x: number) {
  'worklet';
  if (x <= 0) return 0;
  return 1 - Math.exp(-6 * x) * Math.cos(12 * x);
}

/** [result line roll 0 … 1, the chosen avatar's pop]. */
export function autoResult(t: number) {
  'worklet';
  const out = easeInOut(seg(t, 5.6, 6.0));
  const k = easeInOut(seg(t, 3.75, 4.05)) * (1 - out);
  return [k, springIn(t - 3.85) * (1 - out)];
}

export { clamp };
