import { Easing } from 'react-native-reanimated';

/** One loop of the autoPlay, in seconds. Every ambient period divides it, so a loop ends as it began. */
export const LOOP = 12;
// The autoPlay's timeline, in seconds from the start of a loop: Select files is pressed twice (the
// second file fails at 64%), its row is retried, and Continue sends both away.
export const AUTO_SELECTS = [0.8, 1.75];
export const PRESS_FOR = 0.12;
export const AUTO_RETRY = 4.7;
/** The row the autoPlay retries: the second one, whose first upload fails. */
export const AUTO_RETRY_ROW = 1;
export const AUTO_CONTINUE = 8.3;
/** Seconds after the last touch before the autoPlay takes over again. */
export const USER_HOLD = 4;
/** Row ids the autoPlay uses, out of the way of the ones touches hand out. */
export const AUTO_ID = 1_000_000;

export type FileItem = {
  name: string;
  /** The share at which its first upload stops and fails; 1 when it succeeds. */
  failAt: number;
};

/** The files Select files hands out, in turn. A file that failed always succeeds on Retry. */
export const FILES: FileItem[] = [
  { name: 'Website_styles.pdf', failAt: 1 },
  { name: 'profile_styles.css', failAt: 0.64 },
  { name: 'brand_guide.fig', failAt: 1 },
  { name: 'hero_photo.png', failAt: 0.4 },
];
/** The list holds this many rows; Select files is disabled while it is full. */
export const MAX_ROWS = 3;

export type Spring = { mass: number; stiffness: number; damping: number };
/** A button coming back up after a press. */
export const RELEASE: Spring = { mass: 1, stiffness: 380, damping: 20 };
/** The clip badge's shake when a file is added: a couple of wobbles. */
export const SHAKE: Spring = { mass: 1, stiffness: 300, damping: 9 };
/** A new row opening its height. */
export const ROW_GROW: Spring = { mass: 1, stiffness: 260, damping: 24 };
/** The green disc of a finished upload, overshooting a little. */
export const DONE_POP: Spring = { mass: 1, stiffness: 320, damping: 15 };
/** The "!" of a failed upload. */
export const BANG_POP: Spring = { mass: 1, stiffness: 400, damping: 14 };

