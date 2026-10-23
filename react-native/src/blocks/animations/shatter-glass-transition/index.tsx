import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ShatterGlassTransition } from './ShatterGlassTransition';

export default function ShatterGlassTransitionDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#05060a' }}>
      <StatusBar hidden />
      <ShatterGlassTransition autoPlay />
    </View>
  );
}
