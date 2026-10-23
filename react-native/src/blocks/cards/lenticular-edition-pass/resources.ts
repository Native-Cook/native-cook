import { BlurStyle, FilterMode, MipmapMode, Skia, TileMode, useImage, type SkImage, type SkMaskFilter, type SkPicture, type SkRuntimeEffect, type SkShader } from '@shopify/react-native-skia';
import { useMemo } from 'react';

import { lensSource } from './shader';

export type Resources = { prints: SkImage[]; atlas: SkImage; atlasShader: SkShader; lens: SkRuntimeEffect; paper: SkPicture; ambientBlur: SkMaskFilter; contactBlur: SkMaskFilter };
const lens = Skia.RuntimeEffect.Make(lensSource);

/** Exact frozen Swift cached CGImages, not regenerated artwork or packed control data. */
export function useLenticularResources(): Resources | null {
  const a = useImage(require('./assets/art-a.png')), b = useImage(require('./assets/art-b.png'));
  const atlas = useImage(require('./assets/atlas.png')), paper = useImage(require('./assets/paper.png'));
  return useMemo(() => {
    if (!a || !b || !atlas || !paper) return null;
    if (!lens) throw new Error('Lenticular Edition Pass lens shader did not compile');
    const recorder = Skia.PictureRecorder(), canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, 1024));
    canvas.drawImageRectOptions(paper, Skia.XYWHRect(0, 0, paper.width(), paper.height()), Skia.XYWHRect(0, 0, 393, 1024), FilterMode.Linear, MipmapMode.None, null);
    return { prints: [a, b], atlas, lens, atlasShader: atlas.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None), paper: recorder.finishRecordingAsPicture(), ambientBlur: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 27, true), contactBlur: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true) };
  }, [a, atlas, b, paper]);
}
