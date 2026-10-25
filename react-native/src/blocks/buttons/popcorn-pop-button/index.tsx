import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PopcornPopButton } from './PopcornPopButton';

export default function PopcornPopButtonDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#16070C' }}>
      <StatusBar hidden />
      <PopcornPopButton autoPlay />
    </View>
  );
}
