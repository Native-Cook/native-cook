import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { NeonSignFlicker } from './NeonSignFlicker';

export default function NeonSignFlickerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0c0908' }}>
      <StatusBar hidden />
      <NeonSignFlicker autoPlay />
    </View>
  );
}
