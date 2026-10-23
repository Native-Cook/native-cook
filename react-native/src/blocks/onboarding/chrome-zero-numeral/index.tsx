// Chrome Zero — catalog demo, edge-to-edge with no status bar.
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { ChromeZeroNumeral } from './ChromeZeroNumeral';

export default function ChromeZeroNumeralDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000' }}>
    <StatusBar hidden />
    <ChromeZeroNumeral autoPlay />
  </View>;
}
