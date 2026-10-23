// A permission primer told by a pair of eyes. The screen has four pages; a change from one to the
// next is a `Stage`, and every layer is a function of the stage and of the seconds since it began.
// What a touch changes (the page, the switches) is stored; nothing is stored per frame.

export const CHAT = 0;
export const ASK = 1;
export const SWITCHES = 2;
export const DONE = 3;

/** The change from one page to another that began at `t0`, on the screen's clock. */
export type Stage = { from: number; to: number; t0: number };

// What a tap can mean. A switch is TAP_SWITCH plus its row.
export const TAP_NONE = -1;
export const TAP_CHAT = 0;
export const TAP_START = 1;
export const TAP_LATER = 2;
export const TAP_ALL = 3;
export const TAP_PILL = 4;
export const TAP_SWITCH = 5;

/** autoPlay is back at the chat every LOOP seconds. In between it taps these, at these seconds. */
export const LOOP = 10;
export const PLAY = [
  [1.5, TAP_CHAT],
  [4.4, TAP_START],
  [6.3, TAP_PILL],
  [9.2, TAP_PILL],
];
/** A loop starts with the chat this long on the screen. */
export const REST = 0.8;
/** After a touch autoPlay waits this long before it goes back to the chat and starts over. */
export const USER_HOLD = 3;
// A page takes taps once it has been arriving this long: its eyes are in place by then.
const READY = [0.75, 1.8, 1.3, 0.6];

// Sizes are in points of a 393 × 852 screen. The eyes hang from the safe area's top, the middle
// of every page from the screen's middle, and the buttons stand on the safe area's floor.
export const EYES = { top: 37, hover: -176, ask: -86 };
export const CHAT_TOP = 89;
export const BUBBLE = { side: 32, bottom: 118 };
export const ROW = { side: 40, bottom: 58, height: 48 };
export const LATER = { bottom: 18, reach: 56, height: 36 };
export const PILL = { side: 32, bottom: 10, height: 46 };
export const PHONE = { half: 136, top: -171, radius: 44, notch: 46, depth: 16, fillet: 8 };
export const TILE = { y: -66, size: 54, radius: 13, heart: 26 };
export const TITLE_Y = -16;
export const ALL = { top: 12, height: 30 };
export const LIST = { width: 226, top: 74, pitch: 23.5, bar: 152, thick: 9 };
export const TOGGLE = { width: 26, height: 15, knob: 11 };
/** The check mark of the last page; the switches shrink into its middle. */
export const MARK = { y: 44, disc: 80 };
export const LABEL_Y = 126;
/** Switches in the list. */
export const COUNT = 6;
// The order the switches' page comes in: the tile, its name, the first row, the switches, the button.
export const ITEM = { tile: 0, title: 1, all: 2, row: 3, pill: 9 };

// The eyes: small at the top, large in the home.
const SMALL = { size: 14, gap: 34, glow: 22 };
const LARGE = { size: 26, gap: 72, glow: 48 };
/** The soft light round the large eyes reaches this far. */
export const HALO = 130;
const TRAVEL = { stiffness: 120, damping: 17.5 };
// Down to the hover first, then a slower drift on into the home.
const DRIFT_AT = 0.6;
const DRIFT_FOR = 0.6;
// A blink shuts the eyes to this share of their height. Per page: the first one, and how often after.
const SHUT = 0.14;
const BLINK = 0.18;
const BLINKS = [
  [1.25, 3.4],
  [2.3, 3.1],
  [3.6, 3.3],
  [1.5, 3.2],
];
// In the home they look left, then right.
const GLANCE = 6;
const GLANCE_AT = 1.15;
const GLANCE_TURN = 0.12;
const GLANCE_HOLD = 0.25;
// Surprise at a tap on the button: they stretch, start upwards, and only then shrink.
const STRETCH = 34 / 26;
const STRETCH_IN = 0.15;
const FLY_AFTER = 0.5;
const SHRINK_AFTER = 0.6;
const STRETCH_OUT = 0.25;
// Pleased with the last page: two arcs for a moment.
const SMILE_AT = 0.15;
const SMILE_IN = 0.12;
const SMILE_FOR = 0.4;
// A "no" for Later, a look down at a switch that flips, and a look towards a finger.
const SHAKE = 7;
const SHAKE_FOR = 0.6;
const SHAKE_TURNS = 2;
const LOOK_DOWN = 3;
const LOOK_DOWN_IN = 0.2;
const LOOK_DOWN_FOR = 0.9;
const LOOK_DOWN_OUT = 0.25;
const LOOK = 4;
const LOOK_REACH = 160;
const LOOK_IN = 0.15;
const LOOK_OUT = 0.4;

