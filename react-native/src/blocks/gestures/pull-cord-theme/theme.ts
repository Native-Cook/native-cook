// Every colour, size and word of the block. Sizes are points on a 402 pt wide screen, scaled by width / 402.
// Spacing sits on a 4 pt grid: page margin 24, header and body blocks on multiples of 4.

export const STAGE_WIDTH = 402;

export type Palette = {
  paper: string;
  ink: string;
  mute: string;
  accent: string;
  track: string;
};

// Day is the lamp on, Night the lamp off. One accent hue: the shade's green enamel, repeated in the page's rule
// and the reading progress.
export const DAY: Palette = {
  paper: '#F1EBE0',
  ink: '#27231E',
  mute: '#6E6558', // 4.9:1 on the paper
  accent: '#2F5D48',
  track: '#DCD3C4',
};

export const NIGHT: Palette = {
  paper: '#121211',
  ink: '#D8D0C2',
  mute: '#8E877C', // 5.1:1 on the night paper
  accent: '#83B097',
  track: '#2A2926',
};

// The lamp's light along a blackbody ramp, by filament heat 0 (cold) to 1 (2,700 K).
export const BLACKBODY = {
  heat: [0, 0.3, 0.5, 0.72, 1],
  color: ['#3A0800', '#B32204', '#F2600F', '#FFA94F', '#FFE4BC'],
};

// The pendant. Local frame: origin at the top of the cap, y down along the cord.
export const LAMP = {
  x: 312, // cord x: the shade clears the island and the heading
  capTop: 60, // cap top at rest
  cord: 640, // pivot (the ceiling) above the cap top
  capHalf: 7,
  capHeight: 12,
  rimY: 72, // local
  rimHalf: 68,
  rimRy: 9, // the opening seen from a little below
  lip: 2.4,
  bulbY: 70, // local bulb centre
  bulbR: 16,
  socket: { x: 26, y: 74 }, // where the pull chain leaves the socket, local
  depth: 120, // distance from the page (the wall) to the bulb
  coneDeg: 64, // half angle of the light the shade lets out, around its axis
  penumbraDeg: 8,
};

export const SHADE = {
  // Lit room: the outside of the shade still faces away from its own bulb, so it stays deep; the window reflection is
  // warm, picked up from the lit page below.
  day: { hi: '#4E7362', mid: '#23402F', low: '#0E1B14', spec: 'rgba(255, 232, 196, 0.3)', lip: '#E2DACB', cord: '#1F1D1A' },
  night: { hi: '#2A3A32', mid: '#121A16', low: '#090D0B', spec: 'rgba(196, 220, 208, 0.16)', lip: '#363A36', cord: '#2A2926' },
  // The white enamel inside, lit by the bulb (centre, edge) or cold.
  litCentre: '#FBEBD0',
  litEdge: '#D9AD72',
  coldCentre: '#34322D',
  coldEdge: '#1C1B18',
  glassCold: '#3A3833',
  glassColdEdge: '#1A1916',
  lipLit: '#F7D49E', // the rolled lip catching the bulb from inside
  bounce: 'rgba(255, 204, 150, 0.16)', // the lit page's warm light on the dome's lower outside
};

// The page as a wall a little behind the lamp. The chain hangs from the back of the opening, between the bulb and
// the wall, so the bulb throws its shadow onto the page: projected from the bulb, twice its distance (the wall is
// twice as far from the bulb as the chain is), with a penumbra as wide as the bulb makes it.
export const SHADOW = {
  k: 2, // projection from the bulb through the chain onto the page
  penumbra: 10, // pt, the soft width of the chain's shadow
  chain: 0.45, // share of the direct light the ball chain takes away at the middle of its shadow
  pull: 0.7, // the teardrop is wider
  shadeUp: 30, // the shade's own shadow on the wall above it, cast by the light coming back up from the page
  shadeRx: 110,
  shadeRy: 60,
  shadeDepth: 0.7, // share of that bounce light it takes away
};

// How the lit page is seen: an ambient share, the bounce off the room near the lamp, and the lamp's direct light.
export const EXPOSE = {
  ambient: 0.83, // far from the lamp
  bounce: 0.06,
  direct: 0.15, // in the pool
  dim: 0.5, // the lit page dims to this with the filament before the front reaches it
  adaptLift: 0.35, // the dark page's ambient lifts this much as the eye adapts to the light
  adaptTime: 0.4,
};

