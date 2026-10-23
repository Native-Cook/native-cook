import { FilterMode, MipmapMode, Skia, useImage, type SkImage, type SkPicture } from '@shopify/react-native-skia';
import { useMemo } from 'react';

export type Resources = { bodies: SkImage[]; left: SkImage[]; eyelets: SkImage[]; soles: SkImage[]; shadow: SkImage; paper: SkPicture };
/** Exact ordinary-sRGB CGImages exported from the frozen Swift reference. */
export function useLacedResources(): Resources | null {
  const paper=useImage(require('./assets/paper.png'));
  const body0=useImage(require('./assets/body-tan.png')),body1=useImage(require('./assets/body-charcoal.png')),body2=useImage(require('./assets/body-chestnut.png'));
  const left0=useImage(require('./assets/left-tan.png')),left1=useImage(require('./assets/left-charcoal.png')),left2=useImage(require('./assets/left-chestnut.png'));
  const rim0=useImage(require('./assets/eyelets-tan.png')),rim1=useImage(require('./assets/eyelets-charcoal.png')),rim2=useImage(require('./assets/eyelets-chestnut.png'));
  const sole0=useImage(require('./assets/sole-dark.png')),sole1=useImage(require('./assets/sole-natural.png')),shadow=useImage(require('./assets/shadow.png'));
  return useMemo(()=>{
    if(!paper||!body0||!body1||!body2||!left0||!left1||!left2||!rim0||!rim1||!rim2||!sole0||!sole1||!shadow)return null;
    const rec=Skia.PictureRecorder(),c=rec.beginRecording(Skia.XYWHRect(0,0,393,852));
    c.drawImageRectOptions(paper,Skia.XYWHRect(0,0,paper.width(),paper.height()),Skia.XYWHRect(0,0,393,852),FilterMode.Linear,MipmapMode.None,null);
    return{bodies:[body0,body1,body2],left:[left0,left1,left2],eyelets:[rim0,rim1,rim2],soles:[sole0,sole1],shadow,paper:rec.finishRecordingAsPicture()};
  },[paper,body0,body1,body2,left0,left1,left2,rim0,rim1,rim2,sole0,sole1,shadow]);
}
