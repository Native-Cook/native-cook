/**
 * Fixed-step UI-thread simulation of the key, the lock and the vault sheet. A finger and the autoplay's scripted
 * finger go through `input`; the buttons and accessibility actions through `command`.
 */
import { KEY, LOCK, SHEET, STAGE_WIDTH } from './theme';
import {
  BOUNCE, CARRY, GLINT, HAPTIC, INSERT, LOOP_MS, MAX_STEPS, RESUME_MS, SCRIPT, SHACKLE, SHAKE, SHEET_MOTION, STEP, TURN,
} from './motion';

export const MODE = { idle: 0, carrying: 1, inserting: 2, seated: 3, open: 4, closing: 5 } as const;

export type Geometry = {
  w: number; h: number; s: number;
  faceX: number; lockTop: number; lockBottom: number; plugX: number; plugY: number;
  restX: number; restY: number; seatY: number;
};

export function makeGeometry(w: number, h: number): Geometry {
  const s = w / STAGE_WIDTH;
  const lockTop = h * LOCK.topRatio;
  const lockBottom = lockTop + LOCK.height * s;
  const plugY = lockBottom + LOCK.plugHeight * s;
  return {
    w, h, s, faceX: w / 2 - (LOCK.face * s) / 2, lockTop, lockBottom, plugX: w / 2, plugY,
    restX: w / 2, restY: h * KEY.restRatio, seatY: plugY - KEY.blade * s,
  };
}

export type Input =
  | { type: 'down'; x: number; y: number; script?: boolean }
  | { type: 'move'; x: number; y: number; vx: number; vy: number }
  | { type: 'up'; vx: number; vy: number }
  | { type: 'command'; action: 'unlock' | 'lock'; script?: boolean };

export type Sim = {
  acc: number; clock: number; mode: number;
  kx: number; ky: number; kvx: number; kvy: number; lift: number;
  insT: number; insFrom: number; insTo: number; insDur: number;
  th: number; tv: number; detent: number;
  shackle: number; shackleV: number; bounce: number; bounceV: number; shakeT: number; glintT: number;
  sheet: number; sheetT: number; sheetFrom: number; sheetTo: number; sheetDur: number; sheetDelay: number;
  grab: number; scripted: boolean; gx: number; gy: number; fx: number; fy: number; x0: number; th0: number;
  wrongHint: number;
  demoOn: boolean; demoT: number; quiet: number;
  autoUnlock: number; // the Unlock button plays the same finger as the demo
  reduced: boolean; events: number[];
};

export function initialSim(g: Geometry, reduced: boolean, autoPlay: boolean): Sim {
  'worklet';
  return {
    acc: 0, clock: 0, mode: MODE.idle,
    kx: g.restX, ky: g.restY, kvx: 0, kvy: 0, lift: 0,
    insT: 1, insFrom: g.plugY, insTo: g.plugY, insDur: 1,
    th: 0, tv: 0, detent: 0,
    shackle: 0, shackleV: 0, bounce: 0, bounceV: 0, shakeT: -1, glintT: -1,
    sheet: 0, sheetT: 1, sheetFrom: 0, sheetTo: 0, sheetDur: 1, sheetDelay: 0,
    grab: 0, scripted: false, gx: 0, gy: 0, fx: 0, fy: 0, x0: 0, th0: 0,
    wrongHint: 0,
    demoOn: autoPlay, demoT: 0, quiet: 0, autoUnlock: -1,
    reduced, events: [],
  };
}

export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return v < lo ? lo : v > hi ? hi : v;
}

function rubber(over: number, limit: number) {
  'worklet';
  return limit * (1 - Math.exp(-over / limit));
}

function easeOutCubic(u: number) {
  'worklet';
  return 1 - Math.pow(1 - u, 3);
}

function emit(s: Sim, e: number) {
  'worklet';
  if (!s.scripted && !s.demoOn && s.autoUnlock < 0) s.events.push(e);
}

function spring(x: number, v: number, target: number, k: number, c: number, dt: number) {
  'worklet';
  const nv = v + (-k * (x - target) - c * v) * dt;
  return { x: x + nv * dt, v: nv };
}

function startSheet(s: Sim, to: number, delay: number) {
  'worklet';
  s.sheetFrom = s.sheet;
  s.sheetTo = to;
  s.sheetT = 0;
  s.sheetDelay = delay;
  const d = s.reduced ? SHEET_MOTION.reducedFade : to === 1 ? SHEET_MOTION.open : SHEET_MOTION.close;
  s.sheetDur = Math.max(0.05, d * Math.abs(to - s.sheet));
}

