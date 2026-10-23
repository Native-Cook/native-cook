/**
 * The upgrade's simulation (fixed step, UI thread), built for the replica's Blender layers: carry the key to the
 * keyhole (magnet), it turns into the lock (key-front → key-in), turn it through three detents, the shackle pops
 * and the vault sheet rises. A finger, the autoplay and the buttons share `input`. Helpers come before callers.
 */
import { UPG } from './theme';
import { G_HAPTIC, G_LOOP_MS, G_SCRIPT, GM, RESUME_MS, STEP } from './motion';

export const M = { idle: 0, carrying: 1, inserting: 2, seated: 3, open: 4, closing: 5 } as const;

export type Geo = { s: number; restX: number; restY: number; plugX: number; plugY: number; ringX: number; ringY: number };

export function makeGeo(width: number): Geo {
  const s = width / 402;
  return {
    s,
    restX: UPG.keyRest.x * s, restY: UPG.keyRest.y * s,
    plugX: UPG.plug.x * s, plugY: UPG.plug.y * s,
    ringX: UPG.ring.x * s, ringY: UPG.ring.y * s,
  };
}

export type Input =
  | { type: 'down'; x: number; y: number; script?: boolean }
  | { type: 'move'; x: number; y: number }
  | { type: 'up'; vx: number; vy: number }
  | { type: 'command'; action: 'unlock' | 'lock'; script?: boolean };

export type GSim = {
  acc: number; clock: number; mode: number; modeT: number;
  kx: number; ky: number; kvx: number; kvy: number; lift: number;
  th: number; tv: number; detent: number; strain: number;
  shackle: number; shackleV: number; sheet: number; glint: number; shake: number; show: number; idleT: number; tick: number;
  grab: number; scripted: boolean; gx: number; gy: number; fx: number; fy: number; x0: number; th0: number;
  demoOn: boolean; demoT: number; quiet: number; auto: number;
  reduced: boolean; events: number[];
};

export function initialGSim(g: Geo, reduced: boolean, autoPlay: boolean): GSim {
  'worklet';
  return {
    acc: 0, clock: 0, mode: M.idle, modeT: 0,
    kx: g.restX, ky: g.restY, kvx: 0, kvy: 0, lift: 0,
    th: 0, tv: 0, detent: 0, strain: 0,
    shackle: 0, shackleV: 0, sheet: 0, glint: -1, shake: -1, show: 0, idleT: 0, tick: -1,
    grab: 0, scripted: false, gx: 0, gy: 0, fx: 0, fy: 0, x0: 0, th0: 0,
    demoOn: autoPlay, demoT: 0, quiet: 0, auto: -1, reduced, events: [],
  };
}

export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return v < lo ? lo : v > hi ? hi : v;
}

function emit(s: GSim, e: number) {
  'worklet';
  if (!s.scripted && !s.demoOn && s.auto < 0) s.events.push(e);
}

function setMode(s: GSim, m: number) {
  'worklet';
  s.mode = m;
  s.modeT = 0;
}

function seat(s: GSim, g: Geo) {
  'worklet';
  setMode(s, M.inserting);
  s.grab = 0;
  s.kx = g.plugX;
  s.ky = g.plugY;
  emit(s, G_HAPTIC.seat);
}

function open(s: GSim) {
  'worklet';
  setMode(s, M.open);
  s.grab = 0;
  s.th = 1;
  s.tv = 0;
  s.shackleV += s.reduced ? 0 : GM.shackleKick;
  s.glint = 0;
  emit(s, G_HAPTIC.open);
}

export function input(s: GSim, g: Geo, ev: Input) {
  'worklet';
  if (ev.type === 'command') {
    if (!ev.script) {
      s.demoOn = false;
      s.quiet = 0;
    }
    if (ev.action === 'lock' && s.mode === M.open) setMode(s, M.closing);
    else if (ev.action === 'unlock' && s.mode === M.idle) s.auto = 0;
    return;
  }
  if (ev.type === 'down') {
    s.scripted = ev.script === true;
    if (!s.scripted) {
      s.demoOn = false;
      s.auto = -1;
      s.quiet = 0;
    }
    s.fx = ev.x;
    s.fy = ev.y;
    s.grab = 0;
    if ((s.mode === M.idle || s.mode === M.carrying) && Math.abs(ev.x - s.kx) < GM.keyHitW * g.s && Math.abs(ev.y - s.ky) < GM.keyHitH * g.s) {
      s.grab = 1;
      setMode(s, M.carrying);
      s.gx = s.kx - ev.x;
      s.gy = s.ky - ev.y;
      emit(s, G_HAPTIC.grab);
    } else if (s.mode === M.seated && Math.hypot(ev.x - g.ringX, ev.y - g.ringY) < GM.ringHit * g.s) {
      s.grab = 2;
      s.x0 = ev.x;
      s.th0 = s.th;
      s.strain = 0;
    }
    return;
  }
  if (ev.type === 'move') {
    s.fx = ev.x;
    s.fy = ev.y;
    return;
  }
  if (s.grab === 1) {
    s.grab = 0;
    // Velocity OR distance: near the keyhole, or flung toward it.
    const d = Math.hypot(s.kx - g.plugX, s.ky - g.plugY);
    const toward = (g.plugX - s.kx) * ev.vx + (g.plugY - s.ky) * ev.vy;
    if (d < GM.magnet * g.s || (toward > 0 && Math.hypot(ev.vx, ev.vy) > GM.flick * g.s && d < GM.magnet * 2.2 * g.s)) seat(s, g);
    else {
      setMode(s, M.idle);
      s.kvx = ev.vx;
      s.kvy = ev.vy;
    }
  } else if (s.grab === 2) {
    s.grab = 0;
    if (s.strain < -0.05 && !s.reduced) {
      s.shake = 0;
      emit(s, G_HAPTIC.wrong);
    }
  }
  s.scripted = false;
}

