import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SlinkyStairs } from './SlinkyStairs';

export default function SlinkyStairsDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#212B2A' }}>
      <StatusBar hidden />
      <SlinkyStairs autoPlay />
    </View>
  );
}
