import { useRouter } from 'expo-router';
import { Alert } from 'react-native';
import { LiquidGauge } from './pjg-gauge';

export { LiquidGauge, VESSELS } from './pjg-gauge';
export type { LiquidGaugeProps, LiquidGaugeProProps } from './pjg-types';
export default function PourJarGoalDemo() {
  const router = useRouter();
  return (
    <LiquidGauge
      defaultValue={2240}
      max={3100}
      step={10}
      ambient
      autoPlay
      onBack={() => router.back()}
      onOptions={() => Alert.alert('Sora, spring 2027', 'Goal: $3,100\nWeekly saving: $40')}
    />
  );
}
