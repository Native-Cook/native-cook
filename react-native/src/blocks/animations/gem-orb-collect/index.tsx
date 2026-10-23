// Self-playing catalog page; direct use of GemOrbCollect defaults to manual control.
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { GemOrbCollect } from './GemOrbCollect';

export default function GemOrbCollectDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000000' }}>
    <StatusBar hidden />
    <GemOrbCollect autoPlay />
  </View>;
}
