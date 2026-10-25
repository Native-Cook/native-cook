import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { RopeTabBar } from './RopeTabBar';

export default function RopeTabBarDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#13201a' }}>
      <StatusBar hidden />
      <RopeTabBar autoPlay />
    </View>
  );
}
