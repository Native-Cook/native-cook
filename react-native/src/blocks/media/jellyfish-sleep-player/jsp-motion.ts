/**
 * The clocks of the screen, stepped on the UI thread once per frame.
 *
 * In the loop every value is a pure function of the loop time t (`loopFrame`): the pulse (every 2 s, a quick 0.44 s
 * squeeze and a slow relax), the tentacle wave and mote clocks, and the three tracks crossfading 0.8 s each (rose →
 * cyan → amber → rose). A swipe, previous / next or play / pause take over at the frame on screen. 2 s after the last
 * touch the player plays again, crossfades back to the first track if needed and restarts the loop at t = 0 on the next
 * pulse, the wave and the motes carrying on from their phases. Reduce Motion: no bob, squeeze or wave, the motes still;
 * the lit animal breathes ± 8 % over 4 s and a track change crossfades in 0.3 s.
 */

/** Loop period, s. */
export const LOOP = 8.0;
const PULSE = 2.0;
const SQUEEZE = 0.22;
const CHANGE = 0.8;
const STILL_CHANGE = 0.3;
const PAUSE_PULSE = 0.6;
const PAUSE_WAVE = 1.0;
const PAUSE_DIM = 0.15;
const IDLE = 2.0;
/** The loop's track segments: [start, end, from, to]. */
const SEGMENTS = [[0, 2.4, 0, 0], [2.4, 3.2, 0, 1], [3.2, 5.0, 1, 1], [5.0, 5.8, 1, 2], [5.8, 7.2, 2, 2], [7.2, 8.0, 2, 0]];
/** When each track came in, in the loop (s). */
const CAME_IN = [0, 3.2, 5.8];

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function io(x: number) {
  'worklet';
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}

/** Contraction 0…1 at pulse time p. */
export function pulseAt(p: number) {
  'worklet';
  const f = (p % PULSE) / PULSE;
  if (f < SQUEEZE) {
    const s = Math.sin(((f / SQUEEZE) * Math.PI) / 2);
    return s * s;
  }
  const c = Math.cos((((f - SQUEEZE) / (1 - SQUEEZE)) * Math.PI) / 2);
  return c * c;
}

export type Frame = {
  /** Wave clock, mote clock (s), contraction 0…1. */
  T: number;
  M: number;
  C: number;
  /** Tracks crossfaded from `a` to `b` by `k`. */
  a: number;
  b: number;
  k: number;
  /** Wave amplitude and the lit animal's light multiplier. */
  wave: number;
  lit: number;
  /** Progress 0…1, and whether it plays. */
  prog: number;
  playing: number;
};

export function trackAt(t: number) {
  'worklet';
  for (let i = 0; i < SEGMENTS.length; i++) {
    const s = SEGMENTS[i];
    if (t >= s[0] && t < s[1]) return { a: s[2], b: s[3], k: s[2] === s[3] ? 0 : io(seg(t, s[0], s[1])), age: t - s[0] };
  }
  return { a: 0, b: 0, k: 0, age: 0 };
}

function progOf(k: number, inFor: number) {
  'worklet';
  return k > 0 ? 0 : Math.min(1, 0.12 + inFor / 30);
}

export function loopFrame(t: number, waveFrom = 0, motesFrom = 0): Frame {
  'worklet';
  const tr = trackAt(t);
  const inFor = (t - CAME_IN[tr.a] + LOOP) % LOOP;
  return {
    T: waveFrom + t, M: motesFrom + t, C: pulseAt(t), a: tr.a, b: tr.b, k: tr.k, wave: 1, lit: 1, prog: progOf(tr.k, inFor),
    playing: 1,
  };
}

export const MODE = { auto: 0, live: 1 } as const;

export type Core = {
  mode: number;
  last: number;
  start: number;
  /** Where the wave and mote clocks were when the loop restarted, s. */
  waveFrom: number;
  motesFrom: number;
  /** Reduce Motion. */
  still: boolean;
  /** Live clocks: pulse, wave, motes (s); the pulse's and the wave's share (0…1); the light multiplier's pause part. */
  P: number;
  T: number;
  M: number;
  amp: number;
  speed: number;
  dim: number;
  playing: boolean;
  /** Tracks: crossfading from `a` to `b`; `age` −1 when settled on `a`. */
  a: number;
  b: number;
  age: number;
  /** Changes asked for while one runs (net steps), started when it ends, so the colour never snaps. */
  pending: number;
  /** Seconds the current track has played. */
  inFor: number;
  touches: number;
  idle: number;
  /** Waiting for the next pulse to restart the loop. */
  homing: boolean;
  /** Events for the JS side: changes counts track changes by touch; target is where they lead; feedback holds the
   * target of each accepted change not yet handed to JS, in order (one tick and one announcement each). */
  changes: number;
  target: number;
  feedback: number[];
  out: Frame;
};

export function newCore(): Core {
  return {
    mode: MODE.auto, last: -1, start: -1, waveFrom: 0, motesFrom: 0, still: false, P: 0, T: 0, M: 0, amp: 1, speed: 1,
    dim: 0, playing: true, a: 0, b: 0, age: -1, pending: 0, inFor: 0, touches: 0, idle: 0, homing: false, changes: 0, target: 0,
    feedback: [], out: loopFrame(0),
  };
}

