import { useLocalSearchParams } from 'expo-router';

import { StackedColourCards } from './StackedColourCards';

export default function StackedColourCardsDemo() {
  // dev only: ?t=<ms> freezes the self-playing swing at that time, for pixel comparisons
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t ? Number(t) : -1;
  return <StackedColourCards autoPlay freezeAt={freezeAt} />;
}
