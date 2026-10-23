import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BadgeVaultGrid } from './BadgeVaultGrid';

export default function BadgeVaultGridDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000000' }}>
    <StatusBar hidden />
    <BadgeVaultGrid autoPlay />
  </View>;
}
