/** Colours, type and layout. Positions are measured from the source (402 pt wide screen). */
export const COLORS = {
  // Gate 2b (16 Oct): page, rows, row icons and card matched to the source's own palette (swatch dE <= 5).
  page: '#DCE5EC',
  ink: '#0B0C10',
  mute: '#7A808C',
  rowFill: '#CCD0DA',
  rowIcon: '#ABAFBB',
  headerButton: '#DDE1E7',
  card: '#0A1FE8',
  cardHigh: '#0C28F2',
  cardDeep: '#0006A8',
  cardInk: '#FFFFFF',
  cardMute: 'rgba(255,255,255,0.62)',
  cardFaint: 'rgba(255,255,255,0.28)',
  cardButton: 'rgba(0,10,120,0.34)',
  chip: '#D9DEFF',
  island: '#000000',
  sheetInk: '#FFFFFF',
  sheetMute: '#7D828C',
  sheetLine: 'rgba(255,255,255,0.08)',
  sheetHover: 'rgba(255,255,255,0.09)',
  chipOff: '#1C1D21',
  chipOn: '#FFFFFF',
  ring: '#3D5BFF',
  next: '#0010FF',
  success: '#3DDC84',
  positive: '#119C55',
  shadow: '#0A1A8C',
} as const;

export const TYPE = {
  balance: 32,
  cardSmall: 12,
  cardLabel: 11,
  brand: 20,
  title: 22,
  rowName: 15,
  rowMeta: 12,
  sheetTitle: 32,
  sheetSection: 22,
  favName: 12,
  contact: 15,
  chip: 14,
  pill: 15,
  hint: 13,
} as const;

/** Spacing scale (pt), from the source's own gaps (row gap 12, list inset 16, card pad 20, arrow 24). */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24 } as const;

/** Rest layout (y from the top of the screen). */
export const LAYOUT = {
  side: 50,
  pillTop: 5,
  pillWidth: 300,
  pillHeight: 48,
  islandWidth: 126,
  islandHeight: 37,
  islandTop: 11,
  headerCenter: 96.5,
  headerButton: 44,
  headerSpread: 26.5,
  cardTop: 160,
  /** "Drag to send" while pulling: centred where the card was (source frame a). */
  dragLabelTop: 305,
  cardHeight: 272,
  cardRadius: 26,
  cardPad: SPACE.xl,
  actionSize: 44,
  listTitleTop: 480,
  rowTop: 557,
  rowHeight: 63,
  rowGap: SPACE.md,
  rowIcon: 40,
  avatarSmall: 28,
  avatarOverlap: 10,
} as const;

/** Open sheet layout. y offsets are from the sheet's top. */
/** Open sheet, measured on source frame f028 (1.136 pt/px). y offsets are from the sheet's top. */
export const SHEET = {
  inset: 19,
  top: 89,
  bottomGap: 40,
  radius: 40,
  pad: 37,
  close: 40,
  closeRight: 18,
  titleTop: 36.5,
  titleSize: 47,
  titleLine: 50,
  favTop: 160,
  favSize: 42,
  favPad: 12,
  /** Per-favourite nudges (pt) to the source's spacing: three evenly, "Add New" a little further (frame 026). */
  favNudge: [1.7, -2, -6.3, -2.3],
  sectionTop: 271,
  rowsTop: 347,
  rowHeight: 59,
  contactSize: 39,
  /** Contact avatars' centre, from the sheet's left edge. */
  rowAvatarX: 54,
  nextTop: 681.5,
  nextHeight: 52,
  nextWidth: 281,
} as const;

/** The drop's shape between the pill and the sheet (g = 1). */
/** The drop at a full pull: a tall gourd hanging from the pill, as in source frame f025. */
export const DROP = { width: 230, height: 342, top: 40, fillet: 22, neckBreak: 22 } as const;

export const TOKEN = { scale: 0.42 } as const;

/** `photo`: generated from the source's avatars — portraits for favourites, glossy 3D faces for contacts. */
export type Person = { id: string; name: string; initials: string; from: string; to: string; photo: number };

export const FAVOURITES: Person[] = [
  { id: 'rafi', photo: require('./assets/fav-1.jpg'), name: 'Rafi', initials: 'R', from: '#FFB199', to: '#FF6A88' },
  { id: 'nico', photo: require('./assets/fav-2.jpg'), name: 'Nico', initials: 'N', from: '#9BE7FF', to: '#2F80ED' },
  { id: 'samir', photo: require('./assets/fav-3.jpg'), name: 'Samir', initials: 'S', from: '#FDE68A', to: '#F59E0B' },
];

export const CONTACTS: Person[] = [
  { id: 'ada', photo: require('./assets/face-1.png'), name: 'Ada Fenwick', initials: 'AF', from: '#C4B5FD', to: '#7C3AED' },
  { id: 'ravi', photo: require('./assets/face-2.png'), name: 'Ravi Okafor', initials: 'RO', from: '#A7F3D0', to: '#059669' },
  { id: 'lena', photo: require('./assets/face-3.png'), name: 'Lena Marsh', initials: 'LM', from: '#FBCFE8', to: '#DB2777' },
  { id: 'joon', photo: require('./assets/face-4.png'), name: 'Joon Park', initials: 'JP', from: '#BAE6FD', to: '#0284C7' },
  { id: 'sofia', photo: require('./assets/face-5.png'), name: 'Sofia Reyes', initials: 'SR', from: '#FED7AA', to: '#EA580C' },
];

export const AMOUNTS = [20, 50, 120, 250] as const;
export const DEFAULT_AMOUNT_INDEX = 2;
export const START_BALANCE = 1500075; // cents

export const TRANSACTIONS = [
  { id: 't1', name: 'Northwind Market', date: 'Oct 12, 2026', amount: -10356, kind: 'out' },
  { id: 't2', name: 'Salary · Harbor Co', date: 'Oct 10, 2026', amount: 100097, kind: 'in' },
  { id: 't3', name: 'Fern Coffee', date: 'Oct 9, 2026', amount: -650, kind: 'out' },
  { id: 't4', name: 'Juniper Books', date: 'Oct 8, 2026', amount: -2480, kind: 'out' },
] as const;
