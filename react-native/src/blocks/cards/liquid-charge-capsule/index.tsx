import { useLocalSearchParams } from 'expo-router';

import LiquidChargeCapsule from './LiquidChargeCapsule';

export default function LiquidChargeCapsuleDemo() {
  const { snapshotTime } = useLocalSearchParams<{ snapshotTime?: string | string[] }>();
  const raw = Array.isArray(snapshotTime) ? snapshotTime[0] : snapshotTime;
  const parsed = raw === undefined ? Number.NaN : Number(raw);
  const previewTime = Number.isFinite(parsed) ? Math.max(0, parsed) : undefined;
  return <LiquidChargeCapsule autoPlay previewTime={previewTime} />;
}
