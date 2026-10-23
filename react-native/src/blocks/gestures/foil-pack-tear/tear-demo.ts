/** A UI-thread finger, using exactly the same input kernel as a real touch. */
import { PACK, TEAR, sampleTearLine } from './tear-contract';
import { MOTION } from './tear-motion-constants';
import { clamp, copyTear, initialTear, inputTear, tickTear, type TearInput, type TearState } from './tear-sim';

const DEMO_LINE = sampleTearLine();

export function initialDemo() {
  'worklet';
  return {
    time: 0, paused: false, realDown: false, quiet: 0, segment: 0,
    startFront: 0, startX: 0, startY: 0, grabOffset: 0,
    lastX: 0, lastY: 0, remainder: 0,
  };
}
export type TearDemo = ReturnType<typeof initialDemo>;
export type TearEngine = { sim: TearState; demo: TearDemo };

export function initialEngine(reduced = false): TearEngine {
  'worklet';
  return { sim: initialTear(reduced), demo: initialDemo() };
}

function lineHeight(front: number): number {
  'worklet';
  const at = clamp(front / TEAR.step, 0, TEAR.columns - 1);
  const low = Math.floor(at);
  const high = Math.min(low + 1, TEAR.columns - 1);
  const value = DEMO_LINE[low] + (DEMO_LINE[high] - DEMO_LINE[low]) * (at - low);
  // Invert the tear's tanh so the recorded seam follows sampleTearLine, not a flattened copy.
  return TEAR.wander * Math.atanh(clamp(value / TEAR.wander, -0.99, 0.99));
}

function segmentAt(t: number): number {
  'worklet';
  if (t >= 1.4 && t < 2.15) return 1;
  if (t >= 2.7 && t < 3.55) return 2;
  if (t >= 5.3 && t < 5.48) return 3;
  if (t >= 7.2 && t < 10.2) return 4;
  if (t >= 10.5 && t < 10.56) return 5;
  return 0;
}

function finger(s: TearState, d: TearDemo, dt: number) {
  'worklet';
  const t = d.time;
  const segment = segmentAt(t + 1e-9);
  if (segment !== d.segment) {
    if (d.segment !== 0 && s.down) {
      // Sample the endpoint before releasing so each scripted path ends exactly level/on target.
      const x = d.segment === 1 ? PACK.x + PACK.width * 0.46 : d.segment === 2 ? PACK.x + PACK.width : d.startX;
      const y = d.segment <= 2 ? PACK.y + TEAR.y + d.grabOffset + lineHeight(x - PACK.x)
        : d.segment === 3 ? d.startY - 48 : d.startY;
      inputTear(s, { type: 'move', x, y, vx: (x - d.lastX) / dt, vy: (y - d.lastY) / dt });
      inputTear(s, { type: 'up', vx: s.vx, vy: s.vy });
    }
    d.segment = segment;
    if (segment === 0) return;
    if (segment === 1 || segment === 2) {
      d.startFront = s.pose.tearFront;
      d.startX = PACK.x + d.startFront + (segment === 1 ? 2 : 0);
      d.startY = PACK.y + TEAR.y + s.lineEnd;
      d.grabOffset = d.startY - PACK.y - TEAR.y - s.lineEnd;
    } else {
      d.startX = s.pose.cardX;
      d.startY = segment === 3 ? Math.min(s.pose.cardY, PACK.y + TEAR.y - 24) : s.pose.cardY;
    }
    d.lastX = d.startX;
    d.lastY = d.startY;
    inputTear(s, { type: 'down', x: d.startX, y: d.startY, silent: true });
  }
  if (segment === 0) return;
  let x = d.startX;
  let y = d.startY;
  if (segment === 1) {
    const u = clamp((t - 1.4) / 0.75);
    x += (PACK.x + PACK.width * 0.46 - x) * (1 - Math.cos(Math.PI * u)) / 2;
    y = PACK.y + TEAR.y + d.grabOffset + lineHeight(x - PACK.x) * clamp(u / 0.12);
  } else if (segment === 2) {
    const u = clamp((t - 2.7) / 0.85);
    const remaining = PACK.width - d.startFront;
    const power = Math.max(1, 500 * 0.85 / Math.max(remaining, 1));
    x += remaining * Math.pow(u, power);
    y = PACK.y + TEAR.y + d.grabOffset + lineHeight(x - PACK.x);
  } else if (segment === 3) y -= 48 * clamp((t - 5.3) / 0.18);
  else if (segment === 4) {
    const angle = 2 * Math.PI * clamp((t - 7.2) / 3);
    x += 110 * 0.24 * Math.sin(angle);
    y -= 140 * 0.16 * Math.sin(2 * angle);
  }
  inputTear(s, { type: 'move', x, y, vx: (x - d.lastX) / dt, vy: (y - d.lastY) / dt });
  d.lastX = x;
  d.lastY = y;
}

/** Cancels the scripted recognizer before the real finger takes ownership. */
export function actEngine(engine: TearEngine, input: TearInput): TearEngine {
  'worklet';
  const sim = copyTear(engine.sim);
  const demo = { ...engine.demo, paused: true, quiet: 0 };
  if (!engine.demo.paused && sim.down) inputTear(sim, { type: 'up', vx: 0, vy: 0, cancelled: true });
  demo.segment = 0;
  if (input.type === 'down') demo.realDown = true;
  if (input.type === 'up') demo.realDown = false;
  inputTear(sim, input);
  return { sim, demo };
}

function resumeAt(s: TearState): number {
  'worklet';
  if (s.phase === 'sealed') return 0.8;
  if (s.phase === 'tearing') return 2.7;
  if (s.phase === 'peek') return 5;
  if (s.phase === 'revealed') return 9.9;
  return -1;
}

/** Accumulate real elapsed time; 60/120 Hz run the identical 600 Hz input/physics timeline. */
export function stepEngine(engine: TearEngine, elapsed: number, autoPlay: boolean): TearEngine {
  'worklet';
  const sim = copyTear(engine.sim);
  const demo = { ...engine.demo };
  demo.remainder += clamp(elapsed, 0, MOTION.maxElapsed);
  while (demo.remainder >= MOTION.step - 1e-9) {
    const dt = MOTION.step;
    if (demo.paused) {
      if (!demo.realDown) demo.quiet += dt;
      const next = resumeAt(sim);
      if (autoPlay && !demo.realDown && demo.quiet >= MOTION.resumeDelay && next >= 0) {
        demo.paused = false;
        demo.time = next;
        demo.segment = 0;
        sim.clock = next;
      }
    }
    if (autoPlay && !demo.paused) {
      finger(sim, demo, dt);
      tickTear(sim, dt);
      demo.time += dt;
      if (demo.time >= MOTION.loop - 1e-9) {
        demo.time = 0;
        demo.segment = 0;
        sim.clock = 0;
      }
    } else tickTear(sim, dt);
    demo.remainder = Math.max(0, demo.remainder - dt);
  }
  return { sim, demo };
}
