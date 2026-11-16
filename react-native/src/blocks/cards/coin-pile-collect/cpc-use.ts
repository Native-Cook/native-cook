// Coin Pile Collect — the resources hook (typefaces, shaders, atlas) shared by the screen and the CoinHopper tile.
import { useMemo } from 'react';
import { PixelRatio, Platform } from 'react-native';
import { useTypeface } from '@shopify/react-native-skia';

import { makeRes, type Res } from './cpc-art';
import { fontScaleOf } from './cpc-theme';

/** oy: the window y of the design's origin (layout.oy), so the navy tile can bleed off the window's bottom edge */
export function useCpcRes(w: number, h: number, scale: number, oy = 0): Res | null {
  const ebTf = useTypeface(require('./assets/fonts/Onest-ExtraBold.ttf'));
  const sbTf = useTypeface(require('./assets/fonts/Onest-SemiBold.ttf'));
  const fs = fontScaleOf(PixelRatio.getFontScale());
  const pr = PixelRatio.get();
  return useMemo(() => {
    if (!ebTf || !sbTf || w <= 0 || h <= 0) return null;
    try {
      return makeRes({
        ebTf,
        sbTf,
        p3: Platform.OS === 'ios' ? 1 : 0,
        fs,
        upx: 1 / (Math.max(scale, 0.01) * pr),
        w,
        h,
        bottom: (h - oy) / Math.max(scale, 0.01),
      });
    } catch (e) {
      console.warn(String(e));
      return null;
    }
  }, [ebTf, sbTf, fs, pr, w, h, scale, oy]);
}

/** "$220.34" from cents */
export function formatMoney(cents: number) {
  return `$${(Math.max(cents, 0) / 100).toFixed(2)}`;
}
