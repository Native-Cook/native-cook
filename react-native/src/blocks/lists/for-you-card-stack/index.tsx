import { useLocalSearchParams } from 'expo-router';

import { ForYouCardStack } from './ForYouCardStack';

export default function ForYouCardStackDemo() {
  // dev only: `?t=<ms>` holds the autoplay at that time, for frame-by-frame comparison with the pin
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined ? Number(t) : undefined;
  return <ForYouCardStack autoPlay freezeAt={freezeAt} />;
}
