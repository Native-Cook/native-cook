import { useLocalSearchParams } from 'expo-router';

import { WorkoutStackList } from './WorkoutStackList';

export default function WorkoutStackListDemo() {
  // dev only: `?t=<ms>` holds the autoplay at that time, for frame-by-frame comparison with the pin
  const { t } = useLocalSearchParams<{ t?: string }>();
  const freezeAt = __DEV__ && t !== undefined ? Number(t) : undefined;
  return <WorkoutStackList autoPlay freezeAt={freezeAt} />;
}