// The chat leaves upwards and comes back from below.
const CHAT_OUT = 0.3;
const CHAT_LIFT = 24;
const CHAT_AFTER = 0.15;
const CHAT_IN = 0.6;
const CHAT_RISE = 28;
// The ask: the message, the button and Later rise in one after the other; then the home grows round the eyes.
const ASK_AT = 0.9;
const ASK_IN = 0.4;
const ASK_GAP = 0.08;
const ASK_RISE = 12;
const ASK_OUT = 0.25;
const HOUSE_AT = 1.7;
const HOUSE_IN = 0.5;
const HOUSE_FROM = 0.6;
// The home: a house round the eyes' place, with rain falling inside it. The rain repeats every
// RAIN.period points, which takes two seconds: a loop is a whole number of them.
const HOUSE = { width: 170, above: 105, below: 85, roof: 56, round: 10 };
const RAIN = { speed: 20, period: 40, pitch: 16, step: 5, head: 1.1, taper: 0.15 };
const RAIN_PHASE = [0, 22, 9, 31, 15, 36, 4, 26, 12, 33];
const RAIN_STREAK = [4, 3, 5, 3, 4, 5, 3, 4, 5, 3];
// The switches: the phone's outline draws itself from the notch, then its content comes in.
const SWITCHES_AT = 1;
const OUTLINE_FOR = 0.6;
const ITEM_GAP = 0.05;
const ITEM_IN = 0.3;
const ITEM_RISE = 6;
const LEAVE = 0.3;
// A knob slides over with a small overshoot. "All" flips the rows one after the other.
const KNOB_SPRING = { stiffness: 300, damping: 22 };
const KNOB = 0.25;
const STAGGER = 0.09;
const LABEL_FADE = 0.2;
/** Every switch on: the last page follows this long after the last knob has landed. */
const DONE_AFTER = 0.4;
// The last page: the switches shrink into the middle, the check pops and draws itself.
const SHRINK = 0.7;
const SHRINK_FOR = 0.3;
const POP_AT = 0.15;
const POP_FROM = 0.5;
const POP = { stiffness: 200, damping: 13 };
const POP_IN = 0.15;
const DRAW_AT = 0.3;
const DRAW_FOR = 0.3;
const LABEL_AT = 0.45;
const LABEL_IN = 0.3;
// A tapped button gives a little.
const PRESS = 0.96;
const PRESS_IN = 0.08;
const PRESS_OUT = 0.24;
// Reduced motion: a page fades out, the next one in, each as it looks when everything has arrived.
const FADE = 0.3;
const SETTLED = 100;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t) * (1 - t);
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** How far a spring of mass 1 has come from 0 to 1 after `t` seconds; it overshoots a little. */
function spring(t: number, { stiffness, damping }: { stiffness: number; damping: number }) {
  'worklet';
  if (t <= 0) return 0;
  const w = Math.sqrt(stiffness);
  const z = damping / (2 * w);
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}

/** What is drawn at `now`: the stage and the seconds into it, and how much of the pages shows. */
export function seen(stage: Stage, now: number, still: boolean) {
  'worklet';
  const e = now - stage.t0;
  if (!still) return { from: stage.from, to: stage.to, e, opacity: 1 };
  const page = e < FADE ? stage.from : stage.to;
  return { from: page, to: page, e: SETTLED, opacity: e < FADE * 2 ? Math.abs(e - FADE) / FADE : 1 };
}

