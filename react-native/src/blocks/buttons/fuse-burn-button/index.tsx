import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FuseBurnButton } from './FuseBurnButton';

export default function FuseBurnButtonDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0d1226' }}>
      <StatusBar hidden />
      <FuseBurnButton autoPlay />
    </View>
  );
}
