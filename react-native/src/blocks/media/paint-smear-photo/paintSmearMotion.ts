// The paint's drag field: up to TRAIL points (card points), each carrying a drag vector (pt).
// A plain object on the UI thread, read by the shader. In autoPlay the points are rebuilt every
// frame from the loop time (three curved brush strokes, then the paint flowing back); under a
// finger new points are added and, when the trail is full, the two oldest merge into one so
// nothing already painted pops away.

export const LOOP = 9;
export const TRAIL = 24;
/** Brush radius (Gaussian sigma, pt) for the S / M / L chips. */
export const BRUSHES = [18, 26, 36];
// The script (s): three strokes, a hold, then the paint flows back.
const STROKES = [
  { t: [1.4, 2.4], from: [0.12, 0.3], via: [0.5, 0.08], to: [0.9, 0.36] },
  { t: [2.6, 3.6], from: [0.88, 0.6], via: [0.46, 0.78], to: [0.1, 0.5] },
  { t: [3.8, 4.8], from: [0.28, 0.94], via: [0.56, 0.6], to: [0.86, 0.86] },
];
const POINTS_PER_STROKE = TRAIL / STROKES.length;
/** Drag per scripted point (pt); a point's drag builds over PICKUP s as the brush passes. */
const SCRIPT_DRAG = 48;
const PICKUP = 0.3;
const FLOW_BACK = [6.2, 8.2];
/** A finger lays a point every STEP pt; its drag is the travel × DRAG_PER_PT, at most DRAG_MAX. */
export const STEP = 16;
const DRAG_PER_PT = 1.1;
const DRAG_MAX = 34;
const MERGED_MAX = 70;
export const IDLE_BEFORE_BACK = 2;
/** Reset / double-tap / back to rest: the paint flows back over this long (s). */
const RESET_FOR = 0.85;

export const MODE_AUTO = 0;
export const MODE_LIVE = 1;

export type Paint = {
  mode: number;
  t: number;
  now: number;
  lastTouch: number;
  x: number[];
  y: number[];
  ax: number[];
  ay: number[];
  /** Field scale (1 = as painted); resetAt ≥ 0 while it flows back. */
  flow: number;
  resetAt: number;
  /** Restart the loop when the flow-back ends (it was the ease back to rest). */
  resume: boolean;
  painting: boolean;
  lx: number;
  ly: number;
};

export function makePaint(auto: boolean): Paint {
  return {
    mode: auto ? MODE_AUTO : MODE_LIVE,
    t: 0,
    now: 0,
    lastTouch: -100,
    x: [],
    y: [],
    ax: [],
    ay: [],
    flow: 1,
    resetAt: -1,
    resume: false,
    painting: false,
    lx: 0,
    ly: 0,
  };
}

function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** A point on a quadratic curve and its unit tangent. */
function quad(a: number[], b: number[], c: number[], u: number, w: number, h: number) {
  'worklet';
  const v = 1 - u;
  const x = (v * v * a[0] + 2 * v * u * b[0] + u * u * c[0]) * w;
  const y = (v * v * a[1] + 2 * v * u * b[1] + u * u * c[1]) * h;
  const tx = (2 * v * (b[0] - a[0]) + 2 * u * (c[0] - b[0])) * w;
  const ty = (2 * v * (b[1] - a[1]) + 2 * u * (c[1] - b[1])) * h;
  const l = Math.max(Math.hypot(tx, ty), 0.0001);
  return [x, y, tx / l, ty / l];
}

function clearTrail(s: Paint) {
  'worklet';
  s.x.length = 0;
  s.y.length = 0;
  s.ax.length = 0;
  s.ay.length = 0;
}

export function scriptFrame(s: Paint, t: number, w: number, h: number) {
  'worklet';
  clearTrail(s);
  for (let k = 0; k < STROKES.length; k += 1) {
    const st = STROKES[k];
    for (let j = 0; j < POINTS_PER_STROKE; j += 1) {
      // Points are laid at even times; the brush accelerates out of the press and slows into
      // the lift, so they bunch up at both ends of the stroke.
      const tau = j / (POINTS_PER_STROKE - 1);
      const born = st.t[0] + (st.t[1] - st.t[0]) * tau;
      if (t < born) continue;
      const u = easeInOut(tau);
      const [x, y, dx, dy] = quad(st.from, st.via, st.to, u, w, h);
      const a = SCRIPT_DRAG * easeOut(seg(t, born, born + PICKUP));
      s.x.push(x);
      s.y.push(y);
      s.ax.push(dx * a);
      s.ay.push(dy * a);
    }
  }
  s.flow = 1 - easeInOut(seg(t, FLOW_BACK[0], FLOW_BACK[1]));
}

/** A finger took over: keep the field exactly as it looks, with the flow baked into the drags. */
export function takeOver(s: Paint) {
  'worklet';
  for (let i = 0; i < s.ax.length; i += 1) {
    s.ax[i] *= s.flow;
    s.ay[i] *= s.flow;
  }
  s.flow = 1;
  s.resetAt = -1;
  s.mode = MODE_LIVE;
}

/** Lays a point between the last one and (x, y), dragging the paint along the travel. */
export function lay(s: Paint, x: number, y: number) {
  'worklet';
  const dx = x - s.lx;
  const dy = y - s.ly;
  const d = Math.hypot(dx, dy);
  if (d < 0.5) return;
  if (s.x.length >= TRAIL) {
    // Merge the two oldest: a drag-weighted position, the summed drag (capped).
    const w0 = Math.hypot(s.ax[0], s.ay[0]) + 0.001;
    const w1 = Math.hypot(s.ax[1], s.ay[1]) + 0.001;
    const mx = (s.x[0] * w0 + s.x[1] * w1) / (w0 + w1);
    const my = (s.y[0] * w0 + s.y[1] * w1) / (w0 + w1);
    let ax = s.ax[0] + s.ax[1];
    let ay = s.ay[0] + s.ay[1];
    const m = Math.hypot(ax, ay);
    if (m > MERGED_MAX) {
      ax *= MERGED_MAX / m;
      ay *= MERGED_MAX / m;
    }
    s.x.splice(0, 2, mx);
    s.y.splice(0, 2, my);
    s.ax.splice(0, 2, ax);
    s.ay.splice(0, 2, ay);
  }
  const a = Math.min(d * DRAG_PER_PT, DRAG_MAX);
  s.x.push((x + s.lx) / 2);
  s.y.push((y + s.ly) / 2);
  s.ax.push((dx / d) * a);
  s.ay.push((dy / d) * a);
  s.lx = x;
  s.ly = y;
}

export function startReset(s: Paint, resume: boolean) {
  'worklet';
  if (s.resetAt >= 0) return;
  s.resetAt = s.now;
  s.resume = resume;
}

/** The flow-back of a reset; true when it has finished. */
export function resetFrame(s: Paint) {
  'worklet';
  if (s.resetAt < 0) return false;
  const k = easeInOut(Math.min((s.now - s.resetAt) / RESET_FOR, 1));
  s.flow = 1 - k;
  if (k >= 1) {
    clearTrail(s);
    s.flow = 1;
    s.resetAt = -1;
    return true;
  }
  return false;
}
