import { useLocalSearchParams } from 'expo-router';

import { MovieCardList } from './MovieCardList';

export default function MovieCardListDemo() {
  // Dev only: `?t=<ms>` freezes the autoplay at that time, for pixel comparison with the pin.
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined ? Number(t) : undefined;
  return <MovieCardList key={t ?? 'live'} autoPlay={freezeAt === undefined} freezeAt={freezeAt} />;
}
