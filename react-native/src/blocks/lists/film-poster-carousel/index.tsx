import { useLocalSearchParams } from 'expo-router';

import { FilmPosterCarousel } from './FilmPosterCarousel';

export default function FilmPosterCarouselDemo() {
  // Dev only: `?t=<ms>` freezes the autoplay at that time, for pixel comparison with the pin.
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined ? Number(t) : undefined;
  return <FilmPosterCarousel key={t ?? 'live'} autoPlay={freezeAt === undefined} freezeAt={freezeAt} />;
}