/** Leave the loop at the frame on screen. */
function takeOver(c: Core) {
  'worklet';
  if (c.mode !== MODE.auto) return;
  const t = c.start < 0 ? 0 : ((c.last - c.start) / 1000) % LOOP;
  c.mode = MODE.live;
  c.P = t;
  c.T = c.waveFrom + t;
  c.M = c.motesFrom + t;
  const tr = trackAt(t);
  c.a = tr.a;
  c.b = tr.b;
  c.age = tr.a === tr.b ? -1 : tr.age;
  c.inFor = (t - CAME_IN[tr.a] + LOOP) % LOOP;
  c.homing = false;
}

function changeTime(c: Core) {
  'worklet';
  return c.still ? STILL_CHANGE : CHANGE;
}

/** Next (+1) or previous (−1) track, wrapping through the three; during a change it queues after it. */
export function changeTrack(c: Core, dir: number) {
  'worklet';
  takeOver(c);
  c.idle = 0;
  c.homing = false;
  if (c.age >= 0) {
    c.pending += dir;
    c.target = (((c.b + c.pending) % 3) + 3) % 3;
  } else {
    c.b = (c.a + dir + 3) % 3;
    c.age = 0;
    c.target = c.b;
  }
  c.changes += 1;
  c.feedback.push(c.target);
}

/** The accepted changes since the last call, oldest first (their targets), so each gets its own feedback even when
 * several land before one frame. */
export function takeFeedback(c: Core): number[] {
  'worklet';
  if (c.feedback.length === 0) return c.feedback;
  const out = c.feedback;
  c.feedback = [];
  return out;
}

export function togglePlay(c: Core) {
  'worklet';
  takeOver(c);
  c.idle = 0;
  c.homing = false;
  c.playing = !c.playing;
}

export function touchDown(c: Core) {
  'worklet';
  takeOver(c);
  c.touches += 1;
  c.idle = 0;
  c.homing = false;
}

export function touchUp(c: Core) {
  'worklet';
  c.touches = Math.max(0, c.touches - 1);
  c.idle = 0;
}

function liveFrame(c: Core): Frame {
  'worklet';
  const k = c.age >= 0 ? io(clamp01(c.age / changeTime(c))) : 0;
  const breath = c.still ? 1 + 0.08 * Math.sin((2 * Math.PI * c.M) / 4) : 1;
  return {
    T: c.T, M: c.still ? 0 : c.M, C: c.still ? 0 : pulseAt(c.P) * c.amp, a: c.a, b: c.age >= 0 ? c.b : c.a, k,
    wave: c.still ? 0 : 1, lit: (1 - PAUSE_DIM * c.dim) * breath, prog: progOf(k, c.inFor), playing: c.playing ? 1 : 0,
  };
}

export function step(c: Core, now: number, autoPlay: boolean) {
  'worklet';
  const dt = c.last < 0 ? 0 : Math.min(Math.max((now - c.last) / 1000, 0), 0.05);
  c.last = now;

  if (c.mode === MODE.auto) {
    if (autoPlay && !c.still) {
      if (c.start < 0) c.start = now;
      c.out = loopFrame(((now - c.start) / 1000) % LOOP, c.waveFrom, c.motesFrom);
      return;
    }
    takeOver(c);
  }

  // Play / pause: the pulse eases to rest in 0.6 s, the wave slows to a stop in 1 s, the light dims 15 %.
  const dir = c.playing ? 1 : -1;
  c.amp = clamp01(c.amp + (dir * dt) / PAUSE_PULSE);
  c.speed = clamp01(c.speed + (dir * dt) / PAUSE_WAVE);
  c.dim = clamp01(c.dim - (dir * dt) / PAUSE_PULSE);
  const beforeP = c.P;
  if (c.playing || c.amp > 0) c.P += dt;
  c.T += dt * c.speed;
  c.M += dt;
  if (c.playing) c.inFor += dt;
  if (c.age >= 0) {
    c.age += dt;
    if (c.age >= changeTime(c)) {
      c.a = c.b;
      c.age = -1;
      c.inFor = 0;
      const next = (((c.a + c.pending) % 3) + 3) % 3;
      c.pending = 0;
      if (next !== c.a) {
        c.b = next;
        c.age = 0;
      }
    }
  }
  c.out = liveFrame(c);

  if (c.touches > 0) {
    c.idle = 0;
    return;
  }
  c.idle += dt;
  if (!autoPlay || c.still || c.idle < IDLE) return;
  // Back to the loop: play, the first track, then t = 0 on the next pulse.
  if (!c.playing) c.playing = true;
  if (c.age < 0 && c.a !== 0) {
    c.b = 0;
    c.age = 0;
  }
  const settled = c.age < 0 && c.a === 0 && c.amp >= 1 && c.speed >= 1 && c.dim <= 0;
  if (!settled) return;
  const wrapped = Math.floor(c.P / PULSE) !== Math.floor(beforeP / PULSE);
  if (!wrapped) return;
  const over = c.P - PULSE * Math.floor(c.P / PULSE);
  c.mode = MODE.auto;
  c.start = now - over * 1000;
  c.waveFrom = c.T - over;
  c.motesFrom = c.M - over;
  c.out = loopFrame(over, c.waveFrom, c.motesFrom);
}

export function setStill(c: Core, still: boolean) {
  'worklet';
  if (still === c.still) return;
  c.still = still;
  if (still) {
    takeOver(c);
  } else if (c.mode === MODE.live) {
    c.idle = IDLE;
  }
}
