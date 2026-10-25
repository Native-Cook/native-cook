import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FortuneCookieCrack } from './FortuneCookieCrack';

export default function FortuneCookieCrackDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1C0E0B' }}>
      <StatusBar hidden />
      <FortuneCookieCrack autoPlay />
    </View>
  );
}
