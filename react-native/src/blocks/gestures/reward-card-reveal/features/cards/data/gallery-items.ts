import type { GalleryItem } from '../models/gallery-item';

export const galleryItems: GalleryItem[] = [
  {
    cardId: 'verdant-sproutling',
    count: 1,
    id: 'the-awakening',
    image: require('../../../assets/cards/the-awakening.png'),
    kind: 'owned',
    name: 'The Awakening',
  },
  {
    id: 'locked-awakening-set',
    kind: 'locked',
    lockCount: 3,
  },
  {
    cardId: 'tide-fox',
    count: 1,
    id: 'wandering-mind',
    image: require('../../../assets/cards/wandering-mind.png'),
    kind: 'owned',
    name: 'Wandering Mind',
  },
  {
    id: 'locked-wandering-set',
    kind: 'locked',
    lockCount: 2,
  },
];
