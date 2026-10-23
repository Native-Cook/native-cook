import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { LiquidSwipeOnboarding } from './LiquidSwipeOnboarding';

export default function LiquidSwipeOnboardingDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#ff5b1f' }}>
      <StatusBar hidden />
      <LiquidSwipeOnboarding autoPlay />
    </View>
  );
}
