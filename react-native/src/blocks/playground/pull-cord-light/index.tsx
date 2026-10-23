import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PullCordLight } from './PullCordLight';

export default function PullCordLightDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#05060a' }}>
      <StatusBar hidden />
      <PullCordLight autoPlay />
    </View>
  );
}
