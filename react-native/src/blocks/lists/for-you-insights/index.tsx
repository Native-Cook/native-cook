import { useLocalSearchParams } from 'expo-router';

import { ForYouInsights } from './ForYouInsights';

export default function ForYouInsightsDemo() {
  const params = useLocalSearchParams<{ manual?: string }>();
  return <ForYouInsights autoPlay={params.manual !== '1'} />;
}
