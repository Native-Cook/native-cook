/**
 * Race Result Standings — tokens. Every position is in reference points of the pin's screen
 * (402 pt wide, the status bar's safe area ending at REF_TOP); the component maps x → x·S and
 * y → safeTop + (y − REF_TOP)·S with S = width / 402. Values were measured on the pin at 3× and
 * kept to a third of a point. Text is placed by its baseline: top of the text box = baseline − ASCENT·size.
 */

export const REF_W = 402;
export const REF_TOP = 62;
/** Montserrat's hhea ascender (968/1000): where the baseline sits below the top of an RN text box. */
export const ASCENT = 0.968;

export const FONT = {
  medium: 'RaceMontserrat-Medium',
  semibold: 'RaceMontserrat-SemiBold',
  bold: 'RaceMontserrat-Bold',
  extrabold: 'RaceMontserrat-ExtraBold',
} as const;

export const colors = {
  ink: '#FFFFFF',
  muted: '#9B9B9B',
  hairline: '#413D41',
  base: '#020002',
  backFill: '#484848',
  backRing: '#686868',
  stopwatch: '#5A42DB',
  award: '#E4232D',
  wheel: '#F8C91C',
  up: '#83D165',
  down: '#E4232D',
  comet: '#FFF6D8',
  cometGlow: '#F8C91C',
  shade: '#000000',
  plate: '#141216', // a lifted overtaker's plate
  plateEdge: '#2E2A30',
  lightOff: '#2A2629', // an unlit start light
} as const;

/**
 * The backdrop: a grey band that peaks 100 pt down and dies into near-black by 333 pt, with the
 * left and right edges lifted by `edge` (strength × u², u = distance from the centre line / half width).
 * Stops are [y in reference pt, grey 0–255].
 */
export const backdrop = {
  centre: [
    [0, 17], [16.7, 23], [33.3, 29], [50, 35], [66.7, 40], [83.3, 45], [100, 47], [116.7, 46],
    [133.3, 43], [150, 40], [166.7, 33], [183.3, 28], [200, 22], [216.7, 18], [233.3, 13],
    [250, 9], [266.7, 6], [283.3, 4], [300, 3], [316.7, 1], [333.3, 0],
  ] as [number, number][],
  edge: [
    [0, 23], [33.3, 22], [50, 18], [66.7, 17], [83.3, 12], [100, 10], [150, 9], [166.7, 11],
    [200, 11], [233.3, 9], [250, 8], [266.7, 5], [300, 4], [316.7, 2], [333.3, 1], [350, 0],
  ] as [number, number][],
  /** u² sampled across the width (0 … 1), used as the horizontal alpha of the edge lift. */
  edgeProfile: [1, 0.64, 0.36, 0.16, 0.04, 0, 0.04, 0.16, 0.36, 0.64, 1],
} as const;

/** Spacing measured on the pin (pt). Every layout number below is built from these. */
export const space = {
  screenInset: 21.67, // left edge of the header copy ("Round 09", event name)
  rowInset: 18.33, // standings hairlines, both sides
  headerRuleInset: 20.33, // the hairline under the stats, both sides
  pointsInset: 19.33, // right edge of the points column (402 − 382.67)
  textColumn: 53.67, // stats copy starts here (icon column 22–43 + 10.67 gap)
  nameColumn: 66.33, // driver name / team in a standings row
  statColumn: 227.67, // the second stats column
  markToCode: 5.67, // livery mark → three-letter code
  rowPitch: 83.15, // one standings row, hairline to hairline
  statPitch: 48.67, // stats line → stats line (fastest lap → driver of the day)
  labelGap: 17.33, // stat value baseline → its label baseline
  teamGap: 18.92, // driver name baseline → team baseline
  badgeToDigit: 5, // change badge → its digit
  hit: 44, // minimum touch target
} as const;

