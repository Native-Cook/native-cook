import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { TypewriterSignup } from './TypewriterSignup';

export default function TypewriterSignupDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1c1613' }}>
      <StatusBar hidden />
      <TypewriterSignup autoPlay />
    </View>
  );
}
