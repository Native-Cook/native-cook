import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { RotaryPhoneDial } from './RotaryPhoneDial';

export default function RotaryPhoneDialDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#163E33' }}>
      <StatusBar hidden />
      <RotaryPhoneDial autoPlay />
    </View>
  );
}
