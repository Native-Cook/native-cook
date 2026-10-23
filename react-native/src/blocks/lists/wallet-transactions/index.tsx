import { useLocalSearchParams } from 'expo-router';

import { WalletTransactions } from './WalletTransactions';

export default function WalletTransactionsDemo() {
  // Dev only: `?t=<ms>` freezes the autoplay at that time, for pixel comparison with the pin
  // (a negative `t` plays again, since a link without the query keeps the last one).
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined && Number(t) >= 0 ? Number(t) : undefined;
  return <WalletTransactions key={String(freezeAt)} autoPlay={freezeAt === undefined} freezeAt={freezeAt} />;
}
