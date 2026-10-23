/** Seconds of one autoPlay loop, and when in it the timer starts, the page scrolls up and back, and the timer stops. */
export const LOOP = 10;
export const START_AT = 0.8;
export const SCROLL_AT = 2.2;
export const BACK_AT = 6.2;
export const STOP_AT = 8.6;
/** Seconds after a touch before the loop starts again. */
export const USER_HOLD = 3;

/** How far the loop scrolls, in points, and for how long each way. */
export const AUTO_SCROLL = 420;
export const SCROLL_UP = 1.2;
export const SCROLL_BACK = 1;
/** Seconds the page takes to return to the top when the loop takes over again. */
export const SCROLL_HOME = 0.5;

/** Seconds from the black button to the light box, and back. */
export const START = 0.35;
export const STOP = 0.3;
/** Seconds the stopwatch takes to swell into the pill, and the pill to collapse. */
export const FORM = 0.4;
export const COLLAPSE = 0.35;
/** A changing digit: seconds, and the points the old one falls and the new one drops in from. */
export const ROLL = 0.3;
export const ROLL_SHIFT = 8;
/** Seconds the pill's time takes to fade when the pill collapses. */
export const TIME_OUT = 0.15;

/** Control points of the curves: a strong ease-out, the swell that passes its end, and the scroll. */
export const EASE_OUT = [0.22, 1, 0.36, 1] as const;
export const SWELL = [0.34, 1.3, 0.64, 1] as const;
export const GLIDE = [0.45, 0, 0.55, 1] as const;

export const MARGIN = 20;
export const HERO = 240;
export const TITLE_GAP = 24;
export const TITLE_LINE = 34;
export const SUBTITLE_GAP = 6;
export const SUBTITLE_LINE = 20;
export const CONTROL_GAP = 24;
export const CONTROL = 68;
export const CONTROL_RADIUS = 14;
export const BOX_WIDTH = 160;
/** The middle of the timer control, from the top of the page. */
export const CONTROL_CENTRE = HERO + TITLE_GAP + TITLE_LINE + SUBTITLE_GAP + SUBTITLE_LINE + CONTROL_GAP + CONTROL / 2;
export const BODY_GAP = 28;
export const BODY_LINE = 28;

/** The tab bar above the bottom inset, and the least that inset counts for. */
export const TAB_BAR = 60;
export const TAB_BOTTOM = 24;
/** The page's last lines fade out over this height above the bar. */
export const FADE = 60;
/** From the screen's edges to the middle of the outer tabs. */
export const TAB_INSET = 48;
export const TAB_ICON = 26;
export const TAB_STROKE = 1.8;
export const PILL_WIDTH = 86;
export const PILL_HEIGHT = 46;
/** The stopwatch's ring, and how far its middle sits under the middle of the icon. */
export const DIAL = 19;
export const DIAL_DROP = 1.5;
/** The stopwatch's hand, from the middle of the ring. */
export const HAND = 4.6;
/** The dash above the ring: its width, and its middle above the ring's top edge. */
export const TICK = 6;
export const TICK_GAP = 3;

/** The control has left the page when its middle is this far past an edge of what shows, and is back this far inside it. */
export const HYSTERESIS = 8;
/** What the timer counts down from, in seconds. */
export const TIMER = 900;
/** The time that rises from the pill to the box: its size at the pill, and the part of the way it fades in over. */
export const GHOST_SCALE = 13 / 19;
export const GHOST_IN = 0.15;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** "14:59" for 899 seconds. */
export function clock(seconds: number) {
  const whole = Math.max(0, Math.round(seconds));
  return `${String(Math.floor(whole / 60)).padStart(2, '0')}:${String(whole % 60).padStart(2, '0')}`;
}
