/*
 Elevator Floor Nav — timing. A trip (press a floor, doors close, ride, ding, doors open) is a pure
 function of the time since its button was pressed; the self-playing loop is two trips, and a
 finger simply starts a trip of its own.

 A trip pressed at p0 from floor a to floor b (durations × `pace`):
   p0          the button lights (and dips 0.12 s)
   +0.3 – 0.9  the doors close (ease in-out, a 2 % bump as they meet)
   +0.9 – R    the car rides 0.4 s a floor (smootherstep); the store behind slides by, blurred
               with speed; the dial's needle follows the car with a little follow-through
   arrive      the bell: the lantern of the direction glows and fades over 1.4 s, the button goes out
   +0.6        the doors open on the new section; the header rolls to its name

 LOOP = 9 s: rest on floor 1 (0 – 1.3), floor 4 pressed at 1.3 (doors 1.6 – 2.2, ride 2.2 – 3.4,
 open 3.4 – 4.0), hold, floor 1 pressed at 5.4 (doors 5.7 – 6.3, ride 6.3 – 7.5, open 7.5 – 8.1),
 rest 8.1 – 9.
*/

export const LOOP = 9;
export const FLOORS = 5;
export const PER_FLOOR = 0.4;
export const IDLE_BEFORE_BACK = 2;
/** Easing back after a touch is a quick trip home at this pace (0.9 s in all). */
export const BACK_PACE = 0.9 / (0.3 + 0.6 + 0.6 + PER_FLOOR * 3);

export type Trip = { from: number; to: number; p0: number; pace: number };

export type Pose = {
  /** The car's floor, continuous (1 … 5). */
  car: number;
  /** Ride speed in floors per second (for blur). */
  speed: number;
  /** Doors: 0 open … 1 closed. */
  doors: number;
  /** The needle's floor (follows the car with a little overshoot). */
  needle: number;
  /** Lit amount per button, and the dip of a pressed button. */
  lit: number[];
  dip: number[];
  /** Bell: glow 0 … 1 of the up lantern and of the down lantern. */
  ding: number[];
  /** Header label: from floor, to floor, roll 0 … 1. */
  labelFrom: number;
  labelTo: number;
  labelK: number;
};

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  const v = clamp01(x);
  return v < 0.5 ? 4 * v * v * v : 1 - Math.pow(-2 * v + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u;
}

function smoother(x: number) {
  'worklet';
  const v = clamp01(x);
  return v * v * v * (v * (v * 6 - 15) + 10);
}

function smootherSlope(x: number) {
  'worklet';
  if (x <= 0 || x >= 1) return 0;
  return 30 * x * x * (x - 1) * (x - 1);
}

/** Times inside a trip, in seconds after the press. */
export function tripTimes(trip: Trip) {
  'worklet';
  const k = trip.pace;
  const ride = Math.max(1, Math.abs(trip.to - trip.from)) * PER_FLOOR * k;
  const close0 = 0.3 * k;
  const close1 = 0.9 * k;
  // Calling the floor the car is on only lights the button for a moment.
  const arrive = trip.to === trip.from ? 0.35 * k : close1 + ride;
  return { close0, close1, ride, arrive, open1: arrive + 0.6 * k };
}

/** The pose during (and after) a trip, at time t on the same clock as trip.p0. */
export function tripPose(trip: Trip, t: number): Pose {
  'worklet';
  const e = t - trip.p0;
  const { close0, close1, ride, arrive, open1 } = tripTimes(trip);
  const span = trip.to - trip.from;
  let car = trip.from;
  let speed = 0;
  if (e >= close1 && span !== 0) {
    const u = (e - close1) / ride;
    car = trip.from + span * smoother(u);
    speed = (Math.abs(span) * smootherSlope(u)) / ride;
  }
  let doors = 0;
  if (span === 0) {
    doors = 0;
  } else if (e >= close0 && e < arrive) {
    const c = seg(e, close0, close1);
    // A 2 % bump as the two doors meet.
    doors = easeInOut(c) + 0.02 * Math.sin(Math.PI * seg(e, close1 - 0.06 * trip.pace, close1 + 0.12 * trip.pace));
  } else if (e >= arrive) {
    doors = 1 - easeInOut(seg(e, arrive, open1));
  }
  // The needle trails the car by 60 ms and overshoots a touch on arrival.
  let needle = trip.from;
  if (e >= close1 && span !== 0) needle = trip.from + span * smoother((e - close1 - 0.06) / ride);
  if (e > arrive) needle += Math.sign(span) * 0.12 * Math.exp(-(e - arrive) * 5) * Math.sin((e - arrive) * 14);
  const lit = [0, 0, 0, 0, 0];
  const dip = [0, 0, 0, 0, 0];
  const b = trip.to - 1;
  if (e >= 0) {
    lit[b] = clamp01(e / 0.08) * (1 - seg(e, arrive, arrive + 0.3));
    dip[b] = e < 0.36 ? Math.sin(Math.PI * clamp01(e / 0.36)) * Math.exp(-e * 3) : 0;
  }
  const glow = e >= arrive && span !== 0 ? Math.exp(-(e - arrive) * 2.2) * clamp01((e - arrive) / 0.05) : 0;
  const ding = span >= 0 ? [glow, 0] : [0, glow];
  return {
    car,
    speed,
    doors: clamp01(doors * 1.0),
    needle,
    lit,
    dip,
    ding,
    labelFrom: trip.from,
    labelTo: e >= arrive ? trip.to : trip.from,
    labelK: e >= arrive ? easeOut((e - arrive) / 0.35) : 1,
  };
}

/** True once a trip has fully finished (doors open, bell faded). */
export function tripDone(trip: Trip, t: number) {
  'worklet';
  return t - trip.p0 > tripTimes(trip).open1 + 0.2;
}

/** A pose with the bell of an earlier trip still fading in it. */
export function mergeBell(p: Pose, earlier: Pose): Pose {
  'worklet';
  return { ...p, ding: [Math.max(p.ding[0], earlier.ding[0]), Math.max(p.ding[1], earlier.ding[1])] };
}

const TRIP_UP: Trip = { from: 1, to: 4, p0: 1.3, pace: 1 };
const TRIP_DOWN: Trip = { from: 4, to: 1, p0: 5.4, pace: 1 };

/** The pose of the self-playing loop at loop time t. */
export function loopPose(t: number): Pose {
  'worklet';
  if (t < TRIP_UP.p0) {
    const p = tripPose(TRIP_DOWN, LOOP + t);
    return { ...p, lit: [0, 0, 0, 0, 0], dip: [0, 0, 0, 0, 0] };
  }
  if (t < TRIP_DOWN.p0) return tripPose(TRIP_UP, t);
  // The first trip's bell is still fading when the second starts.
  return mergeBell(tripPose(TRIP_DOWN, t), tripPose(TRIP_UP, t));
}

/** Resting pose at a floor (doors open, nothing lit). */
export function restAt(floor: number): Pose {
  'worklet';
  return { car: floor, speed: 0, doors: 0, needle: floor, lit: [0, 0, 0, 0, 0], dip: [0, 0, 0, 0, 0], ding: [0, 0], labelFrom: floor, labelTo: floor, labelK: 1 };
}
