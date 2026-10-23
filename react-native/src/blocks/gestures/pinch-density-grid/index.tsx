import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PinchDensityGrid } from './PinchDensityGrid';

export default function PinchDensityGridDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#eeece4' }}>
      <StatusBar hidden />
      <PinchDensityGrid autoPlay />
    </View>
  );
}