/** Whether the page takes taps yet, `e` seconds into its stage. */
export function ready(to: number, e: number) {
  'worklet';
  return e >= READY[to];
}

/** How far the eyes are shut, 0 to 1. */
function blink(e: number, first: number, every: number) {
  'worklet';
  if (e < first) return 0;
  const u = ((e - first) % every) / BLINK;
  return u >= 1 ? 0 : 1 - Math.abs(2 * u - 1);
}

/**
 * The eyes: where their middle is (`dx` off the screen's), their size, the gap between their
 * centres, how far their glow reaches, how much of the large halo shows, their height as a share
 * of their width, and how much they are a smile. `top`, `hover` and `ask` are the heights of
 * their three places. `still` leaves out everything they do by themselves.
 */
export function eyes(from: number, to: number, e: number, still: boolean, top: number, hover: number, ask: number) {
  'worklet';
  let large = 0;
  let y = top;
  if (to === ASK) {
    large = spring(e, TRAVEL);
    y = top + (hover - top) * large + (ask - hover) * easeInOut(clamp01((e - DRIFT_AT) / DRIFT_FOR));
  } else if (from === ASK) {
    const startled = to === SWITCHES;
    large = 1 - spring(e - (startled ? SHRINK_AFTER : 0), TRAVEL);
    y = ask + (top - ask) * spring(e - (startled ? FLY_AFTER : 0), TRAVEL);
  }
  let tall = 1;
  let dx = 0;
  let smile = 0;
  if (!still) {
    tall = 1 - (1 - SHUT) * blink(e, BLINKS[to][0], BLINKS[to][1]);
    if (to === ASK) {
      const right = GLANCE_AT + GLANCE_HOLD;
      const back = right + GLANCE_HOLD;
      dx = GLANCE * (2 * smooth(e, right, right + GLANCE_TURN) - smooth(e, GLANCE_AT, GLANCE_AT + GLANCE_TURN) - smooth(e, back, back + GLANCE_TURN));
    }
    if (from === ASK && to === SWITCHES) {
      tall *= 1 + (STRETCH - 1) * smooth(e, 0, STRETCH_IN) * (1 - smooth(e, SHRINK_AFTER, SHRINK_AFTER + STRETCH_OUT));
    }
    if (to === DONE) {
      smile = smooth(e, SMILE_AT, SMILE_AT + SMILE_IN) * (1 - smooth(e, SMILE_AT + SMILE_FOR, SMILE_AT + SMILE_FOR + SMILE_IN));
    }
  }
  return {
    dx,
    y,
    size: SMALL.size + (LARGE.size - SMALL.size) * large,
    gap: SMALL.gap + (LARGE.gap - SMALL.gap) * large,
    glow: SMALL.glow + (LARGE.glow - SMALL.glow) * large,
    halo: clamp01(large),
    tall,
    smile,
  };
}

/** The eyes' "no", `t` seconds after Later was tapped: how far to the side they are. */
export function shake(t: number) {
  'worklet';
  if (t < 0 || t > SHAKE_FOR) return 0;
  const u = t / SHAKE_FOR;
  return SHAKE * Math.sin(2 * Math.PI * SHAKE_TURNS * u) * (1 - u);
}

/** How far down the eyes look, `t` seconds after a switch was flipped. */
export function lookDown(t: number) {
  'worklet';
  return LOOK_DOWN * smooth(t, 0, LOOK_DOWN_IN) * (1 - smooth(t, LOOK_DOWN_FOR, LOOK_DOWN_FOR + LOOK_DOWN_OUT));
}

/** How far the eyes look towards a finger that is `d` points to one side of them. */
export function toward(d: number) {
  'worklet';
  return LOOK * Math.max(-1, Math.min(1, d / LOOK_REACH));
}

