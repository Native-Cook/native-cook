/**
 * Two piles of cards, placed where the source's frames put them (f011 + the van from f017, and f009),
 * in screen points on a 402 pt wide screen (the source's screen is 272 × 590 px: 1 px = 1.478 pt). Earlier entries are further back; `blur` is the source's
 * depth of field on the far cards.
 */
import type { ImageSource } from 'expo-image';

export type Card = { id: string; art: ImageSource; label: string; x: number; y: number; w: number; h: number; blur: number; pile: 0 | 1; soft?: ImageSource };

/** Both piles at rest, baked as the source shows it: each card in its depth of field (soft edge included), SOFT_PAD pt of falloff around it. */
export const SOFT_PAD = 24;
const SOFT = {
  woman: require('./assets/soft-woman.png'),
  violet: require('./assets/soft-violet.png'),
  bloom: require('./assets/soft-bloom.png'),
  figure: require('./assets/soft-figure.png'),
  cat: require('./assets/soft-cat.png'),
  lemon: require('./assets/soft-lemon.png'),
  van: require('./assets/soft-van.png'),
  woman2: require('./assets/soft-woman2.png'),
  violet2: require('./assets/soft-violet2.png'),
  bloom2: require('./assets/soft-bloom2.png'),
  jazz: require('./assets/soft-jazz.png'),
  valley: require('./assets/soft-valley.png'),
  bust: require('./assets/soft-bust.png'),
};

const ART = {
  woman: require('./assets/art-orange-woman.jpg'),
  violet: require('./assets/art-violet-flowers.jpg'),
  bloom: require('./assets/art-pastel-bloom.jpg'),
  figure: require('./assets/art-blue-figure.jpg'),
  cat: require('./assets/art-white-cat.jpg'),
  lemon: require('./assets/art-lemon.jpg'),
  van: require('./assets/art-white-van.jpg'),
  jazz: require('./assets/art-slow-jazz.jpg'),
  valley: require('./assets/art-valley.jpg'),
  bust: require('./assets/art-pop-bust.jpg'),
};

export const CARDS: Card[] = [
  // Pile 0: re-measured on the rectified frame 017 for the pixel gate.
  { id: 'woman', art: ART.woman, label: 'Orange afternoon', x: 100, y: 25, w: 98, h: 102, blur: 30, pile: 0, soft: SOFT.woman },
  { id: 'violet', art: ART.violet, label: 'Violet meadow', x: 143, y: 153, w: 100, h: 72, blur: 4, pile: 0, soft: SOFT.violet },
  { id: 'bloom', art: ART.bloom, label: 'Pastel bloom', x: 35, y: 250, w: 95, h: 88, blur: 2, pile: 0, soft: SOFT.bloom },
  { id: 'figure', art: ART.figure, label: 'Blue figure', x: 132, y: 255, w: 159, h: 185, blur: 1, pile: 0, soft: SOFT.figure },
  { id: 'cat', art: ART.cat, label: 'White cat', x: 27, y: 370, w: 125, h: 147, blur: 1, pile: 0, soft: SOFT.cat },
  { id: 'lemon', art: ART.lemon, label: 'Lemon', x: 182, y: 368, w: 188, h: 150, blur: 1, pile: 0, soft: SOFT.lemon },
  { id: 'van', art: ART.van, label: 'White van', x: 105, y: 495, w: 178, h: 162, blur: 0, pile: 0, soft: SOFT.van },
  // Pile 1: re-measured on the rectified frame 008 (the pile dealt and still) for the pixel gate.
  { id: 'woman2', art: ART.woman, label: 'Orange afternoon', x: 72, y: 20, w: 148, h: 160, blur: 30, pile: 1, soft: SOFT.woman2 },
  { id: 'violet2', art: ART.violet, label: 'Violet meadow', x: 100, y: 161, w: 185, h: 106, blur: 1, pile: 1, soft: SOFT.violet2 },
  { id: 'bloom2', art: ART.bloom, label: 'Pastel bloom', x: 0, y: 255, w: 172, h: 175, blur: 2, pile: 1, soft: SOFT.bloom2 },
  { id: 'jazz', art: ART.jazz, label: 'Slow jazz', x: 75, y: 310, w: 128, h: 177, blur: 0, pile: 1, soft: SOFT.jazz },
  { id: 'valley', art: ART.valley, label: 'Green valley', x: 152, y: 269, w: 200, h: 138, blur: 0, pile: 1, soft: SOFT.valley },
  { id: 'bust', art: ART.bust, label: 'Pop bust', x: 203, y: 379, w: 150, h: 169, blur: 6, pile: 1, soft: SOFT.bust },
];

/** The source's pile is drawn for a 402 × 874 screen; other sizes scale it about the centre. */
export const DESIGN = { width: 402, height: 874 } as const;
