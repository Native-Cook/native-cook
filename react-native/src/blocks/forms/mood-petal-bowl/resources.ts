import { Skia, TileMode, useImage, type SkImage, type SkImageFilter, type SkPath, type SkPicture } from '@shopify/react-native-skia';
import { useMemo } from 'react';

import { makeStaticArt } from './static-art';

export type Resources = { sprites: SkImage[]; sharpStrip: SkImage; softStrip: SkImage; disk: SkPath; sharpVisibility: SkPath; softFilter: SkImageFilter; crop: SkImageFilter; background: SkPicture; rim: SkPicture; footer: SkPicture };
const refraction = Skia.RuntimeEffect.Make(`uniform shader image;
half4 main(float2 p) {
  float depth = clamp((p.y - 273.0) / 153.0, 0.0, 1.0);
  return image.eval(float2(p.x - 1.6 * depth, p.y));
}`);

/** Direct exports of the frozen Swift CGImages; all are ordinary tagged sRGB. */
export function useMoodResources(): Resources | null {
  const calm=useImage(require('./assets/token-calm.png')),low=useImage(require('./assets/token-low.png')),bright=useImage(require('./assets/token-bright.png')),tense=useImage(require('./assets/token-tense.png'));
  const paper=useImage(require('./assets/paper.png')),header=useImage(require('./assets/header.png')),sharpStrip=useImage(require('./assets/sharp-strip.png')),softStrip=useImage(require('./assets/soft-strip.png'));
  return useMemo(() => {
    if(!calm||!low||!bright||!tense||!paper||!header||!sharpStrip||!softStrip)return null;
    if(!refraction)throw new Error('Mood Petal Bowl refraction shader did not compile');
    const blur=Skia.ImageFilter.MakeBlur(8.5,8.5,TileMode.Decal),s=.95,r=.213*(1-s),g=.715*(1-s),b=.072*(1-s);
    const saturation=Skia.ColorFilter.MakeMatrix([s+r,g,b,0,0,r,s+g,b,0,0,r,g,s+b,0,0,0,0,0,1,0]);
    const saturated=Skia.ImageFilter.MakeColorFilter(saturation,blur),builder=Skia.RuntimeShaderBuilder(refraction);
    const softFilter=Skia.ImageFilter.MakeRuntimeShaderWithChildren(builder,1.6,['image'],[saturated]);
    const disk=Skia.PathBuilder.Make().addOval(Skia.XYWHRect(43.5,120,306,306)).detach(),pictures=makeStaticArt(paper,header,softStrip,disk);
    return {sprites:[calm,low,bright,tense],sharpStrip,softStrip,softFilter,disk,...pictures,crop:Skia.ImageFilter.MakeCrop(Skia.XYWHRect(35,137,323,293),TileMode.Decal),
      sharpVisibility:Skia.PathBuilder.Make().addRect(Skia.XYWHRect(9,111,375,162)).addOval(Skia.XYWHRect(43.5,120,306,306)).detach()};
  },[bright,calm,header,low,paper,sharpStrip,softStrip,tense]);
}
