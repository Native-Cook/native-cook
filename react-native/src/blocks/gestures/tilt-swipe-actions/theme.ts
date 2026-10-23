/**
 * Direction A, "Private Ledger" (`.work/uxr/tsa/lux-1-ledger.html`): warm laid paper, deep ink, one brass accent.
 * The balance is the one dark, engraved object; the list is a ruled ledger set in a Didone. Every colour, font,
 * radius, shadow and size of the block lives here, so a new look is a change of tokens.
 */
export const COLOR = {
  paper: '#F5F1E8', // under the grain bitmap (assets/paper@3x.png)
  stock: '#FBF9F4', // the heavier paper of a share
  ink: '#17181D',
  ink2: '#6E695F', // secondary text
  ink3: '#A39D91', // tertiary: idle tabs
  ivory: '#F5F1E8', // text on ink
  ivory55: 'rgba(245,241,232,0.55)',
  ivory60: 'rgba(245,241,232,0.6)',
  ivory50: 'rgba(245,241,232,0.5)',
  ivory42: 'rgba(245,241,232,0.42)',
  brass: '#C8AD7E', // the accent on ink
  brassPaper: '#8B6B3A', // the accent on paper (labels, link)
  brassDot: '#A6844F', // the active tab's dot
  rule: 'rgba(23,24,29,0.10)', // ledger rules between rows
  ruleBottom: 'rgba(23,24,29,0.22)', // the ledger's closing rule
  ring: 'rgba(23,24,29,0.16)', // icon circles
  pieceEdge: 'rgba(23,24,29,0.14)',
  tabBar: '#EFEADF',
  monoToast: '#2E2F36', // a requested person's monogram on the ink toast
  flag: '#2B2C33',
  delete: '#8F3A2C', // oxide
  card: ['#22232A', '#17181D', '#121317'] as const,
  cardStops: [0, 0.55, 1] as const,
  cardFrame: 'rgba(200,173,126,0.38)',
  finger: 'rgba(23,24,29,0.10)',
  fingerEdge: 'rgba(23,24,29,0.18)',
  menuScrim: 'rgba(23,24,29,0.04)',
} as const;

export const SHADOW = {
  card: '0 1px 0 rgba(255,255,255,0.6), 0 18px 36px -18px rgba(30,24,12,0.55), 0 4px 10px -4px rgba(30,24,12,0.25)',
  piece: '0 14px 28px -14px rgba(40,30,10,0.45), 0 2px 4px rgba(40,30,10,0.06)',
  hud: '0 12px 24px -12px rgba(30,24,12,0.6)',
  toast: '0 16px 32px -16px rgba(30,24,12,0.6)',
  fab: '0 10px 20px -10px rgba(30,24,12,0.6)',
  menu: '0 18px 36px -18px rgba(30,24,12,0.45), 0 2px 6px rgba(30,24,12,0.08)',
} as const;

/** Spacing scale, 4 pt base. */
export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export const LAYOUT = {
  gutter: 16, // screen edge → card, ledger, toast, tab bar content
  cardHeight: 128,
  cardRadius: 20,
  cardFrameInset: 6,
  cardFrameRadius: 15,
  cardPad: 24, // labels; the figure sits at 22 (optical)
  sectionGap: 28, // card → section title
  sectionTitle: 32,
  listGap: 12,
  listPad: 5, // the ledger's 1 pt top rule and 4 pt of paper above the first row
  row: 72,
  circle: 40,
  text: 52, // title and subtitle, from the ledger's left edge (the ledger hangs on the edges)
  inset: 4, // a share inside the 72 pt slot
  bar: 64,
  outer: 14, // the bar's ends
  broken: 4, // a share's broken faces
  mini: 16,
  mono: 28,
  hud: 40,
  hudOver: 56, // the HUD's top above the row's slot: it covers the (dimmed) row above
  toast: 56,
  toastRadius: 16,
  tile: 72, // Flag and Delete, flush tiles the height of the row
  linkTop: 722, // "All transactions", from the screen's top on a 874 pt screen (kept 64 pt above the tab bar)
  tabBar: 54, // above the home area: the bar starts 88 pt above the screen's bottom, as in the mock
  tab: 56,
  tabHeight: 40,
  tabGap: 8,
  fab: 48,
  finger: 40,
  menu: { width: 220, item: 52, padY: 8, radius: 16 },
} as const;

