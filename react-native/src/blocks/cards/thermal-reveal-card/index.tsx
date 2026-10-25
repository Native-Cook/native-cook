import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ThermalRevealCard } from './ThermalRevealCard';

export default function ThermalRevealCardDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0e1219' }}>
      <StatusBar hidden />
      <ThermalRevealCard autoPlay />
    </View>
  );
}
