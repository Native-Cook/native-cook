import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { InkDropBloom } from './InkDropBloom';

export default function InkDropBloomDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#E6DED2' }}>
      <StatusBar hidden />
      <InkDropBloom autoPlay />
    </View>
  );
}
