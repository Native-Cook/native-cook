import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PianoKeyButton } from './PianoKeyButton';

export default function PianoKeyButtonDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#15100D' }}>
      <StatusBar hidden />
      <PianoKeyButton autoPlay />
    </View>
  );
}
