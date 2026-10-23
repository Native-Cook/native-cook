// Catalog entry: a borderless theatre with a simulation-clock performance.
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CrystalTrophyReveal } from './CrystalTrophyReveal';

export { CrystalTrophyReveal, type CrystalTrophyRevealProps } from './CrystalTrophyReveal';
export default function CrystalTrophyRevealDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000000' }}>
    <StatusBar hidden />
    <CrystalTrophyReveal autoPlay />
  </View>;
}
