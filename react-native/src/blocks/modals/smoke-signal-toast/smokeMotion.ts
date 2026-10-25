/*
 Smoke Signal Toast — geometry, the autoPlay script and the touch simulation (worklets).

 LOOP = 9 s:
   0.0 – 1.3   rest: the fire crackles, a faint wisp rises.
   1.3 – 2.2   the fire flares and puffs a cloud (flare 1 → 1.35 → 1).
   2.2 – 3.8   the cloud rises to the middle of the sky (ease-in-out, 1.6 – 3.1) and condenses
               into the message: blurred words appear inside it (2.8 – 3.3), their scatter shrinks
               50 → 0 pt and their offset from below 50 → 0 pt (ease-in-out, 2.9 – 3.85) while
               the cloud thins away (3.0 – 3.7).
   3.8 – 5.6   readable hold.
   5.6 – 7.2   a gust from the left tears it to the right and it thins out (ease-in-out).
   7.2 – 9.0   the fire calms (flare dips to 0.85 and returns) and rests.
*/

export const LOOP = 9;
export const REDUCED_AT = 4.6;
export const IDLE_BEFORE_BACK = 2;
export const BACK_FOR = 0.9;
export const MODE_AUTO = 0;
export const MODE_LIVE = 1;
export const MODE_BACK = 2;

export const HEADER_H = 60;
export const INPUT_H = 52;

export type SmokeGeo = {
  w: number;
  h: number;
  fireX: number;
  fireY: number;
  fireS: number;
  treeY: number;
  toastY: number;
  inputTop: number;
};

export function makeSmokeGeo(w: number, h: number, insetTop: number, insetBottom: number): SmokeGeo {
  'worklet';
  const inputTop = h - Math.max(insetBottom, 12) - 8 - INPUT_H;
  const fireS = Math.min(1, h / 852);
  const fireY = inputTop - 40 * fireS;
  const treeY = fireY - 70 * fireS;
  const msgBottom = insetTop + HEADER_H + 200;
  const toastY = Math.max(msgBottom + 70, Math.min((msgBottom + treeY - 110) / 2 + 20, treeY - 170));
  return { w, h, fireX: w / 2, fireY, fireS, treeY, toastY, inputTop };
}

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export type SmokeFrame = {
  flare: number;
  /** Puff: x, y, radius, density. */
  puff: number[];
  /** Formed, scatter, rise, alpha. */
  text: number[];
  /** Wind direction and amount. */
  wind: number[];
  phase: number;
};

export function emptyFrame(): SmokeFrame {
  'worklet';
  return { flare: 1, puff: [0, 0, 1, 0], text: [0, 50, 50, 0], wind: [1, 0, 0, 0], phase: 0 };
}

export function scriptFrame(t: number, g: SmokeGeo, f: SmokeFrame) {
  'worklet';
  f.phase = t / LOOP;
  // The flare when the puff leaves, the calm at the end.
  f.flare = 1 + 0.35 * Math.sin(Math.PI * seg(t, 1.3, 2.2)) - 0.15 * Math.sin(Math.PI * seg(t, 7.2, 8.6));
  // The puff: born at the fire, rising to the toast's line.
  const born = easeOut(seg(t, 1.3, 1.8));
  const up = easeInOut(seg(t, 1.6, 3.1));
  f.puff[0] = g.fireX + Math.sin(up * Math.PI) * 18;
  f.puff[1] = g.fireY - 40 * g.fireS - (g.fireY - 40 * g.fireS - g.toastY) * up;
  f.puff[2] = 22 + 110 * up;
  f.puff[3] = born * (1 - easeInOut(seg(t, 3.0, 3.7))) * 0.9;
  // The message condenses out of it.
  const form = easeInOut(seg(t, 2.9, 3.85));
  f.text[0] = form;
  f.text[1] = 50 * (1 - form);
  f.text[2] = 50 * (1 - form);
  f.text[3] = easeOut(seg(t, 2.8, 3.3));
  f.wind[0] = 1;
  f.wind[1] = -0.18;
  f.wind[2] = easeInOut(seg(t, 5.6, 7.2));
  if (t >= 7.2) f.text[3] = 0;
}

export type SmokeSim = {
  mode: number;
  t: number;
  now: number;
  last: number;
  back: number;
  frame: SmokeFrame;
  /** Swipe: whether a finger drags the message, how far (pt), and the release animation. */
  dragging: boolean;
  blow: number;
  blowV: number;
  blowTarget: number;
  /** Whether the message card is open. */
  open: boolean;
  from: number[];
};