export const layout = {
  back: { cx: 29.17, cy: 82.5, d: 42.33, ring: 0.67 },
  title: { x: 123.92, baseline: 93.97, size: 20.17 },
  sort: { x: 272, y: 80, w: 8.33, h: 14.33, stroke: 1.55 },
  round: { x: 20.34, baseline: 147.18, size: 14.17 },
  event: { x: 20.07, baseline: 177.03, size: 22.83 },
  podium: {
    markRight: [50.33, 121.33, 194.67],
    codeX: [54.89, 126.22, 199.22],
    markY: 203,
    markW: 30,
    markH: 8.33,
    baseline: 211.07,
    size: 11.83,
  },
  /** The Blender render's 270 × 290 px canvas (3×); the circuit's own bounds are x 297–383, y 151.3–241. */
  track: { x: 295, y: 145.67, w: 90, h: 96.67 },
  stats: {
    valueSize: 12.17,
    labelSize: 10.17,
    lap: { x: 53.39, labelX: 53.26, baseline: 257.07 },
    day: { x: 53.13, labelX: 53.26, baseline: 305.36 },
    pit: { x: 226.53, labelX: 226.26, baseline: 305.36 },
  },
  headerRule: { y: 356.5 },
  rows: {
    firstRule: 447.83,
    numberX: 18.56,
    numberBaseline: 405.12,
    numberSize: 19.83,
    nameX: 65.62,
    nameBaseline: 403.48,
    nameSize: 18.17,
    teamX: 65.19,
    teamBaseline: 422.4,
    teamSize: 12.17,
    pointsRight: 383.09,
    pointsBaseline: 403.64,
    pointsSize: 18.17,
    ptsRight: 383.34,
    ptsBaseline: 420.31,
    ptsSize: 9.83,
    badge: { x: 21, y: 418, w: 6.67, h: 7.33 },
    digitX: 32.59,
    digitBaseline: 424.88,
    digitSize: 9.83,
    /** Where a moving driver's light trail sits, between the position column and the name. */
    plate: { x: 52, w: 262, inset: 6, radius: 16 },
    trailX: 58,
    trailW: 2.33,
    trailMax: 44,
  },
} as const;

/** Icons, in reference pt (absolute). */
export const icons = {
  stopwatch: { cx: 32.67, cy: 258.67, r: 9.55, stroke: 1.5 },
  award: { cx: 32.67, cy: 302.5, r: 6.25, stroke: 1.5 },
  wheel: { cx: 205.83, cy: 305.67, r: 10.3, stroke: 1.7 },
} as const;

export type TeamId = 'scarlet' | 'tidewave' | 'kestrel' | 'bluecrest' | 'crest';
export type MarkKind = 'fin' | 'twin' | 'stair' | 'chevron';

/** Invented teams of the Turn Nine championship; text and mark colours match the pin's teams. */
export const teams: Record<TeamId, { name: string; text: string; mark: string }> = {
  scarlet: { name: 'Scarlet', text: '#D72A36', mark: '#E92432' },
  tidewave: { name: 'Tidewave', text: '#16BFB7', mark: '#00C9C0' },
  kestrel: { name: 'Kestrel', text: '#EC601B', mark: '#EC601B' },
  bluecrest: { name: 'Bluecrest', text: '#2966CC', mark: '#2966CC' },
  crest: { name: 'Crest Junior', text: '#2B66CB', mark: '#2B66CB' },
};

export type Driver = { name: string; code: string; team: TeamId; mark: MarkKind };

/** Mira Chen, Alex Vale, Sam Ives and Noah Reed are the Turn Nine racers of Pit Stop Review and Paddock Widget Bento. */
export const drivers: Driver[] = [
  { name: 'Mira Chen', code: 'CHE', team: 'scarlet', mark: 'fin' },
  { name: 'Alex Vale', code: 'VAL', team: 'tidewave', mark: 'twin' },
  { name: 'Sam Ives', code: 'IVE', team: 'scarlet', mark: 'stair' },
  { name: 'Noah Reed', code: 'REE', team: 'kestrel', mark: 'chevron' },
  { name: 'Theo Marsh', code: 'MAR', team: 'bluecrest', mark: 'twin' },
  { name: 'Kai Lindgren', code: 'LIN', team: 'crest', mark: 'twin' },
];

