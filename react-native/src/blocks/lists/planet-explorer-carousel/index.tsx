import { useLocalSearchParams } from 'expo-router';

import { PlanetExplorerCarousel } from './PlanetExplorerCarousel';

export default function PlanetExplorerCarouselDemo() {
  const { manual, t } = useLocalSearchParams<{ manual?: string; t?: string }>();
  // Dev only: `?t=<ms>` freezes the deterministic loop for pixel comparison.
  const freezeAt = __DEV__ && t !== undefined && Number(t) >= 0 ? Number(t) : undefined;
  const autoPlay = !(__DEV__ && manual === '1');

  return <PlanetExplorerCarousel key={`${autoPlay}-${freezeAt ?? 'live'}`} autoPlay={autoPlay} freezeAt={freezeAt} />;
}
