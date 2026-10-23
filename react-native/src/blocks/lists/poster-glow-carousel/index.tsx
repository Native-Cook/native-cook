import { useLocalSearchParams } from 'expo-router';

import { PosterGlowCarousel } from './PosterGlowCarousel';

export default function PosterGlowCarouselDemo() {
  // Dev only: `?t=<ms>` freezes the autoplay at that time, for pixel comparison with the pin.
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined ? Number(t) : undefined;
  return <PosterGlowCarousel autoPlay={freezeAt === undefined} freezeAt={freezeAt} />;
}
