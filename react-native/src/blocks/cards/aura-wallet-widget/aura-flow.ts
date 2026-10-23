// The choreography of the wallet, as numbers and pure math. One idea runs
// through all of it: surface tension. The cushion is a pool of light, a friend
// on its lip is a drop about to fall, and the pane under them is where a drop
// lands. Sending money is letting a drop fall.
//
// The shader and the views both read this file, so the glass and the faces
// cannot disagree about where a drop is. Lengths are in the widget's design
// points (multiply by `u`), times in milliseconds. Every function is a
// worklet and allocates nothing but its result.

import { auraDesign as D } from "./aura-palette";

/** What the widget is doing. Numbers, so a worklet can compare them. */
export const AuraPhase = {
  /** Nothing chosen. */
  rest: 0,
  /** A friend was tapped: their drop has formed and Send is ready. */
  armed: 1,
  /** A finger is pulling a friend's drop down. */
  pulling: 2,
  /** A drop was let go: it snaps, falls, lands, and the friend grows back. */
  sending: 3,
} as const;
export type AuraPhaseValue = (typeof AuraPhase)[keyof typeof AuraPhase];

export const auraFlow = {
  // --- The drop a friend becomes ---
  drop: {
    /** The cushion's light wraps a chosen friend this far past their ring: a drop holding nothing, and one holding the largest amount. */
    halo: 6,
    haloFull: 9.5,
    /** A step of the amount is seen as well as felt: the drop takes its new weight with a little overshoot. */
    weigh: { duration: 220, dampingRatio: 0.6 },
    /** A formed drop hangs this much lower than a friend at rest. */
    sag: 6,
    /** The others step back: smaller, dimmer, and aside by `aside / distance`. */
    othersScale: 0.92,
    othersOpacity: 0.55,
    aside: 3,
    /** Forming and letting go of a drop: liquid, so a little overshoot. */
    form: { duration: 400, dampingRatio: 0.7 },
  },

  // --- Pulling: how far is how much ---
  pull: {
    /** Travel before the first dollar: a touch that only wobbles the drop sends nothing. */
    dead: 12,
    /** One step of `amountStep` dollars for each of these points. */
    perStep: 6.5,
    amountStep: 10,
    amountMax: 100,
    /** What Send offers when a friend was only tapped. */
    amountDefault: 60,
    /** Sideways the drop follows the finger this far, then resists. */
    sideways: 28,
    /** Past a limit the drop moves this fraction of the finger's extra travel. */
    rubber: 0.3,
  },

  // --- The neck between the lip and a pulled drop ---
  neck: {
    /** Half its width where it leaves the lip: the lip itself is drawn down into a wider cone as the drop goes deeper. */
    root: 13,
    rootLong: 19,
    /** Half its width at its thinnest, for a short pull and for the longest. Never a hair: liquid, not a stalk. */
    waistShort: 9,
    waistLong: 4,
    /** The smooth union that fillets neck into lip and drop. */
    blend: 12,
  },

  // --- Letting go ---
  // The one slow moment of the widget: each beat is long enough to be read,
  // and the whole takes a little over a second.
  send: {
    /** The neck pinches shut over this long. */
    snap: 140,
    /** The drop hangs for this long of the snap before it starts to fall. */
    hang: 90,
    /** Points per second squared. A drop falling 60 pt takes about 266 ms. */
    gravity: 1700,
    /** A fall is never shorter than this, so a deep pull still reads as a landing. */
    fallMin: 140,
    /** Taller and narrower by this much at the middle of the fall. */
    stretch: 0.12,
    /** On the pane it flattens by this much within `squash` ms, then rings back to round. */
    flatten: 0.22,
    squash: 50,
    ringDecay: 110,
    ringPeriod: 300,
    /** The glass takes it in: from `absorbAfter` ms after the landing, over `absorb` ms, sinking and shrinking a little. */
    absorbAfter: 60,
    absorb: 220,
    sinkBy: 3,
    shrinkTo: 0.86,
    /** The ripple the landing sends across the pane, in points per second, and how long it lives. */
    rippleSpeed: 460,
    rippleLife: 700,
    /** The pane's hairline flashes to this many times its light at the landing. */
    rimFlash: 2.2,
    /** The lip, freed of the neck, rings: this far, with this spring. */
    lipKick: 3,
    lipRing: { duration: 500, dampingRatio: 0.45 },
    /** The cushion gives up a little light when the drop leaves, and gets it back. */
    cushionDip: 0.06,
    cushionRecover: 600,
    /** A ring of light runs up the cushion from where the neck broke: this fast, for this long. */
    waveSpeed: 330,
    waveLife: 520,
    /** The lip stays empty this long after the landing, then the friend grows back. */
    budDelay: 380,
    budFrom: 0.5,
    bud: { duration: 520, dampingRatio: 0.55 },
    /** Touches are accepted again this long after the landing. */
    unlock: 300,
    /** Tapping Send instead of pulling: the drop sinks to its amount's depth by itself first. */
    sink: 300,
  },

  // --- Send grows, Receive steps aside ---
  pills: {
    sendWide: 164,
    receiveRound: 52,
    /** While they trade width their facing ends reach for each other by this blend. */
    kiss: 8,
    morph: { duration: 400, dampingRatio: 0.85 },
  },

  // --- The nudge when Send is pressed with nobody chosen ---
  nudge: { depth: 4, stagger: 45, each: 320 },

  // --- Arriving ---
  // The glass is drawn a few frames after the views, and a screen can take
  // long to show its first frame. So the wait is counted in frames that were
  // really shown: after `hold` ms of them, each no longer than `smooth`, the
  // type fades in over `reveal` and the five grow out of the lip, left to right.
  enter: { hold: 260, smooth: 50, reveal: 140, delay: 60, stagger: 55 },

  // --- Until the first touch, the nudge runs by itself to say the five can be pulled ---
  cue: { first: 2400, every: 6000 },

  // --- What only the views animate ---
  views: {
    /** A touch that travels further than this is a pull, not a tap. */
    pullSlop: 6,
    /** The chosen friend's small face slides out from behind the owner's. */
    route: { travel: 14, duration: 220 },
    /** The chip's two labels trade places, blurred by this much at the middle. */
    chip: { fade: 180, blur: 2 },
    /** The line under the widget, when its sentence changes; and how long "Sent" stays. */
    hint: { fade: 160, sentHold: 2000 },
    /** Send's arrow leaves upwards and a new one rises into its place. */
    arrow: { exit: 12, exitTime: 160, enter: 8, enterTime: 200, enterDelay: 60 },
    /** Under Reduce Motion a new line of the pane crossfades in, and a sent friend's face fades out on the lip. */
    receiptFade: 180,
    departureFade: 150,
  },
} as const;

