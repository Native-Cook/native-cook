import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MarblingComb } from './MarblingComb';

export default function MarblingCombDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#16243c' }}>
      <StatusBar hidden />
      <MarblingComb autoPlay />
    </View>
  );
}
