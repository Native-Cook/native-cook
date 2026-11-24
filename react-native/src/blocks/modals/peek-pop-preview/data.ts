// "Margin", its six saved articles and their covers are invented. The covers are drawn in code (./CoverArt).

/** A cover shape. Positions and sizes are in units of the cover's height, measured from its centre. */
export type Shape =
  | { kind: 'circle'; x: number; y: number; size: number; color: string; opacity?: number }
  | { kind: 'ring'; x: number; y: number; size: number; stroke: number; color: string; opacity?: number }
  | { kind: 'bar'; x: number; y: number; width: number; height: number; turn?: number; radius?: number; color: string; opacity?: number };

export type Cover = {
  /** Three-stop gradient, top left to bottom right. */
  colors: [string, string, string];
  shapes: Shape[];
};

export type Article = {
  key: string;
  title: string;
  source: string;
  minutes: number;
  topic: string;
  /** The first paragraph: what the preview shows. */
  excerpt: string;
  body: string[];
  cover: Cover;
};

export const COPY = {
  brand: 'Margin',
  title: 'Reading list',
  edit: 'Edit',
  back: 'Reading list',
  add: 'Save a link',
};

export const ACTIONS = [
  { key: 'save', label: 'Save offline', symbol: 'arrow.down.circle', destructive: false },
  { key: 'share', label: 'Share', symbol: 'square.and.arrow.up', destructive: false },
  { key: 'archive', label: 'Archive', symbol: 'archivebox', destructive: true },
] as const;
export type ActionKey = (typeof ACTIONS)[number]['key'];