/** Where a friend rests, and where a drop lands: the pane's centre line. */
export function auraSlotX(index: number) {
  "worklet";
  return 181 + (index - 2) * D.lip.pitch;
}
export const AURA_LAND_Y = D.pane.y + D.pane.h / 2;

function clamp(value: number, low: number, high: number) {
  "worklet";
  return Math.min(Math.max(value, low), high);
}

/** 0 → 1 with zero slope at both ends. */
function ease(value: number) {
  "worklet";
  const t = clamp(value, 0, 1);
  return t * t * (3 - 2 * t);
}

/**
 * A spring from 0 to 1 as a function of time, so a moment of the send can be
 * drawn from the clock alone (and a proof can render any frame of it).
 * It has settled, to within a few percent, at `duration`.
 */
export function auraSpring(elapsed: number, duration: number, dampingRatio: number) {
  "worklet";
  if (elapsed <= 0) return 0;
  const t = elapsed / duration;
  const omega = 2 * Math.PI;
  const zeta = clamp(dampingRatio, 0.05, 0.999);
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  const envelope = Math.exp(-zeta * omega * t);
  return 1 - envelope * (Math.cos(damped * t) + ((zeta * omega) / damped) * Math.sin(damped * t));
}

/** A finger's travel past a limit, softened. */
export function auraRubber(travel: number, limit: number) {
  "worklet";
  if (travel <= limit) return travel;
  return limit + (travel - limit) * auraFlow.pull.rubber;
}

