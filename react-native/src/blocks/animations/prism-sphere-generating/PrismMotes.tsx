import { BlendMode, BlurStyle, Picture, Skia } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { PRISM } from './prism-theme';
import { PRISM_MOTES, prismMoteAt } from './prism-timeline';

type Props = {
  layer: 'back' | 'front'; width: number; height: number; cx: number; cy: number; k: number;
  clock: SharedValue<number>; charge: SharedValue<number>; flash: SharedValue<number>;
  scale: SharedValue<number>; burstAge: SharedValue<number>; burstGain: SharedValue<number>;
};

export function PrismMotes({ layer, width, height, cx, cy, k, clock, charge, flash, scale, burstAge, burstGain }: Props) {
  // Each layer owns its paints; recording the other picture cannot change them.
  const { core, halo, bokeh, colours } = useMemo(() => {
    const corePaint = Skia.Paint();
    corePaint.setAntiAlias(true); corePaint.setBlendMode(BlendMode.Plus);
    const haloPaint = Skia.Paint();
    haloPaint.setAntiAlias(true); haloPaint.setBlendMode(BlendMode.Plus);
    haloPaint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.1 * k, true));
    const bokehPaint = Skia.Paint();
    bokehPaint.setAntiAlias(true); bokehPaint.setBlendMode(BlendMode.Plus);
    bokehPaint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3 * k, true));
    return { core: corePaint, halo: haloPaint, bokeh: bokehPaint, colours: PRISM.moteColours.map((colour) => Skia.Color(colour)) };
  }, [k]);
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const time = clock.get(), compression = charge.get(), burst = burstAge.get();
    const gain = burstGain.get(), flare = flash.get(), size = scale.get();
    for (let i = 0; i < PRISM_MOTES.length; i++) {
      const mote = PRISM_MOTES[i];
      if ((mote.z < 0) !== (layer === 'back')) continue;
      const point = prismMoteAt(mote, time, compression, burst, gain, flare, size);
      const x = cx + point.x * k, y = cy + point.y * k, radius = mote.radius * k;
      halo.setColor(colours[mote.colour]); halo.setAlphaf(point.alpha * 0.25);
      canvas.drawCircle(x, y, radius * 3, halo);
      const paint = mote.bokeh ? bokeh : core;
      paint.setColor(colours[mote.colour]); paint.setAlphaf(point.alpha);
      canvas.drawCircle(x, y, radius, paint);
    }
    return recorder.finishRecordingAsPicture();
  });
  return <Picture picture={picture} />;
}
