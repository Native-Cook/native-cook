// Setup Checklist All Set — the state is made of flags and the moments they last changed (each item
// done or not, each row open or not, all set, the sheet leaving and coming back); one function per
// piece turns it and a time into what is on screen. The loop builds that state for any time t;
// touches write a live one on the clock.

export const LOOP = 9.0;
export const RESUME = 2;
export const N = 6;
export const NEVER = -100;

export const D = {
  tick: 0.42, // the check: fill, then the tick drawn, then the strike through
  morph: 0.55, // the sheet shrinks into the icon's bubble …
  bloom: 0.6, // … and the all-set sheet grows out of it
  leave: 0.5, // a sheet sliding down
  enter: 0.7, // the checklist sliding back up
};

export type State = {
  done: boolean[];
  doneAt: number[]; // when `done` last changed
  open: boolean[];
  openAt: number[]; // when `open` last changed
  pressAt: number; // an action button pressed …
  pressRow: number; // … on this row
  allSetAt: number; // the morph to "You're all set" starts (NEVER: not yet)
  leaveAt: number; // the sheet starts down …
  backAt: number; // … and a checklist comes back up
  reset: boolean; // the one coming back is fresh
  ctaAt: number;
  laterAt: number;
};

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

/** The list as the pin opens it: only the last item done, the first row open. */
export function freshState(): State {
  'worklet';
  return {
    done: [false, false, false, false, false, true],
    doneAt: [NEVER, NEVER, NEVER, NEVER, NEVER, NEVER],
    open: [true, false, false, false, false, false],
    openAt: [NEVER, NEVER, NEVER, NEVER, NEVER, NEVER],
    pressAt: NEVER,
    pressRow: -1,
    allSetAt: NEVER,
    leaveAt: NEVER,
    backAt: NEVER,
    reset: false,
    ctaAt: NEVER,
    laterAt: NEVER,
  };
}

const L = {
  done: [0.7, 1.45, 2.15, 2.85, 3.55],
  press: [0.52, 1.27, 1.97, 2.67, 3.37],
  allSet: 3.95,
  later: 7.0,
  leave: 7.2,
  back: 7.9,
};

/** The loop's state at t: five items ticked one after another, all set, maybe later, back. */
export function loopState(t: number): State {
  'worklet';
  const s = freshState();
  if (t >= L.back) {
    s.backAt = L.back;
    s.leaveAt = L.leave;
    return s;
  }
  for (let i = 0; i < 5; i++) {
    const dn = L.done[i];
    if (t >= dn) {
      s.done[i] = true;
      s.doneAt[i] = dn;
    }
    if (t >= L.press[i]) {
      s.pressAt = L.press[i];
      s.pressRow = i;
    }
    const openT = i === 0 ? NEVER : L.done[i - 1] + 0.24;
    const closeT = dn + 0.16;
    if (t >= closeT) {
      s.open[i] = false;
      s.openAt[i] = closeT;
    } else if (t >= openT) {
      s.open[i] = true;
      s.openAt[i] = openT;
    } else {
      s.open[i] = false;
      s.openAt[i] = NEVER;
    }
  }
  if (t >= L.allSet) s.allSetAt = L.allSet;
  if (t >= L.later) s.laterAt = L.later;
  if (t >= L.leave) {
    s.leaveAt = L.leave;
    s.backAt = L.back;
    s.reset = true;
  }
  return s;
}

/** How open row i is (0…1, a spring). */
export function openness(s: State, i: number, now: number) {
  'worklet';
  const k = s.openAt[i] <= NEVER ? 1 : s.open[i] ? spring(now - s.openAt[i], 0.42, 0.86) : spring(now - s.openAt[i], 0.36, 0.92);
  return s.open[i] ? k : 1 - k;
}
/** How done item i looks (0…1 over the tick's 0.42 s; 0.22 s back when unticked). */
export function doneness(s: State, i: number, now: number) {
  'worklet';
  if (s.doneAt[i] <= NEVER) return s.done[i] ? 1 : 0;
  return s.done[i] ? clamp01((now - s.doneAt[i]) / D.tick) : 1 - clamp01((now - s.doneAt[i]) / 0.22);
}
/** The count, rolling for 0.3 s after each change. */
export function countRolled(s: State, now: number) {
  'worklet';
  let v = 0;
  for (let i = 0; i < N; i++) {
    const k = s.doneAt[i] <= NEVER ? 1 : smooth((now - s.doneAt[i] - 0.08) / 0.3);
    v += s.done[i] ? k : 1 - k;
  }
  return v;
}

export type Driver = { t: number; mode: 0 | 2; last: number; amb: number; s: State };

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, last: -10, amb: t, s: loopState(t) };
}

