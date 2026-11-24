import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FloatingLettersStir } from './FloatingLettersStir';

export default function FloatingLettersStirDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#c5e3e1' }}>
      <StatusBar style="dark" />
      <FloatingLettersStir autoPlay />
    </View>
  );
}
