// Page Curl Journal: every colour, size and position. Positions are points on the 402 × 874 reference screen,
// measured from the source recording (rectified to 1206 × 2622) and scaled with the screen width.

export const REF = { width: 402, height: 874 };

// The edges of the pages still to turn, seen at the right edge of the book: the paper's own edge, a shade darker.
export const STACK_INK = '#D8CDB8';

/** The paper and its print: ivory stock, a warm near-black ink, one muted oxblood for the ornaments. */
export const PAPER = {
  base: '#F6F0E3',
  ink: '#2B241C',
  mute: '#7C6E5B',
  rule: '#CDBFA8',
  accent: '#8A3B2E',
  foil: '#6B4E1F',
};

export const SERIF = { roman: 'PCJ-Newsreader', italic: 'PCJ-NewsreaderItalic' };

/** The page's furniture on the reference: margins, running head and folio lines, the binding's shade. */
export const BOOK = { margin: 48, head: 92, folio: 806, gutter: 30 };

// Only the hint line's position is read by the curl (a tap on it turns the page).
export const TYPE = { hint: { top: 806 } };

/** Spacing scale: 4 pt base. */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32, body: 48 } as const;

// The invented brand and every line the book prints. Glyphs: Newsreader's subset holds ASCII, curly quotes, dashes,
// the ellipsis and the middle dot, nothing else.
export const COPY = {
  presents: 'Ritualbook presents',
  title: 'The Great Book\nof Habits',
  subtitle: 'a journal written one day at a time',
  sealTop: 'VOL. I',
  sealMid: 'MMXXVI',
  sealBottom: 'RITUALBOOK',
  imprint: 'RITUALBOOK  \u00B7  FIRST EDITION',
  hint: 'turn the page',
  headPreface: 'THE GREAT BOOK OF HABITS',
  headForeword: 'HOW IT WORKS',
  preface: 'PREFACE',
  prefaceTitle: 'Your Data Is Yours',
  rows: [
    { title: 'Track habits', body: 'Mark the small things you do each day and watch them add up. A tick is enough; a line of notes is better. Nothing is graded, and nothing is lost when you miss.' },
    { title: 'Review records', body: 'Every Sunday the book turns back to the week behind you: what held, what slipped, and the one habit worth carrying into the next.' },
  ],
  footnote: '* Everything you write stays on this iPhone. No servers, no tracking, no account, no ads.',
  foreword: 'FOREWORD',
  forewordTitle: 'How It Works',
  body: [
    ' your book adds one fresh page. It is yours to fill, a line or a whole page, and it waits patiently for you if you are late.',
    'Missed a day? Turn back a page and fill in the blank; nobody is keeping score but you.',
    'Each check-in moves you a step closer to the last chapter of a story that only you can write.',
  ],
  bodyLead: 'EVERY MORNING',
  signoff: 'with care, the makers of Ritualbook',
  belongs: 'THIS BOOK BELONGS TO',
  author: 'Untitled Author',
  begun: 'begun on the 23rd of October',
  pages: ['Title page', 'Preface', 'Foreword', 'Ownership page'],
};

// Page-curl shading. Fitted against the source's mid-turn frame with the numpy twin of the shader
// (.work/pcj/curl.py) on our own pages: edge mean 3.69 px, p90 9.55, colour ΔE 2.32.
export const CURL = {
  radius: 22.77, // roll radius where the fold meets the grabbed corner, pt
  radiusSlope: 0.01, // the roll widens along the fold (a cone, as on iOS)
  radiusMin: 1,
  shadow: 0.413, // cast shadow on the page beneath, darkest at the roll
  shadowWidth: 1.6,
  edgeShadow: 0.334, // contact shadow under the lying flap's edge
  edgeShadowWidth: 3.62,
  frontLight: 0.793, // ambient on the rising front of the roll
  backLight: 0.414, // ambient on the back of the roll, the fold's dark line
  backGamma: 0.455,
  backPaper: 0.962, // the back side of the sheet
  showThrough: 0.218, // how much of the printed front reads through the back
  grain: 0.018, // paper grain on the back, a seeded hash per device pixel
  edgeInk: 0.08, // the sheet's cut edge reads as thickness
  sheen: 0.06, // soft specular band on the roll where it faces the key light
};
