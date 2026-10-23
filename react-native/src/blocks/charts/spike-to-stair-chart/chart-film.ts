import { atArc, atU, clamp, mix, nearestTread, PIECES, STAIR_END } from './chart-curves';
import { C, G, RISERS, SHARDS, T } from './chart-theme';

// Ease a bounded phase without introducing velocity jumps at its endpoints.
export function smooth(n: number) {
  'worklet';
  const p = clamp(n);
  return p * p * (3 - 2 * p);
}
/** Solve x(t) for the specified cinematic cubic Bezier. */
export function filmEase(n: number) {
  'worklet';
  const x = clamp(n);
  let lo = 0,
    hi = 1,
    t = x;
  for (let i = 0; i < 16; i++) {
    t = (lo + hi) / 2;
    const q = 1 - t;
    const v = 3 * q * q * t * 0.77 + 3 * q * t * t * 0.175 + t * t * t;
    if (v < x) lo = t;
    else hi = t;
  }
  return 3 * (1 - t) * t * t + t * t * t;
}
// Join eased attack and decay envelopes at full intensity.
export function pulse(t: number, up: number, down: number) {
  'worklet';
  return t < 0 ? 0 : t < up ? smooth(t / up) : 1 - smooth((t - up) / down);
}
// Evaluate the damped spring analytically for arbitrary film times.
export function springProgress(t: number, damping: number, stiffness: number) {
  'worklet';
  if (t <= 0) return 0;
  const a = damping / 2,
    w = Math.sqrt(stiffness - a * a);
  return 1 - Math.exp(-a * t) * (Math.cos(w * t) + (a / w) * Math.sin(w * t));
}
// Resolve a mood channel without whitening the illuminated surfaces.
function channel(mood: number, ch: number) {
  'worklet';
  return mood < 1 ? mix(C.red[ch], C.amber[ch], clamp(mood)) : mix(C.amber[ch], C.green[ch], clamp(mood - 1));
}
// Convert numeric worklet channels to a bounded Skia colour.
export function rgba(r: number, g: number, b: number, a: number) {
  'worklet';
  return `rgba(${Math.round(r)},${Math.round(g)},${Math.round(b)},${clamp(a)})`;
}
const MOTIVATION_TIMES = [0, 0.7, 0.87, 1.04, 1.2, 1.37, 1.54, 1.7, 1.87, 2.04, 2.2];
const MOTIVATION_US = [0, 0.27, 0.31, 0.38, 0.52, 0.62, 0.78, 0.85, 0.92, 0.945, 0.957];
// Interpolate the reference timing table with monotone cubic tangents.
export function motivationU(s: number) {
  'worklet';
  const times = MOTIVATION_TIMES,
    us = MOTIVATION_US;
  // Monotone cubic Hermite interpolation keeps the table timing without velocity corners.
  let i = 0;
  while (i < times.length - 2 && s > times[i + 1]) i++;
  const dt = times[i + 1] - times[i],
    p = clamp((s - times[i]) / dt),
    p2 = p * p,
    p3 = p2 * p;
  const slope = (us[i + 1] - us[i]) / dt;
  const before = i ? Math.min(slope, (us[i] - us[i - 1]) / (times[i] - times[i - 1])) : slope;
  const after =
    i < times.length - 2 ? Math.min(slope, (us[i + 2] - us[i + 1]) / (times[i + 2] - times[i + 1])) : 0;
  return (
    (2 * p3 - 3 * p2 + 1) * us[i] +
    (p3 - 2 * p2 + p) * dt * before +
    (-2 * p3 + 3 * p2) * us[i + 1] +
    (p3 - p2) * dt * after
  );
}
// Fall under gravity, retaining tube thickness until the final landing phase.
export function piecePose(L: number, i: number) {
  'worklet';
  const p = clamp((L - T.collapse - (5 - i) * 0.045) / 0.3),
    fall = p * p;
  const bounce = p > 0.85 ? Math.sin(((p - 0.85) / 0.15) * Math.PI) * 0.06 : 0;
  const settle = smooth((p - 0.7) / 0.3);
  const flatten = mix(1, 0.03, settle);
  const piece = PIECES[i];
  const angle = (((i % 2 ? 1 : -1) * Math.PI) / 30) * Math.sin(Math.PI * p);
  const bottomOffset =
    (piece.bottom - piece.cy) * flatten * Math.cos(angle) +
    ((piece.right - piece.left) / 2) * Math.abs(Math.sin(angle));
  // Floor contact prevents an unflattened endpoint from passing through the axis.
  const y = Math.min(mix(piece.cy, G.ay, fall - bounce), G.ay - bottomOffset);
  return {
    x: PIECES[i].cx,
    y,
    sy: flatten,
    angle,
    alpha: mix(1, 0.6, settle),
  };
}
// Animate the glass burst and periodic idle drift from a fixed rest pose.
export function shardPose(L: number, i: number, still = false, life = L) {
  'worklet';
  const rest = SHARDS[i];
  let p = 1,
    alpha = 1;
  if (L >= 3.6 && L < 9.41) {
    p = 1 - smooth((L - 3.6) / 0.35);
    alpha = p;
  }
  if (L >= 9.41) {
    const t = L - 9.41;
    p = 1 - Math.exp(-t / 0.22);
    alpha = smooth(t / 0.08);
  }
  // Envelope reaches exactly one at the loop join; slow sine is exactly 12 s periodic.
  if (L > 11.4) p = mix(p, 1, smooth((L - 11.4) / 0.6));
  const bob = still ? 0 : Math.sin((life * Math.PI) / 2 + i * 1.7) * 2;
  return {
    x: mix(330, rest[0] + bob, p),
    y: mix(250, rest[1] + (still ? 0 : Math.cos((life * Math.PI) / 2 + i) * 2), p),
    angle:
      ((rest[3] + (1 - p) * (40 + 16 * i) + (still ? 0 : 3 * Math.sin((life * Math.PI) / 2 + i))) * Math.PI) /
      180,
    alpha: alpha * rest[4],
  };
}
// Drive the autonomous scrub demonstration and release spring.
export function demoU(L: number) {
  'worklet';
  if (L < 1.05) return 0.8;
  if (L < 2.3) return mix(0.8, 0.16, filmEase((L - 1.05) / 1.15));
  if (L < 3.05) return mix(0.16, 0.62, filmEase((L - 2.3) / 0.75));
  return mix(0.62, nearestTread(0.62), springProgress(L - 3.05, 22, 240));
}
// Evaluate the complete loop from a single time without renderer dependencies.
export function film(L: number, still = false) {
  'worklet';
  L = ((L % T.period) + T.period) % T.period;
  let d = 1,
    end = 1,
    mood = 2,
    area = 1,
    broken = 0,
    heat = 0,
    heatEnd = 0,
    gather = 0;
  let label = 1,
    grid = 1,
    thumb = 1;
  if (L >= 3.6 && L < 4.15) {
    end = 1 - filmEase((L - 3.6) / 0.5);
    mood = L < 4.1 ? mix(2, 1, smooth((L - 3.6) / 0.5)) : mix(1, 2, smooth((L - 4.1) / 0.05));
    label = 1 - smooth((L - 3.7) / 0.45);
    grid = 1 - smooth((L - 3.6) / 0.55);
    thumb = 1 - springProgress(L - 3.6, 20, 260);
  }
  if (L >= 4.15 && L < 8.07) {
    d = 0;
    label = 0;
    grid = 0;
    thumb = 0;
    const s = L - 4.15;
    end = atU(0, motivationU(s)).a;
    mood =
      s < 0.8
        ? 2
        : s < 1
          ? 2 * (1 - smooth((s - 0.8) / 0.2))
          : s < 1.12
            ? 0
            : s < 1.3
              ? 2 * smooth((s - 1.12) / 0.18)
              : s < 1.62
                ? 2
                : s < 1.86
                  ? 2 * (1 - smooth((s - 1.62) / 0.24))
                  : 0;
    if (L >= 6.95) thumb = springProgress(L - 6.95, 20, 260);
    if (L >= 7.05) {
      broken = smooth((L - 7.05) / 0.04);
      area = 1 - smooth((L - 7.05) / 0.18);
    }
    label = smooth((L - 7.35) / 0.65);
    grid = smooth((L - 7.35) / 0.6);
    if (L >= 7.57) {
      heat = 1;
      heatEnd = 1 - filmEase((L - 7.57) / 0.42);
      end = heatEnd;
      broken = 1 - smooth((L - 7.57) / 0.04);
    }
    if (L >= 7.99) {
      heat = 1;
      heatEnd = 0;
      gather = Math.sin(Math.PI * clamp((L - 7.99) / 0.08));
    }
  }
  if (L >= 8.07 && L < 9.59) {
    mood = L < 8.55 ? 0 : L < 8.72 ? smooth((L - 8.55) / 0.17) : 1 + smooth((L - 8.72) / 0.23);
    if (L < 9.23) {
      const t = L - 8.07;
      const distance = t < 0.15 ? 0.6 * t + 0.4 * ((t * t) / (2 * 0.15)) : t - 0.03;
      end = STAIR_END * clamp(distance / 1.13);
    } else {
      const t = clamp((L - 9.23) / 0.36);
      end = mix(STAIR_END, 1, (t + t * t) / 2);
    }
  }
  const head = atArc(d, end);
  let hx = head.x,
    hy = head.y,
    angle = head.angle;
  if (L >= 7.05 && L < 7.57) {
    const p = piecePose(L, 5);
    const dx = head.x - PIECES[5].cx,
      dy = (head.y - PIECES[5].cy) * p.sy;
    hx = p.x + dx * Math.cos(p.angle) - dy * Math.sin(p.angle);
    hy = p.y + dx * Math.sin(p.angle) + dy * Math.cos(p.angle);
    angle = mix(head.angle, Math.PI, smooth((L - 7.05) / 0.3));
  }
  if (L >= 7.57 && L < 8.07) {
    hx = G.px + G.pw * 0.957 * heatEnd;
    hy = G.ay;
    angle = mix(Math.PI, 2 * Math.PI + atArc(1, 0).angle, smooth((L - 7.99) / 0.08));
  }
  const impact = pulse(L - T.payoff, 0.025, 0.3),
    crash = pulse(L - T.crash, 0.02, 0.4);
  const r = channel(mood, 0),
    g = channel(mood, 1),
    b = channel(mood, 2);
  const coreR = mix(r + (255 - r) * 0.55, C.flash[0], impact * 0.6);
  const coreG = mix(g + (255 - g) * 0.55, C.flash[1], impact * 0.6);
  const coreB = mix(b + (255 - b) * 0.55, C.flash[2], impact * 0.6);
  let heatStart = 1;
  for (let i = 0; i < 6; i++) {
    const landed = smooth((L - (T.collapse + (5 - i) * 0.045 + 0.27)) / 0.025);
    heatStart -= ((PIECES[i].right - PIECES[i].left) / (G.pw * 0.957)) * landed;
  }
  heatStart = clamp(heatStart);
  const axisLight = smooth((L - 7.32) / 0.025) * (1 - smooth((L - 8.07) / 0.08));
  if (L >= 7.32 && L < 7.57) {
    heat = axisLight;
    heatEnd = 1;
  }
  const comet = smooth((L - 7.32) / 0.25) * (1 - smooth((L - 8.07) / 0.08));
  const gatherTime = clamp((L - T.gather) / 0.16);
  const gatherFlash = smooth((L - T.gather) / 0.008) * (1 - gatherTime);
  const idle = (1 - smooth((L - 3.5) / 0.1)) * d + smooth((L - 10) / 0.45) * d;
  const breathe = still ? 1 : 1 + 0.06 * Math.sin((L * Math.PI) / 2) * idle;
  const ember =
    L >= 6.35 && L < 7.05
      ? Math.sin((L - 6.35) * 53) *
        Math.sin((L - 6.35) * 19) *
        0.1 *
        smooth((L - 6.35) / 0.1) *
        (1 - smooth((L - 6.95) / 0.1))
      : 0;
  const jolt = still
    ? 0
    : L >= 9.41
      ? (1 - springProgress(L - 9.41, 14, 320)) * smooth((L - 9.41) / 0.02)
      : 0;
  const dip = still ? 0 : L >= 6.35 && L < 7.05 ? 3 * pulse(L - 6.35, 0.035, 0.28) : 0;
  const phase = (L % 4) / 1.6,
    packet = still ? 0 : idle * smooth(phase / 0.08) * (1 - smooth((phase - 0.92) / 0.08));
  return {
    L,
    life: L,
    d,
    end,
    headU: head.u,
    hx,
    hy,
    angle,
    area: area * (1 + 1.2 * pulse(L - 9.41, 0.025, 0.35)),
    broken,
    heat,
    heatEnd,
    gather,
    label,
    grid,
    thumb,
    r,
    g,
    b,
    coreR,
    coreG,
    coreB,
    axisLight,
    heatStart,
    comet,
    gatherTime,
    gatherFlash,
    glow: breathe + ember,
    pool: Math.max(1 + 1.5 * Math.max(impact, crash), 1 + axisLight),
    jolt,
    dip,
    flash: impact,
    glitch: pulse(L - 9.41, 0.02, 0.16),
    packet,
    packetStart: clamp(phase - 0.06),
    packetEnd: clamp(phase),
    markerU: demoU(L),
    markerAlpha: still ? 0 : smooth((L - 0.9) / 0.15) * (1 - smooth((L - 3.3) / 0.2)),
    arrow: d ? 1 - smooth((end - 0.985) / 0.015) : 1,
  };
}
export type Film = ReturnType<typeof film>;
// Invert passage times once on the JS runtime, never in a frame worklet.
const COLUMN_EVENTS = RISERS.map((u) => {
  let lo = 8.07,
    hi = 9.59;
  for (let n = 0; n < 24; n++) {
    const t = (lo + hi) / 2;
    if (film(t, true).headU < u) lo = t;
    else hi = t;
  }
  let lo1 = 1.05,
    hi1 = 2.2;
  for (let n = 0; n < 24; n++) {
    const t = (lo1 + hi1) / 2;
    if (demoU(t) > u) lo1 = t;
    else hi1 = t;
  }
  let lo2 = 2.3,
    hi2 = 3.05;
  for (let n = 0; n < 24; n++) {
    const t = (lo2 + hi2) / 2;
    if (demoU(t) < u) lo2 = t;
    else hi2 = t;
  }
  return [
    (lo + hi) / 2,
    u <= 0.8 && u >= 0.16 ? (lo1 + hi1) / 2 : -100,
    u <= 0.62 && u >= 0.16 ? (lo2 + hi2) / 2 : -100,
  ];
});
// Combine precomputed film and demonstration crossing pulses.
export function columnFlash(L: number, i: number) {
  'worklet';
  const events = COLUMN_EVENTS[i];
  return Math.max(
    pulse(L - events[0], 0.12, 0.4),
    pulse(L - events[1], 0.12, 0.4),
    pulse(L - events[2], 0.12, 0.4),
  );
}
/** Fixed count; inactive particles are placed outside the scene. A spark is thrown up, falls
 * under gravity and goes out where it meets the axis again. */
export function sparkPoint(L: number, i: number) {
  'worklet';
  let t = L - 6.35,
    x = G.px + 0.957 * G.pw,
    active = i < 12;
  if (L >= 7.05) {
    const piece = Math.floor(i / 5);
    t = L - (7.05 + (5 - piece) * 0.045 + 0.3);
    x = PIECES[piece].cx + (((i * 29) % 13) - 6) * 3;
    active = i % 5 < 3;
  }
  const height = 10 + ((i * 17) % 31),
    gravity = 1400,
    vertical = Math.sqrt(2 * gravity * height),
    horizontal = (((i * 37) % 17) - 8) * 7;
  if (!active || t < 0 || t > (2 * vertical) / gravity) return { x: -100, y: -100 };
  return { x: x + horizontal * t, y: G.ay - vertical * t + 0.5 * gravity * t * t };
}

// Flash the joint just as its downstream piece separates.
export function crackPose(L: number, i: number) {
  'worklet';
  const t = L - (T.collapse + (5 - i) * 0.045);
  const p = clamp(t / 0.09);
  const point = atArc(0, PIECES[i].start);
  return { x: point.x, y: point.y, r: 2 + 7 * p, alpha: pulse(t, 0.006, 0.084) };
}