/** The deepest a drop can be pulled before it resists: the depth of the largest amount. */
export function auraPullLimit() {
  "worklet";
  const P = auraFlow.pull;
  return P.dead + (P.amountMax / P.amountStep - 0.5) * P.perStep;
}

/** Dollars for a drop pulled `depth` points below its formed position. */
export function auraAmountAt(depth: number) {
  "worklet";
  const P = auraFlow.pull;
  if (depth < P.dead) return 0;
  const steps = Math.floor((depth - P.dead) / P.perStep) + 1;
  return clamp(steps * P.amountStep, P.amountStep, P.amountMax);
}

/** The depth at the middle of an amount's step: where a drop sinks to when Send is tapped. */
export function auraDepthFor(amount: number) {
  "worklet";
  const P = auraFlow.pull;
  return P.dead + (clamp(amount, P.amountStep, P.amountMax) / P.amountStep - 0.5) * P.perStep;
}

/** How far the cushion's light wraps a drop holding `weight` of the largest amount (0–1, may overshoot). */
export function auraDropHalo(weight: number) {
  "worklet";
  const R = auraFlow.drop;
  return R.halo + (R.haloFull - R.halo) * clamp(weight, 0, 1.15);
}

/** Half the neck's width where it leaves the lip, for a drop `depth` points down. */
export function auraNeckRoot(depth: number) {
  "worklet";
  const N = auraFlow.neck;
  return N.root + (N.rootLong - N.root) * ease(depth / auraPullLimit());
}

/**
 * Where the drop is `elapsed` ms after Send was tapped, on its way to `depth`.
 * It moves at once, because a press must be answered at once, and then
 * gravity takes over: never a slow start.
 */
export function auraSinkDepth(elapsed: number, depth: number) {
  "worklet";
  const t = clamp(elapsed / auraFlow.send.sink, 0, 1);
  const answer = 1 - (1 - t) * (1 - t) * (1 - t);
  return depth * (0.2 * answer + 0.8 * Math.pow(t, 2.4));
}

/** How long the arrival takes. */
export function auraEnterLength() {
  "worklet";
  const E = auraFlow.enter;
  return E.delay + E.stagger * (D.lip.count - 1) + auraFlow.send.bud.duration;
}

/** Friend `index`, `elapsed` ms into the arrival: 0 before their turn, then growing out of the lip as a bud does. */
export function auraEnterScale(index: number, elapsed: number) {
  "worklet";
  const E = auraFlow.enter;
  const S = auraFlow.send;
  const since = elapsed - E.delay - index * E.stagger;
  if (since <= 0) return 0;
  return S.budFrom + (1 - S.budFrom) * auraSpring(since, S.bud.duration, S.bud.dampingRatio);
}

/** Their opacity at the same moment. */
export function auraEnterOpacity(index: number, elapsed: number) {
  "worklet";
  const E = auraFlow.enter;
  return clamp((elapsed - E.delay - index * E.stagger) / 120, 0, 1);
}

/** Half the neck's width at its thinnest, for a drop `depth` points down. */
export function auraNeckWaist(depth: number) {
  "worklet";
  const N = auraFlow.neck;
  const stretched = clamp(depth / auraPullLimit(), 0, 1);
  return N.waistShort + (N.waistLong - N.waistShort) * ease(stretched);
}

export type AuraFriendPose = {
  /** Offset from the friend's slot. */
  dx: number;
  dy: number;
  /** The face and its ring, 1 at rest. */
  scale: number;
  opacity: number;
  /** How far the cushion's light wraps them, 0 to `drop.halo`. */
  halo: number;
};

/**
 * A friend who is not falling. `formed` is how formed the chosen friend's drop
 * is (0–1, a spring that may overshoot) and `chosen` who that is (-1 for nobody).
 */