/** Seconds an upload takes from 0 to 100%. */
export const UPLOAD = 1.6;
export const UPLOAD_EASE = Easing.bezierFn(0.45, 0, 0.2, 1);
export const EASE_OUT = Easing.bezierFn(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = Easing.bezierFn(0.65, 0, 0.35, 1);

// Ambient, always running: the zone's glow breathes, its hairlines drift, the badge's halo pulses.
export const BREATHE = 4;
export const GLOW_LOW = 0.82;
export const GLOW_HIGH = 1;
/** Reduced motion: the glow holds still at this level. */
export const GLOW_STILL = 1;
/** Seconds the hairlines take to drift one spacing to the right. */
export const DRIFT = 6;
export const HALO = 3;
export const HALO_SCALE_LOW = 0.92;
export const HALO_SCALE_HIGH = 1.08;
export const HALO_ALPHA_LOW = 0.22;
export const HALO_ALPHA_HIGH = 0.34;
export const HALO_ALPHA_STILL = 0.28;

/** Seconds a button takes to go down when pressed, and how far each one goes. */
export const PRESS_IN = 0.1;
export const PILL_PRESS = 0.95;
export const RETRY_PRESS = 0.94;
export const BUTTON_PRESS = 0.97;

// A file added: the zone flares, the badge shakes and a ring leaves it.
export const FLARE_FOR = 0.7;
/** The strength of the second glow a flare lays over the zone's at its start. */
export const FLARE_GLOW = 0.45;
export const FLARE_SCALE = 0.012;
export const SHAKE_ANGLE = -18;
export const PULSE_FOR = 0.7;
/** The ring grows from the badge's size to this many times it. */
export const PULSE_GROW = 2.6;
export const PULSE_ALPHA = 0.5;

// A new row: its height opens on ROW_GROW; its content comes in from above, a little small.
export const ROW_IN = 0.25;
export const ROW_IN_DELAY = 0.04;
export const ROW_IN_Y = -14;
export const ROW_IN_SCALE = 0.94;

// Done: the ring turns into the green disc, the check draws, the row's fill fades.
export const DONE_SCALE = 0.6;
/** The ring fades out and the disc fades in over these, as the one turns into the other. */
export const RING_OUT = 0.1;
export const DISC_FADE = 0.1;
export const CHECK_DELAY = 0.06;
export const CHECK_DRAW = 0.28;
export const FILL_OUT = 0.3;

// Failed: the fill turns red and drains, the ring shrinks, the row shakes, the pill and Retry come in.
/** Seconds the fill takes to turn red. */
export const FAIL_TINT = 0.12;
export const DRAIN = 0.35;
export const RING_SHRINK = 0.15;
export const ROW_SHAKE_FOR = 0.6;
export const ROW_SHAKE_AMP = 7;
export const ROW_SHAKE_DECAY = 7;
export const ROW_SHAKE_HZ = 6;
export const PILL_OPEN = 0.32;
export const RETRY_IN = 0.25;
export const RETRY_IN_DELAY = 0.1;
export const RETRY_IN_X = 10;

// Retry: the arrows spin one turn, the pill closes, Retry fades, and the upload restarts.
export const SPIN = 0.55;
export const PILL_CLOSE = 0.2;
export const RETRY_OUT = 0.2;
export const RESTART = 0.15;

// Continue: enabled, it brightens and a sheen crosses it; pressed, it reads "Uploaded" for a while.
export const ENABLE_FADE = 0.3;
export const SHEEN_DELAY = 0.1;
export const SHEEN_FOR = 0.8;
export const LABEL_FADE = 0.2;
export const UPLOADED_FOR = 0.9;
/** Disabled labels and borders are drawn at this opacity. */
export const DISABLED_ALPHA = 0.4;
/** Seconds a button takes to go from enabled to disabled and back. */
export const ENABLE_SWAP = 0.2;

// Rows leaving, from the bottom one up.
export const STAGGER = 0.07;
export const LEAVE_FADE = 0.22;
export const LEAVE_Y = -10;
export const LEAVE_SCALE = 0.96;
export const COLLAPSE = 0.3;

/** Reduced motion: every change is a fade of this length, in place. */
export const FADE = 0.2;
/** Seconds after the last row is gone before the autoPlay starts a loop again. */
export const SETTLE = 1.2;

export const NEVER = -1e6;
export const FOREVER = 1e9;

/** One file in the list. Every time is on the clock, in seconds; FOREVER is "not (yet) planned". */
export type Row = {
  id: number;
  /** Its index in FILES. */
  file: number;
  addedAt: number;
  /** The current upload starts (a Retry starts a new one RESTART after its release). */
  startAt: number;
  /** The current upload fails at this time and share, or finishes at doneAt. */
  failAt: number;
  failPoint: number;
  doneAt: number;
  /** The failure before a Retry, and the Retry's press. */
  lastFailAt: number;
  retryDown: number;
  retryUp: number;
  /** It starts leaving at leaveAt + leaveDelay. */
  leaveAt: number;
  leaveDelay: number;
};

/** Everything that happened, as times on the clock in seconds. */
export type Events = {
  rows: Row[];
  nextId: number;
  nextFile: number;
  /** Select files' press, and the last release (on it or the zone) that added a file. */
  pillDown: number;
  pillUp: number;
  addAt: number;
  cancelDown: number;
  cancelUp: number;
  continueDown: number;
  continueUp: number;
  /** Continue was pressed while enabled: it reads "Uploaded" from then on for UPLOADED_FOR. */
  uploadedAt: number;
  /** The rows last started leaving (all of them leave together). */
  clearAt: number;
  /** Continue last became enabled, and disabled. */
  enabledAt: number;
  disabledAt: number;
};

export function restEvents(nextId = 0): Events {
  'worklet';
  return {
    rows: [],
    nextId,
    nextFile: 0,
    pillDown: NEVER,
    pillUp: NEVER,
    addAt: NEVER,
    cancelDown: NEVER,
    cancelUp: NEVER,
    continueDown: NEVER,
    continueUp: NEVER,
    uploadedAt: NEVER,
    clearAt: NEVER,
    enabledAt: NEVER,
    disabledAt: NEVER,
  };
}

export function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

/** A spring let go from 0 towards 1 at rest, `t` seconds later (the closed form, so every stack agrees). */
export function spring(t: number, s: Spring) {
  'worklet';
  if (t <= 0) return 0;
  const w0 = Math.sqrt(s.stiffness / s.mass);
  const zeta = s.damping / (2 * Math.sqrt(s.stiffness * s.mass));
  if (zeta >= 1) return 1 - Math.exp(-w0 * t) * (1 + w0 * t);
  const wd = w0 * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w0 * t) * (Math.cos(wd * t) + ((zeta * w0) / wd) * Math.sin(wd * t));
}

