import { LAND_MS, LOOP, SIGNATURE_MS, signaturePose } from './HabitatMotion';

/**
 * A rig for flat cut-out art: no extra drawings, only regions of the one PNG that bend around
 * their own pivot (CREATURE_LIFE inverts the warp per pixel). Coordinates are pixels of the
 * 512×512 source art. Swapping in another mascot means another PNG and another one of these.
 */
type Sway = readonly [degrees: number, cyclesPerLoop: number, phase: number];
export type Joint = {
  pivot: readonly [number, number];
  center: readonly [number, number];
  radius: number;
  sway: readonly Sway[];
  /** A twitch: degrees and the loop seconds where it fires. */
  twitch?: { degrees: number; at: readonly number[] };
};
export type Rig = {
  /** Where the feet meet the ground; squash, lean and hops pivot here. */
  feet: number;
  chest: readonly [x: number, y: number, radius: number];
  /** Where the legs meet the body: above it the weight shifts sideways, below it the legs hang in the air. */
  hips: number;
  /** Joint 0 is always the head: it also tilts towards the finger and rights itself after a lean. */
  joints: readonly Joint[];
  /** Eye boxes (cx, cy, half width, half height) the blink squashes the face around; rx 0 is absent. */
  eyes: readonly (readonly [number, number, number, number])[];
  /**
   * Loop seconds of every blink; two close together make a double blink. Each creature blinks as
   * it lands (the jolt of the hop), and its acting blinks fill its move, so none come in a cluster.
   */
  blinks: readonly number[];
  /**
   * How this creature blinks. The lids follow each eye's drawn outline (baked in `<name>-lids.png`):
   * the upper lid falls with the top lash line, the lower one rises `lower` of the way to meet it,
   * and the skin around the eye squashes towards the closing line. Seconds to close, stay shut and
   * reopen; lash width (art px) and ink; how much of a lower lash line shows; squash of the face.
   */
  lids: { close: number; hold: number; open: number; lower: number; lash: number; ink: readonly [number, number, number]; lowerInk: number; squash: number };
  /** Where the signature move lights the body from, its reach and colour. */
  glow: { at: readonly [number, number]; radius: number; color: readonly [number, number, number] };
  /**
   * The face, for a 2.5D head turn: its centre and radius, and how far its middle slides (art px)
   * at a full turn. The outline stays put and the middle slides, so the head reads as turning.
   */
  face: { at: readonly [number, number]; radius: number; shift: number };
  /**
   * A drawn flame that is always drawn live (CREATURE_LIFE): its base, its height, and the
   * ellipse that holds it (only flame colours inside it, down to the tail tip it burns from), and
   * the box (art px) of its baked distance map (`assets/bake-flame.py`), the blaze's outline.
   */
  flame?: { base: readonly [number, number]; height: number; at: readonly [number, number]; radius: readonly [number, number]; map: readonly [number, number, number, number] };
  /**
   * Acting around the signature move, in seconds of its effect clock (FX): the head turns and
   * tilts to watch what the move throws, and blinks on each turn. Read with `flow`.
   */
  acting: { yaw: readonly FlowKey[]; tilt: readonly FlowKey[]; blinks: readonly number[]; accents: readonly Accent[] };
  /** Phase offset, so three creatures never breathe in step. */
  phase: number;
};
/** A pose key for `flow`: seconds, value. */
export type FlowKey = readonly [seconds: number, value: number];
/**
 * A joint's accent during the move: degrees, switched on and off over a window of the effect clock
 * (rise from, rise to, fall from, fall to), as a held bend (hz 0) or a beat (hz, phase).
 */
type Accent = { joint: number; degrees: number; window: readonly [number, number, number, number]; hz: number; phase: number };

