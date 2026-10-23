import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { OwlAlarmRuler } from './OwlAlarmRuler';

export default function OwlAlarmRulerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#EDEDED' }}>
      <StatusBar style="dark" />
      <OwlAlarmRuler autoPlay />
    </View>
  );
}
