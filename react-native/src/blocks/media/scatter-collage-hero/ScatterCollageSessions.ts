/** One practice in the library: a photo in the collage, and the hero it opens into. */
export type Session = {
  /** Unsplash photo id. */
  photo: string;
  /** Orientation of the photo, which sets the tile's shape in the collage. */
  shape: 'portrait' | 'landscape' | 'square';
  /** The hero's title, on two lines; the second is set in italic. */
  title: [string, string];
  guide: string;
  minutes: number;
  /** Day the session was published, shown as "25 Apr". */
  date: string;
  category: Category;
};

export type Category = 'Meditation' | 'Yoga' | 'Breath' | 'Sleep';

/** The chips under the collage; "All" shows every tile, the others dim the rest. */
export const CATEGORIES = ['All', 'Meditation', 'Yoga', 'Breath', 'Sleep'] as const;

/** One line under the title, by category. */
export const BLURBS: Record<Category, string> = {
  Meditation: 'Settle on a single point and let everything else drift past, a little slower each time.',
  Yoga: 'A slow sequence that lengthens the spine and steadies the breath between each hold.',
  Breath: 'Four counts in, six counts out, until the shoulders drop and the body follows.',
  Sleep: 'Lower the lights, soften the jaw and let the day unhook itself, one thought at a time.',
};

/** Photo URL at a width in pixels, cropped to the photo's own aspect. */
export function photoUrl(id: string, width: number) {
  return `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=80`;
}

/**
 * Twenty-four sessions. Their order is the order they are born in the collage, so neighbours
 * here are kept unlike each other (the two sunset photos sit far apart).
 */
export const SESSIONS: Session[] = [
  { photo: 'photo-1506126613408-eca07ce68773', shape: 'portrait', title: ['Golden', 'Hour'], guide: 'Mira Okafor', minutes: 20, date: '25 Apr', category: 'Meditation' },
  { photo: 'photo-1791188012967-d34fdec9219d', shape: 'portrait', title: ['Salt', 'Balance'], guide: 'Theo Marlow', minutes: 35, date: '24 Apr', category: 'Yoga' },
  { photo: 'photo-1546458887-b4d5d7e7a00b', shape: 'portrait', title: ['Stacked', 'Calm'], guide: 'Noa Lindqvist', minutes: 12, date: '24 Apr', category: 'Sleep' },
  { photo: 'photo-1552196563-55cd4e45efb3', shape: 'landscape', title: ['Grounded', 'Breath'], guide: 'Ines Albrecht', minutes: 15, date: '23 Apr', category: 'Breath' },
  { photo: 'photo-1562088287-bde35a1ea917', shape: 'portrait', title: ['Eagle', 'Twist'], guide: 'June Okada', minutes: 40, date: '22 Apr', category: 'Yoga' },
  { photo: 'photo-1765490526583-4bf7f007096f', shape: 'landscape', title: ['Blue', 'Hour'], guide: 'Sol Ramírez', minutes: 25, date: '22 Apr', category: 'Sleep' },
  { photo: 'photo-1591228127791-8e2eaef098d3', shape: 'landscape', title: ['Tidal', 'Mudra'], guide: 'Mira Okafor', minutes: 10, date: '21 Apr', category: 'Meditation' },
  { photo: 'photo-1560233026-ad254fa8da38', shape: 'portrait', title: ['Lilac', 'Arch'], guide: 'Theo Marlow', minutes: 30, date: '20 Apr', category: 'Yoga' },
  { photo: 'photo-1602192509154-0b900ee1f851', shape: 'landscape', title: ['Open', 'Field'], guide: 'Ines Albrecht', minutes: 18, date: '19 Apr', category: 'Breath' },
  { photo: 'photo-1616074735081-0a3a0a79600d', shape: 'portrait', title: ['Ritual', 'Flame'], guide: 'Noa Lindqvist', minutes: 22, date: '18 Apr', category: 'Sleep' },
  { photo: 'photo-1447452001602-7090c7ab2db3', shape: 'landscape', title: ['Tree', 'Pose'], guide: 'June Okada', minutes: 28, date: '18 Apr', category: 'Yoga' },
  { photo: 'photo-1722094250550-4993fa28a51b', shape: 'landscape', title: ['Lake', 'Stillness'], guide: 'Sol Ramírez', minutes: 16, date: '17 Apr', category: 'Meditation' },
  { photo: 'photo-1554244933-d876deb6b2ff', shape: 'square', title: ['Heart', 'Centre'], guide: 'Mira Okafor', minutes: 24, date: '16 Apr', category: 'Yoga' },
  { photo: 'photo-1778159242575-3f2c0b02212d', shape: 'portrait', title: ['Coastal', 'Pause'], guide: 'Ines Albrecht', minutes: 8, date: '15 Apr', category: 'Breath' },
  { photo: 'photo-1600618528240-fb9fc964b853', shape: 'landscape', title: ['Palm', 'Shade'], guide: 'Theo Marlow', minutes: 20, date: '15 Apr', category: 'Meditation' },
  { photo: 'photo-1611800065908-233b597db552', shape: 'landscape', title: ['Evening', 'Ritual'], guide: 'Noa Lindqvist', minutes: 32, date: '14 Apr', category: 'Sleep' },
  { photo: 'photo-1556816723-1ce827b9cfbb', shape: 'landscape', title: ['Dusk', 'Dancer'], guide: 'June Okada', minutes: 26, date: '13 Apr', category: 'Yoga' },
  { photo: 'photo-1532798442725-41036acc7489', shape: 'landscape', title: ['Quiet', 'Hands'], guide: 'Sol Ramírez', minutes: 14, date: '12 Apr', category: 'Meditation' },
  { photo: 'photo-1764054655220-99548fc12fe3', shape: 'portrait', title: ['Shore', 'Cairn'], guide: 'Ines Albrecht', minutes: 11, date: '11 Apr', category: 'Breath' },
  { photo: 'photo-1695795910772-6336b0beba36', shape: 'landscape', title: ['Misty', 'Summit'], guide: 'Mira Okafor', minutes: 45, date: '10 Apr', category: 'Breath' },
  { photo: 'photo-1577344718665-3e7c0c1ecf6b', shape: 'landscape', title: ['Inner', 'Light'], guide: 'Noa Lindqvist', minutes: 19, date: '9 Apr', category: 'Sleep' },
  { photo: 'photo-1541588007165-da26f41a1996', shape: 'portrait', title: ['First', 'Light'], guide: 'Theo Marlow', minutes: 21, date: '8 Apr', category: 'Meditation' },
  { photo: 'photo-1782231722172-762f60de15a9', shape: 'landscape', title: ['River', 'Stones'], guide: 'Sol Ramírez', minutes: 17, date: '7 Apr', category: 'Sleep' },
  { photo: 'photo-1566501206188-5dd0cf160a0e', shape: 'portrait', title: ['Soft', 'Morning'], guide: 'June Okada', minutes: 30, date: '6 Apr', category: 'Meditation' },
];