export const RIGS: readonly Rig[] = [
  { // Charizard: wings beat out of step, the tail flame flickers on two periods.
    feet: 495, chest: [256, 392, 96], hips: 440, phase: 0,
    joints: [
      { pivot: [268, 300], center: [245, 150], radius: 88, sway: [[1.6, 3, 0.1]] },
      { pivot: [226, 276], center: [95, 245], radius: 122, sway: [[2.6, 4, 0], [0.9, 7, 0.3]] },
      { pivot: [292, 280], center: [420, 250], radius: 122, sway: [[-2.4, 4, 0.37], [-0.8, 7, 0.71]] },
      { pivot: [396, 446], center: [452, 372], radius: 62, sway: [[3.4, 18, 0.2], [2.0, 12, 0.55]] },
    ],
    eyes: [[241, 138.5, 13, 6.5], [0, 0, 0, 0]],
    blinks: [0.06, 4.6, 8.1, 8.42, 11.6],
    // A predator's blink: quick and sharp, almost all upper lid.
    lids: { close: 0.075, hold: 0.03, open: 0.17, lower: 0.15, lash: 2.4, ink: [0.12, 0.07, 0.05], lowerInk: 0, squash: 0.1 },
    glow: { at: [452, 380], radius: 150, color: [1, 0.55, 0.2] },
    face: { at: [250, 140], radius: 52, shift: 5 },
    flame: { base: [452, 402], height: 110, at: [451, 345], radius: [38, 68], map: [408, 272, 96, 140] },
    // Turns to its tail as the flame swells, watches the embers rise up and away, settles.
    acting: {
      yaw: [[0, 0], [0.18, 0], [0.55, 0.85], [1.15, 0.85], [1.7, 0.2], [2.3, -0.25], [3.2, -0.1], [3.9, 0]],
      tilt: [[0, 0], [0.5, -2], [1.2, -3], [2, -4], [2.8, -1], [3.9, 0]],
      blinks: [0.2, 1.72],
      // The wings beat as the flame swells, the far one a beat behind.
      accents: [
        { joint: 1, degrees: 4, window: [0.2, 0.45, 1.1, 1.6], hz: 2.2, phase: 0 },
        { joint: 2, degrees: -3.5, window: [0.2, 0.45, 1.1, 1.6], hz: 2.2, phase: 0.12 },
      ],
    },
  },
  { // Blastoise: a heavy body, so its head and arm move least and slowest.
    feet: 500, chest: [300, 330, 112], hips: 405, phase: 0.31,
    joints: [
      { pivot: [300, 166], center: [320, 104], radius: 78, sway: [[1.8, 3, 0.4]] },
      { pivot: [372, 252], center: [432, 292], radius: 62, sway: [[3.0, 3, 0.6], [0.8, 6, 0.1]] },
      { pivot: [112, 262], center: [88, 266], radius: 26, sway: [[-3.0, 3, 0.85]] },
      { pivot: [0, 0], center: [0, 0], radius: 0, sway: [] },
    ],
    eyes: [[319.5, 95, 20.5, 11], [0, 0, 0, 0]],
    blinks: [2.1, 4.58, 7.25, 7.6, 12.6],
    // A heavy, unhurried blink: the lid lingers shut and lifts slowly.
    lids: { close: 0.11, hold: 0.07, open: 0.26, lower: 0.22, lash: 2, ink: [0.07, 0.1, 0.18], lowerInk: 0.6, squash: 0.12 },
    glow: { at: [409, 196], radius: 140, color: [0.5, 0.85, 1] },
    face: { at: [318, 100], radius: 58, shift: 6 },
    // Braces towards the higher jet while both fire, then looks down at the spray landing.
    acting: {
      yaw: [[0, 0], [0.15, 0], [0.4, -0.45], [1.05, -0.45], [1.5, 0.15], [2.1, 0.4], [3, 0.3], [3.9, 0]],
      tilt: [[0, 0], [0.3, 1.5], [1, 1.5], [1.6, 3], [2.6, 3], [3.9, 0]],
      blinks: [0.4, 1.5],
      // Both arms brace against the recoil while the cannons fire.
      accents: [
        { joint: 1, degrees: 3, window: [0.2, 0.32, 1, 1.4], hz: 0, phase: 0 },
        { joint: 2, degrees: -3, window: [0.24, 0.36, 1, 1.45], hz: 0, phase: 0 },
      ],
    },
  },
  { // Bulbasaur: the bulb sways and its tip follows a beat later; one ear twitches now and then.
    feet: 492, chest: [292, 420, 122], hips: 432, phase: 0.62,
    joints: [
      { pivot: [258, 432], center: [190, 300], radius: 152, sway: [[1.3, 3, 0.15]] },
      { pivot: [330, 236], center: [345, 168], radius: 96, sway: [[2.6, 3, 0.3]] },
      { pivot: [350, 162], center: [366, 146], radius: 42, sway: [[3.2, 3, 0.45]] },
      { pivot: [116, 240], center: [106, 214], radius: 27, sway: [[1.5, 6, 0.2]], twitch: { degrees: -9, at: [2.6, 9.9] } },
    ],
    eyes: [[86.5, 298, 19.5, 31], [250.5, 307.5, 40.5, 30.5]],
    blinks: [0.4, 3.9, 6.6, 9.06, 12.2],
    // A soft, round blink: the cheeks push the lower lids well up, so it shuts with a smile.
    lids: { close: 0.09, hold: 0.05, open: 0.22, lower: 0.3, lash: 3.2, ink: [0.11, 0.26, 0.23], lowerInk: 0.6, squash: 0.08 },
    glow: { at: [377, 152], radius: 190, color: [0.8, 1, 0.45] },
    face: { at: [172, 300], radius: 132, shift: 9 },
    // Looks up the beam as it fires, then down at the leaves as they fall and settle.
    acting: {
      yaw: [[0, 0], [0.3, 0], [0.6, 0.5], [1.3, 0.5], [1.8, -0.3], [2.7, -0.45], [3.4, -0.2], [3.9, 0]],
      tilt: [[0, 0], [0.5, 1.5], [1.3, 2], [2, -1.5], [2.8, -2], [3.9, 0]],
      blinks: [0.62, 1.85],
      // The bulb leans back as the beam pushes off it, and its crown flutters after.
      accents: [
        { joint: 1, degrees: -2, window: [0.5, 0.62, 1.2, 1.6], hz: 0, phase: 0 },
        { joint: 2, degrees: 3, window: [0.55, 0.68, 1.3, 1.9], hz: 3, phase: 0.2 },
      ],
    },
  },
];