/** Where a row rests when its actions are open: the two tiles. */
export const OPEN_X = -2 * LAYOUT.tile;

export type Scene = ReturnType<typeof sceneLayout>;

/** Every position on the screen, from its size and safe area. */
export function sceneLayout(width: number, height: number, insets: { top: number; bottom: number }) {
  const L = LAYOUT;
  const cardTop = insets.top;
  const sectionTop = cardTop + L.cardHeight + L.sectionGap;
  const listTop = sectionTop + L.sectionTitle + L.listGap;
  const listWidth = width - 2 * L.gutter;
  const barTop = height - insets.bottom - L.tabBar;
  const toastTop = barTop - L.listGap - L.toast - 4;
  const fit = (bottom: number) => Math.max(3, Math.floor((bottom - listTop - L.listPad - 3) / L.row));
  return {
    width, height,
    cardTop, sectionTop, listTop, listWidth,
    capacity: fit(toastTop),
    capacityUnderToast: fit(toastTop - 8),
    rowsTop: listTop + L.listPad,
    barTop, toastTop,
    linkTop: barTop - 64,
    fabLeft: width - L.gutter - L.fab,
    fabTop: barTop + 4,
    tabs: [0, 1, 2].map((i) => L.gutter + i * (L.tab + L.tabGap) + L.tab / 2),
  };
}

/**
 * Bodoni Moda and Instrument Sans (SIL OFL, `assets/BodoniModa-OFL.txt`, `assets/InstrumentSans-OFL.txt`). The
 * variable fonts are cut into static instances at the optical size each use is set at (the mock lets the browser
 * pick `opsz` = font size), latin only, renamed so iOS keeps them apart.
 */
export const FONT = {
  display: 'TSABodoni48', // 500, opsz 48: the balance
  money22: 'TSABodoni22', // 500, opsz 22: the balance's cents
  money22r: 'TSABodoni22R', // 400, opsz 22: the balance's dollar sign
  money: 'TSABodoni20', // 500, opsz 20: amounts, shares, the HUD
  moneySmall: 'TSABodoni14', // 500, opsz 14: cents
  mono: 'TSABodoni11', // 500, opsz 11: monograms
  italic: 'TSABodoniItalic26', // italic 400, opsz 26: the section title
  sans: 'TSAInstrument400',
  sansMedium: 'TSAInstrument500',
  sansSemi: 'TSAInstrument600',
} as const;

export const FONT_FILES = {
  [FONT.display]: require('./assets/TSABodoni48.ttf'),
  [FONT.money22]: require('./assets/TSABodoni22.ttf'),
  [FONT.money22r]: require('./assets/TSABodoni22R.ttf'),
  [FONT.money]: require('./assets/TSABodoni20.ttf'),
  [FONT.moneySmall]: require('./assets/TSABodoni14.ttf'),
  [FONT.mono]: require('./assets/TSABodoni11.ttf'),
  [FONT.italic]: require('./assets/TSABodoniItalic26.ttf'),
  [FONT.sans]: require('./assets/TSAInstrument400.ttf'),
  [FONT.sansMedium]: require('./assets/TSAInstrument500.ttf'),
  [FONT.sansSemi]: require('./assets/TSAInstrument600.ttf'),
} as const;

const LINING = ['lining-nums', 'proportional-nums'] as ('lining-nums' | 'proportional-nums')[];
const TABULAR = ['lining-nums', 'tabular-nums'] as ('lining-nums' | 'tabular-nums')[];

