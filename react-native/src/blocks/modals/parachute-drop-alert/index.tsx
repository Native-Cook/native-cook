import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ParachuteDropAlert } from './ParachuteDropAlert';

export default function ParachuteDropAlertDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F3EEE7' }}>
      <StatusBar hidden />
      <ParachuteDropAlert autoPlay />
    </View>
  );
}
