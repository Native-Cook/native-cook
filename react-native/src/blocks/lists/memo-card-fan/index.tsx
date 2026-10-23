import { useLocalSearchParams } from 'expo-router';

import { MemoCardFan } from './MemoCardFan';

export default function MemoCardFanDemo() {
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined && Number(t) >= 0 ? Number(t) : undefined;

  return <MemoCardFan key={String(freezeAt)} autoPlay freezeAt={freezeAt} />;
}
