/**
 * The paper: the "invite them" button folded into a dart, flown, and a new sheet unfolded.
 *
 * The button is a sheet in its own flat coordinates (x 0..W, y 0..H, origin at its top-left).
 * Each visible piece of paper (a "face") is a flat polygon in those coordinates carried into 3D
 * by a chain of rigid folds (rotations about crease lines), then placed on screen and projected
 * with a pinhole camera. A planar face under a perspective projection is a 3×3 homography, so
 * Skia draws every face as its flat polygon (with the button's own gradient) under one matrix:
 * the gradient stays printed on the paper, and nothing is tessellated.
 *
 * Faces (indices are fixed; the component draws them in this order: 1, 0, 4, 5, 2, 3):
 *   0  left half   (book fold, and the re-formed sheet)      1  right half
 *   2  top wing    3  bottom wing   (the dart: a triangle, nose to the right, split by the keel)
 *   4  top flap    5  bottom flap   (the long corners that fold back behind the dart)
 * After the book fold the left half lies on the right one; only its top layer is drawn.
 *
 * Everything here is a pure function of the clock, worklet-safe, and free of callbacks so the
 * React Compiler cannot hoist anything off the UI runtime.
 */
import {
  CONTRAIL,
  ease,
  EASE_FOLD,
  EASE_HANG,
  EASE_OUT,
  FLIGHT,
  FOLD,
  INVITE,
  LIGHT,
  PATH,
  pathAt,
  REFORM,
  seg,
  SPRING_UI,
  springStep,
} from './motion';

export const FACES = 6;

/** A point as a plain object: Skia reads {x, y} as a point, so no Skia object is made per frame. */
export type Pt = { x: number; y: number };

/** 3D affine map, row-major: [m00, m01, m02, m10, m11, m12, m20, m21, m22, t0, t1, t2]. */
type Aff = number[];

function affMul(a: Aff, b: Aff): Aff {
  'worklet';
  const r: number[] = [];
  for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3; j++) {
      r.push(a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]);
    }
  }
  for (let i = 0; i < 3; i++) {
    r.push(a[i * 3] * b[9] + a[i * 3 + 1] * b[10] + a[i * 3 + 2] * b[11] + a[9 + i]);
  }
  return r;
}

/** Rotation by `th` (rad, right-handed) about the axis through (px, py, pz) along unit (ux, uy, uz). */
function rotAbout(px: number, py: number, pz: number, ux: number, uy: number, uz: number, th: number): Aff {
  'worklet';
  const c = Math.cos(th);
  const s = Math.sin(th);
  const C = 1 - c;
  const m = [
    c + ux * ux * C,
    ux * uy * C - uz * s,
    ux * uz * C + uy * s,
    uy * ux * C + uz * s,
    c + uy * uy * C,
    uy * uz * C - ux * s,
    uz * ux * C - uy * s,
    uz * uy * C + ux * s,
    c + uz * uz * C,
  ];
  return [
    ...m,
    px - (m[0] * px + m[1] * py + m[2] * pz),
    py - (m[3] * px + m[4] * py + m[5] * pz),
    pz - (m[6] * px + m[7] * py + m[8] * pz),
  ];
}

function affMove(x: number, y: number, z: number): Aff {
  'worklet';
  return [1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z];
}

/** Uniform scale `k` about (x, y) in the screen plane. */
function affScaleAbout(k: number, x: number, y: number): Aff {
  'worklet';
  return [k, 0, 0, 0, k, 0, 0, 0, k, x - k * x, y - k * y, 0];
}

/** Perspective projection of the face plane (camera at distance d in front of (cx, cy)). */
function homography(a: Aff, cx: number, cy: number, d: number): number[] {
  'worklet';
  return [
    a[0] - (cx * a[6]) / d,
    a[1] - (cx * a[7]) / d,
    a[9] - (cx * a[11]) / d,
    a[3] - (cy * a[6]) / d,
    a[4] - (cy * a[7]) / d,
    a[10] - (cy * a[11]) / d,
    -a[6] / d,
    -a[7] / d,
    1 - a[11] / d,
  ];
}

/** The face's shadow on the card (z = 0), cast along the light. */
function shadowMatrix(a: Aff): number[] {
  'worklet';
  const sx = LIGHT.shadowDir[0];
  const sy = LIGHT.shadowDir[1];
  return [a[0] + sx * a[6], a[1] + sx * a[7], a[9] + sx * a[11], a[3] + sy * a[6], a[4] + sy * a[7], a[10] + sy * a[11], 0, 0, 1];
}

