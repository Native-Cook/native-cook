import { useLocalSearchParams } from 'expo-router';
import FruitSmoothieCarousel from './FruitSmoothieCarousel';

export default function FruitSmoothieCarouselDemo() {
  const { snapshotTime } = useLocalSearchParams<{ snapshotTime?: string | string[] }>();
  const raw = Array.isArray(snapshotTime) ? snapshotTime[0] : snapshotTime;
  const time = raw === undefined ? Number.NaN : Number(raw);
  return <FruitSmoothieCarousel autoPlay previewTime={Number.isFinite(time) ? Math.max(0, time) : undefined} />;
}
