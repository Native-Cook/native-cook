// Lucky Task Hub — the loop and the touch driver. Both end in a Frame the drawing reads.

import { BALANCE, BILL_VALUE, FLIPS } from './ltk-theme';

export const LOOP = 9.0;
export const RESUME = 2;
export const BILLS = 5;
export const BILL_FLIGHT = 0.78;
export const BILL_GAP = 0.09;
export const FLIP_DUR = 0.9;
export const HOP_DUR = 1.0;

export const T = {
  spin: [0.8, 2.6], // the wheel spins 2¼ turns and lands …
  bills: 2.62, // … five bills fly to the balance
  prog: [3.0, 3.7], // a step of the newbie tasks is done
  flip: 4.2, // the Lucky Flip card turns peace → fist
  hop: 5.4, // the trophy hops and comes down silver
  flipBack: 7.0,
  tierBack: [7.2, 7.7],
  progBack: [7.4, 8.2],
  balBack: [7.7, 8.2],
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
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}
export function easeOutQuint(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 5);
}
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}
/** 0 → 1 → 0 bump over `dur` seconds after `at`. */
export function bump(t: number, at: number, dur: number) {
  'worklet';
  const p = (t - at) / dur;
  return p <= 0 || p >= 1 ? 0 : Math.sin(Math.PI * p);
}

export type Frame = {
  amb: number;
  wheel: number; // degrees
  wheelSpeed: number; // 0…1, for the eyes and the blur of the slices
  pop: number; // the spin card's flash on landing
  billsT: number; // seconds since the bills left the wheel (−1 none)
  balance: number; // the balance as it reads (a float: digits roll)
  balFade: number; // 0…1: crossfade of the balance back to its start (loop only)
  bump: number; // the balance pill's bump as a bill lands
  prog: number; // finished steps (a float)
  flipAng: number; // degrees of the Lucky Flip card's turn, 0…360
  flipFrom: number; // 0 peace, 1 fist
  flipTo: number;
  remaining: number; // flips left (a float: rolls)
  hop: number; // seconds since the trophy hopped (−1 none)
  tierFrom: number;
  tierTo: number;
  tierK: number; // 0…1 crossfade between tiers
  chest: number; // seconds since the chest was poked (−1 none)
  press: number; // which Go is held (−1 none) …
  pressK: number; // … and how far in
};

/** What has landed of `BILLS` bills by `tau` seconds after they left. */
export function landed(tau: number) {
  'worklet';
  let n = 0;
  for (let i = 0; i < BILLS; i++) n += smooth((tau - BILL_FLIGHT - i * BILL_GAP) / 0.22);
  return n;
}
export function landBump(tau: number) {
  'worklet';
  let b = 0;
  for (let i = 0; i < BILLS; i++) b = Math.max(b, bump(tau, BILL_FLIGHT + i * BILL_GAP, 0.24));
  return b;
}

export function frameAt(t: number): Frame {
  'worklet';
  const sp = seg(t, T.spin[0], T.spin[1]);
  const wheel = 810 * easeOutQuint(sp);
  const wheelSpeed = sp > 0 && sp < 1 ? Math.pow(1 - sp, 4) : 0;
  const billsT = t >= T.bills && t < T.balBack[0] ? t - T.bills : -1;
  const balUp = billsT >= 0 ? landed(billsT) : t >= T.balBack[0] ? BILLS : 0;
  const balFade = smooth(seg(t, T.balBack[0], T.balBack[1]));
  const prog = 3 + easeInOut(seg(t, T.prog[0], T.prog[1])) - easeInOut(seg(t, T.progBack[0], T.progBack[1]));
  let flipAng = 0;
  let flipFrom = 0;
  let flipTo = 0;
  if (t >= T.flip && t < T.flipBack) {
    flipAng = 360 * easeInOut(seg(t, T.flip, T.flip + FLIP_DUR));
    flipTo = 1;
  } else if (t >= T.flipBack) {
    flipAng = 360 * easeInOut(seg(t, T.flipBack, T.flipBack + FLIP_DUR));
    flipFrom = 1;
  }
  const remaining = FLIPS - 4 - smooth(seg(t, T.flip + 0.35, T.flip + 0.75)) + smooth(seg(t, T.flipBack + 0.35, T.flipBack + 0.75));
  const hop = t >= T.hop && t < T.hop + HOP_DUR + 0.6 ? t - T.hop : -1;
  let tierFrom = 0;
  let tierTo = 0;
  let tierK = 0;
  if (t >= T.hop && t < T.tierBack[0]) {
    tierTo = 1;
    tierK = smooth(seg(t, T.hop + 0.32, T.hop + 0.44));
  } else if (t >= T.tierBack[0]) {
    tierFrom = 1;
    tierK = smooth(seg(t, T.tierBack[0], T.tierBack[1]));
  }
  return {
    amb: t,
    wheel,
    wheelSpeed,
    pop: bump(t, T.spin[1] - 0.05, 0.4),
    billsT,
    balance: BALANCE + BILL_VALUE * balUp,
    balFade,
    bump: billsT >= 0 ? landBump(billsT) : 0,
    prog,
    flipAng,
    flipFrom,
    flipTo,
    remaining,
    hop,
    tierFrom,
    tierTo,
    tierK,
    chest: t >= 6.7 && t < 7.6 ? t - 6.7 : -1,
    press: -1,
    pressK: 0,
  };
}

