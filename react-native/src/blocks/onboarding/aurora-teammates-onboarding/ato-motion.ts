// Aurora Teammates Onboarding — the whole motion, as pure functions of the page position.
//
// `pos` runs 0..3 round a circle: an integer is a page at rest, a fraction is the way from page
// floor(pos) to the next. Autoplay walks it along the loop clock; a drag scrubs it; Next plays it to
// the next integer at the designed tempo. Every frame of every layer (the tgfx stage, the Skia
// sheet, the controls, the WebGPU motes) is computed here from (pos, ambient time), so a drag held
// half-way holds the whole screen half-way, and the loop's last frame is its first.
//
// Choreography of one page change (u = 0..1 of TRANS seconds):
//   0.00-0.41  the page's objects fold back into squircles and are drawn down into the light in the
//              notch, nearest first, tipping backwards (anticipation lift, then gravity).
//   0.34       the light flares as they go in; the notch dips; the aurora turns to the next palette.
//   0.22-0.77  the sheet slides to the next page's height (springy), the title's shared words glide.
//   0.22-1.00  the next page's objects are breathed out of the notch, back ones first: each rises as
//              a squircle lying back, stands up with a rock, fans out on an arc and unfolds into its
//              card or tile; its content fades in once it is open.
//
// On top of that, the inputs (`Input`):
//   - tilt: the phone's attitude (or, without a gyroscope, a slow drift that is a whole number of
//     turns per loop) moves the glass over the aurora with depth, turns it and swings its light;
//   - drag: while a finger drags, the notch (and the light in it) follows the finger and deepens with
//     its speed, then springs back to the centre on release;
//   - autoplay timer: the current dot is the timer, dim and filling while the page rests, its fill
//     flowing on to the next dot; a resting finger freezes it;
//   - finale (fin 0..1, page 3's Next): the tiles give up their light, it streams into the button,
//     the button blooms, pops and turns into "You're all set", then the onboarding starts over.

import type { Layout } from './ato-theme';
import { CARD, CARDS, ICONS, PAGES, TILE, TILES } from './ato-theme';

export const LOOP = 9;
export const HOLD = 1.5;
export const TRANS = 1.5;
export const SEG = HOLD + TRANS;
/** Release / Next: time for one full page change (s). */
export const PLAY_TRANS = TRANS;
/** Touch resumes the loop this long after the last touch (s). */
export const RESUME = 2;

const EXIT_START = 0.0;
const EXIT_STAGGER = 0.03;
const EXIT_DUR = 0.26;
const ENTER_START = 0.22;
const ENTER_STAGGER = 0.055;
const ENTER_DUR = 0.52;
const SHEET_START = 0.22;
const SHEET_DUR = 0.55;
const FLARE_AT = 0.34;
const FLARE_W = 0.14;
/** The glass seed every card and tile unfolds from (pin px). */
const SEED = 40;
const EXIT_ROTX = (62 * Math.PI) / 180;
const ENTER_ROTX = (72 * Math.PI) / 180;
const EXIT_SCALE = 0.45;
const ENTER_SCALE = 0.5;
const DIP_EXTRA = 8;

/** Object order in the slot table: 2 icons, 5 cards, 6 tiles. */
export const OBJ_COUNT = 2 + 5 + 6;
export const STRIDE = 13; // tx, ty, rot, rotX, scale, alpha, hw, hh, contentAlpha, rotY, dispersion, exit q, enter q
export const G = OBJ_COUNT * STRIDE; // globals start here
/** Globals: aurora phase, page weights x3, flare, core x/y, sheet top, notch dip, dots y, dot pos,
 * dot stretch, button bottom, page blend, from page, to page. */