/** How much of that look shows, `t` seconds after the finger came down (`held`) or left. */
export function follow(held: boolean, t: number) {
  'worklet';
  return held ? smooth(t, 0, LOOK_IN) : 1 - smooth(t, 0, LOOK_OUT);
}

/** The chat: how much shows, and how far under its place it is. */
export function chat(from: number, to: number, e: number) {
  'worklet';
  if (to === CHAT) {
    const u = clamp01((e - CHAT_AFTER) / CHAT_IN);
    return { opacity: u, shift: CHAT_RISE * (1 - easeOut(u)) };
  }
  if (from === CHAT) {
    const u = clamp01(e / CHAT_OUT);
    return { opacity: 1 - u, shift: -CHAT_LIFT * u * u };
  }
  return { opacity: 0, shift: 0 };
}

/** A part of the ask (0 the message, 1 the button, 2 Later): how much shows, and how far under its place it is. */
export function ask(from: number, to: number, e: number, part: number) {
  'worklet';
  if (to === ASK) {
    const u = clamp01((e - ASK_AT - part * ASK_GAP) / ASK_IN);
    return { opacity: u, shift: ASK_RISE * (1 - easeOut(u)) };
  }
  return { opacity: from === ASK ? 1 - clamp01(e / ASK_OUT) : 0, shift: 0 };
}

/** The home round the eyes: how much shows, and its scale. */
export function house(from: number, to: number, e: number) {
  'worklet';
  if (to === ASK) {
    const u = clamp01((e - HOUSE_AT) / HOUSE_IN);
    return { opacity: u, scale: HOUSE_FROM + (1 - HOUSE_FROM) * easeOut(u) };
  }
  return { opacity: from === ASK ? 1 - clamp01(e / ASK_OUT) : 0, scale: 1 };
}

/** How far the rain has fallen at `now`, on the screen's clock. */
export function rain(now: number, still: boolean) {
  'worklet';
  return still ? 0 : (now * RAIN.speed) % RAIN.period;
}

/** The phone's outline: how much shows, and how much of it is drawn. */
export function outline(from: number, to: number, e: number) {
  'worklet';
  if (to === SWITCHES) return { opacity: 1, drawn: easeInOut(clamp01((e - SWITCHES_AT) / OUTLINE_FOR)) };
  if (to === DONE) return { opacity: 1, drawn: 1 };
  return { opacity: from === SWITCHES || from === DONE ? 1 - clamp01(e / LEAVE) : 0, drawn: 1 };
}

/** A thing on the switches' page, `index` in ITEM's order: how much shows, how far under its place it is, and its scale about the check mark. */
export function item(from: number, to: number, e: number, index: number) {
  'worklet';
  if (to === SWITCHES) {
    const u = clamp01((e - SWITCHES_AT - index * ITEM_GAP) / ITEM_IN);
    return { opacity: u, rise: ITEM_RISE * (1 - easeOut(u)), scale: 1 };
  }
  if (from !== SWITCHES) return { opacity: 0, rise: 0, scale: 1 };
  const u = clamp01(e / SHRINK_FOR);
  return { opacity: 1 - u, rise: 0, scale: to === DONE ? 1 + (SHRINK - 1) * u * u : 1 };
}

/** The button under the phone, which stays for the last page: how much shows. */
export function pill(from: number, to: number, e: number) {
  'worklet';
  if (to === SWITCHES) return clamp01((e - SWITCHES_AT - ITEM.pill * ITEM_GAP) / ITEM_IN);
  if (to === DONE) return 1;
  return from === SWITCHES || from === DONE ? 1 - clamp01(e / LEAVE) : 0;
}

