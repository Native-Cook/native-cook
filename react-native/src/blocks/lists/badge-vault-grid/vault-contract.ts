/**
 * What the drawing code and the screen agree on. The screen describes each badge as a pose;
 * the shaders turn a pose into light. Nothing here imports React or Skia.
 */

/** sRGB, each channel 0 to 1. */
export type Rgb = readonly [number, number, number];

export type BadgeStat = { value: string; label: string };

export type BadgeDef = {
  id: string;
  name: string;
  /** Which drawing of the atlas this badge wears (0 to 17, row-major). */
  tile: number;
  /** The three enamels: the field, the second field, the accent. */
  a: Rgb;
  b: Rgb;
  c: Rgb;
  /** The colour the page takes while this badge is held. */
  flood: Rgb;
  /** The date it was earned, or null while it is locked. */
  earned: string | null;
  /** Progress of a locked badge. */
  goal: { have: number; need: number; unit: string } | null;
  /** One sentence: what it is for. At most 44 characters, so it sets on two short lines. */
  blurb: string;
  /** The share of members who hold it, in percent. */
  rarity: number;
  stats: readonly [BadgeStat, BadgeStat, BadgeStat];
};

function rgb(hex: string): Rgb {
  const n = parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

function badge(
  id: string, name: string, tile: number, a: string, b: string, c: string, earned: string | null,
  goal: BadgeDef['goal'], blurb: string, rarity: number, stats: [string, string, string, string, string, string],
): BadgeDef {
  return {
    id, name, tile, a: rgb(a), b: rgb(b), c: rgb(c), flood: rgb(a), earned, goal, blurb, rarity,
    stats: [
      { value: stats[0], label: stats[1] },
      { value: stats[2], label: stats[3] },
      { value: stats[4], label: stats[5] },
    ],
  };
}

/** Three columns, six rows, in reading order. Every name and mark was invented for this block. */
export const BADGES: readonly BadgeDef[] = [
  badge('first-light', 'First Light', 0, '#FF7A2F', '#C7352B', '#FFE08A', '9 Jan 2026', null,
    'Twenty sessions started before sunrise.', 12, ['06:02', 'Earliest', '20', 'Sessions', '4 wk', 'Span']),
  badge('night-shift', 'Night Shift', 1, '#3D45E0', '#1A1D6E', '#F6F1DC', '23 Jan 2026', null,
    'Ten sessions finished after midnight.', 9, ['00:47', 'Latest', '10', 'Sessions', '3 wk', 'Span']),
  badge('summit', 'Summit', 2, '#1CC4E8', '#0D6FA3', '#FFFFFF', '2 Feb 2026', null,
    'Climbed 8,848 metres in a single month.', 6, ['8,848 m', 'Climbed', '17', 'Days', '612 m', 'Best day']),
  badge('spark', 'Spark', 3, '#FFD21F', '#FF9A1F', '#FFFFFF', '14 Feb 2026', null,
    'Fifty quick sessions under five minutes.', 21, ['50', 'Sessions', '3:40', 'Average', '9', 'Best day']),
  badge('deep-dive', 'Deep Dive', 4, '#14D8B2', '#0A8A8E', '#D9FFF5', '1 Mar 2026', null,
    'Four hours of focus without one break.', 4, ['4:12 h', 'Longest', '31', 'Sessions', '96%', 'Finished']),
  badge('iron-month', 'Iron Month', 5, '#9BE21A', '#3F9E12', '#F2FFD0', null, { have: 12, need: 30, unit: 'days' },
    'Show up thirty days in a row.', 3, ['12', 'Days so far', '18', 'To go', '30', 'Target']),
  badge('trailhead', 'Trailhead', 6, '#FF4D6D', '#B3123F', '#FFE7EC', '19 Mar 2026', null,
    'Tried every kind of session at least once.', 17, ['8', 'Kinds', '11', 'Days', '2', 'Favourites']),
  badge('seven-up', 'Seven Up', 7, '#A45BFF', '#5E2BC4', '#F0E5FF', '30 Mar 2026', null,
    'Seven sessions in a single day.', 8, ['7', 'Sessions', '3:05 h', 'Total', '26 min', 'Average']),
  badge('bullseye', 'Bullseye', 8, '#FF3B30', '#B01212', '#FFFFFF', '11 Apr 2026', null,
    'Hit the weekly goal exactly, to the minute.', 2, ['300', 'Minutes', '0', 'Over', '5', 'Sessions']),
  badge('evergreen', 'Evergreen', 9, '#2ED158', '#0F7A3B', '#D9FFE3', '2 May 2026', null,
    'A hundred days without missing a week.', 11, ['100', 'Days', '15', 'Weeks', '71', 'Sessions']),
  badge('long-haul', 'Long Haul', 10, '#FF9F0A', '#B85C00', '#FFF1D6', '28 May 2026', null,
    'A thousand kilometres, one step at a time.', 5, ['1,000 km', 'Distance', '148', 'Days', '6.8 km', 'Average']),
  badge('gemcutter', 'Gemcutter', 11, '#5AC8FA', '#1E78D6', '#FFFFFF', '16 Jun 2026', null,
    'Beat a personal best five times running.', 7, ['5', 'Records', '12 d', 'Span', '+9%', 'Gain']),
  badge('hot-streak', 'Hot Streak', 12, '#FF5E3A', '#D11A2A', '#FFC83D', '4 Jul 2026', null,
    'Fourteen days, and the streak is alive.', 14, ['14', 'Days', '19', 'Sessions', '41 min', 'Average']),
  badge('orbit', 'Orbit', 13, '#7D5CFF', '#2C1E8F', '#FFD86B', '21 Aug 2026', null,
    'Three hundred and sixty-five sessions.', 1, ['365', 'Sessions', '212', 'Days', '1.7', 'Per day']),
  badge('full-week', 'Full Week', 14, '#FF2D92', '#A3125E', '#FFE0F0', null, { have: 6, need: 7, unit: 'days' },
    'Seven days out of seven.', 19, ['7', 'Days', '9', 'Sessions', '38 min', 'Average']),
  badge('steady', 'Steady', 15, '#1FB6FF', '#0A4FA8', '#FFFFFF', '9 Sep 2026', null,
    'Same hour, every day, for three weeks.', 6, ['07:30', 'Hour', '21', 'Days', '±4 min', 'Drift']),
  badge('crowned', 'Crowned', 16, '#FFC531', '#D98A00', '#FFF6D0', null, { have: 2, need: 5, unit: 'wins' },
    'Finish first in five weekly leagues.', 1, ['2', 'Wins so far', '3', 'To go', '5', 'Target']),
  badge('keystone', 'Keystone', 17, '#34E0C2', '#128F7C', '#EFFFFA', '1 Oct 2026', null,
    'Completed every badge of one set.', 3, ['6', 'Badges', '1', 'Set', '9 mo', 'Span']),
];

/** The date a badge gets when it is unlocked in front of you. */
export const UNLOCK_DATE = '13 Oct 2026';

/** How one badge sits on its canvas. Angles in radians, lengths in points. */
export type BadgePose = {
  /** Centre of the badge on the canvas. */
  cx: number;
  cy: number;
  /** Half the badge's width (flat side to flat side). */
  r: number;
  /** Positive tips the top edge away from the viewer. */
  tiltX: number;
  /** Positive turns the right edge away from the viewer. */
  tiltY: number;
  /** A turn about the vertical axis; at pi the back faces the viewer. */
  spin: number;
  /** In-plane rotation, clockwise (the rattle of a locked badge). */
  roll: number;
  /** -1 to 1: where the badge is on the screen (left to right, top to bottom). Slides the studio's reflections. */
  envX: number;
  envY: number;
  /** A band of light crossing the face: 0 touches the top-left, 1 has left at the bottom-right. Outside 0..1 there is none. */
  sheen: number;
  /** 0 locked (graphite, no colour) to 1 (the enamel has risen to the top). */
  fill: number;
  /** 0 to 1: how lively the surface of the rising enamel is. */
  wave: number;
  /** Seconds; moves the rising enamel's surface. */
  time: number;
  /** 0 seated in its socket to 1 floating in front of the page (shadow and bounce light spread). */
  lift: number;
  /** 0 the badge is away and only its socket shows, 1 it is here. */
  present: number;
  /** 0 no socket is drawn (the flying badge), 1 the socket is drawn under the badge. */
  socket: number;
};

/** The page while a badge is held: its colour pours out of the socket and settles behind the badge. */
export type FloodPose = {
  /** Canvas size. */
  w: number;
  h: number;
  /** Where the colour comes from: the centre of the badge's socket. */
  ox: number;
  oy: number;
  /** Where the badge is now, and half its width. */
  cx: number;
  cy: number;
  r: number;
  /** 0 nothing, 1 the front has passed every corner of the screen. */
  spread: number;
  /** 0 to 1: how much of the page's black the colour replaces once the front has passed. */
  power: number;
  /** 0 to 1: the rays and motes behind the badge. */
  rays: number;
  /** Seconds since the badge landed (motes rise from then); negative before. */
  since: number;
  /** Seconds, free running while the badge is held. */
  time: number;
};

/** A control made like the badges: a polished silver rim around a pool of glossy enamel. A disc when w equals h. */
export type PillPose = {
  w: number;
  h: number;
  /** 0 to 1 while a finger is on it. */
  press: number;
  /** Same band of light as on a badge; outside 0..1 none. */
  sheen: number;
  /** 0 fired enamel, 1 unfired graphite in a gunmetal rim (a disabled control). */
  dark: number;
  /** The enamel poured inside the rim. */
  tint: Rgb;
};
