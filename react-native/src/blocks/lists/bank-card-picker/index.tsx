import { useLocalSearchParams, useRouter } from 'expo-router';

import { BankCardPicker } from './BankCardPicker';

export default function BankCardPickerDemo() {
  // Dev only: `?t=<ms>` freezes the exact scripted selection and transition pose.
  const { t } = useLocalSearchParams<{ t?: string }>();
  const router = useRouter();
  const time = t === undefined ? undefined : Number(t);
  const freezeAt = __DEV__ && time !== undefined && Number.isFinite(time) && time >= 0 ? time : undefined;
  return <BankCardPicker key={t ?? 'live'} autoPlay={freezeAt === undefined} freezeAt={freezeAt} onBack={router.back} />;
}
