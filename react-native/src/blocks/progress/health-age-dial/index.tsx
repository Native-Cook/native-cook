import { useLocalSearchParams } from 'expo-router';

import { HealthAgeDial } from './HealthAgeDial';

export default function HealthAgeDialDemo() {
  const params = useLocalSearchParams<{ manual?: string }>();
  return <HealthAgeDial autoPlay={params.manual !== '1'} />;
}