/** The check mark: how much shows, its scale, how much of the stroke is drawn, and its label. */
export function check(from: number, to: number, e: number) {
  'worklet';
  if (to === DONE) {
    const label = clamp01((e - LABEL_AT) / LABEL_IN);
    return {
      opacity: smooth(e, POP_AT, POP_AT + POP_IN),
      scale: POP_FROM + (1 - POP_FROM) * spring(e - POP_AT, POP),
      drawn: easeOut(clamp01((e - DRAW_AT) / DRAW_FOR)),
      label,
      rise: ITEM_RISE * (1 - easeOut(label)),
    };
  }
  const left = from === DONE ? 1 - clamp01(e / LEAVE) : 0;
  return { opacity: left, scale: 1, drawn: 1, label: left, rise: 0 };
}

/** The switches: which are on, when each was last flipped, whether all are on and since when, when the last page is due, and the last touch of any. */
export type Switches = { on: boolean[]; at: number[]; all: boolean; allAt: number; doneAt: number; flippedAt: number };

export const ALL_ON = -1;
export const ALL_OFF = -2;

export function switchesOff(): Switches {
  'worklet';
  const on: boolean[] = [];
  const at: number[] = [];
  for (let i = 0; i < COUNT; i++) {
    on.push(false);
    at.push(-SETTLED);
  }
  return { on, at, all: false, allAt: -SETTLED, doneAt: Infinity, flippedAt: -SETTLED };
}

/** The switches after a flip of row `which`, or of all of them, at `at`. A knob that is still sliding is left alone. */
export function flip(s: Switches, which: number, at: number): Switches {
  'worklet';
  if (at < (which < 0 ? Math.max(...s.at) : s.at[which]) + KNOB) return s;
  const on = s.on.slice();
  const times = s.at.slice();
  for (let i = 0; i < COUNT; i++) {
    const next = which === ALL_ON ? true : which === ALL_OFF ? false : i === which ? !on[i] : on[i];
    if (next !== on[i]) {
      on[i] = next;
      times[i] = at + (which < 0 ? i * STAGGER : 0);
    }
  }
  const all = on.every((one) => one);
  return {
    on,
    at: times,
    all,
    allAt: all === s.all ? s.allAt : at,
    doneAt: all ? Math.max(...times) + KNOB + DONE_AFTER : Infinity,
    flippedAt: at,
  };
}

/** Where a switch's knob is, 0 off to 1 on, `t` seconds after its flip. */
export function knob(on: boolean, t: number, still: boolean) {
  'worklet';
  const p = still ? (t >= 0 ? 1 : 0) : spring(t, KNOB_SPRING);
  return on ? p : 1 - p;
}

/** How much the first row reads "Turn off all". */
export function allOn(s: Switches, now: number) {
  'worklet';
  const u = smooth(now - s.allAt, 0, LABEL_FADE);
  return s.all ? u : 1 - u;
}

/** A button's scale, `t` seconds after it was tapped. */
export function pressed(t: number) {
  'worklet';
  return 1 - (1 - PRESS) * smooth(t, 0, PRESS_IN) * (1 - smooth(t, PRESS_IN, PRESS_IN + PRESS_OUT));
}

/** What a tap at (x, y) means on a page. `floor` is the height the buttons stand on. */
export function hit(page: number, x: number, y: number, width: number, height: number, floor: number) {
  'worklet';
  const side = Math.abs(x - width / 2);
  const middle = height / 2;
  if (page === CHAT) return TAP_CHAT;
  if (page === ASK) {
    if (side <= width / 2 - ROW.side && y >= floor - ROW.bottom - ROW.height && y <= floor - ROW.bottom) return TAP_START;
    if (side <= LATER.reach && Math.abs(y - (floor - LATER.bottom)) <= LATER.height / 2) return TAP_LATER;
    return TAP_NONE;
  }
  if (side <= width / 2 - PILL.side && y >= floor - PILL.bottom - PILL.height && y <= floor - PILL.bottom) return TAP_PILL;
  if (page === SWITCHES && side <= LIST.width / 2) {
    if (y >= middle + ALL.top && y <= middle + ALL.top + ALL.height) return TAP_ALL;
    const row = Math.floor((y - middle - LIST.top + LIST.pitch / 2) / LIST.pitch);
    if (row >= 0 && row < COUNT) return TAP_SWITCH + row;
  }
  return TAP_NONE;
}

