import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SwitcherDeckNav } from './SwitcherDeckNav';

export default function SwitcherDeckNavDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0d0c0b' }}>
      <StatusBar hidden />
      <SwitcherDeckNav autoPlay />
    </View>
  );
}
