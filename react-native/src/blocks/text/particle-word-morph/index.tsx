import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ParticleWordMorph } from './ParticleWordMorph';

export default function ParticleWordMorphDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#060a1c' }}>
      <StatusBar hidden />
      <ParticleWordMorph autoPlay />
    </View>
  );
}