// The drawings. Each stack turns the same numbers into its own kind of path.

/** The home's corners round the eyes' place, clockwise from the roof's tip; y grows downwards. */
export const HOUSE_CORNERS = [
  [0, -HOUSE.above],
  [HOUSE.width / 2, HOUSE.roof - HOUSE.above],
  [HOUSE.width / 2, HOUSE.below],
  [-HOUSE.width / 2, HOUSE.below],
  [-HOUSE.width / 2, HOUSE.roof - HOUSE.above],
];

/** The home's outline: every corner is cut back along both of its sides and curved. */
export function housePath() {
  const n = HOUSE_CORNERS.length;
  let d = '';
  for (let i = 0; i < n; i++) {
    const [x, y] = HOUSE_CORNERS[i];
    const [px, py] = HOUSE_CORNERS[(i + n - 1) % n];
    const [nx, ny] = HOUSE_CORNERS[(i + 1) % n];
    const a = HOUSE.round / Math.hypot(px - x, py - y);
    const b = HOUSE.round / Math.hypot(nx - x, ny - y);
    d += `${i === 0 ? 'M' : 'L'}${x + (px - x) * a} ${y + (py - y) * a}Q${x} ${y} ${x + (nx - x) * b} ${y + (ny - y) * b}`;
  }
  return `${d}Z`;
}

/** The rain's drops as [x, y, radius] round the eyes' place: streaks in columns, with a period to spare above the home. */
export function rainDrops() {
  const drops: number[][] = [];
  const columns = RAIN_PHASE.length;
  for (let column = 0; column < columns; column++) {
    const x = (column - (columns - 1) / 2) * RAIN.pitch;
    for (let y = RAIN_PHASE[column] - HOUSE.above - RAIN.period; y < HOUSE.below + RAIN.step * RAIN_STREAK[column]; y += RAIN.period) {
      for (let drop = 0; drop < RAIN_STREAK[column]; drop++) drops.push([x, y - drop * RAIN.step, RAIN.head - drop * RAIN.taper]);
    }
  }
  return drops;
}

/** The right half of the phone's outline, from the middle of its notch out and down to `bottom`. */
export function outlinePath(middle: number, top: number, bottom: number) {
  const { half, radius, notch, depth, fillet } = PHONE;
  return (
    `M${middle} ${top + depth}H${middle + notch - fillet}Q${middle + notch} ${top + depth} ${middle + notch} ${top + depth - fillet}` +
    `Q${middle + notch} ${top} ${middle + notch + fillet} ${top}H${middle + half - radius}Q${middle + half} ${top} ${middle + half} ${top + radius}V${bottom}`
  );
}

/** A heart in a box from -1 to 1: where it starts, and its curves as two control points and an end. */
export const HEART_START = [0, 0.9];
export const HEART_CURVES = [
  [-0.25, 0.68, -1, 0.25, -1, -0.3],
  [-1, -0.68, -0.72, -0.9, -0.45, -0.9],
  [-0.25, -0.9, -0.08, -0.78, 0, -0.62],
  [0.08, -0.78, 0.25, -0.9, 0.45, -0.9],
  [0.72, -0.9, 1, -0.68, 1, -0.3],
  [1, 0.25, 0.25, 0.68, 0, 0.9],
];

export function heartPath() {
  return `M${HEART_START[0]} ${HEART_START[1]}${HEART_CURVES.map((c) => `C${c.join(' ')}`).join('')}Z`;
}

/** The check mark's three corners round its middle, and its stroke. */
export const CHECK = [-17, 0, -6, 11, 17, -12];
export const CHECK_STROKE = 2.5;
/** A smiling eye, one unit wide: an arc from corner to corner over a control point, and its stroke. */
export const SMILE = [-0.5, 0.18, 0, -0.55, 0.5, 0.18];
export const SMILE_STROKE = 0.26;
