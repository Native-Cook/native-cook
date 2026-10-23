import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { GlassFolderBurst } from './GlassFolderBurst';

export default function GlassFolderBurstDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <GlassFolderBurst autoPlay />
    </View>
  );
}
