import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BobaPearlProgress } from './BobaPearlProgress';

export default function BobaPearlProgressDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F4E2D0' }}>
      <StatusBar hidden />
      <BobaPearlProgress autoPlay />
    </View>
  );
}
