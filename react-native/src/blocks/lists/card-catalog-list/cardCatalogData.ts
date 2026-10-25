// The drawer's cards: fiction from F to G, every name and title invented. Index 14 (Gaskell) is
// where the self-playing demo stops thumbing.

export type CatalogCard = {
  surname: string;
  given: string;
  title: string;
  /** Call number: the class letter on the first line, the author's first three letters below. */
  call: string;
  publisher: string;
  year: number;
  pages: number;
  /** Where the tab sits along the top edge: 0 left, 1 middle, 2 right. */
  tab: 0 | 1 | 2;
  /** Available, on loan, or in the reading room. */
  status: 0 | 1 | 2;
};

const PUBLISHERS = ['Harrow & Finch', 'Mill Lane Press', 'Copperleaf Books', 'Weir & Daughters', 'North Quay'];

const RAW: [string, string, string][] = [
  ['Fairley', 'Ines', 'The Orchard Ledger'],
  ['Falk', 'Tomas', 'Winter Ferry'],
  ['Farrow', 'June', 'A House of Small Weather'],
  ['Faulk', 'Daniel', 'The Glass Cartographer'],
  ['Fenn', 'Odile', 'Saltmarsh Hours'],
  ['Fern', 'Ruth', 'The Night Telegraph'],
  ['Finch', 'Ada', 'Paper Lanterns'],
  ['Fisk', 'Owen', 'The Copper Bell'],
  ['Flint', 'Margo', 'Rooms Above the Bakery'],
  ['Ford', 'Elias', 'The Long Quiet'],
  ['Frey', 'Nadia', 'A Field Guide to Leaving'],
  ['Frost', 'Colm', 'The Ninth Lighthouse'],
  ['Gale', 'Simone', 'The Weather Clock'],
  ['Garnet', 'Theo', 'Small Hours'],
  ['Gaskell', 'Mara', 'The Lantern Weir'],
  ['Gilroy', 'Pen', 'Under the Plane Trees'],
  ['Glass', 'Henry', 'The Cartwright Letters'],
  ['Glenn', 'Ottilie', 'A Room of Blue Tiles'],
  ['Goode', 'Ravi', 'The Tidewater Year'],
  ['Gordon', 'Lise', 'North of the River'],
  ['Grace', 'Wim', 'The Clockmaker’s Niece'],
  ['Gray', 'Edda', 'Lamplight'],
  ['Greer', 'Basil', 'The Quiet Engine'],
  ['Grove', 'Hana', 'Wild Plum'],
];

export const CARDS: CatalogCard[] = RAW.map(([surname, given, title], i) => ({
  surname,
  given,
  title,
  call: `F\n${surname.slice(0, 3).toUpperCase()}`,
  publisher: PUBLISHERS[(i * 3) % PUBLISHERS.length],
  year: 1948 + ((i * 17) % 61),
  pages: 168 + ((i * 53) % 260),
  tab: (i % 3) as 0 | 1 | 2,
  status: (i % 5 === 3 ? 1 : i % 7 === 5 ? 2 : 0) as 0 | 1 | 2,
}));

export const STATUS_LABEL = ['On shelf', 'On loan', 'Reading room'];
export const GASKELL = 14;
