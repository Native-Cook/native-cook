// Page Curl Journal: every colour, size and position. Positions are points on the 402 × 874 reference screen,
// measured from the source recording (rectified to 1206 × 2622) and scaled with the screen width.

export const REF = { width: 402, height: 874 };

// The edges of the pages still to turn, seen at the right edge of the book.
export const STACK_INK = '#D9D9DD';

export const INK = {
  paper: '#FFFFFF',
  text: '#000000',
  eyebrow: '#6E6E73',
  mute: '#636367',
  footnote: '#727276',
  author: '#8E8E92',
  caption: '#7A7A7E',
  iconLine: '#E3E3E6',
};

// One entry per text line or block: top of the line box, font size, weight, line height.
type Line = { top: number; size: number; weight: '400' | '500' | '600' | '700' | '800' | '900'; line: number };

export const TYPE = {
  eyebrow: { top: 104.3, size: 13.6, weight: '500', line: 18 } as Line,
  brand: { top: 124.6, size: 30.0, weight: '800', line: 37 } as Line,
  title: { top: 129.1, size: 25.7, weight: '700', line: 31 } as Line,
  intro: { top: 586.2, size: 14.1, weight: '400', line: 20 } as Line,
  hint: { top: 816.2, size: 14.2, weight: '500', line: 18 } as Line,
  folio: { top: 816.2, size: 14, weight: '400', line: 18 } as Line,
  rowTitle: { top: 0, size: 14.7, weight: '600', line: 18 } as Line,
  rowBody: { top: 0, size: 13.6, weight: '400', line: 17 } as Line,
  footnote: { top: 712.7, size: 11.2, weight: '400', line: 13.7 } as Line,
  body: { top: 273, size: 14.1, weight: '400', line: 19.7 } as Line,
  cover: { top: 214.2, size: 32.3, weight: '700', line: 37.3 } as Line,
  caption: { top: 416.4, size: 9.9, weight: '600', line: 12 } as Line,
  author: { top: 444.9, size: 26.1, weight: '700', line: 31 } as Line,
};

/**
 * Spacing scale (step 0, 20 Oct), derived from the source's measured gaps: 4 pt base; 8 = the hint arrow's gap,
 * 48 ≈ the body's left margin (47.8). Source elements keep their measured positions; the upgrade's additions
 * (the spine's binding shadow) sit on this scale. The page-stack hairlines are sheet thickness, not spacing.
 */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32, body: 48 } as const;

export const LAYOUT = {
  mark: { left: 145, top: 314.7, size: 112.6, radius: 25.5 },
  bodyLeft: 47.8,
  rows: { iconX: 62, textLeft: 101.5, titleTop: [313.1, 402.8], icon: 30.5, iconY: [341.7, 431], gap: 3.4 },
  shield: { top: 689, size: 15.5 },
  hintArrow: { size: 13.5, gap: 8 },
  sparkle: { cx: 201.5, cy: 154, size: 47 },
};

// The invented brand. The source's wordmark dots its i's with sparkles; ours does the same.
export const BRAND = { name: 'Ritualbook', mark: 'Ri' };

export const COPY = {
  meet: 'Meet',
  intro: 'Your personal ‘Great Book of Habits’, written\nand completed by you, one day at a time.',
  hint: 'Turn the page',
  preface: 'Preface',
  prefaceTitle: 'Your Data Is Yours',
  rows: [
    { title: 'Track Habits', body: 'Feel proud of every small step you take\ntoward your goals.' },
    { title: 'Review Records', body: 'Learn from your setbacks and your wins\nas the weeks go by.' },
  ],
  footnote: 'Everything you write stays private on this iPhone.\nNo servers, no tracking, no account, no ads.',
  foreword: 'Foreword',
  forewordTitle: 'How It Works',
  body:
    'Every day, your book automatically adds\none fresh page. It’s up to you to make the\nmost of it.\n\n' +
    'Missed a day? Don’t worry about it. You can\nalways turn back a page and fill in\nany blanks as you go.\n\n' +
    'Every time you check in on your daily habits,\nyou move a step closer to finishing all the\nchapters of your own unfolding story.\n\n' +
    'Now, before your book finds its rightful\nplace on your home screen, all that’s left is\nto make the commitment to the journey\nahead...',
  cover: 'The Great Book\nof Habits',
  caption: 'TO BE WRITTEN BY',
  author: 'Untitled Author',
  pages: ['Title page', 'Preface', 'Foreword', 'Cover'],
};

// Icon colours, read from the source frames.
export const ICON = {
  check: ['#6FB6F5', '#8C8DF0', '#B08BEF'],
  bars: ['#79A6F2', '#9B8CEE'],
  barsBase: '#B9A5F2',
  sparkle: ['#FFC83D', '#3FA0FF', '#8A63F0', '#FF7A8A', '#FFC83D'],
  markInk: '#141414',
  curlDark: '#8F8F93',
  curlMid: '#C9C9CC',
  curlLight: '#F2F2F4',
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
