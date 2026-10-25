import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { KettleBoilProgress } from './KettleBoilProgress';

export default function KettleBoilProgressDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#141C1D' }}>
      <StatusBar hidden />
      <KettleBoilProgress autoPlay />
    </View>
  );
}
