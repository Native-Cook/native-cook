import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PluckStringHarp } from './PluckStringHarp';

export default function PluckStringHarpDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#050403' }}>
      <StatusBar hidden />
      <PluckStringHarp autoPlay />
    </View>
  );
}