export const GL = {
  phase: G + 0,
  w0: G + 1,
  w1: G + 2,
  w2: G + 3,
  flare: G + 4,
  coreX: G + 5,
  coreY: G + 6,
  sheetTop: G + 7,
  dip: G + 8,
  dotsY: G + 9,
  dotPos: G + 10,
  dotStretch: G + 11,
  buttonBottom: G + 12,
  sheetMix: G + 13,
  from: G + 14,
  to: G + 15,
  u: G + 16,
  lightAngle: G + 17,
  lightIntensity: G + 18,
  fingerX: G + 19,
  fingerY: G + 20,
  touch: G + 21,
  touched: G + 22,
  tiltX: G + 23,
  tiltY: G + 24,
  /** autoplay timer: the current pill's dimness, the fill's width 0..1 and its opacity */
  dotDim: G + 25,
  dotFill: G + 26,
  dotFillA: G + 27,
  /** finale: progress, the button's bloom, its pop, the label's turn to the check, the sheen's sweep */
  fin: G + 28,
  finGlow: G + 29,
  finPop: G + 30,
  finCheck: G + 31,
  finSheen: G + 32,
  /** where the finale's light goes (the button's centre), 1 while it streams, the burst's progress */
  sinkX: G + 33,
  sinkY: G + 34,
  sinkMix: G + 35,
  burst: G + 36,
} as const;
export const SCENE_SIZE = G + 37;

/**
 * What the screen is given besides the page position:
 * the finger (drag velocity pt/s, where it is, down 0..1, dragging 0..1 springy), the phone's tilt
 * (-1..1 each way) and whether to drift instead (no gyroscope), the autoplay timer (how far the rest
 * is, 0..1, and how visible the timer is) and the finale's progress.
 */
export type Input = {
  vx: number;
  x: number;
  y: number;
  down: number;
  drag: number;
  tiltX: number;
  tiltY: number;
  drift: number;
  holdQ: number;
  autoVis: number;
  fin: number;
};

// ------------------------------------------------------------------ curves (worklets, helpers first)
export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

export function smooth(e0: number, e1: number, x: number) {
  'worklet';
  const t = clamp01((x - e0) / (e1 - e0));
  return t * t * (3 - 2 * t);
}

/** Step response of a damped spring over q = 0..1, landing exactly on 1 at q = 1. */
export function spring(q: number, zeta: number, omega: number) {
  'worklet';
  if (q <= 0) return 0;
  if (q >= 1) return 1;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const e = Math.exp(-zeta * omega * q);
  const s = 1 - e * (Math.cos(wd * q) + ((zeta * omega) / wd) * Math.sin(wd * q));
  const q8 = q * q * q * q * q * q * q * q;
  return s * (1 - q8) + q8;
}

/** Back-in: a small lift first, then a falling acceleration. */
function backIn(q: number) {
  'worklet';
  const c = 1.4;
  return q * q * ((c + 1) * q - c);
}

/** The loop clock (s) to the page position. */
export function posAt(t: number) {
  'worklet';
  const tt = ((t % LOOP) + LOOP) % LOOP;
  const k = Math.floor(tt / SEG);
  const tau = tt - k * SEG;
  return tau < HOLD ? k : k + (tau - HOLD) / TRANS;
}

/** A page position to the loop clock that shows it (holds map to their start). */
export function clockAt(pos: number) {
  'worklet';
  const p = ((pos % PAGES) + PAGES) % PAGES;
  const k = Math.floor(p + 1e-6);
  const u = p - k;
  return u < 1e-4 ? k * SEG : k * SEG + HOLD + u * TRANS;
}

export function wrapPos(p: number) {
  'worklet';
  return ((p % PAGES) + PAGES) % PAGES;
}

// ------------------------------------------------------------------ objects
type Rest = {
  x: number;
  y: number;
  rot: number;
  alpha: number;
  w: number;
  h: number;
};

