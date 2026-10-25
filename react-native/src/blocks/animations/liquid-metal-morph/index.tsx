import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { LiquidMetalMorph } from './LiquidMetalMorph';

export default function LiquidMetalMorphDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#16171b' }}>
      <StatusBar hidden />
      <LiquidMetalMorph autoPlay />
    </View>
  );
}
