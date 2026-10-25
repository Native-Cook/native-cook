import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BreadRisingProgress } from './BreadRisingProgress';

export default function BreadRisingProgressDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F0E5D3' }}>
      <StatusBar hidden />
      <BreadRisingProgress autoPlay />
    </View>
  );
}
