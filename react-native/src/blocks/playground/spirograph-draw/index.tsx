import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SpirographDraw } from './SpirographDraw';

export default function SpirographDrawDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0a0c16' }}>
      <StatusBar hidden />
      <SpirographDraw autoPlay />
    </View>
  );
}
