import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MorphOtpInput } from './MorphOtpInput';

export default function MorphOtpInputDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0c0d10' }}>
      <StatusBar hidden />
      <MorphOtpInput autoPlay />
    </View>
  );
}
