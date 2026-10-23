import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { WaveformScrubber } from './WaveformScrubber';

export default function WaveformScrubberDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#05070c' }}>
      <StatusBar hidden />
      <WaveformScrubber autoPlay />
    </View>
  );
}