export function makeSim(): SmokeSim {
  'worklet';
  return {
    mode: MODE_AUTO,
    t: 0,
    now: 0,
    last: 0,
    back: 0,
    frame: emptyFrame(),
    dragging: false,
    blow: 0,
    blowV: 0,
    blowTarget: 0,
    open: false,
    from: [0, 0, 0, 0, 0],
  };
}

function goLive(s: SmokeSim) {
  'worklet';
  if (s.mode === MODE_LIVE) return;
  s.mode = MODE_LIVE;
  s.blow = s.frame.wind[2];
  s.blowTarget = s.blow > 0.5 ? 1 : s.blow;
  s.blowV = 0;
  s.frame.puff[3] = 0;
}

export function simTouch(s: SmokeSim) {
  'worklet';
  goLive(s);
  s.last = s.now;
}

/** Whether the message is up and readable enough to be tapped open or blown away. */
export function readable(s: SmokeSim) {
  'worklet';
  const f = s.frame;
  return f.text[3] > 0.5 && f.text[0] > 0.6 && f.wind[2] < 0.5;
}

/** A swipe drags the wind: its direction and how far it has gone blow the words apart. */
export function simDrag(s: SmokeSim, dx: number, dy: number) {
  'worklet';
  goLive(s);
  s.dragging = true;
  s.last = s.now;
  const d = Math.hypot(dx, dy);
  if (d > 4) {
    s.frame.wind[0] = dx / d;
    s.frame.wind[1] = dy / d;
  }
  s.blow = Math.min(1, d / 260);
}

export function simDragEnd(s: SmokeSim, vx: number, vy: number) {
  'worklet';
  s.dragging = false;
  s.last = s.now;
  const fling = Math.hypot(vx, vy);
  s.blowTarget = s.blow > 0.32 || fling > 650 ? 1 : 0;
  s.blowV = Math.min(4, fling / 400);
}

export function simOpen(s: SmokeSim, open: boolean) {
  'worklet';
  goLive(s);
  s.open = open;
  s.last = s.now;
  if (open) {
    // The words drift up out of the way as the card takes their place.
    s.frame.wind[0] = 0.15;
    s.frame.wind[1] = -1;
    s.blowTarget = 1;
    s.blowV = 1.2;
  }
}

/** One frame; returns true when the loop restarts. */
export function simStep(s: SmokeSim, dt: number, g: SmokeGeo, auto: boolean, reduced: boolean) {
  'worklet';
  s.now += dt;
  const f = s.frame;
  if (s.mode === MODE_AUTO) {
    if (auto) s.t = (s.t + dt) % LOOP;
    scriptFrame(reduced ? REDUCED_AT : s.t, g, f);
    return false;
  }
  f.phase += dt / LOOP;
  if (s.mode === MODE_LIVE) {
    f.flare += (1 - f.flare) * Math.min(1, dt * 3);
    if (!s.dragging) {
      // Released: a spring to blown away (1) or back together (0), carrying the fling.
      const w = 9;
      const a = w * w * (s.blowTarget - s.blow) - 2 * 0.9 * w * s.blowV;
      s.blowV += a * dt;
      s.blow = Math.min(1, Math.max(0, s.blow + s.blowV * dt));
    }
    f.wind[2] = s.blow;
    if (auto && !s.dragging && s.now - s.last > IDLE_BEFORE_BACK) {
      s.mode = MODE_BACK;
      s.back = 0;
      s.from[0] = f.text[3];
      s.from[1] = f.wind[2];
      s.from[2] = f.phase;
      s.from[3] = f.flare;
      s.open = false;
    }
    return false;
  }
  s.back = Math.min(1, s.back + dt / BACK_FOR);
  const k = easeInOut(s.back);
  f.text[3] = s.from[0] * (1 - k);
  f.wind[2] = s.from[1] + (1 - s.from[1]) * k * 0.5;
  f.phase = s.from[2] + (Math.ceil(s.from[2]) - s.from[2]) * k;
  f.flare = s.from[3] + (1 - s.from[3]) * k;
  if (s.back >= 1) {
    s.mode = MODE_AUTO;
    s.t = 0;
    s.blow = 0;
    s.open = false;
    scriptFrame(0, g, f);
    return true;
  }
  return false;
}

export function smokeUniforms(f: SmokeFrame, g: SmokeGeo) {
  'worklet';
  return {
    size: [Math.max(1, g.w), Math.max(1, g.h)],
    fire: [g.fireX, g.fireY, f.flare, g.fireS],
    puff: f.puff.slice(),
    text: f.text.slice(),
    wind: f.wind.slice(),
    phase: f.phase - Math.floor(f.phase),
    treeY: g.treeY,
  };
}