function restOf(index: number, L: Layout, out: Rest) {
  'worklet';
  const px = L.S;
  if (index < 2) {
    const ic = ICONS[index];
    out.x = ic.cx * px;
    out.y = ic.cy * px;
    out.rot = (ic.rot * Math.PI) / 180;
    out.alpha = 1;
    out.w = 0;
    out.h = 0;
  } else if (index < 7) {
    const c = CARDS[index - 2];
    out.x = c.cx * px;
    out.y = c.cy * px;
    out.rot = (c.rot * Math.PI) / 180;
    out.alpha = c.alpha;
    out.w = CARD.w * px;
    out.h = CARD.h * px;
  } else {
    const t = TILES[index - 7];
    out.x = t.cx * px;
    out.y = t.cy * px;
    out.rot = (t.rot * Math.PI) / 180;
    out.alpha = t.alpha;
    out.w = TILE.w * px;
    out.h = TILE.h * px;
  }
}

/** Page of an object, its rank on the way out (nearest the notch first) and on the way in (back first). */
function pageOf(index: number) {
  'worklet';
  return index < 2 ? 0 : index < 7 ? 1 : 2;
}
function exitRank(index: number) {
  'worklet';
  if (index < 2) return index === 0 ? 0 : 1;
  if (index < 7) return 6 - index; // card 5 (index 6) first
  return index >= 11 ? 12 - index : index >= 9 ? 2 + (10 - index) : 4 + (8 - index);
}
function enterRank(index: number) {
  'worklet';
  if (index < 2) return index === 1 ? 0 : 1;
  if (index < 7) return index - 2;
  return index - 7;
}
/** How near the glass sits to the eye (1 = nearest): it moves most with the tilt. */
function depthOf(index: number) {
  'worklet';
  if (index < 2) return index === 1 ? 1 : 0.72;
  if (index < 7) return 1 - CARDS[index - 2].depth * 0.6;
  return 0.6 + 0.2 * Math.floor((index - 7) / 2);
}
function countOf(page: number) {
  'worklet';
  return page === 0 ? 2 : page === 1 ? 5 : 6;
}

function bob(index: number, amb: number, L: Layout) {
  'worklet';
  const a = index < 2 ? 3.2 : index < 7 ? 1.4 : 1.8;
  const ph = index * 1.7;
  return a * L.S * (Math.sin((2 * Math.PI * 3 * amb) / LOOP + ph) - Math.sin(ph));
}
function sway(index: number, amb: number) {
  'worklet';
  const a = index < 2 ? 1.6 : 0.7;
  return ((a * Math.PI) / 180) * (Math.sin((2 * Math.PI * 2 * amb) / LOOP + index * 2.3) - Math.sin(index * 2.3));
}

/**
 * Fill `out` with the whole scene for page position `pos` and ambient time `amb` (s, loop clock).
 * `reduced`: Reduce Motion, where a page change is a cross-fade with no travel.
 */
