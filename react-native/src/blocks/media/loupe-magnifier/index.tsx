import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { LoupeMagnifier } from './LoupeMagnifier';

export default function LoupeMagnifierDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0b0c10' }}>
      <StatusBar hidden />
      <LoupeMagnifier autoPlay />
    </View>
  );
}