/** Seconds into an upload at which its curve reaches `share` (the curve only rises, so halve the interval). */
export function timeToReach(share: number) {
  'worklet';
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 30; i++) {
    const mid = (lo + hi) / 2;
    if (UPLOAD_EASE(mid) < share) lo = mid;
    else hi = mid;
  }
  return ((lo + hi) / 2) * UPLOAD;
}

/** The same press seen as a scale: down over PRESS_IN, back up on RELEASE. */
export function pressScale(down: number, up: number, now: number, depth: number) {
  'worklet';
  let press = 0;
  if (down > up) press = EASE_OUT(clamp01((now - down) / PRESS_IN));
  else press = EASE_OUT(clamp01((up - down) / PRESS_IN)) * (1 - spring(now - up, RELEASE));
  return 1 - (1 - depth) * press;
}

// ——— Ambient (`t` is seconds since the block first drew) ———

export function wave(t: number, period: number) {
  'worklet';
  return (1 - Math.cos((2 * Math.PI * t) / period)) / 2;
}

/** How far the hairlines have drifted, in points (0 up to one spacing). */
export function drift(t: number, spacing: number, still: boolean) {
  'worklet';
  if (still) return 0;
  const k = t / DRIFT;
  return (k - Math.floor(k)) * spacing;
}

// ——— The list ———

/** "u" uploading (or about to restart), "d" done, "f" failed, "l" leaving. */
export function rowStatus(row: Row, now: number) {
  'worklet';
  if (row.leaveAt <= now) return 'l';
  if (row.doneAt <= now) return 'd';
  if (row.failAt <= now) return 'f';
  return 'u';
}

export function activeCount(rows: Row[], now: number) {
  'worklet';
  let n = 0;
  for (let i = 0; i < rows.length; i++) if (rows[i].leaveAt > now) n++;
  return n;
}

/** Seconds after a row starts leaving until it takes no room. */
export function leaveFor(still: boolean) {
  'worklet';
  return still ? FADE : Math.max(COLLAPSE, LEAVE_FADE);
}

/** Every row in the list is done (and there is one): Continue is enabled. */
export function ready(e: Events, now: number) {
  'worklet';
  let any = false;
  for (const row of e.rows) {
    if (row.leaveAt <= now) continue;
    if (row.doneAt > now) return false;
    any = true;
  }
  return any;
}

/** Adds the next file at `at` and starts its upload, unless the list is full. */
export function addRow(e: Events, at: number): Events {
  'worklet';
  if (activeCount(e.rows, at) >= MAX_ROWS) return e;
  const file = e.nextFile;
  const point = FILES[file].failAt;
  const fails = point < 1;
  const row: Row = {
    id: e.nextId,
    file,
    addedAt: at,
    startAt: at,
    failAt: fails ? at + timeToReach(point) : FOREVER,
    failPoint: point,
    doneAt: fails ? FOREVER : at + UPLOAD,
    lastFailAt: NEVER,
    retryDown: NEVER,
    retryUp: NEVER,
    leaveAt: FOREVER,
    leaveDelay: 0,
  };
  return {
    ...e,
    rows: [...e.rows, row],
    nextId: e.nextId + 1,
    nextFile: (file + 1) % FILES.length,
    addAt: at,
  };
}

