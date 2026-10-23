// The only clock and state machine. Fixed substeps make display refresh rate irrelevant.
import { EVENT, M } from './supercharge-constants';
import { BOLT_TOP, GLASS, PAPER, PIVOT, RING } from './supercharge-layout';
import { corona, emptyLightning, filaments, rimArcs, random, strikeFrame, strikeGeometry, type BoltGeometry } from './supercharge-lightning';
import type { SuperchargePhase } from './supercharge-types';
export type Config = { reduced: boolean; autoPlay: boolean };
export type Action = { type: 'down' | 'move'; x: number; y: number } | { type: 'up' | 'cancel' | 'activate' };
export type State = ReturnType<typeof initialState>;
export function initialState(seed: number = M.seed) {
  'worklet';
  return {
    phase: 'rest' as SuperchargePhase, clock: 0, phaseTime: 0, accumulator: 0, ticks: 0,
    seed, initialSeed: seed, events: 0, silent: false, down: false, real: false, automated: false,
    script: 0, scriptPaused: false, resumeAt: 0, downAt: 0, downX: 0, downY: 0, travel: 0,
    energy: 0, glow: 0, glyph: M.ember.base as number, glyphScale: 1, ring: 0, ringLit: 0,
    dim: 1, flash: 0, tree: 0, treeGlow: 0, pulse: -1, shock: 0, meter: 0, charged: 0, caption: 0,
    card: [0, 0, 0, 1], sink: 0, shaft: [240, 0], touch: [201, 392, 0],
    sinkV: 0, scale: 1, scaleV: 0, drop: 0, dropV: 0, rotation: 0, rotationV: 0,
    chargeTime: 0, tickTime: 0, drainFrom: 1, glowFrom: 1, treeFrom: 0.3, ringFrom: 1, typeFrom: 1,
    geometry: null as BoltGeometry | null, lightning: emptyLightning(), filament: emptyLightning(), filamentAt: -1,
    filamentX: 0, filamentY: 0, filamentEnergy: 0,
    arc: emptyLightning(), arcAt: 0, arcUntil: 0,
    coronaLines: [] as number[], coronaAt: -1, coronaUntil: 0, shakeAt: -1, shakeX: 0, shakeY: 0, shakeR: 0,
    particles: [] as number[], sparks: [] as number[],
  };
}
export function clamp(x: number, low = 0, high = 1) {
  'worklet';
  return Math.max(low, Math.min(high, x));
}
function emit(s: State, event: number) {
  'worklet';
  if (!s.silent) s.events |= event;
}
function phase(s: State, next: SuperchargePhase) {
  'worklet';
  s.phase = next; s.phaseTime = 0;
}
export function chargeAt(t: number) {
  'worklet';
  const p = clamp(t / M.chargeDuration);
  if (p <= M.chargeLinearEnd) return p * M.chargeKnee / M.chargeLinearEnd;
  const tail = (p - M.chargeLinearEnd) / (1 - M.chargeLinearEnd);
  return M.chargeKnee + (1 - M.chargeKnee) * (1 - (1 - tail) ** 2);
}
function chargeTimeAt(e: number) {
  'worklet';
  return M.chargeDuration * (e <= M.chargeKnee ? e * M.chargeLinearEnd / M.chargeKnee
    : M.chargeLinearEnd + (1 - M.chargeLinearEnd) * (1 - Math.sqrt(Math.max(0, (1 - e) / (1 - M.chargeKnee)))));
}
export function drainEase(t: number) {
  'worklet';
  const target = clamp(t);
  let lo = 0, hi = 1;
  for (let i = 0; i < 18; i++) {
    const u = (lo + hi) / 2, v = 1 - u;
    const x = 3 * v * v * u * M.drain.bezierX1 + 3 * v * u * u * M.drain.bezierX2 + u * u * u;
    if (x < target) lo = u; else hi = u;
  }
  const u = (lo + hi) / 2;
  return 3 * (1 - u) * u * u + u * u * u;
}
// The shared light breath uses cubic-bezier(0.23, 1, 0.32, 1), then holds until impact.
export function strikeDim(age: number) {
  'worklet';
  if (age <= 0 || age >= M.strike.leader) return 1;
  if (age >= M.strike.dimDuration) return M.strike.dim;
  const target = age / M.strike.dimDuration;
  let lo = 0, hi = 1;
  for (let i = 0; i < 18; i++) {
    const u = (lo + hi) / 2, v = 1 - u;
    const x = 3 * v * v * u * 0.23 + 3 * v * u * u * 0.32 + u * u * u;
    if (x < target) lo = u; else hi = u;
  }
  const v = 1 - (lo + hi) / 2;
  return 1 - (1 - M.strike.dim) * (1 - v * v * v);
}
function localPoint(s: State, x: number, y: number) {
  'worklet';
  const dx = x - PIVOT.x - s.card[0], dy = y - PIVOT.y - s.card[1];
  const c = Math.cos(s.card[2]), n = Math.sin(s.card[2]);
  return { x: PIVOT.x + (dx * c + dy * n) / s.card[3], y: PIVOT.y + (-dx * n + dy * c) / s.card[3] };
}
function move(s: State, x: number, y: number) {
  'worklet';
  s.touch[0] = clamp(x, GLASS.x + M.touch.inset, GLASS.x + GLASS.width - M.touch.inset);
  s.touch[1] = clamp(y - s.sink, GLASS.y + M.touch.inset, M.touch.bottom);
}
function hit(x: number, y: number) {
  'worklet';
  const h = M.touch.hitSlop;
  return (x >= GLASS.x - h && x <= GLASS.x + GLASS.width + h && y >= GLASS.y - h && y <= GLASS.y + GLASS.height + h)
    || (x >= PAPER.left - h && x <= PAPER.right + h && y >= PAPER.top - h && y <= PAPER.bottom + h);
}
function down(s: State, x: number, y: number) {
  'worklet';
  if (s.phase === 'strike' || !hit(x, y)) return;
  s.down = true; s.downAt = s.clock; s.downX = x; s.downY = y; s.travel = 0;
  move(s, x, y); s.filamentAt = -1;
  if (s.phase === 'rest' || s.phase === 'draining' || s.phase === 'discharging') {
    s.chargeTime = chargeTimeAt(s.energy); s.tickTime = 0; phase(s, 'charging');
  }
  emit(s, EVENT.light);
}
function spawn(s: State, drain = false) {
  'worklet';
  const count = drain ? M.spark.drainCount : M.spark.ringCount + M.spark.boltCount + M.spark.groundCount;
  for (let i = 0; i < count; i++) {
    const ring = drain || i < M.spark.ringCount;
    const ground = !drain && i >= M.spark.ringCount + M.spark.boltCount;
    let theta = (ground ? Math.PI / 2 : -Math.PI / 2) + (random(s) - 0.5) * Math.PI * 150 / 180;
    if (ring && random(s) < 0.15) theta = random(s) * Math.PI;
    const speed = (M.spark.minSpeed + random(s) ** 3 * (M.spark.maxSpeed - M.spark.minSpeed)) * (drain ? 0.4 : 1);
    const life = M.spark.minLife + random(s) * (M.spark.maxLife - M.spark.minLife);
    const x = ring ? RING.x + (random(s) - 0.5) * 14 : ground ? s.geometry?.groundX ?? 200 : BOLT_TOP.x;
    const y = ring ? RING.y - RING.radius + random(s) * 6 : ground ? PAPER.bottom : BOLT_TOP.y;
    const delay = drain ? 0 : random(s) * M.spark.birthSpan;
    s.particles.push(x, y, Math.cos(theta) * speed, Math.sin(theta) * speed, life, life, delay);
  }
}
function drainingGlyph(energy: number, time: number, reduced: boolean) {
  'worklet';
  const base = Math.max(M.ember.base, M.drain.glyph * energy ** M.drain.glyphPower);
  const dip = !reduced && ((time > M.drain.flickers[0] && time < M.drain.flickers[1])
    || (time > M.drain.flickers[2] && time < M.drain.flickers[3]));
  return Math.max(M.ember.base, base * (dip ? M.drain.flickerLevel : 1));
}
function discharge(s: State, config: Config) {
  'worklet';
  s.drainFrom = s.energy; s.glowFrom = s.glow; s.treeFrom = s.treeGlow;
  s.ringFrom = s.ringLit; s.typeFrom = s.charged;
  phase(s, 'discharging');
  if (!config.reduced) spawn(s, true);
  emit(s, EVENT.soft);
}
function up(s: State, config: Config, cancelled = false) {
  'worklet';
  if (!s.down) return;
  s.down = false;
  if (s.phase === 'primed' && !cancelled) {
    phase(s, 'strike'); s.caption = -1; s.geometry = strikeGeometry(s.seed); random(s);
    s.lightning = strikeFrame(s.geometry, 0, config.reduced);
  } else if (s.phase === 'charging' || s.phase === 'primed') {
    phase(s, 'draining'); emit(s, EVENT.soft);
  } else if (s.phase === 'charged' && !cancelled && s.clock - s.downAt <= M.touch.tapTime && s.travel <= M.touch.tapTravel) discharge(s, config);
}
export function act(state: State, action: Action, config: Config): State {
  'worklet';
  const s = { ...state, touch: [...state.touch], particles: [...state.particles], events: 0 };
  if (s.phase === 'strike') {
    if (action.type === 'down') { s.scriptPaused = true; s.real = true; }
    if (action.type === 'up' || action.type === 'cancel') { s.real = false; s.resumeAt = s.clock + M.script.resume; }
    return s;
  }
  if (action.type === 'down' || action.type === 'activate') {
    // Transfer ownership without inventing a script release/strike or charged tap.
    s.scriptPaused = true; s.real = true; s.down = false; s.silent = false; s.automated = false;
    if (action.type === 'activate') {
      if (s.phase === 'charged') discharge(s, config);
      else { down(s, 201, 392); s.automated = true; }
      s.real = false; s.resumeAt = s.clock + M.script.resume;
    } else if (action.type === 'down') {
      const p = localPoint(s, action.x, action.y);
      if (!hit(p.x, p.y) && (s.phase === 'charging' || s.phase === 'primed')) phase(s, 'draining');
      down(s, p.x, p.y);
    }
  } else if (action.type === 'move' && s.real && s.down) {
    const p = localPoint(s, action.x, action.y);
    s.travel = Math.max(s.travel, Math.hypot(p.x - s.downX, p.y - s.downY));
    move(s, p.x, p.y);
  } else if (action.type === 'up' || action.type === 'cancel') {
    up(s, config, action.type === 'cancel'); s.real = false; s.resumeAt = s.clock + M.script.resume;
  }
  return s;
}
function spring(x: number, v: number, target: number, k: number, c: number, dt: number) {
  'worklet';
  const velocity = v + (-k * (x - target) - c * v) * dt;
  return [x + velocity * dt, velocity];
}
function physics(s: State, dt: number, config: Config) {
  'worklet';
  const before = s.phaseTime;
  s.phaseTime = Math.round((s.phaseTime + dt) / M.step) * M.step;
  const t = s.phaseTime, e = s.energy;
  s.dim = 1; s.flash = 0; s.pulse = -1; s.shock = 0; s.shaft = [240, 0];
  if (s.phase === 'charging' || s.phase === 'primed') {
    s.chargeTime += dt;
    s.energy = chargeAt(s.chargeTime);
    s.glow = M.charging.glow * s.energy;
    s.glyph = M.ember.base + (1 - M.ember.base) * s.energy ** M.charging.glyphPower;
    if (!config.reduced && e < M.charging.flickerUntil && t % M.flicker.period < M.flicker.duration) s.glyph *= M.flicker.depth;
    s.tickTime += dt;
    const tick = M.tick.slow + (M.tick.fast - M.tick.slow) * s.energy;
    if (s.tickTime >= tick && s.phase === 'charging') {
      s.tickTime -= tick; emit(s, e < M.tick.light ? EVENT.selection : e < M.tick.medium ? EVENT.light : EVENT.medium);
    }
    if (s.phase === 'charging' && s.chargeTime + M.epsilon >= M.chargeDuration) { s.energy = 1; phase(s, 'primed'); emit(s, EVENT.rigid); }
    if (s.automated && s.phase === 'primed') { s.automated = false; up(s, config); s.resumeAt = s.clock + M.script.resume; }
  } else if (s.phase === 'draining') {
    s.energy = Math.max(0, s.energy - (M.drain.rate + M.drain.acceleration * (1 - s.energy)) * dt);
    s.glow = M.charging.glow * s.energy; s.glyph = drainingGlyph(s.energy, t, config.reduced);
    if (s.energy === 0) phase(s, 'rest');
  } else if (s.phase === 'strike') {
    s.energy = 1;
    const age = Math.max(0, t - M.strike.leader);
    s.dim = config.reduced ? 1 : strikeDim(t);
    if (t < M.strike.leader) { s.glow = M.charging.glow; s.glyph = 1; }
    else {
      if (before + M.epsilon < M.strike.leader && t + M.epsilon >= M.strike.leader) {
        emit(s, EVENT.heavy | EVENT.success);
        if (!config.reduced) { s.drop = M.strike.kick; s.dropV = 0; s.rotation = M.strike.rotation; }
      }
      s.flash = (config.reduced ? M.reduced.flash : M.strike.flash) * Math.exp(-Math.max(0, age - M.strike.flashHold) / M.strike.flashTau);
      s.glow = clamp(age / M.strike.glowRise);
      s.glyph = 1 + (M.strike.glyph - 1) * (1 - clamp(age / M.strike.glyphCool)) ** 2;
      s.glyphScale = config.reduced ? 1 : 1 + (M.strike.glyphScale - 1) * Math.exp(-age / M.strike.glyphScaleTau);
      s.tree = clamp(age / M.strike.treeDuration);
      s.treeGlow = M.strike.treeCold + (M.strike.treeHot - M.strike.treeCold) * Math.exp(-age / M.strike.treeTau);
      s.charged = clamp((t - M.strike.stampAt) / M.strike.stampDuration);
    }
    if (t + M.epsilon >= M.strike.ringAt) {
      const ringAge = t - M.strike.ringAt;
      s.ringLit = 1 + (M.strike.ringHot - 1) * (1 - clamp(ringAge / M.strike.ringSettle)) ** 2;
      s.shock = config.reduced || ringAge >= M.strike.shock ? 0 : clamp(ringAge / M.strike.shock);
      if (before + M.epsilon < M.strike.ringAt && !config.reduced) spawn(s);
    }
    if (t + M.epsilon >= M.strike.end) { phase(s, 'charged'); s.glyph = s.glyphScale = s.charged = s.glow = 1; s.flash = 0; }
  } else if (s.phase === 'charged') {
    s.energy = s.ringLit = s.glyph = s.glyphScale = s.tree = s.charged = 1;
    s.glow = 1 + M.charged.breath * Math.sin(2 * Math.PI * t / M.charged.period);
    s.treeGlow = M.strike.treeCold;
    const cycle = t % M.charged.pulsePeriod;
    s.pulse = config.reduced || cycle >= M.charged.pulseDuration ? -1 : cycle / M.charged.pulseDuration;
  } else if (s.phase === 'discharging') {
    s.energy = s.drainFrom * (1 - drainEase(t / M.drain.energy));
    if (t >= M.drain.energy) s.energy = 0;
    s.glow = s.glowFrom * (1 - clamp(t / M.drain.glow));
    s.treeGlow = s.treeFrom * (1 - clamp(t / M.drain.glow));
    if (s.treeGlow === 0) s.tree = 0;
    s.ringLit = s.ringFrom * (1 - clamp(t / M.drain.ring));
    s.charged = s.typeFrom * (1 - clamp(t / M.drain.type));
    s.glyph = drainingGlyph(s.energy, t, config.reduced);
    if (t + M.epsilon >= M.drain.end) phase(s, 'rest');
  }
  if (s.phase === 'rest') {
    s.energy = s.glow = s.ringLit = s.tree = s.treeGlow = s.charged = 0; s.glyphScale = 1;
    s.glyph = M.ember.base + M.ember.amplitude * Math.sin(2 * Math.PI * s.clock / M.ember.period);
    if (!config.reduced && s.clock > M.ember.tickPeriod && s.clock % M.ember.tickPeriod < M.ember.tickDuration) s.glyph = M.ember.tickLevel;
  }
  s.ring = s.meter = s.energy;
  s.caption = s.phase === 'primed' ? 1 : s.phase === 'strike' ? (t >= M.print.captionAt ? 2 : -1) : s.phase === 'charged' ? 2 : 0;
  const held = s.down && (s.phase === 'charging' || s.phase === 'primed' || s.phase === 'charged');
  s.touch[2] = clamp(s.touch[2] + (held ? dt / M.touch.rise : -dt / M.touch.fall));
  const strike = s.phase === 'strike', anticipation = strike && t < M.strike.leader;
  const k = held ? M.press.stiffness : strike ? M.strike.stiffness : M.release.stiffness;
  const c = held ? M.press.damping : strike ? M.strike.damping : M.release.damping;
  const sink = spring(s.sink, s.sinkV, held ? s.phase === 'charged' ? M.touch.holdSink : M.press.sink : 0, k, c, dt);
  s.sink = sink[0]; s.sinkV = sink[1];
  const scale = spring(s.scale, s.scaleV, anticipation ? M.strike.dipScale : held ? M.press.scale : 1, k, c, dt);
  s.scale = scale[0]; s.scaleV = scale[1];
  const drop = spring(s.drop, s.dropV, anticipation ? M.strike.drop : 0, k, c, dt);
  s.drop = config.reduced ? 0 : drop[0]; s.dropV = config.reduced ? 0 : drop[1];
  const rotation = spring(s.rotation, s.rotationV, 0, k, c, dt);
  s.rotation = config.reduced ? 0 : rotation[0]; s.rotationV = config.reduced ? 0 : rotation[1];
  let shake = 0;
  if (!config.reduced && (s.phase === 'charging' || s.phase === 'primed')) {
    shake = M.shake.base + M.shake.gain * s.energy * s.energy;
    const slot = Math.floor((s.clock + M.epsilon) / M.shake.interval);
    if (slot !== s.shakeAt) { s.shakeAt = slot; s.shakeX = random(s) * 2 - 1; s.shakeY = random(s) * 2 - 1; s.shakeR = random(s) * 2 - 1; }
  }
  s.card = [s.shakeX * shake, s.drop + s.shakeY * shake, s.rotation + (shake ? s.shakeR * M.shake.rotation * s.energy ** 2 : 0), s.scale];
  if (s.phase === 'rest' && Math.abs(s.sink) + Math.abs(s.sinkV) + Math.abs(s.scaleV) < M.settleTolerance) {
    s.sink = s.sinkV = s.scaleV = s.drop = s.dropV = s.rotation = s.rotationV = 0; s.scale = 1; s.card = [0, 0, 0, 1];
  }
  s.sparks = [];
  const particles: number[] = [];
  for (let i = 0; i < s.particles.length; i += 7) {
    const delay = Math.max(0, s.particles[i + 6] - dt);
    if (s.particles[i + 6] > 0) {
      particles.push(s.particles[i], s.particles[i + 1], s.particles[i + 2], s.particles[i + 3], s.particles[i + 4], s.particles[i + 5], delay);
      continue;
    }
    const life = s.particles[i + 4] - dt;
    if (life <= 0) continue;
    const vx = s.particles[i + 2] * Math.exp(-M.spark.drag * dt);
    const vy = s.particles[i + 3] * Math.exp(-M.spark.drag * dt) + M.spark.gravity * dt;
    const x = s.particles[i] + vx * dt, y = s.particles[i + 1] + vy * dt;
    particles.push(x, y, vx, vy, life, s.particles[i + 5], 0); s.sparks.push(x, y, vx, vy, life / s.particles[i + 5]);
  }
  s.particles = particles;
}
function electricity(s: State, config: Config) {
  'worklet';
  if (s.phase === 'strike' && s.geometry) {
    s.lightning = strikeFrame(s.geometry, s.phaseTime, config.reduced);
    s.shaft = [s.geometry.meanX, s.lightning.level * M.strike.shaft]; return;
  }
  const held = s.down && (s.phase === 'charging' || s.phase === 'primed' || s.phase === 'charged');
  if (held) {
    const interval = s.energy > M.filament.fastAt ? M.filament.fastInterval : M.filament.interval;
    const fingerMoved = s.filamentX !== s.touch[0] || s.filamentY !== s.touch[1] || s.filamentEnergy !== s.energy;
    if (s.filamentAt < 0 || (config.reduced && fingerMoved) || (!config.reduced && s.clock - s.filamentAt + M.epsilon >= interval)) {
      if (!config.reduced || s.filamentAt < 0) random(s);
      s.filamentAt = s.clock; s.filamentX = s.touch[0]; s.filamentY = s.touch[1]; s.filamentEnergy = s.energy;
      s.filament = filaments(s.touch[0], s.touch[1], s.energy, s.seed, s.phase === 'charged');
    }
  }
  const charged = s.phase === 'charged';
  const coronaPeriod = charged ? M.corona.chargedPeriod : M.corona.period;
  if (!config.reduced && (charged || ((s.phase === 'charging' || s.phase === 'primed') && s.energy >= M.corona.threshold))
    && s.clock - s.coronaAt >= coronaPeriod) {
    s.coronaAt = s.clock; s.coronaUntil = s.clock + M.corona.lifeMin + random(s) * (M.corona.lifeMax - M.corona.lifeMin);
    s.coronaLines = corona(s.seed, charged ? 2 + Math.floor(random(s) * 2) : s.phase === 'primed' ? 4 + Math.floor(random(s) * 3) : 1 + Math.floor(s.energy * 4));
  }
  const arcEligible = charged || ((s.phase === 'charging' || s.phase === 'primed') && s.energy >= M.arc.threshold);
  if (!config.reduced && arcEligible && s.clock >= s.arcAt) {
    const life = M.arc.lifeMin + random(s) * (M.arc.lifeMax - M.arc.lifeMin);
    s.arcUntil = s.clock + life;
    const progress = clamp((s.energy - M.arc.threshold) / (1 - M.arc.threshold));
    s.arcAt = s.clock + (charged ? M.arc.chargedPeriod : life + M.arc.slowPause + (M.arc.fastPause - M.arc.slowPause) * progress);
    s.arc = rimArcs(s.seed, s.energy, charged ? 1 : s.phase === 'primed' ? 3 : s.energy >= 0.7 ? 2 : 1);
  }
  const activeArc = arcEligible && s.clock < s.arcUntil;
  const activeCorona = s.clock < s.coronaUntil;
  // One strong root across both populations; a live rim arc takes precedence over the finger.
  s.lightning = { main: [], branch: held && !activeArc ? [...s.filament.branch] : [], twig: held ? [...s.filament.twig] : [], level: held ? M.filament.base + M.filament.gain * s.energy : activeCorona ? 0.7 : 0 };
  if (activeArc) {
    if (held) s.lightning.twig.push(...s.filament.branch);
    s.lightning.branch.push(...s.arc.branch); s.lightning.twig.push(...s.arc.twig);
    s.lightning.level = Math.max(s.lightning.level, s.arc.level);
  }
  if (activeCorona) for (let i = 0; i < s.coronaLines.length; i++) s.lightning.twig.push(s.coronaLines[i]);
}
function script(s: State, previous: number, config: Config) {
  'worklet';
  if (!config.autoPlay || config.reduced) return;
  if (s.scriptPaused) {
    if (s.real || s.automated || s.clock < s.resumeAt || (s.phase !== 'rest' && s.phase !== 'charged')) return;
    s.scriptPaused = false; s.script = s.phase === 'charged' ? M.script.tap : 0; s.silent = true;
    if (s.phase === 'charged') { down(s, 201, 380); up(s, config); }
    return;
  }
  s.silent = true;
  const t = s.script;
  if (previous < M.script.firstDown && t + M.epsilon >= M.script.firstDown) down(s, M.script.firstPoint[0], M.script.firstPoint[1]);
  if (previous < M.script.firstUp && t + M.epsilon >= M.script.firstUp) up(s, config);
  if (previous < M.script.secondDown && t + M.epsilon >= M.script.secondDown) down(s, M.script.secondPoint[0], M.script.secondPoint[1]);
  if (previous < M.script.release && t + M.epsilon >= M.script.release) up(s, config);
  if (previous < M.script.playDown && t + M.epsilon >= M.script.playDown) down(s, M.script.playFrom[0], M.script.playFrom[1]);
  if (previous < M.script.playUp && t + M.epsilon >= M.script.playUp) up(s, config);
  if (previous < M.script.tap && t + M.epsilon >= M.script.tap) { down(s, 201, 380); up(s, config); }
  if (s.down) {
    const gliding = t >= M.script.playDown;
    const glide = clamp(t - M.script.playDown);
    const x = gliding ? M.script.playFrom[0] + glide * (M.script.playTo[0] - M.script.playFrom[0]) : t < M.script.secondDown ? M.script.firstPoint[0] : M.script.secondPoint[0];
    const y = gliding ? M.script.playFrom[1] + glide * (M.script.playTo[1] - M.script.playFrom[1]) : t < M.script.secondDown ? M.script.firstPoint[1] : M.script.secondPoint[1];
    move(s, x + M.script.wanderX * Math.sin(2 * Math.PI * M.script.frequencyX * t), y + M.script.wanderY * Math.sin(2 * Math.PI * M.script.frequencyY * t + 1));
    s.travel = gliding ? 150 : 30;
  }
}
export function step(state: State, seconds: number, config: Config): State {
  'worklet';
  let s = { ...state, touch: [...state.touch], particles: [...state.particles], events: 0 };
  s.accumulator += clamp(seconds, 0, M.maxFrame);
  while (s.accumulator + M.epsilon >= M.step) {
    s.accumulator = Math.max(0, s.accumulator - M.step);
    if (s.accumulator < M.epsilon) s.accumulator = 0;
    s.ticks++; s.clock = s.ticks * M.step;
    const previous = s.script;
    if (!s.scriptPaused && config.autoPlay && !config.reduced) s.script = Math.round((s.script + M.step) / M.step) * M.step;
    physics(s, M.step, config);
    script(s, previous, config);
    electricity(s, config);
    if (!s.scriptPaused && config.autoPlay && !config.reduced && s.script + M.epsilon >= M.script.period) {
      // Exact canonical boundary: RNG, held shapes, springs and every published level repeat.
      const remainder = s.accumulator;
      s = initialState(s.initialSeed); s.accumulator = remainder;
    }
  }
  return s;
}
