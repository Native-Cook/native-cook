import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SpiritLevelSlider } from './SpiritLevelSlider';

export default function SpiritLevelSliderDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#ECE4D5' }}>
      <StatusBar hidden />
      <SpiritLevelSlider autoPlay />
    </View>
  );
}
