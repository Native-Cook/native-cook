// The two feeds of Hearth, an invented podcast app. A refresh swaps one for the other.
// Every show, episode and cover is made up; the covers are drawn in Skia (CoverArt.tsx).

export type CoverMotif = 'sun' | 'rings' | 'bars' | 'stripes' | 'dots' | 'arc' | 'steps';

export type CoverSpec = {
  motif: CoverMotif;
  /** Background, top to bottom. */
  ground: [string, string, string];
  ink: string;
  accent: string;
};

export type Episode = {
  title: string;
  show: string;
  minutes: number;
  day: string;
  cover: CoverSpec;
};

export type Feed = {
  eyebrow: string;
  /** The featured episode's title, as it breaks over two lines. */
  title: [string, string];
  show: string;
  minutes: number;
  cover: CoverSpec;
  episodes: Episode[];
};

export const FEEDS: Feed[] = [
  {
    eyebrow: 'New this week',
    title: ['The Long Way', 'Round the Coast'],
    show: 'Salt & Signal',
    minutes: 42,
    cover: { motif: 'sun', ground: ['#f2b36b', '#e9824a', '#b4472f'], ink: '#2c2233', accent: '#fbe3b0' },
    episodes: [
      { title: 'How Bread Learned to Rise', show: 'Crumb Theory', minutes: 31, day: 'Today', cover: { motif: 'bars', ground: ['#e8dcc4', '#d9c7a3', '#c6ae84'], ink: '#5a3d24', accent: '#b8552f' } },
      { title: 'Field Guide to Night Trains', show: 'Slow Departures', minutes: 54, day: 'Mon', cover: { motif: 'rings', ground: ['#274046', '#1e3138', '#15232a'], ink: '#7fb6a8', accent: '#f0c987' } },
      { title: 'The Map That Was Wrong', show: 'Paper Coasts', minutes: 27, day: 'Sun', cover: { motif: 'stripes', ground: ['#d7e0d2', '#b9cdb8', '#93b19b'], ink: '#2f4a43', accent: '#f3ead7' } },
      { title: 'Notes From a Quiet Kitchen', show: 'Hearthside', minutes: 38, day: 'Fri', cover: { motif: 'arc', ground: ['#3a2430', '#532b33', '#7a3a32'], ink: '#f08a4b', accent: '#f6d7a6' } },
    ],
  },
  {
    eyebrow: 'Fresh for you',
    title: ['Small Hours,', 'Big Weather'],
    show: 'Overcast Club',
    minutes: 36,
    cover: { motif: 'rings', ground: ['#31405f', '#27324d', '#1a2136'], ink: '#9fb4e8', accent: '#f2c36b' },
    episodes: [
      { title: 'Why Rivers Bend', show: 'Loose Gravel', minutes: 44, day: 'Today', cover: { motif: 'steps', ground: ['#cfe0dc', '#a9c9c4', '#7fada9'], ink: '#1f4a4c', accent: '#f5efe0' } },
      { title: 'Last Orchard on the Line', show: 'Branch Lines', minutes: 58, day: 'Today', cover: { motif: 'dots', ground: ['#f0d9a8', '#e6bf78', '#d99f52'], ink: '#6b3a1d', accent: '#fff3d6' } },
      { title: 'Letters to a Lost Umbrella', show: 'Odd Post', minutes: 22, day: 'Tue', cover: { motif: 'sun', ground: ['#e6c9d0', '#cf9fb0', '#a86d8a'], ink: '#3c2340', accent: '#fbe9d2' } },
      { title: 'Tuning a House by Ear', show: 'Room Tone', minutes: 49, day: 'Sat', cover: { motif: 'bars', ground: ['#2b2a3f', '#39355a', '#4d4378'], ink: '#b9a8f0', accent: '#f2c9a0' } },
    ],
  },
];

/** Bar widths while loading, pt: fixed per slot, so the skeleton never shows what is coming. */
export const PLACEHOLDER = {
  eyebrow: 88,
  titleA: 132,
  titleB: 164,
  meta: 124,
  play: 86,
  rowTitle: [172, 204, 150, 186],
  rowMeta: [118, 96, 132, 104],
  day: 34,
};