function mapRow(e: Events, id: number, change: (row: Row) => Row): Events {
  'worklet';
  return {
    ...e,
    rows: e.rows.map((row) => (row.id === id ? change(row) : row)),
  };
}

/** Retry goes down on a failed row. */
export function retryDown(e: Events, id: number, at: number): Events {
  'worklet';
  return mapRow(e, id, (row) => (rowStatus(row, at) === 'f' ? { ...row, retryDown: at } : row));
}

/** Retry comes up: the row's upload restarts RESTART later, and this time it succeeds. */
export function retryUp(e: Events, id: number, at: number): Events {
  'worklet';
  return mapRow(e, id, (row) =>
    rowStatus(row, at) === 'f'
      ? {
          ...row,
          retryUp: at,
          lastFailAt: row.failAt,
          startAt: at + RESTART,
          failAt: FOREVER,
          doneAt: at + RESTART + UPLOAD,
        }
      : row,
  );
}

/** Retry let go without a press (dragged off): it comes back up and nothing restarts. */
export function retryCancel(e: Events, id: number): Events {
  'worklet';
  return mapRow(e, id, (row) => (row.retryDown > row.retryUp ? { ...row, retryDown: row.retryUp } : row));
}

/** The presses the autoPlay has in progress, dropped when a touch takes over. */
export function dropPresses(e: Events): Events {
  'worklet';
  return {
    ...e,
    pillDown: Math.min(e.pillDown, e.pillUp),
    continueDown: Math.min(e.continueDown, e.continueUp),
    rows: e.rows.map((row) => ({ ...row, retryDown: Math.min(row.retryDown, row.retryUp) })),
  };
}

/** Every row still in the list starts leaving at `at`, the bottom one first. */
export function leaveAll(e: Events, at: number): Events {
  'worklet';
  const rows = e.rows.slice();
  let below = 0;
  for (let i = rows.length - 1; i >= 0; i--) {
    if (rows[i].leaveAt <= at) continue;
    rows[i] = { ...rows[i], leaveAt: at, leaveDelay: below * STAGGER };
    below++;
  }
  return { ...e, rows, clearAt: at };
}

/** Continue comes up while enabled: it reads "Uploaded", then the rows leave. */
export function sendAll(e: Events, at: number): Events {
  'worklet';
  if (!ready(e, at) || at < e.uploadedAt + UPLOADED_FOR) return e;
  return leaveAll({ ...e, uploadedAt: at }, at + UPLOADED_FOR);
}

/**
 * After touches: notes when Continue becomes enabled or disabled (at the time that caused it), and
 * drops rows that have left. Returns `e` itself when nothing changed.
 */
export function tidy(e: Events, now: number, still: boolean): Events {
  'worklet';
  let next = e;
  const on = e.enabledAt > e.disabledAt;
  const isReady = ready(e, now);
  if (isReady && !on) {
    let at = NEVER;
    for (const row of e.rows) if (row.leaveAt > now) at = Math.max(at, row.doneAt);
    next = { ...next, enabledAt: at };
  } else if (!isReady && on) {
    let at = e.clearAt <= now ? e.clearAt : NEVER;
    for (const row of e.rows) if (row.leaveAt > now && row.doneAt > now) at = Math.max(at, row.addedAt);
    next = { ...next, disabledAt: at > NEVER ? at : now };
  }
  const gone = leaveFor(still);
  let drop = false;
  for (const row of next.rows) if (now >= row.leaveAt + row.leaveDelay + gone) drop = true;
  if (drop)
    next = {
      ...next,
      rows: next.rows.filter((row) => now < row.leaveAt + row.leaveDelay + gone),
    };
  return next;
}