/**
 * A car's colour on the circuit (dot, trail, overtake tag), per driver index. Mira Chen and Sam Ives drive
 * for the same team, and Theo Marsh and Kai Lindgren's teams are near-twin blues, so the second car of each
 * pair gets a lighter shade of the team colour: six dots, six colours. The list keeps the team colours.
 * `ink` is the code on the tag (dark on the light shades for contrast).
 */
export const carShade: { fill: string; ink: string }[] = [
  { fill: '#D72A36', ink: '#FFFFFF' }, // Mira Chen, Scarlet
  { fill: '#16BFB7', ink: '#FFFFFF' }, // Alex Vale, Tidewave
  { fill: '#FF8C93', ink: '#2A0407' }, // Sam Ives, Scarlet (light)
  { fill: '#EC601B', ink: '#FFFFFF' }, // Noah Reed, Kestrel
  { fill: '#2966CC', ink: '#FFFFFF' }, // Theo Marsh, Bluecrest
  { fill: '#8DB4F5', ink: '#06142E' }, // Kai Lindgren, Crest Junior (light)
];

export type Session = {
  word: string;
  /** Driver index per finishing slot. */
  order: number[];
  points: number[];
  /** Places gained from the grid, per driver index. */
  delta: number[];
  fastestLap: string;
};

/** Index 0 is the rest frame (the pin); index 1 is the other session behind the ⇅ switch. */
export const sessions: [Session, Session] = [
  {
    word: 'Race',
    order: [0, 1, 2, 3, 4, 5],
    points: [25, 18, 15, 12, 10, 8],
    delta: [1, 2, 0, 2, 0, 4],
    fastestLap: 'Jonah Price (1:31.482)',
  },
  {
    word: 'Sprint',
    order: [1, 3, 0, 5, 2, 4],
    points: [8, 7, 6, 5, 4, 3],
    delta: [-1, 3, -2, 1, 0, 5],
    fastestLap: 'Alex Vale (1:32.906)',
  },
];

export const copy = {
  results: ' Results',
  round: 'Round 09',
  event: 'Harbor GP',
  lapLabel: 'Fastest Lap',
  dayValue: 'Mira Chen',
  dayLabel: 'Driver of the Day',
  pitValue: 'Tidewave (2.140s)',
  pitLabel: 'Fastest Pitstop',
  pts: 'PTS',
} as const;

/** slot(driver) for each session. */
export function slotsOf(session: Session) {
  const slots: number[] = [];
  session.order.forEach((driver, slot) => {
    slots[driver] = slot;
  });
  return slots;
}

export const SLOT_OF: [number[], number[]] = [slotsOf(sessions[0]), slotsOf(sessions[1])];

/** Livery marks: polygons in a 30 × 8.33 pt box, slanted like the pin's (top edge 3.2 pt right of the bottom). */
const SLANT = 3.2;
function bar(left: number, w: number, top: number, bottom: number) {
  const k = SLANT / 8.33;
  return `M${(left + (8.33 - bottom) * k).toFixed(2)} ${bottom}L${(left + w + (8.33 - bottom) * k).toFixed(2)} ${bottom}L${(left + w + (8.33 - top) * k).toFixed(2)} ${top}L${(left + (8.33 - top) * k).toFixed(2)} ${top}Z`;
}
/** A tapered speed streak that ends where the stripes begin (drawn with a fading gradient). */
function streak(end: number) {
  return `M0 1.1L${end} 0L${end - 1.6} 4.3Z`;
}
export const marks: Record<MarkKind, { streak?: string; bars: string }> = {
  fin: { streak: streak(20), bars: [bar(19, 8, 0, 3.6), bar(20.6, 8, 4.6, 8.33)].join('') },
  twin: { streak: streak(15.5), bars: [bar(14.5, 6.2, 0, 8.33), bar(22.5, 3.6, 0, 8.33)].join('') },
  stair: { bars: [bar(5.5, 5.2, 3.2, 8.33), bar(12.6, 5.2, 1.6, 8.33), bar(19.7, 5.2, 0, 8.33)].join('') },
  chevron: { streak: streak(17), bars: 'M16.5 8.33L21 8.33L26.8 4.17L24.2 0L19.7 0L22.3 4.17Z' },
};
