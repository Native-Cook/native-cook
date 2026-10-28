export const EMERALD_TIER_LEADERBOARD_LOOP_SECONDS = 10;
export const LOOP = EMERALD_TIER_LEADERBOARD_LOOP_SECONDS;
export const TAKEOVER_MS = 1200;
export const CROWN_SEAT_PHASE = .98;
export const DEMO_TAKEOVER_START = 2;
export const DEMO_RETURN_START = 7.6;
const SNAP_DECELERATION = .995;
export const clamp = (n: number, a = 0, b = 1) => { 'worklet'; return Math.max(a, Math.min(b, n)); };
export const range = (t: number, a: number, b: number) => { 'worklet'; return clamp((t - a) / (b - a)); };
export function bezier(x: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet'; x = clamp(x); let u = x;
  for (let i = 0; i < 7; i++) { const a = 1 - u, f = 3 * a * a * u * x1 + 3 * a * u * u * x2 + u * u * u - x, d = 3 * a * a * x1 + 6 * a * u * (x2 - x1) + 3 * u * u * (1 - x2); if (Math.abs(d) < .000001) break; u = clamp(u - f / d); }
  return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u;
}
export const out = (x: number) => { 'worklet'; return bezier(x, .23, 1, .32, 1); };
const mix = (a: number, b: number, p: number) => { 'worklet'; return a + (b - a) * p; };
const smooth = (p: number) => { 'worklet'; return p * p * (3 - 2 * p); };

/** The demo clock has readable holds; touch never enters its backwards return branch. */
export function emeraldDemoPhase(progress: number) {
  'worklet';
  const seconds = clamp(progress) * LOOP;
  if (seconds < DEMO_TAKEOVER_START) return 0;
  if (seconds < DEMO_TAKEOVER_START + TAKEOVER_MS / 1000) return (seconds - DEMO_TAKEOVER_START) / (TAKEOVER_MS / 1000);
  if (seconds < DEMO_RETURN_START) return 1;
  if (seconds < DEMO_RETURN_START + TAKEOVER_MS / 1000) return 1 - (seconds - DEMO_RETURN_START) / (TAKEOVER_MS / 1000);
  return 0;
}

/** Continuous resistance at either endpoint, in the same units as the drag. */
export function emeraldRubberPhase(value: number) {
  'worklet';
  const edge = clamp(value), over = value - edge;
  return edge + over / (1 + Math.abs(over) / .12);
}
export function emeraldUnrubberPhase(value: number) {
  'worklet';
  const edge = clamp(value), over = value - edge;
  return edge + over / Math.max(.00001, 1 - Math.abs(over) / .12);
}
/** A deliberate flick commits in its direction; a slow release chooses the nearest projected rest. */
export function emeraldSnapTarget(phase: number, velocity: number): 0 | 1 {
  'worklet';
  if (velocity > .35) return 1;
  if (velocity < -.35) return 0;
  const projected = phase + (velocity / 1000) * SNAP_DECELERATION / (1 - SNAP_DECELERATION);
  return clamp(projected) >= .5 ? 1 : 0;
}

/** One monotonic, interruptible rank journey. All coordinates remain local to the existing arena. */
export function emeraldRankFrame(rankPhase: number, reduced = false, allowResponse = true) {
  'worklet';
  const t = reduced ? (rankPhase >= .5 ? 1 : 0) : clamp(rankPhase);
  const edgeOffset = reduced ? 0 : (rankPhase - t) * 112;
  const rise = out(range(t, 0, .24));
  const p = bezier(range(t, .28, .7175), .4, 0, .2, 1);
  const arc = Math.sin(p * Math.PI);
  const anticipation = reduced ? 0 : Math.sin(range(t, .20, .28) * Math.PI);
  const landingAge = Math.max(0, (t - .7175) * TAKEOVER_MS / 1000);
  const age = Math.max(0, (t - CROWN_SEAT_PHASE) * TAKEOVER_MS / 1000);
  const landed = !reduced && t > .7175 && t < 1;
  const impact = allowResponse && !reduced && t >= CROWN_SEAT_PHASE && t < 1;
  const landing = landed ? Math.sin(range(t, .7175, .91) * Math.PI) : 0;
  const settle = landed ? -1.1 * Math.exp(-16 * landingAge) * Math.sin(24 * landingAge) : 0;
  const responsePhase = range(t, CROWN_SEAT_PHASE, 1);
  const response = impact ? Math.sin(responsePhase * Math.PI) ** 2 : 0;
  const crownSeat = impact ? -1.2 * Math.exp(-20 * age) * Math.sin(28 * age) : 0;
  // Eight extra points on Ari's lower route keep the two circular portraits separated.
  const ari = { x: mix(142, 30, p) + edgeOffset, y: mix(35, 73, p) + arc * 35, scale: mix(1, .8, p) };
  const milo = { x: mix(30, 142, p) + edgeOffset, y: mix(73, 35, p) - arc * 37 + anticipation * 3 + landing * 1.4 + settle, scale: mix(.8, 1, p) * (1 - anticipation * .012) };
  // The crown belongs to the first-place axis, not an interpolation of two moving heads.
  // Its top never rises above -10: the countdown ends at -13 in arena coordinates.
  const lift = out(range(t, .20, .28)) * (1 - smooth(range(t, .7175, CROWN_SEAT_PHASE)));
  const crown = { x: 180 + edgeOffset, y: 14 - lift * 24 + (p === 1 ? landing * 1.4 + settle : 0) + crownSeat, rotation: 0 };
  // Both text blocks are fully hidden before their boxes can meet the other portrait.
  const labelOpacity = reduced ? 1 : 1 - smooth(range(p, .02, .12)) + smooth(range(p, .88, .98));
  return { progress: t, rawProgress: rankPhase, cycle: t, rise, travel: p, arc, ari, milo, crown, crownTravel: range(t, .20, CROWN_SEAT_PHASE), age, impact,
    burst: response, response, responsePhase, anticipation, settle, labelOpacity,
    score: 950 + Math.round(100 * rise), hold: smooth(range(t, CROWN_SEAT_PHASE, .94)), landing, reduced, allowResponse };
}

/** Public deterministic preview: t=0 and t=10 have exactly the same frame. */
export function emeraldFrame(progress: number, reduced = false) {
  'worklet';
  const wrapped = ((progress % 1) + 1) % 1;
  return emeraldRankFrame(emeraldDemoPhase(wrapped), reduced, wrapped * LOOP < DEMO_RETURN_START);
}
export type EmeraldFrame = ReturnType<typeof emeraldRankFrame>;
