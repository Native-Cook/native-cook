// One loop drives the whole film. Every film value below is a pure function of the loop time t (seconds,
// 0 <= t < LOOP), so t = LOOP gives the same frame as t = 0 and the Swift / Kotlin ports can match it frame for frame.
//
//  0.0 –  0.9  poster: the whole city, the blue route from the hub to Home, "Arriving in 12 min"
//  0.9 – 12.6  the drive. The camera frames the van and the door together, so it zooms in by itself as the van gets
//              closer: whole city → district → street; the map turns 37 deg until north is up and tilts into 3D as it
//              nears the street. The ETA and the distance count down.
//  5.25 – 7.0  a finger drags the map aside to look round (the camera stops following; the locate button empties)
//  7.5  – 8.3  a tap on the locate button: the camera glides back onto the van
//  ~8.8 – 12.6 the last 100 m: right in to the door, the van parks; 12.45 "Arrived", the door pulses
// 13.1  – 14.45 Call is tapped: a "Calling Marco…" pill shows, then the call ends
// 14.45 – 17.0 Message is tapped: the quick reply "Coming down now" is sent (the toast from 14.6)
// 15.45 – 18.35 the camera pulls back out (an even zoom) to the whole city; the van leaves the door and the next
//              round starts at the hub; the panel goes back to "12 min". 18.35 – 19.0 poster. Last frame = first frame.

export const LOOP = 19;

export const PHASE = {
  driveA: 0.9,
  driveB: 12.6,
  grabIn: 5.25,
  grab: 5.45,
  dragA: 5.5,
  dragB: 6.7,
  release: 6.8,
  recenter: 7.5,
  glideB: 8.3,
  arriveA: 12.45,
  arriveB: 12.85,
  call: 13.1,
  callOpen: 13.2,
  callClose: 14.2,
  msg: 14.45,
  msgSend: 14.6,
  backA: 15.45,
  backB: 18.35,
  vanOutA: 15.7,
  vanOutB: 16.2,
  vanInA: 16.9,
  vanInB: 17.6,
} as const;

/** Minutes shown at the hub; the ETA is proportional to the distance left. */
export const ETA_MIN = 12;
/** Closest zoom (pt per metre) and the softening distance (m) of the log-distance drive. */
export const S_MAX = 8.5;
export const S_MIN = 0.07;
const S_FLOOR = 0.12;
const SOFT = 40;
/** The drive's speed ramps (s): smooth start and a longer, gentle stop at the door. */
const RAMP_IN = 1.6;
const RAMP_OUT = 2.2;
/** Bearing at the poster: the hub-to-Home line points up (radians). */
export const BEARING0 = (36.8 * Math.PI) / 180;
/** The film's drag, in points (the map follows the finger). */
export const DRAG_X = -120;
export const DRAG_Y = 36;

/** After the last touch, wait this long, then ease back to the poster in RETURN_MS and loop from 0. */
export const IDLE_MS = 2000;
export const RETURN_MS = 1500;

/** Layout handed to the worklets: [width, height, focus x, focus y, framing width, framing height, panel top]. */
export const L_W = 0;
export const L_H = 1;
export const L_FX = 2;
export const L_FY = 3;
export const L_AW = 4;
export const L_AH = 5;
export const L_PANEL = 6;

// ---------------------------------------------------------------- helpers (declared before their callers)

export function clamp01(x: number): number {
  "worklet";
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number): number {
  "worklet";
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, k: number): number {
  "worklet";
  return a + (b - a) * k;
}

