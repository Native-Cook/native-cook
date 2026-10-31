// Clay Category Tabs — colours, type, the three categories and their listings, and the layout.

export const C = {
  page: '#111015',
  card: '#1B1A21',
  line: '#2A2932',
  ink: '#F4F3F7',
  mute: '#8E8C99',
  accent: '#FF5A4F',
  star: '#FFC24A',
} as const;

export const FONT = {
  regular: 'CctInter-Regular',
  medium: 'CctInter-Medium',
  semibold: 'CctInter-SemiBold',
} as const;

export type Listing = { title: string; meta: string; when: string; price: string; unit: string; rating: string };
export type Category = { name: string; heading: string; listings: Listing[] };

export const CATEGORIES: Category[] = [
  {
    name: 'Homes',
    heading: 'Stays near Lisbon',
    listings: [
      { title: 'Cabin above the tide', meta: 'Ericeira · 2 beds', when: 'Nov 12 – 17', price: '€142', unit: 'night', rating: '4.92' },
      { title: 'Whitewashed loft in Alfama', meta: 'Lisbon · 1 bed', when: 'Nov 3 – 6', price: '€118', unit: 'night', rating: '4.88' },
      { title: 'Cork-oak farmhouse', meta: 'Alentejo · 4 beds', when: 'Dec 1 – 5', price: '€205', unit: 'night', rating: '4.97' },
    ],
  },
  {
    name: 'Experiences',
    heading: 'Things to do nearby',
    listings: [
      { title: 'Sunrise balloon over the plains', meta: 'Évora · 3 hours', when: 'Sat, 6:10 AM', price: '€190', unit: 'guest', rating: '4.99' },
      { title: 'Paint your own azulejo', meta: 'Lisbon · 2 hours', when: 'Every day', price: '€45', unit: 'guest', rating: '4.91' },
      { title: 'Surf the north swell', meta: 'Ericeira · 4 hours', when: 'Sun, 9:00 AM', price: '€60', unit: 'guest', rating: '4.86' },
    ],
  },
  {
    name: 'Services',
    heading: 'Brought to your stay',
    listings: [
      { title: 'Private seafood dinner', meta: 'Chef at home · up to 6', when: 'Any evening', price: '€85', unit: 'guest', rating: '4.95' },
      { title: 'Morning yoga on your terrace', meta: '60 minutes', when: '7 – 10 AM', price: '€40', unit: 'guest', rating: '4.90' },
      { title: 'Luggage drop and city transfer', meta: 'Door to door', when: 'Any day', price: '€28', unit: 'trip', rating: '4.84' },
    ],
  },
];

export const SEARCH_H = 56;
export const STAGE_H = 244;
export const ICON_Y = 108; // the icons' centre in the stage
export const ICON_S = 76; // the lit icon's unit size (pt)
export const GHOST_S = 24;
export const LABEL_Y = 204;
export const HOP = 30;

export function makeLayout(w: number, top: number) {
  const searchTop = top + 8;
  const stageTop = searchTop + SEARCH_H + 8;
  const pagerTop = stageTop + STAGE_H;
  const slot = Math.min(114, (w - 64) / 3);
  const xs = [w / 2 - slot, w / 2, w / 2 + slot];
  return { w, searchTop, stageTop, pagerTop, slot, xs };
}