// The lamp's light on the Night page while the filament glows: paper and type take this share of it.
export const NIGHT_GLOW = { paper: 0.5, type: 0.12 };

// Contrast of the type against the paper under it while the theme changes. Below `rest` (every role is above it at
// rest, so the rest colours are untouched) mute leans toward the ink and is the ink at `floor`; the green leans the same
// way by `accentShift` toward black (Day) or white (Night), keeping its hue. Worst frame: ink and mute 4:1, green 3.8:1.
export const LEGIBLE = { floor: 3, rest: 4.2, accentShift: 0.5 };

// The air under the shade: a faint haze in the cone and a few slow dust motes catching its light.
export const AIR = {
  cone: [56, 70], // degrees from the shade's axis: full haze inside the first, none past the second
  reach: 140, // pt: the haze halves at this distance from the bulb
  haze: 0.07, // strongest additive share, next to the bulb
  motes: 12,
  mote: 0.32, // a mote's peak brightness in full light
  moteSize: [0.7, 1.3], // radius range, pt
  drift: 6, // pt each mote wanders in a loop
};

// A nickel ball chain: one simulated particle per ball, so every ball is drawn where it really is.
export const CHAIN = {
  links: 24, // balls below the switch (one particle each), plus the switch's own end
  pitch: 4.6, // ball centre to ball centre when the chain is straight
  ball: 1.75, // radius: 1.1 pt of wire shows between balls
  wire: 0.6,
  pullWidth: 10, // the nickel teardrop pull
  pullHeight: 22,
  hit: 56, // touch target around the pull
  // Each ball: a dark rim, a lit body offset toward the light, a specular point.
  day: { rim: '#3F3D39', body: '#A8A196', spec: 'rgba(255, 244, 222, 0.95)', wire: '#5C5A55' },
  night: { rim: '#1F1E1C', body: '#5E5B55', spec: 'rgba(232, 226, 216, 0.8)', wire: '#3A3935' },
};

// The pull switch's housing: a knurled nickel barrel hanging through the shade's opening, the chain leaving its foot.
export const SWITCH_BODY = {
  x: -30, // local, from the shade's axis: left of the bulb, at the back of the opening (between bulb and wall)
  top: 60, // local, its top seen through the opening
  height: 26, // its foot 6 pt below the rim's near edge
  width: 9,
  knurls: 4,
  day: ['#4A4844', '#E2D6C2', '#8E8A82', '#3E3C38'],
  night: ['#22211F', '#6E6A63', '#45423D', '#1C1B19'],
};

export const TYPE = {
  family: 'Newsreader',
  margin: 24,
  eyebrowTop: 124,
  eyebrowSize: 12,
  eyebrowLine: 16,
  eyebrowTracking: 2.2,
  titleTop: 144,
  titleSize: 42,
  titleLine: 48,
  ruleTop: 192, // 12 pt under the title's baseline, as the eyebrow sits over its caps
  ruleWidth: 28,
  ruleHeight: 2,
  bodyTop: 296,
  bodySize: 18,
  bodyLine: 29,
  footBottom: 48, // labels' baseline box bottom from the screen's bottom
  footSize: 13,
  footLine: 16,
  barGap: 12, // progress bar above the labels
  barHeight: 2,
};

export const BOOK = {
  title: 'The Salt Ledger',
  chapter: 'CHAPTER SEVEN',
  heading: 'Low Water',
  body: [
    'By the time the tide turned, Mara had counted the boats twice. Eleven out, ten back. She wrote the number in the ledger anyway, in pencil, the way her father had taught her: nothing in ink until morning.',
    'The lamp above the desk swung a little whenever the door opened, and the light slid across the page and back. Outside, fog had taken the breakwater. The last ferry’s horn came through it late and flattened, like a voice from another room.',
    'She left the window open. If the eleventh boat came in, she wanted to hear it before she saw it. Her father used to say the sea kept its own books, and that it was never late with a payment, only with the news.',
  ],
  page: 112,
  pages: 304,
  left: '9 min left in chapter',
};

export const COPY = {
  a11yLabel: 'Reading lamp',
  a11yHint: 'Pull the chain to switch between the Day and Night page',
  day: 'Day page',
  night: 'Night page',
};