// ------------------------------------------------------------------ the driver

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  wheel: number;
  wheelV: number; // degrees per second
  dragging: number;
  spun: number; // degrees turned since the finger let go
  spinning: number;
  billsAt: number;
  balFrom: number;
  balance: number;
  progFrom: number;
  progTo: number;
  progAt: number;
  flipAt: number;
  flipFrom: number;
  flipTo: number;
  remFrom: number;
  remTo: number;
  hopAt: number;
  tierFrom: number;
  tierTo: number;
  chestAt: number;
  press: number;
  pressLast: number;
  pressAt: number;
  releaseAt: number;
  homeAt: number; // easing back to the loop's first frame before it resumes
  wheelFrom: number;
  wheelTo: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return {
    t,
    mode: 0,
    last: -10,
    amb: t,
    wheel: 0,
    wheelV: 0,
    dragging: 0,
    spun: 0,
    spinning: 0,
    billsAt: -10,
    balFrom: BALANCE,
    balance: BALANCE,
    progFrom: 3,
    progTo: 3,
    progAt: -10,
    flipAt: -10,
    flipFrom: 0,
    flipTo: 0,
    remFrom: FLIPS - 4,
    remTo: FLIPS - 4,
    hopAt: -10,
    tierFrom: 0,
    tierTo: 0,
    chestAt: -10,
    press: -1,
    pressLast: 0,
    pressAt: -10,
    releaseAt: -10,
    homeAt: -10,
    wheelFrom: 0,
    wheelTo: 0,
  };
}

/** Hand the loop's state to the driver at the moment a finger lands. */
export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) {
    if (d.homeAt > 0) {
      // a finger stops the way home where it is
      d.homeAt = -10;
      d.balance = Math.round(d.balance);
      d.wheelV = 0;
    }
    return;
  }
  const f = frameAt(d.t);
  d.mode = 2;
  d.wheel = f.wheel % 360;
  d.wheelV = 0;
  d.spinning = 0;
  d.billsAt = -10;
  d.balance = f.balFade > 0.5 ? BALANCE : Math.round(f.balance);
  d.balFrom = d.balance;
  d.progFrom = d.progTo = Math.round(f.prog);
  d.flipFrom = d.flipTo = f.flipAng > 180 ? f.flipTo : f.flipFrom;
  d.flipAt = -10;
  d.remFrom = d.remTo = Math.round(f.remaining);
  d.tierFrom = d.tierTo = f.tierK > 0.5 ? f.tierTo : f.tierFrom;
  d.hopAt = -10;
  d.chestAt = -10;
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  // the wheel: free spin with friction, then a soft settle onto the nearest slice
  if (d.dragging === 0 && d.spinning === 1) {
    const sgn = d.wheelV >= 0 ? 1 : -1;
    const speed = Math.abs(d.wheelV);
    const next = Math.max(0, speed - (90 + speed * 0.9) * dt);
    d.wheelV = sgn * next;
    d.wheel += d.wheelV * dt;
    d.spun += Math.abs(d.wheelV * dt);
    if (next < 40) {
      // settle onto a slice
      const target = Math.round(d.wheel / 45) * 45;
      d.wheel += (target - d.wheel) * Math.min(1, dt * 10);
      if (Math.abs(target - d.wheel) < 0.3) {
        d.wheel = target;
        d.wheelV = 0;
        d.spinning = 0;
        if (d.spun > 300) {
          d.billsAt = now;
          d.balFrom = d.balance;
          d.progFrom = d.progTo;
          d.progTo = Math.min(14, d.progTo + 1);
          d.progAt = now + 0.4;
        }
      }
    }
  }
  if (d.billsAt > 0) {
    const n = landed(now - d.billsAt);
    d.balance = d.balFrom + BILL_VALUE * n;
    if (now - d.billsAt > BILL_FLIGHT + BILLS * BILL_GAP + 0.4) {
      d.balance = d.balFrom + BILL_VALUE * BILLS;
      d.billsAt = -10;
    }
  }
  if (d.homeAt > 0) {
    const k = easeInOut((now - d.homeAt) / 0.8);
    d.wheel = mix(d.wheelFrom, d.wheelTo, k);
    d.balance = mix(d.balFrom, BALANCE, k);
    if (now - d.homeAt > 0.9 && now - d.hopAt > HOP_DUR + 0.6 && now - d.flipAt > FLIP_DUR && now - d.progAt > 0.8) {
      d.homeAt = -10;
      d.mode = 0;
      d.t = 0;
    }
    return;
  }
  if (auto && d.dragging === 0 && d.spinning === 0 && d.billsAt < 0 && d.press < 0 && now - d.last >= RESUME) {
    // ease every piece back to the loop's first frame, then hand over
    d.homeAt = now;
    d.wheelFrom = d.wheel;
    d.wheelTo = Math.round(d.wheel / 90) * 90;
    d.balFrom = d.balance;
    d.progFrom = mix(d.progFrom, d.progTo, easeInOut((now - d.progAt) / 0.7));
    d.progTo = 3;
    d.progAt = now;
    if (d.flipTo !== 0) {
      flipCard(d, now);
      d.remTo = FLIPS - 4;
    } else if (d.remTo !== FLIPS - 4) {
      d.remFrom = d.remTo;
      d.remTo = FLIPS - 4;
      d.flipAt = now - FLIP_DUR;
    }
    if (d.tierTo !== 0) {
      d.tierFrom = d.tierTo;
      d.tierTo = 0;
      d.hopAt = now;
    }
  }
}