function runDemo(s: GSim, g: Geo, t0: number, t1: number) {
  'worklet';
  const c = G_SCRIPT.carry;
  const fromX = g.restX;
  const fromY = g.restY + GM.grabBelow * g.s;
  const ease = (u: number) => {
    'worklet';
    return (1 - Math.cos(Math.PI * clamp(u))) / 2;
  };
  if (t0 <= c[0] && c[0] < t1) input(s, g, { type: 'down', x: fromX, y: fromY, script: true });
  if (t1 > c[0] && t0 < c[1] && s.grab === 1) {
    const u = ease((Math.min(t1, c[1]) - c[0]) / (c[1] - c[0]));
    const tx = g.plugX + 6 * g.s;
    const ty = g.plugY + (GM.grabBelow + 10) * g.s;
    input(s, g, { type: 'move', x: fromX + (tx - fromX) * u, y: fromY + (ty - fromY) * u });
  }
  if (t0 < c[1] && c[1] <= t1 && s.grab === 1) input(s, g, { type: 'up', vx: 0, vy: 0 });
  const tn = G_SCRIPT.turn;
  if (t0 <= tn[0] && tn[0] < t1) input(s, g, { type: 'down', x: g.ringX, y: g.ringY, script: true });
  if (t1 > tn[0] && t0 < tn[1] && s.grab === 2) {
    const u = ease((Math.min(t1, tn[1]) - tn[0]) / (tn[1] - tn[0]));
    input(s, g, { type: 'move', x: g.ringX + G_SCRIPT.turnBy * g.s * u, y: g.ringY });
  }
  if (t0 < tn[1] && tn[1] <= t1 && s.grab === 2) input(s, g, { type: 'up', vx: 0, vy: 0 });
  if (t0 <= G_SCRIPT.lockAt && G_SCRIPT.lockAt < t1) input(s, g, { type: 'command', action: 'lock', script: true });
}

function spring(x: number, v: number, to: number, k: number, c: number, dt: number) {
  'worklet';
  const nv = v + (-k * (x - to) - c * v) * dt;
  return { x: x + nv * dt, v: nv };
}

