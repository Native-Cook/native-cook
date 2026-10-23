import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PillRiseSplash } from './PillRiseSplash';

export default function PillRiseSplashDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#a3eb6e' }}>
      <StatusBar hidden />
      <PillRiseSplash autoPlay />
    </View>
  );
}
