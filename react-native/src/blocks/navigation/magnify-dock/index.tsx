import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MagnifyDock } from './MagnifyDock';

export default function MagnifyDockDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1d3150' }}>
      <StatusBar hidden />
      <MagnifyDock autoPlay />
    </View>
  );
}
