import { useLocalSearchParams } from 'expo-router';
import ElasticFoodCards from './ElasticFoodCards';

export default function ElasticFoodCardsDemo() {
  const { snapshotTime } = useLocalSearchParams<{ snapshotTime?: string | string[] }>();
  const raw = Array.isArray(snapshotTime) ? snapshotTime[0] : snapshotTime;
  const parsed = raw === undefined ? Number.NaN : Number(raw);
  return <ElasticFoodCards autoPlay previewTime={Number.isFinite(parsed) ? Math.max(0, parsed) : undefined} />;
}
