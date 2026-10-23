// Shared by the native block and the offline CanvasKit proofs. Units are points / milliseconds.

export const D = {
  width: 402, height: 778, inset: 24, headerTop: 12, headerHeight: 28,
  stageTop: 96, stageHeight: 320, centreY: 264, card: 192, radius: 20, guard: 48,
  pagerY: 408, dot: 6, dotActive: 24, dotGap: 8,
  overlineTop: 456, overlineHeight: 16, titleTop: 480, titleHeight: 40,
  bodyTop: 532, bodyHeight: 44, bodyWidth: 300, speakerTop: 600, speakerSize: 36,
  speakerGap: 12, buttonTop: 706, buttonHeight: 56, buttonWidth: 354, buttonRadius: 28,
  icon: 20, iconStroke: 2.25, buttonGap: 8, buttonOpticalX: 4, canvasCount: 12,
  type: {
    header: 13, date: 12, label: 26, overline: 12, title: 32, body: 15,
    name: 15, role: 13, initials: 13, button: 17
  },
  tracking: { header: 1.6, overline: 1.2, title: -0.6, label: -0.5 },
  shadow: { y: 8, blur: 16, opacity: 0.5 },
  glow: {
    tightScale: 1.02, tightBlur: 10, tightOpacity: 0.55,
    wideScale: 1.10, wideBlur: 34, wideOpacity: 0.26, bloomScale: 0.08, pulse: 0.8, edgeFade: 32
  },
  reflect: { gap: 6, height: 36, blur: 3, opacity: 0.26 },
} as const;

export const tone = {
  page: '#000000', ink: '#FFFFFF', dim: '#8E8E99', glass: '#131316',
  hairline: 'rgba(255,255,255,0.10)', label: '#0B0B10', dot: 'rgba(255,255,255,0.22)'
} as const;

export const T = {
  loop: 13333, beat: 13333 / 4, flow: 1, surge: 2800, resume: 3500, settle: 220,
  press: 120, reveal: 200, arrival: 900, follow: 420, sheenPass: 500, morph: 320, fade: 150,
  velocityTau: 70,
  move: 2400, anticipate: 130, whip: 800, add: 700, clear: 3000,
  uiCurve: [0.23, 1, 0.32, 1] as const
} as const;

// Where in its palette cycle each card (WELCOME, 2ND DAY, EXPERIENCE, KEYNOTE) starts. Fitted to the
// reference recording, so the same rings are born at about the same moments after a card arrives.
export const HOLO_PHASES = [0.84, 0.55, 0.56, 0.904] as const;

export const M = {
  drag: 124, press: 0.97, perspective: 700, tilt: 2.2, tiltMax: 11,
  velocityMax: 8, project: 0.16, maxFling: 2, flingVelocity: 7, flingDistance: 1.6,
  spring: { stiffness: 210, damping: 26, mass: 1 }
} as const;

export const sessions = [
  {
    label: 'WELCOME', overline: 'DAY 1 · 09:00', title: 'Opening welcome',
    body: 'Doors, coffee and a first look at what the next two days hold.',
    name: 'Mira Solène', role: 'Host, Iridia', initials: 'MS'
  },
  {
    label: '2ND DAY', overline: 'DAY 2 · 09:30', title: 'Second day kickoff',
    body: 'A recap of day one, then straight into the workshops.',
    name: 'Kenji Marlow', role: 'Programme lead', initials: 'KM'
  },
  {
    label: 'EXPERIENCE', overline: 'DAY 2 · 14:00', title: 'The experience lab',
    body: 'Hands-on stations: light, sound and motion you can touch.',
    name: 'Ines Okoro', role: 'Studio director', initials: 'IO'
  },
  {
    label: 'KEYNOTE', overline: 'DAY 2 · 17:30', title: 'Closing keynote',
    body: 'One idea, forty minutes, and the reason we all came.',
    name: 'Avery Lindqvist', role: 'Founder', initials: 'AL'
  },
] as const;

export function clamp(n: number, lo = 0, hi = 1) {
  'worklet';
  return Math.max(lo, Math.min(hi, n));
}

export function mod(n: number, divisor = 4) {
  'worklet';
  return ((n % divisor) + divisor) % divisor;
}

export function wrap(n: number) {
  'worklet';
  return mod(n + 2) - 2;
}

export function smooth(lo: number, hi: number, n: number) {
  'worklet';
  const t = clamp((n - lo) / (hi - lo));
  return t * t * (3 - 2 * t);
}
// Hermite tangents remove visible velocity changes at half-card knots without overshooting them.

function curve(a: number, values: readonly number[], slopes: readonly number[]) {
  'worklet';
  const j = Math.min(3, Math.floor(a * 2));
  const t = clamp(a * 2 - j);
  const t2 = t * t;
  const t3 = t2 * t;
  return (2 * t3 - 3 * t2 + 1) * values[j] + (t3 - 2 * t2 + t) * slopes[j] / 2
    + (-2 * t3 + 3 * t2) * values[j + 1] + (t3 - t2) * slopes[j + 1] / 2;
}

export function cardPose(d: number) {
  'worklet';
  const a = Math.abs(d);
  return {
    x: Math.sign(d) * curve(a, [0, 80, 117, 100, 56], [180, 101, 0, -49, -88]),
    side: curve(a, [192, 164, 136, 120, 104], [0, -56, -40, -32, -32]),
    opacity: 1 - smooth(1.6, 1.95, a),
    // Nearer the centre is further forward; a tie goes to the card on the right.
    z: Math.round((2 - a) * 1000) * 2 + (d >= 0 ? 1 : 0)
  };
}
// A deterministic cubic-bezier solver, also executed by the proof script (no runtime dependency).

