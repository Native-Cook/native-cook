// Spilled Coffee Error — the loop and the touch driver. One pose drives the drawing: how far the cup
// is tipped (0 upright … 90 on its side), how much coffee is on the paper, where the lid is.

export const LOOP = 9.0;
export const RESUME = 2;

export const T = {
  press: 1.0, // Retry is pressed …
  rewind: [1.15, 2.35], // … time runs back: the coffee flows up into the cup …
  rise: 1.9, // … the cup stands up (a spring with a wobble) …
  lid: [2.35, 2.85], // … and its lid flies back on
  text: [1.2, 1.5], // "Trying again…"
  steam: [3.0, 5.6],
  fail: 5.65, // it fails again: the cup tips over …
  land: 6.2, // … lands with a bounce, the lid pops off, the coffee spills
  lidOff: [6.15, 6.7],
  spill: [6.2, 7.4],
  textBack: [5.8, 6.1],
} as const;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function mix(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}

export type Pose = {
  amb: number;
  theta: number; // degrees, 0 upright … 90 on its side
  spill: number; // 0 … 1 of the coffee on the paper
  pour: number; // 0 … 1: coffee running out of the mouth now
  lid: number; // 0 on the cup … 1 at rest on the floor (in between: flying)
  lidHop: number; // seconds since the resting lid was poked (−1 none)
  text: number; // 0 "Error!" … 1 "Trying again…"
  steam: number;
  dots: number; // 0 … 1: the button's loading dots
  rewind: number; // 0 … 1 while time runs back
  press: number; // 0 … 1: the button pressed in
  splash: number; // seconds since the coffee hit the paper (−1 none)
  drip: number; // seconds since a drop fell from the mouth (−1 none)
};

/** The retry script from a starting pose (θ0, s0, lid0) at τ seconds after the press. */
export function retryPose(tau: number, th0: number, s0: number, lid0: number, amb: number): Pose {
  'worklet';
  const t = T.press + tau;
  const rw = seg(t, T.rewind[0], T.rewind[1]);
  let theta: number;
  let spill = s0 * (1 - easeInOut(rw));
  let lid = lid0 * (1 - easeInOut(seg(t, T.lid[0], T.lid[1])));
  let pour = 0;
  let splash = -1;
  if (t < T.fail) {
    // standing up: a spring from θ0 to 0 that overshoots a little the other way
    theta = th0 * (1 - spring(t - T.rise, 0.55, 0.5));
  } else if (t < T.land) {
    const p = seg(t, T.fail, T.land);
    theta = 90 * p * p; // it falls, faster and faster
  } else {
    const q = t - T.land;
    theta = 90 - 9 * Math.exp(-q * 7) * Math.abs(Math.sin(q * 14));
    lid = easeOut(seg(t, T.lidOff[0], T.lidOff[1]));
    spill = easeOut(seg(t, T.spill[0], T.spill[1]));
    pour = 1 - smooth(seg(t, T.spill[1] - 0.5, T.spill[1]));
    splash = t - T.land;
  }
  if (t >= T.lidOff[0] && t < T.land) lid = easeOut(seg(t, T.lidOff[0], T.lidOff[1]));
  const text = smooth(seg(t, T.text[0], T.text[1])) * (1 - smooth(seg(t, T.textBack[0], T.textBack[1])));
  const steam = smooth(seg(t, T.steam[0], T.steam[0] + 0.6)) * (1 - smooth(seg(t, T.steam[1] - 0.4, T.steam[1])));
  const dots = smooth(seg(t, 1.25, 1.45)) * (1 - smooth(seg(t, 5.7, 5.9)));
  const press = Math.max(0, 1 - Math.abs(t - T.press - 0.06) / 0.12);
  return { amb, theta, spill, pour, lid, lidHop: -1, text, steam, dots, rewind: rw > 0 && rw < 1 ? Math.sin(Math.PI * rw) : 0, press, splash, drip: -1 };
}

export function poseAt(t: number): Pose {
  'worklet';
  if (t < T.press) {
    const p = retryPose(LOOP - T.press + t, 90, 1, 1, t);
    p.drip = t >= 0.35 ? t - 0.35 : -1;
    p.pour = 0;
    p.splash = -1;
    return p;
  }
  const p = retryPose(t - T.press, 90, 1, 1, t);
  if (t >= 7.9) p.drip = t - 7.9;
  return p;
}

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  theta: number;
  thetaV: number;
  dragging: number;
  dragTheta: number;
  spill: number;
  lid: number; // 0 on … 1 off (animated toward lidTo)
  lidFrom: number;
  lidAt: number;
  lidTo: number;
  hopAt: number;
  retryAt: number; // a retry script running from this moment (−10 none) …
  r0: number[]; // … from this pose: θ, spill, lid
  splashAt: number;
  pressAt: number;
  pressing: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return {
    t,
    mode: 0,
    last: -10,
    amb: t,
    theta: 90,
    thetaV: 0,
    dragging: 0,
    dragTheta: 90,
    spill: 1,
    lid: 1,
    lidFrom: 1,
    lidAt: -10,
    lidTo: 1,
    hopAt: -10,
    retryAt: -10,
    r0: [90, 1, 1],
    splashAt: -10,
    pressAt: -10,
    pressing: 0,
  };
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) return;
  d.mode = 2;
  if (d.t >= T.press && d.t < LOOP - 0.3) {
    // the loop was inside its retry: carry it on as a retry from its start
    d.retryAt = now - (d.t - T.press);
    d.r0 = [90, 1, 1];
  } else d.retryAt = -10;
  const p = poseAt(d.t);
  d.theta = p.theta;
  d.thetaV = 0;
  d.spill = p.spill;
  d.lid = p.lid;
  d.lidTo = p.lid > 0.5 ? 1 : 0;
  d.lidFrom = d.lid;
  d.lidAt = now - 1;
}