function isFresh(s: State) {
  'worklet';
  if (s.allSetAt > NEVER) return false;
  for (let i = 0; i < N; i++) {
    if (s.done[i] !== (i === 5)) return false;
    if (s.open[i] !== (i === 0)) return false;
  }
  return true;
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  const s = d.s;
  // a sheet sent down comes back (fresh when asked)
  if (s.reset && s.backAt > NEVER && now >= s.backAt) {
    const f = freshState();
    f.leaveAt = s.leaveAt;
    f.backAt = s.backAt;
    d.s = f;
  }
  if (!auto || now - d.last < RESUME) return;
  const busy = d.s.leaveAt > NEVER && now < d.s.backAt + D.enter;
  if (busy) return;
  if (isFresh(d.s) && openness(d.s, 0, now) > 0.999) {
    d.mode = 0;
    d.t = 0;
  } else if (now - d.last >= RESUME + 2) {
    // ease home the way the loop does: down, and a fresh list up
    d.s.leaveAt = now;
    d.s.backAt = now + D.leave + 0.2;
    d.s.reset = true;
  }
}

/** Hand the loop's moment to the driver, its moments moved onto the clock. */
export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) return;
  const s = loopState(d.t);
  const k = now - d.t;
  const sh = (x: number) => (x <= NEVER ? x : x + k);
  for (let i = 0; i < N; i++) {
    s.doneAt[i] = sh(s.doneAt[i]);
    s.openAt[i] = sh(s.openAt[i]);
  }
  s.pressAt = sh(s.pressAt);
  s.allSetAt = sh(s.allSetAt);
  s.laterAt = sh(s.laterAt);
  s.leaveAt = sh(s.leaveAt);
  s.backAt = sh(s.backAt);
  if (s.backAt > NEVER && now >= s.backAt) s.reset = false; // already the fresh list
  // what the loop was about to do next still happens: the next row opens, or the list goes all set
  if (s.allSetAt <= NEVER && s.leaveAt <= NEVER) {
    let next = -1;
    let anyOpen = false;
    for (let i = 0; i < N; i++) {
      if (s.open[i] && s.done[i]) {
        // a row just ticked folds as it would have
        s.open[i] = false;
        s.openAt[i] = Math.max(now, s.doneAt[i] + 0.16);
      }
      if (s.open[i]) anyOpen = true;
      if (next < 0 && !s.done[i]) next = i;
    }
    if (next < 0) s.allSetAt = now + 0.3;
    else if (!anyOpen) {
      s.open[next] = true;
      s.openAt[next] = now + 0.08;
    }
  }
  d.s = s;
  d.mode = 2;
}

/** Tick item i (or untick it); open the next undone row; all set after the last. */
export function toggle(d: Driver, i: number, now: number) {
  'worklet';
  const s = d.s;
  if (s.done[i]) {
    s.done[i] = false;
    s.doneAt[i] = now;
    return 'uncheck';
  }
  s.done[i] = true;
  s.doneAt[i] = now;
  s.pressAt = now;
  s.pressRow = i;
  if (s.open[i]) {
    s.open[i] = false;
    s.openAt[i] = now + 0.16;
  }
  let next = -1;
  for (let k = 1; k <= N; k++) {
    const j = (i + k) % N;
    if (!s.done[j]) {
      next = j;
      break;
    }
  }
  if (next < 0) {
    s.allSetAt = now + 0.4;
    return 'complete';
  }
  if (!s.open[next]) {
    s.open[next] = true;
    s.openAt[next] = now + 0.24;
  }
  return 'check';
}

export function toggleRow(d: Driver, i: number, now: number) {
  'worklet';
  const s = d.s;
  if (s.open[i]) {
    s.open[i] = false;
    s.openAt[i] = now;
    return;
  }
  for (let j = 0; j < N; j++) {
    if (j !== i && s.open[j]) {
      s.open[j] = false;
      s.openAt[j] = now;
    }
  }
  s.open[i] = true;
  s.openAt[i] = now;
}

export function skip(d: Driver, i: number, now: number) {
  'worklet';
  const s = d.s;
  s.open[i] = false;
  s.openAt[i] = now;
  for (let k = 1; k < N; k++) {
    const j = (i + k) % N;
    if (!s.done[j]) {
      s.open[j] = true;
      s.openAt[j] = now + 0.12;
      return;
    }
  }
}

/** Send the sheet down; it comes back (fresh after "all set"). */
export function dismiss(d: Driver, now: number, fresh: boolean) {
  'worklet';
  const s = d.s;
  if (s.leaveAt > NEVER && now < s.backAt + D.enter) return false;
  s.leaveAt = now;
  s.backAt = now + D.leave + 0.35;
  s.reset = fresh;
  return true;
}
