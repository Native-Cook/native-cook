// The block's own content: an invented restaurant guide. Photos are credited in the block's notes.
export type Place = {
  key: string;
  name: string;
  area: string;
  about: string;
  facts: { value: string; label: string }[];
  photo: number;
};

export const COPY = {
  brand: 'Forkful',
  title: 'Weekend tables',
  meals: ['Brunch', 'Dinner', 'Late'],
  reserve: 'Reserve a table',
  close: 'Close',
};

export const PLACES: Place[] = [
  {
    key: 'ember',
    name: 'Ember & Rye',
    area: 'Canal Quarter',
    about: 'Squash, peppers and beets roasted over coals, with a short list of natural reds. The counter seats face the fire.',
    facts: [
      { value: '4.8', label: 'Rating' },
      { value: '7:30 PM', label: 'Next table' },
      { value: '$$$', label: 'Price' },
    ],
    photo: require('./assets/veg.webp'),
  },
  {
    key: 'lume',
    name: 'Osteria Lume',
    area: 'Old Mill Row',
    about: 'Hand-cut tagliatelle, slow tomato and a wedge of aged cheese grated at the table. Loud, warm and always full.',
    facts: [
      { value: '4.7', label: 'Rating' },
      { value: '8:15 PM', label: 'Next table' },
      { value: '$$', label: 'Price' },
    ],
    photo: require('./assets/pasta.webp'),
  },
  {
    key: 'saltwater',
    name: 'Saltwater',
    area: 'Harbour Steps',
    about: 'Seared scallops and line-caught fish, plated simply. Ask for a window table when the boats come in.',
    facts: [
      { value: '4.9', label: 'Rating' },
      { value: '6:45 PM', label: 'Next table' },
      { value: '$$$$', label: 'Price' },
    ],
    photo: require('./assets/scallop.webp'),
  },
  {
    key: 'quay',
    name: 'Quay Nine',
    area: 'North Quay',
    about: 'Charred cauliflower, black rice cakes and a view of the water under strings of lights. Good for a long evening.',
    facts: [
      { value: '4.6', label: 'Rating' },
      { value: '9:00 PM', label: 'Next table' },
      { value: '$$', label: 'Price' },
    ],
    photo: require('./assets/lights.webp'),
  },
  {
    key: 'oak',
    name: 'Hollow Oak',
    area: 'Fernhill',
    about: 'Crisp duck leg, herb dumplings and whatever came out of the garden that morning. Twelve tables, one sitting.',
    facts: [
      { value: '4.8', label: 'Rating' },
      { value: '7:00 PM', label: 'Next table' },
      { value: '$$$', label: 'Price' },
    ],
    photo: require('./assets/duck.webp'),
  },
  {
    key: 'lantern',
    name: 'Tin Lantern',
    area: 'Market Lane',
    about: 'Thick pork chops with sweet peppers, cooked in cast iron and carried to the table still spitting.',
    facts: [
      { value: '4.5', label: 'Rating' },
      { value: '8:45 PM', label: 'Next table' },
      { value: '$$', label: 'Price' },
    ],
    photo: require('./assets/chop.webp'),
  },
];
