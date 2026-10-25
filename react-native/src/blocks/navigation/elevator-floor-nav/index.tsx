import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ElevatorFloorNav } from './ElevatorFloorNav';

export default function ElevatorFloorNavDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#10221C' }}>
      <StatusBar hidden />
      <ElevatorFloorNav autoPlay />
    </View>
  );
}
