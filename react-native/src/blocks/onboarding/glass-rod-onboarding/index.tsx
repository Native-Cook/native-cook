import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { GlassRodOnboarding } from './GlassRodOnboarding';

export default function GlassRodOnboardingDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0d0907' }}>
      <StatusBar hidden />
      <GlassRodOnboarding autoPlay />
    </View>
  );
}
