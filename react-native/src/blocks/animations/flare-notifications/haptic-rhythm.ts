import * as Haptics from "expo-haptics";

// A cancellable run of selection ticks. Each gap is the previous one times
// `stretch`, plus up to `jitter` ms, so the rhythm can speed up, slow down or
// crackle irregularly. `delay` holds the first tick back, so a press can turn
// into a hold before anything plays. Owned by one component via useMemo.
export function hapticRhythm() {
  let timer: ReturnType<typeof setTimeout> | null = null;
  const stop = () => {
    if (timer) clearTimeout(timer);
    timer = null;
  };
  const play = ({
    first,
    stretch,
    jitter = 0,
    total,
    delay = 0,
  }: {
    first: number;
    stretch: number;
    jitter?: number;
    total: number;
    delay?: number;
  }) => {
    stop();
    let left = total;
    const step = (gap: number) => {
      void Haptics.selectionAsync().catch(() => {});
      const wait = gap + Math.random() * jitter;
      left -= wait;
      if (left > 0) timer = setTimeout(() => step(gap * stretch), wait);
      else timer = null;
    };
    if (delay > 0) timer = setTimeout(() => step(first), delay);
    else step(first);
  };
  return { play, stop };
}