export function auraFriendPose(index: number, chosen: number, formed: number): AuraFriendPose {
  "worklet";
  const R = auraFlow.drop;
  if (chosen < 0 || formed === 0) return { dx: 0, dy: 0, scale: 1, opacity: 1, halo: 0 };
  if (index === chosen) {
    return { dx: 0, dy: R.sag * formed, scale: 1, opacity: 1, halo: R.halo * clamp(formed, 0, 1.2) };
  }
  const away = index - chosen;
  const back = clamp(formed, 0, 1);
  return {
    dx: (Math.sign(away) * R.aside * formed) / Math.abs(away),
    dy: 0,
    scale: 1 + (R.othersScale - 1) * back,
    opacity: 1 + (R.othersOpacity - 1) * back,
    halo: 0,
  };
}

/**
 * A friend on the lip when the choice may be changing hands: `chosen`'s drop
 * is forming by `formed` while `leaving`'s is letting go by `unformed` (1 → 0).
 * With nobody leaving, pass -1 and 0.
 */
export function auraLipPose(index: number, chosen: number, formed: number, leaving: number, unformed: number): AuraFriendPose {
  "worklet";
  const R = auraFlow.drop;
  if (index === leaving && index !== chosen) {
    // Still a drop, shrinking back onto the lip; it does not step back for the next one.
    return { dx: 0, dy: R.sag * unformed, scale: 1, opacity: 1, halo: R.halo * clamp(unformed, 0, 1.2) };
  }
  // The others stay back for as long as anyone is a drop.
  const pose = auraFriendPose(index, chosen, formed);
  if (index === chosen || leaving < 0) return pose;
  const back = clamp(Math.max(formed, unformed), 0, 1);
  return { ...pose, scale: 1 + (R.othersScale - 1) * back, opacity: 1 + (R.othersOpacity - 1) * back };
}

/** How long the nudge takes to run along all five. */
export function auraNudgeLength() {
  "worklet";
  const N = auraFlow.nudge;
  return N.stagger * (D.lip.count - 1) + N.each;
}

/** How far friend `index` dips, `elapsed` ms into the nudge: one soft bob each, left to right. */
export function auraNudgeDip(index: number, elapsed: number) {
  "worklet";
  const N = auraFlow.nudge;
  const local = (elapsed - index * N.stagger) / N.each;
  if (elapsed < 0 || local <= 0 || local >= 1) return 0;
  return N.depth * Math.sin(Math.PI * local);
}

/** The moments of a send, in ms after the drop is let go `fromY` points down the widget. */
export function auraSendMoments(fromY: number) {
  "worklet";
  const S = auraFlow.send;
  const fall = Math.max(AURA_LAND_Y - fromY, 0);
  const fallTime = Math.max(Math.sqrt((2 * fall) / S.gravity) * 1000, S.fallMin);
  const impact = S.hang + fallTime;
  const bud = impact + S.budDelay;
  return {
    fallStart: S.hang,
    fallTime,
    /** The drop touches the pane: the haptic, the ripple and the new line start here. */
    impact,
    unlock: impact + S.unlock,
    bud,
    end: Math.max(bud + S.bud.duration, impact + S.rippleLife, S.snap + S.cushionRecover, S.snap + S.waveLife),
  };
}

export type AuraSendPose = {
  /** The neck, 1 whole to 0 pinched shut. */
  neck: number;
  /** The lip where the neck was: points it springs up past its line (negative is up). */
  lipKick: number;
  /** The falling drop's centre, and its height and width as factors of a round drop. */
  y: number;
  tall: number;
  wide: number;
  /** 1 while the drop exists, 0 once the pane has taken it in. */
  presence: number;
  /** Being taken in, 0 → 1: the drop and its face sink by `sinkBy` and shrink towards `shrinkTo`. */
  taken: number;
  /** Radius of the ripple on the pane in points, and its strength 1 → 0. Strength 0 before the landing. */
  rippleRadius: number;
  rippleStrength: number;
  /** The pane's hairline, as a factor of its resting light. */
  rim: number;
  /** The cushion's light, as a factor (dips below 1 when the drop leaves). */
  cushion: number;
  /** The ring of light running up the cushion from the break: its radius in points and its strength 1 → 0. */
  waveRadius: number;
  waveStrength: number;
  /** The friend back on the lip: 0 while the lip is empty, then `budFrom` → 1 with overshoot. */
  bud: number;
  budOpacity: number;
};

