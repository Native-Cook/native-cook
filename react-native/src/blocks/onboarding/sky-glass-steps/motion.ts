// Everything on the screen is a function of the clock and of the last few events (a swipe, a
// change of the step in focus, a tap), so a finger and the loop that plays by itself make exactly
// the same pictures. Times are in seconds, lengths in points.

/** autoPlay: one pass through the three steps every LOOP seconds. The sky repeats in the same time. */
export const LOOP = 9;
/** autoPlay swipes on at these moments; the last swipe brings the first step round again. */
export const AUTO_SWIPES = [2.2, 5.2, 8.2];
/** How long the first step has been in focus when a loop starts: its rows are in and it is still. */
export const LEAD = LOOP - AUTO_SWIPES[2];
/** After a touch autoPlay waits this long before it goes back to the first step and starts over. */
export const USER_HOLD = 3;

export const STEPS = 3;
/** From one card to the next: a card's width and the gap. */
export const PITCH = 314;

// The carousel: a spring with almost no bounce (response 0.5 s, damping ratio 0.85).
export const SLIDE_MASS = 1;
export const SLIDE_STIFFNESS = 158;
export const SLIDE_DAMPING = 21.4;
/** After this long the spring is where it was going. */
export const SLIDE_REST = 1.2;
/** How far ahead a flick looks for the card to stop on, and the fastest flick, in cards a second. */
export const FLICK = 0.2;
export const FLICK_MAX = 8;

// A preview comes in when its card takes the focus: each part rises and fades in, one after another.
export const ENTER_AFTER = 0.1;
export const ENTER_RISE = 0.45;
export const ENTER_FADE = 0.25;
/** The preview of the card that lost the focus fades out in this time. */
export const LEAVE_FADE = 0.25;
/** With reduced motion, and for anything that only fades. */
export const FADE = 0.2;

// Step 1: four rows; the white fill steps down them once.
export const ROW_STAGGER = 0.06;
export const ROW_SHIFT = 16;
export const ROW_FIRST = 1.2;
export const ROW_EVERY = 0.55;
export const ROW_SLIDE = 0.3;

// Step 2: three lesson cards, back to front; the front one lifts and settles once.
export const LESSON_STAGGER = 0.08;
export const LESSON_SHIFT = 30;
export const LIFT_AFTER = 1.3;
export const LIFT_FOR = 0.6;
export const LIFT = 4;

// Step 3: the panel grows in, the checks tick in reading order, the badge pulses once.
export const PANEL_IN = 0.35;
export const PANEL_SCALE = 0.96;
export const TICK_FIRST = 0.7;
export const TICK_EVERY = 0.15;
export const TICK_FOR = 0.3;
export const TICK_POP = 0.2;
export const UNTICK_FOR = 0.15;
export const PULSE_AFTER = 0.2;
export const PULSE_FOR = 0.45;
export const PULSE = 0.14;

// The headline moves against a dragging finger by up to this much, and goes back when it lifts.
export const PARALLAX = 6;
export const PARALLAX_BACK = 0.3;

/** The longest a preview plays after its card took the focus; the clock may stop after this. */
export const QUIET = 3.5;

/** The carousel's spring: where it is going (a whole card, counted without end), from where, how fast it was let go, and when. */
export type Slide = { page: number; from: number; velocity: number; at: number };

/**
 * The card whose preview plays: its page, the step that had the focus before and for how long,
 * and when the focus changed. `warm` means the card kept the focus and only starts over, so
 * nothing comes in again; `row` is where its white fill was then.
 */
export type Focus = { page: number; was: number; held: number; at: number; warm: boolean; row: number };

/** A row picked by hand: the fill goes there from where it was. No row is -1. */
export type Pick = { row: number; from: number; at: number };

/** The headline's drag: how far the finger has pulled, and when it let go (negative while it holds). */
export type Pull = { x: number; released: number };

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

export function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

