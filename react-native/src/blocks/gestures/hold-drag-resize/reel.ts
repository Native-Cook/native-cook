/** The reel in the preview: stills generated from the source's footage, captions in the source's style. */
import type { ImageSource } from 'expo-image';

export type Clip = { art: ImageSource; caption: string };

// Ordered so the reel's clock puts each clip where the source shows it: the bar behind the coach mark
// (f001), the cocktail while the preview grows (f043), the lamp wall at full screen (f034), the lounge
// back at the compact size (f029).
export const CLIPS: Clip[] = [
  { art: require('./assets/clip-shelves.jpg'), caption: '' }, // the bar behind the coach mark (f001)
  { art: require('./assets/clip-cocktail.jpg'), caption: 'and a lot of' },
  { art: require('./assets/clip-lamps.jpg'), caption: 'small tight spaces,' },
  { art: require('./assets/clip-lounge.jpg'), caption: 'casual drinking den.' },
];

export const THUMBS = CLIPS.map((c) => c.art);