function seat(s: Sim, g: Geometry) {
  'worklet';
  s.mode = MODE.inserting;
  s.grab = 0;
  s.kx = g.plugX;
  s.insFrom = g.plugY;
  s.insTo = g.seatY;
  s.insT = 0;
  s.insDur = s.reduced ? STEP : INSERT.duration;
  emit(s, HAPTIC.seat);
}

function unseat(s: Sim, g: Geometry) {
  'worklet';
  s.mode = MODE.inserting;
  s.insFrom = s.ky;
  s.insTo = g.plugY + 2 * g.s;
  s.insT = 0;
  s.insDur = s.reduced ? STEP : INSERT.outDuration;
}

function open(s: Sim) {
  'worklet';
  s.mode = MODE.open;
  s.grab = 0;
  s.th = TURN.open;
  s.tv = 0;
  s.bounceV += BOUNCE.openKick;
  s.shackleV += s.reduced ? 0 : 4;
  s.glintT = 0;
  startSheet(s, 1, s.reduced ? 0 : SHEET_MOTION.delay);
  emit(s, HAPTIC.open);
}

export function input(s: Sim, g: Geometry, ev: Input) {
  'worklet';
  if (ev.type === 'command') {
    if (!ev.script) {
      s.demoOn = false;
      s.quiet = 0;
    }
    if (ev.action === 'lock' && s.mode === MODE.open) {
      s.mode = MODE.closing;
      startSheet(s, 0, 0);
    } else if (ev.action === 'unlock' && s.mode === MODE.idle) {
      s.autoUnlock = 0;
    }
    return;
  }
  if (ev.type === 'down') {
    s.scripted = ev.script === true;
    if (!s.scripted) {
      s.demoOn = false;
      s.autoUnlock = -1;
      s.quiet = 0;
    }
    s.fx = ev.x;
    s.fy = ev.y;
    s.grab = 0;
    const bowY = s.ky + (KEY.blade + KEY.ring / 2) * g.s;
    if (s.mode === MODE.idle || s.mode === MODE.carrying) {
      // Anywhere on the key: shaft or bow.
      if (Math.abs(ev.x - s.kx) < (KEY.hit / 2) * g.s && ev.y > s.ky - 20 * g.s && ev.y < bowY + (KEY.ring / 2 + 16) * g.s) {
        s.grab = 1;
        s.mode = MODE.carrying;
        s.gx = s.kx - ev.x;
        s.gy = s.ky - ev.y;
        emit(s, HAPTIC.grab);
      }
    } else if (s.mode === MODE.seated) {
      if (Math.hypot(ev.x - s.kx, ev.y - bowY) < (KEY.hit / 2 + 12) * g.s) {
        s.grab = 2;
        s.x0 = ev.x;
        s.gy = ev.y;
        s.th0 = s.th;
      }
    }
    return;
  }
  if (ev.type === 'move') {
    s.fx = ev.x;
    s.fy = ev.y;
    return;
  }
  // up
  if (s.grab === 1) {
    s.grab = 0;
    const d = Math.hypot(s.kx - g.plugX, s.ky - g.plugY);
    if (d < CARRY.magnet * g.s) seat(s, g);
    else {
      s.mode = MODE.idle;
      s.kvx = ev.vx;
      s.kvy = ev.vy;
    }
  } else if (s.grab === 2) {
    s.grab = 0;
    if (s.th < -0.1 && !s.reduced) {
      s.shakeT = 0;
      emit(s, HAPTIC.wrong);
    }
    if (s.th < -0.1) s.wrongHint = 1;
  }
  s.scripted = false;
}

