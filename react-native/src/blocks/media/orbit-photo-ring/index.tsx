import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { OrbitPhotoRing } from './OrbitPhotoRing';

export default function OrbitPhotoRingDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#050506' }}>
      <StatusBar hidden />
      <OrbitPhotoRing autoPlay />
    </View>
  );
}