/** Something is still going on that the autoPlay should not cut short. */
export function busy(e: Events, now: number) {
  'worklet';
  if (e.pillDown > e.pillUp || e.cancelDown > e.cancelUp || e.continueDown > e.continueUp) return true;
  if (now < e.uploadedAt + UPLOADED_FOR) return true;
  for (const row of e.rows) {
    if (row.retryDown > row.retryUp) return true;
    if (row.leaveAt > now && rowStatus(row, now) === 'u') return true;
  }
  return false;
}

/** The events of the autoPlay at `now` for a loop that started at `start`: its presses replayed in order. */
export function autoEvents(now: number, start: number): Events {
  'worklet';
  const tau = (now - start) % LOOP;
  const base = now - tau;
  let e = restEvents(AUTO_ID);
  for (const at of AUTO_SELECTS) {
    if (tau >= at) e = { ...e, pillDown: base + at };
    if (tau >= at + PRESS_FOR) e = addRow({ ...e, pillUp: base + at + PRESS_FOR }, base + at + PRESS_FOR);
  }
  const retried = e.rows[AUTO_RETRY_ROW];
  if (retried && tau >= AUTO_RETRY) e = retryDown(e, retried.id, base + AUTO_RETRY);
  if (retried && tau >= AUTO_RETRY + PRESS_FOR) e = retryUp(e, retried.id, base + AUTO_RETRY + PRESS_FOR);
  if (ready(e, now)) {
    let at = NEVER;
    for (const row of e.rows) at = Math.max(at, row.doneAt);
    e = { ...e, enabledAt: at };
  }
  if (tau >= AUTO_CONTINUE) e = { ...e, continueDown: base + AUTO_CONTINUE };
  if (tau >= AUTO_CONTINUE + PRESS_FOR) {
    const up = base + AUTO_CONTINUE + PRESS_FOR;
    e = sendAll({ ...e, continueUp: up }, up);
    if (now >= up + UPLOADED_FOR) e = { ...e, disabledAt: up + UPLOADED_FOR };
  }
  return e;
}

// ——— What each part looks like at `now` ———

/** The share of its full height (and gap) a row takes: it opens when added and closes when it leaves. */
export function rowSlot(row: Row, now: number, still: boolean) {
  'worklet';
  const grown = still ? EASE_IN_OUT(clamp01((now - row.addedAt) / FADE)) : spring(now - row.addedAt, ROW_GROW);
  const t = now - row.leaveAt - row.leaveDelay;
  const collapse = EASE_IN_OUT(clamp01(t / (still ? FADE : COLLAPSE)));
  return grown * (1 - collapse);
}

export type RowLook = {
  /** Its top, from the top of the list. */
  top: number;
  /** The height its box has opened to, and where its content sits in it. */
  boxHeight: number;
  boxOpacity: number;
  boxY: number;
  boxScale: number;
  shakeX: number;
  contentOpacity: number;
  contentY: number;
  contentScale: number;
  /** The upload fill: how far it reaches, and how much of it is the normal and the red colour. */
  fill: number;
  fillAlpha: number;
  redAlpha: number;
  ring: number;
  ringScale: number;
  ringAlpha: number;
  discScale: number;
  discAlpha: number;
  check: number;
  pill: number;
  pillAlpha: number;
  bang: number;
  retryAlpha: number;
  retryX: number;
  retryScale: number;
  spin: number;
};