/** Stop a running retry where it is (a finger took the cup). */
function freeze(d: Driver, now: number) {
  'worklet';
  if (d.retryAt < 0) return;
  const p = retryPose(now - d.retryAt, d.r0[0], d.r0[1], d.r0[2], d.amb);
  d.theta = p.theta;
  d.spill = p.spill;
  d.lid = p.lid;
  d.lidFrom = p.lid;
  d.lidTo = p.lid > 0.5 ? 1 : 0;
  d.lidAt = now;
  d.retryAt = -10;
}

export function grabCup(d: Driver, now: number) {
  'worklet';
  freeze(d, now);
  d.dragging = 1;
  d.dragTheta = d.theta;
}

export function retry(d: Driver, now: number) {
  'worklet';
  freeze(d, now);
  d.retryAt = now - 0.02;
  d.r0 = [d.theta, d.spill, d.lid];
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  if (d.retryAt > 0) {
    const tau = now - d.retryAt;
    if (tau >= LOOP - T.press - 0.2) {
      // the script has played out: the cup lies spilled again
      const p = retryPose(tau, d.r0[0], d.r0[1], d.r0[2], d.amb);
      d.theta = p.theta;
      d.spill = p.spill;
      d.lid = 1;
      d.lidTo = 1;
      d.lidFrom = 1;
      d.retryAt = -10;
    }
    return;
  }
  if (d.dragging === 1) {
    d.theta += (d.dragTheta - d.theta) * Math.min(1, dt * 20);
    d.thetaV = 0;
  } else {
    // past 45° it falls; below, it rights itself
    const target = d.theta > 45 ? 90 : 0;
    const k = target === 90 ? 110 : 90;
    const c = 2 * (target === 90 ? 0.35 : 0.5) * Math.sqrt(k);
    d.thetaV += (-k * (d.theta - target) - c * d.thetaV) * dt;
    d.theta += d.thetaV * dt;
    if (d.theta > 90) {
      d.theta = 90;
      d.thetaV = -Math.abs(d.thetaV) * 0.3;
    }
  }
  // it pours while tipped past 55°
  if (d.theta > 55 && d.spill < 1) {
    const before = d.spill;
    d.spill = Math.min(1, d.spill + 1.1 * ((d.theta - 55) / 35) * dt);
    if (before === 0 && d.spill > 0) d.splashAt = now;
  }
  // the lid comes off when the cup goes over
  if (d.theta > 60 && d.lidTo === 0) {
    d.lidFrom = d.lid;
    d.lidTo = 1;
    d.lidAt = now;
  }
  d.lid = mix(d.lidFrom, d.lidTo, easeOut((now - d.lidAt) / 0.55));
  // back home: on its side, spilled, lid off; then the loop
  if (auto && d.dragging === 0 && now - d.last >= RESUME) {
    if (d.theta < 89.5 && d.theta <= 45) d.thetaV += 260 * dt; // tip it over
    if (d.theta > 89.4 && Math.abs(d.thetaV) < 2 && d.spill > 0.999 && d.lid > 0.999 && now - d.splashAt > 1.2) {
      d.mode = 0;
      d.t = 0;
    }
  }
}

export function poseOf(d: Driver, now: number): Pose {
  'worklet';
  if (d.mode === 0) return poseAt(d.t);
  if (d.retryAt > 0) {
    const p = retryPose(now - d.retryAt, d.r0[0], d.r0[1], d.r0[2], d.amb);
    p.amb = d.amb;
    return p;
  }
  const pour = d.theta > 55 && d.spill < 1 ? clamp01((d.theta - 55) / 20) : 0;
  return {
    amb: d.amb,
    theta: d.theta,
    spill: d.spill,
    pour,
    lid: d.lid,
    lidHop: now - d.hopAt < 0.7 ? now - d.hopAt : -1,
    text: 0,
    steam: 0,
    dots: 0,
    rewind: 0,
    press: d.pressing === 1 ? smooth((now - d.pressAt) / 0.1) : 0,
    splash: now - d.splashAt < 1.4 ? now - d.splashAt : -1,
    drip: -1,
  };
}
