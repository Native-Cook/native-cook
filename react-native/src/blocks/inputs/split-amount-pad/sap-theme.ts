// Split Amount Pad — colours, copy and the layout's numbers.

export const C = {
  /** the page: a warm paper gradient, top to bottom */
  paper: ['#F7F3EB', '#F2EDE3', '#EAE4D7'] as const,
  ink: '#1E1B16',
  /** secondary and tertiary text, the same warm grey family */
  mute: '#7B7568',
  faint: '#A8A294',
  /** the one accent */
  accent: '#1F6B4E',
  accentInk: '#F4FBF6',
  key: '#FFFEFB',
  keyPressed: 'rgba(40,32,20,0.07)',
  hair: 'rgba(40,32,20,0.08)',
} as const;

export const COPY = {
  title: 'Split the bill',
  place: "Dinner at Marlow's · Friday",
  limit: 'Up to $2,500 a bill',
  between: 'Split evenly between',
  out: 'Sits out',
  cta: 'Send requests',
} as const;

/** who is at the table: name, initials, the avatar's tint and its ink */
export const PEOPLE = [
  { name: 'You', initials: 'AK', tint: '#DCE8DD', ink: '#2F6B4A' },
  { name: 'Maren', initials: 'MH', tint: '#F6DFD2', ink: '#A0502C' },
  { name: 'Tobi', initials: 'TO', tint: '#D9E4F1', ink: '#355C88' },
  { name: 'Ines', initials: 'IV', tint: '#F2E6BF', ink: '#7A5E14' },
] as const;

export const KEY_LABELS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', ''] as const;
/** the code each pad cell sends (10 the dot, 11 backspace) */
export const KEY_CODES = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 11] as const;

export const SIDE = 20;

/** the numbers that change with the screen: a phone under 740 pt tall gets the compact set */
export function layoutFor(width: number, height: number) {
  const compact = height < 740;
  const gap = compact ? 8 : 10;
  const keyH = compact ? 54 : 64;
  const keyW = (width - SIDE * 2 - gap * 2) / 3;
  const amount = compact ? 54 : 64;
  return {
    compact,
    gap,
    keyH,
    keyW,
    /** the amount's font size and line height */
    amount,
    line: Math.round(amount * 1.14),
    disc: compact ? 46 : 52,
    cta: compact ? 48 : 54,
    block: compact ? 10 : 16,
    column: (width - SIDE * 2) / 4,
  };
}

/** the small rolling share: cell widths at 15 pt semibold tabular digits */
export const SHARE = { size: 15, line: 20, digit: 9.6, mark: 4.4 } as const;

/** the amount's glyph widths as fractions of its font size, used until the real ones are measured */
export const GUESS = { digit: 0.64, dot: 0.3, comma: 0.3, dollar: 0.36 } as const;
export const DOLLAR_SCALE = 0.56;
export const DOLLAR_GAP = 4;
/** digit slots kept mounted: seven can be typed, the rest are still flying away */
export const POOL = 12;
