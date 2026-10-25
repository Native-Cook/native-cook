import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SeedSproutWelcome } from './SeedSproutWelcome';

export default function SeedSproutWelcomeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#E0E8D8' }}>
      <StatusBar hidden />
      <SeedSproutWelcome autoPlay />
    </View>
  );
}
