// Coin Pile Collect — what the JS thread does with the beats the frame callback reports: haptics, the optional sound,
// the host's callbacks and the announcement text.
import { useCallback, useState } from 'react';

import { CUE, haptic } from './cpc-haptics';
import { useCoinSound } from './cpc-sound';
import { COPY } from './cpc-theme';
import { formatMoney } from './cpc-use';

export type CueOptions = {
  haptics: boolean;
  sound: boolean;
  totalCents: number;
  onCollect?: (collected: number, remaining: number) => void;
  onEmpty?: (total: number) => void;
  onRefill?: () => void;
};

export function useCues({ haptics, sound, totalCents, onCollect, onEmpty, onRefill }: CueOptions) {
  const play = useCoinSound(sound);
  const [spoken, setSpoken] = useState(COPY.a11yAmount(formatMoney(totalCents)));
  const [empty, setEmpty] = useState(false);
  const onCue = useCallback(
    (kind: number, a: number, b: number) => {
      if (kind === CUE.press && haptics) haptic.press();
      else if (kind === CUE.coin && haptics) haptic.coin();
      else if (kind === CUE.click) {
        if (haptics) haptic.click();
        play('clink');
      } else if (kind === CUE.latch && a > 0 && haptics) haptic.latch();
      else if (kind === CUE.collect) onCollect?.(a / 100, b / 100);
      else if (kind === CUE.empty) {
        if (haptics) haptic.empty();
        play('thunk');
        setEmpty(true);
        setSpoken(COPY.a11yEmpty);
        onEmpty?.(a / 100);
      } else if (kind === CUE.refill) {
        setEmpty(false);
        setSpoken(COPY.a11yAmount(formatMoney(totalCents)));
        onRefill?.();
      }
    },
    [haptics, play, onCollect, onEmpty, onRefill, totalCents],
  );
  return { onCue, spoken, empty };
}
