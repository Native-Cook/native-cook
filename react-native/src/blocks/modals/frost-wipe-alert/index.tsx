import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FrostWipeAlert } from './FrostWipeAlert';

export default function FrostWipeAlertDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0a1424' }}>
      <StatusBar hidden />
      <FrostWipeAlert autoPlay />
    </View>
  );
}
