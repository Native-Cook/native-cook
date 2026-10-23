import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { GlassBladeDraw } from './GlassBladeDraw';

export default function GlassBladeDrawDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000000' }}>
    <StatusBar hidden />
    <GlassBladeDraw autoPlay />
  </View>;
}