const TAU = Math.PI * 2;

/**
 * Acting curve from rossark-motion-skill's engine: passes through every key with a continuous speed
 * and never overshoots one (monotone cubic), so a chain of poses never stops dead at a key.
 */
export function flow(t: number, k: readonly FlowKey[]): number {
  'worklet';
  const n = k.length;
  if (t <= k[0][0]) return k[0][1];
  if (t >= k[n - 1][0]) return k[n - 1][1];
  let i = 0;
  while (k[i + 1][0] < t) i++;
  const slope = (j: number) => (k[j + 1][1] - k[j][1]) / (k[j + 1][0] - k[j][0]);
  const tangent = (j: number) => {
    if (j === 0 || j === n - 1) return 0;
    const a = slope(j - 1);
    const b = slope(j);
    if (a * b <= 0) return 0;
    const w0 = 2 * (k[j + 1][0] - k[j][0]) + (k[j][0] - k[j - 1][0]);
    const w1 = (k[j + 1][0] - k[j][0]) + 2 * (k[j][0] - k[j - 1][0]);
    return (w0 + w1) / (w0 / a + w1 / b);
  };
  const h = k[i + 1][0] - k[i][0];
  const s = (t - k[i][0]) / h;
  const s2 = s * s;
  const s3 = s2 * s;
  return (2 * s3 - 3 * s2 + 1) * k[i][1] + (s3 - 2 * s2 + s) * h * tangent(i) + (-2 * s3 + 3 * s2) * k[i + 1][1] + (s3 - s2) * h * tangent(i + 1);
}
/** 0 → 1 over [a, b], held, then back to 0 over [c, d] (SIGNATURE_EFFECT's `window`). */
function rises(x: number, a: number, b: number, c: number, d: number) {
  'worklet';
  const rise = Math.max(0, Math.min(1, (x - a) / (b - a)));
  const fall = Math.max(0, Math.min(1, (x - c) / (d - c)));
  return rise * rise * (3 - 2 * rise) * (1 - fall * fall * (3 - 2 * fall));
}
/** Seconds into a hold of the contented double blink, and how much slower than a plain blink it is. */
const PET_BLINKS = [0.22, 0.78] as const;
const PET_SLOW = 2.2;
const RAD = Math.PI / 180;

