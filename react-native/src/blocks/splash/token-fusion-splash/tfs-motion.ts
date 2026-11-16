import { comp } from './tfs-theme';

export const LOOP = 7.5;
export const T = {
  rest: [0, 0.4],
  gather: [0.4, 1],
  approach: [1, 1.7],
  neck: [1.7, 2],
  captures: [2, 2.55, 2.95, 3.28],
  captureDuration: 0.32,
  flood: [3.75, 4.6],
  mark: [4.18, 4.92],
  word: [4.96, 5.2],
  brand: [5.2, 5.7],
  recede: [5.7, 7],
  seam: [7, 7.5],
} as const;
export const SPRINGS = {
  gather: { damping: 14, stiffness: 120, mass: 1 },
  capture: { damping: 16, stiffness: 200, mass: 1 },
  recoil: { damping: 12, stiffness: 160, mass: 1 },
  coreHome: { damping: 16, stiffness: 110, mass: 1.2 },
  mass: { damping: 18, stiffness: 90, mass: 1 },
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
} as const;
export const PHYSICS = {
  decayPerMs: 0.996,
  gravity: 9000,
  gravityMinGap: 20,
  pullRadius: 160,
  captureGap: 40,
  contactGap: 6,
  holdGap: 44,
  reachGap: 96,
  snapGap: 110,
  pullBackSeconds: 0.16,
  minGrabRadius: 9,
  ringHz: 3.2,
  ringDecay: 0.26,
  ringMin: 0.09,
  ringBoost: 0.09,
  speedMax: 1500,
  recoil: 14,
  resumeAfter: 2,
  resumeDuration: 0.6,
  maxStretch: 1.25,
  orbitSpeeds: [6, 9, 13, 19, 19, 19, 19],
  orbitTighten: 0.93,
  massLag: 0.12,
  firstRingPeak: Math.atan(2 * Math.PI * 3.2 * 0.26) / (2 * Math.PI * 3.2),
  dyeSeconds: 2.4,
  flightTimeout: 0.8,
  projection: 0.12,
} as const;
export type Coin = { x: number; y: number; r: number; reach: number; dye: number; lean: number; heat: number };
export type Scene = {
  x: number;
  y: number;
  r: number;
  sphere: number;
  stretch: number;
  axis: number;
  ringScale: number;
  mark: number;
  markSize: number;
  cut: number;
  word: number;
  wordY: number;
  flood: number;
  coins: Coin[];
};
export function clamp(x: number, lo = 0, hi = 1) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}
export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}
export function smooth(x: number) {
  'worklet';
  const t = clamp(x);
  return t * t * (3 - 2 * t);
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return smooth((t - a) / Math.max(b - a, 0.0001));
}
/** Analytic damped spring, preserving exact time sampling for ports and offline previews. */
export function springAt(t: number, damping: number, stiffness: number, mass = 1) {
  'worklet';
  if (t <= 0) return 0;
  const w = Math.sqrt(stiffness / mass),
    z = damping / (2 * Math.sqrt(stiffness * mass));
  const wd = w * Math.sqrt(Math.max(0.0001, 1 - z * z));
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}
export function ringAt(t: number, speed = 0) {
  'worklet';
  if (t < 0) return 0;
  return (
    (PHYSICS.ringMin + PHYSICS.ringBoost * clamp(speed / PHYSICS.speedMax)) *
    Math.sin(2 * Math.PI * PHYSICS.ringHz * t) *
    Math.exp(-t / PHYSICS.ringDecay)
  );
}
export function orbitAt(i: number, count: number, angle: number, gather: number, mass: number) {
  'worklet';
  const a = (((count === 4 ? comp.orbit.angles[i] : -125 + (i * 360) / count) + angle) * Math.PI) / 180;
  const tilt = (comp.orbit.tilt * Math.PI) / 180;
  const gain = Math.pow(PHYSICS.orbitTighten, mass);
  const x = mix(comp.orbit.wideX, comp.orbit.tightX, gather) * gain * Math.cos(a);
  const y = mix(comp.orbit.wideY, comp.orbit.tightY, gather) * gain * Math.sin(a);
  return { x: comp.core.x + x * Math.cos(tilt) - y * Math.sin(tilt), y: comp.core.y + x * Math.sin(tilt) + y * Math.cos(tilt) };
}
export function coinReach(x: number, y: number, r: number, coreX: number, coreY: number, coreR: number) {
  'worklet';
  return 1 - seg(Math.hypot(x - coreX, y - coreY) - r - coreR, 30, PHYSICS.reachGap);
}
export function sceneAt(time: number, count = 4): Scene {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  const returning = t > T.recede[0];
  const back = seg(t, T.recede[0], T.recede[1]);
  const gather = returning ? 1 - back : clamp(springAt(t - T.gather[0], 14, 120));
  let area = 0,
    mass = 0,
    angle = 6 * Math.min(t, 5.7),
    ringing = 0;
  for (let i = 0; i < count; i++) {
    const cap = count === 4 ? T.captures[i] : 2 + i * (1.28 / Math.max(1, count - 1));
    const p = returning ? 1 - seg(t, 5.84 + i * 0.07, 6.85 + i * 0.04) : seg(t, cap, cap + T.captureDuration);
    area += 22 * 22 * p;
    mass += clamp(springAt(t - cap - 0.18 - PHYSICS.firstRingPeak - PHYSICS.massLag, 18, 90)) * (1 - back);
    if (t > cap + 0.2)
      angle += Math.max(0, t - cap - 0.32) * (PHYSICS.orbitSpeeds[Math.min(i + 1, 6)] - PHYSICS.orbitSpeeds[Math.min(i, 6)]);
    ringing += ringAt(t - cap - 0.18) * (1 - back);
  }
  // An orbit turns forward, then retraces only while droplets split off. The seam has an exact resting interval.
  angle *= 1 - back;
  const base = Math.sqrt(mix(120 * 120, 55 * 55, gather) + area);
  const flood = seg(t, T.flood[0], T.flood[1]) * (1 - back);
  const r = mix(base, 980, flood);
  const coins: Coin[] = [];
  for (let i = 0; i < count; i++) {
    const cap = count === 4 ? T.captures[i] : 2 + i * (1.28 / Math.max(1, count - 1));
    const slot = orbitAt(i, count, angle, gather, mass);
    const approach = i === 0 ? seg(t, 1, 1.7) : seg(t, cap - 0.7, cap - 0.08);
    const swallowed = seg(t, cap, cap + T.captureDuration);
    const release = seg(t, 5.84 + i * 0.07, 6.85 + i * 0.04);
    const travel = returning ? 1 - release : approach;
    const progress = returning ? 1 - release : swallowed;
    const dx = slot.x - comp.core.x,
      dy = slot.y - comp.core.y;
    const len = Math.max(1, Math.hypot(dx, dy));
    const rr = 22 * Math.sqrt(Math.max(0, 1 - progress));
    const destination = 55 + PHYSICS.holdGap + 22;
    const radial = mix(len, mix(destination, 20, swallowed), travel);
    const x = comp.core.x + (dx / len) * radial,
      y = comp.core.y + (dy / len) * radial;
    coins.push({
      x,
      y,
      r: rr,
      reach: coinReach(x, y, rr, comp.core.x, comp.core.y, base) * travel,
      dye: Math.max(approach * (1 - swallowed), 1 - seg(t, cap, cap + PHYSICS.dyeSeconds)) * (approach > 0 ? 1 : 0) * (1 - back),
      lean: (Math.sign(comp.core.x - x) * Math.min(1, mass) * 6 * Math.PI) / 180,
      heat: approach * (1 - progress) * (1 - back),
    });
  }
  const mark = returning ? back : 1 - seg(t, 1.12, 1.35) + seg(t, T.mark[0], T.mark[1]);
  return {
    x: comp.core.x,
    y: comp.core.y,
    r,
    sphere: 1 - gather * 0.6,
    stretch: 1 + ringing,
    axis: -2.1,
    ringScale: 1 - gather * 0.6 * 0.54,
    mark: clamp(mark),
    markSize: returning ? mix(60, 60, back) : mix(mix(60, 40, gather), 60, seg(t, 4.18, 4.92)),
    cut: seg(t, 4.18, 4.42) * (1 - seg(t, 4.42, 4.92)) * (1 - back),
    word: seg(t, T.word[0], T.word[1]) * (1 - seg(t, 5.7, 5.94)),
    wordY: comp.wordmark.y + comp.wordmark.rise * clamp(1 - seg(t, T.word[0], T.word[1]) + back),
    flood,
    coins,
  };
}
export type LiveCoin = Coin & {
  state: number;
  vx: number;
  vy: number;
  age: number;
  speed: number;
  fromX: number;
  fromY: number;
  pointer: number;
  ox: number;
  oy: number;
  lastX: number;
  lastY: number;
  lastT: number;
  snapped: boolean;
  neckFormed: boolean;
};
export type Live = {
  coins: LiveCoin[];
  now: number;
  angle: number;
  gather: number;
  mass: number;
  massV: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
  corePointer: number;
  coreOX: number;
  coreOY: number;
  lastTouch: number;
  coreLastT: number;
  corePull: number;
  mark: number;
  flood: number;
  floodV: number;
  completed: boolean;
  recoilX: number;
  recoilY: number;
  recoilVX: number;
  recoilVY: number;
  ringTime: number;
  ringSpeed: number;
  ringAxis: number;
  mergedMask: number;
  mergeEvents: number;
  releaseEvents: number;
  cue: number;
  stretch: number;
  snapTime: number;
  carryStretch: number;
  carryStretchV: number;
  visual: { markSize: number; cut: number; word: number; wordY: number };
};
export function liveFrom(scene: Scene, next?: Scene): Live {
  'worklet';
  let mask = 0;
  const coins = scene.coins.map((c, i) => {
    const fused = c.r < 1;
    if (fused) mask |= 1 << i;
    const shrinking = !fused && c.r < 21.9 && (!next || next.coins[i].r < c.r);
    let low = 0,
      high = 1;
    const progress = clamp(1 - (c.r * c.r) / (22 * 22));
    for (let j = 0; j < 12; j++) {
      const mid = (low + high) / 2;
      if (smooth(mid) < progress) low = mid;
      else high = mid;
    }
    const age = shrinking ? ((low + high) / 2) * 0.32 : 0;
    const left = Math.max(0.001, 1 - clamp(springAt(age, 16, 200)));
    return {
      ...c,
      state: fused ? 4 : shrinking ? 3 : 0,
      vx: next ? (next.coins[i].x - c.x) * 120 : 0,
      vy: next ? (next.coins[i].y - c.y) * 120 : 0,
      age,
      speed: 0,
      fromX: scene.x + (c.x - scene.x) / left,
      fromY: scene.y + (c.y - scene.y) / left,
      pointer: -1,
      ox: 0,
      oy: 0,
      lastX: c.x,
      lastY: c.y,
      lastT: 0,
      snapped: false,
      neckFormed: false,
    };
  });
  return {
    coins,
    now: 0,
    angle: 0,
    gather: clamp((1 - scene.sphere) / 0.6),
    mass: 0,
    massV: 0,
    x: scene.x,
    y: scene.y,
    vx: next ? (next.x - scene.x) * 120 : 0,
    vy: next ? (next.y - scene.y) * 120 : 0,
    corePointer: -1,
    coreOX: 0,
    coreOY: 0,
    coreLastT: 0,
    corePull: 0,
    lastTouch: 0,
    mark: scene.mark,
    flood: scene.flood,
    floodV: 0,
    completed: false,
    recoilX: 0,
    recoilY: 0,
    recoilVX: 0,
    recoilVY: 0,
    ringTime: 10,
    ringSpeed: 0,
    ringAxis: scene.axis,
    mergedMask: mask,
    mergeEvents: 0,
    releaseEvents: 0,
    cue: 0,
    stretch: scene.stretch,
    snapTime: 10,
    carryStretch: scene.stretch - 1,
    carryStretchV: next ? (next.stretch - scene.stretch) * 120 : 0,
    visual: { markSize: scene.markSize, cut: scene.cut, word: scene.word, wordY: scene.wordY },
  };
}
export function copyLive(m: Live): Live {
  'worklet';
  return { ...m, visual: { ...m.visual }, coins: m.coins.map((c) => ({ ...c })) };
}
export function liveRadius(m: Live) {
  'worklet';
  let area = mix(120 * 120, 55 * 55, m.gather);
  for (const c of m.coins) area += Math.max(0, 22 * 22 - c.r * c.r);
  return Math.sqrt(Math.max(55 * 55, area));
}
export function capture(m: Live, index: number, speed = 0) {
  'worklet';
  const c = m.coins[index];
  if (!c || c.state === 3 || c.state === 4) return;
  c.state = 3;
  c.age = 0;
  c.speed = speed;
  c.fromX = c.x;
  c.fromY = c.y;
  c.pointer = -1;
  m.mergeEvents |= 1 << index;
  m.mergedMask |= 1 << index;
  m.ringTime = 0;
  m.ringSpeed = speed;
  m.ringAxis = Math.atan2(c.y - m.y, c.x - m.x);
  m.recoilVX -= Math.cos(m.ringAxis) * PHYSICS.recoil * 25;
  m.recoilVY -= Math.sin(m.ringAxis) * PHYSICS.recoil * 25;
  m.cue = speed > PHYSICS.speedMax ? 4 : 3;
}
export function grab(m: Live, pointer: number, x: number, y: number, touchCore: boolean): boolean {
  'worklet';
  let best = -1,
    dist = 36;
  for (let i = 0; i < m.coins.length; i++) {
    const c = m.coins[i];
    if (c.pointer >= 0 || c.state === 4 || (c.state === 3 && (c.age > PHYSICS.pullBackSeconds || c.r < PHYSICS.minGrabRadius))) continue;
    const d = Math.hypot(x - c.x, y - c.y);
    if (d < dist) {
      best = i;
      dist = d;
    }
  }
  if (best >= 0) {
    const c = m.coins[best];
    if (c.state === 3) {
      m.mergedMask &= ~(1 << best);
      m.releaseEvents |= 1 << best;
    }
    c.state = 1;
    if (c.r >= 21.9) c.r = 22 * 1.04; // Immediate press response; the live spring continues to 1.12.
    c.pointer = pointer;
    c.ox = x - c.x;
    c.oy = y - c.y;
    c.lastT = m.now;
    c.snapped = false;
    c.neckFormed = c.reach > 0.2;
    c.lastX = c.x;
    c.lastY = c.y;
    c.age = 0;
    m.cue = 1;
    m.lastTouch = m.now;
    return true;
  }
  if (touchCore && m.corePointer < 0 && Math.hypot(x - m.x, y - m.y) < Math.min(liveRadius(m), 120)) {
    m.corePointer = pointer;
    m.coreOX = x - m.x;
    m.coreOY = y - m.y;
    m.lastTouch = m.now;
    m.coreLastT = m.now;
    m.cue = 1;
    return true;
  }
  return false;
}
export function movePointer(m: Live, pointer: number, x: number, y: number, minX: number, maxX: number, minY: number, maxY: number) {
  'worklet';
  if (pointer === m.corePointer) {
    let dx = x - m.coreOX - comp.core.x,
      dy = y - m.coreOY - comp.core.y;
    const d = Math.hypot(dx, dy),
      radius = comp.touch.coreLimit;
    m.corePull = Math.max(0, d - radius) / (120 + Math.max(0, d - radius));
    m.ringAxis = Math.atan2(dy, dx);
    if (d > radius) {
      dx *= radius / d;
      dy *= radius / d;
    }
    const dt = Math.max(1 / 120, m.now - m.coreLastT);
    m.vx = (comp.core.x + dx - m.x) / dt;
    m.vy = (comp.core.y + dy - m.y) / dt;
    m.x = comp.core.x + dx;
    m.y = comp.core.y + dy;
    m.coreLastT = m.now;
  }
  for (const c of m.coins)
    if (c.pointer === pointer) {
      const nx = clamp(x - c.ox, minX, maxX),
        ny = clamp(y - c.oy, minY, maxY);
      const dt = Math.max(1 / 120, m.now - c.lastT);
      c.vx = mix(c.vx, (nx - c.x) / dt, 0.7);
      c.vy = mix(c.vy, (ny - c.y) / dt, 0.7);
      c.x = nx;
      c.y = ny;
      c.lastT = m.now;
      const gap = Math.hypot(c.x - m.x, c.y - m.y) - liveRadius(m) - c.r;
      if (gap > PHYSICS.snapGap && c.neckFormed && !c.snapped) {
        c.snapped = true;
        m.snapTime = 0;
        m.cue = 2;
      }
      if (gap < PHYSICS.contactGap) capture(m, m.coins.indexOf(c), Math.hypot(c.vx, c.vy));
    }
  m.lastTouch = m.now;
}
export function releasePointer(m: Live, pointer: number, cancel = false) {
  'worklet';
  if (m.corePointer === pointer) m.corePointer = -1;
  for (let i = 0; i < m.coins.length; i++) {
    const c = m.coins[i];
    if (c.pointer !== pointer) continue;
    c.pointer = -1;
    c.age = 0;
    const gap = Math.hypot(c.x - m.x, c.y - m.y) - liveRadius(m) - c.r;
    if (!cancel && gap < PHYSICS.captureGap) capture(m, i, Math.hypot(c.vx, c.vy));
    else {
      c.state = cancel ? 5 : 2;
      c.fromX = c.x;
      c.fromY = c.y;
    }
  }
  m.lastTouch = m.now;
}
/** Semi-implicit live physics; fixed substeps keep throws stable at 30, 60 and 120 Hz. */
export function stepLive(m: Live, dt: number, reduced = false, allowAutoFlood = true) {
  'worklet';
  const steps = Math.max(1, Math.ceil(dt * 120)),
    h = Math.min(dt, 0.05) / steps;
  for (let n = 0; n < steps; n++) {
    m.now += h;
    m.ringTime += h;
    m.snapTime += h;
    m.carryStretchV += (-160 * m.carryStretch - 12 * m.carryStretchV) * h;
    m.carryStretch += m.carryStretchV * h;
    let captured = 0;
    for (const c of m.coins) if ((c.state === 3 && c.age > 0.32) || c.state === 4) captured++;
    const massTarget = m.ringTime > 0.18 + PHYSICS.firstRingPeak + PHYSICS.massLag ? captured : m.mass;
    m.massV += (90 * (massTarget - m.mass) - 18 * m.massV) * h;
    m.mass += m.massV * h;
    m.angle += PHYSICS.orbitSpeeds[Math.min(6, Math.round(clamp(m.mass, 0, 6)))] * h;
    if (m.corePointer < 0) {
      m.corePull *= Math.exp(-h * 10);
      m.vx += ((110 * (comp.core.x - m.x) - 16 * m.vx) / 1.2) * h;
      m.x += m.vx * h;
      m.vy += ((110 * (comp.core.y - m.y) - 16 * m.vy) / 1.2) * h;
      m.y += m.vy * h;
    }
    m.recoilVX += (-160 * m.recoilX - 12 * m.recoilVX) * h;
    m.recoilX += m.recoilVX * h;
    m.recoilVY += (-160 * m.recoilY - 12 * m.recoilVY) * h;
    m.recoilY += m.recoilVY * h;
    const r = liveRadius(m);
    for (let i = 0; i < m.coins.length; i++) {
      const c = m.coins[i];
      c.age += h;
      const slot = orbitAt(i, m.coins.length, m.angle, m.gather, m.mass);
      const dx = m.x - c.x,
        dy = m.y - c.y,
        d = Math.max(1, Math.hypot(dx, dy)),
        gap = d - r - c.r;
      if (c.state === 0 || c.state === 5) {
        c.vx += (120 * (slot.x - c.x) - 14 * c.vx) * h;
        c.vy += (120 * (slot.y - c.y) - 14 * c.vy) * h;
        c.x += c.vx * h;
        c.y += c.vy * h;
        c.r += (22 - c.r) * Math.min(1, h * 18);
        if (c.state === 5 && Math.hypot(slot.x - c.x, slot.y - c.y) < 1) c.state = 0;
        if (m.corePointer >= 0 && gap < PHYSICS.contactGap) capture(m, i, Math.hypot(m.vx, m.vy));
      } else if (c.state === 2) {
        const pull = gap < PHYSICS.pullRadius ? PHYSICS.gravity / Math.max(gap, PHYSICS.gravityMinGap) : 0;
        c.vx = (c.vx + (dx / d) * pull * h) * Math.pow(PHYSICS.decayPerMs, h * 1000);
        c.vy = (c.vy + (dy / d) * pull * h) * Math.pow(PHYSICS.decayPerMs, h * 1000);
        c.x += c.vx * h;
        c.y += c.vy * h;
        if (gap < PHYSICS.captureGap) capture(m, i, Math.hypot(c.vx, c.vy));
        else if (c.age > PHYSICS.flightTimeout) {
          c.state = 6;
          c.age = 0;
          c.fromX = c.x;
          c.fromY = c.y;
        }
      } else if (c.state === 6) {
        const p = seg(c.age, 0, 0.8);
        const startAngle = Math.atan2(c.fromY - m.y, c.fromX - m.x);
        const endAngle = Math.atan2(slot.y - m.y, slot.x - m.x);
        const turn = ((((endAngle - startAngle) % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI)) + 2 * Math.PI;
        const angle = startAngle + turn * p;
        const radius = mix(Math.hypot(c.fromX - m.x, c.fromY - m.y), Math.hypot(slot.x - m.x, slot.y - m.y), p);
        const nx = m.x + Math.cos(angle) * radius,
          ny = m.y + Math.sin(angle) * radius;
        c.vx = (nx - c.x) / h;
        c.vy = (ny - c.y) / h;
        c.x = nx;
        c.y = ny;
        if (p >= 1) {
          c.state = 5;
          c.age = 0;
        }
      } else if (c.state === 3) {
        const p = clamp(springAt(c.age, 16, 200));
        c.x = mix(c.fromX, m.x, p);
        c.y = mix(c.fromY, m.y, p);
        c.r = 22 * Math.sqrt(Math.max(0, 1 - seg(c.age, 0, 0.32)));
        if (c.age > 0.32) {
          c.state = 4;
          c.r = 0;
        }
      } else if (c.state === 1) c.r += (22 * 1.12 - c.r) * Math.min(1, h * 22);
      c.reach = reduced || ((c.state === 0 || c.state === 5) && m.corePointer < 0) ? 0 : coinReach(c.x, c.y, c.r, m.x, m.y, r);
      if (c.reach > 0.2 && !c.neckFormed) {
        c.neckFormed = true;
        if (c.state === 1 || c.state === 2) m.cue = 2;
      }
      c.dye = c.state === 3 || c.state === 4 ? 1 - seg(c.age, 0, PHYSICS.dyeSeconds) : c.reach;
      c.heat = c.reach;
      c.lean = (Math.sign(m.x - c.x) * (Math.min(1, m.mass) * 6 * Math.PI)) / 180;
    }
    m.mark += ((m.flood > 0.65 || !m.coins.some((c) => c.reach > 0.2) ? 1 : 0) - m.mark) * Math.min(1, h * 14);
    const all = m.coins.every((c) => c.state === 4);
    if ((all && allowAutoFlood) || m.flood > 0) {
      m.floodV += (48.7 * (1 - m.flood) - 14 * m.floodV) * h;
      m.flood = clamp(m.flood + m.floodV * h);
    }
    const follow = Math.min(1, h * 14);
    m.visual.markSize = mix(m.visual.markSize, mix(mix(60, 40, m.gather), 60, m.flood), follow);
    m.visual.cut = mix(m.visual.cut, seg(m.flood, 0.4, 0.65) * (1 - seg(m.flood, 0.65, 0.85)), follow);
    m.visual.word = mix(m.visual.word, seg(m.flood, 0.88, 0.98), follow);
    m.visual.wordY = mix(m.visual.wordY, comp.wordmark.y + comp.wordmark.rise * (1 - seg(m.flood, 0.88, 0.98)), follow);
    m.stretch = reduced
      ? 1
      : Math.min(PHYSICS.maxStretch, 1 + Math.hypot(m.vx, m.vy) / 6000 + 0.25 * m.corePull) +
        ringAt(m.ringTime - 0.18, m.ringSpeed) +
        m.carryStretch;
  }
}
export function sceneOf(m: Live, reduced = false): Scene {
  'worklet';
  const r = liveRadius(m),
    flood = m.flood;
  return {
    x: m.x + (reduced ? 0 : m.recoilX),
    y: m.y + (reduced ? 0 : m.recoilY),
    r: mix(r, 980, flood),
    sphere: 1 - m.gather * 0.6,
    stretch: reduced ? 1 : m.stretch + (0.05 * ringAt(m.snapTime)) / 0.09,
    axis: m.corePointer >= 0 && Math.hypot(m.vx, m.vy) > 1 ? Math.atan2(m.vy, m.vx) : m.ringAxis,
    ringScale: 1 - m.gather * 0.324,
    mark: reduced && flood > 0 ? 1 : m.mark,
    markSize: reduced && flood > 0 ? 60 : m.visual.markSize,
    cut: reduced ? 0 : m.visual.cut,
    word: reduced ? (flood > 0 ? 1 : 0) : m.visual.word,
    wordY: reduced ? comp.wordmark.y : m.visual.wordY,
    flood,
    coins: m.coins.map((c) => ({ ...c, r: c.r * (1 + (c.snapped && !reduced ? (0.03 * ringAt(m.snapTime)) / 0.09 : 0)) })),
  };
}
