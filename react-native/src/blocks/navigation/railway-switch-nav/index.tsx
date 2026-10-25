import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { RailwaySwitchNav } from './RailwaySwitchNav';

export default function RailwaySwitchNavDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#131D19' }}>
      <StatusBar hidden />
      <RailwaySwitchNav autoPlay />
    </View>
  );
}
