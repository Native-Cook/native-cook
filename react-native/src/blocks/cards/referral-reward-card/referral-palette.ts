// Referral Reward Card — a "Refer friends" card whose row of avatars runs
// under a fixed lens. After a still by ALX (@alxui_ux): a dark card, white
// type and a row of avatars. Here the card is clear glass over light in the
// colors of the friend under the lens. The motion and the glass are ours.
//
// Pure data and math so node tests can import it without React Native.

export const referralTone = {
  page: "#000000",
  // The dark of a mark drawn on white: a check, an icon in a full disc.
  night: "#0C0C0D",
  ink: "#FFFFFF",
  // Secondary copy is white let through, so it keeps its contrast whatever
  // color the glass under it has taken.
  sub: "rgba(255,255,255,0.72)",
  // The caption under the reward marks lies over the upper right lamp, the
  // brightest light a small text has under it, so it lets less through.
  note: "rgba(255,255,255,0.9)",
  pageHint: "rgba(255,255,255,0.6)",
  ringTrack: "rgba(255,255,255,0.26)",
  ring: "#FFFFFF",
  tick: "#FFFFFF",
  // The capsule a friend opens into: a darker pane set in the glass.
  pane: "rgba(4,4,8,0.46)",
  onButton: "#FFFFFF",
};

// Sizes in points. The reference is a 436 px wide web card; these are its
// proportions on a 370 pt card, with the lens kept large enough to read.
export const referralSize = {
  cardMax: 370,
  cardMargin: 16,
  cardRadius: 32,
  cardPad: 24,
  // The rail's canvas: full card width, this tall, the lens centred at `cy`.
  railHeight: 212,
  cy: 112,
  // A side avatar, and the distance between two of them.
  avatar: 52,
  pitch: 56,
  // The avatars next to the lens sit this much further out, so 6 pt of each
  // tucks under the ring.
  tuck: 6,
  // Inside the lens an avatar is this wide, and the row moves this far per
  // friend, which leaves a 6 pt seam between two of them in transit.
  focus: 72,
  lensPitch: 78,
  lens: 37,
  ring: 42,
  ringStroke: 1.5,
  arcStroke: 2,
  // The four ticks, measured from the lens centre along the diagonals.
  tickFrom: 55,
  tickLen: 23,
  // The points sit this far above the lens centre.
  pointsRise: 75,
  pointsFont: 30,
  pointsLine: 36,
};

export const referralMotion = {
  // Arrival of the card's three parts.
  stagger: 80,
  enter: 380,
  // A press that has to bring the next friend under the lens first.
  travel: 300,
  wave: 420,
  // The points start rolling a moment before it lands.
  countLead: 40,
  roll: 520,
  rollStagger: 45,
  // When a tier fills, a second comet leaves the points this long after the
  // first has landed, and takes this long to reach the tier's mark.
  tierAfter: 200,
  flight: 440,
  drainAfter: 260,
  drain: 520,
  // autoPlay: the gap after an ordinary join, after one that fills a tier,
  // and after the last friend, before it starts over. At the demo's layout,
  // motion ends by 1854 / 2444 ms, including the springs' settling time.
  beat: 2000,
  tierBeat: 2800,
  restBeat: 3200,
  // One turn of the light under the glass. It is the length of an autoPlay
  // loop (three beats, two tier beats, a rest), so a recording of one loops.
  drift: 14800,
};

/** t²(3 − 2t), clamped: a ramp with no corner at either end. */
export function smooth(t: number) {
  "worklet";
  const c = Math.min(Math.max(t, 0), 1);
  return c * c * (3 - 2 * c);
}

/** `values[i]` at distance `i`, eased between them so the slope never jumps. */
export function ramp(distance: number, values: readonly number[]) {
  "worklet";
  const last = values.length - 1;
  const a = Math.min(Math.max(distance, 0), last);
  const i = Math.min(Math.floor(a), last - 1);
  return values[i] + (values[i + 1] - values[i]) * smooth(a - i);
}

export const POINTS_PER_FRIEND = 25;
// A tier fills when this many friends have joined.
export const TIERS = [3, 5, 7] as const;
// Friends who have already joined when the card opens: 50 points.
export const JOINED_AT_START = 2;

/** How many tiers are full with `joined` friends. */
export function tierOf(joined: number) {
  "worklet";
  let level = 0;
  for (const need of TIERS) if (joined >= need) level += 1;
  return level;
}

/** How far the arc has closed towards the next tier, 0..1. */
export function arcOf(joined: number) {
  "worklet";
  const level = tierOf(joined);
  if (level >= TIERS.length) return 1;
  const from = level === 0 ? 0 : TIERS[level - 1];
  return (joined - from) / (TIERS[level] - from);
}

/** `i - scroll` wrapped into [-n/2, n/2): where friend `i` sits, in friends from the lens. */
export function offsetOf(i: number, scroll: number, n: number) {
  "worklet";
  return ((((i - scroll + n / 2) % n) + n) % n) - n / 2;
}

export function wrapIndex(i: number, n: number) {
  "worklet";
  return ((i % n) + n) % n;
}

/** A strong ease-out for values derived on the UI thread. */
export function easeOut(t: number) {
  "worklet";
  const u = 1 - Math.min(Math.max(t, 0), 1);
  return 1 - u * u * u * u;
}

export function easeInOut(t: number) {
  "worklet";
  const c = Math.min(Math.max(t, 0), 1);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}