/** Lighting of a face from its normal: [dark overlay, bright overlay]; a flat face gets [0, 0]. */
function shade(a: Aff): [number, number] {
  'worklet';
  let nx = a[2];
  let ny = a[5];
  let nz = a[8];
  const len = Math.hypot(nx, ny, nz) || 1;
  nx /= len;
  ny /= len;
  nz /= len;
  if (nz < 0) {
    nx = -nx;
    ny = -ny;
    nz = -nz;
  }
  const l = LIGHT.dir;
  const ll = Math.hypot(l[0], l[1], l[2]);
  const d = (nx * l[0] + ny * l[1] + nz * l[2]) / ll - l[2] / ll;
  return [Math.max(0, -d) * LIGHT.dark, Math.max(0, d) * LIGHT.bright];
}

/** Scale of a pressed button at local time τ: press-in, hold, spring back after the release. */
export function pressScale(tau: number, pressIn: number, release: number, to: number) {
  'worklet';
  if (tau <= 0) return 1;
  if (tau < pressIn) return 1 - (1 - to) * ease(EASE_OUT, tau / pressIn);
  if (tau < release) return to;
  return to + (1 - to) * springStep(tau - release, SPRING_UI.omega, SPRING_UI.zeta);
}

/** Corner radius of the button's sheet (React Native and Skia read the same value). */
export function sheetRadius(tau: number, pill: number) {
  'worklet';
  const t = tau - INVITE.release;
  if (tau <= INVITE.release) return pill;
  if (t < REFORM.radiusBack[0]) return pill + (FOLD.paperRadius - pill) * ease(EASE_OUT, seg(t, 0, FOLD.radiusDur));
  return FOLD.paperRadius + (pill - FOLD.paperRadius) * ease(EASE_OUT, seg(t, REFORM.radiusBack[0], REFORM.radiusBack[1]));
}

/** Label + icon of the button: [opacity, scale]. */
export function labelState(tau: number): [number, number] {
  'worklet';
  const t = tau - INVITE.release;
  if (tau <= INVITE.release) return [1, 1];
  const out = ease(EASE_OUT, seg(t, 0, FOLD.labelOut));
  const back = ease(EASE_OUT, seg(t, REFORM.labelIn[0], REFORM.labelIn[1]));
  const v = t < REFORM.labelIn[0] ? 1 - out : back;
  return [v, FOLD.labelScale + (1 - FOLD.labelScale) * v];
}

/** Whether the React Native button (pill + label) is shown. */
export function buttonShown(tau: number) {
  'worklet';
  const t = tau - INVITE.release;
  return tau <= INVITE.release || t < FOLD.rnOff || t >= REFORM.rnOn;
}

/**
 * Does the overlay draw any paper at τ (the sheet, the dart, its shadow or its contrail)? Outside
 * this window the overlay pins its paper clock to 0, so the canvas does not redraw while the clock
 * runs on through the press and the rest after the re-form.
 */
export function paperOn(tau: number) {
  'worklet';
  const t = tau - INVITE.release;
  return tau > INVITE.release && t >= FOLD.skiaOn && t < Math.max(REFORM.skiaOff, FOLD.launch + FLIGHT.duration + CONTRAIL.fadeOut);
}

export type Geo = {
  W: number;
  H: number;
  left: number;
  top: number;
  camX: number;
  camY: number;
  /** Screen offset of the flight path (pin pt → screen), so it starts at the dart's centre. */
  offX: number;
  offY: number;
};

export type PaperFrame = {
  mats: number[][];
  shadows: number[][];
  vis: number[];
  dark: number[];
  bright: number[];
  radius: number;
  /** Face 0 is the whole pill (no crease yet / any more). */
  flat: boolean;
  shadowOpacity: number;
  shadowBlur: number;
  trailOpacity: number;
  /** Ribbon edge points, two per sample (tail → head: a triangle strip), plain {x, y} (no Skia objects per
   *  frame); empty while there is no contrail. */
  trail: Pt[];
};

const HIDDEN = [0, 0, 0, 0, 0, 0, 0, 0, 1];