export function rowLook(rows: Row[], index: number, now: number, still: boolean, rowHeight: number, gap: number): RowLook {
  'worklet';
  const row = rows[index];
  let top = 0;
  for (let i = 0; i < index; i++) top += rowSlot(rows[i], now, still) * (rowHeight + gap);

  // Arriving.
  const since = now - row.addedAt;
  const grown = still ? 1 : spring(since, ROW_GROW);
  const arrive = still ? clamp01(since / FADE) : EASE_OUT(clamp01((since - ROW_IN_DELAY) / ROW_IN));
  // Leaving.
  const leave = clamp01((now - row.leaveAt - row.leaveDelay) / (still ? FADE : LEAVE_FADE));
  const gone = still ? leave : EASE_IN_OUT(leave);

  // The upload.
  let fill = 0;
  let fillAlpha = 0;
  let redAlpha = 0;
  let ring = 0;
  let ringScale = 1;
  let ringAlpha = 0;
  let discScale = 1;
  let discAlpha = 0;
  let check = 0;
  if (now >= row.startAt && now < Math.min(row.failAt, row.doneAt)) {
    fill = UPLOAD_EASE(clamp01((now - row.startAt) / UPLOAD));
    fillAlpha = 1;
    ring = fill;
    ringAlpha = 1;
  } else if (row.failAt <= now) {
    const t = now - row.failAt;
    fill = row.failPoint * (1 - EASE_IN_OUT(clamp01(t / DRAIN)));
    redAlpha = clamp01(t / FAIL_TINT);
    fillAlpha = 1 - redAlpha;
    ring = row.failPoint;
    ringScale = still ? 1 : 1 - clamp01(t / RING_SHRINK);
    ringAlpha = still ? 1 - clamp01(t / FADE) : 1;
  } else if (row.doneAt <= now) {
    const t = now - row.doneAt;
    fill = 1;
    fillAlpha = 1 - clamp01(t / FILL_OUT);
    ring = 1;
    ringAlpha = 1 - clamp01(t / RING_OUT);
    discScale = still ? 1 : DONE_SCALE + (1 - DONE_SCALE) * spring(t, DONE_POP);
    discAlpha = clamp01(t / (still ? FADE : DISC_FADE));
    check = still ? 1 : EASE_OUT(clamp01((t - CHECK_DELAY) / CHECK_DRAW));
  }

  // Failing: the row shakes, the pill opens and Retry comes in; a Retry closes them again.
  let shakeX = 0;
  let pill = 0;
  let pillAlpha = 0;
  let bang = 0;
  let retryAlpha = 0;
  let retryX = 0;
  if (row.failAt <= now) {
    const t = now - row.failAt;
    if (!still && t < ROW_SHAKE_FOR) {
      shakeX = ROW_SHAKE_AMP * Math.exp(-t * ROW_SHAKE_DECAY) * Math.sin(2 * Math.PI * t * ROW_SHAKE_HZ);
    }
    pill = still ? 1 : EASE_OUT(clamp01(t / PILL_OPEN));
    pillAlpha = still ? clamp01(t / FADE) : 1;
    bang = still ? 1 : spring(t, BANG_POP);
    const r = EASE_OUT(clamp01((t - RETRY_IN_DELAY) / RETRY_IN));
    retryAlpha = still ? clamp01(t / FADE) : r;
    retryX = still ? 0 : RETRY_IN_X * (1 - r);
  } else if (row.lastFailAt > NEVER && row.retryUp >= row.lastFailAt) {
    const open = row.retryUp - row.lastFailAt;
    const t = now - row.retryUp;
    const shut = clamp01(t / PILL_CLOSE);
    pill = (still ? 1 : EASE_OUT(clamp01(open / PILL_OPEN))) * (1 - (still ? 0 : EASE_OUT(shut)));
    pillAlpha = 1 - shut;
    bang = 1;
    const shown = still ? clamp01(open / FADE) : EASE_OUT(clamp01((open - RETRY_IN_DELAY) / RETRY_IN));
    retryAlpha = shown * (1 - clamp01(t / RETRY_OUT));
  }
  const spin = still || row.retryUp <= NEVER ? 0 : 360 * EASE_OUT(clamp01((now - row.retryUp) / SPIN));

  return {
    top,
    boxHeight: rowHeight * grown,
    boxOpacity: 1 - gone,
    boxY: still ? 0 : LEAVE_Y * gone,
    boxScale: still ? 1 : 1 - (1 - LEAVE_SCALE) * gone,
    shakeX,
    contentOpacity: arrive,
    contentY: still ? 0 : ROW_IN_Y * (1 - arrive),
    contentScale: still ? 1 : ROW_IN_SCALE + (1 - ROW_IN_SCALE) * arrive,
    fill,
    fillAlpha,
    redAlpha,
    ring,
    ringScale,
    ringAlpha,
    discScale,
    discAlpha,
    check,
    pill,
    pillAlpha,
    bang,
    retryAlpha,
    retryX,
    retryScale: pressScale(row.retryDown, row.retryUp, now, RETRY_PRESS),
    spin,
  };
}

