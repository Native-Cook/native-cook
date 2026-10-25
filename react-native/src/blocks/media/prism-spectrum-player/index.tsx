import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PrismSpectrumPlayer } from './PrismSpectrumPlayer';

export default function PrismSpectrumPlayerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0D0F1F' }}>
      <StatusBar hidden />
      <PrismSpectrumPlayer autoPlay />
    </View>
  );
}
