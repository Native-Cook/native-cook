import { useLocalSearchParams } from 'expo-router';

import { GiftCardPicker } from './GiftCardPicker';

export default function GiftCardPickerDemo() {
  // dev only: ?t=<ms> freezes the self-playing loop at that time, for pixel comparisons
  const { t, manual } = useLocalSearchParams<{ t?: string; manual?: string }>();
  const parsed = t === undefined ? Number.NaN : Number(t);
  const freezeAt = __DEV__ && Number.isFinite(parsed) && parsed >= 0 ? parsed : -1;
  return <GiftCardPicker autoPlay={!(__DEV__ && manual === '1')} freezeAt={freezeAt} />;
}