export const TYPE = {
  cardLabel: { fontFamily: FONT.sansMedium, fontSize: 11, lineHeight: 16, letterSpacing: 1.98 }, // caps, +0.18 em
  cardNumber: { fontFamily: FONT.sans, fontSize: 12, lineHeight: 16, letterSpacing: 0.96, fontVariant: TABULAR },
  cardNote: { fontFamily: FONT.sans, fontSize: 12, lineHeight: 16 },
  balance: { fontFamily: FONT.display, fontSize: 48, lineHeight: 72, letterSpacing: -0.5, fontVariant: LINING },
  balanceCents: { fontFamily: FONT.money22, fontSize: 22, lineHeight: 28, fontVariant: LINING },
  balanceSign: { fontFamily: FONT.money22r, fontSize: 22, lineHeight: 28 },
  section: { fontFamily: FONT.italic, fontSize: 26, lineHeight: 36, letterSpacing: -0.2 },
  sectionMeta: { fontFamily: FONT.sans, fontSize: 13, lineHeight: 16, fontVariant: TABULAR },
  rowTitle: { fontFamily: FONT.sansMedium, fontSize: 16, lineHeight: 20, letterSpacing: -0.1 },
  rowSub: { fontFamily: FONT.sans, fontSize: 13, lineHeight: 16 },
  amount: { fontFamily: FONT.money, fontSize: 20, lineHeight: 32, letterSpacing: -0.2, fontVariant: LINING },
  cents: { fontFamily: FONT.moneySmall, fontSize: 14 },
  amountOf: { fontFamily: FONT.sans, fontSize: 12, lineHeight: 16, fontVariant: TABULAR },
  mini: { fontFamily: FONT.mono, fontSize: 9, lineHeight: 13 },
  mono: { fontFamily: FONT.mono, fontSize: 11, lineHeight: 17, letterSpacing: 0.44 },
  pieceName: { fontFamily: FONT.sansMedium, fontSize: 10, lineHeight: 12, letterSpacing: 1.4 }, // caps, +0.14 em
  pieceAmount: { fontFamily: FONT.money, fontSize: 18, lineHeight: 28, fontVariant: LINING },
  pieceCents: { fontFamily: FONT.moneySmall, fontSize: 13 },
  hudSum: { fontFamily: FONT.money, fontSize: 18, lineHeight: 28, fontVariant: LINING },
  hudCents: { fontFamily: FONT.moneySmall, fontSize: 13 },
  hudDivide: { fontFamily: FONT.sans, fontSize: 16, lineHeight: 20 },
  hudStep: { fontFamily: FONT.sansMedium, fontSize: 13, lineHeight: 16 },
  hudStepOn: { fontFamily: FONT.sansSemi, fontSize: 13, lineHeight: 16 },
  toastTitle: { fontFamily: FONT.sansMedium, fontSize: 15, lineHeight: 20, fontVariant: TABULAR },
  toastSub: { fontFamily: FONT.sans, fontSize: 12, lineHeight: 16, fontVariant: TABULAR },
  toastAction: { fontFamily: FONT.sansSemi, fontSize: 14, lineHeight: 18 },
  action: { fontFamily: FONT.sansMedium, fontSize: 11, lineHeight: 16, letterSpacing: 1.32 }, // caps, +0.12 em
  link: { fontFamily: FONT.sansMedium, fontSize: 11, lineHeight: 24, letterSpacing: 1.54 }, // caps, +0.14 em
  menu: { fontFamily: FONT.sansMedium, fontSize: 16, lineHeight: 20 },
} as const;

/**
 * iOS sets Bodoni Moda higher in its line box than the browser does (its tall ascender; more the larger the size):
 * positions of text in the Didone add this so it lands where the mock has it. Measured against lux-1-ledger.png at 3×.
 */
/**
 * iOS clips text to its line box, and Bodoni Moda's ascenders (the 1's flag, the $'s stroke) rise above a tight one,
 * so the Didone styles get a taller line box and iOS puts the extra above the glyphs. Each use pulls the text back
 * up by this much (marginTop), so the glyphs and the box's bottom stay where the mock has them.
 */
// A line box clears the glyphs when it is at least 1.23 em (0.83 em of glyph over a 0.40 em descent); section, monogram
// and mini monogram were at 1.23, 1.18 and 1.22 em, so they get room too.
export const DIDONE_ROOM = { display: 20, amount: 8, piece: 8, hud: 6, title: 4, mono: 4, mini: 2 } as const;

export const DIDONE_DROP = { display: 25 / 3, title: 8 / 3, money22: 8 / 3, money: 2, mono: 5 / 3 } as const;

export const ICON = { row: 20, tab: 24, fab: 22, action: 20, stroke: 1.5, fabStroke: 1.75 } as const;