export const ARTICLES: Article[] = [
  {
    key: 'slow-software',
    title: 'The quiet case for slower software',
    source: 'Slow Wire',
    minutes: 6,
    topic: 'Software',
    excerpt:
      'For a decade the answer to every product question was to make it faster. Faster to load, faster to ship, faster to forget. A small group of teams has started asking what happens when a tool is allowed to take its time.',
    body: [
      'The first thing they noticed was the support inbox. When an export took four seconds and said so, people waited. When it took one second and failed without a word a tenth of the time, they wrote in. Speed had been standing in for trust, and it was a poor stand-in.',
      'None of this is an argument for spinners. It is an argument for honesty about what a computer is doing on your behalf. A progress bar that tells the truth is slower than one that lies, and almost everyone prefers it once they have seen both.',
      'The teams that slowed down did not lose their users. They lost the ones who had never been sure why they were there, and kept the ones who had work to finish.',
    ],
    cover: {
      colors: ['#0E5A4E', '#157A63', '#2FA07C'],
      shapes: [
        { kind: 'circle', x: -0.12, y: 0.02, size: 0.52, color: '#F3E9D2' },
        { kind: 'circle', x: 0.2, y: -0.08, size: 0.3, color: '#F28B3C' },
        { kind: 'ring', x: 0.2, y: -0.08, size: 0.46, stroke: 0.012, color: '#F3E9D2', opacity: 0.7 },
        { kind: 'bar', x: 0, y: 0.34, width: 0.9, height: 0.016, color: '#F3E9D2', opacity: 0.55 },
      ],
    },
  },
  {
    key: 'tide-tables',
    title: 'What tide tables taught me about deadlines',
    source: 'Harbour Review',
    minutes: 9,
    topic: 'Work',
    excerpt:
      'A tide table is the only schedule I have ever fully believed. It is printed a year ahead, it has never once been renegotiated, and the boats in our harbour plan their whole week around a column of numbers nobody argues with.',
    body: [
      'I started keeping one on my desk the winter our release slipped for the third time. Not as a joke, exactly. I wanted to look at a plan that was allowed to be true.',
      'What the table does well is separate the two things a deadline usually blurs together: when the water will be there, and whether you will be ready for it. The first is fixed. The second is yours. A skipper who misses the tide does not ask the sea for an extension.',
      'We now write two dates on everything. One is the tide, and it does not move. The other is our guess at being ready, and it moves all the time, in the open, where everyone can watch it.',
    ],
    cover: {
      colors: ['#12284F', '#1D4A8C', '#3D7CC9'],
      shapes: [
        { kind: 'bar', x: 0, y: -0.2, width: 0.42, height: 0.07, radius: 0.035, color: '#F4EFE3' },
        { kind: 'bar', x: 0.04, y: -0.08, width: 0.62, height: 0.07, radius: 0.035, color: '#9ADBE8' },
        { kind: 'bar', x: -0.03, y: 0.04, width: 0.5, height: 0.07, radius: 0.035, color: '#F4EFE3', opacity: 0.9 },
        { kind: 'bar', x: 0.02, y: 0.16, width: 0.72, height: 0.07, radius: 0.035, color: '#5FB6DD' },
        { kind: 'circle', x: 0.3, y: -0.3, size: 0.1, color: '#FFD166' },
      ],
    },
  },
  {
    key: 'old-houses',
    title: 'A field guide to the sounds of old houses',
    source: 'Lamplight',
    minutes: 4,
    topic: 'Home',
    excerpt:
      'Every old house keeps a short vocabulary. A tick in the hallway at dusk, a long sigh from the stairs around two, a knock in the pipes that arrives exactly when the heating does. Learn the words and the house stops being haunted.',
    body: [
      'The tick is timber. Wood that has spent the day warming gives the heat back in the evening, and each board lets go in its own time. It is loudest in autumn, when days and nights disagree the most.',
      'The sigh is the staircase settling under no weight at all. Builders call it creep. It is the sound of a structure that has been carrying a load for ninety years and is still, very slowly, getting comfortable.',
      'The knock deserves a plumber. Everything else deserves a cup of tea and a little patience.',
    ],
    cover: {
      colors: ['#7A2E1D', '#B4492A', '#DE7B45'],
      shapes: [
        { kind: 'ring', x: 0, y: 0.06, size: 0.26, stroke: 0.035, color: '#F6E7CF' },
        { kind: 'ring', x: 0, y: 0.06, size: 0.46, stroke: 0.03, color: '#F6E7CF', opacity: 0.75 },
        { kind: 'ring', x: 0, y: 0.06, size: 0.66, stroke: 0.024, color: '#F6E7CF', opacity: 0.5 },
        { kind: 'ring', x: 0, y: 0.06, size: 0.86, stroke: 0.018, color: '#F6E7CF', opacity: 0.28 },
        { kind: 'circle', x: 0, y: 0.06, size: 0.09, color: '#2B1710' },
      ],
    },
  },
  {
    key: 'night-trains',
    title: 'Why night trains are having a second life',
    source: 'Platform Nine',
    minutes: 12,
    topic: 'Travel',
    excerpt:
      'The sleeper from the coast leaves at 22:14 and arrives, eight hundred kilometres later, in time for breakfast. Ten years ago the line was a week from closing. This summer it sold out every Friday from May to September.',
    body: [
      'Ask the passengers why and nobody mentions speed. They talk about waking up somewhere. A flight gives you two airports and a headache; a night train hands you the journey as a kind of gift and takes none of your day in return.',
      'The economics are stubborn. A carriage that carries sixty seated people carries twenty-four asleep, and it only earns once a night. Operators who survived did it by selling the bed rather than the distance.',
      'What changed was not the trains. It was what people decided an evening was worth.',
    ],
    cover: {
      colors: ['#23174F', '#4A2A78', '#8A3F8C'],
      shapes: [
        { kind: 'bar', x: -0.16, y: 0.04, width: 0.1, height: 0.86, turn: 28, radius: 0.05, color: '#FFD23F' },
        { kind: 'bar', x: 0.0, y: 0.04, width: 0.1, height: 0.7, turn: 28, radius: 0.05, color: '#FF7FA8' },
        { kind: 'bar', x: 0.16, y: 0.04, width: 0.1, height: 0.54, turn: 28, radius: 0.05, color: '#F4EFE3', opacity: 0.92 },
        { kind: 'circle', x: 0.3, y: -0.26, size: 0.13, color: '#F4EFE3' },
      ],
    },
  },
  {
    key: 'weather-map',
    title: 'The map that put colour on the weather',
    source: 'Meridian',
    minutes: 7,
    topic: 'Science',
    excerpt:
      'Before the forecast was a picture it was a paragraph. Somebody read it aloud, you listened for the name of your county, and you guessed. The first printed map with a colour scale changed what people thought weather was.',
    body: [
      'The trick was not the colours. It was the legend: eleven steps from pale to dark, each one a promise that the same shade meant the same thing in every town on the page.',
      'Readers took to it in a season. Within two years farmers were writing in to complain about a single misprinted band, which is the surest sign that a chart has become part of how people think.',
      'We still argue about the palette. Nobody argues about the idea.',
    ],
    cover: {
      colors: ['#B8791A', '#E19A2B', '#F5C453'],
      shapes: [
        { kind: 'circle', x: -0.3, y: -0.16, size: 0.13, color: '#3B2A12' },
        { kind: 'circle', x: -0.1, y: -0.16, size: 0.13, color: '#3B2A12' },
        { kind: 'circle', x: 0.1, y: -0.16, size: 0.13, color: '#FFF4D6' },
        { kind: 'circle', x: 0.3, y: -0.16, size: 0.13, color: '#3B2A12' },
        { kind: 'circle', x: -0.3, y: 0.04, size: 0.13, color: '#3B2A12' },
        { kind: 'circle', x: -0.1, y: 0.04, size: 0.13, color: '#D94F30' },
        { kind: 'circle', x: 0.1, y: 0.04, size: 0.13, color: '#3B2A12' },
        { kind: 'circle', x: 0.3, y: 0.04, size: 0.13, color: '#3B2A12' },
        { kind: 'circle', x: -0.3, y: 0.24, size: 0.13, color: '#3B2A12' },
        { kind: 'circle', x: -0.1, y: 0.24, size: 0.13, color: '#3B2A12' },
        { kind: 'circle', x: 0.1, y: 0.24, size: 0.13, color: '#3B2A12' },
        { kind: 'circle', x: 0.3, y: 0.24, size: 0.13, color: '#FFF4D6' },
      ],
    },
  },
  {
    key: 'one-loaf',
    title: 'Notes from a year of baking one loaf',
    source: 'Crumb',
    minutes: 5,
    topic: 'Food',
    excerpt:
      'I baked the same loaf fifty-two times. Same flour, same tin, same oven that runs twelve degrees hot at the back. I wanted to find out how much there was to learn inside a recipe I already knew by heart.',
    body: [
      'Week nine was the first loaf I would have served to someone. Week twenty was the first I would not have apologised for. Nothing in the recipe had changed; I had simply started to notice the dough.',
      'The lesson is not that repetition makes you good. It is that a fixed recipe turns everything else into a variable you can finally see: the weather, the flour, your own impatience at ten to seven.',
      'I am starting again in January. Same loaf.',
    ],
    cover: {
      colors: ['#254D2B', '#3F7A3A', '#8DB652'],
      shapes: [
        { kind: 'bar', x: -0.15, y: 0.02, width: 0.3, height: 0.3, radius: 0.03, color: '#F2EAD6' },
        { kind: 'circle', x: 0.15, y: 0.02, size: 0.3, color: '#F0B43C' },
        { kind: 'bar', x: -0.15, y: -0.28, width: 0.3, height: 0.3, radius: 0.15, color: '#16301A', opacity: 0.85 },
        { kind: 'bar', x: 0.15, y: 0.32, width: 0.3, height: 0.3, turn: 45, radius: 0.03, color: '#F2EAD6', opacity: 0.6 },
      ],
    },
  },
];
