import { useLocalSearchParams } from 'expo-router';

import { CardBalanceWallet } from './CardBalanceWallet';

export default function CardBalanceWalletDemo() {
  // Dev only: `?t=<ms>` holds the screen still (the pin's frame), for pixel comparisons.
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined ? Number(t) : undefined;
  return <CardBalanceWallet autoPlay freezeAt={freezeAt} />;
}
