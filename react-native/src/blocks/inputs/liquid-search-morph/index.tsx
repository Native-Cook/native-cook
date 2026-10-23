import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { LiquidSearchMorph } from './LiquidSearchMorph';

export default function LiquidSearchMorphDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f3f0ea' }}>
      <StatusBar hidden />
      <LiquidSearchMorph autoPlay />
    </View>
  );
}
