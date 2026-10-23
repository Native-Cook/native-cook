import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { GlassMarblesTray } from './GlassMarblesTray';

export default function GlassMarblesTrayDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0d0907' }}>
      <StatusBar hidden />
      <GlassMarblesTray autoPlay />
    </View>
  );
}