export function bezier(x: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  x = clamp(x);
  let lo = 0;
  let hi = 1;
  let t = x;
  for (let i = 0; i < 18; i++) {
    const inv = 1 - t;
    const bx = 3 * inv * inv * t * x1 + 3 * inv * t * t * x2 + t * t * t;
    if (bx < x)
      lo = t;
    else
      hi = t;
    t = (lo + hi) / 2;
  }
  const inv = 1 - t;
  return x === 0 || x === 1 ? x : 3 * inv * inv * t * y1 + 3 * inv * t * t * y2 + t * t * t;
}

export function autoplayAt(ms: number) {
  'worklet';
  const loops = Math.floor(ms / T.loop);
  const time = mod(ms, T.loop);
  const beat = Math.floor(time / T.beat);
  const local = time - beat * T.beat;
  let advance = 0;
  let squeeze = 1;
  // Every step is the reference's: a short gathering, then a whip that eases out with no overshoot.
  if (local >= T.move) {
    if (local < T.move + T.anticipate) {
      const p = smooth(0, 1, (local - T.move) / T.anticipate);
      advance = 0.03 * p;
      squeeze = 1 - 0.03 * p;
    }
    else {
      // Fitted to the incoming card's position in the reference, frame by frame.
      const p = bezier((local - T.move - T.anticipate) / T.whip, 0.04, 0.85, 0.55, 1);
      advance = 0.03 + 0.97 * p;
      squeeze = M.press + 0.03 * p;
    }
  }
  const sincePress = local - T.add;
  const buttonScale = beat === 2 && sincePress >= 0 && sincePress < T.press * 2
    ? 1 - 0.03 * Math.sin(sincePress / (T.press * 2) * Math.PI) : 1;
  return {
    pos: loops * 4 + beat + advance, squeeze, buttonScale,
    demoAdded: (beat === 2 && local >= T.add + T.press) || (beat === 3 && local < T.clear), time
  };
}

export function holoLayout(width: number, height: number, top: number, bottom: number) {
  const k = Math.min(1, width / D.width, (height - top - bottom) / D.height);
  const grid = (n: number) => Math.round(n * k / 4) * 4;
  return {
    k, width: D.width * k, left: (width - D.width * k) / 2, top,
    header: grid(D.headerTop), stage: grid(D.stageTop), centre: grid(D.centreY),
    pager: grid(D.pagerY), overline: grid(D.overlineTop), title: grid(D.titleTop),
    body: grid(D.bodyTop), speaker: grid(D.speakerTop), button: grid(D.buttonTop)
  };
}
// Fixed samples make the self-playing material independent of display refresh rate.
// Manual motion still uses the real per-frame derivative in use-holo-pager.
const crossings: number[] = [];
for (let beat = 0; beat < 4; beat++) {
  let lo = beat * T.beat;
  let hi = (beat + 1) * T.beat;
  for (let j = 0; j < 32; j++) {
    const mid = (lo + hi) / 2;
    if (autoplayAt(mid).pos < beat + 0.5)
      lo = mid;
    else
      hi = mid;
  }
  crossings.push((lo + hi) / 2);
}

export function autoplayDynamics(ms: number) {
  'worklet';
  const time = mod(ms, T.loop);
  const current = autoplayAt(ms);
  let velocity = 0;
  let weightTotal = 0;
  for (let j = 0; j < 14; j++) {
    const lag = j * 25;
    const weight = Math.exp(-lag / T.velocityTau);
    const sample = (autoplayAt(ms - lag).pos - autoplayAt(ms - lag - 8).pos) * 125;
    velocity += clamp(sample, -M.velocityMax, M.velocityMax) * weight;
    weightTotal += weight;
  }
  velocity /= weightTotal;
  let lastCrossing = crossings[3] - T.loop;
  for (let j = 0; j < 4; j++)
    if (time >= crossings[j])
      lastCrossing = crossings[j];
  const arrival = 1 - bezier(clamp((time - lastCrossing) / T.arrival), ...T.uiCurve);
  return { ...current, velocity, arrival, arrivalAge: Math.min(T.arrival, time - lastCrossing) };
}
// The touch-down card owns the release cap, even if the finger interrupted a transition.

export function releaseTarget(position: number, start: number, velocity: number) {
  'worklet';
  const origin = Math.round(start);
  const projected = position + velocity * M.project;
  const limit = Math.abs(velocity) > M.flingVelocity
    && Math.abs(projected - origin) > M.flingDistance ? M.maxFling : 1;
  return clamp(Math.round(projected), origin - limit, origin + limit);
}

export function arrivalScale(age: number) {
  'worklet';
  return 1 + 0.02 * Math.sin(Math.PI * clamp(age / T.follow));
}
/**
 * A card's place in the stack: 0 is the farthest, 3 the one in front. Used as its zIndex, so the
 * native order changes only when two cards actually swap (twice per step), not on every frame.
 */
export function cardDepth(position: number, index: number) {
  'worklet';
  const z = cardPose(wrap(index - position)).z;
  let depth = 0;
  for (let other = 0; other < 4; other++) {
    if (cardPose(wrap(other - position)).z < z)
      depth++;
  }
  return depth;
}

// Four stable depth slots: only drawings inside the same canvas switch their material.

export function cardAtDepth(position: number, depth: number) {
  'worklet';
  for (let index = 0; index < 4; index++) {
    const z = cardPose(wrap(index - position)).z;
    let rank = 0;
    for (let other = 0; other < 4; other++) {
      if (cardPose(wrap(other - position)).z < z)
        rank++;
    }
    if (rank === depth)
      return index;
  }
  return 0;
}
