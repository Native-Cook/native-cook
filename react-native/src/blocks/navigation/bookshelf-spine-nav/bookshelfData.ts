// The shelf: one book per section, each a different binding. Every title and name is invented.

export type Binding = 'cloth' | 'leather' | 'linen';

export type Row = { title: string; author: string; progress: number; tone: [string, string] };

export type ShelfBook = {
  section: string;
  /** Spine width and height in points. */
  w: number;
  h: number;
  /** Base colour of the binding and the colour of its foil. */
  color: string;
  foil: string;
  binding: Binding;
  count: number;
  blurb: string;
  rows: Row[];
};

export const BOOKS: ShelfBook[] = [
  {
    section: 'Novels',
    w: 40,
    h: 196,
    color: '#2F5243',
    foil: '#D9B663',
    binding: 'cloth',
    count: 41,
    blurb: '41 books · 2 in progress',
    rows: [
      { title: 'The Salt Orchard', author: 'Ines Calloway', progress: 0.64, tone: ['#C96F4A', '#5B2A2E'] },
      { title: 'Harbour Lights', author: 'Teo Marchetti', progress: 0.12, tone: ['#3F6E8C', '#1C2B3E'] },
      { title: 'A Year of Small Rain', author: 'Juno Vale', progress: 1, tone: ['#8DA36A', '#2F4030'] },
    ],
  },
  {
    section: 'Essays',
    w: 40,
    h: 210,
    color: '#6E2430',
    foil: '#E2C072',
    binding: 'leather',
    count: 12,
    blurb: '12 books · 3 in progress',
    rows: [
      { title: 'On Walking Slowly', author: 'Mira Okonjo', progress: 0.38, tone: ['#D9A24E', '#6B3A1F'] },
      { title: 'The Kitchen Window', author: 'Paul Arden-Hale', progress: 0.81, tone: ['#6F8FA6', '#26364A'] },
      { title: 'Notes from a Ferry', author: 'Sana Iqbal', progress: 0.07, tone: ['#B85C6C', '#45202C'] },
    ],
  },
  {
    section: 'Poetry',
    w: 32,
    h: 178,
    color: '#26365A',
    foil: '#C9D1DE',
    binding: 'cloth',
    count: 19,
    blurb: '19 books · 1 in progress',
    rows: [
      { title: 'Lantern Hours', author: 'Elsa Brandt', progress: 0.5, tone: ['#E0B64F', '#5A4414'] },
      { title: 'Field Recordings', author: 'Kofi Mensah-Lowe', progress: 1, tone: ['#7CA08B', '#24392F'] },
      { title: 'Blue Hour Psalms', author: 'Noor Haddad', progress: 0.22, tone: ['#5966A8', '#1E2448'] },
    ],
  },
  {
    section: 'Notes',
    w: 42,
    h: 168,
    color: '#B8862F',
    foil: '#3A2510',
    binding: 'linen',
    count: 86,
    blurb: '86 highlights · 9 books',
    rows: [
      { title: '“Rivers keep their own time.”', author: 'The Salt Orchard, p. 112', progress: 1, tone: ['#C96F4A', '#5B2A2E'] },
      { title: '“Begin where your feet are.”', author: 'On Walking Slowly, p. 9', progress: 1, tone: ['#D9A24E', '#6B3A1F'] },
      { title: '“Light is a kind of weather.”', author: 'Lantern Hours, p. 31', progress: 1, tone: ['#E0B64F', '#5A4414'] },
    ],
  },
  {
    section: 'Saved',
    w: 36,
    h: 202,
    color: '#4E2F52',
    foil: '#E0BE6E',
    binding: 'cloth',
    count: 27,
    blurb: '27 books saved for later',
    rows: [
      { title: 'The Copper Bell', author: 'Owen Fisk', progress: 0, tone: ['#B9784A', '#4A2A18'] },
      { title: 'North of the River', author: 'Lise Gordon', progress: 0, tone: ['#6F9AA0', '#203A3E'] },
      { title: 'Wild Plum', author: 'Hana Grove', progress: 0, tone: ['#9C5A7E', '#3A1D30'] },
    ],
  },
  {
    section: 'History',
    w: 40,
    h: 186,
    color: '#8A5A35',
    foil: '#F0CF83',
    binding: 'leather',
    count: 15,
    blurb: '15 books · 1 in progress',
    rows: [
      { title: 'The Canal Builders', author: 'R. T. Ashdown', progress: 0.44, tone: ['#8E7A5A', '#2F271A'] },
      { title: 'Tea, Silk and Salt', author: 'Wen Huang', progress: 0, tone: ['#B44A3C', '#3E1914'] },
      { title: 'Map of a Vanished Sea', author: 'Ilse Varga', progress: 1, tone: ['#4E7E9E', '#1A2E3E'] },
    ],
  },
  {
    section: 'Lent',
    w: 30,
    h: 160,
    color: '#2E6466',
    foil: '#D3DCD8',
    binding: 'cloth',
    count: 4,
    blurb: '4 books with friends',
    rows: [
      { title: 'The Long Quiet', author: 'with Dara · due 3 Nov', progress: 0, tone: ['#5E7F6E', '#1E2E26'] },
      { title: 'Small Hours', author: 'with Felix · due 9 Nov', progress: 0, tone: ['#C78B5A', '#4A2C18'] },
      { title: 'Paper Lanterns', author: 'with Ama · due 21 Nov', progress: 0, tone: ['#D0A452', '#4E3A12'] },
    ],
  },
  {
    section: 'Audio',
    w: 38,
    h: 192,
    color: '#34353B',
    foil: '#D69A6A',
    binding: 'leather',
    count: 8,
    blurb: '8 audiobooks · 6 h 20 min left',
    rows: [
      { title: 'The Weather Clock', author: 'read by June Ellery · 4 h', progress: 0.71, tone: ['#6E7FA8', '#232A44'] },
      { title: 'Lamplight', author: 'read by Theo Rask · 7 h', progress: 0.18, tone: ['#D49A4C', '#4A3014'] },
      { title: 'Saltmarsh Hours', author: 'read by Odile Fenn · 5 h', progress: 1, tone: ['#7FA38F', '#263A30'] },
    ],
  },
  {
    section: 'Wishlist',
    w: 38,
    h: 174,
    color: '#9A4E5A',
    foil: '#F2E2C4',
    binding: 'linen',
    count: 33,
    blurb: '33 books · 4 on sale',
    rows: [
      { title: 'The Ninth Lighthouse', author: 'Colm Frost', progress: 0, tone: ['#4F7A9A', '#1A2A3A'] },
      { title: 'A Room of Blue Tiles', author: 'Ottilie Glenn', progress: 0, tone: ['#3F6FB0', '#16284A'] },
      { title: 'Rooms Above the Bakery', author: 'Margo Flint', progress: 0, tone: ['#C9925A', '#4C301A'] },
    ],
  },
];

/** The book the self-playing demo pulls. */
export const DEMO_BOOK = 1;
