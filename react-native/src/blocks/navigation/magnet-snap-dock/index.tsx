import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MagnetSnapDock } from './MagnetSnapDock';

export default function MagnetSnapDockDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#16181c' }}>
      <StatusBar hidden />
      <MagnetSnapDock autoPlay />
    </View>
  );
}