export function computeScene(pos: number, amb: number, L: Layout, reduced: boolean, finger: Input, out: number[]) {
  'worklet';
  const p = wrapPos(pos);
  let from = Math.floor(p + 1e-6);
  let u = p - from;
  if (u < 1e-5) u = 0;
  if (from >= PAGES) from = 0;
  const to = (from + 1) % PAGES;

  // sheet and globals first: objects need the notch
  const sheetQ = reduced ? smooth(0, 1, u) : spring(clamp01((u - SHEET_START) / SHEET_DUR), 0.78, 10);
  const sheetTop = mix(L.sheetTop[from], L.sheetTop[to], sheetQ);
  const flareRaw = Math.exp(-((u - FLARE_AT) / FLARE_W) * ((u - FLARE_AT) / FLARE_W));
  const flare = reduced ? 0 : flareRaw * smooth(0, 0.08, u) * (1 - smooth(0.92, 1, u));
  const blend = smooth(0.22, 0.78, u);
  // a dragging finger pulls the notch (and the light in it) along the edge
  const reach = L.W / 2 - L.notch.half - L.sheetCorner - 6;
  const pull = Math.max(-reach, Math.min(reach, (finger.x - L.cx) * 0.6));
  const nx = reduced ? L.cx : L.cx + pull * finger.drag;
  const ny = sheetTop + L.notch.depth + 18 * L.S;
  // the phone's tilt, or the drift that stands in for it
  const tX = reduced ? 0 : finger.tiltX + finger.drift * 0.45 * Math.sin((2 * Math.PI * amb) / LOOP);
  const tY = reduced ? 0 : finger.tiltY + finger.drift * 0.3 * Math.sin((4 * Math.PI * amb) / LOOP);
  // the finale only exists at rest on page 3
  const finOn = from === 2 && u === 0 && finger.fin > 0 ? 1 : 0;

  const r: Rest = { x: 0, y: 0, rot: 0, alpha: 0, w: 0, h: 0 };
  // the object under a resting finger (the topmost of the page on screen), which lifts toward it
  let touched = -1;
  if (finger.down > 0.001 && u < 0.04) {
    for (let i = 0; i < OBJ_COUNT; i++) {
      if (pageOf(i) !== from) continue;
      restOf(i, L, r);
      const hw0 = r.w > 0 ? r.w / 2 : 52 * L.S * 0.5;
      const hh0 = r.h > 0 ? r.h / 2 : 52 * L.S * 0.5;
      const c = Math.cos(-r.rot);
      const sn = Math.sin(-r.rot);
      const dx = finger.x - r.x;
      const dy = finger.y - r.y;
      const lx = dx * c - dy * sn;
      const ly = dx * sn + dy * c;
      if (Math.abs(lx) <= hw0 && Math.abs(ly) <= hh0) touched = i;
    }
  }
  const seed = SEED * L.S;
  for (let i = 0; i < OBJ_COUNT; i++) {
    const o = i * STRIDE;
    restOf(i, L, r);
    const page = pageOf(i);
    const by = bob(i, amb, L);
    const sw = sway(i, amb);
    let tx = r.x;
    let ty = r.y + by;
    let rot = r.rot + sw;
    let rotX = 0;
    let scale = 1;
    let alpha = r.alpha;
    let hw = r.w / 2;
    let hh = r.h / 2;
    let content = 1;
    let unfold = 1;
    let exitQ = 0;
    let enterQ = 0;
    if (page === from && u > 0) {
      if (reduced) {
        alpha = r.alpha * (1 - smooth(0, 0.45, u));
        content = alpha;
      } else {
        const q = clamp01((u - EXIT_START - EXIT_STAGGER * exitRank(i)) / EXIT_DUR);
        exitQ = q;
        const path = backIn(q);
        const down = path > 0 ? path : 0;
        tx = mix(r.x, nx, path);
        ty = mix(r.y + by, ny, path);
        rot = (r.rot + sw) * (1 - 0.7 * down);
        rotX = EXIT_ROTX * down;
        scale = mix(1, EXIT_SCALE, down);
        alpha = r.alpha * (1 - smooth(0.78, 1, q));
        const fold = smooth(0.05, 0.6, q);
        unfold = 1 - fold;
        hw = mix(r.w / 2, seed / 2, fold);
        hh = mix(r.h / 2, seed / 2, fold);
        content = 1 - smooth(0, 0.3, q);
      }
    } else if (page === to && u > 0) {
      if (reduced) {
        alpha = r.alpha * smooth(0.45, 1, u);
        content = alpha;
      } else {
        const s0 = ENTER_START + ENTER_STAGGER * enterRank(i);
        const d = Math.min(ENTER_DUR, 1 - s0);
        const q = clamp01((u - s0) / d);
        enterQ = q;
        const m = spring(q, 0.74, 11);
        const arc = Math.sin(Math.PI * clamp01(m));
        tx = mix(nx, r.x, m) + (r.x - nx) * 0.18 * arc;
        ty = mix(ny, r.y + by, m);
        rot = (r.rot + sw) * spring(q, 0.7, 11);
        rotX = ENTER_ROTX * (1 - spring(q, 0.52, 12));
        scale = mix(ENTER_SCALE, 1, spring(q, 0.7, 11));
        alpha = r.alpha * smooth(0, 0.12, q);
        const f = spring(clamp01((q - 0.08) / 0.92), 0.78, 10);
        unfold = f;
        hw = mix(seed / 2, r.w / 2, f);
        hh = mix(seed / 2, r.h / 2, f);
        content = smooth(0.42, 0.85, q);
      }
    } else if (page !== from) {
      alpha = 0;
      content = 0;
      tx = nx;
      ty = ny;
      scale = ENTER_SCALE;
      hw = seed / 2;
      hh = seed / 2;
      unfold = 0;
    }
    if (i === touched && !reduced) {
      // pressed: it rises toward the finger and tips toward where it is held
      const lift = finger.down * (1 - smooth(0, 0.04, u));
      const hwT = hw > 0 ? hw : 40 * L.S;
      const hhT = hh > 0 ? hh : 40 * L.S;
      // the edge under the finger dips (like pressing one side of a glass plate)
      rotX += (((clamp01(((finger.y - ty) / hhT) * 0.5 + 0.5) - 0.5) * 2 * 9 * Math.PI) / 180) * lift;
      scale *= 1 + 0.07 * lift;
      ty -= 9 * L.S * lift;
      out[o + 9] = (((clamp01(((finger.x - tx) / hwT) * 0.5 + 0.5) - 0.5) * -2 * 11 * Math.PI) / 180) * lift;
    } else if (touched >= 0 && pageOf(i) === from && !reduced) {
      // the rest of the page steps back a little, so the held one pops
      const back = finger.down * (1 - smooth(0, 0.04, u));
      scale *= 1 - 0.025 * back;
      alpha *= 1 - 0.3 * back;
      content *= 1 - 0.2 * back;
    }
    // finale: each tile gives up its light to the button, nearest first
    let drain = 0;
    if (finOn > 0 && page === 2 && !reduced) {
      const q = clamp01((finger.fin - 0.05 * (12 - i)) / 0.42);
      exitQ = q;
      drain = Math.sin(Math.PI * q);
      scale *= 1 - 0.035 * drain;
      content *= 1 - 0.45 * drain;
      alpha *= 1 - 0.12 * drain;
      ty += 4 * L.S * drain;
    }
    // tilt: nearer glass slides further over the aurora and turns toward the eye
    const dp = depthOf(i);
    tx += tX * 7 * L.S * dp;
    ty += tY * 5 * L.S * dp;
    rotX += ((-tY * 7 * Math.PI) / 180) * dp;
    out[o] = tx;
    out[o + 1] = ty;
    out[o + 2] = rot;
    out[o + 3] = rotX;
    out[o + 4] = scale;
    out[o + 5] = alpha;
    out[o + 6] = hw;
    out[o + 7] = hh;
    out[o + 8] = content;
    // the finger's drag leans the glass; the deeper ones lean less
    const lean = reduced ? 0 : clamp01(Math.abs(finger.vx) / 1800) * (finger.vx < 0 ? -1 : 1);
    if (i !== touched) out[o + 9] = (lean * 16 * Math.PI) / 180;
    out[o + 9] += ((tX * 9 * Math.PI) / 180) * dp;
    // a glass that is still unfolding splits light at its rim (and one giving up its light, too)
    const stretch = 1 - unfold;
    out[o + 10] = reduced ? 4 : 4 + 46 * stretch * (1 - stretch) * 4 * (u > 0 ? 1 : 0) + 36 * drain;
    out[o + 11] = exitQ;
    out[o + 12] = enterQ;
  }

  out[GL.phase] = (2 * Math.PI * amb) / LOOP;
  out[GL.w0] = from === 0 ? 1 - blend : to === 0 ? blend : 0;
  out[GL.w1] = from === 1 ? 1 - blend : to === 1 ? blend : 0;
  out[GL.w2] = from === 2 ? 1 - blend : to === 2 ? blend : 0;
  out[GL.flare] = flare;
  out[GL.coreX] = nx;
  out[GL.coreY] = sheetTop + L.coreDy;
  out[GL.sheetTop] = sheetTop;
  const speed = clamp01(Math.abs(finger.vx) / 1500);
  out[GL.dip] = L.notch.depth + DIP_EXTRA * L.S * flare + (reduced ? 0 : (4 + 10 * speed) * L.S * Math.max(0, finger.drag));
  out[GL.dotsY] = sheetTop + mix(L.dots.centre[from], L.dots.centre[to], sheetQ);
  const dq = reduced ? smooth(0, 1, u) : clamp01((u - 0.3) / 0.45);
  const dm = reduced ? dq : spring(dq, 0.8, 11);
  const toIndex = to === 0 && from === PAGES - 1 ? 0 : to;
  out[GL.dotPos] = mix(from, toIndex, dm);
  out[GL.dotStretch] = reduced ? 0 : Math.sin(Math.PI * dq);
  // the timer: while a page rests its pill is dim and fills; in a page change the full pill flows on
  // and dims as it lands, so the fill is continuous across the whole loop
  const av = reduced ? 0 : finger.autoVis;
  out[GL.dotDim] = av * (u > 0 ? dm : 1);
  out[GL.dotFill] = u > 0 ? 1 : finger.holdQ;
  out[GL.dotFillA] = u > 0 ? 0 : av * smooth(0, 0.06, finger.holdQ);
  const buttonBottom = mix(L.button.bottom[from], L.button.bottom[to], sheetQ);
  out[GL.buttonBottom] = buttonBottom;
  out[GL.sheetMix] = sheetQ;
  out[GL.from] = from;
  out[GL.to] = to;
  out[GL.u] = u;
  // the glass is lit from the notch; a finger on the screen pulls the light round to it
  const toward = (Math.atan2(finger.x - L.cx, -(finger.y - L.H * 0.3)) * 180) / Math.PI;
  const delta = ((((toward - 180 + 540) % 360) + 360) % 360) - 180;
  let angle = 180 + delta * finger.down + 40 * tX;
  if (angle > 180) angle -= 360;
  out[GL.lightAngle] = angle;
  out[GL.lightIntensity] = 42 + 30 * finger.down + 20 * flare;
  out[GL.fingerX] = finger.x;
  out[GL.fingerY] = finger.y;
  out[GL.touch] = finger.down;
  out[GL.touched] = touched;
  out[GL.tiltX] = tX;
  out[GL.tiltY] = tY;
  // finale; its label and bloom hold through the page change that follows and fade with it
  const page3 = from === 2 && finger.fin > 0;
  const fin = page3 ? finger.fin : 0;
  const after = 1 - smooth(0, 0.3, u);
  out[GL.fin] = fin;
  out[GL.finGlow] = reduced ? 0 : (Math.exp(-((fin - 0.5) / 0.13) * ((fin - 0.5) / 0.13)) + 0.3 * smooth(0.5, 0.75, fin)) * after;
  out[GL.finPop] = reduced || !page3 ? 0 : 0.055 * Math.exp(-((fin - 0.52) / 0.07) * ((fin - 0.52) / 0.07));
  out[GL.finCheck] = smooth(0.46, 0.6, fin) * after;
  out[GL.finSheen] = reduced ? 0 : clamp01((fin - 0.5) / 0.3) * (u > 0 ? 0 : 1);
  out[GL.sinkX] = L.cx;
  out[GL.sinkY] = L.H - buttonBottom - L.button.h / 2;
  out[GL.sinkMix] = finOn > 0 && !reduced ? 1 : 0;
  out[GL.burst] = finOn > 0 && !reduced ? clamp01((fin - 0.5) / 0.45) : 0;
}

/** How far a drag of `dx` points moves the page position. */
export function dragToPos(dx: number, width: number) {
  'worklet';
  return -dx / (width * 0.85);
}

export { countOf, pageOf };
