import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { RocketLaunchProgress } from './RocketLaunchProgress';

export default function RocketLaunchProgressDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0B0D26' }}>
      <StatusBar hidden />
      <RocketLaunchProgress autoPlay />
    </View>
  );
}