/**
 * Eyelid closure (0 open, 1 shut) as a real lid moves: it falls faster and faster and stops dead
 * when the lids meet, rests, then lifts quickly at first and settles slowly open.
 */
export function lidAt(seconds: number, blinks: readonly number[], close: number, hold: number, open: number) {
  'worklet';
  let lid = 0;
  for (let i = 0; i < blinks.length; i++) {
    let d = seconds - blinks[i];
    if (d < -LOOP / 2) d += LOOP;
    if (d > LOOP / 2) d -= LOOP;
    let v = 0;
    if (d >= 0 && d < close) v = Math.pow(d / close, 1.6);
    else if (d >= close && d < close + hold) v = 1;
    else if (d >= close + hold && d < close + hold + open) v = Math.pow(1 - (d - close - hold) / open, 2.4);
    lid = Math.max(lid, v);
  }
  return lid;
}

/** A twitch: a quick turn out (90 ms) and a slower return (260 ms). */
function twitchAt(seconds: number, at: readonly number[]) {
  'worklet';
  let v = 0;
  for (let i = 0; i < at.length; i++) {
    const d = seconds - at[i];
    if (d >= 0 && d < 0.09) v = Math.max(v, Math.sin((d / 0.09) * Math.PI / 2));
    else if (d >= 0.09 && d < 0.35) { const u = 1 - (d - 0.09) / 0.26; v = Math.max(v, u * u * (3 - 2 * u)); }
  }
  return v;
}

/** Height of the incoming specimen's hop across a crossing, as a fraction of its size; it lands at f = 0.92. */
export const HOP_HEIGHT = 0.11;
export function hopLift(f: number) {
  'worklet';
  const u = Math.max(0, Math.min(1, (f - 0.3) / 0.62));
  return 4 * u * (1 - u);
}
/** Contact squash after a landing (0 → 1 over LAND_MS): a fast squash, one overshoot, then rest. */
export function landEnvelope(progress: number) {
  'worklet';
  if (progress <= 0 || progress >= 1) return 0;
  const s = progress * (LAND_MS / 1000);
  if (s < 0.05) { const u = s / 0.05; return u * u * (3 - 2 * u); }
  return Math.exp(-(s - 0.05) * 7.5) * Math.cos((s - 0.05) * 15);
}

export type LifeInput = {
  /** Ambient loop time 0..1 (frozen under Reduced Motion). */
  t: number;
  reduced: boolean;
  role: number;
  f: number;
  press: number;
  look: number;
  pet: number;
  /** Seconds since a hold began. */
  petClock: number;
  /** Landing squash envelope for this creature, -1..1. */
  land: number;
  /** Signature progress for this creature, 0 outside its move. */
  signature: number;
  /** Seconds of this creature's move's effect clock (it runs on while the particles land); -1 when idle. */
  fx: number;
  index: number;
};

/**
 * Every channel of one creature for one frame: key poses come from the exchange, the press and
 * the signature move; an idle layer (breath, sway, weight, head) keeps even a held pose alive;
 * the head follows the body's lean 110 ms late and the bulb tip follows its bulb.
 */
