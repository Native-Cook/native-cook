import { useLocalSearchParams } from 'expo-router';

import { SendMoneyCards } from './SendMoneyCards';

export default function SendMoneyCardsDemo() {
  // Dev only: `?t=<ms>` holds the autoplay still at that moment, for pixel comparisons with the pin.
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined ? Number(t) : undefined;
  return <SendMoneyCards autoPlay freezeAt={freezeAt} />;
}
