import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { JellyCubeSquish } from './JellyCubeSquish';

export default function JellyCubeSquishDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F1DCD6' }}>
      <StatusBar hidden />
      <JellyCubeSquish autoPlay />
    </View>
  );
}
