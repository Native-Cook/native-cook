import { useLocalSearchParams } from 'expo-router';

import { MenuProductCarousel } from './MenuProductCarousel';

export default function MenuProductCarouselDemo() {
  // Dev only: `?t=<ms>` holds the loop still at that moment, for pixel comparisons with the pin
  // (a negative `t` plays again, since a link without the query keeps the last one).
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined && Number(t) >= 0 ? Number(t) : undefined;
  return <MenuProductCarousel key={String(freezeAt)} autoPlay freezeAt={freezeAt} />;
}
