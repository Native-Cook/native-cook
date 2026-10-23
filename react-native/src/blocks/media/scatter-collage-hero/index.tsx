import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ScatterCollageHero } from './ScatterCollageHero';

export default function ScatterCollageHeroDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#fafaf7' }}>
      <StatusBar hidden />
      <ScatterCollageHero autoPlay />
    </View>
  );
}
