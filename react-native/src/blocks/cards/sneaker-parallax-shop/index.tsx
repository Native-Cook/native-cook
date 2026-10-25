import { useLocalSearchParams, useRouter } from 'expo-router';

import SneakerParallaxShop from './SneakerParallaxShop';

export default function SneakerParallaxShopDemo() {
  const { snapshotTime } = useLocalSearchParams<{ snapshotTime?: string | string[] }>();
  const router = useRouter();
  const raw = Array.isArray(snapshotTime) ? snapshotTime[0] : snapshotTime;
  const parsed = raw === undefined ? Number.NaN : Number(raw);
  const previewTime = Number.isFinite(parsed) ? Math.max(0, parsed) : undefined;
  return <SneakerParallaxShop autoPlay previewTime={previewTime} onBack={router.back} />;
}
