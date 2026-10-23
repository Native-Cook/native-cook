import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MorphBubbleColumn } from './MorphBubbleColumn';

export default function MorphBubbleColumnDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f4f4f0' }}>
      <StatusBar hidden />
      <MorphBubbleColumn autoPlay />
    </View>
  );
}