export function lifePose(input: LifeInput) {
  'worklet';
  const rig = RIGS[input.index];
  const live = input.reduced ? 0 : 1;
  const seconds = input.t * LOOP;
  const wave = (cycles: number, phase: number, t = input.t) => Math.sin(TAU * (t * cycles + phase + rig.phase));
  const leanAt = (t: number) => 0.7 * wave(3, 0.2, t);
  const lean = live * (leanAt(input.t) + input.pet * 1.6 * Math.sin(input.petClock * TAU * 1.6)) * RAD;
  const acting = input.fx >= 0;
  const angles: number[] = [];
  for (let j = 0; j < 4; j++) {
    const joint = rig.joints[j];
    let degrees = 0;
    for (let k = 0; k < joint.sway.length; k++) degrees += joint.sway[k][0] * wave(joint.sway[k][1], joint.sway[k][2]);
    if (joint.twitch) degrees += joint.twitch.degrees * twitchAt(seconds, joint.twitch.at);
    if (acting) {
      for (let a = 0; a < rig.acting.accents.length; a++) {
        const accent = rig.acting.accents[a];
        if (accent.joint !== j) continue;
        const [r0, r1, f0, f1] = accent.window;
        degrees += accent.degrees * rises(input.fx, r0, r1, f0, f1) * (accent.hz > 0 ? Math.sin(TAU * (input.fx * accent.hz + accent.phase)) : 1);
      }
    }
    if (j === 0) {
      // The head rights itself a beat after the body leans, looks towards the finger, tilts while
      // petted, and tilts to watch what its move throws.
      degrees += -0.45 * leanAt(input.t - 0.11 / LOOP) + input.look * 5 + input.pet * 4 + (acting ? flow(input.fx, rig.acting.tilt) : 0);
    }
    angles.push(live * degrees * RAD);
  }
  const breath = live * 0.012 * (0.5 + 0.5 * wave(4, 0));
  const lift = live * (input.role === 1 ? hopLift(input.f) : 0);
  const signature = signaturePose(input.signature, input.index);
  // Height changes keep the volume: width scales by 1 / sy^0.6.
  const wiggle = input.pet * (0.5 + 0.5 * Math.sin(input.petClock * TAU * 3.2));
  const air = live * 0.05 * Math.sin(Math.PI * Math.min(1, lift));
  const squash = (1 - 0.035 * input.press) * (1 - 0.15 * input.land) * (1 - 0.03 * live * wiggle) * (1 + air) * (1 + 0.005 * live * wave(4, 0.1));
  return {
    sy: squash * signature.sy,
    sx: signature.sx / Math.pow(squash, 0.6),
    lean,
    tx: live * signature.tx,
    ty: live * signature.ty,
    lift,
    angles,
    headShift: live * input.look * 4,
    // The head turns (2.5D): a slow glance that never quite stops, and the move's acting turns.
    yaw: live * (0.2 * wave(2, 0.15) + (acting ? flow(input.fx, rig.acting.yaw) : 0) + input.look * 0.35),
    breath,
    // The weight drifts from one foot to the other (art px, the feet stay planted), and in the air
    // the legs hang a little longer, as a body held up by nothing does.
    shift: live * 4 * wave(3, 0.35),
    hang: 0.07 * lift,
    // A petted creature answers with a slow, contented double blink, as a cat does. It also blinks
    // on each acting turn.
    lid: input.reduced ? 0 : Math.max(
      lidAt(seconds, rig.blinks, rig.lids.close, rig.lids.hold, rig.lids.open),
      input.pet > 0 ? lidAt(input.petClock, PET_BLINKS, rig.lids.close * PET_SLOW, 0.22, rig.lids.open * PET_SLOW) : 0,
      acting ? lidAt(input.fx, rig.acting.blinks, rig.lids.close, rig.lids.hold, rig.lids.open) : 0,
    ),
    // The live tail flame: it swells into a plume during the move, and stretches 3-6% on two waves
    // (about 0.7 s and 1.1 s) the rest of the time.
    flameGrow: acting ? rises(input.fx / (SIGNATURE_MS / 1000), 0.1, 0.28, 0.6, 0.96) : 0,
    flameStretch: live * (0.03 * wave(19, 0.1) + 0.02 * wave(12, 0.4)),
  };
}

