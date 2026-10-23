import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FractalTreeBloom } from './FractalTreeBloom';

export default function FractalTreeBloomDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f6e6df' }}>
      <StatusBar hidden />
      <FractalTreeBloom autoPlay />
    </View>
  );
}