/** The paper at `tau` seconds after the press of "invite them". */
export function paperFrame(tau: number, g: Geo, pill: number): PaperFrame {
  'worklet';
  const t = tau - INVITE.release;
  const W = g.W;
  const H = g.H;
  const c = H / 2;
  const d = FLIGHT.camera;
  const mats: number[][] = [];
  const shadows: number[][] = [];
  const vis: number[] = [];
  const dark: number[] = [];
  const bright: number[] = [];
  for (let i = 0; i < FACES; i++) {
    mats.push(HIDDEN);
    shadows.push(HIDDEN);
    vis.push(0);
    dark.push(0);
    bright.push(0);
  }
  const K = CONTRAIL.samples;
  // Points are only made while the contrail is out; at rest the frame allocates almost nothing.
  const trail: Pt[] = [];
  const frame: PaperFrame = {
    mats,
    shadows,
    vis,
    dark,
    bright,
    radius: sheetRadius(tau, pill),
    flat: false,
    shadowOpacity: LIGHT.shadowOpacity,
    shadowBlur: LIGHT.shadowBlur,
    trailOpacity: 0,
    trail,
  };
  if (tau <= INVITE.release || t < FOLD.skiaOn) return frame;

  const put = (i: number, a: Aff, o: number) => {
    'worklet';
    mats[i] = homography(a, g.camX, g.camY, d);
    shadows[i] = shadowMatrix(a);
    vis[i] = o;
    const sh = shade(a);
    dark[i] = sh[0];
    bright[i] = sh[1];
  };

  const cx = g.left + W / 2;
  const cy = g.top + c;
  const flightEnd = FOLD.launch + FLIGHT.duration;

  /* ----- the sheet before and during the book fold (faces 0, 1) ----- */
  if (t < FOLD.book[1]) {
    const k = pressScale(tau, INVITE.pressIn, INVITE.release, INVITE.pressScale);
    const place = affMul(affScaleAbout(k, cx, cy), affMove(g.left, g.top, 0));
    const th = Math.PI * ease(EASE_FOLD, seg(t, FOLD.book[0], FOLD.book[1]));
    frame.flat = th <= 0;
    put(0, affMul(place, rotAbout(W / 2, 0, 0, 0, 1, 0, th)), 1);
    if (!frame.flat) put(1, place, 1);
  }

  /* ----- the dart: flaps, keel, launch and flight (faces 2…5) ----- */
  let z = 0;
  if (t >= FOLD.book[1] && t < flightEnd) {
    // Wings: close up along the keel, then pop open on a spring.
    let beta = FOLD.keelAngle * ease(EASE_FOLD, seg(t, FOLD.keelClose[0], FOLD.keelClose[1]));
    if (t > FOLD.keelClose[1]) {
      beta = FOLD.keelAngle + (FOLD.wingAngle - FOLD.keelAngle) * springStep(t - FOLD.keelClose[1], FOLD.wingSpring.omega, FOLD.wingSpring.zeta);
    }
    beta = (beta * Math.PI) / 180;
    const fa = (FOLD.flapAngle * Math.PI) / 180;
    const ft = fa * ease(EASE_FOLD, seg(t, FOLD.flapTop[0], FOLD.flapTop[1]));
    const fb = fa * ease(EASE_FOLD, seg(t, FOLD.flapBot[0], FOLD.flapBot[1]));
    const fadeFrom = ((FOLD.flapAngle - FOLD.flapFade) * Math.PI) / 180;
    const ot = 1 - seg(ft, fadeFrom, fa);
    const ob = 1 - seg(fb, fadeFrom, fa);

    // Placement: on the button until the launch, then along the path.
    let px = g.left + (3 * W) / 4;
    let py = g.top + c;
    let yaw = 0;
    let m = 1;
    let roll = 0;
    let k = pressScale(tau, INVITE.pressIn, INVITE.release, INVITE.pressScale);
    if (t >= FOLD.launch) {
      const tf = t - FOLD.launch;
      const p = ease(EASE_HANG, tf / FLIGHT.duration);
      const at = pathAt(PATH.xs, PATH.ys, PATH.ss, PATH.hs, PATH.bank, p * PATH.length);
      px = at.x + g.offX;
      py = at.y + g.offY;
      z = FLIGHT.lift * Math.sin(Math.PI * p);
      yaw = at.h * ease(EASE_OUT, seg(tf, 0, FLIGHT.yawDur));
      m = 1 - (1 - FLIGHT.scale) * ease(EASE_OUT, seg(tf, 0, FLIGHT.scaleDur));
      roll = at.b * ease(EASE_OUT, seg(tf, 0, FLIGHT.bankIn));
      k = 1;
    }
    const cs = Math.cos(yaw);
    const sn = Math.sin(yaw);
    // T(P) · Rz(yaw) · S(m) · T(−dart centre)
    const dcx = (3 * W) / 4;
    const dcy = c;
    const fly: Aff = [
      cs * m,
      -sn * m,
      0,
      sn * m,
      cs * m,
      0,
      0,
      0,
      m,
      px - m * (cs * dcx - sn * dcy),
      py - m * (sn * dcx + cs * dcy),
      z,
    ];
    const place = affMul(affScaleAbout(k, cx, cy), fly);
    const book = rotAbout(W / 2, 0, 0, 0, 1, 0, Math.PI);
    put(2, affMul(affMul(place, rotAbout(0, c, 0, 1, 0, 0, -beta + roll)), book), 1);
    put(3, affMul(affMul(place, rotAbout(0, c, 0, 1, 0, 0, beta + roll)), book), 1);
    // Flaps: creases from the tail corners to the nose, folding away from the viewer.
    const ln = Math.hypot(W / 2, c);
    if (ot > 0) put(4, affMul(affMul(place, rotAbout(W / 2, 0, 0, W / 2 / ln, c / ln, 0, ft)), book), ot);
    if (ob > 0) put(5, affMul(affMul(place, rotAbout(W / 2, H, 0, W / 2 / ln, -c / ln, 0, -fb)), book), ob);
  }

  /* ----- the new sheet: appears folded, unfolds into the button (faces 0, 1) ----- */
  if (t >= REFORM.appear && t < REFORM.skiaOff) {
    const a = ease(EASE_OUT, seg(t, REFORM.appear, REFORM.appear + REFORM.appearDur));
    const k = REFORM.appearScale + (1 - REFORM.appearScale) * a;
    const place = affMul(affScaleAbout(k, g.left + (3 * W) / 4, cy), affMove(g.left, g.top, 0));
    let th = Math.PI;
    if (t > REFORM.unfold) th = Math.PI * Math.max(0, 1 - springStep(t - REFORM.unfold, REFORM.spring.omega, REFORM.spring.zeta));
    if (t >= REFORM.rnOn) th = 0;
    frame.flat = th <= 0;
    put(0, affMul(place, rotAbout(W / 2, 0, 0, 0, 1, 0, th)), a);
    if (!frame.flat) put(1, place, a);
  }

  /* ----- shadow: softer and lighter as the dart climbs ----- */
  frame.shadowOpacity = LIGHT.shadowOpacity * (1 - (0.5 * z) / FLIGHT.lift);
  frame.shadowBlur = LIGHT.shadowBlur + LIGHT.shadowBlurPerPt * z;

  /* ----- contrail: a ribbon along the path behind the dart, widening and drifting with age ----- */
  if (t >= FOLD.launch && t < flightEnd + CONTRAIL.fadeOut) {
    const tf = t - FOLD.launch;
    const p = ease(EASE_HANG, Math.min(tf / FLIGHT.duration, 1));
    const m = 1 - (1 - FLIGHT.scale) * ease(EASE_OUT, seg(tf, 0, FLIGHT.scaleDur));
    const head = p * PATH.length - (W / 4) * m * CONTRAIL.headGap;
    const tail = Math.max(0, head - CONTRAIL.length);
    if (head > 1) {
      frame.trailOpacity = seg(tf, 0, CONTRAIL.fadeIn) * (1 - seg(tf, FLIGHT.duration, FLIGHT.duration + CONTRAIL.fadeOut));
      for (let i = 0; i < K; i++) {
        const u = i / (K - 1); // 0 tail → 1 head
        const s = tail + (head - tail) * u;
        const at = pathAt(PATH.xs, PATH.ys, PATH.ss, PATH.hs, PATH.bank, s);
        const zi = FLIGHT.lift * Math.sin((Math.PI * s) / PATH.length);
        const f = d / (d - zi);
        const age = 1 - u;
        const w = (CONTRAIL.widthHead + (CONTRAIL.widthTail - CONTRAIL.widthHead) * age) * f;
        const drift = CONTRAIL.wobble * age * Math.sin(s * 0.07 + tf * 2.2);
        const nx = -Math.sin(at.h);
        const ny = Math.cos(at.h);
        const x = g.camX + (at.x + g.offX + nx * drift - g.camX) * f;
        const y = g.camY + (at.y + g.offY + ny * drift - g.camY) * f;
        trail.push({ x: x + (nx * w) / 2, y: y + (ny * w) / 2 }, { x: x - (nx * w) / 2, y: y - (ny * w) / 2 });
      }
    }
  }
  return frame;
}
