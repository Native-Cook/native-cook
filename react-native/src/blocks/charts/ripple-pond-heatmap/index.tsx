import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { RipplePondHeatmap } from './RipplePondHeatmap';

export default function RipplePondHeatmapDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0B2A2E' }}>
      <StatusBar hidden />
      <RipplePondHeatmap autoPlay />
    </View>
  );
}