/** Where one creature stands this frame: its layout, its place in the exchange and its pose. */
export type BodyFrame = {
  index: number;
  /** Art square and the transparent margin around it, in points. */
  size: number;
  pad: number;
  /** Art square's top-left inside the hero, the hero's top, and the screen. */
  left: number;
  top: number;
  heroTop: number;
  width: number;
  screenHeight: number;
  scale: number;
  exchange: { role: number; f: number; drift: number; scale: number };
  driftPx: number;
  pose: ReturnType<typeof lifePose>;
};
export type LifeFrame = BodyFrame & {
  phase: number;
  /** Element of the incoming habitat. */
  element: number;
  /** Key light direction and how much of the baked volume shows. */
  light: readonly [number, number, number];
  tint: readonly [number, number, number];
  glow: number;
  /** Ambient loop seconds (the live flame's flicker loops with it) and Reduced Motion. */
  seconds: number;
  reduced: boolean;
};

/** The body's pose as one affine about the feet, and where its canvas sits on screen. */
function placement(frame: BodyFrame) {
  'worklet';
  const { size, pad, pose, exchange } = frame;
  const k = size / 512;
  const fx = size / 2;
  const fy = RIGS[frame.index].feet * k;
  const view = size + pad * 2;
  const z = exchange.scale;
  return {
    k, fx, fy, z,
    ox: pad + fx + pose.tx * frame.scale,
    oy: pad + fy + pose.ty * frame.scale - pose.lift * HOP_HEIGHT * size,
    c: Math.cos(pose.lean),
    s: Math.sin(pose.lean),
    origin: [frame.left - pad + (view / 2) * (1 - z) + exchange.drift * frame.driftPx, frame.heroTop + frame.top - pad + (view / 2) * (1 - z)] as const,
  };
}

