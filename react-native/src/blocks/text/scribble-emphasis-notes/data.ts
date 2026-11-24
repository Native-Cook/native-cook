// The page's copy. "Folio" and the course are invented.
export const COPY = {
  brand: 'Folio',
  course: 'Biology · Unit 4',
  title: 'Cellular respiration',
  meta: 'Lecture 7 · Thu 12 Nov · 4 min read',
  label: 'Key idea',
  page: 'p. 142',
  tray: 'Highlighter',
  hint: 'Tap a sentence, or drag across words',
  marked: 'Marked on this page',
};

/** Plain ASCII, so character offsets are the same in every stack. */
export const TEXT =
  'Cellular respiration turns glucose into energy the cell can spend. ' +
  'Most of it is released in the mitochondria, where the electron transport chain pumps protons across the inner membrane. ' +
  'That gradient drives ATP synthase, making about 30 ATP per glucose. ' +
  'Without oxygen the chain backs up and the cell falls back on fermentation.';

/** What the loop marks. Each phrase is found in TEXT by its first occurrence. */
export const TERMS = {
  circle: 'mitochondria',
  highlight: 'electron transport chain',
  underline: 'about 30 ATP per glucose',
  /** The sentence the margin bracket holds (0-based). */
  bracketSentence: 3,
};

/** The summary rows under the tray; `mark` is the loop's mark that lights the row. */
export const MARKED = [
  { key: 'circle', term: 'mitochondria', how: 'Circled', mark: 0 },
  { key: 'highlight', term: 'electron transport chain', how: 'Highlighted', mark: 1 },
  { key: 'underline', term: '30 ATP per glucose', how: 'Underlined', mark: 2 },
] as const;

/** Highlighter inks (sRGB). Multiplied over the page. */
export const TINTS = [
  { key: 'lemon', name: 'Lemon', color: '#FFE14A' },
  { key: 'rose', name: 'Rose', color: '#FF9FB6' },
  { key: 'mint', name: 'Mint', color: '#8FE6B4' },
] as const;