export function easeInOutCubic(x: number): number {
  "worklet";
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeInOutSine(x: number): number {
  "worklet";
  return 0.5 - 0.5 * Math.cos(Math.PI * x);
}

export function easeOutCubic(x: number): number {
  "worklet";
  return 1 - Math.pow(1 - x, 3);
}

export function easeInCubic(x: number): number {
  "worklet";
  return x * x * x;
}

export function smooth01(x: number): number {
  "worklet";
  const k = clamp01(x);
  return k * k * (3 - 2 * k);
}

export function bump(t: number, a: number, b: number): number {
  "worklet";
  const k = Math.sin(Math.PI * seg(t, a, b));
  return k * k;
}

/** Distance left (m) for a log-distance parameter u (0 at the hub, 1 at the door): equal steps of u are equal zoom
 * steps, which is what makes the camera's push-in feel steady. */
export function remainingAt(u: number, len: number): number {
  "worklet";
  return (len + SOFT) * Math.pow(SOFT / (len + SOFT), u) - SOFT;
}

/** The inverse of remainingAt. */
export function logParam(dLeft: number, len: number): number {
  "worklet";
  return Math.log((len + SOFT) / (Math.max(dLeft, 0) + SOFT)) / Math.log((len + SOFT) / SOFT);
}

/** A point on the route d metres from the hub: [x, y, heading x, heading y]. */
export function routeAt(d: number, rx: number[], ry: number[], step: number, out: number[]): void {
  "worklet";
  const n = rx.length - 1;
  const f = Math.min(Math.max(d / step, 0), n);
  const i = Math.min(Math.floor(f), n - 1);
  const k = f - i;
  out[0] = rx[i] + (rx[i + 1] - rx[i]) * k;
  out[1] = ry[i] + (ry[i + 1] - ry[i]) * k;
  // heading from samples about 9 m either side: smooth through the rounded corners
  const a = Math.max(i - 3, 0);
  const b = Math.min(i + 4, n);
  const hx = rx[b] - rx[a];
  const hy = ry[b] - ry[a];
  const hl = Math.hypot(hx, hy) || 1;
  out[2] = hx / hl;
  out[3] = hy / hl;
}

/** Map bearing (radians) for the log-distance parameter: the hub-to-Home line up at first, north up on the street. */
export function bearingOf(u: number): number {
  "worklet";
  return BEARING0 * (1 - smooth01((u - 0.5) / 0.36));
}

/** How far the view leans into 3D for a zoom (0 flat .. 0.8). */
export function tiltOf(s: number): number {
  "worklet";
  const k = (Math.log2(s) - Math.log2(0.55)) / (Math.log2(3.6) - Math.log2(0.55));
  return 0.8 * smooth01(k);
}

/** The camera that frames the van (d metres along the route) and the door together: [cx, cy, log2 zoom, bearing].
 * The zoom fits the van-to-door line, stretched to the road distance left, into the map area (an ellipse fit, so it
 * never kinks) and softly caps at S_MAX, so it closes in by itself as the van approaches. */
export function framing(d: number, len: number, rx: number[], ry: number[], step: number, lay: number[], out: number[]): void {
  "worklet";
  const p = [0, 0, 0, 0];
  routeAt(d, rx, ry, step, p);
  const n = rx.length - 1;
  const hx = rx[n];
  const hy = ry[n];
  const left = len - d;
  const b = bearingOf(logParam(left, len));
  const vx = hx - p[0];
  const vy = hy - p[1];
  const c = Math.cos(-b);
  const s = Math.sin(-b);
  const sx = vx * c - vy * s;
  const sy = vx * s + vy * c;
  const a = lay[L_AH] * 0.8;
  const w = lay[L_AW] * 0.66;
  // stretched towards the road distance left (half-way, in log terms): it falls steadily with the drive, so the zoom
  // closes in evenly even where the route turns away from the door
  const vl = Math.hypot(sx, sy);
  const grow = vl > 1 ? Math.sqrt(Math.max(left, vl) / vl) : 1;
  const q = ((sy / a) * (sy / a) + (sx / w) * (sx / w)) * grow * grow;
  const inv = 1 / S_MAX;
  const zc = 1 / Math.pow(q * q + inv * inv * inv * inv, 0.25);
  // and never further out than S_FLOOR (soft), so a small screen still shows streets, not a postage stamp
  const z = Math.pow(zc * zc * zc * zc + S_FLOOR * S_FLOOR * S_FLOOR * S_FLOOR, 0.25);
  // close in, keep the door a little below the middle so the building above it is in frame
  const near = 1 - clamp01(left / 150);
  out[0] = (p[0] + hx) / 2;
  out[1] = (p[1] + hy) / 2 - 5 * near;
  out[2] = Math.log2(z);
  out[3] = b;
}

// ---------------------------------------------------------------- the film

/** The drive's log-distance parameter at t (0 .. 1): a steady push with a smooth start and a smooth stop (velocity
 * ramps shaped by smoothstep, so there is no jerk at either end). */
export function driveU(t: number): number {
  "worklet";
  const T = PHASE.driveB - PHASE.driveA;
  const tau = Math.min(Math.max(t - PHASE.driveA, 0), T);
  const v = 1 / (T - RAMP_IN / 2 - RAMP_OUT / 2);
  if (tau < RAMP_IN) {
    const x = tau / RAMP_IN;
    return v * RAMP_IN * (x * x * x - (x * x * x * x) / 2);
  }
  if (tau < T - RAMP_OUT) return v * (RAMP_IN / 2 + tau - RAMP_IN);
  const y = (tau - (T - RAMP_OUT)) / RAMP_OUT;
  return Math.min(1, v * (RAMP_IN / 2 + (T - RAMP_OUT - RAMP_IN) + RAMP_OUT * (y - (y * y * y - (y * y * y * y) / 2))));
}

/** Metres driven at t. */
export function filmDrive(t: number, len: number): number {
  "worklet";
  return len - remainingAt(driveU(t), len);
}

/** The film's camera: [cx, cy, log2 zoom, bearing, following 0/1]. Framing, except for the drag-to-look-ahead and the
 * glide back after the locate tap; while the van stands at the door the view turns and leans in a touch. */
export function filmCamera(t: number, len: number, rx: number[], ry: number[], step: number, lay: number[], out: number[]): void {
  "worklet";
  const live = [0, 0, 0, 0];
  if (t < PHASE.backA) {
    framing(filmDrive(t, len), len, rx, ry, step, lay, live);
  } else {
    // the pull-back: an even zoom out from the door to the poster (log zoom eased), the centre weighted by the zoom so
    // the door stays put on screen at first and the city slides in under it
    const a = [0, 0, 0, 0];
    const b = [0, 0, 0, 0];
    framing(len, len, rx, ry, step, lay, a);
    framing(0, len, rx, ry, step, lay, b);
    const k = easeInOutSine(seg(t, PHASE.backA, PHASE.backB));
    const lz = lerp(a[2], b[2], k);
    const ia = Math.pow(2, -a[2]);
    const ib = Math.pow(2, -b[2]);
    const w = (Math.pow(2, -lz) - ia) / (ib - ia);
    live[0] = lerp(a[0], b[0], w);
    live[1] = lerp(a[1], b[1], w);
    live[2] = lz;
    live[3] = lerp(a[3], b[3], k);
  }
  const f = easeOutCubic(seg(t, PHASE.grab - 0.05, PHASE.grab + 0.3)) * (1 - easeInOutCubic(seg(t, PHASE.recenter, PHASE.glideB)));
  if (f > 0) {
    const fr = [0, 0, 0, 0];
    framing(filmDrive(PHASE.grab - 0.05, len), len, rx, ry, step, lay, fr);
    // the map follows the finger: the camera moves the other way, in metres
    const k = easeInOutCubic(seg(t, PHASE.dragA, PHASE.dragB));
    const ox = DRAG_X * k;
    const oy = DRAG_Y * k;
    const z = Math.pow(2, fr[2]);
    const cb = Math.cos(fr[3]);
    const sb = Math.sin(fr[3]);
    const wx = (ox * cb - oy * sb) / z;
    const wy = (ox * sb + oy * cb) / z;
    live[0] = lerp(live[0], fr[0] - wx, f);
    live[1] = lerp(live[1], fr[1] - wy, f);
    live[2] = lerp(live[2], fr[2], f);
    live[3] = lerp(live[3], fr[3], f);
  }
  const drift = bump(t, 11.8, 16.4);
  out[0] = live[0];
  out[1] = live[1];
  out[2] = live[2] + 0.1 * drift;
  out[3] = live[3] - 0.09 * drift;
  out[4] = t >= PHASE.grab && t < PHASE.recenter ? 0 : 1;
}

/** The van: [metres along the route, visibility]. It leaves the door while the camera pulls out and the next round
 * appears at the hub. */
export function filmVan(t: number, len: number, out: number[]): void {
  "worklet";
  if (t < 16.5) {
    out[0] = filmDrive(t, len);
    out[1] = 1 - easeInCubic(seg(t, PHASE.vanOutA, PHASE.vanOutB));
  } else {
    out[0] = 0;
    out[1] = easeOutCubic(seg(t, PHASE.vanInA, PHASE.vanInB));
  }
}

/** The travelled part of the route, greyed: [end fraction, visibility]. */
export function filmTravelled(t: number, len: number, out: number[]): void {
  "worklet";
  out[0] = t < 17.3 ? filmDrive(t, len) / len : 0;
  out[1] = t < 17.3 ? 1 - easeInOutCubic(seg(t, 16.5, 17.3)) : 1;
}

/** The panel's "Arrived" state, 0 .. 1. */
export function filmArrived(t: number): number {
  "worklet";
  return easeInOutCubic(seg(t, PHASE.arriveA, PHASE.arriveB)) * (1 - easeInOutCubic(seg(t, PHASE.vanInA, PHASE.vanInB)));
}

/** The "Sent" toast, 0 .. 1. */
export function filmToast(t: number): number {
  "worklet";
  return easeOutCubic(seg(t, PHASE.msgSend, PHASE.msgSend + 0.3)) * (1 - easeInCubic(seg(t, 16.6, 17.0)));
}

/** The film's "Calling Marco…" pill after the Call tap, 0 .. 1. */
export function filmCall(t: number): number {
  "worklet";
  return easeOutCubic(seg(t, PHASE.callOpen, PHASE.callOpen + 0.3)) * (1 - easeInCubic(seg(t, PHASE.callClose, PHASE.callClose + 0.25)));
}

/** The door's pulse after arrival, 0 .. 1 (one ring), or 0. */
export function filmDoorPulse(t: number): number {
  "worklet";
  const k = seg(t, PHASE.driveB - 0.05, PHASE.driveB + 1.0);
  return k > 0 && k < 1 ? k : 0;
}

/** Ghost finger: [kind (0 none, 1 drag on the map, 2 tap on locate, 3 tap on Call, 4 tap on Message), visibility,
 * press 0..1, x, y]. `buttons` holds the centres [locate x, y, call x, y, message x, y]. */
export function filmFinger(t: number, lay: number[], buttons: number[], out: number[]): void {
  "worklet";
  out[0] = 0;
  out[1] = 0;
  out[2] = 0;
  if (t >= PHASE.grabIn && t < PHASE.release + 0.3) {
    const k = easeInOutCubic(seg(t, PHASE.dragA, PHASE.dragB));
    out[0] = 1;
    out[1] = easeOutCubic(seg(t, PHASE.grabIn, PHASE.grab)) * (1 - easeOutCubic(seg(t, PHASE.release, PHASE.release + 0.3)));
    out[2] = seg(t, PHASE.grabIn + 0.08, PHASE.grab) * (1 - seg(t, PHASE.dragB + 0.02, PHASE.release));
    out[3] = lay[L_W] * 0.7 + DRAG_X * k;
    out[4] = lay[L_FY] - 110 + DRAG_Y * k;
    return;
  }
  const taps = [PHASE.recenter, PHASE.call, PHASE.msg];
  for (let i = 0; i < 3; i++) {
    const tap = taps[i];
    if (t >= tap - 0.32 && t < tap + 0.42) {
      out[0] = 2 + i;
      out[1] = easeOutCubic(seg(t, tap - 0.32, tap - 0.12)) * (1 - easeOutCubic(seg(t, tap + 0.14, tap + 0.42)));
      out[2] = bump(t, tap - 0.12, tap + 0.14);
      out[3] = buttons[i * 2];
      out[4] = buttons[i * 2 + 1] + 3;
      return;
    }
  }
}

// ---------------------------------------------------------------- copy

/** "12 min" .. "1 min" for the distance left. */
export function etaText(dLeft: number, len: number): string {
  "worklet";
  const m = Math.max(1, Math.ceil((ETA_MIN * dLeft) / len - 0.02));
  return `${m} min`;
}

/** "3.9 km away · On the way" .. "40 m away · Almost there". */
export function distanceText(dLeft: number): string {
  "worklet";
  const d = Math.max(dLeft, 0);
  const dist = d >= 1000 ? `${(d / 1000).toFixed(1)} km` : `${Math.max(10, Math.round(d / 10) * 10)} m`;
  if (d < 15) return "Pulling up outside";
  return `${dist} away · ${d > 400 ? "On the way" : "Almost there"}`;
}

// ---------------------------------------------------------------- projection

/** The screen camera from [cx, cy, log2 zoom, bearing]: [cx, cy, zoom, cos, sin, focus x, focus y, ground squash,
 * tilt]. */
export function screenCam(cx: number, cy: number, lz: number, b: number, lay: number[], out: number[]): void {
  "worklet";
  const s = Math.pow(2, lz);
  const tilt = tiltOf(s);
  out[0] = cx;
  out[1] = cy;
  out[2] = s;
  out[3] = Math.cos(-b);
  out[4] = Math.sin(-b);
  out[5] = lay[L_FX];
  out[6] = lay[L_FY];
  out[7] = 1 - 0.28 * tilt;
  out[8] = tilt;
}

/** A ground point (m) on the screen (pt). */
export function toScreen(wx: number, wy: number, cam: number[], out: number[], o: number): void {
  "worklet";
  const dx = (wx - cam[0]) * cam[2];
  const dy = (wy - cam[1]) * cam[2];
  out[o] = cam[5] + dx * cam[3] - dy * cam[4];
  out[o + 1] = cam[6] + (dx * cam[4] + dy * cam[3]) * cam[7];
}

/** Screen points per metre of height, and the direction a height goes on the screen (a little to the right of up, so
 * the west walls show as well as the south ones). */
export const EXTRUDE_X = 0.2;
export function heightScale(cam: number[]): number {
  "worklet";
  return cam[2] * cam[8] * 0.9;
}