/** Everything a send looks like `elapsed` ms after a drop was let go at `fromY`. */
export function auraSendPose(elapsed: number, fromY: number): AuraSendPose {
  "worklet";
  const S = auraFlow.send;
  const M = auraSendMoments(fromY);
  const t = Math.max(elapsed, 0);

  const falling = clamp((t - M.fallStart) / M.fallTime, 0, 1);
  const sinceImpact = t - M.impact;
  const landed = sinceImpact >= 0;

  // Gravity: the square of the time. The stretch peaks mid-fall and is gone at the landing.
  const y = fromY + (AURA_LAND_Y - fromY) * falling * falling;
  const stretch = S.stretch * Math.sin(Math.PI * falling);
  // The landing: flat within `squash`, then ringing back to round while it dies away.
  const ringing = Math.max(sinceImpact - S.squash, 0);
  const flat = landed
    ? S.flatten * ease(sinceImpact / S.squash) * Math.exp(-ringing / S.ringDecay) * Math.cos((2 * Math.PI * ringing) / S.ringPeriod)
    : 0;
  const taken = landed ? ease((sinceImpact - S.absorbAfter) / S.absorb) : 0;

  const ripple = landed ? clamp(sinceImpact / S.rippleLife, 0, 1) : 0;
  const sinceSnap = t - S.snap;
  const wave = clamp(sinceSnap / S.waveLife, 0, 1);
  const sinceBud = t - M.bud;

  return {
    neck: 1 - ease(t / S.snap),
    lipKick: sinceSnap > 0 ? -S.lipKick * (1 - auraSpring(sinceSnap, S.lipRing.duration, S.lipRing.dampingRatio)) : 0,
    y: y + S.sinkBy * taken,
    tall: (1 + stretch - flat) * (1 + (S.shrinkTo - 1) * taken),
    wide: (1 - stretch * 0.7 + flat * 0.9) * (1 + (S.shrinkTo - 1) * taken),
    presence: 1 - taken,
    taken,
    rippleRadius: landed ? (S.rippleSpeed * sinceImpact) / 1000 : 0,
    rippleStrength: landed ? (1 - ripple) * (1 - ripple) : 0,
    rim: landed ? 1 + (S.rimFlash - 1) * (1 - ripple) * (1 - ripple) : 1,
    cushion: sinceSnap > 0 ? 1 - S.cushionDip * (1 - ease(sinceSnap / S.cushionRecover)) : 1 - S.cushionDip * ease(t / S.snap),
    waveRadius: sinceSnap > 0 ? (S.waveSpeed * sinceSnap) / 1000 : 0,
    waveStrength: sinceSnap > 0 ? (1 - wave) * (1 - wave) : 0,
    bud: sinceBud > 0 ? S.budFrom + (1 - S.budFrom) * auraSpring(sinceBud, S.bud.duration, S.bud.dampingRatio) : 0,
    budOpacity: clamp(sinceBud / 120, 0, 1),
  };
}

/**
 * The four pillows' frames while Send and Receive trade width. `wide` is 0 at
 * rest and 1 with Send grown; it is a spring and may pass either end a little.
 * Lengths here are design points, as in `auraDesign.actions`.
 */
export function auraActionFrames(wide: number) {
  "worklet";
  const A = D.actions;
  const P = auraFlow.pills;
  const receive = A.widths[2] + (P.receiveRound - A.widths[2]) * wide;
  const send = A.widths[3] + (P.sendWide - A.widths[3]) * wide;
  const settingsX = D.inset;
  const swapX = settingsX + A.widths[0] + A.gap;
  const receiveX = swapX + A.widths[1] + A.gap;
  const sendX = receiveX + receive + A.gap;
  return {
    xs: [settingsX, swapX, receiveX, sendX],
    widths: [A.widths[0], A.widths[1], receive, send],
    /** Largest in the middle of the trade, zero at both ends. */
    kiss: P.kiss * Math.sin(Math.PI * clamp(wide, 0, 1)),
  };
}
