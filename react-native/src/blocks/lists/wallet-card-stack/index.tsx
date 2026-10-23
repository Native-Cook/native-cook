import { useLocalSearchParams } from 'expo-router';

import { WalletCardStack } from './WalletCardStack';

export default function WalletCardStackDemo() {
  // Dev only: `?t=<ms>` holds the screen still (the pin's frame), for pixel comparisons.
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined ? Number(t) : undefined;
  return <WalletCardStack autoPlay freezeAt={freezeAt} />;
}