function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = Math.max(0, Math.min(1, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

/**
 * Where a point of the source art is on screen this frame: CREATURE_LIFE's warp run forwards
 * (the rig's bends, the breath and the head's shift, then the body's pose). Effects that leave the
 * body (the tail flame, the cannons, the bulb) start here, so they stay attached as it moves.
 */
export function bodyPoint(frame: BodyFrame, x: number, y: number): [number, number] {
  'worklet';
  const rig = RIGS[frame.index];
  const { k, fx, fy, z, ox, oy, c, s, origin } = placement(frame);
  const { pose } = frame;
  const ax = x * k;
  const ay = y * k;
  let dx = 0;
  let dy = 0;
  for (let j = 0; j < 4; j++) {
    const joint = rig.joints[j];
    if (joint.radius <= 0) continue;
    const r = joint.radius * k;
    const w = 1 - smooth(r * 0.45, r * 1.1, Math.hypot(ax - joint.center[0] * k, ay - joint.center[1] * k));
    if (w <= 0) continue;
    const t = pose.angles[j] * w;
    const px = ax - joint.pivot[0] * k;
    const py = ay - joint.pivot[1] * k;
    dx += Math.cos(t) * px - Math.sin(t) * py - px;
    dy += Math.sin(t) * px + Math.cos(t) * py - py;
    if (j === 0) dx += pose.headShift * frame.scale * w;
  }
  const face = rig.face;
  dx += pose.yaw * face.shift * k * (1 - smooth(face.radius * k * 0.15, face.radius * k * 0.95, Math.hypot(ax - face.at[0] * k, ay - face.at[1] * k)));
  const chest = 1 - smooth(rig.chest[2] * k * 0.3, rig.chest[2] * k * 1.25, Math.hypot(ax - rig.chest[0] * k, ay - rig.chest[1] * k));
  dx += (ax - rig.chest[0] * k) * 0.5 * pose.breath * chest;
  dy += (ay - fy) * pose.breath * chest;
  const hips = rig.hips * k;
  dx += pose.shift * k * (1 - smooth(hips - 10 * k, fy, ay));
  dy += Math.max(ay - hips, 0) * pose.hang;
  const u = (ax + dx - fx) * pose.sx;
  const v = (ay + dy - fy) * pose.sy;
  return [origin[0] + (ox + c * u - s * v) * z, origin[1] + (oy + s * u + c * v) * z];
}

/**
 * A point held to an anchor of the body by the warp linearised there: the anchor follows the warp
 * (`bodyPoint`), the offset (art px) turns, leans and stretches as the body does at the anchor, as
 * CREATURE_LIFE draws the plume, without the far-reaching bends of other joints.
 */
export function bodyPointFrom(frame: BodyFrame, x: number, y: number, offsetX: number, offsetY: number): [number, number] {
  'worklet';
  const p = bodyPoint(frame, x, y);
  const px = bodyPoint(frame, x + 1, y);
  const py = bodyPoint(frame, x, y + 1);
  return [p[0] + (px[0] - p[0]) * offsetX + (py[0] - p[0]) * offsetY, p[1] + (px[1] - p[1]) * offsetX + (py[1] - p[1]) * offsetY];
}

/** CREATURE_LIFE's uniforms for one frame. */
export function lifeUniforms(frame: LifeFrame) {
  'worklet';
  const { index, size, pose, exchange } = frame;
  const rig = RIGS[index];
  const { k, fx, fy, z, ox, oy, c, s, origin } = placement(frame);
  const joint = (j: number) => {
    const data = rig.joints[j];
    return {
      at: [data.pivot[0] * k, data.pivot[1] * k, data.center[0] * k, data.center[1] * k],
      bend: [data.radius * k, pose.angles[j]],
    };
  };
  const j0 = joint(0);
  const j1 = joint(1);
  const j2 = joint(2);
  const j3 = joint(3);
  const eye = (e: number) => [rig.eyes[e][0] * k, rig.eyes[e][1] * k, rig.eyes[e][2] * k, rig.eyes[e][3] * k];
  const flame = rig.flame;
  return {
    box: [size, fy],
    pd: 0.9,
    invX: [c / pose.sx, s / pose.sx, fx - (c * ox + s * oy) / pose.sx],
    invY: [-s / pose.sy, c / pose.sy, fy - (-s * ox + c * oy) / pose.sy],
    j0: j0.at, k0: j0.bend, j1: j1.at, k1: j1.bend, j2: j2.at, k2: j2.bend, j3: j3.at, k3: j3.bend,
    headShift: pose.headShift * frame.scale,
    chest: [rig.chest[0] * k, rig.chest[1] * k, rig.chest[2] * k, pose.breath],
    hips: [rig.hips * k, pose.shift * k, pose.hang, 10 * k],
    eyeA: eye(0), eyeB: eye(1),
    lid: [pose.lid, rig.lids.lower, rig.lids.lash, rig.lids.lowerInk],
    lidInk: [rig.lids.ink[0], rig.lids.ink[1], rig.lids.ink[2], rig.lids.squash],
    origin: [origin[0], origin[1]],
    zoom: z,
    screen: [frame.width, frame.screenHeight],
    front: exchange.f,
    phase: frame.phase,
    role: exchange.role === 1 ? 1 : 0,
    element: frame.element,
    tint: [frame.tint[0], frame.tint[1], frame.tint[2]],
    strength: 0.6,
    // Pushed back into the old world's shade as the new one covers it.
    shade: exchange.role === 0 ? 0.3 * exchange.f : 0,
    light: [frame.light[0], frame.light[1], frame.light[2]],
    glowAt: [rig.glow.at[0] * k, rig.glow.at[1] * k, rig.glow.radius * k, frame.glow],
    glowColor: [rig.glow.color[0], rig.glow.color[1], rig.glow.color[2]],
    face: [rig.face.at[0] * k, rig.face.at[1] * k, rig.face.radius * k, pose.yaw * rig.face.shift * k],
    flame: flame ? [flame.base[0] * k, flame.base[1] * k, flame.height * k, 1] : [0, 0, 1, 0],
    flameArea: flame ? [flame.at[0] * k, flame.at[1] * k, flame.radius[0] * k, flame.radius[1] * k] : [0, 0, 1, 1],
    flameMap: flame ? [flame.map[0] * k, flame.map[1] * k, flame.map[2] * k, flame.map[3] * k] : [0, 0, 1, 1],
    flameState: [pose.flameGrow, frame.seconds, pose.flameStretch, frame.reduced ? 0 : 1],
    hidden: exchange.role < 0 ? 1 : 0,
  };
}
