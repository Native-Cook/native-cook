import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MagneticLettersName } from './MagneticLettersName';

export default function MagneticLettersNameDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#E8E9E5' }}>
      <StatusBar hidden />
      <MagneticLettersName autoPlay />
    </View>
  );
}
