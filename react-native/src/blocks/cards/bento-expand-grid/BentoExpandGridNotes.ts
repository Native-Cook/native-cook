/** Photo URL at a width in pixels, cropped to the photo's own aspect. */
export function photoUrl(id: string, width: number) {
  return `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=80`;
}

/** What a tile shows in the grid, and what it grows into at the top of its page. */
export type Face =
  | { kind: 'photo'; photo: string }
  | { kind: 'stat'; value: string; unit: string; label: string }
  | { kind: 'quote'; text: string; by: string }
  | { kind: 'map'; place: string; stops: number };

/** A tile's place on the grid: column and row, and how many of each it spans (4 columns × 4 rows). */
export type Span = { col: number; row: number; cols: number; rows: number };

/** One field note: a tile on the grid and the page it opens into. */
export type Note = {
  id: string;
  span: Span;
  face: Face;
  kicker: string;
  title: string;
  byline: string;
  lead: string;
  body: string[];
  pull: string;
  /** Two photos set side by side in the middle of the page. */
  pair: [string, string];
  outro: string;
};

export const GRID_COLUMNS = 4;
export const GRID_ROWS = 4;

export const NOTES: Note[] = [
  {
    id: 'desk',
    span: { col: 0, row: 0, cols: 2, rows: 2 },
    face: { kind: 'photo', photo: 'photo-1499750310107-5fef28a66643' },
    kicker: 'Workspace',
    title: 'The slow desk',
    byline: 'Ines Moreau · 6 min read',
    lead: 'A desk that asks for nothing is a desk you return to. Ours holds one notebook, one cup and the light from the east window.',
    body: [
      'We cleared everything that blinked. The lamp stays off until four, the phone lives in a drawer, and the notebook is the only screen.',
      'Most mornings start with ten minutes of copying a page by hand. It is not about the words. It is about letting the day arrive at the speed of a pen.',
    ],
    pull: 'Clear the desk, and the work finds its own edges.',
    pair: ['photo-1611269154421-4e27233ac5c7', 'photo-1487017159836-4e23ece2e4cf'],
    outro: 'By noon the cup is cold and the page is full. That is the whole system.',
  },
  {
    id: 'pages',
    span: { col: 2, row: 0, cols: 2, rows: 1 },
    face: { kind: 'stat', value: '1,284', unit: 'pages', label: 'Read this season' },
    kicker: 'Reading log',
    title: 'Twelve books, one chair',
    byline: 'Theo Lindahl · 4 min read',
    lead: 'Twenty-one pages a day, give or take, since the first cold morning in September. Nothing heroic: one chair, one lamp, no phone in the room.',
    body: [
      'The number only matters because it is boring. A habit that survives a dull Tuesday is the one that lasts the winter.',
      'Half the list was chosen by the shelf at the café on Wren Street. The other half came from the margins of the first half.',
    ],
    pull: 'Twenty pages is a walk, not a race.',
    pair: ['photo-1541963463532-d68292c34b19', 'photo-1506880018603-83d5b814b5a6'],
    outro: 'Next season the chair stays, the lamp gets a warmer bulb, and the goal stays quietly the same.',
  },
  {
    id: 'tea',
    span: { col: 2, row: 1, cols: 1, rows: 1 },
    face: { kind: 'photo', photo: 'photo-1531969179221-3946e6b5a5e7' },
    kicker: 'Ritual',
    title: 'Morning tea',
    byline: 'Aiko Brandt · 5 min read',
    lead: 'Water just off the boil, a bowl warmed in the steam, and three minutes where the only job is to wait.',
    body: [
      'The whisk moves in a quick zigzag, never a circle, until the foam is fine enough to hold a thumbprint.',
      'Nobody talks during the first bowl. It is the one rule of the house that nobody has ever broken.',
    ],
    pull: 'Wait for the water, and the water waits for you.',
    pair: ['photo-1564890369478-c89ca6d9cde9', 'photo-1567922045116-2a00fae2ed03'],
    outro: 'The second bowl is for conversation. The third is for whoever arrives late.',
  },
  {
    id: 'kyoto',
    span: { col: 3, row: 1, cols: 1, rows: 1 },
    face: { kind: 'map', place: 'Kōzan', stops: 4 },
    kicker: 'Walk',
    title: 'Four stops along the river',
    byline: 'Aiko Brandt · 7 min read',
    lead: 'A morning loop through the old quarter of Kōzan: two tea houses, a paper shop and a bench that faces the water.',
    body: [
      'Start at the north bridge before the shutters go up. The first tea house opens at seven and only seats six.',
      'Follow the river south past the paper shop, where the owner will press a sheet for you while you wait.',
    ],
    pull: 'The best route is the one that ends at a bench.',
    pair: ['photo-1531970227416-f0cddeb1f748', 'photo-1577016029703-cc22a7c0c28c'],
    outro: 'The loop is four kilometres. Plan on three hours, most of them sitting down.',
  },
  {
    id: 'hands',
    span: { col: 0, row: 2, cols: 3, rows: 1 },
    face: { kind: 'quote', text: 'The hands remember what the mind forgets.', by: 'Maren Holt, potter' },
    kicker: 'Studio talk',
    title: 'What the hands remember',
    byline: 'Maren Holt · 5 min read',
    lead: 'Thirty years at the wheel and she still starts every session the same way: wedging the clay until her shoulders stop thinking.',
    body: [
      'Centring is the whole lesson. If the clay is not centred nothing after it will be, and no amount of skill upstream fixes it.',
      'She keeps a bowl from her first year on the shelf by the door. It is lopsided and heavy, and it is the one she drinks from.',
    ],
    pull: 'Centre the clay, then centre yourself. The order matters less than you think.',
    pair: ['photo-1590605095243-072811dbe64c', 'photo-1607556671927-78a6605e290b'],
    outro: 'Her advice for beginners fits on a tag: slow wheel, wet hands, no audience.',
  },
  {
    id: 'clay',
    span: { col: 3, row: 2, cols: 1, rows: 2 },
    face: { kind: 'photo', photo: 'photo-1589051088132-06f36a22012a' },
    kicker: 'Making',
    title: 'Wet clay season',
    byline: 'Jonas Reyes · 6 min read',
    lead: 'From October the studio smells of rain and earth. The kilns run on Thursdays; everything else bends around them.',
    body: [
      'A batch of twelve cups takes nine days from wedge to glaze, and at least two of them never make it out of the kiln.',
      'We stopped hiding the cracks. A seam of gold now marks every cup that broke and was mended.',
    ],
    pull: 'Nine days for a cup you will hold for ten years.',
    pair: ['photo-1609881583302-61548332039c', 'photo-1493106641515-6b5631de4bb9'],
    outro: 'Open studio is the last Sunday of the month. Bring an apron and an empty afternoon.',
  },
  {
    id: 'room',
    span: { col: 0, row: 3, cols: 3, rows: 1 },
    face: { kind: 'photo', photo: 'photo-1544716278-ca5e3f4abd8c' },
    kicker: 'Places',
    title: 'The reading room',
    byline: 'Theo Lindahl · 4 min read',
    lead: 'Up two flights above the bakery on Wren Street, a room with twelve chairs, one rule and a radiator that clanks like a metronome.',
    body: [
      'The rule is silence after ten. Before ten you may whisper, laugh once, and ask the person beside you what they are reading.',
      'The shelves are stocked by whoever comes. Leave a book, take a book, write your name on the card inside the cover.',
    ],
    pull: 'Twelve chairs, one rule, and the bakery below.',
    pair: ['photo-1553729784-e91953dec042', 'photo-1513475382585-d06e58bcb0e0'],
    outro: 'Open from seven until the last chair empties. Nobody has ever checked the time.',
  },
];
