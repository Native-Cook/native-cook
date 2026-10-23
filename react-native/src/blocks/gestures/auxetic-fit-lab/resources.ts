import { useImage, type SkImage } from '@shopify/react-native-skia';
import { useMemo } from 'react';

export type Resources = { plate: SkImage; tightShadow: SkImage; ambientShadow: SkImage; paper: SkImage };
/** The four actual frozen Swift CGImages, exported as ordinary tagged sRGB PNGs. */
export function useAuxeticResources(): Resources | null {
  const plate = useImage(require('./assets/plate.png')), tightShadow = useImage(require('./assets/tight-shadow.png')), ambientShadow = useImage(require('./assets/ambient-shadow.png')), paper = useImage(require('./assets/paper.png'));
  return useMemo(() => plate && tightShadow && ambientShadow && paper ? { plate, tightShadow, ambientShadow, paper } : null, [ambientShadow, paper, plate, tightShadow]);
}