// ——— The zone and the buttons ———

/** How much of a flare is left: 1 when a file was just added, 0 after FLARE_FOR. */
export function flare(e: Events, now: number, still: boolean) {
  'worklet';
  const t = now - e.addAt;
  if (still || t < 0 || t >= FLARE_FOR) return 0;
  return 1 - EASE_OUT(t / FLARE_FOR);
}

/** The glow's strength as it breathes (a flare is drawn as a second glow over it, FLARE_GLOW strong). */
export function glowLevel(t: number, still: boolean) {
  'worklet';
  return still ? GLOW_STILL : GLOW_LOW + (GLOW_HIGH - GLOW_LOW) * wave(t, BREATHE);
}

/** The badge's turn in degrees: a shake when a file is added. */
export function badgeTurn(e: Events, now: number, still: boolean) {
  'worklet';
  if (still || e.addAt <= NEVER) return 0;
  return SHAKE_ANGLE * (1 - spring(now - e.addAt, SHAKE));
}

/** The ring that leaves the badge when a file is added: its scale (of the badge) and opacity. */
export function pulse(e: Events, now: number, still: boolean) {
  'worklet';
  const t = (now - e.addAt) / PULSE_FOR;
  if (still || t < 0 || t >= 1) return { scale: 1, alpha: 0 };
  const k = EASE_OUT(t);
  return { scale: 1 + (PULSE_GROW - 1) * k, alpha: PULSE_ALPHA * (1 - k) };
}

/** How far Select files is disabled (0 enabled, 1 disabled): while the list is full. */
export function selectOff(e: Events, now: number) {
  'worklet';
  let active = 0;
  let last = NEVER;
  let batch = 0;
  for (const row of e.rows) {
    if (row.leaveAt > now) {
      active++;
      last = Math.max(last, row.addedAt);
    } else if (row.leaveAt === e.clearAt) batch++;
  }
  if (active >= MAX_ROWS) return clamp01((now - last) / ENABLE_SWAP);
  if (batch >= MAX_ROWS && e.clearAt <= now) return 1 - clamp01((now - e.clearAt) / ENABLE_SWAP);
  return 0;
}

/** How far Cancel is enabled: while the list holds a row. */
export function cancelOn(e: Events, now: number) {
  'worklet';
  let first = FOREVER;
  for (const row of e.rows) if (row.leaveAt > now) first = Math.min(first, row.addedAt);
  if (first < FOREVER) return clamp01((now - first) / ENABLE_SWAP);
  return e.clearAt <= now ? 1 - clamp01((now - e.clearAt) / ENABLE_SWAP) : 0;
}

/** How far Continue is enabled. */
export function continueOn(e: Events, now: number) {
  'worklet';
  if (e.enabledAt > e.disabledAt) return clamp01((now - e.enabledAt) / ENABLE_FADE);
  return 1 - clamp01((now - e.disabledAt) / ENABLE_FADE);
}

/** Where the sheen across Continue is, from 0 (left, outside) to 1 (right, outside), or -1 when there is none. */
export function sheen(e: Events, now: number, still: boolean) {
  'worklet';
  if (still || e.enabledAt <= e.disabledAt) return -1;
  const t = (now - e.enabledAt - SHEEN_DELAY) / SHEEN_FOR;
  if (t <= 0 || t >= 1) return -1;
  return EASE_IN_OUT(t);
}

/** How much Continue reads "Uploaded" instead of its name. */
export function uploaded(e: Events, now: number) {
  'worklet';
  const t = now - e.uploadedAt;
  return clamp01(t / LABEL_FADE) * (1 - clamp01((t - UPLOADED_FOR) / LABEL_FADE));
}
