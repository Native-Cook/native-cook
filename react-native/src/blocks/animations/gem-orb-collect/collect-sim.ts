// Deterministic, fixed-step reward physics. This module owns no clocks or React objects.
import { BALANCE_AFTER, BALANCE_BEFORE, CAMERA, LIGHTS, MAX_SPRITES, ORB_COUNT, ORB_VALUE, REST, SPRITE_STRIDE, TIMING, type Layout } from './gem-contract';

export const STEP = 1 / 240;
const TAU = Math.PI * 2;
export const TRANSFER_MORPH = 0.16;
export const STRIKE_FLASH = 0.09;
export const SWAP_DURATION = 0.3;
export const CROSSFADE_DURATION = 0.2;
const SPARK_COUNT = 50;
export const EVENT = { press: 1, hit: 2, burst: 4, land: 8, ping: 16, rewind: 32 } as const;
export type Phase = 'before' | 'collecting' | 'hush' | 'burst' | 'after' | 'rewind';
export type CollectInput = { gemDown: boolean; dragX: number; dragY: number; releaseVX: number; releaseVY: number; tap: boolean; collect: boolean; replay: boolean; reduced: boolean; silent?: boolean; barDown?: boolean; press?: boolean; touchX?: number; touchY?: number; holdMoved?: boolean };
export function emptyInput(reduced = false): CollectInput {
  'worklet';
  return { gemDown: false, dragX: 0, dragY: 0, releaseVX: 0, releaseVY: 0, tap: false, collect: false, replay: false, reduced };
}
export function clamp(v: number, lo = 0, hi = 1) { 'worklet'; return Math.min(hi, Math.max(lo, v)); }
export function ease(t: number, x1 = 0.23, y1 = 1, x2 = 0.32, y2 = 1) {
  'worklet';
  const p = clamp(t); if (p === 0 || p === 1) return p;
  let lo = 0, hi = 1;
  for (let i = 0; i < 12; i++) {
    const u = (lo + hi) / 2, v = 1 - u;
    if (3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u < p) lo = u; else hi = u;
  }
  const u = (lo + hi) / 2, v = 1 - u;
  return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u;
}
function hash(i: number) { 'worklet'; const n = Math.sin(i * 127.1 + 43.7) * 43758.5453; return n - Math.floor(n); }
/** One clipped line travels upward while the next enters immediately below it. */
export function swapProgress(from: number, target: number, age: number, reduced = false) {
  'worklet';
  return from + (target - from) * (reduced ? clamp(age / CROSSFADE_DURATION) : ease(age / SWAP_DURATION, 0.77, 0, 0.175, 1));
}
export function copyFrame(progress: number, target: number, after: boolean, line = 34, speed = 0, reduced = false) {
  'worklet';
  const visibility = after ? progress : 1 - progress;
  const blur = reduced ? 0 : clamp((Math.abs(speed) - 60) / 180);
  return { opacity: reduced ? visibility : visibility <= 0 ? 0 : 1 - 0.4 * blur,
    offset: reduced ? 0 : (after === (target === 1) ? 1 : -1) * (1 - visibility) * line,
    scale: 1 + 0.12 * blur };
}
export type Orb = { hx: number; hy: number; hz: number; x: number; y: number; z: number; sx: number; sy: number; sz: number; tx: number; ty: number; tz: number; size: number; tint: number; angle: number; arc: number; launch: number; hit: number; duration: number; slot: number; status: number; silent: boolean };
export type Spark = { x: number; y: number; z: number; vx: number; vy: number; life: number; age: number; size: number; tint: number };
export function makeHomes(l: Layout): Orb[] {
  'worklet';
  const orbs: Orb[] = [];
  for (let i = 0; i < ORB_COUNT; i++) {
    const a = TAU * (i + (hash(i) - 0.5) * 0.38) / ORB_COUNT;
    const z = i < 5 ? -1.6 + i * 0.18 : i > 17 ? 1.85 + (i - 18) * 0.3 : 0.12 + hash(i + 52) * 1.32;
    const p = CAMERA / (CAMERA - z), r = l.gemRadius;
    const distance = 1.45 + hash(i + 20) * 0.9;
    // Sizes are authored in screen points, including the two foreground bokeh discs.
    const core = i > 17 ? 22 + hash(i + 66) * 6 : 9 + hash(i + 66) * 5;
    const margin = 16 + core * 2 + 34;
    let px = clamp(Math.cos(a) * distance * r * p, -l.gemX + margin, l.width - l.gemX - margin);
    let py = clamp(Math.sin(a) * distance * r * p * 1.12, -(l.tallyY - l.gemY - margin - 4), l.gemY - margin);
    if (Math.hypot(px, py) < r * 1.25 && Math.abs(z) <= 1.2) py = Math.sqrt(Math.max(0, (r * 1.29) ** 2 - px * px));
    if (i > 17) { px = (i === 18 ? -1 : 1) * (l.gemX - margin); py = r * (i === 18 ? 1.1 : 1.4); }
    const hx = px / (r * p), hy = py / (r * p);
    const strikeAngle = Math.atan2(hy, hx) + (i % 2 ? 1 : -1) * (0.87 + hash(i + 80) * 1.05);
    const tz = i % 6 === 0 ? -0.08 : 0.38 + 0.3 * hash(i + 40);
    const rr = Math.sqrt(0.82 ** 2 - tz * tz);
    orbs.push({ hx, hy, hz: z, x: hx, y: hy, z, sx: hx, sy: hy, sz: z, tx: Math.cos(strikeAngle) * rr, ty: Math.sin(strikeAngle) * rr, tz, size: core / p, tint: i % 3, angle: 0, arc: strikeAngle - Math.atan2(hy, hx), launch: -1, hit: -1, duration: 0, slot: -1, status: 0, silent: false });
  }
  return orbs;
}
function launchOrder(orbs: Orb[]) {
  'worklet';
  const order: number[] = []; let side = 1;
  for (let n = 0; n < ORB_COUNT; n++) {
    let best = -1, score = 999;
    for (let i = 0; i < ORB_COUNT; i++) {
      let used = false; for (let j = 0; j < order.length; j++) if (order[j] === i) used = true;
      const rank = Math.abs(orbs[i].hz) + (orbs[i].hx * side < 0 ? 0.32 : 0);
      if (!used && rank < score) { best = i; score = rank; }
    }
    order.push(best); side = orbs[best].hx > 0 ? -1 : 1;
  }
  return order;
}
export function initialCollect(l: Layout) {
  'worklet';
  const orbs = makeHomes(l), sprites: number[] = [], lights: number[] = [], sparks: Spark[] = [];
  for (let i = 0; i < SPARK_COUNT; i++) sparks.push({ x: 0, y: 0, z: 0, vx: 0, vy: 0, life: 0, age: 0, size: 0, tint: 0 });
  for (let i = 0; i < MAX_SPRITES * SPRITE_STRIDE; i++) sprites.push(0);
  for (let i = 0; i < LIGHTS * 4; i++) lights.push(0);
  return { phase: 'before' as Phase, clock: 0, ticks: 0, accumulator: 0, phaseAt: 0, events: 0, audibleEvents: 0, phaseSilent: false, down: false, barDown: false, held: 0, holdX: 0, holdY: 0, dragOnly: false, holdAccepted: false, automatic: false,
    launched: 0, hits: 0, cadence: 0, nextLaunch: 0, targetCharge: 0, charge: 0, orbs, order: launchOrder(orbs), sparks, sprites, lights, point: [0, 0, 0], tickInput: emptyInput(),
    x: 0, y: 0, vx: 0, vy: 0, turn: 0, tilt: 0, turnV: 0, tiltV: 0, scale: 1, scaleV: 0, stretch: 1, stretchV: 0,
    yaw: REST.yaw as number, pitch: REST.pitch + 0.07, centreX: l.gemX, centreY: l.gemY, radius: l.gemRadius,
    hushFromCharge: 0, hushFromScale: 1, hush: 0, flare: 0, shock: 0, beams: 0, burstAt: -1, spinFrom: 0, pingAt: -10, pingX: 0, pingY: 0, lastHitAt: -10,
    tally: 0, tallyX: l.tallyX, tallyY: l.tallyY, tallyScale: 1, tallyV: 0, tallyOpacity: 0, firstHitAt: -1,
    landed: false, value: BALANCE_BEFORE, rollFade: 1, copy: 0, bodyCopy: 0, copySpeed: 0, bodyCopySpeed: 0, barLabelSpeed: 0, copyAt: -10, copyFrom: 0, copyTarget: 0,
    barLabel: 0, barLabelFrom: 0, barLabelTarget: 0, barLabelAt: -10, barOpacity: 1, barPress: 0, barScale: 1, barV: 0, barGlow: 0, barSheen: -1,
    capsuleScale: 1, capsuleV: 0, capsuleGlow: 0, capsuleSheen: -1, miniYaw: REST.yaw as number, miniFlare: 0,
    beadX: l.tallyX, beadY: l.tallyY, beadSize: 2, beadAlpha: 0, beadStretch: 1, beadAngle: Math.PI / 2,
    rewindCharge: 0, rewindValue: BALANCE_BEFORE, rewindAt: -1,
  };
}
export type CollectState = ReturnType<typeof initialCollect>;
function emit(s: CollectState, event: number, silent: boolean) {
  'worklet'; s.events |= event; if (!silent) s.audibleEvents |= event;
}
function changePhase(s: CollectState, phase: Phase) { 'worklet'; s.phase = phase; s.phaseAt = s.clock; }
function setCopy(s: CollectState, target: number) { 'worklet'; s.copyFrom = s.copy; s.copyTarget = target; s.copyAt = s.clock; }
function setLabel(s: CollectState, target: number) { 'worklet'; s.barLabelFrom = s.barLabel; s.barLabelTarget = target; s.barLabelAt = s.clock; }
function spring(s: CollectState, x: 'turn' | 'tilt' | 'x' | 'y' | 'scale' | 'stretch' | 'tallyScale' | 'barScale' | 'capsuleScale', v: 'turnV' | 'tiltV' | 'vx' | 'vy' | 'scaleV' | 'stretchV' | 'tallyV' | 'barV' | 'capsuleV', target: number, k: number, c: number, dt: number) {
  'worklet'; s[v] += (k * (target - s[x]) - c * s[v]) * dt; s[x] += s[v] * dt;
}
function fitFlight(x: number, y: number, z: number, l: Layout, weightIn: number, core: number, out: number[]): number[] {
  'worklet';
  const projection = l.gemRadius * CAMERA / (CAMERA - z);
  const px = x * projection, py = -y * projection;
  const halo = core * CAMERA / (CAMERA - z) * 2;
  const rx = Math.abs(px) / Math.max(1, l.gemX - 20 - halo);
  const ry = Math.abs(py) / Math.max(1, py > 0 ? l.tallyY - 24 - halo - l.gemY : l.gemY - 20 - halo);
  // A smooth superellipse compresses the outer arc without straight clipped edges.
  const cap = 1 / (1 + rx ** 8 + ry ** 8) ** 0.125;
  const weight = clamp((Math.hypot(x, y, z) - 0.9) / 0.35);
  const scale = 1 + (cap - 1) * weight * weightIn;
  out[0] = x * scale; out[1] = y * scale; out[2] = z; return out;
}
export function orbPoint(o: Orb, progress: number, l: Layout, out: number[] = [0, 0, 0]): number[] {
  'worklet';
  const radius = l.gemRadius;
  const p = clamp(progress), homeR = Math.hypot(o.sx, o.sy), a = Math.atan2(o.sy, o.sx);
  if (p < 0.14) { const push = Math.sin(p / 0.14 * Math.PI / 2) * 12 / radius; out[0] = o.sx + Math.cos(a) * push; out[1] = o.sy + Math.sin(a) * push; out[2] = o.sz; return out; }
  const u = (p - 0.14) / 0.86, inward = u ** 3.5, endR = Math.hypot(o.tx, o.ty);
  const r = (homeR + 12 / radius) * (1 - inward) + endR * inward;
  const angle = a + o.arc * u * u;
  return fitFlight(r * Math.cos(angle), r * Math.sin(angle), o.sz * (1 - inward) + o.tz * inward + Math.sin(Math.PI * u) * (o.sz < 0 ? -0.22 : 0.15), l, ease(u / 0.35), o.size, out);
}
function sprite(s: CollectState, slot: number, x: number, y: number, z: number, size: number, alpha: number, tint: number, stretch = 1, angle = 0) {
  'worklet'; const k = slot * SPRITE_STRIDE;
  s.sprites[k] = x; s.sprites[k + 1] = y; s.sprites[k + 2] = z; s.sprites[k + 3] = size; s.sprites[k + 4] = clamp(alpha); s.sprites[k + 5] = tint; s.sprites[k + 6] = stretch; s.sprites[k + 7] = angle;
}
function strike(s: CollectState, o: Orb, l: Layout, index: number) {
  'worklet';
  o.status = 2; o.hit = s.clock; s.hits++; s.tally = s.hits * ORB_VALUE; emit(s, EVENT.hit, o.silent); s.lastHitAt = s.clock;
  s.targetCharge = s.hits / ORB_COUNT; s.tallyScale = 1.12; s.tallyV = 0;
  if (s.firstHitAt < 0) s.firstHitAt = s.clock;
  const norm = Math.max(0.01, Math.hypot(o.tx, o.ty));
  s.vx -= 120 * o.tx / norm; s.vy += 120 * o.ty / norm; s.turnV += o.tx * 1.4; s.tiltV -= o.ty * 1.4; s.scaleV += 0.975;
  const p = CAMERA / (CAMERA - o.tz), x = l.gemX + o.tx * l.gemRadius * p, y = l.gemY - o.ty * l.gemRadius * p;
  for (let j = 0; j < 4 + index % 2; j++) {
    const a = Math.atan2(-o.ty, o.tx) + Math.PI / 2 + (hash(index * 7 + j) - 0.5) * 1.8 + (j % 2 ? Math.PI : 0), speed = 120 + hash(index * 9 + j) * 140;
    for (let slot = 0; slot < SPARK_COUNT; slot++) {
      const spark = s.sparks[slot]; if (spark.age < spark.life) continue;
      spark.x = x; spark.y = y; spark.z = o.tz; spark.vx = Math.cos(a) * speed; spark.vy = Math.sin(a) * speed;
      spark.life = 0.25 + hash(index * 4 + j) * 0.15; spark.age = 0; spark.size = 3 + hash(index + j) * 1.5; spark.tint = o.tint; break;
    }
  }
  if (s.hits === ORB_COUNT) { s.phaseSilent = o.silent; s.hushFromCharge = s.charge; s.hushFromScale = s.scale; changePhase(s, 'hush'); }
}
function rewind(s: CollectState, silent: boolean) {
  'worklet';
  emit(s, EVENT.rewind, silent); s.rewindCharge = s.charge; s.rewindValue = s.value; s.rewindAt = s.clock;
  s.automatic = false; s.tally = 0; s.tallyOpacity = 0; for (let i = 0; i < SPARK_COUNT; i++) s.sparks[i].life = 0; setCopy(s, 0); changePhase(s, 'rewind');
}
function advance(s: CollectState, input: CollectInput, l: Layout) {
  'worklet';
  const dt = STEP; s.ticks++; s.clock = s.ticks * STEP;
  if (input.press || (input.gemDown && !s.down) || (input.barDown && !s.barDown)) emit(s, EVENT.press, !!input.silent);
  if (!s.down || !input.gemDown || input.press) { s.held = 0; s.holdX = 0; s.holdY = 0; s.dragOnly = false; s.holdAccepted = false; }
  if (input.gemDown) {
    s.holdX += input.dragX; s.holdY += input.dragY;
    // Once accepted, moving does not cancel collection; a rejected touch stays a drag.
    if (!s.holdAccepted && (input.holdMoved || Math.hypot(s.holdX, s.holdY) > TIMING.holdSlop)) s.dragOnly = true;
    s.held += dt;
    if (!s.dragOnly && s.held >= TIMING.holdDelay - 1e-8) s.holdAccepted = true;
  }
  s.down = input.gemDown; s.barDown = !!input.barDown;
  if (input.collect && s.phase === 'before') { s.automatic = true; s.phaseSilent = !!input.silent; changePhase(s, 'collecting'); }
  if (s.phase === 'before' && s.holdAccepted) { s.phaseSilent = !!input.silent; changePhase(s, 'collecting'); }
  if (input.replay && s.phase !== 'before' && s.phase !== 'rewind') rewind(s, !!input.silent);
  const frozen = s.phase === 'hush' || s.phase === 'burst';
  if (!frozen) {
    if (s.down) {
      s.turn += input.dragX / l.gemRadius;
      if (input.dragY !== 0) s.tilt = Math.tanh((s.tilt + input.dragY / l.gemRadius) / 1.1) * 1.1;
      if (input.dragX !== 0 || input.dragY !== 0) { s.turnV = 0; s.tiltV = 0; }
      // A stationary hold still lets off-centre strikes shiver the stone.
      s.turn += s.turnV * dt; s.tilt += s.tiltV * dt; s.turnV *= Math.exp(-12.25 * dt); s.tiltV *= Math.exp(-12.25 * dt);
    }
    else if (!input.reduced) { if (input.releaseVX) s.turnV = input.releaseVX / l.gemRadius; if (input.releaseVY) s.tiltV = input.releaseVY / l.gemRadius; }
    if (input.tap) { s.pingAt = s.clock; s.pingX = input.touchX ?? 0.25; s.pingY = input.touchY ?? 0.2; s.scaleV += input.reduced ? 0 : 1.6; emit(s, EVENT.ping, !!input.silent); }
  }
  const t = s.clock - s.phaseAt;
  s.flare = 0; s.shock = 0; s.beams = 0; s.hush = 0;
  if (s.phase === 'collecting') {
    if (input.reduced) {
      s.charge = clamp(t / 0.5); s.flare = 0.35 * Math.sin(Math.PI * clamp(t / 0.6)); s.rollFade = Math.abs(2 * clamp(t / 0.6) - 1);
      s.value = t >= 0.3 ? BALANCE_AFTER : BALANCE_BEFORE;
      if (t < dt * 1.5) setCopy(s, 1);
      if (t >= 0.6 - 1e-8) { s.hits = ORB_COUNT; s.targetCharge = 1; s.rollFade = 1; changePhase(s, 'after'); setLabel(s, 1); emit(s, EVENT.land, s.phaseSilent); }
    } else if (s.automatic || s.holdAccepted) {
      s.cadence += dt;
      if (s.launched < ORB_COUNT && s.cadence + 1e-8 >= s.nextLaunch) {
        const n = s.launched, o = s.orbs[s.order[n]];
        o.status = 1; o.silent = !!input.silent; o.launch = s.clock; o.duration = TIMING.flightFirst + (TIMING.flightLast - TIMING.flightFirst) * n / (ORB_COUNT - 1);
        o.sx = o.x; o.sy = o.y; o.sz = o.z; o.arc = Math.atan2(o.ty, o.tx) - Math.atan2(o.sy, o.sx);
        // Keep the signed short arc, avoiding a 2π branch-cut jump.
        if (o.arc > Math.PI) o.arc -= TAU; if (o.arc < -Math.PI) o.arc += TAU;
        s.launched++; s.nextLaunch += Math.max(TIMING.minGap, TIMING.firstGap * TIMING.gapRatio ** n) + (TIMING.flightFirst - TIMING.flightLast) / (ORB_COUNT - 1);
      }
    }
  }
  if (s.phase === 'hush') {
    const h = ease(t / TIMING.hush, 0.77, 0, 0.175, 1); s.hush = h; s.scale = s.hushFromScale + (0.86 - s.hushFromScale) * h; s.scaleV = 0; s.stretch = 1 - 0.06 * h; s.charge = s.hushFromCharge + (0.35 - s.hushFromCharge) * h;
    if (t >= TIMING.hush - 1e-8) { changePhase(s, 'burst'); s.burstAt = s.clock; emit(s, EVENT.burst, s.phaseSilent); s.spinFrom = s.turn; s.scale = 0.86; s.scaleV = 2.5; s.stretch = 1.06; s.stretchV = 0; s.charge = 1; }
  }
  if (s.phase === 'burst' || s.phase === 'after') {
    const age = s.clock - s.burstAt;
    s.charge = 1;
    if (!input.reduced && s.burstAt >= 0) {
      s.flare = age < 1.9 ? (age < TIMING.flareAttack ? age / TIMING.flareAttack : Math.exp(-(age - TIMING.flareAttack) / TIMING.flareDecay) * clamp((1.9 - age) / 0.2)) : 0;
      s.hush = 1 - clamp(age / 0.15); s.shock = age <= TIMING.shock ? 1 - (1 - clamp(age / TIMING.shock)) ** 3 : 0;
      s.beams = clamp(age / 0.12) * (1 - ease((age - 0.65) / (TIMING.beams - 0.65)));
      if (age < TIMING.spin) { s.turn = s.spinFrom - 0.18 + (TAU + 0.18) * ease(age / TIMING.spin, 0.16, 1, 0.3, 1); s.turnV = 0; s.tilt = -0.25 * Math.sin(Math.PI * clamp(age / TIMING.spin)); }
      else if (age < TIMING.spin + dt * 1.1) { s.turn = s.spinFrom + TAU; s.turnV = 0.35; }
      if (age >= 0.2 && s.copyTarget !== 1) setCopy(s, 1);
      const landAge = age - TIMING.tallyLeaves - TRANSFER_MORPH - TIMING.tallyFlight;
      if (landAge >= -1e-8 && !s.landed) { s.landed = true; emit(s, EVENT.land, s.phaseSilent); s.capsuleScale = 0.94; s.capsuleV = 0; }
      if (s.landed) {
        s.value = BALANCE_BEFORE + (BALANCE_AFTER - BALANCE_BEFORE) * ease(landAge / TIMING.roll);
        s.capsuleGlow = Math.sin(Math.PI * clamp(landAge / 0.9)); s.capsuleSheen = -1 + 3 * clamp(landAge / 0.9);
        s.miniYaw = REST.yaw + TAU * ease(landAge / 1.1);
        s.miniFlare = 0.7 * clamp(landAge / 0.06) * (1 - ease((landAge - 0.06) / 0.5));
      }
      if (s.phase === 'burst' && age >= 2 - 1e-8) { changePhase(s, 'after'); setLabel(s, 1); }
    }
  }
  if (s.phase === 'rewind') {
    s.charge = s.rewindCharge * (1 - ease(t / 0.6)); s.value = input.reduced ? (t < 0.3 ? s.rewindValue : BALANCE_BEFORE) : s.rewindValue + (BALANCE_BEFORE - s.rewindValue) * ease(t / 0.6);
    s.rollFade = input.reduced ? Math.abs(2 * clamp(t / 0.6) - 1) : 1;
    s.flare = 0; s.hush = 0; s.beams = 0; s.shock = 0; s.miniFlare = 0;
    if (t >= TIMING.rewind - 1e-8) {
      changePhase(s, 'before'); setLabel(s, 0); s.launched = 0; s.hits = 0; s.cadence = 0; s.nextLaunch = 0; s.targetCharge = 0; s.charge = 0; s.burstAt = -1; s.landed = false; s.firstHitAt = -1; s.miniYaw = REST.yaw; s.capsuleGlow = 0; s.capsuleSheen = -1; s.rollFade = 1;
      for (let i = 0; i < ORB_COUNT; i++) { const o = s.orbs[i]; o.status = 0; o.launch = -1; o.hit = -1; o.slot = -1; }
    }
  }
  if (s.phase === 'before' || (s.phase === 'collecting' && !input.reduced)) s.charge += (s.targetCharge - s.charge) * (1 - Math.exp(-dt / 0.25));
  if (!s.down && !(s.phase === 'burst' && s.clock - s.burstAt < TIMING.spin)) {
    spring(s, 'turn', 'turnV', Math.round(s.turn / TAU) * TAU, 66.7, frozen ? 24 : 12.25, dt);
    spring(s, 'tilt', 'tiltV', 0, 66.7, frozen ? 24 : 12.25, dt);
  }
  spring(s, 'x', 'vx', 0, 420, frozen ? 42 : 20, dt); spring(s, 'y', 'vy', 0, 420, frozen ? 42 : 20, dt);
  if (s.phase !== 'hush') {
    if (s.down && !frozen) spring(s, 'scale', 'scaleV', 0.97, 420, 30, dt);
    else spring(s, 'scale', 'scaleV', 1, 220, 11, dt);
    spring(s, 'stretch', 'stretchV', 1, 220, 15, dt);
  }
  const idle = s.clock % TIMING.loop, sway = s.phase === 'after' ? 0.28 : 0.22;
  // The inward twist is a presentation offset; the burst unwinds it continuously.
  // One extra half-sway carries the fire through the end of the balance roll.
  const afterSway = s.phase === 'after' ? 0.18 * Math.sin(Math.PI * clamp((s.clock - s.phaseAt) / 1.8)) : 0;
  s.yaw = REST.yaw + s.turn + (input.reduced ? 0 : Math.sin(TAU * idle / 12) * sway * (1 - s.hush) + afterSway - (s.phase === 'hush' ? 0.18 * s.hush : 0));
  s.pitch = REST.pitch + s.tilt + (input.reduced ? 0 : Math.cos(TAU * idle / 6) * 0.07 * (1 - s.hush));
  s.centreX = l.gemX + s.x; s.centreY = l.gemY + s.y + (input.reduced ? 0 : 5 * Math.sin(TAU * idle / 6) * (1 - s.hush)); s.radius = l.gemRadius * s.scale;
  spring(s, 'tallyScale', 'tallyV', 1, 500, 22, dt);
  s.tallyX = l.tallyX; s.tallyY = l.tallyY + 6 * (1 - ease((s.clock - s.firstHitAt) / 0.16)); s.tallyOpacity = s.tally > 0 && !input.reduced ? ease((s.clock - s.firstHitAt) / 0.16) : 0;
  s.beadAlpha = 0;
  if (!input.reduced && (s.phase === 'burst' || s.phase === 'after') && s.burstAt >= 0) {
    const transferAge = s.clock - s.burstAt - TIMING.tallyLeaves;
    if (transferAge >= 0) {
      const morph = clamp(transferAge / TRANSFER_MORPH);
      s.tallyScale = 1 - 0.3 * morph; s.tallyOpacity *= 1 - morph;
      const flightAge = transferAge - TRANSFER_MORPH;
      const e = ease(flightAge / TIMING.tallyFlight, 0.77, 0, 0.175, 1);
      const x = l.tallyX + (l.miniGemX - l.tallyX) * e + 24 * Math.sin(Math.PI * e);
      const y = l.tallyY + (l.miniGemY - l.tallyY) * e;
      const dx = x - s.beadX, dy = y - s.beadY;
      const speed = flightAge > 0 ? Math.hypot(dx, dy) / dt : 0;
      s.beadX = x; s.beadY = y; s.beadSize = 4 + 9 * morph;
      s.beadAlpha = morph * (1 - clamp((flightAge - TIMING.tallyFlight) / 0.06));
      s.beadStretch = 1 + 2.5 * clamp(speed / 850);
      if (speed > 0.01) s.beadAngle = Math.atan2(dy, dx);
    }
  }
  const oldCopy = s.copy, oldBody = s.bodyCopy, oldLabel = s.barLabel;
  s.copy = swapProgress(s.copyFrom, s.copyTarget, s.clock - s.copyAt, input.reduced);
  s.bodyCopy = swapProgress(s.copyFrom, s.copyTarget, s.clock - s.copyAt - 0.08, input.reduced);
  s.barLabel = swapProgress(s.barLabelFrom, s.barLabelTarget, s.clock - s.barLabelAt, input.reduced);
  s.copySpeed = (s.copy - oldCopy) / dt; s.bodyCopySpeed = (s.bodyCopy - oldBody) / dt; s.barLabelSpeed = (s.barLabel - oldLabel) / dt;
  const enabled = s.phase === 'before' || s.phase === 'after';
  s.barOpacity += ((enabled ? 1 : 0.45) - s.barOpacity) * (1 - Math.exp(-dt / 0.055));
  s.barPress += ((s.barDown && enabled ? 1 : 0) - s.barPress) * (1 - Math.exp(-dt / 0.04));
  spring(s, 'barScale', 'barV', s.barDown && enabled ? 0.97 : 1, 420, 24, dt);
  spring(s, 'capsuleScale', 'capsuleV', 1, 420, 22, dt);
  const availableAge = s.clock - s.barLabelAt; s.barGlow = s.phase === 'after' ? Math.sin(Math.PI * clamp(availableAge / 0.9)) : 0; s.barSheen = s.phase === 'after' ? -1 + 3 * clamp(availableAge / 0.9) : -1;
  // All fixed sprite slots are cleared, then populated; both depth passes read the same array.
  for (let i = 0; i < s.sprites.length; i++) s.sprites[i] = 0;
  for (let i = 0; i < s.lights.length; i++) s.lights[i] = 0;
  for (let i = 0; i < ORB_COUNT; i++) {
    const o = s.orbs[i]; let alpha = 0, stretch = 1;
    const driftX = input.reduced ? 0 : Math.sin(TAU * idle / 6 + i) * (4 + hash(i) * 5) / l.gemRadius;
    const driftY = input.reduced ? 0 : Math.sin(TAU * idle / 4 + i * 1.7) * (4 + hash(i + 5) * 5) / l.gemRadius;
    if (o.status === 0) { o.x = o.hx + driftX; o.y = o.hy + driftY; o.z = o.hz + (input.reduced ? 0 : Math.sin(TAU * idle / 3 + i) * 0.035); alpha = 0.55 + 0.18 * Math.sin(TAU * idle / 3 + i); }
    if (o.status === 1) {
      const progress = (s.clock - o.launch) / o.duration, p = orbPoint(o, progress, l, s.point);
      o.angle = Math.atan2(-(p[1] - o.y), p[0] - o.x); o.x = p[0]; o.y = p[1]; o.z = p[2]; alpha = 0.65 + 0.35 * progress; stretch = 1 + 4 * clamp(progress) ** 3;
      if (progress >= 1 - 1e-8) strike(s, o, l, i);
    }
    if (o.status === 2) { alpha = 1 - clamp((s.clock - o.hit) / 0.05); stretch = 5; if (s.clock - o.hit >= STRIKE_FLASH + TIMING.absorb) { o.status = 3; o.slot = -1; } }
    if (input.reduced && s.phase !== 'before') alpha *= s.phase === 'rewind' ? clamp((t - i * 0.015) / 0.3) : s.phase === 'collecting' ? 1 - clamp((t - i * 0.015) / 0.3) : 0;
    if (s.phase === 'rewind' && !input.reduced) {
      const u = clamp((t - i * 0.012) / (TIMING.rewind - i * 0.012)), e = 1 - Math.exp(-7 * u) * Math.cos(8 * u);
      const twist = Math.sin(Math.PI * u) * 0.055 * (i % 2 ? 1 : -1);
      o.x = o.tx + (o.hx + driftX - o.tx) * e - o.hy * twist; o.y = o.ty + (o.hy + driftY - o.ty) * e + o.hx * twist; o.z = o.tz + (o.hz - o.tz) * e;
      alpha = clamp((t - i * 0.012) / 0.04); stretch = 1 + 2 * (1 - u); o.slot = -1;
    }
    const perspective = CAMERA / (CAMERA - o.z);
    sprite(s, i, l.gemX + o.x * l.gemRadius * perspective, l.gemY - o.y * l.gemRadius * perspective, o.z, o.size * perspective, alpha, o.tint, stretch, o.angle);
  }
  // Existing light ownership wins; free slots go to the flight closest to impact.
  // At the fastest cadence, 90 ms flash + 280 ms absorption exceeds six slots.
  // Only a new impact can retire an already absorbed tail below 0.01 strength;
  // its energy is already in charge. Visible lights never swap or lose their flash.
  for (let i = 0; i < ORB_COUNT; i++) {
    if (s.orbs[i].status !== 2 || s.orbs[i].slot >= 0) continue;
    let occupied = 0, weakest = -1, strength = 0.01;
    for (let j = 0; j < ORB_COUNT; j++) {
      const o = s.orbs[j];
      if (o.slot < 0) continue;
      occupied++;
      if (o.status !== 2 || s.clock - o.hit < STRIKE_FLASH) continue;
      const tail = 2 * (1 - ease((s.clock - o.hit - STRIKE_FLASH) / TIMING.absorb));
      if (tail < strength) { strength = tail; weakest = j; }
    }
    if (occupied === LIGHTS && weakest >= 0) { s.orbs[weakest].slot = -1; s.orbs[weakest].status = 3; }
  }
  let pingPlaced = false;
  for (let slot = 0; slot < LIGHTS; slot++) {
    let selected = -1;
    for (let i = 0; i < ORB_COUNT; i++) if (s.orbs[i].slot === slot) selected = i;
    if (selected < 0) {
      let remaining = Math.max(0.012, Math.max(TIMING.minGap, TIMING.firstGap * TIMING.gapRatio ** s.hits) * LIGHTS - STRIKE_FLASH - TIMING.absorb - 0.01);
      for (let i = 0; i < ORB_COUNT; i++) { const o = s.orbs[i], r = o.launch + o.duration - s.clock; if ((o.status === 1 || o.status === 2) && o.slot < 0 && r < remaining) { selected = i; remaining = r; } }
      if (selected >= 0) s.orbs[selected].slot = slot;
    }
    if (selected >= 0 && s.phase !== 'rewind') {
      const o = s.orbs[selected], j = slot * 4, age = s.clock - o.hit, absorb = 1 - ease((age - STRIKE_FLASH) / TIMING.absorb);
      const factor = o.status === 2 && age >= STRIKE_FLASH ? absorb : 1;
      s.lights[j] = o.x * factor; s.lights[j + 1] = o.y * factor; s.lights[j + 2] = o.z * factor;
      s.lights[j + 3] = o.status === 2 ? (age < STRIKE_FLASH ? 3 * (1 - age / STRIKE_FLASH) : -2 * absorb) : 0.3 + 0.7 * clamp((1.5 - Math.hypot(o.x, o.y, o.z)) / 0.68);
    } else if (!pingPlaced && s.clock - s.pingAt < 0.25) { const j = slot * 4; s.lights[j] = s.pingX; s.lights[j + 1] = s.pingY; s.lights[j + 2] = 0.85; s.lights[j + 3] = 2.5 * (1 - (s.clock - s.pingAt) / 0.25); pingPlaced = true; }
  }
  let live = 0;
  for (let i = 0; i < s.sparks.length; i++) {
    const p = s.sparks[i]; if (p.age >= p.life) continue; p.age += dt; if (p.age >= p.life) continue;
    p.vx *= Math.exp(-5 * dt); p.vy *= Math.exp(-5 * dt); p.x += p.vx * dt; p.y += p.vy * dt;
    if (live < 50 && !input.reduced) sprite(s, ORB_COUNT + live, p.x, p.y, p.z, p.size, (1 - p.age / p.life) ** 2, p.tint, 2.5, Math.atan2(p.vy, p.vx));
    live++;
  }
  sprite(s, 70, s.beadX, s.beadY, 0.3, s.beadSize, s.beadAlpha, 1, s.beadStretch, s.beadAngle);
  const flashAge = s.clock - s.burstAt;
  if (!input.reduced && s.burstAt >= 0 && flashAge < 0.5) sprite(s, 71, s.centreX, s.centreY, 0.6, l.gemRadius * 1.5, 0.12 * Math.sin(Math.PI * flashAge / 0.5), 0);
}
/** Explicit offline snapshot; the live tick never clones. */
export function cloneCollect(s: CollectState): CollectState {
  'worklet';
  const orbs: Orb[] = [], sparks: Spark[] = [];
  for (let i = 0; i < s.orbs.length; i++) orbs.push({ ...s.orbs[i] });
  for (let i = 0; i < s.sparks.length; i++) sparks.push({ ...s.sparks[i] });
  return { ...s, orbs, sparks, lights: [...s.lights], sprites: [...s.sprites], order: [...s.order], point: [...s.point], tickInput: { ...s.tickInput } };
}
function copyField<K extends keyof CollectState>(s: CollectState, rest: CollectState, key: K) {
  'worklet'; s[key] = rest[key];
}
/** Reset the canonical loop without replacing any pool or renderer buffer. */
export function resetCollect(s: CollectState, rest: CollectState) {
  'worklet';
  for (const key in rest) {
    const k = key as keyof CollectState;
    if (typeof rest[k] !== 'object') copyField(s, rest, k);
  }
  for (let i = 0; i < s.orbs.length; i++) Object.assign(s.orbs[i], rest.orbs[i]);
  for (let i = 0; i < s.sparks.length; i++) Object.assign(s.sparks[i], rest.sparks[i]);
  for (let i = 0; i < s.sprites.length; i++) s.sprites[i] = rest.sprites[i];
  for (let i = 0; i < s.lights.length; i++) s.lights[i] = rest.lights[i];
  for (let i = 0; i < s.point.length; i++) s.point[i] = rest.point[i];
  Object.assign(s.tickInput, rest.tickInput);
  return s;
}
/** Mutates the UI-owned state and reusable tick input; no display-frame snapshot. */
export function stepCollect(s: CollectState, seconds: number, input: CollectInput, l: Layout): CollectState {
  'worklet';
  s.events = 0; s.audibleEvents = 0;
  s.accumulator += clamp(seconds, 0, 0.05);
  const count = Math.floor((s.accumulator + 1e-9) / STEP), tick = s.tickInput;
  tick.gemDown = input.gemDown; tick.reduced = input.reduced; tick.silent = !!input.silent;
  tick.barDown = !!input.barDown; tick.press = !!input.press; tick.tap = input.tap;
  tick.collect = input.collect; tick.replay = input.replay; tick.holdMoved = !!input.holdMoved;
  tick.touchX = input.touchX; tick.touchY = input.touchY; tick.releaseVX = input.releaseVX; tick.releaseVY = input.releaseVY;
  tick.dragX = input.dragX / Math.max(1, count); tick.dragY = input.dragY / Math.max(1, count);
  for (let i = 0; i < count; i++) {
    advance(s, tick, l);
    tick.press = false; tick.tap = false; tick.collect = false; tick.replay = false; tick.releaseVX = 0; tick.releaseVY = 0;
    s.accumulator -= STEP;
  }
  if (s.accumulator < 1e-9) s.accumulator = 0;
  return s;
}
/** Canonical visible rest, also used for the seamless demo boundary. */
export function restCollect(l: Layout, reduced = false): CollectState {
  'worklet'; const s = initialCollect(l); const next = stepCollect(s, STEP, emptyInput(reduced), l);
  next.clock = 0; next.ticks = 0;
  // Sample all periodic outputs at exactly zero, without a physics step.
  next.centreY = l.gemY; next.yaw = REST.yaw; next.pitch = REST.pitch + (reduced ? 0 : 0.07);
  for (let i = 0; i < ORB_COUNT; i++) {
    const o = next.orbs[i]; o.x = o.hx + (reduced ? 0 : Math.sin(i) * (4 + hash(i) * 5) / l.gemRadius); o.y = o.hy + (reduced ? 0 : Math.sin(i * 1.7) * (4 + hash(i + 5) * 5) / l.gemRadius); o.z = o.hz + (reduced ? 0 : Math.sin(i) * 0.035);
    const p = CAMERA / (CAMERA - o.z); sprite(next, i, l.gemX + o.x * l.gemRadius * p, l.gemY - o.y * l.gemRadius * p, o.z, o.size * p, 0.55 + 0.18 * Math.sin(i), o.tint);
  }
  return next;
}