function step(s: GSim, g: Geo, autoPlay: boolean) {
  'worklet';
  const dt = STEP;
  if (s.demoOn) {
    const t0 = s.demoT;
    let t1 = t0 + dt;
    runDemo(s, g, t0, t1);
    s.demoOn = true;
    if (t1 * 1000 >= G_LOOP_MS) {
      t1 -= G_LOOP_MS / 1000;
      s.kx = g.restX;
      s.ky = g.restY;
      s.kvx = 0;
      s.kvy = 0;
    }
    s.demoT = t1;
  } else if (s.auto >= 0) {
    const t0 = s.auto + G_SCRIPT.carry[0];
    runDemo(s, g, t0, t0 + dt);
    s.auto += dt;
    if (t0 > G_SCRIPT.turn[1] + 0.1) s.auto = -1;
  } else if (autoPlay && s.grab === 0) {
    s.quiet += dt;
    if (s.quiet * 1000 >= RESUME_MS && (s.mode === M.idle || s.mode === M.open)) {
      s.demoOn = true;
      s.demoT = s.mode === M.open ? G_SCRIPT.resumeOpen : 0;
    }
  }
  s.modeT += dt;

  if (s.mode === M.carrying && s.grab === 1) {
    let tx = s.fx + s.gx;
    let ty = s.fy + s.gy;
    const d = Math.hypot(tx - g.plugX, ty - g.plugY);
    const r = GM.magnet * g.s;
    if (d < r && !s.reduced) {
      const pull = GM.magnetPull * Math.pow(1 - d / r, 0.6);
      tx += (g.plugX - tx) * pull;
      ty += (g.plugY - ty) * pull;
    }
    s.kvx = (tx - s.kx) / dt;
    s.kvy = (ty - s.ky) / dt;
    s.kx = tx;
    s.ky = ty;
    if (Math.hypot(tx - g.plugX, ty - g.plugY) < GM.seat * g.s) seat(s, g);
  } else if (s.mode === M.idle) {
    const a = spring(s.kx, s.kvx, g.restX, GM.home.k, GM.home.c, dt);
    s.kx = a.x;
    s.kvx = a.v;
    const b = spring(s.ky, s.kvy, g.restY, GM.home.k, GM.home.c, dt);
    s.ky = b.x;
    s.kvy = b.v;
  } else if (s.mode === M.inserting && s.modeT >= (s.reduced ? STEP : GM.insert)) {
    setMode(s, M.seated);
  }
  s.lift += ((s.grab === 1 ? 1 : 0) - s.lift) * (1 - Math.exp(-14 * dt));

  // Turning: horizontal drag across the ring; the wrong way strains with rubber; three detents; 1 = open.
  if (s.mode === M.seated) {
    if (s.grab === 2) {
      const raw = s.th0 + (s.fx - s.x0) / (GM.turnPx * g.s);
      if (raw < 0) {
        s.strain = raw;
        s.th = -GM.wrong * (1 - Math.exp(raw / GM.wrong));
      } else s.th = raw;
      if (s.th >= 1) open(s);
      // Pulling down at 0 takes the key out again.
      if (s.fy - g.ringY > GM.pullOut * g.s && s.th < 0.05) {
        setMode(s, M.carrying);
        s.grab = 1;
        s.th = 0;
        s.kx = g.plugX;
        s.ky = g.plugY;
        s.gx = s.kx - s.fx;
        s.gy = s.ky - s.fy;
      }
    } else {
      const a = spring(s.th, s.tv, 0, GM.back.k, GM.back.c, dt);
      s.th = a.x;
      s.tv = a.v;
    }
  } else if (s.mode === M.closing) {
    if (s.sheet < 0.02 && s.shackle < 0.05) {
      const a = spring(s.th, s.tv, 0, GM.back.k, GM.back.c, dt);
      s.th = a.x;
      s.tv = a.v;
      if (Math.abs(s.th) < 0.02) {
        s.th = 0;
        s.tv = 0;
        setMode(s, M.idle);
        s.kx = g.plugX;
        s.ky = g.plugY + GM.outPull * g.s;
        s.kvy = GM.outSpeed * g.s;
      }
    }
  }
  let k = 0;
  for (let i = 0; i < GM.detents.length; i++) if (s.th >= GM.detents[i] - 1e-4) k = i + 1;
  if (k > s.detent) {
    emit(s, G_HAPTIC.detent);
    if (!s.reduced) s.tick = 0; // the detent's flash at the keyhole
  }
  s.detent = k;

  const up = s.mode === M.open || (s.mode === M.closing && s.sheet > GM.shackleAfter) ? 1 : 0;
  if (s.reduced) {
    s.shackle = up;
    s.shackleV = 0;
  } else {
    const a = spring(s.shackle, s.shackleV, up, up ? GM.shackle.k : GM.shackle.kClose, up ? GM.shackle.c : GM.shackle.cClose, dt);
    s.shackle = a.x;
    s.shackleV = a.v;
  }
  const want = s.mode === M.open ? 1 : 0;
  const delay = s.mode === M.open ? GM.sheetDelay : 0;
  if (s.modeT >= delay) s.sheet += (want - s.sheet) * (1 - Math.exp(-(want ? GM.sheetIn : GM.sheetOut) * dt));
  // The lock materialises (as in the source's cross-fade) once the key is taken, and goes when it is left alone.
  const resting = s.mode === M.idle && s.grab === 0;
  s.idleT = resting ? s.idleT + dt : 0;
  const showTo = resting && s.idleT > GM.show.hold ? 0 : resting && s.show < 0.01 ? 0 : 1;
  s.show += (showTo - s.show) * (1 - Math.exp(-(showTo ? GM.show.in : GM.show.out) * dt));
  if (s.glint >= 0) s.glint = s.glint + dt > GM.glint ? -1 : s.glint + dt;
  if (s.shake >= 0) s.shake = s.shake + dt > GM.shake.duration ? -1 : s.shake + dt;
  if (s.tick >= 0) s.tick = s.tick + dt > GM.tick.duration ? -1 : s.tick + dt;
  s.clock += dt;
}

export function stepG(s: GSim, g: Geo, frameDt: number, autoPlay: boolean) {
  'worklet';
  s.acc = Math.min(s.acc + frameDt, STEP * 30);
  while (s.acc >= STEP) {
    step(s, g, autoPlay);
    s.acc -= STEP;
  }
}
