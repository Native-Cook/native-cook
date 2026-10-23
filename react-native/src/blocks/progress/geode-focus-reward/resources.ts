import { useImage, type SkImage } from '@shopify/react-native-skia';
import { useMemo } from 'react';

export type Resources = { rock: SkImage; cut: SkImage; floor: SkImage; contact: SkImage; glow: SkImage; cores: SkImage[] };
/** Tagged ordinary sRGB images exported from the actual frozen Swift cache. */
export function useGeodeResources(): Resources | null {
  const rock=useImage(require('./assets/rock.png')),cut=useImage(require('./assets/cut.png')),floor=useImage(require('./assets/floor-light.png')),contact=useImage(require('./assets/contact.png')),glow=useImage(require('./assets/glow.png'));
  const c0=useImage(require('./assets/core-00.png')),c1=useImage(require('./assets/core-01.png')),c2=useImage(require('./assets/core-02.png')),c3=useImage(require('./assets/core-03.png')),c4=useImage(require('./assets/core-04.png')),c5=useImage(require('./assets/core-05.png')),c6=useImage(require('./assets/core-06.png')),c7=useImage(require('./assets/core-07.png')),c8=useImage(require('./assets/core-08.png')),c9=useImage(require('./assets/core-09.png')),c10=useImage(require('./assets/core-10.png')),c11=useImage(require('./assets/core-11.png')),c12=useImage(require('./assets/core-12.png')),c13=useImage(require('./assets/core-13.png'));
  return useMemo(()=>{
    if(!rock||!cut||!floor||!contact||!glow||!c0||!c1||!c2||!c3||!c4||!c5||!c6||!c7||!c8||!c9||!c10||!c11||!c12||!c13)return null;
    return {rock,cut,floor,contact,glow,cores:[c0,c1,c2,c3,c4,c5,c6,c7,c8,c9,c10,c11,c12,c13]};
  },[rock,cut,floor,contact,glow,c0,c1,c2,c3,c4,c5,c6,c7,c8,c9,c10,c11,c12,c13]);
}
