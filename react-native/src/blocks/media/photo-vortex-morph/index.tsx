import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PhotoVortexMorph } from './PhotoVortexMorph';

export default function PhotoVortexMorphDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f3f2ee' }}>
      <StatusBar hidden />
      <PhotoVortexMorph autoPlay />
    </View>
  );
}