function runDemo(s: Sim, g: Geometry, t0: number, t1: number) {
  'worklet';
  const c = SCRIPT.carry;
  const tn = SCRIPT.turn;
  if (t0 <= c[0] && c[0] < t1) input(s, g, { type: 'down', x: g.restX, y: g.restY + 40 * g.s, script: true });
  if (t1 > c[0] && t0 < c[1] && s.grab === 1) {
    const u = (1 - Math.cos(Math.PI * clamp((Math.min(t1, c[1]) - c[0]) / (c[1] - c[0])))) / 2;
    input(s, g, { type: 'move', x: g.restX + SCRIPT.carryTo[0] * g.s * u, y: g.restY + 40 * g.s + SCRIPT.carryTo[1] * g.s * u, vx: 0, vy: 0 });
  }
  if (t0 < c[1] && c[1] <= t1 && s.grab === 1) input(s, g, { type: 'up', vx: 0, vy: 0 });
  const bowY = g.seatY + (KEY.blade + KEY.ring / 2) * g.s;
  if (t0 <= tn[0] && tn[0] < t1) input(s, g, { type: 'down', x: g.plugX - 8 * g.s, y: bowY, script: true });
  if (t1 > tn[0] && t0 < tn[1] && s.grab === 2) {
    const u = (1 - Math.cos(Math.PI * clamp((Math.min(t1, tn[1]) - tn[0]) / (tn[1] - tn[0])))) / 2;
    input(s, g, { type: 'move', x: g.plugX - 8 * g.s + SCRIPT.turnBy * g.s * u, y: bowY, vx: 0, vy: 0 });
  }
  if (t0 < tn[1] && tn[1] <= t1 && s.grab === 2) input(s, g, { type: 'up', vx: 0, vy: 0 });
  if (t0 <= SCRIPT.lockAt && SCRIPT.lockAt < t1) input(s, g, { type: 'command', action: 'lock', script: true });
}