export function easeInOut(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** One smooth rise and fall over 0…1, flat at both ends. */
export function bump(t: number) {
  'worklet';
  return (1 - Math.cos(2 * Math.PI * clamp01(t))) / 2;
}

/** The step a page shows: pages go on without end in both directions. */
export function stepOf(page: number) {
  'worklet';
  return ((page % STEPS) + STEPS) % STEPS;
}

/** How many cards a step sits from the middle of the screen, the short way round. */
export function offsetOf(step: number, position: number) {
  'worklet';
  const d = step - position;
  return d - STEPS * Math.round(d / STEPS);
}

/** Where the carousel is, in cards. With reduced motion it is where it is going. */
export function slideAt(slide: Slide, t: number, still: boolean) {
  'worklet';
  const e = t - slide.at;
  if (still || e >= SLIDE_REST) return slide.page;
  if (e <= 0) return slide.from;
  const omega = Math.sqrt(SLIDE_STIFFNESS / SLIDE_MASS);
  const decay = SLIDE_DAMPING / (2 * SLIDE_MASS);
  const damped = Math.sqrt(omega * omega - decay * decay);
  const a = slide.from - slide.page;
  const b = (slide.velocity + decay * a) / damped;
  return slide.page + Math.exp(-decay * e) * (a * Math.cos(damped * e) + b * Math.sin(damped * e));
}

/**
 * The clock of a step's preview: seconds since its card took the focus, how visible it is, and
 * whether its parts come in. The card that lost the focus stays as it was and fades; the third
 * one is hidden.
 */
export function previewAt(step: number, focus: Focus, t: number) {
  'worklet';
  const since = t - focus.at;
  if (step === stepOf(focus.page)) return { e: since, shown: 1, warm: focus.warm };
  if (step === focus.was) return { e: focus.held, shown: 1 - clamp01(since / LEAVE_FADE), warm: false };
  return { e: 0, shown: 0, warm: false };
}

/** One part coming in, `delay` after the others started: its opacity and how far below its place it is. */
export function enter(e: number, delay: number, shift: number, warm: boolean, still: boolean) {
  'worklet';
  if (warm) return { opacity: 1, y: 0 };
  const since = e - ENTER_AFTER - delay;
  if (still) return { opacity: clamp01(since / FADE), y: 0 };
  return { opacity: clamp01(since / ENTER_FADE), y: shift * (1 - easeOut(since / ENTER_RISE)) };
}

/** Step 1: the row the white fill is on, 0 to 3; between rows while it slides. */
export function rowAt(e: number, from: number, pick: Pick, t: number, still: boolean) {
  'worklet';
  if (pick.row >= 0) return still ? pick.row : pick.from + (pick.row - pick.from) * easeInOut((t - pick.at) / ROW_SLIDE);
  const steps = Math.min(3, Math.max(0, Math.floor((e - ROW_FIRST) / ROW_EVERY) + 1));
  // A card that starts over brings its fill back to the first row.
  if (steps === 0) return still ? 0 : from * (1 - easeInOut(e / ROW_SLIDE));
  if (still) return steps;
  return steps - 1 + easeInOut((e - ROW_FIRST - (steps - 1) * ROW_EVERY) / ROW_SLIDE);
}

/** Step 2: how far the front lesson card is lifted. */
export function liftAt(e: number, still: boolean) {
  'worklet';
  return still ? 0 : LIFT * bump((e - LIFT_AFTER) / LIFT_FOR);
}

/** Step 3: the panel's opacity and scale. */
export function panelAt(e: number, warm: boolean, still: boolean) {
  'worklet';
  if (warm) return { opacity: 1, scale: 1 };
  const since = e - ENTER_AFTER;
  if (still) return { opacity: clamp01(since / FADE), scale: 1 };
  return { opacity: smooth(since, 0, PANEL_IN), scale: PANEL_SCALE + (1 - PANEL_SCALE) * easeOut(since / PANEL_IN) };
}

/**
 * Step 3: how checked a square is, 0 to 1, and how much it swells while it is being checked.
 * `order` is its place among the squares that tick by themselves (-1: it stays empty), `toggled`
 * the time a finger last changed it: positive for checked, negative for cleared, 0 for never.
 */
export function tickAt(e: number, order: number, toggled: number, t: number, still: boolean) {
  'worklet';
  let q = 0;
  let rising = true;
  if (toggled > 0) q = clamp01((t - toggled) / TICK_FOR);
  else if (toggled < 0) {
    q = 1 - clamp01((t + toggled) / UNTICK_FOR);
    rising = false;
  } else if (order >= 0) q = clamp01((e - TICK_FIRST - order * TICK_EVERY) / TICK_FOR);
  return { q, pop: still || !rising ? 1 : 1 + TICK_POP * Math.sin(Math.PI * q) };
}

/** Step 3: the badge's scale; it pulses once after the last square that ticks by itself. */
export function pulseAt(e: number, ticks: number, still: boolean) {
  'worklet';
  if (still) return 1;
  const from = TICK_FIRST + Math.max(0, ticks - 1) * TICK_EVERY + TICK_FOR + PULSE_AFTER;
  return 1 + PULSE * bump((e - from) / PULSE_FOR);
}

/** How far the headline stands off its place. */
export function parallaxAt(pull: Pull, t: number, still: boolean) {
  'worklet';
  if (still) return 0;
  const back = pull.released < 0 ? 1 : 1 - easeOut((t - pull.released) / PARALLAX_BACK);
  return -PARALLAX * Math.sin((Math.PI / 2) * Math.max(-1, Math.min(1, pull.x / PITCH))) * back;
}
