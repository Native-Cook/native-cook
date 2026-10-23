import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CityEventHero } from './CityEventHero';

export default function CityEventHeroDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar style="light" />
      <CityEventHero autoPlay />
    </View>
  );
}