function fixedStep(s: Sim, g: Geometry, autoPlay: boolean) {
  'worklet';
  const dt = STEP;
  if (s.demoOn) {
    const t0 = s.demoT;
    let t1 = t0 + dt;
    runDemo(s, g, t0, t1);
    if (t1 * 1000 >= LOOP_MS) {
      t1 -= LOOP_MS / 1000;
      if (s.mode === MODE.idle && Math.hypot(s.kx - g.restX, s.ky - g.restY) < 0.5) {
        s.kx = g.restX;
        s.ky = g.restY;
        s.kvx = 0;
        s.kvy = 0;
      }
    }
    s.demoT = t1;
  } else if (s.autoUnlock >= 0) {
    // The Unlock button replays the demo's finger from its first segment.
    const t0 = s.autoUnlock + SCRIPT.carry[0];
    runDemo(s, g, t0, t0 + dt);
    s.autoUnlock += dt;
    if (t0 > SCRIPT.turn[1] + 0.1) s.autoUnlock = -1;
  } else if (autoPlay && s.grab === 0) {
    s.quiet += dt;
    if (s.quiet * 1000 >= RESUME_MS && (s.mode === MODE.idle || s.mode === MODE.open)) {
      s.demoOn = true;
      s.demoT = s.mode === MODE.open ? 5 : 0;
    }
  }

  // Key: carried with a magnet near the keyhole, sliding in or out, or floating home.
  if (s.mode === MODE.carrying && s.grab === 1) {
    let tx = s.fx + s.gx;
    let ty = s.fy + s.gy;
    const d = Math.hypot(tx - g.plugX, ty - g.plugY);
    const r = CARRY.magnet * g.s;
    if (d < r && !s.reduced) {
      const pull = CARRY.magnetPull * Math.pow(1 - d / r, 0.6);
      tx += (g.plugX - tx) * pull;
      ty += (g.plugY - ty) * pull;
    }
    s.kvx = (tx - s.kx) / dt;
    s.kvy = (ty - s.ky) / dt;
    s.kx = tx;
    s.ky = ty;
    if (Math.hypot(tx - g.plugX, ty - g.plugY) < CARRY.seat * g.s) seat(s, g);
  } else if (s.mode === MODE.inserting) {
    s.insT = Math.min(s.insDur, s.insT + dt);
    const u = easeOutCubic(s.insT / s.insDur);
    s.ky = s.insFrom + (s.insTo - s.insFrom) * u;
    s.kx = g.plugX;
    if (s.insT >= s.insDur) {
      if (s.insTo === g.seatY) s.mode = MODE.seated;
      else {
        s.mode = MODE.idle;
        s.kvy = 0;
      }
    }
  } else if (s.mode === MODE.idle) {
    const a = spring(s.kx, s.kvx, g.restX, CARRY.home.stiffness, CARRY.home.damping, dt);
    s.kx = a.x;
    s.kvx = a.v;
    const b = spring(s.ky, s.kvy, g.restY, CARRY.home.stiffness, CARRY.home.damping, dt);
    s.ky = b.x;
    s.kvy = b.v;
  }
  s.lift += ((s.grab === 1 ? 1 : 0) - s.lift) * (1 - Math.exp(-14 * dt));

  // Turning: a horizontal drag across the bow, rubber-banded the wrong way; detents click; 90° opens.
  if (s.mode === MODE.seated) {
    if (s.grab === 2) {
      let th = s.th0 + (s.fx - s.x0) / (TURN.radius * g.s);
      if (th < 0) th = -rubber(-th, TURN.wrong);
      s.tv = (th - s.th) / dt;
      s.th = th;
      // Pulling straight down at 0° takes the key out again.
      if (s.fy - s.gy > CARRY.pullOut * g.s && Math.abs(s.th) < 0.1) {
        s.grab = 1;
        s.mode = MODE.carrying;
        s.gx = s.kx - s.fx;
        s.gy = s.ky - s.fy;
        s.th = 0;
        s.tv = 0;
      }
      if (s.th >= TURN.open) open(s);
    } else {
      const a = spring(s.th, s.tv, 0, TURN.back.stiffness, TURN.back.damping, dt);
      s.th = a.x;
      s.tv = a.v;
    }
  } else if (s.mode === MODE.closing && s.shackle < 0.06) {
    // Closing runs in order: sheet down, shackle home, key turns back, key out.
    const a = spring(s.th, s.tv, 0, TURN.close.stiffness, TURN.close.damping, dt);
    s.th = a.x;
    s.tv = a.v;
    if (s.sheet < 0.02 && Math.abs(s.th) < 0.02 && s.shackle < 0.02) {
      s.th = 0;
      s.tv = 0;
      unseat(s, g);
    }
  }
  if (s.th > 0.3 && s.mode === MODE.seated) s.wrongHint = 0;
  let k = 0;
  for (let i = 0; i < TURN.detents.length; i++) if (s.th >= TURN.detents[i] - 1e-4) k = i + 1;
  if (k !== s.detent) {
    if (k > s.detent) {
      s.bounceV += BOUNCE.detentKick;
      emit(s, HAPTIC.detent);
    }
    s.detent = k;
  }

  // Shackle and lock body.
  const up = s.mode === MODE.open || (s.mode === MODE.closing && s.sheet > SHEET_MOTION.shackleAfter) ? 1 : 0;
  if (s.reduced) {
    s.shackle += (up - s.shackle) * (1 - Math.exp(-18 * dt));
    s.shackleV = 0;
  } else {
    const opening = up === 1;
    const a = spring(s.shackle, s.shackleV, up, opening ? SHACKLE.stiffness : SHACKLE.closeStiffness,
      opening ? SHACKLE.damping : SHACKLE.closeDamping, dt);
    s.shackle = a.x;
    s.shackleV = a.v;
  }
  if (!s.reduced) {
    const b = spring(s.bounce, s.bounceV, 0, BOUNCE.stiffness, BOUNCE.damping, dt);
    s.bounce = b.x;
    s.bounceV = b.v;
  } else {
    s.bounce = 0;
    s.bounceV = 0;
  }
  if (s.shakeT >= 0) {
    s.shakeT += dt;
    if (s.shakeT > SHAKE.duration) s.shakeT = -1;
  }
  if (s.glintT >= 0) {
    s.glintT += dt;
    if (s.glintT > GLINT.duration) s.glintT = -1;
  }

  // Sheet.
  if (s.sheetT < s.sheetDur + s.sheetDelay) {
    s.sheetT += dt;
    const u = clamp((s.sheetT - s.sheetDelay) / s.sheetDur);
    const e = s.reduced ? u : s.sheetTo === 1 ? 1 - Math.pow(1 - u, 4) : u * u * (3 - 2 * u);
    s.sheet = s.sheetFrom + (s.sheetTo - s.sheetFrom) * e;
  }
  s.clock += dt;
}

export function stepSim(s: Sim, g: Geometry, frameDt: number, autoPlay: boolean) {
  'worklet';
  // Events from input() and from the steps both wait here until publish() hands them to JS and clears them.
  s.acc = Math.min(s.acc + frameDt, STEP * MAX_STEPS);
  while (s.acc >= STEP) {
    fixedStep(s, g, autoPlay);
    s.acc -= STEP;
  }
}

/** Horizontal shake offset of the lock after a wrong-way turn. */
export function shakeOffset(s: Sim) {
  'worklet';
  if (s.shakeT < 0) return 0;
  const u = s.shakeT / SHAKE.duration;
  return SHAKE.amp * (1 - u) * Math.sin(2 * Math.PI * SHAKE.cycles * u);
}

/** The lock group's rise and scale toward the top while the sheet is up. */
export function lockLift(sheet: number, g: Geometry) {
  'worklet';
  return { dy: -SHEET.rise * g.h * sheet, scale: 1 - (1 - SHEET.shrink) * sheet };
}
