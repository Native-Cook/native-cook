import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BubbleWrapPop } from './BubbleWrapPop';

export default function BubbleWrapPopDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#FBF3EA' }}>
      <StatusBar hidden />
      <BubbleWrapPop autoPlay />
    </View>
  );
}
