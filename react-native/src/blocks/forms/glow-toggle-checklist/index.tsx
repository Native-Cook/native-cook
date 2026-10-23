import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { GlowToggleChecklist } from './GlowToggleChecklist';

export default function GlowToggleChecklistDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000000' }}>
    <StatusBar hidden />
    <GlowToggleChecklist autoPlay />
  </View>;
}