export function frameOf(d: Driver, now: number): Frame {
  'worklet';
  if (d.mode === 0) return frameAt(d.t);
  const billsT = d.billsAt > 0 ? now - d.billsAt : -1;
  const flipP = easeInOut((now - d.flipAt) / FLIP_DUR);
  const hopT = now - d.hopAt;
  const pressK = d.press >= 0 ? smooth((now - d.pressAt) / 0.1) : 1 - smooth((now - d.releaseAt) / 0.16);
  return {
    amb: d.amb,
    wheel: d.wheel,
    wheelSpeed: Math.min(1, Math.abs(d.wheelV) / 900),
    pop: billsT >= 0 ? bump(billsT, -0.05, 0.4) : 0,
    billsT,
    balance: d.balance,
    balFade: 0,
    bump: billsT >= 0 ? landBump(billsT) : 0,
    prog: mix(d.progFrom, d.progTo, easeInOut((now - d.progAt) / 0.7)),
    flipAng: flipP >= 1 ? 0 : 360 * flipP,
    flipFrom: flipP >= 1 ? d.flipTo : d.flipFrom,
    flipTo: d.flipTo,
    remaining: mix(d.remFrom, d.remTo, smooth((now - d.flipAt - 0.35) / 0.4)),
    hop: hopT < HOP_DUR + 0.6 ? hopT : -1,
    tierFrom: d.tierFrom,
    tierTo: d.tierTo,
    tierK: smooth((hopT - 0.32) / 0.12),
    chest: now - d.chestAt < 0.9 ? now - d.chestAt : -1,
    press: d.press >= 0 ? d.press : d.pressLast,
    pressK,
  };
}

export function flipCard(d: Driver, now: number) {
  'worklet';
  if (now - d.flipAt < FLIP_DUR) return false;
  d.flipFrom = d.flipTo;
  d.flipTo = 1 - d.flipTo;
  d.flipAt = now;
  d.remFrom = d.remTo;
  d.remTo = d.remTo > 0 ? d.remTo - 1 : FLIPS;
  return true;
}

export function hopTrophy(d: Driver, now: number) {
  'worklet';
  if (now - d.hopAt < HOP_DUR) return false;
  d.tierFrom = d.tierTo;
  d.tierTo = (d.tierTo + 1) % 3;
  d.hopAt = now;
  return true;
}

/** A tap on the wheel (or its Go) gives it a strong spin. */
export function kickWheel(d: Driver) {
  'worklet';
  if (d.spinning === 1 || d.billsAt > 0) return false;
  d.wheelV = 1500 + Math.random() * 400;
  d.spinning = 1;
  d.spun = 0;
  return true;
}
