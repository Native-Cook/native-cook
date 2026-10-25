import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SlotReelList } from './SlotReelList';

export default function SlotReelListDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1B120E' }}>
      <StatusBar hidden />
      <SlotReelList autoPlay />
    </View>
  );
}
