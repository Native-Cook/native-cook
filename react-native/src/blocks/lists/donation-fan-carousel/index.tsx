import { useLocalSearchParams } from 'expo-router';

import { DonationFanCarousel } from './DonationFanCarousel';

export default function DonationFanCarouselDemo() {
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined && Number(t) >= 0 ? Number(t) : undefined;

  return <DonationFanCarousel key={String(freezeAt)} autoPlay freezeAt={freezeAt} />;
}
